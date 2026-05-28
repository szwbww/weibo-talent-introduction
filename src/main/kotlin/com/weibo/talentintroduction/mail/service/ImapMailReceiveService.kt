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

@Service
class ImapMailReceiveService : MailReceiveService {
    override fun fetchUnread(account: MailSenderAccount, maxMessages: Int): List<ReceivedMail> {
        require(maxMessages in 1..100) { "maxMessages must be between 1 and 100" }

        val session = Session.getInstance(imapProperties(account.imapPort))
        val store = session.getStore("imap")
        store.connect(account.imapHost, account.imapPort, account.imapUsername, account.imapPassword)

        return store.use { connectedStore ->
            val inbox = connectedStore.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)
            inbox.use { folder ->
                val uidFolder = folder as? UIDFolder
                    ?: error("IMAP INBOX does not support UID lookup")
                folder.messages
                    .asSequence()
                    .filterNot { it.flags.contains(Flags.Flag.SEEN) }
                    .take(maxMessages)
                    .map { message -> message.toReceivedMail(uidFolder.getUID(message)) }
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
                ?: LocalDateTime.now()
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
