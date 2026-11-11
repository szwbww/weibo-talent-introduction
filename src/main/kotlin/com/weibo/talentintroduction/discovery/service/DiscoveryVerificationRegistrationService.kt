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
data class DiscoveryVerificationExecution(val executionId: Long?, val run: VerificationRun? = null,
    val archivedPaper: ArchivedPaperExecution? = null) {
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
    private val papers: PaperVerificationEvidenceService? = null,
    private val paperReprocess: com.weibo.talentintroduction.discovery.repository.DiscoveryPaperReprocessRepository? = null
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
            "ruleVersion" to 4,
            "institutionAliasVersion" to DiscoveryInstitutionNormalizer.ALIAS_VERSION,
            "apolloLimitPolicy" to "PROVIDER",
            "apolloAccess" to access.toJson(mapper),
            "maxRequestsPerItem" to properties.maxRequestsPerItem, "maxPagesPerQuery" to properties.maxPagesPerQuery,
            "maxTransientAttempts" to properties.maxTransientAttempts, "leaseSeconds" to properties.leaseSeconds,
            "httpConnectTimeout" to properties.httpConnectTimeout.toString(), "httpReadTimeout" to properties.httpReadTimeout.toString()
        ))
        return DiscoveryVerificationExecution(executionId, repository.createRun(executionId, properties.mode, apollo, settings, clock.instant()))
    }

    internal fun freezePaperSettings(access: FrozenApolloAccess): com.fasterxml.jackson.databind.JsonNode {
        if (!properties.enabled || !properties.workerEnabled || access.searchEnabled && properties.apolloApiKey.isBlank())
            throw IllegalStateException("VERIFICATION_UNAVAILABLE")
        return mapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(linkedMapOf(
            "mode" to VerificationMode.ENFORCE.name, "policyVersion" to VerificationMode.ENFORCE.policyVersion,
            "apolloMatchingVersion" to 2, "ruleVersion" to 4,
            "institutionAliasVersion" to DiscoveryInstitutionNormalizer.ALIAS_VERSION, "apolloLimitPolicy" to "PROVIDER",
            "apolloAccess" to access.toJson(mapper), "maxRequestsPerItem" to properties.maxRequestsPerItem,
            "maxPagesPerQuery" to properties.maxPagesPerQuery, "maxTransientAttempts" to properties.maxTransientAttempts,
            "leaseSeconds" to properties.leaseSeconds, "httpConnectTimeout" to properties.httpConnectTimeout.toString(),
            "httpReadTimeout" to properties.httpReadTimeout.toString()))
    }

    /** No client settings argument: the immutable parent scope is the only authority. */
    internal fun beginFromPaperReprocess(parent: ReprocessRun): DiscoveryVerificationExecution =
        transactions.execute {
            repository.requirePaperParent(parent)
            val settings = parent.scope.path("verificationSettings").deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
            require(settings.path("mode").asText() == VerificationMode.ENFORCE.name &&
                settings.path("policyVersion").asText() == VerificationMode.ENFORCE.policyVersion)
            val access = FrozenApolloAccess.parse(settings.path("apolloAccess"))
            if (!properties.enabled || !properties.workerEnabled || access.searchEnabled && properties.apolloApiKey.isBlank())
                error("VERIFICATION_UNAVAILABLE")
            settings.put("paperReprocessParentId", parent.executionId)
            settings.put("evaluationAt", parent.scope.path("evaluationAt").asText())
            DiscoveryVerificationExecution(parent.executionId, repository.createRun(parent.executionId,
                VerificationMode.ENFORCE, access.searchEnabled, settings.toString(), clock.instant()))
        }!!

    internal fun requirePaperPermission(context: DiscoveryVerificationExecution) {
        context.archivedPaper?.parent?.let { parent ->
            transactions.executeWithoutResult { repository.requirePaperParent(parent) }
        }
    }

    internal fun recordPaperEffect(context: DiscoveryVerificationExecution, registration: DiscoveryRegistration,
        docId: String, effect: String, personResult: DiscoveryPersonRefreshResult? = null) {
        val archived = context.archivedPaper ?: return
        val parent = archived.parent ?: return
        val target = requireNotNull(archived.target)
        val receipts = requireNotNull(paperReprocess)
        requirePaperPermission(context)
        val item = requireNotNull(registration.item)
        require(item.ownerExecutionId == parent.executionId && item.expertDocId == docId)
        val result = requireNotNull(receipts.item(parent.executionId, target.id)?.result)
            .deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        result.put("docId", docId).put("verificationItemId", item.id).put("verificationRunId", item.ownerExecutionId)
        val effects = result.with("effects")
        effects.put(effect, personResult?.retryable != true)
        if (personResult != null) {
            result.set<com.fasterxml.jackson.databind.JsonNode>("personRegistration", mapper.valueToTree(
                linkedMapOf("status" to personResult.status.name, "reason" to personResult.reason,
                    "retryable" to personResult.retryable)))
        }
        if (effect == "RAW") {
            result.put("actualNew", 1).put("targetOutcome", "NEW")
            result.set<com.fasterxml.jackson.databind.JsonNode>("after",
                mapper.valueToTree(requireNotNull(writer.readDiscoveryDocument(ExpertIndexLevel.RAW, docId))))
        }
        check(receipts.receipt(parent, target, result)) { "TARGET_LEASE_LOST" }
    }

    /** Only this parent's exact reserved identity can resume after RAW succeeded without a TARGET receipt. */
    internal fun ownedPaperRegistration(context: DiscoveryVerificationExecution, docId: String,
        incoming: AuthorEmail): DiscoveryRegistration? {
        requirePaperPermission(context)
        val parent = context.archivedPaper?.parent ?: return null
        val item = repository.findItemsByDocIds(listOf(docId)).singleOrNull {
            it.ownerExecutionId == parent.executionId && it.reviewEpoch == 0L &&
                it.sourceName == context.archivedPaper.source.sourceName &&
                it.sourceItemKey == context.archivedPaper.source.sourceItemKey
        } ?: return null
        val raw = writer.readDiscoveryDocument(ExpertIndexLevel.RAW, docId) ?: return null
        val profile = writer.discoveryProfile(docId, raw.source)
        val frozen = frozenProfile(item)
        // RAW adds operational/classification metadata; ownership compares the same canonical facts used by commit.
        val config = policy.currentConfigSnapshot()
        val actual = DiscoveryVerificationAdmissionGate.snapshot(docId, profile, config)
        val expected = DiscoveryVerificationAdmissionGate.snapshot(docId, frozen, config)
        require(profile.givenNames == incoming.givenNames && profile.familyNames == incoming.familyNames &&
            DiscoveryIdentity.normalizedEmail(profile.email) == DiscoveryIdentity.normalizedEmail(incoming.email) &&
            profile.identityVerification == frozen.identityVerification &&
            actual.identityHash == item.identityHash && actual.factFingerprint == expected.factFingerprint) {
            "PAPER_REPROCESS_OWNERSHIP_CONFLICT"
        }
        return DiscoveryRegistration(item, preparedProfile = frozen)
    }

    fun closeInput(context: DiscoveryVerificationExecution) {
        context.run?.let { repository.closeInput(it.executionId, clock.instant()) }
    }

    fun reserve(context: DiscoveryVerificationExecution, docId: String, profile: ExpertProfile,
                sourceName: String, sourceItemKey: String, paper: PaperMetadata? = null): DiscoveryRegistration {
        try {
            requirePaperPermission(context)
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
            existing.firstOrNull()?.let {
                if (context.archivedPaper?.parent != null) require(it.ownerExecutionId == run.executionId) { "FOREIGN_REGISTRATION" }
                return resumeExisting(it, rawProfile, sourceName, sourceItemKey, config)
            }

            var finalProfile = rawProfile
            var receipt: CountrySupplementReceipt? = null
            if (properties.countrySupplementEnabled && countrySupplement != null) {
                requirePaperPermission(context)
                val supplemented = countrySupplement.supplement(run.executionId, docId, rawProfile, paper)
                finalProfile = supplemented.profile
                receipt = supplemented.receipt
            }
            val finalSnapshot = DiscoveryVerificationAdmissionGate.snapshot(docId, finalProfile, config)
            val savedMap = linkedMapOf<String, Any?>("docId" to docId, "identityHash" to finalSnapshot.identityHash,
                "factFingerprint" to finalSnapshot.factFingerprint, "profile" to finalProfile, "admissionRevision" to admission?.revision,
                "sourceName" to sourceName, "sourceItemKey" to sourceItemKey, "constructedAt" to clock.instant().toString())
            val archived = context.archivedPaper
            if (archived != null) {
                require(archived.source.sourceName == sourceName && archived.source.sourceItemKey == sourceItemKey)
                require(archived.source.sourceExecutionId != context.executionId)
            }
            val evaluatedAt = archived?.evaluationAt ?: clock.instant()
            val refs = archived?.let { listOf(it.source) } ?: papers?.locate(listOf(
                com.weibo.talentintroduction.discovery.archive.PaperArchiveLookup(
                    run.executionId, sourceName, sourceItemKey, paper?.doi, paper?.pmid, paper?.pmcId)))?.values?.flatten().orEmpty()
            savedMap["executionContext"] = VerificationExecutionContext(evaluatedAt, refs)
            receipt?.let { savedMap["countrySupplement"] = it }
            val saved = mapper.writeValueAsString(savedMap)
            val item = transactions.execute {
                requirePaperPermission(context)
                repository.reserveItem(VerificationReservation(run.executionId, docId, finalSnapshot.identityHash,
                    run.policyVersion, sourceName, sourceItemKey, saved, finalSnapshot.factFingerprint), clock.instant())
            }!!
            if (archived?.parent != null) require(item.ownerExecutionId == archived.parent.executionId) { "FOREIGN_REGISTRATION" }
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
            PaperReprocessFailure.log("DISCOVERY_REGISTRATION_RESERVE_FAILED", e, context.executionId)
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
        profile.copy(country = null, institutionEvidence = null, emailRouting = null)

    /** Preserve V1 signed receipts; V2 additionally supports country-only ORCID enrichment. */
    private fun validateReceipt(snapshotJson: String, frozen: ExpertProfile) {
        val node = mapper.readTree(snapshotJson).get("countrySupplement") ?: return
        require(node.isObject) { "Country supplement receipt must be an object" }
        val receipt = mapper.treeToValue(node, CountrySupplementReceipt::class.java)
        require(receipt.version in setOf("COUNTRY_V1", DiscoveryCountrySupplementService.VERSION)) { "Unknown country supplement version" }
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
                val countryOnly = receipt.version == "COUNTRY_V2" && frozen.institution.isNullOrBlank() &&
                    receipt.originalInstitutionEvidence == null && frozen.institutionEvidence == null
                if (countryOnly) {
                    // V2 permits a bound ORCID employment country without inventing an institution token.
                    require(receipt.selectedSource == DiscoveryCountrySupplementService.SOURCE_ORCID &&
                        receipt.reason == DiscoveryCountrySupplementService.REASON_MATCHED_BOUND_INSTITUTION &&
                        DiscoveryIdentity.allowed(frozen) && !frozen.identityVerification?.orcid.isNullOrBlank()) {
                        "Country-only receipt requires verified ORCID evidence"
                    }
                } else {
                    val original = requireNotNull(receipt.originalInstitutionEvidence)
                    val current = requireNotNull(frozen.institutionEvidence)
                    require(original.substringBefore(':') == current.substringBefore(':')) { "Institution evidence source changed" }
                    require(DiscoveryIdentity.validInstitutionEvidence(frozen)) { "Frozen institution evidence is invalid" }
                }
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
                if (repository.isPaperReprocessOwned(item.ownerExecutionId))
                    check(repository.lockPaperRunPermission(item.ownerExecutionId)) { "PAPER_REPROCESS_PERMISSION_LOST" }
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
        } catch (e: Exception) {
            PaperReprocessFailure.log("DISCOVERY_REGISTRATION_ACTIVATE_FAILED", e, item.ownerExecutionId)
            throw DiscoveryRegistrationException(e)
        }
    }

    /** Only an existing reservation with the exact RAW identity and source may be recovered. */
    fun recoverDuplicate(docId: String, sourceName: String, sourceItemKey: String) {
        try {
            repository.findItemsByDocIds(listOf(docId)).filter {
                it.state == VerificationState.RESERVED && it.sourceName == sourceName && it.sourceItemKey == sourceItemKey
            }.forEach { recover(it) }
        } catch (e: Exception) {
            PaperReprocessFailure.log("DISCOVERY_REGISTRATION_RECOVER_FAILED", e)
            throw DiscoveryRegistrationException(e)
        }
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

    override fun recoverReserved(limit: Int): Int = repository.listReserved(limit).count { item ->
        // Interrupted paper parents park reservations; they must not abort shared recovery.
        // Activation still rechecks permission under the parent lock before any write.
        if (repository.isPaperReprocessOwned(item.ownerExecutionId) &&
            !repository.paperRunPermission(item.ownerExecutionId)) false
        else recover(item)
    }
}
