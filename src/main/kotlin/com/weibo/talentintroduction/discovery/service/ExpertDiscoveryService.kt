package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.config.OpenAlexProperties
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.DiscoveryResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryStats
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult
import com.weibo.talentintroduction.discovery.domain.SourceStats
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
    private val restTemplate: RestTemplate,
    private val esProperties: ElasticsearchProperties,
    private val discoveryProperties: ExpertDiscoveryProperties,
    private val openAlexProperties: OpenAlexProperties,
    private val objectMapper: ObjectMapper,
    private val progressStore: TaskProgressStore,
    private val cursorRepository: DiscoverySourceCursorRepository,
    @Qualifier("discoveryFetchExecutor")
    private val discoveryFetchExecutor: Executor
) {
    private val log = LoggerFactory.getLogger(ExpertDiscoveryService::class.java)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** Sources whose cursors expire between runs (e.g. ES scroll context) */
    private val nonPersistableCursorSources = setOf("CORE")

    private fun loadSourceCursor(sourceName: String): String? {
        if (sourceName in nonPersistableCursorSources) return null
        return try {
            cursorRepository.findBySourceName(sourceName)?.cursorValue
        } catch (e: Exception) {
            log.warn("Failed to load cursor for {}: {}", sourceName, e.message)
            null
        }
    }

    private fun saveSourceCursor(sourceName: String, cursorValue: String?, papersInRun: Int) {
        if (sourceName in nonPersistableCursorSources) return
        try {
            val now = LocalDateTime.now()
            val existing = cursorRepository.findBySourceName(sourceName)
            val entity = if (existing != null) {
                existing.copy(
                    cursorValue = cursorValue,
                    papersProcessedTotal = existing.papersProcessedTotal + papersInRun,
                    lastRunAt = now,
                    updatedAt = now
                )
            } else {
                DiscoverySourceCursor(
                    sourceName = sourceName,
                    cursorValue = cursorValue,
                    papersProcessedTotal = papersInRun.toLong(),
                    lastRunAt = now,
                    updatedAt = now
                )
            }
            cursorRepository.save(entity)
            log.info("[{}] 游标已保存: cursor={}, 累计论文={}", sourceName,
                cursorValue?.take(30) ?: "null(已穷尽)", entity.papersProcessedTotal)
        } catch (e: Exception) {
            log.warn("Failed to save cursor for {}: {}", sourceName, e.message)
        }
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
                "apiRequests" to ss.apiRequests
            )
        }
        return bySource
    }

    private fun buildSummaryText(stats: DiscoveryStats, totalElapsed: Long): String {
        val sourceSummaries = stats.bySource.map { (name, ss) ->
            "$name 收录 ${ss.indexed}/晋升 ${ss.promoted}"
        }.joinToString(", ")
        return "发现任务完成: 总耗时 ${totalElapsed}ms | 各平台: $sourceSummaries | 合计: 论文 ${stats.totalPapers}, 收录 ${stats.indexed}, 晋升 ${stats.promoted}"
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
            if (src != null && (criteria.sources.isEmpty() || criteria.sources.contains(name))) {
                sources.add(src)
            }
        }
        add({ europePmc }, europePmc.sourceName)
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

                val savedCursor = loadSourceCursor(source.sourceName)
                val sourceCriteria = if (savedCursor != null) criteria.copy(cursor = savedCursor) else criteria
                if (savedCursor != null) {
                    log.info("[{}] 从上次游标继续: {}", source.sourceName, savedCursor.take(50))
                }
                val finalCursor = discoverFromSource(source, sourceCriteria, stats)
                val sourceStats = stats.bySource[source.sourceName]
                saveSourceCursor(source.sourceName, finalCursor, sourceStats?.papersSearched ?: 0)
            }

            val orcidSavedCursor = loadSourceCursor("ORCID")
            val orcidCriteria = if (orcidSavedCursor != null) criteria.copy(cursor = orcidSavedCursor) else criteria
            val orcidFinalCursor = discoverFromOrcid(orcidCriteria, stats)
            val orcidStats = stats.bySource["ORCID"]
            saveSourceCursor("ORCID", orcidFinalCursor, orcidStats?.papersSearched ?: 0)
            stats.refreshGlobalCounts()

            val totalElapsed = System.currentTimeMillis() - startTime
            val details = buildProgressDetails(stats).toMutableMap()
            details["summaryText"] = buildSummaryText(stats, totalElapsed)

            if (progressStore.isCancelled("EXPERT_DISCOVERY")) {
                log.info("发现任务取消: 论文=${stats.totalPapers}, 收录=${stats.indexed}, 晋升=${stats.promoted}")
                progressStore.update("EXPERT_DISCOVERY", TaskProgress(
                    taskType = "EXPERT_DISCOVERY", status = "CANCELLED",
                    batchNumber = -1, processedCount = stats.totalPapers.toLong(),
                    totalCount = stats.totalPapers.toLong(),
                    message = "已取消: 论文 ${stats.totalPapers}, 收录 ${stats.indexed}, 晋升 ${stats.promoted}",
                    details = details, errors = snapshotErrors(stats)
                ), execId)
                val cancelSummary = buildSummaryText(stats, totalElapsed)
                return DiscoveryResult(triggeredBy, stats, wasCancelled = true, summaryText = cancelSummary)
            }

            val totalValidEmails = stats.bySource.values.sumOf { it.emailsValid }
            val sourceSummaries = stats.bySource.map { (name, ss) -> "$name 收录 ${ss.indexed}/晋升 ${ss.promoted}" }.joinToString(", ")
            log.info("发现任务完成: 总耗时 ${totalElapsed}ms | 各平台: $sourceSummaries | " +
                "合计: 论文 ${stats.totalPapers}, 作者候选 ${stats.totalAuthors}, " +
                "邮箱有效 $totalValidEmails (无效 ${stats.emailRejected}), 收录 ${stats.indexed}, 晋升 ${stats.promoted}")

            progressStore.update("EXPERT_DISCOVERY", TaskProgress(
                taskType = "EXPERT_DISCOVERY", status = "COMPLETED",
                batchNumber = -1, processedCount = stats.totalPapers.toLong(),
                totalCount = stats.totalPapers.toLong(),
                message = "完成: 论文 ${stats.totalPapers}, 收录 ${stats.indexed}, 晋升 ${stats.promoted}",
                details = details, errors = snapshotErrors(stats)
            ), execId)
        } catch (e: Exception) {
            stats.refreshGlobalCounts()
            val totalElapsed = System.currentTimeMillis() - startTime
            val details = buildProgressDetails(stats).toMutableMap()
            details["summaryText"] = buildSummaryText(stats, totalElapsed)
            progressStore.update("EXPERT_DISCOVERY", TaskProgress(
                taskType = "EXPERT_DISCOVERY", status = "FAILED",
                batchNumber = -1, processedCount = stats.totalPapers.toLong(), totalCount = 0,
                message = "失败: ${e.message}",
                details = details, errors = snapshotErrors(stats)
            ), execId)
            throw e
        }
        val finalElapsed = System.currentTimeMillis() - startTime
        return DiscoveryResult(triggeredBy, stats, summaryText = buildSummaryText(stats, finalElapsed))
    }

    private fun getSourceLimit(source: AcademicDataSource): Int {
        return source.maxPapersPerSource
    }

    private fun discoverFromSource(source: AcademicDataSource, criteria: PaperSearchCriteria, stats: DiscoveryStats): String? {
        val sourceStats = stats.getOrCreateSourceStats(source.sourceName, source.emailExtractionMethod)
        val sourceStartTime = System.currentTimeMillis()
        val execId = progressStore.getCurrentExecutionId("EXPERT_DISCOVERY")
        val sourceLimit = getSourceLimit(source)

        log.info("[{}] 开始: 方式={}, 本源限额={}", source.sourceName, source.emailExtractionMethod, sourceLimit)

        var cursor: String? = criteria.cursor
        var lastNextCursor: String? = null
        var batchNumber = 0
        var sourcePapersProcessed = 0
        var consecutiveFailures = 0
        var circuitBreakerTripped = false

        do {
            if (progressStore.isCancelled("EXPERT_DISCOVERY")) {
                log.info("[{}] 已取消, 当前批次={}", source.sourceName, batchNumber)
                break
            }
            stats.refreshGlobalCounts()
            if (stats.totalPapers >= discoveryProperties.maxPapersPerRun) break
            if (stats.totalAuthors >= discoveryProperties.maxAuthorsPerRun) break
            if (sourcePapersProcessed >= sourceLimit) break

            sourceStats.apiRequests++

            var batch: PaperSearchResult? = null
            try {
                batch = source.searchPapers(criteria.copy(cursor = cursor))
            } catch (e: HttpStatusCodeException) {
                val code = e.statusCode.value()
                if (code == 429 || code == 503) {
                    consecutiveFailures++
                    sourceStats.failureReasons.merge("RATE_LIMITED", 1) { a, b -> a + b }
                    if (consecutiveFailures >= 5) {
                        sourceStats.failureReasons["CIRCUIT_BREAKER"] = 1
                        circuitBreakerTripped = true
                        log.warn("[{}] 连续 5 次限流/不可用，熔断", source.sourceName)
                        break
                    }
                    Thread.sleep(1000)
                    continue
                }
                sourceStats.failureReasons.merge("SEARCH_FAILED", 1) { a, b -> a + b }
                break
            } catch (e: Exception) {
                sourceStats.failureReasons.merge("SEARCH_FAILED", 1) { a, b -> a + b }
                log.error("[{}] 搜索失败: {}", source.sourceName, e.message)
                break
            }
            if (batch == null || batch.papers.isEmpty()) break
            consecutiveFailures = 0
            batchNumber++

            val papersBefore = sourceStats.papersSearched
            val indexedBefore = sourceStats.indexed
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

            // I-4: 只有当批次全量处理完毕才推进游标；部分批次保持上一完整批次游标
            if (!limitReached) {
                lastNextCursor = batch.nextCursor
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

            if (limitReached || circuitBreakerTripped) break
            cursor = batch?.nextCursor
        } while (cursor != null)

        val elapsed = System.currentTimeMillis() - sourceStartTime
        sourceStats.elapsedMs = elapsed

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

        return lastNextCursor
    }

    private fun discoverFromOrcid(criteria: PaperSearchCriteria, stats: DiscoveryStats): String? {
        val orcid = orcidProvider.getIfAvailable() ?: return null
        if (criteria.sources.isNotEmpty() && !criteria.sources.contains(orcid.sourceName)) return null

        val sourceStats = stats.getOrCreateSourceStats(orcid.sourceName, "API_FIELD")
        val execId = progressStore.getCurrentExecutionId("EXPERT_DISCOVERY")
        val sourceStartTime = System.currentTimeMillis()

        log.info("[{}] 开始: 方式=API_FIELD", orcid.sourceName)

        val orcidLimit = orcid.maxRecordsPerRun
        var cursor: String? = criteria.cursor ?: "0"
        var batchNumber = 0
        var recordsProcessed = 0

        do {
            if (progressStore.isCancelled("EXPERT_DISCOVERY")) {
                log.info("[{}] 已取消", orcid.sourceName)
                return cursor
            }
            stats.refreshGlobalCounts()
            if (stats.totalAuthors >= discoveryProperties.maxAuthorsPerRun) break
            if (recordsProcessed >= orcidLimit) break

            sourceStats.apiRequests++

            val records = try {
                orcid.searchOrcidRecords(criteria.copy(cursor = cursor))
            } catch (e: Exception) {
                sourceStats.failureReasons.merge("SEARCH_FAILED", 1) { a, b -> a + b }
                log.error("[{}] 搜索失败: {}", orcid.sourceName, e.message)
                break
            }
            if (records.isEmpty()) break
            batchNumber++

            val indexedBefore = sourceStats.indexed
            val recordsProcessedBeforeBatch = recordsProcessed
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

            cursor = (cursor?.toIntOrNull()?.plus(records.size))?.toString()
        } while (cursor != null)

        val elapsed = System.currentTimeMillis() - sourceStartTime
        sourceStats.elapsedMs = elapsed

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

        return cursor
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
            externalIds = buildExternalIds(paper, authorEmail)
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
        val enrichedRecently = total - pending
        return EnrichmentStats(pending, enrichedRecently, total)
    }

    private fun buildEnrichmentFilters(cutoff: String): List<Map<String, Any>> {
        return listOf(
            mapOf(
                "bool" to mapOf(
                    "should" to listOf(
                        mapOf("bool" to mapOf("must_not" to listOf(mapOf("exists" to mapOf("field" to "enrichedAt"))))),
                        mapOf("range" to mapOf("enrichedAt" to mapOf("lt" to cutoff)))
                    ),
                    "minimum_should_match" to 1,
                    "must_not" to listOf(
                        mapOf("prefix" to mapOf("orcidId" to "EMAIL-"))
                    )
                )
            )
        )
    }

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

    fun enrichExistingExperts(): EnrichmentResult {
        val taskType = "EXPERT_ENRICHMENT"
        val execId = progressStore.getCurrentExecutionId(taskType)
        val cutoff = LocalDateTime.now().minusDays(30).format(dateFormatter)
        val filters = buildEnrichmentFilters(cutoff)
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
                                    if (updateExpertAcademicFields(profile.orcidId, outcome.data)) {
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

    private fun updateExpertAcademicFields(orcidId: String, enrichment: AuthorEnrichment): Boolean {
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

data class EnrichmentStats(val pending: Long, val enrichedLast30d: Long, val total: Long)

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
