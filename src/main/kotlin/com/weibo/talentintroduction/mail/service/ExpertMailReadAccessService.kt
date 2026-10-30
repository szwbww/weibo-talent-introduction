package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** NONE is represented by an absent row, never a persisted grant value. */
enum class ExpertMailAccessLevel {
    NONE, READ, FULL;

    val canRead: Boolean get() = this != NONE
}

/**
 * Single/bulk access-level reads and the sole explicit grant writer. No permission cache.
 * Legacy grants only ensure READ, preserving an existing FULL and first-grant metadata.
 * Explicit levels may switch READ/FULL; revocation deletes the row.
 */
@Service
class ExpertMailReadAccessService(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun getAccess(actorUsername: String?, expertContactId: Long): Boolean =
        getAccessLevel(actorUsername, expertContactId).canRead

    fun getAccessLevel(actorUsername: String?, expertContactId: Long): ExpertMailAccessLevel {
        MailReadOnlyPolicy.requireAdmin(actorUsername)
        requireValidContactId(expertContactId)
        requireContactExists(expertContactId)
        return accessLevel(expertContactId)
    }

    /** Internal resource lookup: absence (including a missing contact) is NONE. */
    fun accessLevel(expertContactId: Long): ExpertMailAccessLevel =
        accessLevels(listOf(expertContactId)).getValue(expertContactId)

    /** One query per page, including explicit NONE for requested contacts without a row. */
    fun accessLevels(expertContactIds: Collection<Long>): Map<Long, ExpertMailAccessLevel> {
        if (expertContactIds.isEmpty()) return emptyMap()
        val levels = expertContactIds.associateWith { ExpertMailAccessLevel.NONE }.toMutableMap()
        jdbcTemplate.query(
            """
            SELECT expert_contact_id, access_level FROM expert_mail_read_access
             WHERE username = :username AND expert_contact_id IN (:contactIds)
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("username", MailReadOnlyPolicy.READER_USERNAME)
                .addValue("contactIds", levels.keys)
        ) { rs ->
            levels[rs.getLong("expert_contact_id")] = persistedLevel(rs.getString("access_level"))
        }
        return levels
    }

    fun requireRead(expertContactId: Long): ExpertMailAccessLevel =
        accessLevel(expertContactId).also {
            if (!it.canRead) forbidden()
        }

    fun requireFull(expertContactId: Long): ExpertMailAccessLevel =
        accessLevel(expertContactId).also {
            if (it != ExpertMailAccessLevel.FULL) forbidden()
        }

    /** Legacy Boolean API, preserving first-grant identity and existing FULL. */
    @Transactional
    fun grant(actorUsername: String?, expertContactId: Long): Boolean =
        setAccessLevel(actorUsername, expertContactId, null).canRead

    @Transactional
    fun setAccessLevel(
        actorUsername: String?,
        expertContactId: Long,
        accessLevel: ExpertMailAccessLevel?
    ): ExpertMailAccessLevel {
        MailReadOnlyPolicy.requireAdmin(actorUsername)
        requireValidContactId(expertContactId)
        require(accessLevel != ExpertMailAccessLevel.NONE) { "accessLevel 必须为 READ 或 FULL" }
        lockContact(expertContactId)
        val previous = currentAccessLevel(expertContactId)
        // Only an explicit body can downgrade. Both statements are atomic upserts.
        val update = if (accessLevel == null) "expert_contact_id = expert_contact_id"
            else "access_level = VALUES(access_level)"
        try {
            jdbcTemplate.update(
                """
                INSERT INTO expert_mail_read_access
                    (username, expert_contact_id, granted_by, created_at, access_level)
                VALUES (:username, :contactId, :grantedBy, :createdAt, :accessLevel)
                ON DUPLICATE KEY UPDATE $update
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("username", MailReadOnlyPolicy.READER_USERNAME)
                    .addValue("contactId", expertContactId)
                    .addValue("grantedBy", actorUsername)
                    .addValue("createdAt", LocalDateTime.now())
                    .addValue("accessLevel", (accessLevel ?: ExpertMailAccessLevel.READ).name)
            )
        } catch (ex: DataIntegrityViolationException) {
            throw NoSuchElementException("Expert contact not found: $expertContactId")
        }
        if (previous == ExpertMailAccessLevel.FULL && accessLevel == ExpertMailAccessLevel.READ) {
            reclaimPending(expertContactId)
        }
        return currentAccessLevel(expertContactId)
    }

    /** DELETE：幂等撤销（无行也成功）；返回 canRead=false。 */
    @Transactional
    fun revoke(actorUsername: String?, expertContactId: Long): Boolean {
        MailReadOnlyPolicy.requireAdmin(actorUsername)
        requireValidContactId(expertContactId)
        lockContact(expertContactId)
        val previous = currentAccessLevel(expertContactId)
        jdbcTemplate.update(
            """
            DELETE FROM expert_mail_read_access
             WHERE username = :username AND expert_contact_id = :contactId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("username", MailReadOnlyPolicy.READER_USERNAME)
                .addValue("contactId", expertContactId)
        )
        if (previous == ExpertMailAccessLevel.FULL) reclaimPending(expertContactId)
        return false
    }


    private fun lockContact(contactId: Long) {
        val rows = jdbcTemplate.queryForList(
            "SELECT id FROM expert_contact WHERE id = :contactId FOR UPDATE",
            MapSqlParameterSource("contactId", contactId), Long::class.java
        )
        if (rows.singleOrNull() != contactId) {
            throw NoSuchElementException("Expert contact not found: $contactId")
        }
    }

    private fun currentAccessLevel(contactId: Long): ExpertMailAccessLevel =
        jdbcTemplate.queryForList(
            """SELECT access_level FROM expert_mail_read_access
                WHERE username = :username AND expert_contact_id = :contactId FOR UPDATE""",
            MapSqlParameterSource("contactId", contactId)
                .addValue("username", MailReadOnlyPolicy.READER_USERNAME),
            String::class.java
        ).singleOrNull()?.let { persistedLevel(it) } ?: ExpertMailAccessLevel.NONE

    private fun reclaimPending(contactId: Long) {
        jdbcTemplate.update(
            """UPDATE inbound_mail_processing SET pending_owner_username = 'admin'
                WHERE expert_contact_id = :contactId
                  AND process_status = 'MANUAL_REVIEW' AND pending_owner_username = 'user1'""",
            MapSqlParameterSource("contactId", contactId)
        )
    }
    private fun requireValidContactId(expertContactId: Long) {
        require(expertContactId > 0) { "expertContactId 必须为正数" }
    }

    private fun requireContactExists(expertContactId: Long) {
        val exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expert_contact WHERE id = :contactId",
            MapSqlParameterSource("contactId", expertContactId),
            Long::class.java
        ) ?: 0L
        if (exists == 0L) {
            throw NoSuchElementException("Expert contact not found: $expertContactId")
        }
    }

    private fun persistedLevel(value: String?): ExpertMailAccessLevel =
        when (value) {
            "READ" -> ExpertMailAccessLevel.READ
            "FULL" -> ExpertMailAccessLevel.FULL
            else -> throw IllegalStateException("无效的专家邮件授权等级")
        }

    private fun forbidden(): Nothing =
        throw com.weibo.talentintroduction.auth.config.MailReadAccessDeniedException(
            MailReadOnlyPolicy.MAIL_READ_FORBIDDEN, "无权访问该专家的邮件"
        )
}
