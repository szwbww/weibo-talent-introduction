---
id: K-introduction-compose-callers
domain: template
created: 2026-07-06
last_used: 2026-07-08
hit_count: 9
source: create-p:template-expert-variables-and-fallback
---

`IntroductionMailComposer.compose()` 有且仅有 2 个调用方：

1. `InitialOutreachService.sendInitialBatch()` line 61 — 自动批量外发，不传 templateId
2. `ManualInitialOutreachService.runScheduledBatch()` line 297 — 手动/调度批量外发，传 `config.templateId`

两者都传入 `ExpertProfile` 实例。修改 `compose()` 的 variables map 时，两个调用方会自动继承新变量，无需额外改动。但对应的测试 mock（`IntroductionMailComposerTest`）中 `variables` 参数需要同步更新。
