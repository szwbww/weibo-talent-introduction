package com.weibo.talentintroduction.discovery.archive

import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Component
import java.io.StringReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
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
    fun extract(bytes: ByteArray, format: String, authors: List<PaperAuthor>, paperYear: Int?, bodySha256: String,
        publicationDate: LocalDate? = null): PaperQualificationResult {
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
            var outsideBiography = false
            for ((page, text) in pages) {
                for (block in paragraphs(text, names)) {
                    paragraph++
                    val trimmed = block.trim()
                    if (BIOGRAPHY.matches(trimmed)) { outsideBiography = false; continue }
                    if (SECTION.matches(trimmed)) { outsideBiography = true; continue }
                    if (outsideBiography) continue
                    val owners = names.indices.filter { names[it].contains(' ') && namePattern(names[it]).containsMatchIn(block) }
                    if (owners.size != 1) continue
                    val owner = owners.single()
                    val name = names[owner]
                    if (names.count { it.equals(name, true) } != 1) continue
                    // A name at the start of an author biography owns only this paragraph, never the next.
                    val lead = Regex("(?is)^\\s*(?:(?:Dr\\.|Assoc\\.\\s*Prof\\.)\\s+)?" + Regex.escape(name) +
                        "(?![\\p{L}\\p{N}])(?:\\s*\\([^)]{0,120}\\))?\\s*")
                        .find(block) ?: continue
                    val candidates = mutableListOf<Pair<PaperQualificationKind, MatchResult>>()
                    val surname = name.substringAfterLast(' ').takeIf { token ->
                        token.length >= 3 && names.count { it.substringAfterLast(' ').equals(token, true) } == 1
                    }
                    val subject = "(?:" + listOfNotNull(name, surname, "he", "she").joinToString("|") { Regex.escape(it) } + ")"
                    // Keep source whitespace: match.value must still locate the literal assertion in the quote.
                    val namedBody = block.substring(lead.range.last + 1)
                    val body = if (Regex("(?i)^(?:$subject\\b|since\\s+\\d{4})").containsMatchIn(namedBody)) namedBody else "$name $namedBody"
                    for (sentence in body.split(Regex("(?<=[!?])\\s+|(?<!\\b[A-Za-z])(?<!Ph\\.D)\\.\\s+(?=[A-Z])|(?<=\\d)\\.(?=[A-Z])|;\\s*"))) {
                        // Unknown/new authors and headings end ownership even when PDF layout lost the blank line.
                        if (Regex("\\b(?:[A-Z][\\p{L}'-]*\\.?\\s+){1,4}[A-Z][\\p{L}'-]+\\s+(?:is|was|received|holds)\\b")
                                .containsMatchIn(sentence) && !sentence.startsWith(name) &&
                            !Regex("(?i)^(?:since\\s+\\d{4}[,\\s]+)?$subject\\s+").containsMatchIn(sentence)) break
                        if (SECTION.matches(sentence.trim()) || sentence.trim().matches(Regex("[A-Z][A-Z ]{3,60}"))) break
                        if (Regex("(?i)^(?:prompt|example(?: question)?|LLM response)\\b").containsMatchIn(sentence)) break
                        val owned = Regex("(?i)^\\s*(?:since\\s+\\d{4}[,\\s]+)?$subject\\s+").find(sentence)
                        val continuation = Regex("(?i)^\\s*(?:received|currently\\s+holding)\\b").containsMatchIn(sentence)
                        if (owned == null && !continuation) continue
                        val predicate = sentence.substring(owned?.range?.last?.plus(1) ?: 0)
                        if (!Regex("(?i)\\b(?:not|never|no)\\b").containsMatchIn(sentence) &&
                            Regex("(?i)^(?:is|was)\\s+|\\b(?:pursu(?:es|ing)|working\\s+towards?)\\b").containsMatchIn(predicate)) {
                            Regex("(?i)((?:$DOCTORATE|doctoral)\\s+(?:student|candidate))\\b|" +
                                "(?:pursu(?:es|ing)|working\\s+towards?)\\s+(?:(?:his|her|their|a|the)\\s+)?($DOCTORATE)")
                                .find(predicate)?.let { match ->
                                    candidates += PaperQualificationKind.DOCTORAL_STUDENT to match
                                }
                        }
                        if (!NEGATIVE.containsMatchIn(sentence)) {
                            if (Regex("(?is)^(?:is|was|has\\s+been)\\s+|^has\\b.{0,150}\\bcurrently\\s+working\\s+as\\s+|^currently\\s+holding\\b")
                                    .containsMatchIn(predicate)) {
                                Regex("(?i)(?:^(?:is|was|has\\s+been)\\s+(?:(?:an?|currently|now|presently|the)\\s+)*|" +
                                    "\\band\\s+(?:an?\\s+)?|\\bworking\\s+as\\s+(?:an?\\s+)?|" +
                                    "^currently\\s+holding\\s+(?:the\\s+)?(?:esteemed\\s+)?position\\s+of\\s+)" +
                                    "(assistant\\s+professor|associate\\s+professor|professor)\\b")
                                    .findAll(predicate).filterNot { ROLE_NEGATIVE.containsMatchIn(sentence) }.forEach { match ->
                                        candidates += when {
                                            match.groupValues[1].startsWith("assistant", true) -> PaperQualificationKind.ASSISTANT_PROFESSOR
                                            match.groupValues[1].startsWith("associate", true) -> PaperQualificationKind.ASSOCIATE_PROFESSOR
                                            else -> PaperQualificationKind.PROFESSOR
                                        } to match
                                    }
                            }
                            Regex("(?i)^(?:received|earned|obtained|holds|was\\s+awarded)\\s+" +
                                "[^;!?]{0,500}?($DOCTORATE)(?!\\.?\\s+(?:candidate|candidacy|student)\\b)(?:\\s+degree)?")
                                .find(predicate)?.let { candidates += PaperQualificationKind.COMPLETED_DOCTORATE to it }
                            Regex("(?i)^was\\s+born\\s+(?:(?:on|in)\\s+)?(?:[^.!?]{0,150}\\s+in\\s+)?($FULL_DATE)(?![\\p{L}\\p{N}-])")
                                .find(predicate)?.let { match ->
                                    val birth = parseBirthDate(match.groupValues[1])
                                    val upper = publicationDate ?: paperYear?.takeIf { it in 1..9999 }?.let { LocalDate.of(it, 12, 31) }
                                    if (birth != null && upper != null && !birth.isAfter(upper))
                                        candidates += PaperQualificationKind.BIRTH_DATE to match
                                }
                        }
                    }
                    val titleConflict = candidates.map { it.first }.filter { it in PROFESSOR_KINDS }.distinct().size > 1
                    val birthConflict = candidates.filter { it.first == PaperQualificationKind.BIRTH_DATE }
                        .map { parseBirthDate(it.second.groupValues[1]) }.distinct().size > 1
                    candidates.forEach { (kind, match) ->
                        if (facts.size >= MAX_FACTS) { reasons += "FACT_LIMIT"; return@forEach }
                        val term = if (kind == PaperQualificationKind.BIRTH_DATE)
                            parseBirthDate(match.groupValues[1]).toString() else match.groupValues[1].ifBlank { match.groupValues.getOrElse(2) { "" } }
                        val conflict = kind in PROFESSOR_KINDS && titleConflict || kind == PaperQualificationKind.BIRTH_DATE && birthConflict
                        // Retain the literal assertion even when the display quote must be bounded.
                        val assertionStart = block.indexOf(match.value).takeIf { it >= 0 } ?: 0
                        val quoteStart = (assertionStart - 200).coerceAtLeast(0)
                        val quote = block.substring(quoteStart, minOf(block.length, maxOf(quoteStart + 1000, assertionStart + match.value.length)))
                        facts += PaperQualificationFact(owner, name, if (conflict) PaperQualificationKind.CONFLICT else kind,
                            term, quote, page, paragraph, paperYear, bodySha256, rule = if (conflict) "BIOGRAPHY_CONFLICT" else "EXPLICIT_UNIQUE_BIOGRAPHY")
                    }
                }
            }
            val distinct = facts.distinctBy { listOf(it.authorIndex, it.kind, it.originalTerm, it.quote, it.page) }
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
            val boundary = BIOGRAPHY.matches(value) || SECTION.matches(value) ||
                value.matches(Regex("[A-Z][A-Z ]{3,60}")) && names.none { it.equals(value, true) }
            val named = names.any { it.contains(' ') && Regex("(?i)^" + Regex.escape(it) + "(?:\\s|$)").containsMatchIn(value) }
            if (value.isEmpty() || boundary || (named && current.isNotBlank() && names.none { current.toString().trim().equals(it, true) })) flush()
            if (value.isNotEmpty()) current.append(line).append('\n')
            if (boundary) flush()
        }
        flush()
        return blocks
    }
    private fun namePattern(name: String) = Regex("(?i)(?<![\\p{L}\\p{N}])" + Regex.escape(name) + "(?![\\p{L}\\p{N}])")
    private fun parseBirthDate(value: String): LocalDate? {
        val normalized = value.trim().replace(Regex("\\s+"), " ").replace(Regex(",(?= \\d{4}$)"), "")
        return BIRTH_FORMATS.firstNotNullOfOrNull { pattern ->
            runCatching { LocalDate.parse(normalized, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
                .withResolverStyle(ResolverStyle.STRICT)) }.getOrNull()
        }?.takeIf { it.year in 1..9999 }
    }
    companion object {
        /** Analysis implementation version; unrelated to identity/cache admission compatibility. */
        const val RULE_VERSION = 3
        const val VERSION = RULE_VERSION
        const val MAX_PAGES = 100
        const val MAX_TEXT_BYTES = 2 * 1024 * 1024
        const val MAX_FACTS = 100
        const val SOFT_NANOS = 10_000_000_000L
        private val BIOGRAPHY = Regex("(?i)^(?:author\\s+)?biograph(?:y|ies)|^about\\s+the\\s+authors?$")
        private val SECTION = Regex("(?i)^(?:(?:[0-9]+[.)]?\\s+)?(?:references|acknowledg(?:e)?ments|conclusions?|introduction|methods?|results?|discussion|funding|author contributions|competing interests|conflicts of interest|research interests)|[0-9]+[.)]?\\s+[A-Z][a-zA-Z ]{2,60})$")
        private val NEGATIVE = Regex("(?i)\\b(?:not|never|no|expected|expecting|pursuing|working\\s+towards?)\\b")
        private val ROLE_NEGATIVE = Regex("(?i)\\b(?:retired|emeritus|former|student|candidate|assistant\\s+to)\\b")
        private const val DOCTORATE = "(?:Ph\\.?\\s?D\\.?|doctorate|Doctor\\s+of\\s+Philosophy|dr\\.?\\s?philos\\.?)(?![\\p{L}])"
        private const val FULL_DATE = "(?:\\d{4}-\\d{2}-\\d{2}|\\d{1,2}\\s+[A-Za-z]+,?\\s+\\d{4}|[A-Za-z]+\\s+\\d{1,2},?\\s*\\d{4})"
        private val BIRTH_FORMATS = listOf("uuuu-MM-dd", "d MMMM uuuu", "d MMM uuuu", "MMMM d uuuu", "MMM d uuuu")
        private val PROFESSOR_KINDS = setOf(PaperQualificationKind.ASSISTANT_PROFESSOR, PaperQualificationKind.ASSOCIATE_PROFESSOR, PaperQualificationKind.PROFESSOR)
    }
}
