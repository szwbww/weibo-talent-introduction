package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

val ruleClock: Clock = Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC)
fun ruleSnapshot(): VerificationSnapshot {
    val proof = DiscoveryIdentity.verified("ada@example.invalid", "Ada", "Lee", "ORCID_RECORD_SHA256:" + "a".repeat(64), "0000-0002-1825-0097", null)
    return VerificationSnapshot("b".repeat(64), "c".repeat(64), ExpertProfile(orcidId = "0000-0002-1825-0097", email = "ada@example.invalid",
        givenNames = "Ada", familyNames = "Lee", country = null, keyword = null, employment = null,
        institution = "Example University", identityVerification = proof))
}
fun ruleFact(field: String, value: String, source: AssertionSource = AssertionSource.ORGANIZATION,
    temporal: TemporalScope = TemporalScope.CURRENT) = VerificationEvidence("b".repeat(64), "c".repeat(64), "ORCID",
    "https://pub.orcid.org/v3.0/0000-0002-1825-0097/employments", ruleClock.instant(), "d".repeat(64), field, value, source, temporal)
fun ruleJob(role: String = "Researcher", source: AssertionSource = AssertionSource.ORGANIZATION,
    temporal: TemporalScope = TemporalScope.CURRENT, institution: String = "Example University") = ruleFact("employment",
    jacksonObjectMapper().writeValueAsString(mapOf("orcid" to "0000-0002-1825-0097", "role" to role, "institution" to institution)), source, temporal)
fun ruleSearch(masked: Boolean = false, count: Int = 1, complete: Boolean = true, institution: String = "Example University") = ApolloSearchEvidence(
    (1..count).map { ApolloCandidate("person-$it", "Ada", if (masked) null else "Lee", if (masked) "L**" else null,
        if (masked) null else "Ada Lee", "Professor", VerificationInstitution(institution)) }, complete, complete, setOf("Ada Lee"))

class DiscoveryVerificationRulesTest {
    private val rules = DiscoveryVerificationRules(ruleClock)
    private val mapper = jacksonObjectMapper()
    @Test fun `fixed annotated fixtures replay identically with auditable fields`() {
        val fixtures = jacksonObjectMapper().readTree(javaClass.getResourceAsStream("/discovery/apollo-verification-cases.json"))
        fixtures.path("cases").forEach { case ->
            val facts = mutableListOf<VerificationEvidence>()
            if (case.path("job").asBoolean(true)) facts += ruleJob(case.path("role").asText("Researcher"),
                AssertionSource.valueOf(case.path("source").asText("ORGANIZATION")), TemporalScope.valueOf(case.path("scope").asText("CURRENT")))
            if (case.path("doctorate").asBoolean()) facts += ruleFact("education", """{"role":"PhD awarded","completed":true}""")
            if (case.has("age")) facts += ruleFact("birthDate", "${2026 - case.path("age").asInt()}-10-18")
            val search = if (case.path("apollo").asBoolean()) ruleSearch(case.path("masked").asBoolean(), case.path("count").asInt(1), case.path("complete").asBoolean(true), case.path("institution").asText("Example University")) else null
            val first = rules.evaluate(ruleSnapshot(), facts, search)
            assertEquals(first, rules.evaluate(ruleSnapshot(), facts, search))
            assertEquals(case.path("qualification").asText(), first.qualification.name, case.path("caseId").asText())
            assertEquals(case.path("outcome").asText(), first.outcome.name, case.path("caseId").asText())
            assertTrue(first.reasonCodes.contains(case.path("reason").asText()), case.path("caseId").asText())
            println("caseId=${case.path("caseId").asText()} route=${first.route} qualification=${first.qualification} reasons=${first.reasonCodes} evidence=${first.evidence.map { it.field + ":" + it.assertionSource + ":" + it.temporalScope }} missing=${first.missingFields}")
        }
        assertTrue(fixtures.path("apolloShapes").path("masked").path("people").first().has("last_name_obfuscated"))
        assertFalse(fixtures.path("apolloShapes").path("masked").path("people").first().has("email"))
    }
    @Test fun `strict name normalization allows known middle omission but no invented variants`() {
        assertTrue(rules.sameName("Ada Lee", "ＡＤＡ  M. Lee"))
        assertFalse(rules.sameName("Ada X Lee", "Ada Lee"))
        assertFalse(rules.sameName("A Lee", "Ada Lee"))
        assertFalse(rules.sameName("Ada Lin", "Ada Lee"))
        assertFalse(rules.sameInstitution(VerificationInstitution("Example University"), VerificationInstitution("Example Technical University")))
        assertTrue(rules.sameInstitution(VerificationInstitution("Example University", "ROR:1"), VerificationInstitution("Different label", "ROR:1")))
        assertFalse(rules.sameInstitution(VerificationInstitution("Example University", "ROR:1"), VerificationInstitution("Example University", "ROR:2")))
        assertTrue(rules.sameInstitution(VerificationInstitution("Example University", aliases = setOf("EU")), VerificationInstitution("EU")))
    }
    @Test fun `identity proof and field fingerprint cannot be repaired by Apollo`() {
        val snapshot = ruleSnapshot()
        val bad = snapshot.copy(profile = snapshot.profile.copy(email = "other@example.invalid"))
        assertEquals("IDENTITY_EVIDENCE_MISSING", rules.evaluate(bad, listOf(ruleJob()), ruleSearch()).reasonCodes.single())
        assertEquals(VerificationQualification.CONFLICT, rules.evaluate(snapshot, listOf(ruleJob().copy(factFingerprint = "e".repeat(64))), ruleSearch()).qualification)
        assertEquals(VerificationQualification.CONFLICT, rules.evaluate(snapshot, listOf(ruleJob().copy(conflicts = listOf("contradictory employment"))), ruleSearch()).qualification)
        assertEquals(VerificationQualification.CONFLICT, rules.evaluate(snapshot, listOf(ruleFact("name", "Other Person")), ruleSearch()).qualification)
        val original = jacksonObjectMapper().writeValueAsString(snapshot)
        rules.evaluate(snapshot, listOf(ruleJob()), ruleSearch())
        assertEquals(original, jacksonObjectMapper().writeValueAsString(snapshot))
    }
    @Test fun `professor title exact allowlist rejects historical and incidental references`() {
        listOf("Professor", "Prof.", "Assistant Professor", "Associate Professor", "Teaching Professor", "Adjunct Professor", "教授", "副教授").forEach { title ->
            assertEquals(VerificationOutcome.DIRECT_CLEAR, rules.evaluate(ruleSnapshot(), listOf(ruleJob(title))).outcome, title)
        }
        listOf("Professor Emeritus", "Former Professor", "PhD student", "Researcher in Professor Lee lab", "Assistant to Professor").forEach { title ->
            assertEquals(VerificationQualification.UNKNOWN, rules.evaluate(ruleSnapshot(), listOf(ruleJob(title))).qualification, title)
        }
        assertEquals(VerificationQualification.UNKNOWN, rules.evaluate(ruleSnapshot(), emptyList()).qualification)
    }
    @Test fun `birth conflict aliases and Apollo ID conflict are explicit not extra independent support`() {
        val facts = listOf(ruleJob(), ruleFact("birthDate", "1987-10-18"), ruleFact("birthDate", "1986-10-18"))
        assertEquals(VerificationQualification.CONFLICT, rules.evaluate(ruleSnapshot(), facts, ruleSearch()).qualification)
        assertEquals(listOf("APOLLO_ID_CONFLICT"), rules.evaluate(ruleSnapshot(), listOf(ruleJob()), ruleSearch().copy(conflictingIds = setOf("person-1"))).reasonCodes)
        assertEquals(VerificationOutcome.PROTECTED, rules.evaluate(ruleSnapshot(), emptyList(), protected = true).outcome)
        val self = ruleJob("Professor", AssertionSource.SELF)
        assertEquals(VerificationQualification.UNKNOWN, rules.evaluate(ruleSnapshot(), listOf(self, self)).qualification)
    }
    @Test fun `matching primary name cannot mask conflicting primary name while aliases remain supported`() {
        val facts = listOf(ruleJob("Professor"), ruleFact("name", "Ada Lee"), ruleFact("name", "Other Person"))
        listOf(null, ruleSearch()).forEach { search ->
            val result = rules.evaluate(ruleSnapshot(), facts, search)
            assertEquals(VerificationQualification.CONFLICT, result.qualification)
            assertEquals(VerificationOutcome.PENDING, result.outcome)
            assertEquals(listOf("NAME_CONFLICT"), result.reasonCodes)
        }
        assertEquals(VerificationOutcome.DIRECT_CLEAR, rules.evaluate(ruleSnapshot(),
            listOf(ruleJob("Professor"), ruleFact("name", "ＡＤＡ, Lee"), ruleFact("otherName", "Other Person"))).outcome)
        val snapshot = ruleSnapshot()
        val proof = DiscoveryIdentity.verified("ada@example.invalid", "Ada M", "Lee",
            "ORCID_RECORD_SHA256:" + "a".repeat(64), "0000-0002-1825-0097", null)
        val middleName = snapshot.copy(profile = snapshot.profile.copy(givenNames = "Ada M", identityVerification = proof))
        assertEquals(VerificationOutcome.DIRECT_CLEAR, rules.evaluate(middleName,
            listOf(ruleJob("Professor"), ruleFact("name", "Ada Lee"), ruleFact("name", "Ada M Lee"))).outcome)
        val aliasCandidate = ruleSearch().candidates.single().copy(firstName = "Other", lastName = "Person", fullName = "Other Person")
        assertEquals(VerificationOutcome.APOLLO_UNCLEAR, rules.evaluate(snapshot,
            listOf(ruleJob(), ruleFact("name", "Ada Lee"), ruleFact("otherName", "Other Person")),
            ruleSearch().copy(candidates = listOf(aliasCandidate))).outcome)
    }
    @Test fun `contradictory ORCID is identical for full and split explicit names`() {
        val search = ruleSearch()
        val wrong = search.candidates.single().copy(id = "wrong-orcid", orcid = "0000-0001-5109-3700")
        val results = listOf(wrong, wrong.copy(fullName = null)).map { candidate ->
            rules.evaluate(ruleSnapshot(), listOf(ruleJob()), search.copy(candidates = search.candidates + candidate))
        }
        assertEquals(results.first(), results.last())
        results.forEach { result ->
            assertEquals(VerificationQualification.CONFLICT, result.qualification)
            assertEquals(VerificationOutcome.PENDING, result.outcome)
            assertEquals(listOf("ORCID_CONFLICT"), result.reasonCodes)
        }
    }
    @Test fun `self current employment confirms identity but never qualification even with self doctorate and title`() {
        val facts = listOf(ruleJob("Professor", AssertionSource.SELF),
            ruleFact("education", """{"role":"PhD awarded","completed":true}""", AssertionSource.SELF),
            ruleFact("birthDate", "1987-10-18", AssertionSource.SELF))
        val result = rules.evaluate(ruleSnapshot(), facts, ruleSearch())
        assertEquals(VerificationQualification.UNKNOWN, result.qualification)
        assertEquals(VerificationOutcome.APOLLO_UNCLEAR, result.outcome)
        assertEquals("person-1", result.matchedPersonId)
        assertEquals(listOf("IDENTITY_AMBIGUOUS"), rules.evaluate(ruleSnapshot(), facts, ruleSearch(count = 2)).reasonCodes)
        assertEquals(listOf("INSTITUTION_CONFLICT"), rules.evaluate(ruleSnapshot(),
            listOf(ruleJob(source = AssertionSource.SELF, institution = "Different University")), ruleSearch()).reasonCodes)
    }

    @Test fun `identity employment rejects unbound unknown historical and mismatched evidence`() {
        val self = ruleJob(source = AssertionSource.SELF)
        val invalid = listOf(self.copy(provider = "PAPER"), self.copy(assertionSource = AssertionSource.UNKNOWN),
            self.copy(temporalScope = TemporalScope.HISTORICAL), self.copy(temporalScope = TemporalScope.UNKNOWN),
            self.copy(identityHash = "e".repeat(64)), self.copy(factFingerprint = "e".repeat(64)),
            self.copy(value = self.value.replace("0000-0002-1825-0097", "0000-0001-5109-3700")),
            ruleJob(source = AssertionSource.SELF, institution = ""))
        invalid.forEach { assertTrue(rules.identityEmployments(ruleSnapshot(), listOf(it)).isEmpty(), it.toString()) }
        val snapshot = ruleSnapshot().let { it.copy(profile = it.profile.copy(identityVerification = null)) }
        assertTrue(rules.identityEmployments(snapshot, listOf(self)).isEmpty())
        assertEquals(2, rules.identityEmployments(ruleSnapshot(), listOf(self,
            ruleJob(source = AssertionSource.SELF, institution = "Second University"))).size)
    }

    private val gachon = "2Department of BioNano Technology, Gachon University, 1342 Seongnam-daero, Sujeong-gu, Seongnam-si 13120, Gyeonggi-do, Republic of Korea"

    private fun newContext(profileInstitution: String?, jobInstitution: String, candidateInstitution: String?,
        snapshot: VerificationSnapshot = ruleSnapshot(),
        evidence: List<VerificationEvidence> = listOf(ruleJob(institution = jobInstitution)),
        search: ApolloSearchEvidence = ruleSearch(institution = candidateInstitution ?: "Example University")) =
        DiscoveryInstitutionNormalizer.build(profileInstitution, rules.identityEmployments(snapshot, evidence), search.candidates)

    @Test fun `new context extracts the subject before RAW ORCID and Apollo compare`() {
        val snapshot = ruleSnapshot().let { it.copy(profile = it.profile.copy(institution = gachon)) }
        val evidence = listOf(ruleJob(institution = gachon).copy(identityHash = snapshot.identityHash,
            factFingerprint = snapshot.factFingerprint))
        val search = ruleSearch(institution = "Gachon University")
        val context = newContext(gachon, gachon, "Gachon University", snapshot, evidence, search)
        assertEquals("Gachon University", context.queryInstitution)
        val decision = rules.evaluate(snapshot, evidence, search, institutionNormalization = context)
        assertEquals(VerificationOutcome.APOLLO_UNCLEAR, decision.outcome)
        assertEquals("person-1", decision.matchedPersonId)
        assertTrue(decision.reasonCodes.contains("APOLLO_PERSON_MATCHED"))
    }

    @Test fun `new context never folds campuses hospitals or joint names into one institution`() {
        val campus = ruleSnapshot().let { it.copy(profile = it.profile.copy(institution = "University of California, Los Angeles")) }
        val campusFacts = listOf(ruleJob(institution = "University of California, Berkeley")
            .copy(identityHash = campus.identityHash, factFingerprint = campus.factFingerprint))
        val campusContext = newContext(campus.profile.institution, "University of California, Berkeley",
            "University of California, Los Angeles", campus, campusFacts,
            ruleSearch(institution = "University of California, Los Angeles"))
        assertEquals(listOf("INSTITUTION_CONFLICT"),
            rules.evaluate(campus, campusFacts, ruleSearch(institution = "University of California, Los Angeles"),
                institutionNormalization = campusContext).reasonCodes)

        val hospital = ruleSnapshot().let { it.copy(profile = it.profile.copy(institution = "Renji Hospital")) }
        val hospitalFacts = listOf(ruleJob(institution = "Shanghai Jiao Tong University")
            .copy(identityHash = hospital.identityHash, factFingerprint = hospital.factFingerprint))
        val hospitalContext = newContext(hospital.profile.institution, "Shanghai Jiao Tong University",
            "Renji Hospital", hospital, hospitalFacts, ruleSearch(institution = "Renji Hospital"))
        assertEquals(listOf("INSTITUTION_CONFLICT"),
            rules.evaluate(hospital, hospitalFacts, ruleSearch(institution = "Renji Hospital"),
                institutionNormalization = hospitalContext).reasonCodes)

        val joint = ruleSnapshot().let { it.copy(profile = it.profile.copy(institution = "CNRS UMI 3288- NTU - THALES")) }
        val jointFacts = listOf(ruleJob(institution = "CNRS UMI 3288- NTU - THALES")
            .copy(identityHash = joint.identityHash, factFingerprint = joint.factFingerprint))
        val jointSearch = ruleSearch(institution = "CNRS")
        val jointContext = newContext(joint.profile.institution, "CNRS UMI 3288- NTU - THALES", "CNRS", joint, jointFacts, jointSearch)
        assertNull(jointContext.queryInstitution)
        val decision = rules.evaluate(joint, jointFacts, jointSearch, institutionNormalization = jointContext)
        assertEquals(VerificationQualification.UNKNOWN, decision.qualification)
        assertEquals(VerificationOutcome.PENDING, decision.outcome)
        assertTrue(decision.reasonCodes.contains("IDENTITY_EVIDENCE_MISSING"))
        assertTrue(decision.missingFields.contains("institutionSubject"))
    }

    @Test fun `country suffixed campuses and contextual standard ids keep identity boundaries`() {
        val countrySuffixed = "University of California, Los Angeles, USA"
        val la = ruleSnapshot().copy(profile = ruleSnapshot().profile.copy(institution = countrySuffixed))
        val berkeleyFacts = listOf(ruleJob(institution = "University of California, Berkeley, USA")
            .copy(identityHash = la.identityHash, factFingerprint = la.factFingerprint))
        val berkeleySearch = ruleSearch(institution = "University of California, Berkeley, USA")
        val berkeleyContext = DiscoveryInstitutionNormalizer.build(la.profile.institution,
            rules.identityEmployments(la, berkeleyFacts), berkeleySearch.candidates)
        val crossCampus = rules.evaluate(la, berkeleyFacts, berkeleySearch, institutionNormalization = berkeleyContext)
        assertNotEquals(VerificationOutcome.APOLLO_UNCLEAR, crossCampus.outcome)
        assertEquals(listOf("INSTITUTION_CONFLICT"), crossCampus.reasonCodes)

        val base = ruleSnapshot()
        fun factWithId(id: String) = ruleJob(institution = "Example University").let {
            it.copy(identityHash = base.identityHash, factFingerprint = base.factFingerprint,
                value = mapper.writeValueAsString(mapOf("orcid" to "0000-0002-1825-0097", "role" to "Researcher",
                    "institution" to "Example University", "standardId" to id)))
        }
        fun searchWith(institution: VerificationInstitution) = ruleSearch(institution = "Example University").let {
            it.copy(candidates = listOf(it.candidates.single().copy(institution = institution)))
        }
        val conflicting = searchWith(VerificationInstitution("Example University", "ROR:2"))
        val conflictingContext = DiscoveryInstitutionNormalizer.build("Example University",
            rules.identityEmployments(base, listOf(factWithId("ROR:1"))), conflicting.candidates)
        val conflict = rules.evaluate(base, listOf(factWithId("ROR:1")), conflicting,
            institutionNormalization = conflictingContext)
        assertEquals(VerificationOutcome.PENDING, conflict.outcome)
        assertEquals(listOf("IDENTITY_NOT_MATCHED"), conflict.reasonCodes)

        val same = searchWith(VerificationInstitution("Example University", "ROR:1"))
        val sameContext = DiscoveryInstitutionNormalizer.build("Example University",
            rules.identityEmployments(base, listOf(factWithId("ROR:1"))), same.candidates)
        assertEquals(VerificationOutcome.APOLLO_UNCLEAR,
            rules.evaluate(base, listOf(factWithId("ROR:1")), same, institutionNormalization = sameContext).outcome)

        val aliasFacts = listOf(ruleJob(institution = "Example University")
            .copy(identityHash = base.identityHash, factFingerprint = base.factFingerprint))
        val aliasSearch = searchWith(VerificationInstitution("Apollo University", aliases = setOf("Example University")))
        val aliasContext = DiscoveryInstitutionNormalizer.build("Example University",
            rules.identityEmployments(base, aliasFacts), aliasSearch.candidates)
        assertEquals(VerificationOutcome.APOLLO_UNCLEAR,
            rules.evaluate(base, aliasFacts, aliasSearch, institutionNormalization = aliasContext).outcome)
    }

    @Test fun `unclear subject is missing evidence and a tampered or unknown context is rejected`() {
        val unresolved = ruleSnapshot().let { it.copy(profile = it.profile.copy(institution = "ACHISINA")) }
        val facts = listOf(ruleJob().copy(identityHash = unresolved.identityHash, factFingerprint = unresolved.factFingerprint))
        val context = newContext("ACHISINA", "Example University", "Example University", unresolved, facts)
        val decision = rules.evaluate(unresolved, facts, ruleSearch(), institutionNormalization = context)
        assertEquals(VerificationOutcome.PENDING, decision.outcome)
        assertEquals(listOf("IDENTITY_EVIDENCE_MISSING"), decision.reasonCodes)
        assertTrue(decision.missingFields.contains("institutionSubject"))

        val tampered = context.copy(inputs = context.inputs.mapIndexed { index, input ->
            if (index == 0) input.copy(subjects = listOf("Example University"), status = "RESOLVED",
                reason = "SUBJECT_EXTRACTED") else input
        })
        assertThrows(IllegalArgumentException::class.java) {
            rules.evaluate(unresolved, facts, ruleSearch(), institutionNormalization = tampered)
        }
        assertThrows(IllegalArgumentException::class.java) {
            rules.evaluate(unresolved, facts, ruleSearch(), institutionNormalization = context.copy(version = "INST_SUBJECT_V2"))
        }
    }

    @Test fun `optional frozen verification replay compares null and explicit contexts deterministically`() {
        val input = System.getProperty("institutionReplayInput")
        val output = System.getProperty("institutionReplayOutput")
        if (input == null && output == null) return
        require(input != null && output != null) { "Both institutionReplayInput and institutionReplayOutput are required" }
        val directory = java.nio.file.Paths.get(input)
        require(java.nio.file.Files.isRegularFile(directory.resolve("manifest.json"))) {
            "institutionReplayInput must be a frozen replay directory with manifest.json"
        }
        val frozenMapper = jacksonObjectMapper().findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        val manifest = frozenMapper.readTree(java.nio.file.Files.readAllBytes(directory.resolve("manifest.json")))
        require(manifest.path("schemaVersion").asInt() == 1) { "Unsupported frozen manifest schema" }
        val beforeBytes = java.nio.file.Files.readAllBytes(directory.resolve("before.json"))
        assertEquals(manifest.path("beforeSha256").asText(), replaySha(beforeBytes), "Frozen input hash mismatch")
        val before = frozenMapper.readTree(beforeBytes)
        val evaluated = manifest.path("evaluatedAt")
        val evaluatedAt = if (evaluated.isNumber) {
            val seconds = evaluated.asDouble()
            val whole = seconds.toLong()
            Instant.ofEpochSecond(whole, ((seconds - whole) * 1_000_000_000).toLong())
        } else Instant.parse(evaluated.asText())
        val clock = Clock.fixed(evaluatedAt, ZoneOffset.UTC)
        val replayRules = DiscoveryVerificationRules(clock)
        fun group(contextual: Boolean) = before.path("items").map { item ->
            val snapshot = frozenMapper.readValue(item.path("snapshotJson").asText(), VerificationSnapshot::class.java)
            val payload = frozenMapper.readValue(item.path("evidenceJson").asText("{}"), VerificationWorkerEvidence::class.java)
            val context = if (contextual) DiscoveryInstitutionNormalizer.build(snapshot.profile.institution,
                replayRules.identityEmployments(snapshot, payload.evidence), payload.search?.candidates) else null
            val decision = replayRules.evaluate(snapshot, payload.evidence, payload.search,
                institutionNormalization = context)
            linkedMapOf<String, Any?>("itemId" to item.path("id").asLong(), "outcome" to decision.outcome.name,
                "qualification" to decision.qualification.name, "route" to decision.route.name,
                "matched" to (decision.matchedPersonId != null), "reasons" to decision.reasonCodes,
                "missing" to decision.missingFields, "institution" to context?.let { built ->
                    mapOf("queryInstitution" to built.queryInstitution, "inputs" to built.inputs.map {
                        mapOf("source" to it.source, "status" to it.status, "subjects" to it.subjects, "reason" to it.reason)
                    })
                })
        }
        val old = group(false)
        val fixed = group(true)
        assertEquals(old, group(false), "Old group must be deterministic")
        assertEquals(fixed, group(true), "New group must be deterministic")
        val negative = old.indices.filter {
            old[it]["matched"] == true && fixed[it]["matched"] != true
        }
        require(!java.nio.file.Files.exists(java.nio.file.Paths.get(output))) { "Replay output must not already exist" }
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get(output))
        java.nio.file.Files.writeString(java.nio.file.Paths.get(output).resolve("replay.json"),
            frozenMapper.writeValueAsString(mapOf("schemaVersion" to 1, "input" to input,
                "inputSha256" to manifest.path("beforeSha256").asText(),
                "evaluatedAt" to manifest.path("evaluatedAt").asText(), "denominator" to old.size,
                "groups" to mapOf("old" to old, "fixed" to fixed),
                "negativeChanges" to negative.map { old[it]["itemId"] },
                "historicalInput" to manifest.path("oldInputStatus").asText("UNAVAILABLE"),
                "note" to "New context is built in memory only; no item or admission is upgraded.")))
        println("institutionReplay denominator=${old.size} negativeChanges=${negative.size} output=$output")
    }

    @Test fun `later empty query preserves incomplete candidate union and conflict protection`() {
        val broad = ApolloSearchQuery("Ada Lee")
        val institution = ApolloSearchQuery("Ada Lee", institution = VerificationInstitution("Example University"))
        val person = ruleSearch().candidates.single()
        val truncated = ApolloSearchEvidence(matchingVersion = 2).merge(
            ApolloSearchPage(listOf(person), 600, 5), broad, broad = true)
        val final = truncated.merge(ApolloSearchPage(emptyList(), 0, 1), institution, broad = false,
            allQueriesComplete = false).copy(detailsComplete = true)
        assertFalse(final.complete); assertFalse(final.broadComplete)
        assertEquals(listOf(person), final.candidates)
        assertEquals(listOf("SEARCH_INCOMPLETE"), rules.evaluate(ruleSnapshot(), emptyList(), final).reasonCodes)
        val conflicting = final.merge(ApolloSearchPage(listOf(person.copy(title = "Researcher")), 1, 1),
            institution, broad = false)
        assertEquals(setOf(person.id), conflicting.conflictingIds)
        assertEquals(VerificationOutcome.PENDING, rules.evaluate(ruleSnapshot(), emptyList(), conflicting).outcome)
        val complete = conflicting.copy(complete = true, broadComplete = true)
        assertEquals(listOf("APOLLO_ID_CONFLICT"), rules.evaluate(ruleSnapshot(), emptyList(), complete).reasonCodes)
    }

    @Test fun `legacy complete facts survive merges without optional diagnostics`() {
        val old = ruleSearch().copy(matchingVersion = 2, detailsComplete = true)
        val merged = old.merge(ApolloSearchPage(emptyList(), 0, 1), ApolloSearchQuery("Ada Lee"), broad = false)
        assertTrue(merged.complete); assertTrue(merged.broadComplete)
        assertEquals(rules.evaluate(ruleSnapshot(), emptyList(), old), rules.evaluate(ruleSnapshot(), emptyList(), merged))
    }

    private fun replaySha(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
