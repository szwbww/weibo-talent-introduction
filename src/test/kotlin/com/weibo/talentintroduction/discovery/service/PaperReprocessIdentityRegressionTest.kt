package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.*
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.DiscoveryEmailHit
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant

/** Regression for #31982; exercises the actual apply gate without external writes. */
class PaperReprocessIdentityRegressionTest {
    private val mapper = jacksonObjectMapper()
    private inline fun <reified T> mock(): T = Mockito.mock(T::class.java)
    private fun ref(given: String?, family: String?) = mapper.createObjectNode()
        .put("givenNames", given).put("familyNames", family)
        .put("identityEvidence", "SOURCE_SHA256:" + "a".repeat(64))
    private val valid get() = ref("Alice", "Smith")

    private fun apply(refs: List<JsonNode>, rawPresent: Boolean = true, discovery: Boolean = true,
        existingName: String = "Alice", protected: Boolean = false): ObjectNode {
        val repository = mock<DiscoveryPaperReprocessRepository>()
        val writer = mock<ExpertIndexWriterService>()
        val consumer = mock<ExpertDiscoveryService>()
        val rechecks = mock<DiscoveryRecheckService>()
        val input = mapper.createObjectNode().put("email", "opaque@fixture.edu")
        input.set<JsonNode>("paperRefs", mapper.valueToTree(refs))
        val now = Instant.now()
        val parent = ReprocessRun(10, 1, "key", "hash", ReprocessMode.ENFORCE, ReprocessState.RUNNING,
            PaperReprocessPhase.APPLY, mapper.createObjectNode(), 1, "lease", now, 1, false, now, now, null)
        val target = ReprocessItem(1, 10, ReprocessKind.TARGET, "email-hash", ReprocessItemState.RUNNING,
            input, null, "item-lease", now, 1, 1)
        val result = mapper.createObjectNode().put("beforeAbsent", false).putNull("before")
        result.putArray("trace").also { trace -> repeat(6) { trace.addObject().put("state", "NOT_STARTED") } }
        Mockito.`when`(repository.receipt(parent, target, result)).thenReturn(true)
        val source = mutableMapOf<String, Any?>("givenNames" to existingName, "familyNames" to "Smith")
        if (discovery) source["emailSource"] = "PAPER_FULLTEXT"
        val level = if (protected) ExpertIndexLevel.APPLICATION else if (rawPresent) ExpertIndexLevel.RAW else ExpertIndexLevel.CANDIDATE
        Mockito.`when`(writer.findDiscoveryHitsByEmail("opaque@fixture.edu"))
            .thenReturn(listOf(DiscoveryEmailHit("doc", level, source)))
        if (rawPresent) {
            Mockito.`when`(writer.readDiscoveryDocument(ExpertIndexLevel.RAW, "doc"))
                .thenReturn(ExpertIndexWriterService.DiscoverySnapshot(source, 1, 1))
            Mockito.`when`(writer.discoveryProfile("doc", source))
                .thenReturn(com.weibo.talentintroduction.expert.domain.ExpertProfile(orcidId = "doc",
                    email = "opaque@fixture.edu", givenNames = existingName, familyNames = "Smith",
                    country = null, keyword = null, employment = null))
        }
        val service = DiscoveryPaperReprocessApplyService(repository, mock(), mock(), consumer, rechecks,
            writer, mock(), mock(), mock(), mapper, mock<JdbcTemplate>(),
            DefaultListableBeanFactory().getBeanProvider(DiscoveryVerificationWorker::class.java))
        val method = service.javaClass.getDeclaredMethod("applyTarget", ReprocessRun::class.java,
            ReprocessItem::class.java, ObjectNode::class.java, DiscoveryVerificationExecution::class.java)
        method.isAccessible = true
        method.invoke(service, parent, target, result, DiscoveryVerificationExecution(10))
        Mockito.verifyNoInteractions(consumer, rechecks)
        return result
    }

    @Test fun `null missing blank and partial names do not conflict with one complete source name`() {
        for (empty in listOf(ref(null, null), mapper.createObjectNode(), ref(" ", ""), ref("Alice", null))) {
            val result = apply(listOf(empty, valid))
            assertTrue(result.path("needsRecheck").asBoolean(), result.toString())
        }
    }

    @Test fun `null names never bind even with a proof token`() {
        assertEquals("IDENTITY_NOT_BOUND", apply(listOf(ref(null, null))).path("reasonCode").asText())
    }

    @Test fun `real source names or stable author IDs still conflict`() {
        for (refs in listOf(listOf(valid, ref("Bob", "Jones")),
            listOf(valid.put("orcidId", "id1"), valid.put("orcidId", "id2")),
            listOf(valid.put("openAlexAuthorId", "A1"), valid.put("openAlexAuthorId", "A2")))) {
            assertEquals("SOURCE_IDENTITY_CONFLICT", apply(refs).path("reasonCode").asText())
        }
        assertTrue(apply(listOf(valid.put("orcidId", ""), valid.put("orcidId", "id1")))
            .path("needsRecheck").asBoolean())
    }

    @Test fun `scope exclusion and absent raw have distinct reasons while true mismatch stays blocked`() {
        assertEquals("EXISTING_RAW_MISSING", apply(listOf(valid), rawPresent = false).path("reasonCode").asText())
        assertEquals("EXISTING_NOT_DISCOVERY", apply(listOf(valid), discovery = false).path("reasonCode").asText())
        assertEquals("EXISTING_IDENTITY_CONFLICT", apply(listOf(valid), existingName = "Bob").path("reasonCode").asText())
        assertEquals("BUSINESS_PROTECTED", apply(listOf(valid), protected = true).path("reasonCode").asText())
    }
}
