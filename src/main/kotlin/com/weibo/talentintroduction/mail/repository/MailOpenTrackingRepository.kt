package com.weibo.talentintroduction.mail.repository

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

data class OpenTrackingReservation(val id: Long, val token: String)
data class OpenTrackingRow(
    val mailRecordId: Long,
    val expertContactId: Long,
    val expertName: String?,
    val recipient: String?,
    val senderAccountCode: String?,
    val mailType: String,
    val subject: String?,
    val sentAt: LocalDateTime,
    val trackingStatus: String,
    val firstOpenAt: LocalDateTime?,
    val lastOpenAt: LocalDateTime?,
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val messageId: String? = null
)

data class OpenTrackingSummary(val trackedSent: Long, val opened: Long, val openSignalRate: Double?)
data class OpenTrackingSnapshot(
    val records: List<OpenTrackingRow>,
    val totalCount: Long,
    val summary: OpenTrackingSummary
)

data class OpenTrackingFilter(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val senderAccountCode: String?,
    val status: String,
    val keyword: String?,
    val pageSize: Int,
    val pageOffset: Int
)

@Repository
class MailOpenTrackingRepository(private val jdbc: JdbcTemplate) {
    fun isEnabled(): Boolean = jdbc.query(
        "SELECT setting_value FROM batch_send_setting WHERE setting_key = ?",
        { rs, _ -> rs.getString(1) }, KEY
    ).firstOrNull() == "true"

    fun setEnabled(enabled: Boolean, now: LocalDateTime) {
        jdbc.update(
            "INSERT INTO batch_send_setting (setting_key, setting_value, updated_at) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE setting_value = ?, updated_at = ?",
            KEY, enabled.toString(), now, enabled.toString(), now
        )
    }

    /** Separate Spring bean proxy and independent commit before SMTP; never FK an uncommitted contact. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun reserve(token: String, recipient: String, now: LocalDateTime): OpenTrackingReservation? {
        if (!isEnabled()) return null
        val keys = GeneratedKeyHolder()
        jdbc.update({ connection ->
            connection.prepareStatement(
                "INSERT INTO mail_open_tracking (token, recipient, created_at) VALUES (?, ?, ?)",
                arrayOf("id")
            ).apply {
                setString(1, token)
                setString(2, recipient)
                setObject(3, now)
            }
        }, keys)
        return OpenTrackingReservation(requireNotNull(keys.key).toLong(), token)
    }

    fun recordSignal(token: String, now: LocalDateTime): Int = jdbc.update(
        """UPDATE mail_open_tracking t
           SET first_open_at = CASE WHEN first_open_at IS NULL OR first_open_at > ? THEN ? ELSE first_open_at END,
               last_open_at = CASE WHEN last_open_at IS NULL OR last_open_at < ? THEN ? ELSE last_open_at END
           WHERE t.token = ? AND EXISTS (
               SELECT 1 FROM batch_send_setting s
               WHERE s.setting_key = 'mailOpenTracking.enabled' AND s.setting_value = 'true'
           )""".trimIndent(), now, now, now, now, token
    )

    fun readPage(filter: OpenTrackingFilter): OpenTrackingSnapshot {
        val (where, args) = conditions(filter, includeStatusAndKeyword = true)
        val records = jdbc.query(
            "$SELECT $where ORDER BY m.sent_at DESC, m.id DESC LIMIT ? OFFSET ?",
            { rs, _ -> row(rs) }, *(args + filter.pageSize + filter.pageOffset).toTypedArray()
        )
        val total = jdbc.queryForObject("SELECT COUNT(*) $FROM $where", Long::class.java, *args.toTypedArray()) ?: 0L
        val (summaryWhere, summaryArgs) = conditions(filter, includeStatusAndKeyword = false)
        val counts = jdbc.queryForMap(
            "SELECT COUNT(t.id) AS tracked_sent, COALESCE(SUM(t.first_open_at IS NOT NULL),0) AS opened $FROM $summaryWhere",
            *summaryArgs.toTypedArray()
        )
        val tracked = (counts["tracked_sent"] as Number).toLong()
        val opened = (counts["opened"] as Number).toLong()
        return OpenTrackingSnapshot(records, total, OpenTrackingSummary(tracked, opened,
            if (tracked == 0L) null else opened.toDouble() / tracked))
    }

    fun detail(id: Long): OpenTrackingRow? = jdbc.query(
        "$SELECT WHERE m.direction = 'OUTBOUND' AND m.send_status = 'SENT' AND m.sent_at IS NOT NULL AND m.id = ?",
        { rs, _ -> row(rs, detail = true) }, id
    ).firstOrNull()

    private fun conditions(filter: OpenTrackingFilter, includeStatusAndKeyword: Boolean): Pair<String, List<Any>> {
        val args = mutableListOf<Any>(filter.start, filter.end)
        val where = StringBuilder(
            "WHERE m.direction = 'OUTBOUND' AND m.send_status = 'SENT' AND m.sent_at IS NOT NULL " +
                "AND m.sent_at >= ? AND m.sent_at < ?"
        )
        if (filter.senderAccountCode != null) {
            where.append(" AND m.sender_account_code = ?")
            args.add(filter.senderAccountCode)
        }
        if (includeStatusAndKeyword) {
            when (filter.status) {
                "OPENED" -> where.append(" AND t.first_open_at IS NOT NULL")
                "NO_SIGNAL" -> where.append(" AND t.id IS NOT NULL AND t.first_open_at IS NULL")
                "NOT_TRACKED" -> where.append(" AND t.id IS NULL")
            }
            if (filter.keyword != null) {
                where.append(" AND (LOCATE(?, ec.expert_name) > 0 OR LOCATE(?, t.recipient) > 0 OR LOCATE(?, m.subject) > 0)")
                repeat(3) { args.add(filter.keyword) }
            }
        }
        return where.toString() to args
    }

    private fun row(rs: java.sql.ResultSet, detail: Boolean = false): OpenTrackingRow {
        val first = rs.getTimestamp("first_open_at")?.toLocalDateTime()
        val tracked = rs.getObject("tracking_id") != null
        return OpenTrackingRow(
            mailRecordId = rs.getLong("mail_record_id"),
            expertContactId = rs.getLong("expert_contact_id"),
            expertName = rs.getString("expert_name"),
            recipient = rs.getString("recipient"),
            senderAccountCode = rs.getString("sender_account_code"),
            mailType = rs.getString("mail_type"),
            subject = rs.getString("subject"),
            sentAt = rs.getTimestamp("sent_at").toLocalDateTime(),
            trackingStatus = if (!tracked) "NOT_TRACKED" else if (first != null) "OPENED" else "NO_SIGNAL",
            firstOpenAt = first,
            lastOpenAt = rs.getTimestamp("last_open_at")?.toLocalDateTime(),
            messageId = if (detail) rs.getString("message_id") else null
        )
    }

    companion object {
        private const val KEY = "mailOpenTracking.enabled"
        private const val FROM = "FROM mail_record m LEFT JOIN mail_open_tracking t ON t.id = m.open_tracking_id " +
            "LEFT JOIN expert_contact ec ON ec.id = m.expert_contact_id"
        private const val SELECT = "SELECT m.id AS mail_record_id, m.expert_contact_id, ec.expert_name, " +
            "t.id AS tracking_id, t.recipient, m.sender_account_code, m.mail_type, m.subject, " +
            "m.sent_at, t.first_open_at, t.last_open_at, m.message_id $FROM"
    }
}
