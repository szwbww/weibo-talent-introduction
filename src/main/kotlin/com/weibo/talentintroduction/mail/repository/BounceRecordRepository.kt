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
}
