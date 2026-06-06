package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.springframework.data.repository.CrudRepository

interface MailSenderAccountRepository : CrudRepository<MailSenderAccount, Long> {
    fun findByAccountCode(accountCode: String): MailSenderAccount?

    fun findByAccountCodeAndEnabledTrue(accountCode: String): MailSenderAccount?

    fun findAllByEnabledTrue(): List<MailSenderAccount>

    fun findAllByEnabledTrueAndAccountCodeNot(accountCode: String): List<MailSenderAccount>

    fun findAllByOrderByAccountCodeAsc(): List<MailSenderAccount>

    fun existsByAccountCode(accountCode: String): Boolean
}
