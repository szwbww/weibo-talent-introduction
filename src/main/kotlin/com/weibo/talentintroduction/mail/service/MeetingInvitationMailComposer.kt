package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.template.service.MailTemplateService
import org.springframework.stereotype.Service

@Service
class MeetingInvitationMailComposer(
    private val mailSenderAccountService: MailSenderAccountService,
    private val mailTemplateService: MailTemplateService
) {
    fun compose(accountCode: String, expert: ExpertProfile): ComposedMail {
        val account = mailSenderAccountService.getEnabledAccount(accountCode)
        val rendered = mailTemplateService.render(
            templateCode = "MEETING_INVITATION",
            variables = mapOf(
                "senderDisplayName" to account.senderDisplayName.orEmpty()
            )
        )

        return ComposedMail(
            to = expert.email ?: error("Expert email is required for meeting invitation mail"),
            subject = rendered.subject ?: "Follow-up on the Talent Program",
            body = rendered.body
        )
    }
}
