package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.MailSendAttempt
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
import com.weibo.talentintroduction.campaign.repository.MailSendAttemptRepository
import com.weibo.talentintroduction.llm.service.RequestGroundingStatus
import com.weibo.talentintroduction.llm.service.TrustReplyDiagnosticFlag
import com.weibo.talentintroduction.llm.service.TrustReplyDiagnostics
import com.weibo.talentintroduction.llm.service.TrustReplyItemHandling
import com.weibo.talentintroduction.llm.service.TrustReplyRequestDiagnostic
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

class ManualReplySendAttemptServiceTest {
    private val attemptRepository = Mockito.mock(MailSendAttemptRepository::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val mailRecordQaRuleRepository = Mockito.mock(MailRecordQaRuleRepository::class.java)
    private val operatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)
    private val service = ManualReplySendAttemptService(
        attemptRepository, mailRecordRepository, mailRecordQaRuleRepository, operatorActionLogService
    )

    private val payload = ManualReplySendAttemptService.SendPayload(
        orcidId = "orcid-1",
        contactId = 1L,
        inboundProcessingId = 100L,
        accountCode = "sender-1",
        normalizedRecipient = "expert@test.com",
        subject = "Re: Test",
        finalText = "Hello world",
        finalHtml = "<p>Hello world</p>",
        inReplyTo = "in-1",
        canonicalQaRuleIds = listOf(10L),
        primaryRuleId = 10L
    )

    private fun createAttempt(status: String, fp: ManualReplySendAttemptService.Fingerprint) = MailSendAttempt(
        id = 1L,
        orcidId = payload.orcidId,
        mailType = fp.shortKey,
        accountCode = payload.accountCode,
        messageId = fp.messageId,
        status = status,
        recipient = payload.normalizedRecipient,
        subject = payload.subject,
        body = fp.fullHex,
        contentType = "application/x-manual-rich-fingerprint-v1"
    )

    private fun stubFindForUpdate(fp: ManualReplySendAttemptService.Fingerprint, status: String) {
        Mockito.`when`(
            attemptRepository.findByOrcidIdAndMailTypeForUpdate(anyString(), anyString())
        ).thenReturn(createAttempt(status, fp))
    }

    @Test
    fun `fingerprint same payload produces consistent short key`() {
        val fp1 = service.computeFingerprint(payload)
        val fp2 = service.computeFingerprint(payload)
        assertEquals(64, fp1.fullHex.length)
        assertEquals("MANUAL_RICH:" + fp1.fullHex.take(32), fp1.shortKey)
        assertEquals(fp1.fullHex, fp2.fullHex)
        assertEquals(fp1.shortKey, fp2.shortKey)
    }

    @Test
    fun `fingerprint different subjects produce different keys`() {
        val fp1 = service.computeFingerprint(payload)
        val fp2 = service.computeFingerprint(payload.copy(subject = "Different"))
        assertTrue(fp1.fullHex != fp2.fullHex)
        assertTrue(fp1.shortKey != fp2.shortKey)
    }

    @Test
    fun `fingerprint different contactId produces different keys`() {
        val fp1 = service.computeFingerprint(payload)
        val fp2 = service.computeFingerprint(payload.copy(contactId = 2L))
        assertTrue(fp1.fullHex != fp2.fullHex)
        assertTrue(fp1.shortKey != fp2.shortKey)
    }

    @Test
    fun `fingerprint same contactId back restores original hash`() {
        val fp1 = service.computeFingerprint(payload)
        val fp2 = service.computeFingerprint(payload.copy(contactId = 2L))
        val fp3 = service.computeFingerprint(payload.copy(contactId = 1L))
        assertEquals(fp1.fullHex, fp3.fullHex)
        assertEquals(fp1.shortKey, fp3.shortKey)
        assertTrue(fp1.fullHex != fp2.fullHex)
    }

    @Test
    fun `fingerprint different QA order produces different keys`() {
        val fp1 = service.computeFingerprint(payload.copy(canonicalQaRuleIds = listOf(10L, 20L)))
        val fp2 = service.computeFingerprint(payload.copy(canonicalQaRuleIds = listOf(20L, 10L)))
        assertTrue(fp1.fullHex != fp2.fullHex)
        assertTrue(fp1.shortKey != fp2.shortKey)
    }

    @Test
    fun `fingerprint messageId is UUID-based`() {
        val fp = service.computeFingerprint(payload)
        assertTrue(fp.messageId.startsWith("<manual-rich-"))
        assertTrue(fp.messageId.endsWith("@weibo.com>"))
    }

    @Test
    fun `prepareAndClaim inserts and claims when status is PREPARED`() {
        val fp = service.computeFingerprint(payload)
        stubFindForUpdate(fp, MailSendAttemptStatus.PREPARED)
        Mockito.`when`(
            attemptRepository.claimStatus(Mockito.anyLong(), anyString(), anyString(), Mockito.any())
        ).thenReturn(1)
        val result = service.prepareAndClaim(payload)
        assertEquals(ManualReplySendAttemptService.ClaimResult.CLAIMED, result.result)
    }

    @Test
    fun `prepareAndClaim returns DEDUP_SENT when status is SENT`() {
        val fp = service.computeFingerprint(payload)
        stubFindForUpdate(fp, MailSendAttemptStatus.SENT)
        val result = service.prepareAndClaim(payload)
        assertEquals(ManualReplySendAttemptService.ClaimResult.DEDUP_SENT, result.result)
    }

    @Test
    fun `prepareAndClaim returns SAFE_RETRY_CLAIMED when status is FAILED_SAFE_TO_RETRY`() {
        val fp = service.computeFingerprint(payload)
        stubFindForUpdate(fp, MailSendAttemptStatus.FAILED_SAFE_TO_RETRY)
        Mockito.`when`(
            attemptRepository.claimStatus(Mockito.anyLong(), anyString(), anyString(), Mockito.any())
        ).thenReturn(1)
        val result = service.prepareAndClaim(payload)
        assertEquals(ManualReplySendAttemptService.ClaimResult.SAFE_RETRY_CLAIMED, result.result)
    }

    @Test
    fun `prepareAndClaim returns IN_PROGRESS when status is DELIVERY_IN_PROGRESS`() {
        val fp = service.computeFingerprint(payload)
        stubFindForUpdate(fp, MailSendAttemptStatus.DELIVERY_IN_PROGRESS)
        val result = service.prepareAndClaim(payload)
        assertEquals(ManualReplySendAttemptService.ClaimResult.IN_PROGRESS, result.result)
    }

    @Test
    fun `prepareAndClaim returns UNKNOWN when status is DELIVERY_UNKNOWN`() {
        val fp = service.computeFingerprint(payload)
        stubFindForUpdate(fp, MailSendAttemptStatus.DELIVERY_UNKNOWN)
        val result = service.prepareAndClaim(payload)
        assertEquals(ManualReplySendAttemptService.ClaimResult.UNKNOWN, result.result)
    }

    @Test
    fun `prepareAndClaim returns PERMANENT_FAILED when status is FAILED`() {
        val fp = service.computeFingerprint(payload)
        stubFindForUpdate(fp, MailSendAttemptStatus.FAILED)
        val result = service.prepareAndClaim(payload)
        assertEquals(ManualReplySendAttemptService.ClaimResult.PERMANENT_FAILED, result.result)
    }

    @Test
    fun `prepareAndClaim rejects full hash collision`() {
        val fp = service.computeFingerprint(payload)
        val attempt = createAttempt(MailSendAttemptStatus.PREPARED, fp).copy(body = "other-hash")
        Mockito.`when`(
            attemptRepository.findByOrcidIdAndMailTypeForUpdate(anyString(), anyString())
        ).thenReturn(attempt)
        assertThrows(IllegalArgumentException::class.java) { service.prepareAndClaim(payload) }
    }

    @Test
    fun `prepareAndClaim rejects recipient collision on short key`() {
        val fp = service.computeFingerprint(payload)
        val attempt = createAttempt(MailSendAttemptStatus.PREPARED, fp).copy(recipient = "other@test.com")
        Mockito.`when`(
            attemptRepository.findByOrcidIdAndMailTypeForUpdate(anyString(), anyString())
        ).thenReturn(attempt)
        assertThrows(IllegalArgumentException::class.java) { service.prepareAndClaim(payload) }
    }

    @Test
    fun `finalizeSuccess creates mail record and writes QA associations`() {
        val attempt = createAttempt(MailSendAttemptStatus.DELIVERY_IN_PROGRESS, service.computeFingerprint(payload))
        Mockito.`when`(attemptRepository.findById(1L)).thenReturn(Optional.of(attempt))
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 500L) }
        val mailRecordId = service.finalizeSuccess(payload, 1L, "<manual-rich-abc@weibo.com>")
        assertEquals(500L, mailRecordId)
    }

    // 03 (I-6): mail_record_qa_rule 按 SendPayload.canonicalQaRuleIds 的 ordinal
    // 逐元素精确写入 —— 与 verified canonical facts 完全一致，无自动推荐事实、
    // 无漏项、无重复键（canonical union 已在 assembly selection 侧去重）。
    @Test
    fun `finalizeSuccess writes QA associations in exact payload ordinal order`() {
        val orderedPayload = payload.copy(canonicalQaRuleIds = listOf(10L, 20L, 30L))
        val attempt = createAttempt(
            MailSendAttemptStatus.DELIVERY_IN_PROGRESS,
            service.computeFingerprint(orderedPayload)
        )
        Mockito.`when`(attemptRepository.findById(1L)).thenReturn(Optional.of(attempt))
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 500L) }

        val mailRecordId = service.finalizeSuccess(orderedPayload, 1L, "<manual-rich-abc@weibo.com>")

        assertEquals(500L, mailRecordId)
        val order = Mockito.inOrder(mailRecordQaRuleRepository)
        order.verify(mailRecordQaRuleRepository).save(
            com.weibo.talentintroduction.mail.domain.MailRecordQaRule(
                mailRecordId = 500L, qaRuleId = 10L, ordinal = 0
            )
        )
        order.verify(mailRecordQaRuleRepository).save(
            com.weibo.talentintroduction.mail.domain.MailRecordQaRule(
                mailRecordId = 500L, qaRuleId = 20L, ordinal = 1
            )
        )
        order.verify(mailRecordQaRuleRepository).save(
            com.weibo.talentintroduction.mail.domain.MailRecordQaRule(
                mailRecordId = 500L, qaRuleId = 30L, ordinal = 2
            )
        )
    }

    @Test
    fun `finalizeFailure creates failed mail record with error summary`() {
        val attempt = createAttempt(MailSendAttemptStatus.DELIVERY_IN_PROGRESS, service.computeFingerprint(payload))
        Mockito.`when`(attemptRepository.findById(1L)).thenReturn(Optional.of(attempt))
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 501L) }
        val mailRecordId = service.finalizeFailure(
            payload, 1L, "<manual-rich-abc@weibo.com>",
            MailSendAttemptStatus.FAILED, "SMTP error: 550 rejected"
        )
        assertEquals(501L, mailRecordId)
    }

    private fun delivered() = DeliveredMail(messageId = "out-1", status = "SENT")

    private fun inboundRecord() = com.weibo.talentintroduction.mail.domain.InboundMailProcessing(
        id = 100L, senderAccountCode = "sender-1", imapUid = 1L,
        messageId = "in-1", fromEmail = "expert@test.com",
        subject = "Test", body = "Hello",
        receivedAt = LocalDateTime.now(),
        processStatus = "MANUAL_REVIEW", processReason = "QA_NO_MATCH",
        expertContactId = 1L
    )

    private fun diagnostics() = TrustReplyDiagnostics(
        schemaVersion = TrustReplyDiagnostics.SCHEMA_VERSION,
        flags = listOf(
            TrustReplyDiagnosticFlag.MANUAL_FACT_SELECTED,
            TrustReplyDiagnosticFlag.INTENT_MISMATCH
        ),
        requestSnapshots = listOf(
            TrustReplyRequestDiagnostic(
                requestKey = "req-1",
                status = RequestGroundingStatus.GROUNDED.name,
                handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE.name,
                detectedIntentKeys = listOf("INTENT_A"),
                unrecognizedAskCount = 0,
                manualFactRuleIds = listOf(10L),
                intentMatchedFactRuleIds = listOf(10L),
                intentMismatchFactRuleIds = listOf(20L),
                flags = listOf(
                    TrustReplyDiagnosticFlag.MANUAL_FACT_SELECTED,
                    TrustReplyDiagnosticFlag.INTENT_MISMATCH
                ),
                factIdsTruncated = false,
                intentKeysTruncated = false
            )
        ),
        requestTotal = 1,
        requestTruncated = false
    )

    // 04 (I-1): 有 verified 诊断时，既有 SEND_MANUAL_COMPOSED_REPLY after map 追加
    // trustReplyDiagnostics；既有字段（mailRecordId/canonicalFactIds/subject/...）逐字保留。
    @Test
    fun `recordSendAudit appends trust reply diagnostics to composed reply after map`() {
        service.recordSendAudit(
            inboundProcessingId = 100L, contactId = 1L, mailRecordId = 500L,
            canonicalFactIds = listOf(10L), carriesQa = true,
            delivered = delivered(), sendSubject = "Re: Test",
            bodyPreviewText = "Hello", operatorName = "op",
            inboundRecord = inboundRecord(), serverSuggestedFactIds = listOf(10L),
            edited = false, note = "note",
            trustReplyDiagnostics = diagnostics()
        )
        val invocation = Mockito.mockingDetails(operatorActionLogService).invocations
            .single { it.method.name == "record" }
        assertEquals(com.weibo.talentintroduction.audit.domain.OperatorActionType.SEND_MANUAL_COMPOSED_REPLY, invocation.arguments[2])
        val after = invocation.arguments[6] as Map<*, *>
        assertEquals(500L, after["mailRecordId"])
        assertEquals(listOf(10L), after["canonicalFactIds"])
        assertEquals(listOf(10L), after["serverSuggestedFactIds"])
        assertEquals("Re: Test", after["subject"])
        assertEquals("Hello", after["bodyPreviewText"])
        assertEquals(false, after["edited"])
        assertEquals(diagnostics(), after["trustReplyDiagnostics"])
    }

    // 04 (阶段 3): 「工作台无事实但完成发送」仍可把 unrecognized/unsupported 诊断
    // 记到 SEND_MANUAL_RICH_REPLY 分支，rich 分支既有字段不变。
    @Test
    fun `recordSendAudit appends trust reply diagnostics to rich reply after map`() {
        service.recordSendAudit(
            inboundProcessingId = 100L, contactId = 1L, mailRecordId = 500L,
            canonicalFactIds = emptyList(), carriesQa = false,
            delivered = delivered(), sendSubject = "Re: Test",
            bodyPreviewText = "Hello", operatorName = "op",
            inboundRecord = inboundRecord(), serverSuggestedFactIds = emptyList(),
            edited = null, note = "note",
            trustReplyDiagnostics = diagnostics()
        )
        val invocation = Mockito.mockingDetails(operatorActionLogService).invocations
            .single { it.method.name == "record" }
        assertEquals(com.weibo.talentintroduction.audit.domain.OperatorActionType.SEND_MANUAL_RICH_REPLY, invocation.arguments[2])
        val after = invocation.arguments[6] as Map<*, *>
        assertEquals(diagnostics(), after["trustReplyDiagnostics"])
        assertEquals(500L, after["mailRecordId"])
        assertEquals("Hello", after["bodyPreviewText"])
        assertFalse(after.containsKey("canonicalFactIds"))
    }

    // 04 (I-7): 无诊断（纯人工 rich reply / legacy QA 发送）时 after payload
    // 逐字保留，不出现 trustReplyDiagnostics 键。
    @Test
    fun `recordSendAudit leaves after payload verbatim when diagnostics absent`() {
        service.recordSendAudit(
            inboundProcessingId = 100L, contactId = 1L, mailRecordId = 500L,
            canonicalFactIds = listOf(10L), carriesQa = true,
            delivered = delivered(), sendSubject = "Re: Test",
            bodyPreviewText = "Hello", operatorName = "op",
            inboundRecord = inboundRecord(), serverSuggestedFactIds = listOf(10L),
            edited = false, note = "note"
        )
        val invocation = Mockito.mockingDetails(operatorActionLogService).invocations
            .single { it.method.name == "record" }
        val after = invocation.arguments[6] as Map<*, *>
        assertEquals(500L, after["mailRecordId"])
        assertEquals(listOf(10L), after["canonicalFactIds"])
        assertEquals("Re: Test", after["subject"])
        assertFalse(after.containsKey("trustReplyDiagnostics"))
    }

    // 04 (I-6): LIVE 审计保持 after-commit best-effort —— 写失败只 warn，不抛异常、
    // 不反转已发送邮件。
    @Test
    fun `recordSendAudit best effort does not throw on failure`() {
        val actionType = com.weibo.talentintroduction.audit.domain.OperatorActionType.SEND_MANUAL_COMPOSED_REPLY
        val before = mapOf("inboundProcessingId" to 100L)
        val after = mapOf(
            "mailRecordId" to 500L,
            "canonicalFactIds" to listOf(10L),
            "serverSuggestedFactIds" to listOf(10L),
            "qaRuleIds" to listOf(10L),
            "suggestedRuleIds" to listOf(10L),
            "draftGenerationState" to null,
            "edited" to false,
            "sendStatus" to "SENT",
            "subject" to "Re: Test",
            "bodyPreviewText" to "Hello"
        )
        Mockito.doThrow(RuntimeException("audit down"))
            .`when`(operatorActionLogService)
            .record("INBOUND_MAIL_PROCESSING", 100L, actionType, 1L, 100L, before, after, "op", "note", null)
        service.recordSendAudit(
            inboundProcessingId = 100L, contactId = 1L, mailRecordId = 500L,
            canonicalFactIds = listOf(10L), carriesQa = true,
            delivered = delivered(), sendSubject = "Re: Test",
            bodyPreviewText = "Hello", operatorName = "op",
            inboundRecord = inboundRecord(), serverSuggestedFactIds = listOf(10L),
            edited = false, note = "note"
        )
        Mockito.verify(operatorActionLogService, Mockito.times(1))
            .record("INBOUND_MAIL_PROCESSING", 100L, actionType, 1L, 100L, before, after, "op", "note", null)
    }

    // =====================================================================
    // 会话（无来信）回信：requestId 幂等键 / 锚点指纹 / 收敛读 / 审计（T1，I-3~I-7）
    // =====================================================================

    // I-5: 固定 inbound payload 的 fullHex/shortKey 回归值（部署前后同一来信/正文的
    // 幂等键不得漂移 —— 字段顺序/首段数字 ID/算法不变）。
    @Test
    fun `inbound fingerprint stays byte identical to the pinned regression values`() {
        val fp = service.computeFingerprint(payload)
        assertEquals(64, fp.fullHex.length)
        assertEquals("fa838dbceec46871ec1eae26e9ba4666d6f1ac0fda1bd36f872110efd38becda", fp.fullHex)
        assertEquals("MANUAL_RICH:fa838dbceec46871ec1eae26e9ba4666", fp.shortKey)
        assertTrue(fp.shortKey.length <= 50, "mail_type VARCHAR(50) 上限")
    }

    @Test
    fun `conversation request id canonicalizes to lowercase uuid and fixed 50 char short key`() {
        val raw = "B5C98F60-7b46-4f1e-9c11-1a2b3c4d5e6f"
        val canonical = service.canonicalConversationRequestId(raw)
        assertEquals("b5c98f60-7b46-4f1e-9c11-1a2b3c4d5e6f", canonical)
        val key1 = service.conversationRequestShortKey(raw)
        val key2 = service.conversationRequestShortKey(canonical)
        assertEquals(50, key1.length, "MANUAL_RICH_REQUEST:20 字符 + 30 hex = 50（VARCHAR(50)）")
        assertTrue(key1.startsWith("MANUAL_RICH_REQUEST:"))
        assertEquals(key1, key2, "大小写/空白规范化后同一短键")
        assertFalse(key1.startsWith("MANUAL_RICH:"), "与来信前缀隔离")
    }

    @Test
    fun `different request ids produce different short keys and invalid ids fail immediately`() {
        val keyA = service.conversationRequestShortKey("00000000-0000-4000-8000-000000000001")
        val keyB = service.conversationRequestShortKey("00000000-0000-4000-8000-000000000002")
        assertTrue(keyA != keyB)
        assertThrows(IllegalArgumentException::class.java) {
            service.canonicalConversationRequestId("not-a-uuid")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.conversationRequestShortKey("not-a-uuid")
        }
    }

    private fun outboundPayload(requestId: String, anchorRecordId: Long = 11L) = payload.copy(
        inboundProcessingId = null,
        sourceAnchor = "${ManualReplySendAttemptService.SOURCE_ANCHOR_PREFIX}$anchorRecordId",
        idempotencyRequestId = requestId
    )

    @Test
    fun `outbound fingerprint requires exactly one of inbound id or anchor plus request id`() {
        val withoutRequest = payload.copy(inboundProcessingId = null, sourceAnchor = "MAIL_RECORD:11")
        assertThrows(IllegalArgumentException::class.java) { service.computeFingerprint(withoutRequest) }
        val withoutAnchor = payload.copy(inboundProcessingId = null, idempotencyRequestId = "00000000-0000-4000-8000-000000000001")
        assertThrows(IllegalArgumentException::class.java) { service.computeFingerprint(withoutAnchor) }
        assertThrows(IllegalArgumentException::class.java) {
            service.computeFingerprint(payload.copy(sourceAnchor = "MAIL_RECORD:11"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.computeFingerprint(payload.copy(idempotencyRequestId = "00000000-0000-4000-8000-000000000001"))
        }
    }

    @Test
    fun `outbound short key is request derived while anchor enters the full hash`() {
        val requestId = "b5c98f60-7b46-4f1e-9c11-1a2b3c4d5e6f"
        val fp1 = service.computeFingerprint(outboundPayload(requestId, anchorRecordId = 11L))
        val fp2 = service.computeFingerprint(outboundPayload(requestId, anchorRecordId = 12L))
        assertEquals(service.conversationRequestShortKey(requestId), fp1.shortKey, "同一 requestId 短键固定")
        assertEquals(fp1.shortKey, fp2.shortKey)
        assertTrue(fp1.fullHex != fp2.fullHex, "锚点不同进入完整 hash")
        val fp3 = service.computeFingerprint(outboundPayload(requestId, anchorRecordId = 11L))
        assertEquals(fp1.fullHex, fp3.fullHex, "同锚点/同正文/同 requestId → 同 fullHex")
        assertTrue(fp1.messageId.startsWith("<manual-rich-"))
        assertEquals(64, fp1.fullHex.length)
    }

    @Test
    fun `findCompletedByRequestId returns completed reply only when attempt SENT and record exists`() {
        val requestId = "b5c98f60-7b46-4f1e-9c11-1a2b3c4d5e6f"
        val fp = service.computeFingerprint(outboundPayload(requestId))
        val attempt = createAttempt(MailSendAttemptStatus.SENT, fp)
        Mockito.`when`(attemptRepository.findByOrcidIdAndMailType(payload.orcidId, fp.shortKey))
            .thenReturn(attempt)
        val completedRecord = MailRecord(
            expertContactId = 1L, direction = "OUTBOUND", mailType = "MANUAL_RICH_REPLY",
            senderAccountCode = "sender-1", messageId = "<manual-rich-x@weibo.com>",
            inReplyTo = "anchor-mid", subject = "Re: Test", body = "sent",
            matchedQaRuleId = null,
            sendStatus = "SENT", receivedAt = null, sentAt = LocalDateTime.now()
        )
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(completedRecord)

        val result = service.findCompletedByRequestId(payload.orcidId, requestId)
        org.junit.jupiter.api.Assertions.assertNotNull(result)
        assertEquals(1L, result!!.attemptId)
        assertEquals("sender-1", result.attemptAccountCode)
        assertEquals("anchor-mid", result.mailRecord.inReplyTo)
        // 非 SENT 状态一律返回空（交给 claim/fail-closed 逻辑），即使结果行存在
        Mockito.`when`(attemptRepository.findByOrcidIdAndMailType(payload.orcidId, fp.shortKey))
            .thenReturn(createAttempt(MailSendAttemptStatus.DELIVERY_UNKNOWN, fp))
        assertTrue(service.findCompletedByRequestId(payload.orcidId, requestId) == null)
        // attempt 存在但结果行缺失 → 空
        Mockito.`when`(attemptRepository.findByOrcidIdAndMailType(payload.orcidId, fp.shortKey))
            .thenReturn(attempt)
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        assertTrue(service.findCompletedByRequestId(payload.orcidId, requestId) == null)
        // 无 attempt → 空
        Mockito.`when`(attemptRepository.findByOrcidIdAndMailType(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(null)
        assertTrue(service.findCompletedByRequestId(payload.orcidId, "00000000-0000-4000-8000-000000000001") == null)
    }

    @Test
    fun `finalizeSuccess persists conversation reply with anchor inReplyTo and no inbound source`() {
        val requestId = "b5c98f60-7b46-4f1e-9c11-1a2b3c4d5e6f"
        val outPayload = payload.copy(
            inboundProcessingId = null,
            inReplyTo = "anchor-mid",
            canonicalQaRuleIds = emptyList(),
            sourceAnchor = "MAIL_RECORD:11",
            idempotencyRequestId = requestId
        )
        val attempt = createAttempt(MailSendAttemptStatus.DELIVERY_IN_PROGRESS, service.computeFingerprint(outPayload))
        Mockito.`when`(attemptRepository.findById(1L)).thenReturn(Optional.of(attempt))
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        var saved: MailRecord? = null
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java)))
            .thenAnswer { invocation ->
                saved = invocation.getArgument<MailRecord>(0)
                saved!!.copy(id = 500L)
            }
        val mailRecordId = service.finalizeSuccess(outPayload, 1L, "<manual-rich-abc@weibo.com>")
        assertEquals(500L, mailRecordId)
        val record = requireNotNull(saved)
        assertEquals("OUTBOUND", record.direction)
        assertEquals("MANUAL_RICH_REPLY", record.mailType)
        assertEquals("sender-1", record.senderAccountCode)
        assertEquals("<manual-rich-abc@weibo.com>", record.messageId)
        assertEquals("anchor-mid", record.inReplyTo)
        assertEquals("SENT", record.sendStatus)
        assertTrue(record.sourceInboundId == null)
        assertEquals(com.weibo.talentintroduction.mail.domain.TriggeredBy.OPERATOR, record.triggeredBy)
        assertEquals(1L, record.expertContactId)
    }

    @Test
    fun `recordConversationSendAudit writes expert contact target with anchor before map`() {
        service.recordConversationSendAudit(
            contactId = 1L,
            anchorMailRecordId = 77L,
            mailRecordId = 500L,
            delivered = delivered(),
            sendSubject = "Re: Intro",
            bodyPreviewText = "Hello",
            operatorName = "op",
            note = "conversation send"
        )
        val invocation = Mockito.mockingDetails(operatorActionLogService).invocations
            .single { it.method.name == "record" }
        assertEquals("EXPERT_CONTACT", invocation.arguments[0])
        assertEquals(1L, invocation.arguments[1])
        assertEquals(com.weibo.talentintroduction.audit.domain.OperatorActionType.SEND_MANUAL_RICH_REPLY, invocation.arguments[2])
        assertEquals(1L, invocation.arguments[3])
        assertTrue(invocation.arguments[4] == null, "inbound_processing_id 恒 null")
        val before = invocation.arguments[5] as Map<*, *>
        assertEquals(77L, before["anchorMailRecordId"])
        val after = invocation.arguments[6] as Map<*, *>
        assertEquals(500L, after["mailRecordId"])
        assertEquals("SENT", after["sendStatus"])
        assertEquals("Re: Intro", after["subject"])
        assertEquals("Hello", after["bodyPreviewText"])
        assertFalse(after.containsKey("canonicalFactIds"))
    }
}

