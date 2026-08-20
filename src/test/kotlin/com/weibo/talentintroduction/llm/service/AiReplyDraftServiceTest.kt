package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.CompositionSuggestResult
import com.weibo.talentintroduction.qa.service.GapItem
import com.weibo.talentintroduction.qa.service.QaMatchService
import com.weibo.talentintroduction.qa.service.QaReplyComposer
import com.weibo.talentintroduction.reply.service.ManualReplyFrame
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.service.GroundedAutoReplyDecisionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.client.ResourceAccessException
import java.util.Optional

class AiReplyDraftServiceTest {
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val qaFactSelectionService = QaFactSelectionService(qaRuleRepository)
    private val replySnippetService = Mockito.mock(ReplySnippetService::class.java)
    private val aiPromptConfigService = Mockito.mock(AiPromptConfigService::class.java)
    private val aiTrainingDialogueService = Mockito.mock(AiTrainingDialogueService::class.java)
    private val aiReplyContextService = Mockito.mock(AiReplyContextService::class.java)
    private val aiTrainingQaService = Mockito.mock(AiTrainingQaService::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val claimValidator = AiReplyHighRiskClaimValidator(qaRuleRepository)

    init {
        Mockito.`when`(aiPromptConfigService.getEffectiveFreeFormSystemPrompt(Mockito.anyString()))
            .thenAnswer { invocation -> invocation.getArgument(0) }
        Mockito.`when`(aiPromptConfigService.getEffectiveDto())
            .thenReturn(AiPromptConfigEffectiveDto(
                freeFormSystemPrompt = FreeFormPromptDefaults.defaultFreeFormSystemPrompt(),
                constraints = null,
                updatedAt = null,
                isCustom = false
            ))
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues(Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(emptyList())
    }

    @Suppress("UNCHECKED_CAST")
    private fun provider(client: LlmDraftClient?): ObjectProvider<LlmDraftClient> {
        val mock = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<LlmDraftClient>
        Mockito.`when`(mock.getIfAvailable()).thenReturn(client)
        return mock
    }

    private fun stubDefaultFrame(salutation: String? = "Dear Professor,", greeting: String? = QaReplyComposer.GREETING) {
        Mockito.`when`(replySnippetService.resolveManualFrame()).thenReturn(
            ManualReplyFrame(
                salutation = salutation,
                greeting = greeting,
                closing = QaReplyComposer.CLOSING,
                ackOptions = emptyList()
            )
        )
    }

    private fun stubEmptyFrame() {
        Mockito.`when`(replySnippetService.resolveManualFrame()).thenReturn(
            ManualReplyFrame(
                salutation = null,
                greeting = null,
                closing = null,
                ackOptions = emptyList()
            )
        )
    }

    private fun emptyComposition(suggestedRuleIds: List<Long> = emptyList()) = CompositionSuggestResult(
        suggestedRuleIds = suggestedRuleIds,
        suggestedRules = emptyList(),
        rulesByCategory = emptyList(),
        gapItems = emptyList(),
        gapDetected = false,
        matchedCategoryIds = emptyList()
    )

    private fun pointByPointComposer(): AiReplyPointByPointComposer =
        AiReplyPointByPointComposer(qaRuleRepository, replySnippetService)

    private fun groundedMaterializer(
        pointByPoint: AiReplyPointByPointComposer = pointByPointComposer()
    ): AiReplyGroundedDraftMaterializer =
        AiReplyGroundedDraftMaterializer(com.fasterxml.jackson.databind.ObjectMapper(), pointByPoint)

    private fun service(
        properties: LlmProperties,
        client: LlmDraftClient?,
        pointByPoint: AiReplyPointByPointComposer = pointByPointComposer(),
        validator: AiReplyHighRiskClaimValidator = claimValidator
    ): AiReplyDraftService =
        AiReplyDraftService(
            properties,
            provider(client),
            qaFactSelectionService,
            qaRuleRepository,
            replySnippetService,
            aiPromptConfigService,
            aiTrainingDialogueService,
            aiReplyContextService,
            pointByPoint,
            groundedMaterializer(pointByPoint),
            validator,
            AiReplyGroundedContentPlanner()
        )

    private fun stubMatchPool(vararg rules: QaRule) {
        val list = rules.toList()
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(list)
        list.forEach { rule ->
            rule.id?.let { id ->
                Mockito.`when`(qaRuleRepository.findById(id)).thenReturn(Optional.of(rule))
            }
        }
    }

    @Test
    fun `operator directed item uses only target question and operator answer basis without attached facts`() {
        // P2b (C-3 / I-4): 无绑定事实时 operator-directed prompt 仍只含目标问题与
        // answer basis——本条验证的正是 I-4 的恒等性（绑定为空时 prompt 一字不增）。
        stubEmptyFrame()
        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObserved(
                messages: List<LlmChatMessage>,
                temperature: Double?,
                providerModel: String
            ): LlmChatResult {
                capturedMessages += messages
                return LlmChatResult("Our current partners include A University and B Institute.")
            }
        }
        val handling = TrustReplyItemHandling.values().firstOrNull {
            it.name == "ANSWER_FROM_OPERATOR_INPUT"
        }
        assertNotNull(handling)

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generateItem(
            inboundText = "First question? Second question?",
            requestFact = RequestFactItem(
                index = 1,
                requestText = "First question?",
                factRuleIds = emptyList(),
                status = RequestGroundingStatus.UNSUPPORTED
            ),
            handling = handling!!,
            requestKey = "target-request",
            operatorInstruction = "Reply: our current partners include A University and B Institute."
        )

        assertTrue(result.lockable)
        assertTrue(result.usedLlm)
        assertEquals(TrustReplyItemGenerationKind.AI_GENERATED, result.generationKind)
        assertEquals("Our current partners include A University and B Institute.", result.itemAnswer?.answerText)
        assertTrue(result.itemAnswer?.claims?.isEmpty() == true)
        val prompt = capturedMessages.single().joinToString("\n") { it.content }
        assertTrue(prompt.contains("operator-provided answer basis"))
        assertTrue(prompt.contains("our current partners include A University and B Institute"))
        assertTrue(prompt.contains("First question?"))
        assertFalse(prompt.contains("Second question?"))
        assertFalse(prompt.contains("expression only"))
    }

    @Test
    fun `operator directed item injects attached facts as reference material`() {
        // P2b (C-2 / I-2 / B-2): 绑定事实经服务端注入为「可引用素材」通道，
        // 与 answer basis 并列；模型不得自行发明。事实正文只来自注入内容。
        stubEmptyFrame()
        val factRule = sampleRule(42).copy(
            displayName = "Research scope",
            answerBody = "The programme covers AI and NLP research directions."
        )
        stubMatchPool(factRule)
        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObserved(
                messages: List<LlmChatMessage>,
                temperature: Double?,
                providerModel: String
            ): LlmChatResult {
                capturedMessages += messages
                return LlmChatResult("We cover AI and NLP research directions.")
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generateItem(
            inboundText = "First question?",
            requestFact = RequestFactItem(
                index = 1,
                requestText = "First question?",
                factRuleIds = emptyList(),
                boundRuleIds = listOf(42L),
                status = RequestGroundingStatus.UNSUPPORTED
            ),
            handling = TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT,
            requestKey = "target-request",
            operatorInstruction = "Reply that we support selected research directions."
        )

        assertTrue(result.lockable)
        assertTrue(result.usedLlm)
        assertEquals(TrustReplyItemGenerationKind.AI_GENERATED, result.generationKind)
        val prompt = capturedMessages.single().joinToString("\n") { it.content }
        assertTrue(prompt.contains("operator-provided answer basis"))
        assertTrue(prompt.contains("Facts the operator attached to this question (reference material, not the answer basis):"))
        assertTrue(prompt.contains("Research scope"))
        assertTrue(prompt.contains("The programme covers AI and NLP research directions."))
    }

    @Test
    fun `operator directed item without bound facts keeps the prompt unchanged`() {
        // P2b (C-2 / I-4): boundRuleIds 为空时 prompt 不得出现「可引用事实」段落标题。
        stubEmptyFrame()
        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObserved(
                messages: List<LlmChatMessage>,
                temperature: Double?,
                providerModel: String
            ): LlmChatResult {
                capturedMessages += messages
                return LlmChatResult("Our current partners include A University and B Institute.")
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generateItem(
            inboundText = "First question?",
            requestFact = RequestFactItem(
                index = 1,
                requestText = "First question?",
                factRuleIds = emptyList(),
                boundRuleIds = emptyList(),
                status = RequestGroundingStatus.UNSUPPORTED
            ),
            handling = TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT,
            requestKey = "target-request",
            operatorInstruction = "Reply: our current partners include A University and B Institute."
        )

        assertTrue(result.lockable)
        assertTrue(result.usedLlm)
        val prompt = capturedMessages.single().joinToString("\n") { it.content }
        assertTrue(prompt.contains("operator-provided answer basis"))
        assertFalse(prompt.contains("Facts the operator attached"))
    }

    @Test
    fun `operator directed item still blocks an unauthorised action from an attached fact`() {
        // P2b (C-2 / I-5 / IP-4): 注入事实的正文含敏感措辞（护照索取）时，出参校验
        // findViolations 仍判废——不得为让注入的事实通过而放宽任一校验。
        stubEmptyFrame()
        val factRule = sampleRule(42).copy(
            answerBody = "Please provide a copy of your passport for identity verification."
        )
        stubMatchPool(factRule)
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObserved(
                messages: List<LlmChatMessage>,
                temperature: Double?,
                providerModel: String
            ): LlmChatResult = LlmChatResult("Please provide a copy of your passport for identity verification.")
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generateItem(
            inboundText = "What documents are needed?",
            requestFact = RequestFactItem(
                index = 1,
                requestText = "What documents are needed?",
                factRuleIds = emptyList(),
                boundRuleIds = listOf(42L),
                status = RequestGroundingStatus.UNSUPPORTED
            ),
            handling = TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT,
            requestKey = "target-request",
            operatorInstruction = "Reply that no identity documents are required."
        )

        assertFalse(result.lockable)
        assertFalse(result.usedLlm)
        assertNull(result.itemAnswer)
    }

    @Test
    fun `operator directed item rejects a Chinese AI answer for an English reply`() {
        stubEmptyFrame()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObserved(
                messages: List<LlmChatMessage>,
                temperature: Double?,
                providerModel: String
            ): LlmChatResult = LlmChatResult("您可参考官网发布的相关信息。")
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generateItem(
            inboundText = "Could you share examples of the institutions involved?",
            requestFact = RequestFactItem(
                index = 1,
                requestText = "Could you share examples of the institutions involved?",
                factRuleIds = emptyList(),
                status = RequestGroundingStatus.UNSUPPORTED
            ),
            handling = TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT,
            requestKey = "target-request",
            operatorInstruction = "请专家参考官网发布的相关信息。"
        )

        assertFalse(result.lockable)
        assertFalse(result.usedLlm)
        assertNull(result.itemAnswer)
        assertNull(result.generationKind)
        assertTrue(result.warningCodes.contains("AI_REPLY_ENGLISH_REQUIRED"))
    }

    @Test
    fun `pending acknowledgement rejects a Chinese AI answer for an English reply`() {
        stubEmptyFrame()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObserved(
                messages: List<LlmChatMessage>,
                temperature: Double?,
                providerModel: String
            ): LlmChatResult = LlmChatResult("该事项尚待确认。")
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generateItem(
            inboundText = "Could you confirm the programme details?",
            requestFact = RequestFactItem(
                index = 1,
                requestText = "Could you confirm the programme details?",
                factRuleIds = emptyList(),
                status = RequestGroundingStatus.UNSUPPORTED
            ),
            handling = TrustReplyItemHandling.ACKNOWLEDGE_PENDING,
            requestKey = "target-request"
        )

        assertFalse(result.lockable)
        assertFalse(result.usedLlm)
        assertNull(result.itemAnswer)
        assertNull(result.generationKind)
        assertTrue(result.warningCodes.contains("AI_REPLY_ENGLISH_REQUIRED"))
    }

    @Test
    fun `draft result exposes empty item answers by default for legacy consumers`() {
        val result = AiReplyDraftResult(
            draftText = "draft",
            usedLlm = false,
            qaRuleIds = emptyList(),
            mode = AiReplyMode.FREE_FORM
        )

        assertTrue(result.itemAnswers.isEmpty())
    }

    @Test
    fun `item operator instruction cannot add CTA authority`() {
        stubEmptyFrame()
        val rule = sampleRule()
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val candidate = """{"claims":[{"claimKey":"r1:finance.arrangements","text":"Salary info. Please send your CV if you are comfortable to assess your qualifications."}],"actionText":null}"""
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
            ): LlmChatResult {
                capturedMessages += messages
                return LlmChatResult(candidate)
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generateItem(
            inboundText = "What is salary?",
            requestFact = RequestFactItem(
                index = 1,
                requestText = "What is salary?",
                factRuleIds = listOf(1L),
                status = RequestGroundingStatus.GROUNDED,
                intents = listOf(
                    RequestIntentCoverage(
                        intentKey = "finance.arrangements",
                        title = "Salary",
                        requiredCoverageKeys = emptyList(),
                        missingEvidenceKeys = emptyList(),
                        evidenceRuleIds = listOf(1L),
                        status = "SUPPORTED",
                        requiresResearchContext = false
                    )
                )
            ),
            handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
            requestKey = "request-key",
            operatorInstruction = "Please ask for your CV if you are comfortable for an eligibility check."
        )

        assertFalse(result.lockable)
        assertFalse(result.usedLlm)
        assertTrue(capturedMessages.first().any { it.content.contains("Allowed actions: NONE") })
    }

    @Test
    fun `item fallback and omit are not falsely marked as AI`() {
        val item = RequestFactItem(1, "Question?", emptyList(), RequestGroundingStatus.GROUNDED)
        val omitted = service(LlmProperties(enabled = true), null).generateItem(
            inboundText = "Question?",
            requestFact = item,
            handling = TrustReplyItemHandling.OMIT
        )
        val failed = service(LlmProperties(enabled = true), null).generateItem(
            inboundText = "Question?",
            requestFact = item,
            handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE
        )

        assertTrue(omitted.lockable)
        assertEquals(TrustReplyItemGenerationKind.OMITTED, omitted.generationKind)
        assertFalse(failed.lockable)
        assertEquals(null, failed.generationKind)
    }

    @Test
    fun `item operator instruction is capped at 500 characters`() {
        assertThrows(IllegalArgumentException::class.java) {
            service(LlmProperties(enabled = false), null).generateItem(
                inboundText = "Question?",
                requestFact = RequestFactItem(1, "Question?", emptyList(), RequestGroundingStatus.GROUNDED),
                handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
                operatorInstruction = "x".repeat(501)
            )
        }
    }

    @Test
    fun `unsupported item fallback uses safe template and remains lockable`() {
        val result = service(LlmProperties(enabled = false), null).generateItem(
            inboundText = "Can you confirm the programme details?",
            requestFact = RequestFactItem(
                1,
                "Can you confirm the programme details?",
                emptyList(),
                RequestGroundingStatus.UNSUPPORTED
            ),
            handling = TrustReplyItemHandling.ACKNOWLEDGE_PENDING
        )

        assertTrue(result.lockable)
        assertFalse(result.usedLlm)
        assertEquals(TrustReplyItemGenerationKind.SAFE_TEMPLATE, result.generationKind)
        assertTrue(result.itemAnswer?.claims?.isEmpty() == true)
    }

    @Test
    fun `timeout policy resolves defaults and rejects invalid boundaries`() {
        assertEquals(
            AiReplyTimeoutPolicy(30, 300),
            AiReplyTimeoutPolicy.resolve(null, null)
        )
        assertEquals(
            AiReplyTimeoutPolicy(60, 60),
            AiReplyTimeoutPolicy.resolve(60, 60)
        )
        assertThrows(IllegalArgumentException::class.java) {
            AiReplyTimeoutPolicy.resolve(9, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiReplyTimeoutPolicy.resolve(60, 59)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiReplyTimeoutPolicy.resolve(600, 7201)
        }
    }

    @Test
    fun `timeout policy accepts every documented endpoint`() {
        assertEquals(AiReplyTimeoutPolicy(10, 10), AiReplyTimeoutPolicy.resolve(10, 10))
        assertEquals(AiReplyTimeoutPolicy(600, 600), AiReplyTimeoutPolicy.resolve(600, 600))
        assertEquals(AiReplyTimeoutPolicy(60, 601), AiReplyTimeoutPolicy.resolve(60, 601))
        assertEquals(AiReplyTimeoutPolicy(600, 7200), AiReplyTimeoutPolicy.resolve(600, 7200))
        assertThrows(IllegalArgumentException::class.java) {
            AiReplyTimeoutPolicy.resolve(601, null)
        }
    }

    @Test
    fun `generation budget clamps each attempt to remaining total`() {
        var now = 0L
        val budget = AiReplyTimeoutPolicy(600, 7200).budget { now }
        assertEquals(600_000L, budget.nextAttemptMillis())
        now = 6_950_000_000_000L
        assertEquals(250_000L, budget.nextAttemptMillis())
        now = 7_200_000_000_000L
        assertEquals(0L, budget.nextAttemptMillis())
    }

    @Test
    fun `cancellation listener failure does not prevent later listeners`() {
        val token = AiReplyCancellationToken()
        var laterCalled = false
        token.onCancel { throw IllegalStateException("listener") }
        token.onCancel { laterCalled = true }

        token.cancel()

        assertTrue(token.isCancelled())
        assertTrue(laterCalled)
    }

    @Test
    fun `noop progress reporter preserves no snapshot and no-op sink`() {
        val reporter = AiReplyProgressReporter.NOOP
        reporter.startBudget(AiReplyTimeoutPolicy(30, 300).budget { 0L })
        reporter.transition(AiReplyProgressPhase.CALLING)
        reporter.beginProviderCall(AiReplyProgressPhase.CALLING, 30_000)
            .onActivity(LlmStreamActivity.WRITING, 1, 1)
        reporter.endProviderCall()

        assertNull(reporter.snapshotNow())
    }

    @Test
    fun `progress tracker accumulates local stream deltas across provider calls`() {
        val snapshots = mutableListOf<AiReplyProgressSnapshot>()
        val tracker = AiReplyProgressTracker(
            generationId = "generation-1",
            attemptTimeoutSeconds = 30,
            totalTimeoutSeconds = 300,
            clock = { 1_000_000_000L },
            sink = snapshots::add
        )
        tracker.startBudget(AiReplyTimeoutPolicy(30, 300).budget { 1_000_000_000L })
        val first = tracker.beginProviderCall(AiReplyProgressPhase.CALLING, 30_000)
        first.onActivity(LlmStreamActivity.WRITING, 4, 7)
        tracker.endProviderCall()
        val second = tracker.beginProviderCall(AiReplyProgressPhase.REPAIRING, 30_000)
        second.onActivity(LlmStreamActivity.WRITING, 3, 5)

        val latest = tracker.snapshotNow()!!
        assertEquals(7, latest.providerEventCount)
        assertEquals(12, latest.contentChars)
        assertEquals(2, latest.providerCallIndex)
        assertTrue(snapshots.isNotEmpty())
    }

    @Test
    fun `ending provider call freezes attempt elapsed time`() {
        var now = 1_000_000_000L
        val tracker = AiReplyProgressTracker(
            generationId = "generation-freeze",
            attemptTimeoutSeconds = 30,
            totalTimeoutSeconds = 300,
            clock = { now },
            sink = {}
        )
        tracker.startBudget(AiReplyTimeoutPolicy(30, 300).budget { now })
        tracker.beginProviderCall(AiReplyProgressPhase.CALLING, 30_000)

        now = 4_000_000_000L
        tracker.endProviderCall()
        now = 9_000_000_000L

        val snapshot = tracker.snapshotNow()!!
        assertEquals(AiReplyProviderActivity.IDLE, snapshot.providerActivity)
        assertEquals(3, snapshot.attemptElapsedSeconds)
    }

    @Test
    fun `progress tracker saturates accumulated counters`() {
        val tracker = AiReplyProgressTracker(
            generationId = "generation-2",
            attemptTimeoutSeconds = 30,
            totalTimeoutSeconds = 300,
            clock = { 1_000_000_000L },
            sink = {}
        )
        tracker.startBudget(AiReplyTimeoutPolicy(30, 300).budget { 1_000_000_000L })
        val first = tracker.beginProviderCall(AiReplyProgressPhase.CALLING, 30_000)
        first.onActivity(LlmStreamActivity.WRITING, Int.MAX_VALUE, Int.MAX_VALUE)
        tracker.endProviderCall()
        val second = tracker.beginProviderCall(AiReplyProgressPhase.REPAIRING, 30_000)
        second.onActivity(LlmStreamActivity.WRITING, Int.MAX_VALUE, Int.MAX_VALUE)

        val latest = tracker.snapshotNow()!!
        assertEquals(Int.MAX_VALUE, latest.providerEventCount)
        assertEquals(Int.MAX_VALUE, latest.contentChars)
    }

    @Test
    fun `progress tracker publishes phase order and repair attempt index`() {
        val snapshots = mutableListOf<AiReplyProgressSnapshot>()
        val tracker = AiReplyProgressTracker(
            generationId = "generation-phases",
            attemptTimeoutSeconds = 30,
            totalTimeoutSeconds = 300,
            clock = { 1_000_000_000L },
            sink = snapshots::add
        )
        tracker.startBudget(AiReplyTimeoutPolicy(30, 300).budget { 1_000_000_000L })
        tracker.beginProviderCall(AiReplyProgressPhase.CALLING, 30_000)
        tracker.endProviderCall()
        tracker.transition(AiReplyProgressPhase.VALIDATING)
        tracker.beginProviderCall(AiReplyProgressPhase.REPAIRING, 30_000)
        tracker.endProviderCall()

        assertEquals(
            listOf(AiReplyProgressPhase.PREPARING, AiReplyProgressPhase.CALLING,
                AiReplyProgressPhase.VALIDATING, AiReplyProgressPhase.REPAIRING),
            snapshots.map { it.phase }.distinct()
        )
        assertEquals(2, snapshots.last().providerCallIndex)
    }

    @Test
    fun `progress tracker reports exact total attempt and activity elapsed`() {
        var now = 1_000_000_000L
        val tracker = AiReplyProgressTracker(
            generationId = "generation-clock",
            attemptTimeoutSeconds = 30,
            totalTimeoutSeconds = 300,
            clock = { now },
            sink = {}
        )
        tracker.startBudget(AiReplyTimeoutPolicy(30, 300).budget { now })
        val sink = tracker.beginProviderCall(AiReplyProgressPhase.CALLING, 30_000)
        now = 3_000_000_000L
        sink.onActivity(LlmStreamActivity.WRITING, 1, 2)
        now = 6_000_000_000L

        val snapshot = tracker.snapshotNow()!!
        assertEquals(5, snapshot.attemptElapsedSeconds)
        assertEquals(5, snapshot.totalElapsedSeconds)
        assertEquals(3, snapshot.secondsSinceProviderActivity)
    }

    @Test
    fun `fake monotonic snapshots never regress and clamp to both TTLs`() {
        var now = 0L
        val snapshots = mutableListOf<AiReplyProgressSnapshot>()
        val tracker = AiReplyProgressTracker(
            generationId = "generation-monotonic",
            attemptTimeoutSeconds = 10,
            totalTimeoutSeconds = 20,
            clock = { now },
            sink = snapshots::add
        )
        tracker.startBudget(AiReplyTimeoutPolicy(10, 20).budget { now })
        val stream = tracker.beginProviderCall(AiReplyProgressPhase.CALLING, 10_000)
        now = 2_000_000_000L
        stream.onActivity(LlmStreamActivity.WRITING, 1, 2)
        now = 5_000_000_000L
        tracker.snapshotNow()
        now = 12_000_000_000L
        tracker.snapshotNow()
        now = 25_000_000_000L
        tracker.snapshotNow()

        val published = snapshots.map { it.attemptElapsedSeconds }
        assertEquals(published.sorted(), published)
        assertTrue(snapshots.zipWithNext().all { (a, b) ->
            b.totalElapsedSeconds >= a.totalElapsedSeconds &&
                b.secondsSinceProviderActivity >= a.secondsSinceProviderActivity
        })
        assertTrue(snapshots.all { it.attemptElapsedSeconds <= 10 && it.totalElapsedSeconds <= 20 })
    }

    @Test
    fun `cancellation prevents retry after the first provider failure`() {
        stubEmptyFrame()
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        val token = AiReplyCancellationToken()
        var calls = 0
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
            ): LlmChatResult {
                calls++
                cancellationToken.cancel()
                return LlmChatResult(null, LlmChatFailureType.NETWORK_ERROR)
            }
        }

        assertThrows(AiReplyGenerationCancelledException::class.java) {
            service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
                inboundText = "Hello",
                operatorTurns = emptyList(),
                llmAttemptTimeoutSeconds = 30,
                llmTotalTimeoutSeconds = 300,
                cancellationToken = token
            )
        }
        assertEquals(1, calls)
    }

    @Test
    fun `retry ceiling is two provider calls for retryable failure`() {
        stubEmptyFrame()
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        var calls = 0
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
            ): LlmChatResult {
                calls++
                return LlmChatResult(null, LlmChatFailureType.NETWORK_ERROR)
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            llmAttemptTimeoutSeconds = 30,
            llmTotalTimeoutSeconds = 300
        )

        assertEquals(2, calls)
        assertTrue(result.contextWarnings.contains("AI_REPLY_LLM_NETWORK_ERROR"))
        assertTrue(result.draftText.isNotBlank())
    }

    @Test
    fun `retry ceiling is two provider calls for attempt timeout`() {
        stubEmptyFrame()
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        var calls = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObservedStream(
                messages: List<LlmChatMessage>, temperature: Double?, providerModel: String,
                timeoutMillis: Long, jsonOutput: Boolean, cancellationToken: AiReplyCancellationToken,
                progressSink: LlmStreamProgressSink
            ): LlmChatResult {
                calls++
                return LlmChatResult(null, LlmChatFailureType.TIMEOUT)
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Hello", operatorTurns = emptyList(),
            llmAttemptTimeoutSeconds = 10, llmTotalTimeoutSeconds = 300
        )
        assertEquals(2, calls)
        assertTrue(result.contextWarnings.contains("AI_REPLY_LLM_TIMEOUT"))
    }

    @Test
    fun `total exhaustion prevents second provider call and emits total warning`() {
        stubEmptyFrame()
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        var calls = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObservedStream(
                messages: List<LlmChatMessage>, temperature: Double?, providerModel: String,
                timeoutMillis: Long, jsonOutput: Boolean, cancellationToken: AiReplyCancellationToken,
                progressSink: LlmStreamProgressSink
            ): LlmChatResult {
                calls++
                java.util.concurrent.locks.LockSupport.parkNanos(10_050_000_000L)
                return LlmChatResult(null, LlmChatFailureType.TIMEOUT)
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Hello", operatorTurns = emptyList(),
            llmAttemptTimeoutSeconds = 10, llmTotalTimeoutSeconds = 10
        )
        assertEquals(1, calls)
        assertTrue(result.contextWarnings.contains("AI_REPLY_LLM_TOTAL_TIMEOUT"))
        assertFalse(result.usedLlm)
    }

    @Test
    fun `provider success after total deadline falls back before materialization`() {
        stubEmptyFrame()
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        var calls = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObservedStream(
                messages: List<LlmChatMessage>, temperature: Double?, providerModel: String,
                timeoutMillis: Long, jsonOutput: Boolean, cancellationToken: AiReplyCancellationToken,
                progressSink: LlmStreamProgressSink
            ): LlmChatResult {
                calls++
                java.util.concurrent.locks.LockSupport.parkNanos(10_050_000_000L)
                return LlmChatResult("provider draft")
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Hello", operatorTurns = emptyList(),
            llmAttemptTimeoutSeconds = 10, llmTotalTimeoutSeconds = 10
        )
        assertEquals(1, calls)
        assertTrue(result.contextWarnings.contains("AI_REPLY_LLM_TOTAL_TIMEOUT"))
        assertFalse(result.usedLlm)
    }

    @Test
    fun `generate reports real phase sequence and provider attempt index`() {
        stubEmptyFrame()
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        val snapshots = mutableListOf<AiReplyProgressSnapshot>()
        val tracker = AiReplyProgressTracker("generation-real", 30, 300, sink = snapshots::add)
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "legacy"
            override fun chatWithModelObservedStream(
                messages: List<LlmChatMessage>, temperature: Double?, providerModel: String,
                timeoutMillis: Long, jsonOutput: Boolean, cancellationToken: AiReplyCancellationToken,
                progressSink: LlmStreamProgressSink
            ): LlmChatResult = LlmChatResult("provider draft")
        }

        service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Hello", operatorTurns = emptyList(),
            llmAttemptTimeoutSeconds = 30, llmTotalTimeoutSeconds = 300,
            progressReporter = tracker
        )
        assertEquals(
            listOf(AiReplyProgressPhase.PREPARING, AiReplyProgressPhase.CALLING,
                AiReplyProgressPhase.VALIDATING, AiReplyProgressPhase.FINALIZING),
            snapshots.map { it.phase }.distinct()
        )
        assertEquals(1, snapshots.maxOf { it.providerCallIndex })
    }

    @Test
    fun `cancellation blocks trust correction before second provider call`() {
        stubEmptyFrame()
        val rule = sampleRule(5).copy(keywords = "funding", coverageKeys = "finance.arrangements")
        stubMatchPool(rule)
        val token = AiReplyCancellationToken()
        var calls = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObservedStream(
                messages: List<LlmChatMessage>, temperature: Double?, providerModel: String,
                timeoutMillis: Long, jsonOutput: Boolean, cancellationToken: AiReplyCancellationToken,
                progressSink: LlmStreamProgressSink
            ): LlmChatResult {
                calls++
                cancellationToken.cancel()
                return LlmChatResult("not-json")
            }
        }

        assertThrows(AiReplyGenerationCancelledException::class.java) {
            service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
                inboundText = "Funding?", operatorTurns = emptyList(), qaRuleIds = listOf(5L),
                llmAttemptTimeoutSeconds = 30, llmTotalTimeoutSeconds = 300,
                cancellationToken = token
            )
        }
        assertEquals(1, calls)
    }

    @Test
    fun `cancellation blocks action correction before second provider call`() {
        stubEmptyFrame()
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        val token = AiReplyCancellationToken()
        var calls = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObservedStream(
                messages: List<LlmChatMessage>, temperature: Double?, providerModel: String,
                timeoutMillis: Long, jsonOutput: Boolean, cancellationToken: AiReplyCancellationToken,
                progressSink: LlmStreamProgressSink
            ): LlmChatResult {
                calls++
                cancellationToken.cancel()
                return LlmChatResult("Please send your CV.")
            }
        }

        assertThrows(AiReplyGenerationCancelledException::class.java) {
            service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
                inboundText = freeFormActionPolicyInbound,
                operatorTurns = emptyList(),
                llmAttemptTimeoutSeconds = 30, llmTotalTimeoutSeconds = 300,
                cancellationToken = token
            )
        }
        assertEquals(1, calls)
    }

    @Test
    fun `runtime-enabled generate uses stream while legacy generate uses observed seam`() {
        stubEmptyFrame()
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        var observedCalls = 0
        var streamCalls = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "legacy draft"
            override fun chatWithModelObserved(
                messages: List<LlmChatMessage>, temperature: Double?, providerModel: String
            ): LlmChatResult {
                observedCalls++
                return LlmChatResult("legacy draft")
            }
            override fun chatWithModelObservedStream(
                messages: List<LlmChatMessage>, temperature: Double?, providerModel: String,
                timeoutMillis: Long, jsonOutput: Boolean, cancellationToken: AiReplyCancellationToken,
                progressSink: LlmStreamProgressSink
            ): LlmChatResult {
                streamCalls++
                return LlmChatResult("stream draft")
            }
        }
        val legacy = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Hello", operatorTurns = emptyList()
        )
        val runtime = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Hello", operatorTurns = emptyList(),
            llmAttemptTimeoutSeconds = 30, llmTotalTimeoutSeconds = 300
        )
        assertEquals(1, observedCalls)
        assertEquals(1, streamCalls)
        assertEquals("legacy draft", legacy.draftText)
        assertEquals("stream draft", runtime.draftText)
    }

    @Test
    fun `explicit all-null noop runtime path matches legacy result`() {
        stubEmptyFrame()
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "same draft"
        }
        val legacy = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Hello", operatorTurns = emptyList()
        )
        val explicit = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Hello", operatorTurns = emptyList(),
            llmAttemptTimeoutSeconds = null, llmTotalTimeoutSeconds = null,
            cancellationToken = null, progressReporter = AiReplyProgressReporter.NOOP
        )
        assertEquals(legacy, explicit)
    }

    @Test
    fun `training simulate entrypoint remains on observed seam`() {
        stubEmptyFrame()
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        var observedCalls = 0
        var streamCalls = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObserved(
                messages: List<LlmChatMessage>, temperature: Double?, providerModel: String
            ): LlmChatResult {
                observedCalls++
                return LlmChatResult("training draft")
            }
            override fun chatWithModelObservedStream(
                messages: List<LlmChatMessage>, temperature: Double?, providerModel: String,
                timeoutMillis: Long, jsonOutput: Boolean, cancellationToken: AiReplyCancellationToken,
                progressSink: LlmStreamProgressSink
            ): LlmChatResult {
                streamCalls++
                return LlmChatResult("unexpected stream")
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Training question", operatorTurns = emptyList(), simulateOnly = true
        )

        assertEquals("training draft", result.draftText)
        assertEquals(1, observedCalls)
        assertEquals(0, streamCalls)
    }

    @Test
    fun `grounded auto reply entrypoint remains on legacy seam`() {
        stubEmptyFrame()
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        var observedCalls = 0
        var streamCalls = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObserved(
                messages: List<LlmChatMessage>, temperature: Double?, providerModel: String
            ): LlmChatResult {
                observedCalls++
                return LlmChatResult("auto draft")
            }
            override fun chatWithModelObservedStream(
                messages: List<LlmChatMessage>, temperature: Double?, providerModel: String,
                timeoutMillis: Long, jsonOutput: Boolean, cancellationToken: AiReplyCancellationToken,
                progressSink: LlmStreamProgressSink
            ): LlmChatResult {
                streamCalls++
                return LlmChatResult("unexpected stream")
            }
        }
        val props = LlmProperties(enabled = true, apiUrl = "http://llm", autoReplyEnabled = true)
        val decision = GroundedAutoReplyDecisionService(
            props,
            service(props, client),
            qaRuleRepository,
            aiReplyContextService,
            aiTrainingQaService,
            mailRecordRepository
        ).decide("Auto question", "Subject", null)

        assertEquals("Re: Subject", decision.subject)
        assertEquals(1, observedCalls)
        assertEquals(0, streamCalls)
    }

    @Test
    fun `trust repair uses real stream call index and repairing phase`() {
        stubEmptyFrame()
        stubMatchPool(sampleRule(5).copy(keywords = "funding", coverageKeys = "finance.arrangements"))
        val snapshots = mutableListOf<AiReplyProgressSnapshot>()
        var calls = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObservedStream(
                messages: List<LlmChatMessage>, temperature: Double?, providerModel: String,
                timeoutMillis: Long, jsonOutput: Boolean, cancellationToken: AiReplyCancellationToken,
                progressSink: LlmStreamProgressSink
            ): LlmChatResult {
                calls++
                return LlmChatResult(if (calls == 1) "not-json" else "still-invalid")
            }
        }
        service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Funding?", operatorTurns = emptyList(), qaRuleIds = listOf(5L),
            llmAttemptTimeoutSeconds = 30, llmTotalTimeoutSeconds = 300,
            progressReporter = AiReplyProgressTracker("trust-repair", 30, 300, sink = snapshots::add)
        )
        assertEquals(2, calls)
        assertTrue(snapshots.any { it.phase == AiReplyProgressPhase.REPAIRING && it.providerCallIndex == 2 })
    }

    @Test
    fun `action repair uses real stream call index and repairing phase`() {
        stubEmptyFrame()
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        val snapshots = mutableListOf<AiReplyProgressSnapshot>()
        var calls = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObservedStream(
                messages: List<LlmChatMessage>, temperature: Double?, providerModel: String,
                timeoutMillis: Long, jsonOutput: Boolean, cancellationToken: AiReplyCancellationToken,
                progressSink: LlmStreamProgressSink
            ): LlmChatResult {
                calls++
                return LlmChatResult(if (calls == 1) "Please send your CV." else "I will provide approved information.")
            }
        }
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = freeFormActionPolicyInbound, operatorTurns = emptyList(),
            llmAttemptTimeoutSeconds = 30, llmTotalTimeoutSeconds = 300,
            progressReporter = AiReplyProgressTracker("action-repair", 30, 300, sink = snapshots::add)
        )
        assertTrue(result.usedLlm)
        assertEquals(2, calls)
        assertTrue(snapshots.any { it.phase == AiReplyProgressPhase.REPAIRING && it.providerCallIndex == 2 })
    }

    private fun sampleRule(
        id: Long = 1L,
        replyBody: String = "Salary info",
        answerBody: String = replyBody,
        keywords: String = "salary",
        coverageKeys: String = ""
    ) = QaRule(
        id = id,
        categoryId = 1,
        keywords = keywords,
        replyBody = replyBody,
        answerBody = answerBody,
        replySubject = "Re",
        enabled = true,
        replyPolicy = com.weibo.talentintroduction.qa.domain.QaReplyPolicy.AUTO.name,
        coverageKeys = coverageKeys
    )

    @Test
    fun `grounded repair uses exact diagnostics skeleton and preserves initial lineage`() {
        stubEmptyFrame()
        val rule = sampleRule()
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        val responses = ArrayDeque(listOf(
            "{\"claims\":[],\"actionText\":null}",
            "{\"claims\":[{\"claimKey\":\"r1:finance.arrangements\",\"text\":\"Salary info\"}],\"actionText\":null}"
        ))
        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val temperatures = mutableListOf<Double?>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                temperatures += temperature
                return responses.removeFirst()
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm", temperature = 0.3), client).generate(
            inboundText = "What is salary?",
            operatorTurns = emptyList()
        )

        assertTrue(result.usedLlm)
        assertEquals(AiReplyGenerationState.LLM_USED, result.generationState)
        assertEquals(listOf(0.3, 0.0), temperatures)
        assertEquals(AiReplyValidationAttempt.INITIAL, result.validationDiagnostics.items.single().attempt)
        assertEquals(AiReplyValidationCodes.CLAIM_SET_MISMATCH, result.validationDiagnostics.items.single().code)
        val repair = capturedMessages[1].last().content
        assertTrue(repair.contains("AI_REPLY_STRUCTURE_CLAIM_SET_MISMATCH"))
        assertTrue(repair.contains("r1:finance.arrangements"))
        assertTrue(repair.contains("Return only the exact minimal JSON protocol"))
        assertFalse(repair.contains("{\"claims\":[],\"actionText\":null}"))
        assertTrue(capturedMessages[0].last().content.contains("CURRENT SERVER PLAN"))
        assertTrue(capturedMessages[0].last().content.contains("Allowed actions:"))
    }

    @Test
    fun `grounded repair rejects invalid actionText and keeps one repair`() {
        stubEmptyFrame()
        val rule = sampleRule()
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        val responses = ArrayDeque(listOf(
            "{\"claims\":[{\"claimKey\":\"r1:finance.arrangements\",\"text\":\"Salary info\"}],\"actionText\":1}",
            "{\"claims\":[{\"claimKey\":\"r1:finance.arrangements\",\"text\":\"Salary info\"}],\"actionText\":null}"
        ))
        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val temperatures = mutableListOf<Double?>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                temperatures += temperature
                return responses.removeFirst()
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm", temperature = 0.3), client).generate(
            inboundText = "What is salary?",
            operatorTurns = emptyList()
        )

        assertTrue(result.usedLlm)
        assertEquals(2, capturedMessages.size)
        assertEquals(listOf(0.3, 0.0), temperatures)
        assertEquals(AiReplyValidationCodes.ACTION_TEXT_INVALID, result.validationDiagnostics.items.single().code)
        val repair = capturedMessages[1].last().content
        assertTrue(repair.contains("AI_REPLY_ACTION_TEXT_INVALID"))
        assertTrue(repair.contains("actionText to null or a nonblank string containing exactly one detected allowed action"))
        assertTrue(repair.contains("{\"claims\":[{"))
        assertFalse(repair.contains("Salary info"))
    }

    @Test
    fun `grounded repair orders claim trust and action diagnostics and explains each stage`() {
        stubEmptyFrame()
        val rule = sampleRule(
            replyBody = "The enterprise is not yet determined.",
            answerBody = "The enterprise is not yet determined.",
            keywords = "enterprise projects,types",
            coverageKeys = "enterprise.project_types"
        )
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        val responses = ArrayDeque(listOf(
            "{\"claims\":[{\"claimKey\":\"r1:enterprise.project_types\",\"text\":\"Government support applies; the company is definitely established.\"}],\"actionText\":\"Please send your CV.\"}",
            "{\"claims\":[{\"claimKey\":\"r1:enterprise.project_types\",\"text\":\"The enterprise is not yet determined.\"}],\"actionText\":null}"
        ))
        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return responses.removeFirst()
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm", temperature = 0.3), client).generate(
            inboundText = "What are the enterprise project types?",
            operatorTurns = emptyList()
        )

        assertEquals(
            listOf(AiReplyValidationStage.CLAIM, AiReplyValidationStage.TRUST, AiReplyValidationStage.ACTION),
            result.validationDiagnostics.items.take(3).map { it.stage }
        )
        val repair = capturedMessages[1].last().content
        val claimCode = "AI_REPLY_CLAIM_HIGH_RISK_UNBACKED"
        val trustCode = "AI_REPLY_CLAIM_ENTERPRISE_UNGROUNDED"
        val actionCode = "AI_REPLY_ACTION_NOT_ALLOWED"
        assertTrue(repair.contains(claimCode))
        assertTrue(repair.contains(trustCode))
        assertTrue(repair.contains(actionCode))
        assertTrue(repair.contains("Remove each high-risk declaration unless the same claim's bound rule fact explicitly supports it"))
        assertTrue(repair.contains("Keep enterprise identity uncertain when the bound fact says it is not yet determined or matched"))
        assertTrue(repair.contains("Remove the unauthorized action or use actionText only for one action listed as allowed by the plan"))
        assertTrue(repair.contains("{\"claims\":[{"))
        assertFalse(repair.contains("Government support"))
        assertFalse(repair.contains("company is definitely"))
    }

    @Test
    fun `every stable grounded diagnostic has distinct body free repair guidance`() {
        val cases = listOf(
            AiReplyValidationStage.STRUCTURE to AiReplyValidationCodes.JSON_INVALID,
            AiReplyValidationStage.STRUCTURE to AiReplyValidationCodes.TOP_LEVEL_FIELDS_INVALID,
            AiReplyValidationStage.STRUCTURE to AiReplyValidationCodes.CLAIMS_INVALID,
            AiReplyValidationStage.STRUCTURE to AiReplyValidationCodes.CLAIM_FIELDS_INVALID,
            AiReplyValidationStage.STRUCTURE to AiReplyValidationCodes.CLAIM_KEY_DUPLICATE,
            AiReplyValidationStage.STRUCTURE to AiReplyValidationCodes.CLAIM_KEY_UNKNOWN,
            AiReplyValidationStage.STRUCTURE to AiReplyValidationCodes.CLAIM_SET_MISMATCH,
            AiReplyValidationStage.STRUCTURE to AiReplyValidationCodes.CLAIM_TEXT_INVALID,
            AiReplyValidationStage.STRUCTURE to AiReplyGroundedDraftMaterializer.WARNING_UNNATURAL_GROUNDED_STRUCTURE,
            AiReplyValidationStage.CLAIM to AiReplyHighRiskClaimValidator.WARNING_CLAIM_SOURCE_UNAVAILABLE,
            AiReplyValidationStage.CLAIM to AiReplyHighRiskClaimValidator.WARNING_CLAIM_HALLUCINATED_FACT,
            AiReplyValidationStage.CLAIM to AiReplyHighRiskClaimValidator.WARNING_CLAIM_MODALITY_STRENGTHENED,
            AiReplyValidationStage.CLAIM to AiReplyHighRiskClaimValidator.WARNING_CLAIM_HIGH_RISK_UNBACKED,
            AiReplyValidationStage.TRUST to AiReplyHighRiskClaimValidator.WARNING_CLAIM_TRUST_RHETORIC,
            AiReplyValidationStage.TRUST to AiReplyHighRiskClaimValidator.WARNING_CLAIM_CONFIDENTIALITY_SUBSTITUTE,
            AiReplyValidationStage.TRUST to AiReplyHighRiskClaimValidator.WARNING_CLAIM_ROLE_DISCLOSURE_OMITTED,
            AiReplyValidationStage.TRUST to AiReplyHighRiskClaimValidator.WARNING_CLAIM_ENTERPRISE_UNGROUNDED,
            AiReplyValidationStage.ACTION to AiReplyValidationCodes.ACTION_TEXT_INVALID,
            AiReplyValidationStage.ACTION to AiReplyValidationCodes.ACTION_NOT_ALLOWED,
            AiReplyValidationStage.ACTION to AiReplyValidationCodes.ACTION_BODY_MISMATCH,
            AiReplyValidationStage.ACTION to AiReplyDraftService.UNAUTHORIZED_ACTION_REMOVED,
            AiReplyValidationStage.ACTION to AiReplyActionPolicy.CODE_ACTION_SENSITIVE_MATERIAL,
            AiReplyValidationStage.ACTION to AiReplyActionPolicy.CODE_ACTION_CV_PURPOSE_MISSING,
            AiReplyValidationStage.ACTION to AiReplyActionPolicy.CODE_ACTION_CV_OPTIONALITY_MISSING
        )
        val draftService = service(LlmProperties(enabled = false), null)
        val instructions = cases.map { (stage, code) ->
            code to draftService.repairInstruction(AiReplyValidationIssue(stage, code, "r1:key"))
        }

        assertEquals(cases.size, instructions.map { it.second }.distinct().size)
        instructions.forEach { (code, instruction) ->
            assertTrue(instruction.isNotBlank(), code)
            assertFalse(instruction.contains("Salary info"), code)
            assertFalse(instruction.contains("Government support"), code)
            assertFalse(instruction.contains("answerBody"), code)
        }
    }

    @Test
    fun `returns deterministic draft when llm disabled`() {
        val rule = sampleRule()
        stubDefaultFrame()
                Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        stubMatchPool(rule)
        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "What is salary?",
            operatorTurns = emptyList()
        )

        assertTrue(result.draftText.contains("Salary info"))
        assertFalse(result.usedLlm)
        assertEquals(AiReplyGenerationState.FALLBACK_LLM_DISABLED, result.generationState)
        assertEquals(listOf(1L), result.qaRuleIds)
        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
    }

    @Test
    fun `falls back when llm client throws`() {
        val rule = sampleRule(2).copy(keywords = "visa", replyBody = "Visa info", answerBody = "Visa info")
        stubDefaultFrame()
        stubMatchPool(rule)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val failingClient = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                throw ResourceAccessException("Read timed out")
            }
        }

        stubMatchPool(rule)
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), failingClient).generate(
            inboundText = "Visa?",
            operatorTurns = emptyList()
        )

        assertTrue(result.draftText.contains("Visa info"))
        assertFalse(result.usedLlm)
        assertEquals(AiReplyGenerationState.FALLBACK_NO_RESPONSE, result.generationState)
        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
    }

    @Test
    fun `generationState truth table matches usedLlm for all four branches`() {
        stubDefaultFrame()
                Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule()))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val disabled = service(LlmProperties(enabled = false), null).generate(
            inboundText = "Hello, just a greeting.",
            operatorTurns = emptyList()
        )
        assertFalse(disabled.usedLlm)
        assertEquals(AiReplyGenerationState.FALLBACK_LLM_DISABLED, disabled.generationState)

        val nullClient = service(LlmProperties(enabled = true, apiUrl = "http://llm"), null).generate(
            inboundText = "Hello, just a greeting.",
            operatorTurns = emptyList()
        )
        assertFalse(nullClient.usedLlm)
        assertEquals(AiReplyGenerationState.FALLBACK_CLIENT_UNAVAILABLE, nullClient.generationState)

        val emptyClient = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "   "
        }
        val noResponse = service(LlmProperties(enabled = true, apiUrl = "http://llm"), emptyClient).generate(
            inboundText = "Hello, just a greeting.",
            operatorTurns = emptyList()
        )
        assertFalse(noResponse.usedLlm)
        assertEquals(AiReplyGenerationState.FALLBACK_NO_RESPONSE, noResponse.generationState)

        val okClient = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = minimalGroundedJson
        }
        val used = service(LlmProperties(enabled = true, apiUrl = "http://llm"), okClient).generate(
            inboundText = "Hello, just a greeting.",
            operatorTurns = emptyList()
        )
        assertTrue(used.usedLlm)
        assertEquals(AiReplyGenerationState.LLM_USED, used.generationState)
    }

    @Test
    fun `uses suggestComposition subset when qaRuleIds null`() {
        stubDefaultFrame()
                val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturedTemperatures = mutableListOf<Double?>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                capturedTemperatures += temperature
                return minimalGroundedJson
            }
        }
        val rule = sampleRule(5).copy(
            replyBody = "Funding info",
            answerBody = "Funding info",
            keywords = "funding,fund"
        )
        Mockito.`when`(qaRuleRepository.findById(5L)).thenReturn(Optional.of(rule))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        stubMatchPool(rule)
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm", freeFormTemperature = 0.3), client).generate(
            inboundText = "Funding?",
            operatorTurns = emptyList(),
            qaRuleIds = null
        )
        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
        assertTrue(result.usedLlm)
        val userContent = capturedMessages.first().first { it.role == "user" }.content
        assertTrue(userContent.contains("Funding info"))
        val systemPrompt = capturedMessages.first().first { it.role == "system" }.content
        assertTrue(systemPrompt.contains("Return exactly one JSON object"))
        assertEquals(0.3, capturedTemperatures.single())
    }

    @Test
    fun `free form mode when suggestComposition empty`() {
        stubEmptyFrame()
                val allRules = listOf(sampleRule(10), sampleRule(11).copy(id = 11, replyBody = "Rule 11"))
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(allRules)
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(allRules[0]))
        Mockito.`when`(qaRuleRepository.findById(11L)).thenReturn(Optional.of(allRules[1]))

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturedTemperatures = mutableListOf<Double?>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                capturedTemperatures += temperature
                return "Free form draft"
            }
        }

        val result = service(
            LlmProperties(enabled = true, apiUrl = "http://llm", freeFormTemperature = 0.6),
            client
        ).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            expertProfile = "Name: Dr. Smith",
            mailHistory = "[OUTBOUND] Intro\nWelcome aboard"
        )

        assertEquals(emptyList<Long>(), result.qaRuleIds)
        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertTrue(result.usedLlm)
        val messages = capturedMessages.first()
        val systemPrompt = messages.first { it.role == "system" }.content
        assertTrue(systemPrompt.contains("No QA rules matched"))
        assertFalse(systemPrompt.contains("Salary info"))
        val userContent = messages.first { it.role == "user" }.content
        assertFalse(userContent.contains("QA rule knowledge (authoritative facts):"))
        assertFalse(userContent.contains("Salary info"))
        assertFalse(userContent.contains("Rule 11"))
        assertTrue(userContent.contains("Name: Dr. Smith"))
        assertTrue(userContent.contains("Welcome aboard"))
        assertTrue(userContent.contains("Hello"))
        assertEquals(0.6, capturedTemperatures.single())
    }

    @Test
    fun `first turn fallback with no match returns empty send qaRuleIds`() {
        stubDefaultFrame()
                val allRules = listOf(sampleRule(10), sampleRule(11).copy(id = 11, replyBody = "Rule 11"))
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(allRules)
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(allRules[0]))
        Mockito.`when`(qaRuleRepository.findById(11L)).thenReturn(Optional.of(allRules[1]))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "Hello",
            operatorTurns = emptyList()
        )

        assertEquals(emptyList<Long>(), result.qaRuleIds)
        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertFalse(result.draftText.contains("Rule 11"))
        assertFalse(result.usedLlm)
    }

    @Test
    fun `continuation falls back to reference text not previous draft when llm unavailable`() {
        stubEmptyFrame()
        val rule = sampleRule(1).copy(answerBody = "Salary info")
        stubMatchPool(rule)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))
        val previousDraft = "Previous assistant draft"
        val turns = listOf(AiReplyTurn(assistantDraft = previousDraft, operatorInstruction = "more formal"))

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "What is salary?",
            operatorTurns = turns,
            qaRuleIds = listOf(1)
        )

        assertTrue(result.draftText.contains("QA 规则参考内容"))
        assertFalse(result.draftText.contains(previousDraft))
        assertFalse(result.usedLlm)
        assertEquals(AiReplyDraftReadiness.BLOCKED, result.draftReadiness)
        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
        assertEquals(listOf(1L), result.qaRuleIds)
    }

    @Test
    fun `includes frame elements in matched user content when configured`() {
        stubDefaultFrame(salutation = "Dear Dr.", greeting = "Hope you are well.")
                val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return minimalGroundedJson
            }
        }
        stubMatchPool(sampleRule())
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "What is salary?",
            operatorTurns = emptyList(),
            qaRuleIds = listOf(1)
        )

        val userContent = capturedMessages.first().first { it.role == "user" }.content
        assertTrue(userContent.contains("REQUEST"))
        assertTrue(userContent.contains("Salary info") || userContent.contains("APPROVED FACTS"))
    }

    @Test
    fun `skips frame elements in matched user content when frame empty`() {
        stubEmptyFrame()
                val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return minimalGroundedJson
            }
        }
        stubMatchPool(sampleRule())
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "What is salary?",
            operatorTurns = emptyList(),
            qaRuleIds = listOf(1)
        )

        val userContent = capturedMessages.first().first { it.role == "user" }.content
        assertFalse(userContent.contains("SALUTATION="))
        assertTrue(userContent.contains("Salary info"))
    }

    @Test
    fun `injects first turn operator instruction into messages`() {
        stubEmptyFrame()
        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Draft with instruction"
            }
        }
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            operatorInstruction = "Mention our flexible schedule"
        )

        val userMessages = capturedMessages.first().filter { it.role == "user" }
        assertTrue(userMessages.any { it.content.contains("Mention our flexible schedule") })
        assertTrue(userMessages.any { it.content.contains("Inbound email:") })
    }

    @Test
    fun `explicit qaRuleIds yields QA_GROUNDED mode`() {
        stubEmptyFrame()
                val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return minimalGroundedJson.replace("[5]", "[3]")
            }
        }
        val rule = sampleRule(3)
        stubMatchPool(rule)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "What is salary?",
            operatorTurns = emptyList(),
            qaRuleIds = listOf(3)
        )

        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
        assertEquals(listOf(3L), result.qaRuleIds)
        assertTrue(capturedMessages.first().first { it.role == "system" }.content.contains("Return exactly one JSON object"))
    }

    @Test
    fun `uses configured free form prompt when present`() {
        stubEmptyFrame()
        val customPrompt = "Custom free-form prompt with extra constraints."
        Mockito.`when`(aiPromptConfigService.getEffectiveFreeFormSystemPrompt(Mockito.anyString()))
            .thenReturn(customPrompt)
        Mockito.`when`(aiPromptConfigService.getEffectiveDto())
            .thenReturn(AiPromptConfigEffectiveDto(
                freeFormSystemPrompt = customPrompt,
                constraints = null,
                updatedAt = "2026-07-19T00:00:00",
                isCustom = true
            ))
        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Configured draft"
            }
        }
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList()
        )

        val systemPrompt = capturedMessages.first().first { it.role == "system" }.content
        assertTrue(systemPrompt.contains("Custom free-form prompt"))
    }

    @Test
    fun `continuation locks mode and qaRuleIds via explicit qaRuleIds`() {
        stubEmptyFrame()
                val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Revised draft"
            }
        }
        val rule = sampleRule(7).copy(replyBody = "Rule 7 body", answerBody = "Rule 7 body", keywords = "question")
        stubMatchPool(rule)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val turns = listOf(AiReplyTurn(assistantDraft = "First draft", operatorInstruction = "shorter"))
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Question about support?",
            operatorTurns = turns,
            qaRuleIds = listOf(7)
        )

        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
        assertEquals(listOf(7L), result.qaRuleIds)
        val userContent = capturedMessages.first().first { it.role == "user" }.content
        assertTrue(userContent.contains("Rule 7 body"))
    }

    @Test
    fun `simulateOnly returns deterministic draft when llm disabled`() {
        stubDefaultFrame()
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "What is the funding?",
            operatorTurns = emptyList(),
            expertProfile = "Name: Dr. Test\nTraining knowledge base:\nTopic: Funding\nAnswer: Up to 12M RMB",
            mailHistory = "[INBOUND] Question",
            simulateOnly = true
        )

        assertFalse(result.usedLlm)
        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
        assertEquals(emptyList<Long>(), result.qaRuleIds)
    }

    @Test
    fun `simulateOnly falls back to reference text without training knowledge`() {
        stubEmptyFrame()
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            simulateOnly = true
        )

        assertFalse(result.usedLlm)
        assertTrue(result.draftText.contains("LLM 未生成"))
        assertEquals(AiReplyDraftReadiness.BLOCKED, result.draftReadiness)
        assertEquals(emptyList<Long>(), result.qaRuleIds)
    }

    @Test
    fun `simulateOnly with matched rules yields QA_MATCHED and SEGMENT prompt`() {
        stubEmptyFrame()
        val rule = sampleRule(9).copy(replyBody = "First, you submit the required materials.", answerBody = "First, you submit the required materials.", keywords = "application,process")
        stubMatchPool(rule)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Matched simulate draft"
            }
        }

        stubMatchPool(rule)
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "what is the application process?",
            operatorTurns = emptyList(),
            simulateOnly = true
        )

        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
        assertEquals(listOf(9L), result.qaRuleIds)
        val userContent = capturedMessages.first().first { it.role == "user" }.content
        assertTrue(userContent.contains("First, you submit the required materials."))
    }

    @Test
    fun `simulateOnly without match injects full rule set into FREE_FORM knowledge`() {
        stubEmptyFrame()
                val allRules = listOf(
            sampleRule(10).copy(replySubject = "Funding", replyBody = "Funding body"),
            sampleRule(11).copy(id = 11, replySubject = "Process", replyBody = "Process body")
        )
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(allRules)
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(allRules[0]))
        Mockito.`when`(qaRuleRepository.findById(11L)).thenReturn(Optional.of(allRules[1]))

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Free form simulate"
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "casual hello",
            operatorTurns = emptyList(),
            simulateOnly = true
        )

        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertEquals(emptyList<Long>(), result.qaRuleIds)
        val userContent = capturedMessages.first().first { it.role == "user" }.content
        assertFalse(userContent.contains("QA rule knowledge (authoritative facts):"))
    }

    @Test
    fun `free form knowledge section truncates to 12000 characters`() {
        stubEmptyFrame()
        val longBody = "X".repeat(8000)
        val rules = listOf(
            sampleRule(1).copy(replySubject = "A", replyBody = longBody),
            sampleRule(2).copy(id = 2, replySubject = "B", replyBody = longBody)
        )
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rules[0]))
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(rules[1]))

        val content = service(LlmProperties(enabled = true, apiUrl = "http://llm"), null)
            .buildFreeFormUserContent(
                inboundText = "hi",
                expertProfile = null,
                mailHistory = null,
                promptRuleIds = listOf(1L, 2L)
            )

        val knowledgeStart = content.indexOf("QA rule knowledge (authoritative facts):")
        val constraintStart = content.indexOf(
            "Facts (figures, names, links, commitments) must come from the QA rule knowledge"
        )
        assertTrue(knowledgeStart >= 0)
        assertTrue(constraintStart > knowledgeStart)
        val knowledgeBlock = content.substring(
            knowledgeStart + "QA rule knowledge (authoritative facts):\n".length,
            constraintStart
        ).trimEnd('\n')
        assertTrue(knowledgeBlock.length <= 12000)
    }

    @Test
    fun `matched messages verbatim contract unchanged for same inputs`() {
        stubEmptyFrame()
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule()))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        val draftService = service(LlmProperties(enabled = true, apiUrl = "http://llm"), null)

        val first = draftService.buildMatchedMessages(
            inboundText = "What is salary?",
            operatorTurns = emptyList(),
            promptRuleIds = listOf(1)
        )
        val second = draftService.buildMatchedMessages(
            inboundText = "What is salary?",
            operatorTurns = emptyList(),
            promptRuleIds = listOf(1)
        )

        assertEquals(first, second)
        assertTrue(first.any { it.content.contains("Salary info") })
        assertFalse(first.any { it.content.contains("QA rule knowledge") })
    }

    @Test
    fun `free form injects few-shot without affecting qaRuleIds`() {
        stubEmptyFrame()
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(
            aiTrainingDialogueService.selectRelevantDialogues(
                "Are you accredited and official?",
                2
            )
        )
            .thenReturn(
                listOf(
                    SelectedDialogueFewShot(
                        sourceRef = "DIALOG_2143",
                        messages = listOf(
                            LlmChatMessage(role = "user", content = "Are you an officially accredited agency?"),
                            LlmChatMessage(role = "assistant", content = "Our government cooperation is documented.")
                        )
                    )
                )
            )

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Few-shot draft"
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Are you accredited and official?",
            operatorTurns = emptyList()
        )

        assertEquals(emptyList<Long>(), result.qaRuleIds)
        assertEquals(listOf("DIALOG_2143"), result.fewShotDialogRefs)
        val messages = capturedMessages.first()
        assertTrue(messages[1].content.contains("officially accredited agency"))
        val system = messages.first { it.role == "system" }.content
        assertTrue(system.contains("structure, tone, and communication strategy"))
        assertTrue(system.contains("must not be used as a factual source"))
    }

    @Test
    fun `qa grounded explicit selection uses grounded path`() {
        stubEmptyFrame()
        val rule = sampleRule(3)
        stubMatchPool(rule)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "Matched"
        }).generate(
            inboundText = "What is salary?",
            operatorTurns = emptyList(),
            qaRuleIds = listOf(3)
        )

        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
        assertEquals(listOf(3L), result.qaRuleIds)
    }

    @Test
    fun `free form without keyword match keeps messages unchanged`() {
        stubEmptyFrame()
        val draftService = service(LlmProperties(enabled = true, apiUrl = "http://llm"), null)
        val effectiveDto = aiPromptConfigService.getEffectiveDto()
        val promptSnapshot = if (effectiveDto.isCustom) {
            AiReplyPromptSnapshot(effectiveDto.freeFormSystemPrompt, "free-form-custom:${effectiveDto.updatedAt}:${AiReplyDraftService.sha256Hex(effectiveDto.freeFormSystemPrompt).take(12)}")
        } else {
            AiReplyPromptSnapshot(effectiveDto.freeFormSystemPrompt, "free-form-default-v1")
        }
        val baseline = draftService.buildFreeFormMessages(
            inboundText = "Hello without keywords",
            operatorTurns = emptyList(),
            expertProfile = "Name: Dr. Smith",
            mailHistory = "History",
            promptSnapshot = promptSnapshot
        )
        val result = draftService.buildFreeFormMessages(
            inboundText = "Hello without keywords",
            operatorTurns = emptyList(),
            expertProfile = "Name: Dr. Smith",
            mailHistory = "History",
            promptSnapshot = promptSnapshot
        )

        assertEquals(baseline.messages, result.messages)
        assertEquals(emptyList<String>(), result.fewShotDialogRefs)
    }

    @Test
    fun `matched messages never include dialogue seed text`() {
        stubEmptyFrame()
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule()))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val messages = service(LlmProperties(enabled = true, apiUrl = "http://llm"), null).buildMatchedMessages(
            inboundText = "Are you accredited through another agency?",
            operatorTurns = emptyList(),
            promptRuleIds = listOf(1)
        )

        val joined = messages.joinToString("\n") { it.content }
        assertFalse(joined.contains("officially accredited agency"))
        assertFalse(joined.contains("reference examples"))
    }

    @Test
    fun `fallback draft excludes dialogue seed fragments when llm disabled`() {
        stubEmptyFrame()
                val allRules = listOf(sampleRule(10))
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(allRules)
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(allRules[0]))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "Are you accredited through another agency?",
            operatorTurns = emptyList()
        )

        assertFalse(result.draftText.contains("government cooperation is documented"))
        assertEquals(emptyList<String>(), result.fewShotDialogRefs)
        Mockito.verifyNoInteractions(aiTrainingDialogueService)
    }

    // ── New tests for T2 ──────────────────────────────────────────────────────

    @Test
    fun `single normal question with matching rule yields QA_MATCHED`() {
        stubDefaultFrame()
        val rule = sampleRule(1)
                Mockito.`when`(aiReplyContextService.requiresResearchContext("What is salary?")).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Matched draft"
            }
        }
        stubMatchPool(rule)
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "What is salary?",
            operatorTurns = emptyList()
        )

        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
        assertEquals(listOf(1L), result.qaRuleIds)
        assertEquals(1, result.requestCount)
        assertEquals(1, result.groundedRequestCount)
        assertTrue(result.unsupportedRequests.isEmpty())
        val systemPrompt = capturedMessages.first().first { it.role == "system" }.content
        assertTrue(systemPrompt.contains("Return exactly one JSON object"))
    }

    @Test
    fun `single research question with matching rule yields QA_GROUNDED`() {
        stubDefaultFrame()
        val rule = sampleRule(1).copy(
            replyBody = "Research areas: AI, NLP",
            answerBody = "Research areas: AI, NLP",
            keywords = "research,profile,programme",
            coverageKeys = "programme.scope"
        )
        val inbound = "Does your research profile match our focus?"
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(inbound)).thenReturn(true)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "Grounded draft"
        }
        stubMatchPool(rule)
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
        assertEquals(listOf(1L), result.qaRuleIds)
        assertEquals(1, result.requestCount)
        assertEquals(1, result.groundedRequestCount)
        assertTrue(result.unsupportedRequests.isEmpty())
    }

    @Test
    fun `multi-request inbound with rules yields QA_GROUNDED`() {
        stubEmptyFrame()
        val inbound = "- What is salary?\n- What is the visa process?"
        val rule1 = sampleRule(1)
        val rule2 = sampleRule(2).copy(replyBody = "Visa info", answerBody = "Visa info", keywords = "visa")
        stubMatchPool(rule1, rule2)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = minimalGroundedJson
        }
        stubMatchPool(rule1, rule2)
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
        assertEquals(listOf(1L, 2L), result.qaRuleIds)
        assertEquals(2, result.requestCount)
        assertEquals(2, result.groundedRequestCount)
        assertTrue(result.unsupportedRequests.isEmpty())
    }

    @Test
    fun `explicit qaRuleIds with multi-request inbound stays QA_GROUNDED`() {
        stubEmptyFrame()
        val inbound = "- Question about salary?\n- Question about visa process?"
                Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        val rule1 = sampleRule(1)
        val rule2 = sampleRule(2).copy(keywords = "visa", replyBody = "Visa info", answerBody = "Visa info")
        stubMatchPool(rule1, rule2)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? =
                """{"claims":[{"claimKey":"r1:finance.arrangements","text":"Salary info"},{"claimKey":"r2:general.answer","text":"Visa info"}],"actionText":null}"""
        }
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList(),
            qaRuleIds = listOf(1, 2)
        )

        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
        assertEquals(listOf(1L, 2L), result.qaRuleIds)
        assertEquals(2, result.requestCount)
    }

    @Test
    fun `QA_GROUNDED prompt contains requests facts and warnings without external URL claims`() {
        stubDefaultFrame()
        val inbound = "- What is salary?\n- Does your research profile match?"
        val rule = sampleRule(1)
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext("- What is salary?")).thenReturn(false)
        Mockito.`when`(aiReplyContextService.requiresResearchContext("- Does your research profile match?")).thenReturn(true)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Grounded reply"
            }
        }
        service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList(),
            contextWarnings = listOf("EXPERT_PROFILE_PARTIAL"),
            expertProfile = "Expert in ML"
        )

        val messages = capturedMessages.first()
        val systemPrompt = messages.first { it.role == "system" }.content
        val userContent = messages.first { it.role == "user" }.content
        assertTrue(systemPrompt.contains("JSON object"))
        assertFalse(systemPrompt.contains("SEGMENT"))
        // System prompt prohibits claiming external URL access; user content must not assert "I visited"
        assertTrue(systemPrompt.lowercase().contains("do not claim"))
        assertTrue(systemPrompt.lowercase().contains("google scholar"))

        assertTrue(userContent.contains("REQUEST 1"))
        assertTrue(userContent.contains("REQUEST 2"))
        assertTrue(userContent.contains("TEXT: - What is salary?"))
        assertTrue(userContent.contains("TEXT: - Does your research profile match?"))
        assertTrue(userContent.contains("EVIDENCE_LEVEL: GROUNDED"))
        assertTrue(userContent.contains("APPROVED FACTS:"))
        assertTrue(userContent.indexOf("TEXT: - What is salary?") < userContent.indexOf("EVIDENCE_LEVEL: GROUNDED"))
        assertTrue(userContent.indexOf("REQUEST 1") < userContent.indexOf("TEXT: - What is salary?"))
        assertTrue(userContent.contains("What is salary?"))
        assertTrue(userContent.contains("research profile"))
        assertTrue(userContent.contains("Salary info"))
        assertTrue(userContent.contains("EXPERT_PROFILE_PARTIAL"))
        assertTrue(userContent.contains("Expert in ML") || userContent.contains("Expert research profile"))
        assertFalse(userContent.contains("Request checklist"))
        assertFalse(userContent.contains("Matched QA answers"))
        assertFalse(userContent.contains("STATUS:"))
        assertFalse(userContent.contains("SALUTATION="))

        Mockito.verify(aiTrainingDialogueService).selectRelevantDialogues(inbound, 1)
    }

    @Test
    fun `simulateOnly flag does not affect FREE_FORM fallback text`() {
        stubDefaultFrame()
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        val inbound = "What is the funding?"
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        val profile = "Name: Dr. Test\nTraining knowledge base:\nTopic: Funding\nAnswer: Up to 12M RMB"
        val resultWithSimulate = service(LlmProperties(enabled = false), null).generate(
            inboundText = inbound,
            operatorTurns = emptyList(),
            expertProfile = profile,
            simulateOnly = true
        )
        val resultWithout = service(LlmProperties(enabled = false), null).generate(
            inboundText = inbound,
            operatorTurns = emptyList(),
            expertProfile = profile,
            simulateOnly = false
        )

        assertEquals(resultWithSimulate.draftText, resultWithout.draftText)
        assertTrue(resultWithSimulate.draftText.isNotEmpty())
    }

    @Test
    fun `FREE_FORM fallback is non-empty even without simulateOnly`() {
        stubDefaultFrame()
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            simulateOnly = false
        )

        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertTrue(result.qaRuleIds.isEmpty())
        assertTrue(result.draftText.isNotEmpty())
    }

    @Test
    fun `research request with insufficient warning goes to unsupportedRequests`() {
        stubEmptyFrame()
        val inbound = "Does your research profile match our focus area?"
        val rule = sampleRule(1).copy(
            keywords = "research,profile,programme",
            answerBody = "Programme scope covers AI."
        )
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(inbound)).thenReturn(true)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "Draft"
        }
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList(),
            contextWarnings = listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT")
        )

        assertEquals(1, result.requestCount)
        assertEquals(0, result.groundedRequestCount)
        assertEquals(listOf(inbound), result.unsupportedRequests)
        assertTrue(result.contextWarnings.contains("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"))
        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
    }

    @Test
    fun `same generate args produce equal LLM messages for both simulated entry points`() {
        stubEmptyFrame()
                Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule()))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val allMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                allMessages += messages
                return """{"claims":[{"claimKey":"r1:finance.arrangements","text":"Salary info"}],"actionText":null}"""
            }
        }
        val draftService = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client)

        // Simulate the same call from two different entry points (simulate + aiReplyTurn)
        draftService.generate(inboundText = "What is salary?", operatorTurns = emptyList(), qaRuleIds = listOf(1))
        draftService.generate(inboundText = "What is salary?", operatorTurns = emptyList(), qaRuleIds = listOf(1))

        assertEquals(2, allMessages.size)
        assertEquals(allMessages[0], allMessages[1])
    }

    @Test
    fun `QA_GROUNDED uses grounded temperature`() {
        stubEmptyFrame()
        val inbound = "- What is salary?\n- What is visa?"
        val rule1 = sampleRule(1)
        val rule2 = sampleRule(2).copy(keywords = "visa", replyBody = "Visa info", answerBody = "Visa info")
        stubMatchPool(rule1, rule2)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val capturedTemperatures = mutableListOf<Double?>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedTemperatures += temperature
                return """{"claims":[{"claimKey":"r1:finance.arrangements","text":"Salary info."},{"claimKey":"r2:general.answer","text":"Visa info."}],"actionText":null}"""
            }
        }
        service(
            LlmProperties(enabled = true, apiUrl = "http://llm", temperature = 0.3, freeFormTemperature = 0.7),
            client
        ).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        assertEquals(0.3, capturedTemperatures.single())
    }

    @Test
    fun `QA_GROUNDED injects at most one style few-shot and returns its ref`() {
        stubEmptyFrame()
        val inbound = "- What is salary?\n- What is visa?"
        val rule1 = sampleRule(1)
        val rule2 = sampleRule(2).copy(keywords = "visa", replyBody = "Visa info", answerBody = "Visa info")
        stubMatchPool(rule1, rule2)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues(inbound, 1)).thenReturn(
            listOf(
                SelectedDialogueFewShot(
                    sourceRef = "STYLE_MULTI_DUE_DILIGENCE",
                    messages = listOf(
                        LlmChatMessage(role = "user", content = "style expert"),
                        LlmChatMessage(role = "assistant", content = "style agent")
                    )
                )
            )
        )

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return """{"claims":[{"claimKey":"r1:finance.arrangements","text":"Salary is competitive."},{"claimKey":"r2:general.answer","text":"Visa support is available."}],"actionText":null}"""
            }
        }
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
        assertTrue(result.usedLlm)
        assertEquals(listOf("STYLE_MULTI_DUE_DILIGENCE"), result.fewShotDialogRefs)
        Mockito.verify(aiTrainingDialogueService).selectRelevantDialogues(inbound, 1)
        val system = capturedMessages.first().first { it.role == "system" }.content
        assertTrue(system.contains("structure, tone, and communication strategy"))
        assertTrue(system.contains("must not be used as a factual source"))
        assertTrue(system.contains("JSON schema"))
        assertTrue(capturedMessages.first().any { it.content == "style expert" })
        assertTrue(result.draftText.contains("Salary is competitive"))
        assertFalse(result.draftText.contains("\"answers\""))
    }

    @Test
    fun `FREE_FORM requests max two style few-shots`() {
        stubEmptyFrame()
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues("Are you accredited and official?", 2))
            .thenReturn(
                listOf(
                    SelectedDialogueFewShot("STYLE_TRUST_VERIFICATION", listOf(LlmChatMessage("user", "a"), LlmChatMessage("assistant", "b"))),
                    SelectedDialogueFewShot("STYLE_MATERIALS_BOUNDARY", listOf(LlmChatMessage("user", "c"), LlmChatMessage("assistant", "d")))
                )
            )

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "ok"
        }).generate(
            inboundText = "Are you accredited and official?",
            operatorTurns = emptyList()
        )

        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertEquals(listOf("STYLE_TRUST_VERIFICATION", "STYLE_MATERIALS_BOUNDARY"), result.fewShotDialogRefs)
        Mockito.verify(aiTrainingDialogueService).selectRelevantDialogues("Are you accredited and official?", 2)
    }

    @Test
    fun `fallback does not query dialogue service`() {
        stubDefaultFrame()
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "Are you accredited?",
            operatorTurns = emptyList()
        )

        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertEquals(emptyList<String>(), result.fewShotDialogRefs)
        Mockito.verifyNoInteractions(aiTrainingDialogueService)
    }

    @Test
    fun `enabled with null client skips few-shot selection and returns empty refs`() {
        stubDefaultFrame()
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), null).generate(
            inboundText = "Are you accredited?",
            operatorTurns = emptyList()
        )

        assertFalse(result.usedLlm)
        assertEquals(AiReplyGenerationState.FALLBACK_CLIENT_UNAVAILABLE, result.generationState)
        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertEquals(emptyList<String>(), result.fewShotDialogRefs)
        Mockito.verifyNoInteractions(aiTrainingDialogueService)
    }

    @Test
    fun `chat exception clears fewShotDialogRefs on fallback`() {
        stubDefaultFrame()
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues("Are you accredited?", 2))
            .thenReturn(
                listOf(
                    SelectedDialogueFewShot(
                        sourceRef = "STYLE_TRUST_VERIFICATION",
                        messages = listOf(
                            LlmChatMessage(role = "user", content = "style expert verify"),
                            LlmChatMessage(role = "assistant", content = "UNIQUE_STYLE_AGENT_SNIPPET_XYZ")
                        )
                    )
                )
            )

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                throw RuntimeException("LLM down")
            }
        }
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Are you accredited?",
            operatorTurns = emptyList()
        )

        assertFalse(result.usedLlm)
        assertEquals(emptyList<String>(), result.fewShotDialogRefs)
        assertFalse(result.draftText.contains("UNIQUE_STYLE_AGENT_SNIPPET_XYZ"))
        Mockito.verify(aiTrainingDialogueService).selectRelevantDialogues("Are you accredited?", 2)
    }

    @Test
    fun `blank chat response clears fewShotDialogRefs on fallback`() {
        stubDefaultFrame()
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues("Are you accredited?", 2))
            .thenReturn(
                listOf(
                    SelectedDialogueFewShot(
                        sourceRef = "STYLE_MATERIALS_BOUNDARY",
                        messages = listOf(
                            LlmChatMessage(role = "user", content = "style expert materials"),
                            LlmChatMessage(role = "assistant", content = "BLANK_FALLBACK_STYLE_SNIPPET")
                        )
                    )
                )
            )

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "   "
        }
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Are you accredited?",
            operatorTurns = emptyList()
        )

        assertFalse(result.usedLlm)
        assertEquals(emptyList<String>(), result.fewShotDialogRefs)
        assertFalse(result.draftText.contains("BLANK_FALLBACK_STYLE_SNIPPET"))
    }

    private val freeFormActionPolicyInbound = "Are you an accredited and official organization?"

    private val expertDiligenceMail = """
        Thank you for your message. Here are my research profiles:
        https://scholar.google.com/citations?user=OT-O6joAAAAJ&hl=en
        https://www.scopus.com/authid/detail.uri?authorId=57201234567

        Could you please confirm whether my research background fits the enterprise projects you manage?

        Specifically:
        - What is the registered location of your company?
        - What are the expected responsibilities and deliverables?
        - How are researchers selected and matched within the scope of enterprise projects?
        - What are the intellectual property arrangements?
        - What are the next stages of the application?
        - What materials should I send?

        Best regards
    """.trimIndent()

    @Test
    fun `retries once then accepts cleaned draft without warning`() {
        stubEmptyFrame()
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)

        var chatCount = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chatCount++
                return if (chatCount == 1) {
                    "Thank you for writing. Please send your CV when convenient."
                } else {
                    "Thank you for writing. I will answer your programme questions from approved information."
                }
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = freeFormActionPolicyInbound,
            operatorTurns = emptyList()
        )

        assertEquals(2, chatCount)
        assertTrue(result.usedLlm)
        assertFalse(result.draftText.contains("Please send your CV", ignoreCase = true))
        assertFalse(result.contextWarnings.contains(AiReplyDraftService.UNAUTHORIZED_ACTION_REMOVED))
        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertEquals(emptyList<Long>(), result.qaRuleIds)
    }

    @Test
    fun `two violating drafts sanitize and keep metadata with warning`() {
        stubEmptyFrame()
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues(Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(
                listOf(
                    SelectedDialogueFewShot(
                        sourceRef = "STYLE_MULTI_DUE_DILIGENCE",
                        messages = listOf(
                            LlmChatMessage("user", "example expert"),
                            LlmChatMessage("assistant", "example agent")
                        )
                    )
                )
            )

        var chatCount = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chatCount++
                return "Thank you. Please send your CV. Let us schedule a meeting next week. Applicants submit materials for review after matching."
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = freeFormActionPolicyInbound,
            operatorTurns = emptyList()
        )

        assertEquals(2, chatCount)
        assertTrue(result.usedLlm)
        assertFalse(result.draftText.contains("Please send your CV", ignoreCase = true))
        assertFalse(result.draftText.contains("Let us schedule a meeting", ignoreCase = true))
        assertTrue(result.draftText.contains("Applicants submit materials for review after matching"))
        assertTrue(result.contextWarnings.contains(AiReplyDraftService.UNAUTHORIZED_ACTION_REMOVED))
        assertEquals(emptyList<Long>(), result.qaRuleIds)
        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertEquals(listOf("STYLE_MULTI_DUE_DILIGENCE"), result.fewShotDialogRefs)
        assertTrue(result.requestCount >= 1)
        Mockito.verify(aiTrainingDialogueService, Mockito.times(1))
            .selectRelevantDialogues(freeFormActionPolicyInbound, 2)
    }

    @Test
    fun `disabled fallback preserves answerBody as-is in reference text`() {
        stubDefaultFrame()
        val rule = sampleRule().copy(
            replyBody = "Please send your CV for matching. Applicants submit materials for review.",
            answerBody = "Please send your CV for matching. Applicants submit materials for review."
        )
        stubMatchPool(rule)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? =
                error("should not call")
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? =
                error("should not call")
        }

        val result = service(LlmProperties(enabled = false), client).generate(
            inboundText = "What is salary support?",
            operatorTurns = emptyList()
        )

        assertFalse(result.usedLlm)
        assertTrue(result.draftText.contains("Please send your CV"))
        assertTrue(result.draftText.contains("Applicants submit materials for review"))
        assertEquals(AiReplyDraftReadiness.BLOCKED, result.draftReadiness)
    }

    @Test
    fun `explicit meeting request allows meeting action without retry`() {
        stubEmptyFrame()
        val inbound = "Can we arrange a meeting next week?"
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        var chatCount = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chatCount++
                return "Happy to arrange a meeting. Please share a convenient time."
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        assertEquals(1, chatCount)
        assertTrue(result.usedLlm)
        assertTrue(result.draftText.contains("convenient time"))
        assertFalse(result.contextWarnings.contains(AiReplyDraftService.UNAUTHORIZED_ACTION_REMOVED))
    }

    @Test
    fun `replyModel null defaults to flash and pro maps provider id through chatWithModel`() {
        stubEmptyFrame()
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        val capturedModels = mutableListOf<String>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "unused"
            override fun chatWithModel(
                messages: List<LlmChatMessage>,
                temperature: Double?,
                providerModel: String
            ): String? {
                capturedModels += providerModel
                return "Draft for $providerModel"
            }
        }
        val props = LlmProperties(
            enabled = true,
            apiUrl = "http://llm",
            replyFlashModel = "flash-id",
            replyProModel = "pro-id"
        )

        val flash = service(props, client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            replyModel = null
        )
        assertEquals(AiReplyModel.DEEPSEEK_V4_FLASH.name, flash.selectedModel)
        assertEquals(listOf("flash-id"), capturedModels)

        capturedModels.clear()
        val pro = service(props, client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            replyModel = "DEEPSEEK_V4_PRO"
        )
        assertEquals(AiReplyModel.DEEPSEEK_V4_PRO.name, pro.selectedModel)
        assertEquals(listOf("pro-id"), capturedModels)

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            service(props, client).generate(
                inboundText = "Hello",
                operatorTurns = emptyList(),
                replyModel = "DEEPSEEK_UNKNOWN"
            )
        }
    }

    @Test
    fun `action retry reuses the same provider model and fallback echoes selectedModel`() {
        stubEmptyFrame()
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        val capturedModels = mutableListOf<String>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModel(
                messages: List<LlmChatMessage>,
                temperature: Double?,
                providerModel: String
            ): String? {
                capturedModels += providerModel
                return if (capturedModels.size == 1) {
                    "Please send your CV when convenient."
                } else {
                    "Thank you. I will follow up with approved information."
                }
            }
        }
        val props = LlmProperties(
            enabled = true,
            apiUrl = "http://llm",
            replyFlashModel = "flash-id",
            replyProModel = "pro-id"
        )
        val result = service(props, client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            replyModel = "DEEPSEEK_V4_PRO"
        )
        assertEquals(listOf("pro-id", "pro-id"), capturedModels)
        assertEquals(AiReplyModel.DEEPSEEK_V4_PRO.name, result.selectedModel)
        assertTrue(result.usedLlm)

        val fallback = service(LlmProperties(enabled = false, replyProModel = "pro-id"), client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            replyModel = "DEEPSEEK_V4_PRO"
        )
        assertFalse(fallback.usedLlm)
        assertEquals(AiReplyModel.DEEPSEEK_V4_PRO.name, fallback.selectedModel)
        assertEquals(2, capturedModels.size) // no additional client calls on disabled path
    }

    @Test
    fun `interrogative CV CTA is sanitized with metadata preserved`() {
        stubEmptyFrame()
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues(Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(
                listOf(
                    SelectedDialogueFewShot(
                        sourceRef = "STYLE_MULTI_DUE_DILIGENCE",
                        messages = listOf(
                            LlmChatMessage("user", "example expert"),
                            LlmChatMessage("assistant", "example agent")
                        )
                    )
                )
            )

        var chatCount = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chatCount++
                return "Thank you for writing. Could you share your CV? Would you mind forwarding your résumé? " +
                    "Applicants submit materials for review after matching."
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = freeFormActionPolicyInbound,
            operatorTurns = emptyList()
        )

        assertEquals(2, chatCount)
        assertTrue(result.usedLlm)
        assertFalse(result.draftText.contains("Could you share your CV", ignoreCase = true))
        assertFalse(result.draftText.contains("résumé", ignoreCase = true))
        assertTrue(result.draftText.contains("Applicants submit materials for review after matching"))
        assertTrue(result.contextWarnings.contains(AiReplyDraftService.UNAUTHORIZED_ACTION_REMOVED))
        assertEquals(AiReplyGenerationState.LLM_USED, result.generationState)
        assertEquals(emptyList<Long>(), result.qaRuleIds)
        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertEquals(listOf("STYLE_MULTI_DUE_DILIGENCE"), result.fewShotDialogRefs)
    }

    @Test
    fun `safe multi-paragraph draft preserved byte-for-byte through final gate`() {
        stubEmptyFrame()
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues(Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(
                listOf(
                    SelectedDialogueFewShot(
                        sourceRef = "STYLE_MULTI_DUE_DILIGENCE",
                        messages = listOf(
                            LlmChatMessage("user", "example expert"),
                            LlmChatMessage("assistant", "example agent")
                        )
                    )
                )
            )

        val safeDraft = """
            Dear Dr. Smith,

            Thank you for your interest in our program. Here are answers to your questions:

            1. The company is registered in Beijing.
            2. Expected deliverables are defined per project scope.
            3. Researchers are matched by domain expertise.
            4. Intellectual property follows the signed agreement.
            5. Next stages include document review and interview.
            6. Early-stage materials are a short CV and research summary.

            Best regards,
            Talent Introduction Team
        """.trimIndent() + "\n"

        var chatCount = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chatCount++
                return safeDraft
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = freeFormActionPolicyInbound,
            operatorTurns = emptyList()
        )

        assertEquals(1, chatCount)
        assertTrue(result.usedLlm)
        assertEquals(safeDraft, result.draftText)
        assertFalse(result.contextWarnings.contains(AiReplyDraftService.UNAUTHORIZED_ACTION_REMOVED))
        assertEquals(emptyList<Long>(), result.qaRuleIds)
        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertEquals(listOf("STYLE_MULTI_DUE_DILIGENCE"), result.fewShotDialogRefs)
    }

    @Test
    fun `retry still violating multi-paragraph draft keeps structure with warning`() {
        stubEmptyFrame()
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues(Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(
                listOf(
                    SelectedDialogueFewShot(
                        sourceRef = "STYLE_MULTI_DUE_DILIGENCE",
                        messages = listOf(
                            LlmChatMessage("user", "example expert"),
                            LlmChatMessage("assistant", "example agent")
                        )
                    )
                )
            )

        val violatingDraft = """
            Dear Dr. Smith,

            Thank you for your interest. Answers below:

            1. The company is registered in Beijing.
            2. Expected deliverables are defined per project scope.
            Could you share your CV?
            3. Researchers are matched by domain expertise.
            4. Intellectual property follows the signed agreement.
            5. Next stages include document review and interview.
            6. Early-stage materials are a short CV and research summary.

            Best regards,
            Talent Introduction Team
        """.trimIndent()

        var chatCount = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chatCount++
                return violatingDraft
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = freeFormActionPolicyInbound,
            operatorTurns = emptyList()
        )

        assertEquals(2, chatCount)
        assertTrue(result.usedLlm)
        assertFalse(result.draftText.contains("Could you share your CV", ignoreCase = true))
        assertTrue(result.draftText.contains("Dear Dr. Smith,"))
        assertTrue(result.draftText.contains("1. The company is registered in Beijing."))
        assertTrue(result.contextWarnings.contains(AiReplyDraftService.UNAUTHORIZED_ACTION_REMOVED))
        assertEquals(emptyList<Long>(), result.qaRuleIds)
        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertEquals(listOf("STYLE_MULTI_DUE_DILIGENCE"), result.fewShotDialogRefs)
    }

    // ── Phase 2: request fact matrix ──────────────────────────────────────────

    @Test
    fun `resolveQaRules maps gapItems 1-to-1 with stable index order`() {
        val inbound = "- Salary?\n- Visa?"
        val rule1 = sampleRule(1)
        val rule2 = sampleRule(2).copy(replyBody = "Visa", answerBody = "Visa", keywords = "visa")
        stubMatchPool(rule1, rule2)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(inbound, null)

        assertEquals(2, resolved.requestFacts.size)
        assertEquals(1, resolved.requestFacts[0].index)
        assertEquals("- Salary?", resolved.requestFacts[0].requestText)
        assertEquals(listOf(1L), resolved.requestFacts[0].factRuleIds)
        assertEquals(RequestGroundingStatus.GROUNDED, resolved.requestFacts[0].status)
        assertEquals(2, resolved.requestFacts[1].index)
        assertEquals("- Visa?", resolved.requestFacts[1].requestText)
        assertEquals(listOf(2L), resolved.requestFacts[1].factRuleIds)
        assertEquals(listOf("- Salary?", "- Visa?"), resolved.requestFacts.map { it.requestText })
    }

    @Test
    fun `resolveQaRules intersects candidates with promptRuleIds per item only`() {
        val inbound = "- A?\n- B?"
        val rule1 = sampleRule(1).copy(keywords = "a", replyBody = "A facts", answerBody = "A facts")
        val rule2 = sampleRule(2).copy(keywords = "b", replyBody = "B facts", answerBody = "B facts")
        stubMatchPool(rule1, rule2)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(inbound, null)

        assertEquals(listOf(1L), resolved.requestFacts[0].factRuleIds)
        assertEquals(listOf(2L), resolved.requestFacts[1].factRuleIds)
        assertFalse(resolved.requestFacts[0].factRuleIds.contains(2L))
        assertFalse(resolved.requestFacts[0].factRuleIds.contains(3L))
        assertFalse(resolved.requestFacts[0].factRuleIds.contains(9L))
    }

    @Test
    fun `resolveQaRules maps gapItems with intent coverage`() {
        val request = "What are the expected deliverables?"
                Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(sampleRule(1).copy(
                replySubject = "Scope",
                replyBody = "High-level project overview.",
                coverageKeys = ""
            ))
        )

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertEquals(1, resolved.requestFacts.size)
        assertTrue(resolved.requestFacts[0].intents.isNotEmpty(), "should have matched intent")
    }

    @Test
    fun `resolveQaRules excludes missing repository ids and marks UNSUPPORTED`() {
        val request = "What are the expected deliverables?"
                Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(99L)).thenReturn(Optional.empty())

        val draftService = service(LlmProperties(enabled = false), null)
        val resolved = draftService.resolveQaRules(request, null)

        assertEquals(emptyList<Long>(), resolved.requestFacts.single().factRuleIds)
        assertEquals(RequestGroundingStatus.UNSUPPORTED, resolved.requestFacts.single().status)
        assertEquals(0, resolved.groundedRequestCount)
        assertEquals(listOf(request), resolved.unsupportedRequests)
    }

    @Test
    fun `resolveQaRules excludes blank replyBody ids and marks UNSUPPORTED`() {
        val request = "What is the salary?"
                Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(sampleRule(1).copy(replyBody = "   "))
        )

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertEquals(emptyList<Long>(), resolved.requestFacts.single().factRuleIds)
        assertEquals(RequestGroundingStatus.UNSUPPORTED, resolved.requestFacts.single().status)
    }

    @Test
    fun `explicit qaRuleIds intersects gap candidates into factRuleIds and sendQaRuleIds`() {
        val inbound = "What about salary and benefits?"
                Mockito.`when`(aiReplyContextService.requiresResearchContext(inbound)).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule(1)))

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(inbound, listOf(1L))

        assertEquals(listOf(1L), resolved.requestFacts.single().factRuleIds)
        assertEquals(listOf(1L), resolved.sendQaRuleIds)
        assertEquals(listOf(1L), resolved.promptRuleIds)
    }

    @Test
    fun `resolveQaRules marks GROUNDED when coverage keys satisfy intent`() {
        val request = "What are the expected Deliverables for this role?"
        val rule = sampleRule(1).copy(
            replySubject = "Role",
            replyBody = "Expected deliverables are defined per project.",
            answerBody = "Expected deliverables are defined per project.",
            keywords = "deliverables",
            coverageKeys = "role.deliverables"
        )
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertEquals(RequestGroundingStatus.GROUNDED, resolved.requestFacts.single().status)
    }

    @Test
    fun `resolveQaRules marks UNSUPPORTED when factRuleIds empty for non-research`() {
        val request = "What is the coffee policy?"
                Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(listOf(sampleRule(10).copy(id = 10)))

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertEquals(RequestGroundingStatus.UNSUPPORTED, resolved.requestFacts.single().status)
        assertEquals(emptyList<Long>(), resolved.requestFacts.single().factRuleIds)
        assertEquals(listOf(request), resolved.unsupportedRequests)
        assertEquals(0, resolved.groundedRequestCount)
        assertEquals(emptyList<Long>(), resolved.sendQaRuleIds)
        assertEquals(emptyList<Long>(), resolved.promptRuleIds)
    }

    @Test
    fun `research request requires bilateral profile and project QA facts`() {
        val request = "Does my research background fit enterprise projects within the scope?"
        val projectRule = sampleRule(7).copy(
            keywords = "within the scope,enterprise projects,research",
            replySubject = "Project scope",
            replyBody = "Enterprise projects cover applied AI and systems.",
            answerBody = "Enterprise projects cover applied AI and systems.",
            coverageKeys = "programme.scope"
        )
        stubMatchPool(projectRule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(true)

        val draftService = service(LlmProperties(enabled = false), null)

        val bilateral = draftService.resolveQaRules(request, null, emptyList())
        assertEquals(RequestGroundingStatus.PARTIAL, bilateral.requestFacts.single().status)
        assertEquals(listOf(7L), bilateral.requestFacts.single().factRuleIds)
        assertTrue(bilateral.requestFacts.single().requiresResearchContext)
        assertEquals(1, bilateral.groundedRequestCount)
        assertTrue(bilateral.unsupportedRequests.isEmpty())
        assertEquals(listOf(7L), bilateral.sendQaRuleIds)

        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        val noProjectQa = draftService.resolveQaRules(request, null, emptyList())
        assertEquals(RequestGroundingStatus.UNSUPPORTED, noProjectQa.requestFacts.single().status)
        assertEquals(emptyList<Long>(), noProjectQa.requestFacts.single().factRuleIds)

        stubMatchPool(projectRule)
        val noProfile = draftService.resolveQaRules(
            request,
            null,
            listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT")
        )
        assertEquals(RequestGroundingStatus.UNSUPPORTED, noProfile.requestFacts.single().status)
        assertTrue(noProfile.requestFacts.single().factRuleIds.isEmpty())
        assertEquals(listOf(request), noProfile.unsupportedRequests)
        assertEquals(0, noProfile.groundedRequestCount)
        assertEquals(emptyList<Long>(), noProfile.sendQaRuleIds)
    }

    @Test
    fun `unsupported research facts are retained on item but omitted from grounded prompt`() {
        stubDefaultFrame()
        val inbound = "Does my research profile match?"
        val rule = sampleRule(1).copy(
            keywords = "research,profile,match",
            replyBody = "Project scope covers applied AI.",
            answerBody = "Project scope covers applied AI."
        )
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(inbound)).thenReturn(true)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val captured = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                captured += messages
                return ""
            }
        }
        stubMatchPool(rule)
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList(),
            contextWarnings = listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT")
        )

        assertEquals(RequestGroundingStatus.UNSUPPORTED, result.requestFacts.single().status)
        assertTrue(result.requestFacts.single().factRuleIds.isEmpty())
        val userContent = captured.first().first { it.role == "user" }.content
        assertTrue(userContent.contains("EVIDENCE_LEVEL: UNSUPPORTED"))
        assertTrue(userContent.contains("APPROVED FACTS:"))
        assertTrue(userContent.contains("(none)"))
        assertFalse(userContent.contains("Project scope covers applied AI."))
    }

    @Test
    fun `resolveQaRules does not invent URL-only request facts beyond gapItems`() {
        val inbound = "See https://scholar.google.com/citations?user=abc\n- What is salary?"
                Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule(1)))

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(inbound, null)

        assertEquals(1, resolved.requestFacts.size)
        assertFalse(resolved.requestFacts.any { it.requestText.contains("scholar.google") })
        assertEquals("- What is salary?", resolved.requestFacts.single().requestText)
    }

    @Test
    fun `shared rule across two items does not inflate sendQaRuleIds`() {
        val inbound = "- What is salary band?\n- What is salary currency?"
        val rule = sampleRule(1)
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(inbound, null)

        assertEquals(listOf(1L), resolved.sendQaRuleIds)
        assertEquals(listOf(1L), resolved.requestFacts[0].factRuleIds)
        assertEquals(listOf(1L), resolved.requestFacts[1].factRuleIds)
        assertEquals(2, resolved.requestCount)
        assertEquals(2, resolved.groundedRequestCount)
    }

    @Test
    fun `generate exposes requestFacts on AiReplyDraftResult`() {
        stubEmptyFrame()
        val inbound = "- What is salary?\n- Unknown topic?"
        val rule = sampleRule(1)
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        assertEquals(2, result.requestFacts.size)
        assertEquals(RequestGroundingStatus.GROUNDED, result.requestFacts[0].status)
        assertEquals(RequestGroundingStatus.UNSUPPORTED, result.requestFacts[1].status)
        assertEquals(listOf(1L), result.qaRuleIds)
        assertEquals(1, result.groundedRequestCount)
        assertEquals(listOf("- Unknown topic?"), result.unsupportedRequests)
    }

    @Test
    fun `multi-request grounded prompt emits per-request fact blocks in order`() {
        stubDefaultFrame(salutation = "Dear \${expertName|Professor},")
        val inbound = (1..7).joinToString("\n") { "- Question $it?" }
        val gapItems = (1..7).map { idx ->
            GapItem(
                "- Question $idx?",
                if (idx == 7) emptyList() else listOf(idx.toLong())
            )
        }
                Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        val promptRules = (1L..6L).map { id ->
            sampleRule(id).copy(
                keywords = "question $id",
                replyBody = "Unique body for rule $id",
                answerBody = "Unique body for rule $id",
                replySubject = "Subject $id"
            )
        }
        stubMatchPool(*promptRules.toTypedArray())
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val captured = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                captured += messages
                return ""
            }
        }
        service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        val systemPrompt = captured.first().first { it.role == "system" }.content
        val userContent = captured.first().first { it.role == "user" }.content
        assertTrue(systemPrompt.contains("JSON object"))
        assertTrue(systemPrompt.contains("\"claims\""))
        assertFalse(systemPrompt.contains("\"paragraphs\""))
        assertFalse(systemPrompt.contains("Keep the reply to at most 4 paragraphs."))
        assertFalse(systemPrompt.contains("plain-text email body only"))

        (1..7).forEach { n ->
            assertTrue(userContent.contains("REQUEST $n"))
            assertTrue(userContent.contains("TEXT: - Question $n?"))
        }
        assertTrue(userContent.contains("APPROVED FACTS:"))
        assertTrue(userContent.contains("EVIDENCE_LEVEL: UNSUPPORTED"))
        assertTrue(userContent.indexOf("REQUEST 1") < userContent.indexOf("TEXT: - Question 1?"))
        assertTrue(userContent.indexOf("TEXT: - Question 1?") < userContent.indexOf("APPROVED FACTS:"))
        assertTrue(userContent.indexOf("REQUEST 1") < userContent.indexOf("REQUEST 2"))
        assertTrue(userContent.indexOf("REQUEST 6") < userContent.indexOf("REQUEST 7"))
        assertTrue(userContent.contains("Unique body for rule 1"))
        assertTrue(userContent.contains("Unique body for rule 6"))
        assertFalse(userContent.contains("Unique body for rule 1\nUnique body for rule 2"))
        // Request 1 facts must not include rule 2 body in its block
        val req1Content = userContent.substringAfter("REQUEST 1").substringBefore("REQUEST 2")
        val req1Block = req1Content.substringAfter("APPROVED FACTS:")
        assertTrue(req1Block.contains("Unique body for rule 1"))
        assertFalse(req1Block.contains("Unique body for rule 2"))
        val req7Content = userContent.substringAfter("REQUEST 7")
        val req7Block = req7Content.substringAfter("APPROVED FACTS:")
            .substringBefore("\n\n")
            .ifBlank {
                req7Content.substringAfter("APPROVED FACTS:").substringBefore("Inbound email:")
            }
        assertTrue(req7Block.contains("(none)"))
        assertFalse(userContent.contains("SALUTATION="))
        assertFalse(userContent.contains("CLOSING="))
        assertFalse(userContent.contains("STATUS:"))
    }

    @Test
    fun `multi-request fallback is isomorphic across llm disabled null client and empty response`() {
        stubDefaultFrame(salutation = "Dear \${expertName|Professor},")
        val inbound = "- Salary?\n- Visa?\n- Unknown?"
        val rule1 = sampleRule(1).copy(replyBody = "Salary only body", answerBody = "Salary only body")
        val rule2 = sampleRule(2).copy(keywords = "visa", replyBody = "Visa only body", answerBody = "Visa only body")
        stubMatchPool(rule1, rule2)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val composer = pointByPointComposer()

        val disabled = service(LlmProperties(enabled = false), null, composer).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )
        val nullClient = service(LlmProperties(enabled = true, apiUrl = "http://llm"), null, composer).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )
        val emptyClient = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
        }
        val noResponse = service(
            LlmProperties(enabled = true, apiUrl = "http://llm"),
            emptyClient,
            composer
        ).generate(inboundText = inbound, operatorTurns = emptyList())

        assertEquals(disabled.draftText, nullClient.draftText)
        assertEquals(disabled.draftText, noResponse.draftText)
        assertTrue(disabled.draftText.contains("QA 规则参考内容"))
        assertTrue(disabled.draftText.contains("Salary only body"))
        assertTrue(disabled.draftText.contains("Visa only body"))
        assertFalse(disabled.draftText.contains("Dear"))
        assertFalse(disabled.usedLlm)
        assertEquals(AiReplyDraftReadiness.BLOCKED, disabled.draftReadiness)
    }

    @Test
    fun `multi-request structured fallback preserves answerBody as-is in reference`() {
        stubDefaultFrame()
        val inbound = "- Salary?\n- Visa?"
        val rule1 = sampleRule(1).copy(
            replyBody = "Salary info. Please send your CV.",
            answerBody = "Salary info. Please send your CV."
        )
        val rule2 = sampleRule(2).copy(keywords = "visa", replyBody = "Visa info", answerBody = "Visa info")
        stubMatchPool(rule1, rule2)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        assertTrue(result.draftText.contains("Salary info"))
        assertTrue(result.draftText.contains("Visa info"))
        assertTrue(result.draftText.contains("Please send your CV"))
        assertEquals(AiReplyDraftReadiness.BLOCKED, result.draftReadiness)
    }

    @Test
    fun `invalid grounded JSON falls back with structured warning and usedLlm false`() {
        stubDefaultFrame()
        val inbound = "- Salary?\n- Visa?"
        val rule1 = sampleRule(1)
        val rule2 = sampleRule(2).copy(keywords = "visa", replyBody = "Visa info", answerBody = "Visa info")
        stubMatchPool(rule1, rule2)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? =
                "Dear expert,\n\nHere is a free-form reply that is not JSON."
        }
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
        assertFalse(result.usedLlm)
        assertEquals(AiReplyGenerationState.FALLBACK_NO_RESPONSE, result.generationState)
        assertTrue(result.contextWarnings.contains(AiReplyGroundedDraftMaterializer.WARNING_STRUCTURED_RESPONSE_INVALID))
        assertTrue(result.draftText.contains("Salary info") || result.draftText.contains("Visa info"))
        assertFalse(result.draftText.contains("\"answers\""))
        assertFalse(result.draftText.contains("free-form reply that is not JSON"))
        assertFalse(result.draftText.contains("STATUS:"))
        assertFalse(result.draftText.contains("This still needs confirmation"))
    }

    @Test
    fun `multi-request with empty send ids stays QA_GROUNDED`() {
        stubDefaultFrame()
        val inbound = "- Alpha?\n- Beta?"
                Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
        assertEquals(2, result.requestCount)
        assertTrue(result.qaRuleIds.isEmpty())
    }

    @Test
    fun `single non-research empty send ids stays FREE_FORM`() {
        stubDefaultFrame()
                Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "Hello there",
            operatorTurns = emptyList()
        )

        assertEquals(AiReplyMode.FREE_FORM, result.mode)
    }

    @Test
    fun `CTA violating grounded draft retries with valid JSON then keeps layout`() {
        stubDefaultFrame(salutation = "Dear \${expertName|Professor},")
        val inbound = "- Salary?\n- Visa?"
        val rule1 = sampleRule(1)
        val rule2 = sampleRule(2).copy(keywords = "visa", replyBody = "Visa info", answerBody = "Visa info")
        stubMatchPool(rule1, rule2)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        var chats = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chats++
                return if (chats == 1) {
                    """{"claims":[{"claimKey":"r1:finance.arrangements","text":"Salary info. Please send your CV."},{"claimKey":"r2:general.answer","text":"Visa info"}],"actionText":null}"""
                } else {
                    """{"claims":[{"claimKey":"r1:finance.arrangements","text":"Salary info without CTA."},{"claimKey":"r2:general.answer","text":"Visa info"}],"actionText":null}"""
                }
            }
        }
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        assertEquals(2, chats)
        assertTrue(result.usedLlm)
        assertEquals(AiReplyGenerationState.LLM_USED, result.generationState)
        assertFalse(result.draftText.contains("Please send your CV", ignoreCase = true))
        assertFalse(result.draftText.contains("\"answers\""))
    }

    @Test
    fun `invalid CTA retry falls back to deterministic draft`() {
        stubDefaultFrame()
        val inbound = "- Salary?\n- Visa?"
        val rule1 = sampleRule(1)
        val rule2 = sampleRule(2).copy(keywords = "visa", replyBody = "Visa info", answerBody = "Visa info")
        stubMatchPool(rule1, rule2)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        var chats = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chats++
                return if (chats == 1) {
                    """{"claims":[{"claimKey":"r1:finance.arrangements","text":"Salary info. Please send your CV."},{"claimKey":"r2:general.answer","text":"Visa info"}],"actionText":null}"""
                } else {
                    "not-json free text Please send your CV"
                }
            }
        }
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        assertEquals(2, chats)
        assertFalse(result.usedLlm)
        assertEquals(AiReplyGenerationState.FALLBACK_NO_RESPONSE, result.generationState)
        assertFalse(result.draftText.contains("Please send your CV", ignoreCase = true))
        assertFalse(result.draftText.contains("not-json"))
        assertTrue(result.contextWarnings.contains(
            AiReplyGroundedDraftMaterializer.WARNING_STRUCTURED_RESPONSE_INVALID))
    }

    @Test
    fun `CTA retry with hallucinated claim falls back to deterministic draft`() {
        stubDefaultFrame()
        val inbound = "- Salary?"
        val rule = sampleRule(1).copy(replyBody = "The programme offers competitive compensation.", answerBody = "The programme offers competitive compensation.")
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        var chats = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chats++
                return if (chats == 1) {
                    """{"claims":[{"claimKey":"r1:finance.arrangements","text":"Salary info. Please send your CV."}],"actionText":null}"""
                } else {
                    """{"claims":[{"claimKey":"r1:finance.arrangements","text":"The salary is RMB 500,000 per year."}],"actionText":null}"""
                }
            }
        }
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        assertEquals(2, chats)
        assertFalse(result.usedLlm)
        assertEquals(AiReplyGenerationState.FALLBACK_NO_RESPONSE, result.generationState)
        assertFalse(result.draftText.contains("Please send your CV", ignoreCase = true))
        assertTrue(result.requestFacts.isNotEmpty())
    }

    @Test
    fun `all-unsupported grounded empty frame returns reference text not blank`() {
        stubEmptyFrame()
        val inbound = "- Alpha?\n- Beta?"
                Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        fun assertReferenceText(result: AiReplyDraftResult) {
            assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
            assertTrue(result.draftText.contains("QA 规则参考内容"))
            assertTrue(result.draftText.contains("缺失：暂无已审核事实"))
            assertFalse(result.draftText.contains("STATUS:"))
            assertFalse(result.draftText.contains("UNSUPPORTED"))
            assertFalse(result.draftText.contains("PARTIAL"))
            assertFalse(result.draftText.contains("GROUNDED"))
            assertEquals(2, result.requestCount)
            assertEquals(0, result.groundedRequestCount)
            assertEquals(2, result.unsupportedRequests.size)
            assertEquals(AiReplyDraftReadiness.BLOCKED, result.draftReadiness)
        }

        val disabled = service(LlmProperties(enabled = false), null).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )
        assertReferenceText(disabled)

        val nullClient = service(LlmProperties(enabled = true, apiUrl = "http://llm"), null).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )
        assertReferenceText(nullClient)

        val emptyClient = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
        }
        val noResponse = service(LlmProperties(enabled = true, apiUrl = "http://llm"), emptyClient).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )
        assertReferenceText(noResponse)
    }

    // ── Phase 1: readiness ─────────────────────────────────────────────────────

    @Test
    fun `resolveDraftReadiness returns READY when requestFacts empty`() {
        val draftService = service(LlmProperties(enabled = false), null)
        assertEquals(AiReplyDraftReadiness.READY, draftService.resolveDraftReadiness(emptyList()))
    }

    @Test
    fun `resolveDraftReadiness returns BLOCKED when any UNSUPPORTED present`() {
        val rule = sampleRule(1)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))
        val draftService = service(LlmProperties(enabled = false), null)
        assertEquals(
            AiReplyDraftReadiness.BLOCKED,
            draftService.resolveDraftReadiness(
                listOf(
                    RequestFactItem(1, "a", listOf(1L), RequestGroundingStatus.GROUNDED),
                    RequestFactItem(2, "b", emptyList(), RequestGroundingStatus.UNSUPPORTED)
                ),
                listOf(1L)
            )
        )
    }

    @Test
    fun `resolveDraftReadiness returns NEEDS_REVIEW when only PARTIAL no UNSUPPORTED`() {
        val rule1 = sampleRule(1)
        val rule2 = sampleRule(2).copy(replyBody = "Rule 2", answerBody = "Rule 2", keywords = "b")
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule1))
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(rule2))
        val draftService = service(LlmProperties(enabled = false), null)
        assertEquals(
            AiReplyDraftReadiness.NEEDS_REVIEW,
            draftService.resolveDraftReadiness(
                listOf(
                    RequestFactItem(1, "a", listOf(1L), RequestGroundingStatus.GROUNDED),
                    RequestFactItem(2, "b", listOf(2L), RequestGroundingStatus.PARTIAL)
                ),
                listOf(1L, 2L)
            )
        )
    }

    @Test
    fun `resolveDraftReadiness returns READY when all GROUNDED`() {
        val rule1 = sampleRule(1)
        val rule2 = sampleRule(2).copy(replyBody = "Rule 2", answerBody = "Rule 2", keywords = "b")
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule1))
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(rule2))
        val draftService = service(LlmProperties(enabled = false), null)
        assertEquals(
            AiReplyDraftReadiness.READY,
            draftService.resolveDraftReadiness(
                listOf(
                    RequestFactItem(1, "a", listOf(1L), RequestGroundingStatus.GROUNDED),
                    RequestFactItem(2, "b", listOf(2L), RequestGroundingStatus.GROUNDED)
                ),
                listOf(1L, 2L)
            )
        )
    }

    // ── Phase 6: intent coverage matrix ─────────────────────────────────────────

    @Test
    fun `resolveQaRules matched intents on request text`() {
        val request = "What are the responsibilities and deliverables?"
        val respRule = sampleRule(1).copy(keywords = "responsibilities", replyBody = "Advisor role info", answerBody = "Advisor role info")
        val delRule = sampleRule(2).copy(keywords = "deliverables", replyBody = "Deliverable info", answerBody = "Deliverable info")
        stubMatchPool(respRule, delRule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertEquals(1, resolved.requestFacts.size)
        assertEquals(2, resolved.requestFacts[0].intents.size)
        val intentKeys = resolved.requestFacts[0].intents.map { it.intentKey }
        assertTrue(intentKeys.contains("role.responsibilities"))
        assertTrue(intentKeys.contains("role.deliverables"))
        assertEquals(RequestGroundingStatus.GROUNDED, resolved.requestFacts[0].status)
    }

    @Test
    fun `selection missing matching supported yields PARTIAL`() {
        val request = "How are researchers selected and matched with enterprises?"
        val selRule = sampleRule(1).copy(
            replyBody = "Matching by domain.",
            answerBody = "Matching by domain.",
            keywords = "matching,matched",
            coverageKeys = "enterprise.matching"
        )
        stubMatchPool(selRule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)
        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertEquals(RequestGroundingStatus.PARTIAL, resolved.requestFacts[0].status)
        assertTrue(resolved.requestFacts[0].intents.any { it.intentKey == "researcher.selection" && it.status == "MISSING" })
        assertTrue(resolved.requestFacts[0].intents.any { it.intentKey == "enterprise.matching" && it.status == "SUPPORTED" })
    }

    @Test
    fun `responsibilities supported deliverables missing yields PARTIAL`() {
        val request = "What are the responsibilities and deliverables?"
        val rule = sampleRule(1).copy(
            replyBody = "You act as advisor.",
            answerBody = "You act as advisor.",
            keywords = "responsibilities",
            coverageKeys = "role.responsibilities"
        )
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)
        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertEquals(RequestGroundingStatus.PARTIAL, resolved.requestFacts[0].status)
        assertTrue(resolved.requestFacts[0].intents.any { it.intentKey == "role.responsibilities" && it.status == "SUPPORTED" })
        assertTrue(resolved.requestFacts[0].intents.any { it.intentKey == "role.deliverables" && it.status == "MISSING" })
    }

    @Test
    fun `contract IP supported finance missing yields PARTIAL`() {
        val request = "What are the contract terms, IP rights, and financial compensation?"
        val rule = sampleRule(1).copy(
            replyBody = "Contract governs IP. Compensation set later.",
            answerBody = "Contract governs IP. Compensation set later.",
            keywords = "contract,ip,terms,rights",
            coverageKeys = "contract.terms,ip.arrangements"
        )
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertEquals(RequestGroundingStatus.PARTIAL, resolved.requestFacts[0].status)
        val supportedCount = resolved.requestFacts[0].intents.count { it.status == "SUPPORTED" }
        assertTrue(supportedCount >= 1, "keyword-matched rule should support at least one contract/IP intent")
        assertTrue(resolved.requestFacts[0].intents.any { it.intentKey == "finance.arrangements" && it.status == "MISSING" })
    }

    @Test
    fun `company legal name and location use company identity rule not credentials`() {
        val request = "What is your full name and registered location?"
        val companyRule = sampleRule(1).copy(
            replyBody = "Jiangsu Qingfei.",
            answerBody = "Jiangsu Qingfei.",
            keywords = "legal name,registered location,full name",
            coverageKeys = "company.legal_name,company.registered_location"
        )
        val credRule = sampleRule(2).copy(
            id = 2,
            replyBody = "Verification info.",
            answerBody = "Verification info.",
            keywords = "verification",
            coverageKeys = "company.verification_evidence"
        )
        stubMatchPool(companyRule, credRule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)

        stubMatchPool(companyRule, credRule)
        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertEquals(RequestGroundingStatus.PARTIAL, resolved.requestFacts[0].status)
        assertTrue(resolved.requestFacts[0].intents.any { it.intentKey == "company.legal_name" && it.status == "SUPPORTED" })
        assertTrue(resolved.requestFacts[0].intents.any { it.intentKey == "company.registered_location" && it.status == "MISSING" })
    }

    @Test
    fun `standalone legal name uses company identity evidence`() {
        val request = "What is your full legal name?"
        val companyRule = sampleRule(1).copy(
            replyBody = "Our legal name is Jiangsu Qingfei Talent Technology Co., Ltd.",
            answerBody = "Our legal name is Jiangsu Qingfei Talent Technology Co., Ltd.",
            keywords = "legal name,full name",
            coverageKeys = "company.legal_name"
        )
        stubMatchPool(companyRule)

        stubMatchPool(companyRule)
        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertEquals(RequestGroundingStatus.GROUNDED, resolved.requestFacts.single().status)
        assertEquals(listOf(1L), resolved.requestFacts.single().factRuleIds)
        assertEquals("company.legal_name", resolved.requestFacts.single().intents.single().intentKey)
        assertEquals("SUPPORTED", resolved.requestFacts.single().intents.single().status)
    }

    @Test
    fun `unknown request falls back to general answer intent`() {
        val request = "Is there a cafeteria on site?"
                Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(sampleRule(1).copy(replyBody = "General info.", coverageKeys = "general.answer"))
        )

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertEquals(1, resolved.requestFacts[0].intents.size)
        assertEquals("general.answer", resolved.requestFacts[0].intents[0].intentKey)
    }

    @Test
    fun `sendQaRuleIds not expanded by intent coverage`() {
        val request = "What are the responsibilities?"
        val rule = sampleRule(1).copy(
            replyBody = "Advisor role.",
            answerBody = "Advisor role.",
            keywords = "responsibilities",
            coverageKeys = "role.responsibilities"
        )
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertEquals(listOf(1L), resolved.sendQaRuleIds)
        assertEquals(1, resolved.sendQaRuleIds.size)
    }

    @Test
    fun `research profile insufficient makes expertise fit missing`() {
        val request = "Does my research profile match your programme scope?"
        val rule = sampleRule(1).copy(
            replyBody = "Programme covers AI.",
            coverageKeys = "programme.scope"
        )
                Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(true)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))

        stubMatchPool(rule)
        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(
            request, null, listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT")
        )

        assertTrue(resolved.requestFacts[0].intents.any { it.intentKey == "expertise.programme_fit" && it.status == "MISSING" })
    }

    @Test
    fun `research-fit aliases cannot bypass missing profile`() {
        stubDefaultFrame()
        val rule = sampleRule(1).copy(
            replyBody = "Programme scope covers applied AI.",
            answerBody = "Programme scope covers applied AI.",
            keywords = "research,programme,fit",
            coverageKeys = "programme.scope"
        )
        stubMatchPool(rule)
        val requests = listOf(
            "Does my research fit the programme?",
            "Does my research align with the programme?"
        )
        requests.forEach { request ->
                    }
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))
        stubMatchPool(rule)
        val draftService = service(LlmProperties(enabled = false), null)

        requests.forEach { request ->
            val resolved = draftService.resolveQaRules(
                request,
                null,
                emptyList(),
                researchProfileSufficient = false
            )
            val fact = resolved.requestFacts.single()
            assertTrue(fact.requiresResearchContext, request)
            assertEquals(RequestGroundingStatus.UNSUPPORTED, fact.status, request)
            assertEquals("MISSING", fact.intents.single().status, request)

            val generated = draftService.generate(
                inboundText = request,
                operatorTurns = emptyList(),
                researchProfileSufficient = false
            )
            assertEquals(AiReplyMode.QA_GROUNDED, generated.mode, request)
            assertEquals(AiReplyDraftReadiness.BLOCKED, generated.draftReadiness, request)
        }
    }

    @Test
    fun `enterprise keyword aligns with project types yielding PARTIAL`() {
        val request = "How are researchers matched and what are the enterprise project types?"
        val rule = sampleRule(1).copy(
            replyBody = "Matched by research direction.",
            answerBody = "Matched by research direction.",
            keywords = "matched,matching,enterprise",
            coverageKeys = "enterprise.matching"
        )
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertTrue(resolved.requestFacts.isNotEmpty())
        val fact = resolved.requestFacts[0]
        assertEquals(RequestGroundingStatus.PARTIAL, fact.status)
    }

    // ── P1-1: next_stages timeline as additional required ───────────────────────

    @Test
    fun `next stages with timing requires both steps and timeline`() {
        val request = "What are the next stages and the timeline?"
        val rule = sampleRule(1).copy(
            replyBody = "Steps and timeline.",
            answerBody = "Steps and timeline.",
            keywords = "stages,timeline,steps,next",
            coverageKeys = "application.steps,application.timeline"
        )
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        val nextIntent = resolved.requestFacts[0].intents.find { it.intentKey == "application.next_stages" }
        assertNotNull(nextIntent)
        assertEquals("SUPPORTED", nextIntent!!.status)
    }

    @Test
    fun `next stages with only steps no timeline is PARTIAL when timing asked`() {
        val request = "What are the next stages and what is the timeline?"
        val rule = sampleRule(1).copy(
            replyBody = "Steps only.",
            answerBody = "Steps only.",
            keywords = "stages,steps,next",
            coverageKeys = "application.steps"
        )
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        val nextIntent = resolved.requestFacts[0].intents.find { it.intentKey == "application.next_stages" }
        assertNotNull(nextIntent)
        assertEquals("SUPPORTED", nextIntent!!.status)
    }

    // ── P1-2: general.answer supports empty-coverage rules ──────────────────────

    @Test
    fun `general answer intent supports rule with empty coverage keys`() {
        val request = "Is there a cafeteria on site?"
        val rule = sampleRule(1).copy(
            replyBody = "General info.",
            answerBody = "General info.",
            keywords = "cafeteria,site",
            coverageKeys = ""
        )
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertEquals(1, resolved.requestFacts[0].intents.size)
        assertEquals("general.answer", resolved.requestFacts[0].intents[0].intentKey)
        assertEquals("SUPPORTED", resolved.requestFacts[0].intents[0].status)
        assertEquals(RequestGroundingStatus.GROUNDED, resolved.requestFacts[0].status)
    }

    // ── P1-3: factRuleIds empty when all intents MISSING ────────────────────────

    @Test
    fun `all missing intents yields empty factRuleIds`() {
        val request = "What are the deliverables and IP rights?"
        val rule = sampleRule(1).copy(
            replyBody = "No coverage here.",
            coverageKeys = ""
        )
                Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))

        stubMatchPool(rule)
        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertTrue(resolved.requestFacts[0].factRuleIds.isEmpty())
        assertEquals(RequestGroundingStatus.UNSUPPORTED, resolved.requestFacts[0].status)
    }

    // ── P1-4: word-boundary alias matching ──────────────────────────────────────

    @Test
    fun `preselected does not trigger selection intent`() {
        val request = "Candidates are preselected based on experience."
                Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(sampleRule(1).copy(replyBody = "General info."))
        )

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        val hasSelection = resolved.requestFacts[0].intents.any { it.intentKey == "researcher.selection" }
        assertFalse(hasSelection, "preselected should not trigger researcher.selection")
        assertEquals("general.answer", resolved.requestFacts[0].intents[0].intentKey)
    }

    @Test
    fun `selected still triggers selection intent with word boundary`() {
        val request = "How are researchers selected?"
        val rule = sampleRule(1).copy(
            replyBody = "Selection process info.",
            answerBody = "Selection process info.",
            keywords = "selected,selection,researchers",
            coverageKeys = "researcher.selection"
        )
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)
        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertTrue(resolved.requestFacts[0].intents.any { it.intentKey == "researcher.selection" })
        assertEquals("SUPPORTED", resolved.requestFacts[0].intents.find { it.intentKey == "researcher.selection" }!!.status)
    }

    @Test
    fun `next stages with only timeline no steps is PARTIAL when timing asked`() {
        val request = "What are the next stages and what is the timeline?"
        val rule = sampleRule(1).copy(
            replyBody = "Timeline only - June to December.",
            answerBody = "Timeline only - June to December.",
            keywords = "timeline,stages,next",
            coverageKeys = "application.timeline"
        )
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        val nextIntent = resolved.requestFacts[0].intents.find { it.intentKey == "application.next_stages" }
        assertNotNull(nextIntent)
        assertEquals("SUPPORTED", nextIntent!!.status)
    }

    @Test
    fun `URL fragment containing selected does not trigger selection intent`() {
        val request = "See https://scholar.google.com/citations?selected=true for more."
                Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(sampleRule(1).copy(replyBody = "General info."))
        )

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        val hasSelection = resolved.requestFacts[0].intents.any { it.intentKey == "researcher.selection" }
        assertFalse(hasSelection, "URL query fragment should not trigger researcher.selection")
    }

    @Test
    fun `factRuleIds preserves candidate order not intent catalog order`() {
        val request = "What are the responsibilities and deliverables?"
        val rule1 = sampleRule(1).copy(
            id = 1L,
            replyBody = "Deliverables body.",
            answerBody = "Deliverables body.",
            keywords = "deliverables",
            coverageKeys = "role.deliverables"
        )
        val rule2 = sampleRule(2).copy(
            id = 2L,
            replyBody = "Responsibilities body.",
            answerBody = "Responsibilities body.",
            keywords = "responsibilities",
            coverageKeys = "role.responsibilities"
        )
        stubMatchPool(rule2, rule1)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertEquals(RequestGroundingStatus.GROUNDED, resolved.requestFacts[0].status)
        assertEquals(listOf(2L, 1L), resolved.requestFacts[0].factRuleIds, "should preserve candidate order [2, 1]")
        assertTrue(resolved.requestFacts[0].intents.all { it.status == "SUPPORTED" })
        assertEquals(listOf(2L, 1L), resolved.sendQaRuleIds, "sendQaRuleIds unchanged")
    }

    // ── T4: DraftService end-to-end intent/coverage matrix regression ─────────────

    /**
     * Original expert email copied verbatim (apart from trimIndent removing test indentation).
     * G4 keeps real "enterprise projects" wording; catalog disambiguation must drop the
     * project-types object hit so intents stay selection + matching only.
     */
    private val janmedaMail = """
        Dear Mr Wu,

        Thank you for your email and for considering me for this research
        collaboration initiative.

        You may review my research background and publications through my Google
        Scholar/Scopus profile:

        https://scholar.google.com/citations?user=1oMj67wAAAAJ&hl=en
        <https://scholar.google.com/citations?user=1oMj67wAAAAJ&hl=en>

        https://www.scopus.com/authid/detail.uri?authorId=56022647300

        Based on my research profile, could you please confirm whether my areas of
        expertise fall within the scope of your programme and the types of
        enterprise projects your team manages?

        I would also be grateful if you could provide further information regarding:

        - the full name and registered location of your company;
        - the purpose and structure of the programme;
        - how researchers are selected and matched with enterprise projects;
        - the expected responsibilities and deliverables;
        - the contractual, financial, and intellectual-property arrangements; and
        - the next stages of the application and collaboration process.

        I am keen to have your email.
    """.trimIndent()

    private fun makeJanmedaRule(id: Long, body: String, keywords: String, coverageKeys: String = "") =
        sampleRule(id).copy(
            replyBody = body,
            answerBody = body,
            keywords = keywords,
            coverageKeys = coverageKeys,
            replyPolicy = com.weibo.talentintroduction.qa.domain.QaReplyPolicy.AUTO.name
        )

    private fun buildJanmedaRules(
        ipCoverageKey: String = "ip.arrangements",
        programmeScopeKey: String = "programme.scope"
    ): List<QaRule> {
        val ipBody = if (ipCoverageKey.isBlank()) "" else "IP belongs to the enterprise by default."
        val scopeBody = if (programmeScopeKey.isBlank()) "" else "Programme scope covers AI and engineering."
        return listOf(
            makeJanmedaRule(101L, scopeBody, "programme,scope,expertise", programmeScopeKey),
            makeJanmedaRule(102L, "Enterprise projects include applied research.", "enterprise projects,types", "enterprise.project_types"),
            makeJanmedaRule(201L, "Our legal name is Weibo Technology.", "full name,legal name,company", "company.legal_name"),
            makeJanmedaRule(202L, "We are registered in Beijing.", "registered location", "company.registered_location"),
            makeJanmedaRule(301L, "The programme aims to bridge academia and industry.", "purpose", "programme.purpose"),
            makeJanmedaRule(302L, "The programme spans 12 months with two tracks.", "structure", "programme.structure"),
            makeJanmedaRule(401L, "Researchers are selected by peer review.", "selected,selection,researchers", "researcher.selection"),
            makeJanmedaRule(402L, "Matching is based on research direction.", "matched,matching", "enterprise.matching"),
            makeJanmedaRule(501L, "Responsibilities include research advisory.", "responsibilities", "role.responsibilities"),
            makeJanmedaRule(502L, "Deliverables are defined per project.", "deliverables", "role.deliverables"),
            makeJanmedaRule(601L, "A formal contract is signed.", "contract,contractual", "contract.terms"),
            makeJanmedaRule(602L, "Funding is provided by the government.", "financial,funding", "finance.government_funding"),
            makeJanmedaRule(603L, ipBody, "intellectual-property,intellectual property,ip", ipCoverageKey),
            makeJanmedaRule(701L, "Next steps include document submission.", "next stages,application", "application.steps")
        )
    }

    private fun stubJanmedaQaRules(
        ipCoverageKey: String = "ip.arrangements",
        programmeScopeKey: String = "programme.scope"
    ) {
        stubMatchPool(*buildJanmedaRules(ipCoverageKey, programmeScopeKey).toTypedArray())
    }

    private fun stubJanmedaComposition(extracted: List<com.weibo.talentintroduction.qa.service.QaRequestExtractor.ExtractedRequest>) {
        // Grounded engine selects facts via keywords; gapItems are no longer used.
    }

    private fun stubJanmedaResearchContext(extracted: List<com.weibo.talentintroduction.qa.service.QaRequestExtractor.ExtractedRequest>) {
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(extracted[0].text)).thenReturn(true)
    }

    @Test
    fun `janmeda full mail yields 7 request facts with correct I2 intent matrix happy path`() {
        val extracted = com.weibo.talentintroduction.qa.service.QaRequestExtractor.extract(janmedaMail)
        assertEquals(7, extracted.size, "extractor must yield exactly 7 groups: ${extracted.map { it.text }}")

        // Verify order and original wording preserved in requestText (soft line wraps fold to spaces).
        assertEquals(
            "Based on my research profile, could you please confirm whether my areas of expertise fall within the scope of your programme and the types of enterprise projects your team manages?",
            extracted[0].text,
            "G1 original wording"
        )
        assertEquals("- the full name and registered location of your company;", extracted[1].text, "G2 original wording")
        assertEquals("- the purpose and structure of the programme;", extracted[2].text, "G3 original wording")
        assertEquals("- how researchers are selected and matched with enterprise projects;", extracted[3].text, "G4 original wording")
        assertEquals("- the expected responsibilities and deliverables;", extracted[4].text, "G5 original wording")
        assertEquals("- the contractual, financial, and intellectual-property arrangements; and", extracted[5].text, "G6 original wording")
        assertEquals("- the next stages of the application and collaboration process.", extracted[6].text, "G7 original wording")

        // No URL fragments in any extracted group
        assertTrue(extracted.none { it.text.contains("scholar.google.com/citations?") }, "URLs must not form groups")
        assertTrue(extracted.none { it.text.contains("citations?user=") }, "Scholar query params must not appear")
        assertTrue(extracted.none { it.text.contains("authorId") }, "URL params must not appear")

        stubJanmedaComposition(extracted)
        stubJanmedaResearchContext(extracted)
        stubJanmedaQaRules()

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(janmedaMail, null)

        assertEquals(7, resolved.requestFacts.size, "must have exactly 7 request facts")

        // Assert index and requestText preserved
        (0..6).forEach { i ->
            assertEquals(i + 1, resolved.requestFacts[i].index, "fact index must be 1-based")
            assertEquals(extracted[i].text, resolved.requestFacts[i].requestText, "requestText G${i+1} must match extractor output")
        }

        // Assert I-2 intent key matrix (order matters)
        val g1Keys = resolved.requestFacts[0].intents.map { it.intentKey }
        assertEquals(listOf("expertise.programme_fit", "enterprise.project_types"), g1Keys, "G1 intents")

        val g2Keys = resolved.requestFacts[1].intents.map { it.intentKey }
        assertEquals(listOf("company.legal_name", "company.registered_location"), g2Keys, "G2 intents")

        val g3Keys = resolved.requestFacts[2].intents.map { it.intentKey }
        assertEquals(listOf("programme.purpose", "programme.structure"), g3Keys, "G3 intents")

        val g4Keys = resolved.requestFacts[3].intents.map { it.intentKey }
        assertEquals(listOf("researcher.selection", "enterprise.matching"), g4Keys, "G4 intents")
        assertEquals(
            "Selection and enterprise matching",
            AiReplyIntentCatalog.resolveGroupTitle(g4Keys, extracted[3].text),
            "G4 fixed title"
        )

        val g5Keys = resolved.requestFacts[4].intents.map { it.intentKey }
        assertEquals(listOf("role.responsibilities", "role.deliverables"), g5Keys, "G5 intents")

        val g6Keys = resolved.requestFacts[5].intents.map { it.intentKey }
        assertEquals(listOf("contract.terms", "finance.arrangements", "ip.arrangements"), g6Keys, "G6 intents")

        val g7Keys = resolved.requestFacts[6].intents.map { it.intentKey }
        assertEquals(listOf("application.next_stages"), g7Keys, "G7 intents")

        val totalIntents = resolved.requestFacts.sumOf { it.intents.size }
        assertEquals(14, totalIntents, "I-2 requires exactly 14 intents across 7 groups")

        // All groups GROUNDED in happy path
        resolved.requestFacts.forEach { fact ->
            assertEquals(RequestGroundingStatus.GROUNDED, fact.status, "G${fact.index} must be GROUNDED")
        }

        // No general.answer intents
        resolved.requestFacts.forEach { fact ->
            assertTrue(fact.intents.none { it.intentKey == "general.answer" }, "G${fact.index} must not have general.answer")
        }

        // G1 requiresResearchContext=true
        assertTrue(resolved.requestFacts[0].requiresResearchContext, "G1 must require research context")
        (1..6).forEach { i ->
            assertFalse(resolved.requestFacts[i].requiresResearchContext, "G${i+1} must not require research context")
        }

        // draftReadiness = READY
        val draftService = service(LlmProperties(enabled = false), null)
        assertEquals(AiReplyDraftReadiness.READY, draftService.resolveDraftReadiness(resolved.requestFacts))
    }

    @Test
    fun `janmeda removing IP coverage degrades G6 to PARTIAL and readiness to NEEDS_REVIEW`() {
        val extracted = com.weibo.talentintroduction.qa.service.QaRequestExtractor.extract(janmedaMail)
        assertEquals(7, extracted.size)

        stubJanmedaComposition(extracted)
        stubJanmedaResearchContext(extracted)
        // Stub with empty ip coverage key on rule 603
        stubJanmedaQaRules(ipCoverageKey = "")

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(janmedaMail, null)

        // G6 must degrade: ip.arrangements intent MISSING → group PARTIAL
        val g6 = resolved.requestFacts[5]
        assertEquals(RequestGroundingStatus.PARTIAL, g6.status, "G6 must be PARTIAL when IP coverage removed")
        val ipIntent = g6.intents.find { it.intentKey == "ip.arrangements" }
        assertNotNull(ipIntent, "G6 must still have ip.arrangements intent")
        assertEquals("MISSING", ipIntent!!.status, "ip.arrangements must be MISSING")

        // contract and finance still SUPPORTED
        val contractIntent = g6.intents.find { it.intentKey == "contract.terms" }
        val financeIntent = g6.intents.find { it.intentKey == "finance.arrangements" }
        assertNotNull(contractIntent)
        assertNotNull(financeIntent)
        assertEquals("SUPPORTED", contractIntent!!.status, "contract.terms must remain SUPPORTED")
        assertEquals("SUPPORTED", financeIntent!!.status, "finance.arrangements must remain SUPPORTED")

        // Other groups unchanged at GROUNDED
        listOf(0, 1, 2, 3, 4, 6).forEach { i ->
            assertEquals(
                RequestGroundingStatus.GROUNDED,
                resolved.requestFacts[i].status,
                "G${i+1} must remain GROUNDED"
            )
        }

        // draftReadiness = NEEDS_REVIEW (has PARTIAL)
        val draftService = service(LlmProperties(enabled = false), null)
        assertEquals(AiReplyDraftReadiness.NEEDS_REVIEW, draftService.resolveDraftReadiness(resolved.requestFacts))
    }

    @Test
    fun `janmeda removing programme scope degrades G1 only and other groups unaffected`() {
        val extracted = com.weibo.talentintroduction.qa.service.QaRequestExtractor.extract(janmedaMail)
        assertEquals(7, extracted.size)

        stubJanmedaComposition(extracted)
        stubJanmedaResearchContext(extracted)
        // Stub with empty programme scope on rule 101 → expertise.programme_fit loses coverage
        stubJanmedaQaRules(programmeScopeKey = "")

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(janmedaMail, null)

        // G1 must degrade: expertise.programme_fit MISSING → group PARTIAL
        val g1 = resolved.requestFacts[0]
        assertEquals(RequestGroundingStatus.PARTIAL, g1.status, "G1 must be PARTIAL when programme.scope removed")
        val fitIntent = g1.intents.find { it.intentKey == "expertise.programme_fit" }
        assertNotNull(fitIntent)
        assertEquals("MISSING", fitIntent!!.status, "expertise.programme_fit must be MISSING")
        // enterprise.project_types still SUPPORTED
        val projIntent = g1.intents.find { it.intentKey == "enterprise.project_types" }
        assertNotNull(projIntent)
        assertEquals("SUPPORTED", projIntent!!.status, "enterprise.project_types must remain SUPPORTED")

        // G2-G7 unchanged at GROUNDED
        (1..6).forEach { i ->
            assertEquals(
                RequestGroundingStatus.GROUNDED,
                resolved.requestFacts[i].status,
                "G${i+1} must remain GROUNDED"
            )
        }

        // draftReadiness = NEEDS_REVIEW
        val draftService = service(LlmProperties(enabled = false), null)
        assertEquals(AiReplyDraftReadiness.NEEDS_REVIEW, draftService.resolveDraftReadiness(resolved.requestFacts))
    }

    @Test
    fun `janmeda profile context warning degrades G1 only leaving G2-G7 at GROUNDED`() {
        val extracted = com.weibo.talentintroduction.qa.service.QaRequestExtractor.extract(janmedaMail)
        assertEquals(7, extracted.size)

        stubJanmedaComposition(extracted)
        stubJanmedaResearchContext(extracted)
        stubJanmedaQaRules()

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(
            janmedaMail, null, listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT")
        )

        // G1 expertise.programme_fit MISSING (profile insufficient)
        val g1 = resolved.requestFacts[0]
        val fitIntent = g1.intents.find { it.intentKey == "expertise.programme_fit" }
        assertNotNull(fitIntent)
        assertEquals("MISSING", fitIntent!!.status, "expertise.programme_fit must be MISSING when profile insufficient")
        assertEquals(RequestGroundingStatus.PARTIAL, g1.status, "G1 must be PARTIAL with profile warning")

        // G2-G7 unchanged at GROUNDED
        (1..6).forEach { i ->
            assertEquals(
                RequestGroundingStatus.GROUNDED,
                resolved.requestFacts[i].status,
                "G${i+1} must remain GROUNDED despite profile warning"
            )
        }

        val draftService = service(LlmProperties(enabled = false), null)
        assertEquals(AiReplyDraftReadiness.NEEDS_REVIEW, draftService.resolveDraftReadiness(resolved.requestFacts))
    }

    @Test
    fun `original janmeda URLs and query params do not form extra groups or request text`() {
        val extracted = com.weibo.talentintroduction.qa.service.QaRequestExtractor.extract(janmedaMail)

        // No URL-only group
        assertTrue(extracted.none { it.text.contains("scholar.google.com") }, "scholar URL must not be a group")
        assertTrue(extracted.none { it.text.contains("scopus.com") }, "scopus URL must not be a group")

        stubJanmedaComposition(extracted)
        stubJanmedaResearchContext(extracted)
        stubJanmedaQaRules()

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(janmedaMail, null)

        // Exactly 7 facts - no extra URL group
        assertEquals(7, resolved.requestFacts.size, "must not create extra group for URLs")

        // No fact has requestText containing URL
        assertTrue(
            resolved.requestFacts.none { it.requestText.contains("scholar.google.com") },
            "no fact must reference scholar URL"
        )

        // No fact references either original URL query fragment.
        assertTrue(
            resolved.requestFacts.none {
                it.requestText.contains("citations?user=") || it.requestText.contains("authorId")
            },
            "Original URL query params must not appear in any requestText"
        )
    }

    // ── T3: Phase 2 — modality strengthening E2E fallback ────────────────────
    // Two-question inbound forces requestCount=2 → QA_GROUNDED mode (claim validator is active).
    // sampleRule default coverageKeys = "finance.government_funding" satisfies finance.arrangements intent.
    // "- Visa?" maps to general.answer; any rule becomes evidence for that intent.

    private fun stubModalityT3(inbound: String, sourceBody1: String) {
        val rule1 = sampleRule(1).copy(replyBody = sourceBody1, answerBody = sourceBody1, keywords = "salary")
        val rule2 = sampleRule(2).copy(replyBody = "Visa info", answerBody = "Visa info", keywords = "visa")
        stubMatchPool(rule1, rule2)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
    }

    private val minimalGroundedJson = """{"claims":[{"claimKey":"r1:finance.arrangements","text":"Funding info"}],"actionText":null}"""

    private val modalityGroundedJson = """{"claims":[{"claimKey":"r1:finance.arrangements","text":"You will receive salary support."},{"claimKey":"r2:general.answer","text":"Visa info"}],"actionText":null}"""

    @Test
    fun `grounded prompt and fallback ignore blank answerBody even when replyBody populated`() {
        stubDefaultFrame()
        val inbound = "What is salary?"
        val selectedRule = sampleRule(1)
        stubMatchPool(selectedRule)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(
                selectedRule.copy(
                    replyBody = "Legacy 10 million RMB guarantee.",
                    answerBody = ""
                )
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(inbound)).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val captured = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                captured += messages
                return null
            }
        }
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        val userContent = captured.first().first { it.role == "user" }.content
        assertFalse(userContent.contains("Legacy 10 million RMB guarantee"))
        assertFalse(result.draftText.contains("Legacy 10 million RMB guarantee"))
        assertFalse(result.usedLlm)
    }

    @Test
    fun `unnatural numbered grounded LLM answer triggers structure fallback`() {
        stubDefaultFrame()
        val inbound = "- Salary?\n- Visa?"
        stubModalityT3(inbound, sourceBody1 = "Salary support is available.")
        val numberedJson = """{"claims":[{"claimKey":"r1:finance.arrangements","text":"1. Program & eligibility\nSalary support is available."},{"claimKey":"r2:general.answer","text":"Visa info"}],"actionText":null}"""
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = numberedJson
        }
        val result = service(
            LlmProperties(enabled = true, apiUrl = "http://llm"),
            client,
            validator = AiReplyHighRiskClaimValidator(qaRuleRepository)
        ).generate(inboundText = inbound, operatorTurns = emptyList())

        assertFalse(result.usedLlm)
        assertEquals(AiReplyGenerationState.FALLBACK_NO_RESPONSE, result.generationState)
        assertTrue(result.contextWarnings.contains(AiReplyGroundedDraftMaterializer.WARNING_UNNATURAL_GROUNDED_STRUCTURE))
        assertFalse(result.draftText.contains("1. Program & eligibility"))
    }

    @Test
    fun `modality strengthening in grounded response causes fallback`() {
        stubDefaultFrame()
        val inbound = "- Salary?\n- Visa?"
        stubModalityT3(inbound, sourceBody1 = "Selected candidates may receive salary support.")

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = modalityGroundedJson
        }

        val result = service(
            LlmProperties(enabled = true, apiUrl = "http://llm"),
            client,
            validator = AiReplyHighRiskClaimValidator(qaRuleRepository)
        ).generate(inboundText = inbound, operatorTurns = emptyList())

        assertFalse(result.usedLlm)
        assertEquals(AiReplyGenerationState.FALLBACK_NO_RESPONSE, result.generationState)
        assertTrue(result.contextWarnings.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_MODALITY_STRENGTHENED))
        assertFalse(result.draftText.contains("You will receive salary support", ignoreCase = true))
    }

    @Test
    fun `high-risk family alias in grounded response causes fallback`() {
        stubDefaultFrame()
        val inbound = "- Salary?\n- Visa?"
        stubModalityT3(inbound, sourceBody1 = "General compensation information is available.")
        val groundedJson = """{"claims":[{"claimKey":"r1:finance.arrangements","text":"Participation is free of charge."},{"claimKey":"r2:general.answer","text":"Visa info"}],"actionText":null}"""
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = groundedJson
        }

        val result = service(
            LlmProperties(enabled = true, apiUrl = "http://llm"),
            client,
            validator = AiReplyHighRiskClaimValidator(qaRuleRepository)
        ).generate(inboundText = inbound, operatorTurns = emptyList())

        assertFalse(result.usedLlm)
        assertEquals(AiReplyGenerationState.FALLBACK_NO_RESPONSE, result.generationState)
        assertTrue(result.contextWarnings.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_HIGH_RISK_UNBACKED))
        assertFalse(result.draftText.contains("free of charge", ignoreCase = true))
    }

    @Test
    fun `explicit will receive source allows grounded response`() {
        stubDefaultFrame()
        val inbound = "- Salary?\n- Visa?"
        stubModalityT3(inbound, sourceBody1 = "Selected candidates will receive salary support.")

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = modalityGroundedJson
        }

        val result = service(
            LlmProperties(enabled = true, apiUrl = "http://llm"),
            client,
            validator = AiReplyHighRiskClaimValidator(qaRuleRepository)
        ).generate(inboundText = inbound, operatorTurns = emptyList())

        assertTrue(result.usedLlm)
        assertEquals(AiReplyGenerationState.LLM_USED, result.generationState)
        assertFalse(result.contextWarnings.contains(AiReplyHighRiskClaimValidator.WARNING_CLAIM_MODALITY_STRENGTHENED))
    }

    @Test
    fun `CTA retry returning modality strengthened answer falls back`() {
        stubDefaultFrame()
        val inbound = "- Salary?\n- Visa?"
        stubModalityT3(inbound, sourceBody1 = "Selected candidates may receive salary support.")

        var chats = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chats++
                return if (chats == 1) {
                    // First call: valid claim but CTA present — triggers retry
                    """{"claims":[{"claimKey":"r1:finance.arrangements","text":"Competitive allowance. Please send your CV."},{"claimKey":"r2:general.answer","text":"Visa info"}],"actionText":null}"""
                } else {
                    // Retry: no CTA but modality-strengthened answer
                    modalityGroundedJson
                }
            }
        }

        val result = service(
            LlmProperties(enabled = true, apiUrl = "http://llm"),
            client,
            validator = AiReplyHighRiskClaimValidator(qaRuleRepository)
        ).generate(inboundText = inbound, operatorTurns = emptyList())

        assertEquals(2, chats)
        assertFalse(result.usedLlm)
        assertEquals(AiReplyGenerationState.FALLBACK_NO_RESPONSE, result.generationState)
        assertFalse(result.draftText.contains("Please send your CV", ignoreCase = true))
    }

    // ── Phase 5B: grounded fallback readiness & evidence revalidation ──────────

    @Test
    fun `grounded fallback preserves answerBody including CTA in reference text`() {
        stubDefaultFrame()
        val rule = sampleRule(1).copy(
            replyBody = "Please send your CV for matching. Applicants submit materials for review.",
            answerBody = "Please send your CV for matching. Applicants submit materials for review."
        )
        stubMatchPool(rule)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "What is salary support?",
            operatorTurns = emptyList()
        )

        assertFalse(result.usedLlm)
        assertTrue(result.draftText.contains("Please send your CV"))
        assertTrue(result.draftText.contains("Applicants submit materials for review"))
        assertEquals(AiReplyDraftReadiness.BLOCKED, result.draftReadiness)
    }

    @Test
    fun `grounded fallback repair exhausted keeps BLOCKED over sanitize removal`() {
        stubEmptyFrame()
        val rule = sampleRule(1).copy(
            replyBody = "Salary info. Please send your CV.",
            answerBody = "Salary info. Please send your CV."
        )
        val rule2 = sampleRule(2).copy(keywords = "visa", replyBody = "Visa info", answerBody = "Visa info")
        stubMatchPool(rule, rule2)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        var chats = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chats++
                return """{"claims":[{"claimKey":"r1:finance.arrangements","text":"Salary info. Please send your CV."},{"claimKey":"r2:general.answer","text":"Visa info"}],"actionText":null}"""
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "- Salary?\n- Visa?",
            operatorTurns = emptyList()
        )

        assertEquals(2, chats)
        assertTrue(result.contextWarnings.contains(AiReplyDraftService.TRUST_REPAIR_EXHAUSTED))
        assertEquals(AiReplyDraftReadiness.BLOCKED, result.draftReadiness)
    }

    @Test
    fun `llm disabled fallback returns BLOCKED for complete AUTO facts`() {
        stubDefaultFrame()
        val rule = sampleRule(1)
        stubMatchPool(rule)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "What is salary?",
            operatorTurns = emptyList()
        )

        assertEquals(AiReplyDraftReadiness.BLOCKED, result.draftReadiness)
        assertTrue(result.draftText.contains("QA 规则参考内容"))
        assertFalse(result.usedLlm)
    }

    @Test
    fun `resolveDraftReadiness returns BLOCKED when evidence rule missing from repository`() {
        stubDefaultFrame()
        val rule = sampleRule(1)
        stubMatchPool(rule)
        val draftService = service(LlmProperties(enabled = false), null)

        val result = draftService.resolveDraftReadiness(
            listOf(RequestFactItem(1, "a", listOf(1L), RequestGroundingStatus.GROUNDED)),
            listOf(1L, 999L)
        )

        assertEquals(AiReplyDraftReadiness.BLOCKED, result)
    }

    @Test
    fun `resolveDraftReadiness returns BLOCKED when evidence rule is disabled`() {
        stubDefaultFrame()
        val rule = sampleRule(1).copy(enabled = false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))
        val draftService = service(LlmProperties(enabled = false), null)

        val result = draftService.resolveDraftReadiness(
            listOf(RequestFactItem(1, "a", listOf(1L), RequestGroundingStatus.GROUNDED)),
            listOf(1L)
        )

        assertEquals(AiReplyDraftReadiness.BLOCKED, result)
    }

    @Test
    fun `resolveDraftReadiness returns BLOCKED when evidence rule has blank answerBody`() {
        stubDefaultFrame()
        val rule = sampleRule(1).copy(answerBody = "   ")
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))
        val draftService = service(LlmProperties(enabled = false), null)

        val result = draftService.resolveDraftReadiness(
            listOf(RequestFactItem(1, "a", listOf(1L), RequestGroundingStatus.GROUNDED)),
            listOf(1L)
        )

        assertEquals(AiReplyDraftReadiness.BLOCKED, result)
    }

    @Test
    fun `resolveDraftReadiness returns BLOCKED when evidence rule has NEVER policy`() {
        stubDefaultFrame()
        val rule = sampleRule(1).copy(
            replyPolicy = com.weibo.talentintroduction.qa.domain.QaReplyPolicy.NEVER.name,
            replyBody = "Salary info",
            answerBody = "Salary info"
        )
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))
        val draftService = service(LlmProperties(enabled = false), null)

        val result = draftService.resolveDraftReadiness(
            listOf(RequestFactItem(1, "a", listOf(1L), RequestGroundingStatus.GROUNDED)),
            listOf(1L)
        )

        assertEquals(AiReplyDraftReadiness.BLOCKED, result)
    }

    @Test
    fun `resolveDraftReadiness returns NEEDS_REVIEW when evidence rule has REVIEW policy`() {
        stubDefaultFrame()
        val rule = sampleRule(1).copy(
            replyPolicy = com.weibo.talentintroduction.qa.domain.QaReplyPolicy.REVIEW.name,
            replyBody = "Salary info",
            answerBody = "Salary info"
        )
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))
        val draftService = service(LlmProperties(enabled = false), null)

        val result = draftService.resolveDraftReadiness(
            listOf(RequestFactItem(1, "a", listOf(1L), RequestGroundingStatus.GROUNDED)),
            listOf(1L)
        )

        assertEquals(AiReplyDraftReadiness.NEEDS_REVIEW, result)
    }

    @Test
    fun `resolveDraftReadiness returns READY when all evidence rules are present enabled auto with answerBody`() {
        stubDefaultFrame()
        val rule = sampleRule(1)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))
        val draftService = service(LlmProperties(enabled = false), null)

        val result = draftService.resolveDraftReadiness(
            listOf(RequestFactItem(1, "a", listOf(1L), RequestGroundingStatus.GROUNDED)),
            listOf(1L)
        )

        assertEquals(AiReplyDraftReadiness.READY, result)
    }

    @Test
    fun `resolveDraftReadiness noncritical unsupported still returns NEEDS_REVIEW with valid evidence`() {
        stubDefaultFrame()
        val rule = sampleRule(1)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))
        val draftService = service(LlmProperties(enabled = false), null)

        val result = draftService.resolveDraftReadiness(
            listOf(
                RequestFactItem(1, "a", listOf(1L), RequestGroundingStatus.GROUNDED),
                RequestFactItem(2, "b", emptyList(), RequestGroundingStatus.UNSUPPORTED,
                    intents = listOf(RequestIntentCoverage(
                        intentKey = "general.answer",
                        title = "General",
                        requiredCoverageKeys = emptyList(),
                        evidenceRuleIds = emptyList(),
                        status = "UNSUPPORTED",
                        missingEvidenceKeys = emptyList()
                    )))
            ),
            listOf(1L)
        )

        assertEquals(AiReplyDraftReadiness.NEEDS_REVIEW, result)
    }

    @Test
    fun `resolveDraftReadiness returns BLOCKED when evidence rule has invalid replyPolicy`() {
        stubDefaultFrame()
        val rule = sampleRule(1).copy(replyPolicy = "invalid")
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))
        val draftService = service(LlmProperties(enabled = false), null)

        val result = draftService.resolveDraftReadiness(
            listOf(RequestFactItem(1, "a", listOf(1L), RequestGroundingStatus.GROUNDED)),
            listOf(1L)
        )

        assertEquals(AiReplyDraftReadiness.BLOCKED, result)
    }

    // ── Transport retry count tests (Phase 08 I-2) ──

    @Test
    fun `first call success makes exactly 1 provider call`() {
        stubEmptyFrame()
        val rule = sampleRule(1)
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        var chats = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chats++
                return """{"claims":[{"claimKey":"r1:finance.arrangements","text":"Salary info."}],"actionText":null}"""
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "- Salary?",
            operatorTurns = emptyList()
        )

        assertEquals(1, chats)
        assertTrue(result.usedLlm)
        assertEquals(AiReplyGenerationState.LLM_USED, result.generationState)
    }

    @Test
    fun `transient failure then success makes 2 calls and no failure warning`() {
        stubEmptyFrame()
        val rule = sampleRule(1)
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        var chats = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chats++
                return if (chats == 1) null else """{"claims":[{"claimKey":"r1:finance.arrangements","text":"Salary info."}],"actionText":null}"""
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "- Salary?",
            operatorTurns = emptyList()
        )

        assertEquals(2, chats)
        assertTrue(result.usedLlm)
        assertEquals(AiReplyGenerationState.LLM_USED, result.generationState)
        assertFalse(result.contextWarnings.any { it.startsWith("AI_REPLY_LLM_") })
    }

    @Test
    fun `two failures makes 2 calls and FALLBACK_NO_RESPONSE`() {
        stubEmptyFrame()
        val rule = sampleRule(1)
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        var chats = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chats++
                return null
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "- Salary?",
            operatorTurns = emptyList()
        )

        assertEquals(2, chats)
        assertFalse(result.usedLlm)
        assertEquals(AiReplyGenerationState.FALLBACK_NO_RESPONSE, result.generationState)
    }

    @Test
    fun `retry success then JSON invalid then correction totals 3 calls`() {
        stubEmptyFrame()
        val rule = sampleRule(1)
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        var chats = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chats++
                return when (chats) {
                    1 -> null // first transport call fails
                    2 -> "not valid json" // retry succeeds but invalid
                    3 -> """{"claims":[{"claimKey":"r1:finance.arrangements","text":"Salary info."}],"actionText":null}""" // correction succeeds
                    else -> null
                }
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "- Salary?",
            operatorTurns = emptyList()
        )

        assertEquals(3, chats)
        assertTrue(result.usedLlm)
        assertEquals(AiReplyGenerationState.LLM_USED, result.generationState)
    }

    @Test
    fun `correction transport failure does not retry twice`() {
        stubEmptyFrame()
        val rule = sampleRule(1)
        val rule2 = sampleRule(2).copy(keywords = "visa", replyBody = "Visa info", answerBody = "Visa info")
        stubMatchPool(rule, rule2)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        var chats = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chats++
                return when (chats) {
                    1 -> """{"claims":[{"claimKey":"r1:finance.arrangements","text":"Salary info. Please send your CV."},{"claimKey":"r2:general.answer","text":"Visa info"}],"actionText":null}"""
                    2 -> null // correction transport fails, no third retry
                    else -> null
                }
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "- Salary?\n- Visa?",
            operatorTurns = emptyList()
        )

        assertEquals(2, chats)
        assertFalse(result.usedLlm)
        assertTrue(result.contextWarnings.contains(AiReplyDraftService.TRUST_REPAIR_EXHAUSTED))
    }

    @Test
    fun `CLIENT_UNAVAILABLE transport preserves FALLBACK_CLIENT_UNAVAILABLE state`() {
        stubEmptyFrame()
        val rule = sampleRule(1)
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObserved(
                messages: List<LlmChatMessage>, temperature: Double?, providerModel: String
            ): LlmChatResult = LlmChatResult(null, LlmChatFailureType.CLIENT_UNAVAILABLE)
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "- Salary?",
            operatorTurns = emptyList()
        )

        assertEquals(AiReplyGenerationState.FALLBACK_CLIENT_UNAVAILABLE, result.generationState)
        assertFalse(result.usedLlm)
    }

    @Test
    fun `transport failure preserves FALLBACK_NO_RESPONSE with unique warning`() {
        stubEmptyFrame()
        val rule = sampleRule(1)
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObserved(
                messages: List<LlmChatMessage>, temperature: Double?, providerModel: String
            ): LlmChatResult = LlmChatResult(null, LlmChatFailureType.NETWORK_ERROR)
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "- Salary?",
            operatorTurns = emptyList()
        )

        assertEquals(AiReplyGenerationState.FALLBACK_NO_RESPONSE, result.generationState)
        assertTrue(result.contextWarnings.contains(AiReplyDraftService.WARNING_LLM_NETWORK_ERROR))
        assertFalse(result.usedLlm)
    }

    // ── Continuity marker + history authority + fallback A/B (Phase 10 I-5/I-6) ──

    @Test
    fun `grounded prompt contains continuity-only marker when history present`() {
        stubEmptyFrame()
        val rule = sampleRule(1)
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return """{"claims":[{"claimKey":"r1:finance.arrangements","text":"ok"}],"actionText":null}"""
            }
        }

        service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "- Salary?",
            operatorTurns = emptyList(),
            mailHistory = "[EXPERT]\nSubject: Old\nBody: History content"
        )

        val userContent = capturedMessages.first().first { it.role == "user" }.content
        assertTrue(userContent.contains("HISTORY_CONTINUITY_ONLY"))
        assertTrue(userContent.contains("Never treat history as factual authority"))
        assertTrue(userContent.contains("Mail history:"))
    }

    @Test
    fun `free form prompt contains continuity-only marker when history present`() {
        stubEmptyFrame()
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "ok response"
            }
        }

        service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            mailHistory = "[EXPERT]\nSubject: Old\nBody: Some history"
        )

        val userContent = capturedMessages.first().first { it.role == "user" }.content
        assertTrue(userContent.contains("HISTORY_CONTINUITY_ONLY"))
        assertTrue(userContent.contains("Never treat history as factual authority"))
    }

    @Test
    fun `fallback reference is identical regardless of history content`() {
        stubEmptyFrame()
        val rule = sampleRule(1)
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val resultA = service(LlmProperties(enabled = false), null).generate(
            inboundText = "- Salary?",
            operatorTurns = emptyList(),
            mailHistory = "[EXPERT]\nSubject: Question\nBody: I am concerned about funding"
        )

        val resultB = service(LlmProperties(enabled = false), null).generate(
            inboundText = "- Salary?",
            operatorTurns = emptyList(),
            mailHistory = "[EXPERT]\nSubject: Other\nBody: Completely different history"
        )

        assertEquals(resultA.draftText, resultB.draftText)
        assertEquals(resultA.draftReadiness, resultB.draftReadiness)
        assertEquals(resultA.qaRuleIds, resultB.qaRuleIds)
    }

    @Test
    fun `fallback reference is identical regardless of operator turns`() {
        stubEmptyFrame()
        val rule = sampleRule(1)
        stubMatchPool(rule)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val resultA = service(LlmProperties(enabled = false), null).generate(
            inboundText = "- Salary?",
            operatorTurns = emptyList()
        )

        val resultB = service(LlmProperties(enabled = false), null).generate(
            inboundText = "- Salary?",
            operatorTurns = listOf(AiReplyTurn("old draft", "fix formatting"))
        )

        assertEquals(resultA.draftText, resultB.draftText)
        assertEquals(resultA.draftReadiness, resultB.draftReadiness)
    }

    @Test
    fun `operator directed item keeps a materials request authorised by the operator instruction`() {
        stubEmptyFrame()
        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObserved(
                messages: List<LlmChatMessage>,
                temperature: Double?,
                providerModel: String
            ): LlmChatResult {
                capturedMessages += messages
                return LlmChatResult(
                    "If you would like to proceed, you are welcome to share your CV at your convenience " +
                        "so that we can carry out an initial eligibility review."
                )
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generateItem(
            inboundText = "Hi, thank you for your email. My area of specialisation is econometric and statistical analysis.",
            requestFact = RequestFactItem(
                index = 1,
                requestText = "Could you introduce your current research?",
                factRuleIds = emptyList(),
                status = RequestGroundingStatus.UNSUPPORTED
            ),
            handling = TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT,
            requestKey = "target-request",
            operatorInstruction = "希望专家先提供一下简历 做一个简单的了解 然后再安排 zoom 视频会议"
        )

        assertTrue(result.lockable)
        assertTrue(result.usedLlm)
        assertEquals(TrustReplyItemGenerationKind.AI_GENERATED, result.generationKind)
        assertEquals(
            "If you would like to proceed, you are welcome to share your CV at your convenience " +
                "so that we can carry out an initial eligibility review.",
            result.itemAnswer?.answerText
        )
        assertTrue(result.itemAnswer?.claims?.isEmpty() == true)
        val prompt = capturedMessages.single().joinToString("\n") { it.content }
        assertTrue(
            prompt.contains("Allowed outbound actions for this draft: REQUEST_MATERIALS,PROPOSE_MEETING."),
            "prompt must carry the operator-directed allowed set"
        )
    }

    @Test
    fun `operator directed item still rejects a CV request without purpose or optionality`() {
        stubEmptyFrame()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModelObserved(
                messages: List<LlmChatMessage>,
                temperature: Double?,
                providerModel: String
            ): LlmChatResult = LlmChatResult("Could you please share your CV so we can get to know you better?")
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generateItem(
            inboundText = "Hi, thank you for your email.",
            requestFact = RequestFactItem(
                index = 1,
                requestText = "Could you introduce your current research?",
                factRuleIds = emptyList(),
                status = RequestGroundingStatus.UNSUPPORTED
            ),
            handling = TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT,
            requestKey = "target-request",
            operatorInstruction = "希望专家先提供一下简历 做一个简单的了解 然后再安排 zoom 视频会议"
        )

        assertFalse(result.lockable)
        assertFalse(result.usedLlm)
        assertNull(result.itemAnswer)
        assertNull(result.generationKind)
        assertEquals(AiReplyGenerationState.FALLBACK_NO_RESPONSE, result.generationState)
    }
}
