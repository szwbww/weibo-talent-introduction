package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.ExpertEmailAlias
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
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

    private val service = UnmatchedInboundMailService(
        inboundMailProcessingRepository = inboundMailProcessingRepository,
        expertContactRepository = expertContactRepository,
        expertEmailAliasService = expertEmailAliasService,
        mailRecordRepository = mailRecordRepository
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
    fun `listUnmatched returns manual review records without contact`() {
        val records = listOf(processing(id = 1L, email = "a@b.com"))
        Mockito.`when`(
            inboundMailProcessingRepository.findAllByProcessStatusAndExpertContactIdIsNullOrderByReceivedAtDesc("MANUAL_REVIEW")
        ).thenReturn(records)

        val result = service.listUnmatched()
        assertEquals(1, result.size)
        assertEquals("a@b.com", result[0].fromEmail)
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
        Mockito.`when`(inboundMailProcessingRepository.save(Mockito.any(InboundMailProcessing::class.java)))
            .thenAnswer { it.getArgument<InboundMailProcessing>(0) }

        val result = service.bindToContact(recordId, contactId, "operator1")
        assertEquals("PROCESSED", result.processStatus)
        assertEquals("MANUAL_BOUND", result.processReason)

        Mockito.verify(expertEmailAliasService).bindAlias(contactId, "old@example.com", "MANUAL_BIND")
        Mockito.verify(inboundMailProcessingRepository).save(Mockito.any(InboundMailProcessing::class.java))
    }
}
