package com.weibo.talentintroduction.expert.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

/**
 * 发信门禁守卫（版本比较 + sendable 读取）—— 04 之后研发类型集合是唯一收口点。
 *
 * 用例 1（I5a2-9 / 04 收尾）：`src/main/kotlin` 下任何把 `classification.version`
 * （含 `expertClassification.version`）与 `ExpertClassificationService.VERSION` **直接比较**
 * 的位置（同一行或跨行），必须恰好等于 [ALLOWED_GATE_SITES]。04 删除 `expertSendableFilter`
 * 与 `ACCEPTED_CLASSIFICATION_VERSIONS` 后，发信判定不再做版本比较 —— 白名单恒为空集。
 *
 * 用例 2（I4-1 / 主计划 M-1 机器判据）：`expertClassification.sendable` /
 * `classification.sendable` 的读取位置必须恰好等于 [ALLOWED_SENDABLE_SITES] 四个文件
 * （派生属性定义 / 序列化 / 回填统计 / API DTO，均为子计划 05 的非过滤用途）。
 * `ExpertSearchService.kt` / `BatchExecutionModels.kt` / `ManualInitialOutreachService.kt`
 * 出现在命中集合里即失败 —— 说明有第二个隐式门禁被重新引入。
 *
 * 不扫描回填/管理面对 `request.version` 或 `term` map 的目标版本比较（I5a2-11）：
 * 那些语义是「还没到新版本」，必须钉死 `VERSION`。
 *
 * 机制：扫源码（相对路径读取，`mvn test` 工作目录为工程根），仿
 * OperatorStatusWriteSeamGuardTest 的白名单闭包写法。只有带比较运算符且同/邻行出现
 * `ExpertClassificationService.VERSION` 的行才构成命中，`term` map 构造（无运算符）天然不命中。
 */
class ExpertClassificationVersionGateGuardTest {

    /** 白名单：允许直接比较 `classification.version` 与 `VERSION` 的文件集合。阶段一之后必须为空。 */
    private val ALLOWED_GATE_SITES: Set<String> = emptySet()

    /**
     * 白名单：允许读取 `expertClassification.sendable` / `classification.sendable`（含派生属性
     * 定义）的文件集合。04 之后 sendable 不再参与任何发信判定，只剩四个非过滤用途
     * （主计划 M-1 的机器判据）；子计划 05 收尾后白名单应为空集。
     */
    private val ALLOWED_SENDABLE_SITES: Set<String> = setOf(
        "ExpertClassification.kt", // 派生属性自身的定义（type ∈ SENDABLE_TYPES）
        "ExpertIndexWriterService.kt", // 序列化（子计划 05 才删）
        "ExpertClassificationBackfillService.kt", // 统计计数（子计划 05 才改）
        "ExpertIndexController.kt" // API DTO（子计划 05 才删）
    )

    private val sourceRoot = Paths.get("src/main/kotlin")

    private val COMPARISON_OPERATOR = Regex("""(==|!=|!in|\bin\b|<=|>=|>|<)""")

    /** 匹配「读取 classification 对象上的 sendable」的位置：`expertClassification.sendable` /
     *  `expertClassification?.sendable` / `classification.sendable`，以及 ExpertClassification.kt
     *  内派生属性的声明（`sendable: Boolean`）。 */
    private val SENDABLE_READ = Regex("""[Cc]lassification(\?)?\.sendable|sendable\s*:\s*Boolean""")

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

    @Test
    fun `sendable reads appear only in the child-05 whitelist (I4-1)`() {
        val hitFiles = sortedSetOf<String>()

        Files.walk(sourceRoot).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .sorted()
                .forEach { file ->
                    val rel = sourceRoot.relativize(file).toString().replace('\\', '/')
                    val lines = Files.readAllLines(file)
                    lines.forEachIndexed { _, raw ->
                        val line = raw.trim()
                        if (line.isEmpty() || isCommentLine(line)) return@forEachIndexed
                        if (!SENDABLE_READ.containsMatchIn(line)) return@forEachIndexed
                        hitFiles += rel.substringAfterLast('/')
                    }
                }
        }

        val message = buildString {
            appendLine("sendable 读取守卫失败（I4-1：研发类型集合是唯一收口点）")
            appendLine("src/main/kotlin 下读取 expertClassification.sendable / classification.sendable 的位置必须恰好等于白名单。")
            appendLine("期望白名单：$ALLOWED_SENDABLE_SITES")
            appendLine("实际命中文件：$hitFiles")
            appendLine("整改指引：发信判定已统一到研发类型集合；sendable 读取只允许出现在子计划 05 的")
            appendLine("  四个非过滤位置（派生属性定义 / 序列化 / 回填统计 / API DTO）。")
        }
        assertEquals(ALLOWED_SENDABLE_SITES, hitFiles, message)
    }

    /** 注释行（`//`、块注释、KDoc 的 `*`）不可能产生比较，确定性跳过。 */
    private fun isCommentLine(trimmed: String): Boolean =
        trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")
}
