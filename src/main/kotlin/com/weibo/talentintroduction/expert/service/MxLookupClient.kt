package com.weibo.talentintroduction.expert.service

fun interface MxLookupClient {
    fun lookup(domain: String): MxLookupResult

    /** Compatibility for validity-only implementations; FOUND alone is not provider evidence. */
    fun lookupDetails(domain: String): MxLookupDetails = MxLookupDetails(lookup(domain))
}

data class MxRecord(val preference: Int, val exchange: String) {
    override fun toString() = "$preference $exchange"
}

data class MxLookupDetails(
    val result: MxLookupResult,
    val records: List<MxRecord> = emptyList(),
    val checkedAt: java.time.Instant = java.time.Instant.now()
) {
    companion object {
        fun parse(values: List<String>): MxLookupDetails {
            val records = values.mapNotNull { raw ->
                val parts = raw.trim().split(Regex("\\s+"), limit = 2)
                val priority = parts.firstOrNull()?.toIntOrNull()
                val host = parts.getOrNull(1)?.trim()?.trimEnd('.')?.lowercase(java.util.Locale.ROOT)
                if (priority == null || priority !in 0..65535 || host.isNullOrBlank()) null else MxRecord(priority, host)
            }.distinct().sortedWith(compareBy<MxRecord> { it.preference }.thenBy { it.exchange })
            return MxLookupDetails(if (records.isEmpty()) MxLookupResult.NOT_FOUND else MxLookupResult.FOUND, records)
        }
    }
}

/** Bounded LRU, 64 single-flight stripes; no thread pool, no unbounded per-domain lock map. */
class MxLookupCache(
    private val capacity: Int = 5000,
    private val now: () -> java.time.Instant = { java.time.Instant.now() },
    private val load: (String) -> MxLookupDetails
) {
    private data class CacheEntry(val value: MxLookupDetails, val expiresAt: java.time.Instant)
    private val entries = object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?) = size > capacity
    }
    private val locks = Array(64) { Any() }
    init { require(capacity > 0) }

    fun get(domain: String): MxLookupDetails {
        val key = domain.trim().lowercase(java.util.Locale.ROOT).trimEnd('.')
        synchronized(locks[(key.hashCode() and Int.MAX_VALUE) % locks.size]) {
            synchronized(entries) { entries[key]?.takeIf { now().isBefore(it.expiresAt) }?.let { return it.value } }
            val value = load(key)
            val ttl = if (value.result == MxLookupResult.DNS_ERROR) 60L else 21600L
            synchronized(entries) { entries[key] = CacheEntry(value, now().plusSeconds(ttl)) }
            return value
        }
    }
}
