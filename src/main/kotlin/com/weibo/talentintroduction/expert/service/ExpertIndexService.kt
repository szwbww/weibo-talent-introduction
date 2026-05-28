package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import org.springframework.stereotype.Service

@Service
class ExpertIndexService(
    private val properties: ElasticsearchProperties
) {
    fun indexName(level: ExpertIndexLevel): String =
        when (level) {
            ExpertIndexLevel.RAW -> properties.rawIndexName
            ExpertIndexLevel.CANDIDATE -> properties.candidateIndexName
            ExpertIndexLevel.APPLICATION -> properties.applicationIndexName
        }
}
