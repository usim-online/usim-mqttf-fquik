package com.moez.QKSMS.fengni

/**
 * Decrypted fengni MQTT connection configuration.
 */
data class FengniConfig(
    val remoteHost: String,
    val remotePort: Int,
    val serverPubKey: String,
    val routePubKey: String,       // 64 hex chars, routing public key
    val groupId: Int,              // group identifier (1-65535)
    val customerId: String,
    val deviceId: String,
    val hmacKey: String = "",     // 64 hex chars (256-bit), empty = no admission
    val paddingMax: Int = 64      // random padding max bytes, 0 = no padding
) {
    companion object {
        /** MQTT topic pattern for subscribing to incoming SMS from all devices */
        const val TOPIC_INCOMING = "customer/%s/device/+/sms/incoming"
        /** MQTT topic for publishing outgoing SMS to remote broker */
        const val TOPIC_OUTGOING = "customer/%s/device/%s/sms/outgoing"

        fun incomingTopic(customerId: String) = TOPIC_INCOMING.format(customerId)
        fun outgoingTopic(customerId: String, deviceId: String) = TOPIC_OUTGOING.format(customerId, deviceId)
        /** Concrete topic for publishing incoming SMS (replaces + with actual deviceId) */
        fun incomingPublishTopic(customerId: String, deviceId: String) =
            "customer/$customerId/device/$deviceId/sms/incoming"
    }
}
