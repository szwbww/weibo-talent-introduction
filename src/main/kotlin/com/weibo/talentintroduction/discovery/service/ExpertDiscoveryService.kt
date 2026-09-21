package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.config.EuropePmcProperties
import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.config.OpenAlexBudgetDeferredException
import com.weibo.talentintroduction.config.OpenAlexProperties
import com.weibo.talentintroduction.config.RequestKind
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.DiscoveryResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryStats
import com.weibo.talentintroduction.discovery.domain.DiscoveryTerminalStatus
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.ExpertAcademicEnrichmentJob
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult
import com.weibo.talentintroduction.discovery.domain.SourceStats
import com.weibo.talentintroduction.discovery.domain.SourceUnit
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
import com.weibo.talentintroduction.expert.service.PromotionOutcome
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
import java.time.Instant
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
    /**
     * I-1（08）：补全任务的唯一入队入口（07 的存储）；发现只在 RAW 写成功后经它幂等入队，
     * 也由本 service 的批次核心领取到期任务。c8 不直接读写任务表。
     */
    private val enrichmentJobService: ExpertAcademicEnrichmentJobService,
    @Qualifier("discoveryFetchExecutor")
    private val discoveryFetchExecutor: Executor,
    private val europePmcProperties: EuropePmcProperties
) {
    private val log = LoggerFactory.getLogger(ExpertDiscoveryService::class.java)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * I-4（08）：最近一批自动/待补批次补全的逐源计数（入队/成功/待补/未匹配）。
     * 只是 `/enrich/stats` 的进程内观测值，任务生命周期的唯一事实仍是 expert_academic_enrichment_job。
     */
    private val lastEnrichmentBatch = java.util.concurrent.atomic.AtomicReference<AutoEnrichmentBatchResult?>(null)

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

    /**
     * I-1（08）：去重结果必须携带**匹配文档的真实 `_id`** —— 重放/重复发现时要按它补建缺失的补全任务
     * （[DedupResult.Exists]），绝不用新论文派生的邮箱/姓名/主键去改写已入库专家身份。
     * [DedupResult.Exists.docId] 只在索引响应里确实带回 `_id` 时非空；读不到时只统计重复、不补建任务。
     */
    private sealed class DedupResult {
        /** RAW 已有该身份：`docId` = 匹配文档的真实 `_id`。 */
        data class Exists(val docId: String?) : DedupResult()

        object NotFound : DedupResult()
        object Error : DedupResult()
    }

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
                "stopReason" to (ss.stopReason ?: ""),
                "runBudget" to ss.runBudget,
                "unit" to ss.unit.name
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
            "合计: ${processedCountsSegment(stats)}, 收录 ${stats.indexed}, 晋升 ${stats.promoted}$sourceFailureSegment"
    }

    /**
     * c9（I-1）：`papersSearched` 对 ORCID 的历史语义是「记录数」，不能与论文混称。这里按
     * [SourceUnit] 把两者分列；没有 ORCID 记录时输出与改动前逐字相同。
     */
    private fun processedCountsSegment(stats: DiscoveryStats): String {
        val paperTotal = stats.bySource.values.filter { it.unit == SourceUnit.PAPER }.sumOf { it.papersSearched }
        val recordTotal = stats.bySource.values.filter { it.unit == SourceUnit.RECORD }.sumOf { it.papersSearched }
        return if (recordTotal > 0) "论文 $paperTotal, ORCID 记录 $recordTotal" else "论文 $paperTotal"
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
        // I-1（09）：启动校验 —— 全局 cap 必须覆盖各启用来源的基础份额，否则直接报配置错误，
        // 不静默饿死后来源，也不把 0 当成无限量。
        validateRunQuota(sources, criteria)
        // I-3（09）：运行级 deadline。请求前与每页内检查，到点按 TIME_BUDGET 停止并保留进入页的检查点。
        val deadline = Instant.now().plus(discoveryProperties.timeBudget)
        val startTime = System.currentTimeMillis()
        // I-3: 结果与进度共用的终态；仅在正常路径赋值（异常路径由 catch 记录 FAILED 后重抛）。
        var terminalStatusOfRun = DiscoveryTerminalStatus.SUCCESS

        log.info("发现任务启动: 启用平台=${sources.map { it.sourceName }}, 关键词=${criteria.keywords}, " +
            "年份=${criteria.publicationYearFrom}-${criteria.publicationYearTo}, " +
            "全局限额: 论文 ${discoveryProperties.maxPapersPerRun} / 作者 ${discoveryProperties.maxAuthorsPerRun}, " +
            "时间预算 ${discoveryProperties.timeBudget}")

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

            for ((index, source) in sources.withIndex()) {
                if (progressStore.isCancelled("EXPERT_DISCOVERY")) break
                stats.refreshGlobalCounts()
                if (stats.totalAuthors >= discoveryProperties.maxAuthorsPerRun) break
                if (stats.totalPapers >= discoveryProperties.maxPapersPerRun) break

                // I-2: 先给每个后来源留一页基础份额，再按来源顺序分配本源的运行额度；
                // 已穷尽/失效来源没用掉的份额自然留在全局剩余里给后来源复用。
                val runQuota = allocateSourceQuota(sources, index, criteria.pageSize, papersUsedForGlobalCap(stats))
                val outcome = discoverFromSource(source, criteria, stats, runQuota, deadline)
                log.info("[{}] 本次运行结束: stopReason={}, exhausted={}, resumeCursor={}",
                    source.sourceName, outcome.stopReason, outcome.exhausted,
                    outcome.resumeCursor?.take(50) ?: "null")
            }

            discoverFromOrcid(criteria, stats, deadline)
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
        // c9（I-1）：论文与 ORCID 记录分列，不把记录数混称论文。
        val counts = processedCountsSegment(stats)
        return when (terminalStatus) {
            DiscoveryTerminalStatus.CANCELLED ->
                "已取消: $counts, 收录 ${stats.indexed}, 晋升 ${stats.promoted}"
            DiscoveryTerminalStatus.FAILED ->
                "失败: 全源搜索失败$sourceFailureSegment, $counts, 收录 ${stats.indexed}"
            DiscoveryTerminalStatus.PARTIAL_SUCCESS ->
                "部分成功: $counts, 收录 ${stats.indexed}, 晋升 ${stats.promoted}" +
                    sourceFailureSegment + pendingSegment
            else ->
                "完成: $counts, 收录 ${stats.indexed}, 晋升 ${stats.promoted}"
        }
    }

    /**
     * c9（I-1）：启动校验。三类错误都必须在**动手之前**以清晰配置错误暴露，而不是静默饿死后来源
     * 或把 0 解释成无限量：
     * 1. 全局论文上限 / 作者防护上限 / 时间预算必须为正数；
     * 2. 来源上限不得为负；
     * 3. 全局论文上限必须覆盖各启用来源的基础份额（每源至少一页，`min(pageSize, cap)`）。
     */
    private fun validateRunQuota(sources: List<AcademicDataSource>, criteria: PaperSearchCriteria) {
        val globalCap = discoveryProperties.maxPapersPerRun
        val authorCap = discoveryProperties.maxAuthorsPerRun
        val timeBudget = discoveryProperties.timeBudget
        require(globalCap > 0) {
            "深度发现配置错误：全局论文上限（EXPERT_DISCOVERY_MAX_PAPERS）必须为正数，当前为 $globalCap。" +
                "0 表示配置错误而不是无限量。"
        }
        require(authorCap > 0) {
            "深度发现配置错误：作者防护上限（EXPERT_DISCOVERY_MAX_AUTHORS）必须为正数，当前为 $authorCap。" +
                "0 表示配置错误而不是无限量。"
        }
        require(!timeBudget.isZero && !timeBudget.isNegative) {
            "深度发现配置错误：单次运行的时间预算（EXPERT_DISCOVERY_TIME_BUDGET）必须为正数，当前为 $timeBudget。" +
                "0 表示配置错误而不是无限量。"
        }
        val negative = sources.filter { it.maxPapersPerSource < 0 }
        require(negative.isEmpty()) {
            "深度发现配置错误：来源上限不得为负：" +
                negative.joinToString(", ") { "${it.sourceName}=${it.maxPapersPerSource}" }
        }
        val pageSize = pageSizeOf(criteria)
        val baseShares = sources.sumOf { minOf(pageSize, it.maxPapersPerSource) }
        require(globalCap >= baseShares) {
            "深度发现配置错误：全局论文上限 $globalCap 小于各启用来源的基础份额合计 $baseShares" +
                "（每源至少一页 $pageSize：${sources.joinToString(", ") { "${it.sourceName}=${minOf(pageSize, it.maxPapersPerSource)}" }}）。" +
                "请提高 EXPERT_DISCOVERY_MAX_PAPERS 或减少本次选择的来源。"
        }
    }

    /**
     * c9（I-2）：本源本次运行的额度 = `min(本源上限, 全局剩余 - 后来源保留份额)`。
     * 后来源保留份额只保底一页（`min(pageSize, cap)`），因此前来源有剩余额度时后来源在本源上限内
     * 仍可用掉它；反过来任何来源都不可能吃掉后来源的保底份额。返回值可能为 0（本源上限为 0），
     * 调用方按 0 额度运行 —— 绝不解释成无限量。
     */
    private fun allocateSourceQuota(
        sources: List<AcademicDataSource>,
        index: Int,
        pageSize: Int,
        papersUsed: Int
    ): Int {
        val page = pageSize.coerceAtLeast(1)
        val sourceCap = sources[index].maxPapersPerSource.coerceAtLeast(0)
        val globalRemaining = (discoveryProperties.maxPapersPerRun - papersUsed).coerceAtLeast(0)
        val reserveForLater = sources.drop(index + 1)
            .sumOf { minOf(page, it.maxPapersPerSource.coerceAtLeast(0)) }
        val available = (globalRemaining - reserveForLater).coerceAtLeast(0)
        return minOf(sourceCap, available)
    }

    private fun pageSizeOf(criteria: PaperSearchCriteria): Int = criteria.pageSize.coerceAtLeast(1)

    /**
     * c9（I-1）：计入全局论文上限的只有论文源。ORCID 走独立的记录限额与作者防护，
     * 不参与论文额度分配（避免它的记录数挤占论文份额）。
     */
    private fun papersUsedForGlobalCap(stats: DiscoveryStats): Int =
        stats.bySource.values.filter { it.unit == SourceUnit.PAPER }.sumOf { it.papersSearched }

    /** c9（I-3）：运行级时间预算是否已到点。 */
    private fun timeBudgetReached(deadline: Instant): Boolean = !Instant.now().isBefore(deadline)

    /**
     * I-1: 单来源运行。每一完整消费页后立即持久化 next cursor；首请求失败、部分页、取消与预算停止
     * 都保留进入该页的 cursor；异常与额度延期绝不产出 `exhausted = true`。
     *
     * c9（I-1/I-2）：[runQuota] 是本次运行分给本源的额度（已含后来源保留份额），它可能小于本源自身上限 ——
     * 此时到界按全局 cap 命名，而不是谎称本源上限用尽。
     * c9（I-3）：[deadline] 到点即按 `TIME_BUDGET` 停在进入页，不推进未消费的半页。
     */
    private fun discoverFromSource(
        source: AcademicDataSource,
        criteria: PaperSearchCriteria,
        stats: DiscoveryStats,
        runQuota: Int,
        deadline: Instant
    ): SourceRunOutcome {
        val sourceStats = stats.getOrCreateSourceStats(source.sourceName, source.emailExtractionMethod)
        val sourceStartTime = System.currentTimeMillis()
        val execId = progressStore.getCurrentExecutionId("EXPERT_DISCOVERY")
        val sourceLimit = runQuota.coerceAtLeast(0)
        // 运行额度小于本源上限 = 本次是全局 cap 在约束本源，停止原因必须按全局 cap 命名。
        val quotaBoundByGlobalCap = sourceLimit < source.maxPapersPerSource
        sourceStats.runBudget = sourceLimit

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

        log.info(
            "[{}] 开始: 方式={}, 本源限额={}, 本次运行额度={}, 全局剩余={}",
            source.sourceName, source.emailExtractionMethod, source.maxPapersPerSource, sourceLimit,
            (discoveryProperties.maxPapersPerRun - papersUsedForGlobalCap(stats)).coerceAtLeast(0)
        )

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
                stopReason = if (quotaBoundByGlobalCap) {
                    DiscoveryStopReason.GLOBAL_PAPER_LIMIT
                } else {
                    DiscoveryStopReason.SOURCE_LIMIT
                }
                break
            }
            // c9（I-3）：HTTP 请求前的 deadline 检查 —— 到点绝不发下一次请求。
            if (timeBudgetReached(deadline)) {
                log.info("[{}] 时间预算到点，停止并发起无请求，保留进入页游标 {}", source.sourceName, resumeCursor?.take(50) ?: "null")
                stopReason = DiscoveryStopReason.TIME_BUDGET
                break
            }

            sourceStats.apiRequests++

            var batch: PaperSearchResult? = null
            var corePage: CoreDataSource.CoreSearchPage? = null
            try {
                if (source is CoreDataSource) {
                    // I-1/I-4: CORE 走 offset 分片协议；分片触达供应商窗口必须显式记录，不能当成穷尽。
                    corePage = source.searchCorePage(runCriteria.copy(cursor = cursor))
                    batch = corePage?.result
                } else {
                    batch = source.searchPapers(runCriteria.copy(cursor = cursor))
                }
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

            if (corePage?.windowLimit == true) {
                // I-4: 分片窗口边界是一次显式事件（不是失败、也不是穷尽）。游标已切到下一分片，
                // 未覆盖尾部只记录不冒充覆盖；该分片不再发出任何 offset 请求。
                sourceStats.failureReasons.merge(DiscoveryStopReason.WINDOW_LIMIT, 1) { a, b -> a + b }
                log.warn("[{}] 分片触达供应商窗口，未覆盖尾部 {} 条（已切下一分片）",
                    source.sourceName, corePage.uncoveredTail)
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
            val enqueueFailedBefore = sourceStats.failureReasons[ENRICHMENT_ENQUEUE_FAILED] ?: 0
            val rejectReasonsBefore = snapshotRejectReasons(sourceStats)

            var limitReached = false
            // c9（I-3）：页内到点单独标记 —— 半页按「进入该页」落盘，且停止原因必须是 TIME_BUDGET。
            var timeBudgetExpired = false
            // c9（I-3）：到界的哪个约束自己命名（全局论文上限 / 作者防护 / 本源额度）。
            var limitReason: String? = null
            var consumedInBatch = 0

            val extractions = parallelExtractOutcomes(batch.papers, source)
            for ((paper, extraction) in extractions) {
                if (consumedInBatch % 10 == 0 && progressStore.isCancelled("EXPERT_DISCOVERY")) { limitReached = true; break }
                if (timeBudgetReached(deadline)) { timeBudgetExpired = true; break }
                stats.refreshGlobalCounts()
                if (stats.totalPapers >= discoveryProperties.maxPapersPerRun) {
                    limitReached = true; limitReason = DiscoveryStopReason.GLOBAL_PAPER_LIMIT; break
                }
                if (stats.totalAuthors >= discoveryProperties.maxAuthorsPerRun) {
                    limitReached = true; limitReason = DiscoveryStopReason.GLOBAL_AUTHOR_LIMIT; break
                }
                if (sourcePapersProcessed >= sourceLimit) {
                    limitReached = true
                    limitReason = if (quotaBoundByGlobalCap) {
                        DiscoveryStopReason.GLOBAL_PAPER_LIMIT
                    } else {
                        DiscoveryStopReason.SOURCE_LIMIT
                    }
                    break
                }
                sourceStats.papersSearched++
                sourcePapersProcessed++
                consumedInBatch++
                consumeOutcome(paper, extraction, source, stats, sourceStats, execId)
            }

            // I-1: 完整消费页 = 页内全部论文处理完且 RAW 持久化/补全入队都未失败；任一失败都保留进入该页的 cursor 以便重放。
            val rawWriteFailedInPage = sourceStats.rawWriteFailed - rawWriteFailedBefore
            val enqueueFailedInPage = (sourceStats.failureReasons[ENRICHMENT_ENQUEUE_FAILED] ?: 0) - enqueueFailedBefore
            if (rawWriteFailedInPage > 0) {
                log.warn("[{}] 批次 {} 内有 {} 篇 RAW 写入失败，保留进入该页的 cursor 以便重放",
                    source.sourceName, batchNumber, rawWriteFailedInPage)
            }
            if (enqueueFailedInPage > 0) {
                log.warn("[{}] 批次 {} 内有 {} 条补全任务入队失败，保留进入该页的 cursor 以便重放补建",
                    source.sourceName, batchNumber, enqueueFailedInPage)
            }
            if (!limitReached && !timeBudgetExpired && rawWriteFailedInPage == 0 && enqueueFailedInPage == 0) {
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
            if (enqueueFailedInPage > 0) {
                stopReason = DiscoveryStopReason.ENQUEUE_INCOMPLETE
                break
            }
            if (limitReached || circuitBreakerTripped || timeBudgetExpired) {
                stopReason = when {
                    circuitBreakerTripped -> DiscoveryStopReason.CIRCUIT_BREAKER
                    timeBudgetExpired -> DiscoveryStopReason.TIME_BUDGET
                    limitReason != null -> limitReason!!
                    else -> DiscoveryStopReason.PAGE_PARTIAL
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
     * I-1/I-2: ORCID 分页只在完整消费一页后推进游标；部分页、取消与失败保留进入该页的 offset。
     * 推进量由 [OrcidDataSource.searchOrcidPage] 按原始返回条数算出，因此「一整页都没有公开邮箱」
     * 也会前进而不是原地打转；只有所有主题分片遍历完（`nextCursor == null`）才判穷尽，
     * EXHAUSTED 允许下一个扫描周期重开并靠去重避免重复收录。
     */
    private fun discoverFromOrcid(criteria: PaperSearchCriteria, stats: DiscoveryStats, deadline: Instant): SourceRunOutcome? {
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
        // c9（I-2）：ORCID 的计量单位是「记录」，限额独立于论文全局 cap，只受作者总数防护约束。
        sourceStats.unit = SourceUnit.RECORD
        sourceStats.runBudget = orcidLimit
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
            // c9（I-3）：请求前 deadline 检查 —— 时间预算到点绝不发下一次 ORCID 请求。
            if (timeBudgetReached(deadline)) {
                log.info("[{}] 时间预算到点，保留进入页 offset {}", orcid.sourceName, resumeCursor ?: "null")
                stopReason = DiscoveryStopReason.TIME_BUDGET
                break
            }

            sourceStats.apiRequests++

            val page = try {
                orcid.searchOrcidPage(criteria.copy(cursor = cursor))
            } catch (e: Exception) {
                recordTerminalSourceFailure(sourceStats, "SEARCH_FAILED")
                log.error("[{}] 搜索失败: {}", orcid.sourceName, e.message)
                stopReason = DiscoveryStopReason.SEARCH_FAILED
                break
            }
            val records = page.records
            if (records.isEmpty()) {
                val next = page.nextCursor
                if (next == null) {
                    // I-2/V-2: 只有「原始返回为 0 且没有下一分片」才算穷尽；
                    // 一整页都没有公开邮箱时 nextCursor 仍会前进，必须继续翻页而不是停在原地。
                    stopReason = DiscoveryStopReason.EXHAUSTED
                    exhausted = true
                    break
                }
                // I-2: 无公开邮箱的整页按原始条数推进 offset/分片后继续。
                cursor = next
                persistCheckpoint(next, DiscoveryStopReason.EMPTY_PAGE, false)
                continue
            }
            batchNumber++

            val indexedBefore = sourceStats.indexed
            val recordsProcessedBeforeBatch = recordsProcessed
            val rawWriteFailedBefore = sourceStats.rawWriteFailed
            val enqueueFailedBefore = sourceStats.failureReasons[ENRICHMENT_ENQUEUE_FAILED] ?: 0
            val rejectReasonsBefore = snapshotRejectReasons(sourceStats)

            // c9（I-3）：页内到点单独标记 —— 部分页按「进入该页」落盘，停止原因按 TIME_BUDGET 命名。
            var timeBudgetExpired = false
            for (record in records) {
                if (recordsProcessed >= orcidLimit) break
                if (timeBudgetReached(deadline)) { timeBudgetExpired = true; break }
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

                    // I-1（08）：与论文路径对称 —— 重复命中只按匹配文档真实 `_id` 补建缺失任务。
                    val duplicate = when (val emailDedup = existsInRawIndexByEmail(authorEmail.email)) {
                        is DedupResult.Exists -> emailDedup
                        DedupResult.Error -> { sourceStats.dedupErrors++; continue }
                        DedupResult.NotFound -> null
                    }
                    if (duplicate != null) {
                        sourceStats.duplicates++
                        duplicate.docId?.let { ensureEnrichmentJob(it, orcid.sourceName, execId, sourceStats) }
                        continue
                    }
                    if (authorEmail.orcidId != null) {
                        when (val orcidDedup = existsInRawIndexByOrcid(authorEmail.orcidId)) {
                            is DedupResult.Exists -> {
                                sourceStats.duplicates++
                                orcidDedup.docId?.let { ensureEnrichmentJob(it, orcid.sourceName, execId, sourceStats) }
                                continue
                            }
                            DedupResult.Error -> { sourceStats.dedupErrors++; continue }
                            DedupResult.NotFound -> {}
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
                    // I-1（08）：RAW 落库成功后才入队；入队失败不推进本页。
                    enqueueEnrichmentJob(esDocId, orcid.sourceName, execId, sourceStats)

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
            val enqueueFailedInPage = (sourceStats.failureReasons[ENRICHMENT_ENQUEUE_FAILED] ?: 0) - enqueueFailedBefore
            if (rawWriteFailedInPage > 0) {
                log.warn("[{}] 批次 {} 内有 {} 条记录 RAW 写入失败，保留进入该页的 offset 以便重放",
                    orcid.sourceName, batchNumber, rawWriteFailedInPage)
            }
            if (enqueueFailedInPage > 0) {
                log.warn("[{}] 批次 {} 内有 {} 条补全任务入队失败，保留进入该页的 offset 以便重放补建",
                    orcid.sourceName, batchNumber, enqueueFailedInPage)
            }
            if (pageFullyConsumed && !timeBudgetExpired && rawWriteFailedInPage == 0 && enqueueFailedInPage == 0) {
                // I-2: 推进量由数据源按原始返回条数算好（不受邮箱过滤影响），这里只搬运它的游标。
                cursor = page.nextCursor
                persistCheckpoint(
                    cursor,
                    if (cursor == null) DiscoveryStopReason.EXHAUSTED else DiscoveryStopReason.PAGE_CONSUMED,
                    cursor == null
                )
                if (exhausted) break
            } else {
                // I-1: 部分页、页内 RAW 持久化未完成或补全入队未完成都保留进入该页的 offset，绝不跳过未消费记录。
                persistCheckpoint(
                    resumeCursor,
                    if (rawWriteFailedInPage > 0) {
                        DiscoveryStopReason.RAW_WRITE_INCOMPLETE
                    } else if (enqueueFailedInPage > 0) {
                        DiscoveryStopReason.ENQUEUE_INCOMPLETE
                    } else if (timeBudgetExpired) {
                        DiscoveryStopReason.TIME_BUDGET
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
        sourceStats: SourceStats,
        executionId: Long?
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

            // I-1（08）：重复命中时只按匹配文档的真实 `_id` 补建缺失任务，不重写整份专家、不算新增。
            val duplicate = when (val emailDedup = existsInRawIndexByEmail(authorEmail.email)) {
                is DedupResult.Exists -> emailDedup
                DedupResult.Error -> { sourceStats.dedupErrors++; continue }
                DedupResult.NotFound -> null
            }
            if (duplicate != null) {
                sourceStats.duplicates++
                duplicate.docId?.let { ensureEnrichmentJob(it, source.sourceName, executionId, sourceStats) }
                continue
            }
            if (authorEmail.orcidId != null) {
                when (val orcidDedup = existsInRawIndexByOrcid(authorEmail.orcidId)) {
                    is DedupResult.Exists -> {
                        sourceStats.duplicates++
                        orcidDedup.docId?.let { ensureEnrichmentJob(it, source.sourceName, executionId, sourceStats) }
                        continue
                    }
                    DedupResult.Error -> { sourceStats.dedupErrors++; continue }
                    DedupResult.NotFound -> {}
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
            // I-1（08）：RAW 落库成功后才入队；入队失败不推进本页（见 enqueueEnrichmentJob）。
            enqueueEnrichmentJob(esDocId, source.sourceName, executionId, sourceStats)

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

    private fun processPaper(
        paper: PaperMetadata,
        source: AcademicDataSource,
        stats: DiscoveryStats,
        sourceStats: SourceStats,
        executionId: Long?
    ) {
        consumeOutcome(paper, extractOutcome(paper, source), source, stats, sourceStats, executionId)
    }

    // ------------------------------------------------------------------
    // I-1（08）：RAW 先落地再入队
    // ------------------------------------------------------------------

    /**
     * 新增专家（RAW 已落库）的补全入队。数据库错误**不吞掉**：计入
     * [ENRICHMENT_ENQUEUE_FAILED]，让当前页按 [DiscoveryStopReason.ENQUEUE_INCOMPLETE] 保留重放
     * （重放时该专家已是重复命中，会由 [ensureEnrichmentJob] 补建缺失任务），
     * 既不假装任务已建、也不推进检查点。
     */
    private fun enqueueEnrichmentJob(docId: String, sourceName: String, executionId: Long?, sourceStats: SourceStats) {
        try {
            enrichmentJobService.enqueue(docId, sourceName, executionId)
        } catch (e: Exception) {
            log.warn("Failed to enqueue academic enrichment job for {} (source={}): {}", docId, sourceName, e.message)
            sourceStats.failureReasons.merge(ENRICHMENT_ENQUEUE_FAILED, 1) { a, b -> a + b }
        }
    }

    /**
     * 重放/重复发现的补建入口：只幂等入队（07 的存储按 `UNIQUE(expert_doc_id)` 合并，
     * 可靠身份变更时重开 `UNMATCHED`），绝不重新写整份专家文档。
     */
    private fun ensureEnrichmentJob(docId: String, sourceName: String, executionId: Long?, sourceStats: SourceStats) {
        enqueueEnrichmentJob(docId, sourceName, executionId, sourceStats)
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
        return EnrichmentStats(
            pending, enrichedRecently, total, institutionTypePending, lastPublicationYearPending,
            autoEnrichment = lastEnrichmentBatch.get()
        )
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
        // I-3/I-4（08）：新增的人工待补重试入口；旧三种 scope 的过滤条件与语义完全不变。
        if (scope == EnrichmentScope.DISCOVERY_PENDING) return enrichDiscoveryPendingJobs()

        val taskType = EXPERT_ENRICHMENT_TASK_TYPE
        val execId = progressStore.getCurrentExecutionId(taskType)
        val cutoff = LocalDateTime.now().minusDays(30).format(dateFormatter)
        val filters = when (scope) {
            EnrichmentScope.DEFAULT -> buildEnrichmentFilters(cutoff)
            EnrichmentScope.INSTITUTION_TYPE_BACKFILL -> buildInstitutionTypeBackfillFilters()
            EnrichmentScope.LAST_PUBLICATION_YEAR_BACKFILL -> buildLastPublicationYearBackfillFilters()
            // DISCOVERY_PENDING 不走 CANDIDATE 过滤扫描（已在上方提前返回），这里只是穷尽性占位。
            EnrichmentScope.DISCOVERY_PENDING -> emptyList()
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
        // I-5：额度延期是与限流/失败都不同的停止原因，单独记录、单独终态。
        var budgetDeferred = false

        try {
            var batchNumber = 0
            expertSearchService.searchAfterExpertsFiltered(ExpertIndexLevel.CANDIDATE, filters) { batch ->
                if (circuitBreakerTripped || budgetDeferred || progressStore.isCancelled(taskType)) {
                    log.info("Enrichment task cancelled, budget deferred or circuit breaker tripped at batch {}", batchNumber)
                    return@searchAfterExpertsFiltered false
                }
                batchNumber++
                val enrichedBefore = enriched
                val failedBefore = failed
                val failureReasonsBefore = HashMap(failureReasons)
                scanned += batch.size

                for (chunk in batch.chunked(enrichmentBatchSize())) {
                    if (circuitBreakerTripped || budgetDeferred || progressStore.isCancelled(taskType)) break

                    // 只重试真正可重试（限流）的身份：已成功写过的层不会被再次触碰。
                    var retryChunk = chunk

                    while (retryChunk.isNotEmpty()) {
                        if (circuitBreakerTripped || budgetDeferred || progressStore.isCancelled(taskType)) break

                        if (openAlexProperties.enrichmentDelayMs > 0) {
                            if (sleepInterruptible(taskType, openAlexProperties.enrichmentDelayMs)) break
                        }

                        val outcomes = enrichProfiles(retryChunk, RequestKind.HISTORY_ENRICHMENT)
                        val retryable = mutableListOf<ExpertProfile>()
                        var firstRetryAfterMs: Long? = null

                        for (profile in retryChunk) {
                            val outcome = outcomes[enrichmentDocId(profile)] ?: ProfileEnrichmentOutcome.NotFound
                            val layers = when (outcome) {
                                is ProfileEnrichmentOutcome.Success -> outcome.layers
                                is ProfileEnrichmentOutcome.Partial -> outcome.layers
                                else -> null
                            }
                            if (layers != null) {
                                // I-2：只要有现存层写入失败就不能算成功；RAW-only 成功必须算成功。
                                when {
                                    layers.hasFailedLayer() -> {
                                        failed++
                                        failureReasons.merge("ES_UPDATE_FAILED", 1) { a, b -> a + b }
                                    }
                                    layers.updatedAnyLayer() -> enriched++
                                    else -> {
                                        failed++
                                        failureReasons.merge("NO_TARGET_LAYER", 1) { a, b -> a + b }
                                    }
                                }
                                continue
                            }
                            when (outcome) {
                                is ProfileEnrichmentOutcome.NotFound -> {
                                    failed++
                                    failureReasons.merge("ORCID_NOT_IN_OPENALEX", 1) { a, b -> a + b }
                                }
                                is ProfileEnrichmentOutcome.NoId -> {
                                    failed++
                                    failureReasons.merge("NO_TRUSTED_IDENTITY", 1) { a, b -> a + b }
                                }
                                is ProfileEnrichmentOutcome.RetryableError -> {
                                    if (outcome.rateLimited) {
                                        retryable += profile
                                        if (firstRetryAfterMs == null) firstRetryAfterMs = outcome.retryAfterMs
                                    } else {
                                        failed++
                                        failureReasons.merge("OPENALEX_API_ERROR", 1) { a, b -> a + b }
                                    }
                                }
                                is ProfileEnrichmentOutcome.Deferred -> budgetDeferred = true
                                // Success/Partial 已在上面的 layers 分支处理。
                                else -> Unit
                            }
                        }

                        // 额度延期：剩余身份一律标 Deferred，不当作失败、也不再打接口。
                        if (budgetDeferred) break
                        if (retryable.isEmpty()) {
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

                        val backoffMs = computeEnrichmentBackoffMs(consecutiveRateLimits, firstRetryAfterMs)
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
                        retryChunk = retryable
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
                !progressStore.isCancelled(taskType) && !circuitBreakerTripped && !budgetDeferred
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

            if (budgetDeferred) {
                val processed = enriched + failed
                failureReasons["BUDGET_DEFERRED"] = 1
                log.info(
                    "Enrichment stopped by OpenAlex daily budget: enriched={}, failed={}, scanned={}",
                    enriched, failed, scanned
                )
                progressStore.update(taskType, TaskProgress(
                    taskType = taskType, status = "PARTIAL_SUCCESS",
                    batchNumber = -1, processedCount = processed.toLong(), totalCount = pendingCount,
                    message = "OpenAlex 日额度延期，本轮暂停（剩余专家可续跑）: 成功 $enriched, 失败 $failed",
                    details = mapOf(
                        "enriched" to enriched,
                        "failed" to failed,
                        "scanned" to scanned,
                        "failureReasons" to HashMap(failureReasons),
                        "rateLimitWaits" to rateLimitWaits,
                        "mode" to rateLimitMode,
                        "budgetDeferred" to true
                    )
                ), execId)
                return EnrichmentResult(enriched, failed, HashMap(failureReasons), budgetDeferred = true)
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

    // ------------------------------------------------------------------
    // I-2/I-3/I-4（08）：补全队列的批次核心（worker 与人工 DISCOVERY_PENDING 共用同一实现）
    // ------------------------------------------------------------------

    /**
     * I-3/I-4（08）：人工 DISCOVERY_PENDING —— 显式重试自动补全队列（PENDING、到点的 RETRY_WAIT、
     * 租约已过期的 RUNNING）。复用与 worker 相同的「领取 + 批次核心」（06 核心补全 + 逐人终态 +
     * RAW-only 定向复评），请求口径按人工历史回填取 [RequestKind.HISTORY_ENRICHMENT]；
     * 不触碰旧三种 scope 的过滤条件，也不会伪造成功/失败计数。
     */
    private fun enrichDiscoveryPendingJobs(): EnrichmentResult {
        val taskType = EXPERT_ENRICHMENT_TASK_TYPE
        val jobs = claimDueEnrichmentJobs(discoveryProperties.autoEnrichmentBatchSize)
        if (jobs.isEmpty()) {
            val reason = if (openAlexProvider.getIfAvailable() == null) "OpenAlex 未启用" else "没有到期任务"
            progressStore.update(taskType, TaskProgress(
                taskType = taskType, status = "COMPLETED", batchNumber = -1,
                processedCount = 0, totalCount = 0, message = "待补重试：$reason，本轮未领取任何任务"
            ), progressStore.getCurrentExecutionId(taskType))
            return EnrichmentResult(0, 0)
        }
        val batch = processClaimedEnrichmentJobBatch(jobs, RequestKind.HISTORY_ENRICHMENT, taskType)
        val failureReasons = LinkedHashMap<String, Int>()
        if (batch.unmatched > 0) failureReasons["ENRICHMENT_UNMATCHED"] = batch.unmatched
        if (batch.pending > 0) failureReasons["ENRICHMENT_RETRY_WAIT"] = batch.pending
        if (batch.failed > 0) failureReasons["ENRICHMENT_FAILED"] = batch.failed
        return EnrichmentResult(
            enriched = batch.succeeded,
            failed = batch.failed + batch.unmatched,
            failureReasons = failureReasons,
            wasCancelled = batch.cancelled,
            budgetDeferred = batch.budgetDeferred
        )
    }

    /**
     * I-2（08）：领取至多 [limit] 条**到期**任务（硬上界 [MAX_ENRICHMENT_IDENTITIES_PER_BATCH]：
     * 100 只是一批，不是每日总量），不处理、不写任务终态。不足上限也照常返回，尾批不等待。
     *
     * OpenAlex 未启用时一条都不领（保持任务原状态、不消耗重试预算），也绝不写进度日志 ——
     * worker 每 30 秒检查一次，空闲/未启用的一次检查不应在任务记录与进度日志里留下噪音。
     */
    fun claimDueEnrichmentJobs(limit: Int): List<ExpertAcademicEnrichmentJob> {
        if (openAlexProvider.getIfAvailable() == null) {
            log.info("OpenAlex 未启用，本轮不领取补全任务")
            return emptyList()
        }
        // I-2：到期判定与租约写入用同一套朴素本地时钟（与 07 的 next_attempt_at 口径一致）。
        return enrichmentJobService.claimDue(
            limit.coerceIn(1, MAX_ENRICHMENT_IDENTITIES_PER_BATCH),
            LocalDateTime.now()
        )
    }

    /**
     * I-2/I-3/I-4（08）：处理一批已领取的补全任务 —— 复用 06 的 [enrichProfiles] 按真实 `_id` 补全、
     * 逐人经 07 的 CAS 写终态，并对**成功且 RAW-only** 的专家做定向复评；批次逐源计数写进既有进度 details。
     *
     * - 跨进程互斥靠 07 的租约（完成必须匹配 token，旧 token 不写任何列）；进程内互斥由调用方
     *   （worker / 人工入口）用 [TaskProgressStore.tryStartWithToken] 的同一把锁保证。
     * - RAW 文档整体读不到（索引/Mapping/ES 异常）时不写任何任务终态：任务保持 `RUNNING`，
     *   租约到期后可重领，基础设施故障不烧掉任务的重试预算。
     * - 逐个任务完成前检查取消，取消后剩余任务保持租约未完成（保存未完成状态）。
     * - [taskType] 只决定进度日志归属；`task_execution` 记录由调用方按自己的 triggerType 写入。
     */
    fun processClaimedEnrichmentJobBatch(
        jobs: List<ExpertAcademicEnrichmentJob>,
        requestKind: RequestKind,
        taskType: String = EXPERT_ENRICHMENT_TASK_TYPE
    ): AutoEnrichmentBatchResult {
        val claimed = jobs.filter { it.id != null && it.leaseToken != null }
        if (claimed.isEmpty()) return AutoEnrichmentBatchResult()

        val profiles = expertSearchService.findByDocumentIds(ExpertIndexLevel.RAW, claimed.map { it.expertDocId })
        if (profiles.isEmpty()) {
            throw IllegalStateException(
                "RAW 文档读取失败：${claimed.size} 条补全任务无法定位专家文档，本轮不写任务终态"
            )
        }
        val profilesByDocId = profiles.associateBy { enrichmentDocId(it) }
        val work = claimed.map { job -> job to profilesByDocId[job.expertDocId] }
        val outcomes = enrichProfiles(work.mapNotNull { (_, profile) -> profile }, requestKind)

        val counters = BatchCounters()
        val bySource = LinkedHashMap<String, SourceBucket>()
        var deferredUntil: String? = null
        var cancelled = false

        for ((job, profile) in work) {
            if (progressStore.isCancelled(taskType)) {
                log.info("补全批次已取消，剩余 {} 条任务保持租约未完成", work.size - counters.enqueued)
                cancelled = true
                break
            }
            val bucket = bySource.getOrPut(job.source) { SourceBucket() }
            bucket.enqueued++
            counters.enqueued++
            // 文档读不到（ES 查无此 `_id`）：算可重试失败，绝不伪造成功；故障尝试用尽后由 07 记为 FAILED。
            val outcome = profile?.let { outcomes[enrichmentDocId(it)] } ?: ProfileEnrichmentOutcome.RetryableError()
            if (outcome is ProfileEnrichmentOutcome.Deferred && deferredUntil == null) {
                deferredUntil = outcome.resetAt.toString()
            }
            if (!enrichmentJobService.complete(job.id!!, job.leaseToken!!, outcome)) {
                // 租约已被其他 worker 拿走或行不再是 RUNNING：不写任何列，也不计入结果桶。
                log.warn("补全任务 {} 未写终态：租约已失效或行已非 RUNNING", job.expertDocId)
                counters.claimLost++
                continue
            }
            when (classifyBatchOutcome(outcome, job.attempts)) {
                BatchOutcomeBucket.SUCCEEDED -> { counters.succeeded++; bucket.succeeded++ }
                BatchOutcomeBucket.PENDING -> { counters.pending++; bucket.pending++ }
                BatchOutcomeBucket.UNMATCHED -> { counters.unmatched++; bucket.unmatched++ }
                BatchOutcomeBucket.FAILED -> { counters.failed++; bucket.failed++ }
            }
            // I-3：只有「成功且 RAW-only」的专家才做定向复评。
            if (outcome is ProfileEnrichmentOutcome.Success && isRawOnly(outcome.layers)) {
                counters.revalidated++
                if (revalidateRawOnlySuccess(job.expertDocId)) counters.promoted++
            }
        }

        val result = AutoEnrichmentBatchResult(
            claimed = counters.enqueued,
            succeeded = counters.succeeded,
            pending = counters.pending,
            unmatched = counters.unmatched,
            failed = counters.failed,
            claimLost = counters.claimLost,
            revalidated = counters.revalidated,
            promoted = counters.promoted,
            bySource = bySource.mapValues { (_, bucket) -> bucket.toCounts() },
            budgetDeferred = deferredUntil != null,
            deferredUntil = deferredUntil,
            cancelled = cancelled
        )
        recordEnrichmentBatch(taskType, result)
        return result
    }

    /**
     * I-3（08）：定向复评的适用对象 —— 只对**RAW-only**（CANDIDATE/APPLICATION 都不存在）的专家执行，
     * 已有候选/申请的专家不重建、不降级。
     */
    private fun isRawOnly(layers: LayerUpdateResult): Boolean =
        layers.updatedAnyLayer() &&
            layers.candidate == LayerUpdateStatus.ABSENT &&
            layers.application == LayerUpdateStatus.ABSENT

    /**
     * I-3（08）：补全成功后的定向复评（06 的核心，门禁与候选写入都不变）。
     * 复评失败不影响已按真实 `_id` 写回的补全事实，也不把补全结果改判为失败。
     */
    private fun revalidateRawOnlySuccess(docId: String): Boolean = try {
        revalidationService.revalidateEnrichedRaw(docId) == PromotionOutcome.Promoted
    } catch (e: Exception) {
        log.warn("补全后定向复评失败 {}: {}", docId, e.message)
        false
    }

    /** I-4（08）：把一批的逐源计数写进既有进度 details（不加列、不改前端），并留存供 `/enrich/stats` 读取。 */
    private fun recordEnrichmentBatch(taskType: String, result: AutoEnrichmentBatchResult) {
        lastEnrichmentBatch.set(result)
        progressStore.update(taskType, TaskProgress(
            taskType = taskType,
            status = if (result.cancelled) "CANCELLED" else "COMPLETED",
            batchNumber = -1,
            processedCount = (result.succeeded + result.pending + result.unmatched + result.failed).toLong(),
            totalCount = result.claimed.toLong(),
            message = buildEnrichmentBatchMessage(result),
            details = result.toDetails()
        ), progressStore.getCurrentExecutionId(taskType))
    }

    private fun buildEnrichmentBatchMessage(result: AutoEnrichmentBatchResult): String {
        val deferred = result.deferredUntil?.let { "，额度延期至 $it" } ?: ""
        return "补全批次: 领取 ${result.claimed}, 成功 ${result.succeeded}, 待补 ${result.pending}, " +
            "未匹配 ${result.unmatched}, 失败 ${result.failed}$deferred"
    }

    /**
     * I-4（08）：把 06 的逐人结果投射成批次结果桶，口径与 07 的状态机一致：
     * `Success → SUCCEEDED`；`NotFound`/`NoId → UNMATCHED`；`Partial` 与网络/5xx 故障各消耗一次故障尝试，
     * 达到 [ExpertAcademicEnrichmentJobService.MAX_FAILURE_ATTEMPTS] 即为 `FAILED`，否则 `PENDING`（待重试）；
     * 额度延期与限流不消耗故障尝试。这里只做计数投射，状态本身由 07 写入。
     */
    private fun classifyBatchOutcome(outcome: ProfileEnrichmentOutcome, currentAttempts: Int): BatchOutcomeBucket =
        when (outcome) {
            is ProfileEnrichmentOutcome.Success -> BatchOutcomeBucket.SUCCEEDED
            is ProfileEnrichmentOutcome.Deferred -> BatchOutcomeBucket.PENDING
            ProfileEnrichmentOutcome.NotFound, ProfileEnrichmentOutcome.NoId -> BatchOutcomeBucket.UNMATCHED
            is ProfileEnrichmentOutcome.Partial ->
                if (exhaustsFailureBudget(currentAttempts)) BatchOutcomeBucket.FAILED else BatchOutcomeBucket.PENDING
            is ProfileEnrichmentOutcome.RetryableError -> when {
                outcome.rateLimited -> BatchOutcomeBucket.PENDING
                exhaustsFailureBudget(currentAttempts) -> BatchOutcomeBucket.FAILED
                else -> BatchOutcomeBucket.PENDING
            }
        }

    /** 07 的故障尝试上限对齐：本次完成会把故障尝试 +1，达到上限即 `FAILED`。 */
    private fun exhaustsFailureBudget(currentAttempts: Int): Boolean =
        currentAttempts + 1 >= ExpertAcademicEnrichmentJobService.MAX_FAILURE_ATTEMPTS

    /** 批次内累加器（只在本方法的作用域内使用，不进任何持久化契约）。 */
    private class BatchCounters {
        var enqueued = 0
        var succeeded = 0
        var pending = 0
        var unmatched = 0
        var failed = 0
        var claimLost = 0
        var revalidated = 0
        var promoted = 0
    }

    private class SourceBucket {
        var enqueued = 0
        var succeeded = 0
        var pending = 0
        var unmatched = 0
        var failed = 0

        fun toCounts() = AutoEnrichmentSourceCounts(enqueued, succeeded, pending, unmatched, failed)
    }

    private enum class BatchOutcomeBucket { SUCCEEDED, PENDING, UNMATCHED, FAILED }

    /**
     * I-2：学术字段的唯一写入点。按真实 `_id` 对每个已存在层做局部 `_update`：404 跳过、非 404 失败可按层重试；
     * 只写非 null 事实（null 绝不覆盖已有值）并重算分类；不触碰姓名/邮箱/署名机构/运营状态，
     * 也绝不创建缺失层（尤其不创建 APPLICATION）。返回逐层结果而不是单一布尔。
     */
    private fun updateExpertAcademicFields(profile: ExpertProfile, enrichment: AuthorEnrichment): LayerUpdateResult {
        val docId = enrichmentDocId(profile)
        val now = LocalDateTime.now().format(dateFormatter)
        val doc = mutableMapOf<String, Any?>(
            "updatedAt" to now,
            "enrichedAt" to now,
            "enrichmentSource" to "OPENALEX"
        )
        // I-2：null 事实不写入（否则 _update 会用 null 擦掉已有指标）。
        enrichment.hIndex?.let { doc["hIndex"] = it }
        enrichment.citationCount?.let { doc["citationCount"] = it }
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
        return LayerUpdateResult(
            raw = updateAcademicFieldsInLayer(ExpertIndexLevel.RAW, docId, updateBody),
            candidate = updateAcademicFieldsInLayer(ExpertIndexLevel.CANDIDATE, docId, updateBody),
            application = updateAcademicFieldsInLayer(ExpertIndexLevel.APPLICATION, docId, updateBody)
        )
    }

    /**
     * I-2：单层局部更新。HEAD 404 = 该层本来就没有这份文档（跳过，绝不创建）；其余 HEAD 失败与
     * `_update` 失败都标 [LayerUpdateStatus.FAILED] 以便按层重试（成功层在重试时不会被再次破坏）。
     */
    private fun updateAcademicFieldsInLayer(
        level: ExpertIndexLevel,
        docId: String,
        updateBody: Map<String, Any?>
    ): LayerUpdateStatus {
        val index = expertIndexService.indexName(level)
        try {
            restTemplate.exchange(
                "${esProperties.baseUrl}/$index/_doc/$docId", HttpMethod.HEAD, HttpEntity(null, esHeaders()),
                Void::class.java
            )
        } catch (e: HttpClientErrorException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) return LayerUpdateStatus.ABSENT
            log.warn("Failed to check academic field target for {} in index {}: {}", docId, level, e.message)
            return LayerUpdateStatus.FAILED
        } catch (e: Exception) {
            log.warn("Failed to check academic field target for {} in index {}: {}", docId, level, e.message)
            return LayerUpdateStatus.FAILED
        }
        return try {
            restTemplate.exchange(
                "${esProperties.baseUrl}/$index/_update/$docId", HttpMethod.POST,
                HttpEntity(updateBody, esHeaders()), com.fasterxml.jackson.databind.JsonNode::class.java
            )
            LayerUpdateStatus.UPDATED
        } catch (e: Exception) {
            log.warn("Failed to update academic fields for {} in index {}: {}", docId, level, e.message)
            LayerUpdateStatus.FAILED
        }
    }

    /** I-1：定位文档一律用真实 `_id`；缺失时退回既有口径 `orcidId`，API ID 绝不参与定位。 */
    private fun enrichmentDocId(profile: ExpertProfile): String = profile.esDocId ?: profile.orcidId

    /** I-1：每批最多 100 个不同身份（OpenAlex 的 OR 过滤上限），配置更小时按配置。 */
    private fun enrichmentBatchSize(): Int =
        openAlexProperties.enrichmentBatchSize.coerceIn(1, MAX_ENRICHMENT_IDENTITIES_PER_BATCH)

    /**
     * I-1：可信作者身份只来自 `externalIds.openAlexAuthorId`，并且只接受 `A` + 数字的规范形状。
     * 任何其他形状（含 `EMAIL-*`、W 前缀、空串）都当作「没有作者 ID」，绝不参与查询或文档定位。
     */
    private fun trustedOpenAlexAuthorId(profile: ExpertProfile): String? {
        val externalIds = profile.externalIds ?: return null
        val parsed = try {
            objectMapper.readValue(externalIds, Map::class.java)
        } catch (e: Exception) {
            return null
        }
        return normalizeOpenAlexAuthorId(parsed["openAlexAuthorId"] as? String)
    }

    /** I-1：有效 ORCID = 非空且不是 `EMAIL-*` 主键；`EMAIL-*` 绝不能作为 `filter=orcid:` 的值。 */
    private fun trustedOrcid(profile: ExpertProfile): String? =
        profile.orcidId.trim().takeIf { it.isNotEmpty() && !it.startsWith(EMAIL_PRIMARY_KEY_PREFIX) }

    /**
     * I-1/I-3：定向补全共享核心 —— 同一批量核心处理原始库/候选库/申请库中的指定专家。
     * - 每批最多 [MAX_ENRICHMENT_IDENTITIES_PER_BATCH] 个不同身份；先用可信 `externalIds.openAlexAuthorId`，
     *   缺失时用有效 ORCID；两者都没有 = [ProfileEnrichmentOutcome.NoId]，不发作者查询；
     * - `EMAIL-*` 主键绝不当 ORCID 传出去，API ID/ORCID 也绝不替代真实 `_id` 定位文档；
     * - 结果以真实 `esDocId` 为键，逐人一个结果；
     * - 额度延期是 [ProfileEnrichmentOutcome.Deferred]（未发请求、不消耗尝试次数），限流/网络失败才是
     *   [ProfileEnrichmentOutcome.RetryableError]；
     * - 基础事实写入成功、但开关控制的最近论文/专利标题子请求失败时是 [ProfileEnrichmentOutcome.Partial]。
     */
    @JvmOverloads
    fun enrichProfiles(
        profiles: List<ExpertProfile>,
        requestKind: RequestKind = RequestKind.HISTORY_ENRICHMENT
    ): Map<String, ProfileEnrichmentOutcome> {
        val outcomes = LinkedHashMap<String, ProfileEnrichmentOutcome>()
        if (profiles.isEmpty()) return outcomes

        val openAlex = openAlexProvider.getIfAvailable()
        if (openAlex == null) {
            // 未启用 OpenAlex：本次无法补全，可等启用后重试（绝不是「查无此人」或「已完成」）。
            profiles.forEach { outcomes[enrichmentDocId(it)] = ProfileEnrichmentOutcome.RetryableError() }
            return outcomes
        }

        val byAuthorId = LinkedHashMap<String, MutableList<ExpertProfile>>()
        val byOrcid = LinkedHashMap<String, MutableList<ExpertProfile>>()
        for (profile in profiles) {
            val authorId = trustedOpenAlexAuthorId(profile)
            val orcid = trustedOrcid(profile)
            when {
                authorId != null -> byAuthorId.getOrPut(authorId) { mutableListOf() }.add(profile)
                orcid != null -> byOrcid.getOrPut(orcid) { mutableListOf() }.add(profile)
                else -> outcomes[enrichmentDocId(profile)] = ProfileEnrichmentOutcome.NoId
            }
        }

        var deferredResetAt = enrichIdentityGroups(byAuthorId, outcomes, requestKind) { ids, kind ->
            openAlex.batchEnrichByAuthorIds(ids, kind)
        }
        if (deferredResetAt == null) {
            deferredResetAt = enrichIdentityGroups(byOrcid, outcomes, requestKind) { ids, kind ->
                openAlex.batchEnrichByOrcids(ids, kind)
            }
        }
        if (deferredResetAt != null) {
            // I-5：额度耗尽后不再发请求，本轮没得出结论的身份一律标 Deferred（可续跑、不算失败）。
            for (profile in profiles) {
                val docId = enrichmentDocId(profile)
                if (!outcomes.containsKey(docId)) outcomes[docId] = ProfileEnrichmentOutcome.Deferred(deferredResetAt)
            }
        }
        return outcomes
    }

    /**
     * 按身份分批查询并写回。[groups] 的键是不同身份，值是该身份对应的全部文档。
     * 返回非 null 表示额度已耗尽：调用方停止后续查询并把剩余身份标 Deferred。
     */
    private fun enrichIdentityGroups(
        groups: Map<String, MutableList<ExpertProfile>>,
        outcomes: MutableMap<String, ProfileEnrichmentOutcome>,
        requestKind: RequestKind,
        query: (List<String>, RequestKind) -> Map<String, EnrichmentOutcome>
    ): Instant? {
        for (chunk in groups.keys.toList().chunked(enrichmentBatchSize())) {
            val lookup = try {
                query(chunk, requestKind)
            } catch (e: OpenAlexBudgetDeferredException) {
                log.info("OpenAlex budget deferred until {}: {} identities not attempted", e.resetAt, chunk.size)
                return e.resetAt
            }
            for (identity in chunk) {
                val profiles = groups[identity].orEmpty()
                when (val found = lookup[identity] ?: EnrichmentOutcome.NotFound) {
                    is EnrichmentOutcome.Success -> {
                        for (profile in profiles) {
                            val layers = updateExpertAcademicFields(profile, found.data)
                            // I-2/I-3：层写入失败或附加标题子请求失败都只算部分完成，绝不报成整体成功。
                            val partial = layers.hasFailedLayer() || found.titlesFailed
                            outcomes[enrichmentDocId(profile)] = if (partial) {
                                ProfileEnrichmentOutcome.Partial(layers, recentWorksFailed = found.titlesFailed)
                            } else {
                                ProfileEnrichmentOutcome.Success(layers)
                            }
                        }
                    }
                    is EnrichmentOutcome.NotFound ->
                        profiles.forEach { outcomes[enrichmentDocId(it)] = ProfileEnrichmentOutcome.NotFound }
                    is EnrichmentOutcome.ApiError ->
                        profiles.forEach {
                            outcomes[enrichmentDocId(it)] = ProfileEnrichmentOutcome.RetryableError()
                        }
                    is EnrichmentOutcome.RateLimited ->
                        profiles.forEach {
                            outcomes[enrichmentDocId(it)] = ProfileEnrichmentOutcome.RetryableError(
                                retryAfterMs = found.retryAfterMs, rateLimited = true
                            )
                        }
                }
            }
        }
        return null
    }

    /** I-1（08）：ORCID 就是该文档的真实 `_id`（HEAD 已按它命中），补建任务直接用这个值。 */
    private fun existsInRawIndexByOrcid(orcid: String): DedupResult {
        val url = "${esProperties.baseUrl}/${expertIndexService.indexName(ExpertIndexLevel.RAW)}/_doc/$orcid"
        return try {
            restTemplate.exchange(url, HttpMethod.HEAD, HttpEntity(null, esHeaders()), Void::class.java)
            DedupResult.Exists(orcid)
        } catch (e: HttpClientErrorException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) DedupResult.NotFound else DedupResult.Error
        } catch (e: Exception) { DedupResult.Error }
    }

    /**
     * I-1（08）：命中时取回匹配文档的真实 `_id`（`size=1` + 不取 `_source`，命中判据仍是 `total`）。
     * 只做去重读取，绝不按新论文的身份改写已入库文档；`_id` 读不到时按 [DedupResult.Exists.docId] 为空处理。
     */
    private fun existsInRawIndexByEmail(email: String): DedupResult {
        val url = "${esProperties.baseUrl}/${expertIndexService.indexName(ExpertIndexLevel.RAW)}/_search"
        val query = mapOf(
            "query" to mapOf("term" to mapOf("email" to email.lowercase(Locale.ROOT))),
            "size" to 1,
            "_source" to false
        )
        return try {
            val response = restTemplate.exchange(url, HttpMethod.POST, HttpEntity(query, esHeaders()),
                com.fasterxml.jackson.databind.JsonNode::class.java).body
            val hits = response?.path("hits")
            val total = hits?.path("total")?.path("value")?.asInt(0) ?: 0
            if (total > 0) {
                DedupResult.Exists(hits?.path("hits")?.firstOrNull()?.path("_id")?.asText()?.takeIf { it.isNotBlank() })
            } else {
                DedupResult.NotFound
            }
        } catch (e: HttpClientErrorException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) DedupResult.NotFound else DedupResult.Error
        } catch (e: Exception) { DedupResult.Error }
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
        // I-1/I-3: 作者 ID 只是 externalIds 的一个子键（合并写入，不覆盖其他导入 ID），
        // 也绝不参与 ES _id：无 ORCID 的专家主键仍是 EMAIL-*。
        normalizeOpenAlexAuthorId(authorEmail.openAlexAuthorId)?.let { ids["openAlexAuthorId"] = it }
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
                        if (!orcidId.startsWith(EMAIL_PRIMARY_KEY_PREFIX) && orcidId.isNotBlank()) {
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

    /**
     * I-1（08）：页内 RAW 已落库但补全任务入队未完成（数据库错误）。与 [RAW_WRITE_INCOMPLETE] 一样
     * 保留进入该页的游标以便重放补建，但原因单独给：两者是不同的持久化缺口。
     */
    const val ENQUEUE_INCOMPLETE = "ENQUEUE_INCOMPLETE"

    /**
     * I-4: 分片触达供应商分页窗口（CORE offset 9000 / ORCID start 9999）。这是「该分片停止」，
     * 既不是搜索失败也不是穷尽：游标切到下一分片，未覆盖尾部只在日志与 failureReasons 里记录。
     */
    const val WINDOW_LIMIT = "WINDOW_LIMIT"

    /**
     * I-3（09）：单次发现的运行级时间预算到点。与额度延期/熔断一样保留进入该页的检查点，
     * 不把未消费的半页当成已消费，也绝不置 `exhausted`。
     */
    const val TIME_BUDGET = "TIME_BUDGET"
}

/**
 * 人工补全 scope（08 追加 [DISCOVERY_PENDING]）：
 * - [DEFAULT] / [INSTITUTION_TYPE_BACKFILL] / [LAST_PUBLICATION_YEAR_BACKFILL]：既有历史回填口径，语义不变；
 * - [DISCOVERY_PENDING]：显式重试自动补全队列里到期/待重试的任务（PENDING、到点的 RETRY_WAIT、
 *   租约已过期的 RUNNING），与 worker 走同一批次核心。
 */
enum class EnrichmentScope { DEFAULT, INSTITUTION_TYPE_BACKFILL, LAST_PUBLICATION_YEAR_BACKFILL, DISCOVERY_PENDING }

/** I-1：无 ORCID 的专家主键前缀 —— 它不是 ORCID，绝不能作为 `filter=orcid:` 的值传给 OpenAlex。 */
private const val EMAIL_PRIMARY_KEY_PREFIX = "EMAIL-"

/** I-1：OpenAlex 的 `filter=...|...` 每批最多 100 个不同身份。 */
private const val MAX_ENRICHMENT_IDENTITIES_PER_BATCH = 100

/** I-1（08）：补全入队的失败原因码（与 [DiscoveryStopReason.ENQUEUE_INCOMPLETE] 配对）。 */
private const val ENRICHMENT_ENQUEUE_FAILED = "ENRICHMENT_ENQUEUE_FAILED"

/** I-4（08）：补全任务的任务类型（worker 与人工入口共用同一把互斥锁）。 */
const val EXPERT_ENRICHMENT_TASK_TYPE = "EXPERT_ENRICHMENT"

/** I-2：单层学术字段写入结果。 */
enum class LayerUpdateStatus {
    /** 文档存在且局部 `_update` 成功。 */
    UPDATED,
    /** HEAD 404：该层本来就没有这份文档，按计划跳过（绝不创建）。 */
    ABSENT,
    /** HEAD 非 404 失败或 `_update` 失败：该层可重试。 */
    FAILED
}

/**
 * I-2：三层局部更新的逐层结果。c7 直接把本类型存进 `result_json`，三个字段名即持久化契约。
 * 判定一律用方法，避免序列化出派生键。
 */
data class LayerUpdateResult(
    val raw: LayerUpdateStatus,
    val candidate: LayerUpdateStatus,
    val application: LayerUpdateStatus
) {
    /** 至少有一层真实写成功（RAW-only 补全同样算成功）。 */
    fun updatedAnyLayer(): Boolean =
        raw == LayerUpdateStatus.UPDATED ||
            candidate == LayerUpdateStatus.UPDATED ||
            application == LayerUpdateStatus.UPDATED

    /** 存在「该层本来存在但没写成功」的层：整体只能算部分完成，可按层重试。 */
    fun hasFailedLayer(): Boolean =
        raw == LayerUpdateStatus.FAILED ||
            candidate == LayerUpdateStatus.FAILED ||
            application == LayerUpdateStatus.FAILED
}

/**
 * 定向补全的逐人结果（跨子计划契约：变体名与语义固定，c7 依此持久化任务状态）。
 * 结果按真实 `esDocId` 为键；`NoId` 与 `NotFound` 都不得伪造成 `Success`。
 */
sealed class ProfileEnrichmentOutcome {
    /** 学术事实已按真实 `_id` 局部写入全部现存层。 */
    data class Success(val layers: LayerUpdateResult) : ProfileEnrichmentOutcome()

    /**
     * 基础事实已拿到，但仍有未完成部分：
     * [layers].[LayerUpdateResult.hasFailedLayer] 表示某现存层没写成功（可按层重试）；
     * [recentWorksFailed] 表示开关控制的最近论文/专利标题子请求失败（可单独重试，空结果不算失败）。
     */
    data class Partial(val layers: LayerUpdateResult, val recentWorksFailed: Boolean) : ProfileEnrichmentOutcome()

    /** OpenAlex 日额度延期：[resetAt] 是额度实际重置时刻；未发请求，重试不消耗故障尝试次数。 */
    data class Deferred(val resetAt: Instant) : ProfileEnrichmentOutcome()

    /** OpenAlex 明确查无此身份（有 A ID/ORCID 但作者不存在）。 */
    object NotFound : ProfileEnrichmentOutcome()

    /** 无可靠身份（既无 A ID 也无有效 ORCID）：绝不发作者查询。 */
    object NoId : ProfileEnrichmentOutcome()

    /** 可重试的失败。[rateLimited] = true 是供应商限流（旧人工入口按 WAIT/ABORT 语义退避重试）。 */
    data class RetryableError(
        val retryAfterMs: Long? = null,
        val rateLimited: Boolean = false
    ) : ProfileEnrichmentOutcome()
}

/**
 * I-4（08）：单个来源在本批次补全里的计数。四个结果桶与 07 的任务终态同口径
 * （成功 / 待重试 / 未匹配 / 失败），`enqueued` 是本批为该来源领取处理的任务数。
 * 这是进度 details 与 `/enrich/stats` 的展示口径，不是任务生命周期的唯一事实（那是任务表本身）。
 */
data class AutoEnrichmentSourceCounts(
    val enqueued: Int = 0,
    val succeeded: Int = 0,
    val pending: Int = 0,
    val unmatched: Int = 0,
    val failed: Int = 0
)

/**
 * I-2/I-4（08）：一批补全的结果 —— 领取了多少、逐源成功/待补/未匹配/失败多少，以及额度延期与取消。
 * worker 用它写自己的 EXPERT_ENRICHMENT 任务记录（逐源计数放在既有 details/result_summary JSON 里），
 * `/enrich/stats` 用它展示最近一批；发现成功数与本结果完全无关（不叠加）。
 */
data class AutoEnrichmentBatchResult(
    /** 本批实际交给补全核心的任务数（= 各来源 `enqueued` 之和）。 */
    val claimed: Int = 0,
    val succeeded: Int = 0,
    /** 到点待重试（含额度延期、限流、可重试故障）的任务数。 */
    val pending: Int = 0,
    val unmatched: Int = 0,
    val failed: Int = 0,
    /** 完成时租约已失效/行不再是 RUNNING 的任务数（不写任何列）。 */
    val claimLost: Int = 0,
    /** 达到 06 定向复评条件（成功且 RAW-only）的专家数与其候选晋升数。 */
    val revalidated: Int = 0,
    val promoted: Int = 0,
    val bySource: Map<String, AutoEnrichmentSourceCounts> = emptyMap(),
    /** OpenAlex 日额度延期：本批没有继续重试，其余来源的发现/补全不受影响。 */
    val budgetDeferred: Boolean = false,
    val deferredUntil: String? = null,
    val cancelled: Boolean = false
) : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int get() = succeeded

    /** 未匹配（无可靠身份/查无作者）与故障失败都算本批未完成的任务。 */
    override val taskFailureCount: Int get() = failed + unmatched

    override val taskFinalStatus: String?
        get() = when {
            cancelled -> "CANCELLED"
            budgetDeferred || pending > 0 -> "PARTIAL_SUCCESS"
            else -> null
        }

    /** I-4：逐源入队/成功/待补/未匹配/失败 —— 只进既有 details/result_summary JSON，不加列也不改前端。 */
    fun toDetails(): Map<String, Any> {
        val details = LinkedHashMap<String, Any>()
        details["claimed"] = claimed
        details["succeeded"] = succeeded
        details["pending"] = pending
        details["unmatched"] = unmatched
        details["failed"] = failed
        details["revalidated"] = revalidated
        details["promoted"] = promoted
        details["budgetDeferred"] = budgetDeferred
        if (claimLost > 0) details["claimLost"] = claimLost
        deferredUntil?.let { details["deferredUntil"] = it }
        details["bySource"] = bySource.mapValues { (_, counts) ->
            mapOf(
                "enqueued" to counts.enqueued,
                "succeeded" to counts.succeeded,
                "pending" to counts.pending,
                "unmatched" to counts.unmatched,
                "failed" to counts.failed
            )
        }
        return details
    }
}

data class EnrichmentStats(
    val pending: Long,
    val enrichedLast30d: Long,
    val total: Long,
    val institutionTypePending: Long,
    val lastPublicationYearPending: Long,
    /**
     * I-4（08）：最近一批自动/待补补全的逐源计数（进程内观测值，重启即空）；
     * 历史任务的详情始终是当时快照，不会被后续补全改写。
     */
    val autoEnrichment: AutoEnrichmentBatchResult? = null
)

data class EnrichmentResult(
    val enriched: Int,
    val failed: Int,
    val failureReasons: Map<String, Int> = emptyMap(),
    val wasCancelled: Boolean = false,
    val circuitBreakerTripped: Boolean = false,
    /** I-5：OpenAlex 日额度延期导致本轮提前停止 —— 不是失败，剩余工作可续跑。 */
    val budgetDeferred: Boolean = false
) : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int get() = enriched
    override val taskFailureCount: Int get() = failed
    override val taskFinalStatus: String?
        get() = when {
            wasCancelled -> "CANCELLED"
            circuitBreakerTripped -> "FAILED"
            budgetDeferred -> "PARTIAL_SUCCESS"
            else -> null
        }
}
