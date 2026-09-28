package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.INSTITUTION_SOURCE_JATS
import com.weibo.talentintroduction.expert.domain.CountryContinentMapping
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.text.Normalizer
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Email ownership is resolved before projecting an author identity. A shared xref is not ownership. */
object JatsXmlEmailParser {
    private val EMAIL = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
    private val CORRESP = Regex("correspondence|corresponding author", RegexOption.IGNORE_CASE)
    private val LABEL = Regex("\\(([^()]*)\\)")
    private val WORD = Regex("[\\p{L}\\p{N}]+")
    private val CONTACT_WORDS = Regex("(?i)\\b(?:correspondence|corresponding\\s+authors?|e-?mails?(?:\\s+addresses?)?|and|or|to)\\b")
    /**
     * I-1：生产样本里 2,480 个 `<institution>` 中 2,262 个无 `content-type`、125 个 `department`、
     * 48 个 `dept`、5 个 `org-division` —— 只有这两类标签能证明组织级含义，其余一律不作为机构。
     */
    private val ORGANISATION_CONTENT_TYPES = setOf("university", "edu")

    private data class Author(val node: Element, val identity: AuthorEmail) {
        val name = listOfNotNull(identity.givenNames, identity.familyNames).joinToString(" ")
        val initials = WORD.findAll(name).map { it.value.first() }.joinToString("")
    }
    private data class Candidate(val email: String, val author: Author?, val corresponding: Boolean)

    fun parse(xml: String): List<AuthorEmail> = parse(xml.toByteArray(Charsets.UTF_8))

    fun parse(bytes: ByteArray): List<AuthorEmail> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val article = doc.documentElement.takeIf { it.tagName == "article" } ?: return emptyList()
        val meta = article.children("front").firstOrNull()?.children("article-meta")?.firstOrNull()
            ?: return emptyList()
        val ids = meta.descendants().filter { it.getAttribute("id").isNotBlank() }.groupBy { it.getAttribute("id") }
        val authors = meta.descendants().filter { it.tagName == "contrib" && isAuthor(it, meta) }.map { node ->
            val name = identityName(node)
            val given = name?.let(::givenNames)?.takeIf { it.isNotBlank() }
            val family = name?.children("surname")?.singleOrNull()?.textContent?.trim()?.takeIf { it.isNotBlank() }
            val orcids = node.children("contrib-id").filter { it.getAttribute("contrib-id-type") == "orcid" }
                .mapNotNull { it.textContent.trim().substringAfterLast('/').takeIf { id -> id.matches(Regex("\\d{4}-\\d{4}-\\d{4}-\\d{3}[0-9X]")) } }.distinct()
            val affs = node.children("aff") + node.children("xref").filter { it.getAttribute("ref-type") == "aff" }
                .flatMap { refs(it) }.mapNotNull { ids[it]?.singleOrNull()?.takeIf { n -> n.tagName == "aff" } }
            val relatedAffs = affs.distinct()
            val institution = structuredInstitution(relatedAffs)
            Author(node, AuthorEmail("", given, family, node.getAttribute("corresp") == "yes",
                relatedAffs.map { it.textContent.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("; ").takeIf { it.isNotBlank() },
                orcids.singleOrNull(),
                institutionName = institution?.name,
                institutionCountry = institution?.country,
                institutionSource = if (institution != null) INSTITUTION_SOURCE_JATS else null))
        }
        val candidates = mutableListOf<Candidate>()
        for (author in authors) {
            val direct = author.node.children("email") + author.node.children("address").flatMap { address ->
                address.descendants().filter { n -> n.tagName == "email" && n.ancestorsUntil(author.node).none { it.tagName in setOf("aff", "contrib") } }
            }
            for (node in direct) {
                val email = node.textContent.trim()
                if (EMAIL.matches(email)) candidates += Candidate(email, author.takeIf { it.name.isNotBlank() }, author.identity.isCorresponding)
            }
        }
        val claimants = mutableMapOf<Element, MutableList<Author>>()
        for (author in authors) {
            for (xref in author.node.children("xref").filter { it.getAttribute("ref-type") in setOf("corresp", "author-notes") }) {
                for (rid in refs(xref)) {
                    val target = ids[rid]?.singleOrNull() ?: continue
                    if (target.tagName !in setOf("corresp", "fn", "p")) continue
                    claimants.getOrPut(target) { mutableListOf() }.add(author)
                }
            }
        }
        val notes = meta.children("author-notes").flatMap { it.descendants() }.filter {
            it.tagName == "corresp" || (it.tagName in setOf("fn", "p") && CORRESP.containsMatchIn(it.textContent))
        }
        for (note in (notes + claimants.keys).distinct()) {
            val text = readableText(note)
            val emails = EMAIL.findAll(text).toList()
            val owners = claimants[note].orEmpty().distinct()
            val uniqueId = note.getAttribute("id").let { it.isBlank() || ids[it]?.size == 1 }
            val available = if (uniqueId) authors else emptyList()
            val assigned = Array(emails.size) { mutableSetOf<Author>() }
            val explicitlyLabelled = mutableSetOf<Int>()
            // Postfix labels can cover a list of emails, but never cross another label or semicolon.
            for (label in LABEL.findAll(text)) {
                val previousLabel = LABEL.findAll(text.substring(0, label.range.first)).lastOrNull()?.range?.last?.plus(1) ?: 0
                val start = maxOf(previousLabel, text.lastIndexOf(';', label.range.first).plus(1))
                var group = emails.indices.filter { emails[it].range.first >= start && emails[it].range.last < label.range.first }
                if (group.isEmpty()) continue
                val residual = EMAIL.replace(text.substring(start, label.range.first), "")
                if (!punctuationOnly(CONTACT_WORDS.replace(residual, ""))) group = listOf(group.last())
                explicitlyLabelled.addAll(group)
                val matches = available.filter { matchesLabel(label.groupValues[1], it) }
                for (i in group) assigned[i].addAll(matches)
            }
            for ((i, email) in emails.withIndex()) {
                val previousEnd = if (i == 0) 0 else emails[i - 1].range.last + 1
                var prefix = text.substring(previousEnd, email.range.first)
                // A preceding email's suffix is never a prefix for this email.
                prefix = prefix.replace(Regex("^\\s*\\([^()]*\\)"), "")
                prefix = CONTACT_WORDS.replace(prefix, "").trim { !it.isLetterOrDigit() }
                // Exact explicit name immediately before email; no nearby-name/substring guessing.
                val matches = available.filter { normalize(prefix) == normalize(it.name) && normalize(it.name).isNotEmpty() }
                assigned[i].addAll(matches)
                if (assigned[i].isEmpty() && i !in explicitlyLabelled && owners.size == 1 && uniqueId) {
                    val residual = CONTACT_WORDS.replace(EMAIL.replace(text, ""), "")
                    if (punctuationOnly(residual)) assigned[i].add(owners.single())
                }
                if (assigned[i].isEmpty()) candidates += Candidate(email.value, null, true)
                else assigned[i].forEach { candidates += Candidate(email.value, it, true) }
            }
        }
        val contributorSections = article.descendants().filter { section ->
            section.tagName == "sec" && section.getAttribute("sec-type") == "contrib-info" &&
                section.ancestorsUntil(article).none { it.tagName in setOf("sub-article", "ref-list") }
        }
        for (section in contributorSections) {
            for (paragraph in section.children("p")) {
                val emails = EMAIL.findAll(paragraph.textContent).toList()
                if (emails.size != 1) continue
                val nameText = paragraph.textContent.substring(0, emails.single().range.first)
                    .replace(Regex("(?i)\\b(?:e-?mail|email\\s+address)\\s*:\\s*"), "")
                    .trim().trimEnd(',', ';', ':', '.', ' ')
                val matches = authors.filter { normalize(it.name).isNotEmpty() && normalize(it.name) == normalize(nameText) }
                matches.singleOrNull()?.let { owner ->
                    candidates += Candidate(emails.single().value, owner, owner.identity.isCorresponding)
                }
            }
        }
        val evidenceHash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return candidates.groupBy { it.email.lowercase(Locale.ROOT) }.map { (_, group) ->
            val owners = group.mapNotNull { it.author }.distinctBy { it.node }
            val email = group.first().email
            val corresponding = group.any { it.corresponding }
            val owner = owners.singleOrNull()
            owner?.identity?.copy(email = email, isCorresponding = corresponding,
                identityEvidence = if (!owner.identity.givenNames.isNullOrBlank() && !owner.identity.familyNames.isNullOrBlank())
                    "JATS_SHA256:" + evidenceHash else null)
                ?: AuthorEmail(email, null, null, corresponding, null, null)
        }
    }

    private fun givenNames(name: Element): String? {
        val given = name.children("given-names").singleOrNull() ?: return null
        val text = given.textContent.trim().takeIf { it.isNotBlank() } ?: return null
        val initials = given.getAttribute("initials").filter { it.isLetter() }
        if (initials.isEmpty()) return text
        return text.split(Regex("\\s+")).mapIndexed { index, part ->
            if (part.length == 1 && part[0].isLetter() &&
                initials.getOrNull(index)?.equals(part[0], ignoreCase = true) == true
            ) "$part." else part
        }.joinToString(" ")
    }

    private fun isAuthor(node: Element, meta: Element): Boolean {
        val ancestors = node.ancestorsUntil(meta)
        if (ancestors.any { it.tagName in setOf("contrib", "ref", "ref-list", "sub-article") }) return false
        if (ancestors.filter { it.tagName == "contrib-group" }.any {
                it.getAttribute("content-type").let { type -> type.isNotBlank() && type != "author" }
            }) return false
        val type = node.getAttribute("contrib-type")
        return (type.isBlank() || type == "author") &&
            (node.children("name").size == 1 || node.children("name-alternatives").size == 1)
    }
    private fun identityName(node: Element): Element? {
        val direct = node.children("name")
        if (direct.size == 1) return direct.single().takeIf(::completeName)
        if (direct.isNotEmpty()) return null
        val alternatives = node.children("name-alternatives").singleOrNull() ?: return null
        val complete = alternatives.children("name").filter(::completeName)
        if (complete.size == 1) return complete.single()
        return complete.filter { it.getAttribute("xml:lang").equals("en", ignoreCase = true) }.singleOrNull()
    }
    private fun completeName(name: Element) =
        name.children("given-names").singleOrNull()?.textContent?.isNotBlank() == true &&
            name.children("surname").singleOrNull()?.textContent?.isNotBlank() == true

    private fun refs(xref: Element) = xref.getAttribute("rid").trim().split(Regex("\\s+")).filter { it.isNotBlank() }

    /** I-1：可证明的组织级机构 = 该 aff 里 `content-type=university|edu` 的唯一非空 `<institution>`。 */
    private data class StructuredInstitution(val name: String, val country: String?)

    /**
     * I-1/I-2：该作者关联的全部 `<aff>` 里唯一的结构机构。
     * 多个不同机构、没有任何 `university|edu` 标签、或机构名不唯一时一律 null（不挑第一个）。
     * 国家只取承载该机构名的同一个 `<aff>` 里唯一且地区表认得的 `<country>`。
     */
    private fun structuredInstitution(affs: List<Element>): StructuredInstitution? {
        val institutions = affs.flatMap { aff -> institutionNames(aff).map { it to aff } }
        val name = institutions.map { it.first }.distinct().singleOrNull() ?: return null
        val country = institutions.filter { it.first == name }
            .mapNotNull { (_, aff) -> affiliationCountry(aff) }
            .distinct().singleOrNull()
        return StructuredInstitution(name, country)
    }

    /** 脚注里的 `<institution>` 不是作者归属；无类型/院系类型的标签也不构成组织级含义。 */
    private fun institutionNames(aff: Element): List<String> = aff.descendants()
        .filter { element ->
            element.tagName == "institution" &&
                element.getAttribute("content-type").trim().lowercase(Locale.ROOT) in ORGANISATION_CONTENT_TYPES &&
                element.ancestorsUntil(aff).none { it.tagName == "fn" }
        }
        .map { it.textContent.trim() }
        .filter { it.isNotEmpty() }

    private fun affiliationCountry(aff: Element): String? = aff.descendants()
        .filter { it.tagName == "country" && it.ancestorsUntil(aff).none { ancestor -> ancestor.tagName == "fn" } }
        .map { it.textContent.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .singleOrNull()
        ?.takeIf { CountryContinentMapping.toRegion(it) != CountryContinentMapping.REGION_OTHER }

    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    private fun matchesLabel(label: String, author: Author): Boolean = normalize(label).let {
        it.isNotEmpty() && (it == normalize(author.name) || (it.length >= 2 && it == normalize(author.initials)))
    }
    private fun punctuationOnly(value: String) = value.none { it.isLetterOrDigit() }
    private fun Element.children(tag: String? = null): List<Element> = (0 until childNodes.length)
        .mapNotNull { childNodes.item(it) as? Element }.filter { tag == null || it.tagName == tag }
    private fun Element.descendants(): List<Element> = children().flatMap { listOf(it) + it.descendants() }
    private fun Element.ancestorsUntil(stop: Element): List<Element> {
        val result = mutableListOf<Element>()
        var parent = parentNode
        while (parent is Element && parent !== stop) { result += parent; parent = parent.parentNode }
        return result
    }
    private fun readableText(node: Node): String {
        if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) return node.nodeValue.orEmpty()
        val isTarget = node is Element && node.tagName in setOf("corresp", "fn")
        val text = (0 until node.childNodes.length).map { node.childNodes.item(it) }
            .filterNot { isTarget && it is Element && it.tagName == "label" &&
                it.textContent.trim().matches(Regex("[0-9]+")) }
            .joinToString("") { readableText(it) }
        return if (node.nodeName == "email") " $text " else text
    }
}
