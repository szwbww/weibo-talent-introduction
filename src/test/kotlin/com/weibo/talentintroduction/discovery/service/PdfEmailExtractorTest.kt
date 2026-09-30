package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.BoundedFulltextHttp
import com.weibo.talentintroduction.config.BoundedHttpExecutor
import com.weibo.talentintroduction.config.PdfExtractionProperties
import com.weibo.talentintroduction.config.SlowHttpServer
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RequestCallback
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.ResponseExtractor
import org.springframework.web.client.RestTemplate
import java.io.ByteArrayInputStream
import java.net.SocketTimeoutException
import java.net.URI
import java.time.Instant
import java.util.zip.ZipInputStream
import org.apache.pdfbox.pdmodel.PDDocument
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import javax.net.ssl.SSLHandshakeException

class PdfEmailExtractorTest {
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val plainTextExtractor = PlainTextEmailExtractor()
    private val properties = PdfExtractionProperties()
    /**
     * 既有单元测试用 mock [RestTemplate] 直接打桩 `execute`，因此让执行器原样委托给它；
     * 真正「在飞」的连接/响应头/响应体约束由本文件末尾基于 [BoundedFulltextHttp] 的用例覆盖。
     */
    private val passThroughBoundedHttp = object : BoundedHttpExecutor {
        override fun <T : Any> getForObject(
            base: RestTemplate,
            url: String,
            responseType: Class<T>,
            connectCapMs: Long,
            readCapMs: Long,
            deadline: Instant?
        ): T? = base.getForObject(url, responseType)

        override fun <T> execute(
            base: RestTemplate,
            uri: URI,
            connectCapMs: Long,
            readCapMs: Long,
            deadline: Instant?,
            responseExtractor: ResponseExtractor<T>
        ): T? = base.execute(uri, HttpMethod.GET, null, responseExtractor)
    }

    private val extractor = PdfEmailExtractor(restTemplate, plainTextExtractor, properties, passThroughBoundedHttp)

    @Test
    fun `original 62 page contact section and selected page mailto remain bounded evidence`() {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(requireNotNull(javaClass.getResourceAsStream("/discovery/pdf-contact-coverage.zip"))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        val expected = mapOf(
            "salvatore.cuomo@unina.it" to "Salvatore Cuomo",
            "vincenzo.schianodicola@unina.it" to "Vincenzo Schiano Di Cola",
            "fabio.giampaolo@unina.it" to "Fabio Giampaolo",
            "grozza@sissa.it" to "Gianluigi Rozza",
            "mara4513@colorado.edu" to "Maziar Raissi"
        )
        val id = "W4288039037"
        val pdf = requireNotNull(entries["sources/$id/source.pdf"])
        val metadata = jacksonObjectMapper().readTree(requireNotNull(entries["sources/$id/metadata.json"]))
        val authors = metadata.path("authorships").map {
            val name = it.path("author").path("display_name").asText()
            PaperAuthor(name.substringBeforeLast(' '), name.substringAfterLast(' '), null, null, false)
        }
        PDDocument.load(pdf).use { document ->
            assertEquals(62, document.numberOfPages)
            assertEquals(listOf(1, 2, 62), extractor.selectedPages(document.numberOfPages))
            val contacts = PdfAuthorContactLayout.collect(document, 2, authors, 1)
            for ((email, name) in expected) {
                val contact = contacts.singleOrNull { it.email == email }
                    ?: error("missing $email, observed contacts: $contacts")
                assertEquals(62, contact.page)
                assertEquals(name, "${authors[contact.authorIndex].givenNames} ${authors[contact.authorIndex].familyNames}")
            }
        }
        assertEquals(listOf(1), extractor.selectedPages(1))
        assertEquals(listOf(1, 2), extractor.selectedPages(2))
        assertEquals(listOf(1, 2), PdfEmailExtractor(restTemplate, plainTextExtractor,
            PdfExtractionProperties(tailPages = 0), passThroughBoundedHttp).selectedPages(62))
        stubPdfDownload(pdf, MediaType.APPLICATION_PDF)
        val withoutTail = PdfEmailExtractor(restTemplate, plainTextExtractor,
            PdfExtractionProperties(tailPages = 0), passThroughBoundedHttp)
            .extract("https://paper.test/no-tail", authors, "TEST")
        assertEquals(1, withoutTail.httpRequests)
        assertTrue(withoutTail.emails.none { it.email in expected.keys })
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            PdfExtractionProperties(tailPages = 2)
        }
        val parsed = extractOwnershipContent(pdf, MediaType.APPLICATION_PDF, authors)
        for ((email, name) in expected) {
            val result = parsed.emails.single { it.email == email }
            assertEquals(name, "${result.givenNames} ${result.familyNames}")
            assertNotNull(result.identityEvidence)
        }
        assertEquals(expected.size, parsed.emails.count { it.email in expected.keys && it.identityEvidence != null })
        val links = jacksonObjectMapper().readTree(requireNotNull(entries["missed-pdf-links.json"]))
        assertEquals(11, links.size())
        for (paper in listOf("W4292779060", "W4385245566", "W4293584584")) {
            val source = requireNotNull(entries["sources/$paper/source.pdf"])
            val sourceMetadata = jacksonObjectMapper().readTree(requireNotNull(entries["sources/$paper/metadata.json"]))
            val sourceAuthors = sourceMetadata.path("authorships").map {
                val name = it.path("author").path("display_name").asText()
                PaperAuthor(name.substringBeforeLast(' '), name.substringAfterLast(' '), null, null, false)
            }
            val result = extractOwnershipContent(source, MediaType.APPLICATION_PDF, sourceAuthors)
            for (link in links.filter { it.path("paper").asText() == paper }) {
                val email = link.path("uri").asText().removePrefix("mailto:").lowercase()
                assertEquals(1, result.emails.count { it.email == email }, "$paper $email")
                if (paper == "W4292779060") {
                    assertNull(result.emails.single { it.email == email }.givenNames)
                    assertNull(result.emails.single { it.email == email }.identityEvidence)
                }
            }
        }
    }

    /**
     * I-1～I-6：归档三篇真实异常原文（逐字复制，SHA256 校验）经真实 PDFBox / layout / resolver /
     * extractor 重放，并现场写出 `target/discovery-plan-acceptance/pdf-contact-integrity.json`
     * （fixture SHA、每篇 contacts/resolved、合成控制、正向控制、I-3 文本边界表）。
     * 报告先落盘再断言：断言失败也保留本次观测数据。
     */
    @Test
    fun `three archived original cases replay with the shipped rules and emit acceptance report (I-1 I-2 I-3 I-4 I-6)`() {
        val archive = threeCaseArchive()
        val members = threeCaseMembers()
        val manifest = jacksonObjectMapper().readTree(requireNotNull(members["manifest.json"]))
        val observations = threeCaseNames().map { observeThreeCase(members, it) }
        val textTable = bareMarkerTextBoundaryCases().map { (label, input, expected) ->
            mapOf("label" to label, "input" to input, "expected" to expected,
                "actual" to plainTextExtractor.extract(input))
        }
        fun identityRow(email: com.weibo.talentintroduction.discovery.domain.AuthorEmail) = mapOf(
            "email" to email.email, "givenNames" to email.givenNames, "familyNames" to email.familyNames,
            "isCorresponding" to email.isCorresponding, "affiliation" to email.affiliation,
            "orcidId" to email.orcidId, "institutionType" to email.institutionType,
            "openAlexAuthorId" to email.openAlexAuthorId, "identityEvidence" to email.identityEvidence,
            "institutionName" to email.institutionName, "institutionCountry" to email.institutionCountry,
            "institutionSource" to email.institutionSource
        )
        val report = mapOf(
            "task" to "fast-p-child-01",
            "fixture" to mapOf(
                "archive" to "src/test/resources/discovery/ownership-20260930.zip",
                "archiveSha256" to fixtureSha256(archive),
                "members" to members.entries.sortedBy { it.key }.associate { it.key to fixtureSha256(it.value) },
                "manifestPdfSha256" to manifest.path("pdfSha256"),
                "manifestMetadataSha256" to manifest.path("metadataSha256")
            ),
            "papers" to observations.map { observation ->
                mapOf(
                    "case" to observation.case, "designation" to "REAL_ORIGINAL",
                    "pdfSha256" to observation.pdfSha256, "metadataSha256" to observation.metadataSha256,
                    "methodUsed" to observation.methodUsed, "httpRequests" to observation.httpRequests,
                    "fulltextObtained" to observation.fulltextObtained,
                    "mailtoClues" to observation.mailtoClues,
                    "authors" to observation.authors.map {
                        mapOf("givenNames" to it.givenNames, "familyNames" to it.familyNames,
                            "orcidId" to it.orcidId, "openAlexAuthorId" to it.openAlexAuthorId,
                            "isCorresponding" to it.isCorresponding)
                    },
                    "contacts" to observation.contacts.map {
                        mapOf("email" to it.email, "authorIndex" to it.authorIndex, "page" to it.page,
                            "authorText" to it.authorText, "contactText" to it.contactText)
                    },
                    "resolved" to observation.resolved.map(::identityRow)
                )
            },
            "syntheticControls" to markerOwnershipControls(),
            "positiveControls" to pdfPositiveControls().map { control ->
                mapOf(
                    "label" to control.label, "designation" to control.designation,
                    "authors" to control.authors.map { "${it.givenNames} ${it.familyNames}" },
                    "methodUsed" to control.parsed.methodUsed, "httpRequests" to control.parsed.httpRequests,
                    "contacts" to control.contacts.map {
                        mapOf("email" to it.email, "authorIndex" to it.authorIndex, "page" to it.page,
                            "authorText" to it.authorText)
                    },
                    "resolved" to control.parsed.emails.map(::identityRow)
                )
            },
            "textTable" to textTable
        )
        val output = java.nio.file.Paths.get("target/discovery-plan-acceptance/pdf-contact-integrity.json")
        java.nio.file.Files.createDirectories(output.parent)
        java.nio.file.Files.write(output, jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(report))
        assertTrue(java.nio.file.Files.size(output) > 0, "报告必须落盘")

        // I-6：归档与成员字节一致（逐字复制，不重生成、不重排）。
        assertEquals("9f5802b0d0862126c570c81f15a0e6053d26fa3abc99ab25ce69aaa81fec6a27", fixtureSha256(archive))
        val expectedPdfSha = mapOf(
            "chee" to "0daf96634f750d280bb1330d9b95d87f4ea04e54bee813db5c372092028fdb99",
            "lyderic" to "7459f1af390be438463bbd1b97d5cc79a183fd2ffb09310e2448d12736fb8f9b",
            "lun" to "7e2468ed9ad9ada830f943a1a5b1bc423704c0a6b51ea672fe250b5aa74805ba")
        for (observation in observations) {
            assertEquals(expectedPdfSha.getValue(observation.case), observation.pdfSha256, observation.case)
            assertEquals(manifest.path("pdfSha256").path("${observation.case}.pdf").asText(),
                observation.pdfSha256, "${observation.case} manifest")
            assertEquals("PDF_PARSE", observation.methodUsed, observation.case)
            assertEquals(1, observation.httpRequests, observation.case)
            assertEquals(true, observation.fulltextObtained, observation.case)
            // X-1：错误归属不允许出现在中间 Contact，也不允许出现在最终 emails。
            assertTrue(observation.contacts.isEmpty(), "${observation.case} contacts: ${observation.contacts}")
            for (email in observation.resolved) {
                assertNull(email.givenNames, "${observation.case} ${email.email}")
                assertNull(email.familyNames, "${observation.case} ${email.email}")
                assertNull(email.orcidId, "${observation.case} ${email.email}")
                assertNull(email.openAlexAuthorId, "${observation.case} ${email.email}")
                assertNull(email.institutionName, "${observation.case} ${email.email}")
                assertNull(email.institutionCountry, "${observation.case} ${email.email}")
                assertNull(email.institutionSource, "${observation.case} ${email.email}")
                assertNull(email.identityEvidence, "${observation.case} ${email.email}")
                assertFalse(email.isCorresponding, "${observation.case} ${email.email}")
            }
        }
        val observed = observations.associate { it.case to it.resolved.map { email -> email.email }.toSet() }
        assertEquals(setOf("ysjang@ucla.edu", "cheewei.wong@ucla.edu"), observed.getValue("chee"))
        assertEquals(setOf("lyderic.bocquet@ens.fr", "alessandro.siria@ens.fr"), observed.getValue("lyderic"))
        // O-2/I-3：断裂后缀 `yue@msn.com` 在 contacts 与 resolved 两路都缺席，正确 mailto 保留为线索。
        assertEquals(setOf("lun_yue@msn.com", "mgaarde1@lsu.edu"), observed.getValue("lun"))

        // I-3 文本边界表逐项复核（与 PlainTextEmailExtractorTest 同源输入）。
        for ((label, input, expected) in bareMarkerTextBoundaryCases()) {
            assertEquals(expected, plainTextExtractor.extract(input), "$label: $input")
        }

        // N-1/N-2 正向控制：正常唯一联系人仍完整绑定（真实现有 PDF + 合成一人两邮箱）。
        val positive = pdfPositiveControls()
        val real = positive.single { it.label == "realNormalPdfSingleOwnedMailbox" }
        val realOwned = real.parsed.emails.single { it.email == "davidechicco@davidechicco.it" }
        assertEquals("Davide Chicco", "${realOwned.givenNames} ${realOwned.familyNames}")
        assertTrue(realOwned.identityEvidence!!.startsWith("SOURCE_SHA256:"))
        val synthetic = positive.single { it.label == "syntheticOneAuthorTwoMailboxes" }
        assertEquals(setOf("first@uni.edu", "second@uni.edu"), synthetic.parsed.emails.map { it.email }.toSet())
        assertTrue(synthetic.parsed.emails.all {
            it.givenNames == "Jane" && it.familyNames == "Doe" && it.openAlexAuthorId == "A123" &&
                it.institutionName == "Jane University" && it.identityEvidence!!.startsWith("SOURCE_SHA256:")
        }, synthetic.parsed.emails.toString())
    }

    @Test
    fun `mailto URI is a single filtered clue and cannot override text ownership`() {
        val pdf = PDDocument.load(ownershipPdf("Jane Doe: owner@uni.edu")).use { doc ->
            for (uri in listOf(
                "mailto:owner@uni.edu?subject=hello&cc=other@uni.edu",
                "mailto:unknown@uni.edu?bcc=secret@uni.edu",
                "mailto:one@uni.edu,two@uni.edu",
                "mailto:support@uni.edu",
                "https://example.edu/?email=web@uni.edu"
            )) {
                val annotation = org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink()
                annotation.action = org.apache.pdfbox.pdmodel.interactive.action.PDActionURI().apply { this.uri = uri }
                doc.getPage(0).annotations.add(annotation)
            }
            java.io.ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
        val result = extractOwnershipContent(pdf, MediaType.APPLICATION_PDF,
            listOf(PaperAuthor("Jane", "Doe", null, null, false)))
        assertEquals(1, result.emails.count { it.email == "owner@uni.edu" })
        assertEquals("Jane", result.emails.single { it.email == "owner@uni.edu" }.givenNames)
        val unknown = result.emails.single { it.email == "unknown@uni.edu" }
        assertNull(unknown.givenNames)
        assertNull(unknown.identityEvidence)
        assertTrue(result.emails.none { it.email in setOf("other@uni.edu", "secret@uni.edu",
            "one@uni.edu", "two@uni.edu", "support@uni.edu", "web@uni.edu") })
    }
    @Test
    fun `contact section stops before affiliations and cannot bind a later reference author`() {
        val pdf = ownershipPdf("Authors and Affiliations", "Jane Doe and John Smith",
            "Jane Doe", "owner@uni.edu", "1 Department of Physics",
            "References", "John Smith", "foreign@uni.edu")
        val result = extractOwnershipContent(pdf, MediaType.APPLICATION_PDF,
            listOf(PaperAuthor("Jane", "Doe", null, null, false),
                PaperAuthor("John", "Smith", null, null, false)))
        assertEquals("Jane", result.emails.single { it.email == "owner@uni.edu" }.givenNames)
        assertNull(result.emails.single { it.email == "foreign@uni.edu" }.givenNames)
    }


    @Test
    fun `original PDFs retain page and contact provenance for only four author mailboxes`() {
        val expected = mapOf(
            "W3014974815" to mapOf("k.maier-hein@dkfz.de" to "Klaus H. Maier-Hein"),
            "W2999309192" to mapOf("davidechicco@davidechicco.it" to "Davide Chicco"),
            "W2907492528" to mapOf(
                "shirui.pan@monash.edu" to "Shirui Pan",
                "psyu@uic.edu" to "Philip S. Yu"
            )
        )
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(requireNotNull(javaClass.getResourceAsStream("/discovery/source-contact-recall.zip"))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        for ((id, pairs) in expected) {
            val pdf = requireNotNull(entries["sources/$id/source.pdf"])
            val metadata = jacksonObjectMapper().readTree(requireNotNull(entries["sources/$id/metadata.json"]))
            val authors = metadata.path("authorships").map {
                val name = it.path("author").path("display_name").asText()
                PaperAuthor(name.substringBeforeLast(' '), name.substringAfterLast(' '),
                    it.path("author").path("orcid").asText(null), null, it.path("is_corresponding").asBoolean(),
                    openAlexAuthorId = it.path("author").path("id").asText(null))
            }
            val contacts = PDDocument.load(pdf).use { PdfAuthorContactLayout.collect(it, 2, authors) }
            for ((mailbox, name) in pairs) {
                val contact = contacts.singleOrNull { it.email == mailbox }
                    ?: error("$id $mailbox layout contacts: $contacts")
                assertEquals(1, contact.page)
                assertTrue(contact.authorText.contains(name), "$id author region: ${contact.authorText}")
                assertTrue(contact.contactText.contains(mailbox), "$id contact: ${contact.contactText}")
                if (id == "W2907492528") {
                    assertTrue(!contact.contactText.contains("graphs and manifolds") &&
                        !contact.contactText.contains("Hamilton"), contact.contactText)
                }
                val resolved = extractOwnershipContent(pdf, MediaType.APPLICATION_PDF, authors)
                    .emails.single { it.email == mailbox }
                assertEquals(name, "${resolved.givenNames} ${resolved.familyNames}", "$id $mailbox")
                assertEquals("SOURCE_SHA256:" +
                    com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.hash(contact.evidenceText),
                    resolved.identityEvidence)
            }
            if (id == "W2907492528") {
                val resolved = extractOwnershipContent(pdf, MediaType.APPLICATION_PDF, authors).emails
                for (mailbox in listOf("zonghan.wu-3@student.uts.edu.au",
                    "fengwen.chen@student.uts.edu.au", "guodong.long@uts.edu.au",
                    "chengqi.zhang@uts.edu.au")) {
                    val unknown = resolved.single { it.email == mailbox }
                    assertNull(unknown.givenNames, mailbox)
                    assertNull(unknown.identityEvidence, mailbox)
                }
            }
        }
    }
    @Test
    fun `contact entry resolves an opaque mailbox from the original name`() {
        stubPdfDownload("<p>Jane Doe: r142@university.edu</p>".toByteArray(), MediaType.TEXT_HTML)
        val result = extractor.extract("https://paper.test/fulltext", listOf(
            PaperAuthor("Jane", "Doe", "0000-0002", "Lab", true),
            PaperAuthor("John", "Smith", null, null, false)), "TEST")
        assertEquals("Jane", result.emails.single().givenNames)
        assertNotNull(result.emails.single().identityEvidence)
    }

    @Test
    fun `sole author and matching mailbox spelling do not prove ownership`() {
        stubPdfDownload("<p>Contact: jane.doe@university.edu</p>".toByteArray(), MediaType.TEXT_HTML)
        val result = extractor.extract("https://paper.test/fulltext", listOf(
            PaperAuthor("Jane", "Doe", "0000-0002", "Lab", true)), "TEST")
        assertNull(result.emails.single().givenNames)
        assertNull(result.emails.single().orcidId)
    }

    @Test
    fun `published HTML takes the real extractor path without guessing shared notes`() {
        for (case in sourceOwnershipCases()) {
            val result = extractOwnershipContent(case.path("html").asText().toByteArray(), MediaType.TEXT_HTML, sourceOwnershipAuthors(case))
            assertEquals("HTML_FALLBACK", result.methodUsed)
            for (email in result.emails) {
                val expected = case.path("expected").path(email.email)
                if (expected.isNull) assertNull(email.givenNames, email.email)
                else assertEquals(expected.asText(), "${email.givenNames} ${email.familyNames}")
            }
            assertEquals(case.path("expected").size(), result.emails.size)
        }
    }

    @Test
    fun `original Anubis challenge and genuinely empty HTML are invalid content while academic prose is readable`() {
        val entries = htmlContactEntries()
        val challenge = requireNotNull(entries["sources/W4381304672/source.html"])
        val original = extractOwnershipContent(challenge, MediaType.TEXT_HTML, emptyList())
        assertEquals("PDF_DOWNLOAD_FAILED", original.failureReason)
        assertEquals("INVALID_CONTENT", original.downloadFailureCategory)
        assertEquals(false, original.fulltextObtained)
        assertTrue(original.emails.isEmpty())
        for (html in listOf("<html><head><script>const x = 1</script><style>p{color:red}</style></head>" +
            "<body><!-- comment -->  </body></html>", "<html><body>&nbsp; </body></html>")) {
            val empty = extractOwnershipContent(html.toByteArray(), MediaType.TEXT_HTML, emptyList())
            assertEquals("PDF_DOWNLOAD_FAILED", empty.failureReason)
            assertEquals("INVALID_CONTENT", empty.downloadFailureCategory)
            assertEquals(false, empty.fulltextObtained)
        }
        for (html in listOf("<html><body><article>We measured a bot challenge in our research.</article></body></html>",
            "<html><head><title>Making sure you're not a bot!</title></head>" +
                "<body>Research on bot challenge protocols.</body></html>",
            "<html><script id='anubis_challenge'>data</script><body>Challenge-response research.</body></html>")) {
            val readable = extractOwnershipContent(html.toByteArray(), MediaType.TEXT_HTML, emptyList())
            assertEquals("NO_EMAIL_IN_HTML", readable.failureReason)
            assertEquals(true, readable.fulltextObtained)
        }
    }

    @Test
    fun `PDF contact field resolves ownership while bare mailbox remains unresolved`() {
        val result = extractOwnershipContent(ownershipPdf("Jane Doe: opaque@uni.edu", "thirdparty@uni.edu"),
            MediaType.APPLICATION_PDF, listOf(PaperAuthor("Jane", "Doe", "real-id", "Lab", true)))
        assertEquals("PDF_PARSE", result.methodUsed)
        assertEquals("Jane", result.emails.first { it.email == "opaque@uni.edu" }.givenNames)
        assertNull(result.emails.first { it.email == "thirdparty@uni.edu" }.givenNames)
    }

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
        val html = "<html><body>Contact: Jane Doe: jane.doe@university.edu</body></html>".toByteArray()
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
            PdfExtractionProperties(htmlFallbackEnabled = false),
            passThroughBoundedHttp
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
        val retryExtractor = PdfEmailExtractor(restTemplate, plainTextExtractor, retryProperties, passThroughBoundedHttp)
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
        val retryExtractor = PdfEmailExtractor(restTemplate, plainTextExtractor, retryProperties, passThroughBoundedHttp)

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
    fun `explicit full-name contact entry carries the author identity (I-2)`() {
        val html = "<html><body>Contact: Jane Doe: jane.doe@university.edu</body></html>".toByteArray()
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
    fun `sole-author mailbox without a source name remains unresolved (I-2)`() {
        val html = "<html><body>Contact: single.author@uni.edu</body></html>".toByteArray()
        stubPdfDownload(html, MediaType.TEXT_HTML)
        val authors = listOf(
            PaperAuthor("Single", "Author", "0000-0009", "Some Lab", true, openAlexAuthorId = "A999")
        )

        val result = extractor.extract("http://example.com/landing", authors, "TEST")

        val email = result.emails.single()
        assertNull(email.givenNames)
        assertNull(email.familyNames)
        assertNull(email.orcidId)
        assertNull(email.openAlexAuthorId)
    }

    @Test
    fun `HTML without an email counts as content obtained and stays NO_EMAIL_IN_HTML (I-3)`() {
        // c10（I-3）：取到 HTML 就是「内容已获取」，与下载失败分开；HTML 不保证是论文全文，故单列原因。
        val html = "<html><body>No contact info here</body></html>".toByteArray()
        stubPdfDownload(html, MediaType.TEXT_HTML)

        val result = extractor.extract("http://example.com/landing", emptyList(), "TEST")

        assertEquals("HTML_FALLBACK", result.methodUsed)
        assertEquals("NO_EMAIL_IN_HTML", result.failureReason)
        assertEquals(true, result.fulltextObtained)
        assertEquals(1, result.httpRequests)
    }

    @Test
    fun `an expired shared deadline issues no download and reports TIMEOUT (I-1)`() {
        // c10（I-1）：时限是单篇共享的 —— 已过期就一个请求都不发，也不能在这里重新计时。
        val result = extractor.extract(
            "http://example.com/paper.pdf", emptyList(), "TEST", Instant.now().minusSeconds(1)
        )

        assertEquals("PDF_DOWNLOAD_FAILED", result.failureReason)
        assertEquals("TIMEOUT", result.downloadFailureCategory)
        assertEquals(false, result.fulltextObtained)
        assertEquals(0, result.httpRequests)
        Mockito.verifyNoInteractions(restTemplate)
    }

    @Test
    fun `aborts a download that runs past the shared deadline (I-1)`() {
        // c10（I-1）：下载途中越过 deadline 立即放弃，不把整个响应体读完。
        var chunksServed = 0
        Mockito.doAnswer { invocation: InvocationOnMock ->
            val responseExtractor = invocation.getArgument<ResponseExtractor<*>>(3)
            val mockResponse = Mockito.mock(ClientHttpResponse::class.java)
            Mockito.doReturn(HttpHeaders().apply { contentType = MediaType.APPLICATION_PDF })
                .`when`(mockResponse).headers
            val slowBody = object : java.io.InputStream() {
                private var remainingChunks = 200

                override fun read(): Int = throw UnsupportedOperationException("single-byte read")

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (remainingChunks == 0) return -1
                    remainingChunks--
                    chunksServed++
                    Thread.sleep(5)
                    java.util.Arrays.fill(b, off, off + minOf(len, 8192), 0x41.toByte())
                    return minOf(len, 8192)
                }
            }
            Mockito.doReturn(slowBody).`when`(mockResponse).body
            responseExtractor.extractData(mockResponse)
        }.`when`(restTemplate).execute(
            Mockito.any(URI::class.java),
            Mockito.eq(HttpMethod.GET),
            Mockito.any(),
            Mockito.any(ResponseExtractor::class.java)
        )

        val result = extractor.extract(
            "http://example.com/slow.pdf", emptyList(), "TEST", Instant.now().plusMillis(20)
        )

        assertEquals("TIMEOUT", result.downloadFailureCategory)
        assertEquals(false, result.fulltextObtained)
        assertEquals(1, result.httpRequests)
        assertTrue(chunksServed < 200, "越过共享 deadline 后不得继续读完整个响应体")
    }

    @Test
    fun `classifies download failures into stable low-cardinality buckets (I-3, V-2)`() {
        assertEquals("HTTP_403", downloadFailureCategory(HttpClientErrorException(HttpStatus.FORBIDDEN)))
        assertEquals("HTTP_404", downloadFailureCategory(HttpClientErrorException(HttpStatus.NOT_FOUND)))
        assertEquals("HTTP_429", downloadFailureCategory(HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS)))
        assertEquals("HTTP_5XX", downloadFailureCategory(HttpServerErrorException(HttpStatus.BAD_GATEWAY)))
        assertEquals("HTTP_4XX", downloadFailureCategory(HttpClientErrorException(HttpStatus.GONE)))
        assertEquals("TLS_ERROR", downloadFailureCategory(
            ResourceAccessException("handshake failed", SSLHandshakeException("unable to find valid certification path"))
        ))
        assertEquals("TIMEOUT", downloadFailureCategory(
            ResourceAccessException("Read timed out", SocketTimeoutException("Read timed out"))
        ))
        assertEquals("NETWORK_ERROR", downloadFailureCategory(
            ResourceAccessException("Connection refused", java.net.ConnectException("Connection refused"))
        ))
    }

    @Test
    fun `a rejected download is not content obtained while an email-less PDF is (I-3)`() {
        stubDownloadFailure(HttpClientErrorException(HttpStatus.FORBIDDEN))
        val rejected = extractor.extract("http://example.com/blocked.pdf", emptyList(), "TEST")
        assertEquals(false, rejected.fulltextObtained)

        stubPdfDownload(readPdfFixture("pdf/no_email.pdf"), MediaType.APPLICATION_PDF)
        val emailLess = extractor.extract("http://example.com/no-email.pdf", emptyList(), "TEST")
        assertEquals("NO_EMAIL_IN_TEXT", emailLess.failureReason)
        assertEquals(true, emailLess.fulltextObtained)
    }

    /** 用同一个 URL 的不同 downloadFailureCategory 断言分类：只需替换 execute 抛出的异常。 */
    private fun downloadFailureCategory(exception: Exception): String? {
        stubDownloadFailure(exception)
        return extractor.extract("http://example.com/paper.pdf", emptyList(), "TEST").downloadFailureCategory
    }

    private fun stubDownloadFailure(exception: Exception) {
        Mockito.doThrow(exception).`when`(restTemplate).execute(
            Mockito.any(URI::class.java),
            Mockito.eq(HttpMethod.GET),
            Mockito.any(),
            Mockito.any(ResponseExtractor::class.java)
        )
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

@Test
    fun `a response header that never arrives is cut off by the remaining budget (R-1, V-4)`() {
        // V-4：连接超时与响应头读取此前不受单篇共享预算约束（mock 用例证明不了这一点，这里用真 client + 挂起服务端）。
        SlowHttpServer(SlowHttpServer.Mode.ACCEPT_ONLY).use { server ->
            val boundedExtractor = PdfEmailExtractor(
                RestTemplate(), plainTextExtractor,
                PdfExtractionProperties(downloadTimeoutMs = 30_000, maxRetries = 0),
                BoundedFulltextHttp
            )
            val startedAt = System.nanoTime()

            val outcome = boundedExtractor.extract(
                "http://127.0.0.1:${server.port}/paper.pdf", emptyList(), "TEST", Instant.now().plusMillis(400)
            )

            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            assertEquals("PDF_DOWNLOAD_FAILED", outcome.failureReason)
            assertEquals("TIMEOUT", outcome.downloadFailureCategory)
            assertEquals(false, outcome.fulltextObtained)
            assertTrue(elapsedMs < 5_000, "剩余预算 400ms 内必须结束（实际 ${elapsedMs}ms）")
            assertEquals(1, server.acceptedCount, "有界 client 只发一次请求，不留孤儿重试")
        }
    }

    @Test
    fun `a stalled response body is cut off by the remaining budget (R-1, V-4)`() {
        SlowHttpServer(SlowHttpServer.Mode.HEADERS_THEN_STALL, bodyPrefix = "%PDF-1.4".toByteArray()).use { server ->
            val boundedExtractor = PdfEmailExtractor(
                RestTemplate(), plainTextExtractor,
                PdfExtractionProperties(downloadTimeoutMs = 30_000, maxRetries = 0),
                BoundedFulltextHttp
            )
            val startedAt = System.nanoTime()

            val outcome = boundedExtractor.extract(
                "http://127.0.0.1:${server.port}/paper.pdf", emptyList(), "TEST", Instant.now().plusMillis(400)
            )

            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            assertEquals("TIMEOUT", outcome.downloadFailureCategory)
            assertEquals(false, outcome.fulltextObtained)
            assertTrue(elapsedMs < 5_000, "响应体挂起时也必须在剩余预算内结束（实际 ${elapsedMs}ms）")
        }
    }

@Test
    fun `a trickling PDF body is cut off at the absolute deadline (R-1, V-4)`() {
        SlowHttpServer(SlowHttpServer.Mode.TRICKLE_BODY, trickleIntervalMs = 20).use { server ->
            val boundedExtractor = PdfEmailExtractor(
                RestTemplate(), plainTextExtractor,
                PdfExtractionProperties(downloadTimeoutMs = 30_000, maxRetries = 0),
                BoundedFulltextHttp
            )
            val startedAt = System.nanoTime()

            val outcome = boundedExtractor.extract(
                "http://127.0.0.1:${server.port}/paper.pdf", emptyList(), "TEST", Instant.now().plusMillis(500)
            )

            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            assertEquals("TIMEOUT", outcome.downloadFailureCategory)
            assertEquals(false, outcome.fulltextObtained)
            assertTrue(elapsedMs < 5_000, "500ms 预算内必须结束（实际 ${elapsedMs}ms）")
        }
    }
}
