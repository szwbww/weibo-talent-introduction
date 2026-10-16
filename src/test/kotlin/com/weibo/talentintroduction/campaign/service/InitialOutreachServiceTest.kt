package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertClassification
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonScope
import com.weibo.talentintroduction.discovery.service.DiscoveryPersonFeatureGate
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.ExpertType
import com.weibo.talentintroduction.expert.domain.IdentityVerification
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.expert.service.ExpertSearchResult
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory
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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
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
        initialOutreachSendJitterMs = 0,
        initialOutreachExpertTypes = listOf("PRODUCTION_RND")
    )
    /** I-1/I-3: 旧首发准入来源 —— 发现档案消费 04 的持久结论。 */
    private val discoveryReviewService =
        Mockito.mock(com.weibo.talentintroduction.discovery.service.DiscoveryReviewService::class.java)
    private val batchRecipientSelectionService = BatchRecipientSelectionService(discoveryReviewService)

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
        senderAccountBindingService = senderAccountBindingService,
        batchRecipientSelectionService = batchRecipientSelectionService
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
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(3, ExpertIndexLevel.CANDIDATE, listOf("PRODUCTION_RND")))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 3))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), Mockito.anyString())).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0001")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001")), Mockito.isNull(), anyBooleanValue()))
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
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.nullable(OutreachGuardTicket::class.java)
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
    fun `sendInitialBatch blocks discovery without institution evidence before contact creation (I-1 I-3)`() {
        val experts = listOf(expert("0001"), expert("0002")).map { it.copy(emailSource = "PAPER_FULLTEXT") }
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(2, ExpertIndexLevel.CANDIDATE, listOf("PRODUCTION_RND")))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 2))

        val result = service.sendInitialBatch(campaignId = 1L, size = 2)

        // 无机构证据的新发现：不建联系人、不选号、不渲染、不投递。
        assertEquals(0, result.sent)
        assertEquals(0, result.failed)
        assertEquals(0, result.candidates)
        assertTrue(result.results.isEmpty())
        Mockito.verify(expertContactRepository, Mockito.never()).save(
            anyValue(ExpertContact(campaignId = 0L, orcidId = "", expertEmail = "", expertName = null))
        )
        Mockito.verify(senderAccountAssignmentService, Mockito.never()).selectAccount(
            anyValue(expert("0001")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)
        )
        Mockito.verifyNoInteractions(introductionMailComposer, mailDeliveryService, txHelper)
    }

    @Test
    fun `sendInitialBatch sends discovery with persistent approval (I-1 I-3)`() {
        val experts = listOf(signedDiscoveryExpert("0001"), signedDiscoveryExpert("0002"))
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(2, ExpertIndexLevel.CANDIDATE, listOf("PRODUCTION_RND")))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 2))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), Mockito.anyString())).thenReturn(false)
        stubAdmissions("0001" to "AUTO_PASSED", "0002" to "MANUAL_APPROVED")
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0001")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001")), Mockito.isNull(), anyBooleanValue()))
            .thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))

        val result = service.sendInitialBatch(campaignId = 1L, size = 2)

        // 三证齐备的新发现不受影响（门禁不得过度拦截）。
        assertEquals(2, result.sent)
        assertEquals(0, result.skipped)
        assertEquals(2, result.candidates)
        Mockito.verify(txHelper, Mockito.times(2)).recordSuccess(
            anyValue(ExpertContact(campaignId = 0L, orcidId = "", expertEmail = "", expertName = null)),
            eqValue("chen"),
            Mockito.anyString(),
            eqValue("Subject"),
            eqValue("Body"),
            eqValue(0L),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.nullable(OutreachGuardTicket::class.java)
        )
    }

    @Test
    fun `sendInitialBatch saves NEW contact before SMTP and records failure without success transition`() {
        val experts = listOf(expert("0001"))
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(1, ExpertIndexLevel.CANDIDATE, listOf("PRODUCTION_RND")))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 1))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), eqValue("0001"))).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0001")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001")), Mockito.isNull(), anyBooleanValue()))
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
            Mockito.any(),
            Mockito.nullable(Long::class.javaObjectType),
            Mockito.nullable(OutreachGuardTicket::class.java)
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
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(2, ExpertIndexLevel.CANDIDATE, listOf("PRODUCTION_RND")))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 2))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), Mockito.anyString())).thenReturn(false)
        Mockito.`when`(emailSuppressionService.isSuppressed("blocked@example.com")).thenReturn(true)
        Mockito.`when`(emailSuppressionService.isSuppressed("0002@example.com")).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(normalExpert), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(normalExpert), Mockito.isNull(), anyBooleanValue()))
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
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(2, ExpertIndexLevel.CANDIDATE, listOf("PRODUCTION_RND")))
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
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(2, ExpertIndexLevel.CANDIDATE, listOf("PRODUCTION_RND")))
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
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(1, ExpertIndexLevel.CANDIDATE, listOf("PRODUCTION_RND")))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 1))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), eqValue("0001"))).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0001")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001")), Mockito.isNull(), anyBooleanValue()))
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
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(1, ExpertIndexLevel.CANDIDATE, listOf("PRODUCTION_RND")))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 1))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), eqValue("0001"))).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0001")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001")), Mockito.isNull(), anyBooleanValue()))
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
                initialOutreachSendJitterMs = 0,
                initialOutreachExpertTypes = listOf("PRODUCTION_RND")
            ),
            senderAccountBindingService = senderAccountBindingService
        )
        val experts = listOf(expert("0001"), expert("0002"))
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(2, ExpertIndexLevel.CANDIDATE, listOf("PRODUCTION_RND")))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 2))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), Mockito.anyString())).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0001")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001")), Mockito.isNull(), anyBooleanValue()))
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
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(3, ExpertIndexLevel.CANDIDATE, listOf("PRODUCTION_RND")))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 3))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), Mockito.anyString())).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.loadBindingStock())
            .thenReturn(SenderBindingStock(emptyMap(), emptyMap(), emptyMap()))
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0001")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001")), Mockito.isNull(), anyBooleanValue()))
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
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(1, ExpertIndexLevel.CANDIDATE, listOf("PRODUCTION_RND")))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 1))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), Mockito.anyString())).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0001")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001")), Mockito.isNull(), anyBooleanValue()))
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
            eqValue(42L),
            Mockito.isNull(),
            Mockito.nullable(OutreachGuardTicket::class.java)
        )
    }

    @Test
    fun `sendInitialBatch last-chance gate skips non-sendable expert without contact or delivery (I3-4)`() {
        // 模拟查询/缓存竞态：ES 查询本应排除，但类型不在配置集合内的专家仍随结果返回 ——
        // 最后门禁必须兜住（I2-5 与查询同口径：null 分类在未配置 UNCLASSIFIED 时被跳过）。
        val experts = listOf(nonSendableExpert("0001"), expert("0002"))
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(2, ExpertIndexLevel.CANDIDATE, listOf("PRODUCTION_RND")))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 2))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), Mockito.anyString())).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0002")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(senderAccountBindingService.bindingFieldsFor(eqValue("chen"), anyValue(LocalDateTime.now())))
            .thenReturn("chen" to LocalDateTime.of(2026, 8, 10, 9, 30, 0))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0002")), Mockito.isNull(), anyBooleanValue()))
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
            eqValue("chen"), anyValue(expert("0002")), Mockito.isNull(), anyBooleanValue()
        )
    }

    @Test
    fun `sendInitialBatch last-chance gate rejects null classification with zero writes (I3-1)`() {
        // I2-5：配置未含 UNCLASSIFIED 时，null 分类 = 类型不存在 → 跳过；门禁在其余逻辑之前拦截。
        val experts = listOf(nonSendableExpert("0001"))
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(1, ExpertIndexLevel.CANDIDATE, listOf("PRODUCTION_RND")))
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

    @Test
    fun `sendInitialBatch last-chance gate skips expert outside configured types and accepts matching type (I2-5)`() {
        // 竞态场景：ES 查询本应排除，但类型不在配置集合内的专家仍随结果返回 —— 最后门禁必须兜住
        // （I2-5 同口径：类型判定取代旧的 sendable/version 门禁，版本不再是判据）。
        val outOfScope = expert("0001").copy(expertClassification = sendableClassification().copy(type = ExpertType.OUT_OF_SCOPE))
        val experts = listOf(
            outOfScope,
            expert("0002")
        )
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(2, ExpertIndexLevel.CANDIDATE, listOf("PRODUCTION_RND")))
            .thenReturn(ExpertSearchResult(experts = experts, totalHits = 2))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), Mockito.anyString())).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0002")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(senderAccountBindingService.bindingFieldsFor(eqValue("chen"), anyValue(LocalDateTime.now())))
            .thenReturn("chen" to LocalDateTime.of(2026, 8, 10, 9, 30, 0))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0002")), Mockito.isNull(), anyBooleanValue()))
            .thenReturn(ComposedMail("0002@example.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail(messageId = "msg-2", status = "SENT"))

        val result = service.sendInitialBatch(campaignId = 1L, size = 2)

        assertEquals(2, result.candidates)
        assertEquals(1, result.sent)
        assertEquals(1, result.skipped)
        assertEquals(0, result.failed)
        // 只有 0002 创建 contact；0001（类型不在配置集合内）不创建、不渲染、不投递。
        val contactCaptor = ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(expertContactRepository, Mockito.times(1)).save(
            captureValue(contactCaptor, ExpertContact(campaignId = 0L, orcidId = "", expertEmail = "", expertName = null))
        )
        assertEquals("0002", contactCaptor.value.orcidId)
        Mockito.verify(mailDeliveryService, Mockito.times(1)).send(
            anyValue(account("chen")),
            anyValue(ComposedMail("", "", ""))
        )
    }

    // ──── I2: 旧首发链路显式类型集合（child 02） ─────────────────────────────

    @Test
    fun `sendInitialBatch pages past uninitialized discovery and sends the next page (I-1 I-3)`() {
        val blocked = expert("0001").copy(emailSource = "PAPER_FULLTEXT")
        val eligible = signedDiscoveryExpert("0002")
        val all = listOf(blocked, eligible)
        stubAdmissions("0002" to "AUTO_PASSED")
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(
            anyValue(1), anyValue(ExpertIndexLevel.CANDIDATE), anyValue(listOf("PRODUCTION_RND")), anyInt()
        )).thenAnswer { invocation ->
            val size = invocation.getArgument<Int>(0)
            val from = invocation.getArgument<Int>(3)
            ExpertSearchResult(experts = all.drop(from).take(size), totalHits = all.size.toLong())
        }
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), Mockito.anyString())).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0002")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0002")), Mockito.isNull(), anyBooleanValue()))
            .thenReturn(ComposedMail("0002@example.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg-2", "SENT"))

        val result = service.sendInitialBatch(campaignId = 1L, size = 1)

        // 第一页（唯一一名无证据新发现）被整页过滤后必须继续取下一 offset，并为第二页专家建联系人。
        assertEquals(1, result.sent)
        assertEquals(0, result.skipped)
        assertEquals(1, result.candidates)
        val contactCaptor = ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(expertContactRepository).save(captureValue(contactCaptor, ExpertContact(
            campaignId = 0L, orcidId = "", expertEmail = "", expertName = null
        )))
        assertEquals("0002", contactCaptor.value.orcidId, "被过滤页不得建联系人")
        Mockito.verify(expertSearchService).searchExpertsByTypesWithEmail(
            anyValue(1), anyValue(ExpertIndexLevel.CANDIDATE), anyValue(listOf("PRODUCTION_RND")), eqValue(1)
        )
    }

    @Test
    fun `sendInitialBatch fails fast when initialOutreachExpertTypes not configured (I2-2)`() {
        val unconfiguredService = InitialOutreachService(
            expertSearchService = expertSearchService,
            senderAccountAssignmentService = senderAccountAssignmentService,
            introductionMailComposer = introductionMailComposer,
            mailDeliveryService = mailDeliveryService,
            expertContactRepository = expertContactRepository,
            txHelper = txHelper,
            emailSuppressionService = emailSuppressionService,
            autoReplySettingService = autoReplySettingService,
            schedulingProperties = MailSchedulingProperties(),
            senderAccountBindingService = senderAccountBindingService
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            unconfiguredService.sendInitialBatch(campaignId = 1L, size = 1)
        }
        assertTrue(ex.message!!.contains("initial-outreach-expert-types"), "I2-2: message must name the missing config: ${ex.message}")
        // 快速失败发生在取目标之前 —— 绝不触碰查询/投递层。
        Mockito.verify(expertSearchService, Mockito.never()).searchExpertsByTypesWithEmail(
            anyValue(0), anyValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList()), anyValue(0)
        )
    }

    @Test
    fun `sendInitialBatch passes configured expert types verbatim to search (I2-2)`() {
        val academicService = InitialOutreachService(
            expertSearchService = expertSearchService,
            senderAccountAssignmentService = senderAccountAssignmentService,
            introductionMailComposer = introductionMailComposer,
            mailDeliveryService = mailDeliveryService,
            expertContactRepository = expertContactRepository,
            txHelper = txHelper,
            emailSuppressionService = emailSuppressionService,
            autoReplySettingService = autoReplySettingService,
            schedulingProperties = MailSchedulingProperties(initialOutreachExpertTypes = listOf("ACADEMIC_RND")),
            senderAccountBindingService = senderAccountBindingService
        )
        val academic = expert("0001").copy(expertClassification = sendableClassification().copy(type = ExpertType.ACADEMIC_RND))
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(1, ExpertIndexLevel.CANDIDATE, listOf("ACADEMIC_RND")))
            .thenReturn(ExpertSearchResult(experts = listOf(academic), totalHits = 1))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), eqValue("0001"))).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(academic), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(academic), Mockito.isNull(), anyBooleanValue()))
            .thenReturn(ComposedMail("0001@example.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))

        val result = academicService.sendInitialBatch(campaignId = 1L, size = 1)

        assertEquals(1, result.sent)
        assertEquals(0, result.skipped)
        // 第三个参数逐字为配置列表，分页起点默认 0。
        Mockito.verify(expertSearchService).searchExpertsByTypesWithEmail(
            eqValue(1), eqValue(ExpertIndexLevel.CANDIDATE), eqValue(listOf("ACADEMIC_RND")), eqValue(0)
        )
    }

    @Test
    fun `sendInitialBatch skips OUT_OF_SCOPE unless explicitly configured (I2-5)`() {
        val outOfScope = expert("0001").copy(expertClassification = sendableClassification().copy(type = ExpertType.OUT_OF_SCOPE))

        val academicService = InitialOutreachService(
            expertSearchService = expertSearchService,
            senderAccountAssignmentService = senderAccountAssignmentService,
            introductionMailComposer = introductionMailComposer,
            mailDeliveryService = mailDeliveryService,
            expertContactRepository = expertContactRepository,
            txHelper = txHelper,
            emailSuppressionService = emailSuppressionService,
            autoReplySettingService = autoReplySettingService,
            schedulingProperties = MailSchedulingProperties(initialOutreachExpertTypes = listOf("ACADEMIC_RND")),
            senderAccountBindingService = senderAccountBindingService
        )
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(1, ExpertIndexLevel.CANDIDATE, listOf("ACADEMIC_RND")))
            .thenReturn(ExpertSearchResult(experts = listOf(outOfScope), totalHits = 1))

        val skippedResult = academicService.sendInitialBatch(campaignId = 1L, size = 1)

        assertEquals(1, skippedResult.skipped)
        assertEquals(0, skippedResult.sent)
        // 门禁在其余逻辑之前拦截：不查重复、不选号、不渲染、不投递。
        Mockito.verify(expertContactRepository, Mockito.never()).existsByCampaignIdAndOrcidId(Mockito.anyLong(), Mockito.anyString())
        Mockito.verifyNoInteractions(introductionMailComposer)
        Mockito.verify(mailDeliveryService, Mockito.never()).send(
            anyValue(account("chen")),
            anyValue(ComposedMail("", "", ""))
        )

        val outOfScopeService = InitialOutreachService(
            expertSearchService = expertSearchService,
            senderAccountAssignmentService = senderAccountAssignmentService,
            introductionMailComposer = introductionMailComposer,
            mailDeliveryService = mailDeliveryService,
            expertContactRepository = expertContactRepository,
            txHelper = txHelper,
            emailSuppressionService = emailSuppressionService,
            autoReplySettingService = autoReplySettingService,
            schedulingProperties = MailSchedulingProperties(initialOutreachExpertTypes = listOf("OUT_OF_SCOPE")),
            senderAccountBindingService = senderAccountBindingService
        )
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(1, ExpertIndexLevel.CANDIDATE, listOf("OUT_OF_SCOPE")))
            .thenReturn(ExpertSearchResult(experts = listOf(outOfScope), totalHits = 1))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), eqValue("0001"))).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(outOfScope), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(outOfScope), Mockito.isNull(), anyBooleanValue()))
            .thenReturn(ComposedMail("0001@example.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))

        val sentResult = outOfScopeService.sendInitialBatch(campaignId = 1L, size = 1)

        // 医学越界类型被显式选中即被发送 —— 证明不再有隐式 sendable 门禁（M-1 / I2-5）。
        assertEquals(0, sentResult.skipped)
        assertEquals(1, sentResult.sent)
    }

    @Test
    fun `sendInitialBatch sends unclassified expert only when UNCLASSIFIED configured (I2-5)`() {
        val unclassified = nonSendableExpert("0001") // expertClassification = null

        val academicService = InitialOutreachService(
            expertSearchService = expertSearchService,
            senderAccountAssignmentService = senderAccountAssignmentService,
            introductionMailComposer = introductionMailComposer,
            mailDeliveryService = mailDeliveryService,
            expertContactRepository = expertContactRepository,
            txHelper = txHelper,
            emailSuppressionService = emailSuppressionService,
            autoReplySettingService = autoReplySettingService,
            schedulingProperties = MailSchedulingProperties(initialOutreachExpertTypes = listOf("ACADEMIC_RND")),
            senderAccountBindingService = senderAccountBindingService
        )
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(1, ExpertIndexLevel.CANDIDATE, listOf("ACADEMIC_RND")))
            .thenReturn(ExpertSearchResult(experts = listOf(unclassified), totalHits = 1))

        val skippedResult = academicService.sendInitialBatch(campaignId = 1L, size = 1)

        assertEquals(1, skippedResult.skipped)
        assertEquals(0, skippedResult.sent)

        val unclassifiedService = InitialOutreachService(
            expertSearchService = expertSearchService,
            senderAccountAssignmentService = senderAccountAssignmentService,
            introductionMailComposer = introductionMailComposer,
            mailDeliveryService = mailDeliveryService,
            expertContactRepository = expertContactRepository,
            txHelper = txHelper,
            emailSuppressionService = emailSuppressionService,
            autoReplySettingService = autoReplySettingService,
            schedulingProperties = MailSchedulingProperties(initialOutreachExpertTypes = listOf("UNCLASSIFIED")),
            senderAccountBindingService = senderAccountBindingService
        )
        Mockito.`when`(expertSearchService.searchExpertsByTypesWithEmail(1, ExpertIndexLevel.CANDIDATE, listOf("UNCLASSIFIED")))
            .thenReturn(ExpertSearchResult(experts = listOf(unclassified), totalHits = 1))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), eqValue("0001"))).thenReturn(false)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(unclassified), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(unclassified), Mockito.isNull(), anyBooleanValue()))
            .thenReturn(ComposedMail("0001@example.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))

        val sentResult = unclassifiedService.sendInitialBatch(campaignId = 1L, size = 1)

        assertEquals(0, sentResult.skipped)
        assertEquals(1, sentResult.sent)
    }

    @Test
    fun `sendInitialBatch selects one address per person across ES pages (I-1 I-2)`() {
        // 同一人物 P1 的两个邮箱成员同页出现 —— 只应产生一个目标、一个地址。
        val d1 = discoveredExpert("D1")
        val d2 = discoveredExpert("D2")
        val personRepo = Mockito.mock(com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository::class.java)
        val m1 = personMember(d1, "P1")
        val m2 = personMember(d2, "P1")
        Mockito.`when`(personRepo.findPersonIdsByDocIds(Mockito.anyCollection<String>())).thenReturn(mapOf("D1" to "P1", "D2" to "P1"))
        Mockito.`when`(personRepo.findMembersByPerson("P1")).thenReturn(listOf(m1, m2))
        Mockito.`when`(personRepo.findMember("D1")).thenReturn(m1)
        Mockito.`when`(personRepo.findMember("D2")).thenReturn(m2)
        // V-5：人物成员的当前事实按层从 ES 重读（页面快照不再被当作当前档案）。
        stubCurrentMemberProfiles(listOf(d1, d2))
        stubStrictLayerReads()
        stubAdmissions("D1" to "AUTO_PASSED", "D2" to "AUTO_PASSED")
        val personRecipientService = DiscoveryPersonRecipientService(
            batchRecipientSelectionService = batchRecipientSelectionService,
            discoveryPersonRepository = personRepo,
            expertSearchService = expertSearchService,
            emailSuppressionService = emailSuppressionService,
            expertContactRepository = expertContactRepository,
            // I-3（R-1）：总开关默认关闭；本用例显式开启以覆盖人物归并的**启用路径**。
            featureGate = DiscoveryPersonFeatureGate(enabled = true)
        )
        val personAwareService = InitialOutreachService(
            expertSearchService = expertSearchService,
            senderAccountAssignmentService = senderAccountAssignmentService,
            introductionMailComposer = introductionMailComposer,
            mailDeliveryService = mailDeliveryService,
            expertContactRepository = expertContactRepository,
            txHelper = txHelper,
            emailSuppressionService = emailSuppressionService,
            autoReplySettingService = autoReplySettingService,
            schedulingProperties = schedulingProperties,
            senderAccountBindingService = senderAccountBindingService,
            batchRecipientSelectionService = batchRecipientSelectionService,
            discoveryPersonRecipientService = personRecipientService
        )
        Mockito.`when`(
            expertSearchService.searchExpertsByTypesWithEmail(
                anyValue(2), anyValue(ExpertIndexLevel.CANDIDATE), anyValue(listOf("PRODUCTION_RND")), anyInt()
            )
        ).thenAnswer { invocation ->
            val from = invocation.getArgument<Int>(3)
            if (from == 0) ExpertSearchResult(experts = listOf(d1, d2), totalHits = 2)
            else ExpertSearchResult(experts = emptyList(), totalHits = 2)
        }
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), Mockito.anyString())).thenReturn(false)
        Mockito.`when`(
            senderAccountAssignmentService.selectAccount(
                anyValue(expert("0001")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)
            )
        ).thenReturn(account("chen"))
        Mockito.`when`(
            introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001")), Mockito.isNull(), anyBooleanValue())
        ).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))

        val result = personAwareService.sendInitialBatch(campaignId = 1L, size = 2)

        assertEquals(1, result.candidates, "同人只计一个目标")
        assertEquals(1, result.sent)
        assertEquals("D1", result.results.single().orcidId)
        // 同一人物只建一个联系人（未选中的另一成员零副作用）。
        Mockito.verify(expertContactRepository, Mockito.times(1)).save(
            anyValue(ExpertContact(campaignId = 0L, orcidId = "", expertEmail = "", expertName = null))
        )
    }

    @Test
    fun `sendInitialBatch consults the person-level guard for a discovered introduction (I-1 I-2 I-3)`() {
        val d1 = signedDiscoveryExpert("D1")
        val personRepo = Mockito.mock(com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository::class.java)
        val m1 = personMember(d1, "P1")
        Mockito.`when`(personRepo.findPersonIdsByDocIds(Mockito.anyCollection<String>())).thenReturn(mapOf("D1" to "P1"))
        Mockito.`when`(personRepo.findMembersByPerson("P1")).thenReturn(listOf(m1))
        Mockito.`when`(personRepo.findMember("D1")).thenReturn(m1)
        // V-5：人物成员的当前事实按层从 ES 重读。
        stubCurrentMemberProfiles(listOf(d1))
        stubStrictLayerReads()
        val personRecipientService = DiscoveryPersonRecipientService(
            batchRecipientSelectionService = batchRecipientSelectionService,
            discoveryPersonRepository = personRepo,
            expertSearchService = expertSearchService,
            emailSuppressionService = emailSuppressionService,
            expertContactRepository = expertContactRepository,
            // I-3（R-1）：总开关默认关闭；本用例显式开启以覆盖人物归并的**启用路径**。
            featureGate = DiscoveryPersonFeatureGate(enabled = true)
        )
        val guard = Mockito.mock(DiscoveryPersonOutreachGuardService::class.java)
        val guardedService = InitialOutreachService(
            expertSearchService = expertSearchService,
            senderAccountAssignmentService = senderAccountAssignmentService,
            introductionMailComposer = introductionMailComposer,
            mailDeliveryService = mailDeliveryService,
            expertContactRepository = expertContactRepository,
            txHelper = txHelper,
            emailSuppressionService = emailSuppressionService,
            autoReplySettingService = autoReplySettingService,
            schedulingProperties = schedulingProperties,
            senderAccountBindingService = senderAccountBindingService,
            batchRecipientSelectionService = batchRecipientSelectionService,
            discoveryPersonRecipientService = personRecipientService,
            outreachGuardService = guard,
            // V-1：总开关开启时对所有 discovered 目标启用门禁（映射缺失即 fail-closed）。
            discoveryPersonFeatureGate = DiscoveryPersonFeatureGate(enabled = true)
        )
        Mockito.`when`(
            expertSearchService.searchExpertsByTypesWithEmail(1, ExpertIndexLevel.CANDIDATE, listOf("PRODUCTION_RND"))
        ).thenReturn(ExpertSearchResult(experts = listOf(d1), totalHits = 1))
        stubAdmissions("D1" to "AUTO_PASSED")
        Mockito.`when`(expertContactRepository.findByOrcidIdIn(Mockito.anyList())).thenReturn(emptyList())
        // 人物级门禁拒绝（同人已在途）—— 绝不建 contact、绝不发 SMTP。
        Mockito.`when`(
            guard.reserveIntroduction(
                anyValue("D1"), anyValue("D1@example.com"), anyValue(d1), anyValue(OutreachAttemptIdentity())
            )
        ).thenReturn(OutreachReserveOutcome.Denied(OutreachGuardReasonCodes.IN_FLIGHT))

        val result = guardedService.sendInitialBatch(campaignId = 1L, size = 1)

        assertEquals(0, result.sent)
        assertEquals(0, result.failed)
        assertEquals(1, result.skipped)
        Mockito.verify(guard).reserveIntroduction(
            anyValue("D1"), anyValue("D1@example.com"), anyValue(d1), anyValue(OutreachAttemptIdentity())
        )
        Mockito.verify(expertContactRepository, Mockito.never()).save(
            anyValue(ExpertContact(campaignId = 0L, orcidId = "", expertEmail = "", expertName = null))
        )
        Mockito.verifyNoInteractions(introductionMailComposer, mailDeliveryService, txHelper)
    }

    // ── V-1 默认关闭总开关不改变旧发送行为 ───────────────────────────────────

    @Test
    fun `gate off sends an unmapped discovered candidate without touching the guard (V-1)`() {
        val d1 = signedDiscoveryExpert("D1")
        val guard = Mockito.mock(DiscoveryPersonOutreachGuardService::class.java)
        Mockito.`when`(guard.isKnownPerson("D1")).thenReturn(false)
        stubSingleDiscoverySend(d1)
        stubAdmissions("D1" to "AUTO_PASSED")
        val svc = personGuardedService(guard, gateEnabled = false, personRepo = singleMemberPersonRepo(d1), memberProfiles = listOf(d1))

        val result = svc.sendInitialBatch(campaignId = 1L, size = 1)

        // 未开启且无人物保护：逐字保持旧行为 —— 正常发送，guard 只被读一次映射、无任何占位/状态写入。
        assertEquals(1, result.sent)
        assertEquals(0, result.skipped)
        assertEquals(0, result.failed)
        Mockito.verify(mailDeliveryService, Mockito.times(1))
            .send(anyValue(account("chen")), anyValue(ComposedMail("", "", "")))
        Mockito.verify(guard).isKnownPerson("D1")
        Mockito.verifyNoMoreInteractions(guard)
    }

    @Test
    fun `gate off still denies a mapped candidate that carries person protection (V-1)`() {
        val d1 = signedDiscoveryExpert("D1")
        val guard = Mockito.mock(DiscoveryPersonOutreachGuardService::class.java)
        // 已登记到人物（存在映射/保护行）：关闭总开关不得撤销首发保护。
        Mockito.`when`(guard.isKnownPerson("D1")).thenReturn(true)
        Mockito.`when`(
            guard.reserveIntroduction(
                anyValue("D1"), anyValue("D1@example.com"), anyValue(d1), anyValue(OutreachAttemptIdentity())
            )
        ).thenReturn(OutreachReserveOutcome.Denied(OutreachGuardReasonCodes.UNKNOWN_PENDING))
        stubSingleDiscoverySend(d1)
        stubAdmissions("D1" to "AUTO_PASSED")
        val svc = personGuardedService(guard, gateEnabled = false, personRepo = singleMemberPersonRepo(d1), memberProfiles = listOf(d1))

        val result = svc.sendInitialBatch(campaignId = 1L, size = 1)

        assertEquals(0, result.sent)
        assertEquals(1, result.skipped)
        Mockito.verify(mailDeliveryService, Mockito.never())
            .send(anyValue(account("chen")), anyValue(ComposedMail("", "", "")))
        Mockito.verify(expertContactRepository, Mockito.never()).save(
            anyValue(ExpertContact(campaignId = 0L, orcidId = "", expertEmail = "", expertName = null))
        )
    }

    // ── V-26 正常首发不被自己的 contact 行阻断 ───────────────────────────────

    @Test
    fun `healthy first send reaches SMTP once with this attempt's own contact identity declared (V-26)`() {
        val d1 = signedDiscoveryExpert("D1")
        val guard = Mockito.mock(DiscoveryPersonOutreachGuardService::class.java)
        val ticket = OutreachGuardTicket(
            personId = "P1", mailType = "INTRODUCTION", revision = 1L,
            ownerToken = "token", selectedDocId = "D1", recipient = "D1@example.com"
        )
        Mockito.`when`(
            guard.reserveIntroduction(
                anyValue("D1"), anyValue("D1@example.com"), anyValue(d1), anyValue(OutreachAttemptIdentity())
            )
        ).thenReturn(OutreachReserveOutcome.Granted(ticket))
        // 模拟 guard 的 claim 历史复核：只有调用方声明了本次自己的 contact 行（建行 id=100）才获准。
        Mockito.`when`(
            guard.claimSending(
                anyValue(ticket), anyValue(d1), anyValue("D1@example.com"), anyValue(OutreachAttemptIdentity())
            )
        ).thenAnswer { invocation ->
            val attempt = invocation.getArgument<OutreachAttemptIdentity>(3)
            if (attempt.contactId == 100L) OutreachClaimOutcome.Claimed
            else OutreachClaimOutcome.Denied(OutreachGuardReasonCodes.HISTORY_PROTECTED)
        }
        stubSingleDiscoverySend(d1)
        stubAdmissions("D1" to "AUTO_PASSED")
        val svc = personGuardedService(guard, gateEnabled = true, personRepo = singleMemberPersonRepo(d1), memberProfiles = listOf(d1))

        val result = svc.sendInitialBatch(campaignId = 1L, size = 1)

        assertEquals(1, result.candidates)
        assertEquals(1, result.sent)
        assertEquals(0, result.skipped)
        assertEquals(0, result.failed)
        Mockito.verify(mailDeliveryService, Mockito.times(1))
            .send(anyValue(account("chen")), anyValue(ComposedMail("", "", "")))
        Mockito.verify(txHelper).recordSuccess(
            anyValue(ExpertContact(campaignId = 0L, orcidId = "", expertEmail = "", expertName = null)),
            eqValue("chen"),
            Mockito.anyString(),
            eqValue("Subject"),
            eqValue("Body"),
            eqValue(0L),
            Mockito.isNull(),
            Mockito.isNull(),
            anyValue(ticket)
        )
    }

    // ── V-8 SMTP 结果分类 ────────────────────────────────────────────────────

    @Test
    fun `accepted-then-lost connection (250 SUCCESS) marks the guard unknown instead of safe retry (V-8)`() {
        val (guard, ticket) = stubGuardedFailedDelivery(
            DeliveredMail("msg-accepted", "FAILED", SmtpErrorCategory.SUCCESS, 250, "queued, connection then lost")
        )

        Mockito.verify(guard).markUnknown(anyValue(ticket))
        Mockito.verify(guard, Mockito.never()).markSafeRetry(anyValue(ticket))
    }

    @Test
    fun `explicit pre-acceptance rejection marks the guard safe retry (V-8)`() {
        val (guard, ticket) = stubGuardedFailedDelivery(
            DeliveredMail("msg-rejected", "FAILED", SmtpErrorCategory.PERMANENT, 550, "recipient rejected")
        )

        Mockito.verify(guard).markSafeRetry(anyValue(ticket))
        Mockito.verify(guard, Mockito.never()).markUnknown(anyValue(ticket))
    }

    @Test
    fun `network failure without a reply code still marks the guard unknown (V-8)`() {
        val (guard, ticket) = stubGuardedFailedDelivery(
            DeliveredMail("msg-net", "FAILED", SmtpErrorCategory.TRANSIENT, null, "network error")
        )

        Mockito.verify(guard).markUnknown(anyValue(ticket))
        Mockito.verify(guard, Mockito.never()).markSafeRetry(anyValue(ticket))
    }

    /** V-8：一次受门禁约束的失败投递，返回本用例的 guard mock 与本次占位凭据。 */
    private fun stubGuardedFailedDelivery(
        delivered: DeliveredMail
    ): Pair<DiscoveryPersonOutreachGuardService, OutreachGuardTicket> {
        val d1 = signedDiscoveryExpert("D1")
        val guard = Mockito.mock(DiscoveryPersonOutreachGuardService::class.java)
        val ticket = OutreachGuardTicket(
            personId = "P1", mailType = "INTRODUCTION", revision = 1L,
            ownerToken = "token", selectedDocId = "D1", recipient = "D1@example.com"
        )
        Mockito.`when`(
            guard.reserveIntroduction(
                anyValue("D1"), anyValue("D1@example.com"), anyValue(d1), anyValue(OutreachAttemptIdentity())
            )
        ).thenReturn(OutreachReserveOutcome.Granted(ticket))
        Mockito.`when`(
            guard.claimSending(
                anyValue(ticket), anyValue(d1), anyValue("D1@example.com"), anyValue(OutreachAttemptIdentity())
            )
        ).thenReturn(OutreachClaimOutcome.Claimed)
        stubSingleDiscoverySend(d1)
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(delivered)
        stubAdmissions("D1" to "AUTO_PASSED")
        val svc = personGuardedService(guard, gateEnabled = true, personRepo = singleMemberPersonRepo(d1), memberProfiles = listOf(d1))

        val result = svc.sendInitialBatch(campaignId = 1L, size = 1)

        assertEquals(1, result.failed)
        assertEquals(0, result.sent)
        return guard to ticket
    }

    /** V-1/V-26/V-8：带人物级 guard 与总开关的 service（人物映射由 [personRepo] 提供）。 */
    private fun personGuardedService(
        guard: DiscoveryPersonOutreachGuardService,
        gateEnabled: Boolean,
        personRepo: com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository,
        memberProfiles: List<ExpertProfile>
    ): InitialOutreachService {
        // V-5：人物成员的当前事实按层从 ES 重读（页面快照不再被当作当前档案）。
        stubCurrentMemberProfiles(memberProfiles)
        stubStrictLayerReads()
        val personRecipientService = DiscoveryPersonRecipientService(
            batchRecipientSelectionService = batchRecipientSelectionService,
            discoveryPersonRepository = personRepo,
            expertSearchService = expertSearchService,
            emailSuppressionService = emailSuppressionService,
            expertContactRepository = expertContactRepository,
            featureGate = DiscoveryPersonFeatureGate(enabled = gateEnabled)
        )
        return InitialOutreachService(
            expertSearchService = expertSearchService,
            senderAccountAssignmentService = senderAccountAssignmentService,
            introductionMailComposer = introductionMailComposer,
            mailDeliveryService = mailDeliveryService,
            expertContactRepository = expertContactRepository,
            txHelper = txHelper,
            emailSuppressionService = emailSuppressionService,
            autoReplySettingService = autoReplySettingService,
            schedulingProperties = schedulingProperties,
            senderAccountBindingService = senderAccountBindingService,
            batchRecipientSelectionService = batchRecipientSelectionService,
            discoveryPersonRecipientService = personRecipientService,
            outreachGuardService = guard,
            discoveryPersonFeatureGate = DiscoveryPersonFeatureGate(enabled = gateEnabled)
        )
    }

    private fun singleMemberPersonRepo(
        profile: ExpertProfile,
        personId: String = "P1"
    ): com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository {
        val repo = Mockito.mock(com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository::class.java)
        val docId = profile.esDocId ?: profile.orcidId
        val member = personMember(profile, personId)
        Mockito.`when`(repo.findPersonIdsByDocIds(Mockito.anyCollection<String>())).thenReturn(mapOf(docId to personId))
        Mockito.`when`(repo.findMembersByPerson(personId)).thenReturn(listOf(member))
        Mockito.`when`(repo.findMember(docId)).thenReturn(member)
        return repo
    }

    /** V-5：成员档案按层从 ES `_mget` 读取（当前事实），非本批快照。 */
    /** R-3：人物调用链经严格批读边界读取当前层；测试替身按同一数据复现（失败由独立用例覆盖）。 */
    private fun stubStrictLayerReads() {
        Mockito.`when`(
            expertSearchService.findByDocumentIdsStrict(anyIndexLevel(), Mockito.anyList())
        ).thenAnswer { invocation ->
            expertSearchService.findByDocumentIds(
                invocation.getArgument(0), invocation.getArgument(1)
            )
        }
    }

    private fun stubCurrentMemberProfiles(profiles: List<ExpertProfile>) {
        Mockito.`when`(expertSearchService.findByDocumentIds(anyIndexLevel(), Mockito.anyList()))
            .thenAnswer { invocation ->
                val ids = invocation.getArgument<List<String>>(1)
                profiles.filter { (it.esDocId ?: it.orcidId) in ids }
            }
    }

    private fun anyIndexLevel(): ExpertIndexLevel =
        Mockito.any(ExpertIndexLevel::class.java) ?: ExpertIndexLevel.CANDIDATE

    /** 单个 discovered 候选的 ES/选号/渲染 stub（投递结果由调用方覆盖）。 */
    private fun stubSingleDiscoverySend(d1: ExpertProfile) {
        Mockito.`when`(
            expertSearchService.searchExpertsByTypesWithEmail(1, ExpertIndexLevel.CANDIDATE, listOf("PRODUCTION_RND"))
        ).thenReturn(ExpertSearchResult(experts = listOf(d1), totalHits = 1))
        Mockito.`when`(expertContactRepository.existsByCampaignIdAndOrcidId(eqValue(1L), anyValue("D1"))).thenReturn(false)
        Mockito.`when`(expertContactRepository.findByOrcidIdIn(Mockito.anyList())).thenReturn(emptyList())
        Mockito.`when`(
            senderAccountAssignmentService.selectAccount(
                anyValue(expert("0001")), anyValue(mutableListOf()), eqValue(false), anyValue(SenderBindingStock.EMPTY)
            )
        ).thenReturn(account("chen"))
        Mockito.`when`(
            introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001")), Mockito.isNull(), anyBooleanValue())
        ).thenReturn(ComposedMail("D1@example.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))
    }

    private fun personMember(profile: ExpertProfile, personId: String) =
        com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberRecord(
            expertDocId = profile.esDocId ?: profile.orcidId,
            personId = personId,
            normalizedEmail = com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
                .normalizedEmail(profile.email).takeIf { it.isNotEmpty() },
            // V-5：登记指纹按「当前档案事实」签发（docId + 规范邮箱 + 规范全名 + 作者键），与 ES 同源。
            identityFingerprint = com.weibo.talentintroduction.discovery.domain.DiscoveryPersonIdentity.fingerprint(
                profile.esDocId ?: profile.orcidId,
                com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.normalizedEmail(profile.email)
                    .takeIf { it.isNotEmpty() },
                profile.givenNames,
                profile.familyNames,
                com.weibo.talentintroduction.discovery.domain.DiscoveryPersonIdentity.authorKeys(listOf(profile))
            ),
            evidenceJson = null,
            createdAt = LocalDateTime.of(2026, 8, 1, 12, 0),
            updatedAt = LocalDateTime.of(2026, 8, 1, 12, 0)
        )

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

    /**
     * 03：三证齐备的新发现档案 —— 主键 / `externalIds` / 身份凭证逐字一致，机构与证据 token 由
     * 02 的唯一签发函数产出（发送门禁读同一份验签口径）。
     */
    private fun signedDiscoveryExpert(orcidId: String): ExpertProfile {
        val externalIds = """{"pmcId":"PMC-$orcidId","doi":"10.0/$orcidId","orcid":"$orcidId","openAlexAuthorId":"A-$orcidId"}"""
        val base = expert(orcidId).copy(
            institution = "Institute $orcidId",
            externalIds = externalIds,
            identityVerification = IdentityVerification(
                status = "VERIFIED",
                version = DiscoveryIdentity.VERSION,
                email = "${orcidId}@example.com",
                givenNames = "Given",
                familyNames = "Family",
                source = "JATS_SHA256",
                evidenceHash = "a".repeat(64),
                orcid = orcidId,
                openAlexAuthorId = "A-$orcidId"
            )
        )
        val token = DiscoveryIdentity.institutionEvidence(base, DiscoveryIdentity.EVIDENCE_SOURCE_JATS)
            ?: error("fixture 必须能签发机构证据 token: $orcidId")
        return base.copy(
            institutionEvidence = token,
            filterResult = "PASSED",
            // V-5：人物成员的当前档案必须能复现实时 discovered 范围（来源证据 + 字面标签）。
            tags = listOf(DiscoveryPersonScope.DISCOVERED_TAG)
        )
    }

    /** V-5：可复现实时 discovered 范围的人物成员档案（发现来源 + 字面标签）。 */
    private fun discoveredExpert(orcidId: String): ExpertProfile =
        expert(orcidId).copy(
            emailSource = "PAPER_FULLTEXT",
            tags = listOf(DiscoveryPersonScope.DISCOVERED_TAG)
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

    private fun anyBooleanValue(): Boolean = Mockito.anyBoolean()

    /** I-1/I-3: stub 持久准入结论（docId → decision）；未列出视为未初始化。 */
    private fun stubAdmissions(vararg decisions: Pair<String, String>) {
        val byDoc = decisions.toMap()
        Mockito.`when`(discoveryReviewService.resolveAdmissionBatch(Mockito.anyList())).thenAnswer { invocation ->
            val keys = invocation.getArgument<List<com.weibo.talentintroduction.discovery.service.DiscoveryReviewAdmissionKey>>(0)
            keys.associate { key ->
                key.docId to com.weibo.talentintroduction.discovery.service.DiscoveryReviewResolvedAdmission(
                    key.docId, byDoc[key.docId], byDoc[key.docId] != null, byDoc[key.docId] != null,
                    byDoc[key.docId] != null, false, 1L, null
                )
            }
        }
    }

    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun <T> captureValue(captor: ArgumentCaptor<T>, defaultValue: T): T {
        captor.capture()
        return defaultValue
    }
}
