package com.weibo.talentintroduction.qa.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

enum class QaReplyPolicy {
    AUTO,
    REVIEW,
    NEVER;

    fun legacyAutoReplyEnabled(): Boolean = this == AUTO

    fun legacyHandoffRequired(): Boolean = this != AUTO

    companion object {
        fun fromName(value: String): QaReplyPolicy =
            try {
                valueOf(value.trim().uppercase())
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("replyPolicy must be AUTO, REVIEW, or NEVER")
            }

        fun aggregate(policies: Collection<QaReplyPolicy>): QaReplyPolicy {
            if (policies.isEmpty()) {
                return REVIEW
            }
            if (policies.any { it == REVIEW }) {
                return REVIEW
            }
            if (policies.all { it == AUTO }) {
                return AUTO
            }
            return REVIEW
        }
    }
}

@Table("qa_rule")
data class QaRule(
    @Id
    val id: Long? = null,
    val categoryId: Long,
    val keywords: String,
    val matchMode: String = "ANY",
    val priority: Int = 100,
    val replySubject: String?,
    val replyBody: String,
    val answerBody: String = "",
    val displayName: String? = null,
    val sectionTitle: String? = null,
    val replyPolicy: String = QaReplyPolicy.REVIEW.name,
    val autoReplyEnabled: Boolean = true,
    val handoffRequired: Boolean = false,
    val supersedesChildren: Boolean = false,
    val enabled: Boolean = true,
    val coverageKeys: String = "",
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
) {
    fun replyPolicyEnum(): QaReplyPolicy = QaReplyPolicy.fromName(replyPolicy)

    fun isMatchable(): Boolean = replyPolicyEnum() != QaReplyPolicy.NEVER

    fun withReplyPolicy(policy: QaReplyPolicy): QaRule = copy(
        replyPolicy = policy.name,
        autoReplyEnabled = policy.legacyAutoReplyEnabled(),
        handoffRequired = policy.legacyHandoffRequired()
    )
}
