package com.weibo.talentintroduction.llm.service

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
            intentKeys = setOf("contract.terms", "finance.arrangements", "ip.arrangements"),
            title = "Contractual, financial and IP arrangements"
        ),
        IntentGroupTitle(
            intentKeys = setOf("application.next_stages"),
            title = "Next stages"
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
            requestAliases = listOf("research background", "research profile", "research fit", "does my research"),
            requiredCoverageKeys = listOf("programme.scope"),
            requiresProfile = true
        ),
        RequestIntentDefinition(
            key = "enterprise.project_types",
            title = "Enterprise project types",
            requestAliases = listOf("enterprise projects", "enterprise project", "types of projects", "types of enterprise", "project type", "project types"),
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
            requestAliases = listOf("purpose of the program", "programme purpose", "what is the program", "objectives"),
            requiredCoverageKeys = listOf("programme.purpose")
        ),
        RequestIntentDefinition(
            key = "programme.structure",
            title = "Programme structure",
            requestAliases = listOf("structure of the program", "how is the program", "programme structure", "program structure"),
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
            requestAliases = listOf("contract terms", "contractual", "contract arrangement", "labor contract"),
            requiredCoverageKeys = listOf("contract.terms"),
            alternativeCoverageKeys = listOf("contract.party")
        ),
        RequestIntentDefinition(
            key = "finance.arrangements",
            title = "Financial arrangements",
            requestAliases = listOf("financial", "compensation", "salary", "funding", "payment", "how much"),
            requiredCoverageKeys = listOf("finance.government_funding"),
            alternativeCoverageKeys = listOf("finance.enterprise_compensation")
        ),
        RequestIntentDefinition(
            key = "ip.arrangements",
            title = "IP arrangements",
            requestAliases = listOf("intellectual property", "ip rights", "ip arrangements", "patent", "who owns", "ownership"),
            requiredCoverageKeys = listOf("ip.arrangements")
        ),
        RequestIntentDefinition(
            key = "application.next_stages",
            title = "Next stages",
            requestAliases = listOf("next stages", "next steps", "what happens next", "application process", "timeline"),
            requiredCoverageKeys = listOf("application.steps")
        )
    )

    private val timingAliases = listOf("timeline", "when", "how long", "duration", "dates", "deadline", "time frame")
    private val urlPattern = Regex("""https?://\S+|[?&]\w+=\S+""")

    fun matchIntents(requestText: String): List<RequestIntentDefinition> {
        val normalized = requestText.lowercase()
        val cleaned = urlPattern.replace(normalized, " ")
        val matched = definitions.filter { def ->
            def.requestAliases.any { alias -> wordBoundaryContains(cleaned, alias) }
        }
        val asksTiming = timingAliases.any { wordBoundaryContains(cleaned, it) }

        val result = if (matched.isEmpty()) {
            listOf(
                RequestIntentDefinition(
                    key = "general.answer",
                    title = "General answer",
                    requestAliases = emptyList(),
                    requiredCoverageKeys = emptyList()
                )
            )
        } else {
            matched.map { def ->
                if (def.key == "application.next_stages" && asksTiming) {
                    def.copy(requiredCoverageKeys = listOf("application.steps", "application.timeline"))
                } else {
                    def
                }
            }
        }
        return result
    }

    private fun wordBoundaryContains(text: String, phrase: String): Boolean {
        val escaped = Regex.escape(phrase)
        return Regex("\\b$escaped\\b").containsMatchIn(text)
    }

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
