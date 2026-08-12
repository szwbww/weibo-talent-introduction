package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.unsubscribe")
data class UnsubscribeProperties(
    val baseUrl: String = "",
    val secret: String = "",
    val brandName: String = "Qingfei Talent",
    val brandLogoUrl: String = "",
    val siteUrl: String = "https://www.qingfeitalent.com",
    val footerLine1: String = "Jiangsu Qingfei Talent Technology Co., Ltd · Nanjing",
    val footerLine2: String = "QFtechtalent@qftechtalent.com"
)
