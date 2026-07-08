package com.weibo.talentintroduction.llm.repository

import com.weibo.talentintroduction.llm.domain.AiTrainingDialogue
import org.springframework.data.repository.CrudRepository

interface AiTrainingDialogueRepository : CrudRepository<AiTrainingDialogue, Long> {
    fun findBySourceRef(sourceRef: String): AiTrainingDialogue?

    fun findAllByEnabledTrue(): List<AiTrainingDialogue>

    fun findAllByOrderByIdAsc(): List<AiTrainingDialogue>
}
