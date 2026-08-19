package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.llm.config.AskEnumeratorProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider

/**
 * P2a (plan 02-unrecognized-request-detection, D-1/D-4): enumerator verbatim
 * validation (I-1), fail-open behaviour (I-4), the 12-item cap, and the fixed
 * [ASK_ENUM] log-line format.
 */
class InboundAskEnumeratorTest {
    private val llmProperties = LlmProperties(enabled = true)
    private val askProperties = AskEnumeratorProperties()
    private val objectMapper = ObjectMapper().registerKotlinModule()

    private fun clientReturning(response: String?): LlmDraftClient = object : LlmDraftClient {
        override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
        override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = response
    }

    private fun clientThrowing(): LlmDraftClient = object : LlmDraftClient {
        override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
        override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? =
            throw RuntimeException("llm down")
    }

    @Suppress("UNCHECKED_CAST")
    private fun providerWith(client: LlmDraftClient?): ObjectProvider<LlmDraftClient> {
        val mock = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<LlmDraftClient>
        Mockito.`when`(mock.getIfAvailable()).thenReturn(client)
        return mock
    }

    private fun enumerator(client: LlmDraftClient?): InboundAskEnumerator =
        InboundAskEnumerator(providerWith(client), llmProperties, askProperties, objectMapper)

    private fun disabledEnumerator(client: LlmDraftClient?): InboundAskEnumerator =
        InboundAskEnumerator(
            providerWith(client),
            LlmProperties(enabled = false),
            askProperties,
            objectMapper
        )

    // ── I-1: verbatim validation ────────────────────────────────────────────

    @Test
    fun `verbatim quote is kept and its original range restores the quote`() {
        val mail = "Before I decide, could you tell me whether you provide visa support?"
        val result = enumerator(clientReturning(
            """[{"label": "Visa support", "quote": "provide visa support"}]"""
        )).enumerate(mail)

        assertTrue(result.available)
        val ask = result.asks.single()
        assertEquals("Visa support", ask.label)
        assertEquals("provide visa support", ask.quote)
        assertEquals("provide visa support", mail.substring(ask.originalRange))
    }

    @Test
    fun `paraphrased quote that is not a substring is discarded`() {
        val mail = "Before I decide, could you tell me whether you provide visa support?"
        val result = enumerator(clientReturning(
            """[{"label": "Visa support", "quote": "provide visa assistance"}]"""
        )).enumerate(mail)

        assertFalse(result.available)
        assertTrue(result.asks.isEmpty())
    }

    @Test
    fun `quote shorter than eight folded characters is discarded`() {
        val result = enumerator(clientReturning(
            """[{"label": "Article", "quote": "the"}]"""
        )).enumerate("I want to know about the programme.")

        assertFalse(result.available)
        assertTrue(result.asks.isEmpty())
    }

    @Test
    fun `quote joining non adjacent text is discarded`() {
        val mail = "Could you tell me about the salary and the intellectual property arrangements?"
        val result = enumerator(clientReturning(
            """[{"label": "Salary and IP", "quote": "salary and intellectual property"}]"""
        )).enumerate(mail)

        assertFalse(result.available)
        assertTrue(result.asks.isEmpty())
    }

    @Test
    fun `duplicate quotes are enumerated once`() {
        val mail = "Before I decide, could you tell me whether you provide visa support?"
        val result = enumerator(clientReturning(
            """[
                {"label": "Visa support", "quote": "provide visa support"},
                {"label": "Visa support again", "quote": "provide visa support"}
            ]"""
        )).enumerate(mail)

        assertTrue(result.available)
        assertEquals(1, result.asks.size)
    }

    @Test
    fun `markdown fenced json payload is parsed`() {
        val mail = "Before I decide, could you tell me whether you provide visa support?"
        val result = enumerator(clientReturning(
            "```json\n[{\"label\": \"Visa support\", \"quote\": \"provide visa support\"}]\n```"
        )).enumerate(mail)

        assertTrue(result.available)
        assertEquals("provide visa support", result.asks.single().quote)
    }

    // ── I-4: fail-open on every failure path ────────────────────────────────

    @Test
    fun `fail open when the llm is disabled`() {
        val result = disabledEnumerator(clientReturning("""[{"label":"X","quote":"anything at all"}]"""))
            .enumerate("Could you tell me about the salary?")

        assertFalse(result.available)
        assertTrue(result.asks.isEmpty())
    }

    @Test
    fun `fail open when the client is unavailable`() {
        val result = enumerator(null).enumerate("Could you tell me about the salary?")

        assertFalse(result.available)
        assertTrue(result.asks.isEmpty())
    }

    @Test
    fun `fail open when chat throws`() {
        val result = enumerator(clientThrowing()).enumerate("Could you tell me about the salary?")

        assertFalse(result.available)
        assertTrue(result.asks.isEmpty())
    }

    @Test
    fun `fail open on a non json response`() {
        val result = enumerator(clientReturning("this is not json")).enumerate("Could you tell me about the salary?")

        assertFalse(result.available)
        assertTrue(result.asks.isEmpty())
    }

    @Test
    fun `fail open on an empty array`() {
        val result = enumerator(clientReturning("[]")).enumerate("Could you tell me about the salary?")

        assertFalse(result.available)
        assertTrue(result.asks.isEmpty())
    }

    // ── I-1: 12-item cap, explicitly truncated ──────────────────────────────

    @Test
    fun `more than twelve asks are truncated to twelve`() {
        val mail = (1..13).joinToString(" ") { "question $it detail" }
        val payload = (1..13).joinToString(",") {
            """{"label": "Q$it", "quote": "question $it detail"}"""
        }
        val result = enumerator(clientReturning("[$payload]")).enumerate(mail)

        assertTrue(result.available)
        assertEquals(12, result.asks.size)
    }

    // ── D-4: fixed [ASK_ENUM] log-line format ───────────────────────────────

    @Test
    fun `ask enum log line has the fixed field names and order`() {
        assertEquals(
            "[ASK_ENUM] source=TRAINING_MAIL contactId=7 available=true enumerated=5 claimed=5 unrecognized=0 kind=FALLBACK",
            buildAskEnumLogLine("TRAINING_MAIL", 7L, true, 5, 5, 0, "FALLBACK")
        )
        assertEquals(
            "[ASK_ENUM] source=AUTO contactId=0 available=false enumerated=0 claimed=0 unrecognized=0 kind=BULLET,QUESTION",
            buildAskEnumLogLine("AUTO", 0L, false, 0, 0, 0, "BULLET,QUESTION")
        )
    }
}
