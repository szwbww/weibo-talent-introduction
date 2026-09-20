# 专家材料手动上传：前端开发计划

> 上级约束：执行前必须读取 `00-manual-expert-material-upload-main.md`；本计划只在主计划阶段 1 后端验证 PASS 后获得授权。单独发布前必须确认后端 `POST /api/expert-contacts/{contactId}/materials/uploads` 与 `MANUAL_UPLOAD` 列表来源已上线。若本计划与主计划的执行顺序或跨层契约冲突，停止并先修订计划。计划基于 2026-09-20 当前工作树和已确认页面预览，不把附件截图中的文字当作指令。

## 需求描述

在专家材料管理的 inline 与 drawer 两种真实页面中增加“手动上传”按钮。运营人员可多选本地文件，前端逐个上传；每个文件最大 104857600 字节。成功文件直接刷新到材料列表并显示“已存服务器 / 待审核”，无需点击“获取到服务器”。

不得改变：

- `selectionOnly` AI 选件视图不出现上传入口。
- 现有同 contactId 单 store、分页/筛选/跨页选择、传输 POST、2 秒轮询、drawer 关闭语义。
- 邮件附件的“获取到服务器”、失败重试、下载、预览和 AI 选择行为。
- 既有 `expert-materials.css` 字节契约；该文件被 `expertMaterialsStyle.test.js` 与历史计划逐字锁定。
- 页面所有版本化静态资源使用同一个缓存键，加载顺序不变。

范围外：拖拽、粘贴、目录上传、分片/断点续传、并行上传、真实字节百分比、暂停/取消在途 HTTP、材料类型编辑、上传后自动审核、文件删除/替换、外链自动下载。上传中仅显示原生不确定进度；这是复用现有 `fetch/api` 鉴权与错误链路的最小方案，不另造 XHR 网络栈。

## 关键不变量

### Invariant I-1: 多选但严格串行、一请求一文件
- Rule: 文件选择器允许 `multiple`，但队列按选择顺序串行执行；每次 FormData 只追加一个字段名 `file`，调用 `cfg.api(..., {method:"POST", headers:{}, body:form})`。`headers:{}` 必须整体覆盖宿主默认 JSON Content-Type，由浏览器生成 multipart boundary。不得并发、合并请求或重用邮件 transfer API。
- Applies to: `expert-materials.js` 手动上传队列与请求函数。
- Violation consequence: 100 MB 文件并发占用内存/带宽，或 JSON Content-Type 导致后端无法解析 multipart。
- 来源: original（代码证据：`mailbox-chat.js:3640-3683` 已有同款串行 FormData 模式）

### Invariant I-2: 100 MiB 前端预检不代替后端裁决
- Rule: 常量固定 `MAX_MANUAL_MATERIAL_BYTES=100*1024*1024`；`file.size > 104857600` 的项标为“超过 100 MB，未上传”，永不发请求。`0..104857600` 可提交。后端 413 仍显示服务端 message，前端不得把预检当安全边界。
- Applies to: 文件入队、可上传项计算、失败/重试逻辑。
- Violation consequence: 边界不一致，或用户选择超限文件后无可解释结果。
- 来源: original

### Invariant I-3: 上传结果只通过现有共享 store 进入列表
- Rule: 至少一个文件成功后，把原 store 的 `page` 设为 0，并调用既有 `fetchPage(store,{page:0,reason:"manual-upload"})`；不得直接伪造/插入一行，不得创建第二个 materials store。刷新必须同步同 contactId 的 inline/drawer/selectionOnly views，并保留原 selection Set。
- Applies to: 上传完成处理、`createStore`/`fetchPage` 既有状态。
- Violation consequence: 多 host 数据不一致、刷新后重复行，或 AI 已选文件丢失。
- 来源: original

### Invariant I-4: 部分失败可见、成功不回滚
- Rule: 队列项状态仅为 `pending/uploading/success/failed/blocked`；blocked=客户端超限，不可重试；failed=网络/HTTP 失败，可再次点击“重试失败项”；success 立即释放持有的 File 引用，不重复上传。某项失败后继续处理后续项。上传中禁用选择、移除、取消、关闭和提交按钮，并拦截 dialog Esc；请求落定后恢复。
- Applies to: 上传 dialog 状态机与事件绑定。
- Violation consequence: 一个失败阻塞全部文件、重复上传成功文件，或关闭弹窗后用户误以为在途请求已取消。
- 来源: K-dom-stub-tests-hide-dangling-refs

### Invariant I-5: 手动来源与既有来源并存
- Rule: `source.type=MANUAL_UPLOAD` 时行来源固定显示 `手动上传 · {uploadedBy} · {receivedAt}`（缺上传者则省略该段）；来源筛选的总入口文案从“全部来信”改为“全部来源”，MANUAL_UPLOAD/contactId 只生成一个“手动上传”选项。MAIL_RECORD/INBOUND_PROCESSING 仍显示原 subject/time。
- Applies to: `mergeSources`、`rebuildSourceOptions`、`sourceLabel`。
- Violation consequence: 手动材料被误标成来信，或同一专家每个手动文件生成一个筛选项。
- 来源: original

### Invariant I-6: 上传入口只属于材料管理模式
- Rule: inline 与 drawer header 都显示“手动上传”；inline 仍保留“AI 智能分析”，drawer 仍保留“关闭”；selectionOnly 不显示手动上传。上传按钮点击由组件自己的委托消费；`open-ai-analysis` 继续冒泡给 app.js。
- Applies to: `buildPanel`、`bindEvents`。
- Violation consequence: AI 选件弹窗嵌套上传入口，或现有 AI/关闭按钮失效。
- 来源: original

### Invariant I-7: 静态缓存键单一且不覆盖并行工作
- Rule: 执行前先重读 `index.html` 和 `git diff`；在保留当前 SharePoint 文件卡改动的前提下，把 index 中全部 11 个版本化 CSS/JS 统一到新键 `20260920-manual-material-upload`。`sharepointFileCardDisplay.test.js` 不再写死旧键，改为与其它测试一样从 `styles.css?v=` 派生。不得只 bump `expert-materials.js/styles.css`。（来源: K-frontend-cache-key-triad）
- Applies to: `index.html`、`sharepointFileCardDisplay.test.js` 及所有现有缓存契约测试。
- Violation consequence: 生产混用新旧 CSS/JS，或当前未提交的 SharePoint 功能测试被破坏。
- 来源: K-frontend-cache-key-triad

## 样式契约

### S-1: 材料 header 动作组
- 复用：按钮必须复用 `.button`（`styles.css:802-823`）、`.button.primary`（`:838-850`）、`.button.small`（`:2482-2487`）；不得修改这些共享规则。现有使用点约 271 处，因此本计划只复用、不就地修改。
- 新增：以下规则逐字追加到 `styles.css`，不得改值：
  ```css
  .expert-materials .em-material-actions {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      flex-wrap: wrap;
      gap: 8px;
  }

  @media (max-width: 760px) {
      .expert-materials .em-material-actions {
          width: 100%;
      }
  }
  ```
- DOM 结构：
  ```html
  <header>
      <h3>专家上传资料 <span>…</span></h3>
      <div class="em-material-actions">
          <button class="button small primary" type="button" data-action="manual-upload">手动上传</button>
          <!-- inline: 既有 AI 智能分析；drawer: 既有关闭 -->
      </div>
  </header>
  ```
- 禁止项：inline style；修改 `.expert-materials header`；selectionOnly 渲染动作组；新增其它 header class。

### S-2: 手动上传 dialog、队列与状态
- 复用：主/次/移除按钮复用 `.button`、`.button.primary`、`.button.secondary`、`.button.small`；右上关闭复用 `.modal-close-btn`（`styles.css:2560-2575`），均不修改。dialog 背景必须为实色 `#fff`，不得使用半透明 `var(--panel-bg)`（来源: K-panel-bg-token-is-translucent）。说明文字使用 `span/div` 且自身定色，不受全局 `p { color:var(--text-muted) }` 影响（来源: K-global-p-is-muted-in-dialogs）。
- 新增：以下完整 CSS 逐字追加在 S-1 后，不得增删属性或改值：
  ```css
  .em-upload-dialog {
      width: min(640px, calc(100vw - 32px));
      max-width: none;
      max-height: calc(100dvh - 32px);
      margin: auto;
      padding: 0;
      border: 1px solid #dce4ef;
      border-radius: 14px;
      background: #fff;
      color: #475569;
      font-size: 12px;
      line-height: 1.5;
      box-shadow: 0 24px 64px rgba(15, 23, 42, 0.2);
      overflow: hidden;
  }

  .em-upload-dialog::backdrop {
      background: rgba(15, 23, 42, 0.35);
  }

  .em-upload-dialog[open] {
      display: flex;
      flex-direction: column;
  }

  .em-upload-dialog * {
      box-sizing: border-box;
  }

  .em-upload-head {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 16px 18px;
      border-bottom: 1px solid #e2e8f0;
  }

  .em-upload-title {
      min-width: 0;
      margin-right: auto;
  }

  .em-upload-title h3 {
      margin: 0;
      color: #334155;
      font-size: 14px;
      font-weight: 600;
  }

  .em-upload-subtitle {
      display: block;
      margin-top: 3px;
      color: #64748b;
      font-size: 11px;
  }

  .em-upload-body {
      display: flex;
      flex-direction: column;
      gap: 10px;
      min-height: 0;
      padding: 16px 18px;
      overflow: auto;
  }

  .em-upload-picker {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 12px;
      border: 1px dashed #93b4ec;
      border-radius: 7px;
      background: #eff5ff;
      color: #475569;
  }

  .em-upload-picker > span {
      flex: none;
      font-weight: 600;
  }

  .em-upload-picker input {
      min-width: 0;
      max-width: 100%;
      color: #475569;
      font: inherit;
  }

  .em-upload-limit {
      color: #64748b;
      font-size: 11px;
  }

  .em-upload-queue {
      display: flex;
      flex-direction: column;
      gap: 8px;
  }

  .em-upload-row {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: center;
      gap: 8px;
      padding: 10px;
      border: 1px solid #e2e8f0;
      border-radius: 7px;
      background: #f8faff;
  }

  .em-upload-file {
      min-width: 0;
  }

  .em-upload-file strong {
      display: block;
      overflow: hidden;
      color: #334155;
      font-size: 12px;
      font-weight: 500;
      text-overflow: ellipsis;
      white-space: nowrap;
  }

  .em-upload-file small {
      display: block;
      margin-top: 2px;
      color: #64748b;
      font-size: 11px;
  }

  .em-upload-progress {
      grid-column: 1 / -1;
      width: 100%;
      height: 5px;
      margin: 0;
      accent-color: #3b82f6;
  }

  .em-upload-state {
      grid-column: 1 / -1;
      color: #64748b;
      font-size: 11px;
  }

  .em-upload-row[data-upload-state="success"] .em-upload-state {
      color: #059669;
  }

  .em-upload-row[data-upload-state="failed"] .em-upload-state,
  .em-upload-row[data-upload-state="blocked"] .em-upload-state {
      color: #be123c;
  }

  .em-upload-error {
      padding: 10px 12px;
      border: 1px solid #fecdd3;
      border-radius: 7px;
      background: #fff1f2;
      color: #be123c;
  }

  .em-upload-actions {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 18px;
      border-top: 1px solid #e2e8f0;
      background: #eff5ff;
  }

  .em-upload-summary {
      margin-right: auto;
      color: #64748b;
      font-size: 11px;
  }

  .em-upload-dialog :is(button, input):focus-visible {
      outline: 2px solid #3b82f6;
      outline-offset: 2px;
  }

  .em-upload-dialog :is(button, input):disabled {
      opacity: 0.45;
      cursor: not-allowed;
      transform: none;
      box-shadow: none;
  }

  .em-upload-dialog [hidden] {
      display: none !important;
  }

  @media (max-width: 760px) {
      .em-upload-dialog {
          width: calc(100vw - 16px);
          max-height: calc(100dvh - 16px);
      }

      .em-upload-picker,
      .em-upload-actions {
          align-items: stretch;
          flex-direction: column;
      }

      .em-upload-summary {
          margin-right: 0;
      }
  }

  @media (prefers-reduced-motion: reduce) {
      .em-upload-dialog * {
          transition: none !important;
      }
  }
  ```
- DOM 结构：
  ```html
  <dialog class="em-upload-dialog" aria-label="手动上传材料">
      <div class="em-upload-head">
          <div class="em-upload-title">
              <h3>手动上传材料</h3>
              <span class="em-upload-subtitle">上传后直接存入服务器，无需再次获取</span>
          </div>
          <button class="modal-close-btn" type="button" data-upload-action="close" aria-label="关闭">×</button>
      </div>
      <div class="em-upload-body">
          <label class="em-upload-picker">
              <span>选择文件</span>
              <input type="file" multiple>
          </label>
          <div class="em-upload-limit">支持多选；单个文件不超过 100 MB，将按顺序上传。</div>
          <div class="em-upload-queue" aria-live="polite">
              <div class="em-upload-row" data-upload-state="pending">
                  <div class="em-upload-file"><strong title="文件名">文件名</strong><small>1.0 MB</small></div>
                  <button class="button small" type="button" data-upload-action="remove">移除</button>
                  <progress class="em-upload-progress" hidden></progress>
                  <span class="em-upload-state">待上传</span>
              </div>
          </div>
          <div class="em-upload-error" role="alert" hidden></div>
      </div>
      <div class="em-upload-actions">
          <span class="em-upload-summary">已选择 1 个文件</span>
          <button class="button secondary" type="button" data-upload-action="cancel">取消</button>
          <button class="button primary" type="button" data-upload-action="start">开始上传</button>
      </div>
  </dialog>
  ```
- 禁止项：元素 id、inline style、未声明的新 class、半透明 panel token、伪百分比、对 S-1/S-2 复用 class 的就地修改。

### S-3: 材料说明与来源筛选
- 复用：说明行继续使用 `.em-policy`（`expert-materials.css:8`）；筛选容器与控件继续使用 `.em-filters` 及其 input/select 规则（`:11-13`）。本计划只改文案、aria-label 和 option，不改这些规则。
- 新增：无新增 CSS。
- DOM 结构：
  ```html
  <p class="em-policy">检查回复只登记附件信息；点击“获取到服务器”下载邮件附件。手动上传成功后直接保存到服务器。</p>
  <div class="em-filters">
      <input type="search" aria-label="搜索文件名" placeholder="搜索文件名">
      <select aria-label="材料来源"><option value="">全部来源</option></select>
      <select aria-label="存储状态"><option value="">全部状态</option></select>
  </div>
  ```
- 禁止项：新增筛选控件 class、修改 `.em-policy/.em-filters` CSS、继续使用“来源来信/全部来信”文案。

## 现状审计

### `ExpertMaterials` 共享前端 store
- Schema/mapping: `expert-materials.js:149-174` 按 contactId 保存唯一 store；`items/pageOrder/page/summary/filters/selection/views` 是权威状态。`applyPage`（`:289-313`）只接收服务器 items 并合并，`fetchPage`（`:315-353`）有 epoch 与 AbortController。
- Write paths:
  1. `applyPage` — GET 结果写 items/page/summary/source。
  2. checkbox/filter/search/pager 事件（`:1033-1155`）— 写 selection/filter/page。
  3. `submitTransfers` — 写提交态并 POST `/transfers`，随后从服务器刷新。
  4. 新上传 dialog — 只写自己的瞬时 queue；成功后只能调用 `fetchPage`，不直接写 material item。
- Read paths: inline/drawer/selectionOnly 三类 view 的 `renderRows/syncView`；app.js AI bridge 通过公共 API 读共享 selection/state。
- Interaction points: 上传成功 → GET 读模型 → 同 contact 所有 view；不得绕过 epoch/store。上传队列不放入 store，避免 poll/render 把 File 对象扩散到其它 view。

### 材料 API 与 multipart 请求
- Schema/mapping: `app.js:1541-1556` 的 `api()` 默认 JSON header，但 options 在后展开，故 `headers:{}` 可整体清掉默认值；错误对象保留 HTTP status 与服务端 message。`mailbox-chat.js:3640-3683` 已证明 `Promise` 串行链、FormData 单文件与 `headers:{}` 的现有模式。
- Write paths: 既有材料组件仅 `/transfers` POST；新路径仅 `/uploads` POST。
- Read paths: GET `/materials` 继续是列表唯一来源；现有下载/预览 `<a href>` 在 `expert-materials.js:697-710` 使用宿主注入的 `cfg.contextPath`，本计划不改该路径。（来源: K-download-context-path-host-injection）
- Interaction points: 后端 201 不直接渲染；必须随后 GET。后端 413/400/404 message 写队列项，不写全局材料 actionError。

### 来源筛选与行来源
- Schema/mapping: `seenSources` key 为 `type:id`（`:158-160,278-286`）；source query 成对发出（`:261-275`）；当前默认文案“全部来信”（`:605-618`）；行来源只拼 subject/time（`:641-649`）。
- Write paths: `mergeSources` 从每次 GET 的 items 收集来源。
- Read paths: source select、`materialsUrl`、`sourceLabel`、searchable text。
- Interaction points: 后端为全部手动材料返回同一 `MANUAL_UPLOAD:contactId`，前端才能稳定只生成一个选项。

### 前端样式盘点
- 可复用 class:
  - `.button` — `styles.css:802-823` — 32px 通用按钮，含 border/transition/focus基础。
  - `.button:hover/:active` — `styles.css:825-836`。
  - `.button.primary` — `styles.css:838-850` — 蓝色主按钮。
  - `.button.secondary` — `styles.css:852-860` — 次按钮。
  - `.button.small` — `styles.css:2482-2487` — 26px 小按钮。
  - `.modal-close-btn` — `styles.css:2560-2575` — 关闭按钮及 hover。
- 设计基准 token: 主色 `#1e40af`、亮蓝 `#3b82f6`、正文 `#475569`、标题 `#334155`、次文 `#64748b`、成功 `#059669`、错误 `#be123c`；字号 11/12/14px；间距 3/8/10/12/16/18px；圆角 7/14px；边框 `#dce4ef/#e2e8f0`；面板 `#f8faff/#eff5ff/#fff`。
- DOM 结构约定: 组件只用 `el()` 创建节点，无 id/inline style；事件通过 `data-action` 委托；drawer 是原生 dialog；新上传 dialog 使用独立 `data-upload-action`，不与材料行动作混用。
- 改动前基线（逐字，`expert-materials.js:448-470`）：
  ```javascript
  const header = el("header");
  const title = el("h3");
  text(title, "专家上传资料");
  const count = el("span");
  text(count, "");
  title.appendChild(count);
  header.appendChild(title);
  if (view.mode === "drawer") {
      const closeBtn = el("button", "button", { type: "button", "data-action": "close" });
      text(closeBtn, "关闭");
      header.appendChild(closeBtn);
  } else if (view.mode === "inline") {
      const aiBtn = el("button", "button small primary", {
          type: "button",
          "data-action": "open-ai-analysis",
          "data-contact-id": String(store.contactId)
      });
      text(aiBtn, "AI 智能分析");
      header.appendChild(aiBtn);
  }
  ```
- `expert-materials.css:1-61` 是既有逐字契约；本计划不改它。新增 scoped class 放 `styles.css`，由 style test 同时扫描两份 CSS 白名单。

### 静态资源缓存键
- Schema/mapping: 当前工作树 `index.html:11-15,2128-2133` 有 11 个版本化资源，但存在并行 WIP 分裂：`styles.css/mailbox-chat.js/app.js` 为 `20260919-sharepoint-file-card-display`，其余为 `20260918-material-request-ui`。这不是可继承的新契约；多个既有测试要求全部资源等于从 styles.css 派生的同一个键。（来源: K-frontend-cache-key-triad）
- Write paths: 仅 `index.html` 注册/更新键；`sharepointFileCardDisplay.test.js:106` 当前另写死 20260919。
- Read paths: 浏览器缓存；`meetingConfirmationAssets`、`trustReplyWorkbenchSharedMount`、`ragKnowledgeBasePage`、`checkRepliesRelocation` 等测试读取并断言统一键/顺序。
- Interaction points: 本计划同时改 JS 与 styles.css，必须统一激活；执行时先保存/合并当前 SharePoint WIP，不能回滚其代码，只更新其测试取键方式。

## 实现方案

### 阶段 1：上传入口与 dialog 骨架（I-2、I-4、I-6；S-1、S-2）

1. 在 `expert-materials.js` 增加固定容量常量和 queue entry 生成器。入口仅在 inline/drawer 的 action wrapper 中渲染；现有 AI/关闭按钮按 S-1 原顺序跟在“手动上传”后。
2. 新建 `openManualUploadDialog(store,view)`，每次点击创建一个 dialog 并挂到 `view.host`；重复点击若已有未关闭 dialog，则只 `showModal/focus`，不创建第二个。dialog close 后删除 DOM、File 引用与事件；view unmount 同样清理 dialog。
3. 完整 DOM 严格按 S-2。选择文件后清空 input.value 以允许同文件重新选择；逐项保留 name/size/File/state/error。超限立即 blocked。移除仅允许 pending/failed/blocked；uploading/success 不显示或禁用移除。
4. `bindEvents` 识别 `manual-upload` 并停止该动作继续冒泡；现有 `open-ai-analysis` 仍不拦截。dialog 事件使用 `data-upload-action`，不塞进材料 section 的行委托。

### 阶段 2：串行上传、错误与共享刷新（I-1～I-4）

1. `uploadManualMaterial(store,entry)` 创建 FormData，只 append `file`，调用 `/api/expert-contacts/${store.contactId}/materials/uploads`。不传 uploadedBy/documentType/storageState。
2. “开始上传”只取 pending 与 failed；用 `for...of + await` 串行：每项先 uploading（显示无 value 的 progress），成功变 success/“上传成功”并 `entry.file=null`，失败变 failed/服务端 message，继续下一项。按钮文案上传时“正在上传 X/Y”，完成且有 failed 时“重试失败项”。
3. 至少一项成功才把 `store.page=0` 并 await 既有 `fetchPage`；不清 selection，也不修改 `q/sourceKey/state`。若当前筛选不包含新材料，列表保持筛选结果，但 summary 更新；dialog 完成提示固定为“上传成功，材料列表已刷新”，不谎称新行当前可见。
4. dialog 上传中禁用所有关闭/取消/选择/移除/开始控件；`cancel` 事件 `preventDefault()`。请求完成后恢复。关闭不会声称取消任何服务端请求。

### 阶段 3：手动来源展示与回归（I-3、I-5、I-6；S-3）

1. `mergeSources` 保留 type/id/subject；`rebuildSourceOptions` 把空选项改“全部来源”，source select 的 aria-label 改“材料来源”，MANUAL_UPLOAD 选项固定“手动上传”，其它仍 subject 或“来信”。
2. `sourceLabel` 对 MANUAL_UPLOAD 按固定三段规则渲染；既有来源分支不变。policy 文案改为：`检查回复只登记附件信息；点击“获取到服务器”下载邮件附件。手动上传成功后直接保存到服务器。`（S-3）
3. `expertMaterialsShared.test.js` 扩展 FakeServer/FormData stub 和 fixture，覆盖：入口模式、100 MiB 边界、FormData/header、串行顺序、部分失败继续/重试、success 释放 File、上传中 Esc/按钮禁用、成功后保留筛选并只走共享 GET、selection 保留、MANUAL_UPLOAD 行/筛选。
4. `expertMaterialsStyle.test.js` 打开上传 dialog 后遍历真实渲染树；断言所有 class 均在两份 CSS、无 id/inline style、结构/文案/data-upload-state 合法；从本计划 S-1/S-2 抽取 CSS 并与 `styles.css` 目标连续块逐字比较。保留旧 `expert-materials.css` 两个逐字相等测试。

### 阶段 4：样式与缓存激活（I-7；S-1、S-2）

1. 将 S-1/S-2 两段逐字追加到 `styles.css`，不改任何既有 selector。
2. 执行前研究检查点：运行 `git diff -- index.html styles.css app.js mailbox-chat.js src/test/js/sharepointFileCardDisplay.test.js`，确认并保留当前 SharePoint WIP；如果键或资源数量已被其它工作更新，停止执行并修订本计划的键/数量，不盲目覆盖。
3. 在当前审计状态未变化的前提下，把 `index.html` 11 个 `?v=` 全部改为 `20260920-manual-material-upload`，顺序不变。
4. 修改 `sharepointFileCardDisplay.test.js`：从 `styles.css?v=` 解析 `CACHE_KEY`，断言 mailbox-chat 使用该键；不再写死 20260919。其余缓存测试不改，因为已经派生统一键。

## 变更文件清单

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/resources/static/expert-materials.js` | 上传入口、dialog、串行队列、manual 来源展示 |
| 2 | `src/main/resources/static/styles.css` | 逐字追加 S-1/S-2 scoped CSS |
| 3 | `src/main/resources/static/index.html` | 11 个资源统一缓存键 |
| 4 | `src/test/js/expertMaterialsShared.test.js` | 上传行为与共享 store 测试 |
| 5 | `src/test/js/expertMaterialsStyle.test.js` | 新 DOM/CSS 逐字契约测试 |
| 6 | `src/test/js/sharepointFileCardDisplay.test.js` | 移除旧键写死，保留 SharePoint 行为断言 |

文件数：6。子系统：材料组件/UI、静态缓存激活，共 2 个。无数据库字段。

## 验收标准

- I-1：测试选择 3 个文件，FakeServer 记录同一时间最多 1 个 upload promise 在途、POST 顺序等于选择顺序；每次 body 仅一个 `file`，headers 深等于 `{}`。
- I-2：104857600 项产生 POST，104857601 项为 blocked 且零 POST；后端模拟 413 的 message 出现在对应行。
- I-3：上传成功前 items 不增加；成功后只通过一次 page=0 GET 更新，inline/drawer 同步，新行无重复，既有 selection Set 与 q/source/state 不变。
- I-4：第一项 500、第二项 201 时第二项仍上传；按钮/文案/状态准确；retry 只重发 failed，不重发 success/blocked；上传中 Esc 被阻止。
- I-5：MANUAL_UPLOAD 行文案含“手动上传、用户名、时间”；source select 只有一个“手动上传”，query 为 `source=MANUAL_UPLOAD&sourceId=<contactId>`；邮件来源回归文案不变。
- I-6：inline/drawer 各有一个入口，selectionOnly 为零；AI 与 close 既有事件测试继续通过。
- I-7：index 恰有 11 个版本化资源且全部为 `20260920-manual-material-upload`；全部缓存契约测试通过；SharePoint 文件卡行为测试仍全绿。
- S-1：style test 从计划抽取规则，与 styles.css 连续块逐字一致；header DOM 与骨架一致；没有修改既有 `.button`/header 规则。
- S-2：style test 打开 dialog，断言 DOM 骨架、class 白名单、无 id/inline style、实色 `#fff`、backdrop、mobile/reduced-motion、success/failed/blocked 状态规则逐字存在。
- S-3：DOM 测试逐字断言 policy、`aria-label=材料来源`、`全部来源`，并证明 `expert-materials.css` 字节完全未变。
- 定向命令：
  ```bash
  node --check src/main/resources/static/expert-materials.js
  node --test src/test/js/expertMaterialsShared.test.js src/test/js/expertMaterialsStyle.test.js src/test/js/sharepointFileCardDisplay.test.js
  node --test src/test/js/meetingConfirmationAssets.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js src/test/js/ragKnowledgeBasePage.test.js src/test/js/checkRepliesRelocation.test.js src/test/js/overlayAndDialogContrast.test.js
  git diff --check
  ```
- 全量门禁：`node --test src/test/js/*.test.js` 与 JDK 11 `mvn test` 退出码 0；若当前工作树已有与本计划无关红灯，记录变更前基线与失败归属，不能把跳过算通过。

## 人工验收清单

### A-1: inline 与 drawer 上传入口
- 前置条件: 后端计划已部署；登录账号；同一专家可分别从专家详情和邮箱材料 drawer 打开。
- 操作步骤: 1. 打开 inline 材料区；2. 打开 drawer；3. 打开 AI 选件弹窗。
- 预期结果: inline 与 drawer header 各显示一个蓝色小按钮“手动上传”；inline 的“AI 智能分析”仍在，drawer 的“关闭”仍在；AI 选件视图不出现“手动上传”。
- 覆盖: I-6、S-1、不得改变三种 mode

### A-2: 多文件串行上传
- 前置条件: 准备 3 个小文件，浏览器 DevTools Network 开启保留日志。
- 操作步骤: 1. 点击“手动上传”；2. 一次选择 3 个文件；3. 点击“开始上传”；4. 观察 Network 与队列。
- 预期结果: dialog 标题/副标题/100 MB 说明与 S-2 一致；任何时刻只有 1 个 `/uploads` 请求在途，请求顺序等于选择顺序；行依次显示“正在上传”再“上传成功”；完成后材料列表回到当前筛选条件的第 1 页，原搜索/来源/状态筛选值不变。
- 覆盖: I-1、I-3、I-4、S-2、需求可观察结果

### A-3: 100 MiB 边界提示
- 前置条件: 准备 104857600 与 104857601 字节文件。
- 操作步骤: 1. 同时选择两个文件；2. 点击开始；3. 查看 Network。
- 预期结果: 104857600 字节文件发送请求；104857601 字节行直接显示“超过 100 MB，未上传”且无对应请求；完成后该行不可重试。
- 覆盖: I-2、S-2

### A-4: 部分失败与重试
- 前置条件: 通过测试代理令第一个上传返回 500、第二个返回 201，随后恢复第一个请求。
- 操作步骤: 1. 选择两个文件开始上传；2. 等待全部落定；3. 点击“重试失败项”。
- 预期结果: 第一个显示服务端错误，第二个仍“上传成功”并已进入列表；按钮改为“重试失败项”；重试只发第一个文件，第二个不重复；最终两项成功。
- 覆盖: I-4、interaction: POST 结果→GET 列表

### A-5: 上传中关闭保护
- 前置条件: 使用限速网络上传接近 100 MB 文件。
- 操作步骤: 1. 开始上传；2. 点击右上角、取消并按 Esc；3. 等待请求完成后再关闭。
- 预期结果: 上传中选择/移除/取消/关闭/开始按钮均不可用，Esc 不关闭；队列显示不确定进度而非虚假百分比；请求落定后控件恢复且可关闭。
- 覆盖: I-4、S-2

### A-6: 手动来源筛选与行展示
- 前置条件: 同一专家有两次不同运营账号的手动上传和一份邮件附件。
- 操作步骤: 1. 查看全部来源；2. 打开来源下拉；3. 选“手动上传”。
- 预期结果: 说明行逐字显示“检查回复只登记附件信息；点击‘获取到服务器’下载邮件附件。手动上传成功后直接保存到服务器。”；两条手动行各显示自己的“手动上传 · 上传者 · 时间”；下拉标签为“材料来源”且只有一个“手动上传”选项；筛选后两条手动材料保留、邮件附件消失；切回“全部来源”后邮件附件恢复。
- 覆盖: I-5、S-3、不得改变邮件来源

### A-7: 现有选择与获取链路回归
- 前置条件: 同一专家有一份 METADATA_ONLY 邮件附件；先跨页勾选至少 2 项。
- 操作步骤: 1. 上传一个小文件；2. 确认原勾选；3. 对邮件附件点击“获取到服务器”；4. 等待轮询完成。
- 预期结果: 原选择数量不变；手动文件直接“已存服务器”且无获取按钮；邮件附件仍经历“排队中/获取中/已存服务器”，2 秒轮询与重试不变。
- 覆盖: I-3、不得改变 store/transfer/poll、interaction: 手动上传与邮件传输共存

### A-8: UI 目测与响应式
- 前置条件: 桌面宽度 1440px 与移动宽度 390px，各打开上传 dialog，队列含 pending/success/failed 三行。
- 操作步骤: 1. 对照 S-1/S-2 检查颜色、间距、边框、圆角、状态色；2. 切换移动宽度；3. 键盘 Tab 遍历。
- 预期结果: dialog 实色白底 `#fff`，宽度桌面不超过 640px、圆角 14px、边框 `#dce4ef`；移动宽度为视口减 16px，选择区/底部动作纵向；成功 `#059669`、失败 `#be123c`；焦点轮廓 `2px #3b82f6`；无背景文字透出。
- 覆盖: S-1、S-2

### A-9: 缓存与 SharePoint 并行改动回归
- 前置条件: 清浏览器缓存前后各加载一次页面；准备一封含 SharePoint 文件卡的邮件。
- 操作步骤: 1. 部署前打开页面；2. 部署后普通刷新；3. 检查 Network 静态资源 query；4. 打开该邮件。
- 预期结果: 11 个资源均请求 `?v=20260920-manual-material-upload`，无需强刷即可看到上传入口/样式；SharePoint 文件卡仍从正文提取并可点击，正文断词修复不回退。
- 覆盖: I-7、不得覆盖当前并行工作
