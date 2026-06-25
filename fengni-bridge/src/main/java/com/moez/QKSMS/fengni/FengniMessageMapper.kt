package com.moez.QKSMS.fengni

import android.content.Context
import android.provider.Telephony
import dev.octoshrimpy.quik.compat.TelephonyCompat
import dev.octoshrimpy.quik.manager.KeyManager
import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.model.Recipient
import dev.octoshrimpy.quik.util.PhoneNumberUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps FengniMessage records to Quik's existing Realm domain models.
 *
 * This replaces the CursorToMessage/CursorToConversation/CursorToRecipient mappers
 * that previously consumed Android ContentProvider cursors.
 */
@Singleton
class FengniMessageMapper @Inject constructor(
    private val context: Context,
    private val keys: KeyManager,
    private val phoneNumberUtils: PhoneNumberUtils
) {

    /**
     * Convert a FengniMessage to a Quik Message entity (RealmObject).
     * Applies Model C: prepends [sourceDevice] tag to body for incoming messages.
     */
    fun toMessage(fengni: FengniMessage): Message {
        val body = if (fengni.sourceDevice.isNotEmpty() && !fengni.isOutgoing) {
            "[${fengni.sourceDevice}] ${fengni.body}"
        } else {
            fengni.body
        }

        // Normalize threadId from ContentProvider so that
        // Conversation lookups and RealmResults auto-refresh work correctly.
        // Use fengni.id as the Realm primary key so that insertOrUpdate
        // matches the same message across syncMessages() rebuilds.
        // Previously keys.newId() generated a new id each time, causing
        // read/seen state to be lost on re-sync.
        return Message().apply {
            id = fengni.id
            threadId = TelephonyCompat.getOrCreateThreadId(context, fengni.address)
            contentId = 0L
            address = fengni.address
            boxId = fengni.boxId
            type = fengni.type
            date = fengni.date
            dateSent = fengni.date
            seen = fengni.seen
            read = fengni.read
            this.body = body
            deliveryStatus = Telephony.Sms.STATUS_NONE
            subId = fengni.subId
        }
    }

    /**
     * Create a Quik Recipient from a phone number address.
     */
    fun toRecipient(address: String, id: Long, contacts: List<dev.octoshrimpy.quik.model.Contact>): Recipient {
        val contact = contacts.firstOrNull { c ->
            c.numbers.any { num -> phoneNumberUtils.compare(address, num.address) }
        }

        return Recipient().apply {
            this.id = id
            this.address = address
            this.contact = contact
        }
    }

    /**
     * Build a Conversation from messages for a given threadId.
     */
    fun toConversation(
        threadId: Long,
        messages: List<Message>,
        persisted: Conversation?
    ): Conversation {
        val lastMessage = messages.maxByOrNull { it.date }

        return Conversation(
            id = threadId,
            archived = persisted?.archived ?: false,
            blocked = persisted?.blocked ?: false,
            pinned = persisted?.pinned ?: false,
            name = persisted?.name ?: "",
            sendAsGroup = persisted?.sendAsGroup ?: true,
            lastMessage = lastMessage,
            draft = persisted?.draft ?: "",
            draftDate = persisted?.draftDate ?: 0L
        )
    }
}
