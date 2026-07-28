---
id: K-grounded-natural-structure-server-gate
domain: llm
created: 2026-07-17
last_used: 2026-07-27
hit_count: 14
source: fix-v:qa-refactor-04-grounded-engine:fix-1
last_source: fix-v:ai-reply-failure-trust-closure-master-plan:blocked-after-fix-1
severity: P1
---
经验：只在 prompt 中要求自然段，模型仍可在 JSON answer 内塞入编号或章节标题，后端直接拼装会把模板化结构外发。
正确做法：在 materialized grounded text 进入 action policy 前由服务端拒绝或降级含编号列表、固定章节、内部标签的 answer，并走 deterministic fallback。
