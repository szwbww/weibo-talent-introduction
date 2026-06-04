package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional

class ExpertIndexLevelOperationServiceTest {
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val expertIndexWriterService = Mockito.mock(ExpertIndexWriterService::class.java)
    private val operatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)
    private val service = ExpertIndexLevelOperationService(
        expertContactRepository,
        expertIndexWriterService,
        operatorActionLogService
    )

    private fun contact(indexLevel: String = "CANDIDATE", applicationIndexed: Boolean = false): ExpertContact =
        ExpertContact(
            id = 1L,
            campaignId = 10L,
            orcidId = "0000-0001",
            expertEmail = "expert@example.com",
            expertName = "Expert",
            currentStatus = ConversationStatus.WAITING_REPLY.name,
            applicationIndexed = applicationIndexed,
            currentIndexLevel = indexLevel,
            firstReplyAt = LocalDateTime.of(2026, 1, 1, 0, 0)
        )

    private fun stubSave() {
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { invocation -> invocation.arguments[0] as ExpertContact }
    }

    @Test
    fun `changeLevel RAW to CANDIDATE promotes via ES`() {
        val c = contact("RAW")
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(c))
        Mockito.`when`(expertIndexWriterService.promoteToCandidate("0000-0001", c)).thenReturn(true)
        stubSave()

        val result = service.changeLevel(1L, "CANDIDATE", "admin", "promoted")

        assertEquals("CANDIDATE", result.currentIndexLevel)
    }

    @Test
    fun `changeLevel CANDIDATE to APPLICATION promotes in ES`() {
        val c = contact("CANDIDATE", applicationIndexed = false)
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(c))
        val expectedInstant = c.firstReplyAt!!.toInstant(ZoneId.systemDefault().rules.getOffset(c.firstReplyAt!!))
        Mockito.`when`(expertIndexWriterService.promoteToApplication("0000-0001", c, expectedInstant, null, "OPERATOR", null))
            .thenReturn(true)
        stubSave()

        val result = service.changeLevel(1L, "APPLICATION", null, null)

        assertEquals("APPLICATION", result.currentIndexLevel)
        assertTrue(result.applicationIndexed)
    }

    @Test
    fun `changeLevel APPLICATION to RAW demotes in ES`() {
        val c = contact("APPLICATION", applicationIndexed = true)
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(c))
        Mockito.`when`(expertIndexWriterService.demoteToRaw("0000-0001", c)).thenReturn(true)
        stubSave()

        val result = service.changeLevel(1L, "RAW", null, null)

        assertEquals("RAW", result.currentIndexLevel)
        assertFalse(result.applicationIndexed)
    }

    @Test
    fun `changeLevel APPLICATION to CANDIDATE rejects`() {
        val c = contact("APPLICATION", applicationIndexed = true)
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(c))

        var caught = false
        try {
            service.changeLevel(1L, "CANDIDATE", null, null)
        } catch (e: IllegalStateException) {
            caught = true
        }
        assertTrue(caught)
    }

    @Test
    fun `changeLevel same level returns unchanged without audit log`() {
        val c = contact("CANDIDATE")
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(c))

        val result = service.changeLevel(1L, "CANDIDATE", null, null)

        assertEquals("CANDIDATE", result.currentIndexLevel)
        Mockito.verifyNoInteractions(operatorActionLogService)
    }

    @Test
    fun `changeLevel CANDIDATE to RAW demotes via ES`() {
        val c = contact("CANDIDATE")
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(c))
        Mockito.`when`(expertIndexWriterService.demoteToRaw("0000-0001", c)).thenReturn(true)
        stubSave()

        val result = service.changeLevel(1L, "RAW", null, null)

        assertEquals("RAW", result.currentIndexLevel)
        assertFalse(result.applicationIndexed)
    }
}