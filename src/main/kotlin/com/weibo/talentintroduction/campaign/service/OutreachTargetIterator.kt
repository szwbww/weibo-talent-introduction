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

    companion object {
        fun demand(
            retryNext: (Int) -> List<Pair<ExpertContact?, ExpertProfile>>,
            esNext: (Int) -> List<ExpertProfile>,
            filterPage: (List<ExpertProfile>) -> List<ExpertProfile>,
            pageSize: () -> Int,
            shouldStop: () -> Boolean,
            dedupeKey: (ExpertProfile) -> String,
            onScanned: (Int) -> Unit,
            close: () -> Unit
        ): DemandOutreachTargetIterator = DemandOutreachTargetIterator(
            retryNext, esNext, filterPage, pageSize, shouldStop, dedupeKey, onScanned, close
        )
    }
}

/**
 * Forward-only demand source. NEW callbacks return already-filtered contacts in contact order;
 * ES pages are filtered here. An empty callback result alone means that source is exhausted:
 * callbacks must skip their internal raw filtered-empty pages rather than return them.
 *
 * Page sizes are reevaluated only before a source read. Short pages are not terminal, and
 * neither exhaustion nor the presence of further candidates is probed after a stop.
 * [onScanned] counts returned source rows before deduplication/ES filtering; callbacks account
 * separately for raw rows discarded internally. Only accepted identities enter the seen set.
 */
class DemandOutreachTargetIterator internal constructor(
    private val retryNext: (Int) -> List<Pair<ExpertContact?, ExpertProfile>>,
    private val esNext: (Int) -> List<ExpertProfile>,
    private val filterPage: (List<ExpertProfile>) -> List<ExpertProfile>,
    private val pageSize: () -> Int,
    private val shouldStop: () -> Boolean,
    private val dedupeKey: (ExpertProfile) -> String,
    private val onScanned: (Int) -> Unit,
    private val closeSource: () -> Unit
) : Iterator<Pair<ExpertContact?, ExpertProfile>>, AutoCloseable {
    private val seenKeys = mutableSetOf<String>()
    private var buffer: List<Pair<ExpertContact?, ExpertProfile>> = emptyList()
    private var bufferIndex = 0
    private var retryExhausted = false
    private var esExhausted = false
    private var closed = false

    val sourceExhausted: Boolean
        get() = retryExhausted && esExhausted

    override fun hasNext(): Boolean {
        if (closed || shouldStop()) return false
        if (bufferIndex < buffer.size) return true
        buffer = emptyList()
        bufferIndex = 0
        while (!closed && !shouldStop() && !sourceExhausted) {
            val size = pageSize()
            require(size in 1..500) { "Demand page size must be between 1 and 500: $size" }
            if (!retryExhausted) {
                val page = retryNext(size)
                onScanned(page.size)
                if (shouldStop()) return false
                if (page.isEmpty()) {
                    retryExhausted = true
                    continue
                }
                buffer = page.filter { seenKeys.add(dedupeKey(it.second)) }
            } else {
                val page = esNext(size)
                onScanned(page.size)
                if (shouldStop()) return false
                if (page.isEmpty()) {
                    esExhausted = true
                    continue
                }
                val unseen = page.filter { dedupeKey(it) !in seenKeys }
                val filtered = if (unseen.isEmpty()) emptyList() else filterPage(unseen)
                if (shouldStop()) return false
                buffer = filtered.mapNotNull { profile ->
                    if (seenKeys.add(dedupeKey(profile))) Pair(null, profile) else null
                }
            }
            if (buffer.isNotEmpty()) return true
        }
        return false
    }

    override fun next(): Pair<ExpertContact?, ExpertProfile> {
        if (!hasNext()) throw NoSuchElementException()
        return buffer[bufferIndex++]
    }

    override fun close() {
        if (closed) return
        closed = true
        buffer = emptyList()
        bufferIndex = 0
        closeSource()
    }
}
