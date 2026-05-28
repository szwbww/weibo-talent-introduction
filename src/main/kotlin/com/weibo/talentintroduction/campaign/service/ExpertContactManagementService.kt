package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.handoff.domain.ManualHandoff
import com.weibo.talentintroduction.handoff.repository.ManualHandoffRepository
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ExpertContactManagementService(
    private val expertContactRepository: ExpertContactRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val manualHandoffRepository: ManualHandoffRepository
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
        return ExpertContactDetail(
            contact = contact,
            mails = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId),
            latestHandoff = manualHandoffRepository.findFirstByExpertContactIdOrderByUpdatedAtDesc(contactId)
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
        expertContactRepository.save(
            contact.copy(
                currentStatus = ConversationStatus.MANUAL_HANDOFF.name,
                manualHandoffRequired = true,
                updatedAt = now
            )
        )
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
        expertContactRepository.save(
            contact.copy(
                manualHandoffRequired = false,
                currentStatus = command.nextStatus ?: contact.currentStatus,
                updatedAt = now
            )
        )
        return completed
    }

    fun closeContact(contactId: Long, reason: String): ExpertContact {
        require(reason.isNotBlank()) { "reason is required" }
        val contact = getContact(contactId)
        return expertContactRepository.save(
            contact.copy(
                currentStatus = ConversationStatus.CLOSED.name,
                manualHandoffRequired = false,
                closedReason = reason,
                updatedAt = LocalDateTime.now()
            )
        )
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
    val latestHandoff: ManualHandoff?
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
