package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ConversationStateService
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.template.domain.MailTemplate
import com.weibo.talentintroduction.template.repository.MailTemplateRepository
import com.weibo.talentintroduction.template.service.MailTemplateService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ManualExpertMailService(
    private val expertContactRepository: ExpertContactRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val mailSenderAccountService: MailSenderAccountService,
    private val mailSenderAccountRepository: MailSenderAccountRepository,
    private val mailDeliveryService: MailDeliveryService,
    private val mailTemplateRepository: MailTemplateRepository,
    private val mailTemplateService: MailTemplateService,
    private val qaRuleRepository: QaRuleRepository,
    private val conversationStateService: ConversationStateService
) {
    fun listSendOptions(): List<ManualMailOption> {
        val templateOptions = mailTemplateRepository.findAllByEnabledTrueOrderByTemplateCodeAsc()
            .filter { it.templateCode in fixedTemplateCodes }
            .map { template ->
                ManualMailOption(
                    optionType = ManualMailOptionType.TEMPLATE.name,
                    optionValue = template.templateCode,
                    optionName = template.templateName.toChineseTemplateName(template.templateCode),
                    subject = template.subject,
                    description = "固定邮件模板"
                )
            }

        val qaOptions = qaRuleRepository.findAllEnabledOrdered()
            .map { rule ->
                ManualMailOption(
                    optionType = ManualMailOptionType.QA.name,
                    optionValue = rule.id?.toString() ?: "",
                    optionName = rule.replySubject ?: "QA Rule #${rule.id}",
                    subject = rule.replySubject,
                    description = "QA 问答邮件"
                )
            }

        return templateOptions + qaOptions
    }

    @Transactional
    fun sendManualMail(contactId: Long, command: ManualMailSendCommand): ManualMailSendResult {
        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }
        require(contact.expertEmail.isNotBlank()) { "Expert email is required" }

        val account = command.senderAccountCode
            ?.takeIf { it.isNotBlank() }
            ?.let(mailSenderAccountService::getEnabledAccount)
            ?: mailSenderAccountService.selectAccountForSending()
        val composed = compose(contact, account.accountCode, command)
        val delivered = mailDeliveryService.send(account, composed.mail)
        val now = LocalDateTime.now()

        mailRecordRepository.save(
            MailRecord(
                expertContactId = contactId,
                direction = "OUTBOUND",
                mailType = composed.mailType,
                messageId = delivered.messageId,
                inReplyTo = null,
                subject = composed.mail.subject,
                body = composed.mail.body,
                matchedQaRuleId = composed.matchedQaRuleId,
                sendStatus = delivered.status,
                receivedAt = null,
                sentAt = now,
                createdAt = now
            )
        )

        mailSenderAccountRepository.save(
            account.copy(
                todaySentCount = account.todaySentCount + 1,
                lastSentAt = now
            )
        )

        conversationStateService.transition(
            contact = contact,
            toStatus = nextStatus(contact.currentStatus, composed.mailType),
            reason = "MANUAL_MAIL_${composed.mailType}",
            source = "MANUAL_MAIL",
            now = now
        ) {
            it.copy(
                lastMailAt = now,
            )
        }

        return ManualMailSendResult(
            contactId = contactId,
            senderAccountCode = account.accountCode,
            mailType = composed.mailType,
            subject = composed.mail.subject,
            sendStatus = delivered.status,
            messageId = delivered.messageId
        )
    }

    private fun compose(
        contact: ExpertContact,
        accountCode: String,
        command: ManualMailSendCommand
    ): ManualComposedMail {
        val optionType = ManualMailOptionType.valueOf(command.optionType.uppercase())
        return when (optionType) {
            ManualMailOptionType.TEMPLATE -> composeTemplate(contact, accountCode, command.optionValue)
            ManualMailOptionType.QA -> composeQa(contact, command.optionValue.toLong())
        }
    }

    private fun composeTemplate(
        contact: ExpertContact,
        accountCode: String,
        templateCode: String
    ): ManualComposedMail {
        require(templateCode in fixedTemplateCodes) { "Unsupported fixed mail template: $templateCode" }
        val account = mailSenderAccountService.getEnabledAccount(accountCode)
        val rendered = mailTemplateService.render(
            templateCode = templateCode,
            variables = mapOf(
                "senderEmail" to account.senderEmail,
                "senderName" to account.senderName,
                "senderTitle" to account.senderTitle.orEmpty(),
                "teamName" to account.teamName.orEmpty(),
                "countryName" to account.countryName.orEmpty(),
                "senderDisplayName" to account.senderDisplayName.orEmpty()
            )
        )

        return ManualComposedMail(
            mailType = templateCode,
            mail = ComposedMail(
                to = contact.expertEmail,
                subject = rendered.subject ?: templateCode.toChineseTemplateName(templateCode),
                body = rendered.body
            ),
            matchedQaRuleId = null
        )
    }

    private fun composeQa(contact: ExpertContact, qaRuleId: Long): ManualComposedMail {
        val rule = qaRuleRepository.findById(qaRuleId)
            .orElseThrow { error("QA rule not found: $qaRuleId") }
        require(rule.enabled) { "QA rule is disabled: $qaRuleId" }

        return ManualComposedMail(
            mailType = "MANUAL_QA_REPLY",
            mail = ComposedMail(
                to = contact.expertEmail,
                subject = rule.replySubject ?: "Re: Talent Program",
                body = rule.replyBody
            ),
            matchedQaRuleId = rule.id
        )
    }

    private fun nextStatus(currentStatus: String, mailType: String): ConversationStatus =
        when (mailType) {
            "INTRODUCTION" -> ConversationStatus.INTRO_SENT
            "MEETING_INVITATION" -> ConversationStatus.MEETING_SCHEDULING
            "MANUAL_QA_REPLY" -> ConversationStatus.QA_AUTO_REPLIED
            else -> ConversationStatus.fromName(currentStatus)
        }

    private fun String.toChineseTemplateName(templateCode: String): String =
        when (templateCode) {
            "INTRODUCTION" -> "项目介绍邮件"
            "MEETING_INVITATION" -> "会议邀约邮件"
            else -> this
        }

    companion object {
        private val fixedTemplateCodes = setOf("INTRODUCTION", "MEETING_INVITATION")
    }
}

enum class ManualMailOptionType {
    TEMPLATE,
    QA
}

data class ManualMailOption(
    val optionType: String,
    val optionValue: String,
    val optionName: String,
    val subject: String?,
    val description: String
)

data class ManualMailSendCommand(
    val optionType: String,
    val optionValue: String,
    val senderAccountCode: String?
)

data class ManualMailSendResult(
    val contactId: Long,
    val senderAccountCode: String,
    val mailType: String,
    val subject: String,
    val sendStatus: String,
    val messageId: String?
)

private data class ManualComposedMail(
    val mailType: String,
    val mail: ComposedMail,
    val matchedQaRuleId: Long?
)
