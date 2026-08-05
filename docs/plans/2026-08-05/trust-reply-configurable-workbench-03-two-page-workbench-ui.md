# 可信回复工作台 03：双页切换前端与全链路透传开发计划

> 使用 `create-p` 编写。前置：计划 01 的摘要—事实矩阵与计划 02 的可选 frame 后端契约已执行并通过独立验证。本计划落实 2026-08-05 已确认的“双页切换”预览，不使用左右分栏。

## 需求描述

### 可观察结果

可信回复工作台改为两个横向切换页面：

1. **摘要与事实**：按原邮件顺序展示摘要卡片；每张卡片直接展示已绑定事实，可添加/删除。已被其他摘要使用的事实显示“已用于摘要 N”并禁用。
2. **回复框架与整合**：选择尊语、开场白、致谢语、结束语，查看配置预览和服务端整合结果；从此页完成“服务端整合”和“采用到人工回复/完成模拟并评估”。

顶部 tab、页内“下一页/上一页”按钮均可切页。页面不同时并排展示。事实变化会清空全部旧生成/锁定版本并重新 bootstrap；frame 变化只清除 assembly，保留摘要版本。

### 必须保持不变

- `TrustReplyWorkbench.mount` 仍是唯一共享组件；训练与正式回复 adapter 只传固定 mode/source/callback，不复制 UI/state/transport。
- SIMULATION 只能完成评估，LIVE 只能采用到人工回复；不能在 UI 内切换 mode 或 source。
- sourceVersion、evidenceSetVersion、requestKey、resolvedVersionId、locked snapshot、optimistic stateVersion 和服务端 assemble 仍是 authority。
- active version 与 resolved version 继续分离；修改 handling/instruction/version 仍按现有规则解锁并持久化。
- 自动补齐只处理冻结的 grounded missing allowlist；PARTIAL/UNSUPPORTED 仍要求显式人工处理。
- instruction 输入不得在每次 `input` 事件重建整棵 DOM；光标、IME 和未完成输入必须保持稳定。
- 翻译、超时、取消、生成进度、stale 提示、state save、assembly adopt、训练评估和正式发送复验继续存在。
- 最终采用只能使用最新服务端 assemble response；本地配置预览不是可发送 authority。
- `app.js` 的训练与 LIVE 两个 mount 继续共用同一组件；同页重挂载/销毁不得串状态。
- 不修改其他页面的全局 tabs、segmented control、button、compose panel 样式。

### 范围外

- 不做左右分栏、拖拽事实、事实排序、模糊搜索或分页加载。
- 不在工作台内编辑 QA 事实正文或 reply snippet 内容。
- 不新增前端框架、构建工具或路由系统；继续使用原生 JS/CSS。
- 不持久化当前页、打开的事实选择器、翻译结果、active-only version 或本地预览。
- 不提供跨摘要“移动事实”一键操作；先删除释放，再在另一摘要添加。
- 不做逐摘要局部保留旧 version；事实矩阵任一变化都会明确提示并清空全部旧版本。
- 不改变邮件编辑器被人工修改后的发送规则；编辑后仍不携带可信 assembly snapshot。

## 关键不变量

### Invariant I-1：两个页面共享一份组件状态，不形成两个工作台

- Rule：`activePage` 仅决定 DOM 可见性；requests、fact mapping、frame snapshot、versions、locks、assembly、timeouts 和 model 均存于同一 `createInstance` state。切页不得 bootstrap、复制或丢失业务状态。
- Applies to：renderMarkup、page action handlers、mount/destroy。
- Violation consequence：两个页面各自维护事实/版本，返回时状态漂移或重复请求。
- 来源：`K-shared-workbench-fixed-mode-host-adapter`

### Invariant I-2：事实占用在前端可见，服务端仍最终校验

- Rule：客户端从 `requests[].factRuleIds` 派生 `factOwnerById`；已占用事实在其他摘要 picker 中 disabled 并标 owner。payload 始终发送完整 `requestFactSelections`，不发送 flat `requestedFactIds`。客户端禁用只是 UX；422 duplicate/invalid 由服务端处理并刷新 canonical 状态。
- Applies to：事实 chips/picker、bootstrap/generation/state/assemble payload。
- Violation consequence：用户看不出事实已被占用，或可通过 DOM 篡改绕过唯一性。
- 来源：`K-request-facts-not-flat-pool` + original

### Invariant I-3：事实变化与 frame 变化采用不同失效边界

- Rule：事实 add/remove 前若存在 active/resolved versions 或 assembly，必须确认“将清空全部已生成/锁定回答”；确认后先按当前 optimistic version 删除旧 durable state，再 resetVersions/bootstrap。frame select change 不 resetVersions，只 invalidateAssembly；若已有 lockedItems，则以同一 locked snapshots 保存新 frame。
- Applies to：fact actions、frame change handler、state save、render button states。
- Violation consequence：事实改变后旧证据版本继续使用，或改问候导致所有回答丢失。
- 来源：`K-trust-reply-resolved-version-single-source`、`K-workbench-lock-replay-needs-dedicated-state-store`

### Invariant I-4：配置预览只用于展示，服务端 assembly 才可采用

- Rule：frame 页可用服务端返回的 option content + 当前 resolved answerText 绘制“配置预览（未整合）”；该文本不得写入 `state.assembly`、adopt callback 或最终发送。只有 assemble response 的 source/evidence/frame identities 与当前 state 全部相同，完成按钮才启用。
- Applies to：renderFramePreview、assemble、complete、app.js adoption。
- Violation consequence：客户端拼接正文绕过 placeholder 渲染、snippet stale 和最终服务端重验。
- 来源：`K-manual-frame-three-consumers` 的服务端组装边界

### Invariant I-5：assembly snapshot 必须完整透传到两个最终复验入口

- Rule：训练评估 payload、LIVE adopt snapshot 和未编辑人工发送 payload 必须复制 canonical `requestFactSelections` 与 `frameSnapshot`，连同 source/evidence/lockedItems 交服务端重新 assemble；不得从 claims 或 `canonicalFactIds` 反推矩阵，也不得丢 frame 后回退 default。
- Applies to：`saveAiTrainingEvaluation`、`buildTrustReplyAssemblySnapshot`、`adoptTrustReplyAssembly`、manual rich send、`AiTrainingController.toDomain`。
- Violation consequence：工作台预览正确，评估或正式发送却使用另一组事实/默认框架。
- 来源：original + `K-ai-reply-evidence-version-deterministic`

### Invariant I-6：resolved version 是摘要正文唯一来源

- Rule：frame 本地预览、state payload 和 assemble payload只读取 `resolvedVersionId` 对应的 version；active 未采用版本不得进入预览或整合。handling/instruction 改变后沿用既有 unlock/save 顺序。
- Applies to：preview、readiness、serializeResolvedVersion、assembly snapshot。
- Violation consequence：用户尚未采用的草稿混入最终正文。
- 来源：`K-trust-reply-resolved-version-single-source`

### Invariant I-7：输入稳定性与无障碍切页

- Rule：instruction 的 `input` 事件只更新 state 与必要局部 DOM，不调用全量 render。tab 使用 `role=tablist/tab/tabpanel`、实例唯一 id、`aria-selected/aria-controls`、hidden；支持点击、上一页/下一页及 Left/Right/Home/End 键盘导航，切页后焦点落在目标 tab/heading。
- Applies to：DOM contract、event delegation、render/syncInstructionUi。
- Violation consequence：输入丢焦、中文 IME 中断，或键盘/读屏用户无法理解两个页面。
- 来源：`K-state-input-no-per-keystroke-innerhtml`

### Invariant I-8：批量生成继续复用逐项路径

- Rule：frame 页点击“服务端整合”时，现有 readiness 逻辑只对 frozen grounded missing requestKeys 逐项调用 ADJUST_ITEM；不调用 FULL_DRAFT，不覆盖已锁定 PARTIAL/UNSUPPORTED 决策。所有逐项请求带完整 fact matrix。
- Applies to：computeReadiness、generateMissingGrounded、assemble。
- Violation consequence：整体生成覆盖人工回答或重新引入扁平事实池。
- 来源：`K-assembly-fill-missing-allowlist`、`K-aggregate-generation-reuse-item-path`

## 样式契约

### 当前 DOM 基线与保留节点

- 当前根结构位于 `trust-reply-workbench.js:1082-1102`：`details.trust-reply-workbench > summary.reply-workflow-summary + .reply-workflow-content`。
- `.reply-workflow-content` 当前依次包含 `.trust-reply-toolbar`、`[data-role=status]`、`.trust-reply-layout`；layout 内是 `aside.trust-reply-summary` 与 `.trust-reply-item-list`。
- 摘要卡片 `article.trust-reply-item`、`.trust-reply-item-head`、`.trust-reply-item-controls`、`.trust-reply-field`、`.trust-reply-answer`、`.trust-reply-item-actions` 的 DOM、data-role 和交互语义保留。
- `.trust-reply-layout` 的“摘要横条 + item list”结构退役；整合 summary 移入第 2 页，不再和摘要卡片同屏。

### 新 DOM 契约

```text
details.trust-reply-workbench
└── .reply-workflow-content
    ├── .trust-reply-toolbar
    ├── .trust-reply-page-nav[role=tablist]
    │   ├── button.trust-reply-page-tab[role=tab][data-page=facts] > .trust-reply-page-step
    │   └── button.trust-reply-page-tab[role=tab][data-page=frame] > .trust-reply-page-step
    ├── .ai-reply-feedback[data-role=status]
    ├── section.trust-reply-page[role=tabpanel][data-page-panel=facts]
    │   ├── .trust-reply-page-head
    │   ├── .trust-reply-item-list
    │   │   └── article.trust-reply-item
    │   │       └── .trust-reply-fact-section
    │   │           ├── .trust-reply-fact-chip-list > .trust-reply-fact-chip
    │   │           └── .trust-reply-fact-picker > .trust-reply-fact-picker-option
    │   └── .trust-reply-page-actions
    └── section.trust-reply-page[role=tabpanel][data-page-panel=frame][hidden]
        ├── .trust-reply-page-head
        ├── .trust-reply-frame-panel.compose-panel
        │   ├── .trust-reply-frame-grid
        │   └── .trust-reply-frame-preview
        │       ├── .trust-reply-preview-state[data-state=LOCAL|CURRENT|STALE]
        │       └── .trust-reply-summary[data-role=summary]
        └── .trust-reply-page-actions
```

### 必须复用的现有规则与 token

- Root/layout：`.trust-reply-workbench`（styles.css:7142）、`.reply-workflow-content`（7146）、`.trust-reply-toolbar`（7154）。
- Card/controls：`.trust-reply-item`（7339）、`.trust-reply-item-head`（7366）、`.trust-reply-item-controls`（7424）、`.trust-reply-field`（7431）、`.trust-reply-answer`（7484）、`.trust-reply-summary`（7546）。
- Global primitives：`.button`（587）、`.button.primary/.secondary/.danger`（623/637/647）、`.compose-panel`（5374）；不复制其基础尺寸与色值。
- Tokens：`--primary/--primary-light/--primary-tint`、`--panel-bg/--panel-border/--surface/--line/--border`、`--text-main/--text-muted/--text-sidebar`、`--success/--warning/--error/--info` 及对应背景/边框、`--radius-sm/md/lg`、`--shadow-sm/md`、`--font-body`。
- 新规则必须全部以 `.trust-reply-workbench` 为 scope；不得新增裸 `button/select/label/article/section` reset，不得修改 `.tabs/.tab` 或 `.mailbox-segmented-control` 以免影响其他页面。

### Style S-1：双页 tab 与 panel

新增 class 的完整 CSS：

```css
.trust-reply-workbench .trust-reply-page-nav {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--panel-border);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.trust-reply-workbench .trust-reply-page-tab {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-width: 0;
  min-height: 36px;
  padding: 6px 12px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-muted);
  font-family: var(--font-body);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
  cursor: pointer;
  transition: background .15s ease, border-color .15s ease, color .15s ease, box-shadow .15s ease;
}

.trust-reply-workbench .trust-reply-page-tab:hover {
  border-color: var(--border);
  color: var(--text-main);
  background: var(--panel-bg);
}

.trust-reply-workbench .trust-reply-page-tab[aria-selected="true"] {
  border-color: rgba(var(--primary-rgb), .18);
  color: var(--primary);
  background: var(--panel-bg);
  box-shadow: var(--shadow-sm);
}

.trust-reply-workbench .trust-reply-page-tab:focus-visible {
  outline: 2px solid rgba(var(--primary-rgb), .35);
  outline-offset: 2px;
}

.trust-reply-workbench .trust-reply-page-tab:disabled {
  opacity: .55;
  cursor: not-allowed;
}

.trust-reply-workbench .trust-reply-page-step {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  flex: none;
  border-radius: 50%;
  background: rgba(15, 23, 42, .06);
  color: inherit;
  font-size: 10px;
  font-weight: 700;
}

.trust-reply-workbench .trust-reply-page-tab[aria-selected="true"] .trust-reply-page-step {
  background: var(--primary-light);
}

.trust-reply-workbench .trust-reply-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.trust-reply-workbench .trust-reply-page[hidden] {
  display: none;
}
```

### Style S-2：页头与上一页/下一页动作

新增 class 的完整 CSS：

```css
.trust-reply-workbench .trust-reply-page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 2px 2px 0;
}

.trust-reply-workbench .trust-reply-page-head h3 {
  margin: 0;
  color: var(--text-main);
  font-size: 14px;
  font-weight: 600;
}

.trust-reply-workbench .trust-reply-page-head small {
  display: block;
  margin-top: 3px;
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.45;
}

.trust-reply-workbench .trust-reply-page-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 2px;
}
```

页内按钮继续使用现有 `.button.primary/.secondary`，不得新增第三套按钮样式。

### Style S-3：摘要卡内事实 chips 与 picker

新增 class 的完整 CSS：

```css
.trust-reply-workbench .trust-reply-fact-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface);
}

.trust-reply-workbench .trust-reply-fact-section > strong {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 600;
}

.trust-reply-workbench .trust-reply-fact-chip-list {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.trust-reply-workbench .trust-reply-fact-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 100%;
  min-height: 28px;
  padding: 3px 5px 3px 9px;
  border: 1px solid rgba(var(--primary-rgb), .18);
  border-radius: 999px;
  background: var(--primary-light);
  color: var(--primary);
  font-size: 11px;
  font-weight: 600;
}

.trust-reply-workbench .trust-reply-fact-chip > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trust-reply-workbench .trust-reply-fact-chip > button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  flex: none;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: currentColor;
  cursor: pointer;
}

.trust-reply-workbench .trust-reply-fact-chip > button:hover {
  background: var(--primary-tint);
}

.trust-reply-workbench .trust-reply-fact-chip > button:focus-visible {
  outline: 2px solid rgba(var(--primary-rgb), .35);
  outline-offset: 1px;
}

.trust-reply-workbench .trust-reply-fact-chip > button:disabled {
  opacity: .55;
  cursor: not-allowed;
}

.trust-reply-workbench .trust-reply-fact-picker {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  padding-top: 2px;
}

.trust-reply-workbench .trust-reply-fact-picker[hidden] {
  display: none;
}

.trust-reply-workbench .trust-reply-fact-picker-option {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
  min-height: 44px;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--panel-bg);
  color: var(--text-main);
  text-align: left;
  cursor: pointer;
  transition: border-color .15s ease, background .15s ease, box-shadow .15s ease;
}

.trust-reply-workbench .trust-reply-fact-picker-option > span {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.trust-reply-workbench .trust-reply-fact-picker-option > span > strong {
  color: var(--text-main);
  font-size: 11px;
  font-weight: 600;
}

.trust-reply-workbench .trust-reply-fact-picker-option > span > em {
  display: -webkit-box;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 10px;
  font-style: normal;
  font-weight: 400;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.trust-reply-workbench .trust-reply-fact-picker-option > small {
  flex: none;
  color: var(--text-muted);
  font-size: 10px;
  line-height: 1.45;
  white-space: nowrap;
}

.trust-reply-workbench .trust-reply-fact-picker-option:not(:disabled):hover {
  border-color: var(--primary);
  background: var(--primary-light);
  box-shadow: var(--shadow-sm);
}

.trust-reply-workbench .trust-reply-fact-picker-option:focus-visible {
  outline: 2px solid rgba(var(--primary-rgb), .35);
  outline-offset: 1px;
}

.trust-reply-workbench .trust-reply-fact-picker-option[data-state="selected"] {
  border-color: var(--primary);
  background: var(--primary-light);
}

.trust-reply-workbench .trust-reply-fact-picker-option[data-state="used"] {
  border-style: dashed;
  background: rgba(15, 23, 42, .035);
}

.trust-reply-workbench .trust-reply-fact-picker-option:disabled {
  opacity: .62;
  cursor: not-allowed;
  box-shadow: none;
}
```

`data-state="used"` 的 `<small>` 必须输出“已用于摘要 N”；`selected` 输出“已选择”；pending disabled 输出“保存中”，不能只靠 opacity 表意。

### Style S-4：回复框架、配置预览与服务端状态

新增 class 的完整 CSS：

```css
.trust-reply-workbench .trust-reply-frame-panel {
  gap: 14px;
}

.trust-reply-workbench .trust-reply-frame-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.trust-reply-workbench .trust-reply-frame-grid .trust-reply-field select {
  min-height: 36px;
}

.trust-reply-workbench .trust-reply-frame-preview {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.trust-reply-workbench .trust-reply-preview-state {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 2px 8px;
  border: 1px solid var(--info-border);
  border-radius: 999px;
  background: var(--info-bg);
  color: #0369a1;
  font-size: 10px;
  font-weight: 600;
}

.trust-reply-workbench .trust-reply-preview-state[data-state="CURRENT"] {
  border-color: var(--success-border);
  background: var(--success-bg);
  color: var(--success);
}

.trust-reply-workbench .trust-reply-preview-state[data-state="STALE"] {
  border-color: var(--warning-border);
  background: var(--warning-bg);
  color: var(--warning);
}

.trust-reply-workbench .trust-reply-frame-preview .trust-reply-summary {
  align-items: center;
  padding: 0;
  border: 0;
  box-shadow: none;
}
```

`LOCAL` 文案固定“配置预览 · 尚未服务端整合”，`CURRENT` 固定“服务端整合完成”，`STALE` 固定“配置已变化 · 请重新整合”。

### Style S-5：retire/复用边界

- 删除 `.trust-reply-layout` 定义（现有使用点只有 `trust-reply-workbench.js` 的 shell/markup 与 `styles.css:7325`），新 page panel 替代它。
- 删除 `.trust-reply-toolbar .compose-rule-list[data-role="facts"]`、其 muted 子规则和 `.trust-reply-fact-option` 全部规则（现有 DOM 使用点只有 `renderToolbar`）；toolbar 不保留隐藏的第二事实入口。
- `.trust-reply-summary` 不就地改全局定义；仅用 S-4 的后代 selector 在 frame preview 内派生。
- `.trust-reply-item/.trust-reply-item-head/.trust-reply-item-controls/.trust-reply-field/.trust-reply-answer/.trust-reply-item-actions` 均不改 selector 定义，只在现有 article 内插入 S-3 DOM。
- 全局 `.tabs/.tab`、`.mailbox-segmented-control`、`.button`、`.compose-panel` 无 diff。

### Style S-6：窄屏与 reduced motion

新增 media 规则的完整 CSS：

```css
@media (max-width: 640px) {
  .trust-reply-workbench .trust-reply-page-tab {
    min-height: 44px;
    padding-inline: 6px;
    white-space: normal;
  }

  .trust-reply-workbench .trust-reply-page-head {
    align-items: flex-start;
  }

  .trust-reply-workbench .trust-reply-frame-grid,
  .trust-reply-workbench .trust-reply-fact-picker {
    grid-template-columns: minmax(0, 1fr);
  }

  .trust-reply-workbench .trust-reply-page-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .trust-reply-workbench .trust-reply-page-actions .button {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .trust-reply-workbench .trust-reply-page-tab,
  .trust-reply-workbench .trust-reply-fact-picker-option {
    transition: none;
  }
}
```

### 禁止改变的视觉行为

- 覆盖状态仍用 GROUNDED success、PARTIAL warning、UNSUPPORTED error；不能仅靠颜色表达。
- locked 卡片仍保留现有绿色边框/背景；pending/error/status banner 仍使用现有组件。
- model/TTL/cancel 控件继续位于 toolbar；全局事实 chips 从 toolbar 删除，不能遗留第二个选择入口。
- frame 本地预览必须标“未整合/配置已变化”；服务端 assembly 才标“整合完成”，采用按钮只在后者启用。

## 现状审计

### 前端状态与 transport

- `createInstance` 当前只有全局 `selectedFactIds`；`requestFromCoverage` 已保存每项 `factRuleIds`，但 UI/请求未使用其分配语义。
- bootstrap/generation/state/delete/assemble 均发送 `requestedFactIds`；`applyBootstrap` 从 canonical/suggested flat IDs 回填。
- `onFactChange` 读取 toolbar checkbox，确认后删除 durable state、reset 全部版本并 bootstrap；此失效语义可复用，但事件需迁移到摘要 card 的 add/remove。
- `renderToolbar` 当前渲染全局事实 checkbox；`renderRequest` 不展示事实；`renderSummary` 与 item list 同屏。
- `render()` 会重建 host；`onInput` 对 instruction 已有局部 `syncInstructionUi` 快速路径，必须保留。
- state schema 字面量当前为 v1；计划 01/02 后必须更新为 v3。

### 下游 read/write paths

- SIMULATION complete：`app.js.saveAiTrainingEvaluation` 从 assembly 读取 source/evidence/requestedFactIds/itemVersions，POST `/api/ai-training/simulate/evaluations`。
- `AiTrainingController.toDomain` 将其构造为 `TrustReplyAssembleRequest`，`AiTrainingEvaluationService` 再调用 workbench assemble 后保存评估。
- LIVE complete：`buildTrustReplyAssemblySnapshot` 复制 source/evidence/requestedFactIds/lockedItems；`adoptTrustReplyAssembly` 保存 snapshot 并把 rendered body 放入编辑器。
- 正式人工发送：编辑器正文与 adopted baseline 完全相同时才携带 `trustReplyAssembly`；`PendingMailOperationService` 再 assemble 并比对 raw/rendered 后外发。
- 若 app.js 或 AiTrainingController 任一处漏传 matrix/frame，最终复验会回退 legacy flat/default，形成预览与发送双事实源。

### 静态资源与测试基线

- `index.html` 当前加载 `styles.css`、`trust-reply-workbench.js`、`app.js` 的日期 query；计划完成需统一更新 cachebuster。
- `batchSendTaskConsoleVisualFix.test.js` 对 query 字符串有精确断言，必须同步。
- `trustReplyWorkbenchSharedMount.test.js` 是共享组件主要行为测试，现有 bootstrap fixture、payload assertions、durable lock、assemble、输入稳定、双 mount/销毁覆盖需迁移。
- `aiReplyReviewConfirmation.test.js` 静态断言训练 payload 和 LIVE assembly snapshot 保存 flat IDs；需改为完整 mapping/frame。
- `AiTrainingSimulateTest` 覆盖 evaluation HTTP→domain 转换；需断言新字段原样进入 `TrustReplyAssembleRequest`。

## 实现方案

### Task 1：重构组件状态与 canonical payload helper（I-1～I-3、I-5、I-6）

文件：`src/main/resources/static/trust-reply-workbench.js`

1. state：
   - 删除业务 authority `selectedFactIds`，只在需要兼容展示并集时通过 helper 派生；
   - 新增 `activePage="facts"`、`frameOptions`、`frameSnapshot`、`frameSavePending`、每 request 的 `factPickerOpen`；
   - assembly 保存服务端回传 canonical matrix/frame。
2. 新增纯 helper：
   - `serializeRequestFactSelections()` 按 request canonical 顺序返回完整矩阵，包括空列表；
   - `factOwnerById()`/`availableFactsFor(request)`；
   - `sameFrameSnapshot()` 与 `currentResolvedVersions()`。
3. applyBootstrap 只从 `requestCoverage[].factRuleIds`/`requestFactSelections` 建 state；响应不一致时 fail closed，不用 flat 并集覆盖 request。
4. bootstrap/generation/state/assemble payload 发送 matrix；bootstrap/state/assemble 发送 frame snapshot；不再发送 `requestedFactIds`，避免后端 ambiguous 错误。
5. state schema 改为 `trust-reply-workbench-state-v3`；restore 支持 `RESTORED` 和 `FRAME_STALE`：后者恢复 locks、显示 frame warning、保持 assembly=null。

### Task 2：实现双页 DOM 与交互（I-1～I-4、I-6、I-7、S-1～S-4）

文件：`src/main/resources/static/trust-reply-workbench.js`

1. 按“样式契约”重写 `renderShell/renderMarkup`；tab/tabpanel id 使用 `state.instanceId` 前缀，避免训练和 LIVE mount ID 冲突。
2. `setActivePage(page, focusTarget)` 只更新 activePage/render；不 bootstrap。click 处理 tab、下一页、上一页；keydown 支持 Left/Right/Home/End。
3. `renderRequest` 在 header/controls 之间加入“对应事实”：
   - selected facts 以 displayName chip 展示，可删除；
   - “+ 添加事实”展开本卡 picker；
   - option 展示 displayName、answerBody 摘要和“可添加/已选择/已用于摘要 N”；
   - 所有文本继续经 `escapeText`。
4. fact add/remove：
   - DOM 操作前检查 owner；
   - 存在 versions/locks/assembly 时弹一次明确确认；取消不改变 state/DOM；
   - 确认后用旧 snapshot 删除 durable state，更新完整矩阵，resetVersions，再 bootstrap 获取服务端 canonical mapping/version；
   - 请求 pending 时禁用全部 fact actions，失败恢复旧 mapping 并显示稳定错误。
5. frame 页渲染四个 native select，每个含“不使用”+同类型 options；label 使用用户确认的中文，option value 只存 ID。
6. frame change：保存 previous snapshot，应用选择并 invalidateAssembly；已有 locks 时 PUT state。失败则回滚 snapshot；成功不改变 request versions。
7. 配置预览从 current resolved answers 与服务端 option content派生，明确标 LOCAL。服务端 assemble response identity 匹配后标 CURRENT 并展示 raw preview；complete 只认 CURRENT assembly。

### Task 3：迁移现有生成、锁定和整合控件到对应页面（I-3、I-4、I-6、I-8、S-2、S-4、S-5）

文件：`src/main/resources/static/trust-reply-workbench.js`

1. model/TTL/cancel 保留 toolbar；删除 toolbar `[data-role=facts]` 与全局 `.trust-reply-fact-option` checkbox 入口。
2. 摘要页保留 handling、instruction、version、translate、generate/adopt/unlock 全部现有行为。
3. `renderSummary` 移到 frame preview；progress/readiness/assemble/complete 逻辑不变，但 complete 增加 frame identity 校验。
4. `generateMissingGrounded` 继续冻结 requestKey allowlist并逐项 ADJUST_ITEM；每次携带同一 matrix，state save 完成后才 assemble。
5. frame change、fact change、version change、instruction change各自调用正确的 invalidation helper；不得用全量 reset 代替 frame-only invalidation。
6. stale errors 扩展 `TRUST_REPLY_FRAME_STALE`：保留 locked answers，切到 frame 页并要求刷新 frame options；source/evidence stale 继续全量 reset/bootstrap。

### Task 4：实现 scoped 双页样式（I-7、S-1～S-6）

文件：`src/main/resources/static/styles.css`

1. 退役 `.trust-reply-layout` 和 toolbar global facts 的专用布局规则；不删除仍被其他组件使用的 `.compose-rule-list` 基础规则。
2. 新增契约列出的 page nav/tab/page head/page actions/fact section/chip/picker/frame grid/preview scoped selectors。
3. 所有颜色、border、radius、shadow、font 使用现有 token；复用 `.button`、`.compose-panel`、既有 request card/coverage/locked styles。
4. 定义 hover/focus-visible/selected/disabled/used/pending/local/current/stale 状态；owner 文本和 aria 属性补足非颜色提示。
5. 更新 640px 和 reduced-motion media；不修改全局 `.tabs/.tab`、`.mailbox-segmented-control` 或 `.button` 基础规则。

### Task 5：完整透传到训练评估与正式发送（I-4、I-5）

文件：

- `src/main/resources/static/app.js`
- `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt`

1. `saveAiTrainingEvaluation` 发送 `requestFactSelections`、`frameSnapshot`、locked itemVersions；不再从 `requestedFactIds || canonicalFactIds` 回退。
2. `buildTrustReplyAssemblySnapshot` 深复制 matrix（包括每个 factRuleIds list）和 frame selection/version；继续复制 locked item 完整 identity。
3. `adoptTrustReplyAssembly` 的 qaRuleIds 仅保留用于现有 UI/audit并集；`trustReplyAssembly` 保存完整权威配置。
4. 未编辑人工发送继续携带完整 snapshot；编辑后不携带 snapshot 的现有边界不变。
5. `AiTrainingEvaluationHttpRequest` 增加矩阵/frame HTTP DTO，并原样映射到 `TrustReplyAssembleRequest`；缺失字段保留旧客户端兼容 defaults。

### Task 6：cachebuster 与自动测试（I-1～I-8、S-1～S-6）

文件：

- `src/main/resources/static/index.html`
- `src/test/js/trustReplyWorkbenchSharedMount.test.js`
- `src/test/js/trustReplyWorkbench.test.js`
- `src/test/js/aiReplyReviewConfirmation.test.js`
- `src/test/js/batchSendTaskConsoleVisualFix.test.js`
- `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt`

1. cache query 统一更新为 `20260805-trust-reply-configurable-pages-01`，同步静态断言。
2. shared mount 行为测试：
   - 两个 tab/tabpanel 的 ARIA、hidden、next/previous、keyboard；
   - 每 card chips/picker、used owner disabled、remove/release、DOM 篡改后 server error；
   - facts 变化确认/全 reset，frame 变化只 invalidate assembly；
   - 所有 bootstrap/generation/state/assemble payload 的完整 matrix/frame；
   - server canonical response 覆盖本地、frame stale、state conflict；
   - resolved-only preview、locked order、frozen grounded allowlist；
   - instruction 连续 input 不重建 textarea，IME/focus/value 稳定；
   - 双 mount instance id/state 隔离与 destroy 取消请求。
3. 静态/contract 测试检查无 toolbar flat facts、无 requestedFactIds fallback、FULL_DRAFT 不被调用、最终 complete 需 server assembly。
4. app 测试断言训练和 LIVE snapshot 深复制 matrix/frame，最终未编辑发送透传；不从 claims/canonicalFactIds 反推。
5. controller 测试断言 evaluation HTTP 完整转为 domain assembly，旧缺失字段仍兼容。

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/resources/static/trust-reply-workbench.js` | 修改 | 双页状态机、逐摘要事实、frame、payload |
| 2 | `src/main/resources/static/styles.css` | 修改 | scoped tabs/facts/frame/响应式样式 |
| 3 | `src/main/resources/static/app.js` | 修改 | 训练评估与 LIVE/发送 snapshot 透传 |
| 4 | `src/main/resources/static/index.html` | 修改 | 静态资源 cachebuster |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt` | 修改 | evaluation matrix/frame HTTP→domain |
| 6 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 修改 | 共享组件完整交互和 transport 测试 |
| 7 | `src/test/js/trustReplyWorkbench.test.js` | 修改 | 结构/契约静态回归 |
| 8 | `src/test/js/aiReplyReviewConfirmation.test.js` | 修改 | 评估、adopt、send snapshot 测试 |
| 9 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改 | cache query 与 CSS/HTML 静态契约 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt` | 修改 | evaluation 新字段转换测试 |

范围：10 个文件；共享工作台前端 + 训练评估 adapter 两个子系统；无新 store 字段、无数据库 migration。

## 验收标准

- I-1：任一时刻只显示一个 page panel；切页不触发 bootstrap，不丢 model/timeout/request/version/lock/frame 状态；双 mount 不串状态。
- I-2：每张摘要卡显示自己的 facts；同一 fact 在其他 picker 中 disabled 并标“已用于摘要 N”；所有请求 payload 发送完整 matrix，无 `requestedFactIds`。
- I-3：事实改变明确确认并清除全部旧版本/state/assembly；frame 改变只清除 assembly，locked versions 不变且新 frame 可持久化。
- I-4：本地预览有“未整合”标记且不能 complete；只有 identity 匹配的服务端 assembly 可采用。
- I-5：训练评估、LIVE adopt、未编辑正式发送都携带同一 canonical matrix/frame/locks；最终重整合不回退 flat/default。
- I-6：preview/readiness/state/assemble 只使用 resolvedVersionId；active 未采用版本不进入。
- I-7：tab ARIA/键盘/focus/hidden 正确；instruction 连续输入不重建节点；640px 下单列无横向溢出。
- I-8：整合时只逐项生成 frozen grounded missing keys；PARTIAL/UNSUPPORTED 的人工 lock 不被覆盖；无 FULL_DRAFT 请求。
- S-1：DOM 测试存在两个等宽 tab、实例唯一 controls/panel id、selected/hidden/focus 状态；CSS 与 S-1 逐字一致。
- S-2：页头、说明和页动作 DOM 存在；下一页复用 primary、上一页复用 secondary；CSS 与 S-2 逐字一致。
- S-3：chip/picker 的 available/selected/used/pending/hover/focus/disabled 状态均有 DOM 和样式断言；used/pending 有明确文字；CSS 与 S-3 逐字一致。
- S-4：frame 两列、LOCAL/CURRENT/STALE 三态、派生 summary 样式与固定文案均有断言；CSS 与 S-4 逐字一致。
- S-5：`.trust-reply-layout`、toolbar global facts、`.trust-reply-fact-option` 从组件和专用 CSS 退出；全局 `.tabs/.tab`、mailbox segmented、button、compose panel 无 diff。
- S-6：≤640px frame/picker 单列、page actions 纵向满宽、tab 44px；reduced-motion transition none；CSS 与 S-6 逐字一致。
- 回归：
  - `node --test src/test/js/trustReplyWorkbenchSharedMount.test.js src/test/js/trustReplyWorkbench.test.js src/test/js/aiReplyReviewConfirmation.test.js src/test/js/batchSendTaskConsoleVisualFix.test.js`
  - `node --test src/test/js/*.test.js`
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test`
  - `git diff --check`

## 人工验收清单

### A-1：双页切换与状态保留

- 前置条件：训练邮件含至少 3 个摘要。
- 操作步骤：在摘要页展开卡片、选择版本、填写 instruction；点击下一页，再点上一页；用键盘 Left/Right/Home/End 切换。
- 预期结果：只显示一个页面；卡片展开、输入值、版本、锁定状态均保留；tab focus/ARIA 正确；无额外 bootstrap。
- 覆盖：I-1、I-7。

### A-2：事实唯一占用、删除与释放

- 前置条件：事实 A 可用于当前邮件，摘要 1/2 都打开 picker。
- 操作步骤：给摘要 1 添加 A；查看摘要 2；从摘要 1 删除 A，再查看摘要 2。
- 预期结果：添加后摘要 1 显示 chip，摘要 2 显示“已用于摘要 1”并禁用；删除后 A 立即可在摘要 2 添加；网络 payload 全局只出现一次 A。
- 覆盖：I-2。

### A-3：事实变化的破坏性确认

- 前置条件：至少一个摘要有已生成或已锁定版本。
- 操作步骤：删除/添加事实，先取消确认，再确认。
- 预期结果：取消时所有状态不变；确认时旧 durable state 删除，全部 versions/locks/assembly 清空，bootstrap 返回新 evidence/matrix。
- 覆盖：I-3。

### A-4：回复框架选择与本地/服务端预览边界

- 前置条件：所有摘要已处理。
- 操作步骤：进入第 2 页，分别选择四类片段；观察本地预览；点击服务端整合。
- 预期结果：整合前标“配置预览/未整合”，采用按钮禁用；整合后显示服务端 raw，状态为整合完成，采用按钮启用；正文顺序为四类 frame + canonical answers。
- 覆盖：I-4、样式契约。

### A-5：frame 修改不清空摘要答案

- 前置条件：已有有效 assembly 和全部 locked versions。
- 操作步骤：只切换 greeting 或选择“不使用”。
- 预期结果：摘要 versions/locks/进度保持；旧 assembly/采用按钮失效；保存新 frame 后重新整合即可采用。
- 覆盖：I-3、I-6。

### A-6：训练评估最终复验

- 前置条件：SIMULATION 使用非默认 frame 和逐摘要 matrix 完成 assembly。
- 操作步骤：完成模拟并保存评估；检查网络请求和服务端捕获 domain。
- 预期结果：evaluation 请求携带同一 matrix/frame/lockedItems；服务端重整合成功后才保存评估。
- 覆盖：I-5。

### A-7：正式采用与未编辑发送

- 前置条件：LIVE 使用非默认 frame 完成 assembly。
- 操作步骤：采用到人工回复，不编辑正文直接发送；另一次采用后修改正文再发送。
- 预期结果：未编辑时携带完整 assembly snapshot 并服务端重整合；编辑后沿用现有人工编辑边界，不携带旧 snapshot；两种路径不把本地预览当 authority。
- 覆盖：I-4、I-5。

### A-8：snippet/fact stale 与并发 tab

- 前置条件：两个浏览器 tab 打开同一 source；片段/事实管理可修改数据。
- 操作步骤：tab A 保存 frame/lock；tab B 用旧 stateVersion 保存；随后禁用选中 snippet 或 fact 并尝试整合。
- 预期结果：tab B 收到 state conflict；snippet 变化提示 frame stale 且 locks 保留；fact 变化提示 evidence stale 并清空旧 versions；均不可采用旧 assembly。
- 覆盖：I-3～I-5。

### A-9：窄屏与其他页面视觉回归

- 前置条件：浏览器宽度 390px、640px、桌面宽度；打开 mailbox、QA 管理、批量发送页面。
- 操作步骤：检查两个 tabs、摘要 picker、frame selects/actions；用鼠标 hover、键盘 Tab 聚焦，制造 selected/used/pending/LOCAL/CURRENT/STALE 状态；再切换其他页面。
- 预期结果：桌面 tab 两列等宽，active 为 `#2563eb` 文本+白底，tab/picker focus outline 为 `rgba(37,99,235,.35)`；used option 为虚线边框并显示“已用于摘要 N”；LOCAL/CURRENT/STALE 分别显示固定文案及 info/success/warning 色。390/640px 下 tab 高度至少 44px、frame/picker 单列、页动作纵向满宽且页面无横向滚动。其他页面 `.tabs`、mailbox segmented、button、compose panel 的 computed style 与变更前截图一致。
- 覆盖：S-1～S-6、I-7。

### A-10：输入、取消和销毁回归

- 前置条件：一个摘要使用 operator instruction；生成请求可延迟。
- 操作步骤：连续中文输入并移动光标；启动生成后取消；切换邮件触发 unmount。
- 预期结果：输入节点不重建、IME/光标稳定；取消不产生迟到版本；销毁后旧请求不更新新 source DOM。
- 覆盖：必须保持不变第 5/8/9 项、I-7、I-8。

### A-11：逐项补齐、翻译与 TTL 回归

- 前置条件：同一邮件含一个未生成 GROUNDED、一个已锁定 PARTIAL、一个已锁定 UNSUPPORTED；LLM 请求可观察，问题/回答翻译接口可用。
- 操作步骤：选择非默认单次 TTL=60 秒、总 TTL=600 秒；翻译一个问题和一个已采用回答；进入 frame 页点击“服务端整合”。
- 预期结果：翻译结果仍显示在原摘要/版本下；只为未生成 GROUNDED 发送一个 ADJUST_ITEM，请求携带 60/600 秒与完整 matrix；PARTIAL/UNSUPPORTED 的 versionId/answerText 不变；最后才发送一次 assemble，无 FULL_DRAFT。
- 覆盖：必须保持不变第 4～7 项、I-6、I-8。
