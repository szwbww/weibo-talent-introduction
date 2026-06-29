package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.MailInboxCursor
import org.springframework.data.repository.CrudRepository

interface MailInboxCursorRepository : CrudRepository<MailInboxCursor, Long> {
    fun findBySenderAccountCode(senderAccountCode: String): MailInboxCursor?
}
