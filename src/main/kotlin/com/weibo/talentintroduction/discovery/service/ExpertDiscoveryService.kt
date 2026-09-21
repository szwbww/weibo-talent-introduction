package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.config.EuropePmcProperties
import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.config.OpenAlexBudgetDeferredException
import com.weibo.talentintroduction.config.OpenAlexProperties
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.DiscoveryResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryStats
import com.weibo.talentintroduction.discovery.domain.DiscoveryTerminalStatus
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult
import com.weibo.talentintroduction.discovery.domain.SourceStats
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.task.service.TaskExecutionSummaryProvider
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import com.weibo.talentintroduction.expert.service.CandidateEligibilityService
import com.weibo.talentintroduction.expert.service.EmailValidationService
import com.weibo.talentintroduction.expert.service.ExpertIdGenerator
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertClassificationService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.expert.service.ExpertRevalidationService
import com.weibo.talentintroduction.discovery.domain.DiscoverySourceCursor
import com.weibo.talentintroduction.discovery.repository.DiscoverySourceCursorRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

@Service
class ExpertDiscoveryService(
    private val europePmc: EuropePmcDataSource,
    private val openAlexProvider: ObjectProvider<OpenAlexDataSource>,
    private val crossrefProvider: ObjectProvider<CrossrefDataSource>,
    private val arxivProvider: ObjectProvider<ArxivDataSource>,
    private val pmcOaProvider: ObjectProvider<PmcOaDataSource>,
    private val orcidProvider: ObjectProvider<OrcidDataSource>,
    private val coreProvider: ObjectProvider<CoreDataSource>,
    private val emailValidationService: EmailValidationService,
    private val eligibilityService: CandidateEligibilityService,
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val expertIndexService: ExpertIndexService,
    private val revalidationService: ExpertRevalidationService,
    private val expertSearchService: ExpertSearchService,
    private val expertClassificationService: ExpertClassificationService,
    private val restTemplate: RestTemplate,
    private val esProperties: ElasticsearchProperties,
    private val discoveryProperties: ExpertDiscoveryProperties,
    private val openAlexProperties: OpenAlexProperties,
    private val objectMapper: ObjectMapper,
    private val progressStore: TaskProgressStore,
    private val cursorRepository: DiscoverySourceCursorRepository,
    @Qualifier("discoveryFetchExecutor")
    private val discoveryFetchExecutor: Executor,
    private val europePmcProperties: EuropePmcProperties
) {
    private val log = LoggerFactory.getLogger(ExpertDiscoveryService::class.java)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * 游标会在两次运行之间失效（例如 ES/搜索服务端 scroll context）因而不得持久化的来源。
     *
     * DP-4：CORE 已不在此集合内 —— c4 通过 [DiscoveryCheckpointCodec] 为它持久化稳定的 offset
     * envelope。该扩展点保留为显式接缝，且必须保持 CORE 不被重新加入。
     */
    private val nonPersistableCursorSources: Set<String> = emptySet()

    /**
     * I-2: 只读本次查询条件对应的 v2 key。旧条件写下的历史行（裸 source_name）不会被挪用，
     * 继续留在表中作备份；行存在但值不是本版本 envelope 时同样按「无检查点」处理。
     */
    private fun loadSourceCheckpoint(sourceName: String, criteria: PaperSearchCriteria): SourceCheckpoint {
        if (sourceName in nonPersistableCursorSources) return SourceCheckpoint.EMPTY
        val key = DiscoveryCheckpointCodec.sourceKey(sourceName, criteria)
        val stored = try {
            cursorRepository.findBySourceName(key)?.cursorValue
        } catch (e: Exception) {
            log.warn("Failed to load checkpoint for {} ({}): {}", sourceName, key, e.message)
            return SourceCheckpoint.EMPTY
        }
        if (stored == null) return SourceCheckpoint.EMPTY
        val decoded = DiscoveryCheckpointCodec.decode(stored)
        if (decoded == null) {
            log.warn(
                "[{}] 检查点 {} 的值不是 {} envelope，按无检查点处理（旧值保留作备份）",
                sourceName, key, DiscoveryCheckpointCodec.QUERY_VERSION
            )
            return SourceCheckpoint.EMPTY
        }
        return decoded
    }

    /**
     * I-1/I-2: 在安全边界写入检查点。[papersDelta] 是本次调用新增的处理量，累计进
     * `papers_processed_total`（该列只是计数，绝不用于反推恢复位置）。
     */
    private fun persistSourceCheckpoint(
        sourceName: String,
        criteria: PaperSearchCriteria,
        resumeCursor: String?,
        exhausted: Boolean,
        papersDelta: Int
    ) {
        if (sourceName in nonPersistableCursorSources) return
        val key = DiscoveryCheckpointCodec.sourceKey(sourceName, criteria)
        val cursorValue = DiscoveryCheckpointCodec.encode(resumeCursor, exhausted)
        try {
            val now = LocalDateTime.now()
            val existing = cursorRepository.findBySourceName(key)
            val entity = if (existing != null) {
                existing.copy(
                    cursorValue = cursorValue,
                    papersProcessedTotal = existing.papersProcessedTotal + papersDelta,
                    lastRunAt = now,
                    updatedAt = now
                )
            } else {
                DiscoverySourceCursor(
                    sourceName = key,
                    cursorValue = cursorValue,
                    papersProcessedTotal = papersDelta.toLong(),
                    lastRunAt = now,
                    updatedAt = now
                )
            }
            cursorRepository.save(entity)
            log.info(
                "[{}] 检查点已保存: key={}, state={}, cursor={}, 本次新增处理 {}",
                sourceName, key, if (exhausted) CheckpointState.EXHAUSTED else CheckpointState.ACTIVE,
                resumeCursor?.take(30) ?: "null", papersDelta
            )
        } catch (e: Exception) {
            log.warn("Failed to save checkpoint for {} ({}): {}", sourceName, key, e.message)
        }
    }

    private fun recordTerminalSourceFailure(sourceStats: SourceStats, reason: String) {
        sourceStats.failureReasons.merge(reason, 1) { a, b -> a + b }
        sourceStats.sourceFailureCount++
    }

    private enum class DedupResult { EXISTS, NOT_FOUND, ERROR }

    private fun snapshotErrors(stats: DiscoveryStats): List<String> {
        return stats.errors.asSequence().map { it.take(500) }.take(100).toList()
    }

    private fun snapshotFailureReasons(sourceStats: SourceStats): Map<String, Int> {
        return sourceStats.failureReasons.entries.sortedByDescending { it.value }.take(20)
            .associate { it.key to it.value }
    }

    private fun snapshotFilterReasons(sourceStats: SourceStats): Map<String, Int> {
        return sourceStats.filterReasons.entries.sortedByDescending { it.value }.take(20)
            .associate { it.key to it.value }
    }

    private fun snapshotRejectReasons(sourceStats: SourceStats): Map<String, Int> {
        val snapshot = HashMap(sourceStats.failureReasons)
        if (sourceStats.emailsRejected > 0) snapshot["EMAIL_INVALID"] = sourceStats.emailsRejected
        if (sourceStats.duplicates > 0) snapshot["DUPLICATE"] = sourceStats.duplicates
        if (sourceStats.dedupErrors > 0) snapshot["DEDUP_ERROR"] = sourceStats.dedupErrors
        if (sourceStats.rawWriteFailed > 0) snapshot["RAW_WRITE_FAILED"] = sourceStats.rawWriteFailed
        return snapshot
    }

    private fun computeBatchRejectReasons(before: Map<String, Int>, after: Map<String, Int>): Map<String, Int>? {
        val delta = mutableMapOf<String, Int>()
        for ((key, afterCount) in after) {
            val diff = afterCount - before.getOrDefault(key, 0)
            if (diff > 0) delta[key] = diff
        }
        return delta.ifEmpty { null }
    }

    private fun buildBySourceDetails(stats: DiscoveryStats): Map<String, Any> {
        val bySource = mutableMapOf<String, Any>()
        stats.bySource.forEach { (name, ss) ->
            bySource[name] = mapOf(
                "extractionMethod" to ss.extractionMethod,
                "papersSearched" to ss.papersSearched,
                "papersSkippedNoId" to ss.papersSkippedNoId,
                "fulltextAttempted" to ss.fulltextAttempted,
                "fulltextObtained" to ss.fulltextObtained,
                "pdfDownloadFailed" to ss.pdfDownloadFailed,
                "pdfParseFailed" to ss.pdfParseFailed,
                "noEmailInFulltext" to ss.noEmailInFulltext,
                "authorsExtracted" to ss.authorsExtracted,
                "emailsValid" to ss.emailsValid,
                "emailsRejected" to ss.emailsRejected,
                "duplicates" to ss.duplicates,
                "dedupErrors" to ss.dedupErrors,
                "indexed" to ss.indexed,
                "rawWriteFailed" to ss.rawWriteFailed,
                "promoted" to ss.promoted,
                "promotionFailed" to ss.promotionFailed,
                "filtered" to ss.filtered,
                "filterReasons" to snapshotFilterReasons(ss),
                "failureReasons" to snapshotFailureReasons(ss),
                "elapsedMs" to ss.elapsedMs,
                "apiRequests" to ss.apiRequests,
                "pendingWork" to ss.pendingWork,
                "sourceFailureCount" to ss.sourceFailureCount,
                "stopReason" to (ss.stopReason ?: "")
            )
        }
        return bySource
    }

    private fun buildSummaryText(stats: DiscoveryStats, totalElapsed: Long, terminalStatus: String): String {
        val sourceSummaries = stats.bySource.map { (name, ss) ->
            "$name 收录 ${ss.indexed}/晋升 ${ss.promoted}"
        }.joinToString(", ")
        // I-4: 源终止失败与按专家计的失败分开说明，不把重试或整源失败算成失败专家数。
        val sourceFailureSegment = if (stats.sourceFailures > 0) {
            " | 源终止失败 ${stats.sourceFailures}/${stats.attemptedSources} 个来源(不计入专家失败)"
        } else {
            ""
        }
        return "发现任务完成[$terminalStatus]: 总耗时 ${totalElapsed}ms | 各平台: $sourceSummaries | " +
            "合计: 论文 ${stats.totalPapers}, 收录 ${stats.indexed}, 晋升 ${stats.promoted}$sourceFailureSegment"
    }

    private fun buildProgressDetails(stats: DiscoveryStats, sourceName: String? = null, method: String? = null): Map<String, Any> {
        val details = mutableMapOf<String, Any>(
            "indexed" to stats.indexed,
            "promoted" to stats.promoted,
            "bySource" to buildBySourceDetails(stats)
        )
        if (sourceName != null) details["currentSource"] = sourceName
        if (method != null) details["currentMethod"] = method
        return details
    }

    private fun resolveEnabledSources(criteria: PaperSearchCriteria): List<AcademicDataSource> {
        val sources = mutableListOf<AcademicDataSource>()
        fun add(provider: () -> AcademicDataSource?, name: String) {
            val src = provider()
            // I4-3: 学科范围排除是「本次不参与」的运行时过滤 —— 六行注册全部保留，
            // 其他 scope 或手动指定 sources 时仍可使用被排除源。
            if (src != null && (criteria.sources.isEmpty() || criteria.sources.contains(name))
                && name !in SubjectScopeCatalog.excludedSources(criteria.subjectScope)) {
                sources.add(src)
            }
        }
        // I4-4: EuropePmcDataSource 是七源中唯一无 @ConditionalOnProperty 的（裸 @Service），
        // 故必须在此显式读 enabled，否则 EUROPE_PMC_ENABLED=false 对定时发现（sources 为空）无效。
        if (europePmcProperties.enabled) add({ europePmc }, europePmc.sourceName)
        add({ pmcOaProvider.getIfAvailable() }, "PMC_OA")
        add({ openAlexProvider.getIfAvailable() }, "OPENALEX")
        add({ crossrefProvider.getIfAvailable() }, "CROSSREF")
        add({ coreProvider.getIfAvailable() }, "CORE")
        add({ arxivProvider.getIfAvailable() }, "ARXIV")
        return sources
    }

    @JvmOverloads
    fun discover(
        criteria: PaperSearchCriteria,
        triggeredBy: String,
        includeRawScan: Boolean = discoveryProperties.includeRawScan
    ): DiscoveryResult {
        val stats = DiscoveryStats()
        val execId = progressStore.getCurrentExecutionId("EXPERT_DISCOVERY")
        val sources = resolveEnabledSources(criteria)
        val startTime = System.currentTimeMillis()
        // I-3: 结果与进度共用的终态；仅在正常路径赋值（异常路径由 catch 记录 FAILED 后重抛）。
        var terminalStatusOfRun = DiscoveryTerminalStatus.SUCCESS

        log.info("发现任务启动: 启用平台=${sources.map { it.sourceName }}, 关键词=${criteria.keywords}, " +
            "年份=${criteria.publicationYearFrom}-${criteria.publicationYearTo}, " +
            "全局限额: 论文 ${discoveryProperties.maxPapersPerRun} / 作者 ${discoveryProperties.maxAuthorsPerRun}")

        try {
            progressStore.update("EXPERT_DISCOVERY", TaskProgress(
                taskType = "EXPERT_DISCOVERY", status = "RUNNING",
                batchNumber = 0, processedCount = 0, totalCount = 0,
                message = "正在加载数据源配置..."
            ), execId)

            if (includeRawScan) {
                progressStore.update("EXPERT_DISCOVERY", TaskProgress(
                    taskType = "EXPERT_DISCOVERY", status = "RUNNING",
                    batchNumber = 0, processedCount = 0, totalCount = 0,
                    message = "正在扫描 RAW 索引并晋升..."
                ), execId)
                log.info("开始执行 RAW 晋升扫描与邮箱补全...")
                try {
                    revalidationService.promoteEligibleRawExperts()
                } catch (e: Exception) {
                    log.warn("Failed to run RAW promotion scan during discovery", e)
                }
                try {
                    backfillRawEmailsAndPromote(100)
                } catch (e: Exception) {
                    log.warn("Failed to run RAW email backfill during discovery", e)
                }
            }

            for (source in sources) {
                if (progressStore.isCancelled("EXPERT_DISCOVERY")) break
                stats.refreshGlobalCounts()
                if (stats.totalAuthors >= discoveryProperties.maxAuthorsPerRun) break
                if (stats.totalPapers >= discoveryProperties.maxPapersPerRun) break

                val outcome = discoverFromSource(source, criteria, stats)
                log.info("[{}] 本次运行结束: stopReason={}, exhausted={}, resumeCursor={}",
                    source.sourceName, outcome.stopReason, outcome.exhausted,
                    outcome.resumeCursor?.take(50) ?: "null")
            }

            discoverFromOrcid(criteria, stats)
            stats.refreshGlobalCounts()

            val totalElapsed = System.currentTimeMillis() - startTime
            val wasCancelled = progressStore.isCancelled("EXPERT_DISCOVERY")
            // I-3: 结果与进度共用同一个终态决策函数。
            terminalStatusOfRun = DiscoveryTerminalStatus.decide(
                cancelled = wasCancelled,
                attemptedSources = stats.attemptedSources,
                failedSources = stats.failedSources,
                pendingWork = stats.pendingSources > 0
            )
            val details = buildProgressDetails(stats).toMutableMap()
            details["terminalStatus"] = terminalStatusOfRun
            details["summaryText"] = buildSummaryText(stats, totalElapsed, terminalStatusOfRun)

            if (wasCancelled) {
                log.info("发现任务取消: 论文=${stats.totalPapers}, 收录=${stats.indexed}, 晋升=${stats.promoted}")
                progressStore.update("EXPERT_DISCOVERY", TaskProgress(
                    taskType = "EXPERT_DISCOVERY",
                    status = DiscoveryTerminalStatus.toProgressStatus(terminalStatusOfRun),
                    batchNumber = -1, processedCount = stats.totalPapers.toLong(),
                    totalCount = stats.totalPapers.toLong(),
                    message = buildProgressMessage(terminalStatusOfRun, stats),
                    details = details, errors = snapshotErrors(stats)
                ), execId)
                return DiscoveryResult(triggeredBy, stats, wasCancelled = true, summaryText = details["summaryText"] as String)
            }

            val totalValidEmails = stats.bySource.values.sumOf { it.emailsValid }
            val sourceSummaries = stats.bySource.map { (name, ss) -> "$name 收录 ${ss.indexed}/晋升 ${ss.promoted}" }.joinToString(", ")
            log.info("发现任务完成[$terminalStatusOfRun]: 总耗时 ${totalElapsed}ms | 各平台: $sourceSummaries | " +
                "合计: 论文 ${stats.totalPapers}, 作者候选 ${stats.totalAuthors}, " +
                "邮箱有效 $totalValidEmails (无效 ${stats.emailRejected}), 收录 ${stats.indexed}, 晋升 ${stats.promoted}, " +
                "源终止失败 ${stats.sourceFailures}, 待续跑来源 ${stats.pendingSources}")

            progressStore.update("EXPERT_DISCOVERY", TaskProgress(
                taskType = "EXPERT_DISCOVERY",
                status = DiscoveryTerminalStatus.toProgressStatus(terminalStatusOfRun),
                batchNumber = -1, processedCount = stats.totalPapers.toLong(),
                totalCount = stats.totalPapers.toLong(),
                message = buildProgressMessage(terminalStatusOfRun, stats),
                details = details, errors = snapshotErrors(stats)
            ), execId)
        } catch (e: Exception) {
            stats.refreshGlobalCounts()
            val totalElapsed = System.currentTimeMillis() - startTime
            val details = buildProgressDetails(stats).toMutableMap()
            details["summaryText"] = buildSummaryText(stats, totalElapsed, DiscoveryTerminalStatus.FAILED)
            progressStore.update("EXPERT_DISCOVERY", TaskProgress(
                taskType = "EXPERT_DISCOVERY", status = "FAILED",
                batchNumber = -1, processedCount = stats.totalPapers.toLong(), totalCount = 0,
                message = "失败: ${e.message}",
                details = details, errors = snapshotErrors(stats)
            ), execId)
            throw e
        }
        stats.refreshGlobalCounts()
        val finalElapsed = System.currentTimeMillis() - startTime
        return DiscoveryResult(
            triggeredBy, stats,
            summaryText = buildSummaryText(stats, finalElapsed, terminalStatusOfRun)
        )
    }

    /** I-3/I-4: 面向操作端的终态说明，源失败单独说明，不与专家级失败混在一起。 */
    private fun buildProgressMessage(terminalStatus: String, stats: DiscoveryStats): String {
        val sourceFailureSegment = if (stats.sourceFailures > 0) {
            "，源终止失败 ${stats.sourceFailures}/${stats.attemptedSources} 个来源"
        } else ""
        val pendingSegment = if (stats.pendingSources > 0) {
            "，待续跑来源 ${stats.pendingSources}"
        } else ""
        return when (terminalStatus) {
            DiscoveryTerminalStatus.CANCELLED ->
                "已取消: 论文 ${stats.totalPapers}, 收录 ${stats.indexed}, 晋升 ${stats.promoted}"
            DiscoveryTerminalStatus.FAILED ->
                "失败: 全源搜索失败$sourceFailureSegment, 论文 ${stats.totalPapers}, 收录 ${stats.indexed}"
            DiscoveryTerminalStatus.PARTIAL_SUCCESS ->
                "部分成功: 论文 ${stats.totalPapers}, 收录 ${stats.indexed}, 晋升 ${stats.promoted}" +
                    sourceFailureSegment + pendingSegment
            else ->
                "完成: 论文 ${stats.totalPapers}, 收录 ${stats.indexed}, 晋升 ${stats.promoted}"
        }
    }

    private fun getSourceLimit(source: AcademicDataSource): Int {
        return source.maxPapersPerSource
    }

    /**
     * I-1: 单来源运行。每一完整消费页后立即持久化 next cursor；首请求失败、部分页、取消与预算停止
     * 都保留进入该页的 cursor；异常与额度延期绝不产出 `exhausted = true`。
     */
    private fun discoverFromSource(
        source: AcademicDataSource,
        criteria: PaperSearchCriteria,
        stats: DiscoveryStats
    ): SourceRunOutcome {
        val sourceStats = stats.getOrCreateSourceStats(source.sourceName, source.emailExtractionMethod)
        val sourceStartTime = System.currentTimeMillis()
        val execId = progressStore.getCurrentExecutionId("EXPERT_DISCOVERY")
        val sourceLimit = getSourceLimit(source)

        val checkpoint = loadSourceCheckpoint(source.sourceName, criteria)
        // I-2: 检查点是游标权威；EXHAUSTED 表示本次扫描周期从头重开，
        // 调用方显式给出的 cursor 只在本次运行起点生效（保持既有入口行为）。
        val enteringCursor: String? = if (checkpoint.exhausted) {
            criteria.cursor
        } else {
            checkpoint.cursor ?: criteria.cursor
        }
        if (enteringCursor != null) {
            log.info("[{}] 从上次检查点继续: {}", source.sourceName, enteringCursor.take(50))
        } else if (checkpoint.exhausted) {
            log.info("[{}] 上次已穷尽，本次扫描周期从头重开", source.sourceName)
        }

        log.info("[{}] 开始: 方式={}, 本源限额={}", source.sourceName, source.emailExtractionMethod, sourceLimit)

        val runCriteria = if (enteringCursor != null) criteria.copy(cursor = enteringCursor) else criteria
        var cursor: String? = enteringCursor
        // I-1: 可安全续跑的位置。进入某页失败时保持该页入口值，绝不用 null 覆盖已有进度。
        var resumeCursor: String? = enteringCursor
        var persistedPapers = 0
        var batchNumber = 0
        var sourcePapersProcessed = 0
        var consecutiveFailures = 0
        var circuitBreakerTripped = false
        var exhausted = false
        var stopReason = DiscoveryStopReason.EXHAUSTED

        /** 在页边界（或运行结束时）落盘检查点；delta 保证 papers_processed_total 不重复计数。 */
        fun persistCheckpoint(nextCursor: String?, reason: String, pageExhausted: Boolean) {
            val delta = sourceStats.papersSearched - persistedPapers
            persistSourceCheckpoint(source.sourceName, criteria, nextCursor, pageExhausted, delta)
            persistedPapers = sourceStats.papersSearched
            resumeCursor = nextCursor
            exhausted = pageExhausted
            stopReason = reason
        }

        while (true) {
            if (progressStore.isCancelled("EXPERT_DISCOVERY")) {
                log.info("[{}] 已取消, 当前批次={}", source.sourceName, batchNumber)
                stopReason = DiscoveryStopReason.CANCELLED
                break
            }
            stats.refreshGlobalCounts()
            if (stats.totalPapers >= discoveryProperties.maxPapersPerRun) {
                stopReason = DiscoveryStopReason.GLOBAL_PAPER_LIMIT
                break
            }
            if (stats.totalAuthors >= discoveryProperties.maxAuthorsPerRun) {
                stopReason = DiscoveryStopReason.GLOBAL_AUTHOR_LIMIT
                break
            }
            if (sourcePapersProcessed >= sourceLimit) {
                stopReason = DiscoveryStopReason.SOURCE_LIMIT
                break
            }

            sourceStats.apiRequests++

            var batch: PaperSearchResult? = null
            try {
                batch = source.searchPapers(runCriteria.copy(cursor = cursor))
            } catch (e: OpenAlexBudgetDeferredException) {
                // 额度延期不是搜索失败：保留进入该页的 cursor，也绝不置 exhausted。
                log.warn("[{}] 额度延期至 {}，保留进入该页的游标 {}", source.sourceName, e.resetAt,
                    resumeCursor?.take(50) ?: "null")
                stopReason = DiscoveryStopReason.BUDGET_DEFERRED
                break
            } catch (e: HttpStatusCodeException) {
                val code = e.statusCode.value()
                if (code == 429 || code == 503) {
                    consecutiveFailures++
                    sourceStats.failureReasons.merge("RATE_LIMITED", 1) { a, b -> a + b }
                    if (consecutiveFailures >= 5) {
                        circuitBreakerTripped = true
                        recordTerminalSourceFailure(sourceStats, "CIRCUIT_BREAKER")
                        log.warn("[{}] 连续 5 次限流/不可用，熔断", source.sourceName)
                        stopReason = DiscoveryStopReason.CIRCUIT_BREAKER
                        break
                    }
                    Thread.sleep(1000)
                    continue
                }
                recordTerminalSourceFailure(sourceStats, "SEARCH_FAILED")
                stopReason = DiscoveryStopReason.SEARCH_FAILED
                break
            } catch (e: Exception) {
                recordTerminalSourceFailure(sourceStats, "SEARCH_FAILED")
                log.error("[{}] 搜索失败: {}", source.sourceName, e.message)
                stopReason = DiscoveryStopReason.SEARCH_FAILED
                break
            }

            if (batch == null || batch.papers.isEmpty()) {
                val next = batch?.nextCursor
                if (next == null) {
                    // V-2: 无记录且无 nextCursor 才判穷尽。
                    stopReason = DiscoveryStopReason.EXHAUSTED
                    exhausted = true
                    break
                }
                // V-2: 过滤后空页只要还有 nextCursor 就继续翻页。
                cursor = next
                persistCheckpoint(next, DiscoveryStopReason.EMPTY_PAGE, false)
                continue
            }
            consecutiveFailures = 0
            batchNumber++

            val papersBefore = sourceStats.papersSearched
            val indexedBefore = sourceStats.indexed
            val rawWriteFailedBefore = sourceStats.rawWriteFailed
            val rejectReasonsBefore = snapshotRejectReasons(sourceStats)

            var limitReached = false
            var consumedInBatch = 0

            val extractions = parallelExtractOutcomes(batch.papers, source)
            for ((paper, extraction) in extractions) {
                if (consumedInBatch % 10 == 0 && progressStore.isCancelled("EXPERT_DISCOVERY")) { limitReached = true; break }
                stats.refreshGlobalCounts()
                if (stats.totalPapers >= discoveryProperties.maxPapersPerRun) { limitReached = true; break }
                if (stats.totalAuthors >= discoveryProperties.maxAuthorsPerRun) { limitReached = true; break }
                if (sourcePapersProcessed >= sourceLimit) { limitReached = true; break }
                sourceStats.papersSearched++
                sourcePapersProcessed++
                consumedInBatch++
                consumeOutcome(paper, extraction, source, stats, sourceStats)
            }

            // I-1: 完整消费页 = 页内全部论文处理完且 RAW 持久化未失败；有失败则保留进入该页的 cursor 以便重放。
            val rawWriteFailedInPage = sourceStats.rawWriteFailed - rawWriteFailedBefore
            if (rawWriteFailedInPage > 0) {
                log.warn("[{}] 批次 {} 内有 {} 篇 RAW 写入失败，保留进入该页的 cursor 以便重放",
                    source.sourceName, batchNumber, rawWriteFailedInPage)
            }
            if (!limitReached && rawWriteFailedInPage == 0) {
                val nextCursor = batch.nextCursor
                // 没有下一页即穷尽；完整消费的页在进入下一页前立即落盘。
                persistCheckpoint(
                    nextCursor,
                    if (nextCursor == null) DiscoveryStopReason.EXHAUSTED else DiscoveryStopReason.PAGE_CONSUMED,
                    nextCursor == null
                )
            }

            val batchProcessed = sourceStats.papersSearched - papersBefore
            val batchPassed = sourceStats.indexed - indexedBefore
            val batchRejected = batchProcessed - batchPassed
            val batchRejectReasons = computeBatchRejectReasons(
                rejectReasonsBefore,
                snapshotRejectReasons(sourceStats)
            )

            log.info("[{}] 批次 {}: 论文 +{} (累计 {}/{}), 获全文 {}, 抽到邮箱 {}, 有效 {}, 重复 {}, 收录 {}, 晋升 {}",
                source.sourceName, batchNumber, batchProcessed,
                sourceStats.papersSearched, sourceLimit,
                sourceStats.fulltextObtained, sourceStats.authorsExtracted,
                sourceStats.emailsValid, sourceStats.duplicates,
                sourceStats.indexed, sourceStats.promoted)

            stats.refreshGlobalCounts()
            val persistedBatchNumber = stats.nextBatchSeq()
            progressStore.update("EXPERT_DISCOVERY", TaskProgress(
                taskType = "EXPERT_DISCOVERY", status = "RUNNING",
                batchNumber = persistedBatchNumber,
                processedCount = stats.totalPapers.toLong(),
                totalCount = discoveryProperties.maxPapersPerRun.toLong(),
                message = "[${source.sourceName}] 批次 $batchNumber: 论文 ${sourceStats.papersSearched}/$sourceLimit, 收录 ${sourceStats.indexed}, 晋升 ${sourceStats.promoted}",
                details = buildProgressDetails(stats, source.sourceName, source.emailExtractionMethod),
                errors = snapshotErrors(stats),
                batchProcessed = batchProcessed,
                batchPassed = batchPassed,
                batchRejected = batchRejected.coerceAtLeast(0),
                batchRejectReasons = batchRejectReasons
            ), execId)

            if (rawWriteFailedInPage > 0) {
                stopReason = DiscoveryStopReason.RAW_WRITE_INCOMPLETE
                break
            }
            if (limitReached || circuitBreakerTripped) {
                stopReason = if (circuitBreakerTripped) {
                    DiscoveryStopReason.CIRCUIT_BREAKER
                } else {
                    DiscoveryStopReason.PAGE_PARTIAL
                }
                break
            }
            if (exhausted) break
            cursor = batch.nextCursor
        }

        val elapsed = System.currentTimeMillis() - sourceStartTime
        sourceStats.elapsedMs = elapsed
        // I-1: 运行结束时落盘终止状态；失败/部分页/取消都停在 resumeCursor（进入该页的位置）。
        persistCheckpoint(resumeCursor, stopReason, exhausted)
        sourceStats.pendingWork = !exhausted
        sourceStats.stopReason = stopReason

        log.info("[{}] 完成: 耗时 ${elapsed}ms, API请求 ${sourceStats.apiRequests} 次 | " +
            "漏斗: 搜索 ${sourceStats.papersSearched} → 尝试全文 ${sourceStats.fulltextAttempted} → 获全文 ${sourceStats.fulltextObtained}" +
            " (PDF下载失败 ${sourceStats.pdfDownloadFailed}, 解析失败 ${sourceStats.pdfParseFailed})" +
            " → 抽到邮箱 ${sourceStats.authorsExtracted} (无邮箱 ${sourceStats.noEmailInFulltext})" +
            " → 有效 ${sourceStats.emailsValid} (无效 ${sourceStats.emailsRejected})" +
            " → 去重后 ${sourceStats.indexed} (重复 ${sourceStats.duplicates})" +
            " → 收录L3 ${sourceStats.indexed} → 晋升L2 ${sourceStats.promoted}" +
            " (资格淘汰 ${sourceStats.filtered})" +
            (if (sourceStats.failureReasons.isNotEmpty()) ", 失败原因 ${sourceStats.failureReasons}" else ""),
            source.sourceName, elapsed, sourceStats.apiRequests,
            sourceStats.papersSearched, sourceStats.fulltextAttempted, sourceStats.fulltextObtained,
            sourceStats.pdfDownloadFailed, sourceStats.pdfParseFailed,
            sourceStats.authorsExtracted, sourceStats.noEmailInFulltext,
            sourceStats.emailsValid, sourceStats.emailsRejected,
            sourceStats.indexed, sourceStats.duplicates,
            sourceStats.indexed, sourceStats.promoted,
            sourceStats.filtered)

        return SourceRunOutcome(resumeCursor, exhausted, stopReason)
    }

    /**
     * I-1: ORCID 分页同样只在完整消费一页后推进 offset；部分页、取消与失败保留进入该页的 offset；
     * 空页即穷尽，EXHAUSTED 允许下一个扫描周期重开（c4 在此之上换成分片 offset envelope）。
     */
    private fun discoverFromOrcid(criteria: PaperSearchCriteria, stats: DiscoveryStats): SourceRunOutcome? {
        val orcid = orcidProvider.getIfAvailable() ?: return null
        if (criteria.sources.isNotEmpty() && !criteria.sources.contains(orcid.sourceName)) return null

        val sourceStats = stats.getOrCreateSourceStats(orcid.sourceName, "API_FIELD")
        val execId = progressStore.getCurrentExecutionId("EXPERT_DISCOVERY")
        val sourceStartTime = System.currentTimeMillis()

        val checkpoint = loadSourceCheckpoint(orcid.sourceName, criteria)
        val enteringCursor: String? = if (checkpoint.exhausted) {
            criteria.cursor
        } else {
            checkpoint.cursor ?: criteria.cursor
        }
        if (enteringCursor != null) {
            log.info("[{}] 从上次检查点继续: offset={}", orcid.sourceName, enteringCursor)
        } else if (checkpoint.exhausted) {
            log.info("[{}] 上次已穷尽，本次扫描周期从头重开", orcid.sourceName)
        }

        log.info("[{}] 开始: 方式=API_FIELD", orcid.sourceName)

        val orcidLimit = orcid.maxRecordsPerRun
        var cursor: String? = enteringCursor ?: "0"
        var resumeCursor: String? = enteringCursor
        var persistedRecords = 0
        var batchNumber = 0
        var recordsProcessed = 0
        var exhausted = false
        var stopReason = DiscoveryStopReason.EXHAUSTED

        fun persistCheckpoint(nextOffset: String?, reason: String, pageExhausted: Boolean) {
            val delta = sourceStats.papersSearched - persistedRecords
            persistSourceCheckpoint(orcid.sourceName, criteria, nextOffset, pageExhausted, delta)
            persistedRecords = sourceStats.papersSearched
            resumeCursor = nextOffset
            exhausted = pageExhausted
            stopReason = reason
        }

        while (true) {
            if (progressStore.isCancelled("EXPERT_DISCOVERY")) {
                log.info("[{}] 已取消", orcid.sourceName)
                stopReason = DiscoveryStopReason.CANCELLED
                break
            }
            stats.refreshGlobalCounts()
            if (stats.totalAuthors >= discoveryProperties.maxAuthorsPerRun) {
                stopReason = DiscoveryStopReason.GLOBAL_AUTHOR_LIMIT
                break
            }
            if (recordsProcessed >= orcidLimit) {
                stopReason = DiscoveryStopReason.SOURCE_LIMIT
                break
            }

            sourceStats.apiRequests++

            val records = try {
                orcid.searchOrcidRecords(criteria.copy(cursor = cursor))
            } catch (e: Exception) {
                recordTerminalSourceFailure(sourceStats, "SEARCH_FAILED")
                log.error("[{}] 搜索失败: {}", orcid.sourceName, e.message)
                stopReason = DiscoveryStopReason.SEARCH_FAILED
                break
            }
            if (records.isEmpty()) {
                // V-2: 无记录即穷尽。
                stopReason = DiscoveryStopReason.EXHAUSTED
                exhausted = true
                break
            }
            batchNumber++

            val indexedBefore = sourceStats.indexed
            val recordsProcessedBeforeBatch = recordsProcessed
            val rawWriteFailedBefore = sourceStats.rawWriteFailed
            val rejectReasonsBefore = snapshotRejectReasons(sourceStats)

            for (record in records) {
                if (recordsProcessed >= orcidLimit) break
                stats.refreshGlobalCounts()
                if (stats.totalAuthors >= discoveryProperties.maxAuthorsPerRun) break
                sourceStats.papersSearched++
                sourceStats.fulltextObtained++
                recordsProcessed++

                val authorEmails = orcid.orcidRecordToAuthorEmails(record)

                for (authorEmail in authorEmails) {
                    stats.refreshGlobalCounts()
                    if (stats.totalAuthors >= discoveryProperties.maxAuthorsPerRun) break
                    sourceStats.authorsExtracted++

                    val emailResult = emailValidationService.validate(authorEmail.email)
                    if (!emailResult.valid) { sourceStats.emailsRejected++; continue }
                    sourceStats.emailsValid++

                    when (existsInRawIndexByEmail(authorEmail.email)) {
                        DedupResult.EXISTS -> { sourceStats.duplicates++; continue }
                        DedupResult.ERROR -> { sourceStats.dedupErrors++; continue }
                        DedupResult.NOT_FOUND -> {}
                    }
                    if (authorEmail.orcidId != null) {
                        when (existsInRawIndexByOrcid(authorEmail.orcidId)) {
                            DedupResult.EXISTS -> { sourceStats.duplicates++; continue }
                            DedupResult.ERROR -> { sourceStats.dedupErrors++; continue }
                            DedupResult.NOT_FOUND -> {}
                        }
                    }

                    val profile = buildOrcidProfile(record, authorEmail, emailResult.level)
                    val esDocId = ExpertIdGenerator.generate(authorEmail.orcidId ?: record.orcidId, authorEmail.email)
                    val eligibility = eligibilityService.evaluateEligibility(profile)
                    val filterResult = if (eligibility.eligible) "PASSED" else "REJECTED"
                    val rejectReasons = if (eligibility.eligible) emptyList() else eligibility.rejectReasons

                    val profileMap = toIndexMap(profile, null, esDocId, filterResult, rejectReasons)
                    if (!expertIndexWriterService.indexToRaw(esDocId, profileMap)) { sourceStats.rawWriteFailed++; continue }
                    sourceStats.indexed++

                    if (eligibility.eligible) {
                        if (promoteDiscoveredToCandidate(esDocId, profileMap)) sourceStats.promoted++
                        else sourceStats.promotionFailed++
                    } else {
                        sourceStats.filtered++
                        for (reason in rejectReasons) {
                            sourceStats.filterReasons.merge(reason, 1) { a, b -> a + b }
                        }
                    }
                }
            }

            val batchProcessed = recordsProcessed - recordsProcessedBeforeBatch
            val batchPassed = sourceStats.indexed - indexedBefore
            val batchRejected = batchProcessed - batchPassed
            val batchRejectReasons = computeBatchRejectReasons(
                rejectReasonsBefore,
                snapshotRejectReasons(sourceStats)
            )

            stats.refreshGlobalCounts()
            val persistedBatchNumber = stats.nextBatchSeq()
            progressStore.update("EXPERT_DISCOVERY", TaskProgress(
                taskType = "EXPERT_DISCOVERY", status = "RUNNING",
                batchNumber = persistedBatchNumber,
                processedCount = recordsProcessed.toLong(),
                totalCount = orcidLimit.toLong(),
                message = "[${orcid.sourceName}] 批次 $batchNumber: 记录 $recordsProcessed/$orcidLimit, 收录 ${sourceStats.indexed}, 晋升 ${sourceStats.promoted}",
                details = buildProgressDetails(stats, orcid.sourceName, "API_FIELD"),
                errors = snapshotErrors(stats),
                batchProcessed = batchProcessed,
                batchPassed = batchPassed,
                batchRejected = batchRejected.coerceAtLeast(0),
                batchRejectReasons = batchRejectReasons
            ), execId)

            val pageFullyConsumed = recordsProcessed - recordsProcessedBeforeBatch == records.size
            val rawWriteFailedInPage = sourceStats.rawWriteFailed - rawWriteFailedBefore
            if (rawWriteFailedInPage > 0) {
                log.warn("[{}] 批次 {} 内有 {} 条记录 RAW 写入失败，保留进入该页的 offset 以便重放",
                    orcid.sourceName, batchNumber, rawWriteFailedInPage)
            }
            if (pageFullyConsumed && rawWriteFailedInPage == 0) {
                cursor = (cursor?.toIntOrNull()?.plus(records.size))?.toString()
                persistCheckpoint(cursor, DiscoveryStopReason.PAGE_CONSUMED, false)
            } else {
                // I-1: 部分页或页内 RAW 持久化未完成都保留进入该页的 offset，绝不跳过未消费记录。
                persistCheckpoint(
                    resumeCursor,
                    if (rawWriteFailedInPage > 0) {
                        DiscoveryStopReason.RAW_WRITE_INCOMPLETE
                    } else {
                        DiscoveryStopReason.PAGE_PARTIAL
                    },
                    false
                )
                break
            }
        }

        val elapsed = System.currentTimeMillis() - sourceStartTime
        sourceStats.elapsedMs = elapsed
        persistCheckpoint(resumeCursor, stopReason, exhausted)
        sourceStats.pendingWork = !exhausted
        sourceStats.stopReason = stopReason

        log.info("[{}] 完成: 耗时 ${elapsed}ms | " +
            "漏斗: 记录 ${recordsProcessed} → 邮箱 ${sourceStats.authorsExtracted}" +
            " → 有效 ${sourceStats.emailsValid} (无效 ${sourceStats.emailsRejected})" +
            " → 去重后 ${sourceStats.indexed} (重复 ${sourceStats.duplicates})" +
            " → 收录L3 ${sourceStats.indexed} → 晋升L2 ${sourceStats.promoted}" +
            " (资格淘汰 ${sourceStats.filtered})" +
            (if (sourceStats.failureReasons.isNotEmpty()) ", 失败原因 ${sourceStats.failureReasons}" else ""),
            orcid.sourceName, elapsed, recordsProcessed, sourceStats.authorsExtracted,
            sourceStats.emailsValid, sourceStats.emailsRejected,
            sourceStats.indexed, sourceStats.duplicates,
            sourceStats.indexed, sourceStats.promoted, sourceStats.filtered)

        return SourceRunOutcome(resumeCursor, exhausted, stopReason)
    }

    private fun buildOrcidProfile(record: OrcidDataSource.OrcidRecord, authorEmail: AuthorEmail, emailVerifiedLevel: Int): ExpertProfile {
        return ExpertProfile(
            orcidId = record.orcidId,
            email = authorEmail.email.lowercase(Locale.ROOT),
            givenNames = record.givenNames,
            familyNames = record.familyNames,
            country = record.country,
            keyword = null, employment = record.institutionName,
            institution = record.institutionName, lastPublicationYear = null,
            emailSource = "ORCID_PUBLIC", emailVerifiedLevel = emailVerifiedLevel, dataSource = "ORCID"
        )
    }

    private data class PaperExtraction(
        val outcome: EmailExtractionOutcome?,
        val extractionError: String?
    )

    private fun extractOutcome(paper: PaperMetadata, source: AcademicDataSource): PaperExtraction {
        return try {
            PaperExtraction(source.extractAuthorEmails(paper), null)
        } catch (e: Exception) {
            PaperExtraction(null, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun parallelExtractOutcomes(
        papers: List<PaperMetadata>,
        source: AcademicDataSource
    ): List<Pair<PaperMetadata, PaperExtraction>> {
        if (papers.isEmpty()) return emptyList()
        if (discoveryProperties.fetchConcurrency <= 1) {
            return papers.map { it to extractOutcome(it, source) }
        }
        val futures = papers.map { paper ->
            paper to CompletableFuture.supplyAsync({ extractOutcome(paper, source) }, discoveryFetchExecutor)
        }
        return futures.map { (paper, future) -> paper to future.join() }
    }

    private fun consumeOutcome(
        paper: PaperMetadata,
        extraction: PaperExtraction,
        source: AcademicDataSource,
        stats: DiscoveryStats,
        sourceStats: SourceStats
    ) {
        sourceStats.fulltextAttempted++
        if (extraction.extractionError != null) {
            stats.errors += "[${source.sourceName}] 提取失败: ${extraction.extractionError}"
            sourceStats.failureReasons.merge("EXTRACTION_EXCEPTION", 1) { a, b -> a + b }
            return
        }

        val outcome = extraction.outcome!!
        sourceStats.apiRequests += outcome.httpRequests

        if (outcome.failureReason != null) {
            sourceStats.failureReasons.merge(outcome.failureReason, 1) { a, b -> a + b }
            if (outcome.failureReason == "PDF_DOWNLOAD_FAILED") sourceStats.pdfDownloadFailed++
            if (outcome.failureReason == "PDF_PARSE_FAILED") sourceStats.pdfParseFailed++
        }

        if (outcome.emails.isEmpty()) {
            if (outcome.failureReason == "NO_PMC_ID" || outcome.failureReason == "NO_DOI") {
                sourceStats.papersSkippedNoId++
            } else if (outcome.failureReason == null ||
                       outcome.failureReason == "NO_EMAIL_IN_FULLTEXT" ||
                       outcome.failureReason == "NO_EMAIL_IN_TEXT") {
                sourceStats.noEmailInFulltext++
                sourceStats.fulltextObtained++
            } else {
                // PDF_DOWNLOAD_FAILED, PDF_PARSE_FAILED, NO_FULLTEXT, etc. — fulltext not obtained
            }
            return
        }

        sourceStats.fulltextObtained++

        for (authorEmail in outcome.emails) {
            stats.refreshGlobalCounts()
            if (stats.totalAuthors >= discoveryProperties.maxAuthorsPerRun) return
            sourceStats.authorsExtracted++

            val emailResult = emailValidationService.validate(authorEmail.email)
            if (!emailResult.valid) { sourceStats.emailsRejected++; continue }
            sourceStats.emailsValid++

            when (existsInRawIndexByEmail(authorEmail.email)) {
                DedupResult.EXISTS -> { sourceStats.duplicates++; continue }
                DedupResult.ERROR -> { sourceStats.dedupErrors++; continue }
                DedupResult.NOT_FOUND -> {}
            }
            if (authorEmail.orcidId != null) {
                when (existsInRawIndexByOrcid(authorEmail.orcidId)) {
                    DedupResult.EXISTS -> { sourceStats.duplicates++; continue }
                    DedupResult.ERROR -> { sourceStats.dedupErrors++; continue }
                    DedupResult.NOT_FOUND -> {}
                }
            }

            val profile = buildProfile(paper, authorEmail, emailResult.level)
            val esDocId = ExpertIdGenerator.generate(authorEmail.orcidId, authorEmail.email)
            val eligibility = eligibilityService.evaluateEligibility(profile)
            val filterResult = if (eligibility.eligible) "PASSED" else "REJECTED"
            val rejectReasons = if (eligibility.eligible) emptyList() else eligibility.rejectReasons

            val profileMap = toIndexMap(profile, paper, esDocId, filterResult, rejectReasons)
            if (!expertIndexWriterService.indexToRaw(esDocId, profileMap)) { sourceStats.rawWriteFailed++; continue }
            sourceStats.indexed++

            if (eligibility.eligible) {
                if (promoteDiscoveredToCandidate(esDocId, profileMap)) sourceStats.promoted++
                else sourceStats.promotionFailed++
            } else {
                sourceStats.filtered++
                for (reason in rejectReasons) {
                    sourceStats.filterReasons.merge(reason, 1) { a, b -> a + b }
                }
            }
        }
    }

    private fun processPaper(paper: PaperMetadata, source: AcademicDataSource, stats: DiscoveryStats, sourceStats: SourceStats) {
        consumeOutcome(paper, extractOutcome(paper, source), source, stats, sourceStats)
    }

    private fun buildProfile(paper: PaperMetadata, authorEmail: AuthorEmail, emailVerifiedLevel: Int): ExpertProfile {
        return ExpertProfile(
            orcidId = authorEmail.orcidId ?: "",
            email = authorEmail.email.lowercase(Locale.ROOT),
            givenNames = authorEmail.givenNames, familyNames = authorEmail.familyNames,
            country = inferCountryFromAffiliation(authorEmail.affiliation),
            keyword = null, employment = authorEmail.affiliation, institution = authorEmail.affiliation,
            lastPublicationYear = paper.pubYear, emailSource = "PAPER_FULLTEXT",
            emailVerifiedLevel = emailVerifiedLevel, dataSource = paper.source,
            externalIds = buildExternalIds(paper, authorEmail),
            institutionType = authorEmail.institutionType
        )
    }

    private fun toIndexMap(profile: ExpertProfile, paper: PaperMetadata?, esDocId: String,
                           filterResult: String, rejectReasons: List<String>): Map<String, Any?> {
        val now = LocalDateTime.now().format(dateFormatter)
        return mapOf(
            "orcidId" to esDocId, "email" to profile.email,
            "givenNames" to profile.givenNames, "familyNames" to profile.familyNames,
            "country" to profile.country, "keyword" to profile.keyword,
            "employment" to profile.employment, "institution" to profile.institution,
            "institutionType" to profile.institutionType,
            "lastPublicationYear" to profile.lastPublicationYear,
            "emailSource" to profile.emailSource, "emailVerifiedLevel" to profile.emailVerifiedLevel,
            "dataSource" to profile.dataSource,
            "externalIds" to profile.externalIds?.let { objectMapper.readValue(it, Map::class.java) },
            "discoveredAt" to now, "updatedAt" to now,
            "filterResult" to filterResult,
            "filterRejectReason" to rejectReasons.takeIf { it.isNotEmpty() }?.joinToString("; "),
            "tags" to listOf("discovered")
        )
    }

    private fun promoteDiscoveredToCandidate(esDocId: String, rawDoc: Map<String, Any?>): Boolean {
        val candidateIndex = expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)
        val now = LocalDateTime.now().format(dateFormatter)
        val candidateDoc = rawDoc.toMutableMap().apply {
            put("candidateValidatedAt", now); put("updatedAt", now)
            val existingTags = (get("tags") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            put("tags", (existingTags + "discovered").distinct())
        }
        val putUrl = "${esProperties.baseUrl}/$candidateIndex/_doc/$esDocId"
        return try {
            restTemplate.exchange(putUrl, HttpMethod.PUT, HttpEntity(candidateDoc, esHeaders()),
                com.fasterxml.jackson.databind.JsonNode::class.java)
            true
        } catch (e: Exception) {
            log.warn("Failed to promote discovered expert {} to candidate: {}", esDocId, e.message)
            false
        }
    }

    fun getEnrichmentStats(): EnrichmentStats {
        val cutoff = LocalDateTime.now().minusDays(30).format(dateFormatter)
        val total = expertSearchService.countExperts(ExpertIndexLevel.CANDIDATE)
        val pending = expertSearchService.countExperts(ExpertIndexLevel.CANDIDATE, buildEnrichmentFilters(cutoff))
        val enrichedRecently = expertSearchService.countExperts(
            ExpertIndexLevel.CANDIDATE,
            listOf(mapOf("range" to mapOf("enrichedAt" to mapOf("gte" to cutoff))))
        )
        val institutionTypePending = expertSearchService.countExperts(
            ExpertIndexLevel.CANDIDATE, buildInstitutionTypeBackfillFilters()
        )
        val lastPublicationYearPending = expertSearchService.countExperts(
            ExpertIndexLevel.CANDIDATE, buildLastPublicationYearBackfillFilters()
        )
        return EnrichmentStats(pending, enrichedRecently, total, institutionTypePending, lastPublicationYearPending)
    }

    private fun buildEnrichmentFilters(cutoff: String): List<Map<String, Any>> {
        return listOf(
            mapOf(
                "bool" to mapOf(
                    "should" to listOf(
                        mapOf("bool" to mapOf("must_not" to listOf(mapOf("exists" to mapOf("field" to "enrichedAt"))))),
                        mapOf("range" to mapOf("enrichedAt" to mapOf("lt" to cutoff))),
                        mapOf(
                            "bool" to mapOf(
                                "must" to listOf(
                                    mapOf("exists" to mapOf("field" to "enrichedAt")),
                                    mapOf("exists" to mapOf("field" to "researchFields"))
                                ),
                                "must_not" to listOf(
                                    mapOf("exists" to mapOf("field" to "disciplineCategory"))
                                )
                            )
                        )
                    ),
                    "minimum_should_match" to 1,
                    "must_not" to listOf(
                        mapOf("prefix" to mapOf("orcidId" to "EMAIL-"))
                    )
                )
            )
        )
    }

    /** I5a2-1/I5a2-3：补采只针对 OpenAlex 认得（有 enrichedAt）且尚无 institutionType 的人。 */
    private fun buildInstitutionTypeBackfillFilters(): List<Map<String, Any>> = listOf(
        mapOf("bool" to mapOf(
            "must" to listOf(mapOf("exists" to mapOf("field" to "enrichedAt"))),
            "must_not" to listOf(
                mapOf("exists" to mapOf("field" to "institutionType")),
                mapOf("prefix" to mapOf("orcidId" to "EMAIL-"))
            )
        ))
    )

    /** I1-5：只针对 OpenAlex 认得（有 enrichedAt）且尚无发表年份的人。 */
    private fun buildLastPublicationYearBackfillFilters(): List<Map<String, Any>> = listOf(
        mapOf("bool" to mapOf(
            "must" to listOf(mapOf("exists" to mapOf("field" to "enrichedAt"))),
            "must_not" to listOf(
                mapOf("exists" to mapOf("field" to "lastPublicationYear")),
                mapOf("prefix" to mapOf("orcidId" to "EMAIL-"))
            )
        ))
    )

    private fun sleepInterruptible(taskType: String, ms: Long): Boolean {
        if (ms <= 0) return progressStore.isCancelled(taskType)
        var remaining = ms
        while (remaining > 0) {
            if (progressStore.isCancelled(taskType)) return true
            val slice = minOf(remaining, 1000L)
            Thread.sleep(slice)
            remaining -= slice
        }
        return progressStore.isCancelled(taskType)
    }

    private fun computeEnrichmentBackoffMs(consecutiveRateLimits: Int, retryAfterMs: Long?): Long {
        val exponential = 2000L * (1L shl (consecutiveRateLimits - 1).coerceAtMost(20))
        return (retryAfterMs ?: exponential).coerceAtMost(openAlexProperties.enrichmentMaxBackoffMs)
    }

    fun enrichExistingExperts(scope: EnrichmentScope = EnrichmentScope.DEFAULT): EnrichmentResult {
        val taskType = "EXPERT_ENRICHMENT"
        val execId = progressStore.getCurrentExecutionId(taskType)
        val cutoff = LocalDateTime.now().minusDays(30).format(dateFormatter)
        val filters = when (scope) {
            EnrichmentScope.DEFAULT -> buildEnrichmentFilters(cutoff)
            EnrichmentScope.INSTITUTION_TYPE_BACKFILL -> buildInstitutionTypeBackfillFilters()
            EnrichmentScope.LAST_PUBLICATION_YEAR_BACKFILL -> buildLastPublicationYearBackfillFilters()
        }
        val pendingCount = expertSearchService.countExperts(ExpertIndexLevel.CANDIDATE, filters)
        val rateLimitMode = openAlexProperties.enrichmentRateLimitMode.uppercase(Locale.ROOT)
        progressStore.update(taskType, TaskProgress(
            taskType = taskType, status = "RUNNING",
            batchNumber = 0, processedCount = 0, totalCount = pendingCount, message = "初始化中..."
        ), execId)

        val openAlex = openAlexProvider.getIfAvailable()
        if (openAlex == null) {
            progressStore.update(taskType, TaskProgress(
                taskType = taskType, status = "COMPLETED",
                batchNumber = -1, processedCount = 0, totalCount = pendingCount,
                message = "OpenAlex 未启用，跳过补充"
            ), execId)
            return EnrichmentResult(0, 0)
        }

        var enriched = 0
        var failed = 0
        var scanned = 0
        var rateLimitWaits = 0
        val failureReasons = mutableMapOf<String, Int>()
        var consecutiveRateLimits = 0
        var circuitBreakerTripped = false

        try {
            var batchNumber = 0
            expertSearchService.searchAfterExpertsFiltered(ExpertIndexLevel.CANDIDATE, filters) { batch ->
                if (circuitBreakerTripped || progressStore.isCancelled(taskType)) {
                    log.info("Enrichment task cancelled or circuit breaker tripped at batch {}", batchNumber)
                    return@searchAfterExpertsFiltered false
                }
                batchNumber++
                val enrichedBefore = enriched
                val failedBefore = failed
                val failureReasonsBefore = HashMap(failureReasons)
                scanned += batch.size

                for (chunk in batch.chunked(openAlexProperties.enrichmentBatchSize)) {
                    if (circuitBreakerTripped || progressStore.isCancelled(taskType)) break

                    val profilesByOrcid = chunk.associateBy { it.orcidId }
                    var retryOrcids = chunk.map { it.orcidId }

                    while (retryOrcids.isNotEmpty()) {
                        if (circuitBreakerTripped || progressStore.isCancelled(taskType)) break

                        if (openAlexProperties.enrichmentDelayMs > 0) {
                            if (sleepInterruptible(taskType, openAlexProperties.enrichmentDelayMs)) break
                        }

                        val outcomes = openAlex.batchEnrichByOrcids(retryOrcids)
                        val rateLimitedOrcids = outcomes.filterValues { it is EnrichmentOutcome.RateLimited }.keys

                        for (orcidId in retryOrcids) {
                            if (orcidId in rateLimitedOrcids) continue
                            val profile = profilesByOrcid[orcidId] ?: continue
                            when (val outcome = outcomes[orcidId] ?: EnrichmentOutcome.NotFound) {
                                is EnrichmentOutcome.Success -> {
                                    if (updateExpertAcademicFields(profile, outcome.data)) {
                                        enriched++
                                    } else {
                                        failed++
                                        failureReasons.merge("ES_UPDATE_FAILED", 1) { a, b -> a + b }
                                    }
                                }
                                is EnrichmentOutcome.NotFound -> {
                                    failed++
                                    failureReasons.merge("ORCID_NOT_IN_OPENALEX", 1) { a, b -> a + b }
                                }
                                is EnrichmentOutcome.ApiError -> {
                                    failed++
                                    failureReasons.merge("OPENALEX_API_ERROR", 1) { a, b -> a + b }
                                }
                                is EnrichmentOutcome.RateLimited -> Unit
                            }
                        }

                        if (rateLimitedOrcids.isEmpty()) {
                            consecutiveRateLimits = 0
                            break
                        }

                        consecutiveRateLimits++
                        rateLimitWaits++
                        if (rateLimitMode == "ABORT" && consecutiveRateLimits >= 5) {
                            failureReasons["CIRCUIT_BREAKER"] = 1
                            circuitBreakerTripped = true
                            log.warn("Enrichment: 连续 {} 次限流，熔断退出 (ABORT 模式)", consecutiveRateLimits)
                            break
                        }

                        val firstRateLimited = outcomes[rateLimitedOrcids.first()] as EnrichmentOutcome.RateLimited
                        val backoffMs = computeEnrichmentBackoffMs(consecutiveRateLimits, firstRateLimited.retryAfterMs)
                        val processed = enriched + failed
                        log.info("Enrichment: 限流退避 {}ms (第 {} 次)", backoffMs, consecutiveRateLimits)
                        progressStore.update(taskType, TaskProgress(
                            taskType = taskType, status = "RUNNING",
                            batchNumber = batchNumber,
                            processedCount = processed.toLong(),
                            totalCount = pendingCount,
                            message = "限流退避中 ${backoffMs / 1000}s（第 $rateLimitWaits 次），已处理 $processed/$pendingCount，成功 $enriched，失败 $failed",
                            details = mapOf(
                                "enriched" to enriched,
                                "failed" to failed,
                                "scanned" to scanned,
                                "failureReasons" to HashMap(failureReasons),
                                "rateLimitWaits" to rateLimitWaits,
                                "currentBackoffMs" to backoffMs,
                                "mode" to rateLimitMode
                            )
                        ), execId)

                        if (sleepInterruptible(taskType, backoffMs)) break
                        retryOrcids = rateLimitedOrcids.toList()
                    }
                }

                val processed = enriched + failed
                val batchPassed = enriched - enrichedBefore
                val batchRejected = failed - failedBefore
                progressStore.update(taskType, TaskProgress(
                    taskType = taskType, status = "RUNNING",
                    batchNumber = batchNumber,
                    processedCount = processed.toLong(),
                    totalCount = pendingCount,
                    message = "批次 $batchNumber: 已处理 $processed/$pendingCount, 成功 $enriched, 失败 $failed",
                    details = mapOf(
                        "enriched" to enriched,
                        "failed" to failed,
                        "scanned" to scanned,
                        "failureReasons" to HashMap(failureReasons),
                        "rateLimitWaits" to rateLimitWaits,
                        "mode" to rateLimitMode
                    ),
                    batchProcessed = batch.size,
                    batchPassed = batchPassed.coerceAtLeast(0),
                    batchRejected = batchRejected.coerceAtLeast(0),
                    batchRejectReasons = computeBatchRejectReasons(failureReasonsBefore, failureReasons)
                ), execId)
                !progressStore.isCancelled(taskType) && !circuitBreakerTripped
            }

            if (progressStore.isCancelled(taskType)) {
                val processed = enriched + failed
                log.info("Enrichment cancelled: enriched={}, failed={}, scanned={}", enriched, failed, scanned)
                progressStore.update(taskType, TaskProgress(
                    taskType = taskType, status = "CANCELLED",
                    batchNumber = -1, processedCount = processed.toLong(), totalCount = pendingCount,
                    message = "已暂停: 成功 $enriched, 失败 $failed",
                    details = mapOf(
                        "enriched" to enriched,
                        "failed" to failed,
                        "scanned" to scanned,
                        "failureReasons" to HashMap(failureReasons),
                        "rateLimitWaits" to rateLimitWaits,
                        "mode" to rateLimitMode
                    )
                ), execId)
                return EnrichmentResult(enriched, failed, HashMap(failureReasons), wasCancelled = true)
            }

            if (circuitBreakerTripped) {
                val processed = enriched + failed
                log.warn(
                    "Enrichment circuit breaker tripped: enriched={}, failed={}, scanned={}, failureReasons={}",
                    enriched, failed, scanned, failureReasons
                )
                progressStore.update(taskType, TaskProgress(
                    taskType = taskType, status = "FAILED",
                    batchNumber = -1, processedCount = processed.toLong(), totalCount = pendingCount,
                    message = "连续限流熔断退出 (ABORT 模式): 成功 $enriched, 失败 $failed",
                    details = mapOf(
                        "enriched" to enriched,
                        "failed" to failed,
                        "scanned" to scanned,
                        "failureReasons" to HashMap(failureReasons),
                        "rateLimitWaits" to rateLimitWaits,
                        "mode" to rateLimitMode
                    )
                ), execId)
                return EnrichmentResult(
                    enriched, failed, HashMap(failureReasons), circuitBreakerTripped = true
                )
            }

            val processed = enriched + failed
            progressStore.update(taskType, TaskProgress(
                taskType = taskType, status = "COMPLETED",
                batchNumber = -1, processedCount = processed.toLong(), totalCount = pendingCount,
                message = "完成: 成功 $enriched, 失败 $failed",
                details = mapOf(
                    "enriched" to enriched,
                    "failed" to failed,
                    "scanned" to scanned,
                    "failureReasons" to HashMap(failureReasons),
                    "rateLimitWaits" to rateLimitWaits,
                    "mode" to rateLimitMode
                )
            ), execId)
        } catch (e: Exception) {
            progressStore.update(taskType, TaskProgress(
                taskType = taskType, status = "FAILED",
                batchNumber = -1, processedCount = (enriched + failed).toLong(), totalCount = pendingCount,
                message = "失败: ${e.message}",
                details = mapOf(
                    "enriched" to enriched,
                    "failed" to failed,
                    "failureReasons" to HashMap(failureReasons),
                    "rateLimitWaits" to rateLimitWaits,
                    "mode" to rateLimitMode
                )
            ), execId)
            throw e
        }
        log.info("Enrichment complete: enriched={}, failed={}, scanned={}, failureReasons={}", enriched, failed, scanned, failureReasons)
        return EnrichmentResult(enriched, failed, HashMap(failureReasons))
    }

    private fun documentExistsInIndex(level: ExpertIndexLevel, orcidId: String): Boolean {
        val index = expertIndexService.indexName(level)
        val url = "${esProperties.baseUrl}/$index/_doc/$orcidId"
        return try {
            restTemplate.exchange(url, HttpMethod.HEAD, HttpEntity(null, esHeaders()), Void::class.java)
            true
        } catch (e: HttpClientErrorException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun updateExpertAcademicFields(profile: ExpertProfile, enrichment: AuthorEnrichment): Boolean {
        val orcidId = profile.orcidId
        val now = LocalDateTime.now().format(dateFormatter)
        var candidateUpdated = false
        val doc = mutableMapOf<String, Any?>(
            "hIndex" to enrichment.hIndex,
            "citationCount" to enrichment.citationCount,
            "updatedAt" to now,
            "enrichedAt" to now,
            "enrichmentSource" to "OPENALEX"
        )
        enrichment.worksCount?.let { doc["worksCount"] = it }
        enrichment.topics?.takeIf { it.isNotEmpty() }?.let { doc["researchFields"] = it.joinToString(", ") }
        enrichment.recentWorkTitles?.takeIf { it.isNotEmpty() }?.let { doc["recentWorkTitles"] = it }
        enrichment.patentTitles?.takeIf { it.isNotEmpty() }?.let { doc["patentTitles"] = it }
        enrichment.disciplineCategory?.let { doc["disciplineCategory"] = it }
        // I5a-3: null 时不写入该键，避免覆盖存量值；I5a-8: 无条件 ?.let，enrichment 值覆盖发现时的值。
        enrichment.institutionType?.let { doc["institutionType"] = it }
        // I1-3: null 时不写入该键，避免覆盖发现时的真实值；I1-4: 非 null 时无条件覆盖。
        enrichment.lastPublicationYear?.let { doc["lastPublicationYear"] = it }
        val enrichedProfile = profile.copy(
            hIndex = enrichment.hIndex ?: profile.hIndex,
            citationCount = enrichment.citationCount ?: profile.citationCount,
            worksCount = enrichment.worksCount ?: profile.worksCount,
            researchFields = enrichment.topics?.takeIf { it.isNotEmpty() }?.joinToString(", ")
                ?: profile.researchFields,
            recentWorkTitles = enrichment.recentWorkTitles?.takeIf { it.isNotEmpty() }
                ?: profile.recentWorkTitles,
            patentTitles = enrichment.patentTitles?.takeIf { it.isNotEmpty() } ?: profile.patentTitles,
            disciplineCategory = enrichment.disciplineCategory ?: profile.disciplineCategory,
            lastPublicationYear = enrichment.lastPublicationYear ?: profile.lastPublicationYear
        )
        doc["expertClassification"] = expertClassificationService.classify(enrichedProfile)
        val updateBody = mapOf("doc" to doc)
        for (level in listOf(ExpertIndexLevel.RAW, ExpertIndexLevel.CANDIDATE, ExpertIndexLevel.APPLICATION)) {
            if (!documentExistsInIndex(level, orcidId)) continue
            try {
                val index = expertIndexService.indexName(level)
                val updateUrl = "${esProperties.baseUrl}/$index/_update/$orcidId"
                restTemplate.exchange(updateUrl, HttpMethod.POST, HttpEntity(updateBody, esHeaders()),
                    com.fasterxml.jackson.databind.JsonNode::class.java)
                if (level == ExpertIndexLevel.CANDIDATE) candidateUpdated = true
            } catch (e: Exception) {
                log.warn("Failed to update academic fields for {} in index {}: {}", orcidId, level, e.message)
            }
        }
        return candidateUpdated
    }

    private fun existsInRawIndexByOrcid(orcid: String): DedupResult {
        val url = "${esProperties.baseUrl}/${expertIndexService.indexName(ExpertIndexLevel.RAW)}/_doc/$orcid"
        return try {
            restTemplate.exchange(url, HttpMethod.HEAD, HttpEntity(null, esHeaders()), Void::class.java)
            DedupResult.EXISTS
        } catch (e: HttpClientErrorException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) DedupResult.NOT_FOUND else DedupResult.ERROR
        } catch (e: Exception) { DedupResult.ERROR }
    }

    private fun existsInRawIndexByEmail(email: String): DedupResult {
        val url = "${esProperties.baseUrl}/${expertIndexService.indexName(ExpertIndexLevel.RAW)}/_search"
        val query = mapOf("query" to mapOf("term" to mapOf("email" to email.lowercase(Locale.ROOT))), "size" to 0)
        return try {
            val response = restTemplate.exchange(url, HttpMethod.POST, HttpEntity(query, esHeaders()),
                com.fasterxml.jackson.databind.JsonNode::class.java).body
            val total = response?.path("hits")?.path("total")?.path("value")?.asInt(0) ?: 0
            if (total > 0) DedupResult.EXISTS else DedupResult.NOT_FOUND
        } catch (e: HttpClientErrorException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) DedupResult.NOT_FOUND else DedupResult.ERROR
        } catch (e: Exception) { DedupResult.ERROR }
    }

    private fun inferCountryFromAffiliation(affiliation: String?): String? {
        if (affiliation.isNullOrBlank()) return null
        val parts = affiliation.split(",").map { it.trim() }
        return parts.lastOrNull()?.takeIf { it.length in 2..30 }
    }

    private fun buildExternalIds(paper: PaperMetadata, authorEmail: AuthorEmail): String? {
        val ids = mutableMapOf<String, String>()
        paper.pmcId?.let { ids["pmcId"] = it }
        paper.doi?.let { ids["doi"] = it }
        paper.pmid?.let { ids["pmid"] = it }
        authorEmail.orcidId?.let { ids["orcid"] = it }
        return if (ids.isEmpty()) null else objectMapper.writeValueAsString(ids)
    }

    private fun esHeaders(): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        val raw = "${esProperties.username}:${esProperties.password}"
        set(HttpHeaders.AUTHORIZATION, "Basic ${Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))}")
    }

    private fun tryGetEmailFromOrcid(orcidId: String): List<String> {
        val orcid = orcidProvider.getIfAvailable() ?: return emptyList()
        try {
            val criteria = PaperSearchCriteria(
                keywords = listOf("orcid:$orcidId"),
                pageSize = 5
            )
            val records = orcid.searchOrcidRecords(criteria)
            val normalizedTarget = orcidId.removePrefix("https://orcid.org/").trim()
            val matched = records.firstOrNull {
                it.orcidId?.removePrefix("https://orcid.org/")?.trim().equals(normalizedTarget, ignoreCase = true)
            }
            return matched?.emails.orEmpty()
        } catch (e: Exception) {
            log.warn("Failed to get email from ORCID for ID {}: {}", orcidId, e.message)
            return emptyList()
        }
    }

    private fun updateRawDocumentEmail(orcidId: String, email: String): Boolean {
        val rawIndex = expertIndexService.indexName(ExpertIndexLevel.RAW)
        val now = LocalDateTime.now().format(dateFormatter)
        val updateBody = mapOf(
            "doc" to mapOf(
                "email" to email,
                "updatedAt" to now
            )
        )
        val updateUrl = "${esProperties.baseUrl}/$rawIndex/_update/$orcidId"
        return try {
            restTemplate.exchange(updateUrl, HttpMethod.POST, HttpEntity(updateBody, esHeaders()),
                com.fasterxml.jackson.databind.JsonNode::class.java)
            true
        } catch (e: Exception) {
            log.warn("Failed to update email for {} in RAW index: {}", orcidId, e.message)
            false
        }
    }

    private fun promoteRawToCandidateWithEmail(profile: ExpertProfile): Boolean {
        val candidateIndex = expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)
        try {
            restTemplate.exchange(
                "${esProperties.baseUrl}/$candidateIndex/_doc/${profile.orcidId}",
                HttpMethod.HEAD,
                HttpEntity(null, esHeaders()),
                Void::class.java
            )
            log.debug("CANDIDATE already exists for {}, skip promotion", profile.orcidId)
            return false
        } catch (e: HttpClientErrorException) {
            if (e.statusCode != HttpStatus.NOT_FOUND) {
                log.warn("Failed to check CANDIDATE existence for {}: {}", profile.orcidId, e.message)
                return false
            }
        } catch (e: Exception) {
            log.warn("Failed to check CANDIDATE existence for {}: {}", profile.orcidId, e.message)
            return false
        }

        val rawIndex = expertIndexService.indexName(ExpertIndexLevel.RAW)
        val getUrl = "${esProperties.baseUrl}/$rawIndex/_doc/${profile.orcidId}"
        val rawDoc = try {
            val response = restTemplate.exchange(getUrl, HttpMethod.GET, HttpEntity(null, esHeaders()),
                com.fasterxml.jackson.databind.JsonNode::class.java).body
            val source = response?.path("_source")
            if (source != null && !source.isMissingNode) {
                objectMapper.convertValue(source, Map::class.java) as? Map<String, Any?>
            } else null
        } catch (e: Exception) {
            log.warn("Failed to read raw document for promotion: {}", e.message)
            null
        }         ?: return false

        val now = LocalDateTime.now().format(dateFormatter)
        val candidateDoc = rawDoc.toMutableMap().apply {
            put("candidateValidatedAt", now)
            put("updatedAt", now)
            val existingTags = (get("tags") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            put("tags", (existingTags + "auto_promoted").distinct())
        }
        val putUrl = "${esProperties.baseUrl}/$candidateIndex/_doc/${profile.orcidId}"
        return try {
            restTemplate.exchange(putUrl, HttpMethod.PUT, HttpEntity(candidateDoc, esHeaders()),
                com.fasterxml.jackson.databind.JsonNode::class.java)
            true
        } catch (e: Exception) {
            log.warn("Failed to promote raw expert {} with email to CANDIDATE: {}", profile.orcidId, e.message)
            false
        }
    }

    private fun backfillRawEmailsAndPromote(limit: Int = 100) {
        var attemptedCount = 0
        var promotedCount = 0
        expertSearchService.scrollExperts(ExpertIndexLevel.RAW) { batch, batchNumber, totalHits ->
            if (progressStore.isCancelled("EXPERT_DISCOVERY")) return@scrollExperts false
            if (attemptedCount >= limit) return@scrollExperts false

            for (profile in batch) {
                if (attemptedCount >= limit) break
                if (progressStore.isCancelled("EXPERT_DISCOVERY")) break

                if (profile.email.isNullOrBlank()) {
                    val tempProfile = profile.copy(email = "temp@weibo.com")
                    if (eligibilityService.evaluateEligibility(tempProfile).eligible) {
                        val orcidId = profile.orcidId
                        if (!orcidId.startsWith("EMAIL-") && orcidId.isNotBlank()) {
                            attemptedCount++
                            val emails = tryGetEmailFromOrcid(orcidId)
                            if (progressStore.isCancelled("EXPERT_DISCOVERY")) break
                            if (emails.isNotEmpty()) {
                                val validEmail = emails.firstOrNull { emailValidationService.validate(it).valid }
                                if (validEmail != null && !progressStore.isCancelled("EXPERT_DISCOVERY")) {
                                    if (updateRawDocumentEmail(orcidId, validEmail)) {
                                        if (progressStore.isCancelled("EXPERT_DISCOVERY")) break
                                        if (promoteRawToCandidateWithEmail(profile.copy(email = validEmail))) {
                                            promotedCount++
                                            log.info("Successfully backfilled email {} for ORCID {} and promoted to CANDIDATE", validEmail, orcidId)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            log.info("RAW email backfill batch {}: attempted={}, promoted={}, totalHits={}", batchNumber, attemptedCount, promotedCount, totalHits)
            attemptedCount < limit
        }
    }
}

/**
 * I-1: 单来源一次运行的结果。[resumeCursor] 是可以安全续跑的游标（首请求失败/部分页/取消/预算停止
 * 时保持进入该页的值）；[exhausted] 只在把来源翻到底时为 true；[stopReason] 见 [DiscoveryStopReason]。
 *
 * c4 的 CORE/ORCID 分页继续使用本类型，异常分支只允许产出 failed/deferred 结果，绝不产出
 * `exhausted = true`。
 */
data class SourceRunOutcome(
    val resumeCursor: String?,
    val exhausted: Boolean,
    val stopReason: String
)

/** I-1: 单来源运行的停止原因，与主方案「每种约束各自给原因」对齐。 */
object DiscoveryStopReason {
    const val EXHAUSTED = "EXHAUSTED"
    const val SEARCH_FAILED = "SEARCH_FAILED"
    const val BUDGET_DEFERRED = "BUDGET_DEFERRED"
    const val CIRCUIT_BREAKER = "CIRCUIT_BREAKER"
    const val CANCELLED = "CANCELLED"
    const val GLOBAL_PAPER_LIMIT = "GLOBAL_PAPER_LIMIT"
    const val GLOBAL_AUTHOR_LIMIT = "GLOBAL_AUTHOR_LIMIT"
    const val SOURCE_LIMIT = "SOURCE_LIMIT"
    const val PAGE_PARTIAL = "PAGE_PARTIAL"
    const val PAGE_CONSUMED = "PAGE_CONSUMED"
    const val EMPTY_PAGE = "EMPTY_PAGE"
    const val RAW_WRITE_INCOMPLETE = "RAW_WRITE_INCOMPLETE"
}

enum class EnrichmentScope { DEFAULT, INSTITUTION_TYPE_BACKFILL, LAST_PUBLICATION_YEAR_BACKFILL }

data class EnrichmentStats(
    val pending: Long,
    val enrichedLast30d: Long,
    val total: Long,
    val institutionTypePending: Long,
    val lastPublicationYearPending: Long
)

data class EnrichmentResult(
    val enriched: Int,
    val failed: Int,
    val failureReasons: Map<String, Int> = emptyMap(),
    val wasCancelled: Boolean = false,
    val circuitBreakerTripped: Boolean = false
) : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int get() = enriched
    override val taskFailureCount: Int get() = failed
    override val taskFinalStatus: String?
        get() = when {
            wasCancelled -> "CANCELLED"
            circuitBreakerTripped -> "FAILED"
            else -> null
        }
}
