package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("bounce_record")
data class BounceRecord(
    @Id val id: Long? = null,
    val senderAccountCode: String,
    val bounceMessageId: String,
    val originalMessageId: String?,
    val originalExpertContactId: Long?,
    val bounceType: String,
    val dsnStatus: String?,
    val bounceReason: String?,
    val receivedAt: LocalDateTime,
    val createdAt: LocalDateTime? = null
)
