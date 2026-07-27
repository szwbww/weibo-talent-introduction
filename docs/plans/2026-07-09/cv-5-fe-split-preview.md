# 计划 CV-5：预览分屏与插入变量按钮（cv-5-fe-split-preview）

> 系列：变体机制二次重构 5/6。**依赖 CV-1**（previewDraft variantPoolSize）、**CV-3**（useVariants API）、**CV-4**（旧变体 UI 已下线）。
> 效果稿：会话 split_pane_preview_redesign widget，用户已确认。

## 需求描述

可观测结果：
1. 统一预览层（`#previewDrawer`）改为**停靠分屏模式**：打开时不再全屏遮罩，编辑弹窗自动让出右侧宽度，左编辑右预览同时可操作；左侧输入停顿约 600ms 自动刷新预览。
2. 编辑器平铺的变量 chip 全部收进「+ 插入变量」按钮：点开分组浮层（发送方/专家），点选插入光标处；「以专家预览」按钮撤出编辑器（预览统一走停靠层）。
3. 模板编辑器内嵌预览列（`compose-template-side`）删除，模板预览走停靠层；「变体组合」‹ › 切换器移入停靠层头部，上限 = previewDraft 返回的 `variantPoolSize`。
4. 人工回复面板新增「使用内容变体」勾选（默认不勾），勾选后组装建议与发送带 `useVariants=true`。

不得改变：
- 预览抽屉的既有上下文选择（预览专家/发件邮箱/抽样模式/随机抽取）与「变量状态」区功能。
- preview-draft 的 strictPlaceholders 行为。
- 邮件正文展示位 `.pre` 全集（K-mail-body-display-sites）。
- 变量合法性校验提示（var-validation-hint）逻辑。

超出范围：变体编辑区（CV-4）；后端（CV-1/2/3）；移动端适配（现状即不支持窄屏，不新增）。

## 关键不变量

### Invariant I-1: 分屏几何
- Rule: 停靠模式由 `body.preview-docked` 驱动：预览层宽度 `--preview-dock-width: 460px`，`modal-shell` 右 padding 让出同宽；预览层无 backdrop、无 transform 动画残留；关闭停靠恢复原布局。窗口 < 1200px 时预览层回退为现有 overlay 模式（媒体查询）。
- Applies to: previewDrawer 开合函数、所有 modal 并存场景。
- Violation consequence: 弹窗被遮、双滚动条、窄屏不可用。
- 来源: original（决策 8c）

### Invariant I-2: 插入变量唯一入口
- Rule: 变量插入一律走 `.var-insert-btn` 浮层（每个可插变量的 textarea 一个）；`var-chip-bar` 平铺容器与 `compose-template-variable-row` 两排全部删除；浮层选项数据源复用现有 `renderVarChipBarContent` 的变量清单逻辑（同一份变量元数据，不得复制第二份清单）。插入位置 = 光标处（selectionStart），无焦点时追加末尾。
- Applies to: QA 规则正文、片段正文、模板自定义文本块、模板主题输入。
- Violation consequence: 两套变量清单漂移；chips 残留。
- 来源: original（决策 8b）

### Invariant I-3: 预览同步
- Rule: 停靠预览与左侧编辑器绑定：input 事件 debounce 600ms 触发 preview-draft（模板）或对应预览接口；请求带当前 `variantIndex`；响应竞态用递增 requestId 丢弃过期结果（沿用 composeTemplatePreviewRequestId 模式）。
- Applies to: 模板/片段/QA 编辑器与停靠层的联动。
- Violation consequence: 预览滞后或闪回旧内容。
- 来源: original

### Invariant I-4: 变体组合切换器
- Rule: 切换器在停靠层头部；上限 N = 响应 `variantPoolSize`；label「组合 ${i+1}/${N}」，N==1 时隐藏；切换只改 `variantIndex` 并重发预览，不影响保存 payload。
- Applies to: 停靠层预览请求链。
- Violation consequence: 越界空滚动、保存污染。
- 来源: original + CV-1 I-4

### Invariant I-5: useVariants 勾选贯通
- Rule: 人工回复面板 checkbox 默认不勾；勾选状态同时传给 suggest（query 参数）与发送（body 字段）；切换勾选立即重新拉取 suggest 刷新运营视野（服从 CV-3 I-3 所见即所发）。
- Applies to: 未匹配来信人工回复面板 JS。
- Violation consequence: 预览 A 发出 B。
- 来源: CV-3 I-3

## 样式契约

### S-1: 停靠分屏
- 就地修改既有 class：`.preview-drawer-shell`/`.preview-drawer`（styles.css:6635-6640，使用点唯一 `#previewDrawer` index.html:1588——grep 确认无第二使用点，声明**就地扩展**：原规则保留，追加 docked 态覆盖）。
- 新增：以下规则块**逐字**追加到 styles.css `.preview-drawer` 区块（:6645 后）之后：

```css
:root {
    --preview-dock-width: 460px;
}

body.preview-docked .preview-drawer-shell {
    inset: 0 0 0 auto;
    width: var(--preview-dock-width);
}

body.preview-docked .preview-drawer-backdrop {
    display: none;
}

body.preview-docked .preview-drawer {
    width: 100%;
    transform: none;
    transition: none;
    box-shadow: none;
    border-left: 1px solid var(--border);
}

body.preview-docked .modal-shell {
    padding-right: calc(var(--preview-dock-width) + 24px);
}

@media (max-width: 1199px) {
    body.preview-docked .preview-drawer-shell {
        inset: 0;
        width: auto;
    }

    body.preview-docked .preview-drawer-backdrop {
        display: block;
    }

    body.preview-docked .preview-drawer {
        width: min(440px, 92vw);
        border-left: 1px solid var(--border);
    }

    body.preview-docked .modal-shell {
        padding-right: 24px;
    }
}
```

- 禁止项：改动 `.preview-drawer` 原规则块本体；z-index 调整（预览层 60 > modal 50 维持——停靠态两者不重叠，遮挡问题由几何解决而非层级）。

### S-2: 插入变量按钮与浮层
- 复用：`.var-chip`（styles.css:5333 区域，浮层内选项直接用）、`.button.small`（:1910）。
- 新增：以下规则块**逐字**追加到 `.var-chip:hover` 块之后：

```css
.var-insert-wrap {
    position: relative;
    display: inline-flex;
}

.var-insert-btn {
    height: 26px;
    min-height: 26px;
    padding: 0 10px;
    font-size: 11px;
    border: 1px solid rgba(37, 99, 235, 0.25);
    border-radius: 7px;
    background: var(--primary-light);
    color: var(--primary-hover);
    cursor: pointer;
}

.var-insert-btn:hover {
    background: var(--primary-tint);
}

.var-insert-menu {
    position: absolute;
    right: 0;
    top: 30px;
    z-index: 20;
    width: 240px;
    padding: 8px;
    background: var(--panel-bg);
    border: 1px solid var(--panel-border);
    border-radius: 9px;
    box-shadow: var(--shadow-lg);
}

.var-insert-menu[hidden] {
    display: none;
}

.var-insert-group-label {
    margin: 0 0 4px;
    font-size: 10px;
    color: var(--text-muted);
}

.var-insert-group {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    margin-bottom: 6px;
}
```

- DOM 结构（每个可插变量的输入区工具栏）：

```html
<div class="var-insert-wrap">
    <button type="button" class="var-insert-btn" data-var-insert-target="replySnippetContent">+ 插入变量 ▾</button>
    <div class="var-insert-menu" hidden>
        <p class="var-insert-group-label">发送方</p>
        <div class="var-insert-group"><!-- .var-chip 按钮 --></div>
        <p class="var-insert-group-label">专家</p>
        <div class="var-insert-group"><!-- .var-chip 按钮 --></div>
    </div>
</div>
```

- 删除 DOM：`var-chip-bar`（index.html :1354/:1416）、`compose-template-variable-row` 两排（:1475-1489）及对应 CSS 块 `.var-chip-bar`（:5325）、`.compose-template-variable-row`（:5751 区域，使用点仅模板编辑器——grep 确认后删）；`var-preview-btn`「以专家预览」按钮（QA/片段编辑器内全部）。
- 禁止项：inline style；第二份变量清单数据。

### S-3: 模板编辑器单列化
- 就地修改：`.compose-template-body`（styles.css:5672，使用点唯一 index.html:1448）`grid-template-columns` 改 `minmax(0, 1fr)`；删除 index.html `compose-template-side` aside 整块（:1495-1518 区域，含 preview-section/预览状态/切换器/预览面板——使用点唯一）及 CSS `.compose-template-side`（:5682 中的 side 部分，`.compose-template-main` 保留）。
- 模板编辑器头部加「预览」按钮（复用 `.button.small`）打开停靠层。

### S-4: 停靠层头部——变体组合切换器 + 目标徽标
- 复用：`.button.small`、`.badge.primary`、`.preview-drawer-head`（:6641）。
- DOM（插入 preview-drawer-head 的 title-group 之后）：

```html
<div class="variant-switcher" id="previewVariantSwitcher" hidden>
    <button type="button" class="button small" id="previewVariantPrev" aria-label="上一个变体组合">‹</button>
    <span class="badge primary variant-switcher-label" id="previewVariantLabel">组合 1/1</span>
    <button type="button" class="button small" id="previewVariantNext" aria-label="下一个变体组合">›</button>
</div>
```

- 复用 CV-4 前既有 `.variant-switcher`（styles.css:5819-5827，2026-07-08 计划三产物）——**无新增 CSS**；若 CV-4 未删该块则直接引用（grep 确认保留）。

### S-5: 人工回复 useVariants 勾选
- 复用：`.checkbox-row`（片段编辑器同款，index.html:1422 用例）。**无新增 CSS**。
- DOM（人工回复组装面板操作区，suggest 按钮同排）：

```html
<label class="checkbox-row"><input type="checkbox" id="manualReplyUseVariants"> 使用内容变体（按专家轮换）</label>
```

## 现状审计

### 前端样式盘点
- 可复用 class：`.var-chip`（:5333）、`.button.small`（:1910）、`.badge.primary`（:790）、`.checkbox-row`、`.variant-switcher`（:5819）、`.preview-drawer-*` 全套（:6635-6645+）。
- 设计基准 token：抽屉宽 min(440px, 92vw)（停靠改 460px 定宽）、modal z-50 / drawer z-60、backdrop rgba(15,23,42,0.35)+blur(6px)、`--shadow-lg`、圆角 9px（浮层）。
- 改动前基线（逐字位置）：`.modal-shell`（:1923-1930 place-items center + padding 24px）；`.preview-drawer-shell/.preview-drawer`（:6635-6640 fixed overlay + translateX 动画）；`.compose-template-body`（:5672 双栏 grid `minmax(0,1fr) minmax(360px,480px)`）；index.html :1448-1518（main+side 结构）、:1354/:1416（var-chip-bar）、:1475-1489（variable-row 两排）。
- DOM 结构约定：抽屉 `#previewDrawer` + open class 开合（app.js 对应开关函数）；变量 chip 渲染 `renderVarChipBarContent`（app.js:1761）+ `refreshVariableEditors`（:1836）——浮层复用其变量元数据。

### 数据流
- 预览：`renderServerComposeTemplatePreview`（app.js:6681）POST preview-draft（payload 已含 variantIndex :6852）→ 改为停靠层触发；响应新读 `variantPoolSize`（CV-1）。
- 人工回复：suggest GET + manual-rich-reply POST（CV-3 增 useVariants）。
- Interaction points: ① variantPoolSize（CV-1 写）× 切换器（I-4 读）；② useVariants 勾选 × CV-3 suggest/send（I-5）；③ 删除 compose-template-side × updateComposeTemplatePreviewMeta 等引用该区 DOM 的 JS（全部随 T3 清理，grep `composeTemplatePreview` 引用清单逐点处理）。

## 实现方案

### T1 — 停靠分屏（I-1, S-1）
styles.css 按 S-1；app.js 抽屉开合函数增 docked 模式（body class 切换）；编辑弹窗打开时若预览已停靠保持并联动上下文。

### T2 — 插入变量浮层（I-2, S-2）
app.js：`renderVarInsertMenu(targetId)` 复用变量元数据；开合/点外关闭/光标插入；删 chip-bar 渲染调用；index.html/styles.css 按 S-2 增删。

### T3 — 模板编辑器单列 + 预览迁移（I-3, I-4, S-3, S-4）
删内嵌预览列及其 JS（renderLocalComposeTemplatePreview/renderServerComposeTemplatePreviewPanel 等迁到停靠层渲染）；debounce 600ms 自动刷新；切换器接 variantPoolSize。

### T4 — useVariants 勾选（I-5, S-5）
人工回复面板 checkbox；suggest/send 参数贯通；切换即重拉 suggest。

## 变更文件清单

| # | 文件 | 变更 |
|---|------|------|
| 1 | src/main/resources/static/app.js | T1-T4 |
| 2 | src/main/resources/static/index.html | S-1..S-5 DOM |
| 3 | src/main/resources/static/styles.css | S-1/S-2/S-3 增删 |

文件数 3；子系统 1。

## 验收标准

- S-1: diff 断言 docked CSS 逐字；`.preview-drawer` 原块 git diff 无改动；≥1200px 停靠、<1200px 回退 overlay。
- S-2: `var-chip-bar|compose-template-variable-row|var-preview-btn` grep 零命中；浮层变量清单与原 chip 清单同源（grep 单一数据函数）。
- S-3: `.compose-template-body` 单列；`compose-template-side` grep 零命中；无引用死 DOM 的 JS（控制台零报错）。
- I-3: debounce 与 requestId 竞态逻辑存在（grep）；手测输入 → ~0.6s 后预览更新。
- I-4: N=1 隐藏；切换发请求 variantIndex 递增循环（Network 核对）。
- I-5: 勾选状态出现在 suggest query 与 send body（Network 核对）。

## 人工验收清单

### A-1: 分屏同时操作（覆盖需求第 1 条、I-1、S-1）
- 前置条件: ≥1200px 宽屏；任一模板。
- 操作步骤: 1) 编辑模板 → 点「预览」；2) 观察布局；3) 左侧改主题文字；4) 右侧同时点「随机抽取」。
- 预期结果: 预览停靠右侧 460px，编辑弹窗完整可见不被遮挡、无灰色蒙层；左侧停止输入约 0.6 秒后右侧 Subject 自动更新；两侧点击互不关闭；缩窗到 <1200px 后预览回退为带蒙层抽屉。

### A-2: 插入变量（覆盖需求第 2 条、I-2、S-2）
- 前置条件: 编辑任一片段。
- 操作步骤: 1) 观察正文上方——应无平铺变量 chips；2) 点「+ 插入变量 ▾」；3) 光标置于正文中间，点「专家姓氏」；4) 点浮层外部。
- 预期结果: 浮层分「发送方/专家」两组显示 chip；`${expertFamilyName}` 插入在光标处而非末尾；点外浮层关闭；模板编辑器原两排变量行同样消失。

### A-3: 模板预览迁移 + 变体组合切换（覆盖需求第 3 条、I-4、S-3/S-4）
- 前置条件: 某模板 QA 块规则带 2 条变体（CV-2/CV-4 建）。
- 操作步骤: 1) 编辑该模板——右列内嵌预览应不存在；2) 打开停靠预览选定专家；3) 点 ›› 循环。
- 预期结果: 停靠层头部显示「组合 1/3」并随点击循环至 3/3 回绕；QA 段落文本随组合滚动；无变体模板打开时切换器隐藏。

### A-4: 人工回复勾选（覆盖需求第 4 条、I-5）
- 前置条件: 带变体规则命中的未匹配来信。
- 操作步骤: 1) 人工回复面板默认状态生成组装建议，记录正文；2) 勾选「使用内容变体」；3) 观察建议刷新；4) 发送。
- 预期结果: 默认建议为主体文案；勾选后建议立即刷新为该专家变体文案；发出正文与勾选后建议逐字一致（CV-3 A-4 同口径）。

### A-5: 预览层既有功能回归（覆盖 must-NOT-change 第 1 条）
- 前置条件: 停靠模式打开。
- 操作步骤: 依次使用预览专家筛选、发件邮箱筛选、抽样模式、随机抽取、「必须满足全部占位符」开关、变量状态区。
- 预期结果: 全部与改动前 overlay 模式行为一致。

### A-6: UI 目测对照契约（覆盖 S-1/S-2 实值）
- 前置条件: A-1/A-2 场景。
- 操作步骤: F12 检查停靠层与浮层。
- 预期结果: 停靠层宽 460px、左边框 1px var(--border)、无 box-shadow、无 transform；浮层宽 240px、圆角 9px、z-index 20；「+ 插入变量」按钮高 26px、背景 var(--primary-light)；均无 style 属性。
