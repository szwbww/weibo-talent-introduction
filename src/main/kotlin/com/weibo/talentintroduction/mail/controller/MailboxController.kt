package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.mail.service.MailboxService
import com.weibo.talentintroduction.mail.service.TagView
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

data class MailboxItemResponse(
    val id: Long,
    val source: String,
    val expertContactId: Long?,
    val direction: String,
    val mailType: String,
    val senderAccountCode: String?,
    val triggeredBy: String?,
    val isSystemSent: Boolean,
    val expertEmail: String?,
    val expertName: String?,
    val subject: String?,
    val bodyPreview: String?,
    val hasAttachment: Boolean,
    val sendStatus: String?,
    val timestamp: String?,
    val tags: List<String>,
    val processStatus: String?,
    val reasonType: String?,
    val inboundProcessingId: Long?,
    val inboundTags: List<TagView> = emptyList()
)

data class MailboxListResponse(
    val items: List<MailboxItemResponse>,
    val totalCount: Long
)

data class MailboxExpertGroupResponse(
    val expertContactId: Long,
    val expertName: String?,
    val expertEmail: String,
    val expertOrcidId: String,
    val operatorStatus: String,
    val expertIndexLevel: String,
    val pendingCount: Long,
    val mails: List<MailboxItemResponse>
)

data class MailboxExpertGroupListResponse(
    val groups: List<MailboxExpertGroupResponse>,
    val totalCount: Long
)

data class MailboxDetailResponse(
    val id: Long,
    val source: String,
    val expertContactId: Long?,
    val direction: String,
    val mailType: String,
    val senderAccountCode: String?,
    val triggeredBy: String?,
    val isSystemSent: Boolean,
    val expertEmail: String?,
    val expertName: String?,
    val subject: String?,
    val bodyPreview: String?,
    val body: String?,
    val hasAttachment: Boolean,
    val sendStatus: String?,
    val timestamp: String?,
    val processStatus: String?,
    val reasonType: String?,
    val inboundProcessingId: Long?,
    val inboundTags: List<TagView> = emptyList(),
    val expertOrcidId: String? = null,
    val expertIndexLevel: String? = null
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
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(defaultValue = "false") pending: Boolean,
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
            pending = pending,
            page = page,
            size = size.coerceIn(1, 100)
        )
    }

    @GetMapping("/pending-by-expert")
    fun listPendingByExpert(
        @RequestParam(required = false) accountCode: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) recipientEmail: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): MailboxExpertGroupListResponse = mailboxService.listPendingByExpert(
        accountCode = accountCode,
        keyword = keyword,
        recipientEmail = recipientEmail,
        page = page,
        size = size.coerceIn(1, 100)
    )

    @GetMapping("/{source}/{id}")
    fun detail(
        @PathVariable source: String,
        @PathVariable id: Long
    ): MailboxDetailResponse = mailboxService.getMailboxDetail(source, id)
}
