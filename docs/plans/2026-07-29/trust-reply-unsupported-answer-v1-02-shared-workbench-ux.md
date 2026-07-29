# 可信回复工作台无依据回答 V1－02：共享工作台交互与翻译

日期：2026-07-29
状态：待批准、未执行
前置：[01 后端逐项语义与版本合同](./trust-reply-unsupported-answer-v1-01-backend-item-semantics.md) 已通过
后续：[04 训练评估合格后入索引](./trust-reply-unsupported-answer-v1-04-training-qualified-archive.md)、[05 正式发送成功后入索引](./trust-reply-unsupported-answer-v1-05-live-send-qualified-archive.md)

## 需求描述

只修改共享 `trust-reply-workbench.js`，让训练模拟与真实来信同时获得完全相同的新体验：

- bootstrap 后在存在有据/部分有据项时自动生成初始版本，删除“生成全部版本”；全为无据项时不发 FULL_DRAFT。
- 有据项生成成功后默认采用、默认收起；无据项默认展开，等待操作员说明。
- 无据项可按说明逐项 AI 生成；点击“采用此版本”必须立刻出现可见状态变化。
- 无据问题默认显示中文译文并保留手动按钮；所有非空 AI 回答提供手动中文翻译。
- `OMIT` 可直接确认，最终零输出。
- assemble 只序列化同一个已采用版本，消除 `TRUST_REPLY_LOCKED_ITEM_INVALID` 的混装来源。

本计划只同步修改后端 UNSUPPORTED 推荐值，不改生成/assemble；不改后端翻译服务、训练/真实宿主适配器或 ES。

必须不改变：

1. `window.TrustReplyWorkbench.mount/unmount` 公共 API、训练与真实固定 mode/source adapter。
2. 现有 model/TTL/cancel/progress、事实选择、source/evidence stale 和服务端 assemble authority。
3. 训练完成只进入评估，真实完成只采用到人工编辑器；组件不发送邮件。
4. 既有 `/api/translate` 请求/响应合同和页面其他翻译入口。

明确不纳入：后端 handling 实现、ES/索引 Tab、训练归档、正式发送 payload、任何 CSS 重设计。

## 关键不变量

### Invariant I-1: 两个入口使用同一内部状态机

- Rule: 两个宿主只传固定 context/callback；全部新增 DOM、状态、transport 和事件只实现一次。
- Applies to: workbench mount/render/events/fetch/unmount、训练/live adapter 回归。
- Violation consequence: 两页再次来回修改、功能漂移或模拟入口获得发送能力。
- 来源: K-shared-workbench-fixed-mode-host-adapter。

- 训练和真实页面继续只调用 `window.TrustReplyWorkbench.mount(host, options)`；公共组件内部不得检查页面专属 DOM ID。
- `mode` 只决定“模拟/正式”和完成按钮文案；request card、处理方式、翻译、生成、采用、整合完全相同。（来源：K-shared-workbench-fixed-mode-host-adapter）
- IIFE 保持幂等；不得引入顶层 `const $`、重复全局变量或 `document.write`，避免再次出现 `Identifier '$' has already been declared`。

### Invariant I-2: 草稿状态与已采用版本分离

- Rule: draftHandling/activeVersion 不能充当 resolvedVersion；任何决定变化必须使旧 resolved/assembly 失效。
- Applies to: request state、change/input handlers、version selection、assemble serialization。
- Violation consequence: handling 与正文混装，复现 `TRUST_REPLY_LOCKED_ITEM_INVALID`。
- 来源: K-trust-reply-resolved-version-single-source。

每个 request 的状态改为：

```text
draftHandling
instruction
versions[]
activeVersionId
resolvedVersionId
expanded
pending/error
questionTranslation
answerTranslationsByVersionId
```

- `draftHandling` 只表示下拉框下一次生成意图。
- `activeVersionId` 只表示版本下拉当前预览。
- `resolvedVersionId` 是 assemble 的唯一来源；其 handling 和全部语义字段来自 versions 中同一对象。
- 修改 handling、instruction 或 active version 后，若不再精确对应 resolved version，立即清空 `resolvedVersionId` 与旧 assembly。
- 禁止用 `request.draftHandling` 覆盖 resolved version 的 handling。

### Invariant I-3: 有据项仅在 canonical 首版本成功时自动采用

- Rule: 只自动 resolve FULL_DRAFT 返回且 requestKey/status 匹配的 GROUNDED/PARTIAL 版本；其他项保持未处理。
- Applies to: automatic generation result reducer、card expanded/resolved/progress state。
- Violation consequence: 失败或无据回答被伪装为默认完成。
- 来源: original。

- 自动 FULL_DRAFT 成功后，只对返回的 `GROUNDED/PARTIAL` canonical 初始版本设置 `activeVersionId=resolvedVersionId`。
- 自动采用后 badge 为“已处理”，卡片 body 收起；用户可展开查看、切换 handling 或重新 AI 调整。
- UNSUPPORTED、无版本、生成失败、版本 key 不匹配均保持未处理且展开。
- 用户修改自动采用项后必须重新显式采用新版本，不可保留旧绿色状态。

### Invariant I-4: 无据项默认等待人工说明

- Rule: UNSUPPORTED 默认展开并选择新 handling；空说明不能生成，单项生成只改变目标项。
- Applies to: `TrustReplyWorkbenchService.recommendedHandling`、state initialization、labels/disabled state、adjustItem reducer。
- Violation consequence: 人工说明被忽略，或调整一项污染全局。
- 来源: original。

- UNSUPPORTED 默认 `draftHandling=ANSWER_FROM_OPERATOR_INPUT`、`expanded=true`、无 active/resolved version。
- 文本框标签为“回答说明（AI 将仅据此生成）”；空说明时 AI 调整按钮禁用，并显示就地提示。
- `ACKNOWLEDGE_PENDING` 仍可主动选择；此时标签退回“AI 调整要求（仅调整表达，可留空）”。
- 单项生成只追加该 request 的版本，不改变其他 request 的版本、采用状态、展开状态或译文。

### Invariant I-5: 采用动作即时且可访问

- Rule: 同一渲染周期同步 badge、aria、按钮、进度、展开状态；取消采用反向同步并清 assembly。
- Applies to: resolve-item/toggle-item events、renderRequest/renderSummary。
- Violation consequence: 用户认为按钮无效，或 UI 与可 assemble 状态不一致。
- 来源: original。

- 未采用版本按钮文案“采用此版本”；点击后同一渲染周期内：badge=`已处理`、`aria-pressed=true`、按钮=`取消采用`、进度加一，可选择自动收起 body。
- 取消采用后 badge=`待处理`、进度减一、assembly 清空、body 展开。
- pending/失败状态保留旧可用版本，不自动取消此前已采用版本，除非用户修改了决定字段。

### Invariant I-6: 翻译是有身份的易失展示状态

- Rule: question translation 绑定 sourceVersion+requestKey，answer translation 绑定 versionId；不改变决定、版本或 assembly。
- Applies to: auto/manual translation requests、render、source switch、unmount。
- Violation consequence: 串译文、迟到响应污染新邮件，或译文进入发送内容。
- 来源: original。

- 无据问题 bootstrap 后自动调用一次既有 `POST /api/translate`；成功即展开译文，按钮显示“收起译文”。
- 同一问题仍有手动收起/展开/失败重试按钮。
- 每个非空 AI version 的回答提供手动翻译；译文以 `versionId` 为 key 缓存，切换版本显示对应译文。
- 空 OMIT 不显示翻译按钮。
- source 切换/unmount 时 abort 请求或用 mount token 丢弃迟到响应；翻译不得触发 `onChange`、版本失效或重新 assemble。

### Invariant I-7: OMIT 只物化空版本

- Rule: 选择省略后通过 ADJUST_ITEM 取得 canonical OMIT 并 resolve；UI 提示不得进入 answerText。
- Applies to: handling change、resolve action、answer rendering、assemble payload。
- Violation consequence: 省略项输出占位文本。
- 来源: K-locked-item-assembly-list-not-set。

- 选择 OMIT 后点击“确认省略”调用既有 ADJUST_ITEM 物化 canonical OMIT version；成功后直接设为 resolved 并可收起。
- UI 可以显示“此项将省略”的状态提示，但不得把该显示文案放入 version.answerText 或 assemble 请求。

### Invariant I-8: 自动完整生成按需且每个 mount/sourceVersion 至多一次

- Rule: 仅当 coverage 含 GROUNDED/PARTIAL 时，在 bootstrap 后执行一次 FULL_DRAFT；全 UNSUPPORTED 为零次；失败/取消不自动循环。
- Applies to: bootstrap lifecycle、generation state、render、retry behavior。
- Violation consequence: 重复扣费、版本重复、无限生成或无法取消。
- 来源: original。

- bootstrap 成功且至少一个 item 为 GROUNDED/PARTIAL 时自动调用一次 FULL_DRAFT；全 UNSUPPORTED 时不调用；同一 mount/sourceVersion 不因 render、展开或翻译重复触发。
- 生成期间沿用现有进度、TTL 和取消；取消/失败后不自动无限重试，用户通过逐项 AI 调整恢复。
- 删除 `data-action="generate-all"` DOM、点击分支及其旧合同测试；保留 model/TTL/cancel 控件。

### Invariant I-9: assemble 只序列化 resolved version

- Rule: canAssemble 和 lockedItems 都只读 resolvedVersion；所有语义字段从同一对象复制。
- Applies to: canAssemble、assemble request、client precheck。
- Violation consequence: 前端混装版本或把未采用预览发给服务端。
- 来源: K-trust-reply-resolved-version-single-source。

- `canAssemble` 要求每个 request 有 resolvedVersion 且无 pending；不再读取 active version 兜底。
- `lockedItems` 从 `resolvedVersionId -> versions.find` 生成，逐字段复制版本；请求不读取 `draftHandling`。
- 如果 resolvedVersion 不存在、requestKey 不符或语义字段缺失，前端阻止整合并定位该项，不拼装半有效请求。

## 样式契约

### S-1: 复用现有设计 token，不改 `styles.css`

- 复用: `.button/.primary/.secondary/.danger/.small`（`styles.css:587-655,2244-2249`）、`.compose-panel.trust-reply-item` 与 workbench classes（7339-7599）、翻译 classes（1664-1694）。禁止用近似新 class 替代。
- 新增: 无新 CSS class、无 CSS 规则块、无 `styles.css` 修改。
- DOM 结构: 全部 card/button/translation 元素必须使用 S-2 骨架和上述 class。
- 禁止项: inline style；未在 S-1/S-2 声明的新 class；修改任何既有 class 规则。

- 颜色：`--primary #2563eb`、`--success #059669`、`--warning #d97706`、`--error #e11d48`、`--text-main #1e293b`、`--text-muted #94a3b8`（`styles.css:1-44`）。
- 圆角：`--radius-sm 7px`、`--radius-md 10px`、`--radius-lg 14px`；阴影只使用现有 `--shadow-sm/md/lg`（46～53 行）。
- 按钮只使用 `.button`、`.primary`、`.secondary`、`.danger`、`.small`（587～655、2244～2249 行）。
- 卡片继续使用 `.compose-panel.trust-reply-item` 及既有 workbench classes（7339～7599 行）。
- 翻译继续使用 `.translatable-body-block`、`.btn-translate`、`.translation-text.pre`（1664～1694 行）。
- 禁止新增 inline color、box-shadow、border-radius 或通用 `<button>/<select>` 覆盖；本计划不修改 `styles.css`。

### S-2: request card 目标 DOM 骨架

- 复用: `.trust-reply-item-head/.trust-reply-item-index/.trust-reply-item-title/.trust-reply-coverage/.trust-reply-item-controls/.trust-reply-field/.trust-reply-answer/.trust-reply-item-actions`（`styles.css:7366-7543`）。
- 新增: 只新增 `data-role/data-action/data-resolved/aria-*` 属性和无 class 的 item-body wrapper；不新增 CSS。
- DOM 结构: 必须按下列层级输出；执行时可插入既有 select/textarea/error 内容，但不可改变 header/body/translation/action 的父子关系。
- 禁止项: 把训练/live 渲染成两套模板；用 `<details>` 默认样式替代现有 article；在隐藏 body 上写内联 display。

每项保持相同 article，不把训练/真实模式分叉：

```html
<article class="compose-panel trust-reply-item" data-role="item" data-locked="true|false">
  <div class="trust-reply-item-head">
    <span class="trust-reply-item-index">…</span>
    <div class="trust-reply-item-title"><strong>原问题</strong><span class="trust-reply-coverage">…</span> <button class="button small secondary" data-action="toggle-item" aria-expanded="true|false">展开|收起</button></div>
    <span class="badge ok|warn">已处理|待处理</span>
  </div>
  <div data-role="item-body" hidden>
    <div class="translatable-body-block" data-role="question-translation">…</div>
    <div class="trust-reply-item-controls">…</div>
    <label class="trust-reply-field">回答说明 / AI 调整要求…</label>
    <div class="trust-reply-answer">
      <div class="trust-reply-answer-body pre">…</div>
      <div class="translatable-body-block" data-role="answer-translation">…</div>
    </div>
    <div class="trust-reply-item-actions">…</div>
  </div>
</article>
```

- 保留既有 `data-locked` 作为 CSS 状态钩子，值由新的 resolved 状态驱动；禁止改为只有 `data-resolved` 导致现有 7339～7364 行样式失效。
- `hidden` 是唯一折叠机制；不另造高度动画。
- UNSUPPORTED 首屏 `aria-expanded=true`；有据自动采用成功后为 false。
- 翻译按钮文案固定：`🌐 翻译为中文`、`翻译中…`、`收起译文`、`翻译失败，重试`。

### S-3: 状态文案与既有 badge/button 映射

- 复用: `.badge/.ok/.warn`（`styles.css:829-859`）和 `.button.primary/.secondary`（623-645）。
- 新增: 无新 class；状态差异只通过既有 class、标准属性与下表实值文案表达。
- DOM 结构: badge 位于 item head；主动作位于 `.trust-reply-item-actions`；progress 继续位于既有 summary。
- 禁止项: 只改内部变量不改可见状态；红色表示普通待处理；保留“所有项目须显式锁定”旧文案。

| 状态 | badge | 主动作 | 内容区 |
|---|---|---|---|
| 有据自动采用 | `已处理` + `.ok` | `取消采用` | 默认收起 |
| 无据未生成 | `待处理` + `.warn` | `AI 生成回答` disabled until description | 展开 |
| 有版本未采用 | `待处理` + `.warn` | `采用此版本` | 展开 |
| 已采用 | `已处理` + `.ok` | `取消采用` | 可收起 |
| OMIT 已确认 | `已省略` + `.ok` | `取消省略` | 默认收起 |
| 生成失败 | `待处理` + `.warn` | `重试 AI 调整` | 展开并显示 `.ai-reply-error` |

摘要文案由“已锁定 n/m，所有当前项目须显式锁定”改为“已处理 n/m”；不得继续暗示有据项必须人工锁定。

### S-4: 响应式与可访问性

- 复用: workbench 移动端规则（`styles.css:7625-7647`）、`.ai-reply-feedback/.ai-reply-error`、原生 `[hidden]`。
- 新增: 无 CSS；只增加 `role/status/alert`、`aria-live`、`aria-expanded`、`aria-pressed` 和真实 `disabled`。
- DOM 结构: toggle button 位于 item head 的 `.trust-reply-item-title` 内，badge 保持 head 的第三个直接子元素；item body 紧随 head；translation status 保持在其所属 block 内。
- 禁止项: 仅用颜色表达状态；伪 disabled class；翻译失败弹全局 modal；新增动画/固定高度。

- 沿用 workbench 现有 7625～7647 行移动端布局；新增 toggle 使用已有 small button，窄屏可换行。
- 状态区保留 `role=status/alert` 与 `aria-live=polite`。
- 折叠按钮同步 `aria-expanded`；采用按钮同步 `aria-pressed`；disabled 必须真实使用 HTML `disabled`。
- 原问题、AI 回答和译文全部通过 `escapeText`，禁止把翻译 API 文本写入 `innerHTML` 未转义。

## 现状审计

### 状态混装根因

- `trust-reply-workbench.js:325-329` 当前 request 同时保存可变 `handling`、`activeVersionId`、`lockedVersionId`。
- handling/version 变化会清空 active/locked，但 UI 异步重渲染和 OMIT 物化仍可能形成不同来源的字段。
- `assemble:541-575` 当前显式发送 `handling: request.handling`，但正文、claims、model、kind 来自 `activeVersion(request)`；这是 `TRUST_REPLY_LOCKED_ITEM_INVALID` 的结构性来源。
- `canAssemble:588-590` 只检查 locked ID 和 active version，未证明 locked version 自身就是完整序列化来源。

### “锁定无变化”体验根因

- `toggleLock:677-701` 只变更 `lockedVersionId` 后整体 render；按钮与 badge 都使用“锁定”术语，未收起卡片，也没有稳定的 resolved 概念。
- `renderRequest:750-760` 所有卡片一直完整展开，状态差异弱；生成失败时按钮仍可能给用户“点击没反应”的感受。
- `renderSummary:763-768` 强制提示所有项目显式锁定，不符合新的有据默认采用规则。

### 分阶段兼容 interaction point

- 01 阶段已让 UNSUPPORTED allowedHandlings 包含新值，但为兼容旧静态资源暂时保留 recommended=ACK。
- 本阶段必须在同一提交中加入 `HANDLING_LABELS.ANSWER_FROM_OPERATOR_INPUT`、空说明 disabled gate 和 `TrustReplyWorkbenchService.recommendedHandling=ANSWER_FROM_OPERATOR_INPUT`；只改一边会分别造成裸枚举/空说明 422 或产品默认仍是安全确认语。

### 全量生成入口

- `generateAll:390` 是必要的内部操作，但 `renderToolbar:747` 同时暴露 `data-action="generate-all"`。
- 旧测试在 `aiReplyLoadingFeedback.test.js:613` 和 `trustReplyWorkbenchSharedMount.test.js:265/291/321/435/478/485` 主动点击该按钮；删除 UI 时必须同步退役这些断言。（来源：K-ui-removal-retires-obsolete-contract-tests）

### 翻译现状

- `app.js:1483-1554` 已有页面级 `translatableBody/onTranslateClick`，调用 `/api/translate` 并正确 escape。
- 公共 workbench 是独立 IIFE，不能依赖 `app.js` 内部函数或全局 `$`；应在组件内实现小型同合同 transport/state，不复制后端。
- 现有 CSS 已覆盖翻译块，无需新增样式。

### 前端样式盘点与改动前基线

- 可复用 class: `.trust-reply-item`（`styles.css:7339-7364`）按 `data-coverage/data-locked` 控制边框与已处理绿色态；`.trust-reply-item-head`（7366-7374）固定三列；`.trust-reply-item-actions`（7543）右对齐；`.trust-reply-summary/.trust-reply-progress`（7546-7580）；翻译三类 class（1664-1694）。
- 设计基准 token: primary `#2563eb`、success `#059669`、warning `#d97706`、error `#e11d48`、text `#1e293b`、muted `#94a3b8`；radius `7/10/14px`；button `12px/32px`；body `13px/1.5`。
- DOM 注册约定: 一个 idempotent IIFE 暴露 `window.TrustReplyWorkbench`；host 只 mount/unmount；所有事件在 host 上委托。
- 改动前 card 关键片段（`trust-reply-workbench.js:750-760`）为：

```html
<article class="compose-panel trust-reply-item" data-role="item" data-request-key="${escapeText(request.requestKey)}" data-coverage="${escapeText(coverage)}" data-locked="${locked}">
  <div class="trust-reply-item-head">
    <span class="trust-reply-item-index">…</span>
    <div class="trust-reply-item-title">…</div>
    <span class="badge ${locked ? "ok" : ""}">${locked ? "已锁定" : "待处理"}</span>
  </div>
  <div class="trust-reply-item-controls">…</div>
  <label class="trust-reply-field">AI 调整要求…</label>
  <div class="trust-reply-answer">…</div>
  <div class="trust-reply-item-actions">…</div>
</article>
```

- 改动前 toolbar 明确包含 `<button type="button" class="button primary" data-action="generate-all">生成全部版本</button>`；本计划删除该 DOM 和事件。
- 既有 CSS 关键规则逐字为：

```css
.trust-reply-item[data-locked="false"]:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--primary) 20%, var(--panel-border));
}

.trust-reply-item[data-locked="true"] {
  border-color: var(--success-border);
  border-left-color: var(--success);
  background: linear-gradient(to right, color-mix(in srgb, var(--success) 4%, #fff), #fff 30%);
  box-shadow: none;
}
```

本计划只改变 DOM 数据和值，不修改上述规则；`data-locked=true` 继续是已处理视觉态。

## 实现方案

### T0：执行前研究检查点

- Governs：I-1～I-9、S-1～S-4。
- Exact files: 本计划清单 1～8。
- 重新 `rg` `generate-all/lockedVersionId/request.handling/activeVersionId/data-locked/btn-translate/TrustReplyWorkbench.mount` 的全部调用和测试断言；重新读取 `styles.css:1664-1694,7339-7647`。
- 若发现 `app.js` 内存在第二套工作台内部状态/DOM，或必须新增/修改 CSS 才能容纳四列 header，停止并修订 S-2；不得临时加 class/inline style。
- 先运行清单内现有 Kotlin/JS 测试确认基线，再开始 T1。

### T1：与新 UI 同批切换服务端推荐值

- Governs：I-4。
- Exact files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt`、`src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt`。
- 先把测试期望从兼容 ACK 改为 `ANSWER_FROM_OPERATOR_INPUT` 并确认失败，再只修改 UNSUPPORTED recommended；allowed matrix 和其他 status 不变。
- 该后端变更必须与 T2～T8 的静态资源同一发布版本交付。

### T2：先改测试夹具为自动初始化流程

- Governs：I-1、I-3、I-8、S-3、S-4。
- Exact files: `src/test/js/trustReplyWorkbenchSharedMount.test.js`、`src/test/js/aiReplyLoadingFeedback.test.js`。
- `trustReplyWorkbenchSharedMount.test.js` 的 mixed-coverage mock server 在 bootstrap 后等待自动 FULL_DRAFT，而不是点击 generate-all；另加 all-unsupported fixture 断言零 FULL_DRAFT。
- 添加“有据时每个 mount/sourceVersion 只调用一次 FULL_DRAFT”“全无据零调用”“训练/live 请求结构一致”“unmount 丢弃迟到结果”。
- `aiReplyLoadingFeedback.test.js` 删除必须存在 generate-all 的旧断言，改为断言源码/DOM 不含该 action 且仍有 cancel/progress。

### T3：重构每项状态，不改变公共 mount API

- Governs：I-1～I-5、I-9、S-1、S-2。
- Exact files: `src/main/resources/static/trust-reply-workbench.js`、`src/test/js/trustReplyWorkbenchSharedMount.test.js`、`src/test/js/trustReplyWorkbench.test.js`。
- 用 `draftHandling/resolvedVersionId/expanded` 替换 `handling/lockedVersionId` 的混合语义。
- 增加 `HANDLING_LABELS.ANSWER_FROM_OPERATOR_INPUT="按回答说明生成"`；UNSUPPORTED 使用“回答说明（AI 将仅据此生成）”，其他 handling 保留“AI 调整要求（仅调整表达）”。
- 增加纯函数：`activeVersion(request)`、`resolvedVersion(request)`、`invalidateDecision(request)`、`applyInitialVersions(result)`、`serializeResolvedVersion(request)`。
- 每次决定变化先清 assembly、触发 `onChange`，再渲染；翻译/展开不触发决定变化。

### T4：bootstrap 后自动执行一次 FULL_DRAFT

- Governs：I-3、I-8、S-3、S-4。
- Exact files: `src/main/resources/static/trust-reply-workbench.js`、`src/test/js/trustReplyWorkbenchSharedMount.test.js`、`src/test/js/aiReplyLoadingFeedback.test.js`。
- bootstrap 完成并建好 requests 后，仅在存在 GROUNDED/PARTIAL 时启动现有 `generateAll()` 内部流程；函数改名可选，但不再绑定按钮。
- 将返回 versions 按 requestKey 追加；只自动 resolve `GROUNDED/PARTIAL`。
- 自动生成错误保持全局/逐项可见；无据项仍可单项生成，不能被全局错误禁用。

### T5：实现展开/收起与明确采用反馈

- Governs：I-3、I-4、I-5、I-7、S-1～S-4。
- Exact files: `src/main/resources/static/trust-reply-workbench.js`、`src/test/js/trustReplyWorkbenchSharedMount.test.js`、`src/test/js/trustReplyWorkbench.test.js`。
- render 按 S-2 输出 header + body；事件委托处理 `toggle-item`。
- `lock-item` 重命名为语义化 `resolve-item`（测试与 DOM 一起更新）；按钮文案使用 S-3。
- OMIT 通过 ADJUST_ITEM 物化后自动 resolved；其他新 AI 版本只 active，不自动 resolved。

### T6：接入组件内逐项翻译状态

- Governs：I-6、S-1～S-4。
- Exact files: `src/main/resources/static/trust-reply-workbench.js`、`src/test/js/trustReplyWorkbenchSharedMount.test.js`、`src/test/js/trustReplyWorkbench.test.js`。
- 复用 `/api/translate` 请求/响应合同，在组件内实现 `requestTranslation`、toggle/retry 和 escape。
- bootstrap 后对 UNSUPPORTED question 启动非阻塞自动翻译；使用 requestKey + sourceVersion token。
- answer 手动翻译按 versionId 缓存；切换版本不串译文，版本淘汰时可随 request 生命周期清理。
- translation fetch 纳入 mount AbortController；失败只更新翻译局部状态。

### T7：从 resolved version 序列化 assemble

- Governs：I-2、I-7、I-9、S-3、S-4。
- Exact files: `src/main/resources/static/trust-reply-workbench.js`、`src/test/js/trustReplyWorkbenchSharedMount.test.js`。
- `canAssemble` 遍历 resolvedVersion，不再允许 active fallback。
- lockedItems 逐字段从 resolvedVersion 复制，包括新 `operatorInstruction`；不得引用 request.draftHandling 或 textarea 当前值。
- 客户端预检查失败时在对应项设置 error 并展开；服务端 422 仍原样显示稳定 code。

### T8：更新静态资源版本与回归测试

- Governs：I-1～I-9、S-1～S-4。
- Exact files: `src/main/resources/static/index.html`、`src/test/js/batchSendTaskConsoleVisualFix.test.js`，以及本计划其余清单文件用于测试执行。

- `index.html` 同步提升 `trust-reply-workbench.js` 与 `app.js` 查询版本；即使本阶段 app.js 未改，也保持两者同版本 convention。
- 更新 `batchSendTaskConsoleVisualFix.test.js` 的版本合同。
- 执行：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=TrustReplyWorkbenchItemFlowTest test
node --check src/main/resources/static/trust-reply-workbench.js
node --test src/test/js/trustReplyWorkbench.test.js
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js
node --test src/test/js/aiReplyLoadingFeedback.test.js
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js
node --test src/test/js/*.test.js
git diff --check
```

## 变更文件清单

| # | 文件 | 动作 | 目的 |
|---:|---|---|---|
| 1 | `src/main/resources/static/trust-reply-workbench.js` | 修改 | 新状态机、自动生成、默认采用/收起、翻译、canonical assemble |
| 2 | `src/main/resources/static/index.html` | 修改 | 静态资源 cache-buster |
| 3 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 修改 | 双入口自动流程、逐项状态、翻译、整合行为测试 |
| 4 | `src/test/js/trustReplyWorkbench.test.js` | 修改 | runtime/DOM/安全静态合同 |
| 5 | `src/test/js/aiReplyLoadingFeedback.test.js` | 修改 | 删除旧全量按钮合同，固定自动生成反馈 |
| 6 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改 | 静态资源版本合同 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 修改 | 与新 UI 同批切换 UNSUPPORTED recommended handling |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | 修改 | 推荐值分阶段兼容与最终值合同 |

文件数：8；子系统：共享前端工作台、后端推荐值，共 2 个。本阶段明确不修改 `app.js`、`styles.css` 或其他后端语义。

## 验收标准

- I-1: 同一 shared-mount fixture 对 TRAINING/LIVE 断言内部 DOM/action 集一致；源码只有一个 renderRequest/adjustItem/assemble 实现。
- I-2: 状态测试分别改变 handling、instruction、activeVersion，断言只清目标 resolved/assembly，其他 request 不变。
- I-3: FULL_DRAFT 只自动 resolve 匹配的 GROUNDED/PARTIAL；失败/未知/UNSUPPORTED 不计入进度且展开。
- I-4: 服务端 UNSUPPORTED recommended 精确为新 handling；前端初始 label/handling/expanded/disabled 文案精确；单项 response 只追加目标 versions。
- I-5: 点击采用/取消后同步断言 badge、aria-pressed、按钮文案、data-locked、progress 和 hidden。
- I-6: 自动 question 与手动 answer translation 的请求身份、缓存、abort/late-drop、失败重试全部有测试；决定 state 深比较不变。
- I-7: OMIT 调 ADJUST_ITEM 后 resolved；answer DOM 无“此项省略”作为版本正文，assemble payload answerText 为空。
- I-8: mixed coverage 每个 mount/sourceVersion FULL_DRAFT 次数精确为 1，全 UNSUPPORTED 为 0；render/翻译/展开不增加；取消后不自动重试。
- I-9: 构造 draftHandling 与 resolved handling 不同，断言 payload 全字段仍等于 resolved version；无 resolved 时 assemble disabled。
- S-1: `git diff -- styles.css` 为空；新增 DOM class 集是契约列出的既有 class 子集，无 inline style。
- S-2: DOM 快照符合三列 head（index、包含 toggle 的 title、直接子 badge）和紧随其后的无 class item-body；保留 `data-locked`，移动端 `.trust-reply-item-head > .badge` 规则继续命中。
- S-3: 六种状态的实值文案/class/action 与表格逐项断言；旧“所有当前项目须显式锁定”不存在。
- S-4: aria-expanded/pressed/live/disabled 与状态同步；窄屏 DOM 不出现固定宽高或新增 style。
- Integration: `node --test src/test/js/*.test.js` 全通过，训练完成仍只回调评估，live 完成仍只回调采用。

## 人工验收清单

### A-1: 双入口同构与自动初始化
- 前置条件: 准备一封训练历史邮件和一封真实未匹配来信，均含 1 个有据和 1 个无据问题；浏览器宽度 1440px。
- 操作步骤: 1. 分别打开两个入口；2. 不点击任何生成按钮；3. 对比两个工作台内部控件顺序与请求。
- 预期结果: 两处都没有“生成全部版本”；各自动显示一次生成进度；有据项显示绿色 `已处理` 并收起，无据项显示 `待处理` 并展开；除“模拟 · 不外发/正式回复”和完成按钮外结构一致。
- 覆盖: I-1、I-3、I-8、S-1～S-3、需求第 1/6/11 条。

### A-2: 无据说明、翻译与采用反馈
- 前置条件: A-1 的无据问题为英文，翻译和 LLM 服务可用。
- 操作步骤: 1. 等待问题译文；2. 收起再展开译文；3. 填“回复对方官网有 A、B 两类案例”；4. 点击 AI 生成；5. 点击采用此版本。
- 预期结果: 中文译文默认可见；回答实际包含 A、B；采用后按钮立即变为“取消采用”、badge 为 `已处理`、进度从 1/2 变 2/2，卡片可收起。
- 覆盖: I-4～I-6、S-2～S-4、需求第 2/3/4/8 条。

### A-3: 单项状态隔离
- 前置条件: 同一工作台含至少 3 项，第二项已有两个 AI 版本。
- 操作步骤: 1. 记下三项版本/状态；2. 切换第二项版本；3. 修改第二项说明；4. 重新生成并采用。
- 预期结果: 只有第二项回到待处理并新增版本；第一、三项版本、采用状态、译文不变；整合预览在决定变化时清空。
- 覆盖: I-2、I-4、I-5、I-9、需求第 3 条。

### A-4: AI 回答手动翻译
- 前置条件: 第二项存在两个非空英文版本。
- 操作步骤: 1. 翻译版本 1；2. 切换版本 2 并翻译；3. 切回版本 1。
- 预期结果: 两个版本各有自己的中文译文；切回版本 1 显示原译文，不显示版本 2 的译文；翻译动作不改变采用状态或进度。
- 覆盖: I-6、S-2、S-4、需求第 5 条。

### A-5: 省略零输出与 canonical 整合
- 前置条件: 一个未处理无据项和至少一个已处理有据项。
- 操作步骤: 1. 无据项选择“省略此项”；2. 点击确认省略；3. 服务端整合。
- 预期结果: badge 显示 `已省略`；进度达到总数；raw 预览只有有据回答，没有“此项省略”、问题标题或空占位。
- 覆盖: I-7、I-9、S-3、需求第 7 条。

### A-6: 翻译失败不阻塞
- 前置条件: 临时关闭翻译服务，LLM 服务保持可用。
- 操作步骤: 1. 打开无据项；2. 等待自动翻译失败；3. 仍填写说明、生成、采用、整合；4. 恢复翻译服务点击重试。
- 预期结果: 按钮显示“翻译失败，重试”；生成/采用/整合均成功；恢复后译文显示，版本 ID 和整合 hash 不变。
- 覆盖: I-6、S-4、must-not-change 2/4。

### A-7: 模拟与正式完成动作隔离
- 前置条件: 两入口都已完成整合。
- 操作步骤: 1. 训练点击“完成模拟并评估”；2. 真实点击“采用到人工回复”；3. 不点击发送。
- 预期结果: 训练只展开评估区且无 SMTP；真实只填充人工编辑器且无 SMTP；公共工作台没有发送按钮。
- 覆盖: I-1、must-not-change 1/3。

### A-8: 目测样式与窄屏
- 前置条件: 浏览器分别设为 1440px 和 390px；存在已处理、待处理、失败三种卡片。
- 操作步骤: 对照卡片边框、badge、按钮、翻译块、展开状态并在两种宽度滚动检查。
- 预期结果: 主色 `#2563eb`、成功色 `#059669`、警告色 `#d97706`；圆角分别沿用 7/10/14px；无横向遮挡、四列挤压或新增视觉体系；键盘可触发展开/采用/翻译。
- 覆盖: S-1～S-4、must-not-change 4。
