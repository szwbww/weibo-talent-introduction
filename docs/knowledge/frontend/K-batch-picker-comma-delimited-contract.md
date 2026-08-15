---
id: K-batch-picker-comma-delimited-contract
domain: frontend
created: 2026-08-15
last_used: 2026-08-15
hit_count: 0
source: create-p:batch-task-filters-main
severity: P2
---

批量任务控制台的多选 chip 组件（`.batch-tag-picker` 族，标签 / 地区 / 后续新增维度共用）有一套
**隐式命名与存值契约**，偏离即与既有 CSS 选择器和事件委托失配。

## 契约（`app.js` 的 `readBatchRegionPickerValue` 是权威实现）

```js
function readBatchRegionPickerValue(valueId) {
    var input = document.getElementById(valueId);
    return String(input ? input.value : "").split(",").map(function(v) { return v.trim(); }).filter(Boolean);
}
```

- `<valueId>` 是**隐藏 input** 的 id，值是**逗号分隔串**（不是 JSON、不是 JS 数组）。
- 同族元素 id 固定为 `<valueId>Chips` / `<valueId>Search` / `<valueId>Dropdown`。
- 外壳带 `data-tag-picker="<valueId>"`（`bindBatchRegionPicker` 用它做 outside-click 判定）。
- CSS 全部挂在 `.batch-tag-picker` / `-control` / `-chips` / `-chip` / `-search` / `-chevron` /
  `-dropdown` / `-option` / `-check` / `-empty` 上，复用即可，无需新样式。

## 直接后果

**选项的 `value` 不得含逗号。** 含逗号的值会在回显时被拆成两个不存在的值，筛选静默命中 0 条。
后端校验层应显式 `require(!it.contains(","))`。

## 外层容器必须是 `<div>` 不是 `<label>`

手动执行面板的单选字段原本写成 `<label class="batch-config-field">`。改成 picker 时若保留 `<label>`，
点击 chip / 下拉选项会触发 label 的隐式聚焦转移，下拉立刻收起，无法多选。
既有的 `manualFieldTags` / `manualFieldRegions` 正是 `<div class="batch-config-field">`，照抄它们。

## picker 没有 `change` 事件

单选 `<select>` 靠 `el.addEventListener("change", ...)` 触发收件预估。改成 picker 后必须
**从监听器数组里移除该 id**，改在 `toggleXxxPickerValue` 内部主动调 `scheduleRecipientPreview(kind)`
（`toggleBatchRegionPickerValue` 已有此范式）。漏掉这一步，预估就永远停在旧值。

## 新增维度时不要复制第三份实现

`renderBatchTagPicker` 与 `renderBatchRegionPicker` 已是两份近似的 ~120 行。再加维度应建
注册表基座（`BATCH_MULTI_PICKER_REGISTRY[valueId] = { options, emptyText, previewKind }`），
新维度只注册不实现；**但不要顺手把既有 tag/region picker 迁过去** —— 那会把回归面从 1 个字段扩到 3 个。

关联：[[K-batch-multi-value-filter-seams]]、[[K-region-constant-not-display-label]]
