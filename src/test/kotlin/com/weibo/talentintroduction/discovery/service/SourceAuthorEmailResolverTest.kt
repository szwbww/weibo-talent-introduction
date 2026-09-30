package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.security.MessageDigest
import java.util.zip.ZipInputStream

internal fun htmlContactEntries(): Map<String, ByteArray> {
    val entries = mutableMapOf<String, ByteArray>()
    ZipInputStream(requireNotNull(SourceAuthorEmailResolverTest::class.java.getResourceAsStream(
        "/discovery/html-contact-recall.zip"))).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            entries[entry.name] = zip.readBytes()
        }
    }
    return entries
}

internal fun htmlContactAuthors(metadata: JsonNode): List<PaperAuthor> = metadata.path("authorships").map {
    val name = it.path("author").path("display_name").asText()
    PaperAuthor(name.substringBeforeLast(' '), name.substringAfterLast(' '),
        it.path("author").path("orcid").asText(null)?.substringAfterLast('/'), null,
        it.path("is_corresponding").asBoolean(),
        openAlexAuthorId = it.path("author").path("id").asText(null)?.substringAfterLast('/'))
}

internal fun fixtureSha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** 原文证据 ZIP（逐字复制自 [docs/plans/2026-09-29/discovery-repair-evidence]）：只按名读取成员，不重排、不重生成。 */
internal fun ownershipArchive(): ByteArray = requireNotNull(
    SourceAuthorEmailResolverTest::class.java.getResourceAsStream("/discovery/ownership-20260929.zip")
).use { it.readBytes() }

internal fun zipMembers(archive: ByteArray): Map<String, ByteArray> {
    val members = mutableMapOf<String, ByteArray>()
    ZipInputStream(archive.inputStream()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            members[entry.name] = zip.readBytes()
        }
    }
    return members
}

internal fun ownershipExpectations(): JsonNode = jacksonObjectMapper().readTree(requireNotNull(
    SourceAuthorEmailResolverTest::class.java.getResourceAsStream("/discovery/ownership-20260929-expected.json")))

/**
 * 生产作者映射：作品 JSON → 作者。走与线上同一条解析链（[OpenAlexDataSource] 的首个空格拆名、
 * 唯一机构、ORCID 与 OpenAlex 作者 ID），因此测试既不手工补中间名，也不手工补机构。
 */
internal fun openAlexPaperAuthors(work: JsonNode): List<PaperAuthor> {
    val restTemplate = Mockito.mock(org.springframework.web.client.RestTemplate::class.java)
    val response = jacksonObjectMapper().createObjectNode().apply {
        putObject("meta").put("count", 1)
        putArray("results").add(work)
    }
    Mockito.`when`(restTemplate.exchange(Mockito.anyString(), Mockito.eq(org.springframework.http.HttpMethod.GET),
        Mockito.nullable(org.springframework.http.HttpEntity::class.java), Mockito.eq(JsonNode::class.java)))
        .thenReturn(org.springframework.http.ResponseEntity.ok(response))
    return OpenAlexDataSource(restTemplate,
        com.weibo.talentintroduction.config.OpenAlexProperties(requestDelayMs = 0),
        Mockito.mock(EuropePmcDataSource::class.java), Mockito.mock(PdfEmailExtractor::class.java),
        Mockito.mock(UnpaywallClient::class.java))
        .searchPapers(com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria())
        .papers.single().authors
}

/** 该作品 JSON 中某位作者的 OpenAlex 作者 ID（生产同款去前缀）；找不到返回 null。 */
internal fun openAlexAuthorId(work: JsonNode, displayName: String): String? {
    val authorship = work.path("authorships").firstOrNull {
        it.path("author").path("display_name").asText() == displayName
    } ?: return null
    return authorship.path("author").path("id").asText(null)?.substringAfterLast('/')
}

/** 与证据重放同一条链：真实 PDFBox 解析 + [SourceAuthorEmailResolver.resolvePdf]；只替换原文下载传输。 */
internal fun replayOriginalOwnership(pdf: ByteArray, authors: List<PaperAuthor>):
    List<com.weibo.talentintroduction.discovery.domain.AuthorEmail> {
    val text = StringBuilder()
    val contacts = org.apache.pdfbox.pdmodel.PDDocument.load(pdf).use { document ->
        PdfAuthorContactLayout.collect(document, 2, authors, 1,
            onPage = { page, pageText -> if (page <= 2) text.append(pageText).append('\n') })
    }
    return SourceAuthorEmailResolver.resolvePdf(text.toString(), authors, contacts)
}

internal fun sourceOwnershipCases(): List<JsonNode> = jacksonObjectMapper().readTree(
    requireNotNull(SourceAuthorEmailResolverTest::class.java.getResourceAsStream("/discovery/source-email-ownership-cases.json"))
).toList()

internal fun sourceOwnershipAuthors(case: JsonNode): List<PaperAuthor> = case.path("authors").map {
    PaperAuthor(it.path("givenNames").asText(), it.path("familyNames").asText(), null, null, false)
}

/**
 * 2026-09-30 三条真实异常原文归档（逐字复制自审计 `original-inputs.zip`，SHA256 `9f5802…`）。
 * `chee`/`lyderic`/`lun` 各自带 PDF 与当次 OpenAlex JSON；测试只按名读取成员，不重排、不重生成。
 */
internal fun threeCaseArchive(): ByteArray = requireNotNull(
    SourceAuthorEmailResolverTest::class.java.getResourceAsStream("/discovery/ownership-20260930.zip")
).use { it.readBytes() }

internal fun threeCaseMembers(): Map<String, ByteArray> = zipMembers(threeCaseArchive())

internal fun threeCaseAuthors(members: Map<String, ByteArray>, case: String): List<PaperAuthor> =
    openAlexPaperAuthors(jacksonObjectMapper().readTree(requireNotNull(members["$case.openalex.json"])))

/** 原文重放的观测结果：同一篇 PDF 的 layout contacts、mailto 链接线索、最终 emails、方法与下载事实。 */
internal data class ThreeCaseObservation(
    val case: String,
    val archiveSha256: String,
    val pdfSha256: String,
    val metadataSha256: String,
    val authors: List<PaperAuthor>,
    val contacts: List<PdfAuthorContactLayout.Contact>,
    val mailtoClues: List<String>,
    val resolved: List<com.weibo.talentintroduction.discovery.domain.AuthorEmail>,
    val methodUsed: String?,
    val httpRequests: Int,
    val fulltextObtained: Boolean?
)

/** X-1/X-2：真实 PDFBox + 真实 layout + 真实 resolver/extractor；只有 HTTP 传输被替换。 */
internal fun observeThreeCase(members: Map<String, ByteArray>, case: String): ThreeCaseObservation {
    val pdf = requireNotNull(members["$case.pdf"])
    val metadata = requireNotNull(members["$case.openalex.json"])
    val authors = threeCaseAuthors(members, case)
    val contacts = mutableListOf<PdfAuthorContactLayout.Contact>()
    val mailtoClues = mutableListOf<String>()
    org.apache.pdfbox.pdmodel.PDDocument.load(pdf).use { document ->
        contacts += PdfAuthorContactLayout.collect(document, 2, authors, 1)
        // 与生产同一条 mailto 线索读取范围：只扫描被选中的页面（头两页 + 尾页）。
        for (page in PdfAuthorContactLayout.selectedPages(document.numberOfPages, 2, 1)) {
            for (annotation in document.getPage(page - 1).annotations) {
                val uri = ((annotation as? org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink)
                    ?.action as? org.apache.pdfbox.pdmodel.interactive.action.PDActionURI)?.uri ?: continue
                if (!uri.startsWith("mailto:", ignoreCase = true)) continue
                mailtoClues += uri.substring(7).substringBefore('?').lowercase()
            }
        }
    }
    val outcome = extractOwnershipContent(pdf, org.springframework.http.MediaType.APPLICATION_PDF, authors)
    return ThreeCaseObservation(
        case = case,
        archiveSha256 = fixtureSha256(threeCaseArchive()),
        pdfSha256 = fixtureSha256(pdf),
        metadataSha256 = fixtureSha256(metadata),
        authors = authors,
        contacts = contacts,
        mailtoClues = mailtoClues,
        resolved = outcome.emails,
        methodUsed = outcome.methodUsed,
        httpRequests = outcome.httpRequests,
        fulltextObtained = outcome.fulltextObtained
    )
}

internal fun threeCaseNames(): List<String> = listOf("chee", "lyderic", "lun")

/**
 * I-1/I-2 合成控制（非原文）：连续标记组、未匹配署名参与歧义、署名区解码损坏、
 * 署名区之外的问号不阻断、损坏标记下独立姓名记录仍生效、同一联系行多标记。
 * 返回现场观测值（contacts/resolved），断言由调用方按 label 逐项执行。
 */
internal fun markerOwnershipControls(): List<Map<String, Any?>> {
    val jane = PaperAuthor("Jane", "Doe", "0000-0002-1825-0097", "Jane Lab", true, openAlexAuthorId = "A123")
    val john = PaperAuthor("John", "Smith", "0000-0002-1825-0098", "John Lab", false, openAlexAuthorId = "A456")
    val controls = listOf(
        Triple("continuousMarkerGroupSharesOneSignature", listOf(jane), listOf(
            Triple(50f, 720f, "Jane Doe1,\u2020,*"),
            Triple(50f, 600f, "\u2020jane@uni.edu"),
            Triple(50f, 580f, "*jane@uni.edu"))),
        Triple("unmatchedSecondSignatureJoinsTheSharedMarker", listOf(jane), listOf(
            Triple(50f, 720f, "Jane Doe1,\u2020,* and John Smith2,*"),
            Triple(50f, 600f, "\u2020jane@uni.edu"),
            Triple(50f, 580f, "*unknown@uni.edu"))),
        Triple("damagedSignatureAreaCannotProve", listOf(jane, john), listOf(
            Triple(50f, 720f, "Jane Doe1,\u2020 and John Smith2,?"),
            Triple(50f, 600f, "\u2020jane@uni.edu"),
            Triple(50f, 580f, "\u2020john@uni.edu"))),
        Triple("questionMarkOutsideAuthorAreaDoesNotBlock", listOf(jane), listOf(
            Triple(50f, 720f, "Jane Doe*"),
            Triple(50f, 600f, "*jane@uni.edu"),
            Triple(50f, 230f, "Why does a question mark matter?"))),
        Triple("damagedMarkerKeepsIndependentNameRecord", listOf(jane), listOf(
            Triple(50f, 720f, "Jane Doe1,?"),
            Triple(50f, 600f, "Jane Doe: jane@uni.edu"))),
        Triple("sameContactLineTwoMarkersBindEachOwner", listOf(jane, john), listOf(
            Triple(50f, 720f, "Jane Doe\u2020 and John Smith\u00a7"),
            Triple(50f, 600f, "\u2020jane@uni.edu, \u00a7john@uni.edu")))
    )
    return controls.map { (label, authors, lines) ->
        val pdf = positionedPdf(lines)
        val contacts = org.apache.pdfbox.pdmodel.PDDocument.load(pdf).use {
            PdfAuthorContactLayout.collect(it, 1, authors)
        }
        val resolved = extractOwnershipContent(pdf, org.springframework.http.MediaType.APPLICATION_PDF, authors).emails
        mapOf(
            "label" to label, "designation" to "SYNTHETIC",
            "authors" to authors.map { "${it.givenNames} ${it.familyNames}" },
            "lines" to lines.map { listOf(it.first, it.second, it.third) },
            "contacts" to contacts.map {
                mapOf("email" to it.email, "authorIndex" to it.authorIndex,
                    "author" to "${authors[it.authorIndex].givenNames} ${authors[it.authorIndex].familyNames}")
            },
            "resolved" to resolved.map {
                mapOf("email" to it.email, "givenNames" to it.givenNames, "familyNames" to it.familyNames,
                    "identityEvidence" to it.identityEvidence)
            }
        )
    }
}

/**
 * 正向控制（X-3 / N-1 / N-2）：一篇真实现有正常 PDF（单一明确联系人）与合成「一人两邮箱」PDF。
 * 两者都走真实 layout + resolver + extractor；合成控制自带完整结构机构字段。
 */
internal data class PdfPositiveControl(
    val label: String,
    val designation: String,
    val authors: List<PaperAuthor>,
    val pdf: ByteArray,
    val contacts: List<PdfAuthorContactLayout.Contact>,
    val parsed: com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
)

internal fun pdfPositiveControls(): List<PdfPositiveControl> {
    val realMembers = zipMembers(requireNotNull(
        SourceAuthorEmailResolverTest::class.java.getResourceAsStream("/discovery/source-contact-recall.zip")
    ).use { it.readBytes() })
    val realPdf = requireNotNull(realMembers["sources/W2999309192/source.pdf"])
    val realAuthors = openAlexPaperAuthors(jacksonObjectMapper().readTree(
        requireNotNull(realMembers["sources/W2999309192/metadata.json"])))
    val syntheticAuthor = PaperAuthor("Jane", "Doe", "0000-0002-1825-0097", "Jane Lab", true,
        openAlexAuthorId = "A123", institutionName = "Jane University", institutionCountry = "GB",
        institutionSource = com.weibo.talentintroduction.discovery.domain.INSTITUTION_SOURCE_OPENALEX)
    val syntheticPdf = positionedPdf(listOf(
        Triple(50f, 720f, "Jane Doe*"),
        Triple(50f, 600f, "*Email: first@uni.edu; second@uni.edu")))
    fun contactsOf(pdf: ByteArray, authors: List<PaperAuthor>) =
        org.apache.pdfbox.pdmodel.PDDocument.load(pdf).use { PdfAuthorContactLayout.collect(it, 2, authors, 1) }
    return listOf(
        PdfPositiveControl("realNormalPdfSingleOwnedMailbox", "REAL_ORIGINAL", realAuthors, realPdf,
            contactsOf(realPdf, realAuthors),
            extractOwnershipContent(realPdf, org.springframework.http.MediaType.APPLICATION_PDF, realAuthors)),
        PdfPositiveControl("syntheticOneAuthorTwoMailboxes", "SYNTHETIC", listOf(syntheticAuthor), syntheticPdf,
            contactsOf(syntheticPdf, listOf(syntheticAuthor)),
            extractOwnershipContent(syntheticPdf, org.springframework.http.MediaType.APPLICATION_PDF, listOf(syntheticAuthor)))
    )
}

class SourceAuthorEmailResolverTest {
    private val jane = PaperAuthor("Jane", "Doe", "0000-0002-1825-0097", "Jane Lab", true, openAlexAuthorId = "A123")
    private val john = PaperAuthor("John", "Smith", null, "John Lab", false)

    @Test fun `five published PDFs cannot certify wrong owner or truncated mailbox`() {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(requireNotNull(javaClass.getResourceAsStream(
            "/discovery/identity-regression-pdfs.zip"))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        val manifest = jacksonObjectMapper().readTree(requireNotNull(entries["manifest.json"]))
        assertEquals(5, manifest.size())
        for ((id, case) in manifest.fields()) {
            val authors = case.path("authors").map { node ->
                val parts = node.asText().split(" ", limit = 2)
                PaperAuthor(parts[0], parts.getOrElse(1) { "" }, null, null, false)
            }
            val result = extractOwnershipContent(requireNotNull(entries["$id.pdf"]),
                org.springframework.http.MediaType.APPLICATION_PDF, authors)
            val badEmail = case.path("badEmail").asText()
            org.apache.pdfbox.pdmodel.PDDocument.load(requireNotNull(entries["$id.pdf"])).use { pdf ->
                val contacts = PdfAuthorContactLayout.collect(pdf, 3, authors)
                assertTrue(contacts.none { it.email == badEmail }, "$id contact: $contacts")
            }
            assertTrue(result.emails.none { it.email == badEmail && it.identityEvidence != null },
                "$id $badEmail: ${result.emails}")
        }
    }

    @Test
    fun `REAL_ORIGINAL six archived papers keep the seven confirmed wrong owners out (I-1 I-2 I-3)`() {
        val archive = ownershipArchive()
        val members = zipMembers(archive)
        val expected = ownershipExpectations()
        val manifest = jacksonObjectMapper().readTree(requireNotNull(members["manifest.json"]))
        val manifestItems = manifest.path("items").associateBy { it.path("name").asText() }
        assertEquals(expected.path("archiveSha256").asText(), fixtureSha256(archive), "ZIP 必须逐字复制")
        val cases = expected.path("cases").toList()
        assertEquals(6, cases.size)
        assertEquals(7, cases.sumOf { it.path("forbidden").size() })
        // 报告区分：4 条在当前版本可复现的错误关系，3 条线上确证但本次重放未复现的真实负例。
        assertEquals(4, cases.filter { it.path("baselineReplay").asText() == "reproduced" }
            .sumOf { it.path("forbidden").size() })
        assertEquals(3, cases.filter { it.path("baselineReplay").asText() == "not-reproduced-in-rerun" }
            .sumOf { it.path("forbidden").size() })
        val report = mutableListOf<Map<String, Any?>>()
        for (case in cases) {
            val id = case.path("case").asText()
            val pdfFile = case.path("pdf")
            val metadataFile = case.path("metadata")
            val pdf = requireNotNull(members[pdfFile.path("path").asText()])
            val metadataBytes = requireNotNull(members[metadataFile.path("path").asText()])
            // 逐项 SHA 同时对齐 ZIP 成员与原始 manifest。
            assertEquals(pdfFile.path("sha256").asText(), fixtureSha256(pdf), "$id PDF")
            assertEquals(manifestItems.getValue(pdfFile.path("path").asText()).path("sha256").asText(),
                fixtureSha256(pdf), "$id PDF manifest")
            assertEquals(metadataFile.path("sha256").asText(), fixtureSha256(metadataBytes), "$id metadata")
            assertEquals(manifestItems.getValue(metadataFile.path("path").asText()).path("sha256").asText(),
                fixtureSha256(metadataBytes), "$id metadata manifest")
            val authors = openAlexPaperAuthors(jacksonObjectMapper().readTree(metadataBytes))
            val resolved = replayOriginalOwnership(pdf, authors)
            for (forbidden in case.path("forbidden")) {
                val email = forbidden.path("email").asText()
                val wrongOwner = forbidden.path("wrongOwner").asText()
                val row = resolved.single { it.email == email }
                // 归属错了名字就等于归属错了机构与作者 ID；机构值本身在 Lumma/Pauly
                // （同属 Heidelberg University）上不可区分，因此这里以主人姓名为判据，机构由下一条 allowed 断言覆盖。
                assertNotEquals(wrongOwner, "${row.givenNames} ${row.familyNames}", "$id $email")
            }
            for (allowed in case.path("allowed")) {
                val email = allowed.path("email").asText()
                val row = resolved.single { it.email == email }
                assertEquals(allowed.path("owner").asText(), "${row.givenNames} ${row.familyNames}", "$id $email")
                assertTrue(row.identityEvidence?.startsWith("SOURCE_SHA256:") == true, "$id $email")
            }
            for (email in case.path("mustRemainUnattributed").map { it.asText() }) {
                val row = resolved.single { it.email == email }
                assertNull(row.givenNames, "$id $email")
                assertNull(row.familyNames, "$id $email")
                assertNull(row.openAlexAuthorId, "$id $email")
                assertNull(row.identityEvidence, "$id $email")
                assertNull(row.institutionName, "$id $email")
                assertNull(row.institutionSource, "$id $email")
            }
            report += mapOf(
                "case" to id, "designation" to "REAL_ORIGINAL",
                "baselineReplay" to case.path("baselineReplay").asText(),
                "pdfSha256" to fixtureSha256(pdf), "metadataSha256" to fixtureSha256(metadataBytes),
                "resolved" to resolved.map {
                    mapOf("email" to it.email, "givenNames" to it.givenNames, "familyNames" to it.familyNames,
                        "openAlexAuthorId" to it.openAlexAuthorId, "institutionName" to it.institutionName,
                        "institutionSource" to it.institutionSource, "identityEvidence" to it.identityEvidence)
                })
        }
        val path = java.nio.file.Paths.get("target/discovery-plan-acceptance/01.json")
        java.nio.file.Files.createDirectories(path.parent)
        java.nio.file.Files.write(path, jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(
            mapOf("task" to "child-01", "designation" to "REAL_ORIGINAL",
                "archive" to "src/test/resources/discovery/ownership-20260929.zip",
                "archiveSha256" to fixtureSha256(archive), "cases" to report)))
    }

    @Test
    fun `SYNTHETIC shared marker with an unmatched signature never certifies the first author (I-1)`() {
        // 合成 PDF（非原文）：同一标记的第二处署名不在元数据里 —— 不能只对成功匹配的作者计数。
        val positioned = positionedPdf(listOf(
            Triple(50f, 720f, "Jane Doe* and Unlisted Person*"),
            Triple(50f, 600f, "*Email: opaque@uni.edu")))
        assertTrue(org.apache.pdfbox.pdmodel.PDDocument.load(positioned).use {
            PdfAuthorContactLayout.collect(it, 1, listOf(jane))
        }.isEmpty())
        val resolved = extractOwnershipContent(positioned, org.springframework.http.MediaType.APPLICATION_PDF,
            listOf(jane)).emails.single { it.email == "opaque@uni.edu" }
        assertNull(resolved.givenNames)
        assertNull(resolved.identityEvidence)
    }

    @Test
    fun `SYNTHETIC same line marker segments bind each mailbox to its own author (I-2)`() {
        // 合成 PDF（非原文）：`∗`/`†`/`‡`/`§` 同属作者与联系两侧，同一行按标记边界分段。
        val result = extractOwnershipContent(positionedPdf(listOf(
            Triple(50f, 720f, "Jane Doe‡ and John Smith§"),
            Triple(50f, 600f, "‡jane@uni.edu, §john@uni.edu"))),
            org.springframework.http.MediaType.APPLICATION_PDF, listOf(jane, john)).emails
        assertEquals("Jane", result.single { it.email == "jane@uni.edu" }.givenNames)
        assertEquals("John", result.single { it.email == "john@uni.edu" }.givenNames)
        // `∗` 与 `*` 归一：作者行用 Symbol 字体的 `∗`、联系行用 `*` 时仍是同一个标记。
        val normalized = extractOwnershipContent(
            positionedPdfWithSymbolAsterisk("Jane Doe", "*Contact: jane@uni.edu"),
            org.springframework.http.MediaType.APPLICATION_PDF, listOf(jane)).emails
        assertEquals("Jane", normalized.single { it.email == "jane@uni.edu" }.givenNames)
    }

    @Test
    fun `SYNTHETIC next author statement ending in are with stops the previous paragraph (I-2)`() {
        // 合成 PDF（非原文）：下一作者的 `are with` 语句必须终止上一段，即使它自己匹配不上元数据。
        val result = extractOwnershipContent(positionedPdf(listOf(
            Triple(50f, 720f, "Jane Doe and John Smith"),
            Triple(50f, 230f, "J.Doe is with Lab One. E-mail: jane@uni.edu"),
            Triple(50f, 220f, "J. Smith are with Lab Two. E-mail: john@uni.edu"))),
            org.springframework.http.MediaType.APPLICATION_PDF, listOf(jane, john)).emails
        assertEquals("Jane", result.single { it.email == "jane@uni.edu" }.givenNames)
        assertNull(result.single { it.email == "john@uni.edu" }.givenNames)
        assertNull(result.single { it.email == "john@uni.edu" }.identityEvidence)
    }

    @Test
    fun `SYNTHETIC multi initial next author statement stops the previous paragraph (I-2)`() {
        // 合成 PDF（非原文）：`A. K. Singh are with …` 这类多首字母边界同样终止上一段（Kumar 形状）。
        val jitendra = PaperAuthor("Jitendra", "Kumar", null, null, false)
        val ashutosh = PaperAuthor("Ashutosh", "Kumar Singh", null, null, false)
        val result = extractOwnershipContent(positionedPdf(listOf(
            Triple(50f, 720f, "Jitendra Kumar, Ashutosh Kumar Singh"),
            Triple(50f, 230f, "J.Kumar is with NIT Tiruchirappalli. E-mail: jitendra@nitt.edu"),
            Triple(50f, 220f, "A. K. Singh are with NIT Kurukshetra. E-mail: ashutosh@nitkkr.ac.in"))),
            org.springframework.http.MediaType.APPLICATION_PDF, listOf(jitendra, ashutosh)).emails
        assertEquals("Jitendra", result.single { it.email == "jitendra@nitt.edu" }.givenNames)
        assertNull(result.single { it.email == "ashutosh@nitkkr.ac.in" }.givenNames)
    }

    // 合法「一人两邮箱」与纯文本/HTML 原有归属继续由本文件既有的
    // `one unique PDF contact marker retains two explicit mailboxes`、
    // `published source blocks preserve two owned mailboxes and reject shared correspondence`
    // 与 `original Springer named mailto anchors bind only unique metadata authors` 覆盖，未弱化。

    @Test fun `original Springer named mailto anchors bind only unique metadata authors`() {
        val entries = htmlContactEntries()
        val manifest = jacksonObjectMapper().readTree(requireNotNull(entries["manifest.json"]))
        val relations = mutableListOf<Pair<String, String>>()
        for (fixture in manifest.path("fixtures")) {
            val id = fixture.path("id").asText()
            val bytes = requireNotNull(entries[fixture.path("html").path("path").asText()])
            val metadataBytes = requireNotNull(entries[fixture.path("metadata").path("path").asText()])
            assertEquals(fixture.path("html").path("sha256").asText(), fixtureSha256(bytes))
            assertEquals(fixture.path("metadata").path("sha256").asText(), fixtureSha256(metadataBytes))
            if (fixture.path("expectedChallenge").asBoolean()) continue
            val resolved = SourceAuthorEmailResolver.resolveHtml(String(bytes, Charsets.UTF_8),
                htmlContactAuthors(jacksonObjectMapper().readTree(metadataBytes)))
            for (expected in fixture.path("expectedExplicitContacts").fields()) {
                val email = resolved.single { it.email == expected.key }
                assertEquals(expected.value.asText(), "${email.givenNames} ${email.familyNames}", id)
                assertTrue(email.identityEvidence!!.startsWith("SOURCE_SHA256:"), id)
                relations += id to email.email
            }
        }
        assertEquals(3, relations.size)
        assertEquals(2, relations.map { it.second }.toSet().size)
    }

    @Test fun `named mailto requires corresponding region exact visible name and single safe recipient`() {
        fun result(heading: String, anchor: String, authors: List<PaperAuthor> = listOf(jane, john)) =
            SourceAuthorEmailResolver.resolveHtml(
                "<div><h3 id='corresponding-author'>$heading</h3>" +
                    "<p id='corresponding-author-list'>Correspondence to $anchor.</p></div>", authors)
        val link = "<a href='mailto:opaque@uni.edu'>Jane Doe</a>"
        assertEquals("Jane", result("Corresponding author", link).single().givenNames)
        assertTrue(result("Author biographies", link).none { it.givenNames != null })
        assertTrue(result("Corresponding author", "<a href='mailto:opaque@uni.edu' aria-label='Jane Doe'>Contact</a>")
            .none { it.givenNames != null })
        assertTrue(result("Corresponding author", link, listOf(jane, jane.copy(orcidId = "other")))
            .none { it.givenNames != null })
        assertTrue(result("Corresponding author", "<a href='mailto:opaque@uni.edu'>Jane Doe and John Smith</a>")
            .none { it.givenNames != null })
        assertTrue(result("Corresponding author", "<a href='mailto:opaque@uni.edu'>Jane Doe</a>" +
            "<a href='mailto:opaque@uni.edu'>John Smith</a>").none { it.givenNames != null })
        assertEquals("jane+lab@uni.edu", result("Corresponding author",
            "<a href='mailto:jane%2Blab@uni.edu?subject=ignore'>Jane Doe</a>").single().email)
        assertEquals("jane+lab@uni.edu", result("Corresponding author",
            "<a href='mailto:jane+lab@uni.edu'>Jane Doe</a>").single().email)
        val independent = result("Corresponding authors",
            "<a href='mailto:jane@uni.edu'>Jane Doe</a> and <a href='mailto:john@uni.edu'>John Smith</a>")
        assertEquals(mapOf("jane@uni.edu" to "Jane", "john@uni.edu" to "John"),
            independent.associate { it.email to it.givenNames })
        val conflicting = SourceAuthorEmailResolver.resolveHtml("<div><h3 id='corresponding-author'>" +
            "Corresponding author</h3><p id='corresponding-author-list'>" +
            "<a href='mailto:opaque@uni.edu'>Jane Doe</a></p></div><p>John Smith: opaque@uni.edu</p>",
            listOf(jane, john))
        assertNull(conflicting.single { it.email == "opaque@uni.edu" }.givenNames)
        for (href in listOf("mailto:first@uni.edu,second@uni.edu", "mailto:opaque@uni.edu%0d%0abcc:other@uni.edu",
            "mailto:opaque@uni.edu;other@uni.edu")) {
            assertTrue(result("Corresponding author", "<a href='$href'>Jane Doe</a>")
                .none { it.givenNames != null }, href)
        }
        assertTrue(result("Corresponding author", "<a href='mailto:support@uni.edu'>Jane Doe</a>")
            .none { it.givenNames != null })
    }

    @Test fun `PDF contact claims are rejected when they conflict with existing source claims`() {
        val contact = PdfAuthorContactLayout.Contact("opaque@uni.edu", 1, 1,
            "Jane Doe* John Smith", "*Correspondence: opaque@uni.edu")
        val result = SourceAuthorEmailResolver.resolvePdf(
            "Jane Doe: opaque@uni.edu", listOf(jane, john), listOf(contact))
        assertNull(result.single().givenNames)
        assertNull(result.single().identityEvidence)
    }

    @Test fun `layout rejects shared markers duplicate names ambiguous initials and a cross-column mailbox`() {
        val cases = pdfLayoutNegativeCases()
        for ((authors, lines, label) in cases) {
            val bytes = positionedPdf(lines)
            val contacts = org.apache.pdfbox.pdmodel.PDDocument.load(bytes).use {
                PdfAuthorContactLayout.collect(it, 1, authors)
            }
            assertTrue(contacts.isEmpty(), "$label: $contacts")
            val resolved = extractOwnershipContent(bytes, org.springframework.http.MediaType.APPLICATION_PDF, authors)
                .emails.single { it.email == "opaque@uni.edu" }
            assertNull(resolved.givenNames, label)
        }
    }

    @Test fun `one unique PDF contact marker retains two explicit mailboxes`() {
        val pdf = positionedPdf(listOf(
            Triple(50f, 720f, "Jane Doe*"),
            Triple(50f, 600f, "*Email: first@uni.edu; second@uni.edu")))
        val result = extractOwnershipContent(pdf, org.springframework.http.MediaType.APPLICATION_PDF, listOf(jane))
        assertEquals(setOf("first@uni.edu", "second@uni.edu"), result.emails.map { it.email }.toSet())
        assertTrue(result.emails.all { it.givenNames == "Jane" && it.familyNames == "Doe" &&
            it.identityEvidence?.startsWith("SOURCE_SHA256:") == true })
    }

    @Test fun `shared marker on later author line cannot claim both correspondence mailboxes`() {
        val pdf = positionedPdf(listOf(
            Triple(50f, 720f, "Jane Doe* Alice Brown"),
            Triple(50f, 680f, "John Smith*"),
            Triple(50f, 600f, "*Correspondence: jane@uni.edu, john@uni.edu"),
            Triple(50f, 530f, "Abstract")))
        val resolved = extractOwnershipContent(pdf, org.springframework.http.MediaType.APPLICATION_PDF,
            listOf(jane, PaperAuthor("Alice", "Brown", null, null, false), john)).emails
        assertEquals(setOf("jane@uni.edu", "john@uni.edu"), resolved.map { it.email }.toSet())
        assertTrue(resolved.all { it.givenNames == null && it.identityEvidence == null })
    }

    @Test fun `broken local part must not become a verified suffix mailbox`() {
        val cases = listOf(
            "*Email: lixingwang- bupt@gmail.com" to "bupt@gmail.com",
            "*Email: yin- qiu001@e.ntu.edu.sg" to "qiu001@e.ntu.edu.sg",
            "*Email: tangde math@connect.hku.hk" to "math@connect.hku.hk",
            "*Email: feng shaohan@mail.zjgsu.edu.cn" to "shaohan@mail.zjgsu.edu.cn")
        for ((contact, suffix) in cases) {
            val pdf = positionedPdf(listOf(Triple(50f, 720f, "Jane Doe*"), Triple(50f, 600f, contact)))
            val resolved = extractOwnershipContent(pdf, org.springframework.http.MediaType.APPLICATION_PDF, listOf(jane)).emails
            assertTrue(resolved.none { it.email == suffix && it.identityEvidence != null }, "$contact: $resolved")
        }
    }

    @Test fun `published source blocks preserve two owned mailboxes and reject shared correspondence`() {
        for (case in sourceOwnershipCases()) {
            val result = SourceAuthorEmailResolver.resolveHtml(case.path("html").asText(), sourceOwnershipAuthors(case))
            assertEquals(case.path("expected").fieldNames().asSequence().toSet(), result.map { it.email }.toSet())
            for (email in result) {
                val expected = case.path("expected").path(email.email)
                if (expected.isNull) {
                    assertNull(email.givenNames, "${case.path("id")}: ${email.email}")
                    assertNull(email.familyNames)
                    assertNull(email.affiliation)
                    assertNull(email.orcidId)
                    assertNull(email.openAlexAuthorId)
                    assertNull(email.identityEvidence)
                } else {
                    assertEquals(expected.asText(), "${email.givenNames} ${email.familyNames}")
                    assertTrue(email.identityEvidence!!.startsWith("SOURCE_SHA256:"))
                }
            }
        }
    }

    @Test fun `structured institution travels only with one uniquely owned mailbox (I-1 I-2)`() {
        val structured = jane.copy(institutionName = "Jane University", institutionCountry = "GB",
            institutionSource = "OPENALEX")
        val owned = SourceAuthorEmailResolver.resolveText("Jane Doe: jane@uni.edu", listOf(structured)).single()
        assertEquals("Jane University", owned.institutionName)
        assertEquals("GB", owned.institutionCountry)
        assertEquals("OPENALEX", owned.institutionSource)
        assertTrue(owned.identityEvidence!!.startsWith("SOURCE_SHA256:"))

        // 没有唯一作者证据的邮箱线索不得携带任何机构字段（邮箱线索不是作者归属）。
        val clue = SourceAuthorEmailResolver.resolveText("opaque@uni.edu", listOf(structured)).single()
        assertNull(clue.givenNames)
        assertNull(clue.institutionName)
        assertNull(clue.institutionCountry)
        assertNull(clue.institutionSource)

        // 同名多作者（机构互相矛盾）→ 无法唯一绑定 → 同样不传播。
        val ambiguous = SourceAuthorEmailResolver.resolveText(
            "Jane Doe: opaque@uni.edu",
            listOf(structured, structured.copy(orcidId = "different", institutionName = "Other Lab"))
        ).single()
        assertNull(ambiguous.givenNames)
        assertNull(ambiguous.institutionName)
        assertNull(ambiguous.institutionCountry)
        assertNull(ambiguous.institutionSource)
    }

    @Test fun `bounded contact fields carry one whole identity and multiple emails`() {
        val result = SourceAuthorEmailResolver.resolveText(
            "Contact: Jane Doe (e-mail: r142@uni.edu; r143@uni.edu)\nJohn Smith: r144@uni.edu", listOf(jane, john))
        assertEquals(listOf("Jane", "Jane", "John"), result.map { it.givenNames })
        assertEquals(listOf(jane.orcidId, jane.orcidId, null), result.map { it.orcidId })
        assertEquals(listOf("Jane Lab", "Jane Lab", "John Lab"), result.map { it.affiliation })
        assertEquals(listOf("A123", "A123", null), result.map { it.openAlexAuthorId })
        val wrapped = SourceAuthorEmailResolver.resolveText("Contact: Jane Doe\nEmail: opaque@uni.edu", listOf(jane))
        assertEquals("Jane", wrapped.single().givenNames)
    }

    @Test fun `spelling adjacency and single-author assumptions leave only an email clue`() {
        for (text in listOf(
            "Contact: jane.doe@uni.edu", "Jane Doe\n\nEmail: jane.doe@uni.edu",
            "John Smith\nJane Doe\nEmail: jane.doe@uni.edu",
            "Jane Doe jane.doe@uni.edu", "Jane Doe discusses data from jane.doe@uni.edu",
            "Jane Doe\nThird party contact: jane.doe@uni.edu",
            "Jane Doe and John Smith: shared@uni.edu", "Jane Doe: {jane,john}@uni.edu")) {
            val result = SourceAuthorEmailResolver.resolveText(text, listOf(jane))
            assertTrue(result.isNotEmpty(), text)
            for (email in result) {
                assertNull(email.givenNames, text)
                assertNull(email.orcidId, text)
                assertNull(email.affiliation, text)
                assertNull(email.openAlexAuthorId, text)
            }
        }
    }

    @Test fun `same names within a paper and competing owners of one email are ambiguous`() {
        val duplicateNames = listOf(jane, jane.copy(orcidId = "different", affiliation = "Other Lab"))
        assertNull(SourceAuthorEmailResolver.resolveText("Jane Doe: opaque@uni.edu", duplicateNames).single().givenNames)
        val shared = SourceAuthorEmailResolver.resolveText("Jane Doe: same@uni.edu\nJohn Smith: same@uni.edu", listOf(jane, john))
        assertNull(shared.single().givenNames)
        // Separate papers are not merged by name.
        for ((i, author) in duplicateNames.withIndex()) {
            assertEquals(author.orcidId, SourceAuthorEmailResolver.resolveText(
                "Jane Doe: opaque$i@uni.edu", listOf(author)).single().orcidId)
        }
    }

    @Test fun `html boundaries prevent a name in one paragraph claiming another paragraph mailbox`() {
        val html = "<p>Jane Doe</p><p>Email: opaque@uni.edu</p>"
        assertNull(SourceAuthorEmailResolver.resolveHtml(html, listOf(jane)).single().givenNames)
    }

    @Test fun `empty structured name never carries an author ID`() {
        val html = "<span class='ltx_role_author'><span class='ltx_personname'></span>" +
            "<span class='ltx_contact'>opaque@uni.edu</span></span>"
        val result = SourceAuthorEmailResolver.resolveHtml(html,
            listOf(PaperAuthor(null, null, "orcid-must-not-leak", "Lab", true, openAlexAuthorId = "A123"))).single()
        assertNull(result.givenNames)
        assertNull(result.orcidId)
        assertNull(result.openAlexAuthorId)
        assertNull(result.affiliation)
        assertNull(result.identityEvidence)
    }

    @Test fun `nested or duplicate author nodes never broadcast a contact`() {
        val node = "<span class='ltx_role_author'><span class='ltx_personname'>Jane Doe</span>" +
            "<span class='ltx_contact'>opaque@uni.edu</span></span>"
        assertNull(SourceAuthorEmailResolver.resolveHtml(node, listOf(jane, jane.copy(orcidId = "different"))).single().givenNames)
        val nested = "<span class='ltx_role_author'><span class='ltx_personname'>John Smith</span>$node</span>"
        assertEquals("Jane", SourceAuthorEmailResolver.resolveHtml(nested, listOf(jane, john)).single().givenNames)
    }
    @Test fun `normalizes obfuscated contact identically while hashing original entry`() {
        val original = "Jane Doe: opaque(at)uni.edu"
        val plain = SourceAuthorEmailResolver.resolveText("Jane Doe: opaque@uni.edu", listOf(jane)).single()
        val obfuscated = SourceAuthorEmailResolver.resolveText(original, listOf(jane)).single()
        assertEquals(plain.email, obfuscated.email)
        assertEquals(plain.givenNames, obfuscated.givenNames)
        assertEquals("SOURCE_SHA256:" + com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.hash(original),
            obfuscated.identityEvidence)
        assertNotEquals(plain.identityEvidence, obfuscated.identityEvidence)
    }
    @Test fun `one-line email wrapping claims only its own bounded contact record`() {
        val original = "Jane Doe: opaque@\nuni.edu"
        val wrapped = SourceAuthorEmailResolver.resolveText(original, listOf(jane)).single()
        assertEquals("opaque@uni.edu", wrapped.email)
        assertEquals("Jane", wrapped.givenNames)
        assertEquals("SOURCE_SHA256:" + com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.hash(original),
            wrapped.identityEvidence)
        val splitParagraph = SourceAuthorEmailResolver.resolveText("Jane Doe: opaque@\n\nuni.edu", listOf(jane))
        assertTrue(splitParagraph.isEmpty())
    }

    @Test fun `brace and wrapped source emails remain unbound without explicit same record owner`() {
        val fixture = jacksonObjectMapper().readTree(
            requireNotNull(javaClass.getResourceAsStream("/discovery/email-text-recall.json")).use { it.readBytes() })
        val brace = fixture.path("sourceBrace").path("cases").first().path("text").asText()
        val braceResults = SourceAuthorEmailResolver.resolveText(brace, listOf(jane))
        assertEquals(6, braceResults.size)
        assertTrue(braceResults.all { it.givenNames == null && it.familyNames == null && it.identityEvidence == null })
        val wrapped = SourceAuthorEmailResolver.resolveText(fixture.path("wrappedSource").path("text").asText(), listOf(jane))
        assertTrue(wrapped.any { it.email == "kairouz@google.com" && it.givenNames == null && it.identityEvidence == null })
    }

    /**
     * I-1/I-2 合成控制（非原文）：连续标记组共享组首署名、未匹配署名参与歧义使共享标记不再独占、
     * 署名区解码损坏使本页标记归属不可用、署名区之外的问号不阻断、损坏标记下独立姓名记录仍生效。
     */
    @Test
    fun `SYNTHETIC continuous marker groups and damaged signature areas (I-1 I-2 I-4)`() {
        val controls = markerOwnershipControls().associateBy { it.getValue("label") }
        fun contacts(label: String) = controls.getValue(label)["contacts"] as List<Map<*, *>>
        fun resolved(label: String) = controls.getValue(label)["resolved"] as List<Map<*, *>>
        fun contactOwner(label: String, email: String) =
            contacts(label).singleOrNull { it["email"] == email }?.get("author")
        fun row(label: String, email: String) = resolved(label).single { it["email"] == email }

        // I-1：`1,†,*` 是一个连续标记组，组内每个符号都共享组首解析到的 Jane Doe。
        val sharedGroupContacts = contacts("continuousMarkerGroupSharesOneSignature")
        assertEquals(listOf("jane@uni.edu", "jane@uni.edu"), sharedGroupContacts.map { it["email"] })
        assertEquals(listOf("Jane Doe", "Jane Doe"), sharedGroupContacts.map { it["author"] })
        assertEquals("Jane", row("continuousMarkerGroupSharesOneSignature", "jane@uni.edu")["givenNames"])

        // I-1：第二个 `*` 所在组的署名（John Smith）不在元数据里 → 该符号拥有者为「Jane Doe + 未知」，
        // 不再是独占证明；未被其他符号证明的邮箱只留线索（Chee 形状）。
        assertEquals(1, contacts("unmatchedSecondSignatureJoinsTheSharedMarker").size)
        assertEquals("Jane Doe", contactOwner("unmatchedSecondSignatureJoinsTheSharedMarker", "jane@uni.edu"))
        assertNull(contactOwner("unmatchedSecondSignatureJoinsTheSharedMarker", "unknown@uni.edu"))
        assertEquals("Jane", row("unmatchedSecondSignatureJoinsTheSharedMarker", "jane@uni.edu")["givenNames"])
        assertNull(row("unmatchedSecondSignatureJoinsTheSharedMarker", "unknown@uni.edu")["givenNames"])
        assertNull(row("unmatchedSecondSignatureJoinsTheSharedMarker", "unknown@uni.edu")["identityEvidence"])

        // I-2：署名区里的 `?` 使本页标记 → 作者归属不可用（不猜测替换成 `*`）。
        assertTrue(contacts("damagedSignatureAreaCannotProve").isEmpty())
        for (email in listOf("jane@uni.edu", "john@uni.edu")) {
            assertNull(row("damagedSignatureAreaCannotProve", email)["givenNames"], email)
            assertNull(row("damagedSignatureAreaCannotProve", email)["identityEvidence"], email)
        }

        // I-2 边界：问号只出现在署名区之外（正文）时，已确定的正常署名区仍能出证明。
        assertEquals("Jane Doe", contactOwner("questionMarkOutsideAuthorAreaDoesNotBlock", "jane@uni.edu"))

        // I-2/I-4：损坏标记使标记证明不可用，但独立的 `Jane Doe: a@uni.edu` 记录仍凭自身绑定。
        assertTrue(contacts("damagedMarkerKeepsIndependentNameRecord").isEmpty())
        val independent = row("damagedMarkerKeepsIndependentNameRecord", "jane@uni.edu")
        assertEquals("Jane", independent["givenNames"])
        assertTrue((independent["identityEvidence"] as String).startsWith("SOURCE_SHA256:"))

        // 既有回归：同一联系行多标记仍各自绑定自己的作者。
        assertEquals("Jane Doe", contactOwner("sameContactLineTwoMarkersBindEachOwner", "jane@uni.edu"))
        assertEquals("John Smith", contactOwner("sameContactLineTwoMarkersBindEachOwner", "john@uni.edu"))
    }
}

/** Real PDF/HTML parsing with an HTTP response fixture; only the transport is replaced. */
internal fun extractOwnershipContent(
    content: ByteArray,
    mediaType: org.springframework.http.MediaType,
    authors: List<PaperAuthor>
): com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome {
    val template = org.springframework.web.client.RestTemplate()
    val server = org.springframework.test.web.client.MockRestServiceServer.bindTo(template).build()
    server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://paper.test/content"))
        .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(content, mediaType))
    val http = object : com.weibo.talentintroduction.config.BoundedHttpExecutor {
        override fun <T : Any> getForObject(base: org.springframework.web.client.RestTemplate, url: String,
            responseType: Class<T>, connectCapMs: Long, readCapMs: Long, deadline: java.time.Instant?): T? =
            base.getForObject(url, responseType)
        override fun <T> execute(base: org.springframework.web.client.RestTemplate, uri: java.net.URI,
            connectCapMs: Long, readCapMs: Long, deadline: java.time.Instant?,
            responseExtractor: org.springframework.web.client.ResponseExtractor<T>): T? =
            base.execute(uri, org.springframework.http.HttpMethod.GET, null, responseExtractor)
    }
    val result = PdfEmailExtractor(template, PlainTextEmailExtractor(),
        com.weibo.talentintroduction.config.PdfExtractionProperties(), http)
        .extract("https://paper.test/content", authors, "TEST")
    server.verify()
    return result
}

/** A synthetic PDF with explicit contact text, not a claimed copy of a published paper. */
internal fun ownershipPdf(vararg lines: String): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
        val page = org.apache.pdfbox.pdmodel.PDPage()
        doc.addPage(page)
        org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page).use { stream ->
            stream.beginText()
            stream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 11f)
            stream.newLineAtOffset(40f, 720f)
            for (line in lines) { stream.showText(line); stream.newLineAtOffset(0f, -16f) }
            stream.endText()
        }
        doc.save(output)
    }
    return output.toByteArray()
}

/** Synthetic coordinate controls; the archived published PDFs remain byte-for-byte unchanged. */
internal fun positionedPdf(lines: List<Triple<Float, Float, String>>): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
        val page = org.apache.pdfbox.pdmodel.PDPage()
        doc.addPage(page)
        org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page).use { stream ->
            stream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 11f)
            for ((x, y, text) in lines) {
                stream.beginText()
                stream.newLineAtOffset(x, y)
                stream.showText(text)
                stream.endText()
            }
        }
        doc.save(output)
    }
    return output.toByteArray()
}

/**
 * Synthetic PDF whose author line carries `∗` (U+2217): WinAnsi Helvetica cannot encode it, so the
 * marker is drawn with the Symbol font on the same line. The contact line keeps the ASCII `*`.
 */
internal fun positionedPdfWithSymbolAsterisk(authorLine: String, contactLine: String): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
        val page = org.apache.pdfbox.pdmodel.PDPage()
        doc.addPage(page)
        org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page).use { stream ->
            stream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 11f)
            stream.beginText()
            stream.newLineAtOffset(50f, 720f)
            stream.showText(authorLine)
            stream.endText()
            stream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.SYMBOL, 11f)
            stream.beginText()
            stream.newLineAtOffset(103f, 720f)
            stream.showText("\u2217")
            stream.endText()
            stream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 11f)
            stream.beginText()
            stream.newLineAtOffset(50f, 600f)
            stream.showText(contactLine)
            stream.endText()
        }
        doc.save(output)
    }
    return output.toByteArray()
}

/** Each control has the same PDFBox path, but is expressly synthetic rather than a source-paper claim. */
internal fun pdfLayoutNegativeCases(): List<Triple<List<PaperAuthor>, List<Triple<Float, Float, String>>, String>> {
    val jane = PaperAuthor("Jane", "Doe", "0000-0002-1825-0097", "Jane Lab", true, openAlexAuthorId = "A123")
    val john = PaperAuthor("John", "Smith", null, "John Lab", false)
    return listOf(
        Triple(listOf(jane, john), listOf(
            Triple(50f, 720f, "Jane Doe* John Smith*"), Triple(50f, 600f, "*Email: opaque@uni.edu")), "shared marker"),
        Triple(listOf(jane, jane.copy(orcidId = "other")), listOf(
            Triple(50f, 720f, "Jane Doe*"), Triple(50f, 600f, "*Email: opaque@uni.edu")), "duplicate name"),
        Triple(listOf(PaperAuthor("Shirui", "Pan", null, null), PaperAuthor("Samantha", "Pan", null, null)),
            listOf(Triple(50f, 720f, "Shirui Pan Samantha Pan"),
                Triple(50f, 220f, "S. Pan is with University (Email: opaque@uni.edu).")), "ambiguous initials"),
        Triple(listOf(jane), listOf(
            Triple(50f, 720f, "Jane Doe*"), Triple(350f, 600f, "*Email: opaque@uni.edu")), "cross-column"),
        Triple(listOf(jane), listOf(
            Triple(50f, 720f, "Jane Doe"), Triple(50f, 220f, "References: Jane Doe Email: opaque@uni.edu")), "references")
    )
}
