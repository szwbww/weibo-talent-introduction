package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.qa.domain.QaRule

internal data class IntentGroupTitle(
    val intentKeys: Set<String>,
    val title: String
)

data class RequestIntentDefinition(
    val key: String,
    val title: String,
    val requestAliases: List<String>,
    val requiredCoverageKeys: List<String>,
    val alternativeCoverageKeys: List<String> = emptyList(),
    val requiresProfile: Boolean = false
)

data class RequestIntentCoverage(
    val intentKey: String,
    val title: String,
    val requiredCoverageKeys: List<String>,
    val evidenceRuleIds: List<Long>,
    val status: String,
    val missingEvidenceKeys: List<String>,
    val requiresResearchContext: Boolean = false
)

object AiReplyIntentCatalog {

    private val groupTitles: List<IntentGroupTitle> = listOf(
        IntentGroupTitle(
            intentKeys = setOf("expertise.programme_fit", "enterprise.project_types"),
            title = "Research fit and enterprise projects"
        ),
        IntentGroupTitle(
            intentKeys = setOf("company.legal_name", "company.registered_location"),
            title = "Company details"
        ),
        IntentGroupTitle(
            intentKeys = setOf("programme.purpose", "programme.structure"),
            title = "Programme purpose and structure"
        ),
        IntentGroupTitle(
            intentKeys = setOf("researcher.selection", "enterprise.matching"),
            title = "Selection and enterprise matching"
        ),
        IntentGroupTitle(
            intentKeys = setOf("role.responsibilities", "role.deliverables"),
            title = "Responsibilities and deliverables"
        ),
        IntentGroupTitle(
            intentKeys = setOf(
                "contract.terms",
                "finance.arrangements",
                "finance.compensation_structure",
                "ip.arrangements",
                "publication.authorship",
                "confidentiality.research"
            ),
            title = "Contractual, financial and IP arrangements"
        ),
        IntentGroupTitle(
            intentKeys = setOf("application.next_stages", "work.time_commitment", "work.advisory_duration"),
            title = "Commitment, duration and next stages"
        )
    )

    fun resolveGroupTitle(intentKeys: List<String>, fallbackText: String): String {
        if (intentKeys.isEmpty()) {
            return AiReplyPointByPointComposer.cleanHeading(fallbackText)
        }
        if (intentKeys.size == 1 && intentKeys[0] == "general.answer") {
            return AiReplyPointByPointComposer.cleanHeading(fallbackText)
        }
        val keySet = intentKeys.toSet()

        if (keySet.size == 1) {
            val def = definitions.firstOrNull { it.key in keySet }
            if (def != null) {
                return def.title
            }
            return AiReplyPointByPointComposer.cleanHeading(fallbackText)
        }

        val bestMatch = groupTitles
            .filter { keySet.all { k -> k in it.intentKeys } }
            .maxByOrNull { it.intentKeys.count { k -> k in keySet } }
        if (bestMatch != null) {
            return bestMatch.title
        }
        val def = definitions.firstOrNull { it.key in keySet }
        return if (def != null) def.title else AiReplyPointByPointComposer.cleanHeading(fallbackText)
    }

    val definitions: List<RequestIntentDefinition> = listOf(
        RequestIntentDefinition(
            key = "expertise.programme_fit",
            title = "Research fit and enterprise projects",
            requestAliases = listOf(
                "research background",
                "research profile",
                "research fit",
                "does my research",
                "does my expertise",
                "expertise fall within"
            ),
            requiredCoverageKeys = listOf("programme.scope"),
            requiresProfile = true
        ),
        RequestIntentDefinition(
            key = "enterprise.project_types",
            title = "Enterprise project types",
            requestAliases = listOf(
                "enterprise projects", "enterprise project", "types of projects", "types of enterprise",
                "project type", "project types",
                "types of chinese enterprises", "types of chinese companies",
                "examples of enterprise", "examples of enterprises",
                "what enterprise", "what enterprises",
                "chinese enterprises involved", "chinese companies involved"
            ),
            requiredCoverageKeys = listOf("enterprise.project_types")
        ),
        RequestIntentDefinition(
            key = "company.legal_name",
            title = "Company legal name",
            requestAliases = listOf("full name", "legal name", "company name", "name of your company", "your company name"),
            requiredCoverageKeys = listOf("company.legal_name")
        ),
        RequestIntentDefinition(
            key = "company.registered_location",
            title = "Registered location",
            requestAliases = listOf("registered location", "registered address", "company registration", "where is your company", "where are you based"),
            requiredCoverageKeys = listOf("company.registered_location")
        ),
        RequestIntentDefinition(
            key = "programme.purpose",
            title = "Programme purpose",
            requestAliases = listOf(
                "purpose of the program", "programme purpose", "what is the program", "objectives",
                "purpose and structure of the programme", "purpose and structure of the program"
            ),
            requiredCoverageKeys = listOf("programme.purpose")
        ),
        RequestIntentDefinition(
            key = "programme.structure",
            title = "Programme structure",
            requestAliases = listOf(
                "structure of the program", "how is the program", "programme structure", "program structure",
                "purpose and structure of the programme", "purpose and structure of the program"
            ),
            requiredCoverageKeys = listOf("programme.structure"),
            alternativeCoverageKeys = listOf("programme.tracks")
        ),
        RequestIntentDefinition(
            key = "researcher.selection",
            title = "Researcher selection",
            requestAliases = listOf("selected", "selection process", "how are researchers selected", "criteria", "eligibility", "how do you select"),
            requiredCoverageKeys = listOf("researcher.selection")
        ),
        RequestIntentDefinition(
            key = "enterprise.matching",
            title = "Enterprise matching",
            requestAliases = listOf("matched", "matching process", "how are researchers matched", "how do you match", "partner enterprise"),
            requiredCoverageKeys = listOf("enterprise.matching")
        ),
        RequestIntentDefinition(
            key = "role.responsibilities",
            title = "Responsibilities",
            requestAliases = listOf("responsibilities", "duties", "what would i do", "my responsibility", "my responsibilities"),
            requiredCoverageKeys = listOf("role.responsibilities")
        ),
        RequestIntentDefinition(
            key = "role.deliverables",
            title = "Deliverables",
            requestAliases = listOf("deliverables", "outputs", "milestones", "reports", "what will be expected"),
            requiredCoverageKeys = listOf("role.deliverables")
        ),
        RequestIntentDefinition(
            key = "contract.terms",
            title = "Contractual arrangements",
            requestAliases = listOf(
                "contract terms", "contractual", "contract arrangement", "labor contract",
                "formal agreement", "formal contract", "before any collaboration begins",
                "before collaboration", "before cooperation", "sign a contract",
                "contract signing", "will there be a contract"
            ),
            requiredCoverageKeys = listOf("contract.terms"),
            alternativeCoverageKeys = listOf("contract.party")
        ),
        RequestIntentDefinition(
            key = "finance.arrangements",
            title = "Financial arrangements",
            requestAliases = listOf(
                "financial", "compensation", "salary", "funding", "payment", "how much",
                "advisory role compensated", "is the advisory role compensated",
                "compensated", "paid", "remuneration", "stipend", "honorarium",
                "whether this is a paid role", "is this a paid position",
                "costs", "obligations", "cost", "any costs", "any fees"
            ),
            requiredCoverageKeys = listOf("finance.government_funding"),
            alternativeCoverageKeys = listOf("finance.enterprise_compensation")
        ),
        RequestIntentDefinition(
            key = "finance.compensation_structure",
            title = "Compensation structure",
            requestAliases = listOf(
                "compensation structure", "salary structure",
                "remuneration structure", "how is the compensation structured",
                "compensation breakdown", "amount breakdown",
                "how is the salary", "payment structure",
                "what is the compensation", "compensation details"
            ),
            requiredCoverageKeys = listOf("finance.compensation_structure")
        ),
        RequestIntentDefinition(
            key = "ip.arrangements",
            title = "IP arrangements",
            requestAliases = listOf("intellectual property", "ip rights", "ip arrangements", "patent", "who owns", "ownership"),
            requiredCoverageKeys = listOf("ip.arrangements")
        ),
        RequestIntentDefinition(
            key = "publication.authorship",
            title = "Publication authorship",
            requestAliases = listOf(
                "publication authorship", "publication rights", "authorship",
                "author order", "publishing rights", "right to publish"
            ),
            requiredCoverageKeys = listOf("publication.authorship")
        ),
        RequestIntentDefinition(
            key = "confidentiality.research",
            title = "Research confidentiality",
            requestAliases = listOf(
                "research confidentiality", "confidentiality arrangements",
                "confidentiality managed", "confidentiality policy", "research data confidentiality"
            ),
            requiredCoverageKeys = listOf("confidentiality.research")
        ),
        RequestIntentDefinition(
            key = "application.next_stages",
            title = "Next stages",
            requestAliases = listOf("next stages", "next steps", "what happens next", "application process", "timeline"),
            requiredCoverageKeys = listOf("application.steps")
        ),
        RequestIntentDefinition(
            key = "work.time_commitment",
            title = "Time commitment",
            requestAliases = listOf(
                "time commitment", "time requirement", "how much time",
                "weekly hours", "monthly hours", "hours per week",
                "hours per month", "level of involvement",
                "how many hours", "time involved", "how involved"
            ),
            requiredCoverageKeys = listOf("work.time_commitment")
        ),
        RequestIntentDefinition(
            key = "work.advisory_duration",
            title = "Advisory project duration",
            requestAliases = listOf(
                "typical duration", "duration of advisory projects",
                "advisory project duration", "how long advisory project",
                "project duration", "how long do projects last",
                "length of advisory", "how long is a typical project"
            ),
            requiredCoverageKeys = listOf("work.advisory_duration")
        )
    )

    private val timingAliases = listOf("timeline", "when", "how long", "duration", "dates", "deadline", "time frame")
    private val advisoryDurationAliases = listOf(
        "typical duration", "duration of advisory projects", "advisory project duration",
        "how long advisory project", "project duration", "how long do projects last",
        "length of advisory", "how long is a typical project"
    )
    /** Explicit project-type ask phrases — keep enterprise.project_types even alongside selection/matching. */
    private val explicitProjectTypeAliases = listOf(
        "types of projects", "types of enterprise", "project type", "project types",
        "types of chinese enterprises", "types of chinese companies",
        "examples of enterprise", "examples of enterprises",
        "what enterprise", "what enterprises"
    )
    private val urlPattern = Regex("""https?://\S+|[?&]\w+=\S+""", RegexOption.IGNORE_CASE)
    private val dashPattern = Regex("""[\u002D\u2013\u2014\u2015]+""")
    private val programmePattern = Regex("""\bprogramme\b""")

    private fun canonicalize(text: String): String {
        // I-1: case-insensitive URL/query mask first, then lowercase + dash/whitespace/programme normalize
        val urlMasked = urlPattern.replace(text, " ")
        val lower = urlMasked.lowercase()
        val dashNormalized = dashPattern.replace(lower, " ")
        val whitespaceCollapsed = dashNormalized.replace(Regex("""\s+"""), " ").trim()
        return programmePattern.replace(whitespaceCollapsed, "program")
    }

    fun matchIntents(requestText: String): List<RequestIntentDefinition> {
        val canonical = canonicalize(requestText)
        val matched = definitions.filter { def ->
            def.requestAliases.any { alias -> wordBoundaryContains(canonical, canonicalize(alias)) }
        }
        val disambiguated = disambiguateSelectionMatchingProjectTypes(canonical, matched)
        val asksTiming = timingAliases.any { wordBoundaryContains(canonical, it) }
        val asksAdvisoryDuration = advisoryDurationAliases.any { alias ->
            wordBoundaryContains(canonical, canonicalize(alias))
        }
        val hasWorkIntents = disambiguated.any {
            it.key == "work.time_commitment" || it.key == "work.advisory_duration"
        }

        val result = if (disambiguated.isEmpty()) {
            listOf(
                RequestIntentDefinition(
                    key = "general.answer",
                    title = "General answer",
                    requestAliases = emptyList(),
                    requiredCoverageKeys = emptyList()
                )
            )
        } else {
            disambiguated.map { def ->
                when {
                    def.key == "application.next_stages" && asksTiming && !hasWorkIntents -> {
                        def.copy(requiredCoverageKeys = listOf("application.steps", "application.timeline"))
                    }
                    else -> def
                }
            }
        }
        return result
    }

    /**
     * When a request already asks selection+matching, a bare "enterprise project(s)" object
     * is the matching complement — not a separate project-types question — unless an explicit
     * type phrase is also present.
     */
    private fun disambiguateSelectionMatchingProjectTypes(
        canonical: String,
        matched: List<RequestIntentDefinition>
    ): List<RequestIntentDefinition> {
        val keys = matched.map { it.key }.toSet()
        if ("researcher.selection" !in keys ||
            "enterprise.matching" !in keys ||
            "enterprise.project_types" !in keys
        ) {
            return matched
        }
        val hasExplicitProjectTypeAsk = explicitProjectTypeAliases.any { alias ->
            wordBoundaryContains(canonical, canonicalize(alias))
        }
        if (hasExplicitProjectTypeAsk) {
            return matched
        }
        return matched.filter { it.key != "enterprise.project_types" }
    }

    private fun wordBoundaryContains(text: String, phrase: String): Boolean {
        val escaped = Regex.escape(phrase)
        return Regex("\\b$escaped\\b").containsMatchIn(text)
    }

    fun resolveIntentEvidence(
        intent: RequestIntentDefinition,
        assignedRuleIds: List<Long>,
        profileSufficient: Boolean
    ): RequestIntentCoverage {
        if (intent.requiresProfile && !profileSufficient) {
            return RequestIntentCoverage(
                intentKey = intent.key,
                title = intent.title,
                requiredCoverageKeys = emptyList(),
                evidenceRuleIds = emptyList(),
                status = "MISSING",
                missingEvidenceKeys = listOf(intent.key, "profile"),
                requiresResearchContext = true
            )
        }

        val evidenceRuleIds = assignedRuleIds.distinct()
        val status = if (evidenceRuleIds.isNotEmpty()) "SUPPORTED" else "MISSING"
        return RequestIntentCoverage(
            intentKey = intent.key,
            title = intent.title,
            requiredCoverageKeys = emptyList(),
            evidenceRuleIds = evidenceRuleIds,
            status = status,
            missingEvidenceKeys = if (evidenceRuleIds.isEmpty()) listOf(intent.key) else emptyList(),
            requiresResearchContext = intent.requiresProfile
        )
    }

    fun assignRulesToIntents(
        rules: List<QaRule>,
        intents: List<RequestIntentDefinition>
    ): Map<String, List<QaRule>> {
        val catalogOrder = definitions.mapIndexed { index, def -> def.key to index }.toMap()
        val intentKeys = intents.map { it.key }.toSet()
        val buckets = intents.associate { it.key to mutableListOf<QaRule>() }.toMutableMap()

        rules.forEach { rule ->
            val targetKey = selectIntentKeyForRule(rule, intents, catalogOrder, intentKeys)
            if (targetKey != null) {
                buckets.getOrPut(targetKey) { mutableListOf() }.add(rule)
            }
        }

        return buckets.mapValues { (_, assigned) ->
            assigned.sortedWith(compareBy({ it.priority }, { it.id ?: Long.MAX_VALUE }))
        }
    }

    fun scoreRuleIntentAlignment(rule: QaRule, intent: RequestIntentDefinition): Int {
        val keywords = QaFactKeywordMatcher.parseKeywords(rule)
        if (keywords.isEmpty()) {
            return 0
        }
        val phrases = (listOf(intent.title) + intent.requestAliases).map { canonicalize(it) }
        var score = 0
        keywords.forEach { keyword ->
            phrases.forEach { phrase ->
                if (phrase.contains(keyword)) {
                    score++
                }
            }
        }
        return score
    }

    private fun selectIntentKeyForRule(
        rule: QaRule,
        intents: List<RequestIntentDefinition>,
        catalogOrder: Map<String, Int>,
        intentKeys: Set<String>
    ): String? {
        val scored = intents.map { intent ->
            intent.key to scoreRuleIntentAlignment(rule, intent)
        }.filter { (_, score) -> score > 0 }

        if (scored.isEmpty()) {
            return if ("general.answer" in intentKeys) "general.answer" else null
        }

        val bestScore = scored.maxOf { it.second }
        val tied = scored.filter { it.second == bestScore }
        return tied.minByOrNull { catalogOrder[it.first] ?: Int.MAX_VALUE }?.first
    }

    @Deprecated("Coverage keys are no longer used for grounded fact selection")
    fun resolveIntentCoverage(
        intent: RequestIntentDefinition,
        candidateRuleIds: List<Long>,
        promptSet: Set<Long>,
        ruleCoverageKeys: Map<Long, List<String>>,
        profileSufficient: Boolean
    ): RequestIntentCoverage {
        val validRuleIds = candidateRuleIds.filter { it in promptSet }
        if (validRuleIds.isEmpty()) {
            return RequestIntentCoverage(
                intentKey = intent.key,
                title = intent.title,
                requiredCoverageKeys = intent.requiredCoverageKeys,
                evidenceRuleIds = emptyList(),
                status = "MISSING",
                missingEvidenceKeys = intent.requiredCoverageKeys,
                requiresResearchContext = intent.requiresProfile
            )
        }

        if (intent.requiresProfile && !profileSufficient) {
            return RequestIntentCoverage(
                intentKey = intent.key,
                title = intent.title,
                requiredCoverageKeys = intent.requiredCoverageKeys,
                evidenceRuleIds = emptyList(),
                status = "MISSING",
                missingEvidenceKeys = intent.requiredCoverageKeys + "profile",
                requiresResearchContext = true
            )
        }

        if (intent.key == "general.answer" || intent.requiredCoverageKeys.isEmpty()) {
            val evidence = validRuleIds.firstOrNull()?.let { listOf(it) } ?: emptyList()
            return RequestIntentCoverage(
                intentKey = intent.key,
                title = intent.title,
                requiredCoverageKeys = emptyList(),
                evidenceRuleIds = evidence,
                status = if (evidence.isNotEmpty()) "SUPPORTED" else "MISSING",
                missingEvidenceKeys = emptyList(),
                requiresResearchContext = false
            )
        }

        val evidenceRuleIds = validRuleIds.filter { ruleId ->
            val keys = ruleCoverageKeys[ruleId].orEmpty()
            val allRequired = intent.requiredCoverageKeys + intent.alternativeCoverageKeys
            intent.requiredCoverageKeys.any { req -> req in keys } ||
                intent.alternativeCoverageKeys.any { alt -> alt in keys }
        }

        val coveredKeys = mutableSetOf<String>()
        if (evidenceRuleIds.isNotEmpty()) {
            evidenceRuleIds.forEach { ruleId ->
                val keys = ruleCoverageKeys[ruleId].orEmpty()
                coveredKeys.addAll(keys)
            }
        }

        val missing = intent.requiredCoverageKeys.filter { req ->
            req !in coveredKeys && intent.alternativeCoverageKeys.none { alt -> alt in coveredKeys }
        }

        val status = when {
            intent.requiredCoverageKeys.isEmpty() -> "SUPPORTED"
            missing.isEmpty() -> "SUPPORTED"
            missing.size == intent.requiredCoverageKeys.size -> "MISSING"
            else -> "PARTIAL"
        }

        return RequestIntentCoverage(
            intentKey = intent.key,
            title = intent.title,
            requiredCoverageKeys = intent.requiredCoverageKeys,
            evidenceRuleIds = evidenceRuleIds,
            status = status,
            missingEvidenceKeys = missing,
            requiresResearchContext = intent.requiresProfile
        )
    }
}
