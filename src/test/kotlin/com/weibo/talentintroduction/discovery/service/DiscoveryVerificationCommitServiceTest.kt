package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.RestTemplate

class DiscoveryVerificationCommitServiceTest {
    private val fixture = AdmissionGateFixture()
    private val admissions = Mockito.mock(DiscoveryReviewRepository::class.java)
    private val writer = Mockito.mock(ExpertIndexWriterService::class.java)
    private val review = Mockito.mock(DiscoveryReviewService::class.java)
    private val jdbc = Mockito.mock(JdbcTemplate::class.java)
    private val service = DiscoveryVerificationCommitService(fixture.repository, admissions, review, fixture.policy, writer, jdbc, ruleClock)
    private fun row(item: VerificationItem, decision: String = "AUTO_PASSED", revision: Long = 0) = ExpertDiscoveryAdmission(
        item.expertDocId, item.identityHash, decision, revision, null, DiscoveryVerificationAdmissionGate.POLICY_VERSION,
        null, ruleClock.instant().verificationUtc())
    private fun lease(): VerificationItem = fixture.item().copy(state = VerificationState.RUNNING, outcome = null,
        completedAt = null, generation = 1, leaseToken = "current-token", leaseUntil = ruleClock.instant().plusSeconds(120))
    private fun proposed() = fixture.item().let { VerificationResult(it.state, it.outcome, it.qualification, it.route, it.evidenceJson!!, it.reasonsJson!!) }

    @Test fun `expired lease and replacement token do not write evidence or admission`() {
        listOf(lease().copy(leaseUntil = ruleClock.instant()), lease().copy(leaseToken = "replacement")).forEach { current ->
            Mockito.`when`(fixture.repository.lockForCommit(current.id)).thenReturn(current)
            assertNull(service.commit(lease(), proposed(), VerificationCommitInput(null, null, null)))
        }
        Mockito.verifyNoInteractions(admissions, review, writer, jdbc)
    }

    @Test fun `current manual decision is protected even when preflight had no admission`() {
        val lease = lease()
        Mockito.`when`(fixture.repository.lockForCommit(lease.id)).thenReturn(lease)
        Mockito.`when`(admissions.findAdmission(lease.expertDocId)).thenReturn(row(lease, "HOLD", 2))
        Mockito.`when`(fixture.repository.saveEvidenceAndOutcome(Mockito.anyLong(), Mockito.anyString(), Mockito.anyLong(),
            Mockito.any(VerificationResult::class.java) ?: proposed(), Mockito.any(java.time.Instant::class.java) ?: ruleClock.instant())).thenReturn(true)
        val snapshot = DiscoveryVerificationAdmissionGate.snapshot(lease.expertDocId, fixture.profile, fixture.config)
        service.commit(lease, proposed(), VerificationCommitInput(snapshot, null, null))
        val saved = Mockito.mockingDetails(fixture.repository).invocations.single { it.method.name == "saveEvidenceAndOutcome" }.arguments[3] as VerificationResult
        assertEquals(VerificationOutcome.PROTECTED, saved.outcome)
        assertTrue(saved.reasonsJson.contains("REVIEW_PROTECTED"))
        Mockito.verifyNoInteractions(review, writer)
    }

    @Test fun `NOT_ADMITTED is never a successful projection even when legacy sync says ok`() {
        val item = fixture.item().copy(admissionRevision = 0, projectionState = VerificationProjection.PENDING)
        Mockito.`when`(admissions.findAdmission(item.expertDocId)).thenReturn(row(item))
        val source = mapOf<String, Any?>("fixture" to "raw")
        Mockito.`when`(writer.readDiscoveryDocument(com.weibo.talentintroduction.expert.domain.ExpertIndexLevel.RAW, item.expertDocId))
            .thenReturn(ExpertIndexWriterService.DiscoverySnapshot(source, 1, 1))
        Mockito.`when`(writer.discoveryProfile(item.expertDocId, source)).thenReturn(fixture.profile)
        fixture.load(item)
        Mockito.`when`(review.projectApprovedCandidate(item.expertDocId, source, "AUTO_PASSED"))
            .thenReturn(DiscoveryReviewCandidateSync("NOT_ADMITTED", true, null))
        service.project(item)
        Mockito.verify(fixture.repository).markProjection(item.id, 0, VerificationProjection.ERROR, "NOT_ADMITTED", ruleClock.instant())
    }

    @Test fun `strict review seam rejects revision zero fallback but accepts confirmed real revision zero row`() {
        val policy = Mockito.mock(DiscoveryAdmissionPolicy::class.java)
        val props = ElasticsearchProperties("http://fixture.invalid", "u", "p", "raw", "candidate", "application")
        val actualReview = DiscoveryReviewService(admissions, policy, writer, RestTemplate(), props,
            Mockito.mock(ExpertIndexService::class.java), fixture.mapper, ruleClock)
        val item = fixture.item()
        val automatic = fixture.policy.evaluate(item.expertDocId, fixture.profile, VerificationAdmissionContext(item.expertDocId, fixture.profile, listOf(item)))
        Mockito.`when`(admissions.findAdmission(item.expertDocId)).thenReturn(null)
        assertThrows(IllegalStateException::class.java) { actualReview.recordAutomaticPersisted(item.expertDocId, fixture.profile, automatic) }
        Mockito.`when`(admissions.findAdmission(item.expertDocId)).thenReturn(row(item))
        assertEquals(0, actualReview.recordAutomaticPersisted(item.expertDocId, fixture.profile, automatic).revision)
    }
}
