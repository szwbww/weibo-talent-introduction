package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.MeetingSchedule
import com.weibo.talentintroduction.campaign.domain.ExpertContactStatusHistory
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.MeetingScheduleRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactStatusHistoryRepository
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.MailDeliveryService
import com.weibo.talentintroduction.mail.service.ComposedMail
import com.weibo.talentintroduction.mail.service.DeliveredMail
import com.weibo.talentintroduction.template.service.ComposeTemplateRenderResult
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.common.domain.ConversationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

class MeetingScheduleServiceTest {
    private val meetingScheduleRepository = Mockito.mock(MeetingScheduleRepository::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val mailSenderAccountRepository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val mailDeliveryService = Mockito.mock(MailDeliveryService::class.java)
    private val mailComposeTemplateService = Mockito.mock(MailComposeTemplateService::class.java)
    private val statusHistoryRepository = Mockito.mock(ExpertContactStatusHistoryRepository::class.java)
    private val conversationStateService = ConversationStateService(expertContactRepository, statusHistoryRepository)

    private val service = MeetingScheduleService(
        meetingScheduleRepository = meetingScheduleRepository,
        expertContactRepository = expertContactRepository,
        mailRecordRepository = mailRecordRepository,
        mailSenderAccountRepository = mailSenderAccountRepository,
        mailSenderAccountService = mailSenderAccountService,
        mailDeliveryService = mailDeliveryService,
        mailComposeTemplateService = mailComposeTemplateService,
        conversationStateService = conversationStateService
    )

    @Test
    fun `extractAndCreate parses meeting tool and availability`() {
        val contactId = 1L
        val mailRecord = MailRecord(
            id = 100L,
            expertContactId = contactId,
            direction = "INBOUND",
            mailType = "REPLY",
            messageId = "msg-in",
            inReplyTo = null,
            subject = "Re: Meeting",
            body = "Hi, I am available next Tuesday at 10 AM. We can discuss via Zoom.",
            cleanedBody = "Hi, I am available next Tuesday at 10 AM. We can discuss via Zoom.",
            matchedQaRuleId = null,
            sendStatus = null,
            receivedAt = LocalDateTime.now(),
            sentAt = null
        )

        Mockito.`when`(meetingScheduleRepository.save(Mockito.any(MeetingSchedule::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<MeetingSchedule>(0).copy(id = 500L) }

        val schedule = service.extractAndCreate(contactId, mailRecord)

        assertNotNull(schedule)
        assertEquals(500L, schedule.id)
        assertEquals(contactId, schedule.expertContactId)
        assertEquals("Zoom", schedule.meetingTool)
        assertEquals("Hi, I am available next Tuesday at 10 AM. We can discuss via Zoom.", schedule.expertAvailableText)
        assertEquals("PENDING", schedule.meetingStatus)
    }

    @Test
    fun `confirmMeetingAndEmail updates schedule and sends mail`() {
        val contactId = 1L
        val scheduleId = 500L
        val contact = ExpertContact(
            id = contactId,
            campaignId = 10L,
            orcidId = "orcid-1",
            expertEmail = "expert@example.com",
            expertName = "Dr. Expert",
            currentStatus = ConversationStatus.MEETING_SCHEDULING.name
        )
        val schedule = MeetingSchedule(
            id = scheduleId,
            expertContactId = contactId,
            meetingStatus = "PENDING"
        )
        val account = MailSenderAccount(
            accountCode = "sender",
            senderEmail = "sender@example.com",
            senderName = "Sender",
            senderTitle = "Recruiter",
            senderDisplayName = "Sender Display",
            teamName = "HR Team",
            countryName = "China",
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "sender@example.com",
            smtpPassword = "pwd",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "sender@example.com",
            imapPassword = "pwd"
        )

        val expectedSeed = MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)
        Mockito.`when`(expertContactRepository.findById(contactId)).thenReturn(Optional.of(contact))
        Mockito.`when`(meetingScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule))
        Mockito.`when`(meetingScheduleRepository.save(Mockito.any(MeetingSchedule::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<MeetingSchedule>(0) }
        Mockito.`when`(mailSenderAccountService.selectAccountForSending()).thenReturn(account)
        Mockito.`when`(
            mailComposeTemplateService.renderByCode(
                eqValue("MEETING_CONFIRMATION"),
                anyValue(emptyMap<String, String>()),
                eqValue(expectedSeed)
            )
        ).thenReturn(
            ComposeTemplateRenderResult(
                subject = "Meeting Confirmed",
                body = "Confirmed meeting link",
                mailType = "MEETING_CONFIRMATION"
            )
        )
        val sentMails = mutableListOf<ComposedMail>()
        Mockito.`when`(
            mailDeliveryService.send(
                eqValue(account),
                anyValue(ComposedMail(to = "stub@example.com", subject = "Stub", body = "Stub"))
            )
        ).thenAnswer { invocation ->
            sentMails.add(invocation.getArgument(1))
            DeliveredMail("msg-123", "SENT")
        }
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertContact>(0) }
        Mockito.`when`(mailSenderAccountRepository.save(Mockito.any(MailSenderAccount::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<MailSenderAccount>(0) }

        val confirmed = service.confirmMeetingAndEmail(
            contactId = contactId,
            scheduleId = scheduleId,
            command = ConfirmMeetingCommand(
                chinaTime = "2026-06-01 10:00 AM",
                meetingTool = "Teams",
                meetingLink = "https://teams.microsoft.com/123",
                note = "Test notes"
            )
        )

        assertEquals("CONFIRMED", confirmed.meetingStatus)
        assertEquals("2026-06-01 10:00 AM", confirmed.chinaTime)
        assertEquals("Teams", confirmed.meetingTool)
        assertEquals("https://teams.microsoft.com/123", confirmed.meetingLink)
        assertEquals("Test notes", confirmed.note)

        Mockito.verify(mailComposeTemplateService).renderByCode(
            eqValue("MEETING_CONFIRMATION"),
            anyValue(emptyMap<String, String>()),
            eqValue(expectedSeed)
        )

        val sentMail = sentMails.single()
        assertNotNull(sentMail.messageId)
        // I-2: domain must come from the stub account's senderEmail, not any hardcoded literal
        val accountDomain = account.senderEmail.substringAfter("@")
        assertTrue(
            sentMail.messageId!!.matches(
                Regex("^<meeting-confirmation-orcid-1-[0-9a-f-]{36}@${Regex.escape(accountDomain)}>$")
            ),
            "unexpected messageId: ${sentMail.messageId}"
        )

        val mailRecordCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository).save(mailRecordCaptor.capture())
        assertEquals("MEETING_CONFIRMATION", mailRecordCaptor.value.mailType)
        assertEquals("OUTBOUND", mailRecordCaptor.value.direction)
        assertEquals("msg-123", mailRecordCaptor.value.messageId)

        val contactCaptor = ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(expertContactRepository).save(contactCaptor.capture())
        assertEquals(ConversationStatus.MEETING_SCHEDULED.name, contactCaptor.value.currentStatus)

        val accountCaptor = ArgumentCaptor.forClass(MailSenderAccount::class.java)
        Mockito.verify(mailSenderAccountRepository).save(accountCaptor.capture())
        assertEquals(1, accountCaptor.value.todaySentCount)
        assertNotNull(accountCaptor.value.lastSentAt)
    }

    @Test
    fun `updateSchedule rejects schedule from another contact`() {
        val scheduleId = 500L
        Mockito.`when`(meetingScheduleRepository.findById(scheduleId)).thenReturn(
            Optional.of(MeetingSchedule(id = scheduleId, expertContactId = 2L, meetingStatus = "PENDING"))
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.updateSchedule(
                contactId = 1L,
                scheduleId = scheduleId,
                command = UpdateMeetingCommand(
                    expertAvailableText = null,
                    expertTimezone = null,
                    chinaTime = "2026-06-01 10:00 AM",
                    meetingTool = "Zoom",
                    meetingLink = "https://zoom.example/1",
                    note = null
                )
            )
        }
    }

    @Test
    fun `completeMeeting updates status and transitions contact`() {
        val contactId = 1L
        val scheduleId = 500L
        val contact = ExpertContact(
            id = contactId,
            campaignId = 10L,
            orcidId = "orcid-1",
            expertEmail = "expert@example.com",
            expertName = "Dr. Expert",
            currentStatus = ConversationStatus.MEETING_SCHEDULED.name
        )
        val schedule = MeetingSchedule(
            id = scheduleId,
            expertContactId = contactId,
            meetingStatus = "CONFIRMED"
        )

        Mockito.`when`(expertContactRepository.findById(contactId)).thenReturn(Optional.of(contact))
        Mockito.`when`(meetingScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule))
        Mockito.`when`(meetingScheduleRepository.save(Mockito.any(MeetingSchedule::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<MeetingSchedule>(0) }
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertContact>(0) }

        val completed = service.completeMeeting(contactId, scheduleId)

        assertEquals("COMPLETED", completed.meetingStatus)
        val contactCaptor = ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(expertContactRepository).save(contactCaptor.capture())
        assertEquals(ConversationStatus.MEETING_DONE.name, contactCaptor.value.currentStatus)
    }

    @Test
    fun `cancelMeeting updates status and resets contact status`() {
        val contactId = 1L
        val scheduleId = 500L
        val contact = ExpertContact(
            id = contactId,
            campaignId = 10L,
            orcidId = "orcid-1",
            expertEmail = "expert@example.com",
            expertName = "Dr. Expert",
            currentStatus = ConversationStatus.MEETING_SCHEDULED.name
        )
        val schedule = MeetingSchedule(
            id = scheduleId,
            expertContactId = contactId,
            meetingStatus = "CONFIRMED"
        )

        Mockito.`when`(expertContactRepository.findById(contactId)).thenReturn(Optional.of(contact))
        Mockito.`when`(meetingScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule))
        Mockito.`when`(meetingScheduleRepository.save(Mockito.any(MeetingSchedule::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<MeetingSchedule>(0) }
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertContact>(0) }

        val cancelled = service.cancelMeeting(contactId, scheduleId)

        assertEquals("CANCELLED", cancelled.meetingStatus)
        val contactCaptor = ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(expertContactRepository).save(contactCaptor.capture())
        assertEquals(ConversationStatus.MEETING_SCHEDULING.name, contactCaptor.value.currentStatus)
    }

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    private fun <T> eqValue(value: T): T =
        Mockito.eq(value) ?: value
}
