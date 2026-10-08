package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID

data class ExpertInboundNotificationSetting(
    val enabled: Boolean = false,
    val generation: Long = 0,
    val updatedAt: LocalDateTime? = null
)
data class ExpertInboundNotificationSettingsResponse(
    val enabled: Boolean,
    val configured: Boolean,
    val generation: Long,
    val updatedAt: LocalDateTime?
)
enum class ExpertInboundNotificationStatus { PENDING, SENDING, SENT, FAILED, CANCELLED }
data class ExpertInboundNotificationClaim(
    val id: Long,
    val generation: Long,
    val payload: String,
    val attempts: Int,
    val token: String
)

@Repository
class ExpertInboundNotificationRepository(
    private val jdbc: JdbcTemplate,
    transactionManager: PlatformTransactionManager
) {
    private val transactions = TransactionTemplate(transactionManager)
    // A fresh template: the application's existing template is REQUIRES_NEW, not a savepoint.
    private val nested = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_NESTED
    }

    fun settings(): ExpertInboundNotificationSetting = readSetting(false) ?: ExpertInboundNotificationSetting()

    fun setEnabled(enabled: Boolean, username: String): ExpertInboundNotificationSetting = transactions.execute {
        var setting = readSetting(true)
        if (setting == null) {
            jdbc.update("INSERT INTO expert_inbound_notification_setting (id,enabled,generation) VALUES (1,FALSE,0) ON DUPLICATE KEY UPDATE id=id")
            setting = requireNotNull(readSetting(true))
        }
        if (setting.enabled != enabled) {
            val now = now()
            jdbc.update("UPDATE expert_inbound_notification_setting SET enabled=?,generation=generation+1,updated_at=?,updated_by=? WHERE id=1",
                enabled, now, username.take(100))
            if (!enabled) {
                jdbc.update("UPDATE expert_inbound_notification_outbox SET status='CANCELLED',lease_token=NULL,lease_until=NULL,updated_at=? WHERE status='PENDING'", now)
            }
        }
        settings()
    }!!

    /** Catch failures outside this boundary so a failed notification cannot poison receipt commit. */
    fun inReceiptSavepoint(action: () -> Unit) {
        check(TransactionSynchronizationManager.isActualTransactionActive()) { "NOTIFICATION_REQUIRES_RECEIPT_TRANSACTION" }
        nested.execute { action() }
    }

    /** Called only inside the receipt savepoint. The setting lock is always acquired first. */
    fun enqueue(source: InboundMailProcessing, payload: (Long) -> String): Long? {
        check(TransactionSynchronizationManager.isActualTransactionActive()) { "NOTIFICATION_REQUIRES_RECEIPT_TRANSACTION" }
        val setting = readSetting(true) ?: return null
        if (!setting.enabled) return null
        val owner = source.mailboxOwnerCode
        require(!owner.isNullOrBlank() && source.uidValidity > 0 && source.imapUid > 0 && source.expertContactId != null && source.id != null)
        val existing = jdbc.query("SELECT id FROM expert_inbound_notification_outbox WHERE mailbox_owner_code=? AND uid_validity=? AND imap_uid=? FOR UPDATE",
            { rs, _ -> rs.getLong(1) }, owner, source.uidValidity, source.imapUid).firstOrNull()
        if (existing != null) return null
        val now = now()
        val keys = GeneratedKeyHolder()
        jdbc.update({ connection ->
            connection.prepareStatement("INSERT INTO expert_inbound_notification_outbox (inbound_processing_id,expert_contact_id,mailbox_owner_code,uid_validity,imap_uid,generation,payload,next_attempt_at,created_at,updated_at) VALUES (?,?,?,?,?,?,'',?,?,?)", arrayOf("id")).apply {
                setLong(1, source.id)
                setLong(2, source.expertContactId)
                setString(3, owner)
                setLong(4, source.uidValidity)
                setLong(5, source.imapUid)
                setLong(6, setting.generation)
                setObject(7, now)
                setObject(8, now)
                setObject(9, now)
            }
        }, keys)
        val id = requireNotNull(keys.key).toLong()
        jdbc.update("UPDATE expert_inbound_notification_outbox SET payload=? WHERE id=?", payload(id), id)
        return id
    }

    fun claim(worker: String): ExpertInboundNotificationClaim? = transactions.execute {
        val setting = readSetting(true) ?: return@execute null
        if (!setting.enabled) return@execute null
        val now = now()
        val lease = jdbc.queryForMap("SELECT worker_owner,(worker_lease_until>UTC_TIMESTAMP(3)) AS lease_active,(next_send_at>UTC_TIMESTAMP(3)) AS rate_limited FROM expert_inbound_notification_setting WHERE id=1")
        val owner = lease["worker_owner"] as? String
        val activeLease = (lease["lease_active"] as? Number)?.toInt() == 1
        if (owner != null && owner != worker && activeLease) return@execute null
        jdbc.update("UPDATE expert_inbound_notification_setting SET worker_owner=?,worker_lease_until=? WHERE id=1", worker, now.plusSeconds(30))
        // Recover one expired claim per tick, never an unbounded history scan.
        val expired = jdbc.query("SELECT id,generation,payload,attempts,lease_token FROM expert_inbound_notification_outbox WHERE status='SENDING' AND lease_until<=? ORDER BY lease_until,id LIMIT 1 FOR UPDATE",
            { rs, _ -> claimRow(rs) }, now).firstOrNull()
        if (expired != null) {
            val status = when {
                expired.generation != setting.generation -> ExpertInboundNotificationStatus.CANCELLED
                expired.attempts >= 3 -> ExpertInboundNotificationStatus.FAILED
                else -> ExpertInboundNotificationStatus.PENDING
            }
            jdbc.update("UPDATE expert_inbound_notification_outbox SET status=?,lease_token=NULL,lease_until=NULL,next_attempt_at=?,updated_at=?,last_error_code='LEASE_EXPIRED' WHERE id=? AND status='SENDING' AND lease_token=?",
                status.name, now.plusSeconds(backoff(expired.attempts)), now, expired.id, expired.token)
        }
        // A live claim blocks all further claims, including another worker after a setting lease race.
        val active = jdbc.query("SELECT id FROM expert_inbound_notification_outbox WHERE status='SENDING' AND lease_until>? LIMIT 1", { rs, _ -> rs.getLong(1) }, now)
        if (active.isNotEmpty()) return@execute null
        if ((lease["rate_limited"] as? Number)?.toInt() == 1) return@execute null
        val row = jdbc.query("SELECT id,generation,payload,attempts,lease_token FROM expert_inbound_notification_outbox WHERE status='PENDING' AND next_attempt_at<=? ORDER BY next_attempt_at,id LIMIT 1 FOR UPDATE",
            { rs, _ -> claimRow(rs) }, now).firstOrNull() ?: return@execute null
        if (row.generation != setting.generation) {
            jdbc.update("UPDATE expert_inbound_notification_outbox SET status='CANCELLED',updated_at=? WHERE id=? AND status='PENDING'", now, row.id)
            return@execute null
        }
        val token = UUID.randomUUID().toString()
        val changed = jdbc.update("UPDATE expert_inbound_notification_outbox SET status='SENDING',attempts=attempts+1,lease_token=?,lease_until=?,updated_at=? WHERE id=? AND status='PENDING' AND attempts<3",
            token, now.plusSeconds(30), now, row.id)
        if (changed != 1) null else row.copy(attempts = row.attempts + 1, token = token)
    }

    /** Last short DB gate; no setting transaction spans HTTP. Only one live claim can pass. */
    fun authorizeSend(worker: String, claim: ExpertInboundNotificationClaim): Boolean = transactions.execute {
        val setting = readSetting(true) ?: return@execute false
        val now = now()
        val lease = jdbc.queryForMap("SELECT worker_owner,(worker_lease_until>UTC_TIMESTAMP(3)) AS lease_active,(next_send_at>UTC_TIMESTAMP(3)) AS rate_limited FROM expert_inbound_notification_setting WHERE id=1")
        if (!setting.enabled || setting.generation != claim.generation) {
            jdbc.update("UPDATE expert_inbound_notification_outbox SET status='CANCELLED',lease_token=NULL,lease_until=NULL,updated_at=? WHERE id=? AND status='SENDING' AND lease_token=?", now, claim.id, claim.token)
            return@execute false
        }
        if (lease["worker_owner"] != worker || (lease["lease_active"] as? Number)?.toInt() != 1 ||
            (lease["rate_limited"] as? Number)?.toInt() == 1) return@execute false
        val changed = jdbc.update("UPDATE expert_inbound_notification_outbox SET lease_until=?,updated_at=? WHERE id=? AND status='SENDING' AND lease_token=? AND lease_until>?",
            now.plusSeconds(30), now, claim.id, claim.token, now)
        if (changed != 1) return@execute false
        jdbc.update("UPDATE expert_inbound_notification_setting SET next_send_at=?,worker_lease_until=? WHERE id=1", now.plusSeconds(5), now.plusSeconds(30))
        true
    } ?: false

    fun finish(claim: ExpertInboundNotificationClaim, success: Boolean, retryable: Boolean, errorCode: String?) {
        transactions.execute {
            val setting = readSetting(true)
            val now = now()
            val status = when {
                success -> ExpertInboundNotificationStatus.SENT
                setting == null || !setting.enabled || setting.generation != claim.generation -> ExpertInboundNotificationStatus.CANCELLED
                retryable && claim.attempts < 3 -> ExpertInboundNotificationStatus.PENDING
                else -> ExpertInboundNotificationStatus.FAILED
            }
            val changed = jdbc.update("UPDATE expert_inbound_notification_outbox SET status=?,next_attempt_at=?,updated_at=?,sent_at=?,last_error_code=?,lease_token=NULL,lease_until=NULL WHERE id=? AND status='SENDING' AND lease_token=?",
                status.name, now.plusSeconds(backoff(claim.attempts)), now, if (success) now else null,
                errorCode?.takeIf { it.matches(Regex("[A-Z0-9_]{1,64}")) } ?: if (success) null else "SEND_FAILED", claim.id, claim.token)
            // Anchor cooldown after network completion as well, not just the pre-HTTP gate.
            if (changed == 1 && setting != null) {
                jdbc.update("UPDATE expert_inbound_notification_setting SET next_send_at=GREATEST(COALESCE(next_send_at,?),?) WHERE id=1", now.plusSeconds(5), now.plusSeconds(5))
            }
        }
    }

    fun cleanTerminal(): Int = transactions.execute {
        readSetting(true) ?: return@execute 0
        jdbc.update("DELETE FROM expert_inbound_notification_outbox WHERE status IN ('SENT','FAILED','CANCELLED') AND updated_at<? ORDER BY updated_at,id LIMIT 100", now().minusDays(30))
    } ?: 0

    fun releaseWorker(worker: String) {
        transactions.execute {
            readSetting(true) ?: return@execute
            jdbc.update("UPDATE expert_inbound_notification_setting SET worker_owner=NULL,worker_lease_until=NULL WHERE id=1 AND worker_owner=?", worker)
        }
    }

    private fun readSetting(lock: Boolean): ExpertInboundNotificationSetting? = jdbc.query(
        "SELECT enabled,generation,updated_at FROM expert_inbound_notification_setting WHERE id=1" + if (lock) " FOR UPDATE" else "",
        { rs, _ -> ExpertInboundNotificationSetting(rs.getBoolean("enabled"), rs.getLong("generation"), rs.getObject("updated_at", LocalDateTime::class.java)) }
    ).firstOrNull()

    private fun claimRow(rs: java.sql.ResultSet) = ExpertInboundNotificationClaim(rs.getLong("id"), rs.getLong("generation"), rs.getString("payload"), rs.getInt("attempts"), rs.getString("lease_token") ?: "")
    private fun now(): LocalDateTime = jdbc.query("SELECT UTC_TIMESTAMP(3)", { rs, _ -> rs.getObject(1, LocalDateTime::class.java) }).single()
    private fun backoff(attempts: Int): Long = if (attempts <= 1) 30 else 120
}
