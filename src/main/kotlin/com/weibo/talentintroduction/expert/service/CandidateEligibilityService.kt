package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.campaign.domain.RecipientScope
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertType
import com.weibo.talentintroduction.expert.domain.EligibilityResult
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.springframework.stereotype.Service
import java.time.Year
import java.util.Locale

@Service
class CandidateEligibilityService(
    private val eligibilityFilterService: EligibilityFilterService,
    private val emailValidationService: EmailValidationService,
    private val classificationService: ExpertClassificationService = ExpertClassificationService()
) {
    fun isEligibleForCandidateIndex(expert: ExpertProfile): Boolean =
        evaluateEligibility(expert).eligible

    fun evaluateEligibility(expert: ExpertProfile): EligibilityResult {
        val properties = eligibilityFilterService.getCandidateFilter()
        val academicProperties = eligibilityFilterService.getAcademicFilter()
        val reasons = mutableListOf<String>()

        if (properties.requireOrcid && expert.orcidId.isBlank())
            reasons += "MISSING_ORCID"

        if (properties.requireValidEmail && !hasValidEmail(expert.email))
            reasons += "INVALID_EMAIL_FORMAT"

        if (properties.requireValidEmail && expert.email != null && emailValidationService.isDisposableEmail(expert.email))
            reasons += "DISPOSABLE_EMAIL"

        if (properties.requireDoctoralDegree && !hasDoctoralDegree(expert.degree))
            reasons += "NO_DOCTORAL_DEGREE"

        if (properties.enableAgeFilter && !isUnderMaxAge(expert.age, properties.maxAgeExclusive))
            reasons += "AGE_EXCEEDED"

        if (properties.excludeChineseNationality && !isNotChineseNationality(nationalityOf(expert)))
            reasons += "CHINESE_NATIONALITY"

        if (academicProperties.enableHIndexFilter && (expert.hIndex ?: 0) < academicProperties.minHIndex)
            reasons += "H_INDEX_TOO_LOW"

        if (academicProperties.enableCitationFilter && (expert.citationCount ?: 0) < academicProperties.minCitationCount)
            reasons += "CITATION_COUNT_TOO_LOW"

        if (academicProperties.enableActivityFilter) {
            val cutoff = Year.now().value - academicProperties.recentYearsThreshold
            if ((expert.lastPublicationYear ?: 0) < cutoff)
                reasons += "INACTIVE"
        }
        if (DiscoveryIdentity.isDiscovery(expert)) {
            val classification = classificationService.classify(expert)
            val professionalReason = when (classification.type) {
                ExpertType.PRODUCTION_RND, ExpertType.ACADEMIC_RND, ExpertType.HYBRID_RND -> null
                ExpertType.UNKNOWN ->
                    if ("RND_SCOPE_UNCONFIRMED" in classification.negativeEvidence) "RND_SCOPE_UNCONFIRMED"
                    else "RND_EVIDENCE_INSUFFICIENT"
                ExpertType.OUT_OF_SCOPE -> "RND_OUT_OF_SCOPE"
                ExpertType.SERVICE_ONLY -> "RND_SERVICE_ONLY"
            }
            if (professionalReason != null) reasons += professionalReason
        }

        return EligibilityResult(reasons.isEmpty(), reasons)
    }

    /**
     * I-2: 新发现/待确认档案只读明确 `nationality` —— 机构所在地 `country` 是「机构在哪」，
     * 不得当作本人国籍。旧非发现档案保留 `nationality ?: country` 原语义。
     */
    private fun nationalityOf(expert: ExpertProfile): String? =
        if (RecipientScope.isDiscoveryOutreach(expert)) expert.nationality
        else expert.nationality ?: expert.country

    fun hasValidEmail(email: String?): Boolean =
        !email.isNullOrBlank() && EMAIL_REGEX.matches(email)

    fun hasDoctoralDegree(degree: String?): Boolean {
        val normalized = normalize(degree)
        return normalized.contains("phd") ||
            normalized.contains("ph.d") ||
            normalized.contains("doctor") ||
            normalized.contains("doctoral")
    }

    fun isUnderMaxAge(age: Int?, maxAgeExclusive: Int): Boolean =
        age != null && age in 1 until maxAgeExclusive

    fun isNotChineseNationality(nationality: String?): Boolean {
        val normalized = normalize(nationality)
        if (normalized.isBlank()) return true // 国籍未知时放行，不视为中国籍
        return normalized != "china" &&
            normalized != "chinese" &&
            normalized != "cn" &&
            !normalized.contains("people's republic of china")
    }

    private fun normalize(value: String?): String =
        value
            ?.lowercase(Locale.ROOT)
            ?.trim()
            .orEmpty()

    private companion object {
        val EMAIL_REGEX = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)
    }
}
