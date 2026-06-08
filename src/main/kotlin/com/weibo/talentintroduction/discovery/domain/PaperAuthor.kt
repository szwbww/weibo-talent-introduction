package com.weibo.talentintroduction.discovery.domain

data class PaperAuthor(
    val givenNames: String?,
    val familyNames: String?,
    val orcidId: String?,
    val affiliation: String?,
    val isCorresponding: Boolean = false,
    val email: String? = null
)
