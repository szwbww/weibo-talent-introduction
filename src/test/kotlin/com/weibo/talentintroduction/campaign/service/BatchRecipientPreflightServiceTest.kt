package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.RecipientFilterKeys
import com.weibo.talentintroduction.campaign.domain.RecipientScope
import com.weibo.talentintroduction.campaign.repository.BatchRecipientReadRepository
import com.weibo.talentintroduction.campaign.repository.IntroductionSendFact
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewDecision
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewAdmissionKey
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewResolvedAdmission
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewService
import com.weibo.talentintroduction.expert.domain.ExpertClassification
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.ExpertType
import com.weibo.talentintroduction.mail.service.BatchTemplateEvaluation
import com.weibo.talentintroduction.mail.service.IntroductionMailComposer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime

/**
 * 02（I-1～I-6 / MAIN M-3 / G-2）：只读统一预检查的真值表。
 *
 * 验证完整母集计数不重复（`baseline = excludedUnique + eligibleBeforeLiveVerification`、
 * `awaitingVerification` 是通过的子集）、原因命中可重叠但绝不冒充总排除人数、每原因样本 ≤20 且标
 * `truncated`、两开关/放行集合的邮箱相位矩阵、保护原因独立，以及全程零业务写入。
 */
class BatchRecipientPreflightServiceTest {

    private val review = Mockito.mock(DiscoveryReviewService::class.java)
    private val selectionService = BatchRecipientSelectionService(review)
    private val emailVerificationService = Mockito.mock(BatchEmailVerificationService::class.java)
    private val readRepository = Mockito.mock(BatchRecipientReadRepository::class.java)
    private val composer = Mockito.mock(IntroductionMailComposer::class.java)
    private val service = BatchRecipientPreflightService(
        selectionService, emailVerificationService, readRepository, composer
    )

    private val asOf: LocalDateTime = LocalDateTime.of(2026, 10, 14, 12, 0)
    private val templateId = 42L

    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    private fun expert(
        docId: String,
        country: String? = "Germany",
        operatorStatus: String? = "CONTACTED",
        type: ExpertType = ExpertType.PRODUCTION_RND
    ): ExpertProfile = ExpertProfile(
        orcidId = docId,
        esDocId = docId,
        email = "$docId@example.org",
        givenNames = "Given",
        familyNames = "Family",
        country = country,
        keyword = "kw",
        employment = "Employment",
        institution = "Institute",
        operatorStatus = operatorStatus,
        emailSource = "PAPER_FULLTEXT",
        expertClassification = ExpertClassification(
            type = type,
            productionScore = 80,
            researchScore = 20,
            positiveEvidence = listOf("EVIDENCE"),
            negativeEvidence = emptyList(),
            version = "rnd-v2-2026",
            sourceFingerprint = "fp",
            classifiedAt = LocalDateTime.of(2026, 8, 1, 12, 0)
        )
    )

    private fun scope(
        mailType: String = BatchSendType.INTRODUCTION.name,
        tags: List<String> = emptyList(),
        regions: List<String> = emptyList(),
        operatorStatuses: List<String> = emptyList(),
        expertTypes: List<String> = listOf("PRODUCTION_RND")
    ) = RecipientScope(
        mailType = mailType,
        funnelLevels = setOf("CANDIDATE"),
        tags = tags,
        regions = regions,
        emailDomains = emptyList(),
        discipline = null,
        operatorStatuses = operatorStatuses,
        expertTypes = expertTypes
    )

    private fun stubAdmissions(vararg docIds: String) {
        Mockito.`when`(review.resolveAdmissionBatch(Mockito.anyList())).thenAnswer { invocation ->
            val keys = invocation.getArgument<List<DiscoveryReviewAdmissionKey>>(0)
            keys.associate { key ->
                val decision = if (key.docId in docIds) DiscoveryReviewDecision.MANUAL_APPROVED.name else null
                key.docId to DiscoveryReviewResolvedAdmission(
                    docId = key.docId,
                    decision = decision,
                    admitted = decision != null,
                    manual = decision != null,
                    initialized = decision != null,
                    identityChanged = false,
                    revision = if (decision == null) 0L else 1L,
                    syncErrorCode = null
                )
            }
        }
    }

    private fun stubTemplateMissing(vararg docIds: String) = Mockito.`when`(
        composer.evaluateForBatch(anyValue<String?>(null), anyValue(expert("x")), anyValue(templateId))
    ).thenAnswer { invocation ->
        val profile = invocation.getArgument<ExpertProfile>(1)
        val missing = if (profile.orcidId in docIds) listOf("expertFamilyName") else emptyList()
        BatchTemplateEvaluation(missing, templateId, 0)
    }

    private fun input(
        profiles: List<ExpertProfile>,
        scope: RecipientScope = scope(),
        gate: Boolean = false,
        emailVerificationEnabled: Boolean = false,
        allowedStates: List<String>? = null,
        excludeUndeliverable: Boolean = false,
        partial: Boolean = false
    ) = BatchRecipientPreflightInput(
        scope = scope,
        profiles = profiles,
        gateFilterEnabled = gate,
        templateId = if (gate) templateId else null,
        emailVerificationEnabled = emailVerificationEnabled,
        emailVerificationAllowedStates = allowedStates,
        excludeVerifiedUnavailableEmails = excludeUndeliverable,
        asOf = asOf,
        partial = partial
    )

    // ── G-2：共同 fixture ─────────────────────────────────────────────────────

    @Test
    fun `GA-2 fixture yields 10 4 6 3 with six overlappable reason hits (MAIN G-2 M-3)`() {
        val all = (1..10).map { "D%02d".format(it) }
        stubAdmissions(*all.toTypedArray())
        // D1/D2：地区 + 状态同时不匹配；D3：模板缺项；D4：有效 risky 缓存（当前不允许 risky）；
        // D5～D7：有效 deliverable；D8～D10：无有效缓存。
        val profiles = listOf(
            expert("D01", country = "Japan", operatorStatus = null),
            expert("D02", country = "Japan", operatorStatus = null),
            expert("D03"), expert("D04"), expert("D05"), expert("D06"),
            expert("D07"), expert("D08"), expert("D09"), expert("D10")
        )
        stubTemplateMissing("D03")
        Mockito.`when`(
            emailVerificationService.readReusableProviderStates(anyValue(emptyList<String?>()), anyValue(asOf))
        ).thenReturn(
            mapOf(
                "d04@example.org" to "risky",
                "d05@example.org" to "deliverable",
                "d06@example.org" to "deliverable",
                "d07@example.org" to "deliverable"
            )
        )

        val result = service.preflight(
            input(
                profiles,
                scope = scope(regions = listOf("Europe"), operatorStatuses = listOf("CONTACTED")),
                gate = true,
                emailVerificationEnabled = true,
                allowedStates = listOf("deliverable", "unknown")
            )
        )

        assertEquals(10, result.baseline, "完整母集（仅层级/标签）")
        assertEquals(4, result.excludedUnique)
        assertEquals(6, result.eligibleBeforeLiveVerification)
        assertEquals(3, result.awaitingVerification, "无有效缓存的通过者待验证")
        assertEquals(10, result.excludedUnique + result.eligibleBeforeLiveVerification, "baseline 必须等于排除+通过")
        assertTrue(result.awaitingVerification <= result.eligibleBeforeLiveVerification)

        val hits = result.reasonHits.associate { it.reasonKey to it.count }
        assertEquals(2, hits[RecipientFilterKeys.REGION])
        assertEquals(2, hits[RecipientFilterKeys.OPERATOR_STATUS])
        assertEquals(1, hits[RecipientPreflightReasonKeys.TEMPLATE_MISSING])
        assertEquals(1, hits[RecipientPreflightReasonKeys.EMAIL_VERIFICATION_REJECTED])
        assertEquals(6, hits.values.sum(), "原因命中可重叠，合计 6 次而不是 4 人")

        // 准入事实独立保留：D1/D2 地区/状态不匹配也仍是 MANUAL_APPROVED 的 ADMITTED。
        val byDoc = result.decisions.associateBy { it.docId }
        assertEquals("ADMITTED", byDoc.getValue("D01").admissionState)
        assertEquals("ADMITTED", byDoc.getValue("D04").admissionState)
        assertFalse(byDoc.getValue("D01").eligible)
        assertEquals(
            listOf(RecipientFilterKeys.OPERATOR_STATUS, RecipientFilterKeys.REGION),
            byDoc.getValue("D01").conditionReasonKeys
        )
        assertEquals(RecipientVerificationPhase.CACHED_REJECTED, byDoc.getValue("D04").verificationPhase)
        assertEquals(RecipientVerificationPhase.CACHED_ALLOWED, byDoc.getValue("D05").verificationPhase)
        assertEquals(RecipientVerificationPhase.AWAITING_VERIFICATION, byDoc.getValue("D08").verificationPhase)
        assertEquals(RecipientVerificationPhase.AWAITING_VERIFICATION, result.verificationPhase)
        assertFalse(result.partial)
        assertEquals(asOf, result.asOf)

        // 小样本不截断。
        assertTrue(result.reasonHits.all { !it.truncated })
    }

    // ── I-5：样本上限与截断标记 ────────────────────────────────────────────────

    @Test
    fun `reason samples are capped at twenty with an explicit truncation marker (I-5)`() {
        val profiles = (1..25).map { expert("S%02d".format(it), country = "Japan") }
        stubAdmissions(*profiles.map { it.orcidId }.toTypedArray())

        val result = service.preflight(input(profiles, scope = scope(regions = listOf("Europe"))))

        assertEquals(25, result.baseline)
        assertEquals(25, result.excludedUnique)
        assertEquals(0, result.eligibleBeforeLiveVerification)
        val region = result.reasonHits.single { it.reasonKey == RecipientFilterKeys.REGION }
        assertEquals(25, region.count)
        assertEquals(BatchRecipientPreflightService.MAX_REASON_SAMPLES, region.samples.size)
        assertTrue(region.truncated)
    }

    // ── I-3：两开关与放行集合矩阵 ──────────────────────────────────────────────

    @Test
    fun `live verification interprets reusable results by the current allowed states (I-3)`() {
        val profiles = listOf(expert("A"), expert("B"), expert("C"), expert("D"))
        stubAdmissions("A", "B", "C", "D")
        Mockito.`when`(emailVerificationService.readReusableProviderStates(anyValue(emptyList<String?>()), anyValue(asOf)))
            .thenReturn(mapOf("a@example.org" to "risky", "b@example.org" to "undeliverable", "c@example.org" to "deliverable"))

        // 1. 不允许 risky → A/C 拒绝；B 拒绝；D 待验证。
        val withoutRisky = service.preflight(
            input(profiles, emailVerificationEnabled = true, allowedStates = listOf("deliverable", "unknown"))
        )
        val byDoc = withoutRisky.decisions.associateBy { it.docId }
        assertEquals(RecipientVerificationPhase.CACHED_REJECTED, byDoc.getValue("A").verificationPhase)
        assertEquals(RecipientVerificationPhase.CACHED_REJECTED, byDoc.getValue("B").verificationPhase)
        assertEquals(RecipientVerificationPhase.CACHED_ALLOWED, byDoc.getValue("C").verificationPhase)
        assertEquals(RecipientVerificationPhase.AWAITING_VERIFICATION, byDoc.getValue("D").verificationPhase)
        assertEquals(2, withoutRisky.eligibleBeforeLiveVerification)
        assertEquals(1, withoutRisky.awaitingVerification)

        // 2. 允许 risky → A 通过缓存；D 仍待验证。
        val withRisky = service.preflight(
            input(profiles, emailVerificationEnabled = true, allowedStates = listOf("deliverable", "risky", "unknown"))
        )
        assertEquals(RecipientVerificationPhase.CACHED_ALLOWED, withRisky.decisions.associateBy { it.docId }.getValue("A").verificationPhase)
        assertEquals(3, withRisky.eligibleBeforeLiveVerification)
        assertEquals(1, withRisky.awaitingVerification)
        // 只读：预检查绝不调用真实 verify()。
        Mockito.verify(emailVerificationService, Mockito.never()).verify(
            anyValue(verificationContext), anyValue(EmailVerificationTarget(null, "", null, "")), anyValue<List<String>?>(null)
        )
    }

    @Test
    fun `historical undeliverable filtering is independent of live verification (I-3)`() {
        val profiles = listOf(expert("A"), expert("B"))
        stubAdmissions("A", "B")
        Mockito.`when`(
            emailVerificationService.findKnownUndeliverableEmails(anyValue(emptyList<String?>()), anyValue(asOf))
        ).thenReturn(setOf("a@example.org"))

        val result = service.preflight(input(profiles, excludeUndeliverable = true))

        val byDoc = result.decisions.associateBy { it.docId }
        assertEquals(RecipientVerificationPhase.CACHED_REJECTED, byDoc.getValue("A").verificationPhase)
        assertEquals(RecipientVerificationPhase.DISABLED, byDoc.getValue("B").verificationPhase)
        assertEquals(1, result.excludedUnique)
        // 关闭实时验证时绝不调用可复用历史解析（那是实时验证的相位判据）。
        Mockito.verify(emailVerificationService, Mockito.never())
            .readReusableProviderStates(anyValue<Collection<String?>>(emptyList()), anyValue(asOf))
    }

    @Test
    fun `both switches off never touch email history (I-3 I-4)`() {
        val profiles = listOf(expert("A"))
        stubAdmissions("A")

        val result = service.preflight(input(profiles))

        assertEquals(1, result.eligibleBeforeLiveVerification)
        assertEquals(RecipientVerificationPhase.DISABLED, result.decisions.single().verificationPhase)
        Mockito.verifyNoInteractions(emailVerificationService)
    }

    // ── I-1/I-3：保护原因独立 ──────────────────────────────────────────────────

    @Test
    fun `protection facts are independent reasons and never loosen admission (I-1 I-3)`() {
        val profiles = listOf(expert("A"), expert("B"), expert("C"))
        stubAdmissions("A", "B", "C")
        Mockito.`when`(readRepository.findBoundOrcids(Mockito.anyCollection())).thenReturn(setOf("B"))
        Mockito.`when`(readRepository.findSuppressedEmails(Mockito.anyCollection())).thenReturn(setOf("c@example.org"))

        val result = service.preflight(input(profiles))

        val byDoc = result.decisions.associateBy { it.docId }
        assertTrue(byDoc.getValue("A").eligible)
        assertEquals(listOf(RecipientProtectionReasonKeys.BOUND_SENDER), byDoc.getValue("B").protectionReasonKeys)
        assertEquals(listOf(RecipientProtectionReasonKeys.SUPPRESSED), byDoc.getValue("C").protectionReasonKeys)
        assertEquals(2, result.excludedUnique)
        assertEquals(1, result.eligibleBeforeLiveVerification)
    }

    @Test
    fun `sent permanent failure and email invalid facts are explained (I-1 I-3)`() {
        stubAdmissions("A", "B", "C")
        Mockito.`when`(readRepository.findIntroductionSendFacts(Mockito.anyCollection())).thenReturn(
            mapOf(
                "A" to IntroductionSendFact(sent = true, permanentFailure = false),
                "B" to IntroductionSendFact(sent = false, permanentFailure = true)
            )
        )
        Mockito.`when`(readRepository.findEmailInvalidOrcids(Mockito.anyCollection())).thenReturn(setOf("C"))

        val result = service.preflight(input(listOf(expert("A"), expert("B"), expert("C"))))

        val byDoc = result.decisions.associateBy { it.docId }
        assertEquals(listOf(RecipientProtectionReasonKeys.ALREADY_SENT), byDoc.getValue("A").protectionReasonKeys)
        assertEquals(listOf(RecipientProtectionReasonKeys.PERMANENT_FAILURE), byDoc.getValue("B").protectionReasonKeys)
        assertEquals(listOf(RecipientProtectionReasonKeys.EMAIL_INVALID), byDoc.getValue("C").protectionReasonKeys)
        assertEquals(0, result.eligibleBeforeLiveVerification)
    }

    // ── I-6：模板门禁只按真实专家变量 ──────────────────────────────────────────

    @Test
    fun `template gate off never evaluates the template and non-introduction ignores it (I-6)`() {
        stubAdmissions("A")
        // gate OFF：即使 composer 未 stub 也不得被调用。
        val off = service.preflight(input(listOf(expert("A"))))
        assertTrue(off.decisions.single().eligible)
        assertTrue(off.decisions.single().templateMissingKeys.isEmpty())
        Mockito.verifyNoInteractions(composer)

        // MATERIAL_REMINDER 门禁不适用（调用方把 gateFilterEnabled 置 false）。
        val material = service.preflight(
            input(
                listOf(expert("A")),
                scope = scope(mailType = BatchSendType.MATERIAL_REMINDER.name, expertTypes = emptyList()),
                gate = false
            )
        )
        assertEquals(1, material.eligibleBeforeLiveVerification)
        Mockito.verifyNoInteractions(composer)
    }

    @Test
    fun `template gate on excludes only experts missing real variables (I-6)`() {
        stubAdmissions("A", "B")
        stubTemplateMissing("B")

        val result = service.preflight(input(listOf(expert("A"), expert("B")), gate = true))

        val byDoc = result.decisions.associateBy { it.docId }
        assertTrue(byDoc.getValue("A").eligible)
        assertEquals(listOf("expertFamilyName"), byDoc.getValue("B").templateMissingKeys)
        assertEquals(listOf(RecipientPreflightReasonKeys.TEMPLATE_MISSING), byDoc.getValue("B").reasonKeys)
        assertEquals(1, result.excludedUnique)
    }

    // ── I-1：去重与 partial ───────────────────────────────────────────────────

    @Test
    fun `duplicate docIds count once and partial is surfaced verbatim (I-1 I-5)`() {
        stubAdmissions("A")
        val result = service.preflight(
            input(listOf(expert("A"), expert("A"), expert("A")), partial = true)
        )

        assertEquals(1, result.baseline)
        assertTrue(result.partial)
        assertEquals(1, result.eligibleBeforeLiveVerification)
    }

    @Test
    fun `empty母集 yields zero counts without touching verification (I-3 I-5)`() {
        val result = service.preflight(input(emptyList()))

        assertEquals(0, result.baseline)
        assertEquals(0, result.excludedUnique)
        assertEquals(0, result.eligibleBeforeLiveVerification)
        assertEquals(0, result.awaitingVerification)
        assertTrue(result.reasonHits.isEmpty())
        assertTrue(result.decisions.isEmpty())
        Mockito.verifyNoInteractions(emailVerificationService)
    }

    private val verificationContext: ExecutionVerificationContext = BatchEmailVerificationService(
        Mockito.mock(com.weibo.talentintroduction.expert.service.ExpertSearchService::class.java),
        Mockito.mock(com.weibo.talentintroduction.expert.service.ExpertIndexWriterService::class.java),
        Mockito.mock(com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationRepository::class.java),
        Mockito.mock(EmailableVerifyClient::class.java),
        "live_secret"
    ).beginExecution(1L) { false }
}
