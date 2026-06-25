package com.moez.QKSMS.fengni

/**
 * Local record of a fengni-forwarded SMS message.
 *
 * Populated by FengniMqttService when receiving synced messages from remote devices.
 * Used by FengniSyncRepository as the data source (replacing Android ContentProvider).
 */
data class FengniMessage(
    val id: Long = 0,
    /** Original sender phone number (e.g. "10086") */
    val address: String = "",
    /** Message body with optional device source tag */
    val body: String = "",
    /** Timestamp from original message */
    val date: Long = 0,
    /** Android Telephony.Threads thread ID (grouped by address) */
    val threadId: Long = 0,
    /** SIM subscription ID from source device */
    val subId: Int = -1,
    /** Message type: "sms" or "mms" */
    val type: String = "sms",
    /** INBOX=1, SENT=2 */
    val boxId: Int = 1,
    /** Whether the message has been read */
    val read: Boolean = false,
    /** Whether the message has been seen */
    val seen: Boolean = false,
    /** Source device identifier for Model C: [设备1] label */
    val sourceDevice: String = "",
    /** Forwarding status: PENDING, SENT, FAILED */
    val forwardStatus: String = "SENT",
    /** Whether this message was sent from this device (true) or received from remote (false) */
    val isOutgoing: Boolean = false
) {
    companion object {
        const val TYPE_SMS = "sms"
        const val TYPE_MMS = "mms"

        const val BOX_INBOX = 1
        const val BOX_SENT = 2
        const val BOX_OUTBOX = 4

        const val STATUS_PENDING = "PENDING"
        const val STATUS_SENT = "SENT"
        const val STATUS_FAILED = "FAILED"
    }
}
