# 人工回复：引用邮件模板

- 状态：待用户审阅；仅开发计划，未实施生产代码、未部署、未发信。
- 日期：2026-09-30。
- 代码基线：`a37efe970e4446242b121c5628db02daa631fc92`，以当前工作树实际文件为审计对象。工作树已有其他任务改动，实施不得覆盖。
- 视觉参考：[已演示的本地预览](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/mockups/template-reference-preview/index.html)、[弹框截图](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/mockups/template-reference-preview/preview.png)。预览使用示例数据；其中模板内容、HTML 粗体、固定账号不是生产数据或接口能力证明。
- 范围决策：先按截图中的**有真实来信的人工回复**拟定最小方案。无来信续信尚未获得明确扩展要求；其账号解析另有规则，不能猜测为联系人绑定账号。本计划不实现该分支。若用户选择同时支持，应先修订计划，不由执行者自行扩展。
- 规模：8 个实施文件，1 个前端子系统；不新增后端接口、DB/ES 字段、依赖包、通用弹框框架。

## 需求描述

### 可观察结果

- O-1：收发件箱有来信的人工回复工具栏增加「引用模板」。弹框展示当前回复对象、账号、可搜索的已启用模板列表及真实变量渲染预览。
- O-2：点击「填入回复」后，模板正文进入当前富文本编辑器，可继续编辑。默认保留回复主题；已有正文默认追加，可显式选择替换。只有「发送人工回复」才进入原有发送流程。

### 必须保持的行为

- N-1：取消、关闭、Esc、模板加载/预览失败不改正文、主题、草稿证据、线程锚点、附件；不保存模板、不发邮件、不改变专家或材料状态。
- N-2：原会议确认、材料索取、跟进邮件、工具栏格式操作继续可用；其相对顺序不变。
- N-3：追加保留原有富文本和会议块；所有填入保留通用附件及既有跟进锚点。会议快照存在时禁止整篇替换，先通过原有「移除日历附件」操作处理。
- N-4：草稿仍按现有会话/目标隔离；切换专家再返回可恢复，遵守现有内存缓存边界，不承诺刷新浏览器后恢复。
- N-5：发送接口、账号选取、线程归属、安全确认、幂等、发送成功/失败后的草稿行为不改变；无来信续信和待匹配来信不增加入口。

### 范围外

无来信续信扩展；新增模板编辑器或模板分类；模板收藏/最近使用；光标位置插入；Markdown/HTML 模板解析；模板本身富文本化；新附件/日历生成；自动发送；模板使用统计；草稿持久化；修复既有无来信富文本恢复；QA/RAG 通用重构；后端门禁改造；部署。

已演示的左右布局和交互保留；正式正文遵循当前接口的**纯文本语义**，换行转换为安全编辑器节点，不把示例中的 `<strong>` 当作现有模板格式承诺。

## 关键不变量

### Invariant I-1: 入口与只读引用
- Rule：入口仅由 `manualComposeHtml(..., outbound=false)` 渲染，不依赖 `meetingEnabled()`。只有 `instance.manual.mode === "inbound"`、真实正整数 processingId/contactId、有效 targetKey 且非 busy 才能打开/应用。模板功能仅调用 `GET /api/compose-templates` 与 `POST /api/compose-templates/preview-draft`；预览 POST 是现有只读计算接口。
- Applies to：工具栏渲染、打开/应用事件、模板列表与预览请求。
- Violation consequence：无来信场景使用错误账号，或把引用动作变成发送/模板写入。
- 来源：original；K-manual-rich-reply-anchor-must-be-real。

### Invariant I-2: 真实上下文与后端渲染
- Rule：每次打开重读列表，只呈现 `enabled === true`，不按 mailType 额外猜测分类，不依赖 `app.js state.composeTemplates` 的预加载。POST 明确映射 `subject`、`subjectSnippetId`、四字段 blocks，携带实际 contactId、实际回复账号、`strictPlaceholders:false`、`variantIndex:0`；不传历史 subjectVariants。
- Rule：普通来信的账号取 `instance.manual.targetAccountCode`；已有跟进锚点时，从 `followupCandidates()` 中查找**相同 id** 的成功发件，取该项 accountCode。找不到时显示「无法确认当前跟进邮件的发件账号，请重新选择跟进邮件」，不退回联系人绑定账号或数组首项。
- Rule：不在前端替换变量、不重新拼模板块。预览成功后保存当前响应快照，填入只用该快照，不再调用预览或重新随机选择片段。选择下一模板即作废旧快照。
- Applies to：列表读取、上下文捕获、预览 payload、预览回包、填入。
- Violation consequence：先访问模板页才可用、引用片段失效、称呼/署名错误、预览与填入内容不同。
- 来源：K-compose-template-preview-endpoint-split、K-preview-account-resolution-order、K-preview-draft-raw-before-render、K-compose-templates-state-scope。

### Invariant I-3: 可填入与提示状态分开
- Rule：无选中模板、请求中、请求失败、空 body、无 toEmail、toEmail 与捕获 contact.expertEmail 不同、正文残留 `${`、存在预览专用退订值时，禁用「填入回复」。模板主题有残留 `${` 时，只有勾选「同时替换回复主题」才阻止填入；主题不替换时不因其内容拦住正文。主题渲染接口本身报错仍按预览失败处理。
- Rule：预览专用退订值依据 `MailVariableService.kt:315` 的精确值 `https://example.com/u/unsubscribe?token=preview`，检查本次要应用的 body，以及勾选替换主题时的 subject；命中即显示「退订链接尚未配置，当前仅为示例链接，请先配置后重试」。variables 合并了主题与正文诊断，不能仅因未使用主题中的示例值阻止正文填入。不把样例数据拷入可发送草稿。
- Rule：`fallbackKeys` 和 `variables.usedFallback` 表示使用默认值，不等同错误。显示「使用默认值：…」。`variables.filled=false && usedFallback=false` 显示「缺少变量值：…；填入后请补齐」。这些是人工编辑提示，不新建发送门禁；不得把部分变量缺失宣称为全部已代入。
- Rule：`blocks.included=false` 展示各块名称/序号和 skipReason，不静默掩盖被略过的正文块。非空剩余正文可引用，由人预览确认。
- Applies to：状态渲染、应用按钮可用性、应用前二次检查。
- Violation consequence：原始占位符、示例链接被带入邮件，或误把正常默认值当作系统故障。
- 来源：original；K-compose-template-preview-endpoint-split、K-preview-result-not-send-ready（本轮审计沉淀）。提示不宣称等价于批量发送的个性化门禁。

### Invariant I-4: 文本安全与富文本保存
- Rule：API body 是纯文本。预览用 `textContent`，配合 `white-space:pre-wrap`。写编辑器时先调用现有 `normalizeManualTextLineBreaks`，逐行创建 text node 与 `<br>`，可包在无 class 的 `<div>`；不得把 body 直接赋给 innerHTML，不解析 HTML/Markdown，不创建自动链接。
- Rule：追加只向编辑器末尾追加新节点，原节点不重建；替换只清空正文子节点，再放入上述安全节点。模板名称、描述、主题、警告、上下文也用 textContent 或 escapeText。
- Rule：一次应用同步修改 DOM，随后调用 `handleManualComposeInput(editor)` 和 `saveConversationState()`。复用现有 html/text 双表示、换行规范化、sanitizer 恢复流程；不直接另建草稿 Map 或将正文存入 localStorage。
- Applies to：预览、应用、草稿保存/恢复。
- Violation consequence：脚本/HTML 执行、既有会议块损坏、页面看到了正文但切换后草稿丢失。
- 来源：K-compose-template-html-after-render、K-mailbox-draft-cache-owner-capture。

### Invariant I-5: 用户选择与草稿附属信息
- Rule：默认不替换主题；勾选才使用预览 subject，原样采用，不额外添加 Re:。打开时正文空则填入；非空默认追加，只有显式选中「替换正文」才替换。程序性主题变化须经过同一次草稿保存。
- Rule：追加保留 `instance.manual.qa`；替换正文清除 `instance.manual.qa`，由保存入口同步清 draft.qa。不得从模板 blocks.refId 推导 qaRuleIds/ragFactCodes，不伪装成可信工作台证据。
- Rule：两种模式都保留当前 `outboundAttachmentDraft` 和 `followUpAnchorMailRecordId`。会议快照（ready 或 stale）存在时禁用「替换正文」，文案「含日历附件，仅支持追加；如需替换，请先移除日历附件」。应用时重查，不能只依赖 radio.disabled。
- Rule：追加不得改变原会议块与 meeting 快照状态；不得绕过 `detectMeetingChange` 或发送侧附件核对。requestId 的失效只沿用 `saveDraftFromInputs`，取消不主动生成/失效 requestId。
- Applies to：应用、handleManualComposeInput、saveDraftFromInputs 既有读写协议。
- Violation consequence：正文被静默覆盖、旧 RAG 证据错误附着新正文、附件丢失、旧幂等请求用于新正文。
- 来源：K-rich-reply-qa-audit-reuse（仅沿用“证据不可伪造”原则；当前前端实际字段是 RAG，见审计）、K-manual-rich-final-body-single-seam。

### Invariant I-6: 异步响应与草稿身份
- Rule：新增 `instance.templateReference` 仅为单次弹框的临时状态，不是持久共享 store。包含 open、单调递增 seq、identity、items、selectedId、preview、loading/error、trigger；不添加 draft 字段。
- Rule：打开时捕获 ownerKey（user/accountScope/contactId）、contactId、contact.expertEmail、contact.expertName、targetKey、convEpoch、当前跟进锚点、实际账号、editorRevision，以及 subject/html 快照。contact 来自 instance.conversation.contact；姓名展示可沿用 selectedSummary.name，邮箱核对使用 contact.expertEmail。每次加载列表、选模板/重试请求、关闭均递增 seq。
- Rule：异步回包只有 open、seq、owner/target/epoch 仍匹配才允许更新本弹框。应用前还须比较正文/主题快照和当前忙状态；变化时禁用应用并显示「回复目标或草稿已变化，请关闭后重新选择模板」。不重新绑定新草稿，也不写旧草稿。
- Rule：晚到附件上传只更新附件状态时，不误判正文冲突；应用保存必须读取当前 draft 保留刚回来的附件，禁止用打开时整份 draft 覆盖。
- Applies to：模板请求、模板选择、应用、关闭、会话切换/卸载。
- Violation consequence：A 模板响应覆盖 B 预览、切专家后填错人、异步上传的附件被旧快照覆盖。
- 来源：K-mailbox-draft-cache-owner-capture。

### Invariant I-7: 弹框所有权与生命周期
- Rule：复用现有 body portal，放入原生 dialog 并 showModal，不嵌入带 backdrop-filter 的 panel。所有新事件采用现有 host/portal 委托。原生 cancel 事件也调用同一关闭函数。
- Rule：关闭函数仅移除自己拥有的 `.reply-template-dialog`，清临时状态并使 seq 失效；不得在未持有本弹框时无条件 `portalRoot.innerHTML=""`，以免删掉后来打开的材料/管理/跟进面板。
- Rule：打开管理/材料/跟进、切专家、切账号、采用新回复目标、unmount 时关闭引用弹框。取消/关闭/Esc 恢复仍在 DOM 中的触发按钮焦点，应用后聚焦正文；切上下文不恢复旧焦点。
- Applies to：打开/关闭、portal 事件注册、既有生命周期挂接点。
- Violation consequence：模态框错位、遮挡残留、事件泄漏、旧关闭回调删掉新面板。
- 来源：K-mailbox-popover-scope-and-fixed-containing-block。

### Invariant I-8: 发布与范围边界
- Rule：只追加本计划声明的 styles.css 样式；不修改 mailbox-chat.css 及其字节基线。index.html 当前 11 个版本化资源统一改为 `20260930-manual-template-reference`，不新增资源文件注册。
- Rule：实现文件仅限变更清单；无后端、DB、ES、发送 adapter 修改。当前未收到续信范围扩展答复，不推断其账号、不实现其格式修复。
- Applies to：静态样式、index 资源引用、测试。
- Violation consequence：缓存旧脚本、新旧 UI 混载或把局部需求扩成邮件管道改造。
- 来源：K-mailbox-chat-css-byte-contract、K-frontend-cache-key-triad。

## 样式契约

样式与 DOM 的唯一实现依据是以下合同。预览文件不直接作为生产依赖，不复制 preview.js 的示例数据、全局变量、MutationObserver 或测试用禁发按钮。

### S-1: 工具栏入口
- 复用 `.mc-editor-tools`：`mailbox-chat.css:63` 的 flex/wrap/gap:6px；`.button`：`styles.css:802–850` 的尺寸、主按钮、hover/active。
- 插入位置：`${meetingTrigger}${materialTrigger}${templateReferenceTrigger}${followUpButton}`；仅新增变量，不改变三个既有按钮的条件。
- DOM：

```html
<button class="button reply-template-trigger" type="button" data-action="mc-open-template-reference">引用模板</button>
```

- 新增 CSS（逐字追加 styles.css，不修改 `.button`）：

```css
.mail-chat .button.reply-template-trigger{color:#284cc3;border-color:#b6cafa;background:#eef4ff;gap:6px}
.mail-chat .button.reply-template-trigger:hover:not(:disabled){background:#e5eeff;border-color:#93b4ec}
.mail-chat .button.reply-template-trigger:active:not(:disabled){background:#dbeafe}
.mail-chat .button.reply-template-trigger:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
.mail-chat .button.reply-template-trigger:focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
```

### S-2: 左列表、右预览、底部操作的弹框
- 复用 `.button`、`.button.primary`（`styles.css:802–850`）、`.checkbox-row`（`1166–1186`）。不借用 `.panel` 或 `.pre`，避免透明背景和全局正文样式干扰。
- 原生 dialog 为当前 portal 的子节点；dialog 使用单一实例 id `replyTemplateTitle`。下面 data-role 节点各唯一，实例销毁同步删除。
- 模板选项使用普通 button + aria-pressed，不引入需要额外键盘实现的 listbox/option。Tab 可遍历，Enter/Space 选择；原生 modal 提供焦点范围。
- 下列 HTML 为最终层级合同；动态列表只替换 `data-role="template-list"` 内部，动态正文只改 textContent。`small` 描述为空时置空/隐藏，不显示虚构分类或语言。

```html
<dialog class="reply-template-dialog" aria-labelledby="replyTemplateTitle">
  <header class="reply-template-head">
    <div><h2 id="replyTemplateTitle">引用邮件模板</h2><p>选择模板，预览后填入当前回复</p></div>
    <button class="reply-template-close" type="button" data-action="mc-close-template-reference" aria-label="关闭模板弹框">×</button>
  </header>
  <div class="reply-template-context">
    <span class="reply-template-avatar" aria-hidden="true">✉</span>
    <div><strong data-role="template-contact-name"></strong><small data-role="template-contact-email"></small></div>
    <span class="reply-template-account">回复账号 <b data-role="template-account"></b></span>
  </div>
  <div class="reply-template-layout">
    <aside class="reply-template-sidebar">
      <input class="reply-template-search" type="search" data-role="template-search" aria-label="搜索邮件模板" placeholder="搜索模板名称、描述">
      <p class="reply-template-caption">已启用模板 <span data-role="template-count">0</span></p>
      <div class="reply-template-list" data-role="template-list" aria-label="邮件模板列表">
        <button class="reply-template-option" type="button" data-action="mc-select-template-reference" data-template-id="动态ID" aria-pressed="false"><strong>动态名称</strong><small>动态描述</small></button>
      </div>
      <p class="reply-template-source">模板来自「邮件模板」</p>
    </aside>
    <section class="reply-template-main" aria-label="模板预览" aria-busy="false">
      <div class="reply-template-preview-head"><h3 data-role="template-name"></h3><span class="reply-template-badge" data-role="template-preview-badge" hidden>预览已生成</span></div>
      <p class="reply-template-status" data-role="template-status" role="status">请选择邮件模板</p>
      <button class="button" type="button" data-action="mc-retry-template-reference" hidden>重试</button>
      <div class="reply-template-warning" data-role="template-warning" role="status" hidden></div>
      <div class="reply-template-paper" data-role="template-paper" hidden>
        <div class="reply-template-subject"><span>模板主题</span><strong data-role="template-subject"></strong><small>默认保留当前回复主题</small></div>
        <div class="reply-template-body" data-role="template-body"></div>
      </div>
    </section>
  </div>
  <footer class="reply-template-footer">
    <div class="reply-template-options">
      <label class="checkbox-row"><input type="checkbox" data-role="template-replace-subject">同时替换回复主题</label>
      <div class="reply-template-modes" data-role="template-modes" hidden>
        <span>正文已有内容</span>
        <label class="checkbox-row"><input type="radio" name="reply-template-mode" value="append" checked>追加到末尾</label>
        <label class="checkbox-row"><input type="radio" name="reply-template-mode" value="replace">替换正文</label>
      </div>
      <p class="reply-template-hint" data-role="template-meeting-hint" hidden>含日历附件，仅支持追加；如需替换，请先移除日历附件</p>
    </div>
    <div class="reply-template-actions">
      <button class="button" type="button" data-action="mc-close-template-reference">取消</button>
      <button class="button primary" type="button" data-action="mc-apply-template-reference" disabled>填入回复</button>
    </div>
  </footer>
</dialog>
```

- 新增 CSS 全量合同；每个新增 class 均在这里声明。非交互结构元素不添加 hover/active；所有新交互元素含焦点与禁用状态，确认/取消复用 `.button` 状态。

```css
.reply-template-dialog{margin:auto;inset:0;box-sizing:border-box;width:min(1010px,calc(100vw - 48px));max-width:none;max-height:calc(100dvh - 48px);padding:0;border:1px solid #dce4ef;border-radius:18px;box-shadow:0 30px 100px #12274b45;background:#fff;color:#334155;overflow:hidden;font-family:var(--font-body);font-size:12px;line-height:1.6}
.reply-template-dialog[open]{display:flex;flex-direction:column}
.reply-template-dialog::backdrop{background:rgba(23,38,66,.32);backdrop-filter:blur(2px)}
.reply-template-dialog *{box-sizing:border-box}
.reply-template-dialog h2,.reply-template-dialog h3,.reply-template-dialog p{margin:0}
.reply-template-head{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:23px 28px 19px;background:#fff;flex-shrink:0}
.reply-template-head h2{font-size:20px;font-weight:600;line-height:1.4;color:#1e293b;letter-spacing:.2px}
.reply-template-head p{font-size:12px;line-height:1.6;color:#94a3b8;margin-top:6px}
.reply-template-close{display:inline-flex;align-items:center;justify-content:center;width:30px;height:30px;flex:none;border:0;border-radius:5px;background:transparent;color:#94a3b8;font:inherit;font-size:26px;cursor:pointer}
.reply-template-close:hover:not(:disabled){background:#edf3ff;color:#2451b9}
.reply-template-close:active:not(:disabled){background:#dbeafe}
.reply-template-context{display:flex;align-items:center;gap:12px;background:#f8faff;border-top:1px solid #e8edf5;border-bottom:1px solid #e8edf5;padding:13px 28px;flex-shrink:0}
.reply-template-context>div{min-width:0}
.reply-template-context strong{font-size:12px;color:#334155;overflow-wrap:anywhere}
.reply-template-context small{display:block;margin-top:3px;color:#8b9bb1;font-size:11px;overflow-wrap:anywhere}
.reply-template-avatar{display:grid;place-items:center;width:35px;height:35px;flex:none;border-radius:10px;color:#4064bd;background:#e9effc;font-size:14px}
.reply-template-account{margin-left:auto;color:#8b9bb1;font-size:11px;overflow-wrap:anywhere}
.reply-template-account b{font-weight:500;color:#526780;margin-left:9px}
.reply-template-layout{display:grid;grid-template-columns:252px minmax(0,1fr);min-height:0;flex:1;overflow:auto;overscroll-behavior:contain}
.reply-template-sidebar{border-right:1px solid #e6ecf4;background:#fbfcff;padding:18px 12px 12px;display:flex;flex-direction:column;min-width:0}
.reply-template-search{height:37px;min-height:37px;width:100%;font-size:12px;border:1px solid #dce4ef;border-radius:8px;background:#fff;padding:0 11px;color:#334155}
.reply-template-search:hover:not(:disabled){border-color:#93b4ec}
.reply-template-search:focus{background:#fff;border-color:#3b82f6;box-shadow:none}
.reply-template-search:disabled{background:#f1f5f9;color:#94a3b8;cursor:not-allowed}
.reply-template-dialog .reply-template-caption{display:flex;justify-content:space-between;color:#94a3b8;font-size:11px;padding:18px 9px 10px}
.reply-template-caption span{background:#eef2f8;border-radius:4px;padding:0 5px}
.reply-template-list{display:flex;flex-direction:column;gap:5px}
.reply-template-option{width:100%;text-align:left;border:1px solid transparent;border-radius:9px;background:transparent;padding:10px 12px;color:#475569;font:inherit;cursor:pointer;position:relative}
.reply-template-option:hover:not(:disabled){background:#f0f4fb}
.reply-template-option:active:not(:disabled){background:#dbeafe}
.reply-template-option[aria-pressed=true]{background:#edf3ff;border-color:#bed0fa;color:#2c50b8}
.reply-template-option strong{display:block;font-size:13px;font-weight:600;margin-bottom:7px;padding-right:18px;overflow-wrap:anywhere}
.reply-template-option small{display:block;font-size:11px;color:#8c9bb0;line-height:1.6;overflow-wrap:anywhere}
.reply-template-option[aria-pressed=true]::after{content:'✓';position:absolute;right:12px;top:10px;font-size:13px;color:#3865d6}
.reply-template-dialog .reply-template-source{font-size:10px;color:#94a3b8;margin-top:auto;padding:12px 9px 3px}
.reply-template-main{padding:23px 26px 14px;background:#fff;min-width:0}
.reply-template-preview-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:19px}
.reply-template-preview-head h3{font-size:15px;color:#334155;font-weight:600;overflow-wrap:anywhere}
.reply-template-badge{white-space:nowrap;color:#439079;background:#f0faf5;border:1px solid #d6eee2;border-radius:5px;padding:4px 7px;font-size:10px}
.reply-template-dialog .reply-template-status{font-size:12px;line-height:1.7;color:#64748b;margin-bottom:12px;white-space:pre-wrap;overflow-wrap:anywhere}
.reply-template-warning{font-size:12px;line-height:1.7;color:#92400e;background:#fffbeb;border:1px solid #fde68a;border-radius:7px;padding:9px 11px;margin-bottom:12px;white-space:pre-wrap;overflow-wrap:anywhere}
.reply-template-paper{border:1px solid #e1e7f0;border-radius:10px;overflow:hidden;box-shadow:0 3px 9px #182c4810;background:#fff}
.reply-template-subject{display:flex;flex-direction:column;gap:8px;background:#fafbfd;padding:15px 20px;border-bottom:1px solid #e8edf5}
.reply-template-subject span{font-size:10px;color:#94a3b8}
.reply-template-subject strong{font-size:12px;font-weight:500;color:#34445b;line-height:1.6;overflow-wrap:anywhere}
.reply-template-subject small{font-size:10px;color:#94a3b8}
.reply-template-body{padding:17px 21px;color:#425268;font-size:12px;line-height:1.75;min-height:256px;white-space:pre-wrap;overflow-wrap:anywhere}
.reply-template-footer{display:flex;align-items:center;justify-content:space-between;border-top:1px solid #e6ecf4;gap:16px;padding:19px 26px;background:#fff;flex-shrink:0}
.reply-template-options{display:flex;flex-direction:column;gap:8px;min-width:0}
.reply-template-options .checkbox-row{min-height:18px;gap:7px;font-size:12px;color:#52647b;font-weight:400;flex-direction:row;letter-spacing:0;text-transform:none}
.reply-template-options input{width:14px;height:14px;min-height:0;margin:0;flex:none;accent-color:#3865d6;cursor:pointer}
.reply-template-options input:disabled{cursor:not-allowed}
.reply-template-modes{display:flex;gap:16px;align-items:center;flex-wrap:wrap;font-size:11px}
.reply-template-modes>span{color:#94a3b8}
.reply-template-dialog .reply-template-hint{font-size:11px;line-height:1.6;color:#92400e}
.reply-template-actions{display:flex;gap:10px;flex-shrink:0}
.reply-template-actions .button{min-width:86px;font-size:12px}
.reply-template-dialog :is(button,input):focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
.reply-template-dialog :is(button,.button):disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
@media(max-width:760px){.reply-template-dialog{width:calc(100vw - 24px);max-height:calc(100dvh - 24px)}.reply-template-layout{grid-template-columns:1fr}.reply-template-sidebar{border-right:0;border-bottom:1px solid #e6ecf4}.reply-template-list{max-height:170px;overflow:auto}.reply-template-head,.reply-template-context{padding:15px 18px}.reply-template-main{padding:16px}.reply-template-footer{padding:15px;align-items:flex-end;flex-wrap:wrap}.reply-template-actions{margin-left:auto}.reply-template-badge{display:none}.reply-template-account{max-width:120px}.reply-template-modes{gap:7px}}
@media(prefers-reduced-motion:reduce){.reply-template-dialog *,.mail-chat .reply-template-trigger{transition:none!important;scroll-behavior:auto!important}}
```

- `[hidden]` 使用全局 `styles.css:96–98`，不新造 display 工具类。
- 选择/取消/确认按钮、动态列表、错误/警告、空状态、checkbox/radio、标题、正文、上下文全部归属 S-2；不允许新增未声明 class 或 inline style。正文内部新增 div/text/br 节点归属 I-4/S-2，沿用 `.mc-editor`。
- 不修改全局 p/label/input/button；不替换系统颜色 token；不顺手做暗色模式。

### S-3: 状态、可访问性和视觉变化
- 列表加载：「正在加载邮件模板…」；列表空：「暂无已启用的邮件模板」；搜索空：「未找到匹配模板」。均使用 S-2 的 status 或在 list 内创建同 class 的 p；不增加样式。
- 预览加载：「正在生成预览…」，设置 `aria-busy=true`，清旧 body/subject/preview，禁用应用；成功置 false。
- 列表失败：「邮件模板加载失败，请重试」；预览失败：「模板预览失败，请重试」。重试按钮只重试当前失败阶段，使用既有 `.button`。
- 警告使用 S-2 的 warning；上下文变化为固定状态文案；不显示请求栈或 SMTP 信息。
- 只在成功预览后显示「预览已生成」。取消、关闭、Esc 与 Tab/Enter 行为遵守 I-7。

### S-4: 资源登记
- 仅修改 index.html 已有 11 处 `?v=`；不新增 script/link、不调整顺序。
- CSS/DOM 字面合同不写入 mailbox-chat.css；新增 class 均为 `reply-template-*`，兼容 `mailboxChatStyle.test.js:118–145` 的字面 class 检查。

## 现状审计

### 证据索引与审计边界

下文 `M` = [mailbox-chat.js](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/resources/static/mailbox-chat.js)，`T` = [MailComposeTemplateService.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt)，`V` = [MailVariableService.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt)，`P` = [PendingMailOperationService.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt)。`M:123` 明确指该文件第 123 行。执行前按函数名复核，不能把旧行号当当前内容。

实际新增写入只有浏览器当前编辑器和既有内存草稿。模板表为本功能新增直接只读来源，以下列全运行时读写入口与历史迁移；联系人/ES/账号/片段为原预览服务的传递依赖，本计划不改其 schema、写入或读取算法，不声称完成这些模块的全库迁移审计。

### 1. 会话 sessionStore 与 drafts Map（实际写入对象）

- Schema：`M:374–445`。外层 key=`user|accountScope|contactId`，record 含 items/nextBefore/hasMore/滚动定位/drafts/lastUsed；drafts 是 Map。`M:2734–2739` 来信 key=`contactId:processingId:accountCode`；无来信 key=`contactId:OUTBOUND:scope`。
- draft 现有字段：`subject/html/text/qa/requestId/updatedAt/meeting/meetingAccountCode/followUpAnchorMailRecordId/outboundAttachmentDraft`，完整构造在 `M:3869–3884`。本计划不加字段。
- 写路径全集（检索 setDraft、draftsMap.set/delete、sessionStore.set/delete、draftsRef）：
  1. `touchSession/getConversationRecord/upsertConversationRecord/dropConversationRecord`，`M:378–445`：LRU 排序、记录创建/合并/淘汰。上限常量在 `M:46–49`，10 会话、500 消息。
  2. `ensureDraftsMap/setDraft/deleteDraft`，`M:573–599`：按当前 owner 取/建 Map，写/删目标 key。
  3. `saveConversationState`，`M:2441–2463`：保存消息窗口和同一 drafts Map，不采集输入 DOM；所以不能仅调用它而省略 saveDraftFromInputs。
  4. `setOutboundAttachmentItems/invalidateOutboundRequestId`，`M:3565–3585`：捕获 owner Map 后浅合并附件状态/失效请求 id，不通过 setDraft。
  5. `saveDraftFromInputs`，`M:3832–3884`：读取 DOM，重新构造全部字段；沿用附件、会议、锚点，内容变化才清 requestId。
  6. `ensureOutboundRequestId`，`M:3914–3937`：有锚点/无来信发送前浅合并 requestId，仅现有发送使用。
  7. `writeDraftWithMeeting`，`M:4818–4839`：整份构造会议草稿，保留通用附件、清跟进锚点。
  8. `removeMeetingFromDraft`，`M:4909–4932`：保存/浅合并移除 meeting 和 meetingAccountCode，正文保留。
  9. `sendManualReply` 成功分支，`M:5154–5191`：捕获 draftsMap 后按会议 revision 或正文/附件快照删除/更新原草稿；失败/取消保留。
  10. `retargetManual`，`M:5288–5332`：无已编辑草稿时自动跟随、否则经确认切换目标；迁移草稿至新 targetKey、删除旧 key、清迁移草稿 subject、会议标 stale。此函数本身不清 requestId；后续 DOM 保存走既有 contentChanged 规则。本计划只增加关闭引用弹框，不修改既有迁移语义。
  11. `instance.manual.qa` 写点：恢复 `M:2755/5314`；跟进应用 `4200` 清除；会议应用 `4872` 按 plan.qaClear 清除；可信回复采用 `4990` 创建；发送成功 `5162/5185` 清除。这是读 DOM 写 draft.qa 的上游状态，不可仅改 draft.qa。
- 读取路径全集：`currentDraftsMap/getDraft`（M:563–588）；会话选择/恢复（1692/1711/2466）；人工回复恢复（2749/2869）；附件读取/校验与捕获 owner（3544–3618）；快照比较（3774–3789）；保存/幂等（3837/3917）；跟进锚点（4020）；会议状态/应用/移除（4532/4778/4853/4923）；发送（5038/5157/5181）；新来信提示与迁移（5269/5291）。本计划只新增“应用模板→既有保存入口”这个调用方，不改这些 reader 的字段语义。
- Interaction X-1：模板应用→DOM→handleManualComposeInput→draft→切专家恢复→sendManualReply；必须同时保存 html/text。
- Interaction X-2：异步附件回包写当前 draft→模板保存重构→发送 attachmentIds；禁止旧 draft 快照覆盖。
- Interaction X-3：会议块/meeting 快照与正文双轨；替换会使现有检测判 stale（M:4586–4594），因此本期禁替换并提示先移除附件，不增加另一套联动清理。
- Interaction X-4：append/replace→qa→RAG payload 和跟进 anchor→会话发送路由；追加保留、替换清 RAG、两种操作均保留线程锚点。
- 来源：K-mailbox-draft-cache-owner-capture，经 grep 复核。

### 2. 邮件模板与正文块（新增只读消费）

- Schema：[domain](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/template/domain/MailComposeTemplate.kt:7)、[repository](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/template/repository/MailComposeTemplateRepository.kt:9)、V61 建表。template_name VARCHAR(100)、subject VARCHAR(255) NOT NULL；enabled 默认 1；block_order、block_type 必填，ref_id/custom_text 可空；block.template_id 外键 ON DELETE CASCADE。V62 增加 template_code/mail_type 和 code 唯一索引；V64 增加 subject_variants；V84 增加 required_keys；V136 增加 subject_snippet_id。没有 HTML 正文字段。
- 全部运行时写入口：T:create（59–81）、update（85–107）、setEnabled（111–120）、delete（131–142）、saveBlocks（512–523）。仓内注入两个 repository 的写服务仅此一个；BatchSendConfigController 另直接读 templateRepository.findById（514），不写。
- 历史写迁移全集：V61（建表）、V62（字段和旧模板迁入）、V64（变体列）、V71（材料提醒内容）、V78（QA_RULE 转 CUSTOM_TEXT 快照）、V84（required_keys 列）、V87/V88（退订内容）、V122（会议模板种子）、V136（主题片段列）。V72 只新增其他表的模板外键/引用读取，不是这两表的数据写入。`scripts/**/*.{py,sh,sql}` 对两表名的检索为 0；文档中的历史运维 SQL 不是本计划执行入口，也不得执行。
- 全部 repository 读取入口：T:listAll/listEnabled/getById/findTemplate/render/renderByCode/renderTextPool/preview/toDetail（47–55、155–169、234–237、280–288、442–448）；BatchSendConfigController:514。上层消费者包括模板控制器、ManualExpertMailService、IntroductionMailComposer、MeetingInvitationMailComposer、MeetingConfirmationService、AutoReplyPreviewService、AutoMailReplyService、MeetingScheduleService、BatchSendTaskConfigService、BatchSendControlService、ManualInitialOutreachService。均继续走原服务，无读写变更。
- HTTP：[控制器](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/template/controller/MailComposeTemplateController.kt:24) 的 GET list 返回 `List<MailComposeTemplateDetail>`，enabled/blocks/description/subjectSnippetId 在 DTO 中真实存在（T:817–839）。不要猜分页结构，不要假设已过滤启用状态。
- `GET /{id}/preview`（T:280）不带当前专家上下文；本功能禁止使用。
- `POST /preview-draft`（T:292–379）保持原始文本到 V.renderPreview 才替换变量；返回 subject/body/blocks/fallbackKeys/toEmail/variables/variantPoolSize，没有 htmlBody，没有渲染账号字段（T:873–893）。
- `subjectSnippetId` 由 `resolveDraftSubject`（T:561–567）服务端重新解析；无须再拉 reply-snippets 或复制 app.js 的本地片段缓存检查。
- `resolveBlocks`（T:595–746）统一处理 CUSTOM_TEXT、REPLY_SNIPPET 和兼容的 QA_RULE，包括禁用/缺失引用的跳过报告。本计划不得只选 customText，也不得把 QA_RULE 当作发送审计证据。
- `resolvePreviewContact`（T:382–397）有 contactId 即从 DB 查；找不到返回 null，并在 T:321–330 返回原始模板及 toEmail=null。必须通过 I-3 拦住，不能认为 HTTP 200 就能填入。
- `resolvePreviewAccount`（T:405–412）显式账号优先，错误被 getOrNull 吞掉。缺失 senderName 等在 variables 中提示；不用“绑定账号就是真实回复账号”的假设。
- `V:197–214/230–247`：filled/usedFallback 是原变量状态；MailPlaceholderService:151–164 的 fallbackKeys 只覆盖部分带默认值的专家变量，不是完整 missingKeys。
- `V:260–272/315`：退订服务不存在或关闭时 previewFallbacks=true 返回示例 URL。这一行为证明不能把只读预览的任何字符串无条件视为真实发送资料。
- Interaction X-5：模板后台 create/update/enable 写表→每次打开 GET→片段/专家/账号渲染→响应快照→填入；不共享 state.composeTemplates，不扩模板管理写路径。
- 来源：K-compose-template-preview-endpoint-split、K-preview-draft-raw-before-render、K-preview-account-resolution-order、K-compose-template-html-after-render。

### 3. 发件上下文、保存接口与恢复（只读核验）

- 普通来信人工回复：`M:2732–2739` 从最新来信建立 targetProcessingId/targetAccountCode；`M:5070–5076` 发信请求 senderAccountCode=null；[app adapter](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/resources/static/app.js:16782) POST 真实 processingId。服务端 `P:313/1299–1304` 回退该来信的 senderAccountCode。因此预览显式传 targetAccountCode 有代码依据。
- 带跟进 anchor：`M:5039–5045/5115` 切换会话级发送；`P:431–451` 重读相同成功发件并采用其账号。`M:3956–3972` 的 followupCandidates 只包含当前窗口/账号范围内 SENT 邮件；本计划有锚点但查不到时明确失败，不伪造。
- 无来信续信：`P:439–451` 用 [repository](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt:872) 的最新成功发件 SQL 选账号（sent_at/created_at DESC、id DESC）；`contact.boundSenderAccountCode` 并非此路径权威。本期不覆盖，是范围决策而非声称“已有接口完全支持”。
- 富文本恢复 `M:2869` 受 `ui=meetingEnabled()&&!isOutbound` 限制；无来信恢复纯 text 是现有事实，未纳入本期修复。正常 inbound 已通过 [sanitizer](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/main/resources/static/meeting-confirmation.js:128) 恢复安全标签，链接只允许 HTTP(S)/mailto，脚本/样式/事件剥除；组件缺席保留既有文本降级。
- 统一发信 seam `P:516–590` 仍对最终正文做变量处理、校验和换行规范化；新功能填入之后走同一路径，不增加 SMTP 接口或模板发送调用。
- 当前 `snapshotQa` 实际只含 ragFactCodes/ragCorpusFingerprint/baselineText（M:3887–3892）；sendManualReply 只在这些值存在时附加 RAG 字段（5078–5083）。K-rich-reply-qa-audit-reuse 所述 qaRuleIds 是兼容后端契约，不能直接当当前前端字段清单。
- Interaction X-6：来信/既有跟进目标→预览账号→静态正文署名→原发送路由；模板应用不改变账号、锚点或目标。

### 4. 前端样式盘点与代码基线

- 可复用 class：`.button`（styles.css:802–850）、`.button.primary`（838）、`.checkbox-row`（1166）、`.mc-editor-tools`（mailbox-chat.css:63）、`.mc-editor`（64/130）。完整规则实测摘录如下。

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
.checkbox-row {
    flex-direction: row;
    align-items: center;
    gap: 8px;
    min-height: 32px;
    cursor: pointer;
    font-weight: 500;
    color: var(--text-main);
    font-family: var(--font-body);
    text-transform: none;
    letter-spacing: 0;
}

.checkbox-row input[type="checkbox"] {
    width: 14px;
    height: 14px;
    min-height: auto;
    border-radius: 2px;
    accent-color: var(--primary);
    cursor: pointer;
}
.mail-chat .mc-editor-tools{display:flex;flex-wrap:wrap;gap:6px}
.mail-chat .mc-editor{min-height:160px;max-height:360px;overflow:auto;padding:12px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#334155;font-size:12px;line-height:1.8;overflow-wrap:anywhere}
.mail-chat .mc-editor{min-height:100px;max-height:240px}
```

- token：主色 `#1e40af`、主 hover `#1e3a8a`、亮蓝 `#3b82f6`；文字 `#1e293b/#475569/#94a3b8`；radius-sm=7px、radius-lg=18px；全局 panel 背景 rgba(255,255,255,.55)，panel 带 backdrop-filter；因此弹框指定不透明 #fff。既有 button 高 32px、padding 0 12px、字号12px、gap6px，hover 上移1px、active scale(.97)。新弹框尺寸/间距/状态全部固定在 S-1/S-2。
- 全局 `*{margin:0;padding:0}`（styles.css:90）、`p` 默认灰色12px（306）、`label` 默认 flex-direction:column（1117），已在合同逐项规避：dialog margin:auto，正文 div 显式颜色，checkbox-row 横排；禁止顺手改全局。
- 既有 DOM 精确基线（M:2889–2903）：

```html
            const outboundFiles = outboundDraftFilesHtml(draft ? outboundAttachmentDraftOf(draft).items : []);
            return `
                <div class="mc-compose" data-role="manual-compose" data-target-key="${escapeText(targetKey)}">
                    <label>主题<input aria-label="回复主题" value="${escapeText(subjectValue)}"></label>
                    <div class="mc-editor-tools">
                        <button class="button" type="button" data-action="mc-rich-command" data-command="bold">B</button>
                        <button class="button" type="button" data-action="mc-rich-command" data-command="italic">I</button>
                        <button class="button" type="button" data-action="mc-rich-command" data-command="insertUnorderedList">列表</button>
                        <button class="button" type="button" data-action="mc-rich-command" data-command="createLink">链接</button>
                        <button class="button outbound-upload" type="button" data-action="mc-upload-attachment" title="上传附件" aria-label="上传附件"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l10.6-10.6a4 4 0 0 1 5.66 5.66L9.41 17.41a2 2 0 0 1-2.83-2.83l9.19-9.19"/></svg></button>
                        <input type="file" data-role="outbound-file-input" multiple hidden>
                        ${meetingTrigger}${materialTrigger}${followUpButton}
                    </div>
                    <div class="mc-editor" contenteditable="true" role="textbox" aria-multiline="true" aria-label="人工回复正文" data-role="mc-editor">${editorContent}</div>
                    ${anchorNote}
```

- 注册套路：M:3026–3047 创建 body portal；材料弹框使用原生 dialog（4277–4298/4425–4433）；host click/portal click/portal change/keydown 分别委托。现有 closeMaterialRequestDialog/closeFollowUpDialog 会清空 portal，必须把新弹框关闭动作接在它们的替换入口前，并让新 close 只删除自己的节点。
- 现有 toolbar 精确顺序断言（3 处，A1 追加第 3 处）：[materialRequestIntegration.test.js:815](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/test/js/materialRequestIntegration.test.js:815)、[meetingConfirmationIntegration.test.js:1423](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/test/js/meetingConfirmationIntegration.test.js:1423)、[mailboxOutboundAttachments.test.js:1559–1564](/Users/lukai/IdeaProjects/weibo-talent-introduction/src/test/js/mailboxOutboundAttachments.test.js:1559)（`deepStrictEqual` 钉死整条 8 项 action 序列），必须同步新增按钮位置，不能删断言。
- mailbox-chat.css 与文档 target.css 字节比较、字面 class 白名单：mailboxChatStyle.test.js:37–41/118–145。故只新增 styles.css 规则。
- 资源：index.html:11–15/2323–2328，5 CSS+6 JS，共11项，当前均 `20260929-bounce-alert`。`rg -l -F '20260929-bounce-alert' src/test` 无命中（exit=1）；当前无固定键测试要改。实施前重新查，不以历史知识扩大名单。
- Interaction X-7：body portal→点击/输入/取消委托→异步回包→close/unmount；必须联合验证。
- 来源：K-global-p-is-muted-in-dialogs、K-panel-bg-token-is-translucent、K-mailbox-popover-scope-and-fixed-containing-block、K-mailbox-chat-css-byte-contract、K-frontend-cache-key-triad。

### 5. 可复核检索与实际基线测试

以下为本轮研究所用命令，不代表实施后的验证通过：

```sh
rg -n 'setDraft\(|getDraft\(|deleteDraft\(|draftsMap\.(set|delete)|sessionStore\.' src/main/resources/static/mailbox-chat.js
rg -n 'saveDraftFromInputs|saveConversationState|closeFollowUpDialog|closeMaterialRequestDialog' src/main/resources/static/mailbox-chat.js
rg -n 'MailComposeTemplateRepository|MailComposeTemplateBlockRepository' src/main/kotlin
rg -n 'templateRepository\.|blockRepository\.' src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt
rg -l 'mail_compose_template' src/main/resources/db/migration
rg -n --no-ignore 'mail_compose_template|mail_compose_template_block' scripts --glob '*.{py,sh,sql}'
rg -n 'href=".*\?v=|src=".*\?v=' src/main/resources/static/index.html
rg -l -F '20260929-bounce-alert' src/test
node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxChatStyle.test.js src/test/js/materialRequestIntegration.test.js src/test/js/composeTemplatePreview.test.js
```

结果：上述4份 JS 用例合计133 tests，133 pass、0 fail、0 skipped。输出位于 `/private/tmp/template-reference-plan-baseline.log`（临时运行证据，非交付依赖）。未连接生产数据库，未查询真实模板清单；不能据此声称线上有哪些模板或所有变量已填。

知识处理：Phase 0 匹配条目已更新 hit_count/last_used；全部本次实际使用条目不做衰减归档。`K-mail-body-display-sites` 的“全部正文点”未采用为改动范围，本需求只操作当前人工编辑器；`K-meeting-confirmation-generic-template-boundary` 用于排除新会议渲染；`K-manual-rich-render-before-send` 用于保持后端 seam 不变；`K-js-test-invocation-surface` 用于明确 node --test 门禁。其余来源已标于具体审计/不变量。既有 docs/design.md 描述早期固定模板体系，当前接口/DTO优先于其历史叙述。

Phase 6 回写：纠正 `K-compose-templates-state-scope` 的“唯一加载链”旧结论；更新 `K-mailbox-draft-cache-owner-capture` 的实际 Map 写入口；补充共用 portal 清理所有权；新增 `K-preview-result-not-send-ready` 记录示例退订值。预览端点知识同步当前 DTO 和严格预览的跳过语义。本轮没有条目首次跨过 hit_count=10，没有同一命题≥5条需合并；未新增项目级通则。不改已有 CLAUDE.md；仓内未找到 agents/*.md 或 templates/project-CLAUDE.md 角色入口，不新建角色体系。

## 实现方案

### T-1：接入入口、弹框与生命周期

- 文件：`src/main/resources/static/mailbox-chat.js`、`src/main/resources/static/styles.css`。
- 约束：I-1/I-6/I-7/I-8；S-1/S-2/S-3。
- 在 createInstance 中新增一个 templateReference 对象；不加全局缓存，不加草稿字段。工具栏按 S-1 插入按钮。
- 建议内部函数名固定为 `templateReferenceDialogHtml/openTemplateReferenceDialog/closeTemplateReferenceDialog/renderTemplateReferenceList/renderTemplateReferencePreview/applyTemplateReference`；只是当前模块局部函数，不导出新公共 API。
- 捕获身份函数复用 conversationCacheKey/currentTargetKey/manualInputs/currentFollowUpAnchorId/followupCandidates；正文和主题快照按 readManualValues 规范化后比较。新增 seq 单调递增，不在关闭时归零。
- 在 onClick/onClickPortal 增加打开、选择、重试、关闭、应用分支；portal 新增 input/change 委托处理搜索与 checkbox/radio，保留现有材料 change 委托；所有监听随 portalHandlers 解绑。
- 搜索按 templateName+description 小写包含匹配，纯本地、不发请求。默认选择列表首个已启用模板并预览；搜索后原选择仍在结果中则保留，否则选首个；结果空则清选中与快照并禁用应用。加载/选择时不重建搜索输入节点，不丢焦点。
- 生命周期逐项挂接：`teardownConversationSubViews`、`meetingCloseDisposeOnAccountScopeChange`、`retargetManual`、管理/材料/跟进打开前、unmount 既有 teardown 链。打开新弹框前关闭旧 portal 占用者；调用顺序不得由旧 close 再清掉新 dialog。
- 发送期间入口 disabled；open/apply 同时检查 instance.manual.busy，防止无附件普通发送只禁用发送按钮时绕过。
- 实际新增临时状态 reader 是本弹框渲染与 apply；写入不触及数据库。

### T-2：只读加载、渲染与异常状态

- 文件：`src/main/resources/static/mailbox-chat.js`。
- 约束：I-1/I-2/I-3/I-6；S-2/S-3。
- 打开后立即加载 GET list。Array 响应验证失败按加载失败；只用当前弹框 items，不调用 app.js 的 loadComposeTemplates（会刷新模板管理表）或 ensureComposeTemplatesLoaded（可能使用陈旧缓存）。选中项必须有正整数 id、string subject、Array blocks；预览结果必须有 string subject/body 和 Array blocks/fallbackKeys/variables，否则进入现有失败状态，不对缺失字段猜默认成功。使用局部检查即可，不引入 schema 库。
- payload 定稿：

```js
{
  subject: selected.subject,
  subjectSnippetId: selected.subjectSnippetId ?? null,
  blocks: selected.blocks.map(block => ({
    blockOrder: block.blockOrder,
    blockType: block.blockType,
    refId: block.refId ?? null,
    customText: block.customText ?? null
  })),
  contactId: identity.contactId,
  senderAccountCode: identity.senderAccountCode,
  strictPlaceholders: false,
  variantIndex: 0
}
```

- contactId 已足够走真实联系人分支；不构造虚拟 contact，不以 orcid/email fallback 掩盖联系人不存在。variantIndex=0 是确定的第一个候选；本期不提供“换一个版本”。
- 请求前清旧 preview；回包经过 seq/identity 校验再存为本次快照并渲染。数据来源是既有模板/片段/联系人/账号写路径，无需修改上游 writer。
- 将 fallbackKeys、variables、未包含 blocks 明确变成 UI 文本；不要消费 textPreview 作完整正文（后端截断200字符）。正文唯一使用 result.body。
- 应用按钮的有效性判断集中一个函数供渲染与点击复核；不实现两套略有差异的判断。勾选主题只更新适用校验，不重发预览。
- 列表重试重读列表；预览重试只重读当前模板。取消/关闭使所有在途回包失效。模板修改在下次打开体现；不新增跨标签页实时同步和“提交前再次随机预览”。

### T-3：原子填入当前草稿

- 文件：`src/main/resources/static/mailbox-chat.js`。
- 约束：I-4/I-5/I-6；S-2/S-3。
- 点击后先复核身份、DOM快照、preview、busy、meeting替换禁用条件；任一失败不得先改主题再报错。
- 从快照 body 生成安全纯文本节点。正文空直接填；追加保留现有节点并在末尾加一个块，换行遵循现有 normalizeManualTextLineBreaks；显式替换则只清正文。正常无需第二次覆盖确认，radio 已是明确选择。
- checkbox 勾选时才写 subject；替换正文时先置 instance.manual.qa=null，追加不改 qa。不要把 templateId 作为发送字段或审计证据存到 draft。
- 调用 `handleManualComposeInput(editor)`，随后 `saveConversationState()`；由现有 reader 消费新 html/text/qa。该入口读取最新 draft，因此保留弹框打开期间完成上传的附件。
- close 本弹框，不额外 renderConversation 重建编辑器；聚焦正文并提示「模板已填入回复，可继续编辑」。
- 不更改 sendManualReply、mcHostSendRichReply、submitManualRichReply 或后端请求 DTO；发送仍从现有 DOM/draft 读取。

### T-4：行为测试与样式合同验证

- 文件：`src/test/js/mailboxChatBehavior.test.js`、`src/test/js/materialRequestIntegration.test.js`、`src/test/js/meetingConfirmationIntegration.test.js`、`src/test/js/mailboxOutboundAttachments.test.js`（A1 追加）、`src/test/js/mailboxTemplateReferenceStyle.test.js`（新增）。
- 约束：I-1至I-8；S-1至S-4。
- 在 mailboxChatBehavior.test.js 复用现有 MiniDOM/bootChat/API调用记录，为新 describe 增加明确模板 fixture 与可控延迟 Promise；不复制另一套完整测试框架。需真实 sanitizer 的用例以局部测试选项加载实际 meeting-confirmation.js，默认原有用例环境不变。
- MiniDOM 不模拟浏览器排版，html/text 一致性用既有 setEditorContent 辅助；不能把 stub 的 innerText 与真实浏览器换行等同。真实 Tab/Esc、dialog 居中、换行和格式保存另外列人工验收。
- materialRequestIntegration、meetingConfirmationIntegration 与 mailboxOutboundAttachments（A1 追加）仅更新原顺序断言，加断言引用按钮位于材料之后/跟进之前并保留 9 项完整序列，保留原断言意义；会议/附件互操作用现有真实 meeting harness 增加验证，不为新功能复制会议实现。
- 新 style 测试从本计划的 S-1/S-2 读取两个 css fenced blocks，逐字验证 styles.css 包含；断言 button/DOM role/class、资源键统一、无新增 inline style、mailbox-chat.css 字节不变。不把动态 HTML 原样字符串当唯一行为测试。

### T-5：版本键与收尾验证

- 文件：`src/main/resources/static/index.html`。
- 约束：I-8；S-4。
- 实施前重新读取11个资源键与固定值测试命中，确认工作树无并行资源改动；全部改为 `20260930-manual-template-reference`。若产生新的硬编码测试文件，先修订文件清单并检查10文件上限，不静默扩文件。
- 执行下列验证；通过后提供真实页面录屏/截图和未执行的人工验收项。仅测试通过不标“人工验收完成”，不部署。

## 变更文件清单

| # | 文件（仓库根目录下） | 改动范围 |
|---|---|---|
| 1 | `src/main/resources/static/mailbox-chat.js` | 入口、dialog、本地状态、只读请求、填入、生命周期；不改发送路由 |
| 2 | `src/main/resources/static/styles.css` | 仅追加 S-1/S-2 精确 CSS |
| 3 | `src/main/resources/static/index.html` | 11个资源缓存键；无新 script/link |
| 4 | `src/test/js/mailboxChatBehavior.test.js` | 复用既有 harness 增加模板行为/竞态/草稿/发送断言 |
| 5 | `src/test/js/materialRequestIntegration.test.js` | 工具栏顺序契约与材料→模板互操作 |
| 6 | `src/test/js/meetingConfirmationIntegration.test.js` | 工具栏顺序、会议/附件/RAG互操作回归 |
| 7 | `src/test/js/mailboxTemplateReferenceStyle.test.js` | 新增 CSS 逐字合同、DOM/资源登记验证 |
| 8 | `src/test/js/mailboxOutboundAttachments.test.js` | （A1）仅 :1559–1564 工具栏顺序断言插入引用模板按钮并同步消息文本 |

共8文件。计划和 create-p 知识维护产物为本次规划输出，不是实现范围；mockups 保持原状。禁止修改 mailbox-chat.css、meeting-confirmation.js、app.js、Kotlin、SQL、历史计划基线、无关工作树改动。

## 验收标准

### 机器验证矩阵

| 不变量 | 必须执行的断言 |
|---|---|
| I-1 | inbound 显示；outbound/unavailable/待匹配无新入口；meeting library 缺席仍可打开纯文本引用；open/apply during busy 无写入；操作录得的网络只有两种只读 URL；发送 adapter 在应用时调用0次 |
| I-2 | 冷进入邮箱不访问模板管理页仍加载；disabled模板被过滤；每次打开重新GET；payload只含四字段blocks，保留subjectSnippetId；普通来信账号、已有跟进账号各以不同账号fixture证明；缺失跟进锚点账号不猜；variantIndex=0；应用不再次预览 |
| I-3 | HTTP错误/响应形状错/toEmail=null或不匹配/空body/残留变量/精确示例退订URL均禁填；fallback和missing分别提示；跳过块显示原因；主题未勾选与勾选对主题残留校验有区别；textPreview截断不影响完整body |
| I-4 | fixture含`<img src=x onerror=...>`、`<script>`、`&`、多行文本：预览与编辑器显示字面文本，不生成可执行节点；send payload为已填完整html/text；真实sanitizer恢复保留安全粗体/列表；无localStorage正文写入 |
| I-5 | 默认原主题不变；勾选精确替换；空正文填入/非空默认追加/显式替换三场景；append保留qa，replace清qa；两种均保留有序attachmentIds与anchor；会议ready/stale都禁replace，append不改原块；原requestId只随真实内容改变失效 |
| I-6 | A预览慢B快只显示B；关后回包不再挂载；切专家/账号/新来信/退出后回包不写新目标；打开后改主题或正文拒绝应用；附件上传回包后应用保留新附件；同user/account/contact但新epoch仍拒旧响应 |
| I-7 | Esc/cancel/X经同一路径清状态；每个挂接点关闭；open/close循环不重复监听；关闭失效旧实例不删除新材料/管理dialog；成功后焦点正文，取消后焦点按钮 |
| I-8 | git diff只涉及7个实现文件；mailbox CSS基线未改；11键同值且无新增固定旧键；无后端/DB/ES/依赖变化 |

### 样式验证

- S-1：源码/DOM断言工具栏顺序，复用button类；styles.css包含S-1逐字块。
- S-2：styles.css包含S-2逐字块；对新增模板class做集合检查，所有class已声明；无新增inline style；动态正文只有安全节点。真实浏览器1280×720与760px以下可滚动且footer按钮可到达。
- S-3：loading/error/empty/selected/disabled/meeting限制逐个断言文案与属性；真实浏览器检查Tab、Enter、Esc、焦点恢复。
- S-4：用index的styles.css键派生并断言全部11项一致，不将历史数量猜成12；原有静态资源测试仍通过。

### 实施后命令

```sh
node --check src/main/resources/static/mailbox-chat.js
node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxTemplateReferenceStyle.test.js src/test/js/materialRequestIntegration.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxOutboundAttachments.test.js src/test/js/composeTemplatePreview.test.js src/test/js/meetingConfirmationAssets.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js
node --test src/test/js/*.test.js
git diff --check
```

源自 K-js-test-invocation-surface：verify.sh 不作为全量前端门禁。本次无Kotlin/SQL改动，不要求启动生产或为此重跑数据库迁移；仓库正式合并门禁若要求Maven，沿用项目JDK11命令，不能以JS通过宣称后端集成已实测。

### 跨路径场景

X-1～X-7 分别由下方 A-3/A-7/A-8/A-9/A-1/A-10/A-6 覆盖。机器验证必须同时观察DOM、草稿恢复、实际提交给发送adapter的payload；不以字符串存在就判断链路通过。

## 人工验收清单

以下是权威清单。验收开始时才导出同目录 `manual-reply-template-reference-acceptance.md`，逐条加勾选框、验收人、日期、结果/备注；本次不提前导出。所有发信项在测试环境、测试账号与测试收件箱执行，不向截图中的真实专家发验收邮件。

### A-1: 冷进入、真实模板维护与搜索
- 前置条件：在测试环境邮件模板页新建并启用「验收-A」，自定义主题 `Template subject A`，正文 `第一段\n\n第二段`（实际输入换行）；另建「验收-B」并停用。两条都使用自定义文本，不依赖专家字段。选择一个已有来信的测试专家；关闭标签页再重新登录直接进入收发件箱。
- 操作步骤：1. 打开该专家人工回复；2. 点击「引用模板」；3. 搜索「验收」；4. 关闭，在模板页修改A正文为 `更新正文`；5. 返回邮箱重开弹框。
- 预期结果：有「引用模板」，列表有A、没有B；预览显示完整正文和模板主题；第二次打开显示 `更新正文`；切到无来信仅成功发件的专家及待匹配页，没有新增引用入口。后台A/B不因引用而改变。
- 覆盖：O-1、N-1/N-5、I-1/I-2/I-8、S-1/S-2、X-5。

### A-2: 空正文填入与主题选项
- 前置条件：A-1的测试专家与模板A；回复主题手填 `Re: 测试主题`，清空正文。
- 操作步骤：1. 选择A后直接填入；2. 检查主题；3. 重开选择A，勾选「同时替换回复主题」，选择替换正文后填入。
- 预期结果：第一次正文=`更新正文`，主题仍=`Re: 测试主题`；第二次主题=`Template subject A`；没有新发件记录，发送按钮仍由用户主动操作。
- 覆盖：O-2、N-1/N-5、I-4/I-5、S-2/S-3。

### A-3: 追加、替换、编辑、草稿恢复与发送
- 前置条件：在测试环境模板页把A正文改成两段：`模板第一段`、空行、`模板第二段`。当前测试专家正文输入 `原有正文`，选中并点击B加粗；无会议/跟进/通用附件。
- 操作步骤：1. 引用A，保留默认追加；2. 切换另一个专家再返回；3. 在已填正文末尾输入 `补充说明`；4. 再次引用A显式选「替换正文」；5. 点击发送人工回复到测试收件箱。
- 预期结果：追加后原有正文仍粗体，后方两段文字完整；切回仍保留；替换后不含 `原有正文` 或 `补充说明`，仅A的两段；发送成功后测试收件箱正文含两段、主题符合当前输入；系统只新增1封发送记录。浏览器刷新恢复不在承诺范围。
- 覆盖：O-2、N-2/N-4/N-5、I-4/I-5、X-1。

### A-4: 取消和失败零覆盖
- 前置条件：当前主题=`Re: 保留主题`，正文=`请保留正文`；模板A可用。测试浏览器开发者工具的Network可切Offline；不得用生产环境做故障注入。
- 操作步骤：1. 选择模板后分别用取消、X、Esc关闭；2. 切Offline再打开；3. 恢复Online点击重试；4. 选择模板后关闭并检查草稿。
- 预期结果：三种关闭和失败后主题/正文逐字不变；失败显示「邮件模板加载失败，请重试」，填入禁用；恢复后可重试；无邮件发送、无模板写入。
- 覆盖：N-1、I-1/I-3/I-7、S-3。

### A-5: 默认值、缺值、失效块与示例链接
- 前置条件：使用测试专家；在「邮件模板」创建正文为 `Dear ${expertFamilyName|Professor},` 的模板，选择其个人档案姓氏确实为空的测试对象（通过专家详情确认，不借真实专家猜测）。另建引用回复片段的模板，再在回复片段页停用该片段；保留一个自定义正文块 `剩余正文`。示例退订用例在独立测试实例关闭退订服务配置，模板包含 `${unsubscribeUrl}`。
- 操作步骤：1. 分别预览上述模板；2. 检查提示和填入按钮；3. 对失效片段模板点击填入；4. 对示例退订模板尝试填入。
- 预期结果：默认值模板显示 `Dear Professor,` 和使用默认值提示；失效片段显示「已禁用」原因，正文只包含 `剩余正文`；示例链接模板显示「退订链接尚未配置，当前仅为示例链接，请先配置后重试」，填入禁用。若实例真实配置无法构造示例状态，此项标未执行，不能冒充通过。
- 覆盖：O-1、I-2/I-3、S-3、X-5。

### A-6: 快速选择、关闭与跨会话隔离
- 前置条件：测试环境有已启用模板A/B（若沿用A-1夹具，先启用B）、专家甲/乙（均有来信）。通过测试浏览器Network节流到Slow 3G；甲正文=`甲草稿`，乙正文=`乙草稿`。
- 操作步骤：1. 在甲弹框依次快速选择A/B；2. 请求未完成时Esc关闭；3. 切到乙并打开引用弹框；4. 等请求完成；5. 返回甲。
- 预期结果：停留在B时仅展示B；关闭后旧弹框不重现；乙仍显示乙姓名和乙模板预览，正文仍=`乙草稿`；甲仍=`甲草稿`。多次开关后Esc一次即可关闭，不残留遮罩。
- 覆盖：N-1/N-4、I-6/I-7、S-3、X-7。

### A-7: 通用附件在引用前后保持
- 前置条件：测试专家甲；上传测试文件 `template-reference-check.txt`，文件内容为 `attachment-check`。用测试环境Network节流，让上传请求在弹框打开期间完成。
- 操作步骤：1. 开始上传后打开引用模板；2. 上传完成后填入A；3. 再引用A并替换正文；4. 关闭弹框检查附件；5. 发到测试收件箱。
- 预期结果：附件卡始终只有1个同名文件，无重传；实际收到1个 `template-reference-check.txt`，内容=`attachment-check`；模板应用不触发发送。
- 覆盖：N-3/N-5、I-5/I-6、X-2。

### A-8: 会议与材料索取回归
- 前置条件：测试专家有来信；使用现有「会议确认」生成一份明日有效时间/测试会议链接的会议正文及ICS；无发送。
- 操作步骤：1. 点击材料索取，选择一项并填入；2. 引用A；3. 检查替换选项，使用追加；4. 关闭后检查会议卡；5. 用原按钮移除日历附件，再开引用弹框。
- 预期结果：材料文字仍在正文；有ICS时「替换正文」禁用并出现固定提示；追加后会议卡仍为「待发送附件」、时间/链接不变；移除后卡消失，替换选项可用；会议/材料按钮仍能打开原弹框。
- 覆盖：N-2/N-3、I-5/I-7、S-1/S-3、X-3。

### A-9: 可信回复证据与跟进锚点
- 前置条件：独立测试环境；同一测试专家存在来信和成功发件。通过可信回复工作台采用一份带RAG证据的草稿；浏览器Network记录manual-rich-reply的请求payload，不需要阅读源码。
- 操作步骤：1. 采用草稿后引用A追加，再发送至测试收件箱；2. 重新采用可信草稿，引用A选择替换，再发送；3. 通过跟进邮件按钮手动选择一封成功发件，填入跟进内容；4. 引用A追加并发送。
- 预期结果：第1次请求保留ragFactCodes，正文变化导致edited=true；第2次不携带旧ragFactCodes/ragCorpusFingerprint；第3次沿用同一「已引用邮件 #ID」提示，发送请求anchorMailRecordId不变；模板refId从未当作证据发送。使用3个独立验收轮次记录，不把重复发信当功能自动执行。
- 覆盖：N-2/N-5、I-2/I-5、X-4。

### A-10: 回复账号不取联系人绑定账号
- 前置条件：测试环境两个发件账号A/B；准备一个最新来信由B接收、联系人绑定账号为A的测试专家（通过测试导入夹具或现有账号收信构造，验收记录实际ID）。另准备其成功发件A可用于手选跟进；两个账号senderName不同，模板正文=`Best regards, ${senderName}`。
- 操作步骤：1. 在来信人工回复预览模板；2. 关闭后选A的成功发件作为跟进锚点，再预览；3. 分别记录上下文账号与署名。
- 预期结果：普通来信显示B和B的senderName；跟进显示A和A的senderName；不会因为联系人绑定A让普通来信预览变成A。无法构造该数据时标未执行，不能用相同账号冒充对照。
- 覆盖：N-5、I-2/I-6、X-6。

### A-11: 样式、长内容与键盘
- 前置条件：测试模板A；另建名称80字、描述较长、正文含长URL和20段文字的模板。浏览器窗口1280×720，另缩到740px宽。
- 操作步骤：1. 打开引用弹框，与计划截图/合同对照；2. 选长内容模板并滚动；3. 缩窄窗口；4. 用Tab、Enter选择模板、Esc关闭。
- 预期结果：宽屏弹框居中，最大宽1010px、左右252px/剩余宽度、圆角18px、不透明白底；正文12px/1.75且清晰，按钮32px高；选中项浅蓝#edf3ff；窄屏单列，列表最多170px后滚动；长URL换行、确认按钮可到达；Esc后焦点回「引用模板」。原页列表与人工回复区域样式没有变化。
- 覆盖：O-1、N-2、I-7/I-8、S-1/S-2/S-3/S-4。

### A-12: 模板特殊字符按文本处理
- 前置条件：测试模板自定义正文写入字面 `<img src=x onerror=alert(1)>`、`A & B` 和两行普通文字；主题=`字符测试`。
- 操作步骤：1. 预览；2. 填入；3. 切另一专家再返回。
- 预期结果：始终能看到完整字面标签和 `A & B`，无图片、无alert；两行仍分行；原模板不会被改写。
- 覆盖：O-2、N-1/N-4、I-4、X-1。

### 规划自检记录

- [x] 需求、关键不变量、样式契约、现状审计、实现方案、变更文件、机器验收、人工验收按顺序齐全。
- [x] I-6覆盖唯一新增的临时状态；共享draft/DB/ES零新增字段。
- [x] 读写审计包含普通setDraft之外的附件/发送捕获Map写入、迁移、删除；模板运行时与历史迁移区分。
- [x] 每个新DOM/class归属S-1或S-2，状态归属S-3；新CSS全文提供，旧CSS只引用/摘录，不改字节基线。
- [x] 8文件≤10；1子系统≤2；各任务列明确文件并引用I/S编号；无暗含后端任务。
- [x] O-1/O-2与N-1至N-5均有A场景；X-1至X-7有跨路径验收；不将机器测试通过当人工验收完成。
- [x] 知识条目逐项复核/取舍，未按旧行号、旧缓存键数量或原型数据猜测生产行为。
- [x] 未生成acceptance衍生文件，未实施生产功能；范围扩展需修订本计划。
