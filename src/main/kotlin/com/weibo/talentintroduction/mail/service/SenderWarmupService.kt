package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.WarmupProperties
import com.weibo.talentintroduction.config.WarmupStep
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime

enum class AccountDailyState {
    SENDABLE,
    WARMUP_LIMIT_REACHED,
    DAILY_LIMIT_REACHED,
    PAUSED_FAULT,
    DISABLED_OR_SIMULATOR
}

@Service
class SenderWarmupService(
    private val props: WarmupProperties,
    private val objectMapper: ObjectMapper
) {
    fun effectiveDailyLimit(account: MailSenderAccount, now: LocalDateTime = LocalDateTime.now()): Int {
        if (account.warmupEnabled == false) {
            return account.dailySendLimit
        }
        if (account.warmupEnabled == true) {
            val startedAt = account.warmupStartedAt ?: return account.dailySendLimit
            val steps = parseSteps(account.warmupStepsJson) ?: props.steps
            return minOf(account.dailySendLimit, rampLimit(startedAt, now, steps))
        }
        if (!props.enabled) {
            return account.dailySendLimit
        }
        val created = account.createdAt ?: return account.dailySendLimit
        return minOf(account.dailySendLimit, rampLimit(created, now, props.steps))
    }

    fun validateWarmupStepsJson(json: String?) {
        if (json.isNullOrBlank()) {
            return
        }
        parseSteps(json) ?: throw IllegalArgumentException("warmupStepsJson must be a valid JSON array of warmup steps")
    }

    fun dailyState(account: MailSenderAccount, now: LocalDateTime = LocalDateTime.now()): AccountDailyState {
        if (!account.enabled || account.accountCode == MailSenderAccountService.SIMULATOR_ACCOUNT_CODE) {
            return AccountDailyState.DISABLED_OR_SIMULATOR
        }
        if (account.autoSendPaused) {
            return AccountDailyState.PAUSED_FAULT
        }
        val eff = effectiveDailyLimit(account, now)
        if (account.todaySentCount >= eff) {
            return if (eff < account.dailySendLimit) {
                AccountDailyState.WARMUP_LIMIT_REACHED
            } else {
                AccountDailyState.DAILY_LIMIT_REACHED
            }
        }
        return AccountDailyState.SENDABLE
    }

    fun remainingCapacity(account: MailSenderAccount, now: LocalDateTime = LocalDateTime.now()): Int {
        if (dailyState(account, now) != AccountDailyState.SENDABLE) {
            return 0
        }
        return maxOf(0, effectiveDailyLimit(account, now) - account.todaySentCount)
    }

    fun isWarmupActive(account: MailSenderAccount, now: LocalDateTime = LocalDateTime.now()): Boolean {
        if (account.warmupEnabled != true || account.warmupStartedAt == null) {
            return false
        }
        return effectiveDailyLimit(account, now) < account.dailySendLimit
    }

    private fun rampLimit(startedAt: LocalDateTime, now: LocalDateTime, steps: List<WarmupStep>): Int {
        val ageDays = Duration.between(startedAt, now).toDays().toInt() + 1
        return steps.filter { it.dayFrom <= ageDays }.maxOfOrNull { it.limit }
            ?: steps.minOf { it.limit }
    }

    private fun parseSteps(json: String?): List<WarmupStep>? {
        if (json.isNullOrBlank()) {
            return null
        }
        return try {
            objectMapper.readValue(json, WARMUP_STEPS_TYPE)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val WARMUP_STEPS_TYPE = object : TypeReference<List<WarmupStep>>() {}
    }
}
