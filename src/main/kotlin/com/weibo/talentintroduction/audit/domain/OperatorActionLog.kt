package com.weibo.talentintroduction.audit.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("operator_action_log")
data class OperatorActionLog(
    @Id
    val id: Long? = null,
    val targetType: String,
    val targetId: Long,
    val expertContactId: Long? = null,
    val inboundProcessingId: Long? = null,
    val actionType: String,
    val actionSummary: String,
    val beforeValue: String? = null,
    val afterValue: String? = null,
    val operatorName: String? = null,
    val note: String? = null,
    val createdAt: LocalDateTime? = null
)