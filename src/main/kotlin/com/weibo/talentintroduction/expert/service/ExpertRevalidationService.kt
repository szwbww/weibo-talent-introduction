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
    private val progressStore: TaskProgressStore
) {
    private val log = LoggerFactory.getLogger(ExpertRevalidationService::class.java)

    fun revalidateCandidates(): RevalidationResult {
        val stats = RevalidationStats()

        try {
            expertSearchService.scrollExperts(ExpertIndexLevel.CANDIDATE) { batch, batchNumber, totalHits ->
                val processedBefore = stats.total
                for (profile in batch) {
                    stats.total++

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
                    }
                }
                log.info("重新验证进度: 批次={}, 本批处理={}, 累计已完成={}/{}",
                    batchNumber, stats.total - processedBefore, stats.total, totalHits)
                progressStore.update("EXPERT_REVALIDATION", TaskProgress(
                    taskType = "EXPERT_REVALIDATION", status = "RUNNING",
                    batchNumber = batchNumber, processedCount = stats.total.toLong(), totalCount = totalHits,
                    message = "批次 $batchNumber: 已处理 ${stats.total}/$totalHits",
                    details = mapOf("passed" to stats.passed, "demoted" to stats.demoted)
                ))
                true
            }

            log.info("Revalidation complete: total={}, passed={}, demoted={}, demotionFailed={}",
                stats.total, stats.passed, stats.demoted, stats.demotionFailed)

            progressStore.update("EXPERT_REVALIDATION", TaskProgress(
                taskType = "EXPERT_REVALIDATION", status = "COMPLETED",
                batchNumber = -1, processedCount = stats.total.toLong(), totalCount = stats.total.toLong(),
                message = "完成: 通过 ${stats.passed}, 降级 ${stats.demoted}",
                details = mapOf("passed" to stats.passed, "demoted" to stats.demoted, "demotionFailed" to stats.demotionFailed)
            ))
        } catch (e: Exception) {
            progressStore.update("EXPERT_REVALIDATION", TaskProgress(
                taskType = "EXPERT_REVALIDATION", status = "FAILED",
                batchNumber = -1, processedCount = stats.total.toLong(), totalCount = 0,
                message = "失败: ${e.message}"
            ))
            throw e
        }

        return RevalidationResult(stats)
    }

    fun promoteEligibleRawExperts(maxPromotions: Int = 1000): PromotionScanResult {
        val stats = PromotionScanStats()
        if (maxPromotions <= 0) return PromotionScanResult(stats)

        try {
            expertSearchService.scrollExperts(ExpertIndexLevel.RAW) { batch, batchNumber, totalHits ->
                val processedBefore = stats.total
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

                    val emailResult = emailValidationService.validate(profile.email.orEmpty())
                    if (!emailResult.valid) {
                        stats.emailRejected++
                        continue
                    }

                    val success = promoteRawToCandidate(profile)
                    if (success) stats.promoted++ else stats.promotionFailed++
                }
                log.info("RAW晋升扫描进度: 批次={}, 本批处理={}, 累计已完成={}/{}, 已晋升={}",
                    batchNumber, stats.total - processedBefore, stats.total, totalHits, stats.promoted)
                progressStore.update("RAW_PROMOTION_SCAN", TaskProgress(
                    taskType = "RAW_PROMOTION_SCAN", status = "RUNNING",
                    batchNumber = batchNumber, processedCount = stats.total.toLong(), totalCount = totalHits,
                    message = "批次 $batchNumber: 已处理 ${stats.total}/$totalHits, 已晋升 ${stats.promoted}",
                    details = mapOf("promoted" to stats.promoted, "filtered" to stats.filtered)
                ))
                !limitReached && stats.promoted < maxPromotions
            }

            progressStore.update("RAW_PROMOTION_SCAN", TaskProgress(
                taskType = "RAW_PROMOTION_SCAN", status = "COMPLETED",
                batchNumber = -1, processedCount = stats.total.toLong(), totalCount = stats.total.toLong(),
                message = "完成: 晋升 ${stats.promoted}, 过滤 ${stats.filtered}"
            ))
        } catch (e: Exception) {
            progressStore.update("RAW_PROMOTION_SCAN", TaskProgress(
                taskType = "RAW_PROMOTION_SCAN", status = "FAILED",
                batchNumber = -1, processedCount = stats.total.toLong(), totalCount = 0,
                message = "失败: ${e.message}"
            ))
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

        val doc = rawDoc.toMutableMap().apply {
            put("candidateValidatedAt", now)
            put("updatedAt", now)
        }

        return expertIndexWriterService.writeCandidateDocument(profile.orcidId, doc)
    }
}
