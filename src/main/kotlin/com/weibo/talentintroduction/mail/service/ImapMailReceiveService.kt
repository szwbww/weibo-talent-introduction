package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Properties
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeUtility

@Service
class ImapMailReceiveService : MailReceiveService {
    override fun fetchInboundSince(
        account: MailSenderAccount,
        afterUid: Long,
        maxMessages: Int
    ): InboundFetchResult {
        require(maxMessages in 1..100) { "maxMessages must be between 1 and 100" }
        require(afterUid >= 0) { "afterUid must be non-negative" }

        val session = Session.getInstance(imapProperties(account.imapPort))
        val store = session.getStore("imap")
        store.connect(account.imapHost, account.imapPort, account.imapUsername, account.imapPassword)

        return store.use { connectedStore ->
            val inbox = connectedStore.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
            inbox.use { folder ->
                val uidFolder = folder as? UIDFolder
                    ?: error("IMAP INBOX does not support UID lookup")
                val uidValidity = uidFolder.uidValidity
                val startUid = if (afterUid == 0L) 1L else afterUid + 1
                val messages = uidFolder.getMessagesByUID(startUid, UIDFolder.LASTUID)
                    .asSequence()
                    .mapNotNull { message ->
                        val uid = uidFolder.getUID(message)
                        if (uid <= afterUid) null else message to uid
                    }
                    .sortedBy { it.second }
                    .take(maxMessages)
                    .map { (message, uid) -> message.toReceivedMail(uid) }
                    .toList()
                InboundFetchResult(
                    mails = messages,
                    uidValidity = uidValidity,
                    maxUidInWindow = messages.maxOfOrNull { it.imapUid } ?: afterUid
                )
            }
        }
    }

    override fun fetchByUids(account: MailSenderAccount, uids: List<Long>): List<ReceivedMail> {
        require(uids.isNotEmpty()) { "uids must not be empty" }
        require(uids.all { it > 0 }) { "each uid must be positive" }

        val session = Session.getInstance(imapProperties(account.imapPort))
        val store = session.getStore("imap")
        store.connect(account.imapHost, account.imapPort, account.imapUsername, account.imapPassword)

        return store.use { connectedStore ->
            val inbox = connectedStore.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
            inbox.use { folder ->
                val uidFolder = folder as? UIDFolder
                    ?: error("IMAP INBOX does not support UID lookup")
                uidFolder.getMessagesByUID(uids.toLongArray())
                    .asSequence()
                    .mapNotNull { message ->
                        message?.let { msg ->
                            msg.toReceivedMail(uidFolder.getUID(msg))
                        }
                    }
                    .sortedBy { it.imapUid }
                    .toList()
            }
        }
    }

    /**
     * Fetches UNSEEN inbox messages as detached [MimeMessage] copies so callers can parse
     * multipart DSN content after the IMAP connection closes.
     */
    fun fetchUnseenMessages(account: MailSenderAccount, maxMessages: Int = 100): List<MimeMessage> {
        require(maxMessages in 1..100) { "maxMessages must be between 1 and 100" }

        val session = Session.getInstance(imapProperties(account.imapPort))
        val store = session.getStore("imap")
        store.connect(account.imapHost, account.imapPort, account.imapUsername, account.imapPassword)

        return store.use { connectedStore ->
            val inbox = connectedStore.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            inbox.use { folder ->
                folder.messages
                    .asSequence()
                    .filterNot { it.flags.contains(Flags.Flag.SEEN) }
                    .take(maxMessages)
                    .map { message -> MimeMessage(message as MimeMessage) }
                    .toList()
            }
        }
    }

    override fun markSeen(account: MailSenderAccount, imapUid: Long) {
        val session = Session.getInstance(imapProperties(account.imapPort))
        val store = session.getStore("imap")
        store.connect(account.imapHost, account.imapPort, account.imapUsername, account.imapPassword)

        store.use { connectedStore ->
            val inbox = connectedStore.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
            inbox.use { folder ->
                val uidFolder = folder as? UIDFolder
                    ?: error("IMAP INBOX does not support UID lookup")
                uidFolder.getMessageByUID(imapUid)?.setFlag(Flags.Flag.SEEN, true)
            }
        }
    }

    private fun Message.toReceivedMail(imapUid: Long): ReceivedMail =
        ReceivedMail(
            imapUid = imapUid,
            from = extractFrom(),
            subject = subject,
            body = extractBody(this),
            messageId = getHeader("Message-ID")?.firstOrNull(),
            inReplyTo = getHeader("In-Reply-To")?.firstOrNull(),
            receivedAt = receivedDate
                ?.toInstant()
                ?.atZone(ZoneId.systemDefault())
                ?.toLocalDateTime()
                ?: LocalDateTime.now(),
            attachments = extractAttachments(this)
        )

    private fun Message.extractFrom(): String =
        from
            ?.filterIsInstance<InternetAddress>()
            ?.firstOrNull()
            ?.address
            ?: error("Received mail has no sender address")

    private fun extractBody(part: Part): String {
        if (part.isMimeType("text/plain")) {
            return part.content as? String ?: ""
        }
        if (part.isMimeType("text/html")) {
            return stripHtml(part.content as? String ?: "")
        }
        if (part.content is Multipart) {
            val multipart = part.content as Multipart
            return (0 until multipart.count)
                .asSequence()
                .map { extractBody(multipart.getBodyPart(it)) }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
        }
        return ""
    }

    private fun extractAttachments(part: Part): List<ReceivedMailAttachment> {
        if (part.isMimeType("multipart/*")) {
            val content = part.content as Multipart
            return (0 until content.count)
                .flatMap { extractAttachments(content.getBodyPart(it)) }
        }

        val fileName = part.fileName?.let { MimeUtility.decodeText(it) }
        val disposition = part.disposition
        val isAttachment = Part.ATTACHMENT.equals(disposition, ignoreCase = true) ||
            Part.INLINE.equals(disposition, ignoreCase = true) && !fileName.isNullOrBlank()
        if (!isAttachment || fileName.isNullOrBlank()) {
            return emptyList()
        }

        val bytes = part.inputStream.use { it.readBytes() }
        return listOf(
            ReceivedMailAttachment(
                fileName = fileName,
                contentType = part.contentType?.substringBefore(";")?.trim(),
                content = bytes
            )
        )
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun imapProperties(port: Int): Properties =
        Properties().apply {
            put("mail.imap.connectiontimeout", "10000")
            put("mail.imap.timeout", "10000")
            put("mail.imap.peek", "true")
            if (port == 993) {
                put("mail.imap.ssl.enable", "true")
            }
        }
}

private inline fun <T : AutoCloseable, R> T.use(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        close()
    }
}
