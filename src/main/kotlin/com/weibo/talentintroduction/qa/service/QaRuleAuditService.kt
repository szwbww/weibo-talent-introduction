package com.weibo.talentintroduction.qa.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.repository.OperatorActionLogRepository
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.Locale

@Service
class QaRuleAuditService(
    private val operatorActionLogRepository: OperatorActionLogRepository,
    private val mailRecordQaRuleRepository: MailRecordQaRuleRepository,
    private val objectMapper: ObjectMapper
) {
    fun aggregateRuleUsage(from: LocalDateTime, to: LocalDateTime): QaRuleUsageAuditReport {
        val logs = operatorActionLogRepository.search(
            expertContactId = null,
            inboundProcessingId = null,
            actionType = OperatorActionType.SEND_MANUAL_COMPOSED_REPLY.name,
            operatorName = null,
            start = from,
            end = to,
            limit = 10_000,
            offset = 0
        )

        val removedCounts = mutableMapOf<Long, Int>()
        val addedCounts = mutableMapOf<Long, Int>()
        val freeTextTopicCounts = mutableMapOf<String, Int>()
        var editedReplyCount = 0

        logs.forEach { log ->
            val after = parseMap(log.afterValue) ?: return@forEach
            val suggested = toLongList(after["suggestedRuleIds"])
            val selected = resolveSelectedRuleIds(after)
            if (after["edited"] == true) {
                editedReplyCount++
            }
            (suggested - selected.toSet()).forEach { ruleId ->
                removedCounts[ruleId] = removedCounts.getOrDefault(ruleId, 0) + 1
            }
            (selected - suggested.toSet()).forEach { ruleId ->
                addedCounts[ruleId] = addedCounts.getOrDefault(ruleId, 0) + 1
            }
            toStringValue(after["freeTextPreview"])
                ?.let(::normalizeFreeTextTopic)
                ?.takeIf { it.isNotBlank() }
                ?.let { topic ->
                    freeTextTopicCounts[topic] = freeTextTopicCounts.getOrDefault(topic, 0) + 1
                }
        }

        return QaRuleUsageAuditReport(
            from = from.toString(),
            to = to.toString(),
            totalActions = logs.size,
            editedReplyCount = editedReplyCount,
            removedRuleCounts = removedCounts.entries
                .sortedByDescending { it.value }
                .map { RuleUsageCount(it.key, it.value) },
            addedRuleCounts = addedCounts.entries
                .sortedByDescending { it.value }
                .map { RuleUsageCount(it.key, it.value) },
            freeTextTopicCounts = freeTextTopicCounts.entries
                .sortedByDescending { it.value }
                .map { FreeTextTopicCount(it.key, it.value) }
        )
    }

    private fun resolveSelectedRuleIds(after: Map<String, Any?>): List<Long> {
        val mailRecordId = when (val value = after["mailRecordId"]) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
        if (mailRecordId != null) {
            val fromAssociation = mailRecordQaRuleRepository
                .findByMailRecordIdOrderByOrdinalAsc(mailRecordId)
                .map { it.qaRuleId }
            if (fromAssociation.isNotEmpty()) {
                return fromAssociation
            }
        }
        return toLongList(after["qaRuleIds"])
    }

    internal fun normalizeFreeTextTopic(text: String): String =
        text.lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)

    private fun parseMap(json: String?): Map<String, Any?>? =
        json?.takeIf { it.isNotBlank() }?.let {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(it, Map::class.java) as Map<String, Any?>
        }

    private fun toLongList(value: Any?): List<Long> =
        when (value) {
            is List<*> -> value.mapNotNull {
                when (it) {
                    is Number -> it.toLong()
                    is String -> it.toLongOrNull()
                    else -> null
                }
            }
            else -> emptyList()
        }

    private fun toStringValue(value: Any?): String? =
        when (value) {
            null -> null
            is String -> value
            else -> value.toString()
        }
}

data class QaRuleUsageAuditReport(
    val from: String,
    val to: String,
    val totalActions: Int,
    val editedReplyCount: Int,
    val removedRuleCounts: List<RuleUsageCount>,
    val addedRuleCounts: List<RuleUsageCount>,
    val freeTextTopicCounts: List<FreeTextTopicCount> = emptyList()
)

data class RuleUsageCount(
    val qaRuleId: Long,
    val count: Int
)

data class FreeTextTopicCount(
    val topic: String,
    val count: Int
)
