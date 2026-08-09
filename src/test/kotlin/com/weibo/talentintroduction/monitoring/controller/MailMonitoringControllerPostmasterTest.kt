package com.weibo.talentintroduction.monitoring.controller

import com.weibo.talentintroduction.config.PostmasterProperties
import com.weibo.talentintroduction.monitoring.service.MailMonitoringService
import com.weibo.talentintroduction.postmaster.repository.DomainReputationHistoryRepository
import com.weibo.talentintroduction.postmaster.service.PostmasterDataCollector
import com.weibo.talentintroduction.postmaster.service.ReputationAutoPauseService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import java.time.LocalDate

class MailMonitoringControllerPostmasterTest {

    private val monitoringService = Mockito.mock(MailMonitoringService::class.java)
    private val historyRepository = Mockito.mock(DomainReputationHistoryRepository::class.java)
    private val collector = Mockito.mock(PostmasterDataCollector::class.java)
    private val autoPause = Mockito.mock(ReputationAutoPauseService::class.java)

    @Suppress("UNCHECKED_CAST")
    private fun <T> providerOf(value: T?): ObjectProvider<T> {
        val provider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<T>
        Mockito.`when`(provider.getIfAvailable()).thenReturn(value)
        return provider
    }

    private fun controller(
        properties: PostmasterProperties,
        collectorBean: PostmasterDataCollector?,
        autoPauseBean: ReputationAutoPauseService?
    ) = MailMonitoringController(
        monitoringService,
        historyRepository,
        properties,
        providerOf(collectorBean),
        providerOf(autoPauseBean)
    )

    @Test
    fun `returns a clear message instead of failing when postmaster is disabled`() {
        val response = controller(
            PostmasterProperties(enabled = false),
            collectorBean = null,
            autoPauseBean = null
        ).collectPostmaster(date = null, skipAutoPause = false)

        assertFalse(response.triggered)
        assertTrue(response.message.contains("POSTMASTER_ENABLED"))
        Mockito.verifyNoInteractions(collector)
    }

    @Test
    fun `returns a clear message when no domain is configured`() {
        val response = controller(
            PostmasterProperties(enabled = true, domains = emptyList()),
            collectorBean = collector,
            autoPauseBean = autoPause
        ).collectPostmaster(date = null, skipAutoPause = false)

        assertFalse(response.triggered)
        assertTrue(response.message.contains("POSTMASTER_DOMAINS"))
        Mockito.verify(collector, Mockito.never()).collect(Mockito.any() ?: LocalDate.now())
    }

    @Test
    fun `defaults to yesterday because postmaster data lags by at least a day`() {
        val response = controller(
            PostmasterProperties(enabled = true, domains = listOf("talents.example.com")),
            collectorBean = collector,
            autoPauseBean = autoPause
        ).collectPostmaster(date = null, skipAutoPause = false)

        val yesterday = LocalDate.now().minusDays(1)
        assertTrue(response.triggered)
        assertEquals(yesterday.toString(), response.reportDate)
        Mockito.verify(collector).collect(yesterday)
        Mockito.verify(autoPause).checkAndAct()
    }

    @Test
    fun `honours an explicit date and can skip the auto pause step`() {
        val date = LocalDate.of(2026, 8, 1)

        val response = controller(
            PostmasterProperties(enabled = true, domains = listOf("talents.example.com")),
            collectorBean = collector,
            autoPauseBean = autoPause
        ).collectPostmaster(date = date, skipAutoPause = true)

        assertTrue(response.triggered)
        assertEquals("2026-08-01", response.reportDate)
        Mockito.verify(collector).collect(date)
        Mockito.verifyNoInteractions(autoPause)
    }
}
