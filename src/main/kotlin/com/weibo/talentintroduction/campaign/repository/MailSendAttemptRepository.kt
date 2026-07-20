package com.weibo.talentintroduction.campaign.repository

import com.weibo.talentintroduction.campaign.domain.MailSendAttempt
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface MailSendAttemptRepository : CrudRepository<MailSendAttempt, Long> {
    fun findByOrcidIdAndMailType(orcidId: String, mailType: String): MailSendAttempt?

    fun findByMessageId(messageId: String): MailSendAttempt?

    @Query("SELECT * FROM mail_send_attempt WHERE orcid_id = :orcidId AND mail_type = :mailType FOR UPDATE")
    fun findByOrcidIdAndMailTypeForUpdate(orcidId: String, mailType: String): MailSendAttempt?

    @Modifying
    @Query(
        """
        INSERT IGNORE INTO mail_send_attempt
          (orcid_id, mail_type, account_code, message_id, status,
           recipient, subject, body, content_type, created_at, updated_at)
        VALUES
          (:orcidId, :mailType, :accountCode, :messageId, :status,
           :recipient, :subject, :body, :contentType, :createdAt, :updatedAt)
        """
    )
    fun insertIgnore(
        orcidId: String,
        mailType: String,
        accountCode: String,
        messageId: String,
        status: String,
        recipient: String,
        subject: String,
        body: String,
        contentType: String,
        createdAt: LocalDateTime,
        updatedAt: LocalDateTime
    ): Int

    @Modifying
    @Query(
        """
        UPDATE mail_send_attempt
           SET status = :newStatus,
               updated_at = COALESCE(:now, NOW())
         WHERE id = :id
           AND status = :expectedStatus
        """
    )
    fun claimStatus(id: Long, expectedStatus: String, newStatus: String, now: LocalDateTime? = null): Int

    @Modifying
    @Query(
        """
        UPDATE mail_send_attempt
           SET status = :status,
               updated_at = :now,
               error_summary = :errorSummary
         WHERE id = :id
        """
    )
    fun updateStatusAndError(id: Long, status: String, errorSummary: String?, now: LocalDateTime): Int
}
