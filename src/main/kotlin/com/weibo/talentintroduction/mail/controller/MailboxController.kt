package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.mail.service.MailboxService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

data class MailboxItemResponse(
    val id: Long,
    val expertContactId: Long,
    val direction: String,
    val mailType: String,
    val senderAccountCode: String?,
    val triggeredBy: String?,       // I-3: Direct pass-through
    val isSystemSent: Boolean,      // I-3: triggeredBy == "SYSTEM"
    val expertEmail: String?,
    val expertName: String?,
    val subject: String?,
    val bodyPreview: String?,       // I-5: Truncated
    val hasAttachment: Boolean,
    val sendStatus: String?,
    val timestamp: String?          // ISO format, COALESCE(sentAt, receivedAt)
)

data class MailboxListResponse(
    val items: List<MailboxItemResponse>,
    val totalCount: Long            // I-4
)

@RestController
@RequestMapping("/api/mail/mailbox")
class MailboxController(private val mailboxService: MailboxService) {

    @GetMapping
    fun list(
        @RequestParam(required = false) direction: String?,
        @RequestParam(required = false) accountCode: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) recipientEmail: String?,
        @RequestParam(required = false) startDate: String?,   // yyyy-MM-dd
        @RequestParam(required = false) endDate: String?,     // yyyy-MM-dd
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): MailboxListResponse {
        val startTime = startDate?.let { LocalDate.parse(it).atStartOfDay() }
        val endTime = endDate?.let { LocalDate.parse(it).plusDays(1).atStartOfDay() }
        return mailboxService.listMailbox(
            direction = direction,
            accountCode = accountCode,
            keyword = keyword,
            recipientEmail = recipientEmail,
            startTime = startTime,
            endTime = endTime,
            page = page,
            size = size.coerceIn(1, 100)
        )
    }
}
