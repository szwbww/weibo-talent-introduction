package com.weibo.talentintroduction.expert.controller

import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertRevalidationService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.EligibilityFilterService
import com.weibo.talentintroduction.expert.service.EligibilityFiltersResponse
import com.weibo.talentintroduction.expert.service.CandidateFilterView
import com.weibo.talentintroduction.expert.service.AcademicFilterView
import com.weibo.talentintroduction.expert.service.EmailValidationView
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.domain.PromotionScanResult
import com.weibo.talentintroduction.expert.domain.PromotionScanStats
import com.weibo.talentintroduction.expert.domain.RevalidationResult
import com.weibo.talentintroduction.expert.domain.RevalidationStats
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.http.MediaType
import java.time.LocalDateTime

@WebMvcTest(ExpertIndexController::class)
class ExpertIndexControllerMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var expertSearchService: ExpertSearchService

    @MockBean
    private lateinit var expertContactRepository: ExpertContactRepository

    @MockBean
    private lateinit var expertIndexWriterService: ExpertIndexWriterService

    @MockBean
    private lateinit var revalidationService: ExpertRevalidationService

    @MockBean
    private lateinit var taskExecutionService: TaskExecutionService

    @MockBean
    private lateinit var progressStore: TaskProgressStore

    @MockBean
    private lateinit var expertIndexService: ExpertIndexService

    @MockBean
    private lateinit var eligibilityFilterService: EligibilityFilterService

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    private fun <T : Any> eqValue(value: T): T =
        Mockito.eq(value) ?: value

    @Test
    fun `revalidateCandidates returns JSON contract on success`() {
        val token = 12345L
        Mockito.`when`(progressStore.tryStartWithToken(eqValue("EXPERT_REVALIDATION"), anyValue(TaskProgress("", "", 0, 0, 0))))
            .thenReturn(Pair(true, token))

        val taskExecution = TaskExecution(
            id = 42L,
            taskType = "EXPERT_REVALIDATION",
            triggerType = "MANUAL",
            status = "SUCCESS",
            requestPayload = null,
            resultSummary = null,
            startedAt = LocalDateTime.now()
        )
        val resultData = RevalidationResult(
            stats = RevalidationStats(
                total = 10,
                passed = 8,
                demoted = 2,
                demotionFailed = 0
            ),
            wasCancelled = false
        )

        Mockito.`when`(taskExecutionService.runAndRecordWithResult<Any>(
            eqValue("EXPERT_REVALIDATION"),
            eqValue("MANUAL"),
            eqValue("revalidate-candidates"),
            anyValue<(Long) -> Unit> { },
            anyValue { Any() }
        )).thenAnswer { invocation ->
            val onStarted = invocation.getArgument<((Long) -> Unit)?>(3)
            onStarted?.invoke(42L)
            Pair(taskExecution, resultData)
        }

        mockMvc.perform(post("/api/experts/revalidate-candidates"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executionId").value(42))
            .andExpect(jsonPath("$.result.stats.total").value(10))
            .andExpect(jsonPath("$.result.wasCancelled").value(false))
    }

    @Test
    fun `promoteEligibleRaw returns JSON contract on success`() {
        val token = 12345L
        Mockito.`when`(progressStore.tryStartWithToken(eqValue("RAW_PROMOTION_SCAN"), anyValue(TaskProgress("", "", 0, 0, 0))))
            .thenReturn(Pair(true, token))

        val taskExecution = TaskExecution(
            id = 43L,
            taskType = "RAW_PROMOTION_SCAN",
            triggerType = "MANUAL",
            status = "SUCCESS",
            requestPayload = null,
            resultSummary = null,
            startedAt = LocalDateTime.now()
        )
        val stats = PromotionScanStats(total = 5, promoted = 3, promotionFailed = 0, existenceCheckFailed = 0)
        val scanResult = PromotionScanResult(stats = stats)

        Mockito.`when`(taskExecutionService.runAndRecordWithResult<Any>(
            eqValue("RAW_PROMOTION_SCAN"),
            eqValue("MANUAL"),
            anyValue(Any()),
            anyValue<(Long) -> Unit> { },
            anyValue { Any() }
        )).thenAnswer { invocation ->
            val onStarted = invocation.getArgument<((Long) -> Unit)?>(3)
            onStarted?.invoke(43L)
            Pair(taskExecution, scanResult)
        }

        mockMvc.perform(post("/api/experts/promote-eligible-raw").param("maxPromotions", "100"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executionId").value(43))
            .andExpect(jsonPath("$.result.stats.promoted").value(3))
    }

    @Test
    fun `revalidateCandidates returns 409 and error message when running`() {
        Mockito.`when`(progressStore.tryStartWithToken(eqValue("EXPERT_REVALIDATION"), anyValue(TaskProgress("", "", 0, 0, 0))))
            .thenReturn(Pair(false, -1L))

        mockMvc.perform(post("/api/experts/revalidate-candidates"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("任务正在执行中，请等待完成"))
    }

    @Test
    fun `getEligibilityFilters returns current settings`() {
        val response = EligibilityFiltersResponse(
            candidateFilter = CandidateFilterView(false, true, true, false, 70),
            academicFilter = AcademicFilterView(false, 5, false, 50, false, 5),
            emailValidation = EmailValidationView(true)
        )
        Mockito.`when`(eligibilityFilterService.getAll()).thenReturn(response)

        mockMvc.perform(get("/api/experts/eligibility-filters"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.candidateFilter.requireValidEmail").value(true))
            .andExpect(jsonPath("$.candidateFilter.requireDoctoralDegree").value(false))
            .andExpect(jsonPath("$.candidateFilter.excludeChineseNationality").value(true))
            .andExpect(jsonPath("$.candidateFilter.enableAgeFilter").value(false))
            .andExpect(jsonPath("$.candidateFilter.maxAgeExclusive").value(70))
            .andExpect(jsonPath("$.academicFilter.enableHIndexFilter").value(false))
            .andExpect(jsonPath("$.academicFilter.minHIndex").value(5))
            .andExpect(jsonPath("$.emailValidation.enableMxCheck").value(true))
    }

    @Test
    fun `updateEligibilityFilters persists and returns updated settings`() {
        val updates = """{"candidate.requireValidEmail":"false","academic.minHIndex":"10"}"""
        val response = EligibilityFiltersResponse(
            candidateFilter = CandidateFilterView(false, false, true, false, 70),
            academicFilter = AcademicFilterView(false, 10, false, 50, false, 5),
            emailValidation = EmailValidationView(true)
        )
        Mockito.`when`(eligibilityFilterService.getAll()).thenReturn(response)
        Mockito.`when`(eligibilityFilterService.update("candidate.requireValidEmail", "false"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EligibilityFilterSetting(
                settingKey = "candidate.requireValidEmail", settingValue = "false"
            ))
        Mockito.`when`(eligibilityFilterService.update("academic.minHIndex", "10"))
            .thenReturn(com.weibo.talentintroduction.expert.domain.EligibilityFilterSetting(
                settingKey = "academic.minHIndex", settingValue = "10"
            ))

        mockMvc.perform(put("/api/experts/eligibility-filters")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updates))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.candidateFilter.requireValidEmail").value(false))
            .andExpect(jsonPath("$.academicFilter.minHIndex").value(10))
    }
}
