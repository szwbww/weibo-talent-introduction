package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.springframework.stereotype.Service

@Service
class MeetingInvitationMailComposer(
    private val mailSenderAccountService: MailSenderAccountService,
    private val mailComposeTemplateService: MailComposeTemplateService
) {
    fun compose(accountCode: String, expert: ExpertProfile): ComposedMail {
        val account = mailSenderAccountService.getEnabledAccount(accountCode)
        val rendered = mailComposeTemplateService.renderByCode(
            templateCode = "MEETING_INVITATION",
            variables = mapOf(
                "senderDisplayName" to account.senderDisplayName.orEmpty()
            ),
            variantSeed = MailComposeTemplateService.variantSeedFor(expert.orcidId, expert.email)
        )

        return ComposedMail(
            to = expert.email ?: error("Expert email is required for meeting invitation mail"),
            subject = rendered.subject,
            body = rendered.body,
            messageId = OutboundMessageIdFactory.newId("meeting-invitation", expert.orcidId, account.senderEmail)
        )
    }
}
