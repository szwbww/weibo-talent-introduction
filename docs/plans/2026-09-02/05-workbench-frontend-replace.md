# 05 可信工作台前端替换：正文 / 用到哪些事实 / 未识别的提问

> 顺序权威：`00-execution-order.md`。**依赖 03b**（不是 03）。
> 03b 未落地时本计划**不得开工**：新工作台产出的草稿没有可用的发送契约，
> 采用后必然 400/422（D-11）。
> 全局不变量 G-1 ~ G-8 适用，本文不重复定义。
> 界面基准：`docs/mockups/trust-workbench-rag.html`。

## 需求描述

**Observable outcome**

1. 可信工作台改为三块：**正文**（含回复框架四段）、**用到了哪些事实**、**未识别的提问**。
   页面上不再有：逐问卡片、处理方式下拉、版本/锁定/采用、整合按钮、重排/pin/合并段落、
   事实矩阵、覆盖审计、专家与流程上下文、来信原文栏、发信账号选择、模型自述。
2. 正文中 `render_mode=VERBATIM` 的段落有紫色左边框与 `fact_code` 角标；
   回复框架四段用虚线灰色标出，与模型产出物区分。
3. 运营的操作收敛为四个：**重新生成 / 加事实 / 去事实 / 直接编辑正文**；
   加减事实后重新生成即可，无需整合与重排。

**What must NOT change**

1. 两个宿主入口的挂载契约：`window.TrustReplyWorkbench.mount(host, {mode, source, contextPath,
   autoBootstrap, onUnauthorized, onChange, onComplete})` 返回带 `unmount()` 的实例。
   `app.js` 的两个 mount 函数签名与调用位置不变。
2. 「采用到人工回复」之后的手动发信链路（富文本编辑区、二次确认、发送）。
3. 回复框架片段的管理界面与数据。

**Out of scope**

- 后端旧端点 `/api/trust-reply/workbench/*` 的删除与旧 Kotlin 服务的删除（→ 07 / 另开计划）。
  本计划落地后旧端点仍在，只是前端不再调用。
- 提示词页（→ 06）。
- 把草稿落库、草稿版本历史：本轮不做，生成结果只存在页面内存里。

## 关键不变量

### I-24: 挂载契约不变
- Rule: 重写后的 `trust-reply-workbench.js` 必须保留同名全局 `window.TrustReplyWorkbench`
  与同形 `mount(host, options)` / `instance.unmount()`；`options` 仍接受
  `mode / source / contextPath / autoBootstrap / onUnauthorized / onChange / onComplete`。
  不再使用的 option 允许被忽略，但**不得改名、不得改必填性**。
- Applies to: `trust-reply-workbench.js` 的导出；`app.js:3941` `mountAiTrainingTrustReply`、
  `app.js:10279` `mountLiveTrustReply`。
- Violation consequence: 两个宿主同时白屏，且 `app.js` 的改动面被迫扩大。
- 来源: K-shared-workbench-fixed-mode-host-adapter

### I-25: 每次 mount 独立的请求序号与 abort
- Rule: 每个实例持有独立的 `requestSeq` 与 `AbortController` 集合；`unmount()` 必须 abort 全部在途请求
  并解绑全部监听器。切换来信后，旧请求的 late response 不得写入新实例的状态。
- Applies to: `trust-reply-workbench.js` 的实例工厂。
- Violation consequence: 快速切换来信时，上一封的草稿覆盖下一封。
- 来源: K-shared-workbench-fixed-mode-host-adapter 第 4 条

### I-26: 逐字段落的视觉标识来自服务端字段，不靠前端猜
- Rule: 段落的 `verbatim` 属性必须来自 `/api/rag-reply/compose` 返回的
  `bodyParagraphs[].renderMode`，前端**不得**通过「正文是否等于某条 answer」反推。
- Applies to: `renderDraft()`。
- Violation consequence: 运营手改一个字后紫色边框消失，看不出这段本该是逐字原文。
- 来源: original

### I-27: 手改正文后重新生成必须二次确认
- Rule: 运营编辑过正文（`dirty=true`）后点「重新生成」，必须弹确认框；确认后才丢弃手改内容。
  `dirty` 状态在页面上有可见标记。
- Applies to: `trust-reply-workbench.js` 的 `regenerate()`。
- Violation consequence: 一键把十分钟的手工润色抹掉。
- 来源: original

### I-28: 加事实 / 去事实必须走服务端重新生成
- Rule: 「加事实」把 `fact_code` 追加到 `forcedFactCodes`，「去事实」把它追加到 `excludedFactCodes`，
  两者都只改请求参数并**重新调用 compose**，前端**不得**自行拼接或删除正文段落。
- Applies to: `addFact()` / `removeFact()`。
- Violation consequence: 前端删段落会让正文与 `usedFacts` 不一致，且逐字校验形同虚设。
- 来源: original（I-14 在服务端，前端绕过它就等于没有）

### I-29: 无强制门禁
- Rule: 未识别提问数量、事实数量、`status=REVIEW` 的事实都**不得**禁用发送按钮。
  最多改变按钮配色与旁注文案。
- Applies to: 发送区渲染。
- Violation consequence: 与 D-1 冲突——本轮的口径是人是唯一的门。
- 来源: D-1；对齐既有 K-manual-rich-send-hard-gates 的「零强制门禁 + 二次确认」口径

## 现状审计

### 宿主挂载点（本计划不改）
- `src/main/resources/static/app.js:3941` `mountAiTrainingTrustReply(mail)` —
  host `#aiTrainingTrustReplyHost`，`mode:"SIMULATION"`，
  `source:{sourceType:"TRAINING_MAIL", sourceId: Number(mail.mailRecordId)}`，
  `autoBootstrap:false`，带 `onChange` 与 `onComplete`。
- `src/main/resources/static/app.js:10279` `mountLiveTrustReply(recordId)` —
  host `[data-trust-reply-live-host]`，`mode:"LIVE"`，
  `source:{sourceType:"LIVE_INBOUND", sourceId:Number(recordId)}`，
  `onComplete` 里调 `adoptTrustReplyAssembly(recordId, assembly)`。
- 运行时取用：`app.js:197-199` `requireTrustReplyWorkbenchRuntime(host)` 读 `window.TrustReplyWorkbench`。
- 脚本引入：`src/main/resources/static/index.html:2110`
  `<script src="trust-reply-workbench.js?v=20260902-monitoring-window"></script>`。
- 视图切走时卸载：`app.js:1664` `if (view !== "ai-training") unmountAiTrainingTrustReply();`。

### 被替换的前端资产
- `src/main/resources/static/trust-reply-workbench.js`，**3122 行 / 178 741 字节**。
  其中与本轮无关而必须删除的能力（按文件内函数名，`grep -n` 可复核）：
  `reorderFactIds` / `resolveFactDrop` / `restoreLockedItem` / `lockedToVersion` /
  `captureVersionState` / `reconcilePreservedVersions` / `persistResolvedItem` /
  `runItemSequence` / `requestItemVersion` / `adjustItem` / `assemble` / `rearrange` /
  `applyRearrange` / `toggleParagraphPin` / `toggleParagraphEdit` / `mergeParagraphUp` /
  `moveParagraph` / `computeReadiness` / `deleteSavedState` / `resetWorkbenchState`。
- 现存前端中文文案 154 条，其中「逐问处理」「处理方式」「采用此版本」「已锁定」「服务端整合」
  「编排预览」「配置已变化 · 请重新整合」等整类消失。

### 被替换的样式
- `styles.css` 中 `trust-reply` 相关行 **201 行**，主要块起点：
  `:5515 .trust-reply-autorun`、`:5536 .trust-reply-preanalysis`、`:5571 .trust-reply-readonly`、
  `:5597 .trust-reply-gate-list`、`:7393 .trust-reply-workbench`、`:7407 .trust-reply-busy-overlay`、
  `:7452 .trust-reply-toolbar`、`:7488 .trust-reply-mode-note`、`:7542 .trust-reply-factset`。
- 本计划保留 `.trust-reply-workbench`（容器）与 `.trust-reply-busy-overlay` 系列（生成中遮罩），
  其余删除或改写，逐条在 `## 样式契约` 中声明。

### 契约测试（G-7 必须同步处理）
| 文件 | 行数 | 处理 |
|---|---|---|
| `src/test/js/trustReplyWorkbench.test.js` | 1042 | 删除（断言逐问卡片、处理方式、版本锁定） |
| `src/test/js/trustReplyWorkbenchThreeStep.test.js` | 566 | 删除（断言三步整合/重排） |
| `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 3062 | **改写**：只保留 I-24 的挂载契约断言与缓存键三联断言，删除其余 |
| `src/test/js/autoPreviewWorkbenchHost.test.js` | 60 | 复核后按需改写（断言宿主容器存在） |

### 前端样式盘点

**可复用 class**：同 04 的表（`.panel` 932 / `.panel-head` 946 / `.button` 786 /
`.button.primary` 822 / `.muted` 2967）。
04 新增的 `--verbatim` / `--verbatim-bg` / `--verbatim-border` 三个变量与 `.rag-badge*`
（04 的 S-1、S-4）本计划**直接复用**，不重复定义。

**设计基准 token**：见 04 `## 现状审计` 的 token 表，值相同。

**保留的既有 class**
- `.trust-reply-workbench`（`styles.css:7393-7396`）— 容器，规则块不动。
- `.trust-reply-busy-overlay` / `-busy-card` / `-busy-text` / `-busy-hint`
  （`styles.css:7407-7451`）— 生成中遮罩，规则块不动。
  注意 K-trust-reply-workbench-busy-has-no-mask：这套遮罩本来就不遮挡交互，保持现状。

**改动前基线**：执行前留档
`git show HEAD:src/main/resources/static/styles.css | sed -n '5515,5625p;7393,7620p'`。

## 样式契约

### S-1: 复用 04 的逐字标识
- 复用：`--verbatim` / `--verbatim-bg` / `--verbatim-border`（04 S-1，`styles.css` `:root` 末尾）；
  `.rag-badge` 与 `.rag-badge.verbatim`（04 S-4）。
- 禁止项：不得在本计划中重新定义这四项；不得为工作台另造一套紫色。

### S-2: 两栏布局与正文段落
新增 class，逐字复制：

```css
.trust-reply-layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 350px;
    gap: 13px;
    align-items: start;
}

@media (max-width: 1100px) {
    .trust-reply-layout {
        grid-template-columns: 1fr;
    }
}

.trust-reply-doc {
    padding: 16px 18px;
    max-height: 620px;
    overflow: auto;
}

.trust-reply-para {
    position: relative;
    padding: 7px 11px 7px 12px;
    border-radius: 6px;
    font-size: 13px;
    line-height: 1.8;
    white-space: pre-wrap;
    margin-bottom: 9px;
    border-left: 3px solid var(--border);
}

.trust-reply-para.verbatim {
    border-left-color: var(--verbatim);
    background: var(--verbatim-bg);
}

.trust-reply-para.frame {
    border-left-style: dashed;
    color: var(--text-muted);
    background: var(--surface);
}

.trust-reply-para-tag {
    position: absolute;
    right: 9px;
    top: 5px;
    font-size: 9.5px;
    opacity: 0;
    transition: opacity 0.12s;
    color: var(--text-muted);
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.trust-reply-para:hover .trust-reply-para-tag {
    opacity: 1;
}

.trust-reply-para.verbatim .trust-reply-para-tag {
    color: var(--verbatim);
}

.trust-reply-para.edited {
    border-left-color: var(--error);
    background: var(--error-bg);
}
```

- DOM 骨架：
```html
<div class="trust-reply-workbench">
    <div class="trust-reply-frame-bar" data-workbench-control>…四个 select…</div>
    <div class="trust-reply-toolbar" data-workbench-control>…模型 select / 重新生成 / 编辑正文 / 复制…</div>
    <div class="trust-reply-layout">
        <section class="panel"><div class="trust-reply-doc" data-role="draft"></div></section>
        <div>
            <section class="panel"><div class="panel-head"><h2>用到了哪些事实</h2>
                <span class="muted" data-role="fact-count"></span></div>
                <div class="trust-reply-facts" data-role="facts"></div></section>
            <section class="panel"><div class="panel-head"><h2>未识别的提问</h2>
                <span class="muted" data-role="unaddressed-count"></span></div>
                <div class="trust-reply-unaddressed" data-role="unaddressed"></div></section>
        </div>
    </div>
    <div class="trust-reply-send" data-workbench-control>…</div>
</div>
```
- `.trust-reply-para.edited` 用于 I-26/I-27：运营手改过的逐字段落转红边框。

### S-3: 事实列表与未识别提问
```css
.trust-reply-facts {
    padding: 10px 13px;
}

.trust-reply-fact {
    display: flex;
    align-items: flex-start;
    gap: 7px;
    padding: 7px 0;
    border-bottom: 1px dashed var(--line);
    font-size: 12px;
}

.trust-reply-fact:last-child {
    border-bottom: none;
}

.trust-reply-fact-index {
    color: var(--text-muted);
    font-size: 10.5px;
    width: 15px;
    flex: none;
    padding-top: 2px;
}

.trust-reply-fact-main {
    min-width: 0;
    flex: 1;
}

.trust-reply-fact-code {
    color: var(--primary);
    font-weight: 600;
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: 11px;
}

.trust-reply-fact-remove {
    color: var(--text-muted);
    cursor: pointer;
    font-size: 14px;
    line-height: 1;
    padding: 2px 3px;
    flex: none;
    background: none;
    border: none;
}

.trust-reply-fact-remove:hover {
    color: var(--error);
}

.trust-reply-fact-add {
    margin-top: 9px;
    display: flex;
    gap: 6px;
}

.trust-reply-fact-add input {
    flex: 1;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    padding: 5px 9px;
    font-size: 12px;
    font-family: inherit;
    background: var(--surface);
}

.trust-reply-unaddressed {
    padding: 10px 13px;
}

.trust-reply-unaddressed-item {
    background: var(--warning-bg);
    border: 1px solid var(--warning-border);
    border-radius: var(--radius-sm);
    padding: 9px 11px;
    font-size: 11.5px;
    color: var(--warning);
    line-height: 1.65;
    margin-bottom: 7px;
}

.trust-reply-unaddressed-item:last-child {
    margin-bottom: 0;
}

.trust-reply-unaddressed-why {
    color: var(--text-muted);
    font-size: 10.5px;
    display: block;
    margin-top: 3px;
}

.trust-reply-unaddressed:empty::before {
    content: "无";
    color: var(--text-muted);
    font-size: 12px;
}
```

### S-4: 框架条与发送区
```css
.trust-reply-frame-bar {
    display: flex;
    gap: 8px;
    align-items: center;
    padding: 9px 13px;
    border-bottom: 1px solid var(--line);
    background: var(--surface);
    flex-wrap: wrap;
    font-size: 11.5px;
}

.trust-reply-frame-bar select {
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 3px 7px;
    font-size: 11.5px;
    font-family: inherit;
    background: #fff;
    max-width: 190px;
}

.trust-reply-send {
    position: sticky;
    bottom: 0;
    background: rgba(255, 255, 255, .96);
    backdrop-filter: blur(8px);
    border: 1px solid var(--border);
    border-top-width: 2px;
    border-radius: var(--radius-md);
    padding: 11px 13px;
    margin-top: 13px;
    display: flex;
    align-items: center;
    gap: 10px;
}
```
- **注意**：`.trust-reply-send` 是 sticky 浮层，`background` 必须写死
  `rgba(255, 255, 255, .96)` + `backdrop-filter: blur(8px)`，**不得**用 `var(--panel-bg)`——
  后者实值为 `rgba(255,255,255,0.55)`，底下的正文会直接透出来，且 z-index 排查不出来。
  两处先例：`.batch-manual-actions-sticky`（`styles.css:9166-9178`）、
  `.batch-config-editor-actions`（`styles.css:8684-8697`）。（来源: K-panel-bg-token-is-translucent）

### S-5: 既有 class 的处置声明
| 既有 class | styles.css 位置 | 处置 | 使用点 |
|---|---|---|---|
| `.trust-reply-workbench` | 7393-7396 | **保留不改** | `trust-reply-workbench.js` 根容器 |
| `.trust-reply-busy-overlay` / `-busy-card` / `-busy-text` / `-busy-hint` | 7407-7451 | **保留不改** | 生成中遮罩 |
| `.trust-reply-toolbar` 及其后代 | 7452-7487 | **就地修改**：删除 `.ai-reply-model-row` 相关三条后代规则（7464/7474/7483/7485-7486），保留容器规则 | 工具条 |
| `.trust-reply-factset` 及其后代 | 7542-7590 | **删除**（矩阵表格随功能消失） | 无剩余使用点 |
| `.trust-reply-autorun` / `-autorun-hint` / `-preanalysis` / `-autofilled` | 5515-5570 | **删除** | 一键预判功能不进新工作台 |
| `.trust-reply-readonly` 及其后代 | 5571-5596 | **保留不改** | 训练模式只读态仍需要 |
| `.trust-reply-gate-list` / `-gate-item` | 5597-5622 | **删除**（D-1 无门禁） | 无剩余使用点 |
| `.trust-reply-mode-note` / `::before` | 5488-5511 区块（7488-7511） | **保留不改** | 训练模式提示仍需要 |

删除任一 class 前必须先跑
`grep -rn "<class-name>" src/main/resources/static/ src/test/js/` 确认无残留使用点。

- 禁止项：inline style；未在本契约声明的新 class；对未在上表出现的既有 class 的任何修改。

## 实现方案

### T1 — 重写 `trust-reply-workbench.js`
保留幂等 IIFE + `window.TrustReplyWorkbench` 命名空间与 `mount/unmount` 契约（I-24），
保留 `requestSeq` + `AbortController` 机制（I-25）。
内部改为单一流程：`compose()` → 渲染三块 → 四个操作。
请求全部指向 `/api/rag-reply/compose`（03），旧 `/api/trust-reply/workbench/*` 一律不再调用。
`onComplete(assembly)` 的载荷改为 `{ text, usedFactCodes, ragCorpusFingerprint, unaddressed }`。
**该载荷的消费方（`adoptTrustReplyAssembly` 的分流与发送请求组装）由 03b 的 T5 实现**，
本计划只负责按此形状产出，不改 `app.js` 的采用逻辑。

遵循 I-24 ~ I-29。

### T2 — 宿主微调
`app.js` **一行不改**。两个 `mount` 调用点、`unmount` 调用点、
`requireTrustReplyWorkbenchRuntime` 全部保持原样（I-24）；
`adoptTrustReplyAssembly` 的分流已由 03b 完成，它按 `assembly` 是否含 `usedFactCodes`
自动走 RAG 分支，因此本计划产出新载荷后即自动接上。

### T3 — 样式
按 S-2 ~ S-4 逐字追加；按 S-5 逐条删除/修改既有块，删除前先跑 grep。

### T4 — 缓存键（G-5）
先跑复核命令，再把 `index.html` 三处与
`src/test/js/batchSendTaskConsoleVisualFix.test.js:49-51` 同步 bump 为 `20260902-rag-workbench`。

### T5 — 契约测试（G-7）
- 删除 `src/test/js/trustReplyWorkbench.test.js`、`src/test/js/trustReplyWorkbenchThreeStep.test.js`。
- 改写 `src/test/js/trustReplyWorkbenchSharedMount.test.js`：只留 I-24 挂载契约、
  缓存键三联相等、`unmount` 后无残留监听器三组断言。
- 新建 `src/test/js/ragWorkbenchRender.test.js`：
  - 断言 `index.html` 源文本仍含 `trust-reply-workbench.js?v=`（G-8）
  - 断言渲染函数对 `renderMode:"VERBATIM"` 的段落输出 `class="trust-reply-para verbatim"`（I-26）
  - 断言 `dirty=true` 时 `regenerate()` 会调用确认回调（I-27）
  - 断言 `removeFact()` 只改请求参数、不改本地段落数组（I-28）
  - 断言 `unaddressed.length > 0` 时发送按钮 `disabled` 属性为 `false`（I-29）
  - 断言 `styles.css` 中 `.trust-reply-send` 的 background 不含 `var(--panel-bg)`（S-4）

## 变更文件清单

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/resources/static/trust-reply-workbench.js` | 重写 |
| 2 | `src/main/resources/static/index.html` | 修改（三处缓存键） |
| 3 | — | （`app.js` 不在本计划范围；采用路径的改动属 03b） |
| 4 | `src/main/resources/static/styles.css` | 修改（追加 S-2~S-4；按 S-5 删除/修改既有块） |
| 5 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改（缓存键 49-51） |
| 6 | `src/test/js/trustReplyWorkbench.test.js` | 删除 |
| 7 | `src/test/js/trustReplyWorkbenchThreeStep.test.js` | 删除 |
| 8 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 改写 |
| 9 | `src/test/js/autoPreviewWorkbenchHost.test.js` | 改写 |
| 10 | `src/test/js/ragWorkbenchRender.test.js` | 新增 |

文件数 10，子系统 1（前端静态页）。后端零改动。

## 验证命令

> 本项目必须用 JDK 11（zulu-11）。前端 JS 用例由 `exec-maven-plugin` 绑在 `mvn test` 的 test 阶段
> （`pom.xml:186-232`）；`verify.sh` 只跑单个文件，不能当前端回归门禁。

```bash
# 缓存键复核（改 index.html 之前先跑）
grep -rn "v=$(grep -o 'styles.css?v=[^"]*' src/main/resources/static/index.html | cut -d= -f3)" src/test/js/

# 删 class 前的残留使用点复核（对 S-5 中标为「删除」的每个 class 各跑一次）
grep -rn "trust-reply-factset" src/main/resources/static/ src/test/js/
grep -rn "trust-reply-autorun" src/main/resources/static/ src/test/js/
grep -rn "trust-reply-gate-list" src/main/resources/static/ src/test/js/

# 全量测试（回归门禁，含前端 JS 用例）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划新增/改写的前端测试
node --test src/test/js/ragWorkbenchRender.test.js
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js
node --test src/test/js/autoPreviewWorkbenchHost.test.js

# 前端 JS 全量 + 语法检查
node --test src/test/js/*.test.js
node --check src/main/resources/static/trust-reply-workbench.js
node --check src/main/resources/static/app.js

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：`mvn test` 退出码 0 且 `Tests run: N, Failures: 0, Errors: 0`；
`node --test` 退出码 0 且 `# fail 0`；`node --check` 无输出；`git diff --check` 无输出。
来源：`CLAUDE.md:10-27` Commands + `CLAUDE.md:66` 团队沉淀知识。

## 验收标准

- **I-24**：`trustReplyWorkbenchSharedMount.test.js` 断言 `window.TrustReplyWorkbench.mount`
  为函数、返回对象含 `unmount`；断言 `app.js` 中两个 mount 调用点的 options 键集合未变
  （`grep -n "mode:" -A 8 app.js` 对比 diff 为空）。
- **I-25**：断言 `unmount()` 后 `AbortController.abort` 被调用、且随后到达的 late response
  不改变 host 的 innerHTML。
- **I-26**：断言渲染 `{renderMode:"VERBATIM"}` 段落时 class 含 `verbatim`；
  断言渲染函数源码中**不含**按正文文本比对 answer 的逻辑
  （`grep -n "answer ===" trust-reply-workbench.js` 无输出）。
- **I-27**：断言 `dirty=true` 时 `regenerate()` 调用了注入的确认函数且未发请求；
  确认返回 true 后才发请求。
- **I-28**：断言 `removeFact("KB-FUND-034")` 后本地 `paragraphs` 数组长度不变，
  且下一次请求体 `excludedFactCodes` 含该值。
- **I-29**：断言 `unaddressed.length === 3` 时发送按钮的 `disabled` 为 false。
- **S-2 ~ S-4**：`ragWorkbenchRender.test.js` 对每个新增 class 断言其 CSS 与契约**逐字一致**；
  单独断言 `.trust-reply-send` 的规则块含 `rgba(255, 255, 255, .96)` 与 `backdrop-filter: blur(8px)`
  且不含 `var(--panel-bg)`。
- **S-5**：对标为「删除」的 class 各跑一次 grep，输出为空；
  对标为「保留不改」的 class，`git diff styles.css` 中其规则块无 hunk。
- **G-5**：三处 `?v=` 同值且等于 `20260902-rag-workbench`；固定值测试同步更新。
- **G-7**：`grep -rn "trust-reply/workbench" src/test/js/` 无输出（前端测试不再引用旧端点）。
- 回归：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 线上入口的新工作台
- 前置条件: 01-03 已落地；库中有一封待处理来信。
- 操作步骤:
  1. 侧栏进「收发件箱」，打开一封待处理来信的处理面板。
  2. 观察可信工作台区域。
  3. 点「重新生成」，等待完成。
- 预期结果: 第 2 步页面上只有三块——正文、用到了哪些事实、未识别的提问；
  **看不到**逐问卡片、处理方式下拉、「采用此版本」「已锁定」「服务端整合」「编排预览」任何一个；
  第 3 步生成完成后正文出现，其中若干段有紫色左边框，首尾各两段是虚线灰色（回复框架）。
- 覆盖: 需求 observable outcome 1、2

### A-2: 训练入口同样可用（回归）
- 前置条件: A-1 已通过。
- 操作步骤: 侧栏进「AI 回复训练 → 历史邮件模拟回复」，选一封历史邮件，生成一次。
- 预期结果: 与 A-1 相同的三块界面；模式提示仍显示「模拟 · 不外发」；不会白屏或报
  `TrustReplyWorkbench is not defined`。
- 覆盖: What must NOT change 第 1 条；I-24

### A-3: 四个操作
- 前置条件: A-1 已通过，已有一份草稿。
- 操作步骤:
  1. 在「用到了哪些事实」里点某条非强制事实的 `×`。
  2. 等待重新生成完成，看该事实是否消失。
  3. 在搜索框输入 `KB-COMM-044`，点「添加」。
  4. 等待重新生成，看它是否出现且标为「强制」。
  5. 点「编辑正文」，改一个词。
  6. 点「重新生成」。
- 预期结果: 第 2 步该事实从列表消失且正文相应段落不再出现；第 4 步 `KB-COMM-044` 出现在列表中；
  第 5 步出现「已手工编辑」标记；第 6 步**弹出确认框**，取消则正文保持手改内容。
- 覆盖: 需求 observable outcome 3；I-27；I-28

### A-4: 逐字段落改了会变红
- 前置条件: A-1 已通过，草稿中有紫色边框段落。
- 操作步骤: 点「编辑正文」，在某个紫色段落里删掉一个词，点别处失焦。
- 预期结果: 该段落左边框由紫变红、底色变浅红，出现「已脱离事实原文」提示；
  **不阻断**，仍可发送。
- 覆盖: I-26；I-29

### A-5: 未识别提问显示为来信逐字片段
- 前置条件: 03 的 A-2 已通过（来信里有无事实支撑的提问）。
- 操作步骤: 生成后查看右下「未识别的提问」块。
- 预期结果: 至少 1 条；把其中的引文复制到来信原文里搜索能精确命中。
- 覆盖: 需求 observable outcome 1

### A-6: 发送前无强制门禁
- 前置条件: A-5 的场景（未识别提问 ≥1）。
- 操作步骤: 观察发送按钮。
- 预期结果: 按钮**可点**（不是灰的），旁边有「未识别提问 N 项」的旁注。
- 覆盖: I-29；D-1

### A-7: 采用到人工回复并真正发出（依赖 03b）
- 前置条件: **03b 已落地**；A-1 已通过。
- 操作步骤:
  1. 在工作台点「采用到人工回复」。
  2. 检查下方富文本编辑区是否收到正文。
  3. 走完发送二次确认，真正发出到测试邮箱。
  4. 查库 `SELECT fact_code, ordinal FROM mail_record_rag_fact
     WHERE mail_record_id = <新记录 id> ORDER BY ordinal;`
- 预期结果: 第 2 步正文完整落入编辑区；第 3 步**发送成功**，不出现 400/422；
  第 4 步返回的 `fact_code` 顺序与右栏「用到了哪些事实」一致。
  若第 3 步报 400 或 422，说明 03b 未落地或未生效——**停止验收，回到 03b**。
- 覆盖: What must NOT change 第 2 条；D-11

### A-8: 回归 —— 回复框架片段可改且生效
- 前置条件: A-1 已通过。
- 操作步骤: 在工作台顶部框架条切换「结束语」下拉到另一个片段。
- 预期结果: 正文末尾的虚线段落随之变化；正文中间部分（模型产出）不变。
- 覆盖: What must NOT change 第 3 条

### A-9: UI 目测 —— 与契约实值一致
- 前置条件: A-1 已通过。
- 操作步骤: 开发者工具检查：
  1. `.trust-reply-para.verbatim` 的 `border-left-color`。
  2. `.trust-reply-para.frame` 的 `border-left-style`。
  3. `.trust-reply-send` 的 `background-color` 的 alpha。
  4. `.trust-reply-layout` 在 1400px 与 1000px 下的列数。
- 预期结果: ① `rgb(124, 58, 237)`；② `dashed`；③ alpha **≥ 0.96**（不是 0.55）；
  ④ 1400px 两列、1000px 一列。
- 覆盖: S-1；S-2；S-4
