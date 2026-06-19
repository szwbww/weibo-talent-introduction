package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class BounceRateMonitorService(
    private val bounceRecordRepository: BounceRecordRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val mailSenderAccountService: MailSenderAccountService
) {
    private val log = LoggerFactory.getLogger(BounceRateMonitorService::class.java)

    fun checkAndPause(
        accountCode: String,
        windowDays: Int = DEFAULT_WINDOW_DAYS,
        threshold: Double = DEFAULT_THRESHOLD
    ): Double {
        val since = LocalDateTime.now().minusDays(windowDays.toLong())
        val hardBounces = bounceRecordRepository.countHardBouncesSince(accountCode, since)
        val sentCount = mailRecordRepository.countSentByAccountSince(accountCode, since)

        if (sentCount < MIN_SAMPLE_SIZE) return -1.0

        val rate = hardBounces.toDouble() / sentCount
        if (rate > threshold) {
            log.warn(
                "Bounce rate for {} is {:.2f}% (threshold {:.2f}%), pausing account",
                accountCode,
                rate * 100,
                threshold * 100
            )
            mailSenderAccountService.pauseAutoSend(
                accountCode,
                "BOUNCE_RATE_HIGH:${String.format("%.2f", rate * 100)}%"
            )
        }
        return rate
    }

    companion object {
        const val DEFAULT_WINDOW_DAYS = 7
        const val DEFAULT_THRESHOLD = 0.05
        const val MIN_SAMPLE_SIZE = 20
    }
}
