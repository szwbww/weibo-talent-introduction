package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonIdentity
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonMemberSnapshot
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonOutcome
import com.weibo.talentintroduction.discovery.domain.DiscoveryPersonSnapshot
import com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.IdentityVerification
import com.weibo.talentintroduction.expert.service.DiscoveryEmailHit
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.IdentityProofWriteResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 01（I-1/I-2/I-3）：重复发现同身份补证的唯一协调器。
 *
 * 只做一件事：把**本次重复来源**已经能证明的身份，补到该业务 `_id` 在 RAW/CANDIDATE/APPLICATION
 * 三层中**还没有 proof** 的层上。它从不推测身份、不创建缺层、不覆盖已有 proof、不改姓名/邮箱/
 * 作者编号/学术/运营字段；成功后由调用方（[ExpertDiscoveryService]）走既有入队与准入接缝，
 * 政策判定不在这里复制。
 *
 * 入口签名与 [IdentityRepairOutcome] 枚举是 02/05 依赖的稳定契约：02 在其上扩展「只填空姓名 + proof」
 * 受限模式，05 在其上统一登记人物关系。
 */
@Service
class DiscoveryIdentityRepairService(
    private val expertIndexWriterService: ExpertIndexWriterService,
    /**
     * 05（I-1）：各层事实补齐成功后的统一人物登记刷新接缝。为空（历史单测/未装配）时保持原行为：
     * 只补 proof、不建立人物关系；生产恒由 Spring 注入。
     */
    private val personRegistrationService: DiscoveryPersonRegistrationService? = null
) {
    private val log = LoggerFactory.getLogger(DiscoveryIdentityRepairService::class.java)
    private val mapper = jacksonObjectMapper()

    /** I-2：补证前必须同时检查三层；缺层不创建，已有 proof 的层不改。 */
    private val repairedLevels = listOf(ExpertIndexLevel.RAW, ExpertIndexLevel.CANDIDATE, ExpertIndexLevel.APPLICATION)

    /**
     * 对 `docId` 执行一次同身份补证。`incoming` 必须来自现有提取器（完整姓名 + 有效来源证据）。
     *
     * 结果语义（I-3）：
     * - [IdentityRepairOutcome.REPAIRED]：至少写成功一层，且每个写入层回读都与目标 proof 一致；
     * - [IdentityRepairOutcome.ALREADY_VERIFIED]：三层都已有 proof（含历史认可/无效摘要），本次零写入；
     * - [IdentityRepairOutcome.MISSING_NAME_NEEDS_RECOVERY]：姓名缺失（incoming 或已存层），不自动补名；
     * - [IdentityRepairOutcome.CONFLICT]：该邮箱映射到多个 `_id`，或姓名/邮箱/作者编号相冲突，零写入；
     * - [IdentityRepairOutcome.PARTIAL] / [FAILED]：存在未完成的层写入或查询失败，必须保留可重试错误。
     */
    fun repairDuplicateIdentity(docId: String, incoming: AuthorEmail): IdentityRepairResult {
        if (docId.isBlank()) return IdentityRepairResult(IdentityRepairOutcome.FAILED)
        // I-1：必须有完整姓名；空姓名不是已证实冲突，交给 02 定向恢复，绝不自动补名。
        if (incoming.givenNames.isNullOrBlank() || incoming.familyNames.isNullOrBlank()) {
            return IdentityRepairResult(IdentityRepairOutcome.MISSING_NAME_NEEDS_RECOVERY)
        }
        // I-1：incoming 只能来自现有提取器；没有有效来源证据就没有可信身份，不做任何写。
        if (!DiscoveryIdentity.validEvidence(incoming.identityEvidence)) {
            return IdentityRepairResult(IdentityRepairOutcome.CONFLICT)
        }
        val email = DiscoveryIdentity.normalizedEmail(incoming.email)
        if (email.isEmpty()) return IdentityRepairResult(IdentityRepairOutcome.CONFLICT)

        // I-2：原 size=1 去重结果只能用于发现重复；修复全集必须来自邮箱全量命中，查询失败不当不存在。
        val hits = try {
            expertIndexWriterService.findDiscoveryHitsByEmail(incoming.email)
        } catch (e: Exception) {
            log.warn("身份补证邮箱全量查询失败 docId={}: {}", docId, e.message)
            return IdentityRepairResult(IdentityRepairOutcome.FAILED)
        }
        val ids = hits.map { it.docId }.distinct()
        if (ids.isEmpty()) return IdentityRepairResult(IdentityRepairOutcome.FAILED)
        // 不同 `_id` 同邮箱一律冲突；三层同 `_id` 只算一个人。
        if (ids.size != 1 || ids.single() != docId) return IdentityRepairResult(IdentityRepairOutcome.CONFLICT)

        val snapshots = LinkedHashMap<ExpertIndexLevel, ExpertIndexWriterService.DiscoverySnapshot?>()
        for (level in repairedLevels) {
            snapshots[level] = try {
                expertIndexWriterService.readDiscoveryDocument(level, docId)
            } catch (e: Exception) {
                log.warn("身份补证读取 {} 层失败 docId={}: {}", level, docId, e.message)
                return IdentityRepairResult(IdentityRepairOutcome.FAILED)
            }
        }
        if (snapshots.values.all { it == null }) return IdentityRepairResult(IdentityRepairOutcome.FAILED)

        val stored = snapshots.mapNotNull { (level, snap) -> snap?.let { level to it.source } }
        // I-1：规范邮箱及已填姓名必须精确一致；空姓名是缺证而非冲突。
        for ((_, source) in stored) {
            if (email != DiscoveryIdentity.normalizedEmail(source["email"] as? String)) {
                return IdentityRepairResult(IdentityRepairOutcome.CONFLICT)
            }
            val given = (source["givenNames"] as? String)?.takeIf { it.isNotBlank() }
            val family = (source["familyNames"] as? String)?.takeIf { it.isNotBlank() }
            if (given == null || family == null) {
                return IdentityRepairResult(IdentityRepairOutcome.MISSING_NAME_NEEDS_RECOVERY)
            }
            if (given != incoming.givenNames || family != incoming.familyNames) {
                return IdentityRepairResult(IdentityRepairOutcome.CONFLICT)
            }
        }
        // I-2：非空作者编号在层内、跨层或与 incoming 任意相冲突即零写入。
        if (authorIdsConflict(stored, incoming)) return IdentityRepairResult(IdentityRepairOutcome.CONFLICT)

        val target = DiscoveryIdentity.verified(
            incoming.email, incoming.givenNames, incoming.familyNames,
            incoming.identityEvidence!!, incoming.orcidId, incoming.openAlexAuthorId
        )

        val layers = mutableListOf<IdentityRepairLayer>()
        var written = 0
        var failed = 0
        for ((level, snapshot) in snapshots) {
            if (snapshot == null) {
                layers += IdentityRepairLayer(level, IdentityRepairLayerOutcome.MISSING)
                continue
            }
            val previous = proofOf(snapshot.source)
            if (previous != null) {
                // I-1：已有 proof（含历史认可/无效摘要）不自动覆盖。
                layers += IdentityRepairLayer(level, IdentityRepairLayerOutcome.ALREADY_VERIFIED, previous, previous)
                continue
            }
            when (expertIndexWriterService.writeIdentityProofIfVersionMatches(
                level, docId, snapshot.seqNo, snapshot.primaryTerm, target
            )) {
                IdentityProofWriteResult.UPDATED -> {
                    val current = try {
                        expertIndexWriterService.readDiscoveryDocument(level, docId)?.let { proofOf(it.source) }
                    } catch (e: Exception) {
                        log.warn("身份补证回读 {} 层失败 docId={}: {}", level, docId, e.message)
                        null
                    }
                    if (current == target) {
                        written++
                        layers += IdentityRepairLayer(level, IdentityRepairLayerOutcome.REPAIRED, null, current)
                    } else {
                        failed++
                        layers += IdentityRepairLayer(level, IdentityRepairLayerOutcome.WRITE_FAILED, null, current)
                    }
                }
                IdentityProofWriteResult.MISSING ->
                    layers += IdentityRepairLayer(level, IdentityRepairLayerOutcome.MISSING)
                IdentityProofWriteResult.VERSION_CONFLICT -> {
                    failed++
                    layers += IdentityRepairLayer(level, IdentityRepairLayerOutcome.VERSION_CONFLICT)
                }
                IdentityProofWriteResult.FAILED -> {
                    failed++
                    layers += IdentityRepairLayer(level, IdentityRepairLayerOutcome.WRITE_FAILED)
                }
            }
        }
        val outcome = when {
            failed > 0 && written > 0 -> IdentityRepairOutcome.PARTIAL
            failed > 0 -> IdentityRepairOutcome.FAILED
            written > 0 -> IdentityRepairOutcome.REPAIRED
            else -> IdentityRepairOutcome.ALREADY_VERIFIED
        }
        return IdentityRepairResult(
            outcome, target, layers,
            // 05（I-1）：事实成功补齐后统一刷新人物登记；失败不改变补证结论，由调用方按可重试语义传播。
            personRegistration = refreshRegistration(docId, outcome)
        )
    }

    /**
     * 02（I-2）：受限「填空姓名 + proof」模式 —— **只服务**已完成来源复查的维护入口
     * （[DiscoveryIdentityRecoveryService]）；自动增量入口从不启用，也不改变
     * [repairDuplicateIdentity] 的既有语义。
     *
     * 前置（调用方已判，此处再次强制）：完整姓名 + 有效来源证据；规范邮箱唯一命中本 `docId`；
     * 三层无身份/作者编号冲突；没有 existing proof。**只填**每层为空的姓名段，
     * 已有非空姓名段与 incoming 不一致或已有 proof 一律零写入；CAS 写失败保留可重试错误。
     */
    fun repairEmptyNameProof(docId: String, incoming: AuthorEmail): IdentityRepairResult {
        if (docId.isBlank()) return IdentityRepairResult(IdentityRepairOutcome.FAILED)
        if (incoming.givenNames.isNullOrBlank() || incoming.familyNames.isNullOrBlank()) {
            return IdentityRepairResult(IdentityRepairOutcome.MISSING_NAME_NEEDS_RECOVERY)
        }
        if (!DiscoveryIdentity.validEvidence(incoming.identityEvidence)) {
            return IdentityRepairResult(IdentityRepairOutcome.CONFLICT)
        }
        val email = DiscoveryIdentity.normalizedEmail(incoming.email)
        if (email.isEmpty()) return IdentityRepairResult(IdentityRepairOutcome.CONFLICT)

        val hits = try {
            expertIndexWriterService.findDiscoveryHitsByEmail(incoming.email)
        } catch (e: Exception) {
            log.warn("空名补证邮箱全量查询失败 docId={}: {}", docId, e.message)
            return IdentityRepairResult(IdentityRepairOutcome.FAILED)
        }
        val ids = hits.map { it.docId }.distinct()
        if (ids.isEmpty()) return IdentityRepairResult(IdentityRepairOutcome.FAILED)
        if (ids.size != 1 || ids.single() != docId) return IdentityRepairResult(IdentityRepairOutcome.CONFLICT)

        val snapshots = LinkedHashMap<ExpertIndexLevel, ExpertIndexWriterService.DiscoverySnapshot?>()
        for (level in repairedLevels) {
            snapshots[level] = try {
                expertIndexWriterService.readDiscoveryDocument(level, docId)
            } catch (e: Exception) {
                log.warn("空名补证读取 {} 层失败 docId={}: {}", level, docId, e.message)
                return IdentityRepairResult(IdentityRepairOutcome.FAILED)
            }
        }
        if (snapshots.values.all { it == null }) return IdentityRepairResult(IdentityRepairOutcome.FAILED)

        val stored = snapshots.mapNotNull { (level, snap) -> snap?.let { level to it.source } }
        for ((_, source) in stored) {
            if (email != DiscoveryIdentity.normalizedEmail(source["email"] as? String)) {
                return IdentityRepairResult(IdentityRepairOutcome.CONFLICT)
            }
            val given = (source["givenNames"] as? String)?.takeIf { it.isNotBlank() }
            val family = (source["familyNames"] as? String)?.takeIf { it.isNotBlank() }
            // 已填姓名从不覆盖：任何非空姓名段与 incoming 不符即零写入。
            if (given != null && given != incoming.givenNames) {
                return IdentityRepairResult(IdentityRepairOutcome.CONFLICT)
            }
            if (family != null && family != incoming.familyNames) {
                return IdentityRepairResult(IdentityRepairOutcome.CONFLICT)
            }
            // 已有 proof（含历史认可/无效摘要）：不是空名补证对象，零写入。
            if (proofOf(source) != null) {
                return IdentityRepairResult(IdentityRepairOutcome.ALREADY_VERIFIED)
            }
        }
        if (authorIdsConflict(stored, incoming)) return IdentityRepairResult(IdentityRepairOutcome.CONFLICT)

        val target = DiscoveryIdentity.verified(
            incoming.email, incoming.givenNames, incoming.familyNames,
            incoming.identityEvidence!!, incoming.orcidId, incoming.openAlexAuthorId
        )

        val layers = mutableListOf<IdentityRepairLayer>()
        var written = 0
        var failed = 0
        for ((level, snapshot) in snapshots) {
            if (snapshot == null) {
                layers += IdentityRepairLayer(level, IdentityRepairLayerOutcome.MISSING)
                continue
            }
            val source = snapshot.source
            val givenBlank = (source["givenNames"] as? String).isNullOrBlank()
            val familyBlank = (source["familyNames"] as? String).isNullOrBlank()
            val givenToWrite = incoming.givenNames.takeIf { givenBlank }
            val familyToWrite = incoming.familyNames.takeIf { familyBlank }
            if (givenToWrite == null && familyToWrite == null) {
                layers += IdentityRepairLayer(level, IdentityRepairLayerOutcome.ALREADY_VERIFIED)
                continue
            }
            when (expertIndexWriterService.writeDiscoveryNameAndProofIfVersionMatches(
                level, docId, snapshot.seqNo, snapshot.primaryTerm, givenToWrite, familyToWrite, target
            )) {
                IdentityProofWriteResult.UPDATED -> {
                    val current = try {
                        expertIndexWriterService.readDiscoveryDocument(level, docId)
                    } catch (e: Exception) {
                        log.warn("空名补证回读 {} 层失败 docId={}: {}", level, docId, e.message)
                        null
                    }
                    val verified = current?.takeIf { snap ->
                        snap.source["givenNames"] == incoming.givenNames &&
                            snap.source["familyNames"] == incoming.familyNames &&
                            proofOf(snap.source) == target
                    }
                    if (verified != null) {
                        written++
                        layers += IdentityRepairLayer(level, IdentityRepairLayerOutcome.REPAIRED, null, proofOf(verified.source))
                    } else {
                        failed++
                        layers += IdentityRepairLayer(level, IdentityRepairLayerOutcome.WRITE_FAILED, null, current?.let { proofOf(it.source) })
                    }
                }
                IdentityProofWriteResult.MISSING ->
                    layers += IdentityRepairLayer(level, IdentityRepairLayerOutcome.MISSING)
                IdentityProofWriteResult.VERSION_CONFLICT -> {
                    failed++
                    layers += IdentityRepairLayer(level, IdentityRepairLayerOutcome.VERSION_CONFLICT)
                }
                IdentityProofWriteResult.FAILED -> {
                    failed++
                    layers += IdentityRepairLayer(level, IdentityRepairLayerOutcome.WRITE_FAILED)
                }
            }
        }
        val outcome = when {
            failed > 0 && written > 0 -> IdentityRepairOutcome.PARTIAL
            failed > 0 -> IdentityRepairOutcome.FAILED
            written > 0 -> IdentityRepairOutcome.REPAIRED
            else -> IdentityRepairOutcome.ALREADY_VERIFIED
        }
        return IdentityRepairResult(outcome, target, layers)
    }

    /** 层内/跨层/与 incoming 的 ORCID 或 OpenAlex 作者编号冲突（任一侧缺值不算冲突）。 */
    private fun authorIdsConflict(stored: List<Pair<ExpertIndexLevel, Map<String, Any?>>>, incoming: AuthorEmail): Boolean {
        val orcids = mutableSetOf<String>()
        val openAlex = mutableSetOf<String>()
        for ((_, source) in stored) {
            val proof = proofOf(source)
            listOfNotNull(
                externalId(source, "orcid"), proof?.orcid?.takeIf { it.isNotBlank() }
            ).forEach { orcids += it.lowercase() }
            listOfNotNull(
                externalId(source, "openAlexAuthorId"), proof?.openAlexAuthorId?.takeIf { it.isNotBlank() }
            ).forEach { openAlex += it.lowercase() }
        }
        if (orcids.size > 1 || openAlex.size > 1) return true
        if (conflicting(orcids.firstOrNull(), incoming.orcidId)) return true
        if (conflicting(openAlex.firstOrNull(), incoming.openAlexAuthorId)) return true
        return false
    }

    private fun conflicting(stored: String?, incoming: String?): Boolean {
        val left = stored?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val right = incoming?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return !left.equals(right, ignoreCase = true)
    }

    /**
     * 05（I-1）：补证成功（实写或本就已证）后触发一次人物关系刷新。只对 [IdentityRepairOutcome.REPAIRED] /
     * [IdentityRepairOutcome.ALREADY_VERIFIED] 触发；缺名/冲突/部分/失败不抢先建立或改写关系。
     * 返回 null 表示未触发（无接缝或非成功结论）。
     */
    private fun refreshRegistration(docId: String, outcome: IdentityRepairOutcome): DiscoveryPersonRefreshResult? =
        when (outcome) {
            IdentityRepairOutcome.REPAIRED, IdentityRepairOutcome.ALREADY_VERIFIED ->
                personRegistrationService?.refresh(PERSON_SOURCE_REPAIR, docId)
            else -> null
        }

    /** proof 存在即返回（含无效摘要默认对象）；键缺失或为 null 视为没有 proof。 */
    private fun proofOf(source: Map<String, Any?>): IdentityVerification? {
        val node: JsonNode = source["identityVerification"]?.let { mapper.valueToTree(it) } ?: return null
        return DiscoveryIdentity.read(node)
    }

    private fun externalId(source: Map<String, Any?>, key: String): String? {
        val raw = source["externalIds"]
        val map: Map<*, *> = when (raw) {
            is Map<*, *> -> raw
            is String -> try {
                mapper.readValue(raw, Map::class.java)
            } catch (_: Exception) {
                emptyMap<Any, Any>()
            }
            else -> emptyMap<Any, Any>()
        }
        return (map[key] as? String)?.takeIf { it.isNotBlank() }
    }
}

/** 05（I-1）：补证路径记入人物成员 `evidence_json` 的发现来源标签（不参与身份判定）。 */
private const val PERSON_SOURCE_REPAIR = "IDENTITY_REPAIR"

/** I-1/I-2/I-3：一次补证的总体结果；计数器以本枚举为准。 */
enum class IdentityRepairOutcome {
    REPAIRED,
    ALREADY_VERIFIED,
    MISSING_NAME_NEEDS_RECOVERY,
    CONFLICT,
    PARTIAL,
    FAILED
}

/** 单层处置：缺层不创建，已有 proof 不改，条件写必须回读匹配。 */
enum class IdentityRepairLayerOutcome {
    REPAIRED,
    ALREADY_VERIFIED,
    MISSING,
    VERSION_CONFLICT,
    WRITE_FAILED
}

/** 单层明细；[previousProof]/[currentProof] 是 02 报告消费的前后值，不用于任何转发决策。 */
data class IdentityRepairLayer(
    val level: ExpertIndexLevel,
    val outcome: IdentityRepairLayerOutcome,
    val previousProof: IdentityVerification? = null,
    val currentProof: IdentityVerification? = null
)

/**
 * 补证结果。[proof] 是本次尝试写入的目标 proof（未尝试时为 null）；[layers] 保留逐层前后值。
 * [retryableFailure] 非空表示消费方必须保留可重试错误，不得推进为修复成功。
 */
data class IdentityRepairResult(
    val outcome: IdentityRepairOutcome,
    val proof: IdentityVerification? = null,
    val layers: List<IdentityRepairLayer> = emptyList(),
    /**
     * 05（I-1）：补证成功后触发的人物登记刷新结果（仅 REPAIRED/ALREADY_VERIFIED 时非空）。
     * 它不改变补证结论；调用方据它区分「关系已建立/追加」与「需复核/范围冲突/可重试登记失败」。
     */
    val personRegistration: DiscoveryPersonRefreshResult? = null
) {
    val retryableFailure: Boolean
        get() = outcome == IdentityRepairOutcome.PARTIAL || outcome == IdentityRepairOutcome.FAILED
}

/**
 * 05（I-1/I-2/I-3）：发现事实持久化后的**统一人物登记刷新接缝**。
 *
 * 由发现的四条成功出口（新建、重复、队列消费、同步 ORCID）与 01/02 的补证/恢复复用，是
 * 「同一事实 → 同一人物关系」的唯一调用点：所有事实都通过 [ExpertIndexWriterService] 从
 * **当前持久化档案**重读，登记参数（含指纹）由 04 的 [DiscoveryPersonIdentity] 单源签发，
 * 绝不从无凭证 externalIds 猜身份，也不据同名归组。
 *
 * 刷新只对**实时 discovered 成员**生效：非 discovered、无档案的 docId 直接 SKIPPED；已有关系的
 * 整组会一并重新核实（指纹变化返回 STALE，不借旧关系通过；跨根连接返回 NEEDS_REVIEW），
 * 这保证「不抢先合并、冲突保持独立」由 04 服务端复验落实。
 */
@Service
class DiscoveryPersonRegistrationService(
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val personService: DiscoveryPersonService,
    private val personRepository: DiscoveryPersonRepository
) {
    private val log = LoggerFactory.getLogger(DiscoveryPersonRegistrationService::class.java)

    private val layers = listOf(ExpertIndexLevel.RAW, ExpertIndexLevel.CANDIDATE, ExpertIndexLevel.APPLICATION)

    /**
     * 对 `docId` 刷新一次人物登记。返回的 [DiscoveryPersonRefreshResult] 是**只读结论**，绝不抛出；
     * 档案读失败按 STALE、登记异常按 FAILED（均可重试），非 discovered/无档案按 SKIPPED（非失败）。
     */
    fun refresh(source: String, docId: String): DiscoveryPersonRefreshResult {
        if (docId.isBlank()) return DiscoveryPersonRefreshResult(DiscoveryPersonRefreshStatus.SKIPPED, "DOC_ID_BLANK")
        val members = try {
            buildMembers(docId)
        } catch (e: Exception) {
            log.warn("人物登记刷新读取档案失败 docId={}: {}", docId, e.message)
            return DiscoveryPersonRefreshResult(DiscoveryPersonRefreshStatus.STALE, "SOURCE_UNAVAILABLE")
        } ?: return DiscoveryPersonRefreshResult(DiscoveryPersonRefreshStatus.SKIPPED, "NOT_DISCOVERY")
        val result = try {
            personService.register(DiscoveryPersonSnapshot(source, members))
        } catch (e: Exception) {
            log.warn("人物登记刷新失败 docId={}: {}", docId, e.message)
            return DiscoveryPersonRefreshResult(DiscoveryPersonRefreshStatus.FAILED, "REGISTRATION_EXCEPTION")
        }
        return when (result.outcome) {
            DiscoveryPersonOutcome.CREATED -> DiscoveryPersonRefreshResult(DiscoveryPersonRefreshStatus.CREATED, result.personId)
            DiscoveryPersonOutcome.ATTACHED -> DiscoveryPersonRefreshResult(DiscoveryPersonRefreshStatus.ATTACHED, result.personId)
            DiscoveryPersonOutcome.UNCHANGED -> DiscoveryPersonRefreshResult(DiscoveryPersonRefreshStatus.UNCHANGED, result.personId)
            DiscoveryPersonOutcome.NEEDS_REVIEW -> DiscoveryPersonRefreshResult(DiscoveryPersonRefreshStatus.NEEDS_REVIEW, result.reason)
            DiscoveryPersonOutcome.SCOPE_CONFLICT -> DiscoveryPersonRefreshResult(DiscoveryPersonRefreshStatus.SCOPE_CONFLICT, result.reason)
            DiscoveryPersonOutcome.STALE -> DiscoveryPersonRefreshResult(DiscoveryPersonRefreshStatus.STALE, result.reason)
        }
    }

    /**
     * 构造登记快照：目标 docId 已在注册表中时，把整组现存成员一并带上（复验完整组）；
     * 未注册或跨多个根时只带目标成员（跨根由 04 复验为 NEEDS_REVIEW）。
     */
    private fun buildMembers(docId: String): List<DiscoveryPersonMemberSnapshot>? {
        val target = facts(docId) ?: return null
        if (!target.discovered) return null
        val related = LinkedHashSet<String>()
        related += personRepository.findPersonIdsByDocIds(listOf(docId)).values
        if (target.authorKeys.isNotEmpty()) {
            related += personRepository.findPersonIdsByAuthorKeys(target.authorKeys).values
        }
        val groupDocIds = if (related.size == 1) {
            personRepository.findMembersByPerson(related.first()).map { it.expertDocId }
        } else {
            emptyList()
        }
        val ids = (groupDocIds + docId).distinct()
        val members = ids.mapNotNull { id ->
            if (id == docId) {
                DiscoveryPersonMemberSnapshot(docId, target.fingerprint)
            } else {
                facts(id)?.takeIf { it.discovered }?.let { DiscoveryPersonMemberSnapshot(id, it.fingerprint) }
            }
        }
        return members.ifEmpty { listOf(DiscoveryPersonMemberSnapshot(docId, target.fingerprint)) }
    }

    private class Facts(val discovered: Boolean, val fingerprint: String, val authorKeys: List<String>)

    /** 从**当前持久化档案**重读并派生指纹（与 04 服务端核实同源：docId + 规范邮箱 + 全名 + 作者键）。 */
    private fun facts(docId: String): Facts? {
        val snapshots = LinkedHashMap<ExpertIndexLevel, ExpertIndexWriterService.DiscoverySnapshot>()
        for (level in layers) {
            val document = expertIndexWriterService.readDiscoveryDocument(level, docId) ?: continue
            snapshots[level] = document
        }
        if (snapshots.isEmpty()) return null
        val raw = snapshots[ExpertIndexLevel.RAW] ?: snapshots.values.first()
        val profiles = snapshots.values.map { expertIndexWriterService.discoveryProfile(docId, it.source) }
        val rawProfile = expertIndexWriterService.discoveryProfile(docId, raw.source)
        val normalizedEmail = DiscoveryIdentity.normalizedEmail(rawProfile.email).takeIf { it.isNotEmpty() }
        val authorKeys = DiscoveryPersonIdentity.authorKeys(profiles)
        val fingerprint = DiscoveryPersonIdentity.fingerprint(
            docId, normalizedEmail, rawProfile.givenNames, rawProfile.familyNames, authorKeys
        )
        return Facts(DiscoveryIdentity.isDiscoveryMap(raw.source), fingerprint, authorKeys)
    }
}

/** 05（I-3）：一次人物登记刷新的判定；SKIPPED 不是失败（非 discovered/无档案）。 */
enum class DiscoveryPersonRefreshStatus { CREATED, ATTACHED, UNCHANGED, NEEDS_REVIEW, SCOPE_CONFLICT, STALE, FAILED, SKIPPED }

/** 05（I-3）：登记刷新结果。[reason] 是判定说明或 personId。 */
data class DiscoveryPersonRefreshResult(
    val status: DiscoveryPersonRefreshStatus,
    val reason: String? = null
) {
    val created: Boolean get() = status == DiscoveryPersonRefreshStatus.CREATED
    val attached: Boolean get() = status == DiscoveryPersonRefreshStatus.ATTACHED
    val needsReview: Boolean get() = status == DiscoveryPersonRefreshStatus.NEEDS_REVIEW
    val scopeConflict: Boolean get() = status == DiscoveryPersonRefreshStatus.SCOPE_CONFLICT
    val skipped: Boolean get() = status == DiscoveryPersonRefreshStatus.SKIPPED

    /** 可重试登记失败：STALE（读档案与登记之间事实/指纹变化）与 FAILED（基础设施异常）。 */
    val retryable: Boolean
        get() = status == DiscoveryPersonRefreshStatus.STALE || status == DiscoveryPersonRefreshStatus.FAILED
}
