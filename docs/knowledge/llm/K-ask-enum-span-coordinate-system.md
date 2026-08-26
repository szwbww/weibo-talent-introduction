---
id: K-ask-enum-span-coordinate-system
domain: llm
created: 2026-08-24
last_used: 2026-08-26
hit_count: 2
source: systematic-debugging:live-inbound-124
severity: P1
---

# ask enumeration 与 intent span 必须使用同一坐标系

`InboundAskEnumerator.parse(inboundText, ...)` 产生的 `EnumeratedAsk.originalRange` 是整封 `inboundText` 的绝对偏移；`AiReplyIntentCatalog.matchIntentsWithSpans(requestText)` 产生的 `MatchedIntentSpan.originalRanges` 是单条 `requestText` 的局部偏移。两者直接交给 `claimed()` 比较时，只要 request 不从全文 offset 0 开始，所有本应重叠的 ask 都会被误判为 unrecognized。

线上样本 `LIVE_INBOUND:124`：问题从全文 offset 246 开始；`eligibility/application process/timeline` 的全文 offset 分别是 296/322/343，但 intent span 在 request 内分别约为 50/76/97。日志因此出现 `enumerated=4 claimed=0 unrecognized=4`。

正确边界：调用 `claimed` 前把 request-local intent ranges 加上 `RequestUnit.startOffset` 转为全文绝对偏移，或把 ask range减去同一 startOffset 转为 request-local；只能选择一种，且测试必须包含前置段落使 request startOffset > 0。`requestRange` 只负责 ask 归属，不能代替 span rebasing。

## 2026-08-26 复核：该缺陷已修复

`QaFactSelectionService.buildRequestFact:483-495` 已做 rebase——
`absoluteMatchedSpans = matchedSpans.map { span.copy(originalRanges = span.originalRanges.map { it.first + range.first..it.last + range.first }) }`，
随后 `:496-501` 的 `unrecognizedAsks` 过滤用的是 `absoluteMatchedSpans`。
本条保留为**回归保护**：任何改动 `buildRequestFact` 里 span/ask 比较的计划，
都必须保住这次 rebase，且测试 fixture 必须包含前置段落使 request `startOffset > 0`。
