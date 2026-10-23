package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties("talent-introduction.expert-discovery.paper-archive")
data class DiscoveryPaperArchiveProperties(
    var directory: String = "/opt/talent/data/discovery-papers",
    var allowed: Boolean = true
)

@Configuration
@EnableConfigurationProperties(DiscoveryPaperArchiveProperties::class)
class DiscoveryPaperArchiveConfiguration
