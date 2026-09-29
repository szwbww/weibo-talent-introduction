package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.discovery.repository.DiscoveryScheduleSetting
import com.weibo.talentintroduction.discovery.repository.DiscoveryScheduleSettingRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryScheduleSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier

/**
 * I-1/I-3/I-4（03）：设置的「一次 Clock 读数 / 同值不动锚点 / 提交后才重排 / 失败语义」定向回归。
 *
 * 数据库与调度器都是内存化替身（真实 MySQL 持久化由 `DiscoveryScheduleSettingRepositoryIT` 覆盖）：
 * - 替身 `repository` 记录**唯一一行**的实际值，替身 `scheduler` 记录**已应用**的行；
 *   因此可以断言「最终 DB 值与有效 future 一致」，而不只是断言某个方法被调用；
 * - `TransactionTemplate` 挂在 mock 的 `PlatformTransactionManager` 上，能真实观察提交/回滚调用。
 */
class DiscoveryScheduleSettingServiceTest {

    private val at10 = Instant.parse("2026-09-29T02:00:00Z")
    private val at11 = Instant.parse("2026-09-29T03:00:00Z")
    private val at12 = Instant.parse("2026-09-29T04:00:00Z")

    private val repository = Mockito.mock(DiscoveryScheduleSettingRepository::class.java)
    private val transactionManager = Mockito.mock(PlatformTransactionManager::class.java)
    private val scheduler = Mockito.mock(ExpertDiscoveryScheduler::class.java)

    @Suppress("UNCHECKED_CAST")
    private val schedulerProvider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<ExpertDiscoveryScheduler>

    private val discoveryProperties = ExpertDiscoveryProperties(cron = "0 0 */2 * * ?")
    private val clock = TestClock(at10)
    private val service = serviceWith(discoveryProperties)

    /** 已保存的唯一一行（替身数据库）。 */
    private var storedRow: DiscoveryScheduleSetting? = null

    /** 调度器**当前已应用**的设置行（null = 应用的是部署 cron）。 */
    private var appliedRow: DiscoveryScheduleSetting? = null
    private var configRegistered = true
    private var reloadResult = true
    private var snapshotReason: String? = null
    private var snapshotNextTriggerAt: Instant? = at12
    private var reloadCount = 0

    @BeforeEach
    fun setUp() {
        storedRow = null
        appliedRow = null
        configRegistered = true
        reloadResult = true
        snapshotReason = null
        snapshotNextTriggerAt = at12
        reloadCount = 0
        clock.advanceTo(at10)
        clock.calls = 0
        Mockito.doReturn(scheduler).`when`(schedulerProvider).getIfAvailable()
        Mockito.doAnswer { storedRow }.`when`(repository).find()
        Mockito.doAnswer { invocation ->
            DiscoveryScheduleSetting(
                id = 1,
                intervalHours = invocation.getArgument(0),
                updatedAt = invocation.getArgument(1)
            ).also { storedRow = it }
        }.`when`(repository).save(Mockito.anyInt(), anyInstant())
        Mockito.doAnswer {
            reloadCount += 1
            if (reloadResult) {
                appliedRow = storedRow
                snapshotReason = null
            } else if (snapshotReason == null) {
                snapshotReason = DiscoveryScheduleSpec.REASON_APPLY_FAILED
            }
            reloadResult
        }.`when`(scheduler).reload()
        Mockito.doAnswer { currentSnapshot() }.`when`(scheduler).snapshot()
    }

    /** Kotlin 的非空参数不接受 `Mockito.any(...)` 返回的 null，用占位值传入（匹配器语义不变）。 */
    private fun anyInstant(): Instant = Mockito.any(Instant::class.java) ?: Instant.EPOCH

    private fun verifyNoSave() =
        Mockito.verify(repository, Mockito.never()).save(Mockito.anyInt(), anyInstant())

    /** 用 doThrow 重设存根：`Mockito.\`when\``（调用式）会在旧存根已经抛异常时再次抛出。 */
    private fun withSettingReadFailure(message: String) {
        Mockito.doThrow(DataAccessResourceFailureException(message)).`when`(repository).find()
    }

    private fun serviceWith(properties: ExpertDiscoveryProperties): DiscoveryScheduleSettingService =
        DiscoveryScheduleSettingService(
            repository, TransactionTemplate(transactionManager), properties, schedulerProvider, clock
        )

    private fun currentSnapshot(): DiscoveryScheduleSnapshot {
        val applied = appliedRow
        val registered = applied != null || configRegistered
        return DiscoveryScheduleSnapshot(
            mode = if (discoveryProperties.pipelineEnabled) {
                DiscoveryScheduleSpec.MODE_CONTINUOUS
            } else {
                DiscoveryScheduleSpec.MODE_LEGACY
            },
            registered = registered,
            source = if (applied != null) {
                DiscoveryScheduleSpec.SOURCE_OVERRIDE
            } else {
                DiscoveryScheduleSpec.SOURCE_CONFIG
            },
            intervalHours = applied?.intervalHours
                ?: DiscoveryScheduleSpec.configuredIntervalHours(discoveryProperties.cron),
            anchorAt = applied?.updatedAt,
            nextTriggerAt = snapshotNextTriggerAt,
            reason = if (registered) snapshotReason else DiscoveryScheduleSpec.REASON_NOT_STARTED
        )
    }

    private fun iso(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant)

    // ------------------------------------------------------------------
    // I-1：一个设置、一条事实来源
    // ------------------------------------------------------------------

    @Test
    fun `get reports the deployment cron as two hours and never creates a row (I-1)`() {
        val result = service.get()

        assertEquals(DiscoveryScheduleStatus.OK, result.status)
        assertEquals(DiscoveryScheduleSpec.MODE_LEGACY, result.view.mode)
        assertTrue(result.view.editable)
        assertEquals(DiscoveryScheduleSpec.SOURCE_CONFIG, result.view.source)
        assertEquals(2, result.view.intervalHours)
        assertNull(result.view.anchorAt)
        assertEquals(iso(at12), result.view.nextTriggerAt)
        assertTrue(result.view.applied)
        assertFalse(result.view.saved)
        assertNull(result.view.reason)
        assertNull(storedRow, "GET 不得建行")
        verifyNoSave()
    }

    @Test
    fun `put persists the interval with a single clock reading as the anchor (I-1)`() {
        val result = service.save(3)

        assertEquals(DiscoveryScheduleStatus.OK, result.status)
        assertEquals(1, clock.calls, "首次保存只允许一次 Clock 读数")
        assertEquals(3, storedRow?.intervalHours)
        assertEquals(at10, storedRow?.updatedAt, "锚点 = 保存时刻（UTC）")
        assertEquals(DiscoveryScheduleSpec.SOURCE_OVERRIDE, result.view.source)
        assertEquals(3, result.view.intervalHours)
        assertEquals(iso(at10), result.view.anchorAt)
        assertTrue(result.view.applied)
        assertTrue(result.view.saved)
        assertNull(result.view.reason)
        assertEquals("已设置每 3 小时执行一次", result.view.message)
        assertEquals(storedRow, appliedRow, "已应用设置必须与已保存行逐字段一致")
    }

    @Test
    fun `saving the same value again keeps the anchor and rewrites nothing (I-1, I-3)`() {
        storedRow = DiscoveryScheduleSetting(1, 3, at10)
        clock.advanceTo(at11)

        val result = service.save(3)

        assertEquals(DiscoveryScheduleStatus.OK, result.status)
        assertEquals(1, clock.calls, "同值重存同样只读一次时钟")
        assertEquals(at10, storedRow?.updatedAt, "相同值重存不得重置锚点")
        assertEquals(iso(at10), result.view.anchorAt)
        assertTrue(result.view.applied, "同值重存必须能恢复应用")
        assertEquals(1, reloadCount)
        verifyNoSave()
    }

    @Test
    fun `saving a different value moves the anchor to the save instant (I-1)`() {
        storedRow = DiscoveryScheduleSetting(1, 3, at10)
        clock.advanceTo(at11)

        val result = service.save(5)

        assertEquals(DiscoveryScheduleStatus.OK, result.status)
        assertEquals(5, storedRow?.intervalHours)
        assertEquals(at11, storedRow?.updatedAt)
        assertEquals(iso(at11), result.view.anchorAt)
        Mockito.verify(repository).save(5, at11)
    }

    @Test
    fun `an out of range interval is rejected before any database access (I-1)`() {
        assertThrows(IllegalArgumentException::class.java) { service.save(0) }
        assertThrows(IllegalArgumentException::class.java) { service.save(169) }

        assertNull(storedRow)
        verifyNoSave()
        Mockito.verify(scheduler, Mockito.never()).reload()
    }

    @Test
    fun `an unreadable or corrupted setting is reported as unavailable and never as no row (I-1)`() {
        // 连接失败：既不是「无行」，也不得悄悄回退默认 cron。
        withSettingReadFailure("db down")

        val get = service.get()

        assertEquals(DiscoveryScheduleStatus.UNAVAILABLE, get.status)
        assertEquals(DiscoveryScheduleSpec.REASON_DB_UNAVAILABLE, get.view.reason)
        assertFalse(get.view.applied)
        assertFalse(get.view.saved)
        assertNull(get.view.intervalHours, "库不可读时不得显示“默认 2 小时”")

        val put = service.save(3)
        assertEquals(DiscoveryScheduleStatus.UNAVAILABLE, put.status)
        assertFalse(put.view.saved)
        assertTrue(put.view.applied.not())
        assertEquals(DiscoveryScheduleSpec.REASON_DB_UNAVAILABLE, put.view.reason)
        Mockito.verify(scheduler, Mockito.never()).reload()

        // 存量值越界（repository 会抛异常而不是返回 null）同样不得被解释成「无行」。
        Mockito.doThrow(IllegalStateException("discovery_schedule_setting 存量值越界"))
            .`when`(repository).find()
        val corrupted = service.get()
        assertEquals(DiscoveryScheduleStatus.UNAVAILABLE, corrupted.status)
        assertEquals(DiscoveryScheduleSpec.REASON_DB_UNAVAILABLE, corrupted.view.reason)
    }

    // ------------------------------------------------------------------
    // I-3：已保存与已应用必须一致才报成功
    // ------------------------------------------------------------------

    @Test
    fun `a database failure rolls back, keeps the old schedule and reports saved=false (I-3)`() {
        withSettingReadFailure("db down")

        val result = service.save(3)

        assertEquals(DiscoveryScheduleStatus.UNAVAILABLE, result.status)
        assertFalse(result.view.saved)
        assertFalse(result.view.applied)
        assertEquals(DiscoveryScheduleSpec.REASON_DB_UNAVAILABLE, result.view.reason)
        assertTrue(result.view.message.contains("保存失败"), result.view.message)
        Mockito.verify(transactionManager).rollback(Mockito.any())
        Mockito.verify(scheduler, Mockito.never()).reload()
        assertNull(storedRow)
    }

    @Test
    fun `an apply failure reports saved=true applied=false and asks for a retry (I-3)`() {
        reloadResult = false

        val result = service.save(3)

        assertEquals(DiscoveryScheduleStatus.UNAVAILABLE, result.status)
        assertTrue(result.view.saved, "设置已提交，必须区分 saved=true 的 503")
        assertFalse(result.view.applied)
        assertEquals(DiscoveryScheduleSpec.REASON_APPLY_FAILED, result.view.reason)
        assertTrue(result.view.message.contains("重试保存"), result.view.message)
        assertEquals(3, result.view.intervalHours, "已保存值仍对页面可见")
        assertNotNull(storedRow)
    }

    @Test
    fun `a missing scheduler never fakes a successful apply (I-3)`() {
        Mockito.doReturn(null).`when`(schedulerProvider).getIfAvailable()

        val result = service.save(3)

        assertEquals(DiscoveryScheduleStatus.UNAVAILABLE, result.status)
        assertTrue(result.view.saved)
        assertFalse(result.view.applied)
        assertEquals(DiscoveryScheduleSpec.REASON_NOT_STARTED, result.view.reason)
    }

    @Test
    fun `a stored value that disagrees with the applied schedule is reported as not applied (I-3)`() {
        storedRow = DiscoveryScheduleSetting(1, 5, at11)
        appliedRow = DiscoveryScheduleSetting(1, 3, at10)
        snapshotReason = DiscoveryScheduleSpec.REASON_APPLY_FAILED

        val result = service.get()

        assertEquals(DiscoveryScheduleStatus.OK, result.status)
        assertFalse(result.view.applied, "DB 值与有效 future 不一致时必须报 applied=false")
        assertEquals(DiscoveryScheduleSpec.REASON_APPLY_FAILED, result.view.reason)
        assertEquals(5, result.view.intervalHours, "显示的是已保存值")
        assertEquals(iso(at11), result.view.anchorAt)
    }

    @Test
    fun `two concurrent saves end with the stored value and the applied schedule in agreement (I-3)`() {
        val barrier = CyclicBarrier(2)
        val results = CopyOnWriteArrayList<DiscoveryScheduleResult>()
        val threads = listOf(3, 5).map { interval ->
            Thread {
                barrier.await()
                results += service.save(interval)
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join(10_000) }

        assertEquals(2, results.size)
        assertEquals(1, storedRow?.id, "永远只有单例行")
        assertEquals(storedRow, appliedRow, "最终 DB 值必须与有效 future 一致")
        assertTrue(results.all { it.status == DiscoveryScheduleStatus.OK })
        assertTrue(results.all { it.view.applied }, "两次保存都必须报 applied=true")
        assertTrue(reloadCount <= 2)
    }

    // ------------------------------------------------------------------
    // I-4：小时设置只在可编辑模式生效
    // ------------------------------------------------------------------

    @Test
    fun `modes that are not editable reject the write without touching the database (I-4)`() {
        assertNotEditable(
            ExpertDiscoveryProperties(cron = "0 0 */2 * * ?", pipelineEnabled = true),
            DiscoveryScheduleSpec.REASON_CONTINUOUS_MODE
        )
        assertNotEditable(
            ExpertDiscoveryProperties(cron = "0 0 */2 * * ?", enabled = false),
            DiscoveryScheduleSpec.REASON_DISABLED
        )
        assertNotEditable(
            ExpertDiscoveryProperties(cron = "-"),
            DiscoveryScheduleSpec.REASON_CRON_DISABLED
        )
    }

    @Test
    fun `cron disabled wins over a stored setting and shows it as not applied (I-4)`() {
        storedRow = DiscoveryScheduleSetting(1, 3, at10)
        configRegistered = false
        snapshotNextTriggerAt = null
        snapshotReason = DiscoveryScheduleSpec.REASON_CRON_DISABLED

        val result = serviceWith(ExpertDiscoveryProperties(cron = "-")).get()

        assertEquals(DiscoveryScheduleStatus.OK, result.status)
        assertFalse(result.view.editable)
        assertFalse(result.view.applied, "cron=- 时遗留的小时设置不得被报告为已生效")
        assertEquals(DiscoveryScheduleSpec.REASON_CRON_DISABLED, result.view.reason)
        assertEquals(3, result.view.intervalHours, "已保存值仍可见（供页面说明），但不可编辑")
        assertNull(result.view.nextTriggerAt)
    }

    private fun assertNotEditable(properties: ExpertDiscoveryProperties, expectedReason: String) {
        val candidate = serviceWith(properties)
        val read = candidate.get()

        assertFalse(read.view.editable, "不可编辑模式必须 editable=false")
        assertEquals(expectedReason, read.view.reason)

        val result = candidate.save(3)

        assertEquals(DiscoveryScheduleStatus.NOT_EDITABLE, result.status)
        assertFalse(result.view.editable)
        assertFalse(result.view.applied)
        assertEquals(expectedReason, result.view.reason)
        assertNull(storedRow, "不可编辑模式不得写库")
        verifyNoSave()
        Mockito.verify(scheduler, Mockito.never()).reload()
    }

    @Test
    fun `continuous mode never reports an applied hourly plan (I-4)`() {
        val continuous = serviceWith(
            ExpertDiscoveryProperties(cron = "0 0 */2 * * ?", pipelineEnabled = true)
        )

        val result = continuous.get()

        assertEquals(DiscoveryScheduleSpec.MODE_CONTINUOUS, result.view.mode)
        assertFalse(result.view.editable)
        assertNull(result.view.nextTriggerAt)
        assertFalse(result.view.applied)
        assertEquals(DiscoveryScheduleSpec.REASON_CONTINUOUS_MODE, result.view.reason)
    }

    /** 可计数的固定时钟：用来证明「首次保存与值变化共用一次读数」。 */
    private class TestClock(private var current: Instant) : Clock() {
        var calls = 0
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant {
            calls += 1
            return current
        }
        fun advanceTo(next: Instant) {
            current = next
        }
    }
}
