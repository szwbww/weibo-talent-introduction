package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.llm.config.AskEnumeratorProperties
import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.QaRequestExtractor
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class QaFactSelectionService(
    private val qaRuleRepository: QaRuleRepository,
    // P2a (plan 02): nullable so the historical single-arg constructor sites
    // (unit tests) compile unchanged; Spring injects the real bean.
    private val inboundAskEnumerator: InboundAskEnumerator? = null,
    private val askEnumeratorProperties: AskEnumeratorProperties = AskEnumeratorProperties()
) {
    private val logger = LoggerFactory.getLogger(QaFactSelectionService::class.java)
    fun select(
        inboundText: String,
        selectedRuleIds: List<Long>?,
        researchProfileSufficient: Boolean
    ): ResolvedQaRules {
        val requests = extractRequests(inboundText)
        val requestTexts = requests.map { it.text }
        val matchableRules = qaRuleRepository.findAllEnabledOrdered()
            .filter { it.isMatchable() && it.answerBody.trim().isNotBlank() }

        val explicitRules = selectedRuleIds?.let { validateExplicitSelection(it) }
        if (explicitRules != null) {
            validateExplicitRulesMatchRequests(explicitRules, requestTexts)
        }
        val promptPool = explicitRules ?: matchableRules
        val promptSet = promptPool.mapNotNull { it.id }.toSet()

        // P2a (plan 02, I-6): the auto-reply path enumerates only while the
        // opt-in flag is on; otherwise it records available=false and never
        // enters client.chat. Judgement/status never depends on it (I-3).
        val enumeration = if (askEnumeratorProperties.enabledForAutoReply) {
            inboundAskEnumerator?.enumerate(inboundText) ?: AskEnumeration(false, emptyList())
        } else {
            AskEnumeration(false, emptyList())
        }

        val requestFacts = if (requestTexts.isEmpty()) {
            emptyList()
        } else {
            requests.mapIndexed { idx, unit ->
                buildRequestFact(
                    index = idx + 1,
                    requestText = unit.text,
                    promptPool = promptPool,
                    promptSet = promptSet,
                    researchProfileSufficient = researchProfileSufficient,
                    askEnumeration = enumeration,
                    requestRange = unit.range
                )
            }
        }

        val sendQaRuleIds = orderEvidenceRuleIds(requestFacts, promptPool)
        val unrecognizedAskCount = requestFacts.sumOf { it.unrecognizedAsks.size }

        val result = ResolvedQaRules(
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
            },
            unrecognizedAskCount = unrecognizedAskCount,
            enumeratorAvailable = enumeration.available,
            enumeratorEnumerated = enumeration.asks.size,
            enumeratorClaimed = enumeration.asks.size - unrecognizedAskCount
        )

        // P2a (plan 02, C-3): the auto-path [ASK_ENUM] record. The fixed field
        // names/order come from buildAskEnumLogLine; select() has no contact
        // identity, so source=AUTO / contactId=0.
        logger.info(
            buildAskEnumLogLine(
                source = "AUTO",
                contactId = 0,
                available = result.enumeratorAvailable,
                enumerated = result.enumeratorEnumerated,
                claimed = result.enumeratorClaimed,
                unrecognized = result.unrecognizedAskCount,
                kind = requests.map { it.kind.name }.distinct().sorted().joinToString(",")
            )
        )
        return result
    }

    /**
     * Read-only diagnostics partition of an explicit rule selection (计划 04,
     * T1.1). Splits [ruleIds] into: [ExplicitSelectionPartition.selectable]
     * (passes the same four availability checks as [validateExplicitSelection]
     * AND keyword-matches at least one extracted request, order preserved),
     * [ExplicitSelectionPartition.unavailable] (missing / disabled / policy
     * NEVER / blank answer body), and [ExplicitSelectionPartition.unmatched]
     * (available but matching no request). Never throws — it is the degraded
     * path's sole seam, so the shared [select] keeps its hard behaviour (I-1).
     */
    fun partitionExplicitSelection(inboundText: String, ruleIds: List<Long>): ExplicitSelectionPartition {
        val normalizedRequests = extractRequests(inboundText).map { QaFactKeywordMatcher.normalize(it.text) }
        val selectable = mutableListOf<Long>()
        val unavailable = mutableListOf<Long>()
        val unmatched = mutableListOf<Long>()
        ruleIds.forEach { ruleId ->
            val rule = qaRuleRepository.findById(ruleId).orElse(null)
            when {
                rule == null || !rule.enabled ||
                    rule.replyPolicyEnum() == QaReplyPolicy.NEVER ||
                    rule.answerBody.trim().isBlank() -> unavailable += ruleId
                normalizedRequests.any { QaFactKeywordMatcher.matchesRule(rule, it) } -> selectable += ruleId
                else -> unmatched += ruleId
            }
        }
        return ExplicitSelectionPartition(
            selectable = selectable,
            unavailable = unavailable,
            unmatched = unmatched,
            noRequests = normalizedRequests.isEmpty()
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
        val requests = extractRequests(inboundText)
        val requestTexts = requests.map { it.text }
        // P2a (plan 02, C-2/I-6): the workbench path always enumerates (never
        // gated by enabledForAutoReply); the enumerator itself is fail-open and
        // returns available=false when the LLM is off (I-4).
        val enumeration = inboundAskEnumerator?.enumerate(inboundText)
            ?: AskEnumeration(false, emptyList())
        return when {
            selectionsByRequest != null -> resolveMatrixSelection(
                requests = requests,
                selectionsByRequest = selectionsByRequest,
                researchProfileSufficient = researchProfileSufficient,
                enumeration = enumeration
            )
            requestedFactIds != null -> resolveLegacySelection(
                requests = requests,
                requestedFactIds = requestedFactIds,
                researchProfileSufficient = researchProfileSufficient,
                enumeration = enumeration
            )
            else -> {
                val matchableRules = qaRuleRepository.findAllEnabledOrdered()
                    .filter { it.isMatchable() && it.answerBody.trim().isNotBlank() }
                resolveAutoSelection(
                    requests = requests,
                    matchableRules = matchableRules,
                    researchProfileSufficient = researchProfileSufficient,
                    enumeration = enumeration
                )
            }
        }
    }

    private fun resolveMatrixSelection(
        requests: List<RequestUnit>,
        selectionsByRequest: List<List<Long>>,
        researchProfileSufficient: Boolean,
        enumeration: AskEnumeration
    ): ResolvedQaRules {
        if (requests.size != selectionsByRequest.size) {
            throw TrustReplyWorkbenchException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "TRUST_REPLY_FACT_SELECTION_INVALID"
            )
        }
        checkWorkbenchUniqueness(selectionsByRequest.flatten())
        val requestFacts = requests.mapIndexed { idx, unit ->
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
                requestText = unit.text,
                promptPool = explicitRules,
                promptSet = promptSet,
                researchProfileSufficient = researchProfileSufficient,
                askEnumeration = enumeration,
                requestRange = unit.range,
                // 计划 02 (I-1): 矩阵路径是唯一允许绕过关键词匹配的调用点——promptPool
                // 恰为运营绑定集，绕过不会外溢到自动链路。其余 4 个调用点（auto /
                // legacy 空选 / legacy / auto）靠默认值 false 逐字保持今日行为。
                operatorBound = true
            )
            // P2a (I-1/I-4/I-6): 运营绑的原样进 boundRuleIds；factRuleIds 保持
            // "系统认可的证据"语义不变。相等性校验回到比对运营输入——由本行的
            // 赋值保证它恒真，作为防御性断言保留（I-4）。
            val accepted = item.factRuleIds.toSet()
            val bound = item.copy(
                boundRuleIds = explicitIds,
                droppedBindingRuleIds = explicitIds.filter { it !in accepted }
            )
            if (bound.boundRuleIds != explicitIds) {
                throw TrustReplyWorkbenchException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "TRUST_REPLY_FACT_SELECTION_INVALID"
                )
            }
            bound
        }
        return workbenchResult(requestFacts, enumeration)
    }

    private fun resolveLegacySelection(
        requests: List<RequestUnit>,
        requestedFactIds: List<Long>,
        researchProfileSufficient: Boolean,
        enumeration: AskEnumeration
    ): ResolvedQaRules {
        if (requestedFactIds.isEmpty()) {
            // Empty flat selection means "no facts assigned": requests keep their
            // intent-derived status without fabricated fallback evidence (Task 2.5).
            return workbenchResult(
                requests.mapIndexed { idx, unit ->
                    buildRequestFact(
                        index = idx + 1,
                        requestText = unit.text,
                        promptPool = emptyList(),
                        promptSet = emptySet(),
                        researchProfileSufficient = researchProfileSufficient,
                        askEnumeration = enumeration,
                        requestRange = unit.range
                    )
                },
                enumeration
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
        val requestFacts = requests.mapIndexed { idx, unit ->
            val pool = remaining.toList()
            val promptSet = pool.mapNotNull { it.id }.toSet()
            val item = buildRequestFact(
                index = idx + 1,
                requestText = unit.text,
                promptPool = pool,
                promptSet = promptSet,
                researchProfileSufficient = researchProfileSufficient,
                askEnumeration = enumeration,
                requestRange = unit.range
            )
            val consumedIds = item.factRuleIds.toSet()
            remaining.removeAll { rule -> rule.id in consumedIds }
            // P2a (I-1): legacy 路径 boundRuleIds 取 factRuleIds（显式赋值）。
            item.copy(boundRuleIds = item.factRuleIds)
        }
        if (remaining.isNotEmpty()) {
            throw TrustReplyWorkbenchException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "TRUST_REPLY_FACT_SELECTION_INVALID"
            )
        }
        return workbenchResult(requestFacts, enumeration)
    }

    private fun resolveAutoSelection(
        requests: List<RequestUnit>,
        matchableRules: List<QaRule>,
        researchProfileSufficient: Boolean,
        enumeration: AskEnumeration
    ): ResolvedQaRules {
        val remaining = matchableRules.toMutableList()
        val requestFacts = requests.mapIndexed { idx, unit ->
            val pool = remaining.toList()
            val promptSet = pool.mapNotNull { it.id }.toSet()
            val item = buildRequestFact(
                index = idx + 1,
                requestText = unit.text,
                promptPool = pool,
                promptSet = promptSet,
                researchProfileSufficient = researchProfileSufficient,
                askEnumeration = enumeration,
                requestRange = unit.range
            )
            val consumedIds = item.factRuleIds.toSet()
            remaining.removeAll { rule -> rule.id in consumedIds }
            // P2a (I-1): auto 路径 boundRuleIds 取 factRuleIds（显式赋值）。
            item.copy(boundRuleIds = item.factRuleIds)
        }
        return workbenchResult(requestFacts, enumeration)
    }

    private fun workbenchResult(
        requestFacts: List<RequestFactItem>,
        enumeration: AskEnumeration
    ): ResolvedQaRules {
        // I-1: the ordered union derives from the canonical per-request lists and is
        // never fed back into per-request pools.
        // P2b (I-1): 外发审计只认证据（sendIds 不变）；prompt 可以多看运营绑定的事实。
        // 并集顺序固定为「证据在前、绑定补在后」，保证两者相等时 promptIds 与 sendIds
        // 逐字相同（I-4）。
        val ordered = requestFacts.sortedBy { it.index }
        // 计划 02 (I-10): D2 已于 2026-08-21 推翻——绑定事实通过成为 factRuleIds
        // 成员（I-1/I-2 路径）自动进入外发审计；此处【不得】额外并入 boundRuleIds，
        // 否则未被 I-2 采纳的绑定（droppedBindingRuleIds）也会进审计。
        val sendIds = ordered.flatMap { it.factRuleIds }.distinct()
        val promptIds = (sendIds + ordered.flatMap { it.boundRuleIds }).distinct()
        val unrecognizedAskCount = requestFacts.sumOf { it.unrecognizedAsks.size }
        return ResolvedQaRules(
            sendQaRuleIds = sendIds,
            promptRuleIds = promptIds,
            requestFacts = requestFacts,
            unsupportedRequests = requestFacts
                .filter { it.status == RequestGroundingStatus.UNSUPPORTED }
                .map { it.requestText },
            requestCount = requestFacts.size,
            groundedRequestCount = requestFacts.count {
                it.status == RequestGroundingStatus.GROUNDED ||
                    it.status == RequestGroundingStatus.PARTIAL
            },
            unrecognizedAskCount = unrecognizedAskCount,
            enumeratorAvailable = enumeration.available,
            enumeratorEnumerated = enumeration.asks.size,
            enumeratorClaimed = enumeration.asks.size - unrecognizedAskCount
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
        researchProfileSufficient: Boolean,
        askEnumeration: AskEnumeration = AskEnumeration(false, emptyList()),
        requestRange: IntRange? = null,
        // 计划 02 (I-1): 只在工作台矩阵路径为 true（resolveMatrixSelection 是唯一
        // 传 true 的调用点）。为 true 时跳过 QaFactKeywordMatcher.matchesRule——
        // 安全依据：矩阵路径的 promptPool 恰为运营绑定集，绕过不会让全库规则
        // 成为每个问题的候选证据。auto / legacy 四处调用点靠默认值 false 逐字
        // 保持今日行为（含关键词匹配）。
        operatorBound: Boolean = false
    ): RequestFactItem {
        val normalizedRequest = QaFactKeywordMatcher.normalize(requestText)
        // P2a (plan 02, A-1): span-aware matching; the definitions are exactly
        // what matchIntents would return (thin wrapper), so intent behaviour is
        // unchanged while the alias-hit ranges feed the I-7 claiming below.
        val matchedSpans = AiReplyIntentCatalog.matchIntentsWithSpans(requestText)
        val matchedIntents = matchedSpans.map { it.definition }
        val isResearch = matchedIntents.any { it.requiresProfile }

        // 计划 02 (I-1): 严格候选集沿用今日逻辑（含关键词匹配）；绕过候选集只在
        // 矩阵路径启用，等于 promptPool 全集（运营绑定集）。
        val strictCandidateRules = promptPool.filter { rule ->
            rule.id != null &&
                rule.id in promptSet &&
                QaFactKeywordMatcher.matchesRule(rule, normalizedRequest)
        }
        val effectiveCandidateRules = if (operatorBound) {
            promptPool.filter { it.id != null && it.id in promptSet }
        } else {
            strictCandidateRules
        }

        val assignments = AiReplyIntentCatalog.assignRulesToIntents(effectiveCandidateRules, matchedIntents)
        var intentCoverages = matchedIntents.map { intent ->
            val assignedIds = assignments[intent.key].orEmpty().mapNotNull { it.id }
            AiReplyIntentCatalog.resolveIntentEvidence(
                intent = intent,
                assignedRuleIds = assignedIds,
                profileSufficient = researchProfileSufficient
            )
        }

        // 计划 02 (I-2): 运营绑定只能【改写已存在】的 coverage 条目，绝不增删条目
        // ——否则 requestKey 的 intentKeys 投影漂移、工作台打不开、历史锁定项作废。
        // 未被任何 intent 采纳的绑定规则，仅当该条目已有 general.answer coverage
        // （零具名意图命中时由 catalog 合成）时并入其 evidenceRuleIds 并置 SUPPORTED；
        // 否则保持未分配（落入 droppedBindingRuleIds，走既有 P1 提示）。
        // 并入必须加 operatorBound 闸：auto/legacy 路径下关键词命中的规则也可能
        // 无法分配给任何 intent（coverage 不匹配），无闸会让自动链路行为改变。
        val assignedIds = assignments.values.flatten().mapNotNull { it.id }.toSet()
        val unassignedBound = effectiveCandidateRules
            .mapNotNull { it.id }
            .filter { it !in assignedIds }
        if (operatorBound && unassignedBound.isNotEmpty()) {
            val generalIndex = intentCoverages.indexOfFirst { it.intentKey == "general.answer" }
            if (generalIndex >= 0) {
                val general = intentCoverages[generalIndex]
                intentCoverages = intentCoverages.toMutableList().apply {
                    this[generalIndex] = general.copy(
                        evidenceRuleIds = (general.evidenceRuleIds + unassignedBound).distinct(),
                        status = "SUPPORTED"
                    )
                }
            }
        }

        // P2a (plan 02, I-7): an ask belongs to this request only when its
        // original range starts inside the request's original region, and it is
        // unrecognized only when no alias-hit span of this request overlaps it.
        // Shadow field — never feeds status/evidence/hashes (I-3/I-2).
        //
        // 计划 01 (I-3): matchIntentsWithSpans returns ranges LOCAL to the passed
        // requestText, while ask.originalRange is absolute in the inbound text.
        // Keep the local matchedSpans for intent/status and derive absolute
        // spans only for the shadow claiming comparison (add requestRange.first).
        val absoluteMatchedSpans = requestRange?.let { range ->
            matchedSpans.map { span ->
                span.copy(
                    originalRanges = span.originalRanges.map { it.first + range.first..it.last + range.first }
                )
            }
        }
        val unrecognizedAsks = askEnumeration.asks.filter { ask ->
            requestRange != null &&
                ask.originalRange.first in requestRange &&
                absoluteMatchedSpans != null &&
                !claimed(ask, absoluteMatchedSpans)
        }

        val researchWarned = isResearch && !researchProfileSufficient
        val allMissing = intentCoverages.isNotEmpty() && intentCoverages.all { it.status == "MISSING" }
        val anySupported = intentCoverages.any { it.status == "SUPPORTED" }
        val allSupported = intentCoverages.isNotEmpty() && intentCoverages.all { it.status == "SUPPORTED" }
        val anyPartial = intentCoverages.any { it.status == "PARTIAL" }

        // 计划 02 (I-4): 自然算得的 status（表达式与今日逐字一致）暂存，再按
        // operatorBypassedRuleIds 决定是否下调——只有「靠绕过才成立」的证据才把
        // GROUNDED 压到 PARTIAL；否则今日已 GROUNDED 且已锁定 ANSWER_WITH_EVIDENCE
        // 的条目会在下次 bootstrap 时允许集变化，requireAllowedHandlingForApi 抛 422。
        val naturalStatus = when {
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

        val factRuleIds = effectiveCandidateRules
            .mapNotNull { it.id }
            .filter { it in evidenceSet }

        // 计划 02 (I-4): 靠绕过才成为证据 = 不在严格关键词候选集内（矩阵路径放行
        // 的运营绑定），或经 I-2 的 general.answer 并入路径进来。operatorBound=false
        // 时恒空（factRuleIds ⊆ strictCandidateRules 且与 unassignedBound 不相交）。
        val operatorBypassedRuleIds = if (operatorBound) {
            val strictIds = strictCandidateRules.mapNotNull { it.id }.toSet()
            factRuleIds.filter { it !in strictIds || it in unassignedBound }
        } else {
            emptyList()
        }
        val status = if (operatorBypassedRuleIds.isNotEmpty() &&
            naturalStatus == RequestGroundingStatus.GROUNDED
        ) {
            RequestGroundingStatus.PARTIAL
        } else {
            naturalStatus
        }

        return RequestFactItem(
            index = index,
            requestText = requestText,
            factRuleIds = factRuleIds,
            status = status,
            requiresResearchContext = isResearch,
            intents = intentCoverages,
            unrecognizedAsks = unrecognizedAsks,
            operatorBypassedRuleIds = operatorBypassedRuleIds
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

    /**
     * P2a (plan 02): extracted request unit carrying the ORIGINAL text offsets
     * (for enumerator-ask attribution) and the extractor kind (for the
     * [ASK_ENUM] log line). Offsets/kind are consumed only by shadow
     * measurement — never by status/evidence/hash computation (I-3/I-2).
     */
    private data class RequestUnit(
        val text: String,
        val startOffset: Int,
        val endOffset: Int,
        val kind: QaRequestExtractor.Kind
    ) {
        val range: IntRange get() = startOffset until endOffset
    }

    private fun extractRequests(inboundText: String): List<RequestUnit> =
        QaRequestExtractor.extract(inboundText).map { request ->
            RequestUnit(
                text = request.text,
                startOffset = request.startOffset,
                endOffset = request.endOffset,
                kind = request.kind
            )
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

/**
 * 计划 04 (T1.1): 只读的可选性分区，供人工富文本发送路径把显式选择失败降级为
 * 可二次确认的风险项。由 [QaFactSelectionService.partitionExplicitSelection]
 * 产出；本类不抛异常（分类即结果）。
 */
data class ExplicitSelectionPartition(
    val selectable: List<Long>,      // 通过全部校验、且至少匹配一条 request（保序）
    val unavailable: List<Long>,     // 不存在 / 停用 / policy=NEVER / answerBody 空
    val unmatched: List<Long>,       // 规则可用，但关键词不匹配任何 request
    val noRequests: Boolean          // 来信抽不出任何 request
)
