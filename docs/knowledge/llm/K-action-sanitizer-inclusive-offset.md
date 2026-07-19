---
id: K-action-sanitizer-inclusive-offset
domain: llm
created: 2026-07-19
last_used: 2026-07-19
hit_count: 4
source: fix-v:ai-reply-05a-action-policy-negation-plan:fix-2
severity: P1
---
经验：Kotlin 的 `IntRange` 是闭区间；把已有的 `start..end` 再转为 `start until end` 会遗漏最后一个字符，敏感 CTA 被清理后仍残留句点或末字符。
正确做法：span API 必须明确端点语义；闭区间平移后保持闭区间，删除测试必须断言整个输出而非仅断言敏感词消失。
反例：AiReplyActionPolicy.kt:157-161 对 `findPositiveSensitiveCtaSpans()` 的闭区间使用 `until span.last`，`Please send your ID card.` 输出 `.`。
