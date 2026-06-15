package com.weibo.talentintroduction.expert.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("eligibility_filter_setting")
data class EligibilityFilterSetting(
    @Id
    val id: Long? = null,
    val settingKey: String,
    val settingValue: String,
    val updatedAt: LocalDateTime? = null
)
