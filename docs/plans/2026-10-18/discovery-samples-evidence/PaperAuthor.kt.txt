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
    val openAlexAuthorId: String? = null,
    /**
     * I-1/I-2：同一 authorship 唯一非空机构对象的 `display_name`；[affiliation] 只是原文线索。
     * 无唯一结构机构时为 null。
     */
    val institutionName: String? = null,
    /** I-2：同一个结构机构明确且能被地区表识别的 `country_code`；否则 null。 */
    val institutionCountry: String? = null,
    /** I-2：[INSTITUTION_SOURCE_OPENALEX] —— 只有结构化 OpenAlex 解析成功才非空。 */
    val institutionSource: String? = null
)
