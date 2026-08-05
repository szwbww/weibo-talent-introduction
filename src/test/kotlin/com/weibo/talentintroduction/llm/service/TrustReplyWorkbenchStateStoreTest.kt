package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.LocalDateTime

class TrustReplyWorkbenchStateStoreTest {
    private val jdbc = Mockito.mock(NamedParameterJdbcTemplate::class.java)
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private val store = TrustReplyWorkbenchStateStore(jdbc, objectMapper)

    private fun v1PayloadJson(
        evidenceSetVersion: String = "e1",
        requestedFactIds: List<Long> = listOf(1L, 2L)
    ): String =
        """{"schemaVersion":"trust-reply-workbench-state-v1","sourceVersion":"s1","evidenceSetVersion":"$evidenceSetVersion","requestedFactIds":[${requestedFactIds.joinToString(",")}],"selectedModel":"DEEPSEEK_V4_FLASH","lockedItems":[]}"""

    @Test
    fun `v1 payload decodes for migration with legacy flat union`() {
        val payload = store.decodePayload(v1PayloadJson())

        assertNotNull(payload)
        assertEquals(TrustReplyWorkbenchStateStore.LEGACY_SCHEMA_VERSION, payload!!.schemaVersion)
        assertEquals(listOf(1L, 2L), payload.requestedFactIds)
        assertTrue(payload.requestFactSelections.isEmpty())
    }

    @Test
    fun `v2 payload decodes with canonical matrix`() {
        val json =
            """{"schemaVersion":"trust-reply-workbench-state-v2","sourceVersion":"s1","evidenceSetVersion":"e1","requestedFactIds":[9],"requestFactSelections":[{"requestKey":"k","factRuleIds":[9]}],"selectedModel":"DEEPSEEK_V4_FLASH","lockedItems":[]}"""
        val payload = store.decodePayload(json)

        assertNotNull(payload)
        assertEquals(TrustReplyWorkbenchStateStore.SCHEMA_VERSION, payload!!.schemaVersion)
        assertEquals(listOf(TrustReplyRequestFactSelection("k", listOf(9L))), payload.requestFactSelections)
        assertEquals(listOf(9L), payload.requestedFactIds)
    }

    @Test
    fun `unknown and corrupt payloads decode to null`() {
        val unknown =
            """{"schemaVersion":"trust-reply-workbench-state-v9","sourceVersion":"s1","evidenceSetVersion":"e1","requestedFactIds":[],"selectedModel":"M","lockedItems":[]}"""
        assertNull(store.decodePayload(unknown))
        assertNull(store.decodePayload("{not-json"))
        assertNull(store.decodePayload("{}"))
    }

    @Test
    fun `encode writes v2 schema with canonical matrix and round trips`() {
        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = "s1",
            evidenceSetVersion = "e1",
            requestedFactIds = listOf(9L),
            requestFactSelections = listOf(TrustReplyRequestFactSelection("k", listOf(9L))),
            selectedModel = "DEEPSEEK_V4_FLASH",
            lockedItems = emptyList()
        )

        val json = store.encodePayload(payload)
        val decoded = store.decodePayload(json)

        assertEquals(payload, decoded)
    }

    @Test
    fun `oversized payload is rejected with stable code`() {
        val hugeAnswer = "x".repeat(300 * 1024)
        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = "s1",
            evidenceSetVersion = "e1",
            requestedFactIds = listOf(9L),
            requestFactSelections = listOf(TrustReplyRequestFactSelection("k", listOf(9L))),
            selectedModel = "DEEPSEEK_V4_FLASH",
            lockedItems = listOf(
                TrustReplyLockedItemRequest(
                    requestKey = "k",
                    versionId = "v",
                    handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
                    answerText = hugeAnswer,
                    claims = emptyList(),
                    model = "DEEPSEEK_V4_FLASH",
                    generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
                    evidenceSetVersion = "e1",
                    sourceVersion = "s1"
                )
            )
        )

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            store.encodePayload(payload)
        }
        assertEquals("TRUST_REPLY_STATE_TOO_LARGE", ex.code)
    }

    @Test
    fun `update conflict surfaces when no row matched the expected version`() {
        Mockito.`when`(jdbc.update(Mockito.anyString(), Mockito.any(MapSqlParameterSource::class.java)))
            .thenReturn(0)
        Mockito.`when`(jdbc.query(Mockito.anyString(), Mockito.any(MapSqlParameterSource::class.java), Mockito.any(org.springframework.jdbc.core.RowMapper::class.java)))
            .thenReturn(emptyList<Any>())

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            store.save("TRAINING_MAIL", 11L, expectedStateVersion = 3L, payloadJson = "{}", now = LocalDateTime.now())
        }
        assertEquals("TRUST_REPLY_STATE_CONFLICT", ex.code)
        assertTrue(ex is TrustReplyStateConflictException)
    }

    @Test
    fun `insert conflict surfaces when the row already exists`() {
        // pruneExpired consumes the first update call; the insert then hits the duplicate key.
        Mockito.`when`(jdbc.update(Mockito.anyString(), Mockito.any(MapSqlParameterSource::class.java)))
            .thenReturn(0)
            .thenThrow(DuplicateKeyException("duplicate"))

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            store.save("TRAINING_MAIL", 11L, expectedStateVersion = 0L, payloadJson = "{}", now = LocalDateTime.now())
        }
        assertEquals("TRUST_REPLY_STATE_CONFLICT", ex.code)
    }

    @Test
    fun `delete conflict surfaces when no row matched the expected version`() {
        Mockito.`when`(jdbc.update(Mockito.anyString(), Mockito.any(MapSqlParameterSource::class.java)))
            .thenReturn(0)
        Mockito.`when`(jdbc.query(Mockito.anyString(), Mockito.any(MapSqlParameterSource::class.java), Mockito.any(org.springframework.jdbc.core.RowMapper::class.java)))
            .thenReturn(emptyList<Any>())

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            store.delete("TRAINING_MAIL", 11L, expectedStateVersion = 2L)
        }
        assertEquals("TRUST_REPLY_STATE_CONFLICT", ex.code)
    }

    @Test
    fun `prune returns the number of expired rows`() {
        Mockito.`when`(jdbc.update(Mockito.anyString(), Mockito.any(MapSqlParameterSource::class.java)))
            .thenReturn(4)

        assertEquals(4, store.pruneExpired(LocalDateTime.now()))
    }
}
