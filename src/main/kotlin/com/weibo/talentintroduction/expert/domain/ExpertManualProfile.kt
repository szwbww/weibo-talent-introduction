package com.weibo.talentintroduction.expert.domain

/**
 * 01（I-1–I-23）：人工档案的字段白名单、分组、枚举、DTO 与业务异常。
 *
 * 19 项人工资料是首次建立的私有业务聚合（`expert_manual_profile`），只由
 * `ExpertManualProfileService` 写入；本文件同时是 PATCH 分组键集（I-3）与 DDL 漂移核对的唯一权威，
 * 迁移测试按 [ExpertManualProfileField.column] 逐列核对真实库。
 */

/**
 * 单个字段元数据。
 *
 * - [apiName]：API JSON 键（02 前端逐字契约）；
 * - [column]：`expert_manual_profile` 列名（服务 SQL 与本字段白名单的对应关系由迁移测试核对）；
 * - [maxCodePoints]：上限，按 Unicode code point 计长（I-4），不静默截断；
 * - [multiline]：是否允许多行；单行列出现换行即拒绝（I-4）。
 */
data class ExpertManualProfileField(
    val apiName: String,
    val column: String,
    val maxCodePoints: Int,
    val multiline: Boolean
)

/** PATCH 分组（I-3）：只更新指定组的全部字段，拒绝额外键。 */
enum class ExpertManualProfileGroup(val wire: String, val fields: List<ExpertManualProfileField>) {
    IDENTITY(
        "identity",
        listOf(
            ExpertManualProfileFields.TALENT_CODE,
            ExpertManualProfileFields.CHINESE_NAME,
            ExpertManualProfileFields.BIRTH_DATE_TEXT,
            ExpertManualProfileFields.DOCUMENT_NUMBER,
            ExpertManualProfileFields.SOURCE_COUNTRY
        )
    ),
    EDUCATION(
        "education",
        listOf(
            ExpertManualProfileFields.GRADUATED_SCHOOL,
            ExpertManualProfileFields.SCHOOL_RANKING,
            ExpertManualProfileFields.PRE_CHINA_ORGANIZATION,
            ExpertManualProfileFields.ORGANIZATION_RANKING,
            ExpertManualProfileFields.POSTDOC_ORGANIZATION,
            ExpertManualProfileFields.POSTDOC_PERIOD,
            ExpertManualProfileFields.PROFESSIONAL_TITLE,
            ExpertManualProfileFields.ADMINISTRATIVE_TITLES
        )
    ),
    TECHNOLOGY(
        "technology",
        listOf(
            ExpertManualProfileFields.INDUSTRY,
            ExpertManualProfileFields.TECHNICAL_EXPERTISE,
            ExpertManualProfileFields.PROGRAM_CATEGORIES
        )
    ),
    MATCHING(
        "matching",
        listOf(
            ExpertManualProfileFields.RESEARCH_ABILITY,
            ExpertManualProfileFields.COMPANY_MATCH_STATUS,
            ExpertManualProfileFields.MATCHED_COMPANIES
        )
    );

    companion object {
        /** 未知分组返回 null，由调用方映射为 400；不接受大小写变体。 */
        fun fromWire(raw: String?): ExpertManualProfileGroup? = values().firstOrNull { it.wire == raw }
    }
}

/** I-21：科研能力六档；未评估由字段为 NULL 表达，不新增枚举值。 */
enum class ResearchAbility {
    VERY_STRONG,
    STRONG,
    ABOVE_AVERAGE,
    AVERAGE,
    FAIR,
    LOW
}

/** I-22：匹配企业进度；未匹配是默认值（NOT NULL 列，空值等价于未匹配）。 */
enum class CompanyMatchStatus {
    NOT_MATCHED,
    MATCHING,
    MATCHED
}

/** 19 项字段白名单（顺序即计划 §1 表格顺序）。 */
object ExpertManualProfileFields {
    val TALENT_CODE = ExpertManualProfileField("talentCode", "talent_code", 64, multiline = false)
    val CHINESE_NAME = ExpertManualProfileField("chineseName", "chinese_name", 200, multiline = false)
    val BIRTH_DATE_TEXT = ExpertManualProfileField("birthDateText", "birth_date_text", 10, multiline = false)
    val DOCUMENT_NUMBER = ExpertManualProfileField("documentNumber", "document_number", 100, multiline = false)
    val SOURCE_COUNTRY = ExpertManualProfileField("sourceCountry", "source_country", 100, multiline = false)
    val GRADUATED_SCHOOL = ExpertManualProfileField("graduatedSchool", "graduated_school", 2000, multiline = true)
    val SCHOOL_RANKING = ExpertManualProfileField("schoolRanking", "school_ranking", 500, multiline = false)
    val PRE_CHINA_ORGANIZATION =
        ExpertManualProfileField("preChinaOrganization", "pre_china_organization", 2000, multiline = true)
    val ORGANIZATION_RANKING =
        ExpertManualProfileField("organizationRanking", "organization_ranking", 500, multiline = false)
    val POSTDOC_ORGANIZATION =
        ExpertManualProfileField("postdocOrganization", "postdoc_organization", 2000, multiline = true)
    val POSTDOC_PERIOD = ExpertManualProfileField("postdocPeriod", "postdoc_period", 200, multiline = false)
    val PROFESSIONAL_TITLE = ExpertManualProfileField("professionalTitle", "professional_title", 200, multiline = false)
    val ADMINISTRATIVE_TITLES =
        ExpertManualProfileField("administrativeTitles", "administrative_titles", 4000, multiline = true)
    val INDUSTRY = ExpertManualProfileField("industry", "industry", 500, multiline = false)
    val TECHNICAL_EXPERTISE =
        ExpertManualProfileField("technicalExpertise", "technical_expertise", 4000, multiline = true)
    val PROGRAM_CATEGORIES = ExpertManualProfileField("programCategories", "program_categories", 500, multiline = false)
    val RESEARCH_ABILITY = ExpertManualProfileField("researchAbility", "research_ability", 16, multiline = false)
    val COMPANY_MATCH_STATUS =
        ExpertManualProfileField("companyMatchStatus", "company_match_status", 16, multiline = false)
    val MATCHED_COMPANIES = ExpertManualProfileField("matchedCompanies", "matched_companies", 2000, multiline = true)

    val ALL: List<ExpertManualProfileField> = listOf(
        TALENT_CODE, CHINESE_NAME, BIRTH_DATE_TEXT, DOCUMENT_NUMBER, SOURCE_COUNTRY,
        GRADUATED_SCHOOL, SCHOOL_RANKING, PRE_CHINA_ORGANIZATION, ORGANIZATION_RANKING,
        POSTDOC_ORGANIZATION, POSTDOC_PERIOD, PROFESSIONAL_TITLE, ADMINISTRATIVE_TITLES,
        INDUSTRY, TECHNICAL_EXPERTISE, PROGRAM_CATEGORIES,
        RESEARCH_ABILITY, COMPANY_MATCH_STATUS, MATCHED_COMPANIES
    )

    val BY_API_NAME: Map<String, ExpertManualProfileField> = ALL.associateBy { it.apiName }

    /**
     * 首次 INSERT 的完整默认值（I-3）：非本组业务列全部 NULL，只有匹配进度取默认 NOT_MATCHED；
     * 本组字段由本次 PATCH 覆盖。
     */
    val INSERT_DEFAULTS: Map<String, Any?> =
        ALL.associate { it.apiName to null } + (COMPANY_MATCH_STATUS.apiName to CompanyMatchStatus.NOT_MATCHED.name)
}

/** PATCH 命令（controller 把请求体解析为已验证的枚举后交给 service）。 */
data class ExpertManualProfilePatch(
    val expertId: String?,
    val level: ExpertIndexLevel,
    val group: ExpertManualProfileGroup,
    val expectedVersion: Long,
    val values: Map<String, Any?>?
)

/**
 * GET/PATCH 的完整响应；键名与 02 前端契约一致：`{expertId,version,identity,education,technology,matching,updatedBy,updatedAt}`。
 *
 * `matching.matchedCompanies` 空列表对应数据库 NULL（I-23：不用空字符串作第二空值表示）；
 * `documentNumber` 返回持久化原文（保留前导零），非编辑态遮罩是 02 展示职责——GET 返回遮罩会导致
 * 组内全字段 PATCH 用遮罩回写真实证件号。
 */
data class ExpertManualProfileDto(
    val expertId: String,
    val version: Long,
    val identity: Identity,
    val education: Education,
    val technology: Technology,
    val matching: Matching,
    val updatedBy: String?,
    val updatedAt: String?
) {
    data class Identity(
        val talentCode: String?,
        val chineseName: String?,
        val birthDateText: String?,
        val documentNumber: String?,
        val sourceCountry: String?
    )

    data class Education(
        val graduatedSchool: String?,
        val schoolRanking: String?,
        val preChinaOrganization: String?,
        val organizationRanking: String?,
        val postdocOrganization: String?,
        val postdocPeriod: String?,
        val professionalTitle: String?,
        val administrativeTitles: String?
    )

    data class Technology(
        val industry: String?,
        val technicalExpertise: String?,
        val programCategories: String?
    )

    data class Matching(
        val researchAbility: String?,
        val companyMatchStatus: String,
        val matchedCompanies: List<String>
    )

    companion object {
        /** GET 无行 / 尚未插入时的默认 DTO（I-3）：version=0、匹配进度默认未匹配、企业为空数组、审计字段为空。 */
        fun empty(expertId: String): ExpertManualProfileDto = ExpertManualProfileDto(
            expertId = expertId,
            version = 0,
            identity = Identity(
                talentCode = null,
                chineseName = null,
                birthDateText = null,
                documentNumber = null,
                sourceCountry = null
            ),
            education = Education(
                graduatedSchool = null,
                schoolRanking = null,
                preChinaOrganization = null,
                organizationRanking = null,
                postdocOrganization = null,
                postdocPeriod = null,
                professionalTitle = null,
                administrativeTitles = null
            ),
            technology = Technology(
                industry = null,
                technicalExpertise = null,
                programCategories = null
            ),
            matching = Matching(
                researchAbility = null,
                companyMatchStatus = CompanyMatchStatus.NOT_MATCHED.name,
                matchedCompanies = emptyList()
            ),
            updatedBy = null,
            updatedAt = null
        )
    }
}

/**
 * 01 私有业务异常：只由 `ExpertManualProfileController` 的局部 `@ExceptionHandler` 映射为固定
 * `{code,message,detail}`，不改全局异常处理器。消息只描述业务，绝不包含字段值、SQL 或文件路径。
 */
sealed class ExpertManualProfileException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** 400：无效参数（字段缺失/额外/类型/长度/枚举/日期、分组、版本）。 */
class ExpertManualProfileInvalidRequestException(message: String) : ExpertManualProfileException(message)

/** 404：专家在所有层都不存在（不伪装成 400/500，也不新建空专家）。 */
class ExpertProfileNotFoundException(message: String) : ExpertManualProfileException(message)

/** 409 PROFILE_CONFLICT：expectedVersion 不匹配，或首次插入撞键。 */
class ExpertProfileConflictException(message: String) : ExpertManualProfileException(message)

/** 503 EXPERT_LOOKUP_UNAVAILABLE：身份查询服务失败，绝不伪装 404。 */
class ExpertProfileLookupUnavailableException(message: String, cause: Throwable? = null) :
    ExpertManualProfileException(message, cause)

/** 500 PROFILE_STORE_FAILED：数据库读写失败；内部 SQL/路径不得外泄。 */
class ExpertProfileStoreException(message: String, cause: Throwable? = null) :
    ExpertManualProfileException(message, cause)
