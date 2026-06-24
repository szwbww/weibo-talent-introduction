package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.Campaign
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.MailSendAttempt
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
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
import com.weibo.talentintroduction.mail.service.ProviderResolver
import com.weibo.talentintroduction.mail.service.ComposedMail
import com.weibo.talentintroduction.mail.service.DeliveredMail
import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory
import com.weibo.talentintroduction.mail.service.SelfCheckResult
import com.weibo.talentintroduction.mail.service.SenderAccountAssignmentService
import com.weibo.talentintroduction.mail.service.SenderAccountSelfCheckService
import com.weibo.talentintroduction.mail.service.SenderWarmupService
import com.weibo.talentintroduction.task.service.TaskProgressStore
import com.weibo.talentintroduction.task.service.TaskProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.time.LocalDateTime

class ManualInitialOutreachServiceTest {
    private val expertSearchService = Mockito.mock(ExpertSearchService::class.java)
    private val senderAccountAssignmentService = Mockito.mock(SenderAccountAssignmentService::class.java)
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
        senderWarmupService = senderWarmupService
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
        Mockito.`when`(mailSenderAccountService.listSendableAccounts()).thenReturn(listOf(account("chen")))
        Mockito.`when`(mailSenderAccountService.listAccounts()).thenReturn(listOf(account("chen")))
        Mockito.`when`(mailSenderAccountService.listEnabledAccounts()).thenReturn(listOf(account("chen")))
        // Default: self-check always passes (from cache, no probe)
        Mockito.`when`(selfCheckService.checkSendable(anyValue(account("chen")))).thenReturn(
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
    fun `runBulkOutreach sends mail successfully`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        // No SENT introduction for new contact
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
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
    fun `runBulkOutreach handles failure gracefully and continues`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
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
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf())))
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

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)

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

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
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

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
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

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
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

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
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

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg1", "SENT"))

        val result = service.runScheduledBatch(12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(1, result.sent)
        // L3-1: self-check invoked for the sendable account at the round gate
        Mockito.verify(selfCheckService).checkSendable(anyValue(account("chen")))
    }

    @Test
    fun `runScheduledBatch pauses flow with NO_AVAILABLE_ACCOUNT when gate finds no sendable accounts`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(mailSenderAccountService.listSendableAccounts()).thenReturn(emptyList())
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
        Mockito.`when`(mailSenderAccountService.listSendableAccounts())
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
        Mockito.verify(selfCheckService).checkSendable(anyValue(account("chen")))
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `runScheduledBatch respects dailyCap and stops full run with COMPLETED`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        // 5 experts, dailyCap=2, roundSize=10 → only 2 sent (L3-2/I-6)
        stubScrolledExperts(listOf(
            expert("0001", "a@b.com"), expert("0002", "c@d.com"), expert("0003", "e@f.com"),
            expert("0004", "g@h.com"), expert("0005", "i@j.com")
        ))
        for (orcid in listOf("0001", "0002", "0003", "0004", "0005")) {
            Mockito.`when`(expertContactRepository.existsByOrcidId(orcid)).thenReturn(false)
        }
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg", "SENT"))

        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig(roundSize = 10, dailyCap = 2))

        val result = service.runScheduledBatch(12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        // I-6/L3-2: at most dailyCap=2 mails sent
        assertEquals(2, result.sent)
        assertEquals(5, result.total)
        // Full run hitting dailyCap → COMPLETED (IDLE for next day)
        assertEquals("COMPLETED", result.finalStatus)
        Mockito.verify(mailDeliveryService, Mockito.times(2)).send(anyValue(account), anyValue(ComposedMail("","","")))
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

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
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

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg", "SENT"))

        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig(roundSize = 2, dailyCap = 10))

        val result = service.runScheduledBatch(12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        assertEquals(3, result.sent)
        assertEquals(3, result.total)
        assertEquals("COMPLETED", result.finalStatus)
        // Round gate invoked for each round (2 rounds)
        Mockito.verify(selfCheckService, Mockito.atLeast(2)).checkSendable(anyValue(account("chen")))
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

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
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
        assertEquals(1000, details["dailyCap"])
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
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
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

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
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

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("bad@example.com", "Subject", "Body"))
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

        Mockito.verify(expertIndexWriterService).syncCandidateOperatorStatus("0001", "EMAIL_INVALID")
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

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
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

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenAnswer { invocation ->
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

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
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

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)

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

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
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

        val expectedFilters = ExpertSearchService.notContactedWithEmailFilters("gmail.com")
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(0L)

        val result = service.runScheduledBatch(12345L, ExecutionMode.MANUAL, oneRoundOnly = false)
        assertEquals(0, result.total)
        
        Mockito.verify(expertSearchService).countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters))
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
        Mockito.`when`(mailSenderAccountService.listSendableAccounts()).thenReturn(emptyList())
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
        Mockito.`when`(mailSenderAccountService.listSendableAccounts()).thenReturn(emptyList())
        Mockito.`when`(mailSenderAccountService.listEnabledAccounts()).thenReturn(listOf(atLimit, faultPaused))

        val result = service.runScheduledBatch(12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        assertEquals("NO_AVAILABLE_ACCOUNT", result.stopReason)
        assertEquals("PAUSED", result.finalStatus)
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

    private fun <T> eqValue(value: T): T =
        Mockito.eq(value) ?: value

    private fun <T> captureValue(captor: org.mockito.ArgumentCaptor<T>, defaultValue: T): T =
        captor.capture() ?: defaultValue
}
