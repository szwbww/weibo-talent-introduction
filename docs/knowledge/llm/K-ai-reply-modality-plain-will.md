---
id: K-ai-reply-modality-plain-will
domain: llm
created: 2026-07-16
last_used: 2026-07-16
hit_count: 6
source: fix-v:ai-reply-modality-strengthening:fix-2
severity: P1
---
经验：条件性 QA 写 `may/can/depends` 时，只拦截 `will definitely/guaranteed` 会放过普通 `will receive`；family 明确来源分支若短路通用强承诺词，或通用强词匹配大小写敏感，都会重新放过升级回答。
正确做法：modality 校验先以大小写不敏感方式拒绝条件来源中的既有通用强承诺词，再比较来源与回答的具体 predicate family；同 family 的明确来源只可允许普通 `will/shall`，不能覆盖 `guaranteed/absolutely` 等强化词。
反例：`4e4263a2^:AiReplyHighRiskClaimValidator.kt:164-167` — family short-circuit 曾绕过强词检查；旧强词检查无法匹配 `GUARANTEED`。
