package com.weibo.talentintroduction.mail.service

import org.springframework.stereotype.Service

/** Only frozen server content enters the existing manual send pipeline. No SMTP transaction here. */
@Service
class MailReviewSendService(
    private val drafts: MailReviewDraftService,
    private val pending: PendingMailOperationService
) {
    fun send(actor: String?, id: Long, request: MailReviewSendRequest): PendingMailSendResult {
        val ref = drafts.prepareSend(actor, id, request)
        val row = ref.draft
        return if (row.targetKind == "INBOUND") {
            pending.sendManualRichReply(row.targetId, row.accountCode, row.subject, row.html, row.text, ref.actor,
                safetyWarningConfirmed = request.safetyWarningConfirmed, strongConfirmationText = request.strongConfirmationText,
                authenticatedUsername = ref.actor, reviewSendRef = ref)
        } else {
            pending.sendConversationManualRichReply(row.contactId, requireNotNull(row.sendRequestId), row.accountCode,
                row.targetId, row.subject, row.html, row.text, ref.actor,
                safetyWarningConfirmed = request.safetyWarningConfirmed, strongConfirmationText = request.strongConfirmationText,
                authenticatedUsername = ref.actor, reviewSendRef = ref)
        }
    }
}
