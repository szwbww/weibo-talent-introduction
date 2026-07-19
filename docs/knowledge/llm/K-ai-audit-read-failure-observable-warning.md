---
id: K-ai-audit-read-failure-observable-warning
domain: llm
created: 2026-07-19
last_used: 2026-07-20
hit_count: 2
source: fix-v:ai-reply-06-draft-audit-evidence-preflight-plan:fix-1
severity: P1
---
经验：证据读取失败若只把 source 标 unavailable 而不写稳定 warning，审计与前端无法区分“事实本来不可用”和“观测失败”。
正确做法：保留草稿返回和 unavailable source，同时在 result/snapshot 写入稳定、无正文的观测 warning。
反例：AiReplyDraftService.kt:1356-1368。
