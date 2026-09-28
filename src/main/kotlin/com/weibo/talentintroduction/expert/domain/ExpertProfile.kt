package com.weibo.talentintroduction.expert.domain

data class ExpertProfile(
    val esDocId: String? = null,
    val orcidId: String,
    val email: String?,
    val givenNames: String?,
    val familyNames: String?,
    val country: String?,
    val keyword: String?,
    val employment: String?,
    val age: Int? = null,
    val degree: String? = null,
    val nationality: String? = null,
    val hIndex: Int? = null,
    val citationCount: Int? = null,
    val lastPublicationYear: Int? = null,
    val researchFields: String? = null,
    val disciplineCategory: String? = null,
    val institution: String? = null,
    val emailSource: String? = null,
    val emailVerifiedLevel: Int? = null,
    val dataSource: String? = null,
    val externalIds: String? = null,
    val worksCount: Int? = null,
    val tags: List<String>? = null,
    val updatedAt: String? = null,
    val operatorStatus: String? = null,
    val recentWorkTitles: List<String>? = null,
    val patentTitles: List<String>? = null,
    val enrichedAt: String? = null,
    val enrichmentSource: String? = null,
    val expertClassification: ExpertClassification? = null,
    /**
     * 机构类型（OpenAlex 枚举）。两条写入路径语义不同，不得假设与 [institution] 同源：
     * - works 路径（发现时）：该专家被发现的那篇论文上的署名机构的类型，与 institution / employment 同源。
     * - authors 路径（enrichment 时）：该作者的当前已知机构（last_known_institutions[0]）的类型，
     *   与 institution 很可能不是同一个机构（institution 永远停留在发现时的论文署名机构）。
     */
    val institutionType: String? = null,
    val identityVerification: IdentityVerification? = null,
    val researchFieldIds: List<String>? = null,
    /**
     * 02（I-1/I-3）：机构来源证据 token（`JATS:<64位小写SHA256>` / `OPENALEX:<...>` / `ORCID:<...>`）。
     * 签发与验签的唯一入口是 [DiscoveryIdentity.institutionEvidence] / [DiscoveryIdentity.validInstitutionEvidence]；
     * 旧文档没有这个键 → null（**不是**合格，缺字段绝不等于通过）。
     */
    val institutionEvidence: String? = null,
    /**
     * 02（I-3）：ES `filterResult` keyword 的读取投影。发送门禁要求新发现档案为 `PASSED`；
     * 旧文档没有这个键 → null，不默认合格。
     */
    val filterResult: String? = null
) {
    val displayName: String
        get() = listOfNotNull(givenNames, familyNames)
            .joinToString(" ")
            .ifBlank { orcidId }
}
