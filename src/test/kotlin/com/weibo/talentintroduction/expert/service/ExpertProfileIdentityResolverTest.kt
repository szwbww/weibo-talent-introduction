package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertManualProfileInvalidRequestException
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.ExpertProfileLookupUnavailableException
import com.weibo.talentintroduction.expert.domain.ExpertProfileNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

/**
 * 01（I-1）：身份核验的规范化、大小写、跨层、失败语义与「不用 _id 猜身份」。
 */
class ExpertProfileIdentityResolverTest {
    private val jdbc = Mockito.mock(NamedParameterJdbcTemplate::class.java)
    private val contacts = Mockito.mock(ExpertContactRepository::class.java)
    private val search = Mockito.mock(ExpertSearchService::class.java)
    private val resolver = ExpertProfileIdentityResolver(jdbc, contacts, search)

    private fun profileRow(exists: Boolean) {
        Mockito.doAnswer { if (exists) 1L else 0L }.`when`(jdbc).queryForObject(
            Mockito.anyString(), Mockito.any(MapSqlParameterSource::class.java), Mockito.eq(Long::class.java)
        )
    }

    private fun contactExists(vararg knownOrcidIds: String) {
        knownOrcidIds.forEach {
            Mockito.`when`(contacts.existsByOrcidId(it)).thenReturn(true)
        }
    }

    private fun searchHits(vararg hits: Pair<Pair<ExpertIndexLevel, String>, ExpertProfile>) {
        hits.forEach { (query, profile) ->
            Mockito.`when`(search.findByOrcidId(query.second, query.first)).thenReturn(profile)
        }
    }

    private fun jdbcProfileQueryParams(): List<MapSqlParameterSource> =
        Mockito.mockingDetails(jdbc).invocations
            .filter { it.method.name == "queryForObject" }
            .map { it.arguments[1] as MapSqlParameterSource }

    private fun contactLookups(): List<String> =
        Mockito.mockingDetails(contacts).invocations
            .filter { it.method.name == "existsByOrcidId" }
            .map { it.arguments[0] as String }

    private fun searchCalls(): List<Pair<ExpertIndexLevel, String>> =
        Mockito.mockingDetails(search).invocations
            .filter { it.method.name == "findByOrcidId" }
            .map { it.arguments[1] as ExpertIndexLevel to it.arguments[0] as String }

    private fun profile(orcidId: String, docId: String? = null) =
        ExpertProfile(esDocId = docId, orcidId = orcidId, email = null, givenNames = null, familyNames = null,
            country = null, keyword = null, employment = null)

    @Test
    fun `blank expert id is rejected before any lookup`() {
        listOf(null, "", "   ", "\t\n").forEach { raw ->
            val ex = assertThrows(ExpertManualProfileInvalidRequestException::class.java) {
                resolver.requireKnown(raw, ExpertIndexLevel.RAW)
            }
            assertEquals("缺少专家标识，无法保存", ex.message)
        }
        assertTrue(Mockito.mockingDetails(jdbc).invocations.isEmpty())
        assertTrue(Mockito.mockingDetails(contacts).invocations.isEmpty())
        assertTrue(Mockito.mockingDetails(search).invocations.isEmpty())
    }

    @Test
    fun `expert key is normalized and keys longer than 64 characters are rejected`() {
        profileRow(exists = true)
        assertEquals("EMAIL-abc@x.com", resolver.requireKnown("  email-Abc@X.com  ", ExpertIndexLevel.RAW))
        assertEquals("0000-0001X", resolver.requireKnown(" 0000-0001x ", ExpertIndexLevel.RAW))
        assertEquals(
            listOf("EMAIL-abc@x.com", "0000-0001X"),
            jdbcProfileQueryParams().map { it.getValue("expertKey") }
        )
        val atLimit = "a".repeat(64)
        assertEquals("A".repeat(64), resolver.requireKnown(atLimit, ExpertIndexLevel.RAW))
        assertThrows(ExpertManualProfileInvalidRequestException::class.java) {
            resolver.requireKnown("a".repeat(65), ExpertIndexLevel.RAW)
        }
        assertEquals(3, jdbcProfileQueryParams().size)
        assertTrue(Mockito.mockingDetails(contacts).invocations.isEmpty())
        assertTrue(Mockito.mockingDetails(search).invocations.isEmpty())
    }

    @Test
    fun `existing profile row proves identity without contact or search lookups`() {
        profileRow(exists = true)
        assertEquals("0000-0001", resolver.requireKnown("0000-0001", ExpertIndexLevel.APPLICATION))
        val invocation = Mockito.mockingDetails(jdbc).invocations.single()
        val sql = invocation.arguments[0] as String
        assertTrue(sql.startsWith("SELECT COUNT(*) FROM expert_manual_profile"))
        assertEquals("0000-0001", (invocation.arguments[1] as MapSqlParameterSource).getValue("expertKey"))
        assertTrue(Mockito.mockingDetails(contacts).invocations.isEmpty())
        assertTrue(Mockito.mockingDetails(search).invocations.isEmpty())
    }

    @Test
    fun `contact existence accepts raw and normalized lookup keys without duplicates`() {
        profileRow(exists = false)
        contactExists("EMAIL-abc@x.com")
        assertEquals("EMAIL-abc@x.com", resolver.requireKnown("email-Abc@x.com", ExpertIndexLevel.RAW))
        assertEquals(listOf("email-Abc@x.com", "EMAIL-abc@x.com"), contactLookups())
        assertTrue(Mockito.mockingDetails(search).invocations.isEmpty())

        Mockito.reset(jdbc, contacts, search)
        profileRow(exists = false)
        contactExists("0000-0001")
        assertEquals("0000-0001", resolver.requireKnown(" 0000-0001 ", ExpertIndexLevel.CANDIDATE))
        assertEquals(listOf("0000-0001"), contactLookups())
        assertTrue(Mockito.mockingDetails(search).invocations.isEmpty())
    }

    @Test
    fun `search visits requested level first then RAW CANDIDATE APPLICATION with both lookup keys`() {
        profileRow(exists = false)
        searchHits(
            (ExpertIndexLevel.APPLICATION to "EMAIL-abc@x.com") to profile("EMAIL-ABC@X.COM", docId = "es-doc-9")
        )
        assertEquals("EMAIL-abc@x.com", resolver.requireKnown("email-Abc@x.com", ExpertIndexLevel.CANDIDATE))
        assertEquals(
            listOf(
                ExpertIndexLevel.CANDIDATE to "email-Abc@x.com",
                ExpertIndexLevel.CANDIDATE to "EMAIL-abc@x.com",
                ExpertIndexLevel.RAW to "email-Abc@x.com",
                ExpertIndexLevel.RAW to "EMAIL-abc@x.com",
                ExpertIndexLevel.APPLICATION to "email-Abc@x.com",
                ExpertIndexLevel.APPLICATION to "EMAIL-abc@x.com"
            ),
            searchCalls()
        )
    }

    @Test
    fun `search visits requested level first when RAW is requested`() {
        profileRow(exists = false)
        searchHits((ExpertIndexLevel.RAW to "0000-0001") to profile("0000-0001"))
        assertEquals("0000-0001", resolver.requireKnown("0000-0001", ExpertIndexLevel.RAW))
        assertEquals(listOf(ExpertIndexLevel.RAW to "0000-0001"), searchCalls())
    }

    @Test
    fun `records not normalizing to the requested key are rejected and never resolved by document id`() {
        profileRow(exists = false)
        searchHits(
            (ExpertIndexLevel.CANDIDATE to "0000-0001") to profile("0000-9999", docId = "different-doc"),
            (ExpertIndexLevel.RAW to "0000-0001") to profile("", docId = "0000-0001")
        )
        assertThrows(ExpertProfileNotFoundException::class.java) {
            resolver.requireKnown("0000-0001", ExpertIndexLevel.CANDIDATE)
        }
        assertEquals(
            listOf(
                ExpertIndexLevel.CANDIDATE to "0000-0001",
                ExpertIndexLevel.RAW to "0000-0001",
                ExpertIndexLevel.APPLICATION to "0000-0001"
            ),
            searchCalls()
        )
        assertTrue(
            Mockito.mockingDetails(search).invocations.none { it.method.name == "findByDocumentIds" },
            "身份只能按 orcidId 命中确认，不得用 _id 猜测"
        )
    }

    @Test
    fun `unknown expert maps to 404 only after every layer missed`() {
        profileRow(exists = false)
        val ex = assertThrows(ExpertProfileNotFoundException::class.java) {
            resolver.requireKnown("0000-0009", ExpertIndexLevel.APPLICATION)
        }
        assertEquals("专家不存在", ex.message)
        assertEquals(
            listOf(
                ExpertIndexLevel.APPLICATION to "0000-0009",
                ExpertIndexLevel.RAW to "0000-0009",
                ExpertIndexLevel.CANDIDATE to "0000-0009"
            ),
            searchCalls()
        )
    }

    @Test
    fun `lookup failures map to 503 and never masquerade as 404`() {
        profileRow(exists = false)
        ExpertIndexLevel.values().forEach { level ->
            Mockito.`when`(search.findByOrcidId("0000-0002", level))
                .thenThrow(IllegalStateException("elasticsearch unreachable"))
        }
        val searchFailure = assertThrows(ExpertProfileLookupUnavailableException::class.java) {
            resolver.requireKnown("0000-0002", ExpertIndexLevel.RAW)
        }
        assertEquals("专家身份查询服务不可用，请稍后重试", searchFailure.message)
        assertEquals("elasticsearch unreachable", searchFailure.cause?.message)

        Mockito.reset(jdbc, contacts, search)
        Mockito.doAnswer { throw DataAccessResourceFailureException("database down") }.`when`(jdbc).queryForObject(
            Mockito.anyString(), Mockito.any(MapSqlParameterSource::class.java), Mockito.eq(Long::class.java)
        )
        assertThrows(ExpertProfileLookupUnavailableException::class.java) {
            resolver.requireKnown("0000-0003", ExpertIndexLevel.RAW)
        }

        Mockito.reset(jdbc, contacts, search)
        profileRow(exists = false)
        Mockito.`when`(contacts.existsByOrcidId(Mockito.anyString()))
            .thenThrow(DataAccessResourceFailureException("contact table down"))
        assertThrows(ExpertProfileLookupUnavailableException::class.java) {
            resolver.requireKnown("0000-0004", ExpertIndexLevel.RAW)
        }
    }

    @Test
    fun `existing profile stays readable while the search service is down`() {
        profileRow(exists = true)
        ExpertIndexLevel.values().forEach { level ->
            Mockito.`when`(search.findByOrcidId("0000-0005", level))
                .thenThrow(IllegalStateException("elasticsearch unreachable"))
        }
        assertEquals("0000-0005", resolver.requireKnown("0000-0005", ExpertIndexLevel.RAW))
        assertTrue(Mockito.mockingDetails(search).invocations.isEmpty())
        assertTrue(Mockito.mockingDetails(contacts).invocations.isEmpty())
    }

    @Test
    fun `lookup keys are queried in level then value order without duplicate layers`() {
        profileRow(exists = false)
        contactExists()
        assertThrows(ExpertProfileNotFoundException::class.java) {
            resolver.requireKnown("Email-Abc@x.com", ExpertIndexLevel.CANDIDATE)
        }
        val levels = searchCalls().map { it.first }
        assertEquals(listOf(
            ExpertIndexLevel.CANDIDATE, ExpertIndexLevel.CANDIDATE,
            ExpertIndexLevel.RAW, ExpertIndexLevel.RAW,
            ExpertIndexLevel.APPLICATION, ExpertIndexLevel.APPLICATION
        ), levels)
    }
}
