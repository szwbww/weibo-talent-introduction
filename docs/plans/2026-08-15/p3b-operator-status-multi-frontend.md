# P3b：专家状态筛选改多值（前端）

主计划：`batch-task-filters-main.md`（共享审计 X-3、验证命令在主计划）
前置计划：**P2b 必须已合并**（通用 picker 基座 `BATCH_MULTI_PICKER_REGISTRY` + 7 个函数已存在，I2b-1）；**P3a 必须已合并**（后端接受 `operatorStatuses`）
子系统数：1（前端）  文件数：3

---

## 需求描述

### Observable outcome

定时任务编辑器与手动执行面板的「专家状态」由单选 `<select>` 变为多选 chip picker；任务列表「收件范围」列显示全部已选状态的中文标签；手动执行「已修改」标红对该字段生效。

### What must NOT change

- **N3b-1** P2b 建立的通用 picker 基座 **7 个函数体一行不改** —— 本计划只往 `BATCH_MULTI_PICKER_REGISTRY` 加两项（I3b-1）。
- **N3b-2** 标签 / 地区 / 邮箱服务商三个既有 picker 行为不变。
- **N3b-3** 专家列表页 `#contactStatusFilter` 仍是单选（主计划 N-1）。
- **N3b-4** `styles.css` 零改动。
- **N3b-5** 手动执行「已修改」对其余字段的行为不变。

### Out of scope

- 后端 —— 归 P3a。
- picker 基座的任何重构。

---

## 关键不变量

### Invariant I3b-1: 只注册，不新建实现
- Rule: 本计划对 `app.js` 的 picker 相关改动**只允许**是往 `BATCH_MULTI_PICKER_REGISTRY` 增加 `batchConfigEditorOperatorStatuses` / `batchManualOperatorStatuses` 两项，以及新增一个 `batchOperatorStatusOptions()` 选项提供函数。**禁止**新增第二套 render/toggle/open/close/bind。
- Applies to: `app.js`。
- Violation consequence: 三份近似实现，改一处必漏两处。
- 来源: I2b-1

### Invariant I3b-2: 状态的 value 是英文枚举名，label 才是中文
- Rule: picker 的 `option.value` 必须是 `OperatorStatus` 的英文枚举名（如 `NOT_CONTACTED`），隐藏 input 里存的、发给后端的、参与 diff 比较的**全部是英文名**；中文仅用于 chip 与下拉的 `label` 展示。
- Applies to: `batchOperatorStatusOptions()`、`renderBatchMultiPicker` 的 chip/label、`formatManualDiffValue`、`renderBatchConfigRow`。
- Violation consequence: 与 `K-region-constant-not-display-label` 同型事故 —— 中文串发到后端过不了 `ALLOWED_OPERATOR_STATUSES` 校验（422），或更糟地被存进 JSON 列后筛选静默命中 0 条。
- 来源: K-region-constant-not-display-label

### Invariant I3b-3: 选项来源仍是既有的 `operatorStatusOptions` 常量
- Rule: `batchOperatorStatusOptions()` 必须从既有的 `operatorStatusOptions`（`fillBatchOperatorStatusSelectOptions` 在用，`app.js:14983`，形态为 `[[value, label], ...]`）派生，**不另抄一份状态列表**。
- Applies to: `app.js`。
- Violation consequence: 枚举新增状态时两份列表漂移。
- 来源: original

### Invariant I3b-4: 手动执行 diff 的 5 个注册点必须同步
- Rule: 同 I2b-4 的 5 点（`normalizeManualSnapshot` / `formatManualDiffValue` / `computeManualDiffs.fieldDefs` / `computeAndRenderDiffs.fieldMap` / `clearAllDiffMarkers`），key 从 `operatorStatus` 改为 `operatorStatuses`，DOM id `manualFieldOperatorStatus` 保持不变。
- Applies to: `app.js`。
- 来源: K-recipient-scope-status-filter

### Invariant I3b-5: 数组比较顺序无关
- Rule: 同 I2b-5，`normalizeManualSnapshot` 对 `operatorStatuses` 做 `slice().sort()`。
- 来源: I2b-5

---

## 样式契约

> 可复用 class 行号见主计划 X-3。本计划 **零新增 CSS**。

### S3b-1: 定时任务编辑器 —— 专家状态字段
- **复用**：与 S2b-1 完全相同的 class 集合（`.batch-config-field` styles.css:8873、`.batch-config-field-label` :8676、`.batch-tag-picker` 族 :8915-9040）。
- **新增**：无。
- **DOM 结构**：替换 `index.html:1214-1219`（现为 `<label class="batch-config-field">` + `<select id="batchConfigEditorOperatorStatus">`）为**逐字**：

```html
                        <div class="batch-config-field">
                            <span class="batch-config-field-label">专家状态</span>
                            <div class="batch-tag-picker" data-tag-picker="batchConfigEditorOperatorStatuses">
                                <div class="batch-tag-picker-control">
                                    <div id="batchConfigEditorOperatorStatusesChips" class="batch-tag-picker-chips"></div>
                                    <input type="search" id="batchConfigEditorOperatorStatusesSearch" class="batch-tag-picker-search" placeholder="搜索并选择专家状态" autocomplete="off" aria-controls="batchConfigEditorOperatorStatusesDropdown" aria-expanded="false">
                                    <span class="batch-tag-picker-chevron" aria-hidden="true">⌄</span>
                                </div>
                                <input type="hidden" id="batchConfigEditorOperatorStatuses" value="">
                                <div id="batchConfigEditorOperatorStatusesDropdown" class="batch-tag-picker-dropdown" role="listbox" aria-multiselectable="true" hidden></div>
                            </div>
                        </div>
```

- **禁止项**：inline style；新 class；外层写成 `<label>`。

### S3b-2: 手动执行面板 —— 专家状态字段
- **复用**：同 S3b-1 + `.batch-config-diff-badge`（:8904）+ `.batch-config-diff-original`（:8913）+ `.batch-manual-section .batch-config-field.is-config-diff`（:8894）。
- **新增**：无。
- **DOM 结构**：替换 `index.html:1391-1399`（`<label class="batch-config-field" id="manualFieldOperatorStatus">` + `<select id="batchManualOperatorStatus">`）为**逐字**：

```html
                    <div class="batch-config-field" id="manualFieldOperatorStatus">
                        <span class="batch-config-field-label">专家状态</span>
                        <div class="batch-tag-picker" data-tag-picker="batchManualOperatorStatuses">
                            <div class="batch-tag-picker-control">
                                <div id="batchManualOperatorStatusesChips" class="batch-tag-picker-chips"></div>
                                <input type="search" id="batchManualOperatorStatusesSearch" class="batch-tag-picker-search" placeholder="搜索并选择专家状态" autocomplete="off" aria-controls="batchManualOperatorStatusesDropdown" aria-expanded="false">
                                <span class="batch-tag-picker-chevron" aria-hidden="true">⌄</span>
                            </div>
                            <input type="hidden" id="batchManualOperatorStatuses" value="">
                            <div id="batchManualOperatorStatusesDropdown" class="batch-tag-picker-dropdown" role="listbox" aria-multiselectable="true" hidden></div>
                        </div>
                        <span class="batch-config-diff-badge" hidden>已修改</span>
                        <div class="batch-config-diff-original" hidden></div>
                    </div>
```

⚠️ 外层必须是 `<div>`（主计划 X-3 已论证）；`id="manualFieldOperatorStatus"` **保持不变**（`computeAndRenderDiffs` fieldMap 与 `clearAllDiffMarkers` 引用它）。

### S3b-3: 任务列表「收件范围」列
- **复用**：`.batch-task-scope-line`（styles.css:8520-8521）。
- **新增**：无。
- **DOM 结构**：`renderBatchConfigRow`（`app.js:13386` 起）内新增一行（放在「学科:」之后，与其余 scopeParts 同构）：
  ```js
  if (Array.isArray(c.operatorStatuses) && c.operatorStatuses.length > 0) scopeParts.push("状态: " + c.operatorStatuses.map(operatorStatusLabel).join("、"));
  ```
  其中 `operatorStatusLabel(value)` 从 `operatorStatusOptions` 查中文标签，查不到时回落为 `escapeHtml(value)`（I3b-2：**只影响展示**，不改 value）。
- **禁止项**：不新增 pill/badge；不改列宽；不改 `.batch-task-scope-line` 规则块。

---

## 现状审计

> 前端样式盘点、picker DOM 与逗号契约见主计划 X-3；通用 picker 基座见 P2b 的 T2b-1。

### `operatorStatus` 在批量任务控制台的全部前端注册点（grep 取证）

```
$ grep -n "OperatorStatus\|operatorStatus" src/main/resources/static/app.js | grep -i "batch\|manual"
```

| # | 行 | 函数 | 作用 |
|---|---|---|---|
| 1 | 13517 | `showBatchConfigEditor` | `setVal("batchConfigEditorOperatorStatus", config ? (config.operatorStatus \|\| "") : "")` |
| 2 | 13939 | `buildConfigEditorRecipientSnapshot` | 预估快照 |
| 3 | 14057 | `saveBatchConfigEditor` | 保存 payload |
| 4 | 14183 | `fillManualFormFromDraft` | `setVal("batchManualOperatorStatus", d.operatorStatus \|\| "")` |
| 5 | 14281 | `readManualFormValues` | 读手动表单 |
| 6 | 14395 | `computeAndRenderDiffs` fieldMap | `operatorStatus: "manualFieldOperatorStatus"`（I3b-4 #4） |
| 7 | 14425 | `clearAllDiffMarkers` | fields 数组含 `"manualFieldOperatorStatus"`（I3b-4 #5） |
| 8 | 14977-14988 | `fillBatchOperatorStatusSelectOptions` | 填两个 `<select>` 的 `<option>`（本计划**整个删除**） |
| 9 | 15028 | `bindBatchSendTaskEvents` | 调用 #8 |
| 10 | 15043 | `bindBatchSendTaskEvents` | 预估触发监听器数组含 `"batchConfigEditorOperatorStatus"` |

⚠️ **缺口**：上表**没有** `deepCloneConfig`（`:14130`）、`fillManualFormDefaults`（`:14151`）、`normalizeManualSnapshot`（`:14290`）、`formatManualDiffValue`（`:14307`）、`computeManualDiffs` fieldDefs（`:14336`）中的 `operatorStatus` 条目 —— grep 未命中，说明**这 5 处当前根本没有该字段**。

这意味着**改动前，「专家状态」压根没有参与手动执行的「已修改」标红**（fieldDefs 里没有它，`computeManualDiffs` 就不会比较它）。本计划把它补齐，属**修复既有缺口**，不是纯改造。执行时必须在这 5 处**新增**条目，而不是"改名"。

> 该缺口须在实现说明中显式记录，并在 T3b-4 的验收里单独断言（V8）。

### 既有选项常量

`operatorStatusOptions` —— 形态 `[[value, label], ...]`，被 `fillBatchOperatorStatusSelectOptions` 消费（`app.js:14983`）。执行前必须 `grep -n "operatorStatusOptions" src/main/resources/static/app.js` 确认其定义位置与元素形态，并在实现说明中贴出定义行（I3b-3）。

### 交互点

| IP | 说明 |
|---|---|
| IP-1 | `showBatchConfigEditor`（回显）→ `saveBatchConfigEditor`（保存）：同字段名，否则打开再保存清空已配状态 |
| IP-2 | picker 值变化 → `scheduleRecipientPreview`：`<select>` 的 change 监听要移除，改由基座 toggle 触发（同 P2b 的 IP-2） |
| IP-3 | 来源配置带入 → diff 计算：I3b-4 的 5 点，其中 3 点是**新增**（见上方缺口） |

---

## 实现方案

### T3b-1 注册到通用 picker 基座（I3b-1 / I3b-2 / I3b-3）

文件：`app.js`

在 `BATCH_MULTI_PICKER_REGISTRY`（P2b 建立）中增加两项：

```js
    batchConfigEditorOperatorStatuses: {
        options: function() { return batchOperatorStatusOptions(); },
        emptyText: "没有匹配状态",
        previewKind: "editor"
    },
    batchManualOperatorStatuses: {
        options: function() { return batchOperatorStatusOptions(); },
        emptyText: "没有匹配状态",
        previewKind: "manual"
    }
```

新增（I3b-2 / I3b-3）：

```js
/* I3b-3：状态选项从既有 operatorStatusOptions 常量派生，不另抄一份。
   I3b-2：value 是英文枚举名（进 payload / 进 diff 比较），label 只用于展示。 */
function batchOperatorStatusOptions() {
    return (operatorStatusOptions || []).map(function(o) {
        return { value: o[0], label: o[1] };
    });
}

function operatorStatusLabel(value) {
    var hit = (operatorStatusOptions || []).find(function(o) { return o[0] === value; });
    return hit ? hit[1] : String(value);
}
```

⚠️ **不得**修改基座 7 个函数中的任何一行（N3b-1）。

### T3b-2 index.html DOM 替换（S3b-1 / S3b-2）

替换 `index.html:1214-1219` 与 `:1391-1399` 为契约中的逐字块。其余一行不改。

### T3b-3 编辑器接线（IP-1 / IP-2）

| 原位置 | 改法 |
|---|---|
| `:13517` | `setBatchMultiPickerValue("batchConfigEditorOperatorStatuses", config && Array.isArray(config.operatorStatuses) ? config.operatorStatuses : []);` |
| `:13939` | `operatorStatuses: readBatchMultiPickerValue("batchConfigEditorOperatorStatuses"),` |
| `:14057` | `operatorStatuses: readBatchMultiPickerValue("batchConfigEditorOperatorStatuses"),` |
| `:14977-14988` `fillBatchOperatorStatusSelectOptions` | **整个函数删除** |
| `:15028` | 删除对上函数的调用 |
| `:15043` 监听器数组 | 移除 `"batchConfigEditorOperatorStatus"` |
| `bindBatchSendTaskEvents` 内 | 新增 `bindBatchMultiPicker("batchConfigEditorOperatorStatuses"); bindBatchMultiPicker("batchManualOperatorStatuses");` |

### T3b-4 手动面板接线 + diff 5 点（I3b-4 / I3b-5，含缺口修复）

| 位置 | 改法 |
|---|---|
| `:13956` `buildManualExecutionSnapshot` | `operatorStatuses: values.operatorStatuses,` |
| `:14130` `deepCloneConfig` | **新增** `operatorStatuses: Array.isArray(c.operatorStatuses) ? c.operatorStatuses.slice() : [],` |
| `:14151` `fillManualFormDefaults` | **新增** `operatorStatuses: [],` |
| `:14183` `fillManualFormFromDraft` | `setBatchMultiPickerValue("batchManualOperatorStatuses", Array.isArray(d.operatorStatuses) ? d.operatorStatuses : []);` |
| `:14281` `readManualFormValues` | `operatorStatuses: readBatchMultiPickerValue("batchManualOperatorStatuses"),` |
| `:14290` `normalizeManualSnapshot` **（#1，新增，I3b-5）** | `operatorStatuses: (Array.isArray(v.operatorStatuses) ? v.operatorStatuses : []).map(function(s){return String(s).trim();}).filter(Boolean).slice().sort(),` |
| `:14307` `formatManualDiffValue` **（#2，新增）** | `if (key === "operatorStatuses") return (Array.isArray(value) && value.length > 0) ? value.map(operatorStatusLabel).join("、") : "全部状态";` |
| `:14336` `computeManualDiffs` fieldDefs **（#3，新增）** | `{ key: "operatorStatuses", label: "专家状态" }` |
| `:14395` `computeAndRenderDiffs` fieldMap **（#4）** | key 改为 `operatorStatuses: "manualFieldOperatorStatus"` |
| `:14425` `clearAllDiffMarkers` **（#5）** | `"manualFieldOperatorStatus"` 保持在数组中（**核对**其仍在） |

⚠️ 与 P2b 的 T2b-4 同款约束：执行前先读 `computeManualDiffs`（`:14336-14378`）确认数组的实际比较方式，在实现说明中写出结论 —— **不得假设**。

### T3b-5 任务列表列（S3b-3）

`renderBatchConfigRow` 内按 S3b-3 新增一行。

### T3b-6 测试

文件：`src/test/js/batchSendTaskConsoleInteraction.test.js`，追加：

| 用例 | 断言 |
|---|---|
| W1 | `batchOperatorStatusOptions()` 的每个元素 `value` 是英文枚举名、`label` 是中文；两者不相等（I3b-2 / I3b-3） |
| W2 | `setBatchMultiPickerValue("batchConfigEditorOperatorStatuses", ["NOT_CONTACTED","CONTACTED"])` → 隐藏 input value 为 `"NOT_CONTACTED,CONTACTED"`（英文，不是中文） |
| W3 | `renderBatchMultiPicker` 的 chips HTML 含**中文** label，但 `data-remove-tag` 属性值是**英文**枚举名（I3b-2） |
| W4 | `showBatchConfigEditor({operatorStatuses:["CONTACTED"]})` → 隐藏 input 为 `"CONTACTED"`；`showBatchConfigEditor(null)` → `""` |
| W5 | `saveBatchConfigEditor` payload 含 `operatorStatuses: ["CONTACTED"]`（英文数组） |
| W6 | `formatManualDiffValue("operatorStatuses", [])` === `"全部状态"`；`(["NOT_CONTACTED"])` 返回中文标签 |
| W7 | `normalizeManualSnapshot` 对 `["B","A"]` 与 `["A","B"]` 结果相等（I3b-5） |
| W8 | **缺口修复断言**：`computeManualDiffs` 在 draft `operatorStatuses=["CONTACTED"]`、source `[]` 时判为 diff（改动前该字段根本不参与比较，本用例证明缺口已补） |
| W9 | `renderBatchConfigRow({operatorStatuses:["NOT_CONTACTED"]})` 输出含 `状态: ` + 中文标签，且被 `.batch-task-scope-line` 包裹 |
| W10 | 回归：P2b 建立的邮箱 picker 用例（V1-V9）继续绿 |

---

## 变更文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `src/main/resources/static/app.js` | 修改 |
| 2 | `src/main/resources/static/index.html` | 修改（2 处 DOM 块替换） |
| 3 | `src/test/js/batchSendTaskConsoleInteraction.test.js` | 修改（追加 W1-W10） |

文件数：**3** ✅  子系统数：**1** ✅
**不改**：`styles.css`、任何 `.kt`、任何 `.sql`。

---

## 验证命令

见主计划。专用：`node --test src/test/js/batchSendTaskConsoleInteraction.test.js`

---

## 验收标准

- **I3b-1**：`git diff src/main/resources/static/app.js` 中，P2b 建立的 7 个基座函数体**无改动行**；`grep -c "function renderBatchMultiPicker" app.js` == 1。
- **I3b-2**：W1 / W2 / W3 / W5 绿；`grep -n '"未联系"\|"已联系"' src/main/resources/static/app.js` 的命中全部位于 label/展示上下文，无一处进入 payload 或隐藏 input。
- **I3b-3**：`grep -c "operatorStatusOptions" app.js` ≥ 2（原定义 + `batchOperatorStatusOptions`），且无第二份状态字面量数组。
- **I3b-4**：W6 / W8 绿；对 5 个注册点逐个 `grep -n "operatorStatuses" app.js` **贴出行号与上下文**（`K-plan-quantified-claims-need-grep-receipts`），并在报告中标明哪 3 处是新增（缺口修复）。
- **I3b-5**：W7 绿。
- **S3b-1/2/3**：`git diff --stat` 不含 `styles.css`；`index.html` diff 中新增 class 全部落在主计划 X-3 的可复用表内；`grep -c "style=" <index.html diff>` == 0。
- **N3b-3**：`git diff app.js` 的 hunk 不覆盖 `#contactStatusFilter` 相关行。
- 回归：主计划全量测试命令通过；W10 绿。

---

## 人工验收清单

### A3b-1: 多选状态可保存可回显，且发的是英文枚举名
- 前置条件：P3a + P3b 已部署。
- 操作步骤：
  1. 「定时任务」→「新增任务」→ 填名称 →「专家状态」picker 勾选「未联系」与另一状态。
  2. **打开浏览器网络面板**，点「保存任务」，查看请求 body。
  3. 回到列表看「收件范围」列 → 点「编辑」重开。
- 预期结果：
  - 第 2 步：body 的 `operatorStatuses` 是**英文枚举名数组**（如 `["NOT_CONTACTED","CONTACTED"]`），**不是中文**。
  - 第 3 步：「收件范围」列出现 `状态: 未联系、已联系` 形态的一行（**中文**）；重开后 chip 是两个中文标签。
- 覆盖：O-3、I3b-2、S3b-1、S3b-3、IP-1

### A3b-2: 预估随选择实时更新
- 前置条件：接 A3b-1。
- 操作步骤：在编辑器里逐个勾选/取消状态，每次观察预估行。
- 预期结果：每次操作后约 0.5 秒预估行更新（防抖生效）；先显示「计算中…」再显示结果；快速连点不出现旧结果覆盖新结果。
- 覆盖：IP-2、主计划 N-5

### A3b-3: 手动执行「已修改」标红（缺口修复的核心验收）
- 前置条件：存在定时任务，其 `operatorStatuses = [未联系, 已联系]`。
- 操作步骤：
  1. 「手动执行」→ 采用该定时配置 → 观察「专家状态」字段。
  2. 取消「已联系」。
  3. 再加回「已联系」。
- 预期结果：
  - 第 1 步：2 个 chip，无红框、无「已修改」。
  - 第 2 步：出现红框 + 「已修改」+ 「专家状态: 未联系、已联系」原配置行。
  - 第 3 步：红框与徽标**全部消失**（顺序不同也必须消失）。
- ⚠️ **改动前该字段根本不会标红**（现状审计的缺口）。本条同时是新功能验收与缺口修复验收。
- 覆盖：I3b-4、I3b-5、S3b-2、IP-3

### A3b-4: 留空 = 不限
- 前置条件：无。
- 操作步骤：新建任务，「专家状态」一个都不选 → 观察预估 → 保存 → 列表看「收件范围」列。
- 预期结果：预估数为不限状态时的总数；列表「收件范围」列**不出现**「状态:」行。
- 覆盖：S3b-3

### A3b-5: 回归 —— 其余三个 picker 无变化
- 前置条件：无。
- 操作步骤：对「标签」「地区」「邮箱服务商」三个 picker 各做一遍：聚焦、搜索、勾选、取消、点 chip ×、Esc、点外部关闭。
- 预期结果：七种交互均与 P3b 上线前一致；空态文案分别为「没有匹配标签」「没有匹配地区」「没有匹配服务商」。
- 覆盖：N3b-1、N3b-2

### A3b-6: 回归 —— 专家列表页状态筛选仍是单选
- 前置条件：无。
- 操作步骤：专家列表页操作「状态」筛选。
- 预期结果：仍是原生 `<select>` 单选。
- 覆盖：N3b-3、主计划 N-1

### A3b-7: UI 目测 —— 四个 picker 视觉一致
- 前置条件：无。
- 操作步骤：定时任务编辑器中把「标签」「地区」「邮箱服务商」「专家状态」四个 picker 并排截图对比。
- 预期结果：控件高度 42px、内边距、聚焦光晕 3px `rgba(37,99,235,.1)`、chip 样式、⌄ 位置、下拉面板样式**逐项一致**。
- 覆盖：S3b-1、N3b-4
