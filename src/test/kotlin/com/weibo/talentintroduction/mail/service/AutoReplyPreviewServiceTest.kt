package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.service.QaMatchResult
import com.weibo.talentintroduction.qa.service.QaMatchService
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.template.service.ComposeTemplateRenderResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

class AutoReplyPreviewServiceTest {
    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val mailBodyCleaner = MailBodyCleaner()
    private val inboundIntentClassifier = InboundIntentClassifier()
    private val qaMatchService = Mockito.mock(QaMatchService::class.java)
    private val mailComposeTemplateService = Mockito.mock(MailComposeTemplateService::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
    private val emailSuppressionService = Mockito.mock(EmailSuppressionService::class.java)

    private val service = AutoReplyPreviewService(
        inboundMailProcessingRepository,
        mailBodyCleaner,
        inboundIntentClassifier,
        qaMatchService,
        mailComposeTemplateService,
        mailSenderAccountService,
        mailRecordRepository,
        expertContactRepository,
        mailAttachmentRepository,
        emailSuppressionService
    )

    private val contactId = 42L
    private val processingId = 100L
    private val previewContact = ExpertContact(
        id = contactId,
        campaignId = 1,
        orcidId = "orcid-1",
        expertEmail = "expert@test.com",
        expertName = "Expert",
        currentStatus = ConversationStatus.WAITING_REPLY.name,
        operatorStatus = "CONTACTED",
        currentIndexLevel = "CANDIDATE",
        autoReplyEnabled = true
    )
    private val meetingInvitationSeed =
        MailComposeTemplateService.variantSeedFor(previewContact.orcidId, previewContact.expertEmail)

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    private fun <T> eqValue(value: T): T =
        Mockito.eq(value) ?: value

    @BeforeEach
    fun setUp() {
        Mockito.`when`(mailAttachmentRepository.findAllByInboundProcessingIdOrderByCreatedAtAsc(processingId))
            .thenReturn(emptyList())
        stubContact(autoReplyEnabled = true, currentStatus = ConversationStatus.WAITING_REPLY.name)
        stubIntroductionSent(introSent = true)
        stubMeetingSent(meetingSent = false)
        Mockito.`when`(emailSuppressionService.isSuppressed(Mockito.anyString())).thenReturn(false)
    }

    @Test
    fun `QA match returns QA_AUTO_REPLIED with reply from match`() {
        stubRecord(body = "Can I work remotely part time?")
        Mockito.`when`(qaMatchService.match(Mockito.anyString(), Mockito.anyInt())).thenReturn(
            QaMatchResult(
                ruleId = 7L,
                replySubject = "Re: Remote work",
                replyBody = "Yes, remote is possible.",
                handoffRequired = false,
                autoReplyEnabled = true,
                matchedRuleIds = listOf(7L),
                gapDetected = false
            )
        )

        val result = service.preview(processingId)

        assertEquals(AutoReplyPreviewKind.QA_AUTO_REPLIED, result.previewKind)
        assertEquals(InboundIntentCode.ASK_REMOTE_PART_TIME, result.intentCode)
        assertEquals(AutoIntentAction.QA, result.autoAction)
        assertEquals("Re: Remote work", result.replySubject)
        assertEquals("Yes, remote is possible.", result.replyBody)
        assertEquals(listOf(7L), result.matchedRuleIds)
    }

    @Test
    fun `QA preview passes same variant seed as auto reply would use`() {
        stubRecord(body = "Can I work remotely part time?")
        val expectedSeed = MailComposeTemplateService.variantSeedFor(previewContact.orcidId, previewContact.expertEmail)
        Mockito.`when`(qaMatchService.match(Mockito.anyString(), eqValue(expectedSeed))).thenReturn(
            QaMatchResult(
                ruleId = 7L,
                replySubject = "Re: Remote work",
                replyBody = "Yes, remote is possible.",
                handoffRequired = false,
                autoReplyEnabled = true,
                matchedRuleIds = listOf(7L),
                gapDetected = false
            )
        )

        service.preview(processingId)

        Mockito.verify(qaMatchService).match(Mockito.anyString(), eqValue(expectedSeed))
    }

    @Test
    fun `QA null match returns QA_NO_MATCH`() {
        stubRecord(body = "Can I work remotely part time?")
        Mockito.`when`(qaMatchService.match(Mockito.anyString(), Mockito.anyInt())).thenReturn(null)

        val result = service.preview(processingId)

        assertEquals(AutoReplyPreviewKind.QA_NO_MATCH, result.previewKind)
        assertEquals("QA_NO_MATCH", result.reason)
        assertNull(result.replyBody)
    }

    @Test
    fun `QA handoffRequired returns QA_NO_MATCH`() {
        stubRecord(body = "Can I work remotely part time?")
        Mockito.`when`(qaMatchService.match(Mockito.anyString(), Mockito.anyInt())).thenReturn(
            qaMatch(handoffRequired = true)
        )

        val result = service.preview(processingId)

        assertEquals(AutoReplyPreviewKind.QA_NO_MATCH, result.previewKind)
        assertEquals("QA_NO_MATCH", result.reason)
    }

    @Test
    fun `QA autoReplyEnabled false returns QA_NO_MATCH`() {
        stubRecord(body = "Can I work remotely part time?")
        Mockito.`when`(qaMatchService.match(Mockito.anyString(), Mockito.anyInt())).thenReturn(
            qaMatch(autoReplyEnabled = false)
        )

        val result = service.preview(processingId)

        assertEquals(AutoReplyPreviewKind.QA_NO_MATCH, result.previewKind)
    }

    @Test
    fun `QA gapDetected returns QA_GAP`() {
        stubRecord(body = "Can I work remotely part time?")
        Mockito.`when`(qaMatchService.match(Mockito.anyString(), Mockito.anyInt())).thenReturn(
            qaMatch(gapDetected = true)
        )

        val result = service.preview(processingId)

        assertEquals(AutoReplyPreviewKind.QA_GAP, result.previewKind)
        assertEquals("QA_GAP", result.reason)
    }

    @Test
    fun `INTERESTED without prior meeting returns MEETING_INVITATION with rendered body`() {
        stubRecord(body = "I am interested in this opportunity")
        stubSenderAccount()
        Mockito.`when`(
            mailComposeTemplateService.renderByCode(
                eqValue("MEETING_INVITATION"),
                anyValue(emptyMap<String, String>()),
                eqValue(meetingInvitationSeed)
            )
        ).thenReturn(
            ComposeTemplateRenderResult(subject = "Meeting invite", body = "<p>Please join us</p>")
        )

        val result = service.preview(processingId)

        assertEquals(AutoReplyPreviewKind.MEETING_INVITATION, result.previewKind)
        assertEquals(InboundIntentCode.INTERESTED, result.intentCode)
        assertEquals("Meeting invite", result.replySubject)
        assertEquals("<p>Please join us</p>", result.replyBody)
    }

    @Test
    fun `INTERESTED with prior meeting returns MEETING_ALREADY_SENT but still shows body`() {
        stubRecord(body = "I am interested in this opportunity")
        stubSenderAccount()
        stubMeetingSent(meetingSent = true)
        Mockito.`when`(
            mailComposeTemplateService.renderByCode(
                eqValue("MEETING_INVITATION"),
                anyValue(emptyMap<String, String>()),
                eqValue(meetingInvitationSeed)
            )
        ).thenReturn(
            ComposeTemplateRenderResult(subject = "Meeting invite", body = "<p>Please join us</p>")
        )

        val result = service.preview(processingId)

        assertEquals(AutoReplyPreviewKind.MEETING_ALREADY_SENT, result.previewKind)
        assertEquals("<p>Please join us</p>", result.replyBody)
    }

    @Test
    fun `INTERESTED preview passes same variant seed as auto send pipeline`() {
        stubRecord(body = "I am interested in this opportunity")
        stubSenderAccount()
        Mockito.`when`(
            mailComposeTemplateService.renderByCode(
                eqValue("MEETING_INVITATION"),
                anyValue(emptyMap<String, String>()),
                eqValue(meetingInvitationSeed)
            )
        ).thenReturn(
            ComposeTemplateRenderResult(subject = "Meeting invite", body = "<p>Please join us</p>")
        )

        service.preview(processingId)

        Mockito.verify(mailComposeTemplateService).renderByCode(
            eqValue("MEETING_INVITATION"),
            anyValue(emptyMap<String, String>()),
            eqValue(meetingInvitationSeed)
        )
    }

    @Test
    fun `INTERESTED without contact id uses zero variant seed`() {
        val record = InboundMailProcessing(
            id = processingId,
            senderAccountCode = "sender-1",
            imapUid = 1L,
            messageId = "msg-1",
            fromEmail = "expert@test.com",
            subject = "Test subject",
            body = "I am interested in this opportunity",
            cleanedBody = null,
            receivedAt = LocalDateTime.now(),
            processStatus = "MANUAL_REVIEW",
            processReason = "QA_NO_MATCH",
            expertContactId = null
        )
        Mockito.`when`(inboundMailProcessingRepository.findById(processingId))
            .thenReturn(Optional.of(record))
        stubSenderAccount()
        Mockito.`when`(
            mailComposeTemplateService.renderByCode(
                eqValue("MEETING_INVITATION"),
                anyValue(emptyMap<String, String>()),
                eqValue(0)
            )
        ).thenReturn(
            ComposeTemplateRenderResult(subject = "Meeting invite", body = "<p>Please join us</p>")
        )

        service.preview(processingId)

        Mockito.verify(mailComposeTemplateService).renderByCode(
            eqValue("MEETING_INVITATION"),
            anyValue(emptyMap<String, String>()),
            eqValue(0)
        )
        Mockito.verify(expertContactRepository, Mockito.never()).findById(Mockito.anyLong())
    }

    @Test
    fun `ASK_FUNDING returns MANUAL_HANDOFF with HANDLE_RISKY_QUESTION`() {
        stubRecord(body = "What is the funding package?")

        val result = service.preview(processingId)

        assertEquals(AutoReplyPreviewKind.MANUAL_HANDOFF, result.previewKind)
        assertEquals(InboundIntentCode.ASK_FUNDING, result.intentCode)
        assertEquals("HANDLE_RISKY_QUESTION", result.reason)
        assertNull(result.replyBody)
    }

    @Test
    fun `CV attached text returns MANUAL_HANDOFF with REVIEW_DOCUMENT`() {
        stubRecord(body = "Please find my cv attached")

        val result = service.preview(processingId)

        assertEquals(AutoReplyPreviewKind.MANUAL_HANDOFF, result.previewKind)
        assertEquals("REVIEW_DOCUMENT", result.reason)
    }

    @Test
    fun `NOT_INTERESTED returns MANUAL_HANDOFF with intent reason`() {
        stubRecord(body = "Not interested, please remove me")

        val result = service.preview(processingId)

        assertEquals(AutoReplyPreviewKind.MANUAL_HANDOFF, result.previewKind)
        assertEquals("INTENT_NOT_INTERESTED", result.reason)
    }

    @Test
    fun `autoReply disabled still shows QA body and marks wouldBeBlockedBy`() {
        stubContact(autoReplyEnabled = false, currentStatus = ConversationStatus.WAITING_REPLY.name)
        stubRecord(body = "Can I work remotely part time?")
        Mockito.`when`(qaMatchService.match(Mockito.anyString(), Mockito.anyInt())).thenReturn(qaMatch())

        val result = service.preview(processingId)

        assertEquals(AutoReplyPreviewKind.QA_AUTO_REPLIED, result.previewKind)
        assertEquals("QA reply body", result.replyBody)
        assertTrue(result.wouldBeBlockedBy.contains("AUTO_REPLY_DISABLED"))
    }

    @Test
    fun `MANUAL_HANDOFF status still shows QA body and marks wouldBeBlockedBy`() {
        stubContact(autoReplyEnabled = true, currentStatus = ConversationStatus.MANUAL_HANDOFF.name)
        stubRecord(body = "Can I work remotely part time?")
        Mockito.`when`(qaMatchService.match(Mockito.anyString(), Mockito.anyInt())).thenReturn(qaMatch())

        val result = service.preview(processingId)

        assertEquals(AutoReplyPreviewKind.QA_AUTO_REPLIED, result.previewKind)
        assertTrue(result.wouldBeBlockedBy.contains("MANUAL_HANDOFF_STATUS"))
    }

    @Test
    fun `missing introduction marks INTRODUCTION_NOT_SENT without hiding body`() {
        stubIntroductionSent(introSent = false)
        stubRecord(body = "Can I work remotely part time?")
        Mockito.`when`(qaMatchService.match(Mockito.anyString(), Mockito.anyInt())).thenReturn(qaMatch())

        val result = service.preview(processingId)

        assertEquals(AutoReplyPreviewKind.QA_AUTO_REPLIED, result.previewKind)
        assertTrue(result.wouldBeBlockedBy.contains("INTRODUCTION_NOT_SENT"))
    }

    @Test
    fun `suppressed recipient still shows QA body and marks RECIPIENT_UNSUBSCRIBED`() {
        stubRecord(body = "Can I work remotely part time?")
        Mockito.`when`(emailSuppressionService.isSuppressed("expert@test.com")).thenReturn(true)
        Mockito.`when`(qaMatchService.match(Mockito.anyString(), Mockito.anyInt())).thenReturn(qaMatch())

        val result = service.preview(processingId)

        assertEquals(AutoReplyPreviewKind.QA_AUTO_REPLIED, result.previewKind)
        assertEquals("QA reply body", result.replyBody)
        assertTrue(result.wouldBeBlockedBy.contains("RECIPIENT_UNSUBSCRIBED"))
    }

    @Test
    fun `suppressed recipient still shows meeting invitation body`() {
        stubRecord(body = "I am interested in this opportunity")
        stubSenderAccount()
        Mockito.`when`(emailSuppressionService.isSuppressed("expert@test.com")).thenReturn(true)
        Mockito.`when`(
            mailComposeTemplateService.renderByCode(
                eqValue("MEETING_INVITATION"),
                anyValue(emptyMap<String, String>()),
                eqValue(meetingInvitationSeed)
            )
        ).thenReturn(
            ComposeTemplateRenderResult(subject = "Meeting invite", body = "<p>Please join us</p>")
        )

        val result = service.preview(processingId)

        assertEquals(AutoReplyPreviewKind.MEETING_INVITATION, result.previewKind)
        assertEquals("<p>Please join us</p>", result.replyBody)
        assertTrue(result.wouldBeBlockedBy.contains("RECIPIENT_UNSUBSCRIBED"))
    }

    @Test
    fun `disabled sender account still shows QA body and marks ACCOUNT_AUTO_SEND_DISABLED`() {
        stubContact(autoReplyEnabled = true, currentStatus = ConversationStatus.WAITING_REPLY.name)
        stubRecord(body = "Can I work remotely part time?")
        stubSenderAccount(enabled = false)
        Mockito.`when`(qaMatchService.match(Mockito.anyString(), Mockito.anyInt())).thenReturn(qaMatch())

        val result = service.preview(processingId)

        assertEquals(AutoReplyPreviewKind.QA_AUTO_REPLIED, result.previewKind)
        assertEquals("QA reply body", result.replyBody)
        assertTrue(result.wouldBeBlockedBy.contains("ACCOUNT_AUTO_SEND_DISABLED"))
    }

    @Test
    fun `attachment with unknown text sets attachmentIntentIgnored and follows QA branch`() {
        stubRecord(body = "Hello there, just checking in")
        Mockito.`when`(mailAttachmentRepository.findAllByInboundProcessingIdOrderByCreatedAtAsc(processingId))
            .thenReturn(listOf(sampleAttachment()))
        Mockito.`when`(qaMatchService.match(Mockito.anyString(), Mockito.anyInt())).thenReturn(qaMatch())

        val result = service.preview(processingId)

        assertTrue(result.attachmentIntentIgnored)
        assertEquals(InboundIntentCode.UNKNOWN, result.intentCode)
        assertEquals(AutoReplyPreviewKind.QA_AUTO_REPLIED, result.previewKind)
        assertFalse(result.wouldBeBlockedBy.contains("AUTO_REPLY_DISABLED"))
    }

    private fun stubRecord(
        body: String,
        subject: String = "Test subject",
        cleanedBody: String? = null
    ) {
        val record = InboundMailProcessing(
            id = processingId,
            senderAccountCode = "sender-1",
            imapUid = 1L,
            messageId = "msg-1",
            fromEmail = "expert@test.com",
            subject = subject,
            body = body,
            cleanedBody = cleanedBody,
            receivedAt = LocalDateTime.now(),
            processStatus = "MANUAL_REVIEW",
            processReason = "QA_NO_MATCH",
            expertContactId = contactId
        )
        Mockito.`when`(inboundMailProcessingRepository.findById(processingId))
            .thenReturn(Optional.of(record))
    }

    private fun stubContact(autoReplyEnabled: Boolean, currentStatus: String) {
        val contact = previewContact.copy(
            autoReplyEnabled = autoReplyEnabled,
            currentStatus = currentStatus
        )
        Mockito.`when`(expertContactRepository.findById(contactId)).thenReturn(Optional.of(contact))
    }

    private fun stubIntroductionSent(introSent: Boolean) {
        Mockito.`when`(
            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
                contactId, "OUTBOUND", "INTRODUCTION"
            )
        ).thenReturn(introSent)
    }

    private fun stubMeetingSent(meetingSent: Boolean) {
        Mockito.`when`(
            mailRecordRepository.existsByExpertContactIdAndDirectionAndMailType(
                contactId, "OUTBOUND", "MEETING_INVITATION"
            )
        ).thenReturn(meetingSent)
    }

    private fun stubSenderAccount(enabled: Boolean = true) {
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender-1")).thenReturn(
            MailSenderAccount(
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
                enabled = enabled
            )
        )
    }

    private fun qaMatch(
        handoffRequired: Boolean = false,
        autoReplyEnabled: Boolean = true,
        gapDetected: Boolean = false
    ) = QaMatchResult(
        ruleId = 1L,
        replySubject = "Re: Test",
        replyBody = "QA reply body",
        handoffRequired = handoffRequired,
        autoReplyEnabled = autoReplyEnabled,
        matchedRuleIds = listOf(1L),
        gapDetected = gapDetected
    )

    private fun sampleAttachment() = MailAttachment(
        id = 1L,
        mailRecordId = null,
        inboundProcessingId = processingId,
        fileName = "cv.pdf",
        contentType = "application/pdf",
        fileSize = 100L,
        storagePath = "/tmp/cv.pdf"
    )
}
