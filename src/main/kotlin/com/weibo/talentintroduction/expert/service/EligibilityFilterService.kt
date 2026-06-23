package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.config.AcademicFilterProperties
import com.weibo.talentintroduction.config.CandidateFilterProperties
import com.weibo.talentintroduction.config.EmailValidationProperties
import com.weibo.talentintroduction.expert.domain.EligibilityFilterSetting
import com.weibo.talentintroduction.expert.repository.EligibilityFilterSettingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class EligibilityFilterService(
    private val repository: EligibilityFilterSettingRepository,
    private val candidateDefaults: CandidateFilterProperties,
    private val academicDefaults: AcademicFilterProperties,
    private val emailDefaults: EmailValidationProperties
) {
    private val log = LoggerFactory.getLogger(EligibilityFilterService::class.java)
    private val settingsCache = mutableMapOf<String, String>()
    private var lastCacheLoad: LocalDateTime = LocalDateTime.MIN

    private fun loadFromDb(): Map<String, String> {
        return try {
            repository.findAll().associate { it.settingKey to it.settingValue }
        } catch (e: Exception) {
            log.warn("Failed to load eligibility filter settings from DB, using defaults", e)
            emptyMap()
        }
    }

    private fun loadAll(): Map<String, String> {
        val now = LocalDateTime.now()
        if (settingsCache.isEmpty() || java.time.Duration.between(lastCacheLoad, now).toMinutes() >= 1) {
            synchronized(this) {
                if (settingsCache.isEmpty() || java.time.Duration.between(lastCacheLoad, now).toMinutes() >= 1) {
                    loadFromDb().forEach { (k, v) -> settingsCache[k] = v }
                    lastCacheLoad = now
                }
            }
        }
        return settingsCache.toMap()
    }

    private fun getBoolean(key: String, default: Boolean): Boolean {
        val dbVal = loadAll()[key] ?: return default
        return dbVal.toBoolean()
    }

    private fun getInt(key: String, default: Int): Int {
        val dbVal = loadAll()[key] ?: return default
        return dbVal.toIntOrNull() ?: default
    }

    fun getCandidateFilter(): CandidateFilterProperties {
        val values = loadAll()
        return CandidateFilterProperties(
            requireOrcid = values["candidate.requireOrcid"]?.toBoolean() ?: candidateDefaults.requireOrcid,
            requireValidEmail = values["candidate.requireValidEmail"]?.toBoolean() ?: candidateDefaults.requireValidEmail,
            requireDoctoralDegree = values["candidate.requireDoctoralDegree"]?.toBoolean() ?: candidateDefaults.requireDoctoralDegree,
            excludeChineseNationality = values["candidate.excludeChineseNationality"]?.toBoolean() ?: candidateDefaults.excludeChineseNationality,
            enableAgeFilter = values["candidate.enableAgeFilter"]?.toBoolean() ?: candidateDefaults.enableAgeFilter,
            maxAgeExclusive = values["candidate.maxAgeExclusive"]?.toIntOrNull() ?: candidateDefaults.maxAgeExclusive
        )
    }

    fun getAcademicFilter(): AcademicFilterProperties {
        val values = loadAll()
        return AcademicFilterProperties(
            enableHIndexFilter = values["academic.enableHIndexFilter"]?.toBoolean() ?: academicDefaults.enableHIndexFilter,
            minHIndex = values["academic.minHIndex"]?.toIntOrNull() ?: academicDefaults.minHIndex,
            enableCitationFilter = values["academic.enableCitationFilter"]?.toBoolean() ?: academicDefaults.enableCitationFilter,
            minCitationCount = values["academic.minCitationCount"]?.toIntOrNull() ?: academicDefaults.minCitationCount,
            enableActivityFilter = values["academic.enableActivityFilter"]?.toBoolean() ?: academicDefaults.enableActivityFilter,
            recentYearsThreshold = values["academic.recentYearsThreshold"]?.toIntOrNull() ?: academicDefaults.recentYearsThreshold
        )
    }

    fun getEmailValidationConfig(): EmailValidationProperties {
        val values = loadAll()
        return EmailValidationProperties(
            enableMxCheck = values["email.enableMxCheck"]?.toBoolean() ?: emailDefaults.enableMxCheck,
            enableSmtpVerify = emailDefaults.enableSmtpVerify,
            cacheTtlDays = emailDefaults.cacheTtlDays,
            disposableDomainListPath = emailDefaults.disposableDomainListPath,
            mxLookupTimeoutMs = emailDefaults.mxLookupTimeoutMs
        )
    }

    fun getAll(): EligibilityFiltersResponse {
        val candidate = getCandidateFilter()
        val academic = getAcademicFilter()
        val email = getEmailValidationConfig()
        return EligibilityFiltersResponse(
            candidateFilter = CandidateFilterView(
                requireOrcid = candidate.requireOrcid,
                requireDoctoralDegree = candidate.requireDoctoralDegree,
                requireValidEmail = candidate.requireValidEmail,
                excludeChineseNationality = candidate.excludeChineseNationality,
                enableAgeFilter = candidate.enableAgeFilter,
                maxAgeExclusive = candidate.maxAgeExclusive
            ),
            academicFilter = AcademicFilterView(
                enableHIndexFilter = academic.enableHIndexFilter,
                minHIndex = academic.minHIndex,
                enableCitationFilter = academic.enableCitationFilter,
                minCitationCount = academic.minCitationCount,
                enableActivityFilter = academic.enableActivityFilter,
                recentYearsThreshold = academic.recentYearsThreshold
            ),
            emailValidation = EmailValidationView(
                enableMxCheck = email.enableMxCheck
            )
        )
    }

    fun update(key: String, value: String): EligibilityFilterSetting {
        val existing = repository.findBySettingKey(key)
        val updated = EligibilityFilterSetting(
            id = existing?.id,
            settingKey = key,
            settingValue = value,
            updatedAt = LocalDateTime.now()
        )
        val saved = repository.save(updated)
        synchronized(this) {
            settingsCache[key] = value
            lastCacheLoad = LocalDateTime.now()
        }
        return saved
    }
}

data class EligibilityFiltersResponse(
    val candidateFilter: CandidateFilterView,
    val academicFilter: AcademicFilterView,
    val emailValidation: EmailValidationView
)

data class CandidateFilterView(
    val requireOrcid: Boolean,
    val requireDoctoralDegree: Boolean,
    val requireValidEmail: Boolean,
    val excludeChineseNationality: Boolean,
    val enableAgeFilter: Boolean,
    val maxAgeExclusive: Int
)

data class AcademicFilterView(
    val enableHIndexFilter: Boolean,
    val minHIndex: Int,
    val enableCitationFilter: Boolean,
    val minCitationCount: Int,
    val enableActivityFilter: Boolean,
    val recentYearsThreshold: Int
)

data class EmailValidationView(
    val enableMxCheck: Boolean
)
