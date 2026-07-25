package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.llm.service.AiReplyContext
import com.weibo.talentintroduction.llm.service.AiReplyContextBuilder
import com.weibo.talentintroduction.llm.service.AiReplyContextService
import com.weibo.talentintroduction.llm.service.AiReplyReviewAuditService
import com.weibo.talentintroduction.llm.service.AiReplyDraftPreviewService
import com.weibo.talentintroduction.llm.service.AiReplyDraftResult
import com.weibo.talentintroduction.llm.service.AiReplyDraftReadiness
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiReplyGenerationState
import com.weibo.talentintroduction.llm.service.AiReplyMode
import com.weibo.talentintroduction.llm.service.AiReplyModel
import com.weibo.talentintroduction.llm.service.AiReplyProgressPhase
import com.weibo.talentintroduction.llm.service.AiReplyProgressReporter
import com.weibo.talentintroduction.llm.service.AiReplyProgressSnapshot
import com.weibo.talentintroduction.llm.service.AiReplyProviderActivity
import com.weibo.talentintroduction.llm.service.AiReplyTimeoutPolicy
import com.weibo.talentintroduction.llm.service.RequestFactItem
import com.weibo.talentintroduction.llm.service.RequestGroundingStatus
import com.weibo.talentintroduction.llm.service.AiTrainingQaService
import com.weibo.talentintroduction.llm.controller.RequestCoverageItem
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.service.AutoReplyPreviewService
import com.weibo.talentintroduction.mail.service.PendingMailOperationService
import com.weibo.talentintroduction.mail.service.UnmatchedInboundMailService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.lang.reflect.Proxy

class UnmatchedInboundAiReplyTurnKnowledgeTest {
    private val unmatchedInboundMailService = Mockito.mock(UnmatchedInboundMailService::class.java)
    private val expertEmailAliasService = Mockito.mock(ExpertEmailAliasService::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val pendingMailOperationService = Mockito.mock(PendingMailOperationService::class.java)
    private val operatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)
    private val llmProperties = LlmProperties(enabled = false)
    private val autoReplyPreviewService = Mockito.mock(AutoReplyPreviewService::class.java)
    private val aiReplyDraftService = Mockito.mock(AiReplyDraftService::class.java)
    private val aiReplyDraftPreviewService = Mockito.mock(AiReplyDraftPreviewService::class.java) { invocation ->
        if (invocation.method.name == "preview") {
            AiReplyDraftPreviewService.PreviewResult(
                renderedText = invocation.getArgument(0),
                warningCodes = emptyList()
            )
        } else {
            Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
    }
    private val aiReplyContextBuilder = AiReplyContextBuilder()
    private val aiTrainingQaService = Mockito.mock(AiTrainingQaService::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val aiReplyContextService = Mockito.mock(AiReplyContextService::class.java)
    private val aiReplyReviewAuditService = Mockito.mock(AiReplyReviewAuditService::class.java) { invocation ->
        if (invocation.method.name == "recordInitialDraft") {
            val result = invocation.getArgument<AiReplyDraftResult>(2)
            val svc = com.weibo.talentintroduction.llm.service.AiReplyReviewAuditService(
                operatorActionLogService
            )
            svc.buildSnapshot(result)
        } else if (invocation.method.name == "buildSnapshot") {
            val result = invocation.getArgument<AiReplyDraftResult>(0)
            val svc = com.weibo.talentintroduction.llm.service.AiReplyReviewAuditService(
                operatorActionLogService
            )
            svc.buildSnapshot(result)
        } else {
            Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
    }

    private val controller = UnmatchedInboundMailController(
        unmatchedInboundMailService,
        expertEmailAliasService,
        expertContactRepository,
        pendingMailOperationService,
        operatorActionLogService,
        llmProperties,
        autoReplyPreviewService,
        aiReplyDraftService,
        aiReplyDraftPreviewService,
        aiReplyContextBuilder,
        aiTrainingQaService,
        mailRecordRepository,
        aiReplyContextService,
        aiReplyReviewAuditService
    )

    private val contact = ExpertContact(
        id = 10L,
        campaignId = 1L,
        orcidId = "0000-0000-0000-0001",
        expertName = "Dr. Test",
        expertEmail = "expert@test.com",
        currentStatus = "WAITING_REPLY"
    )

    private fun <T> anyV(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNullable(): T = Mockito.any<T>() ?: null as T

    private fun <T> anyNonNull(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    @Test
    fun `aiReplyTurn injects training knowledge into expert profile via context service`() {
        val detail = InboundMailProcessing(
            id = 1L,
            senderAccountCode = "a1",
            imapUid = 1L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "Question",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(1L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
            .thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Hello"))
            .thenReturn("Topic: office mascot\nAnswer: QINGFEI-PANDA")
        val expectedProfile = "Name: Dr. Test\nTraining knowledge base:\nTopic: office mascot\nAnswer: QINGFEI-PANDA"
        val expectedContext = AiReplyContext(
            profileText = expectedProfile,
            mailHistory = "",
            contextWarnings = emptyList()
        )
        // Use exact values (not matchers) to avoid Kotlin non-null parameter check issues
        Mockito.`when`(
            aiReplyContextService.build(
                contact,
                emptyList(),
                "Hello",
                "Topic: office mascot\nAnswer: QINGFEI-PANDA"
            )
        ).thenReturn(expectedContext)

        var capturedProfile: String? = null
        var capturedWarnings: List<String>? = null
        Mockito.doAnswer { invocation ->
            capturedProfile = invocation.getArgument(4)
            capturedWarnings = invocation.getArgument(7)
            AiReplyDraftResult(
                draftText = "draft",
                usedLlm = false,
                qaRuleIds = emptyList(),
                mode = AiReplyMode.FREE_FORM
            )
        }.`when`(aiReplyDraftService).generate(
            Mockito.anyString(),
            Mockito.anyList(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.anyBoolean(),
            Mockito.anyList(),
            Mockito.any(),
            Mockito.anyBoolean()
        )

        controller.aiReplyTurn(1L, AiReplyTurnRequest())

        assertTrue(capturedProfile!!.contains("Training knowledge base:"))
        assertTrue(capturedProfile!!.contains("QINGFEI-PANDA"))
        assertEquals(emptyList<String>(), capturedWarnings)
        Mockito.verify(aiTrainingQaService).buildKnowledgeContext("Hello")
    }

    @Test
    fun `aiReplyTurn response includes new coverage fields`() {
        val detail = InboundMailProcessing(
            id = 2L,
            senderAccountCode = "a1",
            imapUid = 2L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "Query",
            body = "Question text",
            cleanedBody = "Question text",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(2L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
            .thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Question text")).thenReturn("")
        // Use exact values (not matchers) to avoid Kotlin non-null parameter check issues
        Mockito.`when`(
            aiReplyContextService.build(contact, emptyList(), "Question text", "")
        ).thenReturn(
            AiReplyContext(
                profileText = "Name: Dr. Test",
                mailHistory = "",
                contextWarnings = listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"),
                researchProfileSufficient = false
            )
        )

        Mockito.doReturn(
            AiReplyDraftResult(
                draftText = "reply draft",
                usedLlm = false,
                qaRuleIds = listOf(5L),
                mode = AiReplyMode.QA_MATCHED,
                requestCount = 1,
                groundedRequestCount = 0,
                unsupportedRequests = listOf("research scope"),
                contextWarnings = listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"),
                fewShotDialogRefs = listOf("DIALOG_1"),
                requestFacts = listOf(
                    RequestFactItem(
                        index = 1,
                        requestText = "research scope",
                        factRuleIds = emptyList(),
                        status = RequestGroundingStatus.UNSUPPORTED
                    )
                ),
                draftReadiness = AiReplyDraftReadiness.BLOCKED
            )
        ).`when`(aiReplyDraftService).generate(
            Mockito.anyString(),
            Mockito.anyList(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.anyBoolean(),
            Mockito.anyList(),
            Mockito.any(),
            Mockito.anyBoolean()
        )

        val response = controller.aiReplyTurn(2L, AiReplyTurnRequest())

        assertEquals("reply draft", response.draftText)
        assertEquals("reply draft", response.renderedDraftText)
        assertEquals(listOf(5L), response.qaRuleIds)
        assertEquals("QA_MATCHED", response.mode)
        assertEquals(1, response.requestCount)
        assertEquals(0, response.groundedRequestCount)
        assertEquals(listOf("research scope"), response.unsupportedRequests)
        assertEquals(listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"), response.contextWarnings)
        assertEquals(listOf("DIALOG_1"), response.injectedDialogRefs)
        assertEquals(AiReplyModel.DEEPSEEK_V4_FLASH.name, response.selectedModel)
        assertEquals("BLOCKED", response.draftReadiness)
        Mockito.verify(aiReplyDraftPreviewService).preview("reply draft", contact, "a1")
        assertEquals(
            listOf(
                RequestCoverageItem(
                    index = 1,
                    requestText = "research scope",
                    status = "UNSUPPORTED",
                    factRuleIds = emptyList()
                )
            ),
            response.requestCoverage
        )
    }

    @Test
    fun `aiReplyTurn maps all shared AiReplyDraftResult fields to response identically`() {
        val detail = InboundMailProcessing(
            id = 5L,
            senderAccountCode = "a1",
            imapUid = 5L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "Multi-question",
            body = "Multiple questions",
            cleanedBody = "Multiple questions",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(5L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Multiple questions")).thenReturn("")
        Mockito.`when`(
            aiReplyContextService.build(contact, emptyList(), "Multiple questions", "")
        ).thenReturn(AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList()))

        val sourceResult = AiReplyDraftResult(
            draftText = "Grounded reply body",
            usedLlm = true,
            qaRuleIds = listOf(1L, 3L),
            mode = AiReplyMode.QA_GROUNDED,
            requestCount = 2,
            groundedRequestCount = 1,
            unsupportedRequests = listOf("research scope question"),
            contextWarnings = listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"),
            fewShotDialogRefs = listOf("DIALOG_42"),
            selectedModel = AiReplyModel.DEEPSEEK_V4_PRO.name,
            requestFacts = listOf(
                RequestFactItem(1, "salary question", listOf(1L), RequestGroundingStatus.GROUNDED),
                RequestFactItem(2, "research scope question", emptyList(), RequestGroundingStatus.UNSUPPORTED)
            ),
            generationState = AiReplyGenerationState.LLM_USED,
            draftReadiness = AiReplyDraftReadiness.BLOCKED
        )
        Mockito.doReturn(sourceResult).`when`(aiReplyDraftService).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any(),
            Mockito.anyBoolean()
        )
        Mockito.`when`(
            aiReplyDraftPreviewService.preview("Grounded reply body", contact, "a1")
        ).thenReturn(
            AiReplyDraftPreviewService.PreviewResult(
                renderedText = "Grounded reply rendered",
                warningCodes = listOf("AI_REPLY_PREVIEW_INVALID_PLACEHOLDER")
            )
        )

        val response = controller.aiReplyTurn(5L, AiReplyTurnRequest())

        assertEquals(sourceResult.draftText, response.draftText)
        assertEquals("Grounded reply rendered", response.renderedDraftText)
        assertEquals(sourceResult.mode.name, response.mode)
        assertEquals(sourceResult.qaRuleIds, response.qaRuleIds)
        assertEquals(sourceResult.requestCount, response.requestCount)
        assertEquals(sourceResult.groundedRequestCount, response.groundedRequestCount)
        assertEquals(sourceResult.unsupportedRequests, response.unsupportedRequests)
        assertEquals(
            listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT", "AI_REPLY_PREVIEW_INVALID_PLACEHOLDER"),
            response.contextWarnings
        )
        assertEquals(sourceResult.fewShotDialogRefs, response.injectedDialogRefs)
        assertEquals(sourceResult.selectedModel, response.selectedModel)
        assertEquals(sourceResult.generationState.name, response.generationState)
        assertEquals(30, response.appliedLlmAttemptTimeoutSeconds)
        assertEquals(300, response.appliedLlmTotalTimeoutSeconds)
        assertEquals("BLOCKED", response.draftReadiness)
        assertEquals(
            sourceResult.requestFacts.map {
                RequestCoverageItem(it.index, it.requestText, it.status.name, it.factRuleIds)
            },
            response.requestCoverage
        )
        // Research flag stays internal on RequestFactItem; response DTO fields: index, requestText, status, factRuleIds, intents
        assertEquals(
            setOf("index", "requestText", "status", "factRuleIds", "intents"),
            RequestCoverageItem::class.java.declaredFields
                .filter { !it.isSynthetic && it.name != "Companion" }
                .map { it.name }
                .toSet()
        )
        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `stream endpoint validates canonical generation id and TTL bounds before work`() {
        val generationId = UUID.randomUUID().toString()
        assertThrows(IllegalArgumentException::class.java) {
            controller.aiReplyTurnStream(1L, AiReplyTurnRequest())
        }
        assertThrows(IllegalArgumentException::class.java) {
            controller.aiReplyTurnStream(
                1L,
                AiReplyTurnRequest(
                    generationId = generationId,
                    llmAttemptTimeoutSeconds = 9
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            controller.aiReplyTurnStream(
                1L,
                AiReplyTurnRequest(
                    generationId = generationId.uppercase(),
                    llmAttemptTimeoutSeconds = 30,
                    llmTotalTimeoutSeconds = 300
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            controller.aiReplyTurnStream(
                1L,
                AiReplyTurnRequest(
                    generationId = generationId.uppercase(),
                    llmAttemptTimeoutSeconds = 30,
                    llmTotalTimeoutSeconds = 20
                )
            )
        }
    }

    @Test
    fun `stream response disables proxy buffering and echoes an emitter`() {
        val response = controller.aiReplyTurnStream(
            1L,
            AiReplyTurnRequest(
                generationId = UUID.randomUUID().toString(),
                llmAttemptTimeoutSeconds = 30,
                llmTotalTimeoutSeconds = 300
            )
        )
        assertTrue(response.body is SseEmitter)
        assertEquals("no-cache, no-transform", response.headers.cacheControl)
        assertEquals("no", response.headers.getFirst("X-Accel-Buffering"))
    }

    @Test
    fun `active generation rejects duplicate UUID through the endpoint`() {
        val generationId = UUID.randomUUID().toString()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        Mockito.doAnswer {
            entered.countDown()
            release.await(2, TimeUnit.SECONDS)
            null
        }.`when`(unmatchedInboundMailService).getDetail(1L)
        try {
            controller.aiReplyTurnStream(
                1L,
                AiReplyTurnRequest(generationId = generationId, llmAttemptTimeoutSeconds = 30, llmTotalTimeoutSeconds = 300)
            )
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertThrows(ResponseStatusException::class.java) {
                controller.aiReplyTurnStream(
                    1L,
                    AiReplyTurnRequest(generationId = generationId, llmAttemptTimeoutSeconds = 30, llmTotalTimeoutSeconds = 300)
                )
            }
        } finally {
            release.countDown()
            controller.cancelAiReplyGeneration(1L, generationId)
        }
    }

    @Test
    fun `generation control cancel and committing states are linearized`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        try {
            val token = com.weibo.talentintroduction.llm.service.AiReplyCancellationToken()
            val emitter = Mockito.mock(SseEmitter::class.java)
            val control = newGenerationControl(token, emitter, scheduler)
            invoke(control, "markRunning")
            assertEquals("CANCEL_REQUESTED", invoke(control, "requestCancel"))
            assertTrue(token.isCancelled())

            val commitToken = com.weibo.talentintroduction.llm.service.AiReplyCancellationToken()
            val commitControl = newGenerationControl(commitToken, emitter, scheduler)
            invoke(commitControl, "markRunning")
            assertEquals(true, invoke(commitControl, "tryBeginCommit"))
            assertEquals("TOO_LATE", invoke(commitControl, "requestCancel"))
            assertTrue(!commitToken.isCancelled())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `queued progress echoes applied TTL and allowlisted fields`() {
        val payload = invoke(
            controller,
            "queuedProgress",
            arrayOf(String::class.java, AiReplyTimeoutPolicy::class.java),
            arrayOf<Any>("generation-queued", AiReplyTimeoutPolicy(60, 601))
        ) as Map<*, *>
        assertEquals("generation-queued", payload["generationId"])
        assertEquals(60, payload["attemptTimeoutSeconds"])
        assertEquals(601, payload["totalTimeoutSeconds"])
        assertEquals(
            setOf(
                "generationId", "progressSeq", "phase", "providerActivity", "providerCallIndex",
                "attemptElapsedSeconds", "attemptTimeoutSeconds", "totalElapsedSeconds",
                "totalTimeoutSeconds", "providerEventCount", "contentChars", "secondsSinceProviderActivity"
            ),
            payload.keys
        )
    }

    @Test
    fun `terminal event is sent once and heartbeat stays silent until running`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val sends = AtomicInteger()
        val emitter = Mockito.mock(SseEmitter::class.java)
        Mockito.doAnswer {
            sends.incrementAndGet()
            null
        }.`when`(emitter).send(Mockito.any(SseEmitter.SseEventBuilder::class.java))
        try {
            val control = newGenerationControl(
                com.weibo.talentintroduction.llm.service.AiReplyCancellationToken(), emitter, scheduler
            )
            val snapshot = progressSnapshot(1)
            invoke(control, "sendHeartbeat", arrayOf(AiReplyProgressSnapshot::class.java), arrayOf<Any>(snapshot))
            assertEquals(0, sends.get())
            invoke(control, "markRunning")
            invoke(control, "sendHeartbeat", arrayOf(AiReplyProgressSnapshot::class.java), arrayOf<Any>(snapshot))
            invoke(control, "sendTerminal", arrayOf(String::class.java, Any::class.java), arrayOf<Any>("result", mapOf("draft" to "ok")))
            invoke(control, "sendTerminal", arrayOf(String::class.java, Any::class.java), arrayOf<Any>("result", mapOf("draft" to "duplicate")))
            assertEquals(2, sends.get())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `active cancel cancels worker heartbeat and token`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val emitter = Mockito.mock(SseEmitter::class.java)
        val worker = Mockito.mock(Future::class.java) as Future<*>
        val heartbeat = Mockito.mock(ScheduledFuture::class.java) as ScheduledFuture<*>
        try {
            val token = com.weibo.talentintroduction.llm.service.AiReplyCancellationToken()
            val control = newGenerationControl(token, emitter, scheduler)
            setField(control, "workerFuture", worker)
            setField(control, "heartbeatFuture", heartbeat)
            invoke(control, "markRunning")

            assertEquals("CANCEL_REQUESTED", invoke(control, "requestCancel"))
            assertTrue(token.isCancelled())
            Mockito.verify(worker).cancel(true)
            Mockito.verify(heartbeat, Mockito.atLeastOnce()).cancel(false)
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `terminal event prevents later progress`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val sends = AtomicInteger()
        val emitter = Mockito.mock(SseEmitter::class.java)
        Mockito.doAnswer {
            sends.incrementAndGet()
            null
        }.`when`(emitter).send(Mockito.any(SseEmitter.SseEventBuilder::class.java))
        try {
            val control = newGenerationControl(
                com.weibo.talentintroduction.llm.service.AiReplyCancellationToken(), emitter, scheduler
            )
            invoke(control, "markRunning")
            invoke(control, "sendTerminal", arrayOf(String::class.java, Any::class.java), arrayOf<Any>("result", mapOf("draft" to "ok")))
            invoke(control, "publishProgress", arrayOf(AiReplyProgressSnapshot::class.java), arrayOf<Any>(progressSnapshot(2)))
            Thread.sleep(100)
            assertEquals(1, sends.get())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `cancel route reports not active for wrong generation or record`() {
        val generationId = UUID.randomUUID().toString()
        val response = controller.cancelAiReplyGeneration(999L, generationId)
        assertEquals(generationId, response["generationId"])
        assertEquals("NOT_ACTIVE", response["status"])
    }

    @Test
    fun `progress callback does not wait for a blocked emitter send`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val emitter = Mockito.mock(SseEmitter::class.java)
        Mockito.doAnswer {
            entered.countDown()
            release.await(2, TimeUnit.SECONDS)
            null
        }.`when`(emitter).send(Mockito.any(SseEmitter.SseEventBuilder::class.java))
        try {
            val token = com.weibo.talentintroduction.llm.service.AiReplyCancellationToken()
            val control = newGenerationControl(token, emitter, scheduler)
            invoke(control, "markRunning")
            val snapshot = AiReplyProgressSnapshot(
                generationId = "generation-1",
                progressSeq = 1,
                phase = AiReplyProgressPhase.CALLING,
                providerActivity = AiReplyProviderActivity.WAITING,
                providerCallIndex = 1,
                attemptElapsedSeconds = 0,
                attemptTimeoutSeconds = 30,
                totalElapsedSeconds = 0,
                totalTimeoutSeconds = 300,
                providerEventCount = 1,
                contentChars = 0,
                secondsSinceProviderActivity = 0
            )
            invoke(control, "publishProgress", arrayOf(AiReplyProgressSnapshot::class.java), arrayOf<Any>(snapshot))
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            val started = System.nanoTime()
            invoke(control, "publishProgress", arrayOf(AiReplyProgressSnapshot::class.java), arrayOf<Any>(snapshot.copy(progressSeq = 2)))
            assertTrue(System.nanoTime() - started < 200_000_000L)
            release.countDown()
        } finally {
            release.countDown()
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `slow emitter keeps pending same phase progress at one hertz`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val sends = AtomicInteger()
        val emitter = Mockito.mock(SseEmitter::class.java)
        Mockito.doAnswer {
            if (sends.incrementAndGet() == 1) {
                entered.countDown()
                release.await(2, TimeUnit.SECONDS)
            }
            null
        }.`when`(emitter).send(Mockito.any(SseEmitter.SseEventBuilder::class.java))
        try {
            val control = newGenerationControl(
                com.weibo.talentintroduction.llm.service.AiReplyCancellationToken(), emitter, scheduler
            )
            invoke(control, "markRunning")
            val first = progressSnapshot(1)
            invoke(control, "publishProgress", arrayOf(AiReplyProgressSnapshot::class.java), arrayOf<Any>(first))
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            invoke(
                control,
                "publishProgress",
                arrayOf(AiReplyProgressSnapshot::class.java),
                arrayOf<Any>(progressSnapshot(2))
            )
            release.countDown()
            Thread.sleep(150)
            assertEquals(1, sends.get(), "same phase pending flush must wait for the 1 Hz window")
        } finally {
            release.countDown()
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `phase change preempts delayed same phase progress flush`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val firstSent = CountDownLatch(1)
        val sends = AtomicInteger()
        val emitter = Mockito.mock(SseEmitter::class.java)
        Mockito.doAnswer {
            sends.incrementAndGet()
            firstSent.countDown()
            null
        }.`when`(emitter).send(Mockito.any(SseEmitter.SseEventBuilder::class.java))
        try {
            val control = newGenerationControl(
                com.weibo.talentintroduction.llm.service.AiReplyCancellationToken(), emitter, scheduler
            )
            invoke(control, "markRunning")
            invoke(control, "publishProgress", arrayOf(AiReplyProgressSnapshot::class.java), arrayOf<Any>(progressSnapshot(1)))
            assertTrue(firstSent.await(1, TimeUnit.SECONDS))
            invoke(control, "publishProgress", arrayOf(AiReplyProgressSnapshot::class.java), arrayOf<Any>(progressSnapshot(2)))
            invoke(
                control,
                "publishProgress",
                arrayOf(AiReplyProgressSnapshot::class.java),
                arrayOf<Any>(progressSnapshot(3, AiReplyProgressPhase.VALIDATING))
            )
            Thread.sleep(150)
            assertEquals(2, sends.get(), "phase change must flush immediately")
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `send failure cleans pending progress and prevents a second send`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val sends = AtomicInteger()
        val emitter = Mockito.mock(SseEmitter::class.java)
        Mockito.doAnswer {
            if (sends.incrementAndGet() == 1) {
                entered.countDown()
                release.await(2, TimeUnit.SECONDS)
                throw IllegalStateException("disconnected")
            }
            null
        }.`when`(emitter).send(Mockito.any(SseEmitter.SseEventBuilder::class.java))
        try {
            val control = newGenerationControl(
                com.weibo.talentintroduction.llm.service.AiReplyCancellationToken(), emitter, scheduler
            )
            invoke(control, "markRunning")
            invoke(control, "publishProgress", arrayOf(AiReplyProgressSnapshot::class.java), arrayOf<Any>(progressSnapshot(1)))
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            invoke(control, "publishProgress", arrayOf(AiReplyProgressSnapshot::class.java), arrayOf<Any>(progressSnapshot(2)))
            release.countDown()
            Thread.sleep(150)
            assertEquals(1, sends.get(), "send failure must cancel pending progress flush")
        } finally {
            release.countDown()
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `disconnect is terminal for concurrent progress callbacks`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val emitter = Mockito.mock(SseEmitter::class.java)
        val sends = AtomicInteger()
        Mockito.doAnswer {
            sends.incrementAndGet()
            null
        }.`when`(emitter).send(Mockito.any(SseEmitter.SseEventBuilder::class.java))
        try {
            val control = newGenerationControl(
                com.weibo.talentintroduction.llm.service.AiReplyCancellationToken(), emitter, scheduler
            )
            invoke(control, "markRunning")
            invoke(control, "disconnect")
            invoke(control, "publishProgress", arrayOf(AiReplyProgressSnapshot::class.java), arrayOf<Any>(progressSnapshot(1)))
            Thread.sleep(150)
            assertEquals(0, sends.get())
        } finally {
            scheduler.shutdownNow()
        }
    }

    private fun newGenerationControl(
        token: com.weibo.talentintroduction.llm.service.AiReplyCancellationToken,
        emitter: SseEmitter,
        scheduler: java.util.concurrent.ScheduledExecutorService
    ): Any {
        val type = Class.forName("com.weibo.talentintroduction.mail.controller.GenerationControl")
        val constructor = type.declaredConstructors.single().apply { isAccessible = true }
        return constructor.newInstance(1L, UUID.randomUUID().toString(), token, emitter, scheduler)
    }

    private fun invoke(control: Any, name: String, types: Array<Class<*>> = emptyArray(), args: Array<Any> = emptyArray()): Any? {
        val method = control.javaClass.getDeclaredMethod(name, *types).apply { isAccessible = true }
        return method.invoke(control, *args)
    }

    private fun setField(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(target, value)
        }
    }

    private fun progressSnapshot(
        sequence: Long,
        phase: AiReplyProgressPhase = AiReplyProgressPhase.CALLING
    ) = AiReplyProgressSnapshot(
        generationId = "generation-1",
        progressSeq = sequence,
        phase = phase,
        providerActivity = AiReplyProviderActivity.WAITING,
        providerCallIndex = 1,
        attemptElapsedSeconds = 0,
        attemptTimeoutSeconds = 30,
        totalElapsedSeconds = 0,
        totalTimeoutSeconds = 300,
        providerEventCount = sequence.toInt(),
        contentChars = 0,
        secondsSinceProviderActivity = 0
    )

    private fun endpointController(
        executor: ExecutorService,
        scheduler: ScheduledExecutorService
    ) = UnmatchedInboundMailController(
        unmatchedInboundMailService,
        expertEmailAliasService,
        expertContactRepository,
        pendingMailOperationService,
        operatorActionLogService,
        llmProperties,
        autoReplyPreviewService,
        aiReplyDraftService,
        aiReplyDraftPreviewService,
        aiReplyContextBuilder,
        aiTrainingQaService,
        mailRecordRepository,
        aiReplyContextService,
        aiReplyReviewAuditService,
        executor,
        scheduler
    )

    private fun stubEndpointContext(id: Long, text: String = "Hello") {
        val detail = InboundMailProcessing(
            id = id,
            senderAccountCode = "a1",
            imapUid = id,
            messageId = "message-$id",
            fromEmail = "expert@test.com",
            subject = "Question",
            body = text,
            cleanedBody = text,
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(id)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
            .thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext(text)).thenReturn("")
        Mockito.`when`(aiReplyContextService.build(contact, emptyList(), text, "", "message-$id"))
            .thenReturn(AiReplyContext("Name: Dr. Test", "", emptyList()))
    }

    private fun runtimeResult(text: String = "runtime draft") = AiReplyDraftResult(
        draftText = text,
        usedLlm = true,
        qaRuleIds = emptyList(),
        mode = AiReplyMode.FREE_FORM,
        generationState = AiReplyGenerationState.LLM_USED
    )

    @Test
    fun `real stream endpoint emits one result and removes generation after completion`() {
        val executor = Executors.newSingleThreadExecutor()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val id = 101L
        val generationId = UUID.randomUUID().toString()
        try {
            stubEndpointContext(id)
            val reporterPhases = mutableListOf<AiReplyProgressPhase>()
            Mockito.doAnswer { invocation ->
                val reporter = invocation.getArgument<AiReplyProgressReporter>(13)
                reporter.transition(AiReplyProgressPhase.PREPARING)
                reporterPhases += AiReplyProgressPhase.PREPARING
                reporter.transition(AiReplyProgressPhase.CALLING)
                reporterPhases += AiReplyProgressPhase.CALLING
                reporter.transition(AiReplyProgressPhase.VALIDATING)
                reporterPhases += AiReplyProgressPhase.VALIDATING
                reporter.transition(AiReplyProgressPhase.FINALIZING)
                reporterPhases += AiReplyProgressPhase.FINALIZING
                runtimeResult()
            }.`when`(aiReplyDraftService).generate(
                Mockito.anyString(), Mockito.anyList(), anyNullable(), anyNullable(), anyNullable(), anyNullable(),
                Mockito.anyBoolean(), Mockito.anyList(), anyNullable(), Mockito.anyBoolean(),
                anyNullable(), anyNullable(), anyNullable(), anyNonNull(AiReplyProgressReporter.NOOP)
            )

            val endpoint = endpointController(executor, scheduler)
            val response = endpoint.aiReplyTurnStream(
                id,
                AiReplyTurnRequest(
                    generationId = generationId,
                    llmAttemptTimeoutSeconds = 30,
                    llmTotalTimeoutSeconds = 300
                )
            )
            assertTrue(response.body is SseEmitter)
            assertEquals("no-cache, no-transform", response.headers.cacheControl)
            assertEquals("no", response.headers.getFirst("X-Accel-Buffering"))
            eventually("runtime phase trace") { reporterPhases.size == 4 }
            assertEquals(
                listOf(
                    AiReplyProgressPhase.PREPARING,
                    AiReplyProgressPhase.CALLING,
                    AiReplyProgressPhase.VALIDATING,
                    AiReplyProgressPhase.FINALIZING
                ),
                reporterPhases
            )
            Mockito.verify(aiReplyReviewAuditService).recordInitialDraft(
                inboundProcessingId = id,
                contactId = 10L,
                result = runtimeResult(),
                operatorName = null
            )
            eventually("terminal cleanup") {
                endpoint.cancelAiReplyGeneration(id, generationId)["status"] == "NOT_ACTIVE"
            }
        } finally {
            executor.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `real stream endpoint emits ready progress and one result with bounded payload`() {
        val executor = Executors.newSingleThreadExecutor()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val id = 106L
        val generationId = UUID.randomUUID().toString()
        val providerEntered = CountDownLatch(1)
        val releaseProvider = CountDownLatch(1)
        val allowResult = CountDownLatch(1)
        lateinit var chunks: MutableList<Any?>
        try {
            stubEndpointContext(id)
            Mockito.doAnswer { invocation ->
                val reporter = invocation.getArgument<AiReplyProgressReporter>(13)
                providerEntered.countDown()
                releaseProvider.await(1, TimeUnit.SECONDS)
                reporter.startBudget(AiReplyTimeoutPolicy.resolve(30, 300).budget())
                reporter.transition(AiReplyProgressPhase.PREPARING)
                allowResult.await(1, TimeUnit.SECONDS)
                runtimeResult("serialized draft")
            }.`when`(aiReplyDraftService).generate(
                Mockito.anyString(), Mockito.anyList(), anyNullable(), anyNullable(), anyNullable(), anyNullable(),
                Mockito.anyBoolean(), Mockito.anyList(), anyNullable(), Mockito.anyBoolean(),
                anyNullable(), anyNullable(), anyNullable(), anyNonNull(AiReplyProgressReporter.NOOP)
            )
            val endpoint = endpointController(executor, scheduler)
            val response = endpoint.aiReplyTurnStream(
                id,
                AiReplyTurnRequest(
                    generationId = generationId,
                    llmAttemptTimeoutSeconds = 30,
                    llmTotalTimeoutSeconds = 300
                )
            )
            chunks = captureEmitterChunks(response.body as SseEmitter).chunks
            assertTrue(providerEntered.await(1, TimeUnit.SECONDS))
            releaseProvider.countDown()
            eventually("captured progress") {
                chunks.filterIsInstance<String>().any { it.contains("event:progress") }
            }
            allowResult.countDown()
            eventually("captured result") { chunks.any { it is AiReplyTurnResponse } }
            val eventHeaders = chunks.filterIsInstance<String>()
            assertTrue(eventHeaders.any { it.contains("event:ready") })
            assertTrue(eventHeaders.any { it.contains("event:progress") })
            assertTrue(eventHeaders.count { it.contains("event:result") } == 1)
            val ready = chunks.filterIsInstance<Map<*, *>>().first { it["generationId"] == generationId }
            assertEquals(30, ready["appliedLlmAttemptTimeoutSeconds"])
            assertEquals(300, ready["appliedLlmTotalTimeoutSeconds"])
            val result = chunks.filterIsInstance<AiReplyTurnResponse>().single()
            assertEquals("serialized draft", result.draftText)
            assertTrue(chunks.filterNot { it is AiReplyTurnResponse }
                .none { it.toString().contains("prompt") || it.toString().contains("reasoning") || it.toString().contains("delta") })
            eventually("real endpoint cleanup") {
                endpoint.cancelAiReplyGeneration(id, generationId)["status"] == "NOT_ACTIVE"
            }
        } finally {
            executor.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `real endpoint emitter completion cleans active generation`() {
        val executor = Executors.newSingleThreadExecutor()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val id = 107L
        val generationId = UUID.randomUUID().toString()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            stubEndpointContext(id)
            Mockito.doAnswer { invocation ->
                entered.countDown()
                release.await(1, TimeUnit.SECONDS)
                invocation.getArgument<com.weibo.talentintroduction.llm.service.AiReplyCancellationToken>(12)
                    .throwIfCancelled()
                runtimeResult()
            }.`when`(aiReplyDraftService).generate(
                Mockito.anyString(), Mockito.anyList(), anyNullable(), anyNullable(), anyNullable(), anyNullable(),
                Mockito.anyBoolean(), Mockito.anyList(), anyNullable(), Mockito.anyBoolean(),
                anyNullable(), anyNullable(), anyNullable(), anyNonNull(AiReplyProgressReporter.NOOP)
            )
            val endpoint = endpointController(executor, scheduler)
            val emitter = endpoint.aiReplyTurnStream(
                id,
                AiReplyTurnRequest(generationId = generationId, llmAttemptTimeoutSeconds = 30, llmTotalTimeoutSeconds = 300)
            ).body as SseEmitter
            val capture = captureEmitterChunks(emitter)
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            emitter.complete()
            eventually("completion cleanup") {
                endpoint.cancelAiReplyGeneration(id, generationId)["status"] == "NOT_ACTIVE"
            }
            assertTrue(capture.completionCallbacks.isNotEmpty())
        } finally {
            release.countDown()
            executor.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `real endpoint emitter timeout cleans active generation`() {
        val executor = Executors.newSingleThreadExecutor()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val id = 108L
        val generationId = UUID.randomUUID().toString()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            stubEndpointContext(id)
            Mockito.doAnswer { invocation ->
                entered.countDown()
                release.await(1, TimeUnit.SECONDS)
                invocation.getArgument<com.weibo.talentintroduction.llm.service.AiReplyCancellationToken>(12)
                    .throwIfCancelled()
                runtimeResult()
            }.`when`(aiReplyDraftService).generate(
                Mockito.anyString(), Mockito.anyList(), anyNullable(), anyNullable(), anyNullable(), anyNullable(),
                Mockito.anyBoolean(), Mockito.anyList(), anyNullable(), Mockito.anyBoolean(),
                anyNullable(), anyNullable(), anyNullable(), anyNonNull(AiReplyProgressReporter.NOOP)
            )
            val endpoint = endpointController(executor, scheduler)
            val emitter = endpoint.aiReplyTurnStream(
                id,
                AiReplyTurnRequest(generationId = generationId, llmAttemptTimeoutSeconds = 30, llmTotalTimeoutSeconds = 300)
            ).body as SseEmitter
            val capture = captureEmitterChunks(emitter)
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertTrue(capture.timeoutCallbacks.isNotEmpty())
            capture.timeoutCallbacks.single().run()
            eventually("timeout cleanup") {
                endpoint.cancelAiReplyGeneration(id, generationId)["status"] == "NOT_ACTIVE"
            }
        } finally {
            release.countDown()
            executor.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `real endpoint keeps same phase progress at one hertz`() {
        val executor = Executors.newSingleThreadExecutor()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val id = 109L
        val generationId = UUID.randomUUID().toString()
        val firstPublished = CountDownLatch(1)
        val releaseSecond = CountDownLatch(1)
        val releaseResult = CountDownLatch(1)
        try {
            stubEndpointContext(id)
            Mockito.doAnswer { invocation ->
                val reporter = invocation.getArgument<AiReplyProgressReporter>(13)
                reporter.startBudget(AiReplyTimeoutPolicy.resolve(30, 300).budget())
                val sink = reporter.beginProviderCall(AiReplyProgressPhase.CALLING, 30_000L)
                sink.onActivity(com.weibo.talentintroduction.llm.service.LlmStreamActivity.WRITING, 1, 1)
                firstPublished.countDown()
                releaseSecond.await(1, TimeUnit.SECONDS)
                sink.onActivity(com.weibo.talentintroduction.llm.service.LlmStreamActivity.WRITING, 2, 2)
                releaseResult.await(1, TimeUnit.SECONDS)
                runtimeResult()
            }.`when`(aiReplyDraftService).generate(
                Mockito.anyString(), Mockito.anyList(), anyNullable(), anyNullable(), anyNullable(), anyNullable(),
                Mockito.anyBoolean(), Mockito.anyList(), anyNullable(), Mockito.anyBoolean(),
                anyNullable(), anyNullable(), anyNullable(), anyNonNull(AiReplyProgressReporter.NOOP)
            )
            val endpoint = endpointController(executor, scheduler)
            val emitter = endpoint.aiReplyTurnStream(
                id,
                AiReplyTurnRequest(generationId = generationId, llmAttemptTimeoutSeconds = 30, llmTotalTimeoutSeconds = 300)
            ).body as SseEmitter
            val capture = captureEmitterChunks(emitter)
            assertTrue(firstPublished.await(1, TimeUnit.SECONDS))
            releaseSecond.countDown()
            eventually("first endpoint progress") { progressEventCount(capture) >= 1 }
            assertEquals(1, progressEventCount(capture), "endpoint same phase progress must wait for the 1 Hz window")
            releaseResult.countDown()
        } finally {
            releaseSecond.countDown()
            releaseResult.countDown()
            executor.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `real endpoint phase change preempts delayed progress flush`() {
        val executor = Executors.newSingleThreadExecutor()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val id = 110L
        val generationId = UUID.randomUUID().toString()
        val ready = CountDownLatch(1)
        val releasePhase = CountDownLatch(1)
        val releaseResult = CountDownLatch(1)
        try {
            stubEndpointContext(id)
            Mockito.doAnswer { invocation ->
                val reporter = invocation.getArgument<AiReplyProgressReporter>(13)
                reporter.startBudget(AiReplyTimeoutPolicy.resolve(30, 300).budget())
                val sink = reporter.beginProviderCall(AiReplyProgressPhase.CALLING, 30_000L)
                sink.onActivity(com.weibo.talentintroduction.llm.service.LlmStreamActivity.WRITING, 1, 1)
                ready.countDown()
                releasePhase.await(1, TimeUnit.SECONDS)
                sink.onActivity(com.weibo.talentintroduction.llm.service.LlmStreamActivity.WRITING, 2, 2)
                reporter.transition(AiReplyProgressPhase.VALIDATING)
                releaseResult.await(1, TimeUnit.SECONDS)
                runtimeResult()
            }.`when`(aiReplyDraftService).generate(
                Mockito.anyString(), Mockito.anyList(), anyNullable(), anyNullable(), anyNullable(), anyNullable(),
                Mockito.anyBoolean(), Mockito.anyList(), anyNullable(), Mockito.anyBoolean(),
                anyNullable(), anyNullable(), anyNullable(), anyNonNull(AiReplyProgressReporter.NOOP)
            )
            val endpoint = endpointController(executor, scheduler)
            val emitter = endpoint.aiReplyTurnStream(
                id,
                AiReplyTurnRequest(generationId = generationId, llmAttemptTimeoutSeconds = 30, llmTotalTimeoutSeconds = 300)
            ).body as SseEmitter
            val capture = captureEmitterChunks(emitter)
            assertTrue(ready.await(1, TimeUnit.SECONDS))
            releasePhase.countDown()
            eventually("phase endpoint progress") { progressEventCount(capture) >= 1 }
            assertEquals(2, progressEventCount(capture), "endpoint phase change must preempt delayed progress")
            releaseResult.countDown()
        } finally {
            releasePhase.countDown()
            releaseResult.countDown()
            executor.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `real endpoint send failure cancels pending progress flush`() {
        val executor = Executors.newSingleThreadExecutor()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val id = 111L
        val generationId = UUID.randomUUID().toString()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            stubEndpointContext(id)
            Mockito.doAnswer { invocation ->
                val reporter = invocation.getArgument<AiReplyProgressReporter>(13)
                entered.countDown()
                release.await(1, TimeUnit.SECONDS)
                reporter.startBudget(AiReplyTimeoutPolicy.resolve(30, 300).budget())
                val sink = reporter.beginProviderCall(AiReplyProgressPhase.CALLING, 30_000L)
                sink.onActivity(com.weibo.talentintroduction.llm.service.LlmStreamActivity.WRITING, 1, 1)
                sink.onActivity(com.weibo.talentintroduction.llm.service.LlmStreamActivity.WRITING, 2, 2)
                runtimeResult()
            }.`when`(aiReplyDraftService).generate(
                Mockito.anyString(), Mockito.anyList(), anyNullable(), anyNullable(), anyNullable(), anyNullable(),
                Mockito.anyBoolean(), Mockito.anyList(), anyNullable(), Mockito.anyBoolean(),
                anyNullable(), anyNullable(), anyNullable(), anyNonNull(AiReplyProgressReporter.NOOP)
            )
            val endpoint = endpointController(executor, scheduler)
            val emitter = endpoint.aiReplyTurnStream(
                id,
                AiReplyTurnRequest(generationId = generationId, llmAttemptTimeoutSeconds = 30, llmTotalTimeoutSeconds = 300)
            ).body as SseEmitter
            val capture = captureEmitterChunks(emitter, failOnEvent = "event:progress")
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            release.countDown()
            eventually("send failure cleanup") {
                endpoint.cancelAiReplyGeneration(id, generationId)["status"] == "NOT_ACTIVE"
            }
            assertEquals(2, capture.sendAttempts.get(), "endpoint send failure must not retry pending progress")
        } finally {
            release.countDown()
            executor.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `real endpoint disconnect is terminal for later reporter callbacks`() {
        val executor = Executors.newSingleThreadExecutor()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val id = 112L
        val generationId = UUID.randomUUID().toString()
        val entered = CountDownLatch(1)
        val allowCallback = CountDownLatch(1)
        val releaseResult = CountDownLatch(1)
        try {
            stubEndpointContext(id)
            Mockito.doAnswer { invocation ->
                val reporter = invocation.getArgument<AiReplyProgressReporter>(13)
                entered.countDown()
                allowCallback.await(1, TimeUnit.SECONDS)
                reporter.startBudget(AiReplyTimeoutPolicy.resolve(30, 300).budget())
                reporter.transition(AiReplyProgressPhase.CALLING)
                releaseResult.await(1, TimeUnit.SECONDS)
                runtimeResult()
            }.`when`(aiReplyDraftService).generate(
                Mockito.anyString(), Mockito.anyList(), anyNullable(), anyNullable(), anyNullable(), anyNullable(),
                Mockito.anyBoolean(), Mockito.anyList(), anyNullable(), Mockito.anyBoolean(),
                anyNullable(), anyNullable(), anyNullable(), anyNonNull(AiReplyProgressReporter.NOOP)
            )
            val endpoint = endpointController(executor, scheduler)
            val emitter = endpoint.aiReplyTurnStream(
                id,
                AiReplyTurnRequest(generationId = generationId, llmAttemptTimeoutSeconds = 30, llmTotalTimeoutSeconds = 300)
            ).body as SseEmitter
            val capture = captureEmitterChunks(emitter)
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            emitter.complete()
            allowCallback.countDown()
            eventually("disconnect callback") { capture.sendAttempts.get() >= 1 }
            assertEquals(0, progressEventCount(capture), "disconnect must prevent later endpoint progress")
            releaseResult.countDown()
        } finally {
            allowCallback.countDown()
            releaseResult.countDown()
            executor.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    private fun progressEventCount(capture: CapturedEmitter): Int =
        capture.chunks.filterIsInstance<String>().count { it.contains("event:progress") }

    private data class CapturedEmitter(
        val chunks: MutableList<Any?>,
        val timeoutCallbacks: MutableList<Runnable>,
        val completionCallbacks: MutableList<Runnable>,
        val sendAttempts: AtomicInteger
    )

    private fun captureEmitterChunks(
        emitter: SseEmitter,
        failOnEvent: String? = null
    ): CapturedEmitter {
        val chunks = java.util.Collections.synchronizedList(mutableListOf<Any?>())
        val timeoutCallbacks = java.util.Collections.synchronizedList(mutableListOf<Runnable>())
        val completionCallbacks = java.util.Collections.synchronizedList(mutableListOf<Runnable>())
        val sendAttempts = AtomicInteger()
        val handlerType = Class.forName(
            "org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter\$Handler"
        )
        val handler = Proxy.newProxyInstance(
            handlerType.classLoader,
            arrayOf(handlerType)
        ) { _, method, args ->
            when (method.name) {
                "send" -> {
                    sendAttempts.incrementAndGet()
                    val data = args?.get(0)
                    if (failOnEvent != null && data?.toString()?.contains(failOnEvent) == true) {
                        throw java.io.IOException("test emitter disconnected")
                    }
                    chunks += data
                }
                "onTimeout" -> timeoutCallbacks += args?.get(0) as Runnable
                "onCompletion" -> completionCallbacks += args?.get(0) as Runnable
                "onError" -> Unit
                "complete", "completeWithError" -> completionCallbacks.toList().forEach { it.run() }
            }
            null
        }
        val initialize = emitter.javaClass.superclass.getDeclaredMethod("initialize", handlerType)
        initialize.isAccessible = true
        initialize.invoke(emitter, handler)
        return CapturedEmitter(chunks, timeoutCallbacks, completionCallbacks, sendAttempts)
    }

    @Test
    fun `real endpoint cancel first skips audit and cleans registry`() {
        val executor = Executors.newSingleThreadExecutor()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val id = 102L
        val generationId = UUID.randomUUID().toString()
        val providerEntered = CountDownLatch(1)
        try {
            stubEndpointContext(id)
            Mockito.doAnswer { invocation ->
                providerEntered.countDown()
                val token = invocation.getArgument<com.weibo.talentintroduction.llm.service.AiReplyCancellationToken>(12)
                try {
                    Thread.sleep(2_000)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                token.throwIfCancelled()
                runtimeResult()
            }.`when`(aiReplyDraftService).generate(
                Mockito.anyString(), Mockito.anyList(), anyNullable(), anyNullable(), anyNullable(), anyNullable(),
                Mockito.anyBoolean(), Mockito.anyList(), anyNullable(), Mockito.anyBoolean(),
                anyNullable(), anyNullable(), anyNullable(), anyNonNull(AiReplyProgressReporter.NOOP)
            )
            val endpoint = endpointController(executor, scheduler)
            endpoint.aiReplyTurnStream(
                id,
                AiReplyTurnRequest(generationId = generationId, llmAttemptTimeoutSeconds = 30, llmTotalTimeoutSeconds = 300)
            )
            assertTrue(providerEntered.await(1, TimeUnit.SECONDS))
            assertEquals("CANCEL_REQUESTED", endpoint.cancelAiReplyGeneration(id, generationId)["status"])
            eventually("cancel cleanup") {
                endpoint.cancelAiReplyGeneration(id, generationId)["status"] == "NOT_ACTIVE"
            }
            Mockito.verify(aiReplyReviewAuditService, Mockito.never()).recordInitialDraft(
                Mockito.anyLong(), Mockito.anyLong(), anyNonNull(runtimeResult()), anyNullable()
            )
        } finally {
            executor.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `real endpoint commit first returns too late and audits once`() {
        val executor = Executors.newSingleThreadExecutor()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val id = 103L
        val generationId = UUID.randomUUID().toString()
        val auditEntered = CountDownLatch(1)
        val releaseAudit = CountDownLatch(1)
        try {
            stubEndpointContext(id)
            Mockito.doReturn(runtimeResult()).`when`(aiReplyDraftService).generate(
                Mockito.anyString(), Mockito.anyList(), anyNullable(), anyNullable(), anyNullable(), anyNullable(),
                Mockito.anyBoolean(), Mockito.anyList(), anyNullable(), Mockito.anyBoolean(),
                anyNullable(), anyNullable(), anyNullable(), anyNonNull(AiReplyProgressReporter.NOOP)
            )
            Mockito.doAnswer { invocation ->
                auditEntered.countDown()
                releaseAudit.await(1, TimeUnit.SECONDS)
                val result = invocation.getArgument<AiReplyDraftResult>(2)
                com.weibo.talentintroduction.llm.service.AiReplyReviewAuditService(operatorActionLogService)
                    .buildSnapshot(result)
            }.`when`(aiReplyReviewAuditService).recordInitialDraft(
                Mockito.anyLong(), Mockito.anyLong(), anyNonNull(runtimeResult()), anyNullable()
            )
            val endpoint = endpointController(executor, scheduler)
            endpoint.aiReplyTurnStream(
                id,
                AiReplyTurnRequest(generationId = generationId, llmAttemptTimeoutSeconds = 30, llmTotalTimeoutSeconds = 300)
            )
            assertTrue(auditEntered.await(1, TimeUnit.SECONDS))
            assertEquals("TOO_LATE", endpoint.cancelAiReplyGeneration(id, generationId)["status"])
            releaseAudit.countDown()
            eventually("commit cleanup") {
                endpoint.cancelAiReplyGeneration(id, generationId)["status"] == "NOT_ACTIVE"
            }
            Mockito.verify(aiReplyReviewAuditService).recordInitialDraft(
                Mockito.anyLong(), Mockito.anyLong(), anyNonNull(runtimeResult()), anyNullable()
            )
        } finally {
            releaseAudit.countDown()
            executor.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `real endpoint provider error and rejected executor both cleanup`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        try {
            val errorExecutor = Executors.newSingleThreadExecutor()
            val errorId = 104L
            val errorGenerationId = UUID.randomUUID().toString()
            try {
                stubEndpointContext(errorId)
                Mockito.doAnswer { throw IllegalStateException("provider failed") }.`when`(aiReplyDraftService).generate(
                    Mockito.anyString(), Mockito.anyList(), anyNullable(), anyNullable(), anyNullable(), anyNullable(),
                    Mockito.anyBoolean(), Mockito.anyList(), anyNullable(), Mockito.anyBoolean(),
                    anyNullable(), anyNullable(), anyNullable(), anyNonNull(AiReplyProgressReporter.NOOP)
                )
                val endpoint = endpointController(errorExecutor, scheduler)
                endpoint.aiReplyTurnStream(
                    errorId,
                    AiReplyTurnRequest(generationId = errorGenerationId, llmAttemptTimeoutSeconds = 30, llmTotalTimeoutSeconds = 300)
                )
                eventually("error cleanup") {
                    endpoint.cancelAiReplyGeneration(errorId, errorGenerationId)["status"] == "NOT_ACTIVE"
                }
            } finally {
                errorExecutor.shutdownNow()
            }

            val rejectedExecutor = Executors.newSingleThreadExecutor()
            rejectedExecutor.shutdownNow()
            val rejectedId = 105L
            val rejectedGenerationId = UUID.randomUUID().toString()
            stubEndpointContext(rejectedId)
            val endpoint = endpointController(rejectedExecutor, scheduler)
            endpoint.aiReplyTurnStream(
                rejectedId,
                AiReplyTurnRequest(generationId = rejectedGenerationId, llmAttemptTimeoutSeconds = 30, llmTotalTimeoutSeconds = 300)
            )
            eventually("rejected cleanup") {
                endpoint.cancelAiReplyGeneration(rejectedId, rejectedGenerationId)["status"] == "NOT_ACTIVE"
            }
        } finally {
            scheduler.shutdownNow()
        }
    }

    private fun eventually(label: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue(condition(), label)
    }

    @Test
    fun `aiReplyTurn throws when expertContactId is null`() {
        val detail = InboundMailProcessing(
            id = 3L,
            senderAccountCode = "a1",
            imapUid = 3L,
            messageId = null,
            fromEmail = "unknown@test.com",
            subject = "?",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = null
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(3L)).thenReturn(detail)

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            controller.aiReplyTurn(3L, AiReplyTurnRequest())
        }
    }
    @Test
    fun `aiReplyTurn accepts model enum and rejects unknown`() {
        val detail = InboundMailProcessing(
            id = 9L,
            senderAccountCode = "a1",
            imapUid = 9L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "Model",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(9L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Hello")).thenReturn("")
        Mockito.`when`(aiReplyContextService.build(contact, emptyList(), "Hello", ""))
            .thenReturn(AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList()))

        var capturedModel: String? = "unset"
        Mockito.doAnswer { invocation ->
            capturedModel = invocation.getArgument(8)
            AiReplyDraftResult(
                draftText = "draft",
                usedLlm = false,
                qaRuleIds = emptyList(),
                mode = AiReplyMode.FREE_FORM,
                selectedModel = AiReplyModel.DEEPSEEK_V4_PRO.name
            )
        }.`when`(aiReplyDraftService).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any(),
            Mockito.anyBoolean()
        )

        val response = controller.aiReplyTurn(9L, AiReplyTurnRequest(model = "DEEPSEEK_V4_PRO"))
        assertEquals("DEEPSEEK_V4_PRO", capturedModel)
        assertEquals(AiReplyModel.DEEPSEEK_V4_PRO.name, response.selectedModel)

        // unknown model is validated inside DraftService; controller passes through
        capturedModel = "unset"
        Mockito.doAnswer { invocation ->
            capturedModel = invocation.getArgument(8)
            throw IllegalArgumentException("Unknown AI reply model: DEEPSEEK_UNKNOWN")
        }.`when`(aiReplyDraftService).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any(),
            Mockito.anyBoolean()
        )
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            controller.aiReplyTurn(9L, AiReplyTurnRequest(model = "DEEPSEEK_UNKNOWN"))
        }
        assertEquals("DEEPSEEK_UNKNOWN", capturedModel)
    }

    @Test
    fun `aiReplyTurn records initial draft on first turn`() {
        val detail = InboundMailProcessing(
            id = 6L,
            senderAccountCode = "a1",
            imapUid = 6L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "First turn",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(6L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Hello")).thenReturn("")
        Mockito.`when`(aiReplyContextService.build(contact, emptyList(), "Hello", ""))
            .thenReturn(AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList()))

        Mockito.doReturn(
            AiReplyDraftResult(
                draftText = "draft",
                usedLlm = false,
                qaRuleIds = emptyList(),
                mode = AiReplyMode.FREE_FORM
            )
        ).`when`(aiReplyDraftService).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any(),
            Mockito.anyBoolean()
        )

        val response = controller.aiReplyTurn(6L, AiReplyTurnRequest(operatorName = "op"))

        assertEquals("draft", response.draftText)
        assertEquals("READY", response.draftReadiness)
        Mockito.verify(aiReplyReviewAuditService).recordInitialDraft(
            anyV(0L), anyV(0L), anyV(AiReplyDraftResult("", false, emptyList(), AiReplyMode.FREE_FORM)), anyV("op")
        )
    }

    @Test
    fun `aiReplyTurn still returns draft when audit logging fails`() {
        val detail = InboundMailProcessing(
            id = 60L,
            senderAccountCode = "a1",
            imapUid = 60L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "First turn fail",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(60L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Hello")).thenReturn("")
        Mockito.`when`(aiReplyContextService.build(contact, emptyList(), "Hello", ""))
            .thenReturn(AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList()))

        Mockito.doReturn(
            AiReplyDraftResult(
                draftText = "draft body here",
                usedLlm = true,
                qaRuleIds = listOf(1L),
                mode = AiReplyMode.QA_GROUNDED
            )
        ).`when`(aiReplyDraftService).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any(),
            Mockito.anyBoolean()
        )

        val failingLogService = Mockito.mock(OperatorActionLogService::class.java)
        Mockito.doThrow(RuntimeException("DB error")).`when`(failingLogService).record(
            anyNonNull(""), anyNonNull(0L), anyNonNull(OperatorActionType.AI_REPLY_DRAFT_READY),
            anyNullable(), anyNullable(), anyNullable(), anyNullable(),
            anyNullable(), anyNullable(), anyNullable()
        )
        val auditWithFailingLog = AiReplyReviewAuditService(failingLogService)
        val failingController = UnmatchedInboundMailController(
            unmatchedInboundMailService,
            expertEmailAliasService,
            expertContactRepository,
            pendingMailOperationService,
            operatorActionLogService,
            llmProperties,
            autoReplyPreviewService,
            aiReplyDraftService,
            aiReplyDraftPreviewService,
            aiReplyContextBuilder,
            aiTrainingQaService,
            mailRecordRepository,
            aiReplyContextService,
            auditWithFailingLog
        )
        Mockito.`when`(aiReplyDraftPreviewService.preview("draft body here", contact, "a1"))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult(renderedText = "rendered draft body here", warningCodes = emptyList()))

        val response = failingController.aiReplyTurn(60L, AiReplyTurnRequest(operatorName = "op"))

        assertEquals("draft body here", response.draftText)
        assertEquals("rendered draft body here", response.renderedDraftText)
        assertEquals("READY", response.draftReadiness)
        Mockito.verify(aiReplyDraftPreviewService).preview("draft body here", contact, "a1")
    }

    @Test
    fun `aiReplyTurn does NOT record initial draft on subsequent turn`() {
        val detail = InboundMailProcessing(
            id = 7L,
            senderAccountCode = "a1",
            imapUid = 7L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "Second turn",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(7L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Hello")).thenReturn("")
        Mockito.`when`(aiReplyContextService.build(contact, emptyList(), "Hello", ""))
            .thenReturn(AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList()))

        Mockito.doReturn(
            AiReplyDraftResult(
                draftText = "draft v2",
                usedLlm = false,
                qaRuleIds = emptyList(),
                mode = AiReplyMode.FREE_FORM
            )
        ).`when`(aiReplyDraftService).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any(),
            Mockito.anyBoolean()
        )

        val response = controller.aiReplyTurn(7L, AiReplyTurnRequest(
            turns = listOf(AiReplyTurnDto(assistantDraft = "v1", operatorInstruction = "fix"))
        ))

        assertEquals("draft v2", response.draftText)
        assertEquals("READY", response.draftReadiness)
        Mockito.verify(aiReplyReviewAuditService, Mockito.never()).recordInitialDraft(
            anyV(0L), anyV(0L), anyV(AiReplyDraftResult("", false, emptyList(), AiReplyMode.FREE_FORM)), anyV("")
        )
    }

    @Test
    fun `aiReplyTurn generates draft on continuation without authority lookup`() {
        val detail = InboundMailProcessing(
            id = 71L,
            senderAccountCode = "a1",
            imapUid = 71L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "Forged continuation",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(71L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Hello")).thenReturn("")
        Mockito.`when`(aiReplyContextService.build(contact, emptyList(), "Hello", ""))
            .thenReturn(AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList()))

        Mockito.doReturn(
            AiReplyDraftResult(
                draftText = "continuation draft",
                usedLlm = false,
                qaRuleIds = emptyList(),
                mode = AiReplyMode.FREE_FORM
            )
        ).`when`(aiReplyDraftService).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any(),
            Mockito.anyBoolean()
        )

        val response = controller.aiReplyTurn(71L, AiReplyTurnRequest(
            turns = listOf(AiReplyTurnDto(assistantDraft = "v1", operatorInstruction = "fix"))
        ))

        assertEquals("continuation draft", response.draftText)
        Mockito.verify(aiReplyDraftService).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any(),
            Mockito.anyBoolean()
        )
    }

    @Test
    fun `aiReplyTurn continuation succeeds even when historical draft logs are corrupt`() {
        val detail = InboundMailProcessing(
            id = 72L,
            senderAccountCode = "a1",
            imapUid = 72L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "Corrupt continuation",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(72L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Hello")).thenReturn("")
        Mockito.`when`(aiReplyContextService.build(contact, emptyList(), "Hello", ""))
            .thenReturn(AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList()))

        Mockito.doReturn(
            AiReplyDraftResult(
                draftText = "draft v3",
                usedLlm = true,
                qaRuleIds = emptyList(),
                mode = AiReplyMode.FREE_FORM,
                draftReadiness = AiReplyDraftReadiness.BLOCKED
            )
        ).`when`(aiReplyDraftService).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any(),
            Mockito.anyBoolean()
        )

        val response = controller.aiReplyTurn(72L, AiReplyTurnRequest(
            turns = listOf(AiReplyTurnDto(assistantDraft = "v1", operatorInstruction = "fix"))
        ))

        assertEquals("draft v3", response.draftText)
        assertEquals("BLOCKED", response.draftReadiness)
    }

    // ── MessageId passthrough (Phase 10 I-2) ──

    @Test
    fun `aiReplyTurn passes current inbound messageId to context service`() {
        val detail = InboundMailProcessing(
            id = 1L,
            senderAccountCode = "a1",
            imapUid = 1L,
            messageId = "<msg-test-001@example.com>",
            fromEmail = "expert@test.com",
            subject = "Question",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(1L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
            .thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Hello")).thenReturn("")

        var capturedMessageId: String? = "unset"
        Mockito.doAnswer { invocation ->
            capturedMessageId = invocation.getArgument(4) as String?
            AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList())
        }.`when`(aiReplyContextService).build(
            contact, emptyList(), "Hello", "", "<msg-test-001@example.com>"
        )

        Mockito.doReturn(
            AiReplyDraftResult(
                draftText = "draft",
                usedLlm = true,
                qaRuleIds = emptyList(),
                mode = AiReplyMode.FREE_FORM
            )
        ).`when`(aiReplyDraftService).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any(),
            Mockito.anyBoolean()
        )

        controller.aiReplyTurn(1L, AiReplyTurnRequest())

        assertEquals("<msg-test-001@example.com>", capturedMessageId)
    }

    @Test
    fun `aiReplyTurn passes final filtered history to draft service`() {
        val now = LocalDateTime.now()
        val detail = InboundMailProcessing(
            id = 81L,
            senderAccountCode = "a1",
            imapUid = 81L,
            messageId = " <CURRENT@example.com> ",
            fromEmail = "expert@test.com",
            subject = "Current question",
            body = "CURRENT_BODY_MUST_BE_EXCLUDED",
            cleanedBody = "CURRENT_BODY_MUST_BE_EXCLUDED",
            receivedAt = now,
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        fun record(
            id: Long,
            direction: String,
            messageId: String?,
            body: String,
            sendStatus: String?,
            time: LocalDateTime
        ) = MailRecord(
            id = id,
            expertContactId = 10L,
            direction = direction,
            mailType = "REPLY",
            messageId = messageId,
            inReplyTo = null,
            subject = "subject-$id",
            body = body,
            cleanedBody = body,
            matchedQaRuleId = null,
            sendStatus = sendStatus,
            receivedAt = if (direction == "INBOUND") time else null,
            sentAt = if (direction == "OUTBOUND") time else null,
            createdAt = time
        )
        val records = listOf(
            record(1L, "INBOUND", "old@example.com", "OLD_INBOUND_INCLUDED", null, now.minusDays(3)),
            record(2L, "OUTBOUND", "sent@example.com", "SENT_OUTBOUND_INCLUDED", "SENT", now.minusDays(2)),
            record(3L, "OUTBOUND", "failed@example.com", "FAILED_OUTBOUND_EXCLUDED", "FAILED", now.minusDays(1)),
            record(4L, "OUTBOUND", "pending@example.com", "PENDING_OUTBOUND_EXCLUDED", "PENDING", now.minusHours(12)),
            record(5L, "INBOUND", "current@example.com", "CURRENT_BODY_MUST_BE_EXCLUDED", null, now)
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(81L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
            .thenReturn(records)
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("CURRENT_BODY_MUST_BE_EXCLUDED")).thenReturn("")
        Mockito.doAnswer { invocation ->
            val history = aiReplyContextBuilder.buildMailHistory(records, invocation.getArgument(4))
            AiReplyContext(profileText = "Name: Dr. Test", mailHistory = history, contextWarnings = emptyList())
        }.`when`(aiReplyContextService).build(
            contact, records, "CURRENT_BODY_MUST_BE_EXCLUDED", "", " <CURRENT@example.com> "
        )

        var capturedHistory: String? = null
        Mockito.doAnswer { invocation ->
            capturedHistory = invocation.getArgument(5)
            AiReplyDraftResult("draft", true, emptyList(), AiReplyMode.FREE_FORM)
        }.`when`(aiReplyDraftService).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any(),
            Mockito.anyBoolean()
        )

        controller.aiReplyTurn(81L, AiReplyTurnRequest())

        assertTrue(capturedHistory!!.contains("OLD_INBOUND_INCLUDED"))
        assertTrue(capturedHistory!!.contains("SENT_OUTBOUND_INCLUDED"))
        assertTrue(!capturedHistory!!.contains("FAILED_OUTBOUND_EXCLUDED"))
        assertTrue(!capturedHistory!!.contains("PENDING_OUTBOUND_EXCLUDED"))
        assertTrue(!capturedHistory!!.contains("CURRENT_BODY_MUST_BE_EXCLUDED"))
    }
}
