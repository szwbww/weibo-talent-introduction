package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.repository.DelayedManualReplyRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import javax.annotation.PreDestroy

/** No local backlog and no SMTP on the shared scheduler. Existing jobs ignore the entry switch. */
@Component
class DelayedManualReplyWorker(
    private val repository: DelayedManualReplyRepository,
    private val lifecycle: DelayedManualReplyService,
    private val pending: PendingMailOperationService,
    private val attempts: ManualReplySendAttemptService,
    transactionManager: PlatformTransactionManager
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val transactions = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    }
    private val executor = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, SynchronousQueue<Runnable>(),
        { runnable -> Thread(runnable, "manual-reply-delay").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy())

    @Scheduled(fixedDelay = 1000)
    fun tick() {
        try { executor.execute { dispatchDue() } } catch (_: RejectedExecutionException) { /* Busy or stopping. */ }
    }

    /** Recovery never joins the delivery thread and never submits another delivery. */
    @Scheduled(fixedDelay = 1000)
    fun recover() { lifecycle.recoverStale() }

    internal fun preflight(row: DelayedManualReplyRow): Pair<PreparedManualReply, ManualReplySendAttemptService.ClaimedAttempt> {
        val snapshot = DelayedManualReplySnapshotCodec.decodeOrNull(row.snapshotJson)
            ?: invalid()
        if (snapshot.payload.contactId != row.expertContactId || snapshot.payload.accountCode != row.accountCode)
            invalid()
        val identityMatches = when (val restore = snapshot.restore) {
            is DelayedLegacyRestore -> row.originKind == DelayedManualReplyOriginKind.LEGACY && row.originId == null && row.originVersion == null
            is DelayedDraftRestore -> row.originKind == DelayedManualReplyOriginKind.DRAFT && row.originId == restore.draftId &&
                row.originVersion == restore.draftVersion && row.actorUsername == restore.owner
            is DelayedReviewRestore -> row.originKind == DelayedManualReplyOriginKind.REVIEW && row.originId == restore.reviewId &&
                row.originVersion == restore.version && row.actorUsername == restore.actor
        }
        if (!identityMatches) invalid()
        return requireNotNull(transactions.execute {
            val prepared = pending.rehydrateDelayed(snapshot, row.actorUsername, row.status == DelayedManualReplyStatus.FAILED)
            val attempt = attempts.lockDelayedAttempt(row.sendAttemptId)
            val fingerprint = attempts.computeFingerprint(prepared.payload)
            if (attempt.body != fingerprint.fullHex || attempt.recipient != snapshot.payload.normalizedRecipient ||
                attempt.accountCode != row.accountCode) invalid()
            prepared to ManualReplySendAttemptService.ClaimedAttempt(row.sendAttemptId,
                requireNotNull(attempt.messageId), ManualReplySendAttemptService.ClaimResult.CLAIMED)
        })
    }

    private fun invalid(): Nothing = throw DelayedManualReplyException(422,
        DelayedReplyPublicError.SNAPSHOT_INVALID.code, DelayedReplyPublicError.SNAPSHOT_INVALID.message)

    internal fun dispatchDue() {
        for (id in repository.scanDueIds(repository.currentTimeMs())) {
            val row = repository.findById(id) ?: continue
            var claimed = false
            try {
                val (prepared, attempt) = preflight(row)
                val claim = lifecycle.claim(id) ?: continue
                claimed = true
                if (claim.job.snapshotJson != row.snapshotJson || claim.job.actorUsername != row.actorUsername) {
                    attempts.finalizeDelayedFailure(prepared.payload, attempt.attemptId, attempt.messageId,
                        com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus.FAILED_SAFE_TO_RETRY,
                        "Source changed before delivery", claim.dispatchRef)
                    continue
                }
                pending.deliverPrepared(prepared, attempt, claim.dispatchRef)
            } catch (ex: Exception) {
                if (!claimed) {
                    val error = when (ex) {
                        is DelayedManualReplyException -> DelayedReplyPublicError.ofOrInvalid(ex.code) ?: DelayedReplyPublicError.SOURCE_CHANGED
                        is com.weibo.talentintroduction.auth.config.MailReadAccessDeniedException -> DelayedReplyPublicError.ACCESS_CHANGED
                        is MailReviewDraftException -> when {
                            ex.status == 403 -> DelayedReplyPublicError.ACCESS_CHANGED
                            ex.code == "REVIEW_CONTEXT_CHANGED" -> DelayedReplyPublicError.REVIEW_CONTEXT_CHANGED
                            else -> DelayedReplyPublicError.SOURCE_CHANGED
                        }
                        else -> DelayedReplyPublicError.SOURCE_CHANGED
                    }
                    try { lifecycle.preflightFailure(id, row.revision, error) }
                    catch (transition: Exception) { log.warn("Delayed preflight transition failed job={}", id, transition) }
                }
                // Once claimed, deliverPrepared settles known results; interrupted/failed completion is recovered UNKNOWN.
                log.warn("Delayed dispatch failed job={} attempt={} claimed={}", id, row.sendAttemptId, claimed, ex)
            }
        }
    }

    @PreDestroy
    fun close() {
        executor.shutdown()
        try { if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow() }
        catch (_: InterruptedException) { executor.shutdownNow(); Thread.currentThread().interrupt() }
    }
}
