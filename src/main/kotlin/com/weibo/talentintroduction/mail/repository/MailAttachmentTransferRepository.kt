package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.MailAttachmentTransfer
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

/**
 * mail_attachment_transfer 的全部状态写路径：
 *
 * - 按源唯一身份（account_code,folder,uid_validity,imap_uid,part_path）幂等查找；
 *   登记并发唯一性由 uk_mail_attachment_transfer_source 兜底。
 * - 请求下载（入队）：仅 METADATA_ONLY|FAILED|SOURCE_UNAVAILABLE -> QUEUED
 *   （重复请求 QUEUED/DOWNLOADING/STORED 不新建、不改写）。
 * - CAS 领取：state=QUEUED 且全局活动数 < globalMax、同账号活动数 < accountMax
 *   才置 DOWNLOADING；attempt+1、bytes_downloaded 归 0、error 清除。容量子查询
 *   用派生表物化，规避 MySQL「UPDATE 目标表不能直接出现在子查询」限制，并保证
 *   跨进程领取安全（不只依赖进程内命名锁）。
 * - 续租/提交/失败全部按 id + worker_token + 租约未过期 CAS；过期或失去 token
 *   的 worker 影响行数 0，绝不能覆盖新尝试。
 * - 租约恢复仅限已请求项：DOWNLOADING 且租约过期 -> QUEUED（重新领取才下载，
 *   METADATA_ONLY/FAILED/STORED 不被恢复触碰）。
 *
 * 所有 GET_LOCK/RELEASE_LOCK 必须与 claim 处于同一事务（同一物理连接），
 * 由调用方（worker）在 finally 释放，禁止连接归还连接池时遗留锁。
 */
interface MailAttachmentTransferRepository : CrudRepository<MailAttachmentTransfer, Long> {

    fun findByAccountCodeAndFolderAndUidValidityAndImapUidAndPartPath(
        accountCode: String,
        folder: String,
        uidValidity: Long,
        imapUid: Long,
        partPath: String
    ): MailAttachmentTransfer?

    fun findByAttachmentId(attachmentId: Long): MailAttachmentTransfer?

    @Query(
        """
        SELECT * FROM mail_attachment_transfer
         WHERE state = 'QUEUED'
         ORDER BY queued_at, id
         LIMIT :limit
        """
    )
    fun findQueuedCandidates(limit: Int): List<MailAttachmentTransfer>

    @Query("SELECT COUNT(*) FROM mail_attachment_transfer WHERE state = 'QUEUED'")
    fun countQueued(): Long

    @Query(
        """
        SELECT COUNT(*) FROM mail_attachment_transfer
         WHERE state = 'DOWNLOADING' AND lease_until > :now
        """
    )
    fun countActiveGlobal(now: LocalDateTime): Long

    @Query(
        """
        SELECT COUNT(*) FROM mail_attachment_transfer
         WHERE state = 'DOWNLOADING' AND lease_until > :now
           AND account_code = :accountCode
        """
    )
    fun countActiveByAccount(accountCode: String, now: LocalDateTime): Long

    @Query(
        """
        SELECT DISTINCT purpose FROM mail_attachment_transfer WHERE state = 'QUEUED'
        """
    )
    fun findQueuedPurposes(): List<String>

    /** 显式请求下载：仅登记/失败态可入队；重复请求其他状态不改写。 */
    @Modifying
    @Query(
        """
        UPDATE mail_attachment_transfer
           SET state = 'QUEUED',
               queued_at = :now,
               requested_by = :requestedBy,
               started_at = NULL,
               lease_until = NULL,
               worker_token = NULL,
               bytes_downloaded = 0,
               error_code = NULL,
               error_message = NULL,
               updated_at = :now
         WHERE id = :id
           AND state IN ('METADATA_ONLY', 'FAILED', 'SOURCE_UNAVAILABLE')
        """
    )
    fun markRequested(id: Long, requestedBy: String, now: LocalDateTime): Int

    /** CAS 领取：容量不足或已被领取返回 0。 */
    @Modifying
    @Query(
        """
        UPDATE mail_attachment_transfer t
           SET state = 'DOWNLOADING',
               worker_token = :workerToken,
               lease_until = :leaseUntil,
               started_at = :startedAt,
               bytes_downloaded = 0,
               error_code = NULL,
               error_message = NULL,
               attempt = attempt + 1,
               updated_at = :now
         WHERE t.id = :id
           AND t.state = 'QUEUED'
           AND (SELECT COUNT(*) FROM (
                    SELECT 1 FROM mail_attachment_transfer a
                     WHERE a.state = 'DOWNLOADING' AND a.lease_until > :now
                ) g) < :globalMax
           AND (SELECT COUNT(*) FROM (
                    SELECT 1 FROM mail_attachment_transfer a
                     WHERE a.state = 'DOWNLOADING' AND a.lease_until > :now
                       AND a.account_code = :accountCode
                ) p) < :accountMax
        """
    )
    fun tryClaim(
        id: Long,
        accountCode: String,
        workerToken: String,
        leaseUntil: LocalDateTime,
        startedAt: LocalDateTime,
        now: LocalDateTime,
        globalMax: Int,
        accountMax: Int
    ): Int

    /** 续租（同时上报本次已写字节）；租约已过期或 token 失效返回 0。 */
    @Modifying
    @Query(
        """
        UPDATE mail_attachment_transfer
           SET lease_until = :leaseUntil,
               bytes_downloaded = :bytesDownloaded,
               updated_at = :now
         WHERE id = :id
           AND state = 'DOWNLOADING'
           AND worker_token = :workerToken
           AND lease_until > :now
        """
    )
    fun renewLease(
        id: Long,
        workerToken: String,
        leaseUntil: LocalDateTime,
        bytesDownloaded: Long,
        now: LocalDateTime
    ): Int

    /** CAS 提交 STORED；清空租约/token/错误，bytes_downloaded 保留实际字节。 */
    @Modifying
    @Query(
        """
        UPDATE mail_attachment_transfer
           SET state = 'STORED',
               lease_until = NULL,
               worker_token = NULL,
               bytes_downloaded = :bytesDownloaded,
               error_code = NULL,
               error_message = NULL,
               updated_at = :now
         WHERE id = :id
           AND state = 'DOWNLOADING'
           AND worker_token = :workerToken
           AND lease_until > :now
        """
    )
    fun commitStored(
        id: Long,
        workerToken: String,
        bytesDownloaded: Long,
        now: LocalDateTime
    ): Int

    /** CAS 失败落 FAILED / SOURCE_UNAVAILABLE（脱敏原因，无 stack/凭据/正文）。 */
    @Modifying
    @Query(
        """
        UPDATE mail_attachment_transfer
           SET state = :state,
               lease_until = NULL,
               worker_token = NULL,
               bytes_downloaded = :bytesDownloaded,
               error_code = :errorCode,
               error_message = :errorMessage,
               updated_at = :now
         WHERE id = :id
           AND state = 'DOWNLOADING'
           AND worker_token = :workerToken
           AND lease_until > :now
        """
    )
    fun failAttempt(
        id: Long,
        workerToken: String,
        state: String,
        errorCode: String?,
        errorMessage: String?,
        bytesDownloaded: Long,
        now: LocalDateTime
    ): Int

    /** 租约恢复：仅已请求项（DOWNLOADING 且租约过期）回 QUEUED，供重新领取。 */
    @Modifying
    @Query(
        """
        UPDATE mail_attachment_transfer
           SET state = 'QUEUED',
               queued_at = :now,
               started_at = NULL,
               lease_until = NULL,
               worker_token = NULL,
               bytes_downloaded = 0,
               error_code = NULL,
               error_message = NULL,
               updated_at = :now
         WHERE state = 'DOWNLOADING'
           AND (lease_until IS NULL OR lease_until <= :now)
        """
    )
    fun recoverExpiredLeases(now: LocalDateTime): Int

    /**
     * 领取必须持有短 MySQL 命名锁 talent-attachment-claim；GET_LOCK 是连接级
     * 的，调用方必须让本方法与 claim CAS 运行在同一事务（同一物理连接），
     * 并在 finally 释放。
     */
    @Query("SELECT GET_LOCK(:lockName, :timeoutSeconds)")
    fun acquireNamedLock(lockName: String, timeoutSeconds: Int): Long

    @Query("SELECT RELEASE_LOCK(:lockName)")
    fun releaseNamedLock(lockName: String): Long
}
