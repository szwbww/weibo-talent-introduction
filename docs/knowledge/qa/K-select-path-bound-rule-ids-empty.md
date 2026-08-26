---
id: K-select-path-bound-rule-ids-empty
domain: qa
created: 2026-08-26
last_used: 2026-08-26
hit_count: 0
source: create-p:01-llm-fact-retrieval
severity: P2
---

# `select()` 返回的 `RequestFactItem.boundRuleIds` 恒为空——新代码不得依赖它

`QaFactSelectionService.buildRequestFact`（`:444-541`）**从不设置** `boundRuleIds`，
返回值走 `AiReplyDraftService.kt:374` 的默认 `emptyList()`。

三条 workbench 路径各自 `copy` 补齐：`:252`（matrix）、`:316`（legacy）、`:349`（auto）。
但 **`select()`（`:22-61`）没有任何 `copy(boundRuleIds = ...)`**。
`resolveLegacySelection` 的空选早返回分支（`:278`）同样没有，但那条分支下 `factRuleIds` 必然也为空，两者一致。

受影响读点：`AiReplyDraftService.kt:463`（`generateOperatorDirectedAnswer` 的 `boundFactsBlock`，
即"运营给这条问题附的参考事实"）与 `:541`（`promptRuleIds = (factRuleIds + boundRuleIds).distinct()`）——
经 `select()` 得到的条目上这两处都拿到空列表。

与 `AiReplyDraftService.kt:370-371` 的注释「生产路径一律在调用点显式赋值（I-1）」**不符**。

正确做法：在 `select()` 路径上写新逻辑时不得读 `boundRuleIds`；
若确需修，必须同时评估 `promptRuleIds` 变大对 `buildFreeFormMessages`（`:1222`）的影响。

关联：[[K-fact-matrix-two-semantics-in-one-field]]、[[K-ai-reply-prompt-vs-send-rule-ids]]
