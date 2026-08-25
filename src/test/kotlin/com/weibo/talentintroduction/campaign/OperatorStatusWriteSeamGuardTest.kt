package com.weibo.talentintroduction.campaign

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * P-D（plan 02）：`expert_contact.operator_status` 唯一写入口守卫测试。
 *
 * 白名单闭包（I-1）：`src/main/kotlin` 下对 `ExpertContact.operatorStatus` 的赋值位置集合
 * 必须恰好等于 [ALLOWED_WRITE_SITES]，多一个少一个都失败。
 * 显式变更（I-2）：新增写入点必须同时登记白名单并写明理由；失败信息给出违规 file:line。
 *
 * 机制：扫描 `src/main/kotlin` 下全部 `.kt` 源码（相对路径读取，`mvn test` 工作目录为工程根，
 * 与 QaRuleManagementServiceTest 读 migration 文件的机制一致）。不用 ArchUnit ——
 * 它工作在字节码层，对 Kotlin data class `.copy(...)` 合成方法的参数名捕获不精确，
 * 故选择扫源码的白名单守卫测试。
 */
class OperatorStatusWriteSeamGuardTest {

    /**
     * T-2 白名单：`src/main/kotlin` 下对 `expert_contact.operator_status` 的唯一 DB 写入口文件集合。
     * 新增合法写入点必须登记到此集合，并在一行中文注释中写明理由（I-2）。
     */
    private val ALLOWED_WRITE_SITES: Set<String> = setOf(
        // 唯一自动出口（updateAutomatically）+ 人工出口（changeStatus）：所有状态推进都必须经由此服务
        "ExpertOperatorStatusService.kt",
        // 建行初始化（:611 NOT_CONTACTED）+ 退信标记（:706 EMAIL_INVALID）：仅这两个例外场景不经服务出口
        "ManualInitialOutreachService.kt"
    )

    /** 非写入命中的显式排除项：文件路径 + 行号 + 预期上下文子串，三者同时命中才排除。 */
    private data class NoiseSite(val path: String, val line: Int, val context: String)

    /**
     * 显式排除名单（I-1 的"恰好等于白名单"要求把非写入命中排除干净）。
     * 规则基于文件路径 + 行号 + 上下文，不用模糊启发式；
     * 行号移位或上下文变化都会让该行重新进入违规集合（宁可误报、不放过）。
     * path 为相对 src/main/kotlin 的路径。
     */
    private val EXCLUDED_NOISE_SITES: List<NoiseSite> = listOf(
        // ── DTO 构造命名参数（7 处，P-A 现状审计确认，非 DB 写入）──
        // 入站处理请求 DTO 构造：把请求体字段透传到处理 DTO，不落库
        NoiseSite("com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt", 203, "operatorStatus = request.operatorStatus"),
        // 响应 DTO 构造：把当前值原样回显到出参 DTO
        // （守卫误报修正：行号登记 1098 → 实际 1099，2026-08-20 人工回复透传新增一行导致偏移）
        NoiseSite("com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt", 1099, "operatorStatus = operatorStatus"),
        // 邮箱汇总响应 DTO 构造：把汇总行字段映射到响应 DTO
        NoiseSite("com/weibo/talentintroduction/mail/service/MailboxService.kt", 165, "operatorStatus = summary.operatorStatus"),
        // 专家联系人列表响应 DTO 构造：查询参数回显到 DTO
        NoiseSite("com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt", 549, "operatorStatus = operatorStatus"),
        // ES 专家列表响应 DTO 构造：contact/ES 文档字段合并到响应 DTO
        NoiseSite("com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt", 90, "operatorStatus = contact?.operatorStatus"),
        // ES 专家详情响应 DTO 构造：同上
        NoiseSite("com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt", 431, "operatorStatus = operatorStatus ?: expert.operatorStatus"),
        // ES 文档 → 响应 DTO 映射：读取 ES 字段写入 DTO，非 DB 写入
        // （05 P-E 新增 operatorStatusFilter 使钉死点 :332 偏移至 :345，A5 授权行号修正；
        //   fast-p 01 A1：T3 分类解析/logger 导入使 :431 偏移至 :445，A1 授权行号修正，context 不变；
        //   expertSendableFilter() 当前新增版本门禁使 :491 偏移至 :498，保持读取映射排除点精确对齐
        NoiseSite("com/weibo/talentintroduction/expert/service/ExpertSearchService.kt", 498, "operatorStatus = source.nullableText"),
        // ── ES 侧写入（非 expert_contact 表写入，扫描模式天然不命中）──
        // 03 P-B T-3 后：ExpertIndexWriterService 的 operatorStatus 同步改为三层 doc 部分更新
        // （"operatorStatus" to operatorStatus），旧脚本行 ctx._source.operatorStatus = params.status
        // 已随 syncCandidateOperatorStatus 移除 → 原 :84 排除项失效，按新契约删除（未弱化白名单闭包断言）。
        // ── SQL 只读上下文（SELECT @Query，非写入）──
        // SELECT @Query 的 WHERE 比较（读过滤条件），不是对列的赋值
        NoiseSite("com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt", 47, "operator_status = :operatorStatus"),
        // SELECT 列投影：读取列值供响应 DTO 使用
        NoiseSite("com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt", 537, "ec.operator_status AS operator_status"),
        // SELECT GROUP BY 列引用：只读聚合
        NoiseSite("com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt", 585, "ec.operator_status, ec.current_index_level")
    )

    /**
     * 属性声明排除：`val|var operatorStatus` 开头的声明是定义而非赋值写入（当前无命中，规则防未来）。
     * 函数形参（`operatorStatus: String = ...`）因 `=` 前是类型而非字段名，天然不命中
     * `operatorStatus = ` 命名参数模式，无需额外规则。
     */
    private val DECLARATION_PREFIX = Regex("""(?<![.\w])(val|var)\s+operatorStatus\b""")

    private val sourceRoot: Path = Paths.get("src/main/kotlin")

    private data class Hit(val path: String, val lineNumber: Int, val text: String)

    @Test
    fun `operator_status write sites exactly match whitelist`() {
        val violations = mutableListOf<Hit>()
        val hitFiles = sortedSetOf<String>()
        val fileLines = mutableMapOf<String, List<String>>()

        Files.walk(sourceRoot).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .sorted()
                .forEach { file ->
                    val rel = sourceRoot.relativize(file).toString().replace('\\', '/')
                    val lines = Files.readAllLines(file)
                    fileLines[rel] = lines
                    lines.forEachIndexed { idx, raw ->
                        val line = raw.trim()
                        if (line.isEmpty() || isCommentLine(line)) return@forEachIndexed
                        // 两种写法的赋值命中：copy()/构造命名参数（`operatorStatus = `）与 SQL `operator_status`
                        if (!(line.contains("operatorStatus = ") || line.contains("operator_status"))) return@forEachIndexed
                        if (DECLARATION_PREFIX.containsMatchIn(line)) return@forEachIndexed
                        val lineNumber = idx + 1
                        if (isExcludedNoise(rel, lineNumber, raw)) return@forEachIndexed
                        violations += Hit(rel, lineNumber, raw.trim())
                        hitFiles += rel.substringAfterLast('/')
                    }
                }
        }

        val message = buildString {
            appendLine("expert_contact.operator_status 写入口守卫失败（I-1 白名单闭包 / I-2 显式变更）")
            appendLine("src/main/kotlin 下对 operatorStatus 的赋值位置集合必须恰好等于 ALLOWED_WRITE_SITES。")
            appendLine("期望白名单：$ALLOWED_WRITE_SITES")
            appendLine("实际命中文件：$hitFiles")
            if (violations.isNotEmpty()) {
                appendLine("未登记白名单的写入点（违规）：")
                violations.forEach { appendLine("  ${it.path}:${it.lineNumber}: ${it.text}") }
            }
            val whitelistedWithoutHits = ALLOWED_WRITE_SITES - hitFiles
            if (whitelistedWithoutHits.isNotEmpty()) {
                appendLine("白名单内但无写入命中的文件（白名单过期或写入被移除）：")
                whitelistedWithoutHits.forEach { appendLine("  $it") }
            }
            appendLine("整改指引（I-2，白名单变更必须显式）：")
            appendLine("  新增对 expert_contact.operator_status 的写入必须登记到 ALLOWED_WRITE_SITES 并在一行中文注释中写明理由；")
            appendLine("  或改用白名单内文件中的唯一写入口（ExpertOperatorStatusService / ManualInitialOutreachService）。")
            appendLine("  排除名单失效请同步更新 EXCLUDED_NOISE_SITES 的 path/line/context（宁可误报、不放过）。")
        }
        assertEquals(ALLOWED_WRITE_SITES, hitFiles, message)

        // 排除名单自检：任何排除项必须仍然精确命中（防过期排除静默放行）
        val staleExclusions = EXCLUDED_NOISE_SITES.filter { site ->
            val lines = fileLines[site.path]
            lines == null || lines.size < site.line || !lines[site.line - 1].contains(site.context)
        }
        assertTrue(
            staleExclusions.isEmpty(),
            "排除名单已失效（path/line/context 不再精确命中），必须同步更新 EXCLUDED_NOISE_SITES：\n" +
                staleExclusions.joinToString("\n") { "${it.path}:${it.line}" }
        )
    }

    /** 注释行（`//`、块注释、KDoc 的 `*`）不可能产生写入，确定性跳过。 */
    private fun isCommentLine(trimmed: String): Boolean =
        trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")

    private fun isExcludedNoise(rel: String, lineNumber: Int, raw: String): Boolean =
        EXCLUDED_NOISE_SITES.any { it.path == rel && it.line == lineNumber && raw.contains(it.context) }
}
