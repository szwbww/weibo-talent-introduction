package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.PromotionScanResult
import com.weibo.talentintroduction.expert.domain.PromotionScanStats
import com.weibo.talentintroduction.expert.domain.RevalidationResult
import com.weibo.talentintroduction.expert.domain.RevalidationStats
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ExpertRevalidationService(
    private val expertSearchService: ExpertSearchService,
    private val eligibilityService: CandidateEligibilityService,
    private val emailValidationService: EmailValidationService,
    private val expertIndexWriterService: ExpertIndexWriterService
) {
    private val log = LoggerFactory.getLogger(ExpertRevalidationService::class.java)

    fun revalidateCandidates(): RevalidationResult {
        val stats = RevalidationStats()

        expertSearchService.scrollExperts(ExpertIndexLevel.CANDIDATE) { batch ->
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
            true
        }

        log.info("Revalidation complete: total={}, passed={}, demoted={}, demotionFailed={}",
            stats.total, stats.passed, stats.demoted, stats.demotionFailed)
        return RevalidationResult(stats)
    }

    fun promoteEligibleRawExperts(maxPromotions: Int = 1000): PromotionScanResult {
        val stats = PromotionScanStats()
        if (maxPromotions <= 0) return PromotionScanResult(stats)

        expertSearchService.scrollExperts(ExpertIndexLevel.RAW) { batch ->
            for (profile in batch) {
                if (stats.promoted >= maxPromotions) {
                    return@scrollExperts false
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
            stats.promoted < maxPromotions
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
