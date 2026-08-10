package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.MeetingSchedule
import com.weibo.talentintroduction.campaign.repository.MeetingScheduleRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.MailDeliveryService
import com.weibo.talentintroduction.mail.service.ComposedMail
import com.weibo.talentintroduction.mail.service.OutboundMessageIdFactory
import com.weibo.talentintroduction.mail.service.SenderAccountBindingService
import com.weibo.talentintroduction.mail.service.SenderAccountNotBoundException
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.common.domain.ConversationStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class MeetingScheduleService(
    private val meetingScheduleRepository: MeetingScheduleRepository,
    private val expertContactRepository: ExpertContactRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val mailSenderAccountRepository: MailSenderAccountRepository,
    private val mailSenderAccountService: MailSenderAccountService,
    private val senderAccountBindingService: SenderAccountBindingService,
    private val mailDeliveryService: MailDeliveryService,
    private val mailComposeTemplateService: MailComposeTemplateService,
    private val conversationStateService: ConversationStateService
) {
    fun extractAndCreate(contactId: Long, mailRecord: MailRecord): MeetingSchedule {
        val body = mailRecord.cleanedBody ?: ""
        val tool = inferMeetingTool(body)
        val availability = extractAvailabilityText(body)

        val now = LocalDateTime.now()
        return meetingScheduleRepository.save(
            MeetingSchedule(
                expertContactId = contactId,
                sourceMailRecordId = mailRecord.id,
                expertAvailableText = availability,
                meetingTool = tool,
                meetingStatus = "PENDING",
                createdAt = now,
                updatedAt = now
            )
        )
    }

    fun createManual(contactId: Long, command: CreateMeetingCommand): MeetingSchedule {
        val now = LocalDateTime.now()
        return meetingScheduleRepository.save(
            MeetingSchedule(
                expertContactId = contactId,
                expertAvailableText = command.expertAvailableText,
                expertTimezone = command.expertTimezone,
                chinaTime = command.chinaTime,
                meetingTool = command.meetingTool,
                meetingLink = command.meetingLink,
                meetingStatus = "PENDING",
                note = command.note,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    fun updateSchedule(contactId: Long, scheduleId: Long, command: UpdateMeetingCommand): MeetingSchedule {
        val existing = findOwnedSchedule(contactId, scheduleId)

        return meetingScheduleRepository.save(
            existing.copy(
                expertAvailableText = command.expertAvailableText ?: existing.expertAvailableText,
                expertTimezone = command.expertTimezone ?: existing.expertTimezone,
                chinaTime = command.chinaTime ?: existing.chinaTime,
                meetingTool = command.meetingTool ?: existing.meetingTool,
                meetingLink = command.meetingLink ?: existing.meetingLink,
                meetingStatus = existing.meetingStatus,
                note = command.note ?: existing.note,
                updatedAt = LocalDateTime.now()
            )
        )
    }

    @Transactional
    fun confirmMeetingAndEmail(contactId: Long, scheduleId: Long, command: ConfirmMeetingCommand): MeetingSchedule {
        require(command.chinaTime.isNotBlank()) { "Meeting time is required" }
        require(command.meetingTool.isNotBlank()) { "Meeting tool is required" }
        require(command.meetingLink.isNotBlank()) { "Meeting link is required" }

        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }
        val schedule = findOwnedSchedule(contactId, scheduleId)
        require(schedule.meetingStatus == "PENDING") {
            "Only pending meeting schedules can be confirmed"
        }

        val updatedSchedule = meetingScheduleRepository.save(
            schedule.copy(
                chinaTime = command.chinaTime,
                meetingTool = command.meetingTool,
                meetingLink = command.meetingLink,
                meetingStatus = "CONFIRMED",
                note = command.note ?: schedule.note,
                updatedAt = LocalDateTime.now()
            )
        )

        val account = try {
            senderAccountBindingService.resolveForSend(contact, manual = true)
        } catch (e: SenderAccountNotBoundException) {
            val fallback = mailSenderAccountService.selectAccountForSending()
            senderAccountBindingService.bindIfAbsent(contactId, fallback.accountCode, LocalDateTime.now())
            fallback
        }
        val rendered = mailComposeTemplateService.renderByCode(
            templateCode = "MEETING_CONFIRMATION",
            variables = mapOf(
                "meetingTime" to command.chinaTime,
                "meetingTool" to command.meetingTool,
                "meetingLink" to command.meetingLink,
                "senderEmail" to account.senderEmail,
                "senderName" to account.senderName,
                "senderTitle" to account.senderTitle.orEmpty(),
                "teamName" to account.teamName.orEmpty(),
                "countryName" to account.countryName.orEmpty(),
                "senderDisplayName" to account.senderDisplayName.orEmpty()
            ),
            variantSeed = MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)
        )

        val composed = ComposedMail(
            to = contact.expertEmail,
            subject = rendered.subject,
            body = rendered.body,
            messageId = OutboundMessageIdFactory.newId("meeting-confirmation", contact.orcidId, account.senderEmail)
        )
        val delivered = mailDeliveryService.send(account, composed)
        val now = LocalDateTime.now()

        mailRecordRepository.save(
            MailRecord(
                expertContactId = contactId,
                direction = "OUTBOUND",
                mailType = "MEETING_CONFIRMATION",
                senderAccountCode = account.accountCode,
                triggeredBy = TriggeredBy.SYSTEM,
                sourceInboundId = schedule.sourceMailRecordId,
                messageId = delivered.messageId,
                inReplyTo = null,
                subject = composed.subject,
                body = composed.body,
                matchedQaRuleId = null,
                sendStatus = delivered.status,
                receivedAt = null,
                sentAt = now,
                createdAt = now
            )
        )

        mailSenderAccountRepository.save(
            account.copy(
                todaySentCount = account.todaySentCount + 1,
                lastSentAt = now
            )
        )

        conversationStateService.transition(
            contact = contact,
            toStatus = ConversationStatus.MEETING_SCHEDULED,
            reason = "CONFIRM_MEETING_SCHEDULE",
            source = "MANUAL",
            now = now
        ) {
            it.copy(
                manualHandoffRequired = false,
                lastMailAt = now
            )
        }

        return updatedSchedule
    }

    @Transactional
    fun completeMeeting(contactId: Long, scheduleId: Long): MeetingSchedule {
        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }
        val schedule = findOwnedSchedule(contactId, scheduleId)
        require(schedule.meetingStatus == "CONFIRMED") {
            "Only confirmed meeting schedules can be completed"
        }

        val updatedSchedule = meetingScheduleRepository.save(
            schedule.copy(
                meetingStatus = "COMPLETED",
                updatedAt = LocalDateTime.now()
            )
        )

        conversationStateService.transition(
            contact = contact,
            toStatus = ConversationStatus.MEETING_DONE,
            reason = "COMPLETE_MEETING",
            source = "MANUAL"
        )

        return updatedSchedule
    }

    @Transactional
    fun cancelMeeting(contactId: Long, scheduleId: Long): MeetingSchedule {
        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }
        val schedule = findOwnedSchedule(contactId, scheduleId)
        require(schedule.meetingStatus == "PENDING" || schedule.meetingStatus == "CONFIRMED") {
            "Only pending or confirmed meeting schedules can be cancelled"
        }

        val updatedSchedule = meetingScheduleRepository.save(
            schedule.copy(
                meetingStatus = "CANCELLED",
                updatedAt = LocalDateTime.now()
            )
        )

        conversationStateService.transition(
            contact = contact,
            toStatus = ConversationStatus.MEETING_SCHEDULING,
            reason = "CANCEL_MEETING_SCHEDULE",
            source = "MANUAL"
        )

        return updatedSchedule
    }

    private fun inferMeetingTool(body: String): String? {
        val lower = body.lowercase()
        return when {
            lower.contains("zoom") -> "Zoom"
            lower.contains("teams") || lower.contains("microsoft teams") -> "Teams"
            lower.contains("webex") -> "Webex"
            lower.contains("google meet") || lower.contains("meet.google") -> "Google Meet"
            else -> null
        }
    }

    private fun extractAvailabilityText(body: String): String {
        val lines = body.lines().map { it.trim() }.filter { it.isNotBlank() }
        val relevantLines = lines.filter { line ->
            val lower = line.lowercase()
            lower.contains("availab") || lower.contains("free") || lower.contains("time") ||
            lower.contains("date") || lower.contains("slot") || lower.contains("meet") ||
            lower.contains("zoom") || lower.contains("teams") || lower.contains("webex") ||
            lower.contains("clock") || lower.contains("hour") || lower.contains("morning") ||
            lower.contains("afternoon") || lower.contains("evening") || lower.contains("schedule")
        }
        return if (relevantLines.isNotEmpty()) {
            relevantLines.joinToString("\n").take(1000)
        } else {
            body.take(200)
        }
    }

    private fun findOwnedSchedule(contactId: Long, scheduleId: Long): MeetingSchedule {
        val schedule = meetingScheduleRepository.findById(scheduleId)
            .orElseThrow { error("Meeting schedule not found: $scheduleId") }
        require(schedule.expertContactId == contactId) {
            "Meeting schedule $scheduleId does not belong to expert contact $contactId"
        }
        return schedule
    }
}

data class CreateMeetingCommand(
    val expertAvailableText: String?,
    val expertTimezone: String?,
    val chinaTime: String?,
    val meetingTool: String?,
    val meetingLink: String?,
    val note: String?
)

data class UpdateMeetingCommand(
    val expertAvailableText: String?,
    val expertTimezone: String?,
    val chinaTime: String?,
    val meetingTool: String?,
    val meetingLink: String?,
    val note: String?
)

data class ConfirmMeetingCommand(
    val chinaTime: String,
    val meetingTool: String,
    val meetingLink: String,
    val note: String?
)
