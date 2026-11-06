package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.MeetingCalendarInput
import com.weibo.talentintroduction.llm.service.AiReplyItemClaim
import com.weibo.talentintroduction.llm.service.ResolvedQaRules
import com.weibo.talentintroduction.llm.service.TrustReplyAssembleResponse
import com.weibo.talentintroduction.llm.service.TrustReplyItemGenerationKind
import com.weibo.talentintroduction.llm.service.TrustReplyItemHandling
import com.weibo.talentintroduction.llm.service.TrustReplyItemVersion
import com.weibo.talentintroduction.llm.service.TrustReplySourceRef
import com.weibo.talentintroduction.llm.service.TrustReplySourceType
import com.weibo.talentintroduction.llm.service.VerifiedTrustReplyAssembly
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.controller.ConversationManualRichReplyRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

/**
 * fast-p 02 T4（I-3/I-4）：冻结快照矩阵。
 *
 * 三种 restore、RAG/QA/assembly、无附件/ICS/10 附件、Unicode/链接/变量正文分别
 * round-trip；快照中绝不能出现账号凭据或绝对路径；未知版本与超限 fail closed。
 */
class DelayedManualReplySnapshotTest {

    private val unicodeSubject = "Re: 你好 — 合作意向 & <meeting>"
    private val unicodeText = "Dear 张教授,\n\n请问 \${senderName} 的链接 https://weibo.com/x?a=1&b=2 是否可用？"
    private val unicodeHtml = "<p>Dear 张教授</p><p>\${senderName}</p><p><a href=\"https://weibo.com/x?a=1&amp;b=2\">链接</a></p>"

    @Test
    fun `legacy snapshot round-trips unicode links and template variables`() {
        val snapshot = DelayedManualReplySnapshotFactory.fromPrepared(prepared())
        val json = DelayedManualReplySnapshotCodec.encode(snapshot)
        val decoded = DelayedManualReplySnapshotCodec.decode(json)

        assertEquals(snapshot, decoded)
        assertEquals(unicodeSubject, decoded.payload.subject)
        assertTrue(decoded.payload.finalText.contains("https://weibo.com/x?a=1&b=2"))
        assertTrue(decoded.payload.finalHtml.contains("\${senderName}"))
        val restore = decoded.restore as DelayedLegacyRestore
        assertEquals(100L, restore.inboundProcessingId)
        assertEquals(unicodeSubject, restore.subject)
        assertEquals(unicodeHtml, restore.html)
        assertEquals(unicodeText, restore.text)
    }

    @Test
    fun `draft restore round-trips the server-validated draft reference`() {
        val snapshot = DelayedManualReplySnapshotFactory.fromPrepared(prepared(draftSendRef = draftRef()))
        val decoded = DelayedManualReplySnapshotCodec.decode(DelayedManualReplySnapshotCodec.encode(snapshot))

        val restore = decoded.restore as DelayedDraftRestore
        assertEquals("user1", restore.owner)
        assertEquals(10L, restore.draftId)
        assertEquals(1L, restore.draftVersion)
        assertEquals(1L, restore.contactId)
        assertEquals("INBOUND", restore.targetKind)
        assertEquals(100L, restore.processingId)
        assertEquals(71L, restore.sendAttemptId)
        assertEquals(1L, restore.sendVersion)
        assertEquals(
            MailReplyDraftContextCodec.serialize(MailReplyDraftContext()),
            restore.contextJson
        )
    }

    @Test
    fun `review restore round-trips the approved frozen identity`() {
        val snapshot = DelayedManualReplySnapshotFactory.fromPrepared(prepared(reviewSendRef = reviewRef()))
        val decoded = DelayedManualReplySnapshotCodec.decode(DelayedManualReplySnapshotCodec.encode(snapshot))

        val restore = decoded.restore as DelayedReviewRestore
        assertEquals(55L, restore.reviewId)
        assertEquals(3L, restore.version)
        assertEquals("admin", restore.actor)
        assertEquals(9L, restore.confirmationEventId)
        assertEquals("INBOUND", restore.targetKind)
        assertEquals(100L, restore.targetId)
        assertEquals(REVIEW_REQUEST_ID, restore.sendRequestId)
        assertEquals(REVIEW_CONTEXT_TOKEN, restore.approvedContextToken)
    }

    @Test
    fun `rag evidence round-trips ordered fact codes without fabricating qa facts`() {
        val snapshot = DelayedManualReplySnapshotFactory.fromPrepared(
            prepared(
                evidence = ManualReplyEvidenceContext(
                    carriesQa = true,
                    ragMode = true,
                    ragFactCodes = listOf("KB-FUND-033", "KB-COMP-007", "KB-FUND-033"),
                    ragFingerprintAtSend = "corpus-fingerprint-1"
                )
            )
        )
        val decoded = DelayedManualReplySnapshotCodec.decode(DelayedManualReplySnapshotCodec.encode(snapshot))

        assertEquals(emptyList<Long>(), decoded.payload.canonicalQaRuleIds)
        assertNull(decoded.payload.primaryRuleId)
        assertEquals(
            listOf("KB-FUND-033", "KB-COMP-007", "KB-FUND-033"),
            decoded.evidence.ragFactCodes
        )
        assertEquals("corpus-fingerprint-1", decoded.evidence.ragCorpusFingerprint)
        assertEquals(emptyList<Long>(), decoded.evidence.canonicalFactIds)
        assertNull(decoded.evidence.assembly)
    }

    @Test
    fun `qa facts and verified assembly round-trip as evidence and legacy restore`() {
        val snapshot = DelayedManualReplySnapshotFactory.fromPrepared(
            prepared(
                evidence = ManualReplyEvidenceContext(
                    carriesQa = true,
                    canonicalFactIds = listOf(10L, 20L),
                    serverSuggestedFactIds = listOf(20L),
                    edited = true,
                    verifiedAssembly = verifiedAssembly()
                )
            )
        )
        val decoded = DelayedManualReplySnapshotCodec.decode(DelayedManualReplySnapshotCodec.encode(snapshot))

        assertEquals(listOf(10L, 20L), decoded.payload.canonicalQaRuleIds)
        assertEquals(10L, decoded.payload.primaryRuleId)
        assertEquals(true, decoded.evidence.carriesQa)
        assertEquals(true, decoded.evidence.edited)
        assertEquals(1, decoded.evidence.assembly?.itemVersions?.size)
        assertEquals("p1", decoded.evidence.assembly?.itemVersions?.first()?.requestKey)
        val legacy = decoded.restore as DelayedLegacyRestore
        assertEquals(listOf(10L, 20L), legacy.qaRuleIds)
        assertEquals(1, legacy.assembly?.itemVersions?.size)
    }

    @Test
    fun `no credentials or absolute paths are serialized`() {
        val json = DelayedManualReplySnapshotCodec.encode(
            DelayedManualReplySnapshotFactory.fromPrepared(prepared())
        )

        assertFalse(json.contains(SMTP_PASSWORD))
        assertFalse(json.contains(IMAP_PASSWORD))
        assertFalse(json.contains("smtpPassword"))
        assertFalse(json.contains("imapPassword"))
        assertFalse(json.contains("/Users/"))
        assertFalse(json.contains("/tmp/"))
        assertFalse(json.contains("smtpHost"))
    }

    @Test
    fun `calendar ics and meeting event round-trip together`() {
        val calendar = CalendarAttachmentSnapshot(
            schemaVersion = 1,
            filename = "meeting.ics",
            contentType = "text/calendar; charset=UTF-8",
            icsText = "BEGIN:VCALENDAR\r\nSUMMARY:会议\r\nEND:VCALENDAR\r\n",
            sha256 = "b".repeat(64),
            semanticSha256 = "c".repeat(64)
        )
        val meeting = MeetingCalendarInput(
            startUtc = Instant.parse("2026-11-05T10:00:00Z"),
            endUtc = Instant.parse("2026-11-05T10:30:00Z"),
            meetingLink = "https://zoom.us/j/123"
        )
        val decoded = DelayedManualReplySnapshotCodec.decode(
            DelayedManualReplySnapshotCodec.encode(
                DelayedManualReplySnapshotFactory.fromPrepared(
                    prepared(calendar = calendar, meeting = meeting,
                        originalMeeting = MeetingInput(zoneId = "Asia/Shanghai", startLocal = "2026-11-05T18:00",
                            endLocal = "2026-11-05T18:30", zoomUrl = "https://zoom.us/j/123",
                            generatedAt = "2026-11-04T10:00:00Z"))
                )
            )
        )

        assertEquals(calendar, decoded.payload.calendarAttachment)
        assertEquals(Instant.parse("2026-11-05T10:00:00Z").toEpochMilli(), decoded.payload.meetingEvent?.startUtcMs)
        assertEquals(Instant.parse("2026-11-05T10:30:00Z").toEpochMilli(), decoded.payload.meetingEvent?.endUtcMs)
        assertEquals("https://zoom.us/j/123", decoded.payload.meetingEvent?.meetingLink)
        assertEquals(calendar, (decoded.restore as DelayedLegacyRestore).calendarAttachment)
    }

    @Test
    fun `ten attachments keep the chosen order across round-trip`() {
        val attachments = (1..10).map { index ->
            OutboundAttachmentSnapshot(
                schemaVersion = 1,
                id = UUID.nameUUIDFromBytes("attachment-$index".toByteArray()).toString(),
                filename = "file-$index.txt",
                contentType = "text/plain",
                byteLength = index.toLong(),
                sha256 = index.toString().repeat(64).take(64)
            )
        }
        val decoded = DelayedManualReplySnapshotCodec.decode(
            DelayedManualReplySnapshotCodec.encode(
                DelayedManualReplySnapshotFactory.fromPrepared(prepared(attachments = attachments))
            )
        )

        assertEquals(attachments, decoded.payload.outboundAttachments)
        assertEquals(attachments, (decoded.restore as DelayedLegacyRestore).outboundAttachments)
    }

    @Test
    fun `unknown schema version fails closed`() {
        val json = DelayedManualReplySnapshotCodec.encode(
            DelayedManualReplySnapshotFactory.fromPrepared(prepared())
        )
        val tampered = json.replace("\"schemaVersion\":1", "\"schemaVersion\":2")

        assertThrows(DelayedManualReplySnapshotException::class.java) {
            DelayedManualReplySnapshotCodec.decode(tampered)
        }
    }

    @Test
    fun `snapshot above the 4 MiB limit fails closed`() {
        val oversized = "x".repeat(4 * 1024 * 1024 + 16)
        val snapshot = DelayedManualReplySnapshotFactory.fromPrepared(
            prepared(text = oversized, html = "<p>$oversized</p>")
        )

        assertThrows(DelayedManualReplySnapshotException::class.java) {
            DelayedManualReplySnapshotCodec.encode(snapshot)
        }
    }

    @Test
    fun `legacy conversation without original input fails closed`() {
        val conversation = prepared(
            source = ManualRichSendSource(
                contact = contact(),
                contactId = 1L,
                inboundProcessingId = null,
                inboundRecord = null,
                account = account(),
                accountCode = "sender-1",
                persistInReplyTo = "<anchor@weibo.com>",
                smtpInReplyTo = "<anchor@weibo.com>",
                smtpReferences = "<anchor@weibo.com>",
                anchorMailRecordId = 900L,
                requestId = "1b4e28ba-2fa1-11d2-883f-0016d3cca427"
            ),
            payload = sendPayload(
                inboundProcessingId = null,
                sourceAnchor = "MAIL_RECORD:900",
                idempotencyRequestId = "1b4e28ba-2fa1-11d2-883f-0016d3cca427"
            )
        )

        assertThrows(DelayedManualReplySnapshotException::class.java) {
            DelayedManualReplySnapshotFactory.fromPrepared(conversation)
        }
    }

    private fun conversationPrepared(scope: String? = null, anchor: Long? = null): PreparedManualReply {
        val requestId = "1b4e28ba-2fa1-11d2-883f-0016d3cca427"
        val base = prepared(payload = sendPayload(null, "MAIL_RECORD:900", requestId))
        return base.copy(
            source = inboundSource().copy(inboundProcessingId = null, inboundRecord = null,
                anchorMailRecordId = 900, requestId = requestId),
            originalInput = null,
            originalConversationInput = ConversationManualRichReplyRequest(
                requestId, scope, anchor, " $unicodeSubject ", unicodeHtml, unicodeText)
        )
    }

    @Test fun `optional conversation source roundtrips separately from frozen authority and fingerprint`() {
        val service = org.mockito.Mockito.mock(ManualReplySendAttemptService::class.java, org.mockito.Mockito.CALLS_REAL_METHODS)
        val original = conversationPrepared()
        val fingerprint = service.computeFingerprint(original.payload)
        for (scope in listOf(null, "", " ", "sender-1")) for (anchor in listOf(null, 900L)) {
            val prepared = conversationPrepared(scope, anchor)
            val snapshot = DelayedManualReplySnapshotFactory.fromPrepared(prepared)
            val decoded = DelayedManualReplySnapshotCodec.decode(DelayedManualReplySnapshotCodec.encode(snapshot))
            val restore = decoded.restore as DelayedLegacyRestore
            assertEquals(prepared.originalConversationInput, restore.originalConversationInput)
            assertEquals(900L, decoded.source.anchorMailRecordId)
            assertEquals(original.payload.sourceAnchor, decoded.payload.sourceAnchor)
            assertEquals(fingerprint.fullHex, service.computeFingerprint(prepared.payload).fullHex)
            assertEquals(fingerprint.shortKey, service.computeFingerprint(prepared.payload).shortKey)
            assertNull(decoded.payload.inboundProcessingId)
            assertNull(restore.originalInput)
        }
    }

    @Test fun `conversation restore rejects mixed mismatched and coerced source input`() {
        val original = conversationPrepared()
        val snapshot = DelayedManualReplySnapshotFactory.fromPrepared(original)
        val restore = snapshot.restore as DelayedLegacyRestore
        val input = requireNotNull(restore.originalConversationInput)
        val invalid = listOf(
            restore.copy(originalInput = prepared().originalInput),
            restore.copy(inboundProcessingId = 100),
            restore.copy(originalConversationInput = null),
            restore.copy(originalConversationInput = input.copy(accountScope = "foreign")),
            restore.copy(originalConversationInput = input.copy(anchorMailRecordId = 901)),
            restore.copy(originalConversationInput = input.copy(anchorMailRecordId = 0)),
            restore.copy(originalConversationInput = input.copy(requestId = UUID.randomUUID().toString())),
            restore.copy(originalConversationInput = input.copy(draftRef = MailReplyDraftRef(1, 1))),
            restore.copy(originalConversationInput = input.copy(htmlBody = "<b>changed</b>")),
            restore.copy(originalConversationInput = input.copy(textBody = "changed")),
            restore.copy(originalConversationInput = input.copy(attachmentIds = listOf(UUID.randomUUID().toString())))
        )
        invalid.forEach { bad -> assertThrows(DelayedManualReplySnapshotException::class.java) {
            DelayedManualReplySnapshotCodec.encode(snapshot.copy(restore = bad))
        } }
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        for (value in listOf("1.5", "9223372036854775808", "\"900\"", "true")) {
            val tree = mapper.readTree(DelayedManualReplySnapshotCodec.encode(snapshot))
            (tree["restore"]["originalConversationInput"] as com.fasterxml.jackson.databind.node.ObjectNode)
                .set<com.fasterxml.jackson.databind.JsonNode>("anchorMailRecordId", mapper.readTree(value))
            assertThrows(DelayedManualReplySnapshotException::class.java) { DelayedManualReplySnapshotCodec.decode(tree.toString()) }
        }
        val inbound = DelayedManualReplySnapshotFactory.fromPrepared(prepared())
        assertThrows(DelayedManualReplySnapshotException::class.java) {
            DelayedManualReplySnapshotCodec.encode(inbound.copy(restore =
                (inbound.restore as DelayedLegacyRestore).copy(originalConversationInput = input)))
        }
    }

    @Test fun `historical inbound missing restore request remains delivery compatible`() {
        val snapshot = DelayedManualReplySnapshotFactory.fromPrepared(prepared())
        val tree = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(DelayedManualReplySnapshotCodec.encode(snapshot))
        (tree["restore"] as com.fasterxml.jackson.databind.node.ObjectNode).remove(listOf("originalInput", "originalConversationInput"))
        val decoded = DelayedManualReplySnapshotCodec.decode(tree.toString())
        assertEquals(snapshot.payload, decoded.payload)
        assertNull((decoded.restore as DelayedLegacyRestore).originalInput)
    }

    // ------------------------------------------------------------------
    // builders
    // ------------------------------------------------------------------

    private fun prepared(
        source: ManualRichSendSource = inboundSource(),
        payload: ManualReplySendAttemptService.SendPayload = sendPayload(),
        draftSendRef: ValidatedDraftSendRef? = null,
        reviewSendRef: MailReviewSendRef? = null,
        evidence: ManualReplyEvidenceContext = ManualReplyEvidenceContext(),
        calendar: CalendarAttachmentSnapshot? = null,
        meeting: MeetingCalendarInput? = null,
        attachments: List<OutboundAttachmentSnapshot> = emptyList(),
        text: String = unicodeText,
        html: String = unicodeHtml,
        originalMeeting: MeetingInput? = null
    ): PreparedManualReply {
        val finalPayload = payload.copy(
            subject = unicodeSubject,
            finalText = text,
            finalHtml = html,
            // QA 事实与 RAG 证据互斥：RAG 路径 canonical ids/primaryRuleId 恒空。
            canonicalQaRuleIds = if (evidence.ragMode) emptyList() else evidence.canonicalFactIds,
            primaryRuleId = if (evidence.ragMode) null else evidence.canonicalFactIds.firstOrNull(),
            calendarAttachment = calendar,
            meetingEvent = meeting,
            outboundAttachments = attachments
        ).also {
            it.draftSendRef = draftSendRef
            it.reviewSendRef = reviewSendRef
        }
        return PreparedManualReply(
            source = source,
            payload = finalPayload,
            renderedSubject = unicodeSubject,
            finalTextBody = text,
            finalHtmlBody = html,
            calendarSnapshot = calendar,
            attachmentFileSet = OutboundAttachmentFileSet(emptyList(), attachments),
            evidence = evidence,
            operatorName = "op",
            authenticatedUsername = "op",
            requiresStrong = false,
            safetyWarningConfirmed = true,
            strongConfirmationText = null,
            bodyPreviewText = text.take(500),
            auditNote = "Manual rich reply sent for inbound processing 100",
            rawSubject = unicodeSubject,
            rawHtmlBody = html,
            rawTextBody = text,
            draftSendRef = draftSendRef,
            reviewSendRef = reviewSendRef,
            originalInput = PendingManualRichReplyRequest(
                senderAccountCode = "sender-1", subject = unicodeSubject, htmlBody = html, textBody = text,
                operatorName = "op", meeting = originalMeeting, previewAttachmentSha256 = calendar?.sha256,
                attachmentIds = attachments.map { it.id }
            )
        )
    }

    private fun sendPayload(
        inboundProcessingId: Long? = 100L,
        sourceAnchor: String? = null,
        idempotencyRequestId: String? = null
    ) = ManualReplySendAttemptService.SendPayload(
        orcidId = "orcid-1",
        contactId = 1L,
        inboundProcessingId = inboundProcessingId,
        accountCode = "sender-1",
        normalizedRecipient = "expert@test.com",
        subject = unicodeSubject,
        finalText = unicodeText,
        finalHtml = unicodeHtml,
        inReplyTo = "<inbound@weibo.com>",
        canonicalQaRuleIds = emptyList(),
        primaryRuleId = null,
        sourceAnchor = sourceAnchor,
        idempotencyRequestId = idempotencyRequestId
    )

    private fun inboundSource() = ManualRichSendSource(
        contact = contact(),
        contactId = 1L,
        inboundProcessingId = 100L,
        inboundRecord = inbound(),
        account = account(),
        accountCode = "sender-1",
        persistInReplyTo = "<inbound@weibo.com>",
        smtpInReplyTo = null,
        smtpReferences = null,
        anchorMailRecordId = null,
        requestId = null
    )

    private fun contact() = ExpertContact(
        id = 1L,
        campaignId = 1L,
        orcidId = "orcid-1",
        expertEmail = "expert@test.com",
        expertName = "Expert",
        currentStatus = "INTRO_SENT",
        operatorStatus = "CONTACTED",
        currentIndexLevel = "CANDIDATE"
    )

    private fun account() = MailSenderAccount(
        id = 1L,
        accountCode = "sender-1",
        senderEmail = "sender@weibo.com",
        senderName = "Sender",
        senderTitle = "Title",
        senderDisplayName = "Sender",
        teamName = "Team",
        countryName = "CN",
        smtpHost = "smtp.weibo.com",
        smtpPort = 465,
        smtpUsername = "smtp-user",
        smtpPassword = SMTP_PASSWORD,
        imapHost = "imap.weibo.com",
        imapPort = 993,
        imapUsername = "imap-user",
        imapPassword = IMAP_PASSWORD
    )

    private fun inbound() = InboundMailProcessing(
        id = 100L,
        senderAccountCode = "sender-1",
        imapUid = 1L,
        messageId = "<inbound@weibo.com>",
        fromEmail = "expert@test.com",
        subject = "Re: Test",
        body = "Can I work remotely?",
        cleanedBody = "Can I work remotely?",
        receivedAt = LocalDateTime.of(2026, 11, 4, 10, 0),
        processStatus = "MANUAL_REVIEW",
        processReason = "MANUAL",
        expertContactId = 1L
    )

    private fun draftRef() = ValidatedDraftSendRef(
        owner = "user1",
        id = 10L,
        version = 1L,
        target = MailReplyDraftTarget(1L, MailReplyDraftKind.INBOUND, 100L, "sender-1"),
        content = MailReplyDraftContent(
            subject = unicodeSubject,
            html = unicodeHtml,
            text = unicodeText
        ),
        sendAttemptId = 71L,
        sendVersion = 1L
    )

    private fun reviewRef() = MailReviewSendRef(
        draft = MailReviewDraftRow(
            id = 55L,
            ownerUsername = "user1",
            contactId = 1L,
            clientRequestId = "c-1",
            targetKind = "INBOUND",
            targetId = 100L,
            accountCode = "sender-1",
            version = 3L,
            state = MailReviewDraftState.APPROVED,
            subject = unicodeSubject,
            html = unicodeHtml,
            text = unicodeText,
            approvedContextToken = REVIEW_CONTEXT_TOKEN,
            sendRequestId = REVIEW_REQUEST_ID,
            createdAt = LocalDateTime.of(2026, 11, 4, 10, 0),
            updatedAt = LocalDateTime.of(2026, 11, 4, 10, 0)
        ),
        actor = "admin",
        confirmationEventId = 9L
    )

    private fun verifiedAssembly(): VerifiedTrustReplyAssembly {
        val version = TrustReplyItemVersion(
            versionId = "v1",
            requestKey = "p1",
            handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
            answerText = "Answer",
            claims = listOf(AiReplyItemClaim("intent", "Answer", listOf(10L))),
            model = "deepseek",
            generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
            evidenceSetVersion = "e1",
            sourceVersion = "s1",
            requestIndex = 0,
            requestText = "Can I work remotely?"
        )
        val response = TrustReplyAssembleResponse(
            source = TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, 100L),
            sourceVersion = "s1",
            evidenceSetVersion = "e1",
            rawDraftText = "Answer",
            renderedDraftText = "Answer",
            draftHash = "d1",
            canonicalFactIds = listOf(10L, 20L),
            itemVersions = listOf(version),
            finalParagraphByRequestKey = mapOf("p1" to "Answer")
        )
        return VerifiedTrustReplyAssembly(
            response = response,
            selection = ResolvedQaRules(
                sendQaRuleIds = listOf(10L, 20L),
                promptRuleIds = listOf(10L),
                requestFacts = emptyList()
            )
        )
    }

    private companion object {
        const val SMTP_PASSWORD = "SMTP-PASSWORD-MUST-NOT-LEAK"
        const val IMAP_PASSWORD = "IMAP-PASSWORD-MUST-NOT-LEAK"
        const val REVIEW_REQUEST_ID = "1b4e28ba-2fa1-11d2-883f-0016d3cca427"
        const val REVIEW_CONTEXT_TOKEN =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
