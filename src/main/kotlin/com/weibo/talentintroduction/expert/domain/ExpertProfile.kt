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
    val institutionType: String? = null
) {
    val displayName: String
        get() = listOfNotNull(givenNames, familyNames)
            .joinToString(" ")
            .ifBlank { orcidId }
}
