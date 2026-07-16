package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.audit.domain.OperatorActionLog
import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.repository.OperatorActionLogRepository
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

class AiReplyReviewAuditServiceTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private fun svc(
        repo: OperatorActionLogRepository = Mockito.mock(OperatorActionLogRepository::class.java),
        logService: OperatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)
    ) = AiReplyReviewAuditService(logService, repo)

    // -- validateConfirmationForSend: no draft, READY bypass --

    @Test
    fun `gate bypasses when no AI draft record exists`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(null)
        val s = svc(repo = repo)

        assertDoesNotThrow {
            s.validateConfirmationForSend(1L, null, emptyList(), "")
        }
    }

    @Test
    fun `gate bypasses when latest draft is READY`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        val record = auditLog(1L, readyAfterJson())
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(record)
        val s = svc(repo = repo)

        assertDoesNotThrow {
            s.validateConfirmationForSend(1L, null, emptyList(), "")
        }
    }

    // -- non-READY: identity required --

    @Test
    fun `gate rejects when non-READY draft exists but no identity provided`() {
        val repo = repoWithNeedsReview("id-abc")
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, null, emptyList(), "")
        }
        assertTrue(ex.message!!.contains("must provide draftIdentity"))
    }

    @Test
    fun `gate rejects when non-READY draft exists but identity is blank`() {
        val repo = repoWithNeedsReview("id-abc")
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "   ", emptyList(), "")
        }
        assertTrue(ex.message!!.contains("must provide draftIdentity"))
    }

    @Test
    fun `gate rejects when identity does not match stored draft identity`() {
        val repo = repoWithNeedsReview("id-abc")
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "wrong-id", listOf("1:a"), "")
        }
        assertTrue(ex.message!!.contains("does not match"))
    }

    @Test
    fun `gate rejects when stored draft identity is missing from audit record`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        val afterJson = objectMapper.writeValueAsString(mapOf(
            "readiness" to "NEEDS_REVIEW",
            "unresolvedSnapshot" to listOf(
                mapOf("reviewKey" to "1:a", "requestIndex" to 1, "intentKey" to "a")
            )
        ))
        val record = auditLog(1L, afterJson)
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(record)
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "any-id", emptyList(), "")
        }
        assertTrue(ex.message!!.contains("has no draftIdentity"))
    }

    // -- valid identity: key & note checks --

    @Test
    fun `gate rejects duplicate confirmed keys when identity matches`() {
        val repo = repoWithNeedsReview("id-abc")
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "id-abc", listOf("1:a", "1:a"), "")
        }
        assertTrue(ex.message!!.contains("Duplicate"))
    }

    @Test
    fun `gate rejects extra confirmed keys when identity matches`() {
        val repo = repoWithNeedsReview("id-abc")
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "id-abc", listOf("1:a", "2:unknown"), "")
        }
        assertTrue(ex.message!!.contains("Unknown"))
    }

    @Test
    fun `gate rejects missing keys when identity matches`() {
        val repo = repoWithTwoUnresolved("id-abc")
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "id-abc", listOf("1:a"), "")
        }
        assertTrue(ex.message!!.contains("Missing"))
    }

    @Test
    fun `gate succeeds when identity matches and all keys confirmed`() {
        val repo = repoWithNeedsReview("id-abc")
        val s = svc(repo = repo)

        assertDoesNotThrow {
            s.validateConfirmationForSend(1L, "id-abc", listOf("1:a"), "")
        }
    }

    @Test
    fun `gate rejects BLOCKED with insufficient note`() {
        val repo = repoWithBlocked("id-blocked")
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "id-blocked", listOf("1:a"), "ab")
        }
        assertTrue(ex.message!!.contains("at least 5"))
    }

    @Test
    fun `gate accepts BLOCKED with sufficient note`() {
        val repo = repoWithBlocked("id-blocked")
        val s = svc(repo = repo)

        assertDoesNotThrow {
            s.validateConfirmationForSend(1L, "id-blocked", listOf("1:a"), "Verified ok")
        }
    }

    @Test
    fun `gate rejects duplicate reviewKeys in canonical snapshot`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        val afterJson = objectMapper.writeValueAsString(mapOf(
            "draftIdentity" to "id-dup",
            "readiness" to "NEEDS_REVIEW",
            "unresolvedSnapshot" to listOf(
                mapOf("reviewKey" to "1:a", "requestIndex" to 1, "intentKey" to "a"),
                mapOf("reviewKey" to "1:a", "requestIndex" to 1, "intentKey" to "a")
            )
        ))
        val record = auditLog(1L, afterJson)
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(record)
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "id-dup", listOf("1:a"), "")
        }
        assertTrue(ex.message!!.contains("duplicate reviewKeys"))
    }

    @Test
    fun `gate rejects reviewKey not matching index-colon-intentKey format`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        val afterJson = objectMapper.writeValueAsString(mapOf(
            "draftIdentity" to "id-fmt",
            "readiness" to "NEEDS_REVIEW",
            "unresolvedSnapshot" to listOf(
                mapOf("reviewKey" to "wrong-format", "requestIndex" to 1, "intentKey" to "a")
            )
        ))
        val record = auditLog(1L, afterJson)
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(record)
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "id-fmt", listOf("1:a"), "")
        }
        assertTrue(ex.message!!.contains("does not match"))
    }

    // -- recordInitialDraft returns identity --

    @Test
    fun `recordInitialDraft returns non-null identity on success`() {
        val logService = Mockito.mock(OperatorActionLogService::class.java)
        Mockito.`when`(logService.record(
            anyNonNull(""), anyNonNull(0L), anyNonNull(OperatorActionType.AI_REPLY_DRAFT_READY),
            anyNullable(), anyNullable(), anyNullable(),
            anyNullable(), anyNullable(), anyNullable(), anyNullable()
        )).thenReturn(
            OperatorActionLog(id = 42L, targetType = "x", targetId = 1L, actionType = "x", actionSummary = "x")
        )
        val s = svc(logService = logService)
        val result = simpleResult(AiReplyDraftReadiness.READY)

        val id = s.recordInitialDraft(1L, 1L, result, "op")
        assertNotNull(id)
        assertFalse(id!!.matches(Regex("\\d+"))) // should NOT be a pure number
    }

    @Test
    fun `recordInitialDraft returns null when recording fails`() {
        val logService = Mockito.mock(OperatorActionLogService::class.java)
        Mockito.doThrow(RuntimeException("DB error")).`when`(logService).record(
            anyNonNull(""), anyNonNull(0L), anyNonNull(OperatorActionType.AI_REPLY_DRAFT_READY),
            anyNullable(), anyNullable(), anyNullable(),
            anyNullable(), anyNullable(), anyNullable(), anyNullable()
        )
        val s = svc(logService = logService)
        val result = simpleResult(AiReplyDraftReadiness.READY)

        val id = s.recordInitialDraft(1L, 1L, result, "op")
        assertNull(id)
    }

    // -- SEND_BLOCKED payload limits --

    @Test
    fun `recordSendBlocked truncates keys when exceeding max items`() {
        val logService = Mockito.mock(OperatorActionLogService::class.java)
        val s = svc(logService = logService)

        val items = (1..150).map {
            AiReplyReviewItem("$it:key", it, "key", "MISSING", emptyList())
        }
        s.recordSendBlocked(1L, 1L, items, "op")

        Mockito.verify(logService).record(
            anyNonNull(""), anyNonNull(0L), anyNonNull(OperatorActionType.AI_REPLY_SEND_BLOCKED),
            anyNullable(), anyNullable(), anyNullable(),
            anyNullable(), anyNullable(), anyNullable(), anyNullable()
        )
    }

    @Test
    fun `recordSendBlocked truncates individual key when exceeding max length`() {
        val logService = Mockito.mock(OperatorActionLogService::class.java)
        val s = svc(logService = logService)

        val longKey = "x".repeat(500)
        val items = listOf(
            AiReplyReviewItem(longKey, 1, "intent", "MISSING", emptyList())
        )
        s.recordSendBlocked(1L, 1L, items, "op")

        Mockito.verify(logService).record(
            anyNonNull(""), anyNonNull(0L), anyNonNull(OperatorActionType.AI_REPLY_SEND_BLOCKED),
            anyNullable(), anyNullable(), anyNullable(),
            anyNullable(), anyNullable(), anyNullable(), anyNullable()
        )
    }

    // -- helpers --

    private fun repoWithNeedsReview(identity: String): OperatorActionLogRepository {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        val afterJson = objectMapper.writeValueAsString(mapOf(
            "draftIdentity" to identity,
            "readiness" to "NEEDS_REVIEW",
            "unresolvedSnapshot" to listOf(
                mapOf("reviewKey" to "1:a", "requestIndex" to 1, "intentKey" to "a")
            )
        ))
        val record = auditLog(1L, afterJson)
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(record)
        return repo
    }

    private fun repoWithTwoUnresolved(identity: String): OperatorActionLogRepository {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        val afterJson = objectMapper.writeValueAsString(mapOf(
            "draftIdentity" to identity,
            "readiness" to "NEEDS_REVIEW",
            "unresolvedSnapshot" to listOf(
                mapOf("reviewKey" to "1:a", "requestIndex" to 1, "intentKey" to "a"),
                mapOf("reviewKey" to "1:b", "requestIndex" to 1, "intentKey" to "b")
            )
        ))
        val record = auditLog(1L, afterJson)
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(record)
        return repo
    }

    private fun repoWithBlocked(identity: String): OperatorActionLogRepository {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        val afterJson = objectMapper.writeValueAsString(mapOf(
            "draftIdentity" to identity,
            "readiness" to "BLOCKED",
            "unresolvedSnapshot" to listOf(
                mapOf("reviewKey" to "1:a", "requestIndex" to 1, "intentKey" to "a")
            )
        ))
        val record = auditLog(1L, afterJson)
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(record)
        return repo
    }

    private fun readyAfterJson(): String = objectMapper.writeValueAsString(mapOf(
        "draftIdentity" to "id-ready",
        "readiness" to "READY",
        "unresolvedSnapshot" to emptyList<Map<String, Any?>>()
    ))

    private fun auditLog(inboundProcessingId: Long, afterJson: String) = OperatorActionLog(
        id = 1L,
        targetType = "INBOUND_MAIL_PROCESSING",
        targetId = inboundProcessingId,
        expertContactId = 1L,
        inboundProcessingId = inboundProcessingId,
        actionType = "AI_REPLY_DRAFT_NEEDS_REVIEW",
        actionSummary = "AI 草稿生成-需审核",
        afterValue = afterJson,
        createdAt = LocalDateTime.now()
    )

    private fun simpleResult(readiness: AiReplyDraftReadiness) = AiReplyDraftResult(
        draftText = "draft",
        usedLlm = true,
        qaRuleIds = emptyList(),
        mode = AiReplyMode.FREE_FORM,
        requestFacts = emptyList(),
        draftReadiness = readiness
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNullable(): T = Mockito.any<T>() ?: null as T
}
