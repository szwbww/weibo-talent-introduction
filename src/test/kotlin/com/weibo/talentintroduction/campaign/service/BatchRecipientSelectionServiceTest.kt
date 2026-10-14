package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.RecipientFilterKeys
import com.weibo.talentintroduction.campaign.domain.RecipientScope
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewDecision
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewAdmissionKey
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewResolvedAdmission
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewService
import com.weibo.talentintroduction.expert.domain.ExpertClassification
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.ExpertType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * 05（I-1/I-3）：统一 selector 真值表。同身份批准后不再二次学术门禁；
 * 未初始化/身份变化/人工拒绝是明确准入状态；多原因重复计命中、总排除按人去重。
 */
class BatchRecipientSelectionServiceTest {
    private val review = Mockito.mock(DiscoveryReviewService::class.java)
    private val service = BatchRecipientSelectionService(review)

    private fun scope(
        mailType: String = BatchSendType.INTRODUCTION.name,
        tags: List<String> = emptyList(),
        regions: List<String> = emptyList(),
        emailDomains: List<String> = emptyList(),
        discipline: String? = null,
        operatorStatuses: List<String> = emptyList(),
        expertTypes: List<String> = listOf("PRODUCTION_RND")
    ) = RecipientScope(
        mailType = mailType,
        funnelLevels = setOf("CANDIDATE"),
        tags = tags,
        regions = regions,
        emailDomains = emailDomains,
        discipline = discipline,
        operatorStatuses = operatorStatuses,
        expertTypes = expertTypes
    )

    private fun expert(
        docId: String,
        email: String = "$docId@example.org",
        discovery: Boolean = true,
        type: ExpertType? = ExpertType.PRODUCTION_RND,
        tags: List<String>? = null,
        operatorStatus: String? = null
    ): ExpertProfile = ExpertProfile(
        orcidId = docId,
        esDocId = docId,
        email = email,
        givenNames = "Given",
        familyNames = "Family",
        country = "China",
        keyword = "kw",
        employment = "Employment",
        tags = tags,
        operatorStatus = operatorStatus,
        emailSource = if (discovery) "PAPER_FULLTEXT" else null,
        expertClassification = type?.let {
            ExpertClassification(
                type = it,
                productionScore = 80,
                researchScore = 20,
                positiveEvidence = listOf("EVIDENCE"),
                negativeEvidence = emptyList(),
                version = "rnd-v2-2026",
                sourceFingerprint = "fp",
                classifiedAt = java.time.LocalDateTime.of(2026, 8, 1, 12, 0)
            )
        }
    )

    private fun stubAdmissions(vararg decisions: Pair<String, String>) {
        val byDoc = decisions.toMap()
        Mockito.`when`(review.resolveAdmissionBatch(Mockito.anyList())).thenAnswer { invocation ->
            val keys = invocation.getArgument<List<DiscoveryReviewAdmissionKey>>(0)
            keys.associate { key ->
                val decision = byDoc[key.docId]
                key.docId to DiscoveryReviewResolvedAdmission(
                    docId = key.docId,
                    decision = decision,
                    admitted = decision in setOf(
                        DiscoveryReviewDecision.AUTO_PASSED.name,
                        DiscoveryReviewDecision.MANUAL_APPROVED.name,
                        DiscoveryReviewDecision.LEGACY_APPROVED.name
                    ),
                    manual = decision in setOf(
                        DiscoveryReviewDecision.MANUAL_APPROVED.name,
                        DiscoveryReviewDecision.LEGACY_APPROVED.name
                    ),
                    initialized = decision != null,
                    identityChanged = false,
                    revision = if (decision == null) 0L else 1L,
                    syncErrorCode = null
                )
            }
        }
    }

    @Test
    fun `auto manual and legacy approvals are all admitted (I-1)`() {
        stubAdmissions(
            "A" to DiscoveryReviewDecision.AUTO_PASSED.name,
            "B" to DiscoveryReviewDecision.MANUAL_APPROVED.name,
            "C" to DiscoveryReviewDecision.LEGACY_APPROVED.name
        )
        val selection = service.select(scope(), listOf(expert("A"), expert("B"), expert("C")))

        assertEquals(3, selection.included.size)
        assertTrue(selection.excluded.isEmpty())
        assertTrue(selection.included.all { it.admissionState == RecipientAdmissionState.ADMITTED })
    }

    @Test
    fun `uninitialized identity changed and manual rejected are distinct excluded states (I-1)`() {
        Mockito.`when`(review.resolveAdmissionBatch(Mockito.anyList())).thenAnswer { invocation ->
            val keys = invocation.getArgument<List<DiscoveryReviewAdmissionKey>>(0)
            keys.associate { key ->
                key.docId to when (key.docId) {
                    "REJ" -> resolved(key.docId, DiscoveryReviewDecision.REJECTED.name)
                    "HOLD" -> resolved(key.docId, DiscoveryReviewDecision.HOLD.name)
                    "CHANGED" -> resolved(key.docId, DiscoveryReviewDecision.AUTO_PASSED.name, identityChanged = true)
                    else -> resolved(key.docId, null)
                }
            }
        }
        val selection = service.select(scope(), listOf(expert("UNINIT"), expert("CHANGED"), expert("REJ"), expert("HOLD")))

        assertTrue(selection.included.isEmpty())
        assertEquals(
            mapOf("UNINIT" to RecipientAdmissionState.UNINITIALIZED),
            selection.excluded.filter { it.docId == "UNINIT" }.associate { it.docId to it.admissionState }
        )
        assertEquals(RecipientAdmissionState.IDENTITY_CHANGED, selection.excluded.single { it.docId == "CHANGED" }.admissionState)
        assertEquals(RecipientAdmissionState.MANUAL_REJECTED, selection.excluded.single { it.docId == "REJ" }.admissionState)
        assertEquals(RecipientAdmissionState.MANUAL_REJECTED, selection.excluded.single { it.docId == "HOLD" }.admissionState)
    }

    @Test
    fun `explicit condition mismatches yield filter keys and repeat reason hits per person (I-2 I-3)`() {
        // 状态与标签同时不匹配：两名各命中 2 个原因 → reasonHits 各 +2；总排除按人去重 = 2。
        val selection = service.select(
            scope(tags = listOf("wanted"), operatorStatuses = listOf("CONTACTED")),
            listOf(
                expert("X", tags = listOf("other"), operatorStatus = null),
                expert("Y", tags = emptyList(), operatorStatus = "NOT_CONTACTED")
            )
        )

        assertTrue(selection.included.isEmpty())
        assertEquals(2, selection.excluded.size)
        assertEquals(2, selection.reasonHits[RecipientFilterKeys.TAGS])
        assertEquals(2, selection.reasonHits[RecipientFilterKeys.OPERATOR_STATUS])
        assertEquals(2, selection.excluded.map { it.docId }.toSet().size)
    }

    @Test
    fun `empty expertTypes still selects zero people (I-2)`() {
        val selection = service.select(scope(expertTypes = emptyList()), listOf(expert("A")))

        assertTrue(selection.included.isEmpty())
        assertEquals(listOf(RecipientFilterKeys.EXPERT_TYPE), selection.excluded.single().filterKeys)
        assertFalse(selection.reasonHits.containsKey(RecipientFilterKeys.OPERATOR_STATUS))
    }

    @Test
    fun `dedups by real docId and batches admission lookups (I-3)`() {
        val many = (1..501).map { expert("D%04d".format(it)) }
        stubAdmissions()
        val selection = service.select(scope(), many + many)

        assertTrue(selection.included.isEmpty())
        assertEquals(501, selection.excluded.size, "同一 docId 只计一次")
        // 501 个 docId → 500 + 1 两批 lookup。
        Mockito.verify(review, Mockito.times(2)).resolveAdmissionBatch(Mockito.anyList())
    }

    @Test
    fun `non-discovery profiles skip admission lookup (I-1)`() {
        val selection = service.select(scope(), listOf(expert("L1", discovery = false)))

        assertEquals(1, selection.included.size)
        assertEquals(RecipientAdmissionState.NOT_DISCOVERY, selection.included.single().admissionState)
        Mockito.verify(review, Mockito.never()).resolveAdmissionBatch(Mockito.anyList())
    }

    @Test
    fun `selector performs no writes and never re-signs approval (I-1 I-3)`() {
        stubAdmissions("A" to DiscoveryReviewDecision.AUTO_PASSED.name)
        service.select(scope(), listOf(expert("A")))

        Mockito.verify(review).resolveAdmissionBatch(Mockito.anyList())
        Mockito.verifyNoMoreInteractions(review)
    }

    @Test
    fun `admission-only scope judges admission only and ignores introduction expert type (I-3)`() {
        // 08 人物级选择层复用本 scope：非 INTRODUCTION 不判研发类型，UNCLASSIFIED 不应被类型门禁排除。
        stubAdmissions("A" to DiscoveryReviewDecision.AUTO_PASSED.name)
        val unclassified = expert("A", type = null)

        val selection = service.selectAdmission(listOf(unclassified))

        assertEquals(1, selection.included.size)
        assertEquals(RecipientAdmissionState.ADMITTED, selection.included.single().admissionState)
        assertTrue(selection.included.single().filterKeys.isEmpty())
    }

    @Test
    fun `reason keys mirror the selection口径 exactly (A3 I-3)`() {
        // 显式条件排除（filterKeys 非空）只计 filterKeys，绝不叠加准入状态原因。
        val filterExcluded = RecipientDecision(
            docId = "X", orcidId = "X", admissionState = RecipientAdmissionState.NOT_DISCOVERY,
            admissionDecision = null, syncErrorCode = null,
            filterKeys = listOf(RecipientFilterKeys.TAGS), included = false
        )
        assertEquals(listOf(RecipientFilterKeys.TAGS), BatchRecipientSelectionService.reasonKeysOf(filterExcluded))

        // 准入未通过只计一个准入状态 key；通过（included）不计任何原因。
        val needsReview = filterExcluded.copy(
            admissionState = RecipientAdmissionState.NEEDS_REVIEW, filterKeys = emptyList()
        )
        assertEquals(
            listOf(RecipientAdmissionReasonKeys.NEEDS_REVIEW),
            BatchRecipientSelectionService.reasonKeysOf(needsReview)
        )
        assertEquals(
            emptyList<String>(),
            BatchRecipientSelectionService.reasonKeysOf(needsReview.copy(included = true))
        )
    }

    @Test
    fun `protection selection ignores the current explicit filters and layers (I-3)`() {
        // 08 人物硬保护：不满足当前筛选（邮箱域/研发类型）的成员的人工 HOLD 必须仍然可见。
        stubAdmissions("A" to DiscoveryReviewDecision.HOLD.name, "B" to DiscoveryReviewDecision.AUTO_PASSED.name)
        val filteredOut = expert("A", email = "a@blocked.com", type = null)

        // 带筛选的本次 scope 会先按显式条件排除 A，准入状态根本不读。
        val scoped = service.select(
            scope(emailDomains = listOf("allowed.com")),
            listOf(filteredOut)
        )
        assertEquals(1, scoped.excluded.size)
        assertEquals(RecipientAdmissionState.NOT_DISCOVERY, scoped.excluded.single().admissionState)

        // 保护读取不带任何本次筛选/层限制：A 的人工 HOLD 必须可见。
        val protection = service.selectProtection(listOf(filteredOut))
        assertEquals(RecipientAdmissionState.MANUAL_REJECTED, protection.excluded.single().admissionState)
        assertEquals(
            listOf(RecipientAdmissionReasonKeys.MANUAL_REJECTED),
            BatchRecipientSelectionService.reasonKeysOf(protection.excluded.single())
        )
    }

    private fun resolved(
        docId: String,
        decision: String?,
        identityChanged: Boolean = false
    ): DiscoveryReviewResolvedAdmission = DiscoveryReviewResolvedAdmission(
        docId = docId,
        decision = decision.takeUnless { identityChanged || decision == null },
        admitted = !identityChanged && decision in setOf(
            DiscoveryReviewDecision.AUTO_PASSED.name,
            DiscoveryReviewDecision.MANUAL_APPROVED.name,
            DiscoveryReviewDecision.LEGACY_APPROVED.name
        ),
        manual = decision in setOf(
            DiscoveryReviewDecision.MANUAL_APPROVED.name,
            DiscoveryReviewDecision.LEGACY_APPROVED.name
        ),
        initialized = decision != null,
        identityChanged = identityChanged,
        revision = if (decision == null) 0L else 1L,
        syncErrorCode = null
    )
}
