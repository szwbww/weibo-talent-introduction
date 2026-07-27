# 计划：预览默认收起为侧栏 + 变体左右滚动切换（ui-collapsible-preview-and-variant-carousel）

> 纯前端迭代，构建在已合并的 cv-4/cv-5（commit e4368787「feat: add content variants and split preview」）之上。
> 效果稿：本会话 widget `template_page_preview_collapsed` / `fragment_page_preview_collapsed` / `qa_page_variant_insertvar`（三页均已由用户确认）。
> 涉及子系统：静态后台前端（`src/main/resources/static/`）单一子系统。

## 需求描述

可观测结果：
1. **变体编辑器改为左右滚动切换**：模板片段编辑器与 QA 规则编辑器里的「内容变体」区，从当前**竖排堆叠全部变体**改为**一次只显示一个变体** + ‹ / › 切换 + `i / N` 计数 + 底部圆点指示 + 「新增」。片段编辑器与 QA 规则编辑器两处**完全一致**。
2. **邮件/命中预览默认收起为右侧栏**：进入模板 / 片段 / QA 规则编辑器时，右侧预览**默认不展开**，仅在窗口右缘显示一条竖排触发条（rail）；点触发条展开为现有停靠预览（占满窗口右侧），预览头部「收起」按钮收回为触发条。触发条文案随目标切换：模板 / 片段 = `邮件预览`，QA 规则 = `命中预览`。
3. 关闭任一编辑器时，预览子系统（触发条 + 停靠面板）**完全卸载**，不残留在下一个界面。
4. **展开预览时主页面不再被编辑器遮罩盖暗**：≥1200px 停靠态下，编辑器 modal 的黑色模糊遮罩隐藏，呈现真正的「编辑器靠左 / 预览靠右」分屏观感（当前 bug：docked 下 `.modal-backdrop` 仍 `inset:0` 铺满全屏、连主页面一起盖住变暗）。

不得改变：
- `collectContentVariants` / `validateContentVariantInputs` / `updateContentVariantsCountBadge` 的读取契约：三者仍通过 `.content-variant-input` 收集**全部**变体值（不只当前可见的那个）；保存 payload 的 `variants` 数组内容与改动前逐字一致。
- 停靠预览展开态的既有几何（`body.preview-docked` 宽 460px、`modal-shell` 右让位、<1200px 回退 overlay）、上下文选择（预览专家 / 发件邮箱 / 抽样模式 / 随机抽取）、变量状态区、变体组合切换器（`#previewVariantSwitcher`）、strictPlaceholders 行为，全部保持。
- 插入变量浮层（`.var-insert-btn` / `.var-insert-menu`）与变量合法性校验（`var-validation-hint`）逻辑不动 —— cv-5 已交付，本计划不触碰。
- 邮件正文展示位 `.pre` 全集（K-mail-body-display-sites）不动。

超出范围：后端 / API / DB（变体数据模型 cv-1/2/3 已定，本计划零后端改动）；插入变量功能本身（cv-5 已交付，三处编辑器均已具备）；移动端窄屏新适配（沿用现状 <1200px 回退，不新增）；AI 训练 QA 条目弹窗（`aiTrainingQaForm`，无变体、无预览，不在范围）。

## 关键不变量

### Invariant I-1: 变体全集常驻 DOM（读取契约不破）
- Rule: 轮播改造后，容器内**每个变体仍各有一个 `.content-variant-input` textarea 常驻 DOM**，仅通过显隐（非活跃行加 `hidden`）控制「一次显示一个」。严禁把非活跃变体从 DOM 移除或改由 JS 数组托管值——否则 `collectContentVariants`（app.js:6656）、`validateContentVariantInputs`（app.js:6671）、`updateContentVariantsCountBadge`（app.js:6642）读到的变体会缺失。
- Applies to: `renderContentVariantRows`（app.js:6624）、`addContentVariantRow`（6716）、`removeContentVariantRow`（6724）、新增的切换/圆点处理。
- Violation consequence: 保存时变体丢失或校验漏检；用户「看不见的变体」被静默丢弃。
- 来源: original

### Invariant I-2: 活跃索引与指示器同步
- Rule: 容器活跃索引存于 `container.dataset.activeIndex`（字符串整数），取值 `0 .. N-1`；切换后必须同步三处：唯一显示的 `.content-variant-row.active`（其余 `hidden`）、计数文本 `${active+1} / ${N}`、圆点 `.content-variant-dot.active`。删除当前变体后活跃索引钳制到 `min(index, N-1)`；`N==0` 时渲染空态（`.content-variants-empty` 文案 + 「新增」按钮），无计数无圆点。新增变体后活跃索引跳到新变体（末位）并聚焦其 textarea。校验失败时（`validateContentVariantInputs` 返回 false）活跃索引切到第一个非法变体行，使其可见。
- Applies to: `renderContentVariantRows` 及切换/新增/删除处理。
- Violation consequence: 计数/圆点与实际显示错位；删末位后空滚动；非法变体隐藏导致用户找不到错误行。
- 来源: original

### Invariant I-3: 预览子系统三态互斥
- Rule: 预览子系统由两个 body class 表达：`preview-available`（子系统已挂载，rail 可见）与 `preview-docked`（已展开为停靠面板）。三态仅允许：
  - **卸载**：两者皆无 —— rail 与面板均不可见（编辑器未开）。
  - **收起（默认）**：`preview-available` 有、`preview-docked` 无 —— rail 可见、`#previewDrawer` 面板 `hidden`。
  - **展开**：`preview-available` 与 `preview-docked` 皆有 —— rail 隐藏、面板可见（`shell.open`）。
  绝不允许「有 `preview-docked` 而无 `preview-available`」。展开态几何完全复用既有 `body.preview-docked` 规则（styles.css:6667-6706），本计划不改这些规则块。
- Applies to: 新增 `mountPreviewRail` / `expandPreviewDrawer` / `collapsePreviewDrawer`，改造后的 `closePreviewDrawer`（卸载）。
- Violation consequence: rail 与面板同现或都不现；展开几何错乱。
- 来源: original（复用 cv-5 I-1 几何）

### Invariant I-4: 默认收起 + 目标一致
- Rule: 三个编辑器**打开时**一律进入「收起」态（`mountPreviewRail`），不得自动展开。rail 文案由 `state.previewDrawer.targetId` 决定：`qaRuleReplyBody` → `命中预览`；`composeTemplate` / `replySnippetContent` → `邮件预览`。展开时预览内容仍对应该 targetId（复用 `openPreviewDrawer` 既有 targetId→textarea/接口映射，`resolveVarTextarea` app.js:1709、badge 逻辑 2175）。
- Applies to: `openComposeTemplateEditor`（app.js:6810）、`fillReplySnippetForm`（2931）、`fillQaRuleForm`（2296）的预览触发点改造；rail 文案设置。
- Violation consequence: 违背「默认收起」需求；QA 显示「邮件预览」文案错配。
- 来源: original

### Invariant I-6: 展开态隐藏编辑器 modal 遮罩
- Rule: `body.preview-docked` 展开态（≥1200px）下，编辑器 modal 的 `.modal-backdrop`（styles.css:1932，`inset:0` 全屏黑色模糊）必须隐藏，使主页面与编辑器/预览构成左右分屏、不被盖暗。<1200px 回退 overlay 态时 `.modal-backdrop` 恢复显示（编辑器仍为传统 modal）。收起态（`preview-available` 无 `preview-docked`）与无预览态，`.modal-backdrop` 保持原样显示（编辑器仍是遮罩 modal）。
- Applies to: 三个编辑器 modal 的 `.modal-backdrop`（`#composeTemplateModalBackdrop` / `#replySnippetModalBackdrop` / `#qaRuleModalBackdrop`），由纯 CSS body-class 选择器驱动。
- Violation consequence: 展开预览时主页面仍被盖暗（当前 bug）；或收起/窄屏态误隐遮罩导致点击穿透。
- 说明: 遮罩隐藏后失去「点遮罩关闭」入口，展开态改由 × 关闭——可接受。
- 来源: original（用户 2026-07-09 现场反馈「预览遮盖主页面」）

### Invariant I-5: 编辑器关闭即完全卸载
- Rule: 三个编辑器的隐藏函数（`hideComposeTemplateEditor` app.js:6824、`hideReplySnippetEditor` 2910、`hideQaRuleEditor` 2284）关闭时必须调用卸载：移除 `preview-available` 与 `preview-docked`、`shell.open`、`shell.hidden=true`、清 `state.previewDrawer.targetId`，并调用 `syncBodyScrollLock()`（app.js:2116）。不得留下 rail 或 docked 面板给下一个视图。
- Applies to: 三个 hide 函数、改造后的 `closePreviewDrawer`。
- Violation consequence: 切到别的页面仍飘着 rail / 停靠面板 / 滚动锁未释放。
- 来源: original

## 样式契约

> 既有样式引用 `file:line`；新增样式逐字给出，执行 agent 原样复制，禁止改值或增删属性。禁止 inline style，禁止未声明的新 class。

### S-1: 变体轮播（carousel）
- **复用**（不得自造近似替代）：`.content-variant-row`（styles.css:5799）、`.content-variant-index`（5806）、`.content-variant-row .content-variant-input`（5819）、`.content-variant-input.duplicate`（5824）、`.content-variant-duplicate-hint`（5829）、`.content-variants-empty`（5835）、`.button.small`（1910）、`.button.small.danger`（既有 danger 变体）。
- **新增**：以下规则块**逐字**追加到 styles.css `.content-variants-empty` 规则块之后（:5839 之后、`.variant-switcher`:5841 之前）：

```css
.content-variant-carousel {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.content-variant-nav {
    display: flex;
    align-items: center;
    gap: 6px;
}

.content-variant-nav-counter {
    min-width: 46px;
    text-align: center;
    font-size: 12px;
    color: var(--text-muted);
}

.content-variant-nav-spacer {
    flex: 1;
}

.content-variant-rows .content-variant-row[hidden] {
    display: none;
}

.content-variant-dots {
    display: flex;
    justify-content: center;
    gap: 6px;
}

.content-variant-dot {
    width: 7px;
    height: 7px;
    padding: 0;
    border: none;
    border-radius: 50%;
    background: var(--border);
    cursor: pointer;
}

.content-variant-dot.active {
    background: var(--primary);
}
```

- **DOM 结构**（`renderContentVariantRows` 在有变体时渲染的 `container.innerHTML` 骨架；`N` 个变体，活跃索引 `a`）：

```html
<div class="content-variant-carousel">
    <div class="content-variant-nav">
        <button type="button" class="button small" data-action="variant-prev" aria-label="上一个变体">‹</button>
        <span class="content-variant-nav-counter">2 / 3</span>
        <button type="button" class="button small" data-action="variant-next" aria-label="下一个变体">›</button>
        <span class="content-variant-nav-spacer"></span>
        <button type="button" class="button small" data-action="add-content-variant">+ 新增</button>
    </div>
    <div class="content-variant-rows">
        <div class="content-variant-row" data-variant-index="0" hidden>
            <span class="content-variant-index">1</span>
            <textarea class="content-variant-input" rows="3" maxlength="2000" placeholder="变体正文">…</textarea>
            <button type="button" class="button small danger" data-action="remove-content-variant" data-index="0">×</button>
        </div>
        <div class="content-variant-row active" data-variant-index="1">…</div>
        <div class="content-variant-row" data-variant-index="2" hidden>…</div>
    </div>
    <div class="content-variant-dots">
        <span class="content-variant-dot" data-index="0"></span>
        <span class="content-variant-dot active" data-index="1"></span>
        <span class="content-variant-dot" data-index="2"></span>
    </div>
</div>
```

- 空态（`N==0`）骨架（复用既有空态文案 class，附带常驻「新增」入口）：

```html
<div class="content-variant-carousel">
    <p class="content-variants-empty">未添加变体，仅使用主体发送</p>
    <div class="content-variant-nav">
        <span class="content-variant-nav-spacer"></span>
        <button type="button" class="button small" data-action="add-content-variant">+ 新增</button>
    </div>
</div>
```

- **删除 DOM**：index.html 中静态的 `+ 添加变体` 按钮两处（QA `<button ... data-action="add-content-variant">+ 添加变体</button>` index.html:1366；片段编辑器同款按钮，`#replySnippetVariantsContainer` 之后的对应行）——新增入口改由 JS 在轮播 nav 内渲染，静态按钮不再需要。`compose-template-variable-hint` 提示文案（「主体与全部变体共同轮换…」）**保留**。
- **禁止项**：inline style；复用 `.variant-switcher`（5841，那是预览头部组合切换器，语义不同，勿混用）；改动 `.content-variant-row` 等既有规则块本体。

### S-2: 预览触发条（rail）
- **复用**：无（全新组件）。文案竖排用 `writing-mode`。
- **新增**：以下规则块**逐字**追加到 styles.css `.preview-drawer-shell` 相关块之后（:6743 `@media (max-width: 640px)` 那行之后，文件该区段末尾）：

```css
.preview-rail {
    display: none;
    position: fixed;
    top: 50%;
    right: 0;
    z-index: 59;
    transform: translateY(-50%);
    flex-direction: column;
    align-items: center;
    gap: 10px;
    padding: 16px 8px;
    border: 1px solid var(--border);
    border-right: none;
    border-radius: 10px 0 0 10px;
    background: var(--panel-bg, #ffffff);
    box-shadow: -8px 0 24px -12px rgba(15, 23, 42, 0.28);
    color: var(--text-main);
    cursor: pointer;
}

body.preview-available:not(.preview-docked) .preview-rail {
    display: flex;
}

.preview-rail:hover {
    background: var(--primary-light);
}

.preview-rail-icon {
    font-size: 15px;
    color: var(--primary);
    line-height: 1;
}

.preview-rail-label {
    writing-mode: vertical-rl;
    font-size: 12px;
    letter-spacing: 2px;
    color: var(--text-secondary, var(--text-muted));
}
```

- **DOM 结构**（新增顶层元素，紧跟 `#previewDrawer` 的 `.preview-drawer-shell` 之后插入 index.html）：

```html
<button type="button" class="preview-rail" id="previewRail" aria-label="展开预览">
    <span class="preview-rail-icon" aria-hidden="true">‹</span>
    <span class="preview-rail-label" id="previewRailLabel">邮件预览</span>
</button>
```

- **禁止项**：inline style；`position: fixed` 之外的定位改动会破坏「贴右缘」；改动既有 `.preview-drawer-shell` / `.preview-drawer` 规则块本体。

### S-3: 收起态几何（不改既有 docked 规则）
- **就地扩展声明**：`body.preview-docked` 全套规则（styles.css:6663-6706）**原样保留不动**；仅**新增**下列规则块，逐字追加到 styles.css `body.preview-docked .modal-shell` 媒体查询块之后（:6706 `}` 之后、`.preview-drawer-head`:6708 之前）：

```css
body.preview-available:not(.preview-docked) .modal-shell {
    padding-right: 24px;
}
```

  （收起态编辑弹窗恢复常规内边距，不为 rail 让位——rail 是右缘浮层，宽度窄不挤压内容。）
- **禁止项**：修改 `--preview-dock-width`（:6664）；调整 z-index（rail 用 59 < 面板 shell 60 < ？——收起态面板 hidden 不参与层叠，展开态 rail 隐藏，两者不同现，59/60 不冲突）。

### S-4: 展开态隐藏编辑器 modal 遮罩（I-6）
- **就地扩展声明**：`.modal-backdrop` 既有规则块（styles.css:1932-1940）**原样保留不动**；仅**新增**下列规则，逐字追加到 S-3 那条 `body.preview-available:not(.preview-docked) .modal-shell` 规则之后：

```css
body.preview-docked .modal-backdrop {
    display: none;
}

@media (max-width: 1199px) {
    body.preview-docked .modal-backdrop {
        display: block;
    }
}
```

- **禁止项**：inline style；改动 `.modal-backdrop` 本体（:1932）；用 JS 直接操作 backdrop 显隐（一律纯 CSS body-class 驱动，与 I-3 三态一致）。

## 现状审计

### 变体编辑器（content-variants）
- 渲染：`renderContentVariantRows(container, variants)`（app.js:6624）—— 竖排 map 全部变体为 `.content-variant-row`（含 `.content-variant-index` / `.content-variant-input` / remove 按钮），空态输出 `.content-variants-empty`。
- 读取（**契约，勿破**）：`collectContentVariants`（6656）`querySelectorAll(".content-variant-input")` 取全部；`validateContentVariantInputs`（6671）同样遍历全部并在 `.content-variant-row` 后插 `.content-variant-duplicate-hint`；`updateContentVariantsCountBadge`（6642）经 collect 计数。
- 增删：`addContentVariantRow`（6716）/ `removeContentVariantRow`（6724）读现有 input 值 → push/splice → 重渲染；事件经 `handleContentVariantEditorClick`（6731）委托，靠 `data-action="add-content-variant"` / `remove-content-variant"` + `.content-variants-block .content-variants-container` 定位。
- 调用点：QA `fillQaRuleForm`（2314）与 `hideQaRuleEditor` reset（2287）；片段 `fillReplySnippetForm`（2945）与 `hideReplySnippetEditor` reset（2913）。两处结构对称。
- 保存读取：QA `saveQaRule`（2322 起，`collectContentVariants($("#qaRuleVariantsContainer"))` 2347）；片段 `saveReplySnippet`（2954，collect 2973）。
- DOM：QA 表单 `#qaRuleVariantsContainer` + 静态 `+ 添加变体` 按钮 + 提示（index.html:1363-1368）；片段 `#replySnippetVariantsContainer` + 同款按钮/提示（1432-1436 区）。

### 预览抽屉（preview drawer）
- Shell：`#previewDrawer`（`.preview-drawer-shell`，index.html:1555，默认 `hidden`）+ 头部 `.preview-drawer-head`（含 `#previewVariantSwitcher` 组合切换器 1562）+ 上下文区 + 正文区。
- 开：`openPreviewDrawer({targetId,…})`（app.js:2139）—— 初始化 `state.previewDrawer`、设 badge、`shell.hidden=false`、`body.add("preview-docked")`、`shell.add("open")`、`refreshPreviewDrawer()`。**当前语义 = 直接展开停靠**。
- 关：`closePreviewDrawer()`（2126）—— `body.remove("preview-docked")`、`shell.remove("open")`、240ms 后 `shell.hidden=true` + `syncBodyScrollLock()`。
- 目标映射：`resolveVarTextarea`（1709）、`isComposeTemplatePreviewTarget`（1731）、badge（2175 `composeTemplate`→「服务端预览」否则「变量渲染」）。
- 触发点（**改造对象**）：
  1. 模板 `openComposeTemplateEditor`（6810）——**不**自动开预览，靠 `#openComposeTemplatePreviewBtn`（index.html:1461，监听 app.js:9556）手动开。
  2. 片段 `fillReplySnippetForm` → `openPreviewDrawer({targetId:"replySnippetContent"})`（2951，**打开编辑器即自动展开**）。
  3. QA `fillQaRuleForm` → `openPreviewDrawer({targetId:"qaRuleReplyBody"})`（2319，**自动展开**）。
- 关联点：三个 hide 函数在 targetId 匹配时调用 `closePreviewDrawer`（6827-6829 / 2918-2920 / 2291-2293）。
- 展开几何：`body.preview-docked`（styles.css:6663-6706，宽 460px + modal 让位 + <1200px 回退 overlay），cv-5 交付，**本计划复用不改**。
- **已知 bug（本计划修）**：编辑器 modal 的 `.modal-backdrop`（styles.css:1932，`inset:0` 全屏 rgba(15,23,42,.35)+blur(6px)，三个 modal 各一个 `#*ModalBackdrop`）在 docked 展开态**未被处理**——`inset:0` 不吃 `.modal-shell` 的 padding，照旧铺满全屏，把主页面盖暗。预览抽屉自身 backdrop 在 docked 下已 `display:none`（6719），唯独 modal backdrop 遗漏。现场表现即用户反馈的「预览遮盖主页面」。

### 前端样式盘点
- 可复用 class：`.content-variant-row`（5799）、`.content-variant-index`（5806）、`.content-variant-input`（5819）、`.content-variant-input.duplicate`（5824）、`.content-variant-duplicate-hint`（5829）、`.content-variants-empty`（5835）、`.button.small`（1910）、`.button.small.danger`（既有）、`.preview-drawer-shell`/`.preview-drawer`/`body.preview-docked` 全套（6657-6706）、`.preview-drawer-head`（6708）。
- 设计基准 token（实值）：主色变量 `--primary`、`--primary-light`（浅底）、`--primary-hover`；`--border`（1px 描边色）；`--panel-bg`（#ffffff 面板底）；`--text-main` / `--text-muted` / `--text-secondary`；`--error` / `--error-bg`（校验红）；圆角：小按钮/index 徽标 7px、面板/rail 10px；停靠宽 `--preview-dock-width:460px`（6664）、overlay 抽屉 `min(440px, 92vw)`；z-index：预览 shell 60、rail 拟用 59；docked 阴影 `-16px 0 40px -16px rgba(15,23,42,0.28)`。
- DOM 约定：编辑器均为 `.modal-shell` 弹窗（模板 `#composeTemplateModal`、片段 `#replySnippetModal`、QA `#qaRuleModal`），fill/open→show、hide→reset+`closePreviewDrawer`；变量插入浮层 `.var-insert-wrap`>`.var-insert-btn`+`.var-insert-menu`（cv-5，三处编辑器均有，勿动）。
- 改动前基线（逐字）：`renderContentVariantRows`（app.js:6624-6640，见上文引用）；`.content-variant-*` CSS（styles.css:5799-5839）；`.preview-drawer-shell/.preview-drawer/preview-docked`（6657-6706）；index.html 变体块 QA:1363-1368、片段:1432-1436；预览触发 index.html:1461 + app.js 2319/2951/6810。

### 数据流 / 交互点
- ① 变体轮播 × collect/validate/countBadge（I-1）：写路径 = 轮播渲染保留全部 input；读路径 = 三个既有函数遍历 `.content-variant-input`。改造须保证读路径零改动即可命中全部变体。
- ② 预览三态 × 展开几何（I-3）：写路径 = mount/expand/collapse/close 切 body class；读路径 = 既有 `body.preview-docked` CSS。新增 class 不得影响 docked 规则。
- ③ 编辑器 open/close × 预览挂卸（I-4/I-5）：写路径 = 三个 fill/open 改 mount、三个 hide 改 unmount；读路径 = rail label、syncBodyScrollLock。

## 实现方案

### T1 — 变体轮播渲染（I-1, I-2, S-1）
文件：app.js、styles.css、index.html。
- app.js `renderContentVariantRows`（6624）改为渲染 S-1 骨架：外层 `.content-variant-carousel` + nav（‹ 计数 › 新增）+ `.content-variant-rows`（全部 `.content-variant-row` 常驻，仅活跃行无 `hidden`）+ `.content-variant-dots`；活跃索引读写 `container.dataset.activeIndex`（默认 0，越界钳制）。空态渲染 S-1 空态骨架。**保留** `.content-variant-input` 全集（I-1）。
- 新增 `setActiveVariant(container, index)`：钳制 `0..N-1`，写 `dataset.activeIndex`，切换 `.content-variant-row.active`/`hidden`、计数文本、`.content-variant-dot.active`。
- `addContentVariantRow`（6716）：push 后活跃索引=末位并聚焦；`removeContentVariantRow`（6724）：splice 后活跃索引 `min(index, N-1)`。
- `handleContentVariantEditorClick`（6731）扩展：`data-action="variant-prev"`→`setActiveVariant(active-1)`；`"variant-next"`→`active+1`；圆点 `.content-variant-dot[data-index]`→`setActiveVariant(该 index)`。add/remove 分支不变（定位逻辑仍成立）。
- `validateContentVariantInputs`（6671）返回 false 时，调用方（`saveQaRule`/`saveReplySnippet`）或该函数内部把活跃索引切到首个 `.content-variant-input.duplicate` 所在行（I-2）。
- styles.css 按 S-1 追加轮播 CSS；index.html 按 S-1 删除两处静态 `+ 添加变体` 按钮（保留提示文案）。

### T2 — 预览 rail 与三态（I-3, I-4, I-6, S-2, S-3, S-4）
文件：app.js、styles.css、index.html。
- index.html：按 S-2 在 `#previewDrawer` 之后加 `#previewRail`。
- styles.css：按 S-2 追加 rail CSS、按 S-3 追加收起态 modal 内边距、按 S-4 追加展开态隐藏 modal 遮罩（I-6，纯 CSS，无 JS）。
- app.js 新增：
  - `mountPreviewRail({targetId, contactId, orcidId})`：初始化 `state.previewDrawer`（同 `openPreviewDrawer` 2150-2161 的赋值）、设 `#previewRailLabel` 文案（I-4：qaRuleReplyBody→命中预览，否则邮件预览）、`body.add("preview-available")`、**不**加 docked、**不**显示 shell、**不** refresh。
  - `expandPreviewDrawer()`：`shell.hidden=false`、`body.add("preview-docked")`、`requestAnimationFrame` 加 `shell.open`、`syncBodyScrollLock`、`refreshPreviewDrawer()`（首帧才拉预览）。
  - `collapsePreviewDrawer()`：`shell.remove("open")`、`body.remove("preview-docked")`、240ms 后 `shell.hidden=true`、`syncBodyScrollLock`（保留 `preview-available`→回到 rail）。
  - 改造 `closePreviewDrawer()`（2126）：卸载——移除 `preview-available`+`preview-docked`+`shell.open`、`shell.hidden=true`、清 `state.previewDrawer.targetId`、`syncBodyScrollLock`。
- 绑定：`#previewRail` click→`expandPreviewDrawer`；预览头部「收起」按钮（复用现有 `#previewDrawer` 头部关闭/收起控件，其 handler 由 `closePreviewDrawer` 改指 `collapsePreviewDrawer`）；`#previewDrawerBackdrop`（1556）在停靠态本就 `display:none`，行为不变。

### T3 — 编辑器打开默认收起 / 关闭卸载（I-4, I-5）
文件：app.js。
- 打开改 mount：`fillReplySnippetForm` 2951 `openPreviewDrawer(...)`→`mountPreviewRail({targetId:"replySnippetContent"})`；`fillQaRuleForm` 2319→`mountPreviewRail({targetId:"qaRuleReplyBody"})`；`openComposeTemplateEditor`（6810）末尾新增 `mountPreviewRail({targetId:"composeTemplate"})`（该编辑器原无自动预览，现补 rail）。
- `#openComposeTemplatePreviewBtn`（监听 9556）改指 `expandPreviewDrawer`（rail 之外的备用展开入口，可保留）。
- 关闭改 unmount：三个 hide 函数（6824 / 2910 / 2284）中原 `closePreviewDrawer()` 保持调用（其语义已在 T2 改为完整卸载）；确认三处都会执行卸载（含 targetId 非匹配的兜底——直接无条件卸载，避免残留）。

## 变更文件清单

| # | 文件 | 变更 |
|---|------|------|
| 1 | src/main/resources/static/app.js | T1 轮播渲染/切换/校验聚焦；T2 mount/expand/collapse/close 四函数 + 绑定；T3 三编辑器 open→mount、close→unmount |
| 2 | src/main/resources/static/index.html | S-1 删两处静态添加按钮；S-2 加 `#previewRail` |
| 3 | src/main/resources/static/styles.css | S-1 轮播 CSS；S-2 rail CSS；S-3 收起态 modal 内边距 |

文件数 3；子系统 1（静态前端）。

## 验收标准

- I-1: 保存前后对比——含 3 变体的规则/片段，切到变体 2 编辑后保存，`collectContentVariants` 结果仍为 3 条且逐字正确（Network payload `variants` 核对）；`querySelectorAll(".content-variant-input").length === 变体数`（Console 断言，非活跃行仍在 DOM）。
- I-2: 计数文本 `active+1 / N` 与可见行 `data-variant-index` 一致；活跃圆点唯一且对应；删末位后不空滚动；新增后跳末位并聚焦；构造重复变体保存→活跃索引跳到红框行。
- I-3: 三态 grep/DevTools——收起态 `body.classList` 含 `preview-available` 不含 `preview-docked`，`#previewDrawer.hidden===true`，`.preview-rail` 可见；展开态含二者，面板可见，rail `display:none`；无「docked 无 available」组合。既有 `body.preview-docked` 规则块 git diff 无改动。
- I-4: 打开模板/片段编辑器 rail 文案「邮件预览」，QA「命中预览」；三编辑器打开后 `preview-docked` 均**不**存在（默认收起）。
- I-5: 关闭任一编辑器后 `body.classList` 无 `preview-available`/`preview-docked`，`#previewDrawer.hidden===true`，`#previewRail` 不可见，`document.body.classList.contains("modal-open")` 经 syncBodyScrollLock 归位。
- S-1: 落地 CSS 与契约代码块逐字一致；DOM 结构与骨架一致；index.html 无 `+ 添加变体` 静态按钮（grep 零命中）；无 inline style。
- S-2: rail CSS 逐字一致；`#previewRail` DOM 与骨架一致；无 inline style。
- S-3: 仅新增 `body.preview-available:not(.preview-docked) .modal-shell` 一条；`--preview-dock-width` 与 6663-6706 块未改（git diff 核对）。
- I-6/S-4: 落地 CSS 与契约逐字一致；`.modal-backdrop` 本体（1932）git diff 无改动；DevTools——展开态（≥1200px）`.modal-backdrop` computed `display:none`、主页面不变暗；收起态与 <1200px 展开态 `.modal-backdrop` 恢复 `display:block`。

## 人工验收清单

### A-1: 变体左右滚动切换（覆盖需求 1、I-2、S-1）
- 前置条件: 存在一条带 3 个内容变体的 QA 规则（无则在编辑器里新增 3 个变体后保存再重开）。
- 操作步骤: 1) 打开该 QA 规则编辑器，看「内容变体」区；2) 点 ›；3) 再点 ›；4) 点底部第 1 个圆点；5) 点当前变体的「×」删除；6) 点「+ 新增」。
- 预期结果: 初始只显示 1 个变体正文，顶部显示「1 / 3」，底部 3 个圆点第 1 个高亮；点 › 后显示第 2 个变体、计数「2 / 3」、第 2 圆点高亮；再点 › 显示第 3 个、「3 / 3」；点第 1 圆点跳回变体 1；删除后计数变「? / 2」不空白、显示相邻变体；新增后跳到末位空变体且光标在其 textarea。
- 覆盖: 需求 1 / I-2 / S-1

### A-2: 片段编辑器变体一致（覆盖需求 1、I-1）
- 前置条件: 任一「尊语」类片段，加 2 个变体。
- 操作步骤: 1) 编辑器里加变体 A、B；2) 切到变体 A 改文字；3) 保存；4) 重新打开该片段。
- 预期结果: 切换样式与 QA 完全一致（‹ › 计数 圆点 新增）；保存后重开变体数仍为 2、A/B 内容与保存前逐字一致（无因「切走后没保存到」而丢失——I-1）。
- 覆盖: 需求 1 / I-1

### A-3: 预览默认收起 + 展开/收起（覆盖需求 2、I-3、I-4、S-2）
- 前置条件: ≥1200px 宽屏。
- 操作步骤: 1) 打开模板编辑器；2) 看右缘；3) 点右缘竖条；4) 看编辑弹窗与预览面板布局；5) 点预览头部「收起」；6) 换成打开 QA 规则编辑器看右缘竖条文案。
- 预期结果: 步骤 2 预览**未展开**，仅右缘一条竖排「邮件预览」触发条；点击后预览面板从右侧停靠展开（约 460px）、编辑弹窗左移让位、竖条消失；点「收起」后面板收回、竖条重现、弹窗恢复满宽；QA 编辑器右缘竖条文案为「命中预览」。
- 覆盖: 需求 2 / I-3 / I-4 / S-2

### A-4: 关闭编辑器完全卸载（覆盖需求 3、I-5）
- 前置条件: A-3 展开状态。
- 操作步骤: 1) 展开预览后直接关闭模板编辑器（× 或点遮罩）；2) 切到别的侧栏视图；3) 再打开片段编辑器又立刻关闭。
- 预期结果: 关闭后右缘竖条与预览面板都消失，别的视图不残留任何预览元素；页面滚动正常（滚动锁已释放）；反复开关无叠加/卡死。
- 覆盖: 需求 3 / I-5

### A-5: 展开态既有功能回归（覆盖 must-NOT-change）
- 前置条件: 某带 2 变体的模板，预览展开。
- 操作步骤: 依次用预览专家筛选、发件邮箱筛选、抽样模式、随机抽取、变体组合切换器 ‹ ›、变量状态区、「必须满足全部占位符」开关；再缩窗到 <1200px。
- 预期结果: 全部与本次改动前行为一致；组合切换器仍在预览头部正常滚动；<1200px 时预览回退为带蒙层 overlay 抽屉。
- 覆盖: must-NOT-change（停靠几何 / 上下文 / 组合切换器 / 变量状态）

### A-6: 样式实值目测（覆盖 S-1/S-2/S-3）
- 前置条件: A-1 与 A-3 场景，开 F12。
- 操作步骤: 检查轮播圆点、rail、收起态 modal。
- 预期结果: 活跃圆点背景 `var(--primary)`、非活跃 `var(--border)`、直径 7px；rail 固定右缘、圆角 `10px 0 0 10px`、竖排文案 `writing-mode: vertical-rl`、z-index 59、hover 底色 `var(--primary-light)`；收起态编辑弹窗 `padding-right:24px`；相关元素均无 `style=` 属性。
- 覆盖: S-1 / S-2 / S-3

### A-7: 展开预览不遮盖主页面（覆盖需求 4、I-6、S-4）
- 前置条件: ≥1200px 宽屏；主页面（如模板列表）有可见内容。
- 操作步骤: 1) 打开模板编辑器（默认收起，rail 在右缘）；2) 点 rail 展开预览；3) 观察编辑弹窗四周、尤其左侧与顶部露出的主页面区域；4) 把窗口缩到 <1200px 再看。
- 预期结果: 展开后编辑弹窗靠左、预览靠右，二者之间与外围的主页面**清晰可见、不变暗、无模糊蒙层**（真正左右分屏）；缩到 <1200px 后预览回退为带蒙层 overlay，编辑器恢复传统 modal（主页面重新变暗）——两种断点行为都符合预期。
- 覆盖: 需求 4 / I-6 / S-4

## 自审
- [x] 每个新字段/状态有不变量：变体活跃索引（I-2）、预览三态（I-3）、展开态 modal 遮罩（I-6）。
- [x] 现状审计列全读写路径（collect/validate/countBadge/save × 4 + 三编辑器 open/close，均带行号，grep 核实）。
- [x] 无未被不变量覆盖的写路径。
- [x] 含前端 → 样式契约存在，每个新增/改动 DOM 映射到 S-1/S-2/S-3；新增 class CSS 全文逐字。
- [x] 无「样式与现有一致」类模糊表述。
- [x] 被改既有函数 `renderContentVariantRows`/`closePreviewDrawer` 已列全部调用点并声明就地改造。
- [x] 人工验收每条可黑盒执行、预期为实值；must-NOT-change 有回归 A-5；交互点有跨路径项（A-2 覆盖交互点①、A-3/A-4 覆盖②③）。
- [x] 文件数 3 ≤ 10；子系统 1 ≤ 2。
- [x] 已存 docs/plans/2026-07-09/。
