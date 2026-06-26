package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.MailRecordQaRule
import org.springframework.data.repository.CrudRepository

interface MailRecordQaRuleRepository : CrudRepository<MailRecordQaRule, Long> {
    fun findByMailRecordIdOrderByOrdinalAsc(mailRecordId: Long): List<MailRecordQaRule>
}
