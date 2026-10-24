package com.weibo.talentintroduction.discovery.archive

import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Component
import java.io.StringReader
import javax.swing.text.MutableAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Historical source evidence only. Page parsing has a checked soft deadline, not a hard timeout. */
@Component
class PaperQualificationEvidenceExtractor {
    internal var nanoTime: () -> Long = System::nanoTime
    fun extract(bytes: ByteArray, format: String, authors: List<PaperAuthor>, paperYear: Int?, bodySha256: String): PaperQualificationResult {
        val start = nanoTime()
        val reasons = mutableListOf<String>()
        val pages = mutableListOf<Pair<Int?, String>>()
        var totalPages = 0
        var scannedPages = 0
        var textBytes = 0
        try {
            if (format == "PDF") {
                PDDocument.load(bytes).use { doc ->
                    totalPages = doc.numberOfPages
                    for (page in 1..minOf(totalPages, MAX_PAGES)) {
                        if (nanoTime() - start >= SOFT_NANOS) { reasons += "SOFT_TIME_LIMIT"; break }
                        val text = PDFTextStripper().apply { startPage = page; endPage = page; sortByPosition = true }.getText(doc)
                        scannedPages++
                        val size = text.toByteArray(Charsets.UTF_8).size
                        if (size > MAX_TEXT_BYTES - textBytes) { reasons += "TEXT_LIMIT"; break }
                        textBytes += size
                        pages += page to text
                        if (nanoTime() - start >= SOFT_NANOS) { reasons += "SOFT_TIME_LIMIT"; break }
                    }
                    if (totalPages > MAX_PAGES) reasons += "PAGE_LIMIT"
                }
            } else {
                val text = safeText(bytes, format)
                if (text.toByteArray(Charsets.UTF_8).size > MAX_TEXT_BYTES) {
                    reasons += "TEXT_LIMIT"
                    // A whole source prefix only; never retain a partially decoded UTF-8 sequence.
                    pages += null to text.take(MAX_TEXT_BYTES / 4)
                } else pages += null to text
            }
            val names = authors.map { listOfNotNull(it.givenNames, it.familyNames).joinToString(" ").trim().replace(Regex("\\s+"), " ") }
            val facts = mutableListOf<PaperQualificationFact>()
            var paragraph = 0
            var biography = false
            for ((page, text) in pages) {
                for (block in paragraphs(text, names)) {
                    paragraph++
                    val trimmed = block.trim()
                    if (BIOGRAPHY.matches(trimmed)) { biography = true; continue }
                    if (SECTION.matches(trimmed)) { biography = false; continue }
                    val owners = names.indices.filter { names[it].contains(' ') && namePattern(names[it]).containsMatchIn(block) }
                    if (owners.size != 1) continue
                    val owner = owners.single()
                    val name = names[owner]
                    if (names.count { it.equals(name, true) } != 1) continue
                    val independent = Regex("(?is)^\\s*" + Regex.escape(name) + "\\s*\\n").containsMatchIn(block)
                    if (!biography && !independent) continue
                    if (NEGATIVE.containsMatchIn(block)) continue
                    val subject = "(?i)(?<![\\p{L}\\p{N}])" + Regex.escape(name) + "(?![\\p{L}\\p{N}])"
                    val assertion = Regex(subject + "\\s+(?:is|was)\\s+(?:(?:an?|currently|now|presently|the)\\s+)*(assistant\\s+professor|associate\\s+professor|professor)\\b")
                    val candidates = mutableListOf<Pair<PaperQualificationKind, MatchResult>>()
                    assertion.findAll(block).forEach { m -> candidates += when {
                        m.groupValues[1].startsWith("assistant", true) -> PaperQualificationKind.ASSISTANT_PROFESSOR
                        m.groupValues[1].startsWith("associate", true) -> PaperQualificationKind.ASSOCIATE_PROFESSOR
                        else -> PaperQualificationKind.PROFESSOR
                    } to m }
                    // Each assertion must explicitly name its subject; pronouns cannot supply ownership.
                    Regex(subject + "\\s+(?:received|earned|obtained|was\\s+awarded)\\s+(?:(?:his|her|their|a|the)\\s+)?(Ph\\.?\\s?D\\.?|doctorate)(?!\\.?\\s+(?:candidate|candidacy|student)\\b)(?:\\s+degree)?\\b", RegexOption.IGNORE_CASE)
                        .find(block)?.let { candidates += PaperQualificationKind.COMPLETED_DOCTORATE to it }
                    Regex(subject + "\\s+(?:is|was)\\s+(?:(?:an?|currently|now|presently)\\s+)*((?:Ph\\.?\\s?D\\.?|doctoral)\\s+(?:student|candidate))\\b", RegexOption.IGNORE_CASE)
                        .find(block)?.let { candidates += PaperQualificationKind.DOCTORAL_STUDENT to it }
                    val conflict = (candidates.any { it.first == PaperQualificationKind.COMPLETED_DOCTORATE } && candidates.any { it.first == PaperQualificationKind.DOCTORAL_STUDENT }) ||
                        candidates.map { it.first }.filter { it in setOf(PaperQualificationKind.ASSISTANT_PROFESSOR, PaperQualificationKind.ASSOCIATE_PROFESSOR, PaperQualificationKind.PROFESSOR) }.distinct().size > 1
                    candidates.forEach { (kind, match) ->
                        if (facts.size >= MAX_FACTS) { reasons += "FACT_LIMIT"; return@forEach }
                        val term = match.groupValues[1]
                        // Never silently truncate the assertion out of a long paragraph.
                        val quoteStart = (match.range.first - 200).coerceAtLeast(0)
                        val quote = block.substring(quoteStart, minOf(block.length, quoteStart + 1000))
                        facts += PaperQualificationFact(owner, name, if (conflict) PaperQualificationKind.CONFLICT else kind,
                            term, quote, page, paragraph, paperYear, bodySha256, rule = if (conflict) "BIOGRAPHY_CONFLICT" else "EXPLICIT_NAMED_BIOGRAPHY")
                    }
                }
            }
            val distinct = facts.distinctBy { listOf(it.authorIndex, it.kind, it.quote, it.page) }
            return PaperQualificationResult(distinct, PaperAnalysisCoverage(when {
                reasons.isNotEmpty() -> PaperEvidenceStatus.PARTIAL
                distinct.isEmpty() -> PaperEvidenceStatus.NOT_FOUND
                else -> PaperEvidenceStatus.FOUND
            }, scannedPages, totalPages, reasons.distinct()))
        } catch (_: Exception) {
            return PaperQualificationResult(emptyList(), PaperAnalysisCoverage(PaperEvidenceStatus.ERROR, scannedPages, totalPages, listOf("PARSE_ERROR")))
        }
    }

    /** No external entity, DTD, script or style content is admitted. Also reused before the JATS email parser. */
    internal fun safeText(bytes: ByteArray, format: String): String {
        val text = bytes.toString(Charsets.UTF_8)
        if (format == "XML") {
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                isXIncludeAware = false; isExpandEntityReferences = false
            }
            val doc = factory.newDocumentBuilder().apply {
                setErrorHandler(object : org.xml.sax.helpers.DefaultHandler() {
                    override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }
                    override fun error(e: org.xml.sax.SAXParseException) { throw e }
                })
            }.parse(bytes.inputStream())
            val result = StringBuilder()
            fun visit(node: org.w3c.dom.Node) {
                if (node.nodeType == org.w3c.dom.Node.TEXT_NODE) { result.append(node.nodeValue); return }
                val block = node.nodeName.lowercase() in setOf("p", "title", "bio", "sec", "ref", "script", "style")
                if (block) result.append("\n\n")
                if (node.nodeName.lowercase() !in setOf("script", "style")) {
                    for (i in 0 until node.childNodes.length) visit(node.childNodes.item(i))
                }
                if (block) result.append("\n\n")
            }
            visit(doc.documentElement)
            return result.toString()
        }
        if (format == "HTML") {
            val result = StringBuilder(); var suppressed = 0
            ParserDelegator().parse(StringReader(text), object : HTMLEditorKit.ParserCallback() {
                override fun handleStartTag(tag: HTML.Tag, attrs: MutableAttributeSet, pos: Int) {
                    if (tag in listOf(HTML.Tag.SCRIPT, HTML.Tag.STYLE)) suppressed++
                    if (tag.isBlock) result.append("\n\n")
                }
                override fun handleEndTag(tag: HTML.Tag, pos: Int) {
                    if (tag in listOf(HTML.Tag.SCRIPT, HTML.Tag.STYLE) && suppressed > 0) suppressed--
                    if (tag.isBlock) result.append("\n\n")
                }
                override fun handleSimpleTag(tag: HTML.Tag, attrs: MutableAttributeSet, pos: Int) { if (tag == HTML.Tag.BR) result.append('\n') }
                override fun handleText(data: CharArray, pos: Int) { if (suppressed == 0) result.append(data) }
            }, true)
            return result.toString()
        }
        return text
    }
    private fun paragraphs(text: String, names: List<String>): List<String> {
        val blocks = mutableListOf<String>(); val current = StringBuilder()
        fun flush() { if (current.isNotBlank()) { blocks += current.toString().trim(); current.setLength(0) } }
        for (line in text.lines()) {
            val value = line.trim()
            val boundary = BIOGRAPHY.matches(value) || SECTION.matches(value)
            val named = names.any { it.contains(' ') && Regex("(?i)^" + Regex.escape(it) + "(?:\\s|$)").containsMatchIn(value) }
            if (value.isEmpty() || boundary || (named && current.isNotBlank() && names.none { current.toString().trim().equals(it, true) })) flush()
            if (value.isNotEmpty()) current.append(line).append('\n')
            if (boundary) flush()
        }
        flush()
        return blocks
    }
    private fun namePattern(name: String) = Regex("(?i)(?<![\\p{L}\\p{N}])" + Regex.escape(name) + "(?![\\p{L}\\p{N}])")
    companion object {
        const val VERSION = 2
        const val MAX_PAGES = 100
        const val MAX_TEXT_BYTES = 2 * 1024 * 1024
        const val MAX_FACTS = 100
        const val SOFT_NANOS = 10_000_000_000L
        private val BIOGRAPHY = Regex("(?i)^(?:author\\s+)?biograph(?:y|ies)|^about\\s+the\\s+authors?$")
        private val SECTION = Regex("(?i)^(?:(?:[0-9]+[.)]?\\s+)?(?:references|acknowledg(?:e)?ments|conclusions?|introduction|methods?|results?|discussion)|[0-9]+[.)]?\\s+[A-Z][a-zA-Z ]{2,60})$")
        private val NEGATIVE = Regex("(?i)\\b(?:not|never|no|expected|expecting|pursuing|working\\s+towards?)\\b")
    }
}
