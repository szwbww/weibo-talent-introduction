# P2：专家详情新增「邮件预览」标签页 + 跳转模板编辑器并自动选中该专家

> 隶属主计划 `expert-mail-preview-main.md`。执行前必须先读主计划的
> `## 共享不变量`（M-1..M-4）、`## 共享验证命令`、`## 需求描述` 的 must-NOT-change 表。
> **本计划必须在 P1 完成之后执行**（依赖理由见主计划 `## 拆分理由与顺序约束`）。
>
> 编号约定：本文件的 `I-n` / `S-n` 为**计划内局部编号**，与 P1 的同名编号无关；跨计划共享的不变量一律用 `M-n`。

---

## 需求描述

**Observable outcome**

1. 在专家列表选中某位专家后，详情区的子标签由三个变为四个：学术档案 / 联系详情 / 模板预览 / **邮件预览**。
2. 「邮件预览」页里有一个模板下拉；选中某个邮件模板后，展示**这位专家**实际会收到的邮件标题与正文（变量已按其数据渲染），并标出哪些变量走了兜底值。
3. 该页有「在模板编辑器中打开」按钮；点击后切到邮件模板页、打开该模板的编辑弹窗、预览抽屉已展开，且预览对象已自动设为当前专家——运营可以直接改模板文本并立刻看到对这位专家的效果。

**What must NOT change**：见主计划 N-1..N-6，逐条为本计划的硬约束。本计划额外强调：

- 既有三个子标签的内容、顺序、默认激活项（`academic`）与懒加载行为完全不变（N-4）。
- 模板编辑器内既有的服务端预览行为不变，包括 variantIndex 轮换与 strict 占位符开关（N-3）。

**Out of scope**

- 不新增任何后端端点、不改任何 Kotlin 文件（本计划后端改动为**零**）。
- 不在预览页里做发送、不做"发送测试邮件"。
- 不做模板下拉的搜索/分组/按 mailType 过滤；下拉列出 `state.composeTemplates` 全集（含禁用模板，用徽标区分），过滤留待后续。
- 不改 `GET /api/compose-templates/{id}/preview`（`MailComposeTemplateController.kt:59-61`），也不为它加 orcidId 参数。
- 不改预览抽屉（`#previewDrawer`）自身的结构与行为，只复用其现有挂载/展开 API。
- 不做反向跳转（从模板编辑器跳回专家详情）。

---

## 关键不变量

### Invariant I-1: 预览必须走 `POST /api/compose-templates/preview-draft`，不得本地渲染
- Rule：本页展示的标题与正文，必须来自 `POST /api/compose-templates/preview-draft` 的响应。**禁止**在前端做任何 `${...}` 替换或兜底值解析；**禁止**改用 `GET /api/compose-templates/{id}/preview`。
- 理由（证据）：`GET /{id}/preview` 的实现是 `MailComposeTemplateService.preview()`（`:187-197`），只做 `resolveBlocks(...).includedTexts.joinToString("\n\n")`，**没有 orcidId 入参、不做变量渲染**，用它必然渲染出带 `${}` 的原始模板文本。而 `previewDraft()`（`:199+`）接收 `orcidId/expertEmail/contactId/senderAccountCode`，变量替换、默认值解析、fallback 检测统一发生在 `MailVariableService.renderPreview()`。
- Applies to：新增的预览渲染函数（唯一网络调用点）。
- Violation consequence：预览与真实发送不一致——正是 `docs/knowledge/mail/K-preview-mirrors-pipeline` 与 `docs/knowledge/template/K-preview-draft-raw-before-render` 反复强调的失效模式。
- 来源: K-preview-draft-raw-before-render + K-preview-mirrors-pipeline

### Invariant I-2: 新页面不得假设 `state.composeTemplates` 已加载
- Rule：进入本标签页时必须自行确保模板数据就绪，且**幂等**（重复切换标签不得重复请求）。写法参照 `loadComposeTemplatePreviewOptions`（`app.js:7980-7991`）的 `state.composeTemplatePreviewOptionsLoaded` 早退范式。
- 理由（证据）：`state.composeTemplates` 的唯一填充点是 `loadComposeTemplates()`（`app.js:7994-7997`），它只在 `loadMailTemplatesView()`（`app.js:7654-7657`）里被调用，而 `loadMailTemplatesView` 只在 `refreshCurrentView()` 的 `state.view === "mail-templates"` 分支触发（`app.js:1645`）。专家详情属 `view === "contacts"`（`app.js:1647`），因此**首次进入专家页时 `state.composeTemplates` 是初始值**。
- Applies to：本页的数据加载入口。
- Violation consequence：用户先进专家页就看到空下拉；先逛过一次模板页才正常——典型的"我这儿好使"型缺陷。
- 来源: original + 参照 K-detail-es-backed-fields-need-authoritative-read（详情页不得把别处的缓存当既有事实）

### Invariant I-3: 跳转序列必须在切视图**之前**备好模板数据
- Rule：「在模板编辑器中打开」的执行顺序必须是：先 `await` 拿到目标模板对象 → 再 `setView("mail-templates")` → 再 `switchMailTemplatesSubTab("compose-templates")` → 再 `openComposeTemplateEditor(template)` → 再写入专家上下文 → 最后 `await openComposeTemplatePreview()`。**禁止**依赖 `setView` 的副作用来加载模板。
- 理由（证据）：`setView`（`app.js:1619-1640`）不是 `async`，其内部第 1634 行调用 `refreshCurrentView()` **未 await**。紧随其后读取 `state.composeTemplates` 会拿到旧值（首次进入时为空），`openComposeTemplateEditor(undefined)` 会开出一个空白"新建模板"弹窗。
- Applies to：跳转处理函数。
- Violation consequence：点按钮后弹出空白新建模板框，用户以为模板被清空。
- 来源: original

### Invariant I-4: 专家上下文必须"输入框 + state"双写
- Rule：自动选中专家时，必须**同时**设置：
  - `#previewComposeExpertInput.value` = 与 `composeTemplatePreviewExpertLabel`（`app.js:7682-7686`）**完全同格式**的标签串，即 `姓名 <邮箱>`（无邮箱时退化为 `姓名`）
  - `state.previewDrawer.orcidId` / `.contactId` / `.expertEmail`
- 理由（证据）：`collectComposeTemplatePreviewContext`（`app.js:7702-7721`）先用 input 文本在 `state.composeTemplatePreviewExperts` 里做 label 匹配，匹配到才取其 `contactId/orcidId/expertEmail`；匹配不到才回落 `state.previewDrawer.*`（`:7717-7718` 的 `|| state.previewDrawer.orcidId` / `|| state.previewDrawer.expertEmail`）。既有同类写法见 `randomComposeTemplatePreviewExpert`（`app.js:8213-8244`，双写在 `:8231-8240`），它正是两者都写。
- Applies to：跳转处理函数。
- Violation consequence：跳过去后输入框显示了专家名，但预览渲染的是无专家上下文的兜底文案（或反之），运营改模板时看到的效果是假的。
- 来源: original

### Invariant I-5: 两套详情面板必须对称落地
- Rule：新标签页的 `.detail-tab-panel[data-panel="mail-preview"]` 容器必须**同时**加入两处渲染函数：
  - `showExpertDetail()` — 无 contactId 的专家（`app.js:6600` 起，既有三个面板在 `:6636` / `:6639` / `:6711`）
  - `loadContactDetail()` — 有 contactId 的专家（`app.js:6935` 起，既有三个面板在 `:7007` / `:7010` / `:7168`）
- `renderDetailSubTabs()`（`app.js:6486-6497`，tabs 数组在 `:6487-6491`）是两者共用的，加一次即两处生效；但 panel 容器 DOM 是各写各的。
- 分流证据：`handleContactAction` 的 `select-expert` 分支（`app.js:8367-8380`）按 `expert?.contactId` 二选一调用。
- Violation consequence：只改一处 → 有联系记录的专家（或反之）点开新标签页是空白，且因为 `activateDetailSubTab`（`:6547-6564`）用 `panel.hidden = ...` 而非报错，是**静默空白**。
- 来源: original + M-3

### Invariant I-6: `strictPlaceholders` 恒为 `false`，且 `fallbackKeys` 必须可见
- Rule：本页发往 `preview-draft` 的请求体中 `strictPlaceholders` 恒为 `false`；响应中的 `fallbackKeys`（`ComposeTemplatePreviewDraftResult`，`MailComposeTemplateService.kt:701`）必须在界面上呈现，不得丢弃。
- 理由：本页的目的正是回答"这位专家收到的信里哪些变量是兜底的"。传 `true` 会让数据不全的专家直接报错而看不到内容，等于把最需要看的情况屏蔽掉。
- Applies to：预览请求构造与结果渲染。
- Violation consequence：运营看到一封"看起来没问题"的预览，实际半数变量走了兜底值。
- 来源: original

### Invariant I-7: 面板内元素一律作用域查询，不使用全局 id
- Rule：新面板内的模板下拉、正文容器等，一律用 `data-role="..."` 属性 + `panel.querySelector(...)` 作用域查询，**不得**使用 `document.getElementById` / 全局 `$("#...")`。
- 理由：两套详情渲染函数（I-5）产出结构相同的面板 DOM，虽然 `#contactDetail` 同时只承载其一（`showExpertDetail` 与 `loadContactDetail` 都是整体 `innerHTML` 覆写），但用全局 id 会让"同一 id 在源码中出现两次"，既踩 HTML id 唯一性，也让 M-3 的存在性核对失去意义。既有 `activateDetailSubTab`（`:6551-6555` 的 `detail.querySelectorAll`、`:6558` 的 `detail.querySelector`）与 `loadTemplatePreview(panel, orcidId)`（`:6565`）已是"传入 panel 再操作"的作用域范式。
- Applies to：新面板的所有 DOM 读写。
- Violation consequence：两套面板的元素互相串扰；且 M-3 的 `grep index.html` 核对无从下手（面板宿主是 `app.js` 模板串，不在 `index.html`）。
- 来源: original + M-3、K-dom-stub-tests-hide-dangling-refs

---

## 样式契约

### S-1: 第四个子标签按钮
- 复用：`.detail-sub-tabs`（`styles.css:8068-8073`）、`.detail-sub-tab`（`:8074-8084`）、`.detail-sub-tab:hover`（`:8085-8087`）、`.detail-sub-tab.active`（`:8088-8091`）。`renderDetailSubTabs`（`app.js:6486-6497`，tabs 数组在 `:6487-6491`）已按数组渲染，加一项即可。
- 新增 CSS：**无**。
- DOM 结构：`app.js:6487-6491` 的 tabs 数组末尾追加一项，逐字：
  ```js
        { key: "mail-preview", label: "邮件预览" }
  ```
  其余数组项、`activeTab` 默认值 `"academic"`、按钮模板串一律不动。
- 禁止项：改 `.detail-sub-tab` 任何既有规则；给新标签加独立配色/角标。

### S-2: 面板占位与加载/错误态
- 复用：`.detail-tab-panel`（结构 class，`app.js:6636` 等处使用）、`.tpl-var-empty` 与 `.tpl-var-loading`（共用规则块 `styles.css:8176-8182`）。
- 新增 CSS：**无**。
- DOM 结构（**两处**渲染函数各插入一份，逐字）：
  ```html
            <div class="detail-tab-panel" data-panel="mail-preview" hidden>
                <div class="tpl-var-empty">切换到本标签页以加载邮件预览。</div>
            </div>
  ```
  插入位置：`showExpertDetail` 中紧跟既有 `data-panel="template"` 面板之后（`app.js:6711-6713` 之后）；`loadContactDetail` 中同理（`app.js:7168-7170` 之后）。
- 加载态逐字：`<div class="tpl-var-loading">加载邮件预览中...</div>`
- 错误态逐字：`<div class="tpl-var-empty">加载失败: ${escapeHtml(e.message)}</div>`（与 `loadTemplatePreview` `app.js:6595` 的既有错误文案格式一致）
- 无 ORCID 态逐字：`<div class="tpl-var-empty">无 ORCID，无法预览邮件。</div>`（与 `app.js:6567` 的既有措辞对齐）
- 禁止项：inline style；新建加载/错误专用 class。

### S-3: 工具条（模板下拉 + 跳转按钮）
- 复用：`.button`、`.button.small`（`styles.css:2329`）。**不复用** `.toolbar`（`styles.css:351-360`）——它带 `background-color: var(--panel-bg)` 与边框，会在已经是面板内部的位置多出一层卡片边框。
- 新增 CSS（**逐字复制到 `styles.css` 末尾的「专家详情 · 邮件预览」注释段内**，不得增删属性或改值）：
  ```css
  /* === 专家详情 · 邮件预览 === */
  .expert-mail-preview-toolbar {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
      margin-bottom: 12px;
  }
  .expert-mail-preview-toolbar select {
      flex: 1;
      min-width: 180px;
      height: 32px;
  }
  ```
- DOM 结构（逐字）：
  ```html
  <div class="expert-mail-preview-toolbar">
      <select data-role="mail-preview-template"></select>
      <button type="button" class="button small" data-role="mail-preview-open-editor">在模板编辑器中打开</button>
  </div>
  ```
- 禁止项：inline style；给 select/button 加未声明 class；使用 `.toolbar`。

### S-4: 标题、正文与兜底变量条
- 复用：`.pre`（`styles.css:1734-1746`）用于正文。**注意**：这使本页成为 `class="pre"` 正文展示点全集的新成员，须按 `CLAUDE.md` 团队沉淀知识 K-mail-body-display-sites 记入知识库（见 `## Phase 6 知识回写`）。`.badge` / `.badge.warn`（`styles.css:900-925`）用于兜底变量标记。
- 新增 CSS（**逐字复制，紧接 S-3 的代码块之后**）：
  ```css
  .expert-mail-preview-subject {
      font-size: 13px;
      font-weight: 600;
      color: var(--text-main);
      padding: 8px 10px;
      background: var(--surface);
      border: 1px solid var(--panel-border);
      border-radius: var(--radius-sm);
      margin-bottom: 8px;
      word-break: break-word;
  }
  .expert-mail-preview-meta {
      display: flex;
      align-items: center;
      gap: 6px;
      flex-wrap: wrap;
      margin-top: 8px;
      font-size: 11px;
      color: var(--text-muted);
  }
  ```
  用到的 token 均已存在：`--text-main: #1e293b`、`--text-muted: #94a3b8`、`--surface: rgba(15, 23, 42, 0.022)`、`--panel-border: rgba(15, 23, 42, 0.08)`、`--radius-sm: 7px`（`styles.css` 的 `:root`）。
- DOM 结构（逐字）：
  ```html
  <div class="expert-mail-preview-subject"></div>
  <div class="pre" data-role="mail-preview-body"></div>
  <div class="expert-mail-preview-meta">
      <span data-role="mail-preview-to"></span>
  </div>
  ```
  兜底变量徽标（每个 key 一枚，追加进 `.expert-mail-preview-meta`）：
  ```html
  <span class="badge warn">兜底: KEY</span>
  ```
- 禁止项：inline style；为正文另造 class 而不用 `.pre`；修改 `.pre` / `.badge` 的既有规则块。

### 既有 class 的使用点声明

本契约**不修改任何既有 class 的规则块**，因此无需列出其全部使用点。四个复用 class（`.detail-sub-tab` / `.tpl-var-empty` / `.pre` / `.badge`）均为"新增使用点"，非"就地修改"。

---

## 现状审计

### 前端渲染链路：专家详情子标签

- **标签定义（共用）**：`renderDetailSubTabs(activeTab = "academic")` `app.js:6486-6497`
  ```js
  const tabs = [
      { key: "academic", label: "学术档案" },
      { key: "contact", label: "联系详情" },
      { key: "template", label: "模板预览" }
  ];
  ```
- **标签切换（共用）**：`activateDetailSubTab(btn)` `app.js:6547-6564`
  - 用 `detail.querySelectorAll(".detail-tab-panel")` + `p.hidden = p.dataset.panel !== tabKey` 显隐（`:6553-6555`）
  - **已有懒加载先例**：`:6557-6562` 的 `if (tabKey === "template")` 分支，用 `panel.dataset.loaded` 做一次性守卫
- **事件绑定**：`app.js:10862-10870`，委托在 `#contactDetail` 上，`event.target.closest("[data-sub-tab]")` → `activateDetailSubTab`。新标签自动继承，**无需新增绑定**。
- **两套 panel DOM（grep 回执）**：
  ```
  $ grep -n "detail-tab-panel" src/main/resources/static/app.js
  6554:    detail.querySelectorAll(".detail-tab-panel").forEach((p) => {
  6636:            <div class="detail-tab-panel" data-panel="academic">
  6639:            <div class="detail-tab-panel" data-panel="contact" hidden>
  6711:            <div class="detail-tab-panel" data-panel="template" hidden>
  7007:            <div class="detail-tab-panel" data-panel="academic" hidden>
  7010:            <div class="detail-tab-panel" data-panel="contact">
  7168:            <div class="detail-tab-panel" data-panel="template" hidden>
  ```
  6636/6639/6711 属 `showExpertDetail`（`:6600`）；7007/7010/7168 属 `loadContactDetail`（`:6935`）。两者默认激活的标签不同（前者 academic，后者 contact），本计划不改这一差异。
- **既有「模板预览」的实际内容**：`loadTemplatePreview(panel, orcidId)` `app.js:6565-6598`，调 `GET /api/experts/template-variables?orcidId=&level=`，渲染的是**变量覆盖率**，不渲染正文。本计划新增的是**正文**预览，两者不重叠、不合并。

### 专家上下文来源

- `state.selectedExpertOrcid` 定义 `app.js:29`。
- 写入点（grep 回执）：
  ```
  $ grep -n "selectedExpertOrcid" src/main/resources/static/app.js
  29:    selectedExpertOrcid: null,
  4738:  ...state.selectedExpertOrcid === contact.orcidId ? "active" : ""...   （只读，列表高亮）
  6560:            loadTemplatePreview(panel, state.selectedExpertOrcid);        （只读，既有消费点）
  7204:    state.selectedExpertOrcid = contact?.orcidId || null;                （写：openContactInList）
  8370:        state.selectedExpertOrcid = orcidId;                             （写：select-expert）
  ```
  两个写入点即两条"切换到另一位专家"的入口，均在渲染详情之前或紧邻其后完成。既有的 `loadTemplatePreview` 已依赖 `state.selectedExpertOrcid`（`:6560`），本计划沿用同一来源以保持一致。
- `loadContactDetail` 的 18 个调用点中，除 `:7203`（`openContactInList`）与 `:8375`（`select-expert`）外均为"当前专家的操作后刷新"，不切换专家，因此不影响 `state.selectedExpertOrcid` 的正确性。
- 无 ORCID 的专家：既有 `loadTemplatePreview` 的处理是渲染提示（`app.js:6566-6569`），本计划按 S-2 的"无 ORCID 态"对齐。

### 预览端点

- `POST /api/compose-templates/preview-draft` — `MailComposeTemplateController.kt:63-65` → `MailComposeTemplateService.previewDraft()`（`:199+`）
- 请求 `ComposeTemplatePreviewDraftRequest`（`MailComposeTemplateService.kt:685-695`）：
  `subject: String` / `subjectVariants: List<String> = emptyList()` / `blocks: List<ComposeDraftBlock> = emptyList()` / `variantIndex: Int?` / `orcidId: String?` / `expertEmail: String?` / `contactId: Long?` / `senderAccountCode: String?` / `strictPlaceholders: Boolean = false`
- 响应 `ComposeTemplatePreviewDraftResult`（`:697-704`）：
  `subject` / `body` / `blocks: List<ComposeTemplatePreviewBlock>` / `fallbackKeys: List<String>` / `toEmail: String?` / `variables: List<PreviewVariableItem>`
- **块结构兼容性（关键）**：
  - `MailComposeTemplateBlockDetail`（`:644-651`）= `id, blockOrder, blockType, refId, refDisplayName, customText`
  - `ComposeDraftBlock`（`:678-683`）= `blockOrder, blockType, refId, customText`
  - 前者是后者的**超集且同名同义**，可直接映射。多余的 `id` / `refDisplayName` 不会导致反序列化失败：仓库无任何 Jackson 定制（`grep -rn "jackson\|ObjectMapper" --include=*.yml --include=*.properties src/main config` 无配置命中；`src/main/kotlin/.../config/` 下无 `ObjectMapper`/`Jackson2ObjectMapperBuilder` bean），Spring Boot 默认 `FAIL_ON_UNKNOWN_PROPERTIES=false`。**尽管如此，仍要求显式映射为 4 个字段**（见实现方案 B-2），不依赖这一默认值。
- 既有前端调用范式：`renderServerComposeTemplatePreview()` `app.js:8178-8210`（构造 payload → `api(...)` → 渲染），可直接照抄请求构造部分。
- **不可用的替代**：`GET /api/compose-templates/{id}/preview`（`Controller:59-61` → `Service:187-197`），见 I-1。

### 模板数据

- `state.composeTemplates` 唯一填充点：`loadComposeTemplates()` `app.js:7994-7997` → `api("/api/compose-templates")` → `MailComposeTemplateController.list()`（`:24-25`）返回 `List<MailComposeTemplateDetail>`，**含 `blocks`**（`MailComposeTemplateService.kt:639`）。
- 调用链：`loadMailTemplatesView()` `app.js:7654-7657` ← `refreshCurrentView()` `app.js:1645`（仅 `view === "mail-templates"`）。→ 支撑 I-2。
- 幂等加载范式：`loadComposeTemplatePreviewOptions()` `app.js:7980-7991`，用 `state.composeTemplatePreviewOptionsLoaded` 早退。

### 跳转所需的既有函数（全部已存在，本计划只编排不新建）

| 函数 | 位置 | 作用 | 注意 |
|---|---|---|---|
| `setView(view)` | `app.js:1619-1640` | 切主视图 | **非 async**，`:1634` 的 `refreshCurrentView()` 未 await → I-3 |
| `switchMailTemplatesSubTab(tab)` | `app.js:7659-7672` | 切模板页子标签，`"compose-templates"` → `mailTemplatesPanelComposeTemplates` | 纯同步 DOM class 切换 |
| `openComposeTemplateEditor(template)` | `app.js:8024-8037` | 打开编辑弹窗 | 内部已调 `loadComposeTemplatePreviewOptions()`（`:8033`）与 `mountPreviewRail({targetId:"composeTemplate"})`（`:8036`） |
| `openComposeTemplatePreview()` | `app.js:2722-2728` | 展开预览抽屉 | async |
| `composeTemplatePreviewExpertLabel(expert)` | `app.js:7682-7686` | 专家标签格式 `姓名 <邮箱>` | I-4 的格式基准 |
| `collectComposeTemplatePreviewContext()` | `app.js:7702-7721` | 从 input + state 解析预览上下文 | I-4 的消费方 |

**跨视图跳转先例**：`openContactInList(contactId)` `app.js:7198-7212` 已经是"`setView` + 显式 `await loadContacts()` + `await loadContactDetail()`"的写法——它**没有**依赖 `setView` 的副作用，与 I-3 要求一致。

### 关键 id 存在性核对（M-3）

```
$ for id in previewComposeExpertInput previewComposeExpertOptions previewDrawer composeTemplateModal contactDetail; do
      printf "%-30s %s\n" "$id" "$(grep -c "id=\"$id\"" src/main/resources/static/index.html)"; done
previewComposeExpertInput      1
previewComposeExpertOptions    1
previewDrawer                  1
composeTemplateModal           1
contactDetail                  1
```
全部存在。新面板自身不引入任何 `index.html` id（I-7），故其宿主核对对象是 `app.js` 的模板串生成处。

### Interaction points

| # | 写路径 | 读路径 | 本计划是否处理 |
|---|---|---|---|
| X-1 | `handleContactAction` 的 `select-expert` 写 `state.selectedExpertOrcid`（`app.js:8370`） | 新预览面板读它构造 `orcidId` | 是 —— A-1 覆盖 |
| X-2 | `openContactInList` 写 `state.selectedExpertOrcid`（`app.js:7204`） | 同上 | 是 —— A-13 覆盖（从收发件箱跳进来的入口） |
| X-3 | 跳转函数写 `#previewComposeExpertInput.value` 与 `state.previewDrawer.*` | `collectComposeTemplatePreviewContext()`（`app.js:7702-7721`） → `renderServerComposeTemplatePreview()`（`:8178`） | 是 —— I-4、A-4 覆盖 |
| X-4 | P1 写 `reply_snippet.name` | 本页预览响应的 `blocks[].refDisplayName`（`app.js` 新面板消费） | 是 —— 主计划 `## 跨子计划的收尾检查` 第 1 条 |

### 前端样式盘点

- **可复用 class**：
  - `.detail-sub-tabs` — `styles.css:8068-8073` — 子标签容器（flex + 下边框）
  - `.detail-sub-tab` / `:hover` / `.active` — `styles.css:8074-8091` — 子标签按钮三态
  - `.detail-tab-panel` — 结构 class，无独立 CSS 规则（`grep -n "\.detail-tab-panel" styles.css` 无命中），仅作 `data-panel` 载体
  - `.tpl-var-empty, .tpl-var-loading` — `styles.css:8176-8182` — 空/加载态文案（居中、20px padding、13px、muted）
  - `.pre` — `styles.css:1734-1746` — 正文块（mono 11px / 1.6 行高 / `max-height: 240px` 滚动 / `white-space: pre-wrap`）
  - `.badge` / `.badge.warn` — `styles.css:900-913` / `920-925` — 徽标
  - `.button` / `.button.small` — `styles.css:2329` — 按钮
  - `.toolbar` — `styles.css:351-360` — **明确不复用**（S-3 已说明理由）
  - `.field-label` — 在 `index.html:1756` 被使用，但 `styles.css` 中**无 bare `.field-label` 规则**（`grep -n "^\.field-label" styles.css` 无命中；`grep -n "field-label"` 的 3 条命中分别是 `.analysis-field-label` `:3034`、`.bsc-field-label` `:5213`、`.batch-config-field-label` `:8655`，均为不同 class）→ 不得引用
- **设计基准 token 实值**（`styles.css` 的 `:root`）：
  `--primary: #1e40af`、`--text-main: #1e293b`、`--text-muted: #94a3b8`、`--panel-border: rgba(15, 23, 42, 0.08)`、`--border: rgba(15, 23, 42, 0.11)`、`--surface: rgba(15, 23, 42, 0.022)`、`--warning: #d97706`、`--warning-bg: rgba(217, 119, 6, 0.08)`、`--warning-border: rgba(217, 119, 6, 0.2)`、`--radius-sm: 7px`、`--radius-md: 10px`、`--font-mono: 'SF Mono', ui-monospace, Menlo, monospace`
- **DOM 结构约定**：详情面板由 `app.js` 的模板字符串整体生成并写入 `#contactDetail.innerHTML`；面板内交互靠 `#contactDetail` 上的事件委托（`app.js:10862-10870`）+ `data-*` 属性，不用 `addEventListener` 逐个绑定。
- **改动前基线**：
  - `app.js:6486-6497`（`renderDetailSubTabs` 全文）—— 见上文「标签定义」
  - `app.js:6711-6713`：
    ```js
            <div class="detail-tab-panel" data-panel="template" hidden>
                <div class="tpl-var-empty">切换到本标签页以加载模板变量预览。</div>
            </div>
    ```
  - `app.js:7168-7170`：与上完全相同的三行
  - `app.js:6557-6562`（`activateDetailSubTab` 的懒加载分支）：
    ```js
    if (tabKey === "template") {
        const panel = detail.querySelector('[data-panel="template"]');
        if (panel && !panel.dataset.loaded) {
            loadTemplatePreview(panel, state.selectedExpertOrcid);
        }
    }
    ```
  - `styles.css` 现有末段附近：`.tpl-var-empty, .tpl-var-loading { ... }`（`:8176-8182`）之后紧接 `.preview-drawer-shell`（`:8184`）——新 CSS 段落插在这两者之间或文件末尾均可，须在 diff 中保持连续。

---

## 实现方案

### 阶段 A：标签与面板骨架（遵守 I-5、S-1、S-2）

**A-1** `renderDetailSubTabs`（`app.js:6487-6491`）tabs 数组追加 `{ key: "mail-preview", label: "邮件预览" }`（S-1）。默认 `activeTab` 参数不动。

**A-2** `showExpertDetail`（`app.js:6600`）在 `:6711-6713` 的 template 面板之后插入 S-2 的面板 DOM。

**A-3** `loadContactDetail`（`app.js:6935`）在 `:7168-7170` 的 template 面板之后插入**同一份** S-2 面板 DOM。

**A-4** `activateDetailSubTab`（`app.js:6557-6562`）在既有 `template` 懒加载分支之后，追加平行分支：
```js
if (tabKey === "mail-preview") {
    const panel = detail.querySelector('[data-panel="mail-preview"]');
    if (panel && !panel.dataset.loaded) {
        loadExpertMailPreview(panel, state.selectedExpertOrcid);
    }
}
```
既有 `template` 分支**逐字不动**（N-4）。

### 阶段 B：预览渲染（遵守 I-1、I-2、I-6、I-7、S-3、S-4）

**B-1 新增 `ensureComposeTemplatesLoaded()`**（I-2）——幂等：若 `state.composeTemplates` 已是非空数组则直接返回，否则 `await loadComposeTemplates()`。放在 `loadComposeTemplates`（`app.js:7994`）之后。

**B-2 新增 `loadExpertMailPreview(panel, orcidId)`**：
1. `orcidId` 为空 → 写入 S-2 的"无 ORCID 态"并 return（对齐 `loadTemplatePreview` `:6568-6571`）。
2. 写入 S-2 加载态，置 `panel.dataset.loaded = "true"`。
3. `await ensureComposeTemplatesLoaded()`。
4. 渲染 S-3 工具条：`<select data-role="mail-preview-template">` 的 option 由 `state.composeTemplates` 生成，文本 = `templateName`，禁用模板在文本后附 `（已禁用）`；默认选中第一个 `enabled === true` 的模板，若全禁用则选第一个。
5. 渲染 S-4 的标题/正文/meta 容器（空壳）。
6. 调 `renderExpertMailPreview(panel, orcidId)` 拉首次预览。
7. `catch` → S-2 错误态，并把 `panel.dataset.loaded` 重置为 `""`（对齐 `loadTemplatePreview` `:6595-6596`）。

**B-3 新增 `renderExpertMailPreview(panel, orcidId)`**（I-1、I-6、I-7）：
- 从 `panel.querySelector('[data-role="mail-preview-template"]').value` 取 templateId，在 `state.composeTemplates` 中定位模板对象。
- payload 逐字：
  ```js
  {
      subject: template.subject || "",
      blocks: (template.blocks || []).map((block) => ({
          blockOrder: block.blockOrder,
          blockType: block.blockType,
          refId: block.refId ?? null,
          customText: block.customText ?? null
      })),
      strictPlaceholders: false,
      orcidId,
      contactId: null,
      expertEmail: null,
      senderAccountCode: null,
      variantIndex: 0
  }
  ```
  显式 4 字段映射（不依赖 Jackson 宽松反序列化）；`strictPlaceholders` 硬编码 `false`（I-6）。
- `await api("/api/compose-templates/preview-draft", { method: "POST", body: JSON.stringify(payload) })`。
- 渲染：`subject` → `.expert-mail-preview-subject`；`body` → `[data-role="mail-preview-body"]`（用 `textContent` 或 `escapeHtml`，正文不得当 HTML 解析）；`toEmail` → `[data-role="mail-preview-to"]`，文案 `收件人: <toEmail 或 "未知">`；`fallbackKeys` 逐个追加 S-4 的 `<span class="badge warn">兜底: KEY</span>`（I-6）。
- 加入请求序号守卫（照抄 `composeTemplatePreviewRequestId` 声明 `app.js:7680`、自增 `:8181`、守卫 `:8200` / `:8204` 的范式，用独立计数器），防止快速切换模板时旧响应覆盖新结果。

**B-4 事件接入**：在 `#contactDetail` 的既有委托处理器（`app.js:10862-10870`）中，于 `[data-sub-tab]` 分支之后、`button[data-action]` 分支之前，新增对 `[data-role="mail-preview-open-editor"]` 的 `click` 分派；模板下拉的 `change` 需另加一个 `#contactDetail` 的 `change` 委托监听器（既有只监听 `click`），只处理 `[data-role="mail-preview-template"]`。

### 阶段 C：跳转（遵守 I-3、I-4）

**C-1 新增 `openTemplateEditorForExpert(templateId, orcidId)`**，顺序**严格**为：
1. `await ensureComposeTemplatesLoaded()`；`const template = state.composeTemplates.find(t => Number(t.id) === Number(templateId))`；找不到 → `showStatus("模板不存在，请刷新后重试", "error")` 并 return（**不继续跳转**）。
2. `const expert = (state.contacts || []).find(item => item.orcidId === orcidId)`。
3. `setView("mail-templates")`。
4. `switchMailTemplatesSubTab("compose-templates")`。
5. `openComposeTemplateEditor(template)`。
6. `await loadComposeTemplatePreviewOptions()` —— 确保 `state.composeTemplatePreviewExperts` 就绪，供 I-4 的 label 匹配。
7. **双写专家上下文**（I-4），照抄 `randomComposeTemplatePreviewExpert` `app.js:8213-8244`（双写发生在 `:8231-8240`） 的形状：
   ```js
   const label = composeTemplatePreviewExpertLabel({
       displayName: expert?.displayName,
       expertEmail: expert?.email
   });
   const expertInput = $("#previewComposeExpertInput");
   if (expertInput) expertInput.value = label;
   state.previewDrawer.orcidId = orcidId || null;
   state.previewDrawer.contactId = expert?.contactId ?? null;
   state.previewDrawer.expertEmail = expert?.email || null;
   ```
   注意 `composeTemplatePreviewExpertLabel`（`:7682-7686`）读的是 `expertName || displayName || name` 与 `expertEmail || email`，因此传入对象须用它认识的键名。
8. `await openComposeTemplatePreview()`。

**禁止**把 3 与 1 调换顺序（I-3）。

### 阶段 D：静态资源与测试

**D-1** `index.html` 按 M-1 bump 三处缓存键（`:11`、`:1969`、`:1970`）为同一新值，建议 `20260814-v10-expert-mail-preview-01`（须与 P1 所用值不同）。

**D-2** `src/test/js/batchSendTaskConsoleVisualFix.test.js:37-39` 同步为 D-1 的新值（M-1）。

**D-3 新建 `src/test/js/expertMailPreviewTab.test.js`**，沿用 `composeTemplatePreview.test.js:1-16` 的 `extractFn` + `vm` + DOM stub 范式：
1. `renderDetailSubTabs` 的输出**恰好包含 4 个** `.detail-sub-tab`，第 4 个 `data-sub-tab="mail-preview"`、文本含 `邮件预览`，且前 3 个的 key/label 与改动前一致（N-4 回归）。
2. `renderExpertMailPreview` 构造的 payload 中 `strictPlaceholders === false`（I-6）。
3. 同上 payload 的 `blocks` 每项**恰好 4 个键** `blockOrder/blockType/refId/customText`，不含 `id` / `refDisplayName`（I-1 的显式映射要求）。
4. `renderExpertMailPreview` 的网络调用 URL 为 `/api/compose-templates/preview-draft`、method 为 `POST`；断言源码中**不出现** `/preview` 结尾的 GET 调用（I-1）。
5. `openTemplateEditorForExpert` 的调用顺序：以桩函数记录调用序列，断言 `loadComposeTemplates`（或 `ensureComposeTemplatesLoaded`）先于 `setView`（I-3）；且模板找不到时 `setView` **未被调用**。
6. `openTemplateEditorForExpert` 同时写了 `#previewComposeExpertInput.value` 与 `state.previewDrawer.orcidId`，且 label 值等于对同一 expert 调用 `composeTemplatePreviewExpertLabel` 的结果（I-4）。
7. **两套面板对称**（I-5）：对 `app.js` 源文本断言 `data-panel="mail-preview"` 出现 **2 次**（`showExpertDetail` 与 `loadContactDetail` 各一），与 `data-panel="template"` 的出现次数相等。
8. **样式契约存在性**（S-3/S-4）：对 `styles.css` 源文本断言 `.expert-mail-preview-toolbar` / `.expert-mail-preview-subject` / `.expert-mail-preview-meta` 三个规则块存在；对 `app.js` 断言面板 DOM 中出现 `class="pre"`（S-4 复用要求）且不出现 `style="`。

---

## 变更文件清单

| # | 文件 | 类型 | 改动摘要 |
|---|---|---|---|
| 1 | `src/main/resources/static/app.js` | 修改 | tabs 数组 +1 项；两处面板 DOM；`activateDetailSubTab` 新分支；`ensureComposeTemplatesLoaded` / `loadExpertMailPreview` / `renderExpertMailPreview` / `openTemplateEditorForExpert` 四个新函数；`#contactDetail` 的 click 分派 + 新增 change 委托 |
| 2 | `src/main/resources/static/styles.css` | 修改 | 新增「专家详情 · 邮件预览」段：`.expert-mail-preview-toolbar`（含 `select` 子规则）、`.expert-mail-preview-subject`、`.expert-mail-preview-meta`（S-3/S-4 逐字） |
| 3 | `src/main/resources/static/index.html` | 修改 | **仅**缓存键三处 bump（M-1），无结构改动 |
| 4 | `src/test/js/expertMailPreviewTab.test.js` | 新建 | 8 组断言（D-3） |
| 5 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改 | 缓存键三条断言同步（M-1） |

文件数 **5**（上限 10）。子系统数 **1**（前端 static）。新增共享存储字段数 **0**。后端 Kotlin 改动 **0 个文件**。

**未列入即视为超范围**：任何 `src/main/kotlin/**`；`trust-reply-workbench.js`；`trustReplyWorkbenchSharedMount.test.js`（正则断言三键相等，bump 后自动通过）；`docs/**`（知识回写除外，见 Phase 6）。

---

## 验证命令

> 本项目必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败。全量命令见主计划 `## 共享验证命令`。

```bash
# 本计划新增的前端用例（前端权威门禁，M-2）
node --test src/test/js/expertMailPreviewTab.test.js

# 缓存键断言用例（M-1）
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js

# 受影响的既有前端用例（详情页与模板预览相关，回归）
node --test src/test/js/composeTemplatePreview.test.js src/test/js/contactsLayoutDefault.test.js src/test/js/expertProfileAbsence.test.js src/test/js/expertTagBatchFix.test.js

# app.js 语法检查（与 pom.xml:216 绑定的 node --check 同口径）
node --check src/main/resources/static/app.js

# 全量回归 + 构建 + 卫生：见主计划 ## 共享验证命令
```

通过判据：各 `node --test` 退出码 0 且输出 `pass N` / `fail 0`；`node --check` 退出码 0 且无输出；主计划全量命令的判据见该节。
来源：`CLAUDE.md:5-27`；`node --test` / `node --check` 形式与 `verify.sh` 不可作门禁的结论来源 K-js-test-invocation-surface（对应 `pom.xml:188-231`）。

---

## 验收标准

- **I-1**：`grep -n "preview-draft" src/main/resources/static/app.js` 在新函数体内命中；`grep -n "compose-templates/\${.*}/preview\|/preview\"" src/main/resources/static/app.js` 在新函数体内**无**命中；`expertMailPreviewTab.test.js` 第 4 组断言通过。新函数体内无任何 `\$\{[^}]*\}` 手工替换逻辑（`replace(/\$\{/...)` 之类）。
- **I-2**：`ensureComposeTemplatesLoaded` 存在且被 `loadExpertMailPreview` 与 `openTemplateEditorForExpert` 各调用一次（grep 附回执，M-4）；重复调用不重复发请求（测试或人工 A-6 覆盖）。
- **I-3**：`expertMailPreviewTab.test.js` 第 5 组断言通过（顺序 + 模板缺失时不 setView）。
- **I-4**：`expertMailPreviewTab.test.js` 第 6 组断言通过。
- **I-5**：`grep -c 'data-panel="mail-preview"' src/main/resources/static/app.js` 输出 `2`，且等于 `grep -c 'data-panel="template"' src/main/resources/static/app.js` 的输出（附回执，M-4）。
- **I-6**：`expertMailPreviewTab.test.js` 第 2 组断言通过；`grep -n "strictPlaceholders" src/main/resources/static/app.js` 在新函数中的赋值为字面量 `false`，不读任何 checkbox。
- **I-7**：新函数体内 `grep -n "getElementById\|\\\$(\"#" ` 仅命中 `#previewComposeExpertInput`（跳转函数写既有全局输入框，属既有 DOM，允许）与 `#contactDetail` 委托绑定；面板内元素访问全部为 `panel.querySelector('[data-role=...]')`。
- **S-1**：`git diff src/main/resources/static/app.js` 中 tabs 数组新增行与 S-1 代码块逐字一致；既有三项未改动。
- **S-2**：diff 中两处面板 DOM 与 S-2 代码块逐字一致（含缩进与文案）；四种状态文案与 S-2 给出的字符串逐字一致。
- **S-3 / S-4**：`git diff src/main/resources/static/styles.css` 新增内容与 S-3、S-4 的 CSS 代码块**逐字一致**（属性数量、顺序、值均不得增删改）；`expertMailPreviewTab.test.js` 第 8 组断言通过；diff 中无 `style="`、无未在契约中声明的新 class。
- **M-1**：`grep -o 'v=[^"]*' src/main/resources/static/index.html | sort -u` 只输出 1 个值，且与 `batchSendTaskConsoleVisualFix.test.js:37-39` 三条断言一致，且不等于 P1 使用的值。
- **M-3**：面板宿主是 `app.js` 模板串 —— 以 I-5 的 grep 计数（=2）作为存在性证据；`index.html` 侧本计划无新增选择器，无需核对。
- 回归：`git diff --stat src/main/kotlin` 为空（后端零改动）；执行主计划 `## 共享验证命令` 的全量测试与构建，均通过。

---

## 人工验收清单

### A-1: 有联系记录的专家能看到自己的邮件预览
- 前置条件：至少存在 1 个启用的邮件模板（在「邮件模板」→「邮件模板」子标签可见）；专家列表中存在一位**有** ORCID、**有**联系记录（列表行可点进联系详情）的专家。
- 操作步骤：
  1. 进入「专家/联系人」页，在左侧列表点选该专家。
  2. 在详情区顶部子标签中点「邮件预览」。
  3. 等待加载完成。
- 预期结果：出现模板下拉（已默认选中第一个启用模板）+「在模板编辑器中打开」按钮；下方显示标题一行、正文一块；正文中**没有** `${` 开头的未替换占位符；正文里出现的是这位专家的真实姓名/机构等值。底部显示 `收件人: <该专家邮箱>`。
- 覆盖：需求 observable outcome 2、I-1、I-5、X-1

### A-2: 无联系记录的专家同样能看到（对称性）
- 前置条件：专家列表中存在一位**有** ORCID 但**无**联系记录的专家（点选后详情区默认停在「学术档案」而非「联系详情」）。
- 操作步骤：点选该专家 → 点「邮件预览」标签。
- 预期结果：与 A-1 相同的界面与内容，**不是**空白面板、**不是**"切换到本标签页以加载邮件预览"的静止占位。
- 覆盖：I-5、需求 observable outcome 2

### A-3: 首次进入专家页即可用（不需要先逛模板页）
- 前置条件：**硬刷新浏览器**（确保 `state.composeTemplates` 为初始值），且刷新后**不要**点击左侧「邮件模板」导航。
- 操作步骤：刷新后直接进入「专家/联系人」页 → 点选一位专家 → 点「邮件预览」。
- 预期结果：模板下拉里有模板可选，不是空下拉。
- 覆盖：I-2

### A-4: 跳转后模板编辑器里预览的就是这位专家
- 前置条件：完成 A-1，记下当前专家的姓名与邮箱、以及当前选中的模板名。
- 操作步骤：
  1. 点「在模板编辑器中打开」。
  2. 观察页面切换后的状态。
  3. 查看右侧预览抽屉里的「预览专家」输入框内容。
  4. 查看预览抽屉里渲染出的正文。
- 预期结果：页面切到「邮件模板」页的「邮件模板」子标签；弹出的编辑弹窗标题为「编辑邮件模板」（**不是**「新建邮件模板」），表单里的模板名称 = 步骤 1 之前选中的那个模板名；预览抽屉已展开；预览专家输入框内容**逐字**为 `<专家姓名> <<专家邮箱>>` 格式；抽屉里的正文与跳转前预览页看到的正文一致。
- 覆盖：需求 observable outcome 3、I-3、I-4、X-3

### A-5: 跳转后改模板文本能立刻看到对该专家的效果
- 前置条件：完成 A-4，编辑弹窗与预览抽屉都开着。
- 操作步骤：在任一「自定义文本」内容块的输入框末尾追加一句 `Regards from Suzhou.`，不点保存。
- 预期结果：右侧预览抽屉的正文末尾出现该句，且正文其余部分仍是**该专家**的渲染结果（姓名等未变回占位符）。
- 覆盖：需求 observable outcome 3、I-4

### A-6: 切换模板与重复切标签
- 前置条件：至少 2 个启用模板；完成 A-1。
- 操作步骤：
  1. 在模板下拉里换选另一个模板。
  2. 切到「学术档案」标签，再切回「邮件预览」。
  3. 再快速连续切换模板下拉 3 次。
- 预期结果：步骤 1 后标题与正文更新为新模板的内容；步骤 2 后面板内容仍在（不重新走一遍完整加载动画、不重置为占位文案）；步骤 3 后最终显示的是**最后一次**选中模板的内容，不出现旧模板内容覆盖新内容。
- 覆盖：I-2、B-3 的请求序号守卫

### A-7: 兜底变量可见
- 前置条件：找一位数据不全的专家（例如「模板预览」标签显示变量覆盖率 < 100% 的那种）。
- 操作步骤：点选该专家 → 「邮件预览」标签 → 查看正文下方。
- 预期结果：正文下方出现一枚或多枚黄色徽标，形如 `兜底: recentWorkTitle`；正文中对应位置显示的是模板里写的兜底文案，而不是报错、也不是空白。整个面板**没有**因为数据不全而变成错误提示。
- 覆盖：I-6

### A-8: 无 ORCID 专家的降级提示
- 前置条件：存在一位无 ORCID 的联系人（若没有，可用「模板预览」标签会显示"无 ORCID，无法预览模板变量"的那位）。
- 操作步骤：点选该联系人 → 「邮件预览」标签。
- 预期结果：显示居中灰色文案 `无 ORCID，无法预览邮件。`，不报错、不空白、不卡在加载中。
- 覆盖：S-2

### A-9: 既有三个标签页未被破坏（回归）
- 前置条件：无。
- 操作步骤：
  1. 点选一位**有**联系记录的专家，观察默认激活的标签。
  2. 依次点「学术档案」→「联系详情」→「模板预览」，各停留查看内容。
  3. 换一位**无**联系记录的专家，观察默认激活的标签，重复步骤 2。
- 预期结果：有联系记录的专家默认停在「联系详情」，无联系记录的默认停在「学术档案」（与改动前一致）；三个标签的内容与改动前一致——学术档案显示 H-Index/引用数/发表数/最近发表四个指标卡，模板预览显示"变量覆盖: N/M (X%)"进度条与变量网格（**不是**邮件正文）。
- 覆盖：must-NOT-change N-4

### A-10: 模板编辑器自身的预览未被破坏（回归）
- 前置条件：无。
- 操作步骤：不经过专家页，直接进「邮件模板」→「邮件模板」子标签 → 点任一模板的「预览」→ 在预览抽屉里手动改「预览专家」输入框、勾选/取消 strict 占位符开关、点变体切换。
- 预期结果：三项交互行为与改动前完全一致；strict 开关仍然生效（勾选后数据不全的专家会提示占位符问题）。
- 覆盖：must-NOT-change N-3

### A-11: 片段名称在预览块说明里一致（跨 P1 联合项）
- 前置条件：P1 已完成；存在一个模板，其某内容块引用了一个**已命名**的回复片段。
- 操作步骤：在专家的「邮件预览」标签选中该模板，查看块说明区域显示的块名。
- 预期结果：显示的是 P1 里填的片段名称，与「邮件模板」列表里该模板行的块 pill 文本**逐字相同**。
- 覆盖：X-4、主计划 `## 跨子计划的收尾检查` 第 1 条

### A-13: 从收发件箱跳进专家详情后预览对象正确
- 前置条件：收发件箱里存在一封来自某位专家的邮件，且该专家与当前「专家/联系人」页选中的**不是同一人**。
- 操作步骤：
  1. 先在「专家/联系人」页点选专家 X，打开「邮件预览」确认显示的是 X。
  2. 切到收发件箱，从某封邮件跳转进专家 Y 的联系详情（走 `openContactInList` 入口）。
  3. 在 Y 的详情区点「邮件预览」标签。
- 预期结果：显示的是**专家 Y** 的渲染结果与 Y 的收件邮箱，不是残留的 X。
- 覆盖：X-2

### A-12: UI 目测（对照样式契约实值）
- 前置条件：完成 A-1。
- 操作步骤：打开浏览器开发者工具，选中新面板的各个元素逐项核对。
- 预期结果：
  - 四个子标签的字号 13px、非激活色 `#94a3b8`、激活色 `#1e40af` 且带 2px 下划线 —— 与既有三个**完全一致**，新标签无任何视觉差异。
  - 工具条为一行 flex，下拉与按钮间距 8px，下拉高度 32px、最小宽度 180px，工具条**没有**独立的卡片背景或边框。
  - 标题块字号 13px / 字重 600 / 圆角 7px / 1px 边框 `rgba(15, 23, 42, 0.08)` / 背景 `rgba(15, 23, 42, 0.022)`。
  - 正文块为等宽字体 11px、行高 1.6、超过 240px 高度出现纵向滚动条（与站内其他邮件正文展示一致）。
  - 兜底徽标为黄色胶囊、字号 11px、圆角 999px。
  - 元素上**没有** `style` 内联属性。
- 覆盖：S-1、S-3、S-4

---

## Phase 6 知识回写

### 已由 create-p 在写计划阶段完成（无需执行 agent 再做）

- **新增** `docs/knowledge/template/K-compose-template-preview-endpoint-split.md` —— 两个都叫 preview 的端点语义完全不同；`GET /{id}/preview` 不做变量渲染，只有 `POST /preview-draft` 接受专家上下文。本计划 I-1 的来源。
- **新增** `docs/knowledge/frontend/K-compose-templates-state-scope.md` —— `state.composeTemplates` 是视图局部缓存；`setView` 非 async 且内部 `refreshCurrentView()` 未 await，跨视图跳转不得依赖其副作用（附 `openContactInList` 的正确先例）。本计划 I-2、I-3 的来源。
- **新增** `docs/knowledge/frontend/K-expert-detail-two-panel-render-sites.md` —— 专家详情子标签一处定义两处渲染；漏改一处是**静默空白**（`activateDetailSubTab` 只做 `hidden` 切换，不抛错）；附 `grep -c 'data-panel=...'` 的验收断言写法。本计划 I-5、I-7 的来源。
- **bump** 已使用条目的 `hit_count` / `last_used`（2026-08-14）：K-preview-draft-raw-before-render(6)、K-preview-mirrors-pipeline(29)、K-frontend-cache-key-triad(4)、K-js-test-invocation-surface(2)、K-dom-stub-tests-hide-dangling-refs(2)、K-plan-quantified-claims-need-grep-receipts(1)、K-detail-es-backed-fields-need-authoritative-read(5)。

### 待执行 agent 在实施后补做

1. **更新** `docs/knowledge/mail/K-mail-body-display-sites.md` —— 把本计划新增的专家详情邮件预览正文（`class="pre"`，`app.js` 新面板）并入该条目的正文展示点全集，bump `last_used` / `hit_count`；同步更新 `CLAUDE.md`「团队沉淀知识」中该条的一行摘要（当前措辞列举了"专家详情、收发件箱、未匹配详情、自动回复预览"四处，需加上本处）。
2. 若实施过程中发现本计划三条新知识有出入（例如 `preview-draft` 的字段在实施时已变化），**就地更正**对应 K 条目并 bump `created`（视为复验）。
