package com.weibo.talentintroduction.qa.controller

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.mail.service.VariableMeta
import com.weibo.talentintroduction.qa.domain.QaCategory
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.service.QaCategoryCreateCommand
import com.weibo.talentintroduction.qa.service.QaRuleCreateCommand
import com.weibo.talentintroduction.qa.service.QaRuleManagementService
import com.weibo.talentintroduction.qa.service.QaRuleUpdateCommand
import com.weibo.talentintroduction.qa.service.QaRuleWithCategory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/qa")
class QaRuleManagementController(
    private val service: QaRuleManagementService,
    private val qaRuleAuditService: com.weibo.talentintroduction.qa.service.QaRuleAuditService,
    private val mailVariableService: MailVariableService,
    private val expertContactRepository: ExpertContactRepository
) {
    @GetMapping("/template-variables-meta")
    fun templateVariablesMeta(): List<VariableMeta> =
        mailVariableService.variableMetadata()

    @PostMapping("/render-preview")
    fun renderPreview(@RequestBody request: QaRenderPreviewRequest): QaRenderPreviewResponse {
        val contact = resolvePreviewContact(request)
        val result = mailVariableService.renderPreview(request.text, account = null, contact)
        return QaRenderPreviewResponse(
            rendered = result.rendered,
            fallbackKeys = result.fallbackKeys
        )
    }

    @GetMapping("/categories")
    fun listCategories(): List<QaCategoryResponse> =
        service.listCategories().map { it.toResponse() }

    @PostMapping("/categories")
    fun createCategory(@RequestBody request: QaCategoryCreateRequest): QaCategoryResponse =
        service.createCategory(request.toCommand()).toResponse()

    @PostMapping("/categories/{categoryCode}/enable")
    fun enableCategory(@PathVariable categoryCode: String): QaCategoryResponse =
        service.setCategoryEnabled(categoryCode, true).toResponse()

    @PostMapping("/categories/{categoryCode}/disable")
    fun disableCategory(@PathVariable categoryCode: String): QaCategoryResponse =
        service.setCategoryEnabled(categoryCode, false).toResponse()

    @GetMapping("/rules")
    fun listRules(@RequestParam(required = false) categoryId: Long?): List<QaRuleResponse> =
        service.listRules(categoryId).map { it.toResponse() }

    @PostMapping("/rules")
    fun createRule(@RequestBody request: QaRuleCreateRequest): QaRuleResponse =
        service.createRule(request.toCommand()).toResponse(category = null)

    @PutMapping("/rules/{ruleId}")
    fun updateRule(
        @PathVariable ruleId: Long,
        @RequestBody request: QaRuleUpdateRequest
    ): QaRuleResponse =
        service.updateRule(ruleId, request.toCommand()).toResponse(category = null)

    @PostMapping("/rules/{ruleId}/enable")
    fun enableRule(@PathVariable ruleId: Long): QaRuleResponse =
        service.setRuleEnabled(ruleId, true).toResponse(category = null)

    @PostMapping("/rules/{ruleId}/disable")
    fun disableRule(@PathVariable ruleId: Long): QaRuleResponse =
        service.setRuleEnabled(ruleId, false).toResponse(category = null)

    @GetMapping("/audit/rule-usage")
    fun ruleUsageAudit(
        @RequestParam from: String,
        @RequestParam to: String
    ): com.weibo.talentintroduction.qa.service.QaRuleUsageAuditReport =
        qaRuleAuditService.aggregateRuleUsage(
            from = java.time.LocalDateTime.parse(from),
            to = java.time.LocalDateTime.parse(to)
        )

    private fun resolvePreviewContact(request: QaRenderPreviewRequest): ExpertContact {
        request.contactId?.let { contactId ->
            return expertContactRepository.findById(contactId)
                .orElseThrow { IllegalArgumentException("Expert contact not found: $contactId") }
        }
        val orcidId = request.orcidId?.trim().orEmpty()
        require(orcidId.isNotBlank()) { "contactId or orcidId is required" }
        return ExpertContact(
            campaignId = 0,
            orcidId = orcidId,
            expertEmail = "preview@local",
            expertName = "Preview",
            currentIndexLevel = request.level?.trim()?.takeIf { it.isNotEmpty() } ?: "CANDIDATE"
        )
    }
}

data class QaRenderPreviewRequest(
    val text: String,
    val contactId: Long? = null,
    val orcidId: String? = null,
    val level: String? = null
)

data class QaRenderPreviewResponse(
    val rendered: String,
    val fallbackKeys: List<String>
)

data class QaCategoryCreateRequest(
    val categoryCode: String,
    val categoryName: String,
    val description: String?,
    val enabled: Boolean = true
) {
    fun toCommand(): QaCategoryCreateCommand =
        QaCategoryCreateCommand(
            categoryCode = categoryCode,
            categoryName = categoryName,
            description = description,
            enabled = enabled
        )
}

data class QaRuleCreateRequest(
    val categoryId: Long,
    val keywords: String,
    val matchMode: String = "ANY",
    val priority: Int = 100,
    val replySubject: String?,
    val replyBody: String,
    val displayName: String?,
    val autoReplyEnabled: Boolean = true,
    val handoffRequired: Boolean = false,
    val enabled: Boolean = true
) {
    fun toCommand(): QaRuleCreateCommand =
        QaRuleCreateCommand(
            categoryId = categoryId,
            keywords = keywords,
            matchMode = matchMode,
            priority = priority,
            replySubject = replySubject,
            replyBody = replyBody,
            displayName = displayName,
            autoReplyEnabled = autoReplyEnabled,
            handoffRequired = handoffRequired,
            enabled = enabled
        )
}

data class QaRuleUpdateRequest(
    val categoryId: Long,
    val keywords: String,
    val matchMode: String,
    val priority: Int,
    val replySubject: String?,
    val replyBody: String,
    val displayName: String?,
    val autoReplyEnabled: Boolean,
    val handoffRequired: Boolean,
    val enabled: Boolean
) {
    fun toCommand(): QaRuleUpdateCommand =
        QaRuleUpdateCommand(
            categoryId = categoryId,
            keywords = keywords,
            matchMode = matchMode,
            priority = priority,
            replySubject = replySubject,
            replyBody = replyBody,
            displayName = displayName,
            autoReplyEnabled = autoReplyEnabled,
            handoffRequired = handoffRequired,
            enabled = enabled
        )
}

data class QaCategoryResponse(
    val id: Long?,
    val categoryCode: String,
    val categoryName: String,
    val description: String?,
    val enabled: Boolean
)

data class QaRuleResponse(
    val id: Long?,
    val categoryId: Long,
    val categoryCode: String?,
    val categoryName: String?,
    val keywords: String,
    val matchMode: String,
    val priority: Int,
    val replySubject: String?,
    val replyBody: String,
    val displayName: String?,
    val autoReplyEnabled: Boolean,
    val handoffRequired: Boolean,
    val enabled: Boolean
)

private fun QaCategory.toResponse(): QaCategoryResponse =
    QaCategoryResponse(
        id = id,
        categoryCode = categoryCode,
        categoryName = categoryName,
        description = description,
        enabled = enabled
    )

private fun QaRuleWithCategory.toResponse(): QaRuleResponse =
    rule.toResponse(category)

private fun QaRule.toResponse(category: QaCategory?): QaRuleResponse =
    QaRuleResponse(
        id = id,
        categoryId = categoryId,
        categoryCode = category?.categoryCode,
        categoryName = category?.categoryName,
        keywords = keywords,
        matchMode = matchMode,
        priority = priority,
        replySubject = replySubject,
        replyBody = replyBody,
        displayName = displayName,
        autoReplyEnabled = autoReplyEnabled,
        handoffRequired = handoffRequired,
        enabled = enabled
    )
