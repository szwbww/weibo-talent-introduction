package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.llm.service.AiReplyDraftReadiness
import com.weibo.talentintroduction.llm.service.AiReplyDraftResult
import com.weibo.talentintroduction.llm.service.AiReplyGenerationState
import com.weibo.talentintroduction.llm.service.AiReplyMode
import com.weibo.talentintroduction.llm.service.RequestFactItem
import com.weibo.talentintroduction.llm.service.RequestGroundingStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoReplyConfidenceScorerTest {

    private val scorer = AutoReplyConfidenceScorer()

    private fun draft(
        facts: List<RequestFactItem> = emptyList(),
        readiness: AiReplyDraftReadiness = AiReplyDraftReadiness.READY,
        warnings: List<String> = emptyList()
    ) = AiReplyDraftResult(
        draftText = "Draft",
        usedLlm = true,
        qaRuleIds = listOf(1L),
        mode = AiReplyMode.QA_GROUNDED,
        generationState = AiReplyGenerationState.LLM_USED,
        draftReadiness = readiness,
        contextWarnings = warnings,
        requestFacts = facts
    )

    private fun fact(
        index: Int,
        status: RequestGroundingStatus,
        factRuleIds: List<Long> = listOf(1L)
    ) = RequestFactItem(
        index = index,
        requestText = "Request $index",
        factRuleIds = factRuleIds,
        status = status
    )

    @Test
    fun `full grounded ready draft with zero warnings and full evidence scores 92`() {
        val result = scorer.score(
            draft(
                facts = listOf(
                    fact(1, RequestGroundingStatus.GROUNDED),
                    fact(2, RequestGroundingStatus.GROUNDED),
                    fact(3, RequestGroundingStatus.GROUNDED),
                    fact(4, RequestGroundingStatus.GROUNDED)
                )
            ),
            verifiedRuleIds = listOf(1L)
        )

        assertEquals(40.0, result.coverageScore, 0.001)
        assertEquals(25.0, result.evidenceScore, 0.001)
        assertEquals(20.0, result.consistencyScore, 0.001)
        assertEquals(7.0, result.historyScore, 0.001)
        assertEquals(92.0, result.crs, 0.001)
    }

    @Test
    fun `four request sample with mixed grounding scores coverage 25_5`() {
        val result = scorer.score(
            draft(
                facts = listOf(
                    fact(1, RequestGroundingStatus.GROUNDED),
                    fact(2, RequestGroundingStatus.PARTIAL),
                    fact(3, RequestGroundingStatus.UNSUPPORTED),
                    fact(4, RequestGroundingStatus.PARTIAL)
                )
            ),
            verifiedRuleIds = listOf(1L)
        )

        assertEquals(25.5, result.coverageScore, 0.001)
        assertEquals(1, result.unsupportedCount)
        assertEquals(2, result.partialCount)
    }

    @Test
    fun `empty requestFacts yields zero coverage and a finite non NaN crs`() {
        val result = scorer.score(draft(facts = emptyList()), verifiedRuleIds = listOf(1L))

        assertEquals(0.0, result.coverageScore, 0.001)
        assertEquals(0.0, result.evidenceScore, 0.001)
        assertEquals(0, result.requestCount)
        assertTrue(result.crs.isFinite(), "crs must not be NaN")
        assertTrue(result.crs in 0.0..100.0, "crs must stay in [0, 100] but was ${result.crs}")
    }

    @Test
    fun `BLOCKED readiness zeroes consistency`() {
        val result = scorer.score(
            draft(facts = listOf(fact(1, RequestGroundingStatus.GROUNDED)), readiness = AiReplyDraftReadiness.BLOCKED),
            verifiedRuleIds = listOf(1L)
        )

        assertEquals(0.0, result.consistencyScore, 0.001)
    }

    @Test
    fun `empty verifiedRuleIds zeroes evidence`() {
        val result = scorer.score(
            draft(facts = listOf(fact(1, RequestGroundingStatus.GROUNDED))),
            verifiedRuleIds = emptyList()
        )

        assertEquals(0.0, result.evidenceScore, 0.001)
        assertEquals(0, result.verifiedRuleCount)
    }

    @Test
    fun `extreme inputs clamp crs into 0 to 100`() {
        val result = scorer.score(
            draft(
                facts = listOf(fact(1, RequestGroundingStatus.UNSUPPORTED, factRuleIds = emptyList())),
                readiness = AiReplyDraftReadiness.BLOCKED,
                warnings = List(10) { "WARNING_$it" }
            ),
            verifiedRuleIds = emptyList()
        )

        // 10 warnings drive consistency below zero before the floor clamps it.
        assertEquals(0.0, result.consistencyScore, 0.001)
        assertTrue(result.crs >= 0.0, "crs must never be negative but was ${result.crs}")
        assertTrue(result.crs <= 100.0, "crs must never exceed 100 but was ${result.crs}")
    }
}
