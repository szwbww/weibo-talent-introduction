package com.weibo.talentintroduction.discovery.domain

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.config.DiscoveryVerificationPropertiesConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Instant
import java.time.Duration
import java.util.TimeZone

class DiscoveryVerificationContractTest {
    private val now = Instant.parse("2026-10-18T10:00:00.123Z")

    @Test
    fun `disabled configuration starts without keys or recovery implementation`() {
        ApplicationContextRunner().withUserConfiguration(DiscoveryVerificationPropertiesConfig::class.java).run { context ->
            assertNull(context.startupFailure)
            val properties = context.getBean(DiscoveryVerificationProperties::class.java)
            assertFalse(properties.enabled); assertFalse(properties.workerEnabled)
            assertEquals(VerificationMode.AUDIT, properties.mode)
            assertEquals(10, properties.requestsPerMinute); assertEquals(500, properties.requestsPerDay)
            assertEquals(12, properties.maxRequestsPerItem); assertEquals(5, properties.maxPagesPerQuery)
            assertTrue(context.getBeansOfType(RegistrationRecovery::class.java).isEmpty())
            assertEquals("", properties.apolloApiKey)
        }
        assertFalse(DiscoveryVerificationProperties(apolloApiKey = "secret-one", orcidAccessToken = "secret-two").toString().contains("secret"))
    }

    @Test
    fun `invalid configuration cannot start`() {
        listOf("requests-per-day=0", "mode=UNMANAGED", "lease-seconds=20", "max-pages-per-query=501").forEach { field ->
            ApplicationContextRunner().withUserConfiguration(DiscoveryVerificationPropertiesConfig::class.java)
                .withPropertyValues("talent-introduction.discovery-verification.$field").run { assertNotNull(it.startupFailure) }
        }
        assertThrows(IllegalArgumentException::class.java) { DiscoveryVerificationProperties(httpReadTimeout = Duration.ZERO) }
    }

    @Test
    fun `outcome matrix is explicit and incomplete states cannot admit`() {
        fun result(outcome: VerificationOutcome?, qualification: VerificationQualification, route: VerificationRoute,
                   state: VerificationState = VerificationState.COMPLETED, matched: Instant? = null,
                   projection: VerificationProjection = VerificationProjection.NOT_REQUIRED, revision: Long? = null) =
            VerificationResult(state, outcome, qualification, route, "{}", "[]", personMatchedAt = matched,
                admissionRevision = revision, projectionState = projection)
        result(VerificationOutcome.DIRECT_CLEAR, VerificationQualification.MET, VerificationRoute.DIRECT)
        result(VerificationOutcome.APOLLO_CLEAR, VerificationQualification.MET, VerificationRoute.APOLLO, matched = now)
        result(VerificationOutcome.APOLLO_UNCLEAR, VerificationQualification.UNKNOWN, VerificationRoute.APOLLO, matched = now)
        result(VerificationOutcome.PENDING, VerificationQualification.NOT_MET, VerificationRoute.NONE)
        result(VerificationOutcome.PROTECTED, VerificationQualification.NOT_EVALUATED, VerificationRoute.NONE)
        result(null, VerificationQualification.NOT_EVALUATED, VerificationRoute.NONE, VerificationState.ERROR)
        assertThrows(IllegalArgumentException::class.java) { result(null, VerificationQualification.UNKNOWN, VerificationRoute.NONE) }
        assertThrows(IllegalArgumentException::class.java) { result(VerificationOutcome.DIRECT_CLEAR, VerificationQualification.UNKNOWN, VerificationRoute.DIRECT) }
        assertThrows(IllegalArgumentException::class.java) { result(VerificationOutcome.APOLLO_UNCLEAR, VerificationQualification.UNKNOWN, VerificationRoute.APOLLO) }
        assertThrows(IllegalArgumentException::class.java) { result(VerificationOutcome.PENDING, VerificationQualification.UNKNOWN, VerificationRoute.NONE, projection = VerificationProjection.PROJECTED, revision = 1) }
        assertThrows(IllegalArgumentException::class.java) { result(VerificationOutcome.DIRECT_CLEAR, VerificationQualification.MET, VerificationRoute.DIRECT, VerificationState.QUEUED) }
    }

    @Test
    fun `bounded identity policy and evidence serialization`() {
        assertNotEquals(VerificationMode.AUDIT.policyVersion, VerificationMode.ENFORCE.policyVersion)
        assertTrue(VerificationMode.values().all { it.policyVersion.length <= 32 })
        assertThrows(IllegalArgumentException::class.java) { VerificationRun(0, VerificationMode.AUDIT, VerificationMode.AUDIT.policyVersion, false, "{}", createdAt = now, updatedAt = now) }
        assertThrows(IllegalArgumentException::class.java) { verificationText("x".repeat(129), 128) }
        assertThrows(IllegalArgumentException::class.java) { verificationHash("not-hash") }
        val evidence = VerificationEvidence("a".repeat(64), "b".repeat(64), "ORCID", "https://example.invalid/record", now,
            "c".repeat(64), "employment", "Professor", AssertionSource.ORGANIZATION, TemporalScope.CURRENT)
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        assertEquals(evidence, mapper.readValue(mapper.writeValueAsString(evidence), VerificationEvidence::class.java))
        assertThrows(Exception::class.java) { mapper.readValue(mapper.writeValueAsString(evidence).replace("CURRENT", "INVALID"), VerificationEvidence::class.java) }
        assertThrows(IllegalArgumentException::class.java) { evidence.copy(sourceUrl = "") }
        assertThrows(IllegalArgumentException::class.java) { VerificationProgress(stage = "DONE") }
    }

    @Test
    fun `UTC conversion is independent from machine timezone`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            assertEquals(now, now.verificationUtc().verificationInstant())
            assertEquals(10, now.verificationUtc().hour)
        } finally { TimeZone.setDefault(original) }
    }
    @Test fun `legacy JSON defaults remain ordinary while recheck spec round trips independently`() {
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val run = VerificationRun(1, VerificationMode.AUDIT, VerificationMode.AUDIT.policyVersion, false, "{}",
            createdAt = now, updatedAt = now)
        val old = mapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(run).also { it.remove("recheckJson") }
        assertNull(mapper.treeToValue(old, VerificationRun::class.java).recheckJson)
        val spec = RecheckSpec("operator", "request-1", """{"docIds":["one"]}""", RecheckEvidenceMode.REUSE,
            now, "code-sha", "parser-version")
        val recheck = RecheckRun(spec)
        assertEquals(recheck, mapper.readValue(mapper.writeValueAsString(recheck), RecheckRun::class.java))
        assertThrows(IllegalArgumentException::class.java) { recheck.copy(schemaVersion = 2) }
        assertThrows(IllegalArgumentException::class.java) { RecheckControl(RecheckPhase.READY) }
        assertThrows(IllegalArgumentException::class.java) { spec.copy(scope = "[]") }
    }

    @Test fun `cancelled is terminal without outcome admission or projection`() {
        val cancelled = VerificationResult(VerificationState.CANCELLED, null, VerificationQualification.NOT_EVALUATED,
            VerificationRoute.NONE, "{}", "[]")
        assertThrows(IllegalArgumentException::class.java) { cancelled.copy(admissionRevision = 1) }
        assertThrows(IllegalArgumentException::class.java) { cancelled.copy(outcome = VerificationOutcome.PENDING) }
        assertThrows(IllegalArgumentException::class.java) { cancelled.copy(route = VerificationRoute.DIRECT) }
        assertThrows(IllegalArgumentException::class.java) { cancelled.copy(projectionState = VerificationProjection.PENDING) }
    }

    @Test fun `eight step numbers and state specific hash and timestamps are enforced`() {
        val slot = VerificationStep(1, 1, 1, 1, VerificationStepState.NOT_STARTED, null, null, null, null, null, null, null, now)
        for (number in 1..8) assertEquals(number, slot.copy(stepNo = number).stepNo)
        assertThrows(IllegalArgumentException::class.java) { slot.copy(stepNo = 9) }
        assertThrows(IllegalArgumentException::class.java) { slot.copy(attemptNo = 0) }
        assertThrows(IllegalArgumentException::class.java) { slot.copy(inputHash = "a".repeat(64)) }
        assertThrows(IllegalArgumentException::class.java) { slot.copy(state = VerificationStepState.RUNNING) }
        val running = slot.copy(state = VerificationStepState.RUNNING, inputHash = "a".repeat(64), startedAt = now)
        assertThrows(IllegalArgumentException::class.java) { running.copy(finishedAt = now) }
        for (state in VerificationStepState.values().filter { it.terminal }) {
            assertThrows(IllegalArgumentException::class.java) { running.copy(state = state) }
            assertEquals(state, running.copy(state = state, finishedAt = now).state)
            assertThrows(IllegalArgumentException::class.java) { running.copy(state = state, finishedAt = now.minusMillis(1)) }
        }
    }

}
