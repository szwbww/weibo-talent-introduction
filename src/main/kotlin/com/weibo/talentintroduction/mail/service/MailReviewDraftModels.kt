package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime

enum class MailReviewDraftState { DRAFT, SUBMITTED, RETURNED, APPROVED }

data class MailReviewDraftRow(
    val id: Long = 0,
    val ownerUsername: String,
    val contactId: Long,
    val clientRequestId: String,
    val targetKind: String,
    val targetId: Long,
    val accountCode: String,
    val version: Long = 1,
    val state: MailReviewDraftState,
    val subject: String,
    val html: String,
    val text: String,
    val submittedAt: LocalDateTime? = null,
    val returnReason: String? = null,
    val reviewedBy: String? = null,
    val reviewedAt: LocalDateTime? = null,
    val approvedContextToken: String? = null,
    val sendRequestId: String? = null,
    val sendAttemptId: Long? = null,
    val deletedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class MailReviewDraftTarget(val targetKind: String, val targetId: Long, val accountCode: String)
data class MailReviewDraftEvent(
    val id: Long,
    val draftId: Long,
    val draftVersion: Long,
    val eventType: String,
    val actorUsername: String,
    val payload: Map<String, Any?>,
    val createdAt: LocalDateTime
)

data class MailReviewDraftDetail(
    val id: Long,
    val contactId: Long,
    val expertName: String?,
    val ownerUsername: String,
    val version: Long,
    val state: MailReviewDraftState,
    val subject: String,
    val html: String,
    val text: String,
    val accountCode: String,
    val targetKind: String,
    val targetId: Long,
    val submittedAt: LocalDateTime?,
    val returnReason: String?,
    val reviewedAt: LocalDateTime?,
    val deleted: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val commentCount: Long,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val currentContextToken: String? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val approvedContextToken: String? = null,
    val sendStatus: String = "NOT_SENT",
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val sentMailRecordId: Long? = null
)
data class MailReviewDraftPage(val items: List<Any>, val total: Long, val page: Int, val size: Int)
data class MailReviewDraftDeleted(val id: Long, val deleted: Boolean = true)
data class MailReviewDraftContext(val currentToken: String, val approvedToken: String?, val changed: Boolean, val observedAt: LocalDateTime)
data class MailReviewDraftConfirmation(val confirmationEventId: Long, val currentToken: String)

data class MailReviewSendRequest(
    val expectedVersion: Long,
    val confirmationEventId: Long? = null,
    val safetyWarningConfirmed: Boolean = false,
    val strongConfirmationText: String? = null
)

/** Server-constructed frozen identity, never accepted from an HTTP payload. */
data class MailReviewSendRef internal constructor(
    val draft: MailReviewDraftRow,
    val actor: String,
    val confirmationEventId: Long?
)

data class MailReviewSendProjection(val sendStatus: String, val sentMailRecordId: Long?)
class MailReviewDraftException(val status: Int, val code: String, message: String) : IllegalStateException(message)
