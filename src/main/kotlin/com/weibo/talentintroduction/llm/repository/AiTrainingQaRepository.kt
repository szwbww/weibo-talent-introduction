package com.weibo.talentintroduction.llm.repository

import com.weibo.talentintroduction.llm.domain.AiTrainingQa
import org.springframework.data.repository.CrudRepository

interface AiTrainingQaRepository : CrudRepository<AiTrainingQa, Long> {
    fun findBySourceAndSourceRef(source: String, sourceRef: String): AiTrainingQa?

    fun findAllByOrderByCreatedAtDesc(): List<AiTrainingQa>
}
