package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.security.MessageDigest
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 01/02（c1/c2）人工所在地配置与回复时间推荐服务单测（I-1～I-6）。
 *
 * `NamedParameterJdbcTemplate` 与账号仓库为 mock（与 ExpertMaterialServiceTest 同款），
 * 行读取通过**真实** [RowMapper]（ResultSet 为 mock）执行，因此列名与映射逻辑仍被覆盖；
 * 真实 SQL / 迁移 / 外键 / 去重语义由 [ExpertContactLocationServiceIT]（mysqlIt 门禁）承担。
 *
 * 覆盖：目录快照（247/418、byte 对齐证据）、unknown/非法国家与时区、null/默认/显式时区、
 * 空白归一、空白 username、不存在 contact、旧表零写入、目录外已保存值不猜国家；
 * 以及 timing 的只读闭包、样本来源 SQL、message-id/物理身份去重、模拟器排除、
 * 1001 行截断、时区重投影与冻结响应字段。
 */
class ExpertContactLocationServiceTest {

    private val jdbc = Mockito.mock(NamedParameterJdbcTemplate::class.java)
    private val catalog = ExpertContactLocationCatalog()
    private val senderAccounts = Mockito.mock(MailSenderAccountRepository::class.java)
    private val service = ExpertContactLocationService(jdbc, catalog, senderAccounts)

    // ------------------------------------------------------------------
    // 目录
    // ------------------------------------------------------------------

    @Test
    fun `catalog matches the frozen evidence snapshot byte for byte`() {
        val all = catalog.all()
        assertEquals("2026c", all.sourceVersion)
        assertEquals("preview-28-overrides-otherwise-first-zone-tab-row", all.defaultPolicy)
        assertTrue(all.sourceUrl.contains("zone.tab"))
        assertEquals(247, all.countries.size, "目录国家/地区数必须与证据快照一致")
        assertEquals(418, all.countries.sumOf { it.zones.size }, "时区关系数必须与证据快照一致")

        val codes = all.countries.map { it.code }
        assertEquals(codes.size, codes.toSet().size, "国家代码必须唯一")
        codes.forEach { assertEquals(it, it.trim().uppercase()); assertTrue(Regex("^[A-Z]{2}$").matches(it)) }

        all.countries.forEach { country ->
            assertTrue(country.zones.isNotEmpty(), "${country.code} 必须至少有一个时区")
            val ids = country.zones.map { it.id }
            assertEquals(ids.size, ids.toSet().size, "${country.code} 时区不允许重复")
            assertTrue(country.defaultZoneId in ids, "${country.code} 的默认时区必须属于该国 zones")
            ids.forEach { ZoneId.of(it) }
            assertTrue(country.labelZh.isNotBlank())
        }

        val br = catalog.country("BR")!!
        assertEquals("巴西", br.labelZh)
        assertEquals("America/Sao_Paulo", br.defaultZoneId)
        assertEquals(16, br.zones.size)
        assertEquals("巴西 · 马瑙斯", br.zones.first { it.id == "America/Manaus" }.labelZh)
        assertEquals(1, catalog.country("AD")!!.zones.size)
        assertNull(catalog.country("ZZ"), "目录没有 ZZ，调用方必须拒绝而不是猜")
        assertNull(catalog.country("br"), "查找只接受已归一的大写代码")

        // 资源必须是证据快照的逐字副本（本轮锁定目录版本，更新须另案审计）。
        val bytes = ClassPathResource("contact-country-timezones.json").inputStream.use { it.readBytes() }
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(EVIDENCE_SNAPSHOT_SHA256, sha256)
    }

    // ------------------------------------------------------------------
    // 读
    // ------------------------------------------------------------------

    @Test
    fun `unconfigured contact reads configured false without creating a placeholder`() {
        contactExists(1L)
        rows(emptyList())

        val view = service.get(881001L)

        assertEquals(
            ContactLocationView(
                contactId = 881001L,
                configured = false,
                countryCode = null,
                countryLabel = null,
                zoneId = null,
                effectiveZoneId = null,
                zoneLabel = null,
                usingDefaultZone = false
            ),
            view
        )
        assertNoWrite()
    }

    @Test
    fun `missing contact is not found for read and write and nothing is written`() {
        contactExists(0L)
        rows(emptyList())

        assertThrows(NoSuchElementException::class.java) { service.get(4242L) }
        assertThrows(NoSuchElementException::class.java) {
            service.save("admin", 4242L, SaveContactLocationRequest("BR", null))
        }
        assertNoWrite()
    }

    // ------------------------------------------------------------------
    // 写
    // ------------------------------------------------------------------

    @Test
    fun `country code is trimmed and uppercased and null zone stays null`() {
        contactExists(1L)
        rows(listOf("BR" to null))

        val view = service.save("admin", 7L, SaveContactLocationRequest("  br ", null))

        val params = capturedUpsertParams()
        assertEquals(7L, params.getValue("contactId"))
        assertEquals("BR", params.getValue("countryCode"))
        assertNull(params.getValue("zoneId"), "未选具体时区必须写 NULL，禁止回写展开后的默认值")
        assertNull(view.zoneId)
        assertEquals("BR", view.countryCode)
        assertEquals("巴西", view.countryLabel)
        assertEquals("America/Sao_Paulo", view.effectiveZoneId)
        assertEquals("巴西 · 圣保罗", view.zoneLabel)
        assertTrue(view.usingDefaultZone)
        assertTrue(view.configured)
    }

    @Test
    fun `explicit zone in the same country is persisted as the effective zone`() {
        contactExists(1L)
        rows(listOf("BR" to "America/Manaus"))

        val view = service.save("admin", 7L, SaveContactLocationRequest("BR", "America/Manaus"))

        val params = capturedUpsertParams()
        assertEquals("America/Manaus", params.getValue("zoneId"))
        assertEquals("America/Manaus", view.effectiveZoneId)
        assertEquals("America/Manaus", view.zoneId)
        assertFalse(view.usingDefaultZone)
    }

    @Test
    fun `blank zone is normalized to the country default`() {
        contactExists(1L)
        rows(listOf("BR" to null))

        val view = service.save("admin", 7L, SaveContactLocationRequest("BR", "   "))

        assertNull(capturedUpsertParams().getValue("zoneId"))
        assertNull(view.zoneId)
        assertTrue(view.usingDefaultZone)
        assertEquals("America/Sao_Paulo", view.effectiveZoneId)
    }

    @Test
    fun `unknown country and foreign zone are rejected before any write`() {
        val unknownCountry = assertThrows(IllegalArgumentException::class.java) {
            service.save("admin", 7L, SaveContactLocationRequest("ZZ", null))
        }
        assertTrue(unknownCountry.message!!.contains("未知国家代码"))

        val foreignZone = assertThrows(IllegalArgumentException::class.java) {
            service.save("admin", 7L, SaveContactLocationRequest("BR", "Asia/Tokyo"))
        }
        assertTrue(foreignZone.message!!.contains("不支持时区"))

        assertNoWrite()
    }

    @Test
    fun `blank session username is rejected before validation`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.save("   ", 7L, SaveContactLocationRequest("BR", null))
        }
        assertEquals("未登录", ex.message)
        assertNoWrite()
        Mockito.verifyNoInteractions(jdbc)
    }

    @Test
    fun `location writes touch exactly the location table`() {
        contactExists(1L)
        rows(listOf("BR" to "America/Manaus"))

        service.save("admin", 7L, SaveContactLocationRequest("BR", "America/Manaus"))

        val sqls = capturedUpdateSql()
        assertEquals(1, sqls.size, "唯一业务写入口只发出一条写语句")
        val sql = sqls.single()
        assertTrue(sql.contains("INSERT INTO expert_contact_location"))
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"))
        assertTrue(sql.contains(":countryCode"), "国家码必须参数化，不得拼接")
        assertFalse(sql.contains("expert_contact "), "不得写 expert_contact 表")
        assertFalse(sql.contains("operator_status"), "不得触碰既有状态写入口")
        assertFalse(sql.contains("'"), "写语句不允许出现字面量拼接")
    }

    // ------------------------------------------------------------------
    // timing（只读推荐，I-1～I-6）
    // ------------------------------------------------------------------

    @Test
    fun `timing without a configured location returns no recommendation and reads nothing else`() {
        contactExists(1L)
        rows(emptyList())

        val view = service.timingAt(CONTACT_ID, NOW)

        assertFalse(view.location.configured)
        assertNull(view.recommendation, "未配置所在地不得猜时区，也不得给出推荐")
        assertTrue(inboundQuerySql().isEmpty(), "未配置所在地时不得查询来信")
        Mockito.verifyNoInteractions(senderAccounts)
        assertNoWrite()
    }

    @Test
    fun `timing for a missing contact is not found and issues no sample query`() {
        contactExists(0L)
        rows(emptyList())

        assertThrows(NoSuchElementException::class.java) { service.timingAt(4242L, NOW) }

        assertTrue(inboundQuerySql().isEmpty())
        assertNoWrite()
    }

    @Test
    fun `timing reads only the whitelisted projection with business accounts and the fixed now`() {
        contactExists(1L)
        rows(listOf("BR" to null))
        accounts("acc-a", "acc-b")
        inboundRows(listOf(inboundSample(messageId = "m-1", receivedAt = localSaoPaulo("2026-10-01T14:15"))))

        service.timingAt(CONTACT_ID, NOW)

        val sql = inboundQuerySql().single()
        listOf("id", "message_id", "mailbox_owner_code", "uid_validity", "imap_uid", "received_at").forEach { column ->
            assertTrue(sql.contains(column), "投影必须包含 $column")
        }
        listOf("body", "cleaned_body", "subject", "from_email", "process_status", "process_reason").forEach { column ->
            assertFalse(sql.contains(column), "样本查询不得读取 $column")
        }
        assertTrue(sql.contains("WHERE expert_contact_id = :contactId"))
        assertTrue(sql.contains("AND sender_account_code IN (:accountCodes)"))
        assertTrue(sql.contains("AND received_at >= :fromBeijing"))
        assertTrue(sql.contains("AND received_at <= :nowBeijing"))
        assertTrue(sql.contains("ORDER BY received_at DESC, id DESC"))
        assertTrue(sql.contains("LIMIT 1001"), "名额上限固定为 1000，多取第 1001 行只用于判断截断")

        val params = capturedTimingParams()
        assertEquals(CONTACT_ID, params.getValue("contactId"))
        assertEquals(listOf("acc-a", "acc-b"), params.getValue("accountCodes"))
        assertEquals(LocalDateTime.parse("2026-04-05T08:00"), params.getValue("fromBeijing"), "180 天窗口按北京时间还原")
        assertEquals(LocalDateTime.parse("2026-10-02T08:00"), params.getValue("nowBeijing"))
        Mockito.verify(senderAccounts).findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
        Mockito.verifyNoMoreInteractions(senderAccounts)
        assertNoWrite()
    }

    @Test
    fun `timing assembles the frozen response shape from deduplicated samples`() {
        contactExists(1L)
        rows(listOf("BR" to null))
        accounts("acc-a")
        inboundRows(
            listOf(
                inboundSample(messageId = "m-1", receivedAt = localSaoPaulo("2026-09-29T14:15")),
                inboundSample(messageId = "m-2", receivedAt = localSaoPaulo("2026-09-30T14:15")),
                inboundSample(messageId = "m-3", receivedAt = localSaoPaulo("2026-10-01T14:15"))
            )
        )

        val view = service.timingAt(CONTACT_ID, NOW)

        assertEquals("BR", view.location.countryCode)
        assertEquals("America/Sao_Paulo", view.location.effectiveZoneId)
        val recommendation = view.recommendation!!
        assertEquals(TimingMode.REPLY_PATTERN, recommendation.mode)
        assertEquals("2026-10-02T13:00:00-03:00", recommendation.localStart)
        assertEquals("2026-10-02T15:00:00-03:00", recommendation.localEnd)
        assertEquals("2026-10-03T00:00:00+08:00", recommendation.beijingStart)
        assertEquals("2026-10-03T02:00:00+08:00", recommendation.beijingEnd)
        assertEquals(3, recommendation.sampleCount)
        assertEquals(3, recommendation.replyDayCount)
        assertEquals(180, recommendation.historyDays)
        assertFalse(recommendation.historyTruncated)
        assertEquals("2026-10-02T00:00:00Z", recommendation.calculatedAt)
        assertEquals(3, recommendation.recentSamples.size)
        assertEquals("2026-10-02T01:15:00+08:00", recommendation.recentSamples.first().receivedAtBeijing)
        assertEquals("2026-10-01T14:15:00-03:00", recommendation.recentSamples.first().receivedAtLocal)
        assertTrue(recommendation.recentSamples.first().receivedAtBeijing.endsWith("+08:00"))
        assertTrue(recommendation.recentSamples.first().receivedAtLocal.endsWith("-03:00"))
    }

    @Test
    fun `duplicate message ids and legacy rows without identity are deduplicated as specified`() {
        contactExists(1L)
        rows(listOf("BR" to null))
        accounts("acc-a", "acc-b")
        inboundRows(
            listOf(
                // 同一 message-id 出现在两个业务账号（账号范围由 SQL 的 IN 条件表达）：只算一次，
                // 保留排序中第一条（即账号范围里更新的那封）。
                inboundSample(messageId = "dup", receivedAt = localSaoPaulo("2026-10-01T14:15")),
                inboundSample(messageId = "dup", receivedAt = localSaoPaulo("2026-10-01T15:15")),
                // 历史未知身份（owner=null、代际=0、无 message-id）：没有可比身份，各自算一条。
                inboundSample(messageId = null, receivedAt = localSaoPaulo("2026-09-30T14:15")),
                inboundSample(messageId = null, receivedAt = localSaoPaulo("2026-09-30T15:15")),
                // 有效物理键相同但 message-id 不同：同一封物理来信，合并为一条。
                inboundSample(messageId = "phys-1", mailboxOwnerCode = "acc-a", uidValidity = 7, imapUid = 900, receivedAt = localSaoPaulo("2026-09-29T14:15")),
                inboundSample(messageId = "phys-2", mailboxOwnerCode = "acc-a", uidValidity = 7, imapUid = 900, receivedAt = localSaoPaulo("2026-09-29T16:15")),
                // 代际未知（owner 已知但 uidValidity=0）不得与任何行合并。
                inboundSample(messageId = "legacy-1", mailboxOwnerCode = "acc-a", uidValidity = 0, imapUid = 901, receivedAt = localSaoPaulo("2026-09-28T14:15")),
                inboundSample(messageId = "legacy-2", mailboxOwnerCode = "acc-a", uidValidity = 0, imapUid = 901, receivedAt = localSaoPaulo("2026-09-28T15:15"))
            )
        )

        val recommendation = service.timingAt(CONTACT_ID, NOW).recommendation!!

        // 8 行 → message-id 去重去掉 1 条 → 物理键去重去掉 1 条 = 6 条。
        assertEquals(6, recommendation.sampleCount)
        assertEquals(4, recommendation.replyDayCount)
        assertTrue(recommendation.recentSamples.all { it.receivedAtBeijing.isNotBlank() })
    }

    @Test
    fun `an empty business account set yields zero samples and never queries inbound rows`() {
        contactExists(1L)
        rows(listOf("BR" to null))
        accounts()

        val recommendation = service.timingAt(CONTACT_ID, NOW).recommendation!!

        assertEquals(0, recommendation.sampleCount)
        assertEquals(0, recommendation.replyDayCount)
        assertEquals(TimingMode.WORK_HOURS, recommendation.mode)
        assertEquals("2026-10-02T08:00:00-03:00", recommendation.localStart)
        assertEquals("2026-10-02T17:00:00-03:00", recommendation.localEnd)
        assertTrue(inboundQuerySql().isEmpty(), "账号集合为空不得发出 IN () 查询")
        assertNoWrite()
    }

    @Test
    fun `one thousand and one rows are truncated to the newest thousand`() {
        contactExists(1L)
        rows(listOf("BR" to null))
        accounts("acc-a")
        val rows = (0 until 1000).map { index ->
            inboundSample(messageId = "m-$index", receivedAt = SHANGHAI_WALL.minusSeconds(index * 20L))
        } + inboundSample(messageId = "m-old", receivedAt = SHANGHAI_WALL.minusDays(3))
        inboundRows(rows)

        val recommendation = service.timingAt(CONTACT_ID, NOW).recommendation!!

        assertEquals(1000, recommendation.sampleCount)
        assertEquals(1, recommendation.replyDayCount, "第 1001 行（最旧）必须被截断丢弃")
        assertTrue(recommendation.historyTruncated)
    }

    @Test
    fun `exactly one thousand rows are not reported as truncated`() {
        contactExists(1L)
        rows(listOf("BR" to null))
        accounts("acc-a")
        inboundRows(
            (0 until 1000).map { index ->
                inboundSample(messageId = "m-$index", receivedAt = SHANGHAI_WALL.minusSeconds(index * 20L))
            }
        )

        val recommendation = service.timingAt(CONTACT_ID, NOW).recommendation!!

        assertEquals(1000, recommendation.sampleCount)
        assertFalse(recommendation.historyTruncated)
    }

    @Test
    fun `changing the configured zone reprojects the same samples without touching sample identity`() {
        contactExists(1L)
        rows(listOf("BR" to "America/Sao_Paulo"))
        accounts("acc-a")
        inboundRows(listOf(inboundSample(messageId = "m-1", receivedAt = localSaoPaulo("2026-10-01T14:15"))))
        val before = service.timingAt(CONTACT_ID, NOW).recommendation!!

        rows(listOf("BR" to "America/Manaus"))
        val afterView = service.timingAt(CONTACT_ID, NOW)
        val after = afterView.recommendation!!

        assertEquals(1, after.sampleCount)
        assertEquals("2026-10-02T01:15:00+08:00", before.recentSamples.first().receivedAtBeijing)
        assertEquals(before.recentSamples.first().receivedAtBeijing, after.recentSamples.first().receivedAtBeijing)
        assertEquals("2026-10-01T14:15:00-03:00", before.recentSamples.first().receivedAtLocal)
        assertEquals("2026-10-01T13:15:00-04:00", after.recentSamples.first().receivedAtLocal, "同一 Instant 在新时区必须重新投影")
        assertEquals("America/Manaus", afterView.location.effectiveZoneId)
        assertFalse(afterView.location.usingDefaultZone)
    }

    // ------------------------------------------------------------------
    // 脏数据
    // ------------------------------------------------------------------

    @Test
    fun `persisted country outside the catalog is refused instead of guessed`() {
        contactExists(1L)
        rows(listOf("ZZ" to null))

        val ex = assertThrows(IllegalStateException::class.java) { service.get(1L) }

        assertTrue(ex.message!!.contains("目录中不存在已保存的国家代码"))
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun contactExists(count: Long) {
        Mockito.`when`(
            jdbc.queryForObject(
                Mockito.contains("FROM expert_contact WHERE id = :contactId"),
                Mockito.any(MapSqlParameterSource::class.java),
                Mockito.eq(Long::class.java)
            )
        ).thenReturn(count)
    }

    /** 用真实 RowMapper + mock ResultSet 提供已持久化行（列名因此仍被断言）。 */
    private fun rows(persisted: List<Pair<String, String?>>) {
        Mockito.`when`(
            jdbc.query(
                Mockito.contains("FROM expert_contact_location WHERE expert_contact_id = :contactId"),
                Mockito.any(MapSqlParameterSource::class.java),
                Mockito.any(RowMapper::class.java)
            )
        ).thenAnswer { invocation ->
            val mapper = invocation.getArgument<RowMapper<Any>>(2)
            persisted.map { (countryCode, zoneId) ->
                val rs = Mockito.mock(ResultSet::class.java)
                Mockito.`when`(rs.getString("country_code")).thenReturn(countryCode)
                Mockito.`when`(rs.getString("zone_id")).thenReturn(zoneId)
                mapper.mapRow(rs, 0)
            }
        }
    }

    /** 业务账号集合（仓库口径由 [MailSenderAccountService.SIMULATOR_ACCOUNT_CODE] 排除模拟器）。 */
    private fun accounts(vararg codes: String) {
        Mockito.`when`(
            senderAccounts.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
        ).thenReturn(codes.map { account(it) })
    }

    private fun account(accountCode: String): MailSenderAccount = MailSenderAccount(
        id = 1L,
        accountCode = accountCode,
        senderEmail = "$accountCode@example.test",
        senderName = accountCode,
        senderTitle = null,
        senderDisplayName = null,
        teamName = null,
        countryName = null,
        smtpHost = "smtp.test",
        smtpPort = 465,
        smtpUsername = "$accountCode@example.test",
        smtpPassword = "pw",
        imapHost = "imap.test",
        imapPort = 993,
        imapUsername = "$accountCode@example.test",
        imapPassword = "pw"
    )

    private data class InboundRow(
        val messageId: String?,
        val mailboxOwnerCode: String?,
        val uidValidity: Long,
        val imapUid: Long,
        val receivedAt: LocalDateTime
    )

    private fun inboundSample(
        messageId: String?,
        receivedAt: LocalDateTime,
        mailboxOwnerCode: String? = null,
        uidValidity: Long = 0,
        imapUid: Long = 0
    ): InboundRow = InboundRow(messageId, mailboxOwnerCode, uidValidity, imapUid, receivedAt)

    /** 用真实 RowMapper + mock ResultSet 提供来信行（列读取因此仍被断言）。 */
    private fun inboundRows(rows: List<InboundRow>) {
        Mockito.`when`(
            jdbc.query(
                Mockito.contains("FROM inbound_mail_processing"),
                Mockito.any(MapSqlParameterSource::class.java),
                Mockito.any(RowMapper::class.java)
            )
        ).thenAnswer { invocation ->
            val mapper = invocation.getArgument<RowMapper<Any>>(2)
            rows.map { row ->
                val rs = Mockito.mock(ResultSet::class.java)
                Mockito.`when`(rs.getString("message_id")).thenReturn(row.messageId)
                Mockito.`when`(rs.getString("mailbox_owner_code")).thenReturn(row.mailboxOwnerCode)
                Mockito.`when`(rs.getLong("uid_validity")).thenReturn(row.uidValidity)
                Mockito.`when`(rs.getLong("imap_uid")).thenReturn(row.imapUid)
                Mockito.`when`(rs.getObject("received_at", LocalDateTime::class.java)).thenReturn(row.receivedAt)
                mapper.mapRow(rs, 0)
            }
        }
    }

    /**
     * fixture 里写专家当地墙上时刻，DB 的 `received_at` 存的是北京时间墙钟：
     * 例如巴西 09-29 14:15（-03）对应北京 09-30 01:15。
     */
    private fun localSaoPaulo(text: String): LocalDateTime =
        LocalDateTime.ofInstant(LocalDateTime.parse(text).atZone(SAO_PAULO).toInstant(), SHANGHAI)

    private fun capturedUpsertParams(): MapSqlParameterSource {
        val captor = ArgumentCaptor.forClass(MapSqlParameterSource::class.java)
        Mockito.verify(jdbc).update(Mockito.contains("INSERT INTO expert_contact_location"), captor.capture())
        return captor.value
    }

    private fun capturedTimingParams(): MapSqlParameterSource {
        val captor = ArgumentCaptor.forClass(MapSqlParameterSource::class.java)
        Mockito.verify(jdbc).query(
            Mockito.contains("FROM inbound_mail_processing"),
            captor.capture(),
            Mockito.any(RowMapper::class.java)
        )
        return captor.value
    }

    private fun capturedUpdateSql(): List<String> =
        Mockito.mockingDetails(jdbc).invocations
            .filter { it.method.name == "update" }
            .map { it.arguments[0] as String }

    private fun inboundQuerySql(): List<String> =
        Mockito.mockingDetails(jdbc).invocations
            .filter { it.method.name == "query" && (it.arguments[0] as String).contains("inbound_mail_processing") }
            .map { it.arguments[0] as String }

    private fun assertNoWrite() {
        assertTrue(capturedUpdateSql().isEmpty(), "失败/只读分支不得发出任何写语句")
    }

    private companion object {
        const val EVIDENCE_SNAPSHOT_SHA256 =
            "c27ad26d63ccc151a82844d757b6494bfa73ba69c772d4f45dc627c6fa6b5480"

        const val CONTACT_ID = 881001L
        val NOW: Instant = Instant.parse("2026-10-02T00:00:00Z")
        val SHANGHAI: ZoneId = ZoneId.of("Asia/Shanghai")
        val SAO_PAULO: ZoneId = ZoneId.of("America/Sao_Paulo")

        /** NOW 对应的北京时间墙钟（fixture 的 received_at 口径）。 */
        val SHANGHAI_WALL: LocalDateTime = LocalDateTime.ofInstant(NOW, SHANGHAI)
    }
}
