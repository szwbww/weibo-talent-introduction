package com.weibo.talentintroduction.qa.repository

import com.weibo.talentintroduction.qa.domain.QaRule
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface QaRuleRepository : CrudRepository<QaRule, Long> {
    fun findAllByOrderByPriorityAscIdAsc(): List<QaRule>

    fun findAllByCategoryIdOrderByPriorityAscIdAsc(categoryId: Long): List<QaRule>

    @Query(
        """
        SELECT *
        FROM qa_rule
        WHERE enabled = 1
        ORDER BY priority ASC, id ASC
        """
    )
    fun findAllEnabledOrdered(): List<QaRule>
}
