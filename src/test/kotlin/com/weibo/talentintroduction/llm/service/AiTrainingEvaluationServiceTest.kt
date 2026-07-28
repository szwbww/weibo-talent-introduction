package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.audit.domain.OperatorActionLog
import com.weibo.talentintroduction.audit.repository.OperatorActionLogRepository
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

class AiTrainingEvaluationServiceTest {
    private val workbenchService = Mockito.mock(TrustReplyWorkbenchService::class.java)
    private val operatorActionLogRepository = Mockito.mock(OperatorActionLogRepository::class.java)
    private lateinit var service: AiTrainingEvaluationService
    private lateinit var assembly: TrustReplyAssembleRequest
    private lateinit var assembled: TrustReplyAssembleResponse

    @BeforeEach
    fun setUp() {
        val source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 11L)
        val contact = ExpertContact(
            id = 17L,
            campaignId = 1L,
            orcidId = "0000-0001",
            expertEmail = "expert@example.com",
            expertName = "Dr. Test"
        )
        val resolved = ResolvedTrustReplySource(
            source = source,
            contact = contact,
            inboundText = "SECRET-INBOUND-27",
            subject = "SECRET-SUBJECT-27",
            messageId = "message-27",
            senderAccountCode = "sender-1",
            profileText = "profile",
            mailHistory = "history",
            contextWarnings = emptyList(),
            researchProfileSufficient = true,
            sourceVersion = "source-v1"
        )
        assembly = TrustReplyAssembleRequest(
            source = source,
            expectedSourceVersion = "source-v1",
            expectedEvidenceSetVersion = "evidence-v1",
            lockedItems = emptyList()
        )
        assembled = TrustReplyAssembleResponse(
            source = source,
            sourceVersion = "source-v1",
            evidenceSetVersion = "evidence-v1",
            rawDraftText = "SECRET-RAW-27",
            renderedDraftText = "SECRET-RENDERED-27",
            draftHash = AiReplyDraftService.sha256Hex("SECRET-RAW-27"),
            canonicalFactIds = listOf(4L),
            itemVersions = listOf(item("SECRET-ANSWER-27"))
        )
        Mockito.`when`(workbenchService.assemble(assembly)).thenReturn(assembled)
        Mockito.`when`(workbenchService.resolveSource(source)).thenReturn(resolved)
        var nextId = 100L
        Mockito.`when`(operatorActionLogRepository.save(Mockito.any(OperatorActionLog::class.java)))
            .thenAnswer { invocation ->
                val log = invocation.arguments[0] as OperatorActionLog
                log.copy(id = nextId++, createdAt = LocalDateTime.of(2026, 7, 28, 20, 0))
            }
        service = AiTrainingEvaluationService(
            workbenchService = workbenchService,
            operatorActionLogService = OperatorActionLogService(
                operatorActionLogRepository,
                ObjectMapper()
            )
        )
    }

    @Test
    fun `all ratings append one audit record and normalize operator fields`() {
        AiTrainingEvaluationRating.values().forEach { rating ->
            val result = service.save(
                AiTrainingEvaluationRequest(
                    assembly = assembly,
                    rating = rating.name,
                    note = "  note-${rating.name}  ",
                    operatorName = "  operator-a  "
                )
            )
            assertEquals(rating.name, result.rating)
            assertTrue(result.evaluationId > 0)
        }
        Mockito.verify(operatorActionLogRepository, Mockito.times(3))
            .save(Mockito.any(OperatorActionLog::class.java))
        val logs = Mockito.mockingDetails(operatorActionLogRepository).invocations
            .filter { it.method.name == "save" }
            .map { it.arguments[0] as OperatorActionLog }
        assertEquals(listOf("MEETS_EXPECTATION", "NEEDS_IMPROVEMENT", "UNUSABLE"), logs.map { it.afterValue!!.let { value -> ObjectMapper().readTree(value)["rating"].asText() } })
        assertEquals("operator-a", logs.first().operatorName)
        assertEquals("note-MEETS_EXPECTATION", logs.first().note)
        assertTrue(logs.all { it.expertContactId == 17L && it.inboundProcessingId == null })
    }

    @Test
    fun `invalid input and stale assembly never write an audit record`() {
        assertThrows(TrustReplyWorkbenchException::class.java) {
            service.save(AiTrainingEvaluationRequest(assembly, "UNKNOWN"))
        }
        assertThrows(TrustReplyWorkbenchException::class.java) {
            service.save(AiTrainingEvaluationRequest(assembly, "UNUSABLE", "x".repeat(1001)))
        }
        assertThrows(TrustReplyWorkbenchException::class.java) {
            service.save(AiTrainingEvaluationRequest(assembly, "UNUSABLE", operatorName = "x".repeat(129)))
        }
        val liveAssembly = assembly.copy(
            source = TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, 22L)
        )
        assertThrows(TrustReplyWorkbenchException::class.java) {
            service.save(AiTrainingEvaluationRequest(liveAssembly, "UNUSABLE"))
        }

        Mockito.`when`(workbenchService.assemble(assembly)).thenThrow(
            TrustReplyWorkbenchException(HttpStatus.CONFLICT, "TRUST_REPLY_EVIDENCE_STALE")
        )
        assertThrows(TrustReplyWorkbenchException::class.java) {
            service.save(AiTrainingEvaluationRequest(assembly, "UNUSABLE"))
        }
        Mockito.verify(operatorActionLogRepository, Mockito.never())
            .save(Mockito.any(OperatorActionLog::class.java))
    }

    @Test
    fun `snapshot is bounded and contains hashes instead of reply content`() {
        val items = (1..51).map { index ->
            item(
                answer = "SECRET-ANSWER-$index",
                requestKey = "request-$index-${"x".repeat(240)}",
                versionId = "version-$index-${"y".repeat(240)}",
                model = "model-$index-${"m".repeat(80)}"
            )
        }
        val response = assembled.copy(
            rawDraftText = "SECRET-RAW-51",
            renderedDraftText = "SECRET-RENDERED-51",
            itemVersions = items
        )
        val snapshot = service.buildSnapshot(response, AiTrainingEvaluationRating.UNUSABLE)
        val json = ObjectMapper().writeValueAsString(snapshot)
        val expectedKeys = setOf(
            "schemaVersion", "sourceVersion", "draftHash", "evidenceSetVersion", "rating",
            "requestCount", "handlingCounts", "models", "itemSnapshots", "itemTotal", "itemTruncated"
        )
        assertEquals(expectedKeys, snapshot.keys)
        assertEquals(51, snapshot["itemTotal"])
        assertEquals(true, snapshot["itemTruncated"])
        assertEquals(AiReplyDraftService.sha256Hex("SECRET-RAW-51"), snapshot["draftHash"])
        assertEquals(50, (snapshot["itemSnapshots"] as List<*>).size)
        assertEquals(5, (snapshot["models"] as List<*>).size)
        assertFalse(json.contains("SECRET-"))
        assertFalse(json.contains("answerText"))
        assertFalse(json.contains("operatorInstruction"))
        assertFalse(json.contains("claims"))
        val first = (snapshot["itemSnapshots"] as List<*>).first() as Map<*, *>
        assertEquals(setOf("requestKey", "handling", "versionId", "answerHash", "model", "generationKind"), first.keys)
        assertTrue((first["requestKey"] as String).length <= 200)
        assertEquals(AiReplyDraftService.sha256Hex("SECRET-ANSWER-1"), first["answerHash"])
        assertEquals(51, ((snapshot["handlingCounts"] as Map<*, *>)[TrustReplyItemHandling.ANSWER_WITH_EVIDENCE.name]))
    }

    private fun item(
        answer: String,
        requestKey: String = "request-key",
        versionId: String = "version-id",
        model: String = "DEEPSEEK_V4_FLASH"
    ): TrustReplyItemVersion = TrustReplyItemVersion(
        versionId = versionId,
        requestKey = requestKey,
        handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
        answerText = answer,
        claims = emptyList(),
        model = model,
        generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
        evidenceSetVersion = "evidence-v1",
        sourceVersion = "source-v1"
    )
}
