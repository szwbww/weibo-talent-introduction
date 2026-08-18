package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("auto_reply_confidence_log")
data class AutoReplyConfidenceLog(
    @Id
    val id: Long? = null,
    val expertContactId: Long,
    val inboundMailRecordId: Long?,
    val senderAccountCode: String,
    val inboundMessageId: String?,
    val crs: Double,
    val coverageScore: Double,
    val evidenceScore: Double,
    val consistencyScore: Double,
    val historyScore: Double,
    val requestCount: Int,
    val unsupportedCount: Int,
    val partialCount: Int,
    val verifiedRuleCount: Int,
    val warningCount: Int,
    val draftReadiness: String,
    val generationState: String,
    val decisionReason: String,
    val readyToSend: Boolean,
    val tier: String,
    val operatorEdited: Boolean? = null,
    val operatorEditDistance: Int? = null,
    val createdAt: LocalDateTime
)
