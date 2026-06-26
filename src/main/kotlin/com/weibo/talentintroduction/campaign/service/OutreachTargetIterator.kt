package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIdNormalizer

/**
 * Lazy iterator: retryable contacts first, then ES candidates fetched page by page.
 * [seenOrcids] is shared for the full iteration lifecycle (R-3 dedup equivalence).
 */
class OutreachTargetIterator(
    retryableTargets: List<Pair<ExpertContact?, ExpertProfile>>,
    private val pageSize: Int,
    private val seenOrcids: MutableSet<String>,
    private val fetchNextPage: (offset: Int, size: Int) -> List<ExpertProfile>
) : Iterator<Pair<ExpertContact?, ExpertProfile>> {

    private val retryableIterator = retryableTargets.iterator()
    private var esBuffer: MutableList<Pair<ExpertContact?, ExpertProfile>> = mutableListOf()
    private var esBufferIndex = 0
    private var esOffset = 0
    private var esExhausted = false

    override fun hasNext(): Boolean {
        if (retryableIterator.hasNext()) return true
        if (esBufferIndex < esBuffer.size) return true
        if (esExhausted) return false
        loadNextEsPage()
        return esBufferIndex < esBuffer.size
    }

    override fun next(): Pair<ExpertContact?, ExpertProfile> {
        if (retryableIterator.hasNext()) return retryableIterator.next()
        if (esBufferIndex >= esBuffer.size && !esExhausted) loadNextEsPage()
        if (esBufferIndex >= esBuffer.size) throw NoSuchElementException()
        return esBuffer[esBufferIndex++]
    }

    private fun loadNextEsPage() {
        while (!esExhausted) {
            val page = fetchNextPage(esOffset, pageSize)
            if (page.size < pageSize) esExhausted = true
            if (page.isEmpty()) return

            esBuffer = mutableListOf()
            esBufferIndex = 0
            for (expert in page) {
                val normOrcid = ExpertIdNormalizer.normalize(expert.orcidId)
                if (seenOrcids.add(normOrcid)) {
                    esBuffer.add(Pair(null, expert))
                }
            }

            if (esBuffer.isNotEmpty()) {
                esOffset = 0
                return
            }

            esOffset += page.size
        }
    }
}
