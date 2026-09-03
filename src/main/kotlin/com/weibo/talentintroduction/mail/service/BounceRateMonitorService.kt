package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class BounceRateMonitorService(
    private val bounceRecordRepository: BounceRecordRepository,
    private val mailRecordRepository: MailRecordRepository
) {
    private val log = LoggerFactory.getLogger(BounceRateMonitorService::class.java)

    fun calculateHardBounceRate(
        accountCode: String,
        windowDays: Int = DEFAULT_WINDOW_DAYS
    ): Double {
        val since = LocalDateTime.now().minusDays(windowDays.toLong())
        val hardBounces = bounceRecordRepository.countHardBouncesSince(accountCode, since)
        val sentCount = mailRecordRepository.countSentByAccountSince(accountCode, since)
        if (sentCount < MIN_SAMPLE_SIZE) return -1.0
        return hardBounces.toDouble() / sentCount
    }

    fun isHardBounceRateHigh(accountCode: String): Boolean =
        calculateHardBounceRate(accountCode) > DEFAULT_THRESHOLD

    fun checkAndWarn(
        accountCode: String,
        windowDays: Int = DEFAULT_WINDOW_DAYS,
        threshold: Double = DEFAULT_THRESHOLD
    ): Double {
        val rate = calculateHardBounceRate(accountCode, windowDays)
        if (rate > threshold) {
            log.warn(
                "Hard bounce rate high for {}: {}% > {}%; warning only, automatic sending continues",
                accountCode,
                String.format("%.2f", rate * 100),
                String.format("%.2f", threshold * 100)
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
