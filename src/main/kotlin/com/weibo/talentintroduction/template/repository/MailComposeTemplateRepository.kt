package com.weibo.talentintroduction.template.repository

import com.weibo.talentintroduction.template.domain.MailComposeTemplate
import com.weibo.talentintroduction.template.domain.MailComposeTemplateBlock
import org.springframework.data.repository.CrudRepository

interface MailComposeTemplateRepository : CrudRepository<MailComposeTemplate, Long> {
    fun findAllByOrderByIdAsc(): List<MailComposeTemplate>

    fun findAllByEnabledTrueOrderByIdAsc(): List<MailComposeTemplate>

    fun findByTemplateCodeAndEnabledTrue(templateCode: String): MailComposeTemplate?
}

interface MailComposeTemplateBlockRepository : CrudRepository<MailComposeTemplateBlock, Long> {
    fun findAllByTemplateIdOrderByBlockOrderAsc(templateId: Long): List<MailComposeTemplateBlock>

    fun deleteAllByTemplateId(templateId: Long)
}
