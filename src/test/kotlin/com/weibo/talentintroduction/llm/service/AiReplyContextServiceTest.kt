package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.domain.MailRecord
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
            "areas of expertise",
            "expertise fall within",
            "within the scope",
            "google scholar",
            "scopus"
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
            sendStatus = null,
            receivedAt = null,
            sentAt = null
        )

        val result = service.build(c, listOf(record), "Hello", "")

        assertTrue(result.mailHistory.contains("OUTBOUND"))
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

    // Test: CANDIDATE level does NOT try APPLICATION fallback
    @Test
    fun `CANDIDATE level only queries CANDIDATE index`() {
        val c = contact(indexLevel = "CANDIDATE")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE))
            .thenReturn(profileWith())

        service.build(c, emptyList(), "Hello", "")

        verify(expertSearchService, never()).findByOrcidId("0000-0001-test", ExpertIndexLevel.APPLICATION)
    }

    // Test: profile not found (null, no exception) only adds warning if research context needed
    @Test
    fun `profile not found without exception does not add EXPERT_PROFILE_NOT_FOUND`() {
        val c = contact(indexLevel = "CANDIDATE")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001-test", ExpertIndexLevel.CANDIDATE))
            .thenReturn(null)

        val result = service.build(c, emptyList(), "Hello", "")

        assertFalse(result.contextWarnings.contains("EXPERT_PROFILE_NOT_FOUND"))
    }

    // Test: null orcidId returns minimal profile, no ES call
    @Test
    fun `contact without orcidId skips ES lookup`() {
        val c = ExpertContact(
            campaignId = 1L,
            orcidId = "",
            expertEmail = "no-orcid@example.com",
            expertName = "Unknown",
            currentIndexLevel = "CANDIDATE"
        )

        val result = service.build(c, emptyList(), "Hello", "")

        Mockito.verifyNoInteractions(expertSearchService)
        assertTrue(result.contextWarnings.isEmpty())
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
}
