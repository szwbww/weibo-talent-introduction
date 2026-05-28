package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("mail_record")
data class MailRecord(
    @Id
    val id: Long? = null,
    val expertContactId: Long,
    val direction: String,
    val mailType: String,
    val messageId: String?,
    val inReplyTo: String?,
    val subject: String?,
    val body: String?,
    val cleanedBody: String? = null,
    val matchedQaRuleId: Long?,
    val sendStatus: String?,
    val receivedAt: LocalDateTime?,
    val sentAt: LocalDateTime?,
    val createdAt: LocalDateTime? = null
)
