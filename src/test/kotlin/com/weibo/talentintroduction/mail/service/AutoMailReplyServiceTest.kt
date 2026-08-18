package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.ExpertContactStatusHistory
import com.weibo.talentintroduction.campaign.domain.OperatorStatus
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactStatusHistoryRepository
import com.weibo.talentintroduction.campaign.service.ConversationStateService
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.handoff.domain.ManualHandoff
import com.weibo.talentintroduction.mail.domain.AutoReplyConfidenceLog
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailRecordQaRule
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.handoff.repository.ManualHandoffRepository
import com.weibo.talentintroduction.mail.repository.AutoReplyConfidenceLogRepository
import com.weibo.talentintroduction.mail.repository.InboundIntentRepository
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import com.weibo.talentintroduction.llm.service.AiReplyDraftReadiness
import com.weibo.talentintroduction.llm.service.AiReplyGenerationState
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.template.service.ComposeTemplateRenderResult
import com.weibo.talentintroduction.variant.repository.ContentVariantRepository
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.LocalDateTime

class AutoMailReplyServiceTest {
    private val accountService = Mockito.mock(MailSenderAccountService::class.java)
    private val receiveService = Mockito.mock(MailReceiveService::class.java)
    private val cursorService = Mockito.mock(MailInboxCursorService::class.java)
    private val deliveryService = Mockito.mock(MailDeliveryService::class.java)
    private val contactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val mailRecordQaRuleRepository = Mockito.mock(MailRecordQaRuleRepository::class.java)
    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val inboundIntentRepository = Mockito.mock(InboundIntentRepository::class.java)
    private val autoReplyConfidenceLogRepository = Mockito.mock(AutoReplyConfidenceLogRepository::class.java)
    private val manualHandoffRepository = Mockito.mock(ManualHandoffRepository::class.java)
    private val mailAttachmentService = Mockito.mock(MailAttachmentService::class.java)
    private val mailComposeTemplateService = Mockito.mock(MailComposeTemplateService::class.java)
    private val groundedAutoReplyDecisionService = Mockito.mock(GroundedAutoReplyDecisionService::class.java)
    private val statusHistoryRepository = Mockito.mock(ExpertContactStatusHistoryRepository::class.java)
    private val conversationStateService = ConversationStateService(contactRepository, statusHistoryRepository)
    private val meetingScheduleService = Mockito.mock(com.weibo.talentintroduction.campaign.service.MeetingScheduleService::class.java)
    private val expertEmailAliasService = Mockito.mock(ExpertEmailAliasService::class.java)
    private val expertIndexWriterService = Mockito.mock(com.weibo.talentintroduction.expert.service.ExpertIndexWriterService::class.java)
    private val automaticApplicationPromotionService = Mockito.mock(AutomaticApplicationPromotionService::class.java)
    private val expertOperatorStatusService = Mockito.mock(ExpertOperatorStatusService::class.java)
    private val bounceDetector = BounceDetector()
    private val selfCheckProbeDetector = SelfCheckProbeDetector()
    private val bounceCollectionService = Mockito.mock(
        BounceCollectionService::class.java,
        Mockito.withSettings().defaultAnswer { invocation ->
            if (invocation.method.name == "collectBounces") {
                BounceCollectionResult(collected = 0, skippedDuplicate = 0)
            } else {
                Mockito.RETURNS_DEFAULTS.answer(invocation)
            }
        }
    )
    private val bounceRateMonitorService = Mockito.mock(
        BounceRateMonitorService::class.java,
        Mockito.withSettings().defaultAnswer { invocation ->
            if (invocation.method.name == "checkAndPause") {
                -1.0
            } else {
                Mockito.RETURNS_DEFAULTS.answer(invocation)
            }
        }
    )
    private val emailSuppressionService = Mockito.mock(EmailSuppressionService::class.java)
    private val dmarcReportDetector = DmarcReportDetector()
    private val dmarcReportIngestService = Mockito.mock(DmarcReportIngestService::class.java)
    private val mailContentService = MailContentService()
    private val autoReplySettingService = Mockito.mock(AutoReplySettingService::class.java)
    private val inboundMailTagService = Mockito.mock(InboundMailTagService::class.java)
    private val expertSearchService = Mockito.mock(ExpertSearchService::class.java)
    private val renderTemplateService = MailComposeTemplateService(
        Mockito.mock(MailComposeTemplateRepository::class.java),
        Mockito.mock(MailComposeTemplateBlockRepository::class.java),
        Mockito.mock(QaRuleRepository::class.java),
        Mockito.mock(ReplySnippetRepository::class.java),
        ObjectMapper(),
        Mockito.mock(MailVariableService::class.java),
        contactRepository,
        accountService,
        ContentVariantService(Mockito.mock(ContentVariantRepository::class.java), MailPlaceholderService())
    )
    private val mailVariableService = MailVariableService(expertSearchService, renderTemplateService)
    private val service = AutoMailReplyService(
        accountService,
        receiveService,
        deliveryService,
        contactRepository,
        mailRecordRepository,
        mailRecordQaRuleRepository,
        inboundMailProcessingRepository,
        inboundIntentRepository,
        manualHandoffRepository,
        mailAttachmentService,
        MailBodyCleaner(),
        InboundIntentClassifier(),
        mailComposeTemplateService,
        groundedAutoReplyDecisionService,
        conversationStateService,
        meetingScheduleService,
        expertEmailAliasService,
        expertIndexWriterService,
        automaticApplicationPromotionService,
        expertOperatorStatusService,
        bounceDetector,
        bounceCollectionService,
        bounceRateMonitorService,
        emailSuppressionService,
        selfCheckProbeDetector,
        dmarcReportDetector,
        dmarcReportIngestService,
        mailContentService,
        cursorService,
        autoReplySettingService,
        inboundMailTagService,
        mailVariableService,
        autoReplyConfidenceLogRepository
    )

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        Mockito.`when`(autoReplySettingService.isGlobalEnabled()).thenReturn(true)
        Mockito.`when`(accountService.getAutoReceiveAccount(Mockito.anyString())).thenAnswer { invocation ->
            val code = invocation.getArgument<String>(0)
            accountService.getEnabledAccount(code)
        }
        Mockito.`when`(inboundMailProcessingRepository.save(Mockito.any(InboundMailProcessing::class.java)))
            .thenAnswer { invocation ->
                val record = invocation.getArgument<InboundMailProcessing>(0)
                record.copy(id = record.id ?: 999L)
            }
        Mockito.`when`(mailAttachmentService.saveUnmatchedAttachments(Mockito.anyLong(), Mockito.anyList()))
            .thenReturn(emptyList())
        Mockito.`when`(cursorService.get(Mockito.anyString())).thenReturn(CursorState(null, 0L))
        Mockito.`when`(
            cursorService.resolveStart(
                anyValue(CursorState(null, 0L)),
                anyValue(0L)
            )
        ).thenAnswer { invocation ->
                val stored = invocation.getArgument<CursorState>(0)
                val currentUidValidity = invocation.getArgument<Long>(1)
                if (stored.uidValidity != null && stored.uidValidity != currentUidValidity) 0L else stored.lastUid
            }
    }

    private fun defaultPromotionStubs(contact: ExpertContact) {
        Mockito.`when`(automaticApplicationPromotionService.promoteByMaterialIfNeeded(
            anyValue(contact),
            anyValue(LocalDateTime.now()),
            anyValue(0L),
            anyValue(0)
        )).thenAnswer { it.getArgument<ExpertContact>(0) }

        Mockito.`when`(automaticApplicationPromotionService.promoteByReplyCountIfNeeded(
            anyValue(contact),
            anyValue(LocalDateTime.now()),
            anyValue(0L)
        )).thenAnswer { it.getArgument<ExpertContact>(0) }

        Mockito.`when`(expertOperatorStatusService.updateAutomatically(
            anyValue(contact),
            anyValue(OperatorStatus.REPLIED),
            anyValue("")
        )).thenAnswer { it.getArgument<ExpertContact>(0) }
    }

    @Test
    fun `receiveAndAutoReply ingests bounce before processSingle and still collects unseen bounces`() {
        val account = account("sender")
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        val bounceMail = reply(
            from = "mailer-daemon@example.com",
            subject = "Undelivered Mail Returned to Sender",
            body = "554 5.1.1 User unknown",
            imapUid = 102L
        )
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(
            inboundFetch(listOf(
                reply(from = "expert@example.com"),
                bounceMail
            ))
        )
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com")).thenReturn(null)
        Mockito.`when`(
            bounceCollectionService.ingest(
                anyValue(BounceSignal("SOFT", null, null, null, null)),
                eqValue("sender"),
                Mockito.nullable(String::class.java),
                Mockito.nullable(String::class.java),
                Mockito.nullable(String::class.java),
                anyValue(LocalDateTime.now())
            )
        ).thenReturn(BounceIngestResult.INGESTED)

        val inOrder = Mockito.inOrder(receiveService, bounceCollectionService, bounceRateMonitorService)

        service.receiveAndAutoReply("sender", 5)

        inOrder.verify(receiveService).fetchInboundSince(account, 0L, 5)
        inOrder.verify(bounceCollectionService).ingest(
            anyValue(BounceSignal("SOFT", null, null, null, null)),
            eqValue("sender"),
            Mockito.nullable(String::class.java),
            Mockito.nullable(String::class.java),
            Mockito.nullable(String::class.java),
            anyValue(LocalDateTime.now())
        )
        inOrder.verify(receiveService).markSeen(account, bounceMail.imapUid)
        inOrder.verify(bounceCollectionService).collectBounces(account)
        inOrder.verify(bounceRateMonitorService).checkAndPause("sender")
        Mockito.verify(expertEmailAliasService, Mockito.never())
            .findContactByEmailOrAlias("mailer-daemon@example.com")
    }

    @Test
    fun `receiveAndAutoReply discards self-check probe without persisting`() {
        val account = account("sender")
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        val probe = reply(
            from = account.senderEmail,
            subject = "[self-check] sender ${System.currentTimeMillis()}",
            body = "self-check probe",
            imapUid = 301L
        )
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(inboundFetch(listOf(probe)))

        service.receiveAndAutoReply("sender", 5)

        Mockito.verify(receiveService).markSeen(account, probe.imapUid)
        Mockito.verify(inboundMailProcessingRepository, Mockito.never())
            .save(Mockito.any(InboundMailProcessing::class.java))
        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any(MailRecord::class.java))
        Mockito.verify(bounceCollectionService, Mockito.never()).ingest(
            anyValue(BounceSignal("SOFT", null, null, null, null)),
            anyValue(""),
            Mockito.nullable(String::class.java),
            Mockito.nullable(String::class.java),
            Mockito.nullable(String::class.java),
            anyValue(LocalDateTime.now())
        )
    }

    @Test
    fun `receiveAndAutoReply collects bounces after business reply processing in order`() {
        val account = account("sender")
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(
            inboundFetch(listOf(reply(from = "expert@example.com")))
        )
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com")).thenReturn(null)

        val inOrder = Mockito.inOrder(receiveService, bounceCollectionService, bounceRateMonitorService)

        service.receiveAndAutoReply("sender", 5)

        inOrder.verify(receiveService).fetchInboundSince(account, 0L, 5)
        inOrder.verify(bounceCollectionService).collectBounces(account)
        inOrder.verify(bounceRateMonitorService).checkAndPause("sender")
    }

    @Test
    fun `dmarc aggregate report is ingested and marked seen without manual review`() {
        val account = account("sender")
        val dmarcMail = ReceivedMail(
            imapUid = 201L,
            from = "noreply-dmarc-support@google.com",
            subject = "Report domain: qftechtalent.com Submitter: google.com Report-ID: abc-123",
            body = "",
            messageId = "dmarc-1",
            inReplyTo = null,
            receivedAt = LocalDateTime.of(2026, 6, 26, 10, 0),
            attachments = listOf(
                ReceivedMailAttachment(
                    fileName = "google.com!qftechtalent.com!1609459200!1609545600.xml.gz",
                    contentType = "application/gzip",
                    content = ByteArray(0)
        ))
        )
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(inboundFetch(listOf(dmarcMail)))

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(0, result.manualReview)
        assertEquals(0, result.recorded)
        Mockito.verify(dmarcReportIngestService).ingest(dmarcMail.attachments)
        Mockito.verify(receiveService).markSeen(account, 201L)
        Mockito.verifyNoInteractions(expertEmailAliasService)
        Mockito.verify(inboundMailProcessingRepository, Mockito.never())
            .save(Mockito.any(InboundMailProcessing::class.java))
    }

    @Test
    fun `corrupted dmarc ingest failure still marks seen without manual review`() {
        val account = account("sender")
        val dmarcMail = ReceivedMail(
            imapUid = 301L,
            from = "noreply-dmarc-support@google.com",
            subject = "Report domain: qftechtalent.com Submitter: google.com Report-ID: bad",
            body = "",
            messageId = "dmarc-bad",
            inReplyTo = null,
            receivedAt = LocalDateTime.of(2026, 6, 26, 10, 0),
            attachments = listOf(
                ReceivedMailAttachment(
                    fileName = "bad.xml.gz",
                    contentType = "application/gzip",
                    content = "not-gzip".toByteArray()
                )
            )
        )
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(inboundFetch(listOf(dmarcMail)))
        Mockito.doThrow(RuntimeException("ingest failed"))
            .`when`(dmarcReportIngestService).ingest(dmarcMail.attachments)

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.fetched)
        assertEquals(0, result.manualReview)
        Mockito.verify(receiveService).markSeen(account, 301L)
        Mockito.verifyNoInteractions(expertEmailAliasService)
    }

    @Test
    fun `dmarc report does not block subsequent expert mail processing`() {
        val account = account("sender")
        val contact = ExpertContact(
            id = 11,
            campaignId = 1,
            orcidId = "ORCID-11",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            currentStatus = ConversationStatus.WAITING_REPLY.name
        )
        val dmarcMail = ReceivedMail(
            imapUid = 301L,
            from = "noreply-dmarc-support@google.com",
            subject = "Report domain: qftechtalent.com Submitter: google.com Report-ID: ok",
            body = "",
            messageId = "dmarc-ok",
            inReplyTo = null,
            receivedAt = LocalDateTime.of(2026, 6, 26, 10, 0),
            attachments = emptyList()
        )
        val expertMail = reply(imapUid = 302L)
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(inboundFetch(listOf(dmarcMail, expertMail)))
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com"))
            .thenReturn(contact)
        Mockito.`when`(
            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
        ).thenReturn(true)
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
            val record = invocation.getArgument<MailRecord>(0)
            record.copy(id = record.id ?: 100)
        }
        Mockito.`when`(
            mailAttachmentService.saveInboundAttachments(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyList())
        ).thenReturn(emptyList())
        defaultPromotionStubs(contact)
        stubNotReadyDecision(GroundedAutoReplyReason.QA_NO_MATCH)
        Mockito.`when`(contactRepository.save(Mockito.any(ExpertContact::class.java))).thenAnswer { invocation ->
            invocation.getArgument<ExpertContact>(0)
        }
        Mockito.`when`(statusHistoryRepository.save(Mockito.any(ExpertContactStatusHistory::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertContactStatusHistory>(0) }
        Mockito.`when`(manualHandoffRepository.save(Mockito.any(ManualHandoff::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ManualHandoff>(0) }

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(2, result.fetched)
        Mockito.verify(receiveService).markSeen(account, 301L)
        Mockito.verify(expertEmailAliasService).findContactByEmailOrAlias("expert@example.com")
    }

    @Test
    fun `does not auto reply before introduction inquiry was sent`() {
        val account = account("sender")
        val contact = ExpertContact(
            id = 11,
            campaignId = 1,
            orcidId = "ORCID-11",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            currentStatus = ConversationStatus.NEW.name
        )
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(inboundFetch(listOf(reply())))
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com"))
            .thenReturn(contact)
        Mockito.`when`(
            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
        ).thenReturn(false)

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.fetched)
        assertEquals(0, result.recorded)
        assertEquals(0, result.replied)
        assertEquals(1, result.manualReview)
        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any(MailRecord::class.java))
        Mockito.verify(inboundMailProcessingRepository).save(Mockito.any(InboundMailProcessing::class.java))
        Mockito.verify(receiveService).markSeen(account, 101)
        Mockito.verifyNoInteractions(groundedAutoReplyDecisionService, deliveryService)
    }

    @Test
    fun `keeps mail unread when automatic reply delivery fails`() {
        val account = account("sender")
        val contact = ExpertContact(
            id = 11,
            campaignId = 1,
            orcidId = "ORCID-11",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            currentStatus = ConversationStatus.INTRO_SENT.name
        )
        defaultPromotionStubs(contact)
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(inboundFetch(listOf(reply())))
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com"))
            .thenReturn(contact)
        Mockito.`when`(
            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
        ).thenReturn(true)
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
            val record = invocation.getArgument<MailRecord>(0)
            record.copy(id = record.id ?: 100)
        }
        Mockito.`when`(
            mailAttachmentService.saveInboundAttachments(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyList())
        ).thenReturn(emptyList())
        stubReadyDecision(subject = "Program details", body = "QA answer", ruleIds = listOf(7L))
        Mockito.`when`(
            deliveryService.send(
                eqValue(account),
                anyValue(ComposedMail(to = "stub@example.com", subject = "Stub", body = "Stub"))
            )
        ).thenThrow(IllegalStateException("SMTP unavailable"))

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.fetched)
        assertEquals(0, result.recorded)
        Mockito.verify(inboundMailProcessingRepository, Mockito.never())
            .save(Mockito.any(InboundMailProcessing::class.java))
        Mockito.verify(receiveService, Mockito.never()).markSeen(account, 101)
    }

    @Test
    fun `meeting time reply creates manual meeting confirmation task`() {
        val account = account("sender")
        val contact = ExpertContact(
            id = 11,
            campaignId = 1,
            orcidId = "ORCID-11",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            currentStatus = ConversationStatus.INTRO_SENT.name,
            firstReplyAt = LocalDateTime.now()
        )
        defaultPromotionStubs(contact)
        val meetingReply = reply(body = "I am available at 9AM China time next Tuesday.")
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(inboundFetch(listOf(meetingReply)))
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com"))
            .thenReturn(contact)
        Mockito.`when`(
            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
        ).thenReturn(true)
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
            val record = invocation.getArgument<MailRecord>(0)
            record.copy(id = record.id ?: 100)
        }
        Mockito.`when`(
            mailAttachmentService.saveInboundAttachments(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyList())
        ).thenReturn(emptyList())
        Mockito.`when`(contactRepository.save(Mockito.any(ExpertContact::class.java))).thenAnswer { invocation ->
            invocation.getArgument<ExpertContact>(0)
        }
        Mockito.`when`(statusHistoryRepository.save(Mockito.any(ExpertContactStatusHistory::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertContactStatusHistory>(0) }

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.recorded)
        assertEquals(1, result.manualReview)
        val handoffCaptor = ArgumentCaptor.forClass(ManualHandoff::class.java)
        Mockito.verify(manualHandoffRepository).save(handoffCaptor.capture())
        assertEquals("CONFIRM_MEETING", handoffCaptor.value.reason)
        assertEquals("PENDING", handoffCaptor.value.handoffStatus)

        val contactCaptor = ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(contactRepository, Mockito.atLeast(1)).save(contactCaptor.capture())
        val lastSaved = contactCaptor.allValues.last()
        assertEquals(ConversationStatus.MANUAL_HANDOFF.name, lastSaved.currentStatus)
        assertEquals(true, lastSaved.manualHandoffRequired)
        Mockito.verifyNoInteractions(groundedAutoReplyDecisionService, deliveryService)
        Mockito.verify(receiveService).markSeen(account, 101)
    }

    @Test
    fun `alias matched email continues auto processing`() {
        val account = account("sender")
        val contact = ExpertContact(
            id = 11,
            campaignId = 1,
            orcidId = "ORCID-11",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            currentStatus = ConversationStatus.INTRO_SENT.name,
            firstReplyAt = LocalDateTime.now()
        )
        defaultPromotionStubs(contact)
        val aliasReply = reply(from = "alias@example.com")
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(inboundFetch(listOf(aliasReply)))
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("alias@example.com"))
            .thenReturn(contact)
        Mockito.`when`(
            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
        ).thenReturn(true)
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
            val record = invocation.getArgument<MailRecord>(0)
            record.copy(id = record.id ?: 100)
        }
        Mockito.`when`(
            mailAttachmentService.saveInboundAttachments(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyList())
        ).thenReturn(emptyList())
        Mockito.`when`(contactRepository.save(Mockito.any(ExpertContact::class.java))).thenAnswer { invocation ->
            invocation.getArgument<ExpertContact>(0)
        }
        Mockito.`when`(statusHistoryRepository.save(Mockito.any(ExpertContactStatusHistory::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertContactStatusHistory>(0) }
        stubReadyDecision()
        Mockito.`when`(emailSuppressionService.isSuppressed("alias@example.com")).thenReturn(false)
        Mockito.`when`(
            deliveryService.send(
                anyValue(account),
                anyValue(ComposedMail(to = "stub@example.com", subject = "stub", body = "stub"))
            )
        ).thenReturn(DeliveredMail(messageId = "msg-200", status = "SUCCESS"))

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.recorded)
        Mockito.verify(inboundMailProcessingRepository).save(Mockito.any(InboundMailProcessing::class.java))
        Mockito.verify(receiveService).markSeen(account, 101)
    }

    @Test
    fun `unmatched email records body and in_reply_to for manual review`() {
        val account = account("sender")
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(inboundFetch(listOf(reply())))
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com"))
            .thenReturn(null)
        Mockito.`when`(inboundMailProcessingRepository.save(Mockito.any(InboundMailProcessing::class.java)))
            .thenAnswer { invocation ->
                val record = invocation.getArgument<InboundMailProcessing>(0)
                record.copy(id = 501L)
            }

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.manualReview)
        val captor = ArgumentCaptor.forClass(InboundMailProcessing::class.java)
        Mockito.verify(inboundMailProcessingRepository).save(captor.capture())
        assertEquals("MANUAL_REVIEW", captor.value.processStatus)
        assertEquals("CONTACT_NOT_FOUND", captor.value.processReason)
        assertEquals("reply-1", captor.value.messageId)
        assertEquals("intro-1", captor.value.inReplyTo)
        Mockito.verify(mailAttachmentService).saveUnmatchedAttachments(501L, emptyList())
    }

    @Test
    fun `unmatched email with attachments saves unmatched attachments`() {
        val account = account("sender")
        val attachment = ReceivedMailAttachment(
            fileName = "resume.pdf",
            contentType = "application/pdf",
            content = "pdf".toByteArray()
        )
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(
            inboundFetch(listOf(reply(attachments = listOf(attachment))))
        )
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com"))
            .thenReturn(null)
        Mockito.`when`(inboundMailProcessingRepository.save(Mockito.any(InboundMailProcessing::class.java)))
            .thenAnswer { invocation ->
                val record = invocation.getArgument<InboundMailProcessing>(0)
                record.copy(id = 502L)
            }

        service.receiveAndAutoReply("sender", 5)

        Mockito.verify(mailAttachmentService).saveUnmatchedAttachments(502L, listOf(attachment))
    }

    @Test
    fun `primary email matched works with alias service`() {
        val account = account("sender")
        val contact = ExpertContact(
            id = 11,
            campaignId = 1,
            orcidId = "ORCID-11",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            currentStatus = ConversationStatus.NEW.name
        )
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(inboundFetch(listOf(reply())))
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com"))
            .thenReturn(contact)
        Mockito.`when`(
            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
        ).thenReturn(false)

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.manualReview)
        Mockito.verify(inboundMailProcessingRepository).save(Mockito.any(InboundMailProcessing::class.java))
    }

    @Test
    fun `first reply with material triggers promotion and sets MATERIALS_RECEIVED`() {
        val account = account("sender")
        val contact = ExpertContact(
            id = 11,
            campaignId = 1,
            orcidId = "ORCID-11",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            currentStatus = ConversationStatus.INTRO_SENT.name,
            operatorStatus = "CONTACTED"
        )
        val promoted = contact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION")
        Mockito.`when`(automaticApplicationPromotionService.promoteByMaterialIfNeeded(
            anyValue(contact), anyValue(LocalDateTime.now()), anyValue(0L), anyValue(1)
        )).thenReturn(promoted)
        Mockito.`when`(automaticApplicationPromotionService.promoteByReplyCountIfNeeded(
            anyValue(contact), anyValue(LocalDateTime.now()), anyValue(0L)
        )).thenAnswer { it.getArgument<ExpertContact>(0) }
        Mockito.`when`(expertOperatorStatusService.updateAutomatically(
            anyValue(promoted), anyValue(OperatorStatus.REPLIED), anyValue("")
        )).thenReturn(promoted)

        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(
            inboundFetch(listOf(reply(body = "Here are my documents")))
        )
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com"))
            .thenReturn(contact)
        Mockito.`when`(
            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
        ).thenReturn(true)
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
            val record = invocation.getArgument<MailRecord>(0)
            record.copy(id = record.id ?: 100)
        }
        Mockito.`when`(
            mailAttachmentService.saveInboundAttachments(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyList())
        ).thenReturn(listOf(
            com.weibo.talentintroduction.document.domain.ExpertDocument(
                expertContactId = 11,
                mailAttachmentId = 1,
                documentType = "CV",
                createdAt = LocalDateTime.now()
        )))
        Mockito.`when`(contactRepository.save(Mockito.any(ExpertContact::class.java))).thenAnswer { invocation ->
            invocation.getArgument<ExpertContact>(0)
        }
        Mockito.`when`(statusHistoryRepository.save(Mockito.any(ExpertContactStatusHistory::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertContactStatusHistory>(0) }

        service.receiveAndAutoReply("sender", 5)

        Mockito.verify(automaticApplicationPromotionService).promoteByMaterialIfNeeded(
            anyValue(contact), anyValue(LocalDateTime.now()), anyValue(0L), Mockito.eq(1)
        )
        Mockito.verify(automaticApplicationPromotionService).promoteByReplyCountIfNeeded(
            anyValue(promoted), anyValue(LocalDateTime.now()), anyValue(0L)
        )
        Mockito.verify(expertOperatorStatusService).updateAutomatically(
            anyValue(promoted), anyValue(OperatorStatus.REPLIED), anyValue("")
        )
    }

    @Test
    fun `third reply triggers promotion by reply count`() {
        val account = account("sender")
        val contact = ExpertContact(
            id = 11,
            campaignId = 1,
            orcidId = "ORCID-11",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            currentStatus = ConversationStatus.INTRO_SENT.name,
            operatorStatus = "REPLIED"
        )
        val promoted = contact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION")
        Mockito.`when`(automaticApplicationPromotionService.promoteByMaterialIfNeeded(
            anyValue(contact), anyValue(LocalDateTime.now()), anyValue(0L), anyValue(0)
        )).thenReturn(contact)
        Mockito.`when`(automaticApplicationPromotionService.promoteByReplyCountIfNeeded(
            anyValue(contact), anyValue(LocalDateTime.now()), anyValue(0L)
        )).thenReturn(promoted)
        Mockito.`when`(expertOperatorStatusService.updateAutomatically(
            anyValue(promoted), anyValue(OperatorStatus.REPLIED), anyValue("")
        )).thenReturn(promoted)

        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(inboundFetch(listOf(reply())))
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com"))
            .thenReturn(contact)
        Mockito.`when`(
            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
        ).thenReturn(true)
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
            val record = invocation.getArgument<MailRecord>(0)
            record.copy(id = record.id ?: 100)
        }
        Mockito.`when`(
            mailAttachmentService.saveInboundAttachments(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyList())
        ).thenReturn(emptyList())
        Mockito.`when`(contactRepository.save(Mockito.any(ExpertContact::class.java))).thenAnswer { invocation ->
            invocation.getArgument<ExpertContact>(0)
        }
        Mockito.`when`(statusHistoryRepository.save(Mockito.any(ExpertContactStatusHistory::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertContactStatusHistory>(0) }

        service.receiveAndAutoReply("sender", 5)

        Mockito.verify(automaticApplicationPromotionService).promoteByReplyCountIfNeeded(
            anyValue(contact), anyValue(LocalDateTime.now()), anyValue(0L)
        )
        Mockito.verify(expertOperatorStatusService).updateAutomatically(
            anyValue(promoted), anyValue(OperatorStatus.REPLIED), anyValue("")
        )
    }

    @Test
    fun `already application indexed contact skips promotion`() {
        val account = account("sender")
        val contact = ExpertContact(
            id = 11,
            campaignId = 1,
            orcidId = "ORCID-11",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            currentStatus = ConversationStatus.INTRO_SENT.name,
            applicationIndexed = true,
            currentIndexLevel = "APPLICATION",
            operatorStatus = "REPLIED"
        )
        defaultPromotionStubs(contact)
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(
            inboundFetch(listOf(reply(body = "I have attached my CV")))
        )
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com"))
            .thenReturn(contact)
        Mockito.`when`(
            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
        ).thenReturn(true)
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
            val record = invocation.getArgument<MailRecord>(0)
            record.copy(id = record.id ?: 100)
        }
        Mockito.`when`(
            mailAttachmentService.saveInboundAttachments(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyList())
        ).thenReturn(emptyList())
        Mockito.`when`(contactRepository.save(Mockito.any(ExpertContact::class.java))).thenAnswer { invocation ->
            invocation.getArgument<ExpertContact>(0)
        }
        Mockito.`when`(statusHistoryRepository.save(Mockito.any(ExpertContactStatusHistory::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertContactStatusHistory>(0) }

        service.receiveAndAutoReply("sender", 5)

        Mockito.verify(automaticApplicationPromotionService).promoteByMaterialIfNeeded(
            anyValue(contact), anyValue(LocalDateTime.now()), anyValue(0L), Mockito.eq(0)
        )
    }

    @Test
    fun `auto reply disabled branch still saves attachments and calls promotion`() {
        val account = account("sender")
        val contact = ExpertContact(
            id = 11,
            campaignId = 1,
            orcidId = "ORCID-11",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            currentStatus = ConversationStatus.INTRO_SENT.name,
            autoReplyEnabled = false,
            operatorStatus = "CONTACTED"
        )
        defaultPromotionStubs(contact)
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(
            inboundFetch(listOf(reply(body = "Hello")))
        )
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com"))
            .thenReturn(contact)
        Mockito.`when`(
            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
        ).thenReturn(true)
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
            val record = invocation.getArgument<MailRecord>(0)
            record.copy(id = record.id ?: 100)
        }
        Mockito.`when`(
            mailAttachmentService.saveInboundAttachments(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyList())
        ).thenReturn(emptyList())
        Mockito.`when`(contactRepository.save(Mockito.any(ExpertContact::class.java))).thenAnswer { invocation ->
            invocation.getArgument<ExpertContact>(0)
        }
        Mockito.`when`(statusHistoryRepository.save(Mockito.any(ExpertContactStatusHistory::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertContactStatusHistory>(0) }

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.recorded)
        assertEquals(1, result.manualReview)
        Mockito.verify(automaticApplicationPromotionService).promoteByMaterialIfNeeded(
            anyValue(contact), anyValue(LocalDateTime.now()), anyValue(0L), Mockito.anyInt()
        )
        Mockito.verify(expertOperatorStatusService).updateAutomatically(
            anyValue(contact), anyValue(OperatorStatus.REPLIED), anyValue("")
        )
        Mockito.verifyNoInteractions(groundedAutoReplyDecisionService, deliveryService)
    }

    @Test
    fun `global auto reply disabled does not send mail`() {
        Mockito.`when`(autoReplySettingService.isGlobalEnabled()).thenReturn(false)
        val account = account("sender")
        val contact = introSentContact()
        val received = reply()
        stubAutoReplyPipeline(account, contact, received)
        Mockito.`when`(
            inboundMailProcessingRepository.findBySenderAccountCodeAndImapUid("sender", received.imapUid)
        ).thenReturn(null)

        val result = service.processSingle(account, received, skipImapAck = true)

        assertEquals(SinglePipelineOutcome.GLOBAL_AUTO_REPLY_DISABLED, result.outcome)
        assertEquals(true, result.recorded)
        assertEquals("GLOBAL_AUTO_REPLY_DISABLED", result.reason)
        Mockito.verifyNoInteractions(deliveryService)
        Mockito.verifyNoInteractions(groundedAutoReplyDecisionService)
    }

    @Test
    fun `promotion does not override completed operator status`() {
        val account = account("sender")
        val contact = ExpertContact(
            id = 11,
            campaignId = 1,
            orcidId = "ORCID-11",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            currentStatus = ConversationStatus.INTRO_SENT.name,
            operatorStatus = "COMPLETED"
        )
        defaultPromotionStubs(contact)
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(
            inboundFetch(listOf(reply(body = "Hello again")))
        )
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com"))
            .thenReturn(contact)
        Mockito.`when`(
            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
        ).thenReturn(true)
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
            val record = invocation.getArgument<MailRecord>(0)
            record.copy(id = record.id ?: 100)
        }
        Mockito.`when`(
            mailAttachmentService.saveInboundAttachments(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyList())
        ).thenReturn(emptyList())
        Mockito.`when`(contactRepository.save(Mockito.any(ExpertContact::class.java))).thenAnswer { invocation ->
            invocation.getArgument<ExpertContact>(0)
        }
        Mockito.`when`(statusHistoryRepository.save(Mockito.any(ExpertContactStatusHistory::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertContactStatusHistory>(0) }

        service.receiveAndAutoReply("sender", 5)

        Mockito.verify(expertOperatorStatusService).updateAutomatically(
            anyValue(contact), anyValue(OperatorStatus.REPLIED), anyValue("")
        )
    }

    @Test
    fun `QA auto reply skips send and hands off when recipient is suppressed`() {
        val account = account("sender")
        val contact = introSentContact()
        stubAutoReplyPipeline(account, contact)
        stubReadyDecision(subject = "Re: Program", body = "Auto reply body", ruleIds = listOf(1L))
        Mockito.`when`(emailSuppressionService.isSuppressed("expert@example.com")).thenReturn(true)
        Mockito.`when`(
            manualHandoffRepository.findFirstByExpertContactIdAndReasonAndHandoffStatusOrderByUpdatedAtDesc(
                11, "RECIPIENT_UNSUBSCRIBED", "PENDING"
            )
        ).thenReturn(null)

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.recorded)
        assertEquals(1, result.manualReview)
        assertEquals(0, result.replied)
        Mockito.verify(deliveryService, Mockito.never()).send(
            anyValue(account),
            anyValue(ComposedMail(to = "stub@example.com", subject = "Stub", body = "Stub"))
        )
        val handoffCaptor = ArgumentCaptor.forClass(ManualHandoff::class.java)
        Mockito.verify(manualHandoffRepository).save(handoffCaptor.capture())
        assertEquals("RECIPIENT_UNSUBSCRIBED", handoffCaptor.value.reason)
        val contactCaptor = ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(contactRepository, Mockito.atLeast(1)).save(contactCaptor.capture())
        assertEquals(ConversationStatus.MANUAL_HANDOFF.name, contactCaptor.allValues.last().currentStatus)
    }

    @Test
    fun `QA auto reply sends normally when recipient is not suppressed`() {
        val account = account("sender")
        val contact = introSentContact()
        stubAutoReplyPipeline(account, contact)
        val plainBody = "Auto reply body"
        stubReadyDecision(subject = "Re: Program", body = plainBody, ruleIds = listOf(1L))
        Mockito.`when`(emailSuppressionService.isSuppressed("expert@example.com")).thenReturn(false)
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(
            deliveryService.send(
                anyValue(account),
                anyValue(ComposedMail(to = "stub@example.com", subject = "stub", body = "stub"))
            )
        ).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            DeliveredMail(messageId = "msg-200", status = "SUCCESS")
        }

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.replied)
        assertEquals(0, result.manualReview)
        Mockito.verify(emailSuppressionService).isSuppressed("expert@example.com")
        val sentMail = sentMails.single()
        assertEquals(true, sentMail.html)
        assertEquals(plainBody, sentMail.text)
        assertEquals(mailContentService.plainTextToHtml(plainBody), sentMail.body)
        assertNotNull(sentMail.messageId)
        assertTrue(
            sentMail.messageId!!.matches(Regex("^<auto-reply-11-[0-9a-f-]{36}@qftechtalent\\.com>$")),
            "unexpected messageId: ${sentMail.messageId}"
        )
        val mailRecordCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository, Mockito.atLeastOnce()).save(mailRecordCaptor.capture())
        val outboundRecord = mailRecordCaptor.allValues.last { it.direction == "OUTBOUND" && it.mailType == "QA_REPLY" }
        assertEquals(plainBody, outboundRecord.body)
        assertFalse(outboundRecord.body!!.contains("<p>"))
        val contactCaptor = ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(contactRepository, Mockito.atLeast(1)).save(contactCaptor.capture())
        assertEquals(ConversationStatus.QA_AUTO_REPLIED.name, contactCaptor.allValues.last().currentStatus)
    }

    @Test
    fun `confidence log write failure does not block inbound processing`() {
        val account = account("sender")
        val contact = introSentContact()
        stubAutoReplyPipeline(account, contact)
        stubReadyDecision(subject = "Re: Program", body = "Auto reply body", ruleIds = listOf(1L))
        Mockito.`when`(emailSuppressionService.isSuppressed("expert@example.com")).thenReturn(false)
        Mockito.`when`(
            deliveryService.send(
                anyValue(account),
                anyValue(ComposedMail(to = "stub@example.com", subject = "stub", body = "stub"))
            )
        ).thenReturn(DeliveredMail(messageId = "msg-203", status = "SUCCESS"))
        Mockito.doThrow(RuntimeException("db down"))
            .`when`(autoReplyConfidenceLogRepository).save(Mockito.any(AutoReplyConfidenceLog::class.java))

        val result = service.processSingle(account, reply(), skipImapAck = true)

        assertEquals(SinglePipelineOutcome.QA_REPLIED, result.outcome)
        assertEquals(true, result.recorded)
        Mockito.verify(autoReplyConfidenceLogRepository)
            .save(Mockito.any(AutoReplyConfidenceLog::class.java))
        Mockito.verify(deliveryService).send(
            anyValue(account),
            anyValue(ComposedMail(to = "stub@example.com", subject = "stub", body = "stub"))
        )
        Mockito.reset(autoReplyConfidenceLogRepository)
    }

    @Test
    fun `mailto unsubscribe mail with empty body is suppressed with MAILTO source`() {
        val account = account("sender")
        val contact = introSentContact()
        val received = reply(body = "", subject = "unsubscribe")
        stubAutoReplyPipeline(account, contact, received)
        Mockito.`when`(emailSuppressionService.detectUnsubscribeSource("unsubscribe", ""))
            .thenReturn(SuppressionSource.MAILTO)

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.recorded)
        Mockito.verify(emailSuppressionService)
            .suppress("expert@example.com", SuppressionSource.MAILTO, "mailto unsubscribe")
    }

    @Test
    fun `repeated QA auto replies for same contact get distinct message ids`() {
        val account = account("sender")
        val contact = introSentContact()
        stubAutoReplyPipeline(account, contact)
        stubReadyDecision(subject = "Re: Program", body = "Auto reply body", ruleIds = listOf(1L))
        Mockito.`when`(emailSuppressionService.isSuppressed("expert@example.com")).thenReturn(false)
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(
            deliveryService.send(
                anyValue(account),
                anyValue(ComposedMail(to = "stub@example.com", subject = "stub", body = "stub"))
            )
        ).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            DeliveredMail(messageId = "msg-200", status = "SUCCESS")
        }

        service.processSingle(account, reply(imapUid = 101), skipImapAck = true)
        service.processSingle(account, reply(imapUid = 202), skipImapAck = true)

        val messageIds = sentMails.map { it.messageId }
        assertEquals(2, messageIds.size)
        assertTrue(messageIds.all { it != null })
        assertTrue(
            messageIds.all { it!!.matches(Regex("^<auto-reply-11-[0-9a-f-]{36}@qftechtalent\\.com>$")) },
            "unexpected messageIds: $messageIds"
        )
        assertEquals(2, messageIds.distinct().size)
    }

    @Test
    fun `QA auto reply uses shared grounded decision service`() {
        val account = account("sender")
        val contact = introSentContact()
        stubAutoReplyPipeline(account, contact)
        stubReadyDecision(subject = "Re: Talent Program")
        Mockito.`when`(emailSuppressionService.isSuppressed("expert@example.com")).thenReturn(false)
        Mockito.`when`(
            deliveryService.send(
                anyValue(account),
                anyValue(ComposedMail(to = "stub@example.com", subject = "stub", body = "stub"))
            )
        ).thenReturn(DeliveredMail(messageId = "msg-200", status = "SUCCESS"))

        service.receiveAndAutoReply("sender", 5)

        Mockito.verify(groundedAutoReplyDecisionService).decide(
            Mockito.anyString(),
            eqValue("Re: Talent Program"),
            Mockito.any(),
            Mockito.any()
        )
    }

    @Test
    fun `QA auto reply renders expert placeholders in outbound body`() {
        val account = account("sender")
        val contact = introSentContact()
        stubAutoReplyPipeline(account, contact)
        stubExpertProfile(contact.orcidId, familyNames = "Lovelace")
        val templateBody = "Dear \${expertFamilyName}, here is the answer."
        val renderedBody = "Dear Lovelace, here is the answer."
        stubReadyDecision(subject = "Re: Program", body = templateBody, ruleIds = listOf(1L))
        Mockito.`when`(emailSuppressionService.isSuppressed("expert@example.com")).thenReturn(false)
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(
            deliveryService.send(
                anyValue(account),
                anyValue(ComposedMail(to = "stub@example.com", subject = "stub", body = "stub"))
            )
        ).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            DeliveredMail(messageId = "msg-201", status = "SUCCESS")
        }

        service.receiveAndAutoReply("sender", 5)

        val sentMail = sentMails.single()
        assertEquals(renderedBody, sentMail.text)
        assertEquals(mailContentService.plainTextToHtml(renderedBody), sentMail.body)
        assertFalse(sentMail.text!!.contains("\${"))
        val mailRecordCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository, Mockito.atLeastOnce()).save(mailRecordCaptor.capture())
        val outboundRecord = mailRecordCaptor.allValues.last { it.direction == "OUTBOUND" && it.mailType == "QA_REPLY" }
        assertEquals(renderedBody, outboundRecord.body)
    }

    @Test
    fun `QA auto reply still sends when expert profile lookup fails`() {
        val account = account("sender")
        val contact = introSentContact()
        stubAutoReplyPipeline(account, contact)
        Mockito.`when`(expertSearchService.findByOrcidId(contact.orcidId, ExpertIndexLevel.CANDIDATE))
            .thenThrow(RuntimeException("ES down"))
        val templateBody = "Dear \${expertFamilyName|there}, thanks."
        val renderedBody = "Dear there, thanks."
        stubReadyDecision(subject = "Re: Program", body = templateBody, ruleIds = listOf(1L))
        Mockito.`when`(emailSuppressionService.isSuppressed("expert@example.com")).thenReturn(false)
        Mockito.`when`(
            deliveryService.send(
                anyValue(account),
                anyValue(ComposedMail(to = "stub@example.com", subject = "stub", body = "stub"))
            )
        ).thenReturn(DeliveredMail(messageId = "msg-202", status = "SUCCESS"))

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.replied)
        val mailRecordCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository, Mockito.atLeastOnce()).save(mailRecordCaptor.capture())
        val outboundRecord = mailRecordCaptor.allValues.last { it.direction == "OUTBOUND" && it.mailType == "QA_REPLY" }
        assertEquals(renderedBody, outboundRecord.body)
    }

    @Test
    fun `disabled account ingests inbound but blocks QA auto reply`() {
        val account = account("sender").copy(enabled = false)
        val contact = introSentContact()
        stubAutoReplyPipeline(account, contact)
        stubReadyDecision(subject = "Re: Program", body = "Auto reply body", ruleIds = listOf(1L))
        Mockito.`when`(emailSuppressionService.isSuppressed("expert@example.com")).thenReturn(false)

        val result = service.processSingle(account, reply(), skipImapAck = true)

        assertEquals(SinglePipelineOutcome.MANUAL_REVIEW_BY_INTENT, result.outcome)
        assertEquals("ACCOUNT_AUTO_SEND_DISABLED", result.reason)
        assertEquals(true, result.recorded)
        Mockito.verify(deliveryService, Mockito.never()).send(
            anyValue(account),
            anyValue(ComposedMail(to = "stub@example.com", subject = "Stub", body = "Stub"))
        )
        val inboundCaptor = ArgumentCaptor.forClass(InboundMailProcessing::class.java)
        Mockito.verify(inboundMailProcessingRepository).save(inboundCaptor.capture())
        assertEquals("MANUAL_REVIEW", inboundCaptor.value.processStatus)
        assertEquals("ACCOUNT_AUTO_SEND_DISABLED", inboundCaptor.value.processReason)
    }

    @Test
    fun `interested reply sends meeting invitation with variant seed from contact`() {
        val account = account("sender")
        val contact = introSentContact()
        val received = reply(body = "I am interested in this opportunity")
        stubAutoReplyPipeline(account, contact, received)
        val expectedSeed = MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)
        Mockito.`when`(
            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "MEETING_INVITATION")
        ).thenReturn(false)
        Mockito.`when`(emailSuppressionService.isSuppressed("expert@example.com")).thenReturn(false)
        Mockito.`when`(
            expertOperatorStatusService.updateAutomatically(
                anyValue(contact),
                anyValue(OperatorStatus.INVITED),
                eqValue("MEETING_INVITATION_SENT")
            )
        ).thenAnswer { it.getArgument<ExpertContact>(0) }
        Mockito.`when`(
            mailComposeTemplateService.renderByCode(
                eqValue("MEETING_INVITATION"),
                anyValue(emptyMap<String, String>()),
                eqValue(expectedSeed)
            )
        ).thenReturn(
            ComposeTemplateRenderResult(
                subject = "Meeting invite",
                body = "<p>Please join us</p>",
                mailType = "MEETING_INVITATION"
            )
        )
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(
            deliveryService.send(
                anyValue(account),
                anyValue(ComposedMail(to = "stub@example.com", subject = "stub", body = "stub"))
            )
        ).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            DeliveredMail(messageId = "msg-meeting", status = "SUCCESS")
        }

        val result = service.processSingle(account, received, skipImapAck = true)

        assertEquals(SinglePipelineOutcome.MEETING_INVITED, result.outcome)
        Mockito.verify(mailComposeTemplateService).renderByCode(
            eqValue("MEETING_INVITATION"),
            anyValue(emptyMap<String, String>()),
            eqValue(expectedSeed)
        )
        val sentMail = sentMails.single()
        assertNotNull(sentMail.messageId)
        // IP-4: kind must be identical to MeetingInvitationMailComposer's "meeting-invitation"
        val kind = Regex("^<([a-z-]+)-").find(sentMail.messageId!!)!!.groupValues[1]
        assertEquals("meeting-invitation", kind)
        assertTrue(
            sentMail.messageId!!.matches(Regex("^<meeting-invitation-ORCID-11-[0-9a-f-]{36}@qftechtalent\\.com>$")),
            "unexpected messageId: ${sentMail.messageId}"
        )
    }

    @Test
    fun `disabled account ingests inbound but blocks meeting invitation auto send`() {
        val account = account("sender").copy(enabled = false)
        val contact = introSentContact()
        stubAutoReplyPipeline(
            account,
            contact,
            reply(body = "I am interested in this opportunity")
        )
        Mockito.`when`(
            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "MEETING_INVITATION")
        ).thenReturn(false)
        Mockito.`when`(emailSuppressionService.isSuppressed("expert@example.com")).thenReturn(false)

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.recorded)
        assertEquals(1, result.manualReview)
        assertEquals(0, result.meetingInvitations)
        assertEquals(0, result.replied)
        Mockito.verify(deliveryService, Mockito.never()).send(
            anyValue(account),
            anyValue(ComposedMail(to = "stub@example.com", subject = "Stub", body = "Stub"))
        )
        val inboundCaptor = ArgumentCaptor.forClass(InboundMailProcessing::class.java)
        Mockito.verify(inboundMailProcessingRepository).save(inboundCaptor.capture())
        assertEquals("ACCOUNT_AUTO_SEND_DISABLED", inboundCaptor.value.processReason)
    }

    @Test
    fun `auto tag failure does not block inbound processing`() {
        val account = account("sender")
        val contact = introSentContact()
        val received = reply(body = "Could you share the program details?")
        stubAutoReplyPipeline(account, contact, received)
        stubReadyDecision(subject = "Re: Program", body = "Auto reply body", ruleIds = listOf(1L))
        Mockito.`when`(emailSuppressionService.isSuppressed("expert@example.com")).thenReturn(false)
        Mockito.`when`(
            deliveryService.send(
                anyValue(account),
                anyValue(ComposedMail(to = "stub@example.com", subject = "stub", body = "stub"))
            )
        ).thenReturn(DeliveredMail(messageId = "msg-200", status = "SUCCESS"))
        Mockito.doAnswer { throw RuntimeException("tag boom") }
            .`when`(inboundMailTagService)
            .autoApplyQaTags(
                Mockito.anyLong(),
                Mockito.nullable(String::class.java),
                Mockito.nullable(String::class.java),
                Mockito.anyString()
            )

        val result = service.processSingle(account, received, skipImapAck = true)

        assertEquals(SinglePipelineOutcome.QA_REPLIED, result.outcome)
        assertEquals(true, result.recorded)
        Mockito.verify(inboundMailProcessingRepository).save(Mockito.any(InboundMailProcessing::class.java))
        Mockito.verify(inboundMailTagService).autoApplyQaTags(
            Mockito.anyLong(),
            Mockito.anyString(),
            Mockito.nullable(String::class.java),
            Mockito.anyString()
        )
    }

    @Test
    fun `duplicate inbound questions in same poll get one QA auto reply`() {
        val account = account("sender")
        val contact = introSentContact()
        val first = reply(
            subject = "Follow-up on the Talent Program",
            body = "Could you share the program details?",
            imapUid = 101L
        )
        val second = first.copy(imapUid = 102L, messageId = "reply-2")
        val third = first.copy(imapUid = 103L, messageId = "reply-3")
        stubAutoReplyPipeline(account, contact, first)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(
            inboundFetch(listOf(first, second, third))
        )
        listOf(first, second, third).forEach { mail ->
            Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias(mail.from)).thenReturn(contact)
        }
        stubReadyDecision(subject = "Re: Program", body = "Auto reply body", ruleIds = listOf(1L))
        Mockito.`when`(emailSuppressionService.isSuppressed("expert@example.com")).thenReturn(false)
        val savedRecords = mutableListOf<MailRecord>()
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
            val record = invocation.getArgument<MailRecord>(0)
            val saved = record.copy(id = record.id ?: (100L + savedRecords.size))
            savedRecords += saved
            saved
        }
        Mockito.`when`(
            mailRecordRepository.findRecentDuplicateInbound(
                anyValue(0L),
                anyValue(""),
                anyValue(""),
                anyValue(""),
                anyValue(LocalDateTime.now()),
                anyValue(LocalDateTime.now())
            )
        ).thenAnswer { invocation ->
            val contactId = invocation.getArgument<Long>(0)
            val accountCode = invocation.getArgument<String>(1)
            val subject = invocation.getArgument<String?>(2)
            val cleanedBody = invocation.getArgument<String>(3)
            val since = invocation.getArgument<LocalDateTime>(4)
            val receivedAt = invocation.getArgument<LocalDateTime>(5)
            savedRecords.lastOrNull {
                val savedReceivedAt = it.receivedAt
                it.expertContactId == contactId &&
                    it.senderAccountCode == accountCode &&
                    it.direction == "INBOUND" &&
                    it.mailType == "REPLY" &&
                    it.subject == subject &&
                    it.cleanedBody == cleanedBody &&
                    savedReceivedAt != null &&
                    !savedReceivedAt.isBefore(since) &&
                    !savedReceivedAt.isAfter(receivedAt)
            }
        }
        Mockito.`when`(
            deliveryService.send(
                anyValue(account),
                anyValue(ComposedMail(to = "stub@example.com", subject = "Stub", body = "Stub"))
            )
        ).thenReturn(DeliveredMail(messageId = "msg-200", status = "SUCCESS"))

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.replied)
        Mockito.verify(deliveryService, Mockito.times(1)).send(
            anyValue(account),
            anyValue(ComposedMail(to = "stub@example.com", subject = "Stub", body = "Stub"))
        )
    }

    @Test
    fun `QA gap hands off without sending outbound mail`() {
        val account = account("sender")
        val contact = introSentContact()
        stubAutoReplyPipeline(account, contact)
        stubNotReadyDecision(GroundedAutoReplyReason.QA_GROUNDING_GAP)

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.manualReview)
        assertEquals(0, result.replied)
        Mockito.verify(deliveryService, Mockito.never()).send(
            anyValue(account),
            anyValue(ComposedMail(to = "stub@example.com", subject = "Stub", body = "Stub"))
        )
        Mockito.verifyNoInteractions(mailRecordQaRuleRepository)
        val handoffCaptor = ArgumentCaptor.forClass(ManualHandoff::class.java)
        Mockito.verify(manualHandoffRepository).save(handoffCaptor.capture())
        assertEquals(GroundedAutoReplyReason.QA_GROUNDING_GAP, handoffCaptor.value.reason)
        val contactCaptor = ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(contactRepository, Mockito.atLeast(1)).save(contactCaptor.capture())
        assertEquals(ConversationStatus.MANUAL_HANDOFF.name, contactCaptor.allValues.last().currentStatus)
    }

    @Test
    fun `QA auto reply persists all matched rules in association table`() {
        val account = account("sender")
        val contact = introSentContact()
        stubAutoReplyPipeline(account, contact)
        stubReadyDecision(subject = "Re: Program", body = "Combined answer", ruleIds = listOf(10L, 20L))
        Mockito.`when`(emailSuppressionService.isSuppressed("expert@example.com")).thenReturn(false)
        Mockito.`when`(
            deliveryService.send(
                anyValue(account),
                anyValue(ComposedMail(to = "stub@example.com", subject = "Re: Program", body = "Combined answer"))
            )
        ).thenReturn(DeliveredMail(messageId = "msg-200", status = "SUCCESS"))

        service.receiveAndAutoReply("sender", 5)

        val mailRecordCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository, Mockito.atLeastOnce()).save(mailRecordCaptor.capture())
        val outboundRecord = mailRecordCaptor.allValues.last { it.direction == "OUTBOUND" && it.mailType == "QA_REPLY" }
        assertEquals(10L, outboundRecord.matchedQaRuleId)

        val qaRuleCaptor = ArgumentCaptor.forClass(MailRecordQaRule::class.java)
        Mockito.verify(mailRecordQaRuleRepository, Mockito.times(2)).save(qaRuleCaptor.capture())
        assertEquals(listOf(10L to 0, 20L to 1), qaRuleCaptor.allValues.map { it.qaRuleId to it.ordinal })
        qaRuleCaptor.allValues.forEach { assertEquals(100L, it.mailRecordId) }
    }

    @Test
    fun `single QA auto reply persists one association row`() {
        val account = account("sender")
        val contact = introSentContact()
        stubAutoReplyPipeline(account, contact)
        stubReadyDecision(subject = "Re: Program", body = "Auto reply body", ruleIds = listOf(1L))
        Mockito.`when`(emailSuppressionService.isSuppressed("expert@example.com")).thenReturn(false)
        Mockito.`when`(
            deliveryService.send(
                anyValue(account),
                anyValue(ComposedMail(to = "stub@example.com", subject = "Re: Program", body = "Auto reply body"))
            )
        ).thenReturn(DeliveredMail(messageId = "msg-200", status = "SUCCESS"))

        service.receiveAndAutoReply("sender", 5)

        val qaRuleCaptor = ArgumentCaptor.forClass(MailRecordQaRule::class.java)
        Mockito.verify(mailRecordQaRuleRepository).save(qaRuleCaptor.capture())
        assertEquals(1L, qaRuleCaptor.value.qaRuleId)
        assertEquals(0, qaRuleCaptor.value.ordinal)
    }

    @Test
    fun `overview match with multiple questions sends instead of QA gap handoff`() {
        val account = account("sender")
        val contact = introSentContact()
        stubAutoReplyPipeline(account, contact)
        stubReadyDecision(subject = "Program overview", body = "Overview answer", ruleIds = listOf(100L))
        Mockito.`when`(emailSuppressionService.isSuppressed("expert@example.com")).thenReturn(false)
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(
            deliveryService.send(
                anyValue(account),
                anyValue(ComposedMail(to = "stub@example.com", subject = "stub", body = "stub"))
            )
        ).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            DeliveredMail(messageId = "msg-200", status = "SUCCESS")
        }

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.replied)
        assertEquals(0, result.manualReview)
        val sentMail = sentMails.single()
        assertEquals(true, sentMail.html)
        assertEquals("Overview answer", sentMail.text)
        assertEquals(mailContentService.plainTextToHtml("Overview answer"), sentMail.body)
        Mockito.verify(manualHandoffRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `meeting invitation skips send and hands off when recipient is suppressed`() {
        val account = account("sender")
        val contact = introSentContact()
        stubAutoReplyPipeline(
            account,
            contact,
            reply(body = "I am interested in this opportunity")
        )
        Mockito.`when`(
            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "MEETING_INVITATION")
        ).thenReturn(false)
        Mockito.`when`(emailSuppressionService.isSuppressed("expert@example.com")).thenReturn(true)
        Mockito.`when`(
            manualHandoffRepository.findFirstByExpertContactIdAndReasonAndHandoffStatusOrderByUpdatedAtDesc(
                11, "RECIPIENT_UNSUBSCRIBED", "PENDING"
            )
        ).thenReturn(null)

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.recorded)
        assertEquals(1, result.manualReview)
        assertEquals(0, result.meetingInvitations)
        Mockito.verify(deliveryService, Mockito.never()).send(
            anyValue(account),
            anyValue(ComposedMail(to = "stub@example.com", subject = "Stub", body = "Stub"))
        )
        Mockito.verifyNoInteractions(mailComposeTemplateService)
        val handoffCaptor = ArgumentCaptor.forClass(ManualHandoff::class.java)
        Mockito.verify(manualHandoffRepository).save(handoffCaptor.capture())
        assertEquals("RECIPIENT_UNSUBSCRIBED", handoffCaptor.value.reason)
        val contactCaptor = ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(contactRepository, Mockito.atLeast(1)).save(contactCaptor.capture())
        assertEquals(ConversationStatus.MANUAL_HANDOFF.name, contactCaptor.allValues.last().currentStatus)
    }

    @Test
    fun `QA reply sets operator status to REPLIED`() {
        val account = account("sender")
        val contact = ExpertContact(
            id = 11,
            campaignId = 1,
            orcidId = "ORCID-11",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            currentStatus = ConversationStatus.INTRO_SENT.name,
            operatorStatus = "CONTACTED",
            firstReplyAt = LocalDateTime.now()
        )
        defaultPromotionStubs(contact)
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(inboundFetch(listOf(reply())))
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com"))
            .thenReturn(contact)
        Mockito.`when`(
            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
        ).thenReturn(true)
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
            val record = invocation.getArgument<MailRecord>(0)
            record.copy(id = record.id ?: 100)
        }
        Mockito.`when`(
            mailAttachmentService.saveInboundAttachments(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyList())
        ).thenReturn(emptyList())
        stubReadyDecision(subject = "Re: Program", body = "Auto reply body", ruleIds = listOf(1L))
        Mockito.`when`(
            deliveryService.send(
                anyValue(account("qa-sender")),
                anyValue(ComposedMail(to = "stub@example.com", subject = "Re: Program", body = "Auto reply body"))
            )
        ).thenReturn(DeliveredMail(messageId = "msg-200", status = "SUCCESS"))
        Mockito.`when`(contactRepository.save(Mockito.any(ExpertContact::class.java))).thenAnswer { invocation ->
            invocation.getArgument<ExpertContact>(0)
        }
        Mockito.`when`(statusHistoryRepository.save(Mockito.any(ExpertContactStatusHistory::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertContactStatusHistory>(0) }

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.replied)
        assertEquals(0, result.manualReview)
        Mockito.verify(expertOperatorStatusService).updateAutomatically(
            anyValue(contact), anyValue(OperatorStatus.REPLIED), anyValue("")
        )
    }

    @Test
    fun `receiveAndAutoReply processes mail above cursor regardless of seen flag`() {
        val account = account("sender")
        val seenReply = reply(imapUid = 22L)
        Mockito.`when`(cursorService.get("sender")).thenReturn(CursorState(uidValidity = 1L, lastUid = 21L))
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 21L, 5)).thenReturn(
            inboundFetch(listOf(seenReply), afterUid = 21L)
        )
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com")).thenReturn(null)

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.fetched)
        assertEquals(1, result.manualReview)
        Mockito.verify(inboundMailProcessingRepository).save(Mockito.any(InboundMailProcessing::class.java))
    }

    @Test
    fun `receiveAndAutoReply does not advance cursor past failed lower uid`() {
        val account = account("sender")
        val mail10 = reply(imapUid = 10L)
        val mail11 = reply(imapUid = 11L)
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(
            inboundFetch(listOf(mail10, mail11))
        )
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com"))
            .thenThrow(RuntimeException("boom"))

        service.receiveAndAutoReply("sender", 5)

        Mockito.verify(cursorService).advance(
            eqValue("sender"),
            eqValue(1L),
            eqValue(listOf(10L, 11L)),
            eqValue(emptySet()),
            eqValue(0L)
        )
    }

    @Test
    fun `receiveAndAutoReply rescans from zero when uid validity changes`() {
        val account = account("sender")
        Mockito.`when`(cursorService.get("sender")).thenReturn(CursorState(uidValidity = 100L, lastUid = 5L))
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 5L, 5)).thenReturn(
            inboundFetch(emptyList(), afterUid = 5L, uidValidity = 200L)
        )
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(
            inboundFetch(listOf(reply()), afterUid = 0L, uidValidity = 200L)
        )
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com")).thenReturn(null)

        service.receiveAndAutoReply("sender", 5)

        Mockito.verify(receiveService).fetchInboundSince(account, 0L, 5)
    }

    @Test
    fun `processByUids returns duplicate for already processed uid`() {
        val account = account("sender")
        val uid = 22L
        val mail = reply(imapUid = uid)
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchByUids(account, listOf(uid))).thenReturn(listOf(mail))
        Mockito.`when`(inboundMailProcessingRepository.findBySenderAccountCodeAndImapUid("sender", uid))
            .thenReturn(
                InboundMailProcessing(
                    id = 1L,
                    senderAccountCode = "sender",
                    imapUid = uid,
                    messageId = "reply-1",
                    fromEmail = "expert@example.com",
                    subject = "Re: Talent Program",
                    receivedAt = LocalDateTime.now(),
                    processStatus = "PROCESSED",
                    processReason = "PROCESSED",
                    expertContactId = 11L
                )
            )

        val results = service.processByUids("sender", listOf(uid))

        assertEquals(1, results.size)
        assertEquals(SinglePipelineOutcome.DUPLICATE_IMAP_UID, results[0].outcome)
    }

    private fun stubExpertProfile(
        orcidId: String,
        familyNames: String? = "Lovelace",
        institution: String? = "Oxford"
    ) {
        val profile = ExpertProfile(
            orcidId = orcidId,
            email = "expert@example.com",
            givenNames = "Ada",
            familyNames = familyNames,
            country = "UK",
            keyword = null,
            employment = null,
            researchFields = null,
            institution = institution
        )
        Mockito.`when`(expertSearchService.findByOrcidId(orcidId, ExpertIndexLevel.CANDIDATE))
            .thenReturn(profile)
    }

    private fun introSentContact(): ExpertContact {
        val contact = ExpertContact(
            id = 11,
            campaignId = 1,
            orcidId = "ORCID-11",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            currentStatus = ConversationStatus.INTRO_SENT.name,
            firstReplyAt = LocalDateTime.now()
        )
        defaultPromotionStubs(contact)
        return contact
    }


    private fun readyDecision(
        subject: String = "Re: Talent Program",
        body: String = "Auto reply body",
        ruleIds: List<Long> = listOf(1L)
    ) = GroundedAutoReplyDecision(
        readyToSend = true,
        reason = GroundedAutoReplyReason.QA_AUTO_REPLIED,
        subject = subject,
        rawDraftText = body,
        qaRuleIds = ruleIds,
        draftReadiness = AiReplyDraftReadiness.READY,
        generationState = AiReplyGenerationState.LLM_USED,
        usedLlm = true,
        confidence = AutoReplyConfidenceScore(
            crs = 92.0,
            coverageScore = 40.0,
            evidenceScore = 25.0,
            consistencyScore = 20.0,
            historyScore = 7.0,
            requestCount = 1,
            unsupportedCount = 0,
            partialCount = 0,
            verifiedRuleCount = ruleIds.size,
            warningCount = 0
        )
    )

    private fun stubReadyDecision(
        subject: String = "Re: Talent Program",
        body: String = "Auto reply body",
        ruleIds: List<Long> = listOf(1L)
    ) {
        Mockito.`when`(
            groundedAutoReplyDecisionService.decide(
                Mockito.anyString(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()
            )
        ).thenReturn(readyDecision(subject, body, ruleIds))
    }

    private fun stubNotReadyDecision(reason: String) {
        Mockito.`when`(
            groundedAutoReplyDecisionService.decide(
                Mockito.anyString(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()
            )
        ).thenReturn(
            GroundedAutoReplyDecision(
                readyToSend = false,
                reason = reason,
                subject = "Re: Talent Program",
                rawDraftText = null,
                qaRuleIds = emptyList(),
                draftReadiness = AiReplyDraftReadiness.BLOCKED,
                generationState = AiReplyGenerationState.FALLBACK_NO_RESPONSE,
                usedLlm = false
            )
        )
    }

    private fun stubAutoReplyPipeline(
        account: MailSenderAccount,
        contact: ExpertContact,
        received: ReceivedMail = reply()
    ) {
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchInboundSince(account, 0L, 5)).thenReturn(inboundFetch(listOf(received)))
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias(received.from))
            .thenReturn(contact)
        Mockito.`when`(
            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(11, "OUTBOUND", "INTRODUCTION")
        ).thenReturn(true)
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { invocation ->
            val record = invocation.getArgument<MailRecord>(0)
            record.copy(id = record.id ?: 100)
        }
        Mockito.`when`(
            mailAttachmentService.saveInboundAttachments(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyList())
        ).thenReturn(emptyList())
        Mockito.`when`(contactRepository.save(Mockito.any(ExpertContact::class.java))).thenAnswer { invocation ->
            invocation.getArgument<ExpertContact>(0)
        }
        Mockito.`when`(statusHistoryRepository.save(Mockito.any(ExpertContactStatusHistory::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertContactStatusHistory>(0) }
    }

    private fun inboundFetch(
        mails: List<ReceivedMail>,
        afterUid: Long = 0L,
        uidValidity: Long = 1L
    ): InboundFetchResult =
        InboundFetchResult(
            mails = mails,
            uidValidity = uidValidity,
            maxUidInWindow = mails.maxOfOrNull { it.imapUid } ?: afterUid
        )

    private fun reply(
        body: String = "Could you share the program details?",
        from: String = "expert@example.com",
        subject: String = "Re: Talent Program",
        attachments: List<ReceivedMailAttachment> = emptyList(),
        imapUid: Long = 101
    ): ReceivedMail =
        ReceivedMail(
            imapUid = imapUid,
            from = from,
            subject = subject,
            body = body,
            messageId = "reply-1",
            inReplyTo = "intro-1",
            receivedAt = LocalDateTime.of(2026, 5, 22, 10, 0),
            attachments = attachments
        )

    private fun account(accountCode: String): MailSenderAccount =
        MailSenderAccount(
            accountCode = accountCode,
            senderEmail = "$accountCode@qftechtalent.com",
            senderName = accountCode,
            senderTitle = "Customer Care Officer",
            senderDisplayName = accountCode,
            teamName = "Qingfei Tech Talent Team",
            countryName = "China",
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "$accountCode@qftechtalent.com",
            smtpPassword = "secret",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "$accountCode@qftechtalent.com",
            imapPassword = "secret"
        )

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    private fun <T> eqValue(value: T): T =
        Mockito.eq(value) ?: value
}
