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
        val exact = mail(id = 11L, body = "What?")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))
        val facts = ResolvedQaRules(
            sendQaRuleIds = listOf(9L),
            promptRuleIds = listOf(9L),
            requestFacts = listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)),
            requestCount = 1,
            groundedRequestCount = 1
        )
        Mockito.`when`(factSelection.selectForWorkbench("What?", null, listOf(9L), true)).thenReturn(facts)
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
        assertEquals(evidenceWithMapping("evidence-v1", canonicalKey(sourceVersion()) to listOf(9L)), bootstrap.evidenceSetVersion)
        assertEquals(1, bootstrap.requestCoverage.size)
        assertEquals(
            listOf(TrustReplyRuleMetadata(9L, "What", 3L, "answer")),
            bootstrap.rulesByCategory
        )
        assertEquals(listOf(TrustReplyRequestFactSelection(canonicalKey(sourceVersion()), listOf(9L))), bootstrap.requestFactSelections)
        Mockito.verify(factSelection).selectForWorkbench("What?", null, listOf(9L), true)
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

    private fun item(
        index: Int,
        requestText: String,
        facts: List<Long>,
        status: RequestGroundingStatus
    ): RequestFactItem = RequestFactItem(
        index = index,
        requestText = requestText,
        factRuleIds = facts,
        status = status,
        intents = listOf(
            RequestIntentCoverage(
                intentKey = "general.answer",
                title = "General answer",
                requiredCoverageKeys = emptyList(),
                evidenceRuleIds = facts,
                status = if (facts.isEmpty()) "MISSING" else "SUPPORTED",
                missingEvidenceKeys = if (facts.isEmpty()) listOf("general.answer") else emptyList(),
                requiresResearchContext = false
            )
        )
    )

    private fun selection(vararg items: RequestFactItem): ResolvedQaRules {
        val sendIds = items.sortedBy { it.index }.flatMap { it.factRuleIds }.distinct()
        return ResolvedQaRules(
            sendQaRuleIds = sendIds,
            promptRuleIds = sendIds,
            requestFacts = items.toList(),
            requestCount = items.size,
            groundedRequestCount = items.count { it.status == RequestGroundingStatus.GROUNDED }
        )
    }

    private fun stubCanonicalSource(items: List<RequestFactItem>) {
        val exact = mail(id = 11L, body = "What?")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))
        val selected = selection(*items.toTypedArray())
        Mockito.`when`(factSelection.selectForWorkbench("What?", null, listOf(9L), true)).thenReturn(selected)
        Mockito.`when`(factSelection.selectForWorkbench("What?", null, null, true)).thenReturn(selected)
        Mockito.`when`(factSelection.selectForWorkbench("What?", listOf(listOf(9L)), null, true)).thenReturn(selected)
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(selected.sendQaRuleIds))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
    }

    private fun sourceVersion(): String =
        service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L)).sourceVersion

    private fun lockedAnswer(
        sourceVersion: String,
        evidenceSetVersion: String,
        index: Int,
        requestText: String,
        answerText: String = "answer",
        ruleId: Long = 9L
    ): TrustReplyLockedItemRequest {
        val requestKey = TrustReplyWorkbenchService.requestKey(
            sourceVersion,
            index,
            requestText,
            AiReplyIntentCatalog.matchIntents(requestText).map { it.key }
        )
        val claims = listOf(AiReplyItemClaim("general.answer", answerText, listOf(ruleId)))
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

    private fun canonicalKey(version: String, index: Int = 1, requestText: String = "What?"): String =
        TrustReplyWorkbenchService.requestKey(
            version,
            index,
            requestText,
            AiReplyIntentCatalog.matchIntents(requestText).map { it.key }
        )

    private fun evidenceWithMapping(base: String, vararg entries: Pair<String, List<Long>>): String {
        val mapping = entries.joinToString("\u0001") { (key, ids) -> "$key\u0000${ids.joinToString(",")}" }
        return AiReplyDraftService.sha256Hex("$base\u0000$mapping")
    }

    private fun canonicalMatrix(version: String): List<TrustReplyRequestFactSelection> =
        listOf(TrustReplyRequestFactSelection(canonicalKey(version), listOf(9L)))

    @Test
    fun `saveState persists canonical-ordered subset after revalidation`() {
        stubCanonicalSource(
            listOf(
                item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED),
                item(2, "When?", listOf(10L), RequestGroundingStatus.GROUNDED)
            )
        )
        val version = sourceVersion()
        val currentEvidence = evidenceWithMapping(
            "evidence-v1",
            canonicalKey(version) to listOf(9L),
            canonicalKey(version, 2, "When?") to listOf(10L)
        )
        val subset = lockedAnswer(version, currentEvidence, 2, "When?", answerText = "later answer", ruleId = 10L)

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
            evidenceSetVersion = currentEvidence,
            requestedFactIds = listOf(9L),
            selectedModel = "DEEPSEEK_V4_PRO",
            lockedItems = listOf(subset)
        ))

        assertEquals("SAVED", response.status)
        assertEquals(4L, response.stateVersion)
        assertEquals("DEEPSEEK_V4_PRO", response.selectedModel)
        assertEquals(listOf(9L, 10L), response.requestedFactIds)
        assertEquals(listOf(subset.requestKey), response.lockedItems.map { it.requestKey })
        assertEquals(
            listOf(
                TrustReplyRequestFactSelection(canonicalKey(version), listOf(9L)),
                TrustReplyRequestFactSelection(canonicalKey(version, 2, "When?"), listOf(10L))
            ),
            response.requestFactSelections
        )
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
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val currentEvidence = evidenceWithMapping("evidence-v1", canonicalKey(version) to listOf(9L))
        val forged = lockedAnswer(version, currentEvidence, 1, "What?").copy(requestKey = "forged")

        assertThrows(TrustReplyWorkbenchException::class.java) {
            service.saveState(TrustReplySaveStateRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                expectedStateVersion = 0,
                sourceVersion = version,
                evidenceSetVersion = currentEvidence,
                lockedItems = listOf(forged)
            ))
        }
        Mockito.verify(stateStore, Mockito.never()).save(Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString(), Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())
    }

    @Test
    fun `saveState rejects re-materialized version id mismatch`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val currentEvidence = evidenceWithMapping("evidence-v1", canonicalKey(version) to listOf(9L))
        val tampered = lockedAnswer(version, currentEvidence, 1, "What?").copy(versionId = "not-the-canonical-id")

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.saveState(TrustReplySaveStateRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                expectedStateVersion = 0,
                sourceVersion = version,
                evidenceSetVersion = currentEvidence,
                lockedItems = listOf(tampered)
            ))
        }
        assertEquals("TRUST_REPLY_ITEM_VERSION_INVALID", ex.code)
    }

    @Test
    fun `saveState rejects stale source and evidence before touching the store`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
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
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val currentEvidence = evidenceWithMapping("evidence-v1", canonicalKey(version) to listOf(9L))
        val locked = lockedAnswer(version, currentEvidence, 1, "What?")
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
                evidenceSetVersion = currentEvidence,
                lockedItems = listOf(locked)
            ))
        }

        assertEquals("TRUST_REPLY_CLAIM_INVALID", ex.code)
        Mockito.verify(stateStore, Mockito.never()).save(Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString(), Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())
    }

    @Test
    fun `saveState with empty locked items deletes the stored snapshot`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val currentEvidence = evidenceWithMapping("evidence-v1", canonicalKey(version) to listOf(9L))
        Mockito.`when`(stateStore.delete("TRAINING_MAIL", 11L, 5L)).thenReturn(true)

        val response = service.saveState(TrustReplySaveStateRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L),
            expectedStateVersion = 5,
            sourceVersion = version,
            evidenceSetVersion = currentEvidence,
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
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val currentEvidence = evidenceWithMapping("evidence-v1", canonicalKey(version) to listOf(9L))
        Mockito.`when`(stateStore.delete("TRAINING_MAIL", 11L, 5L))
            .thenThrow(TrustReplyStateConflictException("conflict"))

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.saveState(TrustReplySaveStateRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                expectedStateVersion = 5,
                sourceVersion = version,
                evidenceSetVersion = currentEvidence,
                lockedItems = emptyList()
            ))
        }

        assertEquals("TRUST_REPLY_STATE_CONFLICT", ex.code)
        Mockito.verify(stateStore, Mockito.never())
            .pruneExpired(Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())
    }

    @Test
    fun `saveState propagates oversized payload and optimistic conflicts`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val currentEvidence = evidenceWithMapping("evidence-v1", canonicalKey(version) to listOf(9L))
        val locked = lockedAnswer(version, currentEvidence, 1, "What?")

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
                evidenceSetVersion = currentEvidence,
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
                evidenceSetVersion = currentEvidence,
                lockedItems = listOf(locked)
            ))
        }
        assertEquals("TRUST_REPLY_STATE_CONFLICT", conflictEx.code)
    }

    @Test
    fun `bootstrap restores only when source and evidence versions and locked items match`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val currentEvidence = evidenceWithMapping("evidence-v1", canonicalKey(version) to listOf(9L))
        val locked = lockedAnswer(version, currentEvidence, 1, "What?")
        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = version,
            evidenceSetVersion = currentEvidence,
            requestedFactIds = listOf(9L),
            requestFactSelections = canonicalMatrix(version),
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
        assertEquals(canonicalMatrix(version), restored.requestFactSelections)
    }

    @Test
    fun `bootstrap marks stale when source or evidence version drifted`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
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
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.LEGACY_SCHEMA_VERSION,
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
        Mockito.`when`(factSelection.selectForWorkbench("What?", null, listOf(99L), true))
            .thenThrow(TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_FACT_SELECTION_INVALID"))

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
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
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
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val currentEvidence = evidenceWithMapping("evidence-v1", canonicalKey(version) to listOf(9L))
        val tampered = lockedAnswer(version, currentEvidence, 1, "What?").copy(versionId = "forged")
        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = version,
            evidenceSetVersion = currentEvidence,
            requestedFactIds = listOf(9L),
            requestFactSelections = canonicalMatrix(version),
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
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val currentEvidence = evidenceWithMapping("evidence-v1", canonicalKey(version) to listOf(9L))
        val locked = lockedAnswer(version, currentEvidence, 1, "What?")
        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = version,
            evidenceSetVersion = currentEvidence,
            requestedFactIds = listOf(9L),
            requestFactSelections = canonicalMatrix(version),
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

    @Test
    fun `bootstrap accepts matrix input and resolves per request`() {
        val exact = mail(id = 11L, body = "What?")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))
        val facts = ResolvedQaRules(
            sendQaRuleIds = listOf(9L),
            promptRuleIds = listOf(9L),
            requestFacts = listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)),
            requestCount = 1,
            groundedRequestCount = 1
        )
        Mockito.`when`(factSelection.selectForWorkbench("What?", listOf(listOf(9L)), null, true)).thenReturn(facts)
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(9L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))

        val version = sourceVersion()
        val key = canonicalKey(version)
        val bootstrap = service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L),
            requestFactSelections = listOf(TrustReplyRequestFactSelection(key, listOf(9L)))
        ))

        assertEquals(listOf(TrustReplyRequestFactSelection(key, listOf(9L))), bootstrap.requestFactSelections)
        Mockito.verify(factSelection).selectForWorkbench("What?", listOf(listOf(9L)), null, true)
    }

    @Test
    fun `same fact union bound to different requests changes evidence version`() {
        val exact = mail(id = 11L, body = "What?\nWho?")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))
        val version = sourceVersion()
        val keyA = canonicalKey(version)
        val keyB = canonicalKey(version, 2, "Who?")
        val selectionA = ResolvedQaRules(
            sendQaRuleIds = listOf(9L),
            promptRuleIds = listOf(9L),
            requestFacts = listOf(
                item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED),
                item(2, "Who?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
            ),
            requestCount = 2,
            groundedRequestCount = 1
        )
        val selectionB = ResolvedQaRules(
            sendQaRuleIds = listOf(9L),
            promptRuleIds = listOf(9L),
            requestFacts = listOf(
                item(1, "What?", emptyList(), RequestGroundingStatus.UNSUPPORTED),
                item(2, "Who?", listOf(9L), RequestGroundingStatus.GROUNDED)
            ),
            requestCount = 2,
            groundedRequestCount = 1
        )
        Mockito.`when`(factSelection.selectForWorkbench("What?\nWho?", listOf(listOf(9L), emptyList()), null, true))
            .thenReturn(selectionA)
        Mockito.`when`(factSelection.selectForWorkbench("What?\nWho?", listOf(emptyList(), listOf(9L)), null, true))
            .thenReturn(selectionB)
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(9L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        val source = TrustReplySourceRef(TRAINING_MAIL, 11L)
        val matrixA = listOf(TrustReplyRequestFactSelection(keyA, listOf(9L)), TrustReplyRequestFactSelection(keyB, emptyList()))
        val matrixB = listOf(TrustReplyRequestFactSelection(keyA, emptyList()), TrustReplyRequestFactSelection(keyB, listOf(9L)))

        val bootA = service.bootstrap(TrustReplyBootstrapRequest(source, requestFactSelections = matrixA))
        val bootB = service.bootstrap(TrustReplyBootstrapRequest(source, requestFactSelections = matrixB))

        assertNotEquals(bootA.evidenceSetVersion, bootB.evidenceSetVersion)
        assertEquals(
            bootA.evidenceSetVersion,
            service.bootstrap(TrustReplyBootstrapRequest(source, requestFactSelections = matrixA)).evidenceSetVersion
        )
    }

    @Test
    fun `full draft with matrix fails closed and adjust item forwards matrix`() {
        val item = item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)
        stubCanonicalSource(listOf(item))
        val version = sourceVersion()
        val key = canonicalKey(version)
        val currentEvidence = evidenceWithMapping("evidence-v1", key to listOf(9L))
        val source = TrustReplySourceRef(TRAINING_MAIL, 11L)
        val matrix = listOf(TrustReplyRequestFactSelection(key, listOf(9L)))

        val fullDraft = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.generate(TrustReplyGenerationRequest(
                source = source,
                expectedSourceVersion = version,
                operation = "FULL_DRAFT",
                requestFactSelections = matrix
            ))
        }
        assertEquals("TRUST_REPLY_OPERATION_INVALID", fullDraft.code)

        val generated = AiReplyItemGenerationResult(
            itemAnswer = AiReplyItemAnswer(
                1,
                "What?",
                RequestGroundingStatus.GROUNDED,
                "Salary info",
                listOf(AiReplyItemClaim("general.answer", "Salary info", listOf(9L)))
            ),
            handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
            generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
            generationState = AiReplyGenerationState.LLM_USED,
            usedLlm = true,
            lockable = true
        )
        Mockito.`when`(draftService.generateItem(
            inboundText = "What?",
            requestFact = item,
            handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
            requestKey = key,
            operatorInstruction = null,
            expertProfile = "Name: Test",
            mailHistory = "history",
            contextWarnings = emptyList(),
            replyModel = null,
            researchProfileSufficient = true,
            llmAttemptTimeoutSeconds = null,
            llmTotalTimeoutSeconds = null,
            cancellationToken = null,
            progressReporter = AiReplyProgressReporter.NOOP
        )).thenReturn(generated)

        val adjust = service.generate(TrustReplyGenerationRequest(
            source = source,
            expectedSourceVersion = version,
            operation = "ADJUST_ITEM",
            expectedEvidenceSetVersion = currentEvidence,
            requestKey = key,
            handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
            requestFactSelections = matrix
        ))

        assertEquals(currentEvidence, adjust.evidenceSetVersion)
        assertEquals("Salary info", adjust.draftText)
    }

    @Test
    fun `bootstrap rejects ambiguous legacy and matrix input`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val key = canonicalKey(version)
        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.bootstrap(TrustReplyBootstrapRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                requestedFactIds = listOf(9L),
                requestFactSelections = listOf(TrustReplyRequestFactSelection(key, listOf(9L)))
            ))
        }
        assertEquals("TRUST_REPLY_FACT_SELECTION_AMBIGUOUS", ex.code)
    }

    @Test
    fun `bootstrap rejects unknown blank and duplicate request keys`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val key = canonicalKey(version)
        val source = TrustReplySourceRef(TRAINING_MAIL, 11L)
        fun code(selections: List<TrustReplyRequestFactSelection>): String = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.bootstrap(TrustReplyBootstrapRequest(source, requestFactSelections = selections))
        }.code

        assertEquals(
            "TRUST_REPLY_REQUEST_KEY_INVALID",
            code(listOf(TrustReplyRequestFactSelection("not-a-canonical-key", listOf(9L))))
        )
        assertEquals(
            "TRUST_REPLY_REQUEST_KEY_INVALID",
            code(listOf(TrustReplyRequestFactSelection("", listOf(9L))))
        )
        assertEquals(
            "TRUST_REPLY_REQUEST_KEY_INVALID",
            code(listOf(
                TrustReplyRequestFactSelection(key, listOf(9L)),
                TrustReplyRequestFactSelection(key, emptyList())
            ))
        )
        assertEquals(
            "TRUST_REPLY_FACT_SELECTION_INVALID",
            code(listOf(TrustReplyRequestFactSelection(key, listOf(0L))))
        )
    }

    @Test
    fun `bootstrap rejects incomplete matrix`() {
        val exact = mail(id = 11L, body = "What?\nWho?")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))
        val version = sourceVersion()
        val keyA = canonicalKey(version)
        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.bootstrap(TrustReplyBootstrapRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                requestFactSelections = listOf(TrustReplyRequestFactSelection(keyA, listOf(9L)))
            ))
        }
        assertEquals("TRUST_REPLY_FACT_SELECTION_INVALID", ex.code)
    }

    @Test
    fun `bootstrap restores v1 payload after flat normalization`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val currentEvidence = evidenceWithMapping("evidence-v1", canonicalKey(version) to listOf(9L))
        val locked = lockedAnswer(version, currentEvidence, 1, "What?")
        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.LEGACY_SCHEMA_VERSION,
            sourceVersion = version,
            evidenceSetVersion = currentEvidence,
            requestedFactIds = listOf(9L),
            selectedModel = "DEEPSEEK_V4_FLASH",
            lockedItems = listOf(locked)
        )
        Mockito.`when`(stateStore.load("TRAINING_MAIL", 11L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(4L, LocalDateTime.now().plusDays(1), "{}")
        )
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(payload)

        val restored = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))

        assertEquals("RESTORED", restored.savedState?.status)
        assertEquals(canonicalMatrix(version), restored.requestFactSelections)
    }

    @Test
    fun `bootstrap marks stale when stored v2 matrix drifts from current`() {
        val item = item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)
        stubCanonicalSource(listOf(item))
        val version = sourceVersion()
        val key = canonicalKey(version)
        val currentEvidence = evidenceWithMapping("evidence-v1", key to listOf(9L))
        Mockito.`when`(factSelection.selectForWorkbench("What?", listOf(emptyList()), null, true))
            .thenReturn(selection(item))
        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = version,
            evidenceSetVersion = currentEvidence,
            requestedFactIds = listOf(9L),
            requestFactSelections = listOf(TrustReplyRequestFactSelection(key, emptyList())),
            selectedModel = "DEEPSEEK_V4_FLASH",
            lockedItems = emptyList()
        )
        Mockito.`when`(stateStore.load("TRAINING_MAIL", 11L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(2L, LocalDateTime.now().plusDays(1), "{}")
        )
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(payload)

        val restored = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))

        assertEquals("STALE", restored.savedState?.status)
    }

    @Test
    fun `bootstrap adjust save restore assemble carry one canonical matrix`() {
        val item = item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)
        stubCanonicalSource(listOf(item))
        val version = sourceVersion()
        val key = canonicalKey(version)
        val matrix = listOf(TrustReplyRequestFactSelection(key, listOf(9L)))
        val source = TrustReplySourceRef(TRAINING_MAIL, 11L)
        val boot = service.bootstrap(TrustReplyBootstrapRequest(source, requestedFactIds = listOf(9L)))
        assertEquals(matrix, boot.requestFactSelections)
        val evidenceVersion = boot.evidenceSetVersion

        val generated = AiReplyItemGenerationResult(
            itemAnswer = AiReplyItemAnswer(
                1,
                "What?",
                RequestGroundingStatus.GROUNDED,
                "Salary info",
                listOf(AiReplyItemClaim("general.answer", "Salary info", listOf(9L)))
            ),
            handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
            generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
            generationState = AiReplyGenerationState.LLM_USED,
            usedLlm = true,
            lockable = true
        )
        Mockito.`when`(draftService.generateItem(
            inboundText = "What?",
            requestFact = item,
            handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
            requestKey = key,
            operatorInstruction = null,
            expertProfile = "Name: Test",
            mailHistory = "history",
            contextWarnings = emptyList(),
            replyModel = null,
            researchProfileSufficient = true,
            llmAttemptTimeoutSeconds = null,
            llmTotalTimeoutSeconds = null,
            cancellationToken = null,
            progressReporter = AiReplyProgressReporter.NOOP
        )).thenReturn(generated)
        Mockito.`when`(pointByPointComposer.composeLockedItems(listOf("Salary info"))).thenReturn("raw Salary info")
        Mockito.`when`(previewService.preview("raw Salary info", contact(), null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("rendered Salary info", emptyList()))

        val adjust = service.adjustItem(TrustReplyItemAdjustmentRequest(
            source = source,
            expectedSourceVersion = version,
            expectedEvidenceSetVersion = evidenceVersion,
            requestKey = key,
            handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
            requestedFactIds = listOf(9L)
        ))
        assertEquals(evidenceVersion, adjust.evidenceSetVersion)
        val locked = TrustReplyLockedItemRequest(
            requestKey = adjust.version.requestKey,
            versionId = adjust.version.versionId,
            handling = adjust.version.handling,
            answerText = adjust.version.answerText,
            claims = adjust.version.claims,
            model = adjust.version.model,
            generationKind = adjust.version.generationKind,
            evidenceSetVersion = adjust.version.evidenceSetVersion,
            sourceVersion = adjust.version.sourceVersion,
            operatorInstructionHash = adjust.version.operatorInstructionHash,
            operatorInstruction = adjust.version.operatorInstruction
        )
        Mockito.`when`(stateStore.encodePayload(Mockito.any(TrustReplySavedStatePayload::class.java) ?: TrustReplySavedStatePayload(
            schemaVersion = "",
            sourceVersion = "",
            evidenceSetVersion = "",
            requestedFactIds = emptyList(),
            selectedModel = "",
            lockedItems = emptyList()
        ))).thenReturn("{}")
        Mockito.`when`(stateStore.save(Mockito.anyString() ?: "TRAINING_MAIL", Mockito.anyLong() ?: 11L, Mockito.anyLong() ?: 0L, Mockito.anyString() ?: "", Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())).thenReturn(1L)
        val saved = service.saveState(TrustReplySaveStateRequest(
            source = source,
            expectedStateVersion = 0,
            sourceVersion = version,
            evidenceSetVersion = evidenceVersion,
            requestedFactIds = listOf(9L),
            lockedItems = listOf(locked)
        ))
        assertEquals("SAVED", saved.status)
        assertEquals(matrix, saved.requestFactSelections)

        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = version,
            evidenceSetVersion = evidenceVersion,
            requestedFactIds = listOf(9L),
            requestFactSelections = matrix,
            selectedModel = "DEEPSEEK_V4_FLASH",
            lockedItems = listOf(locked)
        )
        Mockito.`when`(stateStore.load("TRAINING_MAIL", 11L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(1L, LocalDateTime.now().plusDays(1), "{}")
        )
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(payload)
        val restored = service.bootstrap(TrustReplyBootstrapRequest(source))
        assertEquals("RESTORED", restored.savedState?.status)
        assertEquals(matrix, restored.requestFactSelections)

        val assembled = service.assemble(TrustReplyAssembleRequest(
            source = source,
            expectedSourceVersion = version,
            expectedEvidenceSetVersion = evidenceVersion,
            lockedItems = listOf(locked),
            requestedFactIds = listOf(9L)
        ))
        assertEquals(matrix, assembled.requestFactSelections)
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
