package com.weibo.talentintroduction.common.domain

enum class ConversationStatus {
    NEW,
    INTRO_SENT,
    WAITING_REPLY,
    QA_AUTO_REPLIED,
    MEETING_INVITATION_SENT,
    WAITING_MEETING_CONFIRMATION,
    MANUAL_HANDOFF,
    MANUAL_REVIEW,
    CLOSED
}
