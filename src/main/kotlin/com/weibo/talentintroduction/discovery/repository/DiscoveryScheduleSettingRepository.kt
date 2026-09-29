package com.weibo.talentintroduction.discovery.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * I-1（03）：深度发现同步模式的**唯一**小时设置（单例行 `id=1`）。
 *
 * - `intervalHours` 为 1～168 的整数小时；`updatedAt` 是该小时周期的**锚点**
 *   （UTC 墙钟、毫秒精度，读写都走 [ZoneOffset.UTC]，与 JVM/连接默认时区无关）。
 * - **无行 = 沿用部署 cron**，既不是「停用」，也不是「已保存默认 2 小时」。
 * - 不加 `ON UPDATE`：只有明确保存（首次或值变化）才移动锚点，相同值重存不重置周期。
 */
data class DiscoveryScheduleSetting(
    val id: Int,
    val intervalHours: Int,
    val updatedAt: Instant
)

/**
 * I-1/I-3/I-4（03）：设置与调度共用的**事实**常量。
 *
 * 放在 repository 层（service、scheduler、controller 都只向下依赖它），避免同一份范围、
 * 模式、来源与原因码在多处各写一遍。它们同时是 `GET/PUT /api/expert-discovery/schedule`
 * 的响应契约（04 按原因码渲染文案）。
 */
object DiscoveryScheduleSpec {

    /** I-1：第一版只支持整数 1～168 小时。 */
    const val MIN_INTERVAL_HOURS: Int = 1
    const val MAX_INTERVAL_HOURS: Int = 168

    /** 部署配置里「每偶数小时整点」的默认 cron；只有它被识别为「2 小时」。 */
    const val DEFAULT_TWO_HOUR_CRON: String = "0 0 */2 * * ?"

    /** `cron=-` 的既有禁用语义（仅停 cron）。 */
    const val DISABLED_CRON: String = "-"

    /** T-3：`mode` 只允许这两种取值。 */
    const val MODE_LEGACY: String = "LEGACY"
    const val MODE_CONTINUOUS: String = "CONTINUOUS"

    /** T-3：`source` 只允许这两种取值（CONFIG = 沿用部署 cron，OVERRIDE = 已保存小时设置）。 */
    const val SOURCE_CONFIG: String = "CONFIG"
    const val SOURCE_OVERRIDE: String = "OVERRIDE"

    /** T-3：`reason` 原因码；可区分「不可编辑」「应用失败」「库不可用」。 */
    const val REASON_CONTINUOUS_MODE: String = "CONTINUOUS_MODE"
    const val REASON_DISABLED: String = "DISABLED"
    const val REASON_CRON_DISABLED: String = "CRON_DISABLED"
    const val REASON_NOT_STARTED: String = "NOT_STARTED"
    const val REASON_APPLY_FAILED: String = "APPLY_FAILED"
    const val REASON_DB_UNAVAILABLE: String = "DB_UNAVAILABLE"

    /**
     * T-3：默认 cron 识别 —— **只**对准确的 [DEFAULT_TWO_HOUR_CRON] 返回 2；
     * 其他 cron 一律 null（不写一个通用 cron→小时推导器，也不猜语义）。
     */
    fun configuredIntervalHours(cron: String): Int? =
        if (cron.trim() == DEFAULT_TWO_HOUR_CRON) 2 else null

    /** I-1：写入与存量读取共用同一范围判据。 */
    fun isValidIntervalHours(value: Int): Boolean = value in MIN_INTERVAL_HOURS..MAX_INTERVAL_HOURS
}

/**
 * I-1（03）：`discovery_schedule_setting`（V144）的唯一读写口。
 *
 * - 只暴露 [find] 与 [save]；`save` 的 SQL **永远**只寻址单例行 [SINGLETON_ID]，
 *   不可能写出第二行或第二份设置；
 * - 范围双保险：SQL 层只认单例行，Kotlin 层 [DiscoveryScheduleSpec.isValidIntervalHours]
 *   在写入与**存量读取**两侧各夹一次（不依赖 MySQL 可能忽略的 CHECK 约束）；
 * - 读失败（连接/权限/超时）与存量值越界都**抛异常**：调用方不得把异常当成「无行」
 *   而静默回退默认 cron（那会把「库不可用」伪装成「未保存」）。
 */
@Repository
class DiscoveryScheduleSettingRepository(private val jdbcTemplate: JdbcTemplate) {

    /** 单例行；无行返回 null。读失败/存量值越界抛异常（绝不返回 null）。 */
    fun find(): DiscoveryScheduleSetting? =
        jdbcTemplate.query(
            "SELECT id, interval_hours, updated_at FROM discovery_schedule_setting WHERE id = ?",
            ROW_MAPPER,
            SINGLETON_ID
        ).firstOrNull()?.also { stored ->
            check(DiscoveryScheduleSpec.isValidIntervalHours(stored.intervalHours)) {
                "discovery_schedule_setting 存量值越界：" +
                    "interval_hours=${stored.intervalHours} 不在 " +
                    "${DiscoveryScheduleSpec.MIN_INTERVAL_HOURS}～${DiscoveryScheduleSpec.MAX_INTERVAL_HOURS}"
            }
        }

    /**
     * 写入给定锚点并回读（同一事务内可见，读回值即持久化后的毫秒精度锚点）。
     * 是否「同值不写」由 service 判定：锚点的移动只发生在首次保存或值变化时。
     */
    fun save(intervalHours: Int, updatedAt: Instant): DiscoveryScheduleSetting {
        require(DiscoveryScheduleSpec.isValidIntervalHours(intervalHours)) {
            "interval_hours 必须是 ${DiscoveryScheduleSpec.MIN_INTERVAL_HOURS}～" +
                "${DiscoveryScheduleSpec.MAX_INTERVAL_HOURS} 的整数，当前为 $intervalHours"
        }
        jdbcTemplate.update(
            """
            INSERT INTO discovery_schedule_setting (id, interval_hours, updated_at)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE interval_hours = VALUES(interval_hours), updated_at = VALUES(updated_at)
            """,
            SINGLETON_ID, intervalHours, toDb(updatedAt)
        )
        return requireNotNull(find()) { "discovery_schedule_setting 写入后读不到单例行" }
    }

    private companion object {
        /** I-1：单例表固定 id=1；SQL 只寻址这一行。 */
        const val SINGLETON_ID: Int = 1

        val ROW_MAPPER = RowMapper { rs: ResultSet, _: Int ->
            DiscoveryScheduleSetting(
                id = rs.getInt("id"),
                intervalHours = rs.getInt("interval_hours"),
                updatedAt = rs.getObject("updated_at", LocalDateTime::class.java).toInstant(ZoneOffset.UTC)
            )
        }

        fun toDb(instant: Instant): LocalDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
    }
}
