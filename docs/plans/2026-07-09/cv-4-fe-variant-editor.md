# 计划 CV-4：前端变体编辑区与旧变体 UI 下线（cv-4-fe-variant-editor）

> 系列：变体机制二次重构 4/6。**依赖 CV-2 已合入**（variants CRUD API、CUSTOM 类型）。
> 效果稿：会话 variant_pool_frontend_redesign_preview 中的变体行样式沿用，位置从模板编辑器移到 QA 规则/片段编辑器。

## 需求描述

可观测结果：
1. QA 规则编辑器与回复片段编辑器的正文下方新增「内容变体」区：变体行（序号徽标 + 输入 + 删除）、空态提示、重复/空值保存预检、计数徽标；保存走 CV-2 的 `variants` 字段。
2. 模板编辑器的「主题变体」区整体删除；片段编辑器的「变体组」输入与 datalist 删除。
3. 片段类型下拉与分组面板新增「自定义内容」（CUSTOM）；QA 规则列表与片段列表显示「N 变体」徽标。

不得改变：
- 模板编辑器其余区域（基础信息、内容块、预览列——预览重构属 CV-5）。
- 片段四既有类型面板结构与默认片段操作。
- 邮件正文展示位 `.pre` 全集（CLAUDE.md K-mail-body-display-sites）。
- 侧栏视图注册（不新增 view，K-view-registration-triad 不触发）。

超出范围：预览分屏/插入变量按钮/人工回复勾选（CV-5）；后端（CV-1/2/3）；旧列删除（CV-6）。

## 关键不变量

### Invariant I-1: 前后端校验同规则
- Rule: 保存前预检与 CV-2 I-2 同规则（trim 非空、互不重复、不与主体重复）；命中行加 `.duplicate` + 行下提示 + showStatus 阻断；后端为最终闸门。
- Applies to: QA 规则/片段两处保存函数共用同一预检函数 `validateContentVariantInputs(container, mainText)`。
- Violation consequence: 保存才报错、定位不到行。
- 来源: original（沿用 variant-pool-3-frontend I-1）

### Invariant I-2: payload 契约
- Rule: 保存 payload 的 `variants` 为 trim 后非空字符串数组（可为空数组=清空）；不再发送 `subjectVariants` 与 `variantGroup` 字段。
- Applies to: saveComposeTemplate、saveReplySnippet、QA 规则保存函数。
- Violation consequence: 死字段复活（CV-6 清理踩雷）。
- 来源: original + K-variant-pool-dto-chain

### Invariant I-3: CUSTOM 注册完整
- Rule: CUSTOM 同时注册于 `replySnippetTypes` 数组、`replySnippetTypeLabels` 映射（文案「自定义内容」）、编辑器类型下拉（index.html select）；CUSTOM 面板不显示「默认」列与「设默认」按钮（同 ACK 现有 showDefault 模式）。
- Applies to: app.js 片段渲染链 + index.html。
- Violation consequence: 面板缺失导致 CUSTOM 片段不可见/切换报错。
- 来源: original（对齐 K-view-registration-triad 的"注册点齐套"教训）

## 样式契约

### S-1: 内容变体行（QA 规则编辑器 + 片段编辑器共用）
- 新增：以下规则块**逐字**追加到 styles.css `.compose-template-variable-hint`（:5771）之后；同时**删除** `.subject-variant-row/.subject-variant-index/.subject-variant-row .subject-variant-input/.subject-variant-input.duplicate/.subject-variant-duplicate-hint/.subject-variants-empty` 六个规则块（styles.css:5777-5817，使用点仅 app.js renderSubjectVariantRows——本计划一并删除，全集确认无其他引用）：

```css
.content-variant-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
}

.content-variant-index {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 26px;
    height: 26px;
    flex-shrink: 0;
    border-radius: 7px;
    background: var(--primary-light);
    color: var(--primary-hover);
    font-size: 11px;
}

.content-variant-row .content-variant-input {
    flex: 1;
    min-width: 0;
}

.content-variant-input.duplicate {
    border-color: var(--error);
    background: var(--error-bg);
}

.content-variant-duplicate-hint {
    margin: -4px 0 8px 34px;
    color: var(--error);
    font-size: 11px;
}

.content-variants-empty {
    margin: 0 0 8px;
    color: var(--text-muted);
    font-size: 12px;
}
```

- DOM 结构（QA 与片段编辑器各一个容器，id 分别 `qaRuleVariantsContainer` / `replySnippetVariantsContainer`，渲染函数共用）：

```html
<div class="full-width content-variants-block">
    <span class="field-label">内容变体 <span class="badge primary content-variants-count" hidden></span></span>
    <div class="content-variants-container" id="replySnippetVariantsContainer"></div>
    <button type="button" class="button small" data-action="add-content-variant">+ 添加变体</button>
    <p class="compose-template-variable-hint">主体与全部变体共同轮换，每位专家固定命中其中一个；变体输入 textarea 支持多行</p>
</div>
```

  变体行：

```html
<div class="content-variant-row" data-variant-index="0">
    <span class="content-variant-index">1</span>
    <textarea class="content-variant-input" rows="2" maxlength="2000" placeholder="变体正文"></textarea>
    <button type="button" class="button small danger" data-action="remove-content-variant" data-index="0">×</button>
</div>
```

  空态：`<p class="content-variants-empty">未添加变体，仅使用主体发送</p>`；重复提示：`<p class="content-variant-duplicate-hint">与变体 N/主体 内容重复</p>`。
- 复用：`.button.small`（styles.css:1910）、`.button.danger`（:569）、`.badge.primary`（:790）、`.compose-template-variable-hint`（:5771）、`.form-grid` 的 `.full-width`。
- 禁止项：inline style；未声明新 class；textarea 改 input（变体是正文，需多行）。

### S-2: 旧变体 UI 删除
- 删除 DOM：index.html 模板编辑器「主题变体」整块（:1456-1462 区域：field-label + `#subjectVariantsContainer` + `#addSubjectVariantBtn` + hint + `#subjectVariantsCountBadge`）；片段编辑器「变体组」label 与 `#variantGroupOptions` datalist（:1411-1412）。
- 删除 JS：`renderSubjectVariantRows/addSubjectVariantRow/removeSubjectVariantRow/collectSubjectVariants/parseSubjectVariantsJson/validateSubjectVariantInputs` 及其事件绑定（app.js :9228-9232 区域）、datalist 填充逻辑（:2765 区域）、模板列表「轮换 · N 个主题」徽标（:6515）。
- 禁止项：留下任何引用上述 id/class 的死代码（grep 验证零命中）。

### S-3: 列表「N 变体」徽标
- 复用：`badge(text, "primary")` helper（`.badge.primary` styles.css:790）。**无新增 CSS**。
- QA 规则列表行与片段列表行：detail 的 `variants.length > 0` 时在内容单元格尾部加 `<span class="badge primary">${variants.length} 变体</span>`；片段表原「变体组」列改列头为「变体」，单元格即此徽标（无变体显示空）。

### S-4: CUSTOM 类型注册（I-3）
- index.html 片段类型下拉（:1402-1407）加 `<option value="CUSTOM">自定义内容</option>`；分组面板由 `replySnippetTypes` 数组驱动自动多一张表。**无新增 CSS**。

## 现状审计

### 前端样式盘点
- 可复用 class：见 S-1 复用清单；`.form-grid`/`.span-2`/`.full-width`（片段与模板表单骨架）；`badge()` helper（app.js）。
- 设计基准 token：`--primary #2563eb`、`--primary-light rgba(37,99,235,0.07)`、`--primary-hover #1d4ed8`、`--error #e11d48`、`--error-bg rgba(225,29,72,0.07)`、`--text-muted #94a3b8`、圆角 7px、徽标 11px、正文 13px。
- 改动前基线：styles.css:5777-5817 六个 `.subject-variant-*` 块（本计划删除对象，逐字见 git）；index.html :1411（变体组 input+datalist）、:1456-1462（主题变体区）、:1402-1407（类型下拉四项）；app.js :6513-6560（subject-variant 渲染链）、:6515（列表轮换徽标）、:2654-2674（片段行/表头 variantGroupCell）、:9228-9232（事件绑定）。
- DOM 结构约定：表格操作 data-action 事件委托；片段面板按 `replySnippetTypes` 数组循环渲染（renderReplySnippetsPanels :2676）。

### 数据流
- 写：QA 规则保存函数、saveReplySnippet（:2769 区域）payload 增 `variants`（I-2）；模板保存去 subjectVariants。
- 读：QA 详情/片段列表响应的 `variants` 字段（CV-2 提供）→ 编辑器回显 + 列表徽标。
- Interaction points: variants 保存 × CV-1 渲染即时生效（模板块）；CUSTOM 建档 × 模板内容块片段下拉（enabledSnippets 按类型标签显示，自动带出 CUSTOM——确认 composeTemplateBlockRowHtml 的 snippet 下拉用 replySnippetTypeLabels，注册后自动正确）。

## 实现方案

### T1 — 通用变体编辑组件（I-1, S-1）
app.js：`renderContentVariantRows(container, variants)`/`collectContentVariants(container)`/`validateContentVariantInputs(container, mainText)`/计数徽标更新；styles.css 按 S-1 增删；index.html 两个编辑器插入 S-1 骨架。

### T2 — 保存链与回显（I-2）
QA 规则与片段的 fill/save 函数接 `variants`；模板保存删 subjectVariants 字段。

### T3 — 旧 UI 下线（S-2）
按 S-2 清单删 DOM/JS/CSS。

### T4 — CUSTOM 注册 + 列表徽标（I-3, S-3, S-4）
replySnippetTypes/labels/下拉/面板 showDefault 逻辑（CUSTOM 同 ACK 隐藏默认列）；两处列表徽标。

## 变更文件清单

| # | 文件 | 变更 |
|---|------|------|
| 1 | src/main/resources/static/app.js | T1-T4 |
| 2 | src/main/resources/static/index.html | T1/T3/T4 DOM |
| 3 | src/main/resources/static/styles.css | S-1 增删 |

文件数 3；子系统 1。

## 验收标准

- S-1: diff 断言新增 CSS 与契约逐字一致；`.subject-variant` grep 全仓零命中；变体区 DOM 与骨架一致；无 inline style。
- S-2: `subjectVariantsContainer|addSubjectVariantBtn|variantGroupOptions|renderSubjectVariantRows|collectSubjectVariants` grep 零命中。
- S-3/I-3: 列表徽标条件渲染正确；CUSTOM 三注册点齐（grep）；CUSTOM 面板无默认列。
- I-1: 预检函数两编辑器共用（grep 单一实现）；重复高亮用例手测。
- I-2: 保存请求 payload 无 subjectVariants/variantGroup（Network 核对）。

## 人工验收清单

### A-1: 片段变体编辑闭环（覆盖需求第 1 条、I-1/I-2、S-1）
- 前置条件: 任一开场白片段。
- 操作步骤: 1) 编辑该片段，变体区应显示空态「未添加变体，仅使用主体发送」；2) 加 2 条变体（多行文本），观察序号徽标 1/2 与计数徽标「2 变体」；3) 把变体 2 改成与主体相同 → 保存；4) 改正后保存；5) 重新打开。
- 预期结果: 步骤 3 该行红框 + 「与主体 内容重复」提示 + 保存被阻断；步骤 4 成功；步骤 5 回显 2 条变体且顺序不变；Network 中 payload 含 `variants` 数组、无 variantGroup。

### A-2: QA 规则变体编辑（覆盖需求第 1 条）
- 前置条件: 任一启用 QA 规则。
- 操作步骤: 同 A-1 流程在 QA 规则编辑器操作。
- 预期结果: 同 A-1；QA 列表该行出现「2 变体」蓝徽标。

### A-3: 旧变体 UI 消失（覆盖需求第 2 条、S-2）
- 前置条件: 无。
- 操作步骤: 1) 打开模板编辑器；2) 打开片段编辑器；3) 查看模板列表。
- 预期结果: 模板编辑器无「主题变体」区；片段编辑器无「变体组」输入；模板列表无「轮换 · N 个主题」徽标；控制台无 JS 报错。

### A-4: CUSTOM 类型（覆盖需求第 3 条、I-3、S-4）
- 前置条件: 无。
- 操作步骤: 1) 新建片段选「自定义内容」，填正文保存；2) 查片段页面板；3) 打开模板编辑器加内容块 → 回复片段下拉。
- 预期结果: 片段页出现「自定义内容」分组表含该片段，且该表无「默认」列；模板块下拉可选「自定义内容 #N」；人工回复骨架不出现该内容（CV-2 A-3 已验，抽查）。

### A-5: UI 目测对照契约（覆盖 S-1 样式实值）
- 前置条件: A-1 数据。
- 操作步骤: F12 检查变体行元素。
- 预期结果: 序号徽标 26×26px、圆角 7px、背景 rgba(37,99,235,0.07)；重复态输入框边框 #e11d48；行间距 margin-bottom 8px；无 style 属性。

### A-6: 既有面板回归（覆盖 must-NOT-change 第 2 条）
- 前置条件: 既有四类型片段数据。
- 操作步骤: 片段页逐面板查看，设/换默认一次。
- 预期结果: 四面板结构、默认徽标与「设默认」行为与改动前一致。
