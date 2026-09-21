package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.expert-discovery.arxiv")
data class ArxivProperties(
    val enabled: Boolean = false,
    /** I-2: 官方入口只支持 HTTPS（http 入口实测返回 301 空体）；application.yml 的同名默认值必须同步为 https。 */
    val baseUrl: String = "https://export.arxiv.org/api",
    val requestDelayMs: Long = 3000,
    val maxPapersPerSource: Int = 100
)
