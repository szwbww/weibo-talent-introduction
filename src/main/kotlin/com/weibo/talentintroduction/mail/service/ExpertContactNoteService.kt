package com.weibo.talentintroduction.mail.service

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class ExpertContactNoteView(
    val contactId: Long,
    val note: String,
    val updatedBy: String?,
    val updatedAt: String?
)

/** Contact-only shared notes; no writes to the parent contact or conversation data. */
@Service
class ExpertContactNoteService(private val jdbcTemplate: NamedParameterJdbcTemplate) {
    fun get(contactId: Long): ExpertContactNoteView {
        requireContactExists(contactId)
        return jdbcTemplate.query(
            SELECT_SQL,
            MapSqlParameterSource("contactId", contactId),
            RowMapper { rs, _ ->
                ExpertContactNoteView(
                    contactId,
                    rs.getString("note"),
                    rs.getString("updated_by"),
                    rs.getTimestamp("updated_at").toLocalDateTime()
                        .atOffset(ZoneOffset.ofHours(8)).format(TIME_FORMAT)
                )
            }
        ).firstOrNull() ?: ExpertContactNoteView(contactId, "", null, null)
    }

    @Transactional
    fun save(username: String, contactId: Long, rawNote: String): ExpertContactNoteView {
        require(username.isNotBlank() && username.length <= 64) { "用户名不能为空且不能超过64个字符" }
        require(rawNote.length <= 2000) { "备注不能超过2000个字符" }
        requireContactExists(contactId)
        val note = rawNote.replace("\r\n", "\n").replace('\r', '\n').trim()
        val params = MapSqlParameterSource("contactId", contactId)
        if (note.isEmpty()) {
            jdbcTemplate.update(DELETE_SQL, params)
        } else {
            params.addValue("note", note)
                .addValue("username", username)
                .addValue("updatedAt", LocalDateTime.now(BEIJING).truncatedTo(ChronoUnit.MILLIS))
            jdbcTemplate.update(UPSERT_SQL, params)
        }
        return get(contactId)
    }

    private fun requireContactExists(contactId: Long) {
        require(contactId > 0) { "联系人ID必须为正数" }
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expert_contact WHERE id=:contactId",
            MapSqlParameterSource("contactId", contactId),
            Long::class.java
        ) ?: 0L
        if (count == 0L) throw NoSuchElementException("联系人不存在：$contactId")
    }

    companion object {
        private val BEIJING = ZoneId.of("Asia/Shanghai")
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        private const val SELECT_SQL =
            "SELECT note, updated_by, updated_at FROM expert_contact_note WHERE expert_contact_id=:contactId"
        private const val DELETE_SQL = "DELETE FROM expert_contact_note WHERE expert_contact_id=:contactId"
        private val UPSERT_SQL = """
            INSERT INTO expert_contact_note (expert_contact_id, note, updated_by, updated_at)
            VALUES (:contactId, :note, :username, :updatedAt)
            ON DUPLICATE KEY UPDATE
                note = VALUES(note),
                updated_by = VALUES(updated_by),
                updated_at = VALUES(updated_at)
        """.trimIndent()
    }
}
