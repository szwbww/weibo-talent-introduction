package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.expert.domain.EligibilityResult
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.springframework.stereotype.Service
import java.time.Year
import java.util.Locale

@Service
class CandidateEligibilityService(
    private val eligibilityFilterService: EligibilityFilterService,
    private val emailValidationService: EmailValidationService
) {
    fun isEligibleForCandidateIndex(expert: ExpertProfile): Boolean =
        evaluateEligibility(expert).eligible

    fun evaluateEligibility(expert: ExpertProfile): EligibilityResult {
        val properties = eligibilityFilterService.getCandidateFilter()
        val academicProperties = eligibilityFilterService.getAcademicFilter()
        val reasons = mutableListOf<String>()

        if (expert.orcidId.isBlank())
            reasons += "MISSING_ORCID"

        if (properties.requireValidEmail && !hasValidEmail(expert.email))
            reasons += "INVALID_EMAIL_FORMAT"

        if (properties.requireValidEmail && expert.email != null && emailValidationService.isDisposableEmail(expert.email))
            reasons += "DISPOSABLE_EMAIL"

        if (properties.requireDoctoralDegree && !hasDoctoralDegree(expert.degree))
            reasons += "NO_DOCTORAL_DEGREE"

        if (properties.enableAgeFilter && !isUnderMaxAge(expert.age, properties.maxAgeExclusive))
            reasons += "AGE_EXCEEDED"

        if (properties.excludeChineseNationality && !isNotChineseNationality(expert.nationality ?: expert.country))
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

        return EligibilityResult(reasons.isEmpty(), reasons)
    }

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
        return normalized.isNotBlank() &&
            normalized != "china" &&
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
