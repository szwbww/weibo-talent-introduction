package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.config.ExpertClassificationProperties
import com.weibo.talentintroduction.expert.domain.ExpertClassification
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.ExpertType
import com.weibo.talentintroduction.expert.domain.PromotionScanResult
import com.weibo.talentintroduction.expert.domain.PromotionScanStats
import com.weibo.talentintroduction.expert.domain.RevalidationResult
import com.weibo.talentintroduction.expert.domain.RevalidationStats
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ExpertRevalidationService(
    private val expertSearchService: ExpertSearchService,
    private val eligibilityService: CandidateEligibilityService,
    private val emailValidationService: EmailValidationService,
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val progressStore: TaskProgressStore,
    private val eligibilityFilterService: EligibilityFilterService,
    private val expertClassificationService: ExpertClassificationService = ExpertClassificationService(),
    private val expertClassificationProperties: ExpertClassificationProperties = ExpertClassificationProperties()
) {
    private val log = LoggerFactory.getLogger(ExpertRevalidationService::class.java)

    fun revalidateCandidates(): RevalidationResult {
        val stats = RevalidationStats()
        val taskType = "EXPERT_REVALIDATION"
        val execId = progressStore.getCurrentExecutionId(taskType)

        try {
            expertSearchService.scrollExperts(ExpertIndexLevel.CANDIDATE) { batch, batchNumber, totalHits ->
                if (progressStore.isCancelled(taskType)) {
                    log.info("重新验证任务已取消，当前批次={}", batchNumber)
                    return@scrollExperts false
                }
                val processedBefore = stats.total
                val passedBefore = stats.passed
                val demotedBefore = stats.demoted
                val requireValidEmail = eligibilityFilterService.getCandidateFilter().requireValidEmail
                for (profile in batch) {
                    stats.total++

                    if (requireValidEmail) {
                        val emailResult = emailValidationService.validate(profile.email.orEmpty())
                        if (!emailResult.valid) {
                            val docId = profile.esDocId ?: profile.orcidId
                            val deleted = expertIndexWriterService.removeFromCandidateIndex(docId)
                            if (deleted) {
                                stats.demoted++
                                stats.demotionReasons.merge("EMAIL:${emailResult.rejectReason}", 1) { a, b -> a + b }
                            } else {
                                stats.demotionFailed++
                                log.warn("Failed to remove candidate {} with invalid email", profile.orcidId)
                            }
                            continue
                        }
                    }

                    val eligibility = eligibilityService.evaluateEligibility(profile)
                    if (!eligibility.eligible) {
                        val docId = profile.esDocId ?: profile.orcidId
                        val deleted = expertIndexWriterService.removeFromCandidateIndex(docId)
                        if (deleted) {
                            stats.demoted++
                            for (reason in eligibility.rejectReasons) {
                                stats.demotionReasons.merge(reason, 1) { a, b -> a + b }
                            }
                            log.info("Demoted candidate {}: {}", profile.orcidId, eligibility.rejectReasons)
                        } else {
                            stats.demotionFailed++
                            log.warn("Failed to remove candidate {}: {}", profile.orcidId, eligibility.rejectReasons)
                        }
                    } else {
                        stats.passed++
                        val docId = profile.esDocId ?: profile.orcidId
                        val tagged = expertIndexWriterService.addTag(docId, "verified", ExpertIndexLevel.CANDIDATE)
                        if (!tagged) {
                            log.warn("Failed to add verified tag to candidate {}", profile.orcidId)
                            stats.tagFailed++
                        }
                    }
                }
                val batchProcessed = stats.total - processedBefore
                val batchPassed = stats.passed - passedBefore
                val batchRejected = stats.demoted - demotedBefore
                log.info("重新验证进度: 批次={}, 本批处理={}, 累计已完成={}/{}",
                    batchNumber, batchProcessed, stats.total, totalHits)
                progressStore.update(taskType, TaskProgress(
                    taskType = taskType, status = "RUNNING",
                    batchNumber = batchNumber, processedCount = stats.total.toLong(), totalCount = totalHits,
                    message = "批次 $batchNumber: 已处理 ${stats.total}/$totalHits",
                    details = mapOf("passed" to stats.passed, "demoted" to stats.demoted, "tagFailed" to stats.tagFailed, "demotionReasons" to stats.demotionReasons),
                    errors = if (stats.tagFailed > 0) listOf("${stats.tagFailed} 个标签写入失败") else null,
                    batchProcessed = batchProcessed,
                    batchPassed = batchPassed,
                    batchRejected = batchRejected
                ), execId)
                true
            }

            if (progressStore.isCancelled(taskType)) {
                log.info("Revalidation cancelled: total={}, passed={}, demoted={}, demotionFailed={}, tagFailed={}",
                    stats.total, stats.passed, stats.demoted, stats.demotionFailed, stats.tagFailed)
                progressStore.update(taskType, TaskProgress(
                    taskType = taskType, status = "CANCELLED",
                    batchNumber = -1, processedCount = stats.total.toLong(), totalCount = stats.total.toLong(),
                    message = "已取消: 通过 ${stats.passed}, 降级 ${stats.demoted}",
                    details = mapOf("passed" to stats.passed, "demoted" to stats.demoted, "demotionFailed" to stats.demotionFailed, "tagFailed" to stats.tagFailed, "demotionReasons" to stats.demotionReasons)
                ), execId)
                return RevalidationResult(stats, wasCancelled = true)
            }

            log.info("Revalidation complete: total={}, passed={}, demoted={}, demotionFailed={}, tagFailed={}",
                stats.total, stats.passed, stats.demoted, stats.demotionFailed, stats.tagFailed)

            progressStore.update(taskType, TaskProgress(
                taskType = taskType, status = "COMPLETED",
                batchNumber = -1, processedCount = stats.total.toLong(), totalCount = stats.total.toLong(),
                message = "完成: 通过 ${stats.passed}, 降级 ${stats.demoted}",
                details = mapOf("passed" to stats.passed, "demoted" to stats.demoted, "demotionFailed" to stats.demotionFailed, "tagFailed" to stats.tagFailed, "demotionReasons" to stats.demotionReasons)
            ), execId)
        } catch (e: Exception) {
            progressStore.update(taskType, TaskProgress(
                taskType = taskType, status = "FAILED",
                batchNumber = -1, processedCount = stats.total.toLong(), totalCount = 0,
                message = "失败: ${e.message}"
            ), execId)
            throw e
        }

        return RevalidationResult(stats)
    }

    fun promoteEligibleRawExperts(): PromotionScanResult {
        val stats = PromotionScanStats()
        val taskType = "RAW_PROMOTION_SCAN"
        val execId = progressStore.getCurrentExecutionId(taskType)

        try {
            expertSearchService.scrollExperts(ExpertIndexLevel.RAW) { batch, batchNumber, totalHits ->
                stats.totalHits = totalHits
                if (progressStore.isCancelled(taskType)) {
                    log.info("RAW晋升扫描任务已取消，当前批次={}", batchNumber)
                    return@scrollExperts false
                }
                val processedBefore = stats.total
                val promotedBefore = stats.promoted
                for (profile in batch) {
                    stats.total++

                    when (val gate = evaluateRawPromotionGate(profile)) {
                        is RawPromotionGate.Rejected -> {
                            // 既有口径：邮箱拒绝单独计数，其余门禁计入 filtered；原因词汇保持不变。
                            if (gate.emailRejected) stats.emailRejected++ else stats.filtered++
                            for (reason in gate.reasons) {
                                stats.filterReasons.merge(reason, 1) { a, b -> a + b }
                            }
                            continue
                        }
                        is RawPromotionGate.Passed -> {
                            val exists: Boolean
                            try {
                                val docId = profile.esDocId ?: profile.orcidId
                                exists = expertIndexWriterService.documentExistsInIndex(
                                    ExpertIndexLevel.CANDIDATE, docId
                                )
                            } catch (e: Exception) {
                                stats.existenceCheckFailed++
                                log.warn("HEAD check failed for candidate {}: {}", profile.orcidId, e.message)
                                continue
                            }
                            if (exists) {
                                stats.alreadyPromoted++
                                continue
                            }

                            val success = promoteRawToCandidate(profile, gate.classification)
                            if (success) {
                                stats.promoted++
                            } else {
                                stats.promotionFailed++
                            }
                        }
                    }
                }
                val batchProcessed = stats.total - processedBefore
                val batchPassed = stats.promoted - promotedBefore
                val batchRejected = batchProcessed - batchPassed
                log.info("RAW晋升扫描进度: 批次={}, 本批处理={}, 累计已完成={}/{}, 已晋升={}",
                    batchNumber, batchProcessed, stats.total, totalHits, stats.promoted)
                progressStore.update(taskType, TaskProgress(
                    taskType = taskType, status = "RUNNING",
                    batchNumber = batchNumber, processedCount = stats.total.toLong(), totalCount = totalHits,
                    message = "批次 $batchNumber: 已处理 ${stats.total}/$totalHits, 已晋升 ${stats.promoted}",
                    details = mapOf("promoted" to stats.promoted, "filtered" to stats.filtered, "filterReasons" to stats.filterReasons),
                    batchProcessed = batchProcessed,
                    batchPassed = batchPassed,
                    batchRejected = batchRejected
                ), execId)
                true
            }

            if (progressStore.isCancelled(taskType)) {
                log.info("RAW promotion scan cancelled: total={}, promoted={}, filtered={}, emailRejected={}, existenceCheckFailed={}",
                    stats.total, stats.promoted, stats.filtered, stats.emailRejected, stats.existenceCheckFailed)
                progressStore.update(taskType, TaskProgress(
                    taskType = taskType, status = "CANCELLED",
                    batchNumber = -1, processedCount = stats.total.toLong(), totalCount = stats.totalHits,
                    message = "已取消: 已处理 ${stats.total}/${stats.totalHits}, 晋升 ${stats.promoted}, 过滤 ${stats.filtered}",
                    details = mapOf("promoted" to stats.promoted, "filtered" to stats.filtered, "emailRejected" to stats.emailRejected, "filterReasons" to stats.filterReasons)
                ), execId)
                return PromotionScanResult(stats, wasCancelled = true)
            }

            progressStore.update(taskType, TaskProgress(
                taskType = taskType, status = "COMPLETED",
                batchNumber = -1, processedCount = stats.total.toLong(), totalCount = stats.totalHits,
                message = "完成: 已处理 ${stats.total}/${stats.totalHits}, 晋升 ${stats.promoted}, 过滤 ${stats.filtered}",
                details = mapOf("promoted" to stats.promoted, "filtered" to stats.filtered, "emailRejected" to stats.emailRejected, "filterReasons" to stats.filterReasons)
            ), execId)
        } catch (e: Exception) {
            progressStore.update(taskType, TaskProgress(
                taskType = taskType, status = "FAILED",
                batchNumber = -1, processedCount = stats.total.toLong(), totalCount = stats.totalHits,
                message = "失败: 已处理 ${stats.total}/${stats.totalHits}, ${e.message}"
            ), execId)
            throw e
        }

        log.info("RAW promotion scan: total={}, promoted={}, filtered={}, emailRejected={}, existenceCheckFailed={}",
            stats.total, stats.promoted, stats.filtered, stats.emailRejected, stats.existenceCheckFailed)
        return PromotionScanResult(stats)
    }

    private fun promoteRawToCandidate(profile: ExpertProfile, classification: ExpertClassification): Boolean {
        val docId = profile.esDocId ?: profile.orcidId
        val rawDoc = expertIndexWriterService.readRawDocument(docId)
            ?: return false

        val now = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        val existingTags = (rawDoc["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val newTags = (existingTags + "auto_promoted").distinct()

        val doc = rawDoc.toMutableMap().apply {
            put("orcidId", docId)
            put("candidateValidatedAt", now)
            put("updatedAt", now)
            put("tags", newTags)
            put("expertClassification", expertIndexWriterService.classificationNode(classification))
        }

        return expertIndexWriterService.writeCandidateDocument(docId, doc)
    }

    /**
     * I-4：RAW → CANDIDATE 的门禁唯一实现（邮箱、资格、分类），[promoteEligibleRawExperts] 与
     * [revalidateEnrichedRaw] 共用，禁止任何一条晋升路径另写一份。
     * 顺序固定：资格 → 分类（资格不过绝不调分类）→ 邮箱；原因词汇沿用既有 filterReasons。
     */
    private fun evaluateRawPromotionGate(profile: ExpertProfile): RawPromotionGate {
        val eligibility = eligibilityService.evaluateEligibility(profile)
        if (!eligibility.eligible) {
            return RawPromotionGate.Rejected(eligibility.rejectReasons)
        }

        // I3-3: 现算，不读 profile.expertClassification（RAW 层几乎恒为 null）。
        val classification = expertClassificationService.classify(profile)
        // I3-1/I3-5: 宽档——只拒证据充分的两类，UNKNOWN 放行；开关关闭时不拒绝，但下方仍写入。
        // I3-2: 被拒不可逆——补全只扫 CANDIDATE，被挡回 RAW 的文档永不补数据，故只拒证据充分者。
        if (expertClassificationProperties.promotionGateEnabled &&
            (classification.type == ExpertType.SERVICE_ONLY || classification.type == ExpertType.OUT_OF_SCOPE)
        ) {
            return RawPromotionGate.Rejected(listOf("CLASSIFICATION:${classification.type.name}"))
        }

        if (eligibilityFilterService.getCandidateFilter().requireValidEmail) {
            val emailResult = emailValidationService.validate(profile.email.orEmpty())
            if (!emailResult.valid) {
                return RawPromotionGate.Rejected(listOf("EMAIL:${emailResult.rejectReason}"), emailRejected = true)
            }
        }
        return RawPromotionGate.Passed(classification)
    }

    /** I-4：门禁决定。[Rejected.emailRejected] 区分既有 emailRejected 计数与 filtered 计数。 */
    private sealed class RawPromotionGate {
        /** 门禁通过，附现算的分类（用于候选文档写入）。 */
        data class Passed(val classification: ExpertClassification) : RawPromotionGate()

        /** 门禁拒绝：[reasons] 沿用既有 filterReasons 词汇。 */
        data class Rejected(val reasons: List<String>, val emailRejected: Boolean = false) : RawPromotionGate()
    }

    /**
     * I-4：补全后的单人定向复评。补全使 RAW 里的学术事实变化后，用最新 RAW 源重新过一遍当前门禁：
     * - APPLICATION 或 CANDIDATE 已存在 ⇒ [PromotionOutcome.AlreadyPresent]（已申请者绝不重建候选，
     *   已有候选者不重复写入，本轮也不自动降级任何既有专家）；
     * - 否则读最新 RAW（真实 `_id`）→ 现算门禁 → 仅在通过时创建候选；
     * - 候选正文始终来自写入时刻的最新 RAW 文档（[promoteRawToCandidate] 内部再读一次），旧快照不会覆盖它。
     */
    fun revalidateEnrichedRaw(docId: String): PromotionOutcome {
        require(docId.isNotBlank()) { "docId must not be blank" }

        val alreadyApplied = try {
            expertIndexWriterService.documentExistsInIndex(ExpertIndexLevel.APPLICATION, docId)
        } catch (e: Exception) {
            log.warn("APPLICATION existence check failed for {}: {}", docId, e.message)
            return PromotionOutcome.ExistenceCheckFailed
        }
        if (alreadyApplied) return PromotionOutcome.AlreadyPresent

        val alreadyCandidate = try {
            expertIndexWriterService.documentExistsInIndex(ExpertIndexLevel.CANDIDATE, docId)
        } catch (e: Exception) {
            log.warn("CANDIDATE existence check failed for {}: {}", docId, e.message)
            return PromotionOutcome.ExistenceCheckFailed
        }
        if (alreadyCandidate) return PromotionOutcome.AlreadyPresent

        // I-4：复评对象是最新 RAW 源（按真实 _id 读取），不是调用方手里的旧快照。
        val raw = expertSearchService.findByDocumentIds(ExpertIndexLevel.RAW, listOf(docId)).firstOrNull()
            ?: return PromotionOutcome.RawMissing

        when (val gate = evaluateRawPromotionGate(raw)) {
            is RawPromotionGate.Rejected -> return PromotionOutcome.Rejected(gate.reasons)
            is RawPromotionGate.Passed ->
                return if (promoteRawToCandidate(raw, gate.classification)) {
                    PromotionOutcome.Promoted
                } else {
                    PromotionOutcome.WriteFailed
                }
        }
    }
}

/**
 * I-4：补全后 RAW 定向复评的结果。跨子计划契约：[AlreadyPresent] 表示 CANDIDATE 或 APPLICATION 已存在，
 * 调用方（c8）不得再尝试创建候选。
 */
sealed class PromotionOutcome {
    /** CANDIDATE 或 APPLICATION 已存在：不重建、不降级。 */
    object AlreadyPresent : PromotionOutcome()

    /** 门禁通过并按真实 `_id` 写入候选。 */
    object Promoted : PromotionOutcome()

    /** RAW 文档不存在或读不到：没有可复评的源。 */
    object RawMissing : PromotionOutcome()

    /** 门禁拒绝：[reasons] 沿用既有 filterReasons 词汇（"EMAIL:…" / "CLASSIFICATION:…" / 资格原因）。 */
    data class Rejected(val reasons: List<String>) : PromotionOutcome()

    /** 门禁通过但候选写入失败，可重试。 */
    object WriteFailed : PromotionOutcome()

    /** CANDIDATE/APPLICATION 存在性检查本身失败：不确定即不创建，可重试。 */
    object ExistenceCheckFailed : PromotionOutcome()
}
