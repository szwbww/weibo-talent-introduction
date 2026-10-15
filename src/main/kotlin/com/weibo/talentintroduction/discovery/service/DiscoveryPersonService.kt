package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberWrite
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberSnapshot
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonOutcome
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonRegistrationResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonScope
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonIdentity
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonSnapshot
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonVerifiedMember
import com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryPersonWriteStatus
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * 04/05–10（I-8/发布段）：人物能力的**统一开启边界**，默认关闭。
 *
 * - 关闭（默认）：05 增量登记不执行、06/07 展示退回逐档案口径、08 目标选择退回逐档案单位；
 *   未开启且无人物保护时行为与本轮引入前逐字一致。
 * - 关闭**不得**绕过已建立的 SENT/UNKNOWN/SENDING 首发保护：09 的占位与历史检查与总开关无关，
 *   始终按人物映射生效（授权维护窗口可在关闭时受限建立映射/导入保护）。
 * - 开启：05—09 完整链生效。
 *
 * 配置键 `talent-introduction.discovery-person.enabled`（环境变量
 * `TALENT_INTRODUCTION_DISCOVERY_PERSON_ENABLED`），缺省 `false`。本计划不修改任何运行配置。
 */
@Component
class DiscoveryPersonFeatureGate(
    @Value("\${talent-introduction.discovery-person.enabled:false}")
    val enabled: Boolean = false
)

/**
 * 04（I-1/I-2/I-3）：新发现人物主档与邮箱成员的**唯一登记协调器**。
 *
 * 只做三件事：①把调用方（05）声明的「同一人物候选」事实快照**逐条在服务端重新核实**
 * （当前 discovered 标签、真实 docId、层间身份一致、证据指纹、可信作者键）；②按
 * [DiscoveryPersonRepository] 的显式事务与唯一约束建立/挂接成员与作者键；③返回
 * `CREATED / ATTACHED / UNCHANGED / NEEDS_REVIEW / SCOPE_CONFLICT / STALE`
 * ——后三者**不写任何关系**。
 *
 * 边界（不变式）：
 * - 登记输入不被信任：客户端只提供 docId 与证据指纹，其余事实全部由服务端从 ES 当前档案重算；
 *   指纹不一致（含档案改动、标签变化、来源不可读）一律 [DiscoveryPersonOutcome.STALE]。
 * - 不据同名归组、不做模糊匹配、不迁移范围外对象：email/作者键/联系人别名命中范围外对象时
 *   返回 [DiscoveryPersonOutcome.SCOPE_CONFLICT]。
 * - 两个已有人物根被新证据连接时返回 [DiscoveryPersonOutcome.NEEDS_REVIEW]，不自动吞并根。
 * - 挂接既有根前必须复验该根**全部当前成员**（不只本次请求快照）：任一成员读不到、已不属实时
 *   discovered 范围或与登记指纹不符即零写入（[DiscoveryPersonOutcome.STALE]）；规范全名与本次组
 *   不一致即待人工复核，绝不以新成员绕过根上无法复验的旧关系。
 * - 并发下「只产生一个主档」由作者键主键唯一约束 + 排序锁保证；唯一键冲突后重新读并复验，
 *   绝不把冲突当成功。
 * - 默认关闭：本服务是**纯登记入口**，不实现任何全表回填/扫描，也不做 GET 或发送预估；
 *   只有 05/10 显式调用才会写关系（无调用者时对既有读写零影响）。
 *
 * @param clock 时间seam，缺省 UTC。
 */
@Service
class DiscoveryPersonService(
    private val repository: DiscoveryPersonRepository,
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val expertContactRepository: ExpertContactRepository,
    private val expertEmailAliasService: ExpertEmailAliasService,
    private val clock: Clock = Clock.systemUTC()
) {
    private val log = LoggerFactory.getLogger(DiscoveryPersonService::class.java)
    private val mapper = jacksonObjectMapper()

    fun register(snapshot: DiscoveryPersonSnapshot): DiscoveryPersonRegistrationResult {
        validateStructure(snapshot)?.let { return it }

        repeat(MAX_CONFLICT_ATTEMPTS) {
            // I-2/I-3：每一轮（含唯一键/锁冲突后的重试）都从当前 ES 事实**重新核实整组**——
            // 绝不把上一轮的验证结果当作复验（冲突后档案可能已被并发改动）。
            val group = when (val verification = verifyGroup(snapshot.source, snapshot.members)) {
                is GroupVerification.Rejected -> return verification.result
                is GroupVerification.Verified -> verification
            }
            when (val resolution = resolve(group.members, group.groupKeys)) {
                is Resolution.Reject -> return resolution.result
                is Resolution.Unchanged -> return resolution.result
                is Resolution.Create -> {
                    val personId = UUID.randomUUID().toString()
                    val status = write {
                        repository.createPerson(
                            personId, resolution.primaryDocId, group.members.map { it.write() }, group.groupKeys, now()
                        )
                    }
                    if (status == DiscoveryPersonWriteStatus.APPLIED) {
                        return result(
                            DiscoveryPersonOutcome.CREATED,
                            personId = personId,
                            memberCount = repository.findMembersByPerson(personId).size
                        )
                    }
                }
                is Resolution.Attach -> {
                    // R-2：锁后核验（revision + 当前完整作者键）——并发窗口内的事实变化不得被旧结果掩盖。
                    val status = write {
                        repository.attachToPersonVerified(
                            resolution.personId, resolution.members, resolution.keys, now(), resolution.expectedRevision
                        )
                    }
                    if (status == DiscoveryPersonWriteStatus.APPLIED) {
                        return result(
                            DiscoveryPersonOutcome.ATTACHED,
                            personId = resolution.personId,
                            memberCount = repository.findMembersByPerson(resolution.personId).size
                        )
                    }
                }
            }
            // 冲突（唯一键/成员被并发占用）：回到循环头部，重新读 ES 并复验后再决策。
        }
        log.warn("人物登记在 {} 次冲突后仍未收敛，保持不写关系：docIds={}", MAX_CONFLICT_ATTEMPTS, snapshot.members.map { it.docId })
        return result(DiscoveryPersonOutcome.NEEDS_REVIEW, reason = "REGISTRATION_CONFLICT")
    }

    // ── 服务端核实 ─────────────────────────────────────────────────────────────

    private sealed interface MemberVerification {
        data class Verified(val member: DiscoveryPersonVerifiedMember) : MemberVerification
        data class Rejected(val outcome: DiscoveryPersonOutcome, val reason: String) : MemberVerification
    }

    private sealed interface GroupVerification {
        data class Verified(
            val members: List<DiscoveryPersonVerifiedMember>,
            val groupKeys: List<String>
        ) : GroupVerification

        data class Rejected(val result: DiscoveryPersonRegistrationResult) : GroupVerification
    }

    /**
     * I-2：**整组**核实（每次尝试都重新执行）——逐个成员按当前 ES 档案重核后，再在组边界上检验
     * 可信作者键、可归入性与规范全名一致；跨范围对象在这里只报冲突、不迁移。
     */
    private fun verifyGroup(
        source: String,
        snapshotMembers: List<DiscoveryPersonMemberSnapshot>
    ): GroupVerification {
        val verified = mutableListOf<DiscoveryPersonVerifiedMember>()
        for (member in snapshotMembers) {
            when (val verification = verifyMember(source, member)) {
                is MemberVerification.Rejected ->
                    return GroupVerification.Rejected(result(verification.outcome, reason = verification.reason))
                is MemberVerification.Verified -> verified += verification.member
            }
        }

        // I-2：多成员组内每个成员都必须有可信作者键，且组必须由共享键连通。
        if (verified.size > 1 && verified.any { it.authorKeys.isEmpty() }) {
            return GroupVerification.Rejected(result(DiscoveryPersonOutcome.NEEDS_REVIEW, reason = "UNBOUND_MEMBER"))
        }
        // I-2「每类作者键不冲突」：比较的是**去重后的类别键**——多个成员共享同一 ORCID
        // 是同一人的正常证据，只有出现两个**不同**的同类作者键才不可归入。
        val allKeys = verified.flatMap { it.authorKeys }
        val distinctOrcid = allKeys.filter { it.startsWith(DiscoveryPersonIdentity.ORCID_PREFIX) }.distinct()
        val distinctOpenAlex = allKeys.filter { it.startsWith(DiscoveryPersonIdentity.OPENALEX_PREFIX) }.distinct()
        if (distinctOrcid.size > 1 || distinctOpenAlex.size > 1) {
            return GroupVerification.Rejected(result(DiscoveryPersonOutcome.NEEDS_REVIEW, reason = "MULTIPLE_AUTHOR_KEYS"))
        }
        // I-2：整组规范全名必须一致——两个不同的规范全名即使共享作者键也不是同一人。
        if (verified.size > 1) {
            val fullNames = verified.map {
                DiscoveryPersonIdentity.normalizedFullName(it.givenNames, it.familyNames)
            }.distinct()
            if (fullNames.size > 1) {
                return GroupVerification.Rejected(result(DiscoveryPersonOutcome.NEEDS_REVIEW, reason = "GROUP_NAME_MISMATCH"))
            }
        }
        if (!connected(verified)) {
            return GroupVerification.Rejected(result(DiscoveryPersonOutcome.NEEDS_REVIEW, reason = "UNCONNECTED_MEMBER"))
        }
        // I-3：跨范围对象不迁移，只报冲突。
        scopeConflict(verified)?.let { return GroupVerification.Rejected(it) }
        return GroupVerification.Verified(verified, allKeys.distinct().sorted())
    }

    private fun validateStructure(snapshot: DiscoveryPersonSnapshot): DiscoveryPersonRegistrationResult? {
        if (snapshot.source.isBlank()) return result(DiscoveryPersonOutcome.STALE, reason = "SOURCE_UNVERIFIED")
        if (snapshot.members.isEmpty()) return result(DiscoveryPersonOutcome.STALE, reason = "EMPTY_SNAPSHOT")
        val ids = snapshot.members.map { it.docId }
        if (ids.any { it.isBlank() } || ids.distinct().size != ids.size) {
            return result(DiscoveryPersonOutcome.STALE, reason = "INVALID_SNAPSHOT")
        }
        if (snapshot.members.any { it.evidenceFingerprint.isBlank() }) {
            return result(DiscoveryPersonOutcome.STALE, reason = "INVALID_SNAPSHOT")
        }
        return null
    }

    private fun verifyMember(source: String, snapshot: DiscoveryPersonMemberSnapshot): MemberVerification {
        val docId = snapshot.docId
        val snapshots = LinkedHashMap<ExpertIndexLevel, ExpertIndexWriterService.DiscoverySnapshot>()
        for (level in LAYERS) {
            val document = try {
                expertIndexWriterService.readDiscoveryDocument(level, docId)
            } catch (e: Exception) {
                log.warn("人物登记读取 {} 层失败 docId={}: {}", level, docId, e.message)
                return MemberVerification.Rejected(DiscoveryPersonOutcome.STALE, "SOURCE_UNAVAILABLE")
            }
            if (document != null) snapshots[level] = document
        }
        if (snapshots.isEmpty()) return MemberVerification.Rejected(DiscoveryPersonOutcome.STALE, "DOCUMENT_MISSING")
        val raw = snapshots[ExpertIndexLevel.RAW] ?: snapshots.values.first()
        // I-3：人物范围必须**实时**成立（发现来源证据 + 字面 discovered 标签），否则快照已失效。
        if (!DiscoveryPersonScope.isLiveDiscovered(raw.source)) {
            return MemberVerification.Rejected(DiscoveryPersonOutcome.STALE, "NOT_DISCOVERY")
        }
        if (!layerIdentityConsistent(snapshots)) {
            return MemberVerification.Rejected(DiscoveryPersonOutcome.NEEDS_REVIEW, "LAYER_IDENTITY_CONFLICT")
        }
        val profiles = snapshots.values.map { expertIndexWriterService.discoveryProfile(docId, it.source) }
        val rawProfile = expertIndexWriterService.discoveryProfile(docId, raw.source)
        val normalizedEmail = DiscoveryIdentity.normalizedEmail(rawProfile.email).takeIf { it.isNotEmpty() }
        val authorKeys = DiscoveryPersonIdentity.authorKeys(profiles)
        val fingerprint = DiscoveryPersonIdentity.fingerprint(
            docId, normalizedEmail, rawProfile.givenNames, rawProfile.familyNames, authorKeys
        )
        if (fingerprint != snapshot.evidenceFingerprint) {
            return MemberVerification.Rejected(DiscoveryPersonOutcome.STALE, "FINGERPRINT_MISMATCH")
        }
        return MemberVerification.Verified(
            DiscoveryPersonVerifiedMember(
                docId = docId,
                businessKey = rawProfile.orcidId.takeIf { it.isNotBlank() } ?: docId,
                normalizedEmail = normalizedEmail,
                givenNames = rawProfile.givenNames,
                familyNames = rawProfile.familyNames,
                authorKeys = authorKeys,
                identityFingerprint = fingerprint,
                evidenceJson = evidenceJson(source, docId, normalizedEmail, authorKeys)
            )
        )
    }

    /** 层间身份一致：`orcidId/email/givenNames/familyNames/externalIds/identityVerification` 逐键相同。 */
    private fun layerIdentityConsistent(
        snapshots: Map<ExpertIndexLevel, ExpertIndexWriterService.DiscoverySnapshot>
    ): Boolean {
        val sources = snapshots.values.map { it.source }
        return LAYER_IDENTITY_KEYS.all { key -> sources.all { it[key] == sources.first()[key] } }
    }

    private fun evidenceJson(source: String, docId: String, normalizedEmail: String?, authorKeys: List<String>): String =
        mapper.writeValueAsString(
            mapOf(
                "source" to source,
                "docId" to docId,
                "normalizedEmail" to normalizedEmail,
                "authorKeys" to authorKeys,
                "ruleVersion" to RULE_VERSION
            )
        )

    /** 作者键连通性：多成员组必须由共享作者键连成一段，否则不是可核实的同一人。 */
    private fun connected(members: List<DiscoveryPersonVerifiedMember>): Boolean {
        if (members.size <= 1) return true
        val keys = members.map { it.authorKeys.toSet() }
        val visited = BooleanArray(members.size)
        val stack = ArrayDeque<Int>()
        visited[0] = true
        stack.addLast(0)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            for (index in members.indices) {
                if (!visited[index] && keys[current].intersect(keys[index]).isNotEmpty()) {
                    visited[index] = true
                    stack.addLast(index)
                }
            }
        }
        return visited.all { it }
    }

    // ── 范围冲突（只读，不迁移范围外对象） ─────────────────────────────────────

    private fun scopeConflict(verified: List<DiscoveryPersonVerifiedMember>): DiscoveryPersonRegistrationResult? {
        for (member in verified) {
            val own = member.businessKey
            val email = member.normalizedEmail
            if (email != null) {
                val contact = try {
                    expertContactRepository.findFirstByExpertEmailOrderByUpdatedAtDesc(email)
                } catch (e: Exception) {
                    log.warn("人物登记范围检查（联系人邮箱）失败 docId={}: {}", member.docId, e.message)
                    return result(DiscoveryPersonOutcome.STALE, reason = "SCOPE_CHECK_FAILED")
                }
                if (contact != null && contact.orcidId != own) {
                    return result(DiscoveryPersonOutcome.SCOPE_CONFLICT, reason = "EMAIL_OUT_OF_SCOPE")
                }
                val aliasContact = try {
                    expertEmailAliasService.findContactByEmailOrAlias(email)
                } catch (e: Exception) {
                    log.warn("人物登记范围检查（别名）失败 docId={}: {}", member.docId, e.message)
                    return result(DiscoveryPersonOutcome.STALE, reason = "SCOPE_CHECK_FAILED")
                }
                if (aliasContact != null && aliasContact.orcidId != own) {
                    return result(DiscoveryPersonOutcome.SCOPE_CONFLICT, reason = "EMAIL_ALIAS_OUT_OF_SCOPE")
                }
            }
            for (key in member.authorKeys) {
                val value = key.substringAfter(':')
                val contact = try {
                    expertContactRepository.findFirstByOrcidIdOrderByUpdatedAtDesc(value)
                } catch (e: Exception) {
                    log.warn("人物登记范围检查（作者键）失败 docId={}: {}", member.docId, e.message)
                    return result(DiscoveryPersonOutcome.STALE, reason = "SCOPE_CHECK_FAILED")
                }
                if (contact != null && contact.orcidId != own) {
                    return result(DiscoveryPersonOutcome.SCOPE_CONFLICT, reason = "AUTHOR_KEY_OUT_OF_SCOPE")
                }
            }
        }
        return null
    }

    // ── 决策（每次冲突都重新读一遍仓储状态） ───────────────────────────────────

    private sealed interface RootGroupVerification {
        data class Verified(val members: List<DiscoveryPersonVerifiedMember>) : RootGroupVerification
        data class Rejected(val result: DiscoveryPersonRegistrationResult) : RootGroupVerification
    }

    private sealed interface Resolution {
        data class Create(val primaryDocId: String) : Resolution
        data class Attach(
            val personId: String,
            val members: List<DiscoveryPersonMemberWrite>,
            val keys: List<String>,
            /** R-2：核验该根时读到的 revision —— 锁后必须仍然一致才允许提交。 */
            val expectedRevision: Long
        ) : Resolution
        data class Unchanged(val result: DiscoveryPersonRegistrationResult) : Resolution
        data class Reject(val result: DiscoveryPersonRegistrationResult) : Resolution
    }

    private fun resolve(verified: List<DiscoveryPersonVerifiedMember>, groupKeys: List<String>): Resolution {
        val existing = verified.mapNotNull { member -> repository.findMember(member.docId) }
        // I-3：成员指纹变化即失效待重核，绝不静默改写已登记关系。
        val changed = existing.firstOrNull { record ->
            verified.first { it.docId == record.expertDocId }.identityFingerprint != record.identityFingerprint
        }
        if (changed != null) {
            return Resolution.Reject(result(DiscoveryPersonOutcome.STALE, reason = "MEMBER_FINGERPRINT_CHANGED"))
        }
        val roots = (
            existing.map { it.personId } + repository.findPersonIdsByAuthorKeys(groupKeys).values
            ).distinct()
        if (roots.size > 1) {
            // I-2：两个已有人物根被新证据连接 → 人工复核，不自动吞并。
            return Resolution.Reject(result(DiscoveryPersonOutcome.NEEDS_REVIEW, reason = "BRIDGE_EXISTING_ROOTS"))
        }
        if (roots.isEmpty()) {
            return Resolution.Create(verified.map { it.docId }.min())
        }
        val personId = roots.single()
        // I-2/V-3：挂接前复验该根**全部当前成员**（不只本次请求快照里的那几个）——任一成员不可
        // 复验即零写入，绝不把新成员挂到无法完整复验的根上。
        val rootGroup = when (val verification = verifyRootGroup(personId, verified)) {
            is RootGroupVerification.Rejected -> return Resolution.Reject(verification.result)
            is RootGroupVerification.Verified -> verification
        }
        // I-2：附加成员必须与既有根**全部当前作者键**（含已复验成员的当前键）同类唯一——同 ORCID 但
        // 不同 OpenAlex 键（或反之）不是同一人，绝不静默挂接；冲突即待核，不写关系。
        val existingKeys = repository.findAuthorKeysByPerson(personId).map { it.authorKey }
        val combined = (existingKeys + rootGroup.members.flatMap { it.authorKeys } + groupKeys).distinct()
        val combinedOrcid = combined.filter { it.startsWith(DiscoveryPersonIdentity.ORCID_PREFIX) }.distinct()
        val combinedOpenAlex = combined.filter { it.startsWith(DiscoveryPersonIdentity.OPENALEX_PREFIX) }.distinct()
        if (combinedOrcid.size > 1 || combinedOpenAlex.size > 1) {
            return Resolution.Reject(result(DiscoveryPersonOutcome.NEEDS_REVIEW, reason = "EXISTING_AUTHOR_KEY_CONFLICT"))
        }
        val attached = existing.map { it.expertDocId }.toSet()
        val newMembers = verified.filter { it.docId !in attached }.map { it.write() }
        val linkedKeys = existingKeys.toSet()
        val newKeys = groupKeys.filter { it !in linkedKeys }
        if (newMembers.isEmpty() && newKeys.isEmpty()) {
            return Resolution.Unchanged(
                result(
                    DiscoveryPersonOutcome.UNCHANGED,
                    personId = personId,
                    memberCount = repository.findMembersByPerson(personId).size
                )
            )
        }
        val expectedRevision = try {
            repository.findPerson(personId)?.revision
        } catch (e: Exception) {
            log.warn("人物登记读取根 revision 失败 personId={}: {}", personId, e.message)
            null
        } ?: return Resolution.Reject(result(DiscoveryPersonOutcome.STALE, reason = "ROOT_REVISION_UNAVAILABLE"))
        return Resolution.Attach(personId, newMembers, newKeys, expectedRevision)
    }

    /**
     * I-2/V-3：挂接既有根前的**整组**复验。
     *
     * 该根的全部当前成员（除本次请求已核实的 docId）都按**当前 ES 档案**重核一次——实时 discovered
     * 范围、层间身份一致、规范邮箱/全名与作者键，且必须与登记时留存的成员指纹一致。任一成员不可读、
     * 已失效或指纹变化即 [RootGroupVerification.Rejected]（零写入）；规范全名与本次组不一致即待人工复核
     * （同名比较覆盖整根成员，不只请求快照）。既有成员不会重新写 evidence_json，故来源标记仅占位。
     */
    private fun verifyRootGroup(
        personId: String,
        verified: List<DiscoveryPersonVerifiedMember>
    ): RootGroupVerification {
        val requestDocIds = verified.map { it.docId }.toSet()
        val rootMembers = mutableListOf<DiscoveryPersonVerifiedMember>()
        for (record in repository.findMembersByPerson(personId)) {
            if (record.expertDocId in requestDocIds) continue
            val snapshot = DiscoveryPersonMemberSnapshot(record.expertDocId, record.identityFingerprint)
            when (val verification = verifyMember(ROOT_GROUP_SOURCE, snapshot)) {
                is MemberVerification.Rejected ->
                    return RootGroupVerification.Rejected(result(verification.outcome, reason = verification.reason))
                is MemberVerification.Verified -> rootMembers += verification.member
            }
        }
        val fullNames = (verified + rootMembers)
            .map { DiscoveryPersonIdentity.normalizedFullName(it.givenNames, it.familyNames) }
            .distinct()
        if (fullNames.size > 1) {
            return RootGroupVerification.Rejected(
                result(DiscoveryPersonOutcome.NEEDS_REVIEW, reason = "GROUP_NAME_MISMATCH")
            )
        }
        return RootGroupVerification.Verified(rootMembers)
    }

    private inline fun write(block: () -> DiscoveryPersonWriteStatus): DiscoveryPersonWriteStatus? =
        try {
            block()
        } catch (e: DuplicateKeyException) {
            // 最后一刻的并发唯一键冲突：事务已回滚，交由外层重新读并复验。
            log.debug("人物登记唯一键冲突，重新读并复验: {}", e.message)
            null
        } catch (e: PessimisticLockingFailureException) {
            // 并发同键首次登记时，MySQL 可能以死锁（DeadlockLoserDataAccessException）而非
            // 重复键落败；同样是「本事务未写任何行」的竞争信号，回滚后重新读并复验。
            log.debug("人物登记并发锁冲突，重新读并复验: {}", e.message)
            null
        }

    private fun now(): LocalDateTime = LocalDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC)

    private fun result(
        outcome: DiscoveryPersonOutcome,
        personId: String? = null,
        memberCount: Int = 0,
        reason: String? = null
    ) = DiscoveryPersonRegistrationResult(outcome, personId, memberCount, reason)

    companion object {
        val LAYERS = listOf(ExpertIndexLevel.RAW, ExpertIndexLevel.CANDIDATE, ExpertIndexLevel.APPLICATION)
        const val MAX_CONFLICT_ATTEMPTS = 3
        const val RULE_VERSION = 1

        /** I-2/V-3：既有根成员复验的来源占位——这些成员已在关系里，不会重新写 `evidence_json`。 */
        const val ROOT_GROUP_SOURCE = "EXISTING_PERSON_MEMBER"
        private val LAYER_IDENTITY_KEYS =
            listOf("orcidId", "email", "givenNames", "familyNames", "externalIds", "identityVerification")
    }
}
