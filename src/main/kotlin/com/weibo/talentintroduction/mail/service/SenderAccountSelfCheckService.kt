package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.service.BatchSendSettingService
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import javax.mail.Message

interface SelfCheckProbeSender {
    fun sendProbe(account: MailSenderAccount)
}

@Service
class DefaultSelfCheckProbeSender(
    private val smtpSenderFactory: SmtpSenderFactory
) : SelfCheckProbeSender {
    override fun sendProbe(account: MailSenderAccount) {
        val sender = smtpSenderFactory.getSender(account)
        val message = sender.createMimeMessage()
        message.setFrom(account.senderEmail)
        message.setRecipients(Message.RecipientType.TO, account.senderEmail)
        message.subject = "[self-check] ${account.accountCode} ${System.currentTimeMillis()}"
        message.setText("self-check probe", Charsets.UTF_8.name())
        sender.send(message)
    }
}

@Service
class SenderAccountSelfCheckService(
    private val repository: MailSenderAccountRepository,
    private val configService: BatchSendSettingService,
    private val probeSender: SelfCheckProbeSender,
    private val timeProvider: () -> LocalDateTime = { LocalDateTime.now() }
) {
    private val log = LoggerFactory.getLogger(SenderAccountSelfCheckService::class.java)
    private val cache = ConcurrentHashMap<String, SelfCheckCacheEntry>()

    fun checkSendable(account: MailSenderAccount): SelfCheckResult {
        val code = account.accountCode
        val ttlMinutes = configService.getConfig().selfCheckTtlMinutes
        val cached = cache[code]
        if (cached != null && !cached.isExpired(ttlMinutes, timeProvider())) {
            return SelfCheckResult(code, cached.passed, cached.message, fromCache = true)
        }
        return runProbe(account, ttlMinutes)
    }

    fun invalidate(accountCode: String) {
        cache.remove(accountCode)
    }

    private fun runProbe(account: MailSenderAccount, ttlMinutes: Int): SelfCheckResult {
        val code = account.accountCode
        return try {
            probeSender.sendProbe(account)
            cache[code] = SelfCheckCacheEntry(passed = true, checkedAt = timeProvider(), message = null)
            SelfCheckResult(code, passed = true, message = null, fromCache = false)
        } catch (e: Exception) {
            val reason = "SELF_CHECK_FAILED:${(e.message ?: e.javaClass.simpleName).take(200)}"
            log.warn("Self-check failed for account {}: {}", code, reason)
            cache[code] = SelfCheckCacheEntry(passed = false, checkedAt = timeProvider(), message = reason)
            repository.pauseAutoSend(code, reason, timeProvider())
            SelfCheckResult(code, passed = false, message = reason, fromCache = false)
        }
    }
}

data class SelfCheckResult(
    val accountCode: String,
    val passed: Boolean,
    val message: String?,
    val fromCache: Boolean
)

private data class SelfCheckCacheEntry(
    val passed: Boolean,
    val checkedAt: LocalDateTime,
    val message: String?
) {
    fun isExpired(ttlMinutes: Int, now: LocalDateTime): Boolean =
        !checkedAt.plusMinutes(ttlMinutes.toLong()).isAfter(now)
}
