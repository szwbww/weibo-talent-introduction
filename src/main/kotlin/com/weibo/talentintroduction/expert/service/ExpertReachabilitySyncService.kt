package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertReachability
import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.EmailSuppressionRepository
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * 专家可达性全量回填与增量单点更新（计划 03 T3/T5）。
 *
 * - 全量 [syncAll]：以 CANDIDATE 层滚动扫描（[ExpertSearchService] 的 scroll 原语）为唯一驱动源（I-3-3，
 *   与 CandidateOperatorStatusSyncService 的 MySQL 驱动方向相反——可达性要覆盖**从未联系过**的专家）；
 *   mapping 断言前置 fail-fast（I-3-6）；逐批聚合 [BulkSyncResult] 并上报 progressStore（I-3-4）。
 * - 增量 [markBlockedByEmail] / [markBlockedByContact]：退订 / 硬退落库后立即单点写 BLOCKED，
 *   无需等待下一轮全量扫描；调用方（EmailSuppressionService / BounceCollectionService）负责 fail-open 吞异常（I-3-5）。
 */
@Service
class ExpertReachabilitySyncService(
    private val expertIndexService: ExpertIndexService,
    private val expertSearchService: ExpertSearchService,
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val classifier: ExpertReachabilityClassifier,
    private val emailSuppressionRepository: EmailSuppressionRepository,
    private val bounceRecordRepository: BounceRecordRepository,
    private val expertContactRepository: ExpertContactRepository,
    private val progressStore: TaskProgressStore,
    private val restTemplate: RestTemplate,
    private val properties: ElasticsearchProperties
) {
    private val log = LoggerFactory.getLogger(ExpertReachabilitySyncService::class.java)

    fun syncAll(): BulkSyncResult {
        // I-3-6: mapping 断言前置且 fail-fast——无 mapping 时字段写进 _source 但不进倒排索引，筛选恒 0 命中且无报错。
        if (!expertIndexService.checkReachabilityMapping()) {
            throw IllegalStateException(
                "CANDIDATE/APPLICATION 索引缺少 keyword 类型的 reachability mapping 声明，请先更新 mapping"
            )
        }
        // 全量判定集合各一次全表读（email_suppression / bounce_record HARD + expert_contact id → orcidId 映射）。
        val suppressedEmails = emailSuppressionRepository.findAll().map { it.email }.toSet()
        val hardBouncedOrcids = buildHardBouncedOrcids()
        val result = BulkSyncResult()
        // I-3-3: 扫描驱动 = CANDIDATE 层滚动扫描；禁止以 expert_contact 为驱动（未联系专家不在其中）。
        expertSearchService.scrollExperts(ExpertIndexLevel.CANDIDATE, 500) { batch, batchNumber, totalHits ->
            // map 而非 mapNotNull：value 为 null 时仍需下发 remove 脚本，
            // 使「曾经是 HIGH、现在数据被清空」的专家能退回 UNKNOWN（I-3-1）。
            val updates = batch.map { expert ->
                expert.orcidId to classifier.classify(expert, suppressedEmails, hardBouncedOrcids)
            }
            val batchResult = expertIndexWriterService.syncReachabilityBatch(updates)
            result.total += batchResult.total
            result.success += batchResult.success
            result.failure += batchResult.failure
            result.skipped += batchResult.skipped
            result.errors.addAll(batchResult.errors)
            progressStore.update("EXPERT_REACHABILITY_SYNC", TaskProgress(
                taskType = "EXPERT_REACHABILITY_SYNC",
                status = "RUNNING",
                batchNumber = batchNumber,
                processedCount = result.total.toLong(),
                totalCount = totalHits,
                message = "reachability 全量回填中"
            ))
            !progressStore.isCancelled("EXPERT_REACHABILITY_SYNC")
        }
        return result
    }

    /** 硬退集合：bounce_record HARD 且可溯源到 contact → 经 contactId → orcidId 映射（过滤写法与 OperatorStatusReconcileService 同款）。 */
    private fun buildHardBouncedOrcids(): Set<String> {
        val contactIdToOrcid = expertContactRepository.findAll()
            .mapNotNull { contact -> contact.id?.let { it to ExpertIdNormalizer.normalize(contact.orcidId) } }
            .toMap()
        return bounceRecordRepository.findAll()
            .filter { it.bounceType == "HARD" && it.originalExpertContactId != null }
            .mapNotNull { contactIdToOrcid[it.originalExpertContactId!!] }
            .toSet()
    }

    /** 增量：退订登记成功后立即写 BLOCKED_UNSUBSCRIBED（调用方负责 try/catch 吞异常，I-3-5）。 */
    fun markBlockedByEmail(normalizedEmail: String) {
        val orcids = resolveOrcidsByEmail(normalizedEmail)
        if (orcids.isEmpty()) return
        expertIndexWriterService.syncReachabilityBatch(
            orcids.map { it to ExpertReachability.BLOCKED_UNSUBSCRIBED }
        )
    }

    /** 增量：硬退落库后立即写 BLOCKED_BOUNCED（调用方负责 try/catch 吞异常，I-3-5）。 */
    fun markBlockedByContact(contact: ExpertContact) {
        expertIndexWriterService.syncReachabilityBatch(
            listOf(ExpertIdNormalizer.normalize(contact.orcidId) to ExpertReachability.BLOCKED_BOUNCED)
        )
    }

    /** 查 orcid：按 email term 查 CANDIDATE 层（email 存储已小写归一，先例见 ExpertDiscoveryService 查询）。 */
    private fun resolveOrcidsByEmail(email: String): List<String> {
        return try {
            val index = expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)
            val query = mapOf(
                "size" to 100,
                "_source" to listOf("orcidId"),
                "query" to mapOf("term" to mapOf("email" to email))
            )
            val resp = restTemplate.exchange(
                "${properties.baseUrl}/$index/_search",
                HttpMethod.POST,
                HttpEntity(query, headers()),
                JsonNode::class.java
            ).body ?: return emptyList()
            resp.path("hits").path("hits")
                .mapNotNull { hit -> hit.path("_source").path("orcidId").asText(null) }
                .map { ExpertIdNormalizer.normalize(it) }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            log.warn("Failed to resolve orcid by email {}: {}", email, e.message)
            emptyList()
        }
    }

    private fun headers(): HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set(HttpHeaders.AUTHORIZATION, basicAuthHeader())
        }

    private fun basicAuthHeader(): String {
        val raw = "${properties.username}:${properties.password}"
        val encoded = Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
        return "Basic $encoded"
    }
}
