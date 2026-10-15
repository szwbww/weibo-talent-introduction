package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonScope
import com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
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

    /**
     * I-3：构成**人物级持久保护**的状态集合。同时用于两个判断：
     * ① 10 的历史保护导入是否已就绪（存在即已保护，缺一即未保护）；
     * ② 10 的回滚提案是否必须保守拒绝（进行中/不确定/已交付都不得标为可回滚）。
     * `SAFE_RETRY` 不在集合内：它表示已证明未交付，可沿用原地址重试，不是保护。
     */
    val PROTECTED_STATES = setOf(SENT, UNKNOWN, SENDING, RESERVED)
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

/**
 * V-26: **本次尝试自己的**行身份（调用方在建行/复用行之后声明）。
 *
 * 健康首发先建 contact（建行即固化发件账号绑定）与 PREPARED attempt，再 claim；
 * 历史复核必须把这两行视为「本次写入」而非旧历史，否则正常首发把自己判成已联系并永久阻断。
 * 只排除**恰好这些 id**的行 —— 同人其他联系人/在途 attempt/已发信/别名仍一律阻断。
 * 未提供（null）时不做任何排除，语义与引入前逐字一致。
 */
data class OutreachAttemptIdentity(
    val contactId: Long? = null,
    val attemptId: Long? = null
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
 * I-3（10 的历史保护导入）：导入后的保护快照。
 * [state] 为空表示既无占位也无已证实历史（无保护）；[created] 表示本次调用是否落库；
 * [recipient] 是绑定的原地址（仅审计/展示用，不构成许可）。
 */
data class OutreachProtectionImport(
    val state: String,
    val created: Boolean,
    val recipient: String?
)

/**
 * 09（I-1/I-2/I-3）：`discovery_person_outreach` 的**唯一**写入点与首发原子门禁。
 *
 * - 唯一键 `(person_id, mail_type)` + 行级 `FOR UPDATE`（先锁人物行再锁占位行）保证多实例
 *   互斥；**不用** JVM `synchronized`，也不设锁超时/自动解锁。
 * - [reserveIntroduction] / [reserve] / [claimSending] / [markUnknown] / [markSafeRetry] 各自
 *   `REQUIRES_NEW` 独立提交：交付权的持久化（RESERVED/SENDING/UNKNOWN/SAFE_RETRY）先于 SMTP 落库，
 *   业务成功事务回滚不能撤销它。只有 [markSent] 用 `REQUIRED`，与成功的邮件业务写同事务。
 * - [reserve] 在同一事务内：锁人物行 → 锁占位行 → 只读历史检查（全部成员）→ 插入 RESERVED。
 *   既存 SENT/UNKNOWN/SENDING/RESERVED 一律不授予；SAFE_RETRY 仅对原地址重授，且重授前**同样**
 *   重跑整组历史检查（期间新增人工拒绝/已联系/别名/已发信即阻断）。
 * - 每次 reserve 与 claim 都要求画像可读且仍属**实时 discovered**（[DiscoveryPersonScope.isLiveDiscovered]）；
 *   已知人物画像不可读绝不退化为无 guard 普通邮件。
 * - [claimSending] 在发送前复核人物 revision、地址、实时标签、整组当前资格与历史，再用
 *   expected revision/owner_token CAS RESERVED→SENDING；失败即不发送。
 * - 历史复核按**本次尝试身份**（[OutreachAttemptIdentity]）排除调用方自己刚建/复用的
 *   contact 与 attempt 行：健康首发在建行后才 claim，自己的行不得被当成旧历史；同人**其他**
 *   联系人/在途 attempt/已发信/别名照旧阻断。
 * - [markSent] 供成功的原事务联动（与 mail_record 同事务）；[markUnknown] 标记不可重发；
 *   [markSafeRetry] 仅在明确未交付（未调用 SMTP 或 SMTP 明确未接受）时使用，且不改变绑定地址。
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
     * [attempt] 是本次尝试自己的行身份（V-26）：重试复用既有 NEW 联系人时必须声明，
     * 该行不参与「旧历史」判定。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun reserveIntroduction(
        selectedDocId: String,
        recipient: String,
        profile: ExpertProfile?,
        attempt: OutreachAttemptIdentity = OutreachAttemptIdentity()
    ): OutreachReserveOutcome {
        return reserve(selectedDocId, INTRODUCTION, recipient, profile, null, attempt)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun reserve(
        selectedDocId: String,
        mailType: String,
        recipient: String,
        profile: ExpertProfile?,
        historyReference: String? = null,
        attempt: OutreachAttemptIdentity = OutreachAttemptIdentity()
    ): OutreachReserveOutcome {
        val docId = selectedDocId.trim()
        if (docId.isEmpty()) return denied(OutreachGuardReasonCodes.PERSON_MISSING, "empty docId")
        val member = discoveryPersonRepository.findMember(docId)
            ?: return denied(OutreachGuardReasonCodes.PERSON_MISSING, "no person mapping for $docId")
        val personId = member.personId
        val revision = lockPersonRevision(personId)
            ?: return denied(OutreachGuardReasonCodes.PERSON_MISSING, "person row missing")

        // 已知人物画像不可读（null）或已脱离实时 discovered 范围：绝不授予，也不退化为普通邮件。
        if (profile == null) {
            return denied(OutreachGuardReasonCodes.PERSON_MISSING, "person profile unavailable for $docId")
        }
        if (!DiscoveryPersonScope.isLiveDiscovered(profile)) {
            return denied(OutreachGuardReasonCodes.ADDRESS_INELIGIBLE, "not live discovered")
        }

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
                        // SAFE_RETRY 也必须重跑整组历史检查：重授期间新增人工拒绝、身份冲突、
                        // 已联系、别名或已发信事实一律阻断，不因「上次证明未交付」而绕过。
                        val history = collectHistory(
                            personId, mailType, docId, normalizedRecipient, profile, historyReference, attempt
                        )
                        if (history.blocked) {
                            denied(history.code ?: OutreachGuardReasonCodes.HISTORY_PROTECTED, history.detail)
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
                    }
                else -> denied(OutreachGuardReasonCodes.UNKNOWN_PENDING, existing.state)
            }
        }

        val history = collectHistory(
            personId, mailType, docId, normalizedRecipient, profile, historyReference, attempt
        )
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
     * I-2/I-3: 发送前取得唯一交付权。复核当前人物 revision、地址、实时 discovered 标签与
     * 整组当前资格/历史后，以 expected revision/owner_token CAS RESERVED→SENDING。
     * 只有 [OutreachClaimOutcome.Claimed] 允许调用 SMTP；任何失败都保持占位不释放。
     *
     * [attempt] 是本次尝试自己的行身份（V-26）：调用方建行/复用行之后才 claim，历史复核必须
     * 排除这两行，否则健康首发会被自己的 NEW 联系人（建行即固化绑定）判成旧历史而永久阻断。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun claimSending(
        ticket: OutreachGuardTicket,
        profile: ExpertProfile?,
        recipient: String,
        attempt: OutreachAttemptIdentity = OutreachAttemptIdentity()
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
        // 画像不可读（已知人物）或已脱离实时 discovered 范围：不发送，也绝不退化为无 guard 普通邮件。
        if (profile == null) {
            return OutreachClaimOutcome.Denied(OutreachGuardReasonCodes.PERSON_MISSING, "person profile unavailable")
        }
        if (!DiscoveryPersonScope.isLiveDiscovered(profile)) {
            return OutreachClaimOutcome.Denied(OutreachGuardReasonCodes.ADDRESS_INELIGIBLE, "not live discovered")
        }
        if (profile.email.isNullOrBlank()) {
            return OutreachClaimOutcome.Denied(OutreachGuardReasonCodes.ADDRESS_INELIGIBLE, "empty email")
        }
        if (emailSuppressionService?.isSuppressed(normalized) == true) {
            return OutreachClaimOutcome.Denied(OutreachGuardReasonCodes.ADDRESS_INELIGIBLE, "suppressed")
        }
        // 当前整组资格与历史复核：人工拒绝、身份冲突、已联系、别名、已发信任一命中即不发送。
        val history = collectHistory(
            ticket.personId, ticket.mailType, ticket.selectedDocId, normalized, profile, null, attempt
        )
        if (history.blocked) {
            return OutreachClaimOutcome.Denied(history.code ?: OutreachGuardReasonCodes.HISTORY_PROTECTED, history.detail)
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

    /** I-2: SMTP 结果不明（异常 / SENDING 中断）→ UNKNOWN，永不自动重发。独立事务提交。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
     * I-2: 明确未交付（未调用 SMTP 或 SMTP 明确未接受）→ SAFE_RETRY。地址绑定不变。独立事务提交。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

    /**
     * I-3（详情/单发入口）：该 docId 是否已登记到人物（只读）。
     * 已知人物画像不可读时据此 fail-closed —— 绝不退化为无 guard 的普通邮件；
     * 未登记（非人物）返回 false，调用方保持原普通路径。
     */
    fun isKnownPerson(docId: String): Boolean {
        val trimmed = docId.trim()
        return trimmed.isNotEmpty() && discoveryPersonRepository.findMember(trimmed) != null
    }

    /** 只读：当前占位状态（对账/对下游 10 暴露），无占位返回 null。 */
    fun stateOf(personId: String, mailType: String = INTRODUCTION): String? =
        jdbcTemplate.query(
            "SELECT state FROM discovery_person_outreach WHERE person_id = ? AND mail_type = ?",
            { rs: ResultSet, _: Int -> rs.getString("state") }, personId, mailType
        ).firstOrNull()

    /**
     * I-3（10 的持久导入接缝）：把**已有成功/未知交付历史**持久化为人物级保护。
     *
     * - 只在没有占位行时写入；**不新建 `RESERVED` 占位**（占位只能由本次 ticket 的 CAS 消费，
     *   导入产生的占位会永久阻塞该人物首发）。
     * - 与调用方（10 的整组应用）同库同事务：本方法只做锁定 + 一次条件插入，异常不吞。
     * - 返回该人物当前的保护快照：[OutreachProtectionImport.state] 为空表示既无占位也无已证实历史
     *   （无保护），非空即表示保护已就绪（[created] 区分本次是否由本调用落库）。
     */
    @Transactional
    fun importExistingProtection(personId: String, mailType: String = INTRODUCTION): OutreachProtectionImport {
        val revision = lockPersonRevision(personId)
            ?: throw IllegalStateException("guard import: person row missing for $personId")
        lockGuardRow(personId, mailType)?.let { existing ->
            return OutreachProtectionImport(existing.state, false, existing.recipient)
        }
        val members = discoveryPersonRepository.findMembersByPerson(personId)
        val memberDocIds = members.map { it.expertDocId }
        val memberEmails = members.mapNotNull { it.normalizedEmail }
        // I-3（V-15）：历史事实的业务键不等于 ES docId —— 以 docId / 邮箱 / 别名命中的**联系人业务
        // orcid_id** 必须一并纳入，否则「同邮箱不同业务键」的既有 SENT/attempt 会漏导入（guard 行保持 null）。
        val historyKeys = historyKeys(memberDocIds, memberEmails)
        val state = historicalDeliveryState(historyKeys) ?: return OutreachProtectionImport("", false, null)
        val recipient = historicalRecipient(historyKeys, memberEmails)
        val selectedDocId = memberDocIds.minOrNull() ?: personId
        val now = LocalDateTime.now()
        try {
            jdbcTemplate.update(
                """
                INSERT INTO discovery_person_outreach
                    (person_id, mail_type, state, revision, owner_token, selected_doc_id, recipient,
                     message_id, attempt_id, history_reference_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?, ?)
                """.trimIndent(),
                personId, mailType, state, revision, UUID.randomUUID().toString(), selectedDocId, recipient,
                jsonSummary(listOf("IMPORTED_HISTORY"), memberDocIds, emptyList(), null),
                timestamp(now), timestamp(now)
            )
        } catch (ex: DuplicateKeyException) {
            // 并发导入：另一所有者已落库，读回其状态（绝不覆盖）。
            return lockGuardRow(personId, mailType)
                ?.let { OutreachProtectionImport(it.state, false, it.recipient) }
                ?: throw IllegalStateException("guard import raced without visible row for $personId", ex)
        }
        return OutreachProtectionImport(state, true, recipient)
    }

    /**
     * 只读：已有历史事实的完整业务键集合 —— 成员 ES docId，加上以 docId / 成员邮箱 / 别名邮箱命中的
     * **联系人业务 orcid_id**（业务键允许与 ES docId 不同；同邮箱/别名关联必须一并覆盖）。
     */
    private fun historyKeys(memberDocIds: List<String>, memberEmails: List<String>): List<String> {
        val keys = LinkedHashSet<String>()
        keys += memberDocIds.filter { it.isNotBlank() }
        val emails = memberEmails.map { normalize(it) }.filter { it.isNotBlank() }.distinct()
        if (emails.isNotEmpty()) {
            val placeholders = emails.joinToString(", ") { "?" }
            jdbcTemplate.query(
                "SELECT orcid_id FROM expert_contact WHERE expert_email IN ($placeholders)",
                { rs: ResultSet, _: Int -> rs.getString("orcid_id") }, *emails.toTypedArray()
            ).forEach { if (!it.isNullOrBlank()) keys += it }
            jdbcTemplate.query(
                """
                SELECT ec.orcid_id FROM expert_email_alias a
                  JOIN expert_contact ec ON ec.id = a.expert_contact_id
                 WHERE a.normalized_email IN ($placeholders)
                """.trimIndent(),
                { rs: ResultSet, _: Int -> rs.getString("orcid_id") }, *emails.toTypedArray()
            ).forEach { if (!it.isNullOrBlank()) keys += it }
        }
        return keys.filter { it.isNotBlank() }.distinct()
    }

    /** 只读：已有成功/未知交付历史（mail_record / mail_send_attempt 事实），无则 null。 */
    private fun historicalDeliveryState(memberDocIds: List<String>): String? {
        if (memberDocIds.isEmpty()) return null
        if (hasSentIntroduction(memberDocIds)) return OutreachGuardStates.SENT
        return when (ambiguousAttemptStatus(memberDocIds)) {
            null -> null
            "SENT" -> OutreachGuardStates.SENT
            else -> OutreachGuardStates.UNKNOWN
        }
    }

    /** 导入保护绑定的地址：优先历史联系人邮箱，其次人物成员的登记邮箱；无法确定时为空串。 */
    private fun historicalRecipient(memberDocIds: List<String>, memberEmails: List<String>): String {
        val ids = memberDocIds.filter { it.isNotBlank() }.distinct()
        if (ids.isNotEmpty()) {
            val placeholders = ids.joinToString(", ") { "?" }
            val historical = jdbcTemplate.query(
                "SELECT expert_email FROM expert_contact WHERE orcid_id IN ($placeholders) ORDER BY updated_at DESC",
                { rs: ResultSet, _: Int -> rs.getString("expert_email") },
                *ids.toTypedArray()
            ).firstOrNull()?.takeIf { it.isNotBlank() }
            if (historical != null) return normalize(historical)
        }
        return memberEmails.firstOrNull()?.let { normalize(it) } ?: ""
    }

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
     *
     * V-26/R-1：调用方声明的本次尝试行（[attempt]）**必须经服务端核实**后才允许从历史里排除 ——
     * 只认确实存在、且与本 ticket 的选中地址/发信类型/人物当前键空间真实关联的那一行；
     * 任意 id 不能解锁其他历史。区分「本次尝试」不依赖任何行状态（NEW/PREPARED），也不全局忽略联系人。
     */
    private fun collectHistory(
        personId: String,
        mailType: String,
        selectedDocId: String,
        recipient: String,
        profile: ExpertProfile?,
        historyReference: String?,
        attempt: OutreachAttemptIdentity = OutreachAttemptIdentity()
    ): HistoryFindings {
        val members = discoveryPersonRepository.findMembersByPerson(personId)
        val memberDocIds = (members.map { it.expertDocId } + selectedDocId).distinct()
        // 既有 contact / mail_send_attempt 以 orcid_id（normOrcid）为键，未必等于 esDocId；
        // 一并纳入当前档案 orcidId，保证选中成员的历史事实不因键形态不同而漏检。
        val lookupIds = (memberDocIds + listOfNotNull(profile?.orcidId?.takeIf { it.isNotBlank() })).distinct()
        val memberEmails = (members.mapNotNull { it.normalizedEmail }
            .map { normalize(it) } + listOfNotNull(profile?.email?.let { normalize(it) }, recipient)).distinct()
        // V-26/R-1：核实后的「本次尝试自己的行」——未通过核实（不存在/不属于本 ticket 的选中地址与键空间）
        // 即视为没有声明，一切历史照旧阻断。
        val ownContactId = verifiedOwnContactId(lookupIds, recipient, attempt.contactId)
        val ownAttemptId = verifiedOwnAttemptId(lookupIds, mailType, attempt.attemptId)

        // 1. 08 人物级准入/抑制/已联系（单一权威）。
        val recipientService = discoveryPersonRecipientService
        if (recipientService != null && profile != null) {
            val selection = recipientService.selectAdmission(listOf(profile))
            val target = (selection.included + selection.excluded).firstOrNull()
            if (target != null) {
                val ownContactOnly = ownContactId != null &&
                    target.exclusionReasons.isNotEmpty() &&
                    target.exclusionReasons.all { it == PersonRecipientReasonKeys.ALREADY_CONTACTED } &&
                    isOwnBoundContactTheOnlyOne(ownContactId, lookupIds, memberEmails)
                if (target.exclusionReasons.isNotEmpty() && !ownContactOnly) {
                    return HistoryFindings(
                        blocked = true,
                        code = OutreachGuardReasonCodes.HISTORY_PROTECTED,
                        detail = target.exclusionReasons.joinToString(","),
                        json = jsonSummary(target.exclusionReasons, lookupIds, memberEmails, historyReference)
                    )
                }
                // 本次自己的行已核实为**唯一**绑定联系人时，被排除的选择结果不带选中地址 ——
                // 此时以 ticket 绑定的地址为准（该地址已在 verifiedOwnContactId 中与自己的行核对过）。
                if (!ownContactOnly) {
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

        // 3. mail_send_attempt：在途/成功/未知均阻断（核实后的本次尝试行除外）。
        val attemptStatus = ambiguousAttemptStatus(lookupIds, ownAttemptId)
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

        // 4. 原联系人（绑定 / 同邮箱），核实后的本次尝试行除外。
        if (anyContactExists(lookupIds, memberEmails, ownContactId)) {
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

    private fun anyContactExists(
        lookupIds: List<String>,
        memberEmails: List<String>,
        ownContactId: Long? = null
    ): Boolean {
        val byDoc = countIn(
            "SELECT COUNT(*) FROM expert_contact WHERE orcid_id IN (%s)",
            lookupIds,
            ownContactId
        )
        if (byDoc > 0) return true
        val byEmail = countIn(
            "SELECT COUNT(*) FROM expert_contact WHERE expert_email IN (%s)",
            memberEmails,
            ownContactId
        )
        return byEmail > 0
    }

    /**
     * V-26/R-1: 核实调用方声明的「本次尝试自己的 contact 行」—— 必须**确实存在**、其地址等于本
     * ticket 的选中地址（[recipient]，服务端持有于占位行），且其业务键属于该人物当前键空间
     * （[lookupIds]）。未通过核实返回 null：任意 contactId 不得解锁其他历史。
     */
    private fun verifiedOwnContactId(lookupIds: List<String>, recipient: String, declaredContactId: Long?): Long? {
        if (declaredContactId == null) return null
        val ids = lookupIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return null
        val placeholders = ids.joinToString(", ") { "?" }
        return jdbcTemplate.query(
            "SELECT id FROM expert_contact WHERE id = ? AND expert_email = ? AND orcid_id IN ($placeholders)",
            { rs: ResultSet, _: Int -> rs.getLong("id") },
            *arrayOf<Any>(declaredContactId, normalize(recipient), *ids.toTypedArray())
        ).firstOrNull()
    }

    /**
     * V-26/R-1: 核实调用方声明的「本次尝试自己的 attempt 行」—— 必须**确实存在**、其 mail_type
     * 等于本 ticket 的发信类型，且其 orcid_id 属于该人物当前键空间。未通过核实返回 null。
     */
    private fun verifiedOwnAttemptId(lookupIds: List<String>, mailType: String, declaredAttemptId: Long?): Long? {
        if (declaredAttemptId == null) return null
        val ids = lookupIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return null
        val placeholders = ids.joinToString(", ") { "?" }
        return jdbcTemplate.query(
            "SELECT id FROM mail_send_attempt WHERE id = ? AND mail_type = ? AND orcid_id IN ($placeholders)",
            { rs: ResultSet, _: Int -> rs.getLong("id") },
            *arrayOf<Any>(declaredAttemptId, mailType, *ids.toTypedArray())
        ).firstOrNull()
    }

    /**
     * V-26: 本次尝试自己的 [ownContactId] 是否**确实**是绑定联系人、且除它之外没有其他绑定联系人。
     * 只有在这种正向归因成立时，08 的 ALREADY_CONTACTED 命中才允许被忽略 —— 自己的行不构成旧历史。
     */
    private fun isOwnBoundContactTheOnlyOne(
        ownContactId: Long,
        lookupIds: List<String>,
        memberEmails: List<String>
    ): Boolean {
        val ownBound = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expert_contact WHERE id = ? AND bound_sender_account_code IS NOT NULL",
            Long::class.java,
            ownContactId
        ) ?: 0L
        if (ownBound == 0L) return false
        return !hasBoundContactOtherThan(lookupIds, memberEmails, ownContactId)
    }

    /**
     * V-26: 除本次尝试自己的 [ownContactId] 外，是否还存在**绑定**发件账号的联系人。
     */
    private fun hasBoundContactOtherThan(
        lookupIds: List<String>,
        memberEmails: List<String>,
        ownContactId: Long
    ): Boolean =
        countIn(
            "SELECT COUNT(*) FROM expert_contact WHERE orcid_id IN (%s) AND bound_sender_account_code IS NOT NULL",
            lookupIds,
            ownContactId
        ) > 0 || countIn(
            "SELECT COUNT(*) FROM expert_contact WHERE expert_email IN (%s) AND bound_sender_account_code IS NOT NULL",
            memberEmails,
            ownContactId
        ) > 0

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

    private fun ambiguousAttemptStatus(memberDocIds: List<String>, ownAttemptId: Long? = null): String? {
        if (memberDocIds.isEmpty()) return null
        val placeholders = memberDocIds.joinToString(", ") { "?" }
        // V-26：本次尝试自己的 attempt 行（PREPARED）不是旧历史，按 id 排除；其余行照旧阻断。
        val exclusion = if (ownAttemptId != null) " AND id <> ?" else ""
        val args: Array<Any> = if (ownAttemptId != null) {
            arrayOf<Any>(
                "INTRODUCTION", *memberDocIds.toTypedArray(),
                "SENT", "DELIVERY_IN_PROGRESS", "DELIVERY_UNKNOWN", ownAttemptId
            )
        } else {
            arrayOf<Any>(
                "INTRODUCTION", *memberDocIds.toTypedArray(),
                "SENT", "DELIVERY_IN_PROGRESS", "DELIVERY_UNKNOWN"
            )
        }
        return jdbcTemplate.query(
            """
            SELECT status FROM mail_send_attempt
             WHERE mail_type = ? AND orcid_id IN ($placeholders)
               AND status IN (?, ?, ?)$exclusion
            """.trimIndent(),
            { rs: ResultSet, _: Int -> rs.getString("status") },
            *args
        ).firstOrNull()
    }

    private fun countIn(sqlTemplate: String, values: List<String>, excludeId: Long? = null): Long {
        val distinct = values.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) return 0L
        val placeholders = distinct.joinToString(", ") { "?" }
        val exclusion = if (excludeId != null) " AND id <> ?" else ""
        val args: Array<Any> = if (excludeId != null) {
            arrayOf<Any>(*distinct.toTypedArray(), excludeId)
        } else {
            arrayOf<Any>(*distinct.toTypedArray())
        }
        return jdbcTemplate.queryForObject(
            sqlTemplate.replace("%s", placeholders) + exclusion, Long::class.java, *args
        ) ?: 0L
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
