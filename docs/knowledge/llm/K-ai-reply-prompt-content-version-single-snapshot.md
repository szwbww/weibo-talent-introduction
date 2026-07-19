---
id: K-ai-reply-prompt-content-version-single-snapshot
domain: llm
created: 2026-07-19
last_used: 2026-07-20
hit_count: 2
source: fix-v:ai-reply-06-draft-audit-evidence-preflight-plan:fix-1
severity: P1
---
经验：先读取 effective DTO 算版本、再经另一方法读取 Prompt 内容，在运营更新配置并发发生时会把 A 内容标成 B 版本。
正确做法：一次生成仅构造一个不可变 Prompt snapshot，并把其 systemPrompt 与 version 传给消息构造、LLM、回退和结果。
反例：AiReplyDraftService.kt:156, 227-235, 969-993, 1389-1403。
