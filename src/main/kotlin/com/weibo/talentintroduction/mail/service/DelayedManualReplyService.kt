package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
import com.weibo.talentintroduction.campaign.domain.MeetingCalendarInput
import com.weibo.talentintroduction.config.ManualReplyDelayProperties
import com.weibo.talentintroduction.mail.repository.DelayedManualReplyRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

/**
 * 固定公开失败原因（总 I-10 / 04T4 逐字协议）。
 *
 * 只有这十个码允许进入 `public_error_code`；SMTP 原始错误仍写 attempt.error_detail，
 * 绝不进入面向操作端的响应。文案逐字来自 04T4 表。
 */
enum class DelayedReplyPublicError(val code: String, val message: String, val retryableAfterRevalidation: Boolean) {
    TEMPORARY_DELIVERY_FAILURE("TEMPORARY_DELIVERY_FAILURE", "发送暂时失败，可安全重试", true),
    PERMANENT_DELIVERY_FAILURE("PERMANENT_DELIVERY_FAILURE", "邮件被拒收，请修改内容或联系管理员", false),
    DELIVERY_UNKNOWN("DELIVERY_UNKNOWN", "发送结果待确认，请勿重复发送", false),
    REVIEW_CONTEXT_CHANGED("REVIEW_CONTEXT_CHANGED", "来信内容已更新，请先重新确认审核上下文", true),
    ACCESS_CHANGED("ACCESS_CHANGED", "权限已变更，邮件未发送", true),
    SOURCE_CHANGED("SOURCE_CHANGED", "回复目标已变更，邮件未发送", false),
    ATTACHMENT_UNAVAILABLE("ATTACHMENT_UNAVAILABLE", "附件不可用，邮件未发送", true),
    ACCOUNT_UNAVAILABLE("ACCOUNT_UNAVAILABLE", "发件账号不可用，邮件未发送", true),
    RECIPIENT_SUPPRESSED("RECIPIENT_SUPPRESSED", "当前收件人不可发送", false),
    SNAPSHOT_INVALID("SNAPSHOT_INVALID", "发送内容无法恢复，请联系管理员", false);

    companion object {
        /** 白名单外的历史/脏值一律按不可恢复处理，绝不回显原文。 */
        fun ofOrInvalid(code: String?): DelayedReplyPublicError? = when {
            code == null -> null
            else -> values().firstOrNull { it.code == code } ?: SNAPSHOT_INVALID
        }
    }
}

/**
 * 延迟任务状态机错误。继承 IllegalStateException 以复用既有全局异常映射；
 * [code] 只用于服务内部/操作端固定协议，消息不包含 SMTP 原文。
 */
class DelayedManualReplyException(val status: Int, val code: String, message: String) :
    IllegalStateException(message)

/** 排队入口的显式结果：新建/幂等任务，或已完成命中（不新增任何任务行）。 */
sealed interface DelayedEnqueueOutcome {
    data class Queued(val job: DelayedManualReplyView) : DelayedEnqueueOutcome
    data class AlreadySent(val attemptId: Long, val mailRecordId: Long, val messageId: String) : DelayedEnqueueOutcome
}

/** claim 成功后的载荷：job 行快照 + 本轮唯一令牌。 */
internal data class DelayedClaimRow(val job: DelayedManualReplyRow, val dispatchRef: DelayedDispatchRef)

/**
 * fast-p 03：人工回复延迟发送的事务状态机。
 *
 * 只做「排队/撤回/重排/重试/领取/投递前失败/超时恢复」与草稿、审核、attempt、job 之间的
 * 原子绑定；SMTP、HTTP、调度与读模型属于 04～07 阶段。所有跨表迁移遵守统一锁序：
 * 审核授权行（适用时）→ 源草稿/审核稿 → attempt → job，且全部在一个短事务内提交。
 *
 * `enabled` 开关不在此处判断：本阶段入口未接通，04 才决定新请求是否入队。
 */
@Service
class DelayedManualReplyService(
    private val repository: DelayedManualReplyRepository,
    private val attempts: ManualReplySendAttemptService,
    private val drafts: MailReplyDraftService,
    private val reviews: MailReviewDraftService,
    private val permissionGuard: ExpertMailPermissionGuard,
    private val mailRecords: MailRecordRepository,
    private val attachments: OutboundAttachmentService,
    private val properties: ManualReplyDelayProperties = ManualReplyDelayProperties(),
    transactionManager: PlatformTransactionManager
) {
    companion object {
        private val log = LoggerFactory.getLogger(DelayedManualReplyService::class.java)
        private const val MAX_ACTOR_LENGTH = 64

        /** 未决（不可换指纹另排）的任务态；FAILED/CANCELLED 允许显式重试或新发送。 */
        private val UNRESOLVED = setOf(
            DelayedManualReplyStatus.WAITING,
            DelayedManualReplyStatus.SENDING,
            DelayedManualReplyStatus.UNKNOWN
        )
    }

    private val transactions = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    }

    private val eligibilityTransactions = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRED
        isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    }

    // ---------------------------------------------------------------- 排队

    /**
     * 首次排队 / 幂等重复 / CANCELLED 重排（T1/I-1/I-3/I-6/I-7）。
     *
     * 源内容在同一事务内重新加锁核对；`dueAt` 一律取数据库时钟 + 60000，重复的
     * WAITING/SENDING 请求只回读原投影，绝不刷新期限。`expectedCancelledRevision`
     * 只有 CANCELLED 重排需要，缺失或过期返回 409 `DELAYED_REPLY_REVISION_CONFLICT`。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    fun enqueue(
        snapshot: DelayedManualReplySnapshot,
        actor: String,
        expectedCancelledRevision: Long? = null
    ): DelayedEnqueueOutcome {
        requireActor(actor)
        DelayedManualReplySnapshotCodec.validate(snapshot)
        val source = lockEnqueueSource(snapshot, actor)
        val payload = payloadOf(snapshot, source, actor)
        val reservation = attempts.reserveDelayed(payload)

        repository.lockByAttemptId(reservation.attemptId)?.let { existing ->
            requireSameIdentity(existing, snapshot, actor)
            requireMatrix(existing, reservation.state.name)
            when (existing.status) {
                DelayedManualReplyStatus.WAITING,
                DelayedManualReplyStatus.SENDING,
                DelayedManualReplyStatus.SENT -> return DelayedEnqueueOutcome.Queued(view(existing))
                DelayedManualReplyStatus.UNKNOWN ->
                    throw conflict(DelayedReplyPublicError.DELIVERY_UNKNOWN.code, DelayedReplyPublicError.DELIVERY_UNKNOWN.message)
                DelayedManualReplyStatus.FAILED ->
                    throw conflict("DELAYED_REPLY_REQUIRES_RETRY", "该回复已失败，请使用重新发送")
                DelayedManualReplyStatus.CANCELLED ->
                    return DelayedEnqueueOutcome.Queued(requeueCancelled(existing, snapshot, source, reservation, actor, expectedCancelledRevision))
            }
        }

        when (reservation.state) {
            ManualReplySendAttemptService.DelayedReservationState.SENT -> {
                val record = mailRecords.findByMailSendAttemptId(reservation.attemptId)
                return DelayedEnqueueOutcome.AlreadySent(
                    attemptId = reservation.attemptId,
                    mailRecordId = requireNotNull(record?.id) { "Completed send has no durable SENT record" },
                    messageId = reservation.messageId
                )
            }
            ManualReplySendAttemptService.DelayedReservationState.DELIVERY_IN_PROGRESS ->
                throw conflict("DELAYED_REPLY_IN_PROGRESS", "回复正在发送，不能重复提交")
            ManualReplySendAttemptService.DelayedReservationState.DELIVERY_UNKNOWN ->
                throw conflict(DelayedReplyPublicError.DELIVERY_UNKNOWN.code, DelayedReplyPublicError.DELIVERY_UNKNOWN.message)
            ManualReplySendAttemptService.DelayedReservationState.FAILED,
            ManualReplySendAttemptService.DelayedReservationState.FAILED_SAFE_TO_RETRY ->
                throw conflict("DELAYED_REPLY_REQUIRES_RETRY", "该回复已失败，请使用重新发送")
            ManualReplySendAttemptService.DelayedReservationState.PREPARED -> Unit
        }


        // 同一源仍有未决任务：禁止换指纹另排（LEGACY 也按真实目标与 actor 检查）。
        if (unresolvedJobFor(snapshot, reservation.attemptId, actor) != null) {
            throw conflict("DELAYED_REPLY_SOURCE_CONFLICT", "该回复已有待发送任务，请先撤回或等待完成")
        }
        if (source is SlottedSource.Draft &&
            (snapshot.restore as DelayedDraftRestore).draftVersion != source.ref.version) {
            throw conflict(DelayedReplyPublicError.SOURCE_CHANGED.code, DelayedReplyPublicError.SOURCE_CHANGED.message)
        }

        val now = repository.currentTimeMs()
        val jobId = repository.insert(
            DelayedManualReplyInsert(
                sendAttemptId = reservation.attemptId,
                expertContactId = snapshot.payload.contactId,
                accountCode = snapshot.payload.accountCode,
                actorUsername = actor,
                originKind = snapshot.restore.originKind(),
                originId = snapshot.restore.originId(),
                originVersion = snapshot.restore.originVersion(),
                dueAtMs = now + DELAYED_MANUAL_REPLY_DELAY_MS,
                snapshot = snapshot,
                createdAtMs = now,
                updatedAtMs = now
            )
        )
        bindDraft(source, reservation.attemptId)
        recordReviewQueueEvent(source, "SEND_QUEUED", jobId, 1L)
        return DelayedEnqueueOutcome.Queued(view(requireNotNull(repository.findById(jobId))))
    }

    /** CANCELLED→WAITING：当前 revision、同 actor/origin、冻结正文逐字不变（T1/I-1/I-6）。 */
    private fun requeueCancelled(
        job: DelayedManualReplyRow,
        snapshot: DelayedManualReplySnapshot,
        source: SlottedSource,
        reservation: ManualReplySendAttemptService.DelayedReservation,
        actor: String,
        expectedRevision: Long?
    ): DelayedManualReplyView {
        if (expectedRevision == null || expectedRevision != job.revision) {
            throw conflict("DELAYED_REPLY_REVISION_CONFLICT", "任务代次已变化，请重新读取最新状态")
        }
        requireActorCanTransition(job, actor)
        if (unresolvedJobFor(snapshot, reservation.attemptId, actor) != null) {
            throw conflict("DELAYED_REPLY_SOURCE_CONFLICT", "该回复已有待发送任务，请先撤回或等待完成")
        }
        val draftSource = source as? SlottedSource.Draft
        val now = repository.currentTimeMs()
        val updated = draftSource?.let {
            snapshot.copy(
                restore = (snapshot.restore as DelayedDraftRestore).copy(
                    draftVersion = it.ref.version,
                    sendAttemptId = reservation.attemptId,
                    sendVersion = it.ref.version
                )
            )
        } ?: snapshot
        val ok = repository.requeue(
            id = job.id,
            expectedRevision = expectedRevision,
            nowMs = now,
            dueAtMs = now + DELAYED_MANUAL_REPLY_DELAY_MS,
            originVersion = draftSource?.ref?.version ?: snapshot.restore.originVersion(),
            snapshotJson = DelayedManualReplySnapshotCodec.encode(updated)
        )
        if (!ok) throw conflict("DELAYED_REPLY_REVISION_CONFLICT", "任务代次已变化，请重新读取最新状态")
        bindDraft(source, reservation.attemptId)
        recordReviewQueueEvent(source, "SEND_REQUEUED", job.id, expectedRevision + 1)
        return view(requireNotNull(repository.findById(job.id)))
    }

    // ---------------------------------------------------------------- 撤回

    /**
     * 撤回（T2/I-2/I-6/I-7）：`now < dueAt` 且 revision 匹配才允许；只释放完全相同的
     * 普通草稿绑定，正文/context/附件逐字保留；审核稿保持 APPROVED 与版本不变。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    fun cancel(jobId: Long, actor: String, expectedRevision: Long): DelayedManualReplyView {
        requireActor(actor)
        val observed = repository.findById(jobId) ?: throw notFound()
        requireActorCanTransition(observed, actor)
        if (observed.status == DelayedManualReplyStatus.CANCELLED) {
            // 重复相同 cancel 读回 CANCELLED 作为幂等成功；旧代次不能取消后续 WAITING。
            if (expectedRevision == observed.revision || expectedRevision == observed.revision - 1) return view(observed)
            throw conflict("DELAYED_REPLY_REVISION_CONFLICT", "任务代次已变化，请重新读取最新状态")
        }
        if (observed.status != DelayedManualReplyStatus.WAITING) {
            throw conflict("DELAYED_REPLY_STATE_CONFLICT", "当前任务状态不可撤回")
        }
        if (observed.revision != expectedRevision) {
            throw conflict("DELAYED_REPLY_REVISION_CONFLICT", "任务代次已变化，请重新读取最新状态")
        }
        val snapshot = decodeSnapshot(observed)
        lockStateSource(observed, snapshot)
        val now = repository.currentTimeMs()
        if (now >= observed.dueAtMs) {
            throw DelayedManualReplyException(409, "DELAYED_REPLY_TOO_LATE", "已超过撤回期限")
        }
        if (!repository.cancelWaiting(jobId, expectedRevision, now)) {
            if (repository.currentTimeMs() >= observed.dueAtMs) {
                throw DelayedManualReplyException(409, "DELAYED_REPLY_TOO_LATE", "已超过撤回期限")
            }
            throw conflict("DELAYED_REPLY_REVISION_CONFLICT", "任务代次已变化，请重新读取最新状态")
        }
        when (val restore = snapshot.restore) {
            is DelayedDraftRestore -> drafts.lockDelayedTransition(restore.owner, restore.draftId)?.let {
                if (it.sendVersion == restore.draftVersion) drafts.releaseDelayedBinding(it, observed.sendAttemptId)
            }
            is DelayedReviewRestore -> reviews.lockDelayedResult(restore.reviewId)?.let {
                reviews.recordQueueEvent(it, "SEND_CANCELLED", jobId, expectedRevision + 1)
            }
            is DelayedLegacyRestore -> Unit
        }
        return view(requireNotNull(repository.findById(jobId)))
    }

    // ---------------------------------------------------------------- 重试

    /**
     * FAILED→WAITING（T2/I-1/I-7）：源内容、权限与审核上下文全部重新校验；只有
     * FAILED_SAFE_TO_RETRY 的 attempt 可以进入，新的 dueAt 重新获得完整 60 秒。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    fun retry(jobId: Long, actor: String, expectedRevision: Long): DelayedManualReplyView {
        requireActor(actor)
        val observed = repository.findById(jobId) ?: throw notFound()
        requireActorCanTransition(observed, actor)
        if (observed.status == DelayedManualReplyStatus.WAITING) {
            throw conflict("DELAYED_REPLY_ALREADY_WAITING", "回复已重新排入队列，请重新读取")
        }
        if (observed.status != DelayedManualReplyStatus.FAILED) {
            throw conflict("DELAYED_REPLY_STATE_CONFLICT", "当前任务状态不可重新发送")
        }
        if (observed.revision != expectedRevision) {
            throw conflict("DELAYED_REPLY_REVISION_CONFLICT", "任务代次已变化，请重新读取最新状态")
        }
        val error = DelayedReplyPublicError.ofOrInvalid(observed.publicErrorCode)
        if (error?.retryableAfterRevalidation != true)
            throw conflict(error?.code ?: DelayedReplyPublicError.SNAPSHOT_INVALID.code,
                error?.message ?: DelayedReplyPublicError.SNAPSHOT_INVALID.message)
        val snapshot = decodeSnapshot(observed)
        val source = lockTransitionSource(observed, snapshot)
        requireRuntimeAvailable(snapshot)
        val attempt = attempts.lockDelayedAttempt(observed.sendAttemptId)
        if (repository.lockById(jobId) != observed)
            throw conflict("DELAYED_REPLY_REVISION_CONFLICT", "任务代次已变化，请重新读取最新状态")
        requireMatrix(observed, attempt.status)
        if (attempt.status != MailSendAttemptStatus.FAILED_SAFE_TO_RETRY) {
            throw conflict(DelayedReplyPublicError.PERMANENT_DELIVERY_FAILURE.code,
                DelayedReplyPublicError.PERMANENT_DELIVERY_FAILURE.message)
        }
        if (unresolvedJobFor(snapshot, observed.sendAttemptId, observed.actorUsername) != null) {
            throw conflict("DELAYED_REPLY_SOURCE_CONFLICT", "该回复已有待发送任务，请先撤回或等待完成")
        }
        val now = repository.currentTimeMs()
        val draftSource = source as? SlottedSource.Draft
        val updated = when (source) {
            is SlottedSource.Draft -> {
                if (!source.contentMatches) {
                    throw conflict(DelayedReplyPublicError.SOURCE_CHANGED.code, "回复目标已变更，邮件未发送")
                }
                snapshot.copy(
                    restore = (snapshot.restore as DelayedDraftRestore).copy(
                        draftVersion = source.ref.version,
                        sendAttemptId = observed.sendAttemptId,
                        sendVersion = source.ref.version
                    )
                )
            }
            is SlottedSource.Review -> {
                val restore = snapshot.restore as DelayedReviewRestore
                snapshot.copy(restore = restore.copy(confirmationEventId = source.confirmationEventId))
            }
            SlottedSource.Legacy -> snapshot
        }
        val ok = repository.retryFailed(
            id = jobId,
            expectedRevision = expectedRevision,
            nowMs = now,
            dueAtMs = now + DELAYED_MANUAL_REPLY_DELAY_MS,
            originVersion = draftSource?.ref?.version ?: snapshot.restore.originVersion(),
            snapshotJson = DelayedManualReplySnapshotCodec.encode(updated)
        )
        if (!ok) throw conflict("DELAYED_REPLY_STATE_CONFLICT", "当前任务状态不可重新发送")
        bindDraft(source, observed.sendAttemptId)
        recordReviewQueueEvent(source, "SEND_REQUEUED", jobId, expectedRevision + 1)
        return view(requireNotNull(repository.findById(jobId)))
    }

    // ---------------------------------------------------------------- 领取

    /**
     * WAITING→SENDING 领取（T2/I-2/I-3）。
     *
     * 源锁 → attempt CAS → job CAS 全部在同一事务内，任一步失败整体回滚；返回的
     * [DelayedDispatchRef] 是唯一令牌，`dispatchRef` 只在 claim 成功时才会存在。
     */
    // 刻意不给 jobId 之外任何参数：Kotlin 默认参数在 CGLIB 代理下会以代理实例求值
    // 目标字段，事务代理调用时会得到 null。到期时间一律在事务内读数据库时钟。
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    internal fun claim(jobId: Long): DelayedClaimRow? {
        val nowMs = repository.currentTimeMs()
        val observed = repository.findById(jobId) ?: return null
        if (observed.status != DelayedManualReplyStatus.WAITING) return null
        if (nowMs < observed.dueAtMs) return null
        val snapshot = decodeSnapshot(observed)
        val source = lockEnqueueSource(snapshot, observed.actorUsername)
        when (source) {
            is SlottedSource.Draft -> check(source.row.version == observed.originVersion &&
                source.row.sendVersion == observed.originVersion && source.row.sendAttemptId == observed.sendAttemptId) {
                "Delayed draft binding changed"
            }
            is SlottedSource.Review -> check(source.row.sendAttemptId == observed.sendAttemptId) {
                "Delayed review binding changed"
            }
            SlottedSource.Legacy -> Unit
        }
        val attempt = attempts.lockDelayedAttempt(observed.sendAttemptId)
        val locked = repository.lockById(jobId) ?: return null
        if (locked != observed) return null
        requireMatrix(locked, attempt.status)
        check(attempts.claimDelayed(observed.sendAttemptId)) {
            "Delayed attempt ${observed.sendAttemptId} is not claimable"
        }
        val token = UUID.randomUUID().toString()
        if (!repository.claimDue(jobId, observed.revision, token, nowMs)) {
            throw IllegalStateException("Delayed job $jobId lost the claim CAS")
        }
        val claimed = requireNotNull(repository.findById(jobId))
        return DelayedClaimRow(claimed, DelayedDispatchRef(jobId, claimed.revision, token))
    }

    // ---------------------------------------------------------------- 投递前失败与恢复

    /**
     * WAITING→FAILED 的投递前失败（T2/I-10）：明确未调用 SMTP，attempt 置可安全重试，
     * 只写固定公开码。仅 worker（04）在 claim 前后调用，故不对外开放身份校验。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    internal fun preflightFailure(
        jobId: Long,
        expectedRevision: Long,
        error: DelayedReplyPublicError
    ): DelayedManualReplyView {
        val observed = repository.findById(jobId) ?: throw notFound()
        if (observed.status != DelayedManualReplyStatus.WAITING || observed.revision != expectedRevision) {
            throw conflict("DELAYED_REPLY_STATE_CONFLICT", "当前任务状态不可标记失败")
        }
        val snapshot = runCatching { decodeSnapshot(observed) }.getOrNull()
        lockStateSource(observed, snapshot)
        attempts.failDelayedBeforeDelivery(observed.sendAttemptId, error.code)
        if (!repository.failBeforeDelivery(jobId, expectedRevision, error.code, repository.currentTimeMs())) {
            throw conflict("DELAYED_REPLY_STATE_CONFLICT", "当前任务状态不可标记失败")
        }
        return view(requireNotNull(repository.findById(jobId)))
    }

    /**
     * 超时 SENDING→UNKNOWN 恢复（T4/I-5/I-11）：逐条短事务，绝不调用 SMTP。
     * 恢复保留原令牌，只为同令牌的确定成功留出收敛路径；返回被恢复的 job id。
     */
    fun recoverStale(nowMs: Long? = null, claimedBeforeMs: Long? = null): List<Long> {
        val now = nowMs ?: repository.currentTimeMs()
        val claimedBefore = claimedBeforeMs ?: (now - properties.unknownAfterMs)
        return repository.scanStaleSendingIds(claimedBefore).mapNotNull { jobId ->
            try {
                if (transactions.execute { recoverOne(jobId, claimedBefore, now) } == true) jobId else null
            } catch (ex: Exception) {
                log.warn("Delayed job {} recovery failed: {}", jobId, ex.message)
                null
            }
        }
    }

    internal fun recoverOne(jobId: Long, claimedBeforeMs: Long, nowMs: Long): Boolean {
        val observed = repository.findById(jobId) ?: return false
        if (observed.status != DelayedManualReplyStatus.SENDING) return false
        if (observed.claimedAtMs == null || observed.claimedAtMs > claimedBeforeMs) return false
        val token = observed.dispatchToken ?: return false
        // 锁序：源 → attempt → job；源缺失时仍恢复（任务不因源删除而永久卡住）。
        lockStateSource(observed, runCatching { decodeSnapshot(observed) }.getOrNull())
        attempts.recoverDelayedUnknown(observed.sendAttemptId)
        val recovered = repository.recoverToUnknown(
            id = jobId,
            dispatchToken = token,
            publicErrorCode = DelayedReplyPublicError.DELIVERY_UNKNOWN.code,
            claimedBeforeMs = claimedBeforeMs,
            nowMs = nowMs
        )
        // 恢复只改任务/attempt 状态：不写 send/queue 事件，也不产生新代次的事件记录。
        return recovered
    }

    // ---------------------------------------------------------------- 投影

    /** 公开投影（T4 字段固定）；未知错误码 fail closed 为 SNAPSHOT_INVALID。 */
    fun view(row: DelayedManualReplyRow, nowMs: Long? = null): DelayedManualReplyView {
        val current = nowMs ?: repository.currentTimeMs()
        val error = DelayedReplyPublicError.ofOrInvalid(row.publicErrorCode)
        return DelayedManualReplyView(
            id = row.id,
            revision = row.revision,
            status = row.status,
            createdAtMs = row.createdAtMs,
            dueAtMs = row.dueAtMs,
            serverNowMs = current,
            originKind = row.originKind,
            originId = row.originId,
            originVersion = row.originVersion,
            canCancel = row.status == DelayedManualReplyStatus.WAITING &&
                row.publicErrorCode == null && current < row.dueAtMs,
            canRetry = row.status == DelayedManualReplyStatus.FAILED &&
                error?.retryableAfterRevalidation == true && retryEligible(row),
            publicErrorCode = error?.code,
            publicErrorMessage = error?.message,
            sentMailRecordId = if (row.status == DelayedManualReplyStatus.SENT) {
                mailRecords.findByMailSendAttemptId(row.sendAttemptId)?.id
            } else null
        )
    }

    fun findVisible(jobId: Long): DelayedManualReplyView? = repository.findById(jobId)?.let { view(it) }

    private fun retryEligible(row: DelayedManualReplyRow): Boolean =
        !org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive() &&
        eligibilityTransactions.execute {
            runCatching {
                val snapshot = decodeSnapshot(row)
                requireActorCanTransition(row, row.actorUsername)
                lockTransitionSource(row, snapshot)
                requireRuntimeAvailable(snapshot)
                val attempt = attempts.lockDelayedAttempt(row.sendAttemptId)
                repository.lockById(row.id) == row &&
                    attempt.status == MailSendAttemptStatus.FAILED_SAFE_TO_RETRY
            }.getOrDefault(false)
        } == true

    private fun requireRuntimeAvailable(snapshot: DelayedManualReplySnapshot) {
        if (!repository.enabledAccountExists(snapshot.payload.accountCode))
            throw conflict(DelayedReplyPublicError.ACCOUNT_UNAVAILABLE.code, DelayedReplyPublicError.ACCOUNT_UNAVAILABLE.message)
        for (expected in snapshot.payload.outboundAttachments) {
            val actual = runCatching {
                attachments.resolveForMessageDownload(snapshot.payload.contactId, expected.id).snapshot
            }.getOrNull()
            if (actual != expected) throw conflict(DelayedReplyPublicError.ATTACHMENT_UNAVAILABLE.code,
                DelayedReplyPublicError.ATTACHMENT_UNAVAILABLE.message)
        }
    }

    // ---------------------------------------------------------------- 内部：源锁

    /** 排队入口的源锁结果；`ref`/`confirmationEventId` 只服务 payload 重建与绑定。 */
    private sealed interface SlottedSource {
        val contentMatches: Boolean

        data class Draft(
            val row: MailReplyDraftRow,
            val ref: ValidatedDraftSendRef,
            override val contentMatches: Boolean = true
        ) : SlottedSource

        data class Review(
            val row: MailReviewDraftRow,
            val confirmationEventId: Long?,
            override val contentMatches: Boolean = true
        ) : SlottedSource

        data object Legacy : SlottedSource {
            override val contentMatches: Boolean = true
        }
    }

    private fun lockEnqueueSource(snapshot: DelayedManualReplySnapshot, actor: String): SlottedSource {
        val payload = snapshot.payload
        return when (val restore = snapshot.restore) {
            is DelayedDraftRestore -> {
                val source = drafts.lockDelayedSource(actor, restore)
                if (!source.contentMatches) {
                    throw conflict(DelayedReplyPublicError.SOURCE_CHANGED.code, "回复目标已变更，邮件未发送")
                }
                SlottedSource.Draft(
                    row = source.row,
                    ref = requireNotNull(source.ref) { "Validated draft reference is required" }
                )
            }
            is DelayedReviewRestore ->
                SlottedSource.Review(
                    row = reviews.lockDelayedReview(actor, restore, restore.confirmationEventId),
                    confirmationEventId = restore.confirmationEventId
                )
            is DelayedLegacyRestore -> {
                if (payload.contactId <= 0) throw conflict(DelayedReplyPublicError.SOURCE_CHANGED.code, "回复目标已变更")
                permissionGuard.requireFull(actor, payload.contactId)
                if (!repository.lockLegacySource(payload.contactId, payload.inboundProcessingId, payload.accountCode)) {
                    throw conflict(DelayedReplyPublicError.SOURCE_CHANGED.code, "回复目标已变更")
                }
                SlottedSource.Legacy
            }
        }
    }

    /** Retry admission validates the current source and latest explicit review confirmation. */
    private fun lockTransitionSource(job: DelayedManualReplyRow, snapshot: DelayedManualReplySnapshot): SlottedSource =
        when (val restore = snapshot.restore) {
            is DelayedDraftRestore -> {
                val source = drafts.lockDelayedSource(restore.owner, restore)
                SlottedSource.Draft(source.row, source.ref ?: failedSource(), source.contentMatches)
            }
            is DelayedReviewRestore -> {
                val resolved = reviews.lockDelayedReviewForRetry(job.actorUsername, restore)
                SlottedSource.Review(resolved.first, resolved.second)
            }
            is DelayedLegacyRestore -> lockEnqueueSource(snapshot, job.actorUsername)
        }

    /** Result transitions lock sources without re-admitting their content or permissions. */
    private fun lockStateSource(job: DelayedManualReplyRow, snapshot: DelayedManualReplySnapshot?) {
        when (job.originKind) {
            DelayedManualReplyOriginKind.DRAFT -> (snapshot?.restore as? DelayedDraftRestore)?.let {
                drafts.lockDelayedTransition(it.owner, it.draftId)
            }
            DelayedManualReplyOriginKind.REVIEW -> job.originId?.let { reviews.lockDelayedResult(it) }
            DelayedManualReplyOriginKind.LEGACY ->
                repository.lockLegacySource(job.expertContactId, snapshot?.payload?.inboundProcessingId, job.accountCode)
        }
        val attempt = attempts.lockDelayedAttempt(job.sendAttemptId)
        val locked = repository.lockById(job.id) ?: throw notFound()
        if (locked != job) throw conflict("DELAYED_REPLY_REVISION_CONFLICT", "任务代次已变化，请重新读取最新状态")
        requireMatrix(locked, attempt.status)
    }

    private fun failedSource(): ValidatedDraftSendRef =
        throw conflict(DelayedReplyPublicError.SOURCE_CHANGED.code, "回复目标已变更，邮件未发送")

    // ---------------------------------------------------------------- 内部：绑定与事件

    /**
     * fast-p 03 (I-3/I-6)：把源绑定到同一个 attempt。普通稿按当前版本写 send_version；
     * 审核稿只写 attempt（APPROVED 内容与版本不变），供发送锁与历史追踪使用。
     */
    private fun bindDraft(source: SlottedSource, attemptId: Long) {
        when (source) {
            is SlottedSource.Draft -> drafts.bindDelayed(source.ref, attemptId)
            is SlottedSource.Review -> reviews.bindDelayedAttempt(source.row, attemptId)
            SlottedSource.Legacy -> Unit
        }
    }

    private fun recordReviewQueueEvent(source: SlottedSource, type: String, jobId: Long, revision: Long) {
        (source as? SlottedSource.Review)?.let { reviews.recordQueueEvent(it.row, type, jobId, revision) }
    }

    // ---------------------------------------------------------------- 内部：租约与身份

    /** 未决任务：同源（DRAFT/REVIEW 按 origin，LEGACY 按真实来信 id）只能存在一个。 */
    private fun unresolvedJobFor(snapshot: DelayedManualReplySnapshot, attemptId: Long, actor: String): DelayedManualReplyRow? {
        val kind = snapshot.restore.originKind()
        val originId = snapshot.restore.originId()
        val candidates = if (kind == DelayedManualReplyOriginKind.LEGACY) {
            repository.findUnresolvedByContact(snapshot.payload.contactId).filter {
                it.sendAttemptId != attemptId && it.actorUsername == actor &&
                    decodeLegacyInboundId(it) == snapshot.payload.inboundProcessingId
            }
        } else {
            repository.findByOrigin(kind, requireNotNull(originId)).filter { it.sendAttemptId != attemptId }
        }
        return candidates.firstOrNull { it.status in UNRESOLVED }
    }

    private fun decodeLegacyInboundId(row: DelayedManualReplyRow): Long? =
        (DelayedManualReplySnapshotCodec.decodeOrNull(row.snapshotJson)?.restore as? DelayedLegacyRestore)
            ?.inboundProcessingId

    private fun requireSameIdentity(job: DelayedManualReplyRow, snapshot: DelayedManualReplySnapshot, actor: String) {
        val original = decodeSnapshot(job)
        val sameOwner = when (val restore = original.restore) {
            is DelayedDraftRestore -> (snapshot.restore as? DelayedDraftRestore)?.owner == restore.owner
            is DelayedReviewRestore -> snapshot.restore is DelayedReviewRestore &&
                snapshot.restore.version == restore.version
            is DelayedLegacyRestore -> snapshot.restore is DelayedLegacyRestore
        }
        if (job.actorUsername != actor || job.originKind != snapshot.restore.originKind() ||
            job.originId != snapshot.restore.originId() || !sameOwner) {
            throw conflict("DELAYED_REPLY_SOURCE_CONFLICT", "发送任务来源或操作者不一致")
        }
    }

    private fun requireMatrix(job: DelayedManualReplyRow, attemptStatus: String) {
        val valid = when (job.status) {
            DelayedManualReplyStatus.WAITING, DelayedManualReplyStatus.CANCELLED ->
                attemptStatus == "PREPARED" || attemptStatus == "FAILED_SAFE_TO_RETRY"
            DelayedManualReplyStatus.SENDING -> attemptStatus == "DELIVERY_IN_PROGRESS"
            DelayedManualReplyStatus.SENT -> attemptStatus == "SENT"
            DelayedManualReplyStatus.UNKNOWN -> attemptStatus == "DELIVERY_UNKNOWN"
            DelayedManualReplyStatus.FAILED -> attemptStatus == "FAILED" || attemptStatus == "FAILED_SAFE_TO_RETRY"
        }
        if (!valid) throw conflict("DELAYED_REPLY_STATE_CONFLICT", "发送任务状态不一致")
    }

    private fun payloadOf(
        snapshot: DelayedManualReplySnapshot,
        source: SlottedSource,
        actor: String
    ): ManualReplySendAttemptService.SendPayload {
        val payload = snapshot.payload
        return ManualReplySendAttemptService.SendPayload(
            orcidId = payload.orcidId,
            contactId = payload.contactId,
            inboundProcessingId = payload.inboundProcessingId,
            accountCode = payload.accountCode,
            normalizedRecipient = payload.normalizedRecipient,
            subject = payload.subject,
            finalText = payload.finalText,
            finalHtml = payload.finalHtml,
            inReplyTo = payload.inReplyTo,
            canonicalQaRuleIds = payload.canonicalQaRuleIds,
            primaryRuleId = payload.primaryRuleId,
            sourceAnchor = payload.sourceAnchor,
            idempotencyRequestId = payload.idempotencyRequestId,
            calendarAttachment = payload.calendarAttachment,
            meetingEvent = payload.meetingEvent?.let {
                MeetingCalendarInput(
                    startUtc = Instant.ofEpochMilli(it.startUtcMs),
                    endUtc = Instant.ofEpochMilli(it.endUtcMs),
                    meetingLink = it.meetingLink
                )
            },
            outboundAttachments = payload.outboundAttachments
        ).also { rebuilt ->
            when (source) {
                is SlottedSource.Draft -> rebuilt.draftSendRef = source.ref
                is SlottedSource.Review -> rebuilt.reviewSendRef =
                    MailReviewSendRef(source.row, actor, source.confirmationEventId)
                SlottedSource.Legacy -> Unit
            }
            rebuilt.ragEvidence = if (snapshot.evidence.ragFactCodes.isEmpty()) null else
                ManualReplySendAttemptService.RagEvidence(
                    factCodes = snapshot.evidence.ragFactCodes,
                    corpusFingerprint = snapshot.evidence.ragCorpusFingerprint
                )
        }
    }

    private fun decodeSnapshot(row: DelayedManualReplyRow): DelayedManualReplySnapshot =
        DelayedManualReplySnapshotCodec.decodeOrNull(row.snapshotJson)
            ?: throw DelayedManualReplyException(
                422,
                DelayedReplyPublicError.SNAPSHOT_INVALID.code,
                DelayedReplyPublicError.SNAPSHOT_INVALID.message
            )

    // ---------------------------------------------------------------- 内部：授权

    private fun requireActor(actor: String) {
        if (actor.isBlank() || actor.length > MAX_ACTOR_LENGTH) {
            throw DelayedManualReplyException(401, "UNAUTHORIZED", "请先登录")
        }
    }

    /** 审核任务限 admin；普通任务限原 actor 或 admin，user1 仍需当前 FULL。 */
    private fun requireActorCanTransition(job: DelayedManualReplyRow, actor: String) {
        if (job.originKind == DelayedManualReplyOriginKind.REVIEW) {
            if (actor != MailReadOnlyPolicy.ADMIN_USERNAME) {
                throw DelayedManualReplyException(403, "ADMIN_REQUIRED", "审核回复仅管理员可操作")
            }
            return
        }
        if (actor != job.actorUsername && actor != MailReadOnlyPolicy.ADMIN_USERNAME) {
            throw DelayedManualReplyException(403, "MAIL_WRITE_FORBIDDEN", "无权操作该发送任务")
        }
        permissionGuard.requireFull(actor, job.expertContactId)
    }

    private fun DelayedReplyRestore.originKind(): DelayedManualReplyOriginKind = when (this) {
        is DelayedDraftRestore -> DelayedManualReplyOriginKind.DRAFT
        is DelayedReviewRestore -> DelayedManualReplyOriginKind.REVIEW
        is DelayedLegacyRestore -> DelayedManualReplyOriginKind.LEGACY
    }

    private fun DelayedReplyRestore.originId(): Long? = when (this) {
        is DelayedDraftRestore -> draftId
        is DelayedReviewRestore -> reviewId
        is DelayedLegacyRestore -> null
    }

    private fun DelayedReplyRestore.originVersion(): Long? = when (this) {
        is DelayedDraftRestore -> draftVersion
        is DelayedReviewRestore -> version
        is DelayedLegacyRestore -> null
    }

    private fun notFound(): DelayedManualReplyException =
        DelayedManualReplyException(404, "DELAYED_REPLY_NOT_FOUND", "发送任务不存在")

    private fun conflict(code: String, message: String): DelayedManualReplyException =
        DelayedManualReplyException(409, code, message)
}
