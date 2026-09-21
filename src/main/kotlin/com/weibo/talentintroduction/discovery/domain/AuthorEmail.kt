package com.weibo.talentintroduction.discovery.domain

data class AuthorEmail @JvmOverloads constructor(
    val email: String,
    val givenNames: String?,
    val familyNames: String?,
    val isCorresponding: Boolean,
    val affiliation: String?,
    val orcidId: String?,
    val institutionType: String? = null,
    /**
     * I-1：随邮箱一起传播的可信作者 ID（规范形式 `A` + 数字），最终落到
     * `externalIds.openAlexAuthorId`。邮箱线索与学术身份的绑定必须唯一且强（见 I-2）；
     * 该字段永远不参与专家主键。
     */
    val openAlexAuthorId: String? = null
)
