---
id: K-free-form-fallback-nonempty
domain: qa
created: 2026-07-01
last_used: 2026-07-02
hit_count: 4
source: fix-v:04-frontend-tab:fix-1
severity: P1
---
经验：FREE_FORM AI 草稿链路如果声明 LLM 关闭/失败时有确定性兜底，调用方不能传空 `qaRuleIds` 后直接复用 QA_MATCHED 的 fallback；否则 `promptRuleIds` 为空会返回空草稿。
正确做法：FREE_FORM fallback 要有独立的非空确定性文本来源，例如基于 inbound/profile/history/训练知识拼一个保守草稿；同时保持发送用 `qaRuleIds` 为空，避免污染 QA 审计。
反例：`AiTrainingController.kt:83-89` 模拟调用传 `qaRuleIds=emptyList()`，`AiReplyDraftService.kt:238-242` 在 LLM 关闭时因 `promptRuleIds` 为空返回 `""`。
