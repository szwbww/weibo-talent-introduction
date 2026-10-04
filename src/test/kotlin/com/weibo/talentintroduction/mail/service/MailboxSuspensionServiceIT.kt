package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 挂起服务真实 MySQL 集成测试（01/T4；mysqlIt 门禁）。
 *
 * 证明表存在性之外的服务语义：PUT/DELETE 幂等与身份隔离、不存在专家 404、无未处理
 * 409、原因 trim/null/长度 400、跨账号 pending 口径（排除模拟器与不存在账号、包含
 * enabled=false）、以及挂起写入不触碰其它业务表（I-1/I-2/I-3/I-5/I-6）。
 * 独立本地库、唯一数据，finally/tearDown cleanup。
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(
    properties = [
        "spring.flyway.placeholder-replacement=false"
    ]
)
@Import(MailboxSuspensionService::class)
class MailboxSuspensionServiceIT {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var service: MailboxSuspensionService

    @BeforeEach
    fun setUp() {
        cleanup()
        seedAccount("acc-a")
        seedAccount("acc-b")
        seedAccount("inactive-acc")
        seedContact(1, "Alice Expert", "alice@example.org")
        seedContact(2, "Bob Expert", "bob@example.org")
    }

    @AfterEach
    fun tearDown() {
        cleanup()
    }

    @Test
    fun `suspend and resume are idempotent and scoped per user`() {
        insertProcessing(1, "acc-a", 101, "MANUAL_REVIEW", "2026-09-01 09:00:00", "susp-it-1")

        val created = service.suspend("op1", 1L, "  等待材料  ")
        assertTrue(created.suspended)
        assertEquals("等待材料", created.suspendReason, "reason 先 trim")
        assertEquals(1L, created.suspensionPendingCount)
        assertFalse(created.followed)
        assertEquals(1L, suspensionRows("op1", 1L))

        // 重复 PUT：原样返回原原因，不覆盖（网络重试安全 I-5）。
        val repeated = service.suspend("op1", 1L, "新原因")
        assertEquals("等待材料", repeated.suspendReason)
        assertEquals(1L, suspensionRows("op1", 1L))

        // 另一用户看到未挂起但同样的跨账号 pending。
        val other = service.get("op2", 1L)
        assertFalse(other.suspended)
        assertNull(other.suspendReason)
        assertEquals(1L, other.suspensionPendingCount)

        // DELETE 幂等：第二次仍 suspended=false。
        assertEquals(false, service.resume("op1", 1L).suspended)
        assertEquals(false, service.resume("op1", 1L).suspended)
        assertEquals(0L, suspensionRows("op1", 1L))
    }

    @Test
    fun `suspend without pending mail conflicts and writes nothing`() {
        val conflict = assertThrows(MailboxSuspensionConflictException::class.java) {
            service.suspend("op1", 2L, "等待材料")
        }
        assertTrue(conflict.message!!.isNotBlank())
        assertEquals(0L, suspensionRows("op1", 2L))
    }

    @Test
    fun `unknown contact is not found for get suspend and resume`() {
        assertThrows(NoSuchElementException::class.java) { service.get("op1", 999L) }
        assertThrows(NoSuchElementException::class.java) { service.suspend("op1", 999L, null) }
        assertThrows(NoSuchElementException::class.java) { service.resume("op1", 999L) }
    }

    @Test
    fun `reason handles blank null boundary and overlong lengths`() {
        insertProcessing(1, "acc-a", 102, "MANUAL_REVIEW", "2026-09-01 09:00:00", "susp-it-len")
        insertProcessing(2, "acc-a", 103, "MANUAL_REVIEW", "2026-09-01 09:00:00", "susp-it-len2")

        assertThrows(IllegalArgumentException::class.java) {
            service.suspend("op1", 1L, "x".repeat(501))
        }
        assertEquals(0L, suspensionRows("op1", 1L), "超长校验失败不落行")

        assertEquals(true, service.suspend("op1", 1L, "y".repeat(500)).suspended)
        assertEquals(500, jdbcTemplate.queryForObject(
            "SELECT CHAR_LENGTH(reason) FROM expert_mailbox_suspension " +
                "WHERE username = 'op1' AND expert_contact_id = 1",
            Int::class.java
        ))

        // 空白 → null；null 原样为 null（未填写原因，绝不表示未挂起 I-1）。
        val blank = service.suspend("op1", 2L, "   ")
        assertTrue(blank.suspended)
        assertNull(blank.suspendReason)
        assertNull(jdbcTemplate.queryForObject(
            "SELECT reason FROM expert_mailbox_suspension WHERE username = 'op1' AND expert_contact_id = 2",
            String::class.java
        ))
    }

    @Test
    fun `pending count aggregates cross account real accounts only`() {
        seedAccount("SIMULATOR_NOOP")
        // acc-a + disabled inactive-acc 计入；模拟器/不存在账号/已处理不计。
        insertProcessing(1, "acc-a", 201, "MANUAL_REVIEW", "2026-09-01 09:00:00", "pc-1")
        insertProcessing(1, "inactive-acc", 202, "MANUAL_REVIEW", "2026-09-02 09:00:00", "pc-2")
        insertProcessing(1, "SIMULATOR_NOOP", 203, "MANUAL_REVIEW", "2026-09-03 09:00:00", "pc-3")
        insertProcessing(1, "ghost-acc", 204, "MANUAL_REVIEW", "2026-09-04 09:00:00", "pc-4")
        insertProcessing(1, "acc-a", 205, "PROCESSED", "2026-09-05 09:00:00", "pc-5")

        service.suspend("op1", 1L, null)
        assertEquals(2L, service.get("op1", 1L).suspensionPendingCount,
            "acc-a 与 disabled inactive-acc 计入；模拟器/不存在账号/已处理不计")
        // 未挂起行也拿到真实跨账号 pending。
        assertEquals(2L, service.get("op2", 1L).suspensionPendingCount)

        // 处理一封后计数下降，但挂起行仍在（I-2 无自动结束）。
        jdbcTemplate.update(
            "UPDATE inbound_mail_processing SET process_status = 'PROCESSED' WHERE message_id = 'pc-1'"
        )
        val after = service.get("op1", 1L)
        assertTrue(after.suspended, "处理完成不自动结束挂起")
        assertEquals(1L, after.suspensionPendingCount)
    }

    @Test
    fun `suspend never writes other business tables`() {
        insertProcessing(1, "acc-a", 301, "MANUAL_REVIEW", "2026-09-01 09:00:00", "iso-1")
        insertOutbound(1)
        jdbcTemplate.update(
            "INSERT INTO expert_follow (username, expert_contact_id, created_at) VALUES ('op1', 1, NOW())"
        )
        jdbcTemplate.update(
            "INSERT INTO expert_replied_dismissal (username, expert_contact_id, last_inbound_id, dismissed_at) " +
                "VALUES ('op1', 1, 1, NOW())"
        )
        val before = countsOf("expert_contact", "expert_follow", "expert_replied_dismissal",
            "mail_record", "inbound_mail_processing")

        service.suspend("op1", 1L, "等待材料")
        service.resume("op1", 1L)

        assertEquals(before, countsOf("expert_contact", "expert_follow", "expert_replied_dismissal",
            "mail_record", "inbound_mail_processing"), "挂起接口不得改动其它业务表 I-6")
        assertEquals(0L, suspensionRows("op1", 1L))
    }

    @Test
    fun `concurrent repeated suspend keeps exactly one row`() {
        insertProcessing(1, "acc-a", 401, "MANUAL_REVIEW", "2026-09-01 09:00:00", "conc-1")
        val pool = Executors.newFixedThreadPool(8)
        val latch = CountDownLatch(1)
        val errors = ConcurrentLinkedQueue<Throwable>()
        try {
            repeat(8) { index ->
                pool.submit {
                    latch.await()
                    runCatching { service.suspend("op1", 1L, "r$index") }.onFailure { errors.add(it) }
                }
            }
            latch.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "并发 PUT 必须全部完成")
        } finally {
            pool.shutdownNow()
        }
        assertTrue(errors.isEmpty(), "并发 PUT 不得失败: $errors")
        assertEquals(1L, suspensionRows("op1", 1L))
    }

    @Test
    fun `reason edit preserves suspension and can clear after all mail processed`() {
        insertProcessing(1, "acc-a", 901, "MANUAL_REVIEW", "2026-10-04 09:00:00", "reason-edit")
        service.suspend("op1", 1, null)
        assertEquals("等待材料", service.updateReason("op1", 1, "  等待材料  ").suspendReason)
        assertThrows(MailboxSuspensionConflictException::class.java) { service.updateReason("op2", 1, "other") }
        assertEquals("等待材料", service.get("op1", 1).suspendReason)
        jdbcTemplate.update("UPDATE inbound_mail_processing SET process_status = 'PROCESSED'")
        val cleared = service.updateReason("op1", 1, "  ")
        assertTrue(cleared.suspended)
        assertNull(cleared.suspendReason)
        assertEquals(0L, cleared.suspensionPendingCount)
        assertEquals(500, service.updateReason("op1", 1, "字".repeat(500)).suspendReason!!.length)
        assertThrows(IllegalArgumentException::class.java) { service.updateReason("op1", 1, "字".repeat(501)) }
        service.resume("op1", 1)
        assertThrows(MailboxSuspensionConflictException::class.java) { service.updateReason("op1", 1, "late") }
        assertFalse(service.get("op1", 1).suspended)
        assertThrows(NoSuchElementException::class.java) { service.updateReason("op1", 9999, "missing") }
    }

    @Test
    fun `pending badge counts mail excluding only current user suspended contacts`() {
        insertProcessing(1, "acc-a", 911, "MANUAL_REVIEW", "2026-10-04 09:00:00", "badge-1")
        insertProcessing(1, "acc-b", 912, "MANUAL_REVIEW", "2026-10-04 09:00:00", "badge-2")
        insertProcessing(2, "inactive-acc", 913, "MANUAL_REVIEW", "2026-10-04 09:00:00", "badge-3")
        insertProcessing(2, "acc-a", 914, "MANUAL_REVIEW", "2026-10-04 09:00:00", "badge-unmatched")
        insertProcessing(2, "acc-a", 915, "PROCESSED", "2026-10-04 09:00:00", "badge-done")
        seedAccount("SIMULATOR_NOOP")
        insertProcessing(2, "SIMULATOR_NOOP", 916, "MANUAL_REVIEW", "2026-10-04 09:00:00", "badge-simulator")
        insertProcessing(2, "missing-account", 917, "MANUAL_REVIEW", "2026-10-04 09:00:00", "badge-orphan")
        jdbcTemplate.update("UPDATE mail_sender_account SET enabled = false WHERE account_code = 'inactive-acc'")
        jdbcTemplate.update("UPDATE inbound_mail_processing SET reason_type = 'QA_NO_MATCH' WHERE expert_contact_id = 1")
        jdbcTemplate.update("UPDATE inbound_mail_processing SET expert_contact_id = NULL, reason_type = 'UNMATCHED_CONTACT' WHERE message_id = 'badge-unmatched'")
        assertEquals(4L, service.pendingBadge("op1").manualReviewTotal)
        service.suspend("op1", 1, null)
        val badge = service.pendingBadge("op1")
        assertEquals(2L, badge.manualReviewTotal)
        assertEquals(1L, badge.countsByReasonType["UNMATCHED_CONTACT"])
        assertEquals(1L, badge.countsByReasonType["UNKNOWN"])
        assertFalse(badge.countsByReasonType.containsKey("QA_NO_MATCH"))
        assertEquals(4L, service.pendingBadge("op2").manualReviewTotal)
        service.resume("op1", 1)
        assertEquals(4L, service.pendingBadge("op1").manualReviewTotal)
    }

    @Test
    fun `suspension responses carry the real progress status across every endpoint`() {
        insertProcessing(1, "acc-a", 501, "MANUAL_REVIEW", "2026-09-01 09:00:00", "prog-1")

        fun mark(contactId: Long, status: String) {
            jdbcTemplate.update(
                "INSERT INTO expert_follow (username, expert_contact_id, created_at, progress_status) " +
                    "VALUES ('op1', ?, NOW(), ?)",
                contactId, status
            )
        }

        // 未标记 → NONE / followed=false。
        val untouched = service.get("op1", 1L)
        assertEquals(MailboxProgressStatus.NONE, untouched.progressStatus)
        assertFalse(untouched.followed)

        mark(1L, "PROVIDED")
        val provided = service.get("op1", 1L)
        assertEquals(MailboxProgressStatus.PROVIDED, provided.progressStatus)
        assertFalse(provided.followed, "已提供绝不能被说成跟进中 I-6")

        // PUT → 挂起响应同样带真实状态；挂起语义不变。
        val suspended = service.suspend("op1", 1L, "等待材料")
        assertTrue(suspended.suspended)
        assertEquals("等待材料", suspended.suspendReason)
        assertEquals(1L, suspended.suspensionPendingCount)
        assertEquals(MailboxProgressStatus.PROVIDED, suspended.progressStatus)
        assertFalse(suspended.followed)

        val reasonEdited = service.updateReason("op1", 1L, "等待补充")
        assertEquals(MailboxProgressStatus.PROVIDED, reasonEdited.progressStatus)
        assertEquals("等待补充", reasonEdited.suspendReason)
        assertEquals(1L, reasonEdited.suspensionPendingCount)

        // 切换标记不改挂起其它字段。
        jdbcTemplate.update(
            "UPDATE expert_follow SET progress_status = 'FOLLOWING' WHERE username = 'op1' AND expert_contact_id = 1"
        )
        val switched = service.get("op1", 1L)
        assertTrue(switched.suspended)
        assertEquals("等待补充", switched.suspendReason)
        assertEquals(1L, switched.suspensionPendingCount)
        assertEquals(MailboxProgressStatus.FOLLOWING, switched.progressStatus)
        assertTrue(switched.followed)

        // 标记期间消息被处理：挂起与标记状态都不变，pending 计数下降。
        jdbcTemplate.update(
            "UPDATE inbound_mail_processing SET process_status = 'PROCESSED' WHERE message_id = 'prog-1'"
        )
        val afterProcessing = service.get("op1", 1L)
        assertTrue(afterProcessing.suspended)
        assertEquals(0L, afterProcessing.suspensionPendingCount)
        assertEquals(MailboxProgressStatus.FOLLOWING, afterProcessing.progressStatus)

        val resumed = service.resume("op1", 1L)
        assertFalse(resumed.suspended)
        assertEquals(MailboxProgressStatus.FOLLOWING, resumed.progressStatus)
        assertTrue(resumed.followed)

        // 用户隔离 + 取消标记回 NONE。
        assertEquals(MailboxProgressStatus.NONE, service.get("op2", 1L).progressStatus)
        jdbcTemplate.update("DELETE FROM expert_follow WHERE username = 'op1' AND expert_contact_id = 1")
        val cleared = service.get("op1", 1L)
        assertEquals(MailboxProgressStatus.NONE, cleared.progressStatus)
        assertFalse(cleared.followed)
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private fun countsOf(vararg tables: String): Map<String, Long> =
        tables.associateWith { table ->
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Long::class.java) ?: 0L
        }

    private fun suspensionRows(username: String, contactId: Long): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expert_mailbox_suspension WHERE username = ? AND expert_contact_id = ?",
            Long::class.java, username, contactId
        )!!

    private fun cleanup() {
        jdbcTemplate.update("DELETE FROM expert_mailbox_suspension")
        jdbcTemplate.update("DELETE FROM expert_replied_dismissal")
        jdbcTemplate.update("DELETE FROM expert_follow")
        jdbcTemplate.update("DELETE FROM inbound_mail_tag")
        jdbcTemplate.update("DELETE FROM inbound_mail_processing")
        jdbcTemplate.update("DELETE FROM mail_record")
        jdbcTemplate.update("DELETE FROM expert_contact")
        jdbcTemplate.update("DELETE FROM campaign")
        jdbcTemplate.update("DELETE FROM mail_sender_account")
    }

    private fun seedAccount(code: String) {
        jdbcTemplate.update(
            """
            INSERT INTO mail_sender_account
                (account_code, sender_email, sender_name, smtp_host, smtp_port,
                 smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password)
            VALUES (?, ?, ?, 'smtp.fixture', 465, ?, ?, 'imap.fixture', 993, ?, ?)
            """.trimIndent(),
            code, "$code@fixture.local", code, code, "pw", code, "pw"
        )
    }

    private fun seedContact(id: Long, name: String, email: String) {
        jdbcTemplate.update(
            "INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id) " +
                "VALUES (?, ?, 'Fixture', (SELECT id FROM mail_sender_account WHERE account_code = 'acc-a'))",
            id, "FIXTURE-$id"
        )
        jdbcTemplate.update(
            """
            INSERT INTO expert_contact (id, campaign_id, orcid_id, expert_email, expert_name, current_status)
            VALUES (?, ?, ?, ?, ?, 'NEW')
            """.trimIndent(),
            id, id, "0000-0000-0000-%04d".format(id), email, name
        )
    }

    private fun insertProcessing(
        contactId: Long,
        accountCode: String,
        imapUid: Long,
        processStatus: String,
        receivedAt: String,
        messageId: String
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO inbound_mail_processing
                (sender_account_code, uid_validity, imap_uid, message_id, from_email, subject,
                 body, cleaned_body, received_at, process_status, process_reason, expert_contact_id)
            VALUES (?, 1, ?, ?, 'expert@example.org', 'subject', 'body', 'cleaned', ?, ?, 'QA_AUTO_REPLIED', ?)
            """.trimIndent(),
            accountCode, imapUid, messageId,
            Timestamp.valueOf(LocalDateTime.parse(receivedAt.replace(' ', 'T'))),
            processStatus, contactId
        )
    }

    private fun insertOutbound(contactId: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, triggered_by,
                 message_id, subject, body, send_status, sent_at, created_at)
            VALUES (?, 'OUTBOUND', 'INTRODUCTION', 'acc-a', 'SYSTEM', ?, 'subject', 'body',
                    'SENT', NOW(), NOW())
            """.trimIndent(),
            contactId, "out-$contactId"
        )
    }
}
