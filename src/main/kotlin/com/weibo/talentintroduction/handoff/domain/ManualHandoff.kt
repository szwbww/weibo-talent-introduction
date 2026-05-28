package com.weibo.talentintroduction.handoff.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("manual_handoff")
data class ManualHandoff(
    @Id
    val id: Long? = null,
    val expertContactId: Long,
    val reason: String,
    val handoffStatus: String = "PENDING",
    val assignedTo: String?,
    val note: String?,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
