package com.moez.QKSMS.fengni

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.fengni.mqttf.FengniLocalProxy
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import com.moez.QKSMS.manager.QkTransaction
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.repository.SyncRepository
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Persistent foreground service managing the fengni MQTT connection.
 *
 * Starts FengniLocalProxy (transparent fengni encryption tunnel),
 * connects HiveMQ MQTT5 client through it, subscribes to incoming
 * SMS topics, and publishes outgoing SMS.
 */
class FengniMqttService : Service() {

    companion object {
        const val ACTION_PUBLISH = "com.moez.QKSMS.fengni.PUBLISH"
        const val ACTION_ONLINE_COUNT = "com.moez.QKSMS.fengni.ONLINE_COUNT"
        const val ACTION_ONLINE_COUNT_QUERY = "com.moez.QKSMS.fengni.ONLINE_COUNT_QUERY"
        const val EXTRA_ONLINE_COUNT = "onlineCount"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_BODY = "body"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_SUB_ID = "subId"

        private const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID = "mqttf_sync"
        private const val CHANNEL_NAME = "MQTTF Sync"

        fun publish(
            context: Context,
            address: String,
            body: String,
            timestamp: Long,
            subId: Int
        ): Boolean {
            val intent = Intent(context, FengniMqttService::class.java).apply {
                action = ACTION_PUBLISH
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_BODY, body)
                putExtra(EXTRA_TIMESTAMP, timestamp)
                putExtra(EXTRA_SUB_ID, subId)
            }
            context.startService(intent)
            return true
        }
    }

    @Inject lateinit var configManager: FengniConfigManager
    @Inject lateinit var messageStore: FengniMessageStore
    @Inject lateinit var syncRepository: SyncRepository
    @Inject lateinit var messageRepo: dev.octoshrimpy.quik.repository.MessageRepository
    @Inject lateinit var phoneUtils: dev.octoshrimpy.quik.util.PhoneNumberUtils
    @Inject lateinit var notificationMgr: dev.octoshrimpy.quik.manager.NotificationManager
    @Inject lateinit var conversationRepo: dev.octoshrimpy.quik.repository.ConversationRepository
    @Inject lateinit var shortcutManager: dev.octoshrimpy.quik.manager.ShortcutManager
    @Inject lateinit var updateBadge: dev.octoshrimpy.quik.interactor.UpdateBadge

    private val running = AtomicBoolean(false)
    @Volatile private var lastOnlineCount: Int = 0
    private var proxy: FengniLocalProxy? = null
    private var mqttClient: Mqtt5BlockingClient? = null
    private var connectThread: Thread? = null
    private var smsObserver: ContentObserver? = null
    private var smsHandlerThread: HandlerThread? = null
    private var lastProcessedSmsId: Long = -1L
    private val smsPrefs: SharedPreferences by lazy {
        getSharedPreferences("fengni_sms_state", Context.MODE_PRIVATE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Observe system SMS database for new incoming messages
        // Always start from current max SMS _id. ContentObserver's job is
        // only to forward NEW SMS that arrive while this service is running.
        // Historical SMS from before the service started should not flood in
        // when the next onChange() fires — they are synced via MQTT instead.
        lastProcessedSmsId = getCurrentMaxSmsId()
        smsPrefs.edit().putLong("last_sms_id", lastProcessedSmsId).apply()
        // Use a background HandlerThread so ContentObserver.onChange()
        // runs off the main thread. This allows syncMessage() to do
        // synchronous Realm writes without blocking the UI.
        smsHandlerThread = HandlerThread("fengni-sms-observer").also { it.start() }
        smsObserver = object : ContentObserver(Handler(smsHandlerThread!!.looper)) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                checkNewSms()
            }
        }
        contentResolver.registerContentObserver(
            Uri.parse("content://sms"),
            true,
            smsObserver!!
        )

        Timber.i("FengniMqttService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PUBLISH -> handlePublish(intent)
            ACTION_ONLINE_COUNT_QUERY -> sendOnlineCountBroadcast(lastOnlineCount)
        }

        if (!running.getAndSet(true)) {
            connectThread = Thread(this::connectLoop, "fengni-mqtt-connect").apply { start() }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        smsObserver?.let { contentResolver.unregisterContentObserver(it) }
        smsHandlerThread?.quitSafely()
        running.set(false)
        disconnect()
        connectThread?.interrupt()
        Timber.i("FengniMqttService destroyed")
        super.onDestroy()
    }

    // ── Online count broadcast ──

    private fun sendOnlineCountBroadcast(count: Int) {
        val intent = Intent(ACTION_ONLINE_COUNT)
            .putExtra(EXTRA_ONLINE_COUNT, count)
            .setPackage(packageName)
        androidx.localbroadcastmanager.content.LocalBroadcastManager
            .getInstance(this@FengniMqttService)
            .sendBroadcast(intent)
    }

    // ── Connection lifecycle ──

    private var retryDelay = 1_000L
    private val maxRetryDelay = 300_000L

    private fun connectLoop() {
        while (running.get()) {
            try {
                connectAndSubscribe()
                retryDelay = 1_000L  // reset on successful connection
                while (running.get() && mqttClient?.state?.isConnected == true) {
                    Thread.sleep(1000)
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                Timber.e(e, "MQTT connection error, retrying in ${retryDelay / 1000}s...")
            }

            if (running.get()) {
                disconnect()
                try { Thread.sleep(retryDelay) } catch (e: InterruptedException) { break }
                retryDelay = minOf(retryDelay * 2, maxRetryDelay)
            }
        }
    }

    private fun connectAndSubscribe() {
        val config = configManager.getConfig()
        if (config == null) {
            Timber.w("No fengni config available; retrying in next cycle")
            return
        }

        Timber.i("Starting fengni proxy → ${config.remoteHost}:${config.remotePort}")

        proxy = FengniLocalProxy(
            config.remoteHost,
            config.remotePort,
            config.serverPubKey,
            config.routePubKey,
            config.groupId,
            config.deviceId,
            config.hmacKey,
            config.paddingMax,
            object : com.fengni.mqttf.FengniLocalProxy.Callback {
                override fun onLog(msg: String) { Timber.d("FengniProxy: $msg") }
                override fun onOnlineCount(count: Int) {
                    lastOnlineCount = count
                    sendOnlineCountBroadcast(count)
                    updateNotification(count)
                }
            }
        )
        proxy?.start()

        // Allow proxy to bind localhost:1883
        Thread.sleep(1000)

        val clientId = "fengni-${config.deviceId}"
        Timber.i("Connecting MQTT5 client '$clientId' to 127.0.0.1:1883")

        val client = Mqtt5Client.builder()
            .serverHost("127.0.0.1")
            .serverPort(1883)
            .identifier(clientId)
            .build()
            .toBlocking()

        client.connectWith()
            .cleanStart(true)
            .keepAlive(30)
            .sessionExpiryInterval(0)
            .send()

        Timber.i("MQTT5 connected")

        val incomingTopic = FengniConfig.incomingTopic(config.customerId)
        client.subscribeWith()
            .topicFilter(incomingTopic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .send()

        Timber.i("Subscribed to $incomingTopic")

        // Subscribe to outgoing topic for cross-device sent-message sync.
        // Each device publishes to customer/{id}/device/{deviceId}/sms/outgoing
        // when sending; subscribing with + wildcard lets every device see
        // what other devices sent, without touching the SMS delivery pipeline.
        val outgoingFilter = "customer/${config.customerId}/device/+/sms/outgoing"
        client.subscribeWith()
            .topicFilter(outgoingFilter)
            .qos(MqttQos.AT_LEAST_ONCE)
            .send()

        Timber.i("Subscribed to $outgoingFilter")

        mqttClient = client

        QkTransaction.fengniSendHandler = { _, address, body, subscriptionId ->
            val result = enqueueOutgoing(address, body, System.currentTimeMillis(), subscriptionId)
            result
        }

        // Hook to update Realm message status after fengni send completes.
        // Replaces the SMS_SENT broadcast → MessageSentReceiver chain.
        QkTransaction.fengniMarkSent = { messageId ->
            messageRepo.markSent(messageId)
        }

        // Hook to mark Realm message as failed when fengni send fails.
        QkTransaction.fengniMarkFailed = { messageId ->
            messageRepo.markFailed(messageId, 0)
        }

        // Load existing messages into the UI
        syncRepository.syncMessages()

        val readerThread = Thread(this::messageLoop, "fengni-mqtt-reader")
        readerThread.isDaemon = true
        readerThread.start()
    }

    private fun disconnect() {
        try {
            mqttClient?.disconnect()
        } catch (e: Exception) {
            Timber.e(e, "MQTT disconnect error")
        }
        try {
            proxy?.shutdown()
        } catch (e: Exception) {
            Timber.e(e, "Proxy shutdown error")
        }
        mqttClient = null
        proxy = null
        lastOnlineCount = 0
        sendOnlineCountBroadcast(0)
    }

    // ── Message loop (incoming + outgoing) ──

    private fun messageLoop() {
        try {
            val client = mqttClient ?: return
            val publishes = client.publishes(MqttGlobalPublishFilter.ALL)

            while (running.get()) {
                val publish = publishes.receive(5, TimeUnit.SECONDS).orElse(null) ?: continue

                try {
                    val payload = String(publish.getPayloadAsBytes() ?: ByteArray(0), Charsets.UTF_8)
                    val json = JSONObject(payload)

                    val topicLevels = publish.getTopic().levels
                    // Topic pattern: customer/{id}/device/{deviceId}/sms/{type}
                    //   levels[3] = deviceId  levels[5] = "incoming" | "outgoing"
                    val sourceDevice = topicLevels.getOrNull(3) ?: "unknown"
                    val topicType = topicLevels.getOrNull(5)
                    val myDeviceId = configManager.getConfig()?.deviceId

                    // Skip messages published by this device for both topic types
                    if (sourceDevice == myDeviceId) {
                        Timber.d("Skipping self-published $topicType from $sourceDevice")
                        continue
                    }

                    // Recompute threadId locally so it is consistent across all devices.
                    // phoneUtils.normalizeNumber() ensures thread grouping survives
                    // locale / formatting differences between devices.
                    val address = json.getString("address")
                    val normalizedAddress = phoneUtils.normalizeNumber(address)
                    val threadId = normalizedAddress.hashCode().toLong()

                    val fengniMsg = when (topicType) {
                        "incoming" -> FengniMessage(
                            id = messageStore.newId(),
                            address = address,
                            body = json.getString("body"),
                            date = json.optLong("date", System.currentTimeMillis()),
                            threadId = threadId,
                            subId = json.optInt("subId", -1),
                            type = FengniMessage.TYPE_SMS,
                            boxId = FengniMessage.BOX_INBOX,
                            read = false,
                            seen = false,
                            sourceDevice = sourceDevice,
                            forwardStatus = FengniMessage.STATUS_SENT,
                            isOutgoing = false
                        )
                        "outgoing" -> FengniMessage(
                            id = messageStore.newId(),
                            address = address,
                            body = json.getString("body"),
                            date = json.optLong("date", System.currentTimeMillis()),
                            threadId = threadId,
                            subId = json.optInt("subId", -1),
                            type = FengniMessage.TYPE_SMS,
                            boxId = FengniMessage.BOX_INBOX,
                            read = true,
                            seen = true,
                            sourceDevice = sourceDevice,
                            forwardStatus = FengniMessage.STATUS_SENT,
                            isOutgoing = false
                        )
                        else -> {
                            Timber.d("Ignoring unknown MQTT topic type: $topicType")
                            continue
                        }
                    }

                    messageStore.insert(fengniMsg)
                    val realmMessage = syncRepository.syncMessage(android.net.Uri.EMPTY, fengniMsg.id)
                    // Use the threadId from the Realm Message (computed by
                    // TelephonyCompat inside FengniMessageMapper), NOT the
                    // hashCode-based threadId on FengniMessage.  They differ
                    // and notificationMgr.update() queries Realm by the
                    // TelephonyCompat threadId.
                    val realmThreadId = realmMessage?.threadId ?: fengniMsg.threadId

                    // Post-sync side effects only for incoming messages
                    // (not for cross-device outgoing sync — no notification needed)
                    if (topicType == "incoming") {
                        notificationMgr.update(realmThreadId)
                        shortcutManager.updateShortcuts()
                        shortcutManager.getOrCreateShortcut(realmThreadId)
                        updateBadge.execute(Unit)
                    }

                    Timber.d("${topicType?.capitalize()} SMS from $sourceDevice: $address")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse MQTT message")
                }
            }
        } catch (e: InterruptedException) {
            // Shutting down
        } catch (e: Exception) {
            Timber.e(e, "Message loop error")
        }
    }

    // ── Outgoing publish ──

    private fun handlePublish(intent: Intent) {
        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
        val body = intent.getStringExtra(EXTRA_BODY) ?: return
        val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        val subId = intent.getIntExtra(EXTRA_SUB_ID, -1)
        enqueueOutgoing(address, body, timestamp, subId)
    }

    /**
     * Store outgoing message locally and attempt MQTT publish.
     * Always persists first — the message is never lost even if MQTT is offline.
     *
     * @return true if MQTT publish succeeded, false otherwise (message still persisted).
     */
    private fun enqueueOutgoing(
        address: String,
        body: String,
        timestamp: Long,
        subId: Int
    ): Boolean {
        val config = configManager.getConfig()
        // Normalize for consistent threadId across all code paths.
        val normalizedAddress = phoneUtils.normalizeNumber(address)
        val threadId = normalizedAddress.hashCode().toLong()
        val deviceId = config?.deviceId ?: "local"

        // Always persist first — don't lose messages if MQTT is offline
        val msg = FengniMessage(
            id = messageStore.newId(),
            address = address,
            body = body,
            date = timestamp,
            threadId = threadId,
            subId = subId,
            type = FengniMessage.TYPE_SMS,
            boxId = FengniMessage.BOX_OUTBOX,
            read = true,
            seen = true,
            sourceDevice = deviceId,
            forwardStatus = FengniMessage.STATUS_PENDING,
            isOutgoing = true
        )
        messageStore.insert(msg)

        // Best-effort MQTT publish
        if (config != null && mqttClient != null) {
            try {
                val payload = JSONObject().apply {
                    put("address", address)
                    put("body", body)
                    put("date", timestamp)
                    put("threadId", threadId)
                    put("subId", subId)
                }.toString()

                mqttClient?.publishWith()
                    ?.topic(FengniConfig.outgoingTopic(config.customerId, config.deviceId))
                    ?.qos(MqttQos.AT_LEAST_ONCE)
                    ?.payload(payload.toByteArray(Charsets.UTF_8))
                    ?.send()

                messageStore.updateBoxId(msg.id, FengniMessage.BOX_SENT)
                Timber.d("Published outgoing SMS: $address")
                return true
            } catch (e: Exception) {
                Timber.e(e, "Failed to publish MQTT message")
                return false
            }
        }

        // MQTT not connected — message persisted, will be sent on reconnection
        Timber.w("MQTT not connected, outgoing SMS persisted: $address")
        return false

        // Realm sync is handled by the send flow (syncProviderMessage + markSent).
        // Do NOT call syncMessage() here — it would create a duplicate Realm Message.
    }

    /**
     * Forward an incoming SMS (received via cellular) to remote devices.
     * Stores locally as BOX_INBOX and publishes to the incoming MQTT topic
     * so other devices subscribed to customer/{id}/device/+/sms/incoming receive it.
     */
    private fun forwardIncomingSms(
        address: String,
        body: String,
        timestamp: Long,
        subId: Int
    ) {
        val config = configManager.getConfig()
        // Normalize address for consistent threadId — must match
        // ConversationRepositoryImpl.createConversation() fallback.
        val normalizedAddress = phoneUtils.normalizeNumber(address)
        val threadId = normalizedAddress.hashCode().toLong()
        val deviceId = config?.deviceId ?: "local"

        val msgId = messageStore.newId()
        messageStore.insert(
            FengniMessage(
                id = msgId,
                address = address,
                body = body,
                date = timestamp,
                threadId = threadId,
                subId = subId,
                type = FengniMessage.TYPE_SMS,
                boxId = FengniMessage.BOX_INBOX,
                read = false,
                seen = false,
                sourceDevice = deviceId,
                forwardStatus = FengniMessage.STATUS_SENT,
                isOutgoing = false
            )
        )

        if (config != null && mqttClient != null) {
            try {
                val payload = JSONObject().apply {
                    put("address", address)
                    put("body", body)
                    put("date", timestamp)
                    put("threadId", threadId)
                    put("subId", subId)
                }.toString()

                mqttClient?.publishWith()
                    ?.topic(FengniConfig.incomingPublishTopic(config.customerId, config.deviceId))
                    ?.qos(MqttQos.AT_LEAST_ONCE)
                    ?.payload(payload.toByteArray(Charsets.UTF_8))
                    ?.send()

                Timber.d("Forwarded incoming SMS to MQTT: $address")
            } catch (e: Exception) {
                Timber.e(e, "Failed to forward incoming SMS via MQTT")
            }
        }

        // Incremental sync — won't be skipped by guard in syncMessages()
        val realmMessage = syncRepository.syncMessage(android.net.Uri.EMPTY, msgId)
        // Use the threadId from the Realm Message (computed by
        // TelephonyCompat inside FengniMessageMapper), NOT the
        // hashCode-based threadId.  They differ and notificationMgr
        // queries Realm by the TelephonyCompat threadId.
        val realmThreadId = realmMessage?.threadId ?: threadId

        // Post-sync side effects for incoming messages from ContentObserver
        notificationMgr.update(realmThreadId)
        shortcutManager.updateShortcuts()
        shortcutManager.getOrCreateShortcut(realmThreadId)
        updateBadge.execute(Unit)
    }

    // ── SMS ContentObserver ──

    private fun getCurrentMaxSmsId(): Long {
        return try {
            val cursor = contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("_id"),
                null, null,
                "_id DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun checkNewSms() {
        try {
            val cursor = contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("_id", "address", "body", "date", "sub_id"),
                "_id > ?",
                arrayOf(lastProcessedSmsId.toString()),
                "_id ASC"
            ) ?: return

            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val address = it.getString(1) ?: continue
                    val body = it.getString(2) ?: ""
                    val date = it.getLong(3)
                    val subId = it.getInt(4)

                    Timber.d("ContentObserver: new SMS _id=$id from=$address")
                    forwardIncomingSms(address, body, date, subId)

                    if (id > lastProcessedSmsId) lastProcessedSmsId = id
                }
            }
            smsPrefs.edit().putLong("last_sms_id", lastProcessedSmsId).apply()
        } catch (e: SecurityException) {
            Timber.w("READ_SMS permission not granted; cannot observe SMS database")
        } catch (e: Exception) {
            Timber.e(e, "checkNewSms error")
        }
    }

    // ── Foreground notification ──

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "MQTTF sync service"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fquik Sync")
            .setContentText("Fquik message sync is active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun updateNotification(onlineCount: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = createNotification().let {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Fquik Sync")
                .setContentText("Fquik · $onlineCount 人在线")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this, 0,
                        packageManager.getLaunchIntentForPackage(packageName),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                .build()
        }
        nm.notify(NOTIFICATION_ID, notification)
    }
}
