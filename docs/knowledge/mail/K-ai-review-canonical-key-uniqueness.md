---
id: K-ai-review-canonical-key-uniqueness
domain: mail
created: 2026-07-15
last_used: 2026-07-16
hit_count: 6
source: fix-v:ai-reply-08-p2-review-audit-backend:fix-1
severity: P1
---
经验：要求“确认集合完全相等且无重复”时，先把未确认快照转 Set 会掩盖快照自身的重复项。
正确做法：在任何集合比较前验证 canonical snapshot 的 reviewKey 唯一，且 key 精确匹配 `{requestIndex}:{intentKey}`；异常记录 fail closed。
反例：`AiReplyReviewAuditService.kt:127-149` 对 unresolved key 直接 `toSet()`。
