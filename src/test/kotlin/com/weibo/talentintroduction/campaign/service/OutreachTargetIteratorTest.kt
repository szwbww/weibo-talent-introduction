package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.BatchExecutionMetadata
import com.weibo.talentintroduction.campaign.domain.BatchOutcomeReasonCodes
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.OutcomeAccumulator
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OutreachTargetIteratorTest {

    @Test
    fun `returns retryable targets before ES pages`() {
        val retryable = listOf(
            Pair(contact("0001"), expert("0001")),
            Pair(contact("0002"), expert("0002"))
        )
        val seenKeys = mutableSetOf("0001", "0002")
        var fetchCalled = false
        val iterator = OutreachTargetIterator(
            retryableTargets = retryable,
            pageSize = 5,
            seenKeys = seenKeys,
            fetchNextPage = { _, _ ->
                fetchCalled = true
                listOf(expert("0003"))
            }
        )

        assertTrue(iterator.hasNext())
        assertEquals("0001", iterator.next().second.orcidId)
        assertTrue(iterator.hasNext())
        assertEquals("0002", iterator.next().second.orcidId)
        assertTrue(iterator.hasNext())
        assertEquals("0003", iterator.next().second.orcidId)
        assertFalse(iterator.hasNext())
        assertTrue(fetchCalled)
    }

    @Test
    fun `paginates ES candidates and stops when last page is smaller than pageSize`() {
        val esExperts = listOf(
            expert("0001"), expert("0002"), expert("0003"),
            expert("0004"), expert("0005"), expert("0006"),
            expert("0007"), expert("0008")
        )
        val iterator = OutreachTargetIterator(
            retryableTargets = emptyList(),
            pageSize = 5,
            seenKeys = mutableSetOf(),
            fetchNextPage = { offset, size -> esExperts.drop(offset).take(size) }
        )

        val collected = mutableListOf<String>()
        while (iterator.hasNext()) {
            collected += iterator.next().second.orcidId
        }

        assertEquals(esExperts.map { it.orcidId }, collected)
    }

    @Test
    fun `deduplicates across retryable and ES pages using seenKeys`() {
        val retryable = listOf(Pair(contact("0001"), expert("0001")))
        val seenKeys = mutableSetOf("0001")
        val iterator = OutreachTargetIterator(
            retryableTargets = retryable,
            pageSize = 5,
            seenKeys = seenKeys,
            fetchNextPage = { _, _ ->
                listOf(expert("0001"), expert("0002"))
            }
        )

        val collected = mutableListOf<String>()
        while (iterator.hasNext()) {
            collected += iterator.next().second.orcidId
        }

        assertEquals(listOf("0001", "0002"), collected)
    }

    @Test
    fun `deduplicates generated email ids without uppercasing ES id`() {
        val seenKeys = mutableSetOf("EMAIL-f07688e64d3dc212a4d")
        val iterator = OutreachTargetIterator(
            retryableTargets = emptyList(),
            pageSize = 5,
            seenKeys = seenKeys,
            fetchNextPage = { _, _ ->
                listOf(expert("EMAIL-f07688e64d3dc212a4d"), expert("EMAIL-abc123"))
            }
        )

        val collected = mutableListOf<String>()
        while (iterator.hasNext()) {
            collected += iterator.next().second.orcidId
        }

        assertEquals(listOf("EMAIL-abc123"), collected)
    }

    @Test
    fun `loads next ES page when entire page is filtered by seenKeys`() {
        val seenKeys = mutableSetOf("0001", "0002")
        var fetchCount = 0
        val iterator = OutreachTargetIterator(
            retryableTargets = emptyList(),
            pageSize = 2,
            seenKeys = seenKeys,
            fetchNextPage = { offset, _ ->
                fetchCount++
                when (offset) {
                    0 -> listOf(expert("0001"), expert("0002"))
                    else -> listOf(expert("0003"))
                }
            }
        )

        assertTrue(iterator.hasNext())
        assertEquals("0003", iterator.next().second.orcidId)
        assertFalse(iterator.hasNext())
        assertEquals(2, fetchCount)
    }

    @Test
    fun `does not skip candidates when ES result set shrinks between pages`() {
        val allExperts = listOf(expert("0001"), expert("0002"), expert("0003"), expert("0004"))
        val seenKeys = mutableSetOf<String>()
        val iterator = OutreachTargetIterator(
            retryableTargets = emptyList(),
            pageSize = 2,
            seenKeys = seenKeys,
            fetchNextPage = { offset, size ->
                allExperts
                    .filterNot { seenKeys.contains(it.orcidId) }
                    .drop(offset)
                    .take(size)
            }
        )

        val collected = mutableListOf<String>()
        while (iterator.hasNext()) {
            collected += iterator.next().second.orcidId
        }

        assertEquals(listOf("0001", "0002", "0003", "0004"), collected)
    }

    @Test
    fun `empty candidate pool has no next element`() {
        val iterator = OutreachTargetIterator(
            retryableTargets = emptyList(),
            pageSize = 5,
            seenKeys = mutableSetOf(),
            fetchNextPage = { _, _ -> emptyList() }
        )

        assertFalse(iterator.hasNext())
    }

    @Test
    fun `fully excluded raw page advances offset and preserves later sendable candidates`() {
        val offsets = mutableListOf<Int>()
        val iterator = OutreachTargetIterator(
            retryableTargets = emptyList(),
            pageSize = 2,
            seenKeys = mutableSetOf(),
            fetchNextPage = { offset, _ ->
                offsets += offset
                when (offset) {
                    0 -> listOf(expert("0001"), expert("0002"))
                    2 -> listOf(expert("0003"))
                    else -> emptyList()
                }
            },
            filterPage = { page -> page.filter { it.orcidId == "0003" } }
        )

        assertTrue(iterator.hasNext())
        assertEquals("0003", iterator.next().second.orcidId)
        assertFalse(iterator.hasNext())
        assertEquals(listOf(0, 2), offsets)
    }

    @Test
    fun `cancellation callback prevents fetching another page`() {
        var stopped = true
        var fetchCount = 0
        val iterator = OutreachTargetIterator(
            retryableTargets = emptyList(),
            pageSize = 2,
            seenKeys = mutableSetOf(),
            fetchNextPage = { _, _ ->
                fetchCount++
                listOf(expert("0001"))
            },
            shouldStop = { stopped }
        )

        assertFalse(iterator.hasNext())
        assertEquals(0, fetchCount)
    }

    @Test
    fun `cancellation after excluded page prevents the next ES fetch`() {
        var cancelled = false
        val offsets = mutableListOf<Int>()
        val iterator = OutreachTargetIterator(
            retryableTargets = emptyList(),
            pageSize = 2,
            seenKeys = mutableSetOf(),
            fetchNextPage = { offset, _ ->
                offsets += offset
                listOf(expert("0001"), expert("0002"))
            },
            filterPage = {
                cancelled = true
                emptyList()
            },
            shouldStop = { cancelled }
        )

        assertFalse(iterator.hasNext())
        assertEquals(listOf(0), offsets)
    }

    @Test
    fun `filtered terminal page finishes without refetching it`() {
        val offsets = mutableListOf<Int>()
        val iterator = OutreachTargetIterator(
            retryableTargets = emptyList(),
            pageSize = 2,
            seenKeys = mutableSetOf(),
            fetchNextPage = { offset, _ ->
                offsets += offset
                if (offset == 0) listOf(expert("0001")) else emptyList()
            },
            filterPage = { emptyList() }
        )

        assertFalse(iterator.hasNext())
        assertEquals(listOf(0), offsets)
    }

    @Test
    fun `caller-provided dedupe key is registered after the decision and dedupes across pages (I-2)`() {
        // 调用方按人物键在完整判定（filterPage）后只保留每人的一个成员；去重键在判定后登记，
        // 后续页的同人成员不再进入判定（先判定后登记，不丢同人后续合格成员）。
        val personKeyByDocId = mapOf("d1" to "P1", "d2" to "P1", "d3" to "P2")
        val seenKeys = mutableSetOf<String>()
        val reviewed = mutableListOf<String>()
        val iterator = OutreachTargetIterator(
            retryableTargets = emptyList(),
            pageSize = 3,
            seenKeys = seenKeys,
            fetchNextPage = { offset, _ ->
                if (offset == 0) listOf(docExpert("d1"), docExpert("d2"), docExpert("d3")) else emptyList()
            },
            filterPage = { page ->
                reviewed += page.map { it.esDocId!! }
                page.distinctBy { personKeyByDocId[it.esDocId] ?: it.orcidId }
            },
            dedupeKey = { personKeyByDocId[it.esDocId] ?: it.orcidId }
        )

        val collected = mutableListOf<String>()
        while (iterator.hasNext()) {
            collected += iterator.next().second.esDocId!!
        }

        assertEquals(listOf("d1", "d3"), collected, "同一 person key 只保留一个成员")
        assertEquals(listOf("d1", "d2", "d3"), reviewed, "整页成员都进入调用方的完整判定")
        assertEquals(setOf("P1", "P2"), seenKeys)
    }

    @Test
    fun `demand reads NEW pages first and deduplicates within and across sources`() {
        val reads = mutableListOf<String>()
        val scanned = mutableListOf<Int>()
        val firstContact = contact("0001")
        var retryPage = 0
        var esPage = 0
        val iterator = demandIterator(
            retryNext = { size ->
                reads += "NEW:$size"
                when (retryPage++) {
                    0 -> listOf(firstContact to expert("0001"), contact("0001") to expert("0001"))
                    1 -> listOf(contact("0002") to expert("0002"))
                    else -> emptyList()
                }
            },
            esNext = { size ->
                reads += "ES:$size"
                if (esPage++ == 0) listOf(expert("0001"), expert("0003"), expert("0003"))
                else emptyList()
            },
            onScanned = { scanned += it }
        )

        assertTrue(reads.isEmpty(), "Construction must not fetch candidates")
        assertTrue(iterator.hasNext())
        assertSame(firstContact, iterator.next().first)
        assertEquals(listOf("NEW:60"), reads, "Buffered results must not prefetch")
        assertEquals(listOf("0002", "0003"), iterator.asSequence().map { it.second.orcidId }.toList())
        assertEquals(listOf("NEW:60", "NEW:60", "NEW:60", "ES:60", "ES:60"), reads)
        assertEquals(6, scanned.sum(), "Scanned includes duplicates, not just retained targets")
        assertTrue(iterator.sourceExhausted)
        assertThrows(NoSuchElementException::class.java) { iterator.next() }
    }

    @Test
    fun `demand shares caller email dedupe keys between NEW and distinct ES identities`() {
        var retryPage = 0
        var esPage = 0
        val iterator = demandIterator(
            retryNext = {
                if (retryPage++ == 0) {
                    listOf(contact("retry") to expert("retry").copy(email = "Shared@example.com"))
                } else emptyList()
            },
            esNext = {
                if (esPage++ == 0) {
                    listOf(expert("different-id").copy(email = "shared@example.com"), expert("unique"))
                } else emptyList()
            },
            dedupeKey = { it.email!!.lowercase() }
        )

        assertEquals(listOf("retry", "unique"), iterator.asSequence().map { it.second.orcidId }.toList())
        assertTrue(iterator.sourceExhausted)
    }

    @Test
    fun `demand continues filtered ES pages without reserving rejected identities`() {
        val reviewed = mutableListOf<List<String>>()
        var page = 0
        val iterator = demandIterator(
            esNext = {
                when (page++) {
                    0 -> listOf(docExpert("rejected"))
                    1 -> listOf(docExpert("accepted"), docExpert("duplicate"), docExpert("other"))
                    else -> emptyList()
                }
            },
            filterPage = { profiles ->
                reviewed += profiles.map { it.esDocId!! }
                profiles.filterNot { it.esDocId == "rejected" }
            },
            dedupeKey = { if (it.esDocId == "other") "P2" else "P1" }
        )

        assertEquals(listOf("accepted", "other"), iterator.asSequence().map { it.second.esDocId }.toList())
        assertEquals(listOf(listOf("rejected"), listOf("accepted", "duplicate", "other")), reviewed)
        assertTrue(iterator.sourceExhausted)
    }

    @Test
    fun `demand reevaluates page size and does not probe exhaustion after quota stop`() {
        var remaining = 30
        val requestedSizes = mutableListOf<Int>()
        var esPage = 0
        val iterator = demandIterator(
            esNext = { size ->
                requestedSizes += size
                listOf(expert("page-${esPage++}"))
            },
            pageSize = { minOf(500L, maxOf(1L, remaining.toLong() * 2)).toInt() },
            shouldStop = { remaining == 0 }
        )

        assertEquals("page-0", iterator.next().second.orcidId)
        remaining = 10
        assertEquals("page-1", iterator.next().second.orcidId)
        remaining = 0
        assertFalse(iterator.hasNext())
        assertThrows(NoSuchElementException::class.java) { iterator.next() }
        assertEquals(listOf(60, 20), requestedSizes)
        assertFalse(iterator.sourceExhausted, "Quota completion is not source exhaustion")
    }

    @Test
    fun `demand traverses more than 500 NEW and 11000 ES candidates with bounded pages`() {
        var retryOffset = 0
        var esOffset = 0
        var maxPageSize = 0
        var scanned = 0
        val iterator = demandIterator(
            retryNext = { size ->
                maxPageSize = maxOf(maxPageSize, size)
                val end = minOf(501, retryOffset + size)
                (retryOffset until end).map { index ->
                    val id = "NEW-$index"
                    contact(id) to expert(id)
                }.also { retryOffset = end }
            },
            esNext = { size ->
                maxPageSize = maxOf(maxPageSize, size)
                val end = minOf(11001, esOffset + size)
                (esOffset until end).map { expert("ES-$it") }.also { esOffset = end }
            },
            pageSize = { 500 },
            onScanned = { scanned += it }
        )

        var count = 0
        while (iterator.hasNext()) {
            val expected = if (count < 501) "NEW-$count" else "ES-${count - 501}"
            assertEquals(expected, iterator.next().second.orcidId)
            count++
        }
        assertEquals(11502, count)
        assertEquals(count, scanned)
        assertEquals(500, maxPageSize)
        assertTrue(iterator.sourceExhausted)
    }

    @Test
    fun `demand cancellation prevents reads and consumption of buffered candidates`() {
        var cancelled = true
        var retryReads = 0
        var esReads = 0
        val iterator = demandIterator(
            retryNext = {
                retryReads++
                listOf(contact("0001") to expert("0001"), contact("0002") to expert("0002"))
            },
            esNext = { esReads++; emptyList() },
            shouldStop = { cancelled }
        )

        assertFalse(iterator.hasNext())
        assertEquals(0, retryReads)
        cancelled = false
        assertEquals("0001", iterator.next().second.orcidId)
        cancelled = true
        assertFalse(iterator.hasNext())
        assertThrows(NoSuchElementException::class.java) { iterator.next() }
        assertEquals(1, retryReads)
        assertEquals(0, esReads)
        assertFalse(iterator.sourceExhausted)
    }

    @Test
    fun `demand cancellation during filtering prevents the next read`() {
        var cancelled = false
        var reads = 0
        val iterator = demandIterator(
            esNext = { reads++; listOf(expert("0001")) },
            filterPage = { cancelled = true; emptyList() },
            shouldStop = { cancelled }
        )

        assertFalse(iterator.hasNext())
        assertEquals(1, reads)
        assertFalse(iterator.sourceExhausted)
    }

    @Test
    fun `demand cancellation during empty read does not imply source exhaustion`() {
        var cancelled = false
        val iterator = demandIterator(
            esNext = { cancelled = true; emptyList() },
            shouldStop = { cancelled }
        )

        assertFalse(iterator.hasNext())
        assertFalse(iterator.sourceExhausted)
    }

    @Test
    fun `demand source failure propagates and closing is idempotent`() {
        val failure = IllegalStateException("candidate read failed")
        var closeCount = 0
        val iterator = demandIterator(
            esNext = { throw failure },
            close = { closeCount++ }
        )

        assertSame(failure, assertThrows(IllegalStateException::class.java) { iterator.hasNext() })
        assertFalse(iterator.sourceExhausted)
        iterator.close()
        iterator.close()
        assertEquals(1, closeCount)
        assertFalse(iterator.hasNext())
        assertThrows(NoSuchElementException::class.java) { iterator.next() }
    }

    @Test
    fun `demand close releases source only once even if release fails`() {
        var closes = 0
        val iterator = demandIterator(close = { closes++; throw IllegalStateException("close failed") })

        assertThrows(IllegalStateException::class.java) { iterator.close() }
        iterator.close()
        assertEquals(1, closes)
        assertFalse(iterator.hasNext())
        assertFalse(iterator.sourceExhausted)
    }

    @Test
    fun `demand rejects invalid dynamic page sizes before source reads`() {
        listOf(0, 501).forEach { size ->
            var reads = 0
            val iterator = demandIterator(
                retryNext = { reads++; emptyList() },
                pageSize = { size }
            )
            assertThrows(IllegalArgumentException::class.java) { iterator.hasNext() }
            assertEquals(0, reads)
            assertFalse(iterator.sourceExhausted)
        }
    }

    @Test
    fun `demand accumulator reports actual processed targets and controlled changed reason`() {
        val accumulator = OutcomeAccumulator.demand()
        assertEquals(0, accumulator.toBreakdown().target)
        repeat(30) { accumulator.recordSuccess() }
        repeat(5) { accumulator.recordFailure(BatchOutcomeReasonCodes.SEND_EXCEPTION) }
        repeat(5) { accumulator.recordSkipped(BatchOutcomeReasonCodes.CANDIDATE_CHANGED) }
        val outcome = accumulator.toBreakdown()

        assertEquals(40, outcome.target)
        assertEquals(30, outcome.success)
        assertEquals(5, outcome.failure)
        assertEquals(5, outcome.skipped)
        assertEquals(0, outcome.remaining)
        assertEquals(0, accumulator.remaining())
        assertEquals("候选信息已变化", outcome.skippedReasons[BatchOutcomeReasonCodes.CANDIDATE_CHANGED]?.label)
        assertEquals(5, outcome.skippedReasons[BatchOutcomeReasonCodes.CANDIDATE_CHANGED]?.count)
    }

    @Test
    fun `demand accumulator terminal stops never manufacture skipped candidates`() {
        listOf(
            "CANCELLED", "DAILY_CAP_REACHED", "NO_AVAILABLE_ACCOUNT", "NO_SENDABLE_ACCOUNT",
            "WARMUP_LIMIT_REACHED", "DAILY_LIMIT_REACHED", "CANDIDATE_READ_FAILED", "ONE_ROUND_DONE"
        ).forEach { reason ->
            val accumulator = OutcomeAccumulator.demand()
            accumulator.recordSuccess()
            accumulator.annotateTerminalRemaining(reason)
            assertEquals(1, accumulator.toBreakdown().target, reason)
            assertEquals(0, accumulator.skipped, reason)
            assertEquals(0, accumulator.remaining(), reason)
        }
    }

    @Test
    fun `legacy accumulator keeps fixed targets and terminal skipped behavior`() {
        val accumulator = OutcomeAccumulator(target = 30)
        repeat(10) { accumulator.recordSuccess() }
        accumulator.annotateTerminalRemaining("ONE_ROUND_DONE")
        assertEquals(20, accumulator.remaining())
        accumulator.annotateTerminalRemaining("CANCELLED")

        assertEquals(30, accumulator.toBreakdown().target)
        assertEquals(20, accumulator.skipped)
        assertEquals(20, accumulator.skippedReasonsMap()[BatchOutcomeReasonCodes.CANCELLED])
        assertEquals(0, accumulator.remaining())
    }

    @Test
    fun `demand accumulator guards processed count overflow before recording outcomes`() {
        val accumulator = OutcomeAccumulator.demand()
        OutcomeAccumulator::class.java.getDeclaredField("success").apply {
            isAccessible = true
            setInt(accumulator, Int.MAX_VALUE)
        }

        assertThrows(IllegalStateException::class.java) { accumulator.recordSuccess() }
        assertThrows(IllegalStateException::class.java) {
            accumulator.recordFailure(BatchOutcomeReasonCodes.SEND_EXCEPTION)
        }
        assertThrows(IllegalStateException::class.java) {
            accumulator.recordSkipped(BatchOutcomeReasonCodes.CANDIDATE_CHANGED)
        }
        assertEquals(Int.MAX_VALUE, accumulator.toBreakdown().target)
        assertEquals(0, accumulator.failure)
        assertEquals(0, accumulator.skipped)
    }

    @Test
    fun `execution metadata provider reflects current demand outcomes without changing breakdown`() {
        val accumulator = OutcomeAccumulator.demand()
        assertEquals(null, accumulator.executionMetadata)
        accumulator.executionMetadata = {
            BatchExecutionMetadata(30, 30L - accumulator.success, 40, false)
        }
        accumulator.recordSuccess()

        assertEquals(29L, accumulator.executionMetadata?.invoke()?.remainingSuccessQuota)
        assertEquals(1, accumulator.toBreakdown().target)
    }

    private fun demandIterator(
        retryNext: (Int) -> List<Pair<ExpertContact?, ExpertProfile>> = { emptyList() },
        esNext: (Int) -> List<ExpertProfile> = { emptyList() },
        filterPage: (List<ExpertProfile>) -> List<ExpertProfile> = { it },
        pageSize: () -> Int = { 60 },
        shouldStop: () -> Boolean = { false },
        dedupeKey: (ExpertProfile) -> String = { it.orcidId },
        onScanned: (Int) -> Unit = {},
        close: () -> Unit = {}
    ): DemandOutreachTargetIterator = OutreachTargetIterator.demand(
        retryNext, esNext, filterPage, pageSize, shouldStop, dedupeKey, onScanned, close
    )


    private fun docExpert(docId: String): ExpertProfile =
        ExpertProfile(
            esDocId = docId,
            orcidId = "EMAIL-$docId",
            email = "$docId@example.com",
            givenNames = "Given",
            familyNames = "Family",
            country = "China",
            keyword = "keyword",
            employment = "University"
        )

    private fun expert(orcidId: String): ExpertProfile =
        ExpertProfile(
            orcidId = orcidId,
            email = "$orcidId@example.com",
            givenNames = "Given",
            familyNames = "Family",
            country = "China",
            keyword = "keyword",
            employment = "University"
        )

    private fun contact(orcidId: String): ExpertContact =
        ExpertContact(
            id = 1L,
            campaignId = 10L,
            orcidId = orcidId,
            expertEmail = "$orcidId@example.com",
            expertName = null,
            currentStatus = "NEW"
        )
}
