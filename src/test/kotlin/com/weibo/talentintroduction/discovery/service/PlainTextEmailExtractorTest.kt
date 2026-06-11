package com.weibo.talentintroduction.discovery.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlainTextEmailExtractorTest {
    private val extractor = PlainTextEmailExtractor()

    @Test
    fun `extracts standard emails`() {
        val text = "Contact: john.smith@oxford.ac.uk, jane.doe@cam.ac.uk"
        val result = extractor.extract(text)
        assertEquals(2, result.size)
        assertTrue(result.contains("john.smith@oxford.ac.uk"))
        assertTrue(result.contains("jane.doe@cam.ac.uk"))
    }

    @Test
    fun `deobfuscates at and dot patterns`() {
        val text = "alice(at)oxford(dot)ac(dot)uk bob{at}university.edu"
        val result = extractor.extract(text)
        assertEquals(2, result.size)
        assertTrue(result.contains("alice@oxford.ac.uk"))
        assertTrue(result.contains("bob@university.edu"))
    }

    @Test
    fun `filters blacklisted prefixes`() {
        val text = "journals@springer.com researcher@gmail.com support@elsevier.com"
        val result = extractor.extract(text)
        assertEquals(1, result.size)
        assertTrue(result.contains("researcher@gmail.com"))
    }

    @Test
    fun `filters blacklist domain example com`() {
        val text = "user@example.com real@oxford.ac.uk"
        val result = extractor.extract(text)
        assertEquals(1, result.size)
        assertTrue(result.contains("real@oxford.ac.uk"))
    }

    @Test
    fun `returns empty for blank text`() {
        val result = extractor.extract("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns empty for text without emails`() {
        val result = extractor.extract("This is a simple document with no email addresses.")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deduplicates emails`() {
        val text = "a@b.com a@b.com c@d.com"
        val result = extractor.extract(text)
        assertEquals(2, result.size)
    }

    @Test
    fun `normalizes to lowercase`() {
        val text = "John@Oxford.ac.uk"
        val result = extractor.extract(text)
        assertEquals(listOf("john@oxford.ac.uk"), result)
    }
}
