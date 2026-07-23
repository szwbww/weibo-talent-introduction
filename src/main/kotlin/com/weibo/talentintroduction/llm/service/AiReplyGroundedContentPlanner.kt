package com.weibo.talentintroduction.llm.service

import org.springframework.stereotype.Service

data class GroundedClaimPlan(
    val claimKey: String,
    val requestIndex: Int,
    val intentKey: String,
    val sourceIds: List<Long>
)

data class GroundedParagraphPlan(
    val paragraphIndex: Int,
    val claimKeys: List<String>
)

data class GroundedMissingFactPlan(
    val requestIndex: Int,
    val intentKeys: List<String>
)

data class GroundedContentPlan(
    val claims: List<GroundedClaimPlan>,
    val paragraphs: List<GroundedParagraphPlan>,
    val missingFacts: List<GroundedMissingFactPlan>,
    val allowedActions: Set<AiReplyAction>,
    val requiresReview: Boolean
)

@Service
class AiReplyGroundedContentPlanner {

    fun buildPlan(
        requestFacts: List<RequestFactItem>,
        allowedActions: Set<AiReplyAction>
    ): GroundedContentPlan {
        val claimKeys = linkedSetOf<String>()
        val claims = mutableListOf<GroundedClaimPlan>()
        val missingFacts = mutableListOf<GroundedMissingFactPlan>()
        val requestClaimKeys = linkedMapOf<Int, MutableList<String>>()

        for (item in requestFacts.sortedBy { it.index }) {
            val itemClaimKeys = mutableListOf<String>()
            val supportedIntents = item.intents.filter { it.status == "SUPPORTED" }

            if (item.status == RequestGroundingStatus.UNSUPPORTED) {
                val missingIntentKeys = if (item.intents.isNotEmpty()) {
                    item.intents.map { it.intentKey }
                } else {
                    emptyList()
                }
                missingFacts += GroundedMissingFactPlan(
                    requestIndex = item.index,
                    intentKeys = missingIntentKeys
                )
                continue
            }

            if (supportedIntents.isNotEmpty()) {
                for (intent in supportedIntents) {
                    val claimKey = "r${item.index}:${intent.intentKey}"
                    if (claimKeys.add(claimKey)) {
                        val sourceIds = intent.evidenceRuleIds.distinct()
                        claims += GroundedClaimPlan(
                            claimKey = claimKey,
                            requestIndex = item.index,
                            intentKey = intent.intentKey,
                            sourceIds = sourceIds
                        )
                        itemClaimKeys += claimKey
                    }
                }
            } else if (item.factRuleIds.isNotEmpty()) {
                val claimKey = "r${item.index}:general.answer"
                if (claimKeys.add(claimKey)) {
                    claims += GroundedClaimPlan(
                        claimKey = claimKey,
                        requestIndex = item.index,
                        intentKey = "general.answer",
                        sourceIds = item.factRuleIds
                    )
                    itemClaimKeys += claimKey
                }
            }

            val missingIntents = item.intents.filter { it.status != "SUPPORTED" }
            if (missingIntents.isNotEmpty()) {
                missingFacts += GroundedMissingFactPlan(
                    requestIndex = item.index,
                    intentKeys = missingIntents.map { it.intentKey }
                )
            }

            if (itemClaimKeys.isNotEmpty()) {
                requestClaimKeys[item.index] = itemClaimKeys
            }
        }

        val paragraphs = buildParagraphs(requestClaimKeys)

        val requiresReview = missingFacts.isNotEmpty() ||
            requestFacts.any { it.status == RequestGroundingStatus.PARTIAL } ||
            requestFacts.any { it.status == RequestGroundingStatus.UNSUPPORTED }

        val trustGap = hasBlockingTrustGap(requestFacts)
        val effectiveActions = AiReplyActionPolicy.restrictForTrustState(allowedActions, trustGap)

        return GroundedContentPlan(
            claims = claims,
            paragraphs = paragraphs,
            missingFacts = missingFacts,
            allowedActions = effectiveActions,
            requiresReview = requiresReview
        )
    }

    fun isBlockingTrustIntent(intentKey: String): Boolean {
        return BLOCKING_TRUST_PREFIXES.any { prefix ->
            intentKey.startsWith(prefix)
        }
    }

    fun hasBlockingTrustGap(requestFacts: List<RequestFactItem>): Boolean {
        return requestFacts.any { item ->
            item.intents.any { intent ->
                intent.status != "SUPPORTED" && isBlockingTrustIntent(intent.intentKey)
            }
        }
    }

    private fun buildParagraphs(
        requestClaimKeys: LinkedHashMap<Int, MutableList<String>>
    ): List<GroundedParagraphPlan> {
        val entries = requestClaimKeys.entries.toList()
        if (entries.isEmpty()) {
            return emptyList()
        }

        if (entries.size <= 4) {
            return entries.mapIndexed { idx, (_, claimKeys) ->
                GroundedParagraphPlan(
                    paragraphIndex = idx + 1,
                    claimKeys = claimKeys.toList()
                )
            }
        }

        val result = mutableListOf<GroundedParagraphPlan>()
        var paraIdx = 1
        var i = 0
        while (i < entries.size) {
            val remaining = entries.size - i
            val maxForThis = 4 - result.size

            if (remaining <= maxForThis) {
                for (j in i until entries.size) {
                    result += GroundedParagraphPlan(
                        paragraphIndex = paraIdx++,
                        claimKeys = entries[j].value.toList()
                    )
                }
                break
            }

            val mergeCount = if (remaining - maxForThis == 0) maxForThis else 2
            val mergedKeys = mutableListOf<String>()
            for (j in i until (i + mergeCount).coerceAtMost(entries.size)) {
                mergedKeys.addAll(entries[j].value)
            }
            result += GroundedParagraphPlan(
                paragraphIndex = paraIdx++,
                claimKeys = mergedKeys.toList()
            )
            i += mergeCount

            if (result.size == 4 && i < entries.size) {
                for (j in i until entries.size) {
                    result.last().let { last ->
                        val updated = GroundedParagraphPlan(
                            paragraphIndex = last.paragraphIndex,
                            claimKeys = last.claimKeys + entries[j].value
                        )
                        result[result.size - 1] = updated
                    }
                }
                break
            }
        }

        return result
    }

    companion object {
        private val TRUST_SENSITIVE_PREFIXES = setOf(
            "company.",
            "agency.",
            "finance.",
            "confidentiality.",
            "contract.",
            "ip.",
            "publication.",
            "fees."
        )

        fun isTrustSensitive(intentKey: String): Boolean {
            return TRUST_SENSITIVE_PREFIXES.any { prefix ->
                intentKey.startsWith(prefix)
            }
        }

        fun hasTrustSensitiveNoFacts(requestFacts: List<RequestFactItem>): Boolean {
            return requestFacts.any { item ->
                item.intents.any { intent ->
                    intent.status != "SUPPORTED" && isTrustSensitive(intent.intentKey)
                }
            }
        }

        private val BLOCKING_TRUST_PREFIXES = setOf(
            "company.",
            "agency.",
            "finance.",
            "fees."
        )
    }
}
