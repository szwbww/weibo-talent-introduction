package com.weibo.talentintroduction.expert.domain

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.security.MessageDigest
import java.util.Locale

/** A proof is bound to the identity it verified, never to a mutable business document ID. */
data class IdentityVerification(
    val status: String = "UNRESOLVED",
    val version: Int = 0,
    val email: String? = null,
    val givenNames: String? = null,
    val familyNames: String? = null,
    val source: String? = null,
    val evidenceHash: String? = null,
    val orcid: String? = null,
    val openAlexAuthorId: String? = null
)

object DiscoveryIdentity {
    const val VERSION = 20260925
    private val mapper = jacksonObjectMapper()
    private val sources = listOf("PAPER_FULLTEXT", "ORCID_PUBLIC")
    fun normalizedEmail(email: String?) = email.orEmpty().trim().lowercase(Locale.ROOT)
    fun hash(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    fun isDiscovery(profile: ExpertProfile) = profile.identityVerification != null ||
        profile.emailSource in sources || profile.tags.orEmpty().contains("discovered")
    fun validEvidence(evidence: String?): Boolean = evidence != null &&
        evidence.matches(Regex("(?:JATS_SHA256|ORCID_RECORD_SHA256):[0-9a-f]{64}"))
    fun verified(email: String, given: String?, family: String?, evidence: String, orcid: String?, authorId: String?) =
        IdentityVerification("VERIFIED", VERSION, normalizedEmail(email), given, family,
            evidence.substringBefore(':'), evidence.substringAfter(':'), orcid, authorId)

    /** Import/enrichment evidence only; outreach and promotion use their configured eligibility rules. */
    fun allowed(profile: ExpertProfile): Boolean {
        if (!isDiscovery(profile)) return true
        val proof = profile.identityVerification ?: return false
        return proof.status == "VERIFIED" && proof.version == VERSION &&
            normalizedEmail(proof.email).isNotEmpty() && normalizedEmail(proof.email) == normalizedEmail(profile.email) &&
            !proof.givenNames.isNullOrBlank() && !proof.familyNames.isNullOrBlank() &&
            proof.givenNames == profile.givenNames && proof.familyNames == profile.familyNames &&
            (validEvidence("${proof.source}:${proof.evidenceHash}") ||
                (proof.source == "REVIEWED_SOURCE_SHA256" && proof.evidenceHash.orEmpty().matches(Regex("[0-9a-f]{64}"))))
    }
    fun read(node: JsonNode): IdentityVerification? {
        if (!node.isObject) return null
        return try { mapper.treeToValue(node, IdentityVerification::class.java) } catch (_: Exception) {
            IdentityVerification() // malformed presence must remain a discovery identity, never a legacy bypass
        }
    }
    fun allowedSource(source: JsonNode): Boolean = allowed(ExpertProfile(
        orcidId = source.path("orcidId").asText(""), email = source.path("email").asText(null),
        givenNames = source.path("givenNames").asText(null), familyNames = source.path("familyNames").asText(null),
        country = null, keyword = null, employment = null, emailSource = source.path("emailSource").asText(null),
        tags = source.path("tags").takeIf { it.isArray }?.map { it.asText() },
        identityVerification = read(source.path("identityVerification"))
    ))
    fun allowedMap(source: Map<String, Any?>): Boolean = allowedSource(mapper.valueToTree(source))

}
