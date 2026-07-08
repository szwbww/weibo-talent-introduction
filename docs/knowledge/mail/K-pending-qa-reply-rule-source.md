---
id: K-pending-qa-reply-rule-source
domain: mail
created: 2026-07-04
last_used: 2026-07-08
hit_count: 2
source: fix-v:mail-compose-template:fix-2
severity: P1
---
经验：同一个前端选项接口被多个业务入口复用时，收口其中一个入口的数据源会连带破坏另一个入口；特别是 `mail-send-options` 从手动发送中移除 QA 后，待处理来信单规则 QA 回复不能再从这个接口取 QA。
正确做法：专家联系页手动发送 options 只承载可手动发送的系统模板和 compose template；待处理来信单规则 QA 回复必须从 `/api/qa/rules` 或已加载的 `state.qaRules` 取启用 QA 规则，保持 `PendingMailOperationService.sendQaReply()` 独立可用。
反例：`app.js:6858-6871` 用 `loadMailSendOptions()` 返回值过滤 `optionType === "QA"` 渲染单规则 QA 回复；`ManualExpertMailService.kt:33-58` 已不返回 QA，导致 UI 消失。
