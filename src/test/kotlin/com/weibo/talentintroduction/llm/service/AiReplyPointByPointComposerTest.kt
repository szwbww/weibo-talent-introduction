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
        replySubject = "Re $id",
        enabled = true
    )

    private fun supportedIntent(key: String, title: String, evidenceIds: List<Long>) =
        RequestIntentCoverage(key, title, emptyList(), evidenceIds, "SUPPORTED", emptyList())

    @Test
    fun `composeFallback emits frame numbered sections for all requests including empty ones`() {
        stubFrame()
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Salary facts")))
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(rule(2, "Visa facts")))

        val text = composer.composeFallback(
            listOf(
                RequestFactItem(1, "- What is salary?", listOf(1L), RequestGroundingStatus.GROUNDED,
                    intents = listOf(supportedIntent("finance.arrangements", "Financial arrangements", listOf(1L)))),
                RequestFactItem(2, "- Visa process?", listOf(2L), RequestGroundingStatus.GROUNDED,
                    intents = listOf(supportedIntent("application.next_stages", "Next stages", listOf(2L))))
            )
        )

        assertTrue(text.startsWith("Dear \${expertName|Professor},"))
        assertTrue(text.contains(QaReplyComposer.GREETING))
        assertTrue(text.contains("1. Financial arrangements"))
        assertTrue(text.contains("2. Next stages"))
        assertTrue(text.contains("Salary facts"))
        assertTrue(text.contains("Visa facts"))
        assertTrue(text.contains(QaReplyComposer.CLOSING))
    }

    @Test
    fun `unsupported items get heading but no body in fallback`() {
        stubFrame()
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Salary only")))

        val text = composer.composeFallback(
            listOf(
                RequestFactItem(1, "- Salary?", listOf(1L), RequestGroundingStatus.GROUNDED,
                    intents = listOf(supportedIntent("finance.arrangements", "Financial arrangements", listOf(1L)))),
                RequestFactItem(2, "- Research match?", listOf(7L), RequestGroundingStatus.GROUNDED,
                    requiresResearchContext = true,
                    intents = listOf(RequestIntentCoverage("expertise.programme_fit", "Research fit", emptyList(), listOf(7L), "SUPPORTED", emptyList(), requiresResearchContext = true))),
                RequestFactItem(3, "- Unknown?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
            )
        )

        assertTrue(text.contains("1. Financial arrangements"))
        assertTrue(text.contains("Salary only"))
        assertTrue(text.contains("2. Research fit and enterprise projects"))
        assertFalse(text.contains("Research match"))
        assertTrue(text.contains("3. Unknown"))
        assertFalse(text.contains("UNSUPPORTED", ignoreCase = true))
        assertFalse(text.contains("This still needs confirmation"))
        assertFalse(text.contains("not covered by the approved information"))
    }

    @Test
    fun `partial has facts without confirmation trailer`() {
        stubFrame()
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Partial facts")))

        val text = composer.composeFallback(
            listOf(
                RequestFactItem(1, "Deliverables details?", listOf(1L), RequestGroundingStatus.PARTIAL,
                    intents = listOf(supportedIntent("role.deliverables", "Deliverables", listOf(1L))))
            )
        )

        assertTrue(text.contains("Partial facts"))
        assertFalse(text.contains("This still needs confirmation"))
    }

    @Test
    fun `identical fact bodies cross-reference later points`() {
        stubFrame()
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(rule(1, "Shared enterprise matching facts"))
        )

        val text = composer.composeFallback(
            listOf(
                RequestFactItem(3, "- Matching?", listOf(1L), RequestGroundingStatus.GROUNDED,
                    intents = listOf(supportedIntent("enterprise.matching", "Enterprise matching", listOf(1L)))),
                RequestFactItem(7, "- Enterprise projects?", listOf(1L), RequestGroundingStatus.GROUNDED,
                    intents = listOf(supportedIntent("enterprise.project_types", "Enterprise project types", listOf(1L))))
            )
        )

        assertTrue(text.contains("Shared enterprise matching facts"))
        assertTrue(text.contains("Please see point 3 above."))
        assertEquals(1, Regex("Shared enterprise matching facts").findAll(text).count())
    }

    @Test
    fun `composeFromSections cross-references identical answers`() {
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
        assertTrue(text.contains("Please see point 1 above."))
        assertEquals(1, Regex("Same answer body").findAll(text).count())
    }

    @Test
    fun `composeFromSections includes all request indexes even without answers`() {
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
        assertTrue(text.contains("1. Financial arrangements"))
        assertTrue(text.contains("Body A"))
        assertTrue(text.contains("2. Deliverables"))
        assertTrue(text.contains("Body B"))
        assertTrue(text.contains("3. C"))
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
    fun `all omitted items return frame with section headings but empty bodies`() {
        stubFrame()
        val text = composer.composeFallback(
            listOf(
                RequestFactItem(1, "- Research?", emptyList(), RequestGroundingStatus.UNSUPPORTED,
                    requiresResearchContext = true,
                    intents = listOf(RequestIntentCoverage("expertise.programme_fit", "Research fit and enterprise projects", emptyList(), emptyList(), "MISSING", emptyList())))
            )
        )
        assertTrue(text.contains("Dear \${expertName|Professor},"))
        assertTrue(text.contains(QaReplyComposer.CLOSING))
        assertTrue(text.contains("1. Research fit and enterprise projects"))
        assertFalse(text.contains("confirmation", ignoreCase = true))
        assertFalse(text.contains("insufficient", ignoreCase = true))
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

    @Test
    fun `research intent fallback consumes supported evidence`() {
        stubFrame()
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(rule(1, "Programme scope includes ML, NLP, and computer vision."))
        )

        val text = composer.composeFallback(
            listOf(
                RequestFactItem(
                    1, "- Research fit?", listOf(1L), RequestGroundingStatus.GROUNDED,
                    requiresResearchContext = true,
                    intents = listOf(
                        RequestIntentCoverage(
                            "expertise.programme_fit",
                            "Research fit and enterprise projects",
                            listOf("programme.scope"),
                            listOf(1L),
                            "SUPPORTED",
                            emptyList(),
                            requiresResearchContext = true
                        )
                    )
                )
            )
        )

        assertTrue(text.contains("Research fit and enterprise projects"))
        assertTrue(text.contains("Programme scope includes ML, NLP, and computer vision."))
        assertFalse(text.contains("This still needs confirmation"))
    }

    @Test
    fun `research fallback does not dump profile text`() {
        stubFrame()
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(rule(1, "Expert profile: Dr. Smith, PhD in ML."))
        )

        val text = composer.composeFallback(
            listOf(
                RequestFactItem(
                    1, "- Research fit?", listOf(1L), RequestGroundingStatus.GROUNDED,
                    requiresResearchContext = true,
                    intents = listOf(
                        RequestIntentCoverage(
                            "expertise.programme_fit",
                            "Research fit and enterprise projects",
                            listOf("programme.scope"),
                            listOf(1L),
                            "SUPPORTED",
                            emptyList(),
                            requiresResearchContext = true
                        )
                    )
                )
            )
        )

        assertTrue(text.contains("Research fit and enterprise projects"))
        assertTrue(text.contains("Expert profile: Dr. Smith, PhD in ML."))
    }
}
