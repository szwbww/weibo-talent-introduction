---
id: K-task-type-semantics-three-lists
domain: task
created: 2026-08-16
last_used: 2026-08-16
hit_count: 0
source: create-p:p1-task-type-catalog-semantics
severity: P1
---

经验：taskType 的"有哪些类型 / 叫什么名字"在仓库里有**三份互不相同的硬编码名单**，且都不等于实际写入的类型全集。

| 位置 | 项数 |
|---|---|
| `index.html:940-947` 的 `#taskTypeFilter` option | 5 |
| `TaskProgressController.kt:33-36` 的 `allowedTaskTypes` | 6 |
| `app.js:678-685` 的 `taskButtonMapping` | 6 |

三者**交集仅 1 项**（`MANUAL_INITIAL_OUTREACH`），并集 10 项，而 `runAndRecord` 实际写入的 taskType 有 **16 种**（另有 `EXPERT_ENRICHMENT` 等以变量形式传入，grep 字面量会漏）。后果：新增任务类型必然漏改其中一处；截图里能看到 `EXPERT_ENRICHMENT` / `BOUNCE_COLLECTION` 的记录，但下拉里选不到。

**正确做法**：语义收口到单一 `TaskTypeCatalog`（中文名 / 分组 / 计数列语义标签 / summary 提取规则 / drilldown 声明）；`allowedTaskTypes` 从 catalog 的 `hasProgressUi` **派生**；前端不得硬编码任何 taskType 中文名。

**但下拉选项不能取 catalog 全集**：必须用 `SELECT DISTINCT task_type FROM task_execution` 与 catalog 做**左连接**，catalog 未声明的类型仍要出现（label 回落原 code）。否则将来新增类型而忘了写 catalog 时，它的记录在 UI 上永久不可筛选——把硬编码问题换了个位置复现。

**边界**：`taskButtonMapping` 管的是"任务启动按钮的文案与 btnId"，catalog 管的是"记录页展示"，二者**刻意不合并**。

关联：[[K-allowedTaskTypes-whitelist]]、[[K-metric-label-not-reflection]]
