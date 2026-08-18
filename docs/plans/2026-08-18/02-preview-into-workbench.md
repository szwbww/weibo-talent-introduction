# 02 · 自动回复预览并入可信回复工作台

日期：2026-08-18
基线 commit：`4583525`（main）
主计划：[00-auto-reply-convergence-master.md](./00-auto-reply-convergence-master.md)
前置依赖：**必须在 [01](./01-decide-context-closure.md) 通过人工验收后执行**
子系统数：1（frontend）
文件数：6

## 需求描述

### Observable outcome

1. 未匹配来信详情页里**只剩一个回复渲染器**。原「自动回复预览」独立折叠块被移除，其内容改由可信回复工作台以只读的 `AUTO_PREVIEW` 宿主呈现。
2. 该只读宿主同屏展示：逐项诉求与 grounding 状态（来自工作台 bootstrap）、自动路会发出的整篇正文、判定 reason、以及会拦住它的运行期闸门列表。
3. 运营能直接看出「工作台认为这条诉求无依据」与「自动路会不会发」是不是一回事——两侧的诉求列表逐条对齐。

### What must NOT change

- 训练宿主（`SIMULATION` + `TRAINING_MAIL`）与生产宿主（`LIVE` + `LIVE_INBOUND`）的既有行为、DOM、请求完全不变。
- 工作台不提供用户可切换的模式下拉；宿主仍由页面固定（K-shared-workbench-fixed-mode-host-adapter 第 3 条）。
- 预览仍是反事实：不因运行期闸门隐藏正文（主计划 X-2）。
- 后端 `/api/mail/unmatched-inbound/{id}/auto-reply-preview` 端点与 `AutoReplyPreviewResponse` 字段**一个不动**（本计划纯前端）。
- `AUTO_PREVIEW` 宿主**没有任何发送、采用、生成按钮**。

### Out of scope

- 逐项 `generateItem()` 管线改造（主计划 X-4）。`AUTO_PREVIEW` 本轮展示的正文仍是 `decide()` 的整篇产物，不是逐项组装的。
- 删除 `/auto-reply-preview` 后端端点（主计划已声明留待本计划人工验收通过后单独处理）。
- CRS 分数展示（→ 03）。
- `AutoReplyPreviewService` 在 `QA_GAP` / `QA_NO_MATCH` / `MANUAL_HANDOFF` 分支出稿——这依赖 X-4，本轮不做；这三种 `previewKind` 下正文区显示 reason 说明而非正文。

## 关键不变量

### Invariant I-1: mode ↔ source 必须是显式配对表
- Rule：`validateMount` 的模式与来源校验必须由一张显式映射表驱动（`MODE_SOURCE = { SIMULATION: TRAINING_MAIL, LIVE: LIVE_INBOUND, AUTO_PREVIEW: LIVE_INBOUND }`），禁止保留二元三目表达式。
- Applies to：`trust-reply-workbench.js` 的 `validateMount()`。
- Violation consequence：现有实现是 `options.mode === MODES.SIMULATION ? SOURCES.TRAINING_MAIL : SOURCES.LIVE_INBOUND`（`trust-reply-workbench.js:130`）——**任何非 SIMULATION 的模式都会被静默当作 LIVE 放行**，第三模式会拿到可采用、可发送的 LIVE 能力。
- 来源：original（`trust-reply-workbench.js:129-133` 逐字核对）

### Invariant I-2: `AUTO_PREVIEW` 是只读的
- Rule：`AUTO_PREVIEW` 模式下，工作台不渲染任何触发写操作或生成的控件：无 handling 下拉、无 operatorInstruction 输入框、无「生成」「采用」「锁定」「整合」按钮；不发起 `POST /generations/stream`、`POST /assemble`、`PUT /state`；`onComplete` 永不触发。仅允许 `POST /bootstrap`。
- Applies to：`trust-reply-workbench.js` 的渲染与请求分支。
- Violation consequence：预览产生副作用（写 `trust_reply_workbench_state`）或消耗 LLM 配额，违反主计划 X-2 的只读约束。
- 来源：K-preview-mirrors-pipeline（"预览服务不加 @Transactional、无 save/send，纯只读"）

### Invariant I-3: 闸门只标记，不隐藏
- Rule：`AUTO_PREVIEW` 宿主必须把 `wouldBeBlockedBy` 的每一项都渲染为可见标记，并且**无论该数组是否非空，正文区照常渲染**。
- Applies to：`app.js` 的 `AUTO_PREVIEW` 宿主适配器。
- Violation consequence：运营把"不可发送"误读成"没生成内容"，或反之。
- 来源：K-preview-runtime-gates-visible、K-preview-mirrors-pipeline 第 2 条

### Invariant I-4: 无联系人的记录必须有明确降级路径
- Rule：`record.expertContactId` 为 null 时不得挂载 `AUTO_PREVIEW` 宿主，必须渲染一段静态说明文案。
- Applies to：`app.js` 的宿主挂载条件。
- Violation consequence：`TrustReplyWorkbenchService.resolveLiveInbound()` 在联系人缺失时抛 `TRUST_REPLY_SOURCE_CONTACT_NOT_FOUND`（422），宿主会显示裸错误。而现状是**预览块对所有记录无条件渲染**（`app.js:9954` 无 `expertContactId` 条件，对比 `:9875` 的 `composeWorkbenchHtml` 有条件）——直接替换会让这类记录丢失整块功能。
- 来源：original（`app.js:9874-9876` 与 `:9954` 的条件差异，逐字核对）

### Invariant I-5: 旧 UI 的契约测试必须同步退役
- Rule：删除旧预览 DOM 的同时，必须改写 `unmatchedQaReplySource.test.js:28-35` 中直接断言旧函数名与旧 DOM id 的 5 条 assert，并对新 DOM id 增加「该 id 确实出现在渲染源文本中」的存在性断言。
- Applies to：`src/test/js/unmatchedQaReplySource.test.js`。
- Violation consequence：全量测试持续失败并阻塞发布；或反过来，DOM stub 让测试全绿而生产静默短路。
- 来源：K-ui-removal-retires-obsolete-contract-tests、K-dom-stub-tests-hide-dangling-refs

## 现状审计

### `trust-reply-workbench.js`（唯一工作台实现，X-1 单实现边界）

模式与来源常量（`:6-7`，逐字）：

```js
const MODES = Object.freeze({ SIMULATION: "SIMULATION", LIVE: "LIVE" });
const SOURCES = Object.freeze({ TRAINING_MAIL: "TRAINING_MAIL", LIVE_INBOUND: "LIVE_INBOUND" });
```

`validateMount()`（`:122-135`，逐字）：

```js
function validateMount(host, options) {
    if (!host || typeof host !== "object" || typeof host.innerHTML !== "string") {
        throw new TypeError("TrustReplyWorkbench.mount requires a host element");
    }
    if (!options || !Object.values(MODES).includes(options.mode)) {
        return rejectMount(host, "工作台模式无效");
    }
    const source = options.source || {};
    const expectedSource = options.mode === MODES.SIMULATION ? SOURCES.TRAINING_MAIL : SOURCES.LIVE_INBOUND;
    if (source.sourceType !== expectedSource || !Number.isInteger(Number(source.sourceId)) || Number(source.sourceId) <= 0) {
        return rejectMount(host, "工作台来源与页面模式不匹配");
    }
    if (typeof options.contextPath !== "string" || typeof options.onComplete !== "function") {
        return rejectMount(host, "工作台宿主参数不完整");
    }
}
```

→ `expectedSource` 的三目表达式是 I-1 的直接依据。
→ `options.onComplete` 当前是**必填**（`typeof options.onComplete !== "function"` 即拒绝）；`AUTO_PREVIEW` 永不完成，需在契约上放宽或由宿主传空函数（见 T2）。

导出面（`:1758`）：`global.TrustReplyWorkbench = Object.freeze({ mount });`
实例返回（`:1755`）：`return { state, bootstrap, unmount };`；`mount()` 只对外暴露 `{ unmount }`（`:119`）。

### `app.js`（宿主适配器）

**工作台宿主挂载点，共 2 处**：

```
$ grep -n "TrustReplyWorkbench" src/main/resources/static/app.js
174:function requireTrustReplyWorkbenchRuntime(host) {
175:    const runtime = window.TrustReplyWorkbench;
3450:    const runtime = requireTrustReplyWorkbenchRuntime(host);   // mountAiTrainingTrustReply
9725:    const runtime = requireTrustReplyWorkbenchRuntime(host);   // mountLiveTrustReply
```

- 训练宿主 `mountAiTrainingTrustReply(mail)`（`:3446-3474`）：`mode: "SIMULATION"`，host `#aiTrainingTrustReplyHost`。
- 生产宿主 `mountLiveTrustReply(recordId)`（`:9722-9739`）：`mode: "LIVE"`，host `[data-trust-reply-live-host]`。

**生产宿主的 DOM 是条件渲染的**（`:9874-9876`，逐字）：

```js
const composeWorkbenchHtml = record.expertContactId
    ? `<div class="detail-section reply-workflow-detail compose-workbench-section" data-trust-reply-live-host></div>`
    : "";
```

**旧预览块是无条件渲染的**（`:9954-9966`，逐字）：

```js
<details class="detail-section reply-workflow-detail auto-reply-preview-section" data-record-id="${id}">
    <summary class="reply-workflow-summary">
        <span class="reply-workflow-icon" aria-hidden="true">自</span>
        <span class="reply-workflow-title"><strong>自动回复预览</strong><small id="autoReplyPreviewMeta">点击按钮后分析来信意图与回复规则</small></span>
        <span class="reply-workflow-status" id="autoReplyPreviewStatus">未生成</span>
        <span class="reply-workflow-chevron" aria-hidden="true">⌄</span>
    </summary>
    <div class="reply-workflow-content">
        <p class="text-muted" style="font-size:12px;margin:0 0 8px;">模拟「若此刻开启自动回复」系统会回什么（不发送、不写库）</p>
        <button type="button" class="button" data-action="preview-auto-reply" data-record-id="${id}">生成自动回复预览</button>
        <div id="autoReplyPreviewResult" style="margin-top:12px;"><p class="text-muted">尚未生成自动回复预览</p></div>
    </div>
</details>
```

**要删除的 JS 符号（全仓引用，已 grep 核对）**：

| 符号 | 定义 | 引用 |
|---|---|---|
| `autoReplyPreviewKindLabels` | `app.js:9345` | `app.js:9362`、`app.js:9413` |
| `renderAutoReplyPreviewHtml(preview)` | `app.js:9361` | `app.js:9445` |
| `renderAutoReplyPreviewSummary(preview)` | `app.js:9412` | `app.js:9444` |
| `loadAutoReplyPreview(recordId)` | `app.js:9429` | `app.js:10026`（action handler） |
| DOM id `#autoReplyPreviewResult` | `app.js:9964` | `app.js:9430、9445` |
| DOM id `#autoReplyPreviewStatus` | `app.js:9958` | `app.js:9431、9446` |
| DOM id `#autoReplyPreviewMeta` | `app.js:9957` | `app.js:9432、9447` |
| `data-action="preview-auto-reply"` | `app.js:9963` | `app.js:9433`（querySelector）、`app.js:10024`（handler 分支） |

**action handler 分支**（`:10024-10031`，逐字）：

```js
if (action === "preview-auto-reply") {
    try {
        await loadAutoReplyPreview(id);
    } catch (error) {
        showStatus(error.message || "预览失败", "error");
    }
    return;
}
```

删除时必须同步移除该分支，否则留下对已删函数的引用（K-ai-reply-modal-helper-scope 的同类问题）。

### `styles.css`（前端样式盘点）

**可复用 class（本计划直接复用，禁止另造近似样式）**：

| class | 位置 | 用途 |
|---|---|---|
| `.detail-section` | `styles.css:1968-1972` | `display:flex; flex-direction:column; gap:6px` |
| `.reply-workflow-detail` | `styles.css:1982-1989` | 折叠卡容器：`gap:0; overflow:hidden; border:1px solid var(--panel-border); border-radius:var(--radius-md); background:var(--panel-bg); box-shadow:var(--shadow-sm)` |
| `.reply-workflow-summary` | `styles.css:1991-1998` | `display:flex; align-items:center; gap:10px; padding:11px 12px; cursor:pointer; list-style:none`；配套 `::-webkit-details-marker{display:none}`（`:2000-2002`） |
| `.reply-workflow-icon` | `styles.css:2004-2014` | 30×30 圆形徽标：`border-radius:50%; background:var(--surface); color:var(--text-main)` |
| `.reply-workflow-title` | `styles.css:2016-2033` | `min-width:0; flex:1`；`strong`/`small` 均 `display:block`，`small` 用 `var(--text-muted)` / `11px` |
| `.reply-workflow-status` | `styles.css:2035-2042` | `padding:2px 8px; border-radius:999px; background:var(--primary-light); color:var(--primary); font-size:11px; white-space:nowrap` |
| `.reply-workflow-chevron` | `styles.css:2044-2051` | `[open]` 时 `transform: rotate(180deg)` |
| `.reply-workflow-content` | `styles.css:2053-2056` | `padding:12px; border-top:1px solid var(--line)` |
| `.compose-workbench-section` | `styles.css:5370-5378`、响应式 `:5886-5893` | 工作台网格布局宿主 |
| `.text-muted` | 全局 | 次要文案 |

**要删除的 class（改动前基线，逐字）**：

```css
/* styles.css:2690-2698 */
.auto-reply-preview-notice {
    padding: 10px 12px;
    margin: 8px 0;
    border-radius: var(--radius-sm);
    background-color: var(--warning-bg);
    border: 1px solid var(--warning-border);
    color: var(--warning);
    font-size: 12px;
    line-height: 1.5;
}

/* styles.css:2700-2702 */
.auto-reply-preview-result {
    margin-top: 4px;
}
```

使用点核对：

```
$ grep -rn "auto-reply-preview-notice\|auto-reply-preview-result\|auto-reply-preview-section" src/main src/test
src/main/resources/static/styles.css:2690
src/main/resources/static/styles.css:2701
src/main/resources/static/app.js:9376
src/main/resources/static/app.js:9383
src/main/resources/static/app.js:9401
src/main/resources/static/app.js:9954
```

→ 仅 `app.js` 与 `styles.css`，无 `index.html` 引用。两个 class 随 `renderAutoReplyPreviewHtml` 一并删除。

**设计基准 token（沿用，不新增）**：`--panel-border`、`--panel-bg`、`--radius-md`、`--radius-sm`、`--shadow-sm`、`--line`、`--text-muted`、`--primary`、`--primary-light`、`--warning`、`--warning-bg`、`--warning-border`。

**注意**：`--panel-bg` 是半透明 token（来源：K-panel-bg-token-is-translucent）。新增只读遮罩层不得在 `.reply-workflow-detail` 之上再叠一层 `--panel-bg`，否则叠加透明度会让下层内容透出。

### Interaction points

| # | 写入方 | 读取方 | 本计划影响 |
|---|---|---|---|
| IP-1 | `POST /api/trust-reply/workbench/bootstrap`（`requestCoverage`） | `AUTO_PREVIEW` 宿主的诉求列表 | 新建。与 LIVE 宿主读同一端点同一 sourceId |
| IP-2 | `GET /api/mail/unmatched-inbound/{id}/auto-reply-preview` | `AUTO_PREVIEW` 宿主的正文/reason/闸门区 | 既有端点，新消费方 |
| IP-3 | 01 计划的 `decide()` 上下文收口 | IP-1 与 IP-2 的诉求状态一致性 | **本计划的可观察价值完全建立在 01 之上**；01 未落地则两侧仍会不一致 |

### 受影响的既有测试

| 文件 | 位置 | 断言内容 | 必须如何改 |
|---|---|---|---|
| `unmatchedQaReplySource.test.js` | `:28-35` | 断言 `async function loadAutoReplyPreview(recordId)`、`id="autoReplyPreviewStatus"`、`id="autoReplyPreviewMeta"`、`data-action="preview-auto-reply"` 存在 | 全部改写为对新 DOM 的断言（I-5） |
| `trustReplyWorkbenchSharedMount.test.js` | `:369-377` `rejects invalid mode and source combinations` | 只覆盖 `SIMULATION` + `LIVE_INBOUND` 的错配 | 扩展覆盖 `AUTO_PREVIEW` + `TRAINING_MAIL` 错配，以及未知 mode |
| `trustReplyWorkbenchSharedMount.test.js` | `:303-341` 双宿主共存用例 | 断言两宿主无 mode 切换控件 | 保持不变（回归） |

## 样式契约

### S-1: `AUTO_PREVIEW` 宿主外壳

- **复用**：`.detail-section`（`styles.css:1968`）、`.reply-workflow-detail`（`:1982`）、`.reply-workflow-summary`（`:1991`）、`.reply-workflow-title`（`:2016`）、`.reply-workflow-status`（`:2035`）、`.reply-workflow-chevron`（`:2044`）、`.reply-workflow-content`（`:2053`）。
  禁止执行 agent 自造"近似"折叠卡样式替代以上 class。
- **新增**：无新 class。宿主复用 `.compose-workbench-section`（`styles.css:5370`）与生产宿主保持同构。
- **DOM 结构**（逐字骨架，`${id}` 为 `record.id`）：

```html
<details class="detail-section reply-workflow-detail compose-workbench-section auto-preview-section" data-record-id="${id}">
    <summary class="reply-workflow-summary">
        <span class="reply-workflow-icon" aria-hidden="true">自</span>
        <span class="reply-workflow-title"><strong>自动回复预览</strong><small>若此刻开启自动回复，系统会怎么处理（只读，不发送、不写库）</small></span>
        <span class="reply-workflow-status" data-auto-preview-status>未生成</span>
        <span class="reply-workflow-chevron" aria-hidden="true">⌄</span>
    </summary>
    <div class="reply-workflow-content" data-trust-reply-auto-preview-host></div>
</details>
```

- **禁止项**：inline style（旧块的 `style="font-size:12px;margin:0 0 8px;"` 与 `style="margin-top:12px;"` 一并删除）；未在本契约声明的新 class；对上述既有 class 规则块的任何修改。

### S-2: 只读态视觉

- **复用**：无既有只读态 class 可复用（已 grep `readonly|read-only|disabled-panel` 于 `styles.css`，无匹配的面板级只读样式）。
- **新增**（逐字复制到 `styles.css`，追加在 `.compose-workbench-section` 规则块之后，即原 `:5378` 之后）：

```css
.trust-reply-readonly {
    position: relative;
}

.trust-reply-readonly [data-workbench-control],
.trust-reply-readonly button,
.trust-reply-readonly select,
.trust-reply-readonly textarea,
.trust-reply-readonly input {
    display: none !important;
}

.trust-reply-readonly-banner {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 12px;
    margin: 0 0 10px;
    border: 1px solid var(--panel-border);
    border-left: 2px solid var(--primary);
    border-radius: var(--radius-sm);
    color: var(--text-muted);
    font-size: 12px;
    line-height: 1.5;
}

.trust-reply-gate-list {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin: 10px 0 0;
    padding: 0;
    list-style: none;
}

.trust-reply-gate-item {
    padding: 2px 8px;
    border-radius: 999px;
    background-color: var(--warning-bg);
    border: 1px solid var(--warning-border);
    color: var(--warning);
    font-size: 11px;
    line-height: 1.6;
    white-space: nowrap;
}

.trust-reply-gate-list:empty {
    display: none;
}
```

- **DOM 结构**（逐字骨架，由 `trust-reply-workbench.js` 在 `AUTO_PREVIEW` 模式下渲染）：

```html
<div class="trust-reply-readonly-banner">只读预览：此处不生成、不采用、不发送</div>
<ul class="trust-reply-gate-list">
    <li class="trust-reply-gate-item">RECIPIENT_UNSUBSCRIBED</li>
</ul>
```

- **禁止项**：inline style；用 `visibility:hidden` / `opacity:0` 代替 `display:none`（控件仍可 Tab 聚焦并触发）；对 `.compose-workbench-section` 既有规则块的修改。
- **说明**：`.trust-reply-readonly` 用 `display:none` 而非 `pointer-events:none`，因为后者不阻止键盘触发。`!important` 是必要的——工作台内部控件的既有规则特异性未知，本契约不逐一枚举。

## 实现方案

### T1 · 模式配对表（I-1）

文件：`src/main/resources/static/trust-reply-workbench.js`

1. `:6` 扩为：

```js
const MODES = Object.freeze({ SIMULATION: "SIMULATION", LIVE: "LIVE", AUTO_PREVIEW: "AUTO_PREVIEW" });
```

2. `:7` 之后新增显式配对表：

```js
const MODE_SOURCE = Object.freeze({
    SIMULATION: SOURCES.TRAINING_MAIL,
    LIVE: SOURCES.LIVE_INBOUND,
    AUTO_PREVIEW: SOURCES.LIVE_INBOUND
});
```

3. `:130` 的三目表达式替换为：

```js
const expectedSource = MODE_SOURCE[options.mode];
if (!expectedSource) {
    return rejectMount(host, "工作台模式无效");
}
```

（保留其后的 `source.sourceType !== expectedSource` 校验不变。）

4. `:133` 的 `onComplete` 必填校验放宽为：`AUTO_PREVIEW` 模式下 `onComplete` 可缺省；其余模式仍必填。

### T2 · 只读态渲染与请求禁用（I-2, S-2）

文件：`src/main/resources/static/trust-reply-workbench.js`

1. `createInstance` 的 `state` 增加 `readOnly: options.mode === MODES.AUTO_PREVIEW`。
2. 宿主根元素在 `readOnly` 时加 class `trust-reply-readonly`（S-2）。
3. `readOnly` 时渲染 `.trust-reply-readonly-banner`（S-2 骨架逐字）。
4. **请求闸门（fail-closed）**：在统一的 `requestJson()` 入口（`trust-reply-workbench.js:204`）加前置断言 —— `readOnly` 为真时，除 `"/bootstrap"` 外的任何 path 直接 `throw new Error("AUTO_PREVIEW 模式禁止写操作")`，不发起 fetch。这是 I-2 的**唯一执行点**，不在每个调用处重复。
5. `readOnly` 时不注册生成/采用/锁定/整合的事件监听器；`onComplete` 永不调用。

### T3 · 宿主适配器（I-3, I-4, S-1）

文件：`src/main/resources/static/app.js`

1. 在 `mountLiveTrustReply` 附近新增 `mountAutoPreviewTrustReply(recordId)`，结构对齐现有宿主（K-shared-workbench-fixed-mode-host-adapter 第 2 条：宿主只传固定上下文）：

```js
function mountAutoPreviewTrustReply(recordId) {
    unmountAutoPreviewTrustReply();
    const host = document.querySelector("[data-trust-reply-auto-preview-host]");
    if (!host) return;
    const runtime = requireTrustReplyWorkbenchRuntime(host);
    if (!runtime) return;
    autoPreviewTrustReplyInstance = runtime.mount(host, {
        mode: "AUTO_PREVIEW",
        source: { sourceType: "LIVE_INBOUND", sourceId: Number(recordId) },
        contextPath,
        onUnauthorized: trustReplyUnauthorized
    });
}
```

2. 新增 `unmountAutoPreviewTrustReply()`，并**在 `unmountLiveTrustReply` 的全部既有调用点旁同步调用**。该函数定义于 `app.js:160`，调用点共 8 处（已 grep 核对，逐一列出，缺一即泄漏实例）：

```
$ grep -n "unmountLiveTrustReply" src/main/resources/static/app.js
160:function unmountLiveTrustReply() {     ← 定义
1627:    if (view !== "mailbox") unmountLiveTrustReply();
9722:    unmountLiveTrustReply();
9769:    unmountLiveTrustReply();
10019:        unmountLiveTrustReply();
10044:        unmountLiveTrustReply();
10058:        unmountLiveTrustReply();
10099:        unmountLiveTrustReply();
11567:        unmountLiveTrustReply();
```

推荐做法：不要在 8 处各加一行，而是把两个 unmount 收进一个 `unmountMailboxTrustReplyHosts()`，把 8 处调用统一替换为它——单点维护，避免下次新增宿主再漏一处。
3. 挂载后异步拉一次 `GET /api/mail/unmatched-inbound/${recordId}/auto-reply-preview`，把 `replyBody` / `reason` / `wouldBeBlockedBy` / `previewKind` 渲染进宿主内的只读区：
   - `wouldBeBlockedBy` 逐项渲染为 `.trust-reply-gate-item`（S-2 骨架）。**数组为空时列表元素仍存在但被 `:empty` 规则隐藏；数组非空时正文区照常渲染**（I-3）。
   - `replyBody` 为 null（`QA_GAP` / `QA_NO_MATCH` / `MANUAL_HANDOFF` 三种 `previewKind`）时，正文区显示 reason 的中文说明，**不显示空白**。
   - 陈旧响应防护：沿用现有 `String(state.mailbox.detailContext?.id) !== String(recordId)` 的判定模式（`app.js:9443` 既有写法）。
4. **DOM 条件渲染**（I-4）：把 S-1 骨架放在 `record.expertContactId` 为真的分支；为假时渲染：

```html
<div class="detail-section reply-workflow-detail">
    <div class="reply-workflow-content">
        <p class="text-muted">该来信尚未绑定专家联系人，无法解析自动回复上下文。请先在上方完成绑定。</p>
    </div>
</div>
```

### T4 · 删除旧预览实现

文件：`src/main/resources/static/app.js`、`src/main/resources/static/styles.css`

按「现状审计 → 要删除的 JS 符号」表逐项删除：

- `app.js`：`autoReplyPreviewKindLabels`（`:9345`）、`renderAutoReplyPreviewHtml`（`:9361`）、`renderAutoReplyPreviewSummary`（`:9412`）、`loadAutoReplyPreview`（`:9429`）四个符号整体删除。
- `app.js:9954-9966`：旧 `<details class="... auto-reply-preview-section">` 块整体删除。
- `app.js:10024-10031`：`preview-auto-reply` action handler 分支整体删除。
- `styles.css:2690-2702`：`.auto-reply-preview-notice` 与 `.auto-reply-preview-result` 两个规则块删除。

删除后必须执行悬空引用核对（K-ai-reply-modal-helper-scope、K-dom-stub-tests-hide-dangling-refs）：

```bash
grep -n "autoReplyPreview\|preview-auto-reply\|auto-reply-preview" src/main/resources/static/app.js src/main/resources/static/styles.css src/main/resources/static/index.html
```

预期输出为空。

### T5 · 测试改写与新增（I-5）

1. `src/test/js/unmatchedQaReplySource.test.js`：改写 `:28-35` 的 5 条 assert。新断言：
   - `appJsSource.includes("function mountAutoPreviewTrustReply(recordId)")`
   - `appJsSource.includes("data-trust-reply-auto-preview-host")`
   - `appJsSource.includes("data-auto-preview-status")`
   - `!appJsSource.includes("loadAutoReplyPreview")`（旧符号已彻底消失）
   - `!appJsSource.includes("preview-auto-reply")`
2. `src/test/js/trustReplyWorkbenchSharedMount.test.js`：扩展 `:369-377`，新增两条错配用例：`{ mode: "AUTO_PREVIEW", source: { sourceType: "TRAINING_MAIL" } }` 必须抛错；`{ mode: "UNKNOWN_MODE", source: { sourceType: "LIVE_INBOUND" } }` 必须抛「模式无效」。
3. 新建 `src/test/js/autoPreviewWorkbenchHost.test.js`，覆盖：
   - `AUTO_PREVIEW` 挂载后只发出 `/bootstrap` 一个请求（用 fetch stub 记录全部 path，断言长度为 1）。
   - 对 `/assemble`、`/generations/stream`、`/state` 的调用被 `requestJson` 前置断言拦下，不产生 fetch。
   - `wouldBeBlockedBy` 非空时，正文区仍渲染（I-3）——断言正文文本存在且 gate item 数量等于数组长度。
   - `onComplete` 缺省时挂载不报错（T1 第 4 点）。

## 变更文件清单

| # | 文件 | 改动类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/resources/static/trust-reply-workbench.js` | 修改 | MODES +1；新增 `MODE_SOURCE`；`validateMount` 改配对表；只读态渲染与 `requestJson` 前置闸门 |
| 2 | `src/main/resources/static/app.js` | 修改 | 删 4 个符号 + 1 个 DOM 块 + 1 个 handler 分支；新增 `mountAutoPreviewTrustReply` / `unmountAutoPreviewTrustReply` 与 S-1 骨架 |
| 3 | `src/main/resources/static/styles.css` | 修改 | 删 2 个规则块；按 S-2 逐字新增 5 个规则块 |
| 4 | `src/test/js/unmatchedQaReplySource.test.js` | 修改 | 改写 `:28-35` 的 5 条 assert |
| 5 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 修改 | 扩展 `:369-377` 错配用例 |
| 6 | `src/test/js/autoPreviewWorkbenchHost.test.js` | 新建 | `AUTO_PREVIEW` 宿主行为契约 |

合计 6 个文件，1 个子系统（frontend）。无后端改动，无迁移，无新增数据字段。

## 验证命令

> 本项目前端 JS 用例有两条**互不等价**的执行入口，必须分清（来源：K-js-test-invocation-surface）：
> `verify.sh` **只跑 `normalizeDiscoveryResultSummary.test.js` 一个文件**，不可用作本计划的回归门禁。
> 以下命令可原样复制执行。来源：K-js-test-invocation-surface 实测记录 + 项目根 `CLAUDE.md`。

```bash
# 本计划权威门禁：目标测试文件单跑
node --test src/test/js/autoPreviewWorkbenchHost.test.js
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js
node --test src/test/js/unmatchedQaReplySource.test.js

# 全部前端用例
node --test src/test/js/*.test.js

# 语法检查（pom 中 test phase 也会跑）
node --check src/main/resources/static/app.js
node --check src/main/resources/static/trust-reply-workbench.js

# 悬空引用核对（T4 要求，预期输出为空）
grep -n "autoReplyPreview\|preview-auto-reply\|auto-reply-preview" \
  src/main/resources/static/app.js \
  src/main/resources/static/styles.css \
  src/main/resources/static/index.html

# 全量测试（回归门禁；其 test phase 经 exec-maven-plugin 覆盖上述 node --test，
# 绑定见 pom.xml:188-203，skipNodeTests 在 pom.xml:19-25 未定义故不跳过）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：`node --test` 输出 `# fail 0`；`node --check` 无输出且退出码 0；`grep` 无输出（退出码 1 属正常）；`mvn` 退出码 0 且含 `Failures: 0, Errors: 0`。

## 验收标准

- **I-1**：`grep -n "MODE_SOURCE" trust-reply-workbench.js` 存在；`grep -n "=== MODES.SIMULATION ?" trust-reply-workbench.js` 无输出（三目表达式已消失）。T5-2 的两条错配用例通过。
- **I-2**：T5-3 的前两个用例通过——挂载后 fetch 记录长度为 1 且 path 以 `/bootstrap` 结尾；对三个写 path 的调用不产生 fetch。
- **I-3**：T5-3 第三个用例通过——`wouldBeBlockedBy.length === 2` 时，`.trust-reply-gate-item` 数量为 2 **且** 正文文本非空。
- **I-4**：`grep -n "data-trust-reply-auto-preview-host" app.js` 的出现位置在 `record.expertContactId` 条件分支内（人工 diff 核对）；同时存在无联系人的降级文案字符串。
- **卸载对称性**：`grep -c "unmountLiveTrustReply\|unmountMailboxTrustReplyHosts" app.js` 与 `grep -c "unmountAutoPreviewTrustReply\|unmountMailboxTrustReplyHosts" app.js` 的调用点集合一致——两个宿主在任一卸载时机都被同时卸载。
- **I-5**：`node --test src/test/js/unmatchedQaReplySource.test.js` 通过；且该文件不再包含 `loadAutoReplyPreview` / `autoReplyPreviewStatus` / `preview-auto-reply` 三个旧标识符。
- **S-1**：`app.js` 中新 DOM 骨架与契约逐字一致（diff 核对）；骨架内无 `style="` 属性。
- **S-2**：`styles.css` 中 5 个新规则块与契约代码块**逐字一致**（属性、值、顺序、`!important` 均不得改动）；`grep -c "auto-reply-preview" styles.css` 返回 0。
- **IP-3 集成**：见 A-1（依赖 01 已落地）。
- **回归**：执行「验证命令」节的全部命令通过。

## 人工验收清单

### A-1: 一个渲染器，两侧诉求逐条对齐（覆盖：需求 1/3，IP-1，IP-2，IP-3）

- 前置条件：01 已落地并通过验收。挑一封有 ≥2 条诉求的来信，且该来信已绑定专家联系人。
- 操作步骤：
  1. 打开该来信详情页。
  2. 确认页面上「自动回复预览」只有**一处**（用浏览器 Ctrl+F 搜"自动回复预览"，命中 1 次）。
  3. 展开「自动回复预览」折叠块，记录其中每条诉求的文本与 grounding 状态。
  4. 展开下方的可信回复工作台，记录同样的信息。
- 预期结果：
  - 两处的诉求**条数相同、文本相同、顺序相同、grounding 状态逐条相同**。
  - 「自动回复预览」块内**没有**任何按钮、下拉框、输入框。
  - 页面上不再有「生成自动回复预览」按钮。

### A-2: 闸门只标记不隐藏（覆盖：I-3，X-2）

- 前置条件：挑一个联系人，把其 `expert_contact.auto_reply_enabled` 置为 0。
- 操作步骤：打开该联系人的来信详情，展开「自动回复预览」。
- 预期结果：
  - 出现一个 pill 标记，文本为 `AUTO_REPLY_DISABLED`。
  - **正文区同时显示完整回复内容**，不是空白、不是"已被拦截"。

### A-3: 只读性（覆盖：I-2）

- 前置条件：任意已绑定联系人的来信。
- 操作步骤：
  1. 打开详情页，展开「自动回复预览」。
  2. 在该块内按 Tab 键 10 次，观察焦点是否落入任何可输入/可点击控件。
  3. 记录 `trust_reply_workbench_state` 表中该 `source_id` 的行数与 `state_version`。
  4. 关闭详情、重新打开、再展开该块 3 次。
  5. 再次查询同一行。
- 预期结果：
  - 步骤 2 焦点不落入该块内任何控件。
  - 步骤 5 的行数与 `state_version` 与步骤 3 完全相同（无写入）。

### A-4: 无联系人来信的降级（覆盖：I-4）

- 前置条件：一条 `expert_contact_id` 为 `NULL` 的 `inbound_mail_processing` 记录。
- 操作步骤：打开该记录详情。
- 预期结果：
  - 「自动回复预览」位置显示文案：`该来信尚未绑定专家联系人，无法解析自动回复上下文。请先在上方完成绑定。`
  - 页面**不显示** `TRUST_REPLY_SOURCE_CONTACT_NOT_FOUND` 或任何 4xx 裸错误。
  - 页面其余部分（绑定表单、邮件正文、历史）正常渲染。

### A-5: 回归 —— 训练宿主未受影响（覆盖：must-NOT-change 第 1 条）

- 前置条件：AI 训练页有可选的模拟邮件。
- 操作步骤：
  1. 进「AI 训练」→「模拟」Tab，选一封邮件。
  2. 逐项生成、采用一条 GROUNDED 项，点整合。
  3. 保存一次训练评估。
- 预期结果：全流程与改动前一致，无报错；评估保存成功并显示 `已保存评估 #<id>`。

### A-6: 回归 —— 生产工作台采用链路未受影响（覆盖：must-NOT-change 第 1 条）

- 前置条件：任意已绑定联系人的未匹配来信。
- 操作步骤：
  1. 打开详情，在**可信回复工作台**（不是自动回复预览块）里逐项处理并点整合。
  2. 确认草稿被采用到下方人工回复编辑器。
- 预期结果：出现提示 `草稿已采用到人工回复区，请确认后发送`，编辑器内有正文，页面自动滚动到编辑器。

### A-7: UI 目测 —— 样式与既有折叠卡同构（覆盖：S-1，S-2）

- 操作步骤：把「自动回复预览」块与紧邻的「可信回复工作台」块、「邮件往来历史」块并排截图对比。
- 预期结果（逐项核对）：
  - 三者外框圆角、边框色、背景、投影一致（同为 `.reply-workflow-detail`）。
  - 折叠头的标题字重、副标题颜色（灰）与字号（11px）一致。
  - 右上角状态 pill 为胶囊形（`border-radius:999px`），底色为主色浅色调。
  - 只读横幅左边框为 2px 主色竖线，文字为灰色 12px。
  - 闸门 pill 为黄色系（`--warning-bg` 底 / `--warning-border` 边 / `--warning` 字），与只读横幅视觉可区分。
  - 该块内**无任何** inline style（浏览器 DevTools 检查元素，`style` 属性为空）。
