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
import java.util.UUID

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
    private val conversationStateService: ConversationStateService,
    private val personalizationGateService: PersonalizationGateService = PersonalizationGateService(),
    private val mailVariableService: MailVariableService? = null
) {
    fun listSendOptions(): List<ManualMailOption> {
        return mailComposeTemplateService.listEnabled()
            .map { template ->
                ManualMailOption(
                    optionType = ManualMailOptionType.COMPOSE_TEMPLATE.name,
                    optionValue = template.id?.toString() ?: "",
                    optionName = template.templateName,
                    subject = template.subject,
                    description = "邮件模板",
                    templateCode = template.templateCode,
                    mailType = template.mailType
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
        personalizationGateService.requireNoPlaceholderResidue(
            composed.mail.subject,
            composed.mail.text,
            composed.mail.body
        )
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
                inReplyTo = composed.mail.inReplyTo,
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

        val variableService = mailVariableService
        val variables = if (variableService != null) {
            val expert = variableService.resolveExpertProfileFor(contact)
            variableService.buildVariables(
                account, expert, contact.expertEmail, previewFallbacks = false, contact = contact
            )
        } else {
            // Test-only fallback for the legacy 9-arg constructor (no MailVariableService
            // wired). Production always injects the bean via Spring; this branch mirrors
            // the pre-gate behavior so pre-existing tests keep their observable contract.
            senderVariables(account) + MailVariableService.EXPERT_KEYS.associateWith { "" }
        }
        val rendered = mailComposeTemplateService.render(
            templateId,
            variables,
            MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)
        )
        require(rendered.body.isNotBlank()) {
            "邮件模板正文为空：所有内容块均不可用，请检查模板配置"
        }

        val requiredKeys = (mailComposeTemplateService.effectiveRequiredKeys(templateId) ?: emptyList())
        val rawTexts = rendered.rawTexts.ifEmpty { listOf(template.subject) }
        val gate = personalizationGateService.evaluate(rawTexts, variables, requiredKeys)
        if (gate.blocked) {
            throw PersonalizationGateException(gate.missingKeys)
        }

        val isMaterialReminder = (rendered.mailType ?: "COMPOSE_TEMPLATE") == "MATERIAL_REMINDER"
        val anchor = if (isMaterialReminder) {
            contact.id?.let { mailRecordRepository.findLatestInboundByExpertContactId(it) }
        } else null
        val anchorMessageId = anchor?.messageId
            ?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= 255 }        // I-1 长度守卫
        val threadSubject = if (anchorMessageId != null) {
            buildReplySubject(anchor?.subject, rendered.subject)     // I-2
        } else rendered.subject
        val references = if (anchorMessageId != null) {
            listOfNotNull(anchor?.inReplyTo?.trim()?.takeIf { it.isNotBlank() }, anchorMessageId)
                .joinToString(" ")
        } else null

        val senderDomain = account.senderEmail.substringAfter("@")
        // I-1: rendered.body 是已完成变量替换的纯文本单源。text/plain 逐字使用它，
        // text/html 必须由 plainTextToHtml() 做一次安全转换（段落/换行/转义），
        // 禁止把纯文本直接标记为 HTML（renderHtmlForContact 只替换变量，不产生标签）。
        val html = mailContentService.plainTextToHtml(rendered.body)
        return ManualComposedMail(
            mailType = rendered.mailType ?: "COMPOSE_TEMPLATE",
            mail = ComposedMail(
                to = contact.expertEmail,
                subject = threadSubject,                                                    // I-2
                body = html,
                html = true,
                text = rendered.body,
                messageId = "<reminder-${contact.id}-${UUID.randomUUID()}@$senderDomain>",   // I-3
                inReplyTo = anchorMessageId,                                                // I-1
                references = references                                                     // I-1
            ),
            matchedQaRuleId = rendered.qaRuleIds.firstOrNull(),
            qaRuleIds = rendered.qaRuleIds
        )
    }

    private fun buildReplySubject(anchorSubject: String?, fallback: String): String {
        val stripped = stripReplyPrefixes(anchorSubject?.trim().orEmpty())
        if (stripped.isBlank()) return fallback
        return ("Re: $stripped").take(255)
    }

    private fun stripReplyPrefixes(subject: String): String {
        var s = subject
        while (true) {
            val m = REPLY_PREFIX_REGEX.find(s) ?: break
            s = s.removeRange(m.range).trimStart()
        }
        return s.trim()
    }

    private fun nextStatus(currentStatus: String, mailType: String): ConversationStatus =
        when (mailType) {
            "INTRODUCTION" -> ConversationStatus.INTRO_SENT
            "MEETING_INVITATION" -> ConversationStatus.MEETING_SCHEDULING
            "MATERIAL_REMINDER" -> ConversationStatus.fromName(currentStatus)
            "COMPOSE_TEMPLATE" -> ConversationStatus.fromName(currentStatus)
            else -> ConversationStatus.fromName(currentStatus)
        }

    private fun senderVariables(account: MailSenderAccount): Map<String, String> =
        mapOf(
            "senderEmail" to account.senderEmail,
            "senderName" to account.senderName,
            "senderTitle" to account.senderTitle.orEmpty(),
            "teamName" to account.teamName.orEmpty(),
            "countryName" to account.countryName.orEmpty()
        )

    companion object {
        private val REPLY_PREFIX_REGEX =
            Regex("""^\s*(re|答复|回复)\s*(\[\d+\])?\s*[:：]\s*""", RegexOption.IGNORE_CASE)
    }
}

enum class ManualMailOptionType {
    COMPOSE_TEMPLATE
}

data class ManualMailOption(
    val optionType: String,
    val optionValue: String,
    val optionName: String,
    val subject: String?,
    val description: String,
    val templateCode: String? = null,
    val mailType: String? = null
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
