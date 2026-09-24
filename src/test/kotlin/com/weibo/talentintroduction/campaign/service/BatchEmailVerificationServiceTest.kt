package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.BatchOutcomeReasonCodes
import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationDecision
import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationErrorCodes
import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationRepository
import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationSendStatus
import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationTagStatus
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.io.IOException
import java.net.http.HttpTimeoutException
import java.time.LocalDateTime

/**
 * 发送前邮箱验证服务的单元测试（子计划 01 T2/T4，覆盖 I-2/I-4/I-5/I-7/I-8）。
 *
 * 只验证 HTTP 结果矩阵、受控错误码、标签规则、复用/间隔成本边界与审计边界；
 * 真实 MySQL 约束、分页与级联见 BatchEmailVerificationRepositoryIT，
 * 引擎接入（跳过计数/停止原因/审计收尾）见 ManualInitialOutreachServiceTest。
 */
class BatchEmailVerificationServiceTest {

    private val expertSearchService = Mockito.mock(ExpertSearchService::class.java)
    private val expertIndexWriterService = Mockito.mock(ExpertIndexWriterService::class.java)
    private val repository = Mockito.mock(BatchEmailVerificationRepository::class.java)
    private val client = FakeVerifyClient()

    private var nextRowId = 100L

    /**
     * `Mockito.any()` 返回 null，不能传给 Kotlin 非空参数（仓储的 `now`、`ids`）——
     * 用非空缺省值承载 any 匹配（与 ManualInitialOutreachServiceTest 的 anyValue 同口径）。
     */
    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    private fun anyTime(): LocalDateTime = anyValue(ANY_TIME)

    /** `Mockito.eq` 对引用类型返回 null（Mockito 4 的 defaultValue），不能传给 Kotlin 非空参数。 */
    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    @BeforeEach
    fun setUp() {
        Mockito.`when`(
            repository.insertPending(Mockito.anyLong(), Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.anyString(), anyTime())
        ).thenAnswer { nextRowId++ }
        Mockito.`when`(
            repository.recordDecision(Mockito.anyLong(), Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any(), anyTime())
        ).thenReturn(1)
        Mockito.`when`(repository.recordTag(Mockito.anyLong(), Mockito.anyString(), Mockito.any(), anyTime())).thenReturn(1)
        Mockito.`when`(repository.recordSend(Mockito.anyLong(), Mockito.anyString(), Mockito.any(), anyTime())).thenReturn(1)
        Mockito.`when`(repository.markSending(Mockito.anyLong(), anyTime())).thenReturn(true)
    }

    // ──── I-2：结果矩阵 ────

    @Test
    fun `only deliverable passes and it needs no tag`() {
        client.respond(ok("a@b.com", "deliverable", "accepted_email"))
        val subject = subject()
        val context = subject.beginExecution(7L) { false }

        val result = subject.verify(context, target(email = "a@b.com"))

        assertEquals(VerificationResult.Passed(100L, "a@b.com"), result)
        Mockito.verify(repository).recordDecision(
            Mockito.eq(100L), eqValue(BatchEmailVerificationDecision.PASS), Mockito.eq("deliverable"),
            Mockito.eq("accepted_email"), Mockito.isNull(), Mockito.eq(1), Mockito.any(), anyTime()
        )
        // deliverable 不写标签：既不清旧标签，也不记标签结果。
        Mockito.verify(repository, Mockito.never()).recordTag(Mockito.anyLong(), Mockito.anyString(), Mockito.any(), anyTime())
        Mockito.verifyNoInteractions(expertIndexWriterService)
    }

    @Test
    fun `risky undeliverable and unknown are rejected and each one is tagged`() {
        val cases = listOf(
            Triple("doc-risky", "risky@b.com", "risky"),
            Triple("doc-undeliverable", "undeliverable@b.com", "undeliverable"),
            Triple("doc-unknown", "unknown@b.com", "unknown")
        )
        cases.forEach { (_, email, state) -> client.respond(ok(email, state, "provider_reason")) }
        cases.forEachIndexed { index, (docId, email, _) ->
            stubMatchingCandidateCopy(docId = docId, orcidId = "orcid-$index", email = email)
        }
        val subject = subject()
        val context = subject.beginExecution(7L) { false }

        val results = cases.mapIndexed { index, (docId, email, _) ->
            subject.verify(context, target(orcid = "orcid-$index", email = email, docId = docId))
        }

        // 三个不同邮箱各自一次物理请求、各自一行明细、各自一次标签。
        results.forEachIndexed { index, result ->
            assertEquals(VerificationResult.Rejected(100L + index, BatchEmailVerificationTagStatus.APPLIED), result)
        }
        Mockito.verify(repository, Mockito.times(3)).recordDecision(
            Mockito.anyLong(), eqValue(BatchEmailVerificationDecision.SKIP), Mockito.anyString(), Mockito.any(),
            Mockito.isNull(), Mockito.eq(1), Mockito.any(), anyTime()
        )
        Mockito.verify(repository, Mockito.times(3)).recordTag(
            Mockito.anyLong(), eqValue(BatchEmailVerificationTagStatus.PENDING), Mockito.isNull(), anyTime()
        )
        Mockito.verify(repository, Mockito.times(3)).recordTag(
            Mockito.anyLong(), eqValue(BatchEmailVerificationTagStatus.APPLIED), Mockito.isNull(), anyTime()
        )
        Mockito.verify(repository, Mockito.times(3)).recordSend(
            Mockito.anyLong(), eqValue(BatchEmailVerificationSendStatus.SKIPPED),
            Mockito.eq(BatchOutcomeReasonCodes.EMAIL_VERIFICATION_REJECTED), anyTime()
        )
        cases.forEach { (docId, _, _) ->
            Mockito.verify(expertIndexWriterService).addTag(
                eqValue(docId), eqValue(BatchEmailVerificationService.EMAIL_ABNORMAL_TAG), eqValue(ExpertIndexLevel.CANDIDATE)
            )
        }
    }

    @Test
    fun `missing or unknown state is a controlled bad response`() {
        client.respond(EmailableHttpResponse(200, """{"email":"a@b.com","reason":"no_state"}"""))
        client.respond(ok("a@b.com", "totally_unknown"))
        val subject = subject()
        val context = subject.beginExecution(7L) { false }

        assertEquals(
            VerificationResult.ServiceFailure(100L, BatchEmailVerificationErrorCodes.BAD_RESPONSE),
            subject.verify(context, target(email = "a@b.com"))
        )
        assertEquals(
            VerificationResult.ServiceFailure(101L, BatchEmailVerificationErrorCodes.BAD_RESPONSE),
            subject.verify(context, target(email = "a@b.com"))
        )
        Mockito.verify(repository, Mockito.times(2)).recordDecision(
            Mockito.anyLong(), eqValue(BatchEmailVerificationDecision.ERROR), Mockito.any(), Mockito.any(),
            Mockito.eq(BatchEmailVerificationErrorCodes.BAD_RESPONSE), Mockito.eq(1), Mockito.any(), anyTime()
        )
    }

    @Test
    fun `a response that does not echo the requested address is an error and never a pass`() {
        client.respond(ok("someone-else@b.com", "deliverable"))
        val subject = subject()

        val result = subject.verify(subject.beginExecution(7L) { false }, target(email = "a@b.com"))

        assertEquals(
            VerificationResult.ServiceFailure(100L, BatchEmailVerificationErrorCodes.BAD_RESPONSE),
            result
        )
    }

    @Test
    fun `the verification key is the normalized address with tag and dot preserved`() {
        client.respond(ok("name+lab@example.test", "deliverable"))
        val subject = subject()

        val result = subject.verify(
            subject.beginExecution(7L) { false },
            target(email = "  Name+Lab@Example.test ")
        )

        assertEquals(VerificationResult.Passed(100L, "name+lab@example.test"), result)
        assertEquals("name+lab@example.test", client.requests.single().normalizedEmail)
    }

    // ──── I-4：服务故障分类与重试 ────

    @Test
    fun `a single 249 is retried once and the second answer decides`() {
        client.respond(EmailableHttpResponse(249, """{"message":"incomplete"}"""))
        client.respond(ok("a@b.com", "deliverable"))
        val subject = subject()
        val context = subject.beginExecution(7L) { false }

        val startedAt = System.nanoTime()
        val result = subject.verify(context, target(email = "a@b.com"))
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        assertEquals(VerificationResult.Passed(100L, "a@b.com"), result)
        assertEquals(2, client.requests.size)
        assertTrue(
            elapsedMillis >= ExecutionVerificationContext.RETRY_DELAY_MILLIS,
            "249 重试必须间隔 ${ExecutionVerificationContext.RETRY_DELAY_MILLIS}ms，实测 ${elapsedMillis}ms"
        )
        Mockito.verify(repository).recordDecision(
            Mockito.eq(100L), eqValue(BatchEmailVerificationDecision.PASS), Mockito.eq("deliverable"),
            Mockito.isNull(), Mockito.isNull(), Mockito.eq(2), Mockito.any(), anyTime()
        )
    }

    @Test
    fun `two consecutive 249 answers are an incomplete error and never a tag`() {
        client.respond(EmailableHttpResponse(249, """{"message":"incomplete"}"""))
        client.respond(EmailableHttpResponse(249, """{"message":"incomplete"}"""))
        val subject = subject()

        val result = subject.verify(subject.beginExecution(7L) { false }, target(email = "a@b.com"))

        assertEquals(VerificationResult.ServiceFailure(100L, BatchEmailVerificationErrorCodes.INCOMPLETE), result)
        assertEquals(2, client.requests.size)
        Mockito.verify(repository).recordDecision(
            Mockito.eq(100L), eqValue(BatchEmailVerificationDecision.ERROR), Mockito.isNull(), Mockito.isNull(),
            Mockito.eq(BatchEmailVerificationErrorCodes.INCOMPLETE), Mockito.eq(2), Mockito.any(), anyTime()
        )
        Mockito.verify(repository, Mockito.never()).recordTag(Mockito.anyLong(), Mockito.anyString(), Mockito.any(), anyTime())
    }

    @Test
    fun `http and transport failures map to controlled codes without tagging`() {
        val cases = listOf(
            401 to BatchEmailVerificationErrorCodes.AUTH_ERROR,
            403 to BatchEmailVerificationErrorCodes.AUTH_ERROR,
            402 to BatchEmailVerificationErrorCodes.NO_CREDITS,
            429 to BatchEmailVerificationErrorCodes.RATE_LIMITED,
            500 to BatchEmailVerificationErrorCodes.SERVICE_ERROR,
            503 to BatchEmailVerificationErrorCodes.SERVICE_ERROR,
            400 to BatchEmailVerificationErrorCodes.BAD_RESPONSE
        )
        cases.forEach { (status, expected) ->
            client.respond(EmailableHttpResponse(status, """{"message":"provider says no"}"""))
            val subject = subject()
            val result = subject.verify(subject.beginExecution(7L) { false }, target(email = "a@b.com"))
            assertEquals(
                VerificationResult.ServiceFailure(100L, expected),
                result,
                "HTTP $status 必须映射为 $expected"
            )
        }
        // 网络超时与 IO 故障分别记 TIMEOUT / SERVICE_ERROR。
        client.respondWith { throw HttpTimeoutException("read timed out") }
        client.respondWith { throw IOException("connection reset") }
        val subject = subject()
        val context = subject.beginExecution(7L) { false }
        assertEquals(
            VerificationResult.ServiceFailure(100L, BatchEmailVerificationErrorCodes.TIMEOUT),
            subject.verify(context, target(email = "a@b.com"))
        )
        assertEquals(
            VerificationResult.ServiceFailure(101L, BatchEmailVerificationErrorCodes.SERVICE_ERROR),
            subject.verify(context, target(email = "a@b.com"))
        )

        // 服务故障绝不追加「邮箱异常」标签。
        Mockito.verifyNoInteractions(expertIndexWriterService)
    }

    @Test
    fun `an unparsable body is a bad response`() {
        client.respond(EmailableHttpResponse(200, "<html>not json</html>"))
        val subject = subject()

        assertEquals(
            VerificationResult.ServiceFailure(100L, BatchEmailVerificationErrorCodes.BAD_RESPONSE),
            subject.verify(subject.beginExecution(7L) { false }, target(email = "a@b.com"))
        )
    }

    // ──── I-7：复用与物理请求间隔 ────

    @Test
    fun `the same address in one execution is reused with zero extra requests and the original time`() {
        client.respond(ok("a@b.com", "deliverable", "accepted_email"))
        val subject = subject()
        val context = subject.beginExecution(7L) { false }

        val first = subject.verify(context, target(orcid = "0001", email = "a@b.com"))
        val second = subject.verify(context, target(orcid = "0002", email = "A@B.com"))

        assertEquals(VerificationResult.Passed(100L, "a@b.com"), first)
        assertEquals(VerificationResult.Passed(101L, "a@b.com"), second)
        assertEquals(1, client.requests.size, "同执行同邮箱只允许一次物理请求")

        val checkedAt = ArgumentCaptor.forClass(LocalDateTime::class.java)
        // 首次物理验证：requestCount=1；复用行：requestCount=0 且沿原结果时间。
        Mockito.verify(repository).recordDecision(
            Mockito.eq(100L), eqValue(BatchEmailVerificationDecision.PASS), Mockito.any(), Mockito.any(),
            Mockito.isNull(), Mockito.eq(1), checkedAt.capture(), anyTime()
        )
        Mockito.verify(repository).recordDecision(
            Mockito.eq(101L), eqValue(BatchEmailVerificationDecision.PASS), Mockito.any(), Mockito.any(),
            Mockito.isNull(), Mockito.eq(0), checkedAt.capture(), anyTime()
        )
        assertEquals(checkedAt.allValues[0], checkedAt.allValues[1])
    }

    @Test
    fun `a new execution verifies the same address again`() {
        client.respond(ok("a@b.com", "deliverable"))
        client.respond(ok("a@b.com", "deliverable"))
        val subject = subject()

        subject.verify(subject.beginExecution(7L) { false }, target(email = "a@b.com"))
        subject.verify(subject.beginExecution(8L) { false }, target(email = "a@b.com"))

        assertEquals(2, client.requests.size, "跨执行不得复用上一次执行的验证结果")
    }

    @Test
    fun `service errors are never reused inside one execution`() {
        client.respond(EmailableHttpResponse(500, """{"message":"boom"}"""))
        client.respond(ok("a@b.com", "deliverable"))
        val subject = subject()
        val context = subject.beginExecution(7L) { false }

        val first = subject.verify(context, target(orcid = "0001", email = "a@b.com"))
        val second = subject.verify(context, target(orcid = "0002", email = "a@b.com"))

        assertEquals(VerificationResult.ServiceFailure(100L, BatchEmailVerificationErrorCodes.SERVICE_ERROR), first)
        assertEquals(VerificationResult.Passed(101L, "a@b.com"), second)
        assertEquals(2, client.requests.size)
    }

    @Test
    fun `adjacent physical requests are spaced by at least one hundred milliseconds`() {
        client.respond(ok("a@b.com", "deliverable"))
        client.respond(ok("c@b.com", "deliverable"))
        val subject = subject()
        val context = subject.beginExecution(7L) { false }

        subject.verify(context, target(email = "a@b.com"))
        subject.verify(context, target(email = "c@b.com"))

        val gapMillis = (client.requestStartedAtNanos[1] - client.requestStartedAtNanos[0]) / 1_000_000
        assertTrue(
            gapMillis >= ExecutionVerificationContext.MIN_REQUEST_SPACING_MILLIS - 10,
            "相邻物理请求必须间隔 ≥${ExecutionVerificationContext.MIN_REQUEST_SPACING_MILLIS}ms，实测 ${gapMillis}ms"
        )
    }

    @Test
    fun `cancellation during verification returns cancelled without a physical request`() {
        val subject = subject()
        val context = subject.beginExecution(7L) { true }

        val result = subject.verify(context, target(email = "a@b.com"))

        assertEquals(VerificationResult.Cancelled(100L), result)
        assertEquals(0, client.requests.size)
        Mockito.verify(repository, Mockito.never()).recordDecision(
            Mockito.anyLong(), Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.anyInt(), Mockito.any(), anyTime()
        )
    }

    // ──── I-5：标签规则 ────

    @Test
    fun `a missing real document id fails the tag and never falls back to the orcid`() {
        client.respond(ok("a@b.com", "risky"))
        val subject = subject()

        val result = subject.verify(subject.beginExecution(7L) { false }, target(email = "a@b.com", docId = null))

        assertEquals(VerificationResult.Rejected(100L, BatchEmailVerificationTagStatus.FAILED), result)
        Mockito.verify(repository).recordTag(
            Mockito.eq(100L), eqValue(BatchEmailVerificationTagStatus.FAILED), Mockito.eq("MISSING_DOC_ID"), anyTime()
        )
        Mockito.verifyNoInteractions(expertIndexWriterService)
        Mockito.verifyNoInteractions(expertSearchService)
    }

    @Test
    fun `only copies with matching real id orcid and current email are tagged`() {
        client.respond(ok("a@b.com", "undeliverable"))
        Mockito.`when`(expertSearchService.findByDocumentIds(ExpertIndexLevel.RAW, listOf(DOC_ID)))
            .thenReturn(listOf(document(orcidId = "0001", email = "a@b.com")))
        // 候选层邮箱已变 → 不写；应用层不存在 → 不创建。
        Mockito.`when`(expertSearchService.findByDocumentIds(ExpertIndexLevel.CANDIDATE, listOf(DOC_ID)))
            .thenReturn(listOf(document(orcidId = "0001", email = "changed@b.com")))
        Mockito.`when`(expertSearchService.findByDocumentIds(ExpertIndexLevel.APPLICATION, listOf(DOC_ID)))
            .thenReturn(emptyList())
        Mockito.`when`(
            expertIndexWriterService.addTag(DOC_ID, BatchEmailVerificationService.EMAIL_ABNORMAL_TAG, ExpertIndexLevel.RAW)
        ).thenReturn(true)
        val subject = subject()

        val result = subject.verify(subject.beginExecution(7L) { false }, target(email = "a@b.com", docId = DOC_ID))

        assertEquals(VerificationResult.Rejected(100L, BatchEmailVerificationTagStatus.APPLIED), result)
        Mockito.verify(expertIndexWriterService).addTag(
            eqValue(DOC_ID), eqValue(BatchEmailVerificationService.EMAIL_ABNORMAL_TAG), eqValue(ExpertIndexLevel.RAW)
        )
        Mockito.verify(expertIndexWriterService, Mockito.never()).addTag(
            Mockito.anyString(), Mockito.anyString(), eqValue(ExpertIndexLevel.CANDIDATE)
        )
        Mockito.verify(expertIndexWriterService, Mockito.never()).addTag(
            Mockito.anyString(), Mockito.anyString(), eqValue(ExpertIndexLevel.APPLICATION)
        )
    }

    @Test
    fun `a false write result is a failed tag while the address stays rejected`() {
        client.respond(ok("a@b.com", "risky"))
        stubMatchingCandidateCopy()
        Mockito.`when`(
            expertIndexWriterService.addTag(DOC_ID, BatchEmailVerificationService.EMAIL_ABNORMAL_TAG, ExpertIndexLevel.CANDIDATE)
        ).thenReturn(false)
        val subject = subject()

        val result = subject.verify(subject.beginExecution(7L) { false }, target(email = "a@b.com", docId = DOC_ID))

        assertEquals(VerificationResult.Rejected(100L, BatchEmailVerificationTagStatus.FAILED), result)
        Mockito.verify(repository).recordTag(
            Mockito.eq(100L), eqValue(BatchEmailVerificationTagStatus.FAILED), Mockito.eq("ES_WRITE_FAILED:CANDIDATE"), anyTime()
        )
    }

    @Test
    fun `an es read failure is a failed tag while the address stays rejected`() {
        client.respond(ok("a@b.com", "risky"))
        Mockito.`when`(expertSearchService.findByDocumentIds(eqValue(ExpertIndexLevel.RAW), anyValue(emptyList<String>())))
            .thenThrow(IllegalStateException("es down"))
        val subject = subject()

        val result = subject.verify(subject.beginExecution(7L) { false }, target(email = "a@b.com", docId = DOC_ID))

        assertEquals(VerificationResult.Rejected(100L, BatchEmailVerificationTagStatus.FAILED), result)
        val tagError = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(repository).recordTag(
            Mockito.eq(100L), eqValue(BatchEmailVerificationTagStatus.FAILED), tagError.capture(), anyTime()
        )
        assertTrue(tagError.value.startsWith("ES_READ_FAILED:"), "实际 tag_error=${tagError.value}")
    }

    @Test
    fun `no existing copy at all is a failed tag`() {
        client.respond(ok("a@b.com", "unknown"))
        val subject = subject()

        val result = subject.verify(subject.beginExecution(7L) { false }, target(email = "a@b.com", docId = DOC_ID))

        assertEquals(VerificationResult.Rejected(100L, BatchEmailVerificationTagStatus.FAILED), result)
        Mockito.verify(repository).recordTag(
            Mockito.eq(100L), eqValue(BatchEmailVerificationTagStatus.FAILED), Mockito.eq("NO_MATCHING_DOC"), anyTime()
        )
        Mockito.verifyNoInteractions(expertIndexWriterService)
    }

    // ──── I-6：审计边界 ────

    @Test
    fun `a failing pending insert stops before any physical request`() {
        Mockito.`when`(
            repository.insertPending(Mockito.anyLong(), Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.anyString(), anyTime())
        ).thenThrow(IllegalStateException("db down"))
        val subject = subject()

        val failure = assertThrows(EmailVerificationAuditException::class.java) {
            subject.verify(subject.beginExecution(7L) { false }, target(email = "a@b.com"))
        }

        assertTrue(failure.message.orEmpty().contains("插入验证明细"))
        assertEquals(0, client.requests.size, "审计不可写时绝不允许调用验证接口")
    }

    @Test
    fun `a decision write that affects no row is an audit failure`() {
        client.respond(ok("a@b.com", "deliverable"))
        Mockito.`when`(
            repository.recordDecision(Mockito.anyLong(), Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any(), anyTime())
        ).thenReturn(0)
        val subject = subject()

        assertThrows(EmailVerificationAuditException::class.java) {
            subject.verify(subject.beginExecution(7L) { false }, target(email = "a@b.com"))
        }
    }

    @Test
    fun `record send requires exactly one affected row and mark sending reports the reservation`() {
        val subject = subject()

        subject.recordSend(100L, BatchEmailVerificationSendStatus.SENT, null)
        assertTrue(subject.markSending(100L))

        Mockito.`when`(repository.recordSend(Mockito.anyLong(), Mockito.anyString(), Mockito.any(), anyTime())).thenReturn(0)
        assertThrows(EmailVerificationAuditException::class.java) {
            subject.recordSend(100L, BatchEmailVerificationSendStatus.SENT, null)
        }
        Mockito.`when`(repository.markSending(Mockito.anyLong(), anyTime())).thenReturn(false)
        assertFalse(subject.markSending(100L))
    }

    // ──── I-8：密钥与目标地址安全 ────

    @Test
    fun `a missing or test key is rejected before any request`() {
        val missing = subject(apiKey = "   ")
        val testKey = subject(apiKey = "test_abc123")

        assertThrows(IllegalArgumentException::class.java) { missing.requireConfiguredApiKey() }
        assertThrows(IllegalArgumentException::class.java) { testKey.requireConfiguredApiKey() }
        subject(apiKey = "live_secret").requireConfiguredApiKey()
        assertEquals(0, client.requests.size)
    }

    @Test
    fun `the api key only travels as a bearer header and never inside the target address`() {
        val key = "live_secret_key_value"
        client.respond(ok("a@b.com", "deliverable"))
        val subject = subject(apiKey = key)

        subject.verify(subject.beginExecution(7L) { false }, target(email = "a@b.com"))

        val request = client.requests.single()
        assertEquals(key, request.apiKey)
        assertFalse(request.normalizedEmail.contains(key))
        val uri = EmailableRequestFactory.verifyUri(request.normalizedEmail).toString()
        assertFalse(uri.contains(key), "密钥不得进入 URL：$uri")
        assertEquals("Bearer $key", EmailableRequestFactory.authorizationHeader(key))
        assertTrue(uri.startsWith("${EmailableRequestFactory.BASE_URL}${EmailableRequestFactory.VERIFY_PATH}?email="))
        assertTrue(uri.contains("smtp=true") && uri.contains("accept_all=true") && uri.contains("timeout=10"))
        // `+` 必须百分号编码，不能被解释成空格。
        assertEquals("name%2Blab%40example.test", EmailableRequestFactory.percentEncode("name+lab@example.test"))
    }

    @Test
    fun `the normalized key is exposed for the smtp recipient assertion`() {
        assertEquals("a@b.com", normalizeVerificationEmail(" A@B.com "))
        assertEquals("", normalizeVerificationEmail(null))
        assertEquals("", normalizeVerificationEmail("   "))
    }

    // ──── 夹具 ────

    /** 每个用例重置行 id 序列，让断言里的明细 id 稳定可读。 */
    private fun subject(apiKey: String = LIVE_KEY): BatchEmailVerificationService {
        nextRowId = 100L
        return BatchEmailVerificationService(expertSearchService, expertIndexWriterService, repository, client, apiKey)
    }

    private fun target(
        orcid: String = "0001",
        email: String = "a@b.com",
        docId: String? = null
    ) = EmailVerificationTarget(
        expertDocId = docId,
        orcidId = orcid,
        expertName = "Given Family",
        email = email
    )

    private fun document(orcidId: String, email: String, docId: String = DOC_ID): ExpertProfile = ExpertProfile(
        esDocId = docId,
        orcidId = orcidId,
        email = email,
        givenNames = "Given",
        familyNames = "Family",
        country = "China",
        keyword = "keyword",
        employment = "University"
    )

    private fun stubMatchingCandidateCopy(
        docId: String = DOC_ID,
        orcidId: String = "0001",
        email: String = "a@b.com"
    ) {
        Mockito.`when`(expertSearchService.findByDocumentIds(ExpertIndexLevel.CANDIDATE, listOf(docId)))
            .thenReturn(listOf(document(orcidId = orcidId, email = email, docId = docId)))
        Mockito.`when`(
            expertIndexWriterService.addTag(docId, BatchEmailVerificationService.EMAIL_ABNORMAL_TAG, ExpertIndexLevel.CANDIDATE)
        ).thenReturn(true)
    }

    private fun ok(email: String, state: String, reason: String? = null): EmailableHttpResponse {
        val body = buildString {
            append("{\"email\":\"").append(email).append("\",\"state\":\"").append(state).append("\"")
            reason?.let { append(",\"reason\":\"").append(it).append("\"") }
            append("}")
        }
        return EmailableHttpResponse(200, body)
    }

    private class FakeVerifyClient : EmailableVerifyClient {
        val requests = mutableListOf<EmailableVerifyRequest>()
        val requestStartedAtNanos = mutableListOf<Long>()
        private val script = ArrayDeque<() -> EmailableHttpResponse>()

        fun respond(vararg responses: EmailableHttpResponse) {
            responses.forEach { response -> script.addLast { response } }
        }

        fun respondWith(block: () -> EmailableHttpResponse) {
            script.addLast(block)
        }

        override fun postVerify(request: EmailableVerifyRequest): EmailableHttpResponse {
            requests += request
            requestStartedAtNanos += System.nanoTime()
            check(script.isNotEmpty()) { "unexpected physical request #${requests.size}" }
            return script.removeFirst().invoke()
        }
    }

    private companion object {
        const val LIVE_KEY = "live_secret"
        const val DOC_ID = "doc-real-1"
        val ANY_TIME: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
    }
}
