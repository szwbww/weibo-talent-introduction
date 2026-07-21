package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.util.HtmlUtils

internal object ExpertRecipientNamePolicy {
    private val orcidPattern = Regex("""^\d{4}-\d{4}-\d{4}-\d{3}[\dXx]$""")
    private val emailPrefixPattern = Regex("""^EMAIL-""")

    fun resolveRecipientName(contact: ExpertContact, expertProfile: ExpertProfile?): String? {
        val contactName = contact.expertName?.trim()
        val profileGiven = expertProfile?.givenNames?.trim().orEmpty()
        val profileFamily = expertProfile?.familyNames?.trim().orEmpty()
        val contactOrcid = contact.orcidId?.trim().orEmpty()
        val contactEmail = contact.expertEmail?.trim().orEmpty()
        val profileOrcid = expertProfile?.orcidId?.trim().orEmpty()
        val profileEmail = expertProfile?.email?.trim().orEmpty()
        val profileEsDocId = expertProfile?.esDocId?.trim().orEmpty()

        fun isKnownTechId(name: String): Boolean =
            isTechnicalId(name) ||
                name.equals(contactOrcid, ignoreCase = true) ||
                name.equals(contactEmail, ignoreCase = true) ||
                name.equals(profileOrcid, ignoreCase = true) ||
                name.equals(profileEmail, ignoreCase = true) ||
                name.equals(profileEsDocId, ignoreCase = true)

        val profileName = buildString {
            if (profileGiven.isNotBlank() && !isKnownTechId(profileGiven)) append(profileGiven)
            if (profileFamily.isNotBlank() && !isKnownTechId(profileFamily)) {
                if (isNotEmpty()) append(" ")
                append(profileFamily)
            }
        }.trim()

        return if (profileName.isNotBlank() && !isKnownTechId(profileName)) {
            profileName
        } else if (!contactName.isNullOrBlank() && !isKnownTechId(contactName)) {
            contactName
        } else {
            null
        }
    }

    fun resolveExpertFullName(expertProfile: ExpertProfile?, contact: ExpertContact? = null): String {
        if (expertProfile == null) return ""
        val given = expertProfile.givenNames?.trim().orEmpty()
        val family = expertProfile.familyNames?.trim().orEmpty()
        val profileOrcid = expertProfile.orcidId?.trim().orEmpty()
        val profileEmail = expertProfile.email?.trim().orEmpty()
        val profileEsDocId = expertProfile.esDocId?.trim().orEmpty()
        val contactOrcid = contact?.orcidId?.trim().orEmpty()
        val contactEmail = contact?.expertEmail?.trim().orEmpty()

        fun isKnownTechId(name: String): Boolean =
            isTechnicalId(name) ||
                name.equals(profileOrcid, ignoreCase = true) ||
                name.equals(profileEmail, ignoreCase = true) ||
                name.equals(profileEsDocId, ignoreCase = true) ||
                name.equals(contactOrcid, ignoreCase = true) ||
                name.equals(contactEmail, ignoreCase = true)

        val fullName = buildString {
            if (given.isNotBlank() && !isKnownTechId(given)) append(given)
            if (family.isNotBlank() && !isKnownTechId(family)) {
                if (isNotEmpty()) append(" ")
                append(family)
            }
        }.trim()
        return if (fullName.isNotBlank() && !isKnownTechId(fullName)) fullName else ""
    }

    fun resolveFamilyName(expertProfile: ExpertProfile?, contact: ExpertContact? = null): String {
        val family = expertProfile?.familyNames?.trim().orEmpty()
        if (family.isBlank()) return ""
        if (isTechnicalId(family)) return ""
        val profileOrcid = expertProfile?.orcidId?.trim().orEmpty()
        val profileEmail = expertProfile?.email?.trim().orEmpty()
        val profileEsDocId = expertProfile?.esDocId?.trim().orEmpty()
        val contactOrcid = contact?.orcidId?.trim().orEmpty()
        val contactEmail = contact?.expertEmail?.trim().orEmpty()
        if (family.equals(profileOrcid, ignoreCase = true) ||
            family.equals(profileEmail, ignoreCase = true) ||
            family.equals(profileEsDocId, ignoreCase = true) ||
            family.equals(contactOrcid, ignoreCase = true) ||
            family.equals(contactEmail, ignoreCase = true)
        ) return ""
        return family
    }

    fun isTechnicalId(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.contains("@")) return true
        if (emailPrefixPattern.containsMatchIn(trimmed)) return true
        if (orcidPattern.matches(trimmed)) return true
        return false
    }
}

@Service
class MailVariableService(
    private val expertSearchService: ExpertSearchService,
    private val mailComposeTemplateService: MailComposeTemplateService,
    private val unsubscribeTokenService: UnsubscribeTokenService? = null,
    private val mailPlaceholderService: MailPlaceholderService = MailPlaceholderService()
) {
    private val log = LoggerFactory.getLogger(MailVariableService::class.java)

    fun buildVariables(
        account: MailSenderAccount?,
        expert: ExpertProfile?,
        unsubscribeEmail: String? = expert?.email,
        previewFallbacks: Boolean = false,
        contact: ExpertContact? = null
    ): Map<String, String> {
        val senderVars = mapOf(
            "senderEmail" to (account?.senderEmail).orEmpty(),
            "senderName" to (account?.senderName).orEmpty(),
            "senderTitle" to account?.senderTitle.orEmpty(),
            "teamName" to account?.teamName.orEmpty(),
            "countryName" to account?.countryName.orEmpty()
        )
        val expertVars = if (expert != null) {
            mapOf(
                "expertName" to resolveExpertName(expert, contact),
                "expertFamilyName" to ExpertRecipientNamePolicy.resolveFamilyName(expert, contact),
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
            "unsubscribeUrl" to unsubscribeUrl(unsubscribeEmail, previewFallbacks)
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
        renderContact(text, account, contact, previewFallbacks = false).rendered

    fun renderHtmlForContact(html: String, account: MailSenderAccount?, contact: ExpertContact): String {
        val expert = resolveExpertProfile(contact)
        val variables = buildVariables(account, expert, contact.expertEmail, previewFallbacks = false, contact = contact)
            .mapValues { (_, value) -> HtmlUtils.htmlEscape(value) }
        return mailComposeTemplateService.renderWithVariables(html, variables)
    }

    fun renderPreview(text: String, account: MailSenderAccount?, contact: ExpertContact): RenderPreviewResult =
        renderContact(text, account, contact, previewFallbacks = true)

    private fun renderContact(
        text: String,
        account: MailSenderAccount?,
        contact: ExpertContact,
        previewFallbacks: Boolean
    ): RenderPreviewResult {
        val expert = resolveExpertProfile(contact)
        val variables = buildVariables(account, expert, contact.expertEmail, previewFallbacks, contact = contact)
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

    private fun unsubscribeUrl(email: String?, previewFallbacks: Boolean): String {
        if (email.isNullOrBlank()) {
            return ""
        }
        val service = unsubscribeTokenService ?: return previewUnsubscribeUrl(previewFallbacks)
        if (!service.enabled()) {
            return previewUnsubscribeUrl(previewFallbacks)
        }
        return service.unsubscribeUrl(email)
    }

    private fun previewUnsubscribeUrl(previewFallbacks: Boolean): String =
        if (previewFallbacks) PREVIEW_UNSUBSCRIBE_URL else ""

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

    private fun resolveExpertName(expert: ExpertProfile, contact: ExpertContact? = null): String {
        return ExpertRecipientNamePolicy.resolveExpertFullName(expert, contact)
    }

    companion object {
        val EXPERT_KEYS: Set<String> = MailPlaceholderService.EXPERT_KEYS
        val VARIABLE_LABELS: Map<String, String> = MailPlaceholderService.VARIABLE_LABELS
        val ES_FIELD_BY_KEY: Map<String, String?> = MailPlaceholderService.ES_FIELD_BY_KEY
        private const val PREVIEW_UNSUBSCRIBE_URL = "https://example.com/u/unsubscribe?token=preview"
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
