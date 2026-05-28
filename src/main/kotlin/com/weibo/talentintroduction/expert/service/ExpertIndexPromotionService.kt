package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.springframework.stereotype.Service

@Service
class ExpertIndexPromotionService(
    private val expertIndexService: ExpertIndexService,
    private val candidateEligibilityService: CandidateEligibilityService
) {
    fun candidateIndexName(): String =
        expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)

    fun applicationIndexName(): String =
        expertIndexService.indexName(ExpertIndexLevel.APPLICATION)

    fun validateForCandidate(expert: ExpertProfile): Boolean =
        candidateEligibilityService.isEligibleForCandidateIndex(expert)

    fun validateForApplication(expert: ExpertProfile, hasReply: Boolean): Boolean =
        validateForCandidate(expert) && hasReply
}
