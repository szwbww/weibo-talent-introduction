package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.stereotype.Service
import java.util.Properties
import javax.mail.Folder
import javax.mail.Session

@Service
class MailAccountConnectivityService(
    private val mailSenderAccountService: MailSenderAccountService
) {
    fun testAccount(accountCode: String): MailAccountConnectivityResult {
        val account = mailSenderAccountService.getAccount(accountCode)
        val smtp = testSmtp(account)
        val imap = testImap(account)
        return MailAccountConnectivityResult(
            accountCode = account.accountCode,
            smtp = smtp,
            imap = imap,
            passed = smtp.passed && imap.passed
        )
    }

    private fun testSmtp(account: MailSenderAccount): MailProtocolConnectivityResult =
        runCatching {
            JavaMailSenderImpl().apply {
                host = account.smtpHost
                port = account.smtpPort
                username = account.smtpUsername
                password = account.smtpPassword
                javaMailProperties = smtpProperties(account.smtpPort)
            }.testConnection()
        }.fold(
            onSuccess = {
                MailProtocolConnectivityResult(
                    protocol = "SMTP",
                    host = account.smtpHost,
                    port = account.smtpPort,
                    passed = true,
                    message = "SMTP connection succeeded"
                )
            },
            onFailure = { ex ->
                MailProtocolConnectivityResult(
                    protocol = "SMTP",
                    host = account.smtpHost,
                    port = account.smtpPort,
                    passed = false,
                    message = ex.message ?: ex.javaClass.simpleName
                )
            }
        )

    private fun testImap(account: MailSenderAccount): MailProtocolConnectivityResult =
        runCatching {
            val session = Session.getInstance(imapProperties(account.imapPort))
            val store = session.getStore("imap")
            store.connect(account.imapHost, account.imapPort, account.imapUsername, account.imapPassword)
            store.use { connectedStore ->
                val inbox = connectedStore.getFolder("INBOX")
                inbox.open(Folder.READ_ONLY)
                inbox.close(false)
            }
        }.fold(
            onSuccess = {
                MailProtocolConnectivityResult(
                    protocol = "IMAP",
                    host = account.imapHost,
                    port = account.imapPort,
                    passed = true,
                    message = "IMAP connection succeeded"
                )
            },
            onFailure = { ex ->
                MailProtocolConnectivityResult(
                    protocol = "IMAP",
                    host = account.imapHost,
                    port = account.imapPort,
                    passed = false,
                    message = ex.message ?: ex.javaClass.simpleName
                )
            }
        )

    private fun smtpProperties(port: Int): Properties =
        Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.auth.mechanisms", "LOGIN")
            put("mail.smtp.connectiontimeout", "10000")
            put("mail.smtp.timeout", "10000")
            put("mail.smtp.writetimeout", "10000")
            if (port == 465) {
                put("mail.smtp.ssl.enable", "true")
            } else {
                put("mail.smtp.starttls.enable", "true")
            }
        }

    private fun imapProperties(port: Int): Properties =
        Properties().apply {
            put("mail.imap.connectiontimeout", "10000")
            put("mail.imap.timeout", "10000")
            if (port == 993) {
                put("mail.imap.ssl.enable", "true")
            }
        }
}

data class MailAccountConnectivityResult(
    val accountCode: String,
    val smtp: MailProtocolConnectivityResult,
    val imap: MailProtocolConnectivityResult,
    val passed: Boolean
)

data class MailProtocolConnectivityResult(
    val protocol: String,
    val host: String,
    val port: Int,
    val passed: Boolean,
    val message: String
)

private inline fun <T : AutoCloseable, R> T.use(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        close()
    }
}
