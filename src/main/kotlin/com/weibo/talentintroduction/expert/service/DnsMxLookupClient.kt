package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.config.EmailValidationProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import javax.naming.directory.InitialDirContext

@Component
class DnsMxLookupClient(
    private val properties: EmailValidationProperties
) : MxLookupClient {
    private val log = LoggerFactory.getLogger(DnsMxLookupClient::class.java)

    private val cache = MxLookupCache(load = ::lookupUncached)

    override fun lookup(domain: String): MxLookupResult = lookupDetails(domain).result

    override fun lookupDetails(domain: String): MxLookupDetails = cache.get(domain)

    private fun lookupUncached(domain: String): MxLookupDetails {
        var ctx: InitialDirContext? = null
        return try {
            ctx = InitialDirContext(java.util.Hashtable<String, String>().apply {
                put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory")
                put("com.sun.jndi.dns.timeout.initial", properties.mxLookupTimeoutMs.toString())
                put("com.sun.jndi.dns.timeout.retries", "2")
            })
            val attrs = ctx.getAttributes(domain, arrayOf("MX"))
            val mx = attrs.get("MX")
            MxLookupDetails.parse(if (mx == null) emptyList() else (0 until mx.size()).map { mx.get(it).toString() })
        } catch (e: Exception) {
            log.debug("MX lookup failed for domain {}: {}", domain, e.message)
            MxLookupDetails(MxLookupResult.DNS_ERROR)
        } finally {
            try {
                ctx?.close()
            } catch (e: Exception) {
                log.debug("Failed to close DNS context: {}", e.message)
            }
        }
    }
}
