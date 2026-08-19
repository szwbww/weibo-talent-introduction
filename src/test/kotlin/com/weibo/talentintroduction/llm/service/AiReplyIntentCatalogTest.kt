package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.service.QaCoverageKeyCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiReplyIntentCatalogTest {

    // ── I-2 exact matrix: seven groups, fixed order ─────────────────────────

    @Test
    fun `group 1 - research fit and enterprise projects`() {
        val keys = AiReplyIntentCatalog.matchIntents(
            "something like expertise/research fit + enterprise projects in programme scope"
        ).map { it.key }
        assertEquals(listOf("expertise.programme_fit", "enterprise.project_types"), keys)
    }

    @Test
    fun `group 2 - company details`() {
        val keys = AiReplyIntentCatalog.matchIntents("full name and registered location").map { it.key }
        assertEquals(listOf("company.legal_name", "company.registered_location"), keys)
    }

    @Test
    fun `group 3 - programme purpose and structure British spelling`() {
        val keys = AiReplyIntentCatalog.matchIntents(
            "purpose and structure of the programme"
        ).map { it.key }
        assertEquals(listOf("programme.purpose", "programme.structure"), keys)
    }

    @Test
    fun `group 3 - programme purpose and structure American spelling`() {
        val keys = AiReplyIntentCatalog.matchIntents(
            "purpose and structure of the program"
        ).map { it.key }
        assertEquals(listOf("programme.purpose", "programme.structure"), keys)
    }

    @Test
    fun `group 4 - selection and matching`() {
        val keys = AiReplyIntentCatalog.matchIntents("selected and matched").map { it.key }
        assertEquals(listOf("researcher.selection", "enterprise.matching"), keys)
    }

    @Test
    fun `group 4 - selected and matched with enterprise projects drops project types object`() {
        val keys = AiReplyIntentCatalog.matchIntents(
            "How researchers are selected and matched with enterprise projects"
        ).map { it.key }
        assertEquals(listOf("researcher.selection", "enterprise.matching"), keys)
        assertEquals(
            "Selection and enterprise matching",
            AiReplyIntentCatalog.resolveGroupTitle(keys, "")
        )
    }

    @Test
    fun `standalone types of enterprise projects still matches project types`() {
        val keys = AiReplyIntentCatalog.matchIntents(
            "What types of enterprise projects do you manage?"
        ).map { it.key }
        assertEquals(listOf("enterprise.project_types"), keys)
    }

    @Test
    fun `selection matching with explicit project type ask keeps three intents`() {
        val keys = AiReplyIntentCatalog.matchIntents(
            "How are researchers selected and matched, and what types of enterprise projects do you manage?"
        ).map { it.key }
        assertEquals(
            listOf("enterprise.project_types", "researcher.selection", "enterprise.matching"),
            keys
        )
    }

    @Test
    fun `group 5 - responsibilities and deliverables`() {
        val keys = AiReplyIntentCatalog.matchIntents("responsibilities and deliverables").map { it.key }
        assertEquals(listOf("role.responsibilities", "role.deliverables"), keys)
    }

    @Test
    fun `group 6 - contractual financial intellectual-property arrangements with hyphen`() {
        val keys = AiReplyIntentCatalog.matchIntents(
            "contractual, financial, intellectual-property arrangements"
        ).map { it.key }
        assertEquals(listOf("contract.terms", "finance.arrangements", "ip.arrangements"), keys)
    }

    @Test
    fun `group 7 - next stages`() {
        val keys = AiReplyIntentCatalog.matchIntents("next stages").map { it.key }
        assertEquals(listOf("application.next_stages"), keys)
    }

    // ── I-1 URL / query-fragment boundary cases ──────────────────────────────

    @Test
    fun `URL containing selected=true does not match researcher selection`() {
        val result = AiReplyIntentCatalog.matchIntents(
            "see https://portal.example.com/list?selected=true for details"
        )
        assertFalse(result.any { it.key == "researcher.selection" })
    }

    @Test
    fun `uppercase HTTPS URL with selected=true does not match researcher selection`() {
        val result = AiReplyIntentCatalog.matchIntents(
            "see HTTPS://Portal.Example.COM/list?Selected=true for details"
        )
        assertFalse(result.any { it.key == "researcher.selection" })
    }

    @Test
    fun `query fragment authorId does not become noise intent`() {
        val result = AiReplyIntentCatalog.matchIntents("citations?user=abc&authorId=123")
        assertFalse(result.any { it.key == "researcher.selection" })
    }

    // ── I-5 word-boundary cases ───────────────────────────────────────────────

    @Test
    fun `preselected does not match researcher selection`() {
        val result = AiReplyIntentCatalog.matchIntents("I am preselected for this")
        assertFalse(result.any { it.key == "researcher.selection" })
    }

    @Test
    fun `plain selected matches researcher selection`() {
        val result = AiReplyIntentCatalog.matchIntents("I was selected for the programme")
        assertTrue(result.any { it.key == "researcher.selection" })
    }

    @Test
    fun `programme not matched as substring of reprogramme`() {
        val result = AiReplyIntentCatalog.matchIntents("reprogramme the system")
        assertFalse(result.any { it.key.startsWith("programme.") })
    }

    // ── resolveGroupTitle: seven fixed English titles ────────────────────────

    @Test
    fun `resolveGroupTitle returns fixed titles for all seven intent sets`() {
        assertEquals(
            "Research fit and enterprise projects",
            AiReplyIntentCatalog.resolveGroupTitle(
                listOf("expertise.programme_fit", "enterprise.project_types"), ""
            )
        )
        assertEquals(
            "Company details",
            AiReplyIntentCatalog.resolveGroupTitle(
                listOf("company.legal_name", "company.registered_location"), ""
            )
        )
        assertEquals(
            "Programme purpose and structure",
            AiReplyIntentCatalog.resolveGroupTitle(
                listOf("programme.purpose", "programme.structure"), ""
            )
        )
        assertEquals(
            "Selection and enterprise matching",
            AiReplyIntentCatalog.resolveGroupTitle(
                listOf("researcher.selection", "enterprise.matching"), ""
            )
        )
        assertEquals(
            "Responsibilities and deliverables",
            AiReplyIntentCatalog.resolveGroupTitle(
                listOf("role.responsibilities", "role.deliverables"), ""
            )
        )
        assertEquals(
            "Contractual, financial and IP arrangements",
            AiReplyIntentCatalog.resolveGroupTitle(
                listOf("contract.terms", "finance.arrangements", "ip.arrangements"), ""
            )
        )
        assertEquals(
            "Next stages",
            AiReplyIntentCatalog.resolveGroupTitle(listOf("application.next_stages"), "")
        )
    }

    // ── Seven due-diligence question exact intent matrix (Phase 09 I-2) ──

    private val q1 = "Before I submit my CV for the preliminary assessment, I would appreciate some additional information regarding the collaboration: Is the research advisory role compensated?"
    private val q2 = "If so, could you please provide information about the remuneration structure?"
    private val q3 = "What is the expected time commitment and typical duration of advisory projects?"
    private val q4 = "Could you share examples of the types of Chinese enterprises or institutions involved in the program?"
    private val q5 = "How are intellectual property rights, publication authorship, and research confidentiality managed?"
    private val q6 = "Will a formal agreement or contract be provided before any collaboration begins?"
    private val q7 = "Are there any costs or obligations for participants at any stage of the process?"

    private fun assertExactKeys(question: String, vararg expected: String) {
        val keys = AiReplyIntentCatalog.matchIntents(question).map { it.key }
        assertEquals(expected.toSet(), keys.toSet())
        assertEquals(expected.size, keys.size, "intent keys must contain no extras or duplicates")
    }

    @Test
    fun `Q1 compensation existence matches finance dot arrangements`() {
        assertExactKeys(q1, "finance.arrangements")
    }

    @Test
    fun `Q2 remuneration structure matches finance intents`() {
        assertExactKeys(q2, "finance.compensation_structure", "finance.arrangements")
    }

    @Test
    fun `Q3 time commitment and duration matches work intents`() {
        assertExactKeys(q3, "work.time_commitment", "work.advisory_duration")
    }

    @Test
    fun `Q4 Chinese enterprise types matches enterprise dot project_types`() {
        assertExactKeys(q4, "enterprise.project_types")
    }

    @Test
    fun `Q5 splits IP publication and confidentiality into separate intents`() {
        assertExactKeys(q5, "ip.arrangements", "publication.authorship", "confidentiality.research")
    }

    @Test
    fun `Q6 formal agreement matches contract dot terms`() {
        assertExactKeys(q6, "contract.terms")
    }

    @Test
    fun `Q7 costs obligations matches fee policy not finance arrangements`() {
        assertExactKeys(q7, "fees.policy")
    }

    // ── P1 (plan 01-fact-and-catalog): programme identity intents ──

    // I-7: verbatim orthopaedic trigger letter, character-for-character from the
    // plan header (fixture authority — never rewrite, truncate or paraphrase).
    private val orthopaedicLetter =
        "Thank you for contacting me and for your explanation of how the programme works. I would be open to exploring whether there could be a suitable match. My current clinical and research interests are mainly focused on orthopaedic trauma, particularly femoral fractures, peri-implant femoral fractures, fracture fixation strategies, implant-related complications, and clinical research and registry development in these areas. Before going further, I would appreciate some additional information about the programme, particularly its official name, the government body or institution supporting it, the usual form of collaboration with the Chinese partner companies, and the general arrangements regarding remuneration and intellectual property. At this stage, I would be happy to continue the conversation by email."

    @Test
    fun `orthopaedic letter matches exactly the five identity intents`() {
        val keys = AiReplyIntentCatalog.matchIntents(orthopaedicLetter).map { it.key }
        assertEquals(
            setOf(
                "programme.name", "governance.sponsor", "collaboration.form",
                "finance.arrangements", "ip.arrangements"
            ),
            keys.toSet()
        )
        assertFalse(keys.contains("general.answer"), "disambiguated intents must be non-empty")
    }

    @Test
    fun `three existing fixtures keep their verbatim intent lists`() {
        // N1 regression: the 20 pre-existing intent definitions are untouched.
        assertExactKeys(q1, "finance.arrangements")
        assertExactKeys(q3, "work.time_commitment", "work.advisory_duration")
        assertExactKeys(q5, "ip.arrangements", "publication.authorship", "confidentiality.research")
    }

    // I-1 bidirectional guard exemptions (plan A-4). Must stay explicit so that a
    // later plan removing an exemption immediately turns these tests red.
    private val intentSideExemptions = setOf(
        // O5 reverse mismatch: intents reference keys the catalog does not have.
        "work.time_commitment",
        "work.advisory_duration"
    )
    private val catalogSideExemptions = setOf(
        // O4: orphan coverage keys, deferral declared by the master plan.
        "general.answer",
        "application.required_materials",
        "work.relocation",
        // Referenced only by matchIntents' runtime copy for next-stages + timing
        // asks (AiReplyIntentCatalog.kt), never by a definition's required or
        // alternative list — hence an O4-class orphan for this guard.
        "application.timeline"
    )

    @Test
    fun `every intent coverage key is a valid catalog key`() {
        val referenced = AiReplyIntentCatalog.definitions
            .flatMap { it.requiredCoverageKeys + it.alternativeCoverageKeys }
            .toSet()
        val invalid = (referenced - intentSideExemptions).filterNot { QaCoverageKeyCatalog.isValid(it) }
        assertTrue(
            invalid.isEmpty(),
            "intent coverage keys missing from QaCoverageKeyCatalog: ${invalid.sorted()}"
        )
    }

    @Test
    fun `every catalog key is referenced by at least one intent`() {
        val referenced = AiReplyIntentCatalog.definitions
            .flatMap { it.requiredCoverageKeys + it.alternativeCoverageKeys }
            .toSet()
        val orphans = QaCoverageKeyCatalog.all()
            .map { it.key }
            .filterNot { it in referenced || it in catalogSideExemptions }
        assertTrue(
            orphans.isEmpty(),
            "coverage keys referenced by no intent: ${orphans.sorted()}"
        )
    }

    @Test
    fun `new intent keywords satisfy both sides of the parity condition`() {
        // I-2 mechanical parity: for each new intent there is a rule keyword k
        // that is (a) a substring of the normalized letter and (b) aligned with
        // the intent's canonicalized title/aliases (scored through the production
        // canonicalize() path in scoreRuleIntentAlignment).
        val normalizedLetter = QaFactKeywordMatcher.normalize(orthopaedicLetter)
        val parity = listOf(
            Triple("programme.name", "official name", 901L),
            Triple("governance.sponsor", "government body", 902L),
            Triple("collaboration.form", "form of collaboration", 903L)
        )
        parity.forEach { (intentKey, keyword, ruleId) ->
            assertTrue(
                normalizedLetter.contains(keyword),
                "letter must contain keyword '$keyword' for $intentKey"
            )
            val intent = AiReplyIntentCatalog.definitions.single { it.key == intentKey }
            val rule = QaRule(
                id = ruleId,
                categoryId = 1,
                keywords = keyword,
                replySubject = null,
                replyBody = "fact",
                answerBody = "fact",
                priority = 100,
                replyPolicy = QaReplyPolicy.AUTO.name,
                enabled = true
            )
            assertTrue(
                AiReplyIntentCatalog.scoreRuleIntentAlignment(rule, intent) > 0,
                "keyword '$keyword' must align with canonicalized $intentKey title/aliases"
            )
        }
    }
}
