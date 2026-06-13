package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.Campaign
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.MailSendAttempt
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
import com.weibo.talentintroduction.campaign.repository.CampaignRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.MailSendAttemptRepository
import com.weibo.talentintroduction.config.ManualOutreachProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.IntroductionMailComposer
import com.weibo.talentintroduction.mail.service.MailDeliveryService
import com.weibo.talentintroduction.mail.service.ComposedMail
import com.weibo.talentintroduction.mail.service.DeliveredMail
import com.weibo.talentintroduction.mail.service.SenderAccountAssignmentService
import com.weibo.talentintroduction.task.service.TaskProgressStore
import com.weibo.talentintroduction.task.service.TaskProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
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
        txHelper = txHelper
    )

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
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
    }

    @Test
    fun `countPending counts new candidates from ES`() {
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(null)
        stubScrolledExperts(listOf(expert("0001", "a@b.com"), expert("0002", "c@d.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(expertContactRepository.existsByOrcidId("0002")).thenReturn(true)

        val summary = service.countPending()
        assertEquals(1, summary.pending) // only 0001 has no contact
        assertEquals(0, summary.retryable)
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
    }

    @Test
    fun `countPending without manual campaign has no retryables`() {
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(null)
        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)

        val summary = service.countPending()

        assertEquals(1, summary.pending)
        assertEquals(0, summary.retryable)
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
        assertEquals(1, result.total)
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
        assertEquals(1, result.total)
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
        assertEquals(1, result.total)
        assertEquals(1, result.sent)
        // Existing contact was reused, no new contact created
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

    private fun stubScrolledExperts(experts: List<ExpertProfile>) {
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val handler = invocation.getArgument<(List<ExpertProfile>) -> Boolean>(2)
            handler(experts)
            null
        }.`when`(expertSearchService).scrollExperts(
            eqValue(ExpertIndexLevel.CANDIDATE),
            anyInt(),
            anyValue<(List<ExpertProfile>) -> Boolean> { true }
        )
    }

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
}
