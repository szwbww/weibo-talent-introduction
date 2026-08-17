package com.weibo.talentintroduction.task.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * B5 迁移与 SQL 文本断言（沿用 QaSeedEncodingRepairMigrationTest 范式，不需 Docker）：
 * - V102 建 idx_tpl_created_at 且不含 `${`（Flyway 占位符）。
 * - 两个 Repository 的 deleteOlderThan 删除条件（I3-1 / M-6 / I3-3）。
 * - application.yml 的 placeholder-replacement: false 回归（K-flyway-placeholder-replacement，
 *   与 UnsubscribeBodyLinkMigrationTest 同类）。
 */
class TaskRetentionMigrationTest {

    private val v102Sql = Files.readString(
        Path.of("src/main/resources/db/migration/V102__add_task_progress_log_created_at_index.sql")
    )
    private val progressLogRepoSource = Files.readString(
        Path.of("src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskProgressLogRepository.kt")
    )
    private val executionRepoSource = Files.readString(
        Path.of("src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt")
    )

    @Test
    fun `V102 creates the created_at index on task_progress_log`() {
        assertTrue(
            v102Sql.contains("CREATE INDEX idx_tpl_created_at ON task_progress_log (created_at)"),
            "V102 must create idx_tpl_created_at on task_progress_log(created_at)"
        )
    }

    @Test
    fun `V102 contains no flyway placeholder`() {
        assertFalse(v102Sql.contains("\${"), "V102 must not contain flyway placeholder syntax")
    }

    @Test
    fun `progress log delete query keys on created_at only without join or execution id`() {
        val queryBlock = queryBlockOf(progressLogRepoSource, "deleteOlderThan")
        assertTrue(queryBlock.contains("created_at <"), "deleteOlderThan must filter on created_at < cutoff (I3-1)")
        assertFalse(queryBlock.contains("JOIN"), "deleteOlderThan must not use JOIN (I3-1)")
        assertFalse(queryBlock.contains("EXISTS"), "deleteOlderThan must not use EXISTS (I3-1)")
        assertFalse(queryBlock.contains("task_execution_id"), "deleteOlderThan must not reference task_execution_id (I3-1 / M-6)")
        assertFalse(queryBlock.contains("task_type"), "deleteOlderThan must not filter by task_type (I3-5 no self-exemption)")
    }

    @Test
    fun `execution delete query keys on started_at not created_at`() {
        val queryBlock = queryBlockOf(executionRepoSource, "deleteOlderThan")
        assertTrue(queryBlock.contains("started_at <"), "deleteOlderThan must filter on started_at < cutoff (I3-3, idx_te_started)")
        assertFalse(queryBlock.contains("created_at <"), "task_execution delete must not filter on created_at (no index, I3-3)")
        assertFalse(queryBlock.contains("task_type"), "deleteOlderThan must not filter by task_type (I3-5 no self-exemption)")
    }

    @Test
    fun `production flyway config disables placeholder replacement`() {
        val yml = Files.readString(Path.of("src/main/resources/application.yml"))
        assertTrue(yml.contains("placeholder-replacement: false"), "flyway placeholder replacement must be disabled")
    }

    /** 取 @Modifying 之后到 fun 声明行末尾的窗口（含 @Query 注解、不含方法注释）。 */
    private fun queryBlockOf(source: String, methodName: String): String {
        val funIdx = source.indexOf("fun $methodName")
        assertTrue(funIdx >= 0, "missing fun $methodName in repository source")
        val modIdx = source.lastIndexOf("@Modifying", funIdx)
        assertTrue(modIdx >= 0, "missing @Modifying before $methodName")
        return source.substring(modIdx, source.indexOf('\n', funIdx) + 1)
    }
}
