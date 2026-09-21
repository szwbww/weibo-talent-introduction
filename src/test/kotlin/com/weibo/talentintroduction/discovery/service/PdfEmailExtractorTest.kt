package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.PdfExtractionProperties
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.RequestCallback
import org.springframework.web.client.ResponseExtractor
import org.springframework.web.client.RestTemplate
import java.io.ByteArrayInputStream
import java.net.URI

class PdfEmailExtractorTest {
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val plainTextExtractor = PlainTextEmailExtractor()
    private val properties = PdfExtractionProperties()
    private val extractor = PdfEmailExtractor(restTemplate, plainTextExtractor, properties)

    @Test
    fun `extracts emails from standard PDF`() {
        val pdfBytes = readPdfFixture("pdf/standard.pdf")
        stubPdfDownload(pdfBytes, MediaType.APPLICATION_PDF)

        val authors = listOf(
            PaperAuthor("John", "Smith", null, "Oxford, UK", false),
            PaperAuthor("Jane", "Doe", null, "Cambridge, UK", false)
        )
        val result = extractor.extract("http://example.com/test.pdf", authors, "TEST")

        assertEquals("PDF_PARSE", result.methodUsed)
        assertTrue(result.emails.isNotEmpty())
        val emails = result.emails.map { it.email }
        assertTrue(emails.any { it.contains("john.smith") })
        assertTrue(emails.any { it.contains("jane.doe") })
    }

    @Test
    fun `handles obfuscated emails in PDF`() {
        val pdfBytes = readPdfFixture("pdf/obfuscated.pdf")
        stubPdfDownload(pdfBytes, MediaType.APPLICATION_PDF)

        val result = extractor.extract("http://example.com/test.pdf", emptyList(), "TEST")

        assertEquals("PDF_PARSE", result.methodUsed)
        assertTrue(result.emails.isNotEmpty())
        val emails = result.emails.map { it.email }
        assertTrue(emails.any { it.contains("oxford.ac.uk") })
        assertTrue(emails.any { it.contains("university.edu") })
    }

    @Test
    fun `filters blacklisted emails in PDF`() {
        val pdfBytes = readPdfFixture("pdf/with_blacklist.pdf")
        stubPdfDownload(pdfBytes, MediaType.APPLICATION_PDF)

        val result = extractor.extract("http://example.com/test.pdf", emptyList(), "TEST")

        assertTrue(result.emails.none { it.email.startsWith("journals@") })
        assertTrue(result.emails.any { it.email == "researcher@gmail.com" })
    }

    @Test
    fun `returns NO_EMAIL_IN_TEXT for PDF without emails`() {
        val pdfBytes = readPdfFixture("pdf/no_email.pdf")
        stubPdfDownload(pdfBytes, MediaType.APPLICATION_PDF)

        val result = extractor.extract("http://example.com/test.pdf", emptyList(), "TEST")

        assertEquals("NO_EMAIL_IN_TEXT", result.failureReason)
        assertTrue(result.emails.isEmpty())
    }

    @Test
    fun `rejects non-PDF non-HTML content`() {
        stubPdfDownload("NOT_PDF_OR_HTML".toByteArray(), MediaType.APPLICATION_OCTET_STREAM)

        val result = extractor.extract("http://example.com/not-a-pdf", emptyList(), "TEST")

        assertEquals("PDF_DOWNLOAD_FAILED", result.failureReason)
    }

    @Test
    fun `extracts emails from HTML landing page`() {
        val html = "<html><body>Contact: jane.doe@university.edu</body></html>".toByteArray()
        stubPdfDownload(html, MediaType.TEXT_HTML)

        val result = extractor.extract("http://example.com/landing", emptyList(), "TEST")

        assertEquals("HTML_FALLBACK", result.methodUsed)
        assertTrue(result.emails.any { it.email == "jane.doe@university.edu" })
    }

    @Test
    fun `returns NO_EMAIL_IN_HTML when HTML has no email`() {
        val html = "<html><body>No contact info here</body></html>".toByteArray()
        stubPdfDownload(html, MediaType.TEXT_HTML)

        val result = extractor.extract("http://example.com/landing", emptyList(), "TEST")

        assertEquals("HTML_FALLBACK", result.methodUsed)
        assertEquals("NO_EMAIL_IN_HTML", result.failureReason)
        assertTrue(result.emails.isEmpty())
    }

    @Test
    fun `detects HTML by doctype prefix without text html content type`() {
        val html = "<!DOCTYPE html><html><body>researcher@gmail.com</body></html>".toByteArray()
        stubPdfDownload(html, MediaType.APPLICATION_OCTET_STREAM)

        val result = extractor.extract("http://example.com/landing", emptyList(), "TEST")

        assertEquals("HTML_FALLBACK", result.methodUsed)
        assertTrue(result.emails.any { it.email == "researcher@gmail.com" })
    }

    @Test
    fun `skips HTML fallback when disabled`() {
        val disabledExtractor = PdfEmailExtractor(
            restTemplate,
            plainTextExtractor,
            PdfExtractionProperties(htmlFallbackEnabled = false)
        )
        val html = "<html><body>jane@uni.edu</body></html>".toByteArray()
        stubPdfDownload(html, MediaType.TEXT_HTML)

        val result = disabledExtractor.extract("http://example.com/landing", emptyList(), "TEST")

        assertEquals("PDF_DOWNLOAD_FAILED", result.failureReason)
    }

    @Test
    fun `rejects empty non-PDF content`() {
        stubPdfDownload(ByteArray(0), MediaType.TEXT_HTML)

        val result = extractor.extract("http://example.com/not-a-pdf", emptyList(), "TEST")

        assertEquals("PDF_DOWNLOAD_FAILED", result.failureReason)
    }

    @Test
    fun `rejects oversized PDF`() {
        val bigBytes = ByteArray((properties.maxPdfSizeBytes + 1).toInt()) { 0 }
        stubPdfDownload(bigBytes, MediaType.APPLICATION_PDF)

        val result = extractor.extract("http://example.com/big.pdf", emptyList(), "TEST")

        assertEquals("PDF_TOO_LARGE", result.failureReason)
    }

    @Test
    fun `retries download when body read throws recoverable IO then succeeds with HTML`() {
        val retryProperties = PdfExtractionProperties(maxRetries = 2, retryBackoffMs = 0)
        val retryExtractor = PdfEmailExtractor(restTemplate, plainTextExtractor, retryProperties)
        val html = "<html><body>retry@test.edu</body></html>".toByteArray()
        var executeCount = 0

        Mockito.doAnswer { invocation ->
            executeCount++
            val extractor = invocation.getArgument<ResponseExtractor<*>>(3)
            val mockResponse = Mockito.mock(ClientHttpResponse::class.java)
            val headers = HttpHeaders().apply { contentType = MediaType.TEXT_HTML }
            Mockito.doReturn(headers).`when`(mockResponse).headers
            if (executeCount == 1) {
                val failingStream = object : java.io.InputStream() {
                    override fun read(): Int = throw java.net.SocketTimeoutException("Read timed out")
                    override fun read(b: ByteArray, off: Int, len: Int): Int =
                        throw java.net.SocketTimeoutException("Read timed out")
                }
                Mockito.doReturn(failingStream).`when`(mockResponse).body
            } else {
                Mockito.doReturn(ByteArrayInputStream(html)).`when`(mockResponse).body
            }
            extractor.extractData(mockResponse)
        }.`when`(restTemplate).execute(
            Mockito.any(URI::class.java),
            Mockito.eq(HttpMethod.GET),
            Mockito.any(),
            Mockito.any(ResponseExtractor::class.java)
        )

        val result = retryExtractor.extract("http://example.com/landing", emptyList(), "TEST")

        assertEquals(2, executeCount)
        assertEquals("HTML_FALLBACK", result.methodUsed)
        assertTrue(result.emails.any { it.email == "retry@test.edu" })
    }

    @Test
    fun `does not retry download for non recoverable HTTP error`() {
        val retryProperties = PdfExtractionProperties(maxRetries = 2, retryBackoffMs = 0)
        val retryExtractor = PdfEmailExtractor(restTemplate, plainTextExtractor, retryProperties)

        Mockito.doThrow(RuntimeException("404 Not Found"))
            .`when`(restTemplate).execute(
                Mockito.any(URI::class.java),
                Mockito.eq(HttpMethod.GET),
                Mockito.any(),
                Mockito.any(ResponseExtractor::class.java)
            )

        val result = retryExtractor.extract("http://example.com/missing.pdf", emptyList(), "TEST")

        assertEquals("PDF_DOWNLOAD_FAILED", result.failureReason)
        Mockito.verify(restTemplate, Mockito.times(1)).execute(
            Mockito.any(URI::class.java),
            Mockito.eq(HttpMethod.GET),
            Mockito.any(),
            Mockito.any(ResponseExtractor::class.java)
        )
    }

    @Test
    fun `returns PDF_DOWNLOAD_FAILED on HTTP error`() {
        Mockito.doThrow(RuntimeException("Connection refused"))
            .`when`(restTemplate).execute(
                Mockito.any(URI::class.java),
                Mockito.eq(HttpMethod.GET),
                Mockito.any(),
                Mockito.any(ResponseExtractor::class.java)
            )

        val result = extractor.extract("http://example.com/missing.pdf", emptyList(), "TEST")

        assertEquals("PDF_DOWNLOAD_FAILED", result.failureReason)
    }

    @Test
    fun `matches single author to single email`() {
        val pdfBytes = readPdfFixture("pdf/standard.pdf")
        stubPdfDownload(pdfBytes, MediaType.APPLICATION_PDF)

        val authors = listOf(PaperAuthor("Single", "Author", "0000-0001", "Some Lab", true))
        val result = extractor.extract("http://example.com/test.pdf", authors, "TEST")

        assertTrue(result.emails.isNotEmpty())
    }

    @Test
    fun `rejects invalid URL with zero requests`() {
        val result = extractor.extract("://invalid", emptyList(), "TEST")

        assertEquals("PDF_DOWNLOAD_FAILED", result.failureReason)
        assertEquals(0, result.httpRequests)
    }

    @Test
    fun `allows PDF by magic bytes despite non-PDF content type`() {
        val pdfBytes = readPdfFixture("pdf/standard.pdf")
        stubPdfDownload(pdfBytes, MediaType.TEXT_HTML)

        val result = extractor.extract("http://example.com/test.pdf", emptyList(), "TEST")

        assertEquals("PDF_PARSE", result.methodUsed)
        assertTrue(result.emails.isNotEmpty())
    }

    @Test
    fun `does not attach identity when two authors share the email initial and surname (I-2)`() {
        // I-2: 首字母命中不是身份依据 —— 甲的邮箱不得绑上乙的 ORCID/作者ID。
        val html = "<html><body>Contact: jsmith@ox.ac.uk</body></html>".toByteArray()
        stubPdfDownload(html, MediaType.TEXT_HTML)
        val authors = listOf(
            PaperAuthor("John", "Smith", "0000-0001", "Oxford, UK", true, openAlexAuthorId = "A5023888391"),
            PaperAuthor("James", "Smith", "0000-0002", "Cambridge, UK", false, openAlexAuthorId = "A5086928770")
        )

        val result = extractor.extract("http://example.com/landing", authors, "TEST")

        val email = result.emails.single()
        assertEquals("jsmith@ox.ac.uk", email.email)
        assertNull(email.givenNames)
        assertNull(email.familyNames)
        assertNull(email.orcidId)
        assertNull(email.openAlexAuthorId)
    }

    @Test
    fun `does not attach identity when two authors share the same full name (I-2)`() {
        val html = "<html><body>Contact: john.smith@ox.ac.uk</body></html>".toByteArray()
        stubPdfDownload(html, MediaType.TEXT_HTML)
        val authors = listOf(
            PaperAuthor("John", "Smith", "0000-0001", "Oxford, UK", true, openAlexAuthorId = "A5023888391"),
            PaperAuthor("John", "Smith", "0000-0002", "Cambridge, UK", false, openAlexAuthorId = "A5086928770")
        )

        val result = extractor.extract("http://example.com/landing", authors, "TEST")

        val email = result.emails.single()
        assertNull(email.givenNames)
        assertNull(email.familyNames)
        assertNull(email.orcidId)
        assertNull(email.openAlexAuthorId)
    }

    @Test
    fun `does not attach identity on a surname-substring collision (I-2)`() {
        // 两个作者的姓名都被 weilian 命中（li ⊂ lian）——本地部分无法唯一定位，保留邮箱但不带身份。
        val html = "<html><body>Contact: weilian@ox.ac.uk</body></html>".toByteArray()
        stubPdfDownload(html, MediaType.TEXT_HTML)
        val authors = listOf(
            PaperAuthor("Wei", "Li", null, "Oxford, UK", false, openAlexAuthorId = "A111"),
            PaperAuthor("Wei", "Lian", null, "Cambridge, UK", false, openAlexAuthorId = "A222")
        )

        val result = extractor.extract("http://example.com/landing", authors, "TEST")

        val email = result.emails.single()
        assertNull(email.familyNames)
        assertNull(email.openAlexAuthorId)
    }

    @Test
    fun `binds the unique full-name combination and carries the author identity (I-2)`() {
        val html = "<html><body>Contact: jane.doe@university.edu</body></html>".toByteArray()
        stubPdfDownload(html, MediaType.TEXT_HTML)
        val authors = listOf(
            PaperAuthor("John", "Smith", "0000-0001", "Oxford, UK", true, openAlexAuthorId = "A5023888391"),
            PaperAuthor("Jane", "Doe", "0000-0002", "Cambridge, UK", true, openAlexAuthorId = "A5086928770")
        )

        val result = extractor.extract("http://example.com/landing", authors, "TEST")

        val email = result.emails.single()
        assertEquals("Jane", email.givenNames)
        assertEquals("Doe", email.familyNames)
        assertEquals("0000-0002", email.orcidId)
        assertEquals("A5086928770", email.openAlexAuthorId)
    }

    @Test
    fun `keeps the sole-author sole-email attribution (I-2)`() {
        val html = "<html><body>Contact: single.author@uni.edu</body></html>".toByteArray()
        stubPdfDownload(html, MediaType.TEXT_HTML)
        val authors = listOf(
            PaperAuthor("Single", "Author", "0000-0009", "Some Lab", true, openAlexAuthorId = "A999")
        )

        val result = extractor.extract("http://example.com/landing", authors, "TEST")

        val email = result.emails.single()
        assertEquals("Single", email.givenNames)
        assertEquals("Author", email.familyNames)
        assertEquals("0000-0009", email.orcidId)
        assertEquals("A999", email.openAlexAuthorId)
    }

    private fun readPdfFixture(path: String): ByteArray {
        return javaClass.classLoader.getResource(path)!!.readBytes()
    }

    @Suppress("UNCHECKED_CAST")
    private fun stubPdfDownload(bytes: ByteArray, contentType: MediaType) {
        Mockito.doAnswer { invocation: InvocationOnMock ->
            val extractor = invocation.getArgument<ResponseExtractor<*>>(3)
            val mockResponse = Mockito.mock(ClientHttpResponse::class.java)
            val headers = HttpHeaders().apply { this.contentType = contentType }
            Mockito.doReturn(headers).`when`(mockResponse).headers
            Mockito.doReturn(ByteArrayInputStream(bytes)).`when`(mockResponse).body
            extractor.extractData(mockResponse)
        }.`when`(restTemplate).execute(
            Mockito.any(URI::class.java),
            Mockito.eq(HttpMethod.GET),
            Mockito.any(),
            Mockito.any(ResponseExtractor::class.java)
        )
    }
}
