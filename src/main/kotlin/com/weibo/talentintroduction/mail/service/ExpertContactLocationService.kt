package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 人工所在地配置的唯一业务写入口（plan 01 / c1，I-1～I-5），并承载只读回复时间推荐
 * （plan 02 / c2，I-1～I-6）。
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
 * - 推荐（[timing]）：只读配置、业务账号集合与 `inbound_mail_processing` 的窄投影，
 *   计算在 [ReplyTimeRecommender] 内完成；不写任何表、不发信、不收信、不改处理状态（I-6）。
 */
@Service
class ExpertContactLocationService(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val catalog: ExpertContactLocationCatalog,
    private val senderAccountRepository: MailSenderAccountRepository
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

    /**
     * 只读推荐（plan 02 / c2，I-1/I-2/I-4/I-6）：每个请求只在入口取一次 `now`。
     *
     * 样本只来自指定 contact 的 `inbound_mail_processing`；账号集合复用
     * `findAllByAccountCodeNot(SIMULATOR_NOOP)` 的既有口径（不读 INBOUND `mail_record`、
     * 不读正文、不按处理状态筛）；`received_at` 按 Asia/Shanghai 还原 Instant 后再投影到
     * 专家时区。未配置所在地直接 `recommendation=null`，绝不猜时区（I-3）。
     */
    fun timing(contactId: Long): ContactLocationTimingView = timingAt(contactId, Instant.now())

    /**
     * [timing] 的显式时刻入口：同一 now / 配置 / 记录必然得到同一输出（I-4），
     * 由服务/IT 测试直接传入固定时刻，不引入 Clock Bean、计算器本身仍不读系统时钟。
     */
    fun timingAt(contactId: Long, now: Instant): ContactLocationTimingView {
        val location = get(contactId)
        if (!location.configured) return ContactLocationTimingView(location, null)
        val zone = ZoneId.of(
            location.effectiveZoneId ?: throw IllegalStateException("已配置所在地缺少生效时区：$contactId")
        )
        val (samples, truncated) = loadTimingSamples(contactId, now)
        val window = ReplyTimeRecommender.recommend(samples, zone, now)
        return ContactLocationTimingView(
            location = location,
            recommendation = TimingRecommendationView(
                mode = window.mode,
                localStart = offsetDateTime(window.localStart),
                localEnd = offsetDateTime(window.localEnd),
                beijingStart = offsetDateTime(window.beijingStart),
                beijingEnd = offsetDateTime(window.beijingEnd),
                sampleCount = window.sampleCount,
                replyDayCount = window.replyDayCount,
                historyDays = HISTORY_DAYS,
                historyTruncated = truncated,
                recentSamples = window.recentSamples.map { sample ->
                    TimingRecentSampleView(
                        receivedAtBeijing = offsetDateTime(sample.beijing),
                        receivedAtLocal = offsetDateTime(sample.local)
                    )
                },
                calculatedAt = now.toString()
            )
        )
    }

    /**
     * 最近 [HISTORY_DAYS] 天的样本：只投影身份与接收时刻，按接收时刻倒序取
     * [TIMING_SAMPLE_LIMIT] + 1 行（多取一行仅用于判断截断）。账号集合为空即零样本，
     * 不发出非法的 `IN ()` 查询。
     */
    private fun loadTimingSamples(contactId: Long, now: Instant): Pair<List<Instant>, Boolean> {
        val accountCodes = senderAccountRepository
            .findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
            .map { it.accountCode }
        if (accountCodes.isEmpty()) return emptyList<Instant>() to false
        val params = MapSqlParameterSource()
            .addValue("contactId", contactId)
            .addValue("accountCodes", accountCodes)
            .addValue(
                "fromBeijing",
                LocalDateTime.ofInstant(now.minus(Duration.ofDays(HISTORY_DAYS.toLong())), ReplyTimeRecommender.BEIJING_ZONE)
            )
            .addValue("nowBeijing", LocalDateTime.ofInstant(now, ReplyTimeRecommender.BEIJING_ZONE))
        val rows = jdbcTemplate.query(TIMING_SAMPLES_SQL, params, timingRowMapper)
        val truncated = rows.size > TIMING_SAMPLE_LIMIT
        val samples = dedupeTimingSamples(rows.take(TIMING_SAMPLE_LIMIT))
            .map { it.receivedAt.atZone(ReplyTimeRecommender.BEIJING_ZONE).toInstant() }
        return samples to truncated
    }

    /**
     * 身份去重（I-1）：先按非空 `trim(message_id)` 精确匹配（不改大小写，保留排序中第一条），
     * 再按有效物理键 `(物理 owner 非空, 代际>0, UID)` 合并。两列身份都不可用的历史行
     * （owner=null、uidValidity=0 且无 message-id）没有可比的物理身份，只能各自按行存在 ——
     * 绝不把未知 owner/代际猜成当前值，也绝不按正文猜重复。
     */
    private fun dedupeTimingSamples(rows: List<TimingSampleRow>): List<TimingSampleRow> {
        val seenMessageIds = mutableSetOf<String>()
        val seenPhysicalIdentities = mutableSetOf<PhysicalIdentity>()
        return rows
            .filter { row ->
                val messageId = row.normalizedMessageId
                messageId == null || seenMessageIds.add(messageId)
            }
            .filter { row ->
                val identity = row.physicalIdentity
                identity == null || seenPhysicalIdentities.add(identity)
            }
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

    /**
     * 一条待判定的来信行。`received_at` 以 [LocalDateTime] 取出（DATETIME 无时区，
     * 由代码显式按 Asia/Shanghai 解释，绝不依赖驱动/系统默认时区换算）。
     */
    private data class TimingSampleRow(
        val messageId: String?,
        val mailboxOwnerCode: String?,
        val uidValidity: Long,
        val imapUid: Long,
        val receivedAt: LocalDateTime
    ) {
        /** 非空 `trim(message_id)`；空串等价于没有 message-id（不改大小写）。 */
        val normalizedMessageId: String?
            get() = messageId?.trim()?.takeIf { it.isNotEmpty() }

        /** 有效物理身份：物理 owner 非空且代际 > 0；历史未知行返回 null，绝不猜代际（I-1）。 */
        val physicalIdentity: PhysicalIdentity?
            get() {
                val owner = mailboxOwnerCode
                return if (owner == null || owner.isBlank() || uidValidity <= 0) {
                    null
                } else {
                    PhysicalIdentity(owner, uidValidity, imapUid)
                }
            }
    }

    private data class PhysicalIdentity(
        val mailboxOwnerCode: String,
        val uidValidity: Long,
        val imapUid: Long
    )

    private val timingRowMapper = RowMapper<TimingSampleRow> { rs, _ ->
        TimingSampleRow(
            messageId = rs.getString("message_id"),
            mailboxOwnerCode = rs.getString("mailbox_owner_code"),
            uidValidity = rs.getLong("uid_validity"),
            imapUid = rs.getLong("imap_uid"),
            receivedAt = rs.getObject("received_at", LocalDateTime::class.java)
        )
    }

    private companion object {
        const val UNAUTHORIZED_MESSAGE = "未登录"

        /** 推荐窗口固定回看天数（响应字段 historyDays 与查询下界同源）。 */
        const val HISTORY_DAYS = 180

        /** 参与计算的最大样本行数；第 [TIMING_SAMPLE_LIMIT] + 1 行只用于判断截断。 */
        const val TIMING_SAMPLE_LIMIT = 1000

        val OFFSET_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        /** 窗口端点统一输出完整 ISO offset date-time（跨日不丢日期，I-5）。 */
        fun offsetDateTime(value: ZonedDateTime): String = OFFSET_DATE_TIME.format(value)

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

        /**
         * 样本窄投影（plan 02 T-1 逐字 SQL）：只取身份与接收时刻，不取正文/主题/发件人，
         * 不按 `process_status`/`process_reason` 过滤（这些列会被人工解决改写，不是学习资格）。
         * `id` 只用于稳定排序（`received_at` 相同时的后备次序），不进入响应。
         */
        const val TIMING_SAMPLES_SQL = """
            SELECT id, message_id, mailbox_owner_code, uid_validity, imap_uid, received_at
            FROM inbound_mail_processing
            WHERE expert_contact_id = :contactId
              AND sender_account_code IN (:accountCodes)
              AND received_at >= :fromBeijing
              AND received_at <= :nowBeijing
            ORDER BY received_at DESC, id DESC
            LIMIT 1001
        """
    }
}
