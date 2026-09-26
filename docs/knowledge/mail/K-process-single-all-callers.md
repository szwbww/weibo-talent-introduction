---
id: K-process-single-all-callers
domain: mail
created: 2026-06-30
last_used: 2026-09-25
hit_count: 10
source: create-p:global-auto-reply-switch
---
`AutoMailReplyService.processSingle()` 是所有入站自动回复逻辑的唯一执行点。调用方共 6 处：
1. `AutoMailReplyService.receiveAndAutoReply()` — 单账号批量拉取
2. `AutoMailReplyService.processByUids()` — UID 回补（backfill-uids API）
3. `BatchAutoMailReplyService.pollAccounts()` → `receiveAndAutoReply()` — 全账号批量
4. `MailAutomationController.checkReplies()` → `batchAutoMailReplyService` — 手动"检查回复"
5. `MailQueueConsumer` (RabbitMQ async) → `receiveAndAutoReply()`
6. `MailAutomationScheduler` → `batchAutoMailReplyService.receiveAndAutoReplyAll()`

推论：任何"硬闸门"逻辑（全局开关、频控等）放在 `processSingle()` 内部即可拦截全部路径，无需在每个调用方重复。

2026-09-23 规划自检过滤时复核到一个反例：`AutoMailReplyService.receiveAndAutoReply()`
在调用 `processSingle()` 之前单独检查 `[self-check]`，但
`processByUids()` 直接调用 `processSingle()`，会绕过该过滤。
对所有入口都应生效的机器邮件闸门须放在共同入口、业务写事务之前，
并明确已确认 UID 的 `markSeen`/游标语义；只在批量循环加条件不够。
