package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository
import com.weibo.talentintroduction.mail.service.CalendarAttachmentCodec
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.util.NoSuchElementException

/**
 * 03 (I-3)：历史已发送日历原件下载。
 *
 * GET /api/mail/conversations/{contactId}/messages/{mailRecordId}/calendar-attachment
 *
 * 只暴露 mail_record 中 OUTBOUND + MANUAL_RICH_REPLY + SENT、expertContactId 等于 path、
 * 且 senderAccountCode 在当前会话 active 范围（与 MailboxConversationService.activeAccountCodes
 * 同款 findAllByAccountCodeNot(SIMULATOR_ACCOUNT_CODE)，无 enabled 附加条件、无新造用户级
 * 权限）的存档快照。快照统一经 01 CalendarAttachmentCodec.parseOrNull 校验（空/损坏/错误
 * hash 返回 null）；不存在/不匹配/未 SENT/空快照/损坏一律 404 固定文案「日历附件不可用」。
 * 返回的是当时存储的 UTF-8 原件字节 —— 绝不从配置/当前模板重新生成，不读客户端文件路径，
 * 不改变任何处理状态。该 path 沿全站 /api/ AuthInterceptor，不设登录豁免。
 */
@RestController
@RequestMapping("/api/mail/conversations")
class CalendarAttachmentController(
    private val mailRecordRepository: MailRecordRepository,
    private val mailSenderAccountRepository: MailSenderAccountRepository
) {
    companion object {
        private const val SEND_STATUS_SENT = "SENT"
        private const val MANUAL_RICH_REPLY_MAIL_TYPE = "MANUAL_RICH_REPLY"
        private const val NOT_AVAILABLE_MESSAGE = "日历附件不可用"
    }

    @GetMapping("/{contactId}/messages/{mailRecordId}/calendar-attachment")
    fun download(
        @PathVariable contactId: Long,
        @PathVariable mailRecordId: Long
    ): ResponseEntity<ByteArray> {
        val record = mailRecordRepository.findById(mailRecordId).orElse(null)
        val snapshot = if (record == null ||
            record.direction != MailboxConversationRepository.DIRECTION_OUTBOUND ||
            record.mailType != MANUAL_RICH_REPLY_MAIL_TYPE ||
            record.sendStatus != SEND_STATUS_SENT ||
            record.expertContactId != contactId
        ) {
            null
        } else {
            CalendarAttachmentCodec.parseOrNull(record.calendarAttachmentJson)
        }
        val accountInScope = record?.senderAccountCode != null &&
            record.senderAccountCode in mailSenderAccountRepository
                .findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
                .map { it.accountCode }
                .toSet()
        if (snapshot == null || !accountInScope) {
            // 统一 404「日历附件不可用」：走 GlobalExceptionHandler 的 NOT_FOUND 通道
            // （与既有 "Expert contact not found" 同款约定），不落通用 500。
            throw NoSuchElementException(NOT_AVAILABLE_MESSAGE)
        }
        val bytes = snapshot.icsText.toByteArray(Charsets.UTF_8)
        val headers = HttpHeaders().apply {
            contentType = MediaType.parseMediaType(snapshot.contentType)
            contentDisposition = ContentDisposition.attachment()
                .filename(snapshot.filename, StandardCharsets.UTF_8)
                .build()
            contentLength = bytes.size.toLong()
            // 契约固定文案 private,no-store（不允许缓存）。
            set(HttpHeaders.CACHE_CONTROL, "private,no-store")
        }
        headers.set("X-Content-Type-Options", "nosniff")
        return ResponseEntity(bytes, headers, HttpStatus.OK)
    }
}
