---
id: K-sensitive-material-cta-not-mention
domain: llm
created: 2026-07-19
last_used: 2026-08-20
hit_count: 9
source: fix-v:ai-reply-05-trust-boundary-readiness-plan:fix-1
severity: P1
---
经验：敏感材料词本身不是索取；把任意提及当作 CTA 会误删“不索要护照”等安全说明并错误触发 fallback。
正确做法：敏感材料拦截必须把材料词与正向直接请求语义绑定；否定、流程说明和非请求提及保持逐字不变，且否定不能掩盖同句后续的正向索取。
反例：AiReplyActionPolicy.kt:244-251 将“We do not request a passport.”误判为 `AI_REPLY_ACTION_SENSITIVE_MATERIAL`。
