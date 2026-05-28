package com.weibo.talentintroduction.qa.service

import com.weibo.talentintroduction.qa.domain.QaCategory
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.springframework.stereotype.Service

@Service
class QaRuleManagementService(
    private val categoryRepository: QaCategoryRepository,
    private val ruleRepository: QaRuleRepository
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
            QaRuleWithCategory(
                rule = rule,
                category = categories[rule.categoryId]
            )
        }
    }

    fun createRule(command: QaRuleCreateCommand): QaRule {
        requireCategoryExists(command.categoryId)
        validateRule(command.keywords, command.matchMode, command.priority, command.replyBody)
        return ruleRepository.save(command.toDomain())
    }

    fun updateRule(ruleId: Long, command: QaRuleUpdateCommand): QaRule {
        val existing = ruleRepository.findById(ruleId)
            .orElseThrow { error("QA rule not found: $ruleId") }
        requireCategoryExists(command.categoryId)
        validateRule(command.keywords, command.matchMode, command.priority, command.replyBody)

        return ruleRepository.save(
            existing.copy(
                categoryId = command.categoryId,
                keywords = command.keywords,
                matchMode = command.matchMode.uppercase(),
                priority = command.priority,
                replySubject = command.replySubject,
                replyBody = command.replyBody,
                autoReplyEnabled = command.autoReplyEnabled,
                handoffRequired = command.handoffRequired,
                enabled = command.enabled
            )
        )
    }

    fun setRuleEnabled(ruleId: Long, enabled: Boolean): QaRule {
        val existing = ruleRepository.findById(ruleId)
            .orElseThrow { error("QA rule not found: $ruleId") }
        return ruleRepository.save(existing.copy(enabled = enabled))
    }

    private fun requireCategoryExists(categoryId: Long) {
        require(categoryRepository.existsById(categoryId)) { "QA category not found: $categoryId" }
    }

    private fun validateRule(keywords: String, matchMode: String, priority: Int, replyBody: String) {
        require(keywords.split(",").any { it.isNotBlank() }) { "keywords is required" }
        require(matchMode.uppercase() in setOf("ANY", "ALL")) { "matchMode must be ANY or ALL" }
        require(priority > 0) { "priority must be positive" }
        require(replyBody.isNotBlank()) { "replyBody is required" }
    }
}

data class QaRuleWithCategory(
    val rule: QaRule,
    val category: QaCategory?
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
    val autoReplyEnabled: Boolean = true,
    val handoffRequired: Boolean = false,
    val enabled: Boolean = true
) {
    fun toDomain(): QaRule =
        QaRule(
            categoryId = categoryId,
            keywords = keywords,
            matchMode = matchMode.uppercase(),
            priority = priority,
            replySubject = replySubject,
            replyBody = replyBody,
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
    val autoReplyEnabled: Boolean,
    val handoffRequired: Boolean,
    val enabled: Boolean
)
