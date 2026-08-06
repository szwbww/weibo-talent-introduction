---
id: K-material-reminder-single-compose-seam
domain: mail
created: 2026-08-06
last_used: 2026-08-06
hit_count: 0
source: create-p:material-reminder-01-threading
---

`MATERIAL_REMINDER` 只有**一个**组装点：`ManualExpertMailService.composeComposeTemplate()`（`:157-185`）。两条业务入口都汇入此处：

1. **批量提醒**：`ManualInitialOutreachService.runMaterialReminderBatch()` `:299-304` 构造 `ManualMailSendCommand(optionType = COMPOSE_TEMPLATE, optionValue = templateId)` → `manualExpertMailService.sendManualMail(contactId, command)`。
2. **手动单发**：专家详情页 → `/api/expert-contacts/{id}/mail` → 同一个 `sendManualMail`。

因此改提醒邮件的组装逻辑（主题、Message-ID、线程头、变量注入）**只需改这一处**，两条入口自动继承。

**不要套用 K-dual-outreach-paths**：那条描述的是 `INTRODUCTION` 的两条并行路径（`InitialOutreachService.sendInitialBatch()` 与 `ManualInitialOutreachService.runScheduledBatch()`，共用 `IntroductionMailComposer.compose()`），与提醒邮件无关。提醒邮件不经过 `IntroductionMailComposer`。

差异边界：批量入口额外受 `hasSentMaterialReminder()` 去重（`mailType='MATERIAL_REMINDER' AND sendStatus='SENT'`）、`buildMaterialReminderSnapshot()` 的 `funnelLevels=[APPLICATION] + tags=[承诺回复材料]` 约束；**手动单发不受任何此类约束**，可对任意 contact（含从未回信、无 INBOUND 记录者）发提醒。任何依赖"提醒对象必然有入站记录"的设计都必须有 fail-open 分支。
