package com.weibo.talentintroduction.campaign.domain

object MailSendAttemptStatus {
    const val PREPARED = "PREPARED"
    const val DELIVERY_IN_PROGRESS = "DELIVERY_IN_PROGRESS"
    const val SENT = "SENT"
    const val FAILED = "FAILED"
    const val DELIVERY_UNKNOWN = "DELIVERY_UNKNOWN"
    const val FAILED_SAFE_TO_RETRY = "FAILED_SAFE_TO_RETRY"
}
