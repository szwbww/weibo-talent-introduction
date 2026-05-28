package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.template.service.MailTemplateService
import org.springframework.stereotype.Service

@Service
class IntroductionMailComposer(
    private val mailSenderAccountService: MailSenderAccountService,
    private val mailTemplateService: MailTemplateService
) {
    fun compose(accountCode: String, expert: ExpertProfile): ComposedMail {
        val account = mailSenderAccountService.getEnabledAccount(accountCode)
        val rendered = mailTemplateService.render(
            templateCode = "INTRODUCTION",
            variables = mapOf(
                "senderEmail" to account.senderEmail,
                "senderName" to account.senderName,
                "senderTitle" to account.senderTitle.orEmpty(),
                "teamName" to account.teamName.orEmpty(),
                "countryName" to account.countryName.orEmpty()
            )
        )

        return ComposedMail(
            to = expert.email ?: error("Expert email is required for introduction mail"),
            subject = rendered.subject ?: "Research Collaboration Opportunity",
            body = rendered.body
        )
    }
}

data class ComposedMail(
    val to: String,
    val subject: String,
    val body: String
)
