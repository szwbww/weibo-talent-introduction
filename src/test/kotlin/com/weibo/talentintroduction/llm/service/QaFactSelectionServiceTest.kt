package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.llm.config.AskEnumeratorProperties
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
import java.nio.file.Files
import java.nio.file.Path
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

    // ── Workbench matrix selection (plan 01, I-1..I-4) ──

    @Test
    fun `auto workbench selection assigns shared rule to the first accepting request only`() {
        val shared = rule(id = 1, keywords = "salary,visa", answerBody = "Shared body")
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(shared))

        val resolved = service.selectForWorkbench(
            inboundText = "- Salary?\n- Visa?",
            selectionsByRequest = null,
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        assertEquals(listOf(1L), resolved.requestFacts[0].factRuleIds)
        assertTrue(resolved.requestFacts[1].factRuleIds.isEmpty())
        assertEquals(listOf(1L), resolved.sendQaRuleIds)
    }

    @Test
    fun `matrix mode accepts exact per request assignment and builds the ordered union`() {
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val visaRule = rule(id = 2, keywords = "visa", answerBody = "Visa body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(visaRule))

        val resolved = service.selectForWorkbench(
            inboundText = "- Salary?\n- Visa?",
            selectionsByRequest = listOf(listOf(1L), listOf(2L)),
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        assertEquals(listOf(1L), resolved.requestFacts[0].factRuleIds)
        assertEquals(listOf(2L), resolved.requestFacts[1].factRuleIds)
        assertEquals(listOf(1L, 2L), resolved.sendQaRuleIds)
    }

    @Test
    fun `matrix mode rejects the same rule assigned to two requests`() {
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val visaRule = rule(id = 2, keywords = "visa", answerBody = "Visa body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(visaRule))

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.selectForWorkbench(
                inboundText = "- Salary?\n- Visa?",
                selectionsByRequest = listOf(listOf(1L, 2L), listOf(2L)),
                requestedFactIds = null,
                researchProfileSufficient = true
            )
        }

        assertEquals("TRUST_REPLY_FACT_ALREADY_ASSIGNED", ex.code)
    }

    @Test
    fun `matrix mode keeps the request working when a bound fact matches another request only`() {
        // P1 (I-1): "绑定的事实不匹配这条摘要"正是本刀降级的场景——服务端过滤掉
        // 运营的绑定后不再抛 422，而是记进 droppedBindingRuleIds（原 :199-204
        // 是唯一取消抛出的判据；本用例随 P1 语义从"rejects"改为"keeps working"）。
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val visaRule = rule(id = 2, keywords = "visa", answerBody = "Visa body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(visaRule))

        val resolved = service.selectForWorkbench(
            inboundText = "- Salary?\n- Visa?",
            selectionsByRequest = listOf(listOf(2L), emptyList()),
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        val salaryItem = resolved.requestFacts[0]
        assertEquals(emptyList<Long>(), salaryItem.factRuleIds)
        assertEquals(listOf(2L), salaryItem.droppedBindingRuleIds)
        val visaItem = resolved.requestFacts[1]
        assertEquals(emptyList<Long>(), visaItem.factRuleIds)
        assertEquals(emptyList<Long>(), visaItem.droppedBindingRuleIds)
        assertEquals(emptyList<Long>(), resolved.sendQaRuleIds)
    }

    @Test
    fun `matrix mode rejects disabled never and blank facts`() {
        val disabled = rule(id = 1, keywords = "salary", answerBody = "X").copy(enabled = false)
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(disabled))
        val disabledEx = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.selectForWorkbench(
                inboundText = "- Salary?",
                selectionsByRequest = listOf(listOf(1L)),
                requestedFactIds = null,
                researchProfileSufficient = true
            )
        }
        assertEquals("TRUST_REPLY_FACT_SELECTION_INVALID", disabledEx.code)

        val never = rule(id = 2, keywords = "salary", answerBody = "X", replyPolicy = QaReplyPolicy.NEVER.name)
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(never))
        val neverEx = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.selectForWorkbench(
                inboundText = "- Salary?",
                selectionsByRequest = listOf(listOf(2L)),
                requestedFactIds = null,
                researchProfileSufficient = true
            )
        }
        assertEquals("TRUST_REPLY_FACT_SELECTION_INVALID", neverEx.code)

        val blank = rule(id = 3, keywords = "salary", answerBody = "")
        Mockito.`when`(repository.findById(3L)).thenReturn(Optional.of(blank))
        val blankEx = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.selectForWorkbench(
                inboundText = "- Salary?",
                selectionsByRequest = listOf(listOf(3L)),
                requestedFactIds = null,
                researchProfileSufficient = true
            )
        }
        assertEquals("TRUST_REPLY_FACT_SELECTION_INVALID", blankEx.code)
    }

    @Test
    fun `matrix mode allows empty fact lists and keeps intent statuses`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(emptyList())

        val resolved = service.selectForWorkbench(
            inboundText = "- Can you guarantee 10 million RMB?",
            selectionsByRequest = listOf(emptyList()),
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        assertEquals(emptyList<Long>(), resolved.requestFacts.single().factRuleIds)
        assertEquals(RequestGroundingStatus.UNSUPPORTED, resolved.requestFacts.single().status)
        assertEquals(emptyList<Long>(), resolved.sendQaRuleIds)
    }

    @Test
    fun `unsupported request adopts bound facts as partial evidence`() {
        // 计划 02 (I-2/I-4，推翻 P1 的旧语义): 零意图命中的摘要（合成 general.answer
        // coverage）上绑定的事实通过 I-2 并入路径成为证据：factRuleIds 采纳全部绑定、
        // status 下调为 PARTIAL（不是 GROUNDED）、droppedBindingRuleIds 变空、
        // 绑定事实经 factRuleIds 进 sendQaRuleIds（D2 已推翻，I-10）。
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val visaRule = rule(id = 2, keywords = "visa", answerBody = "Visa body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(visaRule))

        val resolved = service.selectForWorkbench(
            inboundText = "- Can you guarantee 10 million RMB?",
            selectionsByRequest = listOf(listOf(2L, 1L)),
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        val item = resolved.requestFacts.single()
        assertEquals(listOf(2L, 1L), item.factRuleIds)
        assertEquals(listOf(2L, 1L), item.boundRuleIds)
        assertEquals(emptyList<Long>(), item.droppedBindingRuleIds)
        assertEquals(RequestGroundingStatus.PARTIAL, item.status)
        assertEquals(listOf(2L, 1L), item.operatorBypassedRuleIds)
        assertEquals(listOf(2L, 1L), resolved.sendQaRuleIds)
    }

    @Test
    fun `accepted bindings report no dropped ids`() {
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val visaRule = rule(id = 2, keywords = "visa", answerBody = "Visa body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(visaRule))

        val resolved = service.selectForWorkbench(
            inboundText = "- Salary?\n- Visa?",
            selectionsByRequest = listOf(listOf(1L), listOf(2L)),
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        assertEquals(listOf(1L), resolved.requestFacts[0].factRuleIds)
        assertEquals(listOf(2L), resolved.requestFacts[1].factRuleIds)
        assertEquals(emptyList<Long>(), resolved.requestFacts[0].droppedBindingRuleIds)
        assertEquals(emptyList<Long>(), resolved.requestFacts[1].droppedBindingRuleIds)
    }

    @Test
    fun `matrix selection keeps operator bindings verbatim in boundRuleIds`() {
        // 计划 02 (I-2，推翻 P2a 的旧语义): UNSUPPORTED 摘要绑 2 条 → boundRuleIds ==
        // explicitIds（含运营顺序，必须-NOT-change 4）；绑定经 I-2 成为证据进
        // factRuleIds（旧断言「factRuleIds 仍为空、status 仍为 UNSUPPORTED」随
        // D1 推翻作废）。
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val visaRule = rule(id = 2, keywords = "visa", answerBody = "Visa body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(visaRule))

        val resolved = service.selectForWorkbench(
            inboundText = "- Can you guarantee 10 million RMB?",
            selectionsByRequest = listOf(listOf(2L, 1L)),
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        val item = resolved.requestFacts.single()
        assertEquals(listOf(2L, 1L), item.boundRuleIds)
        assertEquals(listOf(2L, 1L), item.factRuleIds)
        assertEquals(RequestGroundingStatus.PARTIAL, item.status)
        assertEquals(listOf(2L, 1L), resolved.sendQaRuleIds)
    }

    @Test
    fun `auto path never bypasses keyword matching even with bound facts present`() {
        // 计划 02 (I-1): 绕过只发生在矩阵路径。自动回复/人工发送走 select()，
        // operatorBound 恒 false——关键词完全不匹配的规则不进 factRuleIds，
        // status 保持 UNSUPPORTED、operatorBypassedRuleIds 恒空。
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val visaRule = rule(id = 2, keywords = "visa", answerBody = "Visa body")
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(salaryRule, visaRule))

        val resolved = service.select(
            inboundText = "Can you guarantee 10 million RMB?",
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        val item = resolved.requestFacts.single()
        assertEquals(emptyList<Long>(), item.factRuleIds)
        assertEquals(RequestGroundingStatus.UNSUPPORTED, item.status)
        assertEquals(emptyList<Long>(), item.operatorBypassedRuleIds)
        assertEquals(emptyList<Long>(), resolved.sendQaRuleIds)
    }

    @Test
    fun `matrix binding on zero intent hit merges into the existing general answer coverage only`() {
        // 计划 02 (I-2): 零意图命中的摘要（catalog 合成 general.answer coverage）
        // 绑定 2 条事实 → intents 条目数保持 1（绝不新增/移除条目，否则 requestKey
        // 漂移）；requestKey 与改动前对同一 (sourceVersion, index, requestText) 的
        // 计算值逐字相等（硬编码期望值锁死）。
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val visaRule = rule(id = 2, keywords = "visa", answerBody = "Visa body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(visaRule))

        val resolved = service.selectForWorkbench(
            inboundText = "- Can you guarantee 10 million RMB?",
            selectionsByRequest = listOf(listOf(2L, 1L)),
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        val item = resolved.requestFacts.single()
        assertEquals(1, item.intents.size)
        assertEquals("general.answer", item.intents[0].intentKey)
        assertEquals("SUPPORTED", item.intents[0].status)
        // assignRulesToIntents 按 (priority, id) 排序 → coverage 内 [1, 2]；
        // factRuleIds 保持运营绑定顺序 [2, 1]（I-2 不改 boundRuleIds 取值与顺序）。
        assertEquals(listOf(1L, 2L), item.intents[0].evidenceRuleIds)
        assertEquals(listOf(2L, 1L), item.factRuleIds)
        // I-2: requestKey(sourceVersion, item) 与改动前对同一 (sourceVersion, index,
        // requestText) 计算出的值逐字相等（hardcoded）。
        assertEquals(
            "e3da9405738529d4413bec3cf2239c7d",
            TrustReplyWorkbenchService.requestKey("source-v1", 1, "Can you guarantee 10 million RMB?", listOf("general.answer"))
        )
    }

    @Test
    fun `matrix binding that matches no named intent never adds a coverage entry`() {
        // 计划 02 (I-2): 有具名意图命中的摘要上绑定一条对不上任何意图的事实 →
        // intents.size 不变、该事实落入 droppedBindingRuleIds（不造新条目）。
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val visaRule = rule(id = 2, keywords = "visa", answerBody = "Visa body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(visaRule))

        val resolved = service.selectForWorkbench(
            inboundText = "- Salary?",
            selectionsByRequest = listOf(listOf(2L)),
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        val item = resolved.requestFacts.single()
        assertEquals(1, item.intents.size)
        assertEquals("finance.arrangements", item.intents[0].intentKey)
        assertEquals(listOf(2L), item.droppedBindingRuleIds)
        assertEquals(emptyList<Long>(), item.factRuleIds)
    }

    @Test
    fun `strict keyword matched binding stays grounded with no bypassed rules`() {
        // 计划 02 (I-4 例 1): 绑定的事实严格命中关键词且落进 SUPPORTED 意图 →
        // operatorBypassedRuleIds 为空、status 为 GROUNDED（与改动前逐字一致）。
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))

        val resolved = service.selectForWorkbench(
            inboundText = "- Salary?",
            selectionsByRequest = listOf(listOf(1L)),
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        val item = resolved.requestFacts.single()
        assertEquals(listOf(1L), item.factRuleIds)
        assertEquals(emptyList<Long>(), item.operatorBypassedRuleIds)
        assertEquals(RequestGroundingStatus.GROUNDED, item.status)
    }

    @Test
    fun `keyword non matching binding caps status at partial`() {
        // 计划 02 (I-4 例 2): 绑定的事实关键词不命中 → 靠绕过才成为证据 →
        // operatorBypassedRuleIds 非空、status 为 PARTIAL（不是 GROUNDED）。
        val visaRule = rule(id = 2, keywords = "visa", answerBody = "Visa body")
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(visaRule))

        val resolved = service.selectForWorkbench(
            inboundText = "- Can you guarantee 10 million RMB?",
            selectionsByRequest = listOf(listOf(2L)),
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        val item = resolved.requestFacts.single()
        assertEquals(listOf(2L), item.factRuleIds)
        assertEquals(listOf(2L), item.operatorBypassedRuleIds)
        assertEquals(RequestGroundingStatus.PARTIAL, item.status)
    }

    @Test
    fun `no binding keeps unsupported with empty bypassed rules`() {
        // 计划 02 (I-4 例 3): 未绑定任何事实 → status 为 UNSUPPORTED、
        // operatorBypassedRuleIds 为空。
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(emptyList())

        val resolved = service.selectForWorkbench(
            inboundText = "- Can you guarantee 10 million RMB?",
            selectionsByRequest = listOf(emptyList()),
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        val item = resolved.requestFacts.single()
        assertEquals(emptyList<Long>(), item.factRuleIds)
        assertEquals(RequestGroundingStatus.UNSUPPORTED, item.status)
        assertEquals(emptyList<Long>(), item.operatorBypassedRuleIds)
    }

    @Test
    fun `send ids contain only adopted bindings never dropped ones`() {
        // 计划 02 (I-10): 绑定 2 条、1 条被采纳 1 条落入 dropped →
        // sendQaRuleIds 只含被采纳的那条（D2 推翻后的新定义：系统匹配认可 ∪
        // 运营显式担保且已生效）。
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val visaRule = rule(id = 2, keywords = "visa", answerBody = "Visa body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(visaRule))

        val resolved = service.selectForWorkbench(
            inboundText = "- Salary?",
            selectionsByRequest = listOf(listOf(1L, 2L)),
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        val item = resolved.requestFacts.single()
        assertEquals(listOf(1L), item.factRuleIds)
        assertEquals(listOf(2L), item.droppedBindingRuleIds)
        assertEquals(listOf(1L), resolved.sendQaRuleIds)
        assertFalse(2L in resolved.sendQaRuleIds)
    }

    @Test
    fun `auto selection sets boundRuleIds equal to factRuleIds`() {
        // P2a (I-1): 自动匹配路径 boundRuleIds == factRuleIds（逐字相等）。
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val visaRule = rule(id = 2, keywords = "visa", answerBody = "Visa body")
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(salaryRule, visaRule))

        val resolved = service.selectForWorkbench(
            inboundText = "- Salary?\n- Visa?",
            selectionsByRequest = null,
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        resolved.requestFacts.forEach { item ->
            assertEquals(item.factRuleIds, item.boundRuleIds)
        }
        assertEquals(listOf(1L), resolved.requestFacts[0].boundRuleIds)
        assertEquals(listOf(2L), resolved.requestFacts[1].boundRuleIds)
    }

    @Test
    fun `send rule ids stay evidence only while bound facts join prompt ids`() {
        // P2b (I-1): 外发审计（sendQaRuleIds）仍只含系统认可的 evidence，绑定但非
        // 证据的 id 只进入 promptRuleIds。本用例随 P2b 修订：P2a 时代断言
        // 「promptRuleIds 不含绑定 id」的部分被本刀有意的契约修订取代
        // （P2b 需求方决策 2：绑定进 prompt、不进 send）。
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val visaRule = rule(id = 2, keywords = "visa", answerBody = "Visa body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(visaRule))

        val resolved = service.selectForWorkbench(
            inboundText = "- Salary?",
            selectionsByRequest = listOf(listOf(2L, 1L)),
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        val item = resolved.requestFacts.single()
        assertEquals(listOf(1L), item.factRuleIds)
        assertEquals(listOf(2L, 1L), item.boundRuleIds)
        assertEquals(listOf(1L), resolved.sendQaRuleIds)
        assertFalse(2L in resolved.sendQaRuleIds)
        // P2b (I-1): 证据在前、绑定补在后。
        assertEquals(listOf(1L, 2L), resolved.promptRuleIds)
    }

    @Test
    fun `prompt rule ids include bound facts while send rule ids do not`() {
        // P2b (C-1 / I-1 / IP-3): 绑定但非证据的事实进 promptRuleIds（证据在前、
        // 绑定补在后），sendQaRuleIds 绝不包含它（外发审计只记真证据）。
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val visaRule = rule(id = 2, keywords = "visa", answerBody = "Visa body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(visaRule))

        val resolved = service.selectForWorkbench(
            inboundText = "- Salary?",
            selectionsByRequest = listOf(listOf(2L, 1L)),
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        val item = resolved.requestFacts.single()
        assertEquals(listOf(1L), item.factRuleIds)
        assertEquals(listOf(2L, 1L), item.boundRuleIds)
        assertEquals(listOf(1L), resolved.sendQaRuleIds)
        assertFalse(2L in resolved.sendQaRuleIds)
        assertEquals(listOf(1L, 2L), resolved.promptRuleIds)
        assertTrue(2L in resolved.promptRuleIds)
    }

    @Test
    fun `prompt rule ids are identical to send rule ids without extra bindings`() {
        // P2b (C-1 / I-4): 无绑定分叉（boundRuleIds == factRuleIds）时，
        // promptRuleIds 与 sendQaRuleIds 逐字相等——恒等变换。
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val visaRule = rule(id = 2, keywords = "visa", answerBody = "Visa body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(visaRule))

        val resolved = service.selectForWorkbench(
            inboundText = "- Salary?\n- Visa?",
            selectionsByRequest = listOf(listOf(1L), listOf(2L)),
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        assertEquals(listOf(1L, 2L), resolved.sendQaRuleIds)
        assertEquals(resolved.sendQaRuleIds, resolved.promptRuleIds)
    }

    @Test
    fun `dropped bindings are reported while still bound`() {
        // P2a (I-6): droppedBindingRuleIds 非空（仍 = explicitIds - factRuleIds），
        // 且 boundRuleIds 仍含这些 id——绑定保留、只是不作为证据。
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val visaRule = rule(id = 2, keywords = "visa", answerBody = "Visa body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(visaRule))

        val resolved = service.selectForWorkbench(
            inboundText = "- Salary?",
            selectionsByRequest = listOf(listOf(2L, 1L)),
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        val item = resolved.requestFacts.single()
        assertEquals(listOf(2L), item.droppedBindingRuleIds)
        assertTrue(item.boundRuleIds.containsAll(item.droppedBindingRuleIds))
    }

    @Test
    fun `matrix size mismatch still throws`() {
        // must-NOT-change 1: 矩阵条数与摘要条数不等仍然硬拦。
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.selectForWorkbench(
                inboundText = "- Salary?\n- Visa?",
                selectionsByRequest = listOf(listOf(1L)),
                requestedFactIds = null,
                researchProfileSufficient = true
            )
        }

        assertEquals("TRUST_REPLY_FACT_SELECTION_INVALID", ex.code)
    }

    @Test
    fun `disabled rule still throws`() {
        // 证据 E-2b: 已停用规则是"客观上不能用"，必须让运营知道，不降级。
        val disabled = rule(id = 1, keywords = "salary", answerBody = "X").copy(enabled = false)
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(disabled))

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.selectForWorkbench(
                inboundText = "- Salary?",
                selectionsByRequest = listOf(listOf(1L)),
                requestedFactIds = null,
                researchProfileSufficient = true
            )
        }

        assertEquals("TRUST_REPLY_FACT_SELECTION_INVALID", ex.code)
    }

    @Test
    fun `legacy flat assigns each id exactly once to the first accepting request`() {
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val visaRule = rule(id = 2, keywords = "visa", answerBody = "Visa body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(visaRule))

        val resolved = service.selectForWorkbench(
            inboundText = "- Salary?\n- Visa?",
            selectionsByRequest = null,
            requestedFactIds = listOf(1L, 2L),
            researchProfileSufficient = true
        )

        assertEquals(listOf(1L), resolved.requestFacts[0].factRuleIds)
        assertEquals(listOf(2L), resolved.requestFacts[1].factRuleIds)
        assertEquals(listOf(1L, 2L), resolved.sendQaRuleIds)
    }

    @Test
    fun `legacy flat rejects ids not consumed by any request`() {
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        val feeRule = rule(id = 2, keywords = "fee", answerBody = "Fee body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))
        Mockito.`when`(repository.findById(2L)).thenReturn(Optional.of(feeRule))

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.selectForWorkbench(
                inboundText = "- Salary?",
                selectionsByRequest = null,
                requestedFactIds = listOf(1L, 2L),
                researchProfileSufficient = true
            )
        }

        assertEquals("TRUST_REPLY_FACT_SELECTION_INVALID", ex.code)
    }

    @Test
    fun `legacy flat rejects duplicate ids as already assigned`() {
        val salaryRule = rule(id = 1, keywords = "salary", answerBody = "Salary body")
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(salaryRule))

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.selectForWorkbench(
                inboundText = "- Salary?",
                selectionsByRequest = null,
                requestedFactIds = listOf(1L, 1L),
                researchProfileSufficient = true
            )
        }

        assertEquals("TRUST_REPLY_FACT_ALREADY_ASSIGNED", ex.code)
    }

    @Test
    fun `workbench selection rejects both matrix and legacy fields`() {
        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.selectForWorkbench(
                inboundText = "- Salary?",
                selectionsByRequest = listOf(listOf(1L)),
                requestedFactIds = listOf(1L),
                researchProfileSufficient = true
            )
        }

        assertEquals("TRUST_REPLY_FACT_SELECTION_AMBIGUOUS", ex.code)
    }

    // ── P1 (plan 01-fact-and-catalog): orthopaedic trigger letter (C-2) ──

    // I-7: verbatim orthopaedic trigger letter, character-for-character from the
    // plan header (fixture authority — never rewrite, truncate or paraphrase).
    private val orthopaedicLetter =
        "Thank you for contacting me and for your explanation of how the programme works. I would be open to exploring whether there could be a suitable match. My current clinical and research interests are mainly focused on orthopaedic trauma, particularly femoral fractures, peri-implant femoral fractures, fracture fixation strategies, implant-related complications, and clinical research and registry development in these areas. Before going further, I would appreciate some additional information about the programme, particularly its official name, the government body or institution supporting it, the usual form of collaboration with the Chinese partner companies, and the general arrangements regarding remuneration and intellectual property. At this stage, I would be happy to continue the conversation by email."

    private val postV105Pool: List<QaRule> = listOf(
        rule(
            id = 1001,
            keywords = "official name,name of the scheme,what is it called",
            answerBody = "The programme runs as three schemes: the Innovative Talent Project, the Entrepreneurial Talent Project and the Young Talent Project.",
            replySubject = "Programme name and public visibility"
        ).copy(coverageKeys = "programme.name"),
        rule(
            id = 1002,
            keywords = "government body,government institution,government agency,institution supporting,supporting body",
            answerBody = "It is a national-level talent scheme, and applications are organised locally through municipal governments and their talent offices.",
            replySubject = "Programme sponsorship and organising level"
        ).copy(coverageKeys = "governance.sponsor_level"),
        // id=6 with the V105-appended collaboration-form keywords.
        rule(
            id = 6,
            keywords = "full time,part time,remote,technical consultant,form of collaboration,forms of collaboration,how the collaboration works",
            answerBody = "The advisory role can be performed on a part-time remote basis.",
            replySubject = "Full-time and part-time options"
        ).copy(coverageKeys = "work.remote_arrangement,work.travel_arrangement"),
        // Funding support — keywords equal the exact post-V106 production state
        // (V3 seed + V81 appends + V106__add_remuneration_keyword_to_funding_support.sql).
        // The letter's "remuneration" phrasing is why V106 exists; the fixture must
        // stay in lockstep with the V106 text assertion below (repair V-1), never a
        // detached test-only override.
        rule(
            id = 8,
            keywords = "salary,subsidy,funding,compensation,advisory role compensated,is the advisory role compensated,remuneration",
            answerBody = "After a successful application, personal subsidies and research funding may be available.",
            replySubject = "Funding support"
        ).copy(coverageKeys = "finance.government_funding,finance.enterprise_compensation"),
        rule(
            id = 34,
            keywords = "intellectual property,ip rights,ip arrangements,patent ownership,who owns the,ip terms",
            answerBody = "Until a contract is signed, nothing you share with us transfers any rights.",
            replySubject = "Pre-contract IP boundary"
        ).copy(coverageKeys = "ip.arrangements")
    )

    @Test
    fun `V106 conditionally appends remuneration to funding support preserving updated_at`() {
        // Repair V-1: the fixture's 'remuneration' keyword must be tied to the
        // production migration, not a detached test override. If V106 is edited,
        // the fixture below and this assertion must move in lockstep.
        val sql = Files.readString(
            Path.of("src/main/resources/db/migration/V106__add_remuneration_keyword_to_funding_support.sql")
        )
        assertTrue(
            sql.contains("WHERE reply_subject = 'Funding support'"),
            "V106 must target Funding support only"
        )
        assertTrue(
            sql.contains("LOWER(keywords) NOT LIKE '%remuneration%'"),
            "V106 must conditionally append remuneration with a NOT LIKE guard"
        )
        assertTrue(
            sql.contains("updated_at = updated_at"),
            "V106 must preserve updated_at"
        )
    }

    @Test
    fun `orthopaedic letter binds five supported intents to five facts`() {
        val promptSet = postV105Pool.mapNotNull { it.id }.toSet()
        val fact = service.buildRequestFact(
            index = 1,
            requestText = orthopaedicLetter,
            promptPool = postV105Pool,
            promptSet = promptSet,
            researchProfileSufficient = true
        )

        assertEquals(
            setOf(
                "programme.name", "governance.sponsor", "collaboration.form",
                "finance.arrangements", "ip.arrangements"
            ),
            fact.intents.map { it.intentKey }.toSet()
        )
        assertTrue(fact.intents.all { it.status == "SUPPORTED" }, "all five intents must be SUPPORTED")
        assertEquals(listOf(1001L, 1002L, 6L, 8L, 34L), fact.factRuleIds)
        assertEquals(RequestGroundingStatus.GROUNDED, fact.status)

        // N3: finance/ip evidence on this letter is unchanged.
        assertEquals(
            listOf(8L),
            fact.intents.single { it.intentKey == "finance.arrangements" }.evidenceRuleIds
        )
        assertEquals(
            listOf(34L),
            fact.intents.single { it.intentKey == "ip.arrangements" }.evidenceRuleIds
        )
    }

    @Test
    fun `partner company question never absorbs the collaboration form fact`() {
        // I-6: "which partner company" is enterprise-identity territory; id=6's
        // collaboration-form keywords must not pull the fact into candidate rules.
        val collaborationRule = postV105Pool.single { it.id == 6L }
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(collaborationRule))

        val resolved = service.select(
            inboundText = "Which partner company would I be matched with?",
            selectedRuleIds = null,
            researchProfileSufficient = true
        )

        assertFalse(
            resolved.requestFacts.flatMap { it.factRuleIds }.contains(6L),
            "id=6 must not evidence a partner-company ask"
        )
    }

    // ── P2a (plan 02, D-2): shadow-period measurement ──────────────────────

    private fun ask(label: String, quote: String, mail: String): EnumeratedAsk {
        val start = mail.indexOf(quote)
        require(start >= 0) { "quote must be a substring of the mail: $quote" }
        return EnumeratedAsk(label = label, quote = quote, originalRange = start until start + quote.length)
    }

    @Test
    fun `shadow enumeration never changes status counts or fact ids`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(emptyList())
        val enumerator = Mockito.mock(InboundAskEnumerator::class.java)
        val mail = "Thank you. Before I decide, could you tell me whether you provide visa support, " +
            "and what happens if the enterprise withdraws midway?"
        val three = AskEnumeration(
            true,
            listOf(
                ask("Visa support", "provide visa support", mail),
                ask("Withdrawal", "what happens if the enterprise withdraws midway", mail),
                ask("Decision", "Before I decide", mail)
            )
        )
        val none = AskEnumeration(true, emptyList())
        Mockito.`when`(enumerator.enumerate(mail)).thenReturn(three, none)
        val selectionService = QaFactSelectionService(repository, enumerator, AskEnumeratorProperties())

        val withShadow = selectionService.selectForWorkbench(mail, null, null, true)
        val withoutShadow = selectionService.selectForWorkbench(mail, null, null, true)

        // I-3: shadow fields differ…
        assertEquals(3, withShadow.unrecognizedAskCount)
        assertEquals(3, withShadow.requestFacts.single().unrecognizedAsks.size)
        assertEquals(0, withoutShadow.unrecognizedAskCount)
        assertTrue(withShadow.enumeratorAvailable)
        assertEquals(3, withShadow.enumeratorEnumerated)
        assertEquals(0, withShadow.enumeratorClaimed)

        // …but every judgement output is byte-identical (I-3/N1/N2).
        assertEquals(
            withoutShadow.requestFacts.map { it.status },
            withShadow.requestFacts.map { it.status }
        )
        assertEquals(withoutShadow.groundedRequestCount, withShadow.groundedRequestCount)
        assertEquals(withoutShadow.unsupportedRequests, withShadow.unsupportedRequests)
        assertEquals(
            withoutShadow.requestFacts.map { it.factRuleIds },
            withShadow.requestFacts.map { it.factRuleIds }
        )
        assertEquals(withoutShadow.sendQaRuleIds, withShadow.sendQaRuleIds)
    }

    @Test
    fun `ask overlapping two intent spans is claimed exactly once`() {
        val enumeration = AskEnumeration(
            true,
            listOf(ask("Remuneration and IP", "remuneration and intellectual property", orthopaedicLetter))
        )
        val promptSet = postV105Pool.mapNotNull { it.id }.toSet()
        val fact = service.buildRequestFact(
            index = 1,
            requestText = orthopaedicLetter,
            promptPool = postV105Pool,
            promptSet = promptSet,
            researchProfileSufficient = true,
            askEnumeration = enumeration,
            requestRange = 0 until orthopaedicLetter.length
        )

        // I-7: the ask overlaps the alias spans of BOTH finance and ip intents,
        // yet it is claimed once — never unrecognized, never a negative count.
        assertTrue(fact.unrecognizedAsks.isEmpty(), "the doubly-overlapping ask must be claimed")
        val spans = AiReplyIntentCatalog.matchIntentsWithSpans(orthopaedicLetter)
        val financeSpan = spans.single { it.definition.key == "finance.arrangements" }
        val ipSpan = spans.single { it.definition.key == "ip.arrangements" }
        val range = enumeration.asks.single().originalRange
        assertTrue(financeSpan.originalRanges.any { it.first <= range.last && range.first <= it.last })
        assertTrue(ipSpan.originalRanges.any { it.first <= range.last && range.first <= it.last })
    }

    @Test
    fun `orthopaedic letter enumeration is fully claimed`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(postV105Pool)
        val enumerator = Mockito.mock(InboundAskEnumerator::class.java)
        val quotes = listOf(
            "its official name",
            "the government body or institution supporting it",
            "the usual form of collaboration",
            "remuneration",
            "intellectual property"
        )
        Mockito.`when`(enumerator.enumerate(orthopaedicLetter)).thenReturn(
            AskEnumeration(true, quotes.map { ask(it, it, orthopaedicLetter) })
        )
        val selectionService = QaFactSelectionService(repository, enumerator, AskEnumeratorProperties())

        val resolved = selectionService.selectForWorkbench(
            inboundText = orthopaedicLetter,
            selectionsByRequest = null,
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        assertEquals(0, resolved.unrecognizedAskCount)
        assertEquals(5, resolved.enumeratorEnumerated)
        assertEquals(5, resolved.enumeratorClaimed)
        assertTrue(resolved.enumeratorAvailable)
        assertTrue(resolved.requestFacts.single().unrecognizedAsks.isEmpty())
    }

    @Test
    fun `catalog missing ask is recorded as unrecognized`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(emptyList())
        val enumerator = Mockito.mock(InboundAskEnumerator::class.java)
        val mail = "Thank you. Before I decide, could you tell me whether you provide visa support, " +
            "and what happens if the enterprise withdraws midway?"
        Mockito.`when`(enumerator.enumerate(mail)).thenReturn(
            AskEnumeration(true, listOf(ask("Visa support", "provide visa support", mail)))
        )
        val selectionService = QaFactSelectionService(repository, enumerator, AskEnumeratorProperties())

        val resolved = selectionService.selectForWorkbench(
            inboundText = mail,
            selectionsByRequest = null,
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        assertTrue(resolved.unrecognizedAskCount >= 1)
        assertEquals(
            listOf("provide visa support"),
            resolved.requestFacts.single().unrecognizedAsks.map { it.quote }
        )
    }

    @Test
    fun `asks are attributed to the request that contains them`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(emptyList())
        val enumerator = Mockito.mock(InboundAskEnumerator::class.java)
        val mail = "- Salary?\n- Visa?"
        Mockito.`when`(enumerator.enumerate(mail)).thenReturn(
            AskEnumeration(
                true,
                listOf(ask("Salary", "Salary", mail), ask("Visa", "Visa", mail))
            )
        )
        val selectionService = QaFactSelectionService(repository, enumerator, AskEnumeratorProperties())

        val resolved = selectionService.selectForWorkbench(
            inboundText = mail,
            selectionsByRequest = null,
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        // "Salary" is claimed by the finance.arrangements alias span; "Visa" is
        // not claimed and belongs to the second request only.
        val byIndex = resolved.requestFacts.associateBy { it.index }
        assertTrue(byIndex.getValue(1).unrecognizedAsks.isEmpty())
        assertEquals(listOf("Visa"), byIndex.getValue(2).unrecognizedAsks.map { it.quote })
        assertEquals(1, resolved.unrecognizedAskCount)
        assertEquals(1, resolved.enumeratorClaimed)
        assertEquals(2, resolved.enumeratorEnumerated)
    }

    // ── P2a (plan 02, I-6): auto-reply path gating ──────────────────────────

    @Test
    fun `auto path skips enumeration while the auto reply flag is off`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(emptyList())
        val enumerator = Mockito.mock(InboundAskEnumerator::class.java)
        val gated = QaFactSelectionService(repository, enumerator, AskEnumeratorProperties(enabledForAutoReply = false))

        gated.select("Hello there, I would like some information.", null, true)

        Mockito.verify(enumerator, Mockito.never()).enumerate(Mockito.anyString())
    }

    @Test
    fun `auto path enumerates when the auto reply flag is on`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(emptyList())
        val enumerator = Mockito.mock(InboundAskEnumerator::class.java)
        Mockito.`when`(enumerator.enumerate(Mockito.anyString())).thenReturn(AskEnumeration(false, emptyList()))
        val gated = QaFactSelectionService(repository, enumerator, AskEnumeratorProperties(enabledForAutoReply = true))

        gated.select("Hello there, I would like some information.", null, true)

        Mockito.verify(enumerator).enumerate(Mockito.anyString())
    }

    @Test
    fun `workbench path always enumerates even with the auto reply flag off`() {
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(emptyList())
        val enumerator = Mockito.mock(InboundAskEnumerator::class.java)
        Mockito.`when`(enumerator.enumerate(Mockito.anyString())).thenReturn(AskEnumeration(false, emptyList()))
        val gated = QaFactSelectionService(repository, enumerator, AskEnumeratorProperties(enabledForAutoReply = false))

        gated.selectForWorkbench("- Hello?", null, null, true)

        Mockito.verify(enumerator).enumerate(Mockito.anyString())
    }

    private fun reqFactStatusCount(resolved: ResolvedQaRules, status: RequestGroundingStatus): Int =
        resolved.requestFacts.count { it.status == status }
}
