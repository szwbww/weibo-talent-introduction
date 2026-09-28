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
    private val markerContact = Regex("^\\s*([*†‡])\\s*(?:(?:correspondence|e-?mail)\\s*:\\s*)?", RegexOption.IGNORE_CASE)
    private val paragraphStart = Regex("^\\s*((?:[A-Z]\\.\\s*){1,4}[A-Z][A-Za-z'-]+|[A-Z][A-Za-z'-]+(?:\\s+[A-Z][A-Za-z.'-]+){1,3})\\s+is\\s+with\\b", RegexOption.IGNORE_CASE)
    private val emailLabel = Regex("\\be-?mail\\s*:", RegexOption.IGNORE_CASE)

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
            val owners = markerOwners(header.text, authors)
            // The geometric header may contain only the first lines of a long author roster.
            // Check the full pre-abstract roster before assigning a shared correspondence marker.
            val abstractStart = Regex("(?im)^\\s*abstract\\b").find(pageText)?.range?.first ?: pageText.length
            val rosterOwners = markerOwners(pageText.substring(0, minOf(abstractStart, 6000)), authors)
            for (line in lines) {
                if (line.y <= header.y || line.y > height * .90f) continue
                if (header.right < width * .48f && line.x > width * .52f) continue
                if (header.x > width * .52f && line.right < width * .48f) continue
                val marker = markerContact.find(line.text)?.groupValues?.get(1) ?: continue
                val addresses = emailExtractor.extract(line.text)
                val owner = owners[marker]?.singleOrNull() ?: continue
                val rosterMatches = rosterOwners[marker]?.distinct().orEmpty()
                if (rosterMatches.size > 1 || (rosterMatches.isNotEmpty() && rosterMatches.single() != owner)) continue
                for (address in addresses) contacts += Contact(address, owner, page, header.text, line.text)
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
                    if (paragraphStart.containsMatchIn(next.text) ||
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
            if (matching >= 2 || (matching == 1 && markerOwners(text, authors).isNotEmpty()))
                return line.copy(text = text)
        }
        return null
    }

    private fun markerOwners(header: String, authors: List<PaperAuthor>): Map<String, List<Int>> {
        val result = mutableMapOf<String, MutableList<Int>>()
        for ((index, author) in authors.withIndex()) {
            val name = fullName(author) ?: continue
            val pattern = Regex("(?<![\\p{L}])${name.split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }}(?![\\p{L}])", RegexOption.IGNORE_CASE)
            for (match in pattern.findAll(header)) {
                val tail = header.substring(match.range.last + 1)
                val marker = Regex("^(?:\\s*\\d+(?:\\s*[,;]\\s*\\d+)*)?\\s*([*†‡])").find(tail)?.groupValues?.get(1)
                if (marker != null) result.getOrPut(marker) { mutableListOf() } += index
            }
        }
        return result
    }

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
