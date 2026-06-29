package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime

class ContactCountryBackfillServiceTest {
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val expertSearchService = Mockito.mock(ExpertSearchService::class.java)
    private val service = ContactCountryBackfillService(expertContactRepository, expertSearchService)

    @Test
    fun `runBackfill queries APPLICATION index for promoted contacts`() {
        val applicationContact = contact(
            id = 1L,
            orcidId = "0000-0001-1111-2222",
            currentIndexLevel = ExpertIndexLevel.APPLICATION.name
        )
        val candidateContact = contact(
            id = 2L,
            orcidId = "0000-0002-3333-4444",
            currentIndexLevel = ExpertIndexLevel.CANDIDATE.name
        )
        Mockito.`when`(expertContactRepository.findAll())
            .thenReturn(listOf(applicationContact, candidateContact))
        Mockito.`when`(
            expertSearchService.searchByOrcidIds(
                listOf("0000-0001-1111-2222"),
                ExpertIndexLevel.APPLICATION
            )
        ).thenReturn(listOf(profile("0000-0001-1111-2222", "Germany")))
        Mockito.`when`(
            expertSearchService.searchByOrcidIds(
                listOf("0000-0002-3333-4444"),
                ExpertIndexLevel.CANDIDATE
            )
        ).thenReturn(listOf(profile("0000-0002-3333-4444", "France")))
        Mockito.`when`(expertContactRepository.updateCountryById(Mockito.anyLong(), Mockito.any()))
            .thenReturn(1)

        val result = service.runBackfill()

        assertEquals(2, result.processed)
        assertEquals(2, result.matched)
        assertEquals(0, result.unmatched)
        Mockito.verify(expertSearchService).searchByOrcidIds(
            listOf("0000-0001-1111-2222"),
            ExpertIndexLevel.APPLICATION
        )
        Mockito.verify(expertSearchService).searchByOrcidIds(
            listOf("0000-0002-3333-4444"),
            ExpertIndexLevel.CANDIDATE
        )
        Mockito.verify(expertContactRepository).updateCountryById(1L, "Germany")
        Mockito.verify(expertContactRepository).updateCountryById(2L, "France")
        Mockito.verify(expertContactRepository, Mockito.never()).save(Mockito.any(ExpertContact::class.java))
    }

    @Test
    fun `runBackfill keeps null when ES misses orcid`() {
        val missingContact = contact(id = 3L, orcidId = "0000-0003-5555-6666")
        Mockito.`when`(expertContactRepository.findAll()).thenReturn(listOf(missingContact))
        Mockito.`when`(
            expertSearchService.searchByOrcidIds(
                listOf("0000-0003-5555-6666"),
                ExpertIndexLevel.CANDIDATE
            )
        ).thenReturn(emptyList())
        Mockito.`when`(expertContactRepository.updateCountryById(3L, null)).thenReturn(1)

        val result = service.runBackfill()

        assertEquals(1, result.processed)
        assertEquals(0, result.matched)
        assertEquals(1, result.unmatched)
        Mockito.verify(expertContactRepository).updateCountryById(3L, null)
        Mockito.verify(expertContactRepository, Mockito.never()).save(Mockito.any(ExpertContact::class.java))
    }

    @Test
    fun `runBackfill skips contacts that already have country`() {
        val filled = contact(id = 4L, orcidId = "0000-0004-7777-8888", country = "Japan")
        val pending = contact(id = 5L, orcidId = "0000-0005-9999-0000")
        Mockito.`when`(expertContactRepository.findAll()).thenReturn(listOf(filled, pending))
        Mockito.`when`(
            expertSearchService.searchByOrcidIds(
                listOf("0000-0005-9999-0000"),
                ExpertIndexLevel.CANDIDATE
            )
        ).thenReturn(listOf(profile("0000-0005-9999-0000", "Canada")))
        Mockito.`when`(expertContactRepository.updateCountryById(5L, "Canada")).thenReturn(1)

        val result = service.runBackfill()

        assertEquals(1, result.processed)
        assertEquals(1, result.skipped)
        Mockito.verify(expertContactRepository).updateCountryById(5L, "Canada")
        Mockito.verify(expertContactRepository, Mockito.never()).updateCountryById(4L, "Japan")
        Mockito.verify(expertContactRepository, Mockito.never()).save(Mockito.any(ExpertContact::class.java))
    }

    private fun contact(
        id: Long,
        orcidId: String,
        currentIndexLevel: String = ExpertIndexLevel.CANDIDATE.name,
        country: String? = null
    ) = ExpertContact(
        id = id,
        campaignId = 10L,
        orcidId = orcidId,
        expertEmail = "$orcidId@example.com",
        expertName = "Expert",
        currentIndexLevel = currentIndexLevel,
        country = country,
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
        updatedAt = LocalDateTime.of(2026, 1, 1, 0, 0)
    )

    private fun profile(orcidId: String, country: String) = ExpertProfile(
        orcidId = orcidId,
        email = "$orcidId@example.com",
        givenNames = "A",
        familyNames = "B",
        country = country,
        keyword = null,
        employment = null
    )
}
