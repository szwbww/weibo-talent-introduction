package com.weibo.talentintroduction.discovery.repository

import com.weibo.talentintroduction.discovery.domain.ExpertDiscoveryAdmission
import com.weibo.talentintroduction.discovery.domain.ExpertDiscoveryReviewItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 02（I-1/I-2/I-4）：审核持久化真实 MySQL 集成（`-DmysqlIt=true`，本机
 * 127.0.0.1:3306/talent_introduction，Flyway 迁移到最新含 V148）。
 *
 * 用真实行锁与唯一约束验证 H2 无法替代的语义：revision CAS、身份 CAS、同 doc 并发只一个成功、
 * 幂等应用、撤销只作用于当前有效决策、初始化不覆盖已有结论。
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = ["spring.flyway.placeholder-replacement=false"])
@Import(DiscoveryReviewRepository::class)
class DiscoveryReviewRepositoryIT {

    @Autowired
    private lateinit var repository: DiscoveryReviewRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val now: LocalDateTime = LocalDateTime.parse("2026-10-04T02:00:00")

    @BeforeEach
    fun clean() {
        jdbcTemplate.update("DELETE FROM expert_discovery_review_item")
        jdbcTemplate.update("DELETE FROM expert_discovery_admission")
    }

    private fun initAdmission(docId: String, identity: String = "h1", decision: String = "NEEDS_REVIEW") {
        repository.initializeAdmission(docId, identity, decision, "v", now)
    }

    private fun insertStaged(
        batchKey: String,
        docId: String,
        action: String = "APPROVE",
        expectedRevision: Long = 0L,
        identity: String = "h1",
        snapshotHash: String = "snap"
    ): Long = repository.insertItem(
        batchKey, docId, "RAW", identity, snapshotHash, expectedRevision, action,
        DiscoveryReviewRepository.STATE_STAGED, "{}", null, "op1", null, null, null, now
    )

    @Test
    fun `initialize never overwrites an existing conclusion`() {
        initAdmission("doc1", decision = "NEEDS_REVIEW")
        initAdmission("doc1", decision = "MANUAL_APPROVED")

        val admission = repository.findAdmission("doc1")!!
        assertEquals("NEEDS_REVIEW", admission.decision)
        assertEquals(0L, admission.revision)
        assertNull(admission.decisionItemId)
    }

    @Test
    fun `items round-trip with batch, history and counts`() {
        initAdmission("doc1")
        val id = insertStaged("b1", "doc1", snapshotHash = "s1")
        insertStaged("b1", "doc2", snapshotHash = "s2")

        val item = repository.findItem(id)!!
        assertEquals("b1", item.batchKey)
        assertEquals("doc1", item.expertDocId)
        assertEquals("RAW", item.sourceLevel)
        assertEquals("STAGED", item.state)
        assertEquals(now, item.createdAt)
        assertNull(item.appliedAt)

        assertEquals(2, repository.findItemsByBatch("b1").size)
        assertEquals(1, repository.findHistory("doc1", 100).size)
        assertEquals(2, repository.batchStateCounts("b1")["STAGED"])
    }

    @Test
    fun `apply bumps the conclusion once and a stale expected revision is recorded without overwriting (I-4)`() {
        initAdmission("doc1")
        val first = insertStaged("b1", "doc1")

        assertEquals(
            DiscoveryReviewApplyOutcome.APPLIED,
            repository.applyItem(first, "h1", "MANUAL_APPROVED", "v", now)
        )
        val afterFirst = repository.findAdmission("doc1")!!
        assertEquals("MANUAL_APPROVED", afterFirst.decision)
        assertEquals(1L, afterFirst.revision)
        assertEquals(first, afterFirst.decisionItemId)

        // 第二个提交仍以为当前版本是 0 → 必须 STALE，绝不覆盖第一个结论。
        val second = insertStaged("b2", "doc1")
        assertEquals(
            DiscoveryReviewApplyOutcome.STALE,
            repository.applyItem(second, "h1", "REJECTED", "v", now)
        )
        val afterSecond = repository.findAdmission("doc1")!!
        assertEquals("MANUAL_APPROVED", afterSecond.decision)
        assertEquals(1L, afterSecond.revision)
        assertEquals("STALE", repository.findItem(second)!!.state)
    }

    @Test
    fun `an identity change is stale even when the revision matches (I-2)`() {
        initAdmission("doc1", identity = "h1")
        val item = insertStaged("b1", "doc1", identity = "h1")

        assertEquals(
            DiscoveryReviewApplyOutcome.STALE,
            repository.applyItem(item, "h2", "MANUAL_APPROVED", "v", now)
        )
        assertEquals(0L, repository.findAdmission("doc1")!!.revision)
    }

    @Test
    fun `applying the same item twice is idempotent`() {
        initAdmission("doc1")
        val item = insertStaged("b1", "doc1")

        assertEquals(DiscoveryReviewApplyOutcome.APPLIED, repository.applyItem(item, "h1", "MANUAL_APPROVED", "v", now))
        assertEquals(
            DiscoveryReviewApplyOutcome.ALREADY_APPLIED,
            repository.applyItem(item, "h1", "MANUAL_APPROVED", "v", now)
        )
        assertEquals(1L, repository.findAdmission("doc1")!!.revision)
    }

    @Test
    fun `batch key and expert doc id are unique together`() {
        initAdmission("doc1")
        insertStaged("b1", "doc1")
        assertThrows(DataIntegrityViolationException::class.java) { insertStaged("b1", "doc1") }
    }

    @Test
    fun `two concurrent reviews of the same expert leave exactly one applied`() {
        initAdmission("doc1")
        val a = insertStaged("b1", "doc1")
        val b = insertStaged("b2", "doc1")
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures = listOf(a, b).map { itemId ->
                pool.submit<DiscoveryReviewApplyOutcome> {
                    start.await(5, TimeUnit.SECONDS)
                    repository.applyItem(itemId, "h1", "MANUAL_APPROVED", "v", now)
                }
            }
            start.countDown()
            val outcomes = futures.map { it.get(15, TimeUnit.SECONDS) }.sortedBy { it.name }
            assertEquals(
                listOf(DiscoveryReviewApplyOutcome.APPLIED.name, DiscoveryReviewApplyOutcome.STALE.name),
                outcomes.map { it.name }
            )
            val admission = repository.findAdmission("doc1")!!
            assertEquals(1L, admission.revision)
            assertTrue(admission.decisionItemId == a || admission.decisionItemId == b)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `revoke writes a new revoke item and only for the current effective decision (I-2)`() {
        initAdmission("doc1")
        val approved = insertStaged("b1", "doc1")
        repository.applyItem(approved, "h1", "MANUAL_APPROVED", "v", now)

        val (outcome, revokeId) = repository.revokeCurrent(
            currentItemId = approved,
            actor = "op1",
            note = "复核",
            newDecision = "AUTO_PASSED",
            currentIdentityHash = "h1",
            policyVersion = "v",
            sourceLevel = "RAW",
            snapshotHash = "snap2",
            snapshotJson = "{}",
            reasonSnapshotJson = null,
            now = now
        )

        assertEquals(DiscoveryReviewRevokeOutcome.APPLIED, outcome)
        assertNotEquals(null, revokeId)
        val admission: ExpertDiscoveryAdmission = repository.findAdmission("doc1")!!
        assertEquals("AUTO_PASSED", admission.decision)
        assertEquals(2L, admission.revision)
        assertEquals(revokeId, admission.decisionItemId)
        val revokeItem: ExpertDiscoveryReviewItem = repository.findItem(revokeId!!)!!
        assertEquals("REVOKE", revokeItem.action)
        assertEquals(approved, revokeItem.previousItemId)

        // 原决策已不是当前有效决策 → 再次撤销必须 NOT_CURRENT。
        val (again, againId) = repository.revokeCurrent(
            approved, "op1", null, "AUTO_PASSED", "h1", "v", "RAW", "snap3", "{}", null, now
        )
        assertEquals(DiscoveryReviewRevokeOutcome.NOT_CURRENT, again)
        assertNull(againId)
    }

    // ---- 03：全页批次持久语义（I-1/I-3/I-4） ----

    @Test
    fun `mark batch ready promotes only the target batch`() {
        initAdmission("doc1")
        initAdmission("doc2")
        initAdmission("doc3")
        insertStaged("b1", "doc1")
        insertStaged("b1", "doc2")
        val other = insertStaged("b2", "doc3")

        assertEquals(2, repository.markBatchReady("b1"))
        assertEquals(2, repository.batchStateCounts("b1")["READY"])
        assertEquals("STAGED", repository.findItem(other)!!.state)
    }

    @Test
    fun `claim is exclusive to a live execution and retry reopens only failed items`() {
        initAdmission("doc1")
        initAdmission("doc2")
        initAdmission("doc3")
        val a = insertStaged("b1", "doc1")
        val b = insertStaged("b1", "doc2")
        val c = insertStaged("b1", "doc3")

        val first = repository.claimBatchItems("b1", 101L, 2, includeFailed = false)
        assertEquals(listOf(a, b), first.map { it.id })
        assertEquals("APPLYING", repository.findItem(a)!!.state)

        // 另一个执行不能抢走已被领取（APPLYING）的项，只能拿剩余项。
        assertEquals(listOf(c), repository.claimBatchItems("b1", 102L, 10, includeFailed = false).map { it.id })

        // 已应用项绝不进入申请/重试范围。
        assertEquals(DiscoveryReviewApplyOutcome.APPLIED, repository.applyItem(a, "h1", "MANUAL_APPROVED", "v", now))
        assertEquals(DiscoveryReviewApplyOutcome.APPLIED, repository.applyItem(b, "h1", "MANUAL_APPROVED", "v", now))
        repository.markItemFailed(c, "BOOM", now)
        assertEquals("FAILED", repository.findItem(c)!!.state)

        assertTrue(repository.claimBatchItems("b1", 103L, 10, includeFailed = false).isEmpty())
        assertEquals(listOf(c), repository.claimBatchItems("b1", 103L, 10, includeFailed = true).map { it.id })
        assertEquals("APPLIED", repository.findItem(a)!!.state)
    }

    @Test
    fun `cancel only touches unapplied items and keeps applied results`() {
        initAdmission("doc1")
        initAdmission("doc2")
        val applied = insertStaged("b1", "doc1")
        repository.applyItem(applied, "h1", "MANUAL_APPROVED", "v", now)
        val staged = insertStaged("b1", "doc2")

        assertEquals(1, repository.cancelUnappliedItems("b1"))
        assertEquals("APPLIED", repository.findItem(applied)!!.state)
        assertEquals("CANCELLED", repository.findItem(staged)!!.state)
    }

    @Test
    fun `id cursor pagination walks the batch without skipping`() {
        initAdmission("doc1")
        initAdmission("doc2")
        initAdmission("doc3")
        val a = insertStaged("b1", "doc1")
        val b = insertStaged("b1", "doc2")
        val c = insertStaged("b1", "doc3")

        val page1 = repository.findItemsByBatchPage("b1", 0L, 2)
        assertEquals(listOf(a, b), page1.map { it.id })
        val page2 = repository.findItemsByBatchPage("b1", page1.last().id, 2)
        assertEquals(listOf(c), page2.map { it.id })
        assertTrue(repository.findItemsByBatchPage("b1", c, 2).isEmpty())
    }

    @Test
    fun `batch created at is the earliest item timestamp for the 24h expiry`() {
        initAdmission("doc1")
        initAdmission("doc2")
        repository.insertItem("b1", "doc1", "RAW", "h1", "s1", 0L, "APPROVE", "STAGED", "{}", null, "op1", null, null, null, now.minusHours(2))
        repository.insertItem("b1", "doc2", "RAW", "h1", "s2", 0L, "APPROVE", "STAGED", "{}", null, "op1", null, null, null, now)

        assertEquals(now.minusHours(2), repository.findBatchCreatedAt("b1"))
        assertNull(repository.findBatchCreatedAt("missing-batch"))
    }
}
