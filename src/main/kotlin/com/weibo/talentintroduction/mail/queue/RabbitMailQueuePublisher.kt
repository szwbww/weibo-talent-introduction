package com.weibo.talentintroduction.mail.queue

import com.weibo.talentintroduction.config.MailQueueProperties
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "talent-introduction.mail-queue", name = ["enabled"], havingValue = "true")
class RabbitMailQueuePublisher(
    private val rabbitTemplate: RabbitTemplate,
    private val properties: MailQueueProperties
) : MailQueuePublisher {
    override fun publishInitialOutreach(campaignId: Long, size: Int): QueuePublishResult {
        rabbitTemplate.convertAndSend(
            properties.exchange,
            properties.initialOutreachRoutingKey,
            InitialOutreachBatchMessage(campaignId = campaignId, size = size)
        )
        return QueuePublishResult(true, properties.initialOutreachQueue, "INITIAL_OUTREACH_BATCH")
    }

    override fun publishAutoReply(accountCode: String, maxMessages: Int): QueuePublishResult {
        rabbitTemplate.convertAndSend(
            properties.exchange,
            properties.autoReplyAccountRoutingKey,
            AutoReplyAccountPollMessage(accountCode = accountCode, maxMessages = maxMessages)
        )
        return QueuePublishResult(true, properties.autoReplyAccountQueue, "AUTO_REPLY_ACCOUNT_POLL")
    }

    override fun publishAutoReplyAll(maxMessagesPerAccount: Int): QueuePublishResult {
        rabbitTemplate.convertAndSend(
            properties.exchange,
            properties.autoReplyAllAccountsRoutingKey,
            AutoReplyAllAccountsPollMessage(maxMessagesPerAccount = maxMessagesPerAccount)
        )
        return QueuePublishResult(true, properties.autoReplyAllAccountsQueue, "AUTO_REPLY_ALL_ACCOUNTS_POLL")
    }
}
