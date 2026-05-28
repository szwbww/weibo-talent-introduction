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
    val fromEmail: String,
    val subject: String?,
    val receivedAt: LocalDateTime,
    val processStatus: String,
    val processReason: String,
    val expertContactId: Long?,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
