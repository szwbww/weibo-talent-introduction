package com.weibo.talentintroduction.discovery.controller

import com.weibo.talentintroduction.config.EuropePmcProperties
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.service.ArxivDataSource
import com.weibo.talentintroduction.discovery.service.CoreDataSource
import com.weibo.talentintroduction.discovery.service.CrossrefDataSource
import com.weibo.talentintroduction.discovery.service.ExpertDiscoveryService
import com.weibo.talentintroduction.discovery.service.OpenAlexDataSource
import com.weibo.talentintroduction.discovery.service.OrcidDataSource
import com.weibo.talentintroduction.discovery.service.PmcOaDataSource
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(ExpertDiscoveryController::class)
@EnableConfigurationProperties(EuropePmcProperties::class)
class ExpertDiscoveryControllerMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var discoveryService: ExpertDiscoveryService

    @MockBean
    private lateinit var taskExecutionService: TaskExecutionService

    @MockBean
    private lateinit var progressStore: TaskProgressStore

    @MockBean
    private lateinit var openAlexProvider: ObjectProvider<OpenAlexDataSource>

    @MockBean
    private lateinit var crossrefProvider: ObjectProvider<CrossrefDataSource>

    @MockBean
    private lateinit var arxivProvider: ObjectProvider<ArxivDataSource>

    @MockBean
    private lateinit var pmcOaProvider: ObjectProvider<PmcOaDataSource>

    @MockBean
    private lateinit var orcidProvider: ObjectProvider<OrcidDataSource>

    @MockBean
    private lateinit var coreProvider: ObjectProvider<CoreDataSource>

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    private fun <T : Any> eqValue(value: T): T =
        Mockito.eq(value) ?: value

    @Test
    fun `triggerDiscovery returns JSON contract on success`() {
        val token = 5555L
        Mockito.`when`(progressStore.tryStartWithToken(eqValue("EXPERT_DISCOVERY"), anyValue(TaskProgress("", "", 0, 0, 0))))
            .thenReturn(Pair(true, token))

        val taskExecution = TaskExecution(
            id = 100L,
            taskType = "EXPERT_DISCOVERY",
            triggerType = "MANUAL",
            status = "SUCCESS",
            requestPayload = null,
            resultSummary = "{\"indexed\": 5}",
            startedAt = LocalDateTime.now()
        )

        Mockito.`when`(taskExecutionService.runAndRecord<Any>(
            eqValue("EXPERT_DISCOVERY"),
            eqValue("MANUAL"),
            anyValue(Any()),
            anyValue<(Long) -> Unit> { },
            anyValue { Any() }
        )).thenAnswer { invocation ->
            val onStarted = invocation.getArgument<((Long) -> Unit)?>(3)
            onStarted?.invoke(100L)
            taskExecution
        }

        mockMvc.perform(post("/api/expert-discovery/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executionId").value(100))
            .andExpect(jsonPath("$.result.taskType").value("EXPERT_DISCOVERY"))
            .andExpect(jsonPath("$.result.resultSummary").value("{\"indexed\": 5}"))
    }

    @Test
    fun `triggerDiscovery returns 409 when task is running`() {
        Mockito.`when`(progressStore.tryStartWithToken(eqValue("EXPERT_DISCOVERY"), anyValue(TaskProgress("", "", 0, 0, 0))))
            .thenReturn(Pair(false, -1L))

        mockMvc.perform(post("/api/expert-discovery/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("任务正在执行中，请等待完成"))
    }

    @Test
    fun `triggerDiscovery returns 500 when execution fails`() {
        val token = 5555L
        Mockito.`when`(progressStore.tryStartWithToken(eqValue("EXPERT_DISCOVERY"), anyValue(TaskProgress("", "", 0, 0, 0))))
            .thenReturn(Pair(true, token))

        val taskExecution = TaskExecution(
            id = 101L,
            taskType = "EXPERT_DISCOVERY",
            triggerType = "MANUAL",
            status = "FAILED",
            requestPayload = null,
            resultSummary = null,
            errorMessage = "Europe PMC is down",
            startedAt = LocalDateTime.now()
        )

        Mockito.`when`(taskExecutionService.runAndRecord<Any>(
            eqValue("EXPERT_DISCOVERY"),
            eqValue("MANUAL"),
            anyValue(Any()),
            anyValue<(Long) -> Unit> { },
            anyValue { Any() }
        )).thenAnswer { invocation ->
            val onStarted = invocation.getArgument<((Long) -> Unit)?>(3)
            onStarted?.invoke(101L)
            taskExecution
        }

        mockMvc.perform(post("/api/expert-discovery/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.message").value("Europe PMC is down"))
    }
}
