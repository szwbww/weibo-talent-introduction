package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.campaign.service.InitialOutreachBatchResult
import com.weibo.talentintroduction.campaign.service.InitialOutreachService
import com.weibo.talentintroduction.mail.service.AutoMailReplyBatchResult
import com.weibo.talentintroduction.mail.service.AutoMailReplyService
import com.weibo.talentintroduction.mail.service.BatchAutoMailReplyResult
import com.weibo.talentintroduction.mail.service.BatchAutoMailReplyService
import com.weibo.talentintroduction.mail.queue.MailQueuePublisher
import com.weibo.talentintroduction.mail.queue.QueuePublishResult
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/mail")
class MailAutomationController(
    private val initialOutreachService: InitialOutreachService,
    private val autoMailReplyService: AutoMailReplyService,
    private val batchAutoMailReplyService: BatchAutoMailReplyService,
    private val mailQueuePublisherProvider: ObjectProvider<MailQueuePublisher>
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

    private fun queuePublisher(): MailQueuePublisher =
        mailQueuePublisherProvider.getIfAvailable()
            ?: error("Mail queue is not enabled. Set MAIL_QUEUE_ENABLED=true and configure RabbitMQ.")
}
