package com.weibo.talentintroduction.qa.service

import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.qa.domain.QaCategory
import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
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
        rejectQaVariants(command.variants)
        val answerBody = command.answerBody.trim()
        QaFactBodyPolicy.validate(answerBody, mailVariableService)
        validateRuleMeta(command.keywords, command.matchMode, command.priority)
        val policy = QaReplyPolicy.fromName(command.replyPolicy)
        val normalizedCoverage = QaCoverageKeyCatalog.normalizeAndValidate(command.coverageKeys)
        QaCoverageKeyCatalog.validateControlledBody(normalizedCoverage, answerBody)
        val coverageKeys = QaCoverageKeyCatalog.serialize(normalizedCoverage)

        val saved = ruleRepository.save(
            command.toDomain(answerBody)
                .copy(
                    replyBody = answerBody,
                    answerBody = answerBody,
                    coverageKeys = coverageKeys
                )
                .withReplyPolicy(policy)
        )
        val ruleId = saved.id ?: error("QA rule id is required")
        return QaRuleDetail(saved, loadVariantTexts(ruleId))
    }

    @Transactional
    fun updateRule(ruleId: Long, command: QaRuleUpdateCommand): QaRuleDetail {
        val existing = ruleRepository.findById(ruleId)
            .orElseThrow { error("QA rule not found: $ruleId") }
        requireCategoryExists(command.categoryId)
        rejectQaVariants(command.variants)
        val answerBody = command.answerBody.trim()
        QaFactBodyPolicy.validate(answerBody, mailVariableService)
        validateRuleMeta(command.keywords, command.matchMode, command.priority)
        val policy = QaReplyPolicy.fromName(command.replyPolicy)
        val effectiveCoverage = when (command.coverageKeys) {
            null -> QaCoverageKeyCatalog.parseStored(existing.coverageKeys)
            else -> QaCoverageKeyCatalog.normalizeAndValidate(command.coverageKeys)
        }
        QaCoverageKeyCatalog.validateControlledBody(effectiveCoverage, answerBody)
        val coverageKeys = when (command.coverageKeys) {
            null -> existing.coverageKeys
            else -> QaCoverageKeyCatalog.serialize(effectiveCoverage)
        }

        val saved = ruleRepository.save(
            existing.copy(
                categoryId = command.categoryId,
                keywords = command.keywords,
                matchMode = command.matchMode.uppercase(),
                priority = command.priority,
                displayName = command.displayName?.trim()?.takeIf { it.isNotEmpty() },
                answerBody = answerBody,
                replyBody = answerBody,
                coverageKeys = coverageKeys,
                enabled = command.enabled
            ).withReplyPolicy(policy)
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
        if (enabled) {
            QaCoverageKeyCatalog.validateControlledBody(
                QaCoverageKeyCatalog.parseStored(existing.coverageKeys),
                existing.answerBody
            )
        }
        val saved = ruleRepository.save(existing.copy(enabled = enabled))
        return QaRuleDetail(saved, loadVariantTexts(ruleId))
    }

    /**
     * I-5: every enabled rule whose stored coverage set contains each controlled
     * key, keyed by that key. Pure read (no repository projection: reuses
     * findAllEnabledOrdered + parseStored), used only for the revoke-confirm copy.
     */
    fun listCoverageAuthorities(): Map<String, List<AuthorityRuleResponse>> {
        val byKey = mutableMapOf<String, MutableList<AuthorityRuleResponse>>()
        ruleRepository.findAllEnabledOrdered().forEach { rule ->
            val ruleId = rule.id ?: error("QA rule id is required")
            QaCoverageKeyCatalog.parseStored(rule.coverageKeys).forEach { key ->
                byKey.getOrPut(key) { mutableListOf() }
                    .add(AuthorityRuleResponse(ruleId, rule.displayName))
            }
        }
        val ordered = LinkedHashMap<String, List<AuthorityRuleResponse>>()
        QaCoverageKeyCatalog.controlledGroups().forEach { group ->
            group.keys.sorted().forEach { key ->
                ordered[key] = byKey[key].orEmpty()
            }
        }
        return ordered
    }

    private fun rejectQaVariants(variants: List<String>) {
        if (variants.isNotEmpty()) {
            throw IllegalArgumentException("QA rule content variants are no longer supported")
        }
    }

    private fun loadVariantTexts(ruleId: Long): List<String> =
        contentVariantService.listByOwner(ContentVariantOwnerType.QA_RULE, ruleId).map { it.content }

    private fun requireCategoryExists(categoryId: Long) {
        require(categoryRepository.existsById(categoryId)) { "QA category not found: $categoryId" }
    }

    private fun validateRuleMeta(keywords: String, matchMode: String, priority: Int) {
        require(keywords.split(",").any { it.isNotBlank() }) { "keywords is required" }
        require(matchMode.uppercase() in setOf("ANY", "ALL")) { "matchMode must be ANY or ALL" }
        require(priority > 0) { "priority must be positive" }
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

data class AuthorityRuleResponse(
    val id: Long,
    val displayName: String?
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
    val answerBody: String,
    val replyPolicy: String = QaReplyPolicy.REVIEW.name,
    val replySubject: String? = null,
    val displayName: String? = null,
    val enabled: Boolean = true,
    val variants: List<String> = emptyList(),
    val coverageKeys: List<String>? = null
) {
    fun toDomain(answerBody: String): QaRule =
        QaRule(
            categoryId = categoryId,
            keywords = keywords,
            matchMode = matchMode.uppercase(),
            priority = priority,
            replySubject = replySubject,
            replyBody = answerBody,
            answerBody = answerBody,
            displayName = displayName?.trim()?.takeIf { it.isNotEmpty() },
            enabled = enabled
        )
}

data class QaRuleUpdateCommand(
    val categoryId: Long,
    val keywords: String,
    val matchMode: String,
    val priority: Int,
    val answerBody: String,
    val replyPolicy: String = QaReplyPolicy.REVIEW.name,
    val replySubject: String? = null,
    val replyBody: String? = null,
    val displayName: String? = null,
    val enabled: Boolean,
    val variants: List<String> = emptyList(),
    val coverageKeys: List<String>? = null
)
