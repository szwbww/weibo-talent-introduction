package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailInboxCursor
import com.weibo.talentintroduction.mail.repository.MailInboxCursorRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

class MailInboxCursorServiceTest {
    private val repository = Mockito.mock(MailInboxCursorRepository::class.java)
    private val service = MailInboxCursorService(repository)

    @Test
    fun `get returns zero cursor when row missing`() {
        Mockito.`when`(repository.findBySenderAccountCode("sender")).thenReturn(null)

        val state = service.get("sender")

        assertNull(state.uidValidity)
        assertEquals(0L, state.lastUid)
    }

    @Test
    fun `resolveStart returns zero when uid validity mismatches`() {
        val start = service.resolveStart(CursorState(uidValidity = 100L, lastUid = 5L), 200L)

        assertEquals(0L, start)
    }

    @Test
    fun `advance stops at gap before failed uid`() {
        val captor = ArgumentCaptor.forClass(MailInboxCursor::class.java)
        Mockito.`when`(repository.findBySenderAccountCode("sender")).thenReturn(null)

        service.advance(
            accountCode = "sender",
            currentUidValidity = 1L,
            fetchedUids = listOf(10L, 11L),
            handledUids = setOf(11L),
            oldStart = 0L
        )

        Mockito.verify(repository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `advance moves cursor to highest consecutive handled uid`() {
        val captor = ArgumentCaptor.forClass(MailInboxCursor::class.java)
        Mockito.`when`(repository.findBySenderAccountCode("sender")).thenReturn(null)

        service.advance(
            accountCode = "sender",
            currentUidValidity = 1L,
            fetchedUids = listOf(10L, 11L),
            handledUids = setOf(10L, 11L),
            oldStart = 0L
        )

        Mockito.verify(repository).save(captor.capture())
        assertEquals(11L, captor.value.lastUid)
        assertEquals(1L, captor.value.uidValidity)
    }

    @Test
    fun `computeConsecutiveHandledMax leaves cursor below failed head`() {
        assertNull(
            service.computeConsecutiveHandledMax(
                fetchedUids = listOf(10L, 11L),
                handledUids = setOf(11L),
                oldStart = 0L
            )
        )
    }

    @Test
    fun `computeConsecutiveHandledMax advances through consecutive successes`() {
        assertEquals(
            11L,
            service.computeConsecutiveHandledMax(
                fetchedUids = listOf(10L, 11L),
                handledUids = setOf(10L, 11L),
                oldStart = 0L
            )
        )
    }
}
