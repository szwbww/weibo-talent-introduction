package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.EmailSuppression
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface EmailSuppressionRepository : CrudRepository<EmailSuppression, Long> {
    fun existsByEmail(email: String): Boolean

    fun findByEmail(email: String): EmailSuppression?

    @Modifying
    @Query("DELETE FROM email_suppression WHERE email = :email")
    fun deleteByEmail(email: String): Int

    @Query(
        """
        SELECT * FROM email_suppression
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun findAllOrderByCreatedAtDesc(limit: Int, offset: Int): List<EmailSuppression>

    @Query(
        """
        SELECT * FROM email_suppression
        WHERE email LIKE CONCAT('%', :keyword, '%')
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun findByEmailContainingOrderByCreatedAtDesc(keyword: String, limit: Int, offset: Int): List<EmailSuppression>

    @Query("SELECT COUNT(*) FROM email_suppression")
    fun countAll(): Long

    @Query("SELECT COUNT(*) FROM email_suppression WHERE email LIKE CONCAT('%', :keyword, '%')")
    fun countByEmailContaining(keyword: String): Long
}
