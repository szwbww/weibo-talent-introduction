package com.weibo.talentintroduction.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Configuration
@ConditionalOnProperty(prefix = "talent-introduction.llm", name = ["enabled"], havingValue = "true")
class LlmClientConfig(
    private val properties: LlmProperties
) {
    @Bean
    @Qualifier("llmRestTemplate")
    fun llmRestTemplate(builder: RestTemplateBuilder): RestTemplate =
        builder
            .setConnectTimeout(Duration.ofMillis(properties.timeoutMs.toLong()))
            .setReadTimeout(Duration.ofMillis(properties.timeoutMs.toLong()))
            .build()
}
