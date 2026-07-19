package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.llm.service.AiReplyActionPolicy
import com.weibo.talentintroduction.llm.service.AiReplyDraftReadiness
import com.weibo.talentintroduction.llm.service.AiReplyDraftResult
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiReplyGenerationState
import com.weibo.talentintroduction.llm.service.AiReplyGroundedDraftMaterializer
import com.weibo.talentintroduction.llm.service.AiReplyHighRiskClaimValidator
import com.weibo.talentintroduction.llm.service.RequestGroundingStatus
import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.springframework.stereotype.Service

object GroundedAutoReplyReason {
    const val AI_AUTO_REPLY_DISABLED = "AI_AUTO_REPLY_DISABLED"
    const val QA_NO_MATCH = "QA_NO_MATCH"
    const val QA_POLICY_REVIEW = "QA_POLICY_REVIEW"
    const val QA_GROUNDING_GAP = "QA_GROUNDING_GAP"
    const val AI_GENERATION_UNAVAILABLE = "AI_GENERATION_UNAVAILABLE"
    const val AI_REPLY_VALIDATION_FAILED = "AI_REPLY_VALIDATION_FAILED"
    const val QA_AUTO_REPLIED = "QA_AUTO_REPLIED"
}

data class GroundedAutoReplyDecision(
    val readyToSend: Boolean,
    val reason: String,
    val subject: String,
    val rawDraftText: String?,
    val qaRuleIds: List<Long>,
    val draftReadiness: AiReplyDraftReadiness,
    val generationState: AiReplyGenerationState,
    val usedLlm: Boolean
)

@Service
class GroundedAutoReplyDecisionService(
    private val llmProperties: LlmProperties,
    private val aiReplyDraftService: AiReplyDraftService,
    private val qaRuleRepository: QaRuleRepository
) {
    fun decide(inboundText: String, inboundSubject: String?): GroundedAutoReplyDecision {
        val subject = buildReplySubject(inboundSubject)
        if (!llmProperties.autoReplyEnabled) {
            return disabledDecision(subject)
        }

        val draft = aiReplyDraftService.generate(
            inboundText = inboundText,
            operatorTurns = emptyList()
        )
        val verifiedRuleIds = verifyAutoEvidenceRuleIds(draft.qaRuleIds)
        val reason = resolveReason(draft, verifiedRuleIds)
        val ready = reason == GroundedAutoReplyReason.QA_AUTO_REPLIED &&
            passesSendGate(draft, verifiedRuleIds)

        return GroundedAutoReplyDecision(
            readyToSend = ready,
            reason = reason,
            subject = subject,
            rawDraftText = draft.draftText.takeIf { it.isNotBlank() },
            qaRuleIds = if (ready) verifiedRuleIds else draft.qaRuleIds,
            draftReadiness = draft.draftReadiness,
            generationState = draft.generationState,
            usedLlm = draft.usedLlm
        )
    }

    internal fun buildReplySubject(inboundSubject: String?): String {
        val trimmed = inboundSubject?.trim().orEmpty()
        if (trimmed.isBlank()) {
            return "Re:"
        }
        return if (trimmed.startsWith("Re:", ignoreCase = true)) {
            trimmed
        } else {
            "Re: $trimmed"
        }
    }

    internal fun verifyAutoEvidenceRuleIds(ruleIds: List<Long>): List<Long> {
        return ruleIds.mapNotNull { ruleId ->
            val rule = qaRuleRepository.findById(ruleId).orElse(null) ?: return@mapNotNull null
            if (!rule.enabled || rule.answerBody.trim().isBlank()) {
                return@mapNotNull null
            }
            if (rule.replyPolicyEnum() != QaReplyPolicy.AUTO) {
                return@mapNotNull null
            }
            ruleId
        }
    }

    internal fun resolveReason(
        draft: AiReplyDraftResult,
        verifiedAutoRuleIds: List<Long>
    ): String {
        if (hasValidationFailure(draft.contextWarnings)) {
            return GroundedAutoReplyReason.AI_REPLY_VALIDATION_FAILED
        }
        if (draft.qaRuleIds.isEmpty()) {
            return GroundedAutoReplyReason.QA_NO_MATCH
        }
        if (hasReviewPolicyEvidence(draft.qaRuleIds)) {
            return GroundedAutoReplyReason.QA_POLICY_REVIEW
        }
        if (hasGroundingGap(draft)) {
            return GroundedAutoReplyReason.QA_GROUNDING_GAP
        }
        if (!draft.usedLlm || draft.generationState != AiReplyGenerationState.LLM_USED) {
            if (draft.draftReadiness == AiReplyDraftReadiness.BLOCKED && hasValidationFailure(draft.contextWarnings)) {
                return GroundedAutoReplyReason.AI_REPLY_VALIDATION_FAILED
            }
            return GroundedAutoReplyReason.AI_GENERATION_UNAVAILABLE
        }
        if (verifiedAutoRuleIds.isEmpty()) {
            return GroundedAutoReplyReason.QA_POLICY_REVIEW
        }
        if (draft.draftReadiness != AiReplyDraftReadiness.READY) {
            return when (draft.draftReadiness) {
                AiReplyDraftReadiness.NEEDS_REVIEW -> GroundedAutoReplyReason.QA_POLICY_REVIEW
                AiReplyDraftReadiness.BLOCKED -> {
                    if (hasValidationFailure(draft.contextWarnings)) {
                        GroundedAutoReplyReason.AI_REPLY_VALIDATION_FAILED
                    } else {
                        GroundedAutoReplyReason.QA_GROUNDING_GAP
                    }
                }
                else -> GroundedAutoReplyReason.QA_GROUNDING_GAP
            }
        }
        if (draft.draftText.isBlank()) {
            return GroundedAutoReplyReason.AI_GENERATION_UNAVAILABLE
        }
        return GroundedAutoReplyReason.QA_AUTO_REPLIED
    }

    internal fun passesSendGate(
        draft: AiReplyDraftResult,
        verifiedAutoRuleIds: List<Long>
    ): Boolean {
        if (verifiedAutoRuleIds.isEmpty()) {
            return false
        }
        if (draft.qaRuleIds != verifiedAutoRuleIds) {
            return false
        }
        if (draft.requestFacts.any {
                it.status == RequestGroundingStatus.PARTIAL ||
                    it.status == RequestGroundingStatus.UNSUPPORTED
            }
        ) {
            return false
        }
        if (draft.draftReadiness != AiReplyDraftReadiness.READY) {
            return false
        }
        if (!draft.usedLlm || draft.generationState != AiReplyGenerationState.LLM_USED) {
            return false
        }
        return draft.draftText.isNotBlank()
    }

    private fun disabledDecision(subject: String): GroundedAutoReplyDecision =
        GroundedAutoReplyDecision(
            readyToSend = false,
            reason = GroundedAutoReplyReason.AI_AUTO_REPLY_DISABLED,
            subject = subject,
            rawDraftText = null,
            qaRuleIds = emptyList(),
            draftReadiness = AiReplyDraftReadiness.BLOCKED,
            generationState = AiReplyGenerationState.FALLBACK_LLM_DISABLED,
            usedLlm = false
        )

    private fun hasReviewPolicyEvidence(ruleIds: List<Long>): Boolean =
        ruleIds.any { ruleId ->
            qaRuleRepository.findById(ruleId).orElse(null)?.replyPolicyEnum() == QaReplyPolicy.REVIEW
        }

    private fun hasGroundingGap(draft: AiReplyDraftResult): Boolean {
        if (draft.draftReadiness == AiReplyDraftReadiness.BLOCKED) {
            return true
        }
        return draft.requestFacts.any {
            it.status == RequestGroundingStatus.PARTIAL ||
                it.status == RequestGroundingStatus.UNSUPPORTED
        }
    }

    private fun hasValidationFailure(warnings: List<String>): Boolean =
        warnings.any { warning ->
            warning == AiReplyGroundedDraftMaterializer.WARNING_STRUCTURED_RESPONSE_INVALID ||
                warning == AiReplyGroundedDraftMaterializer.WARNING_UNNATURAL_GROUNDED_STRUCTURE ||
                warning == AiReplyGroundedDraftMaterializer.WARNING_CLAIM_VALIDATION_FAILED ||
                warning == AiReplyDraftService.TRUST_REPAIR_EXHAUSTED ||
                warning == AiReplyDraftService.UNAUTHORIZED_ACTION_REMOVED ||
                warning.startsWith("AI_REPLY_CLAIM_") ||
                warning.startsWith("AI_REPLY_ACTION_")
        }
}
