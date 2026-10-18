package com.weibo.talentintroduction.discovery.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class VerificationMode { AUDIT, ENFORCE;
    val policyVersion: String get() = "APV_20261018_V1_$name"
}
enum class VerificationState { RESERVED, QUEUED, RUNNING, RETRY_WAIT, COMPLETED, ERROR }
enum class VerificationOutcome { DIRECT_CLEAR, APOLLO_CLEAR, APOLLO_UNCLEAR, PENDING, PROTECTED }
enum class VerificationQualification { NOT_EVALUATED, MET, UNKNOWN, NOT_MET, CONFLICT }
enum class VerificationRoute { NONE, DIRECT, APOLLO }
enum class VerificationProjection { NOT_REQUIRED, PENDING, PROJECTED, ALREADY_PRESENT, APPLICATION_PRESENT, ERROR }
enum class VerificationContext { UNMANAGED, MANAGED_AUDIT, MANAGED_ENFORCE, UNAVAILABLE }
enum class VerificationProvider { APOLLO, ORCID }
enum class VerificationQueryKind { LOCAL_EVIDENCE, BROAD, VARIANT, NARROW, ORCID }
enum class AssertionSource { ORGANIZATION, SELF, UNKNOWN }
enum class TemporalScope { CURRENT, HISTORICAL, UNKNOWN }

fun verificationText(value: String, max: Int): String {
    require(value.isNotBlank() && value.length <= max) { "Invalid verification field length (maximum $max)" }
    return value
}
fun verificationHash(value: String): String {
    require(value.matches(Regex("[a-f0-9]{64}"))) { "Expected SHA-256" }
    return value
}
fun Instant.verificationUtc(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
fun LocalDateTime.verificationInstant(): Instant = toInstant(ZoneOffset.UTC)

interface RegistrationRecovery { fun recoverReserved(limit: Int): Int }

data class VerificationRun(
    val executionId: Long, val mode: VerificationMode, val policyVersion: String,
    val apolloRequested: Boolean, val settingsJson: String, val inputClosed: Boolean = false,
    val revision: Long = 0, val createdAt: Instant, val updatedAt: Instant
) {
    init { require(executionId > 0); require(policyVersion == mode.policyVersion) }
}

data class VerificationReservation(
    val ownerExecutionId: Long, val expertDocId: String, val identityHash: String,
    val policyVersion: String, val sourceName: String, val sourceItemKey: String,
    val snapshotJson: String, val factFingerprint: String
) {
    init {
        require(ownerExecutionId > 0)
        verificationText(expertDocId, 128); verificationHash(identityHash)
        verificationText(policyVersion, 32); verificationText(sourceName, 32)
        verificationText(sourceItemKey, 128); verificationHash(factFingerprint)
        require(snapshotJson.isNotBlank())
    }
}

data class VerificationItem(
    val id: Long, val ownerExecutionId: Long, val expertDocId: String, val identityHash: String,
    val policyVersion: String, val sourceName: String, val sourceItemKey: String,
    val batchNumber: Int, val snapshotJson: String, val factFingerprint: String,
    val state: VerificationState, val outcome: VerificationOutcome?,
    val qualification: VerificationQualification, val route: VerificationRoute,
    val apolloEnteredAt: Instant?, val searchCompletedAt: Instant?, val candidateFoundAt: Instant?,
    val personMatchedAt: Instant?, val apolloPersonId: String?, val evidenceJson: String?,
    val reasonsJson: String?, val requestCount: Int, val transientAttempts: Int,
    val generation: Long, val leaseToken: String?, val leaseUntil: Instant?, val nextAttemptAt: Instant?,
    val admissionRevision: Long?, val projectionState: VerificationProjection, val projectionError: String?,
    val activatedAt: Instant?, val completedAt: Instant?, val createdAt: Instant, val updatedAt: Instant
) {
    init {
        require(id > 0 && ownerExecutionId > 0 && batchNumber >= 0 && requestCount >= 0 && transientAttempts >= 0 && generation >= 0)
        verificationText(expertDocId, 128); verificationHash(identityHash); verificationHash(factFingerprint)
        require(policyVersion in VerificationMode.values().map { it.policyVersion })
        if (state == VerificationState.RESERVED) require(batchNumber == 0 && activatedAt == null)
        if (activatedAt != null) require(batchNumber > 0)
        if (state == VerificationState.RUNNING) require(leaseToken != null && leaseUntil != null)
        if (state != VerificationState.COMPLETED) require(outcome == null)
        if (state == VerificationState.COMPLETED || state == VerificationState.ERROR) {
            require(completedAt != null)
            VerificationResult(state, outcome, qualification, route, evidenceJson ?: "{}", reasonsJson ?: "[]",
                apolloEnteredAt, searchCompletedAt, candidateFoundAt, personMatchedAt, apolloPersonId, admissionRevision, projectionState)
        } else require(admissionRevision == null && projectionState == VerificationProjection.NOT_REQUIRED)
    }
}

data class VerificationEvidence(
    val identityHash: String, val factFingerprint: String, val provider: String,
    val sourceUrl: String, val fetchedAt: Instant, val responseSha256: String,
    val field: String, val value: String, val assertionSource: AssertionSource,
    val temporalScope: TemporalScope, val sourceUpdatedAt: Instant? = null,
    val jsonPointer: String? = null, val excerpt: String? = null,
    val conflicts: List<String> = emptyList(), val schemaVersion: Int = 1
) {
    init {
        require(schemaVersion == 1); verificationHash(identityHash); verificationHash(factFingerprint)
        verificationHash(responseSha256); verificationText(provider, 16); verificationText(sourceUrl, 2048)
        require(sourceUrl.startsWith("https://") || sourceUrl.startsWith("http://"))
        verificationText(field, 128); verificationText(value, 4096)
        require(excerpt == null || excerpt.length <= 2048)
    }
}

data class VerificationProgress(
    val stage: String = "LOCAL_EVIDENCE", val queryIndex: Int = 0, val page: Int = 1,
    val complete: Boolean = false, val candidateIds: List<String> = emptyList(),
    val finishedQueryKeys: List<String> = emptyList(), val schemaVersion: Int = 1
) {
    init { require(schemaVersion == 1); require(stage in setOf("LOCAL_EVIDENCE", "APOLLO_SEARCH", "DECIDE", "PROJECT")); require(queryIndex >= 0 && page > 0) }
}

data class VerificationResult(
    val state: VerificationState, val outcome: VerificationOutcome?,
    val qualification: VerificationQualification, val route: VerificationRoute,
    val evidenceJson: String, val reasonsJson: String,
    val apolloEnteredAt: Instant? = null, val searchCompletedAt: Instant? = null,
    val candidateFoundAt: Instant? = null, val personMatchedAt: Instant? = null,
    val apolloPersonId: String? = null, val admissionRevision: Long? = null,
    val projectionState: VerificationProjection = VerificationProjection.NOT_REQUIRED
) {
    init {
        require(state == VerificationState.COMPLETED || state == VerificationState.ERROR)
        require((state == VerificationState.ERROR) == (outcome == null))
        when (outcome) {
            VerificationOutcome.DIRECT_CLEAR -> require(route == VerificationRoute.DIRECT && qualification == VerificationQualification.MET)
            VerificationOutcome.APOLLO_CLEAR -> require(route == VerificationRoute.APOLLO && qualification == VerificationQualification.MET && personMatchedAt != null)
            VerificationOutcome.APOLLO_UNCLEAR -> require(route == VerificationRoute.APOLLO && qualification == VerificationQualification.UNKNOWN && personMatchedAt != null)
            VerificationOutcome.PROTECTED -> require(route == VerificationRoute.NONE && qualification == VerificationQualification.NOT_EVALUATED)
            VerificationOutcome.PENDING -> require(route == VerificationRoute.NONE)
            null -> require(route == VerificationRoute.NONE)
        }
        require(apolloPersonId == null || apolloPersonId.length <= 128)
        if (projectionState != VerificationProjection.NOT_REQUIRED) require(admissionRevision != null && outcome in setOf(VerificationOutcome.DIRECT_CLEAR, VerificationOutcome.APOLLO_CLEAR, VerificationOutcome.APOLLO_UNCLEAR))
    }
}

data class VerificationAttemptRequest(
    val requestKey: String, val provider: VerificationProvider, val queryKind: VerificationQueryKind,
    val pageNumber: Int, val attemptNumber: Int, val requestJson: String
) {
    init { verificationText(requestKey, 128); require(pageNumber > 0 && attemptNumber > 0); require(requestJson.isNotBlank()) }
}
data class VerificationAttempt(
    val id: Long, val itemId: Long, val requestKey: String, val provider: VerificationProvider,
    val queryKind: VerificationQueryKind, val pageNumber: Int, val attemptNumber: Int,
    val requestedAt: Instant, val completedAt: Instant?, val httpStatus: Int?, val resultCode: String,
    val requestJson: String, val responseJson: String?, val responseSha256: String?
)
enum class VerificationBudgetReason { LEASE_LOST, LOCK_BUSY, MINUTE_LIMIT, DAY_LIMIT, ITEM_LIMIT, DUPLICATE_REQUEST }
data class VerificationAttemptReservation(val attemptId: Long? = null, val deferred: VerificationBudgetReason? = null)
data class VerificationCounts(
    val profiles: Long = 0, val autoAdmitted: Long = 0, val directAdmitted: Long = 0,
    val apolloAdmitted: Long = 0, val clear: Long = 0, val unclear: Long = 0, val pending: Long = 0,
    val errors: Long = 0, val queued: Long = 0, val protected: Long = 0,
    val projectionPending: Long = 0, val projectionErrors: Long = 0, val apolloEntered: Long = 0,
    val apolloSearched: Long = 0, val apolloCandidates: Long = 0, val apolloMatched: Long = 0,
    val apolloHttpAttempts: Long = 0, val registrationErrors: Long = 0
)
