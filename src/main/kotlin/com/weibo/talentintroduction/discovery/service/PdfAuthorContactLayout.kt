package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.text.Normalizer
import java.util.Locale

/** Bounded, same-page author/contact evidence; no inference from the article body or reference list. */
internal object PdfAuthorContactLayout {
    data class Contact(val email: String, val authorIndex: Int, val page: Int, val authorText: String, val contactText: String) {
        val evidenceText get() = "PDF_PAGE:$page\nAUTHOR:$authorText\nCONTACT:$contactText"
    }

    private data class Line(val page: Int, val x: Float, val right: Float, val y: Float, val text: String)
    private val mailbox = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val emailExtractor = PlainTextEmailExtractor()
    /** I-1/I-2：作者与联系两侧共用的标记集合，`∗` 与 `*` 归一。 */
    private val markerGlyph = Regex("[*∗†‡§]")
    private val markerContact = Regex("^\\s*([*∗†‡§])")
    private val paragraphStart = Regex("^\\s*((?:[A-Z]\\.\\s*){1,4}[A-Z][A-Za-z'-]+|[A-Z][A-Za-z'-]+(?:\\s+[A-Z][A-Za-z.'-]+){1,3})\\s+is\\s+with\\b", RegexOption.IGNORE_CASE)
    /** I-2：下一个作者的联系语句（含 `are with` 与 `A. K. Singh` 这类多首字母）终止上一段，与元数据匹配无关。 */
    private val paragraphBoundary = Regex("^\\s*((?:[A-Z]\\.\\s*){1,4}[A-Z][A-Za-z'-]+|[A-Z][A-Za-z'-]+(?:\\s+[A-Z][A-Za-z.'-]+){1,3})\\s+(?:is|are)\\s+with\\b", RegexOption.IGNORE_CASE)
    private val emailLabel = Regex("\\be-?mail\\s*:", RegexOption.IGNORE_CASE)
    /** I-1：作者区之后的行（编号机构脚注、联系行、摘要等），其中的标记不是作者署名。 */
    private val outsideAuthorArea = Regex(
        "(?i)^\\s*(?:\\d|[*∗†‡§]|abstract\\b|keywords?\\b|received\\b|accepted\\b|published\\b|doi\\b|https?://|©|downloaded\\b|corresponding\\b|e-?mail\\b)")
    /** I-1：机构行关键词；这类行只说明脚注归属，不产生邮箱拥有者。 */
    private val affiliationLine = Regex(
        "(?i)\\b(?:universit|institut|school|department|faculty|college|centre|center|academy|laborator|gmbh|fondazione|foundation|hospital|chair\\s+of|tno)\\b")
    /** I-1：紧贴标记左侧的署名词串；中间只允许数字/逗号/空白，因此不会跨过上一个标记或前一位署名。 */
    private val signatureBeforeMarker = Regex("([\\p{L}][\\p{L}'’.-]*(?:[\\s,]+[\\p{L}][\\p{L}'’.-]*)*)[\\d,\\s]*$")

    fun selectedPages(pageCount: Int, maxPages: Int, tailPages: Int): List<Int> {
        if (pageCount < 1) return emptyList()
        return ((1..minOf(maxPages, pageCount)).toList() +
            (if (tailPages == 1) listOf(pageCount) else emptyList())).distinct()
    }

    fun collect(
        document: PDDocument,
        maxPages: Int,
        authors: List<PaperAuthor>,
        tailPages: Int = 0,
        beforePage: () -> Unit = {},
        onPage: (Int, String) -> Unit = { _, _ -> }
    ): List<Contact> {
        if (document.numberOfPages < 1) return emptyList()
        val pages = selectedPages(document.numberOfPages, maxPages, tailPages)
        val contacts = mutableListOf<Contact>()
        for (page in pages) {
            beforePage()
            val glyphs = mutableListOf<TextPosition>()
            val stripper = object : PDFTextStripper() {
                override fun processTextPosition(text: TextPosition) {
                    if (authors.isNotEmpty() && text.dir == 0f) glyphs += text
                    super.processTextPosition(text)
                }
            }
            stripper.startPage = page
            stripper.endPage = page
            val pageText = stripper.getText(document)
            onPage(page, pageText)
            if (authors.isEmpty()) continue
            val height = document.getPage(page - 1).mediaBox.height
            val width = document.getPage(page - 1).mediaBox.width
            val lines = toLines(page, glyphs, width)
            contacts += endContactBlocks(page, pageText, authors)
            val header = authorHeader(lines, height, authors) ?: continue
            // I-1：拥有者计数取自作者署名区（含匹配不上元数据的署名）；分不清作者段则回退为空集。
            val owners = markerOwners(authorArea(lines, header, height).orEmpty(), authors)
            for (line in lines) {
                if (line.y <= header.y || line.y > height * .90f) continue
                if (header.right < width * .48f && line.x > width * .52f) continue
                if (header.x > width * .52f && line.right < width * .48f) continue
                // I-2：同一联系行按标记边界切分，各自抽邮箱；分段不明确则整行不出证明。
                for ((marker, segment) in markerSegments(line.text)) {
                    val owner = uniqueOwner(owners, marker) ?: continue
                    // I-3：把该片段自己的标记一起交给提取器，裸标记残缺邮箱（`∗ lun yue@msn.com`）
                    // 才与整页文本走同一条过滤；Contact/evidenceText 仍保留原始行。
                    for (address in emailExtractor.extract("$marker$segment")) {
                        contacts += Contact(address, owner, page, header.text, line.text)
                    }
                }
            }
            // A contact paragraph is its own column-local record, not a line chosen by nearest email.
            for (start in lines.indices) {
                val line = lines[start]
                if (line.y <= header.y || line.y < height * .60f || line.y > height * .94f) continue
                val anchor = paragraphStart.find(line.text)?.groupValues?.get(1) ?: continue
                val owner = uniqueAuthor(anchor, header.text, authors) ?: continue
                val paragraph = mutableListOf(line)
                for (next in lines.drop(start + 1)) {
                    if (next.y - paragraph.last().y > 16f || next.y > height * .95f) break
                    if (line.x < width * .5f && next.x >= width * .5f) continue
                    if (next.x > line.right + 24f || next.right < line.x - 24f) continue
                    if (paragraphBoundary.containsMatchIn(next.text) ||
                        next.text.startsWith("Corresponding author:", ignoreCase = true) || paragraph.size == 5) break
                    paragraph += next
                }
                val text = paragraph.joinToString(" ") { it.text }
                if (!emailLabel.containsMatchIn(text)) continue
                // A second author's explicit mention or a shared contact list defeats the single owner.
                if (authors.indices.any { it != owner && mentionsAuthor(text, authors[it]) }) continue
                val addresses = emailExtractor.extract(text)
                for (address in addresses) contacts += Contact(address, owner, page, header.text, text)
            }
        }
        return contacts
    }

    /** Section heading + actual metadata author roster + independent name/mailbox records. */
    private fun endContactBlocks(page: Int, text: String, authors: List<PaperAuthor>): List<Contact> {
        val lines = text.lines().map(String::trim)
        val section = lines.indexOfFirst { it.matches(Regex("(?i)authors\\s+and\\s+affiliations")) }
        if (section < 0) return emptyList()
        val remainder = lines.drop(section + 1)
        val roster = remainder.takeWhile { line ->
            !authors.any { normalize(line) == normalize(fullName(it).orEmpty()) }
        }.take(5)
        val rosterText = roster.joinToString(" ")
        if (authors.count { containsName(rosterText, it) } < 2) return emptyList()
        val contacts = mutableListOf<Contact>()
        var index = roster.size
        while (index + 1 < remainder.size) {
            val line = remainder[index]
            val owner = authors.indices.singleOrNull { author ->
                normalize(line) == normalize(fullName(authors[author]).orEmpty()) &&
                    containsName(rosterText, authors[author])
            } ?: break
            val address = mailbox.matchEntire(remainder[index + 1])?.value ?: break
            contacts += Contact(address, owner, page, rosterText, "$line\n$address")
            index += 2
        }
        return contacts
    }

    private fun authorHeader(lines: List<Line>, height: Float, authors: List<PaperAuthor>): Line? {
        for ((index, line) in lines.withIndex()) {
            if (line.y > height * .36f) break
            val next = lines.getOrNull(index + 1)
            val text = if (next != null && next.y - line.y in 1f..20f && next.y < height * .36f)
                line.text + " " + next.text else line.text
            val matching = authors.count { containsName(text, it) }
            // I-1：标题行判据保持「至少一个受支持标记前面能解析出署名」，与归属计数（含 null 拥有者）解耦。
            if (matching >= 2 || (matching == 1 && hasSignatureMarker(text)))
                return line.copy(text = text)
        }
        return null
    }

    /**
     * I-1：作者署名区 —— 作者标题行起，遇到机构行/联系行/摘要等即止的连续署名行。
     * 分不清作者段时返回 null，调用方因此不产出任何标记归属。
     */
    private fun authorArea(lines: List<Line>, header: Line, height: Float): String? =
        lines.filter { it.y >= header.y }
            .takeWhile { it.y <= height * .36f && !outsideAuthorArea.containsMatchIn(it.text) &&
                !affiliationLine.containsMatchIn(it.text) }
            .joinToString(" ") { it.text }
            .takeIf { it.isNotBlank() }

    /**
     * I-1：每个标记在作者署名区的拥有者集合；`null` 元素代表一处无法对应元数据的署名。
     * 连续标记组（数字/逗号/空白连接、至少一个受支持符号，如 `1,†,*`）只为组首解析一次前置署名，
     * 组内每个符号共享同一个拥有者集合；解析不出署名的符号登记 null 而不是跳过 ——
     * 只对成功匹配的元数据作者计数会把共享标记误判给首位作者。
     */
    private fun markerOwners(area: String, authors: List<PaperAuthor>): Map<String, Set<Int?>> {
        // I-2：署名区出现已证实的解码损坏（`?` 或 U+FFFD）时，本页「标记 → 作者」归属不可用。
        if (damagedSignature.containsMatchIn(area)) return emptyMap()
        val result = mutableMapOf<String, MutableSet<Int?>>()
        val markers = markerGlyph.findAll(area).toList()
        var index = 0
        while (index < markers.size) {
            var end = index
            while (end + 1 < markers.size &&
                markerGroupGap.matches(area.substring(markers[end].range.last + 1, markers[end + 1].range.first))) {
                end++
            }
            val owners = ownersOfSignature(signatureBefore(area, markers[index].range.first), authors)
            for (position in index..end) {
                result.getOrPut(normalizeMarker(markers[position].value)) { mutableSetOf() }.addAll(owners)
            }
            index = end + 1
        }
        return result
    }

    /** I-2：已证实的解码损坏符号（原文视觉星号解码成 `?`）；不做 `? → *` 猜测替换。 */
    private val damagedSignature = Regex("[?\\uFFFD]")

    /** I-1：组内相邻符号之间只允许数字/逗号/空白；出现字母即跨姓名边界，成为新的一组。 */
    private val markerGroupGap = Regex("[\\d,\\s]*")

    /** I-1：解析出的署名词串 → 元数据作者；解析不出署名或对不上元数据都登记 null 拥有者。 */
    private fun ownersOfSignature(signature: String?, authors: List<PaperAuthor>): Set<Int?> {
        val tokens = signature?.split(' ') ?: return setOf(null)
        // 最长候选优先：只删除左侧多余词（连接词/前一行残词），不为匹配去猜姓名变体。
        val owners = tokens.indices.asSequence()
            .map { ownersOfMetadataName(tokens.drop(it).joinToString(" "), authors) }
            .firstOrNull { it.isNotEmpty() } ?: emptyList()
        return owners.ifEmpty { listOf(null) }.toSet()
    }

    /** 作者标题行的既有判据：至少一个受支持标记前面能解析出署名词串（与归属计数无关）。 */
    private fun hasSignatureMarker(text: String): Boolean =
        markerGlyph.findAll(text).any { signatureBefore(text, it.range.first) != null }

    /** I-1：标记紧邻左侧的署名词串；只有数字/逗号/空白间隔才算署名，标记之间的成组符号不算。 */
    private fun signatureBefore(area: String, markerIndex: Int): String? =
        signatureBeforeMarker.find(area.substring(0, markerIndex))
            ?.groupValues?.get(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun ownersOfMetadataName(candidate: String, authors: List<PaperAuthor>): List<Int> =
        authors.indices.filter { normalize(candidate) == normalize(fullName(authors[it]).orEmpty()) }

    /** I-1：只有唯一的、能对应元数据的拥有者才证明邮箱；否则该标记只留下邮箱线索。 */
    private fun uniqueOwner(owners: Map<String, Set<Int?>>, marker: String): Int? =
        owners[normalizeMarker(marker)]?.singleOrNull()

    /** I-2：同一联系行按标记边界切分成 (标记, 片段)；标记成组时分段不明确，整行不出证明。 */
    private fun markerSegments(text: String): List<Pair<String, String>> {
        val first = markerContact.find(text) ?: return emptyList()
        val marks = mutableListOf(first.range.last to first.groupValues[1])
        var cursor = first.range.last + 1
        while (cursor < text.length) {
            val next = markerGlyph.find(text, cursor) ?: break
            if (text.substring(marks.last().first + 1, next.range.first).isBlank()) return emptyList()
            marks += next.range.first to next.value
            cursor = next.range.last + 1
        }
        return marks.mapIndexed { position, (offset, marker) ->
            val end = marks.getOrNull(position + 1)?.first ?: text.length
            marker to text.substring(offset + 1, end)
        }
    }

    private fun normalizeMarker(marker: String): String = if (marker == "∗") "*" else marker

    private fun uniqueAuthor(anchor: String, header: String, authors: List<PaperAuthor>): Int? {
        val matched = authors.indices.filter { index ->
            containsName(header, authors[index]) &&
                (normalize(anchor) == normalize(fullName(authors[index]).orEmpty()) ||
                    abbreviation(author = authors[index]) == normalize(anchor).replace(" ", ""))
        }
        return matched.singleOrNull()
    }

    private fun abbreviation(author: PaperAuthor): String? {
        val given = author.givenNames?.split(Regex("[\\s.-]+"))?.filter { it.isNotEmpty() } ?: return null
        val family = author.familyNames ?: return null
        return normalize(given.joinToString("") { it.first().toString() + "." } + family).replace(" ", "")
    }

    private fun mentionsAuthor(text: String, author: PaperAuthor): Boolean {
        if (containsName(text, author)) return true
        val given = author.givenNames?.split(Regex("[\\s.-]+"))?.filter { it.isNotEmpty() } ?: return false
        val family = author.familyNames ?: return false
        val pattern = given.joinToString("\\s*") { Regex.escape(it.first().toString() + ".") } +
            "\\s*" + Regex.escape(family)
        return Regex("(?<![\\p{L}])$pattern(?![\\p{L}])", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    private fun containsName(text: String, author: PaperAuthor): Boolean {
        val name = fullName(author) ?: return false
        return Regex("(?<![\\p{L}])${name.split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }}(?![\\p{L}])", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
    }

    private fun fullName(author: PaperAuthor): String? =
        if (author.givenNames.isNullOrBlank() || author.familyNames.isNullOrBlank()) null
        else "${author.givenNames} ${author.familyNames}"

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")

    private fun toLines(page: Int, positions: List<TextPosition>, pageWidth: Float): List<Line> {
        val rows = mutableListOf<MutableList<TextPosition>>()
        for (position in positions.sortedWith(compareBy<TextPosition> { it.yDirAdj }.thenBy { it.xDirAdj })) {
            if (position.xDirAdj < 40f) continue
            val row = rows.lastOrNull()?.takeIf { kotlin.math.abs(it.first().yDirAdj - position.yDirAdj) < 5f }
                ?: mutableListOf<TextPosition>().also { rows += it }
            row += position
        }
        val lines = mutableListOf<Line>()
        for (row in rows) {
            val ordered = row.sortedBy { it.xDirAdj }
            var text = StringBuilder()
            var left = ordered.first().xDirAdj
            var right = left
            fun flush() {
                if (text.isNotBlank()) lines += Line(page, left, right, row.first().yDirAdj, text.toString().trim())
                text = StringBuilder()
            }
            for (position in ordered) {
                val x = position.xDirAdj
                val gap = x - right
                if (text.isNotEmpty() && (gap > 24f ||
                        (gap > 6f && right < pageWidth * .5f && x > pageWidth * .5f))) {
                    flush()
                    left = x
                } else if (text.isNotEmpty() && gap > 1.2f) text.append(' ')
                if (position.unicode.isBlank()) {
                    if (text.isNotEmpty() && text.last() != ' ') text.append(' ')
                } else text.append(position.unicode)
                right = maxOf(right, x + position.widthDirAdj)
            }
            flush()
        }
        return lines.sortedWith(compareBy<Line> { it.y }.thenBy { it.x })
    }
}
