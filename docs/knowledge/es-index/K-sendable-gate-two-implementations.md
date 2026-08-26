---
id: K-sendable-gate-two-implementations
domain: es-index
created: 2026-08-25
last_used: 2026-08-25
hit_count: 0
source: create-p:05a-institution-type-collection
severity: P1
---

发信硬门禁（`sendable` + 分类策略版本）在仓库中有**两处独立实现**，不是一处。
任何改动分类 `VERSION` 或门禁语义的计划，两处必须同步改，否则复现
[[K-batch-send-filter-retry-parity]]（ES 路径与 MySQL 重试路径口径分裂）。

## 两处

1. **ES 侧** — `ExpertSearchService.expertSendableFilter()`（`:55-63`）
   返回 `bool.filter` 含两个 term：`expertClassification.sendable == true`
   与 `expertClassification.version == ExpertClassificationService.VERSION`。
   **3 个生产调用点**（2026-08-25 grep 复核）：
   - `ManualInitialOutreachService.kt:1324` —— 批量首发 ES 目标
   - `ExpertSearchService.kt:376` —— `searchSendableExpertsWithEmail`，
     被 `InitialOutreachService.kt:34` 的**旧定时/队列首发**调用
   - （函数自身）

2. **内存侧** — `RecipientScope.matchesExpert`（`BatchExecutionModels.kt:65-69`）
   **不调用**上面的函数，是独立复刻：
   `classification?.sendable != true || classification.version != ExpertClassificationService.VERSION` 即拒。
   用于 MySQL NEW 重试联系人的过滤。

## 易被忽略的第二个调用点

计划里常写「`buildEsFiltersForLevel` 是 ES 侧唯一 seam」——**对批量首发成立，对全局不成立**。
`searchSendableExpertsWithEmail` 是另一条独立查询，走旧定时/队列链路。
好在两者调用同一个函数，改函数即两处生效；但**统计调用点时不要漏**。

## 对版本迁移的直接影响

升 `VERSION` 会让存量分类在两处同时失效 → 可发送池归零直到全量回填完成。
零停发窗口的做法是两阶段：
1. 两处的版本判定从「等于 VERSION」改为「属于 ACCEPTED_VERSIONS 集合」（迁移期含新旧两版）
2. 部署新版代码 → 跑全量回填 → 把集合收窄为只含新版

改 ES 侧是 `term` → `terms`（语义等价扩展，不削弱门禁）；内存侧是 `!=` → `!in`。
**两者必须同一次提交**，且须配一条不变量约束「迁移完成后必须收窄」。

测试面：`expertSendableFilter` 在测试中被引用约 28 处
（`ManualInitialOutreachServiceTest` 与 `ExpertSearchServiceTest`），
多数以函数返回值做期望值会自动跟随，但
`ExpertSearchServiceTest.kt:1951` 是**逐字结构断言**，必须手改。
