# 04b · 批量任务控制台前端重排（日限额下线 / 执行轮次 / 地区多选 / 自定义 cron / 执行时间列）

> 主计划：`batch-send-rhythm-and-filter-00-master.md`
> 覆盖需求：第 1 条（UI 部分）、第 2 条（UI）、第 3 条（UI）、第 6 条（UI）、第 7 条
> 依赖：**01、02b、03、04a 全部完成**（本计划消费它们提供的字段与接口）
> 合并理由：五项需求全部落在同一个 `#batchConfigEditor` modal 与同一张任务表格上。拆成多个计划会对同一段 DOM 做 3~4 轮样式契约与 JS 测试改写，合并成一次重排更省且更不易失真。

## 需求描述

### Observable outcome

1. 配置编辑器的「发送控制」区块**移除「日限额（封）」输入框**，**新增「执行轮次（轮）」输入框**；区块下方给出实时提示「单次调度最多发送 = 执行轮次 × 每轮数量 = N 封」。
2. 「收件范围」区块新增「地区」多选控件，可同时选中多个大区；已选项以 chip 形式展示，可点击移除。
3. 「定时调度」区块的「执行频率」下拉新增「自定义 cron」选项；选中后展开 cron 输入框与「测试」按钮，点击后在下方列出**最近 5 次**将要执行的时间；表达式非法时显示红色错误原因。
4. 定时任务列表的「最近执行」列改为「执行时间」列，一列内两行显示：上行「下次 {时间}」、下行「最近 {时间}」；无值时显示 `—`。
5. 手动执行 tab 的日限额输入与差异对比字段一并移除。

### What must NOT change

- 配置编辑器其余字段（任务名称、模板、漏斗层级、标签、邮箱服务商、学科、每轮数量、每封间隔、每轮间隔、自检 TTL）的 id、读写逻辑、秒↔毫秒换算（K-batch-console-time-unit-normalization）与校验文案。
- 「执行频率」下拉的既有三项（每小时 / 每天 / 每周一）生成的 cron 字符串逐字不变：每小时 `0 0 * * * ?`、每天 `0 {min} {hour} * * ?`、每周一 `0 {min} {hour} ? * MON`。
- 任务列表其余 6 列（任务名称、收件范围、模板、执行计划、状态、操作）的表头文案、渲染逻辑与列顺序。
- 手动执行 tab 的来源选择、baseline/draft 分离、diff 红框机制（K-batch-console-source-identity、K-batch-console-source-selection、K-batch-console-diff-tag-normalization）。
- 标签多选控件（`batchConfigEditorTags` / `batchManualTags`）的行为与样式。
- 侧栏 view 注册（K-view-registration-triad）——本计划不新增 view。

### Out of scope

- 后端任何改动（01/02b/03/04a 已完成）
- 专家列表的地区/学科下拉 → 05
- 地区中文标签 → 05（本计划先按后端返回的**英文常量**显示，05 再加中文映射层）
- 手动执行 tab 新增地区多选（本计划只删日限额；地区多选仅加在配置编辑器）

## 关键不变量

### Invariant I-1: 地区控件的 option.value 必须是英文常量
- Rule: 地区多选控件中每个选项的**取值**（进入 payload `regions` 数组的字符串）必须是 `CountryContinentMapping` 的 9 个英文常量原串。本计划的显示文案**也**用英文常量（中文化留给 05），因此本计划中 value 与 label 恰好相同——但代码结构上必须把二者分开（`{ value, label }` 的选项数组），为 05 预留注入点。
- Applies to: `app.js` 新增的地区选项常量数组、`readBatchRegionPickerValue()`、编辑器 payload 构造。
- Violation consequence: 若把 value 与 label 写成同一个字面量而不分离，05 加中文时会直接把中文写进 value，导致 ES 命中 0 条且无报错。
- 来源: 主计划 G-1（K-region-constant-not-display-label）

### Invariant I-2: 自定义 cron 与频率下拉是互斥的单一事实源
- Rule: 保存时提交的 `cron` 字段只有一个来源：频率 ≠ `custom` 时由「频率 + 时间」拼装（既有逻辑逐字不变），频率 = `custom` 时直接取 cron 输入框的值（trim 后）。回显时：若配置的 cron 能被既有的三种模式反解，选中对应频率；否则选中「自定义 cron」并把原串填入输入框。
- Applies to: `app.js` 的 `openBatchConfigEditor()` 回显段（`:13241-13256`）与 `saveBatchConfig()` 的 cron 拼装段（`:13460-13466`）。
- Violation consequence: 两个来源同时参与拼装会产生「界面显示每天 09:00，实际存的是上次的自定义表达式」这类不可见错配。
- 来源: original

### Invariant I-3: cron 测试按钮只调用后端预览接口，前端不自行解析
- Rule: 「测试」按钮必须 `POST /api/mail/batch-send/cron/preview` 并渲染其返回的 `nextFireTimes`。**禁止**在 `app.js` 中内联实现 cron 解析或计算下次时间。
- Applies to: 新增的 `previewBatchCron()` 函数。
- Violation consequence: JS 无现成 cron 库，自写解析必然与服务端 Spring `CronExpression` 的 6 段语义、`?` 处理、时区产生差异，界面预览与实际触发不符（04a 的 I-1 同理）。
- 来源: 04a I-1

### Invariant I-4: 移除日限额必须覆盖全部 5 类站点
- Rule: `dailyCap` 在 `app.js` 中的残留分属五类，必须全部清理：① 配置编辑器读（`:13235`）/写（`:13481`）/校验（`:13494`）；② 手动 tab 读（`:13610`）/写（`:13705`）/校验（`:13942`、`:13957`）/默认值（`:13589`、`:13906`）；③ diff 字段表（`:13763` `{ key: "dailyCap", label: "日限额" }`）与字段 id 映射（`:13807` `dailyCap: "manualFieldDailyCap"`）；④ 来源摘要文案（`:13876` `'日限额: ' + source.dailyCap + ' 封 · 每轮: '`）；⑤ 列表行 draft 构造（`:13571` `dailyCap: c.dailyCap || 1000`）与状态视图（`:1146`、`:5968`）。
- Applies to: `app.js`。
- Violation consequence: 漏掉 ③ 会让 diff 永远把 `dailyCap` 标为「已变更」（因为服务端不再返回该字段，baseline 恒为 undefined）；漏掉 ④ 会在来源摘要里显示「日限额: undefined 封」。
- 来源: original（grep `dailyCap` in app.js 实测 19 处）

### Invariant I-5: 已死的旧 KV 控制台代码与其测试同步退役
- Rule: `app.js` 的 `fillBatchSendConfigForm()`（含 `:5778` `setVal("batchSendDailyCap", ...)`）与 `buildBatchSendConfigPayload()`（含 `:5864`、`:5891`）操作的 DOM id（`#batchSendDailyCap` / `#batchSendRoundSize` / `#batchSendFrequency` / `#batchSendTime` / `#batchSendSelfCheckTtlMin` / `#batchSendDiscipline`）**在 `index.html` 中已不存在**（grep 实测：`index.html` 中 `id="batchSend*"` 仅剩 `batchSendPausedBanner` / `batchSendPausedBannerText` / `batchSendTaskModal` / `batchSendTaskModalTitle`）。`batchSendControls.test.js:308-382` 的 "config form seconds <-> milliseconds conversion" 一节靠 DOM stub 让这段死代码「测试通过」。本计划中该节测试必须**退役或改写为针对 `#batchConfigEditor*` 的等价断言**。
- Applies to: `src/test/js/batchSendControls.test.js`。
- Violation consequence: 保留这些 stub 测试会让「日限额已从 UI 移除」这一事实无法被测试证明，且未来任何人删掉那段死代码时 CI 会红（K-dom-stub-tests-hide-dangling-refs、K-ui-removal-retires-obsolete-contract-tests）。
- **本计划的处理**：只改测试，**不删** `app.js` 中的死函数（删除死代码是独立清理任务，超出本计划范围）；在测试中加一条断言记录该事实。
- 来源: K-dom-stub-tests-hide-dangling-refs + K-ui-removal-retires-obsolete-contract-tests

## 样式契约

> 原则：既有样式引用 `styles.css` 行号复用，新增样式在此逐字给出。执行 agent 只许复制，不许改写。

### S-1：执行轮次输入框（替换日限额）
- **复用**：整块沿用「发送控制」区块既有的 `label.batch-config-field`（`styles.css:8814-8820`）+ `span.batch-config-field-label`（`styles.css:8617-8623`）+ `input.bsc-input`（`styles.css:5246-5256`）三件套，与同区块的「每轮数量」逐字同构。
- **新增 CSS**：无。
- **DOM 结构**（替换 `index.html:1213-1216` 的日限额 label；置于「每轮数量」**之前**，使「执行轮次 × 每轮数量」在视觉上左右相邻）：
  ```html
  <label class="batch-config-field">
      <span class="batch-config-field-label">执行轮次（轮）</span>
      <input type="number" id="batchConfigEditorRoundsPerRun" class="bsc-input" min="1" value="1">
  </label>
  ```
- **禁止项**：inline style；新建 class；修改 `.batch-config-field` / `.bsc-input` 的既有规则块。

### S-2：单次调度发送量提示行
- **复用**：`.batch-config-editor-section-heading span`（`styles.css:8591-8595`）已是该区块的说明文字样式，但本提示需要动态更新且位置在 grid 之下，故新建 class。
- **新增 CSS**（追加到 `styles.css` 的 `.batch-config-editor-grid-controls` 规则块之后，即 `:8602` 所在规则块之后）：
  ```css
  .batch-config-editor-hint {
    margin-top: 10px;
    padding: 8px 12px;
    border-radius: 8px;
    background: rgba(37, 99, 235, .06);
    color: #475569;
    font-size: 12px;
    line-height: 1.6;
  }

  .batch-config-editor-hint strong {
    color: #2563eb;
    font-weight: 600;
  }
  ```
- **DOM 结构**（置于「发送控制」区块的 `div.batch-config-editor-grid-controls` 之后、`</section>` 之前）：
  ```html
  <div class="batch-config-editor-hint" id="batchConfigEditorVolumeHint"></div>
  ```
  JS 填充文案格式：`单次调度最多发送 <strong>N</strong> 封（执行轮次 × 每轮数量）；跨调度总量由发件账号每日限额兜底。`
- **禁止项**：inline style；用 `.batch-config-editor-section-heading span` 冒充。

### S-3：地区多选控件
- **复用**：**完整复用**标签多选组件的 class 家族，`styles.css:8856-8985` 共 14 条规则（`.batch-tag-picker` `:8856`、`-control` `:8861`、`:focus-within` `:8874`、`-chips` `:8879`、`-chip` `:8883`、`-chip button` `:8897`、`-search` `:8907`、`-chevron` `:8918`、`-dropdown` `:8926`、`-option` `:8940`、`-option:hover/.is-selected` `:8954`、`-check` `:8960`、`.is-selected .batch-tag-picker-check` `:8972`、`-empty` `:8977`）。
- **新增 CSS**：无。**禁止**为地区另建一套 `.batch-region-picker-*` 样式。
- **DOM 结构**（置于 `index.html` 「收件范围」区块内，「标签」控件之后、「邮箱服务商」之前；与标签控件逐字同构，仅 id 与 placeholder 不同）：
  ```html
  <div class="batch-config-field">
      <span class="batch-config-field-label">地区</span>
      <div class="batch-tag-picker" data-tag-picker="batchConfigEditorRegions">
          <div class="batch-tag-picker-control">
              <div id="batchConfigEditorRegionsChips" class="batch-tag-picker-chips"></div>
              <input type="search" id="batchConfigEditorRegionsSearch" class="batch-tag-picker-search" placeholder="搜索并选择地区" autocomplete="off" aria-controls="batchConfigEditorRegionsDropdown" aria-expanded="false">
              <span class="batch-tag-picker-chevron" aria-hidden="true">⌄</span>
          </div>
          <input type="hidden" id="batchConfigEditorRegions" value="">
          <div id="batchConfigEditorRegionsDropdown" class="batch-tag-picker-dropdown" role="listbox" aria-multiselectable="true" hidden></div>
      </div>
  </div>
  ```
- **既有 class 的使用点声明**：`.batch-tag-picker*` 家族当前有 2 个使用点（`index.html:1180-1188` 配置编辑器标签、手动 tab 的 `batchManualTags`）。本计划**新增第 3 个使用点，不修改任何既有规则块**（派生新使用点，非就地修改）。
- **禁止项**：新建 `.batch-region-*` class；修改 `.batch-tag-picker*` 任一规则块；inline style。

### S-4：自定义 cron 输入与测试按钮
- **复用**：容器用 `.batch-config-field`（`styles.css:8814`）；输入框用 `.bsc-input`（`:5246`）；下拉用 `.bsc-input.bsc-select`（`:5257`）；测试按钮用 `.button.secondary`（`styles.css:669-678`，hover 见 `:675`）。
- **新增 CSS**（追加在 S-2 的两条规则之后）：
  ```css
  .batch-cron-row {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .batch-cron-row .bsc-input {
    flex: 1 1 auto;
  }

  .batch-cron-row .button {
    flex: 0 0 auto;
  }

  .batch-cron-preview {
    margin-top: 8px;
    padding: 8px 12px;
    border: 1px solid rgba(15, 23, 42, .08);
    border-radius: 8px;
    background: #f8fafc;
    color: #475569;
    font-size: 12px;
    line-height: 1.7;
  }

  .batch-cron-preview[hidden] {
    display: none;
  }

  .batch-cron-preview-title {
    display: block;
    margin-bottom: 4px;
    color: #64748b;
    font-size: 11px;
    font-weight: 600;
  }

  .batch-cron-preview-item {
    display: block;
    font-variant-numeric: tabular-nums;
  }

  .batch-cron-preview.is-error {
    border-color: rgba(220, 38, 38, .25);
    background: rgba(220, 38, 38, .05);
    color: #b91c1c;
  }
  ```
- **DOM 结构**：
  - `index.html:1244-1249` 的频率下拉**新增一个 option**（置于末尾）：
    ```html
    <option value="custom">自定义 cron</option>
    ```
  - 在 `#batchConfigEditorTimeField`（`index.html:1250-1253`）**之后**新增：
    ```html
    <label class="batch-config-field" id="batchConfigEditorCronField" hidden>
        <span class="batch-config-field-label">cron 表达式（6 段：秒 分 时 日 月 周）</span>
        <div class="batch-cron-row">
            <input type="text" id="batchConfigEditorCron" class="bsc-input" placeholder="0 0 9 * * ?" autocomplete="off">
            <button type="button" class="button secondary" id="batchConfigEditorCronTestBtn">测试</button>
        </div>
        <div class="batch-cron-preview" id="batchConfigEditorCronPreview" hidden></div>
    </label>
    ```
- **可见性规则**：频率 = `custom` 时 `#batchConfigEditorCronField.hidden = false` 且 `#batchConfigEditorTimeField.style.display = "none"`；频率 = `hourly` 时两者都隐藏；频率 = `daily` / `weekly` 时只显示时间字段。**沿用**既有的 `timeField.style.display` 写法（`app.js:13254-13255`），cron 字段用 `hidden` 属性。
- **禁止项**：inline style；用 `.button.primary`（测试不是主操作）；自造 loading 遮罩（按钮期间置 `disabled` 即可）。

### S-5：任务列表「执行时间」合并列
- **复用**：`.batch-task-scope-line`（`styles.css:8524-8526`）已定义「块级 + 次行淡色 + 3px 间距」，正是两行单元格需要的形态，**直接复用**，不新建。
- **新增 CSS**：无。
- **DOM 结构**：
  - `index.html:1122` 表头 `<th>最近执行</th>` 改为 `<th>执行时间</th>`
  - `app.js:13126` 的单元格改为：
    ```
    '<td>' +
        '<span class="batch-task-scope-line">下次 ' + <nextFireTime 格式化或 "—"> + '</span>' +
        '<span class="batch-task-scope-line">最近 ' + <lastExecutedAt 格式化或 "—"> + '</span>' +
    '</td>'
    ```
- **既有 class 的使用点声明**：`.batch-task-scope-line` 当前使用点为 `app.js:13114`（收件范围列）。本计划新增第 2 个使用点，**不修改**其规则块。
- **列数不变**：仍为 7 列，`tbody` 空态的 `colspan="7"`（`app.js:13099`）不改。
- **禁止项**：新增第 8 列；改 `.batch-task-table` 的 `min-width: 1080px`（`styles.css:8519`）；inline style。

## 现状审计

### 配置编辑器 DOM（`index.html`）

| 区块 | 行号 | 现有内容 |
|---|---|---|
| 收件范围 | `:1164-1205` | 漏斗层级 select、标签 tag-picker、邮箱服务商 select、学科 select |
| 发送控制 | `:1207-1234` | 日限额 `:1213-1216`、每轮数量 `:1217-1220`、每封间隔 `:1221-1224`、每轮间隔 `:1225-1228`、自检 TTL `:1229-1232` |
| 定时调度 | `:1236-1255` | 执行频率 select `:1242-1249`（3 个 option）、执行时间 `#batchConfigEditorTimeField` `:1250-1253` |
| 操作 | `:1257-1260` | 取消 / 保存任务 |

### 任务列表（`index.html:1114-1127` + `app.js:13092-13158`）

表头 7 列：任务名称 / 收件范围 / 模板 / 执行计划 / **最近执行** / 状态 / 操作。

> ⚠ **已存在的语义错配**：表头写「最近执行」，但 `renderBatchConfigRow()`（`app.js:13126`）渲染的是 `c.updatedAt`（配置**更新**时间），不是执行时间。本计划顺带修正这一错配——`updatedAt` 不再出现在该列。

`cronToDisplayText()`（`app.js:13146-13158`）负责「执行计划」列文案，处理 `每小时` / `周X hh:mm` / `每天 hh:mm` 三种；对自定义 cron 会落到 `每天 hh:mm` 的错误分支。本计划需为 `custom` 增加兜底：无法归入三种模式时**原样显示 cron 字符串**。

### `dailyCap` 在 `app.js` 的 19 处（grep 实测，按 I-4 的五类归并）

| 类 | 行号 | 内容 |
|---|---|---|
| ① 配置编辑器 | `:13235` 读、`:13481` 写、`:13494` 校验 | `setVal("batchConfigEditorDailyCap", ...)` / `dailyCap: Number(val(...)) \|\| 1000` / `if (payload.dailyCap < payload.roundSize)` |
| ② 手动 tab | `:13589` 默认、`:13610` 读、`:13705` 写、`:13906` 默认、`:13942` 校验、`:13957` 校验 | — |
| ③ diff 字段表 | `:13763`、`:13807` | `{ key: "dailyCap", label: "日限额" }` / `dailyCap: "manualFieldDailyCap"` |
| ④ 来源摘要 | `:13876` | `'日限额: ' + source.dailyCap + ' 封 · 每轮: '` |
| ⑤ 列表/状态 | `:13571`、`:1146`、`:5968`、`:13720` | draft 构造 / 状态视图 |
| ⑥ **已死的旧 KV 面板** | `:5778`、`:5864`、`:5891` | 操作 `index.html` 中已不存在的 `#batchSendDailyCap`（I-5） |

### JS 测试现状（grep 实测）

| 文件 | 行数 | 与本计划相关的断言 |
|---|---|---|
| `batchSendControls.test.js` | 566 | `:100` `batchSendDailyCap: "value"` stub、`:220` `dailyCap: 1000` 状态、`:308-382` "config form seconds <-> ms" 整节（测死代码，I-5）、`:319/329/337/343/358/373/395/448/483/539` |
| `expertTagBatchFix.test.js` | 687 | `:471-499` 四个 `batchManualDailyCap` 用例 |
| `batchSendTaskConsoleInteraction.test.js` | 276 | `:179` `currentValues = { funnelLevel, tags, dailyCap: 60 }`（diff 用例） |
| `batchManualExecutionLog.test.js` | 482 | `:74`、`:108` `dailyCap: 5` 快照 fixture |
| `batchSendTaskConsoleVisualFix.test.js` | 115 | 无 dailyCap；但涉及编辑器 DOM，改 DOM 后须复核 |

### 交互点

| # | 写路径 | 读路径 | 处理 |
|---|---|---|---|
| X-1 | 编辑器 payload 的 `regions` 数组 | 03 的 `BatchSendTaskConfigService.normalizeRegions()` | I-1：value 必须英文常量，否则 422 |
| X-2 | 编辑器 payload 的 `cron`（自定义） | 03/04a 的 `normalizeAndValidate()` 的 `CronExpression.parse` | 保存前应先用「测试」按钮验证；保存失败时后端返回 422，前端须展示其 message |
| X-3 | `GET /configs` 的 `nextFireTime` / `lastExecutedAt`（04a 提供） | 列表「执行时间」列 | 二者均可为 null，渲染须有 `—` 兜底 |
| X-4 | 频率下拉的 `custom` 选项 | `cronToDisplayText()`（执行计划列） | 需加原样显示兜底，否则自定义 cron 在列表里显示成错误的「每天 hh:mm」 |
| X-5 | 手动 tab 的 diff 字段表 | 服务端不再返回 `dailyCap` | I-4 ③：不删则 diff 恒标红 |

## 实现方案

### A-1 `index.html`（S-1、S-2、S-3、S-4、S-5）

1. 删除日限额 label（`:1213-1216`），在「每轮数量」**之前**插入 S-1 的执行轮次 label
2. 在「发送控制」区块的 grid 之后插入 S-2 的提示行 div
3. 在「收件范围」区块的标签控件之后插入 S-3 的地区 tag-picker
4. 频率下拉（`:1244-1248`）末尾加 S-4 的 `custom` option；`#batchConfigEditorTimeField` 之后插入 S-4 的 cron 字段块
5. 表头 `:1122` `<th>最近执行</th>` → `<th>执行时间</th>`

### A-2 `styles.css`（S-2、S-4）

在 `.batch-config-editor-grid-controls` 规则块（`:8602` 所在块）之后，逐字追加 S-2 的 2 条与 S-4 的 7 条规则，共 9 条新规则。**不修改任何既有规则块。**

### A-3 `app.js` —— 编辑器读写（I-1、I-2、I-4）

**回显 `openBatchConfigEditor()`（`:13230-13260`）**
- 删除 `:13235` 日限额 setVal
- 新增 `setVal("batchConfigEditorRoundsPerRun", config ? config.roundsPerRun : "1")`
- 新增 `setBatchRegionPickerValue("batchConfigEditorRegions", config && Array.isArray(config.regions) ? config.regions : [])`
- cron 回显改为（I-2）：先按既有三模式反解；三种都不匹配时 `freq = "custom"` 并 `setVal("batchConfigEditorCron", config.cron)`
- 可见性同步：新增 `syncBatchConfigEditorScheduleFields()`，按 freq 控制 `#batchConfigEditorTimeField.style.display` 与 `#batchConfigEditorCronField.hidden`
- 新增 `updateBatchConfigVolumeHint()` 调用（S-2）

**保存 `saveBatchConfig()`（`:13460-13512`）**
- cron 拼装（`:13460-13466`）加 `custom` 分支：`if (freq === "custom") cron = (val("batchConfigEditorCron") || "").trim();` 且为空时 `showStatus("请填写 cron 表达式", "error"); return;`（I-2）
- payload 删 `dailyCap`（`:13481`），加 `roundsPerRun: Number(val("batchConfigEditorRoundsPerRun")) || 1`
- payload 加 `regions: readBatchRegionPickerValue("batchConfigEditorRegions")`
- 校验删 `:13494` 的 `dailyCap < roundSize`，加 `if (payload.roundsPerRun < 1) { showStatus("执行轮次需 ≥ 1", "error"); return; }`

**新增地区选择器函数族**（I-1）
- 常量 `var BATCH_REGION_OPTIONS = [{ value: "China", label: "China" }, { value: "Asia (Japan & Korea)", label: "Asia (Japan & Korea)" }, { value: "Asia (Other)", label: "Asia (Other)" }, { value: "Europe", label: "Europe" }, { value: "North America", label: "North America" }, { value: "South America", label: "South America" }, { value: "Africa", label: "Africa" }, { value: "Oceania", label: "Oceania" }, { value: "Other", label: "Other" }];`
  > **顺序必须与 `CountryContinentMapping.REGION_ORDER`（`:16-26`）逐项一致**；`label` 本计划等于 `value`，05 只改 `label` 一列。
- `readBatchRegionPickerValue` / `setBatchRegionPickerValue` / `renderBatchRegionPicker` / `toggleBatchRegionPickerValue` / `openBatchRegionPicker` / `closeBatchRegionPicker` / `bindBatchRegionPicker` —— **逐个对照 `app.js:13341-13437` 的标签选择器同名函数实现**，差异仅两点：数据源是 `BATCH_REGION_OPTIONS` 常量（非异步加载的标签列表），渲染 chip / option 文案时用 `opt.label` 而非 `batchTagDisplayName(tag)`
- 在 `bindBatchTagPicker("batchConfigEditorTags")` 所在的初始化处（`:14438`）追加 `bindBatchRegionPicker("batchConfigEditorRegions")`

**新增 cron 预览**（I-3）
```
async function previewBatchCron() {
    var input = document.getElementById("batchConfigEditorCron");
    var box = document.getElementById("batchConfigEditorCronPreview");
    var btn = document.getElementById("batchConfigEditorCronTestBtn");
    if (!input || !box) return;
    btn.disabled = true;
    try {
        var res = await api("/api/mail/batch-send/cron/preview", {
            method: "POST", body: JSON.stringify({ cron: input.value, count: 5 })
        });
        box.hidden = false;
        if (res.valid) {
            box.classList.remove("is-error");
            box.innerHTML = '<span class="batch-cron-preview-title">最近 5 次执行时间</span>' +
                res.nextFireTimes.map(function(t) {
                    return '<span class="batch-cron-preview-item">' + escapeHtml(formatDateTime(t)) + '</span>';
                }).join("");
        } else {
            box.classList.add("is-error");
            box.textContent = res.message || "cron 表达式不合法";
        }
    } finally {
        btn.disabled = false;
    }
}
```
绑定到 `#batchConfigEditorCronTestBtn` 的 click，与 `bindBatchRegionPicker` 同处初始化。

**新增发送量提示**（S-2）
```
function updateBatchConfigVolumeHint() {
    var hint = document.getElementById("batchConfigEditorVolumeHint");
    if (!hint) return;
    var rounds = Number(val("batchConfigEditorRoundsPerRun")) || 0;
    var size = Number(val("batchConfigEditorRoundSize")) || 0;
    hint.innerHTML = "单次调度最多发送 <strong>" + (rounds * size) +
        "</strong> 封（执行轮次 × 每轮数量）；跨调度总量由发件账号每日限额兜底。";
}
```
绑定 `#batchConfigEditorRoundsPerRun` 与 `#batchConfigEditorRoundSize` 的 `input` 事件。

### A-4 `app.js` —— 手动 tab 与 diff（I-4 ②③④⑤）

按 I-4 的分类表逐点删除 `:13571`、`:13589`、`:13610`、`:13705`、`:13720`、`:13763`、`:13807`、`:13876`、`:13906`、`:13942`、`:13957`、`:1146`、`:5968` 的 `dailyCap` 相关代码。
`:13876` 的来源摘要文案改为 `'轮次: ' + source.roundsPerRun + ' 轮 · 每轮: ' + source.roundSize + ' 封<br>'`。
diff 字段表（`:13762-13763` 附近）把 `{ key: "dailyCap", label: "日限额" }` 替换为 `{ key: "roundsPerRun", label: "执行轮次" }`，字段 id 映射（`:13807`）同步替换。

> `index.html` 中手动 tab 的 `#batchManualDailyCap` 输入框及其 `manualFieldDailyCap` 容器一并删除；执行前 grep `batchManualDailyCap` 与 `manualFieldDailyCap` 复核 HTML 侧全集。

### A-5 `app.js` —— 列表渲染（S-5、X-3、X-4）

- `renderBatchConfigRow()`（`:13105-13135`）：`:13126` 单元格按 S-5 改写；新增 `formatDateTime` 的 null 兜底（返回 `—`）
- 收件范围摘要（`:13106-13110`）新增地区一行：`if (Array.isArray(c.regions) && c.regions.length > 0) scopeParts.push("地区: " + escapeHtml(c.regions.join(", ")));`
- `cronToDisplayText()`（`:13146-13158`）：三种模式都不匹配时返回 `escapeHtml(cron)` 原串（X-4）

### A-6 测试

**`batchSendTaskConsoleInteraction.test.js`** — 改写 + 新增：
- `:179` 的 `currentValues` 用 `roundsPerRun` 替换 `dailyCap`
- 新增：地区选择器 `toggle` 两次后 `readBatchRegionPickerValue` 返回两项，且**顺序与 `BATCH_REGION_OPTIONS` 一致**
- 新增：`BATCH_REGION_OPTIONS` 的 9 个 `value` 逐字等于 9 个英文常量（I-1，**这条直接锁住主计划 G-1**）
- 新增：freq = `custom` 时 `saveBatchConfig` 的 payload `cron` 取自输入框；freq = `daily` 时取自频率+时间拼装（I-2）
- 新增：cron 回显——传入 `"0 15 3 * * ?"` 选中 `daily`；传入 `"0 0 9 ? * MON#2"`（三模式均不匹配）选中 `custom` 且输入框为原串（I-2）
- 新增：`updateBatchConfigVolumeHint()` 在 rounds=2、size=20 时输出含 `40`（S-2）
- 新增：`cronToDisplayText("0 0 9 ? * MON#2")` 返回原串（X-4）

**`batchSendControls.test.js`** — 退役 + 记录（I-5）：
- 删除 `:308-382` "config form seconds <-> milliseconds conversion" 整节（其被测函数 `buildBatchSendConfigPayload` 操作的 DOM 已不存在于 `index.html`）
- 新增一条断言：`index.html` 中不存在 `id="batchSendDailyCap"` 与 `id="batchConfigEditorDailyCap"`（把「UI 已移除」变成可测事实）
- 其余节（按钮状态机、mode/status badge、banner）保持不动

**`expertTagBatchFix.test.js`** — 改写：
- `:471-499` 四个 `batchManualDailyCap` 用例改为针对 `batchManualRoundsPerRun`（若手动 tab 保留轮次字段）或整体删除（若手动 tab 不含该字段）；执行时按 A-4 的实际 DOM 决定，并在提交信息中说明选择

**`batchManualExecutionLog.test.js`** — 改写：
- `:74`、`:108` 的快照 fixture 用 `roundsPerRun: 1` 替换 `dailyCap: 5`

## 变更文件清单

| # | 文件 | 类型 | 改动摘要 |
|---|---|---|---|
| 1 | `src/main/resources/static/index.html` | 修改 | S-1~S-5 的 DOM：删日限额、加执行轮次、加提示行、加地区 picker、加 custom option 与 cron 字段、表头改「执行时间」、删手动 tab 日限额 |
| 2 | `src/main/resources/static/styles.css` | 修改 | 追加 S-2 的 2 条 + S-4 的 7 条新规则；既有规则块零改动 |
| 3 | `src/main/resources/static/app.js` | 修改 | 编辑器读写、地区选择器函数族、cron 预览、发送量提示、手动 tab 与 diff 清理、列表渲染 |
| 4 | `src/test/js/batchSendTaskConsoleInteraction.test.js` | 修改 | 改写 1 处 + 新增 7 个用例 |
| 5 | `src/test/js/batchSendControls.test.js` | 修改 | 删除死代码测试节 + 新增 UI 移除断言 |
| 6 | `src/test/js/expertTagBatchFix.test.js` | 修改 | 改写/删除 4 个 dailyCap 用例 |
| 7 | `src/test/js/batchManualExecutionLog.test.js` | 修改 | fixture 字段替换 |

**文件数 7 ≤ 10 ✅　独立子系统 1（前端控制台）≤ 2 ✅　新增字段 0 ✅**

> **不得**修改：任何 `.kt` 文件、任何迁移文件、`src/test/js/batchSendTaskConsoleVisualFix.test.js`（若其因 DOM 变更失败，说明 S-1~S-5 的 DOM 契约执行有误，应改实现而非改该测试）。

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。
> 前端 JS 用例的权威门禁是对目标文件的 `node --test` 单跑；`verify.sh` **只跑 `normalizeDiscoveryResultSummary.test.js` 一个文件，不可用作本计划的门禁**（K-js-test-invocation-surface）。

```bash
# 本计划的前端权威门禁（4 个目标测试文件）
node --test \
  src/test/js/batchSendTaskConsoleInteraction.test.js \
  src/test/js/batchSendControls.test.js \
  src/test/js/expertTagBatchFix.test.js \
  src/test/js/batchManualExecutionLog.test.js

# 未被本计划修改但会受 DOM 变更影响的相邻用例（必须一并绿）
node --test \
  src/test/js/batchSendTaskConsoleVisualFix.test.js \
  src/test/js/batchExecutionLogTimeline.test.js

# 全部前端用例
node --test src/test/js/*.test.js

# 语法检查（pom.xml:216/231 绑定的同一命令）
node --check src/main/resources/static/app.js

# 全量回归（其 JS 覆盖由 exec-maven-plugin 绑定在 test phase，pom.xml:188-203）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

**通过判据**：`node --test` 输出 `# fail 0`；`node --check` 无输出且退出码 0；`mvn test` 输出 `Tests run: N, Failures: 0, Errors: 0`；`git diff --check` 无输出。
**来源**：`CLAUDE.md` 的「Commands」章节 + `pom.xml:188-231` + K-js-test-invocation-surface（实测记录）。

## 验收标准

- **I-1**：`batchSendTaskConsoleInteraction.test.js` 的 `BATCH_REGION_OPTIONS` 逐字断言用例通过；grep `app.js` 确认地区选项为 `{ value, label }` 对象数组而非裸字符串数组。
- **I-2**：cron 拼装与回显的 4 个用例通过；grep `saveBatchConfig` 确认 `cron` 变量只有一条赋值链，`custom` 分支与三模式分支互斥。
- **I-3**：grep `app.js` 中新增代码无 cron 解析实现（无 `split(/\s+/)` 用于计算时间的新代码）；`previewBatchCron` 中出现 `/api/mail/batch-send/cron/preview`。
- **I-4**：grep `dailyCap` 在 `app.js` 中仅剩 I-5 所述的旧 KV 死代码 3 处（`:5778` / `:5864` / `:5891` 附近）；grep `dailyCap` 在 `index.html` 结果为空。
- **I-5**：`batchSendControls.test.js` 中不再存在 "config form seconds <-> milliseconds conversion" 节；新增的「UI 已移除」断言通过。
- **S-1**：grep `index.html` 存在 `id="batchConfigEditorRoundsPerRun"` 且其 class 为 `bsc-input`，父级为 `label.batch-config-field`；`git diff -- styles.css` 中无与 S-1 相关的新增。
- **S-2**：`git diff -- styles.css` 中 `.batch-config-editor-hint` 与 `.batch-config-editor-hint strong` 两条规则与本契约**逐字一致**（属性顺序、值、空格）。
- **S-3**：grep `styles.css` 无 `batch-region-picker`；`git diff -- styles.css` 中 `.batch-tag-picker*` 家族 14 条规则**零改动**；`index.html` 中 `data-tag-picker="batchConfigEditorRegions"` 存在。
- **S-4**：`git diff -- styles.css` 中 7 条新规则与本契约逐字一致；`index.html` 中测试按钮 class 为 `button secondary`（非 `primary`）。
- **S-5**：`git diff -- styles.css` 中 `.batch-task-scope-line` 规则块零改动；`app.js:13099` 的 `colspan="7"` 未变；`index.html` 表头仍为 7 个 `<th>`。
- **无 inline style**：`git diff -- src/main/resources/static/index.html | grep '^+' | grep 'style='` 只允许命中 S-4 描述的 `#batchConfigEditorTimeField.style.display` 相关的 JS 操作（HTML 中不得新增 `style=` 属性）。
- **回归**：执行「验证命令」节的全部命令通过。

## 人工验收清单

### A-1：编辑器不再有日限额，改为执行轮次并显示总量
- 前置条件：控制台可访问，至少一条定时任务配置。
- 操作步骤：
  1. 打开「批量邮件任务控制台」→ 定时任务 → 编辑任一任务
  2. 查看「发送控制」区块
  3. 把「执行轮次」设为 `2`、「每轮数量」设为 `20`
  4. 保存后重新打开
- 预期结果：区块中**没有**「日限额（封）」输入框；有「执行轮次（轮）」输入框且位于「每轮数量」左侧；第 3 步时区块下方提示实时变为「单次调度最多发送 **40** 封（执行轮次 × 每轮数量）；跨调度总量由发件账号每日限额兜底。」；第 4 步回显轮次为 2、每轮为 20。
- 覆盖：Observable outcome 1、5；I-4；S-1、S-2

### A-2：地区多选可用且样式与标签一致
- 前置条件：同上。
- 操作步骤：
  1. 在「收件范围」区块点击「地区」搜索框
  2. 依次点选 `Europe` 与 `China`
  3. 输入 `asia` 过滤，观察候选项
  4. 点击 `Europe` chip 上的移除按钮
  5. 保存后重新打开
- 预期结果：下拉展开列出 **9** 项，顺序为 China / Asia (Japan & Korea) / Asia (Other) / Europe / North America / South America / Africa / Oceania / Other；选中项显示 chip 且带 ✓；第 3 步只剩 2 项 Asia；第 4 步 Europe chip 消失；第 5 步回显仅剩 `China`。**控件的边框、圆角、chip 外观、聚焦蓝色描边与上方「标签」控件肉眼完全一致。**
- 覆盖：Observable outcome 2；S-3

### A-3：自定义 cron 与测试按钮
- 前置条件：同上。
- 操作步骤：
  1. 在「定时调度」区块把「执行频率」切到「自定义 cron」
  2. 观察「执行时间」字段与 cron 字段的显示状态
  3. 输入 `0 0 9 * * ?`，点击「测试」
  4. 改为 `每天九点`，再点「测试」
  5. 清空输入框直接点「保存任务」
  6. 输入 `0 30 8 ? * MON`，保存后回到列表
- 预期结果：
  - 第 2 步：「执行时间」隐藏，cron 输入框与「测试」按钮出现
  - 第 3 步：下方灰底框列出 **5 行**时间，均为 09:00:00，日期递增；标题为「最近 5 次执行时间」
  - 第 4 步：框变为**红底红字**，显示「不是合法的 Spring cron 表达式（6 段，秒 分 时 日 月 周）…」
  - 第 5 步：提示「请填写 cron 表达式」，不发起保存请求
  - 第 6 步：保存成功；列表「执行计划」列显示 `周一 08:30`
- 覆盖：Observable outcome 3；I-2、I-3；S-4

### A-4：执行时间合并列
- 前置条件：三条配置——① `auto_enabled = 1` 且已执行过；② `auto_enabled = 0` 且已执行过；③ 从未执行过。
- 操作步骤：打开定时任务列表，查看第 5 列。
- 预期结果：表头为「**执行时间**」（不是「最近执行」）；
  - 配置①：两行「下次 {未来时间}」「最近 {过去时间}」
  - 配置②：「下次 —」「最近 {过去时间}」
  - 配置③：「最近 —」
  - 两行的字号与颜色层次与「收件范围」列的多行摘要一致（次行更淡）；表格仍为 7 列，无横向溢出加剧。
- 覆盖：Observable outcome 4；S-5；交互点 X-3

### A-5：非法自定义 cron 保存时后端拒绝并展示原因
- 前置条件：同 A-3。
- 操作步骤：频率选「自定义 cron」，**跳过测试按钮**直接输入 `0 0 9 * *`（5 段）并点「保存任务」。
- 预期结果：保存失败，页面显示后端返回的错误消息（含 `cron is not a valid Spring cron expression`）；编辑器保持打开，已填内容不丢失。
- 覆盖：交互点 X-2

### A-6【回归】其余字段与频率下拉三项行为不变
- 前置条件：一条配置。
- 操作步骤：
  1. 频率依次选「每小时」「每天 09:00」「每周一 09:00」，每次保存后查 `SELECT cron FROM batch_send_task_config WHERE id = <id>;`
  2. 把「每封间隔」设为 3 秒、「每轮间隔」设为 120 秒并保存，查 `SELECT per_mail_interval_ms, per_round_interval_ms FROM ...`
  3. 重新打开编辑器
- 预期结果：第 1 步三次分别为 `0 0 * * * ?`、`0 0 9 * * ?`、`0 0 9 ? * MON`；第 2 步为 `3000` 与 `120000`（秒→毫秒换算未坏，K-batch-console-time-unit-normalization）；第 3 步回显为 3 与 120。
- 覆盖：must-NOT-change 第 1、2 条

### A-7【回归】手动执行 tab 的来源选择与 diff 机制不坏
- 前置条件：至少两条配置，参数不同。
- 操作步骤：
  1. 切到「手动执行」tab，在来源下拉中搜索并选择配置 A
  2. 修改「每轮数量」为一个不同的值
  3. 观察该字段是否标红
  4. 在来源下拉中改选配置 B，观察是否弹确认
  5. 确认后执行一次
- 预期结果：第 3 步「每轮数量」字段标红且顶部显示差异提示；**不出现「日限额」相关的红框或差异项**；第 4 步弹出「放弃当前修改」确认；第 5 步执行日志归属到配置 B（点该配置的「日志」能看到）。
- 覆盖：must-NOT-change 第 5 条；I-4 ③④；交互点 X-5

### A-8【回归】标签多选控件未受影响
- 前置条件：同 A-2。
- 操作步骤：在同一个编辑器中操作「标签」控件——展开、搜索、选中两个、移除一个、保存后回显。
- 预期结果：行为与样式与本计划上线前完全一致；地区控件的引入未改变标签控件的任何表现。
- 覆盖：must-NOT-change 第 4 条；S-3 的「派生新使用点，非就地修改」

## 修正记录

（暂无）

---

## 全局约束（主计划 00 共享，本批所有子计划必须复述并各自验证）

### G-1 地区常量是领域值，不可中文化
`CountryContinentMapping` 的 9 个大区英文串（`China` / `Asia (Japan & Korea)` / `Asia (Other)` / `Europe` / `North America` / `South America` / `Africa` / `Oceania` / `Other`）是领域常量，参与 ES term 查询构造（`countriesForRegion` → `esTermVariants`）。需求 4 的「改为中文」只能作用于显示标签；API 传值、DB 存值、ES 查询值必须保持英文原串。

### G-2 服务端始终存在至少一道单次调度发送量硬闸门
从 01 提交开始到 02 提交完成，`ManualInitialOutreachService` 的轮次循环必须始终受一个服务端配置字段约束（先是 `dailyCap`，01 后新增 `roundsPerRun`，02 后仅剩 `roundsPerRun` + 账号容量）。

### G-3 UNCLASSIFIED 学科的过滤实现必须同源
`ExpertSearchService.disciplineFilter()` 已正确实现 `UNCLASSIFIED` = `must_not exists disciplineCategory`，且 `ALLOWED_DISCIPLINES` 已含该值。已知缺陷点：#1 `ManualInitialOutreachService.buildEsFiltersForLevel()` else 分支（:1219）直接写 `term disciplineCategory = it`（活跃旁路）；#2 `RecipientScope.matchesExpert()`（BatchExecutionModels.kt:54）直接写 `profile.disciplineCategory != discipline`（活跃缺陷）；#3 `BatchSendTaskConfigService.ALLOWED_DISCIPLINES`（:473）= `setOf("STEM","HUMANITIES")`（白名单缺项）；#4 `BatchSendSettingService.ALLOWED_DISCIPLINES`（:236）有意不改；#5 `buildMaterialReminderEsFilters()`（:1088）是死代码；#6 前端 `index.html:1199-1201`、`:1336-1338` 缺 option。

### G-4 运行中只消费启动快照
任何新增配置字段（`roundsPerRun`、`regions`）都必须经 `BatchExecutionSnapshot` 传入执行循环，禁止在循环内重新读 `batch_send_task_config`。

### G-5 调度重排的触发条件是 cron ∪ autoEnabled
`BatchSendScheduler.reload()` 目前仅在 `scheduledCrons[configId] != cron` 时重排；04 引入自定义 cron 后必须确认「沿用原 cron、仅把 autoEnabled 由 false 改 true」的场景仍会重排。

### 全批约束
- 迁移文件禁止包含 `${...}`（生产 application.yml 未关 Flyway placeholder-replacement）。
- 新建迁移前必须先跑 `ls src/main/resources/db/migration/ | sort -V | tail -3` 与 `grep -rn "V9[0-9]__" docs/plans/` 确认版本号未被占用；本批计划编号 V91/V92/V93，若实际落地顺序不同则按实际重编号并同步本计划与主计划引用。已应用的迁移一律不得编辑。
- `BatchSendTaskConfig` 等 data class 的新增字段必须带默认值（全仓 11 个构造点，10 个在测试里）。
- 不在本批范围：账号侧 `dailySendLimit` / warmup ramp 语义与配置入口、`AccountRateLimiter` 动态间隔算法、`oneRoundOnly` 手动单轮语义、`batch_send_setting` KV 兼容表迁移、跨执行自然日发送量统计替代品（`TaskExecutionService.sumSuccessCountTodayByBatchConfigId()` 保留方法与其测试）。

## 执行契约（fast-p 实施者）
- 使用 execute-p 技能；本 brief 是完整批准的契约。
- 只修改「变更文件清单」列出的授权文件；不引入新文件（除计划明示的迁移/测试文件）。
- 保留全部关键不变量与下游接口；data class 新增字段带默认值。
- 运行「验证命令」中全部命令；记录命令与退出码。
- 禁止修改 docs/plans/fast/ 下的任何 fast-p 工件；实现提交不得包含它们。
- 实现提交信息：`feat(fast-p): implement 04b`；只提交授权文件。

