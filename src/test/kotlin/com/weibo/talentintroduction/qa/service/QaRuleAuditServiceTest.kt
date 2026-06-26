package com.weibo.talentintroduction.qa.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.audit.domain.OperatorActionLog
import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.repository.OperatorActionLogRepository
import com.weibo.talentintroduction.mail.domain.MailRecordQaRule
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime

class QaRuleAuditServiceTest {
    private val operatorActionLogRepository = Mockito.mock(OperatorActionLogRepository::class.java)
    private val mailRecordQaRuleRepository = Mockito.mock(MailRecordQaRuleRepository::class.java)
    private val objectMapper = ObjectMapper()
    private val service = QaRuleAuditService(
        operatorActionLogRepository,
        mailRecordQaRuleRepository,
        objectMapper
    )

    @Test
    fun `aggregates removed and added rule counts`() {
        val from = LocalDateTime.of(2026, 6, 1, 0, 0)
        val to = LocalDateTime.of(2026, 6, 30, 0, 0)
        val after = objectMapper.writeValueAsString(
            mapOf(
                "mailRecordId" to 100,
                "suggestedRuleIds" to listOf(1, 2, 3),
                "qaRuleIds" to listOf(2, 3, 4),
                "edited" to false
            )
        )
        stubLogs(from, to, after)
        Mockito.`when`(mailRecordQaRuleRepository.findByMailRecordIdOrderByOrdinalAsc(100L))
            .thenReturn(
                listOf(
                    MailRecordQaRule(mailRecordId = 100, qaRuleId = 2, ordinal = 0),
                    MailRecordQaRule(mailRecordId = 100, qaRuleId = 3, ordinal = 1),
                    MailRecordQaRule(mailRecordId = 100, qaRuleId = 4, ordinal = 2)
                )
            )

        val report = service.aggregateRuleUsage(from, to)

        assertEquals(1, report.totalActions)
        assertEquals(listOf(1L), report.removedRuleCounts.map { it.qaRuleId })
        assertEquals(1, report.removedRuleCounts.first().count)
        assertEquals(listOf(4L), report.addedRuleCounts.map { it.qaRuleId })
        assertEquals(1, report.addedRuleCounts.first().count)
        assertEquals(0, report.editedReplyCount)
    }

    @Test
    fun `selected rule ids prefer mail_record_qa_rule over log qaRuleIds`() {
        val from = LocalDateTime.of(2026, 6, 1, 0, 0)
        val to = LocalDateTime.of(2026, 6, 30, 0, 0)
        val after = objectMapper.writeValueAsString(
            mapOf(
                "mailRecordId" to 200,
                "suggestedRuleIds" to listOf(1, 2, 3),
                "qaRuleIds" to listOf(2, 3, 4),
                "edited" to false
            )
        )
        stubLogs(from, to, after)
        Mockito.`when`(mailRecordQaRuleRepository.findByMailRecordIdOrderByOrdinalAsc(200L))
            .thenReturn(
                listOf(
                    MailRecordQaRule(mailRecordId = 200, qaRuleId = 2, ordinal = 0),
                    MailRecordQaRule(mailRecordId = 200, qaRuleId = 4, ordinal = 1)
                )
            )

        val report = service.aggregateRuleUsage(from, to)

        assertEquals(listOf(1L, 3L), report.removedRuleCounts.map { it.qaRuleId }.sorted())
        assertEquals(listOf(4L), report.addedRuleCounts.map { it.qaRuleId })
    }

    @Test
    fun `falls back to log qaRuleIds when association table is empty`() {
        val from = LocalDateTime.of(2026, 6, 1, 0, 0)
        val to = LocalDateTime.of(2026, 6, 30, 0, 0)
        val after = objectMapper.writeValueAsString(
            mapOf(
                "mailRecordId" to 300,
                "suggestedRuleIds" to listOf(1, 2),
                "qaRuleIds" to listOf(2, 3),
                "edited" to false
            )
        )
        stubLogs(from, to, after)
        Mockito.`when`(mailRecordQaRuleRepository.findByMailRecordIdOrderByOrdinalAsc(300L))
            .thenReturn(emptyList())

        val report = service.aggregateRuleUsage(from, to)

        assertEquals(listOf(1L), report.removedRuleCounts.map { it.qaRuleId })
        assertEquals(listOf(3L), report.addedRuleCounts.map { it.qaRuleId })
    }

    @Test
    fun `counts edited replies`() {
        val from = LocalDateTime.of(2026, 6, 1, 0, 0)
        val to = LocalDateTime.of(2026, 6, 30, 0, 0)
        val after = objectMapper.writeValueAsString(
            mapOf(
                "suggestedRuleIds" to listOf(1),
                "qaRuleIds" to listOf(1),
                "edited" to true
            )
        )
        stubLogs(from, to, after)

        val report = service.aggregateRuleUsage(from, to)

        assertEquals(1, report.editedReplyCount)
    }

    @Test
    fun `aggregates identical free text topics`() {
        val from = LocalDateTime.of(2026, 6, 1, 0, 0)
        val to = LocalDateTime.of(2026, 6, 30, 0, 0)
        val after1 = objectMapper.writeValueAsString(
            mapOf(
                "suggestedRuleIds" to listOf(1),
                "qaRuleIds" to listOf(1),
                "freeTextPreview" to "Need more visa details"
            )
        )
        val after2 = objectMapper.writeValueAsString(
            mapOf(
                "suggestedRuleIds" to listOf(1),
                "qaRuleIds" to listOf(1),
                "freeTextPreview" to "Need   more VISA details"
            )
        )
        Mockito.`when`(
            operatorActionLogRepository.search(
                null, null, OperatorActionType.SEND_MANUAL_COMPOSED_REPLY.name,
                null, from, to, 10_000, 0
            )
        ).thenReturn(
            listOf(
                OperatorActionLog(
                    targetType = "INBOUND_MAIL_PROCESSING",
                    targetId = 1,
                    actionType = OperatorActionType.SEND_MANUAL_COMPOSED_REPLY.name,
                    actionSummary = "发送组装 QA 回复",
                    afterValue = after1
                ),
                OperatorActionLog(
                    targetType = "INBOUND_MAIL_PROCESSING",
                    targetId = 2,
                    actionType = OperatorActionType.SEND_MANUAL_COMPOSED_REPLY.name,
                    actionSummary = "发送组装 QA 回复",
                    afterValue = after2
                )
            )
        )

        val report = service.aggregateRuleUsage(from, to)

        assertEquals(1, report.freeTextTopicCounts.size)
        assertEquals(2, report.freeTextTopicCounts.first().count)
        assertEquals("need more visa details", report.freeTextTopicCounts.first().topic)
    }

    private fun stubLogs(from: LocalDateTime, to: LocalDateTime, afterJson: String) {
        Mockito.`when`(
            operatorActionLogRepository.search(
                null, null, OperatorActionType.SEND_MANUAL_COMPOSED_REPLY.name,
                null, from, to, 10_000, 0
            )
        ).thenReturn(
            listOf(
                OperatorActionLog(
                    targetType = "INBOUND_MAIL_PROCESSING",
                    targetId = 1,
                    actionType = OperatorActionType.SEND_MANUAL_COMPOSED_REPLY.name,
                    actionSummary = "发送组装 QA 回复",
                    afterValue = afterJson
                )
            )
        )
    }
}
