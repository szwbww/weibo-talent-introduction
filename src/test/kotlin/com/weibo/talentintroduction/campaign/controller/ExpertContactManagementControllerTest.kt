package com.weibo.talentintroduction.campaign.controller

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.service.BulkAutoReplyResult
import com.weibo.talentintroduction.campaign.service.ExpertContactManagementService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

class ExpertContactManagementControllerTest {
    private val service = Mockito.mock(ExpertContactManagementService::class.java)
    private val controller = ExpertContactManagementController(
        service = service,
        manualExpertMailService = Mockito.mock(com.weibo.talentintroduction.mail.service.ManualExpertMailService::class.java),
        meetingScheduleService = Mockito.mock(com.weibo.talentintroduction.campaign.service.MeetingScheduleService::class.java),
        expertOperatorStatusService = Mockito.mock(com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService::class.java),
        expertIndexLevelOperationService = Mockito.mock(com.weibo.talentintroduction.campaign.service.ExpertIndexLevelOperationService::class.java)
    )

    @Test
    fun `bulk auto reply rejects missing operator name`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.bulkUpdateAutoReply(BulkAutoReplyRequest(enabled = true, operatorName = null))
        }
        assertEquals("operatorName is required", ex.message)
    }

    @Test
    fun `bulk auto reply rejects blank operator name`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.bulkUpdateAutoReply(BulkAutoReplyRequest(enabled = true, operatorName = "   "))
        }
        assertEquals("operatorName is required", ex.message)
    }

    @Test
    fun `bulk auto reply trims operator name`() {
        Mockito.`when`(service.bulkUpdateAutoReply(true, "admin"))
            .thenReturn(BulkAutoReplyResult(globalEnabled = true))

        val result = controller.bulkUpdateAutoReply(BulkAutoReplyRequest(enabled = true, operatorName = " admin "))

        assertEquals(true, result.globalEnabled)
        Mockito.verify(service).bulkUpdateAutoReply(true, "admin")
    }

    @Test
    fun `markFollowUp delegates to service`() {
        val contact = sampleContact().copy(followUpMarked = true, followUpMarkedAt = LocalDateTime.of(2026, 6, 30, 10, 0))
        Mockito.`when`(service.markFollowUp(1L)).thenReturn(contact)

        val response = controller.markFollowUp(1L)

        assertTrue(response.followUpMarked)
        assertEquals("2026-06-30T10:00", response.followUpMarkedAt)
        Mockito.verify(service).markFollowUp(1L)
    }

    @Test
    fun `unmarkFollowUp delegates to service`() {
        val contact = sampleContact()
        Mockito.`when`(service.unmarkFollowUp(1L)).thenReturn(contact)

        val response = controller.unmarkFollowUp(1L)

        assertFalse(response.followUpMarked)
        assertEquals(null, response.followUpMarkedAt)
        Mockito.verify(service).unmarkFollowUp(1L)
    }

    private fun sampleContact(): ExpertContact =
        ExpertContact(
            id = 1L,
            campaignId = 10L,
            orcidId = "0000-0001",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            currentStatus = "WAITING_REPLY"
        )
}
