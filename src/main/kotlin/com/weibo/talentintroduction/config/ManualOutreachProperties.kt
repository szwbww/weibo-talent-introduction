package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.manual-outreach")
data class ManualOutreachProperties(
    val sendIntervalMs: Long = 1000L
)
