# 04 人工回复会议弹窗与可搜索时区

依赖01→02→03；1个前端子系统；7个实施文件，新增DB字段0。仅修改稳定的人工回复与消息附件接入点，不替换当前收发件箱整页。新文件直到05加载才显示按钮；无组件脚本时旧路径可用。

## 需求描述

R1：人工回复“链接”右侧增加“会议确认”，弹窗配置专家称呼、日期/起止/时区、Zoom、签名和模板；时区支持中文常用地名、英文/IANA、UTC偏移搜索。R2：先预览/下载ICS，再确认填入正文和1个待发送附件；可编辑/移除。R3：发出后消息显示真实日历下载。

必须保留 M1：原主题、B/I/列表/链接、手工正文和QA/RAG采用；M2：用户/账号/专家/目标来信草稿隔离、旧发送确认；M3：当前列表/筛选/关注/标签/翻译/滚动和原材料附件；M4：无真实来信仍显示原模板跟进入口，不伪造人工回复。范围外：全局换肤、浏览器刷新后持久草稿、自动猜会议时间、AI自动取Zoom链接、修改旧会议业务、主题解码第二轮。

## 关键不变量

### Invariant I-1: 确认只写草稿
- Rule：弹窗options/zones/preview最多调用01只读API；confirm只有写当前draft和editor，不能调用manual-rich-reply或旧confirmMeeting接口。唯一发送入口仍mc-send-manual。取消包括Esc/关闭不写草稿。
- Applies to：open/input/confirm/cancel
- Violation consequence：草稿串目标、正文/附件失配、界面失真或误发。
- 来源：用户请求；D2

### Invariant I-2: 草稿身份与异步所有权
- Rule：外层会话缓存继续user|accountScope|contactId，内层targetKey继续contactId:processingId:accountCode；不另建按专家名全局Map。preview结果必须匹配owner+targetKey+requestSeq+formRevision；切换/unmount后丢弃晚响应。带会议发送捕获旧draftsMap引用和key/revision，成功只清该份已发送快照，不能清新目标草稿/QA。
- Applies to：get/setDraft、select/refresh/retarget、preview/send生命周期
- Violation consequence：草稿串目标、正文/附件失配、界面失真或误发。
- 来源：K-state-input-no-per-keystroke-innerhtml；K-shared-action-dialog-cleanup

### Invariant I-3: 会议块与附件成对更新
- Rule：每draft最多1份calendar；首次空正文替换，已有正文默认追加；同一未手改生成块更新替换旧块；已手改块不悄悄覆盖，用户可显式替换全文或移除附件后保留手工信。输入修改导致旧preview立即失效；只能应用最后成功preview。
- Applies to：apply/edit/remove、save/restore、adoptAssembly
- Violation consequence：草稿串目标、正文/附件失配、界面失真或误发。
- 来源：original

### Invariant I-4: 同源数据不自行再算
- Rule：正文、日期星期/偏移、中国时间、ICS、sha都使用01同一response。浏览器只搜索服务端时区目录、绑定控件、Blob下载，不运行preview MeetingCore的本地→UTC算法；待发送JSON携带meeting和previewAttachmentSha256。
- Applies to：form/render/download/send
- Violation consequence：草稿串目标、正文/附件失配、界面失真或误发。
- 来源：original

### Invariant I-5: 搜索不隐式改时区
- Rule：搜索词与selectedZoneId分开；只点击option/键盘Enter明确选中才变selectedZoneId；Esc/Tab/外部点击恢复已选标签；无结果不得提交搜索词为IANA。日期变化重新加载对应目录偏移，仍保留id。
- Applies to：combobox、目录请求/键盘
- Violation consequence：草稿串目标、正文/附件失配、界面失真或误发。
- 来源：original

### Invariant I-6: 样式与HTML可信边界
- Rule：S-1..S-5逐字契约；新CSS放独立meeting-confirmation.css，不改既有CSS、不引入rev类/导航/mock数组/fetch拦截。外部文本仅textContent/escape；恢复draft html只接受本页捕获内容且经04限定sanitizer，不能信任邮件HTML作为编辑器输入。
- Applies to：组件/样式/草稿恢复
- Violation consequence：草稿串目标、正文/附件失配、界面失真或误发。
- 来源：K-global-p-is-muted-in-dialogs；K-mailbox-popover-scope-and-fixed-containing-block

### Invariant I-7: 附件状态明示
- Rule：draft.meeting为null=无日历，非空为{input,preview,blockHtml,blockText,state,revision}；state仅ready或stale。stale包括目标改变/会议块手改，禁止携带附件发送，必须编辑重生或移除。loading/error仅modal状态，不覆盖已确认draft。历史卡只消费03calendarAttachment，不改变材料count。
- Applies to：draft保存/恢复/retarget/历史渲染
- Violation consequence：草稿串目标、正文/附件失配、界面失真或误发。
- 来源：K-attachment-metadata-consumer-chain

## 样式契约

### S-1 全量 CSS 与宿主边界

复用 `.button` styles.css802..850的32px/12px/7px、蓝渐变/hover/active；当前mailbox-chat.css63..68的tools/editor/footer/focus，130编辑器100..240px，147按钮padding11px。**不改这些规则**；所有新规则全文如下，逐字写入 `src/main/resources/static/meeting-confirmation.css`，与证据副本必须字节相同。正文paragraph显式inherit防止body下dialog吃到全站p的灰色12px。未列出的新class、inline style、修改全局p/button以及复制preview的#manual/.rev-*一律禁止。

```css
/* 专家会议确认：只限新组件及新生成正文块。 */
.mail-chat .button.meeting-trigger{color:#1e40af;border-color:#b8cef5;background:#eef4ff;gap:7px;margin-left:3px}
.mail-chat .button.meeting-trigger:hover{background:#e5eeff;border-color:#93b4ec}
.mail-chat .button.meeting-trigger:active{background:#dbeafe}
.meeting-icon{display:inline-flex;font-size:16px;line-height:1}
.meeting-dialog{margin:auto;inset:0;width:min(1080px,calc(100vw - 40px));max-height:calc(100dvh - 40px);padding:0;border:1px solid #d5dfed;border-radius:14px;background:#fff;color:#334155;font-family:var(--font-body);font-size:12px;line-height:1.6;box-shadow:0 24px 100px #172c473d;overflow:auto;overscroll-behavior:contain}
.meeting-dialog::backdrop{background:#182a464f;backdrop-filter:blur(2px)}
.meeting-dialog *{box-sizing:border-box}
.meeting-dialog [hidden],.mail-chat .meeting-attachment[hidden]{display:none!important}
.meeting-dialog form{margin:0;padding:0}
.meeting-head{display:flex;align-items:center;justify-content:space-between;padding:20px 24px;border-bottom:1px solid #e2e8f0;gap:16px;position:sticky;top:0;background:#fff;z-index:2}
.meeting-head h2{margin:0;font-size:19px;font-weight:600;color:#334155;line-height:1.4}
.meeting-head p{margin:6px 0 0;font-size:12px;color:#64748b;line-height:1.6;overflow-wrap:anywhere}
.meeting-close{display:inline-flex;align-items:center;justify-content:center;flex:none;width:28px;height:28px;padding:0;border:0;border-radius:5px;background:transparent;color:#91a1b7;font:inherit;font-size:21px;line-height:1;cursor:pointer}
.meeting-close:hover{background:#edf3ff;color:#2451b9}
.meeting-close:active{background:#dbeafe}
.meeting-grid{display:grid;grid-template-columns:45% 55%}
.meeting-form{padding:18px 24px;border-right:1px solid #e2e8f0;min-width:0}
.meeting-preview{padding:22px 24px;background:#f8faff;min-width:0}
.meeting-form label{display:flex;flex-direction:column;gap:6px;font-size:12px;line-height:18px;color:#52647e;margin-bottom:12px;font-weight:500;letter-spacing:0;text-transform:none;min-width:0}
.meeting-form input,.meeting-form select,.meeting-form textarea{min-width:0;width:100%;height:36px;min-height:36px;margin:0;padding:7px 10px;font:inherit;font-weight:400;color:#334155;border:1px solid #d7e0ed;border-radius:7px;background:#fff;box-shadow:none}
.meeting-form textarea{height:70px;resize:vertical;line-height:1.6}
.meeting-form input::placeholder,.meeting-form textarea::placeholder{color:#94a3b8;opacity:1}
.meeting-form small{font-size:11px;font-weight:400;color:#76859b;line-height:16px}
.meeting-form :is(input,textarea,select):hover:not(:disabled){border-color:#93b4ec}
.meeting-form :is(input,textarea,select)[aria-invalid=true]{border-color:#e11d48;background:#fff8fa}
.meeting-form :is(input,textarea):read-only{background:#f8faff}
.meeting-form :is(input,textarea,select):disabled{opacity:.55;cursor:not-allowed;background:#f1f5f9}
.meeting-fields{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:12px}
.meeting-clock{margin:-3px 0 14px;padding:11px 12px;border:1px solid #dce7fa;border-radius:7px;background:#f0f5ff;font-size:12px;line-height:1.65;color:#47658b}
.meeting-clock b{font-weight:500;color:#234f99}
.meeting-preview h3{font-size:12px;font-weight:600;margin:0 0 12px;color:#536680;display:flex;align-items:center;justify-content:space-between;gap:8px;line-height:1.6}
.meeting-preview h3>span:last-child{font-weight:400;color:#73859c;font-size:11px}
.meeting-step{display:inline-block;color:#1e40af;background:#eff5ff;border:1px solid #d6e3f8;border-radius:5px;padding:3px 7px;font-size:11px;margin-right:9px}
.meeting-paper{background:#fff;border:1px solid #dce4ef;border-radius:9px;padding:20px 22px;font-size:13px;line-height:1.85;overflow-wrap:anywhere;min-height:342px;color:#334155}
.meeting-paper p{margin:0 0 14px;color:inherit;font-size:inherit;line-height:inherit}
.meeting-paper p:last-child{margin:0}
.meeting-paper a,.mail-chat .meeting-body-block a{color:#2563b1;text-decoration:underline;overflow-wrap:anywhere}
.meeting-paper a:hover,.mail-chat .meeting-body-block a:hover{color:#1e40af}
.meeting-paper a:active,.mail-chat .meeting-body-block a:active{color:#172554}
.meeting-file{display:flex;align-items:flex-start;gap:11px;padding:13px 14px;margin-top:14px;border:1px solid #d8e3f2;border-radius:8px;background:#fff;min-width:0}
.meeting-file-icon{flex:none;display:grid;place-items:center;width:36px;height:42px;background:#edf4ff;border:1px solid #cbdcf6;border-radius:6px;font-size:11px;color:#3964a4;font-weight:600}
.meeting-file-main{flex:1;min-width:0}
.meeting-file-main strong{display:block;font-size:12px;font-weight:500;overflow-wrap:anywhere;color:#435b7c;line-height:1.6}
.meeting-file-main small{display:block;font-size:11px;color:#788ba5;margin-top:5px;line-height:1.6}
.meeting-file-actions{display:flex;flex-wrap:wrap;gap:14px;align-items:center;margin-top:9px}
.meeting-link{display:inline-flex;align-items:center;border:0;border-radius:3px;background:none;color:#315fa7;font-family:inherit;font-size:12px;line-height:1.6;cursor:pointer;padding:0;text-decoration:none;white-space:nowrap}
.meeting-link:hover{color:#244ca9;text-decoration:underline}
.meeting-link:active{color:#172554;background:#edf3ff}
.meeting-raw{font-family:ui-monospace,monospace;font-size:11px;line-height:1.6;color:#62768e;white-space:pre-wrap;overflow-wrap:anywhere;max-height:160px;overflow:auto;padding:12px;border:1px solid #dae3ef;border-radius:7px;background:#fff;margin:12px 0 0}
.meeting-bottom{display:flex;align-items:center;justify-content:space-between;gap:14px;padding:16px 24px;border-top:1px solid #e2e8f0;background:#fff;position:sticky;bottom:0;z-index:1}
.meeting-bottom p{font-size:12px;line-height:1.6;color:#76859b;margin:0}
.meeting-bottom>div{display:flex;gap:9px;flex:none}
.meeting-dialog .button{min-height:36px;height:36px;font-size:12px;padding:0 15px}
.meeting-error{font-size:12px;line-height:1.6;padding:10px 14px;color:#be123c;border:1px solid #fecdd3;background:#fff1f2;border-radius:7px;margin:12px 0;overflow-wrap:anywhere}
.meeting-status{font-size:12px;line-height:1.6;padding:10px 14px;color:#47658b;border:1px solid #dce7fa;background:#f0f5ff;border-radius:7px;margin:0 0 12px}
.meeting-status .meeting-link{margin-left:10px}
.meeting-note{font-size:11px;line-height:1.7;color:#73859d;margin:12px 0 0}
.meeting-template{margin:0 0 14px;font-size:12px}
.meeting-template summary{color:#466795;cursor:pointer;font-size:12px;line-height:1.6}
.meeting-template summary:hover{color:#1e40af}
.meeting-template summary:active{color:#172554}
.meeting-form .meeting-template textarea{height:245px;margin:12px 0 8px;font-family:ui-monospace,monospace;font-size:11px}
.meeting-template code{font-size:11px;color:#466795}
.meeting-template p{font-size:11px;color:#73859d;line-height:1.8;margin:8px 0}
.meeting-badge{display:inline-flex;align-items:center;margin-left:6px;padding:2px 6px;border:1px solid #d6e3f8;border-radius:5px;background:#eff5ff;color:#1e40af;font-size:10px;line-height:1.6;font-weight:400;vertical-align:middle}
.meeting-badge[data-state=stale]{border-color:#fed7aa;background:#fff7ed;color:#b45309}
.meeting-badge[data-state=sent]{border-color:#a7f3d0;background:#ecfdf5;color:#059669}
.mail-chat .meeting-attachment:empty{display:none}
.mail-chat .meeting-attachment .meeting-file{margin:0}
.mail-chat .meeting-draft-note{font-size:11px;color:#70829a;margin:7px 0 0;line-height:1.6}
.mail-chat .meeting-body-block{font-size:13px;line-height:1.85;color:#334155;overflow-wrap:anywhere}
.mail-chat .meeting-body-block p{font-size:inherit;line-height:inherit;color:inherit;margin:0 0 14px}
.mail-chat .meeting-body-block p:last-child{margin:0}
.mail-chat .meeting-attachment[data-state=stale] .meeting-file{border-color:#fed7aa;background:#fffcf7}
.meeting-zone-field{position:relative;margin-bottom:14px}
.meeting-zone-field>label{margin-bottom:6px}
.meeting-zone-control{display:flex;position:relative}
.meeting-zone-control input{padding-right:40px}
.meeting-zone-control>button{position:absolute;right:1px;top:1px;width:34px;height:34px;padding:0;border:0;background:#f7faff;color:#627ca5;border-radius:0 6px 6px 0;font:inherit;cursor:pointer}
.meeting-zone-control>button:hover{background:#edf3ff;color:#1e40af}
.meeting-zone-control>button:active{background:#dbeafe}
.meeting-zone-field>small{display:block;margin-top:6px}
.meeting-zone-options{position:absolute;top:64px;left:0;right:0;max-height:252px;overflow:auto;overscroll-behavior:contain;z-index:10;border:1px solid #cbd9ed;border-radius:8px;background:#fff;box-shadow:0 10px 25px #223c6226;padding:5px}
.meeting-zone-options>button{width:100%;display:flex;align-items:center;justify-content:space-between;gap:10px;padding:9px 10px;background:#fff;border:0;border-radius:5px;text-align:left;color:#334155;font:inherit;font-size:12px;line-height:1.6;cursor:pointer}
.meeting-zone-options>button:hover,.meeting-zone-options>button.focused{background:#eff5ff}
.meeting-zone-options>button:active{background:#dbeafe}
.meeting-zone-options>button[aria-selected=true]{background:#eaf1ff;color:#1e40af}
.meeting-zone-options small{display:block;margin-top:2px;font-size:11px;color:#7b8ba2;line-height:1.5}
.meeting-zone-options>button>span:last-child{white-space:nowrap;color:#5b769e}
.meeting-zone-empty{padding:15px;color:#718198;font-size:12px;line-height:1.6}
.meeting-dialog :is(button,a,input,select,textarea,summary):focus-visible,.mail-chat :is(.meeting-trigger,.meeting-link):focus-visible{outline:2px solid #82a8e8;outline-offset:2px}
.meeting-dialog :is(button,.button):disabled,.meeting-link:disabled,.meeting-link[aria-disabled=true],.mail-chat .meeting-trigger:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none;text-decoration:none}
.meeting-dialog .button:disabled::after,.mail-chat .meeting-trigger:disabled::after{display:none}
.meeting-dialog :is(button,.button):disabled:hover,.meeting-link:disabled:hover,.meeting-link[aria-disabled=true]:hover{filter:none;transform:none;box-shadow:none}
.meeting-dialog .button:not(.primary):disabled,.meeting-dialog .button:not(.primary):disabled:hover{background-color:transparent;color:#1e293b;border-color:rgba(15,23,42,.11)}
.meeting-dialog .button.primary:disabled,.meeting-dialog .button.primary:disabled:hover{background-image:linear-gradient(180deg,#3b82f6,#1e40af);background-color:#1e40af;border-color:transparent;color:#fff}
.meeting-link:disabled,.meeting-link:disabled:hover,.meeting-link[aria-disabled=true],.meeting-link[aria-disabled=true]:hover{color:#315fa7;background:none}
.mail-chat .button.meeting-trigger:disabled,.mail-chat .button.meeting-trigger:disabled:hover{color:#1e40af;border-color:#b8cef5;background:#eef4ff}
.mail-chat [data-role=manual-compose][data-meeting-sending=true] .mc-editor{background:#f8faff;cursor:wait}
@media(max-width:800px){.meeting-grid{grid-template-columns:minmax(0,1fr)}.meeting-form{padding:18px;border-right:0;border-bottom:1px solid #e2e8f0}.meeting-preview{padding:18px}.meeting-dialog{width:calc(100vw - 20px);max-height:calc(100dvh - 20px)}.meeting-bottom{flex-wrap:wrap;padding:12px 18px}.meeting-head{padding:16px 18px}.meeting-head h2{font-size:17px}.meeting-bottom>div{margin-left:auto}.meeting-fields{gap:9px}.meeting-file{flex-wrap:wrap}}
@media(max-width:420px){.meeting-head,.meeting-form,.meeting-preview{padding:14px}.meeting-paper{padding:16px}.meeting-bottom{padding:12px 14px}.meeting-bottom>div{width:100%;justify-content:flex-end}.meeting-bottom .button{padding:0 10px}.meeting-zone-options{max-height:220px}}
@media(prefers-reduced-motion:reduce){.meeting-dialog *,.mail-chat .meeting-trigger,.mail-chat .meeting-link{transition:none!important;scroll-behavior:auto!important}.meeting-dialog .button:hover,.meeting-dialog .button:active,.mail-chat .meeting-trigger:hover,.mail-chat .meeting-trigger:active{transform:none}}
```

交互不依赖额外CSS：JS切换hidden、disabled、aria-invalid、aria-busy、aria-selected、data-state、data-meeting-sending、focused；不得写element.style。发件正文HTML不得包含这份页面CSS。

### S-2 弹窗完整 DOM

新`dialog`直接append到document.body，showModal进入top layer；单个MailboxMeeting实例只创建一个dialog，unmount销毁。全局id唯一，若发现已有其他owner实例先由该组件dispose旧实例后创建。点击backdrop不关闭；Esc/关闭/取消才关闭。关闭恢复本次触发按钮焦点；目标切换销毁且不把焦点抢回旧会话。下列代码即层级合同，不是设计示意；空动态容器按后续绑定表填充。

```html
<dialog class="meeting-dialog" id="meetingDialog" aria-labelledby="meetingTitle">
<form id="meetingForm" novalidate>
<header class="meeting-head">
<div>
<h2 id="meetingTitle">专家会议确认</h2>
<p id="meetingContext">生成确认邮件与日历附件</p>
</div>
<button type="button" class="meeting-close" id="closeMeeting" aria-label="关闭会议确认">×</button>
</header>
<div class="meeting-grid">
<div class="meeting-form">
<p id="meetingLoadStatus" class="meeting-status" role="status" aria-live="polite" hidden>
<span>
</span>
<button type="button" class="meeting-link" id="retryMeeting" hidden>重试</button>
</p>
<label>邮件模板<select id="meetingTemplate">
<!-- TEMPLATE_OPTIONS：API返回的id/name，后接“自定义本次模板”；对应规则见S-2 -->
</select>
</label>
<details id="templateDetails" class="meeting-template">
<summary>查看模板与变量</summary>
<textarea id="templateText" maxlength="10000" aria-label="会议邮件模板正文" spellcheck="false">
</textarea>
<small>修改仅用于本次回复；保存的模板由「邮件模板」统一维护。</small>
<p>
<code>{{expert_salutation}}</code> 专家称呼<br>
<code>{{meeting_time}}</code> 日期、时间和时区<br>
<code>{{zoom_url}}</code> 会议链接<br>
<code>{{sender_signature}}</code> 发件账号签名</p>
<button type="button" class="meeting-link" id="resetTemplate">恢复所选模板</button>
</details>
<label>专家称呼 <input id="meetingName" required maxlength="100" placeholder="Professor Basdogan">
<small>用于 Dear 后的称呼，可按专家习惯调整。</small>
</label>
<div class="meeting-zone-field">
<label id="meetingZoneLabel" for="meetingZoneSearch">会议时区</label>
<div class="meeting-zone-control">
<input id="meetingZoneSearch" type="search" role="combobox" aria-autocomplete="list" aria-expanded="false" aria-controls="meetingZoneOptions" aria-labelledby="meetingZoneLabel" autocomplete="off" placeholder="搜索国家、城市、时区或 UTC 偏移">
<button type="button" id="toggleZone" aria-label="展开时区选项">⌄</button>
</div>
<div id="meetingZoneOptions" class="meeting-zone-options" role="listbox" aria-label="会议时区选项" hidden>
</div>
<small id="meetingZoneHint">下方日期和时间均按所选时区填写。</small>
</div>
<div class="meeting-fields">
<label>开始日期<input type="date" id="meetingDate" required>
</label>
<label>开始时间<input type="time" id="meetingStart" required step="60">
</label>
</div>
<div class="meeting-fields">
<label>结束日期<input type="date" id="meetingEndDate" required>
</label>
<label>结束时间<input type="time" id="meetingEnd" required step="60">
</label>
</div>
<div class="meeting-clock" id="meetingClock" aria-live="polite">
</div>
<label>Zoom 会议链接<input type="url" id="meetingUrl" required maxlength="2048" placeholder="https://zoom.us/j/…">
<small>粘贴已创建的会议链接，包含入会密码参数。</small>
</label>
<label>发件签名<textarea id="meetingSignature" required maxlength="2000">
</textarea>
<small>默认带入回复账号签名。</small>
</label>
<label id="insertModeLabel" hidden>正文已有内容<select id="insertMode">
<option value="append">保留原文，追加确认邮件</option>
<option value="replace">替换整篇正文</option>
</select>
<small>请检查原文是否包含其他会议时间。</small>
</label>
</div>
<section class="meeting-preview" aria-label="会议邮件和日历预览">
<h3>
<span class="meeting-step">邮件正文</span>
<span>填写后自动预览</span>
</h3>
<div id="meetingError" class="meeting-error" role="alert" hidden>
</div>
<div id="meetingBody" class="meeting-paper" aria-busy="false">
</div>
<div class="meeting-file">
<span class="meeting-file-icon">ICS</span>
<div class="meeting-file-main">
<strong id="meetingFilename">
</strong>
<small id="meetingFileMeta">
</small>
<div class="meeting-file-actions">
<a id="downloadMeeting" class="meeting-link" aria-disabled="true" tabindex="-1">下载 ICS 查看</a>
<button type="button" id="inspectIcs" class="meeting-link" disabled>查看文件内容</button>
</div>
</div>
</div>
<pre id="meetingRaw" class="meeting-raw" tabindex="0" aria-label="日历文件内容" hidden>
</pre>
<p class="meeting-note">日历包含会议时间、Zoom 链接及团队签名，可下载后导入日历查看。确认后作为待发送附件加入当前回复。</p>
</section>
</div>
<footer class="meeting-bottom">
<p>确认仅填入草稿，发送仍由人工操作。</p>
<div>
<button type="button" class="button" id="cancelMeeting">取消</button>
<button type="submit" class="button primary" id="applyMeeting" disabled>确认并填入回复</button>
</div>
</footer>
</form>
</dialog>
```

| 动态区域/元素 | 逐字结构或绑定值 |
|---|---|
| TEMPLATE_OPTIONS | API每项创建`<option value="id">name</option>`；name用textContent；末项`<option value="custom">自定义本次模板</option>`，只有至少1项时存在；custom保留baseTemplateId，不作为templateId发送 |
| meetingContext | `{专家显示名} · 回复账号 {resolvedAccountCode}`，姓名为空用邮箱；textContent |
| meetingLoadStatus | span固定状态文案见T2；retryMeeting仅网络/服务错误时解除hidden；不另加DOM |
| meetingClock | 成功`<b>北京时间</b><br><span data-role="china-time"></span><br><span data-role="meeting-duration"></span>`，两个span textContent为server chinaTime、`会议时长 30 分钟`；无有效结果textContent=`时间待确认` |
| meetingBody | 成功只插server htmlBody；无结果textContent=`请填写左侧配置后预览邮件。`；错误=`请修正左侧配置后预览邮件。` |
| meetingFilename/meetingFileMeta | 文件名与`日历事件 · {分钟} 分钟 · {(byteLength/1024).toFixed(1)} KB`；无结果=`日历附件待生成`/`填写完整后可下载` |
| meetingRaw | textContent=icsText，不innerHTML；展开按钮文字`查看文件内容`↔`收起文件内容` |
| meetingError | message textContent；aria-invalid只标本地明确校验的字段；服务端无字段标识时只显示错误区；无结果hidden |
| insertModeLabel | 只有已有正文且不能原位替换未手改块时显示；默认append；其它隐藏 |
| meetingTitle/applyMeeting | 新建`专家会议确认`/`确认并填入回复`；编辑`编辑会议确认`/`更新并填入回复` |

禁止项：本节没有新的视觉class；只引用S-1内完整规则。`form`没有method=dialog，submit必须preventDefault并执行草稿事务，不允许提交刷新页面。

### S-3 人工回复入口、正文块与附件卡

在当前manualComposeHtml的四个rich按钮之后插入下面trigger；editor后、mc-compose-footer之前插入attachment容器；原主题和所有既有DOM顺序保持。组件不存在时不生成trigger/容器。

```html
<button class="button meeting-trigger" type="button" data-action="mc-open-meeting"><span class="meeting-icon" aria-hidden="true">▦</span>会议确认</button>
<!-- 当前 mc-editor 保留；仅内部插入下面一个会议块 -->
<div class="meeting-body-block" data-meeting-block="true"></div>
<!-- editor之后 -->
<div class="meeting-attachment" data-role="meeting-attachment" data-state="ready">
  <div class="meeting-file">
    <span class="meeting-file-icon" aria-hidden="true">ICS</span>
    <div class="meeting-file-main">
      <strong><span data-role="filename"></span><span class="meeting-badge" data-state="ready">待发送附件</span></strong>
      <small data-role="file-meta"></small>
      <div class="meeting-file-actions">
        <a class="meeting-link" data-action="mc-download-meeting">下载</a>
        <button class="meeting-link" type="button" data-action="mc-edit-meeting">编辑会议</button>
        <button class="meeting-link" type="button" data-action="mc-remove-meeting" aria-label="移除日历附件">移除</button>
      </div>
    </div>
  </div>
  <p class="meeting-draft-note">修改会议时间或链接请使用「编辑会议」，同步更新正文和附件。</p>
</div>
```

无附件：容器清空`replaceChildren()`，:empty隐藏；不能保留上一个对象URL。ready metadata=`2026-09-11 · 10:00–10:30 · Europe/Istanbul · 30 分钟`，跨日两端写日期。stale：容器和badge data-state=stale、badge文字“待重新确认”、note“会议正文或回复目标已变化，请编辑会议重新生成，或移除日历附件。”；仍允许下载原快照核对，不得把它当新目标已确认结果。发送中：manual-compose data-meeting-sending=true、editor contenteditable=false、subject/tools/会议操作/发送按钮disabled，结束恢复原属性。无calendar发送沿旧行为。

### S-4 搜索候选与无结果

复用S-1 meeting-zone-*规则，不使用浏览器datalist替代。每项完整结构：

```html
<button type="button" id="meeting-zone-option-N" role="option" aria-selected="false" data-zone="Europe/Istanbul" tabindex="-1">
  <span><span data-role="zone-label">土耳其 · 伊斯坦布尔</span><small>Europe/Istanbul</small></span>
  <span data-role="zone-offset">UTC+3</span>
</button>
```

N为本次结果内0起序号；selected末span追加` ✓`，键盘active只有focused class；input aria-activedescendant指向该id，保持焦点在input。无结果唯一`<div class="meeting-zone-empty">没有匹配的时区，请尝试英文城市名或 UTC+3。</div>`。别名全文：[timezone-aliases.target.js](meeting-confirmation-evidence/timezone-aliases.target.js)，其数据移至01服务；浏览器不维护另一套时区值。默认搜索提示“搜索国家、城市、时区或 UTC 偏移”，selected label=`labelZh (offsetLabel)`，hint=`zoneId · 日期和时间均按此时区填写`。

### S-5 已发送附件卡与状态覆盖

仅新calendarAttachment非空的SENT OUTBOUND MAIL_RECORD，在renderMessage的bodyHtml后、原attachmentHtml前插入：

```html
<div class="meeting-file" data-role="sent-meeting-attachment">
  <span class="meeting-file-icon" aria-hidden="true">ICS</span>
  <div class="meeting-file-main">
    <strong><span data-role="filename"></span><span class="meeting-badge" data-state="sent">已发送日历</span></strong>
    <small data-role="file-meta"></small>
    <div class="meeting-file-actions"><a class="meeting-link" data-role="calendar-download" data-action="mc-download-sent-meeting" download>下载 ICS</a></div>
  </div>
</div>
```

filename/text为03 metadata；meta=`日历事件 · {size.toFixed(1)} KB`；a.href为注入contextPath拼03同源URL，用URL构造后必须origin=location.origin且去除该contextPath后的pathname符合固定下载路由，不接受javascript/外域。原材料卡、翻译、标签、footer不变。T4下载适配的瞬时节点固定为`<a hidden download></a>`，只绑定Blob href/安全文件名，点击后移除；原生hidden无需新class或inline style，亦归本S-5。错误下载通过下述新宿主binary下载适配捕获并使用既有hostShowStatus，不导航离开当前草稿，不新增dialog或未知class。以上所有节点均由S-1规则或现有button样式覆盖，空态/加载/错误/ready/stale/sending/sent均有确定DOM与样式。

## 现状审计

[完整审计](meeting-confirmation-audit.md) D5/D7及[最新逐字基线](meeting-confirmation-evidence/frontend-before.md)为本节组成部分。前次快照保存在frontend-before.initial.md；已检测到同日mailbox-refinement落地，接入定位更新如下：

- 当前manual renderer=`renderManualSectionInto:2126`、manualComposeHtml:2163；saveDraftFromInputs:2702、adoptAssembly:2724、sendManualReply:2753、retargetManual:2890、unmount:3317、message:1514。方法名+data-role/action是定位合同，最终哈希索引可复核。
- 当前缓存schema：外层`sessionStore`的key=user|accountScope|contactId（conversationCacheKey:215）；value含items/anchor/scroll/drafts Map/lastUsed，LRU上限10会话、消息缓存上限500（mailbox-chat.js:40..41）；本期保留既有淘汰语义，不承诺被淘汰/硬刷新恢复；draft key仍contact:processing:account，value subject/html/text/qa/updatedAt。写路径完整：upsertConversationRecord:237、setDraft:416、deleteDraft:422、saveDraftFromInputs:2702、adoptAssembly:2724、retargetManual:2890、发送成功:2790；saveConversationState:1849将map持入cache。读路径：getConversationRecord:226、current/ensureDraftsMap:389/399、getDraft:410、selectExpert恢复:1300、manualComposeHtml、send、checkInboundChangeQuiet、retarget、unmount保存；逐行见evidence/frontend-latest-paths.txt。不是localStorage/sessionStorage，无硬刷新持久化承诺。
- 现有senderAccount筛选服务activeAccountCodes:408实际是所有非SIMULATOR账号，没有enabled条件；新下载/预览不能误加enabled过滤。原标签服务批量读、主题解码、专家标签投影保持。
- 当前`.mc-editor`最后规则100..240px、manual section白底；新class衍生隔离，不就地改老class，所以无需扩大到所有老class使用点。新CSS文件避免现有mailboxChatStyle精确全文测试冲突。复用class完整规则/所有当前宿主节点见frontend-before.md。
- 交互点：IP-6表单→草稿→采用/切换/目标更新→发送；IP-7dialog→搜索/键盘/布局/销毁；IP-4存档→历史卡；IP-5原材料不变；IP-8脚本缺席→05注册后挂载。

## 实现方案

### T1 独立组件与适配接口（I-1/I-2/I-6；S-1..S-5）

新增meeting-confirmation.js（普通IIFE，浏览器window.MailboxMeeting、Node module.exports测试入口），不加npm依赖/构建链。新增meeting-confirmation.css原样复制S-1。app.js:14414的首次mountOptions增加contextPath，后续mount(list,{})保留已有值；mailbox-chat实例初始化保存options.contextPath并传组件，不能从window.contextPath猜（它是const词法绑定）。JSON API已经在app.js:1478添加前缀，不重复添加；历史a链接需`${contextPath}${downloadUrl}`，和expert-materials.js:699相同约定。只声明以下适配：

```javascript
MailboxMeeting.create({
  api, // hostApi()返回的现有JSON API函数
  contextPath, // app.js传入的部署前缀，空字符串或/talent
  onApply, // (capturedTargetKey, {preview:MeetingPreviewResponse, mode:"append"|"replace", capturedEditorRevision}) => boolean
  onStatus // 现有hostShowStatus
});
// controller methods:
controller.open({ ownerKey, targetKey, contactId, processingId,
  senderAccountCode, expertLabel, editorHtml, editorText, savedMeeting });
controller.close({restoreFocus:true});
controller.dispose();
// pure exports: filterZones(zones,query), normalizeMeetingText(text),
// sanitizeDraftHtml(html,document), planMeetingInsertion(editor,saved,result,mode)
```

ownerKey用当前会话cache完整key；open捕获触发button，通过document activeElement保存。组件onApply返回false意味着目标/修订不匹配，显示“回复目标已变化，请重新打开会议确认”，不提交旧结果。有contextPath=/talent时JSON API路径和实际下载均为/talent/api/...；不拼成/api根路径。没有window.MailboxMeeting时manualComposeHtml不渲染trigger，任何现有动作不调用未定义对象；05之前测试可注入组件验证，不靠修改index提前激活。

### T2 表单生命周期、状态机（I-1/I-2/I-4/I-5/I-7；S-2/S-4）

1. open先saveDraftFromInputs，捕获owner/key/编辑器revision，创建dialog显示，拉options与zones（新建无日期先用当日中国日期只作目录偏移查询，日期控件仍空）。并行结果分别序号保护；状态“正在加载会议配置…”，确认/下载/表单disabled，取消/关闭可用；请求失败显示“会议配置加载失败，请重试”，重试按钮可用；无模板显示01固定文案，disabled，无伪默认模板。
2. 成功新建默认值按01 I-4；已有saved用已保存input和templateBody，仍读取options检查template存在/启用；若无效显示400文案，允许重新选择有效模板。日期/Zoom不复制示例。日期start变化且endDate为空/早于startDate时endDate=startDate，不静默改endTime；用户已有合法跨日endDate保留。时长本期不设自动30分钟默认，须选结束时间。
3. input事件只更新state，不重建整个form，不丢输入法/焦点；表单revision++，pendingPreview=null、撤销Blob、确认/下载disabled。完整且原生格式校验通过后300ms debounce POST preview；loading文案“正在生成邮件与日历…”，aria-busy=true。文字输入时不触发所有zones重载；只有startDate change重新GET目录。发送前以server响应为准，过期响应不覆盖新值。
4. preview ready填入S-2右栏，Blob `new Blob([icsText],{type:'text/calendar;charset=UTF-8'})`；保存响应sha/input/targetKey。下载链接href=URL.createObjectURL/blob、download=filename；禁用a移除href/download、aria-disabled=true、tabindex=-1并拦截click；ready恢复tabindex=0。更改配置/close/dispose/替换下载URL均revoke；实际点击后不立即revoke造成下载中断。
5. 错误：400显示服务端message，保留所有左栏值，只清本轮右栏/Blob；网络错误“预览生成失败，请重试”，可点重试；取消之前草稿没变。模板选择有效id把库body填入textarea；选custom只是展开编辑并保留baseTemplateId；textarea修改自动选择custom；“恢复所选模板”恢复baseTemplateId在options中的body。UI字段校验只是便利，server规则01为唯一权威。
6. 搜索：NFKC、lowercase、去空白、−→-；支持UTC+03:00→UTC+3与UTC+05:30→UTC+5:30规范化；在id/labelZh/aliases/offsetLabel联合包含匹配，不截断结果前N条。点input显示全部，输入筛选；ArrowDown从首项、ArrowUp从末项起，循环；Enter选active，若无active且仅1项则选该项，多项不自动选；Escape有list时只关闭list并stopPropagation，第二次才关dialog；Tab/外点恢复selected label；清空搜索不清selected id；没有结果不隐藏当前有效选中值。
7. 确认只接受当前owner/key/formRevision最后一次ready response；若输入尚请求中直接disabled，不悄悄采用上一结果。若本地时钟判定startUtc已过去，meetingLoadStatus提示“会议时间已过去，请核对”，不阻止下载/应用。此提示不成为后台校验。

### T3 草稿事务与正文一致性（I-1/I-2/I-3/I-6/I-7；S-3）

readManualValues/saveDraftFromInputs保留新增meeting深拷贝；已有值不要通过只列subject/html/text/qa的旧save丢弃。snapshot字段：input=01规范化meeting，preview={htmlBody,textBody,attachment,startUtc,endUtc,meetingTime,chinaTime,durationMinutes}，blockHtml/blockText是实际插入后DOM/可见文字规范化基线，state=ready/stale，revision是单调本地整数。更新snapshot不写数据库。

apply：创建div.meeting-body-block[data-meeting-block=true]放server htmlBody。空editor replaceChildren(block)；已有未手改单块 replaceWith(block)；其它正文默认append(br,block)，显式replace则replaceChildren(block)并清旧qa。append保留qa但edited仍按原baseline比较。**如果已有会议块已手改**，旧块也保留会制造两套会议；因此默认append仅用于首次生成。已手改且带旧meeting时确认按钮禁用，提示“会议正文已手动修改；选择替换整篇正文，或取消后移除日历附件。”；选replace方可应用。不新增第三种隐式策略。无论替换/追加后保存并渲染一张卡，不发邮件。

edit：有且仅一块，且当前blockHtml等于保存基线，原位更新；外部段落可编辑。editor input检测块数量/normalize(block.innerText)与基线，变化则stale；仅格式变化且文本相同可以更新blockHtml基线仍ready；手动删除块则stale。服务端03最终文本校验为第二道保障。remove：确认按钮无需二次弹窗，置meeting=null、卡消失、撤销draft Blob，**正文保留**，提示“已移除日历附件，正文保留”；块标记可保留以便样式，后续新建视为普通已有正文，用户自己决定append/replace。

草稿恢复：当前只恢复draft.text的缺口在manualComposeHtml修复。sanitizeDraftHtml通过`template.content`遍历，只保留div/p/br/b/strong/i/em/u/ul/ol/li/a/span；删除script/style/iframe/object及其内容，其它不允许标签unwrap；仅a保留经过URL协议校验的https/http/mailto href和target=_blank/rel=noopener noreferrer；保留自身meeting-body-block class与data-meeting-block=true，其它属性/所有style/on*删除；禁止仅正则清HTML。生成基线同样经过此函数，避免restore后无意义stale；普通rich格式按白名单保留。HTML缺省则escapeText(draft.text)。这只规范本页编辑草稿，不把收到的HTML渲染进编辑器。

adoptAssembly仍原语义全文替换，先清meeting/Blob再调用原采用并设置新QA；提示中追加“原日历附件已移除”仅当原来有附件。retarget迁移完整draft，但meeting.state=stale、保留旧input供改、关闭dialog并撤旧modal URL，卡提示重新确认。编辑重生时按新目标重拉options；若senderAccountCode改变，将签名默认重置为新账号拼接值，用户可再次改；新target重新计算UID/digest，旧sha不能直接发送。新来信但用户选保持旧目标则不改变现有meeting。

### T4 发送锁与历史卡（I-2/I-4/I-7；S-3/S-5）

仅带meeting时：发送前state必须ready；再次比较现会议块text一致；捕获`draftsMap=ensureDraftsMap()`、owner/key、revision、processingId和requestBody，不在Promise回调再次取currentDraftsMap。payload加meeting.input与previewAttachmentSha256=preview.attachment.sha256；只加字段，不改变senderAccountCode:null/QA/RAG/safety适配。用模块级inFlight集合按owner+targetKey锁同一目标（volatile，不写缓存/DB），渲染同目标时恢复锁。发送中用S-3锁当前editor/subject/tools，仍允许选择其他专家；切换后新目标不被旧promise解锁或清QA。

sent=true：仅当capturedMap当前key的revision与提交相同才delete；如果不同保留新草稿并标stale。清该ownerKey的inFlight标记，即使当前视图已销毁也释放；只有owner/key仍匹配且未disposed才清当前QA并调用afterSuccessfulSend(key)，否则仅下一次进入由正常刷新读新发件。失败/取消/UNKNOWN：不删draft、不自动重试，解除当前匹配目标锁，恢复输入；adapter自己的错误/安全确认照旧。不带meeting仍原发送路径，不顺带改全部旧业务。

历史renderMessage只加S-5片段，使用03 metadata直接链接；新API downloadUrl前缀固定，ID数值校验。历史a点击事件preventDefault；新增app.js宿主`mcHostDownloadCalendar(relativePath, filename):Promise<void>`：校验relativePath匹配03固定路由、filename符合01安全文件名；用fetch(`${contextPath}${relativePath}`)读取二进制响应，先await handleAuthResponse(response)，非ok从JSON.message读取错误并throw，调用方hostShowStatus(error.message,"error")，不能调用现有JSON api解析ICS。成功用response.blob()创建临时a[download]点击，完成点击后在setTimeout(1000)中移除节点并revoke ObjectURL；不用window.open，不离开当前页面。此宿主是本期新增，引用的已有证据是app.js:1478登录响应处理与expert-materials.js:699前缀约定。人工验收浏览器真实保存文件确认，捕获Network成功不等于已下载。

### T5 资源生命周期与验证（I-1..I-7；S-1..S-5）

组件controller在teardownConversationSubViews/selectExpert、账号过滤变化、unmount时close/dispose并abort在途请求（hostApi若无signal能力用seq兜底）；移除document事件和dialog，revoke所有该视图Blob。**草稿缓存不清**，返回时按其icsText重建URL。当前manual区同目标刷新只换必要节点，不能因请求每次重建编辑器。

三个新增测试文件的职责固定：meetingConfirmation.test.js负责纯搜索/DOM清洗/时间结果绑定/草稿插入/键盘和异步状态；meetingConfirmationIntegration.test.js负责真实mailbox-chat实例+stub API/adapter的草稿切换/QA/retarget/发送锁/历史卡，无真实SMTP；meetingConfirmationStyle.test.js读取CSS与证据逐字比对/DOM class白名单/禁inline/资源未注册时降级。测试中挂载真实组件调用事件，不只对源码includes作功能验收。真实浏览器在05激活后执行下方A-n并截图。

## 变更文件清单

| # | 文件（仓库根目录相对路径） | 操作 |
|---|---|---|
| 1 | `src/main/resources/static/meeting-confirmation.js` | 新增组件及纯函数 |
| 2 | `src/main/resources/static/meeting-confirmation.css` | 新增S-1全文 |
| 3 | `src/main/resources/static/mailbox-chat.js` | manual/draft/send/history边界接入 |
| 4 | `src/main/resources/static/app.js` | mountOptions前缀及专用binary下载适配 |
| 5 | `src/test/js/meetingConfirmation.test.js` | 新增组件行为 |
| 6 | `src/test/js/meetingConfirmationIntegration.test.js` | 新增宿主行为 |
| 7 | `src/test/js/meetingConfirmationStyle.test.js` | 新增样式合同 |

## 验收标准

- I-1：mock API计数断言确认/取消不调用发送、无业务写接口；原send按钮才传meeting。
- I-2：延迟A的options/preview/send后切B，B文本/QA/meeting不变；同用户不同accountScope、不同用户、同expert不同processing三矩阵；销毁无泄漏；capturedMap和revision回归。
- I-3：首次/追加/replace/未手改更新1块1卡；手改块stale禁止含附件发送；remove只删附件；QA append保留/replace清空；restore保留允许的B/I/list/link。
- I-4：response中sha、icsText、text/html对应同一revision；根路径和/talent两种上下文下载实际字节=server，不能再有MeetingCore/localInstant算法；失败立即取消旧可发送preview。
- I-5：土耳其/Türkiye/istanbul/Europe/Istanbul/UTC+03:00命中；+5:30半小时、负偏移、空结果；键盘active/selected分离与两级Esc；日期变化offset重新请求不改zone。
- I-6：无preview fetch拦截/实际默认值硬编码专家URL；placeholder中Professor Basdogan只是用户样例提示，不作为值提交；恶意HTML经sanitizer只显示文本/去事件；全局CSS及mailbox-chat.css diff为空。
- I-7：ready/stale/null/sending状态完整；复用卡下载失败不改草稿；仅metadata出现历史卡，材料原count不变。
- S-1：新CSS与target.css逐字相等；无未声明class/inline style；正常/hover/active/disabled/focus/error/loading/mobile/reduced-motion逐项计算样式核对。
- S-2：dialog唯一且body直属、form层级/id/文案/动态绑定与合同一致；loading取消可用，预览p color=#334155/font13px/1.85。
- S-3：触发顺序B/I/列表/链接/会议确认；editor原100..240px不改，附件在footer之前；stale琥珀、ready蓝、发送禁用45%不点击。
- S-4：选项DOM与aria严格对应，400+结果不是截断前20；不能把自由搜索词当zone；空结果固定文案。
- S-5：历史卡在body后原材料前，仅新字段控制；SENT徽标green；旧材料/翻译/标签/处理/滚动测试通过。
- 命令：`node --test src/test/js/meetingConfirmation*.test.js`，`node --test src/test/js/*.test.js`，`node --check src/main/resources/static/meeting-confirmation.js`与mailbox-chat.js；整体`mvn test`按阶段只需一次。浏览器不以DOM stub替代。

## 人工验收清单

统一前置：隔离验收环境完成01..05；普通测试用户登录，SMTP仅验收邮箱；测试专家A姓名Cagatay Basdogan有真实来信，账号LuKai字段senderName=LuKai、senderTitle=Customer Care Officer、teamName=Qingfei Tech Talent Team、countryName=China；测试专家B另有来信。记录A/B页面显示的contactId/processingId，不假定生产是#182。示例配置：Professor Basdogan、Europe/Istanbul、2026-09-11 10:00–10:30；Zoom使用用户本次所给完整URL。浏览器窗口1440×1000，缩放100%。

### A-1: 入口及完整样式
- 前置条件：统一前置，人工回复为空。
- 操作步骤：1. 打开人工回复。2. 点击链接右侧会议确认。3. 查看加载、表单和预览。4. 将窗口依次改800、390px宽。
- 预期结果：入口32px高；dialog宽1080px上限、圆角14、左45%右55%、输入36px高；800px及以下单列；390px无横向溢出；取消/确认底部可达；旧列表/筛选/管理/翻译仍在。
- 覆盖：R1/M3；I-6；S-1/S-2/S-3；IP-7/IP-8

### A-2: 搜索时区
- 前置条件：统一前置，弹窗已打开。
- 操作步骤：1. 输入“土耳其”。2. 看候选并按下箭头/Enter。3. 依次搜索Europe/Istanbul、UTC+03:00。4. 搜索zzzz-no-zone后按Esc、Tab。
- 预期结果：中文候选土耳其 · 伊斯坦布尔/Europe/Istanbul/UTC+3；明确选择后表单保存IANA；无匹配显示固定提示；Esc/Tab恢复原选中标签，时区仍Europe/Istanbul；未执行发送。
- 覆盖：R1；I-1/I-5；S-4；IP-7

### A-3: 预览下载后确认
- 前置条件：统一前置，填样例。
- 操作步骤：1. 设置起止与Zoom/称呼。2. 等预览完成。3. 下载ICS并在日历打开。4. 点击确认并填入回复。
- 预期结果：右栏英文包含Friday, September 11, 2026和UTC+3；北京时间15:00–15:30/时长30分钟；文件名meeting-2026-09-11-Professor-Basdogan.ics；正文可读13px深灰；确认后1块1卡，主题未改，邮箱未新增邮件。
- 覆盖：R2/M1；I-1/I-3/I-4；S-2/S-3；IP-6

### A-4: 追加、更新、取消
- 前置条件：正文先输入“Please also review the agenda.”并加粗；样例生成后仍未发送。
- 操作步骤：1. 首次生成选默认追加。2. 编辑会议改10:30–11:00再确认。3. 再次改11:30但点取消。4. 移除附件。
- 预期结果：原加粗句保留；第二次仍1个会议块1卡，日历北京时间15:30–16:00；取消保留10:30–11:00；移除后0卡但英文正文保留；B/I/列表/链接仍可用。
- 覆盖：R2/M1；I-1/I-3；S-3；IP-6

### A-5: 手改和QA采用
- 前置条件：生成A会议ready卡，工作台有可采用的测试草稿。
- 操作步骤：1. 手工将会议块10:00改11:00。2. 尝试发送。3. 编辑会议，选择替换整篇正文恢复。4. 再采用工作台草稿。
- 预期结果：步骤1徽标“待重新确认”，步骤2阻止带附件发送；替换后ready；采用工作台后旧ICS卡消失、新QA草稿存在，并提示原日历附件移除；没有自动发件。
- 覆盖：M1；I-3/I-7；S-3；IP-6

### A-6: 隔离与晚响应
- 前置条件：A/B均有不同主题/正文/会议；浏览器Network延迟预览响应3秒。
- 操作步骤：1. A编辑会议请求未回时切B。2. 返回A。3. 更换账号筛选再返回。4. A收到新来信时选择切新目标。
- 预期结果：B草稿不出现A称呼/附件，返回A恢复原富文本与附件；新目标卡标“待重新确认”，必须重新预览；不同账号筛选缓存隔离，取消dialog无串写。
- 覆盖：M2；I-2/I-7；S-2/S-3；IP-6

### A-7: 真实发送与历史下载
- 前置条件：A样例ready卡；测试SMTP允许投递，延迟响应3秒；B已有草稿。
- 操作步骤：1. 点击发送人工回复，必要时走原安全确认。2. 等待时切B。3. 完成后返回A并刷新会话。4. 下载已发送日历。
- 预期结果：A发送中编辑器不可改；B正文/QA保留；A成功后已提交draft清理；历史卡“已发送日历”，实际收件1封/1个ICS；下载SHA等于预览；原材料数量不增加。
- 覆盖：R3/M2/M3；I-2/I-4/I-7；S-3/S-5；IP-3/IP-4/IP-5/IP-6

### A-8: 错误/取消/无来信回归
- 前置条件：可禁用模板、断开网络；另有仅发件无来信专家C。
- 操作步骤：1. 禁用专用模板打开弹窗。2. 恢复模板后断网生成。3. 填New_York 2026-03-08 02:30。4. 关闭弹窗后打开C。5. 不带日历发普通测试回复。
- 预期结果：无模板明确提示；网络失败重试可用且确认disabled；DST提示不存在，旧preview不可下载；C仍只有原模板跟进入口；普通回复走旧安全流程且无ICS。
- 覆盖：M1/M2/M4；I-1/I-4/I-6；S-1/S-2；IP-1/IP-6/IP-8
