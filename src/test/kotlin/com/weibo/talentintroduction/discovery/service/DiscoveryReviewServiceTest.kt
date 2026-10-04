package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.discovery.domain.AdmissionConfigSnapshot
import com.weibo.talentintroduction.discovery.domain.AutomaticAdmissionResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryAdmissionStatus
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewIdentity
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewItemState
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewPrepareRequest
import com.weibo.talentintroduction.discovery.domain.ExpertDiscoveryAdmission
import com.weibo.talentintroduction.discovery.domain.ExpertDiscoveryReviewItem
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewApplyOutcome
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRevokeOutcome
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
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
}
