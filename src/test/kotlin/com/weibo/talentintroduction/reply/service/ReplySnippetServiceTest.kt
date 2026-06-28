package com.weibo.talentintroduction.reply.service

import com.weibo.talentintroduction.reply.domain.ReplySnippet
import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.util.Optional

class ReplySnippetServiceTest {
    private val repository = Mockito.mock(ReplySnippetRepository::class.java)
    private val service = ReplySnippetService(repository)

    @Test
    fun `setDefault clears previous default of same type`() {
        val oldDefault = snippet(id = 1L, snippetType = SnippetType.SALUTATION.name, isDefault = true)
        val target = snippet(id = 2L, snippetType = SnippetType.SALUTATION.name, isDefault = false, enabled = true)

        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(target))
        Mockito.`when`(repository.findBySnippetTypeAndIsDefaultTrue(SnippetType.SALUTATION.name))
            .thenReturn(listOf(oldDefault, target))
        Mockito.`when`(repository.save(Mockito.any(ReplySnippet::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as ReplySnippet }

        val result = service.setDefault(2L)

        assertTrue(result.isDefault)

        val captor = ArgumentCaptor.forClass(ReplySnippet::class.java)
        Mockito.verify(repository, Mockito.times(2)).save(captor.capture())
        val cleared = captor.allValues.first { it.id == 1L }
        assertEquals(false, cleared.isDefault)
    }

    @Test
    fun `resolveManualFrame omits type without enabled default`() {
        Mockito.`when`(repository.findBySnippetTypeAndEnabledTrueAndIsDefaultTrue(SnippetType.SALUTATION.name))
            .thenReturn(listOf(snippet(id = 1L, snippetType = SnippetType.SALUTATION.name, isDefault = true)))
        Mockito.`when`(repository.findBySnippetTypeAndEnabledTrueAndIsDefaultTrue(SnippetType.GREETING.name))
            .thenReturn(emptyList())
        Mockito.`when`(repository.findBySnippetTypeAndEnabledTrueAndIsDefaultTrue(SnippetType.CLOSING.name))
            .thenReturn(listOf(snippet(id = 3L, snippetType = SnippetType.CLOSING.name, isDefault = true, content = "Closing")))
        Mockito.`when`(repository.findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(SnippetType.ACK.name))
            .thenReturn(emptyList())

        val frame = service.resolveManualFrame()

        assertEquals("Dear Professor,", frame.salutation)
        assertNull(frame.greeting)
        assertEquals("Closing", frame.closing)
        assertTrue(frame.ackOptions.isEmpty())
    }

    @Test
    fun `resolveAck returns null for missing disabled or non-ack snippet`() {
        assertNull(service.resolveAck(null))
        assertNull(service.resolveAck(99L))

        val disabled = snippet(id = 5L, snippetType = SnippetType.ACK.name, enabled = false, content = "Thanks")
        Mockito.`when`(repository.findById(5L)).thenReturn(Optional.of(disabled))
        assertNull(service.resolveAck(5L))

        val greeting = snippet(id = 6L, snippetType = SnippetType.GREETING.name, enabled = true, content = "Hi")
        Mockito.`when`(repository.findById(6L)).thenReturn(Optional.of(greeting))
        assertNull(service.resolveAck(6L))
    }

    @Test
    fun `resolveAck returns enabled ack content`() {
        val ack = snippet(id = 7L, snippetType = SnippetType.ACK.name, enabled = true, content = "Thank you for sharing your CV.")
        Mockito.`when`(repository.findById(7L)).thenReturn(Optional.of(ack))

        assertEquals("Thank you for sharing your CV.", service.resolveAck(7L))
    }

    private fun snippet(
        id: Long,
        snippetType: String,
        isDefault: Boolean = false,
        enabled: Boolean = true,
        content: String = "Dear Professor,"
    ): ReplySnippet =
        ReplySnippet(
            id = id,
            snippetType = snippetType,
            content = content,
            displayOrder = 10,
            isDefault = isDefault,
            enabled = enabled
        )
}
