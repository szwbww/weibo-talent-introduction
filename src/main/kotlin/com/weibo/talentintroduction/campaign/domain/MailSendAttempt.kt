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
    val status: String, // PREPARE_FAILED, DELIVERY_UNKNOWN, SENT, FAILED_SAFE_TO_RETRY
    val errorSummary: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
