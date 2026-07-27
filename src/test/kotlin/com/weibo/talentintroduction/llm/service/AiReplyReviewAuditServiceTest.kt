package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.audit.domain.OperatorActionLog
import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

class AiReplyReviewAuditServiceTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private fun svc(logService: OperatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)) =
        AiReplyReviewAuditService(logService)

    @Test
    fun `recordInitialDraft writes READY action with minimal after JSON`() {
        val logService = Mockito.mock(OperatorActionLogService::class.java)
        Mockito.`when`(logService.record(
            anyNonNull(""), anyNonNull(0L), anyNonNull(OperatorActionType.AI_REPLY_DRAFT_READY),
            anyNullable(), anyNullable(), anyNullable(),
            anyNullable(), anyNullable(), anyNullable(), anyNullable()
        )).thenReturn(
            OperatorActionLog(id = 42L, targetType = "x", targetId = 1L, actionType = "x", actionSummary = "x")
        )
        val s = svc(logService)
        val result = simpleResult(AiReplyDraftReadiness.READY)

        s.recordInitialDraft(1L, 10L, result, "op")

        @Suppress("UNCHECKED_CAST")
        val afterCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any?>>
        Mockito.verify(logService).record(
            anyNonNull(""), eqValue(1L), eqValue(OperatorActionType.AI_REPLY_DRAFT_READY),
            eqValue(10L), eqValue(1L),
            anyNullable(), afterCaptor.capture(),
            eqValue("op"), anyNonNull(""), anyNullable()
        )
        val after = afterCaptor.value!!
        assertEquals("READY", after["readiness"])
        assertEquals("FREE_FORM", after["mode"])
        assertFalse(after.containsKey("draftIdentity"))
        assertFalse(after.containsKey("unresolvedSnapshot"))
        assertFalse(after.containsKey("unresolvedCount"))
        assertFalse(after.containsKey("draftText"))
        assertEquals("ai-reply-draft-audit-v2", after["schemaVersion"])
        assertTrue(after.containsKey("validationDiagnostics"))
    }

    @Test
    fun `audit projects bounded diagnostics without sensitive content`() {
        val logService = Mockito.mock(OperatorActionLogService::class.java)
        val s = svc(logService)
        val diagnostics = AiReplyValidationDiagnostics.from(List(25) {
            AiReplyValidationDiagnostic(AiReplyValidationAttempt.INITIAL, AiReplyValidationStage.CLAIM, "CODE", "r1:key")
        })
        val snapshot = s.buildSnapshot(simpleResult(AiReplyDraftReadiness.BLOCKED, diagnostics))
        @Suppress("UNCHECKED_CAST")
        val items = snapshot.validationDiagnostics["items"] as List<Map<String, Any?>>
        assertEquals(1, items.size)
        assertEquals(1, snapshot.validationDiagnostics["total"])
        assertEquals(false, snapshot.validationDiagnostics["truncated"])
        assertEquals(setOf("attempt", "stage", "code", "claimKey"), items.single().keys)
        assertFalse(snapshot.validationDiagnostics.toString().contains("draft body should not be logged"))
    }

    @Test
    fun `audit bounds distinct diagnostics and preserves every legacy after key`() {
        val logService = Mockito.mock(OperatorActionLogService::class.java)
        val s = svc(logService)
        val diagnostics = AiReplyValidationDiagnostics.from(List(25) { index ->
            AiReplyValidationDiagnostic(
                AiReplyValidationAttempt.REPAIR,
                AiReplyValidationStage.values()[index % AiReplyValidationStage.values().size],
                "CODE_$index",
                "claim-$index"
            )
        })
        val result = simpleResult(AiReplyDraftReadiness.BLOCKED, diagnostics)

        val recorded = s.recordInitialDraft(9L, 90L, result, "op")

        @Suppress("UNCHECKED_CAST")
        val afterCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any?>>
        Mockito.verify(logService).record(
            anyNonNull(""), eqValue(9L), eqValue(OperatorActionType.AI_REPLY_DRAFT_BLOCKED),
            eqValue(90L), eqValue(9L), anyNullable(), afterCaptor.capture(),
            eqValue("op"), anyNonNull(""), anyNullable()
        )
        val after = afterCaptor.value!!
        val legacyKeys = setOf(
            "schemaVersion", "observedAt", "draftHash", "model", "mode", "promptVersion",
            "evidenceSetVersion", "evidenceSources", "sourceTotal", "sourceTruncated",
            "requestCount", "groundedRequestCount", "requestCoverage", "coverageTotal",
            "coverageTruncated", "readiness", "generationState", "usedLlm", "warningCodes",
            "warningTotal", "warningTruncated", "fewShotRefs", "fewShotTotal", "fewShotTruncated"
        )
        assertEquals(legacyKeys + "validationDiagnostics", after.keys)
        assertEquals(recorded.schemaVersion, after["schemaVersion"])
        assertEquals(recorded.observedAt, after["observedAt"])
        assertEquals(recorded.draftHash, after["draftHash"])
        assertEquals(recorded.model, after["model"])
        assertEquals(recorded.mode, after["mode"])
        assertEquals(recorded.promptVersion, after["promptVersion"])
        assertEquals(recorded.evidenceSetVersion, after["evidenceSetVersion"])
        assertEquals(recorded.evidenceSources, after["evidenceSources"])
        assertEquals(recorded.sourceTotal, after["sourceTotal"])
        assertEquals(recorded.sourceTruncated, after["sourceTruncated"])
        assertEquals(recorded.requestCount, after["requestCount"])
        assertEquals(recorded.groundedRequestCount, after["groundedRequestCount"])
        assertEquals(recorded.requestCoverage, after["requestCoverage"])
        assertEquals(recorded.coverageTotal, after["coverageTotal"])
        assertEquals(recorded.coverageTruncated, after["coverageTruncated"])
        assertEquals(recorded.readiness, after["readiness"])
        assertEquals(recorded.generationState, after["generationState"])
        assertEquals(recorded.usedLlm, after["usedLlm"])
        assertEquals(recorded.warningCodes, after["warningCodes"])
        assertEquals(recorded.warningTotal, after["warningTotal"])
        assertEquals(recorded.warningTruncated, after["warningTruncated"])
        assertEquals(recorded.fewShotRefs, after["fewShotRefs"])
        assertEquals(recorded.fewShotTotal, after["fewShotTotal"])
        assertEquals(recorded.fewShotTruncated, after["fewShotTruncated"])

        @Suppress("UNCHECKED_CAST")
        val validation = after["validationDiagnostics"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val items = validation["items"] as List<Map<String, Any?>>
        assertEquals(20, items.size)
        assertEquals(25, validation["total"])
        assertEquals(true, validation["truncated"])
        assertTrue(items.all { it.keys == setOf("attempt", "stage", "code", "claimKey") })
        assertFalse(after.toString().contains("draft body should not be logged"))
    }

    @Test
    fun `recordInitialDraft writes NEEDS_REVIEW action`() {
        val logService = Mockito.mock(OperatorActionLogService::class.java)
        val s = svc(logService)

        s.recordInitialDraft(2L, 20L, simpleResult(AiReplyDraftReadiness.NEEDS_REVIEW), "op")

        Mockito.verify(logService).record(
            anyNonNull(""), eqValue(2L), eqValue(OperatorActionType.AI_REPLY_DRAFT_NEEDS_REVIEW),
            eqValue(20L), eqValue(2L),
            anyNullable(), anyNullable(),
            eqValue("op"), anyNonNull(""), anyNullable()
        )
    }

    @Test
    fun `recordInitialDraft writes BLOCKED action`() {
        val logService = Mockito.mock(OperatorActionLogService::class.java)
        val s = svc(logService)

        s.recordInitialDraft(3L, 30L, simpleResult(AiReplyDraftReadiness.BLOCKED), "op")

        Mockito.verify(logService).record(
            anyNonNull(""), eqValue(3L), eqValue(OperatorActionType.AI_REPLY_DRAFT_BLOCKED),
            eqValue(30L), eqValue(3L),
            anyNullable(), anyNullable(),
            eqValue("op"), anyNonNull(""), anyNullable()
        )
    }

    @Test
    fun `recordInitialDraft swallows log service failures`() {
        val logService = Mockito.mock(OperatorActionLogService::class.java)
        Mockito.doThrow(RuntimeException("DB error")).`when`(logService).record(
            anyNonNull(""), anyNonNull(0L), anyNonNull(OperatorActionType.AI_REPLY_DRAFT_READY),
            anyNullable(), anyNullable(), anyNullable(),
            anyNullable(), anyNullable(), anyNullable(), anyNullable()
        )
        val s = svc(logService)

        assertDoesNotThrow {
            s.recordInitialDraft(1L, 1L, simpleResult(AiReplyDraftReadiness.READY), "op")
        }
    }

    private fun simpleResult(
        readiness: AiReplyDraftReadiness,
        diagnostics: AiReplyValidationDiagnostics = AiReplyValidationDiagnostics()
    ) = AiReplyDraftResult(
        draftText = "draft body should not be logged",
        usedLlm = true,
        qaRuleIds = emptyList(),
        mode = AiReplyMode.FREE_FORM,
        requestFacts = emptyList(),
        draftReadiness = readiness,
        requestCount = 2,
        groundedRequestCount = 1,
        generationState = AiReplyGenerationState.LLM_USED,
        selectedModel = "DEEPSEEK_V4_FLASH",
        validationDiagnostics = diagnostics
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNullable(): T = Mockito.any<T>() ?: null as T

    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value
}
