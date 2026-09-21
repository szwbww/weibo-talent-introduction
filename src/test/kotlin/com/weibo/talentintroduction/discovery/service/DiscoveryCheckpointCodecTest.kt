package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiscoveryCheckpointCodecTest {

    private val criteria = PaperSearchCriteria(
        keywords = listOf("cancer"),
        publicationYearFrom = 2020,
        publicationYearTo = 2026
    )

    @Test
    fun `source key is source plus v2 plus 24 hex chars`() {
        val key = DiscoveryCheckpointCodec.sourceKey("EUROPE_PMC", criteria)

        assertTrue(key.startsWith("EUROPE_PMC:v2:"), "key must be <SOURCE>:v2:<hash>, was $key")
        val hash = key.removePrefix("EUROPE_PMC:v2:")
        assertEquals(24, hash.length)
        assertTrue(hash.all { it in "0123456789abcdef" }, "hash must be hex, was $hash")
    }

    @Test
    fun `every production source name stays within the V32 column width`() {
        val sourceNames = listOf("EUROPE_PMC", "PMC_OA", "OPENALEX", "CROSSREF", "CORE", "ARXIV", "ORCID")

        for (name in sourceNames) {
            val key = DiscoveryCheckpointCodec.sourceKey(name, criteria)
            assertTrue(
                key.length <= DiscoveryCheckpointCodec.MAX_SOURCE_NAME_LENGTH,
                "$name key must fit VARCHAR(50), was ${key.length}: $key"
            )
        }
    }

    @Test
    fun `key ignores the transient cursor so pages of one query share a key`() {
        val firstPage = DiscoveryCheckpointCodec.sourceKey("OPENALEX", criteria.copy(cursor = null))
        val laterPage = DiscoveryCheckpointCodec.sourceKey("OPENALEX", criteria.copy(cursor = "AoJpage2cursor"))

        assertEquals(firstPage, laterPage)
    }

    @Test
    fun `key covers criteria years scope page size sources and version`() {
        val base = DiscoveryCheckpointCodec.sourceKey("OPENALEX", criteria)

        assertNotEquals(base, DiscoveryCheckpointCodec.sourceKey("OPENALEX", criteria.copy(keywords = listOf("diabetes"))))
        assertNotEquals(base, DiscoveryCheckpointCodec.sourceKey("OPENALEX", criteria.copy(publicationYearFrom = 2019)))
        assertNotEquals(base, DiscoveryCheckpointCodec.sourceKey("OPENALEX", criteria.copy(publicationYearTo = 2027)))
        assertNotEquals(base, DiscoveryCheckpointCodec.sourceKey("OPENALEX", criteria.copy(subjectScope = "RND_TARGET")))
        assertNotEquals(base, DiscoveryCheckpointCodec.sourceKey("OPENALEX", criteria.copy(pageSize = 50)))
        assertNotEquals(base, DiscoveryCheckpointCodec.sourceKey("OPENALEX", criteria.copy(sources = listOf("OPENALEX"))))
        assertNotEquals(base, DiscoveryCheckpointCodec.sourceKey("CROSSREF", criteria))
        assertTrue(base.contains(":${DiscoveryCheckpointCodec.QUERY_VERSION}:"))
    }

    @Test
    fun `key normalizes keyword order blanks and duplicates`() {
        val plain = DiscoveryCheckpointCodec.sourceKey("OPENALEX", criteria.copy(keywords = listOf("a", "b")))
        val reshuffled = DiscoveryCheckpointCodec.sourceKey(
            "OPENALEX", criteria.copy(keywords = listOf(" b ", "a", "a"))
        )

        assertEquals(plain, reshuffled)
    }

    @Test
    fun `envelope round trips ACTIVE with a resume cursor`() {
        val decoded = DiscoveryCheckpointCodec.decode(DiscoveryCheckpointCodec.encode("C7", exhausted = false))

        assertEquals("C7", decoded?.cursor)
        assertEquals(CheckpointState.ACTIVE, decoded?.state)
        assertFalse(decoded!!.exhausted)
    }

    @Test
    fun `envelope round trips ACTIVE without a cursor`() {
        val decoded = DiscoveryCheckpointCodec.decode(DiscoveryCheckpointCodec.encode(null, exhausted = false))

        assertNull(decoded?.cursor)
        assertEquals(CheckpointState.ACTIVE, decoded?.state)
    }

    @Test
    fun `envelope round trips EXHAUSTED without a cursor`() {
        val decoded = DiscoveryCheckpointCodec.decode(DiscoveryCheckpointCodec.encode(null, exhausted = true))

        assertNull(decoded?.cursor)
        assertEquals(CheckpointState.EXHAUSTED, decoded?.state)
        assertTrue(decoded!!.exhausted)
    }

    @Test
    fun `decode keeps a cursor containing the field separator`() {
        val decoded = DiscoveryCheckpointCodec.decode(DiscoveryCheckpointCodec.encode("page|2|token", exhausted = false))

        assertEquals("page|2|token", decoded?.cursor)
    }

    @Test
    fun `decode rejects legacy and malformed values so they are never adopted`() {
        assertNull(DiscoveryCheckpointCodec.decode(null), "absent value has no checkpoint")
        assertNull(DiscoveryCheckpointCodec.decode("C7"), "legacy raw cursor must not be adopted")
        assertNull(DiscoveryCheckpointCodec.decode("*"), "legacy first-page cursor must not be adopted")
        assertNull(DiscoveryCheckpointCodec.decode("v1|ACTIVE|C7"), "unknown query version must not be adopted")
        assertNull(DiscoveryCheckpointCodec.decode("v2|CANCELLED|C7"), "unknown state must not be adopted")
        assertNull(DiscoveryCheckpointCodec.decode("v2|ACTIVE"), "truncated envelope must not be adopted")
    }
}
