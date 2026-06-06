package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.campaign.service.InitialOutreachBatchResult
import com.weibo.talentintroduction.campaign.service.InitialOutreachService
import com.weibo.talentintroduction.mail.service.AutoMailReplyBatchResult
import com.weibo.talentintroduction.mail.service.AutoMailReplyService
import com.weibo.talentintroduction.mail.service.BatchAutoMailReplyResult
import com.weibo.talentintroduction.mail.service.BatchAutoMailReplyService
import com.weibo.talentintroduction.mail.queue.MailQueuePublisher
import com.weibo.talentintroduction.mail.queue.QueuePublishResult
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.service.TaskExecutionService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/mail")
class MailAutomationController(
    private val initialOutreachService: InitialOutreachService,
    private val autoMailReplyService: AutoMailReplyService,
    private val batchAutoMailReplyService: BatchAutoMailReplyService,
    private val mailQueuePublisherProvider: ObjectProvider<MailQueuePublisher>,
    private val taskExecutionService: TaskExecutionService
) {
    @PostMapping("/initial-outreach")
    fun sendInitialOutreach(
        @RequestParam campaignId: Long,
        @RequestParam(defaultValue = "10") size: Int
    ): InitialOutreachBatchResult =
        initialOutreachService.sendInitialBatch(campaignId, size)

    @PostMapping("/auto-reply")
    fun receiveAndAutoReply(
        @RequestParam accountCode: String,
        @RequestParam(defaultValue = "20") maxMessages: Int
    ): AutoMailReplyBatchResult =
        autoMailReplyService.receiveAndAutoReply(accountCode, maxMessages)

    @PostMapping("/auto-reply/all")
    fun receiveAndAutoReplyAll(
        @RequestParam(defaultValue = "20") maxMessagesPerAccount: Int
    ): BatchAutoMailReplyResult =
        batchAutoMailReplyService.receiveAndAutoReplyAll(maxMessagesPerAccount)

    @PostMapping("/initial-outreach/async")
    fun enqueueInitialOutreach(
        @RequestParam campaignId: Long,
        @RequestParam(defaultValue = "10") size: Int
    ): QueuePublishResult =
        queuePublisher().publishInitialOutreach(campaignId, size)

    @PostMapping("/auto-reply/async")
    fun enqueueAutoReply(
        @RequestParam accountCode: String,
        @RequestParam(defaultValue = "20") maxMessages: Int
    ): QueuePublishResult =
        queuePublisher().publishAutoReply(accountCode, maxMessages)

    @PostMapping("/auto-reply/all/async")
    fun enqueueAutoReplyAll(
        @RequestParam(defaultValue = "20") maxMessagesPerAccount: Int
    ): QueuePublishResult =
        queuePublisher().publishAutoReplyAll(maxMessagesPerAccount)

    @PostMapping("/auto-reply/check-replies")
    fun checkReplies(
        @RequestBody request: CheckRepliesRequest
    ): TaskExecution {
        val maxMessages = request.maxMessagesPerAccount ?: 20
        require(maxMessages in 1..100) {
            "maxMessagesPerAccount must be between 1 and 100"
        }
        val contactIds = request.contactIds
            ?.distinct()
            .orEmpty()

        if (contactIds.isNotEmpty()) {
            require(contactIds.all { it > 0 }) {
                "contactIds must contain positive ids"
            }
            require(contactIds.size <= 500) {
                "contactIds must not contain more than 500 ids"
            }
        }

        val normalizedRequest = CheckRepliesRequest(
            contactIds = contactIds.takeIf { it.isNotEmpty() },
            maxMessagesPerAccount = maxMessages
        )

        return taskExecutionService.runAndRecord(
            taskType = "AUTO_REPLY_ALL",
            triggerType = if (contactIds.isEmpty()) "MANUAL_ALL" else "MANUAL_SELECTIVE",
            request = normalizedRequest
        ) {
            if (contactIds.isEmpty()) {
                batchAutoMailReplyService.receiveAndAutoReplyAll(maxMessages)
            } else {
                batchAutoMailReplyService.receiveAndAutoReplyForContacts(
                    contactIds = contactIds,
                    maxMessagesPerAccount = maxMessages
                )
            }
        }
    }

    private fun queuePublisher(): MailQueuePublisher =
        mailQueuePublisherProvider.getIfAvailable()
            ?: error("Mail queue is not enabled. Set MAIL_QUEUE_ENABLED=true and configure RabbitMQ.")
}

data class CheckRepliesRequest(
    val contactIds: List<Long>? = null,
    val maxMessagesPerAccount: Int? = 20
)
