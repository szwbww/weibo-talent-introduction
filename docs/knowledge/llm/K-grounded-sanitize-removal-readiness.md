---
id: K-grounded-sanitize-removal-readiness
domain: llm
created: 2026-07-19
last_used: 2026-07-19
hit_count: 3
source: fix-v:ai-reply-05-trust-boundary-readiness-plan:fix-3
severity: P1
---
经验：Grounded fallback 的最终 sanitizer 若删掉未授权动作却仍只按事实覆盖计算 readiness，会把被安全删改的草稿标为 READY。
正确做法：任何 Grounded 最终正文发生动作删除时，readiness 必须不为 READY；修复耗尽仍保持 BLOCKED。
反例：AiReplyDraftService.kt:571-581。
