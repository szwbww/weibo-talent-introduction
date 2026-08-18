package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.llm.service.AiReplyContext
import com.weibo.talentintroduction.llm.service.AiReplyContextService
import com.weibo.talentintroduction.llm.service.AiReplyDraftReadiness
import com.weibo.talentintroduction.llm.service.AiReplyDraftResult
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiReplyGenerationState
import com.weibo.talentintroduction.llm.service.AiReplyGroundedDraftMaterializer
import com.weibo.talentintroduction.llm.service.AiTrainingQaService
import com.weibo.talentintroduction.llm.service.AiReplyHighRiskClaimValidator
import com.weibo.talentintroduction.llm.service.AiReplyMode
import com.weibo.talentintroduction.llm.service.AiReplyValidationAttempt
import com.weibo.talentintroduction.llm.service.AiReplyValidationDiagnostics
import com.weibo.talentintroduction.llm.service.AiReplyValidationDiagnostic
import com.weibo.talentintroduction.llm.service.AiReplyValidationStage
import com.weibo.talentintroduction.llm.service.RequestFactItem
import com.weibo.talentintroduction.llm.service.RequestGroundingStatus
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.util.Optional

class GroundedAutoReplyDecisionServiceTest {
    private val aiReplyDraftService = Mockito.mock(AiReplyDraftService::class.java)
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val aiReplyContextService = Mockito.mock(AiReplyContextService::class.java)
    private val aiTrainingQaService = Mockito.mock(AiTrainingQaService::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)

    private val contact = ExpertContact(
        id = 1L,
        campaignId = 1,
        orcidId = "orcid-1",
        expertEmail = "expert@test.com",
        expertName = "Expert"
    )

    @BeforeEach
    fun resetMocks() {
        Mockito.reset(
            aiReplyDraftService,
            qaRuleRepository,
            aiReplyContextService,
            aiTrainingQaService,
            mailRecordRepository
        )
    }

    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    @Suppress("UNCHECKED_CAST")
    private fun <T> listCaptor(): ArgumentCaptor<List<T>> =
        ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<T>>

    private fun stubGenerate(result: AiReplyDraftResult) {
        Mockito.`when`(
            aiReplyDraftService.generate(
                Mockito.anyString(),
                Mockito.anyList(),
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyBoolean(),
                Mockito.anyList(),
                Mockito.isNull(),
                Mockito.anyBoolean()
            )
        ).thenReturn(result)
    }

    private fun service(autoReplyEnabled: Boolean = true) =
        GroundedAutoReplyDecisionService(
            LlmProperties(enabled = true, autoReplyEnabled = autoReplyEnabled),
            aiReplyDraftService,
            qaRuleRepository,
            aiReplyContextService,
            aiTrainingQaService,
            mailRecordRepository
        )

    private fun autoRule(id: Long, policy: QaReplyPolicy = QaReplyPolicy.AUTO) = QaRule(
        id = id,
        categoryId = 1,
        keywords = "salary",
        replyBody = "Body $id",
        answerBody = "Body $id",
        replySubject = null,
        replyPolicy = policy.name,
        enabled = true
    )

    private fun readyDraft(
        text: String = "Grounded reply",
        ruleIds: List<Long> = listOf(1L)
    ) = AiReplyDraftResult(
        draftText = text,
        usedLlm = true,
        qaRuleIds = ruleIds,
        mode = AiReplyMode.QA_GROUNDED,
        generationState = AiReplyGenerationState.LLM_USED,
        draftReadiness = AiReplyDraftReadiness.READY,
        requestFacts = listOf(
            RequestFactItem(
                index = 1,
                requestText = "Salary?",
                factRuleIds = ruleIds,
                status = RequestGroundingStatus.GROUNDED
            )
        )
    )

    @Test
    fun `kill switch returns AI_AUTO_REPLY_DISABLED without generating`() {
        val decision = service(autoReplyEnabled = false).decide("Salary?", "Question", null)

        assertFalse(decision.readyToSend)
        assertEquals(GroundedAutoReplyReason.AI_AUTO_REPLY_DISABLED, decision.reason)
        Mockito.verifyNoInteractions(aiReplyDraftService)
    }

    @Test
    fun `ready draft with verified AUTO rules is ready to send`() {
        stubGenerate(readyDraft())
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(autoRule(1)))

        val decision = service().decide("Salary?", "Question", null)

        assertTrue(decision.readyToSend)
        assertEquals(GroundedAutoReplyReason.QA_AUTO_REPLIED, decision.reason)
        assertEquals("Re: Question", decision.subject)
        assertEquals("Grounded reply", decision.rawDraftText)
        assertEquals(listOf(1L), decision.qaRuleIds)
    }

    @Test
    fun `initial diagnostics do not independently block a ready decision`() {
        stubGenerate(readyDraft().copy(
            validationDiagnostics = AiReplyValidationDiagnostics.from(listOf(
                AiReplyValidationDiagnostic(AiReplyValidationAttempt.INITIAL, AiReplyValidationStage.STRUCTURE, "CODE", "r1:key")
            ))
        ))
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(autoRule(1)))

        val decision = service().decide("Salary?", "Question", null)

        assertTrue(decision.readyToSend)
        assertEquals(GroundedAutoReplyReason.QA_AUTO_REPLIED, decision.reason)
    }

    @Test
    fun `repair exhausted aggregate warning remains fail closed`() {
        stubGenerate(readyDraft().copy(
            usedLlm = false,
            generationState = AiReplyGenerationState.FALLBACK_NO_RESPONSE,
            draftReadiness = AiReplyDraftReadiness.BLOCKED,
            contextWarnings = listOf(
                AiReplyGroundedDraftMaterializer.WARNING_STRUCTURED_RESPONSE_INVALID,
                AiReplyDraftService.TRUST_REPAIR_EXHAUSTED
            ),
            validationDiagnostics = AiReplyValidationDiagnostics.from(listOf(
                AiReplyValidationDiagnostic(AiReplyValidationAttempt.INITIAL, AiReplyValidationStage.STRUCTURE, "CODE"),
                AiReplyValidationDiagnostic(AiReplyValidationAttempt.REPAIR, AiReplyValidationStage.CLAIM, "CODE_2", "r1:key")
            ))
        ))

        val decision = service().decide("Salary?", "Question", null)

        assertFalse(decision.readyToSend)
        assertEquals(GroundedAutoReplyReason.AI_REPLY_VALIDATION_FAILED, decision.reason)
    }

    @Test
    fun `empty qaRuleIds returns QA_NO_MATCH`() {
        stubGenerate(readyDraft(text = "", ruleIds = emptyList()).copy(draftText = ""))

        val decision = service().decide("Hello", "Question", null)

        assertFalse(decision.readyToSend)
        assertEquals(GroundedAutoReplyReason.QA_NO_MATCH, decision.reason)
    }

    @Test
    fun `REVIEW policy evidence returns QA_POLICY_REVIEW before grounding gap`() {
        stubGenerate(
                readyDraft(ruleIds = listOf(1L)).copy(
                    requestFacts = listOf(
                        RequestFactItem(1, "Salary?", listOf(1L), RequestGroundingStatus.PARTIAL)
                    )
                )
            )
        Mockito.`when`(qaRuleRepository.findById(1L))
            .thenReturn(Optional.of(autoRule(1, QaReplyPolicy.REVIEW)))

        val decision = service().decide("Salary?", "Question", null)

        assertFalse(decision.readyToSend)
        assertEquals(GroundedAutoReplyReason.QA_POLICY_REVIEW, decision.reason)
    }

    @Test
    fun `PARTIAL request fact returns QA_GROUNDING_GAP`() {
        stubGenerate(
                readyDraft().copy(
                    requestFacts = listOf(
                        RequestFactItem(1, "Salary?", listOf(1L), RequestGroundingStatus.PARTIAL)
                    ),
                    draftReadiness = AiReplyDraftReadiness.NEEDS_REVIEW
                )
            )
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(autoRule(1)))

        val decision = service().decide("Salary?", "Question", null)

        assertFalse(decision.readyToSend)
        assertEquals(GroundedAutoReplyReason.QA_GROUNDING_GAP, decision.reason)
    }

    @Test
    fun `validation warnings return AI_REPLY_VALIDATION_FAILED before generation unavailable`() {
        stubGenerate(
                readyDraft().copy(
                    usedLlm = false,
                    generationState = AiReplyGenerationState.FALLBACK_NO_RESPONSE,
                    contextWarnings = listOf(AiReplyGroundedDraftMaterializer.WARNING_CLAIM_VALIDATION_FAILED)
                )
            )
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(autoRule(1)))

        val decision = service().decide("Salary?", "Question", null)

        assertFalse(decision.readyToSend)
        assertEquals(GroundedAutoReplyReason.AI_REPLY_VALIDATION_FAILED, decision.reason)
    }

    @Test
    fun `LLM unavailable returns AI_GENERATION_UNAVAILABLE`() {
        stubGenerate(
                readyDraft().copy(
                    usedLlm = false,
                    generationState = AiReplyGenerationState.FALLBACK_NO_RESPONSE
                )
            )
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(autoRule(1)))

        val decision = service().decide("Salary?", "Question", null)

        assertFalse(decision.readyToSend)
        assertEquals(GroundedAutoReplyReason.AI_GENERATION_UNAVAILABLE, decision.reason)
    }

    @Test
    fun `READY draft with UNAUTHORIZED_ACTION_REMOVED returns AI_REPLY_VALIDATION_FAILED`() {
        stubGenerate(
                readyDraft().copy(
                    contextWarnings = listOf(AiReplyDraftService.UNAUTHORIZED_ACTION_REMOVED)
                )
            )
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(autoRule(1)))

        val decision = service().decide("Salary?", "Question", null)

        assertFalse(decision.readyToSend)
        assertEquals(GroundedAutoReplyReason.AI_REPLY_VALIDATION_FAILED, decision.reason)
    }

    @Test
    fun `BLOCKED draft with UNAUTHORIZED_ACTION_REMOVED returns AI_REPLY_VALIDATION_FAILED`() {
        stubGenerate(
                readyDraft().copy(
                    draftReadiness = AiReplyDraftReadiness.BLOCKED,
                    contextWarnings = listOf(AiReplyDraftService.UNAUTHORIZED_ACTION_REMOVED)
                )
            )
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(autoRule(1)))

        val decision = service().decide("Salary?", "Question", null)

        assertFalse(decision.readyToSend)
        assertEquals(GroundedAutoReplyReason.AI_REPLY_VALIDATION_FAILED, decision.reason)
    }

    @Test
    fun `buildReplySubject keeps existing Re prefix`() {
        val svc = service()
        assertEquals("Re: Question", svc.buildReplySubject("Question"))
        assertEquals("Re: Question", svc.buildReplySubject("Re: Question"))
        assertEquals("Re:", svc.buildReplySubject(null))
    }

    @Test
    fun `decide passes real research sufficiency instead of warning absence`() {
        stubGenerate(readyDraft())
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(autoRule(1)))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contact.id!!))
            .thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext(Mockito.anyString())).thenReturn("")
        // First scenario: warnings present + insufficient profile.
        // Second scenario: warnings EMPTY but profile still insufficient — the generate()
        // default (!contextWarnings.contains("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT")) would be
        // true here, so a false value proves it came from AiReplyContext, not the back-inference.
        Mockito.`when`(
            aiReplyContextService.build(
                anyValue(contact),
                Mockito.anyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any()
            )
        ).thenReturn(
            AiReplyContext(
                profileText = "profile",
                mailHistory = "history",
                contextWarnings = listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"),
                researchProfileSufficient = false
            ),
            AiReplyContext(
                profileText = "profile",
                mailHistory = "history",
                contextWarnings = emptyList(),
                researchProfileSufficient = false
            )
        )

        val sufficientCaptor = ArgumentCaptor.forClass(Boolean::class.javaObjectType)
        service().decide("Salary?", "Question", contact)
        service().decide("Salary?", "Question", contact)

        Mockito.verify(aiReplyDraftService, Mockito.times(2)).generate(
            Mockito.anyString(),
            Mockito.anyList(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyBoolean(),
            Mockito.anyList(),
            Mockito.isNull(),
            sufficientCaptor.capture()
        )
        assertEquals(2, sufficientCaptor.allValues.size)
        assertTrue(sufficientCaptor.allValues.none { it })
    }

    @Test
    fun `decide with null contact fails closed`() {
        stubGenerate(readyDraft())

        service().decide("Salary?", "Question", null)

        val profileCaptor = ArgumentCaptor.forClass(String::class.java)
        val warningsCaptor = listCaptor<String>()
        val sufficientCaptor = ArgumentCaptor.forClass(Boolean::class.javaObjectType)
        Mockito.verify(aiReplyDraftService).generate(
            Mockito.anyString(),
            Mockito.anyList(),
            Mockito.isNull(),
            Mockito.isNull(),
            profileCaptor.capture(),
            Mockito.anyString(),
            Mockito.anyBoolean(),
            warningsCaptor.capture() ?: emptyList(),
            Mockito.isNull(),
            sufficientCaptor.capture()
        )
        assertEquals("", profileCaptor.value)
        assertFalse(sufficientCaptor.value)
        assertTrue(warningsCaptor.value.contains("EXPERT_PROFILE_NOT_FOUND"))
        Mockito.verifyNoInteractions(aiReplyContextService)
    }

    @Test
    fun `decide injects training knowledge through context service`() {
        stubGenerate(readyDraft())
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contact.id!!))
            .thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext(Mockito.anyString()))
            .thenReturn("KNOWLEDGE-MARKER")
        Mockito.`when`(
            aiReplyContextService.build(
                anyValue(contact),
                Mockito.anyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any()
            )
        ).thenReturn(AiReplyContext("profile", "history", emptyList(), true))

        service().decide("Salary?", "Question", contact)

        val knowledgeCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(aiReplyContextService).build(
            anyValue(contact),
            Mockito.anyList(),
            Mockito.anyString(),
            knowledgeCaptor.capture() ?: "",
            Mockito.any()
        )
        assertEquals("KNOWLEDGE-MARKER", knowledgeCaptor.value)
    }
}
