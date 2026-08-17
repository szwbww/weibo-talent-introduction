# A1：定时任务列表行错位 + 执行日志抽屉视觉/层级修复

> 编号：**A1**（全链第 1 份，见 `00-execution-order.md`）。依赖：无。
> 缓存键取值：`20260817-v1-batch-console-row-drawer`（S-5）。
> 共享不变量 M-1…M-4、共享审计 X-1…X-3 见 `batch-console-log-drawer-main.md`，本文不重复。

## 需求描述

**Observable outcome**

1. 定时任务列表中，筛选条件多于 3 条的任务行**七列全部对齐**（模板列显示模板徽章、
   执行计划列显示 cron 文案、操作列宽度 170px），收件范围列常驻 3 行，其余收进
   「展开剩余 N 项」，门禁 pill 恒在该列最后一行且永不丢失。
2. 执行日志抽屉打开时，底下的表格内容**完全不可见**（当前 55% 透明）。
3. 执行日志抽屉从「定时任务 / 手动执行」页签下沿开始，**不再覆盖弹窗标题与弹窗关闭按钮**；
   抽屉开着时仍可点弹窗右上角 × 关掉整个控制台。
4. 执行日志指标卡在有「剩余」时排成 3 列 × 2 行，不再出现末尾单卡独占一行。
5. 一次无失败、无跳过、无错误样例的执行，日志抽屉不再显示这三个空区块。

**What must NOT change**

- `renderBatchConfigRow` 前 3 条筛选行的文案与包装（`服务商: a.com, b.com`、`状态: 未联系` 等）
  逐字不变；空筛选仍渲染 `无限制`；门禁 pill 仍是三态之一且恒输出一行。
- 抽屉内既有渲染函数的输出结构：`renderBatchTimeline`、`renderReasons`、`renderErrorSamples`、
  `renderLogStatusInfo`、`renderIntegrityWarning`、`renderBatchLiveSection` 的 DOM 与文案。
- `.task-modal.batch-send-task-modal` 规则块内**仍不得出现** `background-color:`
  （`batchSendTaskConsoleVisualFix.test.js:54-61` 断言）。
- 抽屉宽度 `min(620px, 72%)`、`z-index: 4`、`overflow: auto`、左边框与阴影不变。
- 「批次时间线」区块**保持恒显示**（含空态），只隐藏失败原因/跳过原因/错误样例三块。

**Out of scope（明确延后）**

- 手动执行页签的日志入口、`switchBatchSendTab` 关抽屉行为、独立执行日志可达性 → P2。
- 「专家联系」改名、批量发送按钮迁移 → P3。
- `scopeParts` 中地区串未走 `escapeHtml`（`c.regions.map(regionLabel).join("、")`，app.js:13399）
  —— 地区是 `CountryContinentMapping` 的 9 个固定英文常量，无外部输入面；本轮记为观察项，不改。
- 抽屉的移动端断点（`styles.css:9288` `.batch-log-drawer { width: 100% }`）不动。
- `showStatus("执行已启动 executionId: " + ...)` 把裸 executionId 抛给运营，不改。

## 关键不变量

### Invariant I-1: 收件范围列的截断发生在数组层，不在字符串层
- Rule: 收件范围单元格由 `scopeParts` **数组**决定可见性 —— 前 `SCOPE_VISIBLE_LINES`(=3) 个元素
  常驻，其余整体放入一个 `<details>`；`<td class="batch-task-scope">` 的内容**不得**再经过
  任何字符级截断。
- Applies to: `app.js` `renderBatchConfigRow`（唯一写入点，见现状审计）。
- Violation consequence: 见 M-1。当前 `scopeHtml.substring(0, 300)`（app.js:13418）即为违例，
  实测使该行 `<td>` 数从 7 掉到 6。
- 来源: original

### Invariant I-2: 门禁 pill 恒输出且永远在收件范围列最后
- Rule: `batchGatePillHtml(c)` 的返回值恒被包进一个 `<span class="batch-task-scope-line">`
  并追加在**可见行与 `<details>` 之后**；它既不参与 `scopeParts`，也不参与 3 行上限计数，
  更不能被折叠进 `<details>`。
- Applies to: `app.js` `renderBatchConfigRow`。
- Violation consequence: ① 并入 `scopeParts` 会让「无限制」分支被 pill 顶掉（V9/W9 回归断言）；
  ② 参与计数或被折叠 → 列表看不到门禁状态，当前 300 截断已经造成这个后果。
- 来源: original（沿用 P4b T4b-5 的既有约束）

### Invariant I-3: 抽屉是不透明面，且不覆盖弹窗 header/tabs
- Rule: `.batch-log-drawer` 的 `background` 不得再引用 `--panel-bg`；其定位上下文是新增的
  `.batch-send-task-body`（包住两个 tab panel 与抽屉本身），而不是 `.batch-send-task-modal`。
- Applies to: `styles.css` `.batch-log-drawer` / 新增 `.batch-send-task-body`；
  `index.html` 批量控制台弹窗的 DOM 层级。
- Violation consequence: ① 仍引用 `--panel-bg`（`rgba(255,255,255,.55)` / 暗色
  `rgba(21,31,48,.55)`）→ 底层表格透出；② 定位上下文仍是 modal → 抽屉 `top:0` 压住弹窗 ×，
  实测点击弹窗 × 命中的是抽屉 ×。
- 来源: original

### Invariant I-4: 空区块隐藏只作用于「异常类」三块
- Rule: 「失败原因」「跳过原因」「错误样例」三个 section wrapper 在对应数据为空时整块
  `hidden = true`；「批次时间线」section **永不隐藏**，空态仍由 `renderBatchTimeline` 输出
  `无执行过程记录`。
- Applies to: `app.js` `renderBatchExecutionDetail`、`clearBatchLogDisplay`。
- Violation consequence: 时间线一并隐藏 → 运行中的执行在还没产生 progress row 时整个抽屉空白，
  运营无法判断任务是否真的在跑（K-batch-console-log-timeline 的原始诉求被回退）。
- 来源: K-batch-console-log-timeline

## 样式契约

### S-1: 弹窗主体定位容器（新增 DOM + 新增 CSS）

**DOM 结构** —— 在 `index.html` 批量控制台弹窗中，用一个新 div 把**两个 tab panel 与抽屉**
一起包住（`<nav class="batch-send-tabs">` 留在外面）：

```html
<nav class="batch-send-tabs">
    <button class="batch-send-tab is-active" data-tab="scheduled">定时任务</button>
    <button class="batch-send-tab" id="batchManualTab" data-tab="manual">手动执行</button>
</nav>

<div class="batch-send-task-body">
    <!-- Scheduled Tasks Tab -->
    <div id="batchScheduledPanel" class="batch-send-tab-panel">
        …原内容不动…
    </div>
    <div id="batchManualPanel" class="batch-send-tab-panel" hidden>
        …原内容不动…
    </div>
    <!-- Log Drawer -->
    <aside id="batchExecutionLogDrawer" class="batch-log-drawer" hidden>
        …原内容不动…
    </aside>
</div>
```

**新增 CSS**（逐字复制，插入到 `styles.css` 现 `.batch-log-drawer` 规则块**之前**）：

```css
.batch-send-task-body {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}
```

- 禁止项：不得给 `.batch-send-task-modal` 增删 `position` / `background-color`
  （X-2 的 `:54-61` 断言盯着 `background-color`）；不得改 `.batch-send-tab-panel`
  的 `flex: 1; min-height: 0; overflow: auto`（`styles.css:8500-8505`）。

### S-2: 抽屉不透明面（就地修改既有 class）

`.batch-log-drawer` 现规则块位于 `styles.css:8773-8785`。**就地修改**，改后完整规则块逐字为：

```css
.batch-log-drawer {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 4;
  width: min(620px, 72%);
  padding: 22px;
  overflow: auto;
  background: rgba(255, 255, 255, .96);
  backdrop-filter: blur(8px);
  border-left: 1px solid rgba(15, 23, 42, .08);
  box-shadow: -12px 0 32px rgba(15, 23, 42, .12);
}
```

- 唯一改动：`background: var(--panel-bg)` → `background: rgba(255, 255, 255, .96)`，
  并新增一行 `backdrop-filter: blur(8px)`。其余 10 行**一字不改**。
- 取值来源不是拍脑袋：同文件 `.batch-manual-actions-sticky`（`styles.css:9166-9178`）与
  `.batch-config-editor-actions`（`styles.css:8684-8697`）用的就是这一对值，抽屉是漏网的那个。
- `.batch-log-drawer` 全仓使用点 grep 结果（2 处，均无需其他改动）：
  `styles.css:8773`（本规则块）、`styles.css:9288`（`@media (max-width: 760px)` 内
  `.batch-log-drawer { width: 100% }`，不动）、`index.html:1504`（class 引用，不动）。
- 暗色模式：本次**有意**不加 `prefers-color-scheme: dark` 覆盖 —— 弹窗内其余不透明面
  （`.batch-manual-actions-sticky` 等）同样是硬编码浅色，抽屉单独适配会造成同一弹窗内深浅撕裂。
  记为观察项，整体暗色适配另立计划。

### S-3: 收件范围折叠块（复用 + 少量新增）

**复用**：`.log-detail`（`styles.css:3111-3126`，含 `summary` 的 primary 色/加粗/11px 与 hover）。
禁止执行 agent 另造近似样式；禁止修改 `.log-detail` 规则块本身（它还服务
`app.js:7605/7611/7654/7664` 四处邮件日志渲染）。

**DOM 骨架**（`renderBatchConfigRow` 产出的收件范围单元格）：

```html
<td class="batch-task-scope">
  <span class="batch-task-scope-line">漏斗: CANDIDATE</span>
  <span class="batch-task-scope-line">地区: 南美洲、非洲、大洋洲、其他</span>
  <span class="batch-task-scope-line">服务商: gmail.com</span>
  <details class="log-detail batch-task-scope-more">
    <summary>展开剩余 2 项</summary>
    <span class="batch-task-scope-line">学科: 仅理工科</span>
    <span class="batch-task-scope-line">状态: 未联系</span>
  </details>
  <span class="batch-task-scope-line"><span class="batch-gate-pill is-off">门禁过滤 · 关</span></span>
</td>
```

**新增 CSS**（逐字复制，追加在 `styles.css` 现 `.batch-task-scope-line` 两条规则
（`styles.css:8531-8532`）之后）：

```css
.batch-task-scope-more { margin-top: 3px; }
.batch-task-scope-more .batch-task-scope-line { margin-top: 3px; color: var(--text-muted); }
.batch-task-scope-more + .batch-task-scope-line { margin-top: 3px; }
```

- 第 3 条不是可选项：`.batch-task-scope-line + .batch-task-scope-line`（`styles.css:8532`）
  是相邻兄弟选择器，`<details>` 插进去会**打断兄弟链**，导致其后的门禁 pill 行贴死上一行。
- `.batch-task-scope-more{margin-top:3px}` 覆盖 `.log-detail{margin-top:4px}` 依赖
  **同特异度后来居上**，因此这三条必须落在 `styles.css:3111` 之后（8531 行区域天然满足）。
- 禁止项：inline style；不得给 `<summary>` 加新 class；不得改 `.batch-task-column-scope { width: 26% }`。

### S-4: 指标卡三列（就地修改既有 class）

`styles.css:9190` 现为：

```css
.batch-log-metrics { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 8px; margin: 14px 0; }
```

改为（逐字）：

```css
.batch-log-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin: 14px 0; }
```

- 仅 `5` → `3` 一处改动。`@media (max-width: 760px)` 内的
  `.batch-log-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }`（`styles.css:9289`）不动。
- 依据：`renderOutcomeMetrics` 固定输出 5 项（目标/成功/失败/跳过/耗时），`remaining > 0` 时
  第 6 项「剩余」。5 列布局下 6 项 = 5 + 1；3 列布局下 = 3 + 3，两种情况都齐整（5 项时为 3 + 2）。

### S-5: 缓存键

`index.html` 三处，逐字改为同一新值 `20260817-v1-batch-console-row-drawer`：

```html
<link rel="stylesheet" href="styles.css?v=20260817-v1-batch-console-row-drawer">
<script src="trust-reply-workbench.js?v=20260817-v1-batch-console-row-drawer"></script>
<script src="app.js?v=20260817-v1-batch-console-row-drawer"></script>
```

同步把 `batchSendTaskConsoleVisualFix.test.js:49-51` 三条断言里的字符串改成同一值（M-2）。
`task-modal-runtime.js`（`index.html:2028`）现在**没有** `?v=`，本计划**不给它加**——
K-frontend-cache-key-triad 说的是三键，加第四个会让上述断言的语义漂移。

## 现状审计

### 定时任务列表行渲染（`app.js`）

- **唯一写入点**：`renderBatchConfigRow`（`app.js:13393-13432`）。
  grep 佐证：

  ```
  $ grep -rn "renderBatchConfigRow" src/main/resources/static src/test/js
  src/main/resources/static/app.js:13381:    tbody.innerHTML = configs.map(function(c) { return renderBatchConfigRow(c); }).join("");
  src/main/resources/static/app.js:13393:function renderBatchConfigRow(c) {
  src/test/js/batchSendTaskConsoleInteraction.test.js:1364,1658,...（V9/W9/G13 抽取该函数）
  ```

  即：生产调用点 1 个（`app.js:13381`），测试抽取点 3 个。无其他调用方。
- **违例行**：`app.js:13418`
  `'<td class="batch-task-scope">' + scopeHtml.substring(0, 300) + '</td>' +`
- **`scopeParts` 的组成顺序**（`app.js:13394-13401`）：漏斗 → 标签 → 地区 → 服务商 → 学科 → 状态，
  之后 `app.js:13411` 追加门禁 pill 行（不并入 `scopeParts`）。
- **实测截断阈值**（用真实函数跑出的 `scopeHtml` 长度，门禁 pill 包装另占约 90 字符）：
  1 条 = 151、2 条 = 200、3 条 = 257、**4 条 = 308（截断）**、5 条 = 358。
- **无用分支**：`app.js:13403` `var cls = i === 0 ? "batch-task-scope-line" : "batch-task-scope-line";`
  两个分支相同，本计划重写该段时一并删除。
- Interaction point：`renderBatchConfigRow` 写出的 `<td>` 数量 × `colgroup`（`index.html:1112-1120`）
  的 7 个 `<col>` —— 两者必须始终一致，否则 `table-layout: fixed` 下所有列错位。

### 执行日志抽屉（`index.html` / `styles.css` / `app.js`）

- **DOM**：`index.html:1504-1544`，`<aside id="batchExecutionLogDrawer" class="batch-log-drawer" hidden>`，
  当前是 `.batch-send-task-modal` 的直接子节点（与两个 tab panel 平级）。
- **定位上下文**：`.task-modal.batch-send-task-modal { position: relative; overflow: hidden; }`
  （`styles.css:8422-8432`）。
- **渲染入口**：`renderBatchExecutionDetail`（`app.js:15102-15110`）依次调用
  `renderBatchLiveSection` → `renderOutcomeMetrics` → `renderIntegrityWarning` →
  `renderReasons`×2 → `renderErrorSamples` → `renderBatchTimeline` → `renderLogStatusInfo`。
- **四个 section wrapper 从未被隐藏**，grep 佐证：

  ```
  $ grep -n "batchLogFailureSection\|batchLogSkippedSection\|batchLogErrorSamples\|batchLogTimelineSection" src/main/resources/static/app.js
  （无输出 —— 四个 id 只在 index.html 出现，app.js 从不引用）
  ```

  内部容器 id 分别是 `batchLogFailureReasons` / `batchLogSkippedReasons` /
  `batchLogErrorSampleList` / `batchLogTimeline`，`renderReasons` 等只写这些内部容器的
  `innerHTML` 空态文案。
- **双重转义**：`app.js:15149`
  `if (messageEl) messageEl.textContent = l.message ? escapeHtml(l.message) : "";`
  —— `textContent` 会再转义一次，含 `&`/`<` 的实时消息显示成 `&amp;`。
  同函数内 `roundEl.textContent`（:15137）、`countsEl.textContent`（:15142-15146）用的都是裸值，
  唯独这一行多套了一层。
- Interaction points：
  1. 抽屉定位上下文 × 弹窗关闭按钮 `.batch-send-close-btn`（`index.html:1100`）—— 实测重叠。
  2. 抽屉背景不透明度 × 定时任务表格 —— 实测透出。
  3. section 隐藏 × `clearBatchLogDisplay`（`app.js:15060` 附近）：该函数在「无执行记录」时清空
     内部容器，若 section 被隐藏后不复位，下次选到有数据的记录时会整块不显示。

### 前端样式盘点

- **可复用 class**
  - `.log-detail` / `.log-detail summary` / `.log-detail summary:hover` — `styles.css:3111-3126` —
    折叠块与可点击摘要，primary 色、600 字重、11px。
  - `.batch-task-scope-line` — `styles.css:8531` `{ display: block; }`；
    `styles.css:8532` `.batch-task-scope-line + .batch-task-scope-line { margin-top: 3px; color: var(--text-muted); }`
  - `.batch-gate-pill` / `.is-off` / `.is-na` — `styles.css:9126-9150` — 门禁三态 pill。
  - `.batch-log-metric` / `-label` / `-value` / `.is-success` / `.is-failure` / `.is-skipped` —
    `styles.css:9191-9196`。
- **设计基准 token 实值**
  - 面板不透明底（弹窗内既有约定）：`rgba(255, 255, 255, .96)` + `backdrop-filter: blur(8px)`
    （`styles.css:9175-9177`、`styles.css:8694-8696`）。
  - `--panel-bg`：浅色 `rgba(255, 255, 255, 0.55)`（`styles.css:15`）；
    暗色 `rgba(21, 31, 48, 0.55)`（`styles.css:9304`）。**半透明，不可用作抽屉底。**
  - 分隔线：`1px solid rgba(15, 23, 42, .08)`；抽屉阴影 `-12px 0 32px rgba(15, 23, 42, .12)`。
  - 收件范围行距：`3px`；表格字号：`.data-table { font-size: 11px }`（`styles.css:3169-3173`）。
- **DOM 结构约定**：弹窗 = `header.batch-send-task-header` → `nav.batch-send-tabs` →
  `div.batch-send-tab-panel`×2 → `aside.batch-log-drawer`；panel-head 里放动作按钮的先例见
  `index.html:738`。
- **改动前基线**：`styles.css:8773-8785`（抽屉规则块）、`styles.css:9190`（指标卡）、
  `styles.css:8531-8532`（scope-line）、`app.js:13393-13432`（行渲染）、
  `index.html:1108/1327/1504`（三个块的起始行）—— 逐字内容已在上文 S-1…S-4 与本节引出。

## 实现方案

### 阶段 A：列表行（I-1 / I-2 / S-3）

- **T-A1** 重写 `renderBatchConfigRow` 的收件范围段（`app.js:13402-13418`）。
  删除 `var cls = ...` 无用三元、删除 `scopeHtml.substring(0, 300)`。新逻辑：
  `scopeParts.slice(0, 3)` 渲染常驻行（空数组时渲染 `无限制` 行）→ `scopeParts.slice(3)`
  非空时渲染一个 `<details class="log-detail batch-task-scope-more">`，`<summary>` 文案为
  `展开剩余 N 项`（N = 折叠条数）→ 最后追加门禁 pill 行。
  单元格拼接改为 `'<td class="batch-task-scope">' + scopeHtml + '</td>'`（无截断）。
  遵守 I-1 / I-2 / S-3。
- **T-A2** 在 `styles.css:8532` 之后追加 S-3 的三条新规则。遵守 S-3。

### 阶段 B：抽屉容器与视觉（I-3 / S-1 / S-2 / S-4）

- **T-B1** `index.html`：按 S-1 插入 `<div class="batch-send-task-body">` 包住
  `#batchScheduledPanel`、`#batchManualPanel`、`#batchExecutionLogDrawer` 三者，
  三个块的内部内容一字不动。遵守 S-1。
- **T-B2** `styles.css`：在 `.batch-log-drawer` 规则块之前插入 `.batch-send-task-body`；
  按 S-2 就地改抽屉背景；按 S-4 改指标卡列数。遵守 S-1 / S-2 / S-4 / I-3。

### 阶段 C：抽屉内容空态与转义（I-4）

- **T-C1** `app.js` `renderBatchExecutionDetail`：在调用 `renderReasons`/`renderErrorSamples`
  前后，按数据是否为空设置 `#batchLogFailureSection`、`#batchLogSkippedSection`、
  `#batchLogErrorSamples` 三个 wrapper 的 `hidden`；`#batchLogTimelineSection` 不动。
  同步在 `clearBatchLogDisplay` 中把这三个 wrapper 复位为 `hidden = false`，
  避免下次切到有数据的记录时整块不显示（现状审计 interaction point 3）。遵守 I-4。
- **T-C2** `app.js:15149` 去掉多余的 `escapeHtml`：
  `messageEl.textContent = l.message || "";`（与同函数内其余 `textContent` 写法一致）。

### 阶段 D：缓存键与测试（M-2 / X-2）

- **T-D1** `index.html` 三处缓存键按 S-5 改值。
- **T-D2** `batchSendTaskConsoleVisualFix.test.js:49-51` 三条断言字符串同步改值；
  在该文件追加两条断言：`.batch-log-drawer` 规则块内**不含** `var(--panel-bg)` 且含
  `rgba(255, 255, 255, .96)`；`index.html` 中 `<div class="batch-send-task-body">` 存在
  且 `#batchExecutionLogDrawer` 位于其内。
- **T-D3** 新建 `src/test/js/batchLogDrawerLayout.test.js`，覆盖：
  - `renderBatchConfigRow` 在 6 条筛选条件下输出的 `<td` 计数恰为 7（I-1 的直接回归）；
  - 输出中不含 `substring`、且收件范围单元格内容未被截断（含最后一条筛选文案的完整文本）；
  - 4 条以上时存在唯一一个 `<details class="log-detail batch-task-scope-more">`，
    `<summary>` 文案为 `展开剩余 N 项`，N 与折叠条数一致；
  - 门禁 pill 行在 `</details>` 之后（字符串下标比较）；
  - 3 条及以内时**不产生** `<details>`；0 条时仍输出 `无限制` 且 pill 仍在；
  - `renderBatchExecutionDetail` 在空 reasons/errorSamples 下把三个 wrapper 置 `hidden`，
    而 `#batchLogTimelineSection` 保持可见（I-4）。
- **T-D4** `src/test/js/batchManualExecutionLog.test.js` 同步 T-C2（fast-p 修正 A1：
  原计划要求该文件 `fail 0` 却未授权修改它，与 T-C2/A-8 自相矛盾，经人工批准修正）：
  - 「renderBatchLiveSection escapes message and accountCode」（:331-349）的 message 断言
    改为裸值：`textContent === "正在发送：<b>x</b>"`（与 A-8 一致）；
  - 该用例改名以反映语义（如 `renderBatchLiveSection renders raw message and escapes accountCode`）；
  - accountCode 的 innerHTML 转义断言与 `is-failing` 断言**一字不改**（该路径仍走 innerHTML，必须转义）。

## 变更文件清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/resources/static/index.html` | 插入 `.batch-send-task-body` 包装层；三处缓存键改值 |
| 2 | `src/main/resources/static/styles.css` | 新增 `.batch-send-task-body`；改 `.batch-log-drawer` 背景；改 `.batch-log-metrics` 列数；追加 3 条 `.batch-task-scope-more` 规则 |
| 3 | `src/main/resources/static/app.js` | 重写 `renderBatchConfigRow` 收件范围段；`renderBatchExecutionDetail` / `clearBatchLogDisplay` 空态隐藏；去掉 `:15149` 双重转义 |
| 4 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 缓存键断言改值；新增抽屉不透明与包装层断言 |
| 5 | `src/test/js/batchLogDrawerLayout.test.js` | **新建** |
| 6 | `src/test/js/batchManualExecutionLog.test.js` | T-D4：:331-349 message 断言改裸值、用例改名；accountCode 断言不动 |

文件数 6 ≤ 10；子系统 1（前端静态资源 + 其 JS 测试）≤ 2。

## 验证命令

> 全量回归、构建、`node --check`、`git diff --check` 一律使用主计划
> `batch-console-log-drawer-main.md` 的 `## 共享审计 / X-3`，此处不重写。

本计划涉及/新增测试文件的单跑命令（K-js-test-invocation-surface：`verify.sh` 不覆盖这些文件，
不可用作门禁）：

```bash
# 新建文件
node --test src/test/js/batchLogDrawerLayout.test.js

# 被本计划修改的既有文件
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js

# 直接断言 renderBatchConfigRow 输出，必须一并跑
node --test src/test/js/batchSendTaskConsoleInteraction.test.js

# 抽屉渲染族回归
node --test src/test/js/batchExecutionLogTimeline.test.js
node --test src/test/js/batchManualExecutionLog.test.js
```

通过判据：每条输出 `# fail 0` 且退出码 0。

## 验收标准

- **I-1**：`node --test src/test/js/batchLogDrawerLayout.test.js` 中「6 条筛选条件 → `<td` 计数 7」
  与「无截断」两条断言通过；`grep -n "substring" src/main/resources/static/app.js` 的结果里
  **不再包含** `renderBatchConfigRow` 函数体所在行段（13393-13435）。
- **I-2**：同文件中「pill 在 `</details>` 之后」「0 条时 pill 仍在」两条断言通过；
  `batchSendTaskConsoleInteraction.test.js` 的 V9 / W9 / G13 三条保持绿。
- **I-3**：`batchSendTaskConsoleVisualFix.test.js` 新增断言通过（抽屉规则块不含
  `var(--panel-bg)`、含 `rgba(255, 255, 255, .96)`；`index.html` 存在 `.batch-send-task-body`
  且抽屉在其内）；且该文件 `:54-61` 的既有 `background-color` 断言仍绿。
- **I-4**：`batchLogDrawerLayout.test.js` 中「三块空态 hidden、时间线不 hidden」断言通过。
- **S-1…S-4**：对 `styles.css` 做 `git diff`，逐字比对四段改动与本文契约代码块一致；
  `grep -n "style=" src/main/resources/static/index.html` 在新增的 `.batch-send-task-body`
  行上无 inline style。
- **S-5 / M-2**：`grep -c "20260817-v1-batch-console-row-drawer" src/main/resources/static/index.html`
  输出 `3`；同串在 `batchSendTaskConsoleVisualFix.test.js` 中出现 3 次。
- **回归**：执行主计划 X-3 的全量测试命令与构建命令通过。

## 人工验收清单

### A-1: 多条件任务行七列对齐
- 前置条件：存在一条定时任务，其收件范围至少配置 4 项（例如漏斗=CANDIDATE、地区=非洲、
  服务商=gmail.com、学科=仅理工科、专家状态=未联系）。可在控制台「新增任务」中直接配出。
- 操作步骤：
  1. 打开批量邮件任务控制台 → 定时任务页签。
  2. 观察该行的「模板」「执行计划」「执行时间」「状态」「操作」五列。
- 预期结果：模板列显示「已指定」或「默认」徽章（不是 cron 文案）；执行计划列显示
  「每天 HH:MM」之类的 cron 文案；操作列完整显示「手动 / 编辑 / 日志 / 删除」四个按钮且不被压窄。
  与同表中「无限制」的任务行列位一一对齐。
- 覆盖：I-1、需求描述 1

### A-2: 收件范围 3 行 + 展开
- 前置条件：同 A-1。
- 操作步骤：
  1. 观察该行「收件范围」列。
  2. 点击「展开剩余 N 项」。
- 预期结果：默认只显示前 3 条筛选文案，第 4 行是蓝色可点的「展开剩余 2 项」（N 与实际
  折叠条数一致），最后一行是门禁 pill（「门禁过滤 · 开 / 关 / 模板无门禁字段」三者之一）。
  点击后展开出第 4、5 条筛选文案，pill 仍在最末。
- 覆盖：I-1、I-2、S-3、需求描述 1

### A-3: 抽屉不透明
- 前置条件：任一任务有至少一条执行记录。
- 操作步骤：在定时任务列表点该行「日志」。
- 预期结果：抽屉覆盖区域内**看不到**任何表格文字、开关或按钮的影子；抽屉左缘有一道细分隔线
  与向左的投影。
- 覆盖：I-3、S-2、需求描述 2

### A-4: 抽屉不再压住弹窗关闭按钮（回归）
- 前置条件：同 A-3，抽屉处于打开状态。
- 操作步骤：
  1. 观察抽屉顶端位置。
  2. 点击弹窗右上角的 ×（不是抽屉标题栏里的那个）。
- 预期结果：抽屉从「定时任务 / 手动执行」页签的下沿开始，弹窗标题「批量邮件任务控制台」、
  副标题与两个页签全程可见；点击弹窗 × 后**整个控制台关闭**（而不是只关掉抽屉）。
- 覆盖：I-3、S-1、需求描述 3

### A-5: 指标卡排布
- 前置条件：一条「剩余」大于 0 的执行记录（例如目标 2289 / 成功 10 / 剩余 2279）。
- 操作步骤：打开该执行记录的日志。
- 预期结果：目标、成功、失败排第一行，跳过、耗时、剩余排第二行，两行等宽三列，无孤卡。
- 覆盖：S-4、需求描述 4

### A-6: 空区块隐藏 + 时间线保留（回归）
- 前置条件：一条无失败、无跳过、无错误样例的执行记录。
- 操作步骤：打开该执行记录的日志，从上往下浏览整个抽屉。
- 预期结果：看不到「失败原因」「跳过原因」「错误样例」三个标题及其空行；
  **仍能看到「批次时间线」标题**及其内容（无记录时显示「无执行过程记录」）。
- 覆盖：I-4、需求描述 5

### A-7: 既有筛选文案未变（回归）
- 前置条件：一条只配了服务商多选（如 gmail.com、qq.com）的任务；另一条什么都没配的任务。
- 操作步骤：在定时任务列表观察这两行的收件范围列。
- 预期结果：前者显示 `服务商: gmail.com, qq.com`（逗号加空格分隔），后者显示 `无限制`；
  两者都在最后一行带门禁 pill。
- 覆盖：What must NOT change 第 1 条

### A-8: 实时消息不再多一层转义
- 前置条件：一次运行中的批量发送，且其进度消息里含 `&` 或 `<`（若难以构造，可跳过并记为
  「未覆盖」，由 I-4 之外的代码审查兜底）。
- 操作步骤：执行中打开日志抽屉，观察实时区的消息行。
- 预期结果：显示原始字符 `&` / `<`，而不是 `&amp;` / `&lt;`。
- 覆盖：T-C2
