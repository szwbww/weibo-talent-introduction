package com.weibo.talentintroduction.expert.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertType
import com.weibo.talentintroduction.expert.service.BackfillMode
import com.weibo.talentintroduction.expert.service.ExpertClassificationBackfillRequest
import com.weibo.talentintroduction.expert.service.ExpertClassificationBackfillResult
import com.weibo.talentintroduction.expert.service.ExpertClassificationBackfillService
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

@WebMvcTest(ExpertClassificationAdminController::class)
@Import(AuthWebConfig::class, ObjectMapper::class)
@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])
class ExpertClassificationAdminControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var authService: AuthService

    @MockBean
    private lateinit var backfillService: ExpertClassificationBackfillService

    @MockBean
    private lateinit var taskExecutionService: TaskExecutionService

    @MockBean
    private lateinit var progressStore: TaskProgressStore

    @MockBean(name = "expertClassificationExecutor")
    private lateinit var executor: Executor

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    private fun <T : Any> eqValue(value: T): T =
        Mockito.eq(value) ?: value

    private fun authorizedSession(): MockHttpSession {
        Mockito.`when`(authService.findUser("admin")).thenReturn(adminUser())
        return MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, "admin") }
    }

    private fun adminUser(): AdminUser =
        AdminUser(
            id = 1L,
            username = "admin",
            passwordHash = "hash",
            mustChangePassword = false,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

    private fun validDryRunBody() =
        """{"level":"CANDIDATE","mode":"DRY_RUN","version":"rnd-v1-2026","batchSize":500,"delayMs":250}"""

    private fun validDryRunRequest() = ExpertClassificationBackfillRequest(
        level = ExpertIndexLevel.CANDIDATE,
        mode = BackfillMode.DRY_RUN,
        version = "rnd-v1-2026",
        batchSize = 500,
        delayMs = 250
    )

    private fun executeBody(confirmation: String? = "EXECUTE_CANDIDATE:rnd-v1-2026") =
        """{"level":"CANDIDATE","mode":"EXECUTE","version":"rnd-v1-2026","batchSize":500,"delayMs":250,"confirmation":${if (confirmation == null) "null" else "\"$confirmation\""}}"""

    private fun result() = ExpertClassificationBackfillResult(
        level = ExpertIndexLevel.CANDIDATE,
        mode = BackfillMode.DRY_RUN,
        policyVersion = "rnd-v1-2026",
        scanned = 3,
        classifiedByType = ExpertType.values().associate { it.name to 0L } + mapOf(ExpertType.PRODUCTION_RND.name to 1L),
        sendable = 1,
        notSendable = 2,
        writeSuccess = 0,
        writeNoop = 0,
        writeFailure = 0,
        skippedMissingDocId = 0,
        reasonCounts = emptyMap(),
        wasCancelled = false
    )

    private fun taskExecution() = TaskExecution(
        id = 100L,
        taskType = ExpertClassificationBackfillService.TASK_TYPE,
        triggerType = "MANUAL",
        status = "RUNNING",
        requestPayload = "{}",
        resultSummary = null,
        startedAt = LocalDateTime.now(),
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    private fun stubRunAndRecordInline(blockResult: ExpertClassificationBackfillResult) {
        Mockito.`when`(
            taskExecutionService.runAndRecordWithResult<ExpertClassificationBackfillResult>(
                eqValue(ExpertClassificationBackfillService.TASK_TYPE),
                eqValue("MANUAL"),
                anyValue(Any()),
                anyValue<(Long) -> Unit> { },
                Mockito.isNull(),
                anyValue { blockResult }
            )
        ).thenAnswer { invocation ->
            val onStarted = invocation.getArgument<((Long) -> Unit)?>(3)
            onStarted?.invoke(100L)
            val block = invocation.getArgument<() -> ExpertClassificationBackfillResult>(5)
            Pair(taskExecution(), block())
        }
    }

    @Test
    fun `unauthenticated POST returns 401 UNAUTHORIZED (I2-5)`() {
        mockMvc.perform(
            post("/api/expert-classification/backfill")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validDryRunBody())
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("未登录"))
    }

    @Test
    fun `valid DRY_RUN returns 202 with taskType and does not wait for the task (I2-5)`() {
        Mockito.`when`(progressStore.tryStartWithToken(eqValue(ExpertClassificationBackfillService.TASK_TYPE), anyValue(TaskProgress("", "", 0, 0, 0))))
            .thenReturn(Pair(true, -12345L))
        // executor 不执行任务：202 必须立即返回，不等待任务结束
        Mockito.doNothing().`when`(executor).execute(anyValue(Runnable { }))

        mockMvc.perform(
            post("/api/expert-classification/backfill")
                .session(authorizedSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validDryRunBody())
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.message").value("任务已启动"))
            .andExpect(jsonPath("$.taskType").value("EXPERT_CLASSIFICATION_BACKFILL"))

        Mockito.verify(taskExecutionService, Mockito.never())
            .runAndRecordWithResult<ExpertClassificationBackfillResult>(anyString(), anyString(), anyValue(Any()), anyValue<(Long) -> Unit> { }, Mockito.isNull(), anyValue { result() })
    }

    @Test
    fun `launch binds pending token to real executionId and clears with it (I2-5)`() {
        Mockito.`when`(progressStore.tryStartWithToken(eqValue(ExpertClassificationBackfillService.TASK_TYPE), anyValue(TaskProgress("", "", 0, 0, 0))))
            .thenReturn(Pair(true, -12345L))
        Mockito.doAnswer { invocation ->
            invocation.getArgument<Runnable>(0).run()
            null
        }.`when`(executor).execute(anyValue(Runnable { }))
        stubRunAndRecordInline(result())
        Mockito.`when`(backfillService.run(anyValue(validDryRunRequest()), anyLong())).thenReturn(result())

        mockMvc.perform(
            post("/api/expert-classification/backfill")
                .session(authorizedSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validDryRunBody())
        )
            .andExpect(status().isAccepted)

        Mockito.verify(progressStore).bindExecutionId(eqValue(ExpertClassificationBackfillService.TASK_TYPE), eqValue(-12345L), eqValue(100L))
        Mockito.verify(progressStore).clearExecutionContext(eqValue(ExpertClassificationBackfillService.TASK_TYPE), eqValue(100L))
        Mockito.verify(backfillService).run(anyValue(validDryRunRequest()), eqValue(100L))
    }

    @Test
    fun `second concurrent launch returns 409 (I2-5)`() {
        Mockito.`when`(progressStore.tryStartWithToken(eqValue(ExpertClassificationBackfillService.TASK_TYPE), anyValue(TaskProgress("", "", 0, 0, 0))))
            .thenReturn(Pair(false, -1L))

        mockMvc.perform(
            post("/api/expert-classification/backfill")
                .session(authorizedSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validDryRunBody())
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("已有分类回填任务运行中"))
            .andExpect(jsonPath("$.taskType").value("EXPERT_CLASSIFICATION_BACKFILL"))
    }

    @Test
    fun `executor rejection returns 409 and clears pending context (I2-5)`() {
        Mockito.`when`(progressStore.tryStartWithToken(eqValue(ExpertClassificationBackfillService.TASK_TYPE), anyValue(TaskProgress("", "", 0, 0, 0))))
            .thenReturn(Pair(true, -12345L))
        Mockito.doThrow(RejectedExecutionException("executor busy")).`when`(executor).execute(anyValue(Runnable { }))

        mockMvc.perform(
            post("/api/expert-classification/backfill")
                .session(authorizedSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validDryRunBody())
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.taskType").value("EXPERT_CLASSIFICATION_BACKFILL"))

        Mockito.verify(progressStore).update(
            eqValue(ExpertClassificationBackfillService.TASK_TYPE),
            anyValue(TaskProgress("", "", 0, 0, 0)),
            eqValue(-12345L)
        )
        Mockito.verify(progressStore).clearExecutionContext(eqValue(ExpertClassificationBackfillService.TASK_TYPE), eqValue(-12345L))
    }

    @Test
    fun `failed execution marks progress FAILED and clears with bound id`() {
        Mockito.`when`(progressStore.tryStartWithToken(eqValue(ExpertClassificationBackfillService.TASK_TYPE), anyValue(TaskProgress("", "", 0, 0, 0))))
            .thenReturn(Pair(true, -12345L))
        Mockito.doAnswer { invocation ->
            invocation.getArgument<Runnable>(0).run()
            null
        }.`when`(executor).execute(anyValue(Runnable { }))
        Mockito.`when`(
            taskExecutionService.runAndRecordWithResult<ExpertClassificationBackfillResult>(
                eqValue(ExpertClassificationBackfillService.TASK_TYPE),
                eqValue("MANUAL"),
                anyValue(Any()),
                anyValue<(Long) -> Unit> { },
                Mockito.isNull(),
                anyValue { result() }
            )
        ).thenAnswer { invocation ->
            val onStarted = invocation.getArgument<((Long) -> Unit)?>(3)
            onStarted?.invoke(100L)
            throw RuntimeException("boom")
        }

        mockMvc.perform(
            post("/api/expert-classification/backfill")
                .session(authorizedSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validDryRunBody())
        )
            .andExpect(status().isAccepted)

        val progressCaptor = org.mockito.ArgumentCaptor.forClass(TaskProgress::class.java)
        Mockito.verify(progressStore).update(
            eqValue(ExpertClassificationBackfillService.TASK_TYPE),
            progressCaptor.capture() ?: TaskProgress("", "", 0, 0, 0),
            eqValue(100L)
        )
        org.junit.jupiter.api.Assertions.assertEquals("FAILED", progressCaptor.value.status)
        Mockito.verify(progressStore).clearExecutionContext(eqValue(ExpertClassificationBackfillService.TASK_TYPE), eqValue(100L))
    }

    @Test
    fun `validation rejects missing level mode wrong version and bad confirmation (I2-3)`() {
        mockMvc.perform(
            post("/api/expert-classification/backfill")
                .session(authorizedSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"mode":"DRY_RUN","version":"rnd-v1-2026"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("level 必填: RAW | CANDIDATE | APPLICATION"))

        mockMvc.perform(
            post("/api/expert-classification/backfill")
                .session(authorizedSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"level":"CANDIDATE","version":"rnd-v1-2026"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("mode 必填: DRY_RUN | EXECUTE"))

        mockMvc.perform(
            post("/api/expert-classification/backfill")
                .session(authorizedSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"level":"CANDIDATE","mode":"DRY_RUN","version":"rnd-v2"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("version 只允许 rnd-v1-2026"))

        mockMvc.perform(
            post("/api/expert-classification/backfill")
                .session(authorizedSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content(executeBody(confirmation = null))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("EXECUTE 需要 confirmation = EXECUTE_CANDIDATE:rnd-v1-2026"))

        mockMvc.perform(
            post("/api/expert-classification/backfill")
                .session(authorizedSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content(executeBody(confirmation = "EXECUTE_RAW:rnd-v1-2026"))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("EXECUTE 需要 confirmation = EXECUTE_CANDIDATE:rnd-v1-2026"))

        mockMvc.perform(
            post("/api/expert-classification/backfill")
                .session(authorizedSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"level":"CANDIDATE","mode":"DRY_RUN","version":"rnd-v1-2026","batchSize":50}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("batchSize 必须在 100..1000"))

        mockMvc.perform(
            post("/api/expert-classification/backfill")
                .session(authorizedSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"level":"CANDIDATE","mode":"DRY_RUN","version":"rnd-v1-2026","maxDocs":0}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("maxDocs 必须是正整数"))

        Mockito.verify(progressStore, Mockito.never()).tryStartWithToken(anyString(), anyValue(TaskProgress("", "", 0, 0, 0)))
    }

    @Test
    fun `RAW requires explicit confirmation never defaulted (I2-3)`() {
        Mockito.`when`(progressStore.tryStartWithToken(eqValue(ExpertClassificationBackfillService.TASK_TYPE), anyValue(TaskProgress("", "", 0, 0, 0))))
            .thenReturn(Pair(true, -999L))
        // RAW EXECUTE 显式 confirmation → 202；缺 confirmation/默认值绝不写 RAW
        mockMvc.perform(
            post("/api/expert-classification/backfill")
                .session(authorizedSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"level":"RAW","mode":"EXECUTE","version":"rnd-v1-2026","confirmation":"EXECUTE_RAW:rnd-v1-2026"}""")
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.taskType").value("EXPERT_CLASSIFICATION_BACKFILL"))
    }
}
