package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 02（c2）纯计算器测试（I-2～I-5）。
 *
 * 全部用例固定 `now` 并直接断言窗口端点/直方图中间量；不连数据库、不读系统时钟。
 * 中间量断言经由 [ReplyTimeRecommender.profile]（同一公式的公开分解），窗口断言经由
 * [ReplyTimeRecommender.recommend]。
 *
 * 依赖运行 JDK 的 tzdb（本仓绑定 Zulu 11.0.32 / tzdb 2026b）；DST 用例的时间点按该版本
 * 规则书写，目录资源版本 G-0 的验收不在本测试范围内。
 */
class ReplyTimeRecommenderTest {

    private val shanghai = ReplyTimeRecommender.BEIJING_ZONE
    private val saoPaulo = ZoneId.of("America/Sao_Paulo")

    // ------------------------------------------------------------------
    // I-2 / I-5：固定 now 下的冷启动工作时间与跨时区投影
    // ------------------------------------------------------------------

    @Test
    fun `zero samples in India return local work hours mapped to Beijing time`() {
        val now = Instant.parse("2026-10-02T00:00:00Z")

        val window = ReplyTimeRecommender.recommend(emptyList(), ZoneId.of("Asia/Kolkata"), now)

        assertEquals(TimingMode.WORK_HOURS, window.mode)
        assertEquals(0, window.sampleCount)
        assertEquals(0, window.replyDayCount)
        assertEquals("2026-10-02T08:00:00+05:30", iso(window.localStart))
        assertEquals("2026-10-02T17:00:00+05:30", iso(window.localEnd))
        assertEquals("2026-10-02T10:30:00+08:00", iso(window.beijingStart))
        assertEquals("2026-10-02T19:30:00+08:00", iso(window.beijingEnd))
        assertTrue(window.localStart.toInstant().isAfter(now))
    }

    @Test
    fun `zero samples in New York keep the next full local window across Beijing midnight`() {
        val now = Instant.parse("2026-10-02T00:00:00Z")

        val window = ReplyTimeRecommender.recommend(emptyList(), ZoneId.of("America/New_York"), now)

        assertEquals("2026-10-02T08:00:00-04:00", iso(window.localStart))
        assertEquals("2026-10-02T17:00:00-04:00", iso(window.localEnd))
        assertEquals("2026-10-02T20:00:00+08:00", iso(window.beijingStart))
        assertEquals("2026-10-03T05:00:00+08:00", iso(window.beijingEnd))
    }

    @Test
    fun `zero samples in winter New York follow the standard time offset`() {
        val now = Instant.parse("2026-12-01T00:00:00Z")

        val window = ReplyTimeRecommender.recommend(emptyList(), ZoneId.of("America/New_York"), now)

        assertEquals("2026-12-01T08:00:00-05:00", iso(window.localStart))
        assertEquals("2026-12-01T17:00:00-05:00", iso(window.localEnd))
        assertEquals("2026-12-01T21:00:00+08:00", iso(window.beijingStart))
        assertEquals("2026-12-02T06:00:00+08:00", iso(window.beijingEnd))
    }

    @Test
    fun `quarter hour and half hour zones use zone rules instead of fixed offsets`() {
        val now = Instant.parse("2026-10-02T00:00:00Z")

        val nepal = ReplyTimeRecommender.recommend(emptyList(), ZoneId.of("Asia/Kathmandu"), now)
        assertEquals("2026-10-02T08:00:00+05:45", iso(nepal.localStart))
        assertEquals("2026-10-02T10:15:00+08:00", iso(nepal.beijingStart))
        assertEquals("2026-10-02T19:15:00+08:00", iso(nepal.beijingEnd))

        // Lord Howe 2026-10-02 仍为标准时 +10:30（夏令时自 10-04 起）；当地 08:00 已过去，落到次日。
        val lordHowe = ReplyTimeRecommender.recommend(emptyList(), ZoneId.of("Australia/Lord_Howe"), now)
        assertEquals("2026-10-03T08:00:00+10:30", iso(lordHowe.localStart))
        assertEquals("2026-10-03T17:00:00+10:30", iso(lordHowe.localEnd))
        assertEquals("2026-10-03T05:30:00+08:00", iso(lordHowe.beijingStart))
    }

    @Test
    fun `recommended start is never before now in any zone`() {
        val now = Instant.parse("2026-10-02T00:00:00Z")
        val zones = listOf(
            "Asia/Shanghai", "Asia/Kolkata", "Asia/Kathmandu", "America/New_York", "America/Sao_Paulo",
            "Europe/London", "Europe/Berlin", "Australia/Lord_Howe", "Pacific/Auckland", "Pacific/Kiritimati"
        )

        zones.forEach { zone ->
            val window = ReplyTimeRecommender.recommend(emptyList(), ZoneId.of(zone), now)
            assertFalse(
                window.localStart.toInstant().isBefore(now),
                "$zone 的推荐起点不得早于 now"
            )
            assertTrue(
                window.localEnd.toInstant().isAfter(window.localStart.toInstant()),
                "$zone 的推荐区间必须为正长度"
            )
            assertEquals(window.localStart.toInstant(), window.beijingStart.toInstant(), "$zone 两投影必须是同一 Instant")
            assertEquals(window.localEnd.toInstant(), window.beijingEnd.toInstant(), "$zone 两投影必须是同一 Instant")
        }
    }

    // ------------------------------------------------------------------
    // I-3：冷启动与习惯模式
    // ------------------------------------------------------------------

    @Test
    fun `one or two reply days still use the full work hours window`() {
        val now = Instant.parse("2026-10-02T00:00:00Z")
        val samples = listOf(
            at(saoPaulo, "2026-09-30T11:00"),
            at(saoPaulo, "2026-09-30T15:30"),
            at(saoPaulo, "2026-10-01T09:00")
        )

        val window = ReplyTimeRecommender.recommend(samples, saoPaulo, now)

        assertEquals(TimingMode.WORK_HOURS, window.mode)
        assertEquals(3, window.sampleCount)
        assertEquals(2, window.replyDayCount)
        assertEquals("2026-10-02T08:00:00-03:00", iso(window.localStart))
        assertEquals("2026-10-02T17:00:00-03:00", iso(window.localEnd))
    }

    @Test
    fun `twenty samples on a single day are still cold start`() {
        val now = Instant.parse("2026-10-02T03:00:00Z")
        val samples = (0 until 20).map { minute -> at(saoPaulo, "2026-10-01T14:${"%02d".format(minute)}") }

        val window = ReplyTimeRecommender.recommend(samples, saoPaulo, now)

        assertEquals(TimingMode.WORK_HOURS, window.mode)
        assertEquals(20, window.sampleCount)
        assertEquals(1, window.replyDayCount, "一天连回二十封不能算作稳定习惯")
        assertEquals("2026-10-02T08:00:00-03:00", iso(window.localStart))
    }

    @Test
    fun `three local reply days pick the earliest tied four bucket window`() {
        val now = Instant.parse("2026-10-02T00:00:00Z")
        val samples = listOf(
            at(saoPaulo, "2026-09-29T14:15"),
            at(saoPaulo, "2026-09-30T14:15"),
            at(saoPaulo, "2026-10-01T14:15")
        )

        val window = ReplyTimeRecommender.recommend(samples, saoPaulo, now)

        assertEquals(TimingMode.REPLY_PATTERN, window.mode)
        assertEquals(3, window.replyDayCount)
        assertEquals("2026-10-02T13:00:00-03:00", iso(window.localStart), "13:00–15:00 与 13:30–15:30 平局时必须取更早起点")
        assertEquals("2026-10-02T15:00:00-03:00", iso(window.localEnd))
        assertEquals("2026-10-03T00:00:00+08:00", iso(window.beijingStart))
        assertEquals("2026-10-03T02:00:00+08:00", iso(window.beijingEnd))
    }

    @Test
    fun `persistent late night replies move the window outside work hours across midnight`() {
        val now = Instant.parse("2026-10-02T00:00:00Z")
        val samples = (0 until 5).map { day -> at(saoPaulo, "2026-09-${"%02d".format(26 + day)}T23:45") }

        val window = ReplyTimeRecommender.recommend(samples, saoPaulo, now)

        assertEquals(TimingMode.REPLY_PATTERN, window.mode)
        assertEquals(5, window.replyDayCount)
        assertEquals("2026-10-01T22:30:00-03:00", iso(window.localStart))
        assertEquals("2026-10-02T00:30:00-03:00", iso(window.localEnd), "四桶跨午夜时结束日期必须 +1")
        assertEquals(window.localStart.toLocalDate().plusDays(1), window.localEnd.toLocalDate())
        assertTrue(window.localStart.hour < 8 || window.localStart.hour >= 17, "习惯窗口不得被裁回工作时间")
    }

    // ------------------------------------------------------------------
    // I-4：上限、衰减、可复现
    // ------------------------------------------------------------------

    @Test
    fun `identical inputs return identical outputs`() {
        val now = Instant.parse("2026-10-02T00:00:00Z")
        val samples = listOf(
            at(saoPaulo, "2026-09-29T14:15"),
            at(saoPaulo, "2026-09-30T14:15"),
            at(saoPaulo, "2026-10-01T14:15")
        )

        assertEquals(
            ReplyTimeRecommender.recommend(samples, saoPaulo, now),
            ReplyTimeRecommender.recommend(samples, saoPaulo, now)
        )
        assertEquals(
            ReplyTimeRecommender.profile(samples, saoPaulo, now),
            ReplyTimeRecommender.profile(samples, saoPaulo, now)
        )
    }

    @Test
    fun `daily weight cap keeps one local day at two units`() {
        val now = Instant.parse("2026-10-02T00:00:00Z")

        assertEquals(2.0, histogramTotal(List(2) { now }, saoPaulo, now), 1e-9)
        assertEquals(2.0, histogramTotal(List(100) { now }, saoPaulo, now), 1e-9)
        assertEquals(2.0, histogramTotal(List(1000) { now }, saoPaulo, now), 1e-9)
    }

    @Test
    fun `thirty day old samples decay to half weight`() {
        val now = Instant.parse("2026-10-02T00:00:00Z")
        val sample = now.minus(Duration.ofDays(30))

        assertEquals(0.5, ReplyTimeRecommender.decayWeight(30.0), 1e-12)
        assertEquals(1.0, ReplyTimeRecommender.decayWeight(0.0), 1e-12)
        assertEquals(0.5, histogramTotal(listOf(sample), saoPaulo, now), 1e-12)
    }

    @Test
    fun `newer night samples gradually override the old daytime habit`() {
        val now = Instant.parse("2026-10-02T00:00:00Z")
        val daytime = listOf("2026-09-26", "2026-09-27", "2026-09-28").map { day -> at(saoPaulo, "${day}T09:00") }
        val night = listOf("2026-09-27", "2026-09-28", "2026-09-29", "2026-09-30", "2026-10-01")
            .map { day -> at(saoPaulo, "${day}T21:00") }

        val before = ReplyTimeRecommender.recommend(daytime, saoPaulo, now)
        val after = ReplyTimeRecommender.recommend(daytime + night, saoPaulo, now)

        assertEquals("2026-10-02T08:00:00-03:00", iso(before.localStart))
        assertEquals("2026-10-02T20:00:00-03:00", iso(after.localStart), "更近的夜间样本应把窗口移出工作时间")
        assertEquals(6, after.replyDayCount)
    }

    @Test
    fun `ageing samples lower both the effective day weight and the pattern weight`() {
        val pattern = listOf(
            at(saoPaulo, "2026-09-29T14:15"),
            at(saoPaulo, "2026-09-30T14:15"),
            at(saoPaulo, "2026-10-01T14:15")
        )
        val now = Instant.parse("2026-10-02T00:00:00Z")
        val aged = pattern.map { it.minus(Duration.ofDays(200)) }

        val fresh = ReplyTimeRecommender.profile(pattern, saoPaulo, now)
        val stale = ReplyTimeRecommender.profile(aged, saoPaulo, now)

        assertTrue(stale.effectiveDayWeight < fresh.effectiveDayWeight, "样本整体老化后 E 必须下降")
        assertTrue(stale.alpha < fresh.alpha, "E 下降必须同时削弱回复模式权重")
        assertTrue(stale.alpha > 0.0)
    }

    @Test
    fun `future samples never enter any statistic`() {
        val now = Instant.parse("2026-10-02T00:00:00Z")
        val past = at(saoPaulo, "2026-09-30T14:15")
        val future = now.plus(Duration.ofHours(1))

        val withFuture = ReplyTimeRecommender.recommend(listOf(past, future), saoPaulo, now)

        assertEquals(ReplyTimeRecommender.recommend(listOf(past), saoPaulo, now), withFuture)
        assertEquals(1, ReplyTimeRecommender.profile(listOf(past, future), saoPaulo, now).sampleCount)
        assertEquals(1, withFuture.recentSamples.size)
    }

    @Test
    fun `recent samples are the newest eight projected into both zones`() {
        val now = Instant.parse("2026-10-02T00:00:00Z")
        val samples = (0 until 12).map { hour -> at(saoPaulo, "2026-10-01T%02d:15".format(hour)) }

        val window = ReplyTimeRecommender.recommend(samples, saoPaulo, now)

        assertEquals(12, window.sampleCount)
        assertEquals(8, window.recentSamples.size)
        val instants = window.recentSamples.map { it.local.toInstant() }
        assertEquals(instants.sortedDescending(), instants, "recentSamples 必须按时间倒序取最新八条")
        window.recentSamples.forEach { sample ->
            assertEquals(shanghai, sample.beijing.zone)
            assertEquals(saoPaulo, sample.local.zone)
            assertEquals(sample.local.toInstant(), sample.beijing.toInstant())
        }
    }

    @Test
    fun `samples are bucketed by their own local rules across a DST change`() {
        val now = Instant.parse("2026-11-10T00:00:00Z")
        val newYork = ZoneId.of("America/New_York")

        // 同一当地墙上时刻（14:00）在夏令时前后属于同一桶；同一 UTC 时刻在切换后落到前一小时。
        val sameWallClock = ReplyTimeRecommender.profile(
            listOf(Instant.parse("2026-10-30T18:00:00Z"), Instant.parse("2026-11-05T19:00:00Z")),
            newYork,
            now
        )
        assertEquals(14 * 2, sameWallClock.histogram.indexOfFirst { it > 0.0 }, "14:00 必须是桶 28")

        val fixedInstant = ReplyTimeRecommender.profile(
            listOf(Instant.parse("2026-11-02T18:00:00Z")),
            newYork,
            now
        )
        assertEquals(0.0, fixedInstant.histogram[14 * 2], "11-02 的 18:00Z 已是 13:00 当地，不得按固定偏移落 14:00 桶")
        assertEquals(13 * 2, fixedInstant.histogram.indexOfFirst { it > 0.0 })
    }

    // ------------------------------------------------------------------
    // I-5：DST 缺口/重叠与极端日期
    // ------------------------------------------------------------------

    @Test
    fun `DST gap shifts the window forward without producing a zero length interval`() {
        val london = ZoneId.of("Europe/London")
        // 2026-03-29 当地 01:00–02:00 是缺口；峰值桶给 01:30 起点，atZone 必须顺延到 02:30。
        val now = Instant.parse("2026-03-29T00:00:00Z")
        val samples = (0 until 3).map { day -> at(london, "2026-03-${"%02d".format(26 + day)}T02:30") }

        val window = ReplyTimeRecommender.recommend(samples, london, now)

        assertEquals("2026-03-29T02:30:00+01:00", iso(window.localStart))
        assertEquals("2026-03-29T03:30:00+01:00", iso(window.localEnd))
        assertTrue(window.localEnd.toInstant().isAfter(window.localStart.toInstant()))
    }

    @Test
    fun `DST overlap resolves to the earlier offset`() {
        val london = ZoneId.of("Europe/London")
        // 2026-10-25 当地 01:00–02:00 出现两次（BST +01 在前）；候选起点 01:30 必须取较早 offset。
        val now = Instant.parse("2026-10-25T00:00:00Z")
        val samples = (0 until 3).map { day -> at(london, "2026-10-${"%02d".format(22 + day)}T02:30") }

        val window = ReplyTimeRecommender.recommend(samples, london, now)

        assertEquals("2026-10-25T01:30:00+01:00", iso(window.localStart))
        assertEquals("2026-10-25T03:30:00Z", iso(window.localEnd), "重叠后的标准时 offset 为 0，ISO 输出为 Z")
        assertEquals("2026-10-25T08:30:00+08:00", iso(window.beijingStart))
    }

    @Test
    fun `collapsed windows retry the next local day instead of faking an interval`() {
        val troll = ZoneId.of("Antarctica/Troll")
        // 2026-03-29 当地 01:00 直接跳到 03:00（缺口 2 小时），四桶窗口 01:00–03:00 会被压成零长度。
        val now = Instant.parse("2026-03-29T00:00:00Z")
        val samples = (0 until 3).map { day -> at(troll, "2026-03-${"%02d".format(26 + day)}T02:15") }

        val window = ReplyTimeRecommender.recommend(samples, troll, now)

        assertEquals("2026-03-30T01:00:00+02:00", iso(window.localStart), "缺口当天无法形成正区间，必须顺延到下一当地日而不是伪造区间")
        assertEquals("2026-03-30T03:00:00+02:00", iso(window.localEnd))
        assertEquals(Duration.ofHours(2), Duration.between(window.localStart.toInstant(), window.localEnd.toInstant()))
        assertFalse(window.localStart.toInstant().isBefore(now))
    }

    // ------------------------------------------------------------------
    // profile 分解
    // ------------------------------------------------------------------

    @Test
    fun `profile keeps histogram smoothing and blend consistent`() {
        val now = Instant.parse("2026-10-02T00:00:00Z")
        // 当地时间 21:00（桶 42）在工作时间先验之外，混合后这 18 个先验桶正好只剩 (1-alpha)。
        val samples = listOf(at(saoPaulo, "2026-10-01T21:00"))

        val profile = ReplyTimeRecommender.profile(samples, saoPaulo, now)

        assertEquals(ReplyTimeRecommender.BINS_PER_DAY, profile.histogram.size)
        assertEquals(1.0, profile.histogram.sum(), 1e-12)
        assertEquals(1.0, profile.smoothed.sum(), 1e-12, "S 的归一化结果就是 P")
        assertEquals(1.0, profile.profile.sum(), 1e-12, "P 必须归一化")
        assertEquals(0.2 * profile.histogram[42], profile.smoothed[41], 1e-12)
        assertEquals(0.6 * profile.histogram[42], profile.smoothed[42], 1e-12)
        assertEquals(0.2 * profile.histogram[42], profile.smoothed[43], 1e-12)
        assertEquals(0.6 * profile.alpha, profile.blended[42], 1e-12, "峰值桶只有 P 的 alpha 份额")
        assertEquals(0.0, profile.blended[0], 1e-12, "先验与峰值之外的桶必须为 0")
        assertEquals(1.0 - profile.alpha, (16 until 34).sumOf { profile.blended[it] }, 1e-12, "工作时间先验占 (1-alpha)")
        assertEquals(1.0, profile.blended.sum(), 1e-12)
        assertEquals(1, profile.replyDayCount)
        assertEquals(1, profile.sampleCount)
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun at(zone: ZoneId, text: String): Instant =
        LocalDateTime.parse(text).atZone(zone).toInstant()

    private fun iso(value: ZonedDateTime): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value)

    private fun histogramTotal(samples: List<Instant>, zone: ZoneId, now: Instant): Double =
        ReplyTimeRecommender.profile(samples, zone, now).histogram.sum()
}
