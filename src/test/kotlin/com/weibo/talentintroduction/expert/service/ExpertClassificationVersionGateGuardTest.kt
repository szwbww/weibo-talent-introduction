package com.weibo.talentintroduction.expert.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

/**
 * I5a2-9：发信门禁的版本比较只有一个权威来源守卫测试。
 *
 * 规则：`src/main/kotlin` 下任何把 `classification.version`（含 `expertClassification.version`）
 * 与 `ExpertClassificationService.VERSION` **直接比较**的位置（同一行或跨行），必须恰好等于
 * [ALLOWED_GATE_SITES]。阶段一（05A-2 Part C）之后白名单应为空集 —— 发信门禁的版本比较
 * 只能引用 `ExpertClassificationService.ACCEPTED_CLASSIFICATION_VERSIONS`
 * （ES `terms` 谓词 / 内存 `!in` 集合判定，I5a2-9）。
 *
 * 不扫描回填/管理面对 `request.version` 或 `term` map 的目标版本比较（I5a2-11）：
 * 那些语义是「还没到新版本」，必须钉死 `VERSION`，不得换成 ACCEPTED 集合。
 *
 * 机制：扫源码（相对路径读取，`mvn test` 工作目录为工程根），仿
 * OperatorStatusWriteSeamGuardTest 的白名单闭包写法。只有带比较运算符且同/邻行出现
 * `ExpertClassificationService.VERSION` 的行才构成命中，`term` map 构造（无运算符）天然不命中。
 */
class ExpertClassificationVersionGateGuardTest {

    /** 白名单：允许直接比较 `classification.version` 与 `VERSION` 的文件集合。阶段一之后必须为空。 */
    private val ALLOWED_GATE_SITES: Set<String> = emptySet()

    private val sourceRoot = Paths.get("src/main/kotlin")

    private val COMPARISON_OPERATOR = Regex("""(==|!=|!in|\bin\b|<=|>=|>|<)""")

    private data class Hit(val path: String, val lineNumber: Int, val text: String)

    @Test
    fun `sendable version comparisons only reference ACCEPTED_CLASSIFICATION_VERSIONS (I5a2-9)`() {
        val violations = mutableListOf<Hit>()
        val hitFiles = sortedSetOf<String>()

        Files.walk(sourceRoot).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .sorted()
                .forEach { file ->
                    val rel = sourceRoot.relativize(file).toString().replace('\\', '/')
                    val lines = Files.readAllLines(file)
                    lines.forEachIndexed { idx, raw ->
                        val line = raw.trim()
                        if (line.isEmpty() || isCommentLine(line)) return@forEachIndexed
                        if (!line.contains("classification.version")) return@forEachIndexed
                        val hasVersionToken = line.contains("ExpertClassificationService.VERSION") ||
                            lines.getOrNull(idx + 1)?.contains("ExpertClassificationService.VERSION") == true
                        if (!hasVersionToken) return@forEachIndexed
                        if (!COMPARISON_OPERATOR.containsMatchIn(line)) return@forEachIndexed
                        violations += Hit(rel, idx + 1, line)
                        hitFiles += rel.substringAfterLast('/')
                    }
                }
        }

        val message = buildString {
            appendLine("发信门禁版本比较守卫失败（I5a2-9：唯一权威来源）")
            appendLine("src/main/kotlin 下 `classification.version` 与 `ExpertClassificationService.VERSION` 直接比较的位置必须恰好等于 ALLOWED_GATE_SITES。")
            appendLine("期望白名单：$ALLOWED_GATE_SITES")
            appendLine("实际命中文件：$hitFiles")
            if (violations.isNotEmpty()) {
                appendLine("未登记白名单的直接比较（违规）：")
                violations.forEach { appendLine("  ${it.path}:${it.lineNumber}: ${it.text}") }
            }
            appendLine("整改指引：发信门禁的版本比较只能引用 ExpertClassificationService.ACCEPTED_CLASSIFICATION_VERSIONS；")
            appendLine("  回填/管理面的目标版本比较（request.version / term map，I5a2-11）不在此列，不得换成 ACCEPTED 集合。")
        }
        assertEquals(ALLOWED_GATE_SITES, hitFiles, message)
    }

    /** 注释行（`//`、块注释、KDoc 的 `*`）不可能产生比较，确定性跳过。 */
    private fun isCommentLine(trimmed: String): Boolean =
        trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")
}
