package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.stereotype.Service
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

@Service
class SmtpSenderFactory {
    private data class CacheKey(val accountCode: String)
    private data class CacheEntry(
        val sender: JavaMailSenderImpl,
        val configFingerprint: String
    )

    private val cache = ConcurrentHashMap<CacheKey, CacheEntry>()

    fun getSender(account: MailSenderAccount): JavaMailSenderImpl {
        val key = CacheKey(account.accountCode)
        val fingerprint = configFingerprint(account)
        return cache.compute(key) { _, existing ->
            if (existing != null && existing.configFingerprint == fingerprint) {
                existing
            } else {
                CacheEntry(buildSender(account), fingerprint)
            }
        }!!.sender
    }

    fun evict(accountCode: String) {
        cache.remove(CacheKey(accountCode))
    }

    fun evictAll() {
        cache.clear()
    }

    private fun configFingerprint(account: MailSenderAccount): String =
        "${account.smtpHost}:${account.smtpPort}:${account.smtpUsername}:${account.smtpPassword}"

    private fun buildSender(account: MailSenderAccount): JavaMailSenderImpl =
        JavaMailSenderImpl().apply {
            host = account.smtpHost
            port = account.smtpPort
            username = account.smtpUsername
            password = account.smtpPassword
            javaMailProperties = smtpProperties(account.smtpPort)
        }

    private fun smtpProperties(port: Int): Properties =
        Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.auth.mechanisms", "LOGIN")
            put("mail.smtp.connectiontimeout", "10000")
            put("mail.smtp.timeout", "10000")
            put("mail.smtp.writetimeout", "10000")
            if (port == 465) {
                put("mail.smtp.ssl.enable", "true")
            } else {
                put("mail.smtp.starttls.enable", "true")
            }
        }
}
