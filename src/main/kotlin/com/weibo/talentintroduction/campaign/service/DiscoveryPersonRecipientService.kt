package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.RecipientScope
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonScope
import com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository
import com.weibo.talentintroduction.discovery.service.DiscoveryPersonFeatureGate
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIdNormalizer
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import org.springframework.stereotype.Service
import java.util.Locale

/**
 * I-1/I-2/I-3: 一个人物（[personId]）在本次目标里的唯一选择结果。非人物档案（无 04 映射）
 * `personId` 为 null，退化为「每条档案一个人」，行为与本计划引入前逐邮箱选择一致。
 *
 * - [selectedDocId] 是本次入选的真实 docId —— 下游以它为过滤键（兼容既有 `includedDocIds` 语义），
 *   **不重写**原档案 `orcidId`；
 * - [selectedEmail] 是选中成员当前档案上的邮箱（展示与发送都用该地址）；
 * - [memberDocIds] 是该人物在该 scope 下考虑过的全部成员 docId（含未入选者）；
 * - [exclusionReasons] 非空表示该人物未入选（人物级保护、无合格地址或显式条件/准入排除）。
 */
data class PersonRecipientTarget(
    val personId: String?,
    val selectedDocId: String,
    val selectedEmail: String?,
    val memberDocIds: List<String>,
    val exclusionReasons: List<String>
)

/** I-3: 人物级选择产物。每人最多一条 [included]；原因命中按人物去重计数。 */
data class PersonRecipientSelection(
    val included: List<PersonRecipientTarget>,
    val excluded: List<PersonRecipientTarget>,
    val reasonHits: Map<String, Int>
) {
    /** 下游过滤键：每人恰好一个入选 docId。 */
    val selectedDocIds: Set<String> get() = included.mapTo(LinkedHashSet()) { it.selectedDocId }

    val selectedByDocId: Map<String, PersonRecipientTarget> get() = included.associateBy { it.selectedDocId }

    companion object {
        val EMPTY = PersonRecipientSelection(emptyList(), emptyList(), emptyMap())
    }
}

/** I-1/I-3: 人物级排除原因 key（准入状态 key 复用 [RecipientAdmissionReasonKeys]）。 */
object PersonRecipientReasonKeys {
    /** 人物有人工拒绝/身份冲突等硬保护，整组不可首发。 */
    const val ALREADY_CONTACTED = "ALREADY_CONTACTED"
    /** 人物全部成员邮箱被抑制，无可发地址。 */
    const val SUPPRESSED = "SUPPRESSED_EMAIL"
    /** 人物无合格（准入通过且邮箱可用）的成员地址。 */
    const val NO_QUALIFIED_EMAIL = "NO_QUALIFIED_EMAIL"
    /** 重试应沿用原尝试地址，但该地址已不合格 —— 不自动换址。 */
    const val PREFERRED_ADDRESS_INELIGIBLE = "PREFERRED_ADDRESS_INELIGIBLE"
    /** 人物整组档案缺失/不可读（缺失/失效/不可读的成员）—— fail-closed，绝不静默发信。 */
    const val PERSON_UNREADABLE = "PERSON_UNREADABLE"
    /** 已发现（实时 discovered）但未登记到人物 —— fail-closed，绝不逐档案静默发信。 */
    const val PERSON_MAPPING_MISSING = "PERSON_MAPPING_MISSING"
}

/**
 * I-1/I-2/I-3: 「先逐邮箱准入/抑制，再每人选址」的共享只读计算层。
 *
 * - 依赖方向：本服务**调用** [BatchRecipientSelectionService]（逐邮箱准入的唯一入口），
 *   两服务不互相注入；
 * - 首次遇到人物时批量加载该人物当前全部成员（04 关系 + ES `_mget` 当前档案），应用同一
 *   [RecipientScope] 后确定唯一地址；因此同一人物跨页 / 跨重试目标只计一次；
 * - 选址确定性：显示代表档案（04 `primaryDocId`）的邮箱仅在合格时优先，
 *   否则按现有验证可信级别（[ExpertProfile.emailVerifiedLevel]）降序、docId 升序确定；
 * - 人物保护先读**整个有效组**：包括不满足当前筛选或不在当前层的成员；任一成员缺失/不可读、
 *   被人工 HOLD/REJECTED 或身份冲突、或已存在绑定联系人，整组排除，不能用另一成员绕过。
 *   已发现（实时 discovered）但未登记人物映射的档案同样 fail-closed，绝不逐档案静默发信；
 * - 纯计算：不写库、不建 contact/campaign、不发 HTTP 写请求（ES 仅 `_mget` 读）。预估与执行共用本函数。
 *
 * 全部依赖可省略 —— 省略时退化为逐邮箱选择（fail-open 到既有行为），保证非人物档案与单元测试零影响。
 */
@Service
class DiscoveryPersonRecipientService(
    private val batchRecipientSelectionService: BatchRecipientSelectionService = BatchRecipientSelectionService(),
    private val discoveryPersonRepository: DiscoveryPersonRepository? = null,
    private val expertSearchService: ExpertSearchService? = null,
    private val emailSuppressionService: EmailSuppressionService? = null,
    private val expertContactRepository: ExpertContactRepository? = null,
    private val featureGate: DiscoveryPersonFeatureGate = DiscoveryPersonFeatureGate()
) {
    /**
     * I-8：默认关闭时不参与人物归并 —— 全部档案按真实 docId 成为独立单位，
     * 目标选择与选中地址与本轮引入前逐字一致。
     */
    private val personRepository: DiscoveryPersonRepository?
        get() = if (featureGate.enabled) discoveryPersonRepository else null

    /**
     * I-2: 人物去重键。有人物映射用 personId；无映射（含未注入仓储/总开关关闭）退回**规范化 ORCID**
     * —— 与本轮引入前 ES 原始身份去重键逐字一致，非人物档案零行为变化。
     * 供 [OutreachTargetIterator] 等调用者作为 `dedupeKey` 使用。
     */
    fun personKeyOf(profile: ExpertProfile): String =
        personRepository?.findMember(docIdOf(profile))?.personId
            ?: ExpertIdNormalizer.normalize(profile.orcidId)

    /**
     * I-2: [personKeyOf] 的批量版本（docId → personId ?: 规范化 ORCID）。一次 `findPersonIdsByDocIds`
     * 读取，供预估/执行在整页/整批上做人物级去重，避免逐条查询。
     */
    fun personKeysOf(profiles: List<ExpertProfile>): Map<String, String> {
        val keys = LinkedHashMap<String, String>(profiles.size)
        profiles.forEach { keys.putIfAbsent(docIdOf(it), ExpertIdNormalizer.normalize(it.orcidId)) }
        val repository = personRepository ?: return keys
        val mapping = repository.findPersonIdsByDocIds(keys.keys)
        return keys.mapValues { (docId, fallback) -> mapping[docId] ?: fallback }
    }

    /**
     * I-1/I-2/I-3: 只判准入（无本次页面条件的 scope），供旧首发等无页面快照入口复用人物级结论。
     */
    fun selectAdmission(
        profiles: List<ExpertProfile>,
        onPerEmailSelection: ((RecipientSelection) -> Unit)? = null
    ): PersonRecipientSelection = select(BatchRecipientSelectionService.admissionOnlyScope(), profiles, onPerEmailSelection)

    /**
     * I-1/I-3: 选择本次可首发人物。
     *
     * @param preferredDocIds 安全重试沿用原尝试地址：若人物命中其中任一 docId/orcid，则必须选中该地址；
     *        该地址不合格时整个人物排除（不自动换址）。空集合 = 无偏好，按确定性规则选址。
     * @param onPerEmailSelection 逐邮箱 selector 的原始结论回调（预估准入明细用），非决策依据。
     */
    fun select(
        scope: RecipientScope,
        profiles: List<ExpertProfile>,
        onPerEmailSelection: ((RecipientSelection) -> Unit)? = null,
        preferredDocIds: Set<String> = emptySet()
    ): PersonRecipientSelection {
        if (profiles.isEmpty()) return PersonRecipientSelection.EMPTY

        // 按真实 docId 保序去重（同一条档案在一批里只判一次）。
        val byDocId = LinkedHashMap<String, ExpertProfile>(profiles.size)
        for (profile in profiles) byDocId.putIfAbsent(docIdOf(profile), profile)

        val personByDocId = personRepository?.findPersonIdsByDocIds(byDocId.keys) ?: emptyMap()

        // 按 personId 分组；无映射档案各自成组（personId = null）。
        val groups = LinkedHashMap<String, MutableList<ExpertProfile>>()
        val groupPersonId = HashMap<String, String?>()
        for ((docId, profile) in byDocId) {
            val personId = personByDocId[docId]
            val key = personId ?: "#doc:$docId"
            groups.getOrPut(key) { mutableListOf() }.add(profile)
            groupPersonId[key] = personId
        }

        val included = ArrayList<PersonRecipientTarget>(groups.size)
        val excluded = ArrayList<PersonRecipientTarget>()
        val reasonHits = LinkedHashMap<String, Int>()

        for ((key, groupProfiles) in groups) {
            val target = decide(scope, groupPersonId[key], groupProfiles, preferredDocIds, onPerEmailSelection)
            if (target.exclusionReasons.isEmpty()) {
                included += target
            } else {
                excluded += target
                target.exclusionReasons.distinct().forEach { reasonHits.merge(it, 1, Int::plus) }
            }
        }
        return PersonRecipientSelection(included, excluded, reasonHits)
    }

    private fun decide(
        scope: RecipientScope,
        personId: String?,
        inBatch: List<ExpertProfile>,
        preferredDocIds: Set<String>,
        onPerEmailSelection: ((RecipientSelection) -> Unit)?
    ): PersonRecipientTarget {
        // 非人物档案：保持逐邮箱选择（不含抑制/联系人判定 —— 由下游原流程负责），范围外行为不变。
        if (personId == null) return decideNonPerson(scope, inBatch.single(), onPerEmailSelection)

        val memberDocIds = allMemberDocIds(personId, inBatch)

        // 1. 人物硬保护：先读**整个有效组**（含不满足当前筛选或不在当前层的成员）。
        //    任一成员缺失/不可读（读不到档案）即 fail-closed，绝不因「本页看不到」而放行。
        val group = loadGroupProfiles(scope, memberDocIds, inBatch)
        if (group.profiles.size < memberDocIds.size) {
            return PersonRecipientTarget(
                personId, selectedDocIdPlaceholder(memberDocIds), null, memberDocIds,
                listOf(PersonRecipientReasonKeys.PERSON_UNREADABLE)
            )
        }

        // 1a. 人物级准入：用**不带本次筛选**的准入 scope 读取整组持久结论 —— 不满足当前筛选
        //     （如邮箱域/研发类型）或不在当前层的成员的人工 HOLD/身份冲突同样可见，不能用另一成员绕过。
        val protection = batchRecipientSelectionService.selectProtection(group.profiles)
        val hardReasons = protection.excluded.mapNotNull { decision ->
            when (decision.admissionState) {
                RecipientAdmissionState.MANUAL_REJECTED -> RecipientAdmissionReasonKeys.MANUAL_REJECTED
                RecipientAdmissionState.IDENTITY_CHANGED -> RecipientAdmissionReasonKeys.IDENTITY_CHANGED
                else -> null
            }
        }.distinct()
        if (hardReasons.isNotEmpty()) {
            return PersonRecipientTarget(personId, selectedDocIdPlaceholder(memberDocIds), null, memberDocIds, hardReasons)
        }

        // 1b. 人物级历史保护：整个有效组任一成员已有绑定联系人（历史首封已发/已联系）→ 整组排除。
        if (hasBoundMemberContact(group.profiles)) {
            return PersonRecipientTarget(
                personId, selectedDocIdPlaceholder(memberDocIds), null, memberDocIds,
                listOf(PersonRecipientReasonKeys.ALREADY_CONTACTED)
            )
        }

        // 2. 具体邮箱：只对**当前 scope 层**的成员执行原 scope、准入、验证与抑制。
        val candidates = group.profiles.filter { docIdOf(it) in group.candidateDocIds }
        val selection = batchRecipientSelectionService.select(scope, candidates)
        onPerEmailSelection?.invoke(selection)
        val includedDocIds = selection.includedDocIds

        // 3. 合格地址：准入通过 + 邮箱非空 + 未被抑制。
        val qualified = candidates.filter { docIdOf(it) in includedDocIds && qualifies(it) }
        if (qualified.isEmpty()) {
            val reasons = LinkedHashSet<String>()
            selection.excluded.forEach { reasons += BatchRecipientSelectionService.reasonKeysOf(it) }
            if (candidates.any { docIdOf(it) in includedDocIds && isSuppressedEmail(it.email) }) {
                reasons += PersonRecipientReasonKeys.SUPPRESSED
            }
            if (reasons.isEmpty()) reasons += PersonRecipientReasonKeys.NO_QUALIFIED_EMAIL
            return PersonRecipientTarget(personId, selectedDocIdPlaceholder(memberDocIds), null, memberDocIds, reasons.toList())
        }

        // 4. 安全重试沿用原尝试地址：命中 preferred 时只能用该地址，不合格则不换址。
        val preferred = preferredDocIds.map { docIdOfValue(it) }.toSet()
        val preferredMember = memberDocIds.firstOrNull { it in preferred }
        val selected = if (preferredMember != null) {
            qualified.firstOrNull { docIdOf(it) == preferredMember }
                ?: return PersonRecipientTarget(
                    personId, preferredMember, null, memberDocIds,
                    listOf(PersonRecipientReasonKeys.PREFERRED_ADDRESS_INELIGIBLE)
                )
        } else {
            pickQualified(qualified, personId)
        }

        return PersonRecipientTarget(
            personId = personId,
            selectedDocId = docIdOf(selected),
            selectedEmail = selected.email,
            memberDocIds = memberDocIds,
            exclusionReasons = emptyList()
        )
    }

    private fun decideNonPerson(
        scope: RecipientScope,
        profile: ExpertProfile,
        onPerEmailSelection: ((RecipientSelection) -> Unit)?
    ): PersonRecipientTarget {
        val docId = docIdOf(profile)
        // I-3: 已发现（实时 discovered）但无 04 人物映射 → fail-closed 排除，绝不逐档案静默发信。
        // 总开关关闭（未注入仓储）时保持既有逐邮箱行为，非人物档案零影响。
        if (personRepository != null && DiscoveryPersonScope.isLiveDiscovered(profile)) {
            return PersonRecipientTarget(
                personId = null,
                selectedDocId = docId,
                selectedEmail = null,
                memberDocIds = listOf(docId),
                exclusionReasons = listOf(PersonRecipientReasonKeys.PERSON_MAPPING_MISSING)
            )
        }
        val selection = batchRecipientSelectionService.select(scope, listOf(profile))
        onPerEmailSelection?.invoke(selection)
        val included = docId in selection.includedDocIds
        return PersonRecipientTarget(
            personId = null,
            selectedDocId = docId,
            selectedEmail = profile.email,
            memberDocIds = listOf(docId),
            exclusionReasons = if (included) emptyList()
            else selection.excluded.firstOrNull()?.let { BatchRecipientSelectionService.reasonKeysOf(it) }
                ?: listOf(PersonRecipientReasonKeys.NO_QUALIFIED_EMAIL)
        )
    }

    /**
     * I-2: 首次遇到人物时批量加载该人物当前全部成员 docId（04 关系，按 docId 升序），
     * 并与本批出现的成员合并（关系读取失败时至少保留本批成员，不丢人）。
     */
    private fun allMemberDocIds(personId: String, inBatch: List<ExpertProfile>): List<String> {
        val ordered = LinkedHashSet<String>()
        personRepository?.findMembersByPerson(personId)?.forEach { ordered += it.expertDocId }
        inBatch.forEach { ordered += docIdOf(it) }
        return ordered.toList()
    }

    /**
     * I-2: 加载该人物**整个有效组**的当前档案（本批已有者直接复用；其余逐层经 ES `_mget` 读取）。
     * scope 层优先（保持既有选址层级），其余层补齐硬保护所需的整组事实；
     * [LoadedGroup.candidateDocIds] 只含命中当前 scope 层（或本批）的成员，用于具体邮箱选址。
     * ES 不可用或读不到的成员不出现在 [LoadedGroup.profiles] —— 由调用方按缺失 fail-closed。
     */
    private fun loadGroupProfiles(
        scope: RecipientScope,
        memberDocIds: List<String>,
        inBatch: List<ExpertProfile>
    ): LoadedGroup {
        val byDocId = LinkedHashMap<String, ExpertProfile>(memberDocIds.size)
        val candidateDocIds = LinkedHashSet<String>()
        inBatch.forEach { profile ->
            val docId = docIdOf(profile)
            if (byDocId.putIfAbsent(docId, profile) == null) candidateDocIds += docId
        }
        val missing = memberDocIds.filter { it !in byDocId }
        if (missing.isNotEmpty() && expertSearchService != null) {
            val levelNames = (scope.funnelLevels + ExpertIndexLevel.values().map { it.name }).distinct()
            for (levelName in levelNames) {
                val level = runCatching { ExpertIndexLevel.valueOf(levelName) }.getOrNull() ?: continue
                expertSearchService.findByDocumentIds(level, missing).forEach { profile ->
                    val docId = docIdOf(profile)
                    if (byDocId.putIfAbsent(docId, profile) == null && levelName in scope.funnelLevels) {
                        candidateDocIds += docId
                    }
                }
            }
        }
        return LoadedGroup(memberDocIds.mapNotNull { byDocId[it] }, candidateDocIds)
    }

    private fun hasBoundMemberContact(memberProfiles: List<ExpertProfile>): Boolean {
        val repository = expertContactRepository ?: return false
        val orcids = memberProfiles.map { it.orcidId }.filter { it.isNotBlank() }.distinct()
        if (orcids.isEmpty()) return false
        return repository.findByOrcidIdIn(orcids).any { !it.boundSenderAccountCode.isNullOrBlank() }
    }

    /**
     * I-2: 选址确定性 —— 显示代表档案（04 `primaryDocId`）的邮箱仅在合格时优先；
     * 否则验证可信级别降序（null 最后）、docId 升序。
     */
    private fun pickQualified(qualified: List<ExpertProfile>, personId: String): ExpertProfile {
        val primaryDocId = personRepository?.findPerson(personId)?.primaryDocId
        return qualified.sortedWith(
            compareByDescending<ExpertProfile> { primaryDocId != null && docIdOf(it) == primaryDocId }
                .thenByDescending { it.emailVerifiedLevel ?: Int.MIN_VALUE }
                .thenBy { docIdOf(it) }
        ).first()
    }

    /** I-2: 一次整组加载的产物：全部可读成员 + 命中当前 scope 层的成员 docId。 */
    private data class LoadedGroup(
        val profiles: List<ExpertProfile>,
        val candidateDocIds: Set<String>
    )

    private fun qualifies(profile: ExpertProfile): Boolean =
        !profile.email.isNullOrBlank() && !isSuppressedEmail(profile.email)

    private fun isSuppressedEmail(email: String?): Boolean =
        !email.isNullOrBlank() && emailSuppressionService?.isSuppressed(email) == true

    /** 排除人物没有单一「选中」地址；用成员中最小 docId 作为可见占位，绝不用于发送。 */
    private fun selectedDocIdPlaceholder(memberDocIds: List<String>): String =
        memberDocIds.minOrNull() ?: ""

    private fun docIdOf(profile: ExpertProfile): String = BatchRecipientSelectionService.docIdOf(profile)

    /** preferred 允许传 docId 或 orcid 任一形态；归一比较。 */
    private fun docIdOfValue(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return trimmed
        val member = personRepository?.findMember(trimmed)
        if (member != null) return member.expertDocId
        return trimmed
    }

    private fun String?.normalizeEmail(): String? = this?.trim()?.lowercase(Locale.ROOT)
}
