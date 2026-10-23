package com.weibo.talentintroduction.discovery.repository

import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

data class DiscoveryApolloSetting(
    val searchEnabled: Boolean,
    val detailEnabled: Boolean,
    val revision: Long,
    val updatedAt: Instant
)

class DiscoveryApolloSettingConflict : IllegalStateException("Apollo setting revision conflict")

/** The only SQL writer. Missing rows are distinct from unreadable or invalid stored settings. */
@Repository
class DiscoveryApolloSettingRepository(
    private val jdbc: JdbcTemplate,
    transactionManager: PlatformTransactionManager
) {
    private val transaction = TransactionTemplate(transactionManager)

    fun find(): DiscoveryApolloSetting? = read(false)

    fun save(expectedRevision: Long, search: Boolean, detail: Boolean, now: Instant): DiscoveryApolloSetting {
        require(expectedRevision >= 0) { "Invalid Apollo setting revision" }
        try {
            return requireNotNull(transaction.execute {
                val current = read(true)
                if ((current?.revision ?: 0L) != expectedRevision) throw DiscoveryApolloSettingConflict()
                if (current != null && current.searchEnabled == search && current.detailEnabled == detail) {
                    return@execute current
                }
                val timestamp = LocalDateTime.ofInstant(now.truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC)
                if (current == null) {
                    jdbc.update("INSERT INTO discovery_apollo_setting " +
                        "(id, search_enabled, detail_enabled, revision, updated_at) VALUES (1, ?, ?, 1, ?)",
                        search, detail, timestamp)
                } else {
                    check(current.revision < Long.MAX_VALUE) { "Apollo setting revision exhausted" }
                    val changed = jdbc.update("UPDATE discovery_apollo_setting SET search_enabled = ?, " +
                        "detail_enabled = ?, revision = revision + 1, updated_at = ? WHERE id = 1 AND revision = ?",
                        search, detail, timestamp, expectedRevision)
                    if (changed != 1) throw DiscoveryApolloSettingConflict()
                }
                checkNotNull(read(false)) { "Apollo setting missing after save" }
            })
        } catch (_: DuplicateKeyException) {
            throw DiscoveryApolloSettingConflict()
        } catch (_: PessimisticLockingFailureException) {
            // InnoDB can resolve competing empty-table gap-lock inserts by aborting one transaction.
            throw DiscoveryApolloSettingConflict()
        }
    }

    private fun read(lock: Boolean): DiscoveryApolloSetting? {
        val rows = jdbc.query("SELECT id, search_enabled, detail_enabled, revision, updated_at " +
            "FROM discovery_apollo_setting" + if (lock) " FOR UPDATE" else "", ROW_MAPPER)
        check(rows.size <= 1) { "Invalid Apollo setting singleton" }
        return rows.firstOrNull()
    }

    private companion object {
        val ROW_MAPPER = RowMapper { rs, _ ->
            val id = rs.getInt("id").also { check(!rs.wasNull()) }
            val search = rs.getInt("search_enabled").also { check(!rs.wasNull()) }
            val detail = rs.getInt("detail_enabled").also { check(!rs.wasNull()) }
            val revision = rs.getLong("revision").also { check(!rs.wasNull()) }
            check(id == 1 && search in 0..1 && detail in 0..1 && revision > 0) {
                "Invalid persisted Apollo setting"
            }
            val updated = checkNotNull(rs.getObject("updated_at", LocalDateTime::class.java)) {
                "Invalid persisted Apollo setting timestamp"
            }
            DiscoveryApolloSetting(search == 1, detail == 1, revision, updated.toInstant(ZoneOffset.UTC))
        }
    }
}
