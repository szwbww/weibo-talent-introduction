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
        val seenOrcids = mutableSetOf("0001", "0002")
        var fetchCalled = false
        val iterator = OutreachTargetIterator(
            retryableTargets = retryable,
            pageSize = 5,
            seenOrcids = seenOrcids,
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
            seenOrcids = mutableSetOf(),
            fetchNextPage = { offset, size -> esExperts.drop(offset).take(size) }
        )

        val collected = mutableListOf<String>()
        while (iterator.hasNext()) {
            collected += iterator.next().second.orcidId
        }

        assertEquals(esExperts.map { it.orcidId }, collected)
    }

    @Test
    fun `deduplicates across retryable and ES pages using seenOrcids`() {
        val retryable = listOf(Pair(contact("0001"), expert("0001")))
        val seenOrcids = mutableSetOf("0001")
        val iterator = OutreachTargetIterator(
            retryableTargets = retryable,
            pageSize = 5,
            seenOrcids = seenOrcids,
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
    fun `loads next ES page when entire page is filtered by seenOrcids`() {
        val seenOrcids = mutableSetOf("0001", "0002")
        var fetchCount = 0
        val iterator = OutreachTargetIterator(
            retryableTargets = emptyList(),
            pageSize = 2,
            seenOrcids = seenOrcids,
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
        val seenOrcids = mutableSetOf<String>()
        val iterator = OutreachTargetIterator(
            retryableTargets = emptyList(),
            pageSize = 2,
            seenOrcids = seenOrcids,
            fetchNextPage = { offset, size ->
                allExperts
                    .filterNot { seenOrcids.contains(it.orcidId) }
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
            seenOrcids = mutableSetOf(),
            fetchNextPage = { _, _ -> emptyList() }
        )

        assertFalse(iterator.hasNext())
    }

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
