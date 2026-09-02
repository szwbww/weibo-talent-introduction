package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.BounceRecord
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

interface BounceRecordRepository : CrudRepository<BounceRecord, Long> {
    fun existsByBounceMessageId(bounceMessageId: String): Boolean

    @Query(
        """
        SELECT COUNT(*) FROM bounce_record
         WHERE sender_account_code = :accountCode
           AND bounce_type = 'HARD'
           AND received_at >= :since
        """
    )
    fun countHardBouncesSince(accountCode: String, since: LocalDateTime): Long

    @Query(
        """
        SELECT COUNT(*) FROM bounce_record
         WHERE sender_account_code = :accountCode
           AND bounce_type = 'SOFT'
           AND received_at >= :since
        """
    )
    fun countSoftBouncesSince(accountCode: String, since: LocalDateTime): Long

    fun findAllBySenderAccountCodeOrderByReceivedAtDesc(accountCode: String): List<BounceRecord>

    @Query(
        """
        SELECT * FROM bounce_record
        WHERE (:accountCode IS NULL OR sender_account_code = :accountCode)
          AND (:bounceType IS NULL OR bounce_type = :bounceType)
        ORDER BY received_at DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun findPaged(
        accountCode: String?,
        bounceType: String?,
        limit: Int,
        offset: Int
    ): List<BounceRecord>

    @Query(
        """
        SELECT COUNT(*) FROM bounce_record
        WHERE (:accountCode IS NULL OR sender_account_code = :accountCode)
          AND (:bounceType IS NULL OR bounce_type = :bounceType)
        """
    )
    fun countPaged(accountCode: String?, bounceType: String?): Long

    // I-4: bounce_record 无任何外键（V29__create_bounce_record.sql），original_expert_contact_id
    // 可能指向已不存在的 expert_contact（孤儿引用）；主查询的 JOIN expert_contact 会丢弃这类行，
    // 故本计数必须用 NOT EXISTS 分支接住，否则 UI 上凭空消失。
    // mail_record.expert_contact_id 有 FK（V1__create_business_tables.sql），发送失败那一支无孤儿问题。
    @Query(
        """
        SELECT COUNT(*) FROM bounce_record br
         WHERE br.received_at >= :from AND br.received_at < :to
           AND (br.original_expert_contact_id IS NULL
                OR NOT EXISTS (SELECT 1 FROM expert_contact ec WHERE ec.id = br.original_expert_contact_id))
        """
    )
    fun countUnattributedBouncesBetween(from: LocalDateTime, to: LocalDateTime): Long
}
