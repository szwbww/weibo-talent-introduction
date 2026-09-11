package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.mail.domain.MailAttachmentTransfer
import com.weibo.talentintroduction.mail.repository.MailAttachmentTransferRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.annotation.PreDestroy

/**
 * 附件传输的领取-下载-提交驱动。独立有界执行器（不占用 manualOutreachExecutor，
 * 不建无界内存队列、不每个文件一条线程）：固定 [GLOBAL_DOWNLOAD_CONCURRENCY] 条
 * 驱动线程循环「恢复过期租约 -> 命名锁内 CAS 领取 -> 下载 -> CAS 提交」；单条
 * 租约续租/总时限 watchdog 由独立调度线程执行。
 *
 * 领取/恢复/提交的 SQL 语义见 [MailAttachmentTransferRepository]；网络下载期间
 * 不持有 DB 锁或事务。租约 15s/续租 2s/总时限 10min 为保守默认，全部来自
 * [MailAttachmentStorageProperties]。
 *
 * purpose 消费者（如 05 的 DMARC 解析）按 purpose 注册到 [AttachmentTransferPurposeConsumer]；
 * 未知 purpose 的 QUEUED 行保持未执行并报配置错误（不领取、不下载、不失败）。
 * MATERIAL 是内建终局（落盘 + 回写 mail_attachment + STORED），不经过消费者。
 */
@Component
class AttachmentTransferWorker(
    private val transferRepository: MailAttachmentTransferRepository,
    private val senderAccountRepository: MailSenderAccountRepository,
    private val fetcher: ImapAttachmentContentFetcher,
    private val properties: MailAttachmentStorageProperties,
    private val transactionTemplate: TransactionTemplate,
    private val jdbcTemplate: JdbcTemplate,
    purposeConsumers: List<AttachmentTransferPurposeConsumer>
) {
    private val log = LoggerFactory.getLogger(AttachmentTransferWorker::class.java)

    private val consumersByPurpose: Map<String, AttachmentTransferPurposeConsumer> =
        purposeConsumers.associateBy { it.purpose }

    private val lock = Any()
    private var running = false
    private var driverExecutor: ScheduledExecutorService? = null
    private var renewerExecutor: ScheduledExecutorService? = null

    /** 已领取、正在下载的任务；续租/总时限 watchdog 按此巡检。 */
    private val activeTransfers = ConcurrentHashMap<Long, ActiveTransfer>()

    private val lastMissingLogMs = AtomicLong(0)
    private val lastRecoverMs = AtomicLong(0)

    /** 最近一次扫描发现的无消费者 QUEUED purpose（观察/配置错误上报）。 */
    @Volatile
    private var lastMissingPurposes: Set<String> = emptySet()

    /**
     * 只在 Spring Boot 已完成启动后才领取任务，确保 Flyway、数据源和全部 purpose
     * consumer 已就绪。重复 ApplicationReadyEvent 由 [start] 的幂等保护吸收。
     */
    @EventListener(ApplicationReadyEvent::class)
    fun startAfterApplicationReady() = start()

    /** Spring 正常关闭时中断进行中的下载并释放本 worker 的线程池。 */
    @PreDestroy
    fun stopBeforeApplicationShutdown() = stop()

    fun start() {
        synchronized(lock) {
            if (running) return
            running = true
            lastMissingPurposes = emptySet()
            val drivers = Executors.newScheduledThreadPool(
                GLOBAL_DOWNLOAD_CONCURRENCY
            ) { runnable ->
                Thread(runnable, "attachment-transfer-").apply { isDaemon = true }
            }
            driverExecutor = drivers
            repeat(GLOBAL_DOWNLOAD_CONCURRENCY) {
                drivers.scheduleWithFixedDelay(
                    { runDriverPass() },
                    0L,
                    POLL_INTERVAL_MS,
                    TimeUnit.MILLISECONDS
                )
            }
            // 续租/总时限 watchdog 独立线程：驱动线程可能长时间阻塞在下载读上，
            // 若共用线程池会被占满而无法按时续租（假 LEASE_LOST）。
            val renewer = Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "attachment-transfer-renew-").apply { isDaemon = true }
            }
            renewerExecutor = renewer
            renewer.scheduleWithFixedDelay(
                { renewAndWatchdog() },
                properties.transferRenewSeconds,
                properties.transferRenewSeconds,
                TimeUnit.SECONDS
            )
        }
    }

    fun stop() {
        synchronized(lock) {
            if (!running) return
            running = false
            activeTransfers.values.forEach { it.abort(REASON_STOPPED) }
            driverExecutor?.shutdownNow()
            driverExecutor = null
            renewerExecutor?.shutdownNow()
            renewerExecutor = null
        }
    }

    fun isRunning(): Boolean = running

    /** 有处理器（内建 MATERIAL + 已注册消费者）的 purpose 集合。 */
    fun handledPurposes(): Set<String> =
        setOf(MailAttachmentTransfer.PURPOSE_MATERIAL) + consumersByPurpose.keys

    /** 最近一次领取扫描发现的、无消费者的 QUEUED purpose（配置错误证据）。 */
    fun lastMissingConsumerPurposes(): Set<String> = lastMissingPurposes

    private fun runDriverPass() {
        if (!running) return
        try {
            val now = LocalDateTime.now()
            if (recoverExpiredLeasesIfDue(now)) {
                val claimed = claimOne(now)
                if (claimed != null) {
                    processClaimed(claimed)
                } else {
                    reportMissingConsumers(now)
                }
            }
        } catch (t: Throwable) {
            log.error("attachment transfer driver pass failed", t)
        }
    }

    private fun recoverExpiredLeasesIfDue(now: LocalDateTime): Boolean {
        val lastRecover = lastRecoverMs.get()
        if (System.currentTimeMillis() - lastRecover < RECOVER_INTERVAL_MS) return true
        if (!lastRecoverMs.compareAndSet(lastRecover, System.currentTimeMillis())) return true
        return try {
            transferRepository.recoverExpiredLeases(now)
            true
        } catch (e: Exception) {
            log.warn("expired lease recovery failed, will retry", e)
            false
        }
    }

    private fun reportMissingConsumers(now: LocalDateTime) {
        val missing = try {
            transferRepository.findQueuedPurposes()
                .filterNot { it in handledPurposes() }
                .toSet()
        } catch (e: Exception) {
            return
        }
        lastMissingPurposes = missing
        if (missing.isNotEmpty()) {
            val lastLog = lastMissingLogMs.get()
            if (System.currentTimeMillis() - lastLog > MISSING_CONSUMER_LOG_INTERVAL_MS &&
                lastMissingLogMs.compareAndSet(lastLog, System.currentTimeMillis())
            ) {
                log.error(
                    "queued attachment transfers have no purpose consumer and stay unexecuted (config error): {}",
                    missing.sorted()
                )
            }
        }
    }

    /**
     * 命名锁 + 容量检查 + CAS 领取。同一事务/同一物理连接内持有短命名锁
     * talent-attachment-claim，finally 释放，绝不把锁带回连接池。
     */
    private fun claimOne(now: LocalDateTime): MailAttachmentTransfer? =
        transactionTemplate.execute {
            val acquired = transferRepository.acquireNamedLock(CLAIM_LOCK_NAME, 5)
            if (acquired != 1L) {
                log.warn("could not acquire attachment claim lock within timeout")
                return@execute null
            }
            try {
                var claimed: MailAttachmentTransfer? = null
                val token = UUID.randomUUID().toString()
                val leaseUntil = now.plusSeconds(properties.transferLeaseSeconds)
                for (candidate in transferRepository.findQueuedCandidates(QUEUE_SCAN_LIMIT)) {
                    if (candidate.purpose !in handledPurposes()) continue
                    val updated = transferRepository.tryClaim(
                        candidate.id ?: continue,
                        candidate.accountCode,
                        token,
                        leaseUntil,
                        now,
                        now,
                        GLOBAL_DOWNLOAD_CONCURRENCY,
                        ACCOUNT_DOWNLOAD_CONCURRENCY
                    )
                    if (updated == 1) {
                        claimed = transferRepository
                            .findById(candidate.id ?: continue)
                            .orElse(null)
                        break
                    }
                }
                claimed
            } finally {
                transferRepository.releaseNamedLock(CLAIM_LOCK_NAME)
            }
        }

    /** 续租 + 总时限 watchdog：总时限不能被续租延长，到期或失去租约即中止并硬关闭连接。 */
    private fun renewAndWatchdog() {
        if (!running) return
        val now = LocalDateTime.now()
        for (active in activeTransfers.values) {
            try {
                if (active.abortReason.get() != null) continue
                if (!now.isBefore(active.deadline)) {
                    active.abort(REASON_TOTAL_TIMEOUT)
                    continue
                }
                val updated = transferRepository.renewLease(
                    active.transferId,
                    active.workerToken,
                    now.plusSeconds(properties.transferLeaseSeconds),
                    active.bytesWritten,
                    now
                )
                if (updated != 1) {
                    active.abort(REASON_LEASE_LOST)
                }
            } catch (e: Exception) {
                // 瞬时 DB 故障：下一 tick 重试；租约过期后由恢复路径回收。
                log.warn("lease renewal failed for transfer {}", active.transferId, e)
            }
        }
    }

    private fun processClaimed(claimed: MailAttachmentTransfer) {
        val transferId = claimed.id ?: return
        val startedAt = LocalDateTime.now()
        val ctx = ActiveTransfer(
            transferId = transferId,
            workerToken = claimed.workerToken ?: return,
            deadline = startedAt.plusSeconds(properties.transferTotalTimeoutSeconds)
        )
        activeTransfers[transferId] = ctx
        try {
            val dir = transferDirectory(transferId)
            if (claimed.purpose == MailAttachmentTransfer.PURPOSE_MATERIAL) {
                downloadToMaterialStored(claimed, ctx, dir)
            } else {
                downloadToConsumer(claimed, ctx, dir)
            }
        } catch (t: Throwable) {
            log.error("unexpected failure processing transfer {}", transferId, t)
            failAttempt(
                claimed, ctx,
                MailAttachmentTransfer.STATE_FAILED, "INTERNAL_ERROR",
                "unexpected internal failure (see logs)"
            )
        } finally {
            activeTransfers.remove(transferId)
            ctx.closeHook = null
        }
    }

    // ------------------------------------------------------------------
    // MATERIAL：下载 -> .part -> 原子转正 -> 事务提交（回写 mail_attachment）
    // ------------------------------------------------------------------

    private fun downloadToMaterialStored(
        claimed: MailAttachmentTransfer,
        ctx: ActiveTransfer,
        dir: Path
    ) {
        val finalPath = dir.resolve(FINAL_FILE_NAME)
        cleanupStalePartFiles(dir, ctx.workerToken)
        if (Files.isRegularFile(finalPath)) {
            // 崩溃收敛：上一尝试已原子转正但 DB 未提交；不重复下载，直接核验提交。
            commitStoredFile(claimed, ctx, Files.size(finalPath), finalPath)
            return
        }
        downloadPart(claimed, ctx, dir) { bytes ->
            val partFile = dir.resolve("${ctx.workerToken}.part")
            moveAtomically(partFile, finalPath)
            commitStoredFile(claimed, ctx, bytes, finalPath)
        }
    }

    private fun downloadToConsumer(
        claimed: MailAttachmentTransfer,
        ctx: ActiveTransfer,
        dir: Path
    ) {
        val consumer = consumersByPurpose[claimed.purpose]
        if (consumer == null) {
            // 领取前已过滤，此处仅防御；未知 purpose 保持未执行。
            log.error("no purpose consumer for transfer {}", claimed.id)
            return
        }
        cleanupStalePartFiles(dir, ctx.workerToken)
        downloadPart(claimed, ctx, dir) { bytes ->
            val partFile = dir.resolve("${ctx.workerToken}.part")
            try {
                consumer.consume(
                    AttachmentTransferConsumeContext(
                        transferId = claimed.id ?: -1,
                        file = partFile,
                        byteCount = bytes,
                        contentType = claimed.contentType
                    )
                )
                deleteQuietly(partFile)
                commitStoredEphemeral(claimed, ctx, bytes)
            } catch (e: AttachmentTransferConsumeError) {
                deleteQuietly(partFile)
                failAttempt(
                    claimed, ctx,
                    MailAttachmentTransfer.STATE_FAILED, e.code, e.message
                )
            } catch (e: Exception) {
                deleteQuietly(partFile)
                log.error("purpose consumer failed for transfer {}", claimed.id, e)
                failAttempt(
                    claimed, ctx,
                    MailAttachmentTransfer.STATE_FAILED, "CONSUMER_FAILED",
                    "purpose handler failed (see logs)"
                )
            }
        }
    }

    /** 下载一个已领取 part 到 .part；成功后回调（移动+提交由调用方决定）。 */
    private fun downloadPart(
        claimed: MailAttachmentTransfer,
        ctx: ActiveTransfer,
        dir: Path,
        onSuccess: (Long) -> Unit
    ) {
        val account = senderAccountRepository.findByAccountCode(claimed.accountCode)
        if (account == null) {
            failAttempt(
                claimed, ctx,
                MailAttachmentTransfer.STATE_FAILED, "ACCOUNT_NOT_FOUND",
                "sender account for this transfer no longer exists"
            )
            return
        }
        val resolved = try {
            fetcher.resolve(
                account,
                RemotePartSource(
                    folder = claimed.folder,
                    uidValidity = claimed.uidValidity,
                    uid = claimed.imapUid,
                    messageId = claimed.messageId,
                    partPath = claimed.partPath,
                    expectedContentType = claimed.contentType
                )
            )
        } catch (e: AttachmentFetchException) {
            failAttempt(claimed, ctx, e)
            return
        }
        ctx.closeHook = { runCatching { resolved.forceClose() } }
        try {
            if (ctx.abortReason.get() != null) return // 领取后、流开始前被中止
            val partFile = dir.resolve("${ctx.workerToken}.part")
            val bytes = try {
                Files.newOutputStream(partFile).use { out ->
                    val counting = ProgressOutputStream(out) { ctx.bytesWritten = it }
                    resolved.streamContent(counting, properties.transferMaxBytes) {
                        ctx.abortReason.get()
                    }
                }
            } catch (e: AttachmentFetchException) {
                deleteQuietly(partFile)
                failAttempt(claimed, ctx, e)
                return
            } catch (e: IOException) {
                deleteQuietly(partFile)
                log.warn("local .part write failed for transfer {}", claimed.id, e)
                failAttempt(
                    claimed, ctx,
                    MailAttachmentTransfer.STATE_FAILED, "STORAGE_ERROR",
                    "local file write failed (see logs)"
                )
                return
            } catch (e: SecurityException) {
                deleteQuietly(partFile)
                failAttempt(
                    claimed, ctx,
                    MailAttachmentTransfer.STATE_FAILED, "STORAGE_ERROR",
                    "local file write denied (see logs)"
                )
                return
            }
            onSuccess(bytes)
        } finally {
            runCatching { resolved.close() }
            ctx.closeHook = null
        }
    }

    /** 原子转正 + 事务提交；STORED 前提是本地最终文件真实存在。 */
    private fun commitStoredFile(
        claimed: MailAttachmentTransfer,
        ctx: ActiveTransfer,
        bytes: Long,
        finalPath: Path
    ) {
        if (ctx.abortReason.get() != null) return
        if (!Files.isRegularFile(finalPath)) {
            failAttempt(
                claimed, ctx,
                MailAttachmentTransfer.STATE_FAILED, "STORAGE_ERROR",
                "final file missing before commit"
            )
            return
        }
        commitStoredWithAttachmentUpdate(claimed, ctx, bytes, finalPath)
    }

    private fun commitStoredWithAttachmentUpdate(
        claimed: MailAttachmentTransfer,
        ctx: ActiveTransfer,
        bytes: Long,
        finalPath: Path
    ) {
        if (ctx.abortReason.get() != null) return
        val updated = try {
            transactionTemplate.execute {
                val u = transferRepository.commitStored(
                    claimed.id ?: return@execute 0,
                    ctx.workerToken,
                    bytes,
                    LocalDateTime.now()
                )
                if (u == 1 && claimed.attachmentId != null) {
                    // mail_attachment 无 updated_at 列（V7 只有 created_at）。
                    jdbcTemplate.update(
                        """
                        UPDATE mail_attachment
                           SET storage_path = ?, file_size = ?
                         WHERE id = ? AND storage_path IS NULL
                        """.trimIndent(),
                        finalPath.toString(),
                        bytes,
                        claimed.attachmentId
                    )
                }
                u
            } ?: 0
        } catch (e: Exception) {
            log.error("STORED commit failed for transfer {}", claimed.id, e)
            0
        }
        if (updated != 1) {
            // 租约丢失/被恢复：新尝试会走确定性路径收敛（文件已在则不再下载）。
            log.info(
                "transfer {} commit lost the race (lease/token changed); file stays for convergence",
                claimed.id
            )
        }
    }

    private fun commitStoredEphemeral(
        claimed: MailAttachmentTransfer,
        ctx: ActiveTransfer,
        bytes: Long
    ) {
        if (ctx.abortReason.get() != null) return
        try {
            val updated = transactionTemplate.execute {
                transferRepository.commitStored(
                    claimed.id ?: return@execute 0,
                    ctx.workerToken,
                    bytes,
                    LocalDateTime.now()
                )
            } ?: 0
            if (updated != 1) {
                log.info("transfer {} ephemeral commit lost the race", claimed.id)
            }
        } catch (e: Exception) {
            log.error("ephemeral STORED commit failed for transfer {}", claimed.id, e)
        }
    }

    // ------------------------------------------------------------------
    // 失败落库（CAS；错误脱敏）
    // ------------------------------------------------------------------

    private fun failAttempt(
        claimed: MailAttachmentTransfer,
        ctx: ActiveTransfer,
        e: AttachmentFetchException
    ) {
        if (e is AttachmentFetchException.Aborted) {
            if (e.reason == REASON_LEASE_LOST || e.reason == REASON_STOPPED) {
                // 旧 worker 停手：行已被恢复/他人重新领取或正在停止，不能提交任何结局。
                log.info(
                    "transfer {} aborted ({}); stale worker stops without committing",
                    claimed.id, e.reason
                )
                return
            }
        }
        failAttempt(claimed, ctx, e.terminalState, e.code, e.message)
    }

    private fun failAttempt(
        claimed: MailAttachmentTransfer,
        ctx: ActiveTransfer,
        state: String,
        code: String,
        message: String?
    ) {
        if (ctx.abortReason.get() == REASON_STOPPED) return
        val sanitized = message?.take(MAX_ERROR_MESSAGE_LENGTH)
        try {
            transactionTemplate.execute {
                transferRepository.failAttempt(
                    claimed.id ?: return@execute 0,
                    ctx.workerToken,
                    state,
                    code,
                    sanitized,
                    ctx.bytesWritten,
                    LocalDateTime.now()
                )
            }
        } catch (e: Exception) {
            log.error("failed to persist transfer failure for {}", claimed.id, e)
        }
    }

    // ------------------------------------------------------------------
    // 文件路径助手（客户端文件名永不参与路径拼接）
    // ------------------------------------------------------------------

    private fun transferDirectory(id: Long): Path {
        val dir = Path.of(properties.basePath, "transfer", id.toString())
        Files.createDirectories(dir)
        return dir
    }

    private fun cleanupStalePartFiles(dir: Path, keepToken: String) {
        if (!Files.isDirectory(dir)) return
        val keepName = "$keepToken.part"
        Files.list(dir).use { stream ->
            stream
                .filter { it.fileName.toString().endsWith(".part") }
                .filter { it.fileName.toString() != keepName }
                .forEach { deleteQuietly(it) }
        }
    }

    private fun moveAtomically(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private fun deleteQuietly(path: Path?) {
        if (path == null) return
        try {
            Files.deleteIfExists(path)
        } catch (e: IOException) {
            log.debug("could not delete {}", path, e)
        }
    }

    private class ActiveTransfer(
        val transferId: Long,
        val workerToken: String,
        val deadline: LocalDateTime,
        val abortReason: AtomicReference<String?> = AtomicReference(null)
    ) {
        @Volatile
        var bytesWritten: Long = 0

        @Volatile
        var closeHook: (() -> Unit)? = null

        fun abort(reason: String) {
            if (abortReason.compareAndSet(null, reason)) {
                closeHook?.invoke()
            }
        }
    }

    /** 计数写流：把本次尝试已写实际字节持续上报给 [ActiveTransfer.bytesWritten]。 */
    private class ProgressOutputStream(
        private val delegate: java.io.OutputStream,
        private val onBytes: (Long) -> Unit
    ) : java.io.OutputStream() {
        private var count = 0L

        override fun write(b: Int) {
            delegate.write(b)
            count += 1
            onBytes(count)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            count += len
            onBytes(count)
        }

        override fun flush() = delegate.flush()

        override fun close() = delegate.close()
    }

    companion object {
        const val CLAIM_LOCK_NAME = "talent-attachment-claim"

        /** 独立全局并发与同账号并发上限（I-3；不共享 manualOutreachExecutor）。 */
        const val GLOBAL_DOWNLOAD_CONCURRENCY = 2
        const val ACCOUNT_DOWNLOAD_CONCURRENCY = 1

        /** .part 与最终文件都在 basePath/transfer/{id}/；最终文件名固定，与客户端文件名无关。 */
        const val FINAL_FILE_NAME = "content.bin"

        const val REASON_TOTAL_TIMEOUT = "TOTAL_TIMEOUT"
        const val REASON_LEASE_LOST = "LEASE_LOST"
        const val REASON_STOPPED = "STOPPED"

        private const val QUEUE_SCAN_LIMIT = 50
        private const val POLL_INTERVAL_MS = 250L
        private const val RECOVER_INTERVAL_MS = 1_000L
        private const val MISSING_CONSUMER_LOG_INTERVAL_MS = 10_000L
        private const val MAX_ERROR_MESSAGE_LENGTH = 500
    }
}

/**
 * purpose 消费者：某个 purpose 的 QUEUED 行只有在注册了消费者（或内建 MATERIAL）
 * 后才会被领取执行。消费者在 [consume] 中处理已完整下载的 part 临时文件；
 * 抛 [AttachmentTransferConsumeError] 行落 FAILED，其他异常按 CONSUMER_FAILED。
 */
interface AttachmentTransferPurposeConsumer {
    val purpose: String

    fun consume(context: AttachmentTransferConsumeContext)
}

class AttachmentTransferConsumeContext(
    val transferId: Long,
    /** 完整下载的 part 内容临时文件（.part 命名，位于 basePath/transfer/{id}/）。 */
    val file: Path,
    val byteCount: Long,
    val contentType: String?
)

/** purpose 消费者可抛的业务失败；message 需已脱敏（无正文/凭据）。 */
class AttachmentTransferConsumeError(val code: String, message: String) :
    RuntimeException(message)

/**
 * worker 的 CAS 领取/提交与 register 并发冲突回查都需要编程式事务：
 * - 领取的命名锁必须与 claim 同一物理连接（worker 调用点本身无外层事务）；
 * - register 的「冲突后回查」必须脱离已 rollback-only 的旧事务（新快照）。
 * 统一 REQUIRES_NEW：worker 调用点无外层事务时等价于新事务，register 冲突回查
 * 时真正另开事务。Spring Boot 不自动提供 TransactionTemplate bean，这里显式注册
 * （同类先例：TaskAuditRetentionScheduler 同文件注册配置）。
 */
@Configuration
class AttachmentTransferTransactionConfig {
    @Bean
    fun attachmentTransferTransactionTemplate(
        transactionManager: PlatformTransactionManager
    ): TransactionTemplate =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
}
