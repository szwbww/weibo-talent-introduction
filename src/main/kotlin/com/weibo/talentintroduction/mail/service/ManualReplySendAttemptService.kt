package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
import com.weibo.talentintroduction.campaign.domain.MailSendAttempt
import com.weibo.talentintroduction.campaign.domain.MeetingCalendarInput
import com.weibo.talentintroduction.campaign.repository.MailSendAttemptRepository
import com.weibo.talentintroduction.campaign.service.MeetingCalendarService
import com.weibo.talentintroduction.llm.service.TrustReplyDiagnostics
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailRecordQaRule
import com.weibo.talentintroduction.mail.domain.MailRecordRagFact
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRagFactRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.DelayedManualReplyRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID

@Service
class ManualReplySendAttemptService(
    private val attemptRepository: MailSendAttemptRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val mailRecordQaRuleRepository: MailRecordQaRuleRepository,
    private val operatorActionLogService: OperatorActionLogService,
    // fast-p 02 (I-2): 成功事务内创建排期的唯一协作件（01 createFromSentMail 要求
    // 调用方已在事务中，本方法即调用方）。
    private val meetingCalendarService: MeetingCalendarService,
    private val mailReplyDraftService: MailReplyDraftService? = null,
    private val mailReviewDraftService: MailReviewDraftService? = null,
    // fast-p 02 (T3/I-5)：RAG 存证写入必须在 SENT 同一事务内完成。可空默认值保持既有
    // 直接构造的单元测试零改动；RAG payload 入口在事务内 requireNotNull 防御性校验。
    private val mailRecordRagFactRepository: MailRecordRagFactRepository? = null,
    // fast-p 03 (I-3)：排队任务与同步入口互斥的唯一读取点。只读仓储（不是 service），
    // 避免与 DelayedManualReplyService 形成构造环；未接线时同步行为逐字不变。
    private val delayedManualReplyRepository: DelayedManualReplyRepository? = null
) {
    companion object {
        private val log = LoggerFactory.getLogger(ManualReplySendAttemptService::class.java)
        private const val SCHEMA_VERSION = 1
        private const val FINGERPRINT_CONTENT_TYPE = "application/x-manual-rich-fingerprint-v1"
        private const val MANUAL_RICH_MAIL_TYPE_PREFIX = "MANUAL_RICH:"
        private const val MESSAGE_ID_TEMPLATE = "<manual-rich-%s@weibo.com>"
        private const val MAX_ERROR_SUMMARY_LENGTH = 500
        /** 通用附件指纹段域标记（fast-p 05，I-2）：独立版本段，不复用 calendar 段。 */
        private const val OUTBOUND_ATTACHMENTS_SEGMENT = "outbound-attachments-v1"
        // 无来信会话自由回信的 attempt 短键：requestId 派生、内容无关（I-4）。
        // mail_type 列宽 VARCHAR(50)：前缀 20 字符 + sha256 前 30 位 = 恰好 50。
        const val CONVERSATION_REQUEST_PREFIX = "MANUAL_RICH_REQUEST:"
        const val CONVERSATION_REQUEST_HEX_LENGTH = 30
        /** 会话回信线程/审计锚点类型前缀（真实 mail_record，绝不伪造 inbound id）。 */
        const val SOURCE_ANCHOR_PREFIX = "MAIL_RECORD:"
    }

    data class SendPayload(
        val orcidId: String,
        val contactId: Long,
        /** 来信路径 = 真实 inbound_mail_processing.id；会话回信路径 = null（I-3）。 */
        val inboundProcessingId: Long?,
        val accountCode: String,
        val normalizedRecipient: String,
        val subject: String,
        val finalText: String,
        val finalHtml: String,
        val inReplyTo: String?,
        val canonicalQaRuleIds: List<Long>,
        val primaryRuleId: Long?,
        /**
         * 会话回信路径的真实线程锚点（"MAIL_RECORD:<mailRecord.id>"，I-3）。
         * 来信路径必须为 null；与 inboundProcessingId 恰有一个非空。
         */
        val sourceAnchor: String? = null,
        /** 会话回信幂等 requestId（可被 UUID.fromString 解析，I-4）；来信路径恒 null。 */
        val idempotencyRequestId: String? = null,
        /** fast-p 02 (I-2/I-3)：会议日历附件快照；null=无日历（原发送身份字节流
         *  完全不变）。非 null 时把语义指纹并入发送身份：同配置不重复发，
         *  改时间/链接等语义获得不同发送身份。 */
        val calendarAttachment: CalendarAttachmentSnapshot? = null,
        /**
         * fast-p 02 (I-1/I-2)：与 calendarAttachment 同一次校验产物派生的结构化排期
         * 输入（只含已校验 startUtc/endUtc/zoomUrl，不接收浏览器排期对象）。
         * 默认 null：普通发送与无会议回信的身份、行为逐字不变；非 null 时
         * finalizeSuccess 在成功事务内创建排期，二者同有同无。
         * 不进入指纹（时间/链接语义已由 calendarAttachment.semanticSha256 覆盖）。
         */
        val meetingEvent: MeetingCalendarInput? = null,
        /**
         * fast-p 05 (I-1/I-2)：本次发送的有序通用附件快照（04
         * `OutboundAttachmentService.resolveForSend` 产物，选取顺序；不含上传 UUID/
         * 磁盘路径/上传时间）。默认空：不进入指纹、不写新列，普通发送身份与行为逐字
         * 不变；非空时追加 outbound-attachments-v1 段，并在两个 finalize 的四分支
         * 显式写入本次快照（空即显式 null）。
         */
        val outboundAttachments: List<OutboundAttachmentSnapshot> = emptyList()
    ) {
        // Server-only raw snapshot, deliberately absent from the fingerprint byte stream.
        internal var draftSendRef: ValidatedDraftSendRef? = null
        internal var reviewSendRef: MailReviewSendRef? = null
        /**
         * fast-p 02 (T3/I-5)：RAG 存证（请求顺序的 fact_code + 发送时语料指纹）。
         * server-only、不进入 computeFingerprint；finalizeSuccess 在 SENT 同一事务内
         * 写入 mail_record_rag_fact。null 表示本次发送无 RAG 证据（空列表同样不写行）。
         */
        internal var ragEvidence: RagEvidence? = null
    }

    /** RAG 存证载荷（有序、可重复；绝不进入指纹/审计正文）。 */
    internal data class RagEvidence(
        val factCodes: List<String>,
        val corpusFingerprint: String
    )

    /** findCompletedByRequestId 命中的已完成会话回信（attempt SENT + 唯一 mail_record）。 */
    data class CompletedOutboundReply(
        val attemptId: Long,
        val attemptMessageId: String,
        val attemptAccountCode: String,
        val mailRecord: MailRecord
    )

    data class Fingerprint(
        val fullHex: String,
        val shortKey: String,
        val messageId: String
    )

    enum class ClaimResult {
        CLAIMED,
        DEDUP_SENT,
        SAFE_RETRY_CLAIMED,
        IN_PROGRESS,
        UNKNOWN,
        PERMANENT_FAILED,
    }

    data class ClaimedAttempt(
        val attemptId: Long,
        val messageId: String,
        val result: ClaimResult
    )

    /**
     * fast-p 03（T1/I-3）：异步排队的 attempt 预留结果。
     *
     * 与 [prepareAndClaim] 的区别是**绝不**把状态推进到 DELIVERY_IN_PROGRESS：
     * 排队阶段只允许 attempt 保持 PREPARED（或既有安全失败态），领取由
     * [claimDelayed] 在 worker 的短事务里完成。`state` 是锁后重新读取的真实状态，
     * 调用方必须按它收敛/拒绝，不能把「已有任务」当成可重新 claim。
     */
    enum class DelayedReservationState {
        PREPARED,
        DELIVERY_IN_PROGRESS,
        SENT,
        DELIVERY_UNKNOWN,
        FAILED,
        FAILED_SAFE_TO_RETRY
    }

    data class DelayedReservation(
        val attemptId: Long,
        val messageId: String,
        val state: DelayedReservationState
    )

    /**
     * fast-p 03（T1/I-3）：延迟发送的 attempt 预留（MANDATORY）。
     *
     * 必须在调用方事务中执行：源行锁 → attempt 插入/锁定 → job 插入/绑定 → 审核事件
     * 必须一起提交或回滚。复用的身份定义与 [prepareAndClaim] 完全相同
     * （[computeFingerprint]、唯一键冲突、Message-ID 生成与 payload 比较），因此排队
     * 任务和同步入口指向同一行 attempt，旧指纹字节保持不变。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun reserveDelayed(payload: SendPayload): DelayedReservation {
        require(payload.draftSendRef == null || payload.reviewSendRef == null) { "Conflicting draft references" }
        validateReviewPayload(payload)
        val fingerprint = computeFingerprint(payload)
        val now = LocalDateTime.now()
        attemptRepository.insertIgnore(
            orcidId = payload.orcidId,
            mailType = fingerprint.shortKey,
            accountCode = payload.accountCode,
            messageId = fingerprint.messageId,
            status = MailSendAttemptStatus.PREPARED,
            recipient = payload.normalizedRecipient,
            subject = payload.subject,
            body = fingerprint.fullHex,
            contentType = FINGERPRINT_CONTENT_TYPE,
            createdAt = now,
            updatedAt = now
        )
        val attempt = attemptRepository.findByOrcidIdAndMailTypeForUpdate(payload.orcidId, fingerprint.shortKey)
            ?: throw IllegalStateException(
                "Mail send attempt not found after delayed reservation: orcidId=${payload.orcidId} " +
                    "mailType=${fingerprint.shortKey}"
            )
        requireFingerprintIdentity(attempt, payload, fingerprint)
        return DelayedReservation(
            attemptId = requireNotNull(attempt.id),
            messageId = attempt.messageId,
            state = when (attempt.status) {
                MailSendAttemptStatus.PREPARED -> DelayedReservationState.PREPARED
                MailSendAttemptStatus.DELIVERY_IN_PROGRESS -> DelayedReservationState.DELIVERY_IN_PROGRESS
                MailSendAttemptStatus.SENT -> DelayedReservationState.SENT
                MailSendAttemptStatus.DELIVERY_UNKNOWN -> DelayedReservationState.DELIVERY_UNKNOWN
                MailSendAttemptStatus.FAILED_SAFE_TO_RETRY -> DelayedReservationState.FAILED_SAFE_TO_RETRY
                MailSendAttemptStatus.FAILED -> DelayedReservationState.FAILED
                else -> throw IllegalStateException("Unsupported attempt status ${attempt.status}")
            }
        )
    }

    /**
     * fast-p 03（T2/I-2）：worker 领取前的 attempt 锁（MANDATORY）。
     *
     * 必须由调用方先持有源草稿/审核锁：attempt 锁先于 job 锁。返回锁后重新读取的行，
     * 调用方必须再次核对 id/status，不能信任加锁前的无锁读取。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun lockDelayedAttempt(attemptId: Long): MailSendAttempt {
        val observed = attemptRepository.findById(attemptId).orElseThrow {
            IllegalStateException("Mail send attempt not found: $attemptId")
        }
        val locked = attemptRepository.findByOrcidIdAndMailTypeForUpdate(observed.orcidId, observed.mailType)
            ?: throw IllegalStateException("Mail send attempt not found after locking: $attemptId")
        check(locked.id == attemptId) { "Mail send attempt identity changed while locking" }
        return locked
    }

    /**
     * fast-p 03（T2/I-2）：WAITING 领取时的 attempt 状态 CAS（MANDATORY）。
     *
     * 只有 PREPARED / FAILED_SAFE_TO_RETRY 可以进入 DELIVERY_IN_PROGRESS；与 job 的
     * `WAITING→SENDING` 在同一事务提交，任一步失败都必须整体回滚。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun claimDelayed(attemptId: Long, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val attempt = lockDelayedAttempt(attemptId)
        return when (attempt.status) {
            MailSendAttemptStatus.PREPARED, MailSendAttemptStatus.FAILED_SAFE_TO_RETRY ->
                attemptRepository.claimStatus(
                    attemptId,
                    attempt.status,
                    MailSendAttemptStatus.DELIVERY_IN_PROGRESS,
                    now
                ) > 0
            else -> false
        }
    }

    /** fast-p 03（T4/I-5）：超时恢复把仍在途的 attempt 收敛为 UNKNOWN（不重发）。 */
    @Transactional(propagation = Propagation.MANDATORY)
    fun recoverDelayedUnknown(attemptId: Long, now: LocalDateTime = LocalDateTime.now()): Boolean =
        attemptRepository.claimStatus(
            attemptId,
            MailSendAttemptStatus.DELIVERY_IN_PROGRESS,
            MailSendAttemptStatus.DELIVERY_UNKNOWN,
            now
        ) > 0

    /** fast-p 03（T2）：投递前已知失败把 attempt 标为可安全重试（绝不伪造 SENT）。 */
    @Transactional(propagation = Propagation.MANDATORY)
    fun failDelayedBeforeDelivery(attemptId: Long, errorSummary: String, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val attempt = lockDelayedAttempt(attemptId)
        if (attempt.status != MailSendAttemptStatus.PREPARED &&
            attempt.status != MailSendAttemptStatus.FAILED_SAFE_TO_RETRY
        ) {
            return false
        }
        return attemptRepository.claimStatus(
            attemptId,
            attempt.status,
            MailSendAttemptStatus.FAILED_SAFE_TO_RETRY,
            now
        ) > 0
    }

    /** 排队任务（只读）：同步入口与恢复路径共用的唯一判断源。 */
    internal fun delayedJobFor(attemptId: Long): DelayedManualReplyRow? =
        delayedManualReplyRepository?.findByAttemptId(attemptId)

    fun computeFingerprint(payload: SendPayload): Fingerprint {
        val messageId = String.format(MESSAGE_ID_TEMPLATE, UUID.randomUUID().toString().replace("-", ""))
        // I-5: 来信路径未带新字段时，原首段仍是原始数字 inboundProcessingId.toString()，
        // 后续字段与顺序逐字不变，部署前后同一来信/正文产生同一 fullHex/shortKey。
        // I-3/I-4: 会话回信路径首段写真实 sourceAnchor（"MAIL_RECORD:<id>"），
        // 短键改由 requestId 派生；两路径指纹身份互不混淆。
        val inboundId = payload.inboundProcessingId
        val firstSegment = when {
            inboundId != null -> {
                require(payload.sourceAnchor == null) {
                    "Mail send attempt fingerprint conflict: sourceAnchor must be null when inboundProcessingId is set"
                }
                require(payload.idempotencyRequestId == null) {
                    "Mail send attempt fingerprint conflict: idempotencyRequestId must be null when inboundProcessingId is set"
                }
                inboundId.toString()
            }
            else -> {
                val sourceAnchor = requireNotNull(payload.sourceAnchor) {
                    "Mail send attempt requires sourceAnchor when no inbound processing id"
                }
                val requestId = requireNotNull(payload.idempotencyRequestId) {
                    "Mail send attempt requires idempotencyRequestId when no inbound processing id"
                }
                // 防御性双检：入口已用 UUID.fromString(...).toString() 规范化，此处仍须可解析
                UUID.fromString(requestId.trim())
                sourceAnchor
            }
        }
        val data = ByteArrayOutputStream()
        appendLengthPrefix(data, SCHEMA_VERSION.toString())
        appendLengthPrefix(data, firstSegment)
        appendLengthPrefix(data, payload.contactId.toString())
        appendLengthPrefix(data, payload.orcidId)
        appendLengthPrefix(data, payload.accountCode)
        appendLengthPrefix(data, payload.normalizedRecipient)
        appendLengthPrefix(data, payload.subject)
        appendLengthPrefix(data, payload.finalText)
        appendLengthPrefix(data, payload.finalHtml)
        appendLengthPrefix(data, payload.inReplyTo ?: "")
        appendLengthPrefix(data, payload.canonicalQaRuleIds.joinToString(","))
        // fast-p 02 (I-2)：calendar 只在非 null 时于全部原字段之后追加两段
        // （域标记 + 01 生成器语义 sha256）；null 时以上 11 段字节流与旧实现
        // 逐字一致，shortKey 仍为 MANUAL_RICH:+32。
        payload.calendarAttachment?.let { calendar ->
            appendLengthPrefix(data, "meeting-calendar-v1")
            appendLengthPrefix(data, calendar.semanticSha256)
        }
        // fast-p 05 (I-2)：通用附件只在非空时于既有全部段之后追加独立版本段
        // （域标记 + 数量 + 逐项 filename/contentType/byteLength/sha256，全部长度前缀
        // 编码，不用带分隔符的字符串拼接）。上传 UUID/磁盘路径/上传时间不进入
        // 身份：同字节同文件名重传（新上传 id）得到同一 fullHex/shortKey。
        // 空列表不追加任何字节，无附件与仅 ICS 的既有 hash 逐字不变。
        if (payload.outboundAttachments.isNotEmpty()) {
            appendLengthPrefix(data, OUTBOUND_ATTACHMENTS_SEGMENT)
            appendLengthPrefix(data, payload.outboundAttachments.size.toString())
            payload.outboundAttachments.forEach { attachment ->
                appendLengthPrefix(data, attachment.filename)
                appendLengthPrefix(data, attachment.contentType)
                appendLengthPrefix(data, attachment.byteLength.toString())
                appendLengthPrefix(data, attachment.sha256)
            }
        }
        payload.reviewSendRef?.let {
            appendLengthPrefix(data, "review-draft-v1")
            appendLengthPrefix(data, it.draft.id.toString())
            appendLengthPrefix(data, it.draft.version.toString())
        }
        val fullHex = sha256Hex(data.toByteArray())
        val shortKey = if (inboundId != null) {
            MANUAL_RICH_MAIL_TYPE_PREFIX + fullHex.take(32)
        } else {
            conversationRequestShortKey(requireNotNull(payload.idempotencyRequestId))
        }
        return Fingerprint(
            fullHex = fullHex,
            shortKey = shortKey,
            messageId = messageId
        )
    }

    /** requestId 规范化：trim 后必须可被 UUID.fromString 解析，输出 canonical 小写形式。 */
    fun canonicalConversationRequestId(value: String): String =
        UUID.fromString(value.trim()).toString()

    /**
     * 会话回信 attempt 短键：MANUAL_RICH_REQUEST: + sha256(canonicalRequestId UTF-8) 前
     * CONVERSATION_REQUEST_HEX_LENGTH 位。内容无关，同一 requestId 恒定（I-4）。
     */
    fun conversationRequestShortKey(requestId: String): String {
        val canonical = canonicalConversationRequestId(requestId)
        return CONVERSATION_REQUEST_PREFIX +
            sha256Hex(canonical.toByteArray(Charsets.UTF_8)).take(CONVERSATION_REQUEST_HEX_LENGTH)
    }

    /**
     * 会话回信幂等收敛（I-4）：按 requestId 短键读取已完成 attempt；仅当 attempt 为 SENT
     * 且其唯一 mail_record 存在时返回。其余状态（IN_PROGRESS/UNKNOWN/FAILED…）返回空，
     * 由调用方继续既有 claim/碰撞/fail-closed 逻辑（I-10）。
     */
    fun findCompletedByRequestId(orcidId: String, requestId: String): CompletedOutboundReply? {
        val shortKey = conversationRequestShortKey(requestId)
        val attempt = attemptRepository.findByOrcidIdAndMailType(orcidId, shortKey) ?: return null
        if (attempt.status != MailSendAttemptStatus.SENT) return null
        val attemptId = attempt.id ?: return null
        val record = mailRecordRepository.findByMailSendAttemptId(attemptId) ?: return null
        return CompletedOutboundReply(
            attemptId = attemptId,
            attemptMessageId = attempt.messageId,
            attemptAccountCode = attempt.accountCode,
            mailRecord = record
        )
    }

    internal fun findCompletedAttempt(attemptId: Long): CompletedOutboundReply? {
        val attempt = attemptRepository.findById(attemptId).orElse(null) ?: return null
        if (attempt.status != MailSendAttemptStatus.SENT) return null
        val record = mailRecordRepository.findByMailSendAttemptId(attemptId) ?: return null
        return CompletedOutboundReply(attemptId,attempt.messageId,attempt.accountCode,record)
    }

    /** Exact completed-payload lookup only: never reserves an attempt or touches draft state. */
    @Transactional
    fun findCompletedByPayload(payload: SendPayload): CompletedOutboundReply? {
        val review = payload.reviewSendRef?.let { reviewService().lockForSend(it) }
        validateReviewPayload(payload)
        val fingerprint = computeFingerprint(payload)
        val attempt = attemptRepository.findByOrcidIdAndMailType(payload.orcidId, fingerprint.shortKey)
            ?: return null
        requireFingerprintIdentity(attempt, payload, fingerprint)
        if (attempt.status != MailSendAttemptStatus.SENT) return null
        val attemptId = attempt.id ?: return null
        if (review != null) {
            check(review.sendAttemptId == attemptId) { "Review is not bound to completed attempt" }
            validateReviewRecord(payload, record = mailRecordRepository.findByMailSendAttemptId(attemptId)
                ?: error("Completed review record missing"))
        }
        val record = mailRecordRepository.findByMailSendAttemptId(attemptId) ?: return null
        return CompletedOutboundReply(attemptId, attempt.messageId, attempt.accountCode, record)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    fun prepareAndClaim(payload: SendPayload): ClaimedAttempt {
        require(payload.draftSendRef == null || payload.reviewSendRef == null) { "Conflicting draft references" }
        val reviewRef = payload.reviewSendRef
        val review = reviewRef?.let { reviewService().lockForSend(it) }
        validateReviewPayload(payload)
        val fingerprint = computeFingerprint(payload)
        if (review?.sendAttemptId != null) {
            val bound = attemptRepository.findById(review.sendAttemptId).orElseThrow { IllegalStateException("Review attempt missing") }
            require(bound.body == fingerprint.fullHex && bound.recipient == payload.normalizedRecipient &&
                bound.contentType == FINGERPRINT_CONTENT_TYPE && bound.accountCode == payload.accountCode &&
                bound.orcidId == payload.orcidId && bound.mailType == fingerprint.shortKey) { "Review attempt identity mismatch" }
            val result = when (bound.status) {
                MailSendAttemptStatus.SENT -> {
                    validateReviewRecord(payload, mailRecordRepository.findByMailSendAttemptId(requireNotNull(bound.id))
                        ?: error("Completed review record missing"))
                    ClaimResult.DEDUP_SENT
                }
                MailSendAttemptStatus.DELIVERY_UNKNOWN -> ClaimResult.UNKNOWN
                MailSendAttemptStatus.DELIVERY_IN_PROGRESS -> ClaimResult.IN_PROGRESS
                else -> null
            }
            if (result != null) return ClaimedAttempt(requireNotNull(bound.id), bound.messageId, result)
        }
        if (review != null) reviewService().validateSendContext(review, requireNotNull(reviewRef))
        val draftRef = payload.draftSendRef
        val drafts = draftRef?.let { requireNotNull(mailReplyDraftService) { "Draft sending is not wired" } }
        val draftRow = draftRef?.let { requireNotNull(drafts).lockForClaim(it) }
        // A different requestId or changed content must not evade an unresolved delivery.
        if (draftRow?.sendAttemptId != null) {
            val bound = attemptRepository.findById(draftRow.sendAttemptId).orElseThrow {
                IllegalStateException("Draft bound attempt is missing")
            }
            // fast-p 03 (I-3): a draft already owned by a delayed job must never be
            // re-claimed synchronously (that would bypass the 60s deadline or double-send).
            delayedJobFor(requireNotNull(bound.id))?.let { job ->
                return ClaimedAttempt(
                    requireNotNull(bound.id),
                    bound.messageId,
                    delayedClaimResult(job.status)
                )
            }
            val blocked = when (bound.status) {
                MailSendAttemptStatus.DELIVERY_IN_PROGRESS -> ClaimResult.IN_PROGRESS
                MailSendAttemptStatus.DELIVERY_UNKNOWN -> ClaimResult.UNKNOWN
                else -> null
            }
            if (blocked != null) return ClaimedAttempt(requireNotNull(bound.id),bound.messageId,blocked)
            require(bound.status == MailSendAttemptStatus.FAILED_SAFE_TO_RETRY ||
                bound.status == MailSendAttemptStatus.FAILED || bound.status == MailSendAttemptStatus.SENT) { "Draft attempt cannot be replaced" }
            requireNotNull(drafts).releaseBinding(draftRow)
        }
        // Review identity is computed before checking its bound attempt.
        val now = LocalDateTime.now()

        attemptRepository.insertIgnore(
            orcidId = payload.orcidId,
            mailType = fingerprint.shortKey,
            accountCode = payload.accountCode,
            messageId = fingerprint.messageId,
            status = MailSendAttemptStatus.PREPARED,
            recipient = payload.normalizedRecipient,
            subject = payload.subject,
            body = fingerprint.fullHex,
            contentType = FINGERPRINT_CONTENT_TYPE,
            createdAt = now,
            updatedAt = now
        )

        val attempt = attemptRepository.findByOrcidIdAndMailTypeForUpdate(
            payload.orcidId, fingerprint.shortKey
        ) ?: throw IllegalStateException(
            "Mail send attempt not found after reservation: orcidId=${payload.orcidId} mailType=${fingerprint.shortKey}"
        )

        requireFingerprintIdentity(attempt, payload, fingerprint)
        // fast-p 03 (I-3): an attempt owned by a delayed job is only completed by the job's
        // claim token. The legacy synchronous entry converges or fails closed here and can
        // never turn a queued PREPARED attempt straight into DELIVERY_IN_PROGRESS.
        delayedJobFor(requireNotNull(attempt.id))?.let { job ->
            val result = delayedClaimResult(job.status)
            if (review != null && result == ClaimResult.DEDUP_SENT) {
                check(review.sendAttemptId == attempt.id) { "Unbound review completed attempt" }
                validateReviewRecord(payload, mailRecordRepository.findByMailSendAttemptId(attempt.id)
                    ?: error("Completed review record missing"))
            }
            return ClaimedAttempt(requireNotNull(attempt.id), attempt.messageId, result)
        }

        val claim = when (attempt.status) {
            MailSendAttemptStatus.PREPARED -> {
                val affected = attemptRepository.claimStatus(
                    requireNotNull(attempt.id),
                    MailSendAttemptStatus.PREPARED,
                    MailSendAttemptStatus.DELIVERY_IN_PROGRESS,
                    now
                )
                if (affected <= 0) {
                    throw IllegalStateException("CAS claim failed for attempt ${attempt.id}")
                }
                ClaimedAttempt(
                    attemptId = requireNotNull(attempt.id),
                    messageId = attempt.messageId,
                    result = ClaimResult.CLAIMED
                )
            }

            MailSendAttemptStatus.SENT ->
                ClaimedAttempt(
                    attemptId = requireNotNull(attempt.id),
                    messageId = attempt.messageId,
                    result = ClaimResult.DEDUP_SENT
                )

            MailSendAttemptStatus.FAILED_SAFE_TO_RETRY -> {
                val affected = attemptRepository.claimStatus(
                    requireNotNull(attempt.id),
                    MailSendAttemptStatus.FAILED_SAFE_TO_RETRY,
                    MailSendAttemptStatus.DELIVERY_IN_PROGRESS,
                    now
                )
                if (affected <= 0) {
                    throw IllegalStateException("CAS claim for safe retry failed for attempt ${attempt.id}")
                }
                ClaimedAttempt(
                    attemptId = requireNotNull(attempt.id),
                    messageId = attempt.messageId,
                    result = ClaimResult.SAFE_RETRY_CLAIMED
                )
            }

            MailSendAttemptStatus.DELIVERY_IN_PROGRESS ->
                ClaimedAttempt(
                    attemptId = requireNotNull(attempt.id),
                    messageId = attempt.messageId,
                    result = ClaimResult.IN_PROGRESS
                )

            MailSendAttemptStatus.DELIVERY_UNKNOWN ->
                ClaimedAttempt(
                    attemptId = requireNotNull(attempt.id),
                    messageId = attempt.messageId,
                    result = ClaimResult.UNKNOWN
                )

            else ->
                ClaimedAttempt(
                    attemptId = requireNotNull(attempt.id),
                    messageId = attempt.messageId,
                    result = ClaimResult.PERMANENT_FAILED
                )
        }
        if (review != null) {
            check(review.sendAttemptId == null || review.sendAttemptId == claim.attemptId) { "Review attempt cannot be replaced" }
            if (claim.result == ClaimResult.CLAIMED || claim.result == ClaimResult.SAFE_RETRY_CLAIMED)
                reviewService().bindSend(review, claim.attemptId, claim.result.name)
            else if (claim.result == ClaimResult.DEDUP_SENT) {
                check(review.sendAttemptId == claim.attemptId) { "Unbound review completed attempt" }
                validateReviewRecord(payload, mailRecordRepository.findByMailSendAttemptId(claim.attemptId)
                    ?: error("Completed review record missing"))
            }
        }
        if (draftRef != null && (claim.result == ClaimResult.CLAIMED || claim.result == ClaimResult.SAFE_RETRY_CLAIMED || claim.result == ClaimResult.DEDUP_SENT)) {
            if (claim.result == ClaimResult.DEDUP_SENT) {
                check(mailRecordRepository.findByMailSendAttemptId(claim.attemptId)?.sendStatus == "SENT") {
                    "Completed attempt has no durable SENT mail record"
                }
            }
            requireNotNull(drafts).bind(draftRef,claim.attemptId)
            if (claim.result == ClaimResult.DEDUP_SENT) drafts.closeSent(draftRef,claim.attemptId,now)
        }
        return claim
    }

    /** 既有同步入口（fast-p 02 行为逐字保留）；延迟任务走 [finalizeDelayedSuccess]。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun finalizeSuccess(payload: SendPayload, attemptId: Long, messageId: String): Long =
        finalizeSuccessTx(payload, attemptId, messageId, null)

    /**
     * fast-p 03 (T4/I-5)：延迟任务的确定性成功完成。
     *
     * [delayedRef] 是 claim 成功时才产生的 server-only 令牌：锁后必须核对
     * job/attempt/dispatchToken/来源，并在同一事务里把 job 收敛为 SENT；同令牌的
     * UNKNOWN 只允许这一次收敛，绝不重发。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    internal fun finalizeDelayedSuccess(
        payload: SendPayload,
        attemptId: Long,
        messageId: String,
        delayedRef: DelayedDispatchRef
    ): Long = finalizeSuccessTx(payload, attemptId, messageId, delayedRef)

    private fun finalizeSuccessTx(
        payload: SendPayload,
        attemptId: Long,
        messageId: String,
        delayedRef: DelayedDispatchRef?
    ): Long {
        // fast-p 02 (T3/I-5)：QA 与 RAG 证据互斥 —— 在任何写入之前 fail closed，
        // 绝不产生同时写入 mail_record_qa_rule 与 mail_record_rag_fact 的记录。
        require(payload.ragEvidence == null || payload.canonicalQaRuleIds.isEmpty()) {
            "QA and RAG evidence are mutually exclusive"
        }
        if (delayedRef != null && payload.draftSendRef == null && payload.reviewSendRef == null)
            requireNotNull(delayedManualReplyRepository).lockLegacySource(payload.contactId, payload.inboundProcessingId, payload.accountCode)
        val review = payload.reviewSendRef?.let { reviewService().lockForSendResult(it) }
        validateReviewPayload(payload)
        check(review == null || review.sendAttemptId == attemptId) { "Review attempt binding mismatch" }
        payload.draftSendRef?.let { requireNotNull(mailReplyDraftService).lockForFinalize(it) }
        val attempt = if (delayedRef != null) lockDelayedAttempt(attemptId) else attemptRepository.findById(attemptId).orElseThrow {
            IllegalStateException("Mail send attempt not found: $attemptId")
        }
        // fast-p 03 (I-2)：延迟任务的完成必须保持 attempt→job 的锁序（恢复路径同序），
        // 否则与 recoverStale 形成交叉等待。
        if (delayedRef != null) requireFingerprintIdentity(attempt, payload, computeFingerprint(payload))
        val lateSuccessJob = delayedRef?.let { verifyDelayedFinalize(it, payload, attemptId) }
        if (lateSuccessJob?.status == DelayedManualReplyStatus.SENT) {
            check(attempt.status == MailSendAttemptStatus.SENT) { "Delayed SENT attempt mismatch" }
            return requireNotNull(mailRecordRepository.findByMailSendAttemptId(attemptId)?.id) {
                "Completed send has no durable record"
            }
        }
        if (lateSuccessJob == null) {
            require(attempt.status == MailSendAttemptStatus.DELIVERY_IN_PROGRESS) {
                "Cannot finalize success: attempt $attemptId is not DELIVERY_IN_PROGRESS (current: ${attempt.status})"
            }
        } else {
            require(attempt.status == MailSendAttemptStatus.DELIVERY_IN_PROGRESS ||
                attempt.status == MailSendAttemptStatus.DELIVERY_UNKNOWN) {
                "Cannot converge delayed success: attempt $attemptId is ${attempt.status}"
            }
        }

        val now = LocalDateTime.now()
        val bodyText = payload.finalText.ifBlank { null }
        val mailBody = bodyText ?: payload.finalHtml
        // fast-p 02 (I-1/I-4)：与本次发送同一实例的规范快照 JSON；null 显式清空
        // （不沿用安全失败记录的旧附件）。
        val snapshotJson = payload.calendarAttachment?.let { CalendarAttachmentCodec.serialize(it) }
        // fast-p 05 (I-1/I-4)：本次发送的有序通用附件快照 JSON；空列表显式 null
        // （唯一 absence 形态，绝不写 []/空串，也不沿用上一次尝试的旧快照）。
        val outboundSnapshotJson = payload.outboundAttachments
            .takeIf { it.isNotEmpty() }
            ?.let { OutboundAttachmentSnapshotCodec.serialize(it) }
        val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)

        val mailRecord = if (existingRecord != null) {
            existingRecord.copy(
                senderAccountCode = payload.accountCode,
                messageId = messageId,
                inReplyTo = payload.inReplyTo,
                subject = payload.subject,
                body = mailBody,
                matchedQaRuleId = payload.primaryRuleId,
                sendStatus = "SENT",
                sentAt = now,
                errorSummary = null,
                calendarAttachmentJson = snapshotJson,
                outboundAttachmentsJson = outboundSnapshotJson
            )
        } else {
            MailRecord(
                expertContactId = payload.contactId,
                direction = "OUTBOUND",
                mailType = "MANUAL_RICH_REPLY",
                senderAccountCode = payload.accountCode,
                triggeredBy = TriggeredBy.OPERATOR,
                sourceInboundId = null,
                messageId = messageId,
                inReplyTo = payload.inReplyTo,
                subject = payload.subject,
                body = mailBody,
                matchedQaRuleId = payload.primaryRuleId,
                sendStatus = "SENT",
                receivedAt = null,
                sentAt = now,
                mailSendAttemptId = attemptId,
                createdAt = existingRecord?.createdAt ?: now,
                calendarAttachmentJson = snapshotJson,
                outboundAttachmentsJson = outboundSnapshotJson
            )
        }
        val savedRecord = mailRecordRepository.save(mailRecord)
        val mailRecordId = requireNotNull(savedRecord.id)

        if (payload.canonicalQaRuleIds.isNotEmpty()) {
            payload.canonicalQaRuleIds.forEachIndexed { ordinal, qaRuleId ->
                mailRecordQaRuleRepository.save(
                    MailRecordQaRule(
                        mailRecordId = mailRecordId,
                        qaRuleId = qaRuleId,
                        ordinal = ordinal
                    )
                )
            }
        }

        // fast-p 02 (T3/I-5)：RAG 存证与 SENT mail_record 在同一 REQUIRES_NEW 事务内提交。
        // 来源仍是发送前已验证的 payload.ragEvidence（请求顺序，可重复，不排序不去重）；
        // 空列表表示无 RAG 事实，绝不写行、也绝不伪造 QA（QA 与 RAG 互斥）。
        // 写入异常向上抛出 → 整个成功事务回滚（attempt 仍为 DELIVERY_IN_PROGRESS），
        // 由调用方按既有安全分类收敛为 UNKNOWN，绝不留下「已 SENT 但事实缺失」。
        payload.ragEvidence?.let { evidence ->
            if (evidence.factCodes.isNotEmpty()) {
                requireNotNull(mailRecordRagFactRepository) {
                    "MailRecordRagFactRepository is not wired for RAG send"
                }.saveAll(
                    evidence.factCodes.mapIndexed { ordinal, factCode ->
                        MailRecordRagFact(
                            mailRecordId = mailRecordId,
                            factCode = factCode,
                            ordinal = ordinal,
                            corpusFingerprint = evidence.corpusFingerprint
                        )
                    }
                )
            }
        }

        // fast-p 02 (I-2)：真实 SENT mail_record 取得 id 之后、attempt 标 SENT 之前创建
        // 排期 —— 与成功落库同一 REQUIRES_NEW 事务提交，二者同成功或同回滚（排期写失败
        // 绝不返回发送成功）。普通发送/无会议回信 meetingEvent 为 null，完全跳过。
        // 01 createFromSentMail 校验来源邮件（OUTBOUND/MANUAL_RICH_REPLY/SENT/带日历附件）
        // 与结构化输入；校验失败同样回滚整个成功事务（不静默吞掉排期写入错误）。
        payload.meetingEvent?.let { event ->
            meetingCalendarService.createFromSentMail(savedRecord, event)
        }

        attemptRepository.updateStatusAndError(
            id = attemptId,
            status = MailSendAttemptStatus.SENT,
            errorSummary = null,
            now = now
        )
        // fast-p 03 (T4/I-5)：job 结果与 SENT 记录/事实/排期/草稿/审核同一事务提交。
        // 同一令牌的 UNKNOWN 收敛为 SENT，绝不改写其他代次或旧令牌。
        delayedRef?.let { ref -> completeDelayedSuccess(ref, requireNotNull(lateSuccessJob)) }
        payload.draftSendRef?.let { requireNotNull(mailReplyDraftService).closeSent(it,attemptId,now) }
        if (review != null) reviewService().recordSendResult(review, attemptId, MailSendAttemptStatus.SENT, mailRecordId)

        return mailRecordId
    }

    /** 既有同步入口（fast-p 02 行为逐字保留）；延迟任务走 [finalizeDelayedFailure]。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun finalizeFailure(
        payload: SendPayload,
        attemptId: Long,
        messageId: String,
        resultStatus: String,
        errorSummary: String?
    ): Long = finalizeFailureTx(payload, attemptId, messageId, resultStatus, errorSummary, null)

    /**
     * fast-p 03 (T4/I-5)：延迟任务的失败收敛 —— 只在 job 仍为 SENDING 且令牌匹配时生效；
     * 迟到的失败既不能改 SENT，也不能覆盖新代次。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    internal fun finalizeDelayedFailure(
        payload: SendPayload,
        attemptId: Long,
        messageId: String,
        resultStatus: String,
        errorSummary: String?,
        delayedRef: DelayedDispatchRef
    ): Long = finalizeFailureTx(payload, attemptId, messageId, resultStatus, errorSummary, delayedRef)

    private fun finalizeFailureTx(
        payload: SendPayload,
        attemptId: Long,
        messageId: String,
        resultStatus: String,
        errorSummary: String?,
        delayedRef: DelayedDispatchRef?
    ): Long {
        if (delayedRef != null && payload.draftSendRef == null && payload.reviewSendRef == null)
            requireNotNull(delayedManualReplyRepository).lockLegacySource(payload.contactId, payload.inboundProcessingId, payload.accountCode)
        val review = payload.reviewSendRef?.let { reviewService().lockForSendResult(it) }
        validateReviewPayload(payload)
        check(review == null || review.sendAttemptId == attemptId) { "Review attempt binding mismatch" }
        payload.draftSendRef?.let { requireNotNull(mailReplyDraftService).lockForFinalize(it) }
        val attempt = if (delayedRef != null) lockDelayedAttempt(attemptId) else attemptRepository.findById(attemptId).orElseThrow {
            IllegalStateException("Mail send attempt not found: $attemptId")
        }
        if (delayedRef != null) requireFingerprintIdentity(attempt, payload, computeFingerprint(payload))
        val delayedJob = delayedRef?.let { ref ->
            verifyDelayedFinalize(ref, payload, attemptId, allowUnknown = true)
        }
        require(attempt.status == MailSendAttemptStatus.DELIVERY_IN_PROGRESS) {
            "Cannot finalize failure: attempt $attemptId is not DELIVERY_IN_PROGRESS (current: ${attempt.status})"
        }

        val now = LocalDateTime.now()
        val bodyText = payload.finalText.ifBlank { null }
        val mailBody = bodyText ?: payload.finalHtml
        val boundedError = errorSummary?.take(MAX_ERROR_SUMMARY_LENGTH)
        // fast-p 02 (I-1/I-4)：安全失败同样持久化本次快照 JSON；null 显式清空。
        val snapshotJson = payload.calendarAttachment?.let { CalendarAttachmentCodec.serialize(it) }
        // fast-p 05 (I-1/I-4)：安全失败同样持久化本次有序通用附件快照；空显式 null，
        // 绝不沿用上一次尝试（成功或失败）的旧快照。
        val outboundSnapshotJson = payload.outboundAttachments
            .takeIf { it.isNotEmpty() }
            ?.let { OutboundAttachmentSnapshotCodec.serialize(it) }
        val existingRecord = mailRecordRepository.findByMailSendAttemptId(attemptId)

        val mailRecord = if (existingRecord != null) {
            existingRecord.copy(
                senderAccountCode = payload.accountCode,
                messageId = messageId,
                inReplyTo = payload.inReplyTo,
                subject = payload.subject,
                body = mailBody,
                matchedQaRuleId = payload.primaryRuleId,
                sendStatus = "FAILED",
                sentAt = null,
                errorSummary = boundedError,
                calendarAttachmentJson = snapshotJson,
                outboundAttachmentsJson = outboundSnapshotJson
            )
        } else {
            MailRecord(
                expertContactId = payload.contactId,
                direction = "OUTBOUND",
                mailType = "MANUAL_RICH_REPLY",
                senderAccountCode = payload.accountCode,
                triggeredBy = TriggeredBy.OPERATOR,
                sourceInboundId = null,
                messageId = messageId,
                inReplyTo = payload.inReplyTo,
                subject = payload.subject,
                body = mailBody,
                matchedQaRuleId = payload.primaryRuleId,
                sendStatus = "FAILED",
                receivedAt = null,
                sentAt = null,
                errorSummary = boundedError,
                mailSendAttemptId = attemptId,
                createdAt = now,
                calendarAttachmentJson = snapshotJson,
                outboundAttachmentsJson = outboundSnapshotJson
            )
        }
        val savedRecord = mailRecordRepository.save(mailRecord)
        val mailRecordId = requireNotNull(savedRecord.id)

        attemptRepository.updateStatusAndError(
            id = attemptId,
            status = resultStatus,
            errorSummary = boundedError,
            now = now
        )
        // fast-p 03 (T4/I-5)：job 失败态与 FAILED mail_record（sent_at 恒 null）/attempt 同一
        // 事务提交；迟到的失败不会把已 SENT/已 UNKNOWN 的任务降级（verify 已 fail closed）。
        if (delayedRef != null) {
            completeDelayedFailure(delayedRef, requireNotNull(delayedJob), resultStatus)
        }
        if (review != null) reviewService().recordSendResult(review, attemptId, resultStatus, mailRecordId)

        return mailRecordId
    }

    fun recordSendAudit(
        inboundProcessingId: Long,
        contactId: Long,
        mailRecordId: Long,
        canonicalFactIds: List<Long>,
        carriesQa: Boolean,
        delivered: DeliveredMail,
        sendSubject: String,
        bodyPreviewText: String,
        operatorName: String?,
        inboundRecord: com.weibo.talentintroduction.mail.domain.InboundMailProcessing,
        serverSuggestedFactIds: List<Long>,
        edited: Boolean?,
        note: String,
        // 04 (I-1/I-7): 服务端权威有界诊断，仅由发送方在 verified assembly 存在时传入；
        // null 时 after payload 保持既有字段逐字不变，不写伪造诊断。不进入 SendPayload
        // 或 attempt 幂等键，不新增 action row/action type。
        trustReplyDiagnostics: TrustReplyDiagnostics? = null
    ) {
        fun auditTask() {
            try {
                val actionType = if (carriesQa) {
                    OperatorActionType.SEND_MANUAL_COMPOSED_REPLY
                } else {
                    OperatorActionType.SEND_MANUAL_RICH_REPLY
                }
                val baseAfter = if (carriesQa) {
                    mapOf(
                        "mailRecordId" to mailRecordId,
                        "canonicalFactIds" to canonicalFactIds,
                        "serverSuggestedFactIds" to serverSuggestedFactIds,
                        "qaRuleIds" to canonicalFactIds,
                        "suggestedRuleIds" to serverSuggestedFactIds,
                        "draftGenerationState" to null,
                        "edited" to (edited ?: false),
                        "sendStatus" to delivered.status,
                        "subject" to sendSubject,
                        "bodyPreviewText" to bodyPreviewText
                    )
                } else {
                    mapOf(
                        "mailRecordId" to mailRecordId,
                        "sendStatus" to delivered.status,
                        "subject" to sendSubject,
                        "bodyPreviewText" to bodyPreviewText
                    )
                }
                // 04 (I-1): 两条既有分支都可携带诊断 —— 「工作台无事实但完成发送」仍能
                // 记录 unrecognized/unsupported 诊断到 SEND_MANUAL_RICH_REPLY。
                val after = if (trustReplyDiagnostics != null) {
                    baseAfter + ("trustReplyDiagnostics" to trustReplyDiagnostics)
                } else {
                    baseAfter
                }
                operatorActionLogService.record(
                    targetType = "INBOUND_MAIL_PROCESSING",
                    targetId = inboundProcessingId,
                    actionType = actionType,
                    expertContactId = contactId,
                    inboundProcessingId = inboundProcessingId,
                    before = mapOf("inboundProcessingId" to inboundProcessingId),
                    after = after,
                    operatorName = operatorName,
                    note = note
                )
            } catch (ex: Exception) {
                log.warn(
                    "Failed to record send audit for inbound {} mailRecord {}: {}",
                    inboundProcessingId, mailRecordId, ex.message, ex
                )
            }
        }
        runAfterCommit(::auditTask)
    }

    /**
     * 会话（无来信锚点）回信审计（I-11）：动作类型固定 SEND_MANUAL_RICH_REPLY，
     * target 为联系人；before 记录锚点 mail record；after 字段沿用纯人工分支的
     * mailRecordId/sendStatus/subject/bodyPreviewText；inbound_processing_id 恒 NULL。
     * 与 recordSendAudit 共享 after-commit 调度与 best-effort 吞吐（I-8）。
     */
    fun recordConversationSendAudit(
        contactId: Long,
        anchorMailRecordId: Long,
        mailRecordId: Long,
        delivered: DeliveredMail,
        sendSubject: String,
        bodyPreviewText: String,
        operatorName: String?,
        note: String
    ) {
        fun auditTask() {
            try {
                val before = mapOf("anchorMailRecordId" to anchorMailRecordId)
                val after = mapOf(
                    "mailRecordId" to mailRecordId,
                    "sendStatus" to delivered.status,
                    "subject" to sendSubject,
                    "bodyPreviewText" to bodyPreviewText
                )
                operatorActionLogService.record(
                    targetType = "EXPERT_CONTACT",
                    targetId = contactId,
                    actionType = OperatorActionType.SEND_MANUAL_RICH_REPLY,
                    expertContactId = contactId,
                    inboundProcessingId = null,
                    before = before,
                    after = after,
                    operatorName = operatorName,
                    note = note
                )
            } catch (ex: Exception) {
                log.warn(
                    "Failed to record conversation send audit for contact {} mailRecord {}: {}",
                    contactId, mailRecordId, ex.message, ex
                )
            }
        }
        runAfterCommit(::auditTask)
    }

    private fun runAfterCommit(task: () -> Unit) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        task()
                    }
                }
            )
        } else {
            task()
        }
    }

    /**
     * fast-p 03（T4/I-5）：锁后核对延迟任务身份。
     *
     * 只接受与本次 attempt 绑定、令牌相同、来源一致且仍处于 SENDING/UNKNOWN 的任务。
     * 任何其他状态（SENT 已收敛、新代次、旧令牌）都 fail closed，迟到结果不能覆盖。
     */

    private fun verifyDelayedFinalize(
        ref: DelayedDispatchRef,
        payload: SendPayload,
        attemptId: Long,
        allowUnknown: Boolean = false
    ): DelayedManualReplyRow {
        val repository = requireNotNull(delayedManualReplyRepository) { "Delayed replies are not wired" }
        val locked = repository.lockByAttemptId(attemptId)
            ?: throw IllegalStateException("Delayed job not found for attempt $attemptId")
        check(locked.id == ref.jobId) { "Delayed job identity mismatch" }
        check(locked.dispatchToken == ref.dispatchToken) { "Delayed dispatch token mismatch" }
        check(locked.expertContactId == payload.contactId && locked.accountCode == payload.accountCode) {
            "Delayed job source mismatch"
        }
        check(when (locked.originKind) {
            DelayedManualReplyOriginKind.DRAFT -> payload.draftSendRef?.let {
                it.id == locked.originId && it.version == locked.originVersion && it.owner == locked.actorUsername
            } == true
            DelayedManualReplyOriginKind.REVIEW -> payload.reviewSendRef?.let {
                it.draft.id == locked.originId && it.draft.version == locked.originVersion && it.actor == locked.actorUsername
            } == true
            DelayedManualReplyOriginKind.LEGACY -> payload.draftSendRef == null && payload.reviewSendRef == null
        }) { "Delayed result origin mismatch" }
        check(locked.revision >= ref.revision) { "Delayed job revision regressed" }
        val allowed = when {
            allowUnknown -> setOf(DelayedManualReplyStatus.SENDING)
            else -> setOf(DelayedManualReplyStatus.SENDING, DelayedManualReplyStatus.UNKNOWN, DelayedManualReplyStatus.SENT)
        }
        check(locked.status in allowed) {
            "Delayed job $attemptId cannot be completed from ${locked.status}"
        }
        if (!allowUnknown) {
            check(locked.status != DelayedManualReplyStatus.UNKNOWN || locked.dispatchToken == ref.dispatchToken) {
                "Delayed UNKNOWN convergence requires the original dispatch token"
            }
        }
        return locked
    }

    private fun completeDelayedSuccess(ref: DelayedDispatchRef, job: DelayedManualReplyRow) {
        val repository = requireNotNull(delayedManualReplyRepository) { "Delayed replies are not wired" }
        val nowMs = repository.currentTimeMs()
        val completed = if (job.status == DelayedManualReplyStatus.UNKNOWN) {
            repository.lateSuccess(ref.jobId, ref.dispatchToken, nowMs)
        } else {
            repository.completeSending(ref.jobId, ref.dispatchToken, DelayedManualReplyStatus.SENT, null, nowMs)
        }
        check(completed) { "Delayed job ${ref.jobId} could not be completed with its claim token" }
    }

    /**
     * 失败分类 → 固定公开码（T2/I-10）。永久失败仍然禁用原内容重发；SMTP 未知绝不重发。
     */
    private fun completeDelayedFailure(ref: DelayedDispatchRef, job: DelayedManualReplyRow, resultStatus: String) {
        val repository = requireNotNull(delayedManualReplyRepository) { "Delayed replies are not wired" }
        check(job.status == DelayedManualReplyStatus.SENDING) {
            "Delayed job ${ref.jobId} already completed as ${job.status}"
        }
        val (status, code) = when (resultStatus) {
            MailSendAttemptStatus.DELIVERY_UNKNOWN ->
                DelayedManualReplyStatus.UNKNOWN to DelayedReplyPublicError.DELIVERY_UNKNOWN
            MailSendAttemptStatus.FAILED_SAFE_TO_RETRY ->
                DelayedManualReplyStatus.FAILED to DelayedReplyPublicError.TEMPORARY_DELIVERY_FAILURE
            else ->
                DelayedManualReplyStatus.FAILED to DelayedReplyPublicError.PERMANENT_DELIVERY_FAILURE
        }
        val completed = repository.completeSending(ref.jobId, ref.dispatchToken, status, code.code, repository.currentTimeMs())
        check(completed) { "Delayed job ${ref.jobId} could not be completed with its claim token" }
    }

    private fun requireFingerprintIdentity(attempt: MailSendAttempt, payload: SendPayload, fingerprint: Fingerprint) {
        if (attempt.contentType != FINGERPRINT_CONTENT_TYPE) {
            throw IllegalArgumentException(
                "Mail send attempt fingerprint collision: unexpected contentType=${attempt.contentType}"
            )
        }
        if (attempt.body != fingerprint.fullHex) {
            throw IllegalArgumentException(
                "Mail send attempt fingerprint collision: full hash mismatch"
            )
        }
        if (attempt.recipient != payload.normalizedRecipient) {
            throw IllegalArgumentException(
                "Mail send attempt fingerprint collision: recipient mismatch"
            )
        }
    }

    /**
     * fast-p 03 (I-3/I-10)：既有同步入口遇到已排队任务时的唯一收敛映射。
     * SENT 收敛为幂等完成；WAITING/SENDING 视为进行中；UNKNOWN 绝不可重发；
     * FAILED/CANCELLED 必须走显式重新发送，不能由旧请求直接抢发。
     */
    private fun delayedClaimResult(status: DelayedManualReplyStatus): ClaimResult = when (status) {
        DelayedManualReplyStatus.SENT -> ClaimResult.DEDUP_SENT
        DelayedManualReplyStatus.WAITING, DelayedManualReplyStatus.SENDING -> ClaimResult.IN_PROGRESS
        DelayedManualReplyStatus.UNKNOWN -> ClaimResult.UNKNOWN
        DelayedManualReplyStatus.FAILED, DelayedManualReplyStatus.CANCELLED -> ClaimResult.PERMANENT_FAILED
    }

    private fun reviewService() = requireNotNull(mailReviewDraftService) { "Review sending is not wired" }

    private fun validateReviewPayload(payload: SendPayload) {
        val ref = payload.reviewSendRef ?: return
        val row = ref.draft
        if (payload.draftSendRef != null || payload.contactId != row.contactId || payload.accountCode != row.accountCode ||
            payload.subject != row.subject || payload.finalHtml != row.html || payload.finalText != row.text ||
            payload.canonicalQaRuleIds.isNotEmpty() || payload.primaryRuleId != null || payload.calendarAttachment != null ||
            payload.meetingEvent != null || payload.outboundAttachments.isNotEmpty() ||
            (row.targetKind == "INBOUND" && (payload.inboundProcessingId != row.targetId || payload.sourceAnchor != null)) ||
            (row.targetKind == "OUTBOUND" && (payload.inboundProcessingId != null ||
                payload.sourceAnchor != "$SOURCE_ANCHOR_PREFIX${row.targetId}" || payload.idempotencyRequestId != row.sendRequestId)))
            throw MailReviewDraftException(409, "REVIEW_CONTENT_CHANGED", "Send payload differs from approved content")
    }

    private fun validateReviewRecord(payload: SendPayload, record: MailRecord) {
        check(record.sendStatus == "SENT" && record.direction == "OUTBOUND" &&
            record.expertContactId == payload.contactId && record.senderAccountCode == payload.accountCode &&
            record.subject == payload.subject && record.body == payload.finalText &&
            record.calendarAttachmentJson == null && record.outboundAttachmentsJson == null) { "Review SENT record mismatch" }
    }

    private fun appendLengthPrefix(data: ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val len = bytes.size
        data.write(len shr 24)
        data.write((len shr 16) and 0xff)
        data.write((len shr 8) and 0xff)
        data.write(len and 0xff)
        data.write(bytes)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
