package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.campaign.service.DiscoveryPersonOutreachGuardService
import com.weibo.talentintroduction.campaign.service.DiscoveryPersonRecipientService
import com.weibo.talentintroduction.campaign.service.OutreachGuardReasonCodes
import com.weibo.talentintroduction.campaign.service.OutreachGuardStates
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonIdentity
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberSnapshot
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonOutcome
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonSnapshot
import com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * 10（I-1/I-2/I-3）：新发现历史人物的**只读复核**与**受限应用**协调器。
 *
 * 只做四件事，且只碰 04/09 已验收的既有写入口：
 * - [preview]：仅按冻结 docId/候选组（服务端自行从 ES 重读三层、标签、身份证据、seq_no/primary_term、
 *   SQL 关系 revision、人工准入决定、历史保护状态），为**每条**冻结档案产出**互斥主类**
 *   `MERGE_READY / REVIEW_REQUIRED / KEEP_SINGLE / CHANGED_OR_FAILED`；「可以合并」（[MaintenanceGroupReview.canMerge]）
 *   与「可以发送」（[MaintenanceGroupReview.canSend]）分别判断。不接受客户端上传的「可信作者结论」。
 * - [apply]：只接受冻结 ID、规则版本、预览摘要与逐组 expected 指纹；服务端重复核实后再以
 *   **每组一个事务**登记整组关系（04）并导入既有首发保护（09）。任何成员不合格即整组失败、不部分合并。
 * - [rollbackPreview]：只生成逐组恢复**提案**，绝不执行拆组/释放保护。
 * - 幂等：同一组/版本再次 apply 返回 `UNCHANGED`，不新增行。
 *
 * 边界（不变式）：ES/联系人/邮件只读，物理删除 0，无生产发送；本服务不新增业务字段，也不新增任何
 * 关系写路径——04 的登记与 09 的保护导入是唯一落库面。冻结后新增 discovered 属 05 增量，本服务只列差额。
 *
 * 依赖可省略（[discoveryPersonRecipientService] / [discoveryPersonOutreachGuardService] 允许为 null）：
 * 省略时「准入/禁发原因」退化为未知（[MaintenanceReasonCodes.UNKNOWN]），关系登记仍可用——保证非人物档案
 * 与单元测试零影响。
 */
@Service
class DiscoveryPersonMaintenanceService(
    private val discoveryPersonService: DiscoveryPersonService,
    private val discoveryPersonRepository: DiscoveryPersonRepository,
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val discoveryPersonOutreachGuardService: DiscoveryPersonOutreachGuardService? = null,
    private val discoveryPersonRecipientService: DiscoveryPersonRecipientService? = null,
    private val clock: Clock = Clock.systemUTC()
) {
    private val log = LoggerFactory.getLogger(DiscoveryPersonMaintenanceService::class.java)

    // ── 只读预览 ───────────────────────────────────────────────────────────────

    fun preview(request: MaintenancePreviewRequest): MaintenancePreviewOutcome {
        if (request.ruleVersion != null && request.ruleVersion != RULE_VERSION) {
            return MaintenancePreviewOutcome.Rejected(MaintenanceReasonCodes.RULE_VERSION_MISMATCH)
        }
        val frozen = request.frozenDocIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (frozen.isEmpty()) return MaintenancePreviewOutcome.Rejected(MaintenanceReasonCodes.EMPTY_FROZEN_LIST)
        val frozenSet = frozen.toSet()

        val reviews = mutableListOf<MaintenanceGroupReview>()
        val covered = mutableSetOf<String>()

        request.candidateGroups.forEach { raw ->
            val members = raw.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (members.isEmpty()) return@forEach
            reviews += reviewGroup(members, frozenSet)
            covered += members
        }
        // 未出现在任何候选组里的冻结档案：一对一保留（缺绑定证据者登记单例，不建可信作者键）。
        frozen.filter { it !in covered }.forEach { docId -> reviews += reviewSingle(docId) }

        val counts = linkedMapOf<String, Int>()
        MaintenanceDisposition.values().forEach { counts[it.name] = 0 }
        reviews.forEach { review ->
            // 逐条档案计数：只统计冻结档案，且每条恰好一个主类（组外的范围外成员不计入）。
            review.memberDocIds.filter { it in frozenSet }.forEach {
                counts[review.disposition.name] = (counts[review.disposition.name] ?: 0) + 1
            }
        }
        val total = counts.values.sum()
        val preview = MaintenancePreview(
            ruleVersion = RULE_VERSION,
            generatedAt = Instant.now(clock).toString(),
            groups = reviews,
            classCounts = counts,
            totalDocuments = total,
            previewDigest = digest(reviews)
        )
        return MaintenancePreviewOutcome.Ok(preview)
    }

    private fun reviewSingle(docId: String): MaintenanceGroupReview {
        val read = readMember(docId)
        val disposition = when {
            read.changed -> MaintenanceDisposition.CHANGED_OR_FAILED
            else -> MaintenanceDisposition.KEEP_SINGLE
        }
        val member = evidence(read)
        val groupKey = groupKey(listOf(docId))
        return MaintenanceGroupReview(
            groupKey = groupKey,
            memberDocIds = listOf(docId),
            disposition = disposition,
            reason = read.reason,
            evidenceDigest = digestMembers(listOf(member)),
            canMerge = false,
            canSend = canSend(listOf(member)),
            members = listOf(member)
        )
    }

    private fun reviewGroup(members: List<String>, frozenSet: Set<String>): MaintenanceGroupReview {
        val reads = members.map { readMember(it) }
        val evidences = reads.map { evidence(it) }
        val groupKey = groupKey(members)

        if (members.any { it !in frozenSet }) {
            return group(groupKey, members, MaintenanceDisposition.CHANGED_OR_FAILED,
                MaintenanceReasonCodes.OUT_OF_SCOPE, evidences)
        }
        if (reads.any { it.changed }) {
            return group(groupKey, members, MaintenanceDisposition.CHANGED_OR_FAILED,
                reads.first { it.changed }.reason, evidences)
        }
        if (members.size == 1) {
            return group(groupKey, members, MaintenanceDisposition.KEEP_SINGLE, null, evidences)
        }
        if (reads.any { it.authorKeys.isEmpty() }) {
            return group(groupKey, members, MaintenanceDisposition.REVIEW_REQUIRED,
                MaintenanceReasonCodes.UNBOUND_MEMBER, evidences)
        }
        val allKeys = reads.flatMap { it.authorKeys }
        val orcids = allKeys.filter { it.startsWith(DiscoveryPersonIdentity.ORCID_PREFIX) }.distinct()
        val openAlex = allKeys.filter { it.startsWith(DiscoveryPersonIdentity.OPENALEX_PREFIX) }.distinct()
        if (orcids.size > 1 || openAlex.size > 1) {
            return group(groupKey, members, MaintenanceDisposition.REVIEW_REQUIRED,
                MaintenanceReasonCodes.MULTIPLE_AUTHOR_KEYS, evidences)
        }
        if (!connected(reads)) {
            return group(groupKey, members, MaintenanceDisposition.REVIEW_REQUIRED,
                MaintenanceReasonCodes.UNCONNECTED_MEMBER, evidences)
        }
        // 现有关系冲突：已登记成员指纹变化，或证据桥接两个已有人物根 → 待核，不自动吞并。
        val existing = discoveryPersonRepository.findMembers(members)
        val currentFingerprints = reads.associate { it.docId to it.fingerprint }
        if (existing.any { currentFingerprints[it.expertDocId] != it.identityFingerprint }) {
            return group(groupKey, members, MaintenanceDisposition.REVIEW_REQUIRED,
                MaintenanceReasonCodes.RELATION_FINGERPRINT_CHANGED, evidences)
        }
        val roots = (
            existing.map { it.personId } +
                discoveryPersonRepository.findPersonIdsByAuthorKeys(allKeys).values
            ).distinct()
        if (roots.size > 1) {
            return group(groupKey, members, MaintenanceDisposition.REVIEW_REQUIRED,
                MaintenanceReasonCodes.BRIDGE_EXISTING_ROOTS, evidences)
        }
        return group(groupKey, members, MaintenanceDisposition.MERGE_READY, null, evidences)
    }

    private fun group(
        groupKey: String,
        members: List<String>,
        disposition: MaintenanceDisposition,
        reason: String?,
        evidences: List<MaintenanceMemberEvidence>
    ): MaintenanceGroupReview = MaintenanceGroupReview(
        groupKey = groupKey,
        memberDocIds = members,
        disposition = disposition,
        reason = reason,
        evidenceDigest = digestMembers(evidences),
        canMerge = disposition == MaintenanceDisposition.MERGE_READY,
        canSend = canSend(evidences),
        members = evidences
    )

    // ── 受限应用 ───────────────────────────────────────────────────────────────

    fun apply(request: MaintenanceApplyRequest): MaintenanceApplyOutcome {
        if (request.ruleVersion != RULE_VERSION) {
            return MaintenanceApplyOutcome.Rejected(MaintenanceReasonCodes.RULE_VERSION_MISMATCH)
        }
        if (request.previewDigest.isNullOrBlank()) {
            return MaintenanceApplyOutcome.Rejected(MaintenanceReasonCodes.PREVIEW_DIGEST_REQUIRED)
        }
        if (request.groups.isEmpty()) {
            return MaintenanceApplyOutcome.Rejected(MaintenanceReasonCodes.EMPTY_FROZEN_LIST)
        }
        val frozenSet = request.groups.flatMap { it.memberDocIds }.toSet()
        // 服务端重复核实：重建冻结集预览并比对摘要，再逐组比对 expected 指纹。
        val currentPreview = when (val outcome = preview(
            MaintenancePreviewRequest(RULE_VERSION, request.groups.flatMap { it.memberDocIds }, request.groups.map { it.memberDocIds })
        )) {
            is MaintenancePreviewOutcome.Ok -> outcome.preview
            is MaintenancePreviewOutcome.Rejected -> return MaintenanceApplyOutcome.Rejected(outcome.reason)
        }
        if (currentPreview.previewDigest != request.previewDigest) {
            return MaintenanceApplyOutcome.Rejected(MaintenanceReasonCodes.PREVIEW_DIGEST_MISMATCH)
        }
        val byKey = currentPreview.groups.associateBy { it.groupKey }

        val results = mutableListOf<MaintenanceGroupApplyResult>()
        request.groups.forEach { requested ->
            val members = requested.memberDocIds
            val key = groupKey(members)
            val review = byKey[key]
            if (review == null || review.evidenceDigest != requested.expectedFingerprint) {
                results += MaintenanceGroupApplyResult(key, ApplyStatus.CHANGED_OR_FAILED, null, 0, false,
                    MaintenanceReasonCodes.FINGERPRINT_MISMATCH)
                return@forEach
            }
            if (review.disposition != MaintenanceDisposition.MERGE_READY &&
                review.disposition != MaintenanceDisposition.KEEP_SINGLE
            ) {
                results += MaintenanceGroupApplyResult(key, ApplyStatus.CHANGED_OR_FAILED, null, 0, false, review.reason)
                return@forEach
            }
            results += applyGroup(review, frozenSet)
        }
        return MaintenanceApplyOutcome.Ok(results)
    }

    /**
     * I-2：整组成员检查完整后**一次提交**——登记关系（04）与导入历史保护（09）在同一事务；
     * 任一步失败整组回滚，绝不产生半合并。幂等：关系已存在且历史保护已就绪 → `UNCHANGED`。
     */
    @Transactional
    fun applyGroup(review: MaintenanceGroupReview, frozenSet: Set<String>): MaintenanceGroupApplyResult {
        val members = review.memberDocIds
        if (members.any { it !in frozenSet }) {
            return MaintenanceGroupApplyResult(review.groupKey, ApplyStatus.CHANGED_OR_FAILED, null, 0, false,
                MaintenanceReasonCodes.OUT_OF_SCOPE)
        }
        // 实时复核：所有成员仍须是 discovered 且证据未变（登记入口会二次核实并可能拒绝）。
        val reads = members.map { readMember(it) }
        if (reads.any { it.changed }) {
            return MaintenanceGroupApplyResult(review.groupKey, ApplyStatus.CHANGED_OR_FAILED, null, 0, false,
                reads.first { it.changed }.reason)
        }
        val snapshot = DiscoveryPersonSnapshot(
            SOURCE,
            reads.map { DiscoveryPersonMemberSnapshot(it.docId, it.fingerprint!!) }
        )
        val registration = discoveryPersonService.register(snapshot)
        if (registration.outcome == DiscoveryPersonOutcome.NEEDS_REVIEW ||
            registration.outcome == DiscoveryPersonOutcome.SCOPE_CONFLICT ||
            registration.outcome == DiscoveryPersonOutcome.STALE
        ) {
            return MaintenanceGroupApplyResult(review.groupKey, ApplyStatus.CHANGED_OR_FAILED, registration.personId, 0, false,
                registration.reason ?: registration.outcome.name)
        }
        val personId = registration.personId
        val alreadyRegistered = registration.outcome == DiscoveryPersonOutcome.UNCHANGED
        val protection = importHistoryProtection(personId, reads)
        val status = if (alreadyRegistered) ApplyStatus.UNCHANGED else ApplyStatus.APPLIED
        return MaintenanceGroupApplyResult(
            review.groupKey,
            status,
            personId,
            registration.memberCount,
            protection == ProtectionImport.IMPORTED,
            registration.reason
        )
    }

    /**
     * I-3：经 09 的**只读**接口确认「已有成功/未知首发保护」，并靠 09 在真实发送前对全部成员的
     * 读检查（联系人/别名/mail_record/mail_send_attempt）持续阻断换址首发；08 的人物级准入（含人工
     * 拒绝/暂停）同样被视为历史保护。
     *
     * 这里**绝不**调用 `reserveIntroduction`：无历史时它会新建 `RESERVED` 占位，而占位只能由本次
     * ticket 的 CAS 消费——导入返回的 ticket 即被丢弃，占位将永久阻塞该人物的首发（违反「合并不能
     * 导致同人多个邮箱被逐一首次邀请」的反面：也不能让任何人都无法邀请）。因此导入不产生新状态行。
     */
    private fun importHistoryProtection(personId: String?, reads: List<MemberRead>): ProtectionImport {
        val guard = discoveryPersonOutreachGuardService ?: return ProtectionImport.UNKNOWN
        if (personId == null) return ProtectionImport.NONE
        return when (guard.stateOf(personId, DiscoveryPersonOutreachGuardService.INTRODUCTION)) {
            OutreachGuardStates.SENT, OutreachGuardStates.UNKNOWN,
            OutreachGuardStates.SENDING, OutreachGuardStates.RESERVED -> ProtectionImport.IMPORTED
            else -> if (hasReadOnlyHistoryReference(reads)) ProtectionImport.IMPORTED else ProtectionImport.NONE
        }
    }

    /** 09 历史检查第 1 步的同一权威只读探针（08 人物级准入）；命中人工拒绝/暂停/已联系即视为保护。 */
    private fun hasReadOnlyHistoryReference(reads: List<MemberRead>): Boolean {
        val recipient = discoveryPersonRecipientService ?: return false
        return reads.any { read ->
            val profile = read.profile ?: return@any false
            try {
                val selection = recipient.selectAdmission(listOf(profile))
                (selection.excluded + selection.included).firstOrNull()?.exclusionReasons?.isNotEmpty() == true
            } catch (e: Exception) {
                log.warn("10 历史保护只读探针失败 docId={}: {}", read.docId, e.message)
                false
            }
        }
    }

    // ── 回滚提案（只提案，不执行） ─────────────────────────────────────────────

    fun rollbackPreview(request: MaintenanceRollbackRequest): MaintenanceRollbackOutcome {
        if (request.ruleVersion != RULE_VERSION) {
            return MaintenanceRollbackOutcome.Rejected(MaintenanceReasonCodes.RULE_VERSION_MISMATCH)
        }
        val proposals = request.groups.map { members ->
            val key = groupKey(members)
            val existing = discoveryPersonRepository.findMembers(members)
            val roots = existing.map { it.personId }.distinct()
            if (roots.size != 1) {
                MaintenanceRollbackProposal(key, members, roots.singleOrNull(), false, members.size,
                    MaintenanceReasonCodes.NO_SINGLE_ROOT)
            } else {
                val personId = roots.single()
                val memberRecords = discoveryPersonRepository.findMembersByPerson(personId)
                val laterMembers = memberRecords.map { it.expertDocId }.filter { it !in members }
                val blocked = laterMembers.isNotEmpty() || members.any { hasSentOrUnknown(personId, it) }
                MaintenanceRollbackProposal(
                    key, members, personId, !blocked, memberRecords.size,
                    if (blocked) MaintenanceReasonCodes.ROLLBACK_UNSAFE else null
                )
            }
        }
        return MaintenanceRollbackOutcome.Ok(proposals)
    }

    private fun hasSentOrUnknown(personId: String, docId: String): Boolean {
        val guard = discoveryPersonOutreachGuardService ?: return false
        val state = guard.stateOf(personId, DiscoveryPersonOutreachGuardService.INTRODUCTION) ?: return false
        return state == OutreachGuardStates.SENT || state == OutreachGuardStates.UNKNOWN
    }

    // ── 只读成员读取（服务端重读证据，绝不写） ─────────────────────────────────

    private data class MemberRead(
        val docId: String,
        val changed: Boolean,
        val reason: String?,
        val authorKeys: List<String>,
        val fingerprint: String?,
        val normalizedEmail: String?,
        val profile: ExpertProfile?,
        val layers: List<String>,
        val layerVersions: Map<String, String?>,
        val seqNo: Long?,
        val primaryTerm: Long?,
        val evidenceSource: String?,
        val evidenceHash: String?,
        val evidenceVersion: Int?
    )

    private fun readMember(docId: String): MemberRead {
        val snapshots = LinkedHashMap<ExpertIndexLevel, ExpertIndexWriterService.DiscoverySnapshot>()
        for (level in LAYERS) {
            val document = try {
                expertIndexWriterService.readDiscoveryDocument(level, docId)
            } catch (e: Exception) {
                log.warn("10 复核读取 {} 层失败 docId={}: {}", level, docId, e.message)
                return failed(docId, MaintenanceReasonCodes.SOURCE_UNAVAILABLE)
            }
            if (document != null) snapshots[level] = document
        }
        if (snapshots.isEmpty()) return failed(docId, MaintenanceReasonCodes.DOCUMENT_MISSING)
        val raw = snapshots[ExpertIndexLevel.RAW] ?: snapshots.values.first()
        if (!DiscoveryIdentity.isDiscoveryMap(raw.source)) return failed(docId, MaintenanceReasonCodes.NOT_DISCOVERY)
        if (!layerIdentityConsistent(snapshots)) {
            return failed(docId, MaintenanceReasonCodes.LAYER_IDENTITY_CONFLICT, snapshots)
        }
        val profiles = snapshots.values.map { expertIndexWriterService.discoveryProfile(docId, it.source) }
        val rawProfile = expertIndexWriterService.discoveryProfile(docId, raw.source)
        val normalizedEmail = DiscoveryIdentity.normalizedEmail(rawProfile.email).takeIf { it.isNotEmpty() }
        val authorKeys = DiscoveryPersonIdentity.authorKeys(profiles)
        val fingerprint = DiscoveryPersonIdentity.fingerprint(
            docId, normalizedEmail, rawProfile.givenNames, rawProfile.familyNames, authorKeys
        )
        val proof = rawProfile.identityVerification
        return MemberRead(
            docId = docId,
            changed = false,
            reason = null,
            authorKeys = authorKeys,
            fingerprint = fingerprint,
            normalizedEmail = normalizedEmail,
            profile = rawProfile,
            layers = snapshots.keys.map { it.name },
            layerVersions = snapshots.mapKeys { it.key.name }.mapValues { it.value.source["updatedAt"] as? String },
            seqNo = raw.seqNo,
            primaryTerm = raw.primaryTerm,
            evidenceSource = proof?.source,
            evidenceHash = proof?.evidenceHash,
            evidenceVersion = proof?.version
        )
    }

    private fun failed(docId: String, reason: String, snapshots: Map<ExpertIndexLevel, ExpertIndexWriterService.DiscoverySnapshot> = emptyMap()) =
        MemberRead(docId, true, reason, emptyList(), null, null, null,
            snapshots.keys.map { it.name }, emptyMap(), null, null, null, null, null)

    private fun layerIdentityConsistent(snapshots: Map<ExpertIndexLevel, ExpertIndexWriterService.DiscoverySnapshot>): Boolean {
        val sources = snapshots.values.map { it.source }
        return LAYER_IDENTITY_KEYS.all { key -> sources.all { it[key] == sources.first()[key] } }
    }

    private fun connected(reads: List<MemberRead>): Boolean {
        if (reads.size <= 1) return true
        val keys = reads.map { it.authorKeys.toSet() }
        val visited = BooleanArray(reads.size)
        val stack = ArrayDeque<Int>()
        visited[0] = true
        stack.addLast(0)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            for (index in reads.indices) {
                if (!visited[index] && keys[current].intersect(keys[index]).isNotEmpty()) {
                    visited[index] = true
                    stack.addLast(index)
                }
            }
        }
        return visited.all { it }
    }

    // ── 证据/原因装配（可读只读 DO：关系、人工准入、历史保护） ─────────────────

    private fun evidence(read: MemberRead): MaintenanceMemberEvidence {
        val relation = try {
            discoveryPersonRepository.findMember(read.docId)
        } catch (e: Exception) {
            log.warn("10 复核读取关系失败 docId={}: {}", read.docId, e.message)
            null
        }
        val personId = relation?.personId
        val (admissionReason, sendBlockReason) = sendability(personId, read)
        return MaintenanceMemberEvidence(
            docId = read.docId,
            email = read.normalizedEmail,
            authorKeys = read.authorKeys,
            layers = read.layers,
            evidenceSource = read.evidenceSource,
            evidenceHash = read.evidenceHash,
            evidenceVersion = read.evidenceVersion,
            sourceFingerprint = read.fingerprint,
            seqNo = read.seqNo,
            primaryTerm = read.primaryTerm,
            relationPersonId = personId,
            relationRevision = personId?.let { runCatching { discoveryPersonRepository.findPerson(it)?.revision }.getOrNull() },
            manualDecision = admissionReason,
            historyReference = sendBlockReason != null,
            admissionReason = admissionReason,
            sendBlockReason = sendBlockReason,
            layerVersions = read.layerVersions
        )
    }

    /** 「可以发送」与「可以合并」分离：准入（人工决定）与既有首发保护分别判断。 */
    private fun sendability(personId: String?, read: MemberRead): Pair<String?, String?> {
        val admission = admissionReason(read)
        val block = sendBlockReason(personId)
        return admission to block
    }

    private fun admissionReason(read: MemberRead): String? {
        val recipient = discoveryPersonRecipientService ?: return null
        val profile = read.profile ?: return null
        return try {
            val selection = recipient.selectAdmission(listOf(profile))
            (selection.excluded + selection.included).firstOrNull()?.exclusionReasons?.firstOrNull()
        } catch (e: Exception) {
            log.warn("10 复核读取准入失败 docId={}: {}", read.docId, e.message)
            null
        }
    }

    private fun sendBlockReason(personId: String?): String? {
        val guard = discoveryPersonOutreachGuardService ?: return null
        if (personId == null) return null
        return when (guard.stateOf(personId, DiscoveryPersonOutreachGuardService.INTRODUCTION)) {
            OutreachGuardStates.SENT -> OutreachGuardReasonCodes.ALREADY_SENT
            OutreachGuardStates.UNKNOWN -> OutreachGuardReasonCodes.UNKNOWN_PENDING
            OutreachGuardStates.SENDING, OutreachGuardStates.RESERVED -> OutreachGuardReasonCodes.IN_FLIGHT
            else -> null
        }
    }

    private fun canSend(evidences: List<MaintenanceMemberEvidence>): Boolean =
        evidences.none { it.sendBlockReason != null } && evidences.any { it.admissionReason == null }

    // ── 摘要 ──────────────────────────────────────────────────────────────────

    private fun digestMembers(evidences: List<MaintenanceMemberEvidence>): String =
        DiscoveryIdentity.hash(evidences.joinToString("\u0000") { "${it.docId}|${it.sourceFingerprint}" })

    /** 预览摘要：绑定「规则版本 + 冻结 docId 全集」，使单组成员变化按逐组指纹跳过而非整批拒绝。 */
    private fun digest(reviews: List<MaintenanceGroupReview>): String =
        DiscoveryIdentity.hash(
            "v$RULE_VERSION\u0000" +
                reviews.flatMap { it.memberDocIds }.distinct().sorted().joinToString("\u0000")
        )

    private fun groupKey(members: List<String>): String =
        DiscoveryIdentity.hash(members.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted().joinToString("\u0000"))

    companion object {
        /** 10 的规则版本；与 04 的登记规则共同构成 apply 的版本门。 */
        const val RULE_VERSION = 1
        const val SOURCE = "DISCOVERY_MAINTENANCE_10"
        val LAYERS = listOf(ExpertIndexLevel.RAW, ExpertIndexLevel.CANDIDATE, ExpertIndexLevel.APPLICATION)
        private val LAYER_IDENTITY_KEYS =
            listOf("orcidId", "email", "givenNames", "familyNames", "externalIds", "identityVerification")
    }
}

/** 10（I-1）：逐条冻结档案的互斥主类。枚举值逐字落盘，读取方按字面量比较。 */
enum class MaintenanceDisposition { MERGE_READY, REVIEW_REQUIRED, KEEP_SINGLE, CHANGED_OR_FAILED }

/** 10：受限应用的分组结果状态。 */
enum class ApplyStatus { APPLIED, UNCHANGED, CHANGED_OR_FAILED, SKIPPED }

/** 10：历史保护导入结果（供对账区分「已导入」「无历史」「未知」）。 */
enum class ProtectionImport { IMPORTED, NONE, BLOCKED, UNKNOWN }

/** 10：可解释原因 key（调用方据此记 skipped，不做静默处理）。 */
object MaintenanceReasonCodes {
    const val RULE_VERSION_MISMATCH = "RULE_VERSION_MISMATCH"
    const val EMPTY_FROZEN_LIST = "EMPTY_FROZEN_LIST"
    const val OUT_OF_SCOPE = "OUT_OF_SCOPE"
    const val SOURCE_UNAVAILABLE = "SOURCE_UNAVAILABLE"
    const val DOCUMENT_MISSING = "DOCUMENT_MISSING"
    const val NOT_DISCOVERY = "NOT_DISCOVERY"
    const val LAYER_IDENTITY_CONFLICT = "LAYER_IDENTITY_CONFLICT"
    const val UNBOUND_MEMBER = "UNBOUND_MEMBER"
    const val MULTIPLE_AUTHOR_KEYS = "MULTIPLE_AUTHOR_KEYS"
    const val UNCONNECTED_MEMBER = "UNCONNECTED_MEMBER"
    const val RELATION_FINGERPRINT_CHANGED = "RELATION_FINGERPRINT_CHANGED"
    const val BRIDGE_EXISTING_ROOTS = "BRIDGE_EXISTING_ROOTS"
    const val PREVIEW_DIGEST_REQUIRED = "PREVIEW_DIGEST_REQUIRED"
    const val PREVIEW_DIGEST_MISMATCH = "PREVIEW_DIGEST_MISMATCH"
    const val FINGERPRINT_MISMATCH = "FINGERPRINT_MISMATCH"
    const val NO_SINGLE_ROOT = "NO_SINGLE_ROOT"
    const val ROLLBACK_UNSAFE = "ROLLBACK_UNSAFE"
    const val UNKNOWN = "UNKNOWN"
}

/** 10：一条成员证据（只读保存；用于复核材料与对账）。 */
data class MaintenanceMemberEvidence(
    val docId: String,
    val email: String?,
    val authorKeys: List<String>,
    val layers: List<String>,
    val evidenceSource: String?,
    val evidenceHash: String?,
    val evidenceVersion: Int?,
    val sourceFingerprint: String?,
    val seqNo: Long?,
    val primaryTerm: Long?,
    val relationPersonId: String?,
    val relationRevision: Long?,
    val manualDecision: String?,
    val historyReference: Boolean,
    val admissionReason: String?,
    val sendBlockReason: String?,
    val layerVersions: Map<String, String?> = emptyMap()
)

/** 10：逐组复核材料。[canMerge] 与 [canSend] 分别判断（合并 != 发送许可）。 */
data class MaintenanceGroupReview(
    val groupKey: String,
    val memberDocIds: List<String>,
    val disposition: MaintenanceDisposition,
    val reason: String?,
    val evidenceDigest: String,
    val canMerge: Boolean,
    val canSend: Boolean,
    val members: List<MaintenanceMemberEvidence>
)

/** 10：只读预览结果。`classCounts` 之和 = [totalDocuments] = 冻结档案数（逐条互斥）。 */
data class MaintenancePreview(
    val ruleVersion: Int,
    val generatedAt: String,
    val groups: List<MaintenanceGroupReview>,
    val classCounts: Map<String, Int>,
    val totalDocuments: Int,
    val previewDigest: String
)

sealed class MaintenancePreviewOutcome {
    data class Ok(val preview: MaintenancePreview) : MaintenancePreviewOutcome()
    data class Rejected(val reason: String) : MaintenancePreviewOutcome()
}

/** 10：预览请求 —— 冻结 docId 全集 + 候选组标识；服务端自行重读证据。 */
data class MaintenancePreviewRequest(
    val ruleVersion: Int? = null,
    val frozenDocIds: List<String> = emptyList(),
    val candidateGroups: List<List<String>> = emptyList()
)

/** 10：apply 的单组请求 —— 只带成员 ID 与预览摘要指纹。 */
data class MaintenanceApplyGroup(
    val memberDocIds: List<String>,
    val expectedFingerprint: String
)

/** 10：apply 请求 —— 只带冻结 ID、规则版本、预览摘要与逐组 expected 指纹。 */
data class MaintenanceApplyRequest(
    val ruleVersion: Int? = null,
    val previewDigest: String? = null,
    val groups: List<MaintenanceApplyGroup> = emptyList()
)

data class MaintenanceGroupApplyResult(
    val groupKey: String,
    val status: ApplyStatus,
    val personId: String?,
    val memberCount: Int,
    val protectionImported: Boolean,
    val reason: String?
)

sealed class MaintenanceApplyOutcome {
    data class Ok(val results: List<MaintenanceGroupApplyResult>) : MaintenanceApplyOutcome()
    data class Rejected(val reason: String) : MaintenanceApplyOutcome()
}

data class MaintenanceRollbackRequest(
    val ruleVersion: Int? = null,
    val groups: List<List<String>> = emptyList()
)

data class MaintenanceRollbackProposal(
    val groupKey: String,
    val memberDocIds: List<String>,
    val personId: String?,
    val safeToRollback: Boolean,
    val affectedCount: Int,
    val reason: String?
)

sealed class MaintenanceRollbackOutcome {
    data class Ok(val proposals: List<MaintenanceRollbackProposal>) : MaintenanceRollbackOutcome()
    data class Rejected(val reason: String) : MaintenanceRollbackOutcome()
}
