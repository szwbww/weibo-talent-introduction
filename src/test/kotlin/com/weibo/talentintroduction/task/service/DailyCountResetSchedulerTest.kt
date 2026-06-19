package com.weibo.talentintroduction.task.service

import com.weibo.talentintroduction.mail.service.DailyResetResult
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.task.domain.TaskExecution
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class DailyCountResetSchedulerTest {
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val taskExecutionService = Mockito.mock(TaskExecutionService::class.java)
    private val scheduler = DailyCountResetScheduler(mailSenderAccountService, taskExecutionService)

    @Test
    fun `runScheduledReset invokes runAndRecord with DAILY_COUNT_RESET task type`() {
        Mockito.`when`(
            taskExecutionService.runAndRecord(
                eqValue("DAILY_COUNT_RESET"),
                eqValue("SCHEDULED"),
                eqValue("daily-count-reset"),
                Mockito.isNull<(Long) -> Unit>(),
                anyValue {}
            )
        ).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val block = invocation.arguments[4] as () -> Unit
            block.invoke()
            TaskExecution(
                taskType = "DAILY_COUNT_RESET",
                triggerType = "SCHEDULED",
                status = "SUCCESS",
                requestPayload = null,
                resultSummary = null,
                startedAt = java.time.LocalDateTime.now(),
                createdAt = java.time.LocalDateTime.now(),
                updatedAt = java.time.LocalDateTime.now()
            )
        }
        Mockito.`when`(mailSenderAccountService.resetDailyCounts())
            .thenReturn(DailyResetResult(countReset = 1, pauseResumed = 1))

        scheduler.runScheduledReset()

        Mockito.verify(taskExecutionService).runAndRecord(
            eqValue("DAILY_COUNT_RESET"),
            eqValue("SCHEDULED"),
            eqValue("daily-count-reset"),
            Mockito.isNull<(Long) -> Unit>(),
            anyValue {}
        )
        Mockito.verify(mailSenderAccountService).resetDailyCounts()
    }

    private fun eqValue(value: String): String =
        Mockito.eq(value) ?: value

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue
}
