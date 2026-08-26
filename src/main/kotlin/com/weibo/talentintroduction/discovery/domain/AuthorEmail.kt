package com.weibo.talentintroduction.discovery.domain

data class AuthorEmail(
    val email: String,
    val givenNames: String?,
    val familyNames: String?,
    val isCorresponding: Boolean,
    val affiliation: String?,
    val orcidId: String?,
    val institutionType: String? = null
)
