package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MailVariableService(
    private val expertSearchService: ExpertSearchService,
    private val mailComposeTemplateService: MailComposeTemplateService,
    private val unsubscribeTokenService: UnsubscribeTokenService? = null,
    private val mailPlaceholderService: MailPlaceholderService = MailPlaceholderService()
) {
    private val log = LoggerFactory.getLogger(MailVariableService::class.java)

    fun buildVariables(account: MailSenderAccount?, expert: ExpertProfile?): Map<String, String> {
        val senderVars = mapOf(
            "senderEmail" to (account?.senderEmail).orEmpty(),
            "senderName" to (account?.senderName).orEmpty(),
            "senderTitle" to account?.senderTitle.orEmpty(),
            "teamName" to account?.teamName.orEmpty(),
            "countryName" to account?.countryName.orEmpty()
        )
        val expertVars = if (expert != null) {
            mapOf(
                "expertName" to expert.displayName,
                "expertFamilyName" to expert.familyNames.orEmpty(),
                "researchFields" to expert.researchFields.orEmpty(),
                "institution" to expert.institution.orEmpty(),
                "keyword" to expert.keyword.orEmpty(),
                "expertCountry" to expert.country.orEmpty(),
                "employment" to expert.employment.orEmpty(),
                "hIndex" to (expert.hIndex?.toString()).orEmpty(),
                "worksCount" to (expert.worksCount?.toString()).orEmpty(),
                "lastPublicationYear" to (expert.lastPublicationYear?.toString()).orEmpty(),
                "degree" to expert.degree.orEmpty(),
                "recentWorkTitle" to (expert.recentWorkTitles?.firstOrNull()).orEmpty(),
                "patentTitle" to (expert.patentTitles?.firstOrNull()).orEmpty()
            )
        } else {
            EXPERT_KEYS.associateWith { "" }
        }
        val unsubscribeVars = mapOf(
            "unsubscribeUrl" to unsubscribeUrl(expert?.email)
        )
        return senderVars + expertVars + unsubscribeVars
    }

    fun variableMetadata(): List<VariableMeta> =
        mailPlaceholderService.variableMetadata()

    fun placeholderKeysIn(vararg texts: String): List<String> {
        return mailPlaceholderService.placeholderKeysIn(*texts)
    }

    fun filterableEsFields(text: String): List<String> {
        return mailPlaceholderService.filterableEsFields(text)
    }

    fun toTemplateVariableItems(variables: Map<String, String>): List<TemplateVariableItem> =
        variables.map { (key, value) ->
            TemplateVariableItem(
                key = key,
                label = VARIABLE_LABELS[key] ?: key,
                value = value,
                filled = value.isNotBlank()
            )
        }

    fun renderForContact(text: String, account: MailSenderAccount?, contact: ExpertContact): String =
        renderPreview(text, account, contact).rendered

    fun renderPreview(text: String, account: MailSenderAccount?, contact: ExpertContact): RenderPreviewResult {
        val expert = resolveExpertProfile(contact)
        val variables = buildVariables(account, expert)
        val rendered = mailComposeTemplateService.renderWithVariables(text, variables)
        val fallbackKeys = detectFallbackKeys(text, variables)
        return RenderPreviewResult(
            rendered = rendered,
            fallbackKeys = fallbackKeys,
            variables = buildPreviewVariables(text, variables, fallbackKeys),
            invalidTokens = unknownPlaceholderTokens(text)
        )
    }

    fun validatePlaceholders(text: String): List<String> {
        return mailPlaceholderService.validatePlaceholders(text)
    }

    fun requireValidPlaceholders(text: String) {
        mailPlaceholderService.requireValidPlaceholders(text)
    }

    private fun buildPreviewVariables(
        text: String,
        variables: Map<String, String>,
        fallbackKeys: List<String>
    ): List<PreviewVariableItem> {
        val metaByKey = variableMetadata().associateBy { it.key }
        val fallbackSet = fallbackKeys.toSet()
        val keysInText = linkedSetOf<String>()
        keysInText.addAll(mailPlaceholderService.placeholderKeysIn(text))
        return keysInText.mapNotNull { key ->
            val meta = metaByKey[key] ?: return@mapNotNull null
            val value = variables[key].orEmpty()
            PreviewVariableItem(
                key = key,
                label = meta.label,
                value = value,
                filled = value.isNotBlank(),
                usedFallback = key in fallbackSet
            )
        }
    }

    private fun unknownPlaceholderTokens(text: String): List<String> {
        return mailPlaceholderService.unknownPlaceholderTokens(text)
    }

    private fun detectFallbackKeys(text: String, variables: Map<String, String>): List<String> {
        return mailPlaceholderService.detectFallbackKeys(text, variables)
    }

    private fun unsubscribeUrl(email: String?): String {
        if (email.isNullOrBlank()) {
            return ""
        }
        val service = unsubscribeTokenService ?: return ""
        if (!service.enabled()) {
            return ""
        }
        return service.unsubscribeUrl(email)
    }

    private fun resolveExpertProfile(contact: ExpertContact): ExpertProfile? {
        val orcidId = contact.orcidId.takeIf { it.isNotBlank() } ?: return null
        return try {
            val level = parseIndexLevel(contact.currentIndexLevel)
            val profile = expertSearchService.findByOrcidId(orcidId, level)
            if (profile != null) {
                return profile
            }
            if (level == ExpertIndexLevel.APPLICATION) {
                expertSearchService.findByOrcidId(orcidId, ExpertIndexLevel.CANDIDATE)
            } else {
                null
            }
        } catch (e: Exception) {
            log.warn("Failed to resolve expert profile for orcidId={}: {}", orcidId, e.message)
            null
        }
    }

    private fun parseIndexLevel(level: String): ExpertIndexLevel =
        runCatching { ExpertIndexLevel.valueOf(level) }.getOrDefault(ExpertIndexLevel.CANDIDATE)

    companion object {
        val EXPERT_KEYS: Set<String> = MailPlaceholderService.EXPERT_KEYS
        val VARIABLE_LABELS: Map<String, String> = MailPlaceholderService.VARIABLE_LABELS
        val ES_FIELD_BY_KEY: Map<String, String?> = MailPlaceholderService.ES_FIELD_BY_KEY
    }
}

data class VariableMeta(
    val key: String,
    val label: String,
    val nullable: Boolean,
    val example: String,
    val esField: String? = null
)

data class PreviewVariableItem(
    val key: String,
    val label: String,
    val value: String,
    val filled: Boolean,
    val usedFallback: Boolean
)

data class RenderPreviewResult(
    val rendered: String,
    val fallbackKeys: List<String>,
    val variables: List<PreviewVariableItem> = emptyList(),
    val invalidTokens: List<String> = emptyList()
)
