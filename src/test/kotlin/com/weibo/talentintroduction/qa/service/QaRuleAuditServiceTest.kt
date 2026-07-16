package com.weibo.talentintroduction.qa.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.audit.domain.OperatorActionLog
import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.repository.OperatorActionLogRepository
import com.weibo.talentintroduction.mail.domain.MailRecordQaRule
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `reports ai reply quality metrics with mixed readiness`() {
        val from = LocalDateTime.of(2026, 6, 1, 0, 0)
        val to = LocalDateTime.of(2026, 6, 30, 0, 0)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_DRAFT_READY, 3L)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_DRAFT_NEEDS_REVIEW, 2L)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_DRAFT_BLOCKED, 1L)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_SEND_BLOCKED, 0L)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_REVIEW_CONFIRMED, 0L)

        val after = objectMapper.writeValueAsString(
            mapOf("suggestedRuleIds" to listOf(1), "qaRuleIds" to listOf(1))
        )
        stubLogs(from, to, after)

        val report = service.aggregateRuleUsage(from, to)
        val metrics = report.aiReplyQuality
        assertNotNull(metrics)
        assertEquals(3, metrics!!.readyCount)
        assertEquals(2, metrics.needsReviewCount)
        assertEquals(1, metrics.blockedCount)
        assertEquals(6, metrics.totalGenerated)
        assertEquals(0.5, metrics.readyRate, 0.001)
        assertEquals(0.333, metrics.partialRate, 0.001)
        assertEquals(0.166, metrics.omissionRate, 0.001)
        assertEquals(0, metrics.directSendBlockedCount)
        assertEquals(0, metrics.reviewConfirmedCount)
    }

    @Test
    fun `zero denominator produces zero rates`() {
        val from = LocalDateTime.of(2026, 6, 1, 0, 0)
        val to = LocalDateTime.of(2026, 6, 30, 0, 0)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_DRAFT_READY, 0L)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_DRAFT_NEEDS_REVIEW, 0L)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_DRAFT_BLOCKED, 0L)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_SEND_BLOCKED, 0L)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_REVIEW_CONFIRMED, 0L)

        val after = objectMapper.writeValueAsString(
            mapOf("suggestedRuleIds" to listOf(1), "qaRuleIds" to listOf(1))
        )
        stubLogs(from, to, after)

        val report = service.aggregateRuleUsage(from, to)
        val metrics = report.aiReplyQuality
        assertNotNull(metrics)
        assertEquals(0, metrics!!.readyCount)
        assertEquals(0, metrics.totalGenerated)
        assertEquals(0.0, metrics.readyRate, 0.0)
        assertEquals(0.0, metrics.partialRate, 0.0)
        assertEquals(0.0, metrics.omissionRate, 0.0)
    }

    @Test
    fun `direct send blocked and review confirmed do not enter generation denominator`() {
        val from = LocalDateTime.of(2026, 6, 1, 0, 0)
        val to = LocalDateTime.of(2026, 6, 30, 0, 0)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_DRAFT_READY, 1L)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_DRAFT_NEEDS_REVIEW, 0L)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_DRAFT_BLOCKED, 0L)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_SEND_BLOCKED, 5L)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_REVIEW_CONFIRMED, 3L)

        val after = objectMapper.writeValueAsString(
            mapOf("suggestedRuleIds" to listOf(1), "qaRuleIds" to listOf(1))
        )
        stubLogs(from, to, after)

        val report = service.aggregateRuleUsage(from, to)
        val metrics = report.aiReplyQuality
        assertNotNull(metrics)
        assertEquals(1, metrics!!.totalGenerated)
        assertEquals(5, metrics.directSendBlockedCount)
        assertEquals(3, metrics.reviewConfirmedCount)
        assertEquals(1.0, metrics.readyRate, 0.001)
        assertEquals(0.0, metrics.partialRate, 0.0)
        assertEquals(0.0, metrics.omissionRate, 0.0)
    }

    @Test
    fun `original qa selected association tests keep passing after metrics addition`() {
        val from = LocalDateTime.of(2026, 6, 1, 0, 0)
        val to = LocalDateTime.of(2026, 6, 30, 0, 0)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_DRAFT_READY, 0L)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_DRAFT_NEEDS_REVIEW, 0L)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_DRAFT_BLOCKED, 0L)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_SEND_BLOCKED, 0L)
        stubCountSearch(from, to, OperatorActionType.AI_REPLY_REVIEW_CONFIRMED, 0L)

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
        assertNotNull(report.aiReplyQuality)
        assertEquals(1, report.totalActions)
        assertEquals(listOf(1L), report.removedRuleCounts.map { it.qaRuleId })
        assertEquals(listOf(4L), report.addedRuleCounts.map { it.qaRuleId })
    }

    private fun stubCountSearch(from: LocalDateTime, to: LocalDateTime, actionType: OperatorActionType, count: Long) {
        Mockito.`when`(
            operatorActionLogRepository.countSearch(
                null, null, actionType.name, null, from, to
            )
        ).thenReturn(count)
    }
}
