package com.weibo.talentintroduction.mail.service

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

/**
 * 人工所在地配置的唯一业务写入口（plan 01 / c1，I-1～I-5）。
 *
 * 窄服务 + `NamedParameterJdbcTemplate` + contact 存在性 + MySQL 参数化 upsert
 * （与 [ExpertRepliedDismissalService] 同范式）：只有本表被写入，绝不触碰
 * `expert_contact.country`、ES、回填、发信、排期或晋级（I-5）。
 *
 * 语义：
 * - 读：无行 = 未配置（`configured=false`），不创建占位行；contact 不存在抛
 *   [NoSuchElementException]（404）。
 * - 写：先完成全部目录校验（国家码 trim 后大写、显式时区必须属于该国），再做参数化
 *   `INSERT ... ON DUPLICATE KEY UPDATE`；失败分支在 `update` 之前抛出，不写任何表（I-4）。
 * - 列语义：`zone_id IS NULL` = 使用目录 `defaultZoneId`；显式值只存用户所选值，
 *   绝不把展开后的默认值回写（I-3）。换国家时两列同时覆盖，旧显式时区不会残留。
 */
@Service
class ExpertContactLocationService(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val catalog: ExpertContactLocationCatalog
) {

    /** 读取配置；contact 不存在 404（[NoSuchElementException]），未配置返回 `configured=false`。 */
    fun get(contactId: Long): ContactLocationView {
        requireContactExists(contactId)
        val persisted = findPersisted(contactId)
            ?: return ContactLocationView(
                contactId = contactId,
                configured = false,
                countryCode = null,
                countryLabel = null,
                zoneId = null,
                effectiveZoneId = null,
                zoneLabel = null,
                usingDefaultZone = false
            )
        return view(contactId, persisted.countryCode, persisted.zoneId)
    }

    /**
     * 唯一写入口：要求会话 username 非空（业务代码不从请求体接收 username），
     * 校验通过后单条 upsert，并回读已持久化的配置作为返回值。
     */
    fun save(username: String, contactId: Long, request: SaveContactLocationRequest): ContactLocationView {
        require(username.isNotBlank()) { UNAUTHORIZED_MESSAGE }
        val countryCode = request.countryCode.trim().uppercase()
        val country = catalog.country(countryCode)
            ?: throw IllegalArgumentException("未知国家代码：${request.countryCode}")
        val zoneId = request.zoneId?.trim()?.takeIf { it.isNotEmpty() }
        if (zoneId != null && country.zones.none { it.id == zoneId }) {
            throw IllegalArgumentException("国家 ${country.code} 不支持时区：$zoneId")
        }
        requireContactExists(contactId)
        val params = MapSqlParameterSource()
            .addValue("contactId", contactId)
            .addValue("countryCode", country.code)
            .addValue("zoneId", zoneId)
        jdbcTemplate.update(UPSERT_SQL, params)
        val persisted = findPersisted(contactId)
            ?: throw IllegalStateException("所在地配置写入后无法读回：contactId=$contactId")
        return view(contactId, persisted.countryCode, persisted.zoneId)
    }

    private fun requireContactExists(contactId: Long) {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expert_contact WHERE id = :contactId",
            MapSqlParameterSource().addValue("contactId", contactId),
            Long::class.java
        ) ?: 0L
        if (count == 0L) throw NoSuchElementException("联系人不存在：$contactId")
    }

    private fun findPersisted(contactId: Long): PersistedLocation? =
        jdbcTemplate.query(
            "SELECT country_code, zone_id FROM expert_contact_location WHERE expert_contact_id = :contactId",
            MapSqlParameterSource().addValue("contactId", contactId),
            locationRowMapper
        ).firstOrNull()

    /**
     * 把已持久化行映射为响应。目录中不存在的国家码只可能来自越权/历史脏数据：
     * 明确报错，绝不猜国家、绝不回退 UTC（I-1/I-3）。
     */
    private fun view(contactId: Long, countryCode: String, zoneId: String?): ContactLocationView {
        val country = catalog.country(countryCode)
            ?: throw IllegalStateException("目录中不存在已保存的国家代码：$countryCode")
        val effectiveZoneId = zoneId ?: country.defaultZoneId
        val zoneLabel = country.zones.firstOrNull { it.id == effectiveZoneId }?.labelZh
            ?: throw IllegalStateException("目录中不存在已保存的时区：$effectiveZoneId")
        return ContactLocationView(
            contactId = contactId,
            configured = true,
            countryCode = country.code,
            countryLabel = country.labelZh,
            zoneId = zoneId,
            effectiveZoneId = effectiveZoneId,
            zoneLabel = zoneLabel,
            usingDefaultZone = zoneId == null
        )
    }

    private data class PersistedLocation(val countryCode: String, val zoneId: String?)

    private val locationRowMapper = RowMapper<PersistedLocation> { rs, _ ->
        PersistedLocation(rs.getString("country_code"), rs.getString("zone_id"))
    }

    private companion object {
        const val UNAUTHORIZED_MESSAGE = "未登录"

        /**
         * 参数化 upsert：`zone_id` 写入原始选择（null = 用国家默认），
         * 换国家时两列一起覆盖，旧显式时区不残留（I-3）；
         * 同 contact 并发保存由数据库决定最终值，不做乐观锁（T-3）。
         */
        const val UPSERT_SQL = """
            INSERT INTO expert_contact_location (expert_contact_id, country_code, zone_id)
            VALUES (:contactId, :countryCode, :zoneId)
            ON DUPLICATE KEY UPDATE
                country_code = VALUES(country_code),
                zone_id = VALUES(zone_id)
        """
    }
}
