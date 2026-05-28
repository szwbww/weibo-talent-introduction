package com.weibo.talentintroduction.template.service

import com.weibo.talentintroduction.template.domain.MailTemplate
import com.weibo.talentintroduction.template.repository.MailTemplateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class MailTemplateServiceTest {
    private val repository = Mockito.mock(MailTemplateRepository::class.java)
    private val service = MailTemplateService(repository)

    @Test
    fun `renders placeholders in template body`() {
        Mockito.`when`(repository.findByTemplateCodeAndEnabledTrue("INTRODUCTION"))
            .thenReturn(
                MailTemplate(
                    templateCode = "INTRODUCTION",
                    templateName = "Introduction",
                    subject = "Hello from \${senderName}",
                    body = "Contact me at \${senderEmail}"
                )
            )

        val rendered = service.render(
            "INTRODUCTION",
            mapOf(
                "senderName" to "Zoe",
                "senderEmail" to "zoe@example.com"
            )
        )

        assertEquals("Hello from Zoe", rendered.subject)
        assertEquals("Contact me at zoe@example.com", rendered.body)
    }
}
