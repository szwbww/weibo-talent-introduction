---
id: K-grounded-trust-family-route
domain: llm
created: 2026-07-19
last_used: 2026-07-19
hit_count: 6
source: fix-v:ai-reply-04-grounded-trust-content-plan:fix-1
severity: P1
---
经验：Grounded 的高风险缺事实路由若只列出少数 catalog key，会把同一 trust family 的公司核验、合同主体或费用问题回退为 FREE_FORM，模型可在没有证据时自由作答。
正确做法：路由规则按计划声明的完整 key 前缀/明确 key 集合覆盖，并用 catalog 中每个 trust-sensitive key 的无事实 fixture 断言 `QA_GROUNDED + BLOCKED`。
反例：AiReplyGroundedContentPlanner.kt:177-195 未覆盖 `company.verification_evidence`、`contract.party`、`fees.policy`。
