---
id: K-grounded-json-materialize-before-policy
domain: llm
created: 2026-07-27
last_used: 2026-07-27
hit_count: 39
source: create-p:ai-reply-grounded-server-owned-envelope-plan
last_source: create-p:ai-reply-grounded-server-owned-envelope-plan
severity: P1
---
经验：多请求 grounded LLM 若直接返回整封字符串，后端无法验证 claim 完整性；若又要求模型复制 request/source/paragraph/missingFacts/review 元数据，则会把服务端已知的确定性信封变成概率性失败点。
正确做法：服务端持有 immutable content plan 与全部元数据，模型只返回 exact claimKey→text 和独立 actionText；初始响应与修复响应都先校验 exact unique key set、绑定 plan，再进入 claim/trust/action policy 和服务端 composer。无效候选走确定性 fallback，raw JSON 永不进入 response。
