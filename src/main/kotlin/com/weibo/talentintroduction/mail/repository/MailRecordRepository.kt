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

data class CountryCount(
    val country: String?,
    val count: Long
)

data class DomainCount(
    val domain: String?,
    val count: Long
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
        SELECT ec.country AS country, COUNT(*) AS count
          FROM mail_record mr
          JOIN expert_contact ec ON mr.expert_contact_id = ec.id
         WHERE mr.direction = 'OUTBOUND'
           AND mr.mail_type = 'INTRODUCTION'
           AND mr.sent_at >= :from AND mr.sent_at < :to
         GROUP BY ec.country
        """
    )
    fun aggregateIntroSentByCountry(from: LocalDateTime, to: LocalDateTime): List<CountryCount>

    @Query(
        """
        SELECT ec.country AS country, COUNT(DISTINCT mr.expert_contact_id) AS count
          FROM mail_record mr
          JOIN expert_contact ec ON mr.expert_contact_id = ec.id
         WHERE mr.direction = 'INBOUND'
           AND mr.received_at >= :from AND mr.received_at < :to
         GROUP BY ec.country
        """
    )
    fun aggregateInboundByCountry(from: LocalDateTime, to: LocalDateTime): List<CountryCount>

    @Query(
        """
        SELECT SUBSTRING_INDEX(ec.expert_email, '@', -1) AS domain, COUNT(*) AS count
          FROM mail_record mr
          JOIN expert_contact ec ON mr.expert_contact_id = ec.id
         WHERE mr.direction = 'OUTBOUND'
           AND mr.mail_type = 'INTRODUCTION'
           AND mr.sent_at >= :from AND mr.sent_at < :to
         GROUP BY SUBSTRING_INDEX(ec.expert_email, '@', -1)
        """
    )
    fun aggregateIntroSentByDomain(from: LocalDateTime, to: LocalDateTime): List<DomainCount>

    @Query(
        """
        SELECT SUBSTRING_INDEX(ec.expert_email, '@', -1) AS domain,
               COUNT(DISTINCT mr.expert_contact_id) AS count
          FROM mail_record mr
          JOIN expert_contact ec ON mr.expert_contact_id = ec.id
         WHERE mr.direction = 'INBOUND'
           AND mr.received_at >= :from AND mr.received_at < :to
         GROUP BY SUBSTRING_INDEX(ec.expert_email, '@', -1)
        """
    )
    fun aggregateInboundByDomain(from: LocalDateTime, to: LocalDateTime): List<DomainCount>

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
          SELECT CONVERT('MAIL_RECORD' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS source,
                 mr.id AS id, mr.expert_contact_id,
                 CONVERT(mr.direction USING utf8mb4) COLLATE utf8mb4_unicode_ci AS direction,
                 CONVERT(mr.mail_type USING utf8mb4) COLLATE utf8mb4_unicode_ci AS mail_type,
                 CONVERT(mr.sender_account_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS sender_account_code,
                 CONVERT(mr.triggered_by USING utf8mb4) COLLATE utf8mb4_unicode_ci AS triggered_by,
                 mr.matched_qa_rule_id,
                 CONVERT(mr.send_status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS send_status,
                 CONVERT(mr.subject USING utf8mb4) COLLATE utf8mb4_unicode_ci AS subject,
                 CONVERT(SUBSTRING(COALESCE(mr.cleaned_body, mr.body), 1, 200) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS body_preview,
                 mr.sent_at, CAST(NULL AS DATETIME) AS received_at,
                 CONVERT(CAST(NULL AS CHAR) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS process_status,
                 CONVERT(CAST(NULL AS CHAR) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS reason_type,
                 CONVERT(ec.expert_email USING utf8mb4) COLLATE utf8mb4_unicode_ci AS expert_email,
                 CONVERT(ec.expert_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS expert_name,
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
          SELECT CONVERT('INBOUND_PROCESSING' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS source,
                 imp.id AS id, imp.expert_contact_id,
                 CONVERT('INBOUND' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS direction,
                 CONVERT('REPLY' USING utf8mb4) COLLATE utf8mb4_unicode_ci AS mail_type,
                 CONVERT(imp.sender_account_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS sender_account_code,
                 CONVERT(CAST(NULL AS CHAR) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS triggered_by,
                 CAST(NULL AS SIGNED) AS matched_qa_rule_id,
                 CONVERT(CAST(NULL AS CHAR) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS send_status,
                 CONVERT(imp.subject USING utf8mb4) COLLATE utf8mb4_unicode_ci AS subject,
                 CONVERT(SUBSTRING(COALESCE(imp.cleaned_body, imp.body), 1, 200) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS body_preview,
                 CAST(NULL AS DATETIME) AS sent_at, imp.received_at,
                 CONVERT(imp.process_status USING utf8mb4) COLLATE utf8mb4_unicode_ci AS process_status,
                 CONVERT(imp.reason_type USING utf8mb4) COLLATE utf8mb4_unicode_ci AS reason_type,
                 CONVERT(COALESCE(ec2.expert_email, imp.from_email) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS expert_email,
                 CONVERT(ec2.expert_name USING utf8mb4) COLLATE utf8mb4_unicode_ci AS expert_name,
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
