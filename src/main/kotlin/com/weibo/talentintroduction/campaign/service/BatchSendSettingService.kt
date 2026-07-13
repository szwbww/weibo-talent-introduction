package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.BatchSendSetting
import com.weibo.talentintroduction.campaign.event.BatchSendCronChangedEvent
import com.weibo.talentintroduction.campaign.repository.BatchSendSettingRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Service
import java.time.LocalDateTime

enum class BatchSendType { INTRODUCTION, MATERIAL_REMINDER }

@Service
class BatchSendSettingService(
    private val repository: BatchSendSettingRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(BatchSendSettingService::class.java)

    // ── Key resolver ───────────────────────────────────────────────────────────

    private fun keyPrefix(sendType: BatchSendType): String = when (sendType) {
        BatchSendType.INTRODUCTION -> "batchSend"
        BatchSendType.MATERIAL_REMINDER -> "batchSend.materialReminder"
    }

    private fun k(sendType: BatchSendType, suffix: String): String =
        "${keyPrefix(sendType)}.$suffix"

    // ── Defaults per type ──────────────────────────────────────────────────────

    private fun defaultCron(sendType: BatchSendType) = when (sendType) {
        BatchSendType.INTRODUCTION -> DEFAULT_INTRO_CRON
        BatchSendType.MATERIAL_REMINDER -> DEFAULT_REMINDER_CRON
    }

    private fun defaultDailyCap(sendType: BatchSendType) = when (sendType) {
        BatchSendType.INTRODUCTION -> DEFAULT_INTRO_DAILY_CAP
        BatchSendType.MATERIAL_REMINDER -> DEFAULT_REMINDER_DAILY_CAP
    }

    private fun defaultRoundSize(sendType: BatchSendType) = when (sendType) {
        BatchSendType.INTRODUCTION -> DEFAULT_INTRO_ROUND_SIZE
        BatchSendType.MATERIAL_REMINDER -> DEFAULT_REMINDER_ROUND_SIZE
    }

    private fun defaultPerMailIntervalMs(sendType: BatchSendType) = when (sendType) {
        BatchSendType.INTRODUCTION -> DEFAULT_INTRO_PER_MAIL_INTERVAL_MS
        BatchSendType.MATERIAL_REMINDER -> DEFAULT_REMINDER_PER_MAIL_INTERVAL_MS
    }

    private fun defaultPerRoundIntervalMs(sendType: BatchSendType) = when (sendType) {
        BatchSendType.INTRODUCTION -> DEFAULT_INTRO_PER_ROUND_INTERVAL_MS
        BatchSendType.MATERIAL_REMINDER -> DEFAULT_REMINDER_PER_ROUND_INTERVAL_MS
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /** INTRODUCTION compat entry — delegates to typed overload. */
    fun getConfig(): BatchSendConfig = getConfig(BatchSendType.INTRODUCTION)

    fun getConfig(sendType: BatchSendType): BatchSendConfig {
        val values = loadAll()
        return BatchSendConfig(
            sendType = sendType,
            autoEnabled = boolValue(values, k(sendType, "autoEnabled"), DEFAULT_AUTO_ENABLED),
            cron = cronValue(values, k(sendType, "cron"), defaultCron(sendType)),
            dailyCap = intValue(values, k(sendType, "dailyCap"), defaultDailyCap(sendType)),
            roundSize = intValue(values, k(sendType, "roundSize"), defaultRoundSize(sendType)),
            perMailIntervalMs = longValue(values, k(sendType, "perMailIntervalMs"), defaultPerMailIntervalMs(sendType)),
            perRoundIntervalMs = longValue(values, k(sendType, "perRoundIntervalMs"), defaultPerRoundIntervalMs(sendType)),
            selfCheckTtlMinutes = intValue(values, k(sendType, "selfCheckTtlMinutes"), DEFAULT_SELF_CHECK_TTL_MINUTES),
            emailDomain = strValue(values, k(sendType, "emailDomain"), DEFAULT_EMAIL_DOMAIN),
            discipline = disciplineValue(values, k(sendType, "discipline"), DEFAULT_DISCIPLINE),
            templateId = nullableLongValue(values, k(sendType, "templateId"))
        )
    }

    /** INTRODUCTION compat entry — delegates to typed overload. */
    fun updateConfig(cmd: BatchSendConfigUpdateRequest): BatchSendConfig =
        updateConfig(cmd, BatchSendType.INTRODUCTION)

    fun updateConfig(cmd: BatchSendConfigUpdateRequest, sendType: BatchSendType): BatchSendConfig {
        validate(cmd, sendType)
        val old = getConfig(sendType)
        val cronChanged = cmd.cron != old.cron
        val enabledChanged = cmd.autoEnabled != old.autoEnabled
        upsert(k(sendType, "autoEnabled"), cmd.autoEnabled.toString())
        upsert(k(sendType, "cron"), cmd.cron)
        upsert(k(sendType, "dailyCap"), cmd.dailyCap.toString())
        upsert(k(sendType, "roundSize"), cmd.roundSize.toString())
        upsert(k(sendType, "perMailIntervalMs"), cmd.perMailIntervalMs.toString())
        upsert(k(sendType, "perRoundIntervalMs"), cmd.perRoundIntervalMs.toString())
        upsert(k(sendType, "selfCheckTtlMinutes"), cmd.selfCheckTtlMinutes.toString())
        upsert(k(sendType, "emailDomain"), cmd.emailDomain)
        upsert(k(sendType, "discipline"), cmd.discipline)
        upsert(k(sendType, "templateId"), cmd.templateId?.toString() ?: "")
        if (cronChanged || enabledChanged) {
            eventPublisher.publishEvent(BatchSendCronChangedEvent(old.cron, cmd.cron))
        }
        return getConfig(sendType)
    }

    /** INTRODUCTION compat entry — delegates to typed overload. */
    fun setAutoEnabled(enabled: Boolean): BatchSendConfig =
        setAutoEnabled(enabled, BatchSendType.INTRODUCTION)

    fun setAutoEnabled(enabled: Boolean, sendType: BatchSendType): BatchSendConfig {
        val values = loadAll()
        val oldEnabled = boolValue(values, k(sendType, "autoEnabled"), DEFAULT_AUTO_ENABLED)
        upsert(k(sendType, "autoEnabled"), enabled.toString())
        if (enabled != oldEnabled) {
            val cron = cronValue(values, k(sendType, "cron"), defaultCron(sendType))
            eventPublisher.publishEvent(BatchSendCronChangedEvent(cron, cron))
        }
        return getConfig(sendType)
    }

    /** INTRODUCTION compat entry — delegates to typed overload. */
    fun getRuntimeStatus(): BatchSendRuntimeState = getRuntimeStatus(BatchSendType.INTRODUCTION)

    fun getRuntimeStatus(sendType: BatchSendType): BatchSendRuntimeState {
        val values = loadAll()
        return BatchSendRuntimeState(
            status = strValue(values, k(sendType, "runtimeStatus"), DEFAULT_RUNTIME_STATUS),
            mode = strValue(values, k(sendType, "runtimeMode"), DEFAULT_RUNTIME_MODE),
            pauseReason = strValue(values, k(sendType, "pauseReason"), DEFAULT_PAUSE_REASON)
        )
    }

    /** INTRODUCTION compat entry — delegates to typed overload. */
    fun setRuntimeStatus(status: String, mode: String, pauseReason: String) =
        setRuntimeStatus(status, mode, pauseReason, BatchSendType.INTRODUCTION)

    fun setRuntimeStatus(status: String, mode: String, pauseReason: String, sendType: BatchSendType) {
        upsert(k(sendType, "runtimeStatus"), status)
        upsert(k(sendType, "runtimeMode"), mode)
        upsert(k(sendType, "pauseReason"), pauseReason)
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    private fun validate(cmd: BatchSendConfigUpdateRequest, sendType: BatchSendType) {
        require(cmd.roundSize >= 1) { "roundSize must be >= 1" }
        require(cmd.dailyCap >= cmd.roundSize) { "dailyCap must be >= roundSize" }
        require(cmd.perMailIntervalMs >= 0) { "perMailIntervalMs must be >= 0" }
        require(cmd.perRoundIntervalMs >= 0) { "perRoundIntervalMs must be >= 0" }
        require(cmd.selfCheckTtlMinutes >= 1) { "selfCheckTtlMinutes must be >= 1" }
        require(cmd.cron.isNotBlank()) { "cron must not be blank" }
        CronExpression.parse(cmd.cron)
        require(cmd.discipline in ALLOWED_DISCIPLINES) { "discipline must be one of $ALLOWED_DISCIPLINES" }
        cmd.templateId?.let { require(it > 0) { "templateId must be > 0" } }
        if (sendType == BatchSendType.MATERIAL_REMINDER) {
            requireNotNull(cmd.templateId) { "MATERIAL_REMINDER config requires a templateId" }
        }
    }

    // ── Persistence helpers ────────────────────────────────────────────────────

    private fun upsert(key: String, value: String) {
        val existing = repository.findBySettingKey(key)
        val toSave = BatchSendSetting(
            id = existing?.id,
            settingKey = key,
            settingValue = value,
            updatedAt = LocalDateTime.now()
        )
        repository.save(toSave)
    }

    private fun loadAll(): Map<String, String> =
        try {
            repository.findAll().associate { it.settingKey to it.settingValue }
        } catch (e: Exception) {
            log.warn("Failed to load batch_send_setting rows, using defaults", e)
            emptyMap()
        }

    // ── Value extractors ───────────────────────────────────────────────────────

    private fun boolValue(values: Map<String, String>, key: String, default: Boolean): Boolean =
        values[key]?.toBooleanStrictOrNull() ?: default

    private fun intValue(values: Map<String, String>, key: String, default: Int): Int =
        values[key]?.toIntOrNull() ?: default

    private fun longValue(values: Map<String, String>, key: String, default: Long): Long =
        values[key]?.toLongOrNull() ?: default

    private fun strValue(values: Map<String, String>, key: String, default: String): String =
        values[key] ?: default

    private fun disciplineValue(values: Map<String, String>, key: String, default: String): String {
        val value = values[key] ?: return default
        return if (value in ALLOWED_DISCIPLINES) value else default
    }

    private fun nullableLongValue(values: Map<String, String>, key: String): Long? {
        val value = values[key] ?: return null
        if (value.isBlank()) return null
        return value.toLongOrNull()?.takeIf { it > 0 }
    }

    private fun cronValue(values: Map<String, String>, key: String, default: String): String {
        val value = values[key] ?: return default
        return try {
            CronExpression.parse(value)
            value
        } catch (e: IllegalArgumentException) {
            default
        }
    }

    private companion object {
        const val DEFAULT_AUTO_ENABLED = false

        const val DEFAULT_INTRO_CRON = "0 0 0 * * ?"
        const val DEFAULT_INTRO_DAILY_CAP = 1000
        const val DEFAULT_INTRO_ROUND_SIZE = 50
        const val DEFAULT_INTRO_PER_MAIL_INTERVAL_MS = 1000L
        const val DEFAULT_INTRO_PER_ROUND_INTERVAL_MS = 60000L

        const val DEFAULT_REMINDER_CRON = "0 0 8 * * ?"
        const val DEFAULT_REMINDER_DAILY_CAP = 60
        const val DEFAULT_REMINDER_ROUND_SIZE = 30
        const val DEFAULT_REMINDER_PER_MAIL_INTERVAL_MS = 3000L
        const val DEFAULT_REMINDER_PER_ROUND_INTERVAL_MS = 120000L

        const val DEFAULT_SELF_CHECK_TTL_MINUTES = 30
        const val DEFAULT_RUNTIME_STATUS = "IDLE"
        const val DEFAULT_RUNTIME_MODE = "NONE"
        const val DEFAULT_PAUSE_REASON = ""
        const val DEFAULT_EMAIL_DOMAIN = ""
        const val DEFAULT_DISCIPLINE = ""
        val ALLOWED_DISCIPLINES = setOf("", "STEM", "HUMANITIES")
    }
}

data class BatchSendConfig(
    val sendType: BatchSendType = BatchSendType.INTRODUCTION,
    val autoEnabled: Boolean,
    val cron: String,
    val dailyCap: Int,
    val roundSize: Int,
    val perMailIntervalMs: Long,
    val perRoundIntervalMs: Long,
    val selfCheckTtlMinutes: Int,
    val emailDomain: String = "",
    val discipline: String = "",
    val templateId: Long? = null
)

data class BatchSendConfigUpdateRequest(
    val autoEnabled: Boolean,
    val cron: String,
    val dailyCap: Int,
    val roundSize: Int,
    val perMailIntervalMs: Long,
    val perRoundIntervalMs: Long,
    val selfCheckTtlMinutes: Int,
    val emailDomain: String = "",
    val discipline: String = "",
    val templateId: Long? = null
)

data class BatchSendRuntimeState(
    val status: String,
    val mode: String,
    val pauseReason: String
)
