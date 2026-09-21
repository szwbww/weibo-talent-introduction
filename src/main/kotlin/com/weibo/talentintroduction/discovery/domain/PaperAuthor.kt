package com.weibo.talentintroduction.discovery.domain

data class PaperAuthor @JvmOverloads constructor(
    val givenNames: String?,
    val familyNames: String?,
    val orcidId: String?,
    val affiliation: String?,
    val isCorresponding: Boolean = false,
    val email: String? = null,
    val institutionType: String? = null,
    /**
     * I-1: 该署名作者在数据源里的作者 ID（OpenAlex 规范形式 = `A` + 数字）。
     * 它是**学术身份**，不是专家主键：ES `_id` 与 `orcidId` 的历史语义完全不变。
     * 只有原始结构化归属或唯一强证据才允许把它传播到 [AuthorEmail]。
     */
    val openAlexAuthorId: String? = null
)
