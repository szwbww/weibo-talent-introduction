package com.weibo.talentintroduction.campaign.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("mail_send_attempt")
data class MailSendAttempt(
    @Id
    val id: Long? = null,
    val orcidId: String,
    val mailType: String,
    val accountCode: String,
    val messageId: String,
    val status: String,
    val recipient: String? = null,
    val subject: String? = null,
    val body: String? = null,
    val contentType: String? = null,
    val errorSummary: String? = null,
    val quotaCounted: Boolean = false,
    val accountCountedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
