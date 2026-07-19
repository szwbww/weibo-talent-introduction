---
id: K-readiness-evidence-revalidation
domain: llm
created: 2026-07-19
last_used: 2026-07-19
hit_count: 3
source: fix-v:ai-reply-05-trust-boundary-readiness-plan:fix-3
severity: P1
---
经验：readiness 在读取 evidence rule 时静默丢弃不存在、禁用或无事实正文的规则，会把残缺证据集误算为 READY。
正确做法：每个 evidence rule 都须在 readiness 计算时确认存在、启用且 answerBody 非空；任一不满足即 fail closed。
反例：AiReplyDraftService.kt:835-836。
