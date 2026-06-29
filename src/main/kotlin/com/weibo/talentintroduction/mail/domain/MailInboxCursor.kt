package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("mail_inbox_cursor")
data class MailInboxCursor(
    @Id
    val id: Long? = null,
    val senderAccountCode: String,
    val uidValidity: Long,
    val lastUid: Long = 0,
    val updatedAt: LocalDateTime
)
