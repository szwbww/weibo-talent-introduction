package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.config.EmailValidationProperties
import com.weibo.talentintroduction.expert.domain.EmailValidationCache
import com.weibo.talentintroduction.expert.domain.EmailValidationResult
import com.weibo.talentintroduction.expert.repository.EmailValidationCacheRepository
import org.slf4j.LoggerFactory
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.Locale

@Service
class EmailValidationService(
    private val properties: EmailValidationProperties,
    private val cacheRepository: EmailValidationCacheRepository,
    private val resourceLoader: ResourceLoader,
    private val mxLookupClient: MxLookupClient,
    private val eligibilityFilterService: EligibilityFilterService
) {
    private val log = LoggerFactory.getLogger(EmailValidationService::class.java)
    private val disposableDomains: Set<String> = loadDisposableDomains()

    private fun mxCheckEnabled(): Boolean =
        eligibilityFilterService.getEmailValidationConfig().enableMxCheck

    fun validate(email: String): EmailValidationResult {
        val normalized = email.lowercase(Locale.ROOT).trim()
        if (normalized.isBlank()) return reject(0, "EMPTY_EMAIL")

        val mxEnabled = mxCheckEnabled()

        val cached = cacheRepository.findByEmail(normalized)
        if (cached != null && !cached.isExpired()) {
            if (cached.rejectReason != null) {
                if (cached.rejectReason == "NO_MX_RECORD" && !mxEnabled) {
                    updateCacheToLevel2(normalized, cached)
                    return EmailValidationResult(2, true)
                }
                return EmailValidationResult(cached.verifiedLevel, false, cached.rejectReason)
            }
            if (!(mxEnabled && cached.mxValid == null)) {
                return EmailValidationResult(cached.verifiedLevel, true, null)
            }
            val domain = normalized.substringAfter("@")
            if (!hasMxRecord(domain)) {
                cacheAndReturn(normalized, 2, "NO_MX_RECORD")
                return reject(2, "NO_MX_RECORD")
            }
            cacheAndReturn(normalized, 3, null)
            return EmailValidationResult(3, true)
        }

        if (!isValidFormat(normalized)) {
            cacheAndReturn(normalized, 0, "INVALID_FORMAT")
            return reject(0, "INVALID_FORMAT")
        }

        val domain = normalized.substringAfter("@")
        if (isDisposableDomain(domain)) {
            cacheAndReturn(normalized, 1, "DISPOSABLE_EMAIL")
            return reject(1, "DISPOSABLE_EMAIL")
        }

        if (mxEnabled) {
            if (!hasMxRecord(domain)) {
                cacheAndReturn(normalized, 2, "NO_MX_RECORD")
                return reject(2, "NO_MX_RECORD")
            }
        }

        val level = if (mxEnabled) 3 else 2
        cacheAndReturn(normalized, level, null)
        return EmailValidationResult(level, true)
    }

    fun isValidFormat(email: String): Boolean =
        email.isNotBlank() && EMAIL_REGEX.matches(email)

    fun isDisposableDomain(domain: String): Boolean =
        domain.lowercase(Locale.ROOT) in disposableDomains

    fun isDisposableEmail(email: String): Boolean {
        if (email.isBlank() || !email.contains("@")) return false
        return isDisposableDomain(email.substringAfter("@").lowercase(Locale.ROOT))
    }

    fun hasMxRecord(domain: String): Boolean {
        val domainCached = cacheRepository.findByDomainWithMxResult(domain.lowercase(Locale.ROOT), LocalDateTime.now())
        if (domainCached != null && !domainCached.isExpired()) {
            return domainCached.mxValid == true
        }
        return when (mxLookupClient.lookup(domain.lowercase(Locale.ROOT))) {
            MxLookupResult.FOUND -> true
            MxLookupResult.NOT_FOUND -> false
            MxLookupResult.DNS_ERROR -> true
        }
    }

    private fun updateCacheToLevel2(email: String, cached: EmailValidationCache) {
        val now = LocalDateTime.now()
        try {
            val entity = cached.copy(
                verifiedLevel = 2,
                rejectReason = null,
                mxValid = null,
                verifiedAt = now,
                expiresAt = now.plusDays(properties.cacheTtlDays.toLong())
            )
            cacheRepository.save(entity)
        } catch (e: Exception) {
            log.warn("Failed to update cache for {} after MX disabled: {}", email, e.message)
        }
    }

    private fun reject(level: Int, reason: String): EmailValidationResult =
        EmailValidationResult(level, false, reason)

    private fun cacheAndReturn(email: String, level: Int, rejectReason: String?) {
        val domain = email.substringAfter("@")
        val now = LocalDateTime.now()
        try {
            val existing = cacheRepository.findByEmail(email)
            val entity = EmailValidationCache(
                id = existing?.id,
                email = email,
                domain = domain,
                formatValid = level >= 1,
                disposable = rejectReason == "DISPOSABLE_EMAIL",
                mxValid = when {
                    level >= 3 -> true
                    rejectReason == "NO_MX_RECORD" -> false
                    else -> null
                },
                verifiedLevel = level,
                rejectReason = rejectReason,
                verifiedAt = now,
                expiresAt = now.plusDays(properties.cacheTtlDays.toLong())
            )
            cacheRepository.save(entity)
        } catch (e: Exception) {
            log.warn("Failed to cache email validation result for {}: {}", email, e.message)
        }
    }

    private fun loadDisposableDomains(): Set<String> {
        return try {
            val resource = resourceLoader.getResource(properties.disposableDomainListPath)
            resource.inputStream.bufferedReader()
                .readLines()
                .asSequence()
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .toSet()
        } catch (e: Exception) {
            log.warn("Failed to load disposable domain list from {}: {}", properties.disposableDomainListPath, e.message)
            emptySet()
        }
    }

    companion object {
        val EMAIL_REGEX = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)
    }
}
