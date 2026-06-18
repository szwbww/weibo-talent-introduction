package com.weibo.talentintroduction.campaign.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("batch_send_setting")
data class BatchSendSetting(
    @Id
    val id: Long? = null,
    val settingKey: String,
    val settingValue: String,
    val updatedAt: LocalDateTime? = null
)
