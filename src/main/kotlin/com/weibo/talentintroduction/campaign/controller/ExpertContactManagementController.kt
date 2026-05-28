package com.weibo.talentintroduction.campaign.controller

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.service.ExpertContactDetail
import com.weibo.talentintroduction.campaign.service.ExpertContactManagementService
import com.weibo.talentintroduction.campaign.service.ManualHandoffAssignCommand
import com.weibo.talentintroduction.campaign.service.ManualHandoffCompleteCommand
import com.weibo.talentintroduction.campaign.service.ManualHandoffCreateCommand
import com.weibo.talentintroduction.handoff.domain.ManualHandoff
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.service.ManualExpertMailService
import com.weibo.talentintroduction.mail.service.ManualMailOption
import com.weibo.talentintroduction.mail.service.ManualMailSendCommand
import com.weibo.talentintroduction.mail.service.ManualMailSendResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/expert-contacts")
class ExpertContactManagementController(
    private val service: ExpertContactManagementService,
    private val manualExpertMailService: ManualExpertMailService
) {
    @GetMapping
    fun listContacts(
        @RequestParam(required = false) campaignId: Long?,
        @RequestParam(required = false) status: String?
    ): List<ExpertContactResponse> =
        service.listContacts(campaignId, status).map { it.toResponse() }

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

    @PostMapping("/{contactId}/close")
    fun closeContact(
        @PathVariable contactId: Long,
        @RequestBody request: ExpertContactCloseRequest
    ): ExpertContactResponse =
        service.closeContact(contactId, request.reason).toResponse()

    @GetMapping("/mail-send-options")
    fun listMailSendOptions(): List<ManualMailOption> =
        manualExpertMailService.listSendOptions()

    @PostMapping("/{contactId}/manual-mail")
    fun sendManualMail(
        @PathVariable contactId: Long,
        @RequestBody request: ManualMailSendRequest
    ): ManualMailSendResult =
        manualExpertMailService.sendManualMail(contactId, request.toCommand())
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
    val note: String?
) {
    fun toCommand(): ManualHandoffCompleteCommand =
        ManualHandoffCompleteCommand(nextStatus = nextStatus, note = note)
}

data class ExpertContactCloseRequest(
    val reason: String
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
    val latestHandoff: ManualHandoffResponse?
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
    val closedReason: String?
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

data class ManualHandoffResponse(
    val id: Long?,
    val expertContactId: Long,
    val reason: String,
    val handoffStatus: String,
    val assignedTo: String?,
    val note: String?
)

private fun ExpertContactDetail.toResponse(): ExpertContactDetailResponse =
    ExpertContactDetailResponse(
        contact = contact.toResponse(),
        mails = mails.map { it.toResponse() },
        latestHandoff = latestHandoff?.toResponse()
    )

private fun ExpertContact.toResponse(): ExpertContactResponse =
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
        closedReason = closedReason
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

private fun ManualHandoff.toResponse(): ManualHandoffResponse =
    ManualHandoffResponse(
        id = id,
        expertContactId = expertContactId,
        reason = reason,
        handoffStatus = handoffStatus,
        assignedTo = assignedTo,
        note = note
    )
