package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.support.DefaultListableBeanFactory

class DiscoveryVerificationWorkerTest {
    private fun worker(props: DiscoveryVerificationProperties, repo: DiscoveryVerificationRepository,
        factory: DefaultListableBeanFactory = DefaultListableBeanFactory()): DiscoveryVerificationWorker =
        DiscoveryVerificationWorker(props, repo, Mockito.mock(OrcidVerificationEvidenceClient::class.java),
            Mockito.mock(ApolloPeopleSearchClient::class.java), Mockito.mock(QualificationEvidenceParser::class.java),
            DiscoveryVerificationRules(ruleClock), Mockito.mock(DiscoveryVerificationCommitService::class.java),
            factory.getBeanProvider(RegistrationRecovery::class.java), ruleClock)

    @Test fun `formal ORCID name is queried alongside middle initial snapshot name`() {
        worker(DiscoveryVerificationProperties(), Mockito.mock(DiscoveryVerificationRepository::class.java)).useWorker { current ->
            val snapshot = ruleSnapshot().let { it.copy(profile = it.profile.copy(givenNames = "Duncan", familyNames = "S. Sutherland")) }
            val queries = current.searchQueries(snapshot, listOf(ruleFact("name", "Duncan Sutherland")))
            assertEquals(listOf("Duncan S. Sutherland", "Duncan Sutherland"), queries.map { it.name })
            val robert = snapshot.copy(profile = snapshot.profile.copy(givenNames = "Robert", familyNames = "H. Hadfield"))
            assertEquals(listOf("Robert H. Hadfield", "Robert Hadfield"), current.searchQueries(robert,
                listOf(ruleFact("name", "Robert Hadfield"))).map { it.name })
        }
    }
    @Test fun `disabled worker neither claims work nor calls optional recovery`() {
        val repo = Mockito.mock(DiscoveryVerificationRepository::class.java)
        val recovery = Mockito.mock(RegistrationRecovery::class.java)
        val factory = DefaultListableBeanFactory().also { it.registerSingleton("recovery", recovery) }
        worker(DiscoveryVerificationProperties(), repo, factory).useWorker { it.tick() }
        Mockito.verifyNoInteractions(repo, recovery)
    }

    @Test fun `enabled worker tolerates absent registration recovery and never creates an execution`() {
        val repo = Mockito.mock(DiscoveryVerificationRepository::class.java)
        worker(DiscoveryVerificationProperties(workerEnabled = true), repo).useWorker { it.tick() }
        Mockito.verify(repo).listProjectionDue(10)
        Mockito.verify(repo).claimDue(ruleClock.instant(), 120)
        Mockito.verifyNoMoreInteractions(repo)
    }

    @Test fun `bounded registration recovery uses the already existing optional seam`() {
        val repo = Mockito.mock(DiscoveryVerificationRepository::class.java)
        val recovery = Mockito.mock(RegistrationRecovery::class.java)
        val factory = DefaultListableBeanFactory().also { it.registerSingleton("recovery", recovery) }
        worker(DiscoveryVerificationProperties(workerEnabled = true), repo, factory).useWorker { it.tick() }
        Mockito.verify(recovery).recoverReserved(10)
        Mockito.verifyNoMoreInteractions(recovery)
    }

    @Test fun `reading current unblocked latch never claims or recovers work`() {
        val repo = Mockito.mock(DiscoveryVerificationRepository::class.java)
        val recovery = Mockito.mock(RegistrationRecovery::class.java)
        val factory = DefaultListableBeanFactory().also { it.registerSingleton("recovery", recovery) }
        worker(DiscoveryVerificationProperties(workerEnabled = true), repo, factory).useWorker { current ->
            repeat(20) { assertFalse(current.isAuthBlocked()) }
        }
        Mockito.verifyNoInteractions(repo, recovery)
    }

    @Test fun `new queries normalize aliases and narrow only one eligible identity institution`() {
        worker(DiscoveryVerificationProperties(), Mockito.mock(DiscoveryVerificationRepository::class.java)).useWorker { current ->
            val facts = listOf(ruleJob(source = com.weibo.talentintroduction.discovery.domain.AssertionSource.SELF),
                ruleFact("otherName", "ＡＤＡ, Lee"), ruleFact("otherName", "Known Alias"), ruleFact("otherName", "known alias"))
            val queries = current.searchQueries(ruleSnapshot(), facts)
            assertEquals(listOf("Ada Lee", "Known Alias", "Ada Lee"), queries.map { it.name })
            assertEquals("Example University", queries.last().institution!!.name)
            assertEquals(2, current.searchQueries(ruleSnapshot(), facts + ruleJob(
                source = com.weibo.talentintroduction.discovery.domain.AssertionSource.SELF, institution = "Other University")).size)
            assertEquals(2, current.searchQueries(ruleSnapshot(), facts).count { it.institution == null })
        }
    }
    private fun DiscoveryVerificationWorker.useWorker(action: (DiscoveryVerificationWorker) -> Unit) {
        try { action(this) } finally { close() }
    }

    private val gachon = "2Department of BioNano Technology, Gachon University, 1342 Seongnam-daero, Sujeong-gu, Seongnam-si 13120, Gyeonggi-do, Republic of Korea"

    @Test fun `new queries narrow once with the frozen subject and never with an ambiguous one`() {
        worker(DiscoveryVerificationProperties(), Mockito.mock(DiscoveryVerificationRepository::class.java)).useWorker { current ->
            val facts = listOf(ruleJob(institution = gachon))
            val context = DiscoveryInstitutionNormalizer.buildInputs(gachon,
                listOf(InstitutionEmploymentInput("e1", gachon)), emptyList())
            val queries = current.searchQueries(ruleSnapshot(), facts, context)
            assertEquals(listOf("Ada Lee", "Ada Lee"), queries.map { it.name })
            assertNull(queries.first().institution)
            assertEquals("Gachon University", queries.last().institution!!.name)

            val joint = "CNRS UMI 3288- NTU - THALES"
            val ambiguous = DiscoveryInstitutionNormalizer.buildInputs(joint,
                listOf(InstitutionEmploymentInput("e1", joint)), emptyList())
            assertNull(ambiguous.queryInstitution)
            assertEquals(1, current.searchQueries(ruleSnapshot(), listOf(ruleJob(institution = joint)), ambiguous).size)
            assertTrue(current.searchQueries(ruleSnapshot(), listOf(ruleJob(institution = joint)), ambiguous).all { it.institution == null })

            val rawOnly = DiscoveryInstitutionNormalizer.buildInputs(gachon, emptyList(), emptyList())
            assertNull(rawOnly.queryInstitution)
            assertEquals(1, current.searchQueries(ruleSnapshot(), emptyList(), rawOnly).size)
            assertNull(current.searchQueries(ruleSnapshot(), emptyList(), rawOnly).single().institution)
        }
    }

    @Test fun `unknown normalization version is accepted as data and rejected only by evaluation`() {
        val payload: VerificationWorkerEvidence = jacksonObjectMapper().readValue(
            """{"institutionNormalization":{"version":"INST_SUBJECT_V9","queryInstitution":"X","inputs":[]},
               "progress":{"stage":"DECIDE","schemaVersion":1}}""")
        assertEquals("INST_SUBJECT_V9", payload.institutionNormalization!!.version)
        val snapshot = ruleSnapshot()
        assertThrows(IllegalArgumentException::class.java) {
            DiscoveryVerificationRules(ruleClock).evaluate(snapshot, listOf(ruleJob()), null,
                institutionNormalization = payload.institutionNormalization)
        }
    }

    @Test fun `unparseable evidence persists the original JSON instead of an empty payload`() {
        val repo = Mockito.mock(DiscoveryVerificationRepository::class.java)
        val lease = lease("{not-json")
        worker(DiscoveryVerificationProperties(workerEnabled = true), repo).useWorker { it.process(lease) }
        val saved = Mockito.mockingDetails(repo).invocations.filter { it.method.name == "saveFailure" }
        assertEquals(1, saved.size)
        val result = saved.single().arguments[3] as VerificationResult
        assertEquals("{not-json", result.evidenceJson)
        assertEquals("""["VERIFICATION_FAILED"]""", result.reasonsJson)
        assertEquals(VerificationState.ERROR, result.state)
    }

    private fun lease(evidenceJson: String?): VerificationItem {
        val now = ruleClock.instant()
        return VerificationItem(id = 7, ownerExecutionId = 1, expertDocId = "doc", identityHash = "b".repeat(64),
            policyVersion = VerificationMode.ENFORCE.policyVersion, sourceName = "ORCID", sourceItemKey = "doc",
            batchNumber = 1, snapshotJson = "{}", factFingerprint = "c".repeat(64), state = VerificationState.RUNNING,
            outcome = null, qualification = VerificationQualification.NOT_EVALUATED, route = VerificationRoute.NONE,
            apolloEnteredAt = null, searchCompletedAt = null, candidateFoundAt = null, personMatchedAt = null,
            apolloPersonId = null, evidenceJson = evidenceJson, reasonsJson = null, requestCount = 0,
            transientAttempts = 0, generation = 0, leaseToken = "token", leaseUntil = now.plusSeconds(120),
            nextAttemptAt = null, admissionRevision = null, projectionState = VerificationProjection.NOT_REQUIRED,
            projectionError = null, activatedAt = now, completedAt = null, createdAt = now, updatedAt = now)
    }
}
