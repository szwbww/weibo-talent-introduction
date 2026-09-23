# 回复片段变体 02：单编辑区与主题可输入下拉框

状态：待实施。2026-09-23。依赖：[01 后端计划](01-reply-snippet-variants-backend.md)完成并通过其机器验证；本计划不修改后端。

## 需求描述

O-1：编辑一个回复片段时，原文和变体在同一个位置左右切换，只显示一个正文编辑框；可新增、删除变体，原文不可删除。多个段落仍由用户自己新增多个回复片段。

O-2：模板主题改为一个默认可下拉、也可直接输入的组合输入框。选片段显示“引用”；没有选中/精确匹配某个选项的输入显示“自定义”。输入、状态标记、下拉按钮、`{}` 插入变量图标保持一行；不新增来源模式下拉框。

O-3：预览使用后端的公共片段抽取能力，点击“换一组”取得新的样本；标明“随机样本，发送时重新生成”。编辑器草稿、模板列表和专家详情预览都支持主题引用。

必须保持：P-1 片段类型/名称/ID/排序/启用/默认值、保存取消和全量变体校验；P-2 模板名称、描述、启用、正文块排序与引用、自定义文本和原有变量菜单光标插入；P-3 预览专家/账号/strictPlaceholders、请求乱序保护、退出弹窗行为；P-4 QA 编辑页及其他变量按钮不受样式改动影响。

范围外：不改页面整体布局、不加段落管理器、不拆数据库、不做 AI 文案生成、批量创建十个变体按钮、全局样式重构或生产发送。已有 `intro-variants-preview.html/.css` 未完成预览文件不纳入产品，不删除或部署。

## 关键不变量

### I-1：一个可见编辑器，全量保留内容
- Rule：逻辑版本序号为 0=原文、1..N=变体。原文保留 `#replySnippetContent/name=content`；每个变体保留一个常驻 `.content-variant-input` DOM 节点，只控制显隐。切换不重建节点、不改值；新增/删除需要重绘时，先收集所有原始值，包含空值。原文不属于 variants 数组。
- Applies to：renderContentVariantRows、setActiveVariant、add/remove、fill/hide/saveReplySnippet。
- Violation consequence：切换或保存丢失非当前变体、原文被覆盖。
- 来源：K-content-variant-input-read-contract（现行调用点重新核实）。

### I-2：校验包含隐藏版本
- Rule：保存前校验原文及全部变体，空值、重复、非法占位符不能因为隐藏而漏过；失败自动显示第一个错误版本并聚焦，禁止“invalid form control not focusable”。原文 textarea 移除原生 required，以现有提交函数显式校验非空替代，其他字段的原生 required 保留。占位符菜单始终指向当前版本并保留其光标位置；取消关闭后重开读取已保存值。
- Applies to：saveReplySnippet、validateContentVariantInputs、变量菜单绑定与编辑器导航。
- Violation consequence：后台拒绝但前端无法定位错误，或隐藏原文为空导致无法提交/聚焦。
- 来源：original；K-content-variant-input-read-contract。

### I-3：选项 ID 与输入显示分离
- Rule：临时状态只保存 `selectedSubjectSnippetId: number|null`，持久化由 01 的 subjectSnippetId 承担。每个选项显示 `名称或摘要 · #ID`，此完整标签唯一。点击/键盘确认选项或精确输入完整标签才成为引用；任意其他输入立即清除 ID 并显示自定义，禁止包含匹配/模糊匹配自动选第一个片段。引用模式请求 `subjectSnippetId=id`、subject=片段原文显示快照；自定义模式请求 ID=null、subject=输入文本。
- Applies to：组合输入事件、编辑回填、保存、预览、列表显示。
- Violation consequence：同名选错片段，界面显示自定义但实际发送旧引用。
- 来源：original；后端 I-2；K-variant-pool-dto-chain。

### I-4：引用与自定义的编辑边界
- Rule：输入框始终可输入；引用状态下 `{}` 按钮 disabled，title=`引用内容请到回复片段中编辑`，不把变量插入片段名称、不偷偷修改片段。用户手动改输入使其成为自定义后，变量按钮恢复。下拉预筛启用且原文为非空、单行、≤255 字的片段；变体候选的有效性由后台保存/预览时最终校验（现有 detail 的 variants 是字符串数组，不携带 enabled）。同名选项靠 ID 区分。已保存的失效引用保持 ID，显示 `引用失效 · #ID` 与错误标记，禁止自动转换成自定义或回退旧快照，必须改选或手动输入才可保存。
- Applies to：选项构建、保存回填、片段数据刷新、subject 图标与预览。
- Violation consequence：误改共享片段，或者片段停用后仍无提示发送旧主题。
- 来源：original；后端 I-3。

### I-5：预览表示新样本
- Rule：模板编辑和专家详情的 preview-draft 请求都携带 subjectSnippetId，且不再携带 variantIndex，不用 ORCID hash 选版本。点击“换一组”只重新请求预览，不修改或保存模板；保留 requestId 保护。展示“随机样本，发送时重新生成”，不显示伪“组合 x/N”。片段编辑预览展示当前正在编辑版本，不能每次切换仍展示原文。
- Applies to：renderServerComposeTemplatePreview、renderExpertMailPreview、预览抽屉与当前变量目标。
- Violation consequence：其他预览入口丢主题引用，或用户误以为预览锁定发送内容。
- 来源：K-preview-mirrors-pipeline；01 I-5/I-6。

### I-6：范围与发布资源
- Rule：仅使用 S-1～S-4 声明的新结构/样式；既有通用 class 不全局改写。静态资源现有 11 项统一 bump 到 `20260923-snippet-reference-variants`，顺序不变，task-modal-runtime.js 仍不带版本键。不引入前端依赖或新运行时资源。业务 WIP、旧 QA 编辑器保留。
- Applies to：index.html、styles.css、app.js 和测试。
- Violation consequence：其他弹窗样式变坏、旧 JS 与新 DOM 混用。
- 来源：K-frontend-cache-key-triad；K-js-test-invocation-surface。

## 样式契约

既有规则不修改，以下新增 CSS 必须逐字落地。类作用域限定在新组件，禁止 inline style 和未声明 class。DOM 中动态 ID/文本/hidden/disabled/ARIA 值可按状态设置，结构不能由执行者另行设计。

### S-1：回复片段单编辑区

- 复用：`.span-2` styles.css:1193（跨两列）、`.button/.small/.danger` :802/:2482/:862、`.badge/.primary` :1054/:1092、`.var-editor-wrap` :5845、`.var-insert-wrap/.var-insert-btn/.var-insert-menu` :5884/:5889/:5905、`.content-variant-input.duplicate` :6610、全局 `[hidden]` :96。外层替换旧 `full-width` 为真正跨 form-grid 的 `span-2`，解决旧容器只占半列的问题。
- 新增 CSS：

```css
.snippet-variant-editor {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 8px;
}
.snippet-version-toolbar {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
}
.snippet-version-spacer {
    flex: 1;
}
.snippet-variant-editor .content-variant-row {
    display: block;
    margin: 0;
}
.snippet-variant-editor textarea {
    min-height: 220px;
    line-height: 1.6;
    resize: vertical;
}
.snippet-variant-editor .content-variant-duplicate-hint {
    margin: 0;
}
.snippet-variant-editor .button:disabled,
.snippet-variant-editor .button:disabled:hover,
.snippet-variant-editor .button:disabled:active {
    opacity: 0.45;
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
}
.snippet-variant-editor .button:focus-visible {
    outline: 2px solid var(--primary);
    outline-offset: 2px;
}
```

- DOM：仅替换原“片段内容”和“内容变体”两块；元数据、默认/启用和底部按钮原位保留。

```html
<div class="span-2 content-variants-block snippet-variant-editor">
  <div class="snippet-version-toolbar">
    <span>片段内容</span>
    <button type="button" class="button small" data-action="variant-prev" aria-label="上一个版本">‹</button>
    <span id="replySnippetVersionLabel" class="badge primary" aria-live="polite">原文 · 1 / 1</span>
    <button type="button" class="button small" data-action="variant-next" aria-label="下一个版本">›</button>
    <span class="snippet-version-spacer"></span>
    <div class="var-insert-wrap">
      <button type="button" class="var-insert-btn snippet-variable-icon" data-var-insert-target="replySnippetContent" title="插入变量" aria-label="插入变量">{}</button>
      <div class="var-insert-menu" hidden></div>
    </div>
    <button type="button" class="button small" data-action="add-content-variant">+ 新增变体</button>
    <button type="button" class="button small danger" id="replySnippetRemoveVersion" data-action="remove-content-variant" disabled>删除变体</button>
  </div>
  <div id="replySnippetOriginalPanel" class="var-editor-wrap">
    <div class="var-validation-hint" id="varHint-replySnippetContent" hidden></div>
    <textarea id="replySnippetContent" name="content" rows="8" placeholder="英文回复片段正文"></textarea>
  </div>
  <div class="content-variants-container" id="replySnippetVariantsContainer" hidden></div>
  <span class="text-muted" id="replySnippetVersionHint">共 1 个版本，引用时随机选择一个</span>
</div>
```

- 动态变体节点，k 是从 0 开始的变体索引；HTML 值必须 escapeHtml，动态目标随重排刷新：

```html
<div class="content-variant-row var-editor-wrap" data-variant-index="k" hidden>
  <div class="var-validation-hint" id="varHint-replySnippetVariant-k" hidden></div>
  <textarea id="replySnippetVariant-k" class="content-variant-input" rows="8" maxlength="2000" placeholder="变体正文"></textarea>
</div>
```

无变体时仍显示原文与 1/1，前后及删除按钮禁用；边界导航不循环；第一个变体为 `变体 1 · 2 / M`。新增定位空的新变体；删除后定位相邻有效版本。删除按钮仅保留工具栏这一处，取消旧圆点、行号和每行删除按钮。

### S-2：主题一行输入、状态、图标及选项

- 复用：全局 input :1131（34px 高，13px 字）、`.badge.primary/.error` :1092/:1080、`.var-insert-wrap/.var-insert-menu` :5884/:5905。不改变 `.var-editor-toolbar` 全局样式。
- 新增 CSS（同一个 snippet-variable-icon 也给 S-1 使用）：

```css
.snippet-subject-field {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
}
.snippet-subject-combobox {
    position: relative;
    display: flex;
    align-items: center;
    flex-wrap: nowrap;
    gap: 6px;
    min-width: 0;
}
.snippet-subject-combobox > input {
    flex: 1;
    width: 0;
    min-width: 0;
}
.snippet-subject-combobox > .badge {
    flex-shrink: 0;
    white-space: nowrap;
}
.snippet-variable-icon,
.snippet-subject-arrow {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex: 0 0 30px;
    width: 30px;
    height: 30px;
    min-height: 30px;
    padding: 0;
    border: 1px solid rgba(37, 99, 235, 0.25);
    border-radius: var(--radius-sm);
    background: var(--primary-light);
    color: var(--primary-hover);
    font-size: 12px;
    line-height: 1;
    cursor: pointer;
}
.snippet-variable-icon:hover,
.snippet-subject-arrow:hover {
    background: var(--primary-tint);
}
.snippet-variable-icon:active,
.snippet-subject-arrow:active {
    background: var(--primary-tint);
    color: var(--primary-active);
}
.snippet-variable-icon:focus-visible,
.snippet-subject-arrow:focus-visible {
    outline: 2px solid var(--primary);
    outline-offset: 2px;
}
.snippet-variable-icon:disabled,
.snippet-variable-icon:disabled:hover,
.snippet-variable-icon:disabled:active,
.snippet-subject-arrow:disabled,
.snippet-subject-arrow:disabled:hover,
.snippet-subject-arrow:disabled:active {
    opacity: 0.45;
    cursor: not-allowed;
    background: var(--primary-light);
    color: var(--primary-hover);
}
.snippet-subject-options {
    position: absolute;
    top: calc(100% + 6px);
    left: 0;
    right: 0;
    z-index: var(--z-dropdown);
    max-height: 240px;
    overflow-y: auto;
    padding: 6px;
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-md);
    background: #ffffff;
    box-shadow: var(--shadow-lg);
}
.snippet-subject-option {
    display: block;
    width: 100%;
    padding: 8px 10px;
    border: 0;
    border-radius: var(--radius-sm);
    background: transparent;
    color: var(--text-main);
    font: inherit;
    font-size: 12px;
    line-height: 1.5;
    text-align: left;
    overflow-wrap: anywhere;
    cursor: pointer;
}
.snippet-subject-option:hover,
.snippet-subject-option[aria-selected="true"] {
    background: var(--primary-light);
    color: var(--primary);
}
.snippet-subject-option:active {
    background: var(--primary-tint);
}
.snippet-subject-option:focus-visible {
    outline: 2px solid var(--primary);
    outline-offset: -2px;
}
```

- DOM：替换旧主题 label 内的双行工具栏；外层不用 label 包住多个交互控件。

```html
<div class="snippet-subject-field">
  <label for="composeTemplateSubject">邮件主题</label>
  <div class="snippet-subject-combobox">
    <input id="composeTemplateSubject" name="subject" type="text" required role="combobox" aria-autocomplete="list" aria-expanded="false" aria-controls="composeSubjectOptions" autocomplete="off">
    <span id="composeSubjectSource" class="badge">自定义</span>
    <button type="button" id="composeSubjectToggle" class="snippet-subject-arrow" title="选择回复片段" aria-label="选择回复片段" aria-controls="composeSubjectOptions">▾</button>
    <div class="var-insert-wrap">
      <button type="button" class="var-insert-btn snippet-variable-icon" data-var-insert-target="composeTemplateSubject" title="插入变量" aria-label="插入变量">{}</button>
      <div class="var-insert-menu" hidden></div>
    </div>
    <div id="composeSubjectOptions" class="snippet-subject-options" role="listbox" aria-label="可引用的回复片段" hidden></div>
  </div>
  <div class="var-validation-hint" id="varHint-composeTemplateSubject" hidden></div>
</div>
```

动态选项结构：`<button type="button" class="snippet-subject-option" id="composeSubjectOption-ID" role="option" aria-selected="false" data-snippet-id="ID">名称或摘要 · #ID</button>`。无候选时容器用 `<span class="text-muted">没有可用于主题的单行片段</span>`。不额外显示第二个主题编辑框。

下拉箭头显示所有合法候选，输入时按标签过滤但不自动确立引用。上下键更新高亮及 aria-activedescendant，Enter 选择且不提交表单，Escape 关闭，Tab 离开，点击外部关闭；键盘选项变化与 badge 只在真正选择/精确匹配后同步。自定义输入长度通过校验限制为 255，input 不加 maxlength，以免截断合法的选项显示标签（名称加 ID 并不是主题正文）。

### S-3：预览样本提示

- 复用 `.variant-switcher` styles.css:6674、`.text-muted` :2489、`.button.small` :2482，不新增 CSS。
- index.html 原 `#previewVariantSwitcher` 内容替换为：

```html
<div class="variant-switcher" id="previewVariantSwitcher" hidden>
  <span class="text-muted" id="previewVariantLabel">随机样本，发送时重新生成</span>
  <button type="button" class="button small" id="previewVariantResample">换一组</button>
</div>
```

仅模板预览显示这一组；纯变量/片段编辑预览不出现该组。专家详情现有 `.expert-mail-preview-meta` 内追加 `<span class="text-muted" data-role="variant-sample-note">随机样本，发送时重新生成</span>`，复用已有刷新动作，不加第二套按钮。运行时采用 textContent，不把后端文本当 HTML 注入。

### S-4：模板列表主题标记

- 复用 `.badge/.primary/.error`，不新增 CSS。模板列表现有主题 td 用以下三种互斥结构：

```html
<td><span class="badge primary">引用</span> 片段名称 · #ID</td>
<td><span class="badge">自定义</span> 用户主题文本</td>
<td><span class="badge error">引用失效</span> #ID</td>
```

名称和文本全部 escapeHtml。不改变表格其余列和操作按钮。

## 现状审计

### 前端表单、API、临时状态读写

后端四张表完整 schema/读写见 [01 现状审计](01-reply-snippet-variants-backend.md#现状审计)；本计划不新增存储，前端通过现有接口读写。代码回执见 [证据 E-1～E-3](reply-snippet-variants-evidence.md)。

- 片段数据：loadReplySnippets 读 `/api/reply-snippets` 到 state.replySnippets；fillReplySnippetForm:5872 读 detail 原文/variants；saveReplySnippet:5897 从 name=content 与 collectContentVariants 写 POST/PUT；hideReplySnippetEditor:5854 清表单及变体。CRUD API/数组格式不变。
- 变体 DOM：renderContentVariantRows:10856 为每个变体常驻 textarea，setActiveVariant:10914 仅显隐；collect:10954、validate:10969、add:11020、remove:11029 都遍历容器中的全部 `.content-variant-input`。原文目前在外面的另一 textarea，故两个框同时显示。旧知识中 QA 共用该编辑器已经过时，当前 QA 页面只编辑标准事实正文，qaFactCardEditor.test.js:130 明确只要求片段 helper 保留。
- 布局缺陷直接证据：replySnippetForm 是 `.form-grid` 两列（styles.css:1105）；原变体外层用 `.full-width`（index.html:1976），但此类的跨列规则只在 `.compose-template-fields .full-width` 生效（styles.css:6570）。因此片段弹窗没有得到跨列样式，解释截图中区域挤在左半边；无需全局改 form-grid。
- 模板读写：loadComposeTemplates:11105→state.composeTemplates；renderComposeTemplatesTable:11246 直接显示 template.subject；openComposeTemplateEditor:11271 回填 subject；saveComposeTemplate:11509 发送 subject；hideComposeTemplateEditor 清选中模板 ID。新增引用状态必须在 open/hide/reset 一致初始化清理。
- 预览两入口：renderServerComposeTemplatePreview:11424 发草稿；renderExpertMailPreview:11161 发已保存模板。后者当前 `variantIndex: javaStringHashCode(orcidId):11181`，不能只修编辑弹窗。randomComposeTemplatePreviewExpert:11461 的样本拼接 collectComposeTemplatePreviewSampleText:10834 也需取引用主题的原文，不能用显示名称提取变量。
- 变量菜单：refreshVariableEditors:4185 按按钮 data-var-insert-target 重建菜单；rememberVarSelection:4070、resolveVarInsertRange、insertVarAtCursor:4108 负责光标。切版本须刷新按钮目标与菜单绑定，不另写字符串 append。新增变体 ID 仍走既有变量规则，不把它加入仅模板字段的 lenient 分类。
- 预览状态：updatePreviewVariantSwitcher:3912 和 stepPreviewVariantIndex:3931 使用“组合 x/N”；实际后台 size 是最大单池长度。事件注册在 app.js:14266/14267；subject input 的预览刷新注册在 :14346。替换后删除孤立旧监听，保留 requestId 和 600ms debounce。
- 交互 X-1：切换→保存全量→API→重新打开；X-2：选择片段→ID 状态→模板保存/列表→回填；X-3：主题选项/变量→两类 preview-draft→后端随机；X-4：片段停用/内容更新→前端选项/已保存引用→错误；X-5：DOM 新旧资源与事件绑定。

### 前端样式盘点

设计实值：主色 #1e40af，hover #1e3a8a，active #172554；正文 #1e293b，辅助 #94a3b8，错误 #e11d48；边框 rgba(15,23,42,.11)，小圆角 7px、中圆角 10px；表单 gap 12px/padding 16px，input 34px/13px，按钮 32px/12px、小按钮 26px/11px，badge 11px/line-height:1；input focus 为主色边框加 3px 蓝色阴影。完整逐字基线在本文末尾自动摘录段。

复用 class 的完整块：styles.css:802–870、1054–1096、1105–1166、1188–1199、2482–2494、5845–5857、5884–5920、6585–6682。新增选择器均派生并限定作用域，不修改这些通用规则；它们的使用位置 grep 在证据 E-3，因未就地改动不牵动其他组件。

审计初始静态键为 `20260923-discovery-traffic`，index.html:11–15、2195–2200 共 11 项；精确 `rg -n -F '20260923-discovery-traffic' src/test` 返回 exit=1、空输出，该次快照没有需要更改的固定字面量测试。测试 meetingConfirmationAssets 从 styles.css 派生键并断言资源数/顺序，执行前必须重新取当前键核实，不照旧知识猜名单。（来源：K-frontend-cache-key-triad、K-plan-quantified-claims-need-grep-receipts）

### 改动前基线（逐字摘录）

以下是本次读取到的实际 DOM/CSS，不是目标设计；实施只采用 S-1～S-4。

`src/main/resources/static/index.html:1963–1980`

```html
            <label>显示排序<input name="displayOrder" type="number" value="100" required></label>
            <label class="span-2">片段内容
                <div class="var-editor-wrap">
                    <div class="var-editor-toolbar">
                        <div class="var-insert-wrap">
                            <button type="button" class="var-insert-btn" data-var-insert-target="replySnippetContent">+ 插入变量 ▾</button>
                            <div class="var-insert-menu" hidden></div>
                        </div>
                    </div>
                    <div class="var-validation-hint" id="varHint-replySnippetContent" hidden></div>
                    <textarea id="replySnippetContent" name="content" rows="6" placeholder="英文回复片段正文" required></textarea>
                </div>
            </label>
            <div class="full-width content-variants-block">
                <span class="field-label">内容变体 <span class="badge primary content-variants-count" hidden></span></span>
                <div class="content-variants-container" id="replySnippetVariantsContainer"></div>
                <p class="compose-template-variable-hint">主体与全部变体共同轮换，每位专家固定命中其中一个；变体输入 textarea 支持多行</p>
            </div>
```

`src/main/resources/static/index.html:2016–2027`

```html
                            <label>邮件主题
                                <div class="var-editor-wrap var-editor-inline">
                                    <div class="var-editor-toolbar">
                                        <div class="var-insert-wrap">
                                            <button type="button" class="var-insert-btn" data-var-insert-target="composeTemplateSubject">+ 插入变量 ▾</button>
                                            <div class="var-insert-menu" hidden></div>
                                        </div>
                                    </div>
                                    <input id="composeTemplateSubject" name="subject" required maxlength="255">
                                </div>
                            </label>
                            <label class="full-width">用途描述<textarea name="description" rows="2" maxlength="500"></textarea></label>
```

`src/main/resources/static/index.html:2111–2115`

```html
        <div class="variant-switcher" id="previewVariantSwitcher" hidden>
            <button type="button" class="button small" id="previewVariantPrev" aria-label="上一个变体组合">‹</button>
            <span class="badge primary variant-switcher-label" id="previewVariantLabel">组合 1/1</span>
            <button type="button" class="button small" id="previewVariantNext" aria-label="下一个变体组合">›</button>
        </div>
```

`src/main/resources/static/styles.css:96–98`

```css
[hidden] {
    display: none !important;
}
```

`src/main/resources/static/styles.css:802–870`

```css
.button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    min-height: 32px;
    height: 32px;
    padding: 0 12px;
    border-radius: var(--radius-sm);
    font-weight: 500;
    font-size: 12px;
    cursor: pointer;
    border: 1px solid var(--border);
    background-color: transparent;
    color: var(--text-main);
    transition: transform 0.12s ease, box-shadow 0.15s ease, background-color 0.15s ease, border-color 0.15s ease, opacity 0.1s ease;
    outline: none;
    user-select: none;
    font-family: var(--font-body);
    position: relative;
    overflow: hidden;
}

.button:hover {
    border-color: rgba(15, 23, 42, 0.2);
    background-color: var(--surface);
    transform: translateY(-1px);
    box-shadow: 0 2px 6px rgba(15, 23, 42, 0.08);
}

.button:active {
    transform: translateY(0) scale(0.97);
    box-shadow: none;
    opacity: 0.85;
}

.button.primary {
    background-image: linear-gradient(180deg, var(--primary-bright), var(--primary));
    background-color: var(--primary);
    border-color: transparent;
    color: #ffffff;
    font-weight: 600;
    box-shadow: 0 1px 2px rgba(var(--primary-rgb), 0.4), inset 0 1px 0 rgba(255,255,255,0.18);
}

.button.primary:hover {
    background-image: linear-gradient(180deg, #2f7bff, var(--primary-hover));
    box-shadow: 0 4px 14px rgba(var(--primary-rgb), 0.35), inset 0 1px 0 rgba(255,255,255,0.18);
}

.button.secondary {
    background-color: var(--primary-light);
    border-color: rgba(var(--primary-rgb), 0.12);
    color: var(--primary);
}

.button.secondary:hover {
    background-color: rgba(var(--primary-rgb), 0.1);
}

.button.danger {
    background-color: var(--error-bg);
    border-color: var(--error-border);
    color: var(--error);
}

.button.danger:hover {
    background-color: rgba(var(--error-rgb), 0.1);
}
```

`src/main/resources/static/styles.css:1054–1096`

```css
.badge {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 11px;
    font-weight: 600;
    font-family: var(--font-body);
    line-height: 1;
    background-color: var(--surface);
    color: var(--text-muted);
    border: 1px solid transparent;
}

.badge.ok {
    background-color: var(--success-bg);
    color: var(--success);
    border-color: var(--success-border);
}

.badge.warn {
    background-color: var(--warning-bg);
    color: var(--warning);
    border-color: var(--warning-border);
}

.badge.error {
    background-color: var(--error-bg);
    color: var(--error);
    border-color: var(--error-border);
}

.badge.info {
    background-color: var(--info-bg);
    color: var(--info);
    border-color: var(--info-border);
}

.badge.primary {
    background-color: var(--primary-light);
    color: var(--primary);
    border-color: rgba(var(--primary-rgb), 0.2);
}
```

`src/main/resources/static/styles.css:1105–1164`

```css
.form-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
    padding: 16px;
    overflow-y: auto;
}

.form-grid.single {
    grid-template-columns: 1fr;
}

label {
    display: flex;
    flex-direction: column;
    gap: 4px;
    color: var(--text-muted);
    font-size: 11px;
    font-weight: 600;
    font-family: var(--font-body);
    text-transform: uppercase;
    letter-spacing: 0.3px;
}

input, select, textarea {
    width: 100%;
    height: 34px;
    min-height: 34px;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    padding: 6px 10px;
    color: var(--text-main);
    background-color: var(--panel-bg);
    transition: var(--transition);
    outline: none;
    font-size: 13px;
}

input:hover, select:hover, textarea:hover {
    border-color: rgba(15, 23, 42, 0.2);
}

input:focus, select:focus, textarea:focus {
    border-color: var(--primary);
    background-color: var(--panel-bg);
    box-shadow: 0 0 0 3px rgba(var(--primary-rgb), 0.1), 0 0 12px rgba(var(--primary-rgb), 0.06);
    transition: border-color 0.2s ease, box-shadow 0.25s ease;
}

input:disabled, select:disabled, textarea:disabled {
    background-color: var(--surface);
    color: var(--text-muted);
    cursor: not-allowed;
    border-color: var(--border);
}

textarea {
    resize: vertical;
    height: auto;
}
```

`src/main/resources/static/styles.css:1188–1195`

```css
.span-2,
.form-actions {
    grid-column: 1 / -1;
}

.span-2 {
    grid-column: span 2;
}
```

`src/main/resources/static/styles.css:2482–2492`

```css
.button.small {
    height: 26px;
    min-height: 26px;
    padding: 0 8px;
    font-size: 11px;
}

.text-muted {
    color: var(--text-muted);
    font-size: 12px;
}
```

`src/main/resources/static/styles.css:5845–5856`

```css
.var-editor-wrap {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.var-editor-toolbar {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    gap: 8px;
}
```

`src/main/resources/static/styles.css:5884–5920`

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
    border-radius: var(--radius-sm);
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
    z-index: var(--z-dropdown);
    width: 240px;
    padding: 8px;
    background: var(--panel-bg);
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-md);
    box-shadow: var(--shadow-lg);
}

.var-insert-menu[hidden] {
    display: none;
}
```

`src/main/resources/static/styles.css:6564–6583`

```css
.compose-template-fields {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px 22px;
}

.compose-template-fields .full-width {
    grid-column: 1 / -1;
}

.compose-template-fields textarea {
    min-height: 74px;
    resize: vertical;
}

.compose-template-variable-hint {
    margin: -8px 0 16px;
    color: var(--text-muted);
    font-size: 12px;
}
```

`src/main/resources/static/styles.css:6585–6678`

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
    border-radius: var(--radius-sm);
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

.variant-switcher {
    display: inline-flex;
    align-items: center;
    gap: 6px;
}
```

收尾复核：并行任务已提交发现任务流量功能，HEAD 为 `13fb91e`，静态键更新为 `20260923-discovery-traffic-v2`；按新键精确反查 src/test 仍为空（exit=1）。本计划未改这些业务文件。证据 E-6 保留此次快照；执行前仍重新检查。

## 实现方案

### T-1 合并原文与变体显示（I-1/I-2/I-6；S-1/S-2）

文件：index.html、styles.css、app.js。

按 S-1 替换两块为一个跨两列区域，原文 DOM 固定保留，variants 容器仅存变体。container.dataset.activeIndex 改为全版本索引 0..N，变体行 data-variant-index 保持数组索引 0..N-1；所有导航、错误定位、增删都显式做 ±1 转换，禁止把原文塞入 collectContentVariants。

保留 renderContentVariantRows/collectContentVariants 函数名以兼容现有读取契约与 QA 回归测试。setActiveVariant(0) 显示原文并隐藏变体容器，>0 显示对应变体；新增/删除先采集 raw 值，不能复用会 trim/filter 的提交收集器。fill/hide 均重置索引为 0，启用 checkbox 保持独立、不嵌入轮播行。

变量按钮按当前版本设置 targetId，关闭旧菜单、重建菜单并绑定当前 textarea；保留每个 textarea 的 selection 数据。切版本同步现有 previewDrawer.targetId，编辑变体时预览它的文本。保存对原文先做非空及占位符检查，再对所有变体检查空/重复/占位符；出错将对应版本显示后再聚焦。更新变量校验通用函数时仅必要地阻止“校验后一项合法把前一项错误的提交按钮又启用”，提交时全量校验是最终前端闸门，后台校验继续保留。

### T-2 主题组合输入框（I-3/I-4/I-6；S-2/S-4）

文件：index.html、styles.css、app.js。

按 S-2 实现轻量输入/列表事件，复用 state.replySnippets 和原有 displayLabel。选项显示标签末尾统一追加 ID；不使用现有预览专家的模糊匹配 helper。状态字段放在现有 state 中，不新增全局框架或持久缓存。打开模板时确保片段列表已加载再按 ID 回填；刷新后重新验证选中 ID，但不可自动丢弃失效引用。不得根据 detail 中未带 enabled 的变体字符串推断后台启用池；原文有效而某个启用变体不合法时，保存/预览显示后端候选错误。

抽出一个窄 helper 收集 `{subject, subjectSnippetId}`，模板保存与草稿预览共用，保证自定义时显式 null。input/change、下拉选取、变量插入触发相同状态同步；使用原菜单插入变量，引用时 disable 图标。引用 label 不计入 255 字的主题限制。

模板列表按 S-4 显示引用/自定义；旧 subject 快照不能用来判断引用身份。已保存引用失效要有标记；用户改选/手动输入才解除，接口拒绝时展示原错误，不隐藏成“保存失败”。不在本页增加编辑共享片段的快捷弹窗。

### T-3 预览双入口接线（I-3/I-4/I-5；S-3）

文件：index.html、styles.css、app.js。

草稿预览 helper 与专家详情保存模板预览都传 subjectSnippetId、不传 variantIndex。collectComposeTemplatePreviewSampleText 对主题引用使用片段原文作为专家抽样文本，保留现有正文拼接规则，不引入“所有候选变量必须具备”的新抽样策略。

替换 updatePreviewVariantSwitcher 的实现为按是否模板预览显隐 S-3，不再根据 poolSize 判断显示；保留函数名可减少无关测试改动。删掉 stepPreviewVariantIndex 及其 prev/next 监听；新 resample 监听只调已有 refreshPreviewDrawer。专家详情移除 ORCID hash 选择；javaStringHashCode 若仍有其他调用则保留，不能扩大删除范围。两个位置显示样本提示且对重复渲染不重复追加 DOM。预览错误显示后端可读错误，不能在失效引用时展示上一次成功样本而无错误提示。

### T-4 发布接线与行为测试（I-1 至 I-6；S-1 至 S-4）

文件：变更清单所列 HTML/JS/CSS 及三个测试文件。

统一 11 项缓存键；不新增 JS/CSS 资源。新增 replySnippetVariantEditor.test.js 使用 node:test/vm 与有真实值/显隐/事件行为的最小 DOM stub，测试切换与全量保存，不只检查字符串存在；组合输入逻辑、保存 payload、键盘输入状态、重复名称 ID 用例也放同一文件。更新 composeTemplatePreview.test.js、expertMailPreviewTab.test.js 的请求字段/无固定 seed 契约，保留原账号、收件人、strict 和乱序测试。

## 变更文件清单

共 6 个文件；一个前端子系统；不新增数据库字段。

| # | 文件 | 修改 |
|---|---|---|
| 1 | src/main/resources/static/index.html | 片段/主题 DOM、预览按钮、缓存键 |
| 2 | src/main/resources/static/styles.css | S-1/S-2 新作用域样式 |
| 3 | src/main/resources/static/app.js | 状态、导航、主题引用、两类预览 |
| 4 | src/test/js/replySnippetVariantEditor.test.js | 新增编辑器/主题行为测试 |
| 5 | src/test/js/composeTemplatePreview.test.js | 草稿预览引用与随机样本 |
| 6 | src/test/js/expertMailPreviewTab.test.js | 专家详情预览不再使用 ORCID hash |

若执行前当前缓存键反查发现新的固定键测试，应先修订文件清单并保持 ≤10，不偷偷改清单外文件。现有 app.js/index.html 含发现任务流量 WIP，必须保留，不覆盖整个文件。

## 验收标准

- I-1：原文 MAIN、三变体 A/B/C，编辑 B→B2，前后切换后提交仍为 content=MAIN、variants=[A,B2,C]；删除 B2 后仍为 [A,C]；添加空变体不丢其他值，原文不可删除；界面可见 textarea 始终恰一个。
- I-2：当前停在 C，隐藏 A=空或非法占位符时不能提交且跳到 A；原文为空且停在变体时跳回原文；重复原文/变体定位明确；菜单只改当前版本选中光标区，不改其他版本；取消重开无未保存值。
- I-3：同名两片段分别选取产生不同 ID；只输入名称不自动选择；输入完整唯一标签可识别；引用后输入自由文本发送 null ID；新建/编辑/重开/列表一致。
- I-4：停用或把片段改成多行后，旧引用显示失效但 ID 保留；自定义输入可解除；引用时图标 disabled，不能插入到 label；前端对可知的原文进行单行预筛；服务端对启用候选池做最终校验，错误明确展示。
- I-5：两种 preview-draft 的 payload 含 ID 且无 variantIndex；一次点击仅请求预览，不 POST/PUT 模板；旧响应后到不覆盖新样本；专家详情无 ORCID seed 断言；预览当前变体不是固定原文。
- I-6：diff 不包含 QA 页改版、旧业务 WIP 删除、额外资源；11 项缓存键一致且顺序保持。按当前字面量反查测试，不能套历史文件数。
- S-1/S-2：CSS 逐字比对上述代码块；DOM 结构、禁用状态、ARIA 和文本匹配。无新增 inline style/未声明 class。变量菜单用原实现，其他按钮仍按原样显示。
- S-3/S-4：样本提示、换一组、引用/自定义/失效 badge 和规定结构一致；“组合 x/N”旧控件不残留。

实施后验证命令：

```bash
node --check src/main/resources/static/app.js
node --test src/test/js/replySnippetVariantEditor.test.js src/test/js/composeTemplatePreview.test.js src/test/js/expertMailPreviewTab.test.js src/test/js/varInsertAtCursor.test.js src/test/js/qaFactCardEditor.test.js src/test/js/replySnippetLabel.test.js src/test/js/meetingConfirmationAssets.test.js
node --test src/test/js/*.test.js
```

不以 verify.sh 代替上面的 JS 门禁（K-js-test-invocation-surface）。浏览器人工验收补足 stub 无法证明的布局与原生表单焦点。此阶段未实施，以上不表示已通过。

## 人工验收清单

### A-1：原文和十个变体只用一个可见框
- 前置条件：后台创建 CUSTOM 片段 `轮播测试`，原文 `MAIN`，添加 10 个不重复单行变体 `V1`～`V10`。
- 操作步骤：1. 打开编辑器。2. 连续右切到最后，再左切。3. 改 V5 为 `V5 edited`，保存关闭再打开。4. 新增一项、填 `V11` 再删掉。5. 切到原文查看删除按钮。
- 预期结果：初始 `原文 · 1 / 11`；最后 `变体 10 · 11 / 11`；始终一个可见正文框；再次打开 V5 仍为 `V5 edited`，其他原值不丢；原文删除按钮禁用。片段类型/名称/ID/排序/默认/启用仍可按原规则查看或修改。
- 覆盖：O-1；I-1/I-2；S-1；X-1；P-1。

### A-2：隐藏错误与变量光标
- 前置条件：A-1 的片段，编辑未保存。
- 操作步骤：1. 清空 V2，切到 V7，点击保存。2. 填回 V2，再把 V3 改为 MAIN，切开后保存。3. 修正 V3，在 V4 文本中间放光标，点击 `{}` 选一个合法变量。4. 取消、重开。5. 原文清空后切到变体保存。
- 预期结果：步骤 1 自动定位 V2 并提示变体不能为空；步骤 2 定位 V3 提示与主体重复；步骤 3 只在 V4 的光标处插入变量，不改 MAIN；步骤 4 放弃未保存修改；步骤 5 定位原文提示非空，不出现浏览器无法聚焦隐藏控件错误。
- 覆盖：I-1/I-2；S-1；X-1；P-1/P-2。

### A-3：主题引用与自由输入
- 前置条件：后台创建两个同名 `合作主题` 的启用单行片段，各记 ID=s1/s2；原文分别 `Topic one`/`Topic two`。创建第三个两行正文片段。
- 操作步骤：1. 打开模板主题下拉。2. 选择 #s2 并保存。3. 关闭重开、看模板列表。4. 把输入改为 `A personal question`，保存重开。5. 输入同名标签但不含 ID，再输入完整 `合作主题 · #s1`。6. 用上下键/Enter/Escape 重复选择与关闭。
- 预期结果：两个同名选项用 ID 区分，第三个多行片段不在可选列表；步骤 2/3 显示“引用”及 #s2；步骤 4 显示“自定义”和原自由文本；不完整标签不误引用，完整唯一标签成为 #s1 引用；Enter 选择不提交模板，Escape 关闭选项；主题文字、badge、▾、`{}` 在一行。
- 覆盖：O-2；I-3/I-4；S-2/S-4；X-2；P-2。

### A-4：共享片段失效与变量按钮
- 前置条件：模板主题引用 s2；另开后台页能够编辑该测试片段。
- 操作步骤：1. 引用状态尝试点 `{}`。2. 停用 s2，刷新并打开模板编辑。3. 尝试预览/保存。4. 输入 `Hello `，在末尾用 `{}` 插入合法变量并预览。5. 再启用 s2，把它改成两行，重复打开引用它的测试模板。
- 预期结果：引用状态图标禁用并提示到片段编辑；失效引用显示 `引用失效 · #s2`，不偷偷发送旧主题；有明确错误且不把旧预览冒充新结果；自由输入后转自定义，图标恢复、光标插入有效；多行同样标记失效。
- 覆盖：I-3/I-4/I-5；S-2/S-4；X-4；P-2。

### A-5：两个预览入口与参数保持
- 前置条件：模板主题与正文均引用含变体片段，选测试专家及发件账号；仅预览。
- 操作步骤：1. 编辑器打开预览，切换 strict 开关，点“换一组”。2. 保存后到该专家详情打开同一模板预览。3. 快速切换两个专家/账号再观察最后返回的内容。4. 片段编辑页切到 V5 并打开其预览。
- 预期结果：前两个入口都有已渲染的主题与正文，并提示 `随机样本，发送时重新生成`；无“组合 x/N”；收件人与发件变量取当前选择，严格检查仍生效；较旧请求不能覆盖后选对象；片段预览显示 V5 而非 MAIN；无实际发信、无额外模板保存请求。
- 覆盖：O-3；I-2/I-5；S-3；X-3；P-3。

### A-6：布局与旧表单回归
- 前置条件：浏览器宽度依次设为 1440px、1024px、768px，页面缩放 100%，保存前保留截图作为对照。
- 操作步骤：1. 查看单编辑区、主题行、打开下拉及变量菜单。2. 回到 QA 编辑页及正文自定义块操作原变量按钮。3. 编辑模板名称/描述/启用，移动正文块、保存、取消和关闭弹窗。4. 普通刷新后再次打开新界面。
- 预期结果：片段区域跨两列，textarea 至少 220px 高、行高 1.6；主题行不换行，输入可收缩，图标 30×30px，间距 6px；主色 #1e40af、小圆角 7px，焦点有可见轮廓；下拉不会改变表单行高；其他变量按钮仍有原文字和行为，QA 不出现变体区域；元数据/块顺序按保存结果回显，取消和关闭正常；普通刷新后仍是同一套新资源。
- 覆盖：I-6；S-1/S-2/S-3/S-4；X-5；P-1/P-2/P-3/P-4。
