package com.weibo.talentintroduction.postmaster.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.gmailpostmastertools.v2.model.DomainStat
import com.google.api.services.gmailpostmastertools.v2.model.QueryDomainStatsResponse
import com.google.api.services.gmailpostmastertools.v2.model.StatisticValue
import com.weibo.talentintroduction.config.PostmasterProperties
import com.weibo.talentintroduction.postmaster.domain.DomainReputationHistory
import com.weibo.talentintroduction.postmaster.repository.DomainReputationHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.time.LocalDate

class PostmasterDataCollectorTest {
    private val properties = PostmasterProperties(
        enabled = true,
        domains = listOf("talents.example.com", "mail.example.com")
    )
    private val repository = Mockito.mock(DomainReputationHistoryRepository::class.java)
    private val fetcher = Mockito.mock(DomainStatsFetcher::class.java)
    private val collector = PostmasterDataCollector(properties, repository, fetcher)

    @Test
    fun `collect upserts parsed stats for each configured domain`() {
        val date = LocalDate.of(2026, 7, 3)
        Mockito.`when`(fetcher.fetch(ArgumentMatchers.eq("talents.example.com") ?: "", ArgumentMatchers.eq(date) ?: date))
            .thenReturn(
                CollectedDomainStats(
                    spamRate = 0.002,
                    spfSuccessRate = 0.99,
                    dkimSuccessRate = 0.98,
                    dmarcSuccessRate = 0.97,
                    rawJson = """{"domain":"talents.example.com"}"""
                )
            )
        Mockito.`when`(fetcher.fetch(ArgumentMatchers.eq("mail.example.com") ?: "", ArgumentMatchers.eq(date) ?: date))
            .thenReturn(null)
        Mockito.`when`(repository.findByDomainAndReportDate(ArgumentMatchers.anyString() ?: "", ArgumentMatchers.any() ?: date))
            .thenReturn(null)

        collector.collect(date)

        val captor = ArgumentCaptor.forClass(DomainReputationHistory::class.java)
        Mockito.verify(repository, Mockito.times(1)).save(captor.capture())
        val saved = captor.value
        assertEquals("talents.example.com", saved.domain)
        assertEquals(date, saved.reportDate)
        assertEquals(0.002, saved.spamRate)
        assertEquals(0.99, saved.spfSuccessRate)
    }

    @Test
    fun `upsert updates existing row by id`() {
        val date = LocalDate.of(2026, 7, 3)
        Mockito.`when`(repository.findByDomainAndReportDate("talents.example.com", date))
            .thenReturn(
                DomainReputationHistory(
                    id = 9L,
                    domain = "talents.example.com",
                    reportDate = date,
                    spamRate = 0.001
                )
            )

        collector.upsert(
            domain = "talents.example.com",
            date = date,
            stats = CollectedDomainStats(spamRate = 0.004, rawJson = "{}")
        )

        val captor = ArgumentCaptor.forClass(DomainReputationHistory::class.java)
        Mockito.verify(repository).save(captor.capture())
        assertEquals(9L, captor.value.id)
        assertEquals(0.004, captor.value.spamRate)
    }

    @Test
    fun `parseResponse maps metric names to collected stats`() {
        val response = QueryDomainStatsResponse().setDomainStats(
            listOf(
                domainStat("spamRate", 0.0069),
                domainStat("spfSuccessRate", 0.95),
                domainStat("dkimSuccessRate", 0.96),
                domainStat("dmarcSuccessRate", 0.94)
            )
        )

        val parsed = GoogleDomainStatsFetcher.parseResponse(response, ObjectMapper())

        assertEquals(0.0069, parsed.spamRate)
        assertEquals(0.95, parsed.spfSuccessRate)
    }

    private fun domainStat(metric: String, value: Double): DomainStat =
        DomainStat()
            .setMetric(metric)
            .setValue(StatisticValue().setDoubleValue(value))
}
