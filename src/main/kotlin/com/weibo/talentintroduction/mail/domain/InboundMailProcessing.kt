package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("inbound_mail_processing")
data class InboundMailProcessing(
    @Id
    val id: Long? = null,
    val senderAccountCode: String,
    val imapUid: Long,
    val messageId: String?,
    val inReplyTo: String? = null,
    val fromEmail: String,
    val subject: String?,
    val body: String? = null,
    val cleanedBody: String? = null,
    val receivedAt: LocalDateTime,
    val processStatus: String,
    val processReason: String,
    val resolvedAt: LocalDateTime? = null,
    val resolvedBy: String? = null,
    val expertContactId: Long?,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
