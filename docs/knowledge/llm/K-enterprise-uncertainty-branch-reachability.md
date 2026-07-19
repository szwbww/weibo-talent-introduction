---
id: K-enterprise-uncertainty-branch-reachability
domain: llm
created: 2026-07-19
last_used: 2026-07-19
hit_count: 2
source: fix-v:ai-reply-05-trust-boundary-readiness-plan:fix-1
severity: P1
---
经验：企业来源明确“尚未匹配”时，只拒绝“已经匹配”的肯定句仍会放过省略不确定性的模糊回答，继续隐藏关键边界。
正确做法：每个 intent family 的语义门禁必须独立可达；enterprise 来源命中 uncertainty family 时，答案必须保留同族不确定性，且不得命中 certainty family。
反例：AiReplyHighRiskClaimValidator.kt:112-115 仅拒绝 certainty，允许“We will share details later.”省略未匹配限制。
