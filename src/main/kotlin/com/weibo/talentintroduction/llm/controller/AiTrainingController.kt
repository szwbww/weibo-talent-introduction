package com.weibo.talentintroduction.llm.controller

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.llm.service.AiPromptConfigDto
import com.weibo.talentintroduction.llm.service.AiPromptConfigEffectiveDto
import com.weibo.talentintroduction.llm.service.AiPromptConfigService
import com.weibo.talentintroduction.llm.service.AiReplyContextService
import com.weibo.talentintroduction.llm.service.AiReplyDraftPreviewService
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiReplyModel
import com.weibo.talentintroduction.llm.service.AiTrainingQaDto
import com.weibo.talentintroduction.llm.service.AiTrainingQaPage
import com.weibo.talentintroduction.llm.service.AiTrainingQaService
import com.weibo.talentintroduction.llm.service.AiTrainingDialogueService
import com.weibo.talentintroduction.llm.service.AiTrainingDialogueView
import com.weibo.talentintroduction.llm.service.AiTrainingEvaluationRequest
import com.weibo.talentintroduction.llm.service.AiTrainingEvaluationResponse
import com.weibo.talentintroduction.llm.service.AiTrainingEvaluationService
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.service.InboundMailTagService
import com.weibo.talentintroduction.mail.service.TagView
import com.weibo.talentintroduction.llm.service.TrustReplyAssembleRequest
import com.weibo.talentintroduction.llm.service.TrustReplyItemGenerationKind
import com.weibo.talentintroduction.llm.service.TrustReplyItemHandling
import com.weibo.talentintroduction.llm.service.TrustReplyLockedItemRequest
import com.weibo.talentintroduction.llm.service.TrustReplySourceRef
import com.weibo.talentintroduction.llm.service.TrustReplySourceType
import com.weibo.talentintroduction.llm.service.TrustReplyWorkbenchException
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ai-training")
class AiTrainingController(
    private val aiTrainingQaService: AiTrainingQaService,
    private val aiPromptConfigService: AiPromptConfigService,
    private val aiReplyDraftService: AiReplyDraftService,
    private val aiReplyDraftPreviewService: AiReplyDraftPreviewService,
    private val aiReplyContextService: AiReplyContextService,
    private val expertContactRepository: ExpertContactRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val expertSearchService: ExpertSearchService,
    private val inboundMailTagService: InboundMailTagService,
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val llmProperties: LlmProperties,
    private val aiTrainingDialogueService: AiTrainingDialogueService,
    private val aiTrainingEvaluationService: AiTrainingEvaluationService
) {
    companion object {
        /** Spring JDBC rejects empty IN lists; ignored when unrestricted=true. */
        private val UNRESTRICTED_CONTACT_IDS = listOf(-1L)
    }

    @GetMapping("/qa")
    fun listQa(
        @RequestParam(required = false) source: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): AiTrainingQaPage = aiTrainingQaService.list(source, page, size)

    @PostMapping("/qa")
    fun createQa(@RequestBody request: AiTrainingQaUpsertRequest): AiTrainingQaDto =
        aiTrainingQaService.create(request.topic, request.question, request.answer, request.keywords)

    @PutMapping("/qa/{id}")
    fun updateQa(
        @PathVariable id: Long,
        @RequestBody request: AiTrainingQaUpsertRequest
    ): AiTrainingQaDto = aiTrainingQaService.update(id, request.topic, request.question, request.answer, request.keywords)

    @DeleteMapping("/qa/{id}")
    fun deleteQa(@PathVariable id: Long) {
        aiTrainingQaService.delete(id)
    }

    @GetMapping("/dialogues")
    fun listDialogues(): List<AiTrainingDialogueView> = aiTrainingDialogueService.listViews()

    @GetMapping("/prompt-config")
    fun getPromptConfig(): AiPromptConfigDto = aiPromptConfigService.getDto()

    @GetMapping("/prompt-config/effective")
    fun getEffectivePromptConfig(): AiPromptConfigEffectiveDto = aiPromptConfigService.getEffectiveDto()

    @PutMapping("/prompt-config")
    fun updatePromptConfig(@RequestBody request: AiPromptConfigDto): AiPromptConfigDto =
        aiPromptConfigService.update(request.freeFormSystemPrompt, request.constraints)

    @GetMapping("/simulate/mails")
    fun listSimulateMails(
        @RequestParam(required = false) expertTag: String?,
        @RequestParam(required = false) inboundTagKey: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): AiTrainingSimulateMailPage {
        val normalizedPage = page.coerceAtLeast(0)
        val normalizedSize = size.coerceIn(1, 100)
        val normalizedExpertTag = expertTag?.trim()?.takeIf { it.isNotEmpty() }
        val (qaRuleId, customLabel) = parseTagKey(inboundTagKey)

        val contactFilter = resolveContactFilter(normalizedExpertTag)
        if (contactFilter.emptyResult) {
            return AiTrainingSimulateMailPage(items = emptyList(), total = 0, page = normalizedPage, size = normalizedSize)
        }

        val offset = normalizedPage * normalizedSize
        val records = mailRecordRepository.findInboundMailsForSimulation(
            unrestricted = contactFilter.unrestricted,
            contactIds = contactFilter.contactIds,
            qaRuleId = qaRuleId,
            customLabel = customLabel,
            limit = normalizedSize,
            offset = offset
        )
        val total = mailRecordRepository.countInboundMailsForSimulation(
            unrestricted = contactFilter.unrestricted,
            contactIds = contactFilter.contactIds,
            qaRuleId = qaRuleId,
            customLabel = customLabel
        )

        val contactIds = records.mapNotNull { it.expertContactId }.distinct()
        val contactsById = if (contactIds.isEmpty()) {
            emptyMap()
        } else {
            expertContactRepository.findAllById(contactIds).associateBy { requireNotNull(it.id) }
        }
        val expertTagsByOrcid = loadExpertTagsByOrcid(contactsById.values.mapNotNull { it.orcidId }.distinct())
        val inboundTagsByContact = loadInboundTagsByContact(contactIds, records)

        val items = records.mapNotNull { mail ->
            val contactId = mail.expertContactId ?: return@mapNotNull null
            val contact = contactsById[contactId] ?: return@mapNotNull null
            val body = mail.cleanedBody?.takeIf { it.isNotBlank() } ?: mail.body.orEmpty()
            AiTrainingSimulateMailItem(
                mailRecordId = requireNotNull(mail.id),
                expertContactId = contactId,
                expertName = contact.expertName,
                expertEmail = contact.expertEmail,
                subject = mail.subject,
                receivedAt = mail.receivedAt?.toString(),
                body = body,
                inboundTags = inboundTagsByContact[contactId].orEmpty(),
                expertTags = expertTagsByOrcid[contact.orcidId].orEmpty()
            )
        }

        return AiTrainingSimulateMailPage(
            items = items,
            total = total,
            page = normalizedPage,
            size = normalizedSize
        )
    }

    @GetMapping("/simulate/experts")
    fun listSimulateExperts(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "50") limit: Int
    ): List<AiTrainingSimulateExpertResponse> {
        val normalizedLimit = limit.coerceIn(1, 200)
        val normalizedKeyword = keyword?.trim()?.takeIf { it.isNotEmpty() }
        val contactIds = mailRecordRepository.findExpertContactIdsWithInboundMail(normalizedKeyword, normalizedLimit)
        if (contactIds.isEmpty()) {
            return emptyList()
        }
        val contacts = expertContactRepository.findAllById(contactIds).associateBy { it.id }
        return contactIds.mapNotNull { contactId ->
            val contact = contacts[contactId] ?: return@mapNotNull null
            val latestInbound = mailRecordRepository.findLatestInboundByExpertContactId(contactId)
            AiTrainingSimulateExpertResponse(
                contactId = contactId,
                expertName = contact.expertName,
                expertEmail = contact.expertEmail,
                lastSubject = latestInbound?.subject
            )
        }
    }

    @PostMapping("/simulate")
    fun simulate(@RequestBody request: AiTrainingSimulateRequest): AiTrainingSimulateResponse {
        val (contact, inboundMail) = if (request.mailRecordId != null) {
            val mail = mailRecordRepository.findById(request.mailRecordId).orElse(null)
                ?: throw IllegalArgumentException("Mail record not found: ${request.mailRecordId}")
            if (mail.direction != "INBOUND")
                throw IllegalArgumentException("Mail ${request.mailRecordId} is not INBOUND")
            val contactId = mail.expertContactId
                ?: throw IllegalArgumentException("Mail ${request.mailRecordId} has no expertContactId")
            val c = expertContactRepository.findById(contactId).orElse(null)
                ?: throw IllegalArgumentException("Expert contact not found: $contactId")
            c to mail
        } else {
            val contactId = request.expertContactId
                ?: throw IllegalArgumentException("Either mailRecordId or expertContactId must be provided")
            val c = expertContactRepository.findById(contactId).orElse(null)
                ?: throw IllegalArgumentException("Expert contact not found: $contactId")
            val latest = mailRecordRepository.findLatestInboundByExpertContactId(contactId)
                ?: throw IllegalArgumentException("No inbound mail found for contact: $contactId")
            c to latest
        }

        val contactId = requireNotNull(contact.id)
        val inboundText = inboundMail.cleanedBody?.takeIf { it.isNotBlank() } ?: inboundMail.body.orEmpty()
        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
        val knowledge = aiTrainingQaService.buildKnowledgeContext(inboundText)
        val context = aiReplyContextService.build(contact, records, inboundText, knowledge, inboundMail.messageId)

        val result = aiReplyDraftService.generate(
            inboundText = inboundText,
            operatorTurns = emptyList(),
            operatorInstruction = request.promptOverride,
            expertProfile = context.profileText,
            mailHistory = context.mailHistory,
            contextWarnings = context.contextWarnings,
            replyModel = request.model,
            researchProfileSufficient = context.researchProfileSufficient
        )
        val preview = aiReplyDraftPreviewService.preview(
            raw = result.draftText,
            contact = contact,
            senderAccountCode = inboundMail.senderAccountCode
        )
        return AiTrainingSimulateResponse(
            draftText = result.draftText,
            renderedDraftText = preview.renderedText,
            usedLlm = result.usedLlm,
            llmEnabled = llmProperties.enabled,
            mode = result.mode.name,
            inboundText = inboundText,
            inboundSubject = inboundMail.subject,
            expertName = contact.expertName,
            expertEmail = contact.expertEmail,
            qaRuleIds = result.qaRuleIds,
            requestCount = result.requestCount,
            groundedRequestCount = result.groundedRequestCount,
            unsupportedRequests = result.unsupportedRequests,
            contextWarnings = mergeWarningsPreserveOrder(result.contextWarnings, preview.warningCodes),
            injectedDialogRefs = result.fewShotDialogRefs,
            selectedModel = result.selectedModel,
            requestCoverage = result.requestFacts.map {
                RequestCoverageItem(
                    index = it.index,
                    requestText = it.requestText,
                    status = it.status.name,
                    factRuleIds = it.factRuleIds,
                    intents = it.intents.map { intent ->
                        IntentCoverageResponse(
                            intentKey = intent.intentKey,
                            title = intent.title,
                            status = intent.status,
                            evidenceRuleIds = intent.evidenceRuleIds,
                            missingEvidenceKeys = intent.missingEvidenceKeys,
                            requiresResearchContext = intent.requiresResearchContext
                        )
                    }
                )
            },
            generationState = result.generationState.name,
            draftReadiness = result.draftReadiness.name
        )
    }

    @PostMapping("/simulate/evaluations")
    fun saveSimulationEvaluation(
        @RequestBody request: AiTrainingEvaluationHttpRequest
    ): AiTrainingEvaluationResponse = aiTrainingEvaluationService.save(request.toDomain())

    @ExceptionHandler(TrustReplyWorkbenchException::class)
    fun handleTrustReplyException(ex: TrustReplyWorkbenchException): ResponseEntity<TrustReplyErrorResponse> =
        ResponseEntity.status(ex.status).body(TrustReplyErrorResponse(code = ex.code))

    private fun AiTrainingEvaluationHttpRequest.toDomain(): AiTrainingEvaluationRequest {
        val sourceType = runCatching { TrustReplySourceType.valueOf(source.sourceType.trim()) }
            .getOrElse { throw TrustReplyWorkbenchException(HttpStatus.BAD_REQUEST, "TRUST_REPLY_SOURCE_INVALID") }
        if (source.sourceId <= 0) {
            throw TrustReplyWorkbenchException(HttpStatus.BAD_REQUEST, "TRUST_REPLY_SOURCE_INVALID")
        }
        if (sourceType != TrustReplySourceType.TRAINING_MAIL) {
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_TRAINING_SOURCE_REQUIRED")
        }
        return AiTrainingEvaluationRequest(
            assembly = TrustReplyAssembleRequest(
                source = TrustReplySourceRef(sourceType, source.sourceId),
                expectedSourceVersion = expectedSourceVersion,
                expectedEvidenceSetVersion = expectedEvidenceSetVersion,
                lockedItems = lockedItems.map { it.toDomain() },
                requestedFactIds = requestedFactIds,
                requestFactSelections = requestFactSelections?.map { it.toDomain() },
                frameSnapshot = frameSnapshot?.toDomain()
            ),
            rating = rating,
            note = note,
            operatorName = operatorName
        )
    }

    private fun TrustReplyRequestFactSelectionHttpRequest.toDomain() =
        com.weibo.talentintroduction.llm.service.TrustReplyRequestFactSelection(
            requestKey = requestKey.orEmpty(),
            factRuleIds = factRuleIds.orEmpty()
        )

    private fun TrustReplyFrameSnapshotHttpRequest.toDomain() =
        com.weibo.talentintroduction.llm.service.TrustReplyFrameSnapshot(
            selection = selection?.let {
                com.weibo.talentintroduction.llm.service.TrustReplyFrameSelection(
                    salutationSnippetId = it.salutationSnippetId,
                    greetingSnippetId = it.greetingSnippetId,
                    ackSnippetId = it.ackSnippetId,
                    closingSnippetId = it.closingSnippetId
                )
            },
            version = version.orEmpty()
        )

    private fun TrustReplyLockedItemHttpRequest.toDomain(): TrustReplyLockedItemRequest {
        val handling = runCatching { TrustReplyItemHandling.valueOf(this.handling.trim().uppercase()) }
            .getOrElse { throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_HANDLING_INVALID") }
        val generationKind = runCatching { TrustReplyItemGenerationKind.valueOf(this.generationKind.trim()) }
            .getOrElse { throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_GENERATION_KIND_INVALID") }
        return TrustReplyLockedItemRequest(
            requestKey = requestKey,
            versionId = versionId,
            handling = handling,
            answerText = answerText,
            claims = claims,
            model = model,
            generationKind = generationKind,
            evidenceSetVersion = evidenceSetVersion,
            sourceVersion = sourceVersion,
            operatorInstructionHash = operatorInstructionHash,
            operatorInstruction = operatorInstruction
        )
    }

    private fun mergeWarningsPreserveOrder(existing: List<String>, extra: List<String>): List<String> {
        val seen = linkedSetOf<String>()
        return (existing + extra).filter { seen.add(it) }
    }

    private data class ContactFilter(
        val unrestricted: Boolean,
        val contactIds: List<Long>,
        val emptyResult: Boolean
    )

    private fun resolveContactFilter(expertTag: String?): ContactFilter {
        if (expertTag == null) {
            return ContactFilter(unrestricted = true, contactIds = UNRESTRICTED_CONTACT_IDS, emptyResult = false)
        }
        val orcidIds = findOrcidIdsByExpertTag(expertTag)
        if (orcidIds.isEmpty()) {
            return ContactFilter(unrestricted = false, contactIds = emptyList(), emptyResult = true)
        }
        val contactIds = expertContactRepository.findByOrcidIdIn(orcidIds).mapNotNull { it.id }
        if (contactIds.isEmpty()) {
            return ContactFilter(unrestricted = false, contactIds = emptyList(), emptyResult = true)
        }
        return ContactFilter(unrestricted = false, contactIds = contactIds, emptyResult = false)
    }

    private fun findOrcidIdsByExpertTag(tag: String): List<String> {
        val candidate = expertSearchService.searchExperts(
            size = 1000,
            level = ExpertIndexLevel.CANDIDATE,
            tag = tag
        ).experts
        val application = expertSearchService.searchExperts(
            size = 1000,
            level = ExpertIndexLevel.APPLICATION,
            tag = tag
        ).experts
        return (candidate + application).map { it.orcidId }.distinct()
    }

    private fun loadExpertTagsByOrcid(orcidIds: List<String>): Map<String, List<String>> {
        if (orcidIds.isEmpty()) return emptyMap()
        val candidate = expertSearchService.searchByOrcidIds(orcidIds, ExpertIndexLevel.CANDIDATE)
            .associate { it.orcidId to (it.tags.orEmpty()) }
        val application = expertSearchService.searchByOrcidIds(orcidIds, ExpertIndexLevel.APPLICATION)
            .associate { it.orcidId to (it.tags.orEmpty()) }
        return orcidIds.associateWith { orcidId ->
            (candidate[orcidId].orEmpty() + application[orcidId].orEmpty()).distinct()
        }
    }

    private fun loadInboundTagsByContact(
        contactIds: List<Long>,
        records: List<com.weibo.talentintroduction.mail.domain.MailRecord>
    ): Map<Long, List<AiTrainingInboundTagView>> {
        if (contactIds.isEmpty()) return emptyMap()
        val processingIdByContact = mutableMapOf<Long, Long>()
        records.forEach { mail ->
            val contactId = mail.expertContactId ?: return@forEach
            val processingId = mail.sourceInboundId
                ?: inboundMailProcessingRepository.findAllByExpertContactId(contactId)
                    .maxByOrNull { it.receivedAt ?: it.createdAt ?: java.time.LocalDateTime.MIN }
                    ?.id
            if (processingId != null) {
                processingIdByContact[contactId] = processingId
            }
        }
        val tagsByProcessing = inboundMailTagService.listTagsBatch(processingIdByContact.values.distinct())
        return processingIdByContact.mapValues { (_, processingId) ->
            tagsByProcessing[processingId].orEmpty().map { toInboundTagView(it) }
        }
    }

    private fun toInboundTagView(tag: TagView): AiTrainingInboundTagView {
        val tagKey = when (tag.tagType) {
            "QA" -> "qa:${tag.qaRuleId}"
            else -> "custom:${tag.label}"
        }
        return AiTrainingInboundTagView(
            tagKey = tagKey,
            label = tag.label,
            tagType = tag.tagType
        )
    }

    private fun parseTagKey(tagKey: String?): Pair<Long?, String?> {
        if (tagKey.isNullOrBlank()) return null to null
        return when {
            tagKey.startsWith("qa:") -> tagKey.removePrefix("qa:").toLongOrNull()?.let { it to null }
                ?: throw IllegalArgumentException("Invalid tagKey: $tagKey")
            tagKey.startsWith("custom:") -> null to tagKey.removePrefix("custom:")
            else -> throw IllegalArgumentException("Invalid tagKey: $tagKey")
        }
    }
}

data class AiTrainingSimulateMailPage(
    val items: List<AiTrainingSimulateMailItem>,
    val total: Long,
    val page: Int,
    val size: Int
)

data class AiTrainingSimulateMailItem(
    val mailRecordId: Long,
    val expertContactId: Long,
    val expertName: String?,
    val expertEmail: String,
    val subject: String?,
    val receivedAt: String?,
    val body: String,
    val inboundTags: List<AiTrainingInboundTagView>,
    val expertTags: List<String>
)

data class AiTrainingInboundTagView(
    val tagKey: String,
    val label: String,
    val tagType: String
)

data class AiTrainingSimulateExpertResponse(
    val contactId: Long,
    val expertName: String?,
    val expertEmail: String,
    val lastSubject: String?
)

data class AiTrainingSimulateRequest(
    val mailRecordId: Long? = null,
    val expertContactId: Long? = null,
    val promptOverride: String? = null,
    val model: String? = null
)

data class AiTrainingQaUpsertRequest(
    val topic: String,
    val question: String? = null,
    val answer: String,
    val keywords: String? = null
)

data class AiTrainingSimulateResponse(
    val draftText: String,
    val renderedDraftText: String = "",
    val usedLlm: Boolean,
    val llmEnabled: Boolean,
    val mode: String,
    val inboundText: String,
    val inboundSubject: String?,
    val expertName: String?,
    val expertEmail: String?,
    val qaRuleIds: List<Long> = emptyList(),
    val requestCount: Int = 0,
    val groundedRequestCount: Int = 0,
    val unsupportedRequests: List<String> = emptyList(),
    val contextWarnings: List<String> = emptyList(),
    val injectedDialogRefs: List<String> = emptyList(),
    val selectedModel: String = AiReplyModel.DEEPSEEK_V4_FLASH.name,
    val requestCoverage: List<RequestCoverageItem> = emptyList(),
    val generationState: String = "FALLBACK_NO_RESPONSE",
    val draftReadiness: String = "READY"
)

data class RequestCoverageItem(
    val index: Int,
    val requestText: String,
    val status: String,
    val factRuleIds: List<Long>,
    val intents: List<IntentCoverageResponse> = emptyList()
)

data class IntentCoverageResponse(
    val intentKey: String,
    val title: String,
    val status: String,
    val evidenceRuleIds: List<Long>,
    val missingEvidenceKeys: List<String>,
    val requiresResearchContext: Boolean
)

data class AiTrainingEvaluationHttpRequest(
    val source: TrustReplySourceHttpRequest,
    val expectedSourceVersion: String,
    val expectedEvidenceSetVersion: String,
    val lockedItems: List<TrustReplyLockedItemHttpRequest>,
    val requestedFactIds: List<Long>? = null,
    val requestFactSelections: List<TrustReplyRequestFactSelectionHttpRequest>? = null,
    val frameSnapshot: TrustReplyFrameSnapshotHttpRequest? = null,
    val rating: String?,
    val note: String? = null,
    val operatorName: String? = null
)
// 07（c8）：旧可信工作台控制器九个端点摘除后其源文件整文件删除；本控制器
// （/simulate/evaluations 仍在运行）仍消费下列共享 HTTP 请求/响应形状，
// 故迁入同包本文件（D-10 / X-4 一并处置）。

data class TrustReplySourceHttpRequest(
    val sourceType: String,
    val sourceId: Long
)

data class TrustReplyRequestFactSelectionHttpRequest(
    val requestKey: String? = null,
    val factRuleIds: List<Long>? = null
)

data class TrustReplyFrameSelectionHttpRequest(
    val salutationSnippetId: Long? = null,
    val greetingSnippetId: Long? = null,
    val ackSnippetId: Long? = null,
    val closingSnippetId: Long? = null
)

data class TrustReplyFrameSnapshotHttpRequest(
    val selection: TrustReplyFrameSelectionHttpRequest? = null,
    val version: String? = null
)

data class TrustReplyLockedItemHttpRequest(
    val requestKey: String,
    val versionId: String,
    val handling: String,
    val answerText: String,
    val claims: List<com.weibo.talentintroduction.llm.service.AiReplyItemClaim> = emptyList(),
    val model: String,
    val generationKind: String,
    val evidenceSetVersion: String,
    val sourceVersion: String,
    val operatorInstructionHash: String = "",
    val operatorInstruction: String = ""
)

data class TrustReplyErrorResponse(
    val code: String
)
