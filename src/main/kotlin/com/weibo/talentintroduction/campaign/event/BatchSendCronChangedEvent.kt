package com.weibo.talentintroduction.campaign.event

data class BatchSendCronChangedEvent(
    val oldCron: String,
    val newCron: String
)
