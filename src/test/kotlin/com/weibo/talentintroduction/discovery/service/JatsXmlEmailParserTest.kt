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
}
