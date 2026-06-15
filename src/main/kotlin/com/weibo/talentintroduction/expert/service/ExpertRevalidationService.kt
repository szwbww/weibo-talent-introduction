package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
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
    private val eligibilityFilterService: EligibilityFilterService
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
                            val deleted = expertIndexWriterService.removeFromCandidateIndex(profile.orcidId)
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
                        val deleted = expertIndexWriterService.removeFromCandidateIndex(profile.orcidId)
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
                        val tagged = expertIndexWriterService.addTag(profile.orcidId, "verified", ExpertIndexLevel.CANDIDATE)
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

    fun promoteEligibleRawExperts(maxPromotions: Int = 1000): PromotionScanResult {
        val stats = PromotionScanStats()
        if (maxPromotions <= 0) return PromotionScanResult(stats)
        val taskType = "RAW_PROMOTION_SCAN"
        val execId = progressStore.getCurrentExecutionId(taskType)

        try {
            val requireValidEmail = eligibilityFilterService.getCandidateFilter().requireValidEmail
            expertSearchService.scrollExperts(ExpertIndexLevel.RAW) { batch, batchNumber, totalHits ->
                if (progressStore.isCancelled(taskType)) {
                    log.info("RAW晋升扫描任务已取消，当前批次={}", batchNumber)
                    return@scrollExperts false
                }
                val processedBefore = stats.total
                val promotedBefore = stats.promoted
                var limitReached = false
                for (profile in batch) {
                    if (stats.promoted >= maxPromotions) {
                        limitReached = true
                        break
                    }
                    stats.total++

                    val eligibility = eligibilityService.evaluateEligibility(profile)
                    if (!eligibility.eligible) {
                        stats.filtered++
                        for (reason in eligibility.rejectReasons) {
                            stats.filterReasons.merge(reason, 1) { a, b -> a + b }
                        }
                        continue
                    }

                    val exists: Boolean
                    try {
                        exists = expertIndexWriterService.documentExistsInIndex(
                            ExpertIndexLevel.CANDIDATE, profile.orcidId
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

                    if (requireValidEmail) {
                        val emailResult = emailValidationService.validate(profile.email.orEmpty())
                        if (!emailResult.valid) {
                            stats.emailRejected++
                            stats.filterReasons.merge("EMAIL:${emailResult.rejectReason}", 1) { a, b -> a + b }
                            continue
                        }
                    }

                    val success = promoteRawToCandidate(profile)
                    if (success) {
                        stats.promoted++
                    } else {
                        stats.promotionFailed++
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
                !limitReached && stats.promoted < maxPromotions
            }

            if (progressStore.isCancelled(taskType)) {
                log.info("RAW promotion scan cancelled: total={}, promoted={}, filtered={}, emailRejected={}, existenceCheckFailed={}",
                    stats.total, stats.promoted, stats.filtered, stats.emailRejected, stats.existenceCheckFailed)
                progressStore.update(taskType, TaskProgress(
                    taskType = taskType, status = "CANCELLED",
                    batchNumber = -1, processedCount = stats.total.toLong(), totalCount = stats.total.toLong(),
                    message = "已取消: 晋升 ${stats.promoted}, 过滤 ${stats.filtered}",
                    details = mapOf("promoted" to stats.promoted, "filtered" to stats.filtered, "emailRejected" to stats.emailRejected, "filterReasons" to stats.filterReasons)
                ), execId)
                return PromotionScanResult(stats, wasCancelled = true)
            }

            progressStore.update(taskType, TaskProgress(
                taskType = taskType, status = "COMPLETED",
                batchNumber = -1, processedCount = stats.total.toLong(), totalCount = stats.total.toLong(),
                message = "完成: 晋升 ${stats.promoted}, 过滤 ${stats.filtered}",
                details = mapOf("promoted" to stats.promoted, "filtered" to stats.filtered, "emailRejected" to stats.emailRejected, "filterReasons" to stats.filterReasons)
            ), execId)
        } catch (e: Exception) {
            progressStore.update(taskType, TaskProgress(
                taskType = taskType, status = "FAILED",
                batchNumber = -1, processedCount = stats.total.toLong(), totalCount = 0,
                message = "失败: ${e.message}"
            ), execId)
            throw e
        }

        log.info("RAW promotion scan: total={}, promoted={}, filtered={}, emailRejected={}, existenceCheckFailed={}",
            stats.total, stats.promoted, stats.filtered, stats.emailRejected, stats.existenceCheckFailed)
        return PromotionScanResult(stats)
    }

    private fun promoteRawToCandidate(profile: com.weibo.talentintroduction.expert.domain.ExpertProfile): Boolean {
        val rawDoc = expertIndexWriterService.readRawDocument(profile.orcidId)
            ?: return false

        val now = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        val existingTags = (rawDoc["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val newTags = (existingTags + "auto_promoted").distinct()

        val doc = rawDoc.toMutableMap().apply {
            put("candidateValidatedAt", now)
            put("updatedAt", now)
            put("tags", newTags)
        }

        return expertIndexWriterService.writeCandidateDocument(profile.orcidId, doc)
    }
}
