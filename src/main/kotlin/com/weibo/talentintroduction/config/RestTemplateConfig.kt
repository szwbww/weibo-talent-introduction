package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
@EnableConfigurationProperties(
    ElasticsearchProperties::class,
    CandidateFilterProperties::class,
    MailQueueProperties::class,
    MailSchedulingProperties::class,
    MailAttachmentStorageProperties::class
)
class RestTemplateConfig {
    @Bean
    fun restTemplate(): RestTemplate = RestTemplate()
}
