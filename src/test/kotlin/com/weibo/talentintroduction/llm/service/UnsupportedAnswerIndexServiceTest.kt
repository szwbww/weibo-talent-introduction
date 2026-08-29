package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.config.ElasticsearchProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

/**
 * c6 (16-unsupported-index T-6)：入库放宽 / 列表修复 / 幂等键 / mapping 补丁的
 * 服务级测试。
 * - T-6.1 允许集合边界（四种 handling × 两种 generationKind 全过；OMIT /
 *   ANSWER_WITH_EVIDENCE 拒绝）
 * - T-6.2 operatorInstruction 为空通过、超长拒绝
 * - T-6.3 parseListItem 空 operatorInstruction 返回条目（渲染 —），其余必填字段
 *   为空仍丢弃
 * - T-6.4 documentId 在新增三个字段前后哈希不变（幂等键）
 * - T-6.5（文档侧）线上归档写 editedByOperator = true、训练归档写 false
 * - T-6.6 bootstrapIndex() 在 HEAD 成功时发出一次 PUT _mapping（方案 A）
 */
class UnsupportedAnswerIndexServiceTest {
    private val mapper = ObjectMapper()
    private val properties = ElasticsearchProperties(
        baseUrl = "https://es.example.com:9200",
        username = "elastic",
        password = "secret",
        rawIndexName = "orcid_info",
        candidateIndexName = "orcid_info_candidate",
        applicationIndexName = "orcid_info_application"
    )
    private val indexUrl = "${properties.baseUrl}/${properties.unsupportedAnswerIndexName}"

    private fun service(): UnsupportedAnswerIndexService =
        UnsupportedAnswerIndexService(properties, mapper, RestTemplateBuilder())

    private fun mockServer(service: UnsupportedAnswerIndexService): MockRestServiceServer =
        MockRestServiceServer.bindTo(service.restTemplate).build()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun document(
        handling: String = "ANSWER_FROM_OPERATOR_INPUT",
        generationKind: String = "AI_GENERATED",
        operatorInstruction: String = "Please say we will follow up next week.",
        status: UnsupportedAnswerIndexStatus = UnsupportedAnswerIndexStatus.CANDIDATE,
        sourceMode: UnsupportedAnswerIndexSourceMode = UnsupportedAnswerIndexSourceMode.TRAINING,
        sourceType: UnsupportedAnswerIndexSourceType = UnsupportedAnswerIndexSourceType.TRAINING_MAIL,
        qualificationType: UnsupportedAnswerIndexQualificationType = UnsupportedAnswerIndexQualificationType.TRAINING_EVALUATION,
        topic: String = "followup",
        finalParagraphText: String = "We will follow up next week.",
        editedByOperator: Boolean = false
    ) = UnsupportedAnswerIndexDocument(
        status = status,
        sourceMode = sourceMode,
        sourceType = sourceType,
        sourceId = 101L,
        sourceVersion = "training-101-v1",
        expertContactId = 202L,
        campaignId = 303L,
        requestKey = "request-0",
        requestIndex = 0,
        requestText = "When will you follow up?",
        handling = handling,
        operatorInstruction = operatorInstruction,
        operatorInstructionHash = sha256(operatorInstruction),
        versionId = "canonical-version-1",
        answerText = "We will follow up next week.",
        answerHash = sha256("We will follow up next week."),
        model = "DEEPSEEK_V4_FLASH",
        generationKind = generationKind,
        qualificationType = qualificationType,
        qualificationId = "evaluation-1",
        approvedBy = "operator-1",
        createdAt = Instant.parse("2026-07-29T10:00:00Z"),
        topic = topic,
        finalParagraphText = finalParagraphText,
        editedByOperator = editedByOperator
    )

    private fun expectCreated(server: MockRestServiceServer, doc: UnsupportedAnswerIndexDocument) {
        val id = sha256("${doc.sourceType}|${doc.sourceId}|${doc.requestKey}|${doc.versionId}")
        server.expect(requestTo("$indexUrl/_create/$id"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.CREATED))
    }

    // T-6.1: 允许集合边界——四种 handling × 两种 generationKind 全部通过；
    // 集合外 OMIT / ANSWER_WITH_EVIDENCE 仍拒绝。
    @Test
    fun `validate admits the widened handling and generation kind allow set`() {
        val service = service()
        val server = mockServer(service)
        // MockRestServiceServer 不允许请求发出后再追加期望：先全部注册再逐个调用。
        val docs = listOf(
            "ANSWER_FROM_OPERATOR_INPUT",
            "ANSWER_EVIDENCE_WITH_OPERATOR_INPUT",
            "ANSWER_SUPPORTED_PART",
            "ACKNOWLEDGE_PENDING"
        ).flatMap { handling ->
            listOf("AI_GENERATED", "SAFE_TEMPLATE").map { kind ->
                document(handling = handling, generationKind = kind)
            }
        }
        docs.forEach { expectCreated(server, it) }
        docs.forEach { doc ->
            assertEquals(UnsupportedAnswerIndexCreateOutcome.CREATED, service.create(doc).outcome)
        }
        server.verify()
    }

    @Test
    fun `validate still rejects handling and generation kind outside the allow set`() {
        val service = service()
        assertEquals(
            UnsupportedAnswerIndexCreateOutcome.REJECTED,
            service.create(document(handling = "OMIT")).outcome
        )
        assertEquals(
            UnsupportedAnswerIndexCreateOutcome.REJECTED,
            service.create(document(handling = "ANSWER_WITH_EVIDENCE")).outcome
        )
        assertEquals(
            UnsupportedAnswerIndexCreateOutcome.REJECTED,
            service.create(document(generationKind = "OMITTED")).outcome
        )
    }

    // T-6.2: operatorInstruction 为空时通过；超长（> 4000）仍拒绝。
    @Test
    fun `operatorInstruction blank passes while overlong is rejected`() {
        val service = service()
        val server = mockServer(service)
        val blank = document(operatorInstruction = "")
        expectCreated(server, blank)
        assertEquals(UnsupportedAnswerIndexCreateOutcome.CREATED, service.create(blank).outcome)
        server.verify()

        val overlong = document(operatorInstruction = "x".repeat(4001))
        assertEquals(UnsupportedAnswerIndexCreateOutcome.REJECTED, service.create(overlong).outcome)
    }

    // T-6.3: parseListItem 在 operatorInstruction 为空时返回条目（渲染 —），
    // 其余必填字段为空时仍返回 null（I-6）。
    @Test
    fun `parseListItem keeps blank operatorInstruction but drops other blank fields`() {
        val service = service()
        val server = mockServer(service)
        server.expect(requestTo("$indexUrl/_search"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                """
                {"hits":{"total":{"value":2,"relation":"eq"},"hits":[
                  {"_id":"blank-op","_source":{"status":"CANDIDATE","sourceMode":"TRAINING","requestText":"Question","operatorInstruction":"","answerText":"Answer","model":"model-1","createdAt":"2026-07-29T10:00:00Z","topic":"followup","editedByOperator":true}},
                  {"_id":"blank-request","_source":{"status":"CANDIDATE","sourceMode":"TRAINING","requestText":"","operatorInstruction":"Anything","answerText":"Answer","model":"model-1","createdAt":"2026-07-29T10:00:00Z"}}
                ]}}
                """.trimIndent(),
                MediaType.APPLICATION_JSON
            ))
        val page = service.list(0, 20, null)
        assertEquals(2L, page.total)
        assertEquals(1, page.items.size)
        assertEquals("—", page.items.single().operatorInstruction)
        assertEquals("followup", page.items.single().topic)
        assertTrue(page.items.single().editedByOperator)
        server.verify()
    }

    // T-6.4: documentId 的四项输入不变——新增三个字段前后哈希相同（幂等键）。
    @Test
    fun `documentId is unchanged by the three new fields`() {
        val base = document()
        val withNewFields = base.copy(
            topic = "topic-x",
            finalParagraphText = "A fully rewritten post-close paragraph.",
            editedByOperator = true
        )
        assertEquals(service().documentId(base), service().documentId(withNewFields))
        val expected = sha256("${base.sourceType}|${base.sourceId}|${base.requestKey}|${base.versionId}")
        assertEquals(expected, service().documentId(base))
    }

    // T-6.5（文档侧）: 线上归档写 editedByOperator = true；训练归档写 false。
    @Test
    fun `live archive writes editedByOperator true while training writes false`() {
        val service = service()
        val server = mockServer(service)
        val source = ResolvedTrustReplySource(
            source = TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, 55L),
            contact = ExpertContact(
                id = 202L,
                campaignId = 303L,
                orcidId = "0000-0001",
                expertEmail = "expert@example.com",
                expertName = "Dr. Test"
            ),
            inboundText = "When will you follow up?",
            subject = "Subject",
            messageId = "message-55",
            senderAccountCode = "sender-1",
            profileText = "profile",
            mailHistory = "history",
            contextWarnings = emptyList(),
            researchProfileSufficient = true,
            sourceVersion = "live-55-v1"
        )
        val version = TrustReplyItemVersion(
            versionId = "live-version-1",
            requestKey = "live-request-0",
            handling = TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT,
            answerText = "We will follow up next week.",
            claims = listOf(AiReplyItemClaim("company.followup", "We will follow up next week.", listOf(10L))),
            model = "DEEPSEEK_V4_FLASH",
            generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
            evidenceSetVersion = "evidence-v1",
            sourceVersion = "live-55-v1",
            operatorInstructionHash = sha256("Please say we will follow up next week."),
            requestIndex = 0,
            requestText = "When will you follow up?",
            operatorInstruction = "Please say we will follow up next week."
        )
        val liveId = sha256("LIVE_INBOUND|55|live-request-0|live-version-1")
        server.expect(requestTo("$indexUrl/_create/$liveId"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(jsonPath("$.editedByOperator").value(true))
            .andExpect(jsonPath("$.topic").value("company"))
            // Repair R-1 (V-3): finalParagraphText 取步骤 03 权威段落（映射），
            // 与 item answerText 分开存放。
            .andExpect(jsonPath("$.finalParagraphText").value("The closed follow-up paragraph."))
            .andRespond(withStatus(HttpStatus.CREATED))
        val liveResult = service.archiveLiveCanonicalVersions(
            source = source,
            versions = listOf(version),
            qualificationId = "9001",
            approvedBy = "operator-live",
            createdAt = Instant.parse("2026-07-30T03:00:00Z"),
            finalParagraphs = mapOf("live-request-0" to "The closed follow-up paragraph.")
        )
        assertEquals(UnsupportedAnswerArchiveStatus.SAVED, liveResult.status)
        server.verify()

        val trainingServer = mockServer(service)
        // 训练归档文档的 sourceType 恒为 TRAINING_MAIL（trainingDocument 工厂）。
        val trainingId = sha256("TRAINING_MAIL|55|live-request-0|live-version-1")
        trainingServer.expect(requestTo("$indexUrl/_create/$trainingId"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(jsonPath("$.editedByOperator").value(false))
            .andExpect(jsonPath("$.answerText").value("We will follow up next week."))
            .andExpect(jsonPath("$.finalParagraphText").value("The closed follow-up paragraph."))
            .andRespond(withStatus(HttpStatus.CREATED))
        val trainingResult = service.archiveCanonicalVersions(
            source = source,
            versions = listOf(version),
            qualificationId = "training-55",
            approvedBy = "operator-training",
            createdAt = Instant.parse("2026-07-30T03:00:00Z"),
            finalParagraphs = mapOf("live-request-0" to "The closed follow-up paragraph.")
        )
        assertEquals(UnsupportedAnswerArchiveStatus.SAVED, trainingResult.status)
        trainingServer.verify()
    }

    // Repair R-1 (V-3): 资格内文档映射缺失/歧义 → fail closed（绝不回退为 answerText）。
    @Test
    fun `eligible archive without a final paragraph mapping is rejected`() {
        val service = service()
        val server = mockServer(service)
        val source = ResolvedTrustReplySource(
            source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 55L),
            contact = ExpertContact(
                id = 202L,
                campaignId = 303L,
                orcidId = "0000-0001",
                expertEmail = "expert@example.com",
                expertName = "Dr. Test"
            ),
            inboundText = "When will you follow up?",
            subject = "Subject",
            messageId = "message-55",
            senderAccountCode = "sender-1",
            profileText = "profile",
            mailHistory = "history",
            contextWarnings = emptyList(),
            researchProfileSufficient = true,
            sourceVersion = "training-55-v1"
        )
        val version = TrustReplyItemVersion(
            versionId = "version-1",
            requestKey = "request-0",
            handling = TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT,
            answerText = "We will follow up next week.",
            claims = emptyList(),
            model = "DEEPSEEK_V4_FLASH",
            generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
            evidenceSetVersion = "evidence-v1",
            sourceVersion = "training-55-v1",
            operatorInstructionHash = sha256(""),
            requestIndex = 0,
            requestText = "When will you follow up?",
            operatorInstruction = ""
        )
        // 无 finalParagraphs（或该 requestKey 映射缺失）→ 文档校验失败 → 全量 FAILED。
        val result = service.archiveCanonicalVersions(
            source = source,
            versions = listOf(version),
            qualificationId = "training-55",
            approvedBy = "operator-training",
            createdAt = Instant.parse("2026-07-30T03:00:00Z"),
            finalParagraphs = emptyMap()
        )
        assertEquals(UnsupportedAnswerArchiveStatus.FAILED, result.status)
        assertEquals(1, result.failedCount)
        server.verify()
    }

    // F-2 (I-5 验收): TRAINING + ACTIVE 与 LIVE + CANDIDATE 均为合法
    // status × sourceMode 组合——status 按「是否已转化」区分（CANDIDATE = 未转化，
    // ACTIVE = 通道 B 转化完成），来源由 sourceMode 表达，两者不再绑死。
    @Test
    fun `create accepts TRAINING plus ACTIVE and LIVE plus CANDIDATE`() {
        val service = service()
        val server = mockServer(service)
        val trainingActive = document(
            status = UnsupportedAnswerIndexStatus.ACTIVE,
            sourceMode = UnsupportedAnswerIndexSourceMode.TRAINING
        )
        val liveCandidate = document(
            status = UnsupportedAnswerIndexStatus.CANDIDATE,
            sourceMode = UnsupportedAnswerIndexSourceMode.LIVE,
            sourceType = UnsupportedAnswerIndexSourceType.LIVE_INBOUND,
            qualificationType = UnsupportedAnswerIndexQualificationType.LIVE_SEND
        )
        listOf(trainingActive, liveCandidate).forEach { expectCreated(server, it) }
        listOf(trainingActive, liveCandidate).forEach { doc ->
            assertEquals(UnsupportedAnswerIndexCreateOutcome.CREATED, service.create(doc).outcome)
        }
        server.verify()
    }

    // T-6.6: bootstrapIndex() 在 HEAD 成功时发出一次 PUT _mapping（方案 A）；
    // 补丁失败只记 warn 不阻断启动。
    @Test
    fun `bootstrap patches mapping on HEAD success`() {
        val service = service()
        val server = mockServer(service)
        server.expect(requestTo(indexUrl))
            .andExpect(method(HttpMethod.HEAD))
            .andRespond(withSuccess())
        server.expect(requestTo("$indexUrl/_mapping"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(jsonPath("$.properties.topic.type").value("keyword"))
            .andExpect(jsonPath("$.properties.finalParagraphText.type").value("text"))
            .andExpect(jsonPath("$.properties.finalParagraphText.index").value(false))
            .andExpect(jsonPath("$.properties.editedByOperator.type").value("boolean"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        service.bootstrapIndex()
        server.verify()
    }

    @Test
    fun `bootstrap also patches mapping right after fresh create`() {
        val service = service()
        val server = mockServer(service)
        server.expect(requestTo(indexUrl))
            .andExpect(method(HttpMethod.HEAD))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))
        server.expect(requestTo(indexUrl))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        server.expect(requestTo("$indexUrl/_mapping"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(jsonPath("$.properties.topic.type").value("keyword"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        service.bootstrapIndex()
        server.verify()
    }

    @Test
    fun `bootstrap mapping patch failure only warns`() {
        val service = service()
        val server = mockServer(service)
        server.expect(requestTo(indexUrl))
            .andExpect(method(HttpMethod.HEAD))
            .andRespond(withSuccess())
        server.expect(requestTo("$indexUrl/_mapping"))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))
        service.bootstrapIndex()
        server.verify()
    }
}
