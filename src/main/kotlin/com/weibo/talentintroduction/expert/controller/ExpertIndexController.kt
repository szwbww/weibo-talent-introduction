package com.weibo.talentintroduction.expert.controller

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.PromotionScanResult
import com.weibo.talentintroduction.expert.domain.RevalidationResult
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertRevalidationService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
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
    private val revalidationService: ExpertRevalidationService,
    private val taskExecutionService: TaskExecutionService,
    private val progressStore: TaskProgressStore
) {
    @GetMapping
    fun listExperts(
        @RequestParam(defaultValue = "CANDIDATE") level: ExpertIndexLevel,
        @RequestParam(defaultValue = "50") size: Int
    ): ExpertListResponse {
        val result = expertSearchService.searchExperts(size, level)
        val experts = result.experts.map { expert ->
            val contact = expert.orcidId
                .takeIf { it.isNotBlank() }
                ?.let(expertContactRepository::findFirstByOrcidIdOrderByUpdatedAtDesc)
            ExpertIndexResponse.from(
                expert = expert,
                level = level,
                contactId = contact?.id,
                contactStatus = contact?.currentStatus,
                needsManualAttention = contact?.needsManualAttention ?: false,
                autoReplyEnabled = contact?.autoReplyEnabled ?: true
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
        if (!progressStore.tryStart("EXPERT_REVALIDATION", TaskProgress(
                taskType = "EXPERT_REVALIDATION", status = "RUNNING",
                batchNumber = 0, processedCount = 0, totalCount = 0, message = "初始化中..."
            ))) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "任务正在执行中，请等待完成"))
        }
        try {
            val (_, result) = taskExecutionService.runAndRecordWithResult(
                "EXPERT_REVALIDATION", "MANUAL", "revalidate-candidates"
            ) {
                revalidationService.revalidateCandidates()
            }
            return ResponseEntity.ok(result)
        } catch (ex: Exception) {
            progressStore.update("EXPERT_REVALIDATION", TaskProgress(
                taskType = "EXPERT_REVALIDATION", status = "FAILED",
                batchNumber = 0, processedCount = 0, totalCount = 0,
                message = ex.message ?: "初始化失败"
            ))
            throw ex
        }
    }

    @PostMapping("/promote-eligible-raw")
    fun promoteEligibleRaw(
        @RequestParam(defaultValue = "1000") maxPromotions: Int
    ): ResponseEntity<Any> {
        require(maxPromotions in 1..10000) { "maxPromotions must be between 1 and 10000" }
        if (!progressStore.tryStart("RAW_PROMOTION_SCAN", TaskProgress(
                taskType = "RAW_PROMOTION_SCAN", status = "RUNNING",
                batchNumber = 0, processedCount = 0, totalCount = 0, message = "初始化中..."
            ))) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "任务正在执行中，请等待完成"))
        }
        try {
            val (_, result) = taskExecutionService.runAndRecordWithResult(
                "RAW_PROMOTION_SCAN", "MANUAL", mapOf("maxPromotions" to maxPromotions)
            ) {
                revalidationService.promoteEligibleRawExperts(maxPromotions)
            }
            return ResponseEntity.ok(result)
        } catch (ex: Exception) {
            progressStore.update("RAW_PROMOTION_SCAN", TaskProgress(
                taskType = "RAW_PROMOTION_SCAN", status = "FAILED",
                batchNumber = 0, processedCount = 0, totalCount = 0,
                message = ex.message ?: "初始化失败"
            ))
            throw ex
        }
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
    val contactId: Long?,
    val contactStatus: String?,
    val needsManualAttention: Boolean = false,
    val autoReplyEnabled: Boolean = true
) {
    companion object {
        fun from(
            expert: ExpertProfile,
            level: ExpertIndexLevel,
            contactId: Long?,
            contactStatus: String?,
            needsManualAttention: Boolean = false,
            autoReplyEnabled: Boolean = true
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
                contactId = contactId,
                contactStatus = contactStatus,
                needsManualAttention = needsManualAttention,
                autoReplyEnabled = autoReplyEnabled
            )
    }
}
