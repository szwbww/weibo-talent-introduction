---
id: K-ai-reply-profile-absence-warning
domain: llm
created: 2026-07-12
last_used: 2026-07-19
hit_count: 13
source: fix-v:ai-reply-grounded-parity-backend:fix-1
severity: P1
---
经验：只读画像查询的“未找到”与“查询异常”都属于上下文不可用；只在异常时报警会把缺失 ES 文档静默降级为普通资料不足，丢失操作诊断。
正确做法：当 ORCID 缺失、目标层及允许回退层均无画像、或查询异常时，统一返回 `EXPERT_PROFILE_NOT_FOUND`；研究类请求另加 `EXPERT_RESEARCH_CONTEXT_INSUFFICIENT`，且全程只读查询，不触发 enrichment。
反例：`AiReplyContextService.kt:68-82` — 正常 null 返回未写 warning。
