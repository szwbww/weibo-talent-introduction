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
     * RND_TARGET 的主题词（可喂给 CORE `q`）。
     * 注意：本轮有意不接线 —— CORE 的 `q` 参数是 AND 拼接（CoreDataSource.kt:48-49），
     * 把多个主题词以 OR 语义塞进去需要改写查询构造，风险与收益不匹配；先在目录中声明，留待后续。
     */
    private val RND_TARGET_CORE_KEYWORDS =
        listOf("engineering", "materials", "computer science", "chemical", "energy", "physics")

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

    /** 返回可喂给 CORE `q` 的主题词；null 或未知 scope 返回空列表（I4-2）。本轮不接线（见 RND_TARGET_CORE_KEYWORDS 注释）。 */
    fun coreKeywords(scope: String?): List<String> = when (scope) {
        RND_TARGET -> RND_TARGET_CORE_KEYWORDS
        else -> emptyList()
    }

    /** 返回本次运行不参与的源名集合；null 或未知 scope 返回空集（I4-3：源退出是「本次不参与」不是「注销」）。 */
    fun excludedSources(scope: String?): Set<String> = when (scope) {
        RND_TARGET -> setOf("EUROPE_PMC", "PMC_OA")
        else -> emptySet()
    }
}
