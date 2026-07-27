# 批量邮件任务控制台前端

> 顺序：3/3。依赖 `batch-send-task-config-crud.md`、`batch-send-task-execution-and-logs.md`。

## 需求描述

- 原单页配置弹窗改为“定时任务 / 手动执行”两个 tab，避免配置语义混淆。
- 定时任务以列表呈现，支持查询、新增、查看/编辑、启停、删除；每行提供“手动”“日志”。
- 手动 tab 默认不选择定时配置；提供按任务名称搜索的可选配置筛选框。
- 由配置进入手动页时载入快照；用户改动字段以红色明确标出，并显示原值。
- 点击执行始终弹确认：有配置且有改动时逐项列出差异；无改动时展示摘要；未选配置时提示独立手动执行，不做差异校验。
- 配置级日志完整展示目标、成功、失败、跳过、剩余、耗时、状态及原因数量。

## 关键不变量

### I-1：tab 状态互不污染

- 直接点击“手动执行”tab 时初始化为未选择配置；不得自动选第一条或上次配置。
- 从定时任务行点击“手动”时才携带该行配置，切 tab 并建立 baseline。
- 手动修改永不更新定时列表；只有定时 tab 的新增/编辑保存才调用配置 CRUD。
- 关闭弹窗清空 source、baseline、draft、diff、确认弹窗和日志抽屉状态。

### I-2：差异比较以规范化值为准

- baseline 是加载配置时的深拷贝，draft 是表单实时值。
- 比较字段：模板、漏斗层级、标签、邮箱服务商、学科、日限额、每轮数量、单封间隔、轮次间隔、自查 TTL；cron/启停不属于手动执行参数。
- null、空串、`ALL` 统一为空；标签 trim、去重、排序后比较；数字按 Number 比较，避免表现差异误报。
- 变化字段容器加 `.is-config-diff`，显示“已修改”和“原：xxx”；恢复原值后立即移除。
- 未选择配置时不计算、不显示 diff。

### I-3：确认弹窗不可绕过

- 所有执行按钮先做普通必填、数值、模板校验，再打开专用确认弹窗；确认后才 POST。
- 有来源且有差异：标题“确认按修改后的配置执行？”，列出字段、原值、新值，并说明“不影响定时配置”。
- 有来源且无差异：标题“确认执行该配置？”，展示来源配置和收件/限额摘要。
- 无来源：标题“确认独立手动执行？”，明确“未关联定时配置，本次参数不会保存”；不出现差异表。
- 禁止复用全局 `#actionDialog`，使用独立 DOM，避免 task modal cleanup 残留文本/按钮状态。

### I-4：日志数量守恒可见

- 摘要表始终显示目标、成功、失败、跳过、耗时、状态；`remaining>0` 时额外显示剩余。
- 选中执行后展示失败原因、跳过原因；每行包含原因文本和数量，按数量降序。
- UI 验证 `target === success + failure + skipped + remaining`；不守恒时显示“统计待核对”警示，不能自行修正服务端数值。
- 失败/跳过为 0 时显示空态，不隐藏整个详情结构；RUNNING 时自动刷新当前配置日志。

### I-5：危险操作和重复提交有反馈

- 删除必须二次确认；运行中的配置由后端拒绝时保留行并展示错误。
- CRUD、启停、执行、日志加载期间仅禁用对应按钮/区域，不锁死整个弹窗。
- 执行确认后立即禁用确认按钮；收到 202 后关闭确认弹窗、显示 executionId 并进入对应日志。

## 样式契约

### 设计基线

- 沿用 `styles.css` 现有 token：主色 `#2563eb`、正文 `#1e293b`、弱文字 `#94a3b8`、成功 `#059669`、错误 `#e11d48`、警告 `#d97706`、面板边框 `rgba(15,23,42,.08)`。
- 沿用字体、`.button`、`.badge`、`.data-table`、`.modal-overlay`、`.task-modal-*`；不引入新字体、图标包、CSS 框架。
- 桌面主弹窗宽 `min(1320px, calc(100vw - 64px))`、高 `min(860px, calc(100vh - 48px))`；移动端单列。

### DOM 结构契约

```html
<section id="batchSendTaskModal" class="task-modal batch-send-task-modal">
  <header class="task-modal-header">...</header>
  <nav class="batch-send-tabs">
    <button class="batch-send-tab is-active" data-tab="scheduled">定时任务</button>
    <button class="batch-send-tab" data-tab="manual">手动执行</button>
  </nav>
  <div id="batchScheduledPanel" class="batch-send-tab-panel">...</div>
  <div id="batchManualPanel" class="batch-send-tab-panel" hidden>...</div>
  <aside id="batchExecutionLogDrawer" class="batch-log-drawer" hidden>...</aside>
</section>
<div id="batchManualConfirmDialog" class="modal-overlay batch-manual-confirm-overlay" hidden>...</div>
```

- S-1：`.task-modal.batch-send-task-modal` 只负责本功能尺寸，不能扩大所有 task modal。
- S-2：`.batch-send-tabs/.batch-send-tab` 对应 tab 导航；active 用底部 2px 主色线，不使用大面积填充。
- S-3：`.batch-task-toolbar` 对应名称搜索、新增按钮。
- S-4：`.batch-task-table-wrap/.batch-task-table` 对应配置表；范围列允许两行，操作列不换行。
- S-5：`.batch-log-drawer` 对应配置级日志右侧抽屉。
- S-6：`.batch-manual-source-card` 对应手动来源搜索和来源说明。
- S-7：`.batch-manual-form-grid/.batch-config-field` 对应手动参数表单。
- S-8：`.batch-config-field.is-config-diff/.batch-config-diff-*` 对应红色差异提示。
- S-9：`.batch-manual-confirm-*` 对应专用执行确认框和差异表。
- S-10：`.batch-log-metrics/.batch-log-metric` 对应执行数量指标。
- S-11：`.batch-reason-list/.batch-reason-row` 对应原因和数量。

### 必须新增的完整 CSS 规则

```css
.task-modal.batch-send-task-modal {
  width: min(1320px, calc(100vw - 64px));
  max-width: none;
  height: min(860px, calc(100vh - 48px));
  max-height: none;
  display: flex;
  flex-direction: column;
}

.batch-send-tabs {
  display: flex;
  gap: 28px;
  min-height: 48px;
  padding: 0 28px;
  border-bottom: 1px solid rgba(15, 23, 42, .08);
}

.batch-send-tab {
  position: relative;
  border: 0;
  background: transparent;
  color: #64748b;
  padding: 0 2px;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}

.batch-send-tab::after {
  content: "";
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  background: transparent;
}

.batch-send-tab:hover { color: #2563eb; }
.batch-send-tab.is-active { color: #2563eb; }
.batch-send-tab.is-active::after { background: #2563eb; }

.batch-send-tab-panel {
  flex: 1;
  min-height: 0;
  padding: 20px 28px 28px;
  overflow: auto;
}

.batch-task-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.batch-task-toolbar-search { width: min(360px, 100%); }
.batch-task-table-wrap { overflow: auto; border: 1px solid rgba(15, 23, 42, .08); border-radius: 10px; }
.batch-task-table { min-width: 1080px; margin: 0; }
.batch-task-table td { vertical-align: middle; }
.batch-task-table td.batch-task-scope { min-width: 210px; white-space: normal; color: #475569; }
.batch-task-table td.batch-task-actions { white-space: nowrap; }
.batch-task-scope-line + .batch-task-scope-line { margin-top: 3px; color: #94a3b8; }

.batch-log-drawer {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 4;
  width: min(620px, 72%);
  padding: 22px;
  overflow: auto;
  background: #fff;
  border-left: 1px solid rgba(15, 23, 42, .08);
  box-shadow: -12px 0 32px rgba(15, 23, 42, .12);
}

.batch-manual-source-card {
  padding: 16px;
  margin-bottom: 16px;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 10px;
  background: #f8fafc;
}

.batch-manual-source-note { margin-top: 8px; color: #64748b; font-size: 12px; }
.batch-manual-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 16px; }

.batch-config-field {
  position: relative;
  padding: 12px;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 10px;
  background: #fff;
}

.batch-config-field.is-config-diff {
  border-color: #e11d48;
  background: #fff7f8;
  box-shadow: 0 0 0 1px rgba(225, 29, 72, .08);
}

.batch-config-diff-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  color: #be123c;
  font-size: 11px;
  font-weight: 700;
}

.batch-config-diff-original { margin-top: 6px; color: #be123c; font-size: 12px; }
.batch-manual-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 18px; }

.batch-manual-confirm-overlay { z-index: 1200; }
.batch-manual-confirm-dialog { width: min(620px, calc(100vw - 32px)); max-height: min(720px, calc(100vh - 40px)); overflow: auto; }
.batch-manual-confirm-summary { padding: 12px; border-radius: 10px; background: #f8fafc; color: #475569; }
.batch-manual-confirm-warning { margin-top: 12px; color: #be123c; font-size: 12px; }
.batch-manual-confirm-table { width: 100%; margin-top: 12px; border-collapse: collapse; }
.batch-manual-confirm-table th,
.batch-manual-confirm-table td { padding: 9px 10px; border-bottom: 1px solid rgba(15, 23, 42, .08); text-align: left; }
.batch-manual-confirm-old { color: #94a3b8; text-decoration: line-through; }
.batch-manual-confirm-new { color: #be123c; font-weight: 600; }

.batch-log-metrics { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 8px; margin: 14px 0; }
.batch-log-metric { padding: 10px; border: 1px solid rgba(15, 23, 42, .08); border-radius: 10px; background: #f8fafc; }
.batch-log-metric-label { color: #94a3b8; font-size: 11px; }
.batch-log-metric-value { margin-top: 3px; color: #1e293b; font-size: 18px; font-weight: 700; }
.batch-log-metric.is-success .batch-log-metric-value { color: #059669; }
.batch-log-metric.is-failure .batch-log-metric-value { color: #e11d48; }
.batch-log-metric.is-skipped .batch-log-metric-value { color: #d97706; }

.batch-reason-list { margin: 8px 0 16px; border: 1px solid rgba(15, 23, 42, .08); border-radius: 10px; overflow: hidden; }
.batch-reason-row { display: flex; justify-content: space-between; gap: 12px; padding: 10px 12px; color: #475569; }
.batch-reason-row + .batch-reason-row { border-top: 1px solid rgba(15, 23, 42, .08); }
.batch-reason-count { color: #1e293b; font-weight: 700; }
.batch-log-integrity-warning { padding: 10px 12px; border-radius: 10px; background: #fff7ed; color: #c2410c; }

@media (max-width: 760px) {
  .task-modal.batch-send-task-modal { width: calc(100vw - 20px); height: calc(100vh - 20px); }
  .batch-send-tabs, .batch-send-tab-panel { padding-right: 16px; padding-left: 16px; }
  .batch-task-toolbar { align-items: stretch; flex-direction: column; }
  .batch-task-toolbar-search { width: 100%; }
  .batch-manual-form-grid { grid-template-columns: 1fr; }
  .batch-log-drawer { width: 100%; }
  .batch-log-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
```

## 现状审计

### DOM 与状态

- `index.html` 当前 `batchSendConfigPanel` 是单份表单，包含全局 `batchSendType`、固定范围说明、模板、调度、限额。
- `app.js` 通过 `batchSendType` 选择 typed endpoint，并在 `fill/readBatchSendConfigForm()` 读写单份配置。
- 通用 task execution 区按 task type 展示，不具备配置 ID 上下文。
- 现有 `taskProgressModal` 有共享清理逻辑；批量专用 dialog/drawer必须在关闭时独立清理。

### 可复用样式

- 按钮：`.button.primary/.secondary/.danger`；状态：`.badge` 变体；表格：`.data-table`。
- 弹层：`.modal-overlay`、`.task-modal`、`.task-modal-header/body`。
- 现有 `.tabs/.tab` 是填充型 active，与已确认的下划线 tab 不同，本功能新增命名空间类，避免改全局。
- 现有 task modal 最大宽 700px；只为 `.batch-send-task-modal` 覆盖。

## 实现方案

### Phase 1：重构 DOM

#### Task 1.1：定时任务 tab

文件：`src/main/resources/static/index.html`

- 删除 `#batchSendType` 及类型切换说明。
- 增加 tab 导航和 `#batchScheduledPanel`。
- toolbar：`#batchConfigSearch`、`#batchConfigCreateButton`。
- 表格列：任务名称、收件范围、模板、执行计划、最近执行、状态、操作。
- 操作固定顺序：手动、编辑、日志、删除；状态列含启停 switch。
- 新增/编辑使用同一配置表单，但标题和保存事件由 mode 区分。
- 日志抽屉包含执行列表、数量指标、失败原因、跳过原因、错误样例、批次时间线。

#### Task 1.2：手动执行 tab 与确认框

文件：`src/main/resources/static/index.html`

- 顶部增加 `#batchManualSourceQuery`，placeholder：`输入任务名称搜索，默认不选择`；hidden `#batchManualSourceId`；autocomplete 列表。
- 未选时显示“独立手动执行”；选择后显示来源名称、更新时间和“清除选择”。
- 表单包含模板、漏斗、标签、邮箱服务商、学科、日限额、每轮数量、间隔、自查 TTL；不显示 cron、启停。
- 每个字段使用 `.batch-config-field`，预留 diff badge 和 original 文本节点。
- 增加独立 `#batchManualConfirmDialog`，不得嵌入共享 `#actionDialog`。

### Phase 2：前端状态与 API

#### Task 2.1：配置列表 CRUD

文件：`src/main/resources/static/app.js`

- 建立局部 `batchTaskState`：`activeTab/configs/query/editorMode/logConfigId/logExecutionId/manualSource/manualBaseline/manualDraft/diffs`。
- 列表使用新 configs API；搜索 250ms debounce；空态、加载态、失败态明确。
- 启停使用 PATCH；删除确认后 DELETE；成功后局部刷新列表。
- “编辑”加载详情；“手动”调用统一 `openManualTab(config)`；“日志”只传当前 id 打开抽屉。
- 所有动态文本用 `textContent`/现有 escape helper，禁止把任务名、原因、错误样例直接拼入不可信 HTML。

#### Task 2.2：手动来源和差异

文件：`src/main/resources/static/app.js`

- 用户直接点手动 tab：调用 `resetManualExecution({preserveSource:false})`，来源为空并填系统默认值。
- 行“手动”：深拷贝配置到 baseline，填充 draft，保存 `sourceUpdatedAt`。
- 来源搜索只筛未删除配置；选择新来源覆盖 draft 前，如当前表单已编辑则先提示确认放弃。
- `normalizeManualSnapshot()` 统一 null/ALL、数字、标签；`computeManualDiffs()` 返回 `{key,label,oldDisplay,newDisplay}`。
- input/change 时增量重算；按 I-2 切换 `.is-config-diff`。

#### Task 2.3：三类执行确认

文件：`src/main/resources/static/app.js`

- `requestManualExecution()`：先运行表单校验，再构造 snapshot 和 diffs。
- source+diff：渲染差异表；source+无 diff：渲染摘要；无 source：渲染独立执行说明。
- `confirmManualExecution()` 防重复点击，POST `/manual-executions`；成功后关闭 dialog、toast executionId。
- 有来源时自动打开该配置日志并选中 executionId；无来源时跳转/打开通用任务记录中的该 executionId。
- API 失败保留 draft 与确认内容，重新启用按钮并展示错误。

#### Task 2.4：配置级日志

文件：`src/main/resources/static/app.js`

- 打开抽屉先请求执行摘要；默认选第一条；再请求详情。
- `renderOutcomeMetrics()` 显示 target/success/failure/skipped，remaining>0 才加第五/第六指标。
- 原因列表按 count 降序；0 项展示“无失败原因”/“无跳过原因”。
- 校验守恒，不一致显示 warning。
- RUNNING 每 3 秒刷新当前详情；切换 execution、关闭抽屉、关闭主弹窗时清理 timer。
- 状态/triggerType 做中文映射，但保留原值作为未知状态回退。

### Phase 3：样式落地

#### Task 3.1：新增命名空间 CSS

文件：`src/main/resources/static/styles.css`

- 完整加入“样式契约”CSS；禁止只写“参考现有样式”。
- 复用全局 token，不改 `.task-modal`、`.tabs`、`.data-table` 的全局规则。
- 检查 1440×900、1280×720、390×844 三个视口；移动端抽屉占满宽度。

## 变更文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `src/main/resources/static/index.html` | 修改 |
| 2 | `src/main/resources/static/app.js` | 修改 |
| 3 | `src/main/resources/static/styles.css` | 修改 |

共 3 文件，1 个子系统（前端），满足 create-p 限制。

## 验收标准

- 主弹窗有两个 tab；定时任务为列表，无“发送类型”。
- 定时配置可查询、新增、编辑、启停、删除；每行可进入手动和当前配置日志。
- 直接进入手动 tab 时配置筛选为空；不会自动采用任何定时配置。
- 由行进入手动后，修改字段立即红框、显示已修改和原值；改回后标记消失。
- 三种确认情形均正确，任何执行都无法跳过确认。
- 未选配置时无差异校验/标红，但普通字段校验仍执行。
- 日志展示完整数量、耗时、状态、失败/跳过原因；不守恒有警示。
- RUNNING 日志刷新和所有 timer 在关闭/切换时正确释放。
- 现有其他 task modal、共享 action dialog、通用 tabs 样式无回归。

## 人工验收清单

- [ ] 打开弹窗：默认定时任务列表；无发送类型字段。
- [ ] 搜索、新增、编辑、启停、删除各执行一次；仅目标行变化。
- [ ] 直接点手动 tab：筛选框为空，表单无红色差异。
- [ ] 行“手动”：加载正确配置；改模板、漏斗、标签、限额，四处均标红并显示原值。
- [ ] 将字段改回原值：对应红框、徽标、原值提示立即消失。
- [ ] 有差异执行：确认框逐项显示原→新，并提示不影响定时配置。
- [ ] 无差异执行：确认框只有配置和范围摘要。
- [ ] 无配置执行：确认框提示独立执行，不显示差异表。
- [ ] 三类确认取消后均零请求；连续点击确认只产生一个 executionId。
- [ ] 配置 A 日志只显示 A；切换记录后数量、原因、错误样例同步变化。
- [ ] 检查成功/失败/跳过/取消/RUNNING 记录和数量守恒警示。
- [ ] 关闭再打开：无残留 source、diff、dialog、drawer、轮询 timer。
- [ ] 1440×900、1280×720、390×844 下无主体溢出，移动端可完成执行。

## 修正记录

| 日期 | 轮次 | 修正 | 原因 |
|---|---:|---|---|
| 2026-07-14 | fix-1 | 将 `src/test/js/expertTagBatchFix.test.js`、`src/test/js/taskModalStateMachine.test.js` 纳入回归测试范围；断言改为任务控制台契约。 | 本计划移除了 `#batchSendType` 与旧 task-modal 启动路径，但未同步测试，导致 CI 必然失败。 |
