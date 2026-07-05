package com.weibo.talentintroduction.template.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query

class MailComposeTemplateBlockRepositoryTest {
    @Test
    fun `deleteAllByTemplateId is an explicit modifying delete query`() {
        val method = MailComposeTemplateBlockRepository::class.java.getMethod(
            "deleteAllByTemplateId",
            java.lang.Long.TYPE
        )

        assertNotNull(method.getAnnotation(Modifying::class.java))
        assertEquals(
            "DELETE FROM mail_compose_template_block WHERE template_id = :templateId",
            method.getAnnotation(Query::class.java)?.value
        )
        assertEquals(Int::class.javaPrimitiveType, method.returnType)
    }
}
