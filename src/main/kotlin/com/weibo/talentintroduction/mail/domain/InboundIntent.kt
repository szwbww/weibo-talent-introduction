package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("inbound_intent")
data class InboundIntent(
    @Id
    val id: Long? = null,
    val mailRecordId: Long,
    val expertContactId: Long,
    val intentCode: String,
    val confidence: Int,
    val matchedKeywords: String?,
    val autoAction: String,
    val createdAt: LocalDateTime? = null
)
