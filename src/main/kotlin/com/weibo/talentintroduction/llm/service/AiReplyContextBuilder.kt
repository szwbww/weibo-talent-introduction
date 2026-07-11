package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.mail.domain.MailRecord
import org.springframework.stereotype.Service

@Service
class AiReplyContextBuilder {
    fun buildExpertProfile(contact: ExpertContact, expertProfile: ExpertProfile? = null): String = buildString {
        contact.expertName?.takeIf { it.isNotBlank() }?.let { appendLine("Name: $it") }
        contact.country?.takeIf { it.isNotBlank() }?.let { appendLine("Country: $it") }
        appendLine("Email: ${contact.expertEmail}")
        appendLine("Status: ${contact.currentStatus}")
        if (expertProfile != null) {
            expertProfile.institution?.takeIf { it.isNotBlank() }?.let { appendLine("Institution: $it") }
            expertProfile.researchFields?.takeIf { it.isNotBlank() }?.let { appendLine("Research fields: $it") }
            expertProfile.keyword?.takeIf { it.isNotBlank() }?.let { appendLine("Keywords: $it") }
            expertProfile.disciplineCategory?.takeIf { it.isNotBlank() }?.let { appendLine("Discipline: $it") }
            expertProfile.hIndex?.let { appendLine("H-Index: $it") }
            expertProfile.recentWorkTitles?.takeIf { it.isNotEmpty() }?.let {
                appendLine("Recent works: ${it.take(5).joinToString("; ")}")
            }
            expertProfile.patentTitles?.takeIf { it.isNotEmpty() }?.let {
                appendLine("Patents: ${it.take(3).joinToString("; ")}")
            }
            expertProfile.enrichedAt?.takeIf { it.isNotBlank() }?.let { appendLine("Enriched at: $it") }
        }
    }.trim()

    fun buildMailHistory(records: List<MailRecord>): String {
        val recent = records.takeLast(20)
        return recent.joinToString("\n\n") { record ->
            val body = record.cleanedBody?.takeIf { it.isNotBlank() } ?: record.body.orEmpty()
            "[${record.direction}] ${record.subject.orEmpty().take(200)}\n${body.take(1500)}"
        }.take(8000)
    }

    fun appendKnowledgeToProfile(profile: String, knowledgeContext: String): String {
        if (knowledgeContext.isBlank()) {
            return profile
        }
        return buildString {
            if (profile.isNotBlank()) {
                appendLine(profile)
                appendLine()
            }
            appendLine("Training knowledge base:")
            append(knowledgeContext)
        }.trim()
    }
}
