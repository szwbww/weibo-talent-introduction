package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

class DiscoveryVerificationQueryServiceTest {
    private val repository = mock(DiscoveryVerificationRepository::class.java)
    private val admissions = mock(DiscoveryReviewRepository::class.java)
    private val service = DiscoveryVerificationQueryService(repository, admissions,
        DiscoveryVerificationProperties(enabled = true, apolloApiKey = "NEVER-OUTPUT-SECRET", orcidAccessToken = "TOKEN-SECRET"))
    private val now = Instant.parse("2026-10-18T10:00:00Z")
    private fun run(id: Long, mode: VerificationMode = VerificationMode.ENFORCE) =
        VerificationRun(id, mode, mode.policyVersion, true, "{}", true, 17, now, now)

    @Test fun `summary distinguishes missing empty expired and audit suggestions using batched aggregate`() {
        val ids = listOf(3001L, 3002L, 3003L, 3004L)
        `when`(repository.reportingRuns(ids)).thenReturn(mapOf(3001L to run(3001), 3003L to run(3003), 3004L to run(3004, VerificationMode.AUDIT)))
        `when`(repository.reportingCollectionStatuses(ids)).thenReturn(mapOf(3002L to "SUCCESS", 3003L to "SUCCESS", 3004L to "SUCCESS"))
        val filter = VerificationReportFilter(3001)
        `when`(repository.reportingCounts(filter, executionIds = ids)).thenReturn(mapOf(3001L to VerificationCounts(profiles = 1, autoAdmitted = 1, directAdmitted = 1, clear = 1),
            3004L to VerificationCounts(profiles = 2, clear = 1, unclear = 1)))
        `when`(repository.reportingSuggestions(filter, executionIds = ids)).thenReturn(mapOf(3004L to Triple(2L, 1L, 1L)))
        val rows = service.summary(ids)
        assertEquals("EXPIRED", rows[0].collectionStatus)
        assertEquals("NOT_RECORDED", rows[1].coverage); assertNull(rows[1].counts); assertNull(rows[1].snapshotVersion)
        assertEquals("RECORDED", rows[2].coverage); assertEquals(0L, rows[2].counts!!.profiles)
        assertEquals("COMPLETED", rows[2].lifecycle); assertEquals(17L, rows[2].snapshotVersion)
        assertEquals(0L, rows[3].counts!!.autoAdmitted); assertEquals(VerificationSuggestions(2, 1, 1), rows[3].suggested)
        verify(repository).reportingCounts(filter, executionIds = ids)
        verify(repository).reportingRuns(ids)
        verify(repository).reportingCollectionStatuses(ids)
        verify(repository).reportingSuggestions(filter, executionIds = ids)
        verifyNoInteractions(admissions)
        verifyNoMoreInteractions(repository)
    }

    @Test fun `legacy batches and records remain null and have no fabricated items`() {
        `when`(repository.reportingCollectionStatuses(listOf(3002L))).thenReturn(mapOf(3002L to "SUCCESS"))
        assertNull(service.batches(3002, 0, 20).runCounts)
        val records = service.records(VerificationReportFilter(3002), 0, 20)
        assertNull(records.total); assertNull(records.counts); assertEquals(emptyList<VerificationRecord>(), records.items)
        verifyNoInteractions(admissions)
    }

    @Test fun `database error is never changed to zero`() {
        `when`(repository.reportingRuns(listOf(1L))).thenThrow(org.springframework.dao.DataAccessResourceFailureException("offline"))
        assertThrows(org.springframework.dao.DataAccessResourceFailureException::class.java) { service.summary(listOf(1)) }
    }

    @Test fun `all public reads share repeatable read transaction contract and expose no credentials`() {
        val annotation = DiscoveryVerificationQueryService::class.java.getAnnotation(Transactional::class.java)
        assertTrue(annotation.readOnly); assertEquals(Isolation.REPEATABLE_READ, annotation.isolation)
        val capabilities = service.capabilities()
        assertTrue(capabilities.ready); assertEquals(emptyList<String>(), capabilities.reasonCodes)
        val json = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(capabilities)
        assertFalse(json.contains("SECRET")); verifyNoInteractions(repository, admissions)
        val disabled = DiscoveryVerificationQueryService(repository, admissions, DiscoveryVerificationProperties()).capabilities()
        assertFalse(disabled.ready); assertTrue(disabled.reasonCodes.contains("VERIFICATION_DISABLED"))
        assertTrue(disabled.reasonCodes.contains("APOLLO_API_KEY_MISSING"))
    }

    @Test fun `invalid request values are rejected before SQL`() {
        listOf(emptyList(), listOf(0L), (1L..51L).toList()).forEach { ids ->
            assertThrows(IllegalArgumentException::class.java) { service.summary(ids) }
        }
        assertThrows(IllegalArgumentException::class.java) { service.records(VerificationReportFilter(1), -1, 20) }
        assertThrows(IllegalArgumentException::class.java) { service.batches(1, 0, 101) }
        assertThrows(IllegalArgumentException::class.java) { VerificationReportFilter(1, route = "NONE") }
        assertThrows(IllegalArgumentException::class.java) { VerificationReportFilter(1, result = "UNKNOWN") }
        assertThrows(IllegalArgumentException::class.java) { VerificationReportFilter(1, q = "x".repeat(101)) }
        assertThrows(IllegalArgumentException::class.java) { VerificationReportFilter(1, batchNumber = 0) }
        verifyNoInteractions(repository, admissions)
    }
}
