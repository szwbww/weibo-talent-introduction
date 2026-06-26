package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SelfCheckProbeDetectorTest {
    private val detector = SelfCheckProbeDetector()
    private val accountEmail = "sender@qftechtalent.com"

    @Test
    fun `self-check subject from account email is probe`() {
        assertTrue(
            detector.isSelfCheckProbe(
                from = "sender@qftechtalent.com",
                subject = "[self-check] sender 1234567890",
                accountEmail = accountEmail
            )
        )
    }

    @Test
    fun `self-check subject with spaces in tag is probe`() {
        assertTrue(
            detector.isSelfCheckProbe(
                from = "sender@qftechtalent.com",
                subject = "[ self - check ] sender 123",
                accountEmail = accountEmail
            )
        )
    }

    @Test
    fun `Re prefix self-check subject is not probe`() {
        assertFalse(
            detector.isSelfCheckProbe(
                from = "sender@qftechtalent.com",
                subject = "Re: [self-check] sender 123",
                accountEmail = accountEmail
            )
        )
    }

    @Test
    fun `self-check subject from different email is not probe`() {
        assertFalse(
            detector.isSelfCheckProbe(
                from = "other@example.com",
                subject = "[self-check] sender 123",
                accountEmail = accountEmail
            )
        )
    }
}
