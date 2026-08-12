---
id: K-material-reminder-single-es-filter-seam
domain: campaign
created: 2026-08-12
last_used: 2026-08-12
hit_count: 0
source: create-p:batch-send-scope-03-region-multiselect-backend
severity: P2
---

材料提醒（MATERIAL_REMINDER）看起来有两套 ES 过滤构造，实际只有一套是活的。

**`ManualInitialOutreachService.buildMaterialReminderEsFilters(config)`（`:1079-1091`）是死代码**，2026-08-12 grep 实证零调用点：

```
$ grep -rn "buildMaterialReminderEsFilters" --include=*.kt src/ | grep -v "private fun buildMaterialReminderEsFilters"
（无输出）
```

发送与待发统计两条路径实际都收敛到 `buildMaterialReminderSnapshotFromScope()`（`:1120`），其 `:1128` 为 `val filters = buildEsFiltersForLevel(scope, level)`。
→ **新增收件范围维度只需接 `buildEsFiltersForLevel()` 一处**，即可同时覆盖发送与统计。

但两条路径的 `RecipientScope` **来源不同**，这是真正需要注意的差异：

| 入口 | 重载 | scope 来源 | 携带新维度？ |
|---|---|---|---|
| 发送 `runMaterialFromSnapshot()`（`:181`） | `buildMaterialReminderSnapshot(scope, config)`（`:1098`） | `RecipientScope.fromSnapshot(snapshot)`（`:179`） | ✅ |
| 统计 `countPending(MATERIAL_REMINDER)`（`:415`） | `buildMaterialReminderSnapshot(config)`（`:1105`） | 方法内手工构造（`:1106-1112`），输入是 `BatchSendConfig`（KV 层） | ❌ |

因为 `BatchSendConfig`（`BatchSendSettingService.kt:240`，KV 兼容层）不承载实体的新维度，**统计数会系统性地大于实际发送范围**。这是有界的已知偏差，不要为消除它去改 KV 层（见 [[K-batch-send-setting-kv]]）——但必须在计划的人工验收里显式记录，否则运营会当成 bug 报上来。

**通用教训**：把某个方法列为「待修旁路」之前，先 grep 它是否真的有调用点。为死代码写运行时测试是虚假覆盖率；把死代码当活跃缺陷会虚增计划范围与验收项。

关联：[[K-batch-send-filter-retry-parity]]、[[K-batch-send-round-loop-symmetry]]、[[K-batch-send-setting-kv]]
