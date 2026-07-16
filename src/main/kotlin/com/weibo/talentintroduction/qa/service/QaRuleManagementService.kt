package com.weibo.talentintroduction.qa.service

import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.qa.domain.QaCategory
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.variant.domain.ContentVariantOwnerType
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class QaRuleManagementService(
    private val categoryRepository: QaCategoryRepository,
    private val ruleRepository: QaRuleRepository,
    private val mailVariableService: MailVariableService,
    private val contentVariantService: ContentVariantService
) {
    fun listCategories(): List<QaCategory> =
        categoryRepository.findAllByOrderByCategoryCodeAsc()

    fun createCategory(command: QaCategoryCreateCommand): QaCategory {
        require(command.categoryCode.isNotBlank()) { "categoryCode is required" }
        require(command.categoryName.isNotBlank()) { "categoryName is required" }
        require(!categoryRepository.existsByCategoryCode(command.categoryCode)) {
            "QA category already exists: ${command.categoryCode}"
        }

        return categoryRepository.save(
            QaCategory(
                categoryCode = command.categoryCode,
                categoryName = command.categoryName,
                description = command.description,
                enabled = command.enabled
            )
        )
    }

    fun setCategoryEnabled(categoryCode: String, enabled: Boolean): QaCategory {
        val category = categoryRepository.findByCategoryCode(categoryCode)
            ?: error("QA category not found: $categoryCode")
        return categoryRepository.save(category.copy(enabled = enabled))
    }

    fun listRules(categoryId: Long?): List<QaRuleWithCategory> {
        val categories = categoryRepository.findAllByOrderByCategoryCodeAsc()
            .associateBy { it.id }
        val rules = if (categoryId == null) {
            ruleRepository.findAllByOrderByPriorityAscIdAsc()
        } else {
            ruleRepository.findAllByCategoryIdOrderByPriorityAscIdAsc(categoryId)
        }

        return rules.map { rule ->
            val ruleId = rule.id ?: error("QA rule id is required")
            QaRuleWithCategory(
                rule = rule,
                category = categories[rule.categoryId],
                variants = loadVariantTexts(ruleId)
            )
        }
    }

    @Transactional
    fun createRule(command: QaRuleCreateCommand): QaRuleDetail {
        requireCategoryExists(command.categoryId)
        validateRule(command.keywords, command.matchMode, command.priority, command.replyBody)
        contentVariantService.validateVariantTexts(command.replyBody, command.variants)
        val normalizedCoverage = QaCoverageKeyCatalog.normalizeAndValidate(command.coverageKeys)
        val domain = command.toDomain().copy(coverageKeys = QaCoverageKeyCatalog.serialize(normalizedCoverage))
        val saved = ruleRepository.save(domain)
        val ruleId = saved.id ?: error("QA rule id is required")
        contentVariantService.replaceForOwner(
            ContentVariantOwnerType.QA_RULE,
            ruleId,
            saved.replyBody,
            command.variants
        )
        return QaRuleDetail(saved, loadVariantTexts(ruleId))
    }

    @Transactional
    fun updateRule(ruleId: Long, command: QaRuleUpdateCommand): QaRuleDetail {
        val existing = ruleRepository.findById(ruleId)
            .orElseThrow { error("QA rule not found: $ruleId") }
        requireCategoryExists(command.categoryId)
        validateRule(command.keywords, command.matchMode, command.priority, command.replyBody)
        contentVariantService.validateVariantTexts(command.replyBody, command.variants)

        val newCoverage = if (command.coverageKeys != null) {
            QaCoverageKeyCatalog.serialize(QaCoverageKeyCatalog.normalizeAndValidate(command.coverageKeys))
        } else {
            existing.coverageKeys
        }

        val saved = ruleRepository.save(
            existing.copy(
                categoryId = command.categoryId,
                keywords = command.keywords,
                matchMode = command.matchMode.uppercase(),
                priority = command.priority,
                replySubject = command.replySubject,
                replyBody = command.replyBody,
                displayName = command.displayName?.trim()?.takeIf { it.isNotEmpty() },
                autoReplyEnabled = command.autoReplyEnabled,
                handoffRequired = command.handoffRequired,
                enabled = command.enabled,
                coverageKeys = newCoverage
            )
        )
        contentVariantService.replaceForOwner(
            ContentVariantOwnerType.QA_RULE,
            ruleId,
            saved.replyBody,
            command.variants
        )
        return QaRuleDetail(saved, loadVariantTexts(ruleId))
    }

    @Transactional
    fun deleteRule(ruleId: Long) {
        ruleRepository.findById(ruleId).orElseThrow { error("QA rule not found: $ruleId") }
        contentVariantService.deleteForOwner(ContentVariantOwnerType.QA_RULE, ruleId)
        ruleRepository.deleteById(ruleId)
    }

    fun setRuleEnabled(ruleId: Long, enabled: Boolean): QaRuleDetail {
        val existing = ruleRepository.findById(ruleId)
            .orElseThrow { error("QA rule not found: $ruleId") }
        val saved = ruleRepository.save(existing.copy(enabled = enabled))
        return QaRuleDetail(saved, loadVariantTexts(ruleId))
    }

    private fun loadVariantTexts(ruleId: Long): List<String> =
        contentVariantService.listByOwner(ContentVariantOwnerType.QA_RULE, ruleId).map { it.content }

    private fun requireCategoryExists(categoryId: Long) {
        require(categoryRepository.existsById(categoryId)) { "QA category not found: $categoryId" }
    }

    private fun validateRule(keywords: String, matchMode: String, priority: Int, replyBody: String) {
        require(keywords.split(",").any { it.isNotBlank() }) { "keywords is required" }
        require(matchMode.uppercase() in setOf("ANY", "ALL")) { "matchMode must be ANY or ALL" }
        require(priority > 0) { "priority must be positive" }
        require(replyBody.isNotBlank()) { "replyBody is required" }
        mailVariableService.requireValidPlaceholders(replyBody)
    }
}

data class QaRuleWithCategory(
    val rule: QaRule,
    val category: QaCategory?,
    val variants: List<String> = emptyList()
)

data class QaRuleDetail(
    val rule: QaRule,
    val variants: List<String> = emptyList()
)

data class QaCategoryCreateCommand(
    val categoryCode: String,
    val categoryName: String,
    val description: String?,
    val enabled: Boolean = true
)

data class QaRuleCreateCommand(
    val categoryId: Long,
    val keywords: String,
    val matchMode: String = "ANY",
    val priority: Int = 100,
    val replySubject: String?,
    val replyBody: String,
    val displayName: String? = null,
    val autoReplyEnabled: Boolean = true,
    val handoffRequired: Boolean = false,
    val enabled: Boolean = true,
    val variants: List<String> = emptyList(),
    val coverageKeys: List<String>? = null
) {
    fun toDomain(): QaRule =
        QaRule(
            categoryId = categoryId,
            keywords = keywords,
            matchMode = matchMode.uppercase(),
            priority = priority,
            replySubject = replySubject,
            replyBody = replyBody,
            displayName = displayName?.trim()?.takeIf { it.isNotEmpty() },
            autoReplyEnabled = autoReplyEnabled,
            handoffRequired = handoffRequired,
            enabled = enabled
        )
}

data class QaRuleUpdateCommand(
    val categoryId: Long,
    val keywords: String,
    val matchMode: String,
    val priority: Int,
    val replySubject: String?,
    val replyBody: String,
    val displayName: String? = null,
    val autoReplyEnabled: Boolean,
    val handoffRequired: Boolean,
    val enabled: Boolean,
    val variants: List<String> = emptyList(),
    val coverageKeys: List<String>? = null
)
