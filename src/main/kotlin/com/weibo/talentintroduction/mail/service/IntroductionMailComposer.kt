package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
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
        val variables = buildVariables(account, expert)
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

    fun buildTemplateVariables(expert: ExpertProfile, accountCode: String?): List<TemplateVariableItem> {
        val account = accountCode?.let { mailSenderAccountService.getEnabledAccount(it) }
        return toTemplateVariableItems(buildVariables(account, expert))
    }

    fun buildVariables(account: MailSenderAccount?, expert: ExpertProfile): Map<String, String> =
        mapOf(
            "senderEmail" to (account?.senderEmail).orEmpty(),
            "senderName" to (account?.senderName).orEmpty(),
            "senderTitle" to account?.senderTitle.orEmpty(),
            "teamName" to account?.teamName.orEmpty(),
            "countryName" to account?.countryName.orEmpty(),
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

    fun toTemplateVariableItems(variables: Map<String, String>): List<TemplateVariableItem> =
        variables.map { (key, value) ->
            TemplateVariableItem(
                key = key,
                label = VARIABLE_LABELS[key] ?: key,
                value = value,
                filled = value.isNotBlank()
            )
        }

    companion object {
        val VARIABLE_LABELS: Map<String, String> = mapOf(
            "senderEmail" to "发件邮箱",
            "senderName" to "发件人姓名",
            "senderTitle" to "发件人职位",
            "teamName" to "团队名称",
            "countryName" to "发件人国家",
            "expertName" to "专家姓名",
            "expertFamilyName" to "专家姓氏",
            "researchFields" to "研究方向",
            "institution" to "所属机构",
            "keyword" to "关键词",
            "expertCountry" to "专家国家",
            "employment" to "职位",
            "hIndex" to "H-Index",
            "worksCount" to "论文数",
            "lastPublicationYear" to "最近发表年份",
            "degree" to "学历",
            "recentWorkTitle" to "近期论文标题",
            "patentTitle" to "专利标题"
        )
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
    val messageId: String? = null
)
