package com.weibo.talentintroduction.discovery.domain

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import java.text.Normalizer
import java.time.LocalDateTime
import java.util.Locale

/**
 * 04（I-1）：人物主档。`personId` 是不带邮箱语义的稳定 UUID，更新只由
 * [com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepository] 的关系写路径产生。
 * `primaryDocId` 是**代表档案引用**（首次登记固定），不复制任何准入/邮箱验证结论。
 */
data class DiscoveryPersonRecord(
    val personId: String,
    val primaryDocId: String,
    val revision: Long,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

/**
 * 04（I-1）：邮箱成员 —— 一个原 docId 只能隶属一个人物（`expert_doc_id` 主键）。
 * [normalizedEmail] 只是展示默认值，不是发送许可；[identityFingerprint] 是登记当时
 * 证据（docId + 规范邮箱 + 规范全名 + 作者键）的摘要，读取方（06/08）使用前必须与当前
 * 档案事实重核，指纹变化即失效待重核。
 */
data class DiscoveryPersonMemberRecord(
    val expertDocId: String,
    val personId: String,
    val normalizedEmail: String?,
    val identityFingerprint: String,
    val evidenceJson: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

/** 04（I-2）：可信作者键，带 `ORCID:` / `OPENALEX:` 类型前缀；每键全局唯一。 */
data class DiscoveryPersonAuthorKeyRecord(
    val authorKey: String,
    val personId: String
)

/** 04：登记写路径的成员事实（不含时间戳，由仓储写入）。 */
data class DiscoveryPersonMemberWrite(
    val expertDocId: String,
    val normalizedEmail: String?,
    val identityFingerprint: String,
    val evidenceJson: String?
)

/**
 * 04：登记结果枚举。[NEEDS_REVIEW] / [SCOPE_CONFLICT] / [STALE] **不写任何关系**，
 * 只把判定交回调用方（05 复用、10 只读复用、06/08 只用读查询）。
 */
enum class DiscoveryPersonOutcome { CREATED, ATTACHED, UNCHANGED, NEEDS_REVIEW, SCOPE_CONFLICT, STALE }

/** 04（I-3）：调用方声明的一条成员事实快照；服务端逐字段重新核实（不接受客户端结论）。 */
data class DiscoveryPersonMemberSnapshot(
    val docId: String,
    val evidenceFingerprint: String
)

/**
 * 04（I-3）：调用方声明的「同一人物候选」事实快照。[source] 是调用方记录的发现来源，
 * 服务端核实档案当前仍是 discovered 记录后原样留存进 `evidence_json`；本快照不是授权，
 * 服务端只核实、不改写。
 */
data class DiscoveryPersonSnapshot(
    val source: String,
    val members: List<DiscoveryPersonMemberSnapshot>
)

/** 04：服务端核实后的成员事实（`authorKeys` 为空表示不创建作者键的单例成员）。 */
data class DiscoveryPersonVerifiedMember(
    val docId: String,
    val businessKey: String,
    val normalizedEmail: String?,
    val givenNames: String?,
    val familyNames: String?,
    val authorKeys: List<String>,
    val identityFingerprint: String,
    val evidenceJson: String?
) {
    fun write() = DiscoveryPersonMemberWrite(
        expertDocId = docId,
        normalizedEmail = normalizedEmail,
        identityFingerprint = identityFingerprint,
        evidenceJson = evidenceJson
    )
}

/**
 * 04：登记结果。[personId] 仅在 [DiscoveryPersonOutcome.CREATED] / [DiscoveryPersonOutcome.ATTACHED] /
 * [DiscoveryPersonOutcome.UNCHANGED] 时非空；[memberCount] 是该人物当前的成员数。
 */
data class DiscoveryPersonRegistrationResult(
    val outcome: DiscoveryPersonOutcome,
    val personId: String? = null,
    val memberCount: Int = 0,
    val reason: String? = null
)

/**
 * 04/10（I-3）：人物链专属的**实时发现范围**判据。
 *
 * 与既有宽口径 [DiscoveryIdentity.isDiscoveryMap]（proof / 来源 / 标签**任一**成立）不同：人物登记、
 * 补证、恢复、维护与展示都要求「发现来源证据 **且** 字面 `discovered` 标签」同时成立。二者缺一即
 * 不是当前人物范围：旧关系立即失效（待重核），不得借保留的历史 proof/来源继续沿用人物语义。
 *
 * 该判据只收敛人物链，不改既有准入 `isDiscoveryOutreach` 的宽口径，也不改 01/02 的身份门槛。
 */
object DiscoveryPersonScope {
    const val DISCOVERED_TAG = "discovered"

    /** 与 [DiscoveryIdentity] 的发现来源词表同值（`PAPER_FULLTEXT` / `ORCID_PUBLIC`）。 */
    private val DISCOVERY_SOURCES = listOf("PAPER_FULLTEXT", "ORCID_PUBLIC")

    /** 档案版判据：发现来源证据（身份凭证或发现来源标记）与字面标签必须同时成立。 */
    fun isLiveDiscovered(profile: ExpertProfile): Boolean =
        hasDiscoverySource(profile.identityVerification != null, profile.emailSource) &&
            profile.tags.orEmpty().contains(DISCOVERED_TAG)

    /** ES 文档 `_source` 版判据：与 [isLiveDiscovered] 同一条规则，供服务端核实只读重算。 */
    fun isLiveDiscovered(source: Map<String, Any?>): Boolean =
        hasDiscoverySource(source["identityVerification"] != null, source["emailSource"] as? String) &&
            (source["tags"] as? Collection<*>)?.any { it == DISCOVERED_TAG } == true

    private fun hasDiscoverySource(hasProof: Boolean, emailSource: String?): Boolean =
        hasProof || emailSource in DISCOVERY_SOURCES
}

/**
 * 04（I-2）：可信作者键与成员指纹的**唯一签发点**（服务端登记与 05 调用方共用同一实现，
 * 保证「同一事实 → 同一键/同一指纹」）。
 *
 * 只有同时满足下面全部条件才产出作者键：
 *  1. 每一层档案都通过 [DiscoveryIdentity.allowed]（凭证 VERIFIED/版本/邮箱/姓名/证据摘要与当前档案一致）；
 *  2. 凭证内 ORCID/OpenAlex 作者 ID 与 `externalIds`/业务主键不冲突；
 *  3. 每类作者键最多一个（多 ORCID / 多 OpenAlex ⇒ 不可归入）；
 *  4. 层间身份一致（规范邮箱与规范全名在各层相同）且规范全名非空。
 * 任一条不成立即返回空表：**不创建作者键、不增加身份可信度或准入，不据同名归组**。
 */
object DiscoveryPersonIdentity {
    const val ORCID_PREFIX = "ORCID:"
    const val OPENALEX_PREFIX = "OPENALEX:"

    private val mapper = jacksonObjectMapper()
    private const val EMAIL_KEY_PREFIX = "EMAIL-"
    private val WHITESPACE = Regex("\\s+")

    /** 全名规范化：NFKC + 小写 + 折叠空白；只用于一致性比较与指纹，不改写档案。 */
    fun normalizedFullName(givenNames: String?, familyNames: String?): String =
        Normalizer.normalize(
            listOf(givenNames.orEmpty(), familyNames.orEmpty()).joinToString(" ").trim(),
            Normalizer.Form.NFKC
        ).lowercase(Locale.ROOT).replace(WHITESPACE, " ").trim()

    /**
     * 服务端核实的作者键（已排序）。[profiles] 是同一 docId 在当前各存在层上的档案；
     * 只读 [ExpertProfile]，绝不触发任何 ES 写。
     */
    fun authorKeys(profiles: List<ExpertProfile>): List<String> {
        if (profiles.isEmpty()) return emptyList()
        if (profiles.any { !DiscoveryIdentity.allowed(it) }) return emptyList()
        val proofs = profiles.mapNotNull { it.identityVerification }
        if (proofs.size != profiles.size) return emptyList()
        if (profiles.any { !idsConsistent(it) }) return emptyList()

        val orcids = proofs.mapNotNull { canonicalId(it.orcid) }.distinctBy { it.lowercase(Locale.ROOT) }
        val openAlex = proofs.mapNotNull { canonicalId(it.openAlexAuthorId) }.distinctBy { it.lowercase(Locale.ROOT) }
        if (orcids.size > 1 || openAlex.size > 1) return emptyList()

        val emails = profiles.map { DiscoveryIdentity.normalizedEmail(it.email) }.distinct()
        val names = profiles.map { normalizedFullName(it.givenNames, it.familyNames) }.distinct()
        if (emails.size > 1 || names.size > 1) return emptyList()
        if (names.single().isEmpty()) return emptyList()

        return buildList {
            orcids.firstOrNull()?.let { add(ORCID_PREFIX + it) }
            openAlex.firstOrNull()?.let { add(OPENALEX_PREFIX + it) }
        }.sorted()
    }

    /** 成员指纹：docId + 规范邮箱 + 规范全名 + 排序后的作者键。同一事实永远同一摘要。 */
    fun fingerprint(
        docId: String,
        normalizedEmail: String?,
        givenNames: String?,
        familyNames: String?,
        authorKeys: List<String>
    ): String = DiscoveryIdentity.hash(
        listOf(
            docId,
            normalizedEmail.orEmpty(),
            normalizedFullName(givenNames, familyNames),
            authorKeys.sorted().joinToString(",")
        ).joinToString("\u0000")
    )

    /** 去掉 URL 前缀/空白，不改写大小写与数字语义。 */
    fun canonicalId(raw: String?): String? = raw?.trim()
        ?.removePrefix("https://orcid.org/")?.removePrefix("http://orcid.org/")
        ?.removePrefix("https://openalex.org/")?.removePrefix("http://openalex.org/")
        ?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * 凭证内非空作者 ID 与 `externalIds` 对应值 / 业务主键（非 `EMAIL-*`）不得冲突；
     * 任一侧缺值不算冲突。与 [DiscoveryIdentity] 的机构证据前置同判据，本处只读重算。
     */
    private fun idsConsistent(profile: ExpertProfile): Boolean {
        val proof = profile.identityVerification ?: return false
        val ids = parsedExternalIds(profile.externalIds)
        val business = profile.orcidId.takeIf { it.isNotBlank() && !it.startsWith(EMAIL_KEY_PREFIX) }
        return !conflicts(proof.orcid, ids["orcid"]) &&
            !conflicts(proof.openAlexAuthorId, ids["openAlexAuthorId"]) &&
            !conflicts(business, ids["orcid"]) &&
            !conflicts(business, proof.orcid)
    }

    private fun conflicts(stored: String?, incoming: String?): Boolean {
        val left = canonicalId(stored) ?: return false
        val right = canonicalId(incoming) ?: return false
        return !left.equals(right, ignoreCase = true)
    }

    private fun parsedExternalIds(externalIds: String?): Map<String, String> {
        if (externalIds.isNullOrBlank()) return emptyMap()
        return try {
            val node = mapper.readTree(externalIds)
            if (!node.isObject) emptyMap()
            else node.fields().asSequence()
                .filter { it.value.isTextual && it.value.asText().isNotBlank() }
                .associate { it.key to it.value.asText() }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
