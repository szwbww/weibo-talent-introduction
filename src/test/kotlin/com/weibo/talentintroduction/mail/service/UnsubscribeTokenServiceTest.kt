package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.UnsubscribeProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class UnsubscribeTokenServiceTest {
    private val properties = UnsubscribeProperties(
        baseUrl = "https://outreach.example.com",
        secret = "test-secret-key"
    )
    private val service = UnsubscribeTokenService(properties)

    @Test
    fun `sign and verify round-trip returns normalized email`() {
        val token = service.sign("  User@Example.COM ")

        assertEquals("user@example.com", service.verify(token))
    }

    @Test
    fun `sign produces same normalized token for different casing`() {
        assertEquals(service.sign("A@X.com"), service.sign("a@x.com "))
    }

    @Test
    fun `verify rejects tampered signature segment`() {
        val token = service.sign("user@example.com")
        val parts = token.split(".")
        val tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString("bad".toByteArray())

        assertNull(service.verify(tampered))
    }

    @Test
    fun `verify rejects tampered email segment`() {
        val token = service.sign("user@example.com")
        val parts = token.split(".")
        val tamperedEmail = Base64.getUrlEncoder().withoutPadding().encodeToString("other@example.com".toByteArray())
        val tampered = tamperedEmail + "." + parts[1]

        assertNull(service.verify(tampered))
    }

    @Test
    fun `verify rejects malformed token`() {
        assertNull(service.verify("not-a-token"))
        assertNull(service.verify("only-one-part"))
    }

    @Test
    fun `verify rejects illegal base64 segments without throwing`() {
        assertNull(service.verify("%%%.abc"))
        assertNull(service.verify("中文.x"))
    }

    @Test
    fun `enabled requires base url and secret`() {
        assertTrue(service.enabled())
        assertFalse(UnsubscribeTokenService(UnsubscribeProperties(baseUrl = "", secret = "x")).enabled())
        assertFalse(UnsubscribeTokenService(UnsubscribeProperties(baseUrl = "https://x.com", secret = "")).enabled())
    }

    @Test
    fun `unsubscribeUrl uses configured base url`() {
        val url = service.unsubscribeUrl("user@example.com")

        assertTrue(url.startsWith("https://outreach.example.com/u/unsubscribe?token="))
        val token = url.substringAfter("token=")
        assertNotNull(service.verify(token))
    }
}
