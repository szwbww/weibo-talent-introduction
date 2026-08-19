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
    val contextWarnings: List<String>,
    val researchProfileSufficient: Boolean = true,
    // 03b (I-5): the two separable source fragments behind [profileText] — the
    // raw expert profile (no training knowledge) and the training knowledge —
    // carried so the workbench can fingerprint them independently. The prompt
    // content ([profileText]) is byte-unchanged; both new fields are
    // observational only.
    val expertProfileText: String = "",
    val trainingKnowledgeText: String = ""
)

@Service
class AiReplyContextService(
    private val expertSearchService: ExpertSearchService,
    private val contextBuilder: AiReplyContextBuilder
) {
    private val log = LoggerFactory.getLogger(AiReplyContextService::class.java)

    fun requiresResearchContext(text: String): Boolean =
        AiReplyIntentCatalog.matchIntents(text).any { it.requiresProfile }

    fun build(
        contact: ExpertContact,
        records: List<MailRecord>,
        inboundText: String,
        trainingKnowledge: String,
        currentInboundMessageId: String? = null
    ): AiReplyContext {
        val warnings = mutableListOf<String>()

        val profile: ExpertProfile? = loadProfile(contact, warnings)
        val researchProfileSufficient = isResearchSufficient(profile)

        val expertProfileText = contextBuilder.buildExpertProfile(contact, profile)
        val profileText = contextBuilder.appendKnowledgeToProfile(
            expertProfileText,
            trainingKnowledge
        )
        val mailHistory = contextBuilder.buildMailHistory(records, currentInboundMessageId)

        if (requiresResearchContext(inboundText) && !researchProfileSufficient) {
            warnings.add("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT")
        }

        return AiReplyContext(
            profileText = profileText,
            mailHistory = mailHistory,
            contextWarnings = warnings,
            researchProfileSufficient = researchProfileSufficient,
            expertProfileText = expertProfileText,
            trainingKnowledgeText = trainingKnowledge
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
