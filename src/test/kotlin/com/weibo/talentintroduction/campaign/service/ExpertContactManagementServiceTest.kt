package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.ExpertContactStatusHistory
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactStatusHistoryRepository
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.document.repository.ExpertDocumentRepository
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.handoff.domain.ManualHandoff
import com.weibo.talentintroduction.handoff.repository.ManualHandoffRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

class ExpertContactManagementServiceTest {
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val manualHandoffRepository = Mockito.mock(ManualHandoffRepository::class.java)
    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
    private val expertDocumentRepository = Mockito.mock(ExpertDocumentRepository::class.java)
    private val statusHistoryRepository = Mockito.mock(ExpertContactStatusHistoryRepository::class.java)
    private val meetingScheduleRepository = Mockito.mock(com.weibo.talentintroduction.campaign.repository.MeetingScheduleRepository::class.java)
    private val expertIndexWriterService = Mockito.mock(ExpertIndexWriterService::class.java)
    private val conversationStateService = ConversationStateService(expertContactRepository, statusHistoryRepository)
    private val service = ExpertContactManagementService(
        expertContactRepository,
        mailRecordRepository,
        manualHandoffRepository,
        mailAttachmentRepository,
        expertDocumentRepository,
        statusHistoryRepository,
        conversationStateService,
        meetingScheduleRepository,
        expertIndexWriterService
    )

    @Test
    fun `creates manual handoff and marks contact`() {
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(manualHandoffRepository.save(Mockito.any(ManualHandoff::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as ManualHandoff }
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as ExpertContact }

        val handoff = service.createManualHandoff(
            1L,
            ManualHandoffCreateCommand(
                reason = "No QA rule matched",
                assignedTo = "operator",
                note = "Please review"
            )
        )

        assertEquals("PENDING", handoff.handoffStatus)
        Mockito.verify(expertContactRepository).save(
            Mockito.argThat { saved ->
                saved.currentStatus == ConversationStatus.MANUAL_HANDOFF.name && saved.manualHandoffRequired
            }
        )
        Mockito.verify(statusHistoryRepository).save(Mockito.any(ExpertContactStatusHistory::class.java))
    }

    @Test
    fun `assigns latest open handoff`() {
        Mockito.`when`(
            manualHandoffRepository.findFirstByExpertContactIdAndHandoffStatusInOrderByUpdatedAtDesc(1L, listOf("PENDING", "ASSIGNED"))
        ).thenReturn(handoff(status = "PENDING"))
        Mockito.`when`(manualHandoffRepository.save(Mockito.any(ManualHandoff::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as ManualHandoff }

        val assigned = service.assignHandoff(
            1L,
            ManualHandoffAssignCommand(assignedTo = "zoe", note = "assigned")
        )

        assertEquals("ASSIGNED", assigned.handoffStatus)
        assertEquals("zoe", assigned.assignedTo)
    }

    @Test
    fun `completes latest open handoff and clears manual flag`() {
        Mockito.`when`(
            manualHandoffRepository.findFirstByExpertContactIdAndHandoffStatusInOrderByUpdatedAtDesc(1L, listOf("PENDING", "ASSIGNED"))
        ).thenReturn(handoff(status = "ASSIGNED"))
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(manualHandoffRepository.save(Mockito.any(ManualHandoff::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as ManualHandoff }
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as ExpertContact }

        val completed = service.completeHandoff(
            1L,
            ManualHandoffCompleteCommand(nextStatus = ConversationStatus.WAITING_REPLY.name, note = "done")
        )

        assertEquals("COMPLETED", completed.handoffStatus)
        Mockito.verify(expertContactRepository).save(
            Mockito.argThat { saved ->
                !saved.manualHandoffRequired && saved.currentStatus == ConversationStatus.WAITING_REPLY.name
            }
        )
        Mockito.verify(statusHistoryRepository).save(Mockito.any(ExpertContactStatusHistory::class.java))
    }

    @Test
    fun `completes correct open handoff when duplicate tickets exist`() {
        Mockito.`when`(
            manualHandoffRepository.findFirstByExpertContactIdAndHandoffStatusInOrderByUpdatedAtDesc(1L, listOf("PENDING", "ASSIGNED"))
        ).thenReturn(
            ManualHandoff(
                id = 1L,
                expertContactId = 1L,
                reason = "QA miss",
                handoffStatus = "PENDING",
                assignedTo = null,
                note = "old open ticket",
                createdAt = LocalDateTime.of(2026, 6, 1, 10, 0),
                updatedAt = LocalDateTime.of(2026, 6, 1, 10, 0)
            )
        )
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(manualHandoffRepository.save(Mockito.any(ManualHandoff::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as ManualHandoff }
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as ExpertContact }

        val completed = service.completeHandoff(
            1L,
            ManualHandoffCompleteCommand(nextStatus = ConversationStatus.WAITING_REPLY.name, note = null)
        )

        assertEquals(1L, completed.id)
        assertEquals("COMPLETED", completed.handoffStatus)
        assertEquals("old open ticket", completed.note)
    }

    @Test
    fun `fails completion when no open handoff exists`() {
        Mockito.`when`(
            manualHandoffRepository.findFirstByExpertContactIdAndHandoffStatusInOrderByUpdatedAtDesc(1L, listOf("PENDING", "ASSIGNED"))
        ).thenReturn(null)

        assertThrows(java.lang.IllegalStateException::class.java) {
            service.completeHandoff(
                1L,
                ManualHandoffCompleteCommand(nextStatus = ConversationStatus.WAITING_REPLY.name, note = "done")
            )
        }
    }

    @Test
    fun `closes contact`() {
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as ExpertContact }

        val closed = service.closeContact(1L, "Expert declined")

        assertEquals(ConversationStatus.CLOSED.name, closed.currentStatus)
        assertFalse(closed.manualHandoffRequired)
        assertEquals("Expert declined", closed.closedReason)
        Mockito.verify(statusHistoryRepository).save(Mockito.any(ExpertContactStatusHistory::class.java))
    }

    private fun contact(): ExpertContact =
        ExpertContact(
            id = 1L,
            campaignId = 10L,
            orcidId = "0000-0001",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            currentStatus = ConversationStatus.MANUAL_HANDOFF.name,
            manualHandoffRequired = true
        )

    private fun handoff(status: String): ManualHandoff =
        ManualHandoff(
            id = 2L,
            expertContactId = 1L,
            reason = "Need manual review",
            handoffStatus = status,
            assignedTo = null,
            note = null
        )
}
