package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import java.io.StringReader
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
    private val prefix = Regex("(?i)^(?:(?:corresponding author|correspondence|contact)\\s*:\\s*)?")
    private val separator = Regex("(?i)^\\s*(?::|[–—]|<|\\(|\\[|(?:e-?mail|email address)\\s*:)\\s*(?:e-?mail\\s*:\\s*)?")
    private val emailLine = Regex("(?i)^e-?mail\\s*:")
    private val shared = Regex("(?i)\\b(?:the authors|corresponding authors|equal contribution|all authors)\\b")

    fun resolveText(text: String, authors: List<PaperAuthor>, blacklist: List<String> = emptyList()): List<AuthorEmail> {
        val claims = textClaims(text, authors)
        return results(emails.extract(text, blacklist), claims, authors)
    }

    fun resolveHtml(html: String, authors: List<PaperAuthor>, blacklist: List<String> = emptyList()): List<AuthorEmail> {
        val root = parseHtml(html)
        val text = root.visibleText()
        val claims = textClaims(text, authors).toMutableList()
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
        return results(emails.extract(text, blacklist), claims, authors)
    }

    private data class Claim(val email: String, val authorIndex: Int, val entry: String)

    private fun textClaims(text: String, authors: List<PaperAuthor>): List<Claim> {
        val lines = text.replace('\u00a0', ' ').lines().map { it.trim() }
        val entries = lines.toMutableList()
        // Require an explicit contact label: an author list next to Email: is not ownership.
        for (i in 0 until lines.lastIndex) {
            if (prefix.find(lines[i])?.value?.isNotBlank() == true &&
                authors.any { fullName(it) == normalize(prefix.replaceFirst(lines[i], "")) } &&
                emailLine.containsMatchIn(lines[i + 1])) entries += lines[i] + ": " + lines[i + 1]
        }
        val claims = mutableListOf<Claim>()
        for (entry in entries) {
            if (entry.length > 1000 || shared.containsMatchIn(entry)) continue
            val body = prefix.replaceFirst(entry, "").trim()
            for ((index, author) in authors.withIndex()) {
                if (author.givenNames.isNullOrBlank() || author.familyNames.isNullOrBlank()) continue
                val namePattern = (author.givenNames + " " + author.familyNames).trim().split(Regex("\\s+"))
                    .joinToString("\\s+") { Regex.escape(it) }
                val name = Regex("^$namePattern(?=\\s|:|<|\\(|\\[|–|—|$)", RegexOption.IGNORE_CASE).find(body) ?: continue
                val tail = body.substring(name.range.last + 1)
                val delimiter = separator.find(tail) ?: continue
                val addressField = tail.substring(delimiter.range.last + 1)
                // Reject prose, other names and shared brace lists. No positional matching.
                if (mailbox.findAll(addressField).none()) continue
                val residue = mailbox.replace(addressField, "").replace(Regex("[\\s,;<>()\\[\\].]+"), "")
                if (residue.isNotEmpty()) continue
                val owner = authors.indices.singleOrNull { fullName(authors[it]) == fullName(author) } ?: continue
                for (email in emails.extract(addressField)) claims += Claim(email, owner, entry)
            }
        }
        return claims
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
                    "SOURCE_SHA256:" + DiscoveryIdentity.hash(matches.first().entry))
            }
        }

    private fun fullName(author: PaperAuthor): String =
        if (author.givenNames.isNullOrBlank() || author.familyNames.isNullOrBlank()) ""
        else normalize("${author.givenNames} ${author.familyNames}")

    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
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
