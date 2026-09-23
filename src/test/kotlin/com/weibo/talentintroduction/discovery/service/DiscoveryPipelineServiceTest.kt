package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.BoundedFulltextHttp
import com.weibo.talentintroduction.config.DeferredReason
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.config.EuropePmcProperties
import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.config.FulltextRequestGate
import com.weibo.talentintroduction.config.OpenAlexBudgetDeferredException
import com.weibo.talentintroduction.config.OpenAlexBudgetSnapshot
import com.weibo.talentintroduction.config.OpenAlexMeteredDestinations
import com.weibo.talentintroduction.config.OpenAlexProperties
import com.weibo.talentintroduction.config.OpenAlexRequestPolicy
import com.weibo.talentintroduction.config.PIPELINE_RESERVED_RESULT_BYTES
import com.weibo.talentintroduction.config.SlowHttpServer
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.DiscoverySourceCursor
import com.weibo.talentintroduction.discovery.domain.DiscoveryTerminalStatus
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
import com.weibo.talentintroduction.discovery.repository.ActiveJobStats
import com.weibo.talentintroduction.discovery.repository.CompletionOutcome
import com.weibo.talentintroduction.discovery.repository.DiscoveryPaperQueueStore
import com.weibo.talentintroduction.discovery.repository.DiscoverySourceCursorRepository
import com.weibo.talentintroduction.discovery.repository.EnqueuePageResult
import com.weibo.talentintroduction.discovery.repository.EnqueuePageStatus
import com.weibo.talentintroduction.discovery.repository.ExpertAcademicEnrichmentJobRepository
import com.weibo.talentintroduction.discovery.repository.JobRow
import com.weibo.talentintroduction.discovery.repository.LaunchOutcome
import com.weibo.talentintroduction.discovery.repository.PipelineDesiredState
import com.weibo.talentintroduction.discovery.repository.PipelinePhase
import com.weibo.talentintroduction.discovery.repository.PipelineRow
import com.weibo.talentintroduction.discovery.repository.PipelineWaitReason
import com.weibo.talentintroduction.discovery.repository.QueueCapacityLimits
import com.weibo.talentintroduction.discovery.repository.QueueIdentityQuality
import com.weibo.talentintroduction.discovery.repository.QueueItemUnit
import com.weibo.talentintroduction.discovery.repository.QueueJobInsert
import com.weibo.talentintroduction.discovery.repository.QueueJobStatus
import com.weibo.talentintroduction.discovery.repository.StreamCursorState
import com.weibo.talentintroduction.discovery.repository.StreamRow
import com.weibo.talentintroduction.expert.domain.EmailValidationResult
import com.weibo.talentintroduction.expert.domain.EligibilityResult
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.CandidateEligibilityService
import com.weibo.talentintroduction.expert.service.EmailValidationService
import com.weibo.talentintroduction.expert.service.ExpertClassificationService
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertRevalidationService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 子计划 02（c2）的窗口 / 暂停 / 公平 / 故障重放测试。
 *
 * 分两层：
 * - **窗口与调度面**（I-3/I-5/I-6/I-7/I-8 的调度与持久化控制）：真实 [DiscoveryPipelineService] +
 *   内存存储（[InMemoryQueueStore]，条件 CAS / 容量账与 SQL 实现同构）+ 可控时钟 + 直接或真实 executor；
 * - **查询规范化 / 身份 / 抽取 / 消费复用**（I-1/I-2/I-4 的数据面）：真实 [ExpertDiscoveryService]
 *   配 Mockito 桩数据源，证明队列与旧流程共用同一套门禁与同一套抽取/消费实现。
 *
 * 事务、行锁、真实并发 CAS 与真实 Flyway 建表由 `DiscoveryPaperQueueRepositoryIT` 在真实 MySQL 上证明，
 * 本文件不重复那部分。
 */
class DiscoveryPipelineServiceTest {

    // 与生产同构：Spring Boot 的 ObjectMapper 会自动注册 jackson-module-kotlin（见 pom 依赖），
    // 队列的元数据/抽取结果都靠它做 Kotlin data class 的往返。
    private val objectMapper = jacksonObjectMapper()

    private val PIPELINE_REQUEST = mapOf("pipelineId" to 1L)

    private val UNUSED_BLOCK: () -> PipelineWindowResult = { error("block 只由 thenAnswer 提供") }

    // ==================================================================
    // 桩：时钟 / 内存存储 / 依赖
    // ==================================================================

    private class FakeClock(var current: Instant) : PipelineTimeSource {
        override fun now(): Instant = current
    }

    private class InMemoryPipeline(
        var desiredState: String = PipelineDesiredState.PAUSED,
        var phase: String = PipelinePhase.QUEUED,
        var criteriaJson: String? = null,
        var criteriaVersion: Int = 0,
        var queryHash: String? = null,
        var generation: Long = 0,
        var ownerToken: String? = null,
        var ownerUntil: Instant? = null,
        var executionId: Long? = null,
        var windowUntil: Instant? = null,
        var nextWakeAt: Instant? = null,
        var waitReason: String? = null,
        var rawScanDone: Boolean = false,
        var queuedPapers: Long = 0,
        var queuedRecords: Long = 0,
        var processedPapers: Long = 0,
        var processedRecords: Long = 0,
        var indexedExperts: Long = 0,
        var duplicateExperts: Long = 0,
        var failedItems: Long = 0,
        var activeCount: Long = 0,
        var payloadBytes: Long = 0,
        var reservedResultBytes: Long = 0,
        var capacityPaused: Boolean = false
    ) {
        fun toRow() = PipelineRow(
            desiredState = desiredState, phase = phase, criteriaJson = criteriaJson,
            criteriaVersion = criteriaVersion, queryHash = queryHash, generation = generation,
            ownerToken = ownerToken, ownerUntil = ownerUntil, executionId = executionId,
            windowUntil = windowUntil, nextWakeAt = nextWakeAt, waitReason = waitReason,
            rawScanDone = rawScanDone, queuedPapers = queuedPapers, queuedRecords = queuedRecords,
            processedPapers = processedPapers, processedRecords = processedRecords,
            indexedExperts = indexedExperts, duplicateExperts = duplicateExperts,
            failedItems = failedItems, activeCount = activeCount, payloadBytes = payloadBytes,
            reservedResultBytes = reservedResultBytes, capacityPaused = capacityPaused
        )
    }

    private class InMemoryStream(
        val id: Long,
        val queryHash: String,
        val source: String,
        var cursorValue: String? = null,
        var cursorState: String = StreamCursorState.ACTIVE,
        var nextAttemptAt: Instant? = null,
        var leaseToken: String? = null,
        var leaseUntil: Instant? = null,
        var sourceError: String? = null,
        var queuedPapers: Long = 0,
        var queuedRecords: Long = 0,
        var processedPapers: Long = 0,
        var processedRecords: Long = 0,
        var indexedExperts: Long = 0,
        var duplicateExperts: Long = 0,
        var failedItems: Long = 0
    ) {
        fun toRow() = StreamRow(
            id = id, pipelineId = 1L, queryHash = queryHash, source = source, epoch = 1L,
            criteriaJson = null, cursorValue = cursorValue, cursorState = cursorState,
            nextAttemptAt = nextAttemptAt, leaseToken = leaseToken, leaseUntil = leaseUntil,
            sourceError = sourceError, queuedPapers = queuedPapers, queuedRecords = queuedRecords,
            processedPapers = processedPapers, processedRecords = processedRecords,
            indexedExperts = indexedExperts, duplicateExperts = duplicateExperts, failedItems = failedItems
        )
    }

    private class InMemoryJob(
        val id: Long,
        val streamId: Long,
        val itemKey: String,
        val identityQuality: String,
        val unit: String,
        val payloadVersion: Int,
        val priority: Int,
        var metadataJson: String,
        var payloadBytes: Long,
        var extractionJson: String? = null,
        var reservedResultBytes: Long = PIPELINE_RESERVED_RESULT_BYTES,
        var status: String = QueueJobStatus.PENDING,
        var attempts: Int = 0,
        var nextAttemptAt: Instant,
        var leaseToken: String? = null,
        var leaseUntil: Instant? = null,
        var generation: Long = 0,
        var lastError: String? = null,
        var completedAt: Instant? = null
    ) {
        fun toRow() = JobRow(
            id = id, streamId = streamId, itemKey = itemKey, identityQuality = identityQuality,
            unit = unit, payloadVersion = payloadVersion, priority = priority,
            metadataJson = metadataJson, extractionJson = extractionJson, payloadBytes = payloadBytes,
            reservedResultBytes = reservedResultBytes, status = status, attempts = attempts,
            nextAttemptAt = nextAttemptAt, leaseToken = leaseToken, leaseUntil = leaseUntil,
            generation = generation, lastError = lastError
        )
    }

    /** 与 SQL 实现同构的内存存储：条件 CAS 失败一律返回「未生效」，容量按数量与字节双口径限制。 */
    private class InMemoryQueueStore(private val clock: FakeClock) : DiscoveryPaperQueueStore {
        val pipeline = InMemoryPipeline()
        val streams = LinkedHashMap<Long, InMemoryStream>()
        val jobs = LinkedHashMap<Long, InMemoryJob>()
        private var streamSeq = 0L
        private var jobSeq = 0L
        var committedPages = 0

        fun seedStream(source: String, queryHash: String): InMemoryStream {
            val stream = InMemoryStream(id = ++streamSeq, queryHash = queryHash, source = source)
            streams[stream.id] = stream
            return stream
        }

        fun seedJob(
            streamId: Long,
            itemKey: String,
            priority: Int = 1,
            unit: String = QueueItemUnit.PAPER,
            status: String = QueueJobStatus.PENDING,
            nextAttemptAt: Instant = clock.current,
            extractionJson: String? = null
        ): InMemoryJob {
            val job = InMemoryJob(
                id = ++jobSeq, streamId = streamId, itemKey = itemKey,
                identityQuality = QueueIdentityQuality.DOI, unit = unit, payloadVersion = 1,
                priority = priority, metadataJson = "{}", payloadBytes = 8,
                extractionJson = extractionJson,
                reservedResultBytes = if (extractionJson == null) PIPELINE_RESERVED_RESULT_BYTES else 0L,
                status = status, nextAttemptAt = nextAttemptAt
            )
            jobs[job.id] = job
            if (job.status in QueueJobStatus.ACTIVE) {
                pipeline.activeCount++
                pipeline.payloadBytes += job.payloadBytes
                pipeline.reservedResultBytes += job.reservedResultBytes
            }
            val stream = requireNotNull(streams[streamId])
            if (unit == QueueItemUnit.RECORD) {
                pipeline.queuedRecords++
                stream.queuedRecords++
            } else {
                pipeline.queuedPapers++
                stream.queuedPapers++
            }
            return job
        }

        private fun streamOf(job: InMemoryJob) = requireNotNull(streams[job.streamId])

        override fun ensurePipeline(now: Instant) = Unit

        override fun findPipeline(): PipelineRow = pipeline.toRow()

        override fun launch(
            criteriaJson: String,
            criteriaVersion: Int,
            queryHash: String,
            rawScanDone: Boolean,
            now: Instant
        ): LaunchOutcome {
            if (pipeline.queryHash != null && pipeline.queryHash == queryHash) {
                pipeline.desiredState = PipelineDesiredState.RUNNING
                pipeline.waitReason = null
                pipeline.nextWakeAt = now
                return LaunchOutcome.Applied(pipeline.toRow(), resumed = true)
            }
            val backlog = pipeline.activeCount > 0 || jobs.values.any {
                it.status != QueueJobStatus.SUCCEEDED && it.status != QueueJobStatus.FAILED
            }
            if (pipeline.queryHash != null && backlog) {
                return LaunchOutcome.Conflict("已有查询仍有 $backlog 条未完成工作")
            }
            pipeline.desiredState = PipelineDesiredState.RUNNING
            pipeline.phase = PipelinePhase.QUEUED
            pipeline.criteriaJson = criteriaJson
            pipeline.criteriaVersion = criteriaVersion
            pipeline.queryHash = queryHash
            pipeline.executionId = null
            pipeline.windowUntil = null
            pipeline.nextWakeAt = now
            pipeline.waitReason = null
            pipeline.rawScanDone = rawScanDone
            pipeline.capacityPaused = false
            return LaunchOutcome.Applied(pipeline.toRow(), resumed = false)
        }

        override fun pause(now: Instant): PipelineRow {
            if (pipeline.desiredState == PipelineDesiredState.RUNNING) pipeline.generation++
            pipeline.desiredState = PipelineDesiredState.PAUSED
            pipeline.waitReason = PipelineWaitReason.MANUAL_PAUSE
            pipeline.nextWakeAt = null
            return pipeline.toRow()
        }

        override fun resume(now: Instant): PipelineRow? {
            if (pipeline.criteriaJson.isNullOrBlank() || pipeline.queryHash.isNullOrBlank()) return null
            pipeline.desiredState = PipelineDesiredState.RUNNING
            pipeline.waitReason = null
            pipeline.nextWakeAt = now
            return pipeline.toRow()
        }

        override fun markDrained(now: Instant): Boolean {
            if (pipeline.phase == PipelinePhase.DRAINED) return false
            pipeline.phase = PipelinePhase.DRAINED
            pipeline.waitReason = null
            pipeline.nextWakeAt = null
            pipeline.capacityPaused = false
            return true
        }

        override fun claimOwner(
            ownerToken: String,
            ownerUntil: Instant,
            windowUntil: Instant,
            now: Instant
        ): Boolean {
            if (pipeline.desiredState != PipelineDesiredState.RUNNING) return false
            val free = pipeline.ownerToken == null || pipeline.ownerUntil == null || !pipeline.ownerUntil!!.isAfter(now)
            if (!free) return false
            if (pipeline.windowUntil == null || !pipeline.windowUntil!!.isAfter(now)) pipeline.windowUntil = windowUntil
            pipeline.ownerToken = ownerToken
            pipeline.ownerUntil = ownerUntil
            pipeline.phase = PipelinePhase.RUNNING
            pipeline.executionId = null
            pipeline.waitReason = null
            pipeline.nextWakeAt = null
            return true
        }

        override fun renewOwner(ownerToken: String, ownerUntil: Instant, now: Instant): Int {
            if (pipeline.ownerToken != ownerToken) return 0
            pipeline.ownerUntil = ownerUntil
            return 1
        }

        override fun releaseOwner(
            ownerToken: String,
            phase: String,
            waitReason: String?,
            nextWakeAt: Instant?,
            now: Instant
        ): Int {
            if (pipeline.ownerToken != ownerToken) return 0
            pipeline.ownerToken = null
            pipeline.ownerUntil = null
            pipeline.phase = phase
            pipeline.waitReason = waitReason
            pipeline.nextWakeAt = nextWakeAt
            return 1
        }

        override fun bindExecutionId(ownerToken: String, executionId: Long, now: Instant): Int {
            if (pipeline.ownerToken != ownerToken) return 0
            pipeline.executionId = executionId
            return 1
        }

        override fun releaseStaleOwner(recoveryUntil: Instant, now: Instant): Int {
            val stale = pipeline.ownerToken != null && pipeline.ownerUntil != null && !pipeline.ownerUntil!!.isAfter(now)
            if (!stale) return 0
            pipeline.ownerToken = null
            pipeline.ownerUntil = null
            pipeline.phase = PipelinePhase.WAITING
            pipeline.waitReason = PipelineWaitReason.OWNER_RECOVERY
            pipeline.nextWakeAt = recoveryUntil
            return 1
        }

        override fun markFaulted(ownerToken: String, reason: String, now: Instant): Int {
            if (pipeline.ownerToken != ownerToken) return 0
            pipeline.phase = PipelinePhase.FAULTED
            pipeline.waitReason = reason
            pipeline.ownerToken = null
            pipeline.ownerUntil = null
            return 1
        }

        override fun markRawScanDone(ownerToken: String, now: Instant): Int {
            if (pipeline.ownerToken != ownerToken) return 0
            pipeline.rawScanDone = true
            return 1
        }

        override fun updatePhase(
            ownerToken: String,
            phase: String,
            waitReason: String?,
            nextWakeAt: Instant?,
            now: Instant
        ): Int {
            if (pipeline.ownerToken != ownerToken) return 0
            pipeline.phase = phase
            pipeline.waitReason = waitReason
            pipeline.nextWakeAt = nextWakeAt
            return 1
        }

        override fun setCapacityPaused(paused: Boolean, now: Instant): Int {
            pipeline.capacityPaused = paused
            return 1
        }

        override fun countActiveJobs(): Long = activeJobs().size.toLong()

        override fun countUnfinishedJobsInStream(streamId: Long): Long =
            jobs.values.count { it.streamId == streamId && it.status in QueueJobStatus.ACTIVE }.toLong()

        override fun ensureStream(
            pipelineId: Long,
            queryHash: String,
            source: String,
            epoch: Long,
            criteriaJson: String?,
            now: Instant
        ): StreamRow = streams.values.firstOrNull { it.queryHash == queryHash && it.source == source }?.toRow()
            ?: seedStream(source, queryHash).toRow()

        override fun findStream(queryHash: String, source: String, epoch: Long): StreamRow? =
            streams.values.firstOrNull { it.queryHash == queryHash && it.source == source }?.toRow()

        override fun findStreamById(streamId: Long): StreamRow? = streams[streamId]?.toRow()

        override fun findStreams(pipelineId: Long): List<StreamRow> = streams.values.map { it.toRow() }

        override fun seedStreamCursorIfPristine(streamId: Long, cursorValue: String?, now: Instant): Boolean {
            if (cursorValue == null) return false
            val stream = streams[streamId] ?: return false
            if (stream.cursorValue != null || stream.cursorState != StreamCursorState.ACTIVE) return false
            if (jobs.values.any { it.streamId == streamId }) return false
            stream.cursorValue = cursorValue
            return true
        }

        override fun claimStreamLease(streamId: Long, leaseToken: String, leaseUntil: Instant, now: Instant): Boolean {
            val stream = streams[streamId] ?: return false
            if (stream.cursorState != StreamCursorState.ACTIVE) return false
            if (stream.nextAttemptAt != null && stream.nextAttemptAt!!.isAfter(now)) return false
            val free = stream.leaseToken == null || stream.leaseUntil == null || !stream.leaseUntil!!.isAfter(now)
            if (!free) return false
            stream.leaseToken = leaseToken
            stream.leaseUntil = leaseUntil
            return true
        }

        override fun releaseStreamLease(streamId: Long, leaseToken: String, now: Instant): Int {
            val stream = streams[streamId] ?: return 0
            if (stream.leaseToken != leaseToken) return 0
            stream.leaseToken = null
            stream.leaseUntil = null
            return 1
        }

        override fun deferStream(
            streamId: Long,
            leaseToken: String,
            nextAttemptAt: Instant,
            reason: String,
            now: Instant
        ): Int {
            val stream = streams[streamId] ?: return 0
            if (stream.leaseToken != leaseToken) return 0
            stream.nextAttemptAt = nextAttemptAt
            stream.sourceError = reason
            stream.leaseToken = null
            stream.leaseUntil = null
            return 1
        }

        override fun recordStreamError(streamId: Long, reason: String, nextAttemptAt: Instant, now: Instant): Int {
            val stream = streams[streamId] ?: return 0
            stream.sourceError = reason
            stream.nextAttemptAt = nextAttemptAt
            stream.leaseToken = null
            stream.leaseUntil = null
            return 1
        }

        override fun enqueuePage(
            streamId: Long,
            leaseToken: String,
            unit: String,
            items: List<QueueJobInsert>,
            cursorValue: String?,
            exhausted: Boolean,
            limits: QueueCapacityLimits,
            now: Instant
        ): EnqueuePageResult {
            val stream = streams[streamId] ?: return EnqueuePageResult(EnqueuePageStatus.LEASE_LOST)
            if (stream.leaseToken != leaseToken) return EnqueuePageResult(EnqueuePageStatus.LEASE_LOST)
            val deduped = LinkedHashMap<String, QueueJobInsert>()
            items.forEach { deduped.putIfAbsent(it.itemKey, it) }
            val existing = jobs.values.filter { it.streamId == streamId }.map { it.itemKey }.toSet()
            val fresh = deduped.values.filterNot { it.itemKey in existing }
            val freshReserved = fresh.size.toLong() * limits.reservedResultBytes
            val freshMetadata = fresh.sumOf { it.payloadBytes }
            if (fresh.isNotEmpty()) {
                val active = pipeline.activeCount + fresh.size
                val bytes = pipeline.payloadBytes + pipeline.reservedResultBytes + freshMetadata + freshReserved
                if (active > limits.highWater || bytes > limits.maxBytes) {
                    return EnqueuePageResult(
                        EnqueuePageStatus.CAPACITY_BLOCKED,
                        duplicateJobs = deduped.size - fresh.size,
                        activeCount = pipeline.activeCount,
                        occupiedBytes = pipeline.payloadBytes + pipeline.reservedResultBytes
                    )
                }
            }
            for (item in fresh) {
                jobs[++jobSeq] = InMemoryJob(
                    id = jobSeq, streamId = streamId, itemKey = item.itemKey,
                    identityQuality = item.identityQuality, unit = item.unit,
                    payloadVersion = item.payloadVersion, priority = if (item.publiclyDownloadable) 1 else 0,
                    metadataJson = item.metadataJson, payloadBytes = item.payloadBytes,
                    reservedResultBytes = limits.reservedResultBytes, nextAttemptAt = now,
                    generation = pipeline.generation
                )
            }
            pipeline.activeCount += fresh.size
            pipeline.payloadBytes += freshMetadata
            pipeline.reservedResultBytes += freshReserved
            pipeline.capacityPaused = false
            if (unit == QueueItemUnit.RECORD) {
                pipeline.queuedRecords += fresh.size
                stream.queuedRecords += fresh.size
            } else {
                pipeline.queuedPapers += fresh.size
                stream.queuedPapers += fresh.size
            }
            stream.cursorValue = cursorValue
            stream.cursorState = if (exhausted) StreamCursorState.EXHAUSTED else StreamCursorState.ACTIVE
            stream.nextAttemptAt = null
            stream.sourceError = null
            stream.leaseToken = null
            stream.leaseUntil = null
            committedPages++
            return EnqueuePageResult(
                EnqueuePageStatus.COMMITTED, insertedJobs = fresh.size,
                duplicateJobs = deduped.size - fresh.size, activeCount = pipeline.activeCount,
                occupiedBytes = pipeline.payloadBytes + pipeline.reservedResultBytes
            )
        }

        private fun activeJobs() = jobs.values.filter { it.status in QueueJobStatus.ACTIVE }

        private fun isDue(job: InMemoryJob, now: Instant): Boolean = when (job.status) {
            QueueJobStatus.PENDING, QueueJobStatus.RETRY_WAIT -> !job.nextAttemptAt.isAfter(now)
            QueueJobStatus.RUNNING -> job.leaseUntil == null || !job.leaseUntil!!.isAfter(now)
            else -> false
        }

        override fun nextDueJob(streamIds: List<Long>, priorityFirst: Boolean, now: Instant): JobRow? {
            val due = activeJobs().filter { it.streamId in streamIds && isDue(it, now) }
            val ordered = if (priorityFirst) {
                due.sortedWith(compareByDescending<InMemoryJob> { it.priority }.thenBy { it.nextAttemptAt }.thenBy { it.id })
            } else {
                due.sortedWith(compareBy<InMemoryJob> { it.nextAttemptAt }.thenBy { it.id })
            }
            return ordered.firstOrNull()?.toRow()
        }

        override fun nextDueOrdinaryJob(streamIds: List<Long>, now: Instant): JobRow? = activeJobs()
            .filter { it.streamId in streamIds && it.priority == 0 && isDue(it, now) }
            .minWithOrNull(compareBy<InMemoryJob> { it.nextAttemptAt }.thenBy { it.id })?.toRow()

        override fun claimJob(
            jobId: Long,
            leaseToken: String,
            leaseUntil: Instant,
            pipelineGeneration: Long,
            now: Instant
        ): Boolean {
            if (pipeline.desiredState != PipelineDesiredState.RUNNING) return false
            if (pipeline.generation != pipelineGeneration) return false
            val job = jobs[jobId] ?: return false
            if (!isDue(job, now)) return false
            job.status = QueueJobStatus.RUNNING
            job.leaseToken = leaseToken
            job.leaseUntil = leaseUntil
            job.generation = pipelineGeneration
            return true
        }

        override fun renewJobLease(
            jobId: Long,
            leaseToken: String,
            generation: Long,
            leaseUntil: Instant,
            now: Instant
        ): Int {
            val job = jobs[jobId] ?: return 0
            if (job.status != QueueJobStatus.RUNNING || job.leaseToken != leaseToken || job.generation != generation) return 0
            job.leaseUntil = leaseUntil
            return 1
        }

        override fun saveExtraction(
            jobId: Long,
            leaseToken: String,
            generation: Long,
            extractionJson: String,
            extractionBytes: Long,
            now: Instant
        ): Boolean {
            val job = jobs[jobId] ?: return false
            if (job.status != QueueJobStatus.RUNNING || job.leaseToken != leaseToken || job.generation != generation) return false
            job.extractionJson = extractionJson
            job.payloadBytes += extractionBytes
            pipeline.payloadBytes += extractionBytes
            pipeline.reservedResultBytes = (pipeline.reservedResultBytes - job.reservedResultBytes).coerceAtLeast(0)
            job.reservedResultBytes = 0
            return true
        }

        private fun applyTerminal(
            job: InMemoryJob,
            status: String,
            attempts: Int,
            nextAttemptAt: Instant,
            lastError: String?,
            now: Instant,
            indexed: Int,
            duplicates: Int
        ) {
            job.status = status
            job.attempts = attempts
            job.nextAttemptAt = nextAttemptAt
            job.leaseToken = null
            job.leaseUntil = null
            job.lastError = lastError
            job.completedAt = now
            val released = if (job.extractionJson.isNullOrEmpty()) job.reservedResultBytes else 0L
            job.reservedResultBytes = 0
            pipeline.activeCount = (pipeline.activeCount - 1).coerceAtLeast(0)
            pipeline.reservedResultBytes = (pipeline.reservedResultBytes - released).coerceAtLeast(0)
            val stream = streamOf(job)
            if (job.unit == QueueItemUnit.RECORD) {
                pipeline.processedRecords++
                stream.processedRecords++
            } else {
                pipeline.processedPapers++
                stream.processedPapers++
            }
            if (status == QueueJobStatus.FAILED) {
                pipeline.failedItems++
                stream.failedItems++
            }
            if (indexed != 0) {
                pipeline.indexedExperts += indexed
                stream.indexedExperts += indexed
            }
            if (duplicates != 0) {
                pipeline.duplicateExperts += duplicates
                stream.duplicateExperts += duplicates
            }
        }

        override fun completeJob(
            jobId: Long,
            leaseToken: String,
            generation: Long,
            status: String,
            attempts: Int,
            nextAttemptAt: Instant,
            lastError: String?,
            now: Instant
        ): CompletionOutcome {
            val job = jobs[jobId] ?: return CompletionOutcome(false, 0, 0)
            if (job.status != QueueJobStatus.RUNNING || job.leaseToken != leaseToken) return CompletionOutcome(false, 0, 0)
            if (job.generation != generation || pipeline.generation != generation) return CompletionOutcome(false, 0, 0)
            applyTerminal(job, status, attempts, nextAttemptAt, lastError, now, indexed = 0, duplicates = 0)
            return CompletionOutcome(true, if (status == QueueJobStatus.FAILED) 1 else 0, 0)
        }

        override fun completeJobWithExperts(
            jobId: Long,
            leaseToken: String,
            generation: Long,
            indexedExperts: Int,
            duplicateExperts: Int,
            attempts: Int,
            nextAttemptAt: Instant,
            now: Instant
        ): CompletionOutcome {
            val job = jobs[jobId] ?: return CompletionOutcome(false, 0, 0)
            if (job.status != QueueJobStatus.RUNNING || job.leaseToken != leaseToken) return CompletionOutcome(false, 0, 0)
            if (job.generation != generation || pipeline.generation != generation) return CompletionOutcome(false, 0, 0)
            applyTerminal(job, QueueJobStatus.SUCCEEDED, attempts, nextAttemptAt, null, now, indexedExperts, duplicateExperts)
            return CompletionOutcome(true, 0, indexedExperts)
        }

        override fun scheduleRetry(
            jobId: Long,
            leaseToken: String,
            generation: Long,
            attempts: Int,
            nextAttemptAt: Instant,
            lastError: String,
            now: Instant
        ): Boolean {
            val job = jobs[jobId] ?: return false
            if (job.status != QueueJobStatus.RUNNING || job.leaseToken != leaseToken || job.generation != generation) return false
            job.status = QueueJobStatus.RETRY_WAIT
            job.attempts = attempts
            job.nextAttemptAt = nextAttemptAt
            job.leaseToken = null
            job.leaseUntil = null
            job.lastError = lastError
            return true
        }

        override fun returnToPending(
            jobId: Long,
            leaseToken: String,
            generation: Long,
            nextAttemptAt: Instant,
            lastError: String?,
            now: Instant
        ): Boolean {
            val job = jobs[jobId] ?: return false
            if (job.status != QueueJobStatus.RUNNING || job.leaseToken != leaseToken || job.generation != generation) return false
            job.status = QueueJobStatus.PENDING
            job.nextAttemptAt = nextAttemptAt
            job.leaseToken = null
            job.leaseUntil = null
            job.lastError = lastError
            return true
        }

        override fun forceFailForLeaseLoss(
            jobId: Long,
            leaseToken: String,
            generation: Long,
            reason: String,
            now: Instant
        ): Boolean {
            val job = jobs[jobId] ?: return false
            if (job.status != QueueJobStatus.RUNNING || job.leaseToken != leaseToken || job.generation != generation) return false
            applyTerminal(job, QueueJobStatus.FAILED, job.attempts, now, reason, now, 0, 0)
            return true
        }

        override fun insertFailedItem(
            streamId: Long,
            item: QueueJobInsert,
            reason: String,
            limits: QueueCapacityLimits,
            now: Instant
        ): Boolean {
            val stream = streams[streamId] ?: return false
            if (jobs.values.any { it.streamId == streamId && it.itemKey == item.itemKey }) return false
            if (pipeline.activeCount + 1 > limits.highWater) return false
            jobs[++jobSeq] = InMemoryJob(
                id = jobSeq, streamId = streamId, itemKey = item.itemKey,
                identityQuality = item.identityQuality, unit = item.unit, payloadVersion = item.payloadVersion,
                priority = 0, metadataJson = item.metadataJson, payloadBytes = item.payloadBytes,
                reservedResultBytes = 0, status = QueueJobStatus.FAILED, nextAttemptAt = now,
                lastError = reason, completedAt = now
            )
            pipeline.payloadBytes += item.payloadBytes
            pipeline.failedItems++
            if (item.unit == QueueItemUnit.RECORD) {
                pipeline.queuedRecords++
                pipeline.processedRecords++
                stream.queuedRecords++
                stream.processedRecords++
            } else {
                pipeline.queuedPapers++
                pipeline.processedPapers++
                stream.queuedPapers++
                stream.processedPapers++
            }
            stream.failedItems++
            return true
        }

        override fun activeJobStats(): ActiveJobStats {
            val active = activeJobs()
            return ActiveJobStats(
                active = active.size.toLong(),
                running = active.count { it.status == QueueJobStatus.RUNNING }.toLong(),
                oldestCreatedAt = active.minOfOrNull { it.nextAttemptAt }
            )
        }

        override fun jobStatusCountsByStream(streamIds: List<Long>): Map<Long, Map<String, Long>> = jobs.values
            .filter { it.streamId in streamIds }
            .groupBy { it.streamId }
            .mapValues { (_, list) -> list.groupingBy { it.status }.eachCount().mapValues { it.value.toLong() } }

        override fun clearTerminalPayloads(completedBefore: Instant, batchSize: Int, now: Instant): Int {
            val batch = terminalBefore(completedBefore)
                .filter { it.payloadBytes > 0 || it.reservedResultBytes > 0 }
                .take(batchSize)
            batch.forEach {
                pipeline.payloadBytes = (pipeline.payloadBytes - it.payloadBytes).coerceAtLeast(0)
                pipeline.reservedResultBytes = (pipeline.reservedResultBytes - it.reservedResultBytes).coerceAtLeast(0)
                it.payloadBytes = 0
                it.reservedResultBytes = 0
                it.metadataJson = ""
                it.extractionJson = null
            }
            return batch.size
        }

        override fun deleteTerminalJobs(completedBefore: Instant, batchSize: Int, now: Instant): Int {
            val batch = terminalBefore(completedBefore).take(batchSize)
            batch.forEach {
                pipeline.payloadBytes = (pipeline.payloadBytes - it.payloadBytes).coerceAtLeast(0)
                pipeline.reservedResultBytes = (pipeline.reservedResultBytes - it.reservedResultBytes).coerceAtLeast(0)
                jobs.remove(it.id)
            }
            return batch.size
        }

        private fun terminalBefore(at: Instant) = jobs.values
            .filter { it.status == QueueJobStatus.SUCCEEDED || it.status == QueueJobStatus.FAILED }
            .filter { it.completedAt != null && !it.completedAt!!.isAfter(at) }
            .sortedBy { it.completedAt }
    }

    // ==================================================================
    // 真实 ExpertDiscoveryService 夹具（I-1/I-2/I-4 的数据面）
    // ==================================================================

    private lateinit var openAlex: OpenAlexDataSource
    private lateinit var openAlexProvider: ObjectProvider<OpenAlexDataSource>
    private lateinit var orcid: OrcidDataSource
    private lateinit var orcidProvider: ObjectProvider<OrcidDataSource>
    private lateinit var emailValidationService: EmailValidationService
    private lateinit var eligibilityService: CandidateEligibilityService
    private lateinit var indexWriterService: ExpertIndexWriterService
    private lateinit var indexService: ExpertIndexService
    private lateinit var expertSearchService: ExpertSearchService
    private lateinit var revalidationService: ExpertRevalidationService
    private lateinit var enrichmentJobService: ExpertAcademicEnrichmentJobService
    private lateinit var enrichmentJobRepository: ExpertAcademicEnrichmentJobRepository
    private lateinit var cursorRepository: DiscoverySourceCursorRepository
    private lateinit var restTemplate: RestTemplate
    private val storedCheckpoints = mutableMapOf<String, DiscoverySourceCursor>()

    /**
     * Kotlin 非空参数 + Mockito 匹配器：`any(Class)` 本身返回 null，会被 Kotlin 的空检查拦下；
     * 这里在**注册匹配器之后**返回一个真实实例兜底，因此既能匹配任意实参，也不会抛 NPE。
     * 兜底值只用于通过编译期/运行期空检查，绝不参与断言。
     */
    private fun <T : Any> anyArg(clazz: Class<T>, fallback: T): T = Mockito.any(clazz) ?: fallback

    private fun anyText(): String = Mockito.anyString() ?: ""

    /** `Mockito.eq` 同样返回 null；需要「匹配特定值」时用它兜底。 */
    private fun <T : Any> eqArg(value: T): T = Mockito.eq(value) ?: value

    private fun anyCriteria(): PaperSearchCriteria = anyArg(PaperSearchCriteria::class.java, PaperSearchCriteria())

    private fun anyEnvelope(): QueuedItemEnvelope = anyArg(QueuedItemEnvelope::class.java, SAMPLE_ENVELOPE)

    private fun anyProgress(): TaskProgress = anyArg(TaskProgress::class.java, FALLBACK_PROGRESS)

    /** `capture() ?: …` 的兜底必须是**纯值**：在那里再调匹配器会让匹配器数量对不上。 */
    private val FALLBACK_PROGRESS = TaskProgress("EXPERT_DISCOVERY", "RUNNING", 0, 0, 0)
    private val FALLBACK_CRITERIA = PaperSearchCriteria()

    private val SAMPLE_ENVELOPE = QueuedItemEnvelope(
        sourceName = "OPENALEX", itemKey = "DOI:sample",
        identityQuality = QueueIdentityQuality.DOI, unit = QueueItemUnit.PAPER,
        payloadVersion = 1, payloadJson = "{}", payloadBytes = 2, publiclyDownloadable = true
    )

    private fun sampleProfile() = ExpertProfile(
        orcidId = "0000-0002-1825-0097", email = "john@ox.ac.uk", givenNames = "John",
        familyNames = "Smith", country = "GB", keyword = null, employment = "Oxford"
    )

    private fun <T : Any> anyProvider(): ObjectProvider<T> {
        @Suppress("UNCHECKED_CAST")
        return Mockito.mock(ObjectProvider::class.java) as ObjectProvider<T>
    }

    @BeforeEach
    fun setUp() {
        openAlex = Mockito.mock(OpenAlexDataSource::class.java)
        openAlexProvider = anyProvider()
        orcid = Mockito.mock(OrcidDataSource::class.java)
        orcidProvider = anyProvider()
        emailValidationService = Mockito.mock(EmailValidationService::class.java)
        eligibilityService = Mockito.mock(CandidateEligibilityService::class.java)
        indexWriterService = Mockito.mock(ExpertIndexWriterService::class.java)
        indexService = Mockito.mock(ExpertIndexService::class.java)
        expertSearchService = Mockito.mock(ExpertSearchService::class.java)
        revalidationService = Mockito.mock(ExpertRevalidationService::class.java)
        enrichmentJobService = Mockito.mock(ExpertAcademicEnrichmentJobService::class.java)
        enrichmentJobRepository = Mockito.mock(ExpertAcademicEnrichmentJobRepository::class.java)
        cursorRepository = Mockito.mock(DiscoverySourceCursorRepository::class.java)
        restTemplate = Mockito.mock(RestTemplate::class.java)
        Mockito.`when`(openAlex.sourceName).thenReturn("OPENALEX")
        Mockito.`when`(openAlex.emailExtractionMethod).thenReturn("FULLTEXT")
        Mockito.`when`(orcid.sourceName).thenReturn("ORCID")
        Mockito.`when`(indexService.indexName(anyArg(ExpertIndexLevel::class.java, ExpertIndexLevel.RAW))).thenReturn("orcid_info")
        Mockito.`when`(cursorRepository.findBySourceName(Mockito.anyString())).thenAnswer { invocation ->
            storedCheckpoints[invocation.getArgument<String>(0)]
        }
        Mockito.`when`(emailValidationService.validate(Mockito.anyString()))
            .thenReturn(EmailValidationResult(level = 2, valid = true))
        Mockito.`when`(eligibilityService.evaluateEligibility(anyArg(ExpertProfile::class.java, sampleProfile()))).thenReturn(EligibilityResult.pass())
        Mockito.`when`(indexWriterService.indexToRaw(Mockito.anyString(), Mockito.anyMap())).thenReturn(true)
    }

    private fun realDiscoveryService(orcidAvailable: Boolean = false): ExpertDiscoveryService {
        Mockito.`when`(orcidProvider.getIfAvailable()).thenReturn(if (orcidAvailable) orcid else null)
        // I-1：队列固定 scope=RND_TARGET，因此 EUROPE_PMC/PMC_OA 不参与；本夹具只用 OPENALEX（必要时 + ORCID）。
        Mockito.`when`(openAlexProvider.getIfAvailable()).thenReturn(openAlex)
        val filler = Mockito.mock(EuropePmcDataSource::class.java)
        Mockito.`when`(filler.sourceName).thenReturn("EUROPE_PMC")
        Mockito.`when`(filler.emailExtractionMethod).thenReturn("FULLTEXT")
        return ExpertDiscoveryService(
            filler, openAlexProvider, anyProvider(), anyProvider(), anyProvider(),
            orcidProvider, anyProvider(),
            emailValidationService, eligibilityService, indexWriterService, indexService,
            revalidationService, expertSearchService, ExpertClassificationService(), restTemplate,
            ElasticsearchProperties(
                baseUrl = "https://es.example.com:9200", username = "elastic", password = "secret",
                rawIndexName = "orcid_info", candidateIndexName = "orcid_info_candidate",
                applicationIndexName = "orcid_info_application"
            ),
            testProperties(), OpenAlexProperties(enabled = false), objectMapper,
            Mockito.mock(TaskProgressStore::class.java), cursorRepository,
            enrichmentJobService, enrichmentJobRepository, Executor { it.run() },
            EuropePmcProperties(enabled = true)
        )
    }

    /**
     * ES 查重桩：邮箱查重用 `POST _search`，ORCID 查重用 `HEAD _doc/{orcid}`（命中即存在，404 即不存在），
     * 两条路径都必须被桩住，否则 mock 返回 null 会被当成「HEAD 成功 = 专家已存在」。
     */
    private fun stubEmailDedup(existingDocId: String? = null) {
        val body = if (existingDocId == null) {
            """{"hits":{"total":{"value":0},"hits":[]}}"""
        } else {
            """{"hits":{"total":{"value":1},"hits":[{"_id":"$existingDocId"}]}}"""
        }
        Mockito.`when`(
            restTemplate.exchange(
                Mockito.anyString(), Mockito.eq(HttpMethod.POST), Mockito.any(HttpEntity::class.java),
                Mockito.eq(JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity.ok(objectMapper.readTree(body)))
        val head = Mockito.`when`(
            restTemplate.exchange(
                Mockito.anyString(), Mockito.eq(HttpMethod.HEAD), Mockito.any(HttpEntity::class.java),
                Mockito.eq(Void::class.java)
            )
        )
        if (existingDocId == null) {
            head.thenThrow(HttpClientErrorException(HttpStatus.NOT_FOUND))
        } else {
            head.thenReturn(ResponseEntity.ok().build())
        }
    }

    private fun paper(
        doi: String? = null,
        pmcId: String? = null,
        pmid: String? = null,
        title: String = "A study",
        downloadUrl: String? = "https://example.org/a.pdf",
        fullText: String? = null
    ) = PaperMetadata(
        pmcId = pmcId, pmid = pmid, doi = doi, title = title, pubYear = 2024, journal = "Nature",
        authors = listOf(PaperAuthor("John", "Smith", "0000-0002-1825-0097", "Oxford, UK")),
        source = "OPENALEX", fullText = fullText, downloadUrl = downloadUrl
    )

    private fun criteria(sources: List<String> = listOf("OPENALEX")) = PaperSearchCriteria(
        keywords = listOf("engineering"),
        affiliationKeywords = listOf("Oxford"),
        excludeCountries = listOf("CN"),
        publicationYearFrom = 2020,
        publicationYearTo = 2026,
        openAccessOnly = true,
        pageSize = 100,
        sources = sources,
        subjectScope = SubjectScopeCatalog.RND_TARGET
    )

    private fun testProperties(
        timeBudget: Duration = Duration.ofMinutes(1),
        queueHighWater: Int = 60,
        queueLowWater: Int = 10,
        pipelineFetchConcurrency: Int = 4
    ) = ExpertDiscoveryProperties(
        enabled = true,
        maxPapersPerRun = 100,
        maxAuthorsPerRun = 100,
        timeBudget = timeBudget,
        pipelineEnabled = true,
        queueHighWater = queueHighWater,
        queueLowWater = queueLowWater,
        queueMaxBytes = 10_000_000L,
        metadataMaxBytes = 4_096,
        extractionMaxBytes = 1_024,
        perHostConcurrency = 2,
        pipelineTick = Duration.ofSeconds(30),
        pipelineFetchConcurrency = pipelineFetchConcurrency
    )

    // ==================================================================
    // 服务装配
    // ==================================================================

    private class Harness(
        val store: InMemoryQueueStore,
        val clock: FakeClock,
        val discovery: ExpertDiscoveryService,
        val progressStore: TaskProgressStore,
        val taskResults: MutableList<PipelineWindowResult>,
        val service: DiscoveryPipelineService
    )

    /**
     * 默认真实发现服务被替换为受控桩：`queueSourceNames` 跟随已建流，采集默认返回**已穷尽空页**，
     * 抽取与消费由各用例自行规定。数据面用例会显式传入真实 [ExpertDiscoveryService]。
     */
    private fun mockDiscovery(store: InMemoryQueueStore): ExpertDiscoveryService {
        val discovery = Mockito.mock(ExpertDiscoveryService::class.java)
        Mockito.`when`(discovery.queueSourceNames(anyCriteria())).thenAnswer {
            store.streams.values.map { it.source }.distinct().ifEmpty { listOf("OPENALEX") }
        }
        // I-1：桩服务也必须给出**按来源稳定**的 per-source hash，否则窗口建流与用例预置的流身份对不上。
        Mockito.`when`(discovery.queueQueryHash(anyText(), anyCriteria())).thenAnswer { invocation ->
            "qh:" + invocation.getArgument<String>(0)
        }
        Mockito.`when`(discovery.collectQueuePage(anyText(), anyCriteria(), Mockito.any(), Mockito.anyInt()))
            .thenReturn(QueuedSourcePage("OPENALEX", QueueItemUnit.PAPER, emptyList(), null, true))
        Mockito.`when`(discovery.extractQueuedItem(anyEnvelope(), anyCriteria(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(QueuedItemExtraction.Extracted("{}", true, 0, false, null))
        Mockito.`when`(discovery.consumeQueuedItem(anyEnvelope(), anyText(), Mockito.any()))
            .thenReturn(QueuedItemConsumption())
        return discovery
    }

    private fun harness(
        store: InMemoryQueueStore? = null,
        properties: ExpertDiscoveryProperties = testProperties(),
        discovery: ExpertDiscoveryService? = null,
        coordinator: Executor = Executor { it.run() },
        collection: Executor = Executor { it.run() },
        fetch: Executor = Executor { it.run() }
    ): Harness {
        val clock = FakeClock(Instant.parse("2026-09-22T00:00:00Z"))
        val queueStore = store ?: InMemoryQueueStore(clock)
        val resolvedDiscovery = discovery ?: mockDiscovery(queueStore)
        val progressStore = Mockito.mock(TaskProgressStore::class.java)
        Mockito.`when`(progressStore.isCancelled(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(progressStore.update(anyText(), anyProgress(), Mockito.any()))
            .thenReturn(true)
        val results = mutableListOf<PipelineWindowResult>()
        val taskExecutionService = Mockito.mock(TaskExecutionService::class.java)
        Mockito.`when`(
            taskExecutionService.runAndRecordWithResult<PipelineWindowResult>(
                anyText(), anyText(), anyArg(Any::class.java, PIPELINE_REQUEST),
                Mockito.any(), Mockito.any(), Mockito.any() ?: UNUSED_BLOCK
            )
        ).thenAnswer { invocation ->
            val onStarted = invocation.getArgument<((Long) -> Unit)?>(3)
            val block = invocation.getArgument<() -> PipelineWindowResult>(5)
            onStarted?.invoke(77L)
            val result = block()
            results += result
            Pair(
                TaskExecution(
                    id = 77L, taskType = DISCOVERY_PIPELINE_TASK_TYPE, triggerType = "PIPELINE",
                    status = result.taskFinalStatus ?: DiscoveryTerminalStatus.SUCCESS,
                    requestPayload = null, resultSummary = null,
                    successCount = result.taskSuccessCount, failureCount = result.taskFailureCount,
                    startedAt = LocalDateTime.now()
                ),
                result
            )
        }
        // OpenAlexRequestPolicy 是 final 类（Mockito 默认无法 mock），且生产装配另有 JDBC 账本；
        // 这里直接用 01 提供的非 Bean 兼容入口（内存账本、无官方校准），与 01 的单元测试同一路径。
        val policy = OpenAlexRequestPolicy(OpenAlexProperties(enabled = false))
        val service = DiscoveryPipelineService(
            repository = queueStore,
            expertDiscoveryService = resolvedDiscovery,
            taskExecutionService = taskExecutionService,
            progressStore = progressStore,
            properties = properties,
            objectMapper = objectMapper,
            openAlexRequestPolicy = policy,
            coordinatorExecutor = coordinator,
            collectionExecutor = collection,
            fetchExecutor = fetch,
            time = clock
        )
        return Harness(queueStore, clock, resolvedDiscovery, progressStore, results, service)
    }

    /** I-1/I-2/I-4 数据面用例：真实发现服务 + 桩存储。 */
    private fun harnessWithRealDiscovery(
        discovery: ExpertDiscoveryService,
        properties: ExpertDiscoveryProperties = testProperties()
    ): Harness = harness(properties = properties, discovery = discovery)

    private fun launch(h: Harness, criteria: PaperSearchCriteria = criteria()) {
        val launched = h.service.launch(criteria, "TEST", includeRawScan = false)
        assertTrue(launched.applied, "launch 必须成功: ${launched.rejection}")
    }

    /**
     * I-1：建一个属于**本次查询**的来源流。若尚未 launch 则先 launch，
     * 保证（query_hash, source）与窗口将要使用的身份完全一致 —— 即**本来源**的规范化 hash
     * （窗口同样按 `queueQueryHash(source, criteria)` 建流）。
     */
    private fun streamFor(h: Harness, source: String = "OPENALEX"): InMemoryStream {
        if (h.store.pipeline.queryHash == null) launch(h)
        val launched = objectMapper.readValue(
            requireNotNull(h.store.pipeline.criteriaJson), PaperSearchCriteria::class.java
        )
        return h.store.seedStream(source, h.discovery.queueQueryHash(source, launched))
    }

    private fun runWindow(h: Harness): PipelineWindowResult {
        val tick = h.service.tick()
        assertTrue(tick.dispatched, "tick 应派发窗口: ${tick.skipReason}")
        return h.taskResults.last()
    }

    private fun extractionJson(email: String = "john@ox.ac.uk") = objectMapper.writeValueAsString(
        EmailExtractionOutcome(
            listOf(AuthorEmail(email, "John", "Smith", true, "Oxford", "0000-0002-1825-0097")), "FULLTEXT"
        )
    )

    // ==================================================================
    // I-1：查询与采集位置独立于处理完成
    // ==================================================================

    @Test
    fun `stream identity is the per-source hash, not the pipeline hash (I-1)`() {
        val discovery = realDiscoveryService()
        Mockito.`when`(openAlex.searchPapers(anyCriteria())).thenReturn(
            PaperSearchResult(listOf(paper(doi = "10.1/a")), null, 1L)
        )
        val h = harnessWithRealDiscovery(discovery)
        val perSource = discovery.queueQueryHash("OPENALEX", criteria())

        // 本源 hash 的敏感度：本来源自己的名字 / 页大小 / OA 必须参与，其他 sources 的写法不参与。
        assertEquals(
            perSource, discovery.queueQueryHash("OPENALEX", criteria(sources = emptyList())),
            "sources 省略与显式列出必须同 hash"
        )
        assertFalse(perSource == discovery.queueQueryHash("CROSSREF", criteria()), "来源名必须参与 hash")
        assertFalse(perSource == discovery.queueQueryHash("OPENALEX", criteria().copy(pageSize = 50)), "页大小必须参与 hash")
        assertFalse(
            perSource == discovery.queueQueryHash("OPENALEX", criteria().copy(openAccessOnly = false)),
            "OA 条件必须参与 hash"
        )

        // 1) 生产路径建流：sources 显式写出（含一个本期不启用的来源）→ stream 身份必须是**本源** hash。
        //    取 PMC_OA 是为了让「流水线级 hash」与「本源 hash」真的不同：PMC_OA 无数据源故不建流，
        //    但它仍进入流水线级条件；本源 OPENALEX 的有效条件两边完全一致。
        launch(h, criteria(sources = listOf("OPENALEX", "PMC_OA")))
        runWindow(h)
        val first = h.store.streams.values.single()
        val pipelineHashA = h.store.pipeline.queryHash
        assertEquals(perSource, first.queryHash, "stream 必须按本源规范化 hash 建流")
        assertNotEquals(pipelineHashA, first.queryHash, "不得把流水线级 hash 当 stream 键")
        assertEquals(StreamCursorState.EXHAUSTED, first.cursorState)
        val streamId = first.id

        // 2) 有效同源条件不变、只把 sources 省略 → 必须命中同一条 stream，EXHAUSTED 不得被遗弃重采。
        clearActiveJobs(h)
        h.clock.current = h.clock.current.plus(Duration.ofDays(1))
        launch(h, criteria(sources = emptyList()))
        assertNotEquals(pipelineHashA, h.store.pipeline.queryHash, "两次 launch 的流水线级身份确实不同")
        // I-4/I-7：窗口只在有可跑工作时才派发，因此这里的队列条目必须是**到点**的。
        h.store.seedJob(streamId, "KEEP-DISPATCH")
        val queuedBeforeSecond = h.store.pipeline.queuedPapers
        runWindow(h)

        val reused = h.store.streams.values.single()
        assertEquals(streamId, reused.id, "sources 省略不得新建 stream")
        assertEquals(perSource, reused.queryHash, "sources 省略必须命中同一本源 hash")
        assertEquals(StreamCursorState.EXHAUSTED, reused.cursorState, "EXHAUSTED 不因 sources 改写而重置")
        assertNull(reused.cursorValue, "已穷尽时不带游标；也绝不回到 ACTIVE")
        assertEquals(queuedBeforeSecond, h.store.pipeline.queuedPapers, "已入队页不得被重头再采一遍")

        // 3) 真正不同的有效条件（页大小）→ 必须得到**另一条** stream。
        clearActiveJobs(h)
        val different = criteria(sources = emptyList()).copy(pageSize = 50)
        launch(h, different)
        h.store.seedJob(streamId, "KEEP-DISPATCH-2")
        runWindow(h)

        assertEquals(2, h.store.streams.values.size, "不同有效条件必须得到不同 stream")
        val second = h.store.streams.values.first { it.id != streamId }
        assertEquals(discovery.queueQueryHash("OPENALEX", different), second.queryHash, "新 stream 同样是本源 hash")
        assertEquals(
            StreamCursorState.EXHAUSTED, h.store.streams.getValue(streamId).cursorState,
            "原 stream 的身份与游标不受其他条件影响"
        )
    }

    /** 清空活跃工作，让下一次 launch 不被「仍有积压」拒绝（身份断言只看 stream 行）。 */
    private fun clearActiveJobs(h: Harness) {
        h.store.jobs.values.forEach { it.status = QueueJobStatus.SUCCEEDED; it.completedAt = h.clock.current }
        h.store.pipeline.activeCount = 0
    }

    @Test
    fun `a whole page commits together with the cursor advance (I-1, I-5)`() {
        val discovery = realDiscoveryService()
        Mockito.`when`(openAlex.searchPapers(anyCriteria())).thenReturn(
            PaperSearchResult(listOf(paper(doi = "10.1/a"), paper(doi = "10.1/b")), "page-2", 2L)
        )
        val h = harnessWithRealDiscovery(discovery)
        launch(h)
        val result = runWindow(h)

        val stream = h.store.streams.values.single()
        assertEquals("page-2", stream.cursorValue, "整页入队成功后 cursor 才推进")
        assertEquals(2L, h.store.pipeline.queuedPapers)
        assertEquals(2L, h.store.pipeline.activeCount)
        assertTrue(h.store.committedPages >= 1, "整页提交事务至少发生一次")
        assertEquals(2, result.collectedPapers, "只统计真正新入队的条目")
        assertEquals(0L, h.store.pipeline.processedPapers, "入队不等于已处理")
    }

    @Test
    fun `a capacity-blocked page keeps the cursor and reports QUEUE_FULL (I-1, I-5)`() {
        val discovery = realDiscoveryService()
        Mockito.`when`(openAlex.searchPapers(anyCriteria())).thenReturn(
            PaperSearchResult(listOf(paper(doi = "10.1/a")), "page-2", 1L)
        )
        val h = harnessWithRealDiscovery(discovery, testProperties(queueHighWater = 20, queueLowWater = 1))
        launch(h)
        runWindow(h)
        val stream = h.store.streams.values.single()
        assertEquals("page-2", stream.cursorValue)

        // 队列已被填满：同一页重放必须整页不提交、cursor 不动。
        val fillerAt = h.clock.current.plus(Duration.ofHours(1))
        while (h.store.pipeline.activeCount < 20) {
            h.store.seedJob(stream.id, "FILL-${h.store.pipeline.activeCount}", nextAttemptAt = fillerAt)
        }
        stream.cursorValue = "page-1"
        stream.nextAttemptAt = null
        // 必须是**新**身份：整页都是重复条目时本来就不占新增容量（I-5 的重放语义）。
        Mockito.`when`(openAlex.searchPapers(anyCriteria())).thenReturn(
            PaperSearchResult(listOf(paper(doi = "10.1/fresh")), "page-9", 1L)
        )
        h.clock.current = h.clock.current.plus(Duration.ofSeconds(20))
        h.service.launch(criteria(), "TEST", includeRawScan = false)
        val blocked = runWindow(h)

        assertEquals("page-1", stream.cursorValue, "容量不足时整页不提交，cursor 必须不变")
        assertTrue(blocked.capacityBlocked, "容量不足必须体现在窗口结果里")
        assertTrue(PipelineWaitReason.QUEUE_FULL in blocked.waitReasons, "必须给出 QUEUE_FULL 等待原因")
        assertTrue(h.store.pipeline.capacityPaused, "必须留下 capacity_paused 标记")
        assertEquals(20L, h.store.pipeline.activeCount, "多个入队者不得越过高水位")
    }

    @Test
    fun `EXHAUSTED survives a new window and a day rollover (I-1, I-7)`() {
        val discovery = realDiscoveryService()
        Mockito.`when`(openAlex.searchPapers(anyCriteria())).thenReturn(
            PaperSearchResult(listOf(paper(doi = "10.1/a")), null, 1L)
        )
        val h = harnessWithRealDiscovery(discovery)
        launch(h)
        runWindow(h)
        val stream = h.store.streams.values.single()
        assertEquals(StreamCursorState.EXHAUSTED, stream.cursorState)

        // 处理掉在手工作，跨过窗口与整天，再启动同查询。
        h.store.jobs.values.forEach { it.status = QueueJobStatus.SUCCEEDED; it.completedAt = h.clock.current }
        h.store.pipeline.activeCount = 0
        h.clock.current = h.clock.current.plus(Duration.ofDays(1))
        Mockito.`when`(openAlex.searchPapers(anyCriteria())).thenThrow(IllegalStateException("不得再次请求"))

        val tick = h.service.tick()
        assertEquals(PipelineTickSkipReason.DRAINED, tick.skipReason, "全部来源 EXHAUSTED 且无活跃工作 → DRAINED")
        assertEquals(
            StreamCursorState.EXHAUSTED,
            h.store.streams.values.single().cursorState,
            "EXHAUSTED 不因重启/次日/新窗口重置"
        )
        assertNull(stream.cursorValue, "已穷尽时不带游标；也绝不回到 ACTIVE")
    }

    @Test
    fun `conflicting legacy v2 cursors replay conservatively (I-1)`() {
        val discovery = realDiscoveryService()
        val base = criteria(sources = emptyList())
        val singleKey = DiscoveryCheckpointCodec.sourceKey("OPENALEX", criteria(sources = listOf("OPENALEX")))
        val allKey = DiscoveryCheckpointCodec.sourceKey("OPENALEX", criteria(sources = emptyList()))

        assertNull(discovery.queueLegacySeedCursor("OPENALEX", base), "没有旧行必须从头开始")

        storedCheckpoints[singleKey] = DiscoverySourceCursor(
            id = 1L, sourceName = singleKey, cursorValue = DiscoveryCheckpointCodec.encode("C-single", false)
        )
        assertEquals("C-single", discovery.queueLegacySeedCursor("OPENALEX", base), "唯一可确认的旧行可作首次种子")

        storedCheckpoints[allKey] = DiscoverySourceCursor(
            id = 2L, sourceName = allKey, cursorValue = DiscoveryCheckpointCodec.encode("C-all", false)
        )
        assertNull(
            discovery.queueLegacySeedCursor("OPENALEX", base),
            "两条冲突的旧游标必须保守重放（返回 null），禁止挑最大游标"
        )

        storedCheckpoints[allKey] = DiscoverySourceCursor(
            id = 2L, sourceName = allKey, cursorValue = DiscoveryCheckpointCodec.encode(null, true)
        )
        assertNull(discovery.queueLegacySeedCursor("OPENALEX", base), "旧 EXHAUSTED 不得当成新流的穷尽")
    }

    @Test
    fun `a matching legacy cursor seeds only a pristine stream (I-1)`() {
        val discovery = realDiscoveryService()
        Mockito.`when`(openAlex.searchPapers(anyCriteria())).thenReturn(
            PaperSearchResult(emptyList(), "page-2", 0L)
        )
        val singleKey = DiscoveryCheckpointCodec.sourceKey("OPENALEX", criteria(sources = listOf("OPENALEX")))
        storedCheckpoints[singleKey] = DiscoverySourceCursor(
            id = 1L, sourceName = singleKey, cursorValue = DiscoveryCheckpointCodec.encode("LEGACY-9", false)
        )
        val h = harnessWithRealDiscovery(discovery)
        launch(h)
        runWindow(h)

        val captor = ArgumentCaptor.forClass(PaperSearchCriteria::class.java)
        Mockito.verify(openAlex, Mockito.atLeastOnce()).searchPapers(captor.capture() ?: FALLBACK_CRITERIA)
        assertEquals("LEGACY-9", captor.allValues.first().cursor, "旧位置必须被用于首次取数")
        assertEquals("page-2", h.store.streams.values.single().cursorValue)
        assertEquals(
            "LEGACY-9",
            DiscoveryCheckpointCodec.decode(storedCheckpoints[singleKey]!!.cursorValue)!!.cursor,
            "旧行不得被改写"
        )
    }

    // ==================================================================
    // I-2：工作唯一性与版本
    // ==================================================================

    @Test
    fun `normalized duplicate DOI yields one identity (I-2)`() {
        val discovery = realDiscoveryService()
        Mockito.`when`(openAlex.searchPapers(anyCriteria())).thenReturn(
            PaperSearchResult(
                listOf(paper(doi = "10.1/AbC"), paper(doi = "https://doi.org/10.1/abc")), null, 2L
            )
        )
        val page = discovery.collectQueuePage("OPENALEX", criteria(), null, 4_096)
        assertEquals(2, page.items.size)
        assertEquals(page.items[0].itemKey, page.items[1].itemKey, "规范化后同一 DOI 必须是同一 item_key")
        assertEquals(QueueIdentityQuality.DOI, page.items[0].identityQuality)
        assertTrue(page.items[0].itemKey.startsWith("DOI:"))
    }

    @Test
    fun `records without an identifier are hashed from canonical metadata, never title-merged (I-2)`() {
        val discovery = realDiscoveryService()
        val first = paper(doi = null, pmcId = null, pmid = null, title = "Same title")
        val secondSame = paper(doi = null, pmcId = null, pmid = null, title = "Same title")
        val thirdDifferent = paper(doi = null, pmcId = null, pmid = null, title = "Same title")
            .copy(pubYear = 2025)
        Mockito.`when`(openAlex.searchPapers(anyCriteria())).thenReturn(
            PaperSearchResult(listOf(first, secondSame, thirdDifferent), null, 3L)
        )
        val page = discovery.collectQueuePage("OPENALEX", criteria(), null, 4_096)
        assertEquals(3, page.items.size)
        assertEquals(QueueIdentityQuality.PAYLOAD_HASH, page.items[0].identityQuality)
        assertEquals(page.items[0].itemKey, page.items[1].itemKey, "完全相同的规范元数据即同一工作")
        assertFalse(page.items[0].itemKey == page.items[2].itemKey, "只有标题相同不得合并（不按标题模糊去重）")
    }

    @Test
    fun `ORCID records are records keyed by the full ORCID (I-2, I-6)`() {
        val discovery = realDiscoveryService(orcidAvailable = true)
        Mockito.`when`(orcid.searchOrcidPage(anyCriteria())).thenReturn(
            OrcidDataSource.OrcidSearchPage(
                listOf(
                    OrcidDataSource.OrcidRecord(
                        orcidId = "0000-0002-1825-0097", givenNames = "John", familyNames = "Smith",
                        emails = listOf("john@ox.ac.uk"), institutionName = "Oxford", country = "GB"
                    )
                ),
                null, 1
            )
        )
        val page = discovery.collectQueuePage("ORCID", criteria(sources = listOf("ORCID")), null, 4_096)
        assertEquals(QueueItemUnit.RECORD, page.unit)
        assertEquals("0000-0002-1825-0097", page.items.single().itemKey, "ORCID 的键就是完整 ORCID")
        assertEquals(QueueIdentityQuality.ORCID, page.items.single().identityQuality)
    }

    @Test
    fun `oversized metadata becomes an observable FAILED item (I-2, I-5)`() {
        val discovery = realDiscoveryService()
        val big = paper(doi = "10.1/big", downloadUrl = null, fullText = "x".repeat(5_000))
        Mockito.`when`(openAlex.searchPapers(anyCriteria())).thenReturn(PaperSearchResult(listOf(big), null, 1L))
        // 单条元数据上限压到 1 KiB：该论文没有可重取地址，全文必须进负载并因此超限。
        val clamped = ExpertDiscoveryProperties(
            enabled = true, maxPapersPerRun = 100, maxAuthorsPerRun = 100,
            timeBudget = Duration.ofMinutes(1), pipelineEnabled = true,
            queueHighWater = 60, queueLowWater = 10, queueMaxBytes = 10_000_000L,
            metadataMaxBytes = 1_024, extractionMaxBytes = 1_024, perHostConcurrency = 2,
            pipelineTick = Duration.ofSeconds(30), pipelineFetchConcurrency = 4
        )
        val h = harnessWithRealDiscovery(discovery, clamped)
        launch(h)
        runWindow(h)

        val failed = h.store.jobs.values.filter { it.status == QueueJobStatus.FAILED }
        assertEquals(1, failed.size, "超限条目必须形成可观测 FAILED 条目，不能静默丢弃")
        assertEquals(QUEUE_PAYLOAD_TOO_LARGE, failed.single().lastError)
        assertEquals(1L, h.store.pipeline.failedItems)
        assertEquals(1L, h.store.pipeline.queuedPapers, "诊断条目仍算「入队过」，与专家产量无关")
        assertEquals(0L, h.store.pipeline.indexedExperts)
        assertEquals(0L, h.store.pipeline.activeCount)
        assertTrue(h.store.jobs.values.none { it.status == QueueJobStatus.PENDING })
    }

    @Test
    fun `an unknown payload version is never consumed (I-2)`() {
        val discovery = realDiscoveryService()
        Mockito.`when`(openAlex.searchPapers(anyCriteria())).thenReturn(
            PaperSearchResult(listOf(paper(doi = "10.1/a")), null, 1L)
        )
        val envelope = discovery.collectQueuePage("OPENALEX", criteria(), null, 4_096).items.single()
        val unknown = envelope.copy(payloadVersion = 99)
        assertEquals(
            QUEUE_UNKNOWN_PAYLOAD_VERSION,
            discovery.consumeQueuedItem(unknown, "{}", null).unrecoverableReason
        )
        val extraction = discovery.extractQueuedItem(unknown, criteria(), 2, 1_024)
        assertTrue(extraction is QueuedItemExtraction.Failed)
        assertFalse((extraction as QueuedItemExtraction.Failed).retryable, "未知版本不得消耗重试预算")
    }

    // ==================================================================
    // I-3：租约与状态机（服务面的 attempts / generation / 退避）
    // ==================================================================

    @Test
    fun `budget deferral and host busy never consume attempts (I-3, I-6)`() {
        val h = harness()
        val stream = streamFor(h)
        stream.cursorValue = null
        stream.cursorState = StreamCursorState.ACTIVE
        Mockito.`when`(h.discovery.collectQueuePage(anyText(), anyCriteria(), Mockito.any(), Mockito.anyInt()))
            .thenReturn(
                QueuedSourcePage(
                    "OPENALEX", QueueItemUnit.PAPER, emptyList(), null, false,
                    deferredUntil = h.clock.current.plus(Duration.ofHours(3)),
                    deferredReason = PipelineWaitReason.DAILY_BUDGET
                )
            )
        val deferred = runWindow(h)
        assertEquals(PipelineTerminationReason.DAILY_BUDGET, deferred.terminationReason)
        assertNotNull(stream.nextAttemptAt, "额度延期必须保存 next_attempt_at")
        assertEquals(PipelineWaitReason.DAILY_BUDGET, stream.sourceError)
        assertEquals(0L, h.store.pipeline.failedItems, "额度等待不是失败")

        // 域名许可不可得：job 退回 PENDING 且 attempts 不变。
        val job = h.store.seedJob(stream.id, "DOI:x", priority = 0, extractionJson = null)
        stream.nextAttemptAt = null
        Mockito.`when`(h.discovery.extractQueuedItem(anyEnvelope(), anyCriteria(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(QueuedItemExtraction.HostBusy)
        // I-4/I-7：上一次的来源延期（本用例 fixture 为 3 小时）把唤醒时间推到了未来，这里必须越过它，
        // 否则 tick 会（正确地）跳过窗口。
        h.clock.current = h.clock.current.plus(Duration.ofHours(4))
        runWindow(h)
        assertEquals(QueueJobStatus.PENDING, h.store.jobs[job.id]!!.status, "HOST_BUSY 必须退回 PENDING")
        assertEquals(0, h.store.jobs[job.id]!!.attempts, "HOST_BUSY 不得消耗 attempts")
        assertEquals(FulltextRequestGate.HOST_BUSY, h.store.jobs[job.id]!!.lastError)
    }

    @Test
    fun `retryable failures back off and the fifth failure terminates (I-3)`() {
        val h = harness()
        val stream = streamFor(h)
        stream.cursorState = StreamCursorState.EXHAUSTED
        val job = h.store.seedJob(stream.id, "DOI:x")
        Mockito.`when`(h.discovery.extractQueuedItem(anyEnvelope(), anyCriteria(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(QueuedItemExtraction.Failed("NETWORK_ERROR", retryable = true))

        val backoffs = mutableListOf<Duration>()
        repeat(4) { round ->
            val before = h.clock.current
            runWindow(h)
            val after = h.store.jobs[job.id]!!
            assertEquals(QueueJobStatus.RETRY_WAIT, after.status, "第 ${round + 1} 次可重试失败仍在重试窗口")
            assertEquals(round + 1, after.attempts)
            backoffs += Duration.between(before, after.nextAttemptAt)
            h.clock.current = after.nextAttemptAt
        }
        assertEquals(
            listOf(Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofMinutes(30)),
            backoffs,
            "退避必须是 30s/2m/10m/30m"
        )

        runWindow(h)
        assertEquals(QueueJobStatus.FAILED, h.store.jobs[job.id]!!.status, "第 5 次失败必须终止")
        assertEquals(5, h.store.jobs[job.id]!!.attempts)
        assertEquals(1L, h.store.pipeline.failedItems)
    }

    @Test
    fun `a non-retryable failure goes straight to FAILED (I-3)`() {
        val h = harness()
        val stream = streamFor(h)
        stream.cursorState = StreamCursorState.EXHAUSTED
        val job = h.store.seedJob(stream.id, "DOI:x")
        Mockito.`when`(h.discovery.extractQueuedItem(anyEnvelope(), anyCriteria(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(QueuedItemExtraction.Failed("AUTH_REJECTED", retryable = false))

        runWindow(h)

        assertEquals(QueueJobStatus.FAILED, h.store.jobs[job.id]!!.status)
        assertEquals("AUTH_REJECTED", h.store.jobs[job.id]!!.lastError)
        assertEquals(1, h.store.jobs[job.id]!!.attempts, "鉴权/载荷错误不进入退避重试")
    }

    @Test
    fun `a manual pause lets a claimed job save its extraction but never consume (I-3, I-7)`() {
        val h = harness()
        val stream = streamFor(h)
        stream.cursorState = StreamCursorState.EXHAUSTED
        val job = h.store.seedJob(stream.id, "DOI:x")
        val payload = extractionJson()
        Mockito.`when`(h.discovery.extractQueuedItem(anyEnvelope(), anyCriteria(), Mockito.anyInt(), Mockito.anyInt()))
            .thenAnswer {
                // 人工暂停发生在 job **已经领取之后**：已领取工作允许保存可靠抽取结果。
                h.service.pause()
                QueuedItemExtraction.Extracted(payload, false, 1, true, null)
            }
        launch(h)
        val result = runWindow(h)

        assertEquals(payload, h.store.jobs[job.id]!!.extractionJson, "暂停后的旧 job 仍能保存可靠抽取结果")
        assertEquals(QueueJobStatus.PENDING, h.store.jobs[job.id]!!.status, "暂停后不得 complete")
        assertEquals(PipelineWaitReason.MANUAL_PAUSE, h.store.jobs[job.id]!!.lastError)
        assertEquals(0, h.store.jobs[job.id]!!.attempts, "暂停不消耗 attempts")
        assertEquals(0L, h.store.pipeline.indexedExperts, "暂停后不得新增专家")
        assertEquals(PipelineTerminationReason.MANUAL_PAUSE, result.terminationReason)
        assertEquals(DiscoveryTerminalStatus.CANCELLED, result.taskFinalStatus, "人工暂停必须记 CANCELLED")
        Mockito.verify(h.discovery, Mockito.never())
            .consumeQueuedItem(anyEnvelope(), anyText(), Mockito.any())
    }

    @Test
    fun `pause is persisted and survives restart and a day rollover (I-7)`() {
        val h = harness()
        val stream = streamFor(h)
        stream.cursorState = StreamCursorState.EXHAUSTED
        launch(h)
        val paused = h.service.pause()
        assertEquals(PipelineDesiredState.PAUSED, paused.desiredState)
        assertEquals(PipelineDesiredState.PAUSED, paused.state)
        val generation = h.store.pipeline.generation
        assertEquals(1L, generation, "从 RUNNING 转入 PAUSED 必须递增 generation")

        // 模拟进程重启（同一持久化状态）与日切。
        val restarted = harness(store = h.store)
        restarted.clock.current = h.clock.current.plus(Duration.ofDays(1))
        val tick = restarted.service.tick()
        assertEquals(PipelineTickSkipReason.PAUSED, tick.skipReason, "重启/日切不得解除暂停")
        assertTrue(restarted.taskResults.isEmpty(), "暂停期间不得创建空的 task_execution")
        assertEquals(PipelineDesiredState.PAUSED, restarted.service.status().state)
        assertEquals(generation, h.store.pipeline.generation)

        val resumed = restarted.service.resume()
        assertTrue(resumed.applied)
        assertEquals(PipelineDesiredState.RUNNING, resumed.desiredState)
        assertEquals(generation, h.store.pipeline.generation, "恢复不得新建 epoch/generation")
    }

    @Test
    fun `resume without a configured query is rejected (I-7)`() {
        val h = harness()
        val resumed = h.service.resume()
        assertFalse(resumed.applied)
        assertEquals(PipelineRejectionReason.NOT_CONFIGURED, resumed.rejection?.reason)
    }

    @Test
    fun `the same query is idempotent while a different query with backlog is rejected (I-7)`() {
        val h = harness()
        val stream = streamFor(h)
        stream.cursorState = StreamCursorState.EXHAUSTED
        h.store.seedJob(stream.id, "DOI:x")
        launch(h)

        val same = h.service.launch(criteria(), "TEST", includeRawScan = false)
        assertTrue(same.applied && same.resumed, "同查询必须幂等")

        val other = h.service.launch(criteria().copy(keywords = listOf("physics")), "TEST", includeRawScan = false)
        assertFalse(other.applied, "有积压时切换查询必须被拒绝")
        assertEquals(PipelineRejectionReason.QUERY_CONFLICT, other.rejection?.reason)
    }

    @Test
    fun `a rejected dispatch keeps QUEUED and creates no duplicate window (I-7)`() {
        val h = harness(coordinator = Executor { throw java.util.concurrent.RejectedExecutionException("busy") })
        launch(h)
        val tick = h.service.tick()

        assertFalse(tick.dispatched)
        assertEquals(PipelineTickSkipReason.REJECTED, tick.skipReason)
        assertEquals(PipelinePhase.QUEUED, h.store.pipeline.phase, "被拒绝必须保留 QUEUED 供下次 tick")
        assertNull(h.store.pipeline.ownerToken, "被拒绝必须释放属主，绝不留下孤儿窗口")
        assertTrue(h.taskResults.isEmpty(), "被拒绝不得创建 task_execution")
    }

    @Test
    fun `a live owner suppresses a second window and a stale owner is recovered once (I-7)`() {
        val h = harness()
        launch(h)
        // 另一个实例仍持有有效的窗口属主：本 tick 绝不派发第二个窗口。
        h.store.pipeline.ownerToken = "other-instance"
        h.store.pipeline.ownerUntil = h.clock.current.plus(Duration.ofSeconds(30))
        val owned = h.service.tick()
        assertFalse(owned.dispatched)
        assertEquals(PipelineTickSkipReason.OWNED, owned.skipReason)
        assertTrue(h.taskResults.isEmpty(), "别的实例在跑时不得开第二个窗口")
        assertEquals("other-instance", h.store.pipeline.ownerToken)

        // owner 崩溃（租约过期、token 尚在）：先进入 OWNER_RECOVERY，不立刻重叠新窗口的外呼。
        h.store.pipeline.ownerUntil = h.clock.current.minus(Duration.ofSeconds(1))
        val recovery = h.service.tick()
        assertFalse(recovery.dispatched)
        assertEquals(PipelineTickSkipReason.RECOVERY, recovery.skipReason)
        assertEquals(PipelineWaitReason.OWNER_RECOVERY, recovery.waitReason)
        assertTrue(h.taskResults.isEmpty(), "恢复等待期间不得开新窗口")
        assertNull(h.store.pipeline.ownerToken, "失效属主必须被清除")

        h.clock.current = recovery.nextWakeAt!!
        assertTrue(h.service.tick().dispatched, "收尾窗口结束后才允许新窗口")
        assertEquals(1, h.taskResults.size, "恢复只开一个窗口，绝不重复")
    }

    @Test
    fun `a one-minute window continues the same queue afterwards (I-7)`() {
        val discovery = realDiscoveryService()
        Mockito.`when`(openAlex.searchPapers(anyCriteria())).thenReturn(
            PaperSearchResult(listOf(paper(doi = "10.1/a")), "page-2", 1L),
            PaperSearchResult(listOf(paper(doi = "10.1/b")), "page-3", 1L),
            PaperSearchResult(emptyList(), null, 0L)
        )
        val h = harnessWithRealDiscovery(discovery, testProperties(timeBudget = Duration.ofMinutes(1)))
        launch(h)
        val first = runWindow(h)
        val streamId = h.store.streams.values.single().id
        assertEquals(2L, h.store.pipeline.queuedPapers, "第一窗口按真实页序列采集了两页")
        assertEquals(
            StreamCursorState.EXHAUSTED, h.store.streams.values.single().cursorState,
            "第一窗口已把来源翻到底"
        )

        h.clock.current = h.clock.current.plus(Duration.ofMinutes(31))
        val second = runWindow(h)

        assertEquals(streamId, h.store.streams.values.single().id, "续跑不得新建 stream/epoch")
        assertEquals(
            StreamCursorState.EXHAUSTED, h.store.streams.values.single().cursorState,
            "续跑只处理同一队列的剩余位置，不重头再扫（EXHAUSTED 不因新窗口重置）"
        )
        assertEquals(2L, h.store.pipeline.queuedPapers, "续跑不得重复采集已入队的页")
        assertEquals(first.pipelineId, second.pipelineId, "同一 pipelineId=1")
        assertEquals(
            0L, h.store.pipeline.indexedExperts,
            "续跑只搬运同一队列的剩余位置，不产生新的专家（本用例的抽取未成功）"
        )
    }

    @Test
    fun `drained pipeline is SUCCESS and all sources exhausted is DRAINED (I-7, I-8)`() {
        val h = harness()
        val stream = streamFor(h)
        stream.cursorState = StreamCursorState.EXHAUSTED
        h.store.seedJob(stream.id, "DOI:x", extractionJson = "{}")
        Mockito.`when`(h.discovery.consumeQueuedItem(anyEnvelope(), anyText(), Mockito.any()))
            .thenReturn(QueuedItemConsumption())
        launch(h)
        val result = runWindow(h)

        assertEquals(PipelineTerminationReason.SOURCE_EXHAUSTED, result.terminationReason)
        assertEquals(PipelinePhase.DRAINED, h.store.pipeline.phase)
        assertEquals(DiscoveryTerminalStatus.SUCCESS, result.taskFinalStatus)
        assertEquals(0, result.failedItems)
        assertEquals(PipelineWaitReason.SOURCE_EXHAUSTED, h.service.status().waitReason)
    }

    @Test
    fun `the pipeline never creates empty task_execution records (I-7)`() {
        val h = harness()
        // 初始 PAUSED：不派发、不建任务记录。
        val paused = h.service.tick()
        assertEquals(PipelineTickSkipReason.PAUSED, paused.skipReason)
        assertTrue(h.taskResults.isEmpty(), "暂停时不得创建空 task_execution")

        val stream = streamFor(h)
        stream.cursorState = StreamCursorState.EXHAUSTED
        val drained = h.service.tick()
        assertEquals(PipelineTickSkipReason.DRAINED, drained.skipReason)
        h.service.tick()
        assertTrue(h.taskResults.isEmpty(), "已排空且无操作需求时不得循环创建空 task_execution")
    }

    @Test
    fun `a fully deferred pipeline opens no window and creates no task record (I-4, I-7)`() {
        val h = harness()
        val stream = streamFor(h)
        // 所有来源都在退避（例如 OpenAlex 额度延期）：唯一来源的下次可尝试时间在未来。
        val retryAt = h.clock.current.plus(Duration.ofMinutes(5))
        stream.nextAttemptAt = retryAt
        stream.sourceError = PipelineWaitReason.DAILY_BUDGET

        val tick = h.service.tick()
        assertFalse(tick.dispatched, "没有任何可跑工作时不得开窗口")
        assertEquals(PipelineTickSkipReason.NOT_DUE, tick.skipReason)
        assertEquals(retryAt, tick.nextWakeAt, "唤醒时间必须是来源真正可尝试的时刻")
        assertTrue(h.taskResults.isEmpty(), "被延期的来源不得循环创建空 task_execution")

        // 延期不是终态：到点后同一个 tick 必须恢复派发。
        h.clock.current = retryAt
        assertTrue(h.service.tick().dispatched, "退避到期后必须恢复派发")
    }

    @Test
    fun `a criteria with no enabled source is drained instead of opening empty windows (I-7)`() {
        val h = harness()
        Mockito.`when`(h.discovery.queueSourceNames(anyCriteria())).thenReturn(emptyList())
        launch(h)

        val tick = h.service.tick()
        assertFalse(tick.dispatched, "没有任何启用来源时不得开窗口")
        assertEquals(PipelineTickSkipReason.DRAINED, tick.skipReason)
        h.service.tick()
        assertTrue(h.taskResults.isEmpty(), "没有启用来源时不得循环创建空 task_execution")
    }

    @Test
    fun `a window that drains at the deadline is SUCCESS, not PARTIAL_SUCCESS (I-7, I-8)`() {
        // 生产里抽取 worker 是**独立线程**：收尾判定读到的 activeCount 仍是 1，随后 worker 才完成，
        // 因此窗口会在下一轮循环的截止检查处收尾 —— 那时流水线已经排空。这里用单线程池如实复现。
        val fetch = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "c3-deadline-fetch").apply { isDaemon = true }
        }
        try {
            val h = harness(fetch = fetch)
            val stream = streamFor(h)
            stream.cursorState = StreamCursorState.EXHAUSTED
            h.store.seedJob(stream.id, "DOI:deadline", extractionJson = "{}")
            Mockito.`when`(h.discovery.consumeQueuedItem(anyEnvelope(), anyText(), Mockito.any()))
                .thenAnswer {
                    // 处理最后一条工作时窗口恰好到点：收尾原因因此是 WINDOW_END，但流水线已经排空。
                    h.clock.current = h.clock.current.plus(Duration.ofMinutes(2))
                    QueuedItemConsumption()
                }
            launch(h)

            val result = runWindow(h)

            assertEquals(PipelineTerminationReason.WINDOW_END, result.terminationReason)
            assertTrue(result.drained, "结束时所有来源已穷尽且没有活跃条目")
            assertEquals(DiscoveryTerminalStatus.SUCCESS, result.taskFinalStatus)
            assertEquals(PipelinePhase.DRAINED, h.store.pipeline.phase)
        } finally {
            fetch.shutdownNow()
        }
    }

    @Test
    fun `a source that never advances its cursor becomes a source error instead of spinning (I-1, I-6)`() {
        val discovery = realDiscoveryService()
        Mockito.`when`(openAlex.searchPapers(anyCriteria())).thenReturn(
            PaperSearchResult(listOf(paper(doi = "10.1/a")), "page-2", 1L)
        )
        val h = harnessWithRealDiscovery(discovery)
        launch(h)
        val result = runWindow(h)

        val stream = h.store.streams.values.single()
        assertEquals("NO_PROGRESS", stream.sourceError, "反复返回同一游标必须变成可观测来源错误")
        assertNotNull(stream.nextAttemptAt, "来源错误必须带下次尝试时间（退避，不空转）")
        assertEquals(StreamCursorState.ACTIVE, stream.cursorState, "来源错误不是穷尽")
        assertEquals(PipelineTerminationReason.SOURCE_ERROR, result.terminationReason)
        assertTrue(
            h.store.committedPages <= 4,
            "同一页最多重放有限次就必须退避（实际 ${h.store.committedPages} 次）"
        )
    }

    // ==================================================================
    // I-4：先保存抽取结果，再幂等消费
    // ==================================================================

    @Test
    fun `an already-saved extraction is consumed without downloading again (I-4)`() {
        val h = harness()
        val stream = streamFor(h)
        stream.cursorState = StreamCursorState.EXHAUSTED
        h.store.seedJob(stream.id, "DOI:x", extractionJson = extractionJson())
        Mockito.`when`(h.discovery.consumeQueuedItem(anyEnvelope(), anyText(), Mockito.any()))
            .thenReturn(QueuedItemConsumption(indexedExperts = 1))
        Mockito.`when`(h.discovery.extractQueuedItem(anyEnvelope(), anyCriteria(), Mockito.anyInt(), Mockito.anyInt()))
            .thenThrow(IllegalStateException("已保存的抽取结果不得再次下载"))
        launch(h)
        runWindow(h)

        Mockito.verify(h.discovery, Mockito.never())
            .extractQueuedItem(anyEnvelope(), anyCriteria(), Mockito.anyInt(), Mockito.anyInt())
        assertEquals(1L, h.store.pipeline.indexedExperts)
    }

    @Test
    fun `a RAW write failure never completes the job while no-email may succeed (I-4)`() {
        val h = harness()
        val stream = streamFor(h)
        stream.cursorState = StreamCursorState.EXHAUSTED
        val job = h.store.seedJob(stream.id, "DOI:x", extractionJson = "{}")
        Mockito.`when`(h.discovery.consumeQueuedItem(anyEnvelope(), anyText(), Mockito.any()))
            .thenReturn(QueuedItemConsumption(rawWriteFailed = true, emailsValid = 1))

        launch(h)
        val first = runWindow(h)
        assertEquals(QueueJobStatus.RETRY_WAIT, h.store.jobs[job.id]!!.status, "RAW 写失败不得 SUCCEEDED")
        assertEquals(1, h.store.jobs[job.id]!!.attempts)
        assertEquals(0, first.failedItems)

        // 「明确无合格邮箱」可以 SUCCEEDED 但新增 0。
        Mockito.`when`(h.discovery.consumeQueuedItem(anyEnvelope(), anyText(), Mockito.any()))
            .thenReturn(QueuedItemConsumption())
        h.clock.current = h.store.jobs[job.id]!!.nextAttemptAt
        val second = runWindow(h)
        assertEquals(QueueJobStatus.SUCCEEDED, h.store.jobs[job.id]!!.status)
        assertEquals(0, second.indexedExperts, "无合格邮箱可以成功但新增 0")
        assertEquals(1, second.processedItems)
    }

    @Test
    fun `replay re-creates the missing enrichment task without rewriting the expert (I-4)`() {
        val discovery = realDiscoveryService()
        Mockito.`when`(openAlex.searchPapers(anyCriteria())).thenReturn(
            PaperSearchResult(listOf(paper(doi = "10.1/a")), null, 1L)
        )
        stubEmailDedup(existingDocId = "existing-doc")
        val envelope = discovery.collectQueuePage("OPENALEX", criteria(), null, 4_096).items.single()

        val consumption = discovery.consumeQueuedItem(envelope, extractionJson(), 42L)

        assertTrue(consumption.succeeded)
        assertEquals(0, consumption.indexedExperts, "已有专家不得重复计新增")
        assertEquals(1, consumption.duplicateExperts)
        Mockito.verify(enrichmentJobService, Mockito.times(1)).enqueue("existing-doc", "OPENALEX", 42L)
        Mockito.verify(indexWriterService, Mockito.never()).indexToRaw(Mockito.anyString(), Mockito.anyMap())
    }

    @Test
    fun `a first-time consumer writes RAW then enqueues enrichment before succeeding (I-4)`() {
        val discovery = realDiscoveryService()
        Mockito.`when`(openAlex.searchPapers(anyCriteria())).thenReturn(
            PaperSearchResult(listOf(paper(doi = "10.1/a")), null, 1L)
        )
        stubEmailDedup()
        val envelope = discovery.collectQueuePage("OPENALEX", criteria(), null, 4_096).items.single()

        val consumption = discovery.consumeQueuedItem(envelope, extractionJson(), 7L)

        assertTrue(consumption.succeeded)
        assertEquals(1, consumption.indexedExperts)
        assertEquals(0, consumption.duplicateExperts)
        val docIdCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(indexWriterService, Mockito.times(1)).indexToRaw(docIdCaptor.capture() ?: "", Mockito.anyMap())
        Mockito.verify(enrichmentJobService, Mockito.times(1))
            .enqueue(docIdCaptor.value, "OPENALEX", 7L)
    }

    @Test
    fun `a failed enrichment enqueue keeps the job out of SUCCEEDED (I-4)`() {
        val discovery = realDiscoveryService()
        Mockito.`when`(openAlex.searchPapers(anyCriteria())).thenReturn(
            PaperSearchResult(listOf(paper(doi = "10.1/a")), null, 1L)
        )
        stubEmailDedup()
        Mockito.doThrow(IllegalStateException("db down")).`when`(enrichmentJobService)
            .enqueue(Mockito.anyString(), Mockito.anyString(), Mockito.any())
        val envelope = discovery.collectQueuePage("OPENALEX", criteria(), null, 4_096).items.single()

        val consumption = discovery.consumeQueuedItem(envelope, extractionJson(), null)

        assertFalse(consumption.succeeded, "补全入队失败不得算成功")
        assertTrue(consumption.enqueueFailed)
        assertEquals(1, consumption.indexedExperts, "RAW 已经写入，但 job 不能 SUCCEEDED；实际 $consumption")
    }

    // ==================================================================
    // I-5：容量（服务面的低水位恢复与字节账）
    // ==================================================================

    @Test
    fun `capacity only resumes below the low-water mark (I-5)`() {
        val h = harness(properties = testProperties(queueHighWater = 20, queueLowWater = 5))
        val stream = streamFor(h)
        stream.cursorState = StreamCursorState.ACTIVE
        stream.cursorValue = "page-1"
        h.store.pipeline.capacityPaused = true
        val fillerAt = h.clock.current.plus(Duration.ofHours(1))
        val filler = (0 until 10).map { h.store.seedJob(stream.id, "FILL-$it", nextAttemptAt = fillerAt) }
        Mockito.`when`(h.discovery.collectQueuePage(anyText(), anyCriteria(), Mockito.any(), Mockito.anyInt()))
            .thenReturn(
                QueuedSourcePage(
                    "OPENALEX", QueueItemUnit.PAPER,
                    listOf(
                        QueuedItemEnvelope(
                            sourceName = "OPENALEX", itemKey = "DOI:resumed",
                            identityQuality = QueueIdentityQuality.DOI, unit = QueueItemUnit.PAPER,
                            payloadVersion = 1, payloadJson = "{}", payloadBytes = 8,
                            publiclyDownloadable = true
                        )
                    ),
                    null, true
                )
            )
        launch(h)

        // 仍高于低水位：必须先停止采集（capacity_paused 未被清除）。
        runWindow(h)
        assertTrue(h.store.pipeline.capacityPaused, "高于低水位时不得恢复生产")
        assertFalse(h.store.jobs.values.any { it.itemKey == "DOI:resumed" })

        // 回落到低水位以下：恢复采集。
        filler.take(6).forEach { it.status = QueueJobStatus.SUCCEEDED; it.completedAt = h.clock.current }
        h.store.pipeline.activeCount = 4
        h.clock.current = h.clock.current.plus(Duration.ofMinutes(5))
        runWindow(h)
        assertTrue(h.store.jobs.values.any { it.itemKey == "DOI:resumed" }, "回落到低水位后必须恢复采集")
    }

    @Test
    fun `in-flight result space is reserved at enqueue and released at terminal (I-5)`() {
        val h = harness()
        val stream = streamFor(h)
        val job = h.store.seedJob(stream.id, "DOI:x")
        assertEquals(PIPELINE_RESERVED_RESULT_BYTES, job.reservedResultBytes, "未抽取的活跃 job 必须预留结果空间")
        assertEquals(PIPELINE_RESERVED_RESULT_BYTES, h.store.pipeline.reservedResultBytes)
        val metadataBytes = h.store.pipeline.payloadBytes
        assertEquals(job.payloadBytes, metadataBytes)

        stream.cursorState = StreamCursorState.EXHAUSTED
        Mockito.`when`(h.discovery.consumeQueuedItem(anyEnvelope(), anyText(), Mockito.any()))
            .thenReturn(QueuedItemConsumption())
        launch(h)
        runWindow(h)

        assertEquals(0L, h.store.pipeline.reservedResultBytes, "终态后预占必须释放")
        assertEquals(
            metadataBytes + "{}".length, h.store.pipeline.payloadBytes,
            "释放的是预留；实际负载 = 元数据 + 已保存的抽取结果"
        )
    }

    @Test
    fun `terminal cleanup releases bytes without touching counters or cursors (I-8)`() {
        val h = harness()
        val stream = streamFor(h)
        val job = h.store.seedJob(stream.id, "DOI:x")
        job.status = QueueJobStatus.SUCCEEDED
        job.completedAt = h.clock.current.minus(Duration.ofDays(8))
        job.payloadBytes = 100
        h.store.pipeline.payloadBytes = 100
        h.store.pipeline.activeCount = 0
        h.store.pipeline.queuedPapers = 5
        h.store.pipeline.processedPapers = 3
        stream.cursorValue = "keep-me"

        val cleared = h.service.cleanup(h.clock.current)

        assertEquals(1, cleared.payloadsCleared)
        assertEquals(0L, h.store.pipeline.payloadBytes, "清理必须释放字节")
        assertEquals(5L, h.store.pipeline.queuedPapers, "清理不得重算累计指标")
        assertEquals(3L, h.store.pipeline.processedPapers)
        assertEquals("keep-me", stream.cursorValue, "清理不得重置 stream 游标")
        assertTrue(h.store.jobs.containsKey(job.id), "7 天内只清负载，不删去重键")

        // 90 天后才删除去重键/状态行。
        val removed = h.service.cleanup(h.clock.current.plus(Duration.ofDays(83)))
        assertEquals(1, removed.jobsDeleted)
        assertFalse(h.store.jobs.containsKey(job.id))
        assertEquals(5L, h.store.pipeline.queuedPapers)
    }

    // ==================================================================
    // I-6：公平与外部请求边界
    // ==================================================================

    @Test
    fun `a suspended source does not block the others (I-6)`() {
        val coordinator = Executors.newSingleThreadExecutor()
        val collection = Executors.newFixedThreadPool(4)
        val h = harness(coordinator = coordinator, collection = collection)
        launch(h, criteria(sources = emptyList()))
        val blocked = streamFor(h, "OPENALEX")
        val other = streamFor(h, "ORCID")
        Mockito.`when`(h.discovery.queueSourceNames(anyCriteria())).thenReturn(listOf("OPENALEX", "ORCID"))

        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        Mockito.`when`(h.discovery.collectQueuePage(eqArg("OPENALEX"), anyCriteria(), Mockito.any(), Mockito.anyInt()))
            .thenAnswer {
                entered.countDown()
                release.await(10, TimeUnit.SECONDS)
                QueuedSourcePage("OPENALEX", QueueItemUnit.PAPER, emptyList(), null, true)
            }
        Mockito.`when`(h.discovery.collectQueuePage(eqArg("ORCID"), anyCriteria(), Mockito.any(), Mockito.anyInt()))
            .thenReturn(QueuedSourcePage("ORCID", QueueItemUnit.RECORD, emptyList(), null, true))
        val window = h.service.tick()
        assertTrue(window.dispatched)
        assertTrue(entered.await(10, TimeUnit.SECONDS), "第一个来源必须已经开始取数")

        // 第一个来源被挂起时，第二个来源仍必须被采集并完成（窗口不 join 整页）。
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && other.cursorState != StreamCursorState.EXHAUSTED) {
            Thread.sleep(20)
        }
        assertEquals(StreamCursorState.EXHAUSTED, other.cursorState, "慢来源不得阻塞其他来源")
        Mockito.verify(h.discovery, Mockito.atLeastOnce())
            .collectQueuePage(eqArg("ORCID"), anyCriteria(), Mockito.any(), Mockito.anyInt())
        release.countDown()
        coordinator.shutdown()
        collection.shutdown()
        assertTrue(coordinator.awaitTermination(20, TimeUnit.SECONDS))
        assertTrue(collection.awaitTermination(20, TimeUnit.SECONDS))
    }

    @Test
    fun `real requests hold per-host permits and a third same-host hop is refused (I-6)`() {
        SlowHttpServer(SlowHttpServer.Mode.TRICKLE_BODY, trickleIntervalMs = 50).use { server ->
            val base = RestTemplate()
            val url = "http://127.0.0.1:${server.port}/pdf"
            val workers = Executors.newFixedThreadPool(2)
            val finished = mutableListOf<java.util.concurrent.Future<Throwable?>>()
            repeat(2) {
                finished += workers.submit<Throwable?> {
                    try {
                        FulltextRequestGate.inQueueExtractionScope(2, Instant.now().plusSeconds(3)) {
                            BoundedFulltextHttp.getForObject(
                                base, url, ByteArray::class.java,
                                connectCapMs = 1_000, readCapMs = 1_000, deadline = Instant.now().plusSeconds(2)
                            )
                        }
                        null
                    } catch (e: Throwable) {
                        e
                    }
                }
            }
            // 连接一旦被接受，说明两个真实请求都已经领取了同域许可。
            val deadline = System.currentTimeMillis() + 10_000
            while (server.acceptedCount < 2 && System.currentTimeMillis() < deadline) Thread.sleep(10)
            assertEquals(2, server.acceptedCount, "两个真实请求必须都已经发出")
            assertEquals(2, FulltextRequestGate.inFlightForHost("127.0.0.1"), "在飞请求必须各自持有域名许可")

            // 第三个同域请求必须被明确拒绝并留下 HOST_BUSY 标志，绝不排队或越界。
            val busyScope = FulltextRequestGate.inQueueExtractionScope(2, Instant.now().plusSeconds(3)) {
                val thrown = assertThrows(FulltextRequestGate.HostBusyException::class.java) {
                    FulltextRequestGate.acquireHop(URI.create(url))!!.use { }
                }
                assertTrue(thrown.message!!.contains(FulltextRequestGate.HOST_BUSY))
                FulltextRequestGate.hostBusyInScope()
            }
            assertTrue(busyScope, "许可不可得时必须在作用域内留下 HOST_BUSY 标志")

            // 预算到期后真实请求结束，许可必须全部归还（不泄漏并发额度）。
            finished.forEach { assertNotNull(it.get(20, TimeUnit.SECONDS), "预算内的细水长流必须被截断") }
            val drainDeadline = System.currentTimeMillis() + 10_000
            while (FulltextRequestGate.inFlightForHost("127.0.0.1") > 0 && System.currentTimeMillis() < drainDeadline) {
                Thread.sleep(10)
            }
            assertEquals(0, FulltextRequestGate.inFlightForHost("127.0.0.1"), "响应流结束后许可必须归还")
            workers.shutdown()
            assertTrue(workers.awaitTermination(20, TimeUnit.SECONDS))
        }
    }
    @Test
    fun `the queue scope refuses metered destinations and strips cross-origin credentials (I-6)`() {
        val metered = "https://${OpenAlexMeteredDestinations.CONTENT_HOST}/works/W1.pdf"
        val thrown = assertThrows(Exception::class.java) {
            FulltextRequestGate.inQueueExtractionScope(2, Instant.now().plusSeconds(30)) {
                BoundedFulltextHttp.getForObject(
                    RestTemplate(), metered, ByteArray::class.java,
                    connectCapMs = 1_000, readCapMs = 1_000, deadline = Instant.now().plusSeconds(2)
                )
            }
        }
        assertTrue(
            generateSequence(thrown as Throwable?) { it.cause }.any {
                it is FulltextRequestGate.MeteredDestinationException
            },
            "计量目的地必须被拒绝，而不是当公开 PDF 下载（实际异常：$thrown）"
        )

        val factory = org.springframework.http.client.SimpleClientHttpRequestFactory()
        val crossOrigin = factory.createRequest(URI.create("https://evil.example.org/a.pdf"), HttpMethod.GET)
        crossOrigin.headers.set("Authorization", "Bearer secret")
        FulltextRequestGate.stripCrossOriginCredential(
            crossOrigin, FulltextRequestGate.originKey(URI.create("https://api.openalex.org/works"))
        )
        assertNull(crossOrigin.headers.getFirst("Authorization"), "跨 origin 的跳不得携带凭证")

        val sameOrigin = factory.createRequest(URI.create("https://api.openalex.org/a.pdf"), HttpMethod.GET)
        sameOrigin.headers.set("Authorization", "Bearer secret")
        FulltextRequestGate.stripCrossOriginCredential(
            sameOrigin, FulltextRequestGate.originKey(URI.create("https://api.openalex.org/works"))
        )
        assertEquals("Bearer secret", sameOrigin.headers.getFirst("Authorization"), "同 origin 保留凭证")
    }

    @Test
    fun `budget deferral of one source does not stop the other sources (I-6)`() {
        val h = harness()
        launch(h, criteria(sources = emptyList()))
        val deferredSource = streamFor(h, "OPENALEX")
        val healthy = streamFor(h, "ORCID")
        Mockito.`when`(h.discovery.queueSourceNames(anyCriteria())).thenReturn(listOf("OPENALEX", "ORCID"))
        Mockito.`when`(
            h.discovery.collectQueuePage(eqArg("OPENALEX"), anyCriteria(), Mockito.any(), Mockito.anyInt())
        ).thenReturn(
            QueuedSourcePage(
                "OPENALEX", QueueItemUnit.PAPER, emptyList(), null, false,
                deferredUntil = h.clock.current.plus(Duration.ofHours(2)),
                deferredReason = PipelineWaitReason.DAILY_BUDGET
            )
        )
        Mockito.`when`(
            h.discovery.collectQueuePage(eqArg("ORCID"), anyCriteria(), Mockito.any(), Mockito.anyInt())
        ).thenReturn(
            QueuedSourcePage(
                "ORCID", QueueItemUnit.RECORD,
                listOf(
                    QueuedItemEnvelope(
                        sourceName = "ORCID", itemKey = "0000-0002-1825-0097",
                        identityQuality = QueueIdentityQuality.ORCID, unit = QueueItemUnit.RECORD,
                        payloadVersion = 1, payloadJson = "{}", payloadBytes = 8, publiclyDownloadable = true
                    )
                ),
                null, true
            )
        )
        runWindow(h)

        assertEquals(PipelineWaitReason.DAILY_BUDGET, deferredSource.sourceError, "被延期的来源记录等待原因")
        assertNotNull(deferredSource.nextAttemptAt)
        assertEquals(StreamCursorState.EXHAUSTED, healthy.cursorState, "其他来源必须照常采集到穷尽")
        assertEquals(1L, h.store.pipeline.queuedRecords, "ORCID 记录计入 queuedRecords，不是 queuedPapers")
        assertEquals(0L, h.store.pipeline.queuedPapers)
    }

    @Test
    fun `the oldest ordinary task is claimed within ten priority claims (I-6)`() {
        val h = harness(properties = testProperties(pipelineFetchConcurrency = 4))
        val stream = streamFor(h)
        stream.cursorState = StreamCursorState.EXHAUSTED
        repeat(25) { h.store.seedJob(stream.id, "PRIORITY-$it", priority = 1, extractionJson = "{}") }
        h.store.seedJob(stream.id, "ORDINARY", priority = 0, extractionJson = "{}")

        val claimed = Collections.synchronizedList(mutableListOf<String>())
        Mockito.`when`(h.discovery.consumeQueuedItem(anyEnvelope(), anyText(), Mockito.any()))
            .thenAnswer { invocation ->
                claimed += invocation.getArgument<QueuedItemEnvelope>(0).itemKey
                QueuedItemConsumption()
            }
        launch(h)
        runWindow(h)

        assertTrue(claimed.contains("ORDINARY"), "最老普通任务必须被领取")
        val position = claimed.indexOf("ORDINARY")
        assertTrue(position <= 10, "每 10 个高优先任务内必须至少取 1 个最老普通任务（实际位置 $position）")
        assertEquals(26, claimed.size)
        assertEquals(26, claimed.distinct().size, "同一 job 不得被同一窗口重复领取")
    }

    // ==================================================================
    // I-8：计数与终止原因一致性
    // ==================================================================

    @Test
    fun `repeated completion does not double count (I-8)`() {
        val h = harness()
        val stream = streamFor(h)
        val job = h.store.seedJob(stream.id, "DOI:x")
        val at = h.clock.current
        assertTrue(h.store.claimJob(job.id, "t1", at.plusSeconds(120), 0L, at))
        assertTrue(h.store.completeJobWithExperts(job.id, "t1", 0L, 1, 0, 0, at, at).applied)
        val after = h.store.pipeline.indexedExperts
        assertEquals(1L, after)

        assertFalse(
            h.store.completeJobWithExperts(job.id, "t1", 0L, 1, 0, 0, at, at).applied,
            "已终态的行再次 complete 必须影响 0 行"
        )
        assertEquals(after, h.store.pipeline.indexedExperts, "重复 complete 不得重复计数")
    }

    @Test
    fun `paper ORCID and expert counters stay separate (I-8)`() {
        val h = harness()
        val paperStream = streamFor(h, "OPENALEX")
        val recordStream = streamFor(h, "ORCID")
        paperStream.cursorState = StreamCursorState.EXHAUSTED
        recordStream.cursorState = StreamCursorState.EXHAUSTED
        Mockito.`when`(h.discovery.queueSourceNames(anyCriteria())).thenReturn(listOf("OPENALEX", "ORCID"))
        h.store.seedJob(paperStream.id, "DOI:a", unit = QueueItemUnit.PAPER, extractionJson = "{}")
        h.store.seedJob(recordStream.id, "0000-0002-1825-0097", unit = QueueItemUnit.RECORD, extractionJson = "{}")
        Mockito.`when`(h.discovery.consumeQueuedItem(anyEnvelope(), anyText(), Mockito.any()))
            .thenReturn(QueuedItemConsumption(indexedExperts = 1))
        runWindow(h)

        val status = h.service.status()
        assertEquals(1L, status.queuedPapers, "论文与 ORCID 计数必须分开")
        assertEquals(1L, status.queuedRecords)
        assertEquals(1L, status.processedPapers)
        assertEquals(1L, status.processedRecords)
        assertEquals(2L, status.indexedExperts)
        assertEquals(0L, status.queueDepth, "queueDepth 只计活跃 job")
        assertEquals(0L, status.failedItems)
        assertEquals(2, status.sources.size)
    }

    @Test
    fun `window result progress and status agree on the termination reason (I-8)`() {
        val h = harness()
        val stream = streamFor(h)
        stream.cursorState = StreamCursorState.EXHAUSTED
        h.store.seedJob(stream.id, "DOI:x", extractionJson = "{}")
        Mockito.`when`(h.discovery.consumeQueuedItem(anyEnvelope(), anyText(), Mockito.any()))
            .thenReturn(QueuedItemConsumption())
        launch(h)
        val windowResult = runWindow(h)

        val progressCaptor = ArgumentCaptor.forClass(TaskProgress::class.java)
        Mockito.verify(h.progressStore, Mockito.atLeastOnce())
            .update(eqArg(DISCOVERY_PIPELINE_TASK_TYPE), progressCaptor.capture() ?: FALLBACK_PROGRESS, Mockito.any())
        val progress = progressCaptor.allValues.last()
        assertEquals(
            windowResult.terminationReason, progress.details?.get("terminationReason"),
            "进度记录与任务结果必须对同一终止原因达成一致"
        )
        assertEquals(
            DiscoveryTerminalStatus.toProgressStatus(requireNotNull(windowResult.taskFinalStatus)),
            progress.status
        )

        val status = h.service.status()
        assertEquals(PipelineWaitReason.SOURCE_EXHAUSTED, status.waitReason)
        assertEquals(windowResult.queueDepth, status.queueDepth)
        assertEquals(windowResult.indexedExperts.toLong(), status.indexedExperts)
        assertNotNull(status.budget, "状态必须带上 01 的预算快照")
        assertEquals("primary", status.budget.accountScope)
    }

    @Test
    fun `status derives PAUSED over phase and reports per-source detail (I-8)`() {
        val discovery = realDiscoveryService()
        Mockito.`when`(openAlex.searchPapers(anyCriteria())).thenReturn(
            PaperSearchResult(listOf(paper(doi = "10.1/a")), "page-2", 1L)
        )
        val h = harnessWithRealDiscovery(discovery)
        launch(h)
        runWindow(h)
        h.service.pause()

        val status = h.service.status()
        assertEquals(PipelineDesiredState.PAUSED, status.state, "desired_state=PAUSED 时 state 必须派生为 PAUSED")
        assertEquals(PipelinePhase.WAITING, status.phase)
        val source = status.sources.single()
        assertEquals("OPENALEX", source.source)
        assertEquals(StreamCursorState.ACTIVE, source.cursorState)
        assertEquals("page-2", source.cursorValue)
        assertEquals(1L, source.activeJobs)
        assertEquals(0L, status.indexedExperts)
        assertTrue(status.waitReasons.contains(PipelineWaitReason.MANUAL_PAUSE))
    }

    @Test
    fun `runtime validation rejects an inconsistent queue configuration (I-5)`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExpertDiscoveryProperties(queueLowWater = 20_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpertDiscoveryProperties(queueMaxBytes = 1_000L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpertDiscoveryProperties(perHostConcurrency = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpertDiscoveryProperties(pipelineFetchConcurrency = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpertDiscoveryProperties(pipelineTick = Duration.ZERO)
        }
    }
}
