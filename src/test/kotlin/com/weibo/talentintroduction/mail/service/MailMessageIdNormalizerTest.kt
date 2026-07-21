package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MailMessageIdNormalizerTest {
    @Test
    fun `normalizes brackets and whitespace`() {
        assertEquals("id@example.com", MailMessageIdNormalizer.normalize("  < id@example.com >  "))
    }

    @Test
    fun `normalizes null and blank to empty`() {
        assertEquals("", MailMessageIdNormalizer.normalize(null))
        assertEquals("", MailMessageIdNormalizer.normalize("  <>  "))
    }
}
