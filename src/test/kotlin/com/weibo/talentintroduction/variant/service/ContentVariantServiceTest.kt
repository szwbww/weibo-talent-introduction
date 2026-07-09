package com.weibo.talentintroduction.variant.service

import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.variant.domain.ContentVariant
import com.weibo.talentintroduction.variant.domain.ContentVariantOwnerType
import com.weibo.talentintroduction.variant.repository.ContentVariantRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

class ContentVariantServiceTest {
    private val repository = Mockito.mock(ContentVariantRepository::class.java)
    private val mailVariableService = Mockito.mock(MailVariableService::class.java)
    private val service = ContentVariantService(repository, mailVariableService)

    @Test
    fun `resolveBody selects from pool by variant_order asc id asc`() {
        Mockito.`when`(
            repository.findByOwnerTypeAndOwnerIdAndEnabledTrueOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.QA_RULE,
                10L
            )
        ).thenReturn(
            listOf(
                variant(id = 1L, order = 1, content = "A"),
                variant(id = 2L, order = 2, content = "B")
            )
        )

        val seed = 2
        val selected = service.resolveBody(ContentVariantOwnerType.QA_RULE, 10L, "MAIN", seed)

        assertEquals("MAIN", selected)
        assertEquals("A", service.resolveBody(ContentVariantOwnerType.QA_RULE, 10L, "MAIN", seed + 1))
        assertEquals("B", service.resolveBody(ContentVariantOwnerType.QA_RULE, 10L, "MAIN", seed + 2))
    }

    @Test
    fun `resolveBody decouples owners with same seed`() {
        Mockito.`when`(
            repository.findByOwnerTypeAndOwnerIdAndEnabledTrueOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.QA_RULE,
                1L
            )
        ).thenReturn(listOf(variant(id = 1L, order = 1, content = "V1")))
        Mockito.`when`(
            repository.findByOwnerTypeAndOwnerIdAndEnabledTrueOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.QA_RULE,
                2L
            )
        ).thenReturn(listOf(variant(id = 2L, order = 1, content = "V2")))

        val seed = 5
        val first = service.resolveBody(ContentVariantOwnerType.QA_RULE, 1L, "MAIN", seed)
        val second = service.resolveBody(ContentVariantOwnerType.QA_RULE, 2L, "MAIN", seed)

        assertEquals(Math.floorMod(seed + 1L, 2), if (first == "MAIN") 0 else 1)
        assertEquals(Math.floorMod(seed + 2L, 2), if (second == "MAIN") 0 else 1)
        assertEquals("MAIN", first)
        assertEquals("V2", second)
    }

    @Test
    fun `resolveBody returns main body for unknown owner type`() {
        val body = service.resolveBody("UNKNOWN", 1L, "  keep spaces  ", seed = 3)
        assertEquals("  keep spaces  ", body)
        Mockito.verifyNoInteractions(repository)
    }

    @Test
    fun `resolveBody returns main body when owner id is null`() {
        assertEquals("MAIN", service.resolveBody(ContentVariantOwnerType.QA_RULE, null, "MAIN", seed = 1))
        Mockito.verifyNoInteractions(repository)
    }

    @Test
    fun `resolveBody returns main body unchanged when no variants`() {
        Mockito.`when`(
            repository.findByOwnerTypeAndOwnerIdAndEnabledTrueOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.REPLY_SNIPPET,
                5L
            )
        ).thenReturn(emptyList())

        assertEquals("  raw body\n", service.resolveBody(ContentVariantOwnerType.REPLY_SNIPPET, 5L, "  raw body\n", 99))
    }

    @Test
    fun `resolveBody ignores variants when useVariants is false`() {
        Mockito.`when`(
            repository.findByOwnerTypeAndOwnerIdAndEnabledTrueOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.QA_RULE,
                10L
            )
        ).thenReturn(listOf(variant(id = 1L, order = 1, content = "VARIANT")))

        assertEquals("MAIN", service.resolveBody(ContentVariantOwnerType.QA_RULE, 10L, "MAIN", seed = 1, useVariants = false))
    }

    @Test
    fun `poolSize returns one when no variants`() {
        Mockito.`when`(
            repository.findByOwnerTypeAndOwnerIdAndEnabledTrueOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.QA_RULE,
                10L
            )
        ).thenReturn(emptyList())

        assertEquals(1, service.poolSize(ContentVariantOwnerType.QA_RULE, 10L, "MAIN"))
    }

    @Test
    fun `poolSize includes main body and enabled variants`() {
        Mockito.`when`(
            repository.findByOwnerTypeAndOwnerIdAndEnabledTrueOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.QA_RULE,
                10L
            )
        ).thenReturn(
            listOf(
                variant(id = 1L, order = 1, content = "A"),
                variant(id = 2L, order = 2, content = "B")
            )
        )

        assertEquals(3, service.poolSize(ContentVariantOwnerType.QA_RULE, 10L, "MAIN"))
    }

    @Test
    fun `replaceForOwner deletes old variants and saves in order`() {
        val stored = mutableListOf<ContentVariant>()
        Mockito.doAnswer {
            stored.clear()
            null
        }.`when`(repository).deleteByOwnerTypeAndOwnerId(ContentVariantOwnerType.QA_RULE, 10L)
        Mockito.`when`(repository.save(Mockito.any(ContentVariant::class.java)))
            .thenAnswer { invocation ->
                val saved = invocation.arguments[0] as ContentVariant
                val withId = saved.copy(id = (stored.size + 1).toLong())
                stored += withId
                withId
            }

        service.replaceForOwner(ContentVariantOwnerType.QA_RULE, 10L, "MAIN", listOf("Variant A", "Variant B"))

        val captor = ArgumentCaptor.forClass(ContentVariant::class.java)
        Mockito.verify(repository, Mockito.times(2)).save(captor.capture())
        assertEquals(listOf(10, 20), captor.allValues.map { it.variantOrder })
        assertEquals(listOf("Variant A", "Variant B"), captor.allValues.map { it.content })
    }

    @Test
    fun `replaceForOwner rejects blank duplicate and invalid placeholder variants`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.replaceForOwner(ContentVariantOwnerType.QA_RULE, 10L, "MAIN", listOf(" "))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.replaceForOwner(ContentVariantOwnerType.QA_RULE, 10L, "MAIN", listOf("MAIN"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.replaceForOwner(ContentVariantOwnerType.QA_RULE, 10L, "MAIN", listOf("A", "A"))
        }
        Mockito.doThrow(IllegalArgumentException("Invalid placeholders: \${bogus}"))
            .`when`(mailVariableService).requireValidPlaceholders("Bad \${bogus}")
        assertThrows(IllegalArgumentException::class.java) {
            service.replaceForOwner(ContentVariantOwnerType.QA_RULE, 10L, "MAIN", listOf("Bad \${bogus}"))
        }
        Mockito.verify(repository, Mockito.never()).deleteByOwnerTypeAndOwnerId(
            Mockito.anyString(),
            Mockito.anyLong()
        )
    }

    @Test
    fun `deleteForOwner removes variants for known owner type`() {
        service.deleteForOwner(ContentVariantOwnerType.REPLY_SNIPPET, 5L)
        Mockito.verify(repository).deleteByOwnerTypeAndOwnerId(ContentVariantOwnerType.REPLY_SNIPPET, 5L)
    }

    private fun variant(id: Long, order: Int, content: String): ContentVariant =
        ContentVariant(
            id = id,
            ownerType = ContentVariantOwnerType.QA_RULE,
            ownerId = 10L,
            variantOrder = order,
            content = content
        )
}
