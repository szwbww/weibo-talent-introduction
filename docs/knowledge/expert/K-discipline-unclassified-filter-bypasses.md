---
id: K-discipline-unclassified-filter-bypasses
domain: expert
created: 2026-08-16
last_used: 2026-08-16
hit_count: 1
source: create-p:batch-send-rhythm-and-filter-00-master
severity: P1
---

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

---

## 2026-08-16 就地更正（create-p:expert-reachability 复核，条目此前已过期）

上文所述「三条未复用权威实现的 `term` 旁路」**已全部修复**，行号亦全部漂移。实测：

```bash
grep -rn "fun buildExpertFilters\|fun buildEsFiltersForLevel\|fun matchesExpert\|fun buildMaterialReminderEsFilters" --include=*.kt src/main/kotlin
```
```
ManualInitialOutreachService.kt:1129:    private fun buildMaterialReminderEsFilters(
ManualInitialOutreachService.kt:1272:    private fun buildEsFiltersForLevel(scope: RecipientScope, level: String)
BatchExecutionModels.kt:60:              fun matchesExpert(profile: ...): Boolean
ExpertSearchService.kt:905:              private fun buildExpertFilters(
```

三处当前实现（逐字）：
- `ManualInitialOutreachService.kt:1284`：`scope.discipline?.let { base.add(ExpertSearchService.disciplineFilter(it)) }`
- `ManualInitialOutreachService.kt:1141`：`filters.add(ExpertSearchService.disciplineFilter(config.discipline))`
- `BatchExecutionModels.kt:70-76`：含 `if (discipline == "UNCLASSIFIED") profile.disciplineCategory.isNullOrBlank()` 分支

**本条目的长期价值不在那三个具体行号，而在这条结构规则**：

> 「专家筛选维度」在本仓库恰有 **4 处构造点**（上列 grep 即权威清单，含 1 处内存侧）。
> 任何新增筛选维度都必须：① 在 `ExpertSearchService` companion 内实现唯一权威表达式；
> ② 四处构造点一律委托调用，不自持表达式；③ 「值缺失 = 某档」这类语义必须在
> ES 侧（`must_not exists`）与内存侧（`isNullOrBlank()`）双侧成对实现，并有等价性测试。

另：白名单双份问题仍成立——新增维度若带取值白名单，配置侧校验必须复用
`ExpertSearchService` 的同一常量，不得另写一份（否则界面能选、保存 422，或保存成功、查询恒 0）。
