package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "talent-introduction.unsubscribe")
data class UnsubscribeProperties(
    val baseUrl: String = "",
    val secret: String = ""
)
