---
id: K-request-key-includes-intent-keys
domain: llm
created: 2026-08-21
last_used: 2026-08-26
hit_count: 3
source: create-p:02-bound-facts-become-partial-evidence
severity: P1
---

# `requestKey` 的哈希输入含 intent key 列表——增删 intent coverage 条目会让工作台整个打不开

`TrustReplyWorkbenchService.kt:2077-2090` 的 `requestKey(sourceVersion, index, requestText, intentKeys)`
把 `intentKeys.joinToString(...)` 拼进 canonical 串再取 sha256 前 32 位。

两个投影必须逐字一致，否则 `validateMatrixKeys` 抛 `TRUST_REPLY_REQUEST_KEY_INVALID` /
`TRUST_REPLY_FACT_SELECTION_INVALID`，**bootstrap 直接失败、工作台打不开**，且全部历史锁定项作废：

| 投影 | 位置 | intentKeys 来源 |
|---|---|---|
| canonical | `TrustReplyWorkbenchService.canonicalRequests`（`:1726`） | `AiReplyIntentCatalog.matchIntents(request.text).map { it.key }` |
| item | `TrustReplyWorkbenchService.requestKey(sourceVersion, item)`（`:1854-1859`） | `item.intents.map { it.intentKey }` |

## 今日一致的原因（很容易被无意破坏）

`AiReplyIntentCatalog.matchIntentsWithSpans`（`:401-414`）在**零命中**时会**合成**一条
`general.answer` 意图定义；`matchIntents`（`:436-437`）只是它的 `.map { it.definition }`。
`QaFactSelectionService.buildRequestFact`（`:405-423`）用同一个函数产出 `matchedIntents`，
再一一映射成 `intentCoverages`。**所以 `item.intents` 与 canonical 的 intentKeys 天然逐元素相等。**

## 安全边界

- **安全**：改某条已存在 coverage 的 `status`、`evidenceRuleIds`、`missingEvidenceKeys`。
  这些字段不进 `requestKey`。
- **不安全**：给 `intentCoverages` 追加条目、移除条目、改 `intentKey` 字面量、改条目顺序
  （`joinToString` 保序，顺序进哈希）。

想让「运营手动绑定的事实成为证据」时，直觉做法是「没有合适的意图就新造一条 `general.answer`」——
**这条直觉是错的**。正确做法是只在该条目**已经**有 `general.answer` coverage（即零具名意图命中）
时并入它；有具名意图但绑定对不上任何意图时，让绑定落进 `droppedBindingRuleIds`，不要造新条目。

关联：[[K-fact-matrix-two-semantics-in-one-field]]、[[K-request-fact-assignment-version-must-include-mapping]]、[[K-workbench-evidence-two-layer-global-coupling]]
