package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.AcademicFilterProperties
import com.weibo.talentintroduction.config.CandidateFilterProperties
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.EligibilityResult
import com.weibo.talentintroduction.expert.service.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

/** Shared synthetic ledger fixture for the authorized read/write/revalidation contracts. */
class AdmissionGateFixture {
    val mapper = jacksonObjectMapper().findAndRegisterModules()
    val repository = Mockito.mock(DiscoveryVerificationRepository::class.java)
    val filters = Mockito.mock(EligibilityFilterService::class.java).also {
        Mockito.`when`(it.getCandidateFilter()).thenReturn(CandidateFilterProperties(
            requireDoctoralDegree = true, enableAgeFilter = true, requireValidEmail = false))
        Mockito.`when`(it.getAcademicFilter()).thenReturn(AcademicFilterProperties())
    }
    val email = Mockito.mock(EmailValidationService::class.java)
    val eligibility = CandidateEligibilityService(filters, email)
    val rules = DiscoveryVerificationRules(ruleClock)
    val gate = DiscoveryVerificationAdmissionGate(repository, rules)
    val basePolicy = DiscoveryAdmissionPolicy(eligibility, filters, ruleClock)
    val policy = DiscoveryAdmissionPolicy(eligibility, filters, ruleClock, gate)
    val docId = "REAL-RAW-42"
    val profile = ruleSnapshot().profile.copy(esDocId = docId, researchFieldIds = listOf("22"), lastPublicationYear = 2026)
    val config = basePolicy.evaluate(profile).configSnapshot

    fun item(profile: ExpertProfile = this.profile, docId: String = this.docId,
        facts: List<VerificationEvidence> = listOf(ruleJob("Professor")), search: ApolloSearchEvidence? = null,
        mode: VerificationMode = VerificationMode.ENFORCE): VerificationItem {
        val snapshot = DiscoveryVerificationAdmissionGate.snapshot(docId, profile, config)
        val evidence = facts.map { it.copy(identityHash = snapshot.identityHash, factFingerprint = snapshot.factFingerprint) }
        val decision = rules.evaluate(snapshot, evidence, search)
        val now = ruleClock.instant()
        return VerificationItem(id = 1, ownerExecutionId = 1, expertDocId = docId, identityHash = snapshot.identityHash,
            policyVersion = mode.policyVersion, sourceName = "ORCID", sourceItemKey = docId, batchNumber = 1,
            snapshotJson = mapper.writeValueAsString(snapshot), factFingerprint = snapshot.factFingerprint,
            state = VerificationState.COMPLETED, outcome = decision.outcome, qualification = decision.qualification,
            route = decision.route, apolloEnteredAt = if (search == null) null else now,
            searchCompletedAt = if (search?.complete == true) now else null,
            candidateFoundAt = if (search?.candidates?.isNotEmpty() == true) now else null,
            personMatchedAt = if (decision.matchedPersonId == null) null else now, apolloPersonId = decision.matchedPersonId,
            evidenceJson = mapper.writeValueAsString(VerificationAdmissionEvidence(evidence, search)), reasonsJson = mapper.writeValueAsString(decision.reasonCodes),
            requestCount = 0, transientAttempts = 0, generation = 0, leaseToken = null, leaseUntil = null, nextAttemptAt = null,
            admissionRevision = null, projectionState = VerificationProjection.NOT_REQUIRED, projectionError = null,
            activatedAt = now, completedAt = now, createdAt = now, updatedAt = now)
    }
    fun load(vararg items: VerificationItem) {
        Mockito.`when`(repository.findItemsByDocIds(Mockito.anyCollection<String>() ?: emptyList())).thenReturn(items.toList())
    }
}

class DiscoveryVerificationAdmissionGateTest {
    @Test fun `gate recomputes version two and rejects a persisted result paired with old rules`() {
        val f = AdmissionGateFixture()
        val candidate = ApolloCandidate("person-v2", "Ada", "Lee", null, "Ada Lee", "Assistenzprofessor",
            VerificationInstitution("Other University"), headline = "Assistant Professor at Other University")
        val search = ApolloSearchEvidence(listOf(candidate), true, true, setOf("Ada Lee"),
            matchingVersion = 2, detailsComplete = true, institutionAliasVersion = 2)
        val original = f.item(facts = emptyList(), search = search)
        val payload = VerificationAdmissionEvidence(emptyList(), search, ruleVersion = 2)
        val current = original.copy(outcome = VerificationOutcome.APOLLO_CLEAR, route = VerificationRoute.APOLLO,
            apolloPersonId = candidate.id, personMatchedAt = ruleClock.instant(),
            evidenceJson = f.mapper.writeValueAsString(payload))
        f.load(current)
        assertTrue(f.policy.evaluate(f.docId, f.profile).eligible)
        f.load(current.copy(evidenceJson = f.mapper.writeValueAsString(payload.copy(ruleVersion = 1))))
        assertFalse(f.policy.evaluate(f.docId, f.profile).eligible)
        f.load(original)
        assertFalse(f.policy.evaluate(f.docId, f.profile).eligible)
    }
    @Test fun `gate honors persisted SELF policy version and leaves old decisions unchanged`() {
        val f = AdmissionGateFixture()
        val old = f.item(facts = listOf(ruleJob("Professor", AssertionSource.SELF)))
        f.load(old)
        assertFalse(f.policy.evaluate(f.docId, f.profile).eligible)
        val evidence = f.mapper.readValue(old.evidenceJson, VerificationAdmissionEvidence::class.java).copy(ruleVersion = 1)
        val changed = old.copy(outcome = VerificationOutcome.DIRECT_CLEAR, qualification = VerificationQualification.MET,
            route = VerificationRoute.DIRECT, evidenceJson = f.mapper.writeValueAsString(evidence))
        f.load(changed)
        assertTrue(f.policy.evaluate(f.docId, f.profile).eligible)
    }
    @Test fun `matrix replaces only old education age blockers and keeps soft facts`() {
        val f = AdmissionGateFixture()
        val cases = listOf(
            Triple(listOf(ruleJob("Professor")), null, true),
            Triple(listOf(ruleJob()), ruleSearch(), true),
            Triple(listOf(ruleJob("Professor")), ruleSearch(), true),
            Triple(listOf(ruleJob(), ruleFact("education", """{"role":"PhD awarded","completed":true}"""), ruleFact("birthDate", "1987-10-18")), null, true),
            Triple(listOf(ruleJob(), ruleFact("education", """{"role":"PhD awarded","completed":true}"""), ruleFact("birthDate", "1986-10-18")), ruleSearch(), false),
            Triple(listOf(ruleJob("Associate Professor"), ruleFact("birthDate", "1986-10-18")), null, true),
            Triple(listOf(ruleJob()), null, false),
            Triple(listOf(ruleJob("Professor", temporal = TemporalScope.HISTORICAL)), null, false),
            Triple(listOf(ruleJob(), ruleFact("education", """{"role":"PhD student","completed":false}""")), null, false),
            Triple(listOf(ruleJob()), ruleSearch(count = 2), false),
            Triple(listOf(ruleJob()), ruleSearch(institution = "Other University"), false),
            Triple(emptyList(), ruleSearch(masked = true), false),
            Triple(listOf(ruleJob()), ruleSearch(complete = false), false),
            Triple(listOf(ruleJob().copy(conflicts = listOf("conflict"))), ruleSearch(), false)
        )
        cases.forEach { (facts, search, admitted) ->
            f.load(f.item(facts = facts, search = search))
            val result = f.policy.evaluate(f.docId, f.profile)
            assertEquals(admitted, result.eligible, "$facts $search ${result.blockingReasons}")
            assertEquals(DiscoveryVerificationAdmissionGate.POLICY_VERSION, result.policyVersion)
            assertFalse(result.blockingReasons.any { it.code in DiscoveryVerificationAdmissionGate.REPLACED_CODES })
            assertTrue(result.hints.any { it.code == "COUNTRY_MISSING" })
        }
        f.load(f.item())
        val hard = f.policy.evaluate(f.docId, f.profile, EligibilityResult(false, listOf("NO_DOCTORAL_DEGREE", "AGE_EXCEEDED", "CHINESE_NATIONALITY")))
        assertEquals(listOf("CHINESE_NATIONALITY"), hard.blockingReasons.map { it.code })
    }

    @Test fun `unmanaged audit and non discovery retain old policy and no provider work`() {
        val f = AdmissionGateFixture()
        listOf(emptyArray(), arrayOf(f.item(mode = VerificationMode.AUDIT))).forEach { items ->
            f.load(*items)
            assertEquals(f.basePolicy.evaluate(f.profile), f.policy.evaluate(f.profile))
        }
        f.load()
        val ordinary = f.profile.copy(identityVerification = null, tags = null)
        Mockito.clearInvocations(f.repository)
        assertEquals(f.basePolicy.evaluate(ordinary), f.policy.evaluate(ordinary))
        Mockito.verify(f.repository).findItemsByDocIds(setOf(f.docId))
        Mockito.clearInvocations(f.repository)
        val withoutId = f.profile.copy(esDocId = null)
        assertEquals(f.basePolicy.evaluate(withoutId), f.policy.evaluate(withoutId))
        Mockito.verifyNoInteractions(f.repository)
    }

    @Test fun `loaded pending missing corrupt changed identity and changed facts never pass`() {
        val f = AdmissionGateFixture()
        val completed = f.item()
        val pending = completed.copy(state = VerificationState.QUEUED, outcome = null, completedAt = null)
        listOf(pending, completed.copy(evidenceJson = "{}"), completed.copy(snapshotJson = "{}"),
            completed.copy(evidenceJson = "not-json")).forEach { item ->
            f.load(item)
            assertFalse(f.policy.evaluate(f.docId, f.profile).eligible)
        }
        f.load(completed)
        assertEquals("IDENTITY_CHANGED", f.policy.evaluate(f.docId, f.profile.copy(email = "changed@example.invalid")).blockingReasons.last().code)
        assertEquals("FACT_CHANGED", f.policy.evaluate(f.docId, f.profile.copy(identityVerification = null, tags = null)).blockingReasons.last().code)
        assertEquals("FACT_CHANGED", f.policy.evaluate(f.docId, f.profile.copy(institution = "Changed University")).blockingReasons.last().code)
        assertEquals("FACT_CHANGED", f.policy.evaluate(f.docId, f.profile.copy(externalIds = """{"openAlexAuthorId":"A999"}""")).blockingReasons.last().code)
        f.load(completed.copy(evidenceJson = f.mapper.writeValueAsString(VerificationAdmissionEvidence(emptyList()))))
        assertFalse(f.policy.evaluate(f.docId, f.profile).eligible)
        f.load()
        assertEquals(f.basePolicy.evaluate(f.profile), f.policy.evaluate(f.docId, f.profile))
    }

    @Test fun `database failure propagates and loaded absence never rereads`() {
        val f = AdmissionGateFixture()
        f.load()
        val context = f.gate.findContext(f.docId, f.profile)
        Mockito.clearInvocations(f.repository)
        f.policy.evaluate(f.docId, f.profile, context)
        Mockito.verifyNoInteractions(f.repository)
        Mockito.`when`(f.repository.findItemsByDocIds(setOf(f.docId))).thenThrow(DataAccessResourceFailureException("offline"))
        assertThrows(DataAccessResourceFailureException::class.java) { f.policy.evaluate(f.docId, f.profile) }
    }

    @Test fun `five hundred real ids read evidence once with no per item reads`() {
        val f = AdmissionGateFixture()
        val profiles = (1..500).associate { "actual-$it" to f.profile.copy(esDocId = "actual-$it") }
        f.load(*profiles.map { (docId, profile) -> f.item(profile, docId) }.toTypedArray())
        val contexts = f.gate.findContexts(profiles)
        contexts.forEach { (id, context) -> assertTrue(f.policy.evaluate(id, profiles.getValue(id), context).eligible) }
        Mockito.verify(f.repository, Mockito.times(1)).findItemsByDocIds(profiles.keys)
        Mockito.verifyNoMoreInteractions(f.repository)
        assertThrows(IllegalArgumentException::class.java) { f.gate.findContexts(profiles + ("501" to f.profile)) }
    }

    @Test fun `repository batch uses one parameterized evidence SQL and preserves old reader contract`() {
        val jdbc = Mockito.mock(JdbcTemplate::class.java)
        val repo = DiscoveryVerificationRepository(jdbc)
        val ids = (1..500).map { "real-$it" }
        assertTrue(repo.findItemsByDocIds(ids).isEmpty())
        val invocation = Mockito.mockingDetails(jdbc).invocations.single()
        val sql = invocation.arguments[0] as String
        assertTrue(sql.startsWith("SELECT * FROM discovery_verification_item WHERE expert_doc_id IN ("))
        assertEquals(500, sql.count { it == '?' })
        assertTrue(invocation.arguments[1] is RowMapper<*>)
        assertEquals(ids, invocation.arguments.drop(2))
        assertThrows(IllegalArgumentException::class.java) { repo.findItemsByDocIds(ids + "501") }
        assertTrue(repo.findItemsByDocIds(emptyList()).isEmpty())
        assertEquals(1, Mockito.mockingDetails(jdbc).invocations.size)
    }
}
