package com.weibo.talentintroduction.campaign.domain

import java.time.Instant

/** Structured calendar row. UTC values are mapped explicitly by the JDBC repository. */
data class MeetingCalendarEvent(
    val id: Long? = null,
    val expertContactId: Long,
    val sourceMailRecordId: Long? = null,
    val startsAtUtc: Instant,
    val endsAtUtc: Instant,
    val meetingLink: String? = null,
    val note: String? = null,
    val cancelReason: String? = null,
    val status: String = ACTIVE,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        const val ACTIVE = "ACTIVE"
        const val CANCELLED = "CANCELLED"
    }
}

/** Input used by the successful-send integration point. Values are already UTC instants. */
data class MeetingCalendarInput(
    val startUtc: Instant,
    val endUtc: Instant,
    val meetingLink: String? = null
)
