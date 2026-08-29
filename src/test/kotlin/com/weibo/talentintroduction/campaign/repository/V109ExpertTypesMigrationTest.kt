package com.weibo.talentintroduction.campaign.repository

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * I3-3/I3-4/I3-5：V109 存量迁移文本断言（照 QaSeedEncodingRepairMigrationTest 范式，
 * 不需 Docker，可进全量 `mvn test`）。
 */
class V109ExpertTypesMigrationTest {
    @Test
    fun `v109 writes the three sendable types verbatim`() {
        val sql = readV109()

        // I3-3: 三个值与 ExpertClassification.SENDABLE_TYPES（枚举前三值）逐字等价，
        //       迁移后线上发信人群零变化。
        assertTrue(
            sql.contains("""["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]"""),
            "V109 must write the exact three-value array"
        )
    }

    @Test
    fun `v109 only covers rows that are currently empty`() {
        val sql = readV109()

        // I3-4: 只覆盖当前为空的行，不抹掉运营已手工勾选的配置。
        assertTrue(sql.contains("WHERE"), "V109 must scope the UPDATE with a WHERE clause")
        assertTrue(sql.contains("'[]'"), "V109 must include the empty-array predicate")
        assertTrue(
            sql.contains("expert_types_json IS NULL") &&
                sql.contains("expert_types_json = ''"),
            "V109 must cover IS NULL and blank-string rows"
        )
    }

    @Test
    fun `v109 contains no flyway placeholder`() {
        val sql = readV109()

        // I3-5: 正文不含 ${（K-flyway-placeholder-replacement 要求维持的约束）。
        assertFalse(sql.contains("${'$'}{"), "V109 must not contain any \${ placeholder")
    }

    private fun readV109(): String {
        val sqlPath = Path.of("src/main/resources/db/migration/V110__require_expert_types_on_batch_send_task_config.sql")
        return Files.readString(sqlPath)
    }
}
