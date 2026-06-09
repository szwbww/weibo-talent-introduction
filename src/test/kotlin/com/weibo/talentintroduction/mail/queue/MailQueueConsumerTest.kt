package com.weibo.talentintroduction.mail.queue

import com.weibo.talentintroduction.campaign.service.InitialOutreachService
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.service.AutoMailReplyService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.service.TaskExecutionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime

class MailQueueConsumerTest {
    private val initialOutreachService = Mockito.mock(InitialOutreachService::class.java)
    private val autoMailReplyService = Mockito.mock(AutoMailReplyService::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val mailQueuePublisher = Mockito.mock(MailQueuePublisher::class.java)
    private val taskExecutionService = Mockito.mock(TaskExecutionService::class.java)
    private val consumer = MailQueueConsumer(
        initialOutreachService,
        autoMailReplyService,
        mailSenderAccountService,
        mailQueuePublisher,
        taskExecutionService
    )

    @Test
    fun `all account poll message fans out to auto-receive accounts only`() {
        Mockito.`when`(
            taskExecutionService.runAndRecord(
                eqValue("AUTO_REPLY_ALL_DISPATCH"),
                eqValue("QUEUE"),
                anyValue(TaskDispatchRequestStub),
                anyValue<(Long) -> Unit> { },
                anyValue { QueueFanOutResult(dispatched = 0) }
            )
        ).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val block = invocation.arguments[4] as () -> QueueFanOutResult
            block()
            taskExecution()
        }
        Mockito.`when`(mailSenderAccountService.listAutoReceiveAccounts()).thenReturn(
            listOf(account("a1"), account("a2"))
        )

        consumer.handleAutoReplyAllAccounts(AutoReplyAllAccountsPollMessage(maxMessagesPerAccount = 7))

        Mockito.verify(mailQueuePublisher).publishAutoReply("a1", 7)
        Mockito.verify(mailQueuePublisher).publishAutoReply("a2", 7)
    }

    @Test
    fun `does not publish to simulator account`() {
        Mockito.`when`(
            taskExecutionService.runAndRecord(
                eqValue("AUTO_REPLY_ALL_DISPATCH"),
                eqValue("QUEUE"),
                anyValue(TaskDispatchRequestStub),
                anyValue<(Long) -> Unit> { },
                anyValue { QueueFanOutResult(dispatched = 0) }
            )
        ).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val block = invocation.arguments[4] as () -> QueueFanOutResult
            block()
            taskExecution()
        }
        Mockito.`when`(mailSenderAccountService.listAutoReceiveAccounts()).thenReturn(
            listOf(account("a1"))
        )

        consumer.handleAutoReplyAllAccounts(AutoReplyAllAccountsPollMessage(maxMessagesPerAccount = 7))

        Mockito.verify(mailQueuePublisher).publishAutoReply("a1", 7)
        Mockito.verify(mailQueuePublisher, Mockito.times(1)).publishAutoReply(
            Mockito.anyString(),
            Mockito.anyInt()
        )
    }

    @Test
    fun `dispatched count matches auto-receive account count`() {
        Mockito.`when`(
            taskExecutionService.runAndRecord(
                eqValue("AUTO_REPLY_ALL_DISPATCH"),
                eqValue("QUEUE"),
                anyValue(TaskDispatchRequestStub),
                anyValue<(Long) -> Unit> { },
                anyValue { QueueFanOutResult(dispatched = 0) }
            )
        ).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val block = invocation.arguments[4] as () -> QueueFanOutResult
            val result = block()
            assertEquals(3, result.dispatched)
            taskExecution()
        }
        Mockito.`when`(mailSenderAccountService.listAutoReceiveAccounts()).thenReturn(
            listOf(account("a1"), account("a2"), account("a3"))
        )

        consumer.handleAutoReplyAllAccounts(AutoReplyAllAccountsPollMessage(maxMessagesPerAccount = 7))
    }

    private fun account(accountCode: String): MailSenderAccount =
        MailSenderAccount(
            accountCode = accountCode,
            senderEmail = "$accountCode@qftechtalent.com",
            senderName = accountCode,
            senderTitle = "Customer Care Officer",
            senderDisplayName = accountCode,
            teamName = "Qingfei Tech Talent Team",
            countryName = "China",
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "$accountCode@qftechtalent.com",
            smtpPassword = "secret",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "$accountCode@qftechtalent.com",
            imapPassword = "secret"
        )

    private fun taskExecution(): TaskExecution =
        TaskExecution(
            taskType = "AUTO_REPLY_ALL_DISPATCH",
            triggerType = "QUEUE",
            status = "SUCCESS",
            requestPayload = "{}",
            resultSummary = "{}",
            startedAt = LocalDateTime.now(),
            finishedAt = LocalDateTime.now()
        )

    private fun eqValue(value: String): String =
        Mockito.eq(value) ?: value

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    private companion object {
        val TaskDispatchRequestStub = QueueAccountPollRequest(
            accountCode = "stub",
            maxMessages = 0
        )
    }
}
