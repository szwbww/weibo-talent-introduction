package com.weibo.talentintroduction.discovery.domain

/**
 * I-2：结构机构来源标记 —— **只**由实际成功的 JATS / OpenAlex 结构解析写入，
 * 不得由 `dataSource`、邮箱域名或名称相似度猜测。
 */
internal const val INSTITUTION_SOURCE_JATS = "JATS"
internal const val INSTITUTION_SOURCE_OPENALEX = "OPENALEX"

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
    val openAlexAuthorId: String? = null,
    /** Explicit source ownership proof; null includes legacy and name-based guesses. */
    val identityEvidence: String? = null,
    /**
     * I-1/I-2：与该作者**唯一绑定**的结构机构名（JATS `content-type=university|edu` 的
     * `<institution>`，或同一 OpenAlex `authorship` 的唯一机构对象）。结构解析没有成功时一律 null；
     * [affiliation] 只是原文提取线索，绝不回填这个展示字段。
     */
    val institutionName: String? = null,
    /**
     * I-2：同一个结构机构明确给出、且能被
     * [com.weibo.talentintroduction.expert.domain.CountryContinentMapping] 识别的国家/代码；否则 null。
     */
    val institutionCountry: String? = null,
    /**
     * I-2：[INSTITUTION_SOURCE_JATS] / [INSTITUTION_SOURCE_OPENALEX]；
     * 无结构证据时 null（`dataSource` 不参与推断）。
     */
    val institutionSource: String? = null
)
