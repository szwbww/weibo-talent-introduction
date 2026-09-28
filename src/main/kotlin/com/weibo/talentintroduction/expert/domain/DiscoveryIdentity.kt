package com.weibo.talentintroduction.expert.domain

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.security.MessageDigest
import java.util.Locale

/** A proof is bound to the identity it verified, never to a mutable business document ID. */
data class IdentityVerification(
    val status: String = "UNRESOLVED",
    val version: Int = 0,
    val email: String? = null,
    val givenNames: String? = null,
    val familyNames: String? = null,
    val source: String? = null,
    val evidenceHash: String? = null,
    val orcid: String? = null,
    val openAlexAuthorId: String? = null
)

object DiscoveryIdentity {
    const val VERSION = 20260925
    /** Cached extractor output compatibility; independent of persisted proof versions. */
    const val EXTRACTION_VERSION = 20261002
    private val mapper = jacksonObjectMapper()
    private val sources = listOf("PAPER_FULLTEXT", "ORCID_PUBLIC")
    fun normalizedEmail(email: String?) = email.orEmpty().trim().lowercase(Locale.ROOT)
    fun hash(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    fun isDiscovery(profile: ExpertProfile) = profile.identityVerification != null ||
        profile.emailSource in sources || profile.tags.orEmpty().contains("discovered")
    fun validEvidence(evidence: String?): Boolean = evidence != null &&
        evidence.matches(Regex("(?:JATS_SHA256|ORCID_RECORD_SHA256|SOURCE_SHA256):[0-9a-f]{64}"))
    fun verified(email: String, given: String?, family: String?, evidence: String, orcid: String?, authorId: String?) =
        IdentityVerification("VERIFIED", VERSION, normalizedEmail(email), given, family,
            evidence.substringBefore(':'), evidence.substringAfter(':'), orcid, authorId)

    fun isDiscoveryMap(source: Map<String, Any?>): Boolean = source["identityVerification"] != null ||
        source["emailSource"] in sources || (source["tags"] as? Collection<*>)?.contains("discovered") == true

    fun isDiscoverySource(source: JsonNode): Boolean =
        (!source.path("identityVerification").isMissingNode && !source.path("identityVerification").isNull) ||
            source.path("emailSource").asText(null) in sources ||
            source.path("tags").takeIf { it.isArray }?.any { it.asText() == "discovered" } == true

    /** Bound academic identity only; admission, outreach and promotion do not require this proof. */
    fun allowed(profile: ExpertProfile): Boolean {
        if (!isDiscovery(profile)) return true
        val proof = profile.identityVerification ?: return false
        return proof.status == "VERIFIED" && proof.version == VERSION &&
            normalizedEmail(proof.email).isNotEmpty() && normalizedEmail(proof.email) == normalizedEmail(profile.email) &&
            !proof.givenNames.isNullOrBlank() && !proof.familyNames.isNullOrBlank() &&
            proof.givenNames == profile.givenNames && proof.familyNames == profile.familyNames &&
            (validEvidence("${proof.source}:${proof.evidenceHash}") ||
                (proof.source == "REVIEWED_SOURCE_SHA256" && proof.evidenceHash.orEmpty().matches(Regex("[0-9a-f]{64}"))))
    }
    fun read(node: JsonNode): IdentityVerification? {
        if (!node.isObject) return null
        return try { mapper.treeToValue(node, IdentityVerification::class.java) } catch (_: Exception) {
            IdentityVerification() // malformed presence must remain a discovery identity, never a legacy bypass
        }
    }
    fun allowedSource(source: JsonNode): Boolean = allowed(ExpertProfile(
        orcidId = source.path("orcidId").asText(""), email = source.path("email").asText(null),
        givenNames = source.path("givenNames").asText(null), familyNames = source.path("familyNames").asText(null),
        country = null, keyword = null, employment = null, emailSource = source.path("emailSource").asText(null),
        tags = source.path("tags").takeIf { it.isArray }?.map { it.asText() },
        identityVerification = read(source.path("identityVerification"))
    ))
    fun allowedMap(source: Map<String, Any?>): Boolean = allowedSource(mapper.valueToTree(source))

    // ── 02（I-1/I-2）：机构来源证据 token ──────────────────────────────────────
    // 一个字段把「已存身份 + 来源种类 + 显示机构」绑成可重算的校验值：签发与验签共用下面
    // 同一份 NUL 分隔、固定顺序的输入；机构/国家/类型或身份 ID 一变，旧 token 立即失效。

    /**
     * 证据 token 的来源种类词表；取值与 01 的内部 `institutionSource` 逐字一致。
     * `ORCID` 不来自 `institutionSource`（ORCID 路径没有该字段），只能由 01b 的「唯一机构」判定结果驱动。
     */
    const val EVIDENCE_SOURCE_JATS = "JATS"
    const val EVIDENCE_SOURCE_OPENALEX = "OPENALEX"
    const val EVIDENCE_SOURCE_ORCID = "ORCID"
    private val EVIDENCE_SOURCES = setOf(EVIDENCE_SOURCE_JATS, EVIDENCE_SOURCE_OPENALEX, EVIDENCE_SOURCE_ORCID)

    /** 无 ORCID 的专家主键前缀（与发现写入侧同值）：它不是真实 ORCID，不参与「主键 = ORCID 值」检查。 */
    private const val EMAIL_KEY_PREFIX = "EMAIL-"

    /**
     * 唯一签发函数（02）：返回 null 表示**不写这个键**（绝不写 `false`/`UNVERIFIED`）。
     * 只有身份凭证成立、来源身份与 `externalIds`/主键无冲突、该来源必需的来源 ID 齐备、机构非空时才产出
     * `来源种类:<64位小写SHA256>`。输入顺序即 token 版本：见 [tokenDigestInput]。
     */
    fun institutionEvidence(profile: ExpertProfile, source: String): String? {
        if (source !in EVIDENCE_SOURCES) return null
        if (!allowed(profile)) return null
        val proof = profile.identityVerification ?: return null
        val ids = parsedExternalIds(profile.externalIds)
        if (!consistentIdentityIds(profile, proof, ids)) return null
        val institution = profile.institution?.takeIf { it.isNotBlank() } ?: return null
        // JATS 必须有 externalIds.pmcId；OPENALEX 必须有同作者 openAlexAuthorId（且 doi/pmcId 至少一项）；
        // ORCID 必须有 externalIds.orcid。
        val sourceAuthorId = when (source) {
            EVIDENCE_SOURCE_OPENALEX -> ids["openAlexAuthorId"]?.takeIf { it.isNotBlank() } ?: return null
            EVIDENCE_SOURCE_ORCID -> ids["orcid"]?.takeIf { it.isNotBlank() } ?: return null
            else -> ids["orcid"].orEmpty()
        }
        if (source == EVIDENCE_SOURCE_JATS && ids["pmcId"].isNullOrBlank()) return null
        if (source == EVIDENCE_SOURCE_OPENALEX && ids["doi"].isNullOrBlank() && ids["pmcId"].isNullOrBlank()) return null
        return "$source:${hash(tokenDigestInput(source, profile, proof, ids, sourceAuthorId, institution).joinToString("\u0000"))}"
    }

    /**
     * 唯一验签函数（02）：**只读已存字段重算**（来源种类取自 token 前缀，不依赖任何本次审计备份）。
     * 旧文档没有该键 → false；机构/国家/类型或身份 ID 与签发时不一致 → false。
     */
    fun validInstitutionEvidence(profile: ExpertProfile): Boolean {
        val token = profile.institutionEvidence ?: return false
        val source = token.substringBefore(':')
        if (source !in EVIDENCE_SOURCES) return false
        return token == institutionEvidence(profile, source)
    }

    /** token 的固定输入顺序（NUL 分隔）：来源种类、凭证 source/evidenceHash、规范邮箱、姓名、来源作者 ID、论文/ORCID ID、机构、国家、机构类型。 */
    private fun tokenDigestInput(
        source: String,
        profile: ExpertProfile,
        proof: IdentityVerification,
        ids: Map<String, String>,
        sourceAuthorId: String,
        institution: String
    ): List<String> = listOf(
        source,
        proof.source.orEmpty(),
        proof.evidenceHash.orEmpty(),
        normalizedEmail(profile.email),
        "${profile.givenNames.orEmpty()} ${profile.familyNames.orEmpty()}",
        sourceAuthorId,
        sourcePaperOrOrcidId(ids, source),
        institution,
        profile.country.orEmpty(),
        profile.institutionType.orEmpty()
    )

    /** `已存 externalIds 中论文 ID 或 ORCID ID`：论文来源取论文 ID（pmcId→doi→pmid），ORCID 来源取 ORCID ID。 */
    private fun sourcePaperOrOrcidId(ids: Map<String, String>, source: String): String = when {
        source == EVIDENCE_SOURCE_ORCID -> ids["orcid"].orEmpty()
        !ids["pmcId"].isNullOrBlank() -> ids["pmcId"]!!
        !ids["doi"].isNullOrBlank() -> ids["doi"]!!
        else -> ids["pmid"].orEmpty()
    }

    /**
     * I-2 的签发前置：`identityVerification` 中非空的 ORCID/OpenAlex 作者 ID 与 `externalIds` 对应 ID 不得冲突；
     * 真实 ORCID 主键（非 `EMAIL-*`）不得与其 ORCID 值冲突（线上 1,256 条反例即此形状）。任一侧缺值不算冲突。
     */
    private fun consistentIdentityIds(
        profile: ExpertProfile,
        proof: IdentityVerification,
        ids: Map<String, String>
    ): Boolean {
        val orcidPrimaryKey = profile.orcidId.takeIf { it.isNotBlank() && !it.startsWith(EMAIL_KEY_PREFIX) }
        return !conflictingId(proof.orcid, ids["orcid"]) &&
            !conflictingId(proof.openAlexAuthorId, ids["openAlexAuthorId"]) &&
            !conflictingId(orcidPrimaryKey, ids["orcid"]) &&
            !conflictingId(orcidPrimaryKey, proof.orcid)
    }

    private fun conflictingId(stored: String?, incoming: String?): Boolean {
        val left = canonicalId(stored) ?: return false
        val right = canonicalId(incoming) ?: return false
        return !left.equals(right, ignoreCase = true)
    }

    /** 只做形状归一（去 URL 前缀 / trim），不改写已存值的语义。 */
    private fun canonicalId(raw: String?): String? = raw?.trim()
        ?.removePrefix("https://orcid.org/")?.removePrefix("http://orcid.org/")
        ?.removePrefix("https://openalex.org/")?.removePrefix("http://openalex.org/")
        ?.trim()?.takeIf { it.isNotEmpty() }

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
