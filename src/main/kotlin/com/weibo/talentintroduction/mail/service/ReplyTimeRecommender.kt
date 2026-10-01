package com.weibo.talentintroduction.mail.service

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 回复时间推荐纯计算器（plan 02 / c2，T-2）。
 *
 * 只做数学：输入去重后的样本 `Instant`、专家生效时区与**一次请求取定的** `now`，
 * 输出窗口与解释计数。不访问数据库、系统时钟、网络，不产生任何副作用（I-6）；
 * 相同 now/样本/时区必然得到相同输出（I-4）。全部参数常量集中在本文件，
 * 不提供用户配置面板。
 *
 * 公式（与计划 T-2 逐字一致）：
 * 1. 一天 [BINS_PER_DAY] 个半小时桶，`bin = hour*2 + minute/30`，按样本**当时**目标时区
 *    的真实当地时刻落桶（夏令时规则随时间变化，不是固定偏移）。
 * 2. 同一当地日 d 有 N_d 条样本时，每条权重 `min(1, 2/N_d) * 2^(-ageDays/30)`；
 *    日上限在衰减前施加，避免一日密集对话压倒长期规律。
 * 3. 桶权重和 H 循环平滑为 S，归一化为 P。
 * 4. 工作时间先验 U（桶 16..33 各 1/18）；`E = sum_d(max_{i in d} 2^(-ageDays_i/30))`，
 *    `alpha = min(0.9, E/(E+2))`，最终 `Q = alpha*P + (1-alpha)*U`。
 * 5. 回复日数 D < [COLD_START_MIN_REPLY_DAYS] 输出完整工作时间 08:00–17:00
 *    （mode=WORK_HOURS）；否则取连续 [PATTERN_WINDOW_BINS] 桶 Q 之和最大的起点
 *    （容差 [TIE_TOLERANCE]，平局取更早的当地起始分钟），允许跨午夜、允许工作时间外。
 * 6. 端点是目标时区的当地日期/时间经 `atZone` 转换（DST 缺口顺延、重叠取较早 offset）；
 *    起点不早于 now，跨日不丢日期，区间必须为正长度。
 */
object ReplyTimeRecommender {

    /** 一天 48 个半小时桶。 */
    const val BINS_PER_DAY = 48

    /** 工作时间起点桶：08:00。 */
    const val WORK_START_BIN = 16

    /** 工作时间窗口长度（桶）：18 桶 = 9 小时（08:00–17:00）。 */
    const val WORK_WINDOW_BINS = 18

    /** 习惯模式窗口长度（桶）：4 桶 = 2 小时。 */
    const val PATTERN_WINDOW_BINS = 4

    /** 回复日数达到该值才启用 REPLY_PATTERN；D<3 一律 WORK_HOURS。 */
    const val COLD_START_MIN_REPLY_DAYS = 3

    /** 衰减半衰期（天）。 */
    const val HALF_LIFE_DAYS = 30.0

    /** 同一当地日的权重上限（日上限，衰减前施加）。 */
    const val DAILY_WEIGHT_CAP = 2.0

    /** 先验强度：alpha = min(0.9, E/(E+2))。 */
    const val PRIOR_STRENGTH = 2.0

    /** alpha 上限：任何时候都保留至少 10% 的工作时间先验。 */
    const val MAX_PATTERN_WEIGHT = 0.9

    /** 峰值窗口比较容差；差值不超过容差视为平局，取更早起点。 */
    const val TIE_TOLERANCE = 1e-12

    /** DST 等极端转换压掉整个区间时，最多顺延的当地天数。 */
    const val MAX_START_RETRY_DAYS = 3

    /** recentSamples 条数上限。 */
    const val MAX_RECENT_SAMPLES = 8

    /** 北京时间时区（API 的 beijing* 投影与 `received_at` DATETIME 的解释口径）。 */
    val BEIJING_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    private const val SECONDS_PER_DAY = 86_400.0
    private const val SMOOTH_SIDE = 0.2
    private const val SMOOTH_CENTER = 0.6

    /**
     * 推荐窗口 + 解释计数。窗口端点保留时区信息，格式化由服务层负责（I-2/I-5）。
     */
    fun recommend(samples: List<Instant>, zone: ZoneId, now: Instant): RecommendedTimingWindow {
        val profile = profile(samples, zone, now)
        val workHours = profile.replyDayCount < COLD_START_MIN_REPLY_DAYS
        val startBin = if (workHours) WORK_START_BIN else bestWindowStart(profile.blended)
        val spanMinutes = if (workHours) WORK_WINDOW_BINS * 30L else PATTERN_WINDOW_BINS * 30L
        val startLocalTime = LocalTime.of(startBin / 2, (startBin % 2) * 30)
        val window = resolveWindow(now.atZone(zone).toLocalDate(), startLocalTime, spanMinutes, zone, now)
        return RecommendedTimingWindow(
            mode = if (workHours) TimingMode.WORK_HOURS else TimingMode.REPLY_PATTERN,
            localStart = window.first,
            localEnd = window.second,
            beijingStart = window.first.toInstant().atZone(BEIJING_ZONE),
            beijingEnd = window.second.toInstant().atZone(BEIJING_ZONE),
            sampleCount = profile.sampleCount,
            replyDayCount = profile.replyDayCount,
            recentSamples = recentSamples(samples, zone, now)
        )
    }

    /**
     * 直方图/先验/混合的纯计算分解（step 1–4）。窗口选择不在此函数内；
     * 独立暴露只为让公式的每个中间量都可被直接断言（I-4）。
     */
    fun profile(samples: List<Instant>, zone: ZoneId, now: Instant): ReplyTimeProfile {
        val usable = usableSamples(samples, now)
        val perDay = usable.groupBy { it.atZone(zone).toLocalDate() }
        val histogram = DoubleArray(BINS_PER_DAY)
        perDay.values.forEach { day ->
            val perSample = minOf(1.0, DAILY_WEIGHT_CAP / day.size)
            day.forEach { instant ->
                val local = instant.atZone(zone)
                histogram[local.hour * 2 + local.minute / 30] += perSample * decayWeight(ageDays(instant, now))
            }
        }
        val smoothed = DoubleArray(BINS_PER_DAY) { bin ->
            SMOOTH_SIDE * histogram[(bin - 1 + BINS_PER_DAY) % BINS_PER_DAY] +
                SMOOTH_CENTER * histogram[bin] +
                SMOOTH_SIDE * histogram[(bin + 1) % BINS_PER_DAY]
        }
        val smoothedTotal = smoothed.sum()
        val normalized = DoubleArray(BINS_PER_DAY) {
            if (smoothedTotal == 0.0) 0.0 else smoothed[it] / smoothedTotal
        }
        val effectiveDayWeight = perDay.values.sumOf { day -> day.maxOf { decayWeight(ageDays(it, now)) } }
        val alpha = minOf(MAX_PATTERN_WEIGHT, effectiveDayWeight / (effectiveDayWeight + PRIOR_STRENGTH))
        val blended = DoubleArray(BINS_PER_DAY) { bin ->
            alpha * normalized[bin] + (1 - alpha) * workHoursPrior(bin)
        }
        return ReplyTimeProfile(
            histogram = histogram.toList(),
            smoothed = smoothed.toList(),
            profile = normalized.toList(),
            blended = blended.toList(),
            effectiveDayWeight = effectiveDayWeight,
            alpha = alpha,
            sampleCount = usable.size,
            replyDayCount = perDay.size
        )
    }

    /** 样本年龄（天）：两 Instant 相差秒数 / 86400（负数=未来样本）。 */
    fun ageDays(instant: Instant, now: Instant): Double =
        Duration.between(instant, now).seconds / SECONDS_PER_DAY

    /** 指数衰减：`2^(-ageDays/30)`。 */
    fun decayWeight(ageDays: Double): Double = Math.pow(2.0, -ageDays / HALF_LIFE_DAYS)

    /** 未来样本不参与任何统计（I-4）。 */
    private fun usableSamples(samples: List<Instant>, now: Instant): List<Instant> =
        samples.filter { !it.isAfter(now) }

    /** 工作时间先验 U：桶 16..33（08:00–17:00）各 1/18，其余 0。 */
    private fun workHoursPrior(bin: Int): Double =
        if (bin >= WORK_START_BIN && bin < WORK_START_BIN + WORK_WINDOW_BINS) {
            1.0 / WORK_WINDOW_BINS
        } else {
            0.0
        }

    /** 连续四桶 Q 之和最大的起点；差值在容差内视为平局，保留更早（更小）的起点。 */
    private fun bestWindowStart(blended: List<Double>): Int {
        var bestStart = 0
        var bestScore = windowScore(blended, 0)
        for (start in 1 until BINS_PER_DAY) {
            val score = windowScore(blended, start)
            if (score > bestScore + TIE_TOLERANCE) {
                bestStart = start
                bestScore = score
            }
        }
        return bestStart
    }

    private fun windowScore(blended: List<Double>, start: Int): Double {
        var score = 0.0
        for (offset in 0 until PATTERN_WINDOW_BINS) {
            score += blended[(start + offset) % BINS_PER_DAY]
        }
        return score
    }

    /**
     * 候选起点：专家当地今天；今天这一段起点已早于 now 则从明天开始（I-5）。
     * 端点由当地日期/时间构造后经 `atZone` 转换（缺口顺延、重叠取较早 offset）；
     * 极端转换压掉整个区间时最多再顺延 [MAX_START_RETRY_DAYS] 个当地日，
     * 仍无正长度区间则报明确计算错误，绝不伪造区间。
     */
    private fun resolveWindow(
        today: LocalDate,
        startLocalTime: LocalTime,
        spanMinutes: Long,
        zone: ZoneId,
        now: Instant
    ): Pair<ZonedDateTime, ZonedDateTime> {
        var date = today
        var window = windowOn(date, startLocalTime, spanMinutes, zone)
        if (window == null || window.first.toInstant().isBefore(now)) {
            date = today.plusDays(1)
            window = windowOn(date, startLocalTime, spanMinutes, zone)
        }
        var retries = 0
        while (window == null && retries < MAX_START_RETRY_DAYS) {
            retries++
            date = date.plusDays(1)
            window = windowOn(date, startLocalTime, spanMinutes, zone)
        }
        return window ?: throw IllegalStateException(
            "无法为时区 $zone 计算有效的推荐窗口：$today 起顺延 $MAX_START_RETRY_DAYS 天仍无正长度区间"
        )
    }

    private fun windowOn(
        date: LocalDate,
        startLocalTime: LocalTime,
        spanMinutes: Long,
        zone: ZoneId
    ): Pair<ZonedDateTime, ZonedDateTime>? {
        val localStart = date.atTime(startLocalTime)
        val localEnd = localStart.plusMinutes(spanMinutes)
        val start = localStart.atZone(zone)
        val end = localEnd.atZone(zone)
        if (!end.toInstant().isAfter(start.toInstant())) return null
        return start to end
    }

    /** 最近 [MAX_RECENT_SAMPLES] 个时刻，同一 Instant 投影到专家时区与北京时间（I-2/I-4）。 */
    private fun recentSamples(samples: List<Instant>, zone: ZoneId, now: Instant): List<SampledReplyInstant> =
        usableSamples(samples, now)
            .sortedDescending()
            .take(MAX_RECENT_SAMPLES)
            .map { SampledReplyInstant(beijing = it.atZone(BEIJING_ZONE), local = it.atZone(zone)) }
}

/** 公式中间量（step 1–4）；`histogram`=H、`smoothed`=S、`profile`=P、`blended`=Q。 */
data class ReplyTimeProfile(
    val histogram: List<Double>,
    val smoothed: List<Double>,
    val profile: List<Double>,
    val blended: List<Double>,
    val effectiveDayWeight: Double,
    val alpha: Double,
    val sampleCount: Int,
    val replyDayCount: Int
)

/** 一条样本的两个投影时刻（当地 + 北京），不携带正文/主题/邮箱/内部 id。 */
data class SampledReplyInstant(
    val beijing: ZonedDateTime,
    val local: ZonedDateTime
)

/** 推荐窗口（端点保留时区与 offset，由服务层格式化为 ISO offset date-time）。 */
data class RecommendedTimingWindow(
    val mode: TimingMode,
    val localStart: ZonedDateTime,
    val localEnd: ZonedDateTime,
    val beijingStart: ZonedDateTime,
    val beijingEnd: ZonedDateTime,
    val sampleCount: Int,
    val replyDayCount: Int,
    val recentSamples: List<SampledReplyInstant>
)
