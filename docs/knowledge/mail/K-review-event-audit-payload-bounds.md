---
id: K-review-event-audit-payload-bounds
domain: mail
created: 2026-07-15
last_used: 2026-08-16
hit_count: 23
source: fix-v:ai-reply-review-authority-fail-closed:fix-1
last_source: fix-v:ai-reply-07-final-send-integrity-plan:stop-after-fix-3
severity: P1
---
经验：面向操作端的审计事件若原样保存客户端列表/字符串，可被超长或超量 payload 膨胀，并把不应留存的内容复制进日志。
正确做法：保留真实总数，限制保存项数与每项长度，并标记截断；审计只存稳定 key，不存正文或可替代正文的字段。
反例：`AiReplyReviewAuditService.kt:95-115` 原样记录所有 `unresolvedKeys`。
