package com.weibo.talentintroduction.rag

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.rag.domain.RagIntentCoverage
import com.weibo.talentintroduction.rag.domain.RagMandatoryRule
import com.weibo.talentintroduction.rag.domain.RagPhraseGroup
import com.weibo.talentintroduction.rag.domain.RagPrefilterExclusion
import com.weibo.talentintroduction.rag.service.RagConstraintInput
import com.weibo.talentintroduction.rag.service.RagCorpusSnapshot
import com.weibo.talentintroduction.rag.service.RagKnowledgeBase
import com.weibo.talentintroduction.rag.service.RagPromptConfigSaveRequest
import com.weibo.talentintroduction.rag.service.RagPromptConfigService
import com.weibo.talentintroduction.rag.service.RagPromptConstraints
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.namedparam.SqlParameterSource
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 计划 06 (T7): RagPromptConfigService 的机器验收 —— 全 Mockito（NamedParameter
 * JdbcTemplate + RagKnowledgeBase 快照），零 DB、零 docker，普通 mvn test 即跑。
 *
 * 覆盖: I-30（NULL 回落常量逐字 / reset 置 NULL）、I-31（派生三条 derived=true、
 * 保存忽略派生入参、再次 effective 仍现算）、I-32（存储为纯文本数组、
 * 无编号字段）、I-33（保存/恢复写审计：改动下标 + 新旧值、新增、删除、操作人）。
 */
class RagPromptConfigServiceTest {

    private val jdbc = Mockito.mock(NamedParameterJdbcTemplate::class.java)
    private val knowledgeBase = Mockito.mock(RagKnowledgeBase::class.java)
    private val objectMapper = ObjectMapper()
    private val service = RagPromptConfigService(jdbc, knowledgeBase, objectMapper)

    // ------------------------------------------------------------------
    // fixture：与 V112 种子同语义的 rag_mandatory_rule 六行
    // （DETAIL_INQUIRY / COMPENSATION / PROGRAMME_NAME / GOVERNMENT_ORGANIZATION /
    //  {PROGRAMME_NAME, GOVERNMENT_ORGANIZATION} / IP）。
    // ------------------------------------------------------------------

    private val fixtureRules = listOf(
        rule("R_DETAIL", listOf("DETAIL_INQUIRY"), listOf("KB-PROG-002", "KB-FUND-033"), 10),
        rule("R_COMP", listOf("COMPENSATION"), listOf("KB-FUND-033"), 15),
        rule("R_PROG", listOf("PROGRAMME_NAME"), listOf("KB-PROG-003"), 20),
        rule("R_GOV", listOf("GOVERNMENT_ORG"), listOf("KB-GOV-004"), 30),
        rule("R_EVID", listOf("PROGRAMME_NAME", "GOVERNMENT_ORGANIZATION"), listOf("KB-COMP-007"), 40),
        rule("R_IP", listOf("IP"), listOf("KB-IP-039", "KB-CONF-036"), 50)
    )

    private fun rule(code: String, groups: List<String>, codes: List<String>, sortOrder: Int) =
        RagMandatoryRule(ruleCode = code, matchGroups = groups, factCodes = codes, sortOrder = sortOrder)

    private fun stubSnapshot(mandatoryRules: List<RagMandatoryRule> = emptyList()) {
        Mockito.`when`(knowledgeBase.snapshot()).thenReturn(
            RagCorpusSnapshot(
                facts = emptyList(),
                phraseGroups = emptyList<RagPhraseGroup>(),
                intentCoverage = emptyList<RagIntentCoverage>(),
                mandatoryRules = mandatoryRules,
                exclusions = emptyList<RagPrefilterExclusion>(),
                fingerprint = "test-fp"
            )
        )
    }

    /** storedRow() 查询的返回行：null 值键省略（service 对缺失键读 null）。 */
    private fun rowMap(retrievalJson: String?, generationJson: String?, updatedBy: String? = null): Map<String, Any> {
        val map = LinkedHashMap<String, Any>()
        retrievalJson?.let { map["retrieval_constraints"] = it }
        generationJson?.let { map["generation_constraints"] = it }
        map["updated_at"] = Timestamp.valueOf(LocalDateTime.of(2026, 9, 2, 10, 0))
        updatedBy?.let { map["updated_by"] = it }
        return map
    }

    private fun stubStoredRows(vararg rows: Map<String, Any>) {
        // 依次返回：save() 头部读旧行、尾部 effective() 读新行（多余调用复用末值）。
        // Java 泛型（List<Map<String,Object>>）在 Kotlin 侧为平台类型，用 doReturn 免去泛型推导。
        @Suppress("UNCHECKED_CAST")
        val stubbing = Mockito.doReturn(listOf(rows.first()) as Any)
        rows.drop(1).forEach { row ->
            stubbing.doReturn(listOf(row) as Any)
        }
        stubbing.`when`(jdbc).queryForList(
            Mockito.anyString(),
            Mockito.any(MapSqlParameterSource::class.java)
        )
    }

    private fun stubSuccessfulUpdates() {
        Mockito.`when`(jdbc.update(Mockito.anyString(), Mockito.any(MapSqlParameterSource::class.java)))
            .thenReturn(1)
    }

    private fun encode(values: List<String>): String = objectMapper.writeValueAsString(values)

    private fun capturedUpdates(): Pair<List<String>, List<SqlParameterSource>> {
        val sqlCaptor = ArgumentCaptor.forClass(String::class.java)
        val paramCaptor = ArgumentCaptor.forClass(SqlParameterSource::class.java)
        Mockito.verify(jdbc, Mockito.atLeastOnce()).update(sqlCaptor.capture(), paramCaptor.capture())
        return sqlCaptor.allValues to paramCaptor.allValues
    }

    // ------------------------------------------------------------------
    // I-30
    // ------------------------------------------------------------------

    @Test
    fun `I-30 NULL 行回落到 RagPromptConstraints 常量逐字相同且 isCustom=false`() {
        stubSnapshot() // 无强制规则行 → 派生槽位回落常量占位文本
        stubStoredRows(rowMap(retrievalJson = null, generationJson = null))

        val effective = service.effective()

        assertFalse(effective.isCustom)
        assertEquals(
            RagPromptConstraints.RETRIEVAL_RULES,
            effective.retrieval.map { it.text }
        )
        assertEquals(
            RagPromptConstraints.GENERATION_RULES,
            effective.generation.map { it.text }
        )
        assertEquals(RagPromptConstraints.RETRIEVAL_SYSTEM_HEAD, effective.retrievalSystemHead)
        assertEquals(RagPromptConstraints.GENERATION_SYSTEM_HEAD, effective.generationSystemHead)
        // 派生三条 derived=true（第 18/19/21 条，0-based 17/18/20）。
        assertEquals(22, effective.generation.size)
        effective.generation.forEachIndexed { index, view ->
            assertEquals(index in setOf(17, 18, 20), view.derived, "derived flag at $index")
        }
        // provenance：默认视图第 12 条标「本次改动」、第 22 条标「新增」（A-1）。
        assertTrue(effective.generation[11].changed)
        assertTrue(effective.generation[21].added)
    }

    @Test
    fun `I-30 reset 把两列置 NULL（不是 JSON 快照）并留审计`() {
        stubSnapshot()
        stubSuccessfulUpdates()
        // 第一次读：置 NULL 前的旧行（审计 before）；第二次读（effective 尾部）：已清空。
        stubStoredRows(
            rowMap(
                retrievalJson = encode(listOf("A", "B")),
                generationJson = encode(listOf("G1", "G2"))
            ),
            rowMap(retrievalJson = null, generationJson = null)
        )

        val effective = service.resetToDefault("alice")

        val (sqls, params) = capturedUpdates()
        val configUpdate = sqls.indexOfFirst { it.contains("UPDATE rag_prompt_config") }
        assertTrue(configUpdate >= 0, "config update must run")
        // I-30: 置 NULL 走 SQL 字面量，不带 JSON 快照参数（列参数不存在）。
        assertTrue(sqls[configUpdate].contains("retrieval_constraints = NULL"), "must clear retrieval to NULL")
        assertTrue(sqls[configUpdate].contains("generation_constraints = NULL"), "must clear generation to NULL")
        assertEquals("alice", params[configUpdate].getValue("operator"))
        val auditIndex = sqls.indexOfFirst { it.contains("INSERT INTO operator_action_log") }
        assertTrue(auditIndex >= 0, "audit insert must run")
        assertEquals("RESET_RAG_PROMPT_CONFIG", params[auditIndex].getValue("actionType"))
        assertEquals("RAG_PROMPT_CONFIG", params[auditIndex].getValue("targetType"))
        assertEquals("alice", params[auditIndex].getValue("operator"))
        assertFalse(effective.isCustom)
    }

    // ------------------------------------------------------------------
    // I-31
    // ------------------------------------------------------------------

    @Test
    fun `I-31 派生三条按规则表现算（含真实令牌），非常量占位文本`() {
        stubSnapshot(fixtureRules)
        stubStoredRows(rowMap(retrievalJson = null, generationJson = null))

        val effective = service.effective()

        assertEquals(22, effective.generation.size)
        assertTrue(effective.generation[17].text.contains("{{FACT:KB-PROG-002}} and {{FACT:KB-FUND-033}}"))
        assertTrue(effective.generation[18].text.contains("{{FACT:KB-COMP-007}}"))
        assertTrue(effective.generation[20].text.contains("{{FACT:KB-IP-039}}"))
        assertTrue(effective.generation[17].derived && effective.generation[18].derived && effective.generation[20].derived)
        // 规则表变了派生文本必须跟着变（A-4）：与常量占位不同。
        assertTrue(effective.generation[17].text != RagPromptConstraints.GENERATION_RULES[17])
    }

    @Test
    fun `I-31 save 忽略被改写的派生三条：入库 19 条、无编号字段、再次 effective 仍现算`() {
        stubSnapshot(fixtureRules)
        stubSuccessfulUpdates()

        // UI 全量 22 条回传：派生三条（0-based 17/18/20）带被改写的文本。
        val generationInput = (0 until 22).map { index ->
            if (index in setOf(17, 18, 20)) {
                RagConstraintInput(text = "TAMPERED DERIVED $index", derived = true)
            } else {
                RagConstraintInput(text = "custom gen $index", derived = false)
            }
        }
        val retrievalInput = listOf(RagConstraintInput(text = "R1", derived = false))
        stubStoredRows(
            rowMap(retrievalJson = null, generationJson = null),
            rowMap(
                retrievalJson = encode(listOf("R1")),
                generationJson = encode((0 until 22).filter { it !in setOf(17, 18, 20) }.map { "custom gen $it" })
            )
        )

        val effective = service.save(
            RagPromptConfigSaveRequest(
                retrieval = retrievalInput,
                generation = generationInput,
                operator = "alice"
            )
        )

        val (sqls, params) = capturedUpdates()
        val configUpdate = sqls.indexOfFirst { it.contains("UPDATE rag_prompt_config") }
        val storedGeneration = objectMapper.readValue(
            params[configUpdate].getValue("generation") as String,
            Array<String>::class.java
        ).toList()
        assertEquals(19, storedGeneration.size, "22 - 3 derived must be stored")
        assertFalse(storedGeneration.any { it.contains("TAMPERED") }, "derived text must never be stored")
        assertTrue(storedGeneration.none { it.contains("derived") || it.contains("\"no\"") || it.contains("\"index\"") },
            "I-32: stored entries are plain strings without no/index fields")
        assertEquals(listOf("R1"), params[configUpdate].getValue("retrieval")?.let {
            objectMapper.readValue(it as String, Array<String>::class.java).toList()
        })
        assertTrue(effective.isCustom)
        // 再次 effective：派生三条仍是现算值（含令牌），不是保存时被改写的内容。
        assertTrue(effective.generation[17].text.contains("{{FACT:KB-PROG-002}} and {{FACT:KB-FUND-033}}"))
        assertFalse(effective.generation[17].text.contains("TAMPERED"))
        assertTrue(effective.generation[20].derived)
    }

    // ------------------------------------------------------------------
    // I-33
    // ------------------------------------------------------------------

    @Test
    fun `I-33 保存写审计：含改动下标+新旧值、新增、删除与操作人`() {
        stubSnapshot(fixtureRules)
        stubSuccessfulUpdates()
        stubStoredRows(
            rowMap(
                retrievalJson = encode(listOf("old-ret-0")),
                generationJson = encode(listOf("r1", "r2", "r3", "r4"))
            ),
            rowMap(
                retrievalJson = encode(listOf("new-ret-0")),
                generationJson = encode(listOf("r1", "r2", "R3-EDITED", "r4", "r5-NEW"))
            )
        )

        service.save(
            RagPromptConfigSaveRequest(
                retrieval = listOf(RagConstraintInput(text = "new-ret-0")),
                generation = listOf(
                    RagConstraintInput(text = "r1"),
                    RagConstraintInput(text = "r2"),
                    RagConstraintInput(text = "R3-EDITED"),
                    RagConstraintInput(text = "r4"),
                    RagConstraintInput(text = "r5-NEW")
                ),
                operator = "bob"
            )
        )

        val (sqls, params) = capturedUpdates()
        val auditIndex = sqls.indexOfFirst { it.contains("INSERT INTO operator_action_log") }
        assertTrue(auditIndex >= 0, "audit insert must run")
        val audit = params[auditIndex]
        assertEquals("SAVE_RAG_PROMPT_CONFIG", audit.getValue("actionType"))
        assertEquals("bob", audit.getValue("operator"))
        val before = objectMapper.readTree(audit.getValue("before") as String)
        val after = objectMapper.readTree(audit.getValue("after") as String)
        assertEquals("old-ret-0", before.path("retrieval").get(0).asText())
        assertEquals("new-ret-0", after.path("retrieval").get(0).asText())
        val note = objectMapper.readTree(audit.getValue("note") as String)
        val generationDiff = note.path("generation")
        assertEquals("generation", generationDiff.path("segment").asText())
        // changed: 第 3 条（afterIndex 2）r3 → R3-EDITED
        val changed = generationDiff.path("changed")
        assertTrue(changed.size() >= 1)
        val changedEntry = changed.first { it.path("afterIndex").asInt() == 2 }
        assertEquals("r3", changedEntry.path("before").asText())
        assertEquals("R3-EDITED", changedEntry.path("after").asText())
        // added: r5-NEW（afterIndex 4）
        val added = generationDiff.path("added")
        assertEquals("r5-NEW", added.first { it.path("index").asInt() == 4 }.path("text").asText())
        // retrieval 段同样留痕
        val retrievalDiff = note.path("retrieval")
        assertEquals(
            "old-ret-0",
            retrievalDiff.path("changed").first { it.path("afterIndex").asInt() == 0 }.path("before").asText()
        )
    }

    @Test
    fun `I-33 保存含删除时审计记 deleted 段`() {
        stubSnapshot(fixtureRules)
        stubSuccessfulUpdates()
        stubStoredRows(
            rowMap(
                retrievalJson = encode(listOf("keep")),
                generationJson = encode(listOf("r1", "r2", "r3", "r4", "r5"))
            ),
            rowMap(
                retrievalJson = encode(listOf("keep")),
                generationJson = encode(listOf("r1", "r2", "r4", "r5"))
            )
        )

        service.save(
            RagPromptConfigSaveRequest(
                retrieval = listOf(RagConstraintInput(text = "keep")),
                generation = listOf("r1", "r2", "r4", "r5").map { RagConstraintInput(text = it) },
                operator = "bob"
            )
        )

        val (sqls, params) = capturedUpdates()
        val auditIndex = sqls.indexOfFirst { it.contains("INSERT INTO operator_action_log") }
        val note = objectMapper.readTree(params[auditIndex].getValue("note") as String)
        val deleted = note.path("generation").path("deleted")
        assertEquals(1, deleted.size(), "one deletion expected")
        assertEquals("r3", deleted.get(0).path("text").asText())
        assertEquals(2, deleted.get(0).path("index").asInt(), "deleted index in the before list")
    }
}
