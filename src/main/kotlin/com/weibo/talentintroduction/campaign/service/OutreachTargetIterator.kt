package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIdNormalizer

/**
 * Lazy iterator: retryable contacts first, then ES candidates fetched page by page.
 * [seenKeys] is shared for the full iteration lifecycle (R-3 dedup equivalence).
 *
 * I-2: 去重键由调用者提供 —— 默认按规范化后的 `orcidId`（普通记录行为不变）；
 * 人物级调用者可传入人物去重键（`personId`，无映射时退回 docId）。去重键在 [filterPage]
 * **之前**登记，仅作原始页/重试的廉价去重；人物级「已处理」必须由调用方在完整判定后自行维护
 * （见 [DiscoveryPersonRecipientService]），不得依赖这里的先标记后审核顺序。
 */
class OutreachTargetIterator(
    retryableTargets: List<Pair<ExpertContact?, ExpertProfile>>,
    private val pageSize: Int,
    private val seenKeys: MutableSet<String>,
    private val fetchNextPage: (offset: Int, size: Int) -> List<ExpertProfile>,
    private val filterPage: (List<ExpertProfile>) -> List<ExpertProfile> = { it },
    private val shouldStop: () -> Boolean = { false },
    private val dedupeKey: (ExpertProfile) -> String = { ExpertIdNormalizer.normalize(it.orcidId) }
) : Iterator<Pair<ExpertContact?, ExpertProfile>> {

    private val retryableIterator = retryableTargets.iterator()
    private var esBuffer: MutableList<Pair<ExpertContact?, ExpertProfile>> = mutableListOf()
    private var esBufferIndex = 0
    private var esOffset = 0
    private var esExhausted = false

    override fun hasNext(): Boolean {
        if (shouldStop()) return false
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
        while (!esExhausted && !shouldStop()) {
            val page = fetchNextPage(esOffset, pageSize)
            if (page.size < pageSize) esExhausted = true
            if (page.isEmpty()) return

            val unseen = mutableListOf<ExpertProfile>()
            for (expert in page) {
                if (seenKeys.add(dedupeKey(expert))) unseen.add(expert)
            }
            val filtered = if (unseen.isEmpty()) emptyList() else filterPage(unseen)
            esBuffer = filtered.mapTo(mutableListOf()) { Pair(null, it) }
            esBufferIndex = 0
            if (esBuffer.isNotEmpty()) {
                esOffset = 0
                return
            }
            esOffset += page.size
        }
    }
}
