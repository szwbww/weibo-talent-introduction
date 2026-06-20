package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.WarmupProperties
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime

@Service
class SenderWarmupService(
    private val props: WarmupProperties
) {
    fun effectiveDailyLimit(account: MailSenderAccount, now: LocalDateTime = LocalDateTime.now()): Int {
        if (!props.enabled) return account.dailySendLimit
        val created = account.createdAt ?: return account.dailySendLimit
        val ageDays = Duration.between(created, now).toDays().toInt() + 1
        val ramp = props.steps.filter { it.dayFrom <= ageDays }.maxOfOrNull { it.limit }
            ?: props.steps.minOf { it.limit }
        return minOf(account.dailySendLimit, ramp)
    }
}
