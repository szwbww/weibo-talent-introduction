package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DiscoveryCountryRefreshServiceTest {
    private val mapper = jacksonObjectMapper().findAndRegisterModules()

    @Test fun `completed verification fills existing layers only and appends country receipt`() {
        val f = AdmissionGateFixture()
        val profile = f.profile.copy(institution = null, externalIds = """{"orcid":"0000-0002-1825-0097"}""")
        val item = f.item(profile).copy(projectionState = VerificationProjection.NOT_REQUIRED)
        val repository = Mockito.mock(DiscoveryVerificationRepository::class.java)
        Mockito.`when`(repository.findItem(item.id)).thenReturn(item)
        Mockito.`when`(repository.findRun(item.ownerExecutionId)).thenReturn(VerificationRun(1, VerificationMode.ENFORCE,
            VerificationMode.ENFORCE.policyVersion, true, "{}", createdAt = ruleClock.instant(), updatedAt = ruleClock.instant()))
        val writer = Mockito.mock(ExpertIndexWriterService::class.java)
        val raw = ExpertIndexWriterService.DiscoverySnapshot(mapOf("country" to null), 2, 1)
        val candidate = ExpertIndexWriterService.DiscoverySnapshot(mapOf("country" to null), 3, 1)
        Mockito.`when`(writer.readDiscoveryDocument(ExpertIndexLevel.RAW, item.expertDocId)).thenReturn(raw)
        Mockito.`when`(writer.readDiscoveryDocument(ExpertIndexLevel.CANDIDATE, item.expertDocId)).thenReturn(candidate)
        Mockito.`when`(writer.discoveryProfile(item.expertDocId, raw.source)).thenReturn(profile)
        Mockito.`when`(writer.writeDiscoveryCountryIfVersionMatches(ExpertIndexLevel.RAW, item.expertDocId, raw, "GB", null)).thenReturn(true)
        Mockito.`when`(writer.writeDiscoveryCountryIfVersionMatches(ExpertIndexLevel.CANDIDATE, item.expertDocId, candidate, "GB", null)).thenReturn(true)
        val jdbc = Mockito.mock(JdbcTemplate::class.java)
        val countries = DiscoveryCountrySupplementService(DiscoveryVerificationProperties(countrySupplementEnabled = true), ruleClock)
        val document = OrcidEvidenceDocument("employments", mapper.readTree("""{"path":"/0000-0002-1825-0097/employments",
          "affiliation-group":[{"summaries":[{"employment-summary":{"visibility":"public","put-code":1,
          "source":{"source-orcid":{"path":"0000-0002-1825-0097"}},"start-date":{"year":{"value":"2020"}},
          "organization":{"name":"Example University","address":{"country":"GB"}}}}]}]}"""), "a".repeat(64), ruleClock.instant())
        DiscoveryCountryRefreshService(countries, writer, repository, jdbc).refresh(item,
            VerificationWorkerEvidence(orcidCompleted = listOf(OrcidEndpointCompletion("employments", document))))
        Mockito.verify(writer).writeDiscoveryCountryIfVersionMatches(ExpertIndexLevel.RAW, item.expertDocId, raw, "GB", null)
        Mockito.verify(writer).writeDiscoveryCountryIfVersionMatches(ExpertIndexLevel.CANDIDATE, item.expertDocId, candidate, "GB", null)
        Mockito.verify(writer).readDiscoveryDocument(ExpertIndexLevel.APPLICATION, item.expertDocId)
        val sqlCalls = Mockito.mockingDetails(jdbc).invocations
        assertEquals(1, sqlCalls.size)
        assertTrue(sqlCalls.single().arguments[0].toString().contains("$.countrySupplement"))
        assertFalse(sqlCalls.single().arguments[0].toString().contains("snapshot_json="))
    }

    @Test fun `audit mode performs no reads writes or provider requests`() {
        val writer = Mockito.mock(ExpertIndexWriterService::class.java)
        val repository = Mockito.mock(DiscoveryVerificationRepository::class.java)
        val jdbc = Mockito.mock(JdbcTemplate::class.java)
        val countries = DiscoveryCountrySupplementService(DiscoveryVerificationProperties(countrySupplementEnabled = true), ruleClock)
        val item = AdmissionGateFixture().item(mode = VerificationMode.AUDIT)
        DiscoveryCountryRefreshService(countries, writer, repository, jdbc).refresh(item, VerificationWorkerEvidence())
        Mockito.verifyNoInteractions(writer, repository, jdbc)
    }
}
