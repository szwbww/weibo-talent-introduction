---
id: K-batch-multi-value-filter-seams
domain: campaign
created: 2026-08-15
last_used: 2026-08-15
hit_count: 0
source: create-p:batch-task-filters-main
severity: P1
---

把批量发送的某个**单值**筛选维度改成**多值**（`String?` → `List<String>`）时，必须同步的全集。
本条是 [[K-recipient-scope-status-filter]] 的多值化补充，其映射点清单仍然适用。

## 两条活体目标来源（不是三条）

1. `ManualInitialOutreachService.buildEsFiltersForLevel(scope, level)` —— ES 新目标。
   3 个调用点：材料提醒目标构建、`countEsTargets`（预估）、`fetchEsPage`（发送）。
2. `RecipientScope.matchesExpert(profile)`（`BatchExecutionModels.kt`）—— MySQL 重试联系人内存过滤。

`buildMaterialReminderEsFilters` **零调用方**，是死代码，见 [[K-recipient-scope-status-filter]] 的更正段。

## 多值的 ES 表达必须是「单个 should 项」

照 `ExpertSearchService.regionsFilter` 的既有范式：

```kotlin
fun xxxFilter(values: List<String>): Map<String, Any>? {
    val v = values.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (v.isEmpty()) return null          // 空集合 = 不限，调用方不得追加
    return mapOf("bool" to mapOf("should" to v.map { predicate(it) }, "minimum_should_match" to 1))
}
```

两个必踩的坑：

- **平铺进 `bool.filter` = AND**，语义变成"同时属于 A 且属于 B"，恒零命中。必须包成单个 `bool.should`。
- **空集合返回 `should: []` + `minimum_should_match: 1` = 匹配 0 条**，所有"不限"的任务静默停发。空集合必须返回 `null`。

## should 分支里只能放**纯谓词**

若某个值的既有单值实现返回的是一组 filter（含 `exists email` 这类 AND 语义条件），**不能**原样塞进 should 分支 —— 其余分支会绕过那些条件。必须先抽出纯谓词版本。

实例：`operatorStatusFilter("NOT_CONTACTED")` 返回 `notContactedWithEmailFilters(null)`，含 `exists email`。多值化时须新写 `operatorStatusPredicate(status)` 只返回状态判定本身。

## 存储：照 `tags_json` / `regions_json` 的 JSON 列范式

`TEXT` 列不能带 `DEFAULT`（MySQL 限制），故照 `V93__add_regions_to_batch_send_task_config.sql` 的两步：

```sql
ALTER TABLE t ADD COLUMN xxx_json TEXT NOT NULL AFTER yyy;
UPDATE t SET xxx_json = CASE WHEN old IS NULL OR old = '' THEN '[]' ELSE CONCAT('["', old, '"]') END;
ALTER TABLE t DROP COLUMN old;      -- 同一迁移里删旧列，避免双事实源（V92 有 DROP 先例）
```

保留旧单值列 = 双事实源，旧 typed API 改一次就分叉，无法排查。

## 旧 typed API 适配器的两个方向

- **写**：`updateLegacyConfig()` 必须 `xxxJson = existing.xxxJson` 显式保留（[[K-batch-config-legacy-adapter-field-preservation]]），**绝不**从旧请求的单值字段重建 —— 那会把多值裁成 1 个。
- **读**：`toLegacyConfig()` 用 `firstOrNull().orEmpty()` 降级；仅当该字段本来就存在于 `BatchSendConfig` KV data class 时才需要，否则**不加**。

## 前端

值以逗号分隔存隐藏 input，见 [[K-batch-picker-comma-delimited-contract]]。校验层必须
`require(!value.contains(","))`，否则回显时被拆坏、筛选静默命中 0 条。
