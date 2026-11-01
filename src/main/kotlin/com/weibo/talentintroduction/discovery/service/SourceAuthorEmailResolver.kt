package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import java.io.StringReader
import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale
import javax.swing.text.MutableAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator

/** Resolves bounded source contact entries, never mailbox spelling or nearest author names. */
internal object SourceAuthorEmailResolver {
    private val emails = PlainTextEmailExtractor()
    private val mailbox = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val prefix = Regex("(?i)^[*∗†‡§\\s]*(?:(?:corresponding\\s+author|correspondence|contact)\\s*(?::|to\\b)\\s*)")
    private val separator = Regex("(?i)^\\s*(?::|[–—]|<|\\(|\\[|(?:e-?mail|email address)\\s*:)\\s*(?:e-?mail\\s*:\\s*)?")
    private val emailLine = Regex("(?i)\\be-?mail(?: address)?\\s*:")
    private val shared = Regex("(?i)\\b(?:the\\s+authors|corresponding\\s+authors|equal\\s+contribution|all\\s+authors)\\b")
    private val sectionBoundary = Regex("(?i)^\\s*(?:references|bibliography|abstract|keywords?|introduction)\\b")
    private val affiliation = Regex("(?i)\\b(?:university|department|institute|school|laboratory|college|faculty|centre|center)\\b")
    private val labelledMailbox = Regex("([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\s*\\(([^()\\r\\n]+)\\)")
    // Swing's legacy HTML parser can discard late elements in a complete HTML5 article.
    // Bound the observed adjacent heading/list region before parsing its named links.
    private val namedContactRegion = Regex(
        """<h3\b[^>]*\bid\s*=\s*["']corresponding-author["'][^>]*>\s*Corresponding authors?\s*</h3>\s*<p\b[^>]*\bid\s*=\s*["']corresponding-author-list["'][^>]*>.*?</p>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun resolveText(text: String, authors: List<PaperAuthor>, blacklist: List<String> = emptyList()): List<AuthorEmail> {
        val normalized = emails.normalizeContactText(text)
        val claims = textClaims(text, authors)
        return results(emails.extract(normalized, blacklist), claims, authors)
    }

    fun resolvePdf(
        text: String,
        authors: List<PaperAuthor>,
        contacts: List<PdfAuthorContactLayout.Contact>,
        blacklist: List<String> = emptyList()
    ): List<AuthorEmail> {
        val normalized = emails.normalizeContactText(text)
        // Flattened PDF text supplies mailbox clues only. All ownership, including named
        // text syntax, is collected from bounded physical lines by the layout collector.
        val claims = contacts.map {
            Claim(it.email.lowercase(Locale.ROOT), it.authorIndex, it.evidenceText)
        }
        return results(emails.extract(normalized, blacklist), claims, authors)
    }

    fun resolveHtml(html: String, authors: List<PaperAuthor>, blacklist: List<String> = emptyList()): List<AuthorEmail> =
        resolveHtmlWithVisibility(html, authors, blacklist).second

    internal fun resolveHtmlWithVisibility(
        html: String, authors: List<PaperAuthor>, blacklist: List<String> = emptyList()
    ): Pair<Boolean, List<AuthorEmail>> {
        val root = parseHtml(html)
        val originalText = root.visibleText()
        val normalized = emails.normalizeContactText(originalText)
        val claims = textClaims(originalText, authors).toMutableList()
        val found = LinkedHashSet(emails.extract(normalized, blacklist))
        for (node in root.descendants().filter { it.isAuthor() }) {
            // Shared notes often sit under the last author in arXiv HTML. That is not ownership.
            if (node.descendants().any { it !== node && (it.isAuthor() || it.hasClass("ltx_note")) }) continue
            val content = node.visibleText()
            if (shared.containsMatchIn(content)) continue
            val names = node.descendants().filter { it.hasClass("ltx_personname") || it.attr("itemprop") == "name" }.toList()
            val ownerName = names.singleOrNull()?.visibleText()?.let(::normalize)?.takeIf { it.isNotEmpty() } ?: continue
            val owner = authors.indices.singleOrNull { fullName(authors[it]) == ownerName } ?: continue
            // Only an author's own contact/affiliation node or explicit schema email property.
            val contacts = node.descendants().filter {
                it.hasClass("ltx_contact") || it.attr("itemprop") == "email"
            }.toList()
            for (contact in contacts) {
                val otherNames = authors.indices.filter { it != owner }.any { index ->
                    val name = fullName(authors[index])
                    name.isNotEmpty() && normalize(contact.visibleText()).contains(name)
                }
                if (otherNames) continue
                for (email in emails.extract(contact.visibleText())) claims += Claim(email, owner, content)
            }
        }
        for (region in namedContactRegion.findAll(html)) {
            val list = parseHtml(region.value).descendants()
                .singleOrNull { it.tag == "p" && it.attr("id") == "corresponding-author-list" } ?: continue
            for (anchor in list.descendants().filter { it.tag == "a" }) {
                val email = mailtoMailbox(anchor.attr("href"), blacklist) ?: continue
                found += email
                val name = normalize(anchor.visibleText())
                val owner = authors.indices.singleOrNull { fullName(authors[it]) == name } ?: continue
                claims += Claim(email, owner, "${anchor.visibleText().trim()} <${anchor.attr("href")}>")
            }
        }
        return originalText.isNotBlank() to results(found.toList(), claims, authors)
    }

    private fun mailtoMailbox(href: String, blacklist: List<String>): String? {
        if (!href.startsWith("mailto:", ignoreCase = true)) return null
        val encoded = href.substring(7).substringBefore('?')
        val address = try {
            URLDecoder.decode(encoded.replace("+", "%2B"), Charsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (!mailbox.matches(address)) return null
        val normalized = address.lowercase(Locale.ROOT)
        return normalized.takeIf { emails.extract(address, blacklist).singleOrNull() == it }
    }

    internal data class Claim(val email: String, val authorIndex: Int, val entry: String)

    internal fun textClaims(originalText: String, authors: List<PaperAuthor>, continuations: Boolean = true): List<Claim> {
        if ('\u000c' in originalText) return originalText.split('\u000c').flatMap { textClaims(it, authors, continuations) }
        val originalLines = originalText.replace('\u00a0', ' ').split(Regex("\\r?\\n"))
            .map { it.trim() }.takeWhile { !it.matches(Regex("(?i)(?:references|bibliography)\\s*[:.]?")) }
        val entries = mutableListOf<Pair<String, String>>()
        var line = 0
        while (line < originalLines.size) {
            if (continuations && line + 1 < originalLines.size && '\u000c' !in originalLines[line] &&
                '\u000c' !in originalLines[line + 1]) {
                val originalPair = originalLines[line] + "\n" + originalLines[line + 1]
                val normalizedPair = emails.normalizeContactText(originalPair)
                if (!normalizedPair.contains('\n')) {
                    entries += originalPair to normalizedPair.trim()
                    line += 2
                    continue
                }
            }
            entries += originalLines[line] to emails.normalizeContactText(originalLines[line]).trim()
            line++
        }
        // Only a named, explicitly labelled contact block may cross text lines.
        // PDF callers supply already bounded physical blocks instead.
        if (continuations) for (i in entries.indices.toList()) {
            if (!prefix.containsMatchIn(entries[i].second)) continue
            val block = mutableListOf(entries[i])
            for (next in entries.drop(i + 1).take(5)) {
                if (next.second.isBlank() || '\u000c' in next.first || sectionBoundary.containsMatchIn(next.second) ||
                    prefix.containsMatchIn(next.second) ||
                    authors.any { fullName(it).isNotEmpty() && normalize(next.second).startsWith(fullName(it)) }) break
                block += next
                if (mailbox.containsMatchIn(next.second)) break
            }
            if (block.size > 1) entries += block.joinToString("\n") { it.first } to block.joinToString(" ") { it.second }
        }
        val claims = mutableListOf<Claim>()
        for ((originalEntry, entry) in entries) {
            if (entry.length > 1000 || shared.containsMatchIn(entry)) continue
            // A trailing parenthesized owner belongs only to the immediately preceding mailbox.
            // Initials are admitted only here, never from the metadata roster alone.
            val comparable = contactNameText(entry)
            for (label in labelledMailbox.findAll(comparable)) {
                val ownerLabel = label.groupValues[2]
                val owner = authors.indices.singleOrNull { matchesExplicitLabel(ownerLabel, authors[it]) } ?: continue
                val email = emails.extract(label.groupValues[1]).singleOrNull() ?: continue
                claims += Claim(email, owner, originalEntry)
            }
            val body = prefix.replaceFirst(comparable, "").trim()
            for (author in authors) {
                if (author.givenNames.isNullOrBlank() || author.familyNames.isNullOrBlank()) continue
                val namePattern = contactNameText(author.givenNames + " " + author.familyNames).trim().split(Regex("\\s+"))
                    .joinToString("\\s+") { Regex.escape(it) }
                val owner = authors.indices.singleOrNull { fullName(authors[it]) == fullName(author) } ?: continue
                // Full name + a closed mailbox delimiter is a self-contained contact, even in a biography.
                val wrapped = Regex("(?<![\\p{L}])$namePattern\\s*(?:<($mailbox)>|\\(($mailbox)\\))", RegexOption.IGNORE_CASE)
                for (match in wrapped.findAll(comparable)) {
                    val preceding = comparable.substring(0, match.range.first).substringAfterLast('>')
                        .substringAfterLast(')').substringAfterLast(';')
                    if (Regex("(?i)(?:\\band|&)\\s*$").containsMatchIn(preceding) ||
                        authors.any { other -> fullName(other).isNotEmpty() && normalize(preceding).contains(fullName(other)) }) continue
                    val email = emails.extract(match.value).singleOrNull() ?: continue
                    claims += Claim(email, owner, originalEntry)
                }
                val name = Regex("^$namePattern(?=\\s|:|,|<|\\(|\\[|–|—|$)", RegexOption.IGNORE_CASE).find(body) ?: continue
                val tail = body.substring(name.range.last + 1)
                val emailLabel = emailLine.find(tail)
                if (prefix.containsMatchIn(comparable) && tail.trimStart().startsWith(",") &&
                    emailLabel != null && affiliation.containsMatchIn(tail.substring(0, emailLabel.range.first))) {
                    val beforeEmail = tail.substring(0, emailLabel.range.first)
                    if (authors.indices.none { it != owner && fullName(authors[it]).isNotEmpty() &&
                            normalize(beforeEmail).contains(fullName(authors[it])) } &&
                        !Regex("(?i)\\b(?:is|are)\\s+with\\b").containsMatchIn(beforeEmail)) {
                        val addressField = tail.substring(emailLabel.range.last + 1).trim()
                        if (mailbox.replace(addressField, "").matches(Regex("[\\s,;.]*")))
                            emails.extract(addressField).forEach { claims += Claim(it, owner, originalEntry) }
                    }
                }
                val delimiter = separator.find(tail) ?: continue
                val addressField = tail.substring(delimiter.range.last + 1)
                // Reject prose, other names and shared brace lists. No positional matching.
                if (mailbox.findAll(addressField).none()) continue
                val residue = mailbox.replace(addressField, "").replace(Regex("[\\s,;<>()\\[\\].]+"), "")
                if (residue.isNotEmpty()) continue
                for (email in emails.extract(addressField)) claims += Claim(email, owner, originalEntry)
            }
        }
        return claims
    }

    private fun matchesExplicitLabel(label: String, author: PaperAuthor): Boolean {
        if (author.givenNames.isNullOrBlank() || author.familyNames.isNullOrBlank()) return false
        if (normalize(label) == fullName(author)) return true
        val given = contactNameText(author.givenNames).split(Regex("[\\s.-]+")).filter { it.isNotBlank() }
        val family = contactNameText(author.familyNames).split(Regex("[\\s.-]+")).filter { it.isNotBlank() }
        val givenInitials = given.joinToString("\\s*") { Regex.escape(it.first().toString()) + "\\.?" }
        val familyName = family.joinToString("\\s+") { Regex.escape(it) }
        if (Regex("$givenInitials\\s*$familyName", RegexOption.IGNORE_CASE).matches(contactNameText(label).trim())) return true
        val allInitials = (given + family).joinToString("\\s*") { Regex.escape(it.first().toString()) + "\\.?" }
        return Regex(allInitials, RegexOption.IGNORE_CASE).matches(contactNameText(label).trim())
    }

    private fun results(found: List<String>, claims: List<Claim>, authors: List<PaperAuthor>): List<AuthorEmail> =
        found.map { email ->
            val matches = claims.filter { it.email == email }
            val owner = matches.map { it.authorIndex }.distinct().singleOrNull()
            if (owner == null) AuthorEmail(email, null, null, false, null, null)
            else {
                val author = authors[owner]
                AuthorEmail(email, author.givenNames, author.familyNames, author.isCorresponding,
                    author.affiliation, author.orcidId, author.institutionType, author.openAlexAuthorId,
                    "SOURCE_SHA256:" + DiscoveryIdentity.hash(matches.first().entry),
                    // I-1/I-2：机构的展示字段只随唯一作者证据一起传播；邮箱线索分支永远不带机构。
                    author.institutionName, author.institutionCountry, author.institutionSource)
            }
        }

    private fun fullName(author: PaperAuthor): String =
        if (author.givenNames.isNullOrBlank() || author.familyNames.isNullOrBlank()) ""
        else normalize("${author.givenNames} ${author.familyNames}")

    private fun contactNameText(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .replace(Regex("[\\u2010\\u2011]"), "-").replace(Regex("\\s*-\\s*"), "-")

    private fun normalize(value: String) = contactNameText(value)
        .trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private class Node(val tag: String, val attributes: Map<String, String> = emptyMap()) {
        val parts = mutableListOf<Any>()
        fun attr(name: String) = attributes[name].orEmpty()
        fun hasClass(name: String) = attr("class").split(Regex("\\s+")).contains(name)
        fun isAuthor() = hasClass("ltx_role_author") ||
            (attr("itemtype").endsWith("/Person") && attr("itemprop") == "author")
        fun descendants(): Sequence<Node> = sequence {
            yield(this@Node)
            for (part in parts) if (part is Node) yieldAll(part.descendants())
        }
        fun visibleText(): String {
            if (tag in setOf("script", "style", "head", "sup")) return ""
            val body = parts.joinToString(" ") { if (it is Node) it.visibleText() else it.toString() }
            return if (tag in setOf("p", "div", "li", "tr", "td", "br") || isAuthor()) "\n$body\n" else body
        }
    }

    private fun parseHtml(html: String): Node {
        val root = Node("root")
        val stack = mutableListOf(root)
        ParserDelegator().parse(StringReader(html), object : HTMLEditorKit.ParserCallback() {
            private fun node(tag: HTML.Tag, attrs: MutableAttributeSet): Node {
                val attributes = mutableMapOf<String, String>()
                val keys = attrs.attributeNames
                while (keys.hasMoreElements()) {
                    val key = keys.nextElement()
                    attributes[key.toString()] = attrs.getAttribute(key).toString()
                }
                return Node(tag.toString(), attributes)
            }
            override fun handleStartTag(tag: HTML.Tag, attrs: MutableAttributeSet, pos: Int) {
                val child = node(tag, attrs)
                stack.last().parts += child
                stack += child
            }
            override fun handleEndTag(tag: HTML.Tag, pos: Int) {
                val index = stack.indexOfLast { it.tag == tag.toString() }
                if (index > 0) while (stack.size > index) stack.removeAt(stack.lastIndex)
            }
            override fun handleSimpleTag(tag: HTML.Tag, attrs: MutableAttributeSet, pos: Int) {
                stack.last().parts += node(tag, attrs)
            }
            override fun handleText(data: CharArray, pos: Int) { stack.last().parts += String(data) }
        }, true)
        return root
    }
}
