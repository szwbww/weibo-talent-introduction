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
import com.weibo.talentintroduction.reply.service.ReplyFrameSelection
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import com.weibo.talentintroduction.reply.service.ResolvedReplyFrame
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
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
    private val replySnippetService = Mockito.mock(ReplySnippetService::class.java)
    private val defaultFrame = ResolvedReplyFrame(
        selection = ReplyFrameSelection(
            salutationSnippetId = 1L,
            greetingSnippetId = 2L,
            ackSnippetId = null,
            closingSnippetId = 3L
        ),
        version = "frame-default",
        salutation = "Salutation",
        greeting = "Greeting",
        acknowledgement = null,
        closing = "Closing"
    )

    private lateinit var service: TrustReplyWorkbenchService

    @BeforeEach
    fun setUp() {
        Mockito.reset(
            mailRecords,
            inboundProcessing,
            contacts,
            trainingQa,
            contextService,
            factSelection,
            qaRules,
            draftService,
            previewService,
            auditService,
            pointByPointComposer,
            claimValidator,
            stateStore,
            replySnippetService
        )
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
            stateStore = stateStore,
            replySnippetService = replySnippetService
        )
        Mockito.`when`(replySnippetService.resolveDefaultSelectableFrame()).thenReturn(defaultFrame)
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

    // 03b (I-1a): changing only the training knowledge alters the context
    // fingerprint but never the identity-only sourceVersion (A-1).
    @Test
    fun `training knowledge change alters context version but not source version`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val base = service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L))
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
                researchProfileSufficient = true,
                expertProfileText = "Name: Test",
                trainingKnowledgeText = "knowledge-v2"
            )
        )
        val changed = service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L))
        assertEquals(base.sourceVersion, changed.sourceVersion)
        assertNotEquals(base.contextVersion, changed.contextVersion)
        assertFalse(base.contextVersion.isBlank())
    }

    // 03b (I-1b): changing only the mail history (a mail was sent to / received
    // from the same expert) alters the context fingerprint but never the
    // identity-only sourceVersion (A-2).
    @Test
    fun `mail history change alters context version but not source version`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val base = service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L))
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
                mailHistory = "history-v2",
                contextWarnings = emptyList(),
                researchProfileSufficient = true
            )
        )
        val changed = service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L))
        assertEquals(base.sourceVersion, changed.sourceVersion)
        assertNotEquals(base.contextVersion, changed.contextVersion)
    }

    // 03b (I-1c): every identity component — messageId, subject,
    // senderAccountCode, contactId — still flips the sourceVersion (inboundText
    // is covered by the existing source-version test above).
    @Test
    fun `every identity component change alters source version`() {
        val base = mail(id = 11L, body = "What?")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(base))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(base))
        val original = service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L)).sourceVersion

        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(base.copy(messageId = "<other@example.com>")))
        assertNotEquals(original, service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L)).sourceVersion)

        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(base.copy(subject = "Other subject")))
        assertNotEquals(original, service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L)).sourceVersion)

        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(base.copy(senderAccountCode = "sender-2")))
        assertNotEquals(original, service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L)).sourceVersion)

        val otherContactMail = base.copy(expertContactId = 8L)
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(otherContactMail))
        Mockito.`when`(contacts.findById(8L)).thenReturn(Optional.of(contact().copy(id = 8L)))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(8L)).thenReturn(listOf(otherContactMail))
        assertNotEquals(original, service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L)).sourceVersion)
    }

    // must-NOT-change 1 (03b regression): a changed inbound body is still a
    // different letter — every requestKey flips and the saved state is judged
    // STALE as a whole; the 03b downgrade never applies to identity changes.
    @Test
    fun `inbound text change flips request keys and marks saved state stale`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val key = canonicalKey(version)
        val currentEvidence = evidenceWithMapping("evidence-v1", key to listOf(9L))
        val perRequest = perRequestEvidence("evidence-v1", key, listOf(9L))
        val locked = lockedAnswer(version, perRequest, 1, "What?")
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

        val changed = mail(id = 11L, body = "What changed?")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(changed))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(changed))
        val changedSelection = selection(item(1, "What changed?", listOf(9L), RequestGroundingStatus.GROUNDED))
        Mockito.`when`(factSelection.selectForWorkbench("What changed?", null, null, true)).thenReturn(changedSelection)
        Mockito.`when`(factSelection.selectForWorkbench("What changed?", null, listOf(9L), true)).thenReturn(changedSelection)
        Mockito.`when`(factSelection.selectForWorkbench("What changed?", listOf(listOf(9L)), null, true)).thenReturn(changedSelection)
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(9L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))

        val changedVersion = service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L)).sourceVersion
        assertNotEquals(version, changedVersion)
        assertNotEquals(canonicalKey(version), canonicalKey(changedVersion, 1, "What changed?"))

        val stale = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))
        assertEquals("STALE", stale.savedState?.status)
        assert(stale.savedState?.lockedItems?.isEmpty() == true)
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
        // P2a (I-1): 夹具镜像生产赋值——auto/legacy/全采纳矩阵路径下
        // boundRuleIds == factRuleIds；需要分叉的用例用 .copy(boundRuleIds = ...) 覆写。
        boundRuleIds = facts,
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
        // 03a (C-1): the resolver now reads the base snapshot per request
        // subset, so every subset (including the empty one) must be stubbed.
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(selected.sendQaRuleIds))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        items.forEach { item ->
            Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(item.factRuleIds))
                .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        }
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(emptyList()))
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

    // 03a (T1): mirrors TrustReplyWorkbenchService.requestEvidenceVersion —
    // the per-request identity binds key + ordered ids + subset base snapshot.
    private fun perRequestEvidence(base: String, requestKey: String, factRuleIds: List<Long>): String =
        AiReplyDraftService.sha256Hex(listOf(requestKey, factRuleIds.joinToString(","), base).joinToString(" "))

    // 03a (T1): mirrors TrustReplyWorkbenchService.aggregateEvidenceVersion —
    // sha256 of the index-ordered per-request values concatenated.
    private fun aggregateEvidence(perRequestByIndex: List<Pair<Int, String>>): String =
        AiReplyDraftService.sha256Hex(perRequestByIndex.sortedBy { it.first }.joinToString("") { it.second })

    // Whole-draft aggregate for the given entries (indices assigned by entry
    // order, matching the canonical matrix ordering used by the tests).
    private fun evidenceWithMapping(base: String, vararg entries: Pair<String, List<Long>>): String =
        aggregateEvidence(
            entries.mapIndexed { index, (key, ids) -> (index + 1) to perRequestEvidence(base, key, ids) }
        )

    private fun canonicalMatrix(version: String): List<TrustReplyRequestFactSelection> =
        listOf(TrustReplyRequestFactSelection(canonicalKey(version), listOf(9L)))

    private fun resolvedFrame(
        selection: ReplyFrameSelection,
        version: String,
        salutation: String? = null,
        greeting: String? = null,
        acknowledgement: String? = null,
        closing: String? = null
    ) = ResolvedReplyFrame(
        selection = selection,
        version = version,
        salutation = salutation,
        greeting = greeting,
        acknowledgement = acknowledgement,
        closing = closing
    )

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
        val subset = lockedAnswer(version, perRequestEvidence("evidence-v1", canonicalKey(version, 2, "When?"), listOf(10L)), 2, "When?", answerText = "later answer", ruleId = 10L)

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

    // 计划 14 (T-5.3, I-2/I-4): 条目级 PATCH 只替换 requestKey 匹配的那一项，
    // 其余 lockedItems 逐字保留；整封快照语义（矩阵/选中模型/框架）不受单条合并影响。
    @Test
    fun `saveStateItem replaces only the target locked item and keeps the rest verbatim`() {
        // 两问正文：canonicalRequests（真实 extractor）恰好产出 key1/key2，
        // 与行内矩阵一致；selection 按该矩阵 stub（同「same fact union」夹具）。
        val exact = mail(id = 11L, body = "What?\nWhen?")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))
        val selected = selection(
            item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED),
            item(2, "When?", listOf(10L), RequestGroundingStatus.GROUNDED)
        )
        Mockito.`when`(factSelection.selectForWorkbench("What?\nWhen?", listOf(listOf(9L), listOf(10L)), null, true))
            .thenReturn(selected)
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(9L, 10L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(9L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(10L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(emptyList()))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        val version = sourceVersion()
        val key1 = canonicalKey(version)
        val key2 = canonicalKey(version, 2, "When?")
        val currentEvidence = evidenceWithMapping(
            "evidence-v1",
            key1 to listOf(9L),
            key2 to listOf(10L)
        )
        val perRequest1 = perRequestEvidence("evidence-v1", key1, listOf(9L))
        val perRequest2 = perRequestEvidence("evidence-v1", key2, listOf(10L))
        val stored1 = lockedAnswer(version, perRequest1, 1, "What?", answerText = "answer-one")
        val stored2 = lockedAnswer(version, perRequest2, 2, "When?", answerText = "answer-two", ruleId = 10L)
        val storedPayload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = version,
            evidenceSetVersion = currentEvidence,
            requestedFactIds = listOf(9L, 10L),
            selectedModel = "DEEPSEEK_V4_PRO",
            lockedItems = listOf(stored1, stored2),
            requestFactSelections = listOf(
                TrustReplyRequestFactSelection(key1, listOf(9L)),
                TrustReplyRequestFactSelection(key2, listOf(10L))
            ),
            frameSnapshot = TrustReplyFrameSnapshot(
                selection = TrustReplyFrameSelection(salutationSnippetId = 7L),
                version = "saved-frame-v1"
            )
        )
        Mockito.`when`(stateStore.load("TRAINING_MAIL", 11L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(3L, LocalDateTime.now().plusDays(1), "{}")
        )
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(storedPayload)
        // 行内已存 v4 矩阵 → saveStateItem 按其解析（与 bootstrap 同款）。
        var capturedPayload: TrustReplySavedStatePayload? = null
        Mockito.doAnswer { invocation ->
            capturedPayload = invocation.arguments[0] as TrustReplySavedStatePayload
            "{}"
        }.`when`(stateStore).encodePayload(
            Mockito.any(TrustReplySavedStatePayload::class.java) ?: TrustReplySavedStatePayload(
                schemaVersion = "",
                sourceVersion = "",
                evidenceSetVersion = "",
                requestedFactIds = emptyList(),
                selectedModel = "",
                lockedItems = emptyList()
            )
        )
        Mockito.`when`(stateStore.save(Mockito.anyString() ?: "TRAINING_MAIL", Mockito.anyLong() ?: 11L, Mockito.anyLong() ?: 3L, Mockito.anyString() ?: "", Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())).thenReturn(4L)

        val updated1 = lockedAnswer(version, perRequest1, 1, "What?", answerText = "answer-one-updated")
        val response = service.saveStateItem(TrustReplySaveStateItemRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L),
            expectedStateVersion = 3,
            sourceVersion = version,
            evidenceSetVersion = currentEvidence,
            requestKey = key1,
            lockedItem = updated1
        ))

        assertEquals("SAVED", response.status)
        assertEquals(4L, response.stateVersion)
        assertEquals(listOf(key1, key2), response.lockedItems.map { it.requestKey })
        assertEquals("answer-one-updated", response.lockedItems[0].answerText)
        assertEquals(stored2, response.lockedItems[1])
        // 其余整封字段逐字保留（不因单条合并而重写）。
        assertEquals("DEEPSEEK_V4_PRO", response.selectedModel)
        assertEquals(listOf(9L, 10L), response.requestedFactIds)
        assertEquals("saved-frame-v1", response.frameSnapshot?.version)
        assertEquals(storedPayload.requestFactSelections, response.requestFactSelections)
        Mockito.verify(stateStore).save(
            Mockito.anyString() ?: "TRAINING_MAIL",
            Mockito.anyLong() ?: 11L,
            Mockito.anyLong() ?: 3L,
            Mockito.anyString() ?: "",
            Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()
        )
        assertEquals(updated1, requireNotNull(capturedPayload).lockedItems[0])
        assertEquals(stored2, requireNotNull(capturedPayload).lockedItems[1])
    }

    // 计划 14 (T-5.3, I-4): 乐观锁冲突返回既有的 stale 码，绝不停靠校验直接覆盖。
    @Test
    fun `saveStateItem propagates optimistic state conflicts as the existing stale code`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val key = canonicalKey(version)
        val currentEvidence = evidenceWithMapping("evidence-v1", key to listOf(9L))
        val perRequest = perRequestEvidence("evidence-v1", key, listOf(9L))
        val stored1 = lockedAnswer(version, perRequest, 1, "What?", answerText = "answer-one")
        val storedPayload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = version,
            evidenceSetVersion = currentEvidence,
            requestedFactIds = listOf(9L),
            selectedModel = "DEEPSEEK_V4_FLASH",
            lockedItems = listOf(stored1),
            requestFactSelections = canonicalMatrix(version)
        )
        Mockito.`when`(stateStore.load("TRAINING_MAIL", 11L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(9L, LocalDateTime.now().plusDays(1), "{}")
        )
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(storedPayload)
        Mockito.`when`(stateStore.encodePayload(Mockito.any(TrustReplySavedStatePayload::class.java) ?: TrustReplySavedStatePayload(
            schemaVersion = "",
            sourceVersion = "",
            evidenceSetVersion = "",
            requestedFactIds = emptyList(),
            selectedModel = "",
            lockedItems = emptyList()
        ))).thenReturn("{}")
        Mockito.`when`(stateStore.save(Mockito.anyString() ?: "TRAINING_MAIL", Mockito.anyLong() ?: 11L, Mockito.anyLong() ?: 9L, Mockito.anyString() ?: "", Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()))
            .thenThrow(TrustReplyWorkbenchException(HttpStatus.CONFLICT, "TRUST_REPLY_STATE_CONFLICT"))

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.saveStateItem(TrustReplySaveStateItemRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                expectedStateVersion = 9,
                sourceVersion = version,
                evidenceSetVersion = currentEvidence,
                requestKey = key,
                lockedItem = stored1
            ))
        }
        assertEquals("TRUST_REPLY_STATE_CONFLICT", ex.code)
        Mockito.verify(stateStore).save(
            Mockito.anyString() ?: "TRAINING_MAIL",
            Mockito.anyLong() ?: 11L,
            Mockito.anyLong() ?: 9L,
            Mockito.anyString() ?: "",
            Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()
        )
    }

    // V-1: a time-commitment display name using a Chinese numeral (三天后答复)
    // is neither a decimal digit nor a listed phrase; it must be omitted while
    // safe neighboring names stay eligible.
    @Test
    fun `suggested instruction omits time-commitment display names`() {
        stubCanonicalSource(listOf(
            item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED),
            item(2, "How?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
        ))
        Mockito.`when`(qaRules.findAllById(Mockito.anyList())).thenReturn(listOf(
            QaRule(id = 9L, categoryId = 3L, keywords = "what", replySubject = null, replyBody = "", answerBody = "answer", displayName = "三天后答复"),
            QaRule(id = 10L, categoryId = 3L, keywords = "safe", replySubject = null, replyBody = "", answerBody = "second", displayName = "薪资标准")
        ))

        val instruction = requireNotNull(service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L)
        )).requestCoverage.single { it.status == "UNSUPPORTED" }.suggestedInstruction)

        assertTrue(instruction.isNotBlank())
        assertTrue(instruction.length <= 500)
        assertFalse(instruction.contains("三天后"), "the time-commitment name must be omitted")
        assertFalse(instruction.contains("天后"), "the time-commitment phrase must be omitted")
        assertTrue(instruction.contains("薪资标准"), "safe adjacent names must remain")
        assertTrue(instruction.contains("先说明它取决于什么、还没定下来的原因"))
        assertTrue(instruction.contains("交出下一步但不承诺具体时间"))
    }

    // V-4: a display name that equals a 12+-character fragment of an adjacent
    // rule answerBody is omitted; the suggestion stays non-empty with the safe
    // structure and the remaining safe names.
    @Test
    fun `suggested instruction omits display names overlapping answer bodies`() {
        val bodyFragment = "薪酬保密制度适用于全体正式员工"
        stubCanonicalSource(listOf(
            item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED),
            item(2, "How?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
        ))
        Mockito.`when`(qaRules.findAllById(Mockito.anyList())).thenReturn(listOf(
            QaRule(id = 9L, categoryId = 3L, keywords = "what", replySubject = null, replyBody = "", answerBody = bodyFragment, displayName = bodyFragment.take(20)),
            QaRule(id = 10L, categoryId = 3L, keywords = "safe", replySubject = null, replyBody = "", answerBody = "second body", displayName = "薪资标准")
        ))

        val instruction = requireNotNull(service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L)
        )).requestCoverage.single { it.status == "UNSUPPORTED" }.suggestedInstruction)

        assertTrue(instruction.isNotBlank())
        assertTrue(instruction.length <= 500)
        assertFalse(instruction.contains(bodyFragment), "answer body must never enter the suggestion")
        assertFalse(instruction.contains(bodyFragment.take(12)), "a 12+ char body fragment carried by the display name must be omitted")
        assertTrue(instruction.contains("薪资标准"), "distinct safe adjacent names must remain")
        assertTrue(instruction.contains("先说明它取决于什么、还没定下来的原因"))
        assertTrue(instruction.contains("交出下一步但不承诺具体时间"))
    }

    @Test
    fun `saveState rejects forged request key and keeps the store untouched`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val currentEvidence = evidenceWithMapping("evidence-v1", canonicalKey(version) to listOf(9L))
        val forged = lockedAnswer(version, perRequestEvidence("evidence-v1", canonicalKey(version), listOf(9L)), 1, "What?").copy(requestKey = "forged")

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
        val tampered = lockedAnswer(version, perRequestEvidence("evidence-v1", canonicalKey(version), listOf(9L)), 1, "What?").copy(versionId = "not-the-canonical-id")

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
        val perRequest = perRequestEvidence("evidence-v1", canonicalKey(version), listOf(9L))
        val locked = lockedAnswer(version, perRequest, 1, "What?")
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
        val perRequest = perRequestEvidence("evidence-v1", canonicalKey(version), listOf(9L))
        val locked = lockedAnswer(version, perRequest, 1, "What?")

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
        val perRequest = perRequestEvidence("evidence-v1", canonicalKey(version), listOf(9L))
        val locked = lockedAnswer(version, perRequest, 1, "What?")
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
    fun `bootstrap marks stale only on source drift not on aggregate evidence drift`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val key = canonicalKey(version)
        val perRequest = perRequestEvidence("evidence-v1", key, listOf(9L))
        val locked = lockedAnswer(version, perRequest, 1, "What?")
        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = version,
            evidenceSetVersion = "old-aggregate",
            requestedFactIds = listOf(9L),
            requestFactSelections = canonicalMatrix(version),
            selectedModel = "DEEPSEEK_V4_FLASH",
            lockedItems = listOf(locked)
        )
        Mockito.`when`(stateStore.load("TRAINING_MAIL", 11L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(2L, LocalDateTime.now().plusDays(1), "{}")
        )
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(payload)

        // I-4: the saved aggregate fingerprint no longer gates the restore;
        // the per-request item evidence is what matters.
        val restored = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))
        assertEquals("RESTORED", restored.savedState?.status)
        assertEquals(2L, restored.savedState?.stateVersion)
        assertEquals(listOf(locked.requestKey), restored.savedState?.lockedItems?.map { it.requestKey })

        // Source drift keeps the whole-draft STALE (must-NOT-change 6).
        val drifted = payload.copy(sourceVersion = "old-source")
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(drifted)
        val stale = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))
        assertEquals("STALE", stale.savedState?.status)
        assertEquals(2L, stale.savedState?.stateVersion)
        assert(stale.savedState?.lockedItems?.isEmpty() == true)
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

    // 计划 01 (阶段 3, I-5): 摘抄器收紧后旧快照含 5 条签名 requestKey，新
    // extractor 只剩真实 question —— 隐式 saved 矩阵无法对应新 request 集合时
    // bootstrap 回退默认选择并标 STALE（绝不 422），不猜测把旧事实重新绑定；
    // 显式传入同一旧矩阵仍 422（未知 requestKey → TRUST_REPLY_REQUEST_KEY_INVALID）。
    @Test
    fun `old saved matrix with signature request keys falls back to stale after extractor tightening`() {
        val body = listOf(
            "Could you tell me the official programme name and the usual form of collaboration?",
            "",
            "*Name*",
            "*Title*",
            "*Institution*",
            "*Phone*",
            "*Address*"
        ).joinToString("\n")
        val exact = mail(id = 11L, body = body)
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))

        val question = "Could you tell me the official programme name and the usual form of collaboration?"
        val current = selection(item(1, question, listOf(9L), RequestGroundingStatus.GROUNDED))
        Mockito.`when`(factSelection.selectForWorkbench(body, null, null, true)).thenReturn(current)
        Mockito.`when`(factSelection.selectForWorkbench(body, null, listOf(9L), true)).thenReturn(current)
        Mockito.`when`(factSelection.selectForWorkbench(body, listOf(listOf(9L)), null, true)).thenReturn(current)
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(current.sendQaRuleIds))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(9L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(emptyList()))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))

        val version = service.resolveSource(TrustReplySourceRef(TRAINING_MAIL, 11L)).sourceVersion
        // 服务端 canonicalMatrix 的 key 取自 item.intents（stub 夹具为
        // general.answer），而非 matchIntents 的目录意图；期望值必须镜像夹具。
        val questionKey = TrustReplyWorkbenchService.requestKey(version, 1, question, listOf("general.answer"))
        // 旧 extractor 的结果：真实 question（key 与新的 known key 一致）+ 5 条
        // *...* 签名（旧 requestKey，已从新集合消失）→ 只有签名 key 漂移。
        val oldQuestionKey = TrustReplyWorkbenchService.requestKey(
            version, 1, question,
            AiReplyIntentCatalog.matchIntents(question).map { it.key }
        )
        val oldMatrix = mutableListOf(
            TrustReplyRequestFactSelection(oldQuestionKey, listOf(9L))
        )
        listOf("*Name*", "*Title*", "*Institution*", "*Phone*", "*Address*")
            .forEachIndexed { i, text ->
                oldMatrix += TrustReplyRequestFactSelection(
                    TrustReplyWorkbenchService.requestKey(version, i + 2, text, emptyList()),
                    listOf(9L)
                )
            }
        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = version,
            evidenceSetVersion = "evidence-v1",
            requestedFactIds = listOf(9L),
            requestFactSelections = oldMatrix,
            selectedModel = "DEEPSEEK_V4_FLASH",
            lockedItems = emptyList()
        )
        Mockito.`when`(stateStore.load("TRAINING_MAIL", 11L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(7L, LocalDateTime.now().plusDays(1), "{}")
        )
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(payload)

        // I-5: 隐式 saved 矩阵无法对应新 request 集合 → 默认选择 + STALE，绝不 422。
        val restored = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))
        assertEquals("STALE", restored.savedState?.status)
        assertEquals(7L, restored.savedState?.stateVersion)
        assert(restored.savedState?.lockedItems?.isEmpty() == true)
        assertEquals(
            listOf(TrustReplyRequestFactSelection(questionKey, listOf(9L))),
            restored.requestFactSelections
        )

        // 显式传入同一旧矩阵仍 422：未知 requestKey → TRUST_REPLY_REQUEST_KEY_INVALID。
        val explicit = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.bootstrap(
                TrustReplyBootstrapRequest(
                    source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                    requestFactSelections = oldMatrix
                )
            )
        }
        assertEquals("TRUST_REPLY_REQUEST_KEY_INVALID", explicit.code)
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
        val tampered = lockedAnswer(version, perRequestEvidence("evidence-v1", canonicalKey(version), listOf(9L)), 1, "What?").copy(versionId = "forged")
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
        val perRequest = perRequestEvidence("evidence-v1", canonicalKey(version), listOf(9L))
        val locked = lockedAnswer(version, perRequest, 1, "What?")
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
    fun `bootstrap surfaces intent mismatch diagnostics per request without failing`() {
        // 计划 02 (I-2): 人工事实整体生效（factRuleIds/boundRuleIds/canonicalMatrix
        // 均为运营矩阵），intentMismatchFactRuleIds 作为诊断投影进 coverage，
        // 不进 canonicalMatrix。
        val exact = mail(id = 11L, body = "What?")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))
        val mismatchItem = item(1, "What?", listOf(10L, 20L), RequestGroundingStatus.UNSUPPORTED)
            .copy(intentMatchedFactRuleIds = emptyList(), intentMismatchFactRuleIds = listOf(10L, 20L))
        val facts = ResolvedQaRules(
            sendQaRuleIds = listOf(10L, 20L),
            promptRuleIds = listOf(10L, 20L),
            requestFacts = listOf(mismatchItem),
            requestCount = 1,
            groundedRequestCount = 0
        )
        Mockito.`when`(factSelection.selectForWorkbench("What?", listOf(listOf(10L, 20L)), null, true)).thenReturn(facts)
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(10L, 20L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))

        val version = sourceVersion()
        val key = canonicalKey(version)
        val bootstrap = service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L),
            requestFactSelections = listOf(TrustReplyRequestFactSelection(key, listOf(10L, 20L)))
        ))

        val coverage = bootstrap.requestCoverage.single()
        assertEquals(listOf(10L, 20L), coverage.factRuleIds)
        assertEquals(emptyList<Long>(), coverage.intentMatchedFactRuleIds)
        assertEquals(listOf(10L, 20L), coverage.intentMismatchFactRuleIds)
        // I-2: canonicalMatrix 只投影 requestKey + factRuleIds（诊断不进矩阵）。
        assertEquals(listOf(TrustReplyRequestFactSelection(key, listOf(10L, 20L))), bootstrap.requestFactSelections)
    }

    @Test
    fun `intent mismatch diagnostics never change the per-request evidence version`() {
        // 计划 02 (I-2): 同一事实集、不同 matched/mismatch 拆分产生的
        // requestCoverage[].evidenceSetVersion 必须完全相同——诊断字段不进哈希。
        val exact = mail(id = 11L, body = "What?")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))
        val mismatchFacts = ResolvedQaRules(
            sendQaRuleIds = listOf(9L),
            promptRuleIds = listOf(9L),
            requestFacts = listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.UNSUPPORTED)
                .copy(intentMatchedFactRuleIds = emptyList(), intentMismatchFactRuleIds = listOf(9L))),
            requestCount = 1,
            groundedRequestCount = 0
        )
        val matchedFacts = ResolvedQaRules(
            sendQaRuleIds = listOf(9L),
            promptRuleIds = listOf(9L),
            requestFacts = listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.UNSUPPORTED)
                .copy(intentMatchedFactRuleIds = listOf(9L), intentMismatchFactRuleIds = emptyList())),
            requestCount = 1,
            groundedRequestCount = 0
        )
        Mockito.`when`(factSelection.selectForWorkbench("What?", listOf(listOf(9L)), null, true))
            .thenReturn(mismatchFacts, matchedFacts)
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(9L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))

        val version = sourceVersion()
        val key = canonicalKey(version)
        val mismatchBootstrap = service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L),
            requestFactSelections = listOf(TrustReplyRequestFactSelection(key, listOf(9L)))
        ))
        val matchedBootstrap = service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L),
            requestFactSelections = listOf(TrustReplyRequestFactSelection(key, listOf(9L)))
        ))

        val mismatchVersion = mismatchBootstrap.requestCoverage.single().evidenceSetVersion
        val matchedVersion = matchedBootstrap.requestCoverage.single().evidenceSetVersion
        assertFalse(mismatchVersion.isBlank())
        assertEquals(matchedVersion, mismatchVersion)
    }

    @Test
    fun `canonical matrix and coverage both project the manual fact set`() {
        // 计划 02 (I-1): canonicalMatrix 与 toCoverage 同一次提交逐字相等
        // （前端 applyBootstrap 守卫的比较对象）——两者都投影人工最终事实集。
        val exact = mail(id = 11L, body = "What?")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))
        val boundItem = item(1, "What?", listOf(10L, 20L), RequestGroundingStatus.UNSUPPORTED)
            .copy(intentMismatchFactRuleIds = listOf(10L, 20L))
        val facts = ResolvedQaRules(
            sendQaRuleIds = listOf(10L, 20L),
            promptRuleIds = listOf(10L, 20L),
            requestFacts = listOf(boundItem),
            requestCount = 1,
            groundedRequestCount = 0
        )
        Mockito.`when`(factSelection.selectForWorkbench("What?", listOf(listOf(10L, 20L)), null, true)).thenReturn(facts)
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(10L, 20L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))

        val version = sourceVersion()
        val key = canonicalKey(version)
        val bootstrap = service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L),
            requestFactSelections = listOf(TrustReplyRequestFactSelection(key, listOf(10L, 20L)))
        ))

        assertEquals(listOf(TrustReplyRequestFactSelection(key, listOf(10L, 20L))), bootstrap.requestFactSelections)
        assertEquals(listOf(10L, 20L), bootstrap.requestCoverage.single().factRuleIds)
    }

    @Test
    fun `evidence version is unchanged when every binding is accepted`() {
        // P2a (I-5): 绑定全部被采纳时（boundRuleIds == factRuleIds），显式矩阵
        // 路径与自动匹配路径同集合产生的 per-request evidenceSetVersion 完全相同。
        val exact = mail(id = 11L, body = "What?")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))
        val accepted = ResolvedQaRules(
            sendQaRuleIds = listOf(9L),
            promptRuleIds = listOf(9L),
            requestFacts = listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)),
            requestCount = 1,
            groundedRequestCount = 1
        )
        Mockito.`when`(factSelection.selectForWorkbench("What?", listOf(listOf(9L)), null, true)).thenReturn(accepted)
        Mockito.`when`(factSelection.selectForWorkbench("What?", null, null, true)).thenReturn(accepted)
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(9L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))

        val version = sourceVersion()
        val key = canonicalKey(version)
        val matrixBootstrap = service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L),
            requestFactSelections = listOf(TrustReplyRequestFactSelection(key, listOf(9L)))
        ))
        val autoBootstrap = service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L)
        ))

        val matrixVersion = matrixBootstrap.requestCoverage.single().evidenceSetVersion
        val autoVersion = autoBootstrap.requestCoverage.single().evidenceSetVersion
        assertFalse(matrixVersion.isBlank())
        assertEquals(autoVersion, matrixVersion)
        assertEquals(perRequestEvidence("evidence-v1", key, listOf(9L)), matrixVersion)
    }

    @Test
    fun `suggested instruction never names a bound-but-unsupported fact`() {
        // P2a (must-NOT-change 4 / B-5): toCoverage 内 adjacentIds 与
        // filterKeys 仍读 factRuleIds——运营绑了但没成为证据的事实不得出现在
        // 机器代填的回答说明里。
        stubCanonicalSource(listOf(
            item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED),
            item(2, "How?", emptyList(), RequestGroundingStatus.UNSUPPORTED).copy(boundRuleIds = listOf(10L))
        ))
        Mockito.`when`(qaRules.findAllById(Mockito.anyList())).thenAnswer { invocation ->
            val ids = invocation.getArgument(0) as List<*>
            listOf(
                QaRule(id = 9L, categoryId = 3L, keywords = "what", replySubject = null, replyBody = "", answerBody = "answer", displayName = "已认可事实"),
                QaRule(id = 10L, categoryId = 3L, keywords = "bound", replySubject = null, replyBody = "", answerBody = "bound body", displayName = "绑定未认可事实")
            ).filter { it.id in ids }
        }
        // P2a (I-2): item2 的 boundRuleIds=[10] 进入 per-request 版本身份，
        // 需要对应的 base snapshot stub。
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(10L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))

        val instruction = requireNotNull(service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L)
        )).requestCoverage.single { it.status == "UNSUPPORTED" }.suggestedInstruction)

        assertTrue(instruction.isNotBlank())
        assertTrue(instruction.length <= 500)
        assertFalse(instruction.contains("绑定未认可事实"), "a bound-but-unsupported fact must never be suggested as basis")
        assertTrue(instruction.contains("已认可事实"), "evidence-backed adjacent names must remain")
        assertTrue(instruction.contains("先说明它取决于什么、还没定下来的原因"))
    }

    @Test
    fun `suggested handling stays inside the allowed set`() {
        stubCanonicalSource(listOf(
            item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED),
            item(2, "Who?", listOf(10L), RequestGroundingStatus.PARTIAL),
            item(3, "How?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
        ))
        Mockito.`when`(qaRules.findAllById(Mockito.anyList())).thenReturn(emptyList<QaRule>())

        val coverage = service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L)
        )).requestCoverage

        assertEquals(3, coverage.size)
        coverage.forEach { itemCoverage ->
            assertTrue(
                itemCoverage.recommendedHandling in itemCoverage.allowedHandlings,
                "recommendedHandling ${itemCoverage.recommendedHandling} must be allowed for ${itemCoverage.status}"
            )
        }
    }

    @Test
    fun `suggested instruction only for unsupported items within 500 chars`() {
        stubCanonicalSource(listOf(
            item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED),
            item(2, "Who?", listOf(10L), RequestGroundingStatus.PARTIAL),
            item(3, "How?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
        ))
        Mockito.`when`(qaRules.findAllById(Mockito.anyList())).thenReturn(listOf(
            QaRule(
                id = 9L,
                categoryId = 3L,
                keywords = "what",
                replySubject = null,
                replyBody = "",
                answerBody = "answer",
                displayName = "What"
            ),
            QaRule(
                id = 10L,
                categoryId = 3L,
                keywords = "who",
                replySubject = null,
                replyBody = "",
                answerBody = "who answer",
                displayName = "Who"
            )
        ))

        val coverage = service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L)
        )).requestCoverage

        val unsupported = coverage.single { it.status == "UNSUPPORTED" }
        val instruction = requireNotNull(unsupported.suggestedInstruction)
        assertTrue(instruction.isNotBlank())
        assertTrue(instruction.length <= 500)
        assertEquals(null, coverage.single { it.status == "GROUNDED" }.suggestedInstruction)
        assertEquals(null, coverage.single { it.status == "PARTIAL" }.suggestedInstruction)
    }

    // V-1: display names may be up to 120 characters each; with enough of them
    // the naive join would exceed the 500-char operator-instruction cap. The
    // instruction must stay non-empty, keep the complete mandatory wording and
    // drop only optional adjacent names that would overflow the budget.
    @Test
    fun `suggested instruction stays within 500 chars with maximum-length adjacent names`() {
        val name = "甲".repeat(120)
        stubCanonicalSource(listOf(
            item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED),
            item(2, "How?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
        ))
        Mockito.`when`(qaRules.findAllById(Mockito.anyList())).thenReturn(listOf(
            QaRule(id = 9L, categoryId = 3L, keywords = "what", replySubject = null, replyBody = "", answerBody = "answer", displayName = name),
            QaRule(id = 10L, categoryId = 3L, keywords = "how", replySubject = null, replyBody = "", answerBody = "second", displayName = "${name}乙"),
            QaRule(id = 11L, categoryId = 3L, keywords = "why", replySubject = null, replyBody = "", answerBody = "third", displayName = "${name}丙"),
            QaRule(id = 12L, categoryId = 3L, keywords = "when", replySubject = null, replyBody = "", answerBody = "fourth", displayName = "${name}丁")
        ))

        val instruction = requireNotNull(service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L)
        )).requestCoverage.single { it.status == "UNSUPPORTED" }.suggestedInstruction)

        assertTrue(instruction.isNotBlank())
        assertTrue(instruction.length <= 500, "instruction length must stay within 500, was ${instruction.length}")
        assertTrue(instruction.contains("先说明它取决于什么、还没定下来的原因"))
        assertTrue(instruction.contains("交出下一步但不承诺具体时间"))
        // names are included greedily while the budget allows
        assertTrue(instruction.contains(name))
        assertFalse(instruction.contains("${name}丁"), "the 4th 120-char adjacent name must be dropped to stay within 500")
    }

    // V-1: adjacent display names are optional context and may never smuggle
    // digits, links or time-promise tokens into the sole operator answer basis.
    // Unsafe names are omitted entirely; the safe name remains.
    @Test
    fun `suggested instruction excludes unsafe adjacent names`() {
        stubCanonicalSource(listOf(
            item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED),
            item(2, "How?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
        ))
        Mockito.`when`(qaRules.findAllById(Mockito.anyList())).thenReturn(listOf(
            QaRule(id = 9L, categoryId = 3L, keywords = "what", replySubject = null, replyBody = "", answerBody = "answer", displayName = "Salary band"),
            QaRule(id = 10L, categoryId = 3L, keywords = "digit", replySubject = null, replyBody = "", answerBody = "second", displayName = "薪酬 2026 版"),
            QaRule(id = 11L, categoryId = 3L, keywords = "link", replySubject = null, replyBody = "", answerBody = "third", displayName = "详见 http://internal/wiki/salary"),
            QaRule(id = 12L, categoryId = 3L, keywords = "promise", replySubject = null, replyBody = "", answerBody = "fourth", displayName = "请尽快回复")
        ))

        val instruction = requireNotNull(service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L)
        )).requestCoverage.single { it.status == "UNSUPPORTED" }.suggestedInstruction)

        assertTrue(instruction.isNotBlank())
        assertTrue(instruction.length <= 500)
        assertTrue(instruction.contains("Salary band"), "the safe adjacent name must remain")
        assertFalse(instruction.contains("2026"), "digit-carrying name must be omitted")
        assertFalse(instruction.contains("http", ignoreCase = true), "link-carrying name must be omitted")
        assertFalse(instruction.contains("尽快"), "time-promise name must be omitted")
        assertTrue(instruction.contains("先说明它取决于什么、还没定下来的原因"))
        assertTrue(instruction.contains("交出下一步但不承诺具体时间"))
    }

    // V-2: URL/domain and Chinese-numeral time-commitment display names are
    // omitted; the instruction stays non-empty with the safe structure and the
    // remaining safe names.
    @Test
    fun `suggested instruction omits url and time-promise display names`() {
        stubCanonicalSource(listOf(
            item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED),
            item(2, "How?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
        ))
        Mockito.`when`(qaRules.findAllById(Mockito.anyList())).thenReturn(listOf(
            QaRule(id = 9L, categoryId = 3L, keywords = "what", replySubject = null, replyBody = "", answerBody = "answer", displayName = "请在一周内回复：www.example.com"),
            QaRule(id = 10L, categoryId = 3L, keywords = "safe", replySubject = null, replyBody = "", answerBody = "second", displayName = "薪资标准")
        ))

        val instruction = requireNotNull(service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L)
        )).requestCoverage.single { it.status == "UNSUPPORTED" }.suggestedInstruction)

        assertTrue(instruction.isNotBlank())
        assertTrue(instruction.length <= 500)
        assertFalse(instruction.contains("www", ignoreCase = true), "www/domain name must be omitted")
        assertFalse(instruction.contains("一周内"), "time-promise name must be omitted")
        assertFalse(instruction.contains("example.com"), "dotted domain must be omitted")
        assertTrue(instruction.contains("薪资标准"), "safe adjacent names must remain")
        assertTrue(instruction.contains("先说明它取决于什么、还没定下来的原因"))
        assertTrue(instruction.contains("交出下一步但不承诺具体时间"))
    }

    // V-1: when every adjacent name is unsafe the instruction must still be
    // emitted with the complete safe structure and no adjacent-name clause.
    @Test
    fun `suggested instruction stays safe when every adjacent name is unsafe`() {
        stubCanonicalSource(listOf(
            item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED),
            item(2, "How?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
        ))
        Mockito.`when`(qaRules.findAllById(Mockito.anyList())).thenReturn(listOf(
            QaRule(id = 9L, categoryId = 3L, keywords = "what", replySubject = null, replyBody = "", answerBody = "answer", displayName = "薪酬 2026 版"),
            QaRule(id = 10L, categoryId = 3L, keywords = "link", replySubject = null, replyBody = "", answerBody = "second", displayName = "请尽快联系 http://internal")
        ))

        val instruction = requireNotNull(service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L)
        )).requestCoverage.single { it.status == "UNSUPPORTED" }.suggestedInstruction)

        assertTrue(instruction.isNotBlank())
        assertTrue(instruction.length <= 500)
        assertFalse(instruction.contains("邻近事实"), "no adjacent-name clause may appear when every name is unsafe")
        assertFalse(instruction.contains("2026"))
        assertFalse(instruction.contains("http", ignoreCase = true))
        assertFalse(instruction.contains("尽快"))
        assertTrue(instruction.contains("先说明它取决于什么、还没定下来的原因"))
        assertTrue(instruction.contains("交出下一步但不承诺具体时间"))
    }

    // I-0: the machine instruction describes HOW to answer; it must never carry
    // rule answer bodies (>=12-char fragments), digits, links or time promises,
    // because the ANSWER_FROM_OPERATOR_INPUT prompt treats it as the sole basis.
    @Test
    fun `suggested instruction never leaks fact bodies numbers links or time promises`() {
        val leakyBody = "The salary band for senior experts is 800k to 1.2M RMB per year; payday is the 25th of next month. http://internal/wiki/salary"
        stubCanonicalSource(listOf(
            item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED),
            item(2, "Who?", listOf(10L), RequestGroundingStatus.PARTIAL),
            item(3, "How?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
        ))
        Mockito.`when`(qaRules.findAllById(Mockito.anyList())).thenReturn(listOf(
            QaRule(
                id = 9L,
                categoryId = 3L,
                keywords = "what",
                replySubject = null,
                replyBody = "",
                answerBody = leakyBody,
                displayName = "Salary band"
            ),
            QaRule(
                id = 10L,
                categoryId = 3L,
                keywords = "who",
                replySubject = null,
                replyBody = "",
                answerBody = "unrelated answer body",
                displayName = "Unrelated fact"
            )
        ))

        val coverage = service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L)
        )).requestCoverage
        val instruction = requireNotNull(coverage.single { it.status == "UNSUPPORTED" }.suggestedInstruction)

        listOf(leakyBody, "unrelated answer body").forEach { body ->
            for (i in 0..body.length - 12) {
                assertFalse(instruction.contains(body.substring(i, i + 12)), "instruction must not leak a 12-char fragment of an answerBody")
            }
        }
        assertFalse(instruction.any { it.isDigit() })
        assertFalse(instruction.contains("http", ignoreCase = true))
        listOf("尽快", "立即", "马上", "今天", "明天", "后天", "本周", "下周", "本月", "下月", "小时内", "天内")
            .forEach { promise -> assertFalse(instruction.contains(promise), "instruction must not promise a time: $promise") }
        // names of adjacent rules are allowed (names only, never bodies)
        assertTrue(instruction.contains("Salary band"))
        assertTrue(instruction.contains("Unrelated fact"))
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
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(emptyList()))
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
        val perRequest = perRequestEvidence("evidence-v1", key, listOf(9L))
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
            expectedEvidenceSetVersion = perRequest,
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
    fun `bootstrap marks v1 saved locks stale under per request semantics`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val currentEvidence = evidenceWithMapping("evidence-v1", canonicalKey(version) to listOf(9L))
        // A v1 locked item carries the legacy aggregate fingerprint.
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

        // I-6: the aggregate fingerprint can never equal the fresh per-request
        // value, so the lock is dropped and the all-dropped snapshot is STALE.
        val restored = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))

        assertEquals("STALE", restored.savedState?.status)
        assert(restored.savedState?.lockedItems?.isEmpty() == true)
        assertEquals(canonicalMatrix(version), restored.requestFactSelections)
    }

    @Test
    fun `bootstrap partial restore keeps matching items and drops stale per request evidence`() {
        val exact = mail(id = 11L, body = "What?\nWho?\nHow?")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))
        val threeItems = listOf(
            item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED),
            item(2, "Who?", listOf(10L), RequestGroundingStatus.GROUNDED),
            item(3, "How?", listOf(11L), RequestGroundingStatus.GROUNDED)
        )
        Mockito.`when`(factSelection.selectForWorkbench(
            "What?\nWho?\nHow?",
            listOf(listOf(9L), listOf(10L), listOf(11L)),
            null,
            true
        )).thenReturn(selection(*threeItems.toTypedArray()))
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(Mockito.anyList<Long>()))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        val version = sourceVersion()
        val key1 = canonicalKey(version)
        val key2 = canonicalKey(version, 2, "Who?")
        val key3 = canonicalKey(version, 3, "How?")
        val matrix = listOf(
            TrustReplyRequestFactSelection(key1, listOf(9L)),
            TrustReplyRequestFactSelection(key2, listOf(10L)),
            TrustReplyRequestFactSelection(key3, listOf(11L))
        )
        val aggregate = evidenceWithMapping(
            "evidence-v1",
            key1 to listOf(9L),
            key2 to listOf(10L),
            key3 to listOf(11L)
        )
        val locked1 = lockedAnswer(version, perRequestEvidence("evidence-v1", key1, listOf(9L)), 1, "What?", ruleId = 9L)
        val locked2 = lockedAnswer(version, perRequestEvidence("evidence-v1", key2, listOf(10L)), 2, "Who?", answerText = "who answer", ruleId = 10L)
        val locked3 = lockedAnswer(version, perRequestEvidence("evidence-v1", key3, listOf(11L)), 3, "How?", answerText = "how answer", ruleId = 11L)
        val staleLocked2 = locked2.copy(evidenceSetVersion = "old-per-request")
        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = version,
            evidenceSetVersion = aggregate,
            requestedFactIds = listOf(9L, 10L, 11L),
            requestFactSelections = matrix,
            selectedModel = "DEEPSEEK_V4_FLASH",
            lockedItems = listOf(locked1, staleLocked2, locked3)
        )
        Mockito.`when`(stateStore.load("TRAINING_MAIL", 11L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(5L, LocalDateTime.now().plusDays(1), "{}")
        )
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(payload)

        // I-4: only the drifted item is dropped; the two matching locks survive.
        val restored = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))

        assertEquals("PARTIALLY_RESTORED", restored.savedState?.status)
        assertEquals(1, restored.savedState?.droppedItemCount)
        assertEquals(
            listOf(locked1.requestKey, locked3.requestKey),
            restored.savedState?.lockedItems?.map { it.requestKey }
        )

        // I-4: when every locked item drifted the snapshot is whole STALE.
        val allStale = payload.copy(lockedItems = listOf(
            staleLocked2.copy(requestKey = locked1.requestKey, versionId = "x1"),
            staleLocked2,
            staleLocked2.copy(requestKey = locked3.requestKey, versionId = "x3")
        ))
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(allStale)
        val stale = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))

        assertEquals("STALE", stale.savedState?.status)
        assertEquals(3, stale.savedState?.droppedItemCount)
        assert(stale.savedState?.lockedItems?.isEmpty() == true)
    }

    @Test
    fun `bootstrap marks stale when stored v4 matrix drifts from current`() {
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
        val perRequest = perRequestEvidence("evidence-v1", key, listOf(9L))

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
        Mockito.`when`(pointByPointComposer.composeLockedItems(listOf("Salary info"), defaultFrame))
            .thenReturn("raw Salary info")
        Mockito.`when`(previewService.preview("raw Salary info", contact(), null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("rendered Salary info", emptyList()))

        val adjust = service.adjustItem(TrustReplyItemAdjustmentRequest(
            source = source,
            expectedSourceVersion = version,
            expectedEvidenceSetVersion = perRequest,
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

    // ── 12-letter-closer (T-4.8 / I-7 / IP-2) ─────────────────────────────────

    @Test
    fun `letter closing keeps canonical fact ids identical to the selection`() {
        // 两条 request 命中同一条事实（sourceRuleIds 相同）→ 收口把正文去重为首次
        // 出现的措辞；canonicalFactIds 仍来自 selection.sendQaRuleIds，与收口前
        // 完全相同（I-7 / IP-2：审计规则集来自选择，不随文本形态变化而丢失）。
        val item1 = item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)
        val item2 = item(2, "How?", listOf(9L), RequestGroundingStatus.GROUNDED)
        stubCanonicalSource(listOf(item1, item2))
        val version = sourceVersion()
        val expectedFactIds = (item1.factRuleIds + item2.factRuleIds).distinct()
        val evidenceVersion = evidenceWithMapping(
            "evidence-v1",
            canonicalKey(version) to listOf(9L),
            canonicalKey(version, 2, "How?") to listOf(9L)
        )
        val locked = listOf(
            lockedAnswer(
                version,
                perRequestEvidence("evidence-v1", canonicalKey(version), listOf(9L)),
                1,
                "What?",
                answerText = "First wording of the shared fact."
            ),
            lockedAnswer(
                version,
                perRequestEvidence("evidence-v1", canonicalKey(version, 2, "How?"), listOf(9L)),
                2,
                "How?",
                answerText = "Second wording of the shared fact."
            )
        )
        // 收口后 composeLockedItems 只收到首次出现的措辞（I-2 去重已生效）。
        Mockito.`when`(pointByPointComposer.composeLockedItems(listOf("First wording of the shared fact."), defaultFrame))
            .thenReturn("First wording of the shared fact.")
        Mockito.`when`(previewService.preview("First wording of the shared fact.", contact(), null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("rendered", emptyList()))

        val assembled = service.assemble(TrustReplyAssembleRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L),
            expectedSourceVersion = version,
            expectedEvidenceSetVersion = evidenceVersion,
            lockedItems = locked,
            requestedFactIds = listOf(9L)
        ))

        // 正文被收口（同事实只出现一次），而审计规则集一字未变。
        assertEquals("First wording of the shared fact.", assembled.rawDraftText)
        assertEquals(expectedFactIds, assembled.canonicalFactIds)
    }

    // ── 02 selectable frame: bootstrap options, strict resolve, state persistence ──

    @Test
    fun `bootstrap returns frame options and default canonical frame snapshot`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        Mockito.`when`(replySnippetService.listSelectableFrameOptions()).thenReturn(listOf(
            com.weibo.talentintroduction.reply.service.ReplyFrameOption(1L, "SALUTATION", "Dear X,", 10, true),
            com.weibo.talentintroduction.reply.service.ReplyFrameOption(4L, "ACK", "Thanks", 10, false)
        ))

        val bootstrap = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))

        assertEquals(2, bootstrap.frameOptions.size)
        assertEquals(listOf(1L, 4L), bootstrap.frameOptions.map { it.id })
        assertEquals("Dear X,", bootstrap.frameOptions[0].content)
        assertEquals("frame-default", bootstrap.frameSnapshot?.version)
        assertEquals(1L, bootstrap.frameSnapshot?.selection?.salutationSnippetId)
        assertNull(bootstrap.frameSnapshot?.selection?.ackSnippetId)
        assertNull(bootstrap.savedState)
    }

    @Test
    fun `bootstrap resolves explicit caller frame selection strictly`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val selection = ReplyFrameSelection(salutationSnippetId = 7L, closingSnippetId = 8L)
        Mockito.`when`(replySnippetService.resolveSelectableFrame(selection)).thenReturn(resolvedFrame(
            selection = selection,
            version = "caller-v1",
            salutation = "Caller Sal",
            closing = "Caller Close"
        ))

        val bootstrap = service.bootstrap(TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L),
            frameSnapshot = TrustReplyFrameSnapshot(
                selection = TrustReplyFrameSelection(salutationSnippetId = 7L, closingSnippetId = 8L),
                version = "caller-v1"
            )
        ))

        assertEquals("caller-v1", bootstrap.frameSnapshot?.version)
        assertEquals(7L, bootstrap.frameSnapshot?.selection?.salutationSnippetId)
        assertEquals(8L, bootstrap.frameSnapshot?.selection?.closingSnippetId)
    }

    @Test
    fun `bootstrap rejects invalid caller frame selection with 422`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        Mockito.`when`(
            replySnippetService.resolveSelectableFrame(
                Mockito.any(ReplyFrameSelection::class.java) ?: ReplyFrameSelection()
            )
        ).thenThrow(IllegalArgumentException("not found"))

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.bootstrap(TrustReplyBootstrapRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                frameSnapshot = TrustReplyFrameSnapshot(
                    selection = TrustReplyFrameSelection(salutationSnippetId = 99L)
                )
            ))
        }

        assertEquals("TRUST_REPLY_FRAME_SELECTION_INVALID", ex.code)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.status)
    }

    @Test
    fun `bootstrap rejects stale caller frame version with 409`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        Mockito.`when`(
            replySnippetService.resolveSelectableFrame(
                Mockito.any(ReplyFrameSelection::class.java) ?: ReplyFrameSelection()
            )
        ).thenReturn(resolvedFrame(
            selection = ReplyFrameSelection(),
            version = "fresh-version"
        ))

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.bootstrap(TrustReplyBootstrapRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                frameSnapshot = TrustReplyFrameSnapshot(
                    selection = TrustReplyFrameSelection(salutationSnippetId = 1L),
                    version = "old-version"
                )
            ))
        }

        assertEquals("TRUST_REPLY_FRAME_STALE", ex.code)
        assertEquals(HttpStatus.CONFLICT, ex.status)
    }

    @Test
    fun `bootstrap restores v4 state with its saved frame snapshot`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val currentEvidence = evidenceWithMapping("evidence-v1", canonicalKey(version) to listOf(9L))
        val perRequest = perRequestEvidence("evidence-v1", canonicalKey(version), listOf(9L))
        val locked = lockedAnswer(version, perRequest, 1, "What?")
        val storedSelection = ReplyFrameSelection(salutationSnippetId = 7L, ackSnippetId = 8L)
        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = version,
            evidenceSetVersion = currentEvidence,
            requestedFactIds = listOf(9L),
            requestFactSelections = canonicalMatrix(version),
            selectedModel = "DEEPSEEK_V4_FLASH",
            lockedItems = listOf(locked),
            frameSnapshot = TrustReplyFrameSnapshot(
                selection = TrustReplyFrameSelection(salutationSnippetId = 7L, ackSnippetId = 8L),
                version = "saved-v1"
            )
        )
        Mockito.`when`(stateStore.load("TRAINING_MAIL", 11L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(3L, LocalDateTime.now().plusDays(1), "{}")
        )
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(payload)
        Mockito.`when`(replySnippetService.resolveSelectableFrame(storedSelection)).thenReturn(resolvedFrame(
            selection = storedSelection,
            version = "saved-v1",
            salutation = "Saved Sal",
            acknowledgement = "Saved Ack"
        ))

        val restored = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))

        assertEquals("RESTORED", restored.savedState?.status)
        assertEquals(listOf(locked.requestKey), restored.savedState?.lockedItems?.map { it.requestKey })
        assertEquals("saved-v1", restored.frameSnapshot?.version)
        assertEquals(7L, restored.savedState?.frameSnapshot?.selection?.salutationSnippetId)
        assertEquals(7L, restored.frameSnapshot?.selection?.salutationSnippetId)
    }

    @Test
    fun `bootstrap frame stale restores locks and falls back to default frame`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val currentEvidence = evidenceWithMapping("evidence-v1", canonicalKey(version) to listOf(9L))
        val perRequest = perRequestEvidence("evidence-v1", canonicalKey(version), listOf(9L))
        val locked = lockedAnswer(version, perRequest, 1, "What?")
        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = version,
            evidenceSetVersion = currentEvidence,
            requestedFactIds = listOf(9L),
            requestFactSelections = canonicalMatrix(version),
            selectedModel = "DEEPSEEK_V4_FLASH",
            lockedItems = listOf(locked),
            frameSnapshot = TrustReplyFrameSnapshot(
                selection = TrustReplyFrameSelection(salutationSnippetId = 7L),
                version = "old-version"
            )
        )
        Mockito.`when`(stateStore.load("TRAINING_MAIL", 11L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(3L, LocalDateTime.now().plusDays(1), "{}")
        )
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(payload)
        Mockito.`when`(
            replySnippetService.resolveSelectableFrame(ReplyFrameSelection(salutationSnippetId = 7L))
        ).thenReturn(resolvedFrame(
            selection = ReplyFrameSelection(salutationSnippetId = 7L),
            version = "fresh-version",
            salutation = "Fresh Sal"
        ))

        val restored = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))

        assertEquals("FRAME_STALE", restored.savedState?.status)
        assertEquals(listOf(locked.requestKey), restored.savedState?.lockedItems?.map { it.requestKey })
        assertEquals("frame-default", restored.frameSnapshot?.version)
        assertEquals(1L, restored.frameSnapshot?.selection?.salutationSnippetId)
        assertEquals("frame-default", restored.savedState?.frameSnapshot?.version)
    }

    @Test
    fun `bootstrap marks v1 and v3 saved state stale with default frame fallback`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val currentEvidence = evidenceWithMapping("evidence-v1", canonicalKey(version) to listOf(9L))
        val perRequest = perRequestEvidence("evidence-v1", canonicalKey(version), listOf(9L))
        val locked = lockedAnswer(version, perRequest, 1, "What?")

        // v1 keeps its legacy flat-union normalization; its locked item still
        // carries the aggregate fingerprint, which per-request validation
        // drops, so the all-dropped snapshot is STALE (I-6).
        val v1Payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.LEGACY_SCHEMA_VERSION,
            sourceVersion = version,
            evidenceSetVersion = currentEvidence,
            requestedFactIds = listOf(9L),
            selectedModel = "DEEPSEEK_V4_FLASH",
            lockedItems = listOf(locked.copy(evidenceSetVersion = currentEvidence))
        )
        Mockito.`when`(stateStore.load("TRAINING_MAIL", 11L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(2L, LocalDateTime.now().plusDays(1), "{}")
        )
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(v1Payload)
        val v1Restored = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))
        assertEquals("STALE", v1Restored.savedState?.status)
        assertEquals("frame-default", v1Restored.frameSnapshot?.version)
        assert(v1Restored.savedState?.lockedItems?.isEmpty() == true)

        // v3 (PREVIOUS_SCHEMA_VERSION) is judged whole STALE without any
        // per-item comparison (I-6).
        val v3Payload = v1Payload.copy(
            schemaVersion = TrustReplyWorkbenchStateStore.PREVIOUS_SCHEMA_VERSION,
            requestFactSelections = canonicalMatrix(version)
        )
        Mockito.`when`(stateStore.decodePayload("{}")).thenReturn(v3Payload)
        val v3Restored = service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))
        assertEquals("STALE", v3Restored.savedState?.status)
        assertEquals("frame-default", v3Restored.frameSnapshot?.version)
        assert(v3Restored.savedState?.lockedItems?.isEmpty() == true)
    }

    @Test
    fun `saveState persists canonical frame snapshot with locked items`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val currentEvidence = evidenceWithMapping("evidence-v1", canonicalKey(version) to listOf(9L))
        val perRequest = perRequestEvidence("evidence-v1", canonicalKey(version), listOf(9L))
        val locked = lockedAnswer(version, perRequest, 1, "What?")
        val selection = ReplyFrameSelection(salutationSnippetId = 7L)
        Mockito.`when`(replySnippetService.resolveSelectableFrame(selection)).thenReturn(resolvedFrame(
            selection = selection,
            version = "saved-frame-v1",
            salutation = "Saved Sal"
        ))
        var capturedPayload: TrustReplySavedStatePayload? = null
        Mockito.doAnswer { invocation ->
            capturedPayload = invocation.arguments[0] as TrustReplySavedStatePayload
            "{}"
        }.`when`(stateStore).encodePayload(
            Mockito.any(TrustReplySavedStatePayload::class.java) ?: TrustReplySavedStatePayload(
                schemaVersion = "",
                sourceVersion = "",
                evidenceSetVersion = "",
                requestedFactIds = emptyList(),
                selectedModel = "",
                lockedItems = emptyList()
            )
        )
        Mockito.`when`(stateStore.save(Mockito.anyString() ?: "TRAINING_MAIL", Mockito.anyLong() ?: 11L, Mockito.anyLong() ?: 0L, Mockito.anyString() ?: "", Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now())).thenReturn(1L)

        val saved = service.saveState(TrustReplySaveStateRequest(
            source = TrustReplySourceRef(TRAINING_MAIL, 11L),
            expectedStateVersion = 0,
            sourceVersion = version,
            evidenceSetVersion = currentEvidence,
            lockedItems = listOf(locked),
            frameSnapshot = TrustReplyFrameSnapshot(
                selection = TrustReplyFrameSelection(salutationSnippetId = 7L),
                version = "saved-frame-v1"
            )
        ))

        assertEquals("SAVED", saved.status)
        assertEquals("saved-frame-v1", saved.frameSnapshot?.version)
        assertEquals(7L, saved.frameSnapshot?.selection?.salutationSnippetId)
        Mockito.verify(stateStore).encodePayload(
            Mockito.any(TrustReplySavedStatePayload::class.java) ?: TrustReplySavedStatePayload(
                schemaVersion = "",
                sourceVersion = "",
                evidenceSetVersion = "",
                requestedFactIds = emptyList(),
                selectedModel = "",
                lockedItems = emptyList()
            )
        )
        assertEquals("saved-frame-v1", requireNotNull(capturedPayload).frameSnapshot?.version)
        assertEquals(7L, requireNotNull(capturedPayload).frameSnapshot?.selection?.salutationSnippetId)
    }

    @Test
    fun `saveState rejects invalid frame selection before touching the store`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val currentEvidence = evidenceWithMapping("evidence-v1", canonicalKey(version) to listOf(9L))
        val perRequest = perRequestEvidence("evidence-v1", canonicalKey(version), listOf(9L))
        val locked = lockedAnswer(version, perRequest, 1, "What?")
        Mockito.`when`(
            replySnippetService.resolveSelectableFrame(
                Mockito.any(ReplyFrameSelection::class.java) ?: ReplyFrameSelection()
            )
        ).thenThrow(IllegalArgumentException("disabled"))

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.saveState(TrustReplySaveStateRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                expectedStateVersion = 0,
                sourceVersion = version,
                evidenceSetVersion = currentEvidence,
                lockedItems = listOf(locked),
                frameSnapshot = TrustReplyFrameSnapshot(
                    selection = TrustReplyFrameSelection(salutationSnippetId = 99L)
                )
            ))
        }

        assertEquals("TRUST_REPLY_FRAME_SELECTION_INVALID", ex.code)
        Mockito.verify(stateStore, Mockito.never()).save(
            Mockito.anyString(),
            Mockito.anyLong(),
            Mockito.anyLong(),
            Mockito.anyString(),
            Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()
        )
    }

    @Test
    fun `saveState rejects stale frame version with 409`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        val version = sourceVersion()
        val currentEvidence = evidenceWithMapping("evidence-v1", canonicalKey(version) to listOf(9L))
        val perRequest = perRequestEvidence("evidence-v1", canonicalKey(version), listOf(9L))
        val locked = lockedAnswer(version, perRequest, 1, "What?")
        Mockito.`when`(
            replySnippetService.resolveSelectableFrame(
                Mockito.any(ReplyFrameSelection::class.java) ?: ReplyFrameSelection()
            )
        ).thenReturn(resolvedFrame(
            selection = ReplyFrameSelection(salutationSnippetId = 7L),
            version = "fresh-version"
        ))

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            service.saveState(TrustReplySaveStateRequest(
                source = TrustReplySourceRef(TRAINING_MAIL, 11L),
                expectedStateVersion = 0,
                sourceVersion = version,
                evidenceSetVersion = currentEvidence,
                lockedItems = listOf(locked),
                frameSnapshot = TrustReplyFrameSnapshot(
                    selection = TrustReplyFrameSelection(salutationSnippetId = 7L),
                    version = "old-version"
                )
            ))
        }

        assertEquals("TRUST_REPLY_FRAME_STALE", ex.code)
        assertEquals(HttpStatus.CONFLICT, ex.status)
        Mockito.verify(stateStore, Mockito.never()).save(
            Mockito.anyString(),
            Mockito.anyLong(),
            Mockito.anyLong(),
            Mockito.anyString(),
            Mockito.any(LocalDateTime::class.java) ?: LocalDateTime.now()
        )
    }

    // P0 (I-4c/I-5): resetState deletes by (source_type, source_id) only and
    // never takes a version; deleteState keeps its version enforcement.
    @Test
    fun `resetState deletes the row by source without a version`() {
        Mockito.`when`(stateStore.deleteBySource("TRAINING_MAIL", 11L)).thenReturn(1)
        val response = service.resetState(TrustReplySourceRef(TRAINING_MAIL, 11L))
        assertEquals("DELETED", response.status)
        assertEquals(0L, response.stateVersion)
        Mockito.verify(stateStore).deleteBySource("TRAINING_MAIL", 11L)
        Mockito.verify(stateStore, Mockito.never()).delete(Mockito.anyString(), Mockito.anyLong(), Mockito.anyLong())
    }

    @Test
    fun `resetState never resolves the source`() {
        // 死锁场景下解析来信所需的联系人/画像可能不可用：任何 resolveSource
        // 尝试都会抛异常，resetState 必须完全不碰解析路径。
        Mockito.`when`(inboundProcessing.findById(99L)).thenThrow(RuntimeException("contact unavailable"))
        Mockito.`when`(stateStore.deleteBySource("LIVE_INBOUND", 99L)).thenReturn(1)
        val response = service.resetState(TrustReplySourceRef(LIVE_INBOUND, 99L))
        assertEquals("DELETED", response.status)
        Mockito.verify(stateStore).deleteBySource("LIVE_INBOUND", 99L)
        Mockito.verify(inboundProcessing, Mockito.never()).findById(Mockito.anyLong())
    }

    @Test
    fun `deleteState still enforces the expected version`() {
        val exact = mail(id = 11L, body = "What?")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))
        Mockito.`when`(stateStore.delete("TRAINING_MAIL", 11L, 5L)).thenReturn(true)
        val response = service.deleteState(TrustReplySourceRef(TRAINING_MAIL, 11L), 5L)
        assertEquals("DELETED", response.status)
        Mockito.verify(stateStore).delete("TRAINING_MAIL", 11L, 5L)
        Mockito.verify(stateStore, Mockito.never()).deleteBySource(Mockito.anyString(), Mockito.anyLong())
    }

    // ── c5 / 15-workbench-three-step（T-6.4 / T-6.5）─────────────────────────────

    private fun stubTwoRequestSource() {
        val exact = mail(id = 11L, body = "What?\nWhen?")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))
        val selected = selection(
            item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED),
            item(2, "When?", listOf(10L), RequestGroundingStatus.GROUNDED)
        )
        Mockito.`when`(factSelection.selectForWorkbench("What?\nWhen?", listOf(listOf(9L), listOf(10L)), null, true))
            .thenReturn(selected)
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(9L, 10L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(9L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(10L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(emptyList()))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        Mockito.`when`(qaRules.findAllEnabledOrdered()).thenReturn(listOf(
            QaRule(id = 9L, categoryId = 3L, keywords = "what", replySubject = null, replyBody = "", answerBody = "answer", displayName = "What"),
            QaRule(id = 10L, categoryId = 3L, keywords = "when", replySubject = null, replyBody = "", answerBody = "second", displayName = "When")
        ))
    }

    // T-6.4: 重排端点对 pinned 段落原样回填（携带条目级 evidenceSetVersion）；
    // 未锁定段落重新编排；pinned 段落不进编排计划。
    @Test
    fun `rearrange fills pinned paragraphs verbatim and re-orchestrates the rest`() {
        stubTwoRequestSource()
        val version = sourceVersion()
        val key1 = canonicalKey(version)
        val key2 = canonicalKey(version, 2, "When?")
        val perRequest1 = perRequestEvidence("evidence-v1", key1, listOf(9L))
        val perRequest2 = perRequestEvidence("evidence-v1", key2, listOf(10L))
        val source = TrustReplySourceRef(TRAINING_MAIL, 11L)

        val request = TrustReplyRearrangeRequest(
            source = source,
            expectedSourceVersion = version,
            requestFactSelections = listOf(
                TrustReplyRequestFactSelection(key1, listOf(9L)),
                TrustReplyRequestFactSelection(key2, listOf(10L))
            ),
            paragraphPlanDraft = listOf(
                ParagraphPlanEntry("enterprise", listOf("f9")),
                ParagraphPlanEntry("review", listOf("f10"))
            ),
            // I-3: pinned 段落携带条目级 evidenceSetVersion（主属 key2），非全信标量。
            pinnedParagraphs = listOf(
                TrustReplyPinnedParagraphRequest("review", listOf("f10"), "LOCKED REVIEW TEXT", perRequest2)
            )
        )
        var orchestratedFacts: List<PlanFact>? = null
        var orchestratedPlan: List<ParagraphPlanEntry>? = null
        val attempt = OrchestrationAttempt { facts, plan, _, _ ->
            orchestratedFacts = facts
            orchestratedPlan = plan
            OrchestratedLetter(
                paragraphs = listOf(OrchestratedParagraph("enterprise", listOf("f9"), "fresh enterprise text")),
                actionText = null
            )
        }

        val response = service.rearrangeInternal(request, attempt)

        // 未锁定段重新编排；pinned 段原样回填。
        assertEquals(
            listOf(
                OrchestratedParagraph("enterprise", listOf("f9"), "fresh enterprise text"),
                OrchestratedParagraph("review", listOf("f10"), "LOCKED REVIEW TEXT")
            ),
            response.paragraphs
        )
        // pinned 段落不进编排计划与事实集。
        assertEquals(listOf(ParagraphPlanEntry("enterprise", listOf("f9"))), orchestratedPlan)
        assertEquals(listOf("f9"), orchestratedFacts?.map { it.id })
        // 响应带服务端规范化协议 + 六道校验全过（空码）。
        assertEquals(listOf("enterprise", "review"), response.topicOrder)
        assertEquals(2, response.paragraphPlan.size)
        assertTrue(response.validationCodes.isEmpty())
        // 事实列表含 f9/f10 及其 QA 正文（I-5：前端不拼装正文）。
        val factsById = response.facts.associateBy { it.id }
        assertEquals("answer", factsById["f9"]?.body)
        assertEquals("second", factsById["f10"]?.body)
    }

    // T-6.5: 运营事实 op* 注入后走 13 的第 3 道校验（逐字）——改一个字即校验失败。
    // 分两段断言：服务端重排响应的六道校验结果（重排路径的逐字再验证），以及
    // 真实编排器（AiReplyLetterOrchestrator）对同一 op 事实的 G3 逐字拒绝。
    @Test
    fun `rearrange applies op fact verbatim validation - one word change fails`() {
        stubCanonicalSource(listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)))
        Mockito.`when`(qaRules.findAllEnabledOrdered()).thenReturn(listOf(
            QaRule(id = 9L, categoryId = 3L, keywords = "what", replySubject = null, replyBody = "", answerBody = "answer", displayName = "What")
        ))
        val version = sourceVersion()
        val source = TrustReplySourceRef(TRAINING_MAIL, 11L)

        val opFact = PlanFact(
            id = "op1",
            topic = "enterprise",
            body = "operator fixed text",
            controlled = null,
            frozen = true,
            required = true
        )
        val request = TrustReplyRearrangeRequest(
            source = source,
            expectedSourceVersion = version,
            requestFactSelections = canonicalMatrix(version),
            paragraphPlanDraft = listOf(
                ParagraphPlanEntry("enterprise", listOf("f9", "op1"))
            ),
            operatorFacts = listOf(opFact)
        )

        // Part A: 桩编排返回改了一个字的 op 正文 → 重排响应的六道校验结果必须命中
        // ORCH_VERBATIM_BODY_MISSING（I-2：与受控事实同一逐字校验）。
        var capturedFacts: List<PlanFact>? = null
        var capturedPlan: List<ParagraphPlanEntry>? = null
        val stubAttempt = OrchestrationAttempt { facts, plan, _, _ ->
            capturedFacts = facts
            capturedPlan = plan
            OrchestratedLetter(
                paragraphs = listOf(
                    OrchestratedParagraph("enterprise", listOf("f9", "op1"), "answer operator fixed wording")
                ),
                actionText = null
            )
        }
        val response = service.rearrangeInternal(request, stubAttempt)
        assertTrue(
            response.validationCodes.contains(AiReplyValidationCodes.ORCH_VERBATIM_BODY_MISSING),
            "one-word change of the op fact body must fail the verbatim check"
        )
        // op 事实以逐字插槽进入编排输入（frozen + required，I-2）。
        val opInFacts = requireNotNull(capturedFacts).first { it.id == "op1" }
        assertTrue(opInFacts.frozen)
        assertTrue(opInFacts.required)
        assertEquals("operator fixed text", opInFacts.body)

        // Part B: 真实 13 编排器对同一 op 事实的 G3 逐字拒绝——改一个字 → orchestrate
        // 返回 null（初始 + 修复两轮均未通过）。
        val badResponse = ObjectMapper().writeValueAsString(
            mapOf(
                "paragraphs" to listOf(
                    mapOf(
                        "topic" to "enterprise",
                        "factIds" to listOf("f9", "op1"),
                        "text" to "answer operator fixed wording"
                    )
                ),
                "actionText" to null
            )
        )
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObservedStream(
                messages: List<LlmChatMessage>,
                temperature: Double?,
                providerModel: String,
                timeoutMillis: Long,
                jsonOutput: Boolean,
                cancellationToken: AiReplyCancellationToken,
                progressSink: LlmStreamProgressSink
            ): LlmChatResult = LlmChatResult(badResponse)
        }
        val providerMock = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<LlmDraftClient>
        Mockito.`when`(providerMock.getIfAvailable()).thenReturn(client)
        val orchestrator = AiReplyLetterOrchestrator(
            properties = LlmProperties(enabled = true, apiUrl = "http://llm.test", temperature = 0.3),
            llmDraftClientProvider = providerMock,
            objectMapper = ObjectMapper()
        )
        val letter = orchestrator.orchestrate(
            facts = requireNotNull(capturedFacts),
            plan = requireNotNull(capturedPlan),
            topicOrder = requireNotNull(capturedPlan).map { it.topic },
            allowedActions = emptySet()
        )
        assertNull(letter, "13's G3 must reject the one-word-changed op fact")

        // 逐字未改时 13 的 G3 放行。
        val validResponse = ObjectMapper().writeValueAsString(
            mapOf(
                "paragraphs" to listOf(
                    mapOf(
                        "topic" to "enterprise",
                        "factIds" to listOf("f9", "op1"),
                        "text" to "answer operator fixed text"
                    )
                ),
                "actionText" to null
            )
        )
        val validClient = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObservedStream(
                messages: List<LlmChatMessage>,
                temperature: Double?,
                providerModel: String,
                timeoutMillis: Long,
                jsonOutput: Boolean,
                cancellationToken: AiReplyCancellationToken,
                progressSink: LlmStreamProgressSink
            ): LlmChatResult = LlmChatResult(validResponse)
        }
        Mockito.`when`(providerMock.getIfAvailable()).thenReturn(validClient)
        val passing = orchestrator.orchestrate(
            facts = requireNotNull(capturedFacts),
            plan = requireNotNull(capturedPlan),
            topicOrder = requireNotNull(capturedPlan).map { it.topic },
            allowedActions = emptySet()
        )
        assertNotNull(passing)
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
