package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.llm.service.TrustReplySourceType.LIVE_INBOUND
import com.weibo.talentintroduction.llm.service.TrustReplySourceType.TRAINING_MAIL
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import java.time.LocalDateTime
import java.util.Optional

class TrustReplyWorkbenchServiceTest {
    private val mailRecords = Mockito.mock(MailRecordRepository::class.java)
    private val inboundProcessing = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val contacts = Mockito.mock(ExpertContactRepository::class.java)
    private val trainingQa = Mockito.mock(AiTrainingQaService::class.java)
    private val contextService = Mockito.mock(AiReplyContextService::class.java)
    private val factSelection = Mockito.mock(QaFactSelectionService::class.java)
    private val qaRules = Mockito.mock(QaRuleRepository::class.java)
    private val draftService = Mockito.mock(AiReplyDraftService::class.java)
    private val previewService = Mockito.mock(AiReplyDraftPreviewService::class.java)
    private val auditService = Mockito.mock(AiReplyReviewAuditService::class.java)
    private val pointByPointComposer = Mockito.mock(AiReplyPointByPointComposer::class.java)
    private val claimValidator = Mockito.mock(AiReplyHighRiskClaimValidator::class.java)
    private val stateStore = Mockito.mock(TrustReplyWorkbenchStateStore::class.java)

    private lateinit var service: TrustReplyWorkbenchService

    @BeforeEach
    fun setUp() {
        service = TrustReplyWorkbenchService(
            mailRecordRepository = mailRecords,
            inboundMailProcessingRepository = inboundProcessing,
            expertContactRepository = contacts,
            aiTrainingQaService = trainingQa,
            aiReplyContextService = contextService,
            qaFactSelectionService = factSelection,
            qaRuleRepository = qaRules,
            aiReplyDraftService = draftService,
            aiReplyDraftPreviewService = previewService,
            aiReplyReviewAuditService = auditService,
            llmProperties = LlmProperties(enabled = true),
            aiReplyPointByPointComposer = pointByPointComposer,
            claimValidator = claimValidator,
            stateStore = stateStore
        )
        Mockito.`when`(trainingQa.buildKnowledgeContext(Mockito.anyString())).thenReturn("")
        Mockito.`when`(
            contextService.build(
                Mockito.any(ExpertContact::class.java) ?: contact(),
                Mockito.anyList<MailRecord>() ?: emptyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString()
            )
        ).thenReturn(
            AiReplyContext(
                profileText = "Name: Test",
                mailHistory = "history",
                contextWarnings = emptyList(),
                researchProfileSufficient = true
            )
        )
        Mockito.`when`(
            claimValidator.validate(
                Mockito.anyList<ValidatedSection>(),
                Mockito.anyList<RequestFactItem>()
            )
        ).thenReturn(ClaimValidationResult(valid = true))
        Mockito.`when`(
            claimValidator.validateGroundedCandidate(Mockito.any(GroundedCandidateInput::class.java) ?: GroundedCandidateInput(
                validatedSections = emptyList(),
                requestFacts = emptyList(),
                plan = AiReplyGroundedContentPlanner().buildPlan(emptyList(), emptySet()),
                finalBody = "",
                hasBlockingTrustGap = false
            ))
        ).thenReturn(ClaimValidationResult(valid = true))
    }

    @Test
    fun `training source reads exact inbound mail and never falls back to latest`() {
        val exact = mail(id = 11L, body = "first")
        val latest = mail(id = 12L, body = "different")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact, latest))

        val resolved = service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L))

        assertEquals("first", resolved.inboundText)
        assertEquals(TRAINING_MAIL, resolved.source.sourceType)
        Mockito.verify(mailRecords, Mockito.never()).findLatestInboundByExpertContactId(Mockito.anyLong())
    }

    @Test
    fun `live source reads exact inbound processing and prefers cleaned body`() {
        val exact = InboundMailProcessing(
            id = 21L,
            senderAccountCode = "sender-1",
            imapUid = 99L,
            messageId = "<live@example.com>",
            fromEmail = "expert@example.com",
            subject = "Live subject",
            body = "raw body",
            cleanedBody = "clean body",
            receivedAt = LocalDateTime.of(2026, 7, 28, 10, 0),
            processStatus = "MANUAL_REVIEW",
            processReason = "needs review",
            expertContactId = 7L
        )
        Mockito.`when`(inboundProcessing.findById(21L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(emptyList())

        val resolved = service.resolveSource(TrustReplySourceRef(LIVE_INBOUND, 21L))

        assertEquals("clean body", resolved.inboundText)
        assertEquals("sender-1", resolved.senderAccountCode)
        assertEquals(LIVE_INBOUND, resolved.source.sourceType)
        Mockito.verify(inboundProcessing).findById(21L)
    }

    @Test
    fun `training source rejects outbound mail and missing contact`() {
        val outbound = mail(id = 11L, direction = "OUTBOUND")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(outbound))
        assertThrows(TrustReplyWorkbenchException::class.java) {
            service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L))
        }

        val inbound = mail(id = 12L)
        Mockito.`when`(mailRecords.findById(12L)).thenReturn(Optional.of(inbound))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.empty())
        assertThrows(TrustReplyWorkbenchException::class.java) {
            service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 12L))
        }
    }

    @Test
    fun `source version is stable and changes when source body changes`() {
        val exact = mail(id = 11L, body = "first")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))

        val first = service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L)).sourceVersion
        val second = service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L)).sourceVersion
        assertEquals(first, second)

        val changed = exact.copy(body = "changed")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(changed))
        assertNotEquals(first, service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L)).sourceVersion)
    }

    @Test
    fun `bootstrap uses canonical selection and common model catalog`() {
        val exact = mail(id = 11L)
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))
        val facts = ResolvedQaRules(
            sendQaRuleIds = listOf(9L),
            promptRuleIds = listOf(9L),
            requestFacts = listOf(RequestFactItem(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)),
            requestCount = 1,
            groundedRequestCount = 1
        )
        Mockito.`when`(factSelection.select("first", listOf(9L), true)).thenReturn(facts)
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(9L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        Mockito.`when`(qaRules.findAllEnabledOrdered()).thenReturn(listOf(
            QaRule(
                id = 9L,
                categoryId = 3L,
                keywords = "what",
                replySubject = null,
                replyBody = "",
                answerBody = "answer",
                displayName = "What"
            )
        ))

        val bootstrap = service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L),
            requestedFactIds = listOf(9L)
        ))

        assertEquals(listOf(9L), bootstrap.canonicalFactIds)
        assertEquals(listOf("DEEPSEEK_V4_FLASH", "DEEPSEEK_V4_PRO"), bootstrap.availableModels)
        assertEquals("DEEPSEEK_V4_FLASH", bootstrap.defaultModel)
        assertEquals("evidence-v1", bootstrap.evidenceSetVersion)
        assertEquals(1, bootstrap.requestCoverage.size)
        assertEquals(
            listOf(TrustReplyRuleMetadata(9L, "What", 3L)),
            bootstrap.rulesByCategory
        )
        Mockito.verify(factSelection).select("first", listOf(9L), true)
    }

    @Test
    fun `training generation returns raw and rendered text without audit`() {
        val exact = mail(id = 11L)
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))
        val result = AiReplyDraftResult(
            draftText = "raw {{expert.name}}",
            usedLlm = false,
            qaRuleIds = emptyList(),
            mode = AiReplyMode.QA_GROUNDED,
            requestCount = 1,
            generationState = AiReplyGenerationState.FALLBACK_LLM_DISABLED
        )
        Mockito.`when`(
            draftService.generate(
                inboundText = "first",
                operatorTurns = emptyList(),
                qaRuleIds = null,
                operatorInstruction = null,
                expertProfile = "Name: Test",
                mailHistory = "history",
                contextWarnings = emptyList(),
                replyModel = null,
                researchProfileSufficient = true
            )
        ).thenReturn(result)
        Mockito.`when`(previewService.preview("raw {{expert.name}}", contact(), null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("rendered Test", emptyList()))

        val response = service.generate(
            TrustReplyGenerationRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                expectedSourceVersion = service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L)).sourceVersion
            )
        )

        assertEquals("raw {{expert.name}}", response.draftText)
        assertEquals("rendered Test", response.renderedDraftText)
        assertEquals(AiReplyDraftService.sha256Hex("raw {{expert.name}}"), response.draftHash)
        Mockito.verifyNoInteractions(auditService)
    }

    @Test
    fun `generation rejects stale source before calling draft service`() {
        val exact = mail(id = 11L)
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))

        assertThrows(TrustReplyWorkbenchException::class.java) {
            service.generate(
                TrustReplyGenerationRequest(
                    source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                    expectedSourceVersion = "stale"
                )
            )
        }
        Mockito.verifyNoInteractions(draftService)
    }

    private fun selection(vararg items: RequestFactItem): ResolvedQaRules = ResolvedQaRules(
        sendQaRuleIds = listOf(9L),
        promptRuleIds = listOf(9L),
        requestFacts = items.toList(),
        requestCount = items.size,
        groundedRequestCount = items.count { it.status == RequestGroundingStatus.GROUNDED }
    )

    private fun stubCanonicalSource(items: List<RequestFactItem>) {
        val exact = mail(id = 11L)
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))
        val selected = selection(*items.toTypedArray())
        Mockito.`when`(factSelection.select("first", listOf(9L), true)).thenReturn(selected)
        Mockito.`when`(factSelection.select("first", null, true)).thenReturn(selected)
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(9L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
    }

    private fun sourceVersion(): String =
        service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L)).sourceVersion

    private fun lockedAnswer(
        sourceVersion: String,
        evidenceSetVersion: String,
        index: Int,
        requestText: String,
        answerText: String = "answer"
    ): TrustReplyLockedItemRequest {
        val requestKey = TrustReplyWorkbenchService.requestKey(sourceVersion, index, requestText, emptyList())
        val claims = listOf(AiReplyItemClaim("general.answer", answerText, listOf(9L)))
        val versionId = TrustReplyWorkbenchService.versionId(
            requestKey = requestKey,
            handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
            answerText = answerText,
            claims = claims,
            model = "DEEPSEEK_V4_FLASH",
            generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
            evidenceSetVersion = evidenceSetVersion,
            sourceVersion = sourceVersion,
            operatorInstructionHash = AiReplyDraftService.sha256Hex("")
        )
        return TrustReplyLockedItemRequest(
            requestKey = requestKey,
            versionId = versionId,
            handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
            answerText = answerText,
            claims = claims,
            model = "DEEPSEEK_V4_FLASH",
            generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
            evidenceSetVersion = evidenceSetVersion,
            sourceVersion = sourceVersion
        )
    }

    @Test
    fun `saveState persists canonical-ordered subset after revalidation`() {
        stubCanonicalSource(
            listOf(
                RequestFactItem(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED),
                RequestFactItem(2, "When?", listOf(9L), RequestGroundingStatus.GROUNDED)
            )
        )
        val version = sourceVersion()
        val subset = lockedAnswer(version, "evidence-v1", 2, "When?", answerText = "later answer")

        Mockito.`when`(stateStore.encodePayload(Mockito.any(TrustReplySavedStatePayload::class.java) ?: TrustReplySavedStatePayload(
            schemaVersion = "",
            sourceVersion = "",
            evidenceSetVersion = "",
            requestedFactIds = emptyList(),
            selectedModel = "",
            lockedItems = emptyList()
        ))).thenReturn("{}")
        Mockito.`when`(stateStore.save(Mockito.anyString() ?: "TRAINING_MAIL", Mockito.anyLong() ?: 11L, Mockito.anyLong() ?: 3L, Mockito.anyString() ?: "", Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())).thenReturn(4L)
        val response = service.saveState(TrustReplySaveStateRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L),
            expectedStateVersion = 3,
            sourceVersion = version,
            evidenceSetVersion = "evidence-v1",
            requestedFactIds = listOf(9L),
            selectedModel = "DEEPSEEK_V4_PRO",
            lockedItems = listOf(subset)
        ))

        assertEquals("SAVED", response.status)
        assertEquals(4L, response.stateVersion)
        assertEquals("DEEPSEEK_V4_PRO", response.selectedModel)
        assertEquals(listOf(9L), response.requestedFactIds)
        assertEquals(listOf(subset.requestKey), response.lockedItems.map { it.requestKey })
        Mockito.verify(stateStore).encodePayload(Mockito.any(TrustReplySavedStatePayload::class.java) ?: TrustReplySavedStatePayload(
            schemaVersion = "",
            sourceVersion = "",
            evidenceSetVersion = "",
            requestedFactIds = emptyList(),
            selectedModel = "",
            lockedItems = emptyList()
        ))
        Mockito.verify(stateStore).save(
            Mockito.anyString() ?: "TRAINING_MAIL",
            Mockito.anyLong() ?: 11L,
            Mockito.anyLong() ?: 3L,
            Mockito.anyString() ?: "",
            Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()
        )
        Mockito.verify(stateStore).pruneExpired(Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())
    }

    @Test
    fun `saveState rejects forged request key and keeps the store untouched`() {
        stubCanonicalSource(listOf(RequestFactItem(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val forged = lockedAnswer(version, "evidence-v1", 1, "What?").copy(requestKey = "forged")

        assertThrows(TrustReplyWorkbenchException::class.java) {
            service.saveState(TrustReplySaveStateRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                expectedStateVersion = 0,
                sourceVersion = version,
                evidenceSetVersion = "evidence-v1",
                lockedItems = listOf(forged)
            ))
        }
        Mockito.verify(stateStore, Mockito.never()).save(Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString(), Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())
    }

    @Test
    fun `saveState rejects re-materialized version id mismatch`() {
        stubCanonicalSource(listOf(RequestFactItem(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val tampered = lockedAnswer(version, "evidence-v1", 1, "What?").copy(versionId = "not-the-canonical-id")

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.saveState(TrustReplySaveStateRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                expectedStateVersion = 0,
                sourceVersion = version,
                evidenceSetVersion = "evidence-v1",
                lockedItems = listOf(tampered)
            ))
        }
        assertEquals("TRUST_REPLY_ITEM_VERSION_INVALID", ex.code)
    }

    @Test
    fun `saveState rejects stale source and evidence before touching the store`() {
        stubCanonicalSource(listOf(RequestFactItem(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val locked = lockedAnswer(version, "evidence-v1", 1, "What?")

        assertThrows(TrustReplyWorkbenchException::class.java) {
            service.saveState(TrustReplySaveStateRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                expectedStateVersion = 0,
                sourceVersion = "stale-source",
                evidenceSetVersion = "evidence-v1",
                lockedItems = listOf(locked)
            ))
        }
        assertThrows(TrustReplyWorkbenchException::class.java) {
            service.saveState(TrustReplySaveStateRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                expectedStateVersion = 0,
                sourceVersion = version,
                evidenceSetVersion = "stale-evidence",
                lockedItems = listOf(locked)
            ))
        }
        Mockito.verify(stateStore, Mockito.never()).save(Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString(), Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())
    }

    @Test
    fun `saveState rejects grounded claim trust failures before touching the store`() {
        stubCanonicalSource(listOf(RequestFactItem(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val locked = lockedAnswer(version, "evidence-v1", 1, "What?")
        Mockito.`when`(
            claimValidator.validateGroundedCandidate(Mockito.any(GroundedCandidateInput::class.java) ?: GroundedCandidateInput(
                validatedSections = emptyList(),
                requestFacts = emptyList(),
                plan = AiReplyGroundedContentPlanner().buildPlan(emptyList(), emptySet()),
                finalBody = "",
                hasBlockingTrustGap = false
            ))
        ).thenReturn(ClaimValidationResult(valid = false))

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.saveState(TrustReplySaveStateRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                expectedStateVersion = 0,
                sourceVersion = version,
                evidenceSetVersion = "evidence-v1",
                lockedItems = listOf(locked)
            ))
        }

        assertEquals("TRUST_REPLY_CLAIM_INVALID", ex.code)
        Mockito.verify(stateStore, Mockito.never()).save(Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString(), Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())
    }

    @Test
    fun `saveState with empty locked items deletes the stored snapshot`() {
        stubCanonicalSource(listOf(RequestFactItem(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        Mockito.`when`(stateStore.delete("TRAINING_MAIL", 11L, 5L)).thenReturn(true)

        val response = service.saveState(TrustReplySaveStateRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L),
            expectedStateVersion = 5,
            sourceVersion = version,
            evidenceSetVersion = "evidence-v1",
            lockedItems = emptyList()
        ))

        assertEquals("DELETED", response.status)
        assertEquals(0, response.stateVersion)
        Mockito.verify(stateStore).delete(
            Mockito.anyString() ?: "TRAINING_MAIL",
            Mockito.anyLong() ?: 11L,
            Mockito.anyLong() ?: 5L
        )
        Mockito.verify(stateStore).pruneExpired(Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())
    }

    @Test
    fun `saveState does not prune when empty snapshot deletion conflicts`() {
        stubCanonicalSource(listOf(RequestFactItem(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        Mockito.`when`(stateStore.delete("TRAINING_MAIL", 11L, 5L))
            .thenThrow(TrustReplyStateConflictException("conflict"))

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.saveState(TrustReplySaveStateRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                expectedStateVersion = 5,
                sourceVersion = version,
                evidenceSetVersion = "evidence-v1",
                lockedItems = emptyList()
            ))
        }

        assertEquals("TRUST_REPLY_STATE_CONFLICT", ex.code)
        Mockito.verify(stateStore, Mockito.never())
            .pruneExpired(Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())
    }

    @Test
    fun `saveState propagates oversized payload and optimistic conflicts`() {
        stubCanonicalSource(listOf(RequestFactItem(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val locked = lockedAnswer(version, "evidence-v1", 1, "What?")

        Mockito.`when`(stateStore.encodePayload(Mockito.any(TrustReplySavedStatePayload::class.java) ?: TrustReplySavedStatePayload(
            schemaVersion = "",
            sourceVersion = "",
            evidenceSetVersion = "",
            requestedFactIds = emptyList(),
            selectedModel = "",
            lockedItems = emptyList()
        ))).thenThrow(TrustReplyWorkbenchException(HttpStatus.PAYLOAD_TOO_LARGE, "TRUST_REPLY_STATE_TOO_LARGE"))
            .thenReturn("{}")
        val sizeEx = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.saveState(TrustReplySaveStateRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                expectedStateVersion = 0,
                sourceVersion = version,
                evidenceSetVersion = "evidence-v1",
                lockedItems = listOf(locked)
            ))
        }
        assertEquals("TRUST_REPLY_STATE_TOO_LARGE", sizeEx.code)

        Mockito.`when`(stateStore.save(Mockito.anyString() ?: "TRAINING_MAIL", Mockito.anyLong() ?: 11L, Mockito.anyLong() ?: 0L, Mockito.anyString() ?: "", Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()))
            .thenThrow(TrustReplyStateConflictException("conflict"))
        val conflictEx = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.saveState(TrustReplySaveStateRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                expectedStateVersion = 0,
                sourceVersion = version,
                evidenceSetVersion = "evidence-v1",
                lockedItems = listOf(locked)
            ))
        }
        assertEquals("TRUST_REPLY_STATE_CONFLICT", conflictEx.code)
    }

    @Test
    fun `bootstrap restores only when source and evidence versions and locked items match`() {
        stubCanonicalSource(listOf(RequestFactItem(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val locked = lockedAnswer(version, "evidence-v1", 1, "What?")
        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = version,
            evidenceSetVersion = "evidence-v1",
            requestedFactIds = listOf(9L),
            selectedModel = "DEEPSEEK_V4_FLASH",
            lockedItems = listOf(locked)
        )
        Mockito.`when`(stateStore.load("TRAINING_MAIL", 11L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(3L, LocalDateTime.now().plusDays(1), "{}")
        )
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(payload)

        val restored = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))

        assertEquals("RESTORED", restored.savedState?.status)
        assertEquals(3L, restored.savedState?.stateVersion)
        assertEquals(listOf(locked.requestKey), restored.savedState?.lockedItems?.map { it.requestKey })
        assertEquals("answer", restored.savedState?.lockedItems?.single()?.answerText)
    }

    @Test
    fun `bootstrap marks stale when source or evidence version drifted`() {
        stubCanonicalSource(listOf(RequestFactItem(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = version,
            evidenceSetVersion = "old-evidence",
            requestedFactIds = listOf(9L),
            selectedModel = "DEEPSEEK_V4_FLASH",
            lockedItems = emptyList()
        )
        Mockito.`when`(stateStore.load("TRAINING_MAIL", 11L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(2L, LocalDateTime.now().plusDays(1), "{}")
        )
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(payload)

        val stale = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))
        assertEquals("STALE", stale.savedState?.status)
        assertEquals(2L, stale.savedState?.stateVersion)
        assert(stale.savedState?.lockedItems?.isEmpty() == true)

        val drifted = payload.copy(sourceVersion = "old-source")
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(drifted)
        assertEquals("STALE", service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L))).savedState?.status)
    }

    @Test
    fun `bootstrap fails closed when implicit saved fact selection is unusable`() {
        stubCanonicalSource(listOf(RequestFactItem(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = version,
            evidenceSetVersion = "evidence-v1",
            requestedFactIds = listOf(99L),
            selectedModel = "DEEPSEEK_V4_FLASH",
            lockedItems = emptyList()
        )
        Mockito.`when`(stateStore.load("TRAINING_MAIL", 11L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(7L, LocalDateTime.now().plusDays(1), "{}")
        )
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(payload)
        Mockito.`when`(factSelection.select("first", listOf(99L), true))
            .thenThrow(IllegalArgumentException("disabled rule"))

        val restored = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))

        assertEquals(listOf(9L), restored.canonicalFactIds)
        assertEquals("STALE", restored.savedState?.status)
        assertEquals(7L, restored.savedState?.stateVersion)
        assert(restored.savedState?.lockedItems?.isEmpty() == true)

        val explicit = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.bootstrap(
                TrustReplyBootstrapRequest(
                    source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                    requestedFactIds = listOf(99L)
                )
            )
        }
        assertEquals("TRUST_REPLY_FACT_SELECTION_INVALID", explicit.code)
    }

    @Test
    fun `bootstrap marks invalid payload and expired rows as non-restorable`() {
        stubCanonicalSource(listOf(RequestFactItem(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        Mockito.`when`(stateStore.load("TRAINING_MAIL", 11L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(2L, LocalDateTime.now().plusDays(1), "{}")
        )
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(null)
        assertEquals("INVALID", service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L))).savedState?.status)

        Mockito.`when`(stateStore.load("TRAINING_MAIL", 11L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(2L, LocalDateTime.now().minusDays(1), "{}")
        )
        val expired = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))
        assertEquals("EXPIRED", expired.savedState?.status)
        assert(expired.savedState?.lockedItems?.isEmpty() == true)
        Mockito.verify(stateStore).pruneExpired(Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())
    }

    @Test
    fun `bootstrap downgrades restored items that fail revalidation to stale`() {
        stubCanonicalSource(listOf(RequestFactItem(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val tampered = lockedAnswer(version, "evidence-v1", 1, "What?").copy(versionId = "forged")
        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = version,
            evidenceSetVersion = "evidence-v1",
            requestedFactIds = listOf(9L),
            selectedModel = "DEEPSEEK_V4_FLASH",
            lockedItems = listOf(tampered)
        )
        Mockito.`when`(stateStore.load("TRAINING_MAIL", 11L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(2L, LocalDateTime.now().plusDays(1), "{}")
        )
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(payload)

        val stale = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))
        assertEquals("STALE", stale.savedState?.status)
        assert(stale.savedState?.lockedItems?.isEmpty() == true)
    }

    @Test
    fun `bootstrap rejects grounded claim trust failures without restoring items`() {
        stubCanonicalSource(listOf(RequestFactItem(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val locked = lockedAnswer(version, "evidence-v1", 1, "What?")
        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = version,
            evidenceSetVersion = "evidence-v1",
            requestedFactIds = listOf(9L),
            selectedModel = "DEEPSEEK_V4_FLASH",
            lockedItems = listOf(locked)
        )
        Mockito.`when`(stateStore.load("TRAINING_MAIL", 11L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(5L, LocalDateTime.now().plusDays(1), "{}")
        )
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(payload)
        Mockito.`when`(
            claimValidator.validateGroundedCandidate(Mockito.any(GroundedCandidateInput::class.java) ?: GroundedCandidateInput(
                validatedSections = emptyList(),
                requestFacts = emptyList(),
                plan = AiReplyGroundedContentPlanner().buildPlan(emptyList(), emptySet()),
                finalBody = "",
                hasBlockingTrustGap = false
            ))
        ).thenReturn(ClaimValidationResult(valid = false))

        val stale = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))

        assertEquals("STALE", stale.savedState?.status)
        assertEquals(5L, stale.savedState?.stateVersion)
        assert(stale.savedState?.lockedItems?.isEmpty() == true)
    }

    private fun contact() = ExpertContact(
        id = 7L,
        campaignId = 1L,
        orcidId = "0000-0000",
        expertEmail = "test@example.com",
        expertName = "Test"
    )

    private fun mail(
        id: Long,
        body: String = "first",
        direction: String = "INBOUND"
    ) = MailRecord(
        id = id,
        expertContactId = 7L,
        direction = direction,
        mailType = "REPLY",
        senderAccountCode = null,
        messageId = "<$id@example.com>",
        inReplyTo = null,
        subject = "Subject",
        body = body,
        cleanedBody = null,
        matchedQaRuleId = null,
        sendStatus = null,
        receivedAt = LocalDateTime.of(2026, 7, 28, 10, 0),
        sentAt = null
    )
}
