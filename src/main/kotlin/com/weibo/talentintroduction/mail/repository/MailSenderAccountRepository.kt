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

    fun findAllByAccountCodeNot(accountCode: String): List<MailSenderAccount>

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

    @Modifying
    @Query(
        """
        UPDATE mail_sender_account
           SET auto_send_paused = 1,
               auto_send_paused_reason = :reason,
               auto_send_paused_at = :pausedAt
         WHERE account_code = :accountCode
        """
    )
    fun pauseAutoSend(accountCode: String, reason: String, pausedAt: java.time.LocalDateTime): Int

    @Modifying
    @Query(
        """
        UPDATE mail_sender_account
           SET auto_send_paused = 0,
               auto_send_paused_reason = NULL,
               auto_send_paused_at = NULL
         WHERE account_code = :accountCode
        """
    )
    fun resumeAutoSend(accountCode: String): Int

    /**
     * 重置所有 enabled 且 last_sent_at 在今天之前的账号的 todaySentCount。
     * L4-1 幂等：只重置 last_sent_at < :todayStart 的行，当天已重置过的不受影响。
     */
    @Modifying
    @Query(
        """
        UPDATE mail_sender_account
           SET today_sent_count = 0
         WHERE enabled = 1
           AND last_sent_at IS NOT NULL
           AND last_sent_at < :todayStart
        """
    )
    fun resetDailyCountsBeforeDate(todayStart: java.time.LocalDateTime): Int

    /**
     * 解除因每日限额耗尽而暂停的账号（L4-3）。
     * 只解除 reason 以 DAILY_LIMIT 开头的暂停，不影响 SELF_CHECK_FAILED 等其他原因的暂停。
     */
    @Modifying
    @Query(
        """
        UPDATE mail_sender_account
           SET auto_send_paused = 0,
               auto_send_paused_reason = NULL,
               auto_send_paused_at = NULL
         WHERE enabled = 1
           AND auto_send_paused = 1
           AND auto_send_paused_reason LIKE 'DAILY_LIMIT%'
        """
    )
    fun resumeDailyLimitPausedAccounts(): Int
}
