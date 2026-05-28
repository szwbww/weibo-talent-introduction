package com.weibo.talentintroduction.template.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("mail_template")
data class MailTemplate(
    @Id
    val id: Long? = null,
    val templateCode: String,
    val templateName: String,
    val subject: String?,
    val body: String,
    val language: String = "en",
    val enabled: Boolean = true,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
