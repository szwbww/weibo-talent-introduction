package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Clock

/** Passed explicitly across executor boundaries; never inferred from a task progress slot. */
data class DiscoveryVerificationExecution(val executionId: Long?, val run: VerificationRun? = null) {
    init {
        require(executionId == null || executionId > 0)
        require(run == null || executionId == run.executionId)
    }
}

data class DiscoveryRegistration(val item: VerificationItem? = null, val protected: Boolean = false)
class DiscoveryRegistrationException(cause: Exception) : IllegalStateException("VERIFICATION_REGISTRATION_FAILED", cause)

@Service
class DiscoveryVerificationRegistrationService(
    private val properties: DiscoveryVerificationProperties,
    private val repository: DiscoveryVerificationRepository,
    private val writer: ExpertIndexWriterService,
    private val policy: DiscoveryAdmissionPolicy,
    private val admissions: DiscoveryReviewRepository,
    private val jdbc: JdbcTemplate,
    private val transactions: TransactionTemplate,
    private val clock: Clock = Clock.systemUTC()
) : RegistrationRecovery {
    private val mapper = jacksonObjectMapper().findAndRegisterModules()

    fun resolveOptions(criteria: PaperSearchCriteria): PaperSearchCriteria {
        if (criteria.apolloAssist == true && (!properties.enabled || properties.apolloApiKey.isBlank())) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Apollo verification is not ready")
        }
        return criteria.copy(apolloAssist = criteria.apolloAssist ?: (properties.enabled && properties.apolloApiKey.isNotBlank() && properties.apolloDefault))
    }

    fun begin(executionId: Long?, criteria: PaperSearchCriteria, historical: Boolean = false): DiscoveryVerificationExecution {
        require(executionId == null || executionId > 0) { "A real positive execution ID is required" }
        val existing = executionId?.let { repository.findRun(it) }
        if (existing != null) return DiscoveryVerificationExecution(executionId, existing)
        if (!properties.enabled) return DiscoveryVerificationExecution(executionId)
        require(executionId != null) { "Verification requires a real positive execution ID" }
        check(jdbc.queryForObject("SELECT COUNT(*) FROM task_execution WHERE id=?", Long::class.java, executionId) == 1L) {
            "Verification requires an existing task execution"
        }
        // Old persisted JSON is OFF, never reinterpreted through today's default.
        val apollo = if (historical && criteria.apolloAssist == null) false else requireNotNull(resolveOptions(criteria).apolloAssist)
        val settings = mapper.writeValueAsString(linkedMapOf(
            "mode" to properties.mode.name, "policyVersion" to properties.mode.policyVersion,
            "requestsPerMinute" to properties.requestsPerMinute, "requestsPerDay" to properties.requestsPerDay,
            "maxRequestsPerItem" to properties.maxRequestsPerItem, "maxPagesPerQuery" to properties.maxPagesPerQuery,
            "maxTransientAttempts" to properties.maxTransientAttempts, "leaseSeconds" to properties.leaseSeconds,
            "httpConnectTimeout" to properties.httpConnectTimeout.toString(), "httpReadTimeout" to properties.httpReadTimeout.toString()
        ))
        return DiscoveryVerificationExecution(executionId, repository.createRun(executionId, properties.mode, apollo, settings, clock.instant()))
    }

    fun closeInput(context: DiscoveryVerificationExecution) {
        context.run?.let { repository.closeInput(it.executionId, clock.instant()) }
    }

    fun reserve(context: DiscoveryVerificationExecution, docId: String, profile: ExpertProfile,
                sourceName: String, sourceItemKey: String): DiscoveryRegistration {
        try {
            val run = context.run ?: run {
                check(!properties.enabled) { "Verification execution context is missing" }
                return DiscoveryRegistration()
            }
            val snapshot = DiscoveryVerificationAdmissionGate.snapshot(docId, profile, policy.evaluate(docId, profile).configSnapshot)
            // Read every business protection before the first RAW write; read failures propagate.
            val layers = ExpertIndexLevel.values().associateWith { writer.readDiscoveryDocument(it, docId) }
            val admission = admissions.findAdmission(docId)
            val email = DiscoveryIdentity.normalizedEmail(profile.email)
            val contacted = jdbc.queryForObject("""SELECT (EXISTS(SELECT 1 FROM expert_contact WHERE LOWER(TRIM(expert_email))=? OR orcid_id=?)
                OR EXISTS(SELECT 1 FROM expert_email_alias WHERE normalized_email=?))""", Boolean::class.java,
                email, profile.orcidId.takeIf { it.isNotBlank() }, email) == true
            if (profile.dataSource.orEmpty().contains("SBIR", true) || profile.emailSource.orEmpty().contains("SBIR", true) ||
                contacted || layers.values.any { it != null } ||
                admission?.let { it.identityHash == snapshot.identityHash && (it.decisionEnum.manual || it.decision in setOf("LEGACY_APPROVED", "AUTO_PASSED")) } == true) {
                return DiscoveryRegistration(protected = true)
            }
            val saved = mapper.writeValueAsString(linkedMapOf("docId" to docId, "identityHash" to snapshot.identityHash,
                "factFingerprint" to snapshot.factFingerprint, "profile" to profile, "admissionRevision" to admission?.revision,
                "sourceName" to sourceName, "sourceItemKey" to sourceItemKey, "constructedAt" to clock.instant().toString()))
            val item = repository.reserveItem(VerificationReservation(run.executionId, docId, snapshot.identityHash,
                run.policyVersion, sourceName, sourceItemKey, saved, snapshot.factFingerprint), clock.instant())
            check(item.sourceName == sourceName && item.sourceItemKey == sourceItemKey) { "Reserved source differs" }
            return DiscoveryRegistration(item)
        } catch (e: Exception) { throw DiscoveryRegistrationException(e) }
    }

    fun activate(registration: DiscoveryRegistration) {
        val item = registration.item ?: return
        try {
            transactions.executeWithoutResult {
                // run -> admission -> item. No external reads while holding these locks.
                jdbc.queryForObject("SELECT execution_id FROM discovery_verification_run WHERE execution_id=? FOR UPDATE", Long::class.java, item.ownerExecutionId)
                if (item.state == VerificationState.RESERVED) {
                    admissions.initializeAdmission(item.expertDocId, item.identityHash, "NEEDS_REVIEW",
                        item.policyVersion, java.time.LocalDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC))
                }
                val count = jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_item WHERE owner_execution_id=? AND activated_at IS NOT NULL FOR UPDATE", Long::class.java, item.ownerExecutionId)!!
                repository.activateItem(item.id, (count / 100 + 1).toInt(), clock.instant())
            }
        } catch (e: Exception) { throw DiscoveryRegistrationException(e) }
    }

    /** Only an existing reservation with the exact RAW identity and source may be recovered. */
    fun recoverDuplicate(docId: String, sourceName: String, sourceItemKey: String) {
        try {
            repository.findItemsByDocIds(listOf(docId)).filter {
                it.state == VerificationState.RESERVED && it.sourceName == sourceName && it.sourceItemKey == sourceItemKey
            }.forEach { recover(it) }
        } catch (e: Exception) { throw DiscoveryRegistrationException(e) }
    }

    private fun recover(item: VerificationItem): Boolean {
        val raw = writer.readDiscoveryDocument(ExpertIndexLevel.RAW, item.expertDocId) ?: run {
            // A closed input alone is not terminal: queued source retries may still write RAW.
            // A finished synchronous execution has no source job, so its finished_at is the
            // terminal proof instead. Never infer completion from the progress slot.
            if (repository.findRun(item.ownerExecutionId)?.inputClosed == true) {
                val statuses = jdbc.query("""SELECT j.status FROM discovery_paper_job j
                    JOIN discovery_collection_stream s ON s.id=j.stream_id WHERE s.source=? AND j.item_key=?""",
                    { rs, _ -> rs.getString(1) }, item.sourceName, item.sourceItemKey)
                val synchronousFinished = statuses.isEmpty() && jdbc.queryForObject(
                    "SELECT COUNT(*) FROM task_execution WHERE id=? AND finished_at IS NOT NULL",
                    Long::class.java, item.ownerExecutionId) == 1L
                if (synchronousFinished || (statuses.isNotEmpty() && statuses.all { it in setOf("FAILED", "SUCCEEDED") })) {
                    repository.failReserved(item.id, "[\"REGISTRATION_RAW_MISSING\"]", clock.instant())
                }
            }
            return false
        }
        val profile = writer.discoveryProfile(item.expertDocId, raw.source)
        val identity = DiscoveryReviewIdentity.hash(item.expertDocId, profile.email, profile.givenNames, profile.familyNames)
        if (identity != item.identityHash) {
            repository.failReserved(item.id, "[\"REGISTRATION_IDENTITY_CHANGED\"]", clock.instant())
            return false
        }
        activate(DiscoveryRegistration(item))
        return true
    }

    override fun recoverReserved(limit: Int): Int = repository.listReserved(limit).count { recover(it) }
}
