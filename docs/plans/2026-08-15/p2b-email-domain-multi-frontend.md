# P2b：邮箱服务商筛选改多值（前端）

主计划：`batch-task-filters-main.md`（共享审计 X-3 前端样式盘点、验证命令在主计划）
前置计划：**P2a 必须已合并**（后端接受 `emailDomains: string[]`，`toView()` 返回 `emailDomains`）
子系统数：1（前端）  文件数：3

---

## 需求描述

### Observable outcome

定时任务编辑器与手动执行面板的「邮箱服务商」由单选 `<select>` 变为可搜索的多选 chip picker；可选 0-N 个域名；任务列表「收件范围」列显示全部已选域名；手动执行面板的「已修改」标红对该字段生效。

### What must NOT change

- **N2b-1** 标签 / 地区两个既有 picker（`batchConfigEditorTags` / `batchConfigEditorRegions` / `batchManualTags` / `batchManualRegions`）的 DOM、CSS、JS 一行不改（主计划 N-3）。
- **N2b-2** 专家列表页 `#expertEmailDomainFilter` 仍是单选 `<select>`，其 `app.js` 相关代码（`:3863`、`:3913`、`:3940`、`:4088`、`:4513`、`:4588-4623`、`:4662`、`:11415`、`:11433`、`:11673-11674`）一行不改（主计划 N-1）。
- **N2b-3** `styles.css` 零改动 —— 完全复用既有 `.batch-tag-picker` 族（主计划 X-3）。
- **N2b-4** 手动执行「已修改」对其余 7 个字段的行为不变（主计划 N-4）。
- **N2b-5** 收件预估的 500ms 防抖 + 请求序号丢弃过期响应机制不变（`refreshRecipientPreview`，`app.js:13975-14000`）。

### Out of scope

- 后端 —— 归 P2a。
- 「学科」「漏斗层级」改多选。
- 域名选项的分组、排序、常用置顶等增强。

---

## 关键不变量

### Invariant I2b-1: 通用多选 picker 基座只新建一次，不复制第三份
- Rule: 新增一族通用函数 `readBatchMultiPickerValue` / `setBatchMultiPickerValue` / `renderBatchMultiPicker` / `toggleBatchMultiPickerValue` / `openBatchMultiPicker` / `closeBatchMultiPicker` / `bindBatchMultiPicker`，选项来源由注册表 `BATCH_MULTI_PICKER_REGISTRY[valueId] = { options: () => [{value,label}], emptyText: string, previewKind: "editor"|"manual" }` 提供。P3b 的「专家状态」只往该注册表加一项，不再写第二套实现。
- Applies to: `app.js` 新增函数族。
- Violation consequence: 照抄 `renderBatchRegionPicker`（`app.js:13785`）会得到第三份几乎相同的 120 行代码；下次改 picker 行为要改三处，必漏一处。
- 来源: original

### Invariant I2b-2: 既有 tag / region picker 不被"顺手统一"
- Rule: 新基座**只服务新字段**。`readBatchTagPickerValue`（`:13627`）、`renderBatchTagPicker`（`:13643`）、`readBatchRegionPickerValue`（`:13773`）、`renderBatchRegionPicker`（`:13785`）及其 toggle/open/close/bind 全家**一行不改**。
- Applies to: `app.js`。
- Violation consequence: 标签/地区是已上线稳定功能，把它们迁到新基座会把本计划的回归面从 1 个字段扩到 3 个（主计划 N-3）。
- 来源: original

### Invariant I2b-3: 值以逗号分隔存隐藏 input，同族元素 id 契约不变
- Rule: 新 picker 沿用既有契约 —— 隐藏 input id = `<valueId>`，值为逗号分隔串；同族元素为 `<valueId>Chips` / `<valueId>Search` / `<valueId>Dropdown`；外壳带 `data-tag-picker="<valueId>"`。
- Applies to: `index.html` 新 DOM、`app.js` 新基座。
- Violation consequence: 偏离契约会让新 picker 与既有 CSS 选择器（`.batch-tag-picker:focus-within`）和事件委托失配。
- 来源: K-batch-picker-comma-delimited-contract（主计划 X-3 已给出 `readBatchRegionPickerValue` 的逐字实现作为证据）

### Invariant I2b-4: 手动执行 diff 的 5 个注册点必须同步
- Rule: `emailDomains` 参与「已修改」标红需要同步改 5 处，缺一即行为不一致：
  1. `normalizeManualSnapshot`（`:14290`）—— 归一化（数组需排序后比较，否则顺序差异误报"已修改"）
  2. `formatManualDiffValue`（`:14307`）—— 展示文案
  3. `computeManualDiffs` 的 `fieldDefs`（`:14336`）
  4. `computeAndRenderDiffs` 的 `fieldMap`（`:14380`）
  5. `clearAllDiffMarkers` 的 fields 数组（`:14423`）
- Applies to: `app.js`。
- Violation consequence: 漏 1/2 → 未改动也标红或改动了不标红；漏 3/4 → 红框不出现；漏 5 → 切换来源配置后红框不消失。
- 来源: K-recipient-scope-status-filter 的前端注册点清单（本轮已用 grep 复核行号）

### Invariant I2b-5: 数组比较必须顺序无关
- Rule: `normalizeManualSnapshot` 对 `emailDomains` 做 `slice().sort()` 后再参与相等比较。
- Applies to: `normalizeManualSnapshot`。
- Violation consequence: 运营取消再重选同一批域名，顺序变了就被判为"已修改"并标红，红框永远消不掉。
- 来源: original

---

## 样式契约

> 既有 class 的行号见主计划 X-3。本计划 **零新增 CSS**。

### S2b-1: 定时任务编辑器 —— 邮箱服务商字段
- **复用**：`.batch-config-field`（styles.css:8873）、`.batch-config-field-label`（:8676）、`.batch-tag-picker`（:8915）、`.batch-tag-picker-control`（:8920）、`.batch-tag-picker-chips`（:8938）、`.batch-tag-picker-chip`（:8942）、`.batch-tag-picker-chip button`（:8956）、`.batch-tag-picker-search`（:8966）、`.batch-tag-picker-chevron`（:8977）、`.batch-tag-picker-dropdown`（:8985）、`.batch-tag-picker-option`（:8999）、`.batch-tag-picker-check`（:9019）、`.batch-tag-picker-empty`（:9036）。
  **禁止**执行 agent 自造任何"近似"样式替代上述 class。
- **新增**：无。`styles.css` 不得出现在本计划的 diff 中。
- **DOM 结构**：替换 `index.html:1200-1206`（现为 `<label class="batch-config-field"> + <select id="batchConfigEditorEmailDomain">`）为**逐字**：

```html
                        <div class="batch-config-field">
                            <span class="batch-config-field-label">邮箱服务商</span>
                            <div class="batch-tag-picker" data-tag-picker="batchConfigEditorEmailDomains">
                                <div class="batch-tag-picker-control">
                                    <div id="batchConfigEditorEmailDomainsChips" class="batch-tag-picker-chips"></div>
                                    <input type="search" id="batchConfigEditorEmailDomainsSearch" class="batch-tag-picker-search" placeholder="搜索并选择邮箱服务商" autocomplete="off" aria-controls="batchConfigEditorEmailDomainsDropdown" aria-expanded="false">
                                    <span class="batch-tag-picker-chevron" aria-hidden="true">⌄</span>
                                </div>
                                <input type="hidden" id="batchConfigEditorEmailDomains" value="">
                                <div id="batchConfigEditorEmailDomainsDropdown" class="batch-tag-picker-dropdown" role="listbox" aria-multiselectable="true" hidden></div>
                            </div>
                        </div>
```

- **禁止项**：inline style；新 class；对既有 class 规则块的任何修改；把外层写成 `<label>`。

### S2b-2: 手动执行面板 —— 邮箱服务商字段
- **复用**：同 S2b-1，外加 `.batch-config-diff-badge`（styles.css:8904）、`.batch-config-diff-original`（:8913）、`.batch-manual-section .batch-config-field`（:8887）、`.batch-manual-section .batch-config-field.is-config-diff`（:8894）。
- **新增**：无。
- **DOM 结构**：替换 `index.html:1368-1375`（现为 `<label class="batch-config-field" id="manualFieldEmailDomain">` + `<select>`）为**逐字**：

```html
                    <div class="batch-config-field" id="manualFieldEmailDomain">
                        <span class="batch-config-field-label">邮箱服务商</span>
                        <div class="batch-tag-picker" data-tag-picker="batchManualEmailDomains">
                            <div class="batch-tag-picker-control">
                                <div id="batchManualEmailDomainsChips" class="batch-tag-picker-chips"></div>
                                <input type="search" id="batchManualEmailDomainsSearch" class="batch-tag-picker-search" placeholder="搜索并选择邮箱服务商" autocomplete="off" aria-controls="batchManualEmailDomainsDropdown" aria-expanded="false">
                                <span class="batch-tag-picker-chevron" aria-hidden="true">⌄</span>
                            </div>
                            <input type="hidden" id="batchManualEmailDomains" value="">
                            <div id="batchManualEmailDomainsDropdown" class="batch-tag-picker-dropdown" role="listbox" aria-multiselectable="true" hidden></div>
                        </div>
                        <span class="batch-config-diff-badge" hidden>已修改</span>
                        <div class="batch-config-diff-original" hidden></div>
                    </div>
```

⚠️ **外层必须是 `<div>` 不是 `<label>`**（主计划 X-3 已论证：`<label>` 的隐式聚焦转移会让下拉立刻收起）。既有的 `manualFieldTags`（`index.html:1391`）、`manualFieldRegions`（`index.html:1405`）正是 `<div>`，本条与它们一致。
- **禁止项**：同 S2b-1；`.batch-config-diff-badge` 与 `.batch-config-diff-original` 必须保留且保持在字段容器的**直接子元素**位置（`.batch-config-diff-badge` 是 `position:absolute`，依赖父级 `.batch-config-field` 的 `position:relative`，styles.css:8873-8879）。

### S2b-3: 任务列表「收件范围」列
- **复用**：`.batch-task-scope-line`（styles.css:8520-8521）。
- **新增**：无。
- **DOM 结构**：`renderBatchConfigRow`（`app.js:13386`）内，把
  ```js
  if (c.emailDomain) scopeParts.push("服务商: " + escapeHtml(c.emailDomain));
  ```
  改为
  ```js
  if (Array.isArray(c.emailDomains) && c.emailDomains.length > 0) scopeParts.push("服务商: " + escapeHtml(c.emailDomains.join(", ")));
  ```
  外层 `<span class="batch-task-scope-line">` 包装逻辑不动。
- **禁止项**：不新增 pill/badge 元素；不改列宽；不改 `.batch-task-scope-line` 规则块。

---

## 现状审计

> 前端样式盘点（可复用 class 行号、设计 token 实值、picker DOM 契约、`readBatchRegionPickerValue` 逐字实现）见**主计划 X-3**，此处不重复。

### `emailDomain` 在批量任务控制台的全部前端注册点（grep 取证）

```
$ grep -n "EmailDomain\|emailDomain" src/main/resources/static/app.js
```
去掉专家列表页的 11 处（`:3863` `:3913` `:3940` `:4088` `:4513` `:4588` `:4601` `:4607` `:4622-4623` `:4662` `:11415` `:11433` `:11673-11674`，属 N2b-2 禁改区）与旧 KV 面板的 6 处（`:5820` `:5905` `:6182` `:6185` `:6288` —— 属 `#batchSendEmailDomain`，本轮不动），**批量任务控制台的注册点为 14 处**：

| # | 行 | 函数 | 作用 |
|---|---|---|---|
| 1 | 13389 | `renderBatchConfigRow` | 任务列表「收件范围」列（S2b-3） |
| 2 | 13544 | `showBatchConfigEditor` | `fillBatchConfigEditorProviderSelect(config ? config.emailDomain : "")` 回填 |
| 3 | 13937 | `buildConfigEditorRecipientSnapshot` | 预估快照 |
| 4 | 13956 | `buildManualExecutionSnapshot` | 手动预估快照 |
| 5 | 14003 | `fillBatchConfigEditorProviderSelect` | 填 `<option>`（本计划改为填注册表 options） |
| 6 | 14055 | `saveBatchConfigEditor` | 保存 payload |
| 7 | 14138 | `deepCloneConfig` | 来源配置深拷贝 |
| 8 | 14158 | `fillManualFormDefaults` | 手动面板默认值 |
| 9 | 14181 | `fillManualFormFromDraft` | `setVal("batchManualEmailDomain", ...)` |
| 10 | 14192 | `fillManualFormFromDraft` | `fillBatchManualProviderSelect(d.emailDomain)` |
| 11 | 14212 | `fillBatchManualProviderSelect` | 填 `<option>` |
| 12 | 14279 | `readManualFormValues` | 读手动表单 |
| 13 | 14296 | `normalizeManualSnapshot` | diff 归一化（I2b-4 #1 / I2b-5） |
| 14 | 14316 | `formatManualDiffValue` | `if (key === "emailDomain") return value \|\| "全部服务商";`（I2b-4 #2） |
| 15 | 14347 | `computeManualDiffs` fieldDefs | `{ key: "emailDomain", label: "邮箱服务商" }`（I2b-4 #3） |
| 16 | 14393 | `computeAndRenderDiffs` fieldMap | `emailDomain: "manualFieldEmailDomain"`（I2b-4 #4） |
| 17 | 14424 | `clearAllDiffMarkers` | fields 数组含 `"manualFieldEmailDomain"`（I2b-4 #5） |
| 18 | 15042 | `bindBatchSendTaskEvents` | 预估触发的 `change` 监听器数组含 `"batchConfigEditorEmailDomain"` |

（表内 18 行，其中 #14-#17 是 I2b-4 的前 4 项；第 5 项 `clearAllDiffMarkers` 是 #17。）

### 既有 picker 的绑定时机

`bindBatchRegionPicker(valueId)` 定义在 `app.js:13841`，末行 `renderBatchRegionPicker(valueId)` 做首次渲染。需确认其调用点在 `bindBatchSendTaskEvents` 内，新 picker 的 `bindBatchMultiPicker` 必须放在同一位置、同一时机绑定。

### 交互点

| IP | 说明 |
|---|---|
| IP-1 | `showBatchConfigEditor`（读 config）→ `saveBatchConfigEditor`（写 payload）：回显与保存必须同字段名，否则打开再保存会把已配的域名清空 |
| IP-2 | picker 值变化 → `scheduleRecipientPreview("editor"/"manual")`：既有 `<select>` 靠 `change` 事件（`:15042`），picker 无 `change` 事件，必须在 `toggleBatchMultiPickerValue` 内主动触发（`toggleBatchRegionPickerValue` 已有此范式，`app.js:13821-13823`） |
| IP-3 | 来源配置带入（`fillManualFormFromDraft`）→ diff 计算（`computeManualDiffs`）：I2b-4 的 5 点必须同步 |

### 既有测试

`src/test/js/batchSendTaskConsoleInteraction.test.js` —— 用 `vm.runInContext` + `extractFn` 抽单函数体执行，DOM 用简易 stub 对象。新 picker 的测试沿用同一套写法。

---

## 实现方案

### T2b-1 新增通用多选 picker 基座（I2b-1 / I2b-2 / I2b-3）

文件：`src/main/resources/static/app.js`，插入位置：`renderBatchRegionPicker` 全家（`:13773-13870`）**之后**，`buildConfigEditorRecipientSnapshot`（`:13919`）之前。

新增注册表与 7 个函数。注册表初始只有一项：

```js
/* 通用多选 picker 注册表（I2b-1）。P3b 只往这里加一项，不再复制实现。
   options 是函数以便延迟求值（服务商列表来自异步预加载）。 */
var BATCH_MULTI_PICKER_REGISTRY = {
    batchConfigEditorEmailDomains: {
        options: function() { return batchProviderOptions(); },
        emptyText: "没有匹配服务商",
        previewKind: "editor"
    },
    batchManualEmailDomains: {
        options: function() { return batchProviderOptions(); },
        emptyText: "没有匹配服务商",
        previewKind: "manual"
    }
};
```

7 个函数**逐字照 `renderBatchRegionPicker` 家族改写**（`app.js:13773-13870`），差别只有三点：
1. 选项来源从常量 `BATCH_REGION_OPTIONS` 换成 `BATCH_MULTI_PICKER_REGISTRY[valueId].options()`；
2. 空态文案从 `'没有匹配地区'` 换成 `meta.emptyText`；
3. `toggleBatchMultiPickerValue` 末尾的预估触发用 `meta.previewKind`，并调用新的 `notifyBatchMultiPickerChanged(valueId)`（用于同步 `batchTaskState.manualDraft`，照 `notifyBatchRegionPickerChanged`，`app.js:13677`）。

新增 `batchProviderOptions()`：从 `batchTaskState.preloadedProviders` 产出 `[{value,label}]`，兼容既有两种元素形态（字符串 或 `{domain}`）—— 该兼容逻辑已存在于 `fillBatchConfigEditorProviderSelect`（`app.js:14005-14012`），照搬其判断。

⚠️ **不得**修改 `readBatchTagPickerValue` / `renderBatchTagPicker` / `readBatchRegionPickerValue` / `renderBatchRegionPicker` 及其 toggle/open/close/bind 的任何一行（I2b-2）。

### T2b-2 index.html DOM 替换（S2b-1 / S2b-2）

文件：`src/main/resources/static/index.html`
- 替换 `:1200-1206` 为 S2b-1 的逐字块。
- 替换 `:1368-1375` 为 S2b-2 的逐字块。

其余一行不改。

### T2b-3 定时任务编辑器接线（IP-1 / IP-2）

文件：`src/main/resources/static/app.js`

| 原位置 | 改法 |
|---|---|
| `:13544` `showBatchConfigEditor` | `setBatchMultiPickerValue("batchConfigEditorEmailDomains", config && Array.isArray(config.emailDomains) ? config.emailDomains : []);` —— 删除 `fillBatchConfigEditorProviderSelect(...)` 调用 |
| `:13937` `buildConfigEditorRecipientSnapshot` | `emailDomains: readBatchMultiPickerValue("batchConfigEditorEmailDomains"),` |
| `:14002-14016` `fillBatchConfigEditorProviderSelect` | **整个函数删除**（其 provider 兼容逻辑已迁入 `batchProviderOptions`） |
| `:14055` `saveBatchConfigEditor` | `emailDomains: readBatchMultiPickerValue("batchConfigEditorEmailDomains"),` |
| `:15042` `bindBatchSendTaskEvents` 监听器数组 | 移除 `"batchConfigEditorEmailDomain"`（picker 无 change 事件，改由 T2b-1 的 toggle 主动触发预估，IP-2） |
| `bindBatchSendTaskEvents` 内 | 新增 `bindBatchMultiPicker("batchConfigEditorEmailDomains"); bindBatchMultiPicker("batchManualEmailDomains");`，位置紧邻既有 `bindBatchRegionPicker(...)` 调用 |

### T2b-4 手动执行面板接线 + diff 5 点（I2b-4 / I2b-5）

文件：`src/main/resources/static/app.js`

| 原位置 | 改法 |
|---|---|
| `:13956` `buildManualExecutionSnapshot` | `emailDomains: values.emailDomains,` |
| `:14138` `deepCloneConfig` | `emailDomains: Array.isArray(c.emailDomains) ? c.emailDomains.slice() : [],` |
| `:14158` `fillManualFormDefaults` | `emailDomains: [],` |
| `:14181` `fillManualFormFromDraft` | `setBatchMultiPickerValue("batchManualEmailDomains", Array.isArray(d.emailDomains) ? d.emailDomains : []);` |
| `:14192` | 删除 `fillBatchManualProviderSelect(d.emailDomain);` |
| `:14211-14225` `fillBatchManualProviderSelect` | **整个函数删除** |
| `:14279` `readManualFormValues` | `emailDomains: readBatchMultiPickerValue("batchManualEmailDomains"),` |
| `:14296` `normalizeManualSnapshot` **（I2b-4 #1 / I2b-5）** | `emailDomains: (Array.isArray(v.emailDomains) ? v.emailDomains : []).map(function(s){return String(s).trim();}).filter(Boolean).slice().sort(),` |
| `:14316` `formatManualDiffValue` **（#2）** | `if (key === "emailDomains") return (Array.isArray(value) && value.length > 0) ? value.join("、") : "全部服务商";` |
| `:14347` `computeManualDiffs` fieldDefs **（#3）** | `{ key: "emailDomains", label: "邮箱服务商" }` |
| `:14393` `computeAndRenderDiffs` fieldMap **（#4）** | `emailDomains: "manualFieldEmailDomain"`（DOM id 不变，只改 key） |
| `:14424` `clearAllDiffMarkers` **（#5）** | 数组中的 `"manualFieldEmailDomain"` 保持不变（DOM id 未变，本项无需改动，但**必须核对**其仍在数组中） |

⚠️ `computeManualDiffs` 的既有相等比较若用 `===`，数组永远不等 → 必须确认它对 `tags` / `regions` 已有数组比较分支；若有，`emailDomains` 复用同一分支；若无（即 tags/regions 走的是别的路径），需在 `normalizeManualSnapshot` 里把数组序列化成排序后的逗号串再比较。**执行前必须先读 `:14336-14378` 确认实际比较方式，并在实现说明中写出结论 —— 不得假设。**

### T2b-5 任务列表列（S2b-3）

文件：`src/main/resources/static/app.js`，`:13389`，按 S2b-3 逐字替换。

### T2b-6 测试

文件：`src/test/js/batchSendTaskConsoleInteraction.test.js`，追加：

| 用例 | 断言 |
|---|---|
| V1 | `setBatchMultiPickerValue("batchConfigEditorEmailDomains", ["a.com","b.com"])` 后 `readBatchMultiPickerValue` 返回 `["a.com","b.com"]`（I2b-3 逗号契约） |
| V2 | `renderBatchMultiPicker` 产出的 chips HTML 含 2 个 `.batch-tag-picker-chip`；dropdown 中已选项带 `is-selected` 与 `✓`（S2b-1 复用既有 class） |
| V3 | 选项为空时 dropdown 内容为 `<div class="batch-tag-picker-empty">没有匹配服务商</div>`（`emptyText` 生效） |
| V4 | `showBatchConfigEditor({emailDomains:["a.com"]})` → 隐藏 input value 为 `"a.com"`；`showBatchConfigEditor(null)` → 为 `""`（IP-1） |
| V5 | `saveBatchConfigEditor` 的 payload 含 `emailDomains: ["a.com","b.com"]`（IP-1） |
| V6 | `normalizeManualSnapshot({emailDomains:["b.com","a.com"]})` 与 `{emailDomains:["a.com","b.com"]}` 结果**相等**（I2b-5 顺序无关） |
| V7 | `formatManualDiffValue("emailDomains", [])` === `"全部服务商"`；`formatManualDiffValue("emailDomains", ["a.com","b.com"])` === `"a.com、b.com"`（I2b-4 #2） |
| V8 | `computeManualDiffs` 在 draft=`["a.com"]`、source=`["a.com","b.com"]` 时把 `emailDomains` 判为 diff；两者同为 `["a.com"]` 时判为不 diff（I2b-4 #3） |
| V9 | `renderBatchConfigRow({emailDomains:["a.com","b.com"], ...})` 输出含 `服务商: a.com, b.com` 且被 `<span class="batch-task-scope-line">` 包裹；`emailDomains:[]` 时不产生该行（S2b-3） |
| V10 | 回归：`readBatchRegionPickerValue` / `readBatchTagPickerValue` 行为未变（对既有函数各跑一次基础断言，锁死 I2b-2） |

---

## 变更文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `src/main/resources/static/app.js` | 修改 |
| 2 | `src/main/resources/static/index.html` | 修改（2 处 DOM 块替换） |
| 3 | `src/test/js/batchSendTaskConsoleInteraction.test.js` | 修改（追加 V1-V10） |

文件数：**3**（≤10 ✅）  子系统数：**1**（≤2 ✅）
**不改**：`styles.css`、任何 `.kt`、任何 `.sql`。

---

## 验证命令

见主计划「验证命令」节。本计划专用：

```bash
# 本计划相关测试（迭代用）
node --test src/test/js/batchSendTaskConsoleInteraction.test.js

# 全部 JS 测试
node --test src/test/js/*.test.js

# 全量（含 Kotlin）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

---

## 验收标准

- **I2b-1**：`grep -c "BATCH_MULTI_PICKER_REGISTRY" src/main/resources/static/app.js` ≥ 3（定义 + 至少 2 处使用）；`grep -c "function renderBatchMultiPicker" app.js` == 1。
- **I2b-2**：`git diff src/main/resources/static/app.js` 的 hunk **不覆盖** `:13627-13870` 区间的 tag/region picker 函数体；V10 绿。
- **I2b-3**：V1 绿；`grep -n 'data-tag-picker="batchConfigEditorEmailDomains"' index.html` 与 `id="batchConfigEditorEmailDomainsChips"` / `Search` / `Dropdown` 四者齐全。
- **I2b-4**：V7 / V8 绿；对 5 个注册点逐个 `grep -n "emailDomains" src/main/resources/static/app.js` 贴出行号与上下文（`K-plan-quantified-claims-need-grep-receipts`：必须贴 grep 输出，不接受"已检查"）。
- **I2b-5**：V6 绿。
- **S2b-1 / S2b-2 / S2b-3**：`git diff --stat` 不含 `styles.css`；`git diff src/main/resources/static/index.html | grep -c 'style='` == 0；`git diff index.html` 中新增的 class 全部出现在主计划 X-3 的可复用 class 表内（逐个核对，不得有表外 class）。
- **N2b-2**：`git diff src/main/resources/static/app.js` 的 hunk 不覆盖 `:3863` `:3913` `:3940` `:4088` `:4513` `:4588-4623` `:4662` `:11415` `:11433` `:11673-11674` 任一行。
- **N2b-3**：`git diff --stat` 不含 `styles.css`。
- 回归：执行主计划「验证命令」节的全量测试命令通过。

---

## 人工验收清单

### A2b-1: 定时任务多选服务商可保存可回显
- 前置条件：P2a + P2b 均已部署；ES 中至少有 gmail.com、outlook.com 两个服务商的专家。
- 操作步骤：
  1. 「批量邮件任务控制台」→「定时任务」→「新增任务」，填名称「多选服务商测试」。
  2. 点「邮箱服务商」输入框 → 下拉出现 → 勾选 gmail.com、outlook.com。
  3. 观察输入框内 chip 与下方预估行。
  4. 保存 → 回到列表 → 观察该行「收件范围」列 → 点「编辑」重新打开。
- 预期结果：
  - 第 2 步：下拉出现，两项左侧出现 ✓；输入框内出现 2 个 chip，每个带 ×。
  - 第 3 步：预估行文案为「当前条件命中 **N** 位专家（其中未联系 x、可重试 y）」，N 大于只选 gmail.com 时的数字。
  - 第 4 步：列表「收件范围」列出现一行 `服务商: gmail.com, outlook.com`；重新打开后 chip 仍是这两个。
- 覆盖：O-2、I2b-3、S2b-1、S2b-3、IP-1

### A2b-2: 取消选择 / 清空
- 前置条件：接 A2b-1，任务已保存且有 2 个 chip。
- 操作步骤：编辑该任务 → 点 gmail.com chip 上的 × → 再点 outlook.com chip 上的 × → 观察预估行 → 保存 → 重新打开。
- 预期结果：chip 逐个消失；全部移除后预估行数字回到"不限服务商"的总数；重新打开后无 chip，「收件范围」列不再出现「服务商:」行。
- 覆盖：I2b-3、S2b-3

### A2b-3: 手动执行的「已修改」标红
- 前置条件：存在定时任务「多选服务商测试」，其 `emailDomains = [gmail.com, outlook.com]`。
- 操作步骤：
  1. 切到「手动执行」tab →「采用定时任务配置」选中「多选服务商测试」。
  2. 观察「邮箱服务商」字段：应有 2 个 chip，**无**红框、**无**「已修改」徽标。
  3. 移除 outlook.com。
  4. 再把 outlook.com 加回来。
- 预期结果：
  - 第 2 步：字段边框为默认灰（`rgba(15,23,42,.08)`），无 `已修改` 字样。
  - 第 3 步：字段出现红框（边框 `#e11d48`、底色 `#fff7f8`），右上角出现红色「已修改」，下方出现「邮箱服务商: gmail.com、outlook.com」形态的原配置行。
  - 第 4 步：红框、徽标、原配置行**全部消失**（顺序不同也必须消失 —— 这正是 I2b-5）。
- 覆盖：I2b-4、I2b-5、S2b-2、IP-3

### A2b-4: 手动执行的值不回写定时配置
- 前置条件：接 A2b-3。
- 操作步骤：在手动执行面板把服务商改成只剩 gmail.com → 点「确认并执行」→ 回到「定时任务」tab → 编辑「多选服务商测试」。
- 预期结果：定时任务的服务商仍是 2 个 chip（gmail.com、outlook.com）。
- 覆盖：主计划 N-4 的语义边界

### A2b-5: 回归 —— 标签 / 地区 picker 无变化
- 前置条件：无。
- 操作步骤：在定时任务编辑器与手动执行面板分别操作「标签」「地区」picker：聚焦、搜索、勾选、取消、点 chip ×、按 Esc、点外部关闭。
- 预期结果：七种交互行为与改动前完全一致；chip 高度、下拉最大高度、✓ 位置、空态文案（「没有匹配标签」/「没有匹配地区」）无变化。
- 覆盖：N2b-1、I2b-2

### A2b-6: 回归 —— 专家列表页仍是单选
- 前置条件：无。
- 操作步骤：打开专家列表页，操作「邮箱服务商」筛选。
- 预期结果：仍是原生 `<select>` 下拉，单选；不是 chip picker。
- 覆盖：N2b-2、主计划 N-1

### A2b-7: UI 目测 —— 新字段与既有 picker 视觉一致
- 前置条件：无。
- 操作步骤：在定时任务编辑器中把「标签」「地区」「邮箱服务商」三个 picker 并排截图对比。
- 预期结果：三者的控件高度（min-height 42px）、内边距（6px 36px 6px 10px）、聚焦光晕（3px `rgba(37,99,235,.1)`）、chip 样式、⌄ 位置、下拉面板样式**逐项一致**，无肉眼可见差异。
- 覆盖：S2b-1、N2b-3
