package com.weibo.talentintroduction.llm.controller

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.llm.service.AiPromptConfigDto
import com.weibo.talentintroduction.llm.service.AiPromptConfigEffectiveDto
import com.weibo.talentintroduction.llm.service.AiPromptConfigService
import com.weibo.talentintroduction.llm.service.AiReplyContextBuilder
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiTrainingQaDto
import com.weibo.talentintroduction.llm.service.AiTrainingQaPage
import com.weibo.talentintroduction.llm.service.AiTrainingQaService
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.service.InboundMailTagService
import com.weibo.talentintroduction.mail.service.TagView
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
    private val aiReplyContextBuilder: AiReplyContextBuilder,
    private val expertContactRepository: ExpertContactRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val expertSearchService: ExpertSearchService,
    private val inboundMailTagService: InboundMailTagService,
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val llmProperties: LlmProperties
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
        val contact = expertContactRepository.findById(request.expertContactId).orElse(null)
            ?: throw IllegalArgumentException("Expert contact not found: ${request.expertContactId}")
        val latestInbound = mailRecordRepository.findLatestInboundByExpertContactId(request.expertContactId)
            ?: throw IllegalArgumentException("No inbound mail found for contact: ${request.expertContactId}")
        val inboundText = latestInbound.cleanedBody?.takeIf { it.isNotBlank() } ?: latestInbound.body.orEmpty()
        val mailHistory = aiReplyContextBuilder.buildMailHistory(
            mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(request.expertContactId)
        )
        val profile = aiReplyContextBuilder.appendKnowledgeToProfile(
            aiReplyContextBuilder.buildExpertProfile(contact),
            aiTrainingQaService.buildKnowledgeContext()
        )
        val result = aiReplyDraftService.generate(
            inboundText = inboundText,
            operatorTurns = emptyList(),
            operatorInstruction = request.promptOverride,
            expertProfile = profile,
            mailHistory = mailHistory,
            simulateOnly = true
        )
        return AiTrainingSimulateResponse(
            draftText = result.draftText,
            usedLlm = result.usedLlm,
            llmEnabled = llmProperties.enabled,
            mode = result.mode.name,
            inboundText = inboundText,
            inboundSubject = latestInbound.subject,
            expertName = contact.expertName,
            expertEmail = contact.expertEmail
        )
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
    val expertContactId: Long,
    val promptOverride: String? = null
)

data class AiTrainingQaUpsertRequest(
    val topic: String,
    val question: String? = null,
    val answer: String,
    val keywords: String? = null
)

data class AiTrainingSimulateResponse(
    val draftText: String,
    val usedLlm: Boolean,
    val llmEnabled: Boolean,
    val mode: String,
    val inboundText: String,
    val inboundSubject: String?,
    val expertName: String?,
    val expertEmail: String?
)
