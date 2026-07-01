---
id: K-composed-reply-order-contract
domain: qa
created: 2026-06-26
last_used: 2026-07-01
hit_count: 11
source: fix-v:qa-rules-phase3:fix-1
severity: P1
---
经验：人工可排序的规则组装链路必须让 UI 预览、payload、后端外发正文、审计 ordinal 使用同一顺序，否则运营调整顺序只会写进日志/关联表，实际邮件正文仍按系统默认排序。
正确做法：把 `qaRuleIds` 的当前顺序作为人工路径的排序契约；前端预览和后端 composer 都按该顺序组装，并用测试覆盖逆序选择场景。
反例：`app.js:4602` 预览按 `composeOrder/id` 重排，`PendingMailOperationService.kt:276-280` 调用 `QaReplyComposer.compose`，`QaReplyComposer.kt:25-30` 又按 `composeOrder/priority/id` 排序。
