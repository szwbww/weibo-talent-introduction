package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

object JatsXmlEmailParser {

    private val EMAIL_REGEX = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
    private val CORRESPONDENCE_PATTERN = Regex(
        "correspondence|corresponding author",
        RegexOption.IGNORE_CASE
    )

    fun parse(xml: String): List<AuthorEmail> {
        return parse(xml.toByteArray(Charsets.UTF_8))
    }

    fun parse(bytes: ByteArray): List<AuthorEmail> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(ByteArrayInputStream(bytes))

        val results = mutableListOf<AuthorEmail>()

        results += parseContribEmails(doc)
        results += parseAuthorNotesCorresp(doc)
        results += parseCorrespTextEmails(doc)
        results += parseXrefCorrespondence(doc)

        return mergeResults(results)
    }

    private fun isContribAuthor(contrib: Element): Boolean {
        val contribType = contrib.getAttribute("contrib-type")
        if (contribType == "author") return true
        if (contribType.isNotBlank()) return false

        val parent = contrib.parentNode as? Element
        if (parent != null && parent.tagName == "contrib-group" &&
            parent.getAttribute("content-type") == "author"
        ) return true

        if (contrib.getElementsByTagName("name").length > 0) return true

        return false
    }

    private fun parseContribEmails(doc: Document): List<AuthorEmail> {
        val results = mutableListOf<AuthorEmail>()
        val contribs = doc.getElementsByTagName("contrib")

        for (i in 0 until contribs.length) {
            val contrib = contribs.item(i) as? Element ?: continue
            if (!isContribAuthor(contrib)) continue

            val emailElements = contrib.getElementsByTagName("email")
            if (emailElements.length == 0) continue

            val nameElement = contrib.getElementsByTagName("name").item(0) as? Element
            val surname = nameElement?.getElementsByTagName("surname")?.item(0)?.textContent?.trim()
            val givenNames = nameElement?.getElementsByTagName("given-names")?.item(0)?.textContent?.trim()

            val isCorresponding = contrib.getAttribute("corresp") == "yes"

            val affiliation = resolveAffiliation(doc, contrib)

            val orcidId = extractOrcid(contrib)

            for (j in 0 until emailElements.length) {
                val email = emailElements.item(j)?.textContent?.trim() ?: continue
                if (!EMAIL_REGEX.matches(email)) continue

                results += AuthorEmail(
                    email = email,
                    givenNames = givenNames,
                    familyNames = surname,
                    isCorresponding = isCorresponding,
                    affiliation = affiliation,
                    orcidId = orcidId
                )
            }
        }

        return results
    }

    private fun parseAuthorNotesCorresp(doc: Document): List<AuthorEmail> {
        val results = mutableListOf<AuthorEmail>()
        val authorNotes = doc.getElementsByTagName("author-notes")

        for (i in 0 until authorNotes.length) {
            val notes = authorNotes.item(i) as? Element ?: continue

            val correspList = notes.getElementsByTagName("corresp")
            for (j in 0 until correspList.length) {
                val emailElements = correspList.item(j).childNodes
                for (k in 0 until emailElements.length) {
                    val node = emailElements.item(k)
                    if (node.nodeName == "email") {
                        val email = node.textContent?.trim() ?: continue
                        if (!EMAIL_REGEX.matches(email)) continue
                        results += AuthorEmail(
                            email = email,
                            givenNames = null,
                            familyNames = null,
                            isCorresponding = true,
                            affiliation = null,
                            orcidId = null
                        )
                    }
                }
            }
        }

        return results
    }

    private fun parseCorrespTextEmails(doc: Document): List<AuthorEmail> {
        val results = mutableListOf<AuthorEmail>()

        val authorNotes = doc.getElementsByTagName("author-notes")
        for (i in 0 until authorNotes.length) {
            val notes = authorNotes.item(i) as? Element ?: continue

            val correspElements = notes.getElementsByTagName("corresp")
            for (j in 0 until correspElements.length) {
                val text = correspElements.item(j)?.textContent ?: continue
                val emails = EMAIL_REGEX.findAll(text)
                for (match in emails) {
                    results += AuthorEmail(
                        email = match.value,
                        givenNames = null,
                        familyNames = null,
                        isCorresponding = true,
                        affiliation = null,
                        orcidId = null
                    )
                }
            }

            val fnElements = notes.getElementsByTagName("fn")
            for (j in 0 until fnElements.length) {
                val fn = fnElements.item(j) as? Element ?: continue
                val text = fn.textContent ?: continue
                if (!CORRESPONDENCE_PATTERN.containsMatchIn(text)) continue
                val emails = EMAIL_REGEX.findAll(text)
                for (match in emails) {
                    results += AuthorEmail(
                        email = match.value,
                        givenNames = null,
                        familyNames = null,
                        isCorresponding = true,
                        affiliation = null,
                        orcidId = null
                    )
                }
            }

            val pElements = notes.getElementsByTagName("p")
            for (j in 0 until pElements.length) {
                val p = pElements.item(j) as? Element ?: continue
                val text = p.textContent ?: continue
                if (!CORRESPONDENCE_PATTERN.containsMatchIn(text)) continue
                val emails = EMAIL_REGEX.findAll(text)
                for (match in emails) {
                    results += AuthorEmail(
                        email = match.value,
                        givenNames = null,
                        familyNames = null,
                        isCorresponding = true,
                        affiliation = null,
                        orcidId = null
                    )
                }
            }
        }

        return results
    }

    private fun parseXrefCorrespondence(doc: Document): List<AuthorEmail> {
        val results = mutableListOf<AuthorEmail>()

        val notesIndex = mutableMapOf<String, Element>()
        val authorNotes = doc.getElementsByTagName("author-notes")
        for (i in 0 until authorNotes.length) {
            val notes = authorNotes.item(i) as? Element ?: continue
            val children = notes.childNodes
            for (j in 0 until children.length) {
                val child = children.item(j) as? Element ?: continue
                val id = child.getAttribute("id")
                if (id.isNotBlank()) notesIndex[id] = child
            }
        }

        val contribs = doc.getElementsByTagName("contrib")
        for (i in 0 until contribs.length) {
            val contrib = contribs.item(i) as? Element ?: continue
            if (!isContribAuthor(contrib)) continue

            val nameElement = contrib.getElementsByTagName("name").item(0) as? Element
            val surname = nameElement?.getElementsByTagName("surname")?.item(0)?.textContent?.trim()
            val givenNames = nameElement?.getElementsByTagName("given-names")?.item(0)?.textContent?.trim()
            val affiliation = resolveAffiliation(doc, contrib)
            val orcidId = extractOrcid(contrib)

            val xrefs = contrib.getElementsByTagName("xref")
            for (j in 0 until xrefs.length) {
                val xref = xrefs.item(j) as? Element ?: continue
                val refType = xref.getAttribute("ref-type")
                if (refType != "corresp" && refType != "author-notes") continue
                val rid = xref.getAttribute("rid")
                if (rid.isBlank()) continue

                val target = notesIndex[rid] ?: doc.getElementById(rid) ?: continue

                val emailElements = target.getElementsByTagName("email")
                for (k in 0 until emailElements.length) {
                    val email = emailElements.item(k)?.textContent?.trim() ?: continue
                    if (!EMAIL_REGEX.matches(email)) continue

                    results += AuthorEmail(
                        email = email,
                        givenNames = givenNames,
                        familyNames = surname,
                        isCorresponding = true,
                        affiliation = affiliation,
                        orcidId = orcidId
                    )
                }

                if (emailElements.length == 0) {
                    val text = target.textContent ?: continue
                    val emails = EMAIL_REGEX.findAll(text)
                    for (match in emails) {
                        results += AuthorEmail(
                            email = match.value,
                            givenNames = givenNames,
                            familyNames = surname,
                            isCorresponding = true,
                            affiliation = affiliation,
                            orcidId = orcidId
                        )
                    }
                }
            }
        }

        return results
    }

    private fun resolveAffiliation(doc: Document, contrib: Element): String? {
        val directAff = contrib.getElementsByTagName("aff").item(0)?.textContent?.trim()
        if (!directAff.isNullOrBlank()) return directAff

        val xrefs = contrib.getElementsByTagName("xref")
        for (j in 0 until xrefs.length) {
            val xref = xrefs.item(j) as? Element ?: continue
            if (xref.getAttribute("ref-type") != "aff") continue
            val rid = xref.getAttribute("rid")
            if (rid.isBlank()) continue

            val allAffs = doc.getElementsByTagName("aff")
            for (k in 0 until allAffs.length) {
                val aff = allAffs.item(k) as? Element ?: continue
                if (aff.getAttribute("id") == rid) {
                    return aff.textContent?.trim()
                }
            }
        }

        return null
    }

    private fun extractOrcid(contrib: Element): String? {
        val contribIds = contrib.getElementsByTagName("contrib-id")
        for (i in 0 until contribIds.length) {
            val contribId = contribIds.item(i) as? Element ?: continue
            if (contribId.getAttribute("contrib-id-type") == "orcid") {
                val raw = contribId.textContent?.trim() ?: continue
                return raw.substringAfterLast("/").takeIf {
                    it.matches(Regex("\\d{4}-\\d{4}-\\d{4}-\\d{3}[0-9X]"))
                }
            }
        }
        return null
    }

    private fun mergeResults(results: List<AuthorEmail>): List<AuthorEmail> {
        return results.groupBy { it.email.lowercase(Locale.ROOT) }.map { (_, group) ->
            AuthorEmail(
                email = group.first().email,
                givenNames = group.mapNotNull { it.givenNames }.firstOrNull(),
                familyNames = group.mapNotNull { it.familyNames }.firstOrNull(),
                isCorresponding = group.any { it.isCorresponding },
                affiliation = group.mapNotNull { it.affiliation }.firstOrNull(),
                orcidId = group.mapNotNull { it.orcidId }.firstOrNull()
            )
        }
    }
}
