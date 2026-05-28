package com.weibo.talentintroduction.template.service

import com.weibo.talentintroduction.template.domain.MailTemplate
import com.weibo.talentintroduction.template.repository.MailTemplateRepository
import org.springframework.stereotype.Service

@Service
class MailTemplateService(
    private val mailTemplateRepository: MailTemplateRepository
) {
    fun getEnabledTemplate(templateCode: String): MailTemplate =
        mailTemplateRepository.findByTemplateCodeAndEnabledTrue(templateCode)
            ?: error("Enabled mail template not found: $templateCode")

    fun render(templateCode: String, variables: Map<String, String>): RenderedMailTemplate {
        val template = getEnabledTemplate(templateCode)
        return RenderedMailTemplate(
            subject = template.subject?.let { renderText(it, variables) },
            body = renderText(template.body, variables)
        )
    }

    private fun renderText(text: String, variables: Map<String, String>): String =
        variables.entries.fold(text) { rendered, (key, value) ->
            rendered.replace("\${$key}", value)
        }
}

data class RenderedMailTemplate(
    val subject: String?,
    val body: String
)
