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
        // 60 位专家，事件时间全部相同（含跨来源同秒），contactId DESC 破平局。
        val sharedTime = "2026-09-05 08:00:00"
        val sharedInstant = LocalDateTime.of(2026, 9, 5, 8, 0, 0)
        val expectedIds = (5L..64L).toMutableList()
        for (id in 5L..64L) {
            seedContact(id, "Expert $id", "expert$id@example.org", "0000-0000-0000-%04d".format(id))
            insertOutbound(id, if (id % 2L == 0L) "acc-a" else "acc-b", "INTRODUCTION", "SENT",
                "Intro $id", sharedTime)
            if (id % 5L == 0L) {
                insertProcessing(id, "acc-a", 600L + id, "PROCESSED", "Re $id", sharedTime,
                    "msg-$id", "expert$id@example.org")
            }
        }
        // 与 1..4 号相比时间较新，确保排序正确性独立于 setUp 数据
        expectedIds.addAll(listOf(1L, 2L, 3L, 4L))
        insertOutbound(1, "acc-a", "INTRODUCTION", "SENT", "A1", sharedTime)
        insertOutbound(2, "acc-a", "INTRODUCTION", "FAILED", "B1", sharedTime)
        insertProcessing(3, "acc-a", 700, "PROCESSED", "C-in", sharedTime, "msg-c", "carol@example.org")
        insertOutbound(4, "acc-a", "INTRODUCTION", "SENT", "D1", sharedTime)

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
    fun `expert with only outbound keeps latestEvent ordering by send time`() {
        insertOutbound(1, "acc-a", "INTRODUCTION", "SENT", "old", "2026-08-01 09:00:00")
        insertOutbound(2, "acc-a", "INTRODUCTION", "SENT", "new", "2026-09-08 09:00:00")
        insertOutbound(3, "acc-a", "INTRODUCTION", "FAILED", "mid", "2026-09-05 09:00:00")

        val rows = page(emptyFilter())
        assertEquals(listOf(2L, 3L, 1L), rows.map { it.expertContactId },
            "排序按最新事件倒序（FAILED 的 created_at 兜底为事件时间）")
        assertEquals(1L, rows[0].sentCount)
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
        label: String? = null
    ) = MailboxConversationRepository.ConversationFilter(
        accountCodes = allAccounts,
        accountCode = accountCode,
        q = q,
        followed = followed,
        waitingReply = waitingReply,
        pendingOnly = pendingOnly,
        direction = direction,
        subject = subject,
        label = label
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
        fromEmail: String
    ): Long = insertAndGetKey(
        """
        INSERT INTO inbound_mail_processing
            (sender_account_code, uid_validity, imap_uid, message_id, from_email, subject,
             body, cleaned_body, received_at, process_status, process_reason, expert_contact_id)
        VALUES (?, 1, ?, ?, ?, ?, 'body', 'cleaned', ?, ?, 'QA_AUTO_REPLIED', ?)
        """.trimIndent(),
        accountCode, imapUid, messageId, fromEmail, subject,
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

    private fun methodBody(source: String, funSignature: String): String {
        val start = source.indexOf(funSignature)
        check(start >= 0) { "源码中找不到 $funSignature" }
        val nextMethod = source.indexOf("\n    fun ", start)
        return source.substring(start, if (nextMethod >= 0) nextMethod else source.length)
    }
}
