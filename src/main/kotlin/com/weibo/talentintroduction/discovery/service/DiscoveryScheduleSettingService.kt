package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.discovery.repository.DiscoveryScheduleSetting
import com.weibo.talentintroduction.discovery.repository.DiscoveryScheduleSettingRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryScheduleSpec
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * T-3（03）：`GET/PUT /api/expert-discovery/schedule` 的**唯一**响应形状（字段固定，04 直接渲染）。
 *
 * - `source` 是**当前生效**来源（来自调度器已应用快照，不来自数据库）：`CONFIG` = 沿用部署 cron，
 *   `OVERRIDE` = 已应用已保存的小时设置；
 * - `intervalHours`/`anchorAt` 是**已保存**值（无行或库不可读时为 null）；未保存且部署 cron 是可识别的
 *   两小时时显示 2，其他 cron 为 null；
 * - `nextTriggerAt`/`applied` 来自调度器已应用快照：无法计算、未应用或连续模式为 null/false；
 * - `saved`：PUT 表示设置是否已落库，GET 表示是否存在已保存设置 —— 503 的 `saved=false/true` 两种变体
 *   据此区分（`saved=true` 时提示「已保存但定时应用失败，请重试保存」）；
 * - `reason` 原因码可区分不可编辑模式（CONTINUOUS_MODE/DISABLED/CRON_DISABLED）、应用失败
 *   （APPLY_FAILED/NOT_STARTED）与库不可用（DB_UNAVAILABLE）；全部生效时为 null；
 * - `message` 是面向操作端的一句话（04 用 textContent 显示，不替代 `reason` 码）；时间是 ISO-8601（UTC，`Z`）。
 */
data class DiscoveryScheduleView(
    val mode: String,
    val editable: Boolean,
    val source: String,
    val intervalHours: Int?,
    val anchorAt: String?,
    val nextTriggerAt: String?,
    val applied: Boolean,
    val saved: Boolean,
    val reason: String?,
    val message: String
)

/**
 * controller 只做 HTTP 映射，语义全在 service：
 * `OK` → 200，`NOT_EDITABLE` → 409，`UNAVAILABLE` → 503（含 `saved=false/true` 两种变体）。
 */
enum class DiscoveryScheduleStatus { OK, NOT_EDITABLE, UNAVAILABLE }

data class DiscoveryScheduleResult(
    val status: DiscoveryScheduleStatus,
    val view: DiscoveryScheduleView
)

/**
 * I-1/I-3（03）：整数小时设置的读写与「保存即生效」接缝。
 *
 * - **一个设置、一条事实来源**：只有 [save] 会写 `discovery_schedule_setting`（单例行）；[get] 纯读，
 *   连 `GET` 都不会建行；
 * - 保存串行化：先在一个 [TransactionTemplate] 事务里判定/写入，**提交后**才经
 *   `ObjectProvider<ExpertDiscoveryScheduler>` 同步重排；重排成功才算 `applied=true`，
 *   因此不存在「页面假成功」；
 * - 首次保存与值变化共用**一次** `Clock` 读数（同一事务内不会出现两个锚点），相同值重存保留原锚点；
 * - 库读/写失败一律显式失败（503 + `DB_UNAVAILABLE`），绝不当作「无行」而静默回退默认 cron。
 */
@Service
class DiscoveryScheduleSettingService(
    private val repository: DiscoveryScheduleSettingRepository,
    private val transactionTemplate: TransactionTemplate,
    private val discoveryProperties: ExpertDiscoveryProperties,
    /** I-3：调度器经 ObjectProvider 读取/应用快照；调度器只依赖 repository，两者无循环依赖。 */
    private val schedulerProvider: ObjectProvider<ExpertDiscoveryScheduler>,
    private val clock: Clock = Clock.systemUTC()
) {

    private val log = LoggerFactory.getLogger(DiscoveryScheduleSettingService::class.java)

    /** I-3：保存串行化（单实例范围）。 */
    private val saveLock = Any()

    /** T-3：GET 无副作用。 */
    fun get(): DiscoveryScheduleResult {
        val read = readSetting()
        return DiscoveryScheduleResult(
            status = if (read.available) DiscoveryScheduleStatus.OK else DiscoveryScheduleStatus.UNAVAILABLE,
            view = view(row = read.setting, dbAvailable = read.available, savedOverride = null)
        )
    }

    /**
     * T-3：PUT。非法值由 controller 先行拒绝；这里再夹一次范围（不写库）。
     * 模式不可编辑（连续模式/`enabled=false`/`cron=-`）→ `NOT_EDITABLE`，**不写库**。
     *
     * I-3：整个「判定 → 写入 → 提交 → 重排 → 组装响应」在一个进程内锁里**串行**执行，
     * 因此并发两次保存不会交叉出「DB 值与有效 future 不一致」的响应；不引入分布式锁。
     * 该锁不会被运行中的发现持有（调度器只在「校验代次 + 占槽」的短临界区用另一把锁）。
     */
    fun save(intervalHours: Int): DiscoveryScheduleResult = synchronized(saveLock) {
        saveLocked(intervalHours)
    }

    private fun saveLocked(intervalHours: Int): DiscoveryScheduleResult {
        require(DiscoveryScheduleSpec.isValidIntervalHours(intervalHours)) {
            "intervalHours 必须是 ${DiscoveryScheduleSpec.MIN_INTERVAL_HOURS}～" +
                "${DiscoveryScheduleSpec.MAX_INTERVAL_HOURS} 的整数，当前为 $intervalHours"
        }
        val blockedReason = editabilityBlockReason()
        if (blockedReason != null) {
            val read = readSetting()
            val base = view(row = read.setting, dbAvailable = read.available, savedOverride = null)
            return DiscoveryScheduleResult(
                status = DiscoveryScheduleStatus.NOT_EDITABLE,
                view = base.copy(
                    editable = false,
                    applied = false,
                    reason = blockedReason,
                    message = messageFor(blockedReason, base.saved, applied = false, source = base.source)
                )
            )
        }
        // I-1：首次保存与值变化共用这一次读数（锚点唯一）。
        val now = clock.instant()
        val persisted: DiscoveryScheduleSetting
        try {
            persisted = requireNotNull(
                transactionTemplate.execute {
                    val existing = repository.find()
                    if (existing != null && existing.intervalHours == intervalHours) {
                        // I-1：相同值重存**不移动锚点、也不写库**；周期仍从原锚点推进。
                        existing
                    } else {
                        repository.save(intervalHours, now)
                    }
                }
            ) { "设置保存事务未返回已持久化的小时设置" }
        } catch (ex: Exception) {
            // I-3：DB 失败不取消原调度（未调用重排），也不假装保存成功。
            log.error("深度发现定时设置保存失败（已回滚，原调度未取消）：{}", ex.message)
            val base = view(row = null, dbAvailable = false, savedOverride = false)
            return DiscoveryScheduleResult(
                status = DiscoveryScheduleStatus.UNAVAILABLE,
                view = base.copy(
                    editable = true,
                    saved = false,
                    applied = false,
                    reason = DiscoveryScheduleSpec.REASON_DB_UNAVAILABLE,
                    message = "定时设置保存失败（数据库不可用），请稍后重试"
                )
            )
        }

        // I-3：DB 提交后同步重排；重排成功才报 applied=true。
        val scheduler = try {
            schedulerProvider.getIfAvailable()
        } catch (ex: Exception) {
            log.error("深度发现定时设置：无法取得调度器：{}", ex.message)
            null
        }
        val applied = scheduler != null && try {
            scheduler.reload()
        } catch (ex: Exception) {
            log.error("深度发现定时设置已保存但重排失败：{}", ex.message)
            false
        }

        val base = view(row = persisted, dbAvailable = true, savedOverride = true)
        if (applied) {
            return DiscoveryScheduleResult(DiscoveryScheduleStatus.OK, base)
        }
        val reason = scheduler?.snapshot()?.reason ?: DiscoveryScheduleSpec.REASON_NOT_STARTED
        return DiscoveryScheduleResult(
            status = DiscoveryScheduleStatus.UNAVAILABLE,
            view = base.copy(
                applied = false,
                reason = reason,
                message = messageFor(reason, saved = true, applied = false, source = base.source)
            )
        )
    }

    // ------------------------------------------------------------------
    // 内部：读取与响应组装
    // ------------------------------------------------------------------

    private class SettingRead(val available: Boolean, val setting: DiscoveryScheduleSetting?)

    /**
     * I-4：只有 `enabled=true`、`cron` 非 `-`、`pipelineEnabled=false` 才允许保存；
     * 其余模式返回可区分的原因码（PUT 一律 409 且不写库）。
     */
    private fun editabilityBlockReason(): String? = when {
        discoveryProperties.pipelineEnabled -> DiscoveryScheduleSpec.REASON_CONTINUOUS_MODE
        !discoveryProperties.enabled -> DiscoveryScheduleSpec.REASON_DISABLED
        discoveryProperties.cron.trim() == DiscoveryScheduleSpec.DISABLED_CRON ->
            DiscoveryScheduleSpec.REASON_CRON_DISABLED
        else -> null
    }

    /**
     * I-1：库不可用/存量值越界都**不是**「无行」—— 返回 `available=false`，
     * 由调用方报 503 + DB_UNAVAILABLE，绝不静默回退默认 cron。
     */
    private fun readSetting(): SettingRead =
        try {
            SettingRead(available = true, setting = repository.find())
        } catch (ex: Exception) {
            log.error("深度发现定时设置读取失败（不按“无行”处理）：{}", ex.message)
            SettingRead(available = false, setting = null)
        }

    private fun snapshotOrNull(): DiscoveryScheduleSnapshot? =
        try {
            schedulerProvider.getIfAvailable()?.snapshot()
        } catch (ex: Exception) {
            log.warn("深度发现定时设置：读取已应用快照失败：{}", ex.message)
            null
        }

    /**
     * I-3：`applied = 已注册 && 已应用快照与当前持久化设置逐字段一致`（无行时 = 已应用的是 CONFIG cron）。
     * 任何一处不一致（含重排失败后的旧快照）都返回 false，页面据此提示重试保存。
     */
    private fun view(
        row: DiscoveryScheduleSetting?,
        dbAvailable: Boolean,
        savedOverride: Boolean?
    ): DiscoveryScheduleView {
        val pipeline = discoveryProperties.pipelineEnabled
        val enabled = discoveryProperties.enabled
        val cronDisabled = discoveryProperties.cron.trim() == DiscoveryScheduleSpec.DISABLED_CRON
        val editable = enabled && !pipeline && !cronDisabled
        val snapshot = snapshotOrNull()
        val source = snapshot?.source ?: DiscoveryScheduleSpec.SOURCE_CONFIG
        val applied = dbAvailable && editable && snapshot?.registered == true && when {
            row != null -> snapshot.source == DiscoveryScheduleSpec.SOURCE_OVERRIDE &&
                snapshot.intervalHours == row.intervalHours && snapshot.anchorAt == row.updatedAt
            else -> snapshot.source == DiscoveryScheduleSpec.SOURCE_CONFIG
        }
        val reason = when {
            !dbAvailable -> DiscoveryScheduleSpec.REASON_DB_UNAVAILABLE
            pipeline -> DiscoveryScheduleSpec.REASON_CONTINUOUS_MODE
            !enabled -> DiscoveryScheduleSpec.REASON_DISABLED
            cronDisabled -> DiscoveryScheduleSpec.REASON_CRON_DISABLED
            applied -> null
            else -> snapshot?.reason ?: DiscoveryScheduleSpec.REASON_NOT_STARTED
        }
        val intervalHours = if (dbAvailable) {
            row?.intervalHours ?: DiscoveryScheduleSpec.configuredIntervalHours(discoveryProperties.cron)
        } else {
            null
        }
        val saved = savedOverride ?: (row != null)
        return DiscoveryScheduleView(
            mode = if (pipeline) DiscoveryScheduleSpec.MODE_CONTINUOUS else DiscoveryScheduleSpec.MODE_LEGACY,
            editable = editable,
            source = source,
            intervalHours = intervalHours,
            anchorAt = iso(row?.updatedAt),
            // 连续模式不返回一个仿佛已应用的小时计划。
            nextTriggerAt = if (pipeline) null else iso(snapshot?.nextTriggerAt),
            applied = applied,
            saved = saved,
            reason = reason,
            message = messageFor(reason, saved, applied, source, row?.intervalHours)
        )
    }

    private fun iso(instant: Instant?): String? = instant?.let { DateTimeFormatter.ISO_INSTANT.format(it) }

    /** 面向操作端的一句话；原因码本身不翻译（04 可自行按码渲染）。 */
    private fun messageFor(
        reason: String?,
        saved: Boolean,
        applied: Boolean,
        source: String,
        intervalHours: Int? = null
    ): String = when (reason) {
        DiscoveryScheduleSpec.REASON_CONTINUOUS_MODE -> "当前为连续发现模式，小时周期设置不适用"
        DiscoveryScheduleSpec.REASON_DISABLED -> "系统定时发现已停用（enabled=false），小时周期设置不适用"
        DiscoveryScheduleSpec.REASON_CRON_DISABLED -> "定时 cron 已停用（cron=-），小时周期设置不适用"
        DiscoveryScheduleSpec.REASON_DB_UNAVAILABLE ->
            if (saved) "已保存但定时设置库暂不可用，请重试保存" else "定时设置库暂不可用，请稍后重试"
        DiscoveryScheduleSpec.REASON_APPLY_FAILED, DiscoveryScheduleSpec.REASON_NOT_STARTED ->
            "已保存但定时应用失败，请重试保存"
        null ->
            if (applied && source == DiscoveryScheduleSpec.SOURCE_OVERRIDE) {
                "已设置每 $intervalHours 小时执行一次"
            } else if (applied) {
                "当前沿用系统定时（部署 cron）"
            } else {
                "定时尚未生效"
            }
        else -> "定时设置未生效（$reason）"
    }
}
