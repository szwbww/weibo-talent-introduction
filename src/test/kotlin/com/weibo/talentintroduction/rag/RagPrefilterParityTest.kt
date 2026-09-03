package com.weibo.talentintroduction.rag

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.rag.domain.RagFact
import com.weibo.talentintroduction.rag.domain.RagIntentCoverage
import com.weibo.talentintroduction.rag.domain.RagMandatoryRule
import com.weibo.talentintroduction.rag.domain.RagPhraseGroup
import com.weibo.talentintroduction.rag.domain.RagPrefilterExclusion
import com.weibo.talentintroduction.rag.service.RagCorpusSnapshot
import com.weibo.talentintroduction.rag.service.RagMandatoryResolver
import com.weibo.talentintroduction.rag.service.RagPhraseMatcher
import com.weibo.talentintroduction.rag.service.RagPrefilterService
import com.weibo.talentintroduction.rag.service.RagProcessContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * 计划 02 (T5): 确定性检索层平价测试。
 *
 * 语料由 `scripts/dump_rag_parity_fixtures.py` 机器生成（deterministic：重跑不改变
 * fixtures.json）—— 每封来信的 expected 三项输出 = 脚本 `prefilter_facts` /
 * `mandatory_fact_ids`（含 D-3 补丁）/ `requested_coverage_keys` 在同一来信上的
 * 输出。本类用 `corpus` 段在内存重建 `RagCorpusSnapshot`（与 V112 种子行同序同义），
 * 断言 Kotlin 确定性层逐字复现每一条期望 —— 不依赖数据库（环境无库；见
 * execution.md §Deviations）。
 *
 * 覆盖: I-7 ~ I-12；D-3 的 8 行登记（[compensationMandatoryIsTheOnlyDeliberateDeviation]）；
 * I-9 日本教授完整样例；I-8 顺序白盒登记；I-12 CV 四条件；A-5 KB-APP-017 永不出现。
 */
class RagPrefilterParityTest {

    // ------------------------------------------------------------------
    // fixture 装载（companion 共享；每个用例类实例复用同一不可变模型）
    // ------------------------------------------------------------------

    companion object {
        private const val FIXTURE_PATH = "src/test/resources/rag-parity/fixtures.json"
        private const val EXPECTED_FINGERPRINT = "e62421a42c432cf3"

        private val document: JsonNode = ObjectMapper().readTree(
            Files.readString(Path.of(FIXTURE_PATH))
        )
        private val snapshot: RagCorpusSnapshot = parseSnapshot(document.get("corpus"))
        private val cases: List<FixtureCase> = parseCases(document.get("cases"))

        // 实测基线 8 行（计划表逐字；id 指向 fixture case）。
        private val planRows: List<PlanRow> = listOf(
            PlanRow("scenario-1-compensation", listOf("KB-FUND-033"), true, false),
            PlanRow("scenario-2-salary", listOf("KB-FUND-033"), true, false),
            PlanRow("scenario-3-remuneration", listOf("KB-FUND-033"), true, false),
            PlanRow(
                "scenario-4-detail-compensation",
                listOf("KB-PROG-002", "KB-FUND-033"), true, false
            ),
            PlanRow("scenario-5-compensation-government-funding", listOf("KB-FUND-033"), true, false),
            PlanRow("scenario-6-compensation-structure", listOf("KB-FUND-033"), true, false),
            PlanRow(
                "scenario-7-official-name",
                listOf("KB-PROG-003", "KB-COMP-007"), false, false
            ),
            PlanRow(
                "real-mockup-japanese-professor-full",
                listOf(
                    "KB-PROG-002", "KB-FUND-033", "KB-PROG-003", "KB-GOV-004",
                    "KB-COMP-007", "KB-IP-039", "KB-CONF-036"
                ),
                true, false
            )
        )

        private fun parseSnapshot(corpus: JsonNode): RagCorpusSnapshot {
            val facts = corpus.get("facts").map { fact ->
                RagFact(
                    factCode = fact.get("factCode").asText(),
                    area = fact.get("area").asText(),
                    seq = fact.get("seq").asInt(),
                    title = fact.get("title").asText(),
                    category = fact.get("category").asText(),
                    questionVariants = fact.get("questionVariants").asText(),
                    keywords = fact.get("keywords").asText(),
                    answer = fact.get("answer").asText(),
                    coverageKeys = fact.get("coverageKeys").asText(),
                    replyPolicy = fact.get("replyPolicy").asText(),
                    status = fact.get("status").asText(),
                    riskLevel = fact.get("riskLevel").asText(),
                    renderMode = fact.get("renderMode").asText(),
                    sourceRefs = fact.get("sourceRefs").asText(),
                    legacyRuleId = if (fact.get("legacyRuleId").isNull) {
                        null
                    } else {
                        fact.get("legacyRuleId").asLong()
                    },
                    enabled = fact.get("enabled").asBoolean(),
                    sortOrder = fact.get("sortOrder").asInt()
                )
            }
            val phraseGroups = corpus.get("phraseGroups").map { group ->
                RagPhraseGroup(
                    groupCode = group.get("groupCode").asText(),
                    phrase = group.get("phrase").asText(),
                    sortOrder = group.get("sortOrder").asInt()
                )
            }
            val intentCoverage = corpus.get("intentCoverage").map { row ->
                RagIntentCoverage(
                    groupCode = row.get("groupCode").asText(),
                    coverageKey = row.get("coverageKey").asText(),
                    sortOrder = row.get("sortOrder").asInt()
                )
            }
            val mandatoryRules = corpus.get("mandatoryRules").map { rule ->
                RagMandatoryRule(
                    ruleCode = rule.get("ruleCode").asText(),
                    matchGroups = splitCsv(rule.get("matchGroups").asText()),
                    factCodes = splitCsv(rule.get("factCodes").asText()),
                    sortOrder = rule.get("sortOrder").asInt()
                )
            }
            val exclusions = corpus.get("exclusions").map { row ->
                RagPrefilterExclusion(
                    ruleCode = row.get("ruleCode").asText(),
                    whenGroups = splitCsv(row.get("whenGroups").asText()),
                    unlessGroups = splitCsv(row.get("unlessGroups").asText()),
                    targetType = row.get("targetType").asText(),
                    targetValue = row.get("targetValue").asText()
                )
            }
            return RagCorpusSnapshot(
                facts = facts,
                phraseGroups = phraseGroups,
                intentCoverage = intentCoverage,
                mandatoryRules = mandatoryRules,
                exclusions = exclusions,
                fingerprint = corpus.get("fingerprint").asText()
            )
        }

        private fun parseCases(node: JsonNode): List<FixtureCase> = node.map { c ->
            val expected = c.get("expected")
            FixtureCase(
                id = c.get("id").asText(),
                kind = c.get("kind").asText(),
                label = c.get("label").asText(),
                email = c.get("email").asText(),
                expectedMandatory = expected.get("mandatory").map { it.asText() },
                expectedRequested = expected.get("requested").map { it.asText() },
                expectedPrefilter = expected.get("prefilter").map { it.asText() }
            )
        }

        private fun splitCsv(raw: String): List<String> =
            raw.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
    }

    private data class FixtureCase(
        val id: String,
        val kind: String,
        val label: String,
        val email: String,
        val expectedMandatory: List<String>,
        val expectedRequested: List<String>,
        val expectedPrefilter: List<String>
    )

    /** 计划 02 实测基线表的 8 行登记（D-3 专用；期望值逐字来自计划表）。 */
    private data class PlanRow(
        val fixtureId: String,
        val mandatory: List<String>,
        val expect033InPrefilter: Boolean,
        val expect036InPrefilter: Boolean
    )

    private val resolver = RagMandatoryResolver()
    private val service = RagPrefilterService()
    private val matcher = RagPhraseMatcher()

    private fun caseById(id: String): FixtureCase =
        cases.firstOrNull { it.id == id }
            ?: error("fixture case '$id' missing from $FIXTURE_PATH")

    private fun kotlinPrefilterCodes(email: String): List<String> =
        service.prefilter(snapshot, email, null).map { it.factCode }

    // ------------------------------------------------------------------
    // 平价整体：每一条 fixture 的三项输出与 Kotlin 逐字相同
    // ------------------------------------------------------------------

    @Test
    fun `parity with spike-derived fixtures is exact for every case`() {
        // 语料规模：8 个实测基线场景（rows 1-7 + row 8 日本教授完整样例）全部就位，
        // 外加 available 的真实来信。计划 ≥28 条的 real 语料因历史 mail_record 数据
        // 在本环境不可达而登记为环境阻塞（execution.md §Deviations），故此处只钉
        // 死「8 个场景 + 至少 1 封真实来信」的存在性，不钉死 28。
        val scenarioIds = (1..7).map { "scenario-$it-" }.map { prefix ->
            cases.first { it.id.startsWith(prefix) }.id
        }
        assertEquals(7, scenarioIds.size)
        assertTrue(cases.any { it.id == "real-mockup-japanese-professor-full" })
        assertTrue(cases.any { it.kind == "real" })
        assertEquals(EXPECTED_FINGERPRINT, snapshot.fingerprint)
        assertEquals(45, snapshot.facts.size)

        assertTrue(cases.size >= 10, "fixture cases count changed unexpectedly: ${cases.size}")
        cases.forEach { case ->
            val mandatory = resolver.resolve(snapshot, case.email)
            val requested = service.requestedCoverageKeys(snapshot, case.email, null)
            val prefilter = kotlinPrefilterCodes(case.email)
            assertEquals(
                case.expectedMandatory, mandatory,
                "mandatory mismatch for ${case.id} (${case.label})"
            )
            assertEquals(
                case.expectedRequested, requested,
                "requested coverage keys mismatch for ${case.id} (${case.label})"
            )
            assertEquals(
                case.expectedPrefilter, prefilter,
                "prefilter mismatch for ${case.id} (${case.label})"
            )
            assertTrue(prefilter.size <= 18, "prefilter must be capped at 18: ${case.id}")
            // I-2 / A-5: 停用事实（KB-APP-017）在全部输出中一次都不出现。
            listOf(mandatory, requested, prefilter).forEach { output ->
                assertFalse(
                    "KB-APP-017" in output,
                    "disabled fact KB-APP-017 leaked into outputs for ${case.id}"
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // D-3：唯一刻意偏离的 8 行登记
    // ------------------------------------------------------------------

    @Test
    fun compensationMandatoryIsTheOnlyDeliberateDeviation() {
        // I-11: 除 rag_mandatory_rule 中 sort_order=15 的 COMPENSATION -> KB-FUND-033
        // 一行外，确定性层行为与脚本逐字相同。本方法把实测基线 8 行的期望值（计划表
        // 逐字）与 ① fixture 机器输出 ② Kotlin 输出 三方互证 —— 任一侧漂移即红。
        planRows.forEach { row ->
            val case = caseById(row.fixtureId)
            // ① fixture（脚本 + D-3 补丁的机器输出）必须等于计划表登记值。
            assertEquals(
                row.mandatory, case.expectedMandatory,
                "fixture mandatory deviates from the plan table for ${row.fixtureId}"
            )
            // ② Kotlin 必须与计划表一致。
            val mandatory = resolver.resolve(snapshot, case.email)
            assertEquals(row.mandatory, mandatory, "Kotlin mandatory deviates for ${row.fixtureId}")
            val prefilter = kotlinPrefilterCodes(case.email)
            assertEquals(
                row.expect033InPrefilter, "KB-FUND-033" in prefilter,
                "033 命中 column mismatch for ${row.fixtureId}"
            )
            assertEquals(
                row.expect036InPrefilter, "KB-FUND-036" in prefilter,
                "036 命中 column mismatch for ${row.fixtureId}"
            )
        }

        // A-3 / I-11 偏离范围受控：完全不问钱的那行（row 7）强制列表与预筛候选都
        // 不得出现 KB-FUND-033（不会多出薪资段落）。
        val noMoney = caseById("scenario-7-official-name")
        assertFalse("KB-FUND-033" in resolver.resolve(snapshot, noMoney.email))
        assertFalse("KB-FUND-033" in kotlinPrefilterCodes(noMoney.email))
        assertEquals(
            listOf("KB-PROG-003", "KB-COMP-007"),
            resolver.resolve(snapshot, noMoney.email)
        )

        // A-1: 问报酬的三种措辞都必出 KB-FUND-033，且 KB-FUND-036 均未命中。
        listOf("scenario-1-compensation", "scenario-2-salary", "scenario-3-remuneration")
            .forEach { id ->
                val case = caseById(id)
                assertTrue("KB-FUND-033" in resolver.resolve(snapshot, case.email))
                assertTrue("KB-FUND-033" in kotlinPrefilterCodes(case.email))
                assertFalse("KB-FUND-036" in kotlinPrefilterCodes(case.email))
            }
    }

    // ------------------------------------------------------------------
    // I-9: 日本教授完整样例强制顺序（D-3 加入后一字未变）
    // ------------------------------------------------------------------

    @Test
    fun `japanese professor full sample mandatory order is exact and D-3 stable - I-9`() {
        val case = caseById("real-mockup-japanese-professor-full")
        val expected = listOf(
            "KB-PROG-002", "KB-FUND-033", "KB-PROG-003", "KB-GOV-004",
            "KB-COMP-007", "KB-IP-039", "KB-CONF-036"
        )
        val mandatory = resolver.resolve(snapshot, case.email)
        assertEquals(expected, mandatory, "I-9: 日本教授样例强制列表逐字相等")
        assertEquals(expected, case.expectedMandatory, "fixture 侧同值（脚本无 D-3 亦同）")
        // I-9 的直接证据：033 同时被 DETAIL_INQUIRY(10) 与 COMPENSATION(15) 命中，
        // 有序去重保留首次出现 → 033 停在 PROG-002 之后、绝不在列表末尾。
        assertEquals(1, mandatory.indexOf("KB-FUND-033"))
        // 计划 prose：requested_coverage_keys 共 16 个（机器校验通过）。
        assertEquals(16, case.expectedRequested.size)
        assertEquals(case.expectedRequested, service.requestedCoverageKeys(snapshot, case.email, null))
    }

    // ------------------------------------------------------------------
    // I-8: 顺序不可交换 —— 场景断言 + 白盒登记
    // ------------------------------------------------------------------

    @Test
    fun `more details plus compensation keeps 033 and drops 034 - I-8`() {
        // 「more details + compensation」场景（实测基线 row 4 / fixture scenario-4）：
        // 最终候选必须包含 KB-FUND-033（薪资原文段落）且不包含 KB-FUND-034。
        val case = caseById("scenario-4-detail-compensation")
        val prefilter = kotlinPrefilterCodes(case.email)
        assertTrue("KB-FUND-033" in prefilter, "033 must survive in the final candidates")
        assertFalse("KB-FUND-034" in prefilter, "034 must be excluded when detail inquiry hits")
        assertEquals(
            listOf("KB-PROG-002", "KB-FUND-033"),
            resolver.resolve(snapshot, case.email)
        )
    }

    @Test
    fun `prefilter step order is not commutable - I-8 white-box registration`() {
        // 白盒登记（计划 I-8：「以注释形式记录该顺序为何不可交换，不必真的写反向实现」）。
        //
        // 为什么 ④（强制前置合并）必须在 ③（反向剔除）之后：
        // KB-FUND-033 同时携带 finance.government_funding / finance.additional_support
        // 覆盖键。本场景文本「more details from you, including the compensation」命中
        // COMPENSATION_MENTION 组、未命中 GOVERNMENT_FUNDING_MENTION 组 → 第 ③ 步的
        // COMPENSATION_MENTION 剔除规则（COVERAGE_KEY 行）会剔掉 033（否则只问报酬的
        // 信会把政府经费/额外支持的推销内容一起带出）。033 之所以仍出现在最终候选，
        // 唯一路径是第 ④ 步把它从**全量 enabled 语料**（不取自 selected）前置加回。
        // 若把 ④ 挪到 ③ 之前：033 先被并入候选、随后在 ③ 被剔出，且不再有任何加回
        // 路径 → 薪资段落消失。下面的断言逐条钉死该论证链。
        val case = caseById("scenario-4-detail-compensation")
        val email = case.email

        // (a) 剔除谓词对 033 本身成立（它在错误顺序下会被第 ③ 步剔掉）。
        val matched = matcher.matchedGroups(email, snapshot.phraseGroups).toSet()
        val fact033 = snapshot.facts.first { it.factCode == "KB-FUND-033" }
        val excludedByStep3 = snapshot.exclusions.any { exclusion ->
            exclusion.whenGroups.isNotEmpty() &&
                exclusion.whenGroups.all { it in matched } &&
                exclusion.unlessGroups.none { it in matched } &&
                when (exclusion.targetType) {
                    "FACT_CODE" -> fact033.factCode == exclusion.targetValue
                    "COVERAGE_KEY" -> exclusion.targetValue in fact033.coverageKeys()
                    else -> false
                }
        }
        assertTrue(excludedByStep3, "033 must be a step-3 exclusion target in this scenario")

        // (b) 033 进入最终候选的唯一路径是强制前置合并（DETAIL_INQUIRY 规则）。
        assertTrue("KB-FUND-033" in resolver.resolve(snapshot, email))

        // (c) 正确顺序下 033 在最终候选里（③ 剔除后 ④ 加回）。
        assertTrue("KB-FUND-033" in kotlinPrefilterCodes(email))
    }

    // ------------------------------------------------------------------
    // I-12: CV 请求四条件「与」+ application.required_materials 追加
    // ------------------------------------------------------------------

    @Test
    fun `shouldRequestCv requires all four conditions - I-12`() {
        // 四条件齐备 → true（positiveIntent 由 POSITIVE_INTENT 短语组命中提供）。
        val fullEmail = "I remain interested, and I am willing to continue with this programme. " +
            "What are the next steps and what do you need from me?"
        val fullContext = RagProcessContext(
            expertReplyCount = 2,
            expertTags = emptyList(),
            cvStatus = "MISSING"
        )
        assertTrue(service.shouldRequestCv(snapshot, fullEmail, fullContext))

        // 四个 false 用例：任一条件缺失即 false。
        // ① 轮次 = 1（首封回信不主动索要 CV）
        assertFalse(
            service.shouldRequestCv(
                snapshot, fullEmail,
                fullContext.copy(expertReplyCount = 1)
            )
        )
        // ② CV 已提供（RECEIVED）
        assertFalse(
            service.shouldRequestCv(
                snapshot, fullEmail,
                fullContext.copy(cvStatus = "RECEIVED")
            )
        )
        // ③ 无继续意愿（文本只问下一步，不含 POSITIVE_INTENT 短语，tags 为空）
        val nextStepOnly = "What are the next steps and what do you need from me?"
        assertFalse(service.shouldRequestCv(snapshot, nextStepOnly, fullContext))
        // ④ 未问下一步（有继续意愿但无 NEXT_STEP 短语）
        val positiveOnly = "I remain interested in the programme."
        assertFalse(service.shouldRequestCv(snapshot, positiveOnly, fullContext))

        // 命中时向 requested 末尾追加 application.required_materials（I-12 第二句）。
        // 本文本未命中任何 intent 组 → requested 恰为单元素追加。
        assertEquals(
            listOf("application.required_materials"),
            service.requestedCoverageKeys(snapshot, fullEmail, fullContext)
        )
        // 不命中（四条件缺一）时不得追加。
        assertFalse("application.required_materials" in service.requestedCoverageKeys(snapshot, fullEmail, fullContext.copy(expertReplyCount = 1)))

        // 标签路径：WILLING_TO_CONTINUE 标签可替代文本意愿（与脚本 tags 同语义）。
        val tagged = RagProcessContext(
            expertReplyCount = 2,
            expertTags = listOf("WILLING_TO_CONTINUE"),
            cvStatus = "MISSING"
        )
        assertTrue(service.shouldRequestCv(snapshot, nextStepOnly, tagged))
    }

    // ------------------------------------------------------------------
    // I-2 / A-5: 停用事实永不进候选（语料层断言）
    // ------------------------------------------------------------------

    @Test
    fun `disabled fact KB-APP-017 never enters any output - I-2 A-5`() {
        // 语料中恰一条停用事实，且就是 KB-APP-017（V112 种子语义）。
        val disabled = snapshot.facts.filter { !it.enabled || it.effectiveStatus() == "DISABLED" }
        assertEquals(1, disabled.size, "V112 种子应恰有 1 条停用事实")
        assertEquals("KB-APP-017", disabled.single().factCode)

        // 全部场景的强制列表都不引用停用事实（enabled-only 过滤）。
        cases.forEach { case ->
            assertFalse(
                "KB-APP-017" in resolver.resolve(snapshot, case.email),
                "mandatory must never contain disabled KB-APP-017 for ${case.id}"
            )
        }
    }
}
