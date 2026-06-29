package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import java.time.LocalDateTime

interface MailReceiveService {
    fun fetchInboundSince(account: MailSenderAccount, afterUid: Long, maxMessages: Int): InboundFetchResult

    fun fetchByUids(account: MailSenderAccount, uids: List<Long>): List<ReceivedMail>

    fun markSeen(account: MailSenderAccount, imapUid: Long)
}

data class InboundFetchResult(
    val mails: List<ReceivedMail>,
    val uidValidity: Long,
    val maxUidInWindow: Long
)

data class ReceivedMail(
    val imapUid: Long,
    val from: String,
    val subject: String?,
    val body: String,
    val messageId: String?,
    val inReplyTo: String?,
    val receivedAt: LocalDateTime,
    val attachments: List<ReceivedMailAttachment> = emptyList()
)

data class ReceivedMailAttachment(
    val fileName: String,
    val contentType: String?,
    val content: ByteArray
)
