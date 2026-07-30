package com.weibo.talentintroduction.llm.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexCreateOutcome
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexDocument
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexListItem
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexPage
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexQualificationType
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexService
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexSourceMode
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexSourceType
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexStatus
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexUnavailableException
import com.weibo.talentintroduction.llm.service.ResolvedTrustReplySource
import com.weibo.talentintroduction.llm.service.TrustReplyItemGenerationKind
import com.weibo.talentintroduction.llm.service.TrustReplyItemHandling
import com.weibo.talentintroduction.llm.service.TrustReplyItemVersion
import com.weibo.talentintroduction.llm.service.TrustReplySourceRef
import com.weibo.talentintroduction.llm.service.TrustReplySourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.ResourceAccessException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

class UnsupportedAnswerIndexApiTest {
    private val mapper = ObjectMapper()
    private val properties = ElasticsearchProperties(
        baseUrl = "https://es.example.com:9200",
        username = "elastic",
        password = "secret",
        rawIndexName = "orcid_info",
        candidateIndexName = "orcid_info_candidate",
        applicationIndexName = "orcid_info_application"
    )
    private val indexUrl = "${properties.baseUrl}/${properties.unsupportedAnswerIndexName}"

    private fun service(): UnsupportedAnswerIndexService =
        UnsupportedAnswerIndexService(properties, mapper, RestTemplateBuilder())

    private fun mockServer(service: UnsupportedAnswerIndexService): MockRestServiceServer =
        MockRestServiceServer.bindTo(service.restTemplate).build()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun document(
        operatorInstruction: String = "Please say we will follow up next week.",
        answerText: String = "We will follow up next week."
    ) = UnsupportedAnswerIndexDocument(
        status = UnsupportedAnswerIndexStatus.CANDIDATE,
        sourceMode = UnsupportedAnswerIndexSourceMode.TRAINING,
        sourceType = UnsupportedAnswerIndexSourceType.TRAINING_MAIL,
        sourceId = 101L,
        sourceVersion = "training-101-v1",
        expertContactId = 202L,
        campaignId = 303L,
        requestKey = "TRAINING_MAIL-101-request-0",
        requestIndex = 0,
        requestText = "When will you follow up?",
        handling = "ANSWER_FROM_OPERATOR_INPUT",
        operatorInstruction = operatorInstruction,
        operatorInstructionHash = sha256(operatorInstruction),
        versionId = "canonical-version-1",
        answerText = answerText,
        answerHash = sha256(answerText),
        model = "DEEPSEEK_V4_FLASH",
        generationKind = "AI_GENERATED",
        qualificationType = UnsupportedAnswerIndexQualificationType.TRAINING_EVALUATION,
        qualificationId = "evaluation-1",
        approvedBy = "operator-1",
        createdAt = Instant.parse("2026-07-29T10:00:00Z")
    )

    @Test
    fun `mapping is strict with only V1 fields and non-searchable bodies`() {
        val mapping = mapper.readTree(ClassPathResource("es/trust_reply_unsupported_answer_v1.json").inputStream)
        assertEquals("strict", mapping.path("mappings").path("dynamic").asText())
        val fields = mapping.path("mappings").path("properties")
        val expected = setOf(
            "schemaVersion", "status", "sourceMode", "sourceType", "sourceId", "sourceVersion",
            "expertContactId", "campaignId", "requestKey", "requestIndex", "requestText", "handling",
            "operatorInstruction", "operatorInstructionHash", "versionId", "answerText", "answerHash",
            "model", "generationKind", "qualificationType", "qualificationId", "approvedBy", "createdAt"
        )
        assertEquals(expected, fields.fieldNames().asSequence().toSet())
        listOf("requestText", "operatorInstruction", "answerText").forEach { field ->
            assertEquals("text", fields.path(field).path("type").asText())
            assertFalse(fields.path(field).path("index").asBoolean(true))
        }
        listOf("translation", "translations", "vector", "embedding", "claims", "prompt").forEach { field ->
            assertFalse(fields.has(field), "mapping must not include $field")
        }
    }

    @Test
    fun `bootstrap creates only after HEAD 404`() {
        val existing = service()
        mockServer(existing)
            .expect(requestTo(indexUrl))
            .andExpect(method(HttpMethod.HEAD))
            .andRespond(withSuccess())
        existing.bootstrapIndex()

        val missing = service()
        val missingServer = mockServer(missing)
        missingServer
            .expect(requestTo(indexUrl))
            .andExpect(method(HttpMethod.HEAD))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        missingServer
            .expect(requestTo(indexUrl))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(jsonPath("$.mappings.dynamic").value("strict"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        missing.bootstrapIndex()

        listOf(HttpStatus.UNAUTHORIZED, HttpStatus.INTERNAL_SERVER_ERROR).forEach { status ->
            val unavailable = service()
            mockServer(unavailable)
                .expect(requestTo(indexUrl))
                .andExpect(method(HttpMethod.HEAD))
                .andRespond(withStatus(status))
            unavailable.bootstrapIndex()
        }
        val networkFailure = service()
        mockServer(networkFailure)
            .expect(requestTo(indexUrl))
            .andExpect(method(HttpMethod.HEAD))
            .andRespond { throw ResourceAccessException("network") }
        networkFailure.bootstrapIndex()
    }

    @Test
    fun `create validates canonical document and treats conflict as idempotent`() {
        val rejected = service().create(document().copy(answerHash = "bad"))
        assertEquals(UnsupportedAnswerIndexCreateOutcome.REJECTED, rejected.outcome)

        val service = service()
        val server = mockServer(service)
        val valid = document()
        val expectedId = sha256("${valid.sourceType}|${valid.sourceId}|${valid.requestKey}|${valid.versionId}")
        server.expect(requestTo("$indexUrl/_create/$expectedId"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(jsonPath("$.handling").value("ANSWER_FROM_OPERATOR_INPUT"))
            .andRespond(withStatus(HttpStatus.CREATED))
        server.expect(requestTo("$indexUrl/_create/$expectedId"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.CONFLICT))
        assertEquals(UnsupportedAnswerIndexCreateOutcome.CREATED, service.create(valid).outcome)
        assertEquals(UnsupportedAnswerIndexCreateOutcome.ALREADY_EXISTS, service.create(valid).outcome)
        server.verify()
    }

    @Test
    fun `training archive writes server qualification fields and summarizes partial results`() {
        val service = service()
        val source = ResolvedTrustReplySource(
            source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 101L),
            contact = ExpertContact(
                id = 202L,
                campaignId = 303L,
                orcidId = "0000-0001",
                expertEmail = "expert@example.com",
                expertName = "Dr. Test"
            ),
            inboundText = "When will you follow up?",
            subject = "Subject",
            messageId = "message-101",
            senderAccountCode = "sender-1",
            profileText = "profile",
            mailHistory = "history",
            contextWarnings = emptyList(),
            researchProfileSufficient = true,
            sourceVersion = "training-101-v1"
        )
        fun version(index: Int, id: String, answer: String) = TrustReplyItemVersion(
            versionId = id,
            requestKey = "training-request-$index",
            handling = TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT,
            answerText = answer,
            claims = emptyList(),
            model = "DEEPSEEK_V4_FLASH",
            generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
            evidenceSetVersion = "evidence-v1",
            sourceVersion = "training-101-v1",
            operatorInstructionHash = sha256("Please say $answer"),
            requestIndex = index,
            requestText = "When will you follow up?",
            operatorInstruction = "Please say $answer"
        )
        val first = version(0, "canonical-version-1", "We will follow up next week.")
        val second = version(1, "canonical-version-2", "We will follow up tomorrow.")
        val server = mockServer(service)
        val firstId = sha256("TRAINING_MAIL|101|training-request-0|canonical-version-1")
        val secondId = sha256("TRAINING_MAIL|101|training-request-1|canonical-version-2")
        server.expect(requestTo("$indexUrl/_create/$firstId"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(jsonPath("$.status").value("CANDIDATE"))
            .andExpect(jsonPath("$.sourceMode").value("TRAINING"))
            .andExpect(jsonPath("$.sourceId").value(101))
            .andExpect(jsonPath("$.expertContactId").value(202))
            .andExpect(jsonPath("$.campaignId").value(303))
            .andExpect(jsonPath("$.qualificationType").value("TRAINING_EVALUATION"))
            .andExpect(jsonPath("$.qualificationId").value("evaluation-55"))
            .andExpect(jsonPath("$.approvedBy").value("operator-1"))
            .andRespond(withStatus(HttpStatus.CREATED))
        server.expect(requestTo("$indexUrl/_create/$secondId"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val result = service.archiveCanonicalVersions(
            source = source,
            versions = listOf(first, second),
            qualificationId = "evaluation-55",
            approvedBy = "operator-1",
            createdAt = Instant.parse("2026-07-30T02:00:00Z")
        )

        assertEquals(com.weibo.talentintroduction.llm.service.UnsupportedAnswerArchiveStatus.PARTIAL, result.status)
        assertEquals(1, result.archivedCount)
        assertEquals(1, result.failedCount)
        server.verify()
    }

    @Test
    fun `list builds bounded fixed query and skips malformed hits`() {
        val service = service()
        val server = mockServer(service)
        server.expect(requestTo("$indexUrl/_search"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.track_total_hits").value(true))
            .andExpect(jsonPath("$.from").value(20))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.query.term.sourceMode").value("TRAINING"))
            .andExpect(jsonPath("$.sort[0].createdAt.order").value("desc"))
            .andExpect(jsonPath("$.sort[1]._id.order").value("asc"))
            .andRespond(withSuccess("""
                {"hits":{"total":{"value":2,"relation":"eq"},"hits":[
                  {"_id":"ok-1","_source":{"status":"CANDIDATE","sourceMode":"TRAINING","requestText":"Question","operatorInstruction":"Instruction","answerText":"Answer","model":"DEEPSEEK_V4_FLASH","createdAt":"2026-07-29T10:00:00Z"}},
                  {"_id":"bad-1","_source":{"status":"CANDIDATE"}}
                ]}}
            """.trimIndent(), MediaType.APPLICATION_JSON))
        val page = service.list(1, 20, UnsupportedAnswerIndexSourceMode.TRAINING)
        assertEquals(2L, page.total)
        assertEquals(1, page.items.size)
        assertEquals("Question", page.items.single().requestText)
        server.verify()
    }

    @Test
    fun `controller keeps list read-only and maps invalid and unavailable requests`() {
        val service = Mockito.mock(UnsupportedAnswerIndexService::class.java)
        val controller = UnsupportedAnswerIndexController(service)
        assertEquals(HttpStatus.BAD_REQUEST, controller.list(-1, null, null).statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, controller.list(0, 101, null).statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, controller.list(0, 20, "OTHER").statusCode)

        Mockito.`when`(service.list(0, 20, UnsupportedAnswerIndexSourceMode.TRAINING)).thenReturn(
            UnsupportedAnswerIndexPage(
                items = listOf(UnsupportedAnswerIndexListItem("CANDIDATE", "TRAINING", "Q", "I", "A", "model", "2026-07-29T10:00:00Z")),
                total = 1,
                page = 0,
                size = 20
            )
        )
        val ok = controller.list(0, 20, "TRAINING")
        assertEquals(HttpStatus.OK, ok.statusCode)
        assertTrue((ok.body as UnsupportedAnswerIndexPage).items.isNotEmpty())

        Mockito.`when`(service.list(0, 20, null)).thenThrow(UnsupportedAnswerIndexUnavailableException())
        val unavailable = controller.list(0, 20, null)
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unavailable.statusCode)
        assertEquals("UNSUPPORTED_ANSWER_INDEX_UNAVAILABLE", (unavailable.body as Map<*, *>)["code"])
    }
}
