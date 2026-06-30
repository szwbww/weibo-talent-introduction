package com.weibo.talentintroduction.campaign.controller

import com.weibo.talentintroduction.campaign.service.BulkAutoReplyResult
import com.weibo.talentintroduction.campaign.service.ExpertContactManagementService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito

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
}
