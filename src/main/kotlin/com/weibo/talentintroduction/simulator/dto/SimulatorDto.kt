package com.weibo.talentintroduction.simulator.dto

import com.weibo.talentintroduction.mail.domain.InboundIntent
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.ExpertContactStatusHistory
import com.weibo.talentintroduction.handoff.domain.ManualHandoff
import com.weibo.talentintroduction.campaign.domain.MeetingSchedule
import com.weibo.talentintroduction.mail.service.SinglePipelineResult
import com.weibo.talentintroduction.mail.service.InboundIntentCode
import com.weibo.talentintroduction.mail.service.AutoIntentAction
import java.time.LocalDateTime

data class ExpertContactResponse(
    val id: Long,
    val campaignId: Long,
    val orcidId: String,
    val expertEmail: String,
    val expertName: String?,
    val currentStatus: String,
    val autoReplyEnabled: Boolean,
    val manualHandoffRequired: Boolean,
    val needsManualAttention: Boolean,
    val applicationIndexed: Boolean,
    val currentIndexLevel: String,
    val firstReplyAt: LocalDateTime?,
    val lastReplyAt: LocalDateTime?,
    val lastMailAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
)

data class MailRecordResponse(
    val id: Long,
    val expertContactId: Long,
    val direction: String,
    val mailType: String,
    val subject: String?,
    val body: String?,
    val cleanedBody: String?,
    val messageId: String?,
    val inReplyTo: String?,
    val sendStatus: String?,
    val matchedQaRuleId: Long?,
    val receivedAt: LocalDateTime?,
    val sentAt: LocalDateTime?,
    val createdAt: LocalDateTime?
)

data class StatusHistoryResponse(
    val id: Long,
    val fromStatus: String?,
    val toStatus: String,
    val reason: String,
    val source: String,
    val createdAt: LocalDateTime?
)

data class ManualHandoffResponse(
    val id: Long,
    val expertContactId: Long,
    val reason: String,
    val handoffStatus: String,
    val assignedTo: String?,
    val note: String?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
)

data class MeetingScheduleResponse(
    val id: Long,
    val expertContactId: Long,
    val expertAvailableText: String?,
    val chinaTime: String?,
    val meetingTool: String?,
    val meetingLink: String?,
    val meetingStatus: String,
    val note: String?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
)

data class InboundIntentResponse(
    val id: Long,
    val mailRecordId: Long,
    val expertContactId: Long,
    val intentCode: String,
    val confidence: Int,
    val matchedKeywords: String?,
    val autoAction: String,
    val createdAt: LocalDateTime?
)

data class InboundProcessingResponse(
    val id: Long,
    val fromEmail: String,
    val subject: String?,
    val body: String?,
    val processStatus: String,
    val processReason: String,
    val reasonType: String?,
    val receivedAt: LocalDateTime,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
)

data class SimulatorSnapshot(
    val contact: ExpertContactResponse,
    val mails: List<MailRecordResponse>,
    val statusHistory: List<StatusHistoryResponse>,
    val latestHandoff: ManualHandoffResponse?,
    val meetingSchedules: List<MeetingScheduleResponse>,
    val recommendedNextAction: String,
    val inboundIntents: List<InboundIntentResponse>,
    val inboundProcessing: List<InboundProcessingResponse>,
    val applicationIndexLevel: String
)

data class SinglePipelineResultResponse(
    val outcome: String,
    val recorded: Boolean,
    val expertContactId: Long?,
    val inboundMailRecordId: Long?,
    val outboundMailRecordId: Long?,
    val intentCode: String?,
    val autoAction: String?,
    val matchedKeywords: List<String>,
    val newStatus: String?,
    val previousStatus: String?,
    val reason: String?,
    val replySendStatus: String?
)

data class AssertionResult(
    val passed: Boolean,
    val expectedIntent: String?,
    val actualIntent: String?,
    val intentOk: Boolean?,
    val expectedAutoAction: String?,
    val actualAutoAction: String?,
    val autoActionOk: Boolean?,
    val expectedNewStatus: String?,
    val actualNewStatus: String?,
    val statusOk: Boolean?,
    val previousStatus: String?
)

data class SimulateInboundResponse(
    val result: SinglePipelineResultResponse,
    val assertion: AssertionResult?
)

// --- Conversion extensions ---

fun ExpertContact.toResponse() = ExpertContactResponse(
    id = id ?: error("id required"),
    campaignId = campaignId,
    orcidId = orcidId,
    expertEmail = expertEmail,
    expertName = expertName,
    currentStatus = currentStatus,
    autoReplyEnabled = autoReplyEnabled,
    manualHandoffRequired = manualHandoffRequired,
    needsManualAttention = needsManualAttention,
    applicationIndexed = applicationIndexed,
    currentIndexLevel = currentIndexLevel,
    firstReplyAt = firstReplyAt,
    lastReplyAt = lastReplyAt,
    lastMailAt = lastMailAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun MailRecord.toResponse() = MailRecordResponse(
    id = id ?: error("id required"),
    expertContactId = expertContactId,
    direction = direction,
    mailType = mailType,
    subject = subject,
    body = body,
    cleanedBody = cleanedBody,
    messageId = messageId,
    inReplyTo = inReplyTo,
    sendStatus = sendStatus,
    matchedQaRuleId = matchedQaRuleId,
    receivedAt = receivedAt,
    sentAt = sentAt,
    createdAt = createdAt
)

fun ExpertContactStatusHistory.toResponse() = StatusHistoryResponse(
    id = id ?: error("id required"),
    fromStatus = fromStatus,
    toStatus = toStatus,
    reason = reason,
    source = source,
    createdAt = createdAt
)

fun ManualHandoff.toResponse() = ManualHandoffResponse(
    id = id ?: error("id required"),
    expertContactId = expertContactId,
    reason = reason,
    handoffStatus = handoffStatus,
    assignedTo = assignedTo,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun MeetingSchedule.toResponse() = MeetingScheduleResponse(
    id = id ?: error("id required"),
    expertContactId = expertContactId,
    expertAvailableText = expertAvailableText,
    chinaTime = chinaTime,
    meetingTool = meetingTool,
    meetingLink = meetingLink,
    meetingStatus = meetingStatus,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun InboundIntent.toResponse() = InboundIntentResponse(
    id = id ?: error("id required"),
    mailRecordId = mailRecordId,
    expertContactId = expertContactId,
    intentCode = intentCode,
    confidence = confidence,
    matchedKeywords = matchedKeywords,
    autoAction = autoAction,
    createdAt = createdAt
)

fun InboundMailProcessing.toResponse() = InboundProcessingResponse(
    id = id ?: error("id required"),
    fromEmail = fromEmail,
    subject = subject,
    body = body,
    processStatus = processStatus,
    processReason = processReason,
    reasonType = reasonType,
    receivedAt = receivedAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun SinglePipelineResult.toResponse() = SinglePipelineResultResponse(
    outcome = outcome.name,
    recorded = recorded,
    expertContactId = expertContactId,
    inboundMailRecordId = inboundMailRecordId,
    outboundMailRecordId = outboundMailRecordId,
    intentCode = intentCode?.name,
    autoAction = autoAction?.name,
    matchedKeywords = matchedKeywords,
    newStatus = newStatus,
    previousStatus = previousStatus,
    reason = reason,
    replySendStatus = replySendStatus
)

data class SeedContactRequest(
    val expertName: String? = null,
    val initialStatus: String? = "INTRO_SENT",
    val createIntroductionMailRecord: Boolean = true
)

data class ResetContactRequest(
    val initialStatus: String? = "INTRO_SENT",
    val createIntroductionMailRecord: Boolean = true
)

data class InboundRequest(
    val subject: String?,
    val body: String,
    val attachments: List<InboundAttachment> = emptyList(),
    val overrideFromEmail: String? = null,
    val expectedIntent: String? = null,
    val expectedAutoAction: String? = null,
    val expectedNewStatus: String? = null
)

data class InboundAttachment(
    val fileName: String,
    val contentType: String?,
    val contentBase64: String
)

data class StepResult(
    val stepIndex: Int,
    val stepKind: String,
    val presetKey: String?,
    val manualApiPath: String?,
    val result: SinglePipelineResultResponse?,
    val assertion: AssertionResult?,
    val contactSnapshot: SimulatorSnapshot?
)

data class ScenarioRunResult(
    val scenarioKey: String,
    val scenarioName: String,
    val contactId: Long,
    val steps: List<StepResult>,
    val passed: Boolean
)

fun InboundRequest.toAssertion(
    r: SinglePipelineResult, previousStatus: String, currentStatus: String
): AssertionResult {
    val intentOk = expectedIntent?.let { it == r.intentCode?.name }
    val actionOk = expectedAutoAction?.let { it == r.autoAction?.name }
    val statusOk = expectedNewStatus?.let { it == currentStatus }
    val anyExpected = expectedIntent != null || expectedAutoAction != null || expectedNewStatus != null
    val passed = anyExpected && (intentOk ?: true) && (actionOk ?: true) && (statusOk ?: true)
    return AssertionResult(
        passed = passed,
        expectedIntent = expectedIntent, actualIntent = r.intentCode?.name, intentOk = intentOk,
        expectedAutoAction = expectedAutoAction, actualAutoAction = r.autoAction?.name, autoActionOk = actionOk,
        expectedNewStatus = expectedNewStatus, actualNewStatus = currentStatus, statusOk = statusOk,
        previousStatus = previousStatus
    )
}
