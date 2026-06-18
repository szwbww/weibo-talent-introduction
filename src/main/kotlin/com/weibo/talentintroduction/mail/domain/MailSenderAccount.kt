package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("mail_sender_account")
data class MailSenderAccount(
    @Id
    val id: Long? = null,
    val accountCode: String,
    val senderEmail: String,
    val senderName: String,
    val senderTitle: String?,
    val senderDisplayName: String?,
    val teamName: String?,
    val countryName: String?,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpUsername: String,
    val smtpPassword: String,
    val imapHost: String,
    val imapPort: Int,
    val imapUsername: String,
    val imapPassword: String,
    val strategyWeight: Int = 100,
    val dailySendLimit: Int = 100,
    val todaySentCount: Int = 0,
    val lastSentAt: LocalDateTime? = null,
    val enabled: Boolean = true,
    val autoSendPaused: Boolean = false,
    val autoSendPausedReason: String? = null,
    val autoSendPausedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
