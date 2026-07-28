package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.QaReplyComposer
import com.weibo.talentintroduction.reply.service.ManualReplyFrame
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.Optional

class AiReplyPointByPointComposerTest {
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val replySnippetService = Mockito.mock(ReplySnippetService::class.java)
    private val composer = AiReplyPointByPointComposer(qaRuleRepository, replySnippetService)

    private fun stubFrame(
        salutation: String? = "Dear \${expertName|Professor},",
        greeting: String? = QaReplyComposer.GREETING,
        closing: String? = QaReplyComposer.CLOSING
    ) {
        Mockito.`when`(replySnippetService.resolveManualFrame()).thenReturn(
            ManualReplyFrame(
                salutation = salutation,
                greeting = greeting,
                closing = closing,
                ackOptions = emptyList()
            )
        )
        Mockito.`when`(replySnippetService.resolveAck(null)).thenReturn(null)
    }

    private fun rule(id: Long, body: String) = QaRule(
        id = id,
        categoryId = 1,
        keywords = "k$id",
        replyBody = body,
        answerBody = body,
        replySubject = "Re $id",
        enabled = true
    )

    private fun supportedIntent(key: String, title: String, evidenceIds: List<Long>) =
        RequestIntentCoverage(key, title, emptyList(), evidenceIds, "SUPPORTED", emptyList())

    @Test
    fun `composeFromSections deduplicates identical answers`() {
        stubFrame()
        val text = composer.composeFromSections(
            requestFacts = listOf(
                RequestFactItem(1, "- A?", listOf(1L), RequestGroundingStatus.GROUNDED,
                    intents = listOf(supportedIntent("finance.arrangements", "Financial arrangements", listOf(1L)))),
                RequestFactItem(2, "- B?", listOf(2L), RequestGroundingStatus.GROUNDED,
                    intents = listOf(supportedIntent("finance.arrangements", "Financial arrangements", listOf(2L))))
            ),
            sections = listOf(
                ValidatedSection(1, listOf(IntentAnswer("finance.arrangements", "Same answer body", listOf(1L)))),
                ValidatedSection(2, listOf(IntentAnswer("finance.arrangements", "Same answer body", listOf(2L))))
            )
        )
        assertTrue(text.contains("Same answer body"))
        assertFalse(text.contains("Please see point"))
        assertEquals(1, Regex("Same answer body").findAll(text).count())
    }

    @Test
    fun `composeLockedItems preserves every answer byte order and duplicate`() {
        stubFrame(salutation = "SALUTATION", greeting = "GREETING", closing = "CLOSING")
        val answers = listOf(
            "answer-1",
            "same answer",
            "answer-3\nwith two lines",
            "answer-4 {{expert.name}}",
            "same answer",
            "answer-6"
        )

        val text = composer.composeLockedItems(answers)

        assertTrue(text.startsWith("SALUTATION\n\nGREETING"))
        assertTrue(text.endsWith("CLOSING"))
        assertEquals(1, Regex("answer-1").findAll(text).count())
        assertEquals(1, Regex("answer-3\\nwith two lines").findAll(text).count())
        assertEquals(1, Regex("answer-4 \\{\\{expert\\.name\\}\\}").findAll(text).count())
        assertEquals(1, Regex("answer-6").findAll(text).count())
        assertEquals(2, Regex("same answer").findAll(text).count())
        assertTrue(text.indexOf("answer-1") < text.indexOf("same answer"))
        assertTrue(text.indexOf("same answer", text.indexOf("same answer") + 1) < text.indexOf("answer-6"))
        assertTrue(text.contains("answer-3\nwith two lines"))
        assertTrue(text.contains("answer-4 {{expert.name}}"))
    }

    @Test
    fun `composeLockedItems keeps ordered answers when frame is empty`() {
        stubFrame(salutation = null, greeting = null, closing = null)

        val text = composer.composeLockedItems(listOf("first", "second", "first"))

        assertEquals("first\n\nsecond\n\nfirst", text)
        assertTrue(text.indexOf("first") < text.indexOf("second"))
        assertFalse(text.isBlank())
    }

    @Test
    fun `composeFromSections includes supported answers only`() {
        stubFrame()
        val text = composer.composeFromSections(
            requestFacts = listOf(
                RequestFactItem(1, "- A?", listOf(1L), RequestGroundingStatus.GROUNDED,
                    intents = listOf(supportedIntent("finance.arrangements", "Financial arrangements", listOf(1L)))),
                RequestFactItem(2, "- B?", listOf(2L), RequestGroundingStatus.PARTIAL,
                    intents = listOf(supportedIntent("role.deliverables", "Deliverables", listOf(2L)))),
                RequestFactItem(3, "- C?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
            ),
            sections = listOf(
                ValidatedSection(1, listOf(IntentAnswer("finance.arrangements", "Body A", listOf(1L)))),
                ValidatedSection(2, listOf(IntentAnswer("role.deliverables", "Body B", listOf(2L))))
            )
        )
        assertTrue(text.contains("Body A"))
        assertTrue(text.contains("Body B"))
        assertFalse(text.contains("1. Financial arrangements"))
        assertFalse(text.contains("UNSUPPORTED", ignoreCase = true))
    }

    @Test
    fun `cleanHeading strips bullets punctuation trailing and and capitalizes`() {
        assertEquals("Salary", AiReplyPointByPointComposer.cleanHeading("- Salary?"))
        assertEquals("Visa process", AiReplyPointByPointComposer.cleanHeading("1. Visa process？"))
        assertEquals(
            "The full name and registered location of your company",
            AiReplyPointByPointComposer.cleanHeading("- the full name and registered location of your company;")
        )
        assertEquals(
            "Contractual, financial and IP arrangements",
            AiReplyPointByPointComposer.cleanHeading("- contractual, financial and IP arrangements; and")
        )
        val long = "A".repeat(200)
        assertEquals(160, AiReplyPointByPointComposer.cleanHeading(long).length)
    }

    @Test
    fun `resolveGroupTitle merges company intents into company details`() {
        val title = AiReplyIntentCatalog.resolveGroupTitle(
            listOf("company.legal_name", "company.registered_location"),
            "fallback"
        )
        assertEquals("Company details", title)
    }

    @Test
    fun `resolveGroupTitle uses single intent title when unique`() {
        val title = AiReplyIntentCatalog.resolveGroupTitle(
            listOf("application.next_stages"),
            "fallback"
        )
        assertEquals("Next stages", title)
    }

    @Test
    fun `resolveGroupTitle falls back to cleanHeading for unknown intents`() {
        val title = AiReplyIntentCatalog.resolveGroupTitle(
            listOf("general.answer"),
            "- Some question text?"
        )
        assertEquals("Some question text", title)
    }

    // ── composeFallbackReference tests (Phase 09 I-5/I-6) ──

    private fun plan(vararg claimTuples: Triple<Int, String, List<Long>>, missing: List<Pair<Int, List<String>>> = emptyList()) =
        GroundedContentPlan(
            claims = claimTuples.map { GroundedClaimPlan(it.first.toString(), it.first, it.second, it.third) },
            paragraphs = claimTuples.mapIndexed { idx, t ->
                GroundedParagraphPlan(idx + 1, listOf(t.first.toString()))
            },
            missingFacts = missing.map { GroundedMissingFactPlan(it.first, it.second) },
            allowedActions = emptySet(),
            requiresReview = false
        )

    @Test
    fun `reference shows facts from plan sourceIds in request index order`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Fact A")))
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(rule(2, "Fact B")))

        val text = composer.composeFallbackReference(
            plan = plan(
                Triple(2, "intentB", listOf(2L)),
                Triple(1, "intentA", listOf(1L))
            ),
            requestFacts = listOf(
                RequestFactItem(1, "Q-A?", emptyList(), RequestGroundingStatus.GROUNDED),
                RequestFactItem(2, "Q-B?", emptyList(), RequestGroundingStatus.GROUNDED)
            )
        )
        assertTrue(text.contains("QA 规则参考内容"))
        assertTrue(text.contains("Fact A"))
        assertTrue(text.contains("Fact B"))
        val posA = text.indexOf("Fact A")
        val posB = text.indexOf("Fact B")
        assertTrue(posA < posB, "request index order: Q-A (1) before Q-B (2)")
    }

    @Test
    fun `reference shows missing requests from plan missingFacts`() {
        val text = composer.composeFallbackReference(
            plan = plan(
                Triple(1, "intentA", listOf(1L)),
                missing = listOf(2 to listOf("MissingIntent"))
            ),
            requestFacts = listOf(
                RequestFactItem(1, "Q-A?", listOf(1L), RequestGroundingStatus.GROUNDED,
                    intents = listOf(RequestIntentCoverage("intentA", "Fact A title", emptyList(), listOf(1L), "SUPPORTED", emptyList()))),
                RequestFactItem(2, "Q-B?", emptyList(), RequestGroundingStatus.UNSUPPORTED,
                    intents = listOf(RequestIntentCoverage("MissingIntent", "MissingIntent title", emptyList(), emptyList(), "MISSING", emptyList())))
            )
        )
        assertTrue(text.contains("缺失：暂无已审核事实"))
        assertTrue(text.contains("MissingIntent title"))
    }

    @Test
    fun `reference with only missing facts still shows all requests`() {
        val text = composer.composeFallbackReference(
            plan = plan(
                missing = listOf(1 to listOf("A"), 2 to listOf("B"))
            ),
            requestFacts = listOf(
                RequestFactItem(1, "Q-A?", emptyList(), RequestGroundingStatus.UNSUPPORTED),
                RequestFactItem(2, "Q-B?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
            )
        )
        assertTrue(text.contains("问题 1"))
        assertTrue(text.contains("问题 2"))
        assertTrue(text.contains("缺失：暂无已审核事实"))
    }

    @Test
    fun `reference deduplicates sourceIds across requests globally`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Shared fact")))
        val text = composer.composeFallbackReference(
            plan = plan(
                Triple(1, "a", listOf(1L)),
                Triple(2, "b", listOf(1L))
            ),
            requestFacts = listOf(
                RequestFactItem(1, "Q-A?", emptyList(), RequestGroundingStatus.GROUNDED),
                RequestFactItem(2, "Q-B?", emptyList(), RequestGroundingStatus.GROUNDED)
            )
        )
        assertEquals(1, Regex("Shared fact").findAll(text).count(), "shared fact appears once")
    }

    @Test
    fun `reference has no salutation greeting closing or CTA`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Please send your CV")))
        val text = composer.composeFallbackReference(
            plan = plan(Triple(1, "a", listOf(1L))),
            requestFacts = listOf(RequestFactItem(1, "Q?", emptyList(), RequestGroundingStatus.GROUNDED))
        )
        assertFalse(text.contains("Dear"))
        assertFalse(text.contains("Best regards"))
        assertFalse(text.contains("Thank you"))
        assertTrue(text.startsWith("QA 规则参考内容"))
    }

    @Test
    fun `reference does not contain intent keys or internal IDs`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "fact")))
        val text = composer.composeFallbackReference(
            plan = plan(Triple(1, "intentKey_x", listOf(1L))),
            requestFacts = listOf(RequestFactItem(1, "Q?", emptyList(), RequestGroundingStatus.GROUNDED))
        )
        assertFalse(text.contains("intentKey_x"))
        assertFalse(text.contains("claimKey"))
        assertFalse(text.contains("sourceIds"))
    }
}
