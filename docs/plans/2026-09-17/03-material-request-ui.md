# 材料索取：操作栏五项标签与人工回复弹窗

依赖顺序：`01-material-request-cache-fixtures.md`、`02-material-request-status-api.md` 完成并通过后执行本计划。交互基准为本轮已确认的预览：操作栏仍有可点的材料标签；人工回复区「会议确认」旁有「材料索取」；弹窗选五项、看英文预览、确认后填入编辑器。

## 需求描述

- 可观察结果：专家联系操作栏显示五项材料标签，可直接切换待提供/已提供/暂不愿提供；人工回复点击「材料索取」后，从待提供项中勾选，按用户给出的五行英文模板实时预览并追加到当前富文本草稿。
- 必须保持：会议确认按钮/附件流程、已有正文/主题/QA/跟进锚点、人工最终发送、上传文件列表、旧 `${pendingExpertMaterials}` 模板变量和操作栏无编辑/保存按钮。
- 不做：自动识别上传文件、确认时自动改材料状态、自动发送邮件、改变旧 7 项历史状态或旧变量正文、编辑邮件模板。预览是新五项材料操作栏；旧 7 项继续供历史变量/RAG 使用（详见 02）。

## 关键不变量

### Invariant I-1：操作栏状态可信
- Rule：操作栏只从 `GET /material-requests` 读五项数组；PUT `/material-requests/{code}` 成功后按回包重渲染五项，失败保持旧 DOM；成功不弹提示。绝不能再把 `/materials` 的分页对象当数组。
- Applies to：`app.js` 的 `loadContactDetail`、`renderExpertMaterialRow`、`saveExpertMaterialStatus`。
- Violation consequence：生产再次出现接口 200 而整行标签消失。
- 来源：`app.js:8060-8063,8122`、`document/ExpertMaterialController.kt:27-45`。

### Invariant I-2：弹窗列表、状态和英文一致
- Rule：每次打开为当前 contact 新读五项；仅 `PENDING` 项默认勾选，`PROVIDED/DECLINED` 项展示但不可选；取消不改状态，确认不发状态 PUT。英文正文只取响应 `requestText`，固定引言 `To proceed, please provide the following supporting materials:`，其后 `<ul>` 五条按 API 目录顺序过滤，不编号。
- Applies to：`mailbox-chat.js` 的打开、勾选、预览和确认路径。
- Violation consequence：已提供/暂不愿提供项目被重复索取或中英文语义分叉。
- 来源：用户指定模板与已确认预览。

### Invariant I-3：只操作当前回复草稿
- Rule：打开时捕获会话 owner、contactId、targetKey、编辑器 revision；迟到 GET/确认时若任一身份或 revision 变化，禁止填入。确认只追加合法 DOM 节点到当前编辑器并走现有 `handleManualComposeInput`/`saveDraftFromInputs` 保存；不碰发送 API、主题、QA、会议快照或附件。没有勾选项时确认禁用。
- Applies to：`mailbox-chat.js` 异步加载/确认/关闭。
- Violation consequence：切专家后材料填到另一人的邮件、会议附件意外失效，或直接发送。
- 来源：K-mailbox-draft-cache-owner-capture；`mailbox-chat.js:3758-3830,4440-4485,4610-4622` 已复核。

### Invariant I-4：弹窗内容是文字
- Rule：API 的 label/requestText 只能用 `textContent`/文本节点渲染，绝不拼接未转义 HTML；填入的 `<p><ul><li>` 应可经现有会议草稿 sanitizer 往返，不新增专属 draft 字段。
- Applies to：弹窗预览、编辑器追加、重新挂载。
- Violation consequence：脚本注入或草稿刷新后内容丢失。
- 来源：`meeting-confirmation.js:81-180` 白名单允许 p/ul/li，`mailbox-chat.js:2770-2780` 恢复路径。

### Invariant I-5：发布资源同键
- Rule：`index.html` 现有 11 个 `?v=` 一次性换为同一个新键；不新增 `<script>`/`<link>`。执行前先重新读当前键，不能假定计划编写时的值仍有效。
- Applies to：`index.html`。
- Violation consequence：浏览器混用旧 `app.js` 与新 `mailbox-chat.js`。
- 来源：K-frontend-cache-key-triad，`index.html:11-15,2110-2115` 已复核。

## 样式契约

### S-1：专家操作栏标签
- 复用：`contact-head-status-row` (`styles.css:1520-1531`)、`contact-head-label` (`:1537-1545`)、`expert-material-tags` (`:10406-10412`)、`expert-material-tag` 及 `is-pending/is-provided/is-declined` (`:10414-10467`)、`dropdown-menu/dropdown-item` (`:488-525`)。这些 class 的规则块不修改；现有 `renderExpertMaterialRow` 的其他使用点：`app.js:8047` PUT 重渲染、`:8122` 初次渲染（`rg renderExpertMaterialRow` 已核）。
- 新增：无 CSS、无 class。
- DOM 骨架（保留既有层级，仅数组由五项接口提供）：
```html
<div class="contact-head-status-row" id="expertMaterialRow" data-contact-id="…">
  <span class="contact-head-label">材料</span>
  <div class="expert-material-tags" aria-label="专家材料状态">
    <span class="dropdown"><button class="expert-material-tag is-pending" data-material-action="toggle">…</button><div class="dropdown-menu" hidden>…</div></span>
  </div>
</div>
```
- 禁止项：正文出现在操作栏；新增编辑/保存按钮；内联样式；修改既有 class 规则。

### S-2：人工回复入口与弹窗
- 复用：现有 `.button`/`.button.primary` (`styles.css:802-850`)、`.mc-editor-tools`/`.mc-editor` (`mailbox-chat.css:63-64`)；两者规则不修改。`mailbox-chat.css` 受逐字测试锁定（来源：K-mailbox-chat-css-byte-contract）。
- 新增：以下规则块逐字放入 `styles.css` 中现有 `/* meeting-mail-07: outbound files */` 之前，不得追加到文件末尾；`mailboxOutboundAttachments.test.js:2190-2194` 要求末尾仍是原 S-2 块。
```css
/* Manual material request: mailbox composer only. */
.mail-chat .button.material-request-trigger{color:#1e40af;border-color:#b8cef5;background:#eef4ff;gap:7px;margin-left:3px}
.mail-chat .button.material-request-trigger:hover{background:#e5eeff;border-color:#93b4ec}
.mail-chat .button.material-request-trigger:active{background:#dbeafe}
.mail-chat .button.material-request-trigger:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
.material-request-dialog{margin:auto;inset:0;width:min(740px,calc(100vw - 40px));max-height:calc(100dvh - 40px);padding:0;border:1px solid #d5dfed;border-radius:14px;background:#fff;color:#334155;font-family:var(--font-body);font-size:12px;line-height:1.6;box-shadow:0 24px 100px #172c473d;overflow:auto;overscroll-behavior:contain}
.material-request-dialog::backdrop{background:#182a464f;backdrop-filter:blur(2px)}
.material-request-dialog *{box-sizing:border-box}
.material-request-head{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:18px 22px;border-bottom:1px solid #e2e8f0;background:#fff}
.material-request-head h2{margin:0;font-size:18px;font-weight:600;color:#334155;line-height:1.4}
.material-request-close{display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;padding:0;border:0;border-radius:5px;background:transparent;color:#91a1b7;font:inherit;font-size:21px;cursor:pointer}
.material-request-close:hover{background:#edf3ff;color:#2451b9}
.material-request-close:active{background:#dbeafe}
.material-request-body{padding:20px 22px}
.material-request-heading{display:flex;align-items:center;justify-content:space-between;gap:8px;margin:0 0 12px;color:#52647e;font-weight:600}
.material-request-options{display:flex;flex-wrap:wrap;gap:9px;margin:0 0 22px}
.material-request-option{display:inline-flex;align-items:center;gap:7px;padding:9px 12px;border:1px solid #d8e1ee;border-radius:8px;background:#fff;color:#334155;cursor:pointer}
.material-request-option:hover{border-color:#93b4ec}
.material-request-option:active{background:#eaf1ff}
.material-request-option:has(input:checked){border-color:#3b82f6;background:#eff5ff;color:#1e40af}
.material-request-option:has(input:disabled){background:#f1f5f9;color:#94a3b8;cursor:not-allowed}
.material-request-option input{margin:0;accent-color:#1e40af}
.material-request-option small{font-size:11px;color:inherit}
.material-request-paper{padding:18px;border:1px solid #dce4ef;border-radius:9px;background:#f8faff;color:#334155;font-size:13px;line-height:1.85;overflow-wrap:anywhere}
.material-request-paper p{margin:0}
.material-request-paper ul{margin:12px 0 0;padding-left:24px}
.material-request-paper li+li{margin-top:10px}
.material-request-error{margin:0 0 12px;padding:9px 11px;border:1px solid #fecdd3;border-radius:7px;background:#fff1f2;color:#be123c}
.material-request-actions{display:flex;justify-content:flex-end;gap:9px;padding:16px 22px;border-top:1px solid #e2e8f0;background:#fff}
.material-request-dialog :is(button,input):focus-visible,.mail-chat .material-request-trigger:focus-visible{outline:2px solid #82a8e8;outline-offset:2px}
.material-request-dialog .button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
@media(max-width:760px){.material-request-dialog{width:calc(100vw - 20px);max-height:calc(100dvh - 20px)}.material-request-head,.material-request-body,.material-request-actions{padding:14px}}
@media(prefers-reduced-motion:reduce){.material-request-dialog *,.mail-chat .material-request-trigger{transition:none!important}}
```
- DOM 骨架（同一 `mailbox-chat.js` 会话宿主）：
```html
<div class="mc-editor-tools">
  <!-- 既有富文本按钮及上传附件按钮 -->
  <!-- 既有会议按钮仍由 meetingTriggerHtml() 生成 -->
  <button class="button material-request-trigger" data-action="mc-open-material-request">材料索取</button>
  <!-- 既有跟进按钮 -->
</div>
<dialog class="material-request-dialog" aria-labelledby="materialRequestTitle">
  <header class="material-request-head"><h2 id="materialRequestTitle">选择需要提供的材料</h2><button class="material-request-close" data-action="mc-close-material-request">×</button></header>
  <div class="material-request-body">
    <p class="material-request-error" role="alert" hidden></p>
    <p class="material-request-heading">材料 <span data-role="material-request-count">已选 3 项</span></p>
    <div class="material-request-options"><label class="material-request-option"><input type="checkbox"><span>代表性论文</span><small>已提供</small></label></div>
    <p class="material-request-heading">正文预览</p>
    <div class="material-request-paper" aria-live="polite"><p>To proceed, please provide the following supporting materials:</p><ul><li>Supporting documents for research projects</li></ul></div>
  </div>
  <footer class="material-request-actions"><button class="button" data-action="mc-close-material-request">取消</button><button class="button primary" data-action="mc-apply-material-request">确认并填入回复</button></footer>
</dialog>
```
- 禁止项：`mailbox-chat.css` 改动、inline style、未在本合同声明的新增 class。入口复用 `.button`；弹窗选择项和纸张使用上面完整规则。

## 现状审计

### `expert_material_status` 与新状态 API
- Schema/mapping：V111 的旧代码 CHECK；计划 02 的 V128 增加五个独立 `REQ_*` 代码并提供新 GET/PUT。UI 不直接读写 MySQL。
- Write paths：旧 `ExpertMaterialService.updateStatus` 保存旧 7；计划 02 的 `updateRequestStatus` 保存新 5；UI 的唯一新状态写是 `app.js:saveExpertMaterialStatus` 改调新 PUT。弹窗确认零状态写。
- Read paths：`app.js:loadContactDetail` 原来误读 `/materials`；本计划改新 GET。`mailbox-chat.js` 弹窗每次打开也读新 GET；`ExpertMaterialService.renderPendingMaterials`/`RagProcessContextResolver` 只读旧代码，不受 UI 影响。
- Interaction points：操作栏 PUT → 再次打开弹窗 GET 应反映最新状态；状态 GET → 弹窗过滤 → 富文本草稿 → 既有发送入口。来源：计划 02 的 I-1/I-4。

### 会话内人工草稿缓存
- Mapping：`mailbox-chat.js:85-92,550-590` 的 sessionStore 按 user/accountScope/contactId，内部 drafts Map 按 targetKey；不是 DB 或浏览器持久存储（来源：K-mailbox-draft-cache-owner-capture）。
- Write paths（`rg -n 'setDraft\(|deleteDraft\(|draftsMap\.(set|delete)|drafts\.(set|delete)|rec\.drafts =' src/main/resources/static/mailbox-chat.js` 复核）：① `:407,419,2444` 建立/恢复会话 Map；② `:585-593` 统一 `setDraft/deleteDraft`；③ `:3791-3850 saveDraftFromInputs` 记录主题/HTML/纯文本/QA/会议/锚点，`:3556,4157,4448,4593,4621,4671` 等调用此入口；④ `:3883` 程序性写 requestId，`:4510` 写会议，`:4596` 移除会议，`:4979-4980` 迁移目标键；⑤ `:3529,3541` 捕获的 Map 写附件状态，`:4832,4845,4855` 发送成功后删/更新捕获的草稿。新填入只调用③，不新增另一个 Map 写口。
- Read paths：`mailbox-chat.js:2732-2745` 用当前草稿重建人工编辑区；`:2834-2887 manualComposeHtml` 放会议按钮、正文与发送按钮；`:3758-3790 readManualValues` 读取编辑器；`:4700-4830 sendManualReply` 发送最终草稿。
- Interaction points：弹窗追加 DOM → `handleManualComposeInput` 保存 → 切换/返回同一回复目标后读回；会议块存在时追加材料不得修改会议块；迟到 GET 不得写别人的草稿。

### 前端样式盘点
- 设计基准：`styles.css:1-37` 主色 `#1e40af`、hover `#1e3a8a`、背景 `#f5f7fb`、正文 `#1e293b`、弱字 `#94a3b8`；`meeting-confirmation.css:2-7,11-15,35-37` 蓝色入口、白底 14px 圆角/阴影、19px 标题、13px 预览、backdrop `#182a464f`。本计划 S-2 给出适配 740px 弹窗的逐字实值。
- 可复用 class 的实码基线（`styles.css:10406-10467`、`mailbox-chat.css:63-64`；下列规则均不改）：
```css
.expert-material-tags {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    min-width: 0;
}

.expert-material-tag {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    height: 26px;
    padding: 0 10px;
    border: 1px solid var(--border);
    border-radius: 999px;
    background: var(--bg-main);
    color: var(--text-secondary);
    font-family: var(--font-body);
    font-size: 11px;
    font-weight: 600;
    line-height: 1;
    white-space: nowrap;
    cursor: pointer;
    transition: var(--transition);
}

.expert-material-tag:hover {
    border-color: var(--primary);
    color: var(--primary);
}

.expert-material-tag:focus-visible {
    border-color: var(--primary);
    outline: none;
    box-shadow: 0 0 0 2px rgba(var(--primary-rgb), 0.18);
}

.expert-material-tag.is-provided {
    border-color: var(--success-border);
    background: var(--success-bg);
    color: var(--success);
}

.expert-material-tag.is-declined {
    border-color: rgba(148, 163, 184, 0.35);
    background: rgba(148, 163, 184, 0.12);
    color: var(--text-muted);
    text-decoration: line-through;
    opacity: 0.72;
}
.contact-head-status-row,
.contact-head-mail-row {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
    flex-wrap: wrap;
}

.contact-head-status-row {
    flex: 1 1 auto;
}

.contact-head-label {
    font-size: 11px;
    font-weight: 600;
    color: var(--text-muted);
    width: 85px;
    min-width: 85px;
    white-space: nowrap;
    text-transform: uppercase;
    letter-spacing: 0.3px;
}
.mail-chat .mc-editor-tools{display:flex;flex-wrap:wrap;gap:6px}
.mail-chat .mc-editor{min-height:160px;max-height:360px;overflow:auto;padding:12px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#334155;font-size:12px;line-height:1.8;overflow-wrap:anywhere}
```
- 改动前 DOM 基线：`app.js:8001-8008` 原片段如下；`:8055-8063` 读 `/materials` 并在 `Array.isArray` 时渲染。`mailbox-chat.js:2863-2878` 原片段如下；入口只能放在既有会议和跟进按钮之间。
```js
return `
    <div class="contact-head-status-row" id="expertMaterialRow" data-contact-id="${escapeHtml(contactId)}">
        <span class="contact-head-label">材料</span>
        <div class="expert-material-tags" aria-label="专家材料状态">
            ${tags}
        </div>
    </div>
`;
```
```html
<div class="mc-editor-tools">
    <button class="button" type="button" data-action="mc-rich-command" data-command="bold">B</button>
    <button class="button" type="button" data-action="mc-rich-command" data-command="italic">I</button>
    <button class="button" type="button" data-action="mc-rich-command" data-command="insertUnorderedList">列表</button>
    <button class="button" type="button" data-action="mc-rich-command" data-command="createLink">链接</button>
    <button class="button outbound-upload" type="button" data-action="mc-upload-attachment" title="上传附件" aria-label="上传附件"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l10.6-10.6a4 4 0 0 1 5.66 5.66L9.41 17.41a2 2 0 0 1-2.83-2.83l9.19-9.19"/></svg></button>
    <input type="file" data-role="outbound-file-input" multiple hidden>
    ${meetingTrigger}${followUpButton}
</div>
```
- 既有 class 使用全集：`renderExpertMaterialRow` 只在 `app.js:8049,8124` 使用；`.mc-editor-tools` 只在 `mailbox-chat.js:2868` 生成且由 `mailbox-chat.css:63` 定义；`.meeting-trigger` 由 `meetingTriggerHtml` 产生，样式在 `meeting-confirmation.css:2-4`。本计划不改三者规则，新增独立 `material-request-*` 派生类。
- 资产契约：`mailboxChatStyle.test.js:116-133` 会检查 `mailbox-chat.js` 中所有字面 class 必须在 `styles.css`（或既有 `mc-*` 在锁定的 CSS）声明，且禁止 `style=`；`mailboxOutboundAttachments.test.js:2190-2194` 锁定 `styles.css` 文件末尾。来源：K-mailbox-chat-css-byte-contract。
- 知识取舍：K-manual-rich-final-body-single-seam 指出最终发送正文在 `PendingMailOperationService.executeManualRichSend` 汇合，本计划只改其上游草稿填入，不碰最终正文/指纹。K-meeting-confirmation-generic-template-boundary 与 K-calendar-not-expert-material-owner 已读，但本计划不改会议模板或日历附件 owner；K-expert-tag-editor-shared-render-contract 已读，但本计划的材料标签不同于 `renderExpertTagEditor`，不触碰其逐字契约。K-phase0-load-by-severity-not-filename 用于本轮知识筛选，不作为产品约束。

## 实现方案

### T1：操作栏复活（I-1、I-5、S-1）
- 文件：`src/main/resources/static/app.js`、`src/test/js/contactHeadLayout.test.js`。
- `loadContactDetail` 改调新 GET；`saveExpertMaterialStatus` 改调新 PUT；沿用现有三态按钮和静默重渲染。测试从旧 7 项改为新 5 项，断言接口 URL、顺序、状态、失败保留旧 DOM、页面只显示中文短标签。
- 新 GET 读取计划 02 的新五项 PUT 写的行。旧 7 项变量/上传文件 API 保持原读取路径。

### T2：材料索取弹窗（I-2 至 I-4、S-2）
- 文件：`src/main/resources/static/mailbox-chat.js`、`src/main/resources/static/styles.css`、`src/test/js/materialRequestIntegration.test.js`。
- 仅在现有 inbound 人工编辑区、沿用 `manualComposeHtml` 的 `ui = meetingEnabled() && !isOutbound` 条件，把入口放在会议按钮后、跟进按钮前；这是当前富文本 HTML 恢复会调用会议 sanitizer 的同一分支（`:2845-2850`）。用现有 portal 的原生 `<dialog>` 挂载，打开时 GET 新五项数据；只勾待提供，可取消/关闭；预览用响应文本构造 `<p>` 和 `<ul><li>`。GET 失败留在弹窗内显示错误并禁止确认，不填编辑器。
- 确认检查 owner/contact/target/revision，向当前 `.mc-editor` 末尾追加 DOM（空正文也可）；调用现有 `handleManualComposeInput(editor)` 保存。关闭弹窗并聚焦编辑器，不发成功 toast。重复打开可再选择，重复确认追加新段；用户可在富文本区手动删改。
- 文本只来自计划 02 的新 GET；草稿仍由 `saveDraftFromInputs` 写、由 `manualComposeHtml` 读，最终发送仍走现有人工发送路径。
- 新集成测试用真实 `mailbox-chat.js` 与 API stub 覆盖：待提供默认勾选、已提供/暂不愿提供不能勾、逐字英文和 bullet 顺序、空选禁确认、取消零写、确认零发送/零状态 PUT、已有正文/会议块保留、切目标和迟到 GET 不串写。

### T3：缓存版本与回归（I-5、S-1、S-2）
- 文件：`src/main/resources/static/index.html`。
- 在计划 01 完成后，将现有 11 个资源键一起 bump 为一个本次发布新值；不改变资源列表/顺序。执行前重新读实际键并按值 `rg -l`，若计划 01 的测试改造尚未落地不得执行 T3。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/resources/static/app.js` | 操作栏新 GET/PUT |
| 2 | `src/main/resources/static/mailbox-chat.js` | 入口、弹窗、预览、草稿填入 |
| 3 | `src/main/resources/static/styles.css` | S-2 逐字样式块 |
| 4 | `src/main/resources/static/index.html` | 11 个静态资源同键 bump |
| 5 | `src/test/js/contactHeadLayout.test.js` | 五项操作栏与接口回归 |
| 6 | `src/test/js/materialRequestIntegration.test.js` | 新交互集成用例 |

共 6 文件、1 个前端子系统。现有 `app.js`/`index.html`/`styles.css` 在编写计划时已有用户改动；执行仅基于当前内容增量编辑，不覆盖其它差异。

## 验收标准

- I-1：操作栏 GET/PUT URL 精确为新路由；API 返回五项时显示五个标签，返回错误时原有其它详情仍可用；保存失败不替换 DOM，保存成功无 toast。
- I-2：服务端五项顺序生成英文模板：固定引言、无编号项目符号、按勾选过滤；`PROVIDED/DECLINED` 禁选，确认零状态写。
- I-3：owner/target/revision 变化时确认不得追加；同目标追加后草稿重挂载仍保留原正文、会议块及新列表；空选确认禁用。
- I-4：XSS 测试用 API 返回 `<img onerror=...>` 文本，预览与编辑器显示字面文本且不生成可执行标签；重挂载后项目符号仍在。
- I-5：`index.html` 11 个 `?v=` 同一新键且不同于执行前键，名字/顺序未变。
- S-1：DOM 层级和 class 与 S-1 骨架一致；旧 `.expert-material-*` CSS diff 为空，操作栏无英文正文、无新增按钮。
- S-2：`styles.css` 包含 S-2 全部 CSS 块且逐字相同；新增 class 均在该块，`mailbox-chat.css` 无 diff，触发按钮位于会议与跟进之间，无内联样式；窄屏弹窗不溢出。
- 定向：`node --check src/main/resources/static/app.js`、`node --check src/main/resources/static/mailbox-chat.js`、`node --test src/test/js/contactHeadLayout.test.js src/test/js/materialRequestIntegration.test.js src/test/js/mailboxChatStyle.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/mailboxOutboundAttachments.test.js`；全量：`node --test src/test/js/*.test.js`；`git diff --check`。来源：K-js-test-invocation-surface，不以 `verify.sh` 代替。

## 人工验收清单

### A-1：操作栏状态
- 前置条件：计划 02 API 已部署；选择一位有专家联系记录、五项皆未设置的专家。
- 操作步骤：1. 打开该专家详情；2. 点击「专利」标签选「✓ 已提供」；3. 点击「荣誉奖项」标签选「⊘ 暂不愿提供」；4. 刷新详情。
- 预期结果：材料行依次显示代表性论文、科研项目、专利、荣誉奖项、学位；专利为绿色「已提供」、荣誉奖项为灰色删除线「暂不愿提供」，其余待提供；无需点保存，也无成功提示。
- 覆盖：I-1、S-1、需求结果 1；操作栏 PUT → GET。

### A-2：弹窗模板与填入
- 前置条件：A-1 的专家有一封真实来信，打开该来信的人工富文本编辑区；编辑器输入 `Dear Professor,`；专利/荣誉奖项状态沿用 A-1。
- 操作步骤：1. 点击「会议确认」旁的「材料索取」；2. 观察勾选和预览；3. 点击「确认并填入回复」。
- 预期结果：弹窗显示五项，专利与荣誉奖项禁选；默认选中代表性论文、科研项目、学位共 3 项。正文预览及富文本区追加 `To proceed, please provide the following supporting materials:`，下方 3 条英文项目符号依次为 `Copies of your representative publications`、`Supporting documents for research projects`、`Bachelor’s, master’s, and doctoral degree certificates`；原有 `Dear Professor,` 保留，邮件未发送，状态未改变。
- 覆盖：I-2、I-3、I-4、S-2、需求结果 2；状态 GET → 弹窗 → 草稿。

### A-3：取消和改写
- 前置条件：A-2 完成；再次打开材料索取弹窗。
- 操作步骤：1. 取消所有可选项；2. 观察确认按钮；3. 点击取消；4. 重开弹窗，恢复一项并确认；5. 在富文本里手动删除新增项目符号。
- 预期结果：空选时「确认并填入回复」禁用；取消后草稿与三态不变；再次确认只追加本次选择的单条；手动删除后发送按钮仍由人工控制。
- 覆盖：I-2、I-3、必须保持项。

### A-4：目标隔离和会议回归
- 前置条件：两个不同专家 A/B 各有真实来信；A 的人工回复已有会议确认正文及待发送 ICS。
- 操作步骤：1. A 打开材料索取，在接口响应前取消弹窗并切到 B；2. 检查 B 正文；3. 返回 A，重新打开并确认一项；4. 查看 A 的会议正文及 ICS 卡。
- 预期结果：B 未出现 A 的材料请求；A 的会议正文、时间与 ICS 卡仍在，材料段落追加其后；A 的发送仍由现有「发送人工回复」按钮触发。
- 覆盖：I-3、S-2、必须保持会议/发送；草稿写 → 重新读/会议跨路径。

### A-5：样式目测与上传材料回归
- 前置条件：登录后台，A-1 专家有上传文件或空上传列表。
- 操作步骤：1. 在桌面与约 375px 宽浏览器查看操作栏和弹窗；2. 展开原「专家上传资料」区域；3. 打开会议确认再取消。
- 预期结果：操作栏材料仍是 26px 高标签；弹窗白底、14px 圆角、蓝色选中项、底部确认按钮，窄屏无横向裁切；「专家上传资料」仍显示分页列表，会议确认仍可打开并取消。
- 覆盖：S-1、S-2、I-1、I-5、必须保持上传/会议。

人工验收开始时再从此节导出 `03-material-request-ui-acceptance.md`，现在不创建。
