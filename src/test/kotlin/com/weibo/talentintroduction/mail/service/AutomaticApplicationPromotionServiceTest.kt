package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.OperatorStatus
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime

class AutomaticApplicationPromotionServiceTest {
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val expertIndexWriterService = Mockito.mock(ExpertIndexWriterService::class.java)
    private val expertOperatorStatusService = Mockito.mock(ExpertOperatorStatusService::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val service = AutomaticApplicationPromotionService(
        expertContactRepository,
        expertIndexWriterService,
        expertOperatorStatusService,
        mailRecordRepository
    )

    private val now = LocalDateTime.of(2026, 6, 4, 10, 0)
    private val contact = ExpertContact(
        id = 1,
        campaignId = 1,
        orcidId = "ORCID-001",
        expertEmail = "expert@example.com",
        expertName = "Expert",
        currentStatus = "INTRO_SENT",
        operatorStatus = "CONTACTED"
    )

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    @Test
    fun `reply count 1 does not promote`() {
        Mockito.`when`(mailRecordRepository.countInboundReplies(anyValue(1L))).thenReturn(1)

        val result = service.promoteByReplyCountIfNeeded(contact, now, 100L)

        assertEquals(contact, result)
        assertFalse(result.applicationIndexed)
        Mockito.verifyNoInteractions(expertIndexWriterService, expertContactRepository, expertOperatorStatusService)
    }

    @Test
    fun `reply count 2 does not promote`() {
        Mockito.`when`(mailRecordRepository.countInboundReplies(anyValue(1L))).thenReturn(2)

        val result = service.promoteByReplyCountIfNeeded(contact, now, 100L)

        assertEquals(contact, result)
        assertFalse(result.applicationIndexed)
    }

    @Test
    fun `reply count 3 triggers promotion`() {
        Mockito.`when`(mailRecordRepository.countInboundReplies(anyValue(1L))).thenReturn(3)
        Mockito.`when`(expertIndexWriterService.promoteToApplication(
            anyValue(""), anyValue(contact), anyValue(java.time.Instant.now()),
            anyValue(null) as Long?, anyValue(""), anyValue(null) as String?
        )).thenReturn(true)
        Mockito.`when`(expertContactRepository.save(anyValue(contact))).thenAnswer {
            it.getArgument<ExpertContact>(0)
        }
        Mockito.`when`(expertOperatorStatusService.updateAutomatically(
            anyValue(contact), anyValue(OperatorStatus.REPLIED), anyValue("")
        )).thenAnswer {
            val c = it.getArgument<ExpertContact>(0)
            c.copy(operatorStatus = OperatorStatus.REPLIED.name)
        }

        val result = service.promoteByReplyCountIfNeeded(contact, now, 100L)

        assertTrue(result.applicationIndexed)
        assertEquals("APPLICATION", result.currentIndexLevel)
        assertEquals(OperatorStatus.REPLIED.name, result.operatorStatus)
    }

    @Test
    fun `already indexed skips reply count promotion`() {
        val indexed = contact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION")

        val result = service.promoteByReplyCountIfNeeded(indexed, now, 100L)

        assertEquals(indexed, result)
        Mockito.verifyNoInteractions(expertIndexWriterService, mailRecordRepository)
    }

    @Test
    fun `promotion failure does not mark application indexed`() {
        Mockito.`when`(mailRecordRepository.countInboundReplies(anyValue(1L))).thenReturn(3)
        Mockito.`when`(expertIndexWriterService.promoteToApplication(
            anyValue(""), anyValue(contact), anyValue(java.time.Instant.now()),
            anyValue(null) as Long?, anyValue(""), anyValue(null) as String?
        )).thenReturn(false)

        val result = service.promoteByReplyCountIfNeeded(contact, now, 100L)

        assertFalse(result.applicationIndexed)
        Mockito.verify(expertContactRepository, Mockito.never()).save(anyValue(contact))
    }

    @Test
    fun `material with zero documents does nothing`() {
        val result = service.promoteByMaterialIfNeeded(contact, now, 100L, 0)

        assertEquals(contact, result)
        Mockito.verifyNoInteractions(expertOperatorStatusService, expertIndexWriterService)
    }

    @Test
    fun `material triggers promotion and sets MATERIALS_RECEIVED`() {
        Mockito.`when`(expertOperatorStatusService.updateAutomatically(
            anyValue(contact), anyValue(OperatorStatus.MATERIALS_RECEIVED), anyValue("")
        )).thenAnswer {
            val c = it.getArgument<ExpertContact>(0)
            c.copy(operatorStatus = OperatorStatus.MATERIALS_RECEIVED.name)
        }
        Mockito.`when`(expertIndexWriterService.promoteToApplication(
            anyValue(""), anyValue(contact), anyValue(java.time.Instant.now()),
            anyValue(null) as Long?, anyValue(""), anyValue(null) as String?
        )).thenReturn(true)
        Mockito.`when`(expertContactRepository.save(anyValue(contact))).thenAnswer {
            it.getArgument<ExpertContact>(0)
        }

        val result = service.promoteByMaterialIfNeeded(contact, now, 100L, 1)

        assertTrue(result.applicationIndexed)
        assertEquals("APPLICATION", result.currentIndexLevel)
        assertEquals(OperatorStatus.MATERIALS_RECEIVED.name, result.operatorStatus)
    }

    @Test
    fun `material sets MATERIALS_RECEIVED even when already in application`() {
        val indexed = contact.copy(
            applicationIndexed = true,
            currentIndexLevel = "APPLICATION",
            operatorStatus = "REPLIED"
        )
        Mockito.`when`(expertOperatorStatusService.updateAutomatically(
            anyValue(indexed), anyValue(OperatorStatus.MATERIALS_RECEIVED), anyValue("")
        )).thenReturn(indexed.copy(operatorStatus = OperatorStatus.MATERIALS_RECEIVED.name))

        val result = service.promoteByMaterialIfNeeded(indexed, now, 100L, 1)

        assertEquals(OperatorStatus.MATERIALS_RECEIVED.name, result.operatorStatus)
        assertTrue(result.applicationIndexed)
        Mockito.verify(expertIndexWriterService, Mockito.never()).promoteToApplication(
            anyValue(""), anyValue(indexed), anyValue(java.time.Instant.now()),
            anyValue(null) as Long?, anyValue(""), anyValue(null) as String?
        )
    }

    @Test
    fun `material promotion failure still sets MATERIALS_RECEIVED`() {
        Mockito.`when`(expertOperatorStatusService.updateAutomatically(
            anyValue(contact), anyValue(OperatorStatus.MATERIALS_RECEIVED), anyValue("")
        )).thenAnswer {
            val c = it.getArgument<ExpertContact>(0)
            c.copy(operatorStatus = OperatorStatus.MATERIALS_RECEIVED.name)
        }
        Mockito.`when`(expertIndexWriterService.promoteToApplication(
            anyValue(""), anyValue(contact), anyValue(java.time.Instant.now()),
            anyValue(null) as Long?, anyValue(""), anyValue(null) as String?
        )).thenReturn(false)

        val result = service.promoteByMaterialIfNeeded(contact, now, 100L, 1)

        assertEquals(OperatorStatus.MATERIALS_RECEIVED.name, result.operatorStatus)
        assertFalse(result.applicationIndexed)
    }

    @Test
    fun `material with blank orcid updates status but skips promotion`() {
        val noOrcid = contact.copy(orcidId = "")
        Mockito.`when`(expertOperatorStatusService.updateAutomatically(
            anyValue(noOrcid), anyValue(OperatorStatus.MATERIALS_RECEIVED), anyValue("")
        )).thenReturn(noOrcid.copy(operatorStatus = OperatorStatus.MATERIALS_RECEIVED.name))

        val result = service.promoteByMaterialIfNeeded(noOrcid, now, 100L, 1)

        assertEquals(OperatorStatus.MATERIALS_RECEIVED.name, result.operatorStatus)
        Mockito.verify(expertIndexWriterService, Mockito.never()).promoteToApplication(
            anyValue(""), anyValue(noOrcid), anyValue(java.time.Instant.now()),
            anyValue(null) as Long?, anyValue(""), anyValue(null) as String?
        )
    }
}
