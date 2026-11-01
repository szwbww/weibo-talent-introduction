package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.discovery.archive.*
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository
import com.weibo.talentintroduction.expert.domain.*
import com.weibo.talentintroduction.expert.service.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class RetainedPaperIdentityServiceTest {
    private val gate = AdmissionGateFixture()
    private val writer = Mockito.mock(ExpertIndexWriterService::class.java)
    private val repair = Mockito.mock(DiscoveryIdentityRepairService::class.java)
    private val contacts = Mockito.mock(ExpertContactRepository::class.java)
    private val aliases = Mockito.mock(ExpertEmailAliasService::class.java)
    private val admissions = Mockito.mock(DiscoveryReviewRepository::class.java)
    private val papers = Mockito.mock(PaperVerificationEvidenceService::class.java)
    private val archive = Mockito.mock(DiscoveryPaperArchiveService::class.java)
    private val service = RetainedPaperIdentityService(archive, Mockito.mock(DiscoveryPaperArchiveRepository::class.java),
        Mockito.mock(PaperBodyAnalyzer::class.java), papers, writer, repair, contacts, aliases, admissions, gate.policy)
    private val incoming = AuthorEmail("ada@example.invalid", "Ada", "Lee", false, null, null,
        identityEvidence = "SOURCE_SHA256:" + "a".repeat(64))
    private val source = mutableMapOf<String, Any?>("email" to incoming.email, "dataSource" to "CROSSREF", "emailSource" to "PAPER_FULLTEXT",
        "givenNames" to "", "familyNames" to "", "tags" to listOf("discovered"))
    private fun arrange() {
        Mockito.`when`(writer.readDiscoveryDocument(ExpertIndexLevel.RAW, "doc")).thenAnswer { ExpertIndexWriterService.DiscoverySnapshot(source.toMap(), 1, 1) }
        Mockito.`when`(writer.findDiscoveryHitsByEmail(incoming.email)).thenAnswer { listOf(DiscoveryEmailHit("doc", ExpertIndexLevel.RAW, source)) }
    }
    private fun proposal() = RetainedIdentityProposal(incoming, emptyList(), "SOURCE", "PROPOSED")

    @Test fun `protection lookup exceptions and duplicate document IDs fail closed before repair`() {
        arrange()
        Mockito.`when`(contacts.existsByOrcidId("doc")).thenThrow(IllegalStateException("offline"))
        assertEquals("UNAVAILABLE", service.apply("doc", proposal()).phase)
        Mockito.verifyNoInteractions(repair)
        Mockito.reset(contacts)
        Mockito.`when`(writer.findDiscoveryHitsByEmail(incoming.email)).thenReturn(listOf(
            DiscoveryEmailHit("doc", ExpertIndexLevel.RAW, source), DiscoveryEmailHit("other", ExpertIndexLevel.RAW, source)))
        assertEquals("CONFLICT", service.apply("doc", proposal()).phase)
        Mockito.verifyNoInteractions(repair)
    }

    @Test fun `application manual history and nonempty name conflicts never write identity`() {
        arrange()
        Mockito.`when`(writer.readDiscoveryDocument(ExpertIndexLevel.APPLICATION, "doc"))
            .thenReturn(ExpertIndexWriterService.DiscoverySnapshot(source, 1, 1))
        assertEquals("PROTECTED", service.apply("doc", proposal()).phase)
        Mockito.`when`(writer.readDiscoveryDocument(ExpertIndexLevel.APPLICATION, "doc")).thenReturn(null)
        for (decision in listOf("HOLD", "MANUAL_APPROVED", "REJECTED", "LEGACY_APPROVED")) {
            Mockito.`when`(admissions.findAdmission("doc")).thenReturn(ExpertDiscoveryAdmission("doc", "b".repeat(64), decision,
                4, null, DiscoveryVerificationAdmissionGate.POLICY_VERSION, null, ruleClock.instant().verificationUtc()))
            assertEquals("PROTECTED", service.apply("doc", proposal()).phase)
        }
        Mockito.`when`(admissions.findAdmission("doc")).thenReturn(null)
        source["givenNames"] = "Eve"
        assertEquals("CONFLICT", service.apply("doc", proposal()).phase)
        Mockito.verifyNoInteractions(repair)
    }

    @Test fun `partial layer receipt cannot report effective identity or call admission or person writers`() {
        arrange()
        Mockito.`when`(repair.repairEmptyNameProof("doc", incoming)).thenReturn(IdentityRepairResult(IdentityRepairOutcome.PARTIAL,
            layers = listOf(IdentityRepairLayer(ExpertIndexLevel.RAW, IdentityRepairLayerOutcome.REPAIRED),
                IdentityRepairLayer(ExpertIndexLevel.CANDIDATE, IdentityRepairLayerOutcome.VERSION_CONFLICT))))
        val result = service.apply("doc", proposal())
        assertEquals("PARTIAL", result.phase)
        assertNull(result.effective)
        assertEquals(2, result.layers.size)
        assertTrue(Mockito.mockingDetails(admissions).invocations.all { it.method.name == "findAdmission" })
    }

    @Test fun `virtual profile fills proof without any business writer and proposal hash is JSON order independent`() {
        val before = ruleSnapshot().let { it.copy(profile = it.profile.copy(givenNames = "", familyNames = "", identityVerification = null)) }
        val effective = service.virtual("doc", before, incoming)
        assertEquals("Ada Lee", effective.fullName)
        assertTrue(effective.identityValid)
        assertNotEquals(before.identityHash, effective.identityHash)
        Mockito.verifyNoInteractions(writer, repair, contacts, aliases, admissions, archive, papers)
        assertEquals(RetainedPaperIdentityService.hash(linkedMapOf("a" to 1, "b" to 2)),
            RetainedPaperIdentityService.hash(linkedMapOf("b" to 2, "a" to 1)))
    }

    @Test fun `complete names still need proof but complete verified identity is unchanged without source access`() {
        arrange()
        source["givenNames"] = "Ada"
        source["familyNames"] = "Lee"
        val profile = ruleSnapshot().profile.copy(email = incoming.email, givenNames = "Ada", familyNames = "Lee",
            identityVerification = null, externalIds = null)
        val before = DiscoveryVerificationAdmissionGate.snapshot("doc", profile, gate.policy.evaluate("doc", profile).configSnapshot)
        assertFalse(before.identityValid)
        assertEquals("UNAVAILABLE", service.propose("doc", before, emptyList(), ruleClock.instant()).phase)
        val effective = service.virtual("doc", before, incoming)
        assertEquals("Ada Lee", effective.fullName)
        assertTrue(effective.identityValid)
        source["identityVerification"] = effective.profile.identityVerification
        val complete = service.propose("doc", effective, emptyList(), ruleClock.instant())
        assertEquals("UNCHANGED", complete.phase)
        assertEquals("IDENTITY_ALREADY_COMPLETE", complete.reason)
        Mockito.verifyNoInteractions(repair, archive, papers)
    }

    @Test fun `proposal hash preserves timestamp and decimal precision across persisted JSON round trip`() {
        val codec = RetainedPaperIdentityService.codec
        val value = codec.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(mapOf(
            "evaluatedAt" to java.time.Instant.parse("2026-10-18T00:00:00.123456789Z"),
            "sourceId" to 31372L, "fact" to java.math.BigDecimal("1234567890.123456789")))
        val restored = codec.readTree(codec.writeValueAsString(value))
        assertEquals(RetainedPaperIdentityService.hash(value), RetainedPaperIdentityService.hash(restored))
        val changed = value.deepCopy().put("fact", java.math.BigDecimal("1234567890.123456790"))
        assertNotEquals(RetainedPaperIdentityService.hash(value), RetainedPaperIdentityService.hash(changed))
    }
}
