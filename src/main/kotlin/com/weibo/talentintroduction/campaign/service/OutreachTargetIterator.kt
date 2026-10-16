package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIdNormalizer

/**
 * Lazy iterator: retryable contacts first, then ES candidates fetched page by page.
 * [seenKeys] is shared for the full iteration lifecycle (R-3 dedup equivalence).
 *
 * I-2: 去重键由调用者提供 —— 默认按规范化后的 `orcidId`（普通记录行为不变）；
 * 人物级调用者可传入人物去重键（`personId`，无映射时退回规范化 ORCID）。原始页内只对**未命中**
 * [seenKeys] 的成员调用 [filterPage]；去重键在**完整判定之后**才登记（只为 [filterPage] 实际
 * 保留的成员登记），因此「先标记后审核」不会让同人后续合格成员被静默丢弃。
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

            // 只对尚未处理的成员做完整判定；去重键在判定之后只为**保留**的成员登记，
            // 保证同一人物「本页成员被判定丢弃」不会连带丢掉后续页的合格成员。
            val unseen = page.filter { dedupeKey(it) !in seenKeys }
            val filtered = if (unseen.isEmpty()) emptyList() else filterPage(unseen)
            filtered.forEach { seenKeys.add(dedupeKey(it)) }
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
