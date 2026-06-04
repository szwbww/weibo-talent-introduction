package com.weibo.talentintroduction.campaign.domain

enum class OperatorStatus {
    NOT_CONTACTED,
    CONTACTED,
    REPLIED,
    MATERIALS_RECEIVED,
    INVITED,
    COMPLETED;

    companion object {
        fun fromName(value: String): OperatorStatus =
            entries.firstOrNull { it.name == value }
                ?: error("Unsupported operator status: $value")
    }
}