package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("inbound_mail_tag")
data class InboundMailTag(
    @Id val id: Long? = null,
    val inboundProcessingId: Long,
    val tagType: String,
    val qaRuleId: Long? = null,
    val label: String,
    val source: String,
    val createdBy: String? = null,
    val createdAt: LocalDateTime? = null
)
