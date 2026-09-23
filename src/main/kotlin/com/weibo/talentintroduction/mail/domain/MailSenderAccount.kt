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
    /**
     * 共享收件箱主账号代码（I-1，V134）：NULL = 本账号独立收件；
     * 非空只指向单层主账号，不修改 SMTP/IMAP 凭据、enabled 或发信计数。
     */
    val inboundMailboxCode: String? = null,
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
    val warmupEnabled: Boolean? = null,
    val warmupStartedAt: LocalDateTime? = null,
    val warmupStepsJson: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
