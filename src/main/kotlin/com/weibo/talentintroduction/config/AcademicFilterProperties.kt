package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.academic-filter")
data class AcademicFilterProperties(
    val enableHIndexFilter: Boolean = false,
    val minHIndex: Int = 5,
    val enableCitationFilter: Boolean = false,
    val minCitationCount: Int = 50,
    val enableActivityFilter: Boolean = false,
    val recentYearsThreshold: Int = 5
)
