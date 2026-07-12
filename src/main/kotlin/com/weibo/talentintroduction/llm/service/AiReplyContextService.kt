package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.domain.MailRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class AiReplyContext(
    val profileText: String,
    val mailHistory: String,
    val contextWarnings: List<String>
)

@Service
class AiReplyContextService(
    private val expertSearchService: ExpertSearchService,
    private val contextBuilder: AiReplyContextBuilder
) {
    private val log = LoggerFactory.getLogger(AiReplyContextService::class.java)

    companion object {
        private val RESEARCH_PHRASES = listOf(
            "research profile",
            "research background",
            "areas of expertise",
            "expertise fall within",
            "within the scope",
            "google scholar",
            "scopus"
        )
    }

    fun requiresResearchContext(text: String): Boolean {
        val lower = text.lowercase()
        return RESEARCH_PHRASES.any { lower.contains(it) }
    }

    fun build(
        contact: ExpertContact,
        records: List<MailRecord>,
        inboundText: String,
        trainingKnowledge: String
    ): AiReplyContext {
        val warnings = mutableListOf<String>()

        val profile: ExpertProfile? = loadProfile(contact, warnings)

        val profileText = contextBuilder.appendKnowledgeToProfile(
            contextBuilder.buildExpertProfile(contact, profile),
            trainingKnowledge
        )
        val mailHistory = contextBuilder.buildMailHistory(records)

        if (requiresResearchContext(inboundText) && !isResearchSufficient(profile)) {
            warnings.add("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT")
        }

        return AiReplyContext(
            profileText = profileText,
            mailHistory = mailHistory,
            contextWarnings = warnings
        )
    }

    private fun loadProfile(contact: ExpertContact, warnings: MutableList<String>): ExpertProfile? {
        val orcidId = contact.orcidId.takeIf { it.isNotBlank() }
        if (orcidId == null) {
            warnings.add("EXPERT_PROFILE_NOT_FOUND")
            return null
        }
        return try {
            val level = parseIndexLevel(contact.currentIndexLevel)
            val profile = expertSearchService.findByOrcidId(orcidId, level)
                ?: if (level == ExpertIndexLevel.APPLICATION) {
                    expertSearchService.findByOrcidId(orcidId, ExpertIndexLevel.CANDIDATE)
                } else {
                    null
                }
            if (profile == null) {
                warnings.add("EXPERT_PROFILE_NOT_FOUND")
            }
            profile
        } catch (e: Exception) {
            log.warn("Failed to load expert profile for orcidId={}: {}", orcidId, e.message)
            warnings.add("EXPERT_PROFILE_NOT_FOUND")
            null
        }
    }

    private fun isResearchSufficient(profile: ExpertProfile?): Boolean {
        if (profile == null) return false
        return !profile.researchFields.isNullOrBlank() ||
            !profile.keyword.isNullOrBlank() ||
            !profile.disciplineCategory.isNullOrBlank() ||
            !profile.recentWorkTitles.isNullOrEmpty()
    }

    private fun parseIndexLevel(level: String): ExpertIndexLevel =
        runCatching { ExpertIndexLevel.valueOf(level) }.getOrDefault(ExpertIndexLevel.CANDIDATE)
}
