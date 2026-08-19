package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.service.MailMessageIdNormalizer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class AiReplyContextServiceTest {

    private val expertSearchService = Mockito.mock(ExpertSearchService::class.java)
    private val contextBuilder = AiReplyContextBuilder()
    private val service = AiReplyContextService(expertSearchService, contextBuilder)

    private fun contact(
        orcidId: String = "0000-0001-test",
        indexLevel: String = "APPLICATION"
    ) = ExpertContact(
        campaignId = 1L,
        orcidId = orcidId,
        expertEmail = "test@example.com",
        expertName = "Dr. Test",
        currentIndexLevel = indexLevel
    )

    private fun profileWith(
        orcidId: String = "0000-0001-test",
        researchFields: String? = null,
        keyword: String? = null,
        disciplineCategory: String? = null,
        recentWorkTitles: List<String>? = null
    ) = ExpertProfile(
        orcidId = orcidId,
        email = "test@example.com",
        givenNames = "Dr.",
        familyNames = "Test",
        employment = null,
        researchFields = researchFields,
        keyword = keyword,
        disciplineCategory = disciplineCategory,
        recentWorkTitles = recentWorkTitles,
        country = "US"
    )

    // Test 1: APPLICATION miss falls back to CANDIDATE
    @Test
    fun `APPLICATION miss falls back to CANDIDATE`() {
        val c = contact(indexLevel = "APPLICATION")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.APPLICATION))
            .thenReturn(null)
        val candidateProfile = profileWith(researchFields = "Machine Learning")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE))
            .thenReturn(candidateProfile)

        val result = service.build(c, emptyList(), "general question", "")

        verify(expertSearchService).findByOrcidId("0000-0001-test", ExpertIndexLevel.APPLICATION)
        verify(expertSearchService).findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE)
        assertFalse(result.contextWarnings.contains("EXPERT_PROFILE_NOT_FOUND"))
        assertTrue(result.profileText.contains("Machine Learning"))
    }

    // Test 2: Exception → EXPERT_PROFILE_NOT_FOUND, never calls enrichment
    @Test
    fun `exception during profile load yields EXPERT_PROFILE_NOT_FOUND warning`() {
        val c = contact(indexLevel = "APPLICATION")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.APPLICATION))
            .thenThrow(RuntimeException("ES down"))

        val result = service.build(c, emptyList(), "general question", "")

        assertTrue(result.contextWarnings.contains("EXPERT_PROFILE_NOT_FOUND"))
        // Constructor-level guarantee: AiReplyContextService only takes ExpertSearchService + AiReplyContextBuilder
    }

    // Test 3: requiresResearchContext true for each canonical phrase; false for unrelated
    @Test
    fun `requiresResearchContext returns true for each canonical phrase`() {
        val phrases = listOf(
            "research profile",
            "research background",
            "research fit",
            "does my research"
        )
        for (phrase in phrases) {
            assertTrue(service.requiresResearchContext(phrase), "Expected true for: $phrase")
            assertTrue(service.requiresResearchContext(phrase.uppercase()), "Expected true (case-insensitive) for: $phrase")
        }
    }

    @Test
    fun `requiresResearchContext returns false for unrelated text`() {
        assertFalse(service.requiresResearchContext("What is the salary?"))
        assertFalse(service.requiresResearchContext("When is the deadline?"))
        assertFalse(service.requiresResearchContext("Thank you for your email"))
        assertFalse(service.requiresResearchContext(""))
    }

    // Test 4: Research phrases + empty research fields → EXPERT_RESEARCH_CONTEXT_INSUFFICIENT
    @Test
    fun `research phrase with empty research fields yields EXPERT_RESEARCH_CONTEXT_INSUFFICIENT`() {
        val c = contact(indexLevel = "CANDIDATE")
        val profile = profileWith(
            researchFields = null,
            keyword = null,
            disciplineCategory = null,
            recentWorkTitles = null
        )
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE))
            .thenReturn(profile)

        val result = service.build(c, emptyList(), "Tell me about your research profile", "")

        assertTrue(result.contextWarnings.contains("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"))
        assertFalse(result.contextWarnings.contains("EXPERT_PROFILE_NOT_FOUND"))
        assertFalse(result.researchProfileSufficient)
    }

    // Test 5: Research phrases + researchFields present → no insufficient warning
    @Test
    fun `research phrase with researchFields present yields no insufficient warning`() {
        val c = contact(indexLevel = "CANDIDATE")
        val profile = profileWith(researchFields = "Deep Learning")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE))
            .thenReturn(profile)

        val result = service.build(c, emptyList(), "Tell me about your research background", "")

        assertFalse(result.contextWarnings.contains("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"))
        assertTrue(result.researchProfileSufficient)
    }

    @Test
    fun `catalog research-fit aliases require actual profile evidence`() {
        val c = contact(indexLevel = "CANDIDATE")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE))
            .thenReturn(profileWith())

        listOf(
            "Does my research fit the programme?",
            "Does my research align with the programme?"
        ).forEach { inbound ->
            val result = service.build(c, emptyList(), inbound, "")
            assertTrue(service.requiresResearchContext(inbound), inbound)
            assertFalse(result.researchProfileSufficient, inbound)
            assertTrue(result.contextWarnings.contains("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"), inbound)
        }
    }

    // Test 6: Profile text includes research fields when present
    @Test
    fun `profile text includes research fields when present`() {
        val c = contact(indexLevel = "CANDIDATE")
        val profile = profileWith(
            researchFields = "Quantum Computing",
            keyword = "quantum",
            disciplineCategory = "STEM"
        )
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE))
            .thenReturn(profile)

        val result = service.build(c, emptyList(), "Hello", "")

        assertTrue(result.profileText.contains("Quantum Computing"))
    }

    // Test 7: verify only findByOrcidId called (never enrich)
    @Test
    fun `only findByOrcidId is called, never enrichment`() {
        val c = contact(indexLevel = "CANDIDATE")
        val profile = profileWith(researchFields = "AI")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE))
            .thenReturn(profile)

        service.build(c, emptyList(), "Hello", "")

        verify(expertSearchService).findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE)
        // Only one findByOrcidId call for CANDIDATE (no APPLICATION fallback needed)
        verify(expertSearchService, never()).findByOrcidId("0000-0001-test", ExpertIndexLevel.APPLICATION)
        // No other methods on expertSearchService invoked
        Mockito.verifyNoMoreInteractions(expertSearchService)
    }

    // Test: mailHistory included in result
    @Test
    fun `mailHistory is built from records`() {
        val c = contact(indexLevel = "CANDIDATE")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE))
            .thenReturn(profileWith())
        val record = MailRecord(
            id = 1L,
            expertContactId = 1L,
            direction = "OUTBOUND",
            mailType = "INTRODUCTION",
            messageId = null,
            inReplyTo = null,
            subject = "Hello",
            body = "Welcome",
            matchedQaRuleId = null,
            sendStatus = "SENT",
            receivedAt = null,
            sentAt = null
        )

        val result = service.build(c, listOf(record), "Hello", "")

        assertTrue(result.mailHistory.contains("OUR_TEAM"))
        assertTrue(result.mailHistory.contains("Welcome"))
    }

    // Test: training knowledge appended to profile
    @Test
    fun `training knowledge is appended to profile text`() {
        val c = contact(indexLevel = "CANDIDATE")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE))
            .thenReturn(profileWith())

        val result = service.build(c, emptyList(), "Hello", "Topic: Salary\nAnswer: Competitive")

        assertTrue(result.profileText.contains("Training knowledge base:"))
        assertTrue(result.profileText.contains("Salary"))
    }

    // 03b (T1/I-5): the two source fragments behind profileText are carried as
    // separate fields — expertProfileText is the raw buildExpertProfile output
    // (no training knowledge), trainingKnowledgeText is the passed value; the
    // existing profileText construction stays untouched.
    @Test
    fun `build carries raw expert profile and training knowledge as separate fields`() {
        val c = contact(indexLevel = "CANDIDATE")
        val profile = profileWith(researchFields = "Machine Learning")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE))
            .thenReturn(profile)
        val training = "Topic: Funding\nAnswer: 100k"

        val result = service.build(c, emptyList(), "Hello", training)

        val rawProfile = contextBuilder.buildExpertProfile(c, profile)
        assertEquals(rawProfile, result.expertProfileText)
        assertEquals(training, result.trainingKnowledgeText)
        // I-5: profileText is still the concatenation and contains the raw
        // profile fragment; the new fields never replace or alter it.
        assertEquals(
            contextBuilder.appendKnowledgeToProfile(rawProfile, training),
            result.profileText
        )
        assertTrue(result.profileText.contains(result.expertProfileText))
        assertTrue(result.profileText.contains(training))
        assertFalse(result.expertProfileText.contains(training))
    }

    // Test: CANDIDATE level does NOT try APPLICATION fallback
    @Test
    fun `CANDIDATE level only queries CANDIDATE index`() {
        val c = contact(indexLevel = "CANDIDATE")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE))
            .thenReturn(profileWith())

        service.build(c, emptyList(), "Hello", "")

        verify(expertSearchService, never()).findByOrcidId("0000-0001-test", ExpertIndexLevel.APPLICATION)
    }

    @Test
    fun `CANDIDATE query null yields EXPERT_PROFILE_NOT_FOUND`() {
        val c = contact(indexLevel = "CANDIDATE")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE))
            .thenReturn(null)

        val result = service.build(c, emptyList(), "Hello", "")

        assertTrue(result.contextWarnings.contains("EXPERT_PROFILE_NOT_FOUND"))
        verify(expertSearchService).findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE)
        Mockito.verifyNoMoreInteractions(expertSearchService)
    }

    @Test
    fun `APPLICATION and CANDIDATE both null yields EXPERT_PROFILE_NOT_FOUND`() {
        val c = contact(indexLevel = "APPLICATION")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.APPLICATION))
            .thenReturn(null)
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE))
            .thenReturn(null)

        val result = service.build(c, emptyList(), "Hello", "")

        assertTrue(result.contextWarnings.contains("EXPERT_PROFILE_NOT_FOUND"))
        verify(expertSearchService).findByOrcidId("0000-0001-test", ExpertIndexLevel.APPLICATION)
        verify(expertSearchService).findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE)
        Mockito.verifyNoMoreInteractions(expertSearchService)
    }

    @Test
    fun `contact without orcidId yields EXPERT_PROFILE_NOT_FOUND and skips ES`() {
        val c = ExpertContact(
            campaignId = 1L,
            orcidId = "",
            expertEmail = "no-orcid@example.com",
            expertName = "Unknown",
            currentIndexLevel = "CANDIDATE"
        )

        val result = service.build(c, emptyList(), "Hello", "")

        Mockito.verifyNoInteractions(expertSearchService)
        assertTrue(result.contextWarnings.contains("EXPERT_PROFILE_NOT_FOUND"))
    }

    @Test
    fun `missing profile with research request yields both profile and research warnings`() {
        val c = contact(indexLevel = "CANDIDATE")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE))
            .thenReturn(null)

        val result = service.build(c, emptyList(), "Does my expertise fall within your projects?", "")

        assertTrue(result.contextWarnings.contains("EXPERT_PROFILE_NOT_FOUND"))
        assertTrue(result.contextWarnings.contains("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"))
        verify(expertSearchService).findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE)
        Mockito.verifyNoMoreInteractions(expertSearchService)
    }

    // Test: recentWorkTitles non-empty satisfies research sufficient condition
    @Test
    fun `recentWorkTitles non-empty satisfies research sufficient condition`() {
        val c = contact(indexLevel = "CANDIDATE")
        val profile = profileWith(recentWorkTitles = listOf("Paper on AI"))
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE))
            .thenReturn(profile)

        val result = service.build(c, emptyList(), "Can you share your google scholar profile?", "")

        assertFalse(result.contextWarnings.contains("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"))
    }

    // Test: keyword alone satisfies research sufficient condition
    @Test
    fun `keyword alone satisfies research sufficient condition`() {
        val c = contact(indexLevel = "CANDIDATE")
        val profile = profileWith(keyword = "bioinformatics")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE))
            .thenReturn(profile)

        val result = service.build(c, emptyList(), "within the scope of your research", "")

        assertFalse(result.contextWarnings.contains("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"))
    }

    // ── History formatter tests (Phase 10 I-1 ~ I-4) ──

    private fun mailRecord(
        id: Long,
        direction: String,
        subject: String = "Subject $id",
        body: String = "Body $id",
        cleanedBody: String? = null,
        sendStatus: String? = null,
        messageId: String? = null,
        receivedAt: java.time.LocalDateTime? = null,
        sentAt: java.time.LocalDateTime? = null,
        createdAt: java.time.LocalDateTime? = null
    ) = MailRecord(
        id = id,
        expertContactId = 1L,
        direction = direction,
        mailType = "INTRODUCTION",
        messageId = messageId,
        inReplyTo = null,
        subject = subject,
        body = body,
        cleanedBody = cleanedBody,
        matchedQaRuleId = null,
        sendStatus = sendStatus,
        receivedAt = receivedAt,
        sentAt = sentAt,
        createdAt = createdAt
    )

    @Test
    fun `history only includes INBOUND and SENT OUTBOUND`() {
        val records = listOf(
            mailRecord(1, "INBOUND", subject = "Inc-1"),
            mailRecord(2, "OUTBOUND", sendStatus = "SENT", subject = "Sent-2"),
            mailRecord(3, "OUTBOUND", sendStatus = "FAILED", subject = "Fail-3"),
            mailRecord(4, "OUTBOUND", sendStatus = "PENDING", subject = "Pend-4"),
            mailRecord(5, "OUTBOUND", sendStatus = null, subject = "Null-5"),
            mailRecord(6, "OTHER_DIRECTION", subject = "Oth-6")
        )
        val history = contextBuilder.buildMailHistory(records, null)
        assertTrue(history.contains("[EXPERT]"))
        assertTrue(history.contains("[OUR_TEAM]"))
        assertTrue(history.contains("Inc-1"))
        assertTrue(history.contains("Sent-2"))
        assertFalse(history.contains("Fail-3"))
        assertFalse(history.contains("Pend-4"))
        assertFalse(history.contains("Null-5"))
        assertFalse(history.contains("Oth-6"))
    }

    @Test
    fun `history excludes current inbound by messageId`() {
        val records = listOf(
            mailRecord(1, "INBOUND", messageId = "<msg-001@example.com>"),
            mailRecord(2, "INBOUND", messageId = "msg-002@example.com"),
            mailRecord(3, "OUTBOUND", sendStatus = "SENT", messageId = "<msg-003>")
        )
        val history = contextBuilder.buildMailHistory(records, "<msg-001@example.com>")
        assertFalse(history.contains("Subject 1"))
        assertTrue(history.contains("Subject 2"))
        assertTrue(history.contains("Subject 3"))
    }

    @Test
    fun `history messageId normalization handles angle brackets and whitespace`() {
        val records = listOf(
            mailRecord(1, "INBOUND", messageId = " <msg-001@example.com> "),
            mailRecord(2, "INBOUND", messageId = "msg-002@example.com")
        )
        val history = contextBuilder.buildMailHistory(records, "  <msg-001@example.com>  ")
        assertFalse(history.contains("Subject 1"))
        assertTrue(history.contains("Subject 2"))
    }

    @Test
    fun `history empty current messageId keeps all eligible records`() {
        val records = listOf(
            mailRecord(1, "INBOUND", messageId = "msg-001"),
            mailRecord(2, "INBOUND", messageId = "msg-002")
        )
        val history = contextBuilder.buildMailHistory(records, null)
        assertTrue(history.contains("Subject 1"))
        assertTrue(history.contains("Subject 2"))
    }

    @Test
    fun `history limits to recent 8 records`() {
        val records = (1L..12L).map { id ->
            mailRecord(id, "INBOUND", subject = "ZZZ-$id-ZZZ", receivedAt = java.time.LocalDateTime.now().plusDays(id))
        }
        val history = contextBuilder.buildMailHistory(records, null)
        val lines = history.lines()
        val expertCount = lines.count { it.startsWith("[EXPERT]") }
        assertEquals(8, expertCount)
        assertTrue(history.contains("ZZZ-12-ZZZ"))
        assertTrue(history.contains("ZZZ-5-ZZZ"))
        assertFalse(history.contains("ZZZ-1-ZZZ"), "oldest records should be dropped")
    }

    @Test
    fun `history same effective time uses id tie break`() {
        val t = java.time.LocalDateTime.now()
        val records = listOf(
            mailRecord(1, "INBOUND", receivedAt = t),
            mailRecord(2, "INBOUND", receivedAt = t),
            mailRecord(3, "INBOUND", receivedAt = t)
        )
        val history = contextBuilder.buildMailHistory(records, null)
        assertTrue(history.contains("Subject 1"))
        assertTrue(history.contains("Subject 2"))
        assertTrue(history.contains("Subject 3"))
    }

    @Test
    fun `history subjects capped at 160 chars`() {
        val longSubject = "S".repeat(200)
        val records = listOf(
            mailRecord(1, "INBOUND", subject = longSubject)
        )
        val history = contextBuilder.buildMailHistory(records, null)
        assertTrue(history.contains("S".repeat(160)))
        assertFalse(history.contains("S".repeat(161)))
    }

    @Test
    fun `history body capped at 800 chars`() {
        val longBody = "B".repeat(1000)
        val records = listOf(
            mailRecord(1, "INBOUND", body = longBody)
        )
        val history = contextBuilder.buildMailHistory(records, null)
        assertTrue(history.contains("B".repeat(800)))
        assertFalse(history.contains("B".repeat(801)))
    }

    @Test
    fun `history prefers cleanedBody over body`() {
        val records = listOf(
            mailRecord(1, "INBOUND", body = "Raw body", cleanedBody = "Cleaned body")
        )
        val history = contextBuilder.buildMailHistory(records, null)
        assertTrue(history.contains("Cleaned body"))
        assertFalse(history.contains("Raw body"))
    }

    @Test
    fun `history total length under 5000 with complete blocks`() {
        val longBody = "B".repeat(1000)
        val records = (1L..10L).map { id ->
            mailRecord(id, "INBOUND", subject = "ZZZ-$id-ZZZ", body = longBody, receivedAt = java.time.LocalDateTime.now().plusDays(id))
        }
        val history = contextBuilder.buildMailHistory(records, null)
        assertTrue(history.length <= 5000)
        assertTrue(history.contains("ZZZ-10-ZZZ"))
        assertFalse(history.contains("ZZZ-1-ZZZ"), "oldest block should be dropped due to budget")
    }

    @Test
    fun `history format uses EXPERT and OUR_TEAM roles`() {
        val records = listOf(
            mailRecord(1, "INBOUND", subject = "Hello", body = "How are you"),
            mailRecord(2, "OUTBOUND", sendStatus = "SENT", subject = "Re: Hello", body = "I am fine")
        )
        val history = contextBuilder.buildMailHistory(records, null)
        assertTrue(history.contains("[EXPERT]"))
        assertTrue(history.contains("[OUR_TEAM]"))
    }

    @Test
    fun `history always includes Subject and Body lines`() {
        val records = listOf(
            mailRecord(1, "INBOUND", subject = "", body = "Only body")
        )
        val history = contextBuilder.buildMailHistory(records, null)
        assertTrue(history.contains("Subject:"))
        assertTrue(history.contains("Body:"))
    }

    @Test
    fun `history does not contain internal metadata`() {
        val records = listOf(
            mailRecord(1, "INBOUND", messageId = "msg-001", subject = "Hello", body = "World")
        )
        val history = contextBuilder.buildMailHistory(records, null)
        assertFalse(history.contains("msg-001"))
        assertFalse(history.contains("INBOUND"))
        assertFalse(history.contains("OUTBOUND"))
        assertFalse(history.contains("SENT"))
        assertFalse(history.contains("INTRODUCTION"))
    }

    @Test
    fun `history same body different messageIds both kept`() {
        val records = listOf(
            mailRecord(1, "INBOUND", messageId = "msg-A", subject = "Same", body = "Identical body"),
            mailRecord(2, "INBOUND", messageId = "msg-B", subject = "Msg-B", body = "Identical body")
        )
        val history = contextBuilder.buildMailHistory(records, "msg-A")
        assertFalse(history.contains("Msg-A"), "current messageId should be excluded")
        assertTrue(history.contains("Msg-B"), "different messageId should be retained")
    }

    @Test
    fun `history blocks joined with double newline separator`() {
        val records = listOf(
            mailRecord(1, "INBOUND", subject = "S1", body = "B1"),
            mailRecord(2, "INBOUND", subject = "S2", body = "B2")
        )
        val history = contextBuilder.buildMailHistory(records, null)
        assertTrue(history.contains("\n\n"))
    }

    // ── normalizeMessageId ├─

    @Test
    fun `normalizeMessageId strips angle brackets and whitespace`() {
        assertEquals("id", MailMessageIdNormalizer.normalize("id"))
        assertEquals("id", MailMessageIdNormalizer.normalize("<id>"))
        assertEquals("id", MailMessageIdNormalizer.normalize(" <id> "))
        assertEquals("id", MailMessageIdNormalizer.normalize("  <id>  "))
        assertEquals("id@x", MailMessageIdNormalizer.normalize("<id@x>"))
    }

    @Test
    fun `normalizeMessageId handles null and blank`() {
        assertEquals("", MailMessageIdNormalizer.normalize(null))
        assertEquals("", MailMessageIdNormalizer.normalize(""))
        assertEquals("", MailMessageIdNormalizer.normalize("   "))
        assertEquals("", MailMessageIdNormalizer.normalize("<>"))
    }
}
