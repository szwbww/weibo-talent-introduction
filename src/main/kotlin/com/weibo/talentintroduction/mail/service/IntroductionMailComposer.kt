package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class IntroductionMailComposer(
    private val mailSenderAccountService: MailSenderAccountService,
    private val mailComposeTemplateService: MailComposeTemplateService
) {
    fun compose(accountCode: String, expert: ExpertProfile, templateId: Long? = null): ComposedMail {
        val account = mailSenderAccountService.getEnabledAccount(accountCode)
        val variables = mapOf(
            "senderEmail" to account.senderEmail,
            "senderName" to account.senderName,
            "senderTitle" to account.senderTitle.orEmpty(),
            "teamName" to account.teamName.orEmpty(),
            "countryName" to account.countryName.orEmpty(),
            "expertName" to expert.displayName,
            "expertFamilyName" to expert.familyNames.orEmpty(),
            "researchFields" to expert.researchFields.orEmpty(),
            "institution" to expert.institution.orEmpty(),
            "keyword" to expert.keyword.orEmpty(),
            "expertCountry" to expert.country.orEmpty()
        )
        val variantSeed = expert.orcidId.hashCode()
        val rendered = if (templateId != null) {
            mailComposeTemplateService.render(templateId, variables, variantSeed)
        } else {
            mailComposeTemplateService.renderByCode(templateCode = "INTRODUCTION", variables = variables, variantSeed = variantSeed)
        }

        val domain = account.senderEmail.substringAfter("@")
        val messageId = "<intro-${expert.orcidId}-${UUID.randomUUID()}@$domain>"

        return ComposedMail(
            to = expert.email ?: error("Expert email is required for introduction mail"),
            subject = rendered.subject,
            body = rendered.body,
            messageId = messageId
        )
    }
}

data class ComposedMail(
    val to: String,
    val subject: String,
    val body: String,
    val html: Boolean = false,
    val text: String? = null,
    val messageId: String? = null
)
