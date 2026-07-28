package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

enum class TrustReplySourceType {
    TRAINING_MAIL,
    LIVE_INBOUND
}

data class TrustReplySourceRef(
    val sourceType: TrustReplySourceType,
    val sourceId: Long
)

data class ResolvedTrustReplySource(
    val source: TrustReplySourceRef,
    val contact: ExpertContact,
    val inboundText: String,
    val subject: String?,
    val messageId: String?,
    val senderAccountCode: String?,
    val profileText: String,
    val mailHistory: String,
    val contextWarnings: List<String>,
    val researchProfileSufficient: Boolean,
    val sourceVersion: String
)

data class TrustReplyBootstrapRequest(
    val source: TrustReplySourceRef,
    val requestedFactIds: List<Long>? = null
)

data class TrustReplyGenerationRequest(
    val source: TrustReplySourceRef,
    val expectedSourceVersion: String?,
    val turns: List<AiReplyTurn> = emptyList(),
    val qaRuleIds: List<Long>? = null,
    val operatorInstruction: String? = null,
    val operatorName: String? = null,
    val model: String? = null,
    val llmAttemptTimeoutSeconds: Int? = null,
    val llmTotalTimeoutSeconds: Int? = null
)

data class TrustReplyRequestCoverage(
    val index: Int,
    val requestText: String,
    val status: String,
    val factRuleIds: List<Long>,
    val intents: List<TrustReplyIntentCoverage> = emptyList()
)

data class TrustReplyIntentCoverage(
    val intentKey: String,
    val title: String,
    val status: String,
    val evidenceRuleIds: List<Long>,
    val missingEvidenceKeys: List<String>,
    val requiresResearchContext: Boolean
)

data class TrustReplyBootstrapResponse(
    val source: TrustReplySourceRef,
    val sourceVersion: String,
    val inboundSubject: String?,
    val inboundText: String,
    val expertName: String?,
    val expertEmail: String,
    val llmEnabled: Boolean,
    val availableModels: List<String>,
    val defaultModel: String,
    val suggestedFactIds: List<Long>,
    val canonicalFactIds: List<Long>,
    val rulesByCategory: List<TrustReplyRuleMetadata> = emptyList(),
    val requestCoverage: List<TrustReplyRequestCoverage>,
    val draftReadiness: String,
    val contextWarnings: List<String> = emptyList(),
    val evidenceSetVersion: String
)

data class TrustReplyRuleMetadata(
    val ruleId: Long,
    val displayName: String,
    val categoryId: Long? = null
)

data class TrustReplyGenerationResult(
    val source: TrustReplySourceRef,
    val sourceVersion: String,
    val draftText: String,
    val renderedDraftText: String,
    val draftHash: String,
    val usedLlm: Boolean,
    val llmEnabled: Boolean,
    val qaRuleIds: List<Long>,
    val mode: String,
    val requestCoverage: List<TrustReplyRequestCoverage>,
    val generationState: String,
    val draftReadiness: String,
    val evidenceSetVersion: String,
    val groundedRequestCount: Int = 0,
    val requestCount: Int = requestCoverage.size,
    val unsupportedRequests: List<String> = emptyList(),
    val contextWarnings: List<String> = emptyList(),
    val injectedDialogRefs: List<String> = emptyList(),
    val selectedModel: String = AiReplyModel.DEEPSEEK_V4_FLASH.name,
    val promptVersion: String = "",
    val appliedLlmAttemptTimeoutSeconds: Int = AiReplyTimeoutPolicy.DEFAULT_ATTEMPT_SECONDS,
    val appliedLlmTotalTimeoutSeconds: Int = AiReplyTimeoutPolicy.DEFAULT_TOTAL_SECONDS,
    val evidenceSources: List<AiReplyEvidenceSnapshot> = emptyList()
)

class TrustReplyWorkbenchException(
    val status: HttpStatus,
    val code: String
) : RuntimeException(code)

@Service
class TrustReplyWorkbenchService(
    private val mailRecordRepository: MailRecordRepository,
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val expertContactRepository: ExpertContactRepository,
    private val aiTrainingQaService: AiTrainingQaService,
    private val aiReplyContextService: AiReplyContextService,
    private val qaFactSelectionService: QaFactSelectionService,
    private val qaRuleRepository: QaRuleRepository,
    private val aiReplyDraftService: AiReplyDraftService,
    private val aiReplyDraftPreviewService: AiReplyDraftPreviewService,
    private val aiReplyReviewAuditService: AiReplyReviewAuditService,
    private val llmProperties: LlmProperties
) {
    fun resolveSource(source: TrustReplySourceRef): ResolvedTrustReplySource {
        require(source.sourceId > 0) { "sourceId must be positive" }
        return when (source.sourceType) {
            TrustReplySourceType.TRAINING_MAIL -> resolveTrainingMail(source)
            TrustReplySourceType.LIVE_INBOUND -> resolveLiveInbound(source)
        }
    }

    fun bootstrap(request: TrustReplyBootstrapRequest): TrustReplyBootstrapResponse {
        val resolved = resolveSource(request.source)
        val selection = try {
            qaFactSelectionService.select(
                inboundText = resolved.inboundText,
                selectedRuleIds = request.requestedFactIds,
                researchProfileSufficient = resolved.researchProfileSufficient
            )
        } catch (ex: IllegalArgumentException) {
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_FACT_SELECTION_INVALID")
        }
        val evidence = aiReplyDraftService.buildEvidenceSnapshotForSelection(selection.sendQaRuleIds)
        return TrustReplyBootstrapResponse(
            source = resolved.source,
            sourceVersion = resolved.sourceVersion,
            inboundSubject = resolved.subject,
            inboundText = resolved.inboundText,
            expertName = resolved.contact.expertName,
            expertEmail = resolved.contact.expertEmail,
            llmEnabled = llmProperties.enabled,
            availableModels = AiReplyModel.values().map { it.name },
            defaultModel = AiReplyModel.DEEPSEEK_V4_FLASH.name,
            suggestedFactIds = selection.sendQaRuleIds,
            canonicalFactIds = selection.sendQaRuleIds,
            rulesByCategory = availableFactMetadata(),
            requestCoverage = selection.requestFacts.toCoverage(),
            draftReadiness = bootstrapReadiness(selection),
            contextWarnings = resolved.contextWarnings,
            evidenceSetVersion = evidence.first
        )
    }

    fun generate(
        request: TrustReplyGenerationRequest,
        cancellationToken: AiReplyCancellationToken? = null,
        progressReporter: AiReplyProgressReporter = AiReplyProgressReporter.NOOP,
        beforeCommit: (() -> Boolean)? = null
    ): TrustReplyGenerationResult {
        val resolved = resolveSource(request.source)
        val expectedVersion = request.expectedSourceVersion?.trim()
        if (expectedVersion.isNullOrEmpty()) {
            throw TrustReplyWorkbenchException(HttpStatus.BAD_REQUEST, "TRUST_REPLY_SOURCE_VERSION_REQUIRED")
        }
        if (expectedVersion != resolved.sourceVersion) {
            throw TrustReplyWorkbenchException(HttpStatus.CONFLICT, "TRUST_REPLY_SOURCE_STALE")
        }

        val result = if (
            request.llmAttemptTimeoutSeconds == null &&
            request.llmTotalTimeoutSeconds == null &&
            cancellationToken == null &&
            progressReporter === AiReplyProgressReporter.NOOP
        ) {
            aiReplyDraftService.generate(
                inboundText = resolved.inboundText,
                operatorTurns = request.turns,
                qaRuleIds = request.qaRuleIds,
                operatorInstruction = request.operatorInstruction,
                expertProfile = resolved.profileText,
                mailHistory = resolved.mailHistory,
                contextWarnings = resolved.contextWarnings,
                replyModel = request.model,
                researchProfileSufficient = resolved.researchProfileSufficient
            )
        } else {
            aiReplyDraftService.generate(
                inboundText = resolved.inboundText,
                operatorTurns = request.turns,
                qaRuleIds = request.qaRuleIds,
                operatorInstruction = request.operatorInstruction,
                expertProfile = resolved.profileText,
                mailHistory = resolved.mailHistory,
                contextWarnings = resolved.contextWarnings,
                replyModel = request.model,
                researchProfileSufficient = resolved.researchProfileSufficient,
                llmAttemptTimeoutSeconds = request.llmAttemptTimeoutSeconds,
                llmTotalTimeoutSeconds = request.llmTotalTimeoutSeconds,
                cancellationToken = cancellationToken,
                progressReporter = progressReporter
            )
        }

        cancellationToken?.throwIfCancelled()
        if (beforeCommit != null && !beforeCommit()) {
            throw AiReplyGenerationCancelledException()
        }
        val auditSnapshot = if (resolved.source.sourceType == TrustReplySourceType.LIVE_INBOUND) {
            if (request.turns.isEmpty()) {
                aiReplyReviewAuditService.recordInitialDraft(
                    inboundProcessingId = resolved.source.sourceId,
                    contactId = requireNotNull(resolved.contact.id),
                    result = result,
                    operatorName = request.operatorName
                )
            } else {
                aiReplyReviewAuditService.buildSnapshot(result)
            }
        } else {
            null
        }

        val preview = aiReplyDraftPreviewService.preview(
            raw = result.draftText,
            contact = resolved.contact,
            senderAccountCode = resolved.senderAccountCode
        )
        val warnings = mergeWarnings(result.contextWarnings, preview.warningCodes)
        val policy = AiReplyTimeoutPolicy.resolve(
            request.llmAttemptTimeoutSeconds,
            request.llmTotalTimeoutSeconds
        )
        return TrustReplyGenerationResult(
            source = resolved.source,
            sourceVersion = resolved.sourceVersion,
            draftText = result.draftText,
            renderedDraftText = preview.renderedText,
            draftHash = auditSnapshot?.draftHash ?: AiReplyDraftService.sha256Hex(result.draftText),
            usedLlm = result.usedLlm,
            llmEnabled = llmProperties.enabled,
            qaRuleIds = result.qaRuleIds,
            mode = result.mode.name,
            requestCoverage = result.requestFacts.toCoverage(),
            generationState = result.generationState.name,
            draftReadiness = result.draftReadiness.name,
            evidenceSetVersion = result.evidenceSetVersion,
            groundedRequestCount = result.groundedRequestCount,
            requestCount = result.requestCount,
            unsupportedRequests = result.unsupportedRequests,
            contextWarnings = warnings,
            injectedDialogRefs = result.fewShotDialogRefs,
            selectedModel = result.selectedModel,
            promptVersion = result.promptVersion,
            appliedLlmAttemptTimeoutSeconds = policy.attemptTimeoutSeconds,
            appliedLlmTotalTimeoutSeconds = policy.totalTimeoutSeconds,
            evidenceSources = result.evidenceSources
        )
    }

    private fun resolveTrainingMail(source: TrustReplySourceRef): ResolvedTrustReplySource {
        val mail = mailRecordRepository.findById(source.sourceId).orElseThrow {
            TrustReplyWorkbenchException(HttpStatus.NOT_FOUND, "TRUST_REPLY_SOURCE_NOT_FOUND")
        }
        if (!mail.direction.equals("INBOUND", ignoreCase = true)) {
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_SOURCE_NOT_INBOUND")
        }
        return resolveWithContact(
            source = source,
            contactId = mail.expertContactId,
            inboundText = mail.inboundText(),
            subject = mail.subject,
            messageId = mail.messageId,
            senderAccountCode = mail.senderAccountCode
        )
    }

    private fun resolveLiveInbound(source: TrustReplySourceRef): ResolvedTrustReplySource {
        val inbound = inboundMailProcessingRepository.findById(source.sourceId).orElseThrow {
            TrustReplyWorkbenchException(HttpStatus.NOT_FOUND, "TRUST_REPLY_SOURCE_NOT_FOUND")
        }
        val contactId = inbound.expertContactId ?: throw TrustReplyWorkbenchException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "TRUST_REPLY_SOURCE_CONTACT_REQUIRED"
        )
        return resolveWithContact(
            source = source,
            contactId = contactId,
            inboundText = inbound.inboundText(),
            subject = inbound.subject,
            messageId = inbound.messageId,
            senderAccountCode = inbound.senderAccountCode
        )
    }

    private fun resolveWithContact(
        source: TrustReplySourceRef,
        contactId: Long,
        inboundText: String,
        subject: String?,
        messageId: String?,
        senderAccountCode: String?
    ): ResolvedTrustReplySource {
        val contact = expertContactRepository.findById(contactId).orElseThrow {
            TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_SOURCE_CONTACT_NOT_FOUND")
        }
        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
        val knowledge = aiTrainingQaService.buildKnowledgeContext(inboundText)
        val context = aiReplyContextService.build(
            contact = contact,
            records = records,
            inboundText = inboundText,
            trainingKnowledge = knowledge,
            currentInboundMessageId = messageId
        )
        val sourceVersion = sourceVersion(
            source = source,
            contactId = contactId,
            messageId = messageId,
            subject = subject,
            senderAccountCode = senderAccountCode,
            inboundText = inboundText,
            mailHistory = context.mailHistory,
            profileText = context.profileText,
            researchProfileSufficient = context.researchProfileSufficient
        )
        return ResolvedTrustReplySource(
            source = source,
            contact = contact,
            inboundText = inboundText,
            subject = subject,
            messageId = messageId,
            senderAccountCode = senderAccountCode,
            profileText = context.profileText,
            mailHistory = context.mailHistory,
            contextWarnings = context.contextWarnings,
            researchProfileSufficient = context.researchProfileSufficient,
            sourceVersion = sourceVersion
        )
    }

    private fun sourceVersion(
        source: TrustReplySourceRef,
        contactId: Long,
        messageId: String?,
        subject: String?,
        senderAccountCode: String?,
        inboundText: String,
        mailHistory: String,
        profileText: String,
        researchProfileSufficient: Boolean
    ): String {
        val canonical = listOf(
            source.sourceType.name,
            source.sourceId.toString(),
            contactId.toString(),
            messageId.orEmpty(),
            subject.orEmpty(),
            senderAccountCode.orEmpty(),
            sha256Hex(inboundText),
            sha256Hex(mailHistory),
            sha256Hex(profileText),
            researchProfileSufficient.toString()
        ).joinToString("\u0000")
        return sha256Hex(canonical)
    }

    private fun bootstrapReadiness(selection: ResolvedQaRules): String =
        if (selection.requestFacts.any { it.status == RequestGroundingStatus.UNSUPPORTED }) "BLOCKED" else "READY"

    private fun availableFactMetadata(): List<TrustReplyRuleMetadata> =
        qaRuleRepository.findAllEnabledOrdered()
            .asSequence()
            .filter { it.isMatchable() && it.answerBody.trim().isNotBlank() }
            .mapNotNull { rule ->
                rule.id?.let { id ->
                    TrustReplyRuleMetadata(
                        ruleId = id,
                        displayName = rule.displayName?.takeIf { it.isNotBlank() } ?: "未命名事实",
                        categoryId = rule.categoryId
                    )
                }
            }
            .toList()

    private fun mergeWarnings(first: List<String>, second: List<String>): List<String> {
        val seen = linkedSetOf<String>()
        return (first + second).filter { seen.add(it) }
    }

    private fun List<RequestFactItem>.toCoverage(): List<TrustReplyRequestCoverage> = map { item ->
        TrustReplyRequestCoverage(
            index = item.index,
            requestText = item.requestText,
            status = item.status.name,
            factRuleIds = item.factRuleIds,
            intents = item.intents.map { intent ->
                TrustReplyIntentCoverage(
                    intentKey = intent.intentKey,
                    title = intent.title,
                    status = intent.status,
                    evidenceRuleIds = intent.evidenceRuleIds,
                    missingEvidenceKeys = intent.missingEvidenceKeys,
                    requiresResearchContext = intent.requiresResearchContext
                )
            }
        )
    }

    private fun MailRecord.inboundText(): String = cleanedBody?.takeIf { it.isNotBlank() } ?: body.orEmpty()

    private fun InboundMailProcessing.inboundText(): String = cleanedBody?.takeIf { it.isNotBlank() } ?: body.orEmpty()

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
