package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.FetchRetry
import com.weibo.talentintroduction.config.PdfExtractionProperties
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.charset.StandardCharsets

@Component
class PdfEmailExtractor(
    @Qualifier("pdfDownloadRestTemplate")
    private val restTemplate: RestTemplate,
    private val plainTextExtractor: PlainTextEmailExtractor,
    private val properties: PdfExtractionProperties
) {
    private val log = LoggerFactory.getLogger(PdfEmailExtractor::class.java)
    private val magicBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46)

    fun extract(
        pdfUrl: String,
        knownAuthors: List<PaperAuthor>,
        sourceName: String
    ): EmailExtractionOutcome {
        val uri = try {
            URI.create(pdfUrl)
        } catch (e: IllegalArgumentException) {
            log.debug("[{}] Invalid PDF URL: {}", sourceName, pdfUrl)
            return EmailExtractionOutcome(
                emails = emptyList(),
                methodUsed = "PDF_PARSE",
                failureReason = "PDF_DOWNLOAD_FAILED",
                httpRequests = 0
            )
        }

        val downloaded = try {
            FetchRetry.retryOnRecoverableIo(
                maxRetries = properties.maxRetries,
                initialBackoffMs = properties.retryBackoffMs
            ) {
                downloadWithStreamLimit(uri)
            }
        } catch (e: PdfTooLargeException) {
            log.debug("[{}] PDF {} too large", sourceName, pdfUrl)
            return EmailExtractionOutcome(emptyList(), "PDF_PARSE", "PDF_TOO_LARGE", httpRequests = 1)
        } catch (e: Exception) {
            log.debug("[{}] Failed to download PDF {}: {}", sourceName, pdfUrl, e.message)
            return EmailExtractionOutcome(emptyList(), "PDF_PARSE", "PDF_DOWNLOAD_FAILED", httpRequests = 1)
        }

        return when (downloaded.kind) {
            ContentKind.PDF -> extractFromPdf(downloaded.bytes, knownAuthors, pdfUrl, sourceName)
            ContentKind.HTML -> extractFromHtml(downloaded.bytes, knownAuthors)
            ContentKind.OTHER -> EmailExtractionOutcome(emptyList(), "PDF_PARSE", "PDF_DOWNLOAD_FAILED", httpRequests = 1)
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
            return EmailExtractionOutcome(emptyList(), "PDF_PARSE", "PDF_PARSE_FAILED", httpRequests = 1)
        }

        if (emails.isEmpty()) {
            return EmailExtractionOutcome(emptyList(), "PDF_PARSE", "NO_EMAIL_IN_TEXT", httpRequests = 1)
        }
        return EmailExtractionOutcome(emails, "PDF_PARSE", null, httpRequests = 1)
    }

    private fun extractFromHtml(bytes: ByteArray, knownAuthors: List<PaperAuthor>): EmailExtractionOutcome {
        val html = String(bytes, StandardCharsets.UTF_8)
        val text = htmlToVisibleText(html)
        val emails = associateEmailsWithAuthors(text, knownAuthors)
        if (emails.isEmpty()) {
            return EmailExtractionOutcome(emptyList(), "HTML_FALLBACK", "NO_EMAIL_IN_HTML", httpRequests = 1)
        }
        return EmailExtractionOutcome(emails, "HTML_FALLBACK", null, httpRequests = 1)
    }

    private enum class ContentKind { PDF, HTML, OTHER }

    private data class DownloadedContent(val bytes: ByteArray, val kind: ContentKind)

    private fun downloadWithStreamLimit(uri: URI): DownloadedContent {
        return restTemplate.execute(uri, HttpMethod.GET, null) { response ->
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
                    val n = input.read(chunk)
                    if (n == -1) break
                    totalRead += n
                    if (totalRead > maxSize) {
                        throw PdfTooLargeException()
                    }
                    buffer.write(chunk, 0, n)
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
