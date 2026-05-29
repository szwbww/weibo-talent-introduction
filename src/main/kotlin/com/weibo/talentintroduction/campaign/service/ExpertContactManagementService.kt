package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.ExpertContactStatusHistory
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactStatusHistoryRepository
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.document.domain.ExpertDocument
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
    private val conversationStateService: ConversationStateService
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
            )
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
        conversationStateService.transition(
            contact = contact,
            toStatus = ConversationStatus.MANUAL_HANDOFF,
            reason = "CREATE_MANUAL_HANDOFF:${command.reason}",
            source = "MANUAL",
            now = now
        ) {
            it.copy(manualHandoffRequired = true)
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
        val now = LocalDateTime.now()
        val completed = manualHandoffRepository.save(
            existing.copy(
                handoffStatus = "COMPLETED",
                note = command.note ?: existing.note,
                updatedAt = now
            )
        )
        val contact = getContact(contactId)
        val nextStatus = command.nextStatus?.let(ConversationStatus::fromName)
            ?: ConversationStatus.fromName(contact.currentStatus)
        conversationStateService.transition(
            contact = contact,
            toStatus = nextStatus,
            reason = "COMPLETE_MANUAL_HANDOFF",
            source = "MANUAL",
            now = now
        ) {
            it.copy(manualHandoffRequired = false)
        }
        return completed
    }

    fun closeContact(contactId: Long, reason: String): ExpertContact {
        require(reason.isNotBlank()) { "reason is required" }
        val contact = getContact(contactId)
        return conversationStateService.transition(
            contact = contact,
            toStatus = ConversationStatus.CLOSED,
            reason = "CLOSE_CONTACT:$reason",
            source = "MANUAL"
        ) {
            it.copy(
                manualHandoffRequired = false,
                closedReason = reason
            )
        }
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
    val recommendedNextAction: String
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
    val note: String?
)
