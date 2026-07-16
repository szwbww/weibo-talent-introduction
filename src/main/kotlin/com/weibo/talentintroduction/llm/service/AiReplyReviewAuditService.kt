package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

const val TARGET_TYPE_INBOUND_MAIL_PROCESSING = "INBOUND_MAIL_PROCESSING"

@Service
class AiReplyReviewAuditService(
    private val operatorActionLogService: OperatorActionLogService
) {

    fun recordInitialDraft(
        inboundProcessingId: Long,
        contactId: Long,
        result: AiReplyDraftResult,
        operatorName: String?
    ) {
        try {
            val actionType = when (result.draftReadiness) {
                AiReplyDraftReadiness.READY -> OperatorActionType.AI_REPLY_DRAFT_READY
                AiReplyDraftReadiness.NEEDS_REVIEW -> OperatorActionType.AI_REPLY_DRAFT_NEEDS_REVIEW
                AiReplyDraftReadiness.BLOCKED -> OperatorActionType.AI_REPLY_DRAFT_BLOCKED
            }

            val afterMap = mapOf(
                "model" to result.selectedModel,
                "mode" to result.mode.name,
                "requestCount" to result.requestCount,
                "groundedRequestCount" to result.groundedRequestCount,
                "readiness" to result.draftReadiness.name,
                "generationState" to result.generationState.name
            )

            operatorActionLogService.record(
                targetType = TARGET_TYPE_INBOUND_MAIL_PROCESSING,
                targetId = inboundProcessingId,
                actionType = actionType,
                expertContactId = contactId,
                inboundProcessingId = inboundProcessingId,
                after = afterMap,
                operatorName = operatorName,
                note = "AI reply draft generated for inbound processing $inboundProcessingId"
            )
        } catch (ex: Exception) {
            logger.warn("Failed to record AI reply draft audit for inboundProcessingId={}: {}", inboundProcessingId, ex.message)
        }
    }

    private val logger = LoggerFactory.getLogger(AiReplyReviewAuditService::class.java)
}
