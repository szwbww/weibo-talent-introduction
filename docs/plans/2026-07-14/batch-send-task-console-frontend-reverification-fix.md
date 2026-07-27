# 批量邮件任务控制台前端：复验失败修复

> 复验对象：`batch-send-task-console-frontend.md`。仅修复 fix-v 复验发现的两个 P1；不扩展控制台功能。

## 需求描述

- 手动页从配置加载后，未改动的“每封间隔/每轮间隔”不得出现红框、原值或确认差异。
- 打开配置日志且默认记录为 RUNNING 时，必须每 3 秒刷新该记录，直到切换、关闭或记录终态。

不得改变：来源配置 `id/updatedAt` 传递、标签去重比较、三类确认弹窗、日志 API 路径、现有 DOM/CSS。

范围外：JVM 既有 20 个失败、启停/删除 loading 反馈、日志指标/时间线重构。

## 关键不变量

### I-1：手动间隔统一为毫秒

- Rule：`readManualFormValues()` 返回的 `perMailIntervalMs`、`perRoundIntervalMs` 必须是毫秒；表单显示秒时只在读取边界乘以 1000。baseline、draft、diff、POST snapshot 均只比较/传递毫秒。
- Applies to：`readManualFormValues()`、`normalizeManualSnapshot()`、`computeManualDiffs()`、`confirmManualExecution()`。
- Violation consequence：未编辑配置仍被判定为差异，确认框谎报修改。
- 来源：原计划 I-2；K-batch-console-diff-tag-normalization。

### I-2：默认日志选择先绑定执行身份

- Rule：`loadBatchLogExecutions(configId, executionId)` 选择默认或指定执行记录后，必须先将 `batchTaskState.logExecutionId` 设为该 executionId，再加载详情/建立 RUNNING 轮询。异步列表响应只可更新仍为同一 `logConfigId` 的抽屉。
- Applies to：`openBatchConfigLogs()`、`loadBatchLogExecutions()`、`loadBatchLogDetail()`、日志下拉 change handler。
- Violation consequence：RUNNING 默认记录的定时器条件永远为 false，进度停滞；旧配置迟到响应可覆盖新抽屉。
- 来源：原计划 I-4；K-batch-console-log-timeline。

### I-3：修复不回归来源与差异语义

- Rule：本修复不得清空或重建 `manualSource.id/updatedAt`，不得改变标签 trim/过滤/去重/排序，未选择来源时仍无 diff。
- Applies to：上述函数的改动与新增回归测试。
- Violation consequence：配置级执行降级为独立执行，或产生伪差异。
- 来源：K-batch-console-source-identity、K-batch-console-source-selection、K-batch-console-regression-contract。

## 样式契约

### S-1：无视觉改动

- 复用：`styles.css:7385-7408` 的 `.batch-config-field.is-config-diff` 与差异提示；`styles.css:7421-7433` 的日志指标和警示样式；`index.html:1186-1322` 的手动表单、日志抽屉、确认框 DOM。
- 新增：无。禁止新增 CSS class、DOM 节点、inline style 或修改 `index.html`/`styles.css`。
- DOM 结构：保持现有 `#batchManualPanel`、`#batchExecutionLogDrawer`、`#batchLogExecutionSelect` 层级不变。
- 禁止项：以样式、隐藏 DOM 或重建抽屉解决状态问题。

## 现状审计

### 前端内存状态：手动执行快照

- Store：`batchTaskState.manualSource/manualDraft` 与手动表单 DOM，无持久化写入。
- Write paths：
  1. `app.js:12417-12423` 行“手动”深拷贝来源和 draft。
  2. `app.js:12854-12892` 搜索选择来源并写入完整来源快照。
  3. `app.js:13174-13183` 表单 change 将读取值写入 `manualDraft`。
- Read paths：
  1. `app.js:12480-12505` 将配置毫秒值转换为秒显示。
  2. `app.js:12556-12631` 读取、规范化、比较 snapshot。
  3. `app.js:12740-12777` 将读取值组装为 POST snapshot。
- Interaction point：`fillManualFormFromDraft()` 显示秒（`12492-12493`），`readManualFormValues()` 却将秒写入名称为 `*Ms` 的字段，`normalizeManualSnapshot()` 直接比较（`12587-12588`），使 1000ms 与 1 产生伪差异。

### 前端内存状态：配置级日志选择与轮询

- Store：`batchTaskState.logConfigId/logExecutionId/logRefreshTimer` 与 `#batchLogExecutionSelect`。
- Write paths：
  1. `app.js:12897-12904` 打开抽屉写 configId，未传 executionId 时写 null。
  2. `app.js:12921-12945` 计算 `targetId` 并设置下拉值，但未写 `logExecutionId`。
  3. `app.js:13194-13202` 仅在人工切换下拉时写 executionId。
  4. `app.js:12906-12918` 关闭抽屉/清理 timer。
- Read paths：`app.js:12948-12962` 仅在 `logConfigId === configId && logExecutionId === executionId` 时轮询详情。
- Interaction point：默认选第一条 RUNNING 时，`targetId` 未回写 state，timer 永不刷新；切换抽屉前的异步列表响应也缺少 configId 守卫。

### 前端样式盘点

- 差异状态：`.batch-config-field.is-config-diff` — `styles.css:7385-7408`。
- 日志抽屉/指标：`.batch-log-drawer` — `styles.css:7360-7372`；`.batch-log-metrics` — `styles.css:7421-7427`。
- 设计 token：差异边框 `#e11d48`、差异背景 `#fff7f8`、面板边框 `rgba(15,23,42,.08)`；本计划不得改动。
- 改动前基线：`index.html:1186-1322` 已有需要的表单、抽屉和下拉节点；仅修 JavaScript 状态/单位。

## 实现方案

### Phase 1：修复单位边界

#### Task 1.1：读取时将秒规范化为毫秒

文件：`src/main/resources/static/app.js`

- 遵守 I-1、I-3、S-1。
- 在 `readManualFormValues()` 中，将两个间隔 input 的有效秒数乘以 1000 后赋给 `perMailIntervalMs/perRoundIntervalMs`；空值继续为 `NaN`，0 秒继续为 0ms。
- `normalizeManualSnapshot()`、`computeManualDiffs()` 保持只处理毫秒；不得为 diff 加显示单位转换或特判。
- `confirmManualExecution()` 移除对已规范化毫秒值的第二次 `* 1000`，直接提交读取的毫秒值；其他 snapshot 字段不变。
- 结果：配置 1000ms/60000ms 显示为 1/60 秒、未编辑时无 diff；操作员输入 2/90 秒时 payload 为 2000/90000ms。

#### Task 1.2：补齐单位回归测试

文件：`src/test/js/expertTagBatchFix.test.js`

- 遵守 I-1、I-3。
- 将现有 `readManualFormValues` 断言改为毫秒语义：2 秒断言为 2000，0 秒断言为 0。
- 在现有 diff sandbox 增加配置 baseline `perMailIntervalMs:1000`、`perRoundIntervalMs:60000` 与表单 draft 1/60 秒的用例，断言 `computeManualDiffs()` 不包含两个 interval key。
- 增加真实变更用例：2/90 秒必须仅产生对应 interval diff；防止“全部忽略间隔”式修复。

### Phase 2：绑定默认日志选择并防御迟到响应

#### Task 2.1：默认选中后写入状态身份

文件：`src/main/resources/static/app.js`

- 遵守 I-2、I-3、S-1。
- `loadBatchLogExecutions()` 请求返回后，先确认 `batchTaskState.logConfigId === configId`；不一致则丢弃响应，不重写下拉、state 或详情。
- 得到 `targetId` 后，在调用 `loadBatchLogDetail()` 前写入 `batchTaskState.logExecutionId = targetId`，并同步下拉值。
- 无记录时只在 configId 仍当前时调用 `clearBatchLogDisplay()`；不得清空来源、手动 draft 或其他抽屉状态。
- 保持 `loadBatchLogDetail()` 的 3 秒间隔、关闭/切换清理逻辑和既有 API URL 不变。

#### Task 2.2：补齐默认 RUNNING 与竞态回归测试

文件：`src/test/js/expertTagBatchFix.test.js`

- 遵守 I-2、I-3。
- 为 `loadBatchLogExecutions()`/`loadBatchLogDetail()` 建立最小 VM sandbox：mock `api`、`document.getElementById`、`setInterval`、`clearInterval`、`renderBatchExecutionDetail`、`escapeHtml`、`formatDateTime`、`statusLabel`。
- 用“未传 executionId、列表首条 RUNNING”的场景断言：列表加载后 state 与 select 都为首条 executionId；首次详情创建的 interval 回调会再次调用同一 configId/executionId 的详情加载。
- 用“请求 A 未返回前打开配置 B”的场景断言：A 响应不会覆盖 B 的下拉、`logExecutionId` 或详情。
- 保留人工切换下拉仍更新 `logExecutionId` 的现有契约。

### Phase 3：验证

文件：无新增实现文件。

- 运行 `node --test src/test/js/*.test.js`。
- 运行 `node --check src/main/resources/static/app.js` 与 `git diff --check`。
- 运行 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipTests package` 验证资源构建；全量 `mvn test` 的既有 20 项失败仅记录，不纳入本修复验收。

## 变更文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `src/main/resources/static/app.js` | 修改 |
| 2 | `src/test/js/expertTagBatchFix.test.js` | 修改 |

共 2 文件，1 个子系统（批量任务控制台前端）。

## 验收标准

- I-1：`readManualFormValues()` 对 1/60 秒返回 1000/60000；未编辑来源配置无 interval diff；2/90 秒仍被检测为真实变化；确认 POST 不再将毫秒二次乘 1000。
- I-2：打开无 executionId 的配置日志时，首条 RUNNING 记录同步写入 `logExecutionId`，3 秒回调使用相同 configId/executionId；旧配置列表响应不得覆盖当前抽屉。
- I-3：标签集合测试、来源身份和来源选择既有断言继续通过；无来源不产生 diff。
- S-1：`git diff --name-only` 不含 `index.html`、`styles.css`；不得新增 `.batch-*` class 或 inline style。
- 构建：JS tests 全绿，`node --check`、`git diff --check` 全绿；资源构建成功。

## 人工验收清单

### A-1：未改间隔不显示伪差异

- 前置条件：存在配置 A，`perMailIntervalMs=1000`、`perRoundIntervalMs=60000`。
- 操作步骤：
  1. 打开“批量邮件任务控制台”。
  2. 在配置 A 行点击“手动”。
  3. 不编辑任何字段。
- 预期结果：每封间隔显示 `1` 秒、每轮间隔显示 `60` 秒；两个字段均无红框、无“已修改”、无“原：”；确认框标题为“确认执行该配置？”。
- 覆盖：I-1、I-3、需求描述第 1 条。

### A-2：真实间隔变化按毫秒执行

- 前置条件：沿用配置 A；浏览器网络面板可查看请求体。
- 操作步骤：
  1. 将每封间隔改为 `2`，每轮间隔改为 `90`。
  2. 点击“执行”，再点击“确认执行”。
- 预期结果：确认框只将两个间隔列为变化，原值为 `1000`/`60000`、新值为 `2000`/`90000`；POST snapshot 中为 `perMailIntervalMs:2000`、`perRoundIntervalMs:90000`。
- 覆盖：I-1、需求描述第 1 条。

### A-3：默认 RUNNING 日志持续刷新

- 前置条件：配置 A 至少有一条最新的 RUNNING 执行记录。
- 操作步骤：
  1. 在配置 A 行点击“日志”。
  2. 不操作下拉框，等待 4 秒。
  3. 在网络面板查看详情请求。
- 预期结果：下拉自动选中该 RUNNING executionId；4 秒内同一 `configs/A/executions/{executionId}` 详情请求至少出现第二次；切换记录、关闭抽屉或执行变为终态后不再刷新。
- 覆盖：I-2、需求描述第 2 条。

### A-4：迟到响应不覆盖当前抽屉

- 前置条件：存在配置 A、B；可用浏览器网络限速延迟 A 的日志列表请求。
- 操作步骤：
  1. 点击 A“日志”，立即点击 B“日志”。
  2. 释放 A 的延迟响应。
- 预期结果：抽屉只显示 B 的执行记录和详情；A 的记录、指标、原因、时间线不出现；B 的 RUNNING 记录继续按 3 秒刷新。
- 覆盖：I-2、I-3、需求描述第 2 条。

### A-5：来源与视觉回归

- 前置条件：配置 A 有标签 `AI`、`updatedAt`；存在可选配置 B。
- 操作步骤：
  1. 由 A 行进入“手动”，在来源搜索框选择 B 后再切回 A。
  2. 输入标签 ` AI,AI `，再清除来源。
- 预期结果：来源说明始终显示所选配置名称和更新时间；重复标签不产生红框；清除来源后显示“当前：独立手动执行（未关联定时配置）”；所有字段保持现有 `#e11d48` 红框样式而无布局/DOM变化。
- 覆盖：I-3、S-1、不得改变项。
