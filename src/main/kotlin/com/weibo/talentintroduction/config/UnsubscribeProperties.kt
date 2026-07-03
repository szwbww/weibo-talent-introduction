package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.unsubscribe")
data class UnsubscribeProperties(
    val baseUrl: String = "",
    val secret: String = ""
)
