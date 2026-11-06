package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.discovery.controller.DiscoveryEmailRoutingController
import com.weibo.talentintroduction.expert.domain.EmailRouting
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.server.ResponseStatusException

class DiscoveryEmailRoutingServiceTest {
    private val writer = mock(ExpertIndexWriterService::class.java)
    private val service = DiscoveryEmailRoutingService(writer, EmailRoutingService(MxLookupClient { error("unexpected DNS") }))
    private val raw = ExpertIndexWriterService.DiscoverySnapshot(mapOf("email" to "p@outlook.com",
        "emailSource" to "PAPER_FULLTEXT", "tags" to listOf("discovered")), 7, 1)
    private fun <T> anyValue(): T = any<T>()

    private fun prepare() {
        `when`(writer.readDiscoveryDocument(ExpertIndexLevel.RAW, "EMAIL-test")).thenReturn(raw)
        doReturn(true).`when`(writer).writeEmailRoutingIfVersionMatches(anyValue(), anyValue(), anyValue(), anyValue())
    }

    @Test fun `historical refresh updates present layers but never creates missing ones`() {
        prepare()
        `when`(writer.readDiscoveryDocument(ExpertIndexLevel.CANDIDATE, "EMAIL-test")).thenReturn(raw)
        val result = service.refresh("EMAIL-test")
        assertEquals(setOf("RAW", "CANDIDATE"), result.updatedLayers.toSet())
        assertEquals(listOf("APPLICATION"), result.missingLayers)
        assertTrue(result.failedLayers.isEmpty())
        verify(writer, times(2)).writeEmailRoutingIfVersionMatches(anyValue(), anyValue(), anyValue(), anyValue())
    }

    @Test fun `different domain in another layer is never overwritten`() {
        prepare()
        `when`(writer.readDiscoveryDocument(ExpertIndexLevel.CANDIDATE, "EMAIL-test"))
            .thenReturn(raw.copy(source = raw.source + ("email" to "p@elsewhere.edu")))
        val result = service.refresh("EMAIL-test")
        assertEquals("EMAIL_CHANGED", result.failedLayers["CANDIDATE"])
        assertEquals(listOf("RAW"), result.updatedLayers)
        verify(writer, times(1)).writeEmailRoutingIfVersionMatches(anyValue(), anyValue(), anyValue(), anyValue())
    }

    @Test fun `RAW conflict stops propagation and is reported as conflict`() {
        prepare()
        doThrow(HttpClientErrorException(HttpStatus.CONFLICT)).`when`(writer)
            .writeEmailRoutingIfVersionMatches(anyValue(), anyValue(), anyValue(), anyValue())
        val result = service.refresh("EMAIL-test")
        assertEquals(mapOf("RAW" to "VERSION_CONFLICT"), result.failedLayers)
        assertTrue(result.updatedLayers.isEmpty())
        verify(writer, never()).readDiscoveryDocument(ExpertIndexLevel.CANDIDATE, "EMAIL-test")
    }

    @Test fun `missing RAW and unsafe document IDs fail without DNS or write`() {
        assertThrows(ResponseStatusException::class.java) { service.refresh("missing") }
        assertThrows(IllegalArgumentException::class.java) { service.refresh("a/_update?x") }
        verify(writer, never()).writeEmailRoutingIfVersionMatches(anyValue(), anyValue(), anyValue(), anyValue())
    }

    @Test fun `controller requires login and uses only requested document ID`() {
        val controller = DiscoveryEmailRoutingController(service)
        val request = MockHttpServletRequest()
        val body = DiscoveryEmailRoutingController.RefreshRequest("EMAIL-test")
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException::class.java) { controller.refresh(request, body) }.status)
        verifyNoInteractions(writer)
        request.getSession(true).setAttribute(AuthSessionKeys.USERNAME, "admin")
        prepare()
        assertEquals("EMAIL-test", (controller.refresh(request, body) as EmailRoutingRefreshResult).docId)
    }

    @Test fun `serialized evidence is domain bound and old documents remain unchecked`() {
        val mapper = jacksonObjectMapper()
        val evidence = EmailRoutingService(MxLookupClient { error("DNS") }).identify("p@outlook.com")!!
        assertEquals(evidence, EmailRouting.read(mapper.valueToTree(evidence), "other@OUTLOOK.COM"))
        assertNull(EmailRouting.read(mapper.valueToTree(evidence), "p@other.edu"))
        assertNull(EmailRouting.read(mapper.nullNode(), "p@outlook.com"))
        assertThrows(IllegalArgumentException::class.java) { EmailRouting.filterQuery("NOT_MICROSOFT") }
        val filter = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(EmailRouting.filterQuery("MICROSOFT"))
        assertTrue(filter.toString().contains("emailRouting.status"))
        assertTrue(filter.toString().contains("emailRouting.domain"))
        assertTrue(EmailRouting.filterQuery("UNCHECKED").toString().contains("must_not"))
    }

    @Test fun `new filter does not change canonical legacy recheck serialization when absent`() {
        val mapper = jacksonObjectMapper()
        assertEquals("{\"q\":null,\"issue\":null,\"decision\":null}", mapper.writeValueAsString(RecheckFilter()))
        assertEquals("MICROSOFT", mapper.readTree(mapper.writeValueAsString(RecheckFilter(emailRouting = "MICROSOFT"))).path("emailRouting").asText())
    }
}
