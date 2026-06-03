package com.weibo.talentintroduction.campaign.controller

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.ExpertContactStatusHistory
import com.weibo.talentintroduction.campaign.domain.MeetingSchedule
import com.weibo.talentintroduction.campaign.service.ExpertContactDetail
import com.weibo.talentintroduction.campaign.service.ExpertContactManagementService
import com.weibo.talentintroduction.campaign.service.ManualHandoffAssignCommand
import com.weibo.talentintroduction.campaign.service.ManualHandoffCompleteCommand
import com.weibo.talentintroduction.campaign.service.ManualHandoffCreateCommand
import com.weibo.talentintroduction.campaign.service.MeetingScheduleService
import com.weibo.talentintroduction.campaign.service.CreateMeetingCommand
import com.weibo.talentintroduction.campaign.service.UpdateMeetingCommand
import com.weibo.talentintroduction.campaign.service.ConfirmMeetingCommand
import com.weibo.talentintroduction.document.domain.ExpertDocument
import com.weibo.talentintroduction.handoff.domain.ManualHandoff
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.service.ManualExpertMailService
import com.weibo.talentintroduction.mail.service.ManualMailOption
import com.weibo.talentintroduction.mail.service.ManualMailSendCommand
import com.weibo.talentintroduction.mail.service.ManualMailSendResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/expert-contacts")
class ExpertContactManagementController(
    private val service: ExpertContactManagementService,
    private val manualExpertMailService: ManualExpertMailService,
    private val meetingScheduleService: MeetingScheduleService
) {
    @GetMapping
    fun listContacts(
        @RequestParam(required = false) campaignId: Long?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) needsAttention: Boolean?
    ): List<ExpertContactResponse> =
        service.listContacts(campaignId, status, needsAttention).map { it.toResponse() }

    @GetMapping("/{contactId}")
    fun getContactDetail(@PathVariable contactId: Long): ExpertContactDetailResponse =
        service.getContactDetail(contactId).toResponse()

    @PostMapping("/{contactId}/manual-handoff")
    fun createManualHandoff(
        @PathVariable contactId: Long,
        @RequestBody request: ManualHandoffCreateRequest
    ): ManualHandoffResponse =
        service.createManualHandoff(contactId, request.toCommand()).toResponse()

    @PostMapping("/{contactId}/manual-handoff/assign")
    fun assignManualHandoff(
        @PathVariable contactId: Long,
        @RequestBody request: ManualHandoffAssignRequest
    ): ManualHandoffResponse =
        service.assignHandoff(contactId, request.toCommand()).toResponse()

    @PostMapping("/{contactId}/manual-handoff/complete")
    fun completeManualHandoff(
        @PathVariable contactId: Long,
        @RequestBody request: ManualHandoffCompleteRequest
    ): ManualHandoffResponse =
        service.completeHandoff(contactId, request.toCommand()).toResponse()

    @PostMapping("/{contactId}/pause-auto-reply")
    fun pauseAutoReply(@PathVariable contactId: Long): ExpertContactResponse =
        service.pauseAutoReply(contactId).toResponse()

    @PostMapping("/{contactId}/resume-auto-reply")
    fun resumeAutoReply(@PathVariable contactId: Long): ExpertContactResponse =
        service.resumeAutoReply(contactId).toResponse()

    @PostMapping("/{contactId}/promote-to-application")
    fun promoteToApplication(@PathVariable contactId: Long): ExpertContactResponse =
        service.promoteToApplication(contactId).toResponse()

    @PostMapping("/{contactId}/promote-to-candidate")
    fun promoteToCandidate(@PathVariable contactId: Long): ExpertContactResponse =
        service.promoteToCandidate(contactId).toResponse()

    @PostMapping("/{contactId}/demote-to-raw")
    fun demoteToRaw(@PathVariable contactId: Long): ExpertContactResponse =
        service.demoteToRaw(contactId).toResponse()

    @PostMapping("/{contactId}/switch-to-manual")
    fun switchToManual(
        @PathVariable contactId: Long,
        @RequestBody request: SwitchToManualRequest
    ): ExpertContactResponse =
        service.switchToManual(contactId, request.reason, request.note).toResponse()

    @PostMapping("/{contactId}/switch-to-auto")
    fun switchToAuto(
        @PathVariable contactId: Long,
        @RequestBody request: SwitchToAutoRequest
    ): ExpertContactResponse =
        service.switchToAuto(contactId, request.note).toResponse()

    @GetMapping("/mail-send-options")
    fun listMailSendOptions(): List<ManualMailOption> =
        manualExpertMailService.listSendOptions()

    @PostMapping("/{contactId}/manual-mail")
    fun sendManualMail(
        @PathVariable contactId: Long,
        @RequestBody request: ManualMailSendRequest
    ): ManualMailSendResult =
        manualExpertMailService.sendManualMail(contactId, request.toCommand())

    @PostMapping("/{contactId}/meeting-schedules")
    fun createMeetingSchedule(
        @PathVariable contactId: Long,
        @RequestBody request: CreateMeetingScheduleRequest
    ): MeetingScheduleResponse =
        meetingScheduleService.createManual(contactId, request.toCommand()).toResponse()

    @PutMapping("/{contactId}/meeting-schedules/{scheduleId}")
    fun updateMeetingSchedule(
        @PathVariable contactId: Long,
        @PathVariable scheduleId: Long,
        @RequestBody request: UpdateMeetingScheduleRequest
    ): MeetingScheduleResponse =
        meetingScheduleService.updateSchedule(contactId, scheduleId, request.toCommand()).toResponse()

    @PostMapping("/{contactId}/meeting-schedules/{scheduleId}/confirm-and-email")
    fun confirmMeetingAndEmail(
        @PathVariable contactId: Long,
        @PathVariable scheduleId: Long,
        @RequestBody request: ConfirmMeetingRequest
    ): MeetingScheduleResponse =
        meetingScheduleService.confirmMeetingAndEmail(contactId, scheduleId, request.toCommand()).toResponse()

    @PostMapping("/{contactId}/meeting-schedules/{scheduleId}/complete")
    fun completeMeeting(
        @PathVariable contactId: Long,
        @PathVariable scheduleId: Long
    ): MeetingScheduleResponse =
        meetingScheduleService.completeMeeting(contactId, scheduleId).toResponse()

    @PostMapping("/{contactId}/meeting-schedules/{scheduleId}/cancel")
    fun cancelMeeting(
        @PathVariable contactId: Long,
        @PathVariable scheduleId: Long
    ): MeetingScheduleResponse =
        meetingScheduleService.cancelMeeting(contactId, scheduleId).toResponse()
}

data class ManualHandoffCreateRequest(
    val reason: String,
    val assignedTo: String?,
    val note: String?
) {
    fun toCommand(): ManualHandoffCreateCommand =
        ManualHandoffCreateCommand(reason = reason, assignedTo = assignedTo, note = note)
}

data class ManualHandoffAssignRequest(
    val assignedTo: String,
    val note: String?
) {
    fun toCommand(): ManualHandoffAssignCommand =
        ManualHandoffAssignCommand(assignedTo = assignedTo, note = note)
}

data class ManualHandoffCompleteRequest(
    val nextStatus: String?,
    val note: String?,
    val resumeAutoReply: Boolean? = null
) {
    fun toCommand(): ManualHandoffCompleteCommand =
        ManualHandoffCompleteCommand(nextStatus = nextStatus, note = note, resumeAutoReply = resumeAutoReply)
}

data class SwitchToManualRequest(
    val reason: String?,
    val note: String?
)

data class SwitchToAutoRequest(
    val note: String?
)

data class ManualMailSendRequest(
    val optionType: String,
    val optionValue: String,
    val senderAccountCode: String?
) {
    fun toCommand(): ManualMailSendCommand =
        ManualMailSendCommand(
            optionType = optionType,
            optionValue = optionValue,
            senderAccountCode = senderAccountCode
        )
}

data class ExpertContactDetailResponse(
    val contact: ExpertContactResponse,
    val mails: List<MailRecordResponse>,
    val attachments: List<MailAttachmentResponse>,
    val documents: List<ExpertDocumentResponse>,
    val latestHandoff: ManualHandoffResponse?,
    val statusHistory: List<ExpertContactStatusHistoryResponse>,
    val recommendedNextAction: String,
    val meetingSchedules: List<MeetingScheduleResponse>
)

data class CreateMeetingScheduleRequest(
    val expertAvailableText: String?,
    val expertTimezone: String?,
    val chinaTime: String?,
    val meetingTool: String?,
    val meetingLink: String?,
    val note: String?
) {
    fun toCommand(): CreateMeetingCommand =
        CreateMeetingCommand(
            expertAvailableText = expertAvailableText,
            expertTimezone = expertTimezone,
            chinaTime = chinaTime,
            meetingTool = meetingTool,
            meetingLink = meetingLink,
            note = note
        )
}

data class UpdateMeetingScheduleRequest(
    val expertAvailableText: String?,
    val expertTimezone: String?,
    val chinaTime: String?,
    val meetingTool: String?,
    val meetingLink: String?,
    val note: String?
) {
    fun toCommand(): UpdateMeetingCommand =
        UpdateMeetingCommand(
            expertAvailableText = expertAvailableText,
            expertTimezone = expertTimezone,
            chinaTime = chinaTime,
            meetingTool = meetingTool,
            meetingLink = meetingLink,
            note = note
        )
}

data class ConfirmMeetingRequest(
    val chinaTime: String,
    val meetingTool: String,
    val meetingLink: String,
    val note: String?
) {
    fun toCommand(): ConfirmMeetingCommand =
        ConfirmMeetingCommand(
            chinaTime = chinaTime,
            meetingTool = meetingTool,
            meetingLink = meetingLink,
            note = note
        )
}

data class MeetingScheduleResponse(
    val id: Long?,
    val expertContactId: Long,
    val sourceMailRecordId: Long?,
    val expertAvailableText: String?,
    val expertTimezone: String?,
    val chinaTime: String?,
    val meetingTool: String?,
    val meetingLink: String?,
    val meetingStatus: String,
    val note: String?,
    val createdAt: String?,
    val updatedAt: String?
)

data class ExpertContactResponse(
    val id: Long?,
    val campaignId: Long,
    val orcidId: String,
    val expertEmail: String,
    val expertName: String?,
    val currentStatus: String,
    val lastMailAt: String?,
    val lastReplyAt: String?,
    val manualHandoffRequired: Boolean,
    val closedReason: String?,
    val autoReplyEnabled: Boolean = true,
    val applicationIndexed: Boolean = false,
    val currentIndexLevel: String,
    val needsManualAttention: Boolean,
    val latestManualReviewReasonType: String? = null
)

data class MailRecordResponse(
    val id: Long?,
    val direction: String,
    val mailType: String,
    val messageId: String?,
    val inReplyTo: String?,
    val subject: String?,
    val body: String?,
    val cleanedBody: String?,
    val matchedQaRuleId: Long?,
    val sendStatus: String?,
    val receivedAt: String?,
    val sentAt: String?,
    val createdAt: String?
)

data class MailAttachmentResponse(
    val id: Long?,
    val mailRecordId: Long,
    val fileName: String,
    val contentType: String?,
    val fileSize: Long,
    val storagePath: String,
    val createdAt: String?
)

data class ExpertDocumentResponse(
    val id: Long?,
    val expertContactId: Long,
    val mailAttachmentId: Long,
    val documentType: String,
    val documentStatus: String,
    val reviewNote: String?,
    val createdAt: String?,
    val updatedAt: String?
)

data class ManualHandoffResponse(
    val id: Long?,
    val expertContactId: Long,
    val reason: String,
    val handoffStatus: String,
    val assignedTo: String?,
    val note: String?
)

data class ExpertContactStatusHistoryResponse(
    val id: Long?,
    val expertContactId: Long,
    val fromStatus: String?,
    val toStatus: String,
    val reason: String,
    val source: String,
    val createdAt: String?
)

private fun ExpertContactDetail.toResponse(): ExpertContactDetailResponse =
    ExpertContactDetailResponse(
        contact = contact.toResponse(latestManualReviewReasonType),
        mails = mails.map { it.toResponse() },
        attachments = attachments.map { it.toResponse() },
        documents = documents.map { it.toResponse() },
        latestHandoff = latestHandoff?.toResponse(),
        statusHistory = statusHistory.map { it.toResponse() },
        recommendedNextAction = recommendedNextAction,
        meetingSchedules = meetingSchedules.map { it.toResponse() }
    )

private fun MeetingSchedule.toResponse(): MeetingScheduleResponse =
    MeetingScheduleResponse(
        id = id,
        expertContactId = expertContactId,
        sourceMailRecordId = sourceMailRecordId,
        expertAvailableText = expertAvailableText,
        expertTimezone = expertTimezone,
        chinaTime = chinaTime,
        meetingTool = meetingTool,
        meetingLink = meetingLink,
        meetingStatus = meetingStatus,
        note = note,
        createdAt = createdAt?.toString(),
        updatedAt = updatedAt?.toString()
    )

private fun ExpertContact.toResponse(latestManualReviewReasonType: String? = null): ExpertContactResponse =
    ExpertContactResponse(
        id = id,
        campaignId = campaignId,
        orcidId = orcidId,
        expertEmail = expertEmail,
        expertName = expertName,
        currentStatus = currentStatus,
        lastMailAt = lastMailAt?.toString(),
        lastReplyAt = lastReplyAt?.toString(),
        manualHandoffRequired = manualHandoffRequired,
        closedReason = closedReason,
        autoReplyEnabled = autoReplyEnabled,
        applicationIndexed = applicationIndexed,
        currentIndexLevel = currentIndexLevel,
        needsManualAttention = needsManualAttention,
        latestManualReviewReasonType = latestManualReviewReasonType
    )

private fun MailRecord.toResponse(): MailRecordResponse =
    MailRecordResponse(
        id = id,
        direction = direction,
        mailType = mailType,
        messageId = messageId,
        inReplyTo = inReplyTo,
        subject = subject,
        body = body,
        cleanedBody = cleanedBody,
        matchedQaRuleId = matchedQaRuleId,
        sendStatus = sendStatus,
        receivedAt = receivedAt?.toString(),
        sentAt = sentAt?.toString(),
        createdAt = createdAt?.toString()
    )

private fun MailAttachment.toResponse(): MailAttachmentResponse =
    MailAttachmentResponse(
        id = id,
        mailRecordId = mailRecordId,
        fileName = fileName,
        contentType = contentType,
        fileSize = fileSize,
        storagePath = storagePath,
        createdAt = createdAt?.toString()
    )

private fun ExpertDocument.toResponse(): ExpertDocumentResponse =
    ExpertDocumentResponse(
        id = id,
        expertContactId = expertContactId,
        mailAttachmentId = mailAttachmentId,
        documentType = documentType,
        documentStatus = documentStatus,
        reviewNote = reviewNote,
        createdAt = createdAt?.toString(),
        updatedAt = updatedAt?.toString()
    )

private fun ManualHandoff.toResponse(): ManualHandoffResponse =
    ManualHandoffResponse(
        id = id,
        expertContactId = expertContactId,
        reason = reason,
        handoffStatus = handoffStatus,
        assignedTo = assignedTo,
        note = note
    )

private fun ExpertContactStatusHistory.toResponse(): ExpertContactStatusHistoryResponse =
    ExpertContactStatusHistoryResponse(
        id = id,
        expertContactId = expertContactId,
        fromStatus = fromStatus,
        toStatus = toStatus,
        reason = reason,
        source = source,
        createdAt = createdAt?.toString()
    )
