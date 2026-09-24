package com.weibo.talentintroduction.campaign.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.BatchOutcomeReasonCodes
import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationDecision
import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationErrorCodes
import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationRepository
import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationSendStatus
import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationTagStatus
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.ExpertIdNormalizer
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import java.time.ZoneId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale

/**
 * 发送前邮箱验证的**唯一**接入服务（子计划 01 T2：HTTP 验证 / 逐次执行上下文 / 标签与审计协调）。
 *
 * 关键不变量（子计划 01）：
 * - I-2 验证对象 = 最终收件地址：规范化（trim + lowercase(Locale.ROOT)，保留 `+tag`、不合并点号）后作验证键；
 *   只有 HTTP 200 且返回 email 匹配且 state=deliverable 才放行；undeliverable/risky/unknown 都跳过；
 *   unknown 是供应商明确结果，不等于 HTTP 超时（超时是 ERROR）。
 * - I-4 服务异常绝不当邮箱异常：249 最多两次物理请求；402/401/403/429/网络超时/5xx/非法 JSON 或 state/
 *   邮箱不匹配一律 ERROR + 受控错误码；不做自动重试（249 除外）；供应商故障绝不追加「邮箱异常」标签。
 * - I-5 明确非通过结果只追加 tags 中的「邮箱异常」：用真实 `esDocId` 做 `_mget`，只在真实 ID、ORCID、
 *   当前邮箱三者都匹配的已存在副本上调用现有 addTag；无匹配/读取异常/任一应写层返回 false 记 FAILED；
 *   缺失真实文档 ID 记 FAILED/MISSING_DOC_ID，绝不用 ORCID 冒充 `_id`；deliverable 不自动清标签。
 * - I-6 审计先落库：先插 PENDING → 请求 → 写 PASS/SKIP/ERROR → 再允许发送；审计不可用抛
 *   [EmailVerificationAuditException]，调用方必须停止发送，绝不静默降级。
 * - I-7 成本边界：只验证实际走到门禁的目标；同一执行内同邮箱可内存复用（复用行 requestCount=0，
 *   沿原结果时间）；跨执行查一年内原始结果，服务异常不缓存；单邮箱连续 249 最多 2 次；相邻物理请求开始间隔 ≥100ms；无并发池。
 * - I-8 密钥安全：EMAILABLE_API_KEY 只从后端环境读取，仅作为 Bearer 头，不进 URL / 请求快照 /
 *   异常日志 / 数据库；只保存允许字段与受控错误码，不保存完整 HTTP 请求或响应。
 */
@Service
class BatchEmailVerificationService(
    private val expertSearchService: ExpertSearchService,
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val repository: BatchEmailVerificationRepository,
    private val verifyClient: EmailableVerifyClient,
    @Value("\${EMAILABLE_API_KEY:}") private val apiKey: String
) {
    private val log = LoggerFactory.getLogger(BatchEmailVerificationService::class.java)
    private val objectMapper = ObjectMapper()

    /**
     * I-1/I-8：开启执行的入口检查 —— 缺密钥或 test_ 密钥必须在任何业务写入前明确拒绝，
     * 绝不「开着验证但静默不发」。关闭开关时本方法不会被调用，因此无密钥依赖。
     */
    fun requireConfiguredApiKey() {
        val key = apiKey.trim()
        require(key.isNotEmpty()) { "未配置 EMAILABLE_API_KEY，无法开启发送前邮箱验证（发送前验证邮箱）" }
        require(!key.startsWith(TEST_KEY_PREFIX, ignoreCase = true)) {
            "EMAILABLE_API_KEY 是 $TEST_KEY_PREFIX 测试密钥，禁止用于真实发信"
        }
    }

    /** 一次执行的内存加速与限速上下文；跨执行结果从持久化明细读取。 */
    fun beginExecution(executionId: Long, isCancelled: () -> Boolean): ExecutionVerificationContext =
        ExecutionVerificationContext(executionId, isCancelled)

    /**
     * 验证一个目标：先落 PENDING 明细，再出结论，SKIP 追加标签并写标签结果。
     * 审计写入失败抛 [EmailVerificationAuditException]；调用方据此停止本次执行且不得发信。
     */
    fun verify(context: ExecutionVerificationContext, target: EmailVerificationTarget): VerificationResult {
        val normalizedEmail = normalizeVerificationEmail(target.email)
        val normalizedOrcid = ExpertIdNormalizer.normalize(target.orcidId)
        val rowId = audit("插入验证明细") {
            repository.insertPending(
                taskExecutionId = context.executionId,
                expertDocId = target.expertDocId,
                orcidId = normalizedOrcid,
                expertName = target.expertName,
                email = normalizedEmail,
                now = verificationNow()
            )
        }
        if (context.isCancelled()) return VerificationResult.Cancelled(rowId)

        val now = verificationNow()
        val reused = context.reuseOf(normalizedEmail)?.takeIf {
            it.checkedAt > now.minusYears(1) && it.checkedAt <= now
        } ?: audit("查询历史验证") {
            repository.findReusable(normalizedEmail, now)?.let {
                ReusedProviderResult(it.decision, it.providerState, it.providerReason, requireNotNull(it.checkedAt), it.id)
            }
        }
        if (context.isCancelled()) return VerificationResult.Cancelled(rowId)
        if (reused != null) {
            audit("记录历史验证复用") {
                require(repository.recordReusedDecision(
                    rowId, reused.sourceRowId, reused.decision, reused.providerState,
                    reused.providerReason, reused.checkedAt, verificationNow()
                ) == 1) { "复用验证结论未落库：id=$rowId" }
            }
            context.remember(normalizedEmail, reused)
            return conclude(context, target.copy(orcidId = normalizedOrcid), normalizedEmail, rowId, reused.decision, null)
        }

        val outcome = requestNewResult(context, normalizedEmail) ?: return VerificationResult.Cancelled(rowId)
        val checkedAt = verificationNow()
        persistDecision(
            rowId, outcome.decision, outcome.providerState, outcome.providerReason,
            outcome.errorCode, outcome.requestCount, checkedAt
        )
        if (outcome.decision in REUSABLE_DECISIONS) {
            context.remember(
                normalizedEmail,
                ReusedProviderResult(outcome.decision, outcome.providerState, outcome.providerReason, checkedAt, rowId)
            )
        }
        return conclude(
            context,
            target.copy(orcidId = normalizedOrcid),
            normalizedEmail,
            rowId,
            outcome.decision,
            outcome.errorCode
        )
    }

    /**
     * I-6：SMTP 前的条件预占。必须恰好影响 1 行（PASS + NOT_SENT → SENDING）才允许发信；
     * false 表示重复目标或状态冲突，调用方必须停止并报告，不得继续发信。
     */
    fun markSending(rowId: Long): Boolean = audit("发送前预占 SENDING") {
        repository.markSending(rowId, verificationNow())
    }

    /**
     * I-6：写发送结果（SENT / FAILED / NOT_SENT）。只更新发送列，不覆盖验证结论。
     * 发送结果无法落库时抛 [EmailVerificationAuditException]：已发出的邮件保留原计数，
     * 本行留在 SENDING（对外「结果未确认」），禁止据此自动重发。
     */
    fun recordSend(rowId: Long, sendStatus: String, sendReason: String?) {
        audit("记录发送结果") {
            require(repository.recordSend(rowId, sendStatus, sendReason, verificationNow()) == 1) {
                "发送结果未落库（受影响 0 行）：id=$rowId"
            }
        }
    }

    private fun conclude(
        context: ExecutionVerificationContext,
        target: EmailVerificationTarget,
        normalizedEmail: String,
        rowId: Long,
        decision: String,
        errorCode: String?
    ): VerificationResult = when (decision) {
        BatchEmailVerificationDecision.PASS -> VerificationResult.Passed(rowId, normalizedEmail)
        BatchEmailVerificationDecision.SKIP -> {
            // 标签处理是 SKIP 的独立结果：先 PENDING（崩溃后可见未完成），ES 处理完再写 APPLIED/FAILED。
            audit("记录标签待处理") {
                require(repository.recordTag(rowId, BatchEmailVerificationTagStatus.PENDING, null, verificationNow()) == 1) {
                    "标签待处理未落库（受影响 0 行）：id=$rowId"
                }
            }
            val tag = appendEmailAbnormalTag(target, normalizedEmail)
            audit("记录标签结果") {
                require(repository.recordTag(rowId, tag.status, tag.error, verificationNow()) == 1) {
                    "标签结果未落库（受影响 0 行）：id=$rowId"
                }
            }
            // SKIP 行不进 SMTP：send_status=SKIPPED + 跳过码（≠ 发送成功）。
            recordSend(rowId, BatchEmailVerificationSendStatus.SKIPPED, BatchOutcomeReasonCodes.EMAIL_VERIFICATION_REJECTED)
            VerificationResult.Rejected(rowId, tag.status)
        }
        else -> VerificationResult.ServiceFailure(rowId, errorCode ?: BatchEmailVerificationErrorCodes.SERVICE_ERROR)
    }

    private fun persistDecision(
        rowId: Long,
        decision: String,
        providerState: String?,
        providerReason: String?,
        errorCode: String?,
        requestCount: Int,
        checkedAt: LocalDateTime
    ) {
        audit("写入验证结论") {
            require(repository.recordDecision(rowId, decision, providerState, providerReason, errorCode, requestCount, checkedAt, verificationNow()) == 1) {
                "验证结论未落库（受影响 0 行，疑似重复目标）：id=$rowId"
            }
        }
    }

    /**
     * 物理请求循环：249（供应商明确「未完成」）只再试一次，间隔 500ms；其它失败不重试。
     * 返回 null 表示等待期间被取消（未验证者不标记）。requestCount 记物理请求次数。
     */
    private fun requestNewResult(context: ExecutionVerificationContext, normalizedEmail: String): ProviderOutcome? {
        var attempt = 0
        while (true) {
            attempt++
            if (!context.beginPhysicalRequest()) return null
            val response = try {
                verifyClient.postVerify(EmailableVerifyRequest(normalizedEmail, apiKey.trim()))
            } catch (e: HttpTimeoutException) {
                log.warn("Email verification timed out for {}: {}", normalizedEmail, e.message)
                return ProviderOutcome.failed(BatchEmailVerificationErrorCodes.TIMEOUT, attempt)
            } catch (e: IOException) {
                log.warn("Email verification transport failure for {}: {}", normalizedEmail, e.message)
                return ProviderOutcome.failed(BatchEmailVerificationErrorCodes.SERVICE_ERROR, attempt)
            }
            val outcome = classifyResponse(response, normalizedEmail, attempt)
            if (!outcome.incomplete || attempt >= MAX_ATTEMPTS_PER_EMAIL) {
                return outcome.copy(incomplete = false)
            }
            if (!context.awaitRetryDelay()) return null
        }
    }

    private fun classifyResponse(
        response: EmailableHttpResponse,
        normalizedEmail: String,
        requestCount: Int
    ): ProviderOutcome = when (response.statusCode) {
        HTTP_OK -> classifyBody(response.body, normalizedEmail, requestCount)
        HTTP_INCOMPLETE -> ProviderOutcome(
            decision = BatchEmailVerificationDecision.ERROR,
            errorCode = BatchEmailVerificationErrorCodes.INCOMPLETE,
            requestCount = requestCount,
            incomplete = requestCount < MAX_ATTEMPTS_PER_EMAIL
        )
        HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> ProviderOutcome.failed(BatchEmailVerificationErrorCodes.AUTH_ERROR, requestCount)
        HTTP_PAYMENT_REQUIRED -> ProviderOutcome.failed(BatchEmailVerificationErrorCodes.NO_CREDITS, requestCount)
        HTTP_TOO_MANY_REQUESTS -> ProviderOutcome.failed(BatchEmailVerificationErrorCodes.RATE_LIMITED, requestCount)
        in 400..499 -> ProviderOutcome.failed(BatchEmailVerificationErrorCodes.BAD_RESPONSE, requestCount)
        else -> ProviderOutcome.failed(BatchEmailVerificationErrorCodes.SERVICE_ERROR, requestCount)
    }

    /** 200 响应必须同时给出可确认的 state、匹配的 email，才可能 PASS；否则是受控 ERROR。 */
    private fun classifyBody(body: String, normalizedEmail: String, requestCount: Int): ProviderOutcome {
        val node = try {
            objectMapper.readTree(body)
        } catch (e: Exception) {
            log.warn("Email verification returned unparsable body for {}: {}", normalizedEmail, e.message)
            return ProviderOutcome.failed(BatchEmailVerificationErrorCodes.BAD_RESPONSE, requestCount)
        }
        if (node == null || !node.isObject) {
            return ProviderOutcome.failed(BatchEmailVerificationErrorCodes.BAD_RESPONSE, requestCount)
        }
        val state = node.path("state").textOrNull()
        val reason = node.path("reason").textOrNull()
        val returnedEmail = node.path("email").textOrNull()
        if (state == null) {
            return ProviderOutcome.failed(BatchEmailVerificationErrorCodes.BAD_RESPONSE, requestCount)
        }
        if (returnedEmail == null || normalizeVerificationEmail(returnedEmail) != normalizedEmail) {
            log.warn("Email verification response does not echo the requested address for {}", normalizedEmail)
            return ProviderOutcome.failed(BatchEmailVerificationErrorCodes.BAD_RESPONSE, requestCount, state, reason)
        }
        return when (state.lowercase(Locale.ROOT)) {
            STATE_DELIVERABLE -> ProviderOutcome(
                BatchEmailVerificationDecision.PASS, null, requestCount, state, reason
            )
            STATE_UNDELIVERABLE, STATE_RISKY, STATE_UNKNOWN -> ProviderOutcome(
                BatchEmailVerificationDecision.SKIP, null, requestCount, state, reason
            )
            else -> ProviderOutcome.failed(BatchEmailVerificationErrorCodes.BAD_RESPONSE, requestCount, state, reason)
        }
    }

    /**
     * I-5：只在 RAW/CANDIDATE/APPLICATION 中**已存在**且真实 ID、ORCID、当前邮箱三者都匹配的副本上
     * 追加「邮箱异常」。不创建缺失层、不按 ORCID 猜 `_id`、不因标签失败放行邮件（标签失败只记 FAILED）。
     */
    private fun appendEmailAbnormalTag(target: EmailVerificationTarget, normalizedEmail: String): TagOutcome {
        val docId = target.expertDocId?.trim().orEmpty()
        if (docId.isEmpty()) return TagOutcome(BatchEmailVerificationTagStatus.FAILED, "MISSING_DOC_ID")

        val matchedLevels = mutableListOf<ExpertIndexLevel>()
        try {
            for (level in ExpertIndexLevel.values()) {
                val document = expertSearchService.findByDocumentIds(level, listOf(docId)).firstOrNull() ?: continue
                if (document.esDocId != docId) continue
                if (ExpertIdNormalizer.normalize(document.orcidId) != target.orcidId) continue
                if (normalizeVerificationEmail(document.email) != normalizedEmail) continue
                matchedLevels += level
            }
        } catch (e: Exception) {
            log.warn("Failed to read expert copies for tagging docId={}: {}", docId, e.message)
            return TagOutcome(BatchEmailVerificationTagStatus.FAILED, "ES_READ_FAILED:${e.message.orEmpty().take(120)}")
        }
        if (matchedLevels.isEmpty()) return TagOutcome(BatchEmailVerificationTagStatus.FAILED, "NO_MATCHING_DOC")

        val failedLevels = matchedLevels.filterNot { level ->
            expertIndexWriterService.addTag(docId, EMAIL_ABNORMAL_TAG, level)
        }
        return if (failedLevels.isEmpty()) {
            TagOutcome(BatchEmailVerificationTagStatus.APPLIED, null)
        } else {
            TagOutcome(BatchEmailVerificationTagStatus.FAILED, "ES_WRITE_FAILED:" + failedLevels.joinToString(","))
        }
    }

    /** I-6：审计边界 —— 仓储异常/无法写入的一律转成 [EmailVerificationAuditException]，调用方必须停止发送。 */
    private fun <T> audit(step: String, block: () -> T): T = try {
        block()
    } catch (e: EmailVerificationAuditException) {
        throw e
    } catch (e: Exception) {
        throw EmailVerificationAuditException("$step 失败：${e.message.orEmpty().take(200)}", e)
    }

    private data class TagOutcome(val status: String, val error: String?)

    private data class ProviderOutcome(
        val decision: String,
        val errorCode: String?,
        val requestCount: Int,
        val providerState: String? = null,
        val providerReason: String? = null,
        val incomplete: Boolean = false
    ) {
        companion object {
            fun failed(
                errorCode: String,
                requestCount: Int,
                providerState: String? = null,
                providerReason: String? = null
            ) = ProviderOutcome(
                decision = BatchEmailVerificationDecision.ERROR,
                providerState = providerState,
                providerReason = providerReason,
                errorCode = errorCode,
                requestCount = requestCount
            )
        }
    }

    private fun com.fasterxml.jackson.databind.JsonNode.textOrNull(): String? =
        takeIf { it.isTextual }?.asText()?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        /** tags 中的异常标签；只追加，不删除其它标签，也不自动清除。 */
        const val EMAIL_ABNORMAL_TAG = "邮箱异常"

        const val TEST_KEY_PREFIX = "test_"

        const val HTTP_OK = 200
        const val HTTP_INCOMPLETE = 249
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_PAYMENT_REQUIRED = 402
        const val HTTP_TOO_MANY_REQUESTS = 429

        const val STATE_DELIVERABLE = "deliverable"
        const val STATE_UNDELIVERABLE = "undeliverable"
        const val STATE_RISKY = "risky"
        const val STATE_UNKNOWN = "unknown"

        const val MAX_ATTEMPTS_PER_EMAIL = 2

        private val REUSABLE_DECISIONS = setOf(
            BatchEmailVerificationDecision.PASS,
            BatchEmailVerificationDecision.SKIP
        )
    }
}

/** I-2：验证键 = 最终收件地址的规范化形式（trim + lowercase(Locale.ROOT)；不删 `+tag`、不合并点号）。 */
fun normalizeVerificationEmail(email: String?): String = email?.trim()?.lowercase(Locale.ROOT) ?: ""

/** 待验证目标：真实文档 ID 可缺失，ORCID 与邮箱必须来自当前发送快照。 */
data class EmailVerificationTarget(
    val expertDocId: String?,
    val orcidId: String,
    val expertName: String?,
    val email: String
)

/** 单个目标的验证结论；列表顺序即引擎的处理顺序。 */
sealed class VerificationResult {
    /** 通过：只有放行目标会带到这里，行内决策已是 PASS。 */
    data class Passed(val rowId: Long, val normalizedEmail: String) : VerificationResult()

    /** 明确非通过：行内决策已是 SKIP，邮件不发，标签结果为 [tagStatus]。 */
    data class Rejected(val rowId: Long, val tagStatus: String) : VerificationResult()

    /** 服务故障：行内决策已是 ERROR + [errorCode]，调用方必须停止本次执行且不追加「邮箱异常」。 */
    data class ServiceFailure(val rowId: Long, val errorCode: String) : VerificationResult()

    /** 等待中取消：未验证者不标记；[rowId] 为已插入的明细行（可为空）。 */
    data class Cancelled(val rowId: Long?) : VerificationResult()
}

/** 内存/持久化复用结果；sourceRowId 始终指实际调用供应商的原始明细。 */
internal data class ReusedProviderResult(
    val decision: String,
    val providerState: String?,
    val providerReason: String?,
    val checkedAt: LocalDateTime,
    val sourceRowId: Long
)

/**
 * 一次执行的验证上下文（I-7）：同邮箱复用表 + 相邻物理请求 100ms 间隔。
 * 可取消等待：取消检查由引擎注入，等待期间按片轮询，取消后立即返回而不是把请求发出去。
 */
class ExecutionVerificationContext internal constructor(
    val executionId: Long,
    private val cancelled: () -> Boolean
) {
    private val reuseByEmail = mutableMapOf<String, ReusedProviderResult>()
    private var lastPhysicalRequestNanos = 0L

    fun isCancelled(): Boolean = cancelled()

    internal fun reuseOf(normalizedEmail: String): ReusedProviderResult? = reuseByEmail[normalizedEmail]

    internal fun remember(normalizedEmail: String, result: ReusedProviderResult) {
        reuseByEmail[normalizedEmail] = result
    }

    /** 相邻物理请求开始间隔 ≥100ms；返回 false 表示等待期间被取消。 */
    internal fun beginPhysicalRequest(): Boolean {
        if (!awaitUntil(lastPhysicalRequestNanos + MIN_REQUEST_SPACING_NANOS)) return false
        lastPhysicalRequestNanos = System.nanoTime()
        return true
    }

    /** 249 重试前的固定间隔；返回 false 表示等待期间被取消。 */
    internal fun awaitRetryDelay(): Boolean = awaitUntil(System.nanoTime() + RETRY_DELAY_NANOS)

    private fun awaitUntil(deadlineNanos: Long): Boolean {
        while (true) {
            if (isCancelled()) return false
            val remainingNanos = deadlineNanos - System.nanoTime()
            if (remainingNanos <= 0) return true
            // 向上取整，保证实际等待不少于约定间隔；上限 50ms 是为了能在等待中响应取消。
            val sleepMillis = minOf((remainingNanos + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI, CANCEL_POLL_MILLIS)
            try {
                Thread.sleep(sleepMillis)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
    }

    companion object {
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val CANCEL_POLL_MILLIS = 50L

        /** I-7：相邻物理请求开始间隔至少 100ms（无并发池时的成本/速率保护）。 */
        const val MIN_REQUEST_SPACING_MILLIS = 100L

        /** I-4：249 只再试一次，间隔 500ms。 */
        const val RETRY_DELAY_MILLIS = 500L

        private const val MIN_REQUEST_SPACING_NANOS = MIN_REQUEST_SPACING_MILLIS * NANOS_PER_MILLI
        private const val RETRY_DELAY_NANOS = RETRY_DELAY_MILLIS * NANOS_PER_MILLI
    }
}

/** HTTP 测试接缝：单次物理请求；网络/超时异常原样抛出，由服务按受控码分类。 */
interface EmailableVerifyClient {
    fun postVerify(request: EmailableVerifyRequest): EmailableHttpResponse
}

/** 单次请求的全部输入；[apiKey] 只允许进 Authorization 头（I-8）。 */
data class EmailableVerifyRequest(
    val normalizedEmail: String,
    val apiKey: String
)

/** 单次响应：状态码 + 原始正文（正文只用于解析受控字段，不落库、不整体进日志）。 */
data class EmailableHttpResponse(
    val statusCode: Int,
    val body: String
)

/**
 * I-8：目标地址与鉴权头构造的唯一位置 —— 固定 `https://api.emailable.com/v1/verify`，
 * 邮箱按 RFC 3986 百分号编码（`+` 不会变成空格），密钥只进 Authorization。
 * 目标地址不可由前端或配置改写，故这里是常量而非配置项。
 */
object EmailableRequestFactory {
    const val BASE_URL = "https://api.emailable.com"
    const val VERIFY_PATH = "/v1/verify"
    const val PARAM_SMTP = "smtp=true"
    const val PARAM_ACCEPT_ALL = "accept_all=true"
    const val PARAM_TIMEOUT = "timeout=10"

    fun verifyUri(normalizedEmail: String): URI =
        URI.create("$BASE_URL$VERIFY_PATH?email=${percentEncode(normalizedEmail)}&$PARAM_SMTP&$PARAM_ACCEPT_ALL&$PARAM_TIMEOUT")

    fun authorizationHeader(apiKey: String): String = "Bearer $apiKey"

    /** 只保留 RFC 3986 unreserved 字符，其余按 UTF-8 字节百分号编码。 */
    internal fun percentEncode(value: String): String = buildString {
        for (byte in value.toByteArray(StandardCharsets.UTF_8)) {
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char == '-' || char == '.' || char == '_' || char == '~') {
                append(char)
            } else {
                append('%')
                append(HEX_DIGITS[code shr 4])
                append(HEX_DIGITS[code and 0x0F])
            }
        }
    }

    private const val HEX_DIGITS = "0123456789ABCDEF"
}

/** 生产 HTTP 客户端：JDK11 HttpClient，连接超时 3s、单请求超时 12s，禁止重定向。 */
@Component
class EmailableHttpVerifyClient : EmailableVerifyClient {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MILLIS))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun postVerify(request: EmailableVerifyRequest): EmailableHttpResponse {
        val httpRequest = HttpRequest.newBuilder()
            .uri(EmailableRequestFactory.verifyUri(request.normalizedEmail))
            .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MILLIS))
            .header("Authorization", EmailableRequestFactory.authorizationHeader(request.apiKey))
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = try {
            httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("email verification request interrupted", e)
        }
        return EmailableHttpResponse(response.statusCode(), response.body().orEmpty())
    }

    companion object {
        const val CONNECT_TIMEOUT_MILLIS = 3_000L
        const val REQUEST_TIMEOUT_MILLIS = 12_000L
    }
}

/**
 * I-6：审计不可用（仓储失败、无法写入的输入）必须显式让调用方停止发送。
 * 继承 IllegalStateException：业务异常按项目约定映射，不会被误当 SMTP 故障统计。
 */
class EmailVerificationAuditException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

private fun verificationNow(): LocalDateTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
