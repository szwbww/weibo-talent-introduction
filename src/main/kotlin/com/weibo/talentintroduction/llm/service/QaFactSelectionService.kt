package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.QaRequestExtractor
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class QaFactSelectionService(
    private val qaRuleRepository: QaRuleRepository
) {
    fun select(
        inboundText: String,
        selectedRuleIds: List<Long>?,
        researchProfileSufficient: Boolean
    ): ResolvedQaRules {
        val requestTexts = QaRequestExtractor.extract(inboundText).map { it.text }
        val matchableRules = qaRuleRepository.findAllEnabledOrdered()
            .filter { it.isMatchable() && it.answerBody.trim().isNotBlank() }

        val explicitRules = selectedRuleIds?.let { validateExplicitSelection(it) }
        if (explicitRules != null) {
            validateExplicitRulesMatchRequests(explicitRules, requestTexts)
        }
        val promptPool = explicitRules ?: matchableRules
        val promptSet = promptPool.mapNotNull { it.id }.toSet()

        val requestFacts = if (requestTexts.isEmpty()) {
            emptyList()
        } else {
            requestTexts.mapIndexed { idx, requestText ->
                buildRequestFact(
                    index = idx + 1,
                    requestText = requestText,
                    promptPool = promptPool,
                    promptSet = promptSet,
                    researchProfileSufficient = researchProfileSufficient
                )
            }
        }

        val sendQaRuleIds = orderEvidenceRuleIds(requestFacts, promptPool)

        return ResolvedQaRules(
            sendQaRuleIds = sendQaRuleIds,
            promptRuleIds = sendQaRuleIds,
            requestFacts = requestFacts,
            unsupportedRequests = requestFacts
                .filter { it.status == RequestGroundingStatus.UNSUPPORTED }
                .map { it.requestText },
            requestCount = requestFacts.size,
            groundedRequestCount = requestFacts.count {
                it.status == RequestGroundingStatus.GROUNDED ||
                    it.status == RequestGroundingStatus.PARTIAL
            }
        )
    }

    /**
     * Workbench-only selection entry (Task 2). Unlike [select], it produces a
     * canonical summary-to-fact matrix in which every fact is consumed at most
     * once across the whole mail:
     * - [selectionsByRequest]: explicit matrix mode, one ordered rule-id list per
     *   canonical request index (aligned with [QaRequestExtractor] order). Each
     *   request sees only its own rules as prompt pool and its resulting
     *   `factRuleIds` must equal the explicit list, otherwise the selection is
     *   invalid (I-3).
     * - [requestedFactIds]: legacy flat mode; each id is assigned to the first
     *   request that both keyword-matches and accepts it into supported evidence,
     *   and every id must be consumed exactly once (I-4).
     * - both null: auto mode with the same unique-consumption semantics.
     * [selectionsByRequest] and [requestedFactIds] are mutually exclusive.
     */
    fun selectForWorkbench(
        inboundText: String,
        selectionsByRequest: List<List<Long>>?,
        requestedFactIds: List<Long>?,
        researchProfileSufficient: Boolean
    ): ResolvedQaRules {
        if (selectionsByRequest != null && requestedFactIds != null) {
            throw TrustReplyWorkbenchException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "TRUST_REPLY_FACT_SELECTION_AMBIGUOUS"
            )
        }
        val requestTexts = QaRequestExtractor.extract(inboundText).map { it.text }
        return when {
            selectionsByRequest != null -> resolveMatrixSelection(
                requestTexts = requestTexts,
                selectionsByRequest = selectionsByRequest,
                researchProfileSufficient = researchProfileSufficient
            )
            requestedFactIds != null -> resolveLegacySelection(
                requestTexts = requestTexts,
                requestedFactIds = requestedFactIds,
                researchProfileSufficient = researchProfileSufficient
            )
            else -> {
                val matchableRules = qaRuleRepository.findAllEnabledOrdered()
                    .filter { it.isMatchable() && it.answerBody.trim().isNotBlank() }
                resolveAutoSelection(
                    requestTexts = requestTexts,
                    matchableRules = matchableRules,
                    researchProfileSufficient = researchProfileSufficient
                )
            }
        }
    }

    private fun resolveMatrixSelection(
        requestTexts: List<String>,
        selectionsByRequest: List<List<Long>>,
        researchProfileSufficient: Boolean
    ): ResolvedQaRules {
        if (requestTexts.size != selectionsByRequest.size) {
            throw TrustReplyWorkbenchException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "TRUST_REPLY_FACT_SELECTION_INVALID"
            )
        }
        checkWorkbenchUniqueness(selectionsByRequest.flatten())
        val requestFacts = requestTexts.mapIndexed { idx, text ->
            val explicitIds = selectionsByRequest[idx]
            val explicitRules = if (explicitIds.isEmpty()) {
                emptyList()
            } else {
                try {
                    validateExplicitSelection(explicitIds)
                } catch (_: IllegalArgumentException) {
                    throw TrustReplyWorkbenchException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "TRUST_REPLY_FACT_SELECTION_INVALID"
                    )
                }
            }
            val promptSet = explicitRules.mapNotNull { it.id }.toSet()
            val item = buildRequestFact(
                index = idx + 1,
                requestText = text,
                promptPool = explicitRules,
                promptSet = promptSet,
                researchProfileSufficient = researchProfileSufficient
            )
            if (item.factRuleIds != explicitIds) {
                throw TrustReplyWorkbenchException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "TRUST_REPLY_FACT_SELECTION_INVALID"
                )
            }
            item
        }
        return workbenchResult(requestFacts)
    }

    private fun resolveLegacySelection(
        requestTexts: List<String>,
        requestedFactIds: List<Long>,
        researchProfileSufficient: Boolean
    ): ResolvedQaRules {
        if (requestedFactIds.isEmpty()) {
            // Empty flat selection means "no facts assigned": requests keep their
            // intent-derived status without fabricated fallback evidence (Task 2.5).
            return workbenchResult(
                requestTexts.mapIndexed { idx, text ->
                    buildRequestFact(
                        index = idx + 1,
                        requestText = text,
                        promptPool = emptyList(),
                        promptSet = emptySet(),
                        researchProfileSufficient = researchProfileSufficient
                    )
                }
            )
        }
        checkWorkbenchUniqueness(requestedFactIds)
        val explicitRules = try {
            validateExplicitSelection(requestedFactIds)
        } catch (_: IllegalArgumentException) {
            throw TrustReplyWorkbenchException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "TRUST_REPLY_FACT_SELECTION_INVALID"
            )
        }
        val remaining = explicitRules.toMutableList()
        val requestFacts = requestTexts.mapIndexed { idx, text ->
            val pool = remaining.toList()
            val promptSet = pool.mapNotNull { it.id }.toSet()
            val item = buildRequestFact(
                index = idx + 1,
                requestText = text,
                promptPool = pool,
                promptSet = promptSet,
                researchProfileSufficient = researchProfileSufficient
            )
            val consumedIds = item.factRuleIds.toSet()
            remaining.removeAll { rule -> rule.id in consumedIds }
            item
        }
        if (remaining.isNotEmpty()) {
            throw TrustReplyWorkbenchException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "TRUST_REPLY_FACT_SELECTION_INVALID"
            )
        }
        return workbenchResult(requestFacts)
    }

    private fun resolveAutoSelection(
        requestTexts: List<String>,
        matchableRules: List<QaRule>,
        researchProfileSufficient: Boolean
    ): ResolvedQaRules {
        val remaining = matchableRules.toMutableList()
        val requestFacts = requestTexts.mapIndexed { idx, text ->
            val pool = remaining.toList()
            val promptSet = pool.mapNotNull { it.id }.toSet()
            val item = buildRequestFact(
                index = idx + 1,
                requestText = text,
                promptPool = pool,
                promptSet = promptSet,
                researchProfileSufficient = researchProfileSufficient
            )
            val consumedIds = item.factRuleIds.toSet()
            remaining.removeAll { rule -> rule.id in consumedIds }
            item
        }
        return workbenchResult(requestFacts)
    }

    private fun workbenchResult(requestFacts: List<RequestFactItem>): ResolvedQaRules {
        // I-1: the ordered union derives from the canonical per-request lists and is
        // never fed back into per-request pools.
        val sendIds = requestFacts.sortedBy { it.index }.flatMap { it.factRuleIds }.distinct()
        return ResolvedQaRules(
            sendQaRuleIds = sendIds,
            promptRuleIds = sendIds,
            requestFacts = requestFacts,
            unsupportedRequests = requestFacts
                .filter { it.status == RequestGroundingStatus.UNSUPPORTED }
                .map { it.requestText },
            requestCount = requestFacts.size,
            groundedRequestCount = requestFacts.count {
                it.status == RequestGroundingStatus.GROUNDED ||
                    it.status == RequestGroundingStatus.PARTIAL
            }
        )
    }

    private fun checkWorkbenchUniqueness(ruleIds: List<Long>) {
        val seen = hashSetOf<Long>()
        ruleIds.forEach { ruleId ->
            if (!seen.add(ruleId)) {
                throw TrustReplyWorkbenchException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "TRUST_REPLY_FACT_ALREADY_ASSIGNED"
                )
            }
        }
    }

    internal fun validateExplicitSelection(ruleIds: List<Long>): List<QaRule> {
        require(ruleIds.isNotEmpty()) { "qaRuleIds must not be empty when provided" }
        return ruleIds.map { ruleId ->
            val rule = qaRuleRepository.findById(ruleId).orElse(null)
                ?: throw IllegalArgumentException("QA rule not found: $ruleId")
            require(rule.enabled) { "QA rule is disabled: $ruleId" }
            require(rule.replyPolicyEnum() != QaReplyPolicy.NEVER) {
                "QA rule is not available for outbound reply: $ruleId"
            }
            require(rule.answerBody.trim().isNotBlank()) {
                "QA rule has no fact body: $ruleId"
            }
            rule
        }
    }

    internal fun validateExplicitRulesMatchRequests(
        explicitRules: List<QaRule>,
        requestTexts: List<String>
    ) {
        if (requestTexts.isEmpty()) {
            throw IllegalArgumentException(
                "Selected QA rules cannot be assigned without extractable requests in the inbound email"
            )
        }
        val normalizedRequests = requestTexts.map { QaFactKeywordMatcher.normalize(it) }
        val unmatchedRuleIds = explicitRules.mapNotNull { rule ->
            val matchesAny = normalizedRequests.any { request ->
                QaFactKeywordMatcher.matchesRule(rule, request)
            }
            if (matchesAny) {
                null
            } else {
                rule.id
            }
        }
        if (unmatchedRuleIds.isNotEmpty()) {
            throw IllegalArgumentException(
                "Selected QA rules do not match any request in the inbound email: $unmatchedRuleIds"
            )
        }
    }

    internal fun buildRequestFact(
        index: Int,
        requestText: String,
        promptPool: List<QaRule>,
        promptSet: Set<Long>,
        researchProfileSufficient: Boolean
    ): RequestFactItem {
        val normalizedRequest = QaFactKeywordMatcher.normalize(requestText)
        val matchedIntents = AiReplyIntentCatalog.matchIntents(requestText)
        val isResearch = matchedIntents.any { it.requiresProfile }

        val candidateRules = promptPool.filter { rule ->
            rule.id != null &&
                rule.id in promptSet &&
                QaFactKeywordMatcher.matchesRule(rule, normalizedRequest)
        }

        val assignments = AiReplyIntentCatalog.assignRulesToIntents(candidateRules, matchedIntents)
        val intentCoverages = matchedIntents.map { intent ->
            val assignedIds = assignments[intent.key].orEmpty().mapNotNull { it.id }
            AiReplyIntentCatalog.resolveIntentEvidence(
                intent = intent,
                assignedRuleIds = assignedIds,
                profileSufficient = researchProfileSufficient
            )
        }

        val researchWarned = isResearch && !researchProfileSufficient
        val allMissing = intentCoverages.isNotEmpty() && intentCoverages.all { it.status == "MISSING" }
        val anySupported = intentCoverages.any { it.status == "SUPPORTED" }
        val allSupported = intentCoverages.isNotEmpty() && intentCoverages.all { it.status == "SUPPORTED" }
        val anyPartial = intentCoverages.any { it.status == "PARTIAL" }

        val status = when {
            researchWarned && !anySupported -> RequestGroundingStatus.UNSUPPORTED
            intentCoverages.isEmpty() -> RequestGroundingStatus.UNSUPPORTED
            allSupported -> RequestGroundingStatus.GROUNDED
            allMissing -> RequestGroundingStatus.UNSUPPORTED
            anySupported || anyPartial -> RequestGroundingStatus.PARTIAL
            else -> RequestGroundingStatus.UNSUPPORTED
        }

        val evidenceSet = intentCoverages
            .filter { it.status == "SUPPORTED" }
            .flatMap { it.evidenceRuleIds }
            .toSet()

        val factRuleIds = candidateRules
            .mapNotNull { it.id }
            .filter { it in evidenceSet }

        return RequestFactItem(
            index = index,
            requestText = requestText,
            factRuleIds = factRuleIds,
            status = status,
            requiresResearchContext = isResearch,
            intents = intentCoverages
        )
    }

    internal fun orderEvidenceRuleIds(
        requestFacts: List<RequestFactItem>,
        promptPool: List<QaRule>
    ): List<Long> {
        val priorityById = promptPool.mapNotNull { rule ->
            rule.id?.let { it to rule.priority }
        }.toMap()
        val ordered = linkedSetOf<Long>()
        requestFacts.forEach { item ->
            item.intents.forEach { intent ->
                intent.evidenceRuleIds
                    .sortedWith(compareBy({ priorityById[it] ?: Int.MAX_VALUE }, { it }))
                    .forEach { ordered.add(it) }
            }
        }
        return ordered.toList()
    }
}

internal object QaFactKeywordMatcher {
    fun normalize(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .replace("details", "information")
            .replace("detail", "information")
            .trim()

    fun parseKeywords(rule: QaRule): List<String> =
        rule.keywords
            .split(",")
            .map { normalize(it) }
            .filter { it.isNotBlank() }

    fun matchesRule(rule: QaRule, normalizedText: String): Boolean {
        val keywords = parseKeywords(rule)
        if (keywords.isEmpty()) {
            return false
        }
        val matchedKeywords = keywords.filter { normalizedText.contains(it) }
        return when (rule.matchMode.uppercase(Locale.ROOT)) {
            "ALL" -> matchedKeywords.size == keywords.size
            else -> matchedKeywords.isNotEmpty()
        }
    }
}
