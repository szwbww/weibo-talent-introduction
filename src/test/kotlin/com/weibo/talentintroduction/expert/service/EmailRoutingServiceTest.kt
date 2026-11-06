package com.weibo.talentintroduction.expert.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class EmailRoutingServiceTest {
    private fun classify(email: String, vararg records: String) = EmailRoutingService(object : MxLookupClient {
        override fun lookup(domain: String) = MxLookupResult.FOUND
        override fun lookupDetails(domain: String) = MxLookupDetails.parse(records.toList())
    }).identify(email)

    @Test fun `university custom domain with either official MX suffix is Microsoft`() {
        for (host in listOf("UNI-EDU.MAIL.PROTECTION.OUTLOOK.COM.", "uni-edu.a.mx.microsoft.")) {
            val result = classify("Person@Uni.edu", "0 $host")!!
            assertEquals("MICROSOFT", result.status)
            assertEquals("uni.edu", result.domain)
            assertEquals("MICROSOFT_MX", result.reason)
            assertEquals(1, result.mxRecords.size)
            assertNotNull(Instant.parse(result.checkedAt))
        }
    }

    @Test fun `suffix spoof and third party gateway are unknown not non Microsoft`() {
        for (host in listOf("evilmail.protection.outlook.com", "mail.protection.outlook.com.attacker.test", "mx.gateway.test")) {
            assertEquals("UNKNOWN", classify("p@uni.edu", "0 $host")!!.status)
        }
    }

    @Test fun `best preference must be wholly Microsoft and all evidence retained`() {
        assertEquals("UNKNOWN", classify("p@uni.edu", "0 gateway.test", "10 uni.mail.protection.outlook.com")!!.status)
        assertEquals("UNKNOWN", classify("p@uni.edu", "0 gateway.test", "0 uni.mail.protection.outlook.com")!!.status)
        val microsoft = classify("p@uni.edu", "0 uni.mail.protection.outlook.com", "10 gateway.test")!!
        assertEquals("MICROSOFT", microsoft.status)
        assertEquals(2, microsoft.mxRecords.size)
    }

    @Test fun `known consumer domain needs no DNS and invalid email never queried`() {
        val svc = EmailRoutingService(MxLookupClient { error("unexpected DNS") })
        assertEquals("MICROSOFT", svc.identify("p@HOTMAIL.COM")!!.status)
        for (email in listOf("", "missing-at", "p@@outlook.com", "p@..com", "<p@outlook.com>", "a(b)@uni.edu")) assertNull(svc.identify(email))
    }

    @Test fun `DNS failures and no MX remain unknown`() {
        for (state in listOf(MxLookupResult.DNS_ERROR, MxLookupResult.NOT_FOUND)) {
            val result = EmailRoutingService(MxLookupClient { state }).identify("p@uni.edu")!!
            assertEquals("UNKNOWN", result.status)
            assertTrue(result.mxRecords.isEmpty())
        }
    }

    @Test fun `MX parsing normalizes hostnames and handles null MX`() {
        assertEquals(listOf("0 uni.mail.protection.outlook.com", "10 backup.test"),
            MxLookupDetails.parse(listOf("10 BACKUP.TEST.", "0 UNI.MAIL.PROTECTION.OUTLOOK.COM.")).records.map { it.toString() })
        assertEquals(MxLookupResult.NOT_FOUND, MxLookupDetails.parse(listOf("0 .")).result)
    }

    @Test fun `domain cache single flight expiry and bounded eviction`() {
        var now = Instant.parse("2026-11-06T00:00:00Z")
        val calls = AtomicInteger()
        val cache = MxLookupCache(capacity = 2, now = { now }) { _ ->
            calls.incrementAndGet()
            MxLookupDetails.parse(listOf("0 uni.mail.protection.outlook.com"))
        }
        val pool = Executors.newFixedThreadPool(4)
        try {
            val latch = CountDownLatch(1)
            val results = (1..8).map { pool.submit<MxLookupDetails> { latch.await(); cache.get("uni.edu") } }
            latch.countDown()
            results.forEach { assertEquals(MxLookupResult.FOUND, it.get().result) }
            assertEquals(1, calls.get())
            now = now.plusSeconds(21601)
            cache.get("uni.edu")
            assertEquals(2, calls.get())
            cache.get("two.edu"); cache.get("three.edu"); cache.get("uni.edu")
            assertEquals(5, calls.get())
        } finally { pool.shutdownNow() }
    }

    @Test fun `DNS error cache is retried after one minute`() {
        var now = Instant.parse("2026-11-06T00:00:00Z")
        var calls = 0
        val cache = MxLookupCache(now = { now }) { _ -> calls++; MxLookupDetails(MxLookupResult.DNS_ERROR) }
        cache.get("uni.edu"); cache.get("uni.edu")
        assertEquals(1, calls)
        now = now.plusSeconds(61)
        cache.get("uni.edu")
        assertEquals(2, calls)
    }
}
