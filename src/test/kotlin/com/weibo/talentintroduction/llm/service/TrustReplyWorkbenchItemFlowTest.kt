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
        val unsupportedItem = item(1, "What?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
        // 计划 02 (I-3): 七种 handling 恒定开放——UNSUPPORTED 也包含
        // ANSWER_FROM_OPERATOR_INPUT。
        assertEquals(
            TrustReplyItemHandling.values().toSet(),
            TrustReplyWorkbenchService.allowedHandlings(unsupportedItem).toSet()
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
    fun `operator directed handling on grounded item still requires the instruction`() {
        // 计划 02 (I-3/I-4): 七种 handling 对 GROUNDED 恒定开放（不再有
        // TRUST_REPLY_HANDLING_INVALID 的状态门禁），但说明前置校验在生成/锁定
        // 阶段执行——空说明 → TRUST_REPLY_OPERATOR_INSTRUCTION_INVALID。
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

        assertEquals("TRUST_REPLY_OPERATOR_INSTRUCTION_INVALID", error.code)
    }

    @Test
    fun `assembled operator directed answer cannot bypass action policy`() {
        // I-2: 授权放开只作用于 G1（该动作是否被允许出现）。本用例的 answerText
        // 「Please send your CV.」缺目的与自愿表述——在新语义下仍被拒，但拒因从
        // G1（动作未授权，detectActions 无条件判废）变为 G2（CV_PURPOSE_MISSING /
        // CV_OPTIONALITY_MISSING，findViolations 在 OPERATOR_DIRECTED_ALLOWED_ACTIONS
        // 下仍判废）。断言（TRUST_REPLY_CLAIM_INVALID）逐字保留。
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
    fun `assembled operator directed answer keeps an operator authorised compliant request`() {
        val fixture = assembleFixture(
            status = RequestGroundingStatus.UNSUPPORTED,
            handling = operatorDirectedHandling(),
            answerText = "If you would like to proceed, you are welcome to share your CV at your convenience " +
                "so that we can carry out an initial eligibility review.",
            claims = emptyList()
        )

        val assembled = fixture.service.assemble(fixture.request)

        assertTrue(assembled.itemVersions.single().answerText.contains("CV"))
        assertTrue(assembled.rawDraftText.contains("eligibility review"))
    }

    @Test
    fun `operator authorized actions come only from operator directed items with a non-blank instruction`() {
        val service = assembleFixture(status = RequestGroundingStatus.UNSUPPORTED).service
        val meetingAnswer =
            "We would also be glad to arrange a Zoom call once that initial review is complete."

        fun locked(
            handling: TrustReplyItemHandling,
            answerText: String,
            operatorInstruction: String = "Use the operator-provided basis."
        ) = TrustReplyLockedItemRequest(
            requestKey = "k",
            versionId = "v",
            handling = handling,
            answerText = answerText,
            claims = emptyList(),
            model = "DEEPSEEK_V4_FLASH",
            generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
            evidenceSetVersion = "e",
            sourceVersion = "s",
            operatorInstruction = operatorInstruction
        )

        // (a) empty list -> empty set
        assertEquals(emptySet<AiReplyAction>(), service.operatorAuthorizedActions(emptyList()))
        // (b) grounded item whose answer contains a CV request -> empty set (handling mismatch)
        assertEquals(
            emptySet<AiReplyAction>(),
            service.operatorAuthorizedActions(
                listOf(locked(TrustReplyItemHandling.ANSWER_WITH_EVIDENCE, "Could you please send me your CV?"))
            )
        )
        // (c) operator-directed but blank instruction -> empty set
        assertEquals(
            emptySet<AiReplyAction>(),
            service.operatorAuthorizedActions(
                listOf(locked(TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT, meetingAnswer, operatorInstruction = ""))
            )
        )
        // (d) operator-directed with instruction; only the meeting action appears -> exactly PROPOSE_MEETING
        assertEquals(
            setOf(AiReplyAction.PROPOSE_MEETING),
            service.operatorAuthorizedActions(
                listOf(locked(TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT, meetingAnswer))
            )
        )
    }

    @Test
    fun `operator authorized actions ignore a missing or expired snapshot`() {
        val fixture = assembleFixture(status = RequestGroundingStatus.UNSUPPORTED)
        val service = fixture.service
        val store = fixture.stateStore
        val source = TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, 100L)

        // (a) no snapshot -> empty set; decode is never consulted
        Mockito.`when`(store.load("LIVE_INBOUND", 100L)).thenReturn(null)
        assertEquals(emptySet<AiReplyAction>(), service.operatorAuthorizedActions(source))
        Mockito.verify(store, Mockito.never()).decodePayload(Mockito.anyString() ?: "")

        // (b) expired snapshot -> empty set even though a payload exists
        Mockito.`when`(store.load("LIVE_INBOUND", 100L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(
                stateVersion = 1L,
                expiresAt = LocalDateTime.now().minusSeconds(1),
                payloadJson = "anything"
            )
        )
        assertEquals(emptySet<AiReplyAction>(), service.operatorAuthorizedActions(source))

        // (c) decode failure -> empty set
        Mockito.`when`(store.load("LIVE_INBOUND", 100L)).thenReturn(
            TrustReplyWorkbenchStateStore.TrustReplyStoredState(
                stateVersion = 1L,
                expiresAt = LocalDateTime.now().plusDays(1),
                payloadJson = "anything"
            )
        )
        Mockito.`when`(store.decodePayload("anything")).thenReturn(null)
        assertEquals(emptySet<AiReplyAction>(), service.operatorAuthorizedActions(source))

        // I-8: this read-only path never prunes
        Mockito.verify(store, Mockito.never()).pruneExpired(Mockito.any() ?: LocalDateTime.now())
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
    fun `handling matrix is fully open across every coverage`() {
        // 计划 02 (I-3): 七种 TrustReplyItemHandling 对 GROUNDED/PARTIAL/UNSUPPORTED
        // 每条 coverage 恒定开放（按 enum 声明顺序）；status 只决定
        // recommendedHandling 与提示，绝不决定可选集合。
        val allSeven = TrustReplyItemHandling.values().toList()
        assertEquals(7, allSeven.size)
        val groundedItem = item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)
        val partialItem = item(1, "What?", listOf(9L), RequestGroundingStatus.PARTIAL)
        val unsupportedItem = item(1, "What?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
        listOf(groundedItem, partialItem, unsupportedItem).forEach { entry ->
            assertEquals(allSeven, TrustReplyWorkbenchService.allowedHandlings(entry))
            TrustReplyItemHandling.values().forEach { handling ->
                // requireAllowedHandling 永不放行失败（全部开放）。
                TrustReplyWorkbenchService.requireAllowedHandling(entry, handling)
            }
        }
        assertEquals(
            TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT,
            TrustReplyWorkbenchService.recommendedHandling(unsupportedItem)
        )
        assertEquals(
            TrustReplyItemHandling.ANSWER_SUPPORTED_PART,
            TrustReplyWorkbenchService.recommendedHandling(partialItem)
        )
        assertEquals(
            TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
            TrustReplyWorkbenchService.recommendedHandling(groundedItem)
        )
    }

    @Test
    fun `fact required handlings fail with fact required at assemble when facts are empty`() {
        // 计划 02 (I-4): 空事实的四种事实模式在锁定/整合同样 422
        // TRUST_REPLY_FACT_REQUIRED——选项仍全部展示，机械校验在锁定阶段执行。
        listOf(
            TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
            TrustReplyItemHandling.ANSWER_SUPPORTED_PART,
            TrustReplyItemHandling.ANSWER_FACTS_VERBATIM,
            TrustReplyItemHandling.ANSWER_EVIDENCE_WITH_OPERATOR_INPUT
        ).forEach { handling ->
            val fixture = assembleFixture(
                status = RequestGroundingStatus.GROUNDED,
                handling = handling,
                answerText = "Salary info",
                claims = emptyList(),
                factIds = emptyList()
            )
            val error = assertThrows(TrustReplyWorkbenchException::class.java) {
                fixture.service.assemble(fixture.request)
            }
            assertEquals("TRUST_REPLY_FACT_REQUIRED", error.code)
        }
    }

    @Test
    fun `operator directed lock on unsupported item survives bound facts`() {
        // 计划 02 (I-2): 人工绑定不再改变 status——UNSUPPORTED 条目带人工事实锁定
        // ANSWER_FROM_OPERATOR_INPUT 后仍能通过 validateLockedItem 并成功 assemble
        // （旧用例的「status 因绑定上升到 PARTIAL」随 operatorBypassed 语义删除；
        // 新语义下 status 恒定自然值，锁定天然有效）。
        val fixture = assembleFixture(
            status = RequestGroundingStatus.UNSUPPORTED,
            handling = operatorDirectedHandling(),
            answerText = "We work with the named institutions.",
            claims = emptyList(),
            factIds = listOf(9L)
        )

        val response = fixture.service.assemble(fixture.request)

        assertEquals(fixture.validLockedItem.versionId, response.itemVersions.single().versionId)
        assertEquals("We work with the named institutions.", response.itemVersions.single().answerText)
        assertTrue(response.rawDraftText.contains("We work with the named institutions."))
    }

    @Test
    fun `bound fact adopted on partial item supports exactly one general answer claim`() {
        // 计划 02 (IP-3): 绑定事实后选「回答有依据部分」生成并锁定 → 恰好 1 条
        // general.answer claim，sourceRuleIds 等于 item.factRuleIds，
        // canonicalizeClaims 不抛异常（:1436-1442 的镜像分支可达）。
        val fixture = assembleFixture(
            status = RequestGroundingStatus.PARTIAL,
            handling = TrustReplyItemHandling.ANSWER_SUPPORTED_PART,
            answerText = "Salary info",
            claims = listOf(AiReplyItemClaim("general.answer", "Salary info", listOf(9L))),
        )

        val response = fixture.service.assemble(fixture.request)

        val claims = response.itemVersions.single().claims
        assertEquals(1, claims.size)
        assertEquals("general.answer", claims.single().intentKey)
        assertEquals(listOf(9L), claims.single().sourceRuleIds)
        assertEquals(listOf(9L), response.canonicalFactIds)
    }

    @Test
    fun `blended answer keeps an operator authorised compliant request`() {
        // 计划 02 (I-7 正面): 混合生成的答案含 CV 目的/自愿措辞时通过 validateLockedItem
        // 与整封 assemble（G1 放开：索要材料/约会议被运营说明授权）。
        val fixture = assembleFixture(
            status = RequestGroundingStatus.PARTIAL,
            handling = TrustReplyItemHandling.ANSWER_EVIDENCE_WITH_OPERATOR_INPUT,
            answerText = "If you would like to proceed, you are welcome to share your CV at your convenience " +
                "so that we can carry out an initial eligibility review.",
            claims = emptyList(),
        )

        val response = fixture.service.assemble(fixture.request)

        assertEquals(fixture.validLockedItem.versionId, response.itemVersions.single().versionId)
        assertTrue(response.itemVersions.single().claims.isEmpty())
        assertTrue(response.rawDraftText.contains("eligibility review"))
    }

    @Test
    fun `blended answer cannot bypass the sensitive material gate`() {
        // 计划 02 (I-7 负面): G2 不放开——混合答案含护照索取仍判 TRUST_REPLY_CLAIM_INVALID。
        val fixture = assembleFixture(
            status = RequestGroundingStatus.PARTIAL,
            handling = TrustReplyItemHandling.ANSWER_EVIDENCE_WITH_OPERATOR_INPUT,
            answerText = "Please send your passport.",
            claims = emptyList(),
        )

        val error = assertThrows(TrustReplyWorkbenchException::class.java) {
            fixture.service.assemble(fixture.request)
        }

        assertEquals("TRUST_REPLY_CLAIM_INVALID", error.code)
    }

    @Test
    fun `blended item never joins grounded sections`() {
        // 计划 02 (I-8): assemble 的 groundedSections 条件只认 ANSWER_WITH_EVIDENCE /
        // ANSWER_SUPPORTED_PART——混合条目不进 groundedSections，
        // validateGroundedTrustBoundary 因此不会因它触发（空 groundedSections 早退）。
        val fixture = assembleFixture(
            status = RequestGroundingStatus.PARTIAL,
            handling = TrustReplyItemHandling.ANSWER_EVIDENCE_WITH_OPERATOR_INPUT,
            answerText = "Salary info",
            claims = emptyList(),
        )

        val response = fixture.service.assemble(fixture.request)

        assertEquals("Salary info", response.itemVersions.single().answerText)
        assertTrue(response.itemVersions.single().claims.isEmpty())
        assertEquals("raw Salary info", response.rawDraftText)
    }

    // ---- 计划 03: 按事实原文回答（T4.1）----

    @Test
    fun `verbatim generation keeps fact order and assembles without version drift`() {
        // 计划 03 (T4.1/IP-2/I-2): 走完「生成（generateItem 的 verbatim 分支）→
        // 锁定（adjustItem）→ assemble」全流程。正文恰好 2 段、顺序等于
        // factRuleIds；materializeVersion 只在 :1190（生成）与 :1256（assemble）
        // 两处各算一次，versionId 必须逐字一致——出现
        // TRUST_REPLY_ITEM_VERSION_INVALID 即失败。
        val verbatimText = "Project overview body.\n\nSalary and funding support body."
        val fixture = assembleFixture(
            status = RequestGroundingStatus.GROUNDED,
            handling = TrustReplyItemHandling.ANSWER_FACTS_VERBATIM,
            answerText = verbatimText,
            claims = emptyList(),
            generationKind = TrustReplyItemGenerationKind.SAFE_TEMPLATE,
            factIds = listOf(2L, 1L),
            factBodies = mapOf(
                2L to "Project overview body.",
                1L to "Salary and funding support body."
            )
        )
        Mockito.`when`(fixture.draftService.composeVerbatimFactAnswer(
            Mockito.any(RequestFactItem::class.java) ?: RequestFactItem(
                index = 1,
                requestText = "What?",
                factRuleIds = listOf(2L, 1L),
                status = RequestGroundingStatus.GROUNDED
            )
        ))
            .thenReturn(verbatimText)
        Mockito.`when`(
            fixture.draftService.generateItem(
                inboundText = Mockito.anyString(),
                requestFact = Mockito.any(RequestFactItem::class.java) ?: RequestFactItem(
                    index = 1,
                    requestText = "What?",
                    factRuleIds = listOf(2L, 1L),
                    status = RequestGroundingStatus.GROUNDED
                ),
                handling = Mockito.eq(TrustReplyItemHandling.ANSWER_FACTS_VERBATIM) ?: TrustReplyItemHandling.ANSWER_FACTS_VERBATIM,
                requestKey = Mockito.anyString(),
                operatorInstruction = Mockito.anyString(),
                expertProfile = Mockito.anyString(),
                mailHistory = Mockito.anyString(),
                contextWarnings = Mockito.anyList<String>() ?: emptyList(),
                replyModel = Mockito.isNull(),
                researchProfileSufficient = Mockito.anyBoolean(),
                llmAttemptTimeoutSeconds = Mockito.isNull(),
                llmTotalTimeoutSeconds = Mockito.isNull(),
                cancellationToken = Mockito.isNull(),
                progressReporter = Mockito.any(AiReplyProgressReporter::class.java) ?: AiReplyProgressReporter.NOOP
            )
        ).thenReturn(
            AiReplyItemGenerationResult(
                itemAnswer = AiReplyItemAnswer(
                    1,
                    "What?",
                    RequestGroundingStatus.GROUNDED,
                    verbatimText,
                    emptyList()
                ),
                handling = TrustReplyItemHandling.ANSWER_FACTS_VERBATIM,
                generationKind = TrustReplyItemGenerationKind.SAFE_TEMPLATE,
                generationState = AiReplyGenerationState.LLM_USED,
                usedLlm = false,
                lockable = true
            )
        )

        val adjusted = fixture.service.adjustItem(
            TrustReplyItemAdjustmentRequest(
                source = fixture.request.source,
                expectedSourceVersion = fixture.request.expectedSourceVersion,
                expectedEvidenceSetVersion = fixture.request.expectedEvidenceSetVersion,
                requestKey = fixture.validLockedItem.requestKey,
                handling = TrustReplyItemHandling.ANSWER_FACTS_VERBATIM,
                operatorInstruction = ""
            )
        )

        assertEquals(verbatimText, adjusted.version.answerText)
        assertEquals(2, adjusted.version.answerText.split("\n\n").size)
        assertTrue(adjusted.version.claims.isEmpty())
        assertEquals(TrustReplyItemGenerationKind.SAFE_TEMPLATE, adjusted.version.generationKind)

        val locked = TrustReplyLockedItemRequest(
            requestKey = adjusted.version.requestKey,
            versionId = adjusted.version.versionId,
            handling = adjusted.version.handling,
            answerText = adjusted.version.answerText,
            claims = adjusted.version.claims,
            model = adjusted.version.model,
            generationKind = adjusted.version.generationKind,
            evidenceSetVersion = adjusted.version.evidenceSetVersion,
            sourceVersion = adjusted.version.sourceVersion,
            operatorInstructionHash = adjusted.version.operatorInstructionHash,
            operatorInstruction = adjusted.version.operatorInstruction
        )
        val assembled = fixture.service.assemble(
            TrustReplyAssembleRequest(
                source = fixture.request.source,
                expectedSourceVersion = fixture.request.expectedSourceVersion,
                expectedEvidenceSetVersion = fixture.request.expectedEvidenceSetVersion,
                lockedItems = listOf(locked)
            )
        )

        val version = assembled.itemVersions.single()
        assertEquals(adjusted.version.versionId, version.versionId)
        assertEquals(verbatimText, version.answerText)
        assertTrue(version.claims.isEmpty())
        // I-2: 段数 = 事实数，顺序逐字等于 factRuleIds。
        val segments = version.answerText.split("\n\n")
        assertEquals(2, segments.size)
        assertEquals("Project overview body.", segments[0])
        assertEquals("Salary and funding support body.", segments[1])
        assertTrue(assembled.rawDraftText.contains("Project overview body."))
    }

    @Test
    fun `verbatim locked item with tampered answerText is rejected`() {
        // 计划 03 (T4.1/I-4): 客户端篡改 answerText——锁定校验用 factRuleIds
        // 从库重算比对，不等即 422 TRUST_REPLY_LOCKED_ITEM_INVALID，绝不外发。
        val fixture = assembleFixture(
            status = RequestGroundingStatus.GROUNDED,
            handling = TrustReplyItemHandling.ANSWER_FACTS_VERBATIM,
            answerText = "Tampered body.",
            claims = emptyList(),
            generationKind = TrustReplyItemGenerationKind.SAFE_TEMPLATE
        )
        Mockito.`when`(fixture.draftService.composeVerbatimFactAnswer(
            Mockito.any(RequestFactItem::class.java) ?: RequestFactItem(
                index = 1,
                requestText = "What?",
                factRuleIds = listOf(9L),
                status = RequestGroundingStatus.GROUNDED
            )
        ))
            .thenReturn("Real fact body.")

        val error = assertThrows(TrustReplyWorkbenchException::class.java) {
            fixture.service.assemble(fixture.request)
        }

        assertEquals("TRUST_REPLY_LOCKED_ITEM_INVALID", error.code)
    }

    @Test
    fun `verbatim locked item with claims is rejected`() {
        // 计划 03 (T4.1/I-3/I-4): verbatim claims 恒空——非空 claims 直接 422
        // TRUST_REPLY_LOCKED_ITEM_INVALID（不落入 canonicalizeClaims 的
        // TRUST_REPLY_CLAIMS_INVALID / ANSWER_CLAIMS_MISMATCH）。
        val fixture = assembleFixture(
            status = RequestGroundingStatus.GROUNDED,
            handling = TrustReplyItemHandling.ANSWER_FACTS_VERBATIM,
            answerText = "Salary info",
            claims = emptyList(),
            generationKind = TrustReplyItemGenerationKind.SAFE_TEMPLATE
        )
        Mockito.`when`(fixture.draftService.composeVerbatimFactAnswer(
            Mockito.any(RequestFactItem::class.java) ?: RequestFactItem(
                index = 1,
                requestText = "What?",
                factRuleIds = listOf(9L),
                status = RequestGroundingStatus.GROUNDED
            )
        ))
            .thenReturn("Salary info")
        val tampered = fixture.validLockedItem.copy(
            claims = listOf(AiReplyItemClaim("general.answer", "Salary info", listOf(9L)))
        )
        val request = TrustReplyAssembleRequest(
            source = fixture.request.source,
            expectedSourceVersion = fixture.request.expectedSourceVersion,
            expectedEvidenceSetVersion = fixture.request.expectedEvidenceSetVersion,
            lockedItems = listOf(tampered)
        )

        val error = assertThrows(TrustReplyWorkbenchException::class.java) {
            fixture.service.assemble(request)
        }

        assertEquals("TRUST_REPLY_LOCKED_ITEM_INVALID", error.code)
    }

    @Test
    fun `two verbatim summaries with identical fact text assemble successfully`() {
        // 计划 02 (I-6/I-7): 跨 item 查重已删除——两条摘要的 verbatim 正文逐字
        // 相同是合法的（同一事实/重复文案可绑定多个 request）；canonical audit
        // 来自 selection.sendQaRuleIds，不因重复正文丢失。
        val fixture = duplicateFixture(
            item1Text = "Salary info",
            item2Text = "Salary info",
            handling = TrustReplyItemHandling.ANSWER_FACTS_VERBATIM,
            generationKind = TrustReplyItemGenerationKind.SAFE_TEMPLATE
        )

        val response = fixture.service.assemble(fixture.request)

        assertEquals(2, response.itemVersions.size)
        assertEquals(listOf(9L, 10L), response.canonicalFactIds)
    }

    @Test
    fun `blended adjustment with empty instruction is a 422 not a 500`() {
        // 计划 02 (I-9): adjustItem 传空 operatorInstruction + 新 handling →
        // 422 TRUST_REPLY_OPERATOR_INSTRUCTION_INVALID（不是服务层 require 的 500）。
        val fixture = assembleFixture(
            status = RequestGroundingStatus.PARTIAL,
        )

        val error = assertThrows(TrustReplyWorkbenchException::class.java) {
            fixture.service.adjustItem(
                TrustReplyItemAdjustmentRequest(
                    source = fixture.request.source,
                    expectedSourceVersion = fixture.request.expectedSourceVersion,
                    expectedEvidenceSetVersion = fixture.request.expectedEvidenceSetVersion,
                    requestKey = fixture.validLockedItem.requestKey,
                    handling = TrustReplyItemHandling.ANSWER_EVIDENCE_WITH_OPERATOR_INPUT,
                    operatorInstruction = null
                )
            )
        }

        assertEquals("TRUST_REPLY_OPERATOR_INSTRUCTION_INVALID", error.code)
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

    // 03a (I-1): the per-request evidence version binds exactly the requestKey,
    // the ordered factRuleIds and the subset-only rule snapshot. No observation
    // time and no other request's ids participate; repeated input is
    // deterministic (K-ai-reply-evidence-version-deterministic).
    @Test
    fun `per request evidence version binds key and fact order and is deterministic`() {
        val baseSnapshotOf = { ids: List<Long> -> "base-${ids.joinToString(".")}" }
        val first = TrustReplyWorkbenchService.requestEvidenceVersion("key-a", listOf(9L, 10L), baseSnapshotOf)

        // (c) determinism: identical input twice produces the identical version.
        assertEquals(
            first,
            TrustReplyWorkbenchService.requestEvidenceVersion("key-a", listOf(9L, 10L), baseSnapshotOf)
        )
        // (a) the same factRuleIds bound to a different requestKey differs.
        assertNotEquals(
            first,
            TrustReplyWorkbenchService.requestEvidenceVersion("key-b", listOf(9L, 10L), baseSnapshotOf)
        )
        // (b) swapping the fact order differs (must-NOT-change 5).
        assertNotEquals(
            first,
            TrustReplyWorkbenchService.requestEvidenceVersion("key-a", listOf(10L, 9L), baseSnapshotOf)
        )
        // subset identity: the subset snapshot of the ids participates, so
        // changing the snapshot changes the version.
        assertNotEquals(
            first,
            TrustReplyWorkbenchService.requestEvidenceVersion("key-a", listOf(9L, 10L), baseSnapshotOf = { "base-9.10.changed" })
        )
    }

    // 03a (I-3): the aggregate fingerprint is the sha256 of the index-ordered
    // per-request values concatenated; ordering is by canonical index.
    @Test
    fun `aggregate evidence version orders by index and is deterministic`() {
        val first = TrustReplyWorkbenchService.aggregateEvidenceVersion(listOf(1 to "aaa", 2 to "bbb"))
        assertEquals(64, first.length)
        assertEquals(
            first,
            TrustReplyWorkbenchService.aggregateEvidenceVersion(listOf(2 to "bbb", 1 to "aaa"))
        )
        assertNotEquals(
            first,
            TrustReplyWorkbenchService.aggregateEvidenceVersion(listOf(1 to "bbb", 2 to "aaa"))
        )
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
        // 03a (I-1): item generation is gated on the per-request evidence
        // version: sha256(key ids-comma base, joined with a space).
        val currentEvidence = AiReplyDraftService.sha256Hex(listOf(key, "9", "e1").joinToString(" "))
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

    // T5-9: a machine-composed instruction (I-0 shape) must pass the existing
    // ANSWER_FROM_OPERATOR_INPUT validation end to end: adjustItem generates
    // with it, assemble locks the version with the same hash. No new backend
    // branch or relaxed validation is involved.
    @Test
    fun `machine composed instruction passes operator directed validation and assembles`() {
        val handling = operatorDirectedHandling()
        val machineInstruction = "这一条我们库里没有确认口径。请按真人对接人的方式回答：先明说没有确认答案，最后交出下一步但不承诺具体时间。不要出现数字、链接或时间承诺。"
        val fixture = assembleFixture(
            status = RequestGroundingStatus.UNSUPPORTED,
            handling = handling,
            answerText = "We have no confirmed answer.",
            claims = emptyList()
        )
        Mockito.`when`(
            fixture.draftService.generateItem(
                inboundText = Mockito.anyString(),
                requestFact = Mockito.any(RequestFactItem::class.java) ?: RequestFactItem(
                    index = 1,
                    requestText = "What?",
                    factRuleIds = emptyList(),
                    status = RequestGroundingStatus.UNSUPPORTED
                ),
                handling = Mockito.eq(handling) ?: handling,
                requestKey = Mockito.anyString(),
                operatorInstruction = Mockito.anyString(),
                expertProfile = Mockito.anyString(),
                mailHistory = Mockito.anyString(),
                contextWarnings = Mockito.anyList<String>() ?: emptyList(),
                replyModel = Mockito.isNull(),
                researchProfileSufficient = Mockito.anyBoolean(),
                llmAttemptTimeoutSeconds = Mockito.isNull(),
                llmTotalTimeoutSeconds = Mockito.isNull(),
                cancellationToken = Mockito.isNull(),
                progressReporter = Mockito.any(AiReplyProgressReporter::class.java) ?: AiReplyProgressReporter.NOOP
            )
        ).thenReturn(
            AiReplyItemGenerationResult(
                itemAnswer = AiReplyItemAnswer(
                    1,
                    "What?",
                    RequestGroundingStatus.UNSUPPORTED,
                    "We have no confirmed answer.",
                    emptyList()
                ),
                handling = handling,
                generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
                generationState = AiReplyGenerationState.LLM_USED,
                usedLlm = true,
                lockable = true
            )
        )

        val adjusted = fixture.service.adjustItem(
            TrustReplyItemAdjustmentRequest(
                source = fixture.request.source,
                expectedSourceVersion = fixture.request.expectedSourceVersion,
                expectedEvidenceSetVersion = fixture.request.expectedEvidenceSetVersion,
                requestKey = fixture.validLockedItem.requestKey,
                handling = handling,
                operatorInstruction = machineInstruction
            )
        )
        assertEquals(machineInstruction, adjusted.version.operatorInstruction)
        assertEquals(handling, adjusted.version.handling)
        assertTrue(adjusted.version.claims.isEmpty())
        assertEquals(AiReplyDraftService.sha256Hex(machineInstruction), adjusted.version.operatorInstructionHash)

        val locked = TrustReplyLockedItemRequest(
            requestKey = adjusted.version.requestKey,
            versionId = adjusted.version.versionId,
            handling = adjusted.version.handling,
            answerText = adjusted.version.answerText,
            claims = adjusted.version.claims,
            model = adjusted.version.model,
            generationKind = adjusted.version.generationKind,
            evidenceSetVersion = adjusted.version.evidenceSetVersion,
            sourceVersion = adjusted.version.sourceVersion,
            operatorInstructionHash = adjusted.version.operatorInstructionHash,
            operatorInstruction = adjusted.version.operatorInstruction
        )
        val assembled = fixture.service.assemble(fixture.request.copy(lockedItems = listOf(locked)))
        assertEquals(adjusted.version.versionId, assembled.itemVersions.single().versionId)
        assertEquals(machineInstruction, assembled.itemVersions.single().operatorInstruction)
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
    fun `bound facts never enter the send audit rule ids`() {
        // P2b (C-4 / must-NOT-change 1 / IP-3): 绑定但非证据的事实只进 prompt 通道，
        // 整封汇总（assemble / generate）暴露的外发审计 id 一律不含它。
        val boundId = 99L
        val fixture = assembleFixture(boundFactIds = listOf(boundId))

        // P2b (I-1): prompt 通道携带绑定事实，send 通道只有证据。
        assertTrue(fixture.selection.promptRuleIds.contains(boundId))
        assertEquals(listOf(9L), fixture.selection.sendQaRuleIds)
        assertFalse(fixture.selection.sendQaRuleIds.contains(boundId))

        // 整封汇总：requestedFactIds（= selection.sendQaRuleIds）与 canonicalFactIds
        // （= claims 的证据 sourceRuleIds）都不含绑定但非证据的 id。
        val response = fixture.service.assemble(fixture.request)
        assertEquals(listOf(9L), response.requestedFactIds)
        assertEquals(listOf(9L), response.canonicalFactIds)
        assertFalse(response.requestedFactIds.contains(boundId))
        assertFalse(response.canonicalFactIds.contains(boundId))

        // 整封生成：外发审计快照的 qaRuleIds 只含证据（mail_record_qa_rule 只记真证据）。
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
        ).thenReturn(
            AiReplyDraftResult(
                draftText = "Salary info",
                usedLlm = true,
                qaRuleIds = listOf(9L),
                mode = AiReplyMode.QA_GROUNDED,
                requestFacts = listOf(item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)),
                generationState = AiReplyGenerationState.LLM_USED,
                draftReadiness = AiReplyDraftReadiness.READY,
                evidenceSetVersion = "e1",
                itemAnswers = emptyList()
            )
        )
        Mockito.`when`(fixture.previewService.preview("Salary info", fixture.contact, null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("rendered Salary info", emptyList()))

        val generated = fixture.service.generate(
            TrustReplyGenerationRequest(
                source = fixture.request.source,
                expectedSourceVersion = fixture.request.expectedSourceVersion
            )
        )
        assertEquals(listOf(9L), generated.qaRuleIds)
        assertFalse(generated.qaRuleIds.contains(boundId))
    }

    @Test
    fun `locked items survive the bound-vs-evidence split`() {
        // P2a (I-5): 自动匹配路径下 boundRuleIds == factRuleIds（I-1），切换后
        // requestEvidenceVersion 的输入逐字不变 → 既有锁定项 versionId 原样通过。
        val fixture = assembleFixture()
        val boot = fixture.service.bootstrap(TrustReplyBootstrapRequest(fixture.request.source))
        val currentEvidence = boot.requestCoverage.single().evidenceSetVersion
        assertEquals(fixture.validLockedItem.evidenceSetVersion, currentEvidence)

        val response = fixture.service.assemble(fixture.request)
        assertEquals(fixture.validLockedItem.versionId, response.itemVersions.single().versionId)
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
    fun `assemble rejects stale source incomplete duplicate unknown and tampered locks`() {
        val fixture = assembleFixture()
        val base = fixture.request
        fun code(request: TrustReplyAssembleRequest): String = assertThrows(TrustReplyWorkbenchException::class.java) {
            fixture.service.assemble(request)
        }.code

        assertEquals("TRUST_REPLY_SOURCE_STALE", code(base.copy(expectedSourceVersion = "stale")))
        // 03a (I-3): the whole-draft expectedEvidenceSetVersion pre-check is
        // gone; a stale expected value is ignored and per-item evidence rules.
        assertNotNull(fixture.service.assemble(base.copy(expectedEvidenceSetVersion = "stale")))
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

    // ── 计划 02 (I-6): cross-request reuse is a legal operator decision ─────────

    @Test
    fun `assemble accepts the same source rule bound to two requests`() {
        // 计划 02 (I-6): 同一事实可合法绑定多个 request——不再抛
        // TRUST_REPLY_FACT_ALREADY_ASSIGNED；canonical ids 为有序 union 去重。
        // 12-letter-closer (I-2): 两条 claim 的 sourceRuleIds 集合相等（都是 {9}）
        // 视为同一事实，整封正文只保留首次出现的措辞 "Claim A"。
        val fixture = duplicateFixture(item2Source = 9L, item2Text = "Claim B")
        Mockito.`when`(fixture.composer.composeLockedItems(listOf("Claim A"), fixture.defaultFrame))
            .thenReturn("raw Claim A")
        Mockito.`when`(fixture.previewService.preview("raw Claim A", fixture.contact, null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("rendered", emptyList()))

        val response = fixture.service.assemble(fixture.request)

        assertEquals(2, response.itemVersions.size)
        assertEquals(listOf(9L), response.canonicalFactIds)
        assertEquals("raw Claim A", response.rawDraftText)
    }

    @Test
    fun `assemble accepts identical normalized answers across requests`() {
        // 计划 02 (I-6): 跨 item 重复正文不再 422 TRUST_REPLY_DUPLICATE_CLAIM。
        // 12-letter-closer (I-2/I-3): 两条 claim 绑定不同事实集（{9} 与 {10}），
        // 不参与去重；同主题 "general" 归并为一段，段内正文空白原样保留。
        val fixture = duplicateFixture(item1Text = "Same  claim", item2Text = "Same CLAIM")
        val closed = "Same  claim Same CLAIM"
        Mockito.`when`(fixture.composer.composeLockedItems(listOf(closed), fixture.defaultFrame))
            .thenReturn("raw $closed")
        Mockito.`when`(fixture.previewService.preview("raw $closed", fixture.contact, null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("rendered", emptyList()))

        val response = fixture.service.assemble(fixture.request)

        assertEquals(2, response.itemVersions.size)
        assertEquals("raw $closed", response.rawDraftText)
    }

    @Test
    fun `assemble keeps similar answers from different claims in canonical order`() {
        // 12-letter-closer (I-2/I-3): 不同事实集 {9} 与 {10} 不去重；同主题
        // "general" 归并为一段，段内保持 canonical order（Claim A 先于 Claim B）。
        val fixture = duplicateFixture()
        val closed = "Claim A Claim B"
        Mockito.`when`(fixture.composer.composeLockedItems(listOf(closed), fixture.defaultFrame))
            .thenReturn("raw $closed")
        Mockito.`when`(fixture.previewService.preview("raw $closed", fixture.contact, null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("rendered", emptyList()))

        val response = fixture.service.assemble(fixture.request)

        assertEquals("raw $closed", response.rawDraftText)
        assertEquals(listOf(9L, 10L), response.canonicalFactIds)
        assertEquals(listOf("Claim A", "Claim B"), response.itemVersions.map { it.answerText })
    }


    // ── 02 claim paragraphs (I-1/I-2/I-3/I-4) ────────────────────────────

    @Test
    fun `multi claim item answerText keeps each claim as its own paragraph`() {
        // 12-letter-closer: 逐条 itemVersions 保持未收口原文（IP-4），两条 claim 在
        // 版本身份里各自分段；整封正文按 sourceRuleIds 收口——两条 claim 同绑事实集
        // {9}（I-2 视为同一事实），只保留首次出现的 "Claim A"。
        val paragraphAnswer = "Claim A" + "\n\n" + "Claim B"
        val fixture = assembleFixture(
            answerText = paragraphAnswer,
            claims = listOf(
                AiReplyItemClaim("policy.eligibility", "Claim A", listOf(9L)),
                AiReplyItemClaim("compensation.details", "Claim B", listOf(9L))
            ),
            intents = listOf(
                RequestIntentCoverage(
                    intentKey = "policy.eligibility",
                    title = "Policy eligibility",
                    requiredCoverageKeys = emptyList(),
                    evidenceRuleIds = listOf(9L),
                    status = "SUPPORTED",
                    missingEvidenceKeys = emptyList(),
                    requiresResearchContext = false
                ),
                RequestIntentCoverage(
                    intentKey = "compensation.details",
                    title = "Compensation details",
                    requiredCoverageKeys = emptyList(),
                    evidenceRuleIds = listOf(9L),
                    status = "SUPPORTED",
                    missingEvidenceKeys = emptyList(),
                    requiresResearchContext = false
                )
            )
        )
        Mockito.`when`(fixture.composer.composeLockedItems(listOf("Claim A"), fixture.defaultFrame))
            .thenReturn("raw Claim A")
        Mockito.`when`(fixture.previewService.preview("raw Claim A", fixture.contact, null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("rendered", emptyList()))

        val response = fixture.service.assemble(fixture.request)

        assertEquals("Claim A\n\nClaim B", response.itemVersions.single().answerText)
        assertEquals(listOf("Claim A", "Claim B"), response.itemVersions.single().claims.map { it.text })
        assertEquals("raw Claim A", response.rawDraftText)
    }

    @Test
    fun `multi claim item with legacy single space answerText is rejected`() {
        val fixture = assembleFixture(
            answerText = "Claim A Claim B",
            claims = listOf(
                AiReplyItemClaim("policy.eligibility", "Claim A", listOf(9L)),
                AiReplyItemClaim("compensation.details", "Claim B", listOf(9L))
            ),
            intents = listOf(
                RequestIntentCoverage(
                    intentKey = "policy.eligibility",
                    title = "Policy eligibility",
                    requiredCoverageKeys = emptyList(),
                    evidenceRuleIds = listOf(9L),
                    status = "SUPPORTED",
                    missingEvidenceKeys = emptyList(),
                    requiresResearchContext = false
                ),
                RequestIntentCoverage(
                    intentKey = "compensation.details",
                    title = "Compensation details",
                    requiredCoverageKeys = emptyList(),
                    evidenceRuleIds = listOf(9L),
                    status = "SUPPORTED",
                    missingEvidenceKeys = emptyList(),
                    requiresResearchContext = false
                )
            )
        )

        val error = assertThrows(TrustReplyWorkbenchException::class.java) {
            fixture.service.assemble(fixture.request)
        }

        assertEquals("TRUST_REPLY_ANSWER_CLAIMS_MISMATCH", error.code)
    }

    @Test
    fun `claims empty handlings stay single line without paragraph separators`() {
        val canonical = AiReplyHighRiskClaimValidator.safeAcknowledgementFor("What?")
        val ackFixture = assembleFixture(
            status = RequestGroundingStatus.UNSUPPORTED,
            handling = TrustReplyItemHandling.ACKNOWLEDGE_PENDING,
            answerText = canonical,
            claims = emptyList(),
            generationKind = TrustReplyItemGenerationKind.SAFE_TEMPLATE
        )
        val ackPadded = "  $canonical  "
        val ackResponse = ackFixture.service.assemble(
            ackFixture.request.copy(lockedItems = listOf(ackFixture.validLockedItem.copy(answerText = ackPadded)))
        )
        assertEquals(ackPadded.trim(), ackResponse.itemVersions.single().answerText)
        assertFalse(ackResponse.itemVersions.single().answerText.contains("\n\n"))

        val operatorHandling = operatorDirectedHandling()
        val operatorFixture = assembleFixture(
            status = RequestGroundingStatus.UNSUPPORTED,
            handling = operatorHandling,
            answerText = "Operator provided basis.",
            claims = emptyList()
        )
        val operatorPadded = "  Operator provided basis.  "
        val operatorResponse = operatorFixture.service.assemble(
            operatorFixture.request.copy(
                lockedItems = listOf(operatorFixture.validLockedItem.copy(answerText = operatorPadded))
            )
        )
        assertEquals(operatorPadded.trim(), operatorResponse.itemVersions.single().answerText)
        assertFalse(operatorResponse.itemVersions.single().answerText.contains("\n\n"))
    }

    @Test
    fun `similar answers across items assemble despite paragraph and space variance`() {
        // 计划 02 (I-6): 跨 item 归一化正文查重已删除——段落/空格差异不再触发
        // TRUST_REPLY_DUPLICATE_CLAIM。12-letter-closer (I-2/I-3): 事实集 {9} 与
        // {10} 不同故不去重；同主题归并为一段，段内正文空白原样保留。
        val fixture = duplicateFixture(item1Text = "Same\n\nclaim", item2Text = "Same  claim")
        val closed = "Same\n\nclaim Same  claim"
        Mockito.`when`(fixture.composer.composeLockedItems(listOf(closed), fixture.defaultFrame))
            .thenReturn("raw $closed")
        Mockito.`when`(fixture.previewService.preview("raw $closed", fixture.contact, null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("rendered", emptyList()))

        val response = fixture.service.assemble(fixture.request)

        assertEquals(2, response.itemVersions.size)
        assertEquals(listOf(9L, 10L), response.canonicalFactIds)
        assertEquals("raw $closed", response.rawDraftText)
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

    private data class DuplicateFixture(
        val service: TrustReplyWorkbenchService,
        val request: TrustReplyAssembleRequest,
        val contact: ExpertContact,
        val composer: AiReplyPointByPointComposer,
        val previewService: AiReplyDraftPreviewService,
        val defaultFrame: ResolvedReplyFrame
    )

    private fun duplicateFixture(
        item1Source: Long = 9L,
        item2Source: Long = 10L,
        item1Text: String = "Claim A",
        item2Text: String = "Claim B",
        // 计划 03 (T4.1/I-7): verbatim 重复用例——claims 恒空、SAFE_TEMPLATE，
        // 两条摘要绑同一事实集时 answerText 逐字相同必然触发查重。
        handling: TrustReplyItemHandling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
        generationKind: TrustReplyItemGenerationKind = TrustReplyItemGenerationKind.AI_GENERATED
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
        val verbatim = handling == TrustReplyItemHandling.ANSWER_FACTS_VERBATIM
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
        // 03a (C-1): per-request subsets must be stubbed too.
        listOf(item1Source, item2Source).distinct().forEach { id ->
            Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(id)))
                .thenReturn(Triple("e2", emptyList(), emptyList()))
        }
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
        // 03a (I-1): each locked item carries its own per-request evidence
        // version; the aggregate is the sha256 of the ordered concatenation.
        val perRequestOf = { key: String, sourceId: Long ->
            AiReplyDraftService.sha256Hex(listOf(key, sourceId.toString(), "e2").joinToString(" "))
        }
        val aggregateEvidence = AiReplyDraftService.sha256Hex(
            perRequestOf(key1, item1Source) + perRequestOf(key2, item2Source)
        )
        val emptyHash = AiReplyDraftService.sha256Hex("")
        fun locked(item: RequestFactItem, text: String, sourceId: Long): TrustReplyLockedItemRequest {
            val claims = if (verbatim) {
                emptyList()
            } else {
                listOf(AiReplyItemClaim("general.answer", text, listOf(sourceId)))
            }
            val requestKey = if (item.index == 1) key1 else key2
            val perRequest = perRequestOf(requestKey, sourceId)
            val versionId = TrustReplyWorkbenchService.versionId(
                requestKey = requestKey,
                handling = handling,
                answerText = text,
                claims = claims,
                model = "DEEPSEEK_V4_FLASH",
                generationKind = generationKind,
                evidenceSetVersion = perRequest,
                sourceVersion = sourceVersion,
                operatorInstructionHash = emptyHash
            )
            return TrustReplyLockedItemRequest(
                requestKey = requestKey,
                versionId = versionId,
                handling = handling,
                answerText = text,
                claims = claims,
                model = "DEEPSEEK_V4_FLASH",
                generationKind = generationKind,
                evidenceSetVersion = perRequest,
                sourceVersion = sourceVersion,
                operatorInstructionHash = emptyHash
            )
        }
        if (verbatim) {
            // I-4: validateLockedItem 服务端重算比对——mock 的 verbatim 正文
            // 逐字等于锁定正文。
            Mockito.`when`(draftService.composeVerbatimFactAnswer(item1)).thenReturn(item1Text)
            Mockito.`when`(draftService.composeVerbatimFactAnswer(item2)).thenReturn(item2Text)
        }
        val lockedItems = listOf(
            locked(item1, item1Text, item1Source),
            locked(item2, item2Text, item2Source)
        )
        return DuplicateFixture(
            service = service,
            request = TrustReplyAssembleRequest(source, sourceVersion, aggregateEvidence, lockedItems),
            contact = contact,
            composer = composer,
            previewService = previewService,
            defaultFrame = defaultFrame
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

    // 03a (I-2/I-3): three canonical requests over facts 9/10/11 with three
    // matrices — base (9/10/11), rebind (none/9+10/11) and item1Changed
    // (none/10/11). Every evidence subset resolves to the same "evidence-v1"
    // base so the tests focus on the version identity, not the snapshot.
    private data class ThreeItemFixture(
        val service: TrustReplyWorkbenchService,
        val source: TrustReplySourceRef,
        val sourceVersion: String,
        val key1: String,
        val key2: String,
        val key3: String,
        val baseMatrix: List<TrustReplyRequestFactSelection>,
        val rebindMatrix: List<TrustReplyRequestFactSelection>,
        val item1ChangedMatrix: List<TrustReplyRequestFactSelection>
    )

    private fun threeItemFixture(): ThreeItemFixture {
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
            body = "What?\nWho?\nHow?",
            cleanedBody = null,
            matchedQaRuleId = null,
            sendStatus = null,
            receivedAt = LocalDateTime.of(2026, 7, 28, 10, 0),
            sentAt = null
        )
        val item1 = item(1, "What?", listOf(9L), RequestGroundingStatus.GROUNDED)
        val item2 = item(2, "Who?", listOf(10L), RequestGroundingStatus.GROUNDED)
        val item3 = item(3, "How?", listOf(11L), RequestGroundingStatus.GROUNDED)
        val baseSelection = ResolvedQaRules(
            sendQaRuleIds = listOf(9L, 10L, 11L),
            promptRuleIds = listOf(9L, 10L, 11L),
            requestFacts = listOf(item1, item2, item3),
            requestCount = 3,
            groundedRequestCount = 3
        )
        val rebindItem1 = item(1, "What?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
        val rebindItem2 = item(2, "Who?", listOf(9L, 10L), RequestGroundingStatus.GROUNDED)
        val rebindSelection = ResolvedQaRules(
            sendQaRuleIds = listOf(9L, 10L, 11L),
            promptRuleIds = listOf(9L, 10L, 11L),
            requestFacts = listOf(rebindItem1, rebindItem2, item3),
            requestCount = 3,
            groundedRequestCount = 2
        )
        val changedItem1 = item(1, "What?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
        val changedSelection = ResolvedQaRules(
            sendQaRuleIds = listOf(10L, 11L),
            promptRuleIds = listOf(10L, 11L),
            requestFacts = listOf(changedItem1, item2, item3),
            requestCount = 3,
            groundedRequestCount = 2
        )
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(mail))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(mail))
        Mockito.`when`(trainingQa.buildKnowledgeContext("What?\nWho?\nHow?")).thenReturn("")
        Mockito.`when`(
            contextService.build(
                Mockito.any(ExpertContact::class.java) ?: contact,
                Mockito.anyList<MailRecord>() ?: emptyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString()
            )
        ).thenReturn(AiReplyContext("Name: Test", "history", emptyList(), true))
        Mockito.`when`(factSelection.selectForWorkbench("What?\nWho?\nHow?", null, null, true)).thenReturn(baseSelection)
        Mockito.`when`(factSelection.selectForWorkbench("What?\nWho?\nHow?", listOf(listOf(9L), listOf(10L), listOf(11L)), null, true))
            .thenReturn(baseSelection)
        Mockito.`when`(factSelection.selectForWorkbench("What?\nWho?\nHow?", listOf(emptyList(), listOf(9L, 10L), listOf(11L)), null, true))
            .thenReturn(rebindSelection)
        Mockito.`when`(factSelection.selectForWorkbench("What?\nWho?\nHow?", listOf(emptyList(), listOf(10L), listOf(11L)), null, true))
            .thenReturn(changedSelection)
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(Mockito.anyList<Long>()))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        Mockito.`when`(qaRules.findById(Mockito.anyLong())).thenReturn(Optional.of(QaRule(
            id = 9L,
            categoryId = 1,
            keywords = "salary",
            replyBody = "Salary info",
            answerBody = "Salary info",
            replySubject = null,
            enabled = true
        )))
        val defaultFrame = defaultResolvedFrame()
        Mockito.`when`(replySnippetService.resolveDefaultSelectableFrame()).thenReturn(defaultFrame)
        Mockito.`when`(composer.composeLockedItems(Mockito.anyList<String>(), Mockito.eq(defaultFrame) ?: defaultFrame))
            .thenAnswer { invocation ->
                val answers = invocation.getArgument(0) as List<*>
                "raw ${answers.joinToString("|")}"
            }
        Mockito.`when`(previewService.preview(Mockito.anyString(), Mockito.eq(contact) ?: contact, Mockito.isNull()))
            .thenAnswer { invocation ->
                AiReplyDraftPreviewService.PreviewResult(invocation.getArgument(0) as String, emptyList())
            }
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
        val key3 = TrustReplyWorkbenchService.requestKey(sourceVersion, 3, "How?", AiReplyIntentCatalog.matchIntents("How?").map { it.key })
        return ThreeItemFixture(
            service = service,
            source = source,
            sourceVersion = sourceVersion,
            key1 = key1,
            key2 = key2,
            key3 = key3,
            baseMatrix = listOf(
                TrustReplyRequestFactSelection(key1, listOf(9L)),
                TrustReplyRequestFactSelection(key2, listOf(10L)),
                TrustReplyRequestFactSelection(key3, listOf(11L))
            ),
            rebindMatrix = listOf(
                TrustReplyRequestFactSelection(key1, emptyList()),
                TrustReplyRequestFactSelection(key2, listOf(9L, 10L)),
                TrustReplyRequestFactSelection(key3, listOf(11L))
            ),
            item1ChangedMatrix = listOf(
                TrustReplyRequestFactSelection(key1, emptyList()),
                TrustReplyRequestFactSelection(key2, listOf(10L)),
                TrustReplyRequestFactSelection(key3, listOf(11L))
            )
        )
    }

    private fun perRequest(fixture: ThreeItemFixture, key: String, ids: List<Long>): String =
        TrustReplyWorkbenchService.requestEvidenceVersion(key, ids, baseSnapshotOf = { "evidence-v1" })

    private fun lockItem(
        fixture: ThreeItemFixture,
        key: String,
        answerText: String,
        ids: List<Long>,
        handling: TrustReplyItemHandling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
        generationKind: TrustReplyItemGenerationKind = TrustReplyItemGenerationKind.AI_GENERATED,
        claims: List<AiReplyItemClaim> = emptyList()
    ): TrustReplyLockedItemRequest {
        val normalizedAnswer = if (handling == TrustReplyItemHandling.OMIT) "" else answerText.trim()
        // canonical claims for grounded locks: general.answer over the ids
        val normalizedClaims = if (handling == TrustReplyItemHandling.OMIT) {
            emptyList()
        } else if (claims.isNotEmpty()) {
            claims
        } else {
            listOf(AiReplyItemClaim("general.answer", normalizedAnswer, ids))
        }
        val evidence = perRequest(fixture, key, ids)
        val versionId = TrustReplyWorkbenchService.versionId(
            requestKey = key,
            handling = handling,
            answerText = normalizedAnswer,
            claims = normalizedClaims,
            model = "DEEPSEEK_V4_FLASH",
            generationKind = generationKind,
            evidenceSetVersion = evidence,
            sourceVersion = fixture.sourceVersion,
            operatorInstructionHash = AiReplyDraftService.sha256Hex("")
        )
        return TrustReplyLockedItemRequest(
            requestKey = key,
            versionId = versionId,
            handling = handling,
            answerText = normalizedAnswer,
            claims = normalizedClaims,
            model = "DEEPSEEK_V4_FLASH",
            generationKind = generationKind,
            evidenceSetVersion = evidence,
            sourceVersion = fixture.sourceVersion,
            operatorInstructionHash = AiReplyDraftService.sha256Hex("")
        )
    }

    // 03a (I-2): moving fact F from request 1 to request 2 changes exactly the
    // per-request evidence versions of requests 1 and 2; request 3 stays
    // identical. A request-2 lock carrying the old version is rejected by
    // validateLockedItem with TRUST_REPLY_EVIDENCE_STALE at assembly time.
    @Test
    fun `rebinding a fact invalidates exactly both ends and leaves the third request untouched`() {
        val fixture = threeItemFixture()
        val source = fixture.source

        val baseBoot = fixture.service.bootstrap(
            TrustReplyBootstrapRequest(source, requestFactSelections = fixture.baseMatrix)
        )
        val rebindBoot = fixture.service.bootstrap(
            TrustReplyBootstrapRequest(source, requestFactSelections = fixture.rebindMatrix)
        )
        val coverageByKey = { boot: TrustReplyBootstrapResponse, key: String ->
            boot.requestCoverage.single { it.requestKey == key }.evidenceSetVersion
        }
        assertNotEquals(
            coverageByKey(baseBoot, fixture.key1),
            coverageByKey(rebindBoot, fixture.key1),
            "request 1 loses F so its per-request version must change"
        )
        assertNotEquals(
            coverageByKey(baseBoot, fixture.key2),
            coverageByKey(rebindBoot, fixture.key2),
            "request 2 gains F with a different key so its per-request version must change"
        )
        assertEquals(
            coverageByKey(baseBoot, fixture.key3),
            coverageByKey(rebindBoot, fixture.key3),
            "request 3 is untouched so its per-request version must not change"
        )

        // A rebind-matrix lock set where request 2 still carries the OLD
        // per-request version must be rejected at assembly time.
        val omitLock1 = lockItem(
            fixture, fixture.key1, "", emptyList(),
            handling = TrustReplyItemHandling.OMIT,
            generationKind = TrustReplyItemGenerationKind.OMITTED
        )
        val staleLock2 = lockItem(fixture, fixture.key2, "old answer", listOf(9L, 10L))
            .copy(evidenceSetVersion = perRequest(fixture, fixture.key2, listOf(10L)))
        val freshLock3 = lockItem(fixture, fixture.key3, "how answer", listOf(11L))
        val error = assertThrows(TrustReplyWorkbenchException::class.java) {
            fixture.service.assemble(TrustReplyAssembleRequest(
                source = source,
                expectedSourceVersion = fixture.sourceVersion,
                expectedEvidenceSetVersion = "ignored",
                lockedItems = listOf(omitLock1, staleLock2, freshLock3),
                requestFactSelections = fixture.rebindMatrix
            ))
        }
        assertEquals("TRUST_REPLY_EVIDENCE_STALE", error.code)
    }

    // 03a (I-3): after request 1's facts change, requests 2 and 3 keep their
    // unchanged per-request versions and still assemble successfully together
    // with a freshly generated request 1 lock.
    @Test
    fun `changing one request facts does not block assembling the unchanged requests`() {
        val fixture = threeItemFixture()
        val source = fixture.source

        val changedBoot = fixture.service.bootstrap(
            TrustReplyBootstrapRequest(source, requestFactSelections = fixture.item1ChangedMatrix)
        )
        val coverageByKey = { boot: TrustReplyBootstrapResponse, key: String ->
            boot.requestCoverage.single { it.requestKey == key }.evidenceSetVersion
        }
        val baseBoot = fixture.service.bootstrap(
            TrustReplyBootstrapRequest(source, requestFactSelections = fixture.baseMatrix)
        )
        // requests 2 and 3 are untouched by request 1's fact change
        assertEquals(coverageByKey(baseBoot, fixture.key2), coverageByKey(changedBoot, fixture.key2))
        assertEquals(coverageByKey(baseBoot, fixture.key3), coverageByKey(changedBoot, fixture.key3))

        val freshLock1 = lockItem(
            fixture, fixture.key1, "", emptyList(),
            handling = TrustReplyItemHandling.OMIT,
            generationKind = TrustReplyItemGenerationKind.OMITTED
        )
        val freshLock2 = lockItem(fixture, fixture.key2, "who answer", listOf(10L))
        val freshLock3 = lockItem(fixture, fixture.key3, "how answer", listOf(11L))
        val assembled = fixture.service.assemble(TrustReplyAssembleRequest(
            source = source,
            expectedSourceVersion = fixture.sourceVersion,
            expectedEvidenceSetVersion = "ignored",
            lockedItems = listOf(freshLock1, freshLock2, freshLock3),
            requestFactSelections = fixture.item1ChangedMatrix
        ))

        assertEquals(listOf(freshLock2.versionId, freshLock3.versionId), assembled.itemVersions.map { it.versionId }.takeLast(2))
        assertTrue(assembled.rawDraftText.isNotBlank())
    }

    // ── 计划 01：剥离后的答案过锁定校验，整合后框架语句恰好各出现一次 ──────────

    @Test
    fun `stripped operator directed answer validates and composes exactly one frame phrase each`() {
        val strippedAnswer =
            "We are reviewing your profile at this stage and will follow up once the initial review is complete."
        val fixture = assembleFixture(
            status = RequestGroundingStatus.UNSUPPORTED,
            handling = operatorDirectedHandling(),
            answerText = strippedAnswer,
            claims = emptyList()
        )
        val v47Frame = ResolvedReplyFrame(
            selection = ReplyFrameSelection(
                salutationSnippetId = 1L,
                greetingSnippetId = 2L,
                ackSnippetId = null,
                closingSnippetId = 3L
            ),
            version = "frame-v47",
            salutation = "Dear Professor,",
            greeting = "Thank you for your email. Please find our answers below.",
            acknowledgement = null,
            closing = "Please let us know if you have any further questions.\n\nBest regards,\nTalent Introduction Team"
        )
        Mockito.`when`(fixture.replySnippetService.resolveDefaultSelectableFrame()).thenReturn(v47Frame)
        // 用真实 composer 产出预期拼接结果，再以精确值桩掉 composer/preview，
        // 避免 any()/isNull() 这类返回 null 的 matcher 在 when(...) 内抛 NPE
        //（本文件既有桩一律使用 `?: default` 兜底或精确值，此处走精确值）。
        val realComposer = AiReplyPointByPointComposer(
            Mockito.mock(QaRuleRepository::class.java),
            fixture.replySnippetService
        )
        val expectedRaw = realComposer.composeLockedItems(listOf(strippedAnswer), v47Frame)
        Mockito.`when`(fixture.composer.composeLockedItems(listOf(strippedAnswer), v47Frame))
            .thenReturn(expectedRaw)
        Mockito.`when`(fixture.previewService.preview(expectedRaw, fixture.contact, null))
            .thenReturn(AiReplyDraftPreviewService.PreviewResult("rendered", emptyList()))

        // IP-2：剥离后的答案逐字通过 validateLockedItem（assemble 内部），
        // claims 为空、findViolations 为空。
        val response = fixture.service.assemble(fixture.request)

        assertEquals(strippedAnswer, response.itemVersions.single().answerText)
        assertTrue(response.itemVersions.single().claims.isEmpty())
        // IP-1：整合正文中三种框架语句各自只出现一次，且只由 frame 片段提供。
        assertEquals(1, response.rawDraftText.split("Dear Professor,").size - 1)
        assertEquals(1, response.rawDraftText.split("Best regards").size - 1)
        assertEquals(1, response.rawDraftText.split("Thank you for your email").size - 1)
        assertTrue(response.rawDraftText.startsWith("Dear Professor,"))
        assertTrue(response.rawDraftText.contains("\n\nBest regards,\nTalent Introduction Team"))
    }

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
        val stateStore: TrustReplyWorkbenchStateStore,
        val defaultFrame: ResolvedReplyFrame,
        val selection: ResolvedQaRules
    )

    private fun assembleFixture(
        status: RequestGroundingStatus = RequestGroundingStatus.GROUNDED,
        handling: TrustReplyItemHandling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
        answerText: String = "Salary info",
        claims: List<AiReplyItemClaim> = listOf(AiReplyItemClaim("general.answer", "Salary info", listOf(9L))),
        generationKind: TrustReplyItemGenerationKind = TrustReplyItemGenerationKind.AI_GENERATED,
        intents: List<RequestIntentCoverage> = emptyList(),
        boundFactIds: List<Long> = emptyList(),
        // 计划 03 (T4.1): 多事实 verbatim 用例需要自定义事实集与 answerBody。
        factIds: List<Long> = listOf(9L),
        factBodies: Map<Long, String> = emptyMap()
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
        val baseItem = item(1, "What?", factIds, status)
        val item = if (intents.isEmpty()) baseItem else baseItem.copy(intents = intents)
        val boundItem = if (boundFactIds.isEmpty()) {
            item
        } else {
            item.copy(boundRuleIds = boundFactIds + item.factRuleIds)
        }
        val selection = ResolvedQaRules(
            sendQaRuleIds = factIds,
            promptRuleIds = (factIds + boundFactIds).distinct(),
            requestFacts = listOf(boundItem),
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
        factIds.distinct().forEach { id ->
            val body = factBodies[id] ?: "Salary info"
            Mockito.`when`(qaRules.findById(id)).thenReturn(Optional.of(QaRule(
                id = id,
                categoryId = 1,
                keywords = "salary",
                replyBody = body,
                answerBody = body,
                replySubject = null,
                enabled = true
            )))
        }
        val baseEvidenceSetVersion = if (factIds == listOf(9L)) {
            AiReplyDraftService.sha256Hex(
                "9:true:null:${AiReplyDraftService.sha256Hex("Salary info")}"
            )
        } else {
            AiReplyDraftService.sha256Hex(
                factIds.joinToString(",") { id ->
                    "$id:true:null:${AiReplyDraftService.sha256Hex(factBodies[id] ?: "Salary info")}"
                }
            )
        }
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(factIds))
            .thenReturn(Triple(baseEvidenceSetVersion, emptyList(), emptyList()))
        if (boundItem.boundRuleIds != factIds) {
            Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(boundItem.boundRuleIds))
                .thenReturn(Triple(baseEvidenceSetVersion, emptyList(), emptyList()))
        }
        val canonicalAnswer = when (handling) {
            TrustReplyItemHandling.OMIT -> ""
            TrustReplyItemHandling.ACKNOWLEDGE_PENDING -> answerText.trim()
            else -> answerText.trim().ifBlank { claims.joinToString(AiReplyDraftService.CLAIM_PARAGRAPH_SEPARATOR) { it.text } }
        }
        val operatorInstruction = if (
            handling.name == "ANSWER_FROM_OPERATOR_INPUT" ||
            // 计划 02 (I-9): 混合生成同样要求非空说明。
            handling.name == "ANSWER_EVIDENCE_WITH_OPERATOR_INPUT"
        ) {
            "Use the operator-provided basis."
        } else {
            ""
        }
        val canonicalClaims = if (
            handling == TrustReplyItemHandling.OMIT ||
            handling == TrustReplyItemHandling.ACKNOWLEDGE_PENDING ||
            // 计划 02 (I-8): 混合答案 claims 恒空。
            handling == TrustReplyItemHandling.ANSWER_EVIDENCE_WITH_OPERATOR_INPUT ||
            // 计划 03 (I-3): verbatim 正文是事实 answerBody 逐字拼接，claims 恒空。
            handling == TrustReplyItemHandling.ANSWER_FACTS_VERBATIM
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
        val requestKey = TrustReplyWorkbenchService.requestKey(
            sourceVersion, 1, "What?", item.intents.map { it.intentKey }
        )
        // 03a (I-1): the locked item carries the per-request evidence version;
        // the assemble request's expectedEvidenceSetVersion is now ignored by
        // the server, so the fixture reuses the same per-request value.
        // P2b (C-4): the per-request evidence version derives from boundRuleIds
        // (an identity input) while sendQaRuleIds stays evidence-only; when there
        // are no extra bindings this is byte-identical to the legacy "9" form.
        val evidenceSetVersion = AiReplyDraftService.sha256Hex(
            listOf(requestKey, boundItem.boundRuleIds.joinToString(","), baseEvidenceSetVersion).joinToString(" ")
        )
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
            stateStore = stateStore,
            defaultFrame = defaultFrame,
            selection = selection
        )
    }

    // 03b (I-2/I-3): two canonical requests — one research-context item and
    // one general item — over the same inbound. The fixture exposes the
    // context-service mock so the test can vary the expert profile content
    // between bootstraps with a constant researchProfileSufficient.
    private data class ResearchSplitFixture(
        val service: TrustReplyWorkbenchService,
        val contextService: AiReplyContextService,
        val source: TrustReplySourceRef,
        val selection: ResolvedQaRules,
        val contact: ExpertContact,
        val keyResearch: String,
        val keyGeneral: String
    )

    private fun researchSplitFixture(): ResearchSplitFixture {
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
            body = "Does my research fit?\nWhat is the funding amount?",
            cleanedBody = null,
            matchedQaRuleId = null,
            sendStatus = null,
            receivedAt = LocalDateTime.of(2026, 7, 28, 10, 0),
            sentAt = null
        )
        // The intent keys must match exactly what canonicalRequests derives
        // from AiReplyIntentCatalog.matchIntents, so they are built from the
        // catalog here instead of hand-written.
        val researchIntents = AiReplyIntentCatalog.matchIntents("Does my research fit?").map { def ->
            RequestIntentCoverage(
                intentKey = def.key,
                title = def.title,
                requiredCoverageKeys = def.requiredCoverageKeys,
                evidenceRuleIds = listOf(9L),
                status = "SUPPORTED",
                missingEvidenceKeys = emptyList(),
                requiresResearchContext = def.requiresProfile
            )
        }
        val generalIntents = AiReplyIntentCatalog.matchIntents("What is the funding amount?").map { def ->
            RequestIntentCoverage(
                intentKey = def.key,
                title = def.title,
                requiredCoverageKeys = def.requiredCoverageKeys,
                evidenceRuleIds = listOf(10L),
                status = "SUPPORTED",
                missingEvidenceKeys = emptyList(),
                requiresResearchContext = def.requiresProfile
            )
        }
        val researchItem = RequestFactItem(
            index = 1,
            requestText = "Does my research fit?",
            factRuleIds = listOf(9L),
            status = RequestGroundingStatus.GROUNDED,
            requiresResearchContext = true,
            boundRuleIds = listOf(9L),
            intents = researchIntents
        )
        val generalItem = RequestFactItem(
            index = 2,
            requestText = "What is the funding amount?",
            factRuleIds = listOf(10L),
            status = RequestGroundingStatus.GROUNDED,
            requiresResearchContext = false,
            boundRuleIds = listOf(10L),
            intents = generalIntents
        )
        val selection = ResolvedQaRules(
            sendQaRuleIds = listOf(9L, 10L),
            promptRuleIds = listOf(9L, 10L),
            requestFacts = listOf(researchItem, generalItem),
            requestCount = 2,
            groundedRequestCount = 2
        )
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(mail))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(mail))
        Mockito.`when`(trainingQa.buildKnowledgeContext(Mockito.anyString())).thenReturn("")
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
                researchProfileSufficient = true,
                expertProfileText = "profile-v1"
            )
        )
        Mockito.`when`(
            factSelection.selectForWorkbench(
                "Does my research fit?\nWhat is the funding amount?",
                listOf(listOf(9L), listOf(10L)),
                null,
                true
            )
        ).thenReturn(selection)
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(9L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(listOf(10L)))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(emptyList()))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        Mockito.`when`(replySnippetService.resolveDefaultSelectableFrame()).thenReturn(defaultResolvedFrame())
        Mockito.`when`(qaRules.findById(Mockito.anyLong())).thenReturn(Optional.of(QaRule(
            id = 9L,
            categoryId = 1,
            keywords = "salary",
            replyBody = "Salary info",
            answerBody = "Salary info",
            replySubject = null,
            enabled = true
        )))
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
        val keyResearch = TrustReplyWorkbenchService.requestKey(
            sourceVersion, 1, "Does my research fit?",
            AiReplyIntentCatalog.matchIntents("Does my research fit?").map { it.key }
        )
        val keyGeneral = TrustReplyWorkbenchService.requestKey(
            sourceVersion, 2, "What is the funding amount?",
            AiReplyIntentCatalog.matchIntents("What is the funding amount?").map { it.key }
        )
        return ResearchSplitFixture(
            service = service,
            contextService = contextService,
            source = source,
            selection = selection,
            contact = contact,
            keyResearch = keyResearch,
            keyGeneral = keyGeneral
        )
    }

    // 03b (I-2/I-3): with researchProfileSufficient constant at true, changing
    // only the expert profile CONTENT alters the research-context item's
    // per-request evidence while the general item's value stays byte-identical
    // to the 03a baseline (requestEvidenceVersion without research evidence).
    @Test
    fun `expert profile content change alters only the research context item evidence`() {
        val fixture = researchSplitFixture()
        val matrix = listOf(
            TrustReplyRequestFactSelection(fixture.keyResearch, listOf(9L)),
            TrustReplyRequestFactSelection(fixture.keyGeneral, listOf(10L))
        )
        val coverageByKey = { boot: TrustReplyBootstrapResponse, key: String ->
            boot.requestCoverage.single { it.requestKey == key }.evidenceSetVersion
        }
        val boot1 = fixture.service.bootstrap(
            TrustReplyBootstrapRequest(fixture.source, requestFactSelections = matrix)
        )
        val research1 = coverageByKey(boot1, fixture.keyResearch)
        val general1 = coverageByKey(boot1, fixture.keyGeneral)

        // I-3: the boolean stays true; only the profile content changes.
        Mockito.`when`(
            fixture.contextService.build(
                Mockito.any(ExpertContact::class.java) ?: fixture.contact,
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
                expertProfileText = "profile-v2"
            )
        )
        val boot2 = fixture.service.bootstrap(
            TrustReplyBootstrapRequest(fixture.source, requestFactSelections = matrix)
        )
        val research2 = coverageByKey(boot2, fixture.keyResearch)
        val general2 = coverageByKey(boot2, fixture.keyGeneral)

        // I-2: the research item's per-request evidence changes with the profile.
        assertNotEquals(research1, research2)
        // I-2: the general item stays byte-identical (not just unchanged) —
        // its value equals the exact 03a 3-component baseline.
        assertEquals(general1, general2)
        assertEquals(
            TrustReplyWorkbenchService.requestEvidenceVersion(fixture.keyGeneral, listOf(10L), baseSnapshotOf = { "evidence-v1" }),
            general2
        )
        // The research value is NOT the 03a 3-component baseline.
        assertNotEquals(
            TrustReplyWorkbenchService.requestEvidenceVersion(fixture.keyResearch, listOf(9L), baseSnapshotOf = { "evidence-v1" }),
            research2
        )
    }

    // 03b (I-2/I-3): requestEvidenceVersion's researchEvidence input — an
    // explicit null keeps the hash byte-identical to the 03a 3-component form;
    // a content-only change (boolean constant) and a sufficiency flip both
    // move the version.
    @Test
    fun `research evidence participates only when provided`() {
        val baseSnapshotOf = { ids: List<Long> -> "base-${ids.joinToString(".")}" }
        val baseline = TrustReplyWorkbenchService.requestEvidenceVersion("key-a", listOf(9L, 10L), baseSnapshotOf)
        // explicit null ≡ absent: the 03a identity is preserved byte-for-byte.
        assertEquals(
            baseline,
            TrustReplyWorkbenchService.requestEvidenceVersion("key-a", listOf(9L, 10L), baseSnapshotOf, null)
        )
        val research = TrustReplyWorkbenchService.requestEvidenceVersion(
            "key-a", listOf(9L, 10L), baseSnapshotOf, "profile-hash-1 true"
        )
        assertNotEquals(baseline, research)
        // I-3: same boolean, different profile content — still moves.
        assertNotEquals(
            research,
            TrustReplyWorkbenchService.requestEvidenceVersion(
                "key-a", listOf(9L, 10L), baseSnapshotOf, "profile-hash-2 true"
            )
        )
        // Sufficiency flip also moves the version.
        assertNotEquals(
            research,
            TrustReplyWorkbenchService.requestEvidenceVersion(
                "key-a", listOf(9L, 10L), baseSnapshotOf, "profile-hash-1 false"
            )
        )
    }
}
