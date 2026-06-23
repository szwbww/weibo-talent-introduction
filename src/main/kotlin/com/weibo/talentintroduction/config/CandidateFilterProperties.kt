package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.candidate-filter")
data class CandidateFilterProperties(
    val requireOrcid: Boolean = false,
    val requireDoctoralDegree: Boolean = false,
    val requireValidEmail: Boolean = true,
    val excludeChineseNationality: Boolean = true,
    val enableAgeFilter: Boolean = false,
    val maxAgeExclusive: Int = 70
)
