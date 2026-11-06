package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.expert.domain.EmailRouting
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class EmailRoutingService(private val mxLookupClient: MxLookupClient) {
    fun identify(email: String?): EmailRouting? {
        val domain = EmailRouting.domainOf(email) ?: return null
        if (domain in setOf("outlook.com", "hotmail.com", "live.com", "msn.com")) {
            return EmailRouting(domain, "MICROSOFT", "CONSUMER_DOMAIN", emptyList(), Instant.now().toString())
        }
        val lookup = try { mxLookupClient.lookupDetails(domain) }
            catch (_: Exception) { MxLookupDetails(MxLookupResult.DNS_ERROR) }
        val primary = lookup.records.minOfOrNull { it.preference }?.let { preference ->
            lookup.records.filter { it.preference == preference }
        }.orEmpty()
        fun microsoft(host: String) = host.endsWith(".mail.protection.outlook.com") || host.endsWith(".mx.microsoft")
        val confirmed = lookup.result == MxLookupResult.FOUND && primary.isNotEmpty() && primary.all { microsoft(it.exchange) }
        val reason = when {
            confirmed -> "MICROSOFT_MX"
            lookup.result == MxLookupResult.DNS_ERROR -> "DNS_ERROR"
            lookup.result == MxLookupResult.NOT_FOUND -> "NO_MX"
            primary.isEmpty() -> "NO_MX_EVIDENCE"
            primary.any { microsoft(it.exchange) } -> "MIXED_MX"
            else -> "OTHER_MX"
        }
        return EmailRouting(domain, if (confirmed) "MICROSOFT" else "UNKNOWN", reason,
            lookup.records.map { it.toString() }, lookup.checkedAt.toString())
    }
}
