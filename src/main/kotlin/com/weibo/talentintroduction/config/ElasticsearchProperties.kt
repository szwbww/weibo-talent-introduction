package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.elasticsearch")
data class ElasticsearchProperties(
    val baseUrl: String,
    val username: String,
    val password: String,
    val rawIndexName: String,
    val candidateIndexName: String,
    val applicationIndexName: String,
    val unsupportedAnswerIndexName: String = "trust_reply_unsupported_answer_v1"
)
