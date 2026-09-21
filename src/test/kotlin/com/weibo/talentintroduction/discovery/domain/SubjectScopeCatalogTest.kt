package com.weibo.talentintroduction.discovery.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubjectScopeCatalogTest {

    @Test
    fun `unknown scope and null return empty for all functions`() {
        // I4-2：null 或未知 scope 必须返回空集合/空列表，保证各源查询串与改动前逐字相同。
        for (scope in listOf(null, "UNKNOWN_SCOPE")) {
            assertEquals(emptyList<String>(), SubjectScopeCatalog.openAlexFilterParts(scope))
            assertEquals(emptyList<String>(), SubjectScopeCatalog.arxivCategories(scope))
            assertEquals(emptyList<String>(), SubjectScopeCatalog.coreKeywords(scope))
            assertEquals(emptyList<String>(), SubjectScopeCatalog.orcidSeedKeywords(scope))
            assertEquals(emptyList<String>(), SubjectScopeCatalog.crossrefQueries(scope))
            assertEquals(emptySet<String>(), SubjectScopeCatalog.excludedSources(scope))
        }
    }

    @Test
    fun `coreKeywords RND_TARGET is the locked six R&D topics`() {
        // I-3：CORE 查询的主题词来自目录唯一来源；逐字锚定，防止主题漂移无人察觉。
        assertEquals(
            listOf("engineering", "materials", "computer science", "chemical", "energy", "physics"),
            SubjectScopeCatalog.coreKeywords(SubjectScopeCatalog.RND_TARGET)
        )
    }

    @Test
    fun `orcidSeedKeywords RND_TARGET is the locked six R&D topics`() {
        // I-2/I-3：ORCID 的关键词字段种子同样只有六类研发范围，逐一字锚定。
        assertEquals(
            listOf("engineering", "materials", "computer science", "chemical", "energy", "physics"),
            SubjectScopeCatalog.orcidSeedKeywords(SubjectScopeCatalog.RND_TARGET)
        )
    }

    @Test
    fun `crossrefQueries RND_TARGET is the locked topic list`() {
        // Crossref 没有学科过滤器，默认研发检索只能靠主题词收窄；逐字锚定，防止日后的主题漂移无人察觉。
        assertEquals(
            listOf("engineering", "materials science", "computer science", "chemical engineering", "energy", "physics"),
            SubjectScopeCatalog.crossrefQueries(SubjectScopeCatalog.RND_TARGET)
        )
    }

    @Test
    fun `openAlexFilterParts RND_TARGET is the single locked field fragment`() {
        // 六个 OpenAlex field id（取数日期 2026-08-25，来源 docs/plans/2026-08-25/00-research-checkpoints.md 的 CP-3）
        // 逐字锚定，防止日后改数字时无人察觉。
        assertEquals(
            listOf("primary_topic.field.id:22|31|17|25|21|15"),
            SubjectScopeCatalog.openAlexFilterParts(SubjectScopeCatalog.RND_TARGET)
        )
    }

    @Test
    fun `excludedSources RND_TARGET is exactly two`() {
        // I4-3：RND_TARGET 下生物医学两源「本次不参与」，恰好两项。
        assertEquals(
            setOf("EUROPE_PMC", "PMC_OA"),
            SubjectScopeCatalog.excludedSources(SubjectScopeCatalog.RND_TARGET)
        )
    }

    @Test
    fun `ALLOWED covers every branch`() {
        // 分支覆盖一致性：ALLOWED 中每个 scope 都必须在各函数中有非空分支，
        // 防止新增 scope 时漏改某个函数（I4-1 单一语义）。
        assertEquals(setOf(SubjectScopeCatalog.RND_TARGET), SubjectScopeCatalog.ALLOWED)
        for (scope in SubjectScopeCatalog.ALLOWED) {
            assertTrue(SubjectScopeCatalog.openAlexFilterParts(scope).isNotEmpty(),
                "openAlexFilterParts must have a branch for scope '$scope'")
            assertTrue(SubjectScopeCatalog.arxivCategories(scope).isNotEmpty(),
                "arxivCategories must have a branch for scope '$scope'")
            assertTrue(SubjectScopeCatalog.coreKeywords(scope).isNotEmpty(),
                "coreKeywords must have a branch for scope '$scope'")
            assertTrue(SubjectScopeCatalog.orcidSeedKeywords(scope).isNotEmpty(),
                "orcidSeedKeywords must have a branch for scope '$scope'")
            assertTrue(SubjectScopeCatalog.crossrefQueries(scope).isNotEmpty(),
                "crossrefQueries must have a branch for scope '$scope'")
            assertTrue(SubjectScopeCatalog.excludedSources(scope).isNotEmpty(),
                "excludedSources must have a branch for scope '$scope'")
        }
    }
}
