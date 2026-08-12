package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.UnsubscribeProperties
import com.weibo.talentintroduction.mail.domain.UnsubscribeToken
import com.weibo.talentintroduction.mail.repository.UnsubscribeTokenRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.dao.DuplicateKeyException
import java.util.Base64

class UnsubscribeTokenServiceTest {
    private val properties = UnsubscribeProperties(
        baseUrl = "https://outreach.example.com",
        secret = "test-secret-key"
    )
    private val service = UnsubscribeTokenService(properties)
    private val repository = Mockito.mock(UnsubscribeTokenRepository::class.java)
    private val saved = mutableListOf<UnsubscribeToken>()
    private val repoService = UnsubscribeTokenService(properties, repository)

    @BeforeEach
    fun setUp() {
        Mockito.reset(repository)
        saved.clear()
        Mockito.`when`(repository.save(Mockito.any(UnsubscribeToken::class.java))).thenAnswer { invocation ->
            val token = invocation.getArgument<UnsubscribeToken>(0)
            saved.add(token)
            token
        }
        Mockito.`when`(repository.findByToken(Mockito.anyString())).thenAnswer { invocation ->
            saved.firstOrNull { it.token == invocation.getArgument<String>(0) }
        }
        Mockito.`when`(repository.findByEmail(Mockito.anyString())).thenAnswer { invocation ->
            saved.firstOrNull { it.email == invocation.getArgument<String>(0) }
        }
    }

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

    @Test
    fun `sign produces opaque 43-char token decoding to 32 random bytes`() {
        val token = repoService.sign("user@example.com")

        assertEquals(43, token.length)
        assertTrue(token.all { it.isLetterOrDigit() || it == '-' || it == '_' }, "token must use base64url alphabet")
        assertEquals(32, Base64.getUrlDecoder().decode(token).size)
    }

    @Test
    fun `sign token carries no dot and decodes without email address`() {
        val token = repoService.sign("user@example.com")

        assertFalse(token.contains("."), "token must not contain a dot")
        val decoded = Base64.getUrlDecoder().decode(token)
        val emailBytes = "user@example.com".toByteArray(Charsets.UTF_8)
        assertFalse(
            decoded.toList().windowed(emailBytes.size).any { it == emailBytes.toList() },
            "token must not encode the recipient email"
        )
    }

    @Test
    fun `sign is idempotent for same email and saves once`() {
        val first = repoService.sign("a@x.com")
        val second = repoService.sign("a@x.com")

        assertEquals(first, second)
        Mockito.verify(repository, Mockito.times(1)).save(Mockito.any(UnsubscribeToken::class.java))
    }

    @Test
    fun `sign normalizes casing and whitespace before reuse`() {
        assertEquals(repoService.sign("A@X.com"), repoService.sign("a@x.com "))
    }

    @Test
    fun `verify returns normalized email for new-format token`() {
        val token = repoService.sign("  User@Example.COM ")

        assertEquals("user@example.com", repoService.verify(token))
    }

    @Test
    fun `verify falls back to legacy hmac channel when token is not in table`() {
        val legacyService = UnsubscribeTokenService(properties)
        val legacyToken = legacyService.sign("legacy@example.com")

        assertEquals("legacy@example.com", repoService.verify(legacyToken))
    }

    @Test
    fun `verify returns null for unknown token without throwing`() {
        assertNull(repoService.verify("unknown-random"))
    }

    @Test
    fun `sign reuses existing token when concurrent insert hits duplicate key`() {
        val existing = UnsubscribeToken(email = "a@x.com", token = "stored-token-abc")
        Mockito.`when`(repository.findByEmail("a@x.com")).thenReturn(null, existing)
        Mockito.`when`(repository.save(Mockito.any(UnsubscribeToken::class.java)))
            .thenThrow(DuplicateKeyException("duplicate key"))

        assertEquals("stored-token-abc", repoService.sign("a@x.com"))
    }

    @Test
    fun `enabled splits semantics by repository presence`() {
        val noSecret = UnsubscribeProperties(baseUrl = "https://outreach.example.com", secret = "")

        assertTrue(UnsubscribeTokenService(noSecret, repository).enabled())
        assertFalse(UnsubscribeTokenService(noSecret).enabled())
    }

    @Test
    fun `verify legacy token returns null when secret blank with repository present`() {
        val noSecretRepoService = UnsubscribeTokenService(
            UnsubscribeProperties(baseUrl = "https://outreach.example.com", secret = ""),
            repository
        )

        assertNull(noSecretRepoService.verify("legacy-token.with.dot"))
    }
}
