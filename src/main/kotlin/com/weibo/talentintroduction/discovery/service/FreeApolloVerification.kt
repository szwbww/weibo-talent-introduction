package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.discovery.domain.*

/** First-page total is essential: a deduplicated candidate union cannot prove a unique search. */
data class ApolloQueryResult(val query: ApolloSearchQuery, val result: ApolloSearchPage, val attemptId: Long? = null) {
    companion object {
        fun retained(query: ApolloSearchQuery, page: ApolloSearchPage, attemptId: Long?) = ApolloQueryResult(query.copy(page = 1),
            if (page.totalEntries > 1) page.copy(candidates = emptyList()) else page, attemptId)
    }
}

internal object FreeApolloVerification {
    data class Result(val personId: String?, val clear: Boolean, val reason: String)
    private data class Group(val status: String, val ids: Set<String>)
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    fun retainedPages(attempts: List<VerificationAttempt>): List<ApolloQueryResult> = attempts.sortedBy { it.id }
        .filter { it.provider == VerificationProvider.APOLLO && it.queryKind != VerificationQueryKind.DETAIL &&
            it.pageNumber == 1 && it.httpStatus == 200 && it.resultCode == "OK" && it.responseJson != null }
        .mapNotNull { attempt -> runCatching {
            ApolloQueryResult.retained(mapper.readValue<ApolloSearchQuery>(attempt.requestJson).copy(page = 1),
                mapper.readValue<ApolloSearchPage>(attempt.responseJson!!), attempt.id)
        }.getOrNull() }.groupBy { it.query }.values.map { it.last() }

    fun evaluate(snapshot: VerificationSnapshot, evidence: List<VerificationEvidence>, search: ApolloSearchEvidence,
        rules: DiscoveryVerificationRules, institution: String?): Result {
        val names = rules.allowedNames(snapshot, evidence)
        val queries = names.flatMap { rules.queryNameVariants(it) }.toSet()
        fun group(org: String?): Group {
            var incomplete = false; var multiple = false; var conflict = false
            val ids = linkedSetOf<String>()
            for (name in queries) {
                val saved = search.firstPages.lastOrNull { it.query.name == name && it.query.institution?.name == org }
                if (saved == null) { incomplete = true; continue }
                val page = saved.result
                if (page.page != 1 || page.totalEntries < 0 || page.candidates.size > page.totalEntries) { incomplete = true; continue }
                if (page.totalEntries > 1) { multiple = true; continue }
                if (page.totalEntries == 0L) continue
                val candidate = page.candidates.singleOrNull()
                if (candidate == null || candidate.id.isBlank()) { incomplete = true; continue }
                if (candidate.orcid != null && snapshot.boundOrcid != null && normalizeOrcid(candidate.orcid) != snapshot.boundOrcid)
                    conflict = true
                val matched = rules.nameMatches(candidate, names, setOf(name), masked = true, ruleVersion = 4)
                val orgMatched = org == null || candidate.institution?.let {
                    DiscoveryInstitutionNormalizer.compareWithAliases(VerificationInstitution(org), it, 2) == InstitutionComparison.MATCH
                } == true
                if (!matched || !orgMatched) incomplete = true else ids += candidate.id
            }
            return Group(when {
                conflict -> "CONFLICT"
                multiple || ids.size > 1 -> "MULTIPLE"
                incomplete || queries.isEmpty() -> "INCOMPLETE"
                ids.size == 1 -> "UNIQUE"
                else -> "NO_MATCH"
            }, ids)
        }
        val broad = group(null)
        val narrow = institution?.let { group(it) }
        if (broad.status == "CONFLICT" || narrow?.status == "CONFLICT") return Result(null, false, "ORCID_CONFLICT")
        if (narrow?.status == "UNIQUE") return Result(narrow.ids.single(), true, "APOLLO_FREE_NAME_INSTITUTION_UNIQUE")
        if (broad.status == "UNIQUE") return Result(broad.ids.single(), false, "APOLLO_FREE_NAME_UNIQUE")
        return Result(null, false, when {
            broad.status == "MULTIPLE" || narrow?.status == "MULTIPLE" -> "IDENTITY_AMBIGUOUS"
            broad.status == "INCOMPLETE" || narrow?.status == "INCOMPLETE" -> "SEARCH_INCOMPLETE"
            else -> "APOLLO_NO_MATCH"
        })
    }
}
