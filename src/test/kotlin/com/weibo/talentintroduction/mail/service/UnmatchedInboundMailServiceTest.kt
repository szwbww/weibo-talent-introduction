package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.ExpertEmailAlias
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.repository.ReasonTypeCount
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

class UnmatchedInboundMailServiceTest {
    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val expertEmailAliasService = Mockito.mock(ExpertEmailAliasService::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val senderAccountRepository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val expertIndexWriterService = Mockito.mock(com.weibo.talentintroduction.expert.service.ExpertIndexWriterService::class.java)
    private val operatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)

    private val service = UnmatchedInboundMailService(
        inboundMailProcessingRepository = inboundMailProcessingRepository,
        expertContactRepository = expertContactRepository,
        expertEmailAliasService = expertEmailAliasService,
        mailRecordRepository = mailRecordRepository,
        senderAccountRepository = senderAccountRepository,
        expertIndexWriterService = expertIndexWriterService,
        operatorActionLogService = operatorActionLogService
    )

    private fun contact(id: Long, email: String) = ExpertContact(
        id = id, campaignId = 10L, orcidId = "orcid-$id",
        expertEmail = email, expertName = null
    )

    private fun processing(
        id: Long, email: String, status: String = "MANUAL_REVIEW",
        reason: String = "CONTACT_NOT_FOUND", contactId: Long? = null,
        messageId: String? = null, inReplyTo: String? = null
    ) = InboundMailProcessing(
        id = id, senderAccountCode = "acc1", imapUid = 100L,
        fromEmail = email, messageId = messageId, inReplyTo = inReplyTo,
        subject = "Test", receivedAt = LocalDateTime.now(),
        processStatus = status, processReason = reason,
        expertContactId = contactId
    )

    @Test
    fun `listManualReviewQueue returns manual review records and counts`() {
        val records = listOf(processing(id = 1L, email = "a@b.com"))
        Mockito.`when`(
            inboundMailProcessingRepository.findManualReviewQueue(null, null, null, 20, 0)
        ).thenReturn(records)
        Mockito.`when`(
            inboundMailProcessingRepository.countManualReviewQueue(null, null, null)
        ).thenReturn(1L)
        Mockito.`when`(senderAccountRepository.findAllByEnabledTrue())
            .thenReturn(listOf(MailSenderAccount(accountCode = "acc1", senderEmail = "a@b.com", senderName = "A", senderTitle = null, senderDisplayName = null, teamName = null, countryName = null, smtpHost = "h", smtpPort = 587, smtpUsername = "u", smtpPassword = "p", imapHost = "h", imapPort = 993, imapUsername = "u", imapPassword = "p")))
        Mockito.`when`(
            inboundMailProcessingRepository.countManualReviewByAccounts(listOf("acc1"))
        ).thenReturn(1L)
        Mockito.`when`(
            inboundMailProcessingRepository.countGroupedByReasonTypeForAccounts(listOf("acc1"))
        ).thenReturn(listOf(ReasonTypeCount("QA_NO_MATCH", 1L)))

        val result = service.listManualReviewQueue(null, null, null, 20, 0)
        assertEquals(1, result.records.size)
        assertEquals("a@b.com", result.records[0].fromEmail)
        assertEquals(1L, result.totalCount)
        assertEquals(1L, result.manualReviewTotal)
        assertEquals(1L, result.countsByReasonType["QA_NO_MATCH"])
    }

    @Test
    fun `bindToContact updates record and adds alias`() {
        val recordId = 1L
        val contactId = 10L
        val record = processing(id = recordId, email = "alias@example.com")
        val c = contact(contactId, "main@example.com")

        Mockito.`when`(inboundMailProcessingRepository.findById(recordId)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(contactId)).thenReturn(Optional.of(c))
        Mockito.`when`(expertEmailAliasService.bindAlias(contactId, "alias@example.com", "MANUAL_BIND"))
            .thenReturn(ExpertEmailAlias(id = 100L, expertContactId = contactId, email = "alias@example.com", normalizedEmail = "alias@example.com"))
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { it.getArgument<ExpertContact>(0) }
        Mockito.`when`(inboundMailProcessingRepository.save(Mockito.any(InboundMailProcessing::class.java)))
            .thenAnswer { it.getArgument<InboundMailProcessing>(0) }

        val result = service.bindToContact(recordId, contactId, "operator1")
        assertEquals(contactId, result.expertContactId)
        assertEquals("PROCESSED", result.processStatus)
        assertEquals("MANUAL_BOUND", result.processReason)
        assertEquals("operator1", result.resolvedBy)
        assertNotNull(result.resolvedAt)
    }

    @Test
    fun `bindToContact rejects already bound record`() {
        val recordId = 1L
        val record = processing(id = recordId, email = "a@b.com", status = "PROCESSED", reason = "MANUAL_BOUND", contactId = 10L)
        Mockito.`when`(inboundMailProcessingRepository.findById(recordId)).thenReturn(Optional.of(record))

        assertThrows(IllegalArgumentException::class.java) {
            service.bindToContact(recordId, 20L, "operator1")
        }
    }

    @Test
    fun `suggestCandidates uses in_reply_to when available`() {
        val record = processing(id = 1L, email = "a@b.com", messageId = "msg-1", inReplyTo = "out-msg-1")
        val outboundRecord = MailRecord(
            id = 50L, expertContactId = 10L, direction = "OUTBOUND", mailType = "INTRODUCTION",
            messageId = "out-msg-1", inReplyTo = null, subject = "Hello", body = "Body",
            matchedQaRuleId = null, sendStatus = null, receivedAt = null, sentAt = LocalDateTime.now()
        )
        val c = contact(10L, "expert@example.com")

        Mockito.`when`(mailRecordRepository.findByMessageId("out-msg-1")).thenReturn(outboundRecord)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(c))

        val candidates = service.suggestCandidates(record)
        assertEquals(1, candidates.size)
        assertEquals("IN_REPLY_TO", candidates[0].reason)
        assertEquals(90, candidates[0].confidence)
    }

    @Test
    fun `bindToContact with unmatched mail does not trigger auto reply`() {
        val recordId = 1L
        val contactId = 10L
        val record = processing(id = recordId, email = "old@example.com")
        val c = contact(contactId, "main@example.com")

        Mockito.`when`(inboundMailProcessingRepository.findById(recordId)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(contactId)).thenReturn(Optional.of(c))
        Mockito.`when`(expertEmailAliasService.bindAlias(contactId, "old@example.com", "MANUAL_BIND"))
            .thenReturn(ExpertEmailAlias(id = 100L, expertContactId = contactId, email = "old@example.com", normalizedEmail = "old@example.com"))
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { it.getArgument<ExpertContact>(0) }
        Mockito.`when`(inboundMailProcessingRepository.save(Mockito.any(InboundMailProcessing::class.java)))
            .thenAnswer { it.getArgument<InboundMailProcessing>(0) }

        val result = service.bindToContact(recordId, contactId, "operator1")
        assertEquals("PROCESSED", result.processStatus)
        assertEquals("MANUAL_BOUND", result.processReason)

        Mockito.verify(expertEmailAliasService).bindAlias(contactId, "old@example.com", "MANUAL_BIND")
        Mockito.verify(inboundMailProcessingRepository).save(Mockito.any(InboundMailProcessing::class.java))
    }

    @Test
    fun `markResolved resolves record and clears attention if last`() {
        val recordId = 1L
        val contactId = 10L
        val record = processing(id = recordId, email = "expert@example.com", contactId = contactId)
        val c = contact(contactId, "expert@example.com").copy(needsManualAttention = true)

        Mockito.`when`(inboundMailProcessingRepository.findById(recordId)).thenReturn(Optional.of(record))
        Mockito.`when`(inboundMailProcessingRepository.save(Mockito.any(InboundMailProcessing::class.java)))
            .thenAnswer { it.getArgument<InboundMailProcessing>(0) }
        Mockito.`when`(inboundMailProcessingRepository.countByExpertContactIdAndProcessStatus(contactId, "MANUAL_REVIEW"))
            .thenReturn(0L)
        Mockito.`when`(expertContactRepository.findById(contactId)).thenReturn(Optional.of(c))
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { it.getArgument<ExpertContact>(0) }

        val result = service.markResolved(recordId, "operator1", "done")
        assertEquals("PROCESSED", result.processStatus)
        assertEquals("MANUAL_RESOLVED", result.processReason)
        Mockito.verify(expertContactRepository).save(Mockito.argThat { !it.needsManualAttention })
    }

    @Test
    fun `bindToContact with promoteToApplication throws when ES promotion fails`() {
        val recordId = 1L
        val contactId = 10L
        val record = processing(id = recordId, email = "alias@example.com")
        val c = contact(contactId, "main@example.com")

        Mockito.`when`(inboundMailProcessingRepository.findById(recordId)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(contactId)).thenReturn(Optional.of(c))
        Mockito.`when`(expertEmailAliasService.bindAlias(contactId, "alias@example.com", "MANUAL_BIND"))
            .thenReturn(ExpertEmailAlias(id = 100L, expertContactId = contactId, email = "alias@example.com", normalizedEmail = "alias@example.com"))
        Mockito.`when`(expertIndexWriterService.promoteToApplication(
            orcid = Mockito.anyString() ?: "",
            contact = Mockito.any(ExpertContact::class.java) ?: contact(10L, ""),
            firstReplyAt = Mockito.any() ?: java.time.Instant.now(),
            sourceInboundId = Mockito.any(),
            triggeredBy = Mockito.anyString() ?: "",
            operatorName = Mockito.anyString() ?: ""
        )).thenReturn(false)

        assertThrows(IllegalStateException::class.java) {
            service.bindToContact(recordId, contactId, "operator1", promoteToApplication = true)
        }
    }
}
