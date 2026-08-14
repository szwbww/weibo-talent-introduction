package com.weibo.talentintroduction.reply.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.service.MailPlaceholderService
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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.LocalDateTime
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
            ContentVariantService(contentVariantRepository, MailPlaceholderService())
        )
    )
    private val contentVariantService = ContentVariantService(contentVariantRepository, MailPlaceholderService())
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

    // ── 02 selectable frame: options, strict resolution, deterministic version ──

    @Test
    fun `listSelectableFrameOptions returns only enabled four slot main snippets in fixed order`() {
        Mockito.`when`(repository.findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(SnippetType.SALUTATION.name))
            .thenReturn(listOf(
                snippet(id = 1L, snippetType = SnippetType.SALUTATION.name, content = "Sal 1"),
                snippet(id = 2L, snippetType = SnippetType.SALUTATION.name, content = "Sal 2", displayOrder = 5)
            ))
        Mockito.`when`(repository.findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(SnippetType.GREETING.name))
            .thenReturn(listOf(snippet(id = 3L, snippetType = SnippetType.GREETING.name, content = "Greet")))
        Mockito.`when`(repository.findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(SnippetType.ACK.name))
            .thenReturn(listOf(
                snippet(id = 4L, snippetType = SnippetType.ACK.name, content = "Ack"),
                snippet(id = 5L, snippetType = SnippetType.ACK.name, content = "   ", displayOrder = 1)
            ))
        Mockito.`when`(repository.findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(SnippetType.CLOSING.name))
            .thenReturn(listOf(snippet(id = 6L, snippetType = SnippetType.CLOSING.name, content = "Close")))

        val options = service.listSelectableFrameOptions()

        // fixed slot order SALUTATION -> GREETING -> ACK -> CLOSING, then displayOrder, then id
        assertEquals(listOf(2L, 1L, 3L, 4L, 6L), options.map { it.id })
        assertTrue(options.all { it.content.isNotBlank() })
        assertTrue(options.all { it.snippetType != SnippetType.CUSTOM.name })
        Mockito.verify(repository, Mockito.never())
            .findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(SnippetType.CUSTOM.name)
    }

    @Test
    fun `listSelectableFrameOptions filters disabled and blank snippets`() {
        Mockito.`when`(repository.findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(SnippetType.SALUTATION.name))
            .thenReturn(listOf(
                snippet(id = 1L, snippetType = SnippetType.SALUTATION.name, content = "Sal", enabled = false),
                snippet(id = 2L, snippetType = SnippetType.SALUTATION.name, content = "   "),
                snippet(id = 3L, snippetType = SnippetType.SALUTATION.name, content = "Sal 3")
            ))
        Mockito.`when`(repository.findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(SnippetType.GREETING.name))
            .thenReturn(emptyList())
        Mockito.`when`(repository.findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(SnippetType.ACK.name))
            .thenReturn(emptyList())
        Mockito.`when`(repository.findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc(SnippetType.CLOSING.name))
            .thenReturn(emptyList())

        assertEquals(listOf(3L), service.listSelectableFrameOptions().map { it.id })
    }

    @Test
    fun `resolveDefaultSelectableFrame uses enabled defaults and null ack`() {
        Mockito.`when`(repository.findBySnippetTypeAndEnabledTrueAndIsDefaultTrue(SnippetType.SALUTATION.name))
            .thenReturn(listOf(snippet(id = 1L, snippetType = SnippetType.SALUTATION.name, isDefault = true, content = "Dear X,")))
        Mockito.`when`(repository.findBySnippetTypeAndEnabledTrueAndIsDefaultTrue(SnippetType.GREETING.name))
            .thenReturn(emptyList())
        Mockito.`when`(repository.findBySnippetTypeAndEnabledTrueAndIsDefaultTrue(SnippetType.CLOSING.name))
            .thenReturn(listOf(snippet(id = 3L, snippetType = SnippetType.CLOSING.name, isDefault = true, content = "Regards")))
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(
            snippet(id = 1L, snippetType = SnippetType.SALUTATION.name, isDefault = true, content = "Dear X,")
        ))
        Mockito.`when`(repository.findById(3L)).thenReturn(Optional.of(
            snippet(id = 3L, snippetType = SnippetType.CLOSING.name, isDefault = true, content = "Regards")
        ))

        val frame = service.resolveDefaultSelectableFrame()

        assertEquals(1L, frame.selection.salutationSnippetId)
        assertNull(frame.selection.greetingSnippetId)
        assertNull(frame.selection.ackSnippetId)
        assertEquals(3L, frame.selection.closingSnippetId)
        assertEquals("Dear X,", frame.salutation)
        assertNull(frame.greeting)
        assertNull(frame.acknowledgement)
        assertEquals("Regards", frame.closing)
        assertTrue(frame.version.isNotBlank())
    }

    @Test
    fun `resolveSelectableFrame fails closed on missing disabled type mismatch and blank`() {
        Mockito.`when`(repository.findById(10L)).thenReturn(Optional.empty())
        val missing = assertThrows(IllegalArgumentException::class.java) {
            service.resolveSelectableFrame(ReplyFrameSelection(salutationSnippetId = 10L))
        }
        assertTrue(missing.message!!.contains("SALUTATION"))

        Mockito.`when`(repository.findById(11L)).thenReturn(Optional.of(
            snippet(id = 11L, snippetType = SnippetType.SALUTATION.name, enabled = false)
        ))
        assertThrows(IllegalArgumentException::class.java) {
            service.resolveSelectableFrame(ReplyFrameSelection(salutationSnippetId = 11L))
        }

        Mockito.`when`(repository.findById(12L)).thenReturn(Optional.of(
            snippet(id = 12L, snippetType = SnippetType.CLOSING.name)
        ))
        val mismatch = assertThrows(IllegalArgumentException::class.java) {
            service.resolveSelectableFrame(ReplyFrameSelection(salutationSnippetId = 12L))
        }
        assertTrue(mismatch.message!!.contains("type mismatch"))

        Mockito.`when`(repository.findById(13L)).thenReturn(Optional.of(
            snippet(id = 13L, snippetType = SnippetType.ACK.name, content = "   ")
        ))
        assertThrows(IllegalArgumentException::class.java) {
            service.resolveSelectableFrame(ReplyFrameSelection(ackSnippetId = 13L))
        }
    }

    @Test
    fun `all null selection resolves explicit empty frame with stable version`() {
        val empty = ReplyFrameSelection(null, null, null, null)
        val first = service.resolveSelectableFrame(empty)
        val second = service.resolveSelectableFrame(empty)

        assertNull(first.salutation)
        assertNull(first.greeting)
        assertNull(first.acknowledgement)
        assertNull(first.closing)
        assertTrue(first.version.isNotBlank())
        assertEquals(first.version, second.version)
    }

    @Test
    fun `frame version is deterministic and changes with content updatedAt id and slot`() {
        val base = snippet(
            id = 1L,
            snippetType = SnippetType.GREETING.name,
            content = "Hello",
            updatedAt = LocalDateTime.of(2026, 8, 1, 9, 0)
        )
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(base))
        val selection = ReplyFrameSelection(greetingSnippetId = 1L)
        val v1 = service.resolveSelectableFrame(selection).version
        assertEquals(v1, service.resolveSelectableFrame(selection).version)

        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(base.copy(content = "Hello there")))
        assertNotEquals(v1, service.resolveSelectableFrame(selection).version)

        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(
            base.copy(updatedAt = LocalDateTime.of(2026, 8, 2, 9, 0))
        ))
        assertNotEquals(v1, service.resolveSelectableFrame(selection).version)

        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(base.copy(id = 2L)))
        val vDifferentId = service.resolveSelectableFrame(ReplyFrameSelection(greetingSnippetId = 2L)).version
        assertNotEquals(v1, vDifferentId)

        Mockito.`when`(repository.findById(3L)).thenReturn(Optional.of(
            snippet(
                id = 3L,
                snippetType = SnippetType.SALUTATION.name,
                content = "Hello",
                updatedAt = LocalDateTime.of(2026, 8, 1, 9, 0)
            )
        ))
        val vDifferentSlot = service.resolveSelectableFrame(ReplyFrameSelection(salutationSnippetId = 3L)).version
        assertNotEquals(v1, vDifferentSlot)
    }

    @Test
    fun `create persists trimmed name`() {
        Mockito.`when`(repository.save(Mockito.any(ReplySnippet::class.java)))
            .thenAnswer { invocation -> (invocation.arguments[0] as ReplySnippet).copy(id = 1L) }
        stubVariantPersistence()

        service.create(
            ReplySnippetCreateCommand(
                snippetType = SnippetType.SALUTATION.name,
                content = "Dear Professor,",
                name = "  尊称-教授  "
            )
        )

        val captor = ArgumentCaptor.forClass(ReplySnippet::class.java)
        Mockito.verify(repository).save(captor.capture())
        assertEquals("尊称-教授", captor.value.name)
    }

    @Test
    fun `create normalizes blank name to null`() {
        Mockito.`when`(repository.save(Mockito.any(ReplySnippet::class.java)))
            .thenAnswer { invocation -> (invocation.arguments[0] as ReplySnippet).copy(id = 1L) }
        stubVariantPersistence()

        service.create(
            ReplySnippetCreateCommand(
                snippetType = SnippetType.SALUTATION.name,
                content = "Dear Professor,",
                name = "   "
            )
        )

        val captor = ArgumentCaptor.forClass(ReplySnippet::class.java)
        Mockito.verify(repository).save(captor.capture())
        assertNull(captor.value.name)
    }

    @Test
    fun `update clears name when blank`() {
        val existing = snippet(id = 5L, snippetType = SnippetType.SALUTATION.name, content = "Dear Professor,", name = "旧名")
        Mockito.`when`(repository.findById(5L)).thenReturn(Optional.of(existing))
        Mockito.`when`(repository.save(Mockito.any(ReplySnippet::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as ReplySnippet }
        stubVariantPersistence()

        service.update(
            5L,
            ReplySnippetUpdateCommand(
                content = "Dear Professor,",
                displayOrder = 10,
                name = "",
                isDefault = false,
                enabled = true
            )
        )

        val captor = ArgumentCaptor.forClass(ReplySnippet::class.java)
        Mockito.verify(repository).save(captor.capture())
        assertNull(captor.value.name)
    }

    @Test
    fun `name does not affect frame version`() {
        val base = snippet(
            id = 1L,
            snippetType = SnippetType.SALUTATION.name,
            content = "Dear Professor,",
            updatedAt = LocalDateTime.of(2026, 8, 1, 9, 0)
        )
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(base))
        val selection = ReplyFrameSelection(salutationSnippetId = 1L)
        val versionWithoutName = service.resolveSelectableFrame(selection).version

        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(base.copy(name = "尊称-教授")))
        val versionWithName = service.resolveSelectableFrame(selection).version

        assertEquals(versionWithoutName, versionWithName)
    }

    @Test
    fun `setDefault preserves name`() {
        val target = snippet(
            id = 2L,
            snippetType = SnippetType.SALUTATION.name,
            isDefault = false,
            enabled = true,
            name = "标准开场-v1"
        )
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(target))
        Mockito.`when`(repository.findBySnippetTypeAndIsDefaultTrue(SnippetType.SALUTATION.name))
            .thenReturn(emptyList())
        Mockito.`when`(repository.save(Mockito.any(ReplySnippet::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as ReplySnippet }
        stubVariantPersistence()

        service.setDefault(2L)

        val captor = ArgumentCaptor.forClass(ReplySnippet::class.java)
        Mockito.verify(repository).save(captor.capture())
        assertEquals("标准开场-v1", captor.value.name)
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
        content: String = "Dear Professor,",
        displayOrder: Int = 10,
        name: String? = null,
        updatedAt: LocalDateTime? = null
    ): ReplySnippet =
        ReplySnippet(
            id = id,
            snippetType = snippetType,
            content = content,
            displayOrder = displayOrder,
            isDefault = isDefault,
            enabled = enabled,
            name = name,
            updatedAt = updatedAt
        )
}
