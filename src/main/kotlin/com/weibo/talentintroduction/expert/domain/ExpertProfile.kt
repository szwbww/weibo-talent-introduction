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
    val expertClassification: ExpertClassification? = null
) {
    val displayName: String
        get() = listOfNotNull(givenNames, familyNames)
            .joinToString(" ")
            .ifBlank { orcidId }
}
