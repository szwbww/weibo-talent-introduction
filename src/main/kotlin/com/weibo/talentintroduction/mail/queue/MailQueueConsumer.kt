package com.weibo.talentintroduction.mail.queue

import com.weibo.talentintroduction.campaign.service.InitialOutreachService
import com.weibo.talentintroduction.mail.service.AutoMailReplyService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.task.service.TaskDispatchRequest
import com.weibo.talentintroduction.task.service.TaskExecutionService
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "talent-introduction.mail-queue", name = ["enabled"], havingValue = "true")
class MailQueueConsumer(
    private val initialOutreachService: InitialOutreachService,
    private val autoMailReplyService: AutoMailReplyService,
    private val mailSenderAccountService: MailSenderAccountService,
    private val mailQueuePublisher: MailQueuePublisher,
    private val taskExecutionService: TaskExecutionService
) {
    @RabbitListener(queues = ["\${talent-introduction.mail-queue.initial-outreach-queue:talent.mail.initial-outreach}"])
    fun handleInitialOutreach(message: InitialOutreachBatchMessage) {
        taskExecutionService.runAndRecord(
            taskType = "INITIAL_OUTREACH",
            triggerType = "QUEUE",
            request = TaskDispatchRequest(
                campaignId = message.campaignId,
                batchSize = message.size,
                dispatchMode = "QUEUE"
            )
        ) {
            initialOutreachService.sendInitialBatch(message.campaignId, message.size)
        }
    }

    @RabbitListener(
        queues = ["\${talent-introduction.mail-queue.auto-reply-account-queue:talent.mail.auto-reply.account}"],
        concurrency = "\${talent-introduction.mail-queue.auto-reply-account-concurrency:2-8}"
    )
    fun handleAutoReplyAccount(message: AutoReplyAccountPollMessage) {
        taskExecutionService.runAndRecord(
            taskType = "AUTO_REPLY_ACCOUNT",
            triggerType = "QUEUE",
            request = QueueAccountPollRequest(
                accountCode = message.accountCode,
                maxMessages = message.maxMessages
            )
        ) {
            autoMailReplyService.receiveAndAutoReply(message.accountCode, message.maxMessages)
        }
    }

    @RabbitListener(queues = ["\${talent-introduction.mail-queue.auto-reply-all-accounts-queue:talent.mail.auto-reply.all}"])
    fun handleAutoReplyAllAccounts(message: AutoReplyAllAccountsPollMessage) {
        taskExecutionService.runAndRecord(
            taskType = "AUTO_REPLY_ALL_DISPATCH",
            triggerType = "QUEUE",
            request = TaskDispatchRequest(
                maxMessagesPerAccount = message.maxMessagesPerAccount,
                dispatchMode = "QUEUE"
            )
        ) {
            val accounts = mailSenderAccountService.listEnabledAccounts()
            accounts.forEach { account ->
                mailQueuePublisher.publishAutoReply(
                    account.accountCode,
                    message.maxMessagesPerAccount
                )
            }
            QueueFanOutResult(dispatched = accounts.size)
        }
    }
}

data class QueueAccountPollRequest(
    val accountCode: String,
    val maxMessages: Int
)

data class QueueFanOutResult(
    val dispatched: Int
)
