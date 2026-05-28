package com.weibo.talentintroduction.template.repository

import com.weibo.talentintroduction.template.domain.MailTemplate
import org.springframework.data.repository.CrudRepository

interface MailTemplateRepository : CrudRepository<MailTemplate, Long> {
    fun findByTemplateCodeAndEnabledTrue(templateCode: String): MailTemplate?

    fun findAllByEnabledTrueOrderByTemplateCodeAsc(): List<MailTemplate>
}
