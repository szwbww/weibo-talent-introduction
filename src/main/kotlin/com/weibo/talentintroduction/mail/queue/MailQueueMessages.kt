package com.weibo.talentintroduction.mail.queue

data class InitialOutreachBatchMessage(
    val campaignId: Long = 0,
    val size: Int = 10
)

data class AutoReplyAccountPollMessage(
    val accountCode: String = "",
    val maxMessages: Int = 20
)

data class AutoReplyAllAccountsPollMessage(
    val maxMessagesPerAccount: Int = 20
)

data class QueuePublishResult(
    val accepted: Boolean,
    val queue: String,
    val messageType: String
)
