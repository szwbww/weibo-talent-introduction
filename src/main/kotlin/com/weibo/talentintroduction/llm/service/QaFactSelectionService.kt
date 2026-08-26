package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.llm.config.AskEnumeratorProperties
import com.weibo.talentintroduction.llm.config.FactRetrieverProperties
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
    private val askEnumeratorProperties: AskEnumeratorProperties = AskEnumeratorProperties(),
    // 计划 01 (T2.1): 同样可空+默认值，既有测试单参构造不变；Spring 注入真 bean。
    private val qaFactRetriever: QaFactRetriever? = null,
    private val factRetrieverProperties: FactRetrieverProperties = FactRetrieverProperties()
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

        // 计划 01 (T2.7, I-2): select() 是自动/人工发送路径，仅当
        // enabledForAutoReply 为真才调用检索；QaFactRetriever 自身 fail-open（I-8）。
        val retrieval = if (factRetrieverProperties.enabledForAutoReply &&
            requestTexts.isNotEmpty() && promptPool.isNotEmpty()
        ) {
            qaFactRetriever?.retrieve(inboundText, requestTexts, promptPool)
        } else {
            null
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
                    requestRange = unit.range,
                    retrievedRuleIds = retrieval?.byRequestIndex?.get(idx + 1) ?: emptyList()
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

        // 计划 01 (T2.8): 每次实际发起的检索打一行固定 [FACT_RETRIEVAL] 日志，
        // 失败（available=false）按 warn 记录，成功按 info。
        retrieval?.let { r ->
            val line = buildFactRetrievalLogLine(
                source = "AUTO",
                available = r.available,
                requested = r.requested,
                returned = r.returned,
                accepted = r.accepted,
                rejected = r.rejected,
                truncated = r.truncated,
                outcome = r.outcome
            )
            if (r.available) logger.info(line) else logger.warn(line)
        }
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
                // 计划 01 (T2.7, I-2): 工作台 auto 分支在 resolveAutoSelection 前调用
                // 检索；门控（factRetrieverProperties.enabled）在 QaFactRetriever 内部
                // 生效——开关关闭时返回 outcome=DISABLED 并照常打一行可观测日志（A-3），
                // 工作台不受 enabledForAutoReply 限制（与枚举器同构）。
                val retrieval = if (requestTexts.isNotEmpty() && matchableRules.isNotEmpty()) {
                    qaFactRetriever?.retrieve(inboundText, requestTexts, matchableRules)
                } else {
                    null
                }
                val result = resolveAutoSelection(
                    requests = requests,
                    matchableRules = matchableRules,
                    researchProfileSufficient = researchProfileSufficient,
                    enumeration = enumeration,
                    retrieval = retrieval
                )
                // 计划 01 (T2.8): 每次实际发起的检索打一行固定 [FACT_RETRIEVAL] 日志。
                retrieval?.let { r ->
                    val line = buildFactRetrievalLogLine(
                        source = "WORKBENCH",
                        available = r.available,
                        requested = r.requested,
                        returned = r.returned,
                        accepted = r.accepted,
                        rejected = r.rejected,
                        truncated = r.truncated,
                        outcome = r.outcome
                    )
                    if (r.available) logger.info(line) else logger.warn(line)
                }
                result
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
        // 计划 02 (I-6): 同一 fact 可跨 request 复用——不再做跨 request 唯一性
        // 校验（checkWorkbenchUniqueness 仅保留给 legacy 扁平路径）。
        val requestFacts = requests.mapIndexed { idx, unit ->
            val explicitIds = selectionsByRequest[idx]
            val explicitRules = if (explicitIds.isEmpty()) {
                emptyList()
            } else {
                try {
                    val rules = validateExplicitSelection(explicitIds)
                    // 计划 02 (I-6): 单条 request 内重复 id 仍是脏输入并硬拦；
                    // 跨 request 复用（同 id 出现在不同 request）仍合法。
                    if (explicitIds.size != explicitIds.toSet().size) {
                        throw TrustReplyWorkbenchException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "TRUST_REPLY_FACT_SELECTION_INVALID"
                        )
                    }
                    rules
                } catch (_: IllegalArgumentException) {
                    throw TrustReplyWorkbenchException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "TRUST_REPLY_FACT_SELECTION_INVALID"
                    )
                }
            }
            val promptSet = explicitRules.mapNotNull { it.id }.toSet()
            // 计划 02 (阶段 1/I-1/I-2): 不再启用 operatorBound 绕过——先得到严格
            // 匹配（关键词 + intent 分配）的自然诊断 item；人工选择随后整体成为
            // 最终事实集（factRuleIds == boundRuleIds == explicitIds，保序）。
            val item = buildRequestFact(
                index = idx + 1,
                requestText = unit.text,
                promptPool = explicitRules,
                promptSet = promptSet,
                researchProfileSufficient = researchProfileSufficient,
                askEnumeration = enumeration,
                requestRange = unit.range,
                // 计划 01 (I-2): 人工矩阵是最终权威——检索必须旁路。
                retrievedRuleIds = emptyList()
            )
            val matchedIds = item.factRuleIds.toSet()
            // I-1: factRuleIds/boundRuleIds 直接取运营矩阵（人工最终权威）。
            // I-2: 自然严格命中的记为 intentMatchedFactRuleIds；其余按人工顺序记为
            // intentMismatchFactRuleIds——两者并集按人工顺序恰为 explicitIds，
            // 且任一诊断字段都不进入授权/版本/发送逻辑。
            val bound = item.copy(
                factRuleIds = explicitIds,
                boundRuleIds = explicitIds,
                intentMatchedFactRuleIds = item.factRuleIds,
                intentMismatchFactRuleIds = explicitIds.filter { it !in matchedIds }
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
                        requestRange = unit.range,
                        // 计划 01 (I-2): legacy 空选路径同样旁路检索。
                        retrievedRuleIds = emptyList()
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
                requestRange = unit.range,
                // 计划 01 (I-2): legacy 主分支同样旁路检索。
                retrievedRuleIds = emptyList()
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
        enumeration: AskEnumeration,
        retrieval: FactRetrieval? = null
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
                requestRange = unit.range,
                // 计划 01 (I-2): auto 路径传真值；已消费的 id 因不在当前 pool 而自然失效。
                retrievedRuleIds = retrieval?.byRequestIndex?.get(idx + 1) ?: emptyList()
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
        // 计划 02 (I-1/I-7): 人工矩阵是最终事实集（factRuleIds == boundRuleIds），
        // sendIds 从最终 factRuleIds 按 request 顺序取首次出现顺序去重；矩阵路径
        // 下 boundRuleIds == factRuleIds，promptIds 与 sendIds 逐字相等。
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
        // 计划 01 (T2.2): 本 request 的检索结果（I-4 校验后）。5 个调用点必须
        // 逐个显式赋值（I-2），默认值仅为源码兼容。
        retrievedRuleIds: List<Long> = emptyList()
    ): RequestFactItem {
        val normalizedRequest = QaFactKeywordMatcher.normalize(requestText)
        // P2a (plan 02, A-1): span-aware matching; the definitions are exactly
        // what matchIntents would return (thin wrapper), so intent behaviour is
        // unchanged while the alias-hit ranges feed the I-7 claiming below.
        val matchedSpans = AiReplyIntentCatalog.matchIntentsWithSpans(requestText)
        val matchedIntents = matchedSpans.map { it.definition }
        val isResearch = matchedIntents.any { it.requiresProfile }

        // 计划 02 (I-2/I-8): 唯一候选集 = 严格关键词匹配候选（今日逻辑逐字）。
        // operatorBound 绕过已删除——矩阵路径的人工权威由 resolveMatrixSelection
        // 在 item 产出后统一 copy 实现，buildRequestFact 本身只产出自然诊断。
        val strictCandidateRules = promptPool.filter { rule ->
            rule.id != null &&
                rule.id in promptSet &&
                QaFactKeywordMatcher.matchesRule(rule, normalizedRequest)
        }

        // 计划 01 (T2.3, I-3): 并集不是替代——候选集 = 严格关键词命中 ∪ 检索结果。
        // assignRulesToIntents 的入参保持 strictCandidateRules 不变（I-1：检索结果
        // 不影响 intent 分配，从而不影响 intentCoverages 与 requestKey）。
        val retrievedRuleIdsSet = retrievedRuleIds.toSet()
        val retrievedRules = promptPool
            .filter { it.id != null && it.id in retrievedRuleIdsSet }
            .filter { it !in strictCandidateRules }

        val assignments = AiReplyIntentCatalog.assignRulesToIntents(strictCandidateRules, matchedIntents)
        val intentCoverages = matchedIntents.map { intent ->
            val assignedIds = assignments[intent.key].orEmpty().mapNotNull { it.id }
            AiReplyIntentCatalog.resolveIntentEvidence(
                intent = intent,
                assignedRuleIds = assignedIds,
                profileSufficient = researchProfileSufficient
            )
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

        // 计划 02 (I-2): 自然 status——表达式与今日逐字一致，仅供诊断/推荐/UI；
        // 不再有人工绑定导致的状态下调（operatorBypassedRuleIds 语义已删除）。
        // 计划 01 (I-6): 今日结论先记在 naturalStatus，随后追加一层降级。
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

        // 计划 01 (T2.4, I-1): 关键词命中（且进入 SUPPORTED 证据集）在前，
        // 检索补入在后，去重保序；检索结果绝不改动 intents。
        val factRuleIds = (strictCandidateRules.mapNotNull { it.id }.filter { it in evidenceSet }
            + retrievedRules.mapNotNull { it.id }).distinct()

        // 计划 01 (I-6) + 计划 02 (I-1/I-3): 单一 status 表达式，先算 01 的提升、
        // 再算 02 的封顶（两条计划编号同注释，避免后续 merge 时其中一条被覆盖）。
        // 01: 今日结论 UNSUPPORTED 且最终 factRuleIds 非空时降为 PARTIAL
        //     （不得直接判 GROUNDED，自动发面不扩大）。
        // 02: 枚举器可用且本条存在未被任何 alias span 认领的诉求时，状态封顶为
        //     PARTIAL——"我认出来的都答了"不等于"专家问的都答了"；
        //     available=false（LLM 关闭/超时/解析失败/全部条目被 verbatim 校验丢弃）
        //     一律不封顶，行为与本计划落地前逐字相同 (I-3)。
        val status = if (naturalStatus == RequestGroundingStatus.UNSUPPORTED && factRuleIds.isNotEmpty()) {
            RequestGroundingStatus.PARTIAL
        } else if (askEnumeration.available &&
            unrecognizedAsks.isNotEmpty() &&
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
            // 计划 02 (I-2): 自然路径 matched 默认等于自身事实集、mismatch 恒空；
            // 矩阵路径由 resolveMatrixSelection 的 copy 显式覆盖。
            intentMatchedFactRuleIds = factRuleIds,
            intentMismatchFactRuleIds = emptyList(),
            // 计划 01 (T2.4, I-1): 仅由 LLM 检索补入的事实 id（按模型返回顺序）。
            // 只读诊断字段——绝不进入授权、版本哈希或发送逻辑（与
            // intentMatchedFactRuleIds/intentMismatchFactRuleIds 同级）。
            retrievedFactRuleIds = retrievedRules.mapNotNull { it.id }
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
            // 计划 01 (T2.6, I-5): 检索补入的事实也必须进入 sendQaRuleIds——在既有
            // intent 证据序之后追加本 request 的 factRuleIds 中尚未出现的 id
            // （保 request 顺序、保 linkedSetOf 去重）。无检索时追加恒为空集，
            // 与今日行为逐字节一致。
            item.factRuleIds.forEach { ordered.add(it) }
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
