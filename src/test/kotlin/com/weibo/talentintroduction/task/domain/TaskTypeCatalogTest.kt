package com.weibo.talentintroduction.task.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TaskTypeCatalogTest {

    @Test
    fun `EXPERT_CLASSIFICATION_BACKFILL is registered with fixed semantics (I2-5)`() {
        val meta = TaskTypeCatalog.byCode("EXPERT_CLASSIFICATION_BACKFILL")
            ?: error("EXPERT_CLASSIFICATION_BACKFILL 未在 TaskTypeCatalog 登记")
        assertEquals("专家研发类型回填", meta.label)
        assertEquals("MANUAL", meta.group)
        assertEquals("已处理/失败", meta.metricLabel)
        assertNull(meta.summaryRule)
        assertTrue(meta.hasProgressUi)
        assertNull(meta.drilldown)
    }

    @Test
    fun `executions whitelist derived from catalog includes the backfill type (I2-5)`() {
        // TaskProgressController.allowedTaskTypes 由 catalog 的 hasProgressUi 派生，
        // 登记后 /api/task-progress/EXPERT_CLASSIFICATION_BACKFILL/executions 才可用。
        val allowed = TaskTypeCatalog.entries.filter { it.value.hasProgressUi }.keys
        assertTrue("EXPERT_CLASSIFICATION_BACKFILL" in allowed)
    }
}
