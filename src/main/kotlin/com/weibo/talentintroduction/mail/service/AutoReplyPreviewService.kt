package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.service.QaMatchService
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.springframework.stereotype.Service

enum class AutoReplyPreviewKind {
    QA_AUTO_REPLIED,
    QA_NO_MATCH,
    QA_GAP,
    MEETING_INVITATION,
    MEETING_ALREADY_SENT,
    MANUAL_HANDOFF
}

data class AutoReplyPreviewResult(
    val previewKind: AutoReplyPreviewKind,
    val intentCode: InboundIntentCode,
    val autoAction: AutoIntentAction,
    val confidence: Int,
    val matchedKeywords: List<String>,
    val replySubject: String? = null,
    val replyBody: String? = null,
    val reason: String? = null,
    val matchedRuleIds: List<Long> = emptyList(),
    val wouldBeBlockedBy: List<String> = emptyList(),
    val attachmentIntentIgnored: Boolean = false
)

@Service
class AutoReplyPreviewService(
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val mailBodyCleaner: MailBodyCleaner,
    private val inboundIntentClassifier: InboundIntentClassifier,
    private val qaMatchService: QaMatchService,
    private val mailComposeTemplateService: MailComposeTemplateService,
    private val mailSenderAccountService: MailSenderAccountService,
    private val mailRecordRepository: MailRecordRepository,
    private val expertContactRepository: ExpertContactRepository,
    private val mailAttachmentRepository: MailAttachmentRepository,
    private val emailSuppressionService: EmailSuppressionService
) {
    fun preview(inboundProcessingId: Long): AutoReplyPreviewResult {
        val record = inboundMailProcessingRepository.findById(inboundProcessingId)
            .orElseThrow { IllegalArgumentException("Inbound mail processing not found: $inboundProcessingId") }

        val cleanedBody = record.cleanedBody?.takeIf { it.isNotBlank() }
            ?: mailBodyCleaner.clean(record.body.orEmpty())
        val classification = inboundIntentClassifier.classify(cleanedBody, record.subject)
        val attachmentIntentIgnored = mailAttachmentRepository
            .findAllByInboundProcessingIdOrderByCreatedAtAsc(inboundProcessingId)
            .isNotEmpty()

        val contactId = record.expertContactId
        val wouldBeBlockedBy = buildWouldBeBlockedBy(contactId, record.fromEmail)

        val base = AutoReplyPreviewResult(
            previewKind = AutoReplyPreviewKind.MANUAL_HANDOFF,
            intentCode = classification.intentCode,
            autoAction = classification.autoAction,
            confidence = classification.confidence,
            matchedKeywords = classification.matchedKeywords,
            wouldBeBlockedBy = wouldBeBlockedBy,
            attachmentIntentIgnored = attachmentIntentIgnored
        )

        return when (classification.autoAction) {
            AutoIntentAction.MANUAL_REVIEW -> base.copy(
                previewKind = AutoReplyPreviewKind.MANUAL_HANDOFF,
                reason = manualReviewReason(classification.intentCode)
            )

            AutoIntentAction.CLOSE -> base.copy(
                previewKind = AutoReplyPreviewKind.MANUAL_HANDOFF,
                reason = "INTENT_${classification.intentCode.name}"
            )

            AutoIntentAction.SEND_MEETING_INVITATION -> {
                val account = mailSenderAccountService.getEnabledAccount(record.senderAccountCode)
                val rendered = mailComposeTemplateService.renderByCode(
                    templateCode = "MEETING_INVITATION",
                    variables = mailTemplateVariables(account)
                )
                val meetingAlreadySent = contactId != null && hasMeetingInvitation(contactId)
                base.copy(
                    previewKind = if (meetingAlreadySent) {
                        AutoReplyPreviewKind.MEETING_ALREADY_SENT
                    } else {
                        AutoReplyPreviewKind.MEETING_INVITATION
                    },
                    replySubject = rendered.subject.ifBlank { "Re: ${record.subject.orEmpty()}".trim() },
                    replyBody = rendered.body
                )
            }

            AutoIntentAction.QA -> {
                val match = qaMatchService.match(cleanedBody)
                when {
                    match == null || !match.autoReplyEnabled || match.handoffRequired -> base.copy(
                        previewKind = AutoReplyPreviewKind.QA_NO_MATCH,
                        reason = "QA_NO_MATCH"
                    )

                    match.gapDetected -> base.copy(
                        previewKind = AutoReplyPreviewKind.QA_GAP,
                        reason = "QA_GAP"
                    )

                    else -> base.copy(
                        previewKind = AutoReplyPreviewKind.QA_AUTO_REPLIED,
                        replySubject = match.replySubject ?: "Re: ${record.subject.orEmpty()}".trim(),
                        replyBody = match.replyBody,
                        matchedRuleIds = match.matchedRuleIds
                    )
                }
            }
        }
    }

    private fun buildWouldBeBlockedBy(contactId: Long?, fromEmail: String): List<String> {
        val blocked = mutableListOf<String>()
        if (emailSuppressionService.isSuppressed(fromEmail)) {
            blocked += "RECIPIENT_UNSUBSCRIBED"
        }
        if (contactId == null) {
            return blocked
        }
        val contact = expertContactRepository.findById(contactId).orElse(null) ?: return blocked
        if (!contact.autoReplyEnabled) {
            blocked += "AUTO_REPLY_DISABLED"
        }
        if (contact.currentStatus == ConversationStatus.MANUAL_HANDOFF.name) {
            blocked += "MANUAL_HANDOFF_STATUS"
        }
        if (!hasIntroductionInquiry(contactId)) {
            blocked += "INTRODUCTION_NOT_SENT"
        }
        return blocked
    }

    private fun hasIntroductionInquiry(contactId: Long): Boolean =
        mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
            expertContactId = contactId,
            direction = "OUTBOUND",
            mailType = "INTRODUCTION"
        )

    private fun hasMeetingInvitation(contactId: Long): Boolean =
        mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
            expertContactId = contactId,
            direction = "OUTBOUND",
            mailType = "MEETING_INVITATION"
        )

    private fun manualReviewReason(intentCode: InboundIntentCode): String =
        when (intentCode) {
            InboundIntentCode.MEETING_TIME_PROVIDED,
            InboundIntentCode.MEETING_REQUESTED -> "CONFIRM_MEETING"

            InboundIntentCode.CV_ATTACHED,
            InboundIntentCode.DOCS_ATTACHED,
            InboundIntentCode.PASSPORT_UPDATED -> "REVIEW_DOCUMENT"

            InboundIntentCode.ASK_FUNDING,
            InboundIntentCode.ASK_CONFIDENTIALITY -> "HANDLE_RISKY_QUESTION"

            else -> "REVIEW_INBOUND_INTENT_${intentCode.name}"
        }

    private fun mailTemplateVariables(account: MailSenderAccount): Map<String, String> =
        mapOf(
            "senderEmail" to account.senderEmail,
            "senderName" to account.senderName,
            "senderTitle" to account.senderTitle.orEmpty(),
            "teamName" to account.teamName.orEmpty(),
            "countryName" to account.countryName.orEmpty(),
            "senderDisplayName" to account.senderDisplayName.orEmpty()
        )
}
