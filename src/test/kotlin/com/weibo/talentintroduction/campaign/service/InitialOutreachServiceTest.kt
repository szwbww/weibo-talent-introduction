package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.expert.domain.ExpertClassification
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.ExpertType
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.expert.service.ExpertSearchResult
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.service.ComposedMail
import com.weibo.talentintroduction.mail.service.DeliveredMail
import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import com.weibo.talentintroduction.mail.service.AutoReplySettingService
import com.weibo.talentintroduction.mail.service.IntroductionMailComposer
import com.weibo.talentintroduction.mail.service.MailDeliveryService
import com.weibo.talentintroduction.mail.service.SenderAccountAssignmentService
import com.weibo.talentintroduction.mail.service.SenderAccountBindingService
import com.weibo.talentintroduction.mail.service.SenderBindingStock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.LocalDateTime

class InitialOutreachServiceTest {
    private val expertSearchService = Mockito.mock(ExpertSearchService::class.java)
    private val senderAccountAssignmentService = Mockito.mock(SenderAccountAssignmentService::class.java)
    private val introductionMailComposer = Mockito.mock(IntroductionMailComposer::class.java)
    private val mailDeliveryService = Mockito.mock(MailDeliveryService::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val txHelper = Mockito.mock(ManualOutreachTxHelper::class.java)
    private val emailSuppressionService = Mockito.mock(EmailSuppressionService::class.java)
    private val autoReplySettingService = Mockito.mock(AutoReplySettingService::class.java)
    private val senderAccountBindingService = Mockito.mock(SenderAccountBindingService::class.java)
    private val schedulingProperties = MailSchedulingProperties(
        initialOutreachSendIntervalMs = 0,
        initialOutreachSendJitterMs = 0
    )

    private val service = InitialOutreachService(
        expertSearchService = expertSearchService,
        senderAccountAssignmentService = senderAccountAssignmentService,
        introductionMailComposer = introductionMailComposer,
        mailDeliveryService = mailDeliveryService,
        expertContactRepository = expertContactRepository,
        txHelper = txHelper,
        emailSuppressionService = emailSuppressionService,
        autoReplySettingService = autoReplySettingService,
        schedulingProperties = schedulingProperties,
        senderAccountBindingService = senderAccountBindingService
    )

    private var contactIdSeq = 100L

    @BeforeEach
    fun setUp() {
        contactIdSeq = 100L
        Mockito.`when`(autoReplySettingService.isGlobalEnabled()).thenReturn(true)
        Mockito.`when`(emailSuppressionService.isSuppressed(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(senderAccountBindingService.bindingFieldsFor(Mockito.anyString(), anyValue(LocalDateTime.now())))
            .thenReturn("chen" to LocalDateTime.of(2026, 8, 10, 12, 0, 0))
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java))).thenAnswer { invocation ->
            val contact = invocation.getArgument<ExpertContact>(0)
            if (contact.id == null) contact.copy(id = contactIdSeq++) else contact
        }
    }

    @Test
    fun `sendInitialBatch commits each success independently when later send throws`() {
        val experts = listOf(expert("0001"), expert("0002"), expert("0003"))
        Mockito.`when`(expertSearchService.searchSendableExpertsWithEmail(3, ExpertIndexLevel.CANDIDATE))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 3))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), Mockito.anyString())).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0001")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001")), Mockito.isNull()))
            .thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))
            .thenReturn(DeliveredMail("msg-2", "SENT"))
            .thenThrow(RuntimeException("SMTP timeout"))

        val result = service.sendInitialBatch(campaignId = 1L, size = 3)

        assertEquals(2, result.sent)
        assertEquals(1, result.failed)
        assertEquals(2, result.results.count { it.status == "SENT" })
        assertEquals(1, result.results.count { it.status == "FAILED" })
        Mockito.verify(txHelper, Mockito.times(2)).recordSuccess(
            anyValue(ExpertContact(campaignId = 0L, orcidId = "", expertEmail = "", expertName = null)),
            eqValue("chen"),
            Mockito.anyString(),
            eqValue("Subject"),
            eqValue("Body"),
            eqValue(0L),
            Mockito.isNull()
        )
        Mockito.verify(txHelper, Mockito.never()).recordFailure(
            Mockito.anyLong(),
            Mockito.anyString(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any()
        )
    }

    @Test
    fun `sendInitialBatch all success preserves result semantics`() {
        val experts = listOf(expert("0001"), expert("0002"))
        Mockito.`when`(expertSearchService.searchSendableExpertsWithEmail(2, ExpertIndexLevel.CANDIDATE))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 2))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), Mockito.anyString())).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0001")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001")), Mockito.isNull()))
            .thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))

        val result = service.sendInitialBatch(campaignId = 1L, size = 2)

        assertEquals(2, result.sent)
        assertEquals(0, result.failed)
        assertEquals(2, result.candidates)
        assertEquals(0, result.skipped)
        assertEquals(listOf("SENT", "SENT"), result.results.map { it.status })
        Mockito.verify(txHelper, Mockito.times(2)).recordSuccess(
            anyValue(ExpertContact(campaignId = 0L, orcidId = "", expertEmail = "", expertName = null)),
            eqValue("chen"),
            Mockito.anyString(),
            eqValue("Subject"),
            eqValue("Body"),
            eqValue(0L),
            Mockito.isNull()
        )
    }

    @Test
    fun `sendInitialBatch saves NEW contact before SMTP and records failure without success transition`() {
        val experts = listOf(expert("0001"))
        Mockito.`when`(expertSearchService.searchSendableExpertsWithEmail(1, ExpertIndexLevel.CANDIDATE))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 1))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), eqValue("0001"))).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0001")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001")), Mockito.isNull()))
            .thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg-fail", "FAILED", errorDetail = "550 rejected"))

        val result = service.sendInitialBatch(campaignId = 1L, size = 1)

        assertEquals(0, result.sent)
        assertEquals(1, result.failed)

        val contactCaptor = ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(expertContactRepository).save(captureValue(contactCaptor, ExpertContact(
            campaignId = 0L, orcidId = "", expertEmail = "", expertName = null
        )))
        assertEquals("NEW", contactCaptor.value.currentStatus)
        Mockito.verify(txHelper, Mockito.never()).recordSuccess(
            anyValue(ExpertContact(campaignId = 0L, orcidId = "", expertEmail = "", expertName = null)),
            Mockito.anyString(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.anyLong(),
            Mockito.any()
        )
        Mockito.verify(txHelper).recordFailure(
            contactId = eqValue(100L),
            accountCode = eqValue("chen"),
            messageId = eqValue("msg-fail"),
            errorSummary = eqValue("550 rejected"),
            subject = eqValue("Subject"),
            body = eqValue("Body"),
            attemptId = Mockito.isNull(),
            taskExecutionId = Mockito.isNull()
        )
    }

    @Test
    fun `sendInitialBatch skips suppressed email without send or contact save`() {
        val suppressedExpert = expert("0001").copy(email = "blocked@example.com")
        val normalExpert = expert("0002")
        val experts = listOf(suppressedExpert, normalExpert)
        Mockito.`when`(expertSearchService.searchSendableExpertsWithEmail(2, ExpertIndexLevel.CANDIDATE))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 2))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), Mockito.anyString())).thenReturn(false)
        Mockito.`when`(emailSuppressionService.isSuppressed("blocked@example.com")).thenReturn(true)
        Mockito.`when`(emailSuppressionService.isSuppressed("0002@example.com")).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(normalExpert), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(normalExpert), Mockito.isNull()))
            .thenReturn(ComposedMail("0002@example.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))

        val result = service.sendInitialBatch(campaignId = 1L, size = 2)

        assertEquals(1, result.sent)
        assertEquals(1, result.skipped)
        assertEquals(1, result.results.size)
        Mockito.verify(mailDeliveryService, Mockito.times(1)).send(
            anyValue(account("chen")),
            anyValue(ComposedMail("", "", ""))
        )
        Mockito.verify(expertContactRepository, Mockito.times(1)).save(
            anyValue(ExpertContact(campaignId = 0L, orcidId = "", expertEmail = "", expertName = null))
        )
        Mockito.verify(emailSuppressionService).isSuppressed("blocked@example.com")
    }

    @Test
    fun `sendInitialBatch skips only suppressed emails with zero sends and no FAILED records`() {
        val suppressedExpert1 = expert("0001").copy(email = "blocked1@example.com")
        val suppressedExpert2 = expert("0002").copy(email = "blocked2@example.com")
        val experts = listOf(suppressedExpert1, suppressedExpert2)
        Mockito.`when`(expertSearchService.searchSendableExpertsWithEmail(2, ExpertIndexLevel.CANDIDATE))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 2))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), Mockito.anyString())).thenReturn(false)
        Mockito.`when`(emailSuppressionService.isSuppressed("blocked1@example.com")).thenReturn(true)
        Mockito.`when`(emailSuppressionService.isSuppressed("blocked2@example.com")).thenReturn(true)

        val result = service.sendInitialBatch(campaignId = 1L, size = 2)

        assertEquals(2, result.skipped)
        assertEquals(0, result.sent)
        assertEquals(0, result.failed)
        assertTrue(result.results.none { it.status == "FAILED" })
        // IP-2: 前置检查（:46）先命中 —— 退订邮箱绝不被记为发送失败，也绝不触达投递层。
        Mockito.verify(mailDeliveryService, Mockito.never()).send(
            anyValue(account("chen")),
            anyValue(ComposedMail("", "", ""))
        )
        Mockito.verify(expertContactRepository, Mockito.never()).save(
            anyValue(ExpertContact(campaignId = 0L, orcidId = "", expertEmail = "", expertName = null))
        )
    }

    @Test
    fun `sendInitialBatch counts existing contact and suppression skips separately`() {
        val suppressedExpert = expert("0001").copy(email = "blocked@example.com")
        val existingExpert = expert("0002")
        val experts = listOf(suppressedExpert, existingExpert)
        Mockito.`when`(expertSearchService.searchSendableExpertsWithEmail(2, ExpertIndexLevel.CANDIDATE))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 2))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), eqValue("0002"))).thenReturn(true)
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), eqValue("0001"))).thenReturn(false)
        Mockito.`when`(emailSuppressionService.isSuppressed("blocked@example.com")).thenReturn(true)

        val result = service.sendInitialBatch(campaignId = 1L, size = 2)

        assertEquals(2, result.skipped)
        assertEquals(0, result.sent)
        Mockito.verify(mailDeliveryService, Mockito.never()).send(
            anyValue(account("chen")),
            anyValue(ComposedMail("", "", ""))
        )
        Mockito.verify(expertContactRepository, Mockito.never()).save(
            anyValue(ExpertContact(campaignId = 0L, orcidId = "", expertEmail = "", expertName = null))
        )
    }

    @Test
    fun `sendInitialBatch creates contact with autoReplyEnabled false when global switch off`() {
        Mockito.`when`(autoReplySettingService.isGlobalEnabled()).thenReturn(false)
        val experts = listOf(expert("0001"))
        Mockito.`when`(expertSearchService.searchSendableExpertsWithEmail(1, ExpertIndexLevel.CANDIDATE))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 1))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), eqValue("0001"))).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0001")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001")), Mockito.isNull()))
            .thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))

        service.sendInitialBatch(campaignId = 1L, size = 1)

        val contactCaptor = ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(expertContactRepository).save(captureValue(contactCaptor, ExpertContact(
            campaignId = 0L, orcidId = "", expertEmail = "", expertName = null
        )))
        assertEquals(false, contactCaptor.value.autoReplyEnabled)
    }

    @Test
    fun `sendInitialBatch binds selected account on contact creation`() {
        val experts = listOf(expert("0001"))
        Mockito.`when`(expertSearchService.searchSendableExpertsWithEmail(1, ExpertIndexLevel.CANDIDATE))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 1))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), eqValue("0001"))).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0001")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001")), Mockito.isNull()))
            .thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))
        Mockito.`when`(senderAccountBindingService.bindingFieldsFor(
            eqValue("chen"),
            anyValue(LocalDateTime.now())
        )).thenReturn("chen" to LocalDateTime.of(2026, 8, 10, 9, 30, 0))

        service.sendInitialBatch(campaignId = 1L, size = 1)

        val contactCaptor = ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(expertContactRepository).save(captureValue(contactCaptor, ExpertContact(
            campaignId = 0L, orcidId = "", expertEmail = "", expertName = null
        )))
        assertEquals("chen", contactCaptor.value.boundSenderAccountCode)
        assertNotNull(contactCaptor.value.senderAccountBoundAt)
    }

    @Test
    fun `sendInitialBatch sleeps between successful sends when interval configured`() {
        val intervalService = InitialOutreachService(
            expertSearchService = expertSearchService,
            senderAccountAssignmentService = senderAccountAssignmentService,
            introductionMailComposer = introductionMailComposer,
            mailDeliveryService = mailDeliveryService,
            expertContactRepository = expertContactRepository,
            txHelper = txHelper,
            emailSuppressionService = emailSuppressionService,
            autoReplySettingService = autoReplySettingService,
            schedulingProperties = MailSchedulingProperties(
                initialOutreachSendIntervalMs = 100,
                initialOutreachSendJitterMs = 0
            ),
            senderAccountBindingService = senderAccountBindingService
        )
        val experts = listOf(expert("0001"), expert("0002"))
        Mockito.`when`(expertSearchService.searchSendableExpertsWithEmail(2, ExpertIndexLevel.CANDIDATE))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 2))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), Mockito.anyString())).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0001")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001")), Mockito.isNull()))
            .thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))

        val start = System.currentTimeMillis()
        intervalService.sendInitialBatch(campaignId = 1L, size = 2)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(elapsed >= 100, "Expected at least 100ms delay between sends, got ${elapsed}ms")
    }

    @Test
    fun `loads binding stock once per batch`() {
        val experts = listOf(expert("0001"), expert("0002"), expert("0003"))
        Mockito.`when`(expertSearchService.searchSendableExpertsWithEmail(3, ExpertIndexLevel.CANDIDATE))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 3))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), Mockito.anyString())).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.loadBindingStock())
            .thenReturn(SenderBindingStock(emptyMap(), emptyMap(), emptyMap()))
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0001")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001")), Mockito.isNull()))
            .thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))

        val result = service.sendInitialBatch(campaignId = 1L, size = 3)

        assertEquals(3, result.sent)
        // I-1: 快照在批次开始处取一次，而非每个专家一次
        Mockito.verify(senderAccountAssignmentService, Mockito.times(1)).loadBindingStock()
    }

    @Test
    fun `sendInitialBatch passes taskExecutionId through to txHelper`() {
        val experts = listOf(expert("0001"))
        Mockito.`when`(expertSearchService.searchSendableExpertsWithEmail(1, ExpertIndexLevel.CANDIDATE))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 1))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), Mockito.anyString())).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0001")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001")), Mockito.isNull()))
            .thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))

        service.sendInitialBatch(campaignId = 1L, size = 1, taskExecutionId = 42L)

        Mockito.verify(txHelper).recordSuccess(
            anyValue(ExpertContact(campaignId = 0L, orcidId = "", expertEmail = "", expertName = null)),
            eqValue("chen"),
            Mockito.anyString(),
            eqValue("Subject"),
            eqValue("Body"),
            eqValue(0L),
            eqValue(42L)
        )
    }

    @Test
    fun `sendInitialBatch last-chance gate skips non-sendable expert without contact or delivery (I3-4)`() {
        // 模拟查询/缓存竞态：ES 查询本应排除，但非可发专家仍随结果返回 —— 最后门禁必须兜住。
        val experts = listOf(nonSendableExpert("0001"), expert("0002"))
        Mockito.`when`(expertSearchService.searchSendableExpertsWithEmail(2, ExpertIndexLevel.CANDIDATE))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 2))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), Mockito.anyString())).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0002")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(senderAccountBindingService.bindingFieldsFor(eqValue("chen"), anyValue(LocalDateTime.now())))
            .thenReturn("chen" to LocalDateTime.of(2026, 8, 10, 9, 30, 0))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0002")), Mockito.isNull()))
            .thenReturn(ComposedMail("0002@example.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail(messageId = "msg-2", status = "SENT"))

        val result = service.sendInitialBatch(campaignId = 1L, size = 2)

        assertEquals(2, result.candidates)
        assertEquals(1, result.sent)
        assertEquals(1, result.skipped)
        assertEquals(0, result.failed)
        // 只有 0002 创建 contact；0001 不创建、不渲染、不投递。
        val contactCaptor = ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(expertContactRepository, Mockito.times(1)).save(
            captureValue(contactCaptor, ExpertContact(campaignId = 0L, orcidId = "", expertEmail = "", expertName = null))
        )
        assertEquals("0002", contactCaptor.value.orcidId)
        Mockito.verify(mailDeliveryService, Mockito.times(1)).send(
            anyValue(account("chen")),
            anyValue(ComposedMail("", "", ""))
        )
        Mockito.verify(introductionMailComposer, Mockito.times(1)).compose(
            eqValue("chen"), anyValue(expert("0002")), Mockito.isNull()
        )
    }

    @Test
    fun `sendInitialBatch last-chance gate rejects null classification with zero writes (I3-1)`() {
        val experts = listOf(nonSendableExpert("0001"))
        Mockito.`when`(expertSearchService.searchSendableExpertsWithEmail(1, ExpertIndexLevel.CANDIDATE))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 1))

        val result = service.sendInitialBatch(campaignId = 1L, size = 1)

        assertEquals(1, result.candidates)
        assertEquals(0, result.sent)
        assertEquals(0, result.failed)
        assertEquals(1, result.skipped)
        // 门禁在 existsByCampaignIdAndOrcidId / 选号 / 渲染 / 投递 之前拦截。
        Mockito.verify(expertContactRepository, Mockito.never()).existsByCampaignIdAndOrcidId(Mockito.anyLong(), Mockito.anyString())
        Mockito.verify(expertContactRepository, Mockito.never()).save(
            anyValue(ExpertContact(campaignId = 0L, orcidId = "", expertEmail = "", expertName = null))
        )
        Mockito.verify(senderAccountAssignmentService, Mockito.never()).selectAccount(
            anyValue(expert("")), anyValue(mutableListOf()), Mockito.anyBoolean(), anyValue(SenderBindingStock.EMPTY)
        )
        Mockito.verifyNoInteractions(introductionMailComposer)
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    private fun expert(orcidId: String): ExpertProfile =
        ExpertProfile(
            orcidId = orcidId,
            email = "$orcidId@example.com",
            givenNames = "Given",
            familyNames = "Family",
            country = "China",
            keyword = "keyword",
            employment = "University",
            // I3-1: 默认 fixture 为可发类型；不可发场景用 nonSendableExpert() 显式构造。
            expertClassification = sendableClassification()
        )

    private fun nonSendableExpert(orcidId: String): ExpertProfile =
        expert(orcidId).copy(expertClassification = null)

    private fun sendableClassification(): ExpertClassification =
        ExpertClassification(
            type = ExpertType.PRODUCTION_RND,
            productionScore = 80,
            researchScore = 20,
            positiveEvidence = listOf("RND_PRODUCTION"),
            negativeEvidence = emptyList(),
            version = "rnd-v2-2026",
            sourceFingerprint = "fp-0001",
            classifiedAt = LocalDateTime.of(2026, 8, 1, 12, 0)
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
            imapPassword = "secret",
            todaySentCount = 0,
            lastSentAt = LocalDateTime.now()
        )

    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun <T> captureValue(captor: ArgumentCaptor<T>, defaultValue: T): T {
        captor.capture()
        return defaultValue
    }
}
