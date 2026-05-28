package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MailBodyCleanerTest {
    private val cleaner = MailBodyCleaner()

    @Test
    fun `keeps latest reply and removes quoted history`() {
        val body = """
            Hi Zoe,

            I am interested in this program.

            Best regards,
            Professor Smith

            On Tue, May 26, 2026 at 10:00 Zoe wrote:
            > Dear Professor,
            > Please share your CV.
        """.trimIndent()

        assertEquals(
            "Hi Zoe,\n\nI am interested in this program.",
            cleaner.clean(body)
        )
    }

    @Test
    fun `removes common confidentiality disclaimer`() {
        val body = """
            Please tell me more about the process.

            Confidentiality Notice: this email and attachments are intended only for the recipient.
        """.trimIndent()

        assertEquals(
            "Please tell me more about the process.",
            cleaner.clean(body)
        )
    }
}
