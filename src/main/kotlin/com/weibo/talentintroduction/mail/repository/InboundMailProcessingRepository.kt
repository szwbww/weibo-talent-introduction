package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

data class ReasonTypeCount(
    val reasonType: String,
    val count: Long
)

data class SenderAccountLastReceived(
    val senderAccountCode: String,
    val lastReceivedAt: LocalDateTime?
)

interface InboundMailProcessingRepository : CrudRepository<InboundMailProcessing, Long> {
    /**
     * 真实远端身份判重（I-1：V120 后唯一键为 account/uid_validity/uid）。
     * 新写/新收信的判重与代际识别必须使用本 finder，禁止仅按 account+uid 判重。
     */
    fun findBySenderAccountCodeAndUidValidityAndImapUid(
        senderAccountCode: String,
        uidValidity: Long,
        imapUid: Long
    ): InboundMailProcessing?

    /** 兼容读取：按 account+uid（不含代际）的旧 finder；仅允许旧行代际认领核验/历史读取，不得用于新写判重。 */
    fun findBySenderAccountCodeAndImapUid(senderAccountCode: String, imapUid: Long): InboundMailProcessing?

    @Modifying
    @Query("""
        UPDATE inbound_mail_processing
           SET process_status = 'MANUAL_REVIEW',
               process_reason = 'MANUAL_REOPENED',
               reason_type = NULL,
               resolved_at = NULL,
               resolved_by = NULL,
               updated_at = :now
         WHERE id = :id
           AND process_status = 'PROCESSED'
           AND process_reason = 'MANUAL_RESOLVED'
           AND reason_type = 'MANUAL_RESOLVED'
    """)
    fun reopenManualResolved(id: Long, now: LocalDateTime): Int

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

    fun findAllByExpertContactId(expertContactId: Long): List<InboundMailProcessing>

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

    /**
     * 收发件箱“待匹配”队列（I-1/I-2/I-8）：仅未关联专家的 MANUAL_REVIEW 来信，
     * 账号范围由调用方传入（非模拟器集合），搜索/排序/分页全部在 SQL 完成。
     */
    @Query("""
        SELECT * FROM inbound_mail_processing
        WHERE process_status = 'MANUAL_REVIEW'
          AND expert_contact_id IS NULL
          AND sender_account_code IN (:accountCodes)
          AND (:query IS NULL
               OR from_email LIKE CONCAT('%', :query, '%')
               OR subject LIKE CONCAT('%', :query, '%'))
        ORDER BY received_at DESC, id DESC
        LIMIT :limit OFFSET :offset
    """)
    fun findUnmatchedManualReviewQueue(
        accountCodes: List<String>,
        query: String?,
        limit: Int,
        offset: Int
    ): List<InboundMailProcessing>

    /** 同上 WHERE 的计数（I-8：过滤必须在 LIMIT/OFFSET 之前）。 */
    @Query("""
        SELECT COUNT(*) FROM inbound_mail_processing
        WHERE process_status = 'MANUAL_REVIEW'
          AND expert_contact_id IS NULL
          AND sender_account_code IN (:accountCodes)
          AND (:query IS NULL
               OR from_email LIKE CONCAT('%', :query, '%')
               OR subject LIKE CONCAT('%', :query, '%'))
    """)
    fun countUnmatchedManualReviewQueue(accountCodes: List<String>, query: String?): Long

    @Query("""
        SELECT reason_type, COUNT(*) as count
        FROM inbound_mail_processing
        WHERE process_status = 'MANUAL_REVIEW' AND reason_type IS NOT NULL
        GROUP BY reason_type
    """)
    fun countGroupedByReasonType(): List<ReasonTypeCount>

    @Query("""
        SELECT COUNT(*) FROM inbound_mail_processing
        WHERE process_status = 'MANUAL_REVIEW'
          AND sender_account_code IN (:accountCodes)
    """)
    fun countManualReviewByAccounts(accountCodes: List<String>): Long

    @Query("""
        SELECT reason_type, COUNT(*) as count
        FROM inbound_mail_processing
        WHERE process_status = 'MANUAL_REVIEW' AND reason_type IS NOT NULL
          AND sender_account_code IN (:accountCodes)
        GROUP BY reason_type
    """)
    fun countGroupedByReasonTypeForAccounts(accountCodes: List<String>): List<ReasonTypeCount>

    @Query(
        """
        SELECT COUNT(*) FROM inbound_mail_processing
        WHERE process_status = 'MANUAL_REVIEW'
          AND received_at >= :from AND received_at < :to
        """
    )
    fun countManualReviewBetween(from: LocalDateTime, to: LocalDateTime): Long

    @Query(
        """
        SELECT COUNT(*) FROM inbound_mail_processing
        WHERE process_status = 'MANUAL_REVIEW' AND reason_type = 'UNMATCHED_CONTACT'
          AND received_at >= :from AND received_at < :to
        """
    )
    fun countUnmatchedBetween(from: LocalDateTime, to: LocalDateTime): Long

    @Query(
        """
        SELECT * FROM inbound_mail_processing
        WHERE received_at >= :from AND received_at < :to
          AND (:processStatus IS NULL OR process_status = :processStatus)
          AND (:reasonType IS NULL OR reason_type = :reasonType)
        ORDER BY received_at DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listInboundActivity(
        from: LocalDateTime,
        to: LocalDateTime,
        processStatus: String?,
        reasonType: String?,
        limit: Int,
        offset: Int
    ): List<InboundMailProcessing>

    @Query(
        """
        SELECT COUNT(*) FROM inbound_mail_processing
        WHERE received_at >= :from AND received_at < :to
          AND (:processStatus IS NULL OR process_status = :processStatus)
          AND (:reasonType IS NULL OR reason_type = :reasonType)
        """
    )
    fun countInboundActivity(
        from: LocalDateTime,
        to: LocalDateTime,
        processStatus: String?,
        reasonType: String?
    ): Long

    @Query(
        """
        SELECT sender_account_code, MAX(received_at) AS last_received_at
          FROM inbound_mail_processing
         WHERE sender_account_code IS NOT NULL AND sender_account_code <> ''
         GROUP BY sender_account_code
        """
    )
    fun findLastReceivedAtPerAccount(): List<SenderAccountLastReceived>

    @Query(
        """
        SELECT * FROM inbound_mail_processing
        ORDER BY received_at ASC, id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun findAllPagedOrderByReceivedAtAsc(limit: Int, offset: Int): List<InboundMailProcessing>

    @Query("SELECT COUNT(*) FROM inbound_mail_processing")
    fun countAll(): Long

    @Query(
        """
        SELECT p.* FROM inbound_mail_processing p
        WHERE p.received_at >= :from AND p.received_at < :to
          AND p.expert_contact_id IS NOT NULL
          AND (:qaRuleId IS NULL OR EXISTS (
                SELECT 1 FROM inbound_mail_tag t
                WHERE t.inbound_processing_id = p.id AND t.qa_rule_id = :qaRuleId))
          AND (:label IS NULL OR EXISTS (
                SELECT 1 FROM inbound_mail_tag t2
                WHERE t2.inbound_processing_id = p.id
                  AND t2.tag_type = 'CUSTOM'
                  AND t2.label = :label))
        ORDER BY p.received_at DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listInboundSummary(
        from: LocalDateTime,
        to: LocalDateTime,
        qaRuleId: Long?,
        label: String?,
        limit: Int,
        offset: Int
    ): List<InboundMailProcessing>

    @Query(
        """
        SELECT COUNT(*) FROM inbound_mail_processing p
        WHERE p.received_at >= :from AND p.received_at < :to
          AND p.expert_contact_id IS NOT NULL
          AND (:qaRuleId IS NULL OR EXISTS (
                SELECT 1 FROM inbound_mail_tag t
                WHERE t.inbound_processing_id = p.id AND t.qa_rule_id = :qaRuleId))
          AND (:label IS NULL OR EXISTS (
                SELECT 1 FROM inbound_mail_tag t2
                WHERE t2.inbound_processing_id = p.id
                  AND t2.tag_type = 'CUSTOM'
                  AND t2.label = :label))
        """
    )
    fun countInboundSummary(
        from: LocalDateTime,
        to: LocalDateTime,
        qaRuleId: Long?,
        label: String?
    ): Long
}
