package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.controller.MailboxItemResponse
import com.weibo.talentintroduction.mail.controller.MailboxListResponse
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
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
        page: Int,
        size: Int
    ): MailboxListResponse {
        val activeAccounts = senderAccountRepository.findAllByEnabledTrue()
        val activeCodes = activeAccounts.map { it.accountCode }
        if (activeCodes.isEmpty()) return MailboxListResponse(emptyList(), 0)

        // I-2: If accountCode is specified, it must be in the active lists
        if (accountCode != null && accountCode !in activeCodes) {
            return MailboxListResponse(emptyList(), 0)
        }

        val offset = page.toLong() * size
        val rows = mailRecordRepository.listMailbox(
            accountCodes = activeCodes,
            direction = direction,
            accountCode = accountCode,
            keyword = keyword,
            recipientEmail = recipientEmail,
            startTime = startTime,
            endTime = endTime,
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
            endTime = endTime
        )

        val items = rows.map { row ->
            val timestamp = (row.sentAt ?: row.receivedAt)?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            MailboxItemResponse(
                id = row.id,
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
                timestamp = timestamp
            )
        }

        return MailboxListResponse(items, total)
    }
}
