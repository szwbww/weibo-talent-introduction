package com.weibo.talentintroduction.llm.controller

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.llm.service.AiPromptConfigDto
import com.weibo.talentintroduction.llm.service.AiPromptConfigService
import com.weibo.talentintroduction.llm.service.AiReplyContextBuilder
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiTrainingQaPage
import com.weibo.talentintroduction.llm.service.AiTrainingQaService
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.springframework.web.bind.annotation.GetMapping
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
    private val llmProperties: LlmProperties
) {
    @GetMapping("/qa")
    fun listQa(
        @RequestParam(required = false) source: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): AiTrainingQaPage = aiTrainingQaService.list(source, page, size)

    @GetMapping("/prompt-config")
    fun getPromptConfig(): AiPromptConfigDto = aiPromptConfigService.getDto()

    @PutMapping("/prompt-config")
    fun updatePromptConfig(@RequestBody request: AiPromptConfigDto): AiPromptConfigDto =
        aiPromptConfigService.update(request.freeFormSystemPrompt, request.constraints)

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
}

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
