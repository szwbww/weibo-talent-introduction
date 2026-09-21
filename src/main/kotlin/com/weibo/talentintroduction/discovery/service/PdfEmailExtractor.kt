package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.BoundedHttpExecutor
import com.weibo.talentintroduction.config.FetchRetry
import com.weibo.talentintroduction.config.PDF_DOWNLOAD_CONNECT_TIMEOUT_MS
import com.weibo.talentintroduction.config.PdfExtractionProperties
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.io.ByteArrayInputStream
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.cert.CertificateException
import java.time.Instant
import javax.net.ssl.SSLException

@Component
class PdfEmailExtractor(
    @Qualifier("pdfDownloadRestTemplate")
    private val restTemplate: RestTemplate,
    private val plainTextExtractor: PlainTextEmailExtractor,
    private val properties: PdfExtractionProperties,
    /**
     * R-1（V-4）：把剩余预算变成一次尝试的连接/读取超时的执行器（生产实现是
     * [com.weibo.talentintroduction.config.BoundedFulltextHttp]，由 `boundedHttpExecutor` bean 注入）。
     */
    private val boundedHttp: BoundedHttpExecutor
) {
    private val log = LoggerFactory.getLogger(PdfEmailExtractor::class.java)
    private val magicBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46)

    /**
     * c10（I-1/I-3）：单个全文地址的提取。
     *
     * [deadline] 是**单篇论文全部全文地址共享**的总时限（不是每个地址各给一份）：进入下载前先看是否已过期，
     * 流式下载途中每读一段再检查一次，越过即放弃并归入 [FULLTEXT_FAILURE_TIMEOUT]，绝不在这里重新计时。
     * [onResponseHeaders] 让调用方（OpenAlex 全文路径）在下载真正发生时读取 provider 响应头写入共享额度口径。
     *
     * 返回值的 [EmailExtractionOutcome.fulltextObtained] 区分「取到内容但没有邮箱」与「根本没取到内容」。
     */
    fun extract(
        pdfUrl: String,
        knownAuthors: List<PaperAuthor>,
        sourceName: String,
        deadline: Instant? = null,
        onResponseHeaders: ((HttpHeaders) -> Unit)? = null
    ): EmailExtractionOutcome {
        val uri = try {
            URI.create(pdfUrl)
        } catch (e: IllegalArgumentException) {
            log.debug("[{}] Invalid PDF URL: {}", sourceName, pdfUrl)
            return EmailExtractionOutcome(
                emails = emptyList(),
                methodUsed = "PDF_PARSE",
                failureReason = "PDF_DOWNLOAD_FAILED",
                httpRequests = 0,
                fulltextObtained = false
            )
        }

        val downloaded = try {
            FetchRetry.retryOnRecoverableIo(
                maxRetries = properties.maxRetries,
                initialBackoffMs = properties.retryBackoffMs
            ) {
                // 重试也不重新计时：共享 deadline 已过期就不再发新的请求。
                if (deadlineExpired(deadline)) throw FulltextDeadlineExceededException(requestIssued = false)
                downloadWithStreamLimit(uri, deadline, onResponseHeaders)
            }
        } catch (e: FulltextDeadlineExceededException) {
            log.debug("[{}] Fulltext download of {} exceeded the shared per-paper deadline", sourceName, pdfUrl)
            return EmailExtractionOutcome(
                emptyList(), "PDF_PARSE", "PDF_DOWNLOAD_FAILED",
                httpRequests = if (e.requestIssued) 1 else 0,
                fulltextObtained = false,
                downloadFailureCategory = FULLTEXT_FAILURE_TIMEOUT
            )
        } catch (e: PdfTooLargeException) {
            log.debug("[{}] PDF {} too large", sourceName, pdfUrl)
            return EmailExtractionOutcome(
                emptyList(), "PDF_PARSE", "PDF_TOO_LARGE", httpRequests = 1, fulltextObtained = false
            )
        } catch (e: Exception) {
            log.debug("[{}] Failed to download PDF {}: {}", sourceName, pdfUrl, e.message)
            return EmailExtractionOutcome(
                emptyList(), "PDF_PARSE", "PDF_DOWNLOAD_FAILED",
                httpRequests = 1,
                fulltextObtained = false,
                downloadFailureCategory = classifyDownloadFailure(e)
            )
        }

        return when (downloaded.kind) {
            ContentKind.PDF -> extractFromPdf(downloaded.bytes, knownAuthors, pdfUrl, sourceName)
            ContentKind.HTML -> extractFromHtml(downloaded.bytes, knownAuthors)
            ContentKind.OTHER -> EmailExtractionOutcome(
                emptyList(), "PDF_PARSE", "PDF_DOWNLOAD_FAILED",
                httpRequests = 1,
                fulltextObtained = false,
                downloadFailureCategory = FULLTEXT_FAILURE_INVALID_CONTENT
            )
        }
    }

    private fun extractFromPdf(
        bytes: ByteArray,
        knownAuthors: List<PaperAuthor>,
        pdfUrl: String,
        sourceName: String
    ): EmailExtractionOutcome {
        val emails = try {
            extractEmailsFromBytes(bytes, knownAuthors)
        } catch (e: Exception) {
            log.debug("[{}] Failed to parse PDF {}: {}", sourceName, pdfUrl, e.message)
            return EmailExtractionOutcome(
                emptyList(), "PDF_PARSE", "PDF_PARSE_FAILED",
                httpRequests = 1,
                fulltextObtained = false,
                downloadFailureCategory = FULLTEXT_FAILURE_INVALID_CONTENT
            )
        }

        if (emails.isEmpty()) {
            return EmailExtractionOutcome(
                emptyList(), "PDF_PARSE", "NO_EMAIL_IN_TEXT", httpRequests = 1, fulltextObtained = true
            )
        }
        return EmailExtractionOutcome(emails, "PDF_PARSE", null, httpRequests = 1, fulltextObtained = true)
    }

    /**
     * I-3（c10）：取到的 HTML 只说明**这个地址返回了可读内容**，并不保证它就是论文全文（可能是落地页/摘要页）：
     * 因此它计入「获取内容成功」（[EmailExtractionOutcome.fulltextObtained] = true），没有邮箱时单列
     * `NO_EMAIL_IN_HTML`，绝不与下载失败混为一谈。
     */
    private fun extractFromHtml(bytes: ByteArray, knownAuthors: List<PaperAuthor>): EmailExtractionOutcome {
        val html = String(bytes, StandardCharsets.UTF_8)
        val text = htmlToVisibleText(html)
        val emails = associateEmailsWithAuthors(text, knownAuthors)
        if (emails.isEmpty()) {
            return EmailExtractionOutcome(
                emptyList(), "HTML_FALLBACK", "NO_EMAIL_IN_HTML", httpRequests = 1, fulltextObtained = true
            )
        }
        return EmailExtractionOutcome(emails, "HTML_FALLBACK", null, httpRequests = 1, fulltextObtained = true)
    }

    private enum class ContentKind { PDF, HTML, OTHER }

    private data class DownloadedContent(val bytes: ByteArray, val kind: ContentKind)

    /** c10（I-3）：下载失败的低基数分桶，供任务 details_json 统计。 */
    private fun classifyDownloadFailure(e: Exception): String {
        var httpStatus: Int? = null
        var timedOut = false
        var tlsFailure = false
        var current: Throwable? = e
        while (current != null) {
            when (current) {
                is HttpStatusCodeException -> httpStatus = current.statusCode.value()
                is SocketTimeoutException -> timedOut = true
                is SSLException, is CertificateException -> tlsFailure = true
                is FulltextDeadlineExceededException -> timedOut = true
                // R-1（V-4）：有界 client 在**绝对** deadline 处截断响应体时抛的类型（含 close() 排空阶段），
                // 必须与「本下载自己的分片检查」归入同一个既有 TIMEOUT 类别，不能落进 NETWORK_ERROR。
                is com.weibo.talentintroduction.config.BoundedFulltextHttp.FulltextBodyDeadlineExceededException ->
                    timedOut = true
            }
            current = current.cause
        }
        httpStatus?.let { status ->
            return when {
                status == 403 -> FULLTEXT_FAILURE_HTTP_403
                status == 404 -> FULLTEXT_FAILURE_HTTP_404
                status == 429 -> FULLTEXT_FAILURE_HTTP_429
                status >= 500 -> FULLTEXT_FAILURE_HTTP_5XX
                status >= 400 -> FULLTEXT_FAILURE_HTTP_4XX
                else -> FULLTEXT_FAILURE_NETWORK
            }
        }
        return when {
            timedOut -> FULLTEXT_FAILURE_TIMEOUT
            tlsFailure -> FULLTEXT_FAILURE_TLS
            else -> FULLTEXT_FAILURE_NETWORK
        }
    }

    private fun downloadWithStreamLimit(
        uri: URI,
        deadline: Instant?,
        onResponseHeaders: ((HttpHeaders) -> Unit)?
    ): DownloadedContent {
        // R-1（V-4）：连接与响应头读取都在同一个剩余预算内 —— 有界 client 的连接/读取超时都取
        // min(既有配置, 剩余预算)，响应体读取仍由下面的分片 deadline 检查把关，两处都不重新计时。
        // R-1（V-4）：dispatch 前再判一次 —— 预算已尽就不发这次请求（0 请求，不是「发出去再超时」）。
        if (com.weibo.talentintroduction.config.BoundedFulltextHttp.remainingMsOrUnbounded(deadline) <= 0L) {
            throw FulltextDeadlineExceededException(requestIssued = false)
        }
        return boundedHttp.execute<DownloadedContent>(
            restTemplate, uri,
            PDF_DOWNLOAD_CONNECT_TIMEOUT_MS, properties.downloadTimeoutMs, deadline
        ) { response ->
            onResponseHeaders?.invoke(response.headers)
            val contentType = response.headers.contentType
            val isPdfContentType = contentType != null &&
                (contentType.isCompatibleWith(MediaType.APPLICATION_PDF) ||
                 contentType.subtype?.lowercase() == "pdf")

            val maxSize = properties.maxPdfSizeBytes
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var totalRead = 0L

            response.body.use { input ->
                while (true) {
                    // R-1（V-4）：每次分片读取前后都对照绝对时限 —— 服务端只要在单次读超时前吐一点数据
                    // 就能让「按次读超时」永远不到期，因此这里必须按绝对 deadline 截断。
                    if (deadlineExpired(deadline)) throw FulltextDeadlineExceededException(requestIssued = true)
                    val n = input.read(chunk)
                    if (n == -1) break
                    totalRead += n
                    if (totalRead > maxSize) {
                        throw PdfTooLargeException()
                    }
                    buffer.write(chunk, 0, n)
                    if (deadlineExpired(deadline)) throw FulltextDeadlineExceededException(requestIssued = true)
                }
            }

            val bytes = buffer.toByteArray()
            if (bytes.isEmpty()) throw RuntimeException("Empty body")

            val hasMagic = bytes.size >= 4 && bytes.take(4).toByteArray().contentEquals(magicBytes)
            if (isPdfContentType || hasMagic) {
                return@execute DownloadedContent(bytes, ContentKind.PDF)
            }

            if (properties.htmlFallbackEnabled && isHtmlContent(contentType, bytes)) {
                return@execute DownloadedContent(bytes, ContentKind.HTML)
            }

            DownloadedContent(bytes, ContentKind.OTHER)
        } ?: throw RuntimeException("Empty response")
    }

    private fun isHtmlContent(contentType: MediaType?, bytes: ByteArray): Boolean {
        if (contentType != null && contentType.isCompatibleWith(MediaType.TEXT_HTML)) {
            return true
        }
        val prefix = String(bytes, 0, minOf(bytes.size, 512), StandardCharsets.UTF_8)
            .trimStart()
            .lowercase()
        return prefix.startsWith("<!doctype html") || prefix.startsWith("<html")
    }

    private fun htmlToVisibleText(html: String): String {
        return html
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractEmailsFromBytes(bytes: ByteArray, knownAuthors: List<PaperAuthor>): List<AuthorEmail> {
        ByteArrayInputStream(bytes).use { stream ->
            PDDocument.load(stream).use { doc ->
                val stripper = PDFTextStripper()
                stripper.startPage = 1
                stripper.endPage = minOf(properties.maxPages, doc.numberOfPages)
                val text = stripper.getText(doc)
                return associateEmailsWithAuthors(text, knownAuthors)
            }
        }
    }

    private fun associateEmailsWithAuthors(text: String, knownAuthors: List<PaperAuthor>): List<AuthorEmail> {
        val emails = plainTextExtractor.extract(text, properties.blacklistPrefixes)
        if (emails.isEmpty()) return emptyList()

        if (knownAuthors.isEmpty()) {
            return emails.map { AuthorEmail(it, null, null, false, null, null) }
        }

        return emails.map { email ->
            val verified = verifiedAuthorFor(email, knownAuthors, emails.size)
            if (verified == null) {
                AuthorEmail(email, null, null, false, null, null)
            } else {
                AuthorEmail(
                    email = email, givenNames = verified.givenNames, familyNames = verified.familyNames,
                    isCorresponding = verified.isCorresponding, affiliation = verified.affiliation,
                    orcidId = verified.orcidId, institutionType = verified.institutionType,
                    openAlexAuthorId = verified.openAlexAuthorId
                )
            }
        }.also { results ->
            val unmatched = knownAuthors.filter { author ->
                results.none { r -> r.familyNames == author.familyNames && r.givenNames == author.givenNames }
            }
            for (author in unmatched) {
                log.debug("Could not associate any email with author {} {}", author.givenNames, author.familyNames)
            }
        }
    }
}

/** c10（I-3）：下载失败的低基数类别词汇表（进入任务 details_json 的 failureReasons）。 */
internal const val FULLTEXT_FAILURE_HTTP_403 = "HTTP_403"
internal const val FULLTEXT_FAILURE_HTTP_404 = "HTTP_404"
internal const val FULLTEXT_FAILURE_HTTP_429 = "HTTP_429"
internal const val FULLTEXT_FAILURE_HTTP_5XX = "HTTP_5XX"
internal const val FULLTEXT_FAILURE_HTTP_4XX = "HTTP_4XX"
internal const val FULLTEXT_FAILURE_TLS = "TLS_ERROR"
internal const val FULLTEXT_FAILURE_TIMEOUT = "TIMEOUT"
internal const val FULLTEXT_FAILURE_INVALID_CONTENT = "INVALID_CONTENT"
internal const val FULLTEXT_FAILURE_NETWORK = "NETWORK_ERROR"

/**
 * c10（I-1）：只有公开 http(s) 链接才是可尝试的开放全文地址 —— 空值、相对地址与 ftp/file 等协议
 * 一律不成其为候选，调用方也就不会去绕付费墙或验证码。
 */
internal fun publicFulltextUrl(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return try {
        val scheme = URI.create(trimmed).scheme?.lowercase()
        if (scheme == "http" || scheme == "https") trimmed else null
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * I-2: 文本挖掘出来的邮箱只有在本地部分同时含「姓」与「名」（完整姓名组合）时才算强证据。
 * 首字母、单姓、单名都不足以绑定学术身份 —— 曾用 `localPart.contains(family.take(1))` 兜底，
 * 会把甲的邮箱绑到乙的 ORCID/作者ID 上。
 */
internal fun hasStrongEmailNameEvidence(email: String, author: PaperAuthor): Boolean {
    val localPart = normalizeNameToken(email.substringBefore("@")) ?: return false
    val family = normalizeNameToken(author.familyNames) ?: return false
    val given = normalizeNameToken(author.givenNames) ?: return false
    return localPart.contains(family) && localPart.contains(given)
}

/**
 * I-2: 邮箱 → 作者的唯一归属。多个作者同时命中（共享首字母、同名、姓氏子串）或证据不足时返回 null：
 * 调用方保留邮箱线索，但不携带任何学术身份。唯一作者且唯一邮箱是明确无歧义、允许的归属。
 */
internal fun verifiedAuthorFor(email: String, authors: List<PaperAuthor>, emailCount: Int): PaperAuthor? {
    val matches = authors.filter { hasStrongEmailNameEvidence(email, it) }
    return matches.singleOrNull() ?: authors.singleOrNull()?.takeIf { emailCount == 1 }
}

/** 姓名/邮箱本地部分的比较单位：小写字母数字，且至少两个字符（单字符姓名不构成证据）。 */
private const val MIN_NAME_TOKEN_LENGTH = 2

private fun normalizeNameToken(value: String?): String? =
    value?.lowercase()?.filter { it.isLetterOrDigit() }?.takeIf { it.length >= MIN_NAME_TOKEN_LENGTH }

private class PdfTooLargeException : RuntimeException()

/** c10（I-1）：共享 deadline 过期。[requestIssued] 区分「还没发请求」与「请求已发出、下载中途放弃」。 */
private class FulltextDeadlineExceededException(val requestIssued: Boolean) :
    RuntimeException("fulltext deadline exceeded")


private fun deadlineExpired(deadline: Instant?): Boolean = deadline != null && !Instant.now().isBefore(deadline)
