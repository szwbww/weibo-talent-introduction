# 07 · 富文本仅图标上传、多附件草稿与会话展示

状态：待审批；依赖06。一个前端子系统，4文件。共享草稿对象只新增outboundAttachmentDraft一个字段；其内部revision/items为本需求的完整对象，不另加平行状态真值。

## 需求描述

“链接”右边新增仅回形针图标的附件按钮；支持任选类型、多选、上传、移除和下载。成功发送后对话消息显示可下载文件。保留B/I/列表/链接、会议确认、跟进邮件的原作用，以及会议ICS、草稿切换、安全确认和上下文路径。范围不含自动上传即发送、插图内嵌、文件内容解析、跨刷新草稿持久化。

## 关键不变量

### Invariant I-1: 图标唯一入口、任意类型
- Rule: 按钮没有“附件上传”等可见文字，只有16px回形针SVG；title和aria-label均“上传附件”。隐藏file input有multiple且没有accept，不按扩展名过滤；选择文件不会发邮件。取消系统选择器不改草稿；同文件可移除后重选。
- Applies to: manualComposeHtml、点击/change委托。
- Violation consequence: 不符合用户最后确认或限制文件类型。
- 来源: user request。

### Invariant I-2: 上传状态与草稿归属
- Rule: 每份草稿outboundAttachmentDraft={revision,items}；item为本地key、状态uploading/ready/failed、服务端metadata或错误；原始File只在上传期间临时持有，结束释放。发起异步前捕获sessionUser/accountScope/contactId/targetKey、原draftsMap及item key；返回只更新仍存在的同owner同item，不污染后来切换的专家；已移除项不复活。Map被LRU淘汰后忽略迟到回包。所有草稿重建写点显式保留此字段。
- Applies to: saveDraftFromInputs、writeDraftWithMeeting、ensureOutboundRequestId、removeMeetingFromDraft、retargetManual、发送回调、session缓存。
- Violation consequence: 会议覆盖附件、附件串专家、删除后回包复活。
- 来源: K-mailbox-draft-cache-owner-capture。

### Invariant I-3: 发送使用已上传快照
- Rule: 只提交ready条目的attachmentIds，顺序保持；有uploading或failed时禁止发送，失败项必须重新选择或移除，不能悄悄漏掉。上传/移除/替换导致附件语义变化时使会话requestId失效；程序性草稿保存、上传同一条目完成、失败重试/安全确认本身不自动换已有requestId。发送期间当前owner编辑/附件增删禁用；其他专家仍可编辑。成功只清捕获owner里仍等于发送快照的草稿；失败、取消确认、UNKNOWN均保留，不修改requestId。
- Applies to: 上传/删除/保存草稿/双发送分支/成功失败回调。
- Violation consequence: 文件漏发、重复邮件、清空别人的新草稿。
- 来源: K-manual-send-unknown-must-converge / K-manual-send-fingerprint-complete-identity。

### Invariant I-4: 会话原件下载
- Rule: 已发消息只消费06的outboundAttachments，不从当前草稿或正文拼文件；草稿与已发卡分别用各自downloadUrl。href加instance.options.contextPath，与ICS已发卡方式一致；从/talent部署下载不能丢前缀。名称用escapeText/textContent，a为download；不创建假下载文本或借Blob重新合成原件。
- Applies to: renderMessage、草稿卡、下载链接。
- Violation consequence: 下载404、文件被替换、XSS。
- 来源: K-download-context-path-host-injection。

### Invariant I-5: 旧能力与缓存界限
- Rule: 会议ICS与通用附件分别保留独立字段/卡片；仅移除其中一种不能删另一种。保留旧session Map容量≤10会话、每会话≤500条消息；不写localStorage文件/正文、不承诺刷新页面恢复草稿。成功消息附件来自服务器，刷新仍能下载。工具栏新顺序B/I/列表/链接/回形针/会议确认/跟进（原条件显示不变）。
- Applies to: 草稿存储、工具栏、renderMessage、会议填入/移除。
- Violation consequence: 回归会议或跟进、无限持有文件对象。
- 来源: K-mailbox-draft-cache-owner-capture / K-mailbox-chat-css-byte-contract。

## 样式契约

### S-1：仅图标按钮
- 复用styles.css:802 .button及原hover/active；mailbox-chat.css:63 .mc-editor-tools gap6；不改这些规则。新outbound-upload见S-2逐字块。
- 在createLink按钮之后、meetingTrigger之前插入以下完整DOM（input无需新class）：

```html
<button class="button outbound-upload" type="button" data-action="mc-upload-attachment" title="上传附件" aria-label="上传附件"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l10.6-10.6a4 4 0 0 1 5.66 5.66L9.41 17.41a2 2 0 0 1-2.83-2.83l9.19-9.19"/></svg></button>
<input type="file" data-role="outbound-file-input" multiple hidden>
```
- 不显示“上传附件”按钮正文或文件类型筛选；title/aria文字属于可访问说明，不违反“仅图标”。

### S-2：文件卡、状态与下载
- 新样式全文原样追加styles.css；不修改mailbox-chat.css或meeting-confirmation.css：

```css
/* meeting-mail-07: outbound files */
.outbound-upload{width:32px;min-width:32px;padding:0}
.outbound-upload svg{width:16px;height:16px;display:block;flex:none}
.outbound-files{display:flex;flex-direction:column;gap:8px}
.outbound-file{display:flex;align-items:center;gap:10px;padding:10px 12px;border:1px solid #dce4ef;border-radius:7px;background:#fff;min-width:0}
.outbound-file-icon{display:flex;align-items:center;justify-content:center;flex:none;width:32px;height:32px;border-radius:7px;background:#eff5ff;color:#1e40af}
.outbound-file-main{display:flex;flex-direction:column;gap:3px;min-width:0;flex:1}
.outbound-file-name{overflow-wrap:anywhere;color:#334155;font-size:12px;line-height:1.6}
.outbound-file-meta{color:#64748b;font-size:11px;line-height:1.6}
.outbound-file-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
.outbound-file-link{padding:0;border:0;background:transparent;color:#1e40af;font:inherit;font-size:12px;text-decoration:none;cursor:pointer}
.outbound-file-link:hover{text-decoration:underline;color:#1e3a8a}
.outbound-file-link:active{color:#172554}
.outbound-file[data-state=failed]{border-color:#fecdd3;background:#fff1f2}
.outbound-files :is(button,a):focus-visible,.outbound-upload:focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
.outbound-files button:disabled,.outbound-upload:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
.outbound-files[hidden]{display:none!important}
@media(max-width:760px){.outbound-file{flex-wrap:wrap}.outbound-file-actions{width:100%;padding-left:42px}}
```
- DOM草稿放在现有会议附件卡之后、发送footer之前；已发放在消息正文与ICS卡之后、原入站材料摘要之前。相同文件骨架：

```html
<div class="outbound-files" data-role="outbound-draft-files" aria-live="polite">
  <div class="outbound-file" data-state="ready" data-file-key="">
    <span class="outbound-file-icon" aria-hidden="true">↧</span>
    <div class="outbound-file-main"><strong class="outbound-file-name"></strong><small class="outbound-file-meta"></small></div>
    <div class="outbound-file-actions"><a class="outbound-file-link" data-role="outbound-download" download>下载</a><button class="outbound-file-link" type="button" data-action="mc-remove-attachment" aria-label="移除附件">移除</button></div>
  </div>
</div>
```
- 已发容器data-role=outbound-sent-files、data-state=sent，不渲染移除按钮。uploading只显示“上传中…”和“移除”；failed显示“上传失败，请重新选择文件”和“移除”；ready显示大小＋“待发送”；sent显示大小＋“已发送”。错误详情只显示安全业务文案，无堆栈/磁盘路径。没有文件时hidden，不渲染空卡。
- 禁止inline style、未列class、既有规则修改。新增class全部出现在styles.css，满足现有字面白名单测试。

## 现状审计

### 工具栏与样式
- 改动前逐字基线在[代码证据](meeting-mail-evidence.md#前端改动前基线)。mailbox-chat.js:2668原工具栏没有file input；:1869已发ICS下载用contextPathValue；:1890 renderMessage仅正文/ICS/原材料附件。
- 样式token：主色#1e40af、亮色#3b82f6；button32px/字12px/圆角7px；工具栏gap6；编辑器min160px/max360px/边框#dce4ef。S-1保持控件高度，S-2以同色值增加文件卡。既有规则不改，因此不影响其他使用点。
- meetingConfirmationIntegration.test.js:1412/1413固定tools[4]会议、tools[5]跟进；新增后明确更新为5/6，并断言4是回形针。不删旧会议/跟进测试。

### 草稿与传输
- sessionStore的write/read全集见evidence.draft_writes。saveDraftFromInputs:3212/3249以字段白名单重建对象；writeDraftWithMeeting:3891也新建对象，所以两者必须显式保留outboundAttachmentDraft。ensureOutboundRequestId:3289和removeMeetingFromDraft:3979用Object.assign；retargetManual:4330迁移同专家的新来信目标。所有路径均纳入I-2测试。
- app.js:1540 api默认JSON header，但...options覆盖整个headers；因此上传可直接hostApi(url,{method:'POST',headers:{},body:formData})让浏览器写multipart边界，继续复用认证/错误处理；无需新增fetch客户端或修改app.js。
- sendManualReply:4110选择inbound/conversation，:4132/:4164构造两种body，:4175已捕获会议owner/Map/revision；非会议成功分支:4230仍按当前Map deleteDraft，通用附件必须走捕获快照保护分支，不能照搬当前目标清理。
- submitManualRichReply（app.js:14576）安全重提用 {...body,safetyWarningConfirmed:true}，可自然保留attachmentIds，不修改审批文案。
- IP-1：file选择→04上传→草稿；IP-2：所有草稿重建→附件保留；IP-3：两个发送入口/安全确认→06附件；IP-4：06消息快照→带contextPath下载；IP-5：异步返回→正确owner。

## 实现方案

1. mailbox-chat按S-1插入唯一按钮/input；change顺序队列上传（限制一次仅1文件上传，避免多文件同时占内存），预检查04同样大小/数量，总量过限直接提示，不清现有成功项；服务端仍最终裁决。每文件独立POST与状态；上传期间发送禁用，取消选择零请求（I-1/I-2，S-1/S-2）。
2. 增加outboundAttachmentDraft字段，按I-2审计所有新建、copy、迁移写点；文件item用本地稳定key，选择顺序固定。移除uploading项时删除其key，迟到成功只成为未引用上传，不重新插回；不发删除服务器原件的请求。切同专家回复目标时保留已ready附件；跨专家/账号通过既有缓存key隔离，绝不自动迁移（I-2/I-5）。
3. sendManualReply两分支都带attachmentIds；空时省略兼容旧接口。对有通用附件的发送复用现有禁用控件能力并扩展附件按钮；捕获正文/主题/附件语义快照＋ownerMap，成功比较仍相同才删除原草稿；回包无权改新会话busy/QA。上传完成/程序重存不重置请求身份，用户实际更改附件才作与正文更改同样requestId失效（I-3/I-5）。
4. renderMessage渲染S-2已发卡；所有下载href=宿主contextPath+服务端相对URL，不调用preview附件JS或IndexedDB（I-4）。文件名恶意HTML用text显示。ICS保留原data-role与handler。
5. 新增测试，使用受控pending Promise验证切A/B、移除中上传、改正文后旧发送完成、会议全文替换与跟进填入保留附件、两类发送payload。修改已有工具栏顺序断言；逐字CSS与图标无可见文本断言（I-1～I-5，S-1/S-2）。

## 变更文件清单

|序号|文件|操作|内容|
|---|---|---|---|
|1|`src/main/resources/static/mailbox-chat.js`|修改|图标、多附件草稿、发送参数和会话下载卡|
|2|`src/main/resources/static/styles.css`|修改|仅追加outbound-* CSS|
|3|`src/test/js/mailboxOutboundAttachments.test.js`|新增|真实链路adapter/异步归属/原文件下载链接|
|4|`src/test/js/meetingConfirmationIntegration.test.js`|修改|工具栏顺序由6项变7项并回归混合附件|

## 验收标准

- I-1：DOM按钮textContent.trim()==''、title/aria正确、input.multiple=true且无accept；取消选择0请求。
- I-2：所有重建路径保留ready项；移除后迟到结果不复活；切专家/账户不串文件；文件对象上传后释放。
- I-3：uploading/failed禁止发送；payload严格有序ready ids；失败/UNKNOWN/取消确认保留全部；旧请求成功不清新owner草稿。
- I-4：/和/talent上下文下载URL正确；浏览器真实下载文件SHA一致；文件名HTML不执行。
- I-5：ICS和通用附件可各自单独移除；会议确认/跟进与QA采用后附件仍在；刷新后已发附件可读，未发草稿没有跨刷新承诺。
- S-1/S-2：CSS逐字包含，图标32px按钮/16pxSVG，工具栏顺序明确，已发无移除动作；mailbox-chat.css字节不变；node --test src/test/js/*.test.js。

## 人工验收清单

### A-1：仅图标上传与草稿移除
- 前置条件：有来信测试专家，准备中文txt、zip、无扩展名文件；页面1440px。
- 操作步骤：1.看链接右侧并悬停。2.多选3文件。3.等待上传；移除zip，再选择同一zip。4.取消一次文件选择器。
- 预期结果：仅16px回形针，无可见“上传附件”文字；悬停提示“上传附件”；3文件卡显示大小/待发送，移除再选可用；取消选择不清已有卡、不发送邮件。
- 覆盖：I-1/I-2；S-1/S-2；IP-1。

### A-2：会议与附件共存
- 前置条件：A-1草稿；SMTP沙箱可收信。
- 操作步骤：1.会议确认填入正文。2.移除ICS再重新生成；确认普通文件仍在。3.人工发送。4.在对话逐个下载，刷新后再下载。
- 预期结果：工具栏顺序B/I/列表/链接/回形针/会议确认/跟进；普通文件不丢；成功后会话显示ICS＋3普通文件且均可下载，已发无移除；每份SHA等于原文件。
- 覆盖：I-2/I-3/I-4/I-5；S-1/S-2；IP-2/IP-3/IP-4。

### A-3：异步不串专家
- 前置条件：专家A/B，浏览器Slow 3G；A上传接近10MiB文件；B输入“B草稿”。
- 操作步骤：1.A上传中切B。2.等待完成，切回A。3.A再上传一份并在完成前移除。4.A发送中切B并编辑B正文。
- 预期结果：A附件只在A；B正文不丢；被移除文件不复活；A发送回包不清B正文或改变B按钮状态。
- 覆盖：I-2/I-3；IP-1/IP-5。

### A-4：失败、下载前缀与窄屏
- 前置条件：测试站部署/talent；760px视口，既有已发附件；准备10MiB+1字节文件。
- 操作步骤：1.下载已发文件。2.上传超限文件。3.模拟单个文件网络失败。4.保留失败项尝试发送，再移除失败项发送。
- 预期结果：下载请求路径以/talent/api/开头；超限有提示；失败卡明确失败，未移除不能发送；移除后发送其余ready附件；卡片不撑宽页面，边框#dce4ef/圆角7px/焦点蓝框2px。
- 覆盖：I-1/I-3/I-4；S-1/S-2；IP-3/IP-4。

