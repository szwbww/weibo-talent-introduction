package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.InboundMailTag
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

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
        FROM inbound_mail_tag t
        WHERE t.tag_type = 'QA'
        GROUP BY t.qa_rule_id
        """
    )
    fun countQaTagsGroupedByRule(): List<QaTagCount>

    @Query(
        """
        SELECT qa_rule_id, MIN(label) AS label, COUNT(*) AS count
        FROM inbound_mail_tag t
        JOIN inbound_mail_processing p ON p.id = t.inbound_processing_id
        WHERE t.tag_type = 'QA'
          AND p.received_at >= :from AND p.received_at < :to
          AND p.expert_contact_id IS NOT NULL
        GROUP BY t.qa_rule_id
        """
    )
    fun countQaTagsGroupedByRule(from: LocalDateTime, to: LocalDateTime): List<QaTagCount>

    @Query(
        """
        SELECT label, COUNT(*) AS count
        FROM inbound_mail_tag t
        WHERE t.tag_type = 'CUSTOM'
        GROUP BY t.label
        """
    )
    fun countCustomTagsGroupedByLabel(): List<CustomTagCount>

    @Query(
        """
        SELECT label, COUNT(*) AS count
        FROM inbound_mail_tag t
        JOIN inbound_mail_processing p ON p.id = t.inbound_processing_id
        WHERE t.tag_type = 'CUSTOM'
          AND p.received_at >= :from AND p.received_at < :to
          AND p.expert_contact_id IS NOT NULL
        GROUP BY t.label
        """
    )
    fun countCustomTagsGroupedByLabel(from: LocalDateTime, to: LocalDateTime): List<CustomTagCount>
}
