package com.weibo.talentintroduction.campaign.repository

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.time.LocalDateTime

/**
 * 验证明细仓储的真实 MySQL 集成测试（`-DmysqlIt=true`，testcontainers MySQL 8.0.36）。
 *
 * 表由真实 V138 迁移建立（不是手写 DDL），因此同时验证迁移契约：H2 不能替代
 * utf8mb4_bin 身份键比较、唯一约束、条件 UPDATE 的受影响行数与 ON DELETE CASCADE。
 *
 * 覆盖子计划 01 的验收：
 * - **I-9**：唯一键 (task_execution_id, orcid_id, email)、索引 (task_execution_id, id) 游标分页
 *   （默认 50 / 最大 100 / hasMore 由 limit+1 判定）、汇总来自本表、执行删除级联清理。
 * - **I-6**：先插 PENDING（列缺省值）；recordDecision 只作用于 PENDING 行，结果固定后只更新发送/标签列；
 *   markSending 条件预占必须影响 1 行；超长身份键明确拒绝而不截断。
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
    properties = [
        "spring.flyway.placeholder-replacement=false"
    ]
)
@Import(BatchEmailVerificationRepository::class)
class BatchEmailVerificationRepositoryIT {

    companion object {
        private const val EXECUTION_ID = 9001L
        private const val OTHER_EXECUTION_ID = 9002L
        private val NOW: LocalDateTime = LocalDateTime.of(2026, 9, 24, 10, 15, 30, 123_000_000)

        private class KotlinMySqlContainer(image: String) :
            MySQLContainer<KotlinMySqlContainer>(image)

        private val mysql = KotlinMySqlContainer("mysql:8.0.36")
            .withDatabaseName("talent_introduction")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @BeforeAll
        fun startMysql() {
            check(DockerClientFactory.instance().isDockerAvailable) {
                "Docker is required for the batch email verification tests"
            }
            mysql.start()
        }

        @JvmStatic
        @AfterAll
        fun stopMysql() {
            if (mysql.isRunning) mysql.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }

    @Autowired
    private lateinit var repository: BatchEmailVerificationRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanDetails() {
        jdbcTemplate.update("DELETE FROM batch_email_verification")
        jdbcTemplate.update("DELETE FROM task_execution")
        seedExecution(EXECUTION_ID)
        seedExecution(OTHER_EXECUTION_ID)
    }

    // ------------------------------------------------------------------
    // I-6：PENDING 缺省、结论只写一次、条件预占
    // ------------------------------------------------------------------

    @Test
    fun `a pending row carries the documented defaults and millisecond timestamps`() {
        val id = repository.insertPending(
            EXECUTION_ID, "doc-real-1", "0001-0002", "Given Family", "a@b.com", NOW
        )

        val row = repository.listAfter(EXECUTION_ID, 0L, 10).single()
        assertEquals(id, row.id)
        assertEquals(EXECUTION_ID, row.taskExecutionId)
        assertEquals("doc-real-1", row.expertDocId)
        assertEquals("0001-0002", row.orcidId)
        assertEquals("Given Family", row.expertName)
        assertEquals("a@b.com", row.email)
        assertEquals(BatchEmailVerificationDecision.PENDING, row.decision)
        assertNull(row.providerState)
        assertNull(row.providerReason)
        assertNull(row.errorCode)
        assertEquals(0, row.requestCount)
        assertNull(row.checkedAt)
        assertEquals(BatchEmailVerificationSendStatus.NOT_SENT, row.sendStatus)
        assertNull(row.sendReason)
        assertEquals(BatchEmailVerificationTagStatus.NOT_REQUIRED, row.tagStatus)
        assertNull(row.tagError)
        assertEquals(NOW, row.createdAt)
        assertEquals(NOW, row.updatedAt)
    }

    @Test
    fun `the decision is written once and later updates never overwrite it`() {
        val id = repository.insertPending(EXECUTION_ID, null, "0001", "Name", "a@b.com", NOW)

        assertEquals(
            1,
            repository.recordDecision(
                id, BatchEmailVerificationDecision.PASS, "deliverable", "accepted_email", null, 1, NOW, NOW
            )
        )
        // 结论固定后再写结论：0 行（不覆盖先前验证结论）。
        assertEquals(
            0,
            repository.recordDecision(
                id, BatchEmailVerificationDecision.ERROR, null, null, BatchEmailVerificationErrorCodes.SERVICE_ERROR, 2, NOW, NOW
            )
        )
        assertEquals(1, repository.recordTag(id, BatchEmailVerificationTagStatus.APPLIED, null, NOW))
        assertEquals(1, repository.recordSend(id, BatchEmailVerificationSendStatus.SENT, null, NOW))

        val row = repository.listAfter(EXECUTION_ID, 0L, 10).single()
        assertEquals(BatchEmailVerificationDecision.PASS, row.decision)
        assertEquals("deliverable", row.providerState)
        assertEquals("accepted_email", row.providerReason)
        assertNull(row.errorCode)
        assertEquals(1, row.requestCount)
        assertEquals(NOW, row.checkedAt)
        assertEquals(BatchEmailVerificationTagStatus.APPLIED, row.tagStatus)
        assertEquals(BatchEmailVerificationSendStatus.SENT, row.sendStatus)
    }

    @Test
    fun `mark sending reserves exactly once and only for a passed row`() {
        val pending = repository.insertPending(EXECUTION_ID, null, "0001", "Name", "a@b.com", NOW)
        val rejected = repository.insertPending(EXECUTION_ID, null, "0002", "Name", "b@b.com", NOW)
        repository.recordDecision(rejected, BatchEmailVerificationDecision.SKIP, "risky", null, null, 1, NOW, NOW)

        // 非 PASS 行永远不能预占发送。
        assertFalse(repository.markSending(pending, NOW))
        assertFalse(repository.markSending(rejected, NOW))

        repository.recordDecision(pending, BatchEmailVerificationDecision.PASS, "deliverable", null, null, 1, NOW, NOW)
        assertTrue(repository.markSending(pending, NOW))
        // 第二次预占是状态冲突（重复目标），不得重发。
        assertFalse(repository.markSending(pending, NOW))

        val row = repository.listAfter(EXECUTION_ID, 0L, 10).first { it.id == pending }
        assertEquals(BatchEmailVerificationSendStatus.SENDING, row.sendStatus)
        assertNull(row.sendReason)
    }

    @Test
    fun `over long identity keys are rejected instead of truncated while the name is truncated`() {
        assertThrows(IllegalArgumentException::class.java) {
            repository.insertPending(EXECUTION_ID, null, "0001", "Name", "a".repeat(321), NOW)
        }
        assertThrows(IllegalArgumentException::class.java) {
            repository.insertPending(EXECUTION_ID, null, "o".repeat(129), "Name", "a@b.com", NOW)
        }

        repository.insertPending(EXECUTION_ID, null, "0001", "N".repeat(300), "a@b.com", NOW)
        val row = repository.listAfter(EXECUTION_ID, 0L, 10).single()
        assertEquals(256, row.expertName?.length)
        assertEquals("a@b.com", row.email)
    }

    // ------------------------------------------------------------------
    // I-9：唯一约束、严格比较、游标分页、汇总、级联清理
    // ------------------------------------------------------------------

    @Test
    fun `the unique key rejects a duplicate target and identity keys compare strictly`() {
        repository.insertPending(EXECUTION_ID, null, "0001", "Name", "a@b.com", NOW)
        assertThrows(DataIntegrityViolationException::class.java) {
            repository.insertPending(EXECUTION_ID, null, "0001", "Other name", "a@b.com", NOW)
        }

        // utf8mb4_bin：大小写不同的地址是不同目标，不得被唯一键吞掉。
        repository.insertPending(EXECUTION_ID, null, "0001", "Name", "A@B.com", NOW)
        // 同目标在另一次执行是独立明细（下一次执行重新验证）。
        repository.insertPending(OTHER_EXECUTION_ID, null, "0001", "Name", "a@b.com", NOW)

        assertEquals(2, repository.aggregate(EXECUTION_ID).total)
        assertEquals(1, repository.aggregate(OTHER_EXECUTION_ID).total)
    }

    @Test
    fun `list after pages strictly by id cursor inside one execution`() {
        repeat(5) { index ->
            repository.insertPending(EXECUTION_ID, null, "orcid-$index", "Name", "user$index@b.com", NOW)
        }

        val first = repository.listAfter(EXECUTION_ID, 0L, 2)
        val second = repository.listAfter(EXECUTION_ID, first.last().id, 2)
        val third = repository.listAfter(EXECUTION_ID, second.last().id, 2)

        assertEquals(listOf("user0@b.com", "user1@b.com"), first.map { it.email })
        assertEquals(listOf("user2@b.com", "user3@b.com"), second.map { it.email })
        assertEquals(listOf("user4@b.com"), third.map { it.email })
        // 跨执行不得串数据。
        assertEquals(emptyList<BatchEmailVerificationRow>(), repository.listAfter(OTHER_EXECUTION_ID, 0L, 2))
    }

    @Test
    fun `read page combines the page with the aggregate of the whole execution`() {
        repeat(60) { index ->
            val id = repository.insertPending(EXECUTION_ID, null, "orcid-$index", "Name", "user$index@b.com", NOW)
            when (index % 3) {
                0 -> repository.recordDecision(id, BatchEmailVerificationDecision.PASS, "deliverable", null, null, 1, NOW, NOW)
                1 -> {
                    repository.recordDecision(id, BatchEmailVerificationDecision.SKIP, "risky", null, null, 1, NOW, NOW)
                    repository.recordTag(id, BatchEmailVerificationTagStatus.APPLIED, null, NOW)
                    repository.recordSend(id, BatchEmailVerificationSendStatus.SKIPPED, "EMAIL_VERIFICATION_REJECTED", NOW)
                }
                else -> repository.recordDecision(
                    id, BatchEmailVerificationDecision.ERROR, null, null, BatchEmailVerificationErrorCodes.NO_CREDITS, 1, NOW, NOW
                )
            }
        }

        val firstPage = repository.readPage(EXECUTION_ID)
        assertEquals(BatchEmailVerificationRepository.DEFAULT_PAGE_SIZE, firstPage.rows.size)
        assertTrue(firstPage.hasMore)
        assertEquals(60, firstPage.aggregate.total)
        assertEquals(20, firstPage.aggregate.passed)
        assertEquals(20, firstPage.aggregate.rejected)
        assertEquals(20, firstPage.aggregate.serviceError)
        assertEquals(20, firstPage.aggregate.tagApplied)
        assertEquals(20, firstPage.aggregate.sendSkipped)

        val secondPage = repository.readPage(EXECUTION_ID, afterId = firstPage.rows.last().id)
        assertEquals(10, secondPage.rows.size)
        assertFalse(secondPage.hasMore)
        assertEquals(60, secondPage.aggregate.total)

        // limit 上限 100、下限 1：分页大小永远落在契约范围内。
        repeat(60) { index ->
            repository.insertPending(EXECUTION_ID, null, "extra-$index", "Name", "extra$index@b.com", NOW)
        }
        val cappedPage = repository.readPage(EXECUTION_ID, limit = 1_000)
        assertEquals(BatchEmailVerificationRepository.MAX_PAGE_SIZE, cappedPage.rows.size)
        assertTrue(cappedPage.hasMore)
        assertEquals(1, repository.readPage(EXECUTION_ID, limit = 0).rows.size)
    }

    @Test
    fun `deleting the execution cascades to its detail rows`() {
        repository.insertPending(EXECUTION_ID, null, "0001", "Name", "a@b.com", NOW)
        repository.insertPending(OTHER_EXECUTION_ID, null, "0002", "Name", "b@b.com", NOW)

        jdbcTemplate.update("DELETE FROM task_execution WHERE id = ?", EXECUTION_ID)

        assertEquals(0, repository.aggregate(EXECUTION_ID).total)
        assertEquals(0L, countRows(EXECUTION_ID))
        // 其它执行的明细不受影响（无孤儿、无串数据）。
        assertEquals(1, repository.aggregate(OTHER_EXECUTION_ID).total)
    }

    @Test
    fun `history selects the latest original across executions and ignores invalid results`() {
        fun original(execution: Long, orcid: String, at: LocalDateTime, decision: String = "PASS",
                     state: String? = "deliverable", count: Int = 1, error: String? = null): Long {
            val id = repository.insertPending(execution, null, orcid, "Name", "shared@b.com", NOW)
            repository.recordDecision(id, decision, state, null, error, count, at, NOW)
            return id
        }
        val older = original(EXECUTION_ID, "older", NOW.minusDays(100))
        val latest = original(OTHER_EXECUTION_ID, "latest", NOW.minusDays(2), "SKIP", "unknown")
        original(EXECUTION_ID, "error", NOW.minusHours(1), "ERROR", null, error = "EMAIL_VERIFY_TIMEOUT")
        original(EXECUTION_ID, "future", NOW.plusDays(1))
        original(EXECUTION_ID, "reuse-without-source", NOW.minusHours(2), count = 0)
        original(EXECUTION_ID, "mismatched", NOW.minusHours(3), "PASS", "risky")
        original(EXECUTION_ID, "stale", NOW.minusYears(1))
        val pending = repository.insertPending(EXECUTION_ID, null, "pending", null, "shared@b.com", NOW)
        assertEquals(latest, repository.findReusable("shared@b.com", NOW)!!.id)
        assertEquals("SKIP", repository.findReusable("shared@b.com", NOW)!!.decision)
        assertNull(repository.findReusable("different@b.com", NOW))
        val reuse = repository.insertPending(OTHER_EXECUTION_ID, null, "reused", null, "shared@b.com", NOW)
        assertEquals(1, repository.recordReusedDecision(reuse, latest, "SKIP", "unknown", null, NOW.minusDays(2), NOW))
        assertEquals(0, repository.recordReusedDecision(reuse, older, "PASS", "deliverable", null, NOW, NOW))
        val row = repository.listAfter(OTHER_EXECUTION_ID, latest, 10).single()
        assertEquals(latest, row.reusedFromId)
        assertEquals(0, row.requestCount)
        assertEquals(NOW.minusDays(2), row.checkedAt)
        assertEquals("NOT_SENT", row.sendStatus)
        assertEquals("NOT_REQUIRED", row.tagStatus)
        assertEquals(latest, repository.findReusable("shared@b.com", NOW)!!.id)
        assertNull(repository.findReusable("shared@b.com", NOW.plusYears(2)))
        assertTrue(pending > latest)
    }

    @Test
    fun `one calendar year is a strict boundary and a reuse cannot renew it`() {
        val id = repository.insertPending(EXECUTION_ID, null, "boundary", null, "year@b.com", NOW)
        repository.recordDecision(id, "PASS", "deliverable", null, null, 1, NOW.minusYears(1), NOW)
        assertNull(repository.findReusable("year@b.com", NOW))
        assertEquals(id, repository.findReusable("year@b.com", NOW.minusNanos(1_000_000))!!.id)
        assertNull(repository.findReusable("year@b.com", NOW.minusYears(1).minusSeconds(1)))
    }

    @Test
    fun `retention preserves original results for a year but not ordinary or reused execution rows`() {
        val now = jdbcTemplate.queryForObject(
            "SELECT CONVERT_TZ(UTC_TIMESTAMP(3), '+00:00', '+08:00')", LocalDateTime::class.java)!!
        jdbcTemplate.update("UPDATE task_execution SET started_at = ?", now.minusDays(200))
        val original = repository.insertPending(EXECUTION_ID, null, "old", null, "keep@b.com", now)
        repository.recordDecision(original, "PASS", "deliverable", null, null, 1, now.minusDays(200), now)
        val copy = repository.insertPending(OTHER_EXECUTION_ID, null, "copy", null, "keep@b.com", now)
        repository.recordReusedDecision(copy, original, "PASS", "deliverable", null, now.minusDays(200), now)
        val repoClass = com.weibo.talentintroduction.task.repository.TaskExecutionRepository::class.java
        val method = repoClass.methods.single { it.name == "deleteOlderThan" }
        val sql = method.getAnnotation(org.springframework.data.jdbc.repository.query.Query::class.java).value
            .replace(":cutoff", "?").replace(":batchSize", "?")
        assertEquals(1, jdbcTemplate.update(sql, now.minusDays(90), 10))
        assertEquals(1L, countRows(EXECUTION_ID))
        assertEquals(0L, countRows(OTHER_EXECUTION_ID))
        assertEquals(original, repository.findReusable("keep@b.com", now)!!.id)
        // 原结果一旦过期，可随执行清理，复用过不会续期。
        jdbcTemplate.update("UPDATE batch_email_verification SET checked_at = ? WHERE id = ?", now.minusYears(1).minusDays(1), original)
        assertEquals(1, jdbcTemplate.update(sql, now.minusDays(90), 10))
        assertEquals(0L, countRows(EXECUTION_ID))
        seedExecution(EXECUTION_ID)
        jdbcTemplate.update("UPDATE task_execution SET started_at = ?", now.minusDays(200))
        val error = repository.insertPending(EXECUTION_ID, null, "error", null, "error@b.com", now)
        repository.recordDecision(error, "ERROR", null, null, "EMAIL_VERIFY_TIMEOUT", 1, now.minusDays(100), now)
        assertEquals(1, jdbcTemplate.update(sql, now.minusDays(90), 10))
    }

    private fun seedExecution(executionId: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO task_execution (id, task_type, trigger_type, status, started_at, created_at, updated_at)
            VALUES (?, 'MANUAL_INITIAL_OUTREACH', 'MANUAL', 'RUNNING', ?, ?, ?)
            """.trimIndent(),
            executionId, NOW, NOW, NOW
        )
    }

    private fun countRows(executionId: Long): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM batch_email_verification WHERE task_execution_id = ?",
            Long::class.java,
            executionId
        ) ?: 0L
}
