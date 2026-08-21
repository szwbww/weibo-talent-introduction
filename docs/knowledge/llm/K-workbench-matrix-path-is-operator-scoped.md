---
id: K-workbench-matrix-path-is-operator-scoped
domain: llm
created: 2026-08-21
last_used: 2026-08-21
hit_count: 0
source: create-p:02-bound-facts-become-partial-evidence
severity: P2
---

# 只给工作台放宽证据口径不会外溢到自动回复——`resolveMatrixSelection` 是运营专属路径

`QaFactSelectionService` 有两个公开入口，分发在 `:137-159`：

| 入口 | 分支条件 | 走到哪个 resolve | `promptPool` 是什么 |
|---|---|---|---|
| `selectForWorkbench(...)` | `selectionsByRequest != null` | `resolveMatrixSelection`（`:162`） | **恰为运营在该条摘要上显式绑定的规则集** |
| `selectForWorkbench(...)` | `requestedFactIds != null` | `resolveLegacySelection`（`:216`） | 扁平全局池，按条目顺序消耗 |
| `selectForWorkbench(...)` | 都为 null | `resolveAutoSelection`（`:277`） | 全部可匹配规则 |
| `select(...)`（`:22`） | —— | 内部自建，**永不进矩阵分支** | 显式选择或全部可匹配规则 |

`select()` 的生产调用点（2026-08-21 实测 `grep -rn "qaFactSelectionService\." --include=*.kt src/main`）：
`AiReplyDraftService.kt:1923`、`PendingMailOperationService.kt:170/474/490/491/557/759/761/946/949/952`
—— 即**自动回复与人工发送的取证全部走 `select()`**。

## 用途

任何「只对运营工作台放宽证据判据」的改动（跳过关键词匹配、接受运营担保、下调 status 上限……），
把开关做成 `buildRequestFact` 的形参并**只在 `:190` 那一个调用点传 true**，即可保证自动链路逐字不变。

`buildRequestFact` 共 5 个调用点：`:52`(auto) / `:190`(matrix) / `:229`(legacy 空选) / `:255`(legacy) / `:288`(auto)。
**改动时必须逐个确认其余 4 处保持默认值**——漏一处就意味着自动回复会拿运营口径的证据发信。

反面后果：若 auto 路径也跳过 `QaFactKeywordMatcher.matchesRule`，全库每条规则都会成为每个问题的
候选证据，自动回复把全库当依据，且完全静默。

关联：[[K-fact-matrix-two-semantics-in-one-field]]、[[K-ai-reply-prompt-vs-send-rule-ids]]、[[K-auto-reply-decide-context-parity]]
