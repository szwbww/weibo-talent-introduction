package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
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
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
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
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val operatorActionLogService: OperatorActionLogService
) {
    fun listContacts(campaignId: Long?, status: String?, operatorStatus: String? = null, needsAttention: Boolean? = null): List<ExpertContact> =
        expertContactRepository.findFilteredContacts(campaignId, status, operatorStatus, needsAttention)

    fun getContactDetail(contactId: Long): ExpertContactDetail {
        val contact = getContact(contactId)
        val mails = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
        val attachments = mails
            .mapNotNull { it.id }
            .flatMap(mailAttachmentRepository::findAllByMailRecordIdOrderByCreatedAtAsc)
        val latestManualMailProcessing = inboundMailProcessingRepository
            .findFirstByExpertContactIdAndProcessStatusOrderByReceivedAtDesc(contactId, "MANUAL_REVIEW")
        return ExpertContactDetail(
            contact = contact,
            mails = mails,
            attachments = attachments,
            documents = expertDocumentRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId),
            latestHandoff = manualHandoffRepository.findFirstByExpertContactIdAndHandoffStatusInOrderByUpdatedAtDesc(
                contactId, listOf("PENDING", "ASSIGNED")
            ),
            statusHistory = statusHistoryRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId),
            recommendedNextAction = conversationStateService.recommendedNextAction(
                contact.currentStatus,
                contact.manualHandoffRequired
            ),
            meetingSchedules = meetingScheduleRepository.findAllByExpertContactIdOrderByCreatedAtDesc(contactId),
            latestManualReviewReasonType = latestManualMailProcessing?.reasonType
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
        val existing = getLatestOpenHandoff(contactId)
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
        val existing = getLatestOpenHandoff(contactId)
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

    fun pauseAutoReply(contactId: Long, operatorName: String? = null): ExpertContact {
        val contact = getContact(contactId)
        val beforeState = mapOf(
            "currentStatus" to contact.currentStatus,
            "autoReplyEnabled" to contact.autoReplyEnabled.toString(),
            "manualHandoffRequired" to contact.manualHandoffRequired.toString(),
            "needsManualAttention" to contact.needsManualAttention.toString()
        )
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
        operatorActionLogService.record(
            targetType = "EXPERT_CONTACT",
            targetId = contactId,
            actionType = OperatorActionType.SWITCH_REPLY_MODE,
            expertContactId = contactId,
            before = beforeState,
            after = mapOf(
                "currentStatus" to updated.currentStatus,
                "autoReplyEnabled" to updated.autoReplyEnabled.toString(),
                "manualHandoffRequired" to updated.manualHandoffRequired.toString(),
                "needsManualAttention" to updated.needsManualAttention.toString()
            ),
            operatorName = operatorName,
            note = "pause auto-reply"
        )
        return updated
    }

    fun resumeAutoReply(contactId: Long, operatorName: String? = null): ExpertContact {
        val contact = getContact(contactId)
        val beforeState = mapOf(
            "currentStatus" to contact.currentStatus,
            "autoReplyEnabled" to contact.autoReplyEnabled.toString(),
            "manualHandoffRequired" to contact.manualHandoffRequired.toString(),
            "needsManualAttention" to contact.needsManualAttention.toString()
        )
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
        operatorActionLogService.record(
            targetType = "EXPERT_CONTACT",
            targetId = contactId,
            actionType = OperatorActionType.SWITCH_REPLY_MODE,
            expertContactId = contactId,
            before = beforeState,
            after = mapOf(
                "currentStatus" to updated.currentStatus,
                "autoReplyEnabled" to updated.autoReplyEnabled.toString(),
                "manualHandoffRequired" to updated.manualHandoffRequired.toString(),
                "needsManualAttention" to updated.needsManualAttention.toString()
            ),
            operatorName = operatorName,
            note = "resume auto-reply"
        )
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
        ConversationStatus.COMPANY_MATCHED
    )

    fun promoteToApplication(contactId: Long): ExpertContact {
        val contact = getContact(contactId)
        if (contact.applicationIndexed) return contact
        require(contact.orcidId.isNotBlank()) { "Contact has no ORCID" }
        val firstReplyInstant = if (contact.firstReplyAt != null) {
            contact.firstReplyAt.toInstant(java.time.ZoneId.systemDefault().rules.getOffset(contact.firstReplyAt))
        } else {
            java.time.Instant.now()
        }
        val ok = expertIndexWriterService.promoteToApplication(
            orcid = contact.orcidId,
            contact = contact,
            firstReplyAt = firstReplyInstant,
            triggeredBy = TriggeredBy.OPERATOR
        )
        require(ok) { "Failed to promote to application index" }
        val updated = expertContactRepository.save(contact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION"))
        return updated
    }

    @org.springframework.transaction.annotation.Transactional
    fun promoteToCandidate(contactId: Long): ExpertContact {
        val contact = getContact(contactId)
        require(contact.currentIndexLevel == "RAW") { "Only RAW contact can be promoted to CANDIDATE" }
        val ok = expertIndexWriterService.promoteToCandidate(contact.orcidId, contact)
        require(ok) { "Failed to promote contact $contactId to CANDIDATE in ES" }
        return expertContactRepository.save(contact.copy(currentIndexLevel = "CANDIDATE"))
    }

    @org.springframework.transaction.annotation.Transactional
    fun demoteToRaw(contactId: Long): ExpertContact {
        val contact = getContact(contactId)
        require(contact.currentIndexLevel != "RAW") { "Contact already in RAW" }
        val ok = expertIndexWriterService.demoteToRaw(contact.orcidId, contact)
        require(ok) { "Failed to demote contact $contactId to RAW in ES" }
        return expertContactRepository.save(contact.copy(
            currentIndexLevel = "RAW",
            applicationIndexed = false
        ))
    }

    @org.springframework.transaction.annotation.Transactional
    fun switchToManual(contactId: Long, reason: String?, note: String?, operatorName: String? = null): ExpertContact {
        val contact = getContact(contactId)
        val actualReason = reason ?: "OPERATOR_SWITCH_TO_MANUAL"
        val now = LocalDateTime.now()
        val beforeState = mapOf(
            "currentStatus" to contact.currentStatus,
            "autoReplyEnabled" to contact.autoReplyEnabled.toString(),
            "manualHandoffRequired" to contact.manualHandoffRequired.toString(),
            "needsManualAttention" to contact.needsManualAttention.toString()
        )
        ensureOpenManualHandoff(contactId, actualReason, note, now)
        if (contact.currentStatus == ConversationStatus.MANUAL_HANDOFF.name) {
            val fixedContact = if (contact.autoReplyEnabled || !contact.manualHandoffRequired) {
                expertContactRepository.save(
                    contact.copy(
                        autoReplyEnabled = false,
                        manualHandoffRequired = true
                    )
                )
            } else {
                contact
            }
            if (fixedContact.applicationIndexed) {
                expertIndexWriterService.syncApplicationStatus(fixedContact, actualReason)
            }
            operatorActionLogService.record(
                targetType = "EXPERT_CONTACT",
                targetId = contactId,
                actionType = OperatorActionType.SWITCH_REPLY_MODE,
                expertContactId = contactId,
                before = beforeState,
                after = mapOf(
                    "currentStatus" to fixedContact.currentStatus,
                    "autoReplyEnabled" to fixedContact.autoReplyEnabled.toString(),
                    "manualHandoffRequired" to fixedContact.manualHandoffRequired.toString(),
                    "needsManualAttention" to fixedContact.needsManualAttention.toString()
                ),
                operatorName = operatorName,
                note = note
            )
            return fixedContact
        }
        val updatedContact = conversationStateService.transition(
            contact = contact,
            toStatus = ConversationStatus.MANUAL_HANDOFF,
            reason = actualReason,
            source = "OPERATOR",
            now = now
        ) {
            it.copy(autoReplyEnabled = false, manualHandoffRequired = true)
        }
        if (updatedContact.applicationIndexed) {
            expertIndexWriterService.syncApplicationStatus(updatedContact, actualReason)
        }
        operatorActionLogService.record(
            targetType = "EXPERT_CONTACT",
            targetId = contactId,
            actionType = OperatorActionType.SWITCH_REPLY_MODE,
            expertContactId = contactId,
            before = beforeState,
            after = mapOf(
                "currentStatus" to updatedContact.currentStatus,
                "autoReplyEnabled" to updatedContact.autoReplyEnabled.toString(),
                "manualHandoffRequired" to updatedContact.manualHandoffRequired.toString(),
                "needsManualAttention" to updatedContact.needsManualAttention.toString()
            ),
            operatorName = operatorName,
            note = note
        )
        return updatedContact
    }

    private fun ensureOpenManualHandoff(
        contactId: Long,
        reason: String,
        note: String?,
        now: LocalDateTime
    ) {
        val openHandoffs = manualHandoffRepository.findAllByExpertContactIdAndHandoffStatusIn(
            contactId, listOf("PENDING", "ASSIGNED")
        )
        if (openHandoffs.isNotEmpty()) return
        manualHandoffRepository.save(
            ManualHandoff(
                expertContactId = contactId,
                reason = reason,
                handoffStatus = "PENDING",
                assignedTo = null,
                note = note,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    @org.springframework.transaction.annotation.Transactional
    fun switchToAuto(contactId: Long, note: String?, operatorName: String? = null): ExpertContact {
        val contact = getContact(contactId)
        if (contact.currentStatus != ConversationStatus.MANUAL_HANDOFF.name) {
            error("Contact $contactId is not in MANUAL_HANDOFF")
        }
        val beforeState = mapOf(
            "currentStatus" to contact.currentStatus,
            "autoReplyEnabled" to contact.autoReplyEnabled.toString(),
            "manualHandoffRequired" to contact.manualHandoffRequired.toString(),
            "needsManualAttention" to contact.needsManualAttention.toString()
        )
        val now = LocalDateTime.now()
        val openHandoffs = manualHandoffRepository.findAllByExpertContactIdAndHandoffStatusIn(
            contactId, listOf("PENDING", "ASSIGNED")
        )
        openHandoffs.forEach { handoff ->
            manualHandoffRepository.save(
                handoff.copy(
                    handoffStatus = "COMPLETED",
                    note = if (note.isNullOrBlank()) handoff.note else note,
                    updatedAt = now
                )
            )
        }
        val updatedContact = conversationStateService.transition(
            contact = contact,
            toStatus = ConversationStatus.WAITING_REPLY,
            reason = "OPERATOR_SWITCH_TO_AUTO",
            source = "OPERATOR",
            now = now
        ) {
            it.copy(autoReplyEnabled = true, needsManualAttention = false, manualHandoffRequired = false)
        }
        if (updatedContact.applicationIndexed) {
            expertIndexWriterService.syncApplicationStatus(updatedContact, "OPERATOR_SWITCH_TO_AUTO")
        }
        operatorActionLogService.record(
            targetType = "EXPERT_CONTACT",
            targetId = contactId,
            actionType = OperatorActionType.SWITCH_REPLY_MODE,
            expertContactId = contactId,
            before = beforeState,
            after = mapOf(
                "currentStatus" to updatedContact.currentStatus,
                "autoReplyEnabled" to updatedContact.autoReplyEnabled.toString(),
                "manualHandoffRequired" to updatedContact.manualHandoffRequired.toString(),
                "needsManualAttention" to updatedContact.needsManualAttention.toString()
            ),
            operatorName = operatorName,
            note = note
        )
        return updatedContact
    }

    private fun getContact(contactId: Long): ExpertContact =
        expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }

    private fun getLatestOpenHandoff(contactId: Long): ManualHandoff =
        manualHandoffRepository.findFirstByExpertContactIdAndHandoffStatusInOrderByUpdatedAtDesc(
            contactId, listOf("PENDING", "ASSIGNED")
        ) ?: error("No open manual handoff found for expert contact: $contactId")

    fun getAutoReplySummary(): AutoReplySummary {
        val allContacts = expertContactRepository.findAll().toList()
        val total = allContacts.size
        val enabled = allContacts.count { it.autoReplyEnabled }
        val disabled = allContacts.count { !it.autoReplyEnabled }
        val handoffLocked = allContacts.count { !it.autoReplyEnabled && it.manualHandoffRequired }
        return AutoReplySummary(
            total = total,
            enabled = enabled,
            disabled = disabled,
            handoffLocked = handoffLocked
        )
    }

    fun bulkUpdateAutoReply(enabled: Boolean, operatorName: String? = null): BulkAutoReplyResult {
        val allContacts = expertContactRepository.findAll().toList()
        var updated = 0
        var skipped = 0
        allContacts.forEach { contact ->
            if (enabled) {
                if (!contact.autoReplyEnabled) {
                    if (!contact.manualHandoffRequired) {
                        resumeAutoReply(contact.id ?: error("contact id is null"), operatorName)
                        updated++
                    } else {
                        skipped++
                    }
                }
            } else {
                if (contact.autoReplyEnabled) {
                    pauseAutoReply(contact.id ?: error("contact id is null"), operatorName)
                    updated++
                }
            }
        }
        return BulkAutoReplyResult(updated = updated, skipped = skipped)
    }
}

data class AutoReplySummary(
    val total: Int,
    val enabled: Int,
    val disabled: Int,
    val handoffLocked: Int
)

data class BulkAutoReplyResult(
    val updated: Int,
    val skipped: Int
)

data class ExpertContactDetail(
    val contact: ExpertContact,
    val mails: List<MailRecord>,
    val attachments: List<MailAttachment>,
    val documents: List<ExpertDocument>,
    val latestHandoff: ManualHandoff?,
    val statusHistory: List<ExpertContactStatusHistory>,
    val recommendedNextAction: String,
    val meetingSchedules: List<MeetingSchedule>,
    val latestManualReviewReasonType: String? = null
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
