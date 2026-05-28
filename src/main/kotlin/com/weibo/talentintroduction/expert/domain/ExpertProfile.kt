package com.weibo.talentintroduction.expert.domain

data class ExpertProfile(
    val orcidId: String,
    val email: String?,
    val givenNames: String?,
    val familyNames: String?,
    val country: String?,
    val keyword: String?,
    val employment: String?,
    val age: Int? = null,
    val degree: String? = null,
    val nationality: String? = null
) {
    val displayName: String
        get() = listOfNotNull(givenNames, familyNames)
            .joinToString(" ")
            .ifBlank { orcidId }
}
