package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot
import com.weibo.talentintroduction.campaign.domain.Campaign
import com.weibo.talentintroduction.campaign.repository.CampaignRepository
import com.weibo.talentintroduction.campaign.service.BatchEmailVerificationService
import com.weibo.talentintroduction.campaign.service.BatchSendSettingService
import com.weibo.talentintroduction.campaign.service.ExecutionMode
import com.weibo.talentintroduction.campaign.service.ManualInitialOutreachService
import com.weibo.talentintroduction.config.ManualOutreachProperties
import com.weibo.talentintroduction.config.WarmupProperties
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgressStore
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.campaign.service.InitialOutreachService
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.expert.domain.ExpertClassification
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.ExpertType
import com.weibo.talentintroduction.expert.service.ExpertSearchResult
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.campaign.domain.MeetingSchedule
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.MeetingScheduleRepository
import com.weibo.talentintroduction.campaign.service.ConfirmMeetingCommand
import com.weibo.talentintroduction.campaign.service.MeetingScheduleService
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.template.service.ComposeTemplateRenderResult
import com.weibo.talentintroduction.template.service.MailComposeTemplateDetail
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.MailSendAttempt
import com.weibo.talentintroduction.campaign.repository.MailSendAttemptRepository
import com.weibo.talentintroduction.campaign.service.ConversationStateService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.campaign.service.ManualOutreachTxHelper
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.Optional

class MailOpenTrackingPersistenceTest {
    private val records = Mockito.mock(MailRecordRepository::class.java)
    private val accounts = Mockito.mock(MailSenderAccountRepository::class.java)
    private val attempts = Mockito.mock(MailSendAttemptRepository::class.java)
    private val states = Mockito.mock(ConversationStateService::class.java)
    private val operator = Mockito.mock(ExpertOperatorStatusService::class.java)
    private val helper = ManualOutreachTxHelper(states, records, accounts, attempts, operator)
    private val contact = ExpertContact(id = 7L, campaignId = 1L, orcidId = "orcid", expertEmail = "expert@example.org",
        expertName = "Expert", currentStatus = "NEW")

    private val account = MailSenderAccount(
        accountCode = "sender", senderEmail = "sender@example.org", senderName = "Sender",
        senderTitle = null, senderDisplayName = null, teamName = null, countryName = null,
        smtpHost = "smtp.example.org", smtpPort = 465, smtpUsername = "sender@example.org",
        smtpPassword = "secret", imapHost = "imap.example.org", imapPort = 993,
        imapUsername = "sender@example.org", imapPassword = "secret"
    )

    private fun <T> anyValue(fallback: T): T = Mockito.any<T>() ?: fallback
    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    @Test
    fun `manual expert batch saves independent successful ids and null on failed or reply`() {
        val contacts = Mockito.mock(ExpertContactRepository::class.java)
        val qa = Mockito.mock(MailRecordQaRuleRepository::class.java)
        val accountService = Mockito.mock(MailSenderAccountService::class.java)
        val delivery = Mockito.mock(MailDeliveryService::class.java)
        val templates = Mockito.mock(MailComposeTemplateService::class.java)
        val binding = Mockito.mock(SenderAccountBindingService::class.java)
        val service = ManualExpertMailService(contacts, records, qa, accountService, accounts, delivery,
            templates, MailContentService(), states, binding, operator)
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact))
        Mockito.`when`(contacts.findById(8L)).thenReturn(Optional.of(contact.copy(id = 8L)))
        Mockito.`when`(accountService.selectAccountForManualSending()).thenReturn(account)
        Mockito.`when`(templates.getById(10L)).thenReturn(MailComposeTemplateDetail(
            id = 10, templateCode = "INTRODUCTION", templateName = "Intro", subject = "Subject",
            description = null, mailType = "INTRODUCTION", subjectVariants = null, enabled = true,
            blocks = emptyList(), createdAt = null, updatedAt = null
        ))
        Mockito.`when`(templates.render(eqValue(10L), anyValue(emptyMap<String, String>()), Mockito.anyInt()))
            .thenReturn(ComposeTemplateRenderResult(subject = "Subject", body = "Original body",
                mailType = "INTRODUCTION"))
        Mockito.`when`(delivery.send(eqValue(account), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg", "SENT", openTrackingId = 11L))
            .thenReturn(DeliveredMail("msg", "SENT", openTrackingId = 12L))
            .thenReturn(DeliveredMail("msg", "FAILED", openTrackingId = 999L))
            .thenReturn(DeliveredMail("msg", "SENT"))
        val saved = mutableListOf<MailRecord>()
        Mockito.`when`(records.save(Mockito.any(MailRecord::class.java))).thenAnswer {
            it.getArgument<MailRecord>(0).also(saved::add).copy(id = 100L)
        }
        Mockito.`when`(states.transition(anyValue(contact), anyValue(ConversationStatus.INTRO_SENT), Mockito.anyString(),
            Mockito.anyString(), anyValue(java.time.LocalDateTime.now()), anyValue { contact }))
            .thenReturn(contact)
        val command = ManualMailSendCommand("COMPOSE_TEMPLATE", "10", null)
        assertEquals(2, service.sendBatchMail(listOf(7L, 8L), command).success)
        service.sendManualMail(7L, command)
        service.sendManualMail(7L, command.copy(sourceInboundId = 55L))
        assertEquals(listOf(11L, 12L, null, null), saved.map { it.openTrackingId })
        assertTrue(saved.all { it.body == "<p>Original body</p>" && it.subject == "Subject" })
        val outgoing = org.mockito.ArgumentCaptor.forClass(ComposedMail::class.java)
        Mockito.verify(delivery, Mockito.times(4)).send(eqValue(account),
            outgoing.capture() ?: ComposedMail("", "", ""))
        assertTrue(outgoing.allValues.last().isReply)
    }

    @Test
    fun `initial outreach entry persists delivered tracking id through real helper`() {
        val search = Mockito.mock(ExpertSearchService::class.java)
        val assignment = Mockito.mock(SenderAccountAssignmentService::class.java)
        val composer = Mockito.mock(IntroductionMailComposer::class.java)
        val delivery = Mockito.mock(MailDeliveryService::class.java)
        val contacts = Mockito.mock(ExpertContactRepository::class.java)
        val suppression = Mockito.mock(EmailSuppressionService::class.java)
        val autoSettings = Mockito.mock(AutoReplySettingService::class.java)
        val binding = Mockito.mock(SenderAccountBindingService::class.java)
        val service = InitialOutreachService(search, assignment, composer, delivery, contacts, helper,
            suppression, autoSettings, MailSchedulingProperties(initialOutreachSendIntervalMs = 0,
                initialOutreachSendJitterMs = 0, initialOutreachExpertTypes = listOf("PRODUCTION_RND")), binding)
        val expert = ExpertProfile(orcidId = "orcid", email = contact.expertEmail, givenNames = "Expert",
            familyNames = "Example", country = "UK", keyword = "science", employment = "University",
            expertClassification = ExpertClassification(type = ExpertType.PRODUCTION_RND, productionScore = 80,
                researchScore = 20, positiveEvidence = listOf("EVIDENCE"), negativeEvidence = emptyList(),
                version = "rnd-v2-2026", sourceFingerprint = "fp", classifiedAt = java.time.LocalDateTime.now()))
        Mockito.`when`(search.searchExpertsByTypesWithEmail(1, ExpertIndexLevel.CANDIDATE,
            listOf("PRODUCTION_RND"))).thenReturn(ExpertSearchResult(experts = listOf(expert), totalHits = 1))
        Mockito.`when`(assignment.selectAccount(anyValue(expert), anyValue(mutableListOf()),
            eqValue(false), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(composer.compose(eqValue("sender"), anyValue(expert), Mockito.isNull()))
            .thenReturn(ComposedMail(contact.expertEmail, "Introduction", "Original body"))
        Mockito.`when`(delivery.send(eqValue(account), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg", "SENT", openTrackingId = 55L))
        Mockito.`when`(contacts.save(Mockito.any(ExpertContact::class.java))).thenAnswer {
            it.getArgument<ExpertContact>(0).copy(id = 7L)
        }
        Mockito.`when`(binding.bindingFieldsFor(Mockito.anyString(), anyValue(java.time.LocalDateTime.now())))
            .thenReturn("sender" to java.time.LocalDateTime.now())
        Mockito.`when`(states.transition(anyValue(contact), anyValue(ConversationStatus.INTRO_SENT), Mockito.anyString(),
            Mockito.anyString(), anyValue(java.time.LocalDateTime.now()), anyValue { contact }))
            .thenReturn(contact)
        Mockito.`when`(attempts.findById(0L)).thenReturn(Optional.empty<MailSendAttempt>())
        val saved = mutableListOf<MailRecord>()
        Mockito.`when`(records.save(Mockito.any(MailRecord::class.java))).thenAnswer {
            it.getArgument<MailRecord>(0).also(saved::add)
        }
        assertEquals(1, service.sendInitialBatch(1L, 1).sent)
        assertEquals(55L, saved.single().openTrackingId)
        assertEquals("Original body", saved.single().body)
    }

    @Test
    fun `manual outreach batch entry persists tracking id through real success helper`() {
        val search = Mockito.mock(ExpertSearchService::class.java)
        val assignment = Mockito.mock(SenderAccountAssignmentService::class.java)
        val composer = Mockito.mock(IntroductionMailComposer::class.java)
        val delivery = Mockito.mock(MailDeliveryService::class.java)
        val contacts = Mockito.mock(ExpertContactRepository::class.java)
        val campaigns = Mockito.mock(CampaignRepository::class.java)
        val progress = Mockito.mock(TaskProgressStore::class.java)
        val config = Mockito.mock(BatchSendSettingService::class.java)
        val accountService = Mockito.mock(MailSenderAccountService::class.java)
        val selfCheck = Mockito.mock(SenderAccountSelfCheckService::class.java)
        val index = Mockito.mock(ExpertIndexWriterService::class.java)
        val suppression = Mockito.mock(EmailSuppressionService::class.java)
        val autoSettings = Mockito.mock(AutoReplySettingService::class.java)
        val binding = Mockito.mock(SenderAccountBindingService::class.java)
        val templates = Mockito.mock(MailComposeTemplateService::class.java)
        val verification = Mockito.mock(BatchEmailVerificationService::class.java)
        val service = ManualInitialOutreachService(search, assignment, composer, delivery, contacts, campaigns,
            records, accounts, attempts, progress, ManualOutreachProperties(sendIntervalMs = 0), helper,
            config, accountService, selfCheck, index, AccountRateLimiter(), suppression, ProviderResolver(),
            SenderWarmupService(WarmupProperties(enabled = false),
                ObjectMapper().registerKotlinModule()), autoSettings,
            Mockito.mock(ManualExpertMailService::class.java), Mockito.mock(TaskExecutionService::class.java),
            binding, templates, verification)
        Mockito.`when`(campaigns.findByCampaignCode("MANUAL_OUTREACH"))
            .thenReturn(Campaign(id = 1L, campaignCode = "MANUAL_OUTREACH", campaignName = "Outreach",
                description = null, senderAccountId = 1L))
        val expert = ExpertProfile(orcidId = "orcid", email = contact.expertEmail, givenNames = "Expert",
            familyNames = "Example", country = "UK", keyword = "science", employment = "University",
            expertClassification = ExpertClassification(type = ExpertType.PRODUCTION_RND, productionScore = 80,
                researchScore = 20, positiveEvidence = listOf("EVIDENCE"), negativeEvidence = emptyList(),
                version = "rnd-v2-2026", sourceFingerprint = "fp", classifiedAt = java.time.LocalDateTime.now()))
        Mockito.`when`(search.countExperts(eqValue(ExpertIndexLevel.CANDIDATE),
            anyValue(emptyList()))).thenReturn(1L)
        Mockito.`when`(search.searchExpertsFiltered(eqValue(ExpertIndexLevel.CANDIDATE),
            anyValue(emptyList()), Mockito.anyInt(), Mockito.anyInt())).thenAnswer {
            listOf(expert).drop(it.getArgument<Int>(2)).take(it.getArgument(3))
        }
        Mockito.`when`(accountService.listSendableAccounts(Mockito.anyBoolean())).thenReturn(listOf(account))
        Mockito.`when`(accountService.listAccounts()).thenReturn(listOf(account))
        Mockito.`when`(accountService.listEnabledAccounts()).thenReturn(listOf(account))
        Mockito.`when`(selfCheck.checkSendable(anyValue(account), Mockito.anyInt()))
            .thenReturn(SelfCheckResult("sender", passed = true, message = null, fromCache = true))
        Mockito.`when`(assignment.selectAccount(anyValue(expert), anyValue(mutableListOf()),
            Mockito.anyBoolean(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(contacts.save(Mockito.any(ExpertContact::class.java))).thenAnswer {
            it.getArgument<ExpertContact>(0).copy(id = 7L)
        }
        Mockito.`when`(binding.bindingFieldsFor(Mockito.anyString(), anyValue(java.time.LocalDateTime.now())))
            .thenReturn("sender" to java.time.LocalDateTime.now())
        Mockito.`when`(composer.compose(eqValue("sender"), anyValue(expert), Mockito.isNull()))
            .thenReturn(ComposedMail(contact.expertEmail, "Introduction", "Original body"))
        Mockito.`when`(attempts.save(Mockito.any(MailSendAttempt::class.java))).thenAnswer {
            it.getArgument<MailSendAttempt>(0).copy(id = 3L)
        }
        Mockito.`when`(attempts.findById(3L)).thenReturn(Optional.empty<MailSendAttempt>())
        Mockito.`when`(delivery.send(eqValue(account), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg", "SENT", openTrackingId = 77L))
        Mockito.`when`(states.transition(anyValue(contact), anyValue(ConversationStatus.INTRO_SENT), Mockito.anyString(),
            Mockito.anyString(), anyValue(java.time.LocalDateTime.now()), anyValue { contact }))
            .thenReturn(contact)
        val saved = mutableListOf<MailRecord>()
        Mockito.`when`(records.save(Mockito.any(MailRecord::class.java))).thenAnswer {
            it.getArgument<MailRecord>(0).also(saved::add)
        }
        val snapshot = BatchExecutionSnapshot(mailType = "INTRODUCTION", roundSize = 1, roundsPerRun = 1,
            perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30, funnelLevel = "CANDIDATE",
            expertTypes = listOf("PRODUCTION_RND"))
        assertEquals(1, service.run(snapshot, 91L, ExecutionMode.MANUAL, oneRoundOnly = false).sent)
        assertEquals(77L, saved.single().openTrackingId)
        assertEquals(91L, saved.single().taskExecutionId)
        assertEquals("Original body", saved.single().body)
    }

    @Test
    fun `meeting confirmation persists id only for successful new message`() {
        val schedules = Mockito.mock(MeetingScheduleRepository::class.java)
        val contacts = Mockito.mock(ExpertContactRepository::class.java)
        val accountService = Mockito.mock(MailSenderAccountService::class.java)
        val binding = Mockito.mock(SenderAccountBindingService::class.java)
        val delivery = Mockito.mock(MailDeliveryService::class.java)
        val templates = Mockito.mock(MailComposeTemplateService::class.java)
        val service = MeetingScheduleService(schedules, contacts, records, accounts, accountService,
            binding, delivery, templates, states)
        val schedule = MeetingSchedule(id = 8L, expertContactId = 7L, meetingStatus = "PENDING")
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact))
        Mockito.`when`(schedules.findById(8L)).thenReturn(Optional.of(schedule))
        Mockito.`when`(schedules.findById(9L))
            .thenReturn(Optional.of(schedule.copy(id = 9L, sourceMailRecordId = 55L)))
        Mockito.`when`(schedules.save(Mockito.any(MeetingSchedule::class.java)))
            .thenAnswer { it.getArgument<MeetingSchedule>(0) }
        Mockito.`when`(binding.resolveForSend(eqValue(contact), eqValue(true), eqValue(false))).thenReturn(account)
        Mockito.`when`(templates.renderByCode(eqValue("MEETING_CONFIRMATION"),
            anyValue(emptyMap<String, String>()), Mockito.anyInt()))
            .thenReturn(ComposeTemplateRenderResult(subject = "Meeting", body = "Meeting body",
                mailType = "MEETING_CONFIRMATION"))
        Mockito.`when`(delivery.send(eqValue(account), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg", "SENT", openTrackingId = 30L))
            .thenReturn(DeliveredMail("msg", "FAILED", openTrackingId = 999L))
            .thenReturn(DeliveredMail("msg", "SENT"))
        val saved = mutableListOf<MailRecord>()
        Mockito.`when`(records.save(Mockito.any(MailRecord::class.java))).thenAnswer {
            it.getArgument<MailRecord>(0).also(saved::add)
        }
        val command = ConfirmMeetingCommand("Tomorrow", "Teams", "https://example.org/meeting", null)
        service.confirmMeetingAndEmail(7L, 8L, command)
        service.confirmMeetingAndEmail(7L, 8L, command)
        service.confirmMeetingAndEmail(7L, 9L, command)
        assertEquals(listOf(30L, null, null), saved.map { it.openTrackingId })
        assertEquals(listOf(null, null, 55L), saved.map { it.sourceInboundId })
        assertTrue(saved.all { it.body == "Meeting body" })
    }
    @Test
    fun `successful outreach persists distinct tracking ids without changing audit body or task identity`() {
        val saved = mutableListOf<MailRecord>()
        Mockito.`when`(states.transition(anyValue(contact), anyValue(ConversationStatus.INTRO_SENT), Mockito.anyString(),
            Mockito.anyString(), anyValue(java.time.LocalDateTime.now()), anyValue { contact }))
            .thenAnswer { it.getArgument<ExpertContact>(0) }
        Mockito.`when`(records.save(Mockito.any(MailRecord::class.java))).thenAnswer {
            it.getArgument<MailRecord>(0).also(saved::add)
        }
        Mockito.`when`(attempts.findById(3L)).thenReturn(Optional.empty<MailSendAttempt>())
        helper.recordSuccess(contact, "sender", "same-message-id", "Subject", "Original body", 3L, 91L, 11L)
        helper.recordSuccess(contact, "sender", "same-message-id", "Subject", "Original body", 3L, 92L, 12L)
        assertEquals(listOf(11L, 12L), saved.map { it.openTrackingId })
        assertEquals(listOf(91L, 92L), saved.map { it.taskExecutionId })
        assertTrue(saved.all { it.body == "Original body" && it.sendStatus == "SENT" })
    }

    @Test
    fun `failed outreach never associates reserved tracking id`() {
        val saved = mutableListOf<MailRecord>()
        Mockito.`when`(records.save(Mockito.any(MailRecord::class.java))).thenAnswer {
            it.getArgument<MailRecord>(0).also(saved::add)
        }
        helper.recordFailure(7L, "sender", "msg", "failure", "Subject", "Original body", null)
        assertEquals("FAILED", saved.single().sendStatus)
        assertNull(saved.single().openTrackingId)
    }
}
