package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.audit.domain.OperatorActionLog
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.ExpertEmailAlias
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.service.AutoReplyPreviewResult
import com.weibo.talentintroduction.mail.service.AutoReplyPreviewService
import com.weibo.talentintroduction.mail.service.CandidateSuggestion
import com.weibo.talentintroduction.mail.service.PendingMailOperationService
import com.weibo.talentintroduction.mail.service.ComposedReplyRequest
import com.weibo.talentintroduction.mail.service.PendingManualRichReplyRequest
import com.weibo.talentintroduction.mail.service.PendingMailSendResult
import com.weibo.talentintroduction.mail.service.PendingQaReplyRequest
import com.weibo.talentintroduction.mail.service.UnmatchedInboundMailService
import com.weibo.talentintroduction.qa.service.CategoryRulesGroup
import com.weibo.talentintroduction.qa.service.CompositionSuggestResult
import com.weibo.talentintroduction.qa.service.SuggestQaRule
import com.weibo.talentintroduction.reply.service.ManualReplyFrame
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/mail")
class UnmatchedInboundMailController(
    private val unmatchedInboundMailService: UnmatchedInboundMailService,
    private val expertEmailAliasService: ExpertEmailAliasService,
    private val expertContactRepository: com.weibo.talentintroduction.campaign.repository.ExpertContactRepository,
    private val pendingMailOperationService: PendingMailOperationService,
    private val operatorActionLogService: OperatorActionLogService,
    private val llmStitchService: com.weibo.talentintroduction.llm.service.LlmStitchService,
    private val autoReplyPreviewService: AutoReplyPreviewService,
    private val replySnippetService: ReplySnippetService,
    private val aiReplyDraftService: com.weibo.talentintroduction.llm.service.AiReplyDraftService
) {
    @GetMapping("/unmatched-inbound")
    fun list(
        @RequestParam(required = false) reasonType: String?,
        @RequestParam(required = false) email: String?,
        @RequestParam(required = false) subject: String?,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int,
        @RequestParam(required = false, defaultValue = "0") pageOffset: Int
    ): InboundMailProcessingListResponse {
        val result = unmatchedInboundMailService.listManualReviewQueue(
            reasonType = reasonType,
            email = email,
            subject = subject,
            pageSize = pageSize,
            pageOffset = pageOffset
        )
        val contactIds = result.records.mapNotNull { it.expertContactId }.distinct()
        val contactsMap = if (contactIds.isNotEmpty()) {
            expertContactRepository.findAllById(contactIds).associateBy { it.id }
        } else {
            emptyMap()
        }
        return InboundMailProcessingListResponse(
            records = result.records.map { record ->
                val contact = record.expertContactId?.let { contactsMap[it] }
                record.toResponse(
                    expertName = contact?.expertName,
                    expertCurrentStatus = contact?.currentStatus,
                    expertOperatorStatus = contact?.operatorStatus,
                    expertIndexLevel = contact?.currentIndexLevel
                )
            },
            totalCount = result.totalCount,
            manualReviewTotal = result.manualReviewTotal,
            countsByReasonType = result.countsByReasonType
        )
    }

    @GetMapping("/unmatched-inbound/{id}")
    fun getUnmatchedDetail(@PathVariable id: Long): UnmatchedDetailResponse {
        val record = unmatchedInboundMailService.getDetail(id)
        val candidates = unmatchedInboundMailService.suggestCandidates(record)
        val contact = record.expertContactId?.let { expertContactRepository.findById(it).orElse(null) }
        val logs = operatorActionLogService.search(
            expertContactId = null,
            inboundProcessingId = id,
            actionType = null,
            operatorName = null,
            start = null,
            end = null,
            pageSize = 50,
            pageOffset = 0
        ).first
        return UnmatchedDetailResponse(
            record = record.toResponse(
                expertName = contact?.expertName,
                expertCurrentStatus = contact?.currentStatus
            ),
            candidates = candidates.map { it.toResponse() },
            contact = contact?.toPendingMailContactResponse(),
            logs = logs.map { it.toResponse() }
        )
    }

    @PostMapping("/unmatched-inbound/{id}/bind")
    fun bindUnmatched(
        @PathVariable id: Long,
        @RequestBody request: BindUnmatchedRequest
    ): InboundMailProcessingResponse {
        val result = unmatchedInboundMailService.bindToContact(
            recordId = id,
            contactId = request.contactId,
            resolvedBy = request.resolvedBy,
            promoteToApplication = request.promoteToApplication ?: false
        )
        return result.toResponse()
    }

    @PostMapping("/unmatched-inbound/{id}/mark-resolved")
    fun markResolved(
        @PathVariable id: Long,
        @RequestBody request: MarkResolvedRequest
    ) {
        val actualOperator = request.operatorName?.takeIf { it.isNotBlank() }
            ?: request.resolvedBy
            ?: "UNKNOWN"
        pendingMailOperationService.markResolved(
            inboundProcessingId = id,
            resolvedBy = actualOperator,
            operatorName = actualOperator,
            note = request.note
        )
    }

    @GetMapping("/unmatched-inbound/search-contacts")
    fun searchContacts(@RequestParam query: String): List<CandidateResponse> {
        val contacts = unmatchedInboundMailService.searchContacts(query)
        return contacts.map {
            CandidateResponse(
                contactId = it.id ?: 0,
                orcidId = it.orcidId,
                expertName = it.expertName,
                expertEmail = it.expertEmail,
                reason = "SEARCH",
                confidence = 100
            )
        }
    }

    @PostMapping("/unmatched-inbound/{id}/operator-status")
    fun changeOperatorStatus(
        @PathVariable id: Long,
        @RequestBody request: OperatorStatusChangeRequest
    ): PendingMailContactResponse {
        val contact = pendingMailOperationService.changeOperatorStatus(
            inboundProcessingId = id,
            operatorStatus = request.operatorStatus,
            operatorName = request.operatorName,
            note = request.note
        )
        return contact.toPendingMailContactResponse()
    }

    @PostMapping("/unmatched-inbound/{id}/index-level")
    fun changeIndexLevel(
        @PathVariable id: Long,
        @RequestBody request: IndexLevelChangeRequest
    ): PendingMailContactResponse {
        val contact = pendingMailOperationService.changeIndexLevel(
            inboundProcessingId = id,
            targetLevel = request.targetLevel,
            operatorName = request.operatorName,
            note = request.note
        )
        return contact.toPendingMailContactResponse()
    }

    @PostMapping("/unmatched-inbound/{id}/qa-reply")
    fun sendQaReply(
        @PathVariable id: Long,
        @RequestBody request: PendingQaReplyRequest
    ): PendingMailSendResult =
        pendingMailOperationService.sendQaReply(
            inboundProcessingId = id,
            qaRuleId = request.qaRuleId,
            senderAccountCode = request.senderAccountCode,
            operatorName = request.operatorName
        )

    @PostMapping("/unmatched-inbound/{id}/manual-rich-reply")
    fun sendManualRichReply(
        @PathVariable id: Long,
        @RequestBody request: PendingManualRichReplyRequest
    ): PendingMailSendResult =
        pendingMailOperationService.sendManualRichReply(
            inboundProcessingId = id,
            senderAccountCode = request.senderAccountCode,
            subject = request.subject,
            htmlBody = request.htmlBody,
            textBody = request.textBody,
            operatorName = request.operatorName
        )

    @GetMapping("/unmatched-inbound/{id}/auto-reply-preview")
    fun previewAutoReply(@PathVariable id: Long): AutoReplyPreviewResponse =
        autoReplyPreviewService.preview(id).toResponse()

    @GetMapping("/unmatched-inbound/{id}/composed-reply/suggest")
    fun suggestComposedReply(@PathVariable id: Long): ComposedReplySuggestResponse {
        val detail = unmatchedInboundMailService.getDetail(id)
        val suggest = pendingMailOperationService.suggestComposedReply(id)
        val inboundText = detail.cleanedBody?.takeIf { it.isNotBlank() } ?: detail.body.orEmpty()
        return suggest.toResponse(
            llmEnabled = llmStitchService.isEnabled(),
            inboundText = inboundText,
            frame = replySnippetService.resolveManualFrame()
        )
    }

    @PostMapping("/unmatched-inbound/{id}/composed-reply/polish")
    fun polishComposedReply(
        @PathVariable id: Long,
        @RequestBody request: com.weibo.talentintroduction.llm.service.PolishDraftRequest
    ): com.weibo.talentintroduction.llm.service.PolishDraftResponse {
        val detail = unmatchedInboundMailService.getDetail(id)
        val inboundText = detail.cleanedBody?.takeIf { it.isNotBlank() } ?: detail.body.orEmpty()
        val result = llmStitchService.polishDraft(
            qaRuleIds = request.qaRuleIds,
            inboundQuestion = inboundText,
            freeText = request.freeText,
            ackSnippetId = request.ackSnippetId
        )
        return com.weibo.talentintroduction.llm.service.PolishDraftResponse(
            draftText = result.draftText,
            usedLlm = result.usedLlm,
            llmEnabled = llmStitchService.isEnabled()
        )
    }

    @PostMapping("/unmatched-inbound/{id}/composed-reply")
    fun sendComposedReply(
        @PathVariable id: Long,
        @RequestBody request: ComposedReplyRequest
    ): PendingMailSendResult =
        pendingMailOperationService.sendManualComposedReply(
            inboundProcessingId = id,
            qaRuleIds = request.qaRuleIds,
            overrideTextBody = request.overrideTextBody,
            freeTextBody = request.freeTextBody,
            ackSnippetId = request.ackSnippetId,
            senderAccountCode = request.senderAccountCode,
            operatorName = request.operatorName
        )

    @PostMapping("/unmatched-inbound/{id}/ai-reply/turn")
    fun aiReplyTurn(
        @PathVariable id: Long,
        @RequestBody request: AiReplyTurnRequest
    ): AiReplyTurnResponse {
        val detail = unmatchedInboundMailService.getDetail(id)
        val inboundText = detail.cleanedBody?.takeIf { it.isNotBlank() } ?: detail.body.orEmpty()
        val turns = request.turns.map {
            com.weibo.talentintroduction.llm.service.AiReplyTurn(
                assistantDraft = it.assistantDraft,
                operatorInstruction = it.operatorInstruction
            )
        }
        val result = aiReplyDraftService.generate(
            inboundText = inboundText,
            operatorTurns = turns,
            qaRuleIds = request.qaRuleIds
        )
        return AiReplyTurnResponse(
            draftText = result.draftText,
            usedLlm = result.usedLlm,
            llmEnabled = llmStitchService.isEnabled(),
            qaRuleIds = result.qaRuleIds
        )
    }
}

@RestController
@RequestMapping("/api/expert-contacts")
class ExpertEmailAliasController(
    private val expertEmailAliasService: ExpertEmailAliasService
) {
    @GetMapping("/{contactId}/email-aliases")
    fun listAliases(@PathVariable contactId: Long): List<ExpertEmailAliasResponse> =
        expertEmailAliasService.listAliases(contactId).map { it.toResponse() }

    @PostMapping("/{contactId}/email-aliases")
    fun addAlias(
        @PathVariable contactId: Long,
        @RequestBody request: AddAliasRequest
    ): ExpertEmailAliasResponse =
        expertEmailAliasService.addAlias(
            expertContactId = contactId,
            email = request.email,
            source = request.source ?: "MANUAL_ADD"
        ).toResponse()

    @DeleteMapping("/{contactId}/email-aliases/{aliasId}")
    fun deleteAlias(
        @PathVariable contactId: Long,
        @PathVariable aliasId: Long
    ) {
        expertEmailAliasService.deleteAlias(aliasId)
    }
}

data class InboundMailProcessingListResponse(
    val records: List<InboundMailProcessingResponse>,
    val totalCount: Long,
    val manualReviewTotal: Long,
    val countsByReasonType: Map<String, Long>
)

data class UnmatchedDetailResponse(
    val record: InboundMailProcessingResponse,
    val candidates: List<CandidateResponse>,
    val contact: PendingMailContactResponse? = null,
    val logs: List<OperatorActionLogResponse> = emptyList()
)

data class InboundMailProcessingResponse(
    val id: Long?,
    val senderAccountCode: String,
    val imapUid: Long,
    val messageId: String?,
    val inReplyTo: String?,
    val fromEmail: String,
    val subject: String?,
    val body: String?,
    val cleanedBody: String?,
    val receivedAt: String?,
    val processStatus: String,
    val processReason: String,
    val reasonType: String?,
    val resolvedAt: String?,
    val resolvedBy: String?,
    val expertContactId: Long?,
    val expertName: String? = null,
    val expertCurrentStatus: String? = null,
    val expertOperatorStatus: String? = null,
    val expertIndexLevel: String? = null
)

data class PendingMailContactResponse(
    val contactId: Long,
    val expertName: String?,
    val expertEmail: String,
    val orcidId: String,
    val currentIndexLevel: String,
    val operatorStatus: String,
    val currentStatus: String,
    val autoReplyEnabled: Boolean
)

data class MarkResolvedRequest(
    val resolvedBy: String?,
    val operatorName: String? = null,
    val note: String?
)

data class OperatorStatusChangeRequest(
    val operatorStatus: String,
    val operatorName: String?,
    val note: String?
)

data class IndexLevelChangeRequest(
    val targetLevel: String,
    val operatorName: String?,
    val note: String?
)

data class CandidateResponse(
    val contactId: Long,
    val orcidId: String,
    val expertName: String?,
    val expertEmail: String,
    val reason: String,
    val confidence: Int
)

data class BindUnmatchedRequest(
    val contactId: Long,
    val resolvedBy: String,
    val promoteToApplication: Boolean? = null
)

data class AddAliasRequest(
    val email: String,
    val source: String?
)

data class ExpertEmailAliasResponse(
    val id: Long?,
    val expertContactId: Long,
    val email: String,
    val normalizedEmail: String,
    val source: String,
    val verified: Boolean,
    val createdAt: String?
)

data class OperatorActionLogResponse(
    val id: Long?,
    val actionType: String,
    val actionSummary: String,
    val beforeValue: String?,
    val afterValue: String?,
    val operatorName: String?,
    val note: String?,
    val createdAt: String?
)

data class GapItemResponse(
    val text: String,
    val candidateRuleIds: List<Long>
)

data class AutoReplyPreviewResponse(
    val previewKind: String,
    val intentCode: String,
    val autoAction: String,
    val confidence: Int,
    val matchedKeywords: List<String>,
    val replySubject: String?,
    val replyBody: String?,
    val reason: String?,
    val matchedRuleIds: List<Long>,
    val wouldBeBlockedBy: List<String>,
    val attachmentIntentIgnored: Boolean
)

data class ComposedReplySuggestResponse(
    val suggestedRuleIds: List<Long>,
    val suggestedRules: List<SuggestQaRuleResponse>,
    val rulesByCategory: List<CategoryRulesGroupResponse>,
    val gapItems: List<GapItemResponse>,
    val gapDetected: Boolean,
    val matchedCategoryIds: List<Long>,
    val llmEnabled: Boolean,
    val inboundText: String,
    val salutation: String?,
    val greeting: String?,
    val closing: String?,
    val ackOptions: List<AckOptionResponse>
)

data class AckOptionResponse(
    val id: Long,
    val content: String
)

data class SuggestQaRuleResponse(
    val id: Long,
    val categoryId: Long,
    val displayName: String?,
    val sectionTitle: String?,
    val replySubject: String?,
    val replyBody: String,
    val keywords: String
)

data class CategoryRulesGroupResponse(
    val categoryId: Long,
    val categoryCode: String,
    val categoryName: String,
    val composeOrder: Int,
    val rules: List<SuggestQaRuleResponse>
)

data class AiReplyTurnDto(
    val assistantDraft: String,
    val operatorInstruction: String
)

data class AiReplyTurnRequest(
    val turns: List<AiReplyTurnDto> = emptyList(),
    val qaRuleIds: List<Long>? = null,
    val sessionId: String? = null
)

data class AiReplyTurnResponse(
    val draftText: String,
    val usedLlm: Boolean,
    val llmEnabled: Boolean,
    val qaRuleIds: List<Long>
)

private fun InboundMailProcessing.toResponse(
    expertName: String? = null,
    expertCurrentStatus: String? = null,
    expertOperatorStatus: String? = null,
    expertIndexLevel: String? = null
) = InboundMailProcessingResponse(
    id = id,
    senderAccountCode = senderAccountCode,
    imapUid = imapUid,
    messageId = messageId,
    inReplyTo = inReplyTo,
    fromEmail = fromEmail,
    subject = subject,
    body = body,
    cleanedBody = cleanedBody,
    receivedAt = receivedAt.toString(),
    processStatus = processStatus,
    processReason = processReason,
    reasonType = reasonType,
    resolvedAt = resolvedAt?.toString(),
    resolvedBy = resolvedBy,
    expertContactId = expertContactId,
    expertName = expertName,
    expertCurrentStatus = expertCurrentStatus,
    expertOperatorStatus = expertOperatorStatus,
    expertIndexLevel = expertIndexLevel
)

private fun CandidateSuggestion.toResponse() = CandidateResponse(
    contactId = contact.id ?: 0,
    orcidId = contact.orcidId,
    expertName = contact.expertName,
    expertEmail = contact.expertEmail,
    reason = reason,
    confidence = confidence
)

private fun ExpertEmailAlias.toResponse() = ExpertEmailAliasResponse(
    id = id,
    expertContactId = expertContactId,
    email = email,
    normalizedEmail = normalizedEmail,
    source = source,
    verified = verified,
    createdAt = createdAt?.toString()
)

private fun ExpertContact.toPendingMailContactResponse() = PendingMailContactResponse(
    contactId = id ?: 0,
    expertName = expertName,
    expertEmail = expertEmail,
    orcidId = orcidId,
    currentIndexLevel = currentIndexLevel,
    operatorStatus = operatorStatus,
    currentStatus = currentStatus,
    autoReplyEnabled = autoReplyEnabled
)

private fun OperatorActionLog.toResponse() = OperatorActionLogResponse(
    id = id,
    actionType = actionType,
    actionSummary = actionSummary,
    beforeValue = beforeValue,
    afterValue = afterValue,
    operatorName = operatorName,
    note = note,
    createdAt = createdAt?.toString()
)

private fun AutoReplyPreviewResult.toResponse() = AutoReplyPreviewResponse(
    previewKind = previewKind.name,
    intentCode = intentCode.name,
    autoAction = autoAction.name,
    confidence = confidence,
    matchedKeywords = matchedKeywords,
    replySubject = replySubject,
    replyBody = replyBody,
    reason = reason,
    matchedRuleIds = matchedRuleIds,
    wouldBeBlockedBy = wouldBeBlockedBy,
    attachmentIntentIgnored = attachmentIntentIgnored
)

private fun CompositionSuggestResult.toResponse(
    llmEnabled: Boolean,
    inboundText: String,
    frame: ManualReplyFrame
) = ComposedReplySuggestResponse(
    suggestedRuleIds = suggestedRuleIds,
    suggestedRules = suggestedRules.map { it.toResponse() },
    rulesByCategory = rulesByCategory.map { it.toResponse() },
    gapItems = gapItems.map { GapItemResponse(it.text, it.candidateRuleIds) },
    gapDetected = gapDetected,
    matchedCategoryIds = matchedCategoryIds,
    llmEnabled = llmEnabled,
    inboundText = inboundText,
    salutation = frame.salutation,
    greeting = frame.greeting,
    closing = frame.closing,
    ackOptions = frame.ackOptions.map { AckOptionResponse(id = it.id, content = it.content) }
)

private fun SuggestQaRule.toResponse() = SuggestQaRuleResponse(
    id = id,
    categoryId = categoryId,
    displayName = displayName,
    sectionTitle = sectionTitle,
    replySubject = replySubject,
    replyBody = replyBody,
    keywords = keywords
)

private fun CategoryRulesGroup.toResponse() = CategoryRulesGroupResponse(
    categoryId = categoryId,
    categoryCode = categoryCode,
    categoryName = categoryName,
    composeOrder = composeOrder,
    rules = rules.map { it.toResponse() }
)
