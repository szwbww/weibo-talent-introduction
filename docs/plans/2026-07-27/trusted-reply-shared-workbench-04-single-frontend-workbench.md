# 可信回复工作台 04：单一前端工作台

日期：2026-07-27  
状态：待批准、待执行  
前置：[01 共享运行时与 API](./trusted-reply-shared-workbench-01-shared-runtime-api.md)、[02 逐项 AI、版本锁定与无改写整合](./trusted-reply-shared-workbench-02-item-lock-assembly.md)、[03 训练评估留存](./trusted-reply-shared-workbench-03-training-evaluation-audit.md) 已通过

## 需求描述

把 AI 训练页的模拟回复与真实来信详情页的可信回复收口到同一个前端组件。组件内部 DOM、状态机、请求、SSE、逐项 AI 调整、版本、锁定和整合预览完全一致；两个页面只提供来源和完成动作。

固定差异只有：

1. 训练页固定 `TRAINING_MAIL`，顶部显示“模拟 · 不外发”，最终按钮为“完成模拟并评估”，完成后显示训练评估区。
2. 真实来信页固定 `LIVE_INBOUND`，顶部显示“正式回复”，最终按钮为“采用到人工回复”，完成后写入既有人工富文本编辑器；仍需人工点击发送。
3. 页面不提供模式开关。模式和 sourceId 由宿主传入，用户不能在工作台内切换。

必须不改变：现有收件箱详情、人工富文本编辑、preflight、`manual-rich-reply` 发送链路、训练邮件列表、旧兼容 API、自动回复决策链路。

本计划不包含：删除旧前端函数/旧 API、保存未完成工作台草稿、多人协同、训练评估历史、移动端专用页面、修改邮件发送审批规则。

## 关键不变量

### Invariant I-1: 工作台内部只有一份实现
- Rule: 工作台 DOM、状态、事件绑定、API/SSE 调用、请求身份校验、逐项版本、锁定和整合逻辑只定义在 `trust-reply-workbench.js`。训练页和真实页只能调用同一个 `mount(host, options)`；`index.html` 不内联工作台逻辑，`app.js` 不复制工作台内部表单或状态机。
- Applies to: `trust-reply-workbench.js`、`app.js`、`index.html`。
- Violation consequence: 两个入口再次分叉，修复需来回同步。
- 来源: original

### Invariant I-2: 模式由宿主固定且无用户切换
- Rule: `mount` 只接受宿主传入的 `SIMULATION|LIVE`；`SIMULATION` 必须配 `TRAINING_MAIL`，`LIVE` 必须配 `LIVE_INBOUND`，组合不合法则拒绝挂载并显示错误。组件 DOM 中不得出现模式 selector/radio/toggle。
- Applies to: `mount` 参数校验、两处 mount adapter、组件渲染。
- Violation consequence: 模拟结果误进入发送上下文，或真实来信被当训练样本处理。
- 来源: K-ai-simulate-exact-mail-id

### Invariant I-3: 两处挂载彼此隔离并可安全卸载
- Rule: 每次 mount 返回 `unmount()`；所有 state、request sequence、AbortController/EventSource、DOM listener 都属于该实例。切换训练邮件、切换真实详情或离开 view 前必须 unmount；旧响应即使晚到也不得修改新实例。
- Applies to: component lifecycle、`setView`、训练选择、真实详情加载。
- Violation consequence: A 邮件的结果覆盖 B 邮件，隐藏页面继续消耗 SSE。
- 来源: K-ai-preflight-stale-response-draft-identity

### Invariant I-4: 训练来源必须是精确 mailRecordId
- Rule: 训练列表 option/value 和 mount sourceId 均使用 `mailRecordId`；不得把 contactId 传给公共工作台，也不得取“联系人最新一封”作为兼容回退。真实页使用当前 `inboundProcessingId`。
- Applies to: 训练列表选择、两个 mount adapter、bootstrap request。
- Violation consequence: 用户选中历史邮件 A 却模拟历史邮件 B。
- 来源: K-ai-simulate-exact-mail-id

### Invariant I-5: 逐项版本和锁定只影响目标项
- Rule: 每项以服务端 `requestKey` 为主键。调整 instruction、选择 handling、生成新版本、切换版本、锁定/解锁都只更新目标项；其他项的 instruction、versions、activeVersionId、lockedVersionId 和正文逐字不变。AI 响应必须同时匹配 component instance、sourceVersion、requestKey 和 requestSeq 才能落 state。
- Applies to: reducer/state update、item API callback、renderer。
- Violation consequence: 调整第二项导致第一项答案或锁定丢失。
- 来源: K-ai-draft-review-state-per-draft

### Invariant I-6: 事实或来源变化使旧版本失效
- Rule: 改变事实选择、收到新的 sourceVersion/evidenceSetVersion 或重新 bootstrap 时，先要求用户确认；确认后清空全部 versions/locks/assembly，取消在途请求并重新生成 canonical request→fact 矩阵。拒绝确认则恢复原事实选择。不得把旧版本静默带入新证据集。
- Applies to: fact selection event、bootstrap/full-generate、assemble enablement。
- Violation consequence: UI 显示新证据，实际整合旧答案。
- 来源: K-explicit-fact-selection-must-match-request, K-ai-reply-evidence-version-deterministic

### Invariant I-7: 锁定和整合由服务端裁决
- Rule: 前端只有在全部当前 request 都显式锁定、没有 item request pending、source/evidence 未 stale 时启用“整合”；`OMIT` 也必须锁定服务端同步返回的 `OMITTED` 版本，不能自动略过确认。整合按钮必须调用服务端 assemble；前端不得拼接、去重、截断、改写 answerText。预览必须显示服务端 `rawDraftText`，发送采用使用同次返回的 `rawDraftText/renderedHtml/draftHash`。
- Applies to: enablement selector、assemble call、summary preview、final callback。
- Violation consequence: 视觉预览与最终正文不一致，重复问题被误删或超过四项被丢弃。
- 来源: K-ai-preview-raw-adoption-boundary, K-answerbody-source-exclusive

### Invariant I-8: 模拟完成永不调用发送路径
- Rule: SIMULATION 的 `onComplete` 只打开/提交训练评估；不得写人工编辑器、不得调用 preflight/manual-rich-reply。LIVE 的 `onComplete` 只采用到人工富文本编辑器并更新基线，不自动发送；SMTP 仍只由既有人工发送按钮触发。
- Applies to: 两种完成 adapter、训练评估区、live adopt。
- Violation consequence: 训练数据外发，或 AI 完成后未经人工确认即发送。
- 来源: K-ai-adopt-direct-send-no-residual-gates, K-manual-rich-render-before-send

### Invariant I-9: 原始正文是采用边界
- Rule: 工作台预览/采用以 `rawDraftText` 为 authority；`renderedHtml` 仅作为同次服务端渲染产物用于富文本显示。不得从 preview DOM、`innerText` 或编辑器 HTML 反向重建 raw。LIVE 采用后仍由既有人工链路在发送前重新渲染和校验。
- Applies to: assembly state、live callback、manual editor baseline。
- Violation consequence: 预览格式污染邮件正文或绕过服务器渲染校验。
- 来源: K-ai-preview-raw-adoption-boundary, K-manual-rich-render-before-send

### Invariant I-10: 公共脚本无全局符号冲突
- Rule: `trust-reply-workbench.js` 使用幂等 IIFE，仅导出 `window.TrustReplyWorkbench`；不得声明顶层 `$`、`$$`、`state`，不得使用 `document.write`，不得通过 inline `<script>` 重复执行。若 global 已存在则直接返回。脚本在 `app.js` 前只加载一次。
- Applies to: 新脚本、`index.html` script 顺序、JS tests。
- Violation consequence: 再次触发 `Identifier '$' has already been declared`，整页脚本停止。
- 来源: original（已发生预览故障）

### Invariant I-11: 加载与错误状态真实可观察
- Rule: full generation 和 item generation 使用服务端 SSE stage/message，不伪造百分比；取消、超时、业务错误和网络错误分别进入可重试状态。pending 时只禁用会破坏一致性的控件，其他已完成项仍可阅读；ARIA live region 公告阶段/错误。
- Applies to: SSE adapter、loading panel、button disabled states。
- Violation consequence: 用户误判完成、重复提交，或屏幕阅读器无法获知状态。
- 来源: K-ai-stream-progress-no-fake-percent, K-ai-reply-loading-panel

## 样式契约

### 复用基线

必须直接复用现有设计 token 和组件，不另造颜色/阴影体系：

- `styles.css:1-60`：`--primary`、`--primary-hover`、`--text-main`、`--text-muted`、`--line`、`--panel-border`、`--surface`、`--error`、`--warning`、`--success`、radius/shadow token。
- `styles.css:587-655`：`.button`、`.button.primary`、`.button.secondary`、`.button.danger` 及 disabled/focus。
- `styles.css:733-755`：`.panel`；`829-877`：`.badge` 状态样式。
- `styles.css:1910-1984`：`.reply-workflow-detail/summary/icon/title/status/chevron/content`。
- `styles.css:5360-5516`：`.compose-panel`、`.compose-fragments`、`.compose-count`、`.compose-rule-*`。
- `styles.css:5885-6115`：`.ai-reply-model-row`、`.ai-reply-generation-controls`、`.ai-reply-loading-overlay`、`.ai-reply-feedback`。
- `.pre` 继续承载保留换行的只读正文；新类只补布局和状态，不覆盖全局 typography。

DOM 必须以现有类为基座：最外层使用 `detail-section reply-workflow-detail trust-reply-workbench`；逐项/摘要/评估卡使用 `compose-panel`；按钮只使用 `button` 变体；coverage 使用现有 `.badge.ok/.badge.warn/.badge.error`；完整生成 loading 使用既有 `.ai-reply-loading-overlay`，阶段/单项反馈使用 `.ai-reply-feedback` 内的既有 coverage/warning/error 类。

### Style S-1: 工作台壳与工具栏
- Governs: I-1、I-2、I-11。
- Append to: `src/main/resources/static/styles.css`，紧接现有 AI reply/loading 样式之后、training media rules 之前。
- Exact CSS:

```css
.trust-reply-workbench {
  position: relative;
}

.trust-reply-workbench .reply-workflow-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.trust-reply-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.trust-reply-toolbar .ai-reply-model-row {
  margin-bottom: 0;
}

.trust-reply-mode-note {
  max-width: 560px;
  margin: 0;
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.5;
}
```

### Style S-2: 逐项卡、处理控件、版本正文
- Governs: I-5、I-6、I-11。
- Exact CSS:

```css
.trust-reply-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(280px, 0.75fr);
  gap: 12px;
  align-items: start;
}

.trust-reply-item-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.trust-reply-item {
  border: 1px solid var(--panel-border);
  border-left: 3px solid var(--text-muted);
  border-radius: 12px;
  padding: 14px;
  background: #fff;
  transition: border-color 0.16s ease, background 0.16s ease, box-shadow 0.16s ease;
}

.trust-reply-item[data-coverage="GROUNDED"] {
  border-left-color: var(--success);
}

.trust-reply-item[data-coverage="PARTIAL"] {
  border-left-color: var(--warning);
}

.trust-reply-item[data-coverage="UNSUPPORTED"] {
  border-left-color: var(--error);
}

.trust-reply-item[data-locked="true"] {
  border-color: color-mix(in srgb, var(--success) 42%, var(--panel-border));
  background: color-mix(in srgb, var(--success) 5%, #fff);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--success) 10%, transparent);
}

.trust-reply-item-head {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: start;
}

.trust-reply-item-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--surface);
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 700;
}

.trust-reply-item-title {
  min-width: 0;
}

.trust-reply-item-title strong,
.trust-reply-item-title small {
  display: block;
}

.trust-reply-item-title strong {
  color: var(--text-main);
  font-size: 13px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.trust-reply-item-title small {
  margin-top: 3px;
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.45;
}

.trust-reply-item-controls {
  display: grid;
  grid-template-columns: minmax(160px, 0.45fr) minmax(0, 1.55fr);
  gap: 10px;
  margin-top: 12px;
}

.trust-reply-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 600;
}

.trust-reply-field select,
.trust-reply-field textarea,
.trust-reply-version-select {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  color: var(--text-main);
  font: inherit;
}

.trust-reply-field select,
.trust-reply-version-select {
  min-height: 36px;
  padding: 7px 30px 7px 9px;
}

.trust-reply-field textarea {
  min-height: 68px;
  padding: 9px 10px;
  line-height: 1.5;
  resize: vertical;
}

.trust-reply-field select:focus,
.trust-reply-field textarea:focus,
.trust-reply-version-select:focus {
  outline: 2px solid color-mix(in srgb, var(--primary) 24%, transparent);
  outline-offset: 1px;
  border-color: var(--primary);
}

.trust-reply-field select:disabled,
.trust-reply-field textarea:disabled,
.trust-reply-version-select:disabled {
  cursor: not-allowed;
  opacity: 0.62;
  background: var(--surface);
}

.trust-reply-answer {
  margin-top: 10px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--surface);
  overflow: hidden;
}

.trust-reply-answer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--line);
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 600;
}

.trust-reply-version-select {
  width: auto;
  max-width: 220px;
  min-height: 30px;
  padding-top: 4px;
  padding-bottom: 4px;
  font-size: 11px;
}

.trust-reply-answer-body {
  min-height: 72px;
  padding: 10px 12px;
  background: #fff;
  color: var(--text-main);
  font-size: 12px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.trust-reply-answer-body:empty::before {
  content: "尚无版本";
  color: var(--text-muted);
}

.trust-reply-item-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
  flex-wrap: wrap;
}
```

### Style S-3: 整合预览与最终动作
- Governs: I-7～I-9。
- Exact CSS:

```css
.trust-reply-summary {
  position: sticky;
  top: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.trust-reply-summary h4 {
  margin-bottom: 0;
}

.trust-reply-assembly {
  min-height: 180px;
  max-height: 420px;
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: #fff;
  color: var(--text-main);
  font-size: 12px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.trust-reply-assembly:empty::before {
  content: "锁定所有需回复条目后生成整合预览";
  color: var(--text-muted);
}

.trust-reply-lock-hint {
  margin: 0;
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.5;
}

.trust-reply-final-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
```

### Style S-4: 训练评估区
- Governs: I-8。
- Exact CSS:

```css
.trust-training-evaluation {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
}

.trust-training-evaluation[hidden] {
  display: none;
}

.trust-training-evaluation h4 {
  margin: 0;
}

.trust-training-rating-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.trust-training-rating-option {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease;
}

.trust-training-rating-option:hover {
  border-color: color-mix(in srgb, var(--primary) 46%, var(--line));
}

.trust-training-rating-option:has(input:checked) {
  border-color: var(--primary);
  background: color-mix(in srgb, var(--primary) 6%, #fff);
}

.trust-training-rating-option input {
  margin-top: 2px;
  accent-color: var(--primary);
}

.trust-training-rating-option span,
.trust-training-rating-option strong,
.trust-training-rating-option small {
  display: block;
  min-width: 0;
}

.trust-training-rating-option strong {
  color: var(--text-main);
  font-size: 12px;
}

.trust-training-rating-option small {
  margin-top: 3px;
  color: var(--text-muted);
  font-size: 10px;
  line-height: 1.45;
}

.trust-training-evaluation-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.trust-training-evaluation-status {
  min-height: 18px;
  color: var(--text-muted);
  font-size: 11px;
}
```

评估备注 textarea 必须复用现有 `.compose-free-text`，不新增重复 input 样式。

### Style S-5: 响应式与无障碍
- Governs: I-3、I-5、I-11。
- Exact CSS:

```css
@media (max-width: 960px) {
  .trust-reply-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .trust-reply-summary {
    position: static;
  }
}

@media (max-width: 640px) {
  .trust-reply-toolbar {
    align-items: stretch;
  }

  .trust-reply-toolbar .ai-reply-model-row {
    justify-content: flex-start;
  }

  .trust-reply-item-head {
    grid-template-columns: 28px minmax(0, 1fr);
  }

  .trust-reply-item-head > .badge {
    grid-column: 2;
    justify-self: start;
  }

  .trust-reply-item-controls,
  .trust-training-rating-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .trust-reply-answer-head {
    align-items: stretch;
    flex-direction: column;
  }

  .trust-reply-version-select {
    width: 100%;
    max-width: none;
  }

  .trust-reply-item-actions,
  .trust-reply-final-actions,
  .trust-training-evaluation-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .trust-reply-item-actions .button,
  .trust-reply-final-actions .button,
  .trust-training-evaluation-actions .button {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .trust-reply-item,
  .trust-training-rating-option {
    transition: none;
  }
}
```

无障碍 DOM 合同：coverage/status 使用文字而非只靠颜色；loading/error 容器 `role="status" aria-live="polite"`；item error 使用 `role="alert"`；所有 label 通过 `for/id` 或包含关系绑定；锁定按钮使用 `aria-pressed`；`details/summary` 保留键盘原生行为；隐藏使用 `hidden` 而非仅 opacity。

## 现状审计

### 改动前训练页基线

- `index.html:774-910` 是独立 AI 训练 view；`865-906` 包含历史邮件 selector、模型 selector、模拟按钮、结果/错误容器。
- `app.js:88-113` 保存 `state.aiTraining`；`3087-3391` 单独加载历史邮件、按 contact/mail 生成模拟结果；`11220-11327` 单独绑定训练事件。
- 当前训练页只显示整封模拟结果，没有 request 级处理方式、instruction、版本、锁定、服务端整合或评估。
- 当前历史选择链路存在 mail/contact 两种身份语义；本计划必须只把精确 mailRecordId 传给公共工作台。

改动前需保留并替换的页面形态：

```html
<select id="aiTrainingMailSelect"></select>
<select id="aiTrainingModelSelect"></select>
<button id="aiTrainingSimulateButton">模拟回复</button>
<div id="aiTrainingSimulateResult"></div>
```

目标是保留邮件选择，移除训练专用模型/模拟/结果控件，替换为一个空宿主和宿主外评估区：

```html
<div id="aiTrainingTrustReplyHost"></div>
<section id="aiTrainingEvaluationPanel" class="compose-panel trust-training-evaluation" hidden></section>
```

### 改动前真实页基线

- `app.js:130-223` 保存 live composed/AI 状态；`8730-9305` 实现真实来信专用生成、feedback、review、adopt；`9119-9204` 拼接当前 workbench DOM；`9419-9655` 把它插入详情并衔接人工编辑器。
- 当前真实 workbench 已复用 `.reply-workflow-*`、`.compose-*` 和 AI loading/feedback 样式，但 state/DOM/handler 全在 `app.js`，训练页无法直接复用。
- 既有 live 采用路径已持有 raw/rendered/baseline，必须作为完成 adapter 保留；公共组件不得复制 manual editor 或 send button。

目标 live 宿主只保留：

```html
<div data-trust-reply-live-host></div>
```

详情渲染后由 `app.js` 以当前 `inboundProcessingId` mount；切换详情/视图时 unmount。

### 全局脚本与已知故障

- `app.js:1406` 已声明顶层 `const $ = ...`。此前 inline preview 使用同名顶层 `const $`，经 `document.write` 重放后触发 `SyntaxError: Identifier '$' has already been declared`。
- 新组件必须采用幂等 IIFE，所有 selector helper 都在函数作用域且使用不冲突命名；`index.html` 以外链 `<script src="/trust-reply-workbench.js"></script>` 在 `app.js` 前加载一次。
- 禁止通过 HTML string 注入 `<script>`、禁止 `document.write`、禁止重复追加 script tag。

### 目标公共 mount 合同

```js
const instance = window.TrustReplyWorkbench.mount(hostElement, {
  mode: "SIMULATION", // or LIVE
  source: { sourceType: "TRAINING_MAIL", sourceId: 123 },
  contextPath,
  onUnauthorized: (response) => handleAuthResponse(response),
  onComplete: async (assembly) => {}
});

instance.unmount();
```

`bootstrap/fullGenerate/adjustItem/assemble/cancel` 的 endpoint、JSON/SSE parser 和取消逻辑全部定义在组件内部唯一 transport。宿主只传 contextPath 与鉴权回调；不得出现 `simulateTransport` 与 `liveTransport` 两套实现。组件不读取 `state.aiTraining`、live detail global 或 DOM 外部字段。

### 目标组件状态

```js
{
  instanceId,
  mode,
  source,
  sourceVersion,
  evidenceSetVersion,
  selectedFactIds,
  selectedModel,
  attemptTimeout: { mode, seconds, customSeconds },
  totalTimeout: { mode, seconds, customSeconds },
  requests: [{
    requestKey,
    requestText,
    coverage,
    availableHandlings,
    handling,
    instruction,
    versions: [{ versionId, answerText, claims, model, generationKind }],
    activeVersionId,
    lockedVersionId,
    requestSeq,
    pending,
    error
  }],
  generation,
  assembly,
  destroyed
}
```

所有 renderer 都从该 state 单向产生 DOM；DOM dataset 只放稳定 key/status，不把 answerText/instruction 序列化到 attribute。

### 现有样式/测试基线

- `styles.css:5360-5883` 已有 compose cards/rules/review；`5885-6115` 已有模型选择、loading、feedback；`6726-7151` 有训练页布局。新增类按样式契约追加，不改全局 `.button/.badge/.panel`。
- `src/test/js/trustReplyWorkbench.test.js` 检查 live workbench；`aiReplyLoadingFeedback.test.js` 检查 loading/SSE；`aiReplyReviewConfirmation.test.js` 检查 review/adopt。它们需改为从公共组件验证，不降低旧断言。
- Maven 已通过 `node --test src/test/js/*.test.js` 执行 JS 测试；仓库无 package.json，验收不用 npm script。

## 实现方案

### T1：先写公共组件合同和双挂载失败测试
- Governs: I-1～I-5、I-10。
- Styles: S-1、S-2、S-5。
- Files: 新增 `src/test/js/trustReplyWorkbenchSharedMount.test.js`，修改三个现有 JS tests。
- 先断言 global 只导出一个 namespace；脚本执行两次不 throw；源码无 `document.write`、无顶层 `const $/let $/var $`；训练/live mount 输出相同内部 `data-role` 树；DOM 中无 mode switch。
- 同页同时 mount 两个 fixture，调整/卸载 A 不改变 B；切换 source 后模拟旧响应，断言旧响应被丢弃。
- 用精确 mailRecordId fixture 断言 bootstrap payload；不得出现 contactId/latest fallback。

### T2：实现幂等组件壳、状态机与组件内统一 transport
- Governs: I-1～I-4、I-10～I-11。
- Styles: S-1、S-2、S-5。
- Files: 新增 `src/main/resources/static/trust-reply-workbench.js`、修改 `index.html` script 顺序。
- 用 `(function (global) { "use strict"; if (global.TrustReplyWorkbench) return; ... })(window);` 包裹；内部 helper 不泄漏。
- `mount` 校验 host/mode/source/contextPath/onComplete，创建实例 state、AbortControllers、listener registry；返回幂等 `unmount`。
- 组件内唯一 transport 只面向 01/02 公共 API；normal JSON 和 SSE 都复用同一 `contextPath`、auth/error parser，并通过可选 `onUnauthorized` 对接现有登录处理。SSE 结束/错误/取消必须关闭连接。
- 模型 selector 只使用 bootstrap `availableModels/defaultModel`；组件内只维护一份 label 映射。单次 TTL、总 TTL 与 custom 输入按现有 30～600 秒/10～7200 秒边界和 auto 语义渲染一次，两入口共享同一 state/校验/request mapping。
- full generation 显示真实 stage/message；使用 instanceId + request sequence 防 stale。错误保留可重试按钮，不清除已经完成的其他项。

### T3：实现逐项处理、版本和显式锁定
- Governs: I-5～I-7、I-11。
- Styles: S-2。
- Files: `trust-reply-workbench.js`、`trustReplyWorkbenchSharedMount.test.js`、`trustReplyWorkbench.test.js`。
- 每个 request card 显示序号、requestText、coverage、服务端允许的 handling、instruction、版本 selector、正文、单项“AI 调整”“锁定/解锁”。
- handling=`OMIT` 时调用普通同步 item operation，取得空 answer/claims、`generationKind=OMITTED` 的确定性版本；用户仍须显式锁定该版本。其他 handling 必须有 active version 才能锁定。锁定时 instruction/handling/version selector/AI 调整禁用，解锁后恢复。
- AI 调整 request 只上传 target requestKey/handling/instruction 和服务端版本身份；response 只 append 目标项 versions 并激活新版本。相同 answerText 仍可作为不同 version 保存。
- 修改事实选择使用确认门；确认后 cancel 全部 pending，清版本/锁/assembly，重新生成；取消则 DOM 恢复 state 中原选择。

### T4：实现服务端权威整合与模式完成回调
- Governs: I-7～I-9。
- Styles: S-3。
- Files: `trust-reply-workbench.js`、`app.js`、两个 review tests。
- summary 始终显示 `已锁定数/总项数`；只有 selector 条件满足时启用 assemble。请求上传 lockedItems，成功后保存完整 assembly object 并显示服务端 rawDraftText。
- 任一 item unlock/handling/version/fact/source 变化立即清除 assembly 和 final-action enablement。
- SIMULATION 最终按钮只调用训练 adapter；LIVE 最终按钮只调用 live adopt adapter。组件不认识 evaluation endpoint、manual editor selector 或 send endpoint。
- LIVE adapter 把同次 assembly 的 raw/rendered/draftHash 写入既有 manual editor/baseline，并保持现有 preflight/adopt confirmation；不得调用 send。

### T5：替换训练页为公共宿主并接训练评估
- Governs: I-2～I-4、I-8。
- Styles: S-1、S-4、S-5。
- Files: `index.html`、`app.js`、`styles.css`、shared mount test。
- 保留历史邮件 selector 与加载/空态；选择精确 mailRecordId 后卸载旧实例、清空旧 evaluation、mount SIMULATION。未选择时 host 显示说明，不生成。
- 删除训练专用模型 selector/模拟结果 DOM 的使用；模型选项来自公共 bootstrap，模型与双 TTL 控件由工作台内部统一渲染。
- `onComplete(assembly)` 只把服务端 assembly input/hash 暂存在当前训练实例并显示评估区。评分为三枚举，note 最长 1000；保存调用 03 endpoint。
- 保存时复验当前 instance/source/assembly identity；成功显示 evaluationId/time 并禁用重复提交，若继续改单项则隐藏评估区并使旧评估 input 失效。

### T6：替换真实详情 workbench 为公共宿主
- Governs: I-1～I-3、I-7～I-9。
- Styles: S-1～S-3、S-5。
- Files: `app.js`、`styles.css`、三个既有 JS tests。
- `renderUnmatchedDetail` 只输出 live host，不再生成内部 workbench HTML；详情装载成功后以 exact inboundProcessingId mount LIVE。
- 新开详情、刷新详情、离开 view、记录处理完成时先 unmount。保留纯人工编辑/发送 UI，不要求先打开工作台。
- live adopt callback 复用既有 raw/rendered adoption 与 baseline reset；采用后可继续人工编辑，最终发送仍走既有按钮和 server preflight/manual-rich。

### T7：删除前端重复调用路径但保留后端兼容 API
- Governs: I-1、I-10。
- Files: `app.js`、`index.html`、所有 JS tests。
- 移除/停止引用旧训练 simulate handler 和旧 live DOM renderer/handler；本计划可删除确定无调用的前端函数/state 字段，但不删除后端旧 endpoint/DTO。
- 用 `rg` 证明旧 DOM IDs、旧 handler names 无 active reference；若某 helper 仍被其他功能调用，保留并记录，不机械删除。
- `index.html` 只加载一次 `trust-reply-workbench.js`，位置在 `app.js` 前；对脚本运行 `node --check`。

## 变更文件清单

| # | 文件 | 动作 | 目的 |
|---:|---|---|---|
| 1 | `src/main/resources/static/trust-reply-workbench.js` | 新增 | 唯一工作台组件、状态机、DOM、transport/lifecycle |
| 2 | `src/main/resources/static/app.js` | 修改 | 两个 mount adapter、训练评估、live adopt、卸载 |
| 3 | `src/main/resources/static/index.html` | 修改 | 训练宿主/评估区、公共脚本顺序 |
| 4 | `src/main/resources/static/styles.css` | 修改 | 精确新增 S-1～S-5 样式 |
| 5 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 新增 | 同组件双挂载、隔离、精确来源、global 安全 |
| 6 | `src/test/js/trustReplyWorkbench.test.js` | 修改 | 逐项处理/版本/锁定/整合回归 |
| 7 | `src/test/js/aiReplyLoadingFeedback.test.js` | 修改 | 公共 SSE stage/error/cancel/stale 回归 |
| 8 | `src/test/js/aiReplyReviewConfirmation.test.js` | 修改 | 模拟评估与 live adopt/send 隔离回归 |

文件数：8。子系统数：2（公共前端组件；页面宿主/完成动作适配）。后端/API 文件：0；数据库：0。

## 验收标准

- I-1/I-10: 新脚本 `node --check`；执行两次无异常；无 `document.write` 和顶层 `$`；训练/live 内部 `data-role` 序列完全相同，模型/双 TTL 选项和校验完全相同。
- I-2: DOM/源码无 mode switch；非法 mode/source 组合 mount 失败；训练状态 badge 固定“模拟 · 不外发”，live 固定“正式回复”。
- I-3: 双实例测试证明 state/DOM/listener/abort 隔离；unmount 后晚到 bootstrap/SSE/item/assemble 均不落 DOM。
- I-4: 训练 bootstrap payload sourceId 等于选中的 mailRecordId；切换相同 contact 的两封邮件分别得到各自 sourceVersion，无 latest fallback。
- I-5: 只调整 requestKey B 时 A/C 的 instruction/version IDs/locked answer byte-for-byte 不变；旧 requestSeq response 被丢弃。
- I-6: 改 fact 取消时 state 不变；确认时所有 version/lock/assembly 清空并新 bootstrap；旧 evidence response 不可采用。
- I-7: assemble 前置 selector 全覆盖；前端源码不存在 answers join/dedupe/truncate/LLM rewrite；预览等于 API rawDraftText。
- I-8: SIMULATION completion 只调用 evaluation adapter；LIVE completion 只调用 adopt adapter；二者都不调用 manual-rich/send，发送仍需现有按钮。
- I-9: live adopt 保留 raw/rendered/draftHash identity；从预览 DOM 修改文字不改变采用 payload；人工编辑后最终发送仍由既有 server 校验。
- I-11: SSE stage/message 可见且 aria-live；错误可重试；取消关闭流；不显示伪百分比。
- S-1～S-5: 所有新 DOM class 均在样式契约有定义或明确复用现有类；无 inline style；960/640 两断点无横向溢出。
- 定向：

```bash
node --check src/main/resources/static/trust-reply-workbench.js
node --check src/main/resources/static/app.js
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js src/test/js/trustReplyWorkbench.test.js src/test/js/aiReplyLoadingFeedback.test.js src/test/js/aiReplyReviewConfirmation.test.js
```

- 全量：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
node --test src/test/js/*.test.js
```

## 人工验收清单

### A-1: 两入口视觉与交互同构
- 前置条件: 准备内容相同的一封训练历史邮件与一条 live inbound；桌面宽度 ≥ 1280px。
- 操作步骤: 分别打开训练页和真实详情；展开可信回复工作台；对比顶部、事实区、逐项卡、版本区、整合摘要。
- 预期结果: 内部顺序、控件、间距、字体、卡片完全一致；仅 mode badge、说明和最终按钮文案不同；页面无模式切换控件。
- 覆盖: I-1、I-2、S-1～S-3；需求核心可观察结果。

### A-2: 精确历史邮件切换
- 前置条件: 同一联系人有历史邮件 A/B，正文与请求不同。
- 操作步骤: 训练页选 A，等待初稿；再选 B；随后让 A 的旧请求晚到（开发工具 throttling）。
- 预期结果: B 的 request/facts/sourceVersion 出现；A 的内容不会覆盖 B；无“联系人最新一封”回退。
- 覆盖: I-3、I-4；interaction `training select → unmount → bootstrap`。

### A-3: 单项 AI 调整隔离
- 前置条件: 一封含三项请求的邮件，三项都有初始版本。
- 操作步骤: 记录第 1/3 项版本 ID 和正文；只在第 2 项输入“更简短但保留日期”并点 AI 调整。
- 预期结果: 仅第 2 项新增并激活版本；第 1/3 项版本、正文、锁定状态逐字不变；第 2 项可切回旧版本。
- 覆盖: I-5、S-2。

### A-4: PARTIAL/UNSUPPORTED 处理方式约束
- 前置条件: 邮件包含 GROUNDED、PARTIAL、UNSUPPORTED 各一项。
- 操作步骤: 展开三个 handling selector；分别尝试服务端不允许的选项；为 UNSUPPORTED 选择 acknowledgement，再生成版本。
- 预期结果: selector 只显示服务端允许矩阵；不存在非法选项；无依据项不得生成事实性回答，只能安全确认待补或省略。
- 覆盖: I-5、01/02 跨计划 handling 合同。

### A-5: 显式锁定与服务端整合
- 前置条件: 两项答案文本故意相同，另有至少三项，使总项数 > 4；其中一项选择 OMIT。
- 操作步骤: 逐项锁定全部项目，包括显式锁定 OMIT 项的 OMITTED 版本；观察计数；点整合；对比 raw preview。
- 预期结果: 任一项目未锁时整合 disabled；锁完后服务端返回预览；OMIT 项不进入正文，相同文本出现两次，全部非省略项目都保留，顺序与请求一致；锁定正文逐字未变。
- 覆盖: I-7、I-9；interaction `locks → assemble → raw preview`。

### A-6: 解锁/改事实使整合失效
- 前置条件: 已有合法整合预览。
- 操作步骤: 解锁任一项，确认预览清空；重新整合后改事实选择，先取消确认，再重复并确认。
- 预期结果: 解锁立即禁用最终动作；取消改事实时旧 state 完整恢复；确认后全部版本/锁/预览清空并重新生成，新 evidence 前旧 assembly 不可使用。
- 覆盖: I-6、I-7。

### A-7: 训练完成只进入评估
- 前置条件: 训练工作台已有 current assembly。
- 操作步骤: 点“完成模拟并评估”；选“需要改进”、填备注、保存；观察网络请求与邮件数据。
- 预期结果: 只显示并提交训练评估；没有 manual-rich/preflight/send 请求，没有 SMTP 和 outbound mail；改单项后旧评估区立即失效。
- 覆盖: I-8、S-4；interaction `assembly → training onComplete → evaluation`。

### A-8: 真实完成只采用、不自动发送
- 前置条件: live 工作台已有 current assembly，人工编辑器中有旧草稿。
- 操作步骤: 点“采用到人工回复”，确认；检查编辑器；等待 10 秒且不点发送；随后手动编辑并点既有发送。
- 预期结果: 编辑器被同次 rendered/raw adoption 更新，基线正确；前 10 秒无发送；人工编辑可继续；点击既有发送后才执行 preflight/manual-rich。
- 覆盖: I-8、I-9；interaction `assembly → live adopt → manual send`。

### A-9: 纯人工回复回归
- 前置条件: 新开一条 live inbound，不展开/不运行工作台。
- 操作步骤: 直接在人工富文本编辑器填写合法回复并发送。
- 预期结果: 与改造前相同，可正常 preflight 并发送；不要求工作台 sourceVersion、锁定或 draftHash。
- 覆盖: must-not-change 人工发送、I-8。

### A-10: 加载、取消、错误和 late response
- 前置条件: 浏览器网络设置 Slow 3G；LLM fixture 可返回一次 500。
- 操作步骤: 开始 full generation 后取消；重试并让第 2 项失败；再次重试第 2 项；期间切换页面。
- 预期结果: stage/message 真实更新且无伪百分比；取消关闭请求；单项错误不抹掉其他项；重试仅作用目标项；离页后无晚到 DOM 更新/控制台异常。
- 覆盖: I-3、I-5、I-11。

### A-11: 桌面/平板/手机视觉
- 前置条件: DevTools 分别设为 1440×900、900×1000、390×844。
- 操作步骤: 查看长 request、长 instruction、10 个版本和长整合正文；键盘 Tab 遍历；启用 reduced motion。
- 预期结果: 1440 为左右双栏且摘要 sticky；900 为单栏且摘要 static；390 无横向滚动，controls/actions/rating 单列、按钮全宽；长文本换行；focus 清晰；status 有文字；reduced motion 无 transition。
- 覆盖: S-1～S-5、I-11。

### A-12: 重复脚本故障回归
- 前置条件: 在测试 fixture 中先加载公共脚本，再重复执行一次同一脚本，随后加载 `app.js`。
- 操作步骤: 打开控制台并分别 mount 训练/live。
- 预期结果: 无 `Identifier '$' has already been declared`、无 `Document.write` 错误、无重复 listener；两个实例均正常工作。
- 覆盖: I-10；已知故障回归。
