package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ConversationStateService
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailRecordQaRule
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ManualExpertMailService(
    private val expertContactRepository: ExpertContactRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val mailRecordQaRuleRepository: MailRecordQaRuleRepository,
    private val mailSenderAccountService: MailSenderAccountService,
    private val mailSenderAccountRepository: MailSenderAccountRepository,
    private val mailDeliveryService: MailDeliveryService,
    private val mailComposeTemplateService: MailComposeTemplateService,
    private val mailContentService: MailContentService,
    private val conversationStateService: ConversationStateService
) {
    fun listSendOptions(): List<ManualMailOption> {
        return mailComposeTemplateService.listEnabled()
            .map { template ->
                ManualMailOption(
                    optionType = ManualMailOptionType.COMPOSE_TEMPLATE.name,
                    optionValue = template.id?.toString() ?: "",
                    optionName = template.templateName,
                    subject = template.subject,
                    description = "邮件模板"
                )
            }
    }

    @Transactional
    fun sendManualMail(contactId: Long, command: ManualMailSendCommand): ManualMailSendResult {
        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }
        require(contact.expertEmail.isNotBlank()) { "Expert email is required" }

        val account = command.senderAccountCode
            ?.takeIf { it.isNotBlank() }
            ?.let(mailSenderAccountService::getManualSendAccount)
            ?: mailSenderAccountService.selectAccountForManualSending()
        val composed = compose(contact, account, command)
        val delivered = mailDeliveryService.send(account, composed.mail)
        val now = LocalDateTime.now()

        val saved = mailRecordRepository.save(
            MailRecord(
                expertContactId = contactId,
                direction = "OUTBOUND",
                mailType = composed.mailType,
                senderAccountCode = account.accountCode,
                triggeredBy = TriggeredBy.OPERATOR,
                sourceInboundId = command.sourceInboundId,
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

        if (composed.qaRuleIds.isNotEmpty()) {
            val mailRecordId = saved.id ?: error("Mail record id is required")
            composed.qaRuleIds.forEachIndexed { ordinal, qaRuleId ->
                mailRecordQaRuleRepository.save(
                    MailRecordQaRule(
                        mailRecordId = mailRecordId,
                        qaRuleId = qaRuleId,
                        ordinal = ordinal
                    )
                )
            }
        }

        mailSenderAccountRepository.save(account.copy(lastSentAt = now))

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

    fun sendBatchMail(contactIds: List<Long>, command: ManualMailSendCommand): BatchMailSendResult {
        var success = 0
        var failed = 0
        val errors = mutableListOf<String>()
        contactIds.forEach { contactId ->
            try {
                sendManualMail(contactId, command)
                success += 1
            } catch (ex: Exception) {
                failed += 1
                errors.add("contactId=$contactId: ${ex.message ?: ex.javaClass.simpleName}")
            }
        }
        return BatchMailSendResult(
            total = contactIds.size,
            success = success,
            failed = failed,
            errors = errors
        )
    }

    private fun compose(
        contact: ExpertContact,
        account: MailSenderAccount,
        command: ManualMailSendCommand
    ): ManualComposedMail {
        val optionType = try {
            ManualMailOptionType.valueOf(command.optionType.uppercase())
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("Unsupported manual mail option type: ${command.optionType}")
        }
        return when (optionType) {
            ManualMailOptionType.COMPOSE_TEMPLATE -> composeComposeTemplate(
                contact,
                account,
                command.optionValue.toLong()
            )
        }
    }

    private fun composeComposeTemplate(
        contact: ExpertContact,
        account: MailSenderAccount,
        templateId: Long
    ): ManualComposedMail {
        val template = mailComposeTemplateService.getById(templateId)
        require(template.enabled) { "Compose template is disabled: $templateId" }
        val rendered = mailComposeTemplateService.render(
            templateId,
            mailTemplateVariables(account)
        )
        require(rendered.body.isNotBlank()) {
            "邮件模板正文为空：所有内容块均不可用，请检查模板配置"
        }

        return ManualComposedMail(
            mailType = rendered.mailType ?: "COMPOSE_TEMPLATE",
            mail = ComposedMail(
                to = contact.expertEmail,
                subject = rendered.subject,
                body = mailContentService.plainTextToHtml(rendered.body),
                html = true,
                text = rendered.body
            ),
            matchedQaRuleId = rendered.qaRuleIds.firstOrNull(),
            qaRuleIds = rendered.qaRuleIds
        )
    }

    private fun nextStatus(currentStatus: String, mailType: String): ConversationStatus =
        when (mailType) {
            "INTRODUCTION" -> ConversationStatus.INTRO_SENT
            "MEETING_INVITATION" -> ConversationStatus.MEETING_SCHEDULING
            "MATERIAL_REMINDER" -> ConversationStatus.fromName(currentStatus)
            "COMPOSE_TEMPLATE" -> ConversationStatus.fromName(currentStatus)
            else -> ConversationStatus.fromName(currentStatus)
        }

    private fun mailTemplateVariables(account: MailSenderAccount): Map<String, String> =
        mapOf(
            "senderEmail" to account.senderEmail,
            "senderName" to account.senderName,
            "senderTitle" to account.senderTitle.orEmpty(),
            "teamName" to account.teamName.orEmpty(),
            "countryName" to account.countryName.orEmpty(),
            "senderDisplayName" to account.senderDisplayName.orEmpty()
        )
}

enum class ManualMailOptionType {
    COMPOSE_TEMPLATE
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
    val senderAccountCode: String?,
    val sourceInboundId: Long? = null
)

data class ManualMailSendResult(
    val contactId: Long,
    val senderAccountCode: String,
    val mailType: String,
    val subject: String,
    val sendStatus: String,
    val messageId: String?
)

data class BatchMailSendResult(
    val total: Int,
    val success: Int,
    val failed: Int,
    val errors: List<String> = emptyList()
)

private data class ManualComposedMail(
    val mailType: String,
    val mail: ComposedMail,
    val matchedQaRuleId: Long?,
    val qaRuleIds: List<Long> = emptyList()
)
