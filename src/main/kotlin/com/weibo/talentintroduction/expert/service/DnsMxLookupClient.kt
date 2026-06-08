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

    override fun lookup(domain: String): MxLookupResult {
        var ctx: InitialDirContext? = null
        return try {
            ctx = InitialDirContext(java.util.Hashtable<String, String>().apply {
                put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory")
                put("com.sun.jndi.dns.timeout.initial", properties.mxLookupTimeoutMs.toString())
                put("com.sun.jndi.dns.timeout.retries", "2")
            })
            val attrs = ctx.getAttributes(domain, arrayOf("MX"))
            val mx = attrs.get("MX")
            if (mx == null || mx.size() == 0) return MxLookupResult.NOT_FOUND
            for (i in 0 until mx.size()) {
                val record = mx.get(i).toString().trim()
                val parts = record.split("\\s+".toRegex(), 2)
                val exchange = if (parts.size == 2) parts[1].trim().trimEnd('.') else ""
                if (exchange.isNotEmpty() && exchange != ".") return MxLookupResult.FOUND
            }
            MxLookupResult.NOT_FOUND
        } catch (e: Exception) {
            log.debug("MX lookup failed for domain {}: {}", domain, e.message)
            MxLookupResult.DNS_ERROR
        } finally {
            try {
                ctx?.close()
            } catch (e: Exception) {
                log.debug("Failed to close DNS context: {}", e.message)
            }
        }
    }
}
