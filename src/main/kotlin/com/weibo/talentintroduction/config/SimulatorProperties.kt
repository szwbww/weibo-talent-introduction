package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties("talent-introduction.simulator")
data class SimulatorProperties(
    val enabled: Boolean = false,
    val campaignId: Long = 9000,
    val senderAccountCode: String = "SIMULATOR_NOOP",
    val orcidPrefix: String = "SIM-",
    val emailPrefix: String = "sim+"
)
