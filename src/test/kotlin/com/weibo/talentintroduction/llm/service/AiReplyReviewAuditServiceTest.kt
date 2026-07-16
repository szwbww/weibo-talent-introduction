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

    // -- validateConfirmationForSend: no draft, MANUAL result --

    @Test
    fun `gate returns MANUAL when no AI draft record and no source or confirmation`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(null)
        val s = svc(repo = repo)

        val result = s.validateConfirmationForSend(1L, null, null)
        assertEquals(AiReplySendAuthorityResult.MANUAL, result)
    }

    @Test
    fun `gate rejects when no draft but replySource is AI_DRAFT`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(null)
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "AI_DRAFT", null)
        }
        assertTrue(ex.message!!.contains("No AI draft record"))
    }

    @Test
    fun `gate rejects when no draft but confirmation provided`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(null)
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, null, AiReviewConfirmation())
        }
        assertTrue(ex.message!!.contains("No AI draft record"))
    }

    @Test
    fun `gate returns AI_READY when latest draft is READY`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        val record = auditLog(1L, readyAfterJson(), "AI_REPLY_DRAFT_READY")
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(record)
        val s = svc(repo = repo)

        val result = s.validateConfirmationForSend(1L, null, null)
        assertEquals(AiReplySendAuthorityResult.AI_READY, result)
    }

    @Test
    fun `gate rejects READY when unresolvedSnapshot is non-empty even if count matches`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        val afterJson = objectMapper.writeValueAsString(mapOf(
            "draftIdentity" to "id-ready-corrupt",
            "readiness" to "READY",
            "unresolvedSnapshot" to listOf(
                mapOf("reviewKey" to "1:a", "requestIndex" to 1, "intentKey" to "a")
            ),
            "unresolvedCount" to 1
        ))
        val record = auditLog(1L, afterJson, "AI_REPLY_DRAFT_READY")
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(record)
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, null, null)
        }
        assertTrue(ex.message!!.contains("empty unresolvedSnapshot"))
    }

    @Test
    fun `gate rejects READY when unresolvedCount is non-zero even if snapshot is empty`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        val afterJson = objectMapper.writeValueAsString(mapOf(
            "draftIdentity" to "id-ready-count",
            "readiness" to "READY",
            "unresolvedSnapshot" to emptyList<Map<String, Any?>>(),
            "unresolvedCount" to 1
        ))
        val record = auditLog(1L, afterJson, "AI_REPLY_DRAFT_READY")
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(record)
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, null, null)
        }
        assertTrue(ex.message!!.contains("does not match snapshot size"))
    }

    @Test
    fun `gate rejects when readiness does not match action_type`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        val afterJson = objectMapper.writeValueAsString(mapOf(
            "draftIdentity" to "id-mismatch",
            "readiness" to "BLOCKED",
            "unresolvedSnapshot" to listOf(
                mapOf("reviewKey" to "1:a", "requestIndex" to 1, "intentKey" to "a")
            ),
            "unresolvedCount" to 1
        ))
        val record = auditLog(1L, afterJson, "AI_REPLY_DRAFT_NEEDS_REVIEW")
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(record)
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, null, AiReviewConfirmation(draftIdentity = "id-mismatch", confirmedReviewKeys = listOf("1:a")))
        }
        assertTrue(ex.message!!.contains("does not match action_type"))
    }

    // -- non-READY: identity required --

    @Test
    fun `gate rejects when non-READY draft exists but no identity provided`() {
        val repo = repoWithNeedsReview("id-abc")
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "AI_DRAFT", AiReviewConfirmation())
        }
        assertTrue(ex.message!!.contains("must provide draftIdentity"))
    }

    @Test
    fun `gate rejects when non-READY draft exists but identity is blank`() {
        val repo = repoWithNeedsReview("id-abc")
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "AI_DRAFT", AiReviewConfirmation(draftIdentity = "   "))
        }
        assertTrue(ex.message!!.contains("must provide draftIdentity"))
    }

    @Test
    fun `gate rejects when identity does not match stored draft identity`() {
        val repo = repoWithNeedsReview("id-abc")
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "AI_DRAFT", AiReviewConfirmation(draftIdentity = "wrong-id", confirmedReviewKeys = listOf("1:a")))
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
            ),
            "unresolvedCount" to 1
        ))
        val record = auditLog(1L, afterJson)
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(record)
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "AI_DRAFT", AiReviewConfirmation(draftIdentity = "any-id"))
        }
        assertTrue(ex.message!!.contains("corrupt record") || ex.message!!.contains("no draftIdentity"))
    }

    // -- valid identity: key & note checks --

    @Test
    fun `gate rejects duplicate confirmed keys when identity matches`() {
        val repo = repoWithNeedsReview("id-abc")
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "AI_DRAFT", AiReviewConfirmation(draftIdentity = "id-abc", confirmedReviewKeys = listOf("1:a", "1:a")))
        }
        assertTrue(ex.message!!.contains("Duplicate"))
    }

    @Test
    fun `gate rejects extra confirmed keys when identity matches`() {
        val repo = repoWithNeedsReview("id-abc")
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "AI_DRAFT", AiReviewConfirmation(draftIdentity = "id-abc", confirmedReviewKeys = listOf("1:a", "2:unknown")))
        }
        assertTrue(ex.message!!.contains("Unknown"))
    }

    @Test
    fun `gate rejects missing keys when identity matches`() {
        val repo = repoWithTwoUnresolved("id-abc")
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "AI_DRAFT", AiReviewConfirmation(draftIdentity = "id-abc", confirmedReviewKeys = listOf("1:a")))
        }
        assertTrue(ex.message!!.contains("Missing"))
    }

    @Test
    fun `gate returns AI_REVIEW_CONFIRMED when identity matches and all keys confirmed`() {
        val repo = repoWithNeedsReview("id-abc")
        val s = svc(repo = repo)

        val result = s.validateConfirmationForSend(1L, "AI_DRAFT", AiReviewConfirmation(draftIdentity = "id-abc", confirmedReviewKeys = listOf("1:a")))
        assertTrue(result is AiReplySendAuthorityResult.AI_REVIEW_CONFIRMED)
        val confirmed = result as AiReplySendAuthorityResult.AI_REVIEW_CONFIRMED
        assertEquals("id-abc", confirmed.draftIdentity)
        assertEquals(listOf("1:a"), confirmed.confirmedReviewKeys)
    }

    @Test
    fun `gate rejects BLOCKED with insufficient note`() {
        val repo = repoWithBlocked("id-blocked")
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "AI_DRAFT", AiReviewConfirmation(draftIdentity = "id-blocked", confirmedReviewKeys = listOf("1:a"), operatorNote = "ab"))
        }
        assertTrue(ex.message!!.contains("at least 5"))
    }

    @Test
    fun `gate accepts BLOCKED with sufficient note and returns AI_REVIEW_CONFIRMED`() {
        val repo = repoWithBlocked("id-blocked")
        val s = svc(repo = repo)

        val result = s.validateConfirmationForSend(1L, "AI_DRAFT", AiReviewConfirmation(draftIdentity = "id-blocked", confirmedReviewKeys = listOf("1:a"), operatorNote = "Verified ok"))
        assertTrue(result is AiReplySendAuthorityResult.AI_REVIEW_CONFIRMED)
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
            ),
            "unresolvedCount" to 2
        ))
        val record = auditLog(1L, afterJson)
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(record)
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "AI_DRAFT", AiReviewConfirmation(draftIdentity = "id-dup", confirmedReviewKeys = listOf("1:a")))
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
            ),
            "unresolvedCount" to 1
        ))
        val record = auditLog(1L, afterJson)
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(record)
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "AI_DRAFT", AiReviewConfirmation(draftIdentity = "id-fmt", confirmedReviewKeys = listOf("1:a")))
        }
        assertTrue(ex.message!!.contains("does not match"))
    }

    @Test
    fun `gate rejects unknown non-empty replySource without confirmation`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(null)
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "UNKNOWN_SOURCE", null)
        }
        assertTrue(ex.message!!.contains("No AI draft record"))
    }

    @Test
    fun `gate rejects missing requestIndex in snapshot item`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        val afterJson = objectMapper.writeValueAsString(mapOf(
            "draftIdentity" to "id-miss-idx",
            "readiness" to "NEEDS_REVIEW",
            "unresolvedSnapshot" to listOf(
                mapOf("reviewKey" to "1:a", "intentKey" to "a")
            ),
            "unresolvedCount" to 1
        ))
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L))
            .thenReturn(auditLog(1L, afterJson))
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(
                1L, "AI_DRAFT",
                AiReviewConfirmation(draftIdentity = "id-miss-idx", confirmedReviewKeys = listOf("1:a"))
            )
        }
        assertTrue(ex.message!!.contains("missing requestIndex"))
    }

    @Test
    fun `gate rejects missing intentKey in snapshot item`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        val afterJson = objectMapper.writeValueAsString(mapOf(
            "draftIdentity" to "id-miss-ik",
            "readiness" to "NEEDS_REVIEW",
            "unresolvedSnapshot" to listOf(
                mapOf("reviewKey" to "1:a", "requestIndex" to 1)
            ),
            "unresolvedCount" to 1
        ))
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L))
            .thenReturn(auditLog(1L, afterJson))
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(
                1L, "AI_DRAFT",
                AiReviewConfirmation(draftIdentity = "id-miss-ik", confirmedReviewKeys = listOf("1:a"))
            )
        }
        assertTrue(ex.message!!.contains("missing intentKey"))
    }

    @Test
    fun `gate rejects string unresolvedCount`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        val afterJson = """{"draftIdentity":"id-str-count","readiness":"NEEDS_REVIEW","unresolvedSnapshot":[{"reviewKey":"1:a","requestIndex":1,"intentKey":"a"}],"unresolvedCount":"1"}"""
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L))
            .thenReturn(auditLog(1L, afterJson))
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(
                1L, "AI_DRAFT",
                AiReviewConfirmation(draftIdentity = "id-str-count", confirmedReviewKeys = listOf("1:a"))
            )
        }
        assertTrue(ex.message!!.contains("unresolvedCount"))
    }

    @Test
    fun `gate rejects decimal unresolvedCount`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        val afterJson = """{"draftIdentity":"id-dec-count","readiness":"NEEDS_REVIEW","unresolvedSnapshot":[{"reviewKey":"1:a","requestIndex":1,"intentKey":"a"}],"unresolvedCount":1.5}"""
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L))
            .thenReturn(auditLog(1L, afterJson))
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(
                1L, "AI_DRAFT",
                AiReviewConfirmation(draftIdentity = "id-dec-count", confirmedReviewKeys = listOf("1:a"))
            )
        }
        assertTrue(ex.message!!.contains("unresolvedCount"))
    }

    @Test
    fun `gate rejects missing unresolvedCount`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        val afterJson = objectMapper.writeValueAsString(mapOf(
            "draftIdentity" to "id-no-count",
            "readiness" to "NEEDS_REVIEW",
            "unresolvedSnapshot" to listOf(
                mapOf("reviewKey" to "1:a", "requestIndex" to 1, "intentKey" to "a")
            )
        ))
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L))
            .thenReturn(auditLog(1L, afterJson))
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(
                1L, "AI_DRAFT",
                AiReviewConfirmation(draftIdentity = "id-no-count", confirmedReviewKeys = listOf("1:a"))
            )
        }
        assertTrue(ex.message!!.contains("missing unresolvedCount"))
    }

    @Test
    fun `gate rejects non-object snapshot item`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        val afterJson = """{"draftIdentity":"id-bad-item","readiness":"NEEDS_REVIEW","unresolvedSnapshot":["1:a"],"unresolvedCount":1}"""
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L))
            .thenReturn(auditLog(1L, afterJson))
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(
                1L, "AI_DRAFT",
                AiReviewConfirmation(draftIdentity = "id-bad-item", confirmedReviewKeys = listOf("1:a"))
            )
        }
        assertTrue(ex.message!!.contains("must be an object"))
    }

    @Test
    fun `gate rejects UNKNOWN_SOURCE when READY authority exists`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L))
            .thenReturn(auditLog(1L, readyAfterJson(), "AI_REPLY_DRAFT_READY"))
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(1L, "UNKNOWN_SOURCE", null)
        }
        assertTrue(ex.message!!.contains("Unsupported replySource"))
    }

    @Test
    fun `gate rejects UNKNOWN_SOURCE when NEEDS_REVIEW authority exists even with valid confirmation`() {
        val repo = repoWithNeedsReview("id-abc")
        val s = svc(repo = repo)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            s.validateConfirmationForSend(
                1L,
                "UNKNOWN_SOURCE",
                AiReviewConfirmation(draftIdentity = "id-abc", confirmedReviewKeys = listOf("1:a"))
            )
        }
        assertTrue(ex.message!!.contains("Unsupported replySource"))
    }

    // -- recordInitialDraft returns identity --

    @Test
    fun `recordInitialDraft returns available with non-null identity on success`() {
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

        val authorityResult = s.recordInitialDraft(1L, 1L, result, "op")
        assertTrue(authorityResult.available)
        assertNotNull(authorityResult.draftIdentity)
        assertFalse(authorityResult.draftIdentity!!.matches(Regex("\\d+")))
    }

    @Test
    fun `recordInitialDraft returns unavailable when recording fails`() {
        val logService = Mockito.mock(OperatorActionLogService::class.java)
        Mockito.doThrow(RuntimeException("DB error")).`when`(logService).record(
            anyNonNull(""), anyNonNull(0L), anyNonNull(OperatorActionType.AI_REPLY_DRAFT_READY),
            anyNullable(), anyNullable(), anyNullable(),
            anyNullable(), anyNullable(), anyNullable(), anyNullable()
        )
        val s = svc(logService = logService)
        val result = simpleResult(AiReplyDraftReadiness.READY)

        val authorityResult = s.recordInitialDraft(1L, 1L, result, "op")
        assertFalse(authorityResult.available)
        assertNull(authorityResult.draftIdentity)
    }

    // -- resolveCurrentDraftAuthority --

    @Test
    fun `resolveCurrentDraftAuthority returns unavailable when no latest draft`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(null)
        val s = svc(repo = repo)

        val result = s.resolveCurrentDraftAuthority(1L)

        assertEquals(false, result.available)
        assertNull(result.draftIdentity)
    }

    @Test
    fun `resolveCurrentDraftAuthority returns identity when latest draft is valid READY`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        val record = auditLog(1L, readyAfterJson(), "AI_REPLY_DRAFT_READY")
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(record)
        val s = svc(repo = repo)

        val result = s.resolveCurrentDraftAuthority(1L)

        assertEquals(true, result.available)
        assertEquals("id-ready", result.draftIdentity)
    }

    @Test
    fun `resolveCurrentDraftAuthority returns unavailable when READY snapshot is corrupt`() {
        val repo = Mockito.mock(OperatorActionLogRepository::class.java)
        val afterJson = objectMapper.writeValueAsString(mapOf(
            "draftIdentity" to "id-ready-corrupt",
            "readiness" to "READY",
            "unresolvedSnapshot" to listOf(
                mapOf("reviewKey" to "1:a", "requestIndex" to 1, "intentKey" to "a")
            ),
            "unresolvedCount" to 1
        ))
        val record = auditLog(1L, afterJson, "AI_REPLY_DRAFT_READY")
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(record)
        val s = svc(repo = repo)

        val result = s.resolveCurrentDraftAuthority(1L)

        assertEquals(false, result.available)
        assertNull(result.draftIdentity)
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
            ),
            "unresolvedCount" to 1
        ))
        val record = auditLog(1L, afterJson, "AI_REPLY_DRAFT_NEEDS_REVIEW")
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
            ),
            "unresolvedCount" to 2
        ))
        val record = auditLog(1L, afterJson, "AI_REPLY_DRAFT_NEEDS_REVIEW")
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
            ),
            "unresolvedCount" to 1
        ))
        val record = auditLog(1L, afterJson, "AI_REPLY_DRAFT_BLOCKED")
        Mockito.`when`(repo.findLatestAiDraftByInboundProcessingId(1L)).thenReturn(record)
        return repo
    }

    private fun readyAfterJson(): String = objectMapper.writeValueAsString(mapOf(
        "draftIdentity" to "id-ready",
        "readiness" to "READY",
        "unresolvedSnapshot" to emptyList<Map<String, Any?>>(),
        "unresolvedCount" to 0
    ))

    private fun auditLog(inboundProcessingId: Long, afterJson: String, actionType: String = "AI_REPLY_DRAFT_NEEDS_REVIEW") = OperatorActionLog(
        id = 1L,
        targetType = "INBOUND_MAIL_PROCESSING",
        targetId = inboundProcessingId,
        expertContactId = 1L,
        inboundProcessingId = inboundProcessingId,
        actionType = actionType,
        actionSummary = "AI 草稿生成",
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
