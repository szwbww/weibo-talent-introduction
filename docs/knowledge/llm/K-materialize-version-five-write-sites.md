---
id: K-materialize-version-five-write-sites
domain: llm
created: 2026-08-21
last_used: 2026-08-24
hit_count: 1
source: create-p:03-verbatim-fact-answer-handling
severity: P1
---

# 新增 `TrustReplyItemHandling` 取值时必须逐个走查的 5 个 `materializeVersion` 调用点

`TrustReplyWorkbenchService.materializeVersion`（定义 `:1498`）有 **5 个调用点**，不是直觉中的 3 个
（`grep -n "materializeVersion(" src/main/kotlin/.../TrustReplyWorkbenchService.kt`，2026-08-21 实测）：

| 行 | 路径 | 新 handling 是否经过 |
|---|---|---|
| `:882` | 快照回放 `restoreSavedStateWithFrame`，先 `validateLockedItem`（`:875-881`）再重算 | **是** |
| `:1146` | `generateItemAdjustment` 的 `OMIT` 早返回 | 否 |
| `:1190` | `generateItemAdjustment` 的生成分支 | **是**（入口） |
| `:1256` | `assemble` 的逐项重算 | **是** |
| `:1867` | 整封聚合路径，`handling` 由 `when (item.status)`（`:1856-1860`）从 status 推导 | **否**——只产 `ANSWER_WITH_EVIDENCE` / `ANSWER_SUPPORTED_PART`，`UNSUPPORTED` 直接 `return@mapNotNull null` |

漏掉 `:882` 的表现最难查：**当次生成、锁定、整合全对，刷新页面后工作台报 STALE 或 422**，
因为回放路径重算出的 `versionId` 与保存的对不上。

## 同一批必须成对修改的三处（handling 语义落点）

新增取值后，`materializeVersion` 内部有两处 `when`/集合判据必须同步，且**必须成对**
（只改一处 → `versionId` 漂移 → `TRUST_REPLY_ITEM_VERSION_INVALID`）：

- `:1517-1524` `normalizedClaims` 的旁路集合（`OMIT` / `ACKNOWLEDGE_PENDING` /
  `ANSWER_FROM_OPERATOR_INPUT` / `ANSWER_EVIDENCE_WITH_OPERATOR_INPUT` 恒空 claims）
- `:1529-1534` `normalizedAnswer` 的 `when`（上述四者走 `answerText.trim()`，`else` 走
  `normalizedClaims.joinToString(CLAIM_PARAGRAPH_SEPARATOR)`）

第三处是 `validateLockedItem` 的 `when (locked.handling)`（`:1386-1432`），**无 `else` 分支**。
Kotlin 1.9.25（`pom.xml:21`）会在穷尽性上给出提示，但**不要把"编译通过"当作已覆盖的证据**——
显式补分支。

## 允许集只有一张表，不要造第二份

`allowedHandlings(item)`（`:2086-2111`）是全仓唯一权威，被 `requireAllowedHandlingForApi`
（生成前置 `:1127`、锁定校验 `:1385`）与下发 `:1972` 共用；
`AiReplyDraftService.validateItemHandling:1023-1025` 已委托给它。
历史上这张表有三份副本，2026-08-21 的计划 02 才收口（见 [[K-operator-directed-authorization-seam]]）。

## 两个容易漏的整封级校验

- `validateNoDuplicateClaims`（`:1340-1370`）对非 `OMIT` 版本按 `answerText` **归一化查重**，
  命中即 422 `TRUST_REPLY_DUPLICATE_CLAIM`。任何"正文由确定性规则拼出"的 handling
  （如按事实原文回答）会让两条摘要绑同一事实集时**必然**撞上——这是既有行为，不是缺陷。
- `groundedSections` 的收集条件（`:899-900` 与 `:1246-1247`）只认
  `ANSWER_WITH_EVIDENCE` / `ANSWER_SUPPORTED_PART`；claims 恒空的新 handling 不进
  `validateGroundedTrustBoundary`，这是有意的，别顺手加进去。

关联：[[K-locked-answer-paragraphs-at-version-time]]、[[K-locked-item-assembly-list-not-set]]、[[K-operator-directed-authorization-seam]]、[[K-workbench-lock-replay-needs-dedicated-state-store]]
