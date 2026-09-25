package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.repository.OpenTrackingFilter
import com.weibo.talentintroduction.mail.repository.MailOpenTrackingRepository
import com.weibo.talentintroduction.mail.repository.OpenTrackingReservation
import com.weibo.talentintroduction.mail.repository.OpenTrackingRow
import com.weibo.talentintroduction.mail.repository.OpenTrackingSnapshot
import com.weibo.talentintroduction.monitoring.service.MonitoringDateRangeResolver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64
import javax.mail.internet.InternetAddress

data class MailOpenTrackingSettings(val enabled: Boolean, val configured: Boolean, val baseUrl: String)
data class MailOpenTrackingReservation(val id: Long, val token: String, val url: String)

@Service
class MailOpenTrackingService(
    private val repository: MailOpenTrackingRepository,
    private val dates: MonitoringDateRangeResolver,
    @Value("\${talent-introduction.mail-open-tracking.base-url:}") private val configuredBaseUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()
    private val zone = ZoneId.of("Asia/Shanghai")

    fun settings(): MailOpenTrackingSettings = MailOpenTrackingSettings(
        repository.isEnabled(), validBaseUrl() != null, configuredBaseUrl.trim()
    )

    fun setEnabled(enabled: Boolean): MailOpenTrackingSettings {
        require(!enabled || validBaseUrl() != null) { "A valid HTTPS mail-open-tracking base URL is required" }
        repository.setEnabled(enabled, now())
        return settings()
    }

    /** Called outside the sender's transaction; the repository proxy commits this row independently. */
    fun reserve(recipient: String): MailOpenTrackingReservation? {
        val base = validBaseUrl() ?: return null
        if (!singleRecipient(recipient)) return null
        repeat(3) {
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            try {
                val row: OpenTrackingReservation = repository.reserve(token, recipient, now()) ?: return null
                return MailOpenTrackingReservation(row.id, row.token, "$base/t/mail-open/$token.gif")
            } catch (e: DuplicateKeyException) {
                log.warn("Mail open tracking token collision; retrying reservation")
            }
        }
        return null
    }

    fun recordSignal(token: String) {
        if (!TOKEN.matches(token)) return
        try {
            repository.recordSignal(token, now())
        } catch (e: Exception) {
            log.warn("Mail open tracking signal storage unavailable: {}", e.javaClass.simpleName)
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun readPage(
        from: LocalDate, to: LocalDate, senderAccountCode: String?, status: String,
        keyword: String?, pageSize: Int, pageOffset: Int
    ): OpenTrackingSnapshot {
        require(!from.isAfter(to)) { "from must not exceed to" }
        require(pageSize in 1..100) { "pageSize must be between 1 and 100" }
        require(pageOffset >= 0) { "pageOffset must be nonnegative" }
        require(status in STATUSES) { "Unsupported tracking status" }
        require(keyword == null || keyword.length <= 200) { "keyword must not exceed 200 characters" }
        val (start, end) = dates.resolveRange(from, to)
        return repository.readPage(OpenTrackingFilter(start, end, senderAccountCode, status, keyword, pageSize, pageOffset))
    }

    @Transactional(readOnly = true)
    fun detail(id: Long): OpenTrackingRow {
        if (id <= 0) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return repository.detail(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }

    private fun validBaseUrl(): String? {
        val value = configuredBaseUrl.trim().trimEnd('/')
        val uri = try { URI(value) } catch (_: Exception) { return null }
        if (!uri.isAbsolute || !uri.scheme.equals("https", ignoreCase = true) ||
            uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.rawQuery != null ||
            uri.rawFragment != null || uri.port !in -1..65535 || value.any { it.isWhitespace() }
        ) return null
        return value
    }

    private fun singleRecipient(value: String): Boolean {
        if (value.length !in 3..255 || value != value.trim() || value.any { it.isWhitespace() || it == ',' || it == ';' }) return false
        return try {
            val address = InternetAddress(value, true)
            address.validate()
            address.address == value && value.contains('@')
        } catch (_: Exception) { false }
    }

    private fun now(): LocalDateTime = LocalDateTime.now(zone)

    private companion object {
        val TOKEN = Regex("[A-Za-z0-9_-]{43}")
        val STATUSES = setOf("ALL", "OPENED", "NO_SIGNAL", "NOT_TRACKED")
    }
}
