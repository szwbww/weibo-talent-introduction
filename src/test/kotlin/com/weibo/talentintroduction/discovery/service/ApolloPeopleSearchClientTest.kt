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
        val cases = listOf(401 to "CONFIG_AUTH", 403 to "CONFIG_AUTH", 400 to "REQUEST_INVALID", 422 to "APOLLO_HTTP_422_UNCLASSIFIED", 302 to "HTTP_ERROR")
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
            assertEquals("RATE_LIMIT", result.diagnostic!!.classification)
            assertEquals(header, result.diagnostic!!.retryAfter)
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
    @Test fun `422 classifications require explicit bounded provider evidence`() {
        val cases = listOf(
            """{"message":"insufficient credits"}""" to "APOLLO_CREDITS_EXHAUSTED",
            """{"error":{"error_code":"CREDITS_EXHAUSTED"}}""" to "APOLLO_CREDITS_EXHAUSTED",
            """{"error":"not enough credits"}""" to "APOLLO_CREDITS_EXHAUSTED",
            """{"message":"invalid parameter"}""" to "REQUEST_INVALID",
            """{"error":{"message":"validation error"}}""" to "REQUEST_INVALID",
            """{"message":"credits limit upgrade plan"}""" to "APOLLO_HTTP_422_UNCLASSIFIED",
            """{"people":[{"message":"insufficient credits"}]}""" to "APOLLO_HTTP_422_UNCLASSIFIED",
            "" to "APOLLO_HTTP_422_UNCLASSIFIED",
            "<html>insufficient credits</html>" to "APOLLO_HTTP_422_UNCLASSIFIED")
        cases.forEach { (body, expected) ->
            server.expect(anything()).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body(body).contentType(MediaType.APPLICATION_JSON))
            val result = client.search(ApolloSearchQuery("Ada Lee"), permit()) as VerificationHttpResult.PermanentFailure
            assertEquals(expected, result.code)
            val diagnostic = requireNotNull(result.diagnostic)
            assertEquals(expected, diagnostic.classification)
            assertEquals(body.toByteArray().size, diagnostic.bodyBytesRead)
            assertEquals("FULL", diagnostic.hashScope)
            assertEquals(java.security.MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString("") { "%02x".format(it) }, diagnostic.bodySha256)
            server.verify()
            server.reset()
        }
        assertEquals("REQUEST_INVALID", VerificationDiagnostics.classify(VerificationProvider.ORCID, 422,
            """{"message":"insufficient credits"}""".toByteArray()))
    }

    @Test fun `diagnostics whitelist and redact secrets and never retain arbitrary bodies`() {
        val body = """{"message":"invalid parameter email alice@example.invalid phone +1 (415) 555-0123 Bearer bearer-secret token=token-secret password:password-secret https://x.invalid/?foo=query-secret&bar=more-secret","people":[{"name":"private-person","email":"hidden@example.invalid"}],"token":"private-token"}"""
        server.expect(anything()).andRespond(withStatus(HttpStatus.BAD_REQUEST).body(body).contentType(MediaType.APPLICATION_JSON))
        val result = client.detail("person-1", permit()) as VerificationHttpResult.PermanentFailure
        val diagnostic = requireNotNull(result.diagnostic)
        val json = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(diagnostic)
        listOf("alice@example.invalid", "415", "bearer-secret", "token-secret", "password-secret", "query-secret",
            "more-secret", "private-person", "hidden@example.invalid", "private-token").forEach { assertFalse(json.contains(it), it) }
        assertFalse(com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(json).has("people"))
        assertEquals("/api/v1/people/person-1", diagnostic.endpointPath)
        server.verify()
        server.reset()
        server.expect(anything()).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("<html>private-token</html>").contentType(MediaType.TEXT_HTML))
        assertNull((client.detail("person-1", permit()) as VerificationHttpResult.PermanentFailure).diagnostic!!.message)
        server.verify()
    }

    @Test fun `quoted secret assignments are redacted before storage and on detail projection`() {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val messages = listOf(
            """invalid parameter {"token":"private-token"}""",
            """invalid parameter {"api_key":"private-key","password":"private-password"}""",
            """invalid parameter {'TOKEN' = 'private-token', 'key': 'private-key', 'password': 'private-password'}""")
        messages.forEach { message ->
            val body = mapper.writeValueAsString(mapOf("message" to message))
            server.expect(anything()).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body(body).contentType(MediaType.APPLICATION_JSON))
            val result = client.detail("person-1", permit()) as VerificationHttpResult.PermanentFailure
            assertEquals("REQUEST_INVALID", result.code)
            val diagnostic = requireNotNull(result.diagnostic)
            val json = mapper.writeValueAsString(diagnostic)
            val projection = requireNotNull(VerificationDiagnostics.read(json))
            val historicalProjection = requireNotNull(VerificationDiagnostics.read(
                mapper.writeValueAsString(diagnostic.copy(message = message))))
            listOf(json, mapper.writeValueAsString(projection), mapper.writeValueAsString(historicalProjection)).forEach { output ->
                listOf("private-token", "private-key", "private-password").forEach { secret ->
                    assertFalse(output.contains(secret), secret)
                }
                assertTrue(output.contains("[REDACTED]"))
            }
            assertEquals(diagnostic.message, projection.message)
            assertEquals(diagnostic.message, historicalProjection.message)
            server.verify()
            server.reset()
        }
    }

    @Test fun `large error reads only the bounded prefix and records its real hash`() {
        val body = """{"message":"token=private-token ${"x".repeat(20000)}"}"""
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body(body).contentType(MediaType.APPLICATION_JSON))
        val diagnostic = (client.detail("person-1", permit()) as VerificationHttpResult.PermanentFailure).diagnostic!!
        assertEquals(16385, diagnostic.bodyBytesRead)
        assertTrue(diagnostic.bodyTruncated)
        assertEquals("PREFIX", diagnostic.hashScope)
        assertNull(diagnostic.message)
        assertEquals(java.security.MessageDigest.getInstance("SHA-256").digest(body.toByteArray().copyOf(16385))
            .joinToString("") { "%02x".format(it) }, diagnostic.bodySha256)
        server.verify()
    }

    @Test fun `malformed and structurally invalid success diagnostics do not invent byte sizes`() {
        server.expect(anything()).andRespond(withSuccess("bad", MediaType.APPLICATION_JSON))
        val parsed = (client.detail("person-1", permit()) as VerificationHttpResult.PermanentFailure).diagnostic!!
        assertEquals(3, parsed.bodyBytesRead)
        assertNull(parsed.message)
        server.verify()
        server.reset()
        val body = """{ "person": { "id": "other" } }"""
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
        val structural = (client.detail("person-1", permit()) as VerificationHttpResult.PermanentFailure).diagnostic!!
        assertNull(structural.bodyBytesRead)
        assertEquals(com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.hash(body), structural.bodySha256)
        assertEquals("FULL", structural.hashScope)
        server.verify()
    }
}
