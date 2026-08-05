package com.weibo.talentintroduction.llm.controller

import com.weibo.talentintroduction.llm.service.AiReplyGenerationCoordinator
import com.weibo.talentintroduction.llm.service.AiReplyGenerationOperation
import com.weibo.talentintroduction.llm.service.AiReplyTimeoutPolicy
import com.weibo.talentintroduction.llm.service.TrustReplyBootstrapRequest
import com.weibo.talentintroduction.llm.service.TrustReplyBootstrapResponse
import com.weibo.talentintroduction.llm.service.TrustReplySourceRef
import com.weibo.talentintroduction.llm.service.TrustReplySourceType
import com.weibo.talentintroduction.llm.service.TrustReplyWorkbenchService
import com.weibo.talentintroduction.llm.service.TrustReplyAssembleRequest
import com.weibo.talentintroduction.llm.service.TrustReplyAssembleResponse
import com.weibo.talentintroduction.llm.service.TrustReplyItemGenerationKind
import com.weibo.talentintroduction.llm.service.TrustReplyItemHandling
import com.weibo.talentintroduction.llm.service.TrustReplyItemVersion
import com.weibo.talentintroduction.llm.service.TrustReplyLockedItemRequest
import com.weibo.talentintroduction.llm.service.TrustReplySaveStateRequest
import com.weibo.talentintroduction.llm.service.TrustReplySavedState
import com.weibo.talentintroduction.llm.service.TrustReplyWorkbenchException
import com.weibo.talentintroduction.llm.service.TrustReplyRequestFactSelection
import com.weibo.talentintroduction.llm.service.TrustReplyFrameSelection
import com.weibo.talentintroduction.llm.service.TrustReplyFrameSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.mockito.Mockito
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

class TrustReplyWorkbenchControllerTest {
    private val service = Mockito.mock(TrustReplyWorkbenchService::class.java)
    private val coordinator = Mockito.mock(AiReplyGenerationCoordinator::class.java)
    private val controller = TrustReplyWorkbenchController(service, coordinator)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    @Test
    fun `bootstrap maps exact source`() {
        val source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L)
        val bootstrap = TrustReplyBootstrapResponse(
            source = source,
            sourceVersion = "source-v1",
            inboundSubject = "Subject",
            inboundText = "Question",
            expertName = "Test",
            expertEmail = "test@example.com",
            llmEnabled = false,
            availableModels = listOf("DEEPSEEK_V4_FLASH", "DEEPSEEK_V4_PRO"),
            defaultModel = "DEEPSEEK_V4_FLASH",
            suggestedFactIds = listOf(9L),
            canonicalFactIds = listOf(9L),
            rulesByCategory = emptyList(),
            requestCoverage = emptyList(),
            draftReadiness = "READY",
            evidenceSetVersion = "evidence-v1"
        )
        Mockito.`when`(service.bootstrap(TrustReplyBootstrapRequest(source, listOf(9L)))).thenReturn(bootstrap)
        assertEquals(bootstrap, controller.bootstrap(TrustReplyBootstrapHttpRequest(
            source = TrustReplySourceHttpRequest("TRAINING_MAIL", 123L),
            requestedFactIds = listOf(9L)
        )))

    }

    @Test
    fun `stream and cancel use canonical source scope`() {
        val id = "00000000-0000-0000-0000-000000000004"
        val emitter = SseEmitter()
        Mockito.`when`(
            coordinator.start(
                Mockito.eq("LIVE_INBOUND:77") ?: "LIVE_INBOUND:77",
                Mockito.eq(id) ?: id,
                Mockito.any(AiReplyTimeoutPolicy::class.java) ?: AiReplyTimeoutPolicy(10, 30),
                Mockito.any<AiReplyGenerationOperation>() ?: { _, _, _ -> "unused" }
            )
        )
            .thenReturn(emitter)
        val response = controller.generateStream(
            TrustReplyGenerationHttpRequest(
                source = TrustReplySourceHttpRequest("LIVE_INBOUND", 77L),
                expectedSourceVersion = "source-v1",
                generationId = id
            )
        )
        assertEquals(emitter, response.body)
        Mockito.`when`(coordinator.cancel("LIVE_INBOUND:77", id)).thenReturn("CANCEL_REQUESTED")
        assertEquals("CANCEL_REQUESTED", controller.cancel(
            id,
            TrustReplyCancelHttpRequest(TrustReplySourceHttpRequest("LIVE_INBOUND", 77L))
        ).status)
    }

    @Test
    fun `http contract rejects invalid source and generation id with stable codes`() {
        mockMvc.perform(
            post("/api/trust-reply/workbench/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"source":{"sourceType":"UNKNOWN","sourceId":1}}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("TRUST_REPLY_SOURCE_INVALID"))

        mockMvc.perform(
            post("/api/trust-reply/workbench/generations/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"source":{"sourceType":"TRAINING_MAIL","sourceId":1},"expectedSourceVersion":"v","generationId":"not-uuid"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("TRUST_REPLY_GENERATION_ID_INVALID"))
    }

    @Test
    fun `stream validation error remains structured when client accepts only event stream`() {
        mockMvc.perform(
            post("/api/trust-reply/workbench/generations/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("""{"source":{"sourceType":"TRAINING_MAIL","sourceId":1},"expectedSourceVersion":"v","generationId":"not-uuid"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value("TRUST_REPLY_GENERATION_ID_INVALID"))
    }

    @Test
    fun `synchronous generation endpoint is unavailable`() {
        mockMvc.perform(
            post("/api/trust-reply/workbench/generations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"source":{"sourceType":"TRAINING_MAIL","sourceId":1},"expectedSourceVersion":"v"}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `assemble returns raw rendered hash and canonical facts`() {
        val source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L)
        val response = TrustReplyAssembleResponse(
            source = source,
            sourceVersion = "s1",
            evidenceSetVersion = "e1",
            rawDraftText = "raw {{expert.name}}",
            renderedDraftText = "raw Test",
            draftHash = "hash",
            canonicalFactIds = listOf(9L),
            itemVersions = listOf(
                TrustReplyItemVersion(
                    versionId = "v1",
                    requestKey = "k".repeat(32),
                    handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
                    answerText = "answer",
                    claims = emptyList(),
                    model = "DEEPSEEK_V4_FLASH",
                    generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
                    evidenceSetVersion = "e1",
                    sourceVersion = "s1"
                )
            )
        )
        Mockito.`when`(
            service.assemble(
                Mockito.any(TrustReplyAssembleRequest::class.java) ?: TrustReplyAssembleRequest(
                    source = source,
                    expectedSourceVersion = "s1",
                    expectedEvidenceSetVersion = "e1",
                    lockedItems = emptyList()
                )
            )
        ).thenReturn(response)

        mockMvc.perform(
            post("/api/trust-reply/workbench/assemble")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"source":{"sourceType":"TRAINING_MAIL","sourceId":123},"expectedSourceVersion":"s1","expectedEvidenceSetVersion":"e1","lockedItems":[]}
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rawDraftText").value("raw {{expert.name}}"))
            .andExpect(jsonPath("$.renderedDraftText").value("raw Test"))
            .andExpect(jsonPath("$.draftHash").value("hash"))
            .andExpect(jsonPath("$.canonicalFactIds[0]").value(9))
    }

    @Test
    fun `assemble round trips operator instruction and new handling`() {
        val source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L)
        var captured: TrustReplyAssembleRequest? = null
        val response = TrustReplyAssembleResponse(
                source = source,
                sourceVersion = "s1",
                evidenceSetVersion = "e1",
                rawDraftText = "answer",
                renderedDraftText = "answer",
                draftHash = "hash",
                canonicalFactIds = emptyList(),
                itemVersions = emptyList()
        )
        Mockito.doAnswer { invocation ->
            captured = invocation.arguments[0] as TrustReplyAssembleRequest
            response
        }.`when`(service).assemble(Mockito.any(TrustReplyAssembleRequest::class.java) ?: TrustReplyAssembleRequest(
            source = source,
            expectedSourceVersion = "s1",
            expectedEvidenceSetVersion = "e1",
            lockedItems = emptyList()
        ))

        mockMvc.perform(
            post("/api/trust-reply/workbench/assemble")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "source":{"sourceType":"TRAINING_MAIL","sourceId":123},
                      "expectedSourceVersion":"s1",
                      "expectedEvidenceSetVersion":"e1",
                      "lockedItems":[{
                        "requestKey":"k",
                        "versionId":"v",
                        "handling":"ANSWER_FROM_OPERATOR_INPUT",
                        "answerText":"answer",
                        "claims":[],
                        "model":"DEEPSEEK_V4_FLASH",
                        "generationKind":"AI_GENERATED",
                        "evidenceSetVersion":"e1",
                        "sourceVersion":"s1",
                        "operatorInstructionHash":"hash",
                        "operatorInstruction":"Use this exact answer basis."
                      }]
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)

        val locked = requireNotNull(captured).lockedItems.single()
        assertEquals(TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT, locked.handling)
        assertEquals("Use this exact answer basis.", locked.operatorInstruction)
    }

    @Test
    fun `synchronous item adjustment endpoint is unavailable`() {
        mockMvc.perform(
            post("/api/trust-reply/workbench/items/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"source":{"sourceType":"TRAINING_MAIL","sourceId":123},"expectedSourceVersion":"s1","expectedEvidenceSetVersion":"e1","requestKey":"k","handling":"OMIT"}
                    """.trimIndent()
                )
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `invalid operation maps to stable 422 code`() {
        mockMvc.perform(
            post("/api/trust-reply/workbench/generations/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"source":{"sourceType":"TRAINING_MAIL","sourceId":123},"expectedSourceVersion":"s1","generationId":"00000000-0000-0000-0000-000000000004","operation":"UNKNOWN"}
                    """.trimIndent()
                )
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("TRUST_REPLY_OPERATION_INVALID"))
    }

    @Test
    fun `assemble maps stale source to stable 409 error DTO`() {
        Mockito.`when`(
            service.assemble(Mockito.any(TrustReplyAssembleRequest::class.java) ?: TrustReplyAssembleRequest(
                source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
                expectedSourceVersion = "stale",
                expectedEvidenceSetVersion = "e1",
                lockedItems = emptyList()
            ))
        ).thenThrow(TrustReplyWorkbenchException(
            org.springframework.http.HttpStatus.CONFLICT,
            "TRUST_REPLY_SOURCE_STALE"
        ))

        mockMvc.perform(
            post("/api/trust-reply/workbench/assemble")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"source":{"sourceType":"TRAINING_MAIL","sourceId":123},"expectedSourceVersion":"stale","expectedEvidenceSetVersion":"e1","lockedItems":[]}
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TRUST_REPLY_SOURCE_STALE"))
    }

    @Test
    fun `state PUT round trips locked subset and expected version`() {
        var captured: TrustReplySaveStateRequest? = null
        Mockito.doAnswer { invocation ->
            captured = invocation.arguments[0] as TrustReplySaveStateRequest
            TrustReplySavedState(
                status = "SAVED",
                stateVersion = 7,
                selectedModel = "DEEPSEEK_V4_FLASH",
                requestedFactIds = listOf(9L),
                lockedItems = captured!!.lockedItems
            )
        }.`when`(service).saveState(Mockito.any(TrustReplySaveStateRequest::class.java) ?: TrustReplySaveStateRequest(
            source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
            expectedStateVersion = 0,
            sourceVersion = "s1",
            evidenceSetVersion = "e1",
            lockedItems = emptyList()
        ))

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/trust-reply/workbench/state")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "source":{"sourceType":"TRAINING_MAIL","sourceId":123},
                      "expectedStateVersion":3,
                      "sourceVersion":"s1",
                      "evidenceSetVersion":"e1",
                      "requestedFactIds":[9],
                      "selectedModel":"DEEPSEEK_V4_FLASH",
                      "lockedItems":[{
                        "requestKey":"k",
                        "versionId":"v",
                        "handling":"ANSWER_WITH_EVIDENCE",
                        "answerText":"answer",
                        "claims":[],
                        "model":"DEEPSEEK_V4_FLASH",
                        "generationKind":"AI_GENERATED",
                        "evidenceSetVersion":"e1",
                        "sourceVersion":"s1"
                      }]
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("SAVED"))
            .andExpect(jsonPath("$.stateVersion").value(7))

        val saved = requireNotNull(captured)
        assertEquals(3L, saved.expectedStateVersion)
        assertEquals("k", saved.lockedItems.single().requestKey)
        assertEquals(TrustReplyItemHandling.ANSWER_WITH_EVIDENCE, saved.lockedItems.single().handling)
    }

    @Test
    fun `state PUT maps conflict and invalid locked item to stable codes`() {
        Mockito.`when`(service.saveState(Mockito.any(TrustReplySaveStateRequest::class.java) ?: TrustReplySaveStateRequest(
            source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
            expectedStateVersion = 0,
            sourceVersion = "s1",
            evidenceSetVersion = "e1",
            lockedItems = emptyList()
        ))).thenThrow(TrustReplyWorkbenchException(
            org.springframework.http.HttpStatus.CONFLICT,
            "TRUST_REPLY_STATE_CONFLICT"
        ))
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/trust-reply/workbench/state")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"source":{"sourceType":"TRAINING_MAIL","sourceId":123},"expectedStateVersion":1,"sourceVersion":"s1","evidenceSetVersion":"e1","lockedItems":[]}
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TRUST_REPLY_STATE_CONFLICT"))

        Mockito.doThrow(TrustReplyWorkbenchException(
            org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
            "TRUST_REPLY_REQUEST_KEY_INVALID"
        )).`when`(service).saveState(Mockito.any(TrustReplySaveStateRequest::class.java) ?: TrustReplySaveStateRequest(
            source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
            expectedStateVersion = 0,
            sourceVersion = "s1",
            evidenceSetVersion = "e1",
            lockedItems = emptyList()
        ))
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/trust-reply/workbench/state")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"source":{"sourceType":"TRAINING_MAIL","sourceId":123},"expectedStateVersion":0,"sourceVersion":"s1","evidenceSetVersion":"e1","lockedItems":[{"requestKey":"k","versionId":"v","handling":"ANSWER_WITH_EVIDENCE","answerText":"answer","claims":[],"model":"DEEPSEEK_V4_FLASH","generationKind":"AI_GENERATED","evidenceSetVersion":"e1","sourceVersion":"s1"}]}
                    """.trimIndent()
                )
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("TRUST_REPLY_REQUEST_KEY_INVALID"))
    }

    @Test
    fun `bootstrap round trips request fact selections`() {
        var captured: TrustReplyBootstrapRequest? = null
        val bootstrap = TrustReplyBootstrapResponse(
            source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
            sourceVersion = "source-v1",
            inboundSubject = "Subject",
            inboundText = "Question",
            expertName = "Test",
            expertEmail = "test@example.com",
            llmEnabled = false,
            availableModels = listOf("DEEPSEEK_V4_FLASH"),
            defaultModel = "DEEPSEEK_V4_FLASH",
            suggestedFactIds = listOf(9L),
            canonicalFactIds = listOf(9L),
            requestCoverage = emptyList(),
            draftReadiness = "READY",
            evidenceSetVersion = "evidence-v1",
            requestFactSelections = listOf(TrustReplyRequestFactSelection("k".repeat(32), listOf(9L)))
        )
        Mockito.doAnswer { invocation ->
            captured = invocation.arguments[0] as TrustReplyBootstrapRequest
            bootstrap
        }.`when`(service).bootstrap(Mockito.any(TrustReplyBootstrapRequest::class.java) ?: TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
            requestedFactIds = null
        ))

        mockMvc.perform(
            post("/api/trust-reply/workbench/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "source":{"sourceType":"TRAINING_MAIL","sourceId":123},
                      "requestFactSelections":[{"requestKey":"${"k".repeat(32)}","factRuleIds":[9]}]
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.requestFactSelections[0].requestKey").value("k".repeat(32)))
            .andExpect(jsonPath("$.requestFactSelections[0].factRuleIds[0]").value(9))

        val capturedRequest = requireNotNull(captured)
        assertEquals("k".repeat(32), capturedRequest.requestFactSelections?.single()?.requestKey)
        assertEquals(listOf(9L), capturedRequest.requestFactSelections?.single()?.factRuleIds)
    }

    @Test
    fun `assemble round trips request fact selections`() {
        var captured: TrustReplyAssembleRequest? = null
        val response = TrustReplyAssembleResponse(
            source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
            sourceVersion = "s1",
            evidenceSetVersion = "e1",
            rawDraftText = "answer",
            renderedDraftText = "answer",
            draftHash = "hash",
            canonicalFactIds = emptyList(),
            itemVersions = emptyList(),
            requestFactSelections = listOf(TrustReplyRequestFactSelection("k".repeat(32), listOf(9L)))
        )
        Mockito.doAnswer { invocation ->
            captured = invocation.arguments[0] as TrustReplyAssembleRequest
            response
        }.`when`(service).assemble(Mockito.any(TrustReplyAssembleRequest::class.java) ?: TrustReplyAssembleRequest(
            source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
            expectedSourceVersion = "s1",
            expectedEvidenceSetVersion = "e1",
            lockedItems = emptyList()
        ))

        mockMvc.perform(
            post("/api/trust-reply/workbench/assemble")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"source":{"sourceType":"TRAINING_MAIL","sourceId":123},"expectedSourceVersion":"s1","expectedEvidenceSetVersion":"e1","requestFactSelections":[{"requestKey":"${"k".repeat(32)}","factRuleIds":[9]}],"lockedItems":[]}
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.requestFactSelections[0].factRuleIds[0]").value(9))

        assertEquals("k".repeat(32), requireNotNull(captured).requestFactSelections?.single()?.requestKey)
        assertEquals(listOf(9L), requireNotNull(captured).requestFactSelections?.single()?.factRuleIds)
    }

    @Test
    fun `state PUT round trips request fact selections`() {
        var captured: TrustReplySaveStateRequest? = null
        Mockito.doAnswer { invocation ->
            captured = invocation.arguments[0] as TrustReplySaveStateRequest
            TrustReplySavedState(
                status = "SAVED",
                stateVersion = 7,
                selectedModel = "DEEPSEEK_V4_FLASH",
                requestedFactIds = listOf(9L),
                lockedItems = emptyList(),
                requestFactSelections = listOf(TrustReplyRequestFactSelection("k".repeat(32), listOf(9L)))
            )
        }.`when`(service).saveState(Mockito.any(TrustReplySaveStateRequest::class.java) ?: TrustReplySaveStateRequest(
            source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
            expectedStateVersion = 0,
            sourceVersion = "s1",
            evidenceSetVersion = "e1",
            lockedItems = emptyList()
        ))

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/trust-reply/workbench/state")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"source":{"sourceType":"TRAINING_MAIL","sourceId":123},"expectedStateVersion":3,"sourceVersion":"s1","evidenceSetVersion":"e1","requestFactSelections":[{"requestKey":"${"k".repeat(32)}","factRuleIds":[9]}],"lockedItems":[]}
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.requestFactSelections[0].requestKey").value("k".repeat(32)))

        assertEquals("k".repeat(32), requireNotNull(captured).requestFactSelections?.single()?.requestKey)
    }

    @Test
    fun `ambiguous and duplicate assignment map to stable 422 codes`() {
        Mockito.`when`(service.bootstrap(Mockito.any(TrustReplyBootstrapRequest::class.java) ?: TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
            requestedFactIds = null
        ))).thenThrow(TrustReplyWorkbenchException(
            org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
            "TRUST_REPLY_FACT_SELECTION_AMBIGUOUS"
        ))
        mockMvc.perform(
            post("/api/trust-reply/workbench/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"source":{"sourceType":"TRAINING_MAIL","sourceId":123},"requestedFactIds":[9],"requestFactSelections":[{"requestKey":"k","factRuleIds":[9]}]}
                    """.trimIndent()
                )
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("TRUST_REPLY_FACT_SELECTION_AMBIGUOUS"))

        Mockito.`when`(service.assemble(Mockito.any(TrustReplyAssembleRequest::class.java) ?: TrustReplyAssembleRequest(
            source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
            expectedSourceVersion = "s1",
            expectedEvidenceSetVersion = "e1",
            lockedItems = emptyList()
        ))).thenThrow(TrustReplyWorkbenchException(
            org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
            "TRUST_REPLY_FACT_ALREADY_ASSIGNED"
        ))
        mockMvc.perform(
            post("/api/trust-reply/workbench/assemble")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"source":{"sourceType":"TRAINING_MAIL","sourceId":123},"expectedSourceVersion":"s1","expectedEvidenceSetVersion":"e1","requestFactSelections":[{"requestKey":"k","factRuleIds":[9]},{"requestKey":"j","factRuleIds":[9]}],"lockedItems":[]}
                    """.trimIndent()
                )
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("TRUST_REPLY_FACT_ALREADY_ASSIGNED"))
    }

    @Test
    fun `bootstrap round trips frame snapshot`() {
        var captured: TrustReplyBootstrapRequest? = null
        val bootstrap = TrustReplyBootstrapResponse(
            source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
            sourceVersion = "source-v1",
            inboundSubject = "Subject",
            inboundText = "Question",
            expertName = "Test",
            expertEmail = "test@example.com",
            llmEnabled = false,
            availableModels = listOf("DEEPSEEK_V4_FLASH"),
            defaultModel = "DEEPSEEK_V4_FLASH",
            suggestedFactIds = listOf(9L),
            canonicalFactIds = listOf(9L),
            requestCoverage = emptyList(),
            draftReadiness = "READY",
            evidenceSetVersion = "evidence-v1",
            frameSnapshot = TrustReplyFrameSnapshot(
                selection = TrustReplyFrameSelection(salutationSnippetId = 1L, greetingSnippetId = 2L),
                version = "v1"
            )
        )
        Mockito.doAnswer { invocation ->
            captured = invocation.arguments[0] as TrustReplyBootstrapRequest
            bootstrap
        }.`when`(service).bootstrap(Mockito.any(TrustReplyBootstrapRequest::class.java) ?: TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
            requestedFactIds = null
        ))

        mockMvc.perform(
            post("/api/trust-reply/workbench/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"source":{"sourceType":"TRAINING_MAIL","sourceId":123},"frameSnapshot":{"selection":{"salutationSnippetId":1,"greetingSnippetId":2},"version":"v1"}}
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.frameSnapshot.version").value("v1"))
            .andExpect(jsonPath("$.frameSnapshot.selection.salutationSnippetId").value(1))

        val capturedRequest = requireNotNull(captured)
        assertEquals(1L, capturedRequest.frameSnapshot?.selection?.salutationSnippetId)
        assertEquals(2L, capturedRequest.frameSnapshot?.selection?.greetingSnippetId)
        assertEquals("v1", capturedRequest.frameSnapshot?.version)
    }

    @Test
    fun `assemble round trips frame snapshot`() {
        var captured: TrustReplyAssembleRequest? = null
        val response = TrustReplyAssembleResponse(
            source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
            sourceVersion = "s1",
            evidenceSetVersion = "e1",
            rawDraftText = "answer",
            renderedDraftText = "answer",
            draftHash = "hash",
            canonicalFactIds = emptyList(),
            itemVersions = emptyList(),
            frameSnapshot = TrustReplyFrameSnapshot(
                selection = TrustReplyFrameSelection(ackSnippetId = 5L),
                version = "v2"
            )
        )
        Mockito.doAnswer { invocation ->
            captured = invocation.arguments[0] as TrustReplyAssembleRequest
            response
        }.`when`(service).assemble(Mockito.any(TrustReplyAssembleRequest::class.java) ?: TrustReplyAssembleRequest(
            source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
            expectedSourceVersion = "s1",
            expectedEvidenceSetVersion = "e1",
            lockedItems = emptyList()
        ))

        mockMvc.perform(
            post("/api/trust-reply/workbench/assemble")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"source":{"sourceType":"TRAINING_MAIL","sourceId":123},"expectedSourceVersion":"s1","expectedEvidenceSetVersion":"e1","frameSnapshot":{"selection":{"ackSnippetId":5},"version":"v2"},"lockedItems":[]}
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.frameSnapshot.selection.ackSnippetId").value(5))

        assertEquals(5L, requireNotNull(captured).frameSnapshot?.selection?.ackSnippetId)
        assertEquals("v2", requireNotNull(captured).frameSnapshot?.version)
    }

    @Test
    fun `state PUT round trips frame snapshot`() {
        var captured: TrustReplySaveStateRequest? = null
        Mockito.doAnswer { invocation ->
            captured = invocation.arguments[0] as TrustReplySaveStateRequest
            TrustReplySavedState(
                status = "SAVED",
                stateVersion = 7,
                selectedModel = "DEEPSEEK_V4_FLASH",
                requestedFactIds = listOf(9L),
                lockedItems = emptyList(),
                frameSnapshot = captured!!.frameSnapshot
            )
        }.`when`(service).saveState(Mockito.any(TrustReplySaveStateRequest::class.java) ?: TrustReplySaveStateRequest(
            source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
            expectedStateVersion = 0,
            sourceVersion = "s1",
            evidenceSetVersion = "e1",
            lockedItems = emptyList()
        ))

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/trust-reply/workbench/state")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"source":{"sourceType":"TRAINING_MAIL","sourceId":123},"expectedStateVersion":3,"sourceVersion":"s1","evidenceSetVersion":"e1","frameSnapshot":{"selection":{"closingSnippetId":6},"version":"v3"},"lockedItems":[]}
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.frameSnapshot.selection.closingSnippetId").value(6))
            .andExpect(jsonPath("$.frameSnapshot.version").value("v3"))

        assertEquals(6L, requireNotNull(captured).frameSnapshot?.selection?.closingSnippetId)
    }

    @Test
    fun `frame errors map to stable codes`() {
        Mockito.`when`(service.assemble(Mockito.any(TrustReplyAssembleRequest::class.java) ?: TrustReplyAssembleRequest(
            source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
            expectedSourceVersion = "s1",
            expectedEvidenceSetVersion = "e1",
            lockedItems = emptyList()
        ))).thenThrow(TrustReplyWorkbenchException(
            org.springframework.http.HttpStatus.CONFLICT,
            "TRUST_REPLY_FRAME_STALE"
        ))
        mockMvc.perform(
            post("/api/trust-reply/workbench/assemble")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"source":{"sourceType":"TRAINING_MAIL","sourceId":123},"expectedSourceVersion":"s1","expectedEvidenceSetVersion":"e1","frameSnapshot":{"selection":{"salutationSnippetId":1},"version":"old"},"lockedItems":[]}
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TRUST_REPLY_FRAME_STALE"))

        Mockito.`when`(service.bootstrap(Mockito.any(TrustReplyBootstrapRequest::class.java) ?: TrustReplyBootstrapRequest(
            source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L),
            requestedFactIds = null
        ))).thenThrow(TrustReplyWorkbenchException(
            org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
            "TRUST_REPLY_FRAME_SELECTION_INVALID"
        ))
        mockMvc.perform(
            post("/api/trust-reply/workbench/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"source":{"sourceType":"TRAINING_MAIL","sourceId":123},"frameSnapshot":{"selection":{"salutationSnippetId":99},"version":"v"}}
                    """.trimIndent()
                )
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("TRUST_REPLY_FRAME_SELECTION_INVALID"))
    }

    @Test
    fun `bootstrap serializes the savedState object`() {
        val source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L)
        val bootstrap = TrustReplyBootstrapResponse(
            source = source,
            sourceVersion = "source-v1",
            inboundSubject = "Subject",
            inboundText = "Question",
            expertName = "Test",
            expertEmail = "test@example.com",
            llmEnabled = false,
            availableModels = listOf("DEEPSEEK_V4_FLASH"),
            defaultModel = "DEEPSEEK_V4_FLASH",
            suggestedFactIds = listOf(9L),
            canonicalFactIds = listOf(9L),
            requestCoverage = emptyList(),
            draftReadiness = "READY",
            evidenceSetVersion = "evidence-v1",
            savedState = TrustReplySavedState(
                status = "RESTORED",
                stateVersion = 2,
                selectedModel = "DEEPSEEK_V4_FLASH",
                requestedFactIds = listOf(9L),
                lockedItems = listOf(
                    TrustReplyLockedItemRequest(
                        requestKey = "k".repeat(32),
                        versionId = "v1",
                        handling = TrustReplyItemHandling.OMIT,
                        answerText = "",
                        claims = emptyList(),
                        model = "DEEPSEEK_V4_FLASH",
                        generationKind = TrustReplyItemGenerationKind.OMITTED,
                        evidenceSetVersion = "e1",
                        sourceVersion = "s1"
                    )
                )
            )
        )
        Mockito.`when`(service.bootstrap(TrustReplyBootstrapRequest(source, null))).thenReturn(bootstrap)

        mockMvc.perform(
            post("/api/trust-reply/workbench/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"source":{"sourceType":"TRAINING_MAIL","sourceId":123}}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.savedState.status").value("RESTORED"))
            .andExpect(jsonPath("$.savedState.stateVersion").value(2))
            .andExpect(jsonPath("$.savedState.lockedItems[0].requestKey").value("k".repeat(32)))
    }
}
