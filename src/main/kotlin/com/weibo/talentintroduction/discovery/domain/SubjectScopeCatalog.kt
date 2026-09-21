package com.weibo.talentintroduction.discovery.domain

/**
 * 学科范围目录 —— 学科范围（包含哪些学科、每个源翻译成什么查询片段）的唯一声明处（I4-1）。
 *
 * 各 DataSource 只调用本目录取回本源的查询片段，不得在自己文件里硬编码学科名单；
 * 否则半年后没人说得清「工程学科」到底怎么定义，各源口径分叉。
 */
object SubjectScopeCatalog {

    /** 研发目标学科范围：工程/材料/计算机/化工/能源/物理。定时发现任务默认启用该范围，手动接口可覆盖。 */
    const val RND_TARGET = "RND_TARGET"

    /** 已注册的学科范围全集；新增 scope 时必须同步为每个函数补充分支（见 SubjectScopeCatalogTest 的分支覆盖断言）。 */
    val ALLOWED: Set<String> = setOf(RND_TARGET)

    /**
     * RND_TARGET 的 OpenAlex field id。
     * 取数日期 2026-08-25，来源 docs/plans/2026-08-25/00-research-checkpoints.md 的 CP-3：
     * Chemical Engineering 15 / Computer Science 17 / Engineering 22 / Energy 21 / Materials Science 25 / Physics and Astronomy 31。
     * 实测六个 field 全部隶属 Physical Sciences 域（domain 3），正向锁定已隐含排除 Health Sciences（domain 4），
     * 故不叠加 `primary_topic.domain.id:!4` 反向排除（主计划 F-2/F-5 选项 1，制药研发本轮列为范围外）。
     * 实测多值 `|` 语法可用：`primary_topic.field.id:22|31|17|25|21|15` count = 1,473,809。
     */
    private val RND_TARGET_OPENALEX_FIELD_IDS = listOf("22", "31", "17", "25", "21", "15")

    /**
     * RND_TARGET 的 arXiv 分类前缀（arXiv 官方分类命名，来源 docs/plans/2026-08-25/04-discovery-subject-scope.md Task 1）：
     * cs（Computer Science）、eess（Electrical Engineering and Systems Science）、
     * cond-mat（Condensed Matter）、physics（Physics）。
     */
    private val RND_TARGET_ARXIV_CATEGORIES = listOf("cs", "eess", "cond-mat", "physics")

    /**
     * RND_TARGET 的主题词（喂给 CORE `q`）。
     * CORE 侧把六个主题词用**显式括号 OR** 合并后与单年份 AND（CoreDataSource.buildQuery），
     * 不再走改动前的 AND 拼接；手动关键词仍然优先且保持 AND 语义。
     */
    private val RND_TARGET_CORE_KEYWORDS =
        listOf("engineering", "materials", "computer science", "chemical", "energy", "physics")

    /**
     * RND_TARGET 的 ORCID 主题种子（喂给 ORCID `keyword` 公开研究关键词字段，每个种子一个检索分片）。
     * 与 CORE 同六个研发类别；只作检索约束，不参与任何国籍/机构判定（I-3）。
     */
    private val RND_TARGET_ORCID_KEYWORDS =
        listOf("engineering", "materials", "computer science", "chemical", "energy", "physics")

    /**
     * RND_TARGET 的主题词（可喂给 Crossref `query`）。
     * Crossref REST API 没有学科过滤器，`query` 是相关性排序的自由文本检索，
     * 多个主题词以空格拼接即等价于「这些主题词之一」，用于让默认研发检索带上领域指向，
     * 而不是无条件下发 filter-only 全领域抓取（I-3）。操作端关键词仍然优先，见 CrossrefDataSource。
     * 顺序与内容逐字锚定在 SubjectScopeCatalogTest。
     */
    private val RND_TARGET_CROSSREF_QUERIES = listOf(
        "engineering", "materials science", "computer science", "chemical engineering", "energy", "physics"
    )

    /** 返回追加到 OpenAlex `buildFilter` `parts` 的片段列表；null 或未知 scope 返回空列表（I4-2）。 */
    fun openAlexFilterParts(scope: String?): List<String> = when (scope) {
        RND_TARGET -> listOf("primary_topic.field.id:${RND_TARGET_OPENALEX_FIELD_IDS.joinToString("|")}")
        else -> emptyList()
    }

    /** 返回 arXiv 分类前缀列表；null 或未知 scope 返回空列表（I4-2）。 */
    fun arxivCategories(scope: String?): List<String> = when (scope) {
        RND_TARGET -> RND_TARGET_ARXIV_CATEGORIES
        else -> emptyList()
    }

    /** 返回可喂给 CORE `q` 的主题词；null 或未知 scope 返回空列表（I4-2）。c4 起由 CoreDataSource 接线。 */
    fun coreKeywords(scope: String?): List<String> = when (scope) {
        RND_TARGET -> RND_TARGET_CORE_KEYWORDS
        else -> emptyList()
    }

    /**
     * 返回可喂给 ORCID `keyword` 字段的主题种子；null 或未知 scope 返回空列表（I-2/I-3）。
     * 空列表是「没有可用主题种子」的唯一信号：调用方不得把它翻译成通配查询。
     */
    fun orcidSeedKeywords(scope: String?): List<String> = when (scope) {
        RND_TARGET -> RND_TARGET_ORCID_KEYWORDS
        else -> emptyList()
    }

    /**
     * 返回可喂给 Crossref `query` 的主题词；null 或未知 scope 返回空列表（I-3/I4-2）。
     * 空列表是「保持改动前的无 query 行为」的唯一信号，调用方不得把空列表翻译成 `all:*` 之类的新查询。
     */
    fun crossrefQueries(scope: String?): List<String> = when (scope) {
        RND_TARGET -> RND_TARGET_CROSSREF_QUERIES
        else -> emptyList()
    }

    /** 返回本次运行不参与的源名集合；null 或未知 scope 返回空集（I4-3：源退出是「本次不参与」不是「注销」）。 */
    fun excludedSources(scope: String?): Set<String> = when (scope) {
        RND_TARGET -> setOf("EUROPE_PMC", "PMC_OA")
        else -> emptySet()
    }
}
