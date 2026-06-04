package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.audit.domain.OperatorActionLog
import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.OperatorStatus
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.common.domain.ConversationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.Optional

class ExpertOperatorStatusServiceTest {
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val operatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)
    private val service = ExpertOperatorStatusService(expertContactRepository, operatorActionLogService)

    private fun contact(operatorStatus: String = "NOT_CONTACTED"): ExpertContact =
        ExpertContact(
            id = 1L,
            campaignId = 10L,
            orcidId = "0000-0001",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            currentStatus = ConversationStatus.MANUAL_HANDOFF.name,
            manualHandoffRequired = true,
            operatorStatus = operatorStatus
        )

    @Test
    fun `changeStatus updates operatorStatus`() {
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact("NOT_CONTACTED")))
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as ExpertContact }

        val result = service.changeStatus(1L, "REPLIED", "admin", "manual verification")

        assertEquals("REPLIED", result.operatorStatus)
    }

    @Test
    fun `changeStatus throws for invalid status`() {
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact()))
        var caught = false
        try {
            service.changeStatus(1L, "INVALID_STATUS", null, null)
        } catch (e: IllegalStateException) {
            caught = true
        }
        assertTrue(caught)
    }

    @Test
    fun `updateAutomatically sets status when not COMPLETED`() {
        val c = contact("CONTACTED")
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as ExpertContact }

        val result = service.updateAutomatically(c, OperatorStatus.REPLIED, "inbound reply")

        assertEquals("REPLIED", result.operatorStatus)
    }

    @Test
    fun `updateAutomatically does not downgrade from COMPLETED`() {
        val c = contact("COMPLETED")

        val result = service.updateAutomatically(c, OperatorStatus.REPLIED, "should not downgrade")

        assertEquals("COMPLETED", result.operatorStatus)
        Mockito.verifyNoInteractions(expertContactRepository)
    }

    @Test
    fun `updateAutomatically allows MATERIALS_RECEIVED after INVITED`() {
        val c = contact("INVITED")
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as ExpertContact }

        val result = service.updateAutomatically(c, OperatorStatus.MATERIALS_RECEIVED, "materials arrived")

        assertEquals("MATERIALS_RECEIVED", result.operatorStatus)
    }
}