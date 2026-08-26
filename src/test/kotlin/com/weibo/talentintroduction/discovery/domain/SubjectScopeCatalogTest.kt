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
            assertEquals(emptySet<String>(), SubjectScopeCatalog.excludedSources(scope))
        }
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
            assertTrue(SubjectScopeCatalog.excludedSources(scope).isNotEmpty(),
                "excludedSources must have a branch for scope '$scope'")
        }
    }
}
