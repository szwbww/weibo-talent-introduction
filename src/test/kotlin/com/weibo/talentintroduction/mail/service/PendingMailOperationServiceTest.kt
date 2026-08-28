package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertIndexLevelOperationService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.llm.service.AiReplyContext
import com.weibo.talentintroduction.llm.service.AiReplyContextService
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiReplyHighRiskClaimValidator
import com.weibo.talentintroduction.llm.service.QaFactSelectionService
import com.weibo.talentintroduction.llm.service.ResolvedQaRules
import com.weibo.talentintroduction.llm.service.ResolvedTrustReplySource
import com.weibo.talentintroduction.llm.service.TrustReplyAssembleRequest
import com.weibo.talentintroduction.llm.service.TrustReplyAssembleResponse
import com.weibo.talentintroduction.llm.service.TrustReplyItemGenerationKind
import com.weibo.talentintroduction.llm.service.TrustReplyItemHandling
import com.weibo.talentintroduction.llm.service.TrustReplyItemVersion
import com.weibo.talentintroduction.llm.service.TrustReplyLockedItemRequest
import com.weibo.talentintroduction.llm.service.TrustReplySourceRef
import com.weibo.talentintroduction.llm.service.TrustReplySourceType
import com.weibo.talentintroduction.llm.service.TrustReplyWorkbenchService
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerArchiveStatus
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexArchiveResult
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexService
import com.weibo.talentintroduction.llm.service.VerifiedTrustReplyAssembly
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Instant
import java.time.LocalDateTime
import java.util.Optional

/**
 * c6 (T-6.5)：线上侧「正文被编辑时仍归档」——运营改过正文（templateTextBody 与
 * assembly 产物逐字不等）的样本照常归档（不再 failedArchive）；editedByOperator
 * 标记由 service 写入（见 UnsupportedAnswerIndexServiceTest 的文档级断言）。
 */
class PendingMailOperationServiceTest {
    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val expertOperatorStatusService = Mockito.mock(ExpertOperatorStatusService::class.java)
    private val expertIndexLevelOperationService = Mockito.mock(ExpertIndexLevelOperationService::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val mailDeliveryService = Mockito.mock(MailDeliveryService::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val mailRecordQaRuleRepository = Mockito.mock(MailRecordQaRuleRepository::class.java)
    private val operatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val qaCategoryRepository = Mockito.mock(QaCategoryRepository::class.java)
    private val qaFactSelectionService = Mockito.mock(QaFactSelectionService::class.java)
    private val aiReplyDraftService = Mockito.mock(AiReplyDraftService::class.java)
    private val aiReplyContextService = Mockito.mock(AiReplyContextService::class.java)
    private val expertSearchService = Mockito.mock(ExpertSearchService::class.java)
    private val renderTemplateService = MailComposeTemplateService(
        Mockito.mock(com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository::class.java),
        Mockito.mock(com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository::class.java),
        qaRuleRepository,
        Mockito.mock(com.weibo.talentintroduction.reply.repository.ReplySnippetRepository::class.java),
        ObjectMapper(),
        Mockito.mock(MailVariableService::class.java),
        expertContactRepository,
        mailSenderAccountService,
        ContentVariantService(
            Mockito.mock(com.weibo.talentintroduction.variant.repository.ContentVariantRepository::class.java),
            MailPlaceholderService()
        )
    )
    private val mailVariableService = MailVariableService(expertSearchService, renderTemplateService)
    private val manualReplySendAttemptService = Mockito.mock(ManualReplySendAttemptService::class.java)
    private val trustReplyWorkbenchService = Mockito.mock(TrustReplyWorkbenchService::class.java)
    private val unsupportedAnswerIndexService = Mockito.mock(UnsupportedAnswerIndexService::class.java)
    private val emailSuppressionService = Mockito.mock(EmailSuppressionService::class.java)
    private val service = PendingMailOperationService(
        inboundMailProcessingRepository,
        expertContactRepository,
        expertOperatorStatusService,
        expertIndexLevelOperationService,
        mailSenderAccountService,
        mailDeliveryService,
        mailRecordRepository,
        mailRecordQaRuleRepository,
        operatorActionLogService,
        qaRuleRepository,
        qaCategoryRepository,
        qaFactSelectionService,
        aiReplyDraftService,
        aiReplyContextService,
        AiReplyHighRiskClaimValidator(qaRuleRepository),
        MailBodyCleaner(),
        MailContentService(),
        mailVariableService,
        manualReplySendAttemptService,
        trustReplyWorkbenchService,
        unsupportedAnswerIndexService,
        emailSuppressionService
    )

    private val contact = ExpertContact(
        id = 1,
        campaignId = 1,
        orcidId = "orcid-1",
        expertEmail = "expert@test.com",
        expertName = "Expert",
        currentStatus = "INTRO_SENT",
        operatorStatus = "CONTACTED",
        currentIndexLevel = "CANDIDATE"
    )

    @BeforeEach
    fun setUp() {
        Mockito.`when`(inboundMailProcessingRepository.findById(100L)).thenReturn(Optional.of(inbound()))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
        Mockito.`when`(aiReplyContextService.build(contact, emptyList(), "Can I work remotely?", ""))
            .thenReturn(
                AiReplyContext(
                    profileText = "",
                    mailHistory = "",
                    contextWarnings = emptyList(),
                    researchProfileSufficient = true
                )
            )
        Mockito.`when`(qaCategoryRepository.findAll()).thenReturn(emptyList())
        Mockito.`when`(expertSearchService.findByOrcidId(contact.orcidId, ExpertIndexLevel.CANDIDATE))
            .thenReturn(
                ExpertProfile(
                    orcidId = contact.orcidId,
                    email = contact.expertEmail,
                    givenNames = null,
                    familyNames = "Expert",
                    country = null,
                    keyword = null,
                    employment = null
                )
            )
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", null, true))
            .thenReturn(
                ResolvedQaRules(
                    sendQaRuleIds = emptyList(),
                    promptRuleIds = emptyList(),
                    requestFacts = emptyList()
                )
            )
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender-1")).thenReturn(senderAccount())
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-abc@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.CLAIMED
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload())))
            .thenReturn(claim)
        Mockito.`when`(
            mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMail()))
        ).thenReturn(DeliveredMail(messageId = "<manual-rich-abc@weibo.com>", status = "SENT"))
        Mockito.`when`(
            manualReplySendAttemptService.finalizeSuccess(anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-abc@weibo.com>"))
        ).thenReturn(500L)
    }

    // T-6.5: 正文被运营编辑（templateTextBody 与 assembly 产物逐字不等）时仍照常归档，
    // 归档状态为 SAVED 而非 FAILED（「去掉正文一字未改才归档」）。
    @Test
    fun `sendManualRichReply archives edited body instead of failing the archive`() {
        val assembly = liveAssembly()
        val eligible = operatorDirectedVersion()
        val assembled = assembledResponse(eligible)
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))
        Mockito.`when`(trustReplyWorkbenchService.resolveSource(assembly.source)).thenReturn(liveResolvedSource())
        Mockito.`when`(
            unsupportedAnswerIndexService.archiveLiveCanonicalVersions(
                Mockito.any(ResolvedTrustReplySource::class.java) ?: liveResolvedSource(),
                Mockito.anyList<TrustReplyItemVersion>() ?: emptyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any(Instant::class.java) ?: Instant.EPOCH
            )
        ).thenReturn(UnsupportedAnswerIndexArchiveResult(UnsupportedAnswerArchiveStatus.SAVED, 1, 0))

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>${assembled.renderedDraftText}</p>",
            textBody = assembled.renderedDraftText,
            operatorName = " operator-a ",
            // 运营改过正文：与 assembly 的 rawDraftText 逐字不等。
            templateTextBody = "Operator-edited final body, no longer verbatim.",
            trustReplyAssembly = assembly
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals(UnsupportedAnswerArchiveStatus.SAVED, result.unsupportedAnswerArchiveStatus)
        assertEquals(1, result.unsupportedAnswerArchivedCount)
        Mockito.verify(unsupportedAnswerIndexService).archiveLiveCanonicalVersions(
            Mockito.any(ResolvedTrustReplySource::class.java) ?: liveResolvedSource(),
            eqValue(listOf(eligible)),
            eqValue("500"),
            eqValue("operator-a"),
            Mockito.any(Instant::class.java) ?: Instant.EPOCH
        )
    }

    // 回归：未编辑（逐字一致）的正文仍照常归档。
    @Test
    fun `sendManualRichReply archives verbatim body as before`() {
        val assembly = liveAssembly()
        val eligible = operatorDirectedVersion()
        val assembled = assembledResponse(eligible)
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))
        Mockito.`when`(trustReplyWorkbenchService.resolveSource(assembly.source)).thenReturn(liveResolvedSource())
        Mockito.`when`(
            unsupportedAnswerIndexService.archiveLiveCanonicalVersions(
                Mockito.any(ResolvedTrustReplySource::class.java) ?: liveResolvedSource(),
                Mockito.anyList<TrustReplyItemVersion>() ?: emptyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any(Instant::class.java) ?: Instant.EPOCH
            )
        ).thenReturn(UnsupportedAnswerIndexArchiveResult(UnsupportedAnswerArchiveStatus.SAVED, 1, 0))

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>${assembled.renderedDraftText}</p>",
            textBody = assembled.renderedDraftText,
            operatorName = "op",
            templateTextBody = assembled.rawDraftText,
            trustReplyAssembly = assembly
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals(UnsupportedAnswerArchiveStatus.SAVED, result.unsupportedAnswerArchiveStatus)
        Mockito.verify(unsupportedAnswerIndexService).archiveLiveCanonicalVersions(
            Mockito.any(ResolvedTrustReplySource::class.java) ?: liveResolvedSource(),
            eqValue(listOf(eligible)),
            eqValue("500"),
            eqValue("op"),
            Mockito.any(Instant::class.java) ?: Instant.EPOCH
        )
    }

    private fun liveAssembly(): TrustReplyAssembleRequest {
        val source = TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, 100L)
        val version = operatorDirectedVersion()
        return TrustReplyAssembleRequest(
            source = source,
            expectedSourceVersion = "live-v1",
            expectedEvidenceSetVersion = "evidence-v1",
            lockedItems = listOf(
                TrustReplyLockedItemRequest(
                    requestKey = version.requestKey,
                    versionId = version.versionId,
                    handling = version.handling,
                    answerText = version.answerText,
                    claims = version.claims,
                    model = version.model,
                    generationKind = version.generationKind,
                    evidenceSetVersion = version.evidenceSetVersion,
                    sourceVersion = version.sourceVersion,
                    operatorInstructionHash = version.operatorInstructionHash,
                    operatorInstruction = version.operatorInstruction
                )
            )
        )
    }

    private fun operatorDirectedVersion() = TrustReplyItemVersion(
        versionId = "live-version-1",
        requestKey = "live-request-1",
        handling = TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT,
        answerText = "We will follow up next week.",
        claims = emptyList(),
        model = "DEEPSEEK_V4_FLASH",
        generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
        evidenceSetVersion = "evidence-v1",
        sourceVersion = "live-v1",
        operatorInstructionHash = "hash-1",
        requestIndex = 0,
        requestText = "When will you follow up?",
        operatorInstruction = "Please say we will follow up next week."
    )

    private fun verified(assembled: TrustReplyAssembleResponse): VerifiedTrustReplyAssembly =
        VerifiedTrustReplyAssembly(
            response = assembled,
            selection = ResolvedQaRules(
                sendQaRuleIds = assembled.canonicalFactIds,
                promptRuleIds = assembled.canonicalFactIds,
                requestFacts = emptyList()
            )
        )

    private fun assembledResponse(version: TrustReplyItemVersion): TrustReplyAssembleResponse {
        val body = "We will follow up next week."
        return TrustReplyAssembleResponse(
            source = TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, 100L),
            sourceVersion = "live-v1",
            evidenceSetVersion = "evidence-v1",
            rawDraftText = body,
            renderedDraftText = body,
            draftHash = "draft-hash",
            canonicalFactIds = emptyList(),
            itemVersions = listOf(version)
        )
    }

    private fun liveResolvedSource() = ResolvedTrustReplySource(
        source = TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, 100L),
        contact = contact,
        inboundText = "Can I work remotely?",
        subject = "Question",
        messageId = "in-1",
        senderAccountCode = "sender-1",
        profileText = "",
        mailHistory = "",
        contextWarnings = emptyList(),
        researchProfileSufficient = true,
        sourceVersion = "live-v1"
    )

    private fun inbound() = InboundMailProcessing(
        id = 100L,
        senderAccountCode = "sender-1",
        imapUid = 1L,
        messageId = "in-1",
        fromEmail = "expert@test.com",
        subject = "Question",
        body = "Can I work remotely?",
        cleanedBody = "Can I work remotely?",
        receivedAt = LocalDateTime.now(),
        processStatus = "MANUAL_REVIEW",
        processReason = "QA_NO_MATCH",
        expertContactId = 1L
    )

    private fun senderAccount() = MailSenderAccount(
        accountCode = "sender-1",
        senderEmail = "sender@test.com",
        senderName = "Sender",
        senderTitle = "Title",
        senderDisplayName = "Sender",
        teamName = "Team",
        countryName = "CN",
        smtpHost = "smtp.test.com",
        smtpPort = 465,
        smtpUsername = "u",
        smtpPassword = "p",
        imapHost = "imap.test.com",
        imapPort = 993,
        imapUsername = "u",
        imapPassword = "p",
        enabled = true
    )

    private fun sendPayload() = ManualReplySendAttemptService.SendPayload(
        orcidId = contact.orcidId,
        contactId = requireNotNull(contact.id),
        inboundProcessingId = 100L,
        accountCode = "sender-1",
        normalizedRecipient = contact.expertEmail,
        subject = "Re: Test",
        finalText = "Test",
        finalHtml = "<p>Test</p>",
        inReplyTo = "in-1",
        canonicalQaRuleIds = emptyList(),
        primaryRuleId = null
    )

    private fun composedMail() = ComposedMail(
        to = contact.expertEmail,
        subject = "Re: Test",
        body = "<p>Test</p>",
        html = true,
        text = "Test",
        messageId = "<manual-rich-abc@weibo.com>"
    )

    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue
}
