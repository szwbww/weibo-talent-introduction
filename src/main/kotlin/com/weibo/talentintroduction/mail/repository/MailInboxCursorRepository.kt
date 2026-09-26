package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.MailInboxCursor
import org.springframework.data.repository.CrudRepository
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query

interface MailInboxCursorRepository : CrudRepository<MailInboxCursor, Long> {
    fun findBySenderAccountCode(senderAccountCode: String): MailInboxCursor?

    @Modifying
    @Query("""
        INSERT INTO mail_inbox_cursor (sender_account_code, uid_validity, last_uid, updated_at)
        VALUES (:accountCode, :uidValidity, :lastUid, CURRENT_TIMESTAMP)
        ON DUPLICATE KEY UPDATE sender_account_code = sender_account_code
    """)
    fun initializeIfAbsent(accountCode: String, uidValidity: Long, lastUid: Long): Int
}
