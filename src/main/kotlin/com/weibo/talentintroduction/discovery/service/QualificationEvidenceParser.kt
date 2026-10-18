package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.domain.*
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** Only these bounded field facts may be retained, never the original person/affiliation response. */
@Service
class QualificationEvidenceParser(private val clock: Clock = Clock.systemUTC()) {
    private val mapper = jacksonObjectMapper()
    fun parse(snapshot: VerificationSnapshot, read: OrcidEvidenceRead): List<VerificationEvidence> {
        val orcid = snapshot.boundOrcid ?: return emptyList()
        val facts = ArrayList<VerificationEvidence>()
        val today = LocalDate.now(clock)
        read.documents.forEach { document ->
            fun emit(field: String, value: String?, node: JsonNode, pointer: String, scope: TemporalScope = TemporalScope.UNKNOWN) {
                if (value.isNullOrBlank() || value.length > 4096 || node.path("visibility").asText("PUBLIC") != "PUBLIC") return
                val source = node.path("source")
                val assertion = when {
                    source.path("source-client-id").path("path").asText().isNotBlank() -> AssertionSource.ORGANIZATION
                    source.path("source-orcid").path("path").asText() == orcid -> AssertionSource.SELF
                    else -> AssertionSource.UNKNOWN
                }
                val updated = node.path("last-modified-date").path("value").takeIf { it.isIntegralNumber && it.canConvertToLong() }
                    ?.longValue()?.let { runCatching { java.time.Instant.ofEpochMilli(it) }.getOrNull() }
                facts += VerificationEvidence(snapshot.identityHash, snapshot.factFingerprint, "ORCID",
                    "https://pub.orcid.org/v3.0/$orcid/${document.endpoint}", document.fetchedAt, document.responseSha256,
                    field, value, assertion, scope, updated, pointer,
                    excerpt = mapper.writeValueAsString(mapOf("sourceOrcid" to source.path("source-orcid").path("path").asText(null),
                        "sourceClientId" to source.path("source-client-id").path("path").asText(null),
                        "visibility" to node.path("visibility").asText("PUBLIC"))).take(2048))
            }
            if (document.endpoint == "person") {
                val name = document.body.path("name")
                val fullName = listOf(name.path("given-names").path("value").asText(""), name.path("family-name").path("value").asText("")).filter { it.isNotBlank() }.joinToString(" ")
                emit("name", fullName, name, "/name")
                document.body.path("other-names").path("other-name").forEachIndexed { index, alias ->
                    emit("otherName", alias.path("content").asText(null), alias, "/other-names/other-name/$index/content")
                }
            } else {
                val employment = document.endpoint.startsWith("employment")
                val kind = if (employment) "employment" else "education"
                val nodes = ArrayList<Pair<String, JsonNode>>()
                if (document.endpoint.contains('/')) nodes += "" to document.body
                else document.body.path("affiliation-group").forEachIndexed { groupIndex, group ->
                    group.path("summaries").forEachIndexed { index, wrapper ->
                        nodes += "/affiliation-group/$groupIndex/summaries/$index/$kind-summary" to wrapper.path("$kind-summary")
                    }
                }
                nodes.forEach { (pointer, node) ->
                    if (!node.isObject || node.path("visibility").asText("PUBLIC") != "PUBLIC") return@forEach
                    val start = date(node.path("start-date"))
                    val endNode = node.path("end-date")
                    val end = date(endNode)
                    val scope = when {
                        end != null && !end.isAfter(today) -> TemporalScope.HISTORICAL
                        start != null && !start.isAfter(today) && (endNode.isMissingNode || endNode.isNull || (end != null && end.isAfter(today))) -> TemporalScope.CURRENT
                        else -> TemporalScope.UNKNOWN
                    }
                    val org = node.path("organization")
                    val role = node.path("role-title").asText("")
                    val value = mapper.writeValueAsString(linkedMapOf(
                        "orcid" to orcid,
                        "institution" to org.path("name").asText("").take(500),
                        "standardId" to org.path("disambiguated-organization").let { dis ->
                            val id = dis.path("disambiguated-organization-identifier").asText("")
                            val source = dis.path("disambiguation-source").asText("")
                            if (id.isNotBlank() && source.isNotBlank()) "$source:$id" else null
                        },
                        "role" to role.take(500),
                        "startDate" to start?.toString(), "endDate" to end?.toString(),
                        "completed" to (!employment && completedDoctorate(role))
                    ))
                    emit(kind, value, node, pointer, scope)
                }
            }
        }
        return facts.distinctBy { listOf(it.field, it.value, it.assertionSource.name, it.temporalScope.name, it.sourceUrl.substringBeforeLast('/')) }
    }

    fun completedDoctorate(text: String): Boolean {
        val normalized = text.lowercase(java.util.Locale.ROOT)
        val doctorate = Regex("(?<![a-z])(?:ph\\.?\\s?d\\.?|dphil|doctorate|doctor of philosophy)(?![a-z])|博士").containsMatchIn(normalized)
        val student = Regex("student|candidate|pursuing|in progress|enrolled|在读|博士生|候选").containsMatchIn(normalized)
        val awarded = Regex("awarded|conferred|completed|graduated|degree obtained|已获|授予|毕业").containsMatchIn(normalized)
        val negated = Regex("\\b(?:not|never)\\s+(?:(?:yet|been|being|successfully|formally)\\s+)*(?:awarded|conferred|completed|graduated|obtained)\\b|\\b(?:uncompleted|incomplete|unawarded)\\b|(?:未|没有|尚未)(?:获|授予|完成|毕业)").containsMatchIn(normalized)
        return doctorate && awarded && !student && !negated
    }

    private fun date(node: JsonNode): LocalDate? = runCatching {
        val year = node.path("year").path("value").asText("").toInt()
        // A partial start date cannot establish current employment until its latest possible date.
        val month = node.path("month").path("value").asText("").toIntOrNull() ?: 12
        val day = node.path("day").path("value").asText("").toIntOrNull() ?: java.time.YearMonth.of(year, month).lengthOfMonth()
        LocalDate.of(year, month, day)
    }.getOrNull()
}
