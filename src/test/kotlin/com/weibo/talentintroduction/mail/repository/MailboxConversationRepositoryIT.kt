package com.weibo.talentintroduction.mail.repository

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Statement
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 专家会话查询真实 MySQL 集成测试（fast-p 07；G-1 门禁，mysqlIt）。
 *
 * 走 mysqlIt 门禁显式启用（-Pmysql-it）连接 127.0.0.1:3306/talent_introduction
 * （测试 application.yml 数据源；Flyway 启动即迁移到最新含 V121），不依赖内存 mock
 * 证明 GROUP BY/分页正确。真实数据覆盖：
 * - I-1：同信只计 processing 一次（INBOUND mail_record 绝不重复计入）；
 * - I-2：mixed 记录口径 + 全历史聚合（日期筛选只限定 membership，不改计数）+
 *   waitingReply（仅 SENT 计发、FAILED 单列）+ pendingOnly（MANUAL_REVIEW 谓词）；
 * - 多标签 membership 无重复行；方向/主题/姓名邮箱 q 筛选；
 * - 同 timestamp 分页稳定（跨页无重复/缺失）；EXPLAIN 计划健全性；
 * - latestInbound 携带真实 processing.id；
 * - I-2 平局证据：同秒跨来源 rank 2 胜出、同来源更大 id 胜出、latestInbound 取真实最大
 *   processing id；账号收窄口径不越界选取其他账号的更新消息；
 * - 无窗口函数源码级回归见同文件 [MailboxConversationRepositorySqlCompatTest]（随
 *   `mvn test` 全量执行，不依赖 mysqlIt 门禁）。
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
@Import(MailboxConversationRepository::class)
class MailboxConversationRepositoryIT {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var repository: MailboxConversationRepository

    private val allAccounts = listOf("acc-a", "acc-b", "acc-c")

    @BeforeEach
    fun setUp() {
        cleanup()
        seedAccount("acc-a")
        seedAccount("acc-b")
        seedAccount("acc-c")
        seedAccount("inactive-acc")
        seedContact(1, "Alice Expert", "alice@example.org", "0000-0000-0000-0001")
        seedContact(2, "Bob Expert", "bob@example.org", "0000-0000-0000-0002")
        seedContact(3, "Carol Expert", "carol@example.org", "0000-0000-0000-0003")
        seedContact(4, "Dan Expert", "dan@example.org", "0000-0000-0000-0004")
    }

    @AfterEach
    fun tearDown() {
        cleanup()
    }

    // ------------------------------------------------------------------
    // 测试
    // ------------------------------------------------------------------

    @Test
    fun `mixed record kinds aggregate by source authority with full-history counts`() {
        // A(1)：2 封 SENT + 无来信 → sent=2 / received=0 / waitingReply 命中
        insertOutbound(1, "acc-a", "INTRODUCTION", "SENT", "Intro A1", "2026-09-01 09:00:00")
        insertOutbound(1, "acc-a", "QA_REPLY", "SENT", "Auto A2", "2026-09-02 09:00:00")
        // B(2)：仅 1 封 FAILED → sent=0 / failed=1，waitingReply 绝不命中
        insertOutbound(2, "acc-a", "INTRODUCTION", "FAILED", "Intro B", "2026-09-01 10:00:00")
        // C(3)：10 天前来信 + 今日发件 → received=1 / sent=1 / waiting=false
        val cInboundId = insertProcessing(3, "acc-a", 101, "PROCESSED", "Re: Intro C",
            "2026-08-29 10:00:00", "msg-c-in", "carol@example.org")
        insertOutbound(3, "acc-a", "MEETING_INVITATION", "SENT", "Invite C", "2026-09-08 10:00:00")
        // D(4)：1 封 MANUAL_REVIEW 待处理来信 → pending=1 / pendingOnly 命中
        val dInboundId = insertProcessing(4, "acc-a", 102, "MANUAL_REVIEW", "Re: Intro D",
            "2026-09-07 10:00:00", "msg-d-in", "dan@example.org")

        // I-1：权威来源 —— processing 同信关联的历史 INBOUND mail_record 绝不重复计数
        insertInboundMailRecord(1, "acc-a", "legacy-inbound-1", "2026-09-03 10:00:00")
        insertInboundMailRecord(1, "acc-a", "legacy-inbound-2", "2026-09-04 10:00:00")

        val rows = page(emptyFilter())

        assertEquals(4, rows.size, "四类专家都在列表中")
        val alice = rows.first { it.expertContactId == 1L }
        assertEquals(0L, alice.receivedCount, "INBOUND mail_record 不进入计数；A 无 processing 来信")
        assertEquals(2L, alice.sentCount)
        assertEquals(0L, alice.failedCount)
        assertEquals(0L, alice.pendingCount)

        val bob = rows.first { it.expertContactId == 2L }
        assertEquals(0L, bob.sentCount, "FAILED 不计为 sent")
        assertEquals(1L, bob.failedCount)

        val carol = rows.first { it.expertContactId == 3L }
        assertEquals(1L, carol.receivedCount)
        assertEquals(1L, carol.sentCount)
        assertEquals(0L, carol.pendingCount)

        val dan = rows.first { it.expertContactId == 4L }
        assertEquals(1L, dan.pendingCount)
        // pendingCount 只数 processing MANUAL_REVIEW（复用现有谓词）
        assertEquals(1L, dan.receivedCount)

        // 真实 processing id 作为 latestInbound（never derived from mail_record）
        val latestInbound = repository.latestInboundByContacts(listOf(1L, 2L, 3L, 4L), allAccounts, null)
        assertEquals(cInboundId, latestInbound[3L]!!.processingId)
        assertEquals(dInboundId, latestInbound[4L]!!.processingId)
        assertNull(latestInbound[1L], "A 无任何 processing 来信 → latestInbound null")
        assertNull(latestInbound[2L])

        // latestMessage：C 最新事件为今日发件（MAIL_RECORD）；A 为较新的 SENT
        val latest = repository.latestMessageByContacts(listOf(1L, 2L, 3L, 4L), allAccounts, null)
        assertEquals(MailboxConversationRepository.SOURCE_MAIL_RECORD, latest[3L]!!.source)
        assertEquals("Invite C", latest[3L]!!.subject)
        assertEquals(MailboxConversationRepository.SOURCE_MAIL_RECORD, latest[1L]!!.source)
    }

    @Test
    fun `date filter only gates membership while aggregation stays full history`() {
        // C：10 天前来信 + 今日发件；7 天窗口内只有发件命中 membership。
        insertProcessing(3, "acc-a", 201, "PROCESSED", "Re: Intro C",
            "2026-08-29 10:00:00", "msg-c-in", "carol@example.org")
        insertOutbound(3, "acc-a", "MEETING_INVITATION", "SENT", "Invite C", "2026-09-08 10:00:00")

        // 默认（无日期筛选）：full-history 口径
        val default = page(emptyFilter())
        assertEquals(1L, default.size.toLong())
        assertEquals(1L, default[0].receivedCount)
        assertEquals(1L, default[0].sentCount)

        // 最近 7 天筛选：C 因今日发件仍是成员，但计数仍是全历史（received=1）
        // → 不因 7 天过滤被误判成"从未回复"（I-2 验收 C 场景）。
        val recent = page(
            MailboxConversationRepository.ConversationFilter(
                accountCodes = allAccounts,
                startTime = LocalDateTime.of(2026, 9, 1, 0, 0),
                endTime = LocalDateTime.of(2026, 9, 9, 0, 0)
            )
        )
        assertEquals(1, recent.size)
        assertEquals(1L, recent[0].receivedCount)
        assertEquals(1L, recent[0].sentCount)

        // 7 天窗口内没有消息的专家不因"账号范围历史存在"而出现
        seedContact(5, "NoRecent Expert", "norecent@example.org", "0000-0000-0000-0005")
        insertOutbound(5, "acc-a", "INTRODUCTION", "SENT", "Intro old", "2026-08-01 10:00:00")
        val recent2 = page(
            MailboxConversationRepository.ConversationFilter(
                accountCodes = allAccounts,
                startTime = LocalDateTime.of(2026, 9, 1, 0, 0),
                endTime = LocalDateTime.of(2026, 9, 9, 0, 0)
            )
        )
        assertTrue(recent2.none { it.expertContactId == 5L }, "窗口外专家不进入 membership")

        // direction=INBOUND 只按来信 membership（历史 10 天来信 → 无日期时不丢）
        val inboundOnly = page(
            MailboxConversationRepository.ConversationFilter(
                accountCodes = allAccounts,
                direction = "INBOUND"
            )
        )
        assertEquals(1, inboundOnly.size)
        assertEquals(3L, inboundOnly[0].expertContactId)
    }

    @Test
    fun `waiting and pending filters use account-range full history`() {
        insertOutbound(1, "acc-a", "INTRODUCTION", "SENT", "A1", "2026-09-01 09:00:00")
        insertOutbound(1, "acc-a", "QA_REPLY", "SENT", "A2", "2026-09-02 09:00:00")
        insertOutbound(2, "acc-a", "INTRODUCTION", "FAILED", "B1", "2026-09-01 10:00:00")
        // C：10 天前有来信（received>0）→ 永不 waiting；同时有今日发件
        insertProcessing(3, "acc-a", 301, "PROCESSED", "Re: Intro C",
            "2026-08-29 10:00:00", "msg-c-in", "carol@example.org")
        insertOutbound(3, "acc-a", "MEETING_INVITATION", "SENT", "Invite C", "2026-09-08 10:00:00")
        // D：1 封 MANUAL_REVIEW
        insertProcessing(4, "acc-a", 302, "MANUAL_REVIEW", "Re: Intro D",
            "2026-09-07 10:00:00", "msg-d-in", "dan@example.org")
        // E(2) 另在 acc-b 有历史 FAILED（8-01）：sent 仍为 0，waiting 判定不受账号数影响
        insertOutbound(2, "acc-b", "INTRODUCTION", "FAILED", "B-old", "2026-08-01 10:00:00")

        val waiting = page(filter(waitingReply = true))
        assertEquals(listOf(1L), waiting.map { it.expertContactId },
            "A(2 SENT 无来信)命中；B/C(FAILED-only 或已收到)不命中")

        val pending = page(filter(pendingOnly = true))
        assertEquals(listOf(4L), pending.map { it.expertContactId })

        // accountCode 范围收窄：B 的 FAILED 在 acc-a；D 的 pending 在 acc-a。
        val pendingAccB = page(filter(pendingOnly = true, accountCode = "acc-b"))
        assertTrue(pendingAccB.isEmpty(), "acc-b 无 pending 行")
    }

    @Test
    fun `multi-label membership never duplicates rows and label filters by source`() {
        // E(5) 两封来信打不同 CUSTOM label
        seedContact(5, "Eve Expert", "eve@example.org", "0000-0000-0000-0005")
        val p1 = insertProcessing(5, "acc-a", 401, "PROCESSED", "Re: E1", "2026-09-01 10:00:00",
            "msg-e1", "eve@example.org")
        val p2 = insertProcessing(5, "acc-a", 402, "PROCESSED", "Re: E2", "2026-09-02 10:00:00",
            "msg-e2", "eve@example.org")
        insertTag(p1, "CUSTOM", "跟进")
        insertTag(p2, "CUSTOM", "材料")
        insertOutbound(5, "acc-a", "QA_REPLY", "SENT", "Auto E", "2026-09-03 10:00:00")

        val labelFollow = page(filter(label = "跟进"))
        assertEquals(1, labelFollow.size, "多标签专家只出现一行（label 经 EXISTS，无重复行）")
        assertEquals(5L, labelFollow[0].expertContactId)
        assertEquals(2L, labelFollow[0].receivedCount, "聚合仍为全历史")

        val labelMaterial = page(filter(label = "材料"))
        assertEquals(1, labelMaterial.size)
        assertEquals(5L, labelMaterial[0].expertContactId)

        val labelMissing = page(filter(label = "不存在"))
        assertEquals(0, labelMissing.size)

        // direction=OUTBOUND + label：outbound 侧无 label 可满足 → 空集
        val impossible = page(filter(label = "跟进", direction = "OUTBOUND"))
        assertTrue(impossible.isEmpty())
    }

    @Test
    fun `q matches real expert name or email`() {
        insertOutbound(1, "acc-a", "INTRODUCTION", "SENT", "Intro Alice", "2026-09-01 09:00:00")
        insertOutbound(2, "acc-a", "INTRODUCTION", "SENT", "Intro Bob", "2026-09-01 09:00:00")

        val byName = page(filter(q = "alice"))
        assertEquals(listOf(1L), byName.map { it.expertContactId })

        val byEmail = page(filter(q = "bob@example.org"))
        assertEquals(listOf(2L), byEmail.map { it.expertContactId })
    }

    @Test
    fun `subject filter restricts membership without changing counts`() {
        insertOutbound(1, "acc-a", "INTRODUCTION", "SENT", "Quantum Physics Intro", "2026-09-01 09:00:00")
        insertOutbound(1, "acc-a", "QA_REPLY", "SENT", "Unrelated reply", "2026-09-02 09:00:00")
        insertOutbound(2, "acc-a", "INTRODUCTION", "SENT", "Materials Science Intro", "2026-09-01 09:00:00")

        val rows = page(filter(subject = "Quantum"))
        assertEquals(listOf(1L), rows.map { it.expertContactId })
        assertEquals(2L, rows[0].sentCount, "subject 只限定 membership，计数仍为全历史 2")
    }

    @Test
    fun `account range narrows full history per account`() {
        insertOutbound(1, "acc-a", "INTRODUCTION", "SENT", "A acc-a", "2026-09-01 09:00:00")
        insertOutbound(1, "acc-b", "INTRODUCTION", "SENT", "A acc-b", "2026-09-02 09:00:00")
        insertProcessing(1, "acc-b", 501, "PROCESSED", "Re: acc-b", "2026-09-03 10:00:00",
            "msg-acc-b", "alice@example.org")

        val all = page(emptyFilter())
        assertEquals(1, all.size)
        assertEquals(2L, all[0].sentCount)
        assertEquals(1L, all[0].receivedCount)
        val codes = repository.accountCodesByContacts(listOf(1L), allAccounts, null)[1L].orEmpty()
        assertEquals(listOf("acc-a", "acc-b"), codes.sorted())

        val onlyA = page(filter(accountCode = "acc-a"))
        assertEquals(1L, onlyA[0].sentCount, "收窄到 acc-a 后 sent 只计该账号全历史")
        assertEquals(0L, onlyA[0].receivedCount)
        val onlyB = page(filter(accountCode = "acc-b"))
        assertEquals(1L, onlyB[0].sentCount)
        assertEquals(1L, onlyB[0].receivedCount)
    }

    @Test
    fun `followed filter and flag come from expert_follow ownership`() {
        insertOutbound(1, "acc-a", "INTRODUCTION", "SENT", "A1", "2026-09-01 09:00:00")
        insertOutbound(2, "acc-a", "INTRODUCTION", "SENT", "B1", "2026-09-01 09:00:00")
        insertOutbound(3, "acc-a", "INTRODUCTION", "SENT", "C1", "2026-09-01 09:00:00")
        jdbcTemplate.update(
            "INSERT INTO expert_follow (username, expert_contact_id, created_at) VALUES (?, ?, ?)",
            "op1", 1L, Timestamp.valueOf(LocalDateTime.of(2026, 9, 1, 10, 0))
        )
        jdbcTemplate.update(
            "INSERT INTO expert_follow (username, expert_contact_id, created_at) VALUES (?, ?, ?)",
            "op2", 2L, Timestamp.valueOf(LocalDateTime.of(2026, 9, 1, 10, 0))
        )

        val followedForOp1 = page(username = "op1", filter = filter(followed = true))
        assertEquals(listOf(1L), followedForOp1.map { it.expertContactId })
        val allForOp1 = page(username = "op1", filter = emptyFilter())
        assertTrue(allForOp1.first { it.expertContactId == 1L }.followed)
        assertFalse(allForOp1.first { it.expertContactId == 2L }.followed, "op2 的关注不属于 op1")
        assertFalse(allForOp1.first { it.expertContactId == 3L }.followed)
    }

    @Test
    fun `same-timestamp pagination is stable across pages without duplicates or gaps`() {
        // 60 位专家，事件时间全部相同（含跨来源同秒）。I-1 后排序只看最近来信：每人补一封
        // 同秒来信使「无来信置底组」为空 → 退化为纯 contactId DESC 破平局，仍测跨页无重复/缺失。
        val sharedTime = "2026-09-05 08:00:00"
        val expectedIds = (5L..64L).toMutableList()
        for (id in 5L..64L) {
            seedContact(id, "Expert $id", "expert$id@example.org", "0000-0000-0000-%04d".format(id))
            insertOutbound(id, if (id % 2L == 0L) "acc-a" else "acc-b", "INTRODUCTION", "SENT",
                "Intro $id", sharedTime)
            insertProcessing(id, "acc-a", 600L + id, "PROCESSED", "Re $id", sharedTime,
                "msg-$id", "expert$id@example.org")
        }
        // 与 1..4 号相比时间较新，确保排序正确性独立于 setUp 数据
        expectedIds.addAll(listOf(1L, 2L, 3L, 4L))
        insertOutbound(1, "acc-a", "INTRODUCTION", "SENT", "A1", sharedTime)
        insertProcessing(1, "acc-a", 701, "PROCESSED", "A-in", sharedTime, "msg-a", "alice@example.org")
        insertOutbound(2, "acc-a", "INTRODUCTION", "FAILED", "B1", sharedTime)
        insertProcessing(2, "acc-a", 702, "PROCESSED", "B-in", sharedTime, "msg-b", "bob@example.org")
        insertProcessing(3, "acc-a", 700, "PROCESSED", "C-in", sharedTime, "msg-c", "carol@example.org")
        insertOutbound(4, "acc-a", "INTRODUCTION", "SENT", "D1", sharedTime)
        insertProcessing(4, "acc-a", 704, "PROCESSED", "D-in", sharedTime, "msg-d", "dan@example.org")

        val size = 17
        var offset = 0L
        val collected = mutableListOf<Long>()
        var guard = 0
        while (guard++ < 10) {
            val rows = page(emptyFilter(), size = size, offset = offset)
            if (rows.isEmpty()) break
            collected += rows.map { it.expertContactId }
            // 跨页顺序 = latest_event_at DESC, contactId DESC（时间全同 → 纯 contactId DESC）
            val ids = rows.map { it.expertContactId }
            assertEquals(ids.sortedDescending(), ids, "页内顺序必须稳定 DESC")
            if (rows.size < size) break
            offset += size
        }
        assertEquals(64, collected.size, "60+4 位专家全部出现")
        assertEquals(collected.sortedDescending(), collected, "同 timestamp 全局顺序按 contactId DESC")
        assertEquals(expectedIds.sortedDescending(), collected, "无重复、无缺失")
    }

    @Test
    fun `timeline keyset is stable under equal timestamps across both sources`() {
        // E 专家：5 条消息同一时刻（跨 MAIL_RECORD / INBOUND_PROCESSING）
        seedContact(5, "Eve Expert", "eve@example.org", "0000-0000-0000-0005")
        val sharedTime = "2026-09-06 12:00:00"
        val sharedInstant = LocalDateTime.of(2026, 9, 6, 12, 0, 0)
        insertOutbound(5, "acc-a", "INTRODUCTION", "SENT", "S1", sharedTime)
        insertOutbound(5, "acc-a", "QA_REPLY", "SENT", "S2", sharedTime)
        insertOutbound(5, "acc-a", "QA_REPLY", "FAILED", "S3", sharedTime)
        insertProcessing(5, "acc-a", 801, "PROCESSED", "R1", sharedTime, "m-r1", "eve@example.org")
        insertProcessing(5, "acc-a", 802, "MANUAL_REVIEW", "R2", sharedTime, "m-r2", "eve@example.org")

        val filter = MailboxConversationRepository.ConversationFilter(accountCodes = allAccounts)
        val page1 = repository.timelineMessages(5L, filter, before = null, limit = 2)
        assertTrue(page1.hasMore)
        assertEquals(2, page1.rows.size)
        assertEquals(page1.rows.map { it.eventAt }, page1.rows.map { sharedInstant })

        val oldest1 = page1.rows.last()
        val keyset = MailboxConversationRepository.ConversationKeyset(
            beforeTime = oldest1.eventAt,
            beforeSourceRank = sourceRank(oldest1.source),
            beforeId = oldest1.id
        )
        val page2 = repository.timelineMessages(5L, filter, before = keyset, limit = 2)
        assertTrue(page2.hasMore)
        val page3 = repository.timelineMessages(5L, filter,
            before = MailboxConversationRepository.ConversationKeyset(
                beforeTime = page2.rows.last().eventAt,
                beforeSourceRank = sourceRank(page2.rows.last().source),
                beforeId = page2.rows.last().id
            ), limit = 2)
        assertFalse(page3.hasMore)

        val seen = (page1.rows + page2.rows + page3.rows).map { it.source to it.id }
        assertEquals(5, seen.size, "同 timestamp 下 keyset 分页无重复/缺失")
        assertEquals(seen.size, seen.toSet().size)
        // 降序键必须严格单调：(event_at DESC, source_rank DESC, id DESC)
        val keys = (page1.rows + page2.rows + page3.rows).map {
            Triple(it.eventAt, sourceRank(it.source), it.id)
        }
        assertEquals(keys.sortedWith(compareByDescending<Triple<LocalDateTime, Int, Long>> { it.first }
            .thenByDescending { it.second }.thenByDescending { it.third }), keys)
    }

    @Test
    fun `outbound-only experts rank last by stable id regardless of send time`() {
        // I-1/X2：排序只看最近来信。1 有 08-01 旧发件 + 09-01 来信（非空组，置前）；2 只有
        // 更新的 09-08 发件、3 只有 09-05 FAILED 发件 → 都进「无来信置底组」，按 contactId DESC。
        insertOutbound(1, "acc-a", "INTRODUCTION", "SENT", "old", "2026-08-01 09:00:00")
        insertProcessing(1, "acc-a", 750, "PROCESSED", "Re old", "2026-09-01 09:00:00",
            "out-only-1", "alice@example.org")
        insertOutbound(2, "acc-a", "INTRODUCTION", "SENT", "new", "2026-09-08 09:00:00")
        insertOutbound(3, "acc-a", "INTRODUCTION", "FAILED", "mid", "2026-09-05 09:00:00")

        val rows = page(emptyFilter())
        assertEquals(listOf(1L, 3L, 2L), rows.map { it.expertContactId },
            "有来信者在前；仅发件者（即使发件更新/FAILED）不参与排序，置底按 contactId DESC")
        assertEquals(1L, rows[0].sentCount)
        assertEquals(1L, rows[1].failedCount)
        assertEquals(1L, rows[2].sentCount)
    }

    @Test
    fun `explain on summary page query is sane`() {
        for (id in 5L..40L) {
            seedContact(id, "Expert $id", "expert$id@example.org", "0000-0000-0000-%04d".format(id))
            insertOutbound(id, "acc-a", "INTRODUCTION", "SENT", "Intro $id", "2026-09-%02d 09:00:00".format(1 + (id % 27L)))
        }
        val plan = repository.explainConversationsPage(
            username = "op1",
            filter = filter(accountCode = "acc-a"),
            size = 20,
            offset = 0
        )
        assertTrue(plan.isNotEmpty(), "EXPLAIN 必须产出计划行")
        // 计划健全性：主查询存在、无语法/命名参数错误；账号列过滤落在可检索路径上。
        // 具体访问方式随数据分布变化，这里只做结构性 sanity（真实数据量下计划可执行）。
        val tableNames = plan.mapNotNull { it["table"] as? String }
        assertTrue(tableNames.isNotEmpty())
        val accessTypes = plan.mapNotNull { it["type"] as? String }
        assertTrue(accessTypes.none { it == "ALL" && tableNames[accessTypes.indexOf(it)] in listOf("expert_contact") },
            "expert_contact 不应全表扫描：${plan.take(4)}")
        // 重复执行结果一致（稳定性）
        val plan2 = repository.explainConversationsPage("op1", filter(accountCode = "acc-a"), 20, 0)
        assertEquals(plan.size, plan2.size)
    }

    @Test
    fun `latest groupwise max breaks ties by source rank then id and stays inbound-scoped`() {
        // I-2：同一专家同一秒存在 OUTBOUND（rank 1）与两封同来源来信（rank 2）。
        val shared = "2026-09-08 12:00:00"
        insertOutbound(1, "acc-a", "INTRODUCTION", "SENT", "Outbound T", shared)
        insertProcessing(1, "acc-a", 900, "PROCESSED", "Re low", shared, "msg-tie-low", "alice@example.org")
        val inboundHigh = insertProcessing(1, "acc-a", 901, "PROCESSED", "Re high", shared,
            "msg-tie-high", "alice@example.org")

        // 同秒跨来源平局：rank 2（INBOUND_PROCESSING）胜出；同来源同秒：更大 processing id 胜出。
        val latest = repository.latestMessageByContacts(listOf(1L), allAccounts, null)
        assertEquals(MailboxConversationRepository.SOURCE_INBOUND_PROCESSING, latest[1L]!!.source)
        assertEquals(inboundHigh, latest[1L]!!.id)
        assertEquals("Re high", latest[1L]!!.subject)

        // latestInbound 指向同秒下真实的最大 processing id（绝不取自 mail_record）。
        val inbound = repository.latestInboundByContacts(listOf(1L), allAccounts, null)
        assertEquals(inboundHigh, inbound[1L]!!.processingId)

        // 更晚的 OUTBOUND 只改 latestMessage；latestInbound 候选恒为 processing，不受其扰动。
        insertOutbound(1, "acc-a", "QA_REPLY", "SENT", "Later outbound", "2026-09-08 13:00:00")
        val later = repository.latestMessageByContacts(listOf(1L), allAccounts, null)
        assertEquals(MailboxConversationRepository.SOURCE_MAIL_RECORD, later[1L]!!.source)
        assertEquals("Later outbound", later[1L]!!.subject)
        val inboundAfter = repository.latestInboundByContacts(listOf(1L), allAccounts, null)
        assertEquals(inboundHigh, inboundAfter[1L]!!.processingId)

        // I-4：空 contactIds 短路仍返回空（不执行 IN () 查询）。
        assertTrue(repository.latestMessageByContacts(emptyList(), allAccounts, null).isEmpty())
        assertTrue(repository.latestInboundByContacts(emptyList(), allAccounts, null).isEmpty())
    }

    @Test
    fun `latest respects accountCode scope despite newer rows on other accounts`() {
        insertOutbound(1, "acc-a", "INTRODUCTION", "SENT", "A acc-a", "2026-09-01 09:00:00")
        insertOutbound(1, "acc-a", "QA_REPLY", "SENT", "A2 acc-a", "2026-09-02 09:00:00")
        // 专家在 acc-b 存在更晚的来信：全账号口径胜出，但 acc-a 收窄口径不得越界选取。
        insertProcessing(1, "acc-b", 951, "PROCESSED", "Re acc-b", "2026-09-08 10:00:00",
            "msg-acc-b-new", "alice@example.org")

        val narrowed = repository.latestMessageByContacts(listOf(1L), allAccounts, "acc-a")
        assertEquals(MailboxConversationRepository.SOURCE_MAIL_RECORD, narrowed[1L]!!.source)
        assertEquals("A2 acc-a", narrowed[1L]!!.subject, "acc-a 口径不得选中 acc-b 的更新消息")
        val narrowedInbound = repository.latestInboundByContacts(listOf(1L), allAccounts, "acc-a")
        assertNull(narrowedInbound[1L], "acc-a 无 processing 来信 → latestInbound 为空（不越界）")

        val all = repository.latestMessageByContacts(listOf(1L), allAccounts, null)
        assertEquals(MailboxConversationRepository.SOURCE_INBOUND_PROCESSING, all[1L]!!.source)
        assertEquals("Re acc-b", all[1L]!!.subject)
        val allInbound = repository.latestInboundByContacts(listOf(1L), allAccounts, null)
        assertEquals("msg-acc-b-new", allInbound[1L]!!.messageId)
    }

    @Test
    fun `i1 ordering matrix across pending processed and outbound-only experts`() {
        // A(1)=09-01 待处理；B(2)=09-09 已处理；C(3)=仅 09-10 发件（无来信）；D(4)=09-08 待处理。
        insertProcessing(1, "acc-a", 901, "MANUAL_REVIEW", "A pending", "2026-09-01 09:00:00",
            "i1-msg-a", "alice@example.org")
        insertProcessing(2, "acc-a", 902, "PROCESSED", "B done", "2026-09-09 09:00:00",
            "i1-msg-b", "bob@example.org")
        insertOutbound(3, "acc-a", "INTRODUCTION", "SENT", "C outbound", "2026-09-10 09:00:00")
        val dProcessingId = insertProcessing(4, "acc-a", 904, "MANUAL_REVIEW", "D pending",
            "2026-09-08 09:00:00", "i1-msg-d", "dan@example.org")
        listOf(1L, 2L, 3L, 4L).forEach { id ->
            jdbcTemplate.update(
                "INSERT INTO expert_follow (username, expert_contact_id, created_at) VALUES (?, ?, ?)",
                "op1", id, Timestamp.valueOf(LocalDateTime.of(2026, 9, 1, 10, 0))
            )
        }

        // 全部：待处理组(D,A)在前、组内来信倒序；B 次之；无来信 C（即使发件最新）置底。
        assertEquals(4L, repository.countConversations("op1", emptyFilter()))
        assertEquals(listOf(4L, 1L, 2L, 3L), page(emptyFilter(), size = 100).map { it.expertContactId })
        assertEquals(listOf(4L, 1L), page(emptyFilter(), size = 2, offset = 0).map { it.expertContactId },
            "第 1 页 = D,A")
        assertEquals(listOf(2L, 3L), page(emptyFilter(), size = 2, offset = 2).map { it.expertContactId },
            "第 2 页 = B,C；有待处理的第 2 页专家不被第 1 页普通专家压住")

        // 关注：不加待处理优先，纯来信倒序 + NULL(无来信)置底 → B,D,A,C
        val followed = page(username = "op1", filter = filter(followed = true))
        assertEquals(listOf(2L, 4L, 1L, 3L), followed.map { it.expertContactId },
            "关注 B,D,A,C（C 无来信恒最后，即使其发件最新）")

        // 待处理：只含 pending 专家、来信倒序 → D,A
        val pending = page(username = "op1", filter = filter(pendingOnly = true))
        assertEquals(listOf(4L, 1L), pending.map { it.expertContactId })
        assertTrue(pending.all { it.pendingCount > 0L })

        // 把 D 的待处理信标为已处理后再看全部：只剩 A 待处理 → A,B,D,C
        jdbcTemplate.update("UPDATE inbound_mail_processing SET process_status = 'PROCESSED' WHERE id = ?",
            dProcessingId)
        val afterProcess = page(emptyFilter(), size = 100)
        assertEquals(listOf(1L, 2L, 4L, 3L), afterProcess.map { it.expertContactId },
            "D 处理后全部 = A,B,D,C")
        assertEquals(listOf(1L, 2L), page(emptyFilter(), size = 2, offset = 0).map { it.expertContactId })
        assertEquals(listOf(4L, 3L), page(emptyFilter(), size = 2, offset = 2).map { it.expertContactId })

        // X2：给 C 再发一封更晚的发件 → latestMessage 更新，但专家排序不提升（C 仍最后）。
        insertOutbound(3, "acc-a", "QA_REPLY", "SENT", "C outbound later", "2026-09-11 09:00:00")
        val afterNewOutbound = page(emptyFilter(), size = 100)
        assertEquals(listOf(1L, 2L, 4L, 3L), afterNewOutbound.map { it.expertContactId },
            "新发件只影响 latestMessage，不提升无来信专家的排序")
        val latest = repository.latestMessageByContacts(listOf(3L), allAccounts, null)
        assertEquals("C outbound later", latest[3L]!!.subject)
        assertEquals(MailboxConversationRepository.SOURCE_MAIL_RECORD, latest[3L]!!.source)
    }

    @Test
    fun `page and count are empty when no mail exists in the account range`() {
        assertEquals(0L, repository.countConversations("", emptyFilter()))
        assertTrue(page(emptyFilter()).isEmpty())
        assertTrue(page(emptyFilter(), size = 2, offset = 0).isEmpty())
    }

    @Test
    fun `keyword matches subject or body inside one message and never leaks into summary`() {
        seedContact(5, "Eve Expert", "eve@example.org", "0000-0000-0000-0005")
        seedContact(6, "Fay Expert", "fay@example.org", "0000-0000-0000-0006")
        // 专家 5：两封来信——P1 正文含 meeting-z9（主题/clean 不含），P2 无关键词。
        insertProcessing(5, "acc-a", 1001, "PROCESSED", "Weekly sync", "2026-09-02 09:00:00",
            "kw-p1", "eve@example.org", body = "discuss meeting-z9 agenda", cleanedBody = "cleaned-p1")
        insertProcessing(5, "acc-a", 1002, "PROCESSED", "No keyword here", "2026-09-04 09:00:00",
            "kw-p2", "eve@example.org", body = "plain body", cleanedBody = "plain cleaned")
        // 专家 6：主题含关键词（正文不含）。
        insertProcessing(6, "acc-a", 1003, "PROCESSED", "meeting-z9 agenda", "2026-09-03 09:00:00",
            "kw-p3", "fay@example.org", body = "no keyword inside", cleanedBody = "cleaned-p3")

        val byBody = page(filter(keyword = "meeting-z9"))
        assertEquals(listOf(5L, 6L), byBody.map { it.expertContactId },
            "正文-only 命中 5、主题命中 6；来信倒序稳定（5 最新来信 09-04 > 6 的 09-03）")
        assertEquals(2L, byBody.first { it.expertContactId == 5L }.receivedCount,
            "keyword 只限定 membership，聚合仍是全历史 2 封")
        assertEquals(1L, byBody.first { it.expertContactId == 6L }.receivedCount)

        // 旧 subject 参数继续只搜主题：5 的正文-only 关键词不命中，6 命中。
        val bySubject = page(filter(subject = "meeting-z9"))
        assertEquals(listOf(6L), bySubject.map { it.expertContactId })

        // count 与 page 口径一致
        assertEquals(2L, repository.countConversations("", filter(keyword = "meeting-z9")))
    }

    @Test
    fun `recipient email matches outbound via contact email and inbound via from alias separately`() {
        seedContact(5, "Eve Expert", "eve@example.org", "0000-0000-0000-0005")
        seedContact(6, "Fay Expert", "fay@example.org", "0000-0000-0000-0006")
        insertOutbound(5, "acc-a", "INTRODUCTION", "SENT", "To Eve", "2026-09-02 09:00:00")
        insertOutbound(6, "acc-a", "INTRODUCTION", "SENT", "To Fay", "2026-09-02 09:00:00")
        // Fay 的来信 from_email 是别名，与 expert_email 不同（证明走 inbound 侧匹配）。
        insertProcessing(6, "acc-a", 1101, "PROCESSED", "Re: intro", "2026-09-05 09:00:00",
            "rec-alias", "alias-fay@elsewhere.org")

        val byContactEmail = page(filter(recipientEmail = "fay@example.org"))
        assertEquals(listOf(6L), byContactEmail.map { it.expertContactId },
            "专家邮箱命中 Fay 的出站 EXISTS")
        val byAlias = page(filter(recipientEmail = "alias-fay@elsewhere.org"))
        assertEquals(listOf(6L), byAlias.map { it.expertContactId },
            "别名命中 Fay 的入站 from_email EXISTS（不得要求同专家邮箱）")
        val byEve = page(filter(recipientEmail = "eve@example.org"))
        assertEquals(listOf(5L), byEve.map { it.expertContactId })

        val miss = page(filter(recipientEmail = "nobody@example.org"))
        assertTrue(miss.isEmpty())
    }

    @Test
    fun `one message must satisfy every message level condition across directions`() {
        seedContact(7, "Grace Expert", "grace@example.org", "0000-0000-0000-0007")
        // G(7)：出站主题 Quantum（09-01），入站主题 Other（09-08）——主题与日期分属两封。
        insertOutbound(7, "acc-a", "INTRODUCTION", "SENT", "Quantum", "2026-09-01 09:00:00")
        insertProcessing(7, "acc-a", 1201, "PROCESSED", "Other", "2026-09-08 09:00:00",
            "mix-g", "grace@example.org")

        // 主题匹配出站、日期窗口只覆盖入站：同一封消息不成立 → 不联合凑出 G。
        val mixed = page(
            filter(
                startTime = LocalDateTime.of(2026, 9, 5, 0, 0),
                endTime = LocalDateTime.of(2026, 9, 9, 0, 0),
                subject = "Quantum"
            )
        )
        assertTrue(mixed.isEmpty(), "方向/主题/日期不得跨消息拼凑出专家")

        // 单独日期窗口命中入站（Other 09-08）；单独主题命中出站。
        assertEquals(listOf(7L),
            page(filter(startTime = LocalDateTime.of(2026, 9, 5, 0, 0), endTime = LocalDateTime.of(2026, 9, 9, 0, 0)))
                .map { it.expertContactId })
        assertEquals(listOf(7L), page(filter(subject = "Quantum")).map { it.expertContactId })
    }

    @Test
    fun `q and followed can never be bypassed by the other direction EXISTS`() {
        // Alice(1)：出站 Quantum。Bob(2)：出站 Other + 一封任意入站（旧括号缺陷下会经
        // 入站 EXISTS 绕过 q/followed）。
        insertOutbound(1, "acc-a", "INTRODUCTION", "SENT", "Quantum", "2026-09-01 09:00:00")
        insertOutbound(2, "acc-a", "INTRODUCTION", "SENT", "Other", "2026-09-01 09:00:00")
        insertProcessing(2, "acc-a", 1301, "PROCESSED", "Anything", "2026-09-08 09:00:00",
            "bypass-bob", "bob@example.org")
        jdbcTemplate.update(
            "INSERT INTO expert_follow (username, expert_contact_id, created_at) VALUES (?, ?, ?)",
            "op1", 1L, Timestamp.valueOf(LocalDateTime.of(2026, 9, 1, 10, 0))
        )

        // q=alice + subject=Quantum：Bob 姓/邮箱不匹配 q，入站 EXISTS 不得把他带进来。
        val qFiltered = page(username = "op1", filter = filter(q = "alice", subject = "Quantum"))
        assertEquals(listOf(1L), qFiltered.map { it.expertContactId })

        // followed=true + subject=Quantum：Bob 未关注，入站 EXISTS 不得绕过关注。
        val followedFiltered = page(username = "op1", filter = filter(followed = true, subject = "Quantum"))
        assertEquals(listOf(1L), followedFiltered.map { it.expertContactId })
        assertEquals(1L, repository.countConversations("op1", filter(followed = true, subject = "Quantum")))
    }

    @Test
    fun `keyword and recipient filters keep pending count and latest inbound at full range`() {
        seedContact(8, "Helen Expert", "helen@example.org", "0000-0000-0000-0008")
        // H(8)：一封 MANUAL_REVIEW（pending）+ 一封 PROCESSED（正文含 unique-term）。
        insertProcessing(8, "acc-a", 1401, "MANUAL_REVIEW", "Pending topic", "2026-09-01 09:00:00",
            "full-h", "h@example.org")
        insertProcessing(8, "acc-a", 1402, "PROCESSED", "Processed topic", "2026-09-06 09:00:00",
            "full-h2", "h@example.org", body = "contains alpha-marker-77", cleanedBody = "cleaned-h2")

        val rows = page(filter(keyword = "alpha-marker-77"))
        assertEquals(listOf(8L), rows.map { it.expertContactId })
        assertEquals(2L, rows[0].receivedCount, "筛选后聚合仍为全账号范围")
        assertEquals(1L, rows[0].pendingCount, "pending 由另一封 MANUAL_REVIEW 统计，不随关键词 membership 缩水")
        assertEquals(1L, repository.countConversations("", filter(keyword = "alpha-marker-77")))

        val latestInbound = repository.latestInboundByContacts(listOf(8L), allAccounts, null)
        assertEquals("full-h2", latestInbound[8L]!!.messageId, "最近来信仍取全账号范围最新一封")
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private fun emptyFilter() = MailboxConversationRepository.ConversationFilter(accountCodes = allAccounts)

    private fun filter(
        q: String? = null,
        followed: Boolean = false,
        waitingReply: Boolean = false,
        pendingOnly: Boolean = false,
        accountCode: String? = null,
        direction: String? = null,
        subject: String? = null,
        label: String? = null,
        startTime: LocalDateTime? = null,
        endTime: LocalDateTime? = null,
        recipientEmail: String? = null,
        keyword: String? = null
    ) = MailboxConversationRepository.ConversationFilter(
        accountCodes = allAccounts,
        accountCode = accountCode,
        q = q,
        followed = followed,
        waitingReply = waitingReply,
        pendingOnly = pendingOnly,
        direction = direction,
        subject = subject,
        label = label,
        startTime = startTime,
        endTime = endTime,
        recipientEmail = recipientEmail,
        keyword = keyword
    )

    private fun page(
        filter: MailboxConversationRepository.ConversationFilter,
        size: Int = 100,
        offset: Long = 0,
        username: String = ""
    ): List<MailboxConversationRepository.ConversationSummarySqlRow> =
        repository.pageConversations(username, filter, size, offset)

    private fun sourceRank(source: String): Int = when (source) {
        "MAIL_RECORD" -> 1
        "INBOUND_PROCESSING" -> 2
        else -> error("unexpected source $source")
    }

    private fun cleanup() {
        jdbcTemplate.update("DELETE FROM expert_follow")
        jdbcTemplate.update("DELETE FROM inbound_mail_tag")
        jdbcTemplate.update("DELETE FROM mail_attachment_transfer")
        jdbcTemplate.update("DELETE FROM expert_document")
        jdbcTemplate.update("DELETE FROM mail_attachment")
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

    private fun seedContact(id: Long, name: String, email: String, orcid: String) {
        jdbcTemplate.update("INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id) " +
            "VALUES (?, ?, 'Fixture', (SELECT id FROM mail_sender_account WHERE account_code = 'acc-a'))",
            id, "FIXTURE-$id")
        jdbcTemplate.update(
            """
            INSERT INTO expert_contact (id, campaign_id, orcid_id, expert_email, expert_name, current_status)
            VALUES (?, ?, ?, ?, ?, 'NEW')
            """.trimIndent(),
            id, id, orcid, email, name
        )
    }

    private fun insertOutbound(
        contactId: Long,
        accountCode: String,
        mailType: String,
        sendStatus: String,
        subject: String,
        eventAt: String
    ): Long {
        val (sentAt, createdAt) = if (sendStatus == "SENT") {
            eventAt to eventAt
        } else {
            null to eventAt // FAILED 行 sent_at 恒 NULL；事件时间取 created_at
        }
        return insertAndGetKey(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, triggered_by,
                 message_id, subject, body, send_status, sent_at, created_at)
            VALUES (?, 'OUTBOUND', ?, ?, 'SYSTEM', ?, ?, 'body', ?, ?, ?)
            """.trimIndent(),
            contactId, mailType, accountCode, "out-${subject.hashCode()}", subject, sendStatus,
            sentAt?.let { Timestamp.valueOf(LocalDateTime.parse(it.replace(' ', 'T'))) },
            Timestamp.valueOf(LocalDateTime.parse(createdAt.replace(' ', 'T')))
        )
    }

    private fun insertInboundMailRecord(
        contactId: Long,
        accountCode: String,
        messageId: String,
        receivedAt: String
    ): Long = insertAndGetKey(
        """
        INSERT INTO mail_record
            (expert_contact_id, direction, mail_type, sender_account_code, message_id,
             subject, body, send_status, received_at, created_at)
        VALUES (?, 'INBOUND', 'REPLY', ?, ?, 'legacy', 'body', 'SENT', ?, ?)
        """.trimIndent(),
        contactId, accountCode, messageId,
        Timestamp.valueOf(LocalDateTime.parse(receivedAt.replace(' ', 'T'))),
        Timestamp.valueOf(LocalDateTime.parse(receivedAt.replace(' ', 'T')))
    )

    private fun insertProcessing(
        contactId: Long,
        accountCode: String,
        imapUid: Long,
        processStatus: String,
        subject: String,
        receivedAt: String,
        messageId: String,
        fromEmail: String,
        body: String = "body",
        cleanedBody: String = "cleaned"
    ): Long = insertAndGetKey(
        """
        INSERT INTO inbound_mail_processing
            (sender_account_code, uid_validity, imap_uid, message_id, from_email, subject,
             body, cleaned_body, received_at, process_status, process_reason, expert_contact_id)
        VALUES (?, 1, ?, ?, ?, ?, ?, ?, ?, ?, 'QA_AUTO_REPLIED', ?)
        """.trimIndent(),
        accountCode, imapUid, messageId, fromEmail, subject, body, cleanedBody,
        Timestamp.valueOf(LocalDateTime.parse(receivedAt.replace(' ', 'T'))),
        processStatus, contactId
    )

    private fun insertTag(processingId: Long, tagType: String, label: String) {
        jdbcTemplate.update(
            """
            INSERT INTO inbound_mail_tag (inbound_processing_id, tag_type, label, source, created_at)
            VALUES (?, ?, ?, 'MANUAL', ?)
            """.trimIndent(),
            processingId, tagType, label,
            Timestamp.valueOf(LocalDateTime.of(2026, 9, 1, 10, 0))
        )
    }

    private fun insertAndGetKey(sql: String, vararg args: Any?): Long {
        val keyHolder = org.springframework.jdbc.support.GeneratedKeyHolder()
        jdbcTemplate.update(
            { connection ->
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).apply {
                    args.forEachIndexed { index, arg ->
                        when (arg) {
                            is Long -> setLong(index + 1, arg)
                            is Int -> setInt(index + 1, arg)
                            is String -> setString(index + 1, arg)
                            is Timestamp -> setTimestamp(index + 1, arg)
                            null -> setNull(index + 1, java.sql.Types.NULL)
                            else -> setObject(index + 1, arg)
                        }
                    }
                }
            },
            keyHolder
        )
        return keyHolder.key!!.toLong()
    }
}

/**
 * 源码级兼容回归（无 DB、不依赖 mysqlIt 门禁）：两条 latest 查询不得再生成
 * `ROW_NUMBER` / `OVER` 窗口函数——线上旧 MySQL（5.7/旧 MariaDB）解析
 * `OVER (PARTITION BY ...)` 报 1064，收发信箱刷新整体失败。
 *
 * 只断言 SQL 形态，不断言语义；groupwise-max 的平局/范围语义由
 * [MailboxConversationRepositoryIT] 在真实 MySQL（mysqlIt）上证明。
 *
 * fast-p 01（I-1）追加源码级断言：page/explain 的排序必须走同一私有
 * `orderByClause(filter)` 片段（模板字面量），不再出现旧 `ORDER BY latest_event_at DESC`
 * 别名排序——防止排序实现只改 page 忘改 explain，或退回按发件时间排。
 */
class MailboxConversationRepositorySqlCompatTest {

    private val sourceFile = Path.of(
        "src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt"
    )

    @Test
    fun `latest queries must not use ROW_NUMBER or OVER window functions`() {
        assertTrue(Files.exists(sourceFile),
            "找不到被测源码（cwd=${Path.of("").toAbsolutePath()}）：${sourceFile.toAbsolutePath()}")
        val source = Files.readString(sourceFile)
        for (method in listOf("latestMessageByContacts(", "latestInboundByContacts(")) {
            val body = methodBody(source, "fun $method")
            assertFalse(body.contains("ROW_NUMBER"), "$method 不得使用 ROW_NUMBER")
            assertFalse(Regex("\\bOVER\\s*\\(").containsMatchIn(body), "$method 不得使用 OVER 窗口子句")
        }
    }

    @Test
    fun `summary page and explain share the private I-1 order helper`() {
        assertTrue(Files.exists(sourceFile),
            "找不到被测源码（cwd=${Path.of("").toAbsolutePath()}）：${sourceFile.toAbsolutePath()}")
        val source = Files.readString(sourceFile)

        val pageBody = methodBody(source, "fun pageConversations(")
        val explainBody = methodBody(source, "fun explainConversationsPage(")
        for (body in listOf(pageBody, explainBody)) {
            assertTrue(body.contains("ORDER BY \${orderByClause(filter)}"),
                "page/explain 必须共用 orderByClause(filter) 排序片段")
            assertFalse(body.contains("ORDER BY latest_event_at DESC"),
                "不得再按旧 MAX(event) 别名（latest_event_at）排序")
            assertFalse(body.contains("ORDER BY latest_event_at,"),
                "不得再按旧 MAX(event) 别名（latest_event_at）排序")
        }

        // 排序核心：来信时间投影 + pending 首项 + NULL 置底 + contactId DESC 稳定（5.7 无窗口函数）。
        val helperBody = methodBody(source, "private fun orderByClause(")
        assertTrue(helperBody.contains("MAX(CASE WHEN u.source = 'INBOUND_PROCESSING' THEN u.event_at END)"),
            "orderByClause 必须以 INBOUND_PROCESSING 来信时间为排序键")
        assertTrue(helperBody.contains("SUM(u.pending_flag) > 0 THEN 0 ELSE 1 END ASC"),
            "全部视图必须保留待处理优先首项")
        assertTrue(helperBody.contains("u.expert_contact_id DESC"), "必须保留 contactId DESC 稳定键")
        assertFalse(Regex("\\bOVER\\s*\\(").containsMatchIn(helperBody), "排序片段不得使用 OVER 窗口子句")
    }

    private fun methodBody(source: String, funSignature: String): String {
        val start = source.indexOf(funSignature)
        check(start >= 0) { "源码中找不到 $funSignature" }
        val nextMethod = source.indexOf("\n    fun ", start)
        return source.substring(start, if (nextMethod >= 0) nextMethod else source.length)
    }
}
