package com.weibo.talentintroduction.expert.domain

import com.fasterxml.jackson.databind.JsonNode
import java.util.Locale

/** Evidence of the inbound mail route, never a claim about the mailbox's underlying hosting. */
data class EmailRouting(
    val domain: String,
    val status: String,
    val reason: String,
    val mxRecords: List<String>,
    val checkedAt: String
) {
    /** Managed tag follows current routing evidence; unrelated user tags stay intact. */
    fun withRoutingTag(tags: List<String>): List<String> =
        (tags.filterNot { it == MICROSOFT_TAG } + if (status == "MICROSOFT") listOf(MICROSOFT_TAG) else emptyList()).distinct()

    companion object {
        const val MICROSOFT_TAG = "微软系邮箱"
        val FILTERS = setOf("MICROSOFT", "UNKNOWN", "UNCHECKED")
        fun normalizeFilter(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() && it != "ALL" }?.also {
            require(it in FILTERS) { "Invalid emailRouting filter" }
        }

        fun domainOf(email: String?): String? {
            val normalized = email?.trim()?.lowercase(Locale.ROOT) ?: return null
            if (!Regex("^[a-z0-9._%+-]+@[^@\\s]+$").matches(normalized)) return null
            val domain = normalized.substringAfter('@')
            if (domain.length > 253 || !domain.contains('.')) return null
            return domain.takeIf { it.split('.').all { label ->
                label.length in 1..63 && Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?").matches(label)
            } }
        }

        fun read(node: JsonNode, email: String?): EmailRouting? {
            if (!node.isObject || node.path("domain").asText() != domainOf(email)) return null
            val status = node.path("status").asText()
            if (status !in setOf("MICROSOFT", "UNKNOWN")) return null
            val checkedAt = node.path("checkedAt").asText("")
            if (runCatching { java.time.Instant.parse(checkedAt) }.isFailure) return null
            return EmailRouting(node.path("domain").asText(), status, node.path("reason").asText(""),
                node.path("mxRecords").takeIf { it.isArray }?.map { it.asText() }.orEmpty(), checkedAt)
        }

        /** Indexed status plus domain binding: changing an email cannot leave a stale provider filter hit. */
        fun filterQuery(value: String?): Map<String, Any>? {
            val filter = normalizeFilter(value) ?: return null
            val matchingDomain = mapOf("script" to mapOf("script" to mapOf("source" to
                "if (doc['email'].size()==0 || doc['emailRouting.domain'].size()==0) return false; " +
                "String e=doc['email'].value.trim().toLowerCase(); int at=e.lastIndexOf('@'); " +
                "return at>0 && e.substring(at+1)==doc['emailRouting.domain'].value;")))
            val known = mapOf("bool" to mapOf("filter" to listOf(
                mapOf("terms" to mapOf("emailRouting.status" to listOf("MICROSOFT", "UNKNOWN"))), matchingDomain)))
            return if (filter == "UNCHECKED") mapOf("bool" to mapOf("must_not" to listOf(known)))
            else mapOf("bool" to mapOf("filter" to listOf(
                mapOf("term" to mapOf("emailRouting.status" to filter)), matchingDomain)))
        }
    }
}
