package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.InboundMailTag
import com.weibo.talentintroduction.mail.repository.CustomTagCount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.InboundMailTagRepository
import com.weibo.talentintroduction.mail.repository.QaTagCount
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.QaMatchService
import org.springframework.stereotype.Service
import java.time.LocalDateTime

data class TagView(
    val tagId: Long,
    val tagType: String,
    val qaRuleId: Long?,
    val label: String,
    val source: String,
    val active: Boolean
)

data class TagStatItem(
    val tagKey: String,
    val label: String,
    val tagType: String,
    val count: Long,
    val active: Boolean
)

data class TagStatsResult(
    val items: List<TagStatItem>,
    val total: Long
)

@Service
class InboundMailTagService(
    private val inboundMailTagRepository: InboundMailTagRepository,
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val qaRuleRepository: QaRuleRepository,
    private val qaMatchService: QaMatchService
) {
    fun autoApplyQaTags(
        inboundProcessingId: Long,
        body: String?,
        createdBy: String? = null,
        source: String = "AUTO"
    ): Int {
        if (body.isNullOrBlank()) return 0
        val ruleIds = qaMatchService.matchAllRuleIds(body)
        val now = LocalDateTime.now()
        var added = 0
        for (ruleId in ruleIds) {
            if (inboundMailTagRepository.existsByInboundProcessingIdAndQaRuleId(inboundProcessingId, ruleId)) {
                continue
            }
            val rule = qaRuleRepository.findById(ruleId).orElse(null) ?: continue
            inboundMailTagRepository.save(
                InboundMailTag(
                    inboundProcessingId = inboundProcessingId,
                    tagType = TAG_TYPE_QA,
                    qaRuleId = ruleId,
                    label = snapshotLabel(rule),
                    source = source,
                    createdBy = createdBy,
                    createdAt = now
                )
            )
            added++
        }
        return added
    }

    fun addQaTag(inboundProcessingId: Long, qaRuleId: Long, operator: String?): InboundMailTag {
        requireInboundExists(inboundProcessingId)
        val rule = qaRuleRepository.findById(qaRuleId)
            .orElseThrow { IllegalArgumentException("QA rule not found: $qaRuleId") }
        if (inboundMailTagRepository.existsByInboundProcessingIdAndQaRuleId(inboundProcessingId, qaRuleId)) {
            throw IllegalArgumentException("QA tag already exists for rule $qaRuleId")
        }
        return inboundMailTagRepository.save(
            InboundMailTag(
                inboundProcessingId = inboundProcessingId,
                tagType = TAG_TYPE_QA,
                qaRuleId = qaRuleId,
                label = snapshotLabel(rule),
                source = SOURCE_MANUAL,
                createdBy = operator,
                createdAt = LocalDateTime.now()
            )
        )
    }

    fun addCustomTag(inboundProcessingId: Long, label: String, operator: String?): InboundMailTag {
        requireInboundExists(inboundProcessingId)
        val trimmed = label.trim()
        require(trimmed.isNotEmpty()) { "label must not be blank" }
        if (inboundMailTagRepository.existsByInboundProcessingIdAndTagTypeAndLabel(
                inboundProcessingId,
                TAG_TYPE_CUSTOM,
                trimmed
            )
        ) {
            throw IllegalArgumentException("Custom tag already exists: $trimmed")
        }
        return inboundMailTagRepository.save(
            InboundMailTag(
                inboundProcessingId = inboundProcessingId,
                tagType = TAG_TYPE_CUSTOM,
                qaRuleId = null,
                label = trimmed,
                source = SOURCE_MANUAL,
                createdBy = operator,
                createdAt = LocalDateTime.now()
            )
        )
    }

    fun deleteTag(tagId: Long) {
        if (!inboundMailTagRepository.existsById(tagId)) {
            throw IllegalArgumentException("Tag not found: $tagId")
        }
        inboundMailTagRepository.deleteById(tagId)
    }

    fun listTags(inboundProcessingId: Long): List<TagView> {
        val tags = inboundMailTagRepository.findAllByInboundProcessingIdOrderByIdAsc(inboundProcessingId)
        val ruleIds = tags.mapNotNull { it.qaRuleId }.distinct()
        val rulesById = if (ruleIds.isEmpty()) {
            emptyMap()
        } else {
            qaRuleRepository.findAllById(ruleIds).associateBy { requireNotNull(it.id) }
        }
        return tags.mapNotNull { tag ->
            val tagId = tag.id ?: return@mapNotNull null
            val rule = tag.qaRuleId?.let { rulesById[it] }
            TagView(
                tagId = tagId,
                tagType = tag.tagType,
                qaRuleId = tag.qaRuleId,
                label = resolveTagLabel(tag, rule),
                source = tag.source,
                active = isActive(tag, rule)
            )
        }
    }

    fun listTagsBatch(inboundProcessingIds: Collection<Long>): Map<Long, List<TagView>> {
        if (inboundProcessingIds.isEmpty()) return emptyMap()
        val tags = inboundMailTagRepository.findAllByInboundProcessingIdIn(inboundProcessingIds)
        val ruleIds = tags.mapNotNull { it.qaRuleId }.distinct()
        val rulesById = if (ruleIds.isEmpty()) {
            emptyMap()
        } else {
            qaRuleRepository.findAllById(ruleIds).associateBy { requireNotNull(it.id) }
        }
        return tags.groupBy { it.inboundProcessingId }.mapValues { (_, tagList) ->
            tagList.mapNotNull { tag ->
                val tagId = tag.id ?: return@mapNotNull null
                val rule = tag.qaRuleId?.let { rulesById[it] }
                TagView(
                    tagId = tagId,
                    tagType = tag.tagType,
                    qaRuleId = tag.qaRuleId,
                    label = resolveTagLabel(tag, rule),
                    source = tag.source,
                    active = isActive(tag, rule)
                )
            }
        }
    }

    fun stats(from: LocalDateTime, to: LocalDateTime): TagStatsResult {
        val qaCounts = inboundMailTagRepository.countQaTagsGroupedByRule(from, to)
        val customCounts = inboundMailTagRepository.countCustomTagsGroupedByLabel(from, to)
        return buildStats(qaCounts, customCounts)
    }

    fun stats(): TagStatsResult {
        val qaCounts = inboundMailTagRepository.countQaTagsGroupedByRule()
        val customCounts = inboundMailTagRepository.countCustomTagsGroupedByLabel()
        return buildStats(qaCounts, customCounts)
    }

    private fun buildStats(qaCounts: List<QaTagCount>, customCounts: List<CustomTagCount>): TagStatsResult {
        val ruleIds = qaCounts.map { it.qaRuleId }
        val rulesById = if (ruleIds.isEmpty()) {
            emptyMap()
        } else {
            qaRuleRepository.findAllById(ruleIds).associateBy { requireNotNull(it.id) }
        }

        val qaItems = qaCounts.map { count ->
            val rule = rulesById[count.qaRuleId]
            val active = rule != null && rule.enabled
            TagStatItem(
                tagKey = "qa:${count.qaRuleId}",
                label = resolveQaDisplayLabel(rule, count.label),
                tagType = TAG_TYPE_QA,
                count = count.count,
                active = active
            )
        }
        val customItems = customCounts.map { count ->
            TagStatItem(
                tagKey = "custom:${count.label}",
                label = count.label,
                tagType = TAG_TYPE_CUSTOM,
                count = count.count,
                active = true
            )
        }
        val items = (qaItems + customItems).sortedByDescending { it.count }
        val total = items.sumOf { it.count }
        return TagStatsResult(items = items, total = total)
    }

    private fun resolveTagLabel(tag: InboundMailTag, rule: QaRule?): String {
        if (tag.tagType == TAG_TYPE_CUSTOM) return tag.label
        return resolveQaDisplayLabel(rule, tag.label)
    }

    private fun resolveQaDisplayLabel(rule: QaRule?, snapshot: String): String {
        if (rule != null && rule.enabled && !rule.displayName.isNullOrBlank()) {
            return rule.displayName
        }
        return snapshot
    }

    private fun isActive(tag: InboundMailTag, rule: QaRule?): Boolean {
        if (tag.tagType == TAG_TYPE_CUSTOM) return true
        return rule != null && rule.enabled
    }

    private fun snapshotLabel(rule: QaRule): String {
        val id = requireNotNull(rule.id)
        return rule.displayName?.takeIf { it.isNotBlank() }
            ?: rule.keywords.substringBefore(",").trim().ifBlank { "规则#$id" }
    }

    private fun requireInboundExists(inboundProcessingId: Long) {
        if (!inboundMailProcessingRepository.existsById(inboundProcessingId)) {
            throw IllegalArgumentException("Inbound mail processing not found: $inboundProcessingId")
        }
    }

    companion object {
        const val TAG_TYPE_QA = "QA"
        const val TAG_TYPE_CUSTOM = "CUSTOM"
        const val SOURCE_MANUAL = "MANUAL"
    }
}
