package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.BatchSendSetting
import com.weibo.talentintroduction.campaign.event.BatchSendCronChangedEvent
import com.weibo.talentintroduction.campaign.repository.BatchSendSettingRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class BatchSendSettingService(
    private val repository: BatchSendSettingRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(BatchSendSettingService::class.java)

    fun getConfig(): BatchSendConfig {
        val values = loadAll()
        return BatchSendConfig(
            autoEnabled = boolValue(values, KEY_AUTO_ENABLED, DEFAULT_AUTO_ENABLED),
            cron = cronValue(values, KEY_CRON, DEFAULT_CRON),
            dailyCap = intValue(values, KEY_DAILY_CAP, DEFAULT_DAILY_CAP),
            roundSize = intValue(values, KEY_ROUND_SIZE, DEFAULT_ROUND_SIZE),
            perMailIntervalMs = longValue(values, KEY_PER_MAIL_INTERVAL_MS, DEFAULT_PER_MAIL_INTERVAL_MS),
            perRoundIntervalMs = longValue(values, KEY_PER_ROUND_INTERVAL_MS, DEFAULT_PER_ROUND_INTERVAL_MS),
            selfCheckTtlMinutes = intValue(values, KEY_SELF_CHECK_TTL_MINUTES, DEFAULT_SELF_CHECK_TTL_MINUTES),
            emailDomain = strValue(values, KEY_EMAIL_DOMAIN, DEFAULT_EMAIL_DOMAIN)
        )
    }

    fun updateConfig(cmd: BatchSendConfigUpdateRequest): BatchSendConfig {
        validate(cmd)
        val oldCron = getConfig().cron
        upsert(KEY_AUTO_ENABLED, cmd.autoEnabled.toString())
        upsert(KEY_CRON, cmd.cron)
        upsert(KEY_DAILY_CAP, cmd.dailyCap.toString())
        upsert(KEY_ROUND_SIZE, cmd.roundSize.toString())
        upsert(KEY_PER_MAIL_INTERVAL_MS, cmd.perMailIntervalMs.toString())
        upsert(KEY_PER_ROUND_INTERVAL_MS, cmd.perRoundIntervalMs.toString())
        upsert(KEY_SELF_CHECK_TTL_MINUTES, cmd.selfCheckTtlMinutes.toString())
        upsert(KEY_EMAIL_DOMAIN, cmd.emailDomain)
        if (cmd.cron != oldCron) {
            eventPublisher.publishEvent(BatchSendCronChangedEvent(oldCron, cmd.cron))
        }
        return getConfig()
    }

    fun setAutoEnabled(enabled: Boolean): BatchSendConfig {
        upsert(KEY_AUTO_ENABLED, enabled.toString())
        return getConfig()
    }

    fun getRuntimeStatus(): BatchSendRuntimeState {
        val values = loadAll()
        return BatchSendRuntimeState(
            status = strValue(values, KEY_RUNTIME_STATUS, DEFAULT_RUNTIME_STATUS),
            mode = strValue(values, KEY_RUNTIME_MODE, DEFAULT_RUNTIME_MODE),
            pauseReason = strValue(values, KEY_PAUSE_REASON, DEFAULT_PAUSE_REASON)
        )
    }

    fun setRuntimeStatus(status: String, mode: String, pauseReason: String) {
        upsert(KEY_RUNTIME_STATUS, status)
        upsert(KEY_RUNTIME_MODE, mode)
        upsert(KEY_PAUSE_REASON, pauseReason)
    }

    private fun validate(cmd: BatchSendConfigUpdateRequest) {
        require(cmd.roundSize >= 1) { "roundSize must be >= 1" }
        require(cmd.dailyCap >= cmd.roundSize) { "dailyCap must be >= roundSize" }
        require(cmd.perMailIntervalMs >= 0) { "perMailIntervalMs must be >= 0" }
        require(cmd.perRoundIntervalMs >= 0) { "perRoundIntervalMs must be >= 0" }
        require(cmd.selfCheckTtlMinutes >= 1) { "selfCheckTtlMinutes must be >= 1" }
        require(cmd.cron.isNotBlank()) { "cron must not be blank" }
        CronExpression.parse(cmd.cron)
    }

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

    private fun boolValue(values: Map<String, String>, key: String, default: Boolean): Boolean =
        values[key]?.toBooleanStrictOrNull() ?: default

    private fun intValue(values: Map<String, String>, key: String, default: Int): Int =
        values[key]?.toIntOrNull() ?: default

    private fun longValue(values: Map<String, String>, key: String, default: Long): Long =
        values[key]?.toLongOrNull() ?: default

    private fun strValue(values: Map<String, String>, key: String, default: String): String =
        values[key] ?: default

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
        const val KEY_AUTO_ENABLED = "batchSend.autoEnabled"
        const val KEY_CRON = "batchSend.cron"
        const val KEY_DAILY_CAP = "batchSend.dailyCap"
        const val KEY_ROUND_SIZE = "batchSend.roundSize"
        const val KEY_PER_MAIL_INTERVAL_MS = "batchSend.perMailIntervalMs"
        const val KEY_PER_ROUND_INTERVAL_MS = "batchSend.perRoundIntervalMs"
        const val KEY_SELF_CHECK_TTL_MINUTES = "batchSend.selfCheckTtlMinutes"
        const val KEY_RUNTIME_STATUS = "batchSend.runtimeStatus"
        const val KEY_RUNTIME_MODE = "batchSend.runtimeMode"
        const val KEY_PAUSE_REASON = "batchSend.pauseReason"
        const val KEY_EMAIL_DOMAIN = "batchSend.emailDomain"

        const val DEFAULT_AUTO_ENABLED = false
        const val DEFAULT_CRON = "0 0 0 * * ?"
        const val DEFAULT_DAILY_CAP = 1000
        const val DEFAULT_ROUND_SIZE = 50
        const val DEFAULT_PER_MAIL_INTERVAL_MS = 1000L
        const val DEFAULT_PER_ROUND_INTERVAL_MS = 60000L
        const val DEFAULT_SELF_CHECK_TTL_MINUTES = 30
        const val DEFAULT_RUNTIME_STATUS = "IDLE"
        const val DEFAULT_RUNTIME_MODE = "NONE"
        const val DEFAULT_PAUSE_REASON = ""
        const val DEFAULT_EMAIL_DOMAIN = ""
    }
}

data class BatchSendConfig(
    val autoEnabled: Boolean,
    val cron: String,
    val dailyCap: Int,
    val roundSize: Int,
    val perMailIntervalMs: Long,
    val perRoundIntervalMs: Long,
    val selfCheckTtlMinutes: Int,
    val emailDomain: String = ""
)

data class BatchSendConfigUpdateRequest(
    val autoEnabled: Boolean,
    val cron: String,
    val dailyCap: Int,
    val roundSize: Int,
    val perMailIntervalMs: Long,
    val perRoundIntervalMs: Long,
    val selfCheckTtlMinutes: Int,
    val emailDomain: String = ""
)

data class BatchSendRuntimeState(
    val status: String,
    val mode: String,
    val pauseReason: String
)
