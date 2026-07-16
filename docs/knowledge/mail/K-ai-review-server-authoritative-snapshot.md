---
id: K-ai-review-server-authoritative-snapshot
domain: mail
created: 2026-07-15
last_used: 2026-07-16
hit_count: 15
source: fix-v:ai-reply-review-authority-fail-closed:fix-1
severity: P1
---
经验：AI 草稿审核若只信任浏览器提交的 source、readiness 或 unresolved snapshot，直接 API 可省略/伪造这些字段并绕过发送闸门；已有 authority 时忽略未知非空 source 也会放宽已定义的请求契约。
正确做法：首轮生成将不可预测的 draft identity 与 canonical readiness/snapshot 写入服务端审计记录；发送只按 identity 读取并校验服务端事实，客户端字段只作展示或携带，非空 source 必须在允许集合内；READY 必须对应空 unresolved snapshot，非 READY 必须对应非空 snapshot。
反例：`PendingMailOperationService.kt:206-214` 仅在客户端提供 `aiAuditRecordId` 时才校验；省略 identity 会直接进入 delivery。
