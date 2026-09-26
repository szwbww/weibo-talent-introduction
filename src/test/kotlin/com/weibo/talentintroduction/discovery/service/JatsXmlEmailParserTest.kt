package com.weibo.talentintroduction.discovery.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class JatsXmlEmailParserTest {

    @Test
    fun `parse email from contrib with corresp=yes`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article>
              <front>
                <article-meta>
                  <contrib-group>
                    <contrib contrib-type="author" corresp="yes">
                      <name><surname>Smith</surname><given-names>John</given-names></name>
                      <email>john.smith@oxford.ac.uk</email>
                    </contrib>
                  </contrib-group>
                </article-meta>
              </front>
            </article>
        """.trimIndent()

        val results = JatsXmlEmailParser.parse(xml)
        assertEquals(1, results.size)
        assertEquals("john.smith@oxford.ac.uk", results[0].email)
        assertEquals("John", results[0].givenNames)
        assertTrue(results[0].identityEvidence!!.startsWith("JATS_SHA256:"))
        assertEquals("Smith", results[0].familyNames)
        assertTrue(results[0].isCorresponding)
    }

    @Test
    fun `parse email from author-notes corresp`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article>
              <front>
                <article-meta>
                  <contrib-group>
                    <contrib contrib-type="author">
                      <name><surname>Jones</surname><given-names>Alice</given-names></name>
                    </contrib>
                  </contrib-group>
                  <author-notes>
                    <corresp id="cor1">*Corresponding author: <email>alice.jones@mit.edu</email></corresp>
                  </author-notes>
                </article-meta>
              </front>
            </article>
        """.trimIndent()

        val results = JatsXmlEmailParser.parse(xml)
        assertEquals(1, results.size)
        assertEquals("alice.jones@mit.edu", results[0].email)
        assertTrue(results[0].isCorresponding)
    }

    @Test
    fun `parse email from corresp plain text via regex`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article>
              <front>
                <article-meta>
                  <author-notes>
                    <corresp>Correspondence to: Prof. X, email: prof.x@university.edu, Tel: +1-234</corresp>
                  </author-notes>
                </article-meta>
              </front>
            </article>
        """.trimIndent()

        val results = JatsXmlEmailParser.parse(xml)
        assertEquals(1, results.size)
        assertEquals("prof.x@university.edu", results[0].email)
    }

    @Test
    fun `deduplicates emails across strategies and merges fields`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article>
              <front>
                <article-meta>
                  <contrib-group>
                    <contrib contrib-type="author" corresp="yes">
                      <name><surname>Lee</surname><given-names>Bob</given-names></name>
                      <email>bob@example.com</email>
                    </contrib>
                  </contrib-group>
                  <author-notes>
                    <corresp><email>bob@example.com</email></corresp>
                  </author-notes>
                </article-meta>
              </front>
            </article>
        """.trimIndent()

        val results = JatsXmlEmailParser.parse(xml)
        assertEquals(1, results.size)
        assertEquals("Bob", results[0].givenNames)
        assertEquals("Lee", results[0].familyNames)
        assertTrue(results[0].isCorresponding)
    }

    @Test
    fun `extracts ORCID from contrib-id`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article>
              <front>
                <article-meta>
                  <contrib-group>
                    <contrib contrib-type="author" corresp="yes">
                      <contrib-id contrib-id-type="orcid">https://orcid.org/0000-0001-2345-6789</contrib-id>
                      <name><surname>Wang</surname><given-names>Li</given-names></name>
                      <email>li.wang@example.com</email>
                    </contrib>
                  </contrib-group>
                </article-meta>
              </front>
            </article>
        """.trimIndent()

        val results = JatsXmlEmailParser.parse(xml)
        assertEquals(1, results.size)
        assertEquals("0000-0001-2345-6789", results[0].orcidId)
    }

    @Test
    fun `resolves affiliation via xref rid`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article>
              <front>
                <article-meta>
                  <contrib-group>
                    <contrib contrib-type="author" corresp="yes">
                      <name><surname>Kim</surname><given-names>Su</given-names></name>
                      <email>su.kim@kaist.ac.kr</email>
                      <xref ref-type="aff" rid="aff1"/>
                    </contrib>
                  </contrib-group>
                  <aff id="aff1">KAIST, Department of Computer Science, Daejeon, South Korea</aff>
                </article-meta>
              </front>
            </article>
        """.trimIndent()

        val results = JatsXmlEmailParser.parse(xml)
        assertEquals(1, results.size)
        assertTrue(results[0].affiliation?.contains("KAIST") == true)
    }

    @Test
    fun `returns empty for xml without emails`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article>
              <front>
                <article-meta>
                  <contrib-group>
                    <contrib contrib-type="author">
                      <name><surname>No</surname><given-names>Email</given-names></name>
                    </contrib>
                  </contrib-group>
                </article-meta>
              </front>
            </article>
        """.trimIndent()

        val results = JatsXmlEmailParser.parse(xml)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `handles malformed xml gracefully`() {
        assertThrows(Exception::class.java) {
            JatsXmlEmailParser.parse("not xml at all")
        }
    }

    @Test
    fun `recognizes author without contrib-type via parent group content-type`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article>
              <front>
                <article-meta>
                  <contrib-group content-type="author">
                    <contrib>
                      <name><surname>Doe</surname><given-names>Jane</given-names></name>
                      <email>jane.doe@university.edu</email>
                    </contrib>
                  </contrib-group>
                </article-meta>
              </front>
            </article>
        """.trimIndent()

        val results = JatsXmlEmailParser.parse(xml)
        assertEquals(1, results.size)
        assertEquals("jane.doe@university.edu", results[0].email)
        assertEquals("Jane", results[0].givenNames)
        assertEquals("Doe", results[0].familyNames)
    }

    @Test
    fun `recognizes author with name element but no contrib-type`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article>
              <front>
                <article-meta>
                  <contrib-group>
                    <contrib>
                      <name><surname>Brown</surname><given-names>Charlie</given-names></name>
                      <email>charlie.brown@lab.gov</email>
                    </contrib>
                  </contrib-group>
                </article-meta>
              </front>
            </article>
        """.trimIndent()

        val results = JatsXmlEmailParser.parse(xml)
        assertEquals(1, results.size)
        assertEquals("charlie.brown@lab.gov", results[0].email)
    }

    @Test
    fun `excludes non-author contrib-type explicitly`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article>
              <front>
                <article-meta>
                  <contrib-group>
                    <contrib contrib-type="editor">
                      <name><surname>NotAuthor</surname><given-names>X</given-names></name>
                      <email>editor@journal.com</email>
                    </contrib>
                    <contrib contrib-type="author">
                      <name><surname>Real</surname><given-names>Author</given-names></name>
                      <email>real.author@lab.gov</email>
                    </contrib>
                  </contrib-group>
                </article-meta>
              </front>
            </article>
        """.trimIndent()

        val results = JatsXmlEmailParser.parse(xml)
        assertEquals(1, results.size)
        assertEquals("real.author@lab.gov", results[0].email)
    }

    @Test
    fun `non-correspondence fn email is not extracted`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article>
              <front>
                <article-meta>
                  <author-notes>
                    <fn id="fn001">
                      <p>Funding note</p>
                      <email>funding@agency.org</email>
                    </fn>
                  </author-notes>
                </article-meta>
              </front>
            </article>
        """.trimIndent()

        val results = JatsXmlEmailParser.parse(xml)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `fn with correspondence text is extracted`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article>
              <front>
                <article-meta>
                  <author-notes>
                    <fn id="fn001">
                      <p>*Correspondence: Dr. Test
                        <email>dr.test@university.edu</email>
                      </p>
                    </fn>
                  </author-notes>
                </article-meta>
              </front>
            </article>
        """.trimIndent()

        val results = JatsXmlEmailParser.parse(xml)
        assertEquals(1, results.size)
        assertEquals("dr.test@university.edu", results[0].email)
        assertTrue(results[0].isCorresponding)
    }

    @Test
    fun `parses email from author-notes p with correspondence text`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article>
              <front>
                <article-meta>
                  <author-notes>
                    <p>*Corresponding author: <email>contact@institution.org</email></p>
                  </author-notes>
                </article-meta>
              </front>
            </article>
        """.trimIndent()

        val results = JatsXmlEmailParser.parse(xml)
        assertEquals(1, results.size)
        assertEquals("contact@institution.org", results[0].email)
        assertTrue(results[0].isCorresponding)
    }

    @Test
    fun `p without correspondence text is not extracted`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article>
              <front>
                <article-meta>
                  <author-notes>
                    <p>Conflict of interest: none declared</p>
                    <p><email>editor@journal.com</email></p>
                  </author-notes>
                </article-meta>
              </front>
            </article>
        """.trimIndent()

        val results = JatsXmlEmailParser.parse(xml)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `resolves xref corresp back-link to fn with author details`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article>
              <front>
                <article-meta>
                  <contrib-group content-type="author">
                    <contrib>
                      <name><surname>Hudecek</surname><given-names>M</given-names></name>
                      <xref ref-type="corresp" rid="fn001"/>
                    </contrib>
                  </contrib-group>
                  <author-notes>
                    <fn id="fn001">
                      <p>*Correspondence: M Hudecek,
                        <email>hudecek_m@ukw.de</email>
                      </p>
                    </fn>
                  </author-notes>
                </article-meta>
              </front>
            </article>
        """.trimIndent()

        val results = JatsXmlEmailParser.parse(xml)
        assertEquals(1, results.size)
        assertEquals("hudecek_m@ukw.de", results[0].email)
        assertEquals("Hudecek", results[0].familyNames)
        assertEquals("M", results[0].givenNames)
        assertTrue(results[0].isCorresponding)
    }

    @Test
    fun `one author with multiple emails returns all`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article>
              <front>
                <article-meta>
                  <contrib-group>
                    <contrib contrib-type="author" corresp="yes">
                      <name><surname>Test</surname><given-names>Multi</given-names></name>
                      <email>multi.primary@univ.edu</email>
                      <email>multi.secondary@gmail.com</email>
                    </contrib>
                  </contrib-group>
                </article-meta>
              </front>
            </article>
        """.trimIndent()

        val results = JatsXmlEmailParser.parse(xml)
        assertEquals(2, results.size)
        val emails = results.map { it.email }.toSet()
        assertTrue(emails.contains("multi.primary@univ.edu"))
        assertTrue(emails.contains("multi.secondary@gmail.com"))
        results.forEach {
            assertEquals("Multi", it.givenNames)
            assertEquals("Test", it.familyNames)
        }
    }

    @Test
    fun `parses email from XML with DOCTYPE declaration`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE article PUBLIC "-//NLM//DTD JATS (Z39.96) Journal Publishing DTD v1.2 20190208//EN" "https://jats.nlm.nih.gov/publishing/1.2/JATS-journalpublishing1.dtd">
            <article>
              <front>
                <article-meta>
                  <contrib-group>
                    <contrib contrib-type="author" corresp="yes">
                      <name><surname>Doe</surname><given-names>Jane</given-names></name>
                      <email>jane.doe@institute.org</email>
                    </contrib>
                  </contrib-group>
                </article-meta>
              </front>
            </article>
        """.trimIndent()

        val results = JatsXmlEmailParser.parse(xml)
        assertEquals(1, results.size)
        assertEquals("jane.doe@institute.org", results[0].email)
    }

    @Test
    fun `external entities are not expanded`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE article [
              <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <article>
              <front>
                <article-meta>
                  <contrib-group>
                    <contrib contrib-type="author" corresp="yes">
                      <name><surname>Test</surname><given-names>XXE</given-names></name>
                      <email>safe@example.com</email>
                    </contrib>
                  </contrib-group>
                </article-meta>
              </front>
            </article>
        """.trimIndent()

        val results = JatsXmlEmailParser.parse(xml)
        assertEquals(1, results.size)
        assertEquals("safe@example.com", results[0].email)
        assertFalse(results[0].email.contains("root"))
    }

    @Test
    fun `parameter entities are not expanded`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE article [
              <!ENTITY % pe SYSTEM "file:///etc/passwd">
              %pe;
            ]>
            <article>
              <front>
                <article-meta>
                  <contrib-group>
                    <contrib contrib-type="author" corresp="yes">
                      <name><surname>Test</surname><given-names>PE</given-names></name>
                      <email>safe2@example.com</email>
                    </contrib>
                  </contrib-group>
                </article-meta>
              </front>
            </article>
        """.trimIndent()

        val results = JatsXmlEmailParser.parse(xml)
        assertEquals(1, results.size)
        assertEquals("safe2@example.com", results[0].email)
    }
    private fun sharedXml(names: List<Pair<String, String>>, text: String): String =
        "<article><front><article-meta><contrib-group>" + names.joinToString("") { (given, family) ->
            "<contrib contrib-type=\"author\"><name><given-names>$given</given-names><surname>$family</surname></name>" +
                "<xref ref-type=\"corresp\" rid=\"c1\"/></contrib>"
        } + "</contrib-group><author-notes><corresp id=\"c1\">$text</corresp></author-notes></article-meta></front></article>"

    @Test
    fun `shared note without explicit ownership must not assign first author`() {
        val result = JatsXmlEmailParser.parse(sharedXml(listOf("John" to "Smith", "Jane" to "Doe"),
            "Correspondence: one@example.org, two@example.org"))
        assertEquals(2, result.size)
        result.forEach { assertNull(it.identityEvidence); assertNull(it.givenNames); assertNull(it.familyNames); assertNull(it.orcidId); assertNull(it.affiliation) }
    }

    @Test
    fun `PMC13280751 postfix name belongs only to preceding email`() {
        val names = listOf("Shibao" to "Lu", "Jie" to "Lu", "Wei" to "Wang")
        val text = "Corresponding authors: Shibao Lu, Jie Lu, Wei Wang; E-mail: spinelu@163.com (Shibao Lu), imaginglu@hotmail.com (Jie Lu), wangwei37@buaa.edu.cn (Wei Wang)."
        for (order in listOf(names, names.reversed())) {
            val result = JatsXmlEmailParser.parse(sharedXml(order, text)).associateBy { it.email }
            assertEquals("Shibao", result.getValue("spinelu@163.com").givenNames)
            assertEquals("Jie", result.getValue("imaginglu@hotmail.com").givenNames)
            assertEquals("Wei", result.getValue("wangwei37@buaa.edu.cn").givenNames)
        }
    }

    @Test
    fun `PMC13196294 grouped emails and accents retain independent owners`() {
        val result = JatsXmlEmailParser.parse(sharedXml(listOf("Fuqiang" to "Gao", "Sébastien" to "Lustig", "Weiguo" to "Wang"),
            "Corresponding authors: gaofuqianghcl@163.com, gaofuqiang@bjmu.edu.cn (Fuqiang Gao); sebastien.lustig@gmail.com (Sébastien Lustig); jointwwg@163.com (Weiguo Wang)"))
        assertEquals(listOf("Fuqiang", "Fuqiang", "Sébastien", "Weiguo"), result.map { it.givenNames })
    }

    @Test
    fun `PMC13241006 explicit initials cover email list not other author`() {
        val result = JatsXmlEmailParser.parse(sharedXml(listOf("Sajjad" to "Abbasi", "Ali Akbar" to "Moosavi"),
            "E-mail: sajjad.abbasi@shirazu.ac.ir, sajjad.abbasi@cnrs.fr, sajjad.abbasi.h@gmail.com (SA); aamousavi@gmail.com, aamousavi@shirazu.ac.ir (AAM)"))
        assertEquals(listOf("Sajjad", "Sajjad", "Sajjad", "Ali Akbar", "Ali Akbar"), result.map { it.givenNames })
    }

    @Test
    fun `same name author nodes and duplicate initials remain ambiguous`() {
        for (names in listOf(listOf("John" to "Smith", "John" to "Smith"), listOf("John" to "Smith", "Jane" to "Smith"))) {
            val result = JatsXmlEmailParser.parse(sharedXml(names, "contact@example.org (JS)"))
            assertNull(result.single().givenNames)
        }
    }

    @Test
    fun `exclusive reference cannot override a different named contact`() {
        val result = JatsXmlEmailParser.parse(sharedXml(listOf("John" to "Smith"), "Correspondence: Jane Doe, other@example.org"))
        assertNull(result.single().givenNames)
    }

    @Test
    fun `multiple rid tokens resolve and duplicate id cannot choose first`() {
        val xml = sharedXml(listOf("John" to "Smith"), "contact@example.org").replace("rid=\"c1\"", "rid=\"missing c1\"")
        assertEquals("John", JatsXmlEmailParser.parse(xml).single().givenNames)
        val duplicate = xml.replace("</author-notes>", "<corresp id=\"c1\">other@example.org</corresp></author-notes>")
        assertTrue(JatsXmlEmailParser.parse(duplicate).all { it.givenNames == null })
    }

    @Test
    fun `direct conflicting authors do not synthesize an identity`() {
        val xml = sharedXml(listOf("John" to "Smith", "Jane" to "Doe"), "shared@example.org")
            .replace("<xref ref-type=\"corresp\" rid=\"c1\"/>", "<email>shared@example.org</email>")
        assertNull(JatsXmlEmailParser.parse(xml).single().givenNames)
    }

    @Test
    fun `editor groups references and affiliation mail do not become author identity`() {
        val xml = """<article><front><article-meta>
          <contrib-group content-type="editor"><contrib><name><surname>Editor</surname></name><email>editor@example.org</email></contrib></contrib-group>
          <contrib-group><contrib contrib-type="author"><name><given-names>John</given-names><surname>Smith</surname></name><aff><email>office@example.org</email></aff><address><email>john@example.org</email></address></contrib></contrib-group>
          </article-meta></front><back><ref-list><contrib><name><surname>Other</surname></name><email>ref@example.org</email></contrib></ref-list></back></article>"""
        val result = JatsXmlEmailParser.parse(xml)
        assertEquals(listOf("john@example.org"), result.map { it.email })
        assertEquals("John", result.single().givenNames)
    }

    @Test
    fun `contradictory prefix and postfix author labels never select one owner`() {
        val result = JatsXmlEmailParser.parse(sharedXml(listOf("John" to "Smith", "Jane" to "Doe"),
            "John Smith: shared@example.org (Jane Doe)"))
        assertNull(result.single().givenNames)
        assertNull(result.single().identityEvidence)
    }

}
