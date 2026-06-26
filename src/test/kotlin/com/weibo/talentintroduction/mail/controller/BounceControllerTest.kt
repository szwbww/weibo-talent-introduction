package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.domain.BounceRecord
import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.service.BounceBackfillService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime

class BounceControllerTest {
    private val bounceRecordRepository = Mockito.mock(BounceRecordRepository::class.java)
    private val bounceBackfillService = Mockito.mock(BounceBackfillService::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val operatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)

    private val controller = BounceController(
        bounceRecordRepository,
        bounceBackfillService,
        expertContactRepository,
        operatorActionLogService
    )

    @Test
    fun `listBounces returns persisted failedRecipient when expert is not linked`() {
        val record = BounceRecord(
            id = 1L,
            senderAccountCode = "acc1",
            bounceMessageId = "bounce-1@example.com",
            originalMessageId = null,
            originalExpertContactId = null,
            failedRecipient = "unknown@example.com",
            bounceType = "HARD",
            dsnStatus = "5.1.1",
            bounceReason = "Undelivered",
            receivedAt = LocalDateTime.of(2026, 6, 26, 12, 0)
        )
        Mockito.`when`(bounceRecordRepository.findPaged(null, null, 20, 0)).thenReturn(listOf(record))
        Mockito.`when`(bounceRecordRepository.countPaged(null, null)).thenReturn(1L)

        val response = controller.listBounces(null, null, 20, 0)

        assertEquals(1, response.totalCount)
        assertEquals("unknown@example.com", response.records.single().failedRecipient)
        assertEquals(null, response.records.single().originalExpertContactId)
    }
}
