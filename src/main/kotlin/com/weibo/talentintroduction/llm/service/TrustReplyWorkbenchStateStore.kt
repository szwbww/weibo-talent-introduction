package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

/**
 * Narrow persistence + JSON codec for the trust-reply workbench durable locked
 * snapshot. One row per (source_type, source_id) with optimistic concurrency:
 * insert requires expectedStateVersion=0, update/delete require the current
 * state version. Any version mismatch surfaces as a stable
 * TRUST_REPLY_STATE_CONFLICT so a stale tab can never silently overwrite a
 * newer operator decision.
 */
@Component
class TrustReplyWorkbenchStateStore(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    data class TrustReplyStoredState(
        val stateVersion: Long,
        val expiresAt: LocalDateTime,
        val payloadJson: String
    )

    fun load(sourceType: String, sourceId: Long): TrustReplyStoredState? =
        jdbc.query(
            """
            SELECT state_version, payload_json, expires_at
              FROM trust_reply_workbench_state
             WHERE source_type = :sourceType AND source_id = :sourceId
            """.trimIndent(),
            MapSqlParameterSource("sourceType", sourceType).addValue("sourceId", sourceId)
        ) { rs, _ ->
            TrustReplyStoredState(
                stateVersion = rs.getLong("state_version"),
                expiresAt = rs.getObject("expires_at", LocalDateTime::class.java),
                payloadJson = rs.getString("payload_json")
            )
        }.firstOrNull()

    fun save(
        sourceType: String,
        sourceId: Long,
        expectedStateVersion: Long,
        payloadJson: String,
        now: LocalDateTime
    ): Long {
        checkPayloadSize(payloadJson)
        pruneExpired(now)
        if (expectedStateVersion == 0L) {
            return insert(sourceType, sourceId, payloadJson, now)
        }
        val updated = jdbc.update(
            """
            UPDATE trust_reply_workbench_state
               SET state_version = state_version + 1,
                   payload_json = :payload,
                   expires_at = :expiresAt,
                   updated_at = :now
             WHERE source_type = :sourceType
               AND source_id = :sourceId
               AND state_version = :expected
            """.trimIndent(),
            MapSqlParameterSource("sourceType", sourceType)
                .addValue("sourceId", sourceId)
                .addValue("expected", expectedStateVersion)
                .addValue("payload", payloadJson)
                .addValue("expiresAt", now.plusDays(EXPIRY_DAYS))
                .addValue("now", now)
        )
        if (updated == 1) {
            return expectedStateVersion + 1
        }
        throwStateConflict(sourceType, sourceId, expectedStateVersion)
    }

    fun delete(sourceType: String, sourceId: Long, expectedStateVersion: Long): Boolean {
        if (expectedStateVersion == 0L) return false
        val deleted = jdbc.update(
            """
            DELETE FROM trust_reply_workbench_state
             WHERE source_type = :sourceType
               AND source_id = :sourceId
               AND state_version = :expected
            """.trimIndent(),
            MapSqlParameterSource("sourceType", sourceType)
                .addValue("sourceId", sourceId)
                .addValue("expected", expectedStateVersion)
        )
        if (deleted == 1) return true
        throwStateConflict(sourceType, sourceId, expectedStateVersion)
    }

    fun pruneExpired(now: LocalDateTime): Int =
        jdbc.update(
            "DELETE FROM trust_reply_workbench_state WHERE expires_at <= :now",
            MapSqlParameterSource("now", now)
        )

    fun encodePayload(payload: TrustReplySavedStatePayload): String {
        val json = objectMapper.writeValueAsString(payload)
        checkPayloadSize(json)
        return json
    }

    /**
     * I-7: v3 payloads carry the canonical matrix plus the frame snapshot;
     * v2 payloads carry the matrix without a frame and are presented as the
     * current matrix schema with a null frame (I-2 default compat); v1 payloads
     * are returned as legacy flat unions so the business layer can normalize
     * them (I-4). Unknown or corrupt payloads decode to null and surface as
     * INVALID on restore.
     */
    fun decodePayload(json: String): TrustReplySavedStatePayload? =
        try {
            val decoded = objectMapper.readValue<TrustReplySavedStatePayload>(json)
            when (decoded.schemaVersion) {
                SCHEMA_VERSION -> decoded
                PREVIOUS_SCHEMA_VERSION -> decoded.copy(schemaVersion = SCHEMA_VERSION)
                LEGACY_SCHEMA_VERSION -> decoded
                else -> null
            }
        } catch (_: Exception) {
            null
        }

    private fun insert(sourceType: String, sourceId: Long, payloadJson: String, now: LocalDateTime): Long {
        try {
            val inserted = jdbc.update(
                """
                INSERT INTO trust_reply_workbench_state
                    (source_type, source_id, state_version, payload_json,
                     expires_at, created_at, updated_at)
                VALUES
                    (:sourceType, :sourceId, 1, :payload, :expiresAt, :now, :now)
                """.trimIndent(),
                MapSqlParameterSource("sourceType", sourceType)
                    .addValue("sourceId", sourceId)
                    .addValue("payload", payloadJson)
                    .addValue("expiresAt", now.plusDays(EXPIRY_DAYS))
                    .addValue("now", now)
            )
            if (inserted == 1) return 1L
            throwStateConflict(sourceType, sourceId, 0L)
        } catch (ex: DuplicateKeyException) {
            throw TrustReplyStateConflictException(
                "state already exists for ${sourceType}:$sourceId (expected version 0)"
            )
        }
    }

    private fun throwStateConflict(sourceType: String, sourceId: Long, expectedStateVersion: Long): Nothing {
        val exists = load(sourceType, sourceId) != null
        throw TrustReplyStateConflictException(
            if (exists) {
                "state version conflict for ${sourceType}:$sourceId (expected $expectedStateVersion)"
            } else {
                "no state row for ${sourceType}:$sourceId to mutate at version $expectedStateVersion"
            }
        )
    }

    private fun checkPayloadSize(json: String) {
        if (json.toByteArray(StandardCharsets.UTF_8).size > MAX_PAYLOAD_BYTES) {
            throw TrustReplyWorkbenchException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "TRUST_REPLY_STATE_TOO_LARGE"
            )
        }
    }

    companion object {
        const val SCHEMA_VERSION = "trust-reply-workbench-state-v3"
        const val PREVIOUS_SCHEMA_VERSION = "trust-reply-workbench-state-v2"
        const val LEGACY_SCHEMA_VERSION = "trust-reply-workbench-state-v1"
        val ACCEPTED_REQUEST_SCHEMA_VERSIONS = setOf(
            SCHEMA_VERSION,
            PREVIOUS_SCHEMA_VERSION,
            LEGACY_SCHEMA_VERSION
        )
        const val MAX_PAYLOAD_BYTES = 256 * 1024
        const val EXPIRY_DAYS = 30L
    }
}

class TrustReplyStateConflictException(message: String) :
    TrustReplyWorkbenchException(HttpStatus.CONFLICT, "TRUST_REPLY_STATE_CONFLICT")
