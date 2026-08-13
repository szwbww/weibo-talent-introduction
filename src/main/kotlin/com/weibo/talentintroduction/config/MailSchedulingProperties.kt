package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.scheduling")
data class MailSchedulingProperties(
    val enabled: Boolean = false,
    val autoReplyAllCron: String = "-",
    val autoReplyMaxMessagesPerAccount: Int = 20,
    val initialOutreachCron: String = "-",
    val initialOutreachCampaignId: Long = 0,
    val initialOutreachBatchSize: Int = 10,
    val initialOutreachSendIntervalMs: Long = 30000,
    val initialOutreachSendJitterMs: Long = 60000,
    val operatorStatusSyncCron: String = "-",
    val operatorStatusReconcileCron: String = "-",
    val aiQaExtractionCron: String = "-",
    val aiQaExtractionMaxContacts: Int = 20
)
