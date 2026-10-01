package com.weibo.talentintroduction.mail.service

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
import java.time.ZoneId

/**
 * 01（c1）人工所在地配置服务单测（I-1～I-5）。
 *
 * `NamedParameterJdbcTemplate` 为 mock（与 ExpertMaterialServiceTest 同款），
 * 行读取通过**真实** [RowMapper]（ResultSet 为 mock）执行，因此列名与映射逻辑仍被覆盖；
 * 真实 SQL / 迁移 / 外键语义由 [ExpertContactLocationServiceIT]（mysqlIt 门禁）承担。
 *
 * 覆盖：目录快照（247/418、byte 对齐证据）、unknown/非法国家与时区、null/默认/显式时区、
 * 空白归一、空白 username、不存在 contact、旧表零写入、目录外已保存值不猜国家。
 */
class ExpertContactLocationServiceTest {

    private val jdbc = Mockito.mock(NamedParameterJdbcTemplate::class.java)
    private val catalog = ExpertContactLocationCatalog()
    private val service = ExpertContactLocationService(jdbc, catalog)

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

    private fun capturedUpsertParams(): MapSqlParameterSource {
        val captor = ArgumentCaptor.forClass(MapSqlParameterSource::class.java)
        Mockito.verify(jdbc).update(Mockito.contains("INSERT INTO expert_contact_location"), captor.capture())
        return captor.value
    }

    private fun capturedUpdateSql(): List<String> =
        Mockito.mockingDetails(jdbc).invocations
            .filter { it.method.name == "update" }
            .map { it.arguments[0] as String }

    private fun assertNoWrite() {
        assertTrue(capturedUpdateSql().isEmpty(), "失败/只读分支不得发出任何写语句")
    }

    private companion object {
        const val EVIDENCE_SNAPSHOT_SHA256 =
            "c27ad26d63ccc151a82844d757b6494bfa73ba69c772d4f45dc627c6fa6b5480"
    }
}
