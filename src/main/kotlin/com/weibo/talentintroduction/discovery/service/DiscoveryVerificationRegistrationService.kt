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

data class DiscoveryRegistration(val item: VerificationItem? = null, val protected: Boolean = false,
    val preparedProfile: ExpertProfile? = null)
class DiscoveryRegistrationException(cause: Exception) : IllegalStateException("VERIFICATION_REGISTRATION_FAILED", cause)

/** Existing runs alone may interpret a missing member through the historical paid-enabled contract. */
internal fun VerificationRun.frozenApolloAccess(mapper: com.fasterxml.jackson.databind.ObjectMapper): FrozenApolloAccess {
    val root = mapper.readTree(settingsJson)
    require(root != null && root.isObject) { "Invalid verification settings snapshot" }
    val node = root.get("apolloAccess") ?: mapper.createObjectNode()
        .put("schemaVersion", FrozenApolloAccess.SCHEMA_VERSION).put("settingRevision", 0)
        .put("searchEnabled", apolloRequested).put("detailEnabled", apolloRequested)
        .put("source", ApolloSettingSource.LEGACY.name)
    return FrozenApolloAccess.parse(node)
}

@Service
class DiscoveryVerificationRegistrationService(
    private val properties: DiscoveryVerificationProperties,
    private val repository: DiscoveryVerificationRepository,
    private val writer: ExpertIndexWriterService,
    private val policy: DiscoveryAdmissionPolicy,
    private val admissions: DiscoveryReviewRepository,
    private val jdbc: JdbcTemplate,
    private val transactions: TransactionTemplate,
    private val apolloSettings: DiscoveryApolloSettingsService,
    private val clock: Clock = Clock.systemUTC(),
    private val countrySupplement: DiscoveryCountrySupplementService? = null,
    private val traces: com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationTraceRepository =
        com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationTraceRepository(jdbc),
    private val papers: PaperVerificationEvidenceService? = null
) : RegistrationRecovery {
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
        .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    fun resolveOptions(criteria: PaperSearchCriteria): PaperSearchCriteria {
        if (criteria.apolloAssist == true) {
            requireApolloReady()
            val settings = try {
                apolloSettings.resolve()
            } catch (_: DiscoveryApolloSettingsUnavailable) {
                throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "APOLLO_SETTINGS_UNAVAILABLE")
            }
            if (!settings.searchEnabled) throw ResponseStatusException(HttpStatus.CONFLICT, "APOLLO_SEARCH_DISABLED")
        }
        return criteria
    }

    private fun requireApolloReady() {
        if (!properties.enabled || properties.apolloApiKey.isBlank()) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Apollo verification is not ready")
        }
    }

    fun begin(executionId: Long?, criteria: PaperSearchCriteria, historical: Boolean = false): DiscoveryVerificationExecution {
        require(executionId == null || executionId > 0) { "A real positive execution ID is required" }
        val existing = executionId?.let { repository.findRun(it) }
        if (existing != null) return DiscoveryVerificationExecution(executionId, existing)
        if (criteria.apolloAssist == true) requireApolloReady()
        if (!properties.enabled) return DiscoveryVerificationExecution(executionId)
        require(executionId != null) { "Verification requires a real positive execution ID" }
        check(jdbc.queryForObject("SELECT COUNT(*) FROM task_execution WHERE id=?", Long::class.java, executionId) == 1L) {
            "Verification requires an existing task execution"
        }
        // Old persisted JSON is OFF, never reinterpreted through today's default.
        val access = try {
            apolloSettings.freeze()
        } catch (_: DiscoveryApolloSettingsUnavailable) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "APOLLO_SETTINGS_UNAVAILABLE")
        }
        if (criteria.apolloAssist == true && !access.searchEnabled) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "APOLLO_SEARCH_DISABLED")
        }
        val apollo = !(historical && criteria.apolloAssist == null) &&
            access.searchEnabled && criteria.apolloAssist != false && properties.apolloApiKey.isNotBlank()
        val settings = mapper.writeValueAsString(linkedMapOf(
            "mode" to properties.mode.name, "policyVersion" to properties.mode.policyVersion,
            "apolloMatchingVersion" to 2,
            "ruleVersion" to 3,
            "institutionAliasVersion" to DiscoveryInstitutionNormalizer.ALIAS_VERSION,
            "apolloLimitPolicy" to "PROVIDER",
            "apolloAccess" to access.toJson(mapper),
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
                sourceName: String, sourceItemKey: String, paper: PaperMetadata? = null): DiscoveryRegistration {
        try {
            val run = context.run ?: run {
                check(!properties.enabled) { "Verification execution context is missing" }
                return DiscoveryRegistration()
            }
            // Match toIndexMap's RAW identity convention; real ORCID remains in externalIds/provenance.
            val rawProfile = profile.copy(esDocId = docId, orcidId = docId)
            val config = policy.evaluate(docId, rawProfile).configSnapshot
            val snapshot = DiscoveryVerificationAdmissionGate.snapshot(docId, rawProfile, config)
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
            // I-3: a retry or a concurrent reservation must consume the already frozen facts and issue no HTTP.
            val existing = repository.findItemsByDocIds(listOf(docId))
                .filter { it.identityHash == snapshot.identityHash && it.policyVersion == run.policyVersion }
            require(existing.size <= 1) { "Multiple reservations for the same identity and policy" }
            existing.firstOrNull()?.let { return resumeExisting(it, rawProfile, sourceName, sourceItemKey, config) }

            var finalProfile = rawProfile
            var receipt: CountrySupplementReceipt? = null
            if (properties.countrySupplementEnabled && countrySupplement != null) {
                val supplemented = countrySupplement.supplement(run.executionId, docId, rawProfile, paper)
                finalProfile = supplemented.profile
                receipt = supplemented.receipt
            }
            val finalSnapshot = DiscoveryVerificationAdmissionGate.snapshot(docId, finalProfile, config)
            val savedMap = linkedMapOf<String, Any?>("docId" to docId, "identityHash" to finalSnapshot.identityHash,
                "factFingerprint" to finalSnapshot.factFingerprint, "profile" to finalProfile, "admissionRevision" to admission?.revision,
                "sourceName" to sourceName, "sourceItemKey" to sourceItemKey, "constructedAt" to clock.instant().toString())
            val evaluatedAt = clock.instant()
            val refs = papers?.locate(listOf(com.weibo.talentintroduction.discovery.archive.PaperArchiveLookup(
                run.executionId, sourceName, sourceItemKey, paper?.doi, paper?.pmid, paper?.pmcId)))?.values?.flatten().orEmpty()
            savedMap["executionContext"] = VerificationExecutionContext(evaluatedAt, refs)
            receipt?.let { savedMap["countrySupplement"] = it }
            val saved = mapper.writeValueAsString(savedMap)
            val item = repository.reserveItem(VerificationReservation(run.executionId, docId, finalSnapshot.identityHash,
                run.policyVersion, sourceName, sourceItemKey, saved, finalSnapshot.factFingerprint), clock.instant())
            return freeze(item, rawProfile, sourceName, sourceItemKey, config)
        } catch (e: InterruptedException) {
            // I-5: cancellation must stop discovery, never become a retryable registration failure.
            Thread.currentThread().interrupt()
            throw e
        } catch (e: java.util.concurrent.CancellationException) {
            throw e
        } catch (e: Exception) {
            val cancellation = cancellationCause(e)
            if (cancellation != null) throw cancellation
            throw DiscoveryRegistrationException(e)
        }
    }

    private fun cancellationCause(error: Throwable): Throwable? {
        var current: Throwable? = error
        while (current != null) {
            if (current is java.util.concurrent.CancellationException) return current
            if (current is InterruptedException) {
                Thread.currentThread().interrupt()
                return current
            }
            current = current.cause
        }
        return if (Thread.currentThread().isInterrupted) InterruptedException("Verification registration interrupted") else null
    }

    /** Reuses a reservation only when its frozen facts (excluding country and the institution token) still match. */
    private fun resumeExisting(item: VerificationItem, rawProfile: ExpertProfile, sourceName: String,
                               sourceItemKey: String, config: AdmissionConfigSnapshot): DiscoveryRegistration {
        require(item.sourceName == sourceName && item.sourceItemKey == sourceItemKey) { "Reserved source differs" }
        return freeze(item, rawProfile, sourceName, sourceItemKey, config, verifyFingerprint = false)
    }

    /**
     * Returns the profile actually persisted: a concurrency winner may have frozen another country, and only
     * its snapshot may be written to RAW. All other facts must still match the current discovery input.
     */
    private fun freeze(item: VerificationItem, rawProfile: ExpertProfile, sourceName: String,
                       sourceItemKey: String, config: AdmissionConfigSnapshot,
                       verifyFingerprint: Boolean = true): DiscoveryRegistration {
        require(item.sourceName == sourceName && item.sourceItemKey == sourceItemKey) { "Reserved source differs" }
        val root = mapper.readTree(item.snapshotJson)
        require(root.path("identityHash").asText() == item.identityHash &&
            root.path("factFingerprint").asText() == item.factFingerprint) { "Reserved snapshot is inconsistent" }
        val frozen = frozenProfile(item)
        require(factsWithoutCountry(frozen) == factsWithoutCountry(rawProfile)) { "Reserved facts differ from the current input" }
        validateReceipt(item.snapshotJson, frozen)
        if (verifyFingerprint) {
            val recomputed = DiscoveryVerificationAdmissionGate.snapshot(item.expertDocId, frozen, config)
            require(recomputed.identityHash == item.identityHash && recomputed.factFingerprint == item.factFingerprint) {
                "Frozen profile does not match the reserved fingerprint"
            }
        }
        // A non-RESERVED item was already activated by another caller: never rewrite its RAW.
        return DiscoveryRegistration(item, protected = item.state != VerificationState.RESERVED, preparedProfile = frozen)
    }

    private fun frozenProfile(item: VerificationItem): ExpertProfile {
        val root = mapper.readTree(item.snapshotJson)
        val profile = root.get("profile") ?: throw IllegalStateException("Reserved snapshot has no profile")
        return mapper.treeToValue(profile, ExpertProfile::class.java)
    }

    private fun factsWithoutCountry(profile: ExpertProfile) =
        profile.copy(country = null, institutionEvidence = null)

    /** Absent member keeps the pre-supplement semantics; present member must be a complete COUNTRY_V1 receipt. */
    private fun validateReceipt(snapshotJson: String, frozen: ExpertProfile) {
        val node = mapper.readTree(snapshotJson).get("countrySupplement") ?: return
        require(node.isObject) { "Country supplement receipt must be an object" }
        val receipt = mapper.treeToValue(node, CountrySupplementReceipt::class.java)
        require(receipt.version == DiscoveryCountrySupplementService.VERSION) { "Unknown country supplement version" }
        require(receipt.status in DiscoveryCountrySupplementService.STATUSES) { "Unknown country supplement status" }
        require(receipt.reason in DiscoveryCountrySupplementService.REASONS) { "Unknown country supplement reason" }
        require(receipt.observations.size <= DiscoveryCountrySupplementService.MAX_OBSERVATIONS)
        require(receipt.observations.all { it.institution.length <= DiscoveryCountrySupplementService.MAX_INSTITUTION_LENGTH })
        require(receipt.institution == null || receipt.institution == frozen.institution) { "Receipt institution differs" }
        when (receipt.status) {
            DiscoveryCountrySupplementService.STATUS_FILLED -> {
                require(receipt.beforeCountry.isNullOrBlank() && receipt.afterCountry == frozen.country && !frozen.country.isNullOrBlank())
                require(receipt.selectedSource in setOf(DiscoveryCountrySupplementService.SOURCE_OPENALEX,
                    DiscoveryCountrySupplementService.SOURCE_ORCID))
                val original = requireNotNull(receipt.originalInstitutionEvidence)
                val current = requireNotNull(frozen.institutionEvidence)
                require(original.substringBefore(':') == current.substringBefore(':')) { "Institution evidence source changed" }
                require(DiscoveryIdentity.validInstitutionEvidence(frozen)) { "Frozen institution evidence is invalid" }
            }
            DiscoveryCountrySupplementService.STATUS_UNCHANGED -> {
                require(!frozen.country.isNullOrBlank() && receipt.beforeCountry == frozen.country && receipt.afterCountry == frozen.country)
            }
            else -> {
                require(frozen.country.isNullOrBlank() && receipt.beforeCountry.isNullOrBlank() && receipt.afterCountry.isNullOrBlank())
            }
        }
    }

    fun activate(registration: DiscoveryRegistration) {
        val item = registration.item ?: return
        try {
            transactions.executeWithoutResult {
                // run -> admission -> item. No external reads while holding these locks.
                jdbc.queryForObject("SELECT execution_id FROM discovery_verification_run WHERE execution_id=? FOR UPDATE", Long::class.java, item.ownerExecutionId)
                if (item.reviewEpoch == 0L && item.policyVersion == VerificationMode.ENFORCE.policyVersion &&
                    item.state == VerificationState.RESERVED) {
                    admissions.initializeAdmission(item.expertDocId, item.identityHash, "NEEDS_REVIEW",
                        item.policyVersion, java.time.LocalDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC))
                }
                val count = jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_item WHERE owner_execution_id=? AND activated_at IS NOT NULL FOR UPDATE", Long::class.java, item.ownerExecutionId)!!
                val activated = repository.activateItem(item.id, (count / 100 + 1).toInt(), clock.instant())
                traces.initSteps(activated.id, clock.instant())
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
