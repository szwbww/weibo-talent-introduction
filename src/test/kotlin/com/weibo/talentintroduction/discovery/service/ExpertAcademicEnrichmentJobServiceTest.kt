package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.discovery.domain.ExpertAcademicEnrichmentJob
import com.weibo.talentintroduction.discovery.repository.ExpertAcademicEnrichmentJobRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyString
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDateTime
import java.util.Optional

/**
 * 任务存储 service 的接缝回归。真实 SQL 语义（唯一键合并、30 天新鲜度、并发 CAS 领取、
 * 过期租约恢复、退避阶梯、UNMATCHED/FAILED）由 [ExpertAcademicEnrichmentJobRepositoryIT]
 * 在真实 MySQL 上按持久化边界验证；本文件只覆盖无法在集成测试里稳定构造的接缝：
 *
 * - 候选-CAS 是「尽力领取」：抢不到的行绝不能交给 worker（I-2）；
 * - 每行一个独立 `lease_token` + 10 分钟租期（I-2 不同 token 隔离完成）；
 * - `complete` 只在 token 匹配且 CAS 真实命中时才返回 `true`（I-2 旧 worker 不能覆盖新结果）。
 *
 * 本仓约定：Kotlin 接口的非空入参遇到返回平台类型的匹配器（`eq(...)`/`any(...)`/`capture()`）
 * 会被编译器插入非空断言，故一律写成 `匹配器 ?: 兜底值`（见 [eqValue]）。
 */
class ExpertAcademicEnrichmentJobServiceTest {

    private val repository: ExpertAcademicEnrichmentJobRepository =
        mock(ExpertAcademicEnrichmentJobRepository::class.java)

    private val service = ExpertAcademicEnrichmentJobService(repository)

    @Test
    fun `claimDue never hands out rows whose conditional claim lost the race (I-2)`() {
        val now = LocalDateTime.now()
        `when`(repository.findDueCandidates(10, now)).thenReturn(
            listOf(pendingJob(1, now), pendingJob(2, now), pendingJob(3, now))
        )
        `when`(repository.claimById(eqValue(1L), anyString(), any(LocalDateTime::class.java) ?: now, eqValue(now)))
            .thenReturn(0)
        `when`(repository.claimById(eqValue(2L), anyString(), any(LocalDateTime::class.java) ?: now, eqValue(now)))
            .thenReturn(1)
        `when`(repository.claimById(eqValue(3L), anyString(), any(LocalDateTime::class.java) ?: now, eqValue(now)))
            .thenReturn(1)
        `when`(repository.findById(2L)).thenReturn(Optional.of(runningJob(2, "token-2", now)))
        `when`(repository.findById(3L)).thenReturn(Optional.of(runningJob(3, "token-3", now)))

        val claimed = service.claimDue(10, now)

        // 只有 CAS 命中的行被交出去，且交出去的是持久化后的状态（带自己的 token）。
        assertEquals(listOf(2L, 3L), claimed.map { it.id })
        assertEquals(listOf("token-2", "token-3"), claimed.map { it.leaseToken })
        verify(repository, never()).findById(1L)
    }

    @Test
    fun `claimDue issues a distinct lease token and a ten minute lease per row (I-2)`() {
        val now = LocalDateTime.now().withNano(0)
        `when`(repository.findDueCandidates(2, now)).thenReturn(listOf(pendingJob(11, now), pendingJob(12, now)))
        `when`(repository.claimById(anyLong(), anyString(), any(LocalDateTime::class.java) ?: now, eqValue(now)))
            .thenReturn(1)
        `when`(repository.findById(11L)).thenReturn(Optional.of(runningJob(11, "t11", now)))
        `when`(repository.findById(12L)).thenReturn(Optional.of(runningJob(12, "t12", now)))

        service.claimDue(2, now)

        val tokenCaptor = ArgumentCaptor.forClass(String::class.java)
        val leaseCaptor = ArgumentCaptor.forClass(LocalDateTime::class.java)
        verify(repository, times(2)).claimById(
            anyLong(),
            tokenCaptor.capture() ?: "",
            leaseCaptor.capture() ?: now,
            eqValue(now)
        )
        assertEquals(2, tokenCaptor.allValues.toSet().size, "每行必须拿到独立 token")
        assertTrue(tokenCaptor.allValues.all { it.isNotBlank() })
        assertEquals(listOf(now.plusMinutes(10), now.plusMinutes(10)), leaseCaptor.allValues)
    }

    @Test
    fun `complete refuses a stale token or a non running row without writing (I-2)`() {
        val now = LocalDateTime.now()
        `when`(repository.findById(7L)).thenReturn(Optional.of(runningJob(7, "current", now)))
        `when`(repository.findById(8L)).thenReturn(Optional.of(pendingJob(8, now)))
        `when`(repository.findById(9L)).thenReturn(Optional.empty())

        assertFalse(service.complete(7L, "stale-token", success()))
        assertFalse(service.complete(8L, "any-token", success()), "非 RUNNING 行不能被完成")
        assertFalse(service.complete(9L, "any-token", success()))

        verify(repository, never()).completeWithToken(
            anyLong(),
            anyString(),
            anyString(),
            anyInt(),
            any(LocalDateTime::class.java) ?: now,
            any(),
            any(),
            any(LocalDateTime::class.java) ?: now
        )
    }

    @Test
    fun `complete reports failure when the token matched CAS no longer applies (I-2)`() {
        val now = LocalDateTime.now()
        `when`(repository.findById(5L)).thenReturn(Optional.of(runningJob(5, "token-5", now)))
        `when`(
            repository.completeWithToken(
                eqValue(5L),
                eqValue("token-5"),
                anyString(),
                anyInt(),
                any(LocalDateTime::class.java) ?: now,
                any(),
                any(),
                any(LocalDateTime::class.java) ?: now
            )
        ).thenReturn(0)

        assertFalse(
            service.complete(5L, "token-5", success()),
            "读取后被重新领取（CAS 未命中）时不得上报完成"
        )
        verify(repository).completeWithToken(
            eqValue(5L),
            eqValue("token-5"),
            anyString(),
            anyInt(),
            any(LocalDateTime::class.java) ?: now,
            any(),
            any(),
            any(LocalDateTime::class.java) ?: now
        )
    }

    // ------------------------------------------------------------------

    /** 本仓既有写法：匹配器返回平台类型，直接入参会被 Kotlin 非空断言拦下（如 `eq(...)`）。 */
    private fun <T> eqValue(value: T): T = eq(value) ?: value

    private fun pendingJob(id: Long, now: LocalDateTime) = ExpertAcademicEnrichmentJob(
        id = id,
        expertDocId = "DOC-$id",
        source = "openalex",
        status = ExpertAcademicEnrichmentJob.STATUS_PENDING,
        nextAttemptAt = now
    )

    private fun runningJob(id: Long, leaseToken: String, now: LocalDateTime) = ExpertAcademicEnrichmentJob(
        id = id,
        expertDocId = "DOC-$id",
        source = "openalex",
        status = ExpertAcademicEnrichmentJob.STATUS_RUNNING,
        nextAttemptAt = now,
        leaseToken = leaseToken,
        leaseUntil = now.plusMinutes(10)
    )

    private fun success() = ProfileEnrichmentOutcome.Success(
        LayerUpdateResult(LayerUpdateStatus.UPDATED, LayerUpdateStatus.ABSENT, LayerUpdateStatus.ABSENT)
    )
}
