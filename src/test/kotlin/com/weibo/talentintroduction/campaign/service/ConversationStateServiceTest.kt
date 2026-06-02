package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.ExpertContactStatusHistory
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactStatusHistoryRepository
import com.weibo.talentintroduction.common.domain.ConversationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

class ConversationStateServiceTest {
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val statusHistoryRepository = Mockito.mock(ExpertContactStatusHistoryRepository::class.java)
    private val service = ConversationStateService(expertContactRepository, statusHistoryRepository)

    @Test
    fun `records status history when status changes`() {
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertContact>(0) }
        Mockito.`when`(statusHistoryRepository.save(Mockito.any(ExpertContactStatusHistory::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertContactStatusHistory>(0) }

        val saved = service.transition(
            contact = contact(ConversationStatus.INTRO_SENT),
            toStatus = ConversationStatus.MEETING_SCHEDULING,
            reason = "CONFIRM_MEETING",
            source = "AUTO_REPLY"
        ) {
            it.copy(manualHandoffRequired = true)
        }

        assertEquals(ConversationStatus.MEETING_SCHEDULING.name, saved.currentStatus)
        assertEquals(true, saved.manualHandoffRequired)
        val historyCaptor = ArgumentCaptor.forClass(ExpertContactStatusHistory::class.java)
        Mockito.verify(statusHistoryRepository).save(historyCaptor.capture())
        assertEquals(ConversationStatus.INTRO_SENT.name, historyCaptor.value.fromStatus)
        assertEquals(ConversationStatus.MEETING_SCHEDULING.name, historyCaptor.value.toStatus)
        assertEquals("CONFIRM_MEETING", historyCaptor.value.reason)
        assertEquals("AUTO_REPLY", historyCaptor.value.source)
    }

    @Test
    fun `records status history even when status is unchanged with fromStatus null`() {
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertContact>(0) }
        Mockito.`when`(statusHistoryRepository.save(Mockito.any(ExpertContactStatusHistory::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ExpertContactStatusHistory>(0) }

        service.transition(
            contact = contact(ConversationStatus.MATERIALS_PARTIAL),
            toStatus = ConversationStatus.MATERIALS_PARTIAL,
            reason = "REVIEW_DOCUMENT",
            source = "AUTO_REPLY"
        )

        val historyCaptor = ArgumentCaptor.forClass(ExpertContactStatusHistory::class.java)
        Mockito.verify(statusHistoryRepository).save(historyCaptor.capture())
        assertEquals(null, historyCaptor.value.fromStatus)
        assertEquals(ConversationStatus.MATERIALS_PARTIAL.name, historyCaptor.value.toStatus)
        assertEquals("REVIEW_DOCUMENT", historyCaptor.value.reason)
    }

    @Test
    fun `returns recommended next action for material state`() {
        val action = service.recommendedNextAction(ConversationStatus.MATERIALS_PARTIAL.name, false)

        assertEquals("审核已收到材料，并提醒补充缺失材料。", action)
    }

    private fun contact(status: ConversationStatus): ExpertContact =
        ExpertContact(
            id = 1L,
            campaignId = 10L,
            orcidId = "0000-0001",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            currentStatus = status.name
        )
}
