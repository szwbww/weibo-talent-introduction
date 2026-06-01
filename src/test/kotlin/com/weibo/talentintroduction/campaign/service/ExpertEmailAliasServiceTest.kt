package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.ExpertEmailAlias
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.ExpertEmailAliasRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.Optional

class ExpertEmailAliasServiceTest {
    private val aliasRepository = Mockito.mock(ExpertEmailAliasRepository::class.java)
    private val contactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val service = ExpertEmailAliasService(aliasRepository, contactRepository)

    private fun contact(id: Long, email: String) = ExpertContact(
        id = id, campaignId = 10L, orcidId = "orcid-$id",
        expertEmail = email, expertName = null
    )

    @Test
    fun `normalizeEmail trims and lowercases`() {
        assertEquals("test@example.com", service.normalizeEmail("  Test@Example.COM "))
        assertEquals("foo@bar.org", service.normalizeEmail("Foo@Bar.Org"))
    }

    @Test
    fun `findContactByEmail matches primary email first`() {
        val email = "expert@example.com"
        val c = contact(1L, email)
        Mockito.`when`(contactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc(email)).thenReturn(c)
        assertEquals(c, service.findContactByEmail(email))
    }

    @Test
    fun `findContactByEmail matches alias when primary not found`() {
        val email = "  Alias@Example.COM "
        val normalized = "alias@example.com"
        val c = contact(1L, "main@example.com")
        Mockito.`when`(contactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc(email)).thenReturn(null)
        Mockito.`when`(aliasRepository.findByNormalizedEmail(normalized))
            .thenReturn(ExpertEmailAlias(id = 10L, expertContactId = 1L, email = "alias@example.com", normalizedEmail = normalized))
        Mockito.`when`(contactRepository.findById(1L)).thenReturn(Optional.of(c))
        assertEquals(c, service.findContactByEmail(email))
    }

    @Test
    fun `findContactByEmail returns null when no match`() {
        val email = "unknown@example.com"
        Mockito.`when`(contactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc(email)).thenReturn(null)
        Mockito.`when`(aliasRepository.findByNormalizedEmail(email)).thenReturn(null)
        assertNull(service.findContactByEmail(email))
    }

    @Test
    fun `addAlias creates alias`() {
        val contactId = 1L
        val email = "alias@example.com"
        val c = contact(contactId, "main@example.com")
        Mockito.`when`(contactRepository.findById(contactId)).thenReturn(Optional.of(c))
        Mockito.`when`(aliasRepository.existsByNormalizedEmail(email)).thenReturn(false)
        Mockito.`when`(aliasRepository.save(Mockito.any(ExpertEmailAlias::class.java)))
            .thenAnswer { it.getArgument<ExpertEmailAlias>(0).copy(id = 100L) }

        val alias = service.addAlias(contactId, email)
        assertNotNull(alias.id)
        assertEquals(contactId, alias.expertContactId)
        assertEquals(email, alias.normalizedEmail)
        assertEquals("MANUAL_ADD", alias.source)
    }

    @Test
    fun `addAlias rejects duplicate normalized email`() {
        val contactId = 1L
        val email = "alias@example.com"
        val c = contact(contactId, "main@example.com")
        Mockito.`when`(contactRepository.findById(contactId)).thenReturn(Optional.of(c))
        Mockito.`when`(aliasRepository.existsByNormalizedEmail(email)).thenReturn(true)

        assertThrows(IllegalArgumentException::class.java) {
            service.addAlias(contactId, email)
        }
    }

    @Test
    fun `bindAlias is idempotent for same contact`() {
        val contactId = 1L
        val email = "alias@example.com"
        val normalized = "alias@example.com"
        val existing = ExpertEmailAlias(id = 100L, expertContactId = contactId, email = email, normalizedEmail = normalized)
        Mockito.`when`(aliasRepository.findByNormalizedEmail(normalized)).thenReturn(existing)

        val result = service.bindAlias(contactId, email)
        assertEquals(existing.id, result.id)
        assertEquals(existing.expertContactId, result.expertContactId)
    }

    @Test
    fun `bindAlias rejects alias already bound to another contact`() {
        val email = "alias@example.com"
        val normalized = "alias@example.com"
        val existing = ExpertEmailAlias(id = 100L, expertContactId = 2L, email = email, normalizedEmail = normalized)
        Mockito.`when`(aliasRepository.findByNormalizedEmail(normalized)).thenReturn(existing)

        assertThrows(IllegalArgumentException::class.java) {
            service.bindAlias(1L, email)
        }
    }

    @Test
    fun `listAliases returns aliases for contact`() {
        val contactId = 1L
        val aliases = listOf(
            ExpertEmailAlias(id = 1L, expertContactId = contactId, email = "a@b.com", normalizedEmail = "a@b.com"),
            ExpertEmailAlias(id = 2L, expertContactId = contactId, email = "c@d.com", normalizedEmail = "c@d.com")
        )
        Mockito.`when`(aliasRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)).thenReturn(aliases)

        val result = service.listAliases(contactId)
        assertEquals(2, result.size)
    }

    @Test
    fun `findContactByEmailOrAlias ignores case and whitespace`() {
        val email = "  Alias@Example.COM "
        val normalized = "alias@example.com"
        val c = contact(1L, "main@example.com")
        Mockito.`when`(contactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc(normalized)).thenReturn(null)
        Mockito.`when`(contactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc(email)).thenReturn(null)
        Mockito.`when`(aliasRepository.findByNormalizedEmail(normalized))
            .thenReturn(ExpertEmailAlias(id = 10L, expertContactId = 1L, email = email.trim(), normalizedEmail = normalized))
        Mockito.`when`(contactRepository.findById(1L)).thenReturn(Optional.of(c))
        assertEquals(c, service.findContactByEmailOrAlias(email))
    }
}
