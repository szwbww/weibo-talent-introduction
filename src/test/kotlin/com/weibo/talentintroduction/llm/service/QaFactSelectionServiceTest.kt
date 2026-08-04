package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.Optional

class QaFactSelectionServiceTest {
    private val repository = Mockito.mock(QaRuleRepository::class.java)
    private val service = QaFactSelectionService(repository)

    private fun rule(
        id: Long,
        keywords: String,
        answerBody: String,
        priority: Int = 100,
        replyPolicy: String = QaReplyPolicy.AUTO.name,
        replySubject: String? = null
    ) = QaRule(
        id = id,
        categoryId = 1,
        keywords = keywords,
        replySubject = replySubject,
        replyBody = answerBody,
        answerBody = answerBody,
        priority = priority,
        replyPolicy = replyPolicy,
        enabled = true
    )

    @Test
    fun `assigns keyword matched rules without coverage keys`() {
        val selectionRule = rule(
            id = 10,
            keywords = "selection,selected,criteria",
            answerBody = "Researchers are selected through a structured review."
        )
        val matchingRule = rule(
            id = 11,
            keywords = "matching,matched,enterprise",
            answerBody = "Matching pairs researchers with partner enterprises."
        )
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(selectionRule, matchingRule))

        val resolved = service.select(
            inboundText = """
                How are researchers selected?
                How are researchers matched with enterprises?
            """.trimIndent(),
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        assertTrue(resolved.sendQaRuleIds.containsAll(listOf(10L, 11L)))
        assertFalse(resolved.requestFacts.any { it.intents.any { intent -> intent.requiredCoverageKeys.isNotEmpty() } })
    }

    @Test
    fun `each rule appears in at most one intent evidence set per request`() {
        val sharedRule = rule(
            id = 20,
            keywords = "responsibilities,deliverables,role",
            answerBody = "Role expectations include research delivery."
        )
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(sharedRule))

        val resolved = service.select(
            inboundText = "What are my responsibilities and deliverables?",
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        val evidenceCounts = resolved.requestFacts
            .flatMap { it.intents }
            .flatMap { it.evidenceRuleIds }
            .groupingBy { it }
            .eachCount()
        assertTrue(evidenceCounts.values.all { it <= 1 })
    }

    @Test
    fun `explicit NEVER rule is rejected`() {
        Mockito.`when`(repository.findById(99L)).thenReturn(
            Optional.of(
                rule(
                    id = 99,
                    keywords = "fee",
                    answerBody = "Internal fee note.",
                    replyPolicy = QaReplyPolicy.NEVER.name
                )
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.select(
                inboundText = "What is the fee?",
                selectedRuleIds = listOf(99L),
                researchProfileSufficient = true
            )
        }
    }

    @Test
    fun `unsupported request yields empty send ids and blocked readiness input`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(emptyList())

        val resolved = service.select(
            inboundText = "Can you guarantee 10 million RMB?",
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        assertEquals(emptyList<Long>(), resolved.sendQaRuleIds)
        assertTrue(resolved.requestFacts.all { it.status == RequestGroundingStatus.UNSUPPORTED })
    }

    @Test
    fun `explicit rules must match at least one extracted request`() {
        val rule = rule(id = 3, keywords = "salary", answerBody = "Salary facts")
        Mockito.`when`(repository.findById(3L)).thenReturn(Optional.of(rule))

        assertThrows(IllegalArgumentException::class.java) {
            service.select(
                inboundText = "Unrelated text",
                selectedRuleIds = listOf(3L),
                researchProfileSufficient = true
            )
        }
    }

    @Test
    fun `explicit rules never bypass keyword matching per request`() {
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val visaRule = rule(id = 2, keywords = "visa", answerBody = "Visa body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(visaRule))

        val resolved = service.select(
            inboundText = "- Salary?\n- Visa?",
            selectedRuleIds = listOf(1L),
            researchProfileSufficient = true
        )

        assertEquals(listOf(1L), resolved.requestFacts[0].factRuleIds)
        assertTrue(resolved.requestFacts[1].factRuleIds.isEmpty())
        assertEquals(listOf(1L), resolved.sendQaRuleIds)
    }

    @Test
    fun `explicit mixed matching and non-matching rules is rejected`() {
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val feeRule = rule(id = 2, keywords = "fee", answerBody = "Fee body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(feeRule))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.select(
                inboundText = "What is salary?",
                selectedRuleIds = listOf(1L, 2L),
                researchProfileSufficient = true
            )
        }
        assertTrue(ex.message!!.contains("2"))
    }

    @Test
    fun `send ids follow request then priority order`() {
        val lowPriority = rule(id = 1, keywords = "salary", answerBody = "Salary A", priority = 200)
        val highPriority = rule(id = 2, keywords = "visa", answerBody = "Visa B", priority = 50)
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(lowPriority, highPriority))

        val resolved = service.select(
            inboundText = """
                What is the salary support?
                What is the visa process?
            """.trimIndent(),
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        assertEquals(listOf(1L, 2L), resolved.sendQaRuleIds)
    }

    // ── Seven due-diligence question 4/1/2 matrix (Phase 09 I-2) ──

    private val q1Due = "Before I submit my CV for the preliminary assessment, I would appreciate some additional information regarding the collaboration: Is the research advisory role compensated?"
    private val q2Due = "If so, could you please provide information about the remuneration structure?"
    private val q3Due = "What is the expected time commitment and typical duration of advisory projects?"
    private val q4Due = "Could you share examples of the types of Chinese enterprises or institutions involved in the program?"
    private val q5Due = "How are intellectual property rights, publication authorship, and research confidentiality managed?"
    private val q6Due = "Will a formal agreement or contract be provided before any collaboration begins?"
    private val q7Due = "Are there any costs or obligations for participants at any stage of the process?"

    private val allDueDiligence = """
        $q1Due
        $q2Due
        $q3Due
        $q4Due
        $q5Due
        $q6Due
        $q7Due
    """.trimIndent()

    @Test
    fun `seven due diligence questions keep unsupported publication and confidentiality facts separate`() {
        val fundingRule = rule(
            id = 1, keywords = "compensated,advisory role compensated",
            answerBody = "The advisory role is compensated through a government-funded stipend.",
            replySubject = "Funding support"
        ).copy(coverageKeys = "finance.government_funding,finance.enterprise_compensation")
        val durationRule = rule(
            id = 3, keywords = "typical duration,advisory project duration,duration of advisory projects",
            answerBody = "Advisory projects typically last 2-3 years.",
            replySubject = "Program overview"
        ).copy(coverageKeys = "work.advisory_duration")
        val ipRule = rule(
            id = 5, keywords = "intellectual property,ip rights,ownership",
            answerBody = "IP rights are negotiated per project.",
            replySubject = "IP arrangements"
        ).copy(coverageKeys = "ip.arrangements")
        val contractRule = rule(
            id = 6, keywords = "formal agreement,formal contract,contract,before any collaboration begins",
            answerBody = "A formal agreement is signed before collaboration begins.",
            replySubject = "Contract and IP arrangements"
        ).copy(coverageKeys = "contract.party,contract.terms")
        val costRule = rule(
            id = 7, keywords = "never charge,any fee,any fees,any costs,money transfer,charge,charges,cost,costs",
            answerBody = "There are no costs or fees for participants at any stage.",
            replySubject = "Costs"
        ).copy(coverageKeys = "fees.policy")

        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(
            listOf(fundingRule, durationRule, ipRule, contractRule, costRule)
        )

        val resolved = service.select(
            inboundText = allDueDiligence,
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        assertEquals(7, resolved.requestFacts.size)
        val statuses = resolved.requestFacts.map { it.status }
        assertEquals(
            listOf(
                RequestGroundingStatus.GROUNDED,
                RequestGroundingStatus.UNSUPPORTED,
                RequestGroundingStatus.PARTIAL,
                RequestGroundingStatus.UNSUPPORTED,
                RequestGroundingStatus.PARTIAL,
                RequestGroundingStatus.GROUNDED,
                RequestGroundingStatus.GROUNDED
            ),
            statuses
        )

        val grounded = reqFactStatusCount(resolved, RequestGroundingStatus.GROUNDED)
        val partial = reqFactStatusCount(resolved, RequestGroundingStatus.PARTIAL)
        val unsupported = reqFactStatusCount(resolved, RequestGroundingStatus.UNSUPPORTED)
        assertEquals(3, grounded)
        assertEquals(2, partial)
        assertEquals(2, unsupported)

        val ipFact = resolved.requestFacts.find { it.requestText.contains("publication authorship") }
        assertNotNull(ipFact)
        assertEquals(
            "SUPPORTED",
            ipFact!!.intents.single { it.intentKey == "ip.arrangements" }.status
        )
        assertEquals(listOf(5L), ipFact.intents.single { it.intentKey == "ip.arrangements" }.evidenceRuleIds)
        assertEquals(
            "MISSING",
            ipFact.intents.single { it.intentKey == "publication.authorship" }.status
        )
        assertEquals(
            "MISSING",
            ipFact.intents.single { it.intentKey == "confidentiality.research" }.status
        )

        val contractFact = resolved.requestFacts.find { it.requestText.contains("formal agreement") }
        assertNotNull(contractFact)
        assertEquals(
            "SUPPORTED",
            contractFact!!.intents.single { it.intentKey == "contract.terms" }.status
        )
        assertEquals(listOf(6L), contractFact.intents.single { it.intentKey == "contract.terms" }.evidenceRuleIds)

        val feeFact = resolved.requestFacts.find { it.requestText.contains("any costs") }
        assertNotNull(feeFact)
        assertEquals(
            "SUPPORTED",
            feeFact!!.intents.single { it.intentKey == "fees.policy" }.status
        )
        assertEquals(listOf(7L), feeFact.intents.single { it.intentKey == "fees.policy" }.evidenceRuleIds)
        assertEquals(7L, feeFact.factRuleIds.single())
    }

    @Test
    fun `compensation availability does not support compensation structure`() {
        val fundingRule = rule(
            id = 1, keywords = "compensated,advisory role compensated",
            answerBody = "The advisory role is compensated.",
            replySubject = "Funding support"
        )
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(fundingRule))

        val resolved = service.select(
            inboundText = "$q1Due\n$q2Due",
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        val q1Fact = resolved.requestFacts.find { it.requestText.contains("advisory role compensated") }
        val q2Fact = resolved.requestFacts.find { it.requestText.contains("remuneration structure") }
        assertNotNull(q1Fact)
        assertNotNull(q2Fact)
        assertEquals(RequestGroundingStatus.GROUNDED, q1Fact!!.status)
        assertEquals(RequestGroundingStatus.UNSUPPORTED, q2Fact!!.status)
    }

    @Test
    fun `publication authorship without an approved fact remains grounded missing`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(emptyList())

        val resolved = service.select(
            inboundText = "How is publication authorship managed?",
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        val fact = resolved.requestFacts.single()
        assertEquals(RequestGroundingStatus.UNSUPPORTED, fact.status)
        assertEquals("publication.authorship", fact.intents.single().intentKey)
        assertTrue(AiReplyGroundedContentPlanner.hasTrustSensitiveNoFacts(resolved.requestFacts))
    }

    @Test
    fun `duration facts do not support time commitment`() {
        val durationRule = rule(
            id = 3, keywords = "typical duration,advisory project duration",
            answerBody = "Advisory projects typically last 2-3 years.",
            replySubject = "Program overview"
        )
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(durationRule))

        val resolved = service.select(
            inboundText = q3Due,
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        val fact = resolved.requestFacts[0]
        assertEquals(RequestGroundingStatus.PARTIAL, fact.status)
        val supportedIntents = fact.intents.filter { it.status == "SUPPORTED" }.map { it.intentKey }
        val missingIntents = fact.intents.filter { it.status == "MISSING" }.map { it.intentKey }
        assertTrue(supportedIntents.contains("work.advisory_duration"))
        assertTrue(missingIntents.contains("work.time_commitment"))
    }

    @Test
    fun `matching facts do not support enterprise examples`() {
        val matchingRule = rule(
            id = 4, keywords = "matching,partner enterprise,how are researchers matched",
            answerBody = "Researchers are matched with partner enterprises.",
            replySubject = "Enterprise matching"
        )
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(matchingRule))

        val resolved = service.select(
            inboundText = q4Due,
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        val fact = resolved.requestFacts[0]
        assertEquals(RequestGroundingStatus.UNSUPPORTED, fact.status)
    }

    @Test
    fun `high risk intent rejects keyword matched rule with blank coverage`() {
        val ipRule = rule(
            id = 5, keywords = "intellectual property,ip rights,ownership",
            answerBody = "IP rights are negotiated per project."
        )
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(ipRule))

        val resolved = service.select(
            inboundText = "How are intellectual property rights managed?",
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        val fact = resolved.requestFacts.single()
        assertEquals(RequestGroundingStatus.UNSUPPORTED, fact.status)
        assertEquals("MISSING", fact.intents.single { it.intentKey == "ip.arrangements" }.status)
        assertTrue(fact.factRuleIds.isEmpty())
    }

    @Test
    fun `high risk intent rejects rule whose coverage does not intersect`() {
        val materialRule = rule(
            id = 8, keywords = "confidential,materials",
            answerBody = "Application materials are kept confidential.",
            replySubject = "Application material confidentiality"
        ).copy(coverageKeys = "confidentiality.materials")
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(materialRule))

        val resolved = service.select(
            inboundText = "How is research confidentiality managed?",
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        val fact = resolved.requestFacts.single()
        assertEquals(RequestGroundingStatus.UNSUPPORTED, fact.status)
        assertEquals("MISSING", fact.intents.single { it.intentKey == "confidentiality.research" }.status)
        assertTrue(fact.factRuleIds.isEmpty())
    }

    @Test
    fun `any costs or fees matches only fee policy intent`() {
        val feeRule = rule(
            id = 7, keywords = "any costs,any fees,costs",
            answerBody = "We never charge participants at any stage.",
            replySubject = "Participant fee policy"
        ).copy(coverageKeys = "fees.policy")
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(feeRule))

        val resolved = service.select(
            inboundText = "Are there any costs or fees for participants?",
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        val fact = resolved.requestFacts.single()
        assertEquals(listOf("fees.policy"), fact.intents.map { it.intentKey })
        assertEquals("SUPPORTED", fact.intents.single().status)
        assertEquals(RequestGroundingStatus.GROUNDED, fact.status)
        assertEquals(listOf(7L), fact.factRuleIds)
    }

    @Test
    fun `application material confidentiality matches materials intent only`() {
        val materialRule = rule(
            id = 8, keywords = "confidential,materials confidential,application materials",
            answerBody = "Application materials are kept confidential.",
            replySubject = "Application material confidentiality"
        ).copy(coverageKeys = "confidentiality.materials")
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(materialRule))

        val resolved = service.select(
            inboundText = "Please keep my application materials confidential.",
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        val fact = resolved.requestFacts.single()
        assertEquals("SUPPORTED", fact.intents.single { it.intentKey == "confidentiality.materials" }.status)
        assertEquals(listOf(8L), fact.intents.single { it.intentKey == "confidentiality.materials" }.evidenceRuleIds)
        assertEquals(RequestGroundingStatus.GROUNDED, fact.status)
    }

    @Test
    fun `registered compensation structure coverage supports its intent`() {
        val rule = rule(
            id = 11, keywords = "amount breakdown,compensation structure",
            answerBody = "The compensation structure is set out in the written agreement.",
            replySubject = "Compensation structure"
        ).copy(coverageKeys = "finance.compensation_structure")
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(rule))

        val resolved = service.select(
            inboundText = "Could you share the amount breakdown?",
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        val fact = resolved.requestFacts.single()
        assertEquals("SUPPORTED", fact.intents.single { it.intentKey == "finance.compensation_structure" }.status)
        assertEquals(listOf(11L), fact.intents.single { it.intentKey == "finance.compensation_structure" }.evidenceRuleIds)
        assertEquals(RequestGroundingStatus.GROUNDED, fact.status)
        assertEquals(listOf(11L), fact.factRuleIds)
    }

    @Test
    fun `registered publication authorship coverage supports its intent`() {
        val rule = rule(
            id = 12, keywords = "publication authorship,publication rights",
            answerBody = "Publication authorship is set out in the written agreement.",
            replySubject = "Publication authorship"
        ).copy(coverageKeys = "publication.authorship")
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(rule))

        val resolved = service.select(
            inboundText = "How is publication authorship managed?",
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        val fact = resolved.requestFacts.single()
        assertEquals("SUPPORTED", fact.intents.single { it.intentKey == "publication.authorship" }.status)
        assertEquals(listOf(12L), fact.intents.single { it.intentKey == "publication.authorship" }.evidenceRuleIds)
        assertEquals(RequestGroundingStatus.GROUNDED, fact.status)
        assertEquals(listOf(12L), fact.factRuleIds)
    }

    @Test
    fun `registered research confidentiality coverage supports its intent`() {
        val rule = rule(
            id = 13, keywords = "research confidentiality,confidentiality policy",
            answerBody = "Research data confidentiality is governed by the collaboration agreement.",
            replySubject = "Research confidentiality"
        ).copy(coverageKeys = "confidentiality.research")
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(rule))

        val resolved = service.select(
            inboundText = "How is research confidentiality managed?",
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        val fact = resolved.requestFacts.single()
        assertEquals("SUPPORTED", fact.intents.single { it.intentKey == "confidentiality.research" }.status)
        assertEquals(listOf(13L), fact.intents.single { it.intentKey == "confidentiality.research" }.evidenceRuleIds)
        assertEquals(RequestGroundingStatus.GROUNDED, fact.status)
        assertEquals(listOf(13L), fact.factRuleIds)
    }

    @Test
    fun `obligations only request never selects the fee rule`() {
        val feeRule = rule(
            id = 7,
            keywords = "never charge,any fee,any fees,any costs,money transfer,charge,charges,cost,costs",
            answerBody = "We never charge any fees throughout the entire process.",
            replySubject = "Participant fee policy"
        ).copy(coverageKeys = "fees.policy")
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(feeRule))

        val resolved = service.select(
            inboundText = "Are there any obligations for participants at any stage of the process?",
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        val fact = resolved.requestFacts.single()
        assertEquals(RequestGroundingStatus.UNSUPPORTED, fact.status)
        assertTrue(fact.factRuleIds.isEmpty(), "fee rule must not be evidence for a bare obligations request")
        assertFalse(resolved.sendQaRuleIds.contains(7L), "fee rule id must not be selected")
        assertEquals("MISSING", fact.intents.single().status)
    }

    @Test
    fun `legacy intent rejects rule with non intersecting non empty coverage`() {
        val rule = rule(
            id = 14,
            keywords = "deliverables,responsibilities",
            answerBody = "Deliverables are defined per project.",
            replySubject = "Deliverables"
        ).copy(coverageKeys = "company.legal_name")
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(rule))

        val resolved = service.select(
            inboundText = "What are my expected deliverables?",
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        val fact = resolved.requestFacts.single()
        assertEquals("MISSING", fact.intents.single { it.intentKey == "role.deliverables" }.status)
        assertTrue(fact.factRuleIds.isEmpty())
        assertEquals(RequestGroundingStatus.UNSUPPORTED, fact.status)
    }

    @Test
    fun `legacy non high risk intent keeps blank coverage assignment`() {
        val selectionRule = rule(
            id = 10,
            keywords = "selection,selected,criteria",
            answerBody = "Researchers are selected through a structured review."
        )
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(selectionRule))

        val resolved = service.select(
            inboundText = "How are researchers selected?",
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        val fact = resolved.requestFacts.single()
        assertEquals("SUPPORTED", fact.intents.single { it.intentKey == "researcher.selection" }.status)
        assertEquals(RequestGroundingStatus.GROUNDED, fact.status)
    }

    private fun reqFactStatusCount(resolved: ResolvedQaRules, status: RequestGroundingStatus): Int =
        resolved.requestFacts.count { it.status == status }
}
