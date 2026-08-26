package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.llm.service.TrustReplySourceType.TRAINING_MAIL
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.service.ReplyFrameSelection
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import com.weibo.talentintroduction.reply.service.ResolvedReplyFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

/**
 * 计划 03 (T3.3): 机器建议说明的整封信编排语义——邻近事实跨条目去重（I-2）、
 * CTA 只在最后一条无据条目出现一次（I-3）、固定措辞无数字/链接/时间承诺（I-4）、
 * 500 字硬预算且绝不截断单个名称（I-5）。通过 bootstrap 的 requestCoverage
 * 观察 toCoverage + suggestedInstructionFor 的组合行为。
 */
class TrustReplySuggestedInstructionTest {

    private val mailRecords = Mockito.mock(MailRecordRepository::class.java)
    private val inboundProcessing = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val contacts = Mockito.mock(ExpertContactRepository::class.java)
    private val trainingQa = Mockito.mock(AiTrainingQaService::class.java)
    private val contextService = Mockito.mock(AiReplyContextService::class.java)
    private val factSelection = Mockito.mock(QaFactSelectionService::class.java)
    private val qaRules = Mockito.mock(QaRuleRepository::class.java)
    private val draftService = Mockito.mock(AiReplyDraftService::class.java)
    private val previewService = Mockito.mock(AiReplyDraftPreviewService::class.java)
    private val auditService = Mockito.mock(AiReplyReviewAuditService::class.java)
    private val pointByPointComposer = Mockito.mock(AiReplyPointByPointComposer::class.java)
    private val claimValidator = Mockito.mock(AiReplyHighRiskClaimValidator::class.java)
    private val stateStore = Mockito.mock(TrustReplyWorkbenchStateStore::class.java)
    private val replySnippetService = Mockito.mock(ReplySnippetService::class.java)
    private val defaultFrame = ResolvedReplyFrame(
        selection = ReplyFrameSelection(
            salutationSnippetId = 1L,
            greetingSnippetId = 2L,
            ackSnippetId = null,
            closingSnippetId = 3L
        ),
        version = "frame-default",
        salutation = "Salutation",
        greeting = "Greeting",
        acknowledgement = null,
        closing = "Closing"
    )

    private lateinit var service: TrustReplyWorkbenchService

    // I-4: 与 TrustReplyWorkbenchService 的过滤正则逐字同源（测试侧复述，
    // 只用于验证建议说明成品不含这些形态）。
    private val domainFormRegex = Regex("""\.[a-zA-Z]{2,}""")
    private val timeCommitmentRegex = Regex(
        "[一二两三四五六七八九十几十半零\\d]+(天|日|周|星期|月|年|小时|分钟|秒)(后|内|之内|以内|前|之前|左右|以后|之后)"
    )
    private val timePromiseTokens = listOf(
        "尽快", "立即", "马上", "今天", "明天", "后天", "本周", "下周", "本月", "下月",
        "周内", "月内", "改天", "稍后", "近日", "小时内", "天内"
    )

    @BeforeEach
    fun setUp() {
        Mockito.reset(
            mailRecords,
            inboundProcessing,
            contacts,
            trainingQa,
            contextService,
            factSelection,
            qaRules,
            draftService,
            previewService,
            auditService,
            pointByPointComposer,
            claimValidator,
            stateStore,
            replySnippetService
        )
        service = TrustReplyWorkbenchService(
            mailRecordRepository = mailRecords,
            inboundMailProcessingRepository = inboundProcessing,
            expertContactRepository = contacts,
            aiTrainingQaService = trainingQa,
            aiReplyContextService = contextService,
            qaFactSelectionService = factSelection,
            qaRuleRepository = qaRules,
            aiReplyDraftService = draftService,
            aiReplyDraftPreviewService = previewService,
            aiReplyReviewAuditService = auditService,
            llmProperties = LlmProperties(enabled = true),
            aiReplyPointByPointComposer = pointByPointComposer,
            claimValidator = claimValidator,
            stateStore = stateStore,
            replySnippetService = replySnippetService
        )
        Mockito.`when`(replySnippetService.resolveDefaultSelectableFrame()).thenReturn(defaultFrame)
        Mockito.`when`(trainingQa.buildKnowledgeContext(Mockito.anyString())).thenReturn("")
        Mockito.`when`(
            contextService.build(
                Mockito.any(ExpertContact::class.java) ?: contact(),
                Mockito.anyList<MailRecord>() ?: emptyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString()
            )
        ).thenReturn(
            AiReplyContext(
                profileText = "Name: Test",
                mailHistory = "history",
                contextWarnings = emptyList(),
                researchProfileSufficient = true
            )
        )
    }

    // I-2/I-3/I-4 共用的整封信 fixture：1 条 GROUNDED（带 2 条事实）+ 2 条 UNSUPPORTED。
    @Test
    fun `adjacent fact names never repeat across unsupported items`() {
        stubCanonicalSource(listOf(
            item(0, "What?", listOf(9L, 10L), RequestGroundingStatus.GROUNDED),
            item(1, "How?", emptyList(), RequestGroundingStatus.UNSUPPORTED),
            item(2, "When?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
        ))
        Mockito.`when`(qaRules.findAllById(Mockito.anyList())).thenReturn(listOf(
            rule(9L, "合作企业类型"),
            rule(10L, "报酬结构")
        ))

        val unsupported = unsupportedInstructions()

        // I-2: 第一条无据条目列出全部邻近事实，第二条不再重复任何一条。
        val firstNameSet = namesIn(unsupported[0])
        val secondNameSet = namesIn(unsupported[1])
        assertEquals(setOf("合作企业类型", "报酬结构"), firstNameSet)
        assertTrue(secondNameSet.isEmpty())
        assertTrue(firstNameSet.intersect(secondNameSet).isEmpty())
    }

    @Test
    fun `CTA wording appears exactly once and only on the last unsupported item`() {
        stubCanonicalSource(listOf(
            item(0, "What?", listOf(9L, 10L), RequestGroundingStatus.GROUNDED),
            item(1, "How?", emptyList(), RequestGroundingStatus.UNSUPPORTED),
            item(2, "When?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
        ))
        Mockito.`when`(qaRules.findAllById(Mockito.anyList())).thenReturn(listOf(
            rule(9L, "合作企业类型"),
            rule(10L, "报酬结构")
        ))

        val unsupported = unsupportedInstructions()
        val all = unsupported.joinToString("")

        // I-3: 只有 index 最大的 UNSUPPORTED 含「交出下一步」；其余条目明确
        // 不索取材料/不约会议/不给下一步；整封信恰好一次。
        assertTrue(unsupported[1].contains("交出下一步"))
        assertTrue(unsupported[0].contains("不要在本条里索取材料"))
        assertFalse(unsupported[0].contains("交出下一步"))
        assertEquals(1, "交出下一步".toRegex().findAll(all).count())
    }

    @Test
    fun `fixed wording never carries digits links domains or time promises for either suffix`() {
        stubCanonicalSource(listOf(
            item(0, "What?", listOf(9L, 10L), RequestGroundingStatus.GROUNDED),
            item(1, "How?", emptyList(), RequestGroundingStatus.UNSUPPORTED),
            item(2, "When?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
        ))
        Mockito.`when`(qaRules.findAllById(Mockito.anyList())).thenReturn(listOf(
            rule(9L, "合作企业类型"),
            rule(10L, "报酬结构")
        ))

        val unsupported = unsupportedInstructions()
        val withAction = unsupported[1]
        val withoutAction = unsupported[0]

        // 两种 suffix 都命中：without-action 是倒数第二条（index 1），
        // with-action 是最后一条（index 2）。
        assertTrue(withoutAction.contains("不要在本条里索取材料"))
        assertTrue(withAction.contains("交出下一步"))
        listOf(withAction, withoutAction).forEach { instruction ->
            assertFalse(Regex("[0-9]").containsMatchIn(instruction), "no digits: $instruction")
            assertFalse(instruction.contains("http", ignoreCase = true), "no http")
            assertFalse(instruction.contains("www", ignoreCase = true), "no www")
            assertFalse(instruction.contains("://"), "no scheme")
            assertFalse(domainFormRegex.containsMatchIn(instruction), "no dotted domain form")
            assertFalse(timeCommitmentRegex.containsMatchIn(instruction), "no time commitment: $instruction")
            timePromiseTokens.forEach { token ->
                assertFalse(instruction.contains(token), "no time-promise token $token")
            }
        }
        // I-3 定稿措辞逐字在场（前缀 + 两种 tail）。
        assertTrue(withAction.contains("这一条我们暂时给不出确定答案。请按真人对接人的方式回答：先说明它取决于什么、还没定下来的原因"))
        assertTrue(withAction.contains("，最后说明确定之后会提供什么，并交出下一步但不承诺具体时间。不要出现数字、链接或时间承诺。"))
        assertTrue(withoutAction.contains("，最后说明确定之后会提供什么。不要在本条里索取材料、提议会议或给出下一步。不要出现数字、链接或时间承诺。"))
    }

    // I-5: 20 条各 40 字的合法邻近名称——贪心装入后仍 ≤500，且绝不出现半个名称。
    @Test
    fun `instruction stays within 500 chars and never truncates a name`() {
        val suffixes = listOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸", "子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉")
        val names = suffixes.mapIndexed { index, suffix -> "邻近事实$suffix".padEnd(40, '好') to (9L + index) }
        stubCanonicalSource(listOf(
            item(0, "What?", listOf(9L), RequestGroundingStatus.GROUNDED),
            item(1, "How?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
        ))
        Mockito.`when`(qaRules.findAllById(Mockito.anyList())).thenReturn(
            names.map { (name, id) -> rule(id, name) }
        )

        val instruction = requireNotNull(
            service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))
                .requestCoverage.single { it.status == "UNSUPPORTED" }.suggestedInstruction
        )

        assertTrue(instruction.length <= 500, "instruction length must stay within 500, was ${instruction.length}")
        val nameSet = names.map { it.first }.toSet()
        val listed = instruction.substringAfter("（", "").substringBefore("）", "")
            .split("、")
            .filter { it.isNotBlank() }
        assertTrue(listed.isNotEmpty(), "some names must fit the budget")
        listed.forEach { name ->
            assertTrue(name in nameSet, "every listed name must be a complete input name, got: $name")
        }
    }

    private fun unsupportedInstructions(): List<String> =
        service.bootstrap(TrustReplyBootstrapRequest(TrustReplySourceRef(TRAINING_MAIL, 11L)))
            .requestCoverage
            .filter { it.status == "UNSUPPORTED" }
            .sortedBy { it.index }
            .map { requireNotNull(it.suggestedInstruction) }

    private fun namesIn(instruction: String): Set<String> =
        instruction.substringAfter("（", "").substringBefore("）", "")
            .split("、")
            .filter { it.isNotBlank() }
            .toSet()

    private fun rule(id: Long, displayName: String) = QaRule(
        id = id,
        categoryId = 3L,
        keywords = "k$id",
        replySubject = null,
        replyBody = "",
        answerBody = "answer body $id",
        displayName = displayName
    )

    private fun item(
        index: Int,
        requestText: String,
        facts: List<Long>,
        status: RequestGroundingStatus
    ): RequestFactItem = RequestFactItem(
        index = index,
        requestText = requestText,
        factRuleIds = facts,
        status = status,
        // P2a (I-1): 夹具镜像生产赋值——auto/legacy/全采纳矩阵路径下
        // boundRuleIds == factRuleIds。
        boundRuleIds = facts,
        intents = listOf(
            RequestIntentCoverage(
                intentKey = "general.answer",
                title = "General answer",
                requiredCoverageKeys = emptyList(),
                evidenceRuleIds = facts,
                status = if (facts.isEmpty()) "MISSING" else "SUPPORTED",
                missingEvidenceKeys = if (facts.isEmpty()) listOf("general.answer") else emptyList(),
                requiresResearchContext = false
            )
        )
    )

    private fun selection(items: List<RequestFactItem>): ResolvedQaRules {
        val sendIds = items.sortedBy { it.index }.flatMap { it.factRuleIds }.distinct()
        return ResolvedQaRules(
            sendQaRuleIds = sendIds,
            promptRuleIds = sendIds,
            requestFacts = items,
            requestCount = items.size,
            groundedRequestCount = items.count { it.status == RequestGroundingStatus.GROUNDED }
        )
    }

    private fun stubCanonicalSource(items: List<RequestFactItem>) {
        val exact = mail(id = 11L, body = "What?")
        Mockito.`when`(mailRecords.findById(11L)).thenReturn(Optional.of(exact))
        Mockito.`when`(contacts.findById(7L)).thenReturn(Optional.of(contact()))
        Mockito.`when`(mailRecords.findAllByExpertContactIdOrderByCreatedAtAsc(7L)).thenReturn(listOf(exact))
        val selected = selection(items)
        Mockito.`when`(factSelection.selectForWorkbench("What?", null, listOf(9L), true)).thenReturn(selected)
        Mockito.`when`(factSelection.selectForWorkbench("What?", null, null, true)).thenReturn(selected)
        Mockito.`when`(factSelection.selectForWorkbench("What?", listOf(listOf(9L)), null, true)).thenReturn(selected)
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(selected.sendQaRuleIds))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        items.forEach { item ->
            Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(item.factRuleIds))
                .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
        }
        Mockito.`when`(draftService.buildEvidenceSnapshotForSelection(emptyList()))
            .thenReturn(Triple("evidence-v1", emptyList(), emptyList()))
    }

    private fun contact() = ExpertContact(
        id = 7L,
        campaignId = 1L,
        orcidId = "0000-0000",
        expertEmail = "test@example.com",
        expertName = "Test"
    )

    private fun mail(id: Long, body: String) = MailRecord(
        id = id,
        expertContactId = 7L,
        direction = "INBOUND",
        mailType = "REPLY",
        senderAccountCode = null,
        messageId = "<$id@example.com>",
        inReplyTo = null,
        subject = "Subject",
        body = body,
        cleanedBody = null,
        matchedQaRuleId = null,
        sendStatus = null,
        receivedAt = LocalDateTime.of(2026, 7, 28, 10, 0),
        sentAt = null
    )
}
