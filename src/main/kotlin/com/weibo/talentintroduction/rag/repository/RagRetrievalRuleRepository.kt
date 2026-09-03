package com.weibo.talentintroduction.rag.repository

import com.weibo.talentintroduction.rag.domain.RagIntentCoverage
import com.weibo.talentintroduction.rag.domain.RagMandatoryRule
import com.weibo.talentintroduction.rag.domain.RagPhraseGroup
import com.weibo.talentintroduction.rag.domain.RagPrefilterExclusion
import com.weibo.talentintroduction.rag.domain.RagRetrievalRuleData
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

/**
 * 计划 01 (T3): 四张规则表的一次性整表读出。表都很小且只在启动期读一次
 * （I-6），用 `NamedParameterJdbcTemplate` 直接读，返回不可变列表。
 *
 * 逗号分隔的列（`match_groups` / `fact_codes` / `when_groups` /
 * `unless_groups`）解析语义与 I-4 同构：按 `,` 切分、每段 trim、丢弃空段。
 */
@Repository
class RagRetrievalRuleRepository(
    private val jdbc: NamedParameterJdbcTemplate
) {

    fun loadAll(): RagRetrievalRuleData {
        val phraseGroups = jdbc.query(
            """
            SELECT group_code, phrase, sort_order
              FROM rag_phrase_group
             ORDER BY group_code ASC, sort_order ASC
            """
        ) { rs, _ ->
            RagPhraseGroup(
                groupCode = rs.getString("group_code"),
                phrase = rs.getString("phrase"),
                sortOrder = rs.getInt("sort_order")
            )
        }

        val intentCoverage = jdbc.query(
            """
            SELECT group_code, coverage_key, sort_order
              FROM rag_intent_coverage
             ORDER BY group_code ASC, sort_order ASC
            """
        ) { rs, _ ->
            RagIntentCoverage(
                groupCode = rs.getString("group_code"),
                coverageKey = rs.getString("coverage_key"),
                sortOrder = rs.getInt("sort_order")
            )
        }

        val mandatoryRules = jdbc.query(
            """
            SELECT rule_code, match_groups, fact_codes, sort_order
              FROM rag_mandatory_rule
             ORDER BY sort_order ASC
            """
        ) { rs, _ ->
            RagMandatoryRule(
                ruleCode = rs.getString("rule_code"),
                matchGroups = splitCsv(rs.getString("match_groups")),
                factCodes = splitCsv(rs.getString("fact_codes")),
                sortOrder = rs.getInt("sort_order")
            )
        }

        val exclusions = jdbc.query(
            """
            SELECT rule_code, when_groups, unless_groups, target_type, target_value
              FROM rag_prefilter_exclusion
             ORDER BY rule_code ASC, target_value ASC
            """
        ) { rs, _ ->
            RagPrefilterExclusion(
                ruleCode = rs.getString("rule_code"),
                whenGroups = splitCsv(rs.getString("when_groups")),
                unlessGroups = splitCsv(rs.getString("unless_groups")),
                targetType = rs.getString("target_type"),
                targetValue = rs.getString("target_value")
            )
        }

        return RagRetrievalRuleData(
            phraseGroups = phraseGroups,
            intentCoverage = intentCoverage,
            mandatoryRules = mandatoryRules,
            exclusions = exclusions
        )
    }

    private fun splitCsv(raw: String): List<String> =
        raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
