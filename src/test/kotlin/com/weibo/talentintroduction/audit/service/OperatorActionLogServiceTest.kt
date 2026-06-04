package com.weibo.talentintroduction.audit.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.audit.domain.OperatorActionLog
import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.repository.OperatorActionLogRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class OperatorActionLogServiceTest {
    private val operatorActionLogRepository = Mockito.mock(OperatorActionLogRepository::class.java)
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        Mockito.`when`(operatorActionLogRepository.save(Mockito.any(OperatorActionLog::class.java)))
            .thenAnswer { invocation ->
                val log = invocation.arguments[0] as OperatorActionLog
                log.copy(id = 1L)
            }
    }

    @Test
    fun `record writes target, actionType, operatorName, and note`() {
        val service = OperatorActionLogService(operatorActionLogRepository, objectMapper)
        val result = service.record(
            targetType = "EXPERT_CONTACT",
            targetId = 11L,
            actionType = OperatorActionType.CHANGE_OPERATOR_STATUS,
            expertContactId = 11L,
            operatorName = "admin",
            note = "人工确认"
        )
        assertEquals("EXPERT_CONTACT", result.targetType)
        assertEquals(11L, result.targetId)
        assertEquals(11L, result.expertContactId)
        assertEquals("CHANGE_OPERATOR_STATUS", result.actionType)
        assertEquals("变更专家状态", result.actionSummary)
        assertEquals("admin", result.operatorName)
        assertEquals("人工确认", result.note)
        assertNull(result.beforeValue)
        assertNull(result.afterValue)
    }

    @Test
    fun `record serializes before and after as JSON`() {
        val service = OperatorActionLogService(operatorActionLogRepository, objectMapper)
        val before = mapOf("operatorStatus" to "CONTACTED")
        val after = mapOf("operatorStatus" to "REPLIED")
        val result = service.record(
            targetType = "EXPERT_CONTACT",
            targetId = 11L,
            actionType = OperatorActionType.CHANGE_OPERATOR_STATUS,
            expertContactId = 11L,
            before = before,
            after = after
        )
        assertNotNull(result.beforeValue)
        assertNotNull(result.afterValue)
        assertTrue(result.beforeValue!!.contains("CONTACTED"))
        assertTrue(result.afterValue!!.contains("REPLIED"))
    }

    @Test
    fun `record uses summaryOverride when provided`() {
        val service = OperatorActionLogService(operatorActionLogRepository, objectMapper)
        val result = service.record(
            targetType = "EXPERT_CONTACT",
            targetId = 11L,
            actionType = OperatorActionType.CHANGE_OPERATOR_STATUS,
            expertContactId = 11L,
            summaryOverride = "自定义摘要"
        )
        assertEquals("自定义摘要", result.actionSummary)
    }

    @Test
    fun `record uses default summary when no override`() {
        val service = OperatorActionLogService(operatorActionLogRepository, objectMapper)
        val result = service.record(
            targetType = "EXPERT_CONTACT",
            targetId = 11L,
            actionType = OperatorActionType.SWITCH_REPLY_MODE,
            expertContactId = 11L
        )
        assertEquals("切换自动/人工回复", result.actionSummary)
    }

    @Test
    fun `search caps pageSize at MAX_PAGE_SIZE`() {
        Mockito.`when`(
            operatorActionLogRepository.search(
                null, null, null, null,
                null, null, 100, 0
            )
        ).thenReturn(emptyList())
        Mockito.`when`(
            operatorActionLogRepository.countSearch(
                null, null, null, null,
                null, null
            )
        ).thenReturn(0L)

        val service = OperatorActionLogService(operatorActionLogRepository, objectMapper)
        val (records, total) = service.search(
            expertContactId = null, inboundProcessingId = null,
            actionType = null, operatorName = null,
            start = null, end = null,
            pageSize = 500, pageOffset = 0
        )

        assertEquals(0, records.size)
        assertEquals(0L, total)
        Mockito.verify(operatorActionLogRepository).search(
            null, null, null, null,
            null, null, 100, 0
        )
    }

    @Test
    fun `record with null before and after stores null`() {
        val service = OperatorActionLogService(operatorActionLogRepository, objectMapper)
        val result = service.record(
            targetType = "INBOUND_MAIL_PROCESSING",
            targetId = 5L,
            actionType = OperatorActionType.MARK_INBOUND_RESOLVED,
            inboundProcessingId = 5L
        )
        assertNull(result.beforeValue)
        assertNull(result.afterValue)
    }
}