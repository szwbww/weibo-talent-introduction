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

    private fun simpleResult(readiness: AiReplyDraftReadiness) = AiReplyDraftResult(
        draftText = "draft body should not be logged",
        usedLlm = true,
        qaRuleIds = emptyList(),
        mode = AiReplyMode.FREE_FORM,
        requestFacts = emptyList(),
        draftReadiness = readiness,
        requestCount = 2,
        groundedRequestCount = 1,
        generationState = AiReplyGenerationState.LLM_USED,
        selectedModel = "DEEPSEEK_V4_FLASH"
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNullable(): T = Mockito.any<T>() ?: null as T

    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value
}
