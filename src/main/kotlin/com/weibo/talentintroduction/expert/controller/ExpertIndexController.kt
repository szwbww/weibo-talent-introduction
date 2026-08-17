package com.weibo.talentintroduction.expert.controller

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.OperatorStatusReconcileService
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.PromotionScanResult
import com.weibo.talentintroduction.expert.domain.RevalidationResult
import com.weibo.talentintroduction.expert.service.EligibilityFilterService
import com.weibo.talentintroduction.expert.service.EligibilityFiltersResponse
import com.weibo.talentintroduction.expert.service.CandidateOperatorStatusSyncService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertReachabilitySyncService
import com.weibo.talentintroduction.expert.service.ExpertRevalidationService
import com.weibo.talentintroduction.expert.service.EmailDomainCount
import com.weibo.talentintroduction.expert.service.RegionCount
import com.weibo.talentintroduction.expert.service.TagCount
import com.weibo.talentintroduction.expert.service.ExpertIdNormalizer
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.service.IntroductionMailComposer
import com.weibo.talentintroduction.mail.service.TemplateVariableItem
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import com.weibo.talentintroduction.task.domain.TaskLaunchResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/experts")
class ExpertIndexController(
    private val expertSearchService: ExpertSearchService,
    private val expertContactRepository: ExpertContactRepository,
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val candidateOperatorStatusSyncService: CandidateOperatorStatusSyncService,
    private val revalidationService: ExpertRevalidationService,
    private val taskExecutionService: TaskExecutionService,
    private val progressStore: TaskProgressStore,
    private val eligibilityFilterService: EligibilityFilterService,
    private val introductionMailComposer: IntroductionMailComposer,
    // 尾部可空默认参数（照 ManualExpertMailService.mailVariableService 先例）：
    // 生产由 Spring 注入 @Service bean；既有 ExpertIndexControllerTest 以 9 个位置参数直接构造，
    // 加默认值后无需改动该未授权测试文件。端点内 requireNotNull 兜底（生产必非空）。
    private val operatorStatusReconcileService: OperatorStatusReconcileService? = null,
    // 尾部可空默认参数（照 operatorStatusReconcileService 先例）：既有 ExpertIndexControllerTest
    // 以 9 个位置参数直接构造，新增依赖加默认值后无需改动该未授权测试文件。端点内 requireNotNull 兜底（生产必非空）。
    private val expertReachabilitySyncService: ExpertReachabilitySyncService? = null
) {
    @GetMapping
    fun listExperts(
        @RequestParam(defaultValue = "CANDIDATE") level: ExpertIndexLevel,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) tag: String?,
        @RequestParam(required = false) sortBy: String?,
        @RequestParam(defaultValue = "0") from: Int,
        @RequestParam(required = false) operatorStatus: String?,
        @RequestParam(required = false) emailDomain: String?,
        @RequestParam(required = false) region: String?,
        @RequestParam(required = false) hIndexMin: Int? = null,
        @RequestParam(required = false) citationCountMin: Int? = null,
        @RequestParam(required = false) recentYears: Int? = null,
        @RequestParam(required = false) hasField: List<String>? = null,
        @RequestParam(required = false) discipline: String? = null,
        @RequestParam(required = false) reachability: String? = null
    ): ExpertListResponse {
        val result = expertSearchService.searchExperts(
            size, level, tag, sortBy, from, operatorStatus, emailDomain, region,
            hIndexMin, citationCountMin, recentYears, hasField, discipline, reachability
        )
        val orcidIds = result.experts.map { it.orcidId }.filter { it.isNotBlank() }
        val contactMap = if (orcidIds.isEmpty()) emptyMap() else expertContactRepository
            .findByOrcidIdIn(orcidIds)
            .groupBy { ExpertIdNormalizer.normalize(it.orcidId) }
            .mapValues { (_, contacts) -> contacts.maxByOrNull { it.updatedAt ?: java.time.LocalDateTime.MIN } }

        val experts = result.experts.map { expert ->
            val contact = contactMap[ExpertIdNormalizer.normalize(expert.orcidId)]
            ExpertIndexResponse.from(
                expert = expert,
                level = level,
                contactId = contact?.id,
                contactStatus = contact?.currentStatus,
                needsManualAttention = contact?.needsManualAttention ?: false,
                autoReplyEnabled = contact?.autoReplyEnabled ?: true,
                boundSenderAccountCode = contact?.boundSenderAccountCode,
                senderAccountChanged = contact?.senderAccountChanged ?: false,
                operatorStatus = contact?.operatorStatus ?: expert.operatorStatus ?: "NOT_CONTACTED"
            )
        }
        return ExpertListResponse(experts = experts, totalHits = result.totalHits)
    }

    @PostMapping("/reindex-applications")
    fun reindexApplications(
        @RequestParam(defaultValue = "100") limit: Int
    ): ReindexResult {
        require(limit in 1..1000) { "limit must be between 1 and 1000" }
        val contacts = expertContactRepository
            .findAllByOrderByUpdatedAtDesc()
            .filter { !it.applicationIndexed && it.firstReplyAt != null }
            .take(limit)
        var success = 0
        var failure = 0
        contacts.forEach { contact ->
            val firstReply = contact.firstReplyAt ?: return@forEach
            val ok = expertIndexWriterService.promoteToApplication(
                orcid = contact.orcidId,
                contact = contact,
                firstReplyAt = firstReply.toInstant(java.time.ZoneId.systemDefault().rules.getOffset(firstReply))
            )
            if (ok) {
                expertContactRepository.save(contact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION"))
                success += 1
            } else {
                failure += 1
            }
        }
        return ReindexResult(total = contacts.size, success = success, failure = failure)
    }

    @PostMapping("/revalidate-candidates")
    fun revalidateCandidates(): ResponseEntity<Any> {
        val (started, token) = progressStore.tryStartWithToken("EXPERT_REVALIDATION", TaskProgress(
            taskType = "EXPERT_REVALIDATION", status = "RUNNING",
            batchNumber = 0, processedCount = 0, totalCount = 0, message = "初始化中..."
        ))
        if (!started) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "任务正在执行中，请等待完成"))
        }
        var executionId: Long? = null
        try {
            val (savedExecution, result) = taskExecutionService.runAndRecordWithResult(
                "EXPERT_REVALIDATION", "MANUAL", "revalidate-candidates",
                onStarted = { id ->
                    executionId = id
                    progressStore.bindExecutionId("EXPERT_REVALIDATION", token, id)
                }
            ) {
                revalidationService.revalidateCandidates()
            }
            return ResponseEntity.ok(TaskLaunchResponse(savedExecution.id!!, result))
        } catch (ex: Exception) {
            progressStore.update("EXPERT_REVALIDATION", TaskProgress(
                taskType = "EXPERT_REVALIDATION", status = "FAILED",
                batchNumber = 0, processedCount = 0, totalCount = 0,
                message = ex.message ?: "初始化失败"
            ), executionId)
            throw ex
        } finally {
            val execId = executionId
            if (execId != null) {
                progressStore.clearExecutionContext("EXPERT_REVALIDATION", execId)
            } else {
                progressStore.clearExecutionContext("EXPERT_REVALIDATION", token)
            }
        }
    }

    @PostMapping("/sync-reachability")
    fun syncReachability(): ResponseEntity<Any> {
        // I-3-4: 全量回填是万级文档量级，必须走 progressStore 长任务模式（并发保护 + 前端进度）。
        val (started, token) = progressStore.tryStartWithToken("EXPERT_REACHABILITY_SYNC", TaskProgress(
            taskType = "EXPERT_REACHABILITY_SYNC", status = "RUNNING",
            batchNumber = 0, processedCount = 0, totalCount = 0, message = "初始化中..."
        ))
        if (!started) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "任务正在执行中，请等待完成"))
        }
        var executionId: Long? = null
        try {
            val (savedExecution, result) = taskExecutionService.runAndRecordWithResult(
                "EXPERT_REACHABILITY_SYNC", "MANUAL", "sync-reachability",
                onStarted = { id ->
                    executionId = id
                    progressStore.bindExecutionId("EXPERT_REACHABILITY_SYNC", token, id)
                }
            ) {
                requireNotNull(expertReachabilitySyncService) { "ExpertReachabilitySyncService 未注入" }.syncAll()
            }
            return ResponseEntity.ok(TaskLaunchResponse(savedExecution.id!!, result))
        } catch (ex: IllegalStateException) {
            // I-3-6: mapping 断言失败 fail-fast，与 /backfill-operator-status 同款返回 400。
            progressStore.update("EXPERT_REACHABILITY_SYNC", TaskProgress(
                taskType = "EXPERT_REACHABILITY_SYNC", status = "FAILED",
                batchNumber = 0, processedCount = 0, totalCount = 0,
                message = ex.message ?: "初始化失败"
            ), executionId)
            return ResponseEntity.badRequest().body(mapOf("message" to (ex.message ?: "同步失败")))
        } catch (ex: Exception) {
            progressStore.update("EXPERT_REACHABILITY_SYNC", TaskProgress(
                taskType = "EXPERT_REACHABILITY_SYNC", status = "FAILED",
                batchNumber = 0, processedCount = 0, totalCount = 0,
                message = ex.message ?: "初始化失败"
            ), executionId)
            throw ex
        } finally {
            val execId = executionId
            if (execId != null) {
                progressStore.clearExecutionContext("EXPERT_REACHABILITY_SYNC", execId)
            } else {
                progressStore.clearExecutionContext("EXPERT_REACHABILITY_SYNC", token)
            }
        }
    }

    @PostMapping("/promote-eligible-raw")
    fun promoteEligibleRaw(): ResponseEntity<Any> {
        val (started, token) = progressStore.tryStartWithToken("RAW_PROMOTION_SCAN", TaskProgress(
            taskType = "RAW_PROMOTION_SCAN", status = "RUNNING",
            batchNumber = 0, processedCount = 0, totalCount = 0, message = "初始化中..."
        ))
        if (!started) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "任务正在执行中，请等待完成"))
        }
        var executionId: Long? = null
        try {
            val (savedExecution, result) = taskExecutionService.runAndRecordWithResult(
                "RAW_PROMOTION_SCAN", "MANUAL", "promote-eligible-raw",
                onStarted = { id ->
                    executionId = id
                    progressStore.bindExecutionId("RAW_PROMOTION_SCAN", token, id)
                }
            ) {
                revalidationService.promoteEligibleRawExperts()
            }
            return ResponseEntity.ok(TaskLaunchResponse(savedExecution.id!!, result))
        } catch (ex: Exception) {
            progressStore.update("RAW_PROMOTION_SCAN", TaskProgress(
                taskType = "RAW_PROMOTION_SCAN", status = "FAILED",
                batchNumber = 0, processedCount = 0, totalCount = 0,
                message = ex.message ?: "初始化失败"
            ), executionId)
            throw ex
        } finally {
            val execId = executionId
            if (execId != null) {
                progressStore.clearExecutionContext("RAW_PROMOTION_SCAN", execId)
            } else {
                progressStore.clearExecutionContext("RAW_PROMOTION_SCAN", token)
            }
        }
    }

    @PostMapping("/backfill-operator-status")
    fun backfillOperatorStatus(): ResponseEntity<Any> {
        return try {
            val (_, result) = taskExecutionService.runAndRecordWithResult(
                "CANDIDATE_OPERATOR_STATUS_SYNC",
                "MANUAL",
                "backfill-operator-status"
            ) {
                candidateOperatorStatusSyncService.reconcileAll()
            }
            ResponseEntity.ok(BackfillResult(
                total = result.total,
                success = result.success,
                failure = result.failure,
                skipped = result.skipped
            ))
        } catch (ex: IllegalStateException) {
            ResponseEntity.badRequest().body(mapOf("message" to (ex.message ?: "同步失败")))
        }
    }

    @PostMapping("/reconcile-operator-status")
    fun reconcileOperatorStatus(): ResponseEntity<Any> {
        return try {
            val (_, result) = taskExecutionService.runAndRecordWithResult(
                "OPERATOR_STATUS_RECONCILE",
                "MANUAL",
                "reconcile-operator-status"
            ) {
                requireNotNull(operatorStatusReconcileService) { "OperatorStatusReconcileService 未注入" }.reconcile()
            }
            ResponseEntity.ok(result)
        } catch (ex: IllegalStateException) {
            ResponseEntity.badRequest().body(mapOf("message" to (ex.message ?: "对账失败")))
        }
    }

    @GetMapping("/eligibility-filters")
    fun getEligibilityFilters(): ResponseEntity<EligibilityFiltersResponse> {
        return ResponseEntity.ok(eligibilityFilterService.getAll())
    }

    @PutMapping("/eligibility-filters")
    fun updateEligibilityFilters(@RequestBody updates: Map<String, String>): ResponseEntity<EligibilityFiltersResponse> {
        updates.forEach { (key, value) -> eligibilityFilterService.update(key, value) }
        return ResponseEntity.ok(eligibilityFilterService.getAll())
    }

    @GetMapping("/email-providers")
    fun getEmailProviders(
        @RequestParam(defaultValue = "CANDIDATE") level: ExpertIndexLevel,
        @RequestParam(required = false) tag: String? = null,
        @RequestParam(required = false) operatorStatus: String? = null,
        @RequestParam(required = false) region: String? = null
    ): List<EmailDomainCount> {
        return expertSearchService.aggregateEmailDomains(level, tag, operatorStatus, region)
    }

    @GetMapping("/regions")
    fun getRegions(
        @RequestParam(defaultValue = "CANDIDATE") level: ExpertIndexLevel,
        @RequestParam(required = false) tag: String? = null,
        @RequestParam(required = false) operatorStatus: String? = null,
        @RequestParam(required = false) emailDomain: String? = null
    ): List<RegionCount> {
        return expertSearchService.aggregateRegions(level, tag, operatorStatus, emailDomain)
    }

    @PostMapping("/tags/add")
    fun addTag(@RequestBody request: ExpertTagMutationRequest): TagMutationResult {
        require(request.orcidId.isNotBlank()) { "orcidId is required" }
        require(request.tag.isNotBlank()) { "tag is required" }
        val profile = expertSearchService.findByOrcidId(request.orcidId, request.level)
            ?: return TagMutationResult(success = false, message = "Expert not found: ${request.orcidId}")
        val docId = profile.esDocId ?: request.orcidId
        val ok = expertIndexWriterService.addTag(docId, request.tag.trim(), request.level)
        return TagMutationResult(
            success = ok,
            message = if (ok) null else "Failed to add tag"
        )
    }

    @PostMapping("/tags/remove")
    fun removeTag(@RequestBody request: ExpertTagMutationRequest): TagMutationResult {
        require(request.orcidId.isNotBlank()) { "orcidId is required" }
        require(request.tag.isNotBlank()) { "tag is required" }
        val profile = expertSearchService.findByOrcidId(request.orcidId, request.level)
            ?: return TagMutationResult(success = false, message = "Expert not found: ${request.orcidId}")
        val docId = profile.esDocId ?: request.orcidId
        val ok = expertIndexWriterService.removeTag(docId, request.tag.trim(), request.level)
        return TagMutationResult(
            success = ok,
            message = if (ok) null else "Failed to remove tag"
        )
    }

    @GetMapping("/tags/aggregation")
    fun aggregateTags(
        @RequestParam(defaultValue = "CANDIDATE") level: ExpertIndexLevel,
        @RequestParam(required = false) operatorStatus: String? = null,
        @RequestParam(required = false) emailDomain: String? = null,
        @RequestParam(required = false) region: String? = null
    ): List<TagCount> {
        return expertSearchService.aggregateTags(level, operatorStatus, emailDomain, region)
    }

    @GetMapping("/template-variables")
    fun getTemplateVariables(
        @RequestParam orcidId: String,
        @RequestParam(defaultValue = "CANDIDATE") level: ExpertIndexLevel,
        @RequestParam(required = false) accountCode: String?
    ): List<TemplateVariableItem> {
        require(orcidId.isNotBlank()) { "orcidId is required" }
        val expert = expertSearchService.findByOrcidId(orcidId, level)
            ?: throw org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Expert not found: $orcidId"
            )
        return introductionMailComposer.buildTemplateVariables(expert, accountCode)
    }

    @GetMapping("/profile")
    fun getExpertProfile(
        @RequestParam orcidId: String,
        @RequestParam(defaultValue = "CANDIDATE") level: ExpertIndexLevel
    ): ExpertProfileTagsResponse {
        require(orcidId.isNotBlank()) { "orcidId is required" }
        val profile = expertSearchService.findByOrcidId(orcidId, level)
        return ExpertProfileTagsResponse(
            orcidId = orcidId,
            found = profile != null,
            tags = profile?.tags.orEmpty()
        )
    }
}

data class ExpertListResponse(
    val experts: List<ExpertIndexResponse>,
    val totalHits: Long
)

data class ReindexResult(
    val total: Int,
    val success: Int,
    val failure: Int
)

data class BackfillResult(
    val total: Int,
    val success: Int,
    val failure: Int,
    val skipped: Int
)

data class ExpertTagMutationRequest(
    val orcidId: String,
    val tag: String,
    val level: ExpertIndexLevel = ExpertIndexLevel.CANDIDATE
)

data class TagMutationResult(
    val success: Boolean,
    val message: String? = null
)

data class ExpertProfileTagsResponse(
    val orcidId: String,
    val found: Boolean,
    val tags: List<String> = emptyList()
)

data class ExpertIndexResponse(
    val indexLevel: String,
    val indexLevelName: String,
    val orcidId: String,
    val email: String?,
    val displayName: String,
    val country: String?,
    val keyword: String?,
    val employment: String?,
    val age: Int?,
    val degree: String?,
    val nationality: String?,
    val operatorStatus: String?,
    val contactId: Long?,
    val contactStatus: String?,
    val needsManualAttention: Boolean = false,
    val autoReplyEnabled: Boolean = true,
    val boundSenderAccountCode: String? = null,
    val senderAccountChanged: Boolean = false,
    val tags: List<String> = emptyList(),
    val updatedAt: String? = null,
    val hIndex: Int? = null,
    val citationCount: Int? = null,
    val lastPublicationYear: Int? = null,
    val researchFields: String? = null,
    val disciplineCategory: String? = null,
    val institution: String? = null,
    val worksCount: Int? = null,
    val enrichedAt: String? = null,
    val reachability: String? = null
) {
    companion object {
        fun from(
            expert: ExpertProfile,
            level: ExpertIndexLevel,
            contactId: Long?,
            contactStatus: String?,
            needsManualAttention: Boolean = false,
            autoReplyEnabled: Boolean = true,
            boundSenderAccountCode: String? = null,
            senderAccountChanged: Boolean = false,
            operatorStatus: String? = null
        ): ExpertIndexResponse =
            ExpertIndexResponse(
                indexLevel = level.name,
                indexLevelName = when (level) {
                    ExpertIndexLevel.RAW -> "原始"
                    ExpertIndexLevel.CANDIDATE -> "筛选"
                    ExpertIndexLevel.APPLICATION -> "有效"
                },
                orcidId = expert.orcidId,
                email = expert.email,
                displayName = expert.displayName,
                country = expert.country,
                keyword = expert.keyword,
                employment = expert.employment,
                age = expert.age,
                degree = expert.degree,
                nationality = expert.nationality,
                operatorStatus = operatorStatus ?: expert.operatorStatus ?: "NOT_CONTACTED",
                contactId = contactId,
                contactStatus = contactStatus,
                needsManualAttention = needsManualAttention,
                autoReplyEnabled = autoReplyEnabled,
                boundSenderAccountCode = boundSenderAccountCode,
                senderAccountChanged = senderAccountChanged,
                tags = expert.tags.orEmpty(),
                updatedAt = expert.updatedAt,
                hIndex = expert.hIndex,
                citationCount = expert.citationCount,
                lastPublicationYear = expert.lastPublicationYear,
                researchFields = expert.researchFields,
                disciplineCategory = expert.disciplineCategory,
                institution = expert.institution,
                worksCount = expert.worksCount,
                enrichedAt = expert.enrichedAt,
                reachability = expert.reachability
            )
    }
}
