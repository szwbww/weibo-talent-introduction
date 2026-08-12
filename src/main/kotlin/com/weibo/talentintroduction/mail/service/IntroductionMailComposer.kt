package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class IntroductionMailComposer(
    private val mailSenderAccountService: MailSenderAccountService,
    private val mailComposeTemplateService: MailComposeTemplateService,
    private val mailVariableService: MailVariableService,
    private val personalizationGateService: PersonalizationGateService = PersonalizationGateService(),
    private val mailContentService: MailContentService = MailContentService()
) {
    fun compose(accountCode: String, expert: ExpertProfile, templateId: Long? = null): ComposedMail {
        val account = mailSenderAccountService.getEnabledAccount(accountCode)
        val variables = buildVariables(account, expert)
        val variantSeed = expert.orcidId.hashCode()
        val rendered = if (templateId != null) {
            mailComposeTemplateService.render(templateId, variables, variantSeed)
        } else {
            mailComposeTemplateService.renderByCode(templateCode = "INTRODUCTION", variables = variables, variantSeed = variantSeed)
        }

        val gateTemplateId = templateId ?: rendered.templateId
        val requiredKeys = gateTemplateId?.let { mailComposeTemplateService.effectiveRequiredKeys(it) }.orEmpty()
        val gate = personalizationGateService.evaluate(rendered.rawTexts, variables, requiredKeys)
        if (gate.blocked) {
            throw PersonalizationGateException(gate.missingKeys)
        }

        val domain = account.senderEmail.substringAfter("@")
        val messageId = "<intro-${expert.orcidId}-${UUID.randomUUID()}@$domain>"

        val plain = rendered.body
        val mail = ComposedMail(
            to = expert.email ?: error("Expert email is required for introduction mail"),
            subject = rendered.subject,
            body = mailContentService.plainTextToHtml(plain, listOfNotNull(variables["unsubscribeUrl"])),
            html = true,
            text = plain,
            messageId = messageId
        )
        personalizationGateService.requireNoPlaceholderResidue(mail.subject, plain)
        return mail
    }

    fun buildTemplateVariables(expert: ExpertProfile, accountCode: String?): List<TemplateVariableItem> {
        val account = accountCode?.let { mailSenderAccountService.getEnabledAccount(it) }
        return toTemplateVariableItems(buildVariables(account, expert))
    }

    fun buildVariables(account: MailSenderAccount?, expert: ExpertProfile): Map<String, String> =
        mailVariableService.buildVariables(account, expert)

    fun toTemplateVariableItems(variables: Map<String, String>): List<TemplateVariableItem> =
        mailVariableService.toTemplateVariableItems(variables)

    companion object {
        val VARIABLE_LABELS: Map<String, String> = MailVariableService.VARIABLE_LABELS
    }
}

data class TemplateVariableItem(
    val key: String,
    val label: String,
    val value: String,
    val filled: Boolean
)

data class ComposedMail(
    val to: String,
    val subject: String,
    val body: String,
    val html: Boolean = false,
    val text: String? = null,
    val messageId: String? = null,
    val inReplyTo: String? = null,
    val references: String? = null,
    /** 显式绕过抑制名单拦截。只允许人工单发路径按操作端请求置 true；批量与自动路径恒为 false。见 plan I-4。 */
    val allowSuppressedRecipient: Boolean = false
)
