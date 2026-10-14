package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.RecipientScope
import com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID

/** I-2: 首发占位状态。枚举值逐字落库，读取方按字面量比较。 */
object OutreachGuardStates {
    /** 已占位、尚未交付（未调用 SMTP）。 */
    const val RESERVED = "RESERVED"
    /** 已取得唯一交付权（SMTP 之前持久化）；进程中断即不可重发。 */
    const val SENDING = "SENDING"
    /** 已确认交付。 */
    const val SENT = "SENT"
    /** SMTP 结果不明；不可自动重发，待人工核对。 */
    const val UNKNOWN = "UNKNOWN"
    /** 已证明未交付，仅可沿用原 recipient 重试。 */
    const val SAFE_RETRY = "SAFE_RETRY"
}

/** I-1/I-3: 未获首发许可的可解释原因 key（调用方据此记 skipped，不新建 contact/不调 SMTP）。 */
object OutreachGuardReasonCodes {
    const val ALREADY_SENT = "OUTREACH_ALREADY_SENT"
    const val IN_FLIGHT = "OUTREACH_IN_FLIGHT"
    const val UNKNOWN_PENDING = "OUTREACH_UNKNOWN_PENDING"
    const val HISTORY_PROTECTED = "OUTREACH_HISTORY_PROTECTED"
    const val PERSON_MISSING = "OUTREACH_PERSON_MISSING"
    const val REVISION_CHANGED = "OUTREACH_REVISION_CHANGED"
    const val ADDRESS_INELIGIBLE = "OUTREACH_ADDRESS_INELIGIBLE"
}

/**
 * I-2: 一次成功占位的凭据。owner_token 与 revision 一起参与后续 CAS；[selectedDocId] /
 * [recipient] 是本次许可绑定的原地址，SAFE_RETRY 与 SENT 归属都以此为准。
 */
data class OutreachGuardTicket(
    val personId: String,
    val mailType: String,
    val revision: Long,
    val ownerToken: String,
    val selectedDocId: String,
    val recipient: String
)

/** I-1: `reserve` 结论 —— [Granted] 携带凭据；[Denied] 表示不获首发许可。 */
sealed class OutreachReserveOutcome {
    data class Granted(val ticket: OutreachGuardTicket) : OutreachReserveOutcome()
    data class Denied(val code: String, val detail: String? = null) : OutreachReserveOutcome()
}

/** I-2: `claimSending` 结论 —— 只有 [Claimed] 允许调用 SMTP。 */
sealed class OutreachClaimOutcome {
    object Claimed : OutreachClaimOutcome()
    data class Denied(val code: String, val detail: String? = null) : OutreachClaimOutcome()
}

/**
 * 09（I-1/I-2/I-3）：`discovery_person_outreach` 的**唯一**写入点与首发原子门禁。
 *
 * - 唯一键 `(person_id, mail_type)` + 行级 `FOR UPDATE`（先锁人物行再锁占位行）保证多实例
 *   互斥；**不用** JVM `synchronized`，也不设锁超时/自动解锁。
 * - [reserveIntroduction] 在同一事务内：锁人物行 → 锁占位行 → 只读历史检查（全部成员）→ 插入
 *   RESERVED。既存 SENT/UNKNOWN/SENDING/RESERVED 一律不授予；SAFE_RETRY 仅对原地址重授。
 * - [claimSending] 在发送前复核人物 revision、地址与当前发现资格，再用
 *   expected revision/owner_token CAS RESERVED→SENDING；失败即不发送。
 * - [markSent] 供成功的原事务联动（与 mail_record 同事务）；[markUnknown] 标记不可重发；
 *   [markSafeRetry] 仅在明确未交付时使用，且不改变绑定地址。
 *
 * 只读历史检查依赖可省略（`discoveryPersonRecipientService` / `emailSuppressionService`）——
 * 省略时退化为直连 SQL 的 contact/alias/mail_record/mail_send_attempt 检查，非人物与单元测试零影响。
 */
@Service
class DiscoveryPersonOutreachGuardService(
    private val jdbcTemplate: JdbcTemplate,
    private val discoveryPersonRepository: DiscoveryPersonRepository,
    private val discoveryPersonRecipientService: DiscoveryPersonRecipientService? = null,
    private val emailSuppressionService: EmailSuppressionService? = null
) {
    private val log = LoggerFactory.getLogger(DiscoveryPersonOutreachGuardService::class.java)

    /**
     * I-1/I-3: 为 `mailType=INTRODUCTION` 抢占人物级首发许可。
     *
     * [selectedDocId] 是 08 选中的真实 docId；未登记到人物（映射未就绪）即
     * [OutreachGuardReasonCodes.PERSON_MISSING]（fail-closed，绝不静默放行）。
     */
    @Transactional
    fun reserveIntroduction(
        selectedDocId: String,
        recipient: String,
        profile: ExpertProfile?
    ): OutreachReserveOutcome {
        return reserve(selectedDocId, INTRODUCTION, recipient, profile, null)
    }

    @Transactional
    fun reserve(
        selectedDocId: String,
        mailType: String,
        recipient: String,
        profile: ExpertProfile?,
        historyReference: String? = null
    ): OutreachReserveOutcome {
        val docId = selectedDocId.trim()
        if (docId.isEmpty()) return denied(OutreachGuardReasonCodes.PERSON_MISSING, "empty docId")
        val member = discoveryPersonRepository.findMember(docId)
            ?: return denied(OutreachGuardReasonCodes.PERSON_MISSING, "no person mapping for $docId")
        val personId = member.personId
        val revision = lockPersonRevision(personId)
            ?: return denied(OutreachGuardReasonCodes.PERSON_MISSING, "person row missing")

        val normalizedRecipient = normalize(recipient)
        val now = LocalDateTime.now()
        val existing = lockGuardRow(personId, mailType)
        if (existing != null) {
            return when (existing.state) {
                OutreachGuardStates.SENT -> denied(OutreachGuardReasonCodes.ALREADY_SENT)
                OutreachGuardStates.UNKNOWN, OutreachGuardStates.SENDING ->
                    denied(OutreachGuardReasonCodes.UNKNOWN_PENDING, existing.state)
                OutreachGuardStates.RESERVED -> denied(OutreachGuardReasonCodes.IN_FLIGHT)
                OutreachGuardStates.SAFE_RETRY ->
                    if (existing.recipient != normalizedRecipient) {
                        denied(
                            OutreachGuardReasonCodes.ADDRESS_INELIGIBLE,
                            "safe retry bound to ${existing.recipient}"
                        )
                    } else {
                        val token = UUID.randomUUID().toString()
                        val updated = jdbcTemplate.update(
                            """
                            UPDATE discovery_person_outreach
                               SET state = ?, revision = ?, owner_token = ?, selected_doc_id = ?,
                                   recipient = ?, updated_at = ?
                             WHERE person_id = ? AND mail_type = ? AND state = ?
                            """.trimIndent(),
                            OutreachGuardStates.RESERVED, revision, token, docId, normalizedRecipient,
                            timestamp(now), personId, mailType, OutreachGuardStates.SAFE_RETRY
                        )
                        if (updated == 1) {
                            OutreachReserveOutcome.Granted(
                                OutreachGuardTicket(personId, mailType, revision, token, docId, normalizedRecipient)
                            )
                        } else {
                            denied(OutreachGuardReasonCodes.IN_FLIGHT)
                        }
                    }
                else -> denied(OutreachGuardReasonCodes.UNKNOWN_PENDING, existing.state)
            }
        }

        val history = collectHistory(personId, docId, normalizedRecipient, profile, historyReference)
        if (history.blocked) {
            return denied(history.code ?: OutreachGuardReasonCodes.HISTORY_PROTECTED, history.detail)
        }

        val token = UUID.randomUUID().toString()
        try {
            jdbcTemplate.update(
                """
                INSERT INTO discovery_person_outreach
                    (person_id, mail_type, state, revision, owner_token, selected_doc_id, recipient,
                     message_id, attempt_id, history_reference_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?, ?)
                """.trimIndent(),
                personId, mailType, OutreachGuardStates.RESERVED, revision, token, docId, normalizedRecipient,
                history.json, timestamp(now), timestamp(now)
            )
        } catch (ex: DuplicateKeyException) {
            // 并发插入：另一所有者已占位，绝不覆盖。
            log.debug("Concurrent guard reservation lost for {}: {}", personId, ex.message)
            return denied(OutreachGuardReasonCodes.IN_FLIGHT)
        }
        return OutreachReserveOutcome.Granted(
            OutreachGuardTicket(personId, mailType, revision, token, docId, normalizedRecipient)
        )
    }

    /**
     * I-2/I-3: 发送前取得唯一交付权。复核当前人物 revision、地址与发现资格后，
     * 以 expected revision/owner_token CAS RESERVED→SENDING。只有 [OutreachClaimOutcome.Claimed]
     * 允许调用 SMTP；任何失败都保持占位不释放。
     */
    @Transactional
    fun claimSending(
        ticket: OutreachGuardTicket,
        profile: ExpertProfile?,
        recipient: String
    ): OutreachClaimOutcome {
        val lockedRevision = lockPersonRevision(ticket.personId)
            ?: return OutreachClaimOutcome.Denied(OutreachGuardReasonCodes.PERSON_MISSING)
        if (lockedRevision != ticket.revision) {
            return OutreachClaimOutcome.Denied(OutreachGuardReasonCodes.REVISION_CHANGED)
        }
        val normalized = normalize(recipient)
        if (normalized != ticket.recipient) {
            return OutreachClaimOutcome.Denied(OutreachGuardReasonCodes.ADDRESS_INELIGIBLE, "recipient changed")
        }
        if (profile != null) {
            if (!RecipientScope.isDiscoveryOutreach(profile)) {
                return OutreachClaimOutcome.Denied(OutreachGuardReasonCodes.ADDRESS_INELIGIBLE, "no longer discovery")
            }
            if (profile.email.isNullOrBlank()) {
                return OutreachClaimOutcome.Denied(OutreachGuardReasonCodes.ADDRESS_INELIGIBLE, "empty email")
            }
        }
        if (emailSuppressionService?.isSuppressed(normalized) == true) {
            return OutreachClaimOutcome.Denied(OutreachGuardReasonCodes.ADDRESS_INELIGIBLE, "suppressed")
        }
        val updated = jdbcTemplate.update(
            """
            UPDATE discovery_person_outreach
               SET state = ?, updated_at = ?
             WHERE person_id = ? AND mail_type = ? AND state = ?
               AND revision = ? AND owner_token = ?
            """.trimIndent(),
            OutreachGuardStates.SENDING, timestamp(LocalDateTime.now()),
            ticket.personId, ticket.mailType, OutreachGuardStates.RESERVED, ticket.revision, ticket.ownerToken
        )
        return if (updated == 1) {
            OutreachClaimOutcome.Claimed
        } else {
            OutreachClaimOutcome.Denied(OutreachGuardReasonCodes.IN_FLIGHT)
        }
    }

    /**
     * I-2: 在成功的原事务中联动 SENT（与 mail_record 同事务）。CAS 失败不回滚成功事实 ——
     * 占位停在 SENDING（结果未核，不可重发）。
     */
    @Transactional
    fun markSent(ticket: OutreachGuardTicket, messageId: String?, attemptId: Long?): Boolean =
        jdbcTemplate.update(
            """
            UPDATE discovery_person_outreach
               SET state = ?, message_id = ?, attempt_id = ?, updated_at = ?
             WHERE person_id = ? AND mail_type = ? AND state = ?
               AND revision = ? AND owner_token = ?
            """.trimIndent(),
            OutreachGuardStates.SENT, messageId, attemptId, timestamp(LocalDateTime.now()),
            ticket.personId, ticket.mailType, OutreachGuardStates.SENDING, ticket.revision, ticket.ownerToken
        ) == 1

    /** I-2: SMTP 结果不明（异常 / SENDING 中断）→ UNKNOWN，永不自动重发。 */
    @Transactional
    fun markUnknown(ticket: OutreachGuardTicket): Boolean =
        jdbcTemplate.update(
            """
            UPDATE discovery_person_outreach
               SET state = ?, updated_at = ?
             WHERE person_id = ? AND mail_type = ? AND state IN (?, ?)
               AND revision = ? AND owner_token = ?
            """.trimIndent(),
            OutreachGuardStates.UNKNOWN, timestamp(LocalDateTime.now()),
            ticket.personId, ticket.mailType, OutreachGuardStates.SENDING, OutreachGuardStates.RESERVED,
            ticket.revision, ticket.ownerToken
        ) == 1

    /**
     * I-2: 明确未交付（未调用 SMTP 或 SMTP 明确未接受）→ SAFE_RETRY。地址绑定不变。
     */
    @Transactional
    fun markSafeRetry(ticket: OutreachGuardTicket): Boolean =
        jdbcTemplate.update(
            """
            UPDATE discovery_person_outreach
               SET state = ?, updated_at = ?
             WHERE person_id = ? AND mail_type = ? AND state IN (?, ?)
               AND revision = ? AND owner_token = ?
            """.trimIndent(),
            OutreachGuardStates.SAFE_RETRY, timestamp(LocalDateTime.now()),
            ticket.personId, ticket.mailType, OutreachGuardStates.SENDING, OutreachGuardStates.RESERVED,
            ticket.revision, ticket.ownerToken
        ) == 1

    /** 只读：当前占位状态（对账/对下游 10 暴露），无占位返回 null。 */
    fun stateOf(personId: String, mailType: String = INTRODUCTION): String? =
        jdbcTemplate.query(
            "SELECT state FROM discovery_person_outreach WHERE person_id = ? AND mail_type = ?",
            { rs: ResultSet, _: Int -> rs.getString("state") }, personId, mailType
        ).firstOrNull()

    /** 只读：当前占位行（对账用），无占位返回 null。 */
    fun findReservation(personId: String, mailType: String = INTRODUCTION): ReservationRow? =
        jdbcTemplate.query(
            "SELECT * FROM discovery_person_outreach WHERE person_id = ? AND mail_type = ?",
            { rs: ResultSet, _: Int ->
                ReservationRow(
                    personId = rs.getString("person_id"),
                    mailType = rs.getString("mail_type"),
                    state = rs.getString("state"),
                    revision = rs.getLong("revision"),
                    ownerToken = rs.getString("owner_token"),
                    selectedDocId = rs.getString("selected_doc_id"),
                    recipient = rs.getString("recipient"),
                    messageId = rs.getString("message_id"),
                    attemptId = rs.getObject("attempt_id") as? Long
                )
            },
            personId, mailType
        ).firstOrNull()

    // ── 内部 ─────────────────────────────────────────────────────────────────

    /**
     * I-3: 占位前的只读历史检查（全部成员）。先复用 08 的人物级准入/抑制/已联系判定，
     * 再补直连 SQL 的 mail_record / mail_send_attempt / alias 事实。任一命中即阻断，
     * 且不暴露/重写原历史。
     */
    private fun collectHistory(
        personId: String,
        selectedDocId: String,
        recipient: String,
        profile: ExpertProfile?,
        historyReference: String?
    ): HistoryFindings {
        val members = discoveryPersonRepository.findMembersByPerson(personId)
        val memberDocIds = (members.map { it.expertDocId } + selectedDocId).distinct()
        // 既有 contact / mail_send_attempt 以 orcid_id（normOrcid）为键，未必等于 esDocId；
        // 一并纳入当前档案 orcidId，保证选中成员的历史事实不因键形态不同而漏检。
        val lookupIds = (memberDocIds + listOfNotNull(profile?.orcidId?.takeIf { it.isNotBlank() })).distinct()
        val memberEmails = (members.mapNotNull { it.normalizedEmail }
            .map { normalize(it) } + listOfNotNull(profile?.email?.let { normalize(it) }, recipient)).distinct()

        // 1. 08 人物级准入/抑制/已联系（单一权威）。
        val recipientService = discoveryPersonRecipientService
        if (recipientService != null && profile != null) {
            val selection = recipientService.selectAdmission(listOf(profile))
            val target = (selection.included + selection.excluded).firstOrNull()
            if (target != null) {
                if (target.exclusionReasons.isNotEmpty()) {
                    return HistoryFindings(
                        blocked = true,
                        code = OutreachGuardReasonCodes.HISTORY_PROTECTED,
                        detail = target.exclusionReasons.joinToString(","),
                        json = jsonSummary(target.exclusionReasons, lookupIds, memberEmails, historyReference)
                    )
                }
                val selectedEmail = target.selectedEmail?.let { normalize(it) }
                if (selectedEmail == null || selectedEmail != recipient) {
                    return HistoryFindings(
                        blocked = true,
                        code = OutreachGuardReasonCodes.ADDRESS_INELIGIBLE,
                        detail = "current qualified address differs",
                        json = jsonSummary(listOf("ADDRESS_INELIGIBLE"), lookupIds, memberEmails, historyReference)
                    )
                }
            }
        }

        // 2. mail_record：已有 SENT 介绍信 —— 「已有成功」优先于笼统的「原联系人」表述。
        if (hasSentIntroduction(lookupIds)) {
            return HistoryFindings(
                blocked = true,
                code = OutreachGuardReasonCodes.ALREADY_SENT,
                detail = "introduction already sent",
                json = jsonSummary(listOf("ALREADY_SENT"), lookupIds, memberEmails, historyReference)
            )
        }

        // 3. mail_send_attempt：在途/成功/未知均阻断。
        val attemptStatus = ambiguousAttemptStatus(lookupIds)
        if (attemptStatus != null) {
            val code = if (attemptStatus == "SENT") OutreachGuardReasonCodes.ALREADY_SENT
            else OutreachGuardReasonCodes.UNKNOWN_PENDING
            return HistoryFindings(
                blocked = true,
                code = code,
                detail = "mail_send_attempt=$attemptStatus",
                json = jsonSummary(listOf(code), lookupIds, memberEmails, historyReference)
            )
        }

        // 4. 原联系人（绑定 / 同邮箱）。
        if (anyContactExists(lookupIds, memberEmails)) {
            return HistoryFindings(
                blocked = true,
                code = OutreachGuardReasonCodes.HISTORY_PROTECTED,
                detail = "existing contact",
                json = jsonSummary(listOf("EXISTING_CONTACT"), lookupIds, memberEmails, historyReference)
            )
        }

        // 5. 邮箱别名。
        if (anyAliasExists(memberEmails)) {
            return HistoryFindings(
                blocked = true,
                code = OutreachGuardReasonCodes.HISTORY_PROTECTED,
                detail = "existing email alias",
                json = jsonSummary(listOf("EXISTING_ALIAS"), lookupIds, memberEmails, historyReference)
            )
        }

        return HistoryFindings(
            blocked = false,
            code = null,
            detail = null,
            json = jsonSummary(emptyList(), lookupIds, memberEmails, historyReference)
        )
    }

    private fun anyContactExists(lookupIds: List<String>, memberEmails: List<String>): Boolean {
        val byDoc = countIn(
            "SELECT COUNT(*) FROM expert_contact WHERE orcid_id IN (%s)",
            lookupIds
        )
        if (byDoc > 0) return true
        val byEmail = countIn(
            "SELECT COUNT(*) FROM expert_contact WHERE expert_email IN (%s)",
            memberEmails
        )
        return byEmail > 0
    }

    private fun anyAliasExists(memberEmails: List<String>): Boolean =
        countIn("SELECT COUNT(*) FROM expert_email_alias WHERE normalized_email IN (%s)", memberEmails) > 0

    private fun hasSentIntroduction(memberDocIds: List<String>): Boolean {
        if (memberDocIds.isEmpty()) return false
        val placeholders = memberDocIds.joinToString(", ") { "?" }
        val sql = """
            SELECT COUNT(*) FROM mail_record mr
              JOIN expert_contact ec ON ec.id = mr.expert_contact_id
             WHERE ec.orcid_id IN ($placeholders)
               AND mr.direction = 'OUTBOUND'
               AND mr.mail_type = 'INTRODUCTION'
               AND mr.send_status = 'SENT'
        """.trimIndent()
        return jdbcTemplate.queryForObject(sql, Long::class.java, *memberDocIds.toTypedArray())!! > 0
    }

    private fun ambiguousAttemptStatus(memberDocIds: List<String>): String? {
        if (memberDocIds.isEmpty()) return null
        val placeholders = memberDocIds.joinToString(", ") { "?" }
        return jdbcTemplate.query(
            """
            SELECT status FROM mail_send_attempt
             WHERE mail_type = ? AND orcid_id IN ($placeholders)
               AND status IN (?, ?, ?)
            """.trimIndent(),
            { rs: ResultSet, _: Int -> rs.getString("status") },
            "INTRODUCTION", *memberDocIds.toTypedArray(),
            "SENT", "DELIVERY_IN_PROGRESS", "DELIVERY_UNKNOWN"
        ).firstOrNull()
    }

    private fun countIn(sqlTemplate: String, values: List<String>): Long {
        val distinct = values.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) return 0L
        val placeholders = distinct.joinToString(", ") { "?" }
        val sql = sqlTemplate.replace("%s", placeholders)
        return jdbcTemplate.queryForObject(sql, Long::class.java, *distinct.toTypedArray()) ?: 0L
    }

    private fun lockPersonRevision(personId: String): Long? =
        jdbcTemplate.query(
            "SELECT revision FROM discovery_person WHERE person_id = ? FOR UPDATE",
            { rs: ResultSet, _: Int -> rs.getLong("revision") },
            personId
        ).firstOrNull()

    private fun lockGuardRow(personId: String, mailType: String): GuardRow? =
        jdbcTemplate.query(
            "SELECT state, recipient FROM discovery_person_outreach WHERE person_id = ? AND mail_type = ? FOR UPDATE",
            { rs: ResultSet, _: Int -> GuardRow(rs.getString("state"), rs.getString("recipient")) },
            personId, mailType
        ).firstOrNull()

    private fun denied(code: String, detail: String? = null) = OutreachReserveOutcome.Denied(code, detail)

    private fun normalize(email: String): String = email.trim().lowercase(Locale.ROOT)

    private fun timestamp(now: LocalDateTime): Timestamp = Timestamp.valueOf(now)

    private fun jsonSummary(
        reasons: List<String>,
        memberDocIds: List<String>,
        memberEmails: List<String>,
        historyReference: String?
    ): String {
        val sb = StringBuilder()
        sb.append("{\"reasons\":[")
        reasons.forEachIndexed { index, reason ->
            if (index > 0) sb.append(',')
            sb.append('"').append(escape(reason)).append('"')
        }
        sb.append("],\"members\":[")
        memberDocIds.forEachIndexed { index, docId ->
            if (index > 0) sb.append(',')
            sb.append('"').append(escape(docId)).append('"')
        }
        sb.append("],\"emails\":[")
        memberEmails.forEachIndexed { index, email ->
            if (index > 0) sb.append(',')
            sb.append('"').append(escape(email)).append('"')
        }
        sb.append(']')
        if (!historyReference.isNullOrBlank()) {
            sb.append(",\"reference\":\"").append(escape(historyReference)).append('"')
        }
        sb.append('}')
        return sb.toString()
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private data class GuardRow(val state: String, val recipient: String)

    private data class HistoryFindings(
        val blocked: Boolean,
        val code: String?,
        val detail: String?,
        val json: String
    )

    /** 只读占位行（对账/下游 10）。 */
    data class ReservationRow(
        val personId: String,
        val mailType: String,
        val state: String,
        val revision: Long,
        val ownerToken: String,
        val selectedDocId: String,
        val recipient: String,
        val messageId: String?,
        val attemptId: Long?
    )

    companion object {
        /** 本期唯一受保护的发信类型（I-1）。 */
        const val INTRODUCTION = "INTRODUCTION"
    }
}
