package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.Campaign
import com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot
import com.weibo.talentintroduction.campaign.domain.BatchOutcomeReasonCodes
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfig
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.MailSendAttempt
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
import com.weibo.talentintroduction.campaign.domain.RecipientScope
import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
import com.weibo.talentintroduction.campaign.repository.CampaignRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.MailSendAttemptRepository
import com.weibo.talentintroduction.config.ManualOutreachProperties
import com.weibo.talentintroduction.config.WarmupProperties
import com.weibo.talentintroduction.config.WarmupStep
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.IntroductionMailComposer
import com.weibo.talentintroduction.mail.service.MailDeliveryService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.AccountRateLimiter
import com.weibo.talentintroduction.mail.service.AutoReplySettingService
import com.weibo.talentintroduction.mail.service.ProviderResolver
import com.weibo.talentintroduction.mail.service.ComposedMail
import com.weibo.talentintroduction.mail.service.DeliveredMail
import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory
import com.weibo.talentintroduction.mail.service.SelfCheckResult
import com.weibo.talentintroduction.mail.service.SenderAccountAssignmentService
import com.weibo.talentintroduction.mail.service.SenderAccountBindingService
import com.weibo.talentintroduction.mail.service.SenderBindingStock
import com.weibo.talentintroduction.mail.service.SenderAccountNotBoundException
import com.weibo.talentintroduction.mail.service.SenderAccountSelfCheckService
import com.weibo.talentintroduction.mail.service.SenderWarmupService
import com.weibo.talentintroduction.task.service.TaskProgressStore
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.time.LocalDateTime

class ManualInitialOutreachServiceTest {
    private val expertSearchService = Mockito.mock(ExpertSearchService::class.java)
    private val senderAccountAssignmentService = Mockito.mock(SenderAccountAssignmentService::class.java)
    private val senderAccountBindingService = Mockito.mock(SenderAccountBindingService::class.java)
    private val introductionMailComposer = Mockito.mock(IntroductionMailComposer::class.java)
    private val mailDeliveryService = Mockito.mock(MailDeliveryService::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val campaignRepository = Mockito.mock(CampaignRepository::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val mailSenderAccountRepository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val mailSendAttemptRepository = Mockito.mock(MailSendAttemptRepository::class.java)
    private val progressStore = Mockito.mock(TaskProgressStore::class.java)
    private val properties = ManualOutreachProperties(sendIntervalMs = 0L)
    private val txHelper = Mockito.mock(ManualOutreachTxHelper::class.java)
    private val batchSendSettingService = Mockito.mock(BatchSendSettingService::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val selfCheckService = Mockito.mock(SenderAccountSelfCheckService::class.java)
    private val expertIndexWriterService = Mockito.mock(ExpertIndexWriterService::class.java)
    private val accountRateLimiter = AccountRateLimiter()
    private val emailSuppressionService = Mockito.mock(EmailSuppressionService::class.java)
    private val autoReplySettingService = Mockito.mock(AutoReplySettingService::class.java)
    private val manualExpertMailService = Mockito.mock(com.weibo.talentintroduction.mail.service.ManualExpertMailService::class.java)
    private val taskExecutionService = Mockito.mock(com.weibo.talentintroduction.task.service.TaskExecutionService::class.java)
    private val mailComposeTemplateService = Mockito.mock(MailComposeTemplateService::class.java)
    private val providerResolver = ProviderResolver()
    private val senderWarmupService = SenderWarmupService(
        WarmupProperties(
            enabled = true,
            steps = listOf(WarmupStep(1, 20), WarmupStep(3, 40))
        ),
        ObjectMapper().registerKotlinModule()
    )

    private val service = ManualInitialOutreachService(
        expertSearchService = expertSearchService,
        senderAccountAssignmentService = senderAccountAssignmentService,
        introductionMailComposer = introductionMailComposer,
        mailDeliveryService = mailDeliveryService,
        expertContactRepository = expertContactRepository,
        campaignRepository = campaignRepository,
        mailRecordRepository = mailRecordRepository,
        mailSenderAccountRepository = mailSenderAccountRepository,
        mailSendAttemptRepository = mailSendAttemptRepository,
        progressStore = progressStore,
        properties = properties,
        txHelper = txHelper,
        batchSendSettingService = batchSendSettingService,
        mailSenderAccountService = mailSenderAccountService,
        selfCheckService = selfCheckService,
        expertIndexWriterService = expertIndexWriterService,
        accountRateLimiter = accountRateLimiter,
        emailSuppressionService = emailSuppressionService,
        providerResolver = providerResolver,
        senderWarmupService = senderWarmupService,
        autoReplySettingService = autoReplySettingService,
        manualExpertMailService = manualExpertMailService,
        taskExecutionService = taskExecutionService,
        senderAccountBindingService = senderAccountBindingService,
        mailComposeTemplateService = mailComposeTemplateService
    )

    private fun fastConfig(
        roundSize: Int = 50,
        dailyCap: Int = 1000,
        perMailIntervalMs: Long = 0,
        perRoundIntervalMs: Long = 0
    ): BatchSendConfig = BatchSendConfig(
        autoEnabled = false, cron = "0 0 0 * * ?",
        dailyCap = dailyCap, roundSize = roundSize,
        perMailIntervalMs = perMailIntervalMs, perRoundIntervalMs = perRoundIntervalMs,
        selfCheckTtlMinutes = 30
    )

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        accountRateLimiter.clear()
        Mockito.`when`(autoReplySettingService.isGlobalEnabled()).thenReturn(true)
        Mockito.`when`(emailSuppressionService.isSuppressed(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(mailSendAttemptRepository.save(Mockito.any(MailSendAttempt::class.java))).thenAnswer { invocation ->
            invocation.getArgument<MailSendAttempt>(0).let { attempt ->
                if (attempt.id == null) attempt.copy(id = 77L) else attempt
            }
        }
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java))).thenAnswer { invocation ->
            val contact = invocation.getArgument<ExpertContact>(0)
            if (contact.id == null) contact.copy(id = 999L) else contact
        }
        // Default: no existing attempt (new expert)
        Mockito.`when`(mailSendAttemptRepository.findByOrcidIdAndMailType(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(null)
        // Default batch send config: fast intervals, generous caps
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig())
        // Default: one sendable account "chen"
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue())).thenReturn(listOf(account("chen")))
        Mockito.`when`(mailSenderAccountService.listAccounts()).thenReturn(listOf(account("chen")))
        Mockito.`when`(mailSenderAccountService.listEnabledAccounts()).thenReturn(listOf(account("chen")))
        // Default: binding resolution returns selected account
        Mockito.`when`(senderAccountBindingService.bindingFieldsFor(Mockito.anyString(), anyValue(LocalDateTime.now())))
            .thenReturn("chen" to LocalDateTime.of(2026, 8, 10, 12, 0, 0))
        // Default: self-check always passes (from cache, no probe)
        Mockito.`when`(selfCheckService.checkSendable(anyValue(account("chen")))).thenReturn(
            SelfCheckResult("chen", passed = true, message = null, fromCache = true)
        )
        Mockito.`when`(selfCheckService.checkSendable(anyValue(account("chen")), anyInt())).thenReturn(
            SelfCheckResult("chen", passed = true, message = null, fromCache = true)
        )
    }

    @Test
    fun `countPending counts new candidates from ES`() {
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(null)
        stubScrolledExperts(listOf(expert("0001", "a@b.com"), expert("0002", "c@d.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(expertContactRepository.existsByOrcidId("0002")).thenReturn(true)
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList())))
            .thenReturn(1L)

        val summary = service.countPending()
        assertEquals(1, summary.pending) // only 0001 has no contact
        assertEquals(0, summary.retryable)
        assertEquals(1, summary.totalSendable)
    }

    @Test
    fun `countPending counts retryable contacts without SENT introduction`() {
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(
            Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        )
        val contact = ExpertContact(id = 1L, campaignId = 10L, orcidId = "0003", expertEmail = "e@f.com", expertName = null, currentStatus = "NEW")
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(contact))
        // No SENT mail record exists
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("0003"))).thenReturn(listOf(expert("0003", "e@f.com")))
        stubScrolledExperts(emptyList())

        val summary = service.countPending()
        assertEquals(0, summary.pending)
        assertEquals(1, summary.retryable)
        assertEquals(1, summary.totalSendable)
    }

    @Test
    fun `countPending skips contacts with SENT introduction`() {
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(
            Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        )
        val contact = ExpertContact(id = 1L, campaignId = 10L, orcidId = "0003", expertEmail = "e@f.com", expertName = null, currentStatus = "NEW")
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(contact))
        // SENT mail record exists
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(listOf(
            MailRecord(expertContactId = 1L, direction = "OUTBOUND", mailType = "INTRODUCTION", sendStatus = "SENT",
                messageId = "msg", inReplyTo = null, subject = "s", body = "b", matchedQaRuleId = null,
                receivedAt = null, sentAt = LocalDateTime.now())
        ))
        stubScrolledExperts(emptyList())

        val summary = service.countPending()
        assertEquals(0, summary.pending)
        assertEquals(0, summary.retryable)
        assertEquals(0, summary.totalSendable)
    }

    @Test
    fun `countPending without manual campaign has no retryables`() {
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(null)
        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)

        val summary = service.countPending()

        assertEquals(1, summary.pending)
        assertEquals(0, summary.retryable)
        assertEquals(1, summary.totalSendable)
        Mockito.verify(campaignRepository, Mockito.never()).save(Mockito.any(Campaign::class.java))
    }

    @Test
    fun `countBySnapshot matches execution path totalEstimate for same snapshot (I-1)`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)

        // 2 retryable NEW contacts without SENT introduction, both present in ES scope
        val retryableContacts = listOf(
            ExpertContact(id = 1L, campaignId = 10L, orcidId = "R001", expertEmail = "r1@b.com", expertName = "R1", currentStatus = "NEW"),
            ExpertContact(id = 2L, campaignId = 10L, orcidId = "R002", expertEmail = "r2@b.com", expertName = "R2", currentStatus = "NEW")
        )
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(retryableContacts)
        for (contact in retryableContacts) {
            Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contact.id!!)).thenReturn(emptyList())
        }
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("R001", "R002"))).thenReturn(listOf(
            expert("R001", "r1@b.com"), expert("R002", "r2@b.com")
        ))

        // 3 ES-only candidates
        stubScrolledExperts(listOf(expert("0001", "a@b.com"), expert("0002", "c@d.com"), expert("0003", "e@f.com")))

        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE"
        )

        val preview = service.countBySnapshot(snapshot)
        assertEquals(2, preview.retryable)
        assertEquals(3, preview.pending)
        assertEquals(5, preview.totalSendable)

        // Execution path (same snapshot) must report the same total; no accounts → stops at the round gate
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue())).thenReturn(emptyList())
        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = true)

        assertEquals(preview.totalSendable, result.total)
        Mockito.verifyNoInteractions(taskExecutionService)
    }

    @Test
    fun `countBySnapshot without manual campaign returns zero retryable and creates no row (I-3)`() {
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(null)
        stubScrolledExperts(listOf(expert("0001", "a@b.com")))

        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE"
        )

        val summary = service.countBySnapshot(snapshot)

        assertEquals(1, summary.pending)
        assertEquals(0, summary.retryable)
        assertEquals(1, summary.totalSendable)
        Mockito.verify(campaignRepository, Mockito.never()).save(Mockito.any(Campaign::class.java))
        Mockito.verify(expertContactRepository, Mockito.never())
            .findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(Mockito.anyLong(), Mockito.anyString())
        Mockito.verifyNoInteractions(taskExecutionService)
    }

    @Test
    fun `countBySnapshot MATERIAL_REMINDER reuses material snapshot targets (I-1)`() {
        val snapshot = BatchExecutionSnapshot(
            mailType = "MATERIAL_REMINDER",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "APPLICATION",
            tags = listOf("承诺回复材料"),
            templateId = 42L
        )
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList())))
            .thenReturn(2L)
        Mockito.`when`(expertSearchService.searchExpertsFiltered(
            eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList()), anyInt(), anyInt()
        )).thenAnswer { invocation ->
            val from = invocation.getArgument<Int>(2)
            val size = invocation.getArgument<Int>(3)
            listOf(expert("M001", "m1@b.com"), expert("M002", "m2@b.com")).drop(from).take(size)
        }
        Mockito.`when`(expertContactRepository.findByOrcidIdIn(listOf("M001", "M002")))
            .thenReturn(listOf(
                ExpertContact(id = 21L, campaignId = 5L, orcidId = "M001", expertEmail = "m1@b.com", expertName = "M1", currentStatus = "CONTACTED"),
                ExpertContact(id = 22L, campaignId = 5L, orcidId = "M002", expertEmail = "m2@b.com", expertName = "M2", currentStatus = "CONTACTED")
            ))

        val preview = service.countBySnapshot(snapshot)
        assertEquals(2, preview.pending)
        assertEquals(0, preview.retryable)
        assertEquals(2, preview.totalSendable)

        // Execution path (same snapshot) must agree; no accounts → stops at the round gate
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue())).thenReturn(emptyList())
        val result = service.run(snapshot, 12346L, ExecutionMode.MANUAL, oneRoundOnly = true)

        assertEquals(preview.totalSendable, result.total)
        Mockito.verifyNoInteractions(taskExecutionService)
    }

    @Test
    fun `runBulkOutreach sends mail successfully`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        // No SENT introduction for new contact
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail(messageId = "msg1", status = "SENT"))

        val result = service.runBulkOutreach(12345L)
        assertEquals(1, result.total)
        assertEquals(1, result.sent)
        assertEquals(0, result.failed)
        Mockito.verify(txHelper).recordSuccess(
            contact = anyValue(ExpertContact(campaignId = 0, orcidId = "", expertEmail = "", expertName = null)),
            accountCode = eqValue("chen"),
            deliveredMessageId = Mockito.anyString(),
            subject = eqValue("Subject"),
            body = eqValue("Body"),
            attemptId = Mockito.anyLong()
        )
    }

    @Test
    fun `new contact is created with binding`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail(messageId = "msg1", status = "SENT"))
        Mockito.`when`(senderAccountBindingService.bindingFieldsFor(
            eqValue("chen"),
            anyValue(LocalDateTime.now())
        )).thenReturn("chen" to LocalDateTime.of(2026, 8, 10, 9, 30, 0))

        val result = service.runBulkOutreach(12345L)

        assertEquals(1, result.total)
        assertEquals(1, result.sent)
        val contactCaptor = org.mockito.ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(expertContactRepository).save(captureValue(contactCaptor, ExpertContact(
            campaignId = 0L, orcidId = "", expertEmail = "", expertName = null
        )))
        assertEquals("chen", contactCaptor.value.boundSenderAccountCode)
        assertNotNull(contactCaptor.value.senderAccountBoundAt)
    }

    @Test
    fun `existing contact binding is not overwritten`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        val existingContact = ExpertContact(
            id = 999L, campaignId = 10L, orcidId = "0001", expertEmail = "a@b.com",
            expertName = null, currentStatus = "NEW",
            boundSenderAccountCode = "old-account",
            senderAccountBoundAt = LocalDateTime.of(2026, 1, 1, 12, 0)
        )
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(existingContact))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(true)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("0001")))
            .thenReturn(listOf(expert("0001", "a@b.com")))
        stubScrolledExperts(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        // A3: 已绑定 contact → resolveForSend 返回账号（I-1：不重选号、不补写绑定）
        Mockito.`when`(senderAccountBindingService.resolveForSend(
            anyValue(existingContact), eqValue(false), eqValue(true)
        )).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail(messageId = "msg1", status = "SENT"))

        val result = service.runBulkOutreach(12345L)

        assertEquals(1, result.total)
        assertEquals(1, result.sent)
        Mockito.verify(expertContactRepository, Mockito.never()).updateBindingById(
            Mockito.anyLong(),
            Mockito.anyString(),
            Mockito.any()
        )
        Mockito.verify(expertContactRepository, Mockito.never()).save(Mockito.any(ExpertContact::class.java))
    }

    @Test
    fun `runBulkOutreach handles failure gracefully and continues`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenThrow(RuntimeException("SMTP connection failed"))

        val result = service.runBulkOutreach(12345L)
        assertEquals(1, result.total)
        assertEquals(0, result.sent)
        assertEquals(1, result.failed) // Failures now always count as failed, no unknown
    }

    @Test
    fun `runBulkOutreach terminates when cancelled`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com"), expert("0002", "c@d.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(expertContactRepository.existsByOrcidId("0002")).thenReturn(false)

        Mockito.`when`(progressStore.isCancelled(eqValue("MANUAL_INITIAL_OUTREACH"), eqValue(12345L))).thenReturn(true)

        val result = service.runBulkOutreach(12345L)
        assertEquals(2, result.total)
        assertEquals(0, result.sent)
        assertTrue(result.wasCancelled)
    }

    @Test
    fun `runBulkOutreach stops and reports quota exhaustion when selectAccount throws NoAvailableSenderAccountException`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)))
            .thenThrow(com.weibo.talentintroduction.mail.service.NoAvailableSenderAccountException("Quota exhausted"))

        val result = service.runBulkOutreach(12345L)
        assertEquals(1, result.total)
        assertEquals(0, result.sent)
        assertEquals(0, result.failed)
        assertEquals(1, result.skippedNoAccount)
    }

    @Test
    fun `runBulkOutreach skips contact with existing SENT introduction`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)

        // After contact is created with id=999L, hasSentIntroduction returns true
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(listOf(
            MailRecord(expertContactId = 999L, direction = "OUTBOUND", mailType = "INTRODUCTION", sendStatus = "SENT",
                messageId = "msg", inReplyTo = null, subject = "s", body = "b", matchedQaRuleId = null,
                receivedAt = null, sentAt = LocalDateTime.now())
        ))

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)

        val result = service.runBulkOutreach(12345L)
        assertEquals(1, result.total)
        assertEquals(0, result.sent)
        // SMTP was never called
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `runBulkOutreach deduplicates identical ORCIDs within the same batch`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com"), expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","",""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))

        val result = service.runBulkOutreach(12345L)
        assertEquals(2, result.total) // ES count estimate before dedup
        assertEquals(1, result.sent)
        Mockito.verify(mailDeliveryService, Mockito.times(1)).send(anyValue(account), anyValue(ComposedMail("","","")))
    }

    @Test
    fun `runBulkOutreach normalizes ORCIDs for deduplication`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert(" 0000-0001-a ", "a@b.com"), expert("0000-0001-A", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0000-0001-A")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","",""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))

        val result = service.runBulkOutreach(12345L)
        assertEquals(2, result.total) // ES count estimate before dedup
        assertEquals(1, result.sent)
    }

    @Test
    fun `runBulkOutreach deduplicates retryable and new candidates prioritizing retryable`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)

        val retryableContact = ExpertContact(id = 1L, campaignId = 10L, orcidId = "0001", expertEmail = "a@b.com", expertName = "A", currentStatus = "NEW")
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(retryableContact))
        // No SENT introduction for this contact
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())

        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("0001"))).thenReturn(listOf(expert("0001", "a@b.com")))
        stubScrolledExperts(listOf(expert("0001", "a@b.com")))

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","",""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))

        val result = service.runBulkOutreach(12345L)
        assertEquals(2, result.total) // retryable + ES estimate, deduped at send time
        assertEquals(1, result.sent)
        Mockito.verify(expertContactRepository, Mockito.never()).save(Mockito.any(ExpertContact::class.java))
    }

    @Test
    fun `runBulkOutreach upserts existing attempt on retry instead of inserting duplicate`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)

        val retryableContact = ExpertContact(id = 1L, campaignId = 10L, orcidId = "0001", expertEmail = "a@b.com", expertName = "A", currentStatus = "NEW")
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(retryableContact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("0001"))).thenReturn(listOf(expert("0001", "a@b.com")))
        stubScrolledExperts(emptyList())

        // Existing FAILED attempt from previous run
        val oldAttempt = MailSendAttempt(id = 50L, orcidId = "0001", mailType = "INTRODUCTION",
            accountCode = "old", messageId = "old-msg", status = MailSendAttemptStatus.FAILED,
            errorSummary = "previous error")
        Mockito.`when`(mailSendAttemptRepository.findByOrcidIdAndMailType("0001", "INTRODUCTION"))
            .thenReturn(oldAttempt)

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg-1", "SENT"))

        val result = service.runBulkOutreach(12345L)
        assertEquals(1, result.sent)

        // Verify the saved attempt reused the old ID (update, not insert)
        val captor = org.mockito.ArgumentCaptor.forClass(MailSendAttempt::class.java)
        // save is called twice: once for PREPARED, once by txHelper (mocked) — capture first
        Mockito.verify(mailSendAttemptRepository).save(captor.capture())
        val saved = captor.value
        assertEquals(50L, saved.id) // reused old attempt's ID
        assertEquals(MailSendAttemptStatus.PREPARED, saved.status)
        assertEquals("chen", saved.accountCode)
    }

    // ──── Phase 03: round-based scheduled batch tests ────

    @Test
    fun `runScheduledBatch triggers self-check for each sendable account at round gate`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg1", "SENT"))

        val result = service.runScheduledBatch(12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(1, result.sent)
        // L3-1: self-check invoked for the sendable account at the round gate
        Mockito.verify(selfCheckService).checkSendable(anyValue(account("chen")), anyInt())
    }

    @Test
    fun `runScheduledBatch pauses flow with NO_AVAILABLE_ACCOUNT when gate finds no sendable accounts`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue())).thenReturn(emptyList())
        Mockito.`when`(mailSenderAccountService.listEnabledAccounts()).thenReturn(
            listOf(account("chen").copy(autoSendPaused = true, autoSendPausedReason = "SMTP_ERROR"))
        )

        val result = service.runScheduledBatch(12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        assertEquals(0, result.sent)
        assertEquals("NO_AVAILABLE_ACCOUNT", result.stopReason)
        assertEquals("PAUSED", result.finalStatus)
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `runScheduledBatch pauses when self-check fails all sendable accounts mid-gate`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)

        // First call returns a candidate; after self-check pauses it, second call returns empty
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue()))
            .thenReturn(listOf(account("chen")))
            .thenReturn(emptyList())
        Mockito.`when`(mailSenderAccountService.listEnabledAccounts()).thenReturn(
            listOf(account("chen").copy(autoSendPaused = true, autoSendPausedReason = "SELF_CHECK_FAILED"))
        )

        val result = service.runScheduledBatch(12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        assertEquals(0, result.sent)
        assertEquals("NO_AVAILABLE_ACCOUNT", result.stopReason)
        assertEquals("PAUSED", result.finalStatus)
        // Self-check was invoked for the candidate
        Mockito.verify(selfCheckService).checkSendable(anyValue(account("chen")), anyInt())
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `runScheduledBatch ignores dailyCap and sends all experts (I-1)`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        // 5 experts, dailyCap=2, roundSize=10 → dailyCap no longer truncates; all 5 sent in one round (I-1)
        stubScrolledExperts(listOf(
            expert("0001", "a@b.com"), expert("0002", "c@d.com"), expert("0003", "e@f.com"),
            expert("0004", "g@h.com"), expert("0005", "i@j.com")
        ))
        for (orcid in listOf("0001", "0002", "0003", "0004", "0005")) {
            Mockito.`when`(expertContactRepository.existsByOrcidId(orcid)).thenReturn(false)
        }
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg", "SENT"))

        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig(roundSize = 10, dailyCap = 2))

        val result = service.runScheduledBatch(12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        // I-1: dailyCap=2 is no longer read; the round sends all 5 experts
        assertEquals(5, result.sent)
        assertEquals(5, result.total)
        assertEquals("COMPLETED", result.finalStatus)
        assertNotEquals("DAILY_CAP_REACHED", result.stopReason)
        Mockito.verify(mailDeliveryService, Mockito.times(5)).send(anyValue(account), anyValue(ComposedMail("","","")))
    }

    @Test
    fun `runScheduledBatch stops at account capacity with DAILY_LIMIT_REACHED (I-5)`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        // 20 experts, roundSize=10, roundsPerRun=5 → round 1 sends 3 (account capacity 3), round 2 hits DAILY_LIMIT_REACHED
        stubScrolledExperts((1..20).map { expert("N%04d".format(it), "n$it@test.com") })
        for (i in 1..20) {
            Mockito.`when`(expertContactRepository.existsByOrcidId("N%04d".format(i))).thenReturn(false)
        }
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg", "SENT"))

        // dailyCap=50 → toSnapshot derives roundsPerRun = ceil(50/10) = 5
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig(roundSize = 10, dailyCap = 50))
        // runRoundGate lists sendable accounts twice per round: round 1 has capacity 3, round 2 has capacity 0
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue()))
            .thenReturn(listOf(account.copy(dailySendLimit = 3, todaySentCount = 0)))
            .thenReturn(listOf(account.copy(dailySendLimit = 3, todaySentCount = 0)))
            .thenReturn(listOf(account.copy(dailySendLimit = 3, todaySentCount = 3)))
            .thenReturn(listOf(account.copy(dailySendLimit = 3, todaySentCount = 3)))

        val result = service.runScheduledBatch(12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        // I-5: account capacity is the only daily-volume bound — exactly 3 sent, then DAILY_LIMIT_REACHED
        assertEquals(3, result.sent)
        assertEquals("DAILY_LIMIT_REACHED", result.stopReason)
        assertEquals("COMPLETED", result.finalStatus)
        Mockito.verify(mailDeliveryService, Mockito.times(3)).send(anyValue(account), anyValue(ComposedMail("","","")))
    }

    @Test
    fun `runScheduledBatch oneRoundOnly returns PAUSED after single round`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        // 3 experts, roundSize=2 → one round sends 2, returns PAUSED (L3-2)
        stubScrolledExperts(listOf(expert("0001", "a@b.com"), expert("0002", "c@d.com"), expert("0003", "e@f.com")))
        for (orcid in listOf("0001", "0002", "0003")) {
            Mockito.`when`(expertContactRepository.existsByOrcidId(orcid)).thenReturn(false)
        }
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg", "SENT"))

        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig(roundSize = 2, dailyCap = 1000))

        val result = service.runScheduledBatch(12345L, ExecutionMode.MANUAL, oneRoundOnly = true)

        // Only one round of 2 mails
        assertEquals(2, result.sent)
        assertEquals(3, result.total)
        assertEquals(1, result.remaining)
        // L3-2: oneRoundOnly returns to PAUSED
        assertEquals("PAUSED", result.finalStatus)
        assertEquals("ONE_ROUND_DONE", result.stopReason)
        Mockito.verify(mailDeliveryService, Mockito.times(2)).send(anyValue(account), anyValue(ComposedMail("","","")))
    }

    @Test
    fun `runScheduledBatch splits snapshot into multiple rounds`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        // 3 experts, roundSize=2, dailyCap=10 → 2 rounds (2 + 1)
        stubScrolledExperts(listOf(expert("0001", "a@b.com"), expert("0002", "c@d.com"), expert("0003", "e@f.com")))
        for (orcid in listOf("0001", "0002", "0003")) {
            Mockito.`when`(expertContactRepository.existsByOrcidId(orcid)).thenReturn(false)
        }
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg", "SENT"))

        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig(roundSize = 2, dailyCap = 10))

        val result = service.runScheduledBatch(12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        assertEquals(3, result.sent)
        assertEquals(3, result.total)
        assertEquals("COMPLETED", result.finalStatus)
        // Round gate invoked for each round (2 rounds)
        Mockito.verify(selfCheckService, Mockito.atLeast(2)).checkSendable(anyValue(account("chen")), anyInt())
    }

    @Test
    fun `runScheduledBatch progress details contain per-account stats`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg", "SENT"))

        service.runScheduledBatch(12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        // I-8: capture progress updates and verify per-account stats + executionMode
        val progressCaptor = org.mockito.ArgumentCaptor.forClass(TaskProgress::class.java)
        Mockito.verify(progressStore, Mockito.atLeastOnce()).update(
            eqValue("MANUAL_INITIAL_OUTREACH"),
            captureValue(progressCaptor, TaskProgress("MANUAL_INITIAL_OUTREACH", "RUNNING", 0, 0, 0)),
            eqValue(12345L)
        )
        val finalProgress = progressCaptor.allValues.last()
        val details = finalProgress.details
        assertNotNull(details)
        assertEquals("MANUAL", details!!["executionMode"])
        // accounts array present with per-account row
        @Suppress("UNCHECKED_CAST")
        val accounts = details["accounts"] as? List<AccountStatRow>
        assertNotNull(accounts)
        assertTrue(accounts!!.isNotEmpty())
        val chenRow = accounts.first { it.accountCode == "chen" }
        assertEquals(100, chenRow.dailyLimit)
        assertEquals(1, chenRow.success)
        assertEquals(0, chenRow.failed)
        assertFalse(chenRow.paused)
    }

    @Test
    fun `runScheduledBatch progress uses per-batch counts instead of cumulative counts`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(
            expert("0001", "a@b.com"),
            expert("0002", "b@b.com")
        ))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(expertContactRepository.existsByOrcidId("0002")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg", "SENT"))
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig(roundSize = 1, dailyCap = 10))

        service.runScheduledBatch(12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        val progressCaptor = org.mockito.ArgumentCaptor.forClass(TaskProgress::class.java)
        Mockito.verify(progressStore, Mockito.atLeastOnce()).update(
            eqValue("MANUAL_INITIAL_OUTREACH"),
            captureValue(progressCaptor, TaskProgress("MANUAL_INITIAL_OUTREACH", "RUNNING", 0, 0, 0)),
            eqValue(12345L)
        )
        val runningUpdates = progressCaptor.allValues.filter {
            it.status == "RUNNING" && it.message?.startsWith("正在发送") == true
        }
        assertEquals(2, runningUpdates.size)
        assertEquals(1, runningUpdates[0].batchNumber)
        assertEquals(1, runningUpdates[0].batchProcessed)
        assertEquals(1, runningUpdates[0].batchPassed)
        assertEquals(0, runningUpdates[0].batchRejected)
        assertEquals(2, runningUpdates[1].batchNumber)
        assertEquals(1, runningUpdates[1].batchProcessed)
        assertEquals(1, runningUpdates[1].batchPassed)
        assertEquals(0, runningUpdates[1].batchRejected)
    }

    @Test
    fun `runScheduledBatch AUTO mode writes executionMode=AUTO to progress`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg", "SENT"))

        service.runScheduledBatch(12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        val progressCaptor = org.mockito.ArgumentCaptor.forClass(TaskProgress::class.java)
        Mockito.verify(progressStore, Mockito.atLeastOnce()).update(
            eqValue("MANUAL_INITIAL_OUTREACH"),
            captureValue(progressCaptor, TaskProgress("MANUAL_INITIAL_OUTREACH", "RUNNING", 0, 0, 0)),
            eqValue(12345L)
        )
        val finalProgress = progressCaptor.allValues.last()
        assertEquals("AUTO", finalProgress.details!!["executionMode"])
    }

    @Test
    fun `runScheduledBatch marks EMAIL_INVALID on PERMANENT SMTP error and excludes from next snapshot`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "bad@example.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("bad@example.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(
            DeliveredMail(
                messageId = "msg-1",
                status = "FAILED",
                errorCategory = SmtpErrorCategory.PERMANENT,
                smtpResponseCode = 550,
                errorDetail = "550 5.1.1 User unknown"
            )
        )

        val firstRun = service.runScheduledBatch(12345L, ExecutionMode.MANUAL, oneRoundOnly = false)
        assertEquals(1, firstRun.failed)
        assertEquals(0, firstRun.sent)

        Mockito.verify(expertIndexWriterService).syncOperatorStatus("0001", "EMAIL_INVALID")
        Mockito.verify(expertContactRepository, Mockito.atLeastOnce()).save(
            Mockito.argThat { contact: ExpertContact -> contact.operatorStatus == "EMAIL_INVALID" }
        )

        val invalidatedContact = ExpertContact(
            id = 999L,
            campaignId = 10L,
            orcidId = "0001",
            expertEmail = "bad@example.com",
            expertName = "Given Family",
            currentStatus = "NEW",
            operatorStatus = "EMAIL_INVALID"
        )
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(invalidatedContact))
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("0001"))).thenReturn(listOf(expert("0001", "bad@example.com")))
        stubScrolledExperts(emptyList())

        val secondRun = service.runScheduledBatch(12346L, ExecutionMode.MANUAL, oneRoundOnly = false)
        assertEquals(0, secondRun.total)
        Mockito.verify(mailDeliveryService, Mockito.times(1)).send(anyValue(account), anyValue(ComposedMail("","","")))
    }

    @Test
    fun `runScheduledBatch throttles but continues on 421 SMTP rate limit`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com"), expert("0002", "c@d.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(expertContactRepository.existsByOrcidId("0002")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(
            DeliveredMail(
                messageId = "msg-1",
                status = "FAILED",
                errorCategory = SmtpErrorCategory.TRANSIENT,
                smtpResponseCode = 421,
                errorDetail = "421 4.7.0 Try again later"
            )
        )
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig(roundSize = 10, dailyCap = 100, perMailIntervalMs = 1000))

        val result = service.runScheduledBatch(12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(2, result.failed)
        assertEquals(0, result.sent)
        Mockito.verify(mailSenderAccountService, Mockito.never()).pauseAutoSend(Mockito.anyString(), Mockito.anyString())
        Mockito.verify(mailDeliveryService, Mockito.times(2)).send(anyValue(account), anyValue(ComposedMail("","","")))
        assertEquals(4000L, accountRateLimiter.getIntervalMs("chen", "other", 1000L))
        assertEquals(1000L, accountRateLimiter.getIntervalMs("chen", 1000L))
    }

    @Test
    fun `runScheduledBatch throttles only throttled provider not others`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(
            expert("0001", "user@gmail.com"),
            expert("0002", "user@gmail.com"),
            expert("0003", "user@outlook.com")
        ))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(expertContactRepository.existsByOrcidId("0002")).thenReturn(false)
        Mockito.`when`(expertContactRepository.existsByOrcidId("0003")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenAnswer { invocation ->
            val expertArg = invocation.getArgument<ExpertProfile>(1)
            ComposedMail(expertArg.email ?: "", "Subject", "Body")
        }
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenAnswer { invocation ->
            val mail = invocation.getArgument<ComposedMail>(1)
            if (mail.to.endsWith("@gmail.com")) {
                DeliveredMail(
                    messageId = "msg-1",
                    status = "FAILED",
                    errorCategory = SmtpErrorCategory.TRANSIENT,
                    smtpResponseCode = 421,
                    errorDetail = "421 4.7.0 Try again later"
                )
            } else {
                DeliveredMail(messageId = "msg-2", status = "SENT")
            }
        }
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig(roundSize = 10, dailyCap = 100, perMailIntervalMs = 1000))

        val result = service.runScheduledBatch(12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(2, result.failed)
        assertEquals(1, result.sent)
        assertEquals(4000L, accountRateLimiter.getIntervalMs("chen", "gmail", 1000L))
        assertEquals(1000L, accountRateLimiter.getIntervalMs("chen", "outlook", 1000L))
        assertEquals(1000L, accountRateLimiter.getIntervalMs("chen", 1000L))
    }

    @Test
    fun `runScheduledBatch pauses account and stops round on non-rate-limit TRANSIENT SMTP error`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com"), expert("0002", "c@d.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(expertContactRepository.existsByOrcidId("0002")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(
            DeliveredMail(
                messageId = "msg-1",
                status = "FAILED",
                errorCategory = SmtpErrorCategory.TRANSIENT,
                smtpResponseCode = 450,
                errorDetail = "450 mailbox busy"
            )
        )
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig(roundSize = 10, dailyCap = 100))

        val result = service.runScheduledBatch(12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(1, result.failed)
        assertEquals(0, result.sent)
        Mockito.verify(mailSenderAccountService).pauseAutoSend(
            eqValue("chen"),
            eqValue("SMTP_TRANSIENT:450:450 mailbox busy")
        )
        Mockito.verify(mailDeliveryService, Mockito.times(1)).send(anyValue(account), anyValue(ComposedMail("","","")))
        Mockito.verify(txHelper).recordFailure(
            contactId = Mockito.eq(999L),
            accountCode = eqValue("chen"),
            messageId = Mockito.anyString(),
            errorSummary = Mockito.contains("TRANSIENT:450"),
            subject = eqValue("Subject"),
            body = eqValue("Body"),
            attemptId = Mockito.eq(77L)
        )
    }

    @Test
    fun `runScheduledBatch preserves anti-duplicate semantics for already CONTACTED expert`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        // I-7: SENT introduction already exists → skip
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(listOf(
            MailRecord(expertContactId = 999L, direction = "OUTBOUND", mailType = "INTRODUCTION", sendStatus = "SENT",
                messageId = "msg", inReplyTo = null, subject = "s", body = "b", matchedQaRuleId = null,
                receivedAt = null, sentAt = LocalDateTime.now())
        ))

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)

        val result = service.runScheduledBatch(12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        // I-7: no send for already-CONTACTED expert
        assertEquals(0, result.sent)
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `runScheduledBatch streams retryable and ES candidates across rounds`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)

        val retryableContacts = listOf(
            ExpertContact(id = 1L, campaignId = 10L, orcidId = "R001", expertEmail = "r1@b.com", expertName = "R1", currentStatus = "NEW"),
            ExpertContact(id = 2L, campaignId = 10L, orcidId = "R002", expertEmail = "r2@b.com", expertName = "R2", currentStatus = "NEW"),
            ExpertContact(id = 3L, campaignId = 10L, orcidId = "R003", expertEmail = "r3@b.com", expertName = "R3", currentStatus = "NEW")
        )
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(retryableContacts)
        for (contact in retryableContacts) {
            Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contact.id!!)).thenReturn(emptyList())
        }
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("R001", "R002", "R003"))).thenReturn(listOf(
            expert("R001", "r1@b.com"), expert("R002", "r2@b.com"), expert("R003", "r3@b.com")
        ))

        val esExperts = listOf(
            expert("E001", "e1@b.com"), expert("E002", "e2@b.com"), expert("E003", "e3@b.com"),
            expert("E004", "e4@b.com"), expert("E005", "e5@b.com"), expert("E006", "e6@b.com"),
            expert("E007", "e7@b.com")
        )
        stubPagedExperts(esExperts)
        Mockito.`when`(expertSearchService.countExperts(
            eqValue(ExpertIndexLevel.CANDIDATE),
            anyValue(emptyList())
        )).thenReturn(7L)

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg", "SENT"))
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig(roundSize = 5, dailyCap = 1000))

        val result = service.runScheduledBatch(12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        assertEquals(10, result.total)
        assertEquals(10, result.sent)
        assertEquals("COMPLETED", result.finalStatus)
        Mockito.verify(mailDeliveryService, Mockito.times(10)).send(anyValue(account), anyValue(ComposedMail("","","")))
    }

    @Test
    fun `countPending reads emailDomain from configuration`() {
        val configWithDomain = fastConfig().copy(emailDomain = "gmail.com")
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(configWithDomain)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(null)
        
        val expectedFilters = ExpertSearchService.notContactedWithEmailFilters("gmail.com")
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(5L)

        val summary = service.countPending()
        assertEquals(5, summary.pending)
        assertEquals(5, summary.totalSendable)
    }

    @Test
    fun `runScheduledBatch passes configured emailDomain to ES filter`() {
        val configWithDomain = fastConfig().copy(emailDomain = "gmail.com")
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(configWithDomain)

        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        // P2a: 单值配置经 KV 桥接成单元素 list，ES 侧走多域版过滤器。
        val expectedFilters = ExpertSearchService.notContactedWithEmailDomainsFilters(listOf("gmail.com"))
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(0L)

        val result = service.runScheduledBatch(12345L, ExecutionMode.MANUAL, oneRoundOnly = false)
        assertEquals(0, result.total)
        
        Mockito.verify(expertSearchService).countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters))
    }

    @Test
    fun `countPending reads discipline from configuration`() {
        val configWithDiscipline = fastConfig().copy(discipline = "STEM")
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(configWithDiscipline)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(null)

        val expectedFilters = ExpertSearchService.notContactedWithEmailFilters(null, "STEM")
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(3L)

        val summary = service.countPending()
        assertEquals(3, summary.pending)
        assertEquals(3, summary.totalSendable)
    }

    @Test
    fun `runScheduledBatch passes configured discipline to ES filter`() {
        val configWithDiscipline = fastConfig().copy(emailDomain = "gmail.com", discipline = "HUMANITIES")
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(configWithDiscipline)

        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        val expectedFilters = ExpertSearchService.notContactedWithEmailDomainsFilters(listOf("gmail.com"), "HUMANITIES")
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(0L)

        val result = service.runScheduledBatch(12345L, ExecutionMode.MANUAL, oneRoundOnly = false)
        assertEquals(0, result.total)

        Mockito.verify(expertSearchService).countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters))
    }

    @Test
    fun `countPending excludes non-STEM retryable when discipline is STEM`() {
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig().copy(discipline = "STEM"))
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(
            Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        )
        val stemContact = ExpertContact(id = 1L, campaignId = 10L, orcidId = "STEM1", expertEmail = "s@x.com", expertName = "S", currentStatus = "NEW")
        val humContact = ExpertContact(id = 2L, campaignId = 10L, orcidId = "HUM1", expertEmail = "h@x.com", expertName = "H", currentStatus = "NEW")
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(stemContact, humContact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(2L)).thenReturn(emptyList())
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("STEM1", "HUM1"))).thenReturn(
            listOf(
                expert("STEM1", "s@x.com").copy(disciplineCategory = "STEM"),
                expert("HUM1", "h@x.com").copy(disciplineCategory = "HUMANITIES")
            )
        )
        val expectedFilters = ExpertSearchService.notContactedWithEmailFilters(null, "STEM")
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(0L)

        val summary = service.countPending()
        assertEquals(0, summary.pending)
        assertEquals(1, summary.retryable)
        assertEquals(1, summary.totalSendable)
    }

    @Test
    fun `countPending keeps all retryable when discipline is blank`() {
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig().copy(discipline = ""))
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(
            Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        )
        val humContact = ExpertContact(id = 2L, campaignId = 10L, orcidId = "HUM1", expertEmail = "h@x.com", expertName = "H", currentStatus = "NEW")
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(humContact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(2L)).thenReturn(emptyList())
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("HUM1"))).thenReturn(
            listOf(expert("HUM1", "h@x.com").copy(disciplineCategory = "HUMANITIES"))
        )
        stubScrolledExperts(emptyList())

        val summary = service.countPending()
        assertEquals(1, summary.retryable)
        assertEquals(1, summary.totalSendable)
    }

    @Test
    fun `runScheduledBatch excludes non-STEM retryable when discipline is STEM`() {
        val account = account("chen")
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig().copy(discipline = "STEM"))
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)

        val humContact = ExpertContact(id = 2L, campaignId = 10L, orcidId = "HUM1", expertEmail = "h@x.com", expertName = "H", currentStatus = "NEW")
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(humContact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(2L)).thenReturn(emptyList())
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("HUM1"))).thenReturn(
            listOf(expert("HUM1", "h@x.com").copy(disciplineCategory = "HUMANITIES"))
        )

        val expectedFilters = ExpertSearchService.notContactedWithEmailFilters(null, "STEM")
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(0L)
        stubPagedExperts(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)

        val result = service.runScheduledBatch(12345L, ExecutionMode.MANUAL, oneRoundOnly = false)
        assertEquals(0, result.total)
        assertEquals(0, result.sent)
        Mockito.verify(mailDeliveryService, Mockito.never()).send(anyValue(account), anyValue(ComposedMail("", "", "")))
    }

    @Test
    fun `runScheduledBatch completes with WARMUP_LIMIT_REACHED when all warmup accounts hit effective limit`() {
        val now = LocalDateTime.of(2026, 6, 24, 12, 0)
        val warmupAccount = account("chen").copy(
            dailySendLimit = 500,
            todaySentCount = 20,
            warmupEnabled = true,
            warmupStartedAt = now,
            warmupStepsJson = """[{"dayFrom":1,"limit":20}]"""
        )
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue())).thenReturn(emptyList())
        Mockito.`when`(mailSenderAccountService.listEnabledAccounts()).thenReturn(listOf(warmupAccount))

        val result = service.runScheduledBatch(12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        assertEquals("WARMUP_LIMIT_REACHED", result.stopReason)
        assertEquals("COMPLETED", result.finalStatus)
        assertEquals(0, result.sent)
        Mockito.verify(mailSenderAccountService, Mockito.never()).pauseAutoSend(Mockito.anyString(), Mockito.anyString())

        val progressCaptor = org.mockito.ArgumentCaptor.forClass(TaskProgress::class.java)
        Mockito.verify(progressStore, Mockito.atLeastOnce()).update(
            eqValue("MANUAL_INITIAL_OUTREACH"),
            captureValue(progressCaptor, TaskProgress("MANUAL_INITIAL_OUTREACH", "COMPLETED", 0, 0, 0)),
            eqValue(12345L)
        )
        assertTrue(progressCaptor.allValues.last().message?.contains("预热上限") == true)
    }

    @Test
    fun `runScheduledBatch prefers NO_AVAILABLE_ACCOUNT when one account is fault paused and others at limit`() {
        val now = LocalDateTime.of(2026, 6, 24, 12, 0)
        val atLimit = account("chen").copy(
            dailySendLimit = 500,
            todaySentCount = 20,
            warmupEnabled = true,
            warmupStartedAt = now,
            warmupStepsJson = """[{"dayFrom":1,"limit":20}]"""
        )
        val faultPaused = account("li").copy(autoSendPaused = true, autoSendPausedReason = "SMTP_INFRA")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue())).thenReturn(emptyList())
        Mockito.`when`(mailSenderAccountService.listEnabledAccounts()).thenReturn(listOf(atLimit, faultPaused))

        val result = service.runScheduledBatch(12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        assertEquals("NO_AVAILABLE_ACCOUNT", result.stopReason)
        assertEquals("PAUSED", result.finalStatus)
    }

    @Test
    fun `runScheduledBatch MANUAL bypasses warmup limit and sends when account is below dailySendLimit`() {
        val now = LocalDateTime.of(2026, 6, 24, 12, 0)
        val warmupAccount = account("chen").copy(
            dailySendLimit = 100,
            todaySentCount = 20,
            warmupEnabled = true,
            warmupStartedAt = now,
            warmupStepsJson = """[{"dayFrom":1,"limit":20}]"""
        )
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(true)).thenReturn(listOf(warmupAccount))
        Mockito.`when`(mailSenderAccountService.listEnabledAccounts()).thenReturn(listOf(warmupAccount))
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), eqValue(true), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(warmupAccount)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull()))
            .thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(warmupAccount), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg1", "SENT"))

        val result = service.runScheduledBatch(12345L, ExecutionMode.MANUAL, oneRoundOnly = true)

        assertEquals(1, result.sent)
        assertEquals("ONE_ROUND_DONE", result.stopReason)
        assertNotEquals("WARMUP_LIMIT_REACHED", result.stopReason)
    }

    @Test
    fun `runScheduledBatch MANUAL reports DAILY_LIMIT_REACHED not WARMUP when dailySendLimit exhausted`() {
        val now = LocalDateTime.of(2026, 6, 24, 12, 0)
        val warmupAccount = account("chen").copy(
            dailySendLimit = 100,
            todaySentCount = 100,
            warmupEnabled = true,
            warmupStartedAt = now,
            warmupStepsJson = """[{"dayFrom":1,"limit":20}]"""
        )
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(true)).thenReturn(emptyList())
        Mockito.`when`(mailSenderAccountService.listEnabledAccounts()).thenReturn(listOf(warmupAccount))

        val result = service.runScheduledBatch(12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals("DAILY_LIMIT_REACHED", result.stopReason)
        assertNotEquals("WARMUP_LIMIT_REACHED", result.stopReason)
        assertEquals("COMPLETED", result.finalStatus)
        assertEquals(0, result.sent)
    }

    @Test
    fun `runMaterialReminderBatch gate rejection records one skip, single progress advancement, and continues (V-1)`() {
        // V-1: the MATERIAL_REMINDER gate catch must not double-count processedTotal/roundSent/roundProcessed,
        // must record exactly one PERSONALIZATION_INCOMPLETE skip (not a failure), and must continue the batch.
        Mockito.`when`(batchSendSettingService.getConfig(BatchSendType.MATERIAL_REMINDER))
            .thenReturn(
                BatchSendConfig(
                    sendType = BatchSendType.MATERIAL_REMINDER,
                    autoEnabled = false, cron = "0 0 8 * * ?",
                    dailyCap = 60, roundSize = 30,
                    perMailIntervalMs = 0, perRoundIntervalMs = 0,
                    selfCheckTtlMinutes = 30, templateId = 10L
                )
            )

        val blockedId = 701L
        val sentId = 702L
        val targets = listOf(
            Pair(
                ExpertContact(
                    id = blockedId, campaignId = 10L, orcidId = "V001", expertEmail = "v1@test.com",
                    expertName = "V1", currentStatus = "WAITING_REPLY"
                ),
                expert("V001", "v1@test.com")
            ),
            Pair(
                ExpertContact(
                    id = sentId, campaignId = 10L, orcidId = "V002", expertEmail = "v2@test.com",
                    expertName = "V2", currentStatus = "WAITING_REPLY"
                ),
                expert("V002", "v2@test.com")
            )
        )
        listOf(blockedId, sentId).forEach { cid ->
            Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(cid))
                .thenReturn(emptyList())
        }
        Mockito.`when`(expertSearchService.countExperts(
            eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList())
        )).thenReturn(2L)
        Mockito.`when`(expertSearchService.searchExpertsFiltered(
            eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList()), eqValue(0), anyInt()
        )).thenReturn(targets.map { it.second })
        Mockito.`when`(expertContactRepository.findByOrcidIdIn(anyValue(emptyList())))
            .thenReturn(targets.map { it.first })

        val acc = account("chen")
        Mockito.`when`(senderAccountAssignmentService.selectAccount(
            anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)
        )).thenReturn(acc)
        stubReminderResolveForSendNotBound()
        Mockito.`when`(manualExpertMailService.sendManualMail(
            anyLong(),
            anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", ""))
        )).thenAnswer { invocation ->
            val cid = invocation.getArgument<Long>(0)
            if (cid == blockedId) {
                throw com.weibo.talentintroduction.mail.service.PersonalizationGateException(listOf("recentWorkTitle"))
            }
            com.weibo.talentintroduction.mail.service.ManualMailSendResult(
                contactId = cid, senderAccountCode = "chen",
                mailType = "MATERIAL_REMINDER", subject = "Subj",
                sendStatus = "SENT", messageId = "msg-$cid"
            )
        }

        val processedCounts = mutableListOf<Long>()
        // 注意：TaskProgressStore.update 是 Kotlin 非空参数接口，Mockito.any(Class)（返回 null）
        // 会被 Kotlin 的非空检查拦截（"any(...) must not be null"），因此用本仓库的 anyValue 助手
        // （内部注册 any() 匹配器、返回非空默认值）来匹配 progress 参数。
        Mockito.`when`(progressStore.update(
            Mockito.anyString(),
            anyValue(TaskProgress(taskType = "", status = "", batchNumber = 0, processedCount = 0, totalCount = 0)),
            Mockito.anyLong()
        )).thenAnswer { invocation ->
            processedCounts.add(invocation.getArgument<TaskProgress>(1).processedCount)
            true
        }

        val result = service.runMaterialReminderBatch(12345L, ExecutionMode.MANUAL, false)

        // gate rejection is exactly one skip with the PERSONALIZATION_INCOMPLETE label, not a failure
        assertEquals(1, result.skipped)
        val outcome = result.outcome!!
        assertEquals(0, outcome.failure)
        assertEquals(
            1,
            outcome.skippedReasons[BatchOutcomeReasonCodes.PERSONALIZATION_INCOMPLETE]?.count ?: 0
        )
        assertEquals(
            "个性化字段缺失",
            outcome.skippedReasons[BatchOutcomeReasonCodes.PERSONALIZATION_INCOMPLETE]?.label
        )
        // the batch continues: the following recipient is still sent
        assertEquals(1, result.sent)
        Mockito.verify(manualExpertMailService, Mockito.times(2)).sendManualMail(
            anyLong(),
            anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", ""))
        )
        // single progress advancement per recipient: 2 recipients ⇒ processedCount never exceeds 2
        // (pre-fix double counting reached 3 for the gate-blocked recipient)
        assertEquals(2L, processedCounts.maxOrNull())
        assertEquals(2L, processedCounts.last())
    }

    @Test
    fun `loads binding stock once per batch`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
        stubScrolledExperts(listOf(expert("0001", "a@b.com"), expert("0002", "c@d.com"), expert("0003", "e@f.com")))
        for (orcid in listOf("0001", "0002", "0003")) {
            Mockito.`when`(expertContactRepository.existsByOrcidId(orcid)).thenReturn(false)
        }
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
        Mockito.`when`(senderAccountAssignmentService.loadBindingStock())
            .thenReturn(SenderBindingStock(emptyMap(), emptyMap(), emptyMap()))
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg1", "SENT"))

        val result = service.runScheduledBatch(12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(3, result.sent)
        assertEquals(3, result.total)
        // I-1: 快照在批次开始处取一次（round 循环之外），而非每个专家一次
        Mockito.verify(senderAccountAssignmentService, Mockito.times(1)).loadBindingStock()
    }

    // ──── roundsPerRun (execution round budget) tests ────

    private fun introSnapshot(
        roundSize: Int,
        roundsPerRun: Int,
        perRoundIntervalMs: Long = 0
    ) = com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot(
        mailType = "INTRODUCTION",
        roundSize = roundSize,
        roundsPerRun = roundsPerRun,
        perMailIntervalMs = 0,
        perRoundIntervalMs = perRoundIntervalMs,
        selfCheckTtlMinutes = 30
    )

    private fun stubIntroSendPipeline(account: MailSenderAccount, experts: List<ExpertProfile>) {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
        stubScrolledExperts(experts)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
        Mockito.`when`(senderAccountAssignmentService.selectAccount(
            anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)
        )).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("", "")), Mockito.isNull()))
            .thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg", "SENT"))
    }

    /**
     * ES paging stub that serves a fresh page slice on every call. The lazy
     * [OutreachTargetIterator] refetches from offset 0 and dedups via seenOrcids,
     * so a flat drop/take stub can only ever surface one page (roundSize * 2).
     */
    private fun stubIntroChunkedExperts(experts: List<ExpertProfile>, pageSize: Int) {
        val chunks = experts.chunked(pageSize)
        var callIndex = 0
        Mockito.`when`(expertSearchService.searchExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList()), anyInt(), anyInt()
        )).thenAnswer { invocation ->
            val from = invocation.getArgument<Int>(2)
            val size = invocation.getArgument<Int>(3)
            val chunk = chunks.getOrNull(callIndex) ?: emptyList()
            callIndex++
            chunk.drop(from).take(size)
        }
        Mockito.`when`(expertSearchService.countExperts(
            eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList())
        )).thenReturn(experts.size.toLong())
    }

    @Test
    fun `roundsPerRun bounds a single execution at rounds times round size`() {
        val account = account("chen")
        val experts = (1..100).map { expert("E%04d".format(it), "e$it@test.com") }
        stubIntroSendPipeline(account, experts)
        // roundSize=20 ⇒ iterator pageSize=40; serve fresh pages so all 100 targets are reachable
        stubIntroChunkedExperts(experts, pageSize = 40)

        val result = service.run(
            introSnapshot(roundSize = 20, roundsPerRun = 2),
            12345L, ExecutionMode.AUTO, oneRoundOnly = false
        )

        // Observable outcome 2: roundsPerRun(2) × roundSize(20) = 40 mails max for this run
        assertEquals(40, result.sent)
        assertEquals(100, result.total)
        // I-1/I-2: budget exhausted is a normal completion, not a pause
        assertEquals("ROUNDS_PER_RUN_REACHED", result.stopReason)
        assertEquals("COMPLETED", result.finalStatus)
        Mockito.verify(mailDeliveryService, Mockito.times(40)).send(anyValue(account), anyValue(ComposedMail("", "", "")))
    }

    @Test
    fun `roundsPerRun not exhausted does not report ROUNDS_PER_RUN_REACHED`() {
        val account = account("chen")
        stubIntroSendPipeline(
            account,
            (1..10).map { expert("F%04d".format(it), "f$it@test.com") }
        )

        val result = service.run(
            introSnapshot(roundSize = 20, roundsPerRun = 5),
            12345L, ExecutionMode.AUTO, oneRoundOnly = false
        )

        // Only 10 targets exist; the run ends after one partial round without touching the round budget
        assertEquals(10, result.sent)
        assertNotEquals("ROUNDS_PER_RUN_REACHED", result.stopReason)
        assertEquals("COMPLETED", result.finalStatus)
    }

    @Test
    fun `oneRoundOnly takes precedence over roundsPerRun`() {
        val account = account("chen")
        stubIntroSendPipeline(
            account,
            (1..100).map { expert("G%04d".format(it), "g$it@test.com") }
        )

        val result = service.run(
            introSnapshot(roundSize = 20, roundsPerRun = 5),
            12345L, ExecutionMode.MANUAL, oneRoundOnly = true
        )

        // I-4: oneRoundOnly wins even when roundsPerRun = 5
        assertEquals(20, result.sent)
        assertEquals("ONE_ROUND_DONE", result.stopReason)
        assertEquals("PAUSED", result.finalStatus)
        Mockito.verify(mailDeliveryService, Mockito.times(20)).send(anyValue(account), anyValue(ComposedMail("", "", "")))
    }

    @Test
    fun `no round interval sleep after roundsPerRun budget is spent`() {
        val account = account("chen")
        stubIntroSendPipeline(
            account,
            (1..50).map { expert("H%04d".format(it), "h$it@test.com") }
        )

        val start = System.currentTimeMillis()
        val result = service.run(
            introSnapshot(roundSize = 5, roundsPerRun = 1, perRoundIntervalMs = 120000),
            12345L, ExecutionMode.AUTO, oneRoundOnly = false
        )
        val elapsedMs = System.currentTimeMillis() - start

        // B-2: the 120s round interval must not be slept once the budget (1 round) is spent
        assertEquals(5, result.sent)
        assertEquals("ROUNDS_PER_RUN_REACHED", result.stopReason)
        assertTrue(elapsedMs < 5000, "expected no 120s round-interval sleep, took ${elapsedMs}ms")
    }

    // ──── Material Reminder Batch Tests ────

    @org.junit.jupiter.api.Nested
    inner class ReminderBatchTests {

        private fun reminderConfig(templateId: Long = 10L) = BatchSendConfig(
            sendType = BatchSendType.MATERIAL_REMINDER,
            autoEnabled = false, cron = "0 0 8 * * ?",
            dailyCap = 60, roundSize = 30,
            perMailIntervalMs = 0, perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30, templateId = templateId
        )

        @org.junit.jupiter.api.BeforeEach
        fun setUpReminder() {
            Mockito.`when`(batchSendSettingService.getConfig(BatchSendType.MATERIAL_REMINDER))
                .thenReturn(reminderConfig())
        }

        @Test
        fun `runMaterialReminderBatch queries APPLICATION index not CANDIDATE (I-3)`() {
            Mockito.`when`(expertSearchService.countExperts(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList())
            )).thenReturn(0L)

            service.runMaterialReminderBatch(12345L, ExecutionMode.MANUAL, false)

            Mockito.verify(expertSearchService).countExperts(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList())
            )
            Mockito.verify(expertSearchService, Mockito.never()).countExperts(
                eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList())
            )
        }

        @Test
        fun `runMaterialReminderBatch excludes experts without existing MySQL contact (I-3)`() {
            val ep = expert("R001", "r1@test.com")
            Mockito.`when`(expertSearchService.countExperts(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList())
            )).thenReturn(1L)
            Mockito.`when`(expertSearchService.searchExpertsFiltered(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList()), eqValue(0), anyInt()
            )).thenReturn(listOf(ep))
            Mockito.`when`(expertContactRepository.findByOrcidIdIn(anyValue(emptyList())))
                .thenReturn(emptyList())

            val result = service.runMaterialReminderBatch(12345L, ExecutionMode.MANUAL, false)

            assertEquals(0, result.sent)
            Mockito.verifyNoInteractions(manualExpertMailService)
        }

        @Test
        fun `runMaterialReminderBatch skips contact with SENT MATERIAL_REMINDER (I-6)`() {
            val contactId = 5L
            val contact = ExpertContact(
                id = contactId, campaignId = 10L, orcidId = "R002", expertEmail = "r2@test.com",
                expertName = "R2", currentStatus = "WAITING_REPLY"
            )
            val ep = expert("R002", "r2@test.com")

            Mockito.`when`(expertSearchService.countExperts(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList())
            )).thenReturn(1L)
            Mockito.`when`(expertSearchService.searchExpertsFiltered(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList()), eqValue(0), anyInt()
            )).thenReturn(listOf(ep))
            Mockito.`when`(expertContactRepository.findByOrcidIdIn(anyValue(emptyList())))
                .thenReturn(listOf(contact))
            Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
                .thenReturn(listOf(
                    MailRecord(
                        expertContactId = contactId, direction = "OUTBOUND",
                        mailType = "MATERIAL_REMINDER", sendStatus = "SENT",
                        messageId = "msg", inReplyTo = null, subject = "s", body = "b",
                        matchedQaRuleId = null, receivedAt = null, sentAt = LocalDateTime.now()
                    )
                ))

            val result = service.runMaterialReminderBatch(12345L, ExecutionMode.MANUAL, false)

            assertEquals(0, result.sent)
            Mockito.verifyNoInteractions(manualExpertMailService)
        }

        @Test
        fun `runMaterialReminderBatch throws IllegalState when count exceeds 10000 before any send (I-6)`() {
            Mockito.`when`(expertSearchService.countExperts(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList())
            )).thenReturn(10001L)

            val thrown = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
                service.runMaterialReminderBatch(12345L, ExecutionMode.MANUAL, false)
            }
            assertTrue(thrown.message!!.contains("10001"))
            assertTrue(thrown.message!!.contains("10000"))
            Mockito.verifyNoInteractions(manualExpertMailService)
        }

        @Test
        fun `runMaterialReminderBatch does not modify tags or call txHelper after send (I-5)`() {
            val contactId = 6L
            val contact = ExpertContact(
                id = contactId, campaignId = 10L, orcidId = "R003", expertEmail = "r3@test.com",
                expertName = "R3", currentStatus = "WAITING_REPLY"
            )
            val ep = expert("R003", "r3@test.com")
            val acc = account("chen")

            Mockito.`when`(expertSearchService.countExperts(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList())
            )).thenReturn(1L)
            Mockito.`when`(expertSearchService.searchExpertsFiltered(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList()), eqValue(0), anyInt()
            )).thenReturn(listOf(ep))
            Mockito.`when`(expertContactRepository.findByOrcidIdIn(anyValue(emptyList())))
                .thenReturn(listOf(contact))
            Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
                .thenReturn(emptyList())
            Mockito.`when`(senderAccountAssignmentService.selectAccount(
                anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)
            )).thenReturn(acc)
            stubReminderResolveForSendNotBound()
            Mockito.`when`(manualExpertMailService.sendManualMail(
                eqValue(contactId),
                anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", ""))
            )).thenReturn(
                com.weibo.talentintroduction.mail.service.ManualMailSendResult(
                    contactId = contactId, senderAccountCode = "chen",
                    mailType = "MATERIAL_REMINDER", subject = "Subj",
                    sendStatus = "SENT", messageId = "msg1"
                )
            )

            val result = service.runMaterialReminderBatch(12345L, ExecutionMode.MANUAL, false)

            assertEquals(1, result.sent)
            // I-5: no tag/index modifications
            Mockito.verify(expertIndexWriterService, Mockito.never())
                .syncOperatorStatus(Mockito.anyString(), Mockito.anyString())
            // Does not use txHelper (no contact creation/status change for reminder)
            Mockito.verify(txHelper, Mockito.never()).recordSuccess(
                anyValue(ExpertContact(campaignId = 0, orcidId = "", expertEmail = "", expertName = null)),
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyLong()
            )
        }

        @Test
        fun `runMaterialReminderBatch sends via COMPOSE_TEMPLATE with configured templateId (I-10)`() {
            val contactId = 7L
            val contact = ExpertContact(
                id = contactId, campaignId = 10L, orcidId = "R004", expertEmail = "r4@test.com",
                expertName = "R4", currentStatus = "WAITING_REPLY"
            )
            val ep = expert("R004", "r4@test.com")
            val acc = account("chen")

            Mockito.`when`(expertSearchService.countExperts(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList())
            )).thenReturn(1L)
            Mockito.`when`(expertSearchService.searchExpertsFiltered(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList()), eqValue(0), anyInt()
            )).thenReturn(listOf(ep))
            Mockito.`when`(expertContactRepository.findByOrcidIdIn(anyValue(emptyList())))
                .thenReturn(listOf(contact))
            Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
                .thenReturn(emptyList())
            Mockito.`when`(senderAccountAssignmentService.selectAccount(
                anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)
            )).thenReturn(acc)
            stubReminderResolveForSendNotBound()

            val cmdCaptor = org.mockito.ArgumentCaptor.forClass(
                com.weibo.talentintroduction.mail.service.ManualMailSendCommand::class.java
            )
            Mockito.`when`(manualExpertMailService.sendManualMail(
                eqValue(contactId),
                anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", ""))
            )).thenReturn(
                com.weibo.talentintroduction.mail.service.ManualMailSendResult(
                    contactId = contactId, senderAccountCode = "chen",
                    mailType = "MATERIAL_REMINDER", subject = "Subj",
                    sendStatus = "SENT", messageId = "msg1"
                )
            )

            service.runMaterialReminderBatch(12345L, ExecutionMode.MANUAL, false)

            Mockito.verify(manualExpertMailService).sendManualMail(
                eqValue(contactId),
                captureValue(cmdCaptor, com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", ""))
            )
            assertEquals("COMPOSE_TEMPLATE", cmdCaptor.value.optionType)
            assertEquals("10", cmdCaptor.value.optionValue)  // configured templateId=10
        }

        @Test
        fun `runMaterialReminderBatch with oneRoundOnly returns PAUSED and ONE_ROUND_DONE (I-9)`() {
            val contactId = 8L
            val contact = ExpertContact(
                id = contactId, campaignId = 10L, orcidId = "R005", expertEmail = "r5@test.com",
                expertName = "R5", currentStatus = "WAITING_REPLY"
            )
            val ep = expert("R005", "r5@test.com")
            val acc = account("chen")

            Mockito.`when`(expertSearchService.countExperts(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList())
            )).thenReturn(1L)
            Mockito.`when`(expertSearchService.searchExpertsFiltered(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList()), eqValue(0), anyInt()
            )).thenReturn(listOf(ep))
            Mockito.`when`(expertContactRepository.findByOrcidIdIn(anyValue(emptyList())))
                .thenReturn(listOf(contact))
            Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
                .thenReturn(emptyList())
            Mockito.`when`(senderAccountAssignmentService.selectAccount(
                anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)
            )).thenReturn(acc)
            stubReminderResolveForSendNotBound()
            Mockito.`when`(manualExpertMailService.sendManualMail(
                eqValue(contactId),
                anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", ""))
            )).thenReturn(
                com.weibo.talentintroduction.mail.service.ManualMailSendResult(
                    contactId = contactId, senderAccountCode = "chen",
                    mailType = "MATERIAL_REMINDER", subject = "Subj",
                    sendStatus = "SENT", messageId = "msg-one"
                )
            )

            val result = service.runMaterialReminderBatch(12345L, ExecutionMode.MANUAL, oneRoundOnly = true)

            assertEquals(1, result.sent)
            assertEquals("PAUSED", result.finalStatus)
            assertEquals("ONE_ROUND_DONE", result.stopReason)
        }

        @Test
        fun `runMaterialReminderBatch ignores dailyCap and sends all round targets (I-1)`() {
            val targets = (1..5).map { i ->
                val contactId = (100 + i).toLong()
                val orcid = "R10$i"
                val email = "r$i@test.com"
                val contact = ExpertContact(
                    id = contactId, campaignId = 10L, orcidId = orcid, expertEmail = email,
                    expertName = "R$i", currentStatus = "WAITING_REPLY"
                )
                val ep = expert(orcid, email)
                Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
                    .thenReturn(emptyList())
                Pair(contact, ep)
            }

            val capConfig = reminderConfig(templateId = 10L).copy(dailyCap = 2, roundSize = 10)
            Mockito.`when`(batchSendSettingService.getConfig(BatchSendType.MATERIAL_REMINDER))
                .thenReturn(capConfig)
            Mockito.`when`(expertSearchService.countExperts(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList())
            )).thenReturn(5L)
            Mockito.`when`(expertSearchService.searchExpertsFiltered(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList()), eqValue(0), anyInt()
            )).thenReturn(targets.map { it.second })
            Mockito.`when`(expertContactRepository.findByOrcidIdIn(anyValue(emptyList())))
                .thenReturn(targets.map { it.first })

            val acc = account("chen")
            Mockito.`when`(senderAccountAssignmentService.selectAccount(
                anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)
            )).thenReturn(acc)
            stubReminderResolveForSendNotBound()
            Mockito.`when`(manualExpertMailService.sendManualMail(
                anyLong(),
                anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", ""))
            )).thenAnswer { invocation ->
                val cid = invocation.getArgument<Long>(0)
                com.weibo.talentintroduction.mail.service.ManualMailSendResult(
                    contactId = cid, senderAccountCode = "chen",
                    mailType = "MATERIAL_REMINDER", subject = "Subj",
                    sendStatus = "SENT", messageId = "msg-$cid"
                )
            }

            val result = service.runMaterialReminderBatch(12345L, ExecutionMode.MANUAL, false)

            // I-1: dailyCap=2 no longer truncates; all 5 targets sent in the single round (roundSize=10)
            assertEquals(5, result.sent)
            assertEquals("COMPLETED", result.finalStatus)
            assertNotEquals("DAILY_CAP_REACHED", result.stopReason)
        }

        @Test
        fun `material reminder roundsPerRun bounds a single execution at rounds times round size`() {
            val targets = (1..100).map { i ->
                val contactId = (300 + i).toLong()
                val orcid = "M$i".padStart(4, '0')
                val email = "m$i@test.com"
                val contact = ExpertContact(
                    id = contactId, campaignId = 10L, orcidId = orcid, expertEmail = email,
                    expertName = "M$i", currentStatus = "WAITING_REPLY"
                )
                val ep = expert(orcid, email)
                Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
                    .thenReturn(emptyList())
                Pair(contact, ep)
            }

            Mockito.`when`(expertSearchService.countExperts(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList())
            )).thenReturn(100L)
            Mockito.`when`(expertSearchService.searchExpertsFiltered(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList()), eqValue(0), anyInt()
            )).thenReturn(targets.map { it.second })
            Mockito.`when`(expertContactRepository.findByOrcidIdIn(anyValue(emptyList())))
                .thenReturn(targets.map { it.first })

            val acc = account("chen")
            Mockito.`when`(senderAccountAssignmentService.selectAccount(
                anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)
            )).thenReturn(acc)
            stubReminderResolveForSendNotBound()
            Mockito.`when`(manualExpertMailService.sendManualMail(
                anyLong(),
                anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", ""))
            )).thenAnswer { invocation ->
                val cid = invocation.getArgument<Long>(0)
                com.weibo.talentintroduction.mail.service.ManualMailSendResult(
                    contactId = cid, senderAccountCode = "chen",
                    mailType = "MATERIAL_REMINDER", subject = "Subj",
                    sendStatus = "SENT", messageId = "msg-$cid"
                )
            }

            val snapshot = com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot(
                mailType = "MATERIAL_REMINDER",
                roundSize = 20,
                roundsPerRun = 2,
                perMailIntervalMs = 0,
                perRoundIntervalMs = 0,
                selfCheckTtlMinutes = 30,
                templateId = 10L
            )
            val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

            // B-2 symmetry: material loop honors the same per-run round budget
            assertEquals(40, result.sent)
            assertEquals("ROUNDS_PER_RUN_REACHED", result.stopReason)
            assertEquals("COMPLETED", result.finalStatus)
        }

        @Test
        fun `runMaterialReminderBatch no longer seeds from persisted SENT count (I-3)`() {
            val targets = (1..5).map { i ->
                val contactId = (200 + i).toLong()
                val orcid = "D10$i"
                val email = "d$i@test.com"
                val contact = ExpertContact(
                    id = contactId, campaignId = 10L, orcidId = orcid, expertEmail = email,
                    expertName = "D$i", currentStatus = "WAITING_REPLY"
                )
                Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
                    .thenReturn(emptyList())
                Pair(contact, expert(orcid, email))
            }

            val capConfig = reminderConfig(templateId = 10L).copy(dailyCap = 3, roundSize = 10)
            Mockito.`when`(batchSendSettingService.getConfig(BatchSendType.MATERIAL_REMINDER))
                .thenReturn(capConfig)
            // I-3: persisted SENT count is no longer queried; it must not reduce the sendable targets
            Mockito.`when`(expertSearchService.countExperts(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList())
            )).thenReturn(5L)
            Mockito.`when`(expertSearchService.searchExpertsFiltered(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList()), eqValue(0), anyInt()
            )).thenReturn(targets.map { it.second })
            Mockito.`when`(expertContactRepository.findByOrcidIdIn(anyValue(emptyList())))
                .thenReturn(targets.map { it.first })

            val acc = account("chen")
            Mockito.`when`(senderAccountAssignmentService.selectAccount(
                anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)
            )).thenReturn(acc)
            stubReminderResolveForSendNotBound()
            Mockito.`when`(manualExpertMailService.sendManualMail(
                anyLong(),
                anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", ""))
            )).thenAnswer { invocation ->
                val cid = invocation.getArgument<Long>(0)
                com.weibo.talentintroduction.mail.service.ManualMailSendResult(
                    contactId = cid, senderAccountCode = "chen",
                    mailType = "MATERIAL_REMINDER", subject = "Subj",
                    sendStatus = "SENT", messageId = "msg-$cid"
                )
            }

            val result = service.runMaterialReminderBatch(12345L, ExecutionMode.MANUAL, false)

            // I-3: with the persisted-SENT seed removed, all 5 targets are sent in the single round
            assertEquals(5, result.sent)
            assertEquals("COMPLETED", result.finalStatus)
            assertNotEquals("DAILY_CAP_REACHED", result.stopReason)
        }

        @Test
        fun `runMaterialReminderBatch sends all targets regardless of FAILED history (I-3)`() {
            val targets = (1..3).map { i ->
                val contactId = (300 + i).toLong()
                val orcid = "F10$i"
                val email = "f$i@test.com"
                val contact = ExpertContact(
                    id = contactId, campaignId = 10L, orcidId = orcid, expertEmail = email,
                    expertName = "F$i", currentStatus = "WAITING_REPLY"
                )
                Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
                    .thenReturn(emptyList())
                Pair(contact, expert(orcid, email))
            }

            val capConfig = reminderConfig(templateId = 10L).copy(dailyCap = 2, roundSize = 10)
            Mockito.`when`(batchSendSettingService.getConfig(BatchSendType.MATERIAL_REMINDER))
                .thenReturn(capConfig)
            // I-3: no persisted-SENT seed at all; sendable targets are bounded by round size / account capacity only
            Mockito.`when`(expertSearchService.countExperts(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList())
            )).thenReturn(3L)
            Mockito.`when`(expertSearchService.searchExpertsFiltered(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList()), eqValue(0), anyInt()
            )).thenReturn(targets.map { it.second })
            Mockito.`when`(expertContactRepository.findByOrcidIdIn(anyValue(emptyList())))
                .thenReturn(targets.map { it.first })

            val acc = account("chen")
            Mockito.`when`(senderAccountAssignmentService.selectAccount(
                anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)
            )).thenReturn(acc)
            stubReminderResolveForSendNotBound()
            Mockito.`when`(manualExpertMailService.sendManualMail(
                anyLong(),
                anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", ""))
            )).thenAnswer { invocation ->
                val cid = invocation.getArgument<Long>(0)
                com.weibo.talentintroduction.mail.service.ManualMailSendResult(
                    contactId = cid, senderAccountCode = "chen",
                    mailType = "MATERIAL_REMINDER", subject = "Subj",
                    sendStatus = "SENT", messageId = "msg-$cid"
                )
            }

            val result = service.runMaterialReminderBatch(12345L, ExecutionMode.MANUAL, false)

            // I-3/I-1: all 3 targets sent in the single round; dailyCap=2 never read
            assertEquals(3, result.sent)
            assertEquals("COMPLETED", result.finalStatus)
            assertNotEquals("DAILY_CAP_REACHED", result.stopReason)
        }

        @Test
        fun `runMaterialReminderBatch second invocation sends again without dailyCap gate (I-1)`() {
            fun stubTargets(prefix: String, baseId: Long, count: Int) =
                (1..count).map { i ->
                    val contactId = baseId + i
                    val orcid = "$prefix$i"
                    val email = "$prefix$i@test.com"
                    val contact = ExpertContact(
                        id = contactId, campaignId = 10L, orcidId = orcid, expertEmail = email,
                        expertName = "$prefix$i", currentStatus = "WAITING_REPLY"
                    )
                    Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId))
                        .thenReturn(emptyList())
                    Pair(contact, expert(orcid, email))
                }

            val capConfig = reminderConfig(templateId = 10L).copy(dailyCap = 2, roundSize = 10)
            Mockito.`when`(batchSendSettingService.getConfig(BatchSendType.MATERIAL_REMINDER))
                .thenReturn(capConfig)

            val firstTargets = stubTargets("A", 400, 3)
            Mockito.`when`(expertSearchService.countExperts(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList())
            )).thenReturn(3L)
            Mockito.`when`(expertSearchService.searchExpertsFiltered(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList()), eqValue(0), anyInt()
            )).thenReturn(firstTargets.map { it.second })
            Mockito.`when`(expertContactRepository.findByOrcidIdIn(anyValue(emptyList())))
                .thenReturn(firstTargets.map { it.first })
            val acc = account("chen")
            Mockito.`when`(senderAccountAssignmentService.selectAccount(
                anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)
            )).thenReturn(acc)
            stubReminderResolveForSendNotBound()
            Mockito.`when`(manualExpertMailService.sendManualMail(
                anyLong(),
                anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", ""))
            )).thenAnswer { invocation ->
                val cid = invocation.getArgument<Long>(0)
                com.weibo.talentintroduction.mail.service.ManualMailSendResult(
                    contactId = cid, senderAccountCode = "chen",
                    mailType = "MATERIAL_REMINDER", subject = "Subj",
                    sendStatus = "SENT", messageId = "msg-$cid"
                )
            }

            val first = service.runMaterialReminderBatch(1L, ExecutionMode.MANUAL, false)
            assertEquals(3, first.sent)

            // I-1: second invocation is not gated by prior sends; all targets sendable again
            val secondTargets = stubTargets("B", 500, 3)
            Mockito.`when`(expertSearchService.countExperts(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList())
            )).thenReturn(3L)
            Mockito.`when`(expertSearchService.searchExpertsFiltered(
                eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList()), eqValue(0), anyInt()
            )).thenReturn(secondTargets.map { it.second })
            Mockito.`when`(expertContactRepository.findByOrcidIdIn(anyValue(emptyList())))
                .thenReturn(secondTargets.map { it.first })

            val second = service.runMaterialReminderBatch(2L, ExecutionMode.MANUAL, false)

            // I-1: same-day re-invocation sends again; only account capacity / roundsPerRun bound the run
            assertEquals(3, second.sent)
            assertEquals("COMPLETED", second.finalStatus)
            assertNotEquals("DAILY_CAP_REACHED", second.stopReason)
        }
    }

    // ──── KV Isolation Tests (I-2) ────

    @org.junit.jupiter.api.Nested
    inner class KvIsolationTests {
        private val kvRepo = Mockito.mock(com.weibo.talentintroduction.campaign.repository.BatchSendSettingRepository::class.java)
        private val kvPub = Mockito.mock(org.springframework.context.ApplicationEventPublisher::class.java)
        private val kvService = BatchSendSettingService(kvRepo, kvPub)

        private fun row(key: String, value: String) =
            com.weibo.talentintroduction.campaign.domain.BatchSendSetting(
                id = null, settingKey = key, settingValue = value, updatedAt = LocalDateTime.now()
            )

        @Test
        fun `INTRODUCTION uses batchSend dot prefix and REMINDER uses batchSend dot materialReminder dot prefix (I-2)`() {
            Mockito.`when`(kvRepo.findAll()).thenReturn(listOf(
                row("batchSend.dailyCap", "111"),
                row("batchSend.materialReminder.dailyCap", "222")
            ))

            val introCfg = kvService.getConfig(BatchSendType.INTRODUCTION)
            val reminderCfg = kvService.getConfig(BatchSendType.MATERIAL_REMINDER)

            assertEquals(111, introCfg.dailyCap)
            assertEquals(222, reminderCfg.dailyCap)
        }

        @Test
        fun `no-arg getConfig() returns same as getConfig(INTRODUCTION) for compat (I-2)`() {
            Mockito.`when`(kvRepo.findAll()).thenReturn(listOf(
                row("batchSend.dailyCap", "777")
            ))

            val compat = kvService.getConfig()
            val typed = kvService.getConfig(BatchSendType.INTRODUCTION)

            assertEquals(compat.dailyCap, typed.dailyCap)
            assertEquals(777, compat.dailyCap)
            assertEquals(BatchSendType.INTRODUCTION, typed.sendType)
        }

        @Test
        fun `REMINDER defaults differ from INTRODUCTION defaults (I-2)`() {
            Mockito.`when`(kvRepo.findAll()).thenReturn(emptyList())

            val intro = kvService.getConfig(BatchSendType.INTRODUCTION)
            val reminder = kvService.getConfig(BatchSendType.MATERIAL_REMINDER)

            assertNotEquals(intro.dailyCap, reminder.dailyCap)
            assertNotEquals(intro.cron, reminder.cron)
            assertEquals(1000, intro.dailyCap)
            assertEquals(60, reminder.dailyCap)
        }

        @Test
        fun `updating INTRODUCTION config does not affect REMINDER config (I-2 isolation)`() {
            // DB has both configs set
            Mockito.`when`(kvRepo.findAll()).thenReturn(listOf(
                row("batchSend.dailyCap", "500"),
                row("batchSend.materialReminder.dailyCap", "40")
            ))
            Mockito.`when`(kvRepo.save(Mockito.any())).thenAnswer { it.arguments[0] }

            kvService.updateConfig(
                BatchSendConfigUpdateRequest(
                    autoEnabled = false, cron = "0 0 0 * * ?", dailyCap = 999, roundSize = 50,
                    perMailIntervalMs = 1000, perRoundIntervalMs = 60000, selfCheckTtlMinutes = 30
                ),
                BatchSendType.INTRODUCTION
            )

            // REMINDER row unchanged (still "40")
            val reminderCfg = kvService.getConfig(BatchSendType.MATERIAL_REMINDER)
            assertEquals(40, reminderCfg.dailyCap)
        }

        @Test
        fun `MATERIAL_REMINDER config requires templateId on updateConfig (I-7)`() {
            Mockito.`when`(kvRepo.findAll()).thenReturn(emptyList())
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
                kvService.updateConfig(
                    BatchSendConfigUpdateRequest(
                        autoEnabled = true, cron = "0 0 8 * * ?", dailyCap = 60, roundSize = 30,
                        perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30
                        // templateId intentionally null → should fail for MATERIAL_REMINDER
                    ),
                    BatchSendType.MATERIAL_REMINDER
                )
            }
        }
    }

    // ──── Template Gate Tests (I-7) ────

    @org.junit.jupiter.api.Nested
    inner class TemplateGateTests {
        private val ctrlProgressStore = Mockito.mock(com.weibo.talentintroduction.task.service.TaskProgressStore::class.java)
        private val ctrlTaskExecService = Mockito.mock(com.weibo.talentintroduction.task.service.TaskExecutionService::class.java)
        private val ctrlOutreachService = Mockito.mock(ManualInitialOutreachService::class.java)
        private val ctrlSettingService = Mockito.mock(BatchSendSettingService::class.java)
        private val ctrlMailAccountService = Mockito.mock(com.weibo.talentintroduction.mail.service.MailSenderAccountService::class.java)
        private val ctrlTemplateService = Mockito.mock(com.weibo.talentintroduction.template.service.MailComposeTemplateService::class.java)
        private val ctrlBatchConfigRepository = Mockito.mock(com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository::class.java)
        private val ctrlObjectMapper = com.fasterxml.jackson.databind.ObjectMapper().registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
        private val ctrlExecutor = Mockito.mock(java.util.concurrent.Executor::class.java)

        private val ctrl = BatchSendControlService(
            progressStore = ctrlProgressStore,
            taskExecutionService = ctrlTaskExecService,
            manualInitialOutreachService = ctrlOutreachService,
            batchSendSettingService = ctrlSettingService,
            batchSendTaskConfigRepository = ctrlBatchConfigRepository,
            mailSenderAccountService = ctrlMailAccountService,
            mailComposeTemplateService = ctrlTemplateService,
            objectMapper = ctrlObjectMapper,
            manualOutreachExecutor = ctrlExecutor
        )

        private fun reminderConfig(templateId: Long? = 10L) = BatchSendConfig(
            sendType = BatchSendType.MATERIAL_REMINDER,
            autoEnabled = true, cron = "0 0 8 * * ?",
            dailyCap = 60, roundSize = 30, perMailIntervalMs = 0, perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30, templateId = templateId
        )

        @org.junit.jupiter.api.BeforeEach
        fun setUpCtrl() {
            Mockito.`when`(ctrlSettingService.getRuntimeStatus()).thenReturn(
                BatchSendRuntimeState("IDLE", "NONE", "")
            )
            Mockito.`when`(ctrlSettingService.getRuntimeStatus(BatchSendType.MATERIAL_REMINDER)).thenReturn(
                BatchSendRuntimeState("IDLE", "NONE", "")
            )
            Mockito.`when`(ctrlSettingService.getConfig()).thenReturn(
                BatchSendConfig(
                    autoEnabled = true, cron = "0 0 0 * * ?", dailyCap = 1000, roundSize = 50,
                    perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30
                )
            )
            Mockito.`when`(ctrlMailAccountService.remainingDailyCapacity(Mockito.anyBoolean())).thenReturn(10)
            Mockito.`when`(ctrlMailAccountService.warmupActiveCount()).thenReturn(0)
            Mockito.`when`(ctrlMailAccountService.todayTotalCapacity()).thenReturn(100)
            // Default: tryStartWithToken succeeds
            Mockito.doReturn(Pair(true, -1L))
                .`when`(ctrlProgressStore).tryStartWithToken(
                    Mockito.anyString(),
                    anyValue(
                        com.weibo.talentintroduction.task.service.TaskProgress(
                            taskType = "MANUAL_INITIAL_OUTREACH",
                            status = "RUNNING",
                            batchNumber = 0,
                            processedCount = 0,
                            totalCount = 0
                        )
                    )
                )
            // Synchronous executor for determinism
            Mockito.doAnswer { it.getArgument<Runnable>(0).run() }
                .`when`(ctrlExecutor).execute(Mockito.any(Runnable::class.java))
        }

        @Test
        fun `MATERIAL_REMINDER start blocked when templateId is null (I-7)`() {
            Mockito.`when`(ctrlSettingService.getConfig(BatchSendType.MATERIAL_REMINDER))
                .thenReturn(reminderConfig(templateId = null))

            val response = ctrl.startManual(BatchSendType.MATERIAL_REMINDER)

            assertEquals(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
            assertTrue(response.body?.get("message")!!.contains("模板"))
            Mockito.verify(ctrlExecutor, Mockito.never()).execute(Mockito.any())
        }

        @Test
        fun `MATERIAL_REMINDER start blocked when template is disabled (I-7)`() {
            Mockito.`when`(ctrlSettingService.getConfig(BatchSendType.MATERIAL_REMINDER))
                .thenReturn(reminderConfig(templateId = 42L))
            Mockito.`when`(ctrlTemplateService.getById(42L)).thenReturn(
                com.weibo.talentintroduction.template.service.MailComposeTemplateDetail(
                    id = 42L, templateCode = "MATERIAL_REMINDER", templateName = "Test",
                    subject = "S", description = null, mailType = "MATERIAL_REMINDER",
                    subjectVariants = null, enabled = false, blocks = emptyList(),
                    createdAt = null, updatedAt = null
                )
            )

            val response = ctrl.startManual(BatchSendType.MATERIAL_REMINDER)

            assertEquals(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
            assertTrue(response.body?.get("message")!!.contains("禁用"))
            Mockito.verify(ctrlExecutor, Mockito.never()).execute(Mockito.any())
        }

        @Test
        fun `MATERIAL_REMINDER start blocked when template mailType does not match (I-7)`() {
            Mockito.`when`(ctrlSettingService.getConfig(BatchSendType.MATERIAL_REMINDER))
                .thenReturn(reminderConfig(templateId = 99L))
            Mockito.`when`(ctrlTemplateService.getById(99L)).thenReturn(
                com.weibo.talentintroduction.template.service.MailComposeTemplateDetail(
                    id = 99L, templateCode = "INTRODUCTION", templateName = "Intro",
                    subject = "S", description = null, mailType = "INTRODUCTION",
                    subjectVariants = null, enabled = true, blocks = emptyList(),
                    createdAt = null, updatedAt = null
                )
            )

            val response = ctrl.startManual(BatchSendType.MATERIAL_REMINDER)

            assertEquals(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
            assertTrue(response.body?.get("message")!!.contains("类型"))
            Mockito.verify(ctrlExecutor, Mockito.never()).execute(Mockito.any())
        }

        @Test
        fun `INTRODUCTION start is not blocked even without templateId (I-7)`() {
            Mockito.`when`(ctrlSettingService.getConfig()).thenReturn(
                BatchSendConfig(
                    autoEnabled = true, cron = "0 0 0 * * ?", dailyCap = 1000, roundSize = 50,
                    perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
                    templateId = null
                )
            )
            // Synchronous execution returns an outreach result
            Mockito.`when`(ctrlTaskExecService.runAndRecordWithResult<ManualOutreachResult>(
                Mockito.anyString(),
                Mockito.anyString(),
                anyValue(""),
                anyValue({ _: Long -> }),
                Mockito.isNull(),
                anyValue({ ManualOutreachResult(0, 0, 0, 0, false, "COMPLETED") })
            )).thenAnswer { invocation ->
                val onStarted = invocation.getArgument<((Long) -> Unit)?>(3)
                onStarted?.invoke(99L)
                val block = invocation.getArgument<() -> ManualOutreachResult>(5)
                Mockito.`when`(ctrlOutreachService.runScheduledBatch(99L, ExecutionMode.MANUAL, false))
                    .thenReturn(ManualOutreachResult(0, 0, 0, 0, false, "COMPLETED"))
                val result = block()
                Pair(
                    com.weibo.talentintroduction.task.domain.TaskExecution(
                        id = 99L, taskType = "T", triggerType = "MANUAL", status = "SUCCESS",
                        requestPayload = "", resultSummary = null,
                        startedAt = LocalDateTime.now(), finishedAt = LocalDateTime.now()
                    ),
                    result
                )
            }

            val response = ctrl.startManual()

            assertEquals(org.springframework.http.HttpStatus.ACCEPTED, response.statusCode)
        }

        @Test
        fun `INTRODUCTION start blocked when explicit template mailType does not match (I-7)`() {
            Mockito.`when`(ctrlSettingService.getConfig()).thenReturn(
                BatchSendConfig(
                    autoEnabled = true, cron = "0 0 0 * * ?", dailyCap = 1000, roundSize = 50,
                    perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
                    templateId = 77L
                )
            )
            Mockito.`when`(ctrlTemplateService.getById(77L)).thenReturn(
                com.weibo.talentintroduction.template.service.MailComposeTemplateDetail(
                    id = 77L, templateCode = "MATERIAL_REMINDER", templateName = "Reminder",
                    subject = "S", description = null, mailType = "MATERIAL_REMINDER",
                    subjectVariants = null, enabled = true, blocks = emptyList(),
                    createdAt = null, updatedAt = null
                )
            )

            val response = ctrl.startManual()

            assertEquals(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
            assertTrue(response.body?.get("message")!!.contains("类型"))
            Mockito.verify(ctrlExecutor, Mockito.never()).execute(Mockito.any())
        }

        @Test
        fun `INTRODUCTION start blocked when explicit template is disabled (I-7)`() {
            Mockito.`when`(ctrlSettingService.getConfig()).thenReturn(
                BatchSendConfig(
                    autoEnabled = true, cron = "0 0 0 * * ?", dailyCap = 1000, roundSize = 50,
                    perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
                    templateId = 88L
                )
            )
            Mockito.`when`(ctrlTemplateService.getById(88L)).thenReturn(
                com.weibo.talentintroduction.template.service.MailComposeTemplateDetail(
                    id = 88L, templateCode = "INTRODUCTION", templateName = "Intro",
                    subject = "S", description = null, mailType = "INTRODUCTION",
                    subjectVariants = null, enabled = false, blocks = emptyList(),
                    createdAt = null, updatedAt = null
                )
            )

            val response = ctrl.startManual()

            assertEquals(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
            assertTrue(response.body?.get("message")!!.contains("禁用"))
            Mockito.verify(ctrlExecutor, Mockito.never()).execute(Mockito.any())
        }
    }

    // ──── Dual Scheduler Tests (I-8) ────

    @org.junit.jupiter.api.Nested
    inner class DualSchedulerTests {
        private val schedConfigRepository = Mockito.mock(com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository::class.java)
        private val schedControlService = Mockito.mock(BatchSendControlService::class.java)
        private val schedTaskScheduler = Mockito.mock(org.springframework.scheduling.TaskScheduler::class.java)
        private val schedFuture = Mockito.mock(java.util.concurrent.ScheduledFuture::class.java)

        private fun enabledConfig(id: Long, mailType: String = "INTRODUCTION") =
            com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfig(
                id = id,
                configName = "cfg-$id",
                mailType = mailType,
                autoEnabled = true,
                cron = if (mailType == "INTRODUCTION") "0 0 0 * * ?" else "0 0 8 * * ?",
                roundSize = 50,
                perMailIntervalMs = 0,
                perRoundIntervalMs = 0,
                selfCheckTtlMinutes = 30,
                templateId = if (mailType == "MATERIAL_REMINDER") 10L else null
            )

        @org.junit.jupiter.api.BeforeEach
        fun setUpSched() {
            Mockito.`when`(schedTaskScheduler.schedule(
                Mockito.any(Runnable::class.java),
                Mockito.any(org.springframework.scheduling.Trigger::class.java)
            )).thenReturn(schedFuture)
            Mockito.`when`(schedConfigRepository.findAllByAutoEnabledTrueAndDeletedAtIsNullOrderByIdAsc())
                .thenReturn(listOf(enabledConfig(1L), enabledConfig(2L, "MATERIAL_REMINDER")))
        }

        @Test
        fun `scheduleInitial registers futures per enabled config`() {
            val scheduler = com.weibo.talentintroduction.task.service.BatchSendScheduler(
                schedConfigRepository, schedControlService, schedTaskScheduler
            )
            scheduler.scheduleInitial()

            Mockito.verify(schedTaskScheduler, Mockito.times(2)).schedule(
                Mockito.any(Runnable::class.java),
                Mockito.any(org.springframework.scheduling.Trigger::class.java)
            )
        }

        @Test
        fun `scheduleInitial registers only one future when one config disabled`() {
            Mockito.`when`(schedConfigRepository.findAllByAutoEnabledTrueAndDeletedAtIsNullOrderByIdAsc())
                .thenReturn(listOf(enabledConfig(1L)))

            val scheduler = com.weibo.talentintroduction.task.service.BatchSendScheduler(
                schedConfigRepository, schedControlService, schedTaskScheduler
            )
            scheduler.scheduleInitial()

            Mockito.verify(schedTaskScheduler, Mockito.times(1)).schedule(
                Mockito.any(Runnable::class.java),
                Mockito.any(org.springframework.scheduling.Trigger::class.java)
            )
        }

        @Test
        fun `unchanged config keeps its schedule and cancels removed config`() {
            val scheduler = com.weibo.talentintroduction.task.service.BatchSendScheduler(
                schedConfigRepository, schedControlService, schedTaskScheduler
            )
            scheduler.scheduleInitial()

            Mockito.verify(schedTaskScheduler, Mockito.times(2)).schedule(
                Mockito.any(Runnable::class.java),
                Mockito.any(org.springframework.scheduling.Trigger::class.java)
            )

            Mockito.`when`(schedConfigRepository.findAllByAutoEnabledTrueAndDeletedAtIsNullOrderByIdAsc())
                .thenReturn(listOf(enabledConfig(1L)))

            scheduler.onCronChanged(
                com.weibo.talentintroduction.campaign.event.BatchSendCronChangedEvent("0 0 8 * * ?", "0 0 8 * * ?")
            )

            Mockito.verify(schedTaskScheduler, Mockito.times(2)).schedule(
                Mockito.any(Runnable::class.java),
                Mockito.any(org.springframework.scheduling.Trigger::class.java)
            )
            Mockito.verify(schedFuture, Mockito.times(1)).cancel(false)
        }

        @Test
        fun `changed cron cancels and reschedules its config`() {
            val scheduler = com.weibo.talentintroduction.task.service.BatchSendScheduler(
                schedConfigRepository, schedControlService, schedTaskScheduler
            )
            scheduler.scheduleInitial()

            Mockito.`when`(schedConfigRepository.findAllByAutoEnabledTrueAndDeletedAtIsNullOrderByIdAsc())
                .thenReturn(listOf(enabledConfig(1L).copy(cron = "0 0 9 * * ?")))

            scheduler.onCronChanged(
                com.weibo.talentintroduction.campaign.event.BatchSendCronChangedEvent("0 0 0 * * ?", "0 0 9 * * ?")
            )

            Mockito.verify(schedTaskScheduler, Mockito.times(3)).schedule(
                Mockito.any(Runnable::class.java),
                Mockito.any(org.springframework.scheduling.Trigger::class.java)
            )
            Mockito.verify(schedFuture, Mockito.times(2)).cancel(false)
        }
    }

    // ──── Shared Progress Mutex Tests (I-8) ────

    @org.junit.jupiter.api.Nested
    inner class SharedMutexTests {
        private val mutexProgressStore = Mockito.mock(com.weibo.talentintroduction.task.service.TaskProgressStore::class.java)
        private val mutexTaskExecService = Mockito.mock(com.weibo.talentintroduction.task.service.TaskExecutionService::class.java)
        private val mutexOutreachService = Mockito.mock(ManualInitialOutreachService::class.java)
        private val mutexSettingService = Mockito.mock(BatchSendSettingService::class.java)
        private val mutexMailAccountService = Mockito.mock(com.weibo.talentintroduction.mail.service.MailSenderAccountService::class.java)
        private val mutexTemplateService = Mockito.mock(com.weibo.talentintroduction.template.service.MailComposeTemplateService::class.java)
        private val mutexBatchConfigRepository = Mockito.mock(com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository::class.java)
        private val mutexObjectMapper = com.fasterxml.jackson.databind.ObjectMapper().registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
        private val mutexExecutor = Mockito.mock(java.util.concurrent.Executor::class.java)

        private val mutexCtrl = BatchSendControlService(
            progressStore = mutexProgressStore,
            taskExecutionService = mutexTaskExecService,
            manualInitialOutreachService = mutexOutreachService,
            batchSendSettingService = mutexSettingService,
            batchSendTaskConfigRepository = mutexBatchConfigRepository,
            mailSenderAccountService = mutexMailAccountService,
            mailComposeTemplateService = mutexTemplateService,
            objectMapper = mutexObjectMapper,
            manualOutreachExecutor = mutexExecutor
        )

        @org.junit.jupiter.api.BeforeEach
        fun setUpMutex() {
            Mockito.`when`(mutexSettingService.getRuntimeStatus()).thenReturn(
                BatchSendRuntimeState("IDLE", "NONE", "")
            )
            Mockito.`when`(mutexSettingService.getRuntimeStatus(BatchSendType.MATERIAL_REMINDER)).thenReturn(
                BatchSendRuntimeState("IDLE", "NONE", "")
            )
            Mockito.`when`(mutexSettingService.getConfig()).thenReturn(
                BatchSendConfig(
                    autoEnabled = true, cron = "0 0 0 * * ?", dailyCap = 100, roundSize = 10,
                    perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30
                )
            )
            Mockito.`when`(mutexSettingService.getConfig(BatchSendType.MATERIAL_REMINDER)).thenReturn(
                BatchSendConfig(
                    sendType = BatchSendType.MATERIAL_REMINDER,
                    autoEnabled = true, cron = "0 0 8 * * ?",
                    dailyCap = 60, roundSize = 30, perMailIntervalMs = 0, perRoundIntervalMs = 0,
                    selfCheckTtlMinutes = 30, templateId = 10L
                )
            )
            Mockito.`when`(mutexMailAccountService.remainingDailyCapacity(Mockito.anyBoolean())).thenReturn(10)
            Mockito.`when`(mutexMailAccountService.warmupActiveCount()).thenReturn(0)
            Mockito.`when`(mutexMailAccountService.todayTotalCapacity()).thenReturn(100)
            Mockito.`when`(mutexTemplateService.getById(10L)).thenReturn(
                com.weibo.talentintroduction.template.service.MailComposeTemplateDetail(
                    id = 10L, templateCode = "MATERIAL_REMINDER", templateName = "Reminder",
                    subject = "S", description = null, mailType = "MATERIAL_REMINDER",
                    subjectVariants = null, enabled = true, blocks = emptyList(),
                    createdAt = null, updatedAt = null
                )
            )
        }

        @Test
        fun `concurrent start of second type returns 409 when progress mutex is held (I-8)`() {
            // Simulate mutex held: tryStartWithToken returns false
            Mockito.doReturn(Pair(false, 0L))
                .`when`(mutexProgressStore).tryStartWithToken(
                    Mockito.anyString(),
                    anyValue(
                        com.weibo.talentintroduction.task.service.TaskProgress(
                            taskType = "MANUAL_INITIAL_OUTREACH",
                            status = "RUNNING",
                            batchNumber = 0,
                            processedCount = 0,
                            totalCount = 0
                        )
                    )
                )

            val response = mutexCtrl.startManual(BatchSendType.MATERIAL_REMINDER)

            assertEquals(org.springframework.http.HttpStatus.CONFLICT, response.statusCode)
            assertTrue(response.body?.get("message")!!.contains("执行中"))
            Mockito.verify(mutexExecutor, Mockito.never()).execute(Mockito.any())
        }

        @Test
        fun `getStatus returns activeSendType from progress details (I-8)`() {
            Mockito.`when`(mutexSettingService.getRuntimeStatus()).thenReturn(
                BatchSendRuntimeState("RUNNING", "AUTO", "")
            )
            val progress = com.weibo.talentintroduction.task.service.TaskProgress(
                taskType = BatchSendControlService.TASK_TYPE,
                status = "RUNNING",
                batchNumber = 1, processedCount = 3, totalCount = 10,
                details = mapOf(
                    "executionMode" to "AUTO",
                    "sendType" to "MATERIAL_REMINDER",
                    "accounts" to emptyList<AccountStatRow>()
                ),
                executionId = 99L
            )
            Mockito.`when`(mutexProgressStore.get(BatchSendControlService.TASK_TYPE)).thenReturn(progress)

            val status = mutexCtrl.getStatus()

            assertEquals("MATERIAL_REMINDER", status.activeSendType)
        }
    }

    @Test
    fun `run passes regions to ES filter on INTRODUCTION CANDIDATE branch (branch A)`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(emptyList())

        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            regions = listOf("Europe")
        )
        val expectedFilters = ExpertSearchService.notContactedWithEmailFilters().toMutableList()
        ExpertSearchService.regionsFilter(listOf("Europe"))?.let { expectedFilters.add(it) }
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(0L)

        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.total)
        Mockito.verify(expertSearchService).countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters))
    }

    @Test
    fun `run passes regions to ES filter on MATERIAL_REMINDER branch (branch B)`() {
        val snapshot = BatchExecutionSnapshot(
            mailType = "MATERIAL_REMINDER",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "APPLICATION",
            tags = listOf("承诺回复材料"),
            regions = listOf("Europe"),
            templateId = 42L
        )
        val expectedFilters = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
        expectedFilters.add(mapOf("terms" to mapOf("tags" to listOf("承诺回复材料"))))
        ExpertSearchService.regionsFilter(listOf("Europe"))?.let { expectedFilters.add(it) }
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.APPLICATION), eqValue(expectedFilters)))
            .thenReturn(0L)

        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.total)
        Mockito.verify(expertSearchService).countExperts(eqValue(ExpertIndexLevel.APPLICATION), eqValue(expectedFilters))
    }

    @Test
    fun `run builds must_not exists filter for UNCLASSIFIED on MATERIAL_REMINDER else branch (I-3)`() {
        val snapshot = BatchExecutionSnapshot(
            mailType = "MATERIAL_REMINDER",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "APPLICATION",
            tags = listOf("承诺回复材料"),
            discipline = "UNCLASSIFIED",
            templateId = 42L
        )
        val expectedFilters = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
        expectedFilters.add(
            mapOf("bool" to mapOf("must_not" to listOf(mapOf("exists" to mapOf("field" to "disciplineCategory")))))
        )
        expectedFilters.add(mapOf("terms" to mapOf("tags" to listOf("承诺回复材料"))))
        // I-3: the else branch must go through disciplineFilter — must_not exists, never a term.
        assertTrue(expectedFilters.any { (it["bool"] as? Map<*, *>)?.get("must_not") != null })
        assertTrue(expectedFilters.none { it.containsKey("term") })
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.APPLICATION), eqValue(expectedFilters)))
            .thenReturn(0L)

        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.total)
        Mockito.verify(expertSearchService).countExperts(eqValue(ExpertIndexLevel.APPLICATION), eqValue(expectedFilters))
    }

    @Test
    fun `run keeps must_not exists discipline filter on INTRODUCTION CANDIDATE branch (I-3)`() {
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(
            Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        )
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(emptyList())
        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            discipline = "UNCLASSIFIED"
        )
        val expectedFilters = ExpertSearchService.notContactedWithEmailFilters(null, "UNCLASSIFIED").toMutableList()
        // Regression: the INTRODUCTION+CANDIDATE branch already routed through disciplineFilter; must stay correct.
        assertTrue(expectedFilters.any { (it["bool"] as? Map<*, *>)?.get("must_not") != null })
        assertTrue(expectedFilters.none { it.containsKey("term") })
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(0L)

        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.total)
        Mockito.verify(expertSearchService).countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters))
    }

    @Test
    fun `countPending keeps retryable without disciplineCategory when discipline is UNCLASSIFIED (I-4)`() {
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig().copy(discipline = "UNCLASSIFIED"))
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(
            Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        )
        val unclassContact = ExpertContact(id = 1L, campaignId = 10L, orcidId = "UNC1", expertEmail = "u@x.com", expertName = "U", currentStatus = "NEW")
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(unclassContact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
        // disciplineCategory defaults to null in the test helper — the retry path must treat missing field as UNCLASSIFIED.
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("UNC1"))).thenReturn(
            listOf(expert("UNC1", "u@x.com"))
        )
        val expectedFilters = ExpertSearchService.notContactedWithEmailFilters(null, "UNCLASSIFIED")
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(0L)

        val summary = service.countPending()
        assertEquals(0, summary.pending)
        assertEquals(1, summary.retryable)
        assertEquals(1, summary.totalSendable)
    }

    @Test
    fun `countPending filters retryable with STEM disciplineCategory when discipline is UNCLASSIFIED (I-4)`() {
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig().copy(discipline = "UNCLASSIFIED"))
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(
            Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        )
        val stemContact = ExpertContact(id = 1L, campaignId = 10L, orcidId = "STEM1", expertEmail = "s@x.com", expertName = "S", currentStatus = "NEW")
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(stemContact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("STEM1"))).thenReturn(
            listOf(expert("STEM1", "s@x.com").copy(disciplineCategory = "STEM"))
        )
        val expectedFilters = ExpertSearchService.notContactedWithEmailFilters(null, "UNCLASSIFIED")
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(0L)

        val summary = service.countPending()
        assertEquals(0, summary.pending)
        assertEquals(0, summary.retryable)
        assertEquals(0, summary.totalSendable)
    }

    @Test
    fun `retryable contact kept when country region matches scope regions`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        val contact = ExpertContact(id = 1L, campaignId = 10L, orcidId = "GER1", expertEmail = "g@x.com", expertName = "G", currentStatus = "NEW")
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("GER1"), ExpertIndexLevel.CANDIDATE))
            .thenReturn(listOf(expert("GER1", "g@x.com").copy(country = "Germany")))

        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            regions = listOf("Europe")
        )
        // No sendable accounts → stop at round gate; target count proves the retryable survived matchesExpert.
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue())).thenReturn(emptyList())
        stubScrolledExperts(emptyList())

        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(1, result.total)
    }

    @Test
    fun `retryable contact filtered when country region outside scope regions`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        val contact = ExpertContact(id = 1L, campaignId = 10L, orcidId = "GER1", expertEmail = "g@x.com", expertName = "G", currentStatus = "NEW")
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("GER1"), ExpertIndexLevel.CANDIDATE))
            .thenReturn(listOf(expert("GER1", "g@x.com").copy(country = "Germany")))

        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            regions = listOf("China")
        )
        stubScrolledExperts(emptyList())

        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.total)
    }

    @Test
    fun `retryable contact with null country kept when Other region selected`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        val contact = ExpertContact(id = 1L, campaignId = 10L, orcidId = "NUL1", expertEmail = "n@x.com", expertName = "N", currentStatus = "NEW")
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("NUL1"), ExpertIndexLevel.CANDIDATE))
            .thenReturn(listOf(expert("NUL1", "n@x.com").copy(country = null)))

        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            regions = listOf("Other")
        )
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue())).thenReturn(emptyList())
        stubScrolledExperts(emptyList())

        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(1, result.total)
    }

    @Test
    fun `ES CANDIDATE branch replaces not-contacted base when explicit non-NOT_CONTACTED status set (I3a-4)`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(emptyList())

        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            operatorStatuses = listOf("CONTACTED")
        )
        // I3a-4: 显式非 NOT_CONTACTED 状态必须换成状态无关基座（I-2 同款：term 与 must_not 并存恒为空）。
        val expectedFilters = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
        expectedFilters.add(
            mapOf(
                "bool" to mapOf(
                    "should" to listOf(mapOf("term" to mapOf("operatorStatus" to "CONTACTED"))),
                    "minimum_should_match" to 1
                )
            )
        )
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(0L)

        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.total)
        Mockito.verify(expertSearchService).countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters))
    }

    @Test
    fun `ES NOT_CONTACTED on CANDIDATE keeps must_not exists and never emits term operatorStatus (I-3)`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(emptyList())

        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            operatorStatuses = listOf("NOT_CONTACTED")
        )
        val expectedFilters = ExpertSearchService.notContactedWithEmailFilters().toMutableList()
        // I-3: NOT_CONTACTED 的唯一语义是 must_not exists operatorStatus，绝不写 term operatorStatus=NOT_CONTACTED。
        assertTrue(expectedFilters.any { (it["bool"] as? Map<*, *>)?.get("must_not") != null })
        assertTrue(expectedFilters.none { it.containsKey("term") })
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(0L)

        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.total)
        Mockito.verify(expertSearchService).countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters))
    }

    @Test
    fun `ES APPLICATION branch applies operatorStatus term filter (I-2)`() {
        val snapshot = BatchExecutionSnapshot(
            mailType = "MATERIAL_REMINDER",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "APPLICATION",
            tags = listOf("承诺回复材料"),
            operatorStatuses = listOf("CONTACTED"),
            templateId = 42L
        )
        val expectedFilters = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
        expectedFilters.add(
            mapOf(
                "bool" to mapOf(
                    "should" to listOf(mapOf("term" to mapOf("operatorStatus" to "CONTACTED"))),
                    "minimum_should_match" to 1
                )
            )
        )
        expectedFilters.add(mapOf("terms" to mapOf("tags" to listOf("承诺回复材料"))))
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.APPLICATION), eqValue(expectedFilters)))
            .thenReturn(0L)

        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.total)
        Mockito.verify(expertSearchService).countExperts(eqValue(ExpertIndexLevel.APPLICATION), eqValue(expectedFilters))
    }

    @Test
    fun `retryable contact with REPLIED status excluded when scope status is NOT_CONTACTED (I-1 retry bypass)`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        val contact = ExpertContact(id = 1L, campaignId = 10L, orcidId = "RPL1", expertEmail = "r@x.com", expertName = "R", currentStatus = "NEW")
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
        // A-3 形态：无 SENT 介绍信、current_status=NEW，但 operator_status=REPLIED（会进入重试目标集合的形态）
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("RPL1"), ExpertIndexLevel.CANDIDATE))
            .thenReturn(listOf(expert("RPL1", "r@x.com").copy(operatorStatus = "REPLIED")))

        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            operatorStatuses = listOf("NOT_CONTACTED")
        )
        stubScrolledExperts(emptyList())

        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.total)
    }

    @Test
    fun `empty operatorStatus leaves ES filters unchanged (must-not-change)`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(emptyList())

        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE"
        )
        // 留空 = 不限：过滤条件与升级前逐字一致（不多不少）。
        val expectedFilters = ExpertSearchService.notContactedWithEmailFilters().toMutableList()
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(0L)

        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.total)
        Mockito.verify(expertSearchService).countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters))
    }

    @Test
    fun `updateLegacyConfig preserves operatorStatus when only cron changes (I-4)`() {
        val configRepository = Mockito.mock(BatchSendTaskConfigRepository::class.java)
        val templateService = Mockito.mock(MailComposeTemplateService::class.java)
        val eventPublisher = Mockito.mock(org.springframework.context.ApplicationEventPublisher::class.java)
        val execService = Mockito.mock(com.weibo.talentintroduction.task.service.TaskExecutionService::class.java)
        val configService = BatchSendTaskConfigService(
            repository = configRepository,
            mailComposeTemplateService = templateService,
            objectMapper = ObjectMapper(),
            eventPublisher = eventPublisher,
            taskExecutionService = execService
        )
        val existing = BatchSendTaskConfig(
            id = 2L, configName = "默认介绍邮件任务", mailType = "INTRODUCTION",
            autoEnabled = false, cron = "0 0 0 * * ?", roundSize = 50,
            perMailIntervalMs = 1000, perRoundIntervalMs = 60000, selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE", tagsJson = "[]", regionsJson = "[]",
            emailDomainsJson = "[]", discipline = null, operatorStatusesJson = """["NOT_CONTACTED"]""",
            templateId = null, legacyCode = "INTRODUCTION",
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )
        Mockito.`when`(configRepository.findByLegacyCode("INTRODUCTION")).thenReturn(existing)
        Mockito.`when`(configRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(existing)
        Mockito.`when`(configRepository.findByConfigNameAndDeletedAtIsNull("默认介绍邮件任务")).thenReturn(existing)
        val captor = org.mockito.ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        Mockito.`when`(configRepository.save(Mockito.any(BatchSendTaskConfig::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as BatchSendTaskConfig).copy(id = 2L, legacyCode = "INTRODUCTION")
        }
        Mockito.`when`(execService.lastExecutedAtByBatchConfigIds(Mockito.anyList())).thenReturn(emptyMap())

        configService.updateLegacyConfig(
            BatchSendType.INTRODUCTION,
            BatchSendConfigUpdateRequest(
                autoEnabled = true,
                cron = "0 30 8 * * ?",
                dailyCap = 200,
                roundSize = 20,
                perMailIntervalMs = 2000,
                perRoundIntervalMs = 120000,
                selfCheckTtlMinutes = 15,
                emailDomain = "ox.ac.uk",
                discipline = "HUMANITIES",
                templateId = null
            )
        )

        // M-2/I3a-6: 旧 typed API 只改 cron，operatorStatuses 必须显式保留（漏写会命中 Kotlin 默认值静默重置）。
        Mockito.verify(configRepository).save(captor.capture())
        assertEquals("""["NOT_CONTACTED"]""", captor.value.operatorStatusesJson)
    }

    // ── P2a: emailDomains multi-value（I2a-2 / I2a-3 / I2a-4）──────────────────

    @Test
    fun `buildEsFiltersForLevel produces exactly one should OR for multi emailDomains (I2a-3)`() {
        val scope = RecipientScope(
            mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(), regions = emptyList(),
            emailDomains = listOf("a.com", "b.com"), discipline = null
        )
        val filters = invokeBuildEsFiltersForLevel(service, scope, "CANDIDATE")

        @Suppress("UNCHECKED_CAST")
        val shouldBlocks = filters.mapNotNull { it["bool"] as? Map<String, Any> }
            .filter { it["should"] is List<*> }
        assertEquals(1, shouldBlocks.size, "exactly one bool.should filter for N domains")
        val should = shouldBlocks.single()
        assertEquals(1, should["minimum_should_match"])
        val shouldList = should["should"] as List<*>
        assertEquals(2, shouldList.size)
        val wildcardValues = shouldList.map { item ->
            val wildcard = (item as Map<*, *>)["wildcard"] as Map<*, *>
            val email = wildcard["email"] as Map<*, *>
            email["value"]
        }
        assertEquals(listOf("*@a.com", "*@b.com"), wildcardValues)
    }

    @Test
    fun `buildEsFiltersForLevel emits no email wildcard for empty emailDomains (I2a-2)`() {
        val scope = RecipientScope(
            mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(), regions = emptyList(),
            emailDomains = emptyList(), discipline = null
        )
        val filters = invokeBuildEsFiltersForLevel(service, scope, "CANDIDATE")

        assertTrue(filters.none { it.containsKey("wildcard") }, "empty domains must not emit any wildcard filter")
        assertTrue(
            filters.none { (it["bool"] as? Map<*, *>)?.containsKey("should") == true },
            "empty domains must not emit any bool.should filter"
        )
    }

    @Test
    fun `matchesExpert applies emailDomains any-OR and skips judgment when empty (I2a-2 I2a-4)`() {
        val scoped = RecipientScope(
            mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(), regions = emptyList(),
            emailDomains = listOf("a.com", "b.com"), discipline = null
        )
        assertTrue(scoped.matchesExpert(expert("0001", "x@b.com")))
        assertFalse(scoped.matchesExpert(expert("0002", "x@c.com")))

        // I2a-2: 空集合 = 不限 —— 即使 profile 无 email 也不判定。
        val unrestricted = scoped.copy(emailDomains = emptyList())
        assertTrue(
            unrestricted.matchesExpert(
                ExpertProfile(
                    orcidId = "0003", email = null, givenNames = "G", familyNames = "F",
                    country = null, keyword = null, employment = null
                )
            )
        )
    }

    @Test
    fun `matchesExpert agrees with emailDomainsFilter semantics per profile (I2a-4)`() {
        val domains = listOf("a.com", "b.com")
        val scope = RecipientScope(
            mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(), regions = emptyList(),
            emailDomains = domains, discipline = null
        )
        val profiles = listOf(
            expert("0001", "x@a.com"),
            expert("0002", "x@b.com"),
            expert("0003", "x@c.com"),
            expert("0004", "no-at-sign"),
            expert("0005", "")
        )
        profiles.forEach { profile ->
            val email = profile.email
            val expected = email != null && domains.any { email.endsWith("@$it") }
            assertEquals(expected, scope.matchesExpert(profile), "parity mismatch for email=${profile.email}")
        }
    }

    // ── P3a: operatorStatuses multi-value（I3a-1 / I3a-2 / I3a-3 / I3a-4 / I3a-5 / N3a-2）──

    @Test
    fun `empty operatorStatuses keeps pre-change baseline filters verbatim on CANDIDATE (N3a-2)`() {
        val scope = RecipientScope(
            mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(), regions = emptyList(),
            emailDomains = emptyList(), discipline = null
        )
        val filters = invokeBuildEsFiltersForLevel(service, scope, "CANDIDATE")

        // 改动前基线逐字硬编码（不调用任何 helper 生成）：operatorStatus 留空（不限）时
        // CANDIDATE 分支走 notContacted 基座且不追加任何状态 filter —— 必须逐字相等（N3a-2）。
        val baseline = listOf(
            mapOf("exists" to mapOf("field" to "email")),
            mapOf(
                "bool" to mapOf(
                    "must_not" to listOf(
                        mapOf("exists" to mapOf("field" to "operatorStatus")),
                        mapOf("term" to mapOf("operatorStatus" to "EMAIL_INVALID"))
                    )
                )
            )
        )
        assertEquals(baseline, filters)
    }

    @Test
    fun `only NOT_CONTACTED keeps pre-change baseline filters verbatim on CANDIDATE (N3a-2)`() {
        val scope = RecipientScope(
            mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(), regions = emptyList(),
            emailDomains = emptyList(), discipline = null,
            operatorStatuses = listOf("NOT_CONTACTED")
        )
        val filters = invokeBuildEsFiltersForLevel(service, scope, "CANDIDATE")

        // 与上一条相同：仅选 NOT_CONTACTED 也必须与改动前逐字一致（N3a-2）。
        val baseline = listOf(
            mapOf("exists" to mapOf("field" to "email")),
            mapOf(
                "bool" to mapOf(
                    "must_not" to listOf(
                        mapOf("exists" to mapOf("field" to "operatorStatus")),
                        mapOf("term" to mapOf("operatorStatus" to "EMAIL_INVALID"))
                    )
                )
            )
        )
        assertEquals(baseline, filters)
    }

    @Test
    fun `CONTACTED switches to status-agnostic base without must_not exists operatorStatus (I3a-4)`() {
        val scope = RecipientScope(
            mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(), regions = emptyList(),
            emailDomains = emptyList(), discipline = null,
            operatorStatuses = listOf("CONTACTED")
        )
        val filters = invokeBuildEsFiltersForLevel(service, scope, "CANDIDATE")

        // 基座已切换：不得再出现 notContacted 基座的 must_not exists operatorStatus（I-2 陷阱）。
        assertTrue(
            filters.none { (it["bool"] as? Map<*, *>)?.get("must_not") != null },
            "CONTACTED must not keep the notContacted base (must_not exists operatorStatus)"
        )
        @Suppress("UNCHECKED_CAST")
        val shouldBlocks = filters.mapNotNull { it["bool"] as? Map<String, Any> }
            .filter { it["should"] is List<*> }
        assertEquals(1, shouldBlocks.size, "exactly one bool.should status filter")
        assertEquals(1, shouldBlocks.single()["minimum_should_match"])
        val shouldList = shouldBlocks.single()["should"] as List<*>
        assertEquals(1, shouldList.size)
        assertEquals(mapOf("term" to mapOf("operatorStatus" to "CONTACTED")), shouldList.single())
    }

    @Test
    fun `mixed NOT_CONTACTED and CONTACTED uses status-agnostic base with pure predicates (I3a-4 I3a-1 I3a-2)`() {
        val scope = RecipientScope(
            mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(), regions = emptyList(),
            emailDomains = emptyList(), discipline = null,
            operatorStatuses = listOf("NOT_CONTACTED", "CONTACTED")
        )
        val filters = invokeBuildEsFiltersForLevel(service, scope, "CANDIDATE")

        assertTrue(
            filters.none { (it["bool"] as? Map<*, *>)?.get("must_not") != null },
            "mixed statuses must use the status-agnostic base"
        )
        @Suppress("UNCHECKED_CAST")
        val shouldBlocks = filters.mapNotNull { it["bool"] as? Map<String, Any> }
            .filter { it["should"] is List<*> }
        assertEquals(1, shouldBlocks.size)
        val shouldList = shouldBlocks.single()["should"] as List<*>
        assertEquals(2, shouldList.size)
        // I3a-1/I3a-2: NOT_CONTACTED 分支必须是纯 must_not exists 谓词（无 exists email / EMAIL_INVALID）。
        assertTrue(
            shouldList.any {
                it == mapOf(
                    "bool" to mapOf(
                        "must_not" to listOf(mapOf("exists" to mapOf("field" to "operatorStatus")))
                    )
                )
            },
            "NOT_CONTACTED should branch must be the pure must_not exists predicate"
        )
        assertTrue(shouldList.any { it == mapOf("term" to mapOf("operatorStatus" to "CONTACTED")) })
    }

    @Test
    fun `operatorStatusPredicate is a pure predicate without email or EMAIL_INVALID terms (I3a-2)`() {
        val predicate = ExpertSearchService.operatorStatusPredicate("NOT_CONTACTED")
        val json = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(predicate)
        assertFalse(json.contains("\"email\""), "pure predicate must not carry exists email")
        assertFalse(json.contains("EMAIL_INVALID"), "pure predicate must not carry term EMAIL_INVALID")
        assertEquals(
            mapOf("bool" to mapOf("must_not" to listOf(mapOf("exists" to mapOf("field" to "operatorStatus"))))),
            predicate
        )
    }

    @Test
    fun `operatorStatusesFilter returns null for empty and trims dedupes (I3a-3)`() {
        assertNull(ExpertSearchService.operatorStatusesFilter(emptyList()))
        assertNull(ExpertSearchService.operatorStatusesFilter(listOf("  ", "")))
        val filter = ExpertSearchService.operatorStatusesFilter(listOf(" CONTACTED ", "CONTACTED"))
        assertNotNull(filter)
        @Suppress("UNCHECKED_CAST")
        val should = (filter!!["bool"] as Map<String, Any>)["should"] as List<*>
        assertEquals(1, should.size)
        assertEquals(mapOf("term" to mapOf("operatorStatus" to "CONTACTED")), should.single())
    }

    @Test
    fun `matchesExpert agrees with operatorStatusesFilter semantics per profile (I3a-5)`() {
        val statusGroups = listOf(
            emptyList(),
            listOf("NOT_CONTACTED"),
            listOf("CONTACTED"),
            listOf("NOT_CONTACTED", "EMAIL_INVALID")
        )
        val profiles = listOf(
            expert("0001", "a@b.com").copy(operatorStatus = null),
            expert("0002", "b@b.com").copy(operatorStatus = ""),
            expert("0003", "c@b.com").copy(operatorStatus = "CONTACTED"),
            expert("0004", "d@b.com").copy(operatorStatus = "EMAIL_INVALID"),
            expert("0005", "e@b.com").copy(operatorStatus = "REPLIED")
        )
        statusGroups.forEach { statuses ->
            val scope = RecipientScope(
                mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
                tags = emptyList(), regions = emptyList(),
                emailDomains = emptyList(), discipline = null,
                operatorStatuses = statuses
            )
            profiles.forEach { profile ->
                val expected = statuses.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    .let { values ->
                        values.isEmpty() || values.any {
                            if (it == "NOT_CONTACTED") profile.operatorStatus.isNullOrBlank()
                            else profile.operatorStatus == it
                        }
                    }
                assertEquals(
                    expected,
                    scope.matchesExpert(profile),
                    "parity mismatch for statuses=$statuses profile.operatorStatus=${profile.operatorStatus}"
                )
            }
        }
    }

    @Test
    fun `serialized CANDIDATE filters never contain literal NOT_CONTACTED term (I3a-1)`() {
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        listOf(
            RecipientScope(
                mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
                tags = emptyList(), regions = emptyList(),
                emailDomains = emptyList(), discipline = null,
                operatorStatuses = listOf("NOT_CONTACTED")
            ),
            RecipientScope(
                mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
                tags = emptyList(), regions = emptyList(),
                emailDomains = emptyList(), discipline = null,
                operatorStatuses = listOf("NOT_CONTACTED", "CONTACTED")
            ),
            RecipientScope(
                mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
                tags = emptyList(), regions = emptyList(),
                emailDomains = emptyList(), discipline = null,
                operatorStatuses = emptyList()
            )
        ).forEach { scope ->
            val json = mapper.writeValueAsString(invokeBuildEsFiltersForLevel(service, scope, "CANDIDATE"))
            assertFalse(json.contains("NOT_CONTACTED"), "no filter may serialize the literal NOT_CONTACTED")
        }
    }

    // ── P4a: 邮件模版门禁过滤（I4a-1..I4a-6 / M-1 / M-2 / M-4）────────────────────

    @Test
    fun `gateFilterEnabled false keeps pre-change baseline filters verbatim (I4a-1)`() {
        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            gateFilterEnabled = false
        )
        val scope = invokeResolveScope(service, snapshot)
        // fromSnapshot 不解析门禁字段（I4a-4：解析只在 resolveScope），开关关闭 → 默认空。
        assertEquals(emptyList<String>(), scope.gateEsFields)

        val filters = invokeBuildEsFiltersForLevel(service, scope, "CANDIDATE")
        // 改动前基线逐字硬编码（I4a-1 / N4a-1）：门禁关闭时不追加任何 filter。
        val baseline = listOf(
            mapOf("exists" to mapOf("field" to "email")),
            mapOf(
                "bool" to mapOf(
                    "must_not" to listOf(
                        mapOf("exists" to mapOf("field" to "operatorStatus")),
                        mapOf("term" to mapOf("operatorStatus" to "EMAIL_INVALID"))
                    )
                )
            )
        )
        assertEquals(baseline, filters)
        // 开关关闭时不得触碰模板解析。
        Mockito.verify(mailComposeTemplateService, Mockito.never()).requiredEsFields(Mockito.anyLong())
    }

    @Test
    fun `gateFilterEnabled true without template keeps pre-change baseline filters verbatim (I4a-1)`() {
        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            templateId = null,
            gateFilterEnabled = true
        )
        val scope = invokeResolveScope(service, snapshot)
        assertEquals(emptyList<String>(), scope.gateEsFields)

        val filters = invokeBuildEsFiltersForLevel(service, scope, "CANDIDATE")
        val baseline = listOf(
            mapOf("exists" to mapOf("field" to "email")),
            mapOf(
                "bool" to mapOf(
                    "must_not" to listOf(
                        mapOf("exists" to mapOf("field" to "operatorStatus")),
                        mapOf("term" to mapOf("operatorStatus" to "EMAIL_INVALID"))
                    )
                )
            )
        )
        assertEquals(baseline, filters)
        // 无 templateId → resolveScope 提前返回，不查模板。
        Mockito.verify(mailComposeTemplateService, Mockito.never()).requiredEsFields(Mockito.anyLong())
    }

    @Test
    fun `gateFilterEnabled true with empty template required keys keeps baseline verbatim (I4a-1)`() {
        Mockito.`when`(mailComposeTemplateService.requiredEsFields(42L)).thenReturn(emptyList())
        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            templateId = 42L,
            gateFilterEnabled = true
        )
        val scope = invokeResolveScope(service, snapshot)
        assertEquals(emptyList<String>(), scope.gateEsFields)

        val filters = invokeBuildEsFiltersForLevel(service, scope, "CANDIDATE")
        val baseline = listOf(
            mapOf("exists" to mapOf("field" to "email")),
            mapOf(
                "bool" to mapOf(
                    "must_not" to listOf(
                        mapOf("exists" to mapOf("field" to "operatorStatus")),
                        mapOf("term" to mapOf("operatorStatus" to "EMAIL_INVALID"))
                    )
                )
            )
        )
        assertEquals(baseline, filters)
    }

    @Test
    fun `gate fields AND two independent presence filters flat in bool filter (I4a-2)`() {
        Mockito.`when`(mailComposeTemplateService.requiredEsFields(42L)).thenReturn(listOf("institution", "researchFields"))
        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            templateId = 42L,
            gateFilterEnabled = true
        )
        val scope = invokeResolveScope(service, snapshot)
        assertEquals(listOf("institution", "researchFields"), scope.gateEsFields)

        val filters = invokeBuildEsFiltersForLevel(service, scope, "CANDIDATE")
        // I4a-2: 基线 2 项 + 恰好 2 项门禁 filter（每字段一个独立 filter，平铺进 bool.filter）。
        assertEquals(4, filters.size)
        assertEquals(mapOf("exists" to mapOf("field" to "institution")), filters[2])
        assertEquals(
            mapOf(
                "bool" to mapOf(
                    "must" to listOf(mapOf("exists" to mapOf("field" to "researchFields"))),
                    "must_not" to listOf(mapOf("term" to mapOf("researchFields" to "")))
                )
            ),
            filters[3]
        )
        // 门禁语义是 AND（任一缺失即拦）：任何 filter 都不得是 should 块。
        val json = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(filters)
        assertFalse(json.contains("\"should\""), "gate filters must be AND (flat), not should")
    }

    @Test
    fun `gate fields outside ALLOWED_HAS_FIELDS are dropped without throwing (I4a-3)`() {
        Mockito.`when`(mailComposeTemplateService.requiredEsFields(42L)).thenReturn(listOf("institution", "keyword", "hIndex"))
        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            templateId = 42L,
            gateFilterEnabled = true
        )
        val scope = invokeResolveScope(service, snapshot)
        // I4a-3: 差集字段（keyword/hIndex）在 resolveScope 被裁剪，只保留交集。
        assertEquals(listOf("institution"), scope.gateEsFields)

        val filters = invokeBuildEsFiltersForLevel(service, scope, "CANDIDATE")
        assertEquals(3, filters.size)
        assertEquals(mapOf("exists" to mapOf("field" to "institution")), filters[2])
    }

    @Test
    fun `fieldPresenceFilters fails fast on fields outside ALLOWED_HAS_FIELDS (I4a-3)`() {
        // 兜底 require：若调用方未裁剪就把越界字段传进来，必须 fail-fast 而非静默忽略。
        val ex = assertThrows(IllegalArgumentException::class.java) {
            ExpertSearchService.fieldPresenceFilters(listOf("keyword"))
        }
        assertTrue(ex.message!!.contains("Invalid gate ES field"))
        // 空集合返回空列表（I4a-1）。
        assertEquals(emptyList<Map<String, Any>>(), ExpertSearchService.fieldPresenceFilters(emptyList()))
    }

    @Test
    fun `preview and execution resolve identical gateEsFields for same snapshot (I4a-4)`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(emptyList())
        Mockito.`when`(mailComposeTemplateService.requiredEsFields(42L)).thenReturn(listOf("institution"))

        // 门禁开启的预期 filter 列表：基线 + institution 存在性 filter（I4a-2 平铺）。
        val expectedFilters = ExpertSearchService.notContactedWithEmailDomainsFilters().toMutableList()
        expectedFilters.add(mapOf("exists" to mapOf("field" to "institution")))
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(1L)

        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            templateId = 42L,
            gateFilterEnabled = true
        )

        // 预估路径（countBySnapshot → resolveScope → countEsTargets）
        val preview = service.countBySnapshot(snapshot)
        assertEquals(1, preview.pending)
        assertEquals(0, preview.retryable)
        assertEquals(1, preview.totalSendable)

        // 执行路径（无可用账号 → 停在轮次闸口，不发信）
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue())).thenReturn(emptyList())
        val result = service.run(snapshot, 12346L, ExecutionMode.MANUAL, oneRoundOnly = true)
        assertEquals(preview.totalSendable, result.total)

        // I4a-4 / M-4: 两条路径对同一 snapshot 使用完全相同的 filter 列表（同源同口径）。
        // 调用次数 = 预估 countEsTargets(1) + 执行 countEsTargets(1) + 执行 fetchEsPage
        // 首页预取(1，OutreachTargetIterator.hasNext 在轮次闸口前拉首页)；全部命中同一列表。
        Mockito.verify(expertSearchService, Mockito.times(3))
            .countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters))
    }

    @Test
    fun `matchesExpert agrees with fieldPresenceFilter semantics per profile (I4a-5)`() {
        data class Case(val field: String, val profile: ExpertProfile, val expected: Boolean, val label: String)

        val base = expert("0001", "a@b.com")
        val cases = listOf(
            // employment / institution 非 BLANK_EXCLUDABLE：ES 仅 exists，空串算有值（!= null）。
            Case("employment", base.copy(employment = null), false, "employment=null"),
            Case("employment", base.copy(employment = ""), true, "employment=\"\" (ES exists 为真)"),
            Case("employment", base.copy(employment = "X University"), true, "employment=value"),
            Case("institution", base.copy(institution = null), false, "institution=null"),
            Case("institution", base.copy(institution = ""), true, "institution=\"\" (ES exists 为真)"),
            Case("institution", base.copy(institution = "Tsinghua"), true, "institution=value"),
            // BLANK_EXCLUDABLE：ES 是 exists AND NOT term ""，空串不算有值。
            Case("degree", base.copy(degree = null), false, "degree=null"),
            Case("degree", base.copy(degree = ""), false, "degree=\"\" (ES must_not term \"\")"),
            Case("degree", base.copy(degree = "PhD"), true, "degree=value"),
            Case("researchFields", base.copy(researchFields = null), false, "researchFields=null"),
            Case("researchFields", base.copy(researchFields = ""), false, "researchFields=\"\""),
            Case("researchFields", base.copy(researchFields = "AI"), true, "researchFields=value"),
            Case("recentWorkTitles", base.copy(recentWorkTitles = null), false, "recentWorkTitles=null"),
            Case("recentWorkTitles", base.copy(recentWorkTitles = emptyList()), false, "recentWorkTitles=[]"),
            Case("recentWorkTitles", base.copy(recentWorkTitles = listOf("")), false, "recentWorkTitles=[\"\"]"),
            Case("recentWorkTitles", base.copy(recentWorkTitles = listOf("Paper A")), true, "recentWorkTitles=[value]"),
            Case("patentTitles", base.copy(patentTitles = null), false, "patentTitles=null"),
            Case("patentTitles", base.copy(patentTitles = listOf(" ")), false, "patentTitles=[blank]"),
            Case("patentTitles", base.copy(patentTitles = listOf("Patent 1")), true, "patentTitles=[value]")
        )

        cases.forEach { case ->
            val scope = RecipientScope(
                mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
                tags = emptyList(), regions = emptyList(),
                emailDomains = emptyList(), discipline = null,
                gateEsFields = listOf(case.field)
            )
            assertEquals(
                case.expected,
                scope.matchesExpert(case.profile),
                "parity mismatch for ${case.label}"
            )
        }

        // AND 语义：两个字段必须同时满足（I4a-2）。
        val both = RecipientScope(
            mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(), regions = emptyList(),
            emailDomains = emptyList(), discipline = null,
            gateEsFields = listOf("institution", "degree")
        )
        assertTrue(both.matchesExpert(base.copy(institution = "Tsinghua", degree = "PhD")))
        assertFalse(both.matchesExpert(base.copy(institution = "Tsinghua", degree = "")))
        // institution 非 BLANK_EXCLUDABLE：空串在 ES 里 exists 为真 → 内存侧算有值（I4a-5）。
        assertTrue(both.matchesExpert(base.copy(institution = "", degree = "PhD")))

        // 空 gateEsFields 不做任何判定（I4a-1）。
        val none = RecipientScope(
            mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(), regions = emptyList(),
            emailDomains = emptyList(), discipline = null,
            gateEsFields = emptyList()
        )
        assertTrue(none.matchesExpert(base.copy(institution = null, degree = null)))
    }

    // ──── Helpers ────

    private fun expert(orcidId: String, email: String): ExpertProfile =
        ExpertProfile(
            orcidId = orcidId,
            email = email,
            givenNames = "Given",
            familyNames = "Family",
            country = "China",
            keyword = "keyword",
            employment = "University"
        )

    private fun stubPagedExperts(experts: List<ExpertProfile>) {
        Mockito.`when`(expertSearchService.searchExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE),
            anyValue(emptyList()),
            anyInt(),
            anyInt()
        )).thenAnswer { invocation ->
            val from = invocation.getArgument<Int>(2)
            val size = invocation.getArgument<Int>(3)
            experts.drop(from).take(size)
        }

        Mockito.`when`(expertSearchService.countExperts(
            eqValue(ExpertIndexLevel.CANDIDATE),
            anyValue(emptyList())
        )).thenReturn(experts.size.toLong())
    }

    private fun stubScrolledExperts(experts: List<ExpertProfile>) = stubPagedExperts(experts)

    private fun account(accountCode: String): MailSenderAccount =
        MailSenderAccount(
            accountCode = accountCode,
            senderEmail = "$accountCode@example.com",
            senderName = accountCode,
            senderTitle = "Title",
            senderDisplayName = accountCode,
            teamName = "Team",
            countryName = "China",
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "$accountCode@example.com",
            smtpPassword = "secret",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "$accountCode@example.com",
            imapPassword = "secret"
        )

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    private fun invokeBuildEsFiltersForLevel(
        service: ManualInitialOutreachService,
        scope: RecipientScope,
        level: String
    ): List<Map<String, Any>> {
        val method = ManualInitialOutreachService::class.java.getDeclaredMethod(
            "buildEsFiltersForLevel", RecipientScope::class.java, String::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(service, scope, level) as List<Map<String, Any>>
    }

    private fun invokeResolveScope(
        service: ManualInitialOutreachService,
        snapshot: BatchExecutionSnapshot
    ): RecipientScope {
        val method = ManualInitialOutreachService::class.java.getDeclaredMethod(
            "resolveScope", BatchExecutionSnapshot::class.java
        )
        method.isAccessible = true
        return method.invoke(service, snapshot) as RecipientScope
    }

    private fun <T> eqValue(value: T): T =
        Mockito.eq(value) ?: value

    private fun <T> captureValue(captor: org.mockito.ArgumentCaptor<T>, defaultValue: T): T =
        captor.capture() ?: defaultValue

    /**
     * A3 测试适配：材料提醒轮（manual=true）的 resolveForSend 桩。
     * 未绑定 contact → 抛 SenderAccountNotBoundException，生产代码走 selectAccount 兜底 + bindIfAbsent（I-1/IP-1）。
     */
    private fun stubReminderResolveForSendNotBound() {
        Mockito.`when`(senderAccountBindingService.resolveForSend(
            anyValue(ExpertContact(campaignId = 0, orcidId = "", expertEmail = "", expertName = null)),
            eqValue(true),
            eqValue(false)
        )).thenThrow(SenderAccountNotBoundException(0L))
    }

    private fun anyBooleanValue(): Boolean = Mockito.anyBoolean() ?: false
}
