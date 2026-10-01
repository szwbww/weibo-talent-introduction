package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
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
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.sql.DataSource

/**
 * 01/02（c1/c2）人工所在地配置与回复时间推荐真实 MySQL 集成测试（mysqlIt 门禁，`-Pmysql-it`）。
 *
 * 只连隔离库 `talent_contact_timing_it`（`@BeforeEach` 用 `SELECT DATABASE()` 守卫，
 * 连错库立即失败，绝不写业务库），Flyway 在上下文启动时从 V1 迁移到 V146。
 *
 * 证明（I-1～I-6 / IP-1～IP-4）：
 * - 迁移：新表列/主键/CHAR(2)/可空 zone_id、外键指向 expert_contact 且 DELETE CASCADE；
 * - I-1：未配置不建占位行；同 id 两次保存仍一行；不存在 contact 读/写 404（异常语义）；
 * - I-2：`br` 归一为 `BR`，`ZZ` 拒绝；目录快照在真实读回路径生效；来信时刻先还原为 Instant 再投影；
 * - I-3：null = 国家默认（DB 中 zone_id 为 NULL）、显式值属于该国、换配置不残留旧显式时区；
 * - I-4/IP-3：校验失败不产生任何写（前值保持）；
 * - IP-1/I-5：手工配置与 `expert_contact.country` 互不影响（回填原 country 不改新配置）；
 * - 持久性：换一条新连接/新服务实例（进程重启的 IT 等价物）仍读回同一配置；
 * - 外键：悬空 contact 被拒绝；contact 删除时级联清理本表；
 * - c2 timing：样本只来自关联的 processing 行（INBOUND mail_record 与模拟器行不计）、
 *   message-id/历史未知身份去重、绑定新增样本而解决/撤销解决不改变样本、180 天边界与
 *   未来行排除、第 1001 行截断、换时区重新投影、全程只读（IP-1/IP-2/IP-3/IP-4）。
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = ["spring.flyway.placeholder-replacement=false"])
class ExpertContactLocationServiceIT {

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var senderAccountRepository: MailSenderAccountRepository

    @Autowired
    private lateinit var inboundMailProcessingRepository: InboundMailProcessingRepository

    @Value("\${spring.datasource.url}")
    private lateinit var dbUrl: String

    @Value("\${spring.datasource.username}")
    private lateinit var dbUser: String

    @Value("\${spring.datasource.password}")
    private lateinit var dbPassword: String

    private val catalog = ExpertContactLocationCatalog()

    private lateinit var service: ExpertContactLocationService

    @BeforeEach
    fun setUp() {
        assertEquals(
            ISOLATED_SCHEMA,
            jdbcTemplate.queryForObject("SELECT DATABASE()", String::class.java),
            "IT 只允许连接隔离测试库"
        )
        service = ExpertContactLocationService(
            NamedParameterJdbcTemplate(dataSource),
            catalog,
            senderAccountRepository
        )
        cleanup()
        seed()
    }

    @AfterEach
    fun tearDown() {
        cleanup()
    }

    // ------------------------------------------------------------------
    // 迁移
    // ------------------------------------------------------------------

    @Test
    fun `migration creates the location table with contact primary key and cascade foreign key`() {
        val columns = jdbcTemplate.queryForList(
            """
            SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'expert_contact_location'
            ORDER BY ORDINAL_POSITION
            """.trimIndent()
        )
        assertEquals(
            listOf("expert_contact_id", "country_code", "zone_id"),
            columns.map { it["COLUMN_NAME"] }
        )
        assertEquals("PRI", columns[0]["COLUMN_KEY"])
        assertEquals("NO", columns[0]["IS_NULLABLE"])
        assertEquals("char(2)", columns[1]["COLUMN_TYPE"].toString().lowercase())
        assertEquals("NO", columns[1]["IS_NULLABLE"])
        assertEquals("varchar(64)", columns[2]["COLUMN_TYPE"].toString().lowercase())
        assertEquals("YES", columns[2]["IS_NULLABLE"], "zone_id 为空表示使用国家默认时区")

        val fk = jdbcTemplate.queryForMap(
            """
            SELECT REFERENCED_TABLE_NAME, DELETE_RULE
            FROM information_schema.REFERENTIAL_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'fk_expert_contact_location_contact'
            """.trimIndent()
        )
        assertEquals("expert_contact", fk["REFERENCED_TABLE_NAME"])
        assertEquals("CASCADE", fk["DELETE_RULE"])

        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '146'",
                Int::class.java
            )
        )
    }

    // ------------------------------------------------------------------
    // I-1 / I-2 / I-3
    // ------------------------------------------------------------------

    @Test
    fun `unconfigured contact reads configured false and creates no row then default and explicit zones persist in one row`() {
        assertEquals(0, locationRowCount(CONTACT_A))

        val unconfigured = service.get(CONTACT_A)
        assertFalse(unconfigured.configured)
        assertNull(unconfigured.zoneId)
        assertNull(unconfigured.effectiveZoneId)
        assertNull(unconfigured.countryLabel)
        assertFalse(unconfigured.usingDefaultZone)
        assertEquals(0, locationRowCount(CONTACT_A), "GET 不得创建占位行")

        val withDefault = service.save("admin", CONTACT_A, SaveContactLocationRequest("br", null))
        assertEquals("BR", withDefault.countryCode, "小写国家码必须归一为大写")
        assertEquals("巴西", withDefault.countryLabel)
        assertNull(withDefault.zoneId)
        assertEquals("America/Sao_Paulo", withDefault.effectiveZoneId)
        assertEquals("巴西 · 圣保罗", withDefault.zoneLabel)
        assertTrue(withDefault.usingDefaultZone)
        assertEquals(1, locationRowCount(CONTACT_A))
        assertNull(
            jdbcTemplate.queryForObject("SELECT zone_id FROM expert_contact_location WHERE expert_contact_id = ?", String::class.java, CONTACT_A),
            "未选具体时区时 DB 必须存 NULL，不得回写展开后的默认值"
        )
        assertEquals(
            "BR",
            jdbcTemplate.queryForObject("SELECT country_code FROM expert_contact_location WHERE expert_contact_id = ?", String::class.java, CONTACT_A)
        )

        val explicit = service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", "America/Manaus"))
        assertEquals("America/Manaus", explicit.zoneId)
        assertEquals("America/Manaus", explicit.effectiveZoneId)
        assertEquals("巴西 · 马瑙斯", explicit.zoneLabel)
        assertFalse(explicit.usingDefaultZone)
        assertEquals(1, locationRowCount(CONTACT_A), "同 id 两次保存仍只有一行")
        assertEquals(
            "America/Manaus",
            jdbcTemplate.queryForObject("SELECT zone_id FROM expert_contact_location WHERE expert_contact_id = ?", String::class.java, CONTACT_A)
        )

        val backToDefault = service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", "  "))
        assertNull(backToDefault.zoneId, "空白时区归一为 null")
        assertTrue(backToDefault.usingDefaultZone)
        assertNull(
            jdbcTemplate.queryForObject("SELECT zone_id FROM expert_contact_location WHERE expert_contact_id = ?", String::class.java, CONTACT_A),
            "切回默认必须清空旧显式时区"
        )
        assertEquals(1, locationRowCount(CONTACT_A))
    }

    @Test
    fun `configuration survives a fresh connection and service instance`() {
        service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", "America/Manaus"))

        val reloaded = freshService().get(CONTACT_A)

        assertEquals("BR", reloaded.countryCode)
        assertEquals("America/Manaus", reloaded.effectiveZoneId)
        assertFalse(reloaded.usingDefaultZone)
        assertTrue(reloaded.configured)
    }

    // ------------------------------------------------------------------
    // I-2 / I-4 / IP-3
    // ------------------------------------------------------------------

    @Test
    fun `rejected writes leave the previous configuration untouched`() {
        service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", "America/Manaus"))

        val foreignZone = assertThrows(IllegalArgumentException::class.java) {
            service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", "Asia/Tokyo"))
        }
        assertTrue(foreignZone.message!!.contains("不支持时区"))

        val unknownCountry = assertThrows(IllegalArgumentException::class.java) {
            service.save("admin", CONTACT_A, SaveContactLocationRequest("ZZ", null))
        }
        assertTrue(unknownCountry.message!!.contains("未知国家代码"))

        val blankUser = assertThrows(IllegalArgumentException::class.java) {
            service.save("  ", CONTACT_A, SaveContactLocationRequest("BR", null))
        }
        assertEquals("未登录", blankUser.message)

        val unchanged = service.get(CONTACT_A)
        assertEquals("BR", unchanged.countryCode)
        assertEquals("America/Manaus", unchanged.zoneId)
        assertEquals(1, locationRowCount(CONTACT_A))

        assertThrows(IllegalArgumentException::class.java) {
            service.save("admin", CONTACT_B, SaveContactLocationRequest("ZZ", null))
        }
        assertEquals(0, locationRowCount(CONTACT_B), "非法国家不得为任何 contact 建行")
    }

    @Test
    fun `missing contact is not found and dangling foreign key is rejected`() {
        assertThrows(NoSuchElementException::class.java) { service.get(ABSENT_CONTACT) }
        assertThrows(NoSuchElementException::class.java) {
            service.save("admin", ABSENT_CONTACT, SaveContactLocationRequest("BR", null))
        }
        assertEquals(0, locationRowCount(ABSENT_CONTACT))

        assertThrows(DataIntegrityViolationException::class.java) {
            jdbcTemplate.update(
                "INSERT INTO expert_contact_location (expert_contact_id, country_code, zone_id) VALUES (?, 'BR', NULL)",
                ABSENT_CONTACT
            )
        }
    }

    @Test
    fun `deleting the contact cascades the location row`() {
        service.save("admin", CONTACT_B, SaveContactLocationRequest("BR", null))
        assertEquals(1, locationRowCount(CONTACT_B))

        jdbcTemplate.update("DELETE FROM expert_contact WHERE id = ?", CONTACT_B)

        assertEquals(0, locationRowCount(CONTACT_B))
    }

    // ------------------------------------------------------------------
    // I-5 / IP-1
    // ------------------------------------------------------------------

    @Test
    fun `manual location and expert_contact country stay independent`() {
        val originalCountry = jdbcTemplate.queryForObject(
            "SELECT country FROM expert_contact WHERE id = ?", String::class.java, CONTACT_A
        )
        assertEquals("旧统计值", originalCountry)

        service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", "America/Manaus"))

        assertEquals(
            "旧统计值",
            jdbcTemplate.queryForObject("SELECT country FROM expert_contact WHERE id = ?", String::class.java, CONTACT_A),
            "人工配置不得改写 expert_contact.country"
        )
        val mailsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mail_record", Long::class.java)

        // 模拟既有画像/回填写入口改原 country：新配置必须完全不受影响。
        jdbcTemplate.update("UPDATE expert_contact SET country = ? WHERE id = ?", "画像回填值", CONTACT_A)

        assertEquals(
            "画像回填值",
            jdbcTemplate.queryForObject("SELECT country FROM expert_contact WHERE id = ?", String::class.java, CONTACT_A)
        )
        val after = service.get(CONTACT_A)
        assertEquals("BR", after.countryCode)
        assertEquals("America/Manaus", after.zoneId)
        assertEquals(
            mailsBefore,
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mail_record", Long::class.java),
            "所在地配置不影响邮件行数"
        )
    }

    // ------------------------------------------------------------------
    // c2 timing：来源、去重、绑定/解决、边界、截断与重投影（I-1～I-6）
    // ------------------------------------------------------------------

    @Test
    fun `timing counts only linked processing rows and ignores inbound mail records`() {
        val now = timingNow()
        service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", null))
        val replyDays = lastBeijingDays(now, 3)
        replyDays.forEachIndexed { index, day ->
            insertInbound(
                accountCode = TIMING_ACCOUNT_A,
                imapUid = 920000100L + index,
                messageId = fixtureMessageId(index + 1),
                receivedAt = day.atTime(1, 15)
            )
        }
        // 对应 INBOUND mail_record（同 message-id）与一条只在 mail_record 的来信：都不得计入样本。
        insertInboundMailRecord(fixtureMessageId(3), replyDays[2].atTime(1, 15))
        insertInboundMailRecord(fixtureMessageId(99), replyDays[0].atTime(2, 15))
        val mailsBefore = mailRecordCount()

        val view = service.timingAt(CONTACT_A, now)

        val recommendation = view.recommendation!!
        assertEquals(3, recommendation.sampleCount, "同一来信另有 INBOUND mail_record 时只计 processing 一次")
        assertEquals(3, recommendation.replyDayCount)
        assertEquals(TimingMode.REPLY_PATTERN, recommendation.mode)
        assertEquals(180, recommendation.historyDays)
        assertFalse(recommendation.historyTruncated)
        assertEquals(now.toString(), recommendation.calculatedAt)

        // 巴西 14:15 → 北京次日 00:00–02:00（13:00–15:00 与 13:30–15:30 平局取更早起点）。
        val localStart = ZonedDateTime.parse(recommendation.localStart)
        val localEnd = ZonedDateTime.parse(recommendation.localEnd)
        val beijingStart = ZonedDateTime.parse(recommendation.beijingStart)
        val beijingEnd = ZonedDateTime.parse(recommendation.beijingEnd)
        assertEquals(LocalTime.of(13, 0), localStart.toLocalTime())
        assertEquals(LocalTime.of(15, 0), localEnd.toLocalTime())
        assertEquals(LocalTime.of(0, 0), beijingStart.toLocalTime())
        assertEquals(LocalTime.of(2, 0), beijingEnd.toLocalTime())
        assertEquals(localStart.toLocalDate().plusDays(1), beijingStart.toLocalDate(), "北京侧跨日不得丢日期")
        assertEquals(localStart.toInstant(), beijingStart.toInstant())
        assertEquals(localEnd.toInstant(), beijingEnd.toInstant())
        assertFalse(localStart.toInstant().isBefore(now), "推荐起点不得早于 now")

        assertEquals(3, recommendation.recentSamples.size)
        recommendation.recentSamples.forEach { sample ->
            val local = ZonedDateTime.parse(sample.receivedAtLocal)
            val beijing = ZonedDateTime.parse(sample.receivedAtBeijing)
            assertEquals(LocalTime.of(14, 15), local.toLocalTime())
            assertEquals(LocalTime.of(1, 15), beijing.toLocalTime())
            assertEquals(local.toLocalDate().plusDays(1), beijing.toLocalDate())
            assertEquals(local.toInstant(), beijing.toInstant())
        }
        assertEquals(mailsBefore, mailRecordCount(), "timing 不得增删邮件行")
    }

    @Test
    fun `timing deduplicates one message id across accounts and never merges unknown identities`() {
        val now = timingNow()
        service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", null))
        val day = lastBeijingDays(now, 1).single()
        // 同一 message-id 落在两个业务账号：只算一次。
        insertInbound(TIMING_ACCOUNT_A, 920001001L, fixtureMessageId(21), day.atTime(1, 15))
        insertInbound(TIMING_ACCOUNT_B, 920001002L, fixtureMessageId(21), day.atTime(1, 20))
        // 一条不同 message-id 的来信。
        insertInbound(TIMING_ACCOUNT_A, 920001003L, fixtureMessageId(22), day.atTime(2, 15))
        // 历史未知身份（owner=NULL、代际=0、无 message-id）在同一物理邮箱下不得被当成同一封。
        insertInbound(TIMING_ACCOUNT_A, 920001004L, messageId = null, receivedAt = day.atTime(3, 15))
        insertInbound(TIMING_ACCOUNT_B, 920001004L, messageId = null, receivedAt = day.atTime(3, 15))

        val recommendation = service.timingAt(CONTACT_A, now).recommendation!!

        assertEquals(4, recommendation.sampleCount, "message-id 去重一次；历史未知身份各自保留")
    }

    @Test
    fun `simulator rows never enter the sample set`() {
        val now = timingNow()
        service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", null))
        val day = lastBeijingDays(now, 1).single()
        insertInbound(SIMULATOR_ACCOUNT, 920004001L, fixtureMessageId(31), day.atTime(1, 15))
        insertInbound(TIMING_ACCOUNT_A, 920004002L, fixtureMessageId(32), day.atTime(2, 15))

        assertTrue(
            senderAccountRepository.findAllByAccountCodeNot(SIMULATOR_ACCOUNT).none { it.accountCode == SIMULATOR_ACCOUNT },
            "既有账号口径不得包含模拟器"
        )
        val recommendation = service.timingAt(CONTACT_A, now).recommendation!!

        assertEquals(1, recommendation.sampleCount, "模拟器来信不得污染学习样本")
    }

    @Test
    fun `binding an unmatched row adds a sample while resolving and reopening keep it`() {
        val now = timingNow()
        service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", null))
        val day = lastBeijingDays(now, 1).single()
        insertInbound(TIMING_ACCOUNT_A, 920005001L, fixtureMessageId(41), day.atTime(1, 15))
        val unmatchedId = insertInbound(
            TIMING_ACCOUNT_A,
            920005002L,
            fixtureMessageId(42),
            day.atTime(2, 15),
            contactId = null
        )
        assertEquals(1, service.timingAt(CONTACT_A, now).recommendation!!.sampleCount)

        // IP-2：人工绑定未匹配来信（与 UnmatchedInboundMailService.bindToContact 同形的存储写入）。
        jdbcTemplate.update(
            "UPDATE inbound_mail_processing SET expert_contact_id = ? WHERE id = ?",
            CONTACT_A,
            unmatchedId
        )
        assertEquals(2, service.timingAt(CONTACT_A, now).recommendation!!.sampleCount, "绑定旧回复必须新增样本")

        // 人工解决（与 markResolved 同形的条件写入）。
        jdbcTemplate.update(
            """
            UPDATE inbound_mail_processing
               SET process_status = 'PROCESSED', process_reason = 'MANUAL_RESOLVED',
                   reason_type = 'MANUAL_RESOLVED', resolved_at = ?, resolved_by = 'admin'
             WHERE id = ?
            """.trimIndent(),
            LocalDateTime.ofInstant(now, SHANGHAI),
            unmatchedId
        )
        assertEquals(2, service.timingAt(CONTACT_A, now).recommendation!!.sampleCount, "处理状态不是学习资格门槛")

        // 撤销解决：走真实的条件 UPDATE 写入口（InboundMailProcessingRepository.reopenManualResolved）。
        assertEquals(
            1,
            inboundMailProcessingRepository.reopenManualResolved(unmatchedId, LocalDateTime.ofInstant(now, SHANGHAI)),
            "撤销解决必须命中真实 reopen 写入口"
        )
        assertEquals(2, service.timingAt(CONTACT_A, now).recommendation!!.sampleCount, "撤销解决不得改变样本身份与时间")
    }

    @Test
    fun `timing includes the exact one hundred eighty day boundary and excludes older or future rows`() {
        val now = timingNow()
        service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", null))
        val beijingNow = LocalDateTime.ofInstant(now, SHANGHAI)
        insertInbound(TIMING_ACCOUNT_A, 920006001L, fixtureMessageId(51), beijingNow.minusDays(180))
        insertInbound(TIMING_ACCOUNT_A, 920006002L, fixtureMessageId(52), beijingNow.minusDays(180).minusSeconds(1))
        insertInbound(TIMING_ACCOUNT_A, 920006003L, fixtureMessageId(53), beijingNow.plusSeconds(60))

        val recommendation = service.timingAt(CONTACT_A, now).recommendation!!

        assertEquals(1, recommendation.sampleCount, "恰好 180 天包含；早 1 秒与未来行排除")
        assertEquals(1, recommendation.replyDayCount)
    }

    @Test
    fun `the thousand and first row is truncated and the read stays inside the detail budget`() {
        val now = timingNow()
        service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", null))
        val beijingNow = LocalDateTime.ofInstant(now, SHANGHAI)
        // 1000 行落在同一当地日，第 1001 行（最旧）只用于判断截断。
        val windowStart = beijingNow.toLocalDate().minusDays(1).atTime(12, 0)
        val inserts = mutableListOf<Array<Any>>()
        (0 until 1000).forEach { index ->
            inserts += arrayOf<Any>(
                TIMING_ACCOUNT_A, 0L, 920010000L + index, fixtureMessageId(1000 + index),
                "timing-fixture@example.org", windowStart.minusSeconds(index.toLong()), CONTACT_A
            )
        }
        inserts += arrayOf<Any>(
            TIMING_ACCOUNT_A, 0L, 920020001L, fixtureMessageId(9999),
            "timing-fixture@example.org", windowStart.minusDays(5), CONTACT_A
        )
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO inbound_mail_processing
                (sender_account_code, uid_validity, imap_uid, message_id, from_email,
                 received_at, process_status, process_reason, expert_contact_id)
            VALUES (?, ?, ?, ?, ?, ?, 'MANUAL_REVIEW', 'TIMING_TEST_FIXTURE', ?)
            """.trimIndent(),
            inserts
        )
        println(
            "c2 EXPLAIN plan: " + jdbcTemplate.queryForList(
                """
                EXPLAIN SELECT id, message_id, mailbox_owner_code, uid_validity, imap_uid, received_at
                FROM inbound_mail_processing
                WHERE expert_contact_id = ? AND sender_account_code IN (?)
                  AND received_at >= ? AND received_at <= ?
                ORDER BY received_at DESC, id DESC
                LIMIT 1001
                """.trimIndent(),
                CONTACT_A,
                TIMING_ACCOUNT_A,
                beijingNow.minusDays(180),
                beijingNow
            )
        )

        val startedAt = System.nanoTime()
        val recommendation = service.timingAt(CONTACT_A, now).recommendation!!
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        println("c2 timing read over 1001 rows: ${elapsedMs} ms")

        assertEquals(1000, recommendation.sampleCount)
        assertEquals(1, recommendation.replyDayCount, "第 1001 行（最旧）必须被截断丢弃")
        assertTrue(recommendation.historyTruncated)
    }

    @Test
    fun `timing is read only and reprojects the same samples after a zone change`() {
        val now = timingNow()
        service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", null))
        val day = lastBeijingDays(now, 1).single()
        insertInbound(TIMING_ACCOUNT_A, 920007001L, fixtureMessageId(61), day.atTime(1, 15))

        val rowsBefore = timingRowCounts()
        val before = service.timingAt(CONTACT_A, now).recommendation!!
        assertEquals(rowsBefore, timingRowCounts(), "只读推荐不得写配置、来信或邮件表")

        val localBefore = ZonedDateTime.parse(before.recentSamples.single().receivedAtLocal)
        val beijingBefore = ZonedDateTime.parse(before.recentSamples.single().receivedAtBeijing)
        assertEquals(LocalTime.of(14, 15), localBefore.toLocalTime())
        assertEquals(LocalTime.of(1, 15), beijingBefore.toLocalTime())

        // IP-4：改时区后下一 GET 以新时区重投影同一批 Instant。
        service.save("admin", CONTACT_A, SaveContactLocationRequest("BR", "America/Manaus"))
        val after = service.timingAt(CONTACT_A, now).recommendation!!

        val localAfter = ZonedDateTime.parse(after.recentSamples.single().receivedAtLocal)
        val beijingAfter = ZonedDateTime.parse(after.recentSamples.single().receivedAtBeijing)
        assertEquals(1, after.sampleCount)
        assertEquals(localBefore.toInstant(), localAfter.toInstant(), "换时区只重新投影同一 Instant")
        assertEquals(LocalTime.of(13, 15), localAfter.toLocalTime())
        assertEquals(beijingBefore, beijingAfter)
        assertEquals(rowsBefore, timingRowCounts(), "换配置后的读取同样只读")

        // 01 的配置读取仍返回所选时区。
        val configured = service.get(CONTACT_A)
        assertEquals("America/Manaus", configured.effectiveZoneId)
        assertFalse(configured.usingDefaultZone)
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 最近 count 个「北京当地日」（不含今天）：fixture 用它构造确定的历史来信。 */
    private fun lastBeijingDays(now: Instant, count: Int): List<LocalDate> {
        val today = LocalDateTime.ofInstant(now, SHANGHAI).toLocalDate()
        return (1..count).map { today.minusDays(it.toLong()) }
    }

    /**
     * timing 用例统一取秒级 now：`inbound_mail_processing.received_at` 是 DATETIME（无小数秒），
     * 带纳秒的边界值入库会被舍入，导致「恰好第 180 天」的包含性断言不确定。
     * 截断到秒后，fixture 值与 service 的窗口下界逐位相同。
     */
    private fun timingNow(): Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)

    private fun fixtureMessageId(suffix: Int): String = "contact-timing-fixture-$CONTACT_A-$suffix"

    /**
     * 插入一条历史来信行（与收信写入同形的最小列集合），返回其 id。
     * `received_at` 是北京墙上时刻：北京 01:15 对应巴西前一天 14:15（计划 fixture 口径）。
     */
    private fun insertInbound(
        accountCode: String,
        imapUid: Long,
        messageId: String?,
        receivedAt: LocalDateTime,
        contactId: Long? = CONTACT_A,
        mailboxOwnerCode: String? = null,
        uidValidity: Long = 0
    ): Long {
        jdbcTemplate.update(
            """
            INSERT INTO inbound_mail_processing
                (sender_account_code, mailbox_owner_code, uid_validity, imap_uid, message_id, from_email,
                 received_at, process_status, process_reason, expert_contact_id)
            VALUES (?, ?, ?, ?, ?, 'timing-fixture@example.org', ?, 'MANUAL_REVIEW', 'TIMING_TEST_FIXTURE', ?)
            """.trimIndent(),
            accountCode,
            mailboxOwnerCode,
            uidValidity,
            imapUid,
            messageId,
            receivedAt,
            contactId
        )
        return jdbcTemplate.queryForObject(
            "SELECT id FROM inbound_mail_processing WHERE sender_account_code = ? AND uid_validity = ? AND imap_uid = ?",
            Long::class.java,
            accountCode,
            uidValidity,
            imapUid
        )!!
    }

    private fun insertInboundMailRecord(messageId: String, receivedAt: LocalDateTime) {
        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, message_id,
                 subject, body, send_status, received_at, created_at)
            VALUES (?, 'INBOUND', 'REPLY', ?, ?, 'timing fixture', 'body', 'SENT', ?, ?)
            """.trimIndent(),
            CONTACT_A,
            TIMING_ACCOUNT_A,
            messageId,
            receivedAt,
            receivedAt
        )
    }

    private fun mailRecordCount(): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mail_record WHERE message_id LIKE 'contact-timing-fixture-%'",
            Long::class.java
        ) ?: 0

    /** timing 读取前后的三张表行数（I-6 只读证明）。 */
    private fun timingRowCounts(): List<Long> = listOf(
        countOf("SELECT COUNT(*) FROM expert_contact_location"),
        countOf("SELECT COUNT(*) FROM inbound_mail_processing"),
        countOf("SELECT COUNT(*) FROM mail_record")
    )

    private fun countOf(sql: String): Long = jdbcTemplate.queryForObject(sql, Long::class.java) ?: 0

    private fun locationRowCount(contactId: Long): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expert_contact_location WHERE expert_contact_id = ?",
            Int::class.java,
            contactId
        ) ?: 0

    /** 进程重启的 IT 等价物：新连接 + 新服务实例，只从 MySQL 读回（无任何内存态）。 */
    private fun freshService(): ExpertContactLocationService =
        ExpertContactLocationService(
            NamedParameterJdbcTemplate(DriverManagerDataSource(dbUrl, dbUser, dbPassword)),
            ExpertContactLocationCatalog(),
            senderAccountRepository
        )

    private fun cleanup() {
        // 先删来信与邮件行：两者都对 expert_contact 有外键。
        // 来信按 fixture message-id 前缀或本 IT 的两个 contact 归属删除 —— 人工解决/撤销解决会改写
        // process_reason（MANUAL_RESOLVED / MANUAL_REOPENED），不能只按初始 fixture 原因匹配。
        jdbcTemplate.update(
            "DELETE FROM inbound_mail_processing WHERE message_id LIKE 'contact-timing-fixture-%' OR expert_contact_id BETWEEN ? AND ?",
            CONTACT_A,
            CONTACT_B
        )
        jdbcTemplate.update("DELETE FROM mail_record WHERE message_id LIKE 'contact-timing-fixture-%'")
        jdbcTemplate.update("DELETE FROM expert_contact_location WHERE expert_contact_id BETWEEN ? AND ?", CONTACT_A, CONTACT_B)
        jdbcTemplate.update("DELETE FROM expert_contact WHERE id BETWEEN ? AND ?", CONTACT_A, CONTACT_B)
        jdbcTemplate.update("DELETE FROM campaign WHERE id = ?", CAMPAIGN_ID)
        jdbcTemplate.update(
            "DELETE FROM mail_sender_account WHERE id IN (?, ?, ?)",
            SENDER_ID,
            TIMING_ACCOUNT_A_ID,
            TIMING_ACCOUNT_B_ID
        )
    }

    private fun seed() {
        jdbcTemplate.update(
            """
            INSERT INTO mail_sender_account
                (id, account_code, sender_email, sender_name, smtp_host, smtp_port,
                 smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password)
            VALUES (?, 'location-it', 'sender@example.test', 'Sender', 'smtp.test', 465,
                    'sender@example.test', 'pw', 'imap.test', 993, 'sender@example.test', 'pw'),
                   (?, ?, 'sender-a@example.test', 'Sender A', 'smtp.test', 465,
                    'sender-a@example.test', 'pw', 'imap.test', 993, 'sender-a@example.test', 'pw'),
                   (?, ?, 'sender-b@example.test', 'Sender B', 'smtp.test', 465,
                    'sender-b@example.test', 'pw', 'imap.test', 993, 'sender-b@example.test', 'pw')
            """.trimIndent(),
            SENDER_ID,
            TIMING_ACCOUNT_A_ID,
            TIMING_ACCOUNT_A,
            TIMING_ACCOUNT_B_ID,
            TIMING_ACCOUNT_B
        )
        jdbcTemplate.update(
            "INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id) VALUES (?, 'LOCATION_IT', 'Location IT', ?)",
            CAMPAIGN_ID,
            SENDER_ID
        )
        jdbcTemplate.update(
            """
            INSERT INTO expert_contact (id, campaign_id, orcid_id, expert_email, expert_name, country)
            VALUES (?, ?, 'location-it-a', 'a@example.test', 'A', '旧统计值'),
                   (?, ?, 'location-it-b', 'b@example.test', 'B', NULL)
            """.trimIndent(),
            CONTACT_A,
            CAMPAIGN_ID,
            CONTACT_B,
            CAMPAIGN_ID
        )
    }

    private companion object {
        const val ISOLATED_SCHEMA = "talent_contact_timing_it"
        const val SENDER_ID = 881000L
        const val TIMING_ACCOUNT_A_ID = 881010L
        const val TIMING_ACCOUNT_B_ID = 881011L
        const val TIMING_ACCOUNT_A = "location-it-a"
        const val TIMING_ACCOUNT_B = "location-it-b"
        const val SIMULATOR_ACCOUNT = "SIMULATOR_NOOP"
        const val CAMPAIGN_ID = 881000L
        const val CONTACT_A = 881001L
        const val CONTACT_B = 881002L
        const val ABSENT_CONTACT = 889999999L
        val SHANGHAI: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}
