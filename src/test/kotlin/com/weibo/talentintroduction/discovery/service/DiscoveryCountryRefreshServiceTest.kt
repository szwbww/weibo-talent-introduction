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
        val sqlCalls = Mockito.mockingDetails(jdbc).invocations.filter { it.method.name == "update" }
        assertEquals(1, sqlCalls.size)
        assertTrue(sqlCalls.single().arguments[0].toString().contains("$.countrySupplement"))
        assertFalse(sqlCalls.single().arguments[0].toString().contains("snapshot_json="))
    }


    @Test fun `busy country channel persists a retry without reopening qualification verification`() {
        val f = AdmissionGateFixture()
        val profile = f.profile.copy(institution = null, country = null,
            externalIds = """{"orcid":"0000-0002-1825-0097"}""")
        val item = f.item(profile).copy(projectionState = VerificationProjection.NOT_REQUIRED)
        val repository = Mockito.mock(DiscoveryVerificationRepository::class.java)
        Mockito.`when`(repository.findItem(item.id)).thenReturn(item)
        Mockito.`when`(repository.findRun(item.ownerExecutionId)).thenReturn(VerificationRun(1, VerificationMode.ENFORCE,
            VerificationMode.ENFORCE.policyVersion, true, "{}", createdAt = ruleClock.instant(), updatedAt = ruleClock.instant()))
        val writer = Mockito.mock(ExpertIndexWriterService::class.java)
        val raw = ExpertIndexWriterService.DiscoverySnapshot(mapOf("country" to null), 2, 1)
        Mockito.`when`(writer.readDiscoveryDocument(ExpertIndexLevel.RAW, item.expertDocId)).thenReturn(raw)
        Mockito.`when`(writer.discoveryProfile(item.expertDocId, raw.source)).thenReturn(profile)
        val jdbc = Mockito.mock(JdbcTemplate::class.java)
        val countries = DiscoveryCountrySupplementService(DiscoveryVerificationProperties(countrySupplementEnabled = true), ruleClock)
        assertEquals(CountryPermit.ACQUIRED, countries.tryAcquirePermit())
        try {
            DiscoveryCountryRefreshService(countries, writer, repository, jdbc).refresh(item, VerificationWorkerEvidence())
        } finally { countries.releasePermit() }
        val save = Mockito.mockingDetails(jdbc).invocations.single { it.method.name == "update" }
        val sql = save.arguments[0].toString()
        assertTrue(sql.contains("next_attempt_at="), "Skipped country lookup must survive worker completion and process restart")
        assertFalse(sql.contains("SET state="), "Must not repeat qualification/Apollo verification")
        val receipt = mapper.readTree(save.arguments[1].toString())
        assertEquals("PENDING", receipt.path("retry").path("state").asText())
        assertEquals("CHANNEL_BUSY", receipt.path("retry").path("reason").asText())
        assertTrue(receipt.path("retry").path("nextAttemptAt").isTextual)
    }


    @Test fun `new excluded location revalidates admission but keeps contacted experts protected`() {
        for (protected in listOf(false, true)) {
            val f = AdmissionGateFixture()
            val profile = f.profile.copy(country = "CN", institution = null,
                externalIds = """{"orcid":"0000-0002-1825-0097"}""")
            val priorReceipt = """{"status":"FILLED","afterCountry":"CN","selectedSource":"ORCID","observations":[{"country":"CN"}]}"""
            val item = f.item(profile).copy(projectionState = VerificationProjection.NOT_REQUIRED,
                evidenceJson = """{"countrySupplement":{"receipt":$priorReceipt}}""")
            val repository = Mockito.mock(DiscoveryVerificationRepository::class.java)
            Mockito.`when`(repository.findItem(item.id)).thenReturn(item)
            Mockito.`when`(repository.findRun(item.ownerExecutionId)).thenReturn(VerificationRun(1, VerificationMode.ENFORCE,
                VerificationMode.ENFORCE.policyVersion, true, "{}", createdAt = ruleClock.instant(), updatedAt = ruleClock.instant()))
            val writer = Mockito.mock(ExpertIndexWriterService::class.java)
            val raw = ExpertIndexWriterService.DiscoverySnapshot(mapOf("country" to "CN"), 2, 1)
            Mockito.`when`(writer.readDiscoveryDocument(ExpertIndexLevel.RAW, item.expertDocId)).thenReturn(raw)
            Mockito.`when`(writer.discoveryProfile(item.expertDocId, raw.source)).thenReturn(profile)
            val jdbc = Mockito.mock(JdbcTemplate::class.java)
            Mockito.doAnswer { call -> call.arguments[0].toString().contains("expert_contact") && protected }
                .`when`(jdbc).queryForObject(Mockito.anyString(), Mockito.eq(Boolean::class.java), Mockito.anyLong())
            // Vararg overload for contact protection has three arguments (email, bound ORCID, email).
            Mockito.`when`(jdbc.queryForObject(Mockito.contains("expert_contact"), Mockito.eq(Boolean::class.java),
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenReturn(protected)
            val revalidation = Mockito.mock(com.weibo.talentintroduction.expert.service.ExpertRevalidationService::class.java)
            Mockito.`when`(revalidation.revalidateDiscovery(item.expertDocId))
                .thenReturn(com.weibo.talentintroduction.expert.service.PromotionOutcome.Rejected(listOf("LOCATION_EXCLUDED")))
            val countries = DiscoveryCountrySupplementService(DiscoveryVerificationProperties(countrySupplementEnabled = true), ruleClock)
            DiscoveryCountryRefreshService(countries, writer, repository, jdbc, revalidation, ruleClock)
                .refresh(item, VerificationWorkerEvidence())
            if (protected) Mockito.verifyNoInteractions(revalidation)
            else Mockito.verify(revalidation).revalidateDiscovery(item.expertDocId)
            val saved = Mockito.mockingDetails(jdbc).invocations.single { it.method.name == "update" }
            val receipt = mapper.readTree(saved.arguments[1].toString())
            assertEquals(mapper.readTree(priorReceipt), receipt.path("receipt"), "Retain the country observation across layer/admission retries")
            assertEquals(if (protected) "CONTACT_PROTECTED" else "Rejected", receipt.path("admission").asText())
            assertEquals("DONE", receipt.path("retry").path("state").asText())
        }
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
