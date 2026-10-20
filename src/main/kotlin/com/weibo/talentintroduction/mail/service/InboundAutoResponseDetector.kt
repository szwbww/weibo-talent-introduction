package com.weibo.talentintroduction.mail.service

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.sql.Timestamp
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale

/**
 * 首封自动回复判断（I-1/I-2/I-7）：只读锁/查询 + 纯规则判定，绝不写任何表、绝不发信。
 *
 * 调用方必须是**已匹配专家**的原收信路径，且在调用方现有事务内、本封任何业务写入
 * 之前调用 [lockContact] 与 [evaluateFirstReply]。首封资格按 `expert_contact.id` 跨该
 * 专家全部逻辑账号与已绑定别名；已存在任意 `inbound_mail_processing` 或 INBOUND
 * `mail_record` 即非首封，此时不解析邮件头、不查询发信锚点。
 */
@Component
class InboundAutoResponseDetector(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    /**
     * 在调用方事务内锁定既有 contact 行（只锁不改）。必须在同一 contact 的任何本封写入
     * 之前取得，避免两个事务先插带外键的 processing 再升级锁造成死锁。
     */
    fun lockContact(contactId: Long) {
        requireActiveTransaction()
        val rows = jdbcTemplate.queryForList(LOCK_CONTACT_SQL, mapOf("contactId" to contactId))
        check(rows.isNotEmpty()) { "Expert contact $contactId not found for inbound first-reply lock" }
    }

    /**
     * 首封资格成立时才计算 HEADER OR FAST_REPLY_5M；非首封立即返回 null。
     * 返回值是 `process_reason`（AUTO_RESPONSE_HEADER / AUTO_RESPONSE_FAST_5M）。
     */
    fun evaluateFirstReply(contactId: Long, accountCode: String, received: ReceivedMail): String? {
        requireActiveTransaction()
        if (hasPriorProcessing(contactId) || hasPriorInboundRecord(contactId)) {
            return null
        }
        if (AutoSubmittedHeader.normalize(listOfNotNull(received.autoSubmitted)) ==
            AutoSubmittedHeader.AUTO_REPLIED
        ) {
            return AUTO_RESPONSE_HEADER
        }
        if (!received.receivedAtReliable) {
            return null
        }
        val sentAt = latestSentAt(contactId, accountCode, received.receivedAt) ?: return null
        return classify(received.autoSubmitted, received.receivedAt, receivedAtReliable = true, sentAt = sentAt)
    }

    /**
     * 纯规则判断（无 SQL、不接收标题/正文）：顶层 `Auto-Submitted` 规范化为
     * `auto-replied` 即 HEADER；否则在真实到达时间与最近成功发信之间
     * `0 <= delta <= 300s`（含两端）即 FAST_5M。两条均命中时返回 HEADER。
     */
    fun classify(
        autoSubmitted: String?,
        receivedAt: LocalDateTime?,
        receivedAtReliable: Boolean,
        sentAt: LocalDateTime?
    ): String? {
        if (AutoSubmittedHeader.normalize(listOfNotNull(autoSubmitted)) == AutoSubmittedHeader.AUTO_REPLIED) {
            return AUTO_RESPONSE_HEADER
        }
        if (!receivedAtReliable || receivedAt == null || sentAt == null) {
            return null
        }
        val delta = Duration.between(sentAt, receivedAt)
        return if (delta >= Duration.ZERO && delta <= FIRST_REPLY_WINDOW) {
            AUTO_RESPONSE_FAST_5M
        } else {
            null
        }
    }

    private fun hasPriorProcessing(contactId: Long): Boolean =
        jdbcTemplate.queryForList(
            PRIOR_PROCESSING_SQL,
            mapOf("contactId" to contactId)
        ).isNotEmpty()

    private fun hasPriorInboundRecord(contactId: Long): Boolean =
        jdbcTemplate.queryForList(
            PRIOR_INBOUND_RECORD_SQL,
            mapOf("contactId" to contactId)
        ).isNotEmpty()

    /**
     * 最近一封「同一专家 + 同一逻辑账号 + OUTBOUND/SENT + sent_at 非空 + 不晚于来信」。
     * 锁定读避免 repeatable-read 下拿到锁等待之前冻结的旧历史；不依赖 created_at。
     */
    private fun latestSentAt(
        contactId: Long,
        accountCode: String,
        receivedAt: LocalDateTime
    ): LocalDateTime? {
        val rows = jdbcTemplate.queryForList(
            LATEST_SENT_OUTBOUND_SQL,
            mapOf(
                "contactId" to contactId,
                "accountCode" to accountCode,
                "receivedAt" to receivedAt
            )
        )
        return toLocalDateTime(rows.firstOrNull()?.get("sent_at"))
    }

    private fun toLocalDateTime(value: Any?): LocalDateTime? = when (value) {
        is LocalDateTime -> value
        is Timestamp -> value.toLocalDateTime()
        else -> null
    }

    private fun requireActiveTransaction() {
        check(TransactionSynchronizationManager.isActualTransactionActive()) {
            "Inbound auto-response first-reply rules require an active caller transaction"
        }
    }

    companion object {
        const val AUTO_RESPONSE_HEADER = "AUTO_RESPONSE_HEADER"
        const val AUTO_RESPONSE_FAST_5M = "AUTO_RESPONSE_FAST_5M"
        const val REASON_TYPE_AUTO_RESPONSE = "AUTO_RESPONSE"

        /** 包含第 300 秒；300.001 秒不属于窗口。 */
        val FIRST_REPLY_WINDOW: Duration = Duration.ofSeconds(300)

        const val LOCK_CONTACT_SQL =
            "SELECT id FROM expert_contact WHERE id = :contactId FOR UPDATE"

        const val PRIOR_PROCESSING_SQL =
            "SELECT id FROM inbound_mail_processing " +
                "WHERE expert_contact_id = :contactId ORDER BY id LIMIT 1 FOR UPDATE"

        const val PRIOR_INBOUND_RECORD_SQL =
            "SELECT id FROM mail_record " +
                "WHERE expert_contact_id = :contactId AND direction = 'INBOUND' " +
                "ORDER BY id LIMIT 1 FOR UPDATE"

        const val LATEST_SENT_OUTBOUND_SQL =
            "SELECT sent_at FROM mail_record " +
                "WHERE expert_contact_id = :contactId AND sender_account_code = :accountCode " +
                "AND direction = 'OUTBOUND' AND send_status = 'SENT' " +
                "AND sent_at IS NOT NULL AND sent_at <= :receivedAt " +
                "ORDER BY sent_at DESC, id DESC LIMIT 1 FOR UPDATE"
    }
}

/**
 * 顶层 `Auto-Submitted` 规范化：去参数（`; owner=...`）、大小写归一。
 * 只有单个/完全一致的合法值才返回该值；重复冲突或空值不构成命中。
 */
object AutoSubmittedHeader {
    const val AUTO_REPLIED = "auto-replied"

    /**
     * 只有**恰好一次**顶层 `Auto-Submitted` 才返回规范值；重复（即使值相同）或冲突
     * 头都不构成单值命中，避免重复头被折叠成一个 auto-replied。
     */
    fun normalize(values: List<String>?): String? {
        if (values == null || values.size != 1) {
            return null
        }
        val normalized = values.single().substringBefore(';').trim().lowercase(Locale.ROOT)
        return normalized.ifEmpty { null }
    }
}
