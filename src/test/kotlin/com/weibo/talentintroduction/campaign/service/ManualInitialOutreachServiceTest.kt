package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.Campaign
import com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot
import com.weibo.talentintroduction.campaign.domain.BatchOutcomeReasonCodes
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfig
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigCreateCommand
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigUpdateCommand
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.MailSendAttempt
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
import com.weibo.talentintroduction.campaign.domain.ManualBatchExecutionRequest
import com.weibo.talentintroduction.campaign.domain.RecipientScope
import com.weibo.talentintroduction.campaign.domain.toExecutionSnapshot
import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationTagStatus
import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationErrorCodes
import com.weibo.talentintroduction.campaign.repository.CampaignRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.MailSendAttemptRepository
import com.weibo.talentintroduction.config.ManualOutreachProperties
import com.weibo.talentintroduction.config.WarmupProperties
import com.weibo.talentintroduction.config.WarmupStep
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertClassification
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.ExpertType
import com.weibo.talentintroduction.expert.domain.IdentityVerification
import com.weibo.talentintroduction.expert.service.ExpertClassificationService
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.time.LocalDateTime
import java.util.concurrent.Executor

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
    private val operatorActionLogService =
        Mockito.mock(com.weibo.talentintroduction.audit.service.OperatorActionLogService::class.java)
    /**
     * I-3（03）：公共 `operator_status` 写入口使用**真实实现** + mock 仓储/审计/ES —— 断言既有
     * DB/ES 语义（REPLIED 及以上保护、无人工审计、ES 同步）而不用替身。
     */
    private val expertOperatorStatusService =
        ExpertOperatorStatusService(expertContactRepository, operatorActionLogService, expertIndexWriterService)
    private val accountRateLimiter = AccountRateLimiter()
    private val emailSuppressionService = Mockito.mock(EmailSuppressionService::class.java)
    private val autoReplySettingService = Mockito.mock(AutoReplySettingService::class.java)
    private val manualExpertMailService = Mockito.mock(com.weibo.talentintroduction.mail.service.ManualExpertMailService::class.java)
    private val taskExecutionService = Mockito.mock(com.weibo.talentintroduction.task.service.TaskExecutionService::class.java)
    private val mailComposeTemplateService = Mockito.mock(MailComposeTemplateService::class.java)
    /** I-1/I-2/I-6：发送前验证的接入点；本类只验证引擎边界与计数，HTTP/标签语义见 BatchEmailVerificationServiceTest。 */
    private val batchEmailVerificationService = Mockito.mock(BatchEmailVerificationService::class.java)
    /**
     * 逐次执行上下文是引擎与验证服务之间的不透明句柄；`ExecutionVerificationContext` 不是 Spring bean
     * （final 类不可 mock），故用真实实例承载 any 匹配所需的非空值。
     */
    private val verificationContext: ExecutionVerificationContext = BatchEmailVerificationService(
        Mockito.mock(ExpertSearchService::class.java),
        Mockito.mock(ExpertIndexWriterService::class.java),
        Mockito.mock(com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationRepository::class.java),
        Mockito.mock(EmailableVerifyClient::class.java),
        "live_secret"
    ).beginExecution(12345L) { false }
    private val providerResolver = ProviderResolver()
    private val senderWarmupService = SenderWarmupService(
        WarmupProperties(
            enabled = true,
            steps = listOf(WarmupStep(1, 20), WarmupStep(3, 40))
        ),
        ObjectMapper().registerKotlinModule()
    )

    /** I-1/I-3: 统一 selector 的准入来源；逐用例 stub 持久准入结论。 */
    private val discoveryReviewService =
        Mockito.mock(com.weibo.talentintroduction.discovery.service.DiscoveryReviewService::class.java)
    private val batchRecipientSelectionService = BatchRecipientSelectionService(discoveryReviewService)

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
        mailComposeTemplateService = mailComposeTemplateService,
        batchEmailVerificationService = batchEmailVerificationService,
        expertOperatorStatusService = expertOperatorStatusService,
        batchRecipientSelectionService = batchRecipientSelectionService
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
    fun `recipient scope applies explicit conditions only, admission is consumed by the selector (I-1 I-3)`() {
        val scope = RecipientScope("INTRODUCTION", setOf("CANDIDATE"), emptyList(), emptyList(), emptyList(), null,
            expertTypes = listOf("PRODUCTION_RND"))
        // 非新发现档案：原有语义逐字保留。
        assertTrue(scope.matchesExpert(expert("legacy-retry", "retry@example.org")))
        // I-1: 发现档案不再在显式条件谓词内做机构/国家/凭证二次拒绝（准入由统一 selector 消费）。
        assertTrue(scope.matchesExpert(expert("discovery-retry", "retry@example.org").copy(emailSource = "PAPER_FULLTEXT")))
        assertTrue(
            scope.matchesExpert(
                expert("discovery-proof", "p@example.org").copy(
                    identityVerification = identityProof("p@example.org")
                )
            )
        )
        assertTrue(scope.matchesExpert(expert("pending-retry", "pending@example.org").copy(tags = listOf("待确认"))))
        assertTrue(scope.matchesExpert(signedDiscoveryExpert("signed-retry", "signed@example.org")))
        // 原有类型 fail-closed 语义不变。
        assertFalse(scope.copy(expertTypes = emptyList()).matchesExpert(expert("legacy-retry", "retry@example.org")))
        // 显式条件仍然生效（标签不匹配即排除）。
        assertFalse(scope.copy(tags = listOf("other-tag")).matchesExpert(expert("legacy-retry", "retry@example.org")))
    }

    @Test
    fun `non-discovery profiles keep the pre-change ES sieve without institution evidence (I-1 regression)`() {
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(
            Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        )
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(emptyList())
        // 旧档案：无身份对象、无 emailSource、无标签、无 country —— 新门禁不得收紧它。
        val legacy = expert("L001", "legacy@example.org").copy(country = null)
        stubScrolledExperts(listOf(legacy))

        val summary = service.countBySnapshot(runScheduledSnapshot())

        assertEquals(1, summary.pending)
        assertEquals(1, summary.totalSendable)
    }

    @Test
    fun `countBySnapshot consumes persistent admission and excludes uninitialized discovery (I-1 I-3)`() {
        stubEmptyRetryCandidates()
        val approved = expert("D001", "approved@example.org").copy(emailSource = "PAPER_FULLTEXT")
        val rejected = signedDiscoveryExpert("D002", "rejected@example.org").copy(filterResult = "REJECTED")
        val uninitialized = signedDiscoveryExpert("D003", "uninit@example.org")
        stubScrolledExperts(listOf(approved, rejected, uninitialized))
        // D001 无机构证据但持久结论 AUTO_PASSED → 进入；D002/D003 无结论 → 排除（不再二次机构/凭证校验）。
        stubAdmissions("D001" to "AUTO_PASSED")

        val summary = service.countBySnapshot(runScheduledSnapshot())

        assertEquals(1, summary.pending, "只消费持久准入结论（I-1）")
        assertEquals(1, summary.totalSendable)
    }

    @Test
    fun `countBySnapshot treats manual and legacy approval equal to auto pass (I-1)`() {
        stubEmptyRetryCandidates()
        val manual = signedDiscoveryExpert("E001", "manual@example.org")
        val legacy = signedDiscoveryExpert("E002", "legacy@example.org")
        stubScrolledExperts(listOf(manual, legacy))
        stubAdmissions("E001" to "MANUAL_APPROVED", "E002" to "LEGACY_APPROVED")

        val summary = service.countBySnapshot(runScheduledSnapshot())

        assertEquals(2, summary.pending, "MANUAL_APPROVED / LEGACY_APPROVED 与 AUTO_PASSED 同等准入")
        assertEquals(2, summary.totalSendable)
    }

    @Test
    fun `countBySnapshot NEW retry consumes admission same as ES (I-1 I-3)`() {
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(
            Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        )
        val contacts = listOf(
            ExpertContact(id = 31L, campaignId = 10L, orcidId = "R031", expertEmail = "blocked@example.org", expertName = "B", currentStatus = "NEW"),
            ExpertContact(id = 32L, campaignId = 10L, orcidId = "R032", expertEmail = "kept@example.org", expertName = "K", currentStatus = "NEW")
        )
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(contacts)
        for (contact in contacts) {
            Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contact.id!!)).thenReturn(emptyList())
        }
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("R031", "R032"), ExpertIndexLevel.CANDIDATE))
            .thenReturn(listOf(
                expert("R031", "blocked@example.org").copy(emailSource = "PAPER_FULLTEXT"),
                signedDiscoveryExpert("R032", "kept@example.org")
            ))
        stubScrolledExperts(emptyList())
        // R031 无持久结论 → 排除；R032 AUTO_PASSED → 纳入（不再二次机构证据校验）。
        stubAdmissions("R032" to "AUTO_PASSED")

        val summary = service.countBySnapshot(runScheduledSnapshot())

        assertEquals(1, summary.retryable, "NEW 重试与 ES 目标必须同判（I-3）")
        assertEquals(1, summary.totalSendable)
    }

    @Test
    fun `discovery region uses country only and non-discovery uses country or nationality (I-1 I-2)`() {
        stubEmptyRetryCandidates()
        val blankCountry = signedDiscoveryExpert("C001", "blank@example.org", country = null)
        val unmappedCountry = signedDiscoveryExpert("C002", "unmapped@example.org", country = "Atlantis")
            .copy(nationality = "Japan")
        val japan = signedDiscoveryExpert("C003", "japan@example.org", country = "Japan")
        val legacyBlank = expert("C004", "legacy-blank@example.org").copy(country = null, nationality = "Japan")
        stubScrolledExperts(listOf(blankCountry, unmappedCountry, japan, legacyBlank))
        stubAdmissions("C001" to "AUTO_PASSED", "C002" to "AUTO_PASSED", "C003" to "AUTO_PASSED")

        // 未指定地区：准入通过者全部进入。
        val unrestricted = service.countBySnapshot(runScheduledSnapshot())
        assertEquals(4, unrestricted.pending)

        // Other：发现档案不进任何地区（空/未映射国家），非发现 legacyBlank 按 country-only 语义也不在 Other（nationality=Japan）。
        val other = service.countBySnapshot(runScheduledSnapshot().copy(regions = listOf("Other")))
        assertEquals(0, other.pending)

        // Asia (Japan & Korea)：发现按 country=Japan 只进 C003；非发现 C004 按 nationality=Japan 进入。
        val asia = service.countBySnapshot(runScheduledSnapshot().copy(regions = listOf("Asia (Japan & Korea)")))
        assertEquals(2, asia.pending)
    }

    @Test
    fun `run consumes persistent admission and keeps paging for eligible candidates (I-1 I-3)`() {
        val acc = account("chen")
        val blockedA = expert("A001", "a1@b.com").copy(emailSource = "PAPER_FULLTEXT")
        val blockedB = expert("A002", "a2@b.com").copy(tags = listOf("待确认"))
        val eligible = signedDiscoveryExpert("A003", "a3@b.com")
        val all = listOf(blockedA, blockedB, eligible)
        stubIntroSendPipeline(acc, all)
        stubAdmissions("A003" to "AUTO_PASSED")
        // 真实 ES 先按 from/size 切片，服务端再对页做 selector 筛选（I-3）。
        Mockito.`when`(expertSearchService.searchExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList()), anyInt(), anyInt()
        )).thenAnswer { invocation ->
            all.drop(invocation.getArgument<Int>(2)).take(invocation.getArgument<Int>(3))
        }
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList())))
            .thenReturn(all.size.toLong())

        val result = service.run(
            introSnapshot(roundSize = 1, roundsPerRun = 1).copy(funnelLevel = "CANDIDATE"),
            12345L, ExecutionMode.MANUAL, oneRoundOnly = false
        )

        // 第一页（pageSize=2）被整页过滤时，offset 必须继续推进而不是当作数据耗尽。
        assertEquals(1, result.sent)
        assertEquals(0, result.failed)
        Mockito.verify(expertSearchService).searchExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList()), eqValue(2), anyInt()
        )
        val saved = ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(expertContactRepository, Mockito.times(1)).save(captureValue(saved, ExpertContact(
            campaignId = 0L, orcidId = "", expertEmail = "", expertName = null
        )))
        assertEquals("A003", saved.value.orcidId, "无机构证据的新发现绝不建联系人")
        Mockito.verify(mailDeliveryService, Mockito.times(1)).send(anyValue(acc), anyValue(ComposedMail("", "", "")))
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
    fun `countBySnapshot counts retryable contacts without SENT introduction`() {
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

        // I4-2: legacy countPending 的 config 派生快照无 expertTypes（恒 fail-closed），
        // retryable 判定改经现代 countBySnapshot（快照携带 fixture 类型）验证。
        val summary = service.countBySnapshot(runScheduledSnapshot())
        assertEquals(0, summary.pending)
        assertEquals(1, summary.retryable)
        assertEquals(1, summary.totalSendable)
    }
    @Test
    fun `historical exclusion removes retry target by its current profile email without live verification`() {
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(
            Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        )
        val contact = ExpertContact(
            id = 4L, campaignId = 10L, orcidId = "R004", expertEmail = "old@example.com",
            expertName = "Retry", currentStatus = "NEW"
        )
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(4L)).thenReturn(emptyList())
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("R004"), ExpertIndexLevel.CANDIDATE))
            .thenReturn(listOf(expert("R004", "Current@Example.com")))
        Mockito.`when`(
            batchEmailVerificationService.findKnownUndeliverableEmails(
                anyValue(emptyList<String?>()),
                anyValue(LocalDateTime.now())
            )
        ).thenReturn(setOf("current@example.com"))

        val summary = service.countBySnapshot(
            runScheduledSnapshot().copy(excludeVerifiedUnavailableEmails = true)
        )

        assertEquals(0, summary.retryable)
        assertEquals(0, summary.totalSendable)
        assertEquals(1, summary.excludedVerifiedUnavailable)
        Mockito.verify(batchEmailVerificationService, Mockito.never()).requireConfiguredApiKey()
        Mockito.verify(batchEmailVerificationService, Mockito.never()).verify(
            anyValue(verificationContext),
            anyValue(verificationTarget()),
            anyAllowedStates()
        )
    }

    @Test
    fun `preview filters ES and retry targets through the same final predicate in both modes (I-3)`() {
        val retry = listOf(
            ExpertContact(id = 61L, campaignId = 10L, orcidId = "R061", expertEmail = "retry-good@example.com", expertName = null, currentStatus = "NEW"),
            ExpertContact(id = 62L, campaignId = 10L, orcidId = "R062", expertEmail = "retry-bad@example.com", expertName = null, currentStatus = "NEW")
        )
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(
            Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        )
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(retry)
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("R061", "R062"), ExpertIndexLevel.CANDIDATE))
            .thenReturn(listOf(expert("R061", "retry-good@example.com"), expert("R062", "retry-bad@example.com")))
        val es = listOf(
            expert("E061", "es-good-1@example.com"),
            expert("E062", "ES-BAD@example.com"),
            expert("E063", "es-good-2@example.com")
        )
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList())))
            .thenReturn(3L)
        Mockito.doAnswer { invocation ->
            val handler = invocation.getArgument<(List<ExpertProfile>) -> Boolean>(3)
            assertTrue(handler(es.take(2)))
            assertTrue(handler(es.drop(2)))
            null
        }.`when`(expertSearchService).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList()), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )
        Mockito.`when`(batchEmailVerificationService.findKnownUndeliverableEmails(
            anyValue(emptyList<String?>()), anyValue(LocalDateTime.now())
        )).thenReturn(setOf("retry-bad@example.com", "es-bad@example.com"))

        val snapshot = runScheduledSnapshot().copy(excludeVerifiedUnavailableEmails = true)
        val filtered = service.countBySnapshot(snapshot)
        assertEquals(2, filtered.pending)
        assertEquals(1, filtered.retryable)
        assertEquals(3, filtered.totalSendable)
        assertEquals(2, filtered.excludedVerifiedUnavailable)
        Mockito.verify(expertSearchService).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList()), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )
        Mockito.verify(expertSearchService, Mockito.never()).countExperts(
            eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList())
        )

        Mockito.clearInvocations(batchEmailVerificationService, expertSearchService)
        val unfiltered = service.countBySnapshot(snapshot.copy(excludeVerifiedUnavailableEmails = false))
        assertEquals(3, unfiltered.pending)
        assertEquals(2, unfiltered.retryable)
        assertEquals(5, unfiltered.totalSendable)
        assertEquals(0, unfiltered.excludedVerifiedUnavailable)
        Mockito.verifyNoInteractions(batchEmailVerificationService)
        // I-3: 预估不再读粗筛命中数 —— 关闭历史不可达过滤时同样走 scroll + 最终谓词（否则预估虚高）。
        Mockito.verify(expertSearchService).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList()), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )
        Mockito.verify(expertSearchService, Mockito.never()).countExperts(
            eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList())
        )
    }

    @Test
    fun `material preview and execution exclude by contact recipient instead of ES profile address`() {
        val contact = ExpertContact(
            id = 64L, campaignId = 10L, orcidId = "M064", expertEmail = "recipient-bad@example.com",
            expertName = "Material", currentStatus = "WAITING_REPLY"
        )
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList())))
            .thenReturn(1L)
        Mockito.`when`(expertSearchService.searchExpertsFiltered(
            eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList()), eqValue(0), anyInt()
        )).thenReturn(listOf(expert("M064", "profile-good@example.com")))
        Mockito.`when`(expertContactRepository.findByOrcidIdIn(listOf("M064"))).thenReturn(listOf(contact))
        Mockito.`when`(batchEmailVerificationService.findKnownUndeliverableEmails(
            anyValue(emptyList<String?>()), anyValue(LocalDateTime.now())
        )).thenAnswer { invocation ->
            val emails = invocation.getArgument<Collection<String>>(0)
            assertEquals(listOf("recipient-bad@example.com"), emails.toList())
            setOf("recipient-bad@example.com")
        }
        val snapshot = BatchExecutionSnapshot(
            mailType = "MATERIAL_REMINDER", roundSize = 10, roundsPerRun = 1,
            perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
            templateId = 42L, excludeVerifiedUnavailableEmails = true
        )

        val preview = service.countBySnapshot(snapshot)
        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)
        assertEquals(0, preview.totalSendable)
        assertEquals(1, preview.excludedVerifiedUnavailable)
        assertEquals(0, result.sent)
        Mockito.verifyNoInteractions(manualExpertMailService)
        Mockito.verify(expertContactRepository, Mockito.never()).save(Mockito.any(ExpertContact::class.java))
        Mockito.verify(batchEmailVerificationService, Mockito.never()).requireConfiguredApiKey()
    }

    @Test
    fun `execution sends only retained candidates without recording skipped excluded address`() {
        val account = account("chen")
        val es = listOf(
            expert("E071", "good-1@example.com"),
            expert("E072", "bad@example.com"),
            expert("E073", "good-2@example.com")
        )
        stubIntroSendPipeline(account, es)
        Mockito.doAnswer { invocation ->
            val handler = invocation.getArgument<(List<ExpertProfile>) -> Boolean>(3)
            handler(es)
            null
        }.`when`(expertSearchService).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList()), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )
        Mockito.`when`(batchEmailVerificationService.findKnownUndeliverableEmails(
            anyValue(emptyList<String?>()), anyValue(LocalDateTime.now())
        )).thenReturn(setOf("bad@example.com"))

        val result = service.run(
            introSnapshot(roundSize = 10, roundsPerRun = 1).copy(excludeVerifiedUnavailableEmails = true),
            12345L, ExecutionMode.MANUAL, oneRoundOnly = false
        )

        assertEquals(2, result.total)
        assertEquals(2, result.sent)
        assertEquals(0, result.skipped)
        Mockito.verify(mailDeliveryService, Mockito.times(2))
            .send(anyValue(account), anyValue(ComposedMail("", "", "")))
        val saved = ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(expertContactRepository, Mockito.times(2)).save(saved.capture())
        assertEquals(setOf("E071", "E073"), saved.allValues.map { it.orcidId }.toSet())
        Mockito.verify(batchEmailVerificationService, Mockito.never()).verify(
            anyValue(verificationContext), anyValue(verificationTarget()), anyAllowedStates()
        )
        Mockito.verifyNoInteractions(expertIndexWriterService)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `cancellation during historical prescan stops later pages and layers even when all are excluded`(oneRoundOnly: Boolean) {
        val campaign = Campaign(
            id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach",
            description = null, senderAccountId = 1L
        )
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(emptyList())
        var cancelled = false
        Mockito.`when`(progressStore.isCancelled(eqValue("MANUAL_INITIAL_OUTREACH"), eqValue(12345L)))
            .thenAnswer { cancelled }
        val first = expert("E081", "excluded@example.com")
        val later = expert("E082", "later@example.com")
        val otherLevel = expert("E083", "other@example.com")
        Mockito.doAnswer { invocation ->
            val handler = invocation.getArgument<(List<ExpertProfile>) -> Boolean>(3)
            if (handler(listOf(first))) handler(listOf(later))
            null
        }.`when`(expertSearchService).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList()), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )
        Mockito.doAnswer { invocation ->
            val handler = invocation.getArgument<(List<ExpertProfile>) -> Boolean>(3)
            handler(listOf(otherLevel))
            null
        }.`when`(expertSearchService).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList()), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )
        Mockito.`when`(batchEmailVerificationService.findKnownUndeliverableEmails(
            anyValue(emptyList<String?>()), anyValue(LocalDateTime.now())
        )).thenAnswer { invocation ->
            assertEquals(listOf("excluded@example.com"), invocation.getArgument<Collection<String>>(0).toList())
            cancelled = true
            setOf("excluded@example.com")
        }

        val result = service.run(
            introSnapshot(roundSize = 10, roundsPerRun = 1).copy(excludeVerifiedUnavailableEmails = true),
            12345L, ExecutionMode.MANUAL, oneRoundOnly
        )

        assertTrue(result.wasCancelled)
        assertEquals("CANCELLED", result.finalStatus)
        assertEquals("CANCELLED", result.taskFinalStatus)
        assertEquals("CANCELLED", result.stopReason)
        Mockito.verify(expertSearchService, Mockito.never()).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList()), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )
        Mockito.verify(batchEmailVerificationService, Mockito.times(1)).findKnownUndeliverableEmails(
            anyValue(emptyList<String?>()), anyValue(LocalDateTime.now())
        )
        Mockito.verifyNoInteractions(senderAccountAssignmentService, mailDeliveryService)
        Mockito.verify(batchEmailVerificationService, Mockito.never()).verify(
            anyValue(verificationContext), anyValue(verificationTarget()), anyAllowedStates()
        )
        val progress = ArgumentCaptor.forClass(TaskProgress::class.java)
        Mockito.verify(progressStore, Mockito.atLeastOnce()).update(
            eqValue("MANUAL_INITIAL_OUTREACH"),
            captureValue(progress, TaskProgress("MANUAL_INITIAL_OUTREACH", "CANCELLED", 0, 0, 0)),
            eqValue(12345L)
        )
        assertEquals("CANCELLED", progress.value.status)
        assertEquals("CANCELLED", progress.value.details?.get("stopReason"))
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
            funnelLevel = "CANDIDATE",
            // I4-2: fixture 分类 PRODUCTION_RND —— 快照必须携带该类型，retryable 才能保留。
            expertTypes = listOf("PRODUCTION_RND")
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

        // I-2: 快照无 expertTypes → 类型空集合 fail-closed，选择零人。
        assertEquals(0, summary.pending)
        assertEquals(0, summary.retryable)
        assertEquals(0, summary.totalSendable)
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
    fun `runBulkOutreach blocks discovery without institution evidence before contact creation (I-1 I-3)`() {
        stubEmptyRetryCandidates()
        stubScrolledExperts(listOf(expert("0001", "a@b.com").copy(emailSource = "PAPER_FULLTEXT")))

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        // 无机构证据的新发现：不建联系人、不占发件名额、不发邮件。
        assertEquals(0, result.total)
        assertEquals(0, result.sent)
        assertEquals(0, result.failed)
        Mockito.verify(expertContactRepository, Mockito.never()).save(Mockito.any(ExpertContact::class.java))
        Mockito.verifyNoInteractions(mailDeliveryService, introductionMailComposer, txHelper)
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
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail(messageId = "msg1", status = "SENT"))
        Mockito.`when`(senderAccountBindingService.bindingFieldsFor(
            eqValue("chen"),
            anyValue(LocalDateTime.now())
        )).thenReturn("chen" to LocalDateTime.of(2026, 8, 10, 9, 30, 0))

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

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
    fun `existing contact binding is never overwritten because a bound target is skipped (I-3)`() {
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

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        // I-3: 已有绑定的目标不发信、不重选号、不改绑（绑定值是否为本次选中账号无关）。
        assertEquals(0, result.sent)
        assertEquals(1, result.skipped)
        val boundSkip = result.outcome?.skippedReasons?.get(BatchOutcomeReasonCodes.BOUND_SENDER_ALREADY_SET)
        assertEquals(1, boundSkip?.count)
        assertEquals("专家已绑定发件账号", boundSkip?.label)
        Mockito.verifyNoInteractions(mailDeliveryService)
        Mockito.verify(expertContactRepository, Mockito.never()).updateBindingById(
            Mockito.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
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
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenThrow(RuntimeException("SMTP connection failed"))

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)
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

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)
        // I-3: 预估改为与取页同一份最终筛选的扫描，取消即中止扫描 —— 取消的预扫描没有完整目标数，
        // 故 total 报 0（与既有「cancelled prescan has no complete target count」口径一致），状态仍为 CANCELLED。
        assertEquals(0, result.total)
        assertEquals(0, result.sent)
        assertEquals("CANCELLED", result.finalStatus)
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

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)
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

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)
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
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","",""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)
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
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","",""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)
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
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","",""))))
            .thenReturn(DeliveredMail("msg-1", "SENT"))

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)
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
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg-1", "SENT"))

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)
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
    fun `run triggers self-check for each sendable account at round gate`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg1", "SENT"))

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(1, result.sent)
        // L3-1: self-check invoked for the sendable account at the round gate
        Mockito.verify(selfCheckService).checkSendable(anyValue(account("chen")), anyInt())
    }

    @Test
    fun `run pauses flow with NO_AVAILABLE_ACCOUNT when gate finds no sendable accounts`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue())).thenReturn(emptyList())
        Mockito.`when`(mailSenderAccountService.listEnabledAccounts()).thenReturn(
            listOf(account("chen").copy(autoSendPaused = true, autoSendPausedReason = "SMTP_ERROR"))
        )

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        assertEquals(0, result.sent)
        assertEquals("NO_AVAILABLE_ACCOUNT", result.stopReason)
        assertEquals("PAUSED", result.finalStatus)
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `run pauses when self-check fails all sendable accounts mid-gate`() {
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

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        assertEquals(0, result.sent)
        assertEquals("NO_AVAILABLE_ACCOUNT", result.stopReason)
        assertEquals("PAUSED", result.finalStatus)
        // Self-check was invoked for the candidate
        Mockito.verify(selfCheckService).checkSendable(anyValue(account("chen")), anyInt())
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `run ignores dailyCap and sends all experts (I-1)`() {
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
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg", "SENT"))

        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig(roundSize = 10, dailyCap = 2))

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        // I-1: dailyCap=2 is no longer read; the round sends all 5 experts
        assertEquals(5, result.sent)
        assertEquals(5, result.total)
        assertEquals("COMPLETED", result.finalStatus)
        assertNotEquals("DAILY_CAP_REACHED", result.stopReason)
        Mockito.verify(mailDeliveryService, Mockito.times(5)).send(anyValue(account), anyValue(ComposedMail("","","")))
    }

    @Test
    fun `run stops at account capacity with DAILY_LIMIT_REACHED (I-5)`() {
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
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg", "SENT"))

        // dailyCap=50 → toSnapshot derives roundsPerRun = ceil(50/10) = 5
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig(roundSize = 10, dailyCap = 50))
        // runRoundGate lists sendable accounts twice per round: round 1 has capacity 3, round 2 has capacity 0
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue()))
            .thenReturn(listOf(account.copy(dailySendLimit = 3, todaySentCount = 0)))
            .thenReturn(listOf(account.copy(dailySendLimit = 3, todaySentCount = 0)))
            .thenReturn(listOf(account.copy(dailySendLimit = 3, todaySentCount = 3)))
            .thenReturn(listOf(account.copy(dailySendLimit = 3, todaySentCount = 3)))

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        // I-5: account capacity is the only daily-volume bound — exactly 3 sent, then DAILY_LIMIT_REACHED
        assertEquals(3, result.sent)
        assertEquals("DAILY_LIMIT_REACHED", result.stopReason)
        assertEquals("COMPLETED", result.finalStatus)
        Mockito.verify(mailDeliveryService, Mockito.times(3)).send(anyValue(account), anyValue(ComposedMail("","","")))
    }

    @Test
    fun `run oneRoundOnly returns PAUSED after single round`() {
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
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg", "SENT"))

        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig(roundSize = 2, dailyCap = 1000))

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = true)

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
    fun `run splits snapshot into multiple rounds`() {
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
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg", "SENT"))

        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig(roundSize = 2, dailyCap = 10))

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        assertEquals(3, result.sent)
        assertEquals(3, result.total)
        assertEquals("COMPLETED", result.finalStatus)
        // Round gate invoked for each round (2 rounds)
        Mockito.verify(selfCheckService, Mockito.atLeast(2)).checkSendable(anyValue(account("chen")), anyInt())
    }

    @Test
    fun `run progress details contain per-account stats`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg", "SENT"))

        service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

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
    fun `run progress uses per-batch counts instead of cumulative counts`() {
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
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg", "SENT"))
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig(roundSize = 1, dailyCap = 10))

        service.run(runScheduledSnapshot(), 12345L, ExecutionMode.AUTO, oneRoundOnly = false)

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
    fun `run AUTO mode writes executionMode=AUTO to progress`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg", "SENT"))

        service.run(runScheduledSnapshot(), 12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        val progressCaptor = org.mockito.ArgumentCaptor.forClass(TaskProgress::class.java)
        Mockito.verify(progressStore, Mockito.atLeastOnce()).update(
            eqValue("MANUAL_INITIAL_OUTREACH"),
            captureValue(progressCaptor, TaskProgress("MANUAL_INITIAL_OUTREACH", "RUNNING", 0, 0, 0)),
            eqValue(12345L)
        )
        val finalProgress = progressCaptor.allValues.last()
        assertEquals("AUTO", finalProgress.details!!["executionMode"])
    }

    /**
     * I-1（03）：把 mock 的 [ManualOutreachTxHelper.recordFailure] 变成真实事实源 —— 捕获调用并以
     * 同一 contact / 同一 errorSummary 生成 mail_record 行，供第二次 run 的仓储原样返回。
     * 禁止手工伪造 EMAIL_INVALID 来「证明」不会重发。
     */
    private fun captureIntroductionFailures(): MutableList<MailRecord> {
        val rows = mutableListOf<MailRecord>()
        Mockito.doAnswer { invocation ->
            rows += MailRecord(
                expertContactId = invocation.getArgument(0),
                direction = "OUTBOUND",
                mailType = "INTRODUCTION",
                messageId = invocation.getArgument(2),
                inReplyTo = null,
                subject = invocation.getArgument(4),
                body = invocation.getArgument(5),
                matchedQaRuleId = null,
                sendStatus = "FAILED",
                receivedAt = null,
                sentAt = null,
                errorSummary = invocation.getArgument(3)
            )
            null
        }.`when`(txHelper).recordFailure(
            Mockito.anyLong(), Mockito.anyString(), Mockito.nullable(String::class.java),
            Mockito.nullable(String::class.java), Mockito.nullable(String::class.java),
            Mockito.nullable(String::class.java), Mockito.nullable(Long::class.javaObjectType),
            Mockito.nullable(Long::class.javaObjectType)
        )
        return rows
    }

    /** 第二次 run 的既有 contact 行：真实状态是 NOT_CONTACTED（04 起不再旁标 EMAIL_INVALID）。 */
    private fun persistedIntroductionContact(email: String, operatorStatus: String = "NOT_CONTACTED") =
        ExpertContact(
            id = 999L, campaignId = 10L, orcidId = "0001", expertEmail = email, expertName = "Given Family",
            currentStatus = "NEW", operatorStatus = operatorStatus
        )

    @Test
    fun `address evidence permanent failure marks EMAIL_INVALID through the public entry and blocks the next snapshot`() {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(expert("0001", "bad@example.com")))
        val failureRows = captureIntroductionFailures()
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("", "", "")))).thenReturn(
            DeliveredMail(
                messageId = "msg-1",
                status = "FAILED",
                errorCategory = SmtpErrorCategory.PERMANENT,
                smtpResponseCode = 550,
                errorDetail = "550 5.1.1 User unknown"
            )
        )

        val firstRun = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(1, firstRun.failed)
        assertEquals(0, firstRun.sent)
        // I-1：判定与 recordFailure 读的是同一份摘要。
        Mockito.verify(txHelper).recordFailure(
            contactId = Mockito.eq(999L),
            accountCode = eqValue("chen"),
            messageId = Mockito.anyString(),
            errorSummary = eqValue("PERMANENT:550:550 5.1.1 User unknown"),
            subject = eqValue("Subject"),
            body = eqValue("Body"),
            attemptId = Mockito.eq(77L),
            taskExecutionId = Mockito.eq(12345L)
        )
        // I-3：合格地址证据 → 经公共入口写 DB/ES；自动路径不写人工审计。
        Mockito.verify(expertContactRepository, Mockito.atLeastOnce()).save(
            Mockito.argThat { contact: ExpertContact -> contact.operatorStatus == "EMAIL_INVALID" }
        )
        Mockito.verify(expertIndexWriterService).syncOperatorStatus("0001", "EMAIL_INVALID")
        Mockito.verifyNoInteractions(operatorActionLogService)

        // 第二次 run：仓储原样返回第一次捕获的失败行（contact 仍是被公共入口改过的行）。
        val persisted = persistedIntroductionContact("bad@example.com", operatorStatus = "EMAIL_INVALID")
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(persisted))
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("0001")))
            .thenReturn(listOf(expert("0001", "bad@example.com")))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(failureRows)
        stubScrolledExperts(emptyList())

        val secondRun = service.run(runScheduledSnapshot(), 12346L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, secondRun.total)
        assertEquals(0, secondRun.failed)
        Mockito.verify(mailDeliveryService, Mockito.times(1)).send(anyValue(account), anyValue(ComposedMail("", "", "")))
        Mockito.verify(txHelper, Mockito.times(1)).recordFailure(
            contactId = Mockito.anyLong(), accountCode = Mockito.anyString(), messageId = Mockito.anyString(),
            errorSummary = Mockito.anyString(), subject = Mockito.anyString(), body = Mockito.anyString(),
            attemptId = Mockito.anyLong(), taskExecutionId = Mockito.any()
        )
    }

    @Test
    fun `policy permanent failure keeps FAILED without EMAIL_INVALID and blocks automatic resend on the next run`() {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(expert("0001", "policy@example.com")))
        val failureRows = captureIntroductionFailures()
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("", "", "")))).thenReturn(
            DeliveredMail(
                messageId = "msg-1",
                status = "FAILED",
                errorCategory = SmtpErrorCategory.PERMANENT,
                smtpResponseCode = 550,
                errorDetail = "550 5.7.1 Blocked by policy"
            )
        )

        val firstRun = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(1, firstRun.failed)
        assertEquals(0, firstRun.sent)
        Mockito.verify(txHelper).recordFailure(
            contactId = Mockito.eq(999L),
            accountCode = eqValue("chen"),
            messageId = Mockito.anyString(),
            errorSummary = eqValue("PERMANENT:550:550 5.7.1 Blocked by policy"),
            subject = eqValue("Subject"),
            body = eqValue("Body"),
            attemptId = Mockito.eq(77L),
            taskExecutionId = Mockito.eq(12345L)
        )
        // O-1/I-3：策略类永久失败没有地址证据 → 不写 EMAIL_INVALID、不同步 ES、不写人工审计。
        Mockito.verify(expertContactRepository, Mockito.never()).save(
            Mockito.argThat { contact: ExpertContact -> contact.operatorStatus == "EMAIL_INVALID" }
        )
        Mockito.verifyNoInteractions(expertIndexWriterService, operatorActionLogService)

        // O-2/I-4：第二次 run 的仓储返回第一次实际捕获的失败行；contact 仍是 NOT_CONTACTED 且未绑定。
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(persistedIntroductionContact("policy@example.com")))
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("0001")))
            .thenReturn(listOf(expert("0001", "policy@example.com")))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(failureRows)
        stubScrolledExperts(emptyList())

        val secondRun = service.run(runScheduledSnapshot(), 12346L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, secondRun.total)
        assertEquals(0, secondRun.sent)
        assertEquals(0, secondRun.failed)
        // 累计：仍只有第一次的 1 次 SMTP、1 条 FAILED、1 次 PREPARED，且无额度增长。
        Mockito.verify(mailDeliveryService, Mockito.times(1)).send(anyValue(account), anyValue(ComposedMail("", "", "")))
        Mockito.verify(txHelper, Mockito.times(1)).recordFailure(
            contactId = Mockito.anyLong(), accountCode = Mockito.anyString(), messageId = Mockito.anyString(),
            errorSummary = Mockito.anyString(), subject = Mockito.anyString(), body = Mockito.anyString(),
            attemptId = Mockito.anyLong(), taskExecutionId = Mockito.any()
        )
        Mockito.verify(mailSendAttemptRepository, Mockito.times(1)).save(Mockito.any(MailSendAttempt::class.java))
        Mockito.verify(mailSenderAccountRepository, Mockito.never())
            .incrementTodaySentCount(Mockito.anyString(), anyValue(LocalDateTime.now()))
    }

    @ParameterizedTest
    @ValueSource(strings = ["REPLIED", "MATERIALS_RECEIVED", "INVITED", "COMPLETED"])
    fun `address evidence never downgrades a contact already at REPLIED or above (I-3)`(operatorStatus: String) {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(expert("0001", "progressed@example.com")))
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(persistedIntroductionContact("progressed@example.com", operatorStatus = operatorStatus)))
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("0001")))
            .thenReturn(listOf(expert("0001", "progressed@example.com")))
        Mockito.`when`(expertContactRepository.findByOrcidIdIn(listOf("0001")))
            .thenReturn(listOf(persistedIntroductionContact("progressed@example.com", operatorStatus = operatorStatus)))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("", "", "")))).thenReturn(
            DeliveredMail(
                messageId = "msg-1",
                status = "FAILED",
                errorCategory = SmtpErrorCategory.PERMANENT,
                smtpResponseCode = 550,
                errorDetail = "550 5.1.1 User unknown"
            )
        )

        val result = service.run(introSnapshot(roundSize = 10, roundsPerRun = 1), 12345L, ExecutionMode.MANUAL, false)

        // 失败事实照记，但公共入口对 REPLIED 及以上零写入（状态不降级、不写人工审计）。
        assertEquals(1, result.failed)
        assertEquals(0, result.sent)
        Mockito.verify(expertContactRepository, Mockito.never()).save(
            Mockito.argThat { contact: ExpertContact -> contact.operatorStatus == "EMAIL_INVALID" }
        )
        Mockito.verify(expertIndexWriterService, Mockito.never())
            .syncOperatorStatus(Mockito.anyString(), Mockito.anyString())
        Mockito.verifyNoInteractions(operatorActionLogService)
    }

    @Test
    fun `an ORCID reappearing from the ES page is blocked by the recorded permanent failure before any row or SMTP`() {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(expert("0001", "policy@example.com")))
        stubVerificationDecision(passedRowId = 555L)
        // NEW 重试已被 O-2 谓词排除（空列表），该 ORCID 仍从 ES 候选页重新出现。
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(emptyList())
        val persisted = persistedIntroductionContact("policy@example.com")
        Mockito.`when`(expertContactRepository.findByOrcidIdIn(listOf("0001"))).thenReturn(listOf(persisted))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(
            listOf(
                MailRecord(
                    expertContactId = 999L, direction = "OUTBOUND", mailType = "INTRODUCTION",
                    messageId = "msg-1", inReplyTo = null, subject = "Subject", body = "Body",
                    matchedQaRuleId = null, sendStatus = "FAILED", receivedAt = null, sentAt = null,
                    errorSummary = "PERMANENT:550:550 5.7.1 Blocked by policy"
                )
            )
        )

        val result = service.run(introSnapshotWithVerification(), 12346L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.sent)
        assertEquals(0, result.failed)
        assertEquals(1, result.skipped)
        assertEquals(1, result.outcome!!.skippedReasons[BatchOutcomeReasonCodes.SEND_EXCEPTION]?.count)
        Mockito.verify(mailDeliveryService, Mockito.never()).send(anyValue(account), anyValue(ComposedMail("", "", "")))
        // 不新建/绑定 contact、不写发送尝试（PREPARED）、不碰 ES 状态。
        Mockito.verify(expertContactRepository, Mockito.never()).save(Mockito.any(ExpertContact::class.java))
        Mockito.verify(mailSendAttemptRepository, Mockito.never()).save(Mockito.any(MailSendAttempt::class.java))
        Mockito.verify(expertIndexWriterService, Mockito.never()).syncOperatorStatus(Mockito.anyString(), Mockito.anyString())
        // I-6：已 PASS 验证但被门禁拦下 → NOT_SENT + 具体原因。
        Mockito.verify(batchEmailVerificationService)
            .recordSend(555L, "NOT_SENT", BatchOutcomeReasonCodes.SEND_EXCEPTION)
    }

    @Test
    fun `a transient failure row does not block the retry of the same contact`() {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(expert("0001", "retry@example.com")))
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(persistedIntroductionContact("retry@example.com")))
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("0001")))
            .thenReturn(listOf(expert("0001", "retry@example.com")))
        Mockito.`when`(expertContactRepository.findByOrcidIdIn(listOf("0001")))
            .thenReturn(listOf(persistedIntroductionContact("retry@example.com")))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(
            listOf(
                MailRecord(
                    expertContactId = 999L, direction = "OUTBOUND", mailType = "INTRODUCTION",
                    messageId = "msg-0", inReplyTo = null, subject = "Subject", body = "Body",
                    matchedQaRuleId = null, sendStatus = "FAILED", receivedAt = null, sentAt = null,
                    errorSummary = "TRANSIENT:421:421 too many connections"
                )
            )
        )

        val result = service.run(introSnapshot(roundSize = 10, roundsPerRun = 1), 12345L, ExecutionMode.MANUAL, false)

        assertEquals(1, result.sent)
        assertEquals(0, result.failed)
        Mockito.verify(mailDeliveryService, Mockito.times(1))
            .send(anyValue(account), anyValue(ComposedMail("", "", "")))
    }

    @Test
    fun `run throttles but continues on 421 SMTP rate limit`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com"), expert("0002", "c@d.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(expertContactRepository.existsByOrcidId("0002")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
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

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(2, result.failed)
        assertEquals(0, result.sent)
        Mockito.verify(mailSenderAccountService, Mockito.never()).pauseAutoSend(Mockito.anyString(), Mockito.anyString())
        Mockito.verify(mailDeliveryService, Mockito.times(2)).send(anyValue(account), anyValue(ComposedMail("","","")))
        assertEquals(4000L, accountRateLimiter.getIntervalMs("chen", "other", 1000L))
        assertEquals(1000L, accountRateLimiter.getIntervalMs("chen", 1000L))
    }

    @Test
    fun `run throttles only throttled provider not others`() {
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
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue())).thenAnswer { invocation ->
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

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(2, result.failed)
        assertEquals(1, result.sent)
        assertEquals(4000L, accountRateLimiter.getIntervalMs("chen", "gmail", 1000L))
        assertEquals(1000L, accountRateLimiter.getIntervalMs("chen", "outlook", 1000L))
        assertEquals(1000L, accountRateLimiter.getIntervalMs("chen", 1000L))
    }

    @Test
    fun `run records an explicit stop reason when the last account faults`() {
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        stubScrolledExperts(listOf(expert("0001", "a@b.com"), expert("0002", "c@d.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(expertContactRepository.existsByOrcidId("0002")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("","")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY))).thenReturn(account)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
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

        Mockito.doAnswer {
            Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue())).thenReturn(emptyList())
            null
        }.`when`(mailSenderAccountService).pauseAutoSend(eqValue("chen"), Mockito.anyString())

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(1, result.failed)
        assertEquals(0, result.sent)
        assertEquals("NO_AVAILABLE_ACCOUNT", result.stopReason)
        assertEquals("PAUSED", result.finalStatus)
        assertEquals(0, result.remaining)
        assertEquals(1, result.skippedNoAccount)
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
            attemptId = Mockito.eq(77L),
            taskExecutionId = Mockito.eq(12345L)
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["TRANSIENT", "INFRASTRUCTURE"])
    fun `healthy selected account fills twenty successes after another account faults`(category: String) {
        val failedAccount = account("chen")
        val healthyAccount = account("backup")
        val experts = (1..23).map { expert("fault-test-$it", "target$it@example.com") }
        stubIntroSendPipeline(failedAccount, experts)
        stubVerificationDecision()
        var paused = false
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue())).thenAnswer {
            if (paused) listOf(healthyAccount) else listOf(failedAccount, healthyAccount)
        }
        Mockito.doAnswer { paused = true; null }.`when`(mailSenderAccountService)
            .pauseAutoSend(eqValue("chen"), Mockito.anyString())
        Mockito.`when`(senderAccountAssignmentService.selectAccount(
            anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(),
            anyValue(SenderBindingStock.EMPTY), eqValue(setOf("chen", "backup"))
        )).thenAnswer { if (paused) healthyAccount else failedAccount }
        Mockito.`when`(introductionMailComposer.compose(Mockito.anyString(), anyValue(expert("", "")), Mockito.isNull(), anyBooleanValue()))
            .thenAnswer { invocation -> ComposedMail(invocation.getArgument<ExpertProfile>(1).email!!, "Subject", "Body") }
        val submissions = mutableListOf<Pair<String, String>>()
        Mockito.`when`(mailDeliveryService.send(anyValue(failedAccount), anyValue(ComposedMail("", "", ""))))
            .thenAnswer { invocation ->
                val account = invocation.getArgument<MailSenderAccount>(0)
                val mail = invocation.getArgument<ComposedMail>(1)
                submissions.add(account.accountCode to mail.to)
                if (account.accountCode == "chen") {
                    DeliveredMail(mail.messageId, "FAILED", SmtpErrorCategory.valueOf(category),
                        errorDetail = "connection unavailable")
                } else DeliveredMail(mail.messageId, "SENT")
            }

        val result = service.run(
            introSnapshotWithVerification(roundSize = 20).copy(senderAccountCodes = listOf("chen", "backup")),
            12345L, ExecutionMode.MANUAL, oneRoundOnly = false
        )

        assertEquals(20, result.sent)
        assertEquals(1, result.failed)
        assertEquals(2, result.remaining)
        assertEquals("ROUNDS_PER_RUN_REACHED", result.stopReason)
        assertEquals(21, submissions.size)
        assertEquals(21, submissions.map { it.second }.distinct().size, "failed recipient must not be resubmitted")
        assertEquals(1, submissions.count { it.first == "chen" })
        assertEquals(20, submissions.count { it.first == "backup" })
        Mockito.verify(txHelper, Mockito.times(20)).recordSuccess(
            anyValue(ExpertContact(campaignId = 10L, orcidId = "", expertEmail = "", expertName = null, currentStatus = "NEW")),
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
            Mockito.anyLong(), Mockito.anyLong(), Mockito.isNull()
        )
    }

    @Test
    fun `unselected healthy account cannot keep a faulted batch running`() {
        val selected = account("chen")
        val unselected = account("outside")
        stubIntroSendPipeline(selected, listOf(expert("0001", "a@b.com"), expert("0002", "b@b.com")))
        var paused = false
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue())).thenAnswer {
            if (paused) listOf(unselected) else listOf(selected, unselected)
        }
        Mockito.doAnswer { paused = true; null }.`when`(mailSenderAccountService)
            .pauseAutoSend(eqValue("chen"), Mockito.anyString())
        Mockito.`when`(senderAccountAssignmentService.selectAccount(
            anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(),
            anyValue(SenderBindingStock.EMPTY), eqValue(setOf("chen"))
        )).thenReturn(selected)
        Mockito.`when`(mailDeliveryService.send(anyValue(selected), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg", "FAILED", SmtpErrorCategory.INFRASTRUCTURE))

        val result = service.run(
            introSnapshotWithVerification().copy(emailVerificationEnabled = false, senderAccountCodes = listOf("chen")),
            12345L, ExecutionMode.MANUAL, oneRoundOnly = false
        )
        assertEquals("NO_AVAILABLE_ACCOUNT", result.stopReason)
        assertEquals("PAUSED", result.finalStatus)
        assertEquals(1, result.failed)
        assertEquals(0, result.remaining)
        assertEquals(1, result.skippedNoAccount)
        Mockito.verify(mailDeliveryService, Mockito.times(1)).send(anyValue(selected), anyValue(ComposedMail("", "", "")))
    }

    @Test
    fun `run preserves anti-duplicate semantics for already CONTACTED expert`() {
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

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        // I-7: no send for already-CONTACTED expert
        assertEquals(0, result.sent)
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `run streams retryable and ES candidates across rounds`() {
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
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg", "SENT"))
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(fastConfig(roundSize = 5, dailyCap = 1000))

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.AUTO, oneRoundOnly = false)

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
    fun `run passes configured emailDomain to ES filter`() {
        val configWithDomain = fastConfig().copy(emailDomain = "gmail.com")
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(configWithDomain)

        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        // P2a: 单值配置经 KV 桥接成单元素 list，ES 侧走多域版过滤器；I4-1 追加类型 filter。
        // I-2: 状态留空 = 不限 —— 只有 exists email 基座，不再走 NOT_CONTACTED 基座。
        val expectedFilters = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
        ExpertSearchService.emailDomainsFilter(listOf("gmail.com"))?.let { expectedFilters.add(it) }
        expectedFilters.add(ExpertSearchService.expertTypesFilter(listOf("PRODUCTION_RND", "ACADEMIC_RND", "HYBRID_RND"))!!)
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(0L)

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)
        assertEquals(0, result.total)
        
        // I-3: 预估走 scroll + 最终谓词（不再读粗筛计数），filter 列表必须逐字同源。
        Mockito.verify(expertSearchService).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )
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
    fun `run passes configured discipline to ES filter`() {
        val configWithDiscipline = fastConfig().copy(emailDomain = "gmail.com", discipline = "HUMANITIES")
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(configWithDiscipline)

        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())

        val expectedFilters = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
        ExpertSearchService.emailDomainsFilter(listOf("gmail.com"))?.let { expectedFilters.add(it) }
        expectedFilters.add(ExpertSearchService.disciplineFilter("HUMANITIES"))
        expectedFilters.add(ExpertSearchService.expertTypesFilter(listOf("PRODUCTION_RND", "ACADEMIC_RND", "HYBRID_RND"))!!)
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(0L)

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)
        assertEquals(0, result.total)

        // I-3: 预估走 scroll + 最终谓词（不再读粗筛计数），filter 列表必须逐字同源。
        Mockito.verify(expertSearchService).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )
    }

    @Test
    fun `countBySnapshot excludes non-STEM retryable when discipline is STEM`() {
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

        // I4-2: legacy countPending 的 config 派生快照无 expertTypes（恒 fail-closed），
        // retryable 判定改经现代 countBySnapshot（快照携带 fixture 类型）验证。
        val summary = service.countBySnapshot(runScheduledSnapshot())
        assertEquals(0, summary.pending)
        assertEquals(1, summary.retryable)
        assertEquals(1, summary.totalSendable)
    }

    @Test
    fun `countBySnapshot keeps all retryable when discipline is blank`() {
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

        // I4-2: legacy countPending 的 config 派生快照无 expertTypes（恒 fail-closed），
        // retryable 判定改经现代 countBySnapshot（快照携带 fixture 类型）验证。
        val summary = service.countBySnapshot(runScheduledSnapshot())
        assertEquals(1, summary.retryable)
        assertEquals(1, summary.totalSendable)
    }

    @Test
    fun `run excludes non-STEM retryable when discipline is STEM`() {
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

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)
        assertEquals(0, result.total)
        assertEquals(0, result.sent)
        Mockito.verify(mailDeliveryService, Mockito.never()).send(anyValue(account), anyValue(ComposedMail("", "", "")))
    }

    @Test
    fun `run completes with WARMUP_LIMIT_REACHED when all warmup accounts hit effective limit`() {
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

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.AUTO, oneRoundOnly = false)

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
    fun `run prefers NO_AVAILABLE_ACCOUNT when one account is fault paused and others at limit`() {
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

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.AUTO, oneRoundOnly = false)

        assertEquals("NO_AVAILABLE_ACCOUNT", result.stopReason)
        assertEquals("PAUSED", result.finalStatus)
    }

    @Test
    fun `run MANUAL bypasses warmup limit and sends when account is below dailySendLimit`() {
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
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue()))
            .thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(warmupAccount), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg1", "SENT"))

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = true)

        assertEquals(1, result.sent)
        assertEquals("ONE_ROUND_DONE", result.stopReason)
        assertNotEquals("WARMUP_LIMIT_REACHED", result.stopReason)
    }

    @Test
    fun `run MANUAL reports DAILY_LIMIT_REACHED not WARMUP when dailySendLimit exhausted`() {
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

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals("DAILY_LIMIT_REACHED", result.stopReason)
        assertNotEquals("WARMUP_LIMIT_REACHED", result.stopReason)
        assertEquals("COMPLETED", result.finalStatus)
        assertEquals(0, result.sent)
    }

    @Test
    fun `runMaterialReminderBatch gate rejection records one skip, single progress advancement, and continues (V-1)`() {
        // V-1: the MATERIAL_REMINDER gate catch must not double-count processedTotal/roundProcessed,
        // must record exactly one PERSONALIZATION_INCOMPLETE skip (not a failure), and must continue the batch.
        Mockito.`when`(batchSendSettingService.getConfig(BatchSendType.MATERIAL_REMINDER))
            .thenReturn(
                BatchSendConfig(
                    sendType = BatchSendType.MATERIAL_REMINDER,
                    autoEnabled = false, cron = "0 0 8 * * ?",
                    dailyCap = 1, roundSize = 1,
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
        Mockito.`when`(manualExpertMailService.sendManualMail(
            anyLong(),
            anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", "")), anyBooleanValue()
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

        val result = service.runMaterialReminderBatch(12345L, ExecutionMode.MANUAL, true)

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
            anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", "")), anyBooleanValue()
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
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("","")), Mockito.isNull(), anyBooleanValue())).thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("","","")))).thenReturn(DeliveredMail("msg1", "SENT"))

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(3, result.sent)
        assertEquals(3, result.total)
        // I-1: 快照在批次开始处取一次（round 循环之外），而非每个专家一次
        Mockito.verify(senderAccountAssignmentService, Mockito.times(1)).loadBindingStock()
    }

    @Test
    fun `runScheduledBatch legacy config without expertTypes sends nobody (I4-2)`() {
        // M-2: legacy BatchSendConfig 派生快照不含 expertTypes —— 04 语义翻转后该入口恒 fail-closed。
        val account = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())

        val result = service.runScheduledBatch(12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        // I-2: 空类型集合 fail-closed → selector 选择零人，目标为空（不是执行期跳过）。
        assertEquals(0, result.total)
        assertEquals(0, result.sent)
        assertEquals(0, result.skipped)
        Mockito.verify(mailDeliveryService, Mockito.never()).send(anyValue(account), anyValue(ComposedMail("", "", "")))
    }

    // ──── roundsPerRun (execution round budget) tests ────

    /**
     * I4-2 迁移助手：runScheduledBatch 的私有 toSnapshot 派生（legacy BatchSendConfig）不含
     * expertTypes —— 04 语义翻转（空集合 = 发给零个人）后该入口恒 fail-closed。下列用例的
     * subject 是 run 流本身（轮次/容量/SMTP/预热），故按 toSnapshot 的相同派生规则构造
     * 等价快照并携带 fixture 类型，改走现代 [ManualInitialOutreachService.run] 入口。
     */
    private fun runScheduledSnapshot(researchDirectionFilter: String = "ANY") =
        com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = batchSendSettingService.getConfig().roundSize,
            roundsPerRun = maxOf(1, (batchSendSettingService.getConfig().dailyCap + batchSendSettingService.getConfig().roundSize - 1) / batchSendSettingService.getConfig().roundSize),
            perMailIntervalMs = batchSendSettingService.getConfig().perMailIntervalMs,
            perRoundIntervalMs = batchSendSettingService.getConfig().perRoundIntervalMs,
            selfCheckTtlMinutes = batchSendSettingService.getConfig().selfCheckTtlMinutes,
            funnelLevel = "CANDIDATE",
            emailDomains = batchSendSettingService.getConfig().emailDomain.ifBlank { null }?.let { listOf(it) } ?: emptyList(),
            discipline = batchSendSettingService.getConfig().discipline.ifBlank { null },
            templateId = batchSendSettingService.getConfig().templateId,
            // I4-2: fixture 默认分类为 PRODUCTION_RND —— 快照必须携带该类型，否则 fail-closed 一律不发。
            expertTypes = listOf("PRODUCTION_RND", "ACADEMIC_RND", "HYBRID_RND"),
            // I-1/I-2: 方向三态默认 ANY —— 既有用例（未显式传值）行为逐字不变。
            researchDirectionFilter = researchDirectionFilter
        )

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
        selfCheckTtlMinutes = 30,
        // I4-2: fixture 默认分类为 PRODUCTION_RND —— 快照必须携带该类型，否则 fail-closed 一律不发。
        expertTypes = listOf("PRODUCTION_RND", "ACADEMIC_RND", "HYBRID_RND")
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
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("", "")), Mockito.isNull(), anyBooleanValue()))
            .thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg", "SENT"))
    }

    /**
     * Legacy fixture serving a fresh page on each call. Real offset paging and
     * shrinking result sets are covered separately by the scheduled-round regressions.
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
            Mockito.`when`(manualExpertMailService.sendManualMail(
                eqValue(contactId),
                anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", "")), anyBooleanValue()
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
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyLong(), Mockito.any(),
                Mockito.nullable(Long::class.javaObjectType)
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
            val cmdCaptor = org.mockito.ArgumentCaptor.forClass(
                com.weibo.talentintroduction.mail.service.ManualMailSendCommand::class.java
            )
            Mockito.`when`(manualExpertMailService.sendManualMail(
                eqValue(contactId),
                anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", "")), anyBooleanValue()
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
                captureValue(cmdCaptor, com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", "")), anyBooleanValue()
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
            Mockito.`when`(manualExpertMailService.sendManualMail(
                eqValue(contactId),
                anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", "")), anyBooleanValue()
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
            Mockito.`when`(manualExpertMailService.sendManualMail(
                anyLong(),
                anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", "")), anyBooleanValue()
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
            Mockito.`when`(manualExpertMailService.sendManualMail(
                anyLong(),
                anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", "")), anyBooleanValue()
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
            Mockito.`when`(manualExpertMailService.sendManualMail(
                anyLong(),
                anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", "")), anyBooleanValue()
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
            Mockito.`when`(manualExpertMailService.sendManualMail(
                anyLong(),
                anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", "")), anyBooleanValue()
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
            Mockito.`when`(manualExpertMailService.sendManualMail(
                anyLong(),
                anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", "")), anyBooleanValue()
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
        val expectedFilters = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
        ExpertSearchService.regionsFilter(listOf("Europe"))?.let { expectedFilters.add(it) }
        // I4-2: 快照无 expertTypes → 追加恒不命中项（fail-closed）。
        expectedFilters.add(ExpertSearchService.MATCH_NONE_FILTER)
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(0L)

        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.total)
        // I-3: 预估走 scroll + 最终谓词（不再读粗筛计数），filter 列表必须逐字同源。
        Mockito.verify(expertSearchService).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )
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
        val expectedFilters = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
        expectedFilters.add(ExpertSearchService.disciplineFilter("UNCLASSIFIED"))
        // Regression: the INTRODUCTION+CANDIDATE branch already routed through disciplineFilter; must stay correct.
        assertTrue(expectedFilters.any { (it["bool"] as? Map<*, *>)?.get("must_not") != null })
        assertTrue(expectedFilters.none { it.containsKey("term") })
        // I4-2: 快照无 expertTypes → 追加恒不命中项（fail-closed）。
        expectedFilters.add(ExpertSearchService.MATCH_NONE_FILTER)
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(0L)

        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.total)
        // I-3: 预估走 scroll + 最终谓词（不再读粗筛计数），filter 列表必须逐字同源。
        Mockito.verify(expertSearchService).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )
    }

    @Test
    fun `countBySnapshot keeps retryable without disciplineCategory when discipline is UNCLASSIFIED (I-4)`() {
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

        // I4-2: legacy countPending 的 config 派生快照无 expertTypes（恒 fail-closed），
        // retryable 判定改经现代 countBySnapshot（快照携带 fixture 类型）验证。
        val summary = service.countBySnapshot(runScheduledSnapshot())
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
            regions = listOf("Europe"),
            // I4-2: fixture 分类 PRODUCTION_RND —— 快照必须携带该类型，retryable 才能保留。
            expertTypes = listOf("PRODUCTION_RND")
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
    fun `retryable contact with no country and no nationality is not in Other region (I-2 ES parity)`() {
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
            regions = listOf("Other"),
            // I4-2: fixture 分类 PRODUCTION_RND —— 快照必须携带该类型，retryable 才能保留。
            expertTypes = listOf("PRODUCTION_RND")
        )
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue())).thenReturn(emptyList())
        stubScrolledExperts(emptyList())

        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.total, "空 country/nationality 不满足 ES Other 的 exists 前置")
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
        // I4-2: 快照无 expertTypes → 追加恒不命中项（fail-closed）。
        expectedFilters.add(ExpertSearchService.MATCH_NONE_FILTER)
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(0L)

        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.total)
        // I-3: 预估走 scroll + 最终谓词（不再读粗筛计数），filter 列表必须逐字同源。
        Mockito.verify(expertSearchService).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )
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
        val expectedFilters = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
        ExpertSearchService.operatorStatusesFilter(listOf("NOT_CONTACTED"))?.let { expectedFilters.add(it) }
        // I-3: NOT_CONTACTED 的唯一语义是 must_not exists operatorStatus，绝不写 term operatorStatus=NOT_CONTACTED。
        assertEquals(
            ExpertSearchService.operatorStatusesFilter(listOf("NOT_CONTACTED"))!!,
            expectedFilters[1]
        )
        assertTrue(expectedFilters.any { it.toString().contains("must_not") })
        assertTrue(expectedFilters.none { it.containsKey("term") })
        // I4-2: 快照无 expertTypes → 追加恒不命中项（fail-closed）。
        expectedFilters.add(ExpertSearchService.MATCH_NONE_FILTER)
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(0L)

        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.total)
        // I-3: 预估走 scroll + 最终谓词（不再读粗筛计数），filter 列表必须逐字同源。
        Mockito.verify(expertSearchService).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )
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
    fun `empty operatorStatus leaves ES filters status-agnostic (I-2)`() {
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
        // I-2: 状态留空 = 不限，绝不偷偷切 NOT_CONTACTED 基座；只有 exists email 基座。
        // I4-2 快照无 expertTypes → 追加恒不命中项。
        val expectedFilters = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
        expectedFilters.add(ExpertSearchService.MATCH_NONE_FILTER)
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(0L)

        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.total)
        // I-3: 预估走 scroll + 最终谓词（不再读粗筛计数），filter 列表必须逐字同源。
        Mockito.verify(expertSearchService).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )
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
            expertTypesJson = """["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]""",
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
            emailDomains = listOf("a.com", "b.com"), discipline = null,
            // I4-2: fixture 分类 PRODUCTION_RND —— 类型判定通过后再测邮箱维度。
            expertTypes = listOf("PRODUCTION_RND")
        )
        assertTrue(scoped.matchesExpert(expert("0001", "x@b.com")))
        assertFalse(scoped.matchesExpert(expert("0002", "x@c.com")))

        // I2a-2: 空集合 = 不限 —— 即使 profile 无 email 也不判定（仍须通过 I3-1 sendable 门禁）。
        val unrestricted = scoped.copy(emailDomains = emptyList())
        assertTrue(
            unrestricted.matchesExpert(
                ExpertProfile(
                    orcidId = "0003", email = null, givenNames = "G", familyNames = "F",
                    country = null, keyword = null, employment = null,
                    expertClassification = sendableClassification()
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
            emailDomains = domains, discipline = null,
            // I4-2: fixture 分类 PRODUCTION_RND —— 类型判定通过后再测邮箱维度。
            expertTypes = listOf("PRODUCTION_RND")
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
    fun `empty operatorStatuses uses the status-agnostic base on CANDIDATE (I-2)`() {
        val scope = RecipientScope(
            mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(), regions = emptyList(),
            emailDomains = emptyList(), discipline = null
        )
        val filters = invokeBuildEsFiltersForLevel(service, scope, "CANDIDATE")

        // I-2: 状态留空（不限）时只保留 exists email 基座，不追加任何状态 filter（不是 NOT_CONTACTED 基座）。
        // I4-2: INTRODUCTION 在 filters 末尾追加类型 filter（快照无 expertTypes → MATCH_NONE_FILTER）。
        val baseline = listOf(
            mapOf("exists" to mapOf("field" to "email"))
        )
        assertEquals(baseline + ExpertSearchService.MATCH_NONE_FILTER, filters)
    }

    @Test
    fun `only NOT_CONTACTED adds the pure status predicate to the base on CANDIDATE (I-2 I3a-2)`() {
        val scope = RecipientScope(
            mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(), regions = emptyList(),
            emailDomains = emptyList(), discipline = null,
            operatorStatuses = listOf("NOT_CONTACTED")
        )
        val filters = invokeBuildEsFiltersForLevel(service, scope, "CANDIDATE")

        // I-2: 仅选 NOT_CONTACTED 时基座仍是 exists email，状态走纯 must_not exists 谓词
        // （绝不写 term operatorStatus=NOT_CONTACTED）；I4-2 追加类型 filter。
        val baseline = listOf(
            mapOf("exists" to mapOf("field" to "email")),
            mapOf(
                "bool" to mapOf(
                    "should" to listOf(
                        mapOf("bool" to mapOf("must_not" to listOf(mapOf("exists" to mapOf("field" to "operatorStatus")))))
                    ),
                    "minimum_should_match" to 1
                )
            )
        )
        assertEquals(baseline + ExpertSearchService.MATCH_NONE_FILTER, filters)
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
        // MATCH_NONE_FILTER（I4-2 空 expertTypes 的 fail-closed 项）自带 must_not，需排除。
        assertTrue(
            filters.none { it != ExpertSearchService.MATCH_NONE_FILTER && (it["bool"] as? Map<*, *>)?.get("must_not") != null },
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

        // MATCH_NONE_FILTER（I4-2 空 expertTypes 的 fail-closed 项）自带 must_not，需排除。
        assertTrue(
            filters.none { it != ExpertSearchService.MATCH_NONE_FILTER && (it["bool"] as? Map<*, *>)?.get("must_not") != null },
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
                operatorStatuses = statuses,
                // I4-2: fixture 分类 PRODUCTION_RND —— 类型判定通过后再测状态维度。
                expertTypes = listOf("PRODUCTION_RND")
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

    // ── P3c: expertTypes 研发类型多值（I4-1 / I4-2 / I4-5）────────────────────────

    @Test
    fun `empty expertTypes appends MATCH_NONE_FILTER on CANDIDATE (I4-2)`() {
        val scope = RecipientScope(
            mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(), regions = emptyList(),
            emailDomains = emptyList(), discipline = null,
            operatorStatuses = emptyList(),
            expertTypes = emptyList()
        )
        val filters = invokeBuildEsFiltersForLevel(service, scope, "CANDIDATE")

        // I4-2: 空集合 = 发给零个人（fail-closed）—— 末尾追加恒不命中的 MATCH_NONE_FILTER，
        // 不得沿用旧"空 = 不限"语义（notContacted 基座 + MATCH_NONE_FILTER）。
        val baseline = listOf(
            mapOf("exists" to mapOf("field" to "email"))
        )
        assertEquals(baseline + ExpertSearchService.MATCH_NONE_FILTER, filters)
    }

    @Test
    fun `INTRODUCTION adds only the expertTypes filter without sendable or version items (I4-1)`() {
        val scope = RecipientScope(
            mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(), regions = emptyList(),
            emailDomains = emptyList(), discipline = null,
            operatorStatuses = emptyList(),
            expertTypes = listOf("ACADEMIC_RND")
        )
        val filters = invokeBuildEsFiltersForLevel(service, scope, "CANDIDATE")

        // I4-1: 唯一收口点 —— filters 含 expertTypesFilter 的 should 结构且位于末尾，
        // 不得再出现任何 expertClassification.sendable / .version 项（M-1 机器判据）。
        val typeFilter = ExpertSearchService.expertTypesFilter(listOf("ACADEMIC_RND"))!!
        assertTrue(filters.contains(typeFilter))
        assertEquals(filters.indexOf(typeFilter), filters.size - 1)
        val json = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(filters)
        assertFalse(json.contains("expertClassification.sendable"), "no filter may reference sendable: $json")
        assertFalse(json.contains("expertClassification.version"), "no filter may reference version: $json")
    }

    @Test
    fun `MATERIAL_REMINDER with expertTypes adds no type filter (I2-6)`() {
        val scope = RecipientScope(
            mailType = "MATERIAL_REMINDER", funnelLevels = setOf("APPLICATION"),
            tags = listOf("承诺回复材料"), regions = emptyList(),
            emailDomains = emptyList(), discipline = null,
            operatorStatuses = emptyList(),
            expertTypes = listOf("PRODUCTION_RND")
        )
        val filters = invokeBuildEsFiltersForLevel(service, scope, "APPLICATION")

        // I2-6: 类型筛选只在 INTRODUCTION 生效；材料提醒既无类型 filter 也无 fail-closed 项。
        assertFalse(filters.contains(ExpertSearchService.expertTypesFilter(listOf("PRODUCTION_RND"))!!))
        assertFalse(filters.contains(ExpertSearchService.MATCH_NONE_FILTER))
    }

    @Test
    fun `matchesExpert applies only the expertTypes decision for INTRODUCTION (I4-1)`() {
        val scope = RecipientScope(
            mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(), regions = emptyList(),
            emailDomains = emptyList(), discipline = null,
            operatorStatuses = emptyList(),
            expertTypes = listOf("PRODUCTION_RND")
        )
        // 类型不命中（null 分类）→ false。
        assertFalse(scope.matchesExpert(expert("0001", "a@b.com").copy(expertClassification = null)))
        // I4-1: 版本不再参与判定 —— 旧策略版本 + 类型命中 → true（无第二个门禁）。
        val stale = classification(ExpertType.PRODUCTION_RND).copy(version = "stale-v1")
        assertTrue(scope.matchesExpert(expert("0002", "b@b.com").copy(expertClassification = stale)))
        // 类型命中 → true。
        assertTrue(scope.matchesExpert(expert("0003", "c@b.com")))
    }

    @Test
    fun `matchesExpertType semantics per profile including UNCLASSIFIED and empty (I4-2 I4-5)`() {
        // 与 ES expertTypePredicate 同口径：UNCLASSIFIED = expertClassification.type 缺失（I4-5）。
        val scope = RecipientScope(
            mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(), regions = emptyList(),
            emailDomains = emptyList(), discipline = null,
            operatorStatuses = emptyList(),
            expertTypes = listOf("PRODUCTION_RND", "UNCLASSIFIED")
        )
        // PRODUCTION_RND 命中 → 放行。
        assertTrue(scope.matchesExpert(expert("0001", "a@b.com")))
        // ACADEMIC_RND 未命中 → 拒绝。
        assertFalse(
            scope.matchesExpert(expert("0002", "b@b.com").copy(expertClassification = classification(ExpertType.ACADEMIC_RND)))
        )
        // null 分类：UNCLASSIFIED 匹配（type 缺失）→ 放行（I4-5 与 ES must_not exists 一致）。
        assertTrue(scope.matchesExpert(expert("0003", "c@b.com").copy(expertClassification = null)))
        // UNCLASSIFIED 不匹配带显式类型的 profile。
        val onlyUnclassified = scope.copy(expertTypes = listOf("UNCLASSIFIED"))
        assertFalse(
            onlyUnclassified.matchesExpert(expert("0006", "f@b.com")),
            "UNCLASSIFIED must not match a profile with an explicit type"
        )

        // I4-2: 空集合 = 发给零个人（fail-closed）—— 任意 profile 一律 false。
        val failClosed = scope.copy(expertTypes = emptyList())
        assertFalse(
            failClosed.matchesExpert(expert("0004", "d@b.com").copy(expertClassification = classification(ExpertType.HYBRID_RND)))
        )
        assertFalse(
            failClosed.matchesExpert(expert("0005", "e@b.com").copy(expertClassification = classification(ExpertType.SERVICE_ONLY)))
        )
        assertFalse(
            failClosed.matchesExpert(expert("0007", "g@b.com").copy(expertClassification = null)),
            "empty expertTypes must fail closed for every profile"
        )
    }

    @Test
    fun `preview and execution use identical filter arrays for same scope with expertTypes (I2-2)`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(emptyList())

        // 预期 filter 列表：exists email 基座（I-2 状态空不限）+ 类型 filter（I4-1 唯一收口点）。
        val expectedFilters = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
        expectedFilters.add(ExpertSearchService.expertTypesFilter(listOf("PRODUCTION_RND"))!!)
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(1L)
        // I-3: 预估走 scroll + 最终谓词 —— 同一个 filter 列表必须同时命中预估与取页两条路径。
        Mockito.doAnswer { invocation ->
            val handler = invocation.getArgument<(List<ExpertProfile>) -> Boolean>(3)
            handler(listOf(expert("0001", "a@b.com").copy(institution = "Institute")))
            null
        }.`when`(expertSearchService).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )

        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            expertTypes = listOf("PRODUCTION_RND")
        )

        // 预估路径（countBySnapshot → resolveScope → countEsTargets）
        val preview = service.countBySnapshot(snapshot)
        assertEquals(1, preview.pending)
        assertEquals(0, preview.retryable)
        assertEquals(1, preview.totalSendable)

        // 执行路径（无可用账号 → 停在轮次闸口，不发信）
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue())).thenReturn(emptyList())
        val result = service.run(snapshot, 12347L, ExecutionMode.MANUAL, oneRoundOnly = true)
        assertEquals(preview.totalSendable, result.total)

        // I2-2 / I-3: 两条路径对同一 snapshot 使用完全相同的 filter 列表（同源同口径）。
        // 预估 = 两次 scroll 最终筛选（预估 1 + 执行前估算 1）；执行取页的 offset 口径另算 1 次 count。
        Mockito.verify(expertSearchService, Mockito.times(2))
            .scrollExpertsFiltered(
                eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters), eqValue(500),
                anyValue({ _: List<ExpertProfile> -> true })
            )
        Mockito.verify(expertSearchService, Mockito.times(1))
            .countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters))
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
        // 改动前基线逐字硬编码（I4a-1 / N4a-1）：模板门禁关闭时不追加任何 gate 字段 filter。
        // I4-2: INTRODUCTION 快照无 expertTypes → 末尾追加 MATCH_NONE_FILTER（fail-closed）。
        val baseline = listOf(
            mapOf("exists" to mapOf("field" to "email"))
        )
        assertEquals(baseline + ExpertSearchService.MATCH_NONE_FILTER, filters)
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
            mapOf("exists" to mapOf("field" to "email"))
        )
        assertEquals(baseline + ExpertSearchService.MATCH_NONE_FILTER, filters)
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
            mapOf("exists" to mapOf("field" to "email"))
        )
        assertEquals(baseline + ExpertSearchService.MATCH_NONE_FILTER, filters)
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
        // I4a-2: exists email 基座（I-2 状态空不限）+ 恰好 2 项模板门禁 filter（每字段一个独立 filter，平铺进 bool.filter）
        // + I4-2: 末尾 1 项 MATCH_NONE_FILTER（快照无 expertTypes）。
        assertEquals(4, filters.size)
        assertEquals(mapOf("exists" to mapOf("field" to "institution")), filters[1])
        assertEquals(
            mapOf(
                "bool" to mapOf(
                    "must" to listOf(mapOf("exists" to mapOf("field" to "researchFields"))),
                    "must_not" to listOf(mapOf("term" to mapOf("researchFields" to "")))
                )
            ),
            filters[2]
        )
        assertEquals(ExpertSearchService.MATCH_NONE_FILTER, filters[3])
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
        // exists email 基座 + institution 存在性 + I4-2 MATCH_NONE_FILTER（快照无 expertTypes）。
        assertEquals(3, filters.size)
        assertEquals(mapOf("exists" to mapOf("field" to "institution")), filters[1])
        assertEquals(ExpertSearchService.MATCH_NONE_FILTER, filters[2])
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

        // 门禁开启的预期 filter 列表：exists email 基座 + institution 存在性 filter（I4a-2 平铺）
        // + 类型 filter（快照携带 PRODUCTION_RND）。
        val expectedFilters = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
        expectedFilters.add(mapOf("exists" to mapOf("field" to "institution")))
        expectedFilters.add(ExpertSearchService.expertTypesFilter(listOf("PRODUCTION_RND"))!!)
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(1L)
        // I-3: 预估走 scroll + 最终谓词 —— 同一个 filter 列表必须同时命中预估与取页两条路径。
        Mockito.doAnswer { invocation ->
            val handler = invocation.getArgument<(List<ExpertProfile>) -> Boolean>(3)
            handler(listOf(expert("0001", "a@b.com").copy(institution = "Institute")))
            null
        }.`when`(expertSearchService).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )

        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = 10,
            roundsPerRun = 1,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            templateId = 42L,
            gateFilterEnabled = true,
            expertTypes = listOf("PRODUCTION_RND")
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

        // I4a-4 / M-4 / I-3: 两条路径对同一 snapshot 使用完全相同的 filter 列表（同源同口径）。
        // 预估 = 两次 scroll 最终筛选（预估 1 + 执行前估算 1）；执行取页的 offset 口径另算 1 次 count
        // （OutreachTargetIterator.hasNext 在轮次闸口前拉首页）。
        Mockito.verify(expertSearchService, Mockito.times(2))
            .scrollExpertsFiltered(
                eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters), eqValue(500),
                anyValue({ _: List<ExpertProfile> -> true })
            )
        Mockito.verify(expertSearchService, Mockito.times(1))
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
                gateEsFields = listOf(case.field),
                // I4-2: fixture 分类 PRODUCTION_RND —— 类型判定通过后再测门禁字段维度。
                expertTypes = listOf("PRODUCTION_RND")
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
            gateEsFields = listOf("institution", "degree"),
            // I4-2: fixture 分类 PRODUCTION_RND —— 类型判定通过后再测门禁字段维度。
            expertTypes = listOf("PRODUCTION_RND")
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
            gateEsFields = emptyList(),
            // I4-2: fixture 分类 PRODUCTION_RND —— 类型判定通过后再测空门禁字段维度。
            expertTypes = listOf("PRODUCTION_RND")
        )
        assertTrue(none.matchesExpert(base.copy(institution = null, degree = null)))
    }

    // ──── P3b: INTRODUCTION type gate (child 04 / I4-1..I4-5) ─────────────────

    @Test
    fun `OUT_OF_SCOPE experts follow only the explicit expertTypes decision (I4-1)`() {
        // I4-1: 没有隐式门禁 —— OUT_OF_SCOPE 只在类型集合未包含它时被跳过；
        // 一旦运营勾选 OUT_OF_SCOPE，它照常发送（A4-2 反向证明）。
        val excluded = RecipientScope(
            mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(), regions = emptyList(),
            emailDomains = emptyList(), discipline = null,
            expertTypes = listOf("ACADEMIC_RND")
        )
        assertFalse(
            excluded.matchesExpert(expert("0001", "a@b.com").copy(expertClassification = classification(ExpertType.OUT_OF_SCOPE)))
        )

        val included = excluded.copy(expertTypes = listOf("ACADEMIC_RND", "OUT_OF_SCOPE"))
        assertTrue(
            included.matchesExpert(expert("0002", "b@b.com").copy(expertClassification = classification(ExpertType.OUT_OF_SCOPE)))
        )
    }

    @Test
    fun `matchesExpert ignores classification version entirely (I4-1)`() {
        val scope = RecipientScope(
            mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(), regions = emptyList(),
            emailDomains = emptyList(), discipline = null,
            expertTypes = listOf("PRODUCTION_RND")
        )
        val stale = classification(ExpertType.PRODUCTION_RND).copy(version = "rnd-v1-2026")

        assertTrue(
            scope.matchesExpert(expert("0001", "a@b.com").copy(expertClassification = stale)),
            "版本不再参与发信判定（I4-1）"
        )
    }

    @Test
    fun `buildEsFiltersForLevel appends the type decision to every INTRODUCTION level not MATERIAL_REMINDER (I4-1 I4-2)`() {
        val introScope = RecipientScope(
            mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE", "APPLICATION"),
            tags = emptyList(), regions = emptyList(),
            emailDomains = emptyList(), discipline = null
        )
        setOf("CANDIDATE", "APPLICATION").forEach { level ->
            val filters = invokeBuildEsFiltersForLevel(service, introScope, level)
            // I4-2: 空 expertTypes 的 INTRODUCTION 一律以 MATCH_NONE_FILTER 收尾（fail-closed）。
            assertEquals(
                ExpertSearchService.MATCH_NONE_FILTER,
                filters.last(),
                "INTRODUCTION $level must end with MATCH_NONE_FILTER: $filters"
            )
        }

        val reminderScope = RecipientScope(
            mailType = "MATERIAL_REMINDER", funnelLevels = setOf("APPLICATION"),
            tags = listOf("承诺回复材料"), regions = emptyList(),
            emailDomains = emptyList(), discipline = null
        )
        val reminderFilters = invokeBuildEsFiltersForLevel(service, reminderScope, "APPLICATION")
        val json = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(reminderFilters)
        assertFalse(json.contains("expertClassification"), "MATERIAL_REMINDER query must not carry the type term: $json")
    }

    @Test
    fun `MATERIAL_REMINDER applies no classification gate in memory (I3-5)`() {
        val scope = RecipientScope(
            mailType = "MATERIAL_REMINDER", funnelLevels = setOf("APPLICATION"),
            tags = listOf("承诺回复材料"), regions = emptyList(),
            emailDomains = emptyList(), discipline = null
        )
        // 缺分类的 APPLICATION 联系人在内存侧仍通过（I3-5 / A3-5 / M-5）。
        assertTrue(scope.matchesExpert(expert("M001", "m@b.com").copy(tags = listOf("承诺回复材料"), expertClassification = null)))
        // 即使分类存在也不影响材料提醒（sendable=false 同样放行）。
        assertTrue(scope.matchesExpert(expert("M002", "m2@b.com").copy(tags = listOf("承诺回复材料"), expertClassification = classification(ExpertType.UNKNOWN))))
    }

    @Test
    fun `INTRODUCTION preview and execution both exclude non-sendable experts (I3-3 I3-1 I3-4)`() {
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        // 1 个 retryable NEW 联系人，其 ES profile 为 SERVICE_ONLY → 从 retryable 排除。
        val retryableContact = ExpertContact(id = 1L, campaignId = 10L, orcidId = "R001", expertEmail = "r1@b.com", expertName = "R1", currentStatus = "NEW")
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(retryableContact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("R001"))).thenReturn(listOf(
            expert("R001", "r1@b.com").copy(expertClassification = classification(ExpertType.SERVICE_ONLY))
        ))
        // ES page：1 可发 + 1 不可发（模拟竞态：ES 侧已按 term 过滤，但分页仍可能带回脏数据）。
        stubScrolledExperts(listOf(
            expert("0001", "a@b.com"),
            expert("0002", "c@d.com").copy(expertClassification = classification(ExpertType.UNKNOWN))
        ))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0001", "a@b.com")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001", "a@b.com")), Mockito.isNull(), anyBooleanValue()))
            .thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail(messageId = "msg1", status = "SENT"))

        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION", roundSize = 10, roundsPerRun = 1,
            perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            expertTypes = listOf("PRODUCTION_RND", "ACADEMIC_RND", "HYBRID_RND")
        )

        // 预估：不可发专家同时从 pending(ES) 与 retryable(内存) 口径排除 ——
        // ES pending 由 countExperts 的过滤器（类型收口点）保证；内存 retryable 由 matchesExpert 保证。
        val preview = service.countBySnapshot(snapshot)
        assertEquals(0, preview.retryable, "SERVICE_ONLY retryable profile must be excluded")
        // I-2/I-3: 统一 selector 在取页时即排除类型不匹配的脏数据 → pending 只含可发专家。
        assertEquals(1, preview.pending)
        assertEquals(1, preview.totalSendable)

        // 执行（同一 snapshot）：totalEstimate 与 preview 一致；脏数据在 selector 层被排除。
        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = true)
        assertEquals(preview.totalSendable, result.total)
        assertEquals(1, result.sent)
        assertEquals(0, result.failed)
        assertEquals(0, result.skipped)
        // 仅可发专家创建 contact / 选号 / 渲染 / 投递各一次。
        val contactCaptor = org.mockito.ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(expertContactRepository, Mockito.times(1)).save(
            captureValue(contactCaptor, ExpertContact(campaignId = 0L, orcidId = "", expertEmail = "", expertName = null))
        )
        assertEquals("0001", contactCaptor.value.orcidId)
        Mockito.verify(senderAccountAssignmentService, Mockito.times(1)).selectAccount(
            anyValue(expert("0001", "a@b.com")), anyValue(mutableListOf()), Mockito.anyBoolean(), anyValue(SenderBindingStock.EMPTY)
        )
        Mockito.verify(introductionMailComposer, Mockito.times(1)).compose(
            eqValue("chen"), anyValue(expert("0001", "a@b.com")), Mockito.isNull(), anyBooleanValue()
        )
        Mockito.verify(mailDeliveryService, Mockito.times(1)).send(
            anyValue(account("chen")), anyValue(ComposedMail("", "", ""))
        )
    }

    @Test
    fun `round loop last gate blocks null classification with no writes (I3-4)`() {
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(
            Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        )
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(emptyList())
        // 竞态场景：iterator 返回缺分类 profile —— 类型判定不命中（I4-1 唯一收口点）。
        stubScrolledExperts(listOf(expert("0001", "a@b.com").copy(expertClassification = null)))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)

        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION", roundSize = 10, roundsPerRun = 1,
            perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            expertTypes = listOf("PRODUCTION_RND")
        )
        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = true)

        // I-2/I-3: 空分类不匹配已配置类型 → selector 在取页时排除（fail-closed），目标为空。
        assertEquals(0, result.total)
        assertEquals(0, result.sent)
        assertEquals(0, result.failed)
        assertEquals(0, result.skipped)
        // 不创建 contact、不选账号、不渲染、不投递。
        Mockito.verify(expertContactRepository, Mockito.never()).save(Mockito.any(ExpertContact::class.java))
        Mockito.verify(senderAccountAssignmentService, Mockito.never()).selectAccount(
            anyValue(expert("0001", "a@b.com")), anyValue(mutableListOf()), Mockito.anyBoolean(), anyValue(SenderBindingStock.EMPTY)
        )
        Mockito.verifyNoInteractions(introductionMailComposer)
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `round loop last gate no longer rejects stale policy version (I4-1)`() {
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(
            Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        )
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(emptyList())
        // 竞态场景：旧策略版本 —— 04 之后版本不参与判定，类型命中即正常发送（I4-1 无版本门禁）。
        val stale = classification(ExpertType.PRODUCTION_RND).copy(version = "rnd-v1-legacy")
        stubScrolledExperts(listOf(expert("0001", "a@b.com").copy(expertClassification = stale)))
        Mockito.`when`(expertContactRepository.existsByOrcidId("0001")).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
        Mockito.`when`(senderAccountAssignmentService.selectAccount(anyValue(expert("0001", "a@b.com")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)))
            .thenReturn(account("chen"))
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("0001", "a@b.com")), Mockito.isNull(), anyBooleanValue()))
            .thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(anyValue(account("chen")), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg1", "SENT"))

        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION", roundSize = 10, roundsPerRun = 1,
            perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            expertTypes = listOf("PRODUCTION_RND")
        )
        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = true)

        assertEquals(1, result.total)
        assertEquals(1, result.sent)
        assertEquals(0, result.failed)
        assertEquals(0, result.skipped)
        Mockito.verify(mailDeliveryService, Mockito.times(1)).send(anyValue(account("chen")), anyValue(ComposedMail("", "", "")))
    }

    // ──── P5a: 研究方向三态 researchDirectionFilter（I-1 / I-2 / I-3）──────────

    private fun directionScope(filter: String) = RecipientScope(
        mailType = "INTRODUCTION", funnelLevels = setOf("CANDIDATE"),
        tags = emptyList(), regions = emptyList(),
        emailDomains = emptyList(), discipline = null,
        operatorStatuses = emptyList(),
        // I4-2: fixture 分类 PRODUCTION_RND —— 类型判定通过后再测方向维度（I-3 的 AND 语义）。
        expertTypes = listOf("PRODUCTION_RND"),
        researchDirectionFilter = filter
    )

    /** I-2: PRESENT 精确复用既有存在性 filter。 */
    private fun researchFieldsPresenceFilter(): Map<String, Any> =
        ExpertSearchService.fieldPresenceFilter("researchFields")

    /** I-2: ABSENT 是同一个 PRESENT filter 的 bool.must_not。 */
    private fun researchFieldsAbsenceFilter(): Map<String, Any> =
        mapOf("bool" to mapOf("must_not" to listOf(researchFieldsPresenceFilter())))

    @Test
    fun `buildEsFiltersForLevel adds no direction filter for ANY and the exact presence pair for PRESENT ABSENT (I-2)`() {
        val presence = researchFieldsPresenceFilter()
        val absence = researchFieldsAbsenceFilter()

        // I-1/I-2/I-3: ANY（旧任务默认）不得追加任何方向 filter —— 收件范围逐字不变。
        val anyFilters = invokeBuildEsFiltersForLevel(service, directionScope("ANY"), "CANDIDATE")
        assertFalse(anyFilters.contains(presence), "ANY must not add the presence filter")
        assertFalse(anyFilters.contains(absence), "ANY must not add the absence filter")

        // I-2: PRESENT 精确等于既有 ExpertSearchService.fieldPresenceFilter("researchFields")。
        val presentFilters = invokeBuildEsFiltersForLevel(service, directionScope("PRESENT"), "CANDIDATE")
        assertEquals(anyFilters.size + 1, presentFilters.size)
        assertEquals(presence, presentFilters[1], "PRESENT must be flat in bool.filter")

        // I-2: ABSENT 是同一个 filter 的 bool.must_not（等值于「不存在 或 term ""」）。
        val absentFilters = invokeBuildEsFiltersForLevel(service, directionScope("ABSENT"), "CANDIDATE")
        assertEquals(anyFilters.size + 1, absentFilters.size)
        assertEquals(absence, absentFilters[1], "ABSENT must be bool.must_not of the PRESENT filter")

        // MATERIAL_REMINDER 走同一 buildEsFiltersForLevel，方向三态同样生效。
        val reminderScope = directionScope("ABSENT").copy(
            mailType = "MATERIAL_REMINDER",
            funnelLevels = setOf("APPLICATION"),
            tags = listOf("承诺回复材料")
        )
        assertTrue(
            invokeBuildEsFiltersForLevel(service, reminderScope, "APPLICATION").contains(absence),
            "MATERIAL_REMINDER shares the same direction filter path"
        )
    }

    @Test
    fun `matchesExpert applies the direction three-state per profile with null and empty string absent (I-2)`() {
        val any = directionScope("ANY")
        val present = directionScope("PRESENT")
        val absent = directionScope("ABSENT")

        val cases = listOf(
            expert("0001", "a@b.com").copy(researchFields = null) to false,
            expert("0002", "b@b.com").copy(researchFields = "") to false,
            expert("0003", "c@b.com").copy(researchFields = "Quantum Computing") to true,
            // I-2: keyword 字段下纯空格串 `exists` 且非 `term ""` —— ES 算「有」，内存侧同口径。
            expert("0004", "d@b.com").copy(researchFields = " ") to true
        )
        cases.forEach { (profile, hasDirection) ->
            assertEquals(
                hasDirection, present.matchesExpert(profile),
                "PRESENT mismatch for researchFields=[${profile.researchFields}]"
            )
            assertEquals(
                !hasDirection, absent.matchesExpert(profile),
                "ABSENT mismatch for researchFields=[${profile.researchFields}]"
            )
            assertTrue(
                any.matchesExpert(profile),
                "ANY must not judge direction for researchFields=[${profile.researchFields}]"
            )
        }
    }

    @Test
    fun `countBySnapshot applies direction to retryable profiles exactly like the ES presence rule (I-2)`() {
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(
            Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        )
        val noDirection = ExpertContact(id = 1L, campaignId = 10L, orcidId = "ABS1", expertEmail = "abs@x.com", expertName = "A", currentStatus = "NEW")
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(noDirection))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("ABS1"))).thenReturn(
            listOf(expert("ABS1", "abs@x.com").copy(researchFields = null))
        )
        stubScrolledExperts(emptyList())

        // ABSENT: 缺方向的 retryable 保留，ES 侧同一快照追加 must_not 存在性 filter（I-2 状态空不限）。
        val absentFilters = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
        absentFilters.add(researchFieldsAbsenceFilter())
        absentFilters.add(ExpertSearchService.expertTypesFilter(listOf("PRODUCTION_RND", "ACADEMIC_RND", "HYBRID_RND"))!!)
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(absentFilters))).thenReturn(0L)
        val absentSummary = service.countBySnapshot(runScheduledSnapshot(researchDirectionFilter = "ABSENT"))
        assertEquals(1, absentSummary.retryable)
        assertEquals(1, absentSummary.totalSendable)

        // PRESENT: 同一个缺方向 retryable 被排除，ES 侧换成存在性 filter。
        val presentFilters = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
        presentFilters.add(researchFieldsPresenceFilter())
        presentFilters.add(ExpertSearchService.expertTypesFilter(listOf("PRODUCTION_RND", "ACADEMIC_RND", "HYBRID_RND"))!!)
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(presentFilters))).thenReturn(0L)
        val presentSummary = service.countBySnapshot(runScheduledSnapshot(researchDirectionFilter = "PRESENT"))
        assertEquals(0, presentSummary.retryable)
        assertEquals(0, presentSummary.totalSendable)

        // I-2 / I-3: 两条路径只认这两个精确 filter 列表（内存重试与 ES 预估同口径）。
        Mockito.verify(expertSearchService).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), eqValue(absentFilters), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )
        Mockito.verify(expertSearchService).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), eqValue(presentFilters), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )
    }

    @Test
    fun `preview and execution use identical direction filters for the same snapshot (I-2)`() {
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(
            Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        )
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(emptyList())

        val expectedFilters = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
        expectedFilters.add(researchFieldsAbsenceFilter())
        expectedFilters.add(ExpertSearchService.expertTypesFilter(listOf("PRODUCTION_RND"))!!)
        Mockito.`when`(expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters)))
            .thenReturn(2L)
        // I-3: 预估走 scroll + 最终谓词 —— 同一个 filter 列表必须同时命中预估与取页两条路径。
        Mockito.doAnswer { invocation ->
            val handler = invocation.getArgument<(List<ExpertProfile>) -> Boolean>(3)
            handler(listOf(expert("0001", "a@b.com"), expert("0002", "b@b.com")))
            null
        }.`when`(expertSearchService).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )

        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION", roundSize = 10, roundsPerRun = 1,
            perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE",
            expertTypes = listOf("PRODUCTION_RND"),
            researchDirectionFilter = "ABSENT"
        )

        // 预估路径（countBySnapshot → resolveScope → countEsTargets）
        val preview = service.countBySnapshot(snapshot)
        assertEquals(2, preview.pending)
        assertEquals(0, preview.retryable)
        assertEquals(2, preview.totalSendable)

        // 执行路径（无可用账号 → 停在轮次闸口，不发信）
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue())).thenReturn(emptyList())
        val result = service.run(snapshot, 12348L, ExecutionMode.MANUAL, oneRoundOnly = true)
        assertEquals(preview.totalSendable, result.total)

        // I-2 / I-3: 调用次数 = 预估 scroll(1) + 执行前估算 scroll(1) + 执行取页 count(1)，
        // 全部命中同一 filter 列表 —— 预估人数与实际收件筛选同口径。
        Mockito.verify(expertSearchService, Mockito.times(2))
            .scrollExpertsFiltered(
                eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters), eqValue(500),
                anyValue({ _: List<ExpertProfile> -> true })
            )
        Mockito.verify(expertSearchService, Mockito.times(1))
            .countExperts(eqValue(ExpertIndexLevel.CANDIDATE), eqValue(expectedFilters))
    }

    @Test
    fun `ABSENT combined with a required researchFields template gate selects nobody (I-3)`() {
        Mockito.`when`(mailComposeTemplateService.requiredEsFields(42L)).thenReturn(listOf("researchFields"))
        val snapshot = BatchExecutionSnapshot(
            mailType = "INTRODUCTION", roundSize = 10, roundsPerRun = 1,
            perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE", templateId = 42L, gateFilterEnabled = true,
            expertTypes = listOf("PRODUCTION_RND"),
            researchDirectionFilter = "ABSENT"
        )
        val scope = invokeResolveScope(service, snapshot)
        assertEquals(listOf("researchFields"), scope.gateEsFields)
        assertEquals("ABSENT", scope.researchDirectionFilter)

        // I-3: 方向与模板门禁是独立维度、取 AND —— 存在性与其否定同时下发，合法结果为 0，
        // 且任何一项都不得被静默丢弃（丢弃任一项都会放进缺必填变量的专家）。
        val filters = invokeBuildEsFiltersForLevel(service, scope, "CANDIDATE")
        assertTrue(filters.contains(researchFieldsPresenceFilter()), "template gate presence filter must survive")
        assertTrue(filters.contains(researchFieldsAbsenceFilter()), "direction absence filter must survive")

        // 内存重试侧同样恒 false —— 缺方向者不可能进入本次目标。
        assertFalse(scope.matchesExpert(expert("0001", "a@b.com").copy(researchFields = null)))
        assertFalse(scope.matchesExpert(expert("0002", "b@b.com").copy(researchFields = "AI")))
    }

    @Test
    fun `persisted config carries the direction state into the snapshot and ES filters (I-1)`() {
        val mapper = ObjectMapper().registerKotlinModule()
        val legacyEntity = BatchSendTaskConfig(
            id = 7L, configName = "旧任务", mailType = "INTRODUCTION",
            autoEnabled = false, cron = "0 0 0 * * ?", roundSize = 50,
            perMailIntervalMs = 1000, perRoundIntervalMs = 60000, selfCheckTtlMinutes = 30,
            expertTypesJson = """["PRODUCTION_RND"]"""
        )

        // I-1: 迁移前的存量任务（未写该列）读出 ANY；旧 BatchSendConfig → snapshot 派生同样不带方向。
        assertEquals("ANY", legacyEntity.researchDirectionFilter)
        val legacyScope = invokeResolveScope(service, legacyEntity.toExecutionSnapshot(mapper))
        assertEquals("ANY", legacyScope.researchDirectionFilter)
        val legacyFilters = invokeBuildEsFiltersForLevel(service, legacyScope, "CANDIDATE")
        assertFalse(legacyFilters.contains(researchFieldsPresenceFilter()), "legacy ANY must not narrow the audience")
        assertFalse(legacyFilters.contains(researchFieldsAbsenceFilter()), "legacy ANY must not narrow the audience")

        // 保存为 ABSENT 的任务：快照与 ES filter 全链路保留。
        val absentScope = invokeResolveScope(
            service,
            legacyEntity.copy(researchDirectionFilter = "ABSENT").toExecutionSnapshot(mapper)
        )
        assertEquals("ABSENT", absentScope.researchDirectionFilter)
        assertTrue(
            invokeBuildEsFiltersForLevel(service, absentScope, "CANDIDATE").contains(researchFieldsAbsenceFilter())
        )
    }

    @Test
    fun `create and update persist the direction state and never reset it (I-1)`() {
        val configRepository = Mockito.mock(BatchSendTaskConfigRepository::class.java)
        val eventPublisher = Mockito.mock(org.springframework.context.ApplicationEventPublisher::class.java)
        val execService = Mockito.mock(com.weibo.talentintroduction.task.service.TaskExecutionService::class.java)
        val configService = BatchSendTaskConfigService(
            repository = configRepository,
            mailComposeTemplateService = mailComposeTemplateService,
            objectMapper = ObjectMapper().registerKotlinModule(),
            eventPublisher = eventPublisher,
            taskExecutionService = execService
        )
        Mockito.`when`(configRepository.findByConfigNameAndDeletedAtIsNull(Mockito.anyString())).thenReturn(null)
        Mockito.`when`(execService.lastExecutedAtByBatchConfigIds(Mockito.anyList())).thenReturn(emptyMap())
        val savedEntities = mutableListOf<BatchSendTaskConfig>()
        Mockito.`when`(configRepository.save(Mockito.any(BatchSendTaskConfig::class.java))).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as BatchSendTaskConfig
            savedEntities.add(entity)
            entity.copy(id = 31L)
        }

        // 未传值 → ANY（旧 typed 客户端与旧前端不发该字段）。
        val defaultView = configService.create(
            BatchSendTaskConfigCreateCommand(
                configName = "默认方向任务", cron = "0 0 9 * * ?", roundSize = 10,
                perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
                expertTypes = listOf("PRODUCTION_RND")
            )
        )
        assertEquals("ANY", defaultView.researchDirectionFilter)

        // 显式 ABSENT → 落库并原样回显（list/get 共用 toView）。
        val absentView = configService.create(
            BatchSendTaskConfigCreateCommand(
                configName = "无方向任务", cron = "0 0 9 * * ?", roundSize = 10,
                perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
                expertTypes = listOf("PRODUCTION_RND"),
                researchDirectionFilter = "ABSENT"
            )
        )
        assertEquals("ABSENT", absentView.researchDirectionFilter)
        assertEquals("ABSENT", savedEntities.last().researchDirectionFilter)

        // 编辑改回 PRESENT → 落库为 PRESENT（不是被默认值重置 / 也不保留旧值）。
        Mockito.`when`(configRepository.findByIdAndDeletedAtIsNull(31L)).thenReturn(
            savedEntities.last().copy(id = 31L)
        )
        val updated = configService.update(
            31L,
            BatchSendTaskConfigUpdateCommand(
                configName = "无方向任务", autoEnabled = false, cron = "0 0 9 * * ?", roundSize = 10,
                perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
                expertTypes = listOf("PRODUCTION_RND"),
                researchDirectionFilter = "PRESENT"
            )
        )
        assertEquals("PRESENT", updated.researchDirectionFilter)
        assertEquals("PRESENT", savedEntities.last().researchDirectionFilter)
    }

    @Test
    fun `create rejects an illegal direction state and saves nothing (I-1)`() {
        val configRepository = Mockito.mock(BatchSendTaskConfigRepository::class.java)
        val configService = BatchSendTaskConfigService(
            repository = configRepository,
            mailComposeTemplateService = mailComposeTemplateService,
            objectMapper = ObjectMapper().registerKotlinModule(),
            eventPublisher = Mockito.mock(org.springframework.context.ApplicationEventPublisher::class.java),
            taskExecutionService = Mockito.mock(com.weibo.talentintroduction.task.service.TaskExecutionService::class.java)
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            configService.create(
                BatchSendTaskConfigCreateCommand(
                    configName = "非法方向任务", cron = "0 0 9 * * ?", roundSize = 10,
                    perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
                    expertTypes = listOf("PRODUCTION_RND"),
                    researchDirectionFilter = "MAYBE"
                )
            )
        }
        assertTrue(ex.message!!.contains("researchDirectionFilter"))
        assertTrue(ex.message!!.contains("ABSENT"), "message must carry the allowed list: ${ex.message}")
        Mockito.verify(configRepository, Mockito.never()).save(Mockito.any(BatchSendTaskConfig::class.java))
    }

    @Test
    fun `updateLegacyConfig preserves the existing direction state (I-1)`() {
        val configRepository = Mockito.mock(BatchSendTaskConfigRepository::class.java)
        val eventPublisher = Mockito.mock(org.springframework.context.ApplicationEventPublisher::class.java)
        val execService = Mockito.mock(com.weibo.talentintroduction.task.service.TaskExecutionService::class.java)
        val configService = BatchSendTaskConfigService(
            repository = configRepository,
            mailComposeTemplateService = mailComposeTemplateService,
            objectMapper = ObjectMapper().registerKotlinModule(),
            eventPublisher = eventPublisher,
            taskExecutionService = execService
        )
        val existing = BatchSendTaskConfig(
            id = 2L, configName = "默认介绍邮件任务", mailType = "INTRODUCTION",
            autoEnabled = false, cron = "0 0 0 * * ?", roundSize = 50,
            perMailIntervalMs = 1000, perRoundIntervalMs = 60000, selfCheckTtlMinutes = 30,
            funnelLevel = "CANDIDATE", tagsJson = "[]", regionsJson = "[]",
            emailDomainsJson = "[]", discipline = null, operatorStatusesJson = "[]",
            expertTypesJson = """["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]""",
            templateId = null, legacyCode = "INTRODUCTION",
            researchDirectionFilter = "ABSENT",
            createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
        )
        Mockito.`when`(configRepository.findByLegacyCode("INTRODUCTION")).thenReturn(existing)
        Mockito.`when`(configRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(existing)
        Mockito.`when`(configRepository.findByConfigNameAndDeletedAtIsNull("默认介绍邮件任务")).thenReturn(existing)
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

        // I-1: 旧 typed API 不传方向三态 —— 必须显式保留存量值（漏写会命中 Kotlin 默认值静默重置为 ANY）。
        val captor = org.mockito.ArgumentCaptor.forClass(BatchSendTaskConfig::class.java)
        Mockito.verify(configRepository).save(captor.capture())
        assertEquals("ABSENT", captor.value.researchDirectionFilter)
    }

    @Test
    fun `startManual rejects an illegal direction state with 422 before launching (I-1)`() {
        val control = BatchSendControlService(
            progressStore = progressStore,
            taskExecutionService = taskExecutionService,
            manualInitialOutreachService = service,
            batchSendSettingService = batchSendSettingService,
            batchSendTaskConfigRepository = Mockito.mock(BatchSendTaskConfigRepository::class.java),
            mailSenderAccountService = mailSenderAccountService,
            mailComposeTemplateService = mailComposeTemplateService,
            objectMapper = ObjectMapper().registerKotlinModule(),
            manualOutreachExecutor = Mockito.mock(Executor::class.java)
        )

        val response = control.startManual(
            ManualBatchExecutionRequest(
                sourceConfigId = null,
                sourceUpdatedAt = null,
                snapshot = BatchExecutionSnapshot(
                    mailType = "INTRODUCTION", roundSize = 10, roundsPerRun = 1,
                    perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
                    funnelLevel = "CANDIDATE",
                    expertTypes = listOf("PRODUCTION_RND"),
                    researchDirectionFilter = "MAYBE"
                )
            )
        )

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertTrue(
            response.body?.get("message").toString().contains("researchDirectionFilter"),
            "validation message must name the field: ${response.body}"
        )
        // 校验先于启动：不占用执行 token、不进入任何发送链路。
        Mockito.verifyNoInteractions(progressStore)
    }

    // ── 发件账号白名单与已绑定跳过（I-2 / I-3 / I-4 / I-5）──────────────────────────

    @Test
    fun `startManual rejects an unknown senderAccountCode with 422 before launching (I-2)`() {
        Mockito.`when`(mailSenderAccountService.listAccounts()).thenReturn(listOf(account("LuKai")))
        val executor = Mockito.mock(Executor::class.java)
        val control = BatchSendControlService(
            progressStore = progressStore,
            taskExecutionService = taskExecutionService,
            manualInitialOutreachService = service,
            batchSendSettingService = batchSendSettingService,
            batchSendTaskConfigRepository = Mockito.mock(BatchSendTaskConfigRepository::class.java),
            mailSenderAccountService = mailSenderAccountService,
            mailComposeTemplateService = mailComposeTemplateService,
            objectMapper = ObjectMapper().registerKotlinModule(),
            manualOutreachExecutor = executor
        )

        val response = control.startManual(
            ManualBatchExecutionRequest(
                sourceConfigId = null,
                sourceUpdatedAt = null,
                snapshot = BatchExecutionSnapshot(
                    mailType = "INTRODUCTION", roundSize = 10, roundsPerRun = 1,
                    perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
                    funnelLevel = "CANDIDATE",
                    expertTypes = listOf("PRODUCTION_RND"),
                    // I-1/I-2: 未知 code 必须 422，绝不能被当作 []（= 放宽成全池）。
                    senderAccountCodes = listOf("DOES_NOT_EXIST")
                )
            )
        )

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertTrue(
            response.body?.get("message").toString().contains("DOES_NOT_EXIST"),
            "validation message must name the offending code: ${response.body}"
        )
        // 校验先于启动：不占用执行 token、不进入任何发送链路。
        Mockito.verifyNoInteractions(progressStore)
        Mockito.verifyNoInteractions(executor)
    }

    @Test
    fun `startManual accepts a snapshot whose senderAccountCodes exist (I-2)`() {
        Mockito.`when`(mailSenderAccountService.listAccounts()).thenReturn(listOf(account("LuKai")))
        // 额度门禁置 0：合法 code 必须穿过 code 校验、只在额度处被拦（409 ≠ 422）。
        Mockito.`when`(mailSenderAccountService.remainingDailyCapacity(Mockito.anyBoolean())).thenReturn(0)
        val control = BatchSendControlService(
            progressStore = progressStore,
            taskExecutionService = taskExecutionService,
            manualInitialOutreachService = service,
            batchSendSettingService = batchSendSettingService,
            batchSendTaskConfigRepository = Mockito.mock(BatchSendTaskConfigRepository::class.java),
            mailSenderAccountService = mailSenderAccountService,
            mailComposeTemplateService = mailComposeTemplateService,
            objectMapper = ObjectMapper().registerKotlinModule(),
            manualOutreachExecutor = Mockito.mock(Executor::class.java)
        )

        val response = control.startManual(
            ManualBatchExecutionRequest(
                sourceConfigId = null,
                sourceUpdatedAt = null,
                snapshot = BatchExecutionSnapshot(
                    mailType = "INTRODUCTION", roundSize = 10, roundsPerRun = 1,
                    perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
                    funnelLevel = "CANDIDATE",
                    expertTypes = listOf("PRODUCTION_RND"),
                    senderAccountCodes = listOf("LuKai")
                )
            )
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertTrue(
            response.body?.get("message").toString().contains("额度"),
            "capacity gate must be the stop point, not code validation: ${response.body}"
        )
        Mockito.verifyNoInteractions(progressStore)
    }

    @Test
    fun `run sends only from the selected sender account and never falls back to an unselected one (I-2)`() {
        val selected = account("LuKai")
        val unselected = account("LuKai_QF")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(emptyList())
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue())).thenReturn(listOf(selected, unselected))
        // I-2: 非空白名单走五参选号；未选中账号即便可用也不得被选中。
        Mockito.`when`(senderAccountAssignmentService.selectAccount(
            anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY), eqValue(setOf("LuKai"))
        )).thenReturn(selected)
        // 旧四参路径（= 不限）在本用例里指向未选中账号，任何回退都会立刻暴露。
        Mockito.`when`(senderAccountAssignmentService.selectAccount(
            anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)
        )).thenReturn(unselected)
        Mockito.`when`(introductionMailComposer.compose(eqValue("LuKai"), anyValue(expert("", "")), Mockito.isNull(), anyBooleanValue()))
            .thenReturn(ComposedMail("a@b.com", "Subject", "Body"))
        Mockito.`when`(mailDeliveryService.send(eqValue(selected), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg", "SENT"))

        val snapshot = introSnapshot(roundSize = 10, roundsPerRun = 1).copy(senderAccountCodes = listOf("LuKai"))
        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = true)

        assertEquals(1, result.sent)
        Mockito.verify(senderAccountAssignmentService).selectAccount(
            anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY), eqValue(setOf("LuKai"))
        )
        Mockito.verify(mailDeliveryService, Mockito.never())
            .send(eqValue(unselected), anyValue(ComposedMail("", "", "")))
        Mockito.verify(introductionMailComposer, Mockito.never())
            .compose(eqValue("LuKai_QF"), anyValue(expert("", "")), Mockito.isNull(), anyBooleanValue())
    }

    @Test
    fun `run stops without falling back when the selected account has no capacity (I-2)`() {
        val selected = account("LuKai")
        val unselected = account("LuKai_QF")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue())).thenReturn(listOf(selected, unselected))
        Mockito.`when`(senderAccountAssignmentService.selectAccount(
            anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY), eqValue(setOf("LuKai"))
        )).thenThrow(com.weibo.talentintroduction.mail.service.NoAvailableSenderAccountException("selected account exhausted"))
        Mockito.`when`(senderAccountAssignmentService.selectAccount(
            anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)
        )).thenReturn(unselected)

        val snapshot = introSnapshot(roundSize = 10, roundsPerRun = 1).copy(senderAccountCodes = listOf("LuKai"))
        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = true)

        assertEquals(0, result.sent)
        assertEquals("NO_AVAILABLE_ACCOUNT", result.stopReason)
        Mockito.verify(mailDeliveryService, Mockito.never())
            .send(eqValue(unselected), anyValue(ComposedMail("", "", "")))
        Mockito.verify(mailDeliveryService, Mockito.never())
            .send(eqValue(selected), anyValue(ComposedMail("", "", "")))
    }

    @Test
    fun `run excludes a bound NEW retryable contact from preview and execution (I-3 I-4)`() {
        val acc = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        val boundContact = ExpertContact(
            id = 901L, campaignId = 10L, orcidId = "0001", expertEmail = "bound@b.com",
            expertName = "Bound", currentStatus = "NEW",
            boundSenderAccountCode = "other-account",
            senderAccountBoundAt = LocalDateTime.of(2026, 1, 1, 12, 0)
        )
        val freeContact = ExpertContact(
            id = 902L, campaignId = 10L, orcidId = "0002", expertEmail = "free@b.com",
            expertName = "Free", currentStatus = "NEW"
        )
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(listOf(boundContact, freeContact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(901L)).thenReturn(emptyList())
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(902L)).thenReturn(emptyList())
        Mockito.`when`(expertSearchService.searchByOrcidIds(listOf("0001", "0002")))
            .thenReturn(listOf(expert("0001", "bound@b.com"), expert("0002", "free@b.com")))
        Mockito.`when`(expertContactRepository.findByOrcidIdIn(listOf("0001", "0002")))
            .thenReturn(listOf(boundContact, freeContact))
        stubScrolledExperts(emptyList())
        Mockito.`when`(senderAccountAssignmentService.selectAccount(
            anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)
        )).thenReturn(acc)
        Mockito.`when`(introductionMailComposer.compose(eqValue("chen"), anyValue(expert("", "")), Mockito.isNull(), anyBooleanValue()))
            .thenAnswer { invocation ->
                ComposedMail(invocation.getArgument<ExpertProfile>(1).email ?: "", "Subject", "Body")
            }
        Mockito.`when`(mailDeliveryService.send(eqValue(acc), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("msg", "SENT"))
        val mailCaptor = org.mockito.ArgumentCaptor.forClass(ComposedMail::class.java)

        // I-4: 预估（countBySnapshot）与执行共用同一目标构造函数。
        val preview = service.countBySnapshot(introSnapshot(roundSize = 10, roundsPerRun = 1))
        val result = service.run(introSnapshot(roundSize = 10, roundsPerRun = 1), 12345L, ExecutionMode.MANUAL, oneRoundOnly = true)

        assertEquals(1, preview.totalSendable)
        assertEquals(1, result.total)
        assertEquals(1, result.sent)
        // 已绑定目标：不组稿、不发送、不改绑、不新建联系人。
        Mockito.verify(mailDeliveryService, Mockito.times(1))
            .send(eqValue(acc), captureValue(mailCaptor, ComposedMail("", "", "")))
        assertEquals("free@b.com", mailCaptor.value.to)
        Mockito.verify(expertContactRepository, Mockito.never()).updateBindingById(
            anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            Mockito.any()
        )
        Mockito.verify(expertContactRepository, Mockito.never()).save(Mockito.any(ExpertContact::class.java))
    }

    @Test
    fun `run skips an ES target bound in another campaign with the bound reason (I-3 I-4)`() {
        val acc = account("chen")
        val campaign = Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        val otherCampaignContact = ExpertContact(
            id = 903L, campaignId = 77L, orcidId = "0001", expertEmail = "a@b.com",
            expertName = "Other", currentStatus = "WAITING_REPLY",
            boundSenderAccountCode = "LuKai_QF"
        )
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(campaign)
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW")).thenReturn(emptyList())
        stubScrolledExperts(listOf(expert("0001", "a@b.com")))
        Mockito.`when`(expertContactRepository.findByOrcidIdIn(listOf("0001"))).thenReturn(listOf(otherCampaignContact))
        Mockito.`when`(senderAccountAssignmentService.selectAccount(
            anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)
        )).thenReturn(acc)

        val result = service.run(introSnapshot(roundSize = 10, roundsPerRun = 1), 12345L, ExecutionMode.MANUAL, oneRoundOnly = true)

        assertEquals(1, result.total)
        assertEquals(0, result.sent)
        assertEquals(1, result.skipped)
        // I-4: 跳过原因必须可见（"专家已绑定发件账号"），而不是被记成 SMTP 失败。
        val boundSkip = result.outcome?.skippedReasons?.get(BatchOutcomeReasonCodes.BOUND_SENDER_ALREADY_SET)
        assertEquals(1, boundSkip?.count)
        assertEquals("专家已绑定发件账号", boundSkip?.label)
        assertEquals(0, result.failed)
        Mockito.verifyNoInteractions(mailDeliveryService)
        Mockito.verify(senderAccountAssignmentService, Mockito.never()).selectAccount(
            anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)
        )
    }

    @Test
    fun `run with empty senderAccountCodes keeps the legacy selection path (I-2)`() {
        val acc = account("chen")
        stubIntroSendPipeline(acc, listOf(expert("0001", "a@b.com")))

        val result = service.run(introSnapshot(roundSize = 10, roundsPerRun = 1), 12345L, ExecutionMode.MANUAL, oneRoundOnly = true)

        assertEquals(1, result.sent)
        Mockito.verify(senderAccountAssignmentService).selectAccount(
            anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)
        )
    }

    @Test
    fun `material reminder sends only from the selected sender account (I-2)`() {
        val contactId = 7L
        val contact = ExpertContact(
            id = contactId, campaignId = 10L, orcidId = "R004", expertEmail = "r4@test.com",
            expertName = "R4", currentStatus = "WAITING_REPLY"
        )
        val ep = expert("R004", "r4@test.com")
        val selected = account("LuKai")
        val unselected = account("LuKai_QF")
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
        Mockito.`when`(mailSenderAccountService.listSendableAccounts(anyBooleanValue()))
            .thenReturn(listOf(selected, unselected))
        Mockito.`when`(senderAccountAssignmentService.selectAccount(
            anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY), eqValue(setOf("LuKai"))
        )).thenReturn(selected)
        Mockito.`when`(senderAccountAssignmentService.selectAccount(
            anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)
        )).thenReturn(unselected)
        val cmdCaptor = org.mockito.ArgumentCaptor.forClass(
            com.weibo.talentintroduction.mail.service.ManualMailSendCommand::class.java
        )
        Mockito.`when`(manualExpertMailService.sendManualMail(
            eqValue(contactId),
            anyValue(com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", "")), anyBooleanValue()
        )).thenReturn(
            com.weibo.talentintroduction.mail.service.ManualMailSendResult(
                contactId = contactId, senderAccountCode = "LuKai",
                mailType = "MATERIAL_REMINDER", subject = "Subj",
                sendStatus = "SENT", messageId = "msg-sel"
            )
        )

        val snapshot = BatchExecutionSnapshot(
            mailType = "MATERIAL_REMINDER",
            roundSize = 10, roundsPerRun = 1,
            perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
            templateId = 10L,
            senderAccountCodes = listOf("LuKai")
        )
        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = true)

        assertEquals(1, result.sent)
        Mockito.verify(manualExpertMailService).sendManualMail(
            eqValue(contactId),
            captureValue(cmdCaptor, com.weibo.talentintroduction.mail.service.ManualMailSendCommand("", "", "")), anyBooleanValue()
        )
        // I-2: 实际外发身份只能是选中账号。
        assertEquals("LuKai", cmdCaptor.value.senderAccountCode)
    }

    @Test
    fun `material reminder with every target bound previews and sends zero (I-3 I-4)`() {
        val contact = ExpertContact(
            id = 904L, campaignId = 10L, orcidId = "R004", expertEmail = "r4@test.com",
            expertName = "R4", currentStatus = "WAITING_REPLY",
            boundSenderAccountCode = "LuKai_QF"
        )
        val ep = expert("R004", "r4@test.com")
        Mockito.`when`(expertSearchService.countExperts(
            eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList())
        )).thenReturn(1L)
        Mockito.`when`(expertSearchService.searchExpertsFiltered(
            eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList()), eqValue(0), anyInt()
        )).thenReturn(listOf(ep))
        Mockito.`when`(expertContactRepository.findByOrcidIdIn(anyValue(emptyList())))
            .thenReturn(listOf(contact))

        val snapshot = BatchExecutionSnapshot(
            mailType = "MATERIAL_REMINDER",
            roundSize = 10, roundsPerRun = 1,
            perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
            templateId = 10L
        )

        val preview = service.countBySnapshot(snapshot)
        val result = service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = true)

        // I-4: 预估与执行同源 —— 全部已绑定 ⇒ 两侧都是 0。
        assertEquals(0, preview.totalSendable)
        assertEquals(0, result.total)
        assertEquals(0, result.sent)
        Mockito.verifyNoInteractions(manualExpertMailService)
    }

    // ──── 发送前邮箱验证（I-1/I-2/I-3/I-4/I-6/I-7）────

    @Test
    fun `verification disabled never touches the verification service (I-1)`() {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(expert("0001", "a@b.com")))

        val result = service.run(runScheduledSnapshot(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(1, result.sent)
        Mockito.verifyNoInteractions(batchEmailVerificationService)
    }

    @Test
    fun `material reminder with verification enabled is rejected before any business write (I-1)`() {
        val snapshot = BatchExecutionSnapshot(
            mailType = "MATERIAL_REMINDER",
            roundSize = 10, roundsPerRun = 1,
            perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
            templateId = 42L,
            emailVerificationEnabled = true
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.run(snapshot, 12345L, ExecutionMode.MANUAL, oneRoundOnly = true)
        }

        Mockito.verify(expertContactRepository, Mockito.never()).save(Mockito.any(ExpertContact::class.java))
        Mockito.verify(mailSendAttemptRepository, Mockito.never()).save(Mockito.any(MailSendAttempt::class.java))
        Mockito.verifyNoInteractions(manualExpertMailService)
        Mockito.verifyNoInteractions(mailDeliveryService)
        Mockito.verifyNoInteractions(batchEmailVerificationService)
        Mockito.verify(campaignRepository, Mockito.never()).save(Mockito.any(Campaign::class.java))
    }

    @Test
    fun `verification enabled requires a configured api key before any business write (I-8)`() {
        Mockito.doThrow(IllegalStateException("未配置 EMAILABLE_API_KEY，无法开启发送前邮箱验证"))
            .`when`(batchEmailVerificationService).requireConfiguredApiKey()

        assertThrows(IllegalStateException::class.java) {
            service.run(introSnapshotWithVerification(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)
        }

        // 入口检查先于任何目标读取、建行、选号与投递。
        Mockito.verifyNoInteractions(expertSearchService)
        Mockito.verifyNoInteractions(mailDeliveryService)
        Mockito.verifyNoInteractions(campaignRepository)
        Mockito.verify(expertContactRepository, Mockito.never()).save(Mockito.any(ExpertContact::class.java))
        Mockito.verify(mailSendAttemptRepository, Mockito.never()).save(Mockito.any(MailSendAttempt::class.java))
    }

    @Test
    fun `a rejected address is skipped without contact attempt smtp or account selection (I-2 I-3 I-7)`() {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(expert("0001", "a@b.com")))
        stubVerificationDecision(rejected = setOf("a@b.com"))

        val result = service.run(introSnapshotWithVerification(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.sent)
        assertEquals(0, result.failed)
        assertEquals(1, result.skipped)
        assertEquals(0, result.remaining)
        assertEquals(
            1,
            result.outcome?.skippedReasons?.get(BatchOutcomeReasonCodes.EMAIL_VERIFICATION_REJECTED)?.count ?: 0
        )
        // 验证不通过：不建/绑 contact、不写 PREPARED、不选号、不发 SMTP、不计发送量。
        Mockito.verify(expertContactRepository, Mockito.never()).save(Mockito.any(ExpertContact::class.java))
        Mockito.verify(mailSendAttemptRepository, Mockito.never()).save(Mockito.any(MailSendAttempt::class.java))
        Mockito.verify(senderAccountAssignmentService, Mockito.never()).selectAccount(
            anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)
        )
        Mockito.verify(mailDeliveryService, Mockito.never()).send(anyValue(account), anyValue(ComposedMail("", "", "")))
        Mockito.verify(introductionMailComposer, Mockito.never())
            .compose(Mockito.anyString(), anyValue(expert("", "")), Mockito.any(), anyBooleanValue())
        Mockito.verifyNoInteractions(txHelper)
    }

    @Test
    fun `a policy skipped address is skipped without contact smtp account selection or abnormal tag (I-2 I-3)`() {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(expert("0001", "a@b.com")))
        stubVerificationDecision(policySkipped = setOf("a@b.com"))

        val result = service.run(introSnapshotWithVerification(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.sent)
        assertEquals(0, result.failed)
        assertEquals(1, result.skipped)
        assertEquals(
            1,
            result.outcome?.skippedReasons?.get(BatchOutcomeReasonCodes.EMAIL_VERIFICATION_POLICY_SKIP)?.count ?: 0
        )
        assertNull(result.outcome?.skippedReasons?.get(BatchOutcomeReasonCodes.EMAIL_VERIFICATION_REJECTED))
        // 策略跳过：不建/绑 contact、不写 PREPARED、不选号、不发 SMTP、不计发送量、不写发送结论。
        Mockito.verify(expertContactRepository, Mockito.never()).save(Mockito.any(ExpertContact::class.java))
        Mockito.verify(mailSendAttemptRepository, Mockito.never()).save(Mockito.any(MailSendAttempt::class.java))
        Mockito.verify(senderAccountAssignmentService, Mockito.never()).selectAccount(
            anyValue(expert("", "")), anyValue(mutableListOf()), anyBooleanValue(), anyValue(SenderBindingStock.EMPTY)
        )
        Mockito.verify(mailDeliveryService, Mockito.never()).send(anyValue(account), anyValue(ComposedMail("", "", "")))
        Mockito.verify(batchEmailVerificationService, Mockito.never())
            .recordSend(Mockito.anyLong(), Mockito.anyString(), Mockito.any())
        Mockito.verify(introductionMailComposer, Mockito.never())
            .compose(Mockito.anyString(), anyValue(expert("", "")), Mockito.any(), anyBooleanValue())
        Mockito.verifyNoInteractions(txHelper)
    }

    @Test
    fun `policy skipped addresses do not consume the successful send quota`() {
        val account = account("chen")
        val experts = listOf(
            expert("0001", "a@b.com"), expert("0002", "b@b.com"), expert("0003", "c@b.com"),
            expert("0004", "d@b.com"), expert("0005", "e@b.com"), expert("0006", "f@b.com")
        )
        stubIntroSendPipeline(account, experts)
        stubIntroChunkedExperts(experts, pageSize = 4)
        // 共享 fixture 的 compose 固定返回 a@b.com —— 多邮箱用例必须按专家返回其真实收件地址，
        // 否则 SMTP 前的「收件地址 = 已验证地址」断言会（正确地）终止本次执行。
        Mockito.`when`(introductionMailComposer.compose(anyValue("chen"), anyValue(expert("", "")), Mockito.any(), anyBooleanValue()))
            .thenAnswer { invocation ->
                val profile = invocation.getArgument<ExpertProfile>(1)
                ComposedMail(profile.email.orEmpty(), "Subject", "Body")
            }
        stubVerificationDecision(policySkipped = setOf("b@b.com", "c@b.com", "d@b.com"))

        val result = service.run(
            introSnapshotWithVerification(roundSize = 2),
            12345L,
            ExecutionMode.MANUAL,
            oneRoundOnly = false
        )

        // 策略跳过仍继续扫描补足本轮成功配额；达到成功上限后不验证第 6 个目标。
        assertEquals(2, result.sent)
        assertEquals(3, result.skipped)
        assertEquals(1, result.remaining)
        assertEquals(
            3,
            result.outcome?.skippedReasons?.get(BatchOutcomeReasonCodes.EMAIL_VERIFICATION_POLICY_SKIP)?.count ?: 0
        )
        Mockito.verify(batchEmailVerificationService, Mockito.times(5))
            .verify(anyValue(verificationContext), anyValue(verificationTarget()), anyAllowedStates())
        Mockito.verify(mailDeliveryService, Mockito.times(2)).send(anyValue(account), anyValue(ComposedMail("", "", "")))
    }

    @Test
    fun `the run passes this snapshot's allow list to every verification (I-1)`() {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(expert("0001", "a@b.com")))
        val seen = mutableListOf<List<String>?>()
        Mockito.`when`(
            batchEmailVerificationService.beginExecution(Mockito.anyLong(), anyValue<() -> Boolean>({ false }))
        ).thenReturn(verificationContext)
        Mockito.`when`(
            batchEmailVerificationService.verify(
                anyValue(verificationContext), anyValue(verificationTarget()), anyAllowedStates()
            )
        ).thenAnswer { invocation ->
            seen += invocation.getArgument<List<String>?>(2)
            VerificationResult.Passed(
                rowId = 555L,
                normalizedEmail = normalizeVerificationEmail(invocation.getArgument<EmailVerificationTarget>(1).email)
            )
        }
        Mockito.`when`(batchEmailVerificationService.markSending(Mockito.anyLong())).thenReturn(true)

        val result = service.run(
            // 快照里顺序打乱也要按固定顺序复制成有效集合。
            introSnapshotWithVerification().copy(emailVerificationAllowedStates = listOf("unknown", "deliverable")),
            12345L,
            ExecutionMode.MANUAL,
            oneRoundOnly = false
        )

        assertEquals(1, result.sent)
        assertEquals(listOf(listOf("deliverable", "unknown")), seen)
    }

    @Test
    fun `legacy snapshots without an allow list keep passing all three explicit states`() {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(expert("0001", "a@b.com")))
        val seen = mutableListOf<List<String>?>()
        Mockito.`when`(
            batchEmailVerificationService.beginExecution(Mockito.anyLong(), anyValue<() -> Boolean>({ false }))
        ).thenReturn(verificationContext)
        Mockito.`when`(
            batchEmailVerificationService.verify(
                anyValue(verificationContext), anyValue(verificationTarget()), anyAllowedStates()
            )
        ).thenAnswer { invocation ->
            seen += invocation.getArgument<List<String>?>(2)
            VerificationResult.Passed(555L, normalizeVerificationEmail(invocation.getArgument<EmailVerificationTarget>(1).email))
        }
        Mockito.`when`(batchEmailVerificationService.markSending(Mockito.anyLong())).thenReturn(true)

        val result = service.run(introSnapshotWithVerification(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(1, result.sent)
        assertEquals(listOf(null), seen)
    }

    @Test
    fun `a verification service failure stops the run with remaining preserved and no abnormal tag (I-4 I-7)`() {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(expert("0001", "a@b.com"), expert("0002", "b@b.com"), expert("0003", "c@b.com")))
        stubVerificationDecision(failing = "b@b.com")

        val result = service.run(
            introSnapshotWithVerification(roundSize = 3),
            12345L,
            ExecutionMode.MANUAL,
            oneRoundOnly = false
        )

        assertEquals(1, result.sent)
        assertEquals(0, result.failed)
        assertEquals(0, result.skipped)
        assertEquals(2, result.remaining)
        assertEquals("PARTIAL_SUCCESS", result.finalStatus)
        assertEquals("PARTIAL_SUCCESS", result.taskFinalStatus)
        assertEquals("EMAIL_VERIFY_NO_CREDITS", result.stopReason)
        assertNull(result.outcome?.skippedReasons?.get(BatchOutcomeReasonCodes.EMAIL_VERIFICATION_REJECTED))
        // 故障目标之后的目标不得继续验证或发送。
        val targets = ArgumentCaptor.forClass(EmailVerificationTarget::class.java)
        Mockito.verify(batchEmailVerificationService, Mockito.times(2))
            .verify(anyValue(verificationContext), captureValue(targets, verificationTarget()), anyAllowedStates())
        assertEquals(listOf("a@b.com", "b@b.com"), targets.allValues.map { it.email })
        Mockito.verify(mailDeliveryService, Mockito.times(1)).send(anyValue(account), anyValue(ComposedMail("", "", "")))
    }

    @Test
    fun `a failing verification on the first target reports FAILED (I-4)`() {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(expert("0001", "a@b.com")))
        stubVerificationDecision(failing = "a@b.com")

        val result = service.run(introSnapshotWithVerification(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.sent)
        assertEquals(1, result.remaining)
        assertEquals("FAILED", result.finalStatus)
        assertEquals("FAILED", result.taskFinalStatus)
        Mockito.verify(mailDeliveryService, Mockito.never()).send(anyValue(account), anyValue(ComposedMail("", "", "")))
    }
    @Test
    fun `recipient verification failures defer and continue to fill successful quota`() {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(
            expert("0001", "a@b.com"), expert("0002", "b@b.com"), expert("0003", "c@b.com")
        ))
        Mockito.`when`(introductionMailComposer.compose(anyValue("chen"), anyValue(expert("", "")), Mockito.any(), anyBooleanValue()))
            .thenAnswer { invocation ->
                val profile = invocation.getArgument<ExpertProfile>(1)
                ComposedMail(profile.email.orEmpty(), "Subject", "Body")
            }
        stubVerificationDecision(failing = "a@b.com", failureCode = BatchEmailVerificationErrorCodes.INCOMPLETE)

        val result = service.run(introSnapshotWithVerification(roundSize = 2), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals("SUCCESS", result.taskFinalStatus)
        assertEquals(2, result.sent)
        assertEquals(0, result.failed)
        assertEquals(1, result.skipped)
        assertEquals(0, result.remaining)
        assertEquals("COMPLETED", result.finalStatus)
        assertNull(result.stopReason)
        assertEquals(1, result.outcome?.skippedReasons?.get(BatchOutcomeReasonCodes.EMAIL_VERIFICATION_DEFERRED)?.count)
        Mockito.verify(batchEmailVerificationService).recordSend(
            555L, "SKIPPED", BatchOutcomeReasonCodes.EMAIL_VERIFICATION_DEFERRED
        )
        val targets = ArgumentCaptor.forClass(EmailVerificationTarget::class.java)
        Mockito.verify(batchEmailVerificationService, Mockito.times(3))
            .verify(anyValue(verificationContext), captureValue(targets, verificationTarget()), anyAllowedStates())
        assertEquals(listOf("a@b.com", "b@b.com", "c@b.com"), targets.allValues.map { it.email })
        val sentMails = ArgumentCaptor.forClass(ComposedMail::class.java)
        Mockito.verify(mailDeliveryService, Mockito.times(2))
            .send(anyValue(account), captureValue(sentMails, ComposedMail("", "", "")))
        assertEquals(listOf("b@b.com", "c@b.com"), sentMails.allValues.map { it.to })
        val savedContacts = ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(expertContactRepository, Mockito.times(2)).save(savedContacts.capture())
        assertEquals(setOf("0002", "0003"), savedContacts.allValues.map { it.orcidId }.toSet())
        Mockito.verify(expertIndexWriterService, Mockito.never()).syncOperatorStatus(Mockito.anyString(), Mockito.anyString())
    }

    @Test
    fun `all deferred verification targets complete without sending or stopping`() {
        stubIntroSendPipeline(account("chen"), listOf(expert("0001", "a@b.com")))
        stubVerificationDecision(failing = "a@b.com", failureCode = BatchEmailVerificationErrorCodes.TIMEOUT)

        val result = service.run(introSnapshotWithVerification(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.sent)
        assertEquals("SUCCESS", result.taskFinalStatus)
        assertEquals(0, result.failed)
        assertEquals(1, result.skipped)
        assertEquals(0, result.remaining)
        assertEquals("COMPLETED", result.finalStatus)
        assertNull(result.stopReason)
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `cancellation after a deferred verification stops before the next candidate`() {
        stubIntroSendPipeline(account("chen"), listOf(expert("0001", "a@b.com"), expert("0002", "b@b.com")))
        stubVerificationDecision(failing = "a@b.com", failureCode = BatchEmailVerificationErrorCodes.BAD_RESPONSE)
        var cancelled = false
        Mockito.`when`(progressStore.isCancelled(eqValue("MANUAL_INITIAL_OUTREACH"), eqValue(12345L)))
            .thenAnswer { cancelled }
        Mockito.doAnswer {
            cancelled = true
            null
        }.`when`(batchEmailVerificationService).recordSend(
            555L, "SKIPPED", BatchOutcomeReasonCodes.EMAIL_VERIFICATION_DEFERRED
        )

        val result = service.run(introSnapshotWithVerification(roundSize = 2), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertTrue(result.wasCancelled)
        assertEquals("CANCELLED", result.finalStatus)
        assertEquals(2, result.skipped)
        assertEquals(0, result.remaining)
        assertEquals(1, result.outcome?.skippedReasons?.get(BatchOutcomeReasonCodes.EMAIL_VERIFICATION_DEFERRED)?.count)
        assertEquals(1, result.outcome?.skippedReasons?.get(BatchOutcomeReasonCodes.CANCELLED)?.count)
        Mockito.verify(batchEmailVerificationService, Mockito.times(1))
            .verify(anyValue(verificationContext), anyValue(verificationTarget()), anyAllowedStates())
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `deferred verification audit failure stops before the next candidate`() {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(expert("0001", "a@b.com"), expert("0002", "b@b.com")))
        stubVerificationDecision(failing = "a@b.com", failureCode = BatchEmailVerificationErrorCodes.BAD_RESPONSE)
        Mockito.doThrow(EmailVerificationAuditException("audit unavailable"))
            .`when`(batchEmailVerificationService).recordSend(
                555L, "SKIPPED", BatchOutcomeReasonCodes.EMAIL_VERIFICATION_DEFERRED
            )

        val result = service.run(introSnapshotWithVerification(roundSize = 2), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.sent)
        assertEquals(0, result.skipped)
        assertEquals(2, result.remaining)
        assertEquals("FAILED", result.finalStatus)
        assertEquals("EMAIL_VERIFY_AUDIT_FAILED", result.stopReason)
        Mockito.verify(batchEmailVerificationService, Mockito.times(1))
            .verify(anyValue(verificationContext), anyValue(verificationTarget()), anyAllowedStates())
        Mockito.verify(mailDeliveryService, Mockito.never()).send(anyValue(account), anyValue(ComposedMail("", "", "")))
    }

    @Test
    fun `verification failure helpers classify only the approved codes`() {
        listOf(
            BatchEmailVerificationErrorCodes.INCOMPLETE,
            BatchEmailVerificationErrorCodes.TIMEOUT,
            BatchEmailVerificationErrorCodes.BAD_RESPONSE
        ).forEach {
            assertTrue(BatchEmailVerificationErrorCodes.isRecipientFailure(it), it)
            assertFalse(BatchEmailVerificationErrorCodes.isGlobalFailure(it), it)
        }
        listOf(
            BatchEmailVerificationErrorCodes.AUTH_ERROR,
            BatchEmailVerificationErrorCodes.NO_CREDITS,
            BatchEmailVerificationErrorCodes.RATE_LIMITED,
            BatchEmailVerificationErrorCodes.SERVICE_ERROR,
            BatchEmailVerificationErrorCodes.AUDIT_FAILED
        ).forEach {
            assertFalse(BatchEmailVerificationErrorCodes.isRecipientFailure(it), it)
            assertTrue(BatchEmailVerificationErrorCodes.isGlobalFailure(it), it)
        }
        assertFalse(BatchEmailVerificationErrorCodes.isRecipientFailure("EMAIL_VERIFY_UNKNOWN"))
        assertFalse(BatchEmailVerificationErrorCodes.isGlobalFailure("EMAIL_VERIFY_UNKNOWN"))
    }
    @Test
    fun `unknown verification failure code stops the execution and is preserved`() {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(expert("0001", "a@b.com"), expert("0002", "b@b.com")))
        stubVerificationDecision(failing = "a@b.com", failureCode = "EMAIL_VERIFY_FUTURE_CODE")

        val result = service.run(introSnapshotWithVerification(roundSize = 2), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.sent)
        assertEquals(2, result.remaining)
        assertEquals("FAILED", result.finalStatus)
        assertEquals("EMAIL_VERIFY_FUTURE_CODE", result.stopReason)
        Mockito.verify(batchEmailVerificationService, Mockito.times(1))
            .verify(anyValue(verificationContext), anyValue(verificationTarget()), anyAllowedStates())
        Mockito.verify(mailDeliveryService, Mockito.never()).send(anyValue(account), anyValue(ComposedMail("", "", "")))
    }

    @Test
    fun `a passed address reserves sending before smtp and records the sent result after (I-6)`() {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(expert("0001", "a@b.com")))
        stubVerificationDecision(passedRowId = 555L)

        val result = service.run(introSnapshotWithVerification(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(1, result.sent)
        val inOrder = Mockito.inOrder(batchEmailVerificationService, mailDeliveryService)
        inOrder.verify(batchEmailVerificationService).markSending(555L)
        inOrder.verify(mailDeliveryService).send(anyValue(account), anyValue(ComposedMail("", "", "")))
        inOrder.verify(batchEmailVerificationService).recordSend(555L, "SENT", null)
    }

    @Test
    fun `a passed address that never reaches smtp keeps NOT_SENT with a concrete reason (I-6)`() {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(expert("0001", "a@b.com")))
        stubVerificationDecision(passedRowId = 555L)
        // 已存在 SENT 介绍邮件 → 去重分支，不进 SMTP。
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(999L)).thenReturn(listOf(
            MailRecord(
                expertContactId = 999L, direction = "OUTBOUND", mailType = "INTRODUCTION", sendStatus = "SENT",
                messageId = "msg", inReplyTo = null, subject = "s", body = "b", matchedQaRuleId = null,
                receivedAt = null, sentAt = LocalDateTime.now()
            )
        ))

        val result = service.run(introSnapshotWithVerification(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(0, result.sent)
        assertEquals(1, result.skipped)
        Mockito.verify(batchEmailVerificationService).recordSend(555L, "NOT_SENT", BatchOutcomeReasonCodes.DEDUP)
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `an smtp failure records FAILED on the verification row (I-6)`() {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(expert("0001", "a@b.com")))
        stubVerificationDecision(passedRowId = 555L)
        Mockito.`when`(mailDeliveryService.send(anyValue(account), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail(messageId = "msg", status = "FAILED", errorCategory = SmtpErrorCategory.PERMANENT))

        val result = service.run(introSnapshotWithVerification(), 12345L, ExecutionMode.MANUAL, oneRoundOnly = false)

        assertEquals(1, result.failed)
        Mockito.verify(batchEmailVerificationService)
            .recordSend(555L, "FAILED", BatchOutcomeReasonCodes.SEND_EXCEPTION)
    }

    @Test
    fun `a send state conflict stops the run without smtp (I-6)`() {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(expert("0001", "a@b.com"), expert("0002", "b@b.com")))
        stubVerificationDecision(passedRowId = 555L)
        Mockito.`when`(batchEmailVerificationService.markSending(555L)).thenReturn(false)

        val result = service.run(
            introSnapshotWithVerification(roundSize = 2),
            12345L,
            ExecutionMode.MANUAL,
            oneRoundOnly = false
        )

        assertEquals(0, result.sent)
        assertEquals(2, result.remaining)
        assertEquals("EMAIL_VERIFY_SEND_STATE_CONFLICT", result.stopReason)
        assertEquals("FAILED", result.finalStatus)
        Mockito.verify(mailDeliveryService, Mockito.never()).send(anyValue(account), anyValue(ComposedMail("", "", "")))
    }

    @Test
    fun `an audit failure after smtp keeps the counted success and stops the run (I-6)`() {
        val account = account("chen")
        stubIntroSendPipeline(account, listOf(expert("0001", "a@b.com"), expert("0002", "b@b.com")))
        stubVerificationDecision(passedRowId = 555L)
        Mockito.doThrow(EmailVerificationAuditException("发送结果未落库（受影响 0 行）：id=555"))
            .`when`(batchEmailVerificationService).recordSend(555L, "SENT", null)

        val result = service.run(
            introSnapshotWithVerification(roundSize = 2),
            12345L,
            ExecutionMode.MANUAL,
            oneRoundOnly = false
        )

        // 已发出的信保留成功计数；审计未确认 → 停止本次执行（不再验证/发送后续目标）。
        assertEquals(1, result.sent)
        assertEquals("PARTIAL_SUCCESS", result.finalStatus)
        assertEquals("EMAIL_VERIFY_AUDIT_FAILED", result.stopReason)
        Mockito.verify(batchEmailVerificationService, Mockito.times(1))
            .verify(anyValue(verificationContext), anyValue(verificationTarget()), anyAllowedStates())
        Mockito.verify(mailDeliveryService, Mockito.times(1)).send(anyValue(account), anyValue(ComposedMail("", "", "")))
    }

    @Test
    fun `rejected addresses do not consume the successful send quota`() {
        val account = account("chen")
        val experts = listOf(
            expert("0001", "a@b.com"), expert("0002", "b@b.com"), expert("0003", "c@b.com"),
            expert("0004", "d@b.com"), expert("0005", "e@b.com"), expert("0006", "f@b.com")
        )
        stubIntroSendPipeline(account, experts)
        stubIntroChunkedExperts(experts, pageSize = 4)
        // I-2：共享 fixture 的 compose 固定返回 a@b.com —— 多邮箱用例必须按专家返回其真实收件地址，
        // 否则 SMTP 前的「收件地址 = 已验证地址」断言会（正确地）终止本次执行。
        Mockito.`when`(introductionMailComposer.compose(anyValue("chen"), anyValue(expert("", "")), Mockito.any(), anyBooleanValue()))
            .thenAnswer { invocation ->
                val profile = invocation.getArgument<ExpertProfile>(1)
                ComposedMail(profile.email.orEmpty(), "Subject", "Body")
            }
        stubVerificationDecision(rejected = setOf("b@b.com", "c@b.com", "d@b.com"))

        val result = service.run(
            introSnapshotWithVerification(roundSize = 2),
            12345L,
            ExecutionMode.MANUAL,
            oneRoundOnly = false
        )

        assertEquals(2, result.sent)
        assertEquals(3, result.skipped)
        assertEquals(1, result.remaining)
        // 跳过 3 个仍补足 2 封成功；达到成功上限后不验证第 6 个目标。
        Mockito.verify(batchEmailVerificationService, Mockito.times(5))
            .verify(anyValue(verificationContext), anyValue(verificationTarget()), anyAllowedStates())
        Mockito.verify(mailDeliveryService, Mockito.times(2)).send(anyValue(account), anyValue(ComposedMail("", "", "")))
    }

    @Test
    fun `twelve verification skips still fill twenty successful sends`() {
        val acc = account("chen")
        val experts = (1..33).map { expert("Q$it", "q$it@test.com") }
        stubIntroSendPipeline(acc, experts)
        Mockito.`when`(introductionMailComposer.compose(anyValue("chen"), anyValue(expert("", "")), Mockito.any(), anyBooleanValue()))
            .thenAnswer { invocation ->
                ComposedMail(invocation.getArgument<ExpertProfile>(1).email.orEmpty(), "Subject", "Body")
            }
        stubVerificationDecision(rejected = experts.take(12).map { it.email!! }.toSet())

        val result = service.run(introSnapshotWithVerification(roundSize = 20), 12345L, ExecutionMode.MANUAL, true)

        assertEquals(20, result.sent)
        assertEquals(12, result.skipped)
        assertEquals(1, result.remaining)
        assertEquals("ONE_ROUND_DONE", result.stopReason)
        Mockito.verify(batchEmailVerificationService, Mockito.times(32))
            .verify(anyValue(verificationContext), anyValue(verificationTarget()), anyAllowedStates())
        Mockito.verify(mailDeliveryService, Mockito.times(20)).send(anyValue(acc), anyValue(ComposedMail("", "", "")))
    }

    @Test
    fun `scheduled round fills twenty sends when skipped experts remain in shrinking ES pages`() {
        val acc = account("chen")
        val experts = (1..100).map { expert("Q$it", "q$it@test.com") }
        val sentEmails = mutableSetOf<String>()
        stubIntroSendPipeline(acc, experts)
        // Real ES applies from/size BEFORE the service's in-memory deduplication.
        Mockito.`when`(expertSearchService.searchExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList()), anyInt(), anyInt()
        )).thenAnswer { call ->
            experts.filterNot { it.email in sentEmails }
                .drop(call.getArgument<Int>(2)).take(call.getArgument<Int>(3))
        }
        Mockito.`when`(expertSearchService.countExperts(
            eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList())
        )).thenAnswer { (experts.size - sentEmails.size).toLong() }
        Mockito.`when`(introductionMailComposer.compose(anyValue("chen"), anyValue(expert("", "")), Mockito.any(), anyBooleanValue()))
            .thenAnswer { ComposedMail(it.getArgument<ExpertProfile>(1).email.orEmpty(), "Subject", "Body") }
        Mockito.`when`(mailDeliveryService.send(anyValue(acc), anyValue(ComposedMail("", "", ""))))
            .thenAnswer {
                assertTrue(sentEmails.add(it.getArgument<ComposedMail>(1).to), "Never resend a recipient")
                DeliveredMail("sent-${sentEmails.size}", "SENT")
            }
        stubVerificationDecision(rejected = experts.take(40).filterIndexed { i, _ -> i != 0 && i != 37 }
            .map { it.email!! }.toSet())

        val result = service.run(introSnapshotWithVerification(roundSize = 20), 12345L, ExecutionMode.AUTO, false)

        assertEquals(20, result.sent)
        assertEquals(38, result.skipped)
        assertEquals(42, result.remaining)
        assertEquals("ROUNDS_PER_RUN_REACHED", result.stopReason)
        Mockito.verify(batchEmailVerificationService, Mockito.times(58))
            .verify(anyValue(verificationContext), anyValue(verificationTarget()), anyAllowedStates())
    }

    @Test
    fun `all rejected raw ES pages are scanned to exhaustion`() {
        val experts = (1..85).map { expert("Q$it", "q$it@test.com") }
        stubIntroSendPipeline(account("chen"), experts)
        stubPagedExperts(experts)
        stubVerificationDecision(rejected = experts.map { it.email!! }.toSet())

        val result = service.run(introSnapshotWithVerification(roundSize = 20), 12345L, ExecutionMode.AUTO, false)

        assertEquals(0, result.sent)
        assertEquals(85, result.skipped)
        assertEquals(0, result.remaining)
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `all rejected targets exhaust across pages without sending or looping forever`() {
        val experts = (1..9).map { expert("Q$it", "q$it@test.com") }
        stubIntroSendPipeline(account("chen"), experts)
        stubIntroChunkedExperts(experts, pageSize = 4)
        stubVerificationDecision(rejected = experts.map { it.email!! }.toSet())

        val result = service.run(introSnapshotWithVerification(roundSize = 2), 12345L, ExecutionMode.AUTO, false)

        assertEquals(0, result.sent)
        assertEquals(9, result.skipped)
        assertEquals(0, result.remaining)
        assertEquals("COMPLETED", result.finalStatus)
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    @Test
    fun `suppression personalization and smtp failure do not consume either round quota`() {
        val acc = account("chen")
        val experts = (1..8).map { expert("Q$it", "q$it@test.com") }
        stubIntroSendPipeline(acc, experts)
        stubIntroChunkedExperts(experts, pageSize = 4)
        Mockito.`when`(emailSuppressionService.isSuppressed("q1@test.com")).thenReturn(true)
        Mockito.`when`(introductionMailComposer.compose(anyValue("chen"), anyValue(expert("", "")), Mockito.any(), anyBooleanValue()))
            .thenAnswer { invocation ->
                val profile = invocation.getArgument<ExpertProfile>(1)
                if (profile.email == "q2@test.com") {
                    throw com.weibo.talentintroduction.mail.service.PersonalizationGateException(listOf("recentWorkTitle"))
                }
                ComposedMail(profile.email.orEmpty(), "Subject", "Body")
            }
        Mockito.`when`(mailDeliveryService.send(anyValue(acc), anyValue(ComposedMail("", "", ""))))
            .thenReturn(DeliveredMail("failed", "FAILED", errorCategory = SmtpErrorCategory.PERMANENT))
            .thenReturn(DeliveredMail("sent", "SENT"))

        val result = service.run(introSnapshot(roundSize = 2, roundsPerRun = 2), 12345L, ExecutionMode.AUTO, false)

        assertEquals(4, result.sent)
        assertEquals(2, result.skipped)
        assertEquals(1, result.outcome!!.failure)
        assertEquals(1, result.remaining)
        assertEquals("ROUNDS_PER_RUN_REACHED", result.stopReason)
        Mockito.verify(mailDeliveryService, Mockito.times(5)).send(anyValue(acc), anyValue(ComposedMail("", "", "")))
    }

    @Test
    fun `cancel during skipped targets stops before scanning the next recipient`() {
        val experts = (1..3).map { expert("Q$it", "q$it@test.com") }
        stubIntroSendPipeline(account("chen"), experts)
        var cancelRequested = false
        Mockito.`when`(emailSuppressionService.isSuppressed(Mockito.anyString())).thenAnswer {
            cancelRequested = true
            true
        }
        Mockito.`when`(progressStore.isCancelled(eqValue("MANUAL_INITIAL_OUTREACH"), eqValue(12345L)))
            .thenAnswer { cancelRequested }

        val result = service.run(introSnapshot(roundSize = 2, roundsPerRun = 1), 12345L, ExecutionMode.MANUAL, true)

        assertTrue(result.wasCancelled)
        assertEquals("CANCELLED", result.finalStatus)
        assertEquals(3, result.skipped)
        assertEquals(0, result.remaining)
        assertEquals(1, result.outcome!!.skippedReasons[BatchOutcomeReasonCodes.SUPPRESSED]?.count)
        assertEquals(2, result.outcome!!.skippedReasons[BatchOutcomeReasonCodes.CANCELLED]?.count)
        Mockito.verify(emailSuppressionService, Mockito.times(1)).isSuppressed(Mockito.anyString())
        Mockito.verifyNoInteractions(mailDeliveryService)
    }

    // ──── Helpers ────

    /** I-1：开启发送前验证的介绍邮件快照（关闭路径由 runScheduledSnapshot 覆盖）。 */
    private fun introSnapshotWithVerification(roundSize: Int = 10, roundsPerRun: Int = 1) =
        BatchExecutionSnapshot(
            mailType = "INTRODUCTION",
            roundSize = roundSize,
            roundsPerRun = roundsPerRun,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            // I4-2: fixture 默认分类为 PRODUCTION_RND —— 快照必须携带该类型，否则 fail-closed 一律不发。
            expertTypes = listOf("PRODUCTION_RND", "ACADEMIC_RND", "HYBRID_RND"),
            emailVerificationEnabled = true
        )

    /**
     * I-1/I-2/I-3：按邮箱给出验证结论 —— 默认全部 PASS；[rejected] 内为 undeliverable 拒绝，
     * [policySkipped] 内为「结果明确但不在本次放行集合」的策略跳过，[failing] 为服务故障。
     * 明细 id 固定 [passedRowId]，便于断言审计调用顺序。
     */
    private fun stubVerificationDecision(
        rejected: Set<String> = emptySet(),
        failing: String? = null,
        failureCode: String = BatchEmailVerificationErrorCodes.NO_CREDITS,
        passedRowId: Long = 555L,
        policySkipped: Set<String> = emptySet()
    ) {
        Mockito.`when`(
            batchEmailVerificationService.beginExecution(Mockito.anyLong(), anyValue<() -> Boolean>({ false }))
        ).thenReturn(verificationContext)
        Mockito.`when`(
            batchEmailVerificationService.verify(
                anyValue(verificationContext), anyValue(verificationTarget()), anyAllowedStates()
            )
        ).thenAnswer { invocation ->
            val target = invocation.getArgument<EmailVerificationTarget>(1)
            when (target.email) {
                failing -> VerificationResult.ServiceFailure(passedRowId, failureCode)
                in rejected -> VerificationResult.Rejected(passedRowId, BatchEmailVerificationTagStatus.APPLIED)
                in policySkipped -> VerificationResult.PolicySkipped(passedRowId)
                else -> VerificationResult.Passed(passedRowId, normalizeVerificationEmail(target.email))
            }
        }
        Mockito.`when`(batchEmailVerificationService.markSending(Mockito.anyLong())).thenReturn(true)
    }

    /** 第三参（本次放行集合）的 any 匹配；null 是合法值（旧请求三态全放行）。 */
    private fun anyAllowedStates(): List<String>? = Mockito.any()

    /** any 匹配用的非空目标占位（Mockito.any() 返回 null，不能传给 Kotlin 非空参数）。 */
    private fun verificationTarget() = EmailVerificationTarget(null, "", null, "")

    private fun expert(orcidId: String, email: String): ExpertProfile =
        ExpertProfile(
            orcidId = orcidId,
            email = email,
            givenNames = "Given",
            familyNames = "Family",
            country = "China",
            keyword = "keyword",
            employment = "University",
            // I3-1: 默认 fixture 为可发类型；缺分类/不可发场景用 expert(...).copy(expertClassification = ...) 显式构造。
            expertClassification = sendableClassification()
        )

    private fun identityProof(email: String, orcidId: String = "0000-0000-0000-0001"): IdentityVerification =
        IdentityVerification(
            status = "VERIFIED",
            version = DiscoveryIdentity.VERSION,
            email = email,
            givenNames = "Given",
            familyNames = "Family",
            source = "JATS_SHA256",
            evidenceHash = "a".repeat(64),
            orcid = orcidId,
            openAlexAuthorId = "A-$orcidId"
        )

    /**
     * 03：一份「主键 / externalIds / 身份凭证逐字一致 + 有机构 + 有证据 token」的新发现档案。
     * token 由 02 的唯一签发函数产出 —— 发送门禁读的必须是同一份验签口径。
     */
    private fun signedDiscoveryExpert(
        orcidId: String,
        email: String,
        country: String? = "China"
    ): ExpertProfile {
        val externalIds = """{"pmcId":"PMC-$orcidId","doi":"10.0/$orcidId","orcid":"$orcidId","openAlexAuthorId":"A-$orcidId"}"""
        val base = expert(orcidId, email).copy(
            country = country,
            institution = "Institute $orcidId",
            externalIds = externalIds,
            identityVerification = identityProof(email, orcidId)
        )
        val token = DiscoveryIdentity.institutionEvidence(base, DiscoveryIdentity.EVIDENCE_SOURCE_JATS)
            ?: error("fixture 必须能签发机构证据 token: $orcidId")
        return base.copy(institutionEvidence = token, filterResult = "PASSED")
    }

    /** I-1/I-3: stub 持久准入结论（docId → decision）。未列出的 docId 视为未初始化。 */
    private fun stubAdmissions(vararg decisions: Pair<String, String>) {
        val byDoc = decisions.toMap()
        Mockito.`when`(discoveryReviewService.resolveAdmissionBatch(Mockito.anyList())).thenAnswer { invocation ->
            val keys = invocation.getArgument<List<com.weibo.talentintroduction.discovery.service.DiscoveryReviewAdmissionKey>>(0)
            keys.associate { key -> key.docId to resolvedAdmission(key.docId, byDoc[key.docId]) }
        }
    }

    private fun resolvedAdmission(
        docId: String,
        decision: String?
    ): com.weibo.talentintroduction.discovery.service.DiscoveryReviewResolvedAdmission {
        if (decision == null) {
            return com.weibo.talentintroduction.discovery.service.DiscoveryReviewResolvedAdmission(
                docId, null, false, false, false, false, 0L, null
            )
        }
        val admitted = decision in setOf("AUTO_PASSED", "MANUAL_APPROVED", "LEGACY_APPROVED")
        val manual = decision in setOf("MANUAL_APPROVED", "LEGACY_APPROVED")
        return com.weibo.talentintroduction.discovery.service.DiscoveryReviewResolvedAdmission(
            docId, decision, admitted, manual, true, false, 1L, null
        )
    }

    /** 预估路径的空 ES 面：settle 为 0 时执行路径立即结束，便于断言重试/预估口径。 */
    private fun stubEmptyRetryCandidates() {
        Mockito.`when`(campaignRepository.findByCampaignCode("MANUAL_OUTREACH")).thenReturn(
            Campaign(id = 10L, campaignCode = "MANUAL_OUTREACH", campaignName = "Manual Outreach", description = null, senderAccountId = 1L)
        )
        Mockito.`when`(expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(10L, "NEW"))
            .thenReturn(emptyList())
    }

    private fun sendableClassification(): ExpertClassification =
        ExpertClassification(
            type = ExpertType.PRODUCTION_RND,
            productionScore = 80,
            researchScore = 20,
            positiveEvidence = listOf("RND_PRODUCTION"),
            negativeEvidence = emptyList(),
            version = ExpertClassificationService.VERSION,
            sourceFingerprint = "fp-0001",
            classifiedAt = LocalDateTime.of(2026, 8, 1, 12, 0)
        )

    private fun classification(type: ExpertType): ExpertClassification =
        ExpertClassification(
            type = type,
            productionScore = if (type in setOf(ExpertType.PRODUCTION_RND, ExpertType.ACADEMIC_RND, ExpertType.HYBRID_RND)) 60 else 0,
            researchScore = if (type in setOf(ExpertType.PRODUCTION_RND, ExpertType.ACADEMIC_RND, ExpertType.HYBRID_RND)) 60 else 0,
            positiveEvidence = listOf("EVIDENCE"),
            negativeEvidence = if (type in setOf(ExpertType.PRODUCTION_RND, ExpertType.ACADEMIC_RND, ExpertType.HYBRID_RND)) emptyList() else listOf("NOT_SENDABLE"),
            version = ExpertClassificationService.VERSION,
            sourceFingerprint = "fp-$type",
            classifiedAt = LocalDateTime.of(2026, 8, 1, 12, 0)
        )

    /**
     * I-3: 预估（`countEsTargets`）走 ES scroll + 最终谓词，取页走 `searchExpertsFiltered`；
     * 这一个 helper 同时铺满两条路径，使既有用例的人数口径逐字不变。
     */
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

        Mockito.doAnswer { invocation ->
            val handler = invocation.getArgument<(List<ExpertProfile>) -> Boolean>(3)
            if (experts.isNotEmpty()) handler(experts)
            null
        }.`when`(expertSearchService).scrollExpertsFiltered(
            eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList()), eqValue(500),
            anyValue({ _: List<ExpertProfile> -> true })
        )
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
        val actual = method.invoke(service, scope, level) as List<Map<String, Any>>
        assertFalse(actual.toString().contains("identityVerification"), "batch selection must use configured filters only")
        return actual
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

    private fun anyBooleanValue(): Boolean = Mockito.anyBoolean() ?: false
}
