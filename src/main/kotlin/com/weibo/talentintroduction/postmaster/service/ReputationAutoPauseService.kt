package com.weibo.talentintroduction.postmaster.service

import com.weibo.talentintroduction.config.PostmasterProperties
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.postmaster.domain.DomainReputationHistory
import com.weibo.talentintroduction.postmaster.repository.DomainReputationHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "talent-introduction.postmaster", name = ["enabled"], havingValue = "true")
class ReputationAutoPauseService(
    private val properties: PostmasterProperties,
    private val historyRepository: DomainReputationHistoryRepository,
    private val mailSenderAccountService: MailSenderAccountService,
    private val mailSenderAccountRepository: MailSenderAccountRepository
) {
    private val log = LoggerFactory.getLogger(ReputationAutoPauseService::class.java)

    fun checkAndAct() {
        properties.domains.forEach { domain ->
            checkDomain(domain)
        }
    }

    internal fun checkDomain(domain: String) {
        val latest = historyRepository.findFirstByDomainOrderByReportDateDesc(domain) ?: return
        val accountsForDomain = mailSenderAccountRepository.findAllByEnabledTrue()
            .filter { extractDomain(it.senderEmail) == domain.lowercase() }

        if (shouldPause(latest)) {
            accountsForDomain
                .filter { !it.autoSendPaused }
                .forEach { account ->
                    val reason = buildPauseReason(latest)
                    log.warn("Pausing account {} due to reputation: {}", account.accountCode, reason)
                    mailSenderAccountService.pauseAutoSend(account.accountCode, reason)
                }
            return
        }

        if (shouldResume(domain)) {
            accountsForDomain
                .filter { it.autoSendPaused && it.autoSendPausedReason?.startsWith(REPUTATION_PREFIX) == true }
                .forEach { account ->
                    log.info("Resuming account {} after reputation recovery", account.accountCode)
                    mailSenderAccountService.resumeAutoSend(account.accountCode)
                }
        }
    }

    internal fun shouldPause(latest: DomainReputationHistory): Boolean {
        val spamRate = latest.spamRate ?: return false
        return spamRate >= properties.pauseThresholdSpamRate
    }

    internal fun shouldResume(domain: String): Boolean {
        val days = properties.resumeConsecutiveDays
        val records = historyRepository.findByDomainOrderByReportDateDesc(domain, days)
        if (records.size < days) {
            return false
        }
        val recent = records.sortedByDescending { it.reportDate }.take(days)
        for (index in 0 until recent.size - 1) {
            if (recent[index].reportDate.minusDays(1) != recent[index + 1].reportDate) {
                return false
            }
        }
        return recent.all { record ->
            val spamRate = record.spamRate ?: return false
            spamRate < properties.resumeThresholdSpamRate
        }
    }

    internal fun buildPauseReason(latest: DomainReputationHistory): String {
        val spamRate = latest.spamRate ?: return "${REPUTATION_PREFIX}unknown"
        return "${REPUTATION_PREFIX}spam_rate=${String.format("%.1f", spamRate * 100)}%"
    }

    companion object {
        const val REPUTATION_PREFIX = "REPUTATION:"

        fun extractDomain(senderEmail: String): String {
            val atIndex = senderEmail.lastIndexOf('@')
            if (atIndex < 0 || atIndex == senderEmail.length - 1) {
                return senderEmail.lowercase()
            }
            return senderEmail.substring(atIndex + 1).lowercase()
        }
    }
}
