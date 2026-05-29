package com.weibo.talentintroduction.common.domain

enum class ConversationStatus {
    NEW,
    INTRO_SENT,
    WAITING_REPLY,
    INTEREST_CONFIRMED,
    QA_AUTO_REPLIED,
    MEETING_SCHEDULING,
    MEETING_SCHEDULED,
    MEETING_DONE,
    MEETING_INVITATION_SENT,
    WAITING_MEETING_CONFIRMATION,
    MATERIALS_REQUESTED,
    MATERIALS_PARTIAL,
    MATERIALS_RECEIVED,
    COMPANY_MATCHED,
    APPLICATION_PREPARING,
    VIDEO_REQUESTED,
    VIDEO_RECEIVED,
    COMMITMENT_REQUESTED,
    COMMITMENT_RECEIVED,
    SUBMITTED,
    RESULT_PENDING,
    REJECTED_THIS_ROUND,
    NEXT_ROUND_FOLLOW_UP,
    MANUAL_HANDOFF,
    MANUAL_REVIEW,
    CLOSED;

    companion object {
        fun fromName(value: String): ConversationStatus =
            entries.firstOrNull { it.name == value }
                ?: error("Unsupported conversation status: $value")
    }
}
