# P-0：手动执行面板补齐 regions / roundsPerRun

优先级 **P0（止血）** ｜ 前置：无 ｜ 子系统：1（前端） ｜ 文件数：2

## 需求描述

**Observable outcome**

1. 批量发送「手动执行」面板出现"地区"选择器与"执行轮次"输入框，与「定时任务配置」编辑器一致。
2. 从定时任务带入配置手动执行时，该配置的地区限制与执行轮次被如实带入并生效。
3. 修改这两个字段时，差异高亮（红框）与既有字段行为一致。

**What must NOT change**

- 既有 6 个字段（模板 / 漏斗层级 / 标签 / 邮箱服务商 / 学科 / 各类间隔）的行为与布局。
- `batchTaskState.manualSource` 的 id / updatedAt 保留逻辑。
- `styles.css` 零改动。
- 后端零改动。

**Out of scope**：后端 snapshot 结构、任何过滤语义变更。

## 关键不变量

### I-1：手动执行 snapshot 必须字段完备
- Rule：`confirmManualExecution()` 构造的 snapshot 必须覆盖 `BatchExecutionSnapshot`
  的**全部**过滤与节奏字段，缺失字段会静默退化为后端默认值。
- Applies to：`app.js` 的 `readManualFormValues` / `normalizeManualSnapshot` /
  `confirmManualExecution` / `deepCloneConfig` / `fillManualFormDefaults` /
  `fillManualFormFromDraft` / `computeManualDiffs`。
- Violation consequence：`BatchExecutionSnapshot.regions` 默认 `emptyList()`
  （`BatchExecutionModels.kt:17`）→ `RecipientScope.regions` 为空 → `regionsFilter` 返回 null
  → **地区过滤完全不生效，静默发给全球**。`roundsPerRun` 同理退回 1。
- 来源：original

### I-2：来源配置深拷贝必须字段完备
- Rule：`deepCloneConfig(c)` 必须复制新增的两个字段。
- Violation consequence：从列表带入配置时地区值丢失，重演
  `K-batch-console-source-identity`(P1) 记录过的事故形态。
- 来源：K-batch-console-source-identity

## 样式契约

### S-1：地区选择器
- **复用**：与 `batchConfigEditorRegions` 完全同构的 `.batch-tag-picker` 组件
  （`index.html:1192-1200` 的 DOM 骨架）。禁止自造近似控件。
- **DOM 骨架**（照抄，仅把 id 前缀 `batchConfigEditor` 换成 `batchManual`）：

```html
<div class="batch-tag-picker" data-tag-picker="batchManualRegions">
  <div class="batch-tag-picker-control">
    <div id="batchManualRegionsChips" class="batch-tag-picker-chips"></div>
    <input type="search" id="batchManualRegionsSearch" class="batch-tag-picker-search"
           placeholder="搜索并选择地区" autocomplete="off"
           aria-controls="batchManualRegionsDropdown" aria-expanded="false">
  </div>
  <input type="hidden" id="batchManualRegions" value="">
  <div id="batchManualRegionsDropdown" class="batch-tag-picker-dropdown"
       role="listbox" aria-multiselectable="true" hidden></div>
</div>
```

- **禁止项**：inline style；新增任何 class；修改既有 class 规则块。

### S-2：执行轮次输入框
- **复用**：`.batch-config-field`（`styles.css:8877-8883`，逐字：
  `position:relative; padding:12px; border:1px solid rgba(15,23,42,.08); border-radius:10px; background:#fff;`）
  \+ `.batch-config-field-label`（`styles.css:8680-8686`）+ `.bsc-input`。
- **DOM 骨架**（对齐 `index.html:1228` 的 `batchConfigEditorRoundsPerRun`）：

```html
<input type="number" id="batchManualRoundsPerRun" class="bsc-input" min="1" value="1">
```

- **差异高亮**：`.batch-config-field.is-config-diff`（`styles.css:8885-8888`）已存在，
  新字段接入 `computeAndRenderDiffs` 的 `fieldMap` 后自动继承，**无需新增 CSS**。

## 现状审计

### 控件 id 全集对照（grep `id="batchManual[A-Za-z]*"` vs `id="batchConfigEditor[A-Za-z]*"`）

`batchConfigEditor*` 有而 `batchManual*` **完全没有**的 5 个 id：

    batchConfigEditorRegions / RegionsChips / RegionsDropdown / RegionsSearch
    batchConfigEditorRoundsPerRun

### 受影响的 7 个 JS 函数

| 函数 | 位置 | 缺什么 |
|---|---|---|
| `readManualFormValues` | `app.js:13860` | 不读 regions、不读 roundsPerRun |
| `normalizeManualSnapshot` | `app.js:13888` | 无这两个 key |
| `confirmManualExecution` | `app.js:14078-14089` | snapshot 对象无这两个 key |
| `deepCloneConfig` | `app.js:13744` | 不复制这两个字段 |
| `fillManualFormDefaults` | `app.js:13758` | 无默认值 |
| `fillManualFormFromDraft` | `app.js:13777` | 不回填 |
| `computeManualDiffs` | `app.js:13926` | fieldDefs 已有 `roundsPerRun` 标签但读不到值；无 regions |

> 注：`computeManualDiffs` 的 `fieldDefs`（`:13934-13944`）**已经列了
> `{ key: "roundsPerRun", label: "执行轮次" }`**，但表单无对应输入框，该项永远读到 undefined。
> 这是"半成品"的直接证据。

### 后端契约（只读，不改）

`BatchExecutionSnapshot`（`BatchExecutionModels.kt:9-22`）已含 `regions: List<String> = emptyList()`
与 `roundsPerRun: Int = 1`；`RecipientScope.fromSnapshot`（`:78-93`）已消费 regions；
`buildEsFiltersForLevel`（`ManualInitialOutreachService:1222`）已调
`ExpertSearchService.regionsFilter(scope.regions)`。**后端一切就绪，纯前端缺口。**

### Interaction points

| # | 写 | 读 | 验收 |
|---|---|---|---|
| IP-1 | 前端 snapshot | `RecipientScope.fromSnapshot` → `regionsFilter` | A-2 |
| IP-2 | `deepCloneConfig` | `computeManualDiffs` 差异比对 | A-3 |

## 实现方案

### T-1 补 DOM【S-1, S-2】
文件：`src/main/resources/static/index.html`

在 `batchManualPanel`（`:1288`）内，「标签」（`:1336-1344`）之后插入 S-1 的地区选择器；
「每轮数量」（`:1378`）之前插入 S-2 的执行轮次字段。位置与 `batchConfigEditor` 的字段顺序保持一致。

### T-2 补 JS 读写【I-1, I-2】
文件：`src/main/resources/static/app.js`

按「现状审计」表逐个补齐 7 个函数。`regions` 复用既有的
`readBatchRegionPickerValue` / `setBatchRegionPickerValue`（`app.js:13481-13485`），
不新写取值逻辑。`computeManualDiffs` 的 `fieldMap`（`:13978-13982`）追加
`regions: "manualFieldRegions"`、`roundsPerRun: "manualFieldRoundsPerRun"`。

## 变更文件清单（2 个）

| # | 文件 | 类型 |
|---|---|---|
| 1 | `src/main/resources/static/index.html` | 改 |
| 2 | `src/main/resources/static/app.js` | 改 |

## 验证命令

> 本项目必须用 JDK 11（zulu-11）。本计划为纯前端改动，无对应单测，以构建与人工验收为准。

```bash
# 构建（确认静态资源打包无误）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0，`Tests run: N, Failures: 0, Errors: 0`。
来源：CLAUDE.md「Commands」章节。

## 验收标准

- **I-1**：`grep -n "regions" src/main/resources/static/app.js` 在上述 7 个函数内均有命中；
  `confirmManualExecution` 的 snapshot 对象字面量含 `regions` 与 `roundsPerRun` 两个 key。
- **I-2**：`deepCloneConfig` 返回对象含这两个字段。
- **S-1/S-2**：`git diff src/main/resources/static/styles.css` 为**空**；
  `git diff src/main/resources/static/index.html` 中新增元素的 class 全部来自契约列出的既有 class，
  无 `style="` 内联样式，无新 class 定义。
- **回归**：执行『验证命令』节的构建与全量测试通过。

## 人工验收清单

### A-1：控件存在且与配置编辑器一致【outcome 1 / S-1, S-2】
- 前置：打开批量发送页 →「手动执行」tab。
- 步骤：① 目视面板字段；② 切到「定时任务配置」编辑器对比。
- 预期：手动面板出现"地区"选择器与"执行轮次"输入框；两者的边框、圆角、内边距、
  标签字号与配置编辑器**肉眼无差异**（同为 `.batch-config-field` + `.batch-config-field-label`）。

### A-2：地区过滤真实生效【outcome 2 / IP-1】
- 前置：新建一个定时任务，地区只选「北美」，保存。
- 步骤：① 手动执行 tab 选择该任务作为来源；② 确认面板地区显示「北美」；
  ③ 执行；④ 打开执行日志，看实际发送对象的国家。
- 预期：目标数量明显小于全量；抽查 5 位收件人，国家均属北美。
  **（改动前此场景会发给全球，是本计划的核心回归点）**

### A-3：执行轮次带入并生效【outcome 2】
- 前置：定时任务的"执行轮次"设为 3。
- 步骤：① 手动执行选该任务；② 确认面板显示 3；③ 执行；④ 看日志轮次数。
- 预期：面板显示 **3**（非 1）；日志中 ROUND 行出现 3 次（或因目标不足提前结束）。

### A-4：差异高亮正常【outcome 3 / IP-2】
- 前置：承 A-2，来源任务地区为「北美」。
- 步骤：把面板地区改成「欧洲」。
- 预期：该字段出现红框（`.is-config-diff`）；确认弹窗的差异列表出现
  「地区：北美 → 欧洲」一行。

### A-5：独立手动执行不受影响【must-NOT-change】
- 步骤：不选任何来源配置，直接填参数执行。
- 预期：地区留空时行为与改动前一致（不限地区）；执行成功。

### A-6：既有字段无回归【must-NOT-change】
- 步骤：逐个修改模板 / 漏斗层级 / 标签 / 邮箱服务商 / 学科 / 三个间隔。
- 预期：差异高亮、确认弹窗、执行结果均与改动前一致。
