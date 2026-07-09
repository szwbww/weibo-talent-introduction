package com.weibo.talentintroduction.reply.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.domain.ReplySnippet
import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.variant.domain.ContentVariant
import com.weibo.talentintroduction.variant.domain.ContentVariantOwnerType
import com.weibo.talentintroduction.variant.repository.ContentVariantRepository
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

class ReplySnippetServiceTest {
    private val repository = Mockito.mock(ReplySnippetRepository::class.java)
    private val contentVariantRepository = Mockito.mock(ContentVariantRepository::class.java)
    private val mailVariableService = MailVariableService(
        Mockito.mock(ExpertSearchService::class.java),
        MailComposeTemplateService(
            Mockito.mock(MailComposeTemplateRepository::class.java),
            Mockito.mock(MailComposeTemplateBlockRepository::class.java),
            Mockito.mock(QaRuleRepository::class.java),
            Mockito.mock(ReplySnippetRepository::class.java),
            ObjectMapper(),
            Mockito.mock(MailVariableService::class.java),
            Mockito.mock(ExpertContactRepository::class.java),
            Mockito.mock(MailSenderAccountService::class.java),
            ContentVariantService(contentVariantRepository, Mockito.mock(MailVariableService::class.java))
        )
    )
    private val contentVariantService = ContentVariantService(contentVariantRepository, mailVariableService)
    private val service = ReplySnippetService(repository, mailVariableService, contentVariantService)
    private val variantIdSeq = AtomicLong(1)

    @Test
    fun `create assigns timestamps before saving`() {
        Mockito.`when`(repository.save(Mockito.any(ReplySnippet::class.java)))
            .thenAnswer { invocation -> (invocation.arguments[0] as ReplySnippet).copy(id = 1L) }
        stubVariantPersistence()

        service.create(
            ReplySnippetCreateCommand(
                snippetType = SnippetType.ACK.name,
                content = "Thanks",
                displayOrder = 100,
                isDefault = false,
                enabled = true
            )
        )

        val captor = ArgumentCaptor.forClass(ReplySnippet::class.java)
        Mockito.verify(repository).save(captor.capture())
        assertNotNull(captor.value.createdAt)
        assertNotNull(captor.value.updatedAt)
    }

    @Test
    fun `create persists variants and delete cascades them`() {
        val snippet = snippet(id = 5L, snippetType = SnippetType.GREETING.name, content = "Hello")
        Mockito.`when`(repository.findById(5L)).thenReturn(Optional.of(snippet))
        Mockito.`when`(repository.save(Mockito.any(ReplySnippet::class.java)))
            .thenAnswer { invocation -> (invocation.arguments[0] as ReplySnippet).copy(id = 5L) }
        stubVariantPersistence()

        val created = service.create(
            ReplySnippetCreateCommand(
                snippetType = SnippetType.GREETING.name,
                content = "Hello",
                variants = listOf("Hi there")
            )
        )

        assertEquals(listOf("Hi there"), created.variants)

        service.delete(5L)

        Mockito.verify(contentVariantRepository, Mockito.times(2))
            .deleteByOwnerTypeAndOwnerId(ContentVariantOwnerType.REPLY_SNIPPET, 5L)
        Mockito.verify(repository).deleteById(5L)
    }

    @Test
    fun `update with empty variants clears stored variants`() {
        val existing = snippet(id = 5L, snippetType = SnippetType.GREETING.name, content = "Hello")
        Mockito.`when`(repository.findById(5L)).thenReturn(Optional.of(existing))
        Mockito.`when`(repository.save(Mockito.any(ReplySnippet::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as ReplySnippet }
        stubVariantPersistence()

        val updated = service.update(
            5L,
            ReplySnippetUpdateCommand(
                content = "Hello",
                displayOrder = 10,
                isDefault = false,
                enabled = true,
                variants = emptyList()
            )
        )

        assertTrue(updated.variants.isEmpty())
        Mockito.verify(contentVariantRepository).deleteByOwnerTypeAndOwnerId(ContentVariantOwnerType.REPLY_SNIPPET, 5L)
    }

    @Test
    fun `setDefault clears previous default of same type`() {
        val oldDefault = snippet(id = 1L, snippetType = SnippetType.SALUTATION.name, isDefault = true)
        val target = snippet(id = 2L, snippetType = SnippetType.SALUTATION.name, isDefault = false, enabled = true)

        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(target))
        Mockito.`when`(repository.findBySnippetTypeAndIsDefaultTrue(SnippetType.SALUTATION.name))
            .thenReturn(listOf(oldDefault, target))
        Mockito.`when`(repository.save(Mockito.any(ReplySnippet::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as ReplySnippet }
        stubVariantPersistence()

        val result = service.setDefault(2L)

        assertTrue(result.snippet.isDefault)

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
    fun `resolveManualFrame excludes CUSTOM snippets`() {
        Mockito.`when`(repository.findBySnippetTypeAndEnabledTrueAndIsDefaultTrue(SnippetType.SALUTATION.name))
            .thenReturn(listOf(snippet(id = 1L, snippetType = SnippetType.SALUTATION.name, isDefault = true)))
        Mockito.`when`(repository.findBySnippetTypeAndEnabledTrueAndIsDefaultTrue(SnippetType.GREETING.name))
            .thenReturn(emptyList())
        Mockito.`when`(repository.findBySnippetTypeAndEnabledTrueAndIsDefaultTrue(SnippetType.CLOSING.name))
            .thenReturn(emptyList())
        Mockito.`when`(repository.findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(SnippetType.ACK.name))
            .thenReturn(listOf(snippet(id = 9L, snippetType = SnippetType.ACK.name, content = "Thanks")))

        val frame = service.resolveManualFrame()

        assertEquals("Dear Professor,", frame.salutation)
        assertNull(frame.greeting)
        assertNull(frame.closing)
        assertEquals(listOf("Thanks"), frame.ackOptions.map { it.content })
        Mockito.verify(repository, Mockito.never())
            .findBySnippetTypeAndEnabledTrueAndIsDefaultTrue(SnippetType.CUSTOM.name)
        Mockito.verify(repository, Mockito.never())
            .findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(SnippetType.CUSTOM.name)
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

    @Test
    fun `create rejects CUSTOM default`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.create(
                ReplySnippetCreateCommand(
                    snippetType = SnippetType.CUSTOM.name,
                    content = "Custom paragraph.",
                    isDefault = true
                )
            )
        }

        assertTrue(ex.message!!.contains("CUSTOM snippets cannot be default"))
        Mockito.verify(repository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `create rejects unknown placeholder in content`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.create(
                ReplySnippetCreateCommand(
                    snippetType = SnippetType.SALUTATION.name,
                    content = "Dear \${bogus},"
                )
            )
        }
        assertTrue(ex.message!!.contains("\${bogus}"))
        Mockito.verify(repository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `create rejects nullable placeholder without fallback`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.create(
                ReplySnippetCreateCommand(
                    snippetType = SnippetType.SALUTATION.name,
                    content = "Dear Dr. \${expertFamilyName},"
                )
            )
        }
        assertTrue(ex.message!!.contains("\${expertFamilyName}"))
    }

    @Test
    fun `create rejects nullable placeholder with whitespace-only fallback`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.create(
                ReplySnippetCreateCommand(
                    snippetType = SnippetType.SALUTATION.name,
                    content = "Dear Dr. \${expertFamilyName|   },"
                )
            )
        }
        assertTrue(ex.message!!.contains("\${expertFamilyName|   }"))
        Mockito.verify(repository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `create accepts nullable placeholder with fallback`() {
        Mockito.`when`(repository.save(Mockito.any(ReplySnippet::class.java)))
            .thenAnswer { invocation -> (invocation.arguments[0] as ReplySnippet).copy(id = 1L) }
        stubVariantPersistence()

        val created = service.create(
            ReplySnippetCreateCommand(
                snippetType = SnippetType.SALUTATION.name,
                content = "Dear Dr. \${expertFamilyName|Professor},"
            )
        )

        assertEquals("Dear Dr. \${expertFamilyName|Professor},", created.snippet.content)
    }

    private fun stubVariantPersistence() {
        val stored = mutableListOf<ContentVariant>()
        Mockito.lenient().doAnswer {
            stored.clear()
            null
        }.`when`(contentVariantRepository).deleteByOwnerTypeAndOwnerId(
            Mockito.anyString(),
            Mockito.anyLong()
        )
        Mockito.`when`(contentVariantRepository.save(Mockito.any(ContentVariant::class.java)))
            .thenAnswer { invocation ->
                val saved = invocation.arguments[0] as ContentVariant
                val withId = saved.copy(id = variantIdSeq.getAndIncrement())
                stored += withId
                withId
            }
        Mockito.lenient().`when`(
            contentVariantRepository.findByOwnerTypeAndOwnerIdOrderByVariantOrderAscIdAsc(
                Mockito.anyString(),
                Mockito.anyLong()
            )
        ).thenAnswer {
            stored.sortedWith(compareBy({ it.variantOrder }, { it.id }))
        }
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
