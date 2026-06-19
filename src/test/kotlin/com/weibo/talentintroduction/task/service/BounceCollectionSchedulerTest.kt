package com.weibo.talentintroduction.task.service

import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.service.AutoMailReplyBatchResult
import com.weibo.talentintroduction.mail.service.AutoMailReplyService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.task.domain.TaskExecution
import org.junit.jupiter.api.Test
import org.mockito.InOrder
import org.mockito.Mockito

class BounceCollectionSchedulerTest {
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val autoMailReplyService = Mockito.mock(AutoMailReplyService::class.java)
    private val mailSchedulingProperties = MailSchedulingProperties(autoReplyMaxMessagesPerAccount = 20)
    private val taskExecutionService = Mockito.mock(TaskExecutionService::class.java)
    private val scheduler = BounceCollectionScheduler(
        mailSenderAccountService,
        autoMailReplyService,
        mailSchedulingProperties,
        taskExecutionService
    )

    @Test
    fun `runCollection runs auto reply before bounce collection via receiveAndAutoReply`() {
        val account = MailSenderAccount(
            accountCode = "a1",
            senderEmail = "a1@example.com",
            senderName = "A1",
            senderTitle = "Officer",
            senderDisplayName = "A1",
            teamName = "Team",
            countryName = "China",
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "a1@example.com",
            smtpPassword = "secret",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "a1@example.com",
            imapPassword = "secret",
            enabled = true
        )
        Mockito.`when`(
            taskExecutionService.runAndRecord<Unit>(
                eqValue("BOUNCE_COLLECTION"),
                eqValue("SCHEDULED"),
                eqValue("bounce-collection"),
                anyValue(null),
                anyValue {}
            )
        ).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val block = invocation.arguments[4] as () -> Unit
            block.invoke()
            TaskExecution(
                taskType = "BOUNCE_COLLECTION",
                triggerType = "SCHEDULED",
                status = "SUCCESS",
                requestPayload = null,
                resultSummary = null,
                startedAt = java.time.LocalDateTime.now(),
                createdAt = java.time.LocalDateTime.now(),
                updatedAt = java.time.LocalDateTime.now()
            )
        }
        Mockito.`when`(mailSenderAccountService.listAutoReceiveAccounts()).thenReturn(listOf(account))
        Mockito.`when`(autoMailReplyService.receiveAndAutoReply("a1", 20))
            .thenReturn(AutoMailReplyBatchResult(fetched = 0, recorded = 0, replied = 0, manualReview = 0))

        scheduler.runCollection()

        val inOrder: InOrder = Mockito.inOrder(taskExecutionService, autoMailReplyService)
        inOrder.verify(taskExecutionService).runAndRecord<Unit>(
            eqValue("BOUNCE_COLLECTION"),
            eqValue("SCHEDULED"),
            eqValue("bounce-collection"),
            anyValue(null),
            anyValue {}
        )
        inOrder.verify(autoMailReplyService).receiveAndAutoReply("a1", 20)
    }

    private fun eqValue(value: String): String =
        Mockito.eq(value) ?: value

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue
}
