package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration
import java.time.LocalDateTime

/**
 * I-1/I-2/I-7 规则边界：纯分类窗口、邮件头变体、非首封短路与事务断言。
 * SQL 语义（真实锁/当前读/跨账号锚点）由 mysqlIt 集成测试证明。
 */
class InboundAutoResponseDetectorTest {

    private val jdbcTemplate = Mockito.mock(NamedParameterJdbcTemplate::class.java)
    private val detector = InboundAutoResponseDetector(jdbcTemplate)

    private val t0 = LocalDateTime.of(2026, 10, 20, 19, 7, 29)
    private val recordedSql = mutableListOf<String>()
    private val recordedParams = mutableListOf<Map<String, Any>>()
    private var rowSets: (String) -> List<Map<String, Any>> = { sql ->
        when (sql) {
            InboundAutoResponseDetector.LOCK_CONTACT_SQL -> listOf(mapOf("id" to 11L))
            else -> emptyList()
        }
    }

    @BeforeEach
    fun setUp() {
        TransactionSynchronizationManager.setActualTransactionActive(true)
        Mockito.`when`(jdbcTemplate.queryForList(Mockito.anyString(), Mockito.anyMap<String, Any>()))
            .thenAnswer { invocation ->
                val sql = invocation.getArgument<String>(0)
                @Suppress("UNCHECKED_CAST")
                val params = invocation.getArgument<Map<String, Any>>(1)
                recordedSql += sql
                recordedParams += params
                rowSets(sql)
            }
    }

    @AfterEach
    fun tearDown() {
        TransactionSynchronizationManager.setActualTransactionActive(false)
        recordedSql.clear()
        recordedParams.clear()
    }

    // ------------------------------------------------------------------
    // 纯分类：仅两条 OR 规则
    // ------------------------------------------------------------------

    @Test
    fun `header auto-replied hits even without a reliable window or any sent anchor`() {
        assertEquals(
            InboundAutoResponseDetector.AUTO_RESPONSE_HEADER,
            detector.classify("auto-replied", null, receivedAtReliable = false, sentAt = null)
        )
        assertEquals(
            InboundAutoResponseDetector.AUTO_RESPONSE_HEADER,
            detector.classify("  Auto-Replied ; owner=ml@example.org ", t0, true, t0.minusSeconds(1))
        )
    }

    @Test
    fun `header wins when both rules match`() {
        assertEquals(
            InboundAutoResponseDetector.AUTO_RESPONSE_HEADER,
            detector.classify("auto-replied", t0.plusSeconds(30), true, t0)
        )
    }

    @Test
    fun `no and auto-generated do not veto the five minute rule`() {
        for (header in listOf("no", "auto-generated", "AUTO-GENERATED", null)) {
            assertEquals(
                InboundAutoResponseDetector.AUTO_RESPONSE_FAST_5M,
                detector.classify(header, t0.plusSeconds(30), true, t0),
                "header=$header 未命中邮件头规则时仍必须执行五分钟规则"
            )
        }
    }

    @Test
    fun `window is inclusive of zero and three hundred seconds and excludes beyond`() {
        listOf(0L, 19L, 299L, 300L).forEach { seconds ->
            assertEquals(
                InboundAutoResponseDetector.AUTO_RESPONSE_FAST_5M,
                detector.classify(null, t0.plusSeconds(seconds), true, t0),
                "delta=$seconds 必须命中"
            )
        }
        assertNull(
            detector.classify(null, t0.plusNanos(Duration.ofSeconds(300).toNanos() + 1_000_000), true, t0),
            "300.001 秒不属于窗口"
        )
        assertNull(detector.classify(null, t0.plusSeconds(301), true, t0))
    }

    @Test
    fun `negative delta missing sent time and unreliable receive time never match`() {
        assertNull(detector.classify(null, t0.minusSeconds(1), true, t0), "负差（发信晚于来信）不命中")
        assertNull(detector.classify(null, t0.plusSeconds(30), true, null), "无成功发信锚点不命中")
        assertNull(
            detector.classify(null, t0.plusSeconds(30), receivedAtReliable = false, sentAt = t0),
            "缺失真实到达时间时不得用 fallback 时间命中五分钟"
        )
        assertNull(detector.classify(null, null, true, t0))
    }

    // ------------------------------------------------------------------
    // 首封短路（非首封不解析邮件头、不查询发信锚点）
    // ------------------------------------------------------------------

    @Test
    fun `prior processing row short circuits before header and anchor`() {
        rowSets = { sql ->
            when (sql) {
                InboundAutoResponseDetector.LOCK_CONTACT_SQL -> listOf(mapOf("id" to 11L))
                InboundAutoResponseDetector.PRIOR_PROCESSING_SQL -> listOf(mapOf("id" to 7L))
                else -> error("non-first reply must not query $sql")
            }
        }

        detector.lockContact(11L)
        val reason = detector.evaluateFirstReply(11L, "acc", received(autoSubmitted = "auto-replied"))

        assertNull(reason)
        assertEquals(
            listOf(
                InboundAutoResponseDetector.LOCK_CONTACT_SQL,
                InboundAutoResponseDetector.PRIOR_PROCESSING_SQL
            ),
            recordedSql,
            "已有 processing 历史必须立即返回，不读邮件头、不查锚点"
        )
    }

    @Test
    fun `prior inbound mail record short circuits even without processing history`() {
        rowSets = { sql ->
            when (sql) {
                InboundAutoResponseDetector.LOCK_CONTACT_SQL -> listOf(mapOf("id" to 11L))
                InboundAutoResponseDetector.PRIOR_PROCESSING_SQL -> emptyList()
                InboundAutoResponseDetector.PRIOR_INBOUND_RECORD_SQL -> listOf(mapOf("id" to 9L))
                else -> error("non-first reply must not query $sql")
            }
        }

        detector.lockContact(11L)
        assertNull(detector.evaluateFirstReply(11L, "acc", received(autoSubmitted = "auto-replied")))
        assertEquals(3, recordedSql.size, "contact lock + processing + inbound record only")
    }

    @Test
    fun `first reply with header hits without querying the sent anchor`() {
        detector.lockContact(11L)

        val reason = detector.evaluateFirstReply(11L, "acc", received(autoSubmitted = "auto-replied"))

        assertEquals(InboundAutoResponseDetector.AUTO_RESPONSE_HEADER, reason)
        assertTrue(recordedSql.none { it == InboundAutoResponseDetector.LATEST_SENT_OUTBOUND_SQL })
    }

    @Test
    fun `first reply with unreliable receive time only tries the header`() {
        detector.lockContact(11L)

        val reason = detector.evaluateFirstReply(11L, "acc", received(reliable = false))

        assertNull(reason)
        assertTrue(recordedSql.none { it == InboundAutoResponseDetector.LATEST_SENT_OUTBOUND_SQL })
    }

    @Test
    fun `first reply uses nearest sent time for the same expert and logical account`() {
        rowSets = { sql ->
            when (sql) {
                InboundAutoResponseDetector.LOCK_CONTACT_SQL -> listOf(mapOf("id" to 11L))
                InboundAutoResponseDetector.LATEST_SENT_OUTBOUND_SQL ->
                    listOf(mapOf("sent_at" to t0))
                else -> emptyList()
            }
        }
        val received = received(at = t0.plusSeconds(300))

        detector.lockContact(11L)
        val reason = detector.evaluateFirstReply(11L, "logical-acc", received)

        assertEquals(InboundAutoResponseDetector.AUTO_RESPONSE_FAST_5M, reason)
        val params = recordedParams.last()
        assertEquals(11L, params["contactId"])
        assertEquals("logical-acc", params["accountCode"])
        assertEquals(t0.plusSeconds(300), params["receivedAt"])
    }

    @Test
    fun `first reply without a sent anchor does not match`() {
        detector.lockContact(11L)

        assertNull(detector.evaluateFirstReply(11L, "acc", received(at = t0.plusSeconds(60))))
    }

    // ------------------------------------------------------------------
    // 真实调用方事务断言
    // ------------------------------------------------------------------

    @Test
    fun `rule reads require an active caller transaction`() {
        TransactionSynchronizationManager.setActualTransactionActive(false)

        assertThrows(IllegalStateException::class.java) { detector.lockContact(11L) }
        assertThrows(IllegalStateException::class.java) {
            detector.evaluateFirstReply(11L, "acc", received())
        }
        assertTrue(recordedSql.isEmpty(), "无真实事务时绝不执行锁/查询")
    }

    private fun received(
        autoSubmitted: String? = null,
        reliable: Boolean = true,
        at: LocalDateTime = t0.plusSeconds(30)
    ): ReceivedMail = ReceivedMail(
        imapUid = 101,
        from = "expert@example.org",
        subject = "Re: Introduction",
        body = "body",
        messageId = "in-1",
        inReplyTo = "out-1",
        receivedAt = at,
        uidValidity = 1,
        autoSubmitted = autoSubmitted,
        receivedAtReliable = reliable
    )
}
