package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

data class ReasonTypeCount(
    val reasonType: String,
    val count: Long
)

interface InboundMailProcessingRepository : CrudRepository<InboundMailProcessing, Long> {
    fun findBySenderAccountCodeAndImapUid(senderAccountCode: String, imapUid: Long): InboundMailProcessing?

    fun findAllByProcessStatusOrderByReceivedAtDesc(processStatus: String): List<InboundMailProcessing>

    fun findAllByProcessStatusAndExpertContactIdIsNullOrderByReceivedAtDesc(processStatus: String): List<InboundMailProcessing>

    fun findAllByExpertContactIdIsNullAndProcessStatusOrderByReceivedAtDesc(
        processStatus: String
    ): List<InboundMailProcessing>

    fun countByProcessStatus(processStatus: String): Long

    fun findFirstByExpertContactIdAndProcessStatusOrderByReceivedAtDesc(
        expertContactId: Long,
        processStatus: String
    ): InboundMailProcessing?

    fun countByExpertContactIdAndProcessStatus(
        expertContactId: Long,
        processStatus: String
    ): Long

    @Query("""
        SELECT * FROM inbound_mail_processing
        WHERE process_status = 'MANUAL_REVIEW'
          AND (:reasonType IS NULL OR reason_type = :reasonType)
          AND (:email IS NULL OR from_email LIKE CONCAT('%', :email, '%'))
          AND (:subject IS NULL OR subject LIKE CONCAT('%', :subject, '%'))
        ORDER BY received_at DESC
        LIMIT :limit OFFSET :offset
    """)
    fun findManualReviewQueue(
        reasonType: String?,
        email: String?,
        subject: String?,
        limit: Int,
        offset: Int
    ): List<InboundMailProcessing>

    @Query("""
        SELECT COUNT(*) FROM inbound_mail_processing
        WHERE process_status = 'MANUAL_REVIEW'
          AND (:reasonType IS NULL OR reason_type = :reasonType)
          AND (:email IS NULL OR from_email LIKE CONCAT('%', :email, '%'))
          AND (:subject IS NULL OR subject LIKE CONCAT('%', :subject, '%'))
    """)
    fun countManualReviewQueue(
        reasonType: String?,
        email: String?,
        subject: String?
    ): Long

    @Query("""
        SELECT reason_type, COUNT(*) as count
        FROM inbound_mail_processing
        WHERE process_status = 'MANUAL_REVIEW' AND reason_type IS NOT NULL
        GROUP BY reason_type
    """)
    fun countGroupedByReasonType(): List<ReasonTypeCount>
}
