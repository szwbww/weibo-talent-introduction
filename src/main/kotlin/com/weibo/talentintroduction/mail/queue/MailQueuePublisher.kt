package com.weibo.talentintroduction.mail.queue

interface MailQueuePublisher {
    fun publishInitialOutreach(campaignId: Long, size: Int): QueuePublishResult

    fun publishAutoReply(accountCode: String, maxMessages: Int): QueuePublishResult

    fun publishAutoReplyAll(maxMessagesPerAccount: Int): QueuePublishResult
}
