package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.Campaign
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.MailSendAttempt
import com.weibo.talentintroduction.campaign.repository.CampaignRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.MailSendAttemptRepository
import com.weibo.talentintroduction.config.ManualOutreachProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.IntroductionMailComposer
import com.weibo.talentintroduction.mail.service.MailDeliveryService
import com.weibo.talentintroduction.mail.service.ComposedMail
import com.weibo.talentintroduction.mail.service.DeliveredMail
import com.weibo.talentintroduction.mail.service.SenderAccountAssignmentService
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
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
    private val progressStore = Mockito.mock(TaskProgressStore::class.java)
    private val properties = ManualOutreachProperties(sendIntervalMs = 0L)
    private val txHelper = Mockito.mock(ManualOutreachTxHelper::class.java)
    private val mailSendAttemptRepository = Mockito.mock(MailSendAttemptRepository::class.java)

    private val service = ManualInitialOutreachService(
        expertSearchService,
        senderAccountAssignmentService,
        introductionMailComposer,
        mailDeliveryService,
        expertContactRepository,
        campaignRepository,
        mailRecordRepository,
        mailSenderAccountRepository,
        progressStore,
        properties,
        txHelper,
        mailSendAttemptRepository
    )

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        Mockito.`when`(mailSendAttemptRepository.save(anyValue(MailSendAttempt(orcidId="", mailType="", accountCode="", messageId="", status="")))).thenAnswer { invocation ->
            invocation.getArgument<MailSendAttempt>(0)
        }
        Mockito.`when`(mailSendAttemptRepository.findByOrcidIdAndMailType(anyValue(""), anyValue(""))).thenReturn(null)
    }

    @Test
    fun `countPending counts correctly`() {
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val handler = invocation.getArgument<(List<ExpertProfile>) -> Boolean>(2)
            handler(listOf(expert("0001", "a@b.com"), expert("0002", "c@d.com")))
            null
        }.`when`(expertSearchService).scrollExperts(
            eqValue(ExpertIndexLevel.CANDIDATE),
            anyInt(),
            anyValue<(List<ExpertProfile>) -> Boolean> { true }
        )

        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(expertContactRepository.existsByOrcidId("0002")).thenReturn(true)
        Mockito.`when`(expertContactRepository.findAllByCurrentStatus("NEW")).thenReturn(listOf(
            ExpertContact(id = 1L, campaignId = 1L, orcidId = "0003", expertEmail = "e@f.com", expertName = null, currentStatus = "NEW")
        ))

        val summary = service.countPending()
        assertEquals(1, summary.pending) // only 0001 has no contact
        assertEquals(1, summary.retryable) // 0003
    }

    @Test
    fun `runBulkOutreach sends mail successfully`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCurrentStatus("NEW")).thenReturn(emptyList())

        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val handler = invocation.getArgument<(List<ExpertProfile>) -> Boolean>(2)
            handler(listOf(expert("0001", "a@b.com")))
            null
        }.`when`(expertSearchService).scrollExperts(
            eqValue(ExpertIndexLevel.CANDIDATE),
            anyInt(),
            anyValue<(List<ExpertProfile>) -> Boolean> { true }
        )

        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        val dummyContact = ExpertContact(id = 1L, campaignId = 10L, orcidId = "0001", expertEmail = "a@b.com", expertName = null)
        Mockito.`when`(expertContactRepository.save(anyValue(dummyContact))).thenAnswer { invocation ->
            invocation.getArgument<ExpertContact>(0).copy(id = 100L)
        }

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail(messageId = "msg1", status = "SENT"))

        val result = service.runBulkOutreach(12345L)
        assertEquals(1, result.total)
        assertEquals(1, result.sent)
        assertEquals(0, result.failed)
        Mockito.verify(txHelper).recordSuccess(anyValue(dummyContact), eqValue("chen"), anyValue(""), eqValue("Subject"), eqValue("Body"))
    }

    @Test
    fun `runBulkOutreach handles failure gracefully`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCurrentStatus("NEW")).thenReturn(emptyList())

        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val handler = invocation.getArgument<(List<ExpertProfile>) -> Boolean>(2)
            handler(listOf(expert("0001", "a@b.com")))
            null
        }.`when`(expertSearchService).scrollExperts(
            eqValue(ExpertIndexLevel.CANDIDATE),
            anyInt(),
            anyValue<(List<ExpertProfile>) -> Boolean> { true }
        )

        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        val dummyContact = ExpertContact(id = 1L, campaignId = 10L, orcidId = "0001", expertEmail = "a@b.com", expertName = null)
        Mockito.`when`(expertContactRepository.save(anyValue(dummyContact))).thenAnswer { invocation ->
            invocation.getArgument<ExpertContact>(0).copy(id = 100L)
        }

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenThrow(RuntimeException("SMTP connection failed"))

        val result = service.runBulkOutreach(12345L)
        assertEquals(1, result.total)
        assertEquals(0, result.sent)
        assertEquals(0, result.failed)
        assertEquals(1, result.unknown)
        Mockito.verify(txHelper).recordFailure(eqValue(100L), eqValue("chen"), eqValue("SMTP connection failed"), eqValue("Subject"), eqValue(""))
    }

    @Test
    fun `runBulkOutreach terminates when cancelled`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCurrentStatus("NEW")).thenReturn(emptyList())

        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val handler = invocation.getArgument<(List<ExpertProfile>) -> Boolean>(2)
            handler(listOf(expert("0001", "a@b.com"), expert("0002", "c@d.com")))
            null
        }.`when`(expertSearchService).scrollExperts(
            eqValue(ExpertIndexLevel.CANDIDATE),
            anyInt(),
            anyValue<(List<ExpertProfile>) -> Boolean> { true }
        )

        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(expertContactRepository.existsByOrcidId("0002")).thenReturn(false)

        Mockito.`when`(progressStore.isCancelled(eqValue("MANUAL_INITIAL_OUTREACH"), eqValue(12345L))).thenReturn(true)

        val result = service.runBulkOutreach(12345L)
        assertEquals(2, result.total)
        assertEquals(0, result.sent)
        assertTrue(result.wasCancelled)
    }

    @Test
    fun `runBulkOutreach prevents duplicate SMTP send when previous database write failed`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCurrentStatus("NEW")).thenReturn(emptyList())

        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val handler = invocation.getArgument<(List<ExpertProfile>) -> Boolean>(2)
            handler(listOf(expert("0001", "a@b.com")))
            null
        }.`when`(expertSearchService).scrollExperts(
            eqValue(ExpertIndexLevel.CANDIDATE),
            anyInt(),
            anyValue<(List<ExpertProfile>) -> Boolean> { true }
        )

        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        val dummyContact = ExpertContact(id = 100L, campaignId = 10L, orcidId = "0001", expertEmail = "a@b.com", expertName = null)
        Mockito.`when`(expertContactRepository.save(anyValue(dummyContact))).thenReturn(dummyContact)

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))

        // Mock database failure during txHelper.recordSuccess
        Mockito.doThrow(RuntimeException("DB save error")).`when`(txHelper).recordSuccess(
            anyValue(dummyContact), eqValue("chen"), any(String::class.java), eqValue("Subject"), eqValue("Body")
        )

        // Mock MailSendAttemptRepository
        var savedAttempt: MailSendAttempt? = null
        Mockito.`when`(mailSendAttemptRepository.findByOrcidIdAndMailType("0001", "INTRODUCTION")).thenAnswer { savedAttempt }
        Mockito.`when`(mailSendAttemptRepository.save(anyValue(MailSendAttempt(orcidId="0001", mailType="INTRODUCTION", accountCode="chen", messageId="msg1", status="DELIVERY_UNKNOWN")))).thenAnswer { invocation ->
            val att = invocation.getArgument<MailSendAttempt>(0)
            savedAttempt = att
            att
        }

        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail(messageId = "msg1", status = "SENT"))

        // Run first time. It should attempt SMTP send but then fail due to DB save error
        try {
            service.runBulkOutreach(12345L)
        } catch (_: Exception) {}

        // Verify that attempt status is DELIVERY_UNKNOWN
        assertEquals("DELIVERY_UNKNOWN", savedAttempt?.status)

        // Now run a second time. The scanning should check attempt.status and see DELIVERY_UNKNOWN.
        // It should NOT call mailDeliveryService.send again!
        Mockito.clearInvocations(mailDeliveryService)

        // Count pending should return 0 pending because it's DELIVERY_UNKNOWN
        val summary = service.countPending()
        assertEquals(0, summary.pending)

        // Run bulk outreach again
        val result2 = service.runBulkOutreach(12345L)
        assertEquals(0, result2.sent)
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `runBulkOutreach stops and reports quota exhaustion when selectAccount throws NoAvailableSenderAccountException`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCurrentStatus("NEW")).thenReturn(emptyList())

        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val handler = invocation.getArgument<(List<ExpertProfile>) -> Boolean>(2)
            handler(listOf(expert("0001", "a@b.com")))
            null
        }.`when`(expertSearchService).scrollExperts(
            eqValue(ExpertIndexLevel.CANDIDATE),
            anyInt(),
            anyValue<(List<ExpertProfile>) -> Boolean> { true }
        )

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
    fun `runBulkOutreach stops immediately on unsafe send exceptions`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCurrentStatus("NEW")).thenReturn(emptyList())

        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val handler = invocation.getArgument<(List<ExpertProfile>) -> Boolean>(2)
            handler(listOf(expert("0001", "a@b.com"), expert("0002", "c@d.com")))
            null
        }.`when`(expertSearchService).scrollExperts(
            eqValue(ExpertIndexLevel.CANDIDATE),
            anyInt(),
            anyValue<(List<ExpertProfile>) -> Boolean> { true }
        )

        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(expertContactRepository.existsByOrcidId("0002")).thenReturn(false)

        val dummyContact = ExpertContact(id = 100L, campaignId = 10L, orcidId = "0001", expertEmail = "a@b.com", expertName = null)
        Mockito.`when`(expertContactRepository.save(anyValue(dummyContact))).thenReturn(dummyContact)

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")))).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))

        // Unsafe exception (e.g. SMTP server rejected message body)
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","",""))))
            .thenThrow(RuntimeException("554 SMTP body rejected"))

        val result = service.runBulkOutreach(12345L)
        assertEquals(2, result.total)
        assertEquals(0, result.sent)
        assertEquals(0, result.failed)
        assertEquals(1, result.unknown)
        // Verify that the loop stopped and did not process the second candidate
        Mockito.verify(mailDeliveryService, Mockito.times(1)).send(anyValue(account), anyValue(ComposedMail("","","")))
    }

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
