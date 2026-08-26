---
id: K-discipline-unclassified-filter-bypasses
domain: expert
created: 2026-08-12
last_used: 2026-08-25
hit_count: 1
source: create-p:batch-send-rhythm-and-filter-00-master
severity: P1
---

> **2026-08-25 复核更正（create-p:02-batch-send-type-filter）**：本条描述的三条 ES 旁路
> **均已修复**，两处白名单**修复其一**。逐项实测：
> - `ManualInitialOutreachService.buildEsFiltersForLevel:1308` 已改为
>   `scope.discipline?.let { base.add(ExpertSearchService.disciplineFilter(it)) }`；
>   notContacted 基座分支（`:1302`）也把 discipline 透传给 `notContactedWithEmailDomainsFilters`。
> - `ManualInitialOutreachService.buildMaterialReminderEsFilters:1165` 已调
>   `ExpertSearchService.disciplineFilter(config.discipline)`。
> - `RecipientScope.matchesExpert`（`BatchExecutionModels.kt:80-88`）已有
>   `if (discipline == "UNCLASSIFIED") profile.disciplineCategory.isNullOrBlank()` 分支。
> - `BatchSendTaskConfigService.kt:633` 已改为 `val ALLOWED_DISCIPLINES = ExpertSearchService.ALLOWED_DISCIPLINES`。
> - **仍未修**：`BatchSendSettingService.kt:236` 的 `setOf("", "STEM", "HUMANITIES")`，
>   使用点 `:152`（校验）与 `:196`（归一化），属 KV 兼容层。
>
> 本条语义因此从「待修复缺陷清单」转为「**必须维持的约束**」：新增筛选维度时
> 仍须复用 `ExpertSearchService` 的单一 filter 实现，并确认白名单指向同一常量。
> 权威行号也已漂移：`disciplineFilter` 现在 `ExpertSearchService.kt:96-106`、
> `ALLOWED_DISCIPLINES` 在 `:94`（原记 `:55-65` / `:53`）。下方原文保留。

`UNCLASSIFIED`（未分类）学科的语义是「`disciplineCategory` 字段**不存在**」，
不是某个字符串值。权威实现只有一处：
`ExpertSearchService.disciplineFilter()`（`:55-65`）——
`UNCLASSIFIED` → `bool.must_not.exists(disciplineCategory)`，其余 → `term`。
`ALLOWED_DISCIPLINES = setOf("STEM", "HUMANITIES", "UNCLASSIFIED")`（`:53`）。

但仓库里存在**三条未复用它的旁路**，在 `discipline = "UNCLASSIFIED"` 时会把目标全部误过滤：

1. `ManualInitialOutreachService.buildEsFiltersForLevel()` 的 else 分支（`:1219`）——
   直接 `mapOf("term" to mapOf("disciplineCategory" to it))`
2. `RecipientScope.matchesExpert()`（`BatchExecutionModels.kt:54`）——
   直接 `profile.disciplineCategory != discipline`，用于**重试联系人**路径
3. `ManualInitialOutreachService.buildMaterialReminderEsFilters()`（`:1086` 附近）——
   同 1 的写法

另有两处白名单**不含** `UNCLASSIFIED`，会在保存配置时先一步 422：
- `BatchSendSettingService.ALLOWED_DISCIPLINES = setOf("", "STEM", "HUMANITIES")`（`:236`）
- `BatchSendTaskConfigService` 内的同名常量

结论：任何「让批量发送支持按未分类筛选」的需求，改前端下拉 option 只是第 1/6 步；
必须同时打通上述 3 条查询旁路 + 2 处白名单，否则表现为
「界面能选未分类，一封都发不出去且无报错」。
这是 K-batch-send-filter-retry-parity 的同类复发（ES 路径与重试路径口径分裂）。

关联：[[K-batch-send-filter-retry-parity]]、[[K-agg-filter-source-of-truth]]
