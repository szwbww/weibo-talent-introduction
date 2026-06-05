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
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.handoff.repository.ManualHandoffRepository
import com.weibo.talentintroduction.mail.repository.InboundIntentRepository
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.service.QaMatchResult
import com.weibo.talentintroduction.qa.service.QaMatchService
import com.weibo.talentintroduction.template.service.MailTemplateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.LocalDateTime

class AutoMailReplyServiceTest {
    private val accountService = Mockito.mock(MailSenderAccountService::class.java)
    private val receiveService = Mockito.mock(MailReceiveService::class.java)
    private val deliveryService = Mockito.mock(MailDeliveryService::class.java)
    private val contactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val inboundIntentRepository = Mockito.mock(InboundIntentRepository::class.java)
    private val manualHandoffRepository = Mockito.mock(ManualHandoffRepository::class.java)
    private val mailAttachmentService = Mockito.mock(MailAttachmentService::class.java)
    private val mailTemplateService = Mockito.mock(MailTemplateService::class.java)
    private val qaMatchService = Mockito.mock(QaMatchService::class.java)
    private val statusHistoryRepository = Mockito.mock(ExpertContactStatusHistoryRepository::class.java)
    private val conversationStateService = ConversationStateService(contactRepository, statusHistoryRepository)
    private val meetingScheduleService = Mockito.mock(com.weibo.talentintroduction.campaign.service.MeetingScheduleService::class.java)
    private val expertEmailAliasService = Mockito.mock(ExpertEmailAliasService::class.java)
    private val expertIndexWriterService = Mockito.mock(com.weibo.talentintroduction.expert.service.ExpertIndexWriterService::class.java)
    private val automaticApplicationPromotionService = Mockito.mock(AutomaticApplicationPromotionService::class.java)
    private val expertOperatorStatusService = Mockito.mock(ExpertOperatorStatusService::class.java)
    private val service = AutoMailReplyService(
        accountService,
        receiveService,
        deliveryService,
        contactRepository,
        mailRecordRepository,
        inboundMailProcessingRepository,
        inboundIntentRepository,
        manualHandoffRepository,
        mailAttachmentService,
        MailBodyCleaner(),
        InboundIntentClassifier(),
        mailTemplateService,
        qaMatchService,
        conversationStateService,
        meetingScheduleService,
        expertEmailAliasService,
        expertIndexWriterService,
        automaticApplicationPromotionService,
        expertOperatorStatusService
    )

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
        Mockito.`when`(receiveService.fetchUnread(account, 5)).thenReturn(listOf(reply()))
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
        Mockito.verifyNoInteractions(qaMatchService, deliveryService)
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
        Mockito.`when`(receiveService.fetchUnread(account, 5)).thenReturn(listOf(reply()))
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
        Mockito.`when`(qaMatchService.match(Mockito.anyString())).thenReturn(
            QaMatchResult(
                ruleId = 7,
                replySubject = "Program details",
                replyBody = "QA answer",
                handoffRequired = false,
                autoReplyEnabled = true
            )
        )
        Mockito.`when`(
            deliveryService.send(
                eqValue(account),
                anyValue(ComposedMail(to = "stub@example.com", subject = "Stub", body = "Stub"))
            )
        ).thenThrow(IllegalStateException("SMTP unavailable"))

        assertThrows(IllegalStateException::class.java) {
            service.receiveAndAutoReply("sender", 5)
        }

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
        Mockito.`when`(receiveService.fetchUnread(account, 5)).thenReturn(listOf(meetingReply))
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
        Mockito.verifyNoInteractions(qaMatchService, deliveryService)
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
        Mockito.`when`(receiveService.fetchUnread(account, 5)).thenReturn(listOf(aliasReply))
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

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.recorded)
        Mockito.verify(inboundMailProcessingRepository).save(Mockito.any(InboundMailProcessing::class.java))
        Mockito.verify(receiveService).markSeen(account, 101)
    }

    @Test
    fun `unmatched email records body and in_reply_to for manual review`() {
        val account = account("sender")
        Mockito.`when`(accountService.getEnabledAccount("sender")).thenReturn(account)
        Mockito.`when`(receiveService.fetchUnread(account, 5)).thenReturn(listOf(reply()))
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com"))
            .thenReturn(null)

        val result = service.receiveAndAutoReply("sender", 5)

        assertEquals(1, result.manualReview)
        val captor = ArgumentCaptor.forClass(InboundMailProcessing::class.java)
        Mockito.verify(inboundMailProcessingRepository).save(captor.capture())
        assertEquals("MANUAL_REVIEW", captor.value.processStatus)
        assertEquals("CONTACT_NOT_FOUND", captor.value.processReason)
        assertEquals("reply-1", captor.value.messageId)
        assertEquals("intro-1", captor.value.inReplyTo)
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
        Mockito.`when`(receiveService.fetchUnread(account, 5)).thenReturn(listOf(reply()))
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
        Mockito.`when`(receiveService.fetchUnread(account, 5)).thenReturn(
            listOf(reply(body = "Here are my documents"))
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
            )
        ))
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
        Mockito.`when`(receiveService.fetchUnread(account, 5)).thenReturn(listOf(reply()))
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
        Mockito.`when`(receiveService.fetchUnread(account, 5)).thenReturn(
            listOf(reply(body = "I have attached my CV"))
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
        Mockito.`when`(receiveService.fetchUnread(account, 5)).thenReturn(
            listOf(reply(body = "Hello"))
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
        Mockito.verifyNoInteractions(qaMatchService, deliveryService)
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
        Mockito.`when`(receiveService.fetchUnread(account, 5)).thenReturn(
            listOf(reply(body = "Hello again"))
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
        Mockito.`when`(receiveService.fetchUnread(account, 5)).thenReturn(listOf(reply()))
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
        Mockito.`when`(qaMatchService.match(Mockito.anyString())).thenReturn(
            QaMatchResult(
                ruleId = 1,
                replySubject = "Re: Program",
                replyBody = "Auto reply body",
                handoffRequired = false,
                autoReplyEnabled = true
            )
        )
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

    private fun reply(body: String = "Could you share the program details?", from: String = "expert@example.com"): ReceivedMail =
        ReceivedMail(
            imapUid = 101,
            from = from,
            subject = "Re: Talent Program",
            body = body,
            messageId = "reply-1",
            inReplyTo = "intro-1",
            receivedAt = LocalDateTime.of(2026, 5, 22, 10, 0)
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
