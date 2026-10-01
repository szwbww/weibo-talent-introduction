package com.weibo.talentintroduction.mail.service

// ---------------------------------------------------------------------------
// 人工所在地配置（plan 01 / c1）与回复时间推荐（plan 02 / c2）模型。
//
// 身份口径（I-1）：配置属于 expert_contact，不属于登录用户或 ORCID；无行即未配置，
// 绝不以空 country 或「未知国家」占位。
// ---------------------------------------------------------------------------

/** PUT 请求体：`{countryCode, zoneId}`；`zoneId` 为 null 表示持续使用该国目录默认时区（I-3）。 */
data class SaveContactLocationRequest(
    val countryCode: String,
    val zoneId: String? = null
)

/**
 * 配置响应（字段冻结，c2/c3 逐字复用）：
 * - 未配置：`configured=false`，后三个 zone 字段全为 null，`usingDefaultZone=false`；
 * - 已配置：`usingDefaultZone = (zoneId == null)`，`effectiveZoneId = zoneId ?: 国家默认`。
 *
 * `countryCode/countryLabel` 是目录权威展示值（不来自 `expert_contact.country`）。
 */
data class ContactLocationView(
    val contactId: Long,
    val configured: Boolean,
    val countryCode: String?,
    val countryLabel: String?,
    val zoneId: String?,
    val effectiveZoneId: String?,
    val zoneLabel: String?,
    val usingDefaultZone: Boolean
)

/** 目录内的一个时区项：`id` 为 IANA zone id（可 `ZoneId.of`），`labelZh` 为展示标签。 */
data class ContactTimezoneEntry(
    val id: String,
    val labelZh: String
)

/**
 * 目录内的一个国家/地区：`defaultZoneId` 必须属于本元素 `zones`；
 * `zones` 保持资源快照顺序，不按当前 UTC 偏移去重（偏移相同不等于规则相同）。
 */
data class ContactCountryEntry(
    val code: String,
    val labelZh: String,
    val defaultZoneId: String,
    val zones: List<ContactTimezoneEntry>
)

/**
 * 随包国家/时区目录（GET /api/mail/contact-locations/countries 的响应模型）。
 * 索引 `index` 不是构造参数，不参与序列化与 equals。
 */
data class ContactCountryTimezoneCatalog(
    val sourceVersion: String,
    val sourceUrl: String,
    val defaultPolicy: String,
    val countries: List<ContactCountryEntry>
) {
    private val index: Map<String, ContactCountryEntry> = countries.associateBy { it.code }

    fun find(code: String): ContactCountryEntry? = index[code]
}

// ---------------------------------------------------------------------------
// 回复时间推荐（plan 02 / c2）
//
// 字段集合冻结（计划 T-3）：只有下面这一套语义，不再增加 confidence/preferredHour/
// bestHour 等同义字段。时刻一律完整 ISO offset date-time，跨日不丢日期（I-5）。
// ---------------------------------------------------------------------------

/** 推荐模式：样本不足用完整工作时间；回复日数达到阈值后改用四桶峰值窗口（I-3）。 */
enum class TimingMode { WORK_HOURS, REPLY_PATTERN }

/** 最近来信时刻（当地 + 北京）。只回时刻：不含正文、主题、邮箱或内部 message-id（I-4）。 */
data class TimingRecentSampleView(
    val receivedAtBeijing: String,
    val receivedAtLocal: String
)

/**
 * 推荐窗口与解释字段：
 * - 窗口端点分别为专家生效时区（local*）与北京时间（beijing*）；
 * - `sampleCount`/`replyDayCount` 是去重后的实际条数 / 目标时区当地日期数；
 * - `historyDays` 固定 180，`historyTruncated` 如实表示最近 1000 条上限被触及；
 * - `calculatedAt` 是本请求取定的 now（ISO_INSTANT）。
 */
data class TimingRecommendationView(
    val mode: TimingMode,
    val localStart: String,
    val localEnd: String,
    val beijingStart: String,
    val beijingEnd: String,
    val sampleCount: Int,
    val replyDayCount: Int,
    val historyDays: Int,
    val historyTruncated: Boolean,
    val recentSamples: List<TimingRecentSampleView>,
    val calculatedAt: String
)

/**
 * `GET /api/mail/contact-locations/{contactId}/timing` 的响应：所在地 + 推荐。
 * 未配置所在地时 `configured=false` 且 `recommendation=null`（绝不猜时区，I-3）。
 */
data class ContactLocationTimingView(
    val location: ContactLocationView,
    val recommendation: TimingRecommendationView?
)
