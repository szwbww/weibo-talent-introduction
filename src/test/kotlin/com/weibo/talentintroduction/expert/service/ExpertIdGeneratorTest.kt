package com.weibo.talentintroduction.expert.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ExpertIdGeneratorTest {

    @Test
    fun `ORCID takes priority`() {
        val id = ExpertIdGenerator.generate("0000-0001-0002-0003", "a@b.com")
        assertEquals("0000-0001-0002-0003", id)
    }

    @Test
    fun `email fallback when orcid is null`() {
        val id = ExpertIdGenerator.generate(null, "a@b.com")
        assert(id.startsWith("EMAIL-"))
        assertEquals(25, id.length)
    }

    @Test
    fun `email fallback when orcid is blank`() {
        val id = ExpertIdGenerator.generate("  ", "a@b.com")
        assert(id.startsWith("EMAIL-"))
    }

    @Test
    fun `throws when both are null`() {
        assertThrows(IllegalStateException::class.java) {
            ExpertIdGenerator.generate(null, null)
        }
    }

    @Test
    fun `throws when both are blank`() {
        assertThrows(IllegalStateException::class.java) {
            ExpertIdGenerator.generate("", "")
        }
    }

    @Test
    fun `email hash is deterministic`() {
        val id1 = ExpertIdGenerator.generate(null, "test@example.com")
        val id2 = ExpertIdGenerator.generate(null, "test@example.com")
        assertEquals(id1, id2)
    }

    @Test
    fun `email case insensitive`() {
        val id1 = ExpertIdGenerator.generate(null, "Test@Example.com")
        val id2 = ExpertIdGenerator.generate(null, "test@example.com")
        assertEquals(id1, id2)
    }
}
