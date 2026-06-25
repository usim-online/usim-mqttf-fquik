package com.moez.QKSMS.fengni

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent store for fengni-synced messages.
 *
 * Backed by an in-memory ConcurrentHashMap for fast access,
 * synchronized with a JSON file in internal storage for persistence
 * across process restarts.
 */
@Singleton
class FengniMessageStore @Inject constructor(
    private val context: Context
) {

    private val messages = ConcurrentHashMap<Long, FengniMessage>()
    private var nextId: Long = 1L

    private val file: File
        get() = File(context.filesDir, FILE_NAME)

    init {
        loadFromDisk()
    }

    // ── Public API (unchanged signatures) ──

    fun getAllMessages(): List<FengniMessage> {
        return messages.values.toList().sortedByDescending { it.date }
    }

    fun getMessageById(id: Long): FengniMessage? {
        return messages[id]
    }

    fun getConversationThreadIds(): List<Long> {
        return messages.values.map { it.threadId }.distinct()
    }

    fun getMessagesByThreadId(threadId: Long): List<FengniMessage> {
        return messages.values
            .filter { it.threadId == threadId }
            .sortedByDescending { it.date }
    }

    fun getDistinctAddresses(): List<String> {
        return messages.values
            .map { it.address }
            .distinct()
            .filter { it.isNotEmpty() }
    }

    fun newId(): Long {
        return nextId++
    }

    fun insert(message: FengniMessage) {
        val id = if (message.id > 0) message.id else newId()
        messages[id] = message.copy(id = id)
        persist()
    }

    fun updateBoxId(id: Long, boxId: Int) {
        messages[id]?.let { existing ->
            messages[id] = existing.copy(boxId = boxId)
            persist()
        }
    }

    fun updateRead(id: Long, read: Boolean, seen: Boolean) {
        messages[id]?.let { existing ->
            messages[id] = existing.copy(read = read, seen = seen)
            persist()
        }
    }

    fun markReadByThreadIds(threadIds: Collection<Long>) {
        val threadIdSet = threadIds.toSet()
        var changed = false
        for ((id, msg) in messages) {
            if (msg.threadId in threadIdSet && (!msg.read || !msg.seen)) {
                messages[id] = msg.copy(read = true, seen = true)
                changed = true
            }
        }
        if (changed) persist()
    }

    fun updateForwardStatus(id: Long, forwardStatus: String) {
        messages[id]?.let { existing ->
            messages[id] = existing.copy(forwardStatus = forwardStatus)
            persist()
        }
    }

    fun getPendingMessages(): List<FengniMessage> {
        return messages.values.filter { it.forwardStatus == FengniMessage.STATUS_PENDING }
    }

    fun insertAll(messageList: List<FengniMessage>) {
        messageList.forEach { insert(it) }
    }

    fun deleteAll() {
        messages.clear()
        nextId = 1L
        persist()
    }

    fun count(): Long {
        return messages.size.toLong()
    }

    // ── Persistence ──

    private fun persist() {
        try {
            val json = JSONArray()
            for (msg in messages.values) {
                json.put(msg.toJson())
            }
            file.writeText(json.toString())
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist message store")
        }
    }

    private fun loadFromDisk() {
        try {
            if (!file.exists()) return

            val raw = file.readText()
            val json = JSONArray(raw)
            var maxId = 0L

            for (i in 0 until json.length()) {
                val msg = FengniMessage.fromJson(json.getJSONObject(i))
                messages[msg.id] = msg
                if (msg.id > maxId) maxId = msg.id
            }

            nextId = maxId + 1
            Timber.d("Loaded ${messages.size} messages from disk, nextId=$nextId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load message store, starting fresh")
        }
    }

    companion object {
        private const val FILE_NAME = "fengni_messages.json"

        // ── JSON serialization (no extra dependencies) ──

        private fun FengniMessage.toJson(): JSONObject {
            return JSONObject().apply {
                put("id", id)
                put("address", address)
                put("body", body)
                put("date", date)
                put("threadId", threadId)
                put("subId", subId)
                put("type", type)
                put("boxId", boxId)
                put("read", read)
                put("seen", seen)
                put("sourceDevice", sourceDevice)
                put("forwardStatus", forwardStatus)
                put("isOutgoing", isOutgoing)
            }
        }

        private fun FengniMessage.Companion.fromJson(obj: JSONObject): FengniMessage {
            return FengniMessage(
                id = obj.optLong("id", 0),
                address = obj.optString("address", ""),
                body = obj.optString("body", ""),
                date = obj.optLong("date", 0),
                threadId = obj.optLong("threadId", 0),
                subId = obj.optInt("subId", -1),
                type = obj.optString("type", TYPE_SMS),
                boxId = obj.optInt("boxId", BOX_INBOX),
                read = obj.optBoolean("read", false),
                seen = obj.optBoolean("seen", false),
                sourceDevice = obj.optString("sourceDevice", ""),
                forwardStatus = obj.optString("forwardStatus", STATUS_SENT),
                isOutgoing = obj.optBoolean("isOutgoing", false)
            )
        }
    }
}
