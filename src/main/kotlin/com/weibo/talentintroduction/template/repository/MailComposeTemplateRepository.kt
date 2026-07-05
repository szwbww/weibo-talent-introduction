package com.weibo.talentintroduction.template.repository

import com.weibo.talentintroduction.template.domain.MailComposeTemplate
import com.weibo.talentintroduction.template.domain.MailComposeTemplateBlock
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface MailComposeTemplateRepository : CrudRepository<MailComposeTemplate, Long> {
    fun findAllByOrderByIdAsc(): List<MailComposeTemplate>

    fun findAllByEnabledTrueOrderByIdAsc(): List<MailComposeTemplate>

    fun findByTemplateCodeAndEnabledTrue(templateCode: String): MailComposeTemplate?
}

interface MailComposeTemplateBlockRepository : CrudRepository<MailComposeTemplateBlock, Long> {
    fun findAllByTemplateIdOrderByBlockOrderAsc(templateId: Long): List<MailComposeTemplateBlock>

    @Modifying
    @Query("DELETE FROM mail_compose_template_block WHERE template_id = :templateId")
    fun deleteAllByTemplateId(templateId: Long): Int
}
