package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.email-validation")
data class EmailValidationProperties(
    val enableMxCheck: Boolean = true,
    val enableSmtpVerify: Boolean = false,
    val cacheTtlDays: Int = 30,
    val disposableDomainListPath: String = "classpath:email/disposable-domains.txt",
    val mxLookupTimeoutMs: Long = 5000
)
