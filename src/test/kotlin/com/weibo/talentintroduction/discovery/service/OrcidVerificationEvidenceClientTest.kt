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

class OrcidVerificationEvidenceClientTest {
    @Test fun `only bound validated identity may request the allowlisted public endpoints`() {
        val props = DiscoveryVerificationProperties(orcidAccessToken = "private-token")
        val http = verificationRestTemplate(props)
        val server = MockRestServiceServer.bindTo(http).build()
        listOf("person", "employments", "educations").forEach { endpoint ->
            server.expect(requestTo("https://pub.orcid.org/v3.0/0000-0002-1825-0097/$endpoint"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer private-token"))
                .andExpect(header("Accept", "application/json"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        }
        var reservations = 0
        val client = OrcidVerificationEvidenceClient(props, http)
        val result = client.read(ruleSnapshot()) { reservations++; RequestPermit(reservations.toLong(), VerificationProvider.ORCID) }
        assertTrue(result.complete)
        assertEquals(3, reservations)
        val bad = ruleSnapshot().let { it.copy(profile = it.profile.copy(identityVerification = null)) }
        assertEquals(listOf("IDENTITY_EVIDENCE_MISSING"), client.read(bad) { fail("must not reserve") }.reasonCodes)
        val unrelated = ruleSnapshot().let { it.copy(profile = it.profile.copy(orcidId = "0000-0001-5109-3700")) }
        assertFalse(client.read(unrelated) { fail("must not reserve") }.complete)
        assertNull(normalizeOrcid("0000-0002-1825-0098"))
        assertNull(normalizeOrcid("https://evil.invalid/0000-0002-1825-0097"))
        server.verify()
    }
    @Test fun `404 is missing evidence whereas authentication rate limit timeout and invalid schemas are interface states`() {
        listOf(404, 401, 403, 429, 500).forEach { code ->
            val props = DiscoveryVerificationProperties()
            val http = verificationRestTemplate(props)
            val server = MockRestServiceServer.bindTo(http).build()
            repeat(if (code == 404) 3 else 1) { server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(code))) }
            val result = OrcidVerificationEvidenceClient(props, http).read(ruleSnapshot()) { RequestPermit(1, VerificationProvider.ORCID) }
            assertEquals(code == 404, result.complete)
            if (code != 404) assertNotNull(result.failure)
            server.verify()
        }
    }
    @Test fun `reservation denial prevents request and details never follow arbitrary response URLs`() {
        val props = DiscoveryVerificationProperties()
        val http = verificationRestTemplate(props)
        val server = MockRestServiceServer.bindTo(http).build()
        server.expect(requestTo("https://pub.orcid.org/v3.0/0000-0002-1825-0097/person")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://pub.orcid.org/v3.0/0000-0002-1825-0097/employments")).andRespond(withSuccess("""{"affiliation-group":[{"summaries":[{"employment-summary":{"put-code":42,"url":{"value":"http://127.0.0.1/secret"}}}]}]}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://pub.orcid.org/v3.0/0000-0002-1825-0097/educations")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://pub.orcid.org/v3.0/0000-0002-1825-0097/employment/42")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        val client = OrcidVerificationEvidenceClient(props, http)
        assertFalse(client.read(ruleSnapshot()) { null }.complete)
        var count = 0
        assertTrue(client.read(ruleSnapshot()) { count++; RequestPermit(count.toLong(), VerificationProvider.ORCID) }.complete)
        assertEquals(4, count)
        server.verify()
    }
    @Test fun `twelve request hard cap includes item details`() {
        val props = DiscoveryVerificationProperties(maxRequestsPerItem = 100)
        val http = verificationRestTemplate(props)
        val server = MockRestServiceServer.bindTo(http).build()
        server.expect(anything()).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        val summaries = (1..20).joinToString(",") { "{\"employment-summary\":{\"put-code\":$it}}" }
        server.expect(anything()).andRespond(withSuccess("{\"affiliation-group\":[{\"summaries\":[$summaries]}]}", MediaType.APPLICATION_JSON))
        repeat(10) { server.expect(anything()).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON)) }
        var count = 0
        val result = OrcidVerificationEvidenceClient(props, http).read(ruleSnapshot()) { count++; RequestPermit(count.toLong(), VerificationProvider.ORCID) }
        assertFalse(result.complete)
        assertEquals(12, count)
        server.verify()
    }

    @Test fun `durable endpoint completion resumes details after client reconstruction without repeating completed HTTP`() {
        val props = DiscoveryVerificationProperties()
        val http = verificationRestTemplate(props)
        val server = MockRestServiceServer.bindTo(http).build()
        fun expect(endpoint: String, body: String) = server.expect(requestTo("https://pub.orcid.org/v3.0/0000-0002-1825-0097/$endpoint"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
        expect("person", """{"biography":"must-not-persist","name":{"given-names":{"value":"Ada"}}}""")
        expect("employments", """{"affiliation-group":[{"summaries":[{"employment-summary":{"put-code":42}}]}]}""")
        server.expect(requestTo("https://pub.orcid.org/v3.0/0000-0002-1825-0097/educations")).andRespond(withServerError())
        val saved = mutableListOf<OrcidEndpointCompletion>()
        var requests = 0
        val first = OrcidVerificationEvidenceClient(props, http).read(ruleSnapshot(), saved, { endpoint, _, document, result ->
            if (result is VerificationHttpResult.Success) saved += OrcidEndpointCompletion(endpoint, document)
        }) { requests++; RequestPermit(requests.toLong(), VerificationProvider.ORCID) }
        assertFalse(first.complete)
        assertEquals(listOf("person", "employments"), saved.map { it.endpoint })
        assertFalse(saved.first().document!!.body.has("biography"))
        server.verify()
        val resumedHttp = verificationRestTemplate(props)
        val resumed = MockRestServiceServer.bindTo(resumedHttp).build()
        listOf("educations", "employment/42").forEach { endpoint ->
            resumed.expect(requestTo("https://pub.orcid.org/v3.0/0000-0002-1825-0097/$endpoint")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        }
        val result = OrcidVerificationEvidenceClient(props, resumedHttp).read(ruleSnapshot(), saved, { endpoint, _, document, _ ->
            saved += OrcidEndpointCompletion(endpoint, document)
        }) { requests++; RequestPermit(requests.toLong(), VerificationProvider.ORCID) }
        assertTrue(result.complete)
        assertEquals(5, requests)
        assertEquals(listOf("person", "employments", "educations", "employment/42"), result.documents.map { it.endpoint })
        resumed.verify()
    }

    @Test fun `completion persistence failure prevents the next request including after missing evidence`() {
        listOf(200, 404).forEach { status ->
            val props = DiscoveryVerificationProperties()
            val http = verificationRestTemplate(props)
            val server = MockRestServiceServer.bindTo(http).build()
            server.expect(requestTo("https://pub.orcid.org/v3.0/0000-0002-1825-0097/person"))
                .andRespond(if (status == 200) withSuccess("{}", MediaType.APPLICATION_JSON) else withStatus(HttpStatus.NOT_FOUND))
            var count = 0
            assertThrows(IllegalStateException::class.java) {
                OrcidVerificationEvidenceClient(props, http).read(ruleSnapshot(), emptyList(), { _, _, _, _ -> error("SQL failure") }) {
                    count++; RequestPermit(count.toLong(), VerificationProvider.ORCID)
                }
            }
            assertEquals(1, count)
            server.verify()
        }
    }
}
