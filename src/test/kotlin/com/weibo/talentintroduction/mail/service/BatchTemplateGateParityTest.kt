package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ConversationStateService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.template.service.ComposeTemplateRenderResult
import com.weibo.talentintroduction.template.service.MailComposeTemplateDetail
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.Optional

/**
 * 05（I-4/I-5）：模板门禁开关真实生效，且两条 composer 使用同一判定。
 *
 * - 开关开启：用同一模板 ID/版本/seed/rawTexts/实际变量判定，缺个性化 key → 阻断/`PERSONALIZATION_INCOMPLETE`；
 * - 开关关闭：不再检查个性化缺项，但 `requireNoPlaceholderResidue` 仍生效（残留占位符是明确模板错误）；
 * - 人工单发默认仍开启门禁（参数默认值保持既有行为）。
 */
class BatchTemplateGateParityTest {
    private val requiredKey = "primaryResearchField"
    private val rawTexts = listOf("Dear expert, your research ${'$'}{$requiredKey} is impressive.")
    private val account = MailSenderAccount(
        accountCode = "sender", senderEmail = "sender@example.org", senderName = "Sender",
        senderTitle = null, senderDisplayName = null, teamName = null, countryName = null,
        smtpHost = "smtp.example.org", smtpPort = 465, smtpUsername = "sender@example.org",
        smtpPassword = "secret", imapHost = "imap.example.org", imapPort = 993,
        imapUsername = "sender@example.org", imapPassword = "secret"
    )
    private val expert = ExpertProfile(
        orcidId = "0001", email = "expert@example.org", givenNames = "G", familyNames = "F",
        country = "China", keyword = "kw", employment = "Employment"
    )

    private fun <T> anyValue(fallback: T): T = Mockito.any<T>() ?: fallback
    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun stubIntroductionTemplate(templates: MailComposeTemplateService) {
        Mockito.`when`(templates.render(eqValue(42L), anyValue(emptyMap<String, String>()), Mockito.anyInt()))
            .thenReturn(
                ComposeTemplateRenderResult(
                    subject = "Hello", body = "Dear expert, your research  is impressive.",
                    mailType = "INTRODUCTION", rawTexts = rawTexts, templateId = 42L
                )
            )
        Mockito.`when`(templates.effectiveRequiredKeys(42L)).thenReturn(listOf(requiredKey))
    }

    @Test
    fun `introduction evaluateForBatch reports the exact missing required key (I-4)`() {
        val accountService = Mockito.mock(MailSenderAccountService::class.java)
        val templates = Mockito.mock(MailComposeTemplateService::class.java)
        val variables = Mockito.mock(MailVariableService::class.java)
        val composer = IntroductionMailComposer(accountService, templates, variables)
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        stubIntroductionTemplate(templates)
        Mockito.`when`(
            variables.buildVariables(
                anyValue(account), anyValue(expert), anyValue<String?>(null),
                Mockito.anyBoolean(), anyValue<ExpertContact?>(null)
            )
        ).thenReturn(mapOf("senderName" to "Sender"))

        val evaluation = composer.evaluateForBatch("sender", expert, 42L)

        assertEquals(listOf(requiredKey), evaluation.missingKeys)
        assertEquals(42L, evaluation.templateId)
    }

    @Test
    fun `introduction compose blocks when gate on and proceeds when gate off (I-4 I-5)`() {
        val accountService = Mockito.mock(MailSenderAccountService::class.java)
        val templates = Mockito.mock(MailComposeTemplateService::class.java)
        val variables = Mockito.mock(MailVariableService::class.java)
        val composer = IntroductionMailComposer(accountService, templates, variables)
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        stubIntroductionTemplate(templates)
        Mockito.`when`(
            variables.buildVariables(
                anyValue(account), anyValue(expert), anyValue<String?>(null),
                Mockito.anyBoolean(), anyValue<ExpertContact?>(null)
            )
        ).thenReturn(mapOf("senderName" to "Sender"))

        assertThrows(PersonalizationGateException::class.java) {
            composer.compose("sender", expert, 42L, enforcePersonalizationGate = true)
        }
        // 开关关闭：裸变量缺值不再阻断（按 renderText 变空串）。
        val mail = composer.compose("sender", expert, 42L, enforcePersonalizationGate = false)
        assertFalse(mail.subject.isBlank())
    }

    @Test
    fun `manual compose template gate honors the explicit batch switch (I-4 I-5)`() {
        val contacts = Mockito.mock(ExpertContactRepository::class.java)
        val records = Mockito.mock(MailRecordRepository::class.java)
        val qa = Mockito.mock(MailRecordQaRuleRepository::class.java)
        val accountService = Mockito.mock(MailSenderAccountService::class.java)
        val accounts = Mockito.mock(MailSenderAccountRepository::class.java)
        val delivery = Mockito.mock(MailDeliveryService::class.java)
        val templates = Mockito.mock(MailComposeTemplateService::class.java)
        val binding = Mockito.mock(SenderAccountBindingService::class.java)
        val states = Mockito.mock(ConversationStateService::class.java)
        val operator = Mockito.mock(ExpertOperatorStatusService::class.java)
        val variables = Mockito.mock(MailVariableService::class.java)
        val service = ManualExpertMailService(
            contacts, records, qa, accountService, accounts, delivery, templates,
            MailContentService(), states, binding, operator, PersonalizationGateService(), variables
        )
        val contact = ExpertContact(
            id = 7L, campaignId = 1L, orcidId = "0001", expertEmail = "expert@example.org",
            expertName = "Expert", currentStatus = "NEW"
        )
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact))
        Mockito.`when`(accountService.selectAccountForManualSending()).thenReturn(account)
        Mockito.`when`(templates.getById(42L)).thenReturn(
            MailComposeTemplateDetail(
                id = 42, templateCode = "MATERIAL", templateName = "Material", subject = "Subject",
                description = null, mailType = "MATERIAL_REMINDER", subjectVariants = null, enabled = true,
                blocks = emptyList(), createdAt = null, updatedAt = null
            )
        )
        stubIntroductionTemplate(templates)
        Mockito.`when`(
            variables.buildVariables(
                eqValue(account), anyValue(expert), anyValue("expert@example.org"),
                eqValue(false), anyValue(contact)
            )
        ).thenReturn(mapOf("senderName" to "Sender"))
        Mockito.`when`(delivery.send(eqValue(account), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg", "SENT"))
        Mockito.`when`(records.save(Mockito.any(MailRecord::class.java))).thenAnswer {
            it.getArgument<MailRecord>(0).copy(id = 100L)
        }
        Mockito.`when`(states.transition(anyValue(contact), anyValue(ConversationStatus.NEW), Mockito.anyString(),
            Mockito.anyString(), anyValue(java.time.LocalDateTime.now()), anyValue { contact }))
            .thenReturn(contact)

        val command = ManualMailSendCommand("COMPOSE_TEMPLATE", "42", null)

        // 默认（人工单发）：门禁开启 → 缺个性化 key 阻断。
        assertThrows(PersonalizationGateException::class.java) {
            service.sendManualMail(7L, command)
        }
        // 批量显式关闭：不再检查个性化缺项，投递继续。
        val result = service.sendManualMail(7L, command, enforcePersonalizationGate = false)
        assertEquals("SENT", result.sendStatus)
        Mockito.verify(delivery, Mockito.times(1)).send(eqValue(account), anyValue(ComposedMail("", "", "")))
    }
}
