package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.DiscoveryResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryStats
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.task.service.TaskExecutionSummaryProvider
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import com.weibo.talentintroduction.expert.service.CandidateEligibilityService
import com.weibo.talentintroduction.expert.service.EmailValidationService
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale

@Service
class ExpertDiscoveryService(
    private val europePmc: EuropePmcDataSource,
    private val openAlexProvider: ObjectProvider<OpenAlexDataSource>,
    private val emailValidationService: EmailValidationService,
    private val eligibilityService: CandidateEligibilityService,
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val expertIndexService: ExpertIndexService,
    private val expertSearchService: ExpertSearchService,
    private val restTemplate: RestTemplate,
    private val esProperties: ElasticsearchProperties,
    private val discoveryProperties: ExpertDiscoveryProperties,
    private val objectMapper: ObjectMapper,
    private val progressStore: TaskProgressStore
) {
    private val log = LoggerFactory.getLogger(ExpertDiscoveryService::class.java)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private enum class DedupResult { EXISTS, NOT_FOUND, ERROR }

    fun discover(criteria: PaperSearchCriteria, triggeredBy: String): DiscoveryResult {
        val stats = DiscoveryStats()
        progressStore.update("EXPERT_DISCOVERY", TaskProgress(
            taskType = "EXPERT_DISCOVERY", status = "RUNNING",
            batchNumber = 0, processedCount = 0, totalCount = discoveryProperties.maxPapersPerRun.toLong(),
            message = "初始化 EuropePMC 搜索..."
        ))

        try {
            discoverFromEuropePmc(criteria, stats)

            val openAlex = openAlexProvider.getIfAvailable()
            if (openAlex != null && stats.indexed < discoveryProperties.maxAuthorsPerRun) {
                discoverFromOpenAlexViaPmc(openAlex, criteria, stats)
            }

            log.info(
                "Discovery complete: papers={}, authors={}, indexed={}, promoted={}, emailRejected={}, duplicates={}, filtered={}, rawWriteFailed={}, promotionFailed={}, dedupErrors={}",
                stats.totalPapers, stats.totalAuthors, stats.indexed, stats.promoted,
                stats.emailRejected, stats.duplicates, stats.filtered,
                stats.rawWriteFailed, stats.promotionFailed, stats.dedupErrors
            )

            progressStore.update("EXPERT_DISCOVERY", TaskProgress(
                taskType = "EXPERT_DISCOVERY", status = "COMPLETED",
                batchNumber = -1,
                processedCount = stats.totalPapers.toLong(),
                totalCount = stats.totalPapers.toLong(),
                message = "完成: 论文 ${stats.totalPapers}, 收录 ${stats.indexed}, 晋升 ${stats.promoted}",
                details = mapOf(
                    "totalPapers" to stats.totalPapers, "totalAuthors" to stats.totalAuthors,
                    "indexed" to stats.indexed, "promoted" to stats.promoted
                )
            ))
        } catch (e: Exception) {
            progressStore.update("EXPERT_DISCOVERY", TaskProgress(
                taskType = "EXPERT_DISCOVERY", status = "FAILED",
                batchNumber = -1, processedCount = stats.totalPapers.toLong(), totalCount = 0,
                message = "失败: ${e.message}"
            ))
            throw e
        }

        return DiscoveryResult(triggeredBy, stats)
    }

    private fun discoverFromEuropePmc(criteria: PaperSearchCriteria, stats: DiscoveryStats) {
        var cursor: String? = criteria.cursor
        var batchNumber = 0
        do {
            val batch = europePmc.searchPapers(criteria.copy(cursor = cursor))
            if (batch.papers.isEmpty()) break
            batchNumber++
            var limitReached = false
            for (paper in batch.papers) {
                if (stats.totalPapers >= discoveryProperties.maxPapersPerRun) { limitReached = true; break }
                if (stats.indexed >= discoveryProperties.maxAuthorsPerRun) { limitReached = true; break }
                stats.totalPapers++
                processPaper(paper, stats)
            }
            log.info("EuropePMC发现进度: 批次={}, 论文累计={}/{}, 收录={}/{}",
                batchNumber, stats.totalPapers, discoveryProperties.maxPapersPerRun,
                stats.indexed, discoveryProperties.maxAuthorsPerRun)
            progressStore.update("EXPERT_DISCOVERY", TaskProgress(
                taskType = "EXPERT_DISCOVERY", status = "RUNNING",
                batchNumber = batchNumber,
                processedCount = stats.totalPapers.toLong(),
                totalCount = discoveryProperties.maxPapersPerRun.toLong(),
                message = "EuropePMC 批次 $batchNumber: 论文 ${stats.totalPapers}/${discoveryProperties.maxPapersPerRun}, 收录 ${stats.indexed}",
                details = mapOf("indexed" to stats.indexed, "promoted" to stats.promoted, "source" to "EuropePMC")
            ))
            if (limitReached) return
            cursor = batch.nextCursor
        } while (cursor != null && stats.totalPapers < discoveryProperties.maxPapersPerRun && stats.indexed < discoveryProperties.maxAuthorsPerRun)
    }

    private fun discoverFromOpenAlexViaPmc(openAlex: OpenAlexDataSource, criteria: PaperSearchCriteria, stats: DiscoveryStats) {
        var cursor: String? = null
        var batchNumber = 0
        do {
            val batch = openAlex.searchPapers(criteria.copy(cursor = cursor))
            if (batch.papers.isEmpty()) break
            batchNumber++
            var limitReached = false
            for (paper in batch.papers) {
                if (stats.totalPapers >= discoveryProperties.maxPapersPerRun) { limitReached = true; break }
                if (stats.indexed >= discoveryProperties.maxAuthorsPerRun) { limitReached = true; break }
                if (paper.pmcId == null) continue
                stats.totalPapers++
                processPaper(paper, stats)
            }
            log.info("OpenAlex发现进度: 批次={}, 论文累计={}/{}, 收录={}/{}",
                batchNumber, stats.totalPapers, discoveryProperties.maxPapersPerRun,
                stats.indexed, discoveryProperties.maxAuthorsPerRun)
            progressStore.update("EXPERT_DISCOVERY", TaskProgress(
                taskType = "EXPERT_DISCOVERY", status = "RUNNING",
                batchNumber = batchNumber,
                processedCount = stats.totalPapers.toLong(),
                totalCount = discoveryProperties.maxPapersPerRun.toLong(),
                message = "OpenAlex 批次 $batchNumber: 论文 ${stats.totalPapers}/${discoveryProperties.maxPapersPerRun}, 收录 ${stats.indexed}",
                details = mapOf("indexed" to stats.indexed, "promoted" to stats.promoted, "source" to "OpenAlex")
            ))
            if (limitReached) return
            cursor = batch.nextCursor
        } while (cursor != null && stats.totalPapers < discoveryProperties.maxPapersPerRun && stats.indexed < discoveryProperties.maxAuthorsPerRun)
    }

    private fun processPaper(paper: PaperMetadata, stats: DiscoveryStats) {
        val pmcId = paper.pmcId
        if (pmcId == null) {
            stats.noEmailPapers++
            return
        }
        val authorEmails = europePmc.extractEmailsFromFullText(pmcId)
        if (authorEmails.isEmpty()) {
            stats.noEmailPapers++
            return
        }
        for (authorEmail in authorEmails) {
            if (stats.indexed >= discoveryProperties.maxAuthorsPerRun) return
            stats.totalAuthors++

            val emailResult = emailValidationService.validate(authorEmail.email)
            if (!emailResult.valid) {
                stats.emailRejected++
                continue
            }

            when (existsInRawIndexByEmail(authorEmail.email)) {
                DedupResult.EXISTS -> { stats.duplicates++; continue }
                DedupResult.ERROR -> { stats.dedupErrors++; continue }
                DedupResult.NOT_FOUND -> {}
            }

            if (authorEmail.orcidId != null) {
                when (existsInRawIndexByOrcid(authorEmail.orcidId)) {
                    DedupResult.EXISTS -> { stats.duplicates++; continue }
                    DedupResult.ERROR -> { stats.dedupErrors++; continue }
                    DedupResult.NOT_FOUND -> {}
                }
            }

            val profile = buildProfile(paper, authorEmail, emailResult.level)
            val esDocId = if (authorEmail.orcidId != null) authorEmail.orcidId
                else generateIdFromEmail(authorEmail.email)
            val eligibility = eligibilityService.evaluateEligibility(profile)
            val filterResult = if (eligibility.eligible) "PASSED" else "REJECTED"
            val rejectReasons = if (eligibility.eligible) emptyList() else eligibility.rejectReasons

            val profileMap = toIndexMap(profile, paper, esDocId, filterResult, rejectReasons)
            val indexed = expertIndexWriterService.indexToRaw(esDocId, profileMap)
            if (!indexed) {
                stats.rawWriteFailed++
                continue
            }
            stats.indexed++

            if (eligibility.eligible) {
                if (promoteDiscoveredToCandidate(esDocId, profileMap)) {
                    stats.promoted++
                } else {
                    stats.promotionFailed++
                }
            } else {
                stats.filtered++
                for (reason in rejectReasons) {
                    stats.filterReasons.merge(reason, 1) { a, b -> a + b }
                }
            }
        }
    }

    private fun buildProfile(
        paper: PaperMetadata,
        authorEmail: AuthorEmail,
        emailVerifiedLevel: Int
    ): ExpertProfile {
        val orcidId = authorEmail.orcidId ?: ""

        return ExpertProfile(
            orcidId = orcidId,
            email = authorEmail.email.lowercase(Locale.ROOT),
            givenNames = authorEmail.givenNames,
            familyNames = authorEmail.familyNames,
            country = inferCountryFromAffiliation(authorEmail.affiliation),
            keyword = null,
            employment = authorEmail.affiliation,
            institution = authorEmail.affiliation,
            lastPublicationYear = paper.pubYear,
            emailSource = "PAPER_FULLTEXT",
            emailVerifiedLevel = emailVerifiedLevel,
            dataSource = paper.source,
            externalIds = buildExternalIds(paper, authorEmail)
        )
    }

    private fun toIndexMap(
        profile: ExpertProfile,
        paper: PaperMetadata,
        esDocId: String,
        filterResult: String,
        rejectReasons: List<String>
    ): Map<String, Any?> {
        val now = LocalDateTime.now().format(dateFormatter)
        return mapOf(
            "orcidId" to profile.orcidId,
            "email" to profile.email,
            "givenNames" to profile.givenNames,
            "familyNames" to profile.familyNames,
            "country" to profile.country,
            "keyword" to profile.keyword,
            "employment" to profile.employment,
            "institution" to profile.institution,
            "lastPublicationYear" to profile.lastPublicationYear,
            "emailSource" to profile.emailSource,
            "emailVerifiedLevel" to profile.emailVerifiedLevel,
            "dataSource" to profile.dataSource,
            "externalIds" to profile.externalIds?.let { objectMapper.readValue(it, Map::class.java) },
            "discoveredAt" to now,
            "filterResult" to filterResult,
            "filterRejectReason" to rejectReasons.takeIf { it.isNotEmpty() }?.joinToString("; ")
        )
    }

    private fun promoteDiscoveredToCandidate(esDocId: String, rawDoc: Map<String, Any?>): Boolean {
        val candidateIndex = expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)
        val now = LocalDateTime.now().format(dateFormatter)
        val candidateDoc = rawDoc.toMutableMap().apply {
            put("candidateValidatedAt", now)
            put("updatedAt", now)
        }
        val putUrl = "${esProperties.baseUrl}/$candidateIndex/_doc/$esDocId"
        return try {
            restTemplate.exchange(putUrl, HttpMethod.PUT, HttpEntity(candidateDoc, esHeaders()), com.fasterxml.jackson.databind.JsonNode::class.java)
            true
        } catch (e: Exception) {
            log.warn("Failed to promote discovered expert {} to candidate: {}", esDocId, e.message)
            false
        }
    }

    fun enrichExistingExperts(maxExperts: Int = 500): EnrichmentResult {
        val openAlex = openAlexProvider.getIfAvailable() ?: return EnrichmentResult(0, 0)
        var enriched = 0
        var failed = 0
        var scanned = 0
        progressStore.update("EXPERT_ENRICHMENT", TaskProgress(
            taskType = "EXPERT_ENRICHMENT", status = "RUNNING",
            batchNumber = 0, processedCount = 0, totalCount = maxExperts.toLong(),
            message = "初始化中..."
        ))

        try {
            expertSearchService.scrollExperts(ExpertIndexLevel.CANDIDATE) { batch, batchNumber, totalHits ->
                var limitReached = false
                for (profile in batch) {
                    if (enriched + failed >= maxExperts) {
                        limitReached = true
                        break
                    }
                    scanned++
                    if (profile.hIndex != null) continue

                    val orcid = profile.orcidId.takeIf { !it.startsWith("EMAIL-") }
                    if (orcid != null) {
                        val enrichment = openAlex.enrichAuthorByOrcid(orcid)
                        if (enrichment != null) {
                            if (updateExpertAcademicFields(profile.orcidId, enrichment)) {
                                enriched++
                            } else {
                                failed++
                            }
                        } else {
                            failed++
                        }
                    }
                }
                log.info("专家丰富进度: 批次={}, 已丰富={}, 失败={}, 上限={}, 累计扫描={}/{}",
                    batchNumber, enriched, failed, maxExperts, scanned, totalHits)
                progressStore.update("EXPERT_ENRICHMENT", TaskProgress(
                    taskType = "EXPERT_ENRICHMENT", status = "RUNNING",
                    batchNumber = batchNumber, processedCount = enriched.toLong(), totalCount = maxExperts.toLong(),
                    message = "批次 $batchNumber: 已丰富 $enriched/$maxExperts",
                    details = mapOf("enriched" to enriched, "failed" to failed, "scanned" to scanned)
                ))
                !limitReached && enriched + failed < maxExperts
            }

            progressStore.update("EXPERT_ENRICHMENT", TaskProgress(
                taskType = "EXPERT_ENRICHMENT", status = "COMPLETED",
                batchNumber = -1, processedCount = enriched.toLong(), totalCount = enriched.toLong(),
                message = "完成: 丰富 $enriched, 失败 $failed"
            ))
        } catch (e: Exception) {
            progressStore.update("EXPERT_ENRICHMENT", TaskProgress(
                taskType = "EXPERT_ENRICHMENT", status = "FAILED",
                batchNumber = -1, processedCount = enriched.toLong(), totalCount = 0,
                message = "失败: ${e.message}"
            ))
            throw e
        }

        log.info("Enrichment complete: enriched={}, failed={}, scanned={}", enriched, failed, scanned)
        return EnrichmentResult(enriched, failed)
    }

    private fun updateExpertAcademicFields(orcidId: String, enrichment: AuthorEnrichment): Boolean {
        val now = LocalDateTime.now().format(dateFormatter)
        var candidateUpdated = false

        val doc = mutableMapOf<String, Any?>(
            "hIndex" to enrichment.hIndex,
            "citationCount" to enrichment.citationCount,
            "updatedAt" to now
        )
        enrichment.worksCount?.let { doc["worksCount"] = it }
        val updateBody = mapOf("doc" to doc)

        for (level in listOf(ExpertIndexLevel.RAW, ExpertIndexLevel.CANDIDATE, ExpertIndexLevel.APPLICATION)) {
            try {
                val index = expertIndexService.indexName(level)
                val updateUrl = "${esProperties.baseUrl}/$index/_update/$orcidId"
                restTemplate.exchange(updateUrl, HttpMethod.POST, HttpEntity(updateBody, esHeaders()), com.fasterxml.jackson.databind.JsonNode::class.java)
                if (level == ExpertIndexLevel.CANDIDATE) candidateUpdated = true
            } catch (e: Exception) {
                log.warn("Failed to update academic fields for {} in index {}: {}", orcidId, level, e.message)
            }
        }

        return candidateUpdated
    }

    private fun existsInRawIndexByOrcid(orcid: String): DedupResult {
        val rawIndex = expertIndexService.indexName(ExpertIndexLevel.RAW)
        val url = "${esProperties.baseUrl}/$rawIndex/_doc/$orcid"
        return try {
            restTemplate.exchange(url, HttpMethod.HEAD, HttpEntity(null, esHeaders()), Void::class.java)
            DedupResult.EXISTS
        } catch (e: HttpClientErrorException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) DedupResult.NOT_FOUND else DedupResult.ERROR
        } catch (e: Exception) {
            DedupResult.ERROR
        }
    }

    private fun existsInRawIndexByEmail(email: String): DedupResult {
        val rawIndex = expertIndexService.indexName(ExpertIndexLevel.RAW)
        val query = mapOf("query" to mapOf("term" to mapOf("email" to email.lowercase(Locale.ROOT))), "size" to 0)
        val url = "${esProperties.baseUrl}/$rawIndex/_search"
        return try {
            val response = restTemplate.exchange(url, HttpMethod.POST, HttpEntity(query, esHeaders()), com.fasterxml.jackson.databind.JsonNode::class.java).body
            val total = response?.path("hits")?.path("total")?.path("value")?.asInt(0) ?: 0
            if (total > 0) DedupResult.EXISTS else DedupResult.NOT_FOUND
        } catch (e: HttpClientErrorException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) DedupResult.NOT_FOUND else DedupResult.ERROR
        } catch (e: Exception) {
            DedupResult.ERROR
        }
    }

    private fun generateIdFromEmail(email: String): String {
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(email.lowercase(Locale.ROOT).toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(19)
        return "EMAIL-$hash"
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
        val encoded = Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
        set(HttpHeaders.AUTHORIZATION, "Basic $encoded")
    }
}

data class EnrichmentResult(
    val enriched: Int,
    val failed: Int
) : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int get() = enriched
    override val taskFailureCount: Int get() = failed
    override val taskFinalStatus: String? get() = null
}
