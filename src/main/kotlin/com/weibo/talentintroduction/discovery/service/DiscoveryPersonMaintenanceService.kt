package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.campaign.service.DiscoveryPersonOutreachGuardService
import com.weibo.talentintroduction.campaign.service.DiscoveryPersonRecipientService
import com.weibo.talentintroduction.campaign.service.OutreachGuardReasonCodes
import com.weibo.talentintroduction.campaign.service.OutreachGuardStates
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonIdentity
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberSnapshot
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonOutcome
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonScope
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonSnapshot
import com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
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
    private val transactionManager: PlatformTransactionManager? = null,
    private val clock: Clock = Clock.systemUTC()
) {
    private val log = LoggerFactory.getLogger(DiscoveryPersonMaintenanceService::class.java)

    /** 同库显式事务模板：apply 由自调用进入，注解不生成代理事务，这里显式开事务保证整组原子。 */
    private val transactionTemplate: TransactionTemplate? = transactionManager?.let { TransactionTemplate(it) }

    // ── 只读预览 ───────────────────────────────────────────────────────────────

    fun preview(request: MaintenancePreviewRequest): MaintenancePreviewOutcome {
        if (request.ruleVersion != null && request.ruleVersion != RULE_VERSION) {
            return MaintenancePreviewOutcome.Rejected(MaintenanceReasonCodes.RULE_VERSION_MISMATCH)
        }
        val frozen = request.frozenDocIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (frozen.isEmpty()) return MaintenancePreviewOutcome.Rejected(MaintenanceReasonCodes.EMPTY_FROZEN_LIST)
        val frozenSet = frozen.toSet()

        // I-1：每个冻结 ID 恰好一个处置 —— 候选组必须互不重叠（重叠组会让同一档案进入两个主类）。
        val claimed = mutableSetOf<String>()
        val overlapping = mutableListOf<String>()
        request.candidateGroups.forEach { raw ->
            val members = raw.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (members.isEmpty()) return@forEach
            val repeated = members.filter { it in claimed }
            if (repeated.isNotEmpty()) {
                overlapping += repeated
            } else {
                claimed += members
            }
        }
        if (overlapping.isNotEmpty()) {
            return MaintenancePreviewOutcome.Rejected(
                MaintenanceReasonCodes.OVERLAPPING_CANDIDATE_GROUPS
            )
        }

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
            totalDocuments = frozen.size,
            previewDigest = frozenDigest(frozen)
        )
        return MaintenancePreviewOutcome.Ok(preview)
    }

    private fun reviewSingle(docId: String): MaintenanceGroupReview {
        val read = readMember(docId)
        val probed = evidence(read)
        val member = probed.evidence
        val disposition = when {
            read.changed -> MaintenanceDisposition.CHANGED_OR_FAILED
            probed.readFailure != null -> MaintenanceDisposition.CHANGED_OR_FAILED
            else -> MaintenanceDisposition.KEEP_SINGLE
        }
        val groupKey = groupKey(listOf(docId))
        return MaintenanceGroupReview(
            groupKey = groupKey,
            memberDocIds = listOf(docId),
            disposition = disposition,
            reason = read.reason ?: probed.readFailure,
            evidenceDigest = identityDigest(listOf(member)),
            relationDigest = relationDigest(listOf(member)),
            canMerge = false,
            canSend = probed.readFailure == null && canSend(listOf(member)),
            members = listOf(member)
        )
    }

    private fun reviewGroup(members: List<String>, frozenSet: Set<String>): MaintenanceGroupReview {
        val reads = members.map { readMember(it) }
        val probed = reads.map { evidence(it) }
        val evidences = probed.map { it.evidence }
        val groupKey = groupKey(members)
        val readFailure = probed.firstOrNull { it.readFailure != null }?.readFailure

        if (members.any { it !in frozenSet }) {
            return group(groupKey, members, MaintenanceDisposition.CHANGED_OR_FAILED,
                MaintenanceReasonCodes.OUT_OF_SCOPE, evidences, readFailure)
        }
        if (reads.any { it.changed }) {
            return group(groupKey, members, MaintenanceDisposition.CHANGED_OR_FAILED,
                reads.first { it.changed }.reason, evidences, readFailure)
        }
        // 必需事实读取失败（关系/准入/保护）⇒ 明确失败，绝不按「无事实」继续判定。
        if (readFailure != null) {
            return group(groupKey, members, MaintenanceDisposition.CHANGED_OR_FAILED,
                readFailure, evidences, readFailure)
        }
        if (members.size == 1) {
            return group(groupKey, members, MaintenanceDisposition.KEEP_SINGLE, null, evidences, null)
        }
        if (reads.any { it.authorKeys.isEmpty() }) {
            return group(groupKey, members, MaintenanceDisposition.REVIEW_REQUIRED,
                MaintenanceReasonCodes.UNBOUND_MEMBER, evidences, null)
        }
        val allKeys = reads.flatMap { it.authorKeys }
        val orcids = allKeys.filter { it.startsWith(DiscoveryPersonIdentity.ORCID_PREFIX) }.distinct()
        val openAlex = allKeys.filter { it.startsWith(DiscoveryPersonIdentity.OPENALEX_PREFIX) }.distinct()
        if (orcids.size > 1 || openAlex.size > 1) {
            return group(groupKey, members, MaintenanceDisposition.REVIEW_REQUIRED,
                MaintenanceReasonCodes.MULTIPLE_AUTHOR_KEYS, evidences, null)
        }
        if (!connected(reads)) {
            return group(groupKey, members, MaintenanceDisposition.REVIEW_REQUIRED,
                MaintenanceReasonCodes.UNCONNECTED_MEMBER, evidences, null)
        }
        // 现有关系冲突：已登记成员指纹变化，或证据桥接两个已有人物根 → 待核，不自动吞并。
        val existing = discoveryPersonRepository.findMembers(members)
        val currentFingerprints = reads.associate { it.docId to it.fingerprint }
        if (existing.any { currentFingerprints[it.expertDocId] != it.identityFingerprint }) {
            return group(groupKey, members, MaintenanceDisposition.REVIEW_REQUIRED,
                MaintenanceReasonCodes.RELATION_FINGERPRINT_CHANGED, evidences, null)
        }
        val roots = (
            existing.map { it.personId } +
                discoveryPersonRepository.findPersonIdsByAuthorKeys(allKeys).values
            ).distinct()
        if (roots.size > 1) {
            return group(groupKey, members, MaintenanceDisposition.REVIEW_REQUIRED,
                MaintenanceReasonCodes.BRIDGE_EXISTING_ROOTS, evidences, null)
        }
        return group(groupKey, members, MaintenanceDisposition.MERGE_READY, null, evidences, null)
    }

    private fun group(
        groupKey: String,
        members: List<String>,
        disposition: MaintenanceDisposition,
        reason: String?,
        evidences: List<MaintenanceMemberEvidence>,
        readFailure: String?
    ): MaintenanceGroupReview = MaintenanceGroupReview(
        groupKey = groupKey,
        memberDocIds = members,
        disposition = disposition,
        reason = reason,
        evidenceDigest = identityDigest(evidences),
        relationDigest = relationDigest(evidences),
        canMerge = disposition == MaintenanceDisposition.MERGE_READY,
        canSend = readFailure == null && canSend(evidences),
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
        val frozen = request.frozenDocIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (request.groups.isEmpty() || frozen.isEmpty()) {
            return MaintenanceApplyOutcome.Rejected(MaintenanceReasonCodes.EMPTY_FROZEN_LIST)
        }
        // I-1：预览摘要绑定的是**完整冻结清单身份**（不是本次选中子集），子集试点/断点续跑仍可用。
        if (frozenDigest(frozen) != request.previewDigest) {
            return MaintenanceApplyOutcome.Rejected(MaintenanceReasonCodes.PREVIEW_DIGEST_MISMATCH)
        }
        val frozenSet = frozen.toSet()

        // 与预览同一条互斥规则：本次选择组内部也不得重叠。
        val claimed = mutableSetOf<String>()
        val overlapping = request.groups.any { group ->
            val members = group.memberDocIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val repeated = members.filter { it in claimed }
            claimed += members
            repeated.isNotEmpty()
        }
        if (overlapping) {
            return MaintenanceApplyOutcome.Rejected(MaintenanceReasonCodes.OVERLAPPING_CANDIDATE_GROUPS)
        }

        val results = mutableListOf<MaintenanceGroupApplyResult>()
        request.groups.forEach { requested ->
            val members = requested.memberDocIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val key = groupKey(members)
            if (members.isEmpty()) {
                results += MaintenanceGroupApplyResult(key, ApplyStatus.CHANGED_OR_FAILED, null, 0, false,
                    MaintenanceReasonCodes.EMPTY_FROZEN_LIST)
                return@forEach
            }
            // R-6：缺关系预期凭据的请求无从证明预览时的关系状态 → 保守拒绝，绝不默认安全。
            if (requested.expectedRelationFingerprint.isNullOrBlank()) {
                results += MaintenanceGroupApplyResult(key, ApplyStatus.CHANGED_OR_FAILED, null, 0, false,
                    MaintenanceReasonCodes.RELATION_EXPECTATION_REQUIRED)
                return@forEach
            }
            // 服务端重读证据（不接受客户端结论）；expected 只用于比对，绝不覆盖预期。
            val review = reviewGroup(members, frozenSet)
            if (review.evidenceDigest != requested.expectedFingerprint) {
                results += resumedTargetResult(review, requested)
                    ?: MaintenanceGroupApplyResult(key, ApplyStatus.CHANGED_OR_FAILED, null, 0, false,
                        MaintenanceReasonCodes.FINGERPRINT_MISMATCH)
                return@forEach
            }
            if (review.disposition != MaintenanceDisposition.MERGE_READY &&
                review.disposition != MaintenanceDisposition.KEEP_SINGLE
            ) {
                results += MaintenanceGroupApplyResult(key, ApplyStatus.CHANGED_OR_FAILED, null, 0, false, review.reason)
                return@forEach
            }
            // 关系事实在预览之后被第三方改动，且不构成「本申请已完成的目标状态」→ 拒绝。
            if (requested.expectedRelationFingerprint != null &&
                review.relationDigest != requested.expectedRelationFingerprint
            ) {
                results += resumedTargetResult(review, requested)
                    ?: MaintenanceGroupApplyResult(key, ApplyStatus.CHANGED_OR_FAILED, null, 0, false,
                        MaintenanceReasonCodes.RELATION_CHANGED)
                return@forEach
            }
            results += applyGroup(review, frozenSet)
        }
        return MaintenanceApplyOutcome.Ok(results)
    }

    /**
     * I-3（断点续跑）：组关系已是**本申请自己的目标状态**时识别为 UNCHANGED。
     *
     * 仅凭「同一人物根 + 成员集合恰好等于本组」不足以免受第三方改动 —— 那只能证明「有人做过一次同形登记」。
     * 因此这里额外要求（全部可证）：
     * - 把**本次 apply 自己会产生**的可变成分（关系行 personId/revision、历史保护 sendBlockReason/historyReference）
     *   归零后，身份/证据/人工准入/RAW 层版本等事实必须与预览预期 `expectedFingerprint` 逐字一致
     *   → 第三方改 proof/准入/层版本即失配；
     * - 预览预期 `expectedRelationFingerprint` 必须等于「整组尚未登记」的关系摘要
     *   → 本申请的原子新建是本组唯一可能来源；
     * - 人物根 `revision` 必须为 0（原子新建只写一次；此后任何关系新增都会递增）
     *   → 第三方改版本即被拒。
     *
     * 只要任一条件不成立即返回 null，由调用方按 FINGERPRINT_MISMATCH / RELATION_CHANGED 拒绝并**零写**。
     */
    private fun resumedTargetResult(
        review: MaintenanceGroupReview,
        requested: MaintenanceApplyGroup
    ): MaintenanceGroupApplyResult? {
        val expectedRelation = requested.expectedRelationFingerprint ?: return null
        val members = review.memberDocIds
        val roots = review.members.mapNotNull { it.relationPersonId }.distinct()
        if (roots.size != 1) return null
        val personId = roots.single()
        if (review.members.any { it.relationPersonId != personId }) return null
        if (review.members.any { it.sourceFingerprint == null }) return null
        if (review.members.any { !it.liveDiscovered }) return null
        if (review.disposition == MaintenanceDisposition.CHANGED_OR_FAILED) return null
        // 第三方身份/证据/准入/层版本变化：归零本申请的写入后仍须与预览预期逐字一致。
        val blanked = applyMutableBlanked(review.members)
        if (identityDigest(blanked) != requested.expectedFingerprint) return null
        // 预览预期必须是「整组尚未登记」，否则无法证明本组单根是本申请的原子新建。
        if (relationDigest(blanked) != expectedRelation) return null
        val registered = try {
            discoveryPersonRepository.findMembersByPerson(personId).map { it.expertDocId }
        } catch (e: Exception) {
            log.warn("10 断点续跑读取人物成员失败 personId={}: {}", personId, e.message)
            return null
        }
        if (registered.toSet() != members.toSet()) return null
        // 原子新建只写一次（revision=0）；任何后续关系新增/版本改动都会使其 >0 —— 版本变化即第三方改动。
        val revision = try {
            discoveryPersonRepository.findPerson(personId)?.revision
        } catch (e: Exception) {
            log.warn("10 断点续跑读取人物版本失败 personId={}: {}", personId, e.message)
            return null
        }
        if (revision != 0L) return null
        return MaintenanceGroupApplyResult(
            review.groupKey,
            ApplyStatus.UNCHANGED,
            personId,
            registered.size,
            true,
            MaintenanceReasonCodes.RESUMED_TARGET_STATE,
            revision
        )
    }

    /**
     * 归零**本次 apply 自己会产生**的可变成分：关系行（personId/revision）与历史保护
     * （sendBlockReason/historyReference）。仅用于「本次已完成」比对 —— 第三方不可改的事实原样保留。
     */
    private fun applyMutableBlanked(evidences: List<MaintenanceMemberEvidence>): List<MaintenanceMemberEvidence> =
        evidences.map {
            it.copy(
                relationPersonId = null,
                relationRevision = null,
                sendBlockReason = null,
                historyReference = false
            )
        }

    /**
     * I-2：整组成员检查完整后**一次提交**——登记关系（04）与导入历史保护（09）在同一事务；
     * 任一步失败整组回滚，绝不产生半合并。幂等：关系已存在且历史保护已就绪 → `UNCHANGED`。
     *
     * 事务边界：本方法由 [apply] 自调用（self-invocation），注解不会生成代理事务，故这里显式使用
     * 同库 [TransactionTemplate]；历史保护持久化失败时抛出回滚信号，让整组关系一并回滚。
     */
    @Transactional
    fun applyGroup(review: MaintenanceGroupReview, frozenSet: Set<String>): MaintenanceGroupApplyResult {
        val template = transactionTemplate
        return try {
            if (template == null) {
                applyGroupInTransaction(review, frozenSet)
            } else {
                template.execute(TransactionCallback { applyGroupInTransaction(review, frozenSet) })
                    ?: MaintenanceGroupApplyResult(
                        review.groupKey, ApplyStatus.CHANGED_OR_FAILED, null, 0, false,
                        MaintenanceReasonCodes.RELATION_CHANGED
                    )
            }
        } catch (e: GroupRollbackSignal) {
            log.warn("10 整组回滚 groupKey={}: {}", review.groupKey, e.message)
            MaintenanceGroupApplyResult(
                review.groupKey, ApplyStatus.CHANGED_OR_FAILED, null, 0, false,
                e.reason
            )
        }
    }

    /** 整组提交的实体（关系 + 历史保护同库同事务）。 */
    private fun applyGroupInTransaction(
        review: MaintenanceGroupReview,
        frozenSet: Set<String>
    ): MaintenanceGroupApplyResult {
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
        // 历史保护必须**实际持久化**：未持久化即整组回滚，绝不报 protectionImported=true。
        val protection = importHistoryProtection(personId, reads)
        if (protection == ProtectionImport.BLOCKED) {
            throw GroupRollbackSignal(MaintenanceReasonCodes.PROTECTION_IMPORT_FAILED)
        }
        val status = if (alreadyRegistered) ApplyStatus.UNCHANGED else ApplyStatus.APPLIED
        return MaintenanceGroupApplyResult(
            review.groupKey,
            status,
            personId,
            registration.memberCount,
            protection == ProtectionImport.IMPORTED,
            registration.reason,
            // R-6：落库后的人物 revision 是本申请的结果凭据，供回滚提案核对原版本。
            personId?.let { currentRevision(it) }
        )
    }

    /** R-6：读取人物关系 revision（读失败 → null，回滚侧缺凭据一律不得默认安全）。 */
    private fun currentRevision(personId: String): Long? =
        try {
            discoveryPersonRepository.findPerson(personId)?.revision
        } catch (e: Exception) {
            log.warn("10 读取人物版本失败 personId={}: {}", personId, e.message)
            null
        }

    /** 整组提交失败信号：由显式事务模板回滚整组关系与保护导入。 */
    private class GroupRollbackSignal(val reason: String) : RuntimeException(reason)

    /**
     * I-3：把**已有**成功/未知历史**持久化**为人物级保护（经 09 的唯一写服务），并靠 09 在真实
     * 发送前对全部成员的读检查（联系人/别名/mail_record/mail_send_attempt）持续阻断换址首发；
     * 08 的人物级准入（含人工拒绝/暂停）同样被视为历史保护。
     *
     * 这里不新建 `RESERVED` 占位（占位只能由本次 ticket 的 CAS 消费，导入的 ticket 会被丢弃并永久
     * 阻塞该人物首发）；只把已证实的 SENT/UNKNOWN 落库。导入失败按 [ProtectionImport.BLOCKED]
     * 交由调用方整组回滚，绝不报「已导入」。
     */
    private fun importHistoryProtection(personId: String?, reads: List<MemberRead>): ProtectionImport {
        val guard = discoveryPersonOutreachGuardService ?: return ProtectionImport.UNKNOWN
        if (personId == null) return ProtectionImport.NONE
        return try {
            val snapshot = guard.importExistingProtection(personId, DiscoveryPersonOutreachGuardService.INTRODUCTION)
            when {
                snapshot == null -> ProtectionImport.NONE
                snapshot.state.isBlank() -> ProtectionImport.NONE
                snapshot.state in OutreachGuardStates.PROTECTED_STATES -> ProtectionImport.IMPORTED
                else -> ProtectionImport.BLOCKED
            }
        } catch (e: Exception) {
            log.warn("10 历史保护持久化失败 personId={}: {}", personId, e.message)
            ProtectionImport.BLOCKED
        }
    }

    // ── 回滚提案（只提案，不执行） ─────────────────────────────────────────────

    fun rollbackPreview(request: MaintenanceRollbackRequest): MaintenanceRollbackOutcome {
        if (request.ruleVersion != RULE_VERSION) {
            return MaintenanceRollbackOutcome.Rejected(MaintenanceReasonCodes.RULE_VERSION_MISMATCH)
        }
        val proposals = request.groups.map { requested ->
            val members = requested.memberDocIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
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
                // I-3/V-15：任何进行中或不确定的保护（SENT/UNKNOWN/SENDING/RESERVED）、后续成员、
                // **关系版本变化或缺失原版本凭据**都不得标为可回滚 —— 只提案，绝不执行。
                // 版本证明必须对照 apply 时落库的结果凭据（[MaintenanceGroupApplyResult.personRevision]）：
                // 缺凭据、读不到版本或与当前版本不一致一律不安全。
                // （旧实现按 personId 读出的成员行再比对 personId 恒等，判据恒真、无效。）
                val state = guardState(personId)
                val unsafeState = state != null && state in OutreachGuardStates.PROTECTED_STATES
                val current = try {
                    discoveryPersonRepository.findPerson(personId)?.revision
                } catch (e: Exception) {
                    log.warn("10 回滚提案读取人物版本失败 personId={}: {}", personId, e.message)
                    null
                }
                val revisionUnproven = requested.expectedRevision == null ||
                    current == null ||
                    current != requested.expectedRevision
                val blocked = laterMembers.isNotEmpty() || unsafeState || revisionUnproven
                MaintenanceRollbackProposal(
                    key, members, personId, !blocked, memberRecords.size,
                    when {
                        laterMembers.isNotEmpty() -> MaintenanceReasonCodes.ROLLBACK_UNSAFE
                        unsafeState -> MaintenanceReasonCodes.ROLLBACK_UNSAFE
                        revisionUnproven -> MaintenanceReasonCodes.ROLLBACK_UNSAFE
                        else -> null
                    }
                )
            }
        }
        return MaintenanceRollbackOutcome.Ok(proposals)
    }

    private fun guardState(personId: String): String? =
        discoveryPersonOutreachGuardService?.stateOf(personId, DiscoveryPersonOutreachGuardService.INTRODUCTION)

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
        val evidenceVersion: Int?,
        val liveDiscovered: Boolean = false
    )

    /** 只读探针结果：`failed=true` 表示读取失败（未知保护/未知准入），绝不退化为「无事实」。 */
    private class Probe<T>(val value: T?, val failed: Boolean)

    private data class MemberEvidence(
        val evidence: MaintenanceMemberEvidence,
        val readFailure: String?
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
        if (!DiscoveryPersonScope.isLiveDiscovered(raw.source)) return failed(docId, MaintenanceReasonCodes.NOT_DISCOVERY)
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
            evidenceVersion = proof?.version,
            liveDiscovered = DiscoveryPersonScope.isLiveDiscovered(rawProfile)
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

    private fun evidence(read: MemberRead): MemberEvidence {
        var readFailure: String? = null
        val relation = try {
            discoveryPersonRepository.findMember(read.docId)
        } catch (e: Exception) {
            log.warn("10 复核读取关系失败 docId={}: {}", read.docId, e.message)
            readFailure = MaintenanceReasonCodes.RELATION_READ_FAILED
            null
        }
        val personId = relation?.personId
        // 人工准入与历史保护读取失败一律显式失败（不得转成 null 后当作「可发送」）。
        val admission = admissionProbe(read)
        if (admission.failed && readFailure == null) readFailure = MaintenanceReasonCodes.ADMISSION_READ_FAILED
        val block = sendBlockProbe(personId)
        if (block.failed && readFailure == null) readFailure = MaintenanceReasonCodes.HISTORY_READ_FAILED
        val relationRevision = personId?.let {
            try {
                discoveryPersonRepository.findPerson(it)?.revision
            } catch (e: Exception) {
                log.warn("10 复核读取人物版本失败 personId={}: {}", it, e.message)
                if (readFailure == null) readFailure = MaintenanceReasonCodes.RELATION_READ_FAILED
                null
            }
        }
        val admissionReason = admission.value
        return MemberEvidence(
            evidence = MaintenanceMemberEvidence(
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
                relationRevision = relationRevision,
                manualDecision = admissionReason,
                historyReference = block.value != null,
                admissionReason = admissionReason,
                sendBlockReason = block.value,
                layerVersions = read.layerVersions,
                liveDiscovered = read.liveDiscovered
            ),
            readFailure = readFailure
        )
    }

    private fun admissionProbe(read: MemberRead): Probe<String> {
        val recipient = discoveryPersonRecipientService ?: return Probe(null, false)
        val profile = read.profile ?: return Probe(null, false)
        return try {
            val selection = recipient.selectAdmission(listOf(profile))
            Probe((selection.excluded + selection.included).firstOrNull()?.exclusionReasons?.firstOrNull(), false)
        } catch (e: Exception) {
            log.warn("10 复核读取准入失败 docId={}: {}", read.docId, e.message)
            Probe(null, true)
        }
    }

    private fun sendBlockProbe(personId: String?): Probe<String> {
        val guard = discoveryPersonOutreachGuardService ?: return Probe(null, false)
        if (personId == null) return Probe(null, false)
        return try {
            Probe(
                when (guard.stateOf(personId, DiscoveryPersonOutreachGuardService.INTRODUCTION)) {
                    OutreachGuardStates.SENT -> OutreachGuardReasonCodes.ALREADY_SENT
                    OutreachGuardStates.UNKNOWN -> OutreachGuardReasonCodes.UNKNOWN_PENDING
                    OutreachGuardStates.SENDING, OutreachGuardStates.RESERVED -> OutreachGuardReasonCodes.IN_FLIGHT
                    else -> null
                },
                false
            )
        } catch (e: Exception) {
            log.warn("10 复核读取保护状态失败 personId={}: {}", personId, e.message)
            Probe(null, true)
        }
    }

    private fun canSend(evidences: List<MaintenanceMemberEvidence>): Boolean =
        evidences.none { it.sendBlockReason != null } && evidences.any { it.admissionReason == null }

    // ── 摘要 ──────────────────────────────────────────────────────────────────

    /**
     * 逐成员**身份/证据/人工与历史保护**摘要（不含关系行事实）：绑定三层版本、实时 discovered 标签、
     * proof 源与摘要版本、身份指纹、逐层 seq_no/primary_term、人工准入决定与历史保护判定。
     * apply 的 expected 指纹以此比对 —— 只改其中任一事实，旧预览即失配。
     */
    private fun identityDigest(evidences: List<MaintenanceMemberEvidence>): String =
        DiscoveryIdentity.hash(
            evidences.sortedBy { it.docId }.joinToString("\u0000") { member ->
                listOf(
                    member.docId,
                    member.email.orEmpty(),
                    member.authorKeys.sorted().joinToString(","),
                    member.sourceFingerprint.orEmpty(),
                    member.evidenceSource.orEmpty(),
                    member.evidenceHash.orEmpty(),
                    member.evidenceVersion?.toString().orEmpty(),
                    member.seqNo?.toString().orEmpty(),
                    member.primaryTerm?.toString().orEmpty(),
                    member.layers.sorted().joinToString(","),
                    member.layerVersions.toSortedMap().entries
                        .joinToString(",") { "${it.key}=${it.value.orEmpty()}" },
                    member.manualDecision.orEmpty(),
                    member.admissionReason.orEmpty(),
                    member.sendBlockReason.orEmpty(),
                    member.historyReference.toString(),
                    member.liveDiscovered.toString()
                ).joinToString("|")
            }
        )

    /** 关系行事实摘要（personId + revision）：被第三方改动即失配，用于断点续跑与并发拒绝。 */
    private fun relationDigest(evidences: List<MaintenanceMemberEvidence>): String =
        DiscoveryIdentity.hash(
            evidences.sortedBy { it.docId }.joinToString("\u0000") { member ->
                listOf(
                    member.docId,
                    member.relationPersonId.orEmpty(),
                    member.relationRevision?.toString().orEmpty()
                ).joinToString("|")
            }
        )

    /**
     * 预览摘要：只绑定「规则版本 + **完整冻结清单**身份」，本次选择子集不参与 ——
     * 合法 50 组试点与断点续跑都沿用同一个预览摘要。
     */
    private fun frozenDigest(frozen: List<String>): String =
        DiscoveryIdentity.hash("v$RULE_VERSION\u0000" + frozen.distinct().sorted().joinToString("\u0000"))

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
    const val OVERLAPPING_CANDIDATE_GROUPS = "OVERLAPPING_CANDIDATE_GROUPS"
    const val RELATION_READ_FAILED = "RELATION_READ_FAILED"
    const val ADMISSION_READ_FAILED = "ADMISSION_READ_FAILED"
    const val HISTORY_READ_FAILED = "HISTORY_READ_FAILED"
    const val RELATION_CHANGED = "RELATION_CHANGED"
    const val RELATION_EXPECTATION_REQUIRED = "RELATION_EXPECTATION_REQUIRED"
    const val PROTECTION_IMPORT_FAILED = "PROTECTION_IMPORT_FAILED"
    const val RESUMED_TARGET_STATE = "RESUMED_TARGET_STATE"
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
    val layerVersions: Map<String, String?> = emptyMap(),
    /** 实时 discovered 范围（来源证据 + 字面标签同时成立）—— 参与 expected 摘要绑定。 */
    val liveDiscovered: Boolean = false
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
    val members: List<MaintenanceMemberEvidence>,
    /** 关系行事实摘要（personId + revision）；apply 用它区分「本申请已完成」与「第三方改动」。 */
    val relationDigest: String = ""
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

/** 10：apply 的单组请求 —— 只带成员 ID、预览摘要指纹与预览时的关系指纹。 */
data class MaintenanceApplyGroup(
    val memberDocIds: List<String>,
    val expectedFingerprint: String,
    val expectedRelationFingerprint: String? = null
)

/**
 * 10：apply 请求 —— 冻结 ID 全集（与预览摘要同源）、规则版本、预览摘要与逐组 expected 指纹。
 * `frozenDocIds` 是**完整冻结清单**，本次 `groups` 只是它的合法子集（试点/断点续跑）。
 */
data class MaintenanceApplyRequest(
    val ruleVersion: Int? = null,
    val previewDigest: String? = null,
    val frozenDocIds: List<String> = emptyList(),
    val groups: List<MaintenanceApplyGroup> = emptyList()
)

data class MaintenanceGroupApplyResult(
    val groupKey: String,
    val status: ApplyStatus,
    val personId: String?,
    val memberCount: Int,
    val protectionImported: Boolean,
    val reason: String?,
    /**
     * R-6：本次 apply 落库后的人物关系 revision —— 供回滚提案核对的**结果凭据**。
     * `null` 表示未证明落库（失败/未变更无凭据），回滚侧缺该凭据一律不得默认安全。
     */
    val personRevision: Long? = null
)

sealed class MaintenanceApplyOutcome {
    data class Ok(val results: List<MaintenanceGroupApplyResult>) : MaintenanceApplyOutcome()
    data class Rejected(val reason: String) : MaintenanceApplyOutcome()
}

data class MaintenanceRollbackRequest(
    val ruleVersion: Int? = null,
    val groups: List<MaintenanceRollbackGroup> = emptyList()
)

/**
 * R-6：回滚提案的单组请求 —— 成员 ID + **apply 落库时的人物 revision 凭据**。
 * 缺该凭据（旧请求/响应丢失）一律不得默认安全：服务端按 `ROLLBACK_UNSAFE` 拒绝。
 */
data class MaintenanceRollbackGroup(
    val memberDocIds: List<String> = emptyList(),
    val expectedRevision: Long? = null
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
