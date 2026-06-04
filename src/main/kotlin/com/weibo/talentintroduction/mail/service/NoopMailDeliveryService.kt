package com.weibo.talentintroduction.mail.service

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedDeque

@Service
@Primary
@Profile("simulator")
class NoopMailDeliveryService : MailDeliveryService {
    private val log = LoggerFactory.getLogger(NoopMailDeliveryService::class.java)
    private val ringBuffer = ConcurrentLinkedDeque<SimulatedOutbound>()
    private val maxBuffered = 200

    override fun send(account: com.weibo.talentintroduction.mail.domain.MailSenderAccount, mail: ComposedMail): DeliveredMail {
        require(account.accountCode == "SIMULATOR_NOOP" || mail.to.startsWith("sim+")) {
            "NoopMailDeliveryService refusing to send: recipient must be sim+* in simulator profile, got ${mail.to}"
        }
        val messageId = "noop-${UUID.randomUUID()}@simulator.local"
        ringBuffer.addFirst(
            SimulatedOutbound(
                ts = LocalDateTime.now(),
                accountCode = account.accountCode,
                to = mail.to,
                subject = mail.subject,
                body = mail.body,
                messageId = messageId
            )
        )
        while (ringBuffer.size > maxBuffered) ringBuffer.pollLast()
        log.info("[simulator] noop send to={} subject={} messageId={}", mail.to, mail.subject, messageId)
        return DeliveredMail(messageId = messageId, status = "SIMULATED")
    }

    fun snapshot(): List<SimulatedOutbound> = ringBuffer.toList()
}

data class SimulatedOutbound(
    val ts: LocalDateTime,
    val accountCode: String,
    val to: String,
    val subject: String,
    val body: String,
    val messageId: String
)
