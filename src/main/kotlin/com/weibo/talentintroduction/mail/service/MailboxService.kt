package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.controller.MailboxItemResponse
import com.weibo.talentintroduction.mail.controller.MailboxListResponse
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.repository.MailboxRow
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class MailboxService(
    private val mailRecordRepository: MailRecordRepository,
    private val senderAccountRepository: MailSenderAccountRepository
) {
    fun listMailbox(
        direction: String?,
        accountCode: String?,
        keyword: String?,
        recipientEmail: String?,
        startTime: LocalDateTime?,
        endTime: LocalDateTime?,
        pending: Boolean,
        page: Int,
        size: Int
    ): MailboxListResponse {
        val activeAccounts = senderAccountRepository.findAllByEnabledTrue()
        val activeCodes = activeAccounts.map { it.accountCode }
        if (activeCodes.isEmpty()) return MailboxListResponse(emptyList(), 0)

        if (accountCode != null && accountCode !in activeCodes) {
            return MailboxListResponse(emptyList(), 0)
        }

        val onlyPending = if (pending) 1 else 0
        val offset = page.toLong() * size
        val rows = mailRecordRepository.listMailbox(
            accountCodes = activeCodes,
            direction = direction,
            accountCode = accountCode,
            keyword = keyword,
            recipientEmail = recipientEmail,
            startTime = startTime,
            endTime = endTime,
            onlyPending = onlyPending,
            limit = size,
            offset = offset
        )
        val total = mailRecordRepository.countMailbox(
            accountCodes = activeCodes,
            direction = direction,
            accountCode = accountCode,
            keyword = keyword,
            recipientEmail = recipientEmail,
            startTime = startTime,
            endTime = endTime,
            onlyPending = onlyPending
        )

        val items = rows.map { row ->
            val timestamp = (row.sentAt ?: row.receivedAt)?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            MailboxItemResponse(
                id = row.id,
                source = row.source,
                expertContactId = row.expertContactId,
                direction = row.direction,
                mailType = row.mailType,
                senderAccountCode = row.senderAccountCode,
                triggeredBy = row.triggeredBy,
                isSystemSent = row.triggeredBy == "SYSTEM",
                expertEmail = row.expertEmail,
                expertName = row.expertName,
                subject = row.subject,
                bodyPreview = row.bodyPreview,
                hasAttachment = row.hasAttachment != 0L,
                sendStatus = row.sendStatus,
                timestamp = timestamp,
                tags = computeTags(row),
                processStatus = row.processStatus,
                reasonType = row.reasonType,
                inboundProcessingId = row.inboundProcessingId
            )
        }

        return MailboxListResponse(items, total)
    }

    internal fun computeTags(row: MailboxRow): List<String> {
        val tags = mutableListOf<String>()
        if (row.expertContactId != null) {
            tags.add("专家")
        } else {
            tags.add("待匹配")
        }
        if (row.direction == "INBOUND") {
            tags.add("收件")
        } else {
            tags.add("发件")
        }
        if (row.direction == "OUTBOUND") {
            if (row.triggeredBy == "SYSTEM" ||
                row.matchedQaRuleId != null ||
                row.mailType in AUTO_REPLY_MAIL_TYPES
            ) {
                tags.add("自动回复")
            }
            if (row.triggeredBy in MANUAL_TRIGGER_TYPES || row.mailType == "MANUAL_QA_REPLY") {
                tags.add("手动回复")
            }
            if (row.mailType == "INTRODUCTION") {
                tags.add("首发")
            }
        }
        if (row.source == "INBOUND_PROCESSING" && row.processStatus == "MANUAL_REVIEW") {
            tags.add("待处理")
        }
        return tags
    }

    companion object {
        private val AUTO_REPLY_MAIL_TYPES = setOf("QA_REPLY", "MEETING_INVITATION", "MEETING_CONFIRMATION")
        private val MANUAL_TRIGGER_TYPES = setOf("OPERATOR", "MANUAL")
    }
}
