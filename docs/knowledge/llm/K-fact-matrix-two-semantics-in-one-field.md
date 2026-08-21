---
id: K-fact-matrix-two-semantics-in-one-field
domain: llm
created: 2026-08-20
last_used: 2026-08-20
hit_count: 0
source: create-p:P1-fact-binding-drop-not-fatal / P2a-bound-vs-evidence-split
severity: P1
---

# `RequestFactItem.factRuleIds` 同时承担两个语义，导致手动绑定在最需要它时必然失败

`QaFactSelectionService.resolveMatrixSelection` 拿运营显式绑定的 `explicitIds` 跑一遍
`buildRequestFact`，然后要求**过滤后的结果等于运营的输入**：

```kotlin
if (item.factRuleIds != explicitIds) throw TrustReplyWorkbenchException(422, "TRUST_REPLY_FACT_SELECTION_INVALID")
```

而 `factRuleIds` 是两层过滤的产物：关键词必须命中本条摘要文本（`candidateRules`），
且必须落在 `status == "SUPPORTED"` 的意图证据集里（`evidenceSet`）。

**推导闭合**：`UNSUPPORTED` ⟹ 无 SUPPORTED 意图 ⟹ `evidenceSet` 恒空 ⟹ `factRuleIds` 恒 `[]`
⟹ 只要绑了任何事实就 `[] != explicitIds` ⟹ **必抛 422，且这个 422 出在 bootstrap 上，工作台整个打不开**。

「+ 添加事实」只在"本来就能自动匹配上"时有效，在真正需要它的时候一定坏。

次要坑：`QaFactKeywordMatcher.matchesRule` 对**没有关键词**的规则直接 `return false`，
所以关键词为空的事实在任何摘要上都绑不了。

## 根源：一个字段两个消费者

| 语义 | 谁需要 | 典型消费点 |
|---|---|---|
| **运营绑了什么** | 运营 / 前端 | `canonicalMatrix`（回传+落快照）、`requestEvidenceVersion`（版本身份）、`toCoverage`（chips） |
| **系统认可什么是证据** | AI / 外发审计 | `sendQaRuleIds`、`promptRuleIds`、`canonicalizeClaims`、`AiReplyGroundedContentPlanner`、`AutoReplyConfidenceScorer`、`AiReplyReviewAuditService` |

实测 `grep -rn "\.factRuleIds" --include=*.kt src/main | wc -l` → **30 处**，跨 10 个文件。
要拆必须分刀，一次全改超出任何合理的验证面。

## 改这条链路前必读的三件事

1. **前端有一份自带的同款相等性校验**：`trust-reply-workbench.js` 的 `applyBootstrap` 比对
   `data.requestFactSelections`（来自 `canonicalMatrix`）与 `requestCoverage[].factRuleIds`
   （来自 `toCoverage`），不一致即 `throw new Error("TRUST_REPLY_FACT_SELECTION_INVALID")`。
   **只改服务端一侧 = 把 422 换成前端异常，工作台照样打不开。** 这两个投影必须同时切换。
2. **`TRUST_REPLY_FACT_SELECTION_INVALID` 在 `src/main` 有 7 个抛点**（`QaFactSelectionService`
   5 个含 legacy 路径 2 个，`TrustReplyWorkbenchService.validateMatrixKeys` 2 个）。
   只有"服务端过滤掉了运营绑定"那一个该降级，其余（条数不等、规则停用/不存在/正文空、
   矩阵未覆盖全部摘要、factId ≤ 0）都是真脏输入，必须继续硬拦。
3. **版本身份的恒等性**：显式矩阵路径下 `validateExplicitSelection` 按 `explicitIds` 顺序返回，
   `filter` 全程保序，所以**全部绑定都被采纳时 `factRuleIds` 与 `explicitIds` 逐元素逐顺序相等**。
   把 `requestEvidenceVersion` 从 `factRuleIds` 切到 `boundRuleIds` 因此对既有条目是恒等变换，
   不会让历史锁定项批量失效——但顺序或去重方式一旦被改动就会全站作废。

## `UNSUPPORTED` 条目拿不到 prompt 事实（改 promptRuleIds 无效）

`AiReplyDraftService.generateItem` 里 `OMIT` / `ACKNOWLEDGE_PENDING` / `ANSWER_FROM_OPERATOR_INPUT`
三个分支**全部在构造 `ResolvedQaRules` 之前就 return**，而 `validateItemHandling` 规定
`UNSUPPORTED` 的允许集恰好就是这三个。所以"让绑定的事实进 prompt"对 `UNSUPPORTED` 条目
**只改 `promptRuleIds` 是零效果的**，必须另给 `generateOperatorDirectedAnswer` 加一条事实通道，
而那会修订 operator-directed 的 system message 契约（见 [[K-operator-directed-authorization-seam]]）。

## 2026-08-21 更新：D1/D2 已推翻，绑定事实可成为证据

计划 `02-bound-facts-become-partial-evidence` 落地后，本文档的两条需求方决策作废：

- **D1（手动绑定不改变 status）已推翻**：绑定事实现在可以改变 status——零意图命中的摘要
  绑定事实后，条目从 `UNSUPPORTED` 变为 `PARTIAL`（「有部分依据」）。解法是 I-5 的
  「允许集由条目而非 status 决定」：「按回答说明生成」被显式加进含运营绑定的 `PARTIAL`
  允许集，不再需要靠冻结 status 保住 handling。
- **D2（绑定但非证据的事实不进 sendQaRuleIds）已推翻**：「真证据」的定义扩展为
  「系统匹配认可 ∪ 运营显式担保且已生效」。绑定事实经 `buildRequestFact` 的绕过路径
  成为 `factRuleIds` 成员，从而**自动**进入 `sendQaRuleIds` 外发审计（I-10：不得在
  `workbenchResult` 额外并入 `boundRuleIds`）。

实现要点（全部在 `QaFactSelectionService.buildRequestFact` 内，I-1/I-2/I-4）：

- 新增 `operatorBound` 形参，**只有** `resolveMatrixSelection`（`:190`）传 `true`；
  auto/legacy 四处保持默认 `false`，自动回复链路逐字不变。
- 绕过只改写已存在的 `RequestIntentCoverage` 条目（合并进 `general.answer` 的
  evidenceRuleIds 并置 `SUPPORTED`），绝不增删条目——否则 requestKey 漂移、工作台打不开。
- 只有「靠绕过才成立」的证据才把自然算得的 `GROUNDED` 下调为 `PARTIAL`
  （`operatorBypassedRuleIds` 非空时）；严格命中的绑定保持 `GROUNDED`。

关联：[[K-request-key-includes-intent-keys]]、[[K-workbench-matrix-path-is-operator-scoped]]、[[K-ai-reply-prompt-vs-send-rule-ids]]、[[K-request-fact-assignment-version-must-include-mapping]]、[[K-operator-directed-authorization-seam]]
