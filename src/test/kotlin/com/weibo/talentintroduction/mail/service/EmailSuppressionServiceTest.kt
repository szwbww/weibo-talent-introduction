package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.EmailSuppression
import com.weibo.talentintroduction.mail.repository.EmailSuppressionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.ArgumentMatchers.anyString
import org.springframework.dao.DuplicateKeyException

class EmailSuppressionServiceTest {
    private val repository = Mockito.mock(EmailSuppressionRepository::class.java)
    private val service = EmailSuppressionService(repository)

    @BeforeEach
    fun setUp() {
        Mockito.reset(repository)
    }

    @Test
    fun `normalize trims and lowercases email`() {
        assertEquals("a@x.com", service.normalize("  A@X.com "))
    }

    @Test
    fun `suppress is idempotent for same email`() {
        Mockito.`when`(repository.existsByEmail("a@x.com")).thenReturn(false, true)
        Mockito.`when`(repository.save(Mockito.any(EmailSuppression::class.java))).thenAnswer { invocation ->
            invocation.getArgument<EmailSuppression>(0).copy(id = 1L)
        }

        assertTrue(service.suppress("A@X.com", SuppressionSource.INBOUND_REPLY, "test"))
        assertFalse(service.suppress("a@x.com ", SuppressionSource.INBOUND_REPLY, "test"))

        Mockito.verify(repository, Mockito.times(1)).save(Mockito.any(EmailSuppression::class.java))
    }

    @Test
    fun `isSuppressed matches normalized email`() {
        Mockito.`when`(repository.existsByEmail("a@x.com")).thenReturn(true)

        assertTrue(service.isSuppressed("  A@X.com "))
        Mockito.verify(repository).existsByEmail("a@x.com")
    }

    @Test
    fun `suppress handles concurrent duplicate key`() {
        Mockito.`when`(repository.existsByEmail("a@x.com")).thenReturn(false)
        Mockito.`when`(repository.save(Mockito.any(EmailSuppression::class.java)))
            .thenThrow(DuplicateKeyException("duplicate"))

        assertFalse(service.suppress("a@x.com", SuppressionSource.INBOUND_REPLY, "test"))
    }

    @Test
    fun `suppress stores source and truncated reason`() {
        Mockito.`when`(repository.existsByEmail("a@x.com")).thenReturn(false)
        Mockito.`when`(repository.save(Mockito.any(EmailSuppression::class.java))).thenAnswer { invocation ->
            invocation.getArgument<EmailSuppression>(0).copy(id = 1L)
        }

        val longReason = "x".repeat(600)
        service.suppress("a@x.com", SuppressionSource.INBOUND_REPLY, longReason)

        val captor = ArgumentCaptor.forClass(EmailSuppression::class.java)
        Mockito.verify(repository).save(captureValue(captor, EmailSuppression(email = "", source = "", reason = null)))
        assertEquals(SuppressionSource.INBOUND_REPLY.name, captor.value.source)
        assertEquals(500, captor.value.reason?.length)
    }

    @Test
    fun `looksLikeUnsubscribe detects unsubscribe phrases`() {
        assertTrue(service.looksLikeUnsubscribe("please unsubscribe me"))
        assertTrue(service.looksLikeUnsubscribe("请退订"))
        assertFalse(service.looksLikeUnsubscribe("I'm not available"))
        assertFalse(service.looksLikeUnsubscribe(null))
    }

    @Test
    fun `remove is idempotent and normalizes email`() {
        Mockito.`when`(repository.deleteByEmail("a@x.com")).thenReturn(0, 1)

        assertFalse(service.remove("  A@X.com "))
        assertTrue(service.remove("a@x.com"))

        Mockito.verify(repository, Mockito.times(2)).deleteByEmail("a@x.com")
    }

    @Test
    fun `remove returns false for blank email without repository call`() {
        assertFalse(service.remove("   "))
        Mockito.verify(repository, Mockito.never()).deleteByEmail(anyString())
    }

    @Test
    fun `list paginates without keyword`() {
        val row = EmailSuppression(id = 1L, email = "a@x.com", source = "MANUAL", reason = null)
        Mockito.`when`(repository.findAllOrderByCreatedAtDesc(50, 0)).thenReturn(listOf(row))
        Mockito.`when`(repository.countAll()).thenReturn(1L)

        val page = service.list(null, 0, 50)

        assertEquals(1, page.items.size)
        assertEquals(0, page.page)
        assertEquals(50, page.size)
        assertEquals(1L, page.total)
    }

    @Test
    fun `list normalizes keyword for search`() {
        Mockito.`when`(repository.findByEmailContainingOrderByCreatedAtDesc("foo", 20, 40)).thenReturn(emptyList())
        Mockito.`when`(repository.countByEmailContaining("foo")).thenReturn(0L)

        service.list("  FOO ", 2, 20)

        Mockito.verify(repository).findByEmailContainingOrderByCreatedAtDesc("foo", 20, 40)
        Mockito.verify(repository).countByEmailContaining("foo")
    }

    @Test
    fun `list caps page size`() {
        Mockito.`when`(repository.findAllOrderByCreatedAtDesc(100, 0)).thenReturn(emptyList())
        Mockito.`when`(repository.countAll()).thenReturn(0L)

        val page = service.list(null, 0, 500)

        assertEquals(100, page.size)
        Mockito.verify(repository).findAllOrderByCreatedAtDesc(100, 0)
    }

    private fun <T> captureValue(captor: ArgumentCaptor<T>, defaultValue: T): T {
        captor.capture()
        return defaultValue
    }
}
