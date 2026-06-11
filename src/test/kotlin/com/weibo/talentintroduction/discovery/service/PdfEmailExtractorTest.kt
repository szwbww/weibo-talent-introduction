package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.PdfExtractionProperties
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
    fun `rejects non-PDF content type`() {
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
    fun `returns PDF_DOWNLOAD_FAILED on HTTP error`() {
        Mockito.doThrow(RuntimeException("Connection refused"))
            .`when`(restTemplate).execute(
                Mockito.any(URI::class.java),
                Mockito.eq(HttpMethod.GET),
                Mockito.any(),
                Mockito.any<ResponseExtractor<ByteArray>>()
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

    private fun readPdfFixture(path: String): ByteArray {
        return javaClass.classLoader.getResource(path)!!.readBytes()
    }

    @Suppress("UNCHECKED_CAST")
    private fun stubPdfDownload(bytes: ByteArray, contentType: MediaType) {
        Mockito.doAnswer { invocation: InvocationOnMock ->
            val extractor = invocation.getArgument<ResponseExtractor<ByteArray>>(3)
            val mockResponse = Mockito.mock(ClientHttpResponse::class.java)
            val headers = HttpHeaders().apply { this.contentType = contentType }
            Mockito.doReturn(headers).`when`(mockResponse).headers
            Mockito.doReturn(ByteArrayInputStream(bytes)).`when`(mockResponse).body
            extractor?.extractData(mockResponse)
        }.`when`(restTemplate).execute(
            Mockito.any(URI::class.java),
            Mockito.eq(HttpMethod.GET),
            Mockito.any(),
            Mockito.any<ResponseExtractor<ByteArray>>()
        )
    }
}
