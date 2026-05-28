package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.mail-queue")
data class MailQueueProperties(
    val enabled: Boolean = false,
    val exchange: String = "talent.mail.exchange",
    val deadLetterExchange: String = "talent.mail.dlx",
    val initialOutreachQueue: String = "talent.mail.initial-outreach",
    val autoReplyAccountQueue: String = "talent.mail.auto-reply.account",
    val autoReplyAllAccountsQueue: String = "talent.mail.auto-reply.all",
    val initialOutreachDeadLetterQueue: String = "talent.mail.initial-outreach.dlq",
    val autoReplyAccountDeadLetterQueue: String = "talent.mail.auto-reply.account.dlq",
    val autoReplyAllAccountsDeadLetterQueue: String = "talent.mail.auto-reply.all.dlq",
    val initialOutreachRoutingKey: String = "mail.initial-outreach",
    val autoReplyAccountRoutingKey: String = "mail.auto-reply.account",
    val autoReplyAllAccountsRoutingKey: String = "mail.auto-reply.all",
    val initialOutreachDeadLetterRoutingKey: String = "mail.initial-outreach.dlq",
    val autoReplyAccountDeadLetterRoutingKey: String = "mail.auto-reply.account.dlq",
    val autoReplyAllAccountsDeadLetterRoutingKey: String = "mail.auto-reply.all.dlq",
    val autoReplyAccountConcurrency: String = "2-8"
)
