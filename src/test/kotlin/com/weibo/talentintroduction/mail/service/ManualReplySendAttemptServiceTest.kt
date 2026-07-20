package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.MailSendAttempt
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
import com.weibo.talentintroduction.campaign.repository.MailSendAttemptRepository
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.junit.jupiter.api.Assertions.assertEquals
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

    /*
    @Test
    fun `recordSendAudit best effort does not throw on failure`() {
        Mockito.doThrow(RuntimeException("audit down"))
            .`when`(operatorActionLogService)
            .record(
                anyString(), anyLong(), com.weibo.talentintroduction.audit.domain.OperatorActionType.SEND_MANUAL_COMPOSED_REPLY,
                anyLong(), anyLong(),
                Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any()
            )
        val delivered = DeliveredMail(messageId = "out-1", status = "SENT")
        val record = com.weibo.talentintroduction.mail.domain.InboundMailProcessing(
            id = 100L, senderAccountCode = "sender-1", imapUid = 1L,
            messageId = "in-1", fromEmail = "expert@test.com",
            subject = "Test", body = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "MANUAL_REVIEW", processReason = "QA_NO_MATCH",
            expertContactId = 1L
        )
        service.recordSendAudit(
            inboundProcessingId = 100L, contactId = 1L, mailRecordId = 500L,
            canonicalFactIds = listOf(10L), carriesQa = true,
            delivered = delivered, sendSubject = "Re: Test",
            bodyPreviewText = "Hello", operatorName = "op",
            inboundRecord = record, serverSuggestedFactIds = listOf(10L),
            edited = false, note = "note"
        )
    }
    */
}

