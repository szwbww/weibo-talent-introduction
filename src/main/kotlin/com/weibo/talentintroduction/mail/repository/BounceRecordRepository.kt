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

    @Query(
        """
        SELECT SUBSTRING_INDEX(failed_recipient, '@', -1) AS domain,
               SUM(CASE WHEN bounce_type = 'HARD' THEN 1 ELSE 0 END) AS hard_count,
               SUM(CASE WHEN bounce_type = 'SOFT' THEN 1 ELSE 0 END) AS soft_count
          FROM bounce_record
         WHERE received_at >= :from AND received_at < :to
         GROUP BY SUBSTRING_INDEX(failed_recipient, '@', -1)
        """
    )
    fun aggregateBouncesByDomain(from: LocalDateTime, to: LocalDateTime): List<DomainBounceCount>
}

data class DomainBounceCount(
    val domain: String?,
    val hardCount: Long,
    val softCount: Long
)
