package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.service.ReplyFrameSelection
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import com.weibo.talentintroduction.reply.service.ResolvedReplyFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

class TrustReplyWorkbenchItemFlowTest {
    @Test
    fun `operator directed handling has canonical version fields`() {
        val operatorHandling = operatorDirectedHandling()
        assertEquals(
            setOf(
                operatorHandling,
                TrustReplyItemHandling.ACKNOWLEDGE_PENDING,
                TrustReplyItemHandling.OMIT
            ),
            TrustReplyWorkbenchService.allowedHandlings(RequestGroundingStatus.UNSUPPORTED).toSet()
        )

        val fixture = assembleFixture(
            status = RequestGroundingStatus.UNSUPPORTED,
            handling = operatorHandling,
            answerText = "We work with the named institutions.",
            claims = emptyList()
        )
        val version = fixture.service.assemble(fixture.request).itemVersions.single()
        assertEquals(1, version.requestIndex)
        assertEquals("What?", version.requestText)
        assertEquals("Use the operator-provided basis.", version.operatorInstruction)
    }

    @Test
    fun `operator directed handling on grounded item rejects before instruction validation`() {
        val fixture = assembleFixture(status = RequestGroundingStatus.GROUNDED)
        val error = assertThrows(TrustReplyWorkbenchException::class.java) {
            fixture.service.adjustItem(
                TrustReplyItemAdjustmentRequest(
                    source = fixture.request.source,
                    expectedSourceVersion = fixture.request.expectedSourceVersion,
                    expectedEvidenceSetVersion = fixture.request.expectedEvidenceSetVersion,
                    requestKey = fixture.validLockedItem.requestKey,
                    handling = operatorDirectedHandling(),
                    operatorInstruction = null
                )
            )
        }

        assertEquals("TRUST_REPLY_HANDLING_INVALID", error.code)
    }

    @Test
    fun `assembled operator directed answer cannot bypass action policy`() {
        val fixture = assembleFixture(
            status = RequestGroundingStatus.UNSUPPORTED,
            handling = operatorDirectedHandling(),
            answerText = "Please send your CV.",
            claims = emptyList()
        )

        val error = assertThrows(TrustReplyWorkbenchException::class.java) {
            fixture.service.assemble(fixture.request)
        }

        assertEquals("TRUST_REPLY_CLAIM_INVALID", error.code)
    }

    @Test
    fun `unsupported operator directed handling rejects incomplete canonical input`() {
        val operatorHandling = operatorDirectedHandling()
        val fixture = assembleFixture(
            status = RequestGroundingStatus.UNSUPPORTED,
            handling = operatorHandling,
            answerText = "We work with the named institutions.",
            claims = emptyList()
        )
        val version = fixture.validLockedItem
        fun code(item: TrustReplyLockedItemRequest): String = assertThrows(TrustReplyWorkbenchException::class.java) {
            fixture.service.assemble(fixture.request.copy(lockedItems = listOf(item)))
        }.code

        assertEquals("TRUST_REPLY_LOCKED_ITEM_INVALID", code(version.copy(answerText = "")))
        assertEquals("TRUST_REPLY_LOCKED_ITEM_INVALID", code(version.copy(claims = listOf(
            AiReplyItemClaim("general.answer", "unexpected claim", listOf(9L))
        ))))
        assertEquals("TRUST_REPLY_LOCKED_ITEM_INVALID", code(version.copy(operatorInstructionHash = "wrong")))
        assertEquals("TRUST_REPLY_LOCKED_ITEM_INVALID", code(version.copy(
            generationKind = TrustReplyItemGenerationKind.SAFE_TEMPLATE
        )))
        assertEquals("TRUST_REPLY_ITEM_VERSION_INVALID", code(version.copy(
            versionId = "tampered",
            answerText = "A different answer."
        )))
    }

    @Test
    fun `request key is deterministic and changes with every identity component`() {
        val first = TrustReplyWorkbenchService.requestKey("source-v1", 1, "  What is the fee? ", listOf("fees.amount"))
        assertEquals(first, TrustReplyWorkbenchService.requestKey("source-v1", 1, "What is the fee?", listOf("fees.amount")))
        assertEquals(32, first.length)
        assertNotEquals(first, TrustReplyWorkbenchService.requestKey("source-v2", 1, "What is the fee?", listOf("fees.amount")))
        assertNotEquals(first, TrustReplyWorkbenchService.requestKey("source-v1", 2, "What is the fee?", listOf("fees.amount")))
        assertNotEquals(first, TrustReplyWorkbenchService.requestKey("source-v1", 1, "What is the salary?", listOf("fees.amount")))
        assertNotEquals(first, TrustReplyWorkbenchService.requestKey("source-v1", 1, "What is the fee?", listOf("fees.other")))
    }

    @Test
    fun `handling matrix is fail closed`() {
        assertEquals(
            setOf(TrustReplyItemHandling.ANSWER_WITH_EVIDENCE, TrustReplyItemHandling.OMIT),
            TrustReplyWorkbenchService.allowedHandlings(RequestGroundingStatus.GROUNDED).toSet()
        )
        assertEquals(
            setOf(
                TrustReplyItemHandling.ANSWER_SUPPORTED_PART,
                TrustReplyItemHandling.ACKNOWLEDGE_PENDING,
                TrustReplyItemHandling.OMIT
            ),
            TrustReplyWorkbenchService.allowedHandlings(RequestGroundingStatus.PARTIAL).toSet()
        )
        assertEquals(
            setOf(
                TrustReplyItemHandling.valueOf("ANSWER_FROM_OPERATOR_INPUT"),
                TrustReplyItemHandling.ACKNOWLEDGE_PENDING,
                TrustReplyItemHandling.OMIT
            ),
            TrustReplyWorkbenchService.allowedHandlings(RequestGroundingStatus.UNSUPPORTED).toSet()
        )
        assertEquals(
            TrustReplyItemHandling.valueOf("ANSWER_FROM_OPERATOR_INPUT"),
            TrustReplyWorkbenchService.recommendedHandling(RequestGroundingStatus.UNSUPPORTED)
        )
        RequestGroundingStatus.values().forEach { status ->
            TrustReplyItemHandling.values()
                .filterNot { it in TrustReplyWorkbenchService.allowedHandlings(status) }
                .forEach { handling ->
                    assertThrows(IllegalArgumentException::class.java) {
                        TrustReplyWorkbenchService.requireAllowedHandling(status, handling)
                    }
                }
        }
    }

    @Test
    fun `version id changes by instruction and is repeatable`() {
        val base = TrustReplyWorkbenchService.versionId(
            requestKey = "a".repeat(32),
            handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
            answerText = "Answer",
            claims = listOf(AiReplyItemClaim("general.answer", "Answer", listOf(1L))),
            model = "DEEPSEEK_V4_FLASH",
            generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
            evidenceSetVersion = "e1",
            sourceVersion = "s1",
            operatorInstructionHash = "i1"
        )
        assertEquals(base, TrustReplyWorkbenchService.versionId(
            requestKey = "a".repeat(32),
            handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
            answerText = "Answer",
            claims = listOf(AiReplyItemClaim("general.answer", "Answer", listOf(1L))),
            model = "DEEPSEEK_V4_FLASH",
            generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
            evidenceSetVersion = "e1",
            sourceVersion = "s1",
            operatorInstructionHash = "i1"
        ))
        assertNotEquals(base, TrustReplyWorkbenchService.versionId(
            requestKey = "a".repeat(32),
            handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
            answerText = "Answer",
            claims = listOf(AiReplyItemClaim("general.answer", "Answer", listOf(1L))),
            model = "DEEPSEEK_V4_FLASH",
            generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
            evidenceSetVersion = "e1",
            sourceVersion = "s1",
            operatorInstructionHash = "i2"
        ))
    }

    @Test
    fun `adjust item forwards coordinator token reporter and commit guard`() {
        val mailRecords = Mockito.mock(MailRecordRepository::class.java)
        val inboundProcessing = Mockito.mock(InboundMailProcessingRepository::class.java)
        val contacts = Mockito.mock(ExpertContactRepository::class.java)
        val trainingQa = Mockito.mock(AiTrainingQaService::class.java)
        val contextService = Mockito.mock(AiReplyContextService::class.java)
        val factSelection = Mockito.mock(QaFactSelectionService::class.java)
        val qaRules = Mockito.mock(QaRuleRepository::class.java)
        val draftService = Mockito.mock(AiReplyDraftService::class.java)
        val previewService = Mockito.mock(AiReplyDraftPreviewService::class.java)
        val auditService = Mockito.mock(AiReplyReviewAuditService::class.java)
        val composer = Mockito.mock(AiReplyPointByPointComposer::class.java)
        val replySnippetService = Mockito.mock(ReplySnippetService::class.java)
        val claimValidator = Mockito.mock(AiReplyHighRiskClaimValidator::class.java)
        val stateStore = Mockito.mock(TrustReplyWorkbenchStateStore::class.java)
        val contact = ExpertContact(
            id = 7L,
            campaignId = 1L,
            orcidId = "0000-0000",
            expertEmail = "test@example.com",
            expertName = "Test"
        )
        val mail = MailRecord(
            id = 11L,
            expertContactId = 7L,
            direction = "INBOUND",
            mailType = "REPLY",
            senderAccountCode = null,
            messageId = "<11@example.com>",
            inReplyTo = null,
            subject = "Subject",
            body = "What?",
            cleanedBody = null,
            matchedQaRuleId = null,
            sendStatus = null,
            receivedAt = LocalDateTime.of(2026, 7, 28, 10, 0),
            sentAt = null
        )
        val item = item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)
        val selection = ResolvedQaRules(
            sendQaRuleIds = listOf(9L),
            promptRuleIds = listOf(9L),
            requestFacts = listOf(item),
            requestCount = 1,
            groundedRequestCount = 1
        )
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(mail))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(mail))
        Mockito.`when`(trainingQa.buildKnowledgeContext("What?")).thenReturn("")
        Mockito.`when`(
            contextService.build(
                Mockito.any(ExpertContact::class.java) ?: contact,
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
        Mockito.`when`(factSelection.selectForWorkbench("What?", null, null, true)).thenReturn(selection)
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(9L)))
            .thenReturn(Triple("e1", emptyList(), emptyList()))

        val service = TrustReplyWorkbenchService(
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
            aiReplyPointByPointComposer = composer,
            claimValidator = claimValidator,
            stateStore = stateStore,
            replySnippetService = replySnippetService
        )
        val source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 11L)
        val sourceVersion = service.resolveSource(source).sourceVersion
        val key = TrustReplyWorkbenchService.requestKey(sourceVersion, 1, "What?", AiReplyIntentCatalog.matchIntents("What?").map { it.key })
        val currentEvidence = AiReplyDraftService.sha256Hex("e1\u0000$key\u00009")
        val token = AiReplyCancellationToken()
        val reporter = Mockito.mock(AiReplyProgressReporter::class.java)
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
        Mockito.`when`(
            draftService.generateItem(
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
                cancellationToken = token,
                progressReporter = reporter
            )
        ).thenReturn(generated)

        val generationRequest = TrustReplyGenerationRequest(
                source = source,
                expectedSourceVersion = sourceVersion,
                operation = "ADJUST_ITEM",
                expectedEvidenceSetVersion = currentEvidence,
                requestKey = key,
                handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE
            )
        val result = service.generate(
            request = generationRequest,
            cancellationToken = token,
            progressReporter = reporter,
            beforeCommit = { true }
        )

        assertEquals("Salary info", result.draftText)
        Mockito.verify(draftService).generateItem(
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
            cancellationToken = token,
            progressReporter = reporter
        )
        assertThrows(AiReplyGenerationCancelledException::class.java) {
            service.generate(
                request = generationRequest,
                cancellationToken = token,
                progressReporter = reporter,
                beforeCommit = { false }
            )
        }
    }

    @Test
    fun `adjust item materializes OMIT version without draft generation and assembles it`() {
        val fixture = assembleFixture()
        val ignoredInstruction = "Ignore this instruction."
        Mockito.clearInvocations(fixture.draftService)

        val result = fixture.service.adjustItem(
            TrustReplyItemAdjustmentRequest(
                source = fixture.request.source,
                expectedSourceVersion = fixture.request.expectedSourceVersion,
                expectedEvidenceSetVersion = fixture.request.expectedEvidenceSetVersion,
                requestKey = fixture.validLockedItem.requestKey,
                handling = TrustReplyItemHandling.OMIT,
                operatorInstruction = ignoredInstruction
            )
        )

        assertEquals(TrustReplyItemGenerationKind.OMITTED, result.version.generationKind)
        assertEquals("", result.version.answerText)
        assertTrue(result.version.claims.isEmpty())
        assertEquals("", result.version.operatorInstruction)
        assertEquals(AiReplyDraftService.sha256Hex(""), result.version.operatorInstructionHash)
        assertFalse(Mockito.mockingDetails(fixture.draftService).invocations.any { it.method.name == "generateItem" })
        assertEquals(
            TrustReplyWorkbenchService.versionId(
                requestKey = fixture.validLockedItem.requestKey,
                handling = TrustReplyItemHandling.OMIT,
                answerText = "",
                claims = emptyList(),
                model = "DEEPSEEK_V4_FLASH",
                generationKind = TrustReplyItemGenerationKind.OMITTED,
                evidenceSetVersion = fixture.request.expectedEvidenceSetVersion,
                sourceVersion = fixture.request.expectedSourceVersion,
                operatorInstructionHash = AiReplyDraftService.sha256Hex("")
            ),
            result.version.versionId
        )
        Mockito.verifyNoInteractions(fixture.draftService)

        val lockedOmit = TrustReplyLockedItemRequest(
            requestKey = result.version.requestKey,
            versionId = result.version.versionId,
            handling = result.version.handling,
            answerText = result.version.answerText,
            claims = result.version.claims,
            model = result.version.model,
            generationKind = result.version.generationKind,
            evidenceSetVersion = result.version.evidenceSetVersion,
            sourceVersion = result.version.sourceVersion,
            operatorInstructionHash = AiReplyDraftService.sha256Hex(ignoredInstruction),
            operatorInstruction = ignoredInstruction
        )
        val assembled = fixture.service.assemble(fixture.request.copy(lockedItems = listOf(lockedOmit)))
        val assembledVersion = assembled.itemVersions.single()
        assertEquals("", assembledVersion.operatorInstruction)
        assertEquals(AiReplyDraftService.sha256Hex(""), assembledVersion.operatorInstructionHash)
        assertEquals(result.version.versionId, assembledVersion.versionId)
        assertEquals("", assembled.rawDraftText)
    }

    @Test
    fun `full draft does not create an unsupported initial version`() {
        val fixture = assembleFixture(status = RequestGroundingStatus.UNSUPPORTED)
        val result = AiReplyDraftResult(
            draftText = "fallback",
            usedLlm = false,
            qaRuleIds = listOf(9L),
            mode = AiReplyMode.QA_GROUNDED,
            requestFacts = listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.UNSUPPORTED)),
            generationState = AiReplyGenerationState.FALLBACK_NO_RESPONSE,
            draftReadiness = AiReplyDraftReadiness.BLOCKED,
            evidenceSetVersion = "e1",
            itemAnswers = emptyList()
        )
        Mockito.`when`(
            fixture.draftService.generate(
                inboundText = "What?",
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
        Mockito.`when`(fixture.previewService.preview("fallback", fixture.contact, null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("fallback", emptyList()))

        val generated = fixture.service.generate(
            TrustReplyGenerationRequest(
                source = fixture.request.source,
                expectedSourceVersion = fixture.request.expectedSourceVersion
            )
        )

        assertTrue(generated.itemVersions.isEmpty())
    }

    @Test
    fun `operator directed adjustment requires bounded nonblank instruction`() {
        val handling = operatorDirectedHandling()
        val fixture = assembleFixture(status = RequestGroundingStatus.UNSUPPORTED, handling = handling)
        fun code(instruction: String?): String = assertThrows(TrustReplyWorkbenchException::class.java) {
            fixture.service.adjustItem(
                TrustReplyItemAdjustmentRequest(
                    source = fixture.request.source,
                    expectedSourceVersion = fixture.request.expectedSourceVersion,
                    expectedEvidenceSetVersion = fixture.request.expectedEvidenceSetVersion,
                    requestKey = fixture.validLockedItem.requestKey,
                    handling = handling,
                    operatorInstruction = instruction
                )
            )
        }.code

        assertEquals("TRUST_REPLY_OPERATOR_INSTRUCTION_INVALID", code(null))
        assertEquals("TRUST_REPLY_OPERATOR_INSTRUCTION_INVALID", code("x".repeat(501)))
        assertFalse(Mockito.mockingDetails(fixture.draftService).invocations.any { it.method.name == "generateItem" })
    }

    @Test
    fun `assemble accepts complete locked set and returns raw rendered hash without side effects`() {
        val fixture = assembleFixture()
        val response = fixture.service.assemble(fixture.request)

        assertEquals("raw Salary info", response.rawDraftText)
        assertEquals("rendered Salary info", response.renderedDraftText)
        assertEquals(AiReplyDraftService.sha256Hex("raw Salary info"), response.draftHash)
        assertEquals(listOf(9L), response.canonicalFactIds)
        assertEquals(listOf(9L), response.requestedFactIds)
        assertEquals(fixture.validLockedItem.versionId, response.itemVersions.single().versionId)
        Mockito.verifyNoInteractions(fixture.auditService)
    }

    @Test
    fun `assemble accepts matrix input and returns the canonical matrix`() {
        val fixture = assembleFixture()
        Mockito.`when`(fixture.factSelection.selectForWorkbench("What?", listOf(listOf(9L)), null, true))
            .thenReturn(ResolvedQaRules(
                sendQaRuleIds = listOf(9L),
                promptRuleIds = listOf(9L),
                requestFacts = listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)),
                requestCount = 1,
                groundedRequestCount = 1
            ))
        val matrix = listOf(TrustReplyRequestFactSelection(fixture.validLockedItem.requestKey, listOf(9L)))

        val response = fixture.service.assemble(
            fixture.request.copy(requestFactSelections = matrix)
        )

        assertEquals(matrix, response.requestFactSelections)
        assertEquals(listOf(9L), response.canonicalFactIds)
    }

    @Test
    fun `assemble rejects tampered flat union that no longer resolves to the matrix`() {
        val fixture = assembleFixture()
        Mockito.`when`(fixture.factSelection.selectForWorkbench("What?", null, listOf(99L), true))
            .thenThrow(TrustReplyWorkbenchException(
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                "TRUST_REPLY_FACT_SELECTION_INVALID"
            ))

        val error = assertThrows(TrustReplyWorkbenchException::class.java) {
            fixture.service.assemble(fixture.request.copy(requestedFactIds = listOf(99L)))
        }

        assertEquals("TRUST_REPLY_FACT_SELECTION_INVALID", error.code)
        Mockito.verifyNoInteractions(fixture.composer)
        Mockito.verifyNoInteractions(fixture.previewService)
    }

    @Test
    fun `assemble rejects ambiguous matrix and legacy input`() {
        val fixture = assembleFixture()

        val error = assertThrows(TrustReplyWorkbenchException::class.java) {
            fixture.service.assemble(
                fixture.request.copy(
                    requestedFactIds = listOf(9L),
                    requestFactSelections = listOf(
                        TrustReplyRequestFactSelection(fixture.validLockedItem.requestKey, listOf(9L))
                    )
                )
            )
        }

        assertEquals("TRUST_REPLY_FACT_SELECTION_AMBIGUOUS", error.code)
        Mockito.verifyNoInteractions(fixture.composer)
    }

    @Test
    fun `assemble rejects stale source evidence incomplete duplicate unknown and tampered locks`() {
        val fixture = assembleFixture()
        val base = fixture.request
        fun code(request: TrustReplyAssembleRequest): String = assertThrows(TrustReplyWorkbenchException::class.java) {
            fixture.service.assemble(request)
        }.code

        assertEquals("TRUST_REPLY_SOURCE_STALE", code(base.copy(expectedSourceVersion = "stale")))
        assertEquals("TRUST_REPLY_EVIDENCE_STALE", code(base.copy(expectedEvidenceSetVersion = "stale")))
        assertEquals("TRUST_REPLY_LOCKED_ITEMS_INCOMPLETE", code(base.copy(lockedItems = emptyList())))
        assertEquals(
            "TRUST_REPLY_LOCKED_ITEMS_INCOMPLETE",
            code(base.copy(lockedItems = listOf(fixture.validLockedItem, fixture.validLockedItem)))
        )
        assertEquals(
            "TRUST_REPLY_LOCKED_ITEMS_INCOMPLETE",
            code(base.copy(lockedItems = listOf(
                fixture.validLockedItem,
                fixture.validLockedItem.copy(requestKey = "unknown")
            )))
        )
        assertEquals(
            "TRUST_REPLY_CLAIMS_INVALID",
            code(base.copy(lockedItems = listOf(fixture.validLockedItem.copy(
                claims = listOf(AiReplyItemClaim("general.answer", "Salary info", listOf(99L)))
            ))))
        )
        assertEquals(
            "TRUST_REPLY_ANSWER_CLAIMS_MISMATCH",
            code(base.copy(lockedItems = listOf(fixture.validLockedItem.copy(answerText = "Other"))))
        )
        assertEquals(
            "TRUST_REPLY_ITEM_VERSION_INVALID",
            code(base.copy(lockedItems = listOf(fixture.validLockedItem.copy(versionId = "tampered"))))
        )
    }

    @Test
    fun `assemble rejects rehashed CTA claim before compose preview and audit`() {
        val fixture = assembleFixture()
        val claims = listOf(AiReplyItemClaim("general.answer", "Please send your CV", listOf(9L)))
        val versionId = TrustReplyWorkbenchService.versionId(
            requestKey = fixture.validLockedItem.requestKey,
            handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
            answerText = "Please send your CV",
            claims = claims,
            model = fixture.validLockedItem.model,
            generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
            evidenceSetVersion = fixture.validLockedItem.evidenceSetVersion,
            sourceVersion = fixture.validLockedItem.sourceVersion,
            operatorInstructionHash = fixture.validLockedItem.operatorInstructionHash
        )

        val error = assertThrows(TrustReplyWorkbenchException::class.java) {
            fixture.service.assemble(
                fixture.request.copy(
                    lockedItems = listOf(
                        fixture.validLockedItem.copy(
                            versionId = versionId,
                            answerText = "Please send your CV",
                            claims = claims
                        )
                    )
                )
            )
        }

        assertEquals("TRUST_REPLY_CLAIM_INVALID", error.code)
        Mockito.verifyNoInteractions(fixture.composer)
        Mockito.verifyNoInteractions(fixture.previewService)
        Mockito.verifyNoInteractions(fixture.auditService)
    }

    @Test
    fun `assemble composes canonical ACK answer after version verification`() {
        val canonical = AiReplyHighRiskClaimValidator.safeAcknowledgementFor("What?")
        val fixture = assembleFixture(
            status = RequestGroundingStatus.UNSUPPORTED,
            handling = TrustReplyItemHandling.ACKNOWLEDGE_PENDING,
            answerText = canonical,
            claims = emptyList(),
            generationKind = TrustReplyItemGenerationKind.SAFE_TEMPLATE
        )
        val padded = "  $canonical  "
        Mockito.`when`(fixture.composer.composeLockedItems(listOf(padded), fixture.defaultFrame))
            .thenReturn("raw $padded")
        Mockito.`when`(fixture.previewService.preview("raw $padded", fixture.contact, null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("rendered $padded", emptyList()))

        val response = fixture.service.assemble(
            fixture.request.copy(lockedItems = listOf(fixture.validLockedItem.copy(answerText = padded)))
        )

        assertEquals("raw $canonical", response.rawDraftText)
        Mockito.verify(fixture.composer).composeLockedItems(listOf(canonical), fixture.defaultFrame)
    }

    // ── I-7/I-8: cross-request duplicate claim guard before compose ────────────

    @Test
    fun `assemble rejects the same source rule bound to two requests as already assigned`() {
        val fixture = duplicateFixture(item2Source = 9L, item2Text = "Claim B")

        val error = assertThrows(TrustReplyWorkbenchException::class.java) {
            fixture.service.assemble(fixture.request)
        }

        assertEquals("TRUST_REPLY_FACT_ALREADY_ASSIGNED", error.code)
        Mockito.verifyNoInteractions(fixture.composer)
        Mockito.verifyNoInteractions(fixture.previewService)
    }

    @Test
    fun `assemble rejects identical normalized answers across requests`() {
        val fixture = duplicateFixture(item1Text = "Same  claim", item2Text = "Same CLAIM")

        val error = assertThrows(TrustReplyWorkbenchException::class.java) {
            fixture.service.assemble(fixture.request)
        }

        assertEquals("TRUST_REPLY_DUPLICATE_CLAIM", error.code)
        Mockito.verifyNoInteractions(fixture.composer)
    }

    @Test
    fun `assemble keeps similar answers from different claims in canonical order`() {
        val fixture = duplicateFixture()

        val response = fixture.service.assemble(fixture.request)

        assertEquals("raw Claim A|Claim B", response.rawDraftText)
        assertEquals(listOf(9L, 10L), response.canonicalFactIds)
        assertEquals(listOf("Claim A", "Claim B"), response.itemVersions.map { it.answerText })
    }


    // ── 02 selectable frame assembly (I-2/I-3/I-4/I-5) ────────────────────

    @Test
    fun `frame switch changes assembly but never locked item identity`() {
        val fixture = assembleFixture()
        val selectionA = ReplyFrameSelection(salutationSnippetId = 1L)
        val selectionB = ReplyFrameSelection(salutationSnippetId = 2L)
        val frameA = resolvedFrame(selection = selectionA, version = "frame-A", salutation = "Sal A", closing = "Close A")
        val frameB = resolvedFrame(selection = selectionB, version = "frame-B", salutation = "Sal B", closing = "Close B")
        Mockito.`when`(fixture.replySnippetService.resolveSelectableFrame(selectionA)).thenReturn(frameA)
        Mockito.`when`(fixture.replySnippetService.resolveSelectableFrame(selectionB)).thenReturn(frameB)
        Mockito.`when`(fixture.composer.composeLockedItems(listOf("Salary info"), frameA)).thenReturn("raw A")
        Mockito.`when`(fixture.composer.composeLockedItems(listOf("Salary info"), frameB)).thenReturn("raw B")
        Mockito.`when`(fixture.previewService.preview("raw A", fixture.contact, null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("rendered A", emptyList()))
        Mockito.`when`(fixture.previewService.preview("raw B", fixture.contact, null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("rendered B", emptyList()))

        val respA = fixture.service.assemble(fixture.request.copy(
            frameSnapshot = TrustReplyFrameSnapshot(
                selection = TrustReplyFrameSelection(salutationSnippetId = 1L),
                version = "frame-A"
            )
        ))
        val respB = fixture.service.assemble(fixture.request.copy(
            frameSnapshot = TrustReplyFrameSnapshot(
                selection = TrustReplyFrameSelection(salutationSnippetId = 2L),
                version = "frame-B"
            )
        ))

        // I-4: locked identity, evidence and request key never change with the frame.
        assertEquals(respA.itemVersions, respB.itemVersions)
        assertEquals("Salary info", respA.itemVersions.single().answerText)
        assertEquals(respA.itemVersions.single().versionId, respB.itemVersions.single().versionId)
        assertEquals(respA.evidenceSetVersion, respB.evidenceSetVersion)
        assertEquals(respA.requestedFactIds, respB.requestedFactIds)
        // I-3/I-4: the assembly (raw, rendered, hash) changes with the frame.
        assertNotEquals(respA.rawDraftText, respB.rawDraftText)
        assertNotEquals(respA.draftHash, respB.draftHash)
        assertEquals("frame-A", respA.frameSnapshot?.version)
        assertEquals("frame-B", respB.frameSnapshot?.version)
        assertEquals(1L, respA.frameSnapshot?.selection?.salutationSnippetId)
        assertEquals(2L, respB.frameSnapshot?.selection?.salutationSnippetId)
    }

    @Test
    fun `assemble fails closed on stale expected frame version before compose or preview`() {
        val fixture = assembleFixture()
        Mockito.`when`(
            fixture.replySnippetService.resolveSelectableFrame(ReplyFrameSelection(salutationSnippetId = 1L))
        ).thenReturn(resolvedFrame(
            selection = ReplyFrameSelection(salutationSnippetId = 1L),
            version = "fresh-version",
            salutation = "Fresh Sal"
        ))

        val ex = assertThrows(TrustReplyWorkbenchException::class.java) {
            fixture.service.assemble(fixture.request.copy(
                frameSnapshot = TrustReplyFrameSnapshot(
                    selection = TrustReplyFrameSelection(salutationSnippetId = 1L),
                    version = "old-version"
                )
            ))
        }

        assertEquals("TRUST_REPLY_FRAME_STALE", ex.code)
        Mockito.verifyNoInteractions(fixture.composer)
        Mockito.verifyNoInteractions(fixture.previewService)
    }

    @Test
    fun `assemble with all null frame selection never falls back to defaults`() {
        val fixture = assembleFixture()
        val emptySelection = ReplyFrameSelection(null, null, null, null)
        val emptyFrame = resolvedFrame(
            selection = emptySelection,
            version = "empty-frame",
            salutation = null,
            greeting = null,
            acknowledgement = null,
            closing = null
        )
        Mockito.`when`(fixture.replySnippetService.resolveSelectableFrame(emptySelection)).thenReturn(emptyFrame)
        Mockito.`when`(fixture.composer.composeLockedItems(listOf("Salary info"), emptyFrame))
            .thenReturn("Salary info")
        Mockito.`when`(fixture.previewService.preview("Salary info", fixture.contact, null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("rendered Salary info", emptyList()))

        val response = fixture.service.assemble(fixture.request.copy(
            frameSnapshot = TrustReplyFrameSnapshot(
                selection = TrustReplyFrameSelection(null, null, null, null),
                version = "empty-frame"
            )
        ))

        // I-2: explicit four-null selection produces answers only, no default frame text.
        assertEquals("Salary info", response.rawDraftText)
        assertEquals("empty-frame", response.frameSnapshot?.version)
        assertNull(response.frameSnapshot?.selection?.salutationSnippetId)
        assertNull(response.frameSnapshot?.selection?.closingSnippetId)
        Mockito.verify(fixture.replySnippetService, Mockito.never()).resolveDefaultSelectableFrame()
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

    private data class DuplicateFixture(
        val service: TrustReplyWorkbenchService,
        val request: TrustReplyAssembleRequest,
        val composer: AiReplyPointByPointComposer,
        val previewService: AiReplyDraftPreviewService
    )

    private fun duplicateFixture(
        item1Source: Long = 9L,
        item2Source: Long = 10L,
        item1Text: String = "Claim A",
        item2Text: String = "Claim B"
    ): DuplicateFixture {
        val mailRecords = Mockito.mock(MailRecordRepository::class.java)
        val inboundProcessing = Mockito.mock(InboundMailProcessingRepository::class.java)
        val contacts = Mockito.mock(ExpertContactRepository::class.java)
        val trainingQa = Mockito.mock(AiTrainingQaService::class.java)
        val contextService = Mockito.mock(AiReplyContextService::class.java)
        val factSelection = Mockito.mock(QaFactSelectionService::class.java)
        val qaRules = Mockito.mock(QaRuleRepository::class.java)
        val draftService = Mockito.mock(AiReplyDraftService::class.java)
        val previewService = Mockito.mock(AiReplyDraftPreviewService::class.java)
        val auditService = Mockito.mock(AiReplyReviewAuditService::class.java)
        val composer = Mockito.mock(AiReplyPointByPointComposer::class.java)
        val replySnippetService = Mockito.mock(ReplySnippetService::class.java)
        val contact = ExpertContact(
            id = 7L,
            campaignId = 1L,
            orcidId = "0000-0000",
            expertEmail = "test@example.com",
            expertName = "Test"
        )
        val mail = MailRecord(
            id = 11L,
            expertContactId = 7L,
            direction = "INBOUND",
            mailType = "REPLY",
            senderAccountCode = null,
            messageId = "<11@example.com>",
            inReplyTo = null,
            subject = "Subject",
            body = "What?",
            cleanedBody = null,
            matchedQaRuleId = null,
            sendStatus = null,
            receivedAt = LocalDateTime.of(2026, 7, 28, 10, 0),
            sentAt = null
        )
        val item1 = item(1, "What?", listOf(item1Source), RequestGroundingStatus.GROUNDED)
        val item2 = item(2, "Who?", listOf(item2Source), RequestGroundingStatus.GROUNDED)
        val sendIds = listOf(item1Source, item2Source).distinct()
        val selection = ResolvedQaRules(
            sendQaRuleIds = sendIds,
            promptRuleIds = sendIds,
            requestFacts = listOf(item1, item2),
            requestCount = 2,
            groundedRequestCount = 2
        )
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(mail))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(mail))
        Mockito.`when`(trainingQa.buildKnowledgeContext("What?")).thenReturn("")
        Mockito.`when`(
            contextService.build(
                Mockito.any(ExpertContact::class.java) ?: contact,
                Mockito.anyList<MailRecord>() ?: emptyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString()
            )
        ).thenReturn(AiReplyContext("Name: Test", "history", emptyList(), true))
        Mockito.`when`(factSelection.selectForWorkbench("What?", null, null, true)).thenReturn(selection)
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(sendIds))
            .thenReturn(Triple("e2", emptyList(), emptyList()))
        listOf(item1Source, item2Source).distinct().forEach { id ->
            Mockito.`when`(qaRules.findById(id)).thenReturn(Optional.of(QaRule(
                id = id,
                categoryId = 1,
                keywords = "salary",
                replyBody = "Salary info",
                answerBody = "Salary info",
                replySubject = null,
                enabled = true
            )))
        }
        val composedRaw = "raw $item1Text|$item2Text"
        val defaultFrame = defaultResolvedFrame()
        Mockito.`when`(replySnippetService.resolveDefaultSelectableFrame()).thenReturn(defaultFrame)
        Mockito.`when`(composer.composeLockedItems(listOf(item1Text, item2Text), defaultFrame))
            .thenReturn(composedRaw)
        Mockito.`when`(previewService.preview(composedRaw, contact, null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("rendered $composedRaw", emptyList()))

        val claimValidator = AiReplyHighRiskClaimValidator(qaRules)
        val stateStore = Mockito.mock(TrustReplyWorkbenchStateStore::class.java)
        val service = TrustReplyWorkbenchService(
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
            aiReplyPointByPointComposer = composer,
            claimValidator = claimValidator,
            stateStore = stateStore,
            replySnippetService = replySnippetService
        )
        val source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 11L)
        val sourceVersion = service.resolveSource(source).sourceVersion
        val key1 = TrustReplyWorkbenchService.requestKey(sourceVersion, 1, "What?", AiReplyIntentCatalog.matchIntents("What?").map { it.key })
        val key2 = TrustReplyWorkbenchService.requestKey(sourceVersion, 2, "Who?", AiReplyIntentCatalog.matchIntents("Who?").map { it.key })
        val evidenceSetVersion = AiReplyDraftService.sha256Hex(
            "e2\u0000$key1\u0000$item1Source\u0001$key2\u0000$item2Source"
        )
        val emptyHash = AiReplyDraftService.sha256Hex("")
        fun locked(item: RequestFactItem, text: String, sourceId: Long): TrustReplyLockedItemRequest {
            val claims = listOf(AiReplyItemClaim("general.answer", text, listOf(sourceId)))
            val requestKey = if (item.index == 1) key1 else key2
            val versionId = TrustReplyWorkbenchService.versionId(
                requestKey = requestKey,
                handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
                answerText = text,
                claims = claims,
                model = "DEEPSEEK_V4_FLASH",
                generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
                evidenceSetVersion = evidenceSetVersion,
                sourceVersion = sourceVersion,
                operatorInstructionHash = emptyHash
            )
            return TrustReplyLockedItemRequest(
                requestKey = requestKey,
                versionId = versionId,
                handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
                answerText = text,
                claims = claims,
                model = "DEEPSEEK_V4_FLASH",
                generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
                evidenceSetVersion = evidenceSetVersion,
                sourceVersion = sourceVersion,
                operatorInstructionHash = emptyHash
            )
        }
        val lockedItems = listOf(
            locked(item1, item1Text, item1Source),
            locked(item2, item2Text, item2Source)
        )
        return DuplicateFixture(
            service = service,
            request = TrustReplyAssembleRequest(source, sourceVersion, evidenceSetVersion, lockedItems),
            composer = composer,
            previewService = previewService
        )
    }

    private fun defaultResolvedFrame() = ResolvedReplyFrame(
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

    private fun operatorDirectedHandling(): TrustReplyItemHandling {
        val handling = TrustReplyItemHandling.values().firstOrNull {
            it.name == "ANSWER_FROM_OPERATOR_INPUT"
        }
        assertNotNull(handling)
        return handling!!
    }

    private data class AssembleFixture(
        val service: TrustReplyWorkbenchService,
        val request: TrustReplyAssembleRequest,
        val validLockedItem: TrustReplyLockedItemRequest,
        val draftService: AiReplyDraftService,
        val factSelection: QaFactSelectionService,
        val contact: ExpertContact,
        val composer: AiReplyPointByPointComposer,
        val previewService: AiReplyDraftPreviewService,
        val auditService: AiReplyReviewAuditService,
        val replySnippetService: ReplySnippetService,
        val defaultFrame: ResolvedReplyFrame
    )

    private fun assembleFixture(
        status: RequestGroundingStatus = RequestGroundingStatus.GROUNDED,
        handling: TrustReplyItemHandling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
        answerText: String = "Salary info",
        claims: List<AiReplyItemClaim> = listOf(AiReplyItemClaim("general.answer", "Salary info", listOf(9L))),
        generationKind: TrustReplyItemGenerationKind = TrustReplyItemGenerationKind.AI_GENERATED
    ): AssembleFixture {
        val mailRecords = Mockito.mock(MailRecordRepository::class.java)
        val inboundProcessing = Mockito.mock(InboundMailProcessingRepository::class.java)
        val contacts = Mockito.mock(ExpertContactRepository::class.java)
        val trainingQa = Mockito.mock(AiTrainingQaService::class.java)
        val contextService = Mockito.mock(AiReplyContextService::class.java)
        val factSelection = Mockito.mock(QaFactSelectionService::class.java)
        val qaRules = Mockito.mock(QaRuleRepository::class.java)
        val draftService = Mockito.mock(AiReplyDraftService::class.java)
        val previewService = Mockito.mock(AiReplyDraftPreviewService::class.java)
        val auditService = Mockito.mock(AiReplyReviewAuditService::class.java)
        val composer = Mockito.mock(AiReplyPointByPointComposer::class.java)
        val replySnippetService = Mockito.mock(ReplySnippetService::class.java)
        val contact = ExpertContact(
            id = 7L,
            campaignId = 1L,
            orcidId = "0000-0000",
            expertEmail = "test@example.com",
            expertName = "Test"
        )
        val mail = MailRecord(
            id = 11L,
            expertContactId = 7L,
            direction = "INBOUND",
            mailType = "REPLY",
            senderAccountCode = null,
            messageId = "<11@example.com>",
            inReplyTo = null,
            subject = "Subject",
            body = "What?",
            cleanedBody = null,
            matchedQaRuleId = null,
            sendStatus = null,
            receivedAt = LocalDateTime.of(2026, 7, 28, 10, 0),
            sentAt = null
        )
        val item = item(1, "What?", listOf(9L), status)
        val selection = ResolvedQaRules(
            sendQaRuleIds = listOf(9L),
            promptRuleIds = listOf(9L),
            requestFacts = listOf(item),
            requestCount = 1,
            groundedRequestCount = if (status == RequestGroundingStatus.GROUNDED) 1 else 0
        )
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(mail))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(mail))
        Mockito.`when`(trainingQa.buildKnowledgeContext("What?")).thenReturn("")
        Mockito.`when`(
            contextService.build(
                Mockito.any(ExpertContact::class.java) ?: contact,
                Mockito.anyList<MailRecord>() ?: emptyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString()
            )
        ).thenReturn(AiReplyContext("Name: Test", "history", emptyList(), true))
        Mockito.`when`(factSelection.selectForWorkbench("What?", null, null, true)).thenReturn(selection)
        Mockito.`when`(qaRules.findById(9L)).thenReturn(Optional.of(QaRule(
            id = 9L,
            categoryId = 1,
            keywords = "salary",
            replyBody = "Salary info",
            answerBody = "Salary info",
            replySubject = null,
            enabled = true
        )))
        val baseEvidenceSetVersion = AiReplyDraftService.sha256Hex(
            "9:true:null:${AiReplyDraftService.sha256Hex("Salary info")}"
        )
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(9L)))
            .thenReturn(Triple(baseEvidenceSetVersion, emptyList(), emptyList()))
        val canonicalAnswer = when (handling) {
            TrustReplyItemHandling.OMIT -> ""
            TrustReplyItemHandling.ACKNOWLEDGE_PENDING -> answerText.trim()
            else -> answerText.trim().ifBlank { claims.joinToString(" ") { it.text } }
        }
        val operatorInstruction = if (handling.name == "ANSWER_FROM_OPERATOR_INPUT") {
            "Use the operator-provided basis."
        } else {
            ""
        }
        val canonicalClaims = if (
            handling == TrustReplyItemHandling.OMIT || handling == TrustReplyItemHandling.ACKNOWLEDGE_PENDING
        ) emptyList() else claims
        val defaultFrame = defaultResolvedFrame()
        Mockito.`when`(replySnippetService.resolveDefaultSelectableFrame()).thenReturn(defaultFrame)
        Mockito.`when`(composer.composeLockedItems(listOf(canonicalAnswer), defaultFrame))
            .thenReturn("raw $canonicalAnswer")
        Mockito.`when`(previewService.preview("raw $canonicalAnswer", contact, null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("rendered $canonicalAnswer", emptyList()))
        Mockito.`when`(composer.composeLockedItems(emptyList(), defaultFrame)).thenReturn("")
        Mockito.`when`(previewService.preview("", contact, null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("", emptyList()))

        val claimValidator = AiReplyHighRiskClaimValidator(qaRules)
        val stateStore = Mockito.mock(TrustReplyWorkbenchStateStore::class.java)
        val service = TrustReplyWorkbenchService(
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
            aiReplyPointByPointComposer = composer,
            claimValidator = claimValidator,
            stateStore = stateStore,
            replySnippetService = replySnippetService
        )
        val source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 11L)
        val sourceVersion = service.resolveSource(source).sourceVersion
        val requestKey = TrustReplyWorkbenchService.requestKey(sourceVersion, 1, "What?", AiReplyIntentCatalog.matchIntents("What?").map { it.key })
        val evidenceSetVersion = AiReplyDraftService.sha256Hex("$baseEvidenceSetVersion\u0000$requestKey\u00009")
        val versionId = TrustReplyWorkbenchService.versionId(
            requestKey,
            handling,
            canonicalAnswer,
            canonicalClaims,
            "DEEPSEEK_V4_FLASH",
            generationKind,
            evidenceSetVersion,
            sourceVersion,
            AiReplyDraftService.sha256Hex(operatorInstruction)
        )
        val locked = TrustReplyLockedItemRequest(
            requestKey = requestKey,
            versionId = versionId,
            handling = handling,
            answerText = canonicalAnswer,
            claims = canonicalClaims,
            model = "DEEPSEEK_V4_FLASH",
            generationKind = generationKind,
            evidenceSetVersion = evidenceSetVersion,
            sourceVersion = sourceVersion,
            operatorInstructionHash = AiReplyDraftService.sha256Hex(operatorInstruction),
            operatorInstruction = operatorInstruction
        )
        return AssembleFixture(
            service = service,
            request = TrustReplyAssembleRequest(source, sourceVersion, evidenceSetVersion, listOf(locked)),
            validLockedItem = locked,
            draftService = draftService,
            factSelection = factSelection,
            contact = contact,
            composer = composer,
            previewService = previewService,
            auditService = auditService,
            replySnippetService = replySnippetService,
            defaultFrame = defaultFrame
        )
    }
}
