package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.service.ExpertRecipientNamePolicy
import com.weibo.talentintroduction.mail.service.MailMessageIdNormalizer
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class AiReplyContextBuilder {
    fun buildExpertProfile(contact: ExpertContact, expertProfile: ExpertProfile? = null): String = buildString {
        val resolvedName = ExpertRecipientNamePolicy.resolveRecipientName(contact, expertProfile)
        resolvedName?.takeIf { it.isNotBlank() }?.let { appendLine("Name: $it") }
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

    fun buildMailHistory(records: List<MailRecord>): String =
        buildMailHistory(records, null)

    fun buildMailHistory(records: List<MailRecord>, currentInboundMessageId: String?): String {
        val eligible = records.filter { record ->
            when (record.direction?.uppercase()) {
                "INBOUND" -> true
                "OUTBOUND" -> record.sendStatus?.uppercase() == "SENT"
                else -> false
            }
        }
        val normalizedCurrentId = MailMessageIdNormalizer.normalize(currentInboundMessageId)
            .takeIf { it.isNotBlank() }
        val filtered = if (normalizedCurrentId != null) {
            eligible.filter { record ->
                val msgId = MailMessageIdNormalizer.normalize(record.messageId)
                record.direction?.uppercase() != "INBOUND" ||
                    !msgId.equals(normalizedCurrentId, ignoreCase = true)
            }
        } else {
            eligible
        }

        val effectiveTime: (MailRecord) -> LocalDateTime = { record ->
            record.receivedAt ?: record.sentAt ?: record.createdAt ?: LocalDateTime.MIN
        }
        val sorted = filtered.sortedWith(
            compareBy(effectiveTime)
                .thenBy { it.id ?: Long.MIN_VALUE }
        )
        val recent = sorted.takeLast(8)

        val blocks = recent.map { record ->
            val role = when (record.direction?.uppercase()) {
                "INBOUND" -> "EXPERT"
                else -> "OUR_TEAM"
            }
            val subject = record.subject.orEmpty().trim().take(160)
            val body = (record.cleanedBody?.takeIf { it.isNotBlank() } ?: record.body.orEmpty())
                .trim()
                .take(800)
            buildString {
                appendLine("[$role]")
                appendLine("Subject: $subject")
                appendLine("Body: $body")
            }.trim()
        }

        val result = mutableListOf<String>()
        var totalChars = 0
        val reversed = blocks.reversed()
        val separatorLen = "\n\n".length
        for (block in reversed) {
            val addLen = block.length + if (result.isNotEmpty()) separatorLen else 0
            if (totalChars + addLen > 5000 && result.isNotEmpty()) {
                break
            }
            result.add(0, block)
            totalChars += addLen
        }
        return result.joinToString("\n\n")
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
