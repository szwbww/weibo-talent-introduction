package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
