package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import java.time.LocalDateTime

interface MailReceiveService {
    fun fetchUnread(account: MailSenderAccount, maxMessages: Int): List<ReceivedMail>

    fun markSeen(account: MailSenderAccount, imapUid: Long)
}

data class ReceivedMail(
    val imapUid: Long,
    val from: String,
    val subject: String?,
    val body: String,
    val messageId: String?,
    val inReplyTo: String?,
    val receivedAt: LocalDateTime
)
