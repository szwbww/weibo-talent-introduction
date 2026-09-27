package com.weibo.talentintroduction.mail.service

import com.sun.mail.util.MailConnectException
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSenderImpl
import javax.mail.MessagingException
import javax.mail.Transport

internal const val SMTP_CONNECT_TIMEOUT_MS = 60_000
internal const val SMTP_IO_TIMEOUT_MS = 120_000

/** Only reconnect before a message can be submitted. Never retry send/sendMessage. */
internal open class SmtpConnectionRetrySender(
    private val retryDelay: (Long) -> Unit = { Thread.sleep(it) }
) : JavaMailSenderImpl() {
    override fun connectTransport(): Transport = try {
        super.connectTransport()
    } catch (e: MailConnectException) {
        // The typed exception proves socket establishment failed. A generic timeout
        // (especially after DATA) cannot prove non-delivery and must not be retried.
        if (Thread.currentThread().isInterrupted) throw e
        log.warn("SMTP connection failed for {}:{}; retrying connection once in 15 seconds", host, port)
        try {
            retryDelay(15_000L)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw MessagingException("SMTP connection retry interrupted", interrupted)
        }
        if (Thread.currentThread().isInterrupted) throw e
        super.connectTransport()
    }

    private companion object {
        val log = LoggerFactory.getLogger(SmtpConnectionRetrySender::class.java)
    }
}
