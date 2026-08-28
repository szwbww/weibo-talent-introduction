package com.weibo.talentintroduction.llm.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 12-letter-closer 单测（T-4.1 ~ T-4.7）。
 * I-5 的两个 fixture 取自需求方 2026-08-28 提供的 id 21 / id 1 逐字正文
 * （id 21 含 en dash U+2013；id 1 含 ${...} 变量占位符与动作句），不得自造替身。
 */
class AiReplyLetterCloserTest {

    private fun version(
        answerText: String,
        claims: List<AiReplyItemClaim>,
        handling: TrustReplyItemHandling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
        generationKind: TrustReplyItemGenerationKind = TrustReplyItemGenerationKind.AI_GENERATED
    ): TrustReplyItemVersion = TrustReplyItemVersion(
        versionId = "v",
        requestKey = "k",
        handling = handling,
        answerText = answerText,
        claims = claims,
        model = "DEEPSEEK_V4_FLASH",
        generationKind = generationKind,
        evidenceSetVersion = "e",
        sourceVersion = "s"
    )

    /** id 21 `Meeting arrangement` 正文逐字（两段，`15–20` 为 en dash U+2013）。 */
    private val meetingArrangementBody: String =
        "We would like to arrange a brief Zoom meeting to learn more about your professional background " +
            "and research interests, and to introduce ourselves briefly.\n\n" +
            "The meeting will take approximately 15–20 minutes. Could you please let us know when you " +
            "would be available? We will arrange the meeting according to your time zone."

    /** id 1 `About the talent program` 正文要素逐字（两个 ${...} 占位符 + 末段动作句）。 */
    private val aboutTalentProgramBody: String =
        "Thank you for your interest in our talent program. " +
            "The program covers \${researchFields|your field} and values \${recentWorkTitle|your recent research}. " +
            "Would you be open to learning more about the program and the possible cooperation format?"

    // ── T-4.1 (I-2): 相同 sourceRuleIds → 只出现一次，保留首次出现 ──────────────

    @Test
    fun `same source rule ids collapse to one and keep the first occurrence`() {
        val first = version(
            "First wording of the shared fact.",
            listOf(AiReplyItemClaim("application.steps", "First wording of the shared fact.", listOf(9L)))
        )
        val second = version(
            "Second wording of the same fact.",
            listOf(AiReplyItemClaim("application.steps", "Second wording of the same fact.", listOf(9L)))
        )

        val result = AiReplyLetterCloser.close(listOf(first, second), emptySet())

        assertEquals(listOf("First wording of the shared fact."), result)
    }

    // ── T-4.2 (I-2 例外): 空 sourceRuleIds 不参与去重，一律保留 ─────────────────

    @Test
    fun `claims without source rule ids are all kept`() {
        val one = version(
            "No basis claim one.",
            listOf(AiReplyItemClaim("general.answer", "No basis claim one.", emptyList()))
        )
        val two = version(
            "No basis claim two.",
            listOf(AiReplyItemClaim("general.answer", "No basis claim two.", emptyList()))
        )

        val result = AiReplyLetterCloser.close(listOf(one, two), emptySet())

        assertEquals(listOf("No basis claim one. No basis claim two."), result)
    }

    // ── T-4.3 (I-3): 主题归并 —— 主题序 = 首次出现序，段内 = canonical order ────

    @Test
    fun `topic grouping keeps first occurrence order and canonical order inside`() {
        val step = version(
            "Application steps text.",
            listOf(AiReplyItemClaim("application.steps", "Application steps text.", listOf(1L)))
        )
        val commitment = version(
            "Work commitment text.",
            listOf(AiReplyItemClaim("work.time_commitment", "Work commitment text.", listOf(2L)))
        )
        val next = version(
            "Next stages text.",
            listOf(AiReplyItemClaim("application.next_stages", "Next stages text.", listOf(3L)))
        )

        val result = AiReplyLetterCloser.close(listOf(step, commitment, next), emptySet())

        assertEquals(
            listOf("Application steps text. Next stages text.", "Work commitment text."),
            result
        )
    }

    // ── T-4.4 (I-4): 只保留最后一处动作句；保留处未授权则整体移除 ───────────────

    @Test
    fun `only the last action sentence survives`() {
        val materials = version(
            "Please send your CV.",
            listOf(AiReplyItemClaim("application.materials", "Please send your CV.", listOf(9L)))
        )
        val meeting = version(
            "Could we schedule a call?",
            listOf(AiReplyItemClaim("meeting.arrange", "Could we schedule a call?", listOf(10L)))
        )

        val result = AiReplyLetterCloser.close(
            listOf(materials, meeting),
            setOf(AiReplyAction.PROPOSE_MEETING)
        )

        assertEquals(listOf("Could we schedule a call?"), result)

        // I-4 授权判据：保留的动作不在授权集合内 → 整体移除。
        val unauthorized = AiReplyLetterCloser.close(listOf(materials, meeting), emptySet())
        assertEquals(emptyList<String>(), unauthorized)
    }

    // ── T-4.5 (I-5): 冻结事实（id 21）正文自带的动作句不删改、不再加 CTA ────────

    @Test
    fun `meeting arrangement frozen body stays verbatim as the only CTA`() {
        val meeting = version(
            meetingArrangementBody,
            listOf(AiReplyItemClaim("meeting.arrangement", meetingArrangementBody, listOf(21L)))
        )

        val result = AiReplyLetterCloser.close(
            listOf(meeting),
            setOf(AiReplyAction.PROPOSE_MEETING)
        )

        // I-5: 该事实的正文一字未删未改，且全信不再另加一处 CTA。
        assertEquals(listOf(meetingArrangementBody), result)
    }

    // ── T-4.6 (I-5): 含 ${...} 的文本不切分、不改写 ─────────────────────────────

    @Test
    fun `placeholder text is never split or rewritten`() {
        val program = version(
            aboutTalentProgramBody,
            listOf(AiReplyItemClaim("application.program", aboutTalentProgramBody, listOf(1L)))
        )
        val meeting = version(
            "Could we schedule a call?",
            listOf(AiReplyItemClaim("meeting.arrange", "Could we schedule a call?", listOf(10L)))
        )

        val result = AiReplyLetterCloser.close(
            listOf(program, meeting),
            setOf(AiReplyAction.PROPOSE_MEETING)
        )

        // 含 ${...} 占位符的段落保持逐字，CTA 收口不得切分或改写它（I-5）。
        assertEquals(listOf(aboutTalentProgramBody, "Could we schedule a call?"), result)
    }

    // ── T-4.7 (I-6): 全部非 AI 生成 → 逃生舱，逐字返回原 orderedAnswers ─────────

    @Test
    fun `all non AI generated versions pass through verbatim`() {
        val operator = version(
            "Operator handwritten answer one.",
            emptyList(),
            handling = TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT,
            generationKind = TrustReplyItemGenerationKind.SAFE_TEMPLATE
        )
        val verbatim = version(
            "Operator handwritten answer two.",
            emptyList(),
            handling = TrustReplyItemHandling.ANSWER_FACTS_VERBATIM,
            generationKind = TrustReplyItemGenerationKind.SAFE_TEMPLATE
        )

        val result = AiReplyLetterCloser.close(listOf(operator, verbatim), emptySet())

        assertEquals(
            listOf("Operator handwritten answer one.", "Operator handwritten answer two."),
            result
        )
    }
}
