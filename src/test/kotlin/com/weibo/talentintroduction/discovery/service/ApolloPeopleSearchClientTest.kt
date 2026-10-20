package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.domain.VerificationProvider
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ApolloPeopleSearchClientTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC)
    private val properties = DiscoveryVerificationProperties(apolloApiKey = "secret")
    private val http = verificationRestTemplate(properties)
    private val server = MockRestServiceServer.bindTo(http).build()
    private val client = ApolloPeopleSearchClient(properties, http, clock)
    private fun permit() = RequestPermit(1, VerificationProvider.APOLLO)

    @Test fun `detail preserves history and academic text but omits contact data`() {
        server.expect(requestTo("https://api.apollo.io/api/v1/people/person-1"))
            .andExpect(method(HttpMethod.GET)).andExpect(header("X-Api-Key", "secret"))
            .andRespond(withSuccess("""{"person":{"id":"person-1","first_name":"Ada","last_name":"Lee","name":"Ada Lee","title":"Professor of Physics","headline":"Quantum optics","organization":{"name":"New University"},"email":"private@example.invalid","employment_history":[{"organization_name":"Old University","title":"Researcher","current":false,"start_date":"2010-01-01","end_date":"2020-01-01","major":"Physics"}]}}""", MediaType.APPLICATION_JSON))
        val result = client.detail("person-1", permit()) as VerificationHttpResult.Success
        assertEquals("Old University", result.value.employmentHistory.single().institution!!.name)
        assertEquals("Quantum optics", result.value.headline)
        assertFalse(result.toString().contains("private@example.invalid"))
        server.verify()
    }
    @Test fun `detail refuses a response for a different person`() {
        server.expect(anything()).andRespond(withSuccess("""{"person":{"id":"other"}}""", MediaType.APPLICATION_JSON))
        assertEquals("RESPONSE_INVALID", (client.detail("person-1", permit()) as VerificationHttpResult.PermanentFailure).code)
        server.verify()
    }
    @Test fun `only encoded search parameters and authentication go on the wire`() {
        server.expect(requestTo("https://api.apollo.io/api/v1/mixed_people/api_search?q_person_name=Ada%20%26%20Lee&page=2&per_page=100&q_organization_name=Example%20University"))
            .andExpect(method(HttpMethod.POST)).andExpect(header("X-Api-Key", "secret"))
            .andExpect(header("Accept", "application/json")).andExpect(content().string(""))
            .andRespond(withSuccess("""{"people":[{"id":"a","first_name":"Ada","last_name_obfuscated":"L**","title":"Professor","organization":{"name":"Example University"},"email":"never-retained@example.invalid"}],"total_entries":101}""", MediaType.APPLICATION_JSON))
        val result = client.search(ApolloSearchQuery("Ada & Lee", 2, VerificationInstitution("Example University")), permit()) as VerificationHttpResult.Success
        assertEquals("L**", result.value.candidates.single().obfuscatedLastName)
        assertFalse(result.value.hasNext)
        assertFalse(result.toString().contains("never-retained"))
        server.verify()
    }
    @Test fun `status mapping never becomes an empty successful search and requests are not retried`() {
        val cases = listOf(401 to "CONFIG_AUTH", 403 to "CONFIG_AUTH", 400 to "REQUEST_INVALID", 422 to "REQUEST_INVALID", 302 to "HTTP_ERROR")
        cases.forEach { (status, code) ->
            server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status)))
            assertEquals(code, (client.search(ApolloSearchQuery("Ada Lee"), permit()) as VerificationHttpResult.PermanentFailure).code)
            server.verify()
            server.reset()
        }
        server.expect(anything()).andRespond(withServerError())
        assertTrue(client.search(ApolloSearchQuery("Ada Lee"), permit()) is VerificationHttpResult.TransientFailure)
        server.verify()
    }
    @Test fun `rate limits parse seconds dates and fallback without transient retries`() {
        listOf("120" to 120L, "Sun, 18 Oct 2026 00:03:00 GMT" to 180L, "invalid" to 60L).forEach { (header, seconds) ->
            server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(org.springframework.http.HttpHeaders().apply { set("Retry-After", header) }))
            val result = client.search(ApolloSearchQuery("Ada Lee"), permit()) as VerificationHttpResult.RateLimited
            assertEquals(clock.instant().plusSeconds(seconds), result.retryAt)
            server.verify()
            server.reset()
        }
        server.verify()
    }
    @Test fun `malformed success and oversized responses are rejected`() {
        listOf("{}", "{\"people\":[],\"total_entries\":\"0\"}", "{\"people\":[{}],\"total_entries\":1}", "[]", "bad", "{\"people\":[],\"total_entries\":1.5}").forEach { body ->
            server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
            assertEquals("RESPONSE_INVALID", (client.search(ApolloSearchQuery("Ada Lee"), permit()) as VerificationHttpResult.PermanentFailure).code)
            server.verify()
            server.reset()
        }
        server.expect(anything()).andRespond(withSuccess(" ".repeat(2 * 1024 * 1024 + 1), MediaType.APPLICATION_JSON))
        assertEquals("RESPONSE_TOO_LARGE", (client.search(ApolloSearchQuery("Ada Lee"), permit()) as VerificationHttpResult.PermanentFailure).code)
        server.verify()
    }
    @Test fun `timeouts are typed and consumed permits cannot be reused or cross providers`() {
        server.expect(anything()).andRespond { throw java.net.SocketTimeoutException("secret endpoint") }
        val permit = permit()
        assertTrue(client.search(ApolloSearchQuery("Ada Lee"), permit) is VerificationHttpResult.TransientFailure)
        assertThrows(IllegalArgumentException::class.java) { client.search(ApolloSearchQuery("Ada Lee"), permit) }
        assertThrows(IllegalArgumentException::class.java) { client.search(ApolloSearchQuery("Ada Lee"), RequestPermit(2, VerificationProvider.ORCID)) }
        server.verify()
    }
    @Test fun `wide union survives narrow empty result and page five cannot complete a continuing search`() {
        val candidate = ApolloCandidate("a", "Ada", null, "L**", null, null, VerificationInstitution("Example University"))
        val broad = ApolloSearchEvidence().merge(ApolloSearchPage(listOf(candidate), 1, 1), ApolloSearchQuery("Ada Lee"), true)
        val narrow = broad.merge(ApolloSearchPage(emptyList(), 0, 1), ApolloSearchQuery("Ada Lee", institution = VerificationInstitution("Other University")), false, true)
        assertEquals(listOf(candidate), narrow.candidates)
        assertTrue(narrow.broadComplete && narrow.complete)
        val incomplete = ApolloSearchEvidence().merge(ApolloSearchPage(listOf(candidate), 501, 5), ApolloSearchQuery("Ada Lee", 5), true, true)
        assertFalse(incomplete.complete || incomplete.broadComplete)
    }
}
