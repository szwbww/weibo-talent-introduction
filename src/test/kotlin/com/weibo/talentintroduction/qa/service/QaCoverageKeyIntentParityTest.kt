package com.weibo.talentintroduction.qa.service

import com.weibo.talentintroduction.llm.service.AiReplyIntentCatalog
import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.domain.QaRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class QaCoverageKeyIntentParityTest {
    // 计划 02 (I-5) 例外集合：每条都必须写明理由，删掉理由即视为缺陷。
    private val knownUnreferencedKeys = setOf(
        // 兜底 intent 自身的 key；requiredCoverageKeys 为空，
        // 由 selectIntentKeyForRule 的 blankCoverage 分支专门处理。
        "general.answer",
        // Workplace arrangement 与 Program overview 都同时带 work.travel_arrangement，
        // 后者已被 intent 引用，故这两条规则仍可达；本键无实害，暂不删。
        "work.relocation"
    )

    // 计划 11 (T-3) 例外集合：intent 可达但没有任何迁移规则占用（"可达但无主"）的键。
    // 理由：本轮无经需求方确认的权威正文，编造对外承诺违反可追溯原则；补上正文后
    // 须从本集合删除。该集合是随 12/13/14 变短的活文件——每次有新事实落库都应变短，
    // 绝不允许变长来"让测试变绿"。
    private val knownUnownedKeys = setOf(
        "publication.authorship"
    )

    // matchIntentsWithSpans 对 next_stages 的 timing 组合（asksTiming 且无工作类
    // intent 时）会把 requiredCoverageKeys 扩为 steps + timeline（:424），
    // 因此 application.timeline 虽不在静态 definitions 里，仍是可达引用——
    // 与计划 C-1 审计按全文件字面量提取的口径一致。
    private fun intentReferencedKeys(): Set<String> =
        (AiReplyIntentCatalog.definitions
            .flatMap { it.requiredCoverageKeys + it.alternativeCoverageKeys } +
            listOf("application.timeline")).toSet()

    @Test
    fun `every intent coverage key exists in the catalog`() {
        // I-5 (a): 违反时 normalizeAndValidate 的 require(unknown.isEmpty()) 会让
        // 任何试图存该键的规则保存失败。
        val catalogKeys = QaCoverageKeyCatalog.all().map { it.key }.toSet()
        val missing = AiReplyIntentCatalog.definitions
            .flatMap { it.requiredCoverageKeys + it.alternativeCoverageKeys }
            .filterNot { it in catalogKeys }
            .distinct()
        assertEquals(emptyList<String>(), missing, "intent coverage keys missing from catalog: $missing")
    }

    @Test
    fun `every catalog key is referenced by at least one intent`() {
        // I-5 (b): 违反时该键的规则在 grounded 链路结构性不可达（isCoverageEligible
        // 对非空 coverage 要求与 intent 的 required+alternative 有交集）。
        val referenced = intentReferencedKeys()
        val unreferenced = QaCoverageKeyCatalog.all()
            .map { it.key }
            .filterNot { it in referenced || it in knownUnreferencedKeys }
        assertEquals(
            emptyList<String>(),
            unreferenced,
            "catalog keys referenced by no intent (structurally unreachable): $unreferenced"
        )
    }

    @Test
    fun `known unreferenced exceptions are exactly the truly unreferenced keys`() {
        // I-5 验收: 把 knownUnreferencedKeys 清空后 (b) 必须失败——本断言证明例外
        // 集合确实在起作用，且与真实失配集合精确一致（多了是掩盖缺陷，少了是误放行）。
        val referenced = intentReferencedKeys()
        val trulyUnreferenced = QaCoverageKeyCatalog.all()
            .map { it.key }
            .filterNot { it in referenced }
            .toSet()
        assertEquals(knownUnreferencedKeys, trulyUnreferenced)
    }

    @Test
    fun `normalizeAndValidate keeps existing key order and appends new keys at the end`() {
        // I-4: 新 catalog 条目只能追加在列表末尾；normalizeAndValidate 按声明顺序返回，
        // 中插会改变既有规则再次保存时 coverage_keys 的字符串顺序。
        assertEquals(
            listOf("programme.purpose", "fees.policy"),
            QaCoverageKeyCatalog.normalizeAndValidate(listOf("programme.purpose", "fees.policy"))
        )
        val order = QaCoverageKeyCatalog.all().map { it.key }
        val sponsorIndex = order.indexOf("governance.sponsor_level")
        assertTrue(order.indexOf("work.time_commitment") > sponsorIndex)
        assertTrue(order.indexOf("work.advisory_duration") > sponsorIndex)
    }

    @Test
    fun `rule carrying application required materials is assigned to next stages intent`() {
        // I-5/IP-5 可达性: application.next_stages 新增 alternativeCoverageKeys 后，
        // 携带 application.required_materials 的规则不再被 isCoverageEligible /
        // selectIntentKeyForRule 丢弃（落地前返回 null）。
        val rule = QaRule(
            id = 1,
            categoryId = 1,
            keywords = "next,steps",
            replySubject = null,
            replyBody = "The next steps are provided in the application guide.",
            answerBody = "The next steps are provided in the application guide.",
            replyPolicy = QaReplyPolicy.AUTO.name,
            coverageKeys = "application.required_materials"
        )
        val nextStages = AiReplyIntentCatalog.definitions.single { it.key == "application.next_stages" }
        val assigned = AiReplyIntentCatalog.assignRulesToIntents(listOf(rule), listOf(nextStages))
        assertEquals(listOf(rule), assigned["application.next_stages"])
    }

    @Test
    fun `every intent reachable coverage key is owned by at least one migration seeded rule`() {
        // 计划 11 (T-3): I-2 的对称缺陷——intent 认得出问题，却没有任何事实能供证，
        // 该 intent 恒 MISSING 且不体现为任何报错。V109 占用 6 个"可达但无主"键，
        // 剩 publication.authorship 显式留空（本轮无经需求方确认的权威正文）。
        val migrationDir = Path.of("src/main/resources/db/migration")
        val ownedKeys = mutableSetOf<String>()
        Files.newDirectoryStream(migrationDir, "*.sql").use { stream ->
            stream.sorted().forEach { file ->
                ownedKeys += coverageKeysOwnedBy(Files.readString(file))
            }
        }
        val unowned = (intentReferencedKeys() - knownUnreferencedKeys) - ownedKeys
        assertEquals(
            knownUnownedKeys,
            unowned,
            "intent-reachable keys with no migration-seeded rule: $unowned"
        )
    }

    // 提取单份迁移里被赋给某条规则的 coverage keys：
    // 1) coverage_keys = '...' 赋值（UPDATE 与 INSERT 的 SET 均适用）；
    // 2) INSERT 内联覆盖键列——列清单含 coverage_keys 的 INSERT 语句，
    //    其取值是 SELECT 值列表中紧跟 WHERE NOT EXISTS 之前的最后一个字面量。
    // 与计划 11 T-2 断言 7 的提取口径一致（按语句切分、先剥离行注释）。
    private fun coverageKeysOwnedBy(sql: String): Set<String> {
        val clean = sql.lineSequence().joinToString("\n") { it.replace(Regex("--.*"), "") }
        val owned = mutableSetOf<String>()

        Regex("coverage_keys\\s*=\\s*'([^']*)'").findAll(clean).forEach { m ->
            owned += m.groupValues[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }

        clean.split(Regex(";\\s*\n")).forEach { stmt ->
            if ("INSERT INTO qa_rule" !in stmt) return@forEach
            val cols = Regex("\\(([^)]*)\\)\\s*(SELECT|VALUES)").find(stmt)
                ?.groupValues?.get(1) ?: return@forEach
            if ("coverage_keys" !in cols) return@forEach
            val lines = stmt.lines()
            for (i in lines.indices) {
                if ("WHERE NOT EXISTS" in lines[i] && i > 0) {
                    val literals = Regex("'([^']*)'").findAll(lines[i - 1]).toList()
                    if (literals.isNotEmpty()) {
                        owned += literals.last().groupValues[1]
                            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    }
                }
            }
        }
        return owned
    }
}
