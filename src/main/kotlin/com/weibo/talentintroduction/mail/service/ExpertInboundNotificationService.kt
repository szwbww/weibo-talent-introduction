package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.repository.ExpertInboundNotificationClaim
import com.weibo.talentintroduction.mail.repository.ExpertInboundNotificationRepository
import com.weibo.talentintroduction.mail.repository.ExpertInboundNotificationSettingsResponse
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

class ExpertInboundNotificationNotConfiguredException : IllegalStateException("未配置企业微信机器人")

data class ExpertInboundNotificationSendResult(val success: Boolean, val retryable: Boolean, val errorCode: String?)

@Service
class ExpertInboundNotificationService(
    private val repository: ExpertInboundNotificationRepository,
    private val inboundRepository: InboundMailProcessingRepository,
    private val contactRepository: ExpertContactRepository,
    private val cleaner: MailBodyCleaner,
    private val mapper: ObjectMapper,
    @Value("\${talent-introduction.wecom-inbound.webhook:}") webhook: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val target = validWebhook(webhook)
    private val worker = UUID.randomUUID().toString()
    private var executor: ScheduledExecutorService? = null
    private var nextCleanupNanos = 0L
    internal val httpClient = RestTemplate(object : SimpleClientHttpRequestFactory() {
        override fun prepareConnection(connection: HttpURLConnection, httpMethod: String) {
            super.prepareConnection(connection, httpMethod)
            connection.instanceFollowRedirects = false
        }
    }.apply { setConnectTimeout(3000); setReadTimeout(5000) }).apply {
        // Inspect status ourselves; never let a HTTP exception retain a credential-bearing URI.
        errorHandler = object : org.springframework.web.client.ResponseErrorHandler {
            override fun hasError(response: org.springframework.http.client.ClientHttpResponse) = false
            override fun handleError(response: org.springframework.http.client.ClientHttpResponse) = Unit
        }
    }

    fun settings(): ExpertInboundNotificationSettingsResponse {
        val setting = repository.settings() // Database errors propagate, never a fabricated disabled state.
        return ExpertInboundNotificationSettingsResponse(setting.enabled, target != null, setting.generation, setting.updatedAt)
    }

    fun setEnabled(enabled: Boolean, username: String): ExpertInboundNotificationSettingsResponse {
        if (enabled && target == null) {
            throw ExpertInboundNotificationNotConfiguredException()
        }
        val setting = repository.setEnabled(enabled, username)
        return ExpertInboundNotificationSettingsResponse(setting.enabled, target != null, setting.generation, setting.updatedAt)
    }

    fun enqueueBestEffort(ownerCode: String, uidValidity: Long, uid: Long) {
        try {
            repository.inReceiptSavepoint {
                if (ownerCode.isBlank() || uidValidity <= 0 || uid <= 0) return@inReceiptSavepoint
                val source = inboundRepository.findByMailboxOwnerCodeAndUidValidityAndImapUid(ownerCode, uidValidity, uid)
                    ?: return@inReceiptSavepoint
                if (source.senderAccountCode == "SIMULATOR_NOOP") return@inReceiptSavepoint
                val contactId = source.expertContactId ?: return@inReceiptSavepoint
                if (source.id == null || source.mailboxOwnerCode != ownerCode || source.uidValidity != uidValidity || source.imapUid != uid) return@inReceiptSavepoint
                val contact = contactRepository.findById(contactId).orElse(null) ?: return@inReceiptSavepoint
                repository.enqueue(source) { id -> message(id, source, contact.expertName) }
            }
        } catch (_: Exception) {
            // No exception object, mail content, mailbox credential or webhook in this diagnostic.
            log.warn("Expert inbound notification registration failed: NOTIFICATION_ENQUEUE_FAILED")
        }
    }

    @PostConstruct
    @Synchronized
    fun start() {
        if (executor != null) return
        executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "expert-inbound-notification").apply { isDaemon = true }
        }.also { it.scheduleWithFixedDelay({ tick() }, 1, 1, TimeUnit.SECONDS) }
    }

    @PreDestroy
    @Synchronized
    fun stop() {
        val current = executor ?: return
        current.shutdown()
        try {
            if (!current.awaitTermination(9, TimeUnit.SECONDS)) current.shutdownNow()
            if (current.isTerminated) repository.releaseWorker(worker)
        } catch (_: InterruptedException) {
            current.shutdownNow()
            Thread.currentThread().interrupt()
        } catch (_: Exception) {
            log.warn("Expert inbound notification shutdown failed: NOTIFICATION_SHUTDOWN_FAILED")
        } finally {
            executor = null
        }
    }

    /** The executor is dedicated; both receipt and shared Spring scheduler stay off this HTTP path. */
    internal fun tick() {
        try {
            if (System.nanoTime() >= nextCleanupNanos) {
                repository.cleanTerminal()
                nextCleanupNanos = System.nanoTime() + TimeUnit.HOURS.toNanos(1)
            }
            if (target == null) return
            val claim = repository.claim(worker) ?: return
            if (!repository.authorizeSend(worker, claim)) return
            val result = send(claim)
            repository.finish(claim, result.success, result.retryable, result.errorCode)
        } catch (_: Exception) {
            // Persisted leases recover claims after unexpected failures; keep the scheduler alive.
            log.warn("Expert inbound notification worker failed: NOTIFICATION_WORKER_FAILED")
        }
    }

    internal fun send(claim: ExpertInboundNotificationClaim): ExpertInboundNotificationSendResult {
        val uri = target ?: return ExpertInboundNotificationSendResult(false, false, "NOT_CONFIGURED")
        return try {
            httpClient.execute(uri, HttpMethod.POST, { request ->
                request.headers.contentType = MediaType.APPLICATION_JSON
                mapper.writeValue(request.body, mapOf("msgtype" to "text", "text" to mapOf("content" to claim.payload)))
            }, { response ->
                val status = response.rawStatusCode
                when {
                    status == 429 -> ExpertInboundNotificationSendResult(false, true, "HTTP_RATE_LIMIT")
                    status >= 500 -> ExpertInboundNotificationSendResult(false, true, "HTTP_SERVER_ERROR")
                    status != 200 -> ExpertInboundNotificationSendResult(false, false, if (status in 300..399) "HTTP_REDIRECT" else "HTTP_REJECTED")
                    else -> {
                        val bytes = response.body.readNBytes(8193)
                        if (bytes.size > 8192) ExpertInboundNotificationSendResult(false, false, "INVALID_RESPONSE")
                        else parseResponse(bytes)
                    }
                }
            }) ?: ExpertInboundNotificationSendResult(false, true, "EMPTY_RESPONSE")
        } catch (error: Exception) {
            val timeout = generateSequence<Throwable>(error) { it.cause }.take(8).any { it is SocketTimeoutException }
            ExpertInboundNotificationSendResult(false, true, if (timeout) "HTTP_TIMEOUT" else "HTTP_NETWORK_ERROR")
        }
    }

    private fun parseResponse(bytes: ByteArray): ExpertInboundNotificationSendResult = try {
        val code = mapper.readTree(bytes)?.get("errcode")
        when {
            code == null || !code.isIntegralNumber || !code.canConvertToInt() -> ExpertInboundNotificationSendResult(false, false, "INVALID_RESPONSE")
            code.intValue() == 0 -> ExpertInboundNotificationSendResult(true, false, null)
            code.intValue() == 45009 -> ExpertInboundNotificationSendResult(false, true, "WECOM_RATE_LIMIT")
            else -> ExpertInboundNotificationSendResult(false, false, "WECOM_REJECTED")
        }
    } catch (_: Exception) { ExpertInboundNotificationSendResult(false, false, "INVALID_RESPONSE") }

    internal fun message(id: Long, source: InboundMailProcessing, expertName: String?): String {
        val from = field(source.fromEmail, 200)
        val name = field(expertName, 80).ifBlank { field(source.fromEmail, 80) }
        val subject = field(source.subject, 200).ifBlank { "无主题" }
        val summary = field(cleaner.clean(source.cleanedBody ?: source.body), 160)
        val content = "专家来信\n通知编号：$id\n专家：$name\n发件邮箱：$from\n账号：${field(source.senderAccountCode, 80)}\n主题：$subject\n收信时间：${source.receivedAt}\n摘要：$summary"
        return utf8Prefix(content, 1800)
    }

    private fun field(value: String?, maxPoints: Int): String {
        val safe = StringBuilder()
        (value ?: "").codePoints().forEach { cp ->
            if (!Character.isISOControl(cp) && Character.getType(cp) != Character.FORMAT.toInt() && cp !in 0xD800..0xDFFF) safe.appendCodePoint(cp)
            else if (cp == 10 || cp == 13 || cp == 9) safe.append(' ')
        }
        // Text never declares mentioned_list, and neutralize the visible all-members token too.
        val text = safe.toString().replace("@all", "＠all", ignoreCase = true).trim()
        val count = minOf(maxPoints, text.codePointCount(0, text.length))
        return text.substring(0, text.offsetByCodePoints(0, count))
    }

    private fun utf8Prefix(value: String, maxBytes: Int): String {
        var index = 0
        var bytes = 0
        while (index < value.length) {
            val cp = value.codePointAt(index)
            val width = when { cp <= 0x7F -> 1; cp <= 0x7FF -> 2; cp <= 0xFFFF -> 3; else -> 4 }
            if (bytes + width > maxBytes) break
            bytes += width
            index += Character.charCount(cp)
        }
        return value.substring(0, index)
    }

    companion object {
        internal fun validWebhook(value: String): URI? = try {
            val uri = URI(value)
            val query = uri.rawQuery?.split('&') ?: emptyList()
            val key = query.singleOrNull()?.takeIf { it.startsWith("key=") }?.substring(4)
                ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
            if (uri.scheme != "https" || uri.host != "qyapi.weixin.qq.com" || uri.path != "/cgi-bin/webhook/send" ||
                uri.rawUserInfo != null || uri.rawFragment != null || uri.port !in listOf(-1, 443) ||
                key.isNullOrBlank() || key.any { Character.isISOControl(it) || it.isWhitespace() }) null else uri
        } catch (_: Exception) { null }
    }
}
