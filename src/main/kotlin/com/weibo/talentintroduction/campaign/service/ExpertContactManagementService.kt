package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.ExpertContactStatusHistory
import com.weibo.talentintroduction.campaign.domain.MeetingSchedule
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactStatusHistoryRepository
import com.weibo.talentintroduction.campaign.repository.MeetingScheduleRepository
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.document.domain.ExpertDocument
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.document.repository.ExpertDocumentRepository
import com.weibo.talentintroduction.handoff.domain.ManualHandoff
import com.weibo.talentintroduction.handoff.repository.ManualHandoffRepository
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ExpertContactManagementService(
    private val expertContactRepository: ExpertContactRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val manualHandoffRepository: ManualHandoffRepository,
    private val mailAttachmentRepository: MailAttachmentRepository,
    private val expertDocumentRepository: ExpertDocumentRepository,
    private val statusHistoryRepository: ExpertContactStatusHistoryRepository,
    private val conversationStateService: ConversationStateService,
    private val meetingScheduleRepository: MeetingScheduleRepository,
    private val expertIndexWriterService: ExpertIndexWriterService
) {
    fun listContacts(campaignId: Long?, status: String?): List<ExpertContact> =
        when {
            campaignId != null && status != null ->
                expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(campaignId, status)

            campaignId != null ->
                expertContactRepository.findAllByCampaignIdOrderByUpdatedAtDesc(campaignId)

            status != null ->
                expertContactRepository.findAllByCurrentStatusOrderByUpdatedAtDesc(status)

            else ->
                expertContactRepository.findAllByOrderByUpdatedAtDesc()
        }

    fun getContactDetail(contactId: Long): ExpertContactDetail {
        val contact = getContact(contactId)
        val mails = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
        val attachments = mails
            .mapNotNull { it.id }
            .flatMap(mailAttachmentRepository::findAllByMailRecordIdOrderByCreatedAtAsc)
        return ExpertContactDetail(
            contact = contact,
            mails = mails,
            attachments = attachments,
            documents = expertDocumentRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId),
            latestHandoff = manualHandoffRepository.findFirstByExpertContactIdOrderByUpdatedAtDesc(contactId),
            statusHistory = statusHistoryRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId),
            recommendedNextAction = conversationStateService.recommendedNextAction(
                contact.currentStatus,
                contact.manualHandoffRequired
            ),
            meetingSchedules = meetingScheduleRepository.findAllByExpertContactIdOrderByCreatedAtDesc(contactId)
        )
    }

    fun createManualHandoff(contactId: Long, command: ManualHandoffCreateCommand): ManualHandoff {
        val contact = getContact(contactId)
        require(command.reason.isNotBlank()) { "reason is required" }
        val now = LocalDateTime.now()
        val handoff = manualHandoffRepository.save(
            ManualHandoff(
                expertContactId = contactId,
                reason = command.reason,
                handoffStatus = "PENDING",
                assignedTo = command.assignedTo,
                note = command.note,
                createdAt = now,
                updatedAt = now
            )
        )
        val updatedContact = conversationStateService.transition(
            contact = contact,
            toStatus = ConversationStatus.MANUAL_HANDOFF,
            reason = "CREATE_MANUAL_HANDOFF:${command.reason}",
            source = "MANUAL",
            now = now
        ) {
            it.copy(manualHandoffRequired = true, autoReplyEnabled = false)
        }
        if (updatedContact.applicationIndexed) {
            expertIndexWriterService.syncApplicationStatus(updatedContact, "MANUAL_HANDOFF")
        }
        return handoff
    }

    fun assignHandoff(contactId: Long, command: ManualHandoffAssignCommand): ManualHandoff {
        require(command.assignedTo.isNotBlank()) { "assignedTo is required" }
        val existing = getLatestHandoff(contactId)
        return manualHandoffRepository.save(
            existing.copy(
                handoffStatus = "ASSIGNED",
                assignedTo = command.assignedTo,
                note = command.note ?: existing.note,
                updatedAt = LocalDateTime.now()
            )
        )
    }

    fun completeHandoff(contactId: Long, command: ManualHandoffCompleteCommand): ManualHandoff {
        val existing = getLatestHandoff(contactId)
        val contact = getContact(contactId)
        val now = LocalDateTime.now()
        val nextStatus = command.nextStatus?.let(ConversationStatus::fromName)
            ?: ConversationStatus.fromName(contact.currentStatus)
        require(nextStatus in allowedAfterManualStates) { "Invalid next status for handoff completion: $nextStatus" }
        val completed = manualHandoffRepository.save(
            existing.copy(
                handoffStatus = "COMPLETED",
                note = command.note ?: existing.note,
                updatedAt = now
            )
        )
        val updatedContact = conversationStateService.transition(
            contact = contact,
            toStatus = nextStatus,
            reason = "COMPLETE_MANUAL_HANDOFF",
            source = "MANUAL",
            now = now
        ) {
            var updated = it.copy(manualHandoffRequired = false)
            if (command.resumeAutoReply == true) {
                updated = updated.copy(autoReplyEnabled = true)
            }
            updated
        }
        if (updatedContact.applicationIndexed) {
            expertIndexWriterService.syncApplicationStatus(updatedContact)
        }
        return completed
    }

    fun pauseAutoReply(contactId: Long): ExpertContact {
        val contact = getContact(contactId)
        val now = LocalDateTime.now()
        val updated = conversationStateService.transition(
            contact = contact,
            toStatus = ConversationStatus.fromName(contact.currentStatus),
            reason = "pause auto-reply",
            source = "MANUAL_OPERATOR",
            now = now
        ) {
            it.copy(autoReplyEnabled = false)
        }
        if (updated.applicationIndexed) {
            expertIndexWriterService.syncApplicationStatus(updated, "AUTO_REPLY_PAUSED")
        }
        return updated
    }

    fun resumeAutoReply(contactId: Long): ExpertContact {
        val contact = getContact(contactId)
        val now = LocalDateTime.now()
        val updated = conversationStateService.transition(
            contact = contact,
            toStatus = ConversationStatus.fromName(contact.currentStatus),
            reason = "resume auto-reply",
            source = "MANUAL_OPERATOR",
            now = now
        ) {
            it.copy(autoReplyEnabled = true)
        }
        if (updated.applicationIndexed) {
            expertIndexWriterService.syncApplicationStatus(updated, "AUTO_REPLY_RESUMED")
        }
        return updated
    }

    private val allowedAfterManualStates = setOf(
        ConversationStatus.WAITING_REPLY,
        ConversationStatus.QA_AUTO_REPLIED,
        ConversationStatus.MEETING_SCHEDULING,
        ConversationStatus.MEETING_SCHEDULED,
        ConversationStatus.MEETING_DONE,
        ConversationStatus.MATERIALS_REQUESTED,
        ConversationStatus.MATERIALS_PARTIAL,
        ConversationStatus.MATERIALS_RECEIVED,
        ConversationStatus.COMPANY_MATCHED,
        ConversationStatus.CLOSED
    )

    fun completeManualReview(contactId: Long, command: ManualHandoffCompleteCommand): ExpertContact {
        val contact = getContact(contactId)
        require(
            contact.currentStatus in listOf(
                ConversationStatus.MANUAL_REVIEW.name,
                ConversationStatus.MANUAL_HANDOFF.name
            )
        ) { "Contact is not in a manual review or handoff state: ${contact.currentStatus}" }
        val now = LocalDateTime.now()
        val nextStatus = command.nextStatus?.let(ConversationStatus::fromName)
            ?: ConversationStatus.fromName(contact.currentStatus)
        require(nextStatus in allowedAfterManualStates) { "Invalid next status for manual completion: $nextStatus" }
        val updated = conversationStateService.transition(
            contact = contact,
            toStatus = nextStatus,
            reason = "COMPLETE_MANUAL_REVIEW",
            source = "MANUAL",
            now = now
        ) {
            var updated = it.copy(manualHandoffRequired = false)
            if (command.resumeAutoReply == true) {
                updated = updated.copy(autoReplyEnabled = true)
            }
            updated
        }
        if (updated.applicationIndexed) {
            expertIndexWriterService.syncApplicationStatus(updated)
        }
        return updated
    }

    fun promoteToApplication(contactId: Long): ExpertContact {
        val contact = getContact(contactId)
        if (contact.applicationIndexed) return contact
        require(contact.currentStatus != ConversationStatus.CLOSED.name) { "Cannot promote a closed contact" }
        require(contact.orcidId.isNotBlank()) { "Contact has no ORCID" }
        val firstReplyInstant = if (contact.firstReplyAt != null) {
            contact.firstReplyAt.toInstant(java.time.ZoneId.systemDefault().rules.getOffset(contact.firstReplyAt))
        } else {
            java.time.Instant.now()
        }
        val ok = expertIndexWriterService.promoteToApplication(
            orcid = contact.orcidId,
            contact = contact,
            firstReplyAt = firstReplyInstant
        )
        require(ok) { "Failed to promote to application index" }
        val updated = expertContactRepository.save(contact.copy(applicationIndexed = true))
        return updated
    }

    fun closeContact(contactId: Long, reason: String): ExpertContact {
        require(reason.isNotBlank()) { "reason is required" }
        val contact = getContact(contactId)
        val updated = conversationStateService.transition(
            contact = contact,
            toStatus = ConversationStatus.CLOSED,
            reason = "CLOSE_CONTACT:$reason",
            source = "MANUAL"
        ) {
            it.copy(
                manualHandoffRequired = false,
                autoReplyEnabled = false,
                closedReason = reason
            )
        }
        if (updated.applicationIndexed) {
            expertIndexWriterService.markApplicationClosed(updated)
        }
        return updated
    }

    private fun getContact(contactId: Long): ExpertContact =
        expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }

    private fun getLatestHandoff(contactId: Long): ManualHandoff =
        manualHandoffRepository.findFirstByExpertContactIdOrderByUpdatedAtDesc(contactId)
            ?: error("Manual handoff not found for expert contact: $contactId")
}

data class ExpertContactDetail(
    val contact: ExpertContact,
    val mails: List<MailRecord>,
    val attachments: List<MailAttachment>,
    val documents: List<ExpertDocument>,
    val latestHandoff: ManualHandoff?,
    val statusHistory: List<ExpertContactStatusHistory>,
    val recommendedNextAction: String,
    val meetingSchedules: List<MeetingSchedule>
)

data class ManualHandoffCreateCommand(
    val reason: String,
    val assignedTo: String?,
    val note: String?
)

data class ManualHandoffAssignCommand(
    val assignedTo: String,
    val note: String?
)

data class ManualHandoffCompleteCommand(
    val nextStatus: String?,
    val note: String?,
    val resumeAutoReply: Boolean? = null
)
