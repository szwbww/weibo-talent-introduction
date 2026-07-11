---
id: K-dual-outreach-paths
domain: mail
created: 2026-07-06
last_used: 2026-07-11
hit_count: 4
source: create-p:mail-personalization-anti-spam
---

系统有两条并行的介绍邮件发送路径，改其中一条时不得影响另一条：

1. **InitialOutreachService.sendInitialBatch()** — 简单循环，无内置 delay，由 `MailAutomationScheduler` 的 cron 调用。
2. **ManualInitialOutreachService.runScheduledBatch()** — 完整的 round-based 引擎，已有 `perMailIntervalMs` + `AccountRateLimiter` + round gate + daily cap + self-check。

两者共用 `IntroductionMailComposer.compose()` 组装邮件，共用 `SenderAccountAssignmentService` 选账号。修改 compose 或 assignment 会同时影响两条路径。
