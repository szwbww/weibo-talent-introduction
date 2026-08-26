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
            val localPart = email.substringBefore("@").lowercase()
            val matchedAuthor = knownAuthors.firstOrNull { author ->
                val family = author.familyNames?.lowercase()?.takeIf { it.isNotBlank() } ?: return@firstOrNull false
                val given = author.givenNames?.lowercase()?.takeIf { it.isNotBlank() } ?: ""
                localPart.contains(family) || (given.isNotBlank() && localPart.contains(given)) ||
                    localPart.contains(family.take(1)) || (given.isNotBlank() && localPart.contains(given.take(1)))
            }
            if (matchedAuthor != null) {
                AuthorEmail(email, matchedAuthor.givenNames, matchedAuthor.familyNames,
                    matchedAuthor.isCorresponding, matchedAuthor.affiliation, matchedAuthor.orcidId,
                    matchedAuthor.institutionType)
            } else if (knownAuthors.size == 1 && emails.size == 1) {
                val sole = knownAuthors[0]
                AuthorEmail(email, sole.givenNames, sole.familyNames,
                    sole.isCorresponding, sole.affiliation, sole.orcidId, sole.institutionType)
            } else {
                AuthorEmail(email, null, null, false, null, null)
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

private class PdfTooLargeException : RuntimeException()
