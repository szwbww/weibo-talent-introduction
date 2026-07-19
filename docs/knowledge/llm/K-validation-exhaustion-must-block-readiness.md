---
id: K-validation-exhaustion-must-block-readiness
domain: llm
created: 2026-07-19
last_used: 2026-07-19
hit_count: 6
source: fix-v:ai-reply-05-trust-boundary-readiness-plan:fix-1
severity: P1
---
经验：fallback 若只从事实覆盖重算 readiness，会丢失“两次模型校验失败”这一确定性安全状态，完整事实可被误标 READY。
正确做法：validation/action failure 必须作为 readiness 的显式最高优先级输入；正常 LLM-disabled fallback 与修复耗尽分开建模。
反例：AiReplyDraftService.kt:404-421,576。
