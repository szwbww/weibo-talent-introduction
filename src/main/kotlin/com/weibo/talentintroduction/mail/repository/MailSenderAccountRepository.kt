package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface MailSenderAccountRepository : CrudRepository<MailSenderAccount, Long> {
    fun findByAccountCode(accountCode: String): MailSenderAccount?

    fun findByAccountCodeAndEnabledTrue(accountCode: String): MailSenderAccount?

    fun findAllByEnabledTrue(): List<MailSenderAccount>

    fun findAllByEnabledTrueAndAccountCodeNot(accountCode: String): List<MailSenderAccount>

    fun findAllByOrderByAccountCodeAsc(): List<MailSenderAccount>

    fun existsByAccountCode(accountCode: String): Boolean

    @Modifying
    @Query(
        """
        UPDATE mail_sender_account
           SET today_sent_count = today_sent_count + 1,
               last_sent_at = :sentAt
         WHERE account_code = :accountCode
        """
    )
    fun incrementTodaySentCount(accountCode: String, sentAt: java.time.LocalDateTime): Int
}
