package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.repository.MailOpenTrackingRepository
import com.weibo.talentintroduction.mail.repository.OpenTrackingFilter
import com.weibo.talentintroduction.mail.repository.OpenTrackingReservation
import com.weibo.talentintroduction.mail.repository.OpenTrackingSnapshot
import com.weibo.talentintroduction.mail.repository.OpenTrackingSummary
import com.weibo.talentintroduction.monitoring.service.MonitoringDateRangeResolver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import java.time.LocalDate
import java.time.LocalDateTime

class MailOpenTrackingServiceTest {
    private val repository = mock(MailOpenTrackingRepository::class.java)
    private val day = LocalDate.of(2026,9,25)
    private val dummyFilter = OpenTrackingFilter(day.atStartOfDay(), day.plusDays(1).atStartOfDay(), null, "ALL", null, 20, 0)
    private fun service(base: String = "https://example.test/talent/") =
        MailOpenTrackingService(repository, MonitoringDateRangeResolver(), base)

    @Test fun `disabled by missing or invalid setting and unsafe base URL cannot be enabled`() {
        assertFalse(service().settings().enabled)
        for (base in listOf("", "http://example.test", "https://user@example.test", "https://example.test/?x=1", "https://example.test/#x", "not a URL")) {
            val subject = service(base)
            assertFalse(subject.settings().configured)
            assertThrows(IllegalArgumentException::class.java) { subject.setEnabled(true) }
            subject.setEnabled(false)
        }
        verify(repository, times(6)).setEnabled(eq(false), any(LocalDateTime::class.java) ?: LocalDateTime.MIN)
        verify(repository, never()).setEnabled(eq(true), any(LocalDateTime::class.java) ?: LocalDateTime.MIN)
    }

    @Test fun `reservation accepts one recipient and returns opaque URL beneath deployment path`() {
        `when`(repository.reserve(anyString() ?: "", anyString() ?: "", any(LocalDateTime::class.java) ?: LocalDateTime.MIN))
            .thenAnswer { call -> OpenTrackingReservation(123L, call.getArgument(0)) }
        val reserved = service().reserve("expert@example.test")!!
        assertEquals(123L, reserved.id)
        assertTrue(reserved.token.matches(Regex("[A-Za-z0-9_-]{43}")))
        assertEquals("https://example.test/talent/t/mail-open/${reserved.token}.gif", reserved.url)
        assertFalse(reserved.url.contains("expert"))
        for (invalid in listOf("one@example.test,two@example.test", "a@example.test\nb@example.test", "x".repeat(256), " Name <a@example.test>")) {
            assertNull(service().reserve(invalid))
        }
        verify(repository, times(1)).reserve(anyString() ?: "", anyString() ?: "", any(LocalDateTime::class.java) ?: LocalDateTime.MIN)
    }

    @Test fun `query rejects unsupported filters before accessing storage and carries half-open Shanghai days`() {
        val subject = service()
        assertThrows(IllegalArgumentException::class.java) { subject.readPage(day.plusDays(1),day,null,"ALL",null,20,0) }
        assertThrows(IllegalArgumentException::class.java) { subject.readPage(day,day,null,"OPENED' OR 1=1",null,20,0) }
        assertThrows(IllegalArgumentException::class.java) { subject.readPage(day,day,null,"ALL",null,101,0) }
        assertThrows(IllegalArgumentException::class.java) { subject.readPage(day,day,null,"ALL",null,20,-1) }
        assertThrows(IllegalArgumentException::class.java) { subject.readPage(day,day,null,"ALL","x".repeat(201),20,0) }
        val empty = OpenTrackingSnapshot(emptyList(),0,OpenTrackingSummary(0,0,null))
        `when`(repository.readPage(any(OpenTrackingFilter::class.java) ?: dummyFilter)).thenReturn(empty)
        assertEquals(empty, subject.readPage(day,day.plusDays(1),"sender","NO_SIGNAL","100%_",20,0))
        val filter = org.mockito.ArgumentCaptor.forClass(OpenTrackingFilter::class.java)
        verify(repository).readPage(filter.capture() ?: dummyFilter)
        assertEquals(day.atStartOfDay(), filter.value.start)
        assertEquals(day.plusDays(2).atStartOfDay(), filter.value.end)
        assertEquals("100%_", filter.value.keyword)
    }

    @Test fun `malformed token never reaches storage`() {
        service().recordSignal("bad")
        verify(repository, never()).recordSignal(anyString() ?: "", any(LocalDateTime::class.java) ?: LocalDateTime.MIN)
    }
}
