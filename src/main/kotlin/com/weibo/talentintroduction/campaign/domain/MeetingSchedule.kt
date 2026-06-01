package com.weibo.talentintroduction.campaign.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("meeting_schedule")
data class MeetingSchedule(
    @Id
    val id: Long? = null,
    val expertContactId: Long,
    val sourceMailRecordId: Long? = null,
    val expertAvailableText: String? = null,
    val expertTimezone: String? = null,
    val chinaTime: String? = null,
    val meetingTool: String? = null,
    val meetingLink: String? = null,
    val meetingStatus: String = "PENDING",
    val note: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
