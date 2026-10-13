package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
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
    private val expertIndexWriterService: ExpertIndexWriterService
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
    val layers: List<IdentityRepairLayer> = emptyList()
) {
    val retryableFailure: Boolean
        get() = outcome == IdentityRepairOutcome.PARTIAL || outcome == IdentityRepairOutcome.FAILED
}
