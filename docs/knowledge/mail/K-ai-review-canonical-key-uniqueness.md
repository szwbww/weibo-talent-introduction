---
id: K-ai-review-canonical-key-uniqueness
domain: mail
created: 2026-07-15
last_used: 2026-07-16
hit_count: 13
source: fix-v:ai-reply-review-authority-fail-closed:fix-1
severity: P1
---
经验：要求“确认集合完全相等且无重复”时，先把未确认快照转 Set 会掩盖快照自身的重复项；把 requestIndex、intentKey 或 unresolvedCount 当作可选/宽松 Number 也会让损坏 snapshot 伪造出可确认 key。
正确做法：在任何集合比较前严格验证 canonical snapshot：列表项必须是对象，reviewKey/requestIndex/intentKey/count 都必须有正确 JSON 类型，key 精确匹配 `{requestIndex}:{intentKey}`，count 为非负整数且等于列表长度；异常记录 fail closed。
反例：`AiReplyReviewAuditService.kt:127-149` 对 unresolved key 直接 `toSet()`。
