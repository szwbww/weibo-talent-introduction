package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.MailRecord
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

data class SenderAccountDailyStats(
    val senderAccountCode: String,
    val introductionCount: Long,
    val autoReplyCount: Long,
    val failedCount: Long,
    val lastSentAt: LocalDateTime?
)

interface MailRecordRepository : CrudRepository<MailRecord, Long> {
    fun findByMailSendAttemptId(mailSendAttemptId: Long): MailRecord?
    fun findAllByExpertContactIdOrderByCreatedAtAsc(expertContactId: Long): List<MailRecord>

    fun existsByExpertContactIdAndDirectionAndMailType(
        expertContactId: Long,
        direction: String,
        mailType: String
    ): Boolean

    fun findByMessageId(messageId: String): MailRecord?

    fun findFirstByMessageIdOrderByCreatedAtDesc(messageId: String): MailRecord?

    @Query(
        """
        SELECT COUNT(*) FROM mail_record
        WHERE direction = 'OUTBOUND' AND mail_type = :mailType
          AND sent_at >= :from AND sent_at < :to
        """
    )
    fun countOutboundByMailTypeBetween(mailType: String, from: LocalDateTime, to: LocalDateTime): Long

    @Query(
        """
        SELECT COUNT(*) FROM mail_record
        WHERE direction = 'INBOUND' AND received_at >= :from AND received_at < :to
        """
    )
    fun countInboundBetween(from: LocalDateTime, to: LocalDateTime): Long

    @Query(
        """
        SELECT COUNT(DISTINCT expert_contact_id) FROM mail_record
        WHERE direction = 'INBOUND' AND received_at >= :from AND received_at < :to
        """
    )
    fun countDistinctRepliedExpertsBetween(from: LocalDateTime, to: LocalDateTime): Long

    @Query(
        """
        SELECT COUNT(*) FROM mail_record
        WHERE direction = 'OUTBOUND' AND triggered_by = 'SYSTEM'
          AND mail_type IN ('QA_REPLY', 'MEETING_INVITATION', 'MEETING_CONFIRMATION')
          AND sent_at >= :from AND sent_at < :to
        """
    )
    fun countAutoRepliesBetween(from: LocalDateTime, to: LocalDateTime): Long

    @Query(
        """
        SELECT COUNT(*) FROM mail_record
        WHERE direction = 'OUTBOUND'
          AND (triggered_by = 'OPERATOR' OR triggered_by IS NULL)
          AND sent_at >= :from AND sent_at < :to
        """
    )
    fun countOperatorOutboundBetween(from: LocalDateTime, to: LocalDateTime): Long

    @Query(
        """
        SELECT COUNT(*) FROM mail_record
        WHERE direction = 'OUTBOUND'
          AND send_status = 'FAILED'
          AND created_at >= :from AND created_at < :to
        """
    )
    fun countFailedOutboundBetween(from: LocalDateTime, to: LocalDateTime): Long

    @Query(
        """
        SELECT * FROM mail_record
        WHERE direction = 'OUTBOUND' AND mail_type = 'INTRODUCTION'
          AND (:from IS NULL OR sent_at >= :from)
          AND (:to IS NULL OR sent_at < :to)
          AND (:senderAccountCode IS NULL OR sender_account_code = :senderAccountCode)
        ORDER BY sent_at DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listIntroductions(
        from: LocalDateTime?,
        to: LocalDateTime?,
        senderAccountCode: String?,
        limit: Int,
        offset: Int
    ): List<MailRecord>

    @Query(
        """
        SELECT COUNT(*) FROM mail_record
        WHERE direction = 'OUTBOUND' AND mail_type = 'INTRODUCTION'
          AND (:from IS NULL OR sent_at >= :from)
          AND (:to IS NULL OR sent_at < :to)
          AND (:senderAccountCode IS NULL OR sender_account_code = :senderAccountCode)
        """
    )
    fun countIntroductions(
        from: LocalDateTime?,
        to: LocalDateTime?,
        senderAccountCode: String?
    ): Long

    @Query(
        """
        SELECT * FROM mail_record
        WHERE direction = 'OUTBOUND'
          AND (:from IS NULL OR sent_at >= :from)
          AND (:to IS NULL OR sent_at < :to)
          AND (:triggeredBy IS NULL OR (triggered_by = :triggeredBy
                                        OR (:triggeredBy = 'OPERATOR' AND triggered_by IS NULL)))
          AND (:mailType IS NULL OR mail_type = :mailType)
          AND (:senderAccountCode IS NULL OR sender_account_code = :senderAccountCode)
          AND (:sendStatus IS NULL OR send_status = :sendStatus)
        ORDER BY sent_at DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listOutboundReplies(
        from: LocalDateTime?,
        to: LocalDateTime?,
        triggeredBy: String?,
        mailType: String?,
        senderAccountCode: String?,
        sendStatus: String?,
        limit: Int,
        offset: Int
    ): List<MailRecord>

    @Query(
        """
        SELECT COUNT(*) FROM mail_record
        WHERE direction = 'OUTBOUND'
          AND (:from IS NULL OR sent_at >= :from)
          AND (:to IS NULL OR sent_at < :to)
          AND (:triggeredBy IS NULL OR (triggered_by = :triggeredBy
                                        OR (:triggeredBy = 'OPERATOR' AND triggered_by IS NULL)))
          AND (:mailType IS NULL OR mail_type = :mailType)
          AND (:senderAccountCode IS NULL OR sender_account_code = :senderAccountCode)
          AND (:sendStatus IS NULL OR send_status = :sendStatus)
        """
    )
    fun countOutboundReplies(
        from: LocalDateTime?,
        to: LocalDateTime?,
        triggeredBy: String?,
        mailType: String?,
        senderAccountCode: String?,
        sendStatus: String?
    ): Long

    @Query("SELECT * FROM mail_record WHERE id = :id")
    fun findByIdOrNull(id: Long): MailRecord?

    @Query(
        """
        SELECT COUNT(*) FROM mail_record
        WHERE expert_contact_id = :expertContactId
          AND direction = 'INBOUND'
          AND mail_type = 'REPLY'
        """
    )
    fun countInboundReplies(expertContactId: Long): Long

    @Query(
        """
        SELECT sender_account_code,
               SUM(CASE WHEN mail_type = 'INTRODUCTION' THEN 1 ELSE 0 END) AS introduction_count,
               SUM(CASE WHEN triggered_by = 'SYSTEM'
                         AND mail_type IN ('QA_REPLY','MEETING_INVITATION','MEETING_CONFIRMATION')
                        THEN 1 ELSE 0 END) AS auto_reply_count,
               SUM(CASE WHEN send_status = 'FAILED' THEN 1 ELSE 0 END) AS failed_count,
               MAX(sent_at) AS last_sent_at
          FROM mail_record
         WHERE direction = 'OUTBOUND'
           AND sender_account_code IS NOT NULL
           AND sent_at >= :from AND sent_at < :to
         GROUP BY sender_account_code
        """
    )
    fun aggregateSenderAccountStats(from: LocalDateTime, to: LocalDateTime): List<SenderAccountDailyStats>

    @Query(
        """
        SELECT DISTINCT sender_account_code FROM mail_record
        WHERE expert_contact_id IN (:contactIds) AND sender_account_code IS NOT NULL
        """
    )
    fun findDistinctSenderAccountCodesByExpertContactIds(contactIds: List<Long>): List<String>

    @Query(
        """
        SELECT COUNT(*) FROM mail_record
         WHERE sender_account_code = :accountCode
           AND direction = 'OUTBOUND'
           AND send_status = 'SENT'
           AND sent_at >= :since
        """
    )
    fun countSentByAccountSince(accountCode: String, since: LocalDateTime): Long

    @Query(
        """
        SELECT * FROM (
          SELECT 'MAIL_RECORD' AS source, mr.id AS id, mr.expert_contact_id,
                 mr.direction, mr.mail_type, mr.sender_account_code, mr.triggered_by,
                 mr.matched_qa_rule_id, mr.send_status, mr.subject,
                 SUBSTRING(COALESCE(mr.cleaned_body, mr.body), 1, 200) AS body_preview,
                 mr.sent_at, CAST(NULL AS DATETIME) AS received_at,
                 CAST(NULL AS CHAR) AS process_status, CAST(NULL AS CHAR) AS reason_type,
                 ec.expert_email, ec.expert_name,
                 CAST(EXISTS(SELECT 1 FROM mail_attachment ma WHERE ma.mail_record_id = mr.id) AS SIGNED) AS has_attachment,
                 CAST(NULL AS SIGNED) AS inbound_processing_id
            FROM mail_record mr
            LEFT JOIN expert_contact ec ON mr.expert_contact_id = ec.id
           WHERE mr.direction = 'OUTBOUND'
             AND mr.sender_account_code IN (:accountCodes)
             AND (:direction IS NULL OR :direction = 'OUTBOUND')
             AND (:onlyPending = 0)
             AND (:accountCode IS NULL OR mr.sender_account_code = :accountCode)
             AND (:keyword IS NULL OR mr.subject LIKE CONCAT('%', :keyword, '%')
                                    OR COALESCE(mr.cleaned_body, mr.body) LIKE CONCAT('%', :keyword, '%'))
             AND (:recipientEmail IS NULL OR ec.expert_email LIKE CONCAT('%', :recipientEmail, '%'))
             AND (:startTime IS NULL OR mr.sent_at >= :startTime)
             AND (:endTime IS NULL OR mr.sent_at < :endTime)
          UNION ALL
          SELECT 'INBOUND_PROCESSING' AS source, imp.id AS id, imp.expert_contact_id,
                 'INBOUND' AS direction, 'REPLY' AS mail_type, imp.sender_account_code,
                 CAST(NULL AS CHAR) AS triggered_by, CAST(NULL AS SIGNED) AS matched_qa_rule_id,
                 CAST(NULL AS CHAR) AS send_status, imp.subject,
                 SUBSTRING(COALESCE(imp.cleaned_body, imp.body), 1, 200) AS body_preview,
                 CAST(NULL AS DATETIME) AS sent_at, imp.received_at,
                 imp.process_status, imp.reason_type,
                 COALESCE(ec2.expert_email, imp.from_email) AS expert_email, ec2.expert_name,
                 CAST(EXISTS(
                   SELECT 1 FROM mail_attachment ma
                     JOIN mail_record mr2 ON ma.mail_record_id = mr2.id
                    WHERE mr2.message_id = imp.message_id AND mr2.direction = 'INBOUND'
                 ) AS SIGNED) AS has_attachment,
                 imp.id AS inbound_processing_id
            FROM inbound_mail_processing imp
            LEFT JOIN expert_contact ec2 ON imp.expert_contact_id = ec2.id
           WHERE imp.sender_account_code IN (:accountCodes)
             AND (:direction IS NULL OR :direction = 'INBOUND')
             AND (:onlyPending = 0 OR imp.process_status = 'MANUAL_REVIEW')
             AND (:accountCode IS NULL OR imp.sender_account_code = :accountCode)
             AND (:keyword IS NULL OR imp.subject LIKE CONCAT('%', :keyword, '%')
                                    OR COALESCE(imp.cleaned_body, imp.body) LIKE CONCAT('%', :keyword, '%'))
             AND (:recipientEmail IS NULL OR imp.from_email LIKE CONCAT('%', :recipientEmail, '%'))
             AND (:startTime IS NULL OR imp.received_at >= :startTime)
             AND (:endTime IS NULL OR imp.received_at < :endTime)
        ) u
        ORDER BY COALESCE(u.sent_at, u.received_at) DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun listMailbox(
        accountCodes: List<String>,
        direction: String?,
        accountCode: String?,
        keyword: String?,
        recipientEmail: String?,
        startTime: LocalDateTime?,
        endTime: LocalDateTime?,
        onlyPending: Int,
        limit: Int,
        offset: Long
    ): List<MailboxRow>

    @Query(
        """
        SELECT COUNT(*) FROM (
          SELECT mr.id
            FROM mail_record mr
            LEFT JOIN expert_contact ec ON mr.expert_contact_id = ec.id
           WHERE mr.direction = 'OUTBOUND'
             AND mr.sender_account_code IN (:accountCodes)
             AND (:direction IS NULL OR :direction = 'OUTBOUND')
             AND (:onlyPending = 0)
             AND (:accountCode IS NULL OR mr.sender_account_code = :accountCode)
             AND (:keyword IS NULL OR mr.subject LIKE CONCAT('%', :keyword, '%')
                                    OR COALESCE(mr.cleaned_body, mr.body) LIKE CONCAT('%', :keyword, '%'))
             AND (:recipientEmail IS NULL OR ec.expert_email LIKE CONCAT('%', :recipientEmail, '%'))
             AND (:startTime IS NULL OR mr.sent_at >= :startTime)
             AND (:endTime IS NULL OR mr.sent_at < :endTime)
          UNION ALL
          SELECT imp.id
            FROM inbound_mail_processing imp
            LEFT JOIN expert_contact ec2 ON imp.expert_contact_id = ec2.id
           WHERE imp.sender_account_code IN (:accountCodes)
             AND (:direction IS NULL OR :direction = 'INBOUND')
             AND (:onlyPending = 0 OR imp.process_status = 'MANUAL_REVIEW')
             AND (:accountCode IS NULL OR imp.sender_account_code = :accountCode)
             AND (:keyword IS NULL OR imp.subject LIKE CONCAT('%', :keyword, '%')
                                    OR COALESCE(imp.cleaned_body, imp.body) LIKE CONCAT('%', :keyword, '%'))
             AND (:recipientEmail IS NULL OR imp.from_email LIKE CONCAT('%', :recipientEmail, '%'))
             AND (:startTime IS NULL OR imp.received_at >= :startTime)
             AND (:endTime IS NULL OR imp.received_at < :endTime)
        ) u
        """
    )
    fun countMailbox(
        accountCodes: List<String>,
        direction: String?,
        accountCode: String?,
        keyword: String?,
        recipientEmail: String?,
        startTime: LocalDateTime?,
        endTime: LocalDateTime?,
        onlyPending: Int
    ): Long
}

data class MailboxRow(
    val source: String,
    val id: Long,
    val expertContactId: Long?,
    val direction: String,
    val mailType: String,
    val senderAccountCode: String?,
    val triggeredBy: String?,
    val matchedQaRuleId: Long?,
    val subject: String?,
    val bodyPreview: String?,
    val sendStatus: String?,
    val sentAt: LocalDateTime?,
    val receivedAt: LocalDateTime?,
    val processStatus: String?,
    val reasonType: String?,
    val expertEmail: String?,
    val expertName: String?,
    val hasAttachment: Long,
    val inboundProcessingId: Long?
)
