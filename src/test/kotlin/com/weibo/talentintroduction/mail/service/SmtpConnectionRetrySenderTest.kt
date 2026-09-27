package com.weibo.talentintroduction.mail.service

import com.sun.mail.util.MailConnectException
import com.sun.mail.util.SocketConnectException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.mail.MailAuthenticationException
import org.springframework.mail.MailSendException
import java.net.SocketTimeoutException
import javax.mail.*

class SmtpConnectionRetrySenderTest {
    @Test
    fun `connection failure retries once before sending exactly one message`() {
        val fixture = Fixture(listOf(connectTimeout(), null))
        fixture.send()
        assertEquals(2, fixture.connections)
        assertEquals(listOf(15_000L), fixture.delays)
        assertEquals(1, fixture.submissions)
    }

    @Test
    fun `two connection failures do not submit or attempt a third connection`() {
        val fixture = Fixture(listOf(connectTimeout(), connectTimeout()))
        assertThrows(MailSendException::class.java) { fixture.send() }
        assertEquals(2, fixture.connections)
        assertEquals(1, fixture.delays.size)
        assertEquals(0, fixture.submissions)
    }

    @Test
    fun `authentication failure is never retried`() {
        val fixture = Fixture(listOf(AuthenticationFailedException("invalid credentials")))
        assertThrows(MailAuthenticationException::class.java) { fixture.send() }
        assertEquals(1, fixture.connections)
        assertTrue(fixture.delays.isEmpty())
        assertEquals(0, fixture.submissions)
    }

    @Test
    fun `untyped timeout does not qualify for connection retry`() {
        val fixture = Fixture(listOf(MessagingException("Read timed out", SocketTimeoutException())))
        assertThrows(MailSendException::class.java) { fixture.send() }
        assertEquals(1, fixture.connections)
        assertTrue(fixture.delays.isEmpty())
    }

    @Test
    fun `timeout after submission never reconnects or resends`() {
        val fixture = Fixture(listOf(null), MessagingException("Read timed out", SocketTimeoutException()))
        assertThrows(MailSendException::class.java) { fixture.send() }
        assertEquals(1, fixture.connections)
        assertEquals(1, fixture.submissions)
        assertTrue(fixture.delays.isEmpty())
    }

    @Test
    fun `submission timeout after a successful reconnect still never resends`() {
        val fixture = Fixture(listOf(connectTimeout(), null), MessagingException("Connection reset after DATA"))
        assertThrows(MailSendException::class.java) { fixture.send() }
        assertEquals(2, fixture.connections)
        assertEquals(1, fixture.submissions)
        assertEquals(1, fixture.delays.size)
    }

    @Test
    fun `interrupted retry preserves interruption and does not reconnect`() {
        val fixture = Fixture(listOf(connectTimeout()), interruptDelay = true)
        try {
            assertThrows(MailSendException::class.java) { fixture.send() }
            assertTrue(Thread.currentThread().isInterrupted)
            assertEquals(1, fixture.connections)
            assertEquals(0, fixture.submissions)
        } finally {
            Thread.interrupted()
        }
    }

    private fun connectTimeout() = MailConnectException(
        SocketConnectException("connect timed out", SocketTimeoutException(), "smtp.gmail.com", 465, 60_000)
    )

    /** Real Spring send lifecycle, fake transport: no network or real email. */
    private class Fixture(
        private val connectFailures: List<MessagingException?>,
        private val sendFailure: MessagingException? = null,
        private val interruptDelay: Boolean = false
    ) {
        var connections = 0
        var submissions = 0
        val delays = mutableListOf<Long>()
        private val sender = object : SmtpConnectionRetrySender({
            delays.add(it)
            if (interruptDelay) throw InterruptedException()
        }) {
            override fun getTransport(session: Session): Transport = object : Transport(session, null) {
                override fun protocolConnect(host: String?, port: Int, user: String?, password: String?): Boolean {
                    val failure = connectFailures[connections++]
                    if (failure != null) throw failure
                    return true
                }

                override fun sendMessage(message: Message, addresses: Array<out Address>) {
                    submissions++
                    if (sendFailure != null) throw sendFailure
                }
            }
        }.apply {
            host = "smtp.example.com"
            port = 465
            username = "sender@example.com"
            password = "test-only"
        }

        fun send() {
            sender.send(sender.createMimeMessage().apply {
                setFrom("sender@example.com")
                setRecipients(Message.RecipientType.TO, "recipient@example.com")
                setText("Test")
            })
        }
    }
}
