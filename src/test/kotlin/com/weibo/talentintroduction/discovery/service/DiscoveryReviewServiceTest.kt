package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.discovery.domain.AdmissionConfigSnapshot
import com.weibo.talentintroduction.discovery.domain.AutomaticAdmissionResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryAdmissionStatus
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewIdentity
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewItemState
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewExpertRow
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewPrepareRequest
import com.weibo.talentintroduction.discovery.domain.ExpertDiscoveryAdmission
import com.weibo.talentintroduction.discovery.domain.ExpertDiscoveryReviewItem
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewApplyOutcome
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRevokeOutcome
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.EligibilityResult
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.RestTemplate
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * 02（I-2/I-3/I-4）：审核服务的纯单元契约（无 DB、无 ES）。
 *
 * 真实事务/并发/幂等由 [com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepositoryIT]
 * 在真实 MySQL 上证明；本测试锁定输入校验、备注规则、actor、固定快照 hash 校验、
 * 版本冲突转 STALE、幂等确认与撤销边界。
 */
class DiscoveryReviewServiceTest {

    private val repository = Mockito.mock(DiscoveryReviewRepository::class.java)
    private val policy = Mockito.mock(DiscoveryAdmissionPolicy::class.java)
    private val writer = Mockito.mock(ExpertIndexWriterService::class.java)
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val expertIndexService = Mockito.mock(ExpertIndexService::class.java)
    private val objectMapper = ObjectMapper().registerModule(KotlinModule())
    private val clock = Clock.fixed(Instant.parse("2026-10-04T02:00:00Z"), ZoneOffset.UTC)
    private val properties = ElasticsearchProperties("http://es", "u", "p", "raw", "cand", "app")
    private val now: LocalDateTime = LocalDateTime.parse("2026-10-04T02:00:00")

    private val service = DiscoveryReviewService(
        repository, policy, writer, restTemplate, properties, expertIndexService, objectMapper, clock
    )

    private fun anyTime(): LocalDateTime = Mockito.any(LocalDateTime::class.java) ?: now

    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun config() = AdmissionConfigSnapshot(
        requireOrcid = true, requireValidEmail = true, requireDoctoralDegree = false,
        excludeChineseNationality = false, enableAgeFilter = false, maxAgeExclusive = 70,
        enableHIndexFilter = false, minHIndex = 5, enableCitationFilter = false, minCitationCount = 50,
        enableActivityFilter = false, recentYearsThreshold = 5
    )

    private fun auto(status: DiscoveryAdmissionStatus = DiscoveryAdmissionStatus.AUTO_PASSED) =
        AutomaticAdmissionResult(status, emptyList(), emptyList(), "2026-10-04.1", Instant.parse("2026-10-04T02:00:00Z"), config())

    private fun profile(email: String? = "a@example.org", given: String = "Ada", family: String = "Lovelace") =
        ExpertProfile(esDocId = "doc1", orcidId = "0000-0001", email = email, givenNames = given, familyNames = family,
            country = "US", keyword = null, employment = null, institution = "X Univ", tags = listOf("discovered"))

    private fun snapshot(source: Map<String, Any?> = mapOf("orcidId" to "0000-0001")) =
        ExpertIndexWriterService.DiscoverySnapshot(source, 7L, 3L)

    private fun identity(docId: String = "doc1", email: String? = "a@example.org", given: String = "Ada", family: String = "Lovelace") =
        DiscoveryReviewIdentity.hash(docId, email, given, family)

    private fun batchHashOf(items: List<ExpertDiscoveryReviewItem>): String = DiscoveryIdentity.hash(
        items.sortedBy { it.expertDocId }
            .joinToString("\n") { "${it.expertDocId}|${it.expectedRevision}|${it.identityHash}|${it.snapshotHash}|${it.action}" }
    )

    private fun stagedItem(
        id: Long = 5L, docId: String = "doc1", action: String = "APPROVE",
        expectedRevision: Long = 0L, identityHash: String = identity(docId),
        snapshotHash: String = "s", state: String = DiscoveryReviewItemState.STAGED.name
    ) = ExpertDiscoveryReviewItem(
        id = id, batchKey = "b1", expertDocId = docId, sourceLevel = "RAW",
        identityHash = identityHash, snapshotHash = snapshotHash, expectedRevision = expectedRevision,
        action = action, state = state, snapshotJson = "{}", reasonSnapshotJson = null,
        actor = "op1", note = null, previousItemId = null, executionId = null, errorCode = null,
        createdAt = now, confirmedAt = null, appliedAt = null
    )

    // ── 输入校验（I-3） ──────────────────────────────────────────────────────

    @Test
    fun `prepare rejects more than one thousand targets`() {
        val request = DiscoveryReviewPrepareRequest(
            action = "APPROVE", docIds = (1..1001).map { "doc$it" }
        )
        assertThrows(IllegalArgumentException::class.java) { service.prepare(request, "op1") }
    }

    @Test
    fun `prepare requires a note for REJECT`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.prepare(DiscoveryReviewPrepareRequest(action = "REJECT", docIds = listOf("doc1")), "op1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.prepare(DiscoveryReviewPrepareRequest(action = "REJECT", docIds = listOf("doc1"), note = "   "), "op1")
        }
    }

    @Test
    fun `prepare rejects REVOKE action and blank actor`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.prepare(DiscoveryReviewPrepareRequest(action = "REVOKE", docIds = listOf("doc1")), "op1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.prepare(DiscoveryReviewPrepareRequest(action = "APPROVE", docIds = listOf("doc1")), "  ")
        }
    }

    @Test
    fun `prepare persists admission and a staged item and returns a reproducible batch hash`() {
        Mockito.`when`(writer.readDiscoveryDocument(ExpertIndexLevel.RAW, "doc1")).thenReturn(snapshot())
        Mockito.`when`(writer.discoveryProfile(anyString(), Mockito.any<Map<String, Any?>>() ?: emptyMap())).thenReturn(profile())
        Mockito.`when`(policy.evaluate(Mockito.any(ExpertProfile::class.java) ?: profile())).thenReturn(auto())
        Mockito.`when`(repository.findAdmission("doc1")).thenReturn(null)
        Mockito.`when`(repository.insertItem(anyString(), anyString(), anyString(), anyString(), anyString(),
            anyLong(), anyString(), anyString(), anyString(), any(), anyString(), any(), any(), any(), anyTime()))
            .thenReturn(42L)

        val result = service.prepare(
            DiscoveryReviewPrepareRequest(action = "APPROVE", docIds = listOf("doc1"), expectedRevisions = mapOf("doc1" to 0L)),
            "op1"
        )

        assertEquals(1, result.itemCount)
        assertEquals("doc1", result.items.single().docId)
        assertEquals(identity(), result.items.single().identityHash)
        assertEquals(0L, result.items.single().expectedRevision)
        Mockito.verify(repository).initializeAdmission(anyString(), anyString(), anyString(), any(), anyTime())
        Mockito.verify(repository).insertItem(anyString(), anyString(), anyString(), anyString(), anyString(),
            anyLong(), anyString(), anyString(), anyString(), any(), anyString(), any(), any(), any(), anyTime())
    }

    // ── 确认（I-3/I-4） ──────────────────────────────────────────────────────

    @Test
    fun `confirm rejects a batch hash that does not match the stored snapshot`() {
        val item = stagedItem()
        Mockito.`when`(repository.findItemsByBatch("b1")).thenReturn(listOf(item))
        assertThrows(DiscoveryReviewConflictException::class.java) {
            service.confirm("b1", "deadbeef", "op1")
        }
        Mockito.verify(repository, Mockito.never()).applyItem(anyLong(), anyString(), anyString(), any(), anyTime())
    }

    @Test
    fun `confirm applies a staged item and returns APPLIED`() {
        val item = stagedItem()
        Mockito.`when`(repository.findItemsByBatch("b1")).thenReturn(listOf(item))
        Mockito.`when`(writer.readDiscoveryDocument(ExpertIndexLevel.RAW, "doc1")).thenReturn(snapshot())
        Mockito.`when`(writer.discoveryProfile(anyString(), Mockito.any<Map<String, Any?>>() ?: emptyMap())).thenReturn(profile())
        Mockito.`when`(repository.applyItem(anyLong(), anyString(), anyString(), any(), anyTime()))
            .thenReturn(DiscoveryReviewApplyOutcome.APPLIED)
        Mockito.`when`(writer.projectDiscoveryCandidate(anyString(), Mockito.any<Map<String, Any?>>() ?: emptyMap(), anyString()))
            .thenReturn(ExpertIndexWriterService.DiscoveryCandidateProjection.PROJECTED)

        val result = service.confirm("b1", batchHashOf(listOf(item)), "op1")

        assertEquals(1, result.applied)
        assertEquals(0, result.stale)
        assertEquals(1, result.total)
    }

    @Test
    fun `repeated confirm is idempotent for already applied items`() {
        val item = stagedItem(state = DiscoveryReviewItemState.APPLIED.name)
        Mockito.`when`(repository.findItemsByBatch("b1")).thenReturn(listOf(item))

        val result = service.confirm("b1", batchHashOf(listOf(item)), "op1")

        assertEquals(1, result.applied)
        Mockito.verify(repository, Mockito.never()).applyItem(anyLong(), anyString(), anyString(), any(), anyTime())
    }

    @Test
    fun `confirm reports STALE when the current conclusion moved on`() {
        val item = stagedItem(expectedRevision = 0L)
        Mockito.`when`(repository.findItemsByBatch("b1")).thenReturn(listOf(item))
        Mockito.`when`(writer.readDiscoveryDocument(ExpertIndexLevel.RAW, "doc1")).thenReturn(snapshot())
        Mockito.`when`(writer.discoveryProfile(anyString(), Mockito.any<Map<String, Any?>>() ?: emptyMap())).thenReturn(profile())
        Mockito.`when`(repository.applyItem(anyLong(), anyString(), anyString(), any(), anyTime()))
            .thenReturn(DiscoveryReviewApplyOutcome.STALE)

        val result = service.confirm("b1", batchHashOf(listOf(item)), "op1")

        assertEquals(1, result.stale)
        assertEquals(0, result.applied)
    }

    @Test
    fun `confirm marks the item FAILED when the ES source is gone`() {
        val item = stagedItem()
        Mockito.`when`(repository.findItemsByBatch("b1")).thenReturn(listOf(item))
        Mockito.`when`(writer.readDiscoveryDocument(ExpertIndexLevel.RAW, "doc1")).thenReturn(null)

        val result = service.confirm("b1", batchHashOf(listOf(item)), "op1")

        assertEquals(1, result.failed)
        Mockito.verify(repository).markItemFailed(anyLong(), anyString(), anyTime())
        Mockito.verify(repository, Mockito.never()).applyItem(anyLong(), anyString(), anyString(), any(), anyTime())
    }

    // ── 撤销（I-2） ──────────────────────────────────────────────────────────

    @Test
    fun `revoke re-runs the automatic check and returns the restored conclusion`() {
        val item = stagedItem(id = 9L, state = DiscoveryReviewItemState.APPLIED.name)
        Mockito.`when`(repository.findItem(9L)).thenReturn(item)
        Mockito.`when`(writer.readDiscoveryDocument(ExpertIndexLevel.RAW, "doc1")).thenReturn(snapshot())
        Mockito.`when`(writer.discoveryProfile(anyString(), Mockito.any<Map<String, Any?>>() ?: emptyMap())).thenReturn(profile())
        Mockito.`when`(policy.evaluate(Mockito.any(ExpertProfile::class.java) ?: profile()))
            .thenReturn(auto(DiscoveryAdmissionStatus.NEEDS_REVIEW))
        Mockito.`when`(repository.revokeCurrent(anyLong(), anyString(), any(), anyString(), anyString(), any(),
            anyString(), anyString(), anyString(), any(), anyTime()))
            .thenReturn(DiscoveryReviewRevokeOutcome.APPLIED to 77L)
        Mockito.`when`(repository.findAdmission("doc1")).thenReturn(
            ExpertDiscoveryAdmission("doc1", identity(), "NEEDS_REVIEW", 2L, 77L, "v", now, now)
        )

        val result = service.revoke(9L, "op1", "复核")

        assertEquals("NEEDS_REVIEW", result.decision)
        assertEquals(2L, result.revision)
        assertEquals(77L, result.itemId)
    }

    @Test
    fun `revoke refuses when the decision is no longer current or is itself a revoke`() {
        Mockito.`when`(repository.findItem(9L)).thenReturn(stagedItem(id = 9L, action = "REVOKE", state = DiscoveryReviewItemState.APPLIED.name))
        assertThrows(DiscoveryReviewConflictException::class.java) { service.revoke(9L, "op1", null) }

        Mockito.`when`(repository.findItem(10L)).thenReturn(stagedItem(id = 10L, state = DiscoveryReviewItemState.APPLIED.name))
        Mockito.`when`(writer.readDiscoveryDocument(ExpertIndexLevel.RAW, "doc1")).thenReturn(snapshot())
        Mockito.`when`(writer.discoveryProfile(anyString(), Mockito.any<Map<String, Any?>>() ?: emptyMap())).thenReturn(profile())
        Mockito.`when`(policy.evaluate(Mockito.any(ExpertProfile::class.java) ?: profile())).thenReturn(auto())
        Mockito.`when`(repository.revokeCurrent(anyLong(), anyString(), any(), anyString(), anyString(), any(),
            anyString(), anyString(), anyString(), any(), anyTime()))
            .thenReturn(DiscoveryReviewRevokeOutcome.NOT_CURRENT to null)
        assertThrows(DiscoveryReviewConflictException::class.java) { service.revoke(10L, "op1", null) }
    }

    @Test
    fun `revoke requires an actor`() {
        assertThrows(IllegalArgumentException::class.java) { service.revoke(1L, "  ", null) }
    }

    // ── 04：自动准入（I-1） ──────────────────────────────────────────────────

    private fun eligibility(eligible: Boolean = true, reasons: List<String> = emptyList()) =
        EligibilityResult(eligible, reasons)

    private fun admission(
        decision: String,
        docId: String = "doc1",
        identityHash: String = identity(docId),
        revision: Long = 1L,
        decisionItemId: Long? = 9L
    ) = ExpertDiscoveryAdmission(docId, identityHash, decision, revision, decisionItemId, "2026-10-04.1", now, now)

    private fun stubAutomatic(result: AutomaticAdmissionResult) {
        Mockito.`when`(
            policy.evaluate(
                Mockito.any(ExpertProfile::class.java) ?: profile(),
                Mockito.any(EligibilityResult::class.java) ?: eligibility()
            )
        ).thenReturn(result)
        Mockito.`when`(policy.evaluate(Mockito.any(ExpertProfile::class.java) ?: profile())).thenReturn(result)
    }

    @Test
    fun `recordAutomatic persists the automatic conclusion for a new document`() {
        stubAutomatic(auto())
        Mockito.`when`(repository.findAdmission("doc1")).thenReturn(null)

        val outcome = service.recordAutomatic("doc1", profile(), eligibility())

        assertEquals("AUTO_PASSED", outcome.decision)
        assertTrue(outcome.admitted)
        assertFalse(outcome.manual)
        Mockito.verify(repository).initializeAdmission(
            eqValue("doc1"), eqValue(identity()), eqValue("AUTO_PASSED"), any(), anyTime()
        )
    }

    @Test
    fun `recordAutomatic persists a needs-review conclusion with reasons`() {
        stubAutomatic(auto(DiscoveryAdmissionStatus.NEEDS_REVIEW))
        Mockito.`when`(repository.findAdmission("doc1")).thenReturn(null)

        val outcome = service.recordAutomatic("doc1", profile(), eligibility())

        assertEquals("NEEDS_REVIEW", outcome.decision)
        assertFalse(outcome.admitted)
        Mockito.verify(repository).initializeAdmission(
            eqValue("doc1"), eqValue(identity()), eqValue("NEEDS_REVIEW"), any(), anyTime()
        )
    }

    @Test
    fun `recordAutomatic never overwrites a same-identity manual decision`() {
        stubAutomatic(auto(DiscoveryAdmissionStatus.NEEDS_REVIEW))
        Mockito.`when`(repository.findAdmission("doc1")).thenReturn(admission("MANUAL_APPROVED"))

        val outcome = service.recordAutomatic("doc1", profile(), eligibility())

        assertEquals("MANUAL_APPROVED", outcome.decision)
        assertTrue(outcome.admitted)
        assertTrue(outcome.manual)
        Mockito.verify(repository, Mockito.never()).initializeAdmission(anyString(), anyString(), anyString(), any(), anyTime())
    }

    @Test
    fun `recordAutomatic never overwrites a same-identity HOLD or LEGACY_APPROVED`() {
        stubAutomatic(auto(DiscoveryAdmissionStatus.NEEDS_REVIEW))
        Mockito.`when`(repository.findAdmission("doc1")).thenReturn(admission("HOLD"))
        val hold = service.recordAutomatic("doc1", profile(), eligibility())
        assertEquals("HOLD", hold.decision)
        assertFalse(hold.admitted)

        Mockito.`when`(repository.findAdmission("doc1")).thenReturn(admission("LEGACY_APPROVED"))
        val legacy = service.recordAutomatic("doc1", profile(), eligibility())
        assertEquals("LEGACY_APPROVED", legacy.decision)
        assertTrue(legacy.admitted)
        Mockito.verify(repository, Mockito.never()).initializeAdmission(anyString(), anyString(), anyString(), any(), anyTime())
    }

    @Test
    fun `recordAutomatic refreshes an automatic conclusion through CAS`() {
        val jdbc = Mockito.mock(JdbcTemplate::class.java)
        val svc = DiscoveryReviewService(
            repository, policy, writer, restTemplate, properties, expertIndexService, objectMapper, clock,
            jdbcTemplate = jdbc
        )
        stubAutomatic(auto())
        Mockito.`when`(repository.findAdmission("doc1")).thenReturn(admission("NEEDS_REVIEW", revision = 2L))
        Mockito.`when`(jdbc.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(1)

        val outcome = svc.recordAutomatic("doc1", profile(), eligibility())

        assertEquals("AUTO_PASSED", outcome.decision)
        assertEquals(3L, outcome.revision)
        assertFalse(outcome.identityChanged)
    }

    @Test
    fun `recordAutomatic generates a new conclusion when the identity changed`() {
        val jdbc = Mockito.mock(JdbcTemplate::class.java)
        val svc = DiscoveryReviewService(
            repository, policy, writer, restTemplate, properties, expertIndexService, objectMapper, clock,
            jdbcTemplate = jdbc
        )
        stubAutomatic(auto(DiscoveryAdmissionStatus.NEEDS_REVIEW))
        Mockito.`when`(repository.findAdmission("doc1")).thenReturn(admission("MANUAL_APPROVED", identityHash = "old-hash"))
        Mockito.`when`(jdbc.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(1)

        val outcome = svc.recordAutomatic("doc1", profile(), eligibility())

        assertEquals("NEEDS_REVIEW", outcome.decision)
        assertFalse(outcome.admitted)
        assertTrue(outcome.identityChanged)
    }

    // ── 04：批量解析（I-1） ──────────────────────────────────────────────────

    @Test
    fun `resolveAdmissionBatch distinguishes uninitialized, current and identity-changed rows`() {
        Mockito.`when`(repository.findAdmissions(Mockito.anyCollection<String>() ?: emptyList())).thenReturn(
            listOf(
                admission("MANUAL_APPROVED", docId = "doc1"),
                admission("AUTO_PASSED", docId = "doc3", identityHash = "stale")
            )
        )
        Mockito.`when`(repository.findItemsByIds(Mockito.anyCollection<Long>() ?: emptyList())).thenReturn(
            listOf(stagedItem(id = 9L, state = DiscoveryReviewItemState.APPLIED.name).copy(errorCode = "CANDIDATE_SYNC_FAILED"))
        )

        val resolved = service.resolveAdmissionBatch(
            listOf(
                DiscoveryReviewAdmissionKey("doc1", identity("doc1")),
                DiscoveryReviewAdmissionKey("doc2", "other"),
                DiscoveryReviewAdmissionKey("doc3", identity("doc3"))
            )
        )

        assertTrue(resolved.getValue("doc1").initialized)
        assertTrue(resolved.getValue("doc1").admitted)
        assertTrue(resolved.getValue("doc1").manual)
        assertEquals("CANDIDATE_SYNC_FAILED", resolved.getValue("doc1").syncErrorCode)
        assertFalse(resolved.getValue("doc2").initialized)
        assertNull(resolved.getValue("doc2").decision)
        assertTrue(resolved.getValue("doc3").identityChanged)
        assertFalse(resolved.getValue("doc3").admitted)
        assertNull(resolved.getValue("doc3").decision)
    }

    @Test
    fun `resolveAdmissionBatch treats an identity change as not approved`() {
        Mockito.`when`(repository.findAdmissions(Mockito.anyCollection<String>() ?: emptyList())).thenReturn(
            listOf(admission("MANUAL_APPROVED", identityHash = "old"))
        )
        Mockito.`when`(repository.findItemsByIds(Mockito.anyCollection<Long>() ?: emptyList())).thenReturn(emptyList())

        val resolved = service.resolveAdmissionBatch(listOf(DiscoveryReviewAdmissionKey("doc1", identity()))).getValue("doc1")

        assertTrue(resolved.identityChanged)
        assertFalse(resolved.admitted)
        assertNull(resolved.decision)
        assertFalse(resolved.manual)
    }

    // ── 04：候选投影与同步失败（I-2） ────────────────────────────────────────

    private fun stubProjection(result: ExpertIndexWriterService.DiscoveryCandidateProjection) {
        Mockito.`when`(writer.projectDiscoveryCandidate(anyString(), Mockito.any<Map<String, Any?>>() ?: emptyMap(), anyString()))
            .thenReturn(result)
    }

    @Test
    fun `projectApprovedCandidate reports a candidate sync failure separately`() {
        stubProjection(ExpertIndexWriterService.DiscoveryCandidateProjection.WRITE_FAILED)

        val sync = service.projectApprovedCandidate("doc1", mapOf("email" to "a@example.org"), "AUTO_PASSED")

        assertFalse(sync.ok)
        assertEquals(DiscoveryReviewService.CANDIDATE_SYNC_FAILED, sync.errorCode)
    }

    @Test
    fun `projectApprovedCandidate treats already-present and not-admitted as no failure`() {
        stubProjection(ExpertIndexWriterService.DiscoveryCandidateProjection.ALREADY_PRESENT)
        assertTrue(service.projectApprovedCandidate("doc1", mapOf("email" to "a@example.org"), "AUTO_PASSED").ok)

        stubProjection(ExpertIndexWriterService.DiscoveryCandidateProjection.NOT_ADMITTED)
        val notAdmitted = service.projectApprovedCandidate("doc1", mapOf("email" to "a@example.org"), "HOLD")
        assertTrue(notAdmitted.ok)
        assertNull(notAdmitted.errorCode)
    }

    @Test
    fun `confirm records CANDIDATE_SYNC_FAILED without failing the saved review`() {
        val jdbc = Mockito.mock(JdbcTemplate::class.java)
        val svc = DiscoveryReviewService(
            repository, policy, writer, restTemplate, properties, expertIndexService, objectMapper, clock,
            jdbcTemplate = jdbc
        )
        val item = stagedItem()
        Mockito.`when`(repository.findItemsByBatch("b1")).thenReturn(listOf(item))
        Mockito.`when`(writer.readDiscoveryDocument(ExpertIndexLevel.RAW, "doc1")).thenReturn(snapshot())
        Mockito.`when`(writer.discoveryProfile(anyString(), Mockito.any<Map<String, Any?>>() ?: emptyMap())).thenReturn(profile())
        Mockito.`when`(repository.applyItem(anyLong(), anyString(), anyString(), any(), anyTime()))
            .thenReturn(DiscoveryReviewApplyOutcome.APPLIED)
        stubProjection(ExpertIndexWriterService.DiscoveryCandidateProjection.WRITE_FAILED)

        val result = svc.confirm("b1", batchHashOf(listOf(item)), "op1")

        assertEquals(1, result.applied)
        Mockito.verify(jdbc).update(
            anyString(), eqValue(DiscoveryReviewService.CANDIDATE_SYNC_FAILED), any(), any()
        )
    }

    @Test
    fun `retryBatchCandidateSync re-projects only failed items`() {
        val failed = stagedItem(id = 5L, state = DiscoveryReviewItemState.APPLIED.name)
            .copy(errorCode = DiscoveryReviewService.CANDIDATE_SYNC_FAILED)
        val clean = stagedItem(id = 6L, docId = "doc2", state = DiscoveryReviewItemState.APPLIED.name)
        Mockito.`when`(repository.findItemsByBatch("b1")).thenReturn(listOf(failed, clean))
        Mockito.`when`(writer.readDiscoveryDocument(ExpertIndexLevel.RAW, "doc1")).thenReturn(snapshot())
        stubProjection(ExpertIndexWriterService.DiscoveryCandidateProjection.PROJECTED)

        val result = service.retryBatchCandidateSync("b1", "op1")

        assertEquals(1, result.retried)
        assertEquals(1, result.synced)
        assertEquals(0, result.failed)
        Mockito.verify(writer, Mockito.never()).readDiscoveryDocument(ExpertIndexLevel.RAW, "doc2")
    }

    // ── 04：存量初始化（I-4） ────────────────────────────────────────────────

    private fun scanExpert(docId: String, profile: ExpertProfile = profile()): DiscoveryReviewScanService.ScanExpert =
        DiscoveryReviewScanService.ScanExpert(
            docId = docId,
            level = ExpertIndexLevel.RAW,
            source = mapOf("orcidId" to "0000-0001"),
            seqNo = 1L,
            primaryTerm = 1L,
            profile = profile,
            row = DiscoveryReviewExpertRow(
                docId = docId, level = "RAW", orcidId = "0000-0001", email = "a@example.org",
                givenNames = "Ada", familyNames = "Lovelace", institution = "X Univ", country = "US",
                researchFields = null, disciplineCategory = null, institutionEvidence = null,
                filterResult = null, tags = emptyList(), automaticStatus = "NEEDS_REVIEW",
                automaticReasons = emptyList(), automaticHints = emptyList(), revision = 0L,
                initialized = false,
                decision = "NEEDS_REVIEW", decisionManual = false, identityChanged = false,
                reviewedActor = null, reviewedAt = null, addressWarning = null
            )
        )

    private fun stubScan(scan: DiscoveryReviewScanService, experts: List<DiscoveryReviewScanService.ScanExpert>) {
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val handler = invocation.getArgument<(List<DiscoveryReviewScanService.ScanExpert>) -> Boolean>(2)
            handler(experts)
            Unit
        }.`when`(scan).scanAll(
            anyScanFilter(), Mockito.anyInt(), anyScanHandler()
        )
    }

    private fun anyScanFilter(): DiscoveryReviewScanService.DiscoveryReviewFilter =
        Mockito.any(DiscoveryReviewScanService.DiscoveryReviewFilter::class.java)
            ?: DiscoveryReviewScanService.DiscoveryReviewFilter(ExpertIndexLevel.RAW, null, null, null, null)

    private fun anyScanHandler(): (List<DiscoveryReviewScanService.ScanExpert>) -> Boolean =
        Mockito.any<(List<DiscoveryReviewScanService.ScanExpert>) -> Boolean>() ?: { true }

    @Test
    fun `runInitializeWorker records only new admissions and is idempotent`() {
        val scan = Mockito.mock(DiscoveryReviewScanService::class.java)
        val svc = DiscoveryReviewService(
            repository, policy, writer, restTemplate, properties, expertIndexService, objectMapper, clock,
            scanService = scan
        )
        stubAutomatic(auto())
        stubScan(scan, listOf(scanExpert("doc1"), scanExpert("doc2")))
        Mockito.`when`(repository.findAdmissions(Mockito.anyCollection<String>() ?: emptyList())).thenReturn(emptyList())

        val first = svc.runInitializeWorker("init-1", listOf(ExpertIndexLevel.RAW))

        assertEquals(2, first.recorded)
        assertEquals(0, first.unchanged)
        Mockito.verify(repository, Mockito.times(2)).initializeAdmission(anyString(), anyString(), anyString(), any(), anyTime())

        Mockito.reset(repository)
        stubAutomatic(auto())
        stubScan(scan, listOf(scanExpert("doc1"), scanExpert("doc2")))
        Mockito.`when`(repository.findAdmissions(Mockito.anyCollection<String>() ?: emptyList())).thenReturn(
            listOf(admission("AUTO_PASSED", docId = "doc1"), admission("AUTO_PASSED", docId = "doc2"))
        )

        val second = svc.runInitializeWorker("init-1", listOf(ExpertIndexLevel.RAW))

        assertEquals(0, second.recorded)
        assertEquals(2, second.unchanged)
        Mockito.verify(repository, Mockito.never()).initializeAdmission(anyString(), anyString(), anyString(), any(), anyTime())
    }

    @Test
    fun `runInitializeWorker writes LEGACY_APPROVED only from a valid legacy receipt`() {
        val scan = Mockito.mock(DiscoveryReviewScanService::class.java)
        val svc = DiscoveryReviewService(
            repository, policy, writer, restTemplate, properties, expertIndexService, objectMapper, clock,
            scanService = scan
        )
        stubAutomatic(auto(DiscoveryAdmissionStatus.LEGACY_APPROVED))
        stubScan(scan, listOf(scanExpert("doc1")))
        Mockito.`when`(repository.findAdmissions(Mockito.anyCollection<String>() ?: emptyList())).thenReturn(emptyList())

        val outcome = svc.runInitializeWorker("init-1", listOf(ExpertIndexLevel.RAW))

        assertEquals(1, outcome.recorded)
        Mockito.verify(repository).initializeAdmission(
            eqValue("doc1"), eqValue(identity()), eqValue("LEGACY_APPROVED"), any(), anyTime()
        )
    }

    @Test
    fun `runInitializeWorker dedupes by real docId across layers`() {
        val scan = Mockito.mock(DiscoveryReviewScanService::class.java)
        val svc = DiscoveryReviewService(
            repository, policy, writer, restTemplate, properties, expertIndexService, objectMapper, clock,
            scanService = scan
        )
        stubAutomatic(auto())
        stubScan(scan, listOf(scanExpert("doc1")))
        Mockito.`when`(repository.findAdmissions(Mockito.anyCollection<String>() ?: emptyList())).thenReturn(emptyList())

        val outcome = svc.runInitializeWorker(
            "init-1", listOf(ExpertIndexLevel.RAW, ExpertIndexLevel.CANDIDATE)
        )

        assertEquals(1, outcome.recorded)
        assertEquals(1, outcome.skipped)
        Mockito.verify(repository, Mockito.times(1)).initializeAdmission(anyString(), anyString(), anyString(), any(), anyTime())
    }
}
