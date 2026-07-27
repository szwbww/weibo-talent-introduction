# 计划三：变体池前端重构（variant-pool-3-frontend）

> 系列：变体池完善 3/3。**依赖计划一（previewDraft 支持 variantIndex + 主主题入池语义）已合入**。与计划二无依赖关系。
> 效果稿已与用户确认（会话中的 variant_pool_frontend_redesign_preview，其中"主主题参与轮换"开关按最终决策移除——主主题始终入池，无开关）。

## 需求描述

可观测结果：
1. 模板编辑器主题变体行样式类化（去 inline style），带序号徽标、重复高亮与错误提示、空态提示。
2. 模板列表主题列显示「轮换 · N 个主题」徽标（N = 主主题 + 变体数），无变体不显示。
3. 预览面板新增 ‹ › 变体切换器，逐个预览主题池成员，snippet 组预览联动（走 preview-draft 的 variantIndex）。
4. 回复片段编辑器「变体组」输入改为 datalist 下拉建议（同类型现有组名），片段表变体组徽标带组内计数。
5. 保存模板时前端预检变体重复/空值，与后端计划一 I-2 校验同规则。

不得改变：
- 保存 payload 结构（`subjectVariants` 仍为 JSON 字符串或 null；`variantIndex` 仅进预览请求，不进保存请求）。
- 现有 preview-draft 的专家/账号上下文选择、strictPlaceholders 行为。
- 侧栏视图结构（不新增 view，K-view-registration-triad 不触发）。
- 片段表现有列结构与操作按钮。

超出范围（明确不做）：
- 「主主题参与轮换」开关（决策：始终入池，无开关）。
- 变体回复率统计 UI（审计本期不做）。
- 邮件正文展示位（`.pre` 全集）的任何改动。(来源: CLAUDE.md K-mail-body-display-sites)

## 关键不变量

### Invariant I-1: 前后端校验同规则
- Rule: 保存前预检与计划一 I-2 完全同规则：变体 trim 后非空、互不重复、不等于主主题 trim。命中 → 对应输入框加 `.duplicate` 类 + 行下 `.subject-variant-duplicate-hint` 提示 + `showStatus(..., "error")` 阻断提交。前端预检是体验层，后端校验是最终闸门（前端绕过时后端仍拒绝）。
- Applies to: `saveComposeTemplate` / `collectSubjectVariants`。
- Violation consequence: 用户保存时才收到后端报错，定位不到具体行。
- 来源: original（闸门归属对齐 K-manual-compose-template-option-type-gate：前端只是展示层约束）

### Invariant I-2: 切换器索引语义与后端一致
- Rule: 池大小 N = 1 + 有效变体数；索引 0 = 主主题，1..N-1 = 变体；`variantIndex` 原值传给 preview-draft（不做前端取模，回绕由后端 floorMod 兜底）。N == 1 时切换器隐藏。变体行增删后当前索引若越界则重置为 0。
- Applies to: 切换器状态与 `renderServerComposeTemplatePreview` payload。
- Violation consequence: 预览显示的变体与实际语义错位。
- 来源: original（对齐计划一 I-1）

### Invariant I-3: datalist 建议限同类型
- Rule: 变体组 datalist 选项 = 当前编辑片段 snippetType 下已存在的 variantGroup 去重集合；切换片段类型时重建选项。
- Applies to: 片段编辑器。
- Violation consequence: 引导用户跨类型建组，与后端计划一 I-3 同类型约束冲突。
- 来源: original

## 样式契约

### S-1: 主题变体行（编辑器）
- 复用：`.button.small`（styles.css:1910）、`.button.danger`（styles.css:569）。禁止自造近似按钮样式。
- 新增：以下规则块**逐字**追加到 styles.css 的 `.compose-template-variable-hint`（:5771）规则块之后：

```css
.subject-variant-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
}

.subject-variant-index {
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

.subject-variant-row .subject-variant-input {
    flex: 1;
    min-width: 0;
}

.subject-variant-input.duplicate {
    border-color: var(--error);
    background: var(--error-bg);
}

.subject-variant-duplicate-hint {
    margin: -4px 0 8px 34px;
    color: var(--error);
    font-size: 11px;
}

.subject-variants-empty {
    margin: 0 0 8px;
    color: var(--text-muted);
    font-size: 12px;
}
```

- DOM 结构（`renderSubjectVariantRows` 输出，替换现有 app.js:6513-6521 的 inline style 版本）：

```html
<div class="subject-variant-row" data-variant-index="0">
    <span class="subject-variant-index">1</span>
    <input type="text" class="subject-variant-input" maxlength="255" placeholder="变体主题">
    <button type="button" class="button small danger" data-action="remove-subject-variant" data-index="0">×</button>
</div>
```

空态（variants 为空时容器内渲染）：`<p class="subject-variants-empty">未添加变体，仅使用主主题发送</p>`
重复提示（紧跟命中行之后）：`<p class="subject-variant-duplicate-hint">与变体 N/主主题 内容重复</p>`

- 禁止项：inline style；未在本契约声明的新 class；修改 `.button.small`/`.button.danger` 既有规则块。

### S-2: 变体计数徽标（编辑器标签行 + 模板列表）
- 复用：`.badge`（styles.css:751）+ `.badge.primary`（styles.css:790）。**无新增 CSS**。
- 编辑器：index.html「主题变体」标签行（现 :1458 `<span class="field-label">主题变体</span>` 处）改为：

```html
<span class="field-label">主题变体 <span class="badge primary" id="subjectVariantsCountBadge" hidden></span></span>
```

  app.js 在 `renderSubjectVariantRows` 末尾更新：有效变体数 V>0 时显示文案 `与主主题共 ${V + 1} 个轮换`，V=0 时 hidden。
- 模板列表：`renderComposeTemplatesTable`（app.js:6476 起）subject 单元格改为：

```html
<td>${escapeHtml(template.subject)} ${variantBadge}</td>
```

  其中 `variantBadge` = 变体数 V>0 ? `<span class="badge primary">轮换 · ${V + 1} 个主题</span>` : `""`（V 由 `parseSubjectVariantsJson(template.subjectVariants).filter(Boolean).length` 计算）。
- 提示文案：index.html :1461 `留空则使用上方默认主题` 改为 `主主题与全部变体共同轮换，每位专家固定命中其中一个`。
- 禁止项：不得为徽标新造 class。

### S-3: 预览变体切换器
- 复用：`.button.small`（styles.css:1910）、`.badge.primary`（styles.css:790）。
- 新增：以下规则块**逐字**追加到 S-1 新增块之后：

```css
.variant-switcher {
    display: inline-flex;
    align-items: center;
    gap: 6px;
}

.variant-switcher .variant-switcher-label {
    min-width: 72px;
    justify-content: center;
}
```

- DOM 结构：index.html 预览区头部（:1495-1499，`composeTemplatePreviewStatus` 所在行内、status span 之前）插入：

```html
<div class="variant-switcher" id="composeTemplateVariantSwitcher" hidden>
    <button type="button" class="button small" id="variantSwitcherPrev" aria-label="上一个主题变体">‹</button>
    <span class="badge primary variant-switcher-label" id="variantSwitcherLabel">主题 1/1</span>
    <button type="button" class="button small" id="variantSwitcherNext" aria-label="下一个主题变体">›</button>
</div>
```

- 标签文案：`主题 ${i + 1}/${N}${i === 0 ? "（主）" : ""}`。
- 禁止项：inline style；不得改动 `.compose-template-blocks-head` 既有规则。

### S-4: 变体组 datalist（片段编辑器）
- **无新增 CSS**。index.html :1411 改为：

```html
<label>变体组<input name="variantGroup" type="text" list="variantGroupOptions" placeholder="选择已有组或输入新组名（留空不参与轮换）"></label>
<datalist id="variantGroupOptions"></datalist>
```

### S-5: 片段表变体组计数徽标
- 复用：`badge(text, "info")` 既有 helper（对应 `.badge.info` styles.css:784）。**无新增 CSS**。
- `renderReplySnippetRow`（app.js:2660 区域）：`variantGroupCell` 文案由 `snippet.variantGroup` 改为 `` `${snippet.variantGroup} · ${count}个` ``，count = 同 snippetType 且同 variantGroup 的 snippet 数（由 `renderReplySnippetTypePanel` 预计算传入）。

## 现状审计

### 前端样式盘点
- 可复用 class：`.button.small` — styles.css:1910；`.button.danger` — styles.css:569；`.badge` 族 — styles.css:751-802（ok/warn/error/info/primary）；`.compose-template-fields` — styles.css:5736；`.compose-template-variable-hint` — styles.css:5771；`.compose-preview-mail-head` — styles.css:5916；`.compose-block-pill` — styles.css:5999。
- 设计基准 token（styles.css:1-60）：`--primary #2563eb`、`--primary-hover #1d4ed8`、`--primary-light rgba(37,99,235,0.07)`、`--error #e11d48`、`--error-bg rgba(225,29,72,0.07)`、`--text-muted #94a3b8`、圆角 `--radius-sm 7px`、正文 13px、徽标 11px。
- DOM 结构约定：表格行内操作按钮用 `data-action`/`data-id` 事件委托；表单弹窗 `modal-shell` + `form-grid`；徽标统一 `badge(text, type)` helper。
- 改动前基线（逐字）：
  - app.js:6513-6521 `renderSubjectVariantRows`：行模板为 `<div class="subject-variant-row" data-variant-index="${index}" style="display:flex;gap:8px;margin-bottom:8px;"><input type="text" class="subject-variant-input" value="${escapeHtml(value || "")}" maxlength="255" placeholder="变体主题" style="flex:1;"><button type="button" class="button small danger" data-action="remove-subject-variant" data-index="${index}">×</button></div>`（inline style 即本计划要移除的部分；styles.css 中现无 `.subject-variant-*` 任何规则）。
  - index.html:1458-1461：`<span class="field-label">主题变体</span>` + `#subjectVariantsContainer` + `#addSubjectVariantBtn` + hint `留空则使用上方默认主题`。
  - index.html:1411：`<label>变体组<input name="variantGroup" type="text" placeholder="如 greeting（留空则不参与变体选择）"></label>`（无 datalist）。
  - index.html:1495-1499/1518：预览区头部含 `#composeTemplatePreviewStatus`（文案「服务端预览」）与 `#composeTemplatePreviewPanel`，无切换器。
  - app.js:6476 `renderComposeTemplatesTable`：subject 单元格 `<td>${escapeHtml(template.subject)}</td>`，无徽标。
  - app.js:2660-2662：`variantGroupCell` = `badge(snippet.variantGroup, "info")`，无计数。

### 数据流（app.js）
- 写路径：`saveComposeTemplate`（:6770 区域）payload.subjectVariants = `collectSubjectVariants()`（:6542，trim+filter 后 JSON.stringify，空 → null）→ POST/PUT /api/compose-templates。片段：`saveReplySnippet`（:2769 区域）payload.variantGroup。
- 读路径：`openComposeTemplateEditor`（:6552 区域）→ `parseSubjectVariantsJson`（:6501）→ `renderSubjectVariantRows`；`renderServerComposeTemplatePreview`（:6681）→ POST /api/compose-templates/preview-draft，payload 已含 `subjectVariants: collectComposeTemplatePreviewSubjectVariants()`（:6434），**无 variantIndex**；事件绑定 :9228-9232（add/remove 变体行）。
- Interaction points：preview-draft 新字段 `variantIndex`（计划一 T4 提供）× 本计划切换器；`renderSubjectVariantRows` 重渲染 × 切换器索引重置（I-2）；片段类型切换 × datalist 重建（I-3）。

## 实现方案

### T1 — 变体行样式类化 + 空态（S-1, S-2）
文件: styles.css（S-1 CSS 逐字追加）、app.js（`renderSubjectVariantRows` 按 S-1 DOM 重写，含序号徽标、空态、计数徽标更新）、index.html（S-2 标签行 + hint 文案）。

### T2 — 保存预检（I-1）
文件: app.js。`saveComposeTemplate` 提交前调用新函数 `validateSubjectVariantInputs()`：按 I-1 规则检测，命中行加 `.duplicate` + 插入 hint + showStatus 阻断；通过则清除标记。

### T3 — 模板列表徽标（S-2）
文件: app.js（`renderComposeTemplatesTable` subject 单元格）。

### T4 — 预览切换器（I-2, S-3）
文件: index.html（S-3 DOM）、app.js：新增 `state.composeTemplateVariantIndex = 0`；prev/next 事件（步进 ±1，范围 [0, N-1] 循环）；`renderServerComposeTemplatePreview` payload 增加 `variantIndex: state.composeTemplateVariantIndex`；`renderSubjectVariantRows` 与 `openComposeTemplateEditor` 中按 I-2 重置索引并更新切换器可见性/标签。

### T5 — 变体组 datalist + 片段表计数（I-3, S-4, S-5）
文件: index.html（S-4）、app.js（`fillReplySnippetForm`/类型切换时重建 datalist 选项；`renderReplySnippetTypePanel` 预计算组计数传入 `renderReplySnippetRow`）。

## 变更文件清单

| # | 文件 | 变更 |
|---|------|------|
| 1 | src/main/resources/static/app.js | T1-T5 全部 JS |
| 2 | src/main/resources/static/index.html | S-2/S-3/S-4 DOM |
| 3 | src/main/resources/static/styles.css | S-1/S-3 CSS 逐字块 |

文件数 3 ≤ 10；子系统 1（静态前端）≤ 2。

## 验收标准

- S-1: diff 断言 styles.css 新增块与契约逐字一致；grep `renderSubjectVariantRows` 输出无 `style="`；序号徽标/空态/重复高亮 DOM 与契约骨架一致。
- S-2: 无变体模板列表行无徽标；2 个变体时徽标文案「轮换 · 3 个主题」；hint 文案已替换。
- S-3: N=1 时切换器 hidden；N=3 时标签依次 `主题 1/3（主）`→`主题 2/3`→`主题 3/3` 循环；每次切换触发一次 preview-draft 且 payload.variantIndex 正确（浏览器 Network 面板核对）。
- S-4: 片段编辑器 GREETING 类型下 datalist 仅含 GREETING 组名（构造跨类型同名组数据验证 I-3）。
- S-5: 同组 3 个片段时徽标「greeting · 3个」。
- I-1: 重复变体保存被阻断且高亮正确行；改正后可保存；直接 curl 绕过前端时后端仍拒绝（计划一 I-2 兜底）。
- I-2: 增删变体行后索引重置为 0、切换器可见性即时更新。
- 回归: 保存 payload 与改动前 schema 一致（variantIndex 不出现在保存请求）；grep 确认未触碰 `class="pre"` 任何展示位。
