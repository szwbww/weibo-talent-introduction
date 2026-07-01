package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.InboundMailTag
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

data class QaTagCount(
    val qaRuleId: Long,
    val label: String,
    val count: Long
)

data class CustomTagCount(
    val label: String,
    val count: Long
)

interface InboundMailTagRepository : CrudRepository<InboundMailTag, Long> {
    fun findAllByInboundProcessingIdOrderByIdAsc(inboundProcessingId: Long): List<InboundMailTag>

    fun findAllByInboundProcessingIdIn(inboundProcessingIds: Collection<Long>): List<InboundMailTag>

    fun existsByInboundProcessingIdAndQaRuleId(inboundProcessingId: Long, qaRuleId: Long): Boolean

    fun existsByInboundProcessingIdAndTagTypeAndLabel(
        inboundProcessingId: Long,
        tagType: String,
        label: String
    ): Boolean

    @Query(
        """
        SELECT qa_rule_id, MIN(label) AS label, COUNT(*) AS count
        FROM inbound_mail_tag
        WHERE tag_type = 'QA'
        GROUP BY qa_rule_id
        """
    )
    fun countQaTagsGroupedByRule(): List<QaTagCount>

    @Query(
        """
        SELECT label, COUNT(*) AS count
        FROM inbound_mail_tag
        WHERE tag_type = 'CUSTOM'
        GROUP BY label
        """
    )
    fun countCustomTagsGroupedByLabel(): List<CustomTagCount>
}
