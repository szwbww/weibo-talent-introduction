package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.config.CandidateFilterProperties
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class CandidateEligibilityService(
    private val properties: CandidateFilterProperties
) {
    fun isEligibleForCandidateIndex(expert: ExpertProfile): Boolean =
        expert.orcidId.isNotBlank() &&
            (!properties.requireValidEmail || hasValidEmail(expert.email)) &&
            (!properties.requireDoctoralDegree || hasDoctoralDegree(expert.degree)) &&
            (!properties.enableAgeFilter || isUnderMaxAge(expert.age)) &&
            (!properties.excludeChineseNationality || isNotChineseNationality(expert.nationality ?: expert.country))

    fun hasValidEmail(email: String?): Boolean =
        !email.isNullOrBlank() && EMAIL_REGEX.matches(email)

    fun hasDoctoralDegree(degree: String?): Boolean {
        val normalized = normalize(degree)
        return normalized.contains("phd") ||
            normalized.contains("ph.d") ||
            normalized.contains("doctor") ||
            normalized.contains("doctoral")
    }

    fun isUnderMaxAge(age: Int?): Boolean =
        age != null && age in 1 until properties.maxAgeExclusive

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
