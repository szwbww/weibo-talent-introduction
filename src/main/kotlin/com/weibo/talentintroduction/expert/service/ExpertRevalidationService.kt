package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.config.ExpertClassificationProperties
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewDecision
import com.weibo.talentintroduction.discovery.service.DiscoveryAdmissionPolicy
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewService
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.EligibilityResult
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
    private val expertClassificationProperties: ExpertClassificationProperties = ExpertClassificationProperties(),
    /**
     * 04（I-3）：准入结论的唯一读取/写入接缝。为空时（历史单测）按改前的纯自动路径运行；
     * 生产恒由 Spring 注入，发现重验先读当前结论再决定自动校验或保留。
     */
    private val discoveryReviewService: DiscoveryReviewService? = null,
    /**
     * R-5（V-30）：当前准入 policy 的唯一结论来源。为空时（历史单测）保持改前行为；
     * 生产恒由 Spring 注入 —— 复用同身份旧自动准入之前必须先形成**当前** policy 结论。
     */
    private val admissionPolicy: com.weibo.talentintroduction.discovery.service.DiscoveryAdmissionPolicy? = null,
    private val verificationGate: com.weibo.talentintroduction.discovery.service.DiscoveryVerificationAdmissionGate? = null
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
                    if (DiscoveryIdentity.isDiscovery(profile)) {
                        when (val outcome = revalidateDiscovery(profile.esDocId ?: profile.orcidId)) {
                            PromotionOutcome.AlreadyPresent -> stats.passed++
                            PromotionOutcome.Promoted -> stats.passed++
                            is PromotionOutcome.Rejected -> {
                                stats.demoted++
                                outcome.reasons.forEach { stats.demotionReasons.merge(it, 1) { a, b -> a + b } }
                            }
                            else -> stats.demotionFailed++
                        }
                        continue
                    }

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
                    if (DiscoveryIdentity.isDiscovery(profile)) {
                        when (val outcome = revalidateDiscovery(profile.esDocId ?: profile.orcidId)) {
                            PromotionOutcome.Promoted -> stats.promoted++
                            PromotionOutcome.AlreadyPresent -> stats.alreadyPromoted++
                            is PromotionOutcome.Rejected -> {
                                stats.filtered++
                                outcome.reasons.forEach { stats.filterReasons.merge(it, 1) { a, b -> a + b } }
                            }
                            else -> stats.promotionFailed++
                        }
                        continue
                    }

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
     * Historical non-discovery RAW gate. Discovery always uses revalidateDiscovery and its
     * current professional criterion, irrespective of promotionGateEnabled.
     */
    private fun evaluateRawPromotionGate(profile: ExpertProfile): RawPromotionGate {
        val eligibility = eligibilityService.evaluateEligibility(profile)
        if (!eligibility.eligible) {
            return RawPromotionGate.Rejected(eligibility.rejectReasons)
        }

        // I3-3: 现算，不读 profile.expertClassification（RAW 层几乎恒为 null）。
        val classification = expertClassificationService.classify(profile)
        // Historical non-discovery gate only; discovery UNKNOWN must not pass.
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

    /** Re-read the real RAW _id, recompute admission, and persist qualification before success. */
    fun revalidateDiscovery(docId: String): PromotionOutcome {
        val applied = try {
            expertIndexWriterService.documentExistsInIndex(ExpertIndexLevel.APPLICATION, docId)
        } catch (e: Exception) {
            return PromotionOutcome.ExistenceCheckFailed
        }
        return try {
            val snapshot = expertIndexWriterService.readDiscoveryDocument(ExpertIndexLevel.RAW, docId)
                ?: return PromotionOutcome.RawMissing
            val profile = expertIndexWriterService.discoveryProfile(docId, snapshot.source)

            // I-3：先读当前准入结论。有效人工批准（含历史认可）跳过基础重新拒绝，且不按基础失败删候选；
            // 人工暂缓/拒绝不自动晋升。身份变化（decision == null）按无有效批准处理。
            val review = discoveryReviewService
            val admission = review?.resolveAdmission(docId, profile)
            if (review != null && admission != null && admission.manual) {
                val decision = admission.decision
                if (decision == DiscoveryReviewDecision.HOLD.name || decision == DiscoveryReviewDecision.REJECTED.name) {
                    return PromotionOutcome.AlreadyPresent
                }
                return projectAdmission(review, docId, snapshot.source, decision!!)
            }

            if (!DiscoveryIdentity.allowedMap(snapshot.source))
                return PromotionOutcome.WriteFailed
            // I-1/I-3：只有硬否决与独立邮箱验证失败参与候选投影；六类缺资料原因不再撤候选。
            val eligibility = eligibilityService.evaluateEligibility(profile)
            val context = verificationGate?.findContext(docId, profile)
            val currentPolicy = if (context != null) admissionPolicy?.evaluate(docId, profile, eligibility, context)
                else admissionPolicy?.evaluate(docId, profile, eligibility)
            if (context?.managed == true) {
                val automatic = requireNotNull(currentPolicy) { "Managed verification requires admission policy" }
                val independentEmailReasons = hardBlockingReasons(profile, eligibility).filter { it.startsWith("EMAIL:") }
                val managedReasons = automatic.blockingReasons.map { it.code } + independentEmailReasons
                val managedAutomatic = if (independentEmailReasons.isEmpty()) automatic else automatic.copy(
                    status = com.weibo.talentintroduction.discovery.domain.DiscoveryAdmissionStatus.NEEDS_REVIEW,
                    blockingReasons = automatic.blockingReasons + independentEmailReasons.map {
                        com.weibo.talentintroduction.discovery.domain.AdmissionReason(
                            it, it, "email", profile.email, "有效邮箱", "EmailValidationService.validate()")
                    }
                )
                val current = requireNotNull(review) { "Managed verification requires admission persistence" }
                    .recordAutomaticConclusion(docId, profile, managedAutomatic)
                if (current.manual) {
                    return if (current.decision in setOf(DiscoveryReviewDecision.HOLD.name, DiscoveryReviewDecision.REJECTED.name))
                        PromotionOutcome.AlreadyPresent else projectAdmission(review, docId, snapshot.source, current.decision!!)
                }
                if (current.decision != managedAutomatic.status.name || current.revision <= 0)
                    return PromotionOutcome.WriteFailed
                if (managedReasons.isEmpty()) {
                    if (!current.admitted)
                        return PromotionOutcome.WriteFailed
                    return projectAdmission(review, docId, snapshot.source, current.decision!!)
                }
                if (!expertIndexWriterService.reconcileDiscoveryCandidate(
                        docId, snapshot, expertClassificationService.classify(profile), managedReasons, preserveApplication = applied
                    )) return PromotionOutcome.WriteFailed
                return if (applied) PromotionOutcome.AlreadyPresent else PromotionOutcome.Rejected(managedReasons)
            }
            val reasons = hardBlockingReasons(profile, eligibility)
            // Nonmanaged/AUDIT retains the historical reconciliation path.
            if (reasons.isEmpty() && currentPolicy != null && currentPolicy.blockingReasons.isNotEmpty()) {
                review?.recordAutomatic(docId, profile, eligibility)
                val policyCodes = currentPolicy.blockingReasons.map { it.code }
                val candidateBefore = if (applied) null
                    else expertIndexWriterService.readDiscoveryDocument(ExpertIndexLevel.CANDIDATE, docId)
                val classification = expertClassificationService.classify(profile)
                if (!expertIndexWriterService.reconcileDiscoveryCandidate(
                        docId, snapshot, classification, policyCodes, preserveApplication = applied
                    )) return PromotionOutcome.WriteFailed
                return if (applied || candidateBefore != null) PromotionOutcome.AlreadyPresent
                else PromotionOutcome.Rejected(policyCodes)
            }
            if (reasons.isEmpty() && review != null && admission != null && admission.admitted &&
                admission.decision != null
            ) {
                // 有效自动准入：复用同一条共享候选投影，缺资料不再回撤（不再走 reconcile 删除候选）。
                return projectAdmission(review, docId, snapshot.source, admission.decision)
            }
            val candidateBefore = if (applied) null
                else expertIndexWriterService.readDiscoveryDocument(ExpertIndexLevel.CANDIDATE, docId)
            val classification = expertClassificationService.classify(profile)
            if (!expertIndexWriterService.reconcileDiscoveryCandidate(
                    docId, snapshot, classification, reasons, preserveApplication = applied
                )) return PromotionOutcome.WriteFailed
            // I-1：补全/重验后刷新自动结论（同身份人工决定不被覆盖；身份变化生成新自动结论）。
            discoveryReviewService?.recordAutomatic(docId, profile, eligibility)
            if (applied) PromotionOutcome.AlreadyPresent
            else if (reasons.isNotEmpty()) PromotionOutcome.Rejected(reasons)
            else if (candidateBefore != null) PromotionOutcome.AlreadyPresent
            else PromotionOutcome.Promoted
        } catch (e: Exception) {
            log.warn("Discovery revalidation failed for {}: {}", docId, e.message)
            PromotionOutcome.WriteFailed
        }
    }

    /**
     * I-1：候选投影用的阻断原因 —— 六类缺资料原因（[DiscoveryAdmissionPolicy.SOFT_REASON_CODES]）
     * 之外的一切资格原因，加上独立邮箱验证失败（含 `EMAIL:` 词汇，与既有流程逐字一致）。
     */
    private fun hardBlockingReasons(profile: ExpertProfile, eligibility: EligibilityResult): List<String> {
        val reasons = eligibility.rejectReasons.filter { it !in DiscoveryAdmissionPolicy.SOFT_REASON_CODES }
        if (!eligibilityFilterService.getCandidateFilter().requireValidEmail) return reasons
        val emailResult = emailValidationService.validate(profile.email.orEmpty())
        return if (emailResult.valid) reasons else reasons + "EMAIL:${emailResult.rejectReason}"
    }

    /** I-3：人工批准与有效自动准入共用的候选投影（复用同一 seam，不另写晋升/删除逻辑）。 */
    private fun projectAdmission(
        review: DiscoveryReviewService,
        docId: String,
        source: Map<String, Any?>,
        decision: String
    ): PromotionOutcome {
        val sync = review.projectApprovedCandidate(docId, source, decision)
        return when (sync.result) {
            ExpertIndexWriterService.DiscoveryCandidateProjection.PROJECTED.name -> PromotionOutcome.Promoted
            ExpertIndexWriterService.DiscoveryCandidateProjection.ALREADY_PRESENT.name,
            ExpertIndexWriterService.DiscoveryCandidateProjection.APPLICATION_PRESENT.name ->
                PromotionOutcome.AlreadyPresent
            else -> PromotionOutcome.WriteFailed
        }
    }

    /**
     * Single-RAW revalidation: discovery follows the CAS qualification/replica path, while
     * non-discovery retains the historical promotion behavior and outcome vocabulary.
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
        val discoveryRaw = try {
            expertIndexWriterService.readDiscoveryDocument(ExpertIndexLevel.RAW, docId)
        } catch (e: Exception) {
            return PromotionOutcome.ExistenceCheckFailed
        }
        if (discoveryRaw != null && DiscoveryIdentity.isDiscoveryMap(discoveryRaw.source))
            return revalidateDiscovery(docId)

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
