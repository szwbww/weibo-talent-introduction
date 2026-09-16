package com.weibo.talentintroduction.campaign.repository

import com.weibo.talentintroduction.campaign.domain.MeetingCalendarEvent
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class MeetingCalendarEventRepository(
    private val jdbc: NamedParameterJdbcTemplate
) {
    data class EventRow(
        val event: MeetingCalendarEvent,
        val expertName: String?,
        val expertEmail: String
    )

    private val eventMapper = RowMapper { rs: ResultSet, _: Int ->
        EventRow(
            event = MeetingCalendarEvent(
                id = rs.getLong("id"),
                expertContactId = rs.getLong("expert_contact_id"),
                sourceMailRecordId = rs.getLongOrNull("source_mail_record_id"),
                startsAtUtc = rs.getUtcInstant("starts_at_utc"),
                endsAtUtc = rs.getUtcInstant("ends_at_utc"),
                meetingLink = rs.getString("meeting_link"),
                note = rs.getString("note"),
                cancelReason = rs.getString("cancel_reason"),
                status = rs.getString("status"),
                createdAt = rs.getUtcInstant("created_at"),
                updatedAt = rs.getUtcInstant("updated_at")
            ),
            expertName = rs.getString("expert_name"),
            expertEmail = rs.getString("expert_email")
        )
    }

    fun findById(id: Long): EventRow? = findOne(BASE_SELECT + " WHERE e.id = :id", mapOf("id" to id))

    fun findByIdForUpdate(id: Long): EventRow? =
        findOne(BASE_SELECT + " WHERE e.id = :id FOR UPDATE", mapOf("id" to id))

    fun findBySourceMailRecordId(sourceMailRecordId: Long): EventRow? =
        findOne(BASE_SELECT + " WHERE e.source_mail_record_id = :sourceMailRecordId", mapOf("sourceMailRecordId" to sourceMailRecordId))

    /**
     * 唯一来源冲突专用查询（I-1）：并发事务刚提交的行在 REPEATABLE READ 的普通读里
     * 不可见（快照早于对方提交），锁读是 current read 才能看到它，从而把并发重复
     * 提交收敛成「返回已存在的那一行」而不是抛唯一键异常。
     */
    fun findBySourceMailRecordIdForUpdate(sourceMailRecordId: Long): EventRow? =
        findOne(
            BASE_SELECT + " WHERE e.source_mail_record_id = :sourceMailRecordId FOR UPDATE",
            mapOf("sourceMailRecordId" to sourceMailRecordId)
        )

    fun insert(event: MeetingCalendarEvent): Long {
        val params = params(event)
        val keyHolder = GeneratedKeyHolder()
        val count = jdbc.update(INSERT_SQL, params, keyHolder, arrayOf("id"))
        check(count == 1) { "Failed to insert meeting calendar event" }
        return keyHolder.key?.toLong() ?: error("Meeting calendar insert did not return an id")
    }

    fun list(
        fromUtc: LocalDateTime?,
        toUtc: LocalDateTime?,
        contactId: Long?,
        showCancelled: Boolean,
        afterStartsAtUtc: LocalDateTime?,
        afterId: Long?,
        limit: Int
    ): List<EventRow> {
        val clauses = mutableListOf<String>()
        val parameters = mutableMapOf<String, Any>()
        if (!showCancelled) clauses += "e.status = 'ACTIVE'"
        if (contactId != null) {
            clauses += "e.expert_contact_id = :contactId"
            parameters["contactId"] = contactId
        }
        if (fromUtc != null && toUtc != null) {
            clauses += "e.starts_at_utc < :toUtc AND e.ends_at_utc > :fromUtc"
            parameters["fromUtc"] = fromUtc
            parameters["toUtc"] = toUtc
        }
        if (afterStartsAtUtc != null && afterId != null) {
            clauses += "(e.starts_at_utc > :afterStartsAtUtc OR (e.starts_at_utc = :afterStartsAtUtc AND e.id > :afterId))"
            parameters["afterStartsAtUtc"] = afterStartsAtUtc
            parameters["afterId"] = afterId
        }
        parameters["limit"] = limit
        val where = if (clauses.isEmpty()) "" else " WHERE " + clauses.joinToString(" AND ")
        return jdbc.query(
            BASE_SELECT + where + " ORDER BY e.starts_at_utc ASC, e.id ASC LIMIT :limit",
            MapSqlParameterSource(parameters),
            eventMapper
        )
    }

    fun findActiveByContactIds(contactIds: List<Long>): List<EventRow> {
        if (contactIds.isEmpty()) return emptyList()
        return jdbc.query(
            BASE_SELECT + " WHERE e.status = 'ACTIVE' AND e.expert_contact_id IN (:contactIds)" +
                " ORDER BY e.starts_at_utc ASC, e.id ASC",
            MapSqlParameterSource("contactIds", contactIds),
            eventMapper
        )
    }

    fun updateMutable(
        id: Long,
        expectedUpdatedAtUtc: LocalDateTime,
        startsAtUtc: LocalDateTime,
        endsAtUtc: LocalDateTime,
        meetingLink: String?,
        note: String?,
        updatedAtUtc: LocalDateTime
    ): Int = jdbc.update(
        """
        UPDATE meeting_calendar_event
           SET starts_at_utc = :startsAtUtc,
               ends_at_utc = :endsAtUtc,
               meeting_link = :meetingLink,
               note = :note,
               updated_at = :updatedAtUtc
         WHERE id = :id AND status = 'ACTIVE' AND updated_at = :expectedUpdatedAtUtc
        """.trimIndent(),
        MapSqlParameterSource()
            .addValue("id", id)
            .addValue("expectedUpdatedAtUtc", expectedUpdatedAtUtc)
            .addValue("startsAtUtc", startsAtUtc)
            .addValue("endsAtUtc", endsAtUtc)
            .addValue("meetingLink", meetingLink)
            .addValue("note", note)
            .addValue("updatedAtUtc", updatedAtUtc)
    )

    fun cancel(
        id: Long,
        expectedUpdatedAtUtc: LocalDateTime,
        reason: String?,
        updatedAtUtc: LocalDateTime
    ): Int = jdbc.update(
        """
        UPDATE meeting_calendar_event
           SET status = 'CANCELLED', cancel_reason = :reason, updated_at = :updatedAtUtc
         WHERE id = :id AND status = 'ACTIVE' AND updated_at = :expectedUpdatedAtUtc
        """.trimIndent(),
        MapSqlParameterSource()
            .addValue("id", id)
            .addValue("expectedUpdatedAtUtc", expectedUpdatedAtUtc)
            .addValue("reason", reason)
            .addValue("updatedAtUtc", updatedAtUtc)
    )

    private fun findOne(sql: String, parameters: Map<String, Any>): EventRow? =
        jdbc.query(sql, MapSqlParameterSource(parameters), eventMapper).firstOrNull()

    private fun params(event: MeetingCalendarEvent): MapSqlParameterSource = MapSqlParameterSource()
        .addValue("expertContactId", event.expertContactId)
        .addValue("sourceMailRecordId", event.sourceMailRecordId)
        .addValue("startsAtUtc", event.startsAtUtc.toUtcLocalDateTime())
        .addValue("endsAtUtc", event.endsAtUtc.toUtcLocalDateTime())
        .addValue("meetingLink", event.meetingLink)
        .addValue("note", event.note)
        .addValue("cancelReason", event.cancelReason)
        .addValue("status", event.status)
        .addValue("createdAt", event.createdAt.toUtcLocalDateTime())
        .addValue("updatedAt", event.updatedAt.toUtcLocalDateTime())

    companion object {
        private const val BASE_SELECT = """
            SELECT e.id, e.expert_contact_id, e.source_mail_record_id,
                   e.starts_at_utc, e.ends_at_utc, e.meeting_link, e.note,
                   e.cancel_reason, e.status, e.created_at, e.updated_at,
                   c.expert_name, c.expert_email
              FROM meeting_calendar_event e
              JOIN expert_contact c ON c.id = e.expert_contact_id
        """

        private const val INSERT_SQL = """
            INSERT INTO meeting_calendar_event
                (expert_contact_id, source_mail_record_id, starts_at_utc, ends_at_utc,
                 meeting_link, note, cancel_reason, status, created_at, updated_at)
            VALUES
                (:expertContactId, :sourceMailRecordId, :startsAtUtc, :endsAtUtc,
                 :meetingLink, :note, :cancelReason, :status, :createdAt, :updatedAt)
        """
    }
}

private fun ResultSet.getLongOrNull(column: String): Long? {
    val value = getLong(column)
    return if (wasNull()) null else value
}

/** DATETIME is read as text so the JVM default timezone cannot affect the UTC mapping. */
private fun ResultSet.getUtcInstant(column: String): Instant =
    LocalDateTime.parse(getString(column).replace(' ', 'T')).toInstant(ZoneOffset.UTC)

private fun Instant.toUtcLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
