package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 账号级硬退统计快照（I-1/I-4）：一次 [BounceRateMonitorService.getStats] 只计算一次，
 * 分子分母复用同一 cutoff。`rate` 为小数比率（不是百分数，也不封顶 100%，因为分子分母可能不是同一批邮件）；
 * 样本不足时 `rate` 为 null，禁止由调用方解释成 0。
 */
data class HardBounceStats(
    val hardBounceCount: Long,
    val sentCount: Long,
    val rate: Double?,
    val sampleSufficient: Boolean,
    val windowDays: Int,
    val high: Boolean
)

@Service
class BounceRateMonitorService(
    private val bounceRecordRepository: BounceRecordRepository,
    private val mailRecordRepository: MailRecordRepository
) {
    private val log = LoggerFactory.getLogger(BounceRateMonitorService::class.java)

    /**
     * I-1：一次调用只发两次 COUNT，且复用同一 `since = now - windowDays`；分子按 HARD + received_at，
     * 分母按 OUTBOUND + SENT + sent_at，不用 todaySentCount 替代。
     * I-2：`sentCount < MIN_SAMPLE_SIZE` → `rate = null`、`sampleSufficient = false`、`high = false`；
     * 否则 `rate = hard/sent`、`high = rate > 0.05`。
     */
    fun getStats(accountCode: String, windowDays: Int = DEFAULT_WINDOW_DAYS): HardBounceStats {
        val since = LocalDateTime.now().minusDays(windowDays.toLong())
        val hardBounceCount = bounceRecordRepository.countHardBouncesSince(accountCode, since)
        val sentCount = mailRecordRepository.countSentByAccountSince(accountCode, since)
        val sampleSufficient = sentCount >= MIN_SAMPLE_SIZE
        val rate = if (sampleSufficient) hardBounceCount.toDouble() / sentCount.toDouble() else null
        return HardBounceStats(
            hardBounceCount = hardBounceCount,
            sentCount = sentCount,
            rate = rate,
            sampleSufficient = sampleSufficient,
            windowDays = windowDays,
            high = rate != null && rate > DEFAULT_THRESHOLD
        )
    }

    /**
     * I-3：旧调用方（AutoMailReplyService 日志路径）的兼容入口，签名与语义不变：低样本仍返回 [NO_SAMPLE_RATE]，
     * 其余复用 [getStats] 的同一次计算。
     */
    fun calculateHardBounceRate(
        accountCode: String,
        windowDays: Int = DEFAULT_WINDOW_DAYS
    ): Double = getStats(accountCode, windowDays).rate ?: NO_SAMPLE_RATE

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

        /** I-3：低样本兼容返回值，旧调用方据此判定「无样本」。 */
        const val NO_SAMPLE_RATE = -1.0
    }
}
