package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.Optional

class AiReplyHighRiskClaimValidatorTest {
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val validator = AiReplyHighRiskClaimValidator(qaRuleRepository)

    private fun rule(id: Long, body: String) = QaRule(
        id = id,
        categoryId = 1,
        keywords = "k$id",
        replyBody = body,
        answerBody = body,
        replySubject = "Re $id",
        enabled = true
    )

    @Test
    fun `hallucinated number not in source fact is detected`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "The programme offers competitive compensation.")))

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("finance.arrangements", "The salary is RMB 500,000 per year.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Salary?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("finance.arrangements", "Finance", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        assertTrue(validator.containsHallucinatedNumberOrUrl("The salary is RMB 500,000 per year.", "The programme offers competitive compensation."))
    }

    @Test
    fun `number also present in source fact passes`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Monthly stipend is RMB 8,000.")))

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("finance.arrangements", "The monthly stipend is RMB 8,000.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Stipend?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("finance.arrangements", "Finance", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertTrue(result.valid)
    }

    @Test
    fun `hallucinated URL detected`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Visit our website for more info.")))

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("application.next_stages", "Apply at https://example.com/apply now.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Apply?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("application.next_stages", "Next", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertFalse(result.valid)
        assertTrue(result.warningCodes.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_HALLUCINATED_FACT))
    }

    @Test
    fun `modality strengthening detected when source says may but answer says will`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Participants may receive travel support.")))

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("finance.arrangements", "Participants will definitely receive full travel support.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Travel?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("finance.arrangements", "Finance", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertFalse(result.valid)
        assertTrue(result.warningCodes.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_MODALITY_STRENGTHENED))
    }

    @Test
    fun `modality check passes when source is unconditional`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Participants will receive a certificate.")))

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("role.deliverables", "Participants will receive a certificate.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Certificate?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("role.deliverables", "Deliverables", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertTrue(result.valid)
    }

    @Test
    fun `government claim without government source is detected`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "The programme is organized by a research institute.")))

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("programme.purpose", "The programme is funded by the government.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Programme?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("programme.purpose", "Purpose", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertFalse(result.valid)
        assertTrue(result.warningCodes.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_HIGH_RISK_UNBACKED))
    }

    @Test
    fun `labor contract claim with labor contract source passes`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "A labor contract will be signed with the host institution.")))

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("contract.terms", "A labor contract will be signed.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Contract?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("contract.terms", "Contract", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertTrue(result.valid)
    }

    @Test
    fun `all registered high-risk aliases require same-family source`() {
        val cases = listOf(
            "Travel costs covered" to "travel expenses",
            "The programme is free of charge" to "no fees",
            "There is an employment contract" to "labor contract",
            "IP ownership is defined" to "intellectual property",
            "An NDA is required" to "confidentiality"
        )

        cases.forEach { (answer, source) ->
            assertTrue(
                validator.containsUnbackedHighRiskDeclarations(answer, "General programme information."),
                "must reject unbacked alias: $answer"
            )
            assertFalse(
                validator.containsUnbackedHighRiskDeclarations(answer, "Approved terms cover $source."),
                "must accept same-family source: $answer"
            )
        }
    }

    @Test
    fun `hyphenated high-risk source supports space-separated paraphrase`() {
        val answer = "Intellectual property and compensation terms are set out in the agreement."
        val sourceVariants = listOf(
            "intellectual-property",
            "intellectual‐property",
            "intellectual‑property",
            "intellectual–property",
            "intellectual—property"
        )

        sourceVariants.forEach { source ->
            assertFalse(
                validator.containsUnbackedHighRiskDeclarations(answer, "Approved terms cover $source."),
                "must accept punctuation variant: $source"
            )
        }
        assertTrue(
            validator.containsUnbackedHighRiskDeclarations(
                "Confidentiality terms are set out in the agreement.",
                "Approved terms cover intellectual-property."
            ),
            "must still reject a different unsupported high-risk family"
        )
    }

    @Test
    fun `multiple validation failures return all distinct warning codes`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Participants may receive a small allowance.")))

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("finance.arrangements", "Participants will definitely receive government funding of RMB 500,000.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Finance?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("finance.arrangements", "Finance", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertFalse(result.valid)
        assertTrue(result.warningCodes.size >= 2)
    }

    @Test
    fun `empty sections pass validation`() {
        val result = validator.validate(emptyList(), emptyList())
        assertTrue(result.valid)
    }

    @Test
    fun `empty answers pass validation`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Some fact.")))

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("finance.arrangements", "", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "X?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("finance.arrangements", "F", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertTrue(result.valid)
    }

    @Test
    fun `number substring 2 not matched by source containing 2026`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(rule(1, "Programme runs from 2026 to 2028."))
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("programme.purpose", "The programme spans 2 years.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Timeline?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("programme.purpose", "Purpose", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        assertTrue(validator.containsHallucinatedNumberOrUrl("The programme spans 2 years.", "Programme runs from 2026 to 2028."))
    }

    @Test
    fun `URL same host but different path is rejected`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(rule(1, "More info at https://example.com/approved-page."))
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("application.next_stages", "Apply at https://example.com/other-page.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Apply?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("application.next_stages", "Next", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertFalse(result.valid)
        assertTrue(result.warningCodes.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_HALLUCINATED_FACT))
    }

    @Test
    fun `claim using fact from replySubject passes validation`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(
                QaRule(
                    id = 1,
                    categoryId = 1,
                    keywords = "k1",
                    replyBody = "The institute is located in Beijing.",
                    answerBody = "The institute is located in Beijing.",
                    replySubject = "About the China Academy of Sciences",
                    displayName = "About the China Academy of Sciences",
                    enabled = true
                )
            )
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("company.registered_location", "We are associated with the China Academy of Sciences.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Company?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("company.registered_location", "Location", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertTrue(result.valid)
    }

    @Test
    fun `missing source rule fails validation with source unavailable warning`() {
        Mockito.`when`(qaRuleRepository.findById(999L)).thenReturn(Optional.empty())

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("finance.arrangements", "The stipend is generous.", listOf(999L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Stipend?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("finance.arrangements", "Finance", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertFalse(result.valid)
        assertTrue(result.warningCodes.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_SOURCE_UNAVAILABLE))
    }

    @Test
    fun `empty source text fails validation`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(rule(1, "").copy(replySubject = null))
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("finance.arrangements", "Some claim.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "X?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("finance.arrangements", "F", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertFalse(result.valid)
        assertTrue(result.warningCodes.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_SOURCE_UNAVAILABLE))
    }

    @Test
    fun `validator returns source unavailable for null replySubject and empty replyBody`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(
                QaRule(id = 1, categoryId = 1, keywords = "k1",
                    replyBody = "",
                    replySubject = null,
                    enabled = true)
            )
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("finance.arrangements", "Some claim.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "X?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("finance.arrangements", "F", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertFalse(result.valid)
        assertTrue(result.warningCodes.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_SOURCE_UNAVAILABLE))
    }

    @Test
    fun `currency switch USD to RMB is detected as hallucinated`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(rule(1, "The stipend is USD 8,000 per month."))
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("finance.arrangements", "The stipend is RMB 8,000 per month.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Stipend?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("finance.arrangements", "Finance", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertFalse(result.valid)
        assertTrue(result.warningCodes.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_HALLUCINATED_FACT))
    }

    @Test
    fun `time unit switch month to year is detected as hallucinated`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(rule(1, "The stipend is USD 8,000 per month."))
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("finance.arrangements", "The stipend is USD 8,000 per year.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Stipend?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("finance.arrangements", "Finance", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertFalse(result.valid)
        assertTrue(result.warningCodes.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_HALLUCINATED_FACT))
    }

    @Test
    fun `exact compound token match passes validation`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(rule(1, "The stipend is USD 8,000 per month."))
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("finance.arrangements", "The stipend is USD 8,000 per month.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Stipend?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("finance.arrangements", "Finance", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertTrue(result.valid)
    }

    @Test
    fun `frequency switch monthly to annually is detected`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(rule(1, "Visits are limited to 2 monthly."))
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("role.responsibilities", "Visits are limited to 2 annually.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Visits?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("role.responsibilities", "Role", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertFalse(result.valid)
        assertTrue(result.warningCodes.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_HALLUCINATED_FACT))
    }

    // ── T2: Phase 2 — predicate family modality tests ─────────────────────────

    @Test
    fun `plain will receive rejected when source says may receive`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(rule(1, "Selected candidates may receive salary support."))
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("finance.arrangements", "You will receive salary support.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Salary?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("finance.arrangements", "Finance", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertFalse(result.valid)
        assertTrue(result.warningCodes.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_MODALITY_STRENGTHENED))
    }

    @Test
    fun `travel costs will be covered rejected when source says can be covered`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(rule(1, "Travel costs can be covered depending on the project."))
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("finance.arrangements", "All travel costs will be covered.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Travel?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("finance.arrangements", "Finance", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertFalse(result.valid)
        assertTrue(result.warningCodes.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_MODALITY_STRENGTHENED))
    }

    @Test
    fun `will own IP rejected when source says subject to enterprise agreement`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(rule(1, "IP terms are subject to the enterprise agreement."))
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("ip.arrangements", "You will own the intellectual property.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "IP?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("ip.arrangements", "IP", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertFalse(result.valid)
        assertTrue(result.warningCodes.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_MODALITY_STRENGTHENED))
    }

    @Test
    fun `shall be provided rejected when source says may be provided`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(rule(1, "Funding may be provided after evaluation."))
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("finance.arrangements", "Funding shall be provided.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Funding?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("finance.arrangements", "Finance", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertFalse(result.valid)
        assertTrue(result.warningCodes.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_MODALITY_STRENGTHENED))
    }

    @Test
    fun `explicit will receive source allows same family answer`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(rule(1, "Selected candidates will receive salary support."))
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("finance.arrangements", "You will receive salary support.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Salary?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("finance.arrangements", "Finance", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertTrue(result.valid)
    }

    @Test
    fun `active will pay source with typically still allows will be paid answer`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(rule(1, "We will pay the salary support typically after onboarding."))
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("finance.arrangements", "You will be paid salary support.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Salary?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("finance.arrangements", "Finance", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertTrue(result.valid)
    }

    @Test
    fun `after selection will sign source allows will be signed answer`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(rule(1, "After selection, you will sign a labor contract with the host institution."))
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("contract.terms", "A labor contract will be signed.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Contract?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("contract.terms", "Contract", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertTrue(result.valid)
    }

    @Test
    fun `low risk we will share details does not trigger modality`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(rule(1, "We can share the enterprise profile upon request."))
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("general.answer", "We will share details about the enterprise.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Profile?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("general.answer", "General", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertTrue(result.valid)
    }

    @Test
    fun `guaranteed still rejected when mixed source has same-family definitive`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(
                rule(
                    1,
                    "Candidates may receive salary support. Selected candidates will receive a certificate."
                )
            )
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer(
                    "finance.arrangements",
                    "You will receive salary support; this is guaranteed.",
                    listOf(1L)
                )
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Salary?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("finance.arrangements", "Finance", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertFalse(result.valid)
        assertTrue(result.warningCodes.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_MODALITY_STRENGTHENED))
    }

    @Test
    fun `resolveSourceText ignores displayName when answerBody blank`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(
                QaRule(
                    id = 1,
                    categoryId = 1,
                    keywords = "salary",
                    replyBody = "10 million RMB guarantee",
                    answerBody = "",
                    replySubject = "Re 1",
                    displayName = "Salary support facts",
                    enabled = true
                )
            )
        )

        assertNull(validator.resolveSourceText(listOf(1L)))
    }

    @Test
    fun `claim with displayName only and blank answerBody fails source validation`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(
                QaRule(
                    id = 1,
                    categoryId = 1,
                    keywords = "salary",
                    replyBody = "10 million RMB guarantee",
                    answerBody = "",
                    replySubject = null,
                    displayName = "Salary support facts",
                    enabled = true
                )
            )
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer("finance.arrangements", "You will receive 10 million RMB.", listOf(1L))
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Salary?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("finance.arrangements", "Finance", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertFalse(result.valid)
        assertTrue(result.warningCodes.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_SOURCE_UNAVAILABLE))
    }

    @Test
    fun `uppercase GUARANTEED still rejected when mixed source has same-family definitive`() {
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(
                rule(
                    1,
                    "Candidates may receive salary support. Selected candidates will receive a certificate."
                )
            )
        )

        val sections = listOf(
            ValidatedSection(1, listOf(
                IntentAnswer(
                    "finance.arrangements",
                    "You will receive salary support; this is GUARANTEED.",
                    listOf(1L)
                )
            ))
        )
        val facts = listOf(
            RequestFactItem(1, "Salary?", listOf(1L), RequestGroundingStatus.GROUNDED,
                intents = listOf(RequestIntentCoverage("finance.arrangements", "Finance", emptyList(), listOf(1L), "SUPPORTED", emptyList())))
        )

        val result = validator.validate(sections, facts)
        assertFalse(result.valid)
        assertTrue(result.warningCodes.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_MODALITY_STRENGTHENED))
    }

    @Test
    fun `validatePlainText rejects unbacked high risk amount in final text`() {
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(
            Optional.of(rule(10L, "Remote work is possible for eligible candidates."))
        )

        val result = validator.validatePlainText("We guarantee 10 million RMB with no fees.", listOf(10L))

        assertFalse(result.valid)
        assertTrue(result.warningCodes.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_HIGH_RISK_UNBACKED))
    }

    @Test
    fun `validatePlainText passes when final text stays within fact sources`() {
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(
            Optional.of(rule(10L, "Remote work is possible for eligible candidates."))
        )

        val result = validator.validatePlainText("Remote work is possible for eligible candidates.", listOf(10L))

        assertTrue(result.valid)
        assertTrue(result.warningCodes.isEmpty())
    }

    @Test
    fun `validatePlainText skips validation when no fact ids provided`() {
        val result = validator.validatePlainText("Any text with 10 million RMB.", emptyList())
        assertTrue(result.valid)
    }
}
