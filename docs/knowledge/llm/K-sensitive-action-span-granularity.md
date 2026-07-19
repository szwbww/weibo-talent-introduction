---
id: K-sensitive-action-span-granularity
domain: llm
created: 2026-07-19
last_used: 2026-07-19
hit_count: 4
source: fix-v:ai-reply-05a-action-policy-negation-plan:fix-1
severity: P1
---
经验：敏感材料 CTA 以整句为删除单位时，同一句前置的安全否定说明会被连带删除，虽拦截了索取但破坏保留语义。
正确做法：检测应返回正向 CTA 的精确原始 span；`findViolations()` 与 `sanitize()` 共用该 span，sanitize 只删除命中的 CTA，不删除否定说明或其他保留文本。
反例：AiReplyActionPolicy.kt:255-267 仅返回句级 Boolean，sanitize 在 157-160 删除整个 TextUnit。
