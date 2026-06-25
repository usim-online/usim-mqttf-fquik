package com.moez.QKSMS.fengni

import android.content.ContentResolver
import android.net.Uri
import dev.octoshrimpy.quik.extensions.insertOrUpdate
import dev.octoshrimpy.quik.extensions.map
import dev.octoshrimpy.quik.manager.KeyManager
import dev.octoshrimpy.quik.mapper.CursorToContact
import dev.octoshrimpy.quik.mapper.CursorToContactGroup
import dev.octoshrimpy.quik.mapper.CursorToContactGroupMember
import dev.octoshrimpy.quik.model.Contact
import dev.octoshrimpy.quik.model.ContactGroup
import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.model.PhoneNumber
import dev.octoshrimpy.quik.model.Recipient
import dev.octoshrimpy.quik.model.SyncLog
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.SyncRepository
import dev.octoshrimpy.quik.util.PhoneNumberUtils
import dev.octoshrimpy.quik.util.tryOrNull
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import io.realm.Realm
import io.realm.RealmList
import io.realm.Sort
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fengni-backed replacement for SyncRepositoryImpl.
 *
 * Instead of reading from Android ContentProvider,
 * this reads from FengniMessageStore (populated by FengniMqttService).
 *
 * The output (Realm Message/Conversation/Recipient/Contact entities)
 * remains identical — Quik's UI layer is unchanged.
 */
@Singleton
class FengniSyncRepository @Inject constructor(
    private val contentResolver: ContentResolver,
    private val conversationRepo: ConversationRepository,
    private val fengniStore: FengniMessageStore,
    private val fengniMapper: FengniMessageMapper,
    private val cursorToContact: CursorToContact,
    private val cursorToContactGroup: CursorToContactGroup,
    private val cursorToContactGroupMember: CursorToContactGroupMember,
    private val keys: KeyManager,
    private val phoneNumberUtils: PhoneNumberUtils,
) : SyncRepository {

    override val syncProgress: Observable<SyncRepository.SyncProgress> =
        BehaviorSubject.createDefault(SyncRepository.SyncProgress.Idle)

    override fun syncMessages() {
        val subject = syncProgress as BehaviorSubject

        if (subject.value is SyncRepository.SyncProgress.Running) return

        val allMessages = fengniStore.getAllMessages()
        val totalCount = allMessages.size
        subject.onNext(SyncRepository.SyncProgress.Running(totalCount, 0, false))

        try {
            Realm.getDefaultInstance().executeTransaction { realm ->
                // Preserve conversation metadata (archived/blocked/pinned)
                val persistedData = realm.copyFromRealm(
                    realm.where(Conversation::class.java)
                        .beginGroup()
                        .equalTo("archived", true)
                        .or()
                        .equalTo("blocked", true)
                        .or()
                        .equalTo("pinned", true)
                        .or()
                        .isNotEmpty("name")
                        .endGroup()
                        .findAll()
                ).associateBy { it.id }.toMutableMap()

                // Upsert messages from fengni store (no delete-all)
                val messages = ArrayList<Message>()
                var progress = 0
                val fengniIds = mutableSetOf<Long>()

                for (fengniMsg in allMessages) {
                    try {
                        val message = fengniMapper.toMessage(fengniMsg)
                        realm.insertOrUpdate(message)
                        messages.add(message)
                        fengniIds.add(message.id)
                        progress++
                        subject.onNext(
                            SyncRepository.SyncProgress.Running(totalCount, progress, false)
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to map fengni message ${fengniMsg.id}")
                    }
                }

                // Remove Realm messages that no longer exist in FengniMessageStore
                if (fengniIds.isNotEmpty()) {
                    realm.where(Message::class.java)
                        .not().`in`("id", fengniIds.toTypedArray())
                        .findAll()
                        .deleteAllFromRealm()
                } else if (allMessages.isEmpty()) {
                    realm.delete(Message::class.java)
                }

                // Sync conversations grouped by threadId
                val contacts = realm.copyToRealmOrUpdate(getContacts())
                val threadGroups = messages.groupBy { it.threadId }

                for ((threadId, threadMessages) in threadGroups) {
                    try {
                        val conversation = fengniMapper.toConversation(
                            threadId,
                            threadMessages,
                            persistedData[threadId]
                        )

                        // Add recipients for this conversation
                        val addresses = threadMessages
                            .map { it.address }
                            .distinct()
                            .filter { it.isNotEmpty() }

                        val recipients = addresses.mapIndexed { index, address ->
                            fengniMapper.toRecipient(address, keys.newId(), contacts)
                        }
                        conversation.recipients = RealmList<Recipient>().apply {
                            addAll(recipients)
                        }

                        conversation.lastMessage = realm.where(Message::class.java)
                            .sort("date", Sort.DESCENDING)
                            .equalTo("threadId", threadId)
                            .findFirst()

                        realm.insertOrUpdate(conversation)
                        recipients.forEach { realm.insertOrUpdate(it) }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to create conversation for thread $threadId")
                    }
                }

                realm.insert(SyncLog())
            }
        } catch (e: Exception) {
            Timber.e(e, "syncMessages failed")
        }
        subject.onNext(SyncRepository.SyncProgress.Idle)
    }

    override fun syncMessage(uri: Uri, messageId: Long): Message? {
        val fengniId = messageId.takeIf { it > 0 } ?: return null

        val fengniMsg = fengniStore.getMessageById(fengniId) ?: return null
        val message = fengniMapper.toMessage(fengniMsg)

        // Synchronous Realm write — callers must not be on the main thread.
        // After this returns, the Message/Conversation are committed and
        // visible to any subsequent Realm query (e.g. notification update).
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction { r ->
                r.insertOrUpdate(message)

                val conversation = r.where(Conversation::class.java)
                    .equalTo("id", message.threadId)
                    .findFirst()
                if (conversation == null) {
                    val contacts = r.where(Contact::class.java).findAll()
                    val recipient = fengniMapper.toRecipient(message.address, keys.newId(), contacts)
                    val conv = fengniMapper.toConversation(message.threadId, listOf(message), null)
                    conv.recipients = RealmList<Recipient>().apply { add(recipient) }
                    conv.lastMessage = r.where(Message::class.java)
                        .sort("date", Sort.DESCENDING)
                        .equalTo("threadId", message.threadId)
                        .findFirst()
                    r.insertOrUpdate(conv)
                    r.insertOrUpdate(recipient)
                } else {
                    conversation.lastMessage = r.where(Message::class.java)
                        .sort("date", Sort.DESCENDING)
                        .equalTo("threadId", message.threadId)
                        .findFirst()
                }
            }
        }

        return message
    }

    override fun syncContacts() {
        // Contact sync delegates to the Android system contacts.
        // For fengni operation, contacts are linked to Recipients when messages are synced.
    }

    override fun markRead(threadIds: Collection<Long>) {
        fengniStore.markReadByThreadIds(threadIds)
    }

    private fun getContacts(): List<Contact> {
        val defaultNumberIds = Realm.getDefaultInstance().use { realm ->
            realm.where(PhoneNumber::class.java)
                .equalTo("isDefault", true)
                .findAll()
                .map { it.id }
        }

        return cursorToContact.getContactsCursor()
            ?.map { cursorToContact.map(it) }
            ?.groupBy { it.lookupKey }
            ?.map { (_, contacts) ->
                val uniqueNumbers = mutableListOf<PhoneNumber>()
                contacts.flatMap { it.numbers }.forEach { number ->
                    number.isDefault = defaultNumberIds.any { id -> id == number.id }
                    val duplicate = uniqueNumbers.find { other ->
                        phoneNumberUtils.compare(number.address, other.address)
                    }
                    if (duplicate == null) uniqueNumbers.add(number)
                    else if (!duplicate.isDefault && number.isDefault) duplicate.isDefault = true
                }
                contacts.first().apply {
                    numbers.clear()
                    numbers.addAll(uniqueNumbers)
                }
            } ?: listOf()
    }

    private fun getContactGroups(contacts: List<Contact>): List<ContactGroup> {
        val groupMembers = cursorToContactGroupMember.getGroupMembersCursor()
            ?.map { cursorToContactGroupMember.map(it) }
            .orEmpty()

        val groups = cursorToContactGroup.getContactGroupsCursor()
            ?.map { cursorToContactGroup.map(it) }
            .orEmpty()

        groups.forEach { group ->
            group.contacts.addAll(
                groupMembers
                    .filter { it.groupId == group.id }
                    .mapNotNull { member -> contacts.find { contact -> contact.lookupKey == member.lookupKey } }
            )
        }
        return groups
    }
}
