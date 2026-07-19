---
id: K-request-facts-not-flat-pool
domain: qa
created: 2026-07-12
last_used: 2026-07-19
hit_count: 27
source: create-p:ai-reply-02-request-fact-matrix
severity: P1
---
经验：多问题 AI 回复只传“扁平 QA 事实池 + request checklist”，模型和 deterministic fallback 都无法可靠知道哪条事实回答哪个问题，容易错配、重复或按 rule 顺序堆叠。
正确做法：在 `gapItems.candidateRuleIds` 尚未丢失时构建按原邮件顺序的 request→factRuleIds→groundingStatus 矩阵；生成与 fallback 共用矩阵，sendQaRuleIds 审计子集保持独立。
