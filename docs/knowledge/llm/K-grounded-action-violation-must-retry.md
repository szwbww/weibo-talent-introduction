---
id: K-grounded-action-violation-must-retry
domain: llm
created: 2026-07-19
last_used: 2026-07-19
hit_count: 8
source: fix-v:ai-reply-05-trust-boundary-readiness-plan:fix-2
severity: P1
---
经验：Grounded candidate 收到无 code 的未授权 action violation 后若只收集非空 code，会把会议/CV CTA 当作 valid candidate，最后 sanitize 掉而跳过统一修复与 BLOCKED 语义。
正确做法：findViolations 的每个结果都必须转换为稳定 validation warning，进入同一个“一次修复→耗尽 fallback/BLOCKED”管线；自动决策也必须把该 warning 归类为 validation failure。
反例：AiReplyDraftService.kt:473-480 仅添加 v.code != null 的 violation。
