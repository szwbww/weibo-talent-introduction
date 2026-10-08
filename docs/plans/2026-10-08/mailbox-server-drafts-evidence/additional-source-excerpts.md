# 补充源码证据

仅仓库读取，不代表功能已实施。

## src/main/resources/static/app.js
```text
5031: function setView(view) {
5032:     if (!Object.prototype.hasOwnProperty.call(viewMeta, view)) return;
5033:     if (view !== "mailbox" && typeof invalidateMailboxGroupPush === "function") invalidateMailboxGroupPush();
5034:     if (view !== "contacts") {
5035:         ++mobileContactPresentation.generation;
5036:         const contactsView = $("#view-contacts");
5037:         if (contactsView) {
5038:             contactsView.dataset.mobilePane = "list";
5039:             contactsView.dataset.mobileLoading = "false";
5040:         }
5041:     }
5042:     const mobileMenu = $("#mobileCoreView");
5043:     if (mobileMenu) mobileMenu.value = view;
5044:     if (view !== "ai-training") unmountAiTrainingTrustReply();
5045:     if (view !== "mailbox") unmountMailboxTrustReplyHosts();
5046:     // child 10（I-2）：离开收发件箱即销毁聊天 mount（草稿为内存态、随销毁清空，
5047:     // 避免跨会话/跨专家残留目标与 QA 上下文）。
5048:     if (view !== "mailbox" && typeof unmountMailboxChatHosts === "function") {
5049:         unmountMailboxChatHosts();
5050:     }
5051:     if (state.monitoring.autoRefreshTimer && view !== "monitoring") {
5052:         clearTimeout(state.monitoring.autoRefreshTimer);
5053:         state.monitoring.autoRefreshTimer = null;
5054:     }
5055:     if (view !== "monitoring") {
5056:         ++state.monitoring.loadSeq;
```
```text
17864:         ]
17865:     },
17866:     "confirm": {
17867:         title: "确认操作",
17868:         fields: [
17869:             { name: "message", label: "", type: "html", required: false }
17870:         ]
17871:     },
17872:     "confirm-typed": {
17873:         title: "高风险发送二次确认",
17874:         fields: [
17875:             { name: "message", label: "", type: "html", required: false },
17876:             { name: "confirmText", label: "请输入「确认发送」", type: "text", required: true, placeholder: "确认发送" }
17877:         ],
17878:         validate: (result) => (String(result.confirmText || "").trim() === "确认发送" ? null : "输入不匹配，请逐字输入「确认发送」四个字。")
17879:     }
17880: };
17881: 
17882: function openActionDialog(type, options = {}) {
```
```text
17882: function openActionDialog(type, options = {}) {
17883:     return new Promise((resolve) => {
17884:         const dialog = document.getElementById("actionDialog");
17885:         const form = document.getElementById("actionDialogForm");
17886:         const titleEl = document.getElementById("actionDialogTitle");
17887:         const bodyEl = document.getElementById("actionDialogBody");
17888: 
17889:         const schema = ACTION_DIALOG_SCHEMAS[type];
17890:         if (!schema) {
17891:             console.error("Unknown dialog type:", type);
17892:             resolve(null);
17893:             return;
17894:         }
17895: 
17896:         titleEl.textContent = schema.title;
17897: 
17898:         // Render fields
17899:         let html = "";
17900:         const fields = schema.fields;
17901:         fields.forEach(field => {
17902:             if (field.type === "html") {
17903:                 html += `<div>${options.message || ''}</div>`;
17904:             } else if (field.type === "checkbox") {
17905:                 html += `
```
```text
18577:     $("#logoutBtn")?.addEventListener("click", async () => {
18578:         try {
18579:             await api("/api/auth/logout", { method: "POST" });
18580:         } catch (e) {
18581:         } finally {
18582:             location.reload();
18583:         }
18584:     });
18585: }
18586: 
18587: function mailboxViewMode() {
18588:     const checked = document.querySelector('input[name="mailboxViewMode"]:checked');
```
```text
18623: function mailboxChatAvailable() {
18624:     return typeof MailboxChat !== "undefined" && !!MailboxChat
18625:         && typeof MailboxChat.mount === "function"
18626:         && typeof MailboxChat.unmount === "function";
18627: }
18628: 
18629: function mailboxChatEligible() {
18630:     return mailboxChatAvailable() && state.mailbox.taskExecutionId == null;
18631: }
18632: 
18633: function unmountMailboxChatHosts() {
18634:     if (!mailboxChatAvailable()) return;
18635:     const list = $("#mailboxList");
18636:     if (!list) return;
18637:     try {
18638:         MailboxChat.unmount(list);
18639:     } catch (e) {
18640:         // 组件内部清理失败不阻断原路径
18641:     }
18642:     state.mailbox.chatMounted = false;
18643:     // 关闭来信标签 modal 宿主 adapter（聊天目标上下文随卸载失效）
```

## src/main/resources/static/mailbox-chat.js
```text
5590:             return "of-" + outboundFileSeq;
5591:         }
5592: 
5593:         function emptyOutboundAttachmentDraft() {
5594:             return { revision: 0, items: [] };
5595:         }
5596: 
5597:         /**
5598:          * I-2：读草稿的附件字段。缺失/损坏视为空草稿（不抛、不另立真值）；items 为浅拷贝，
5599:          * 增删后必须经 setOutboundAttachmentItems 写回。
5600:          */
5601:         function outboundAttachmentDraftOf(draft) {
5602:             const field = draft ? draft.outboundAttachmentDraft : null;
5603:             const items = field && Array.isArray(field.items) ? field.items : [];
5604:             return {
5605:                 revision: field && Number.isFinite(Number(field.revision)) ? Number(field.revision) : 0,
5606:                 items: items.slice()
5607:             };
5608:         }
5609: 
5610:         /** I-3：ready 条目按选择顺序提交；空数组 = 旧接口形态（payload 省略 attachmentIds）。 */
```
```text
5819:         function uploadOutboundFile(captured, file) {
5820:             const name = file && file.name != null ? String(file.name) : "";
5821:             const size = Number(file && file.size) || 0;
5822:             const item = {
5823:                 key: nextOutboundFileKey(),
5824:                 state: OUTBOUND_STATE_UPLOADING,
5825:                 filename: name,
5826:                 byteLength: size,
5827:                 file
5828:             };
5829:             const items = outboundItemsIn(captured);
5830:             items.push(item);
5831:             if (!setOutboundAttachmentItems(captured, items)) return Promise.resolve();
5832:             invalidateOutboundRequestId(captured);
5833:             if (outboundOwnerIsCurrent(captured)) refreshOutboundFilesCard();
5834:             // 预检查（服务端仍最终裁决）：先落一张明确的失败卡，绝不静默丢掉这次选择。
5835:             const tooLarge = size > OUTBOUND_MAX_FILE_BYTES;
5836:             const overBudget = !tooLarge && outboundBytesIn(captured) > OUTBOUND_MAX_TOTAL_BYTES;
5837:             if (tooLarge || overBudget) {
5838:                 failOutboundItem(captured, item.key, tooLarge
5839:                     ? "单个附件不能超过 " + (OUTBOUND_MAX_FILE_BYTES / (1024 * 1024)) + "MiB"
5840:                     : "通用附件总计不能超过 " + (OUTBOUND_MAX_TOTAL_BYTES / (1024 * 1024)) + "MiB");
5841:                 return Promise.resolve();
5842:             }
5843:             const form = typeof FormData === "function" ? new FormData() : null;
5844:             if (!form) {
5845:                 failOutboundItem(captured, item.key, "当前浏览器不支持附件上传");
5846:                 return Promise.resolve();
5847:             }
5848:             form.append("file", file, name);
5849:             return hostApi()(`/api/mail/conversations/${captured.contactId}/outbound-attachments`, {
5850:                 method: "POST",
5851:                 // headers 整体覆盖宿主的 JSON 默认值，让浏览器自己写 multipart 边界。
5852:                 headers: {},
5853:                 body: form
5854:             }).then((uploaded) => {
5855:                 const id = uploaded && uploaded.id != null ? String(uploaded.id) : "";
5856:                 if (id === "") {
5857:                     failOutboundItem(captured, item.key, OUTBOUND_TEXT_FAILED);
5858:                     return;
5859:                 }
5860:                 applyOutboundItemChange(captured, item.key, () => ({
5861:                     key: item.key,
5862:                     state: OUTBOUND_STATE_READY,
5863:                     filename: uploaded.filename != null ? String(uploaded.filename) : name,
5864:                     contentType: uploaded.contentType != null ? String(uploaded.contentType) : "",
5865:                     byteLength: Number(uploaded.byteLength) || size,
5866:                     sha256: uploaded.sha256 != null ? String(uploaded.sha256) : "",
5867:                     id,
5868:                     downloadUrl: uploaded.downloadUrl != null ? String(uploaded.downloadUrl) : ""
5869:                 }));
5870:             }).catch((err) => {
5871:                 failOutboundItem(captured, item.key, err && err.message ? String(err.message) : OUTBOUND_TEXT_FAILED);
5872:             });
5873:         }
5874: 
5875:         /**
5876:          * 单条落定：整项换成新对象（ready 项不含 File，原件随落定释放）；key 已不在草稿里
5877:          * （被移除/草稿被淘汰）时返回 false —— 迟到回包绝不重新插回。
5878:          */
5879:         function applyOutboundItemChange(captured, fileKey, build) {
5880:             const items = outboundItemsIn(captured);
5881:             const index = items.findIndex((item) => item && String(item.key) === String(fileKey));
5882:             if (index === -1) return false;
5883:             items[index] = build(items[index]);
5884:             if (!setOutboundAttachmentItems(captured, items)) return false;
5885:             if (outboundOwnerIsCurrent(captured)) refreshOutboundFilesCard();
5886:             return true;
5887:         }
5888: 
5889:         function failOutboundItem(captured, fileKey, message) {
5890:             const text = message || OUTBOUND_TEXT_FAILED;
5891:             const changed = applyOutboundItemChange(captured, fileKey, (item) => ({
5892:                 key: item.key,
5893:                 state: OUTBOUND_STATE_FAILED,
5894:                 filename: item.filename,
5895:                 byteLength: 0,
5896:                 error: text
5897:             }));
5898:             if (changed && outboundOwnerIsCurrent(captured)) hostShowStatus(text, "error");
5899:             return changed;
5900:         }
5901: 
5902:         /** I-2：移除只删本地 key（不发任何服务器删除请求）；迟到回包因 key 消失而作废。 */
```
```text
8970:         return {
8971:             instance,
8972:             applyOptions,
8973:             refresh,
8974:             refreshFromHost,
8975:             loadList,
8976:             unmount,
8977:             isMounted: () => true
8978:         };
8979:     }
8980: 
8981:     // ------------------------------------------------------------------
8982:     // 公共 API（mount 同 host 再次调用 = 刷新语义，不重复建 DOM）
8983:     // ------------------------------------------------------------------
8984: 
8985:     function mount(host, options) {
8986:         if (!host) throw new Error("MailboxChat.mount requires a host element");
8987:         const existing = instances.get(host);
8988:         if (existing) {
8989:             const opts = options || {};
8990:             if (opts.filters || opts.focus || opts.sessionUser) {
8991:                 if (typeof existing.applyOptions === "function") existing.applyOptions(opts);
8992:             }
8993:             if (typeof existing.loadList === "function") existing.loadList();
8994:             return existing;
8995:         }
8996:         const controller = createInstance(host, options || {});
8997:         const api = {
8998:             applyOptions: (next) => { if (controller) controller.applyOptions(next); },
8999:             refresh: () => { if (controller) return controller.refresh(); return undefined; },
9000:             refreshFromHost: () => { if (controller) return controller.refreshFromHost(); return undefined; },
9001:             loadList: () => { if (controller) return controller.loadList(); return undefined; },
9002:             unmount: () => { if (controller) controller.unmount(); },
9003:             isMounted: () => { if (controller) return controller.isMounted(); return false; }
9004:         };
9005:         controller.api = api;
9006:         instances.set(host, api);
9007:         api.loadList();
9008:         return api;
9009:     }
9010: 
9011:     function unmount(host) {
9012:         if (!host) return false;
9013:         const api = instances.get(host);
9014:         if (api && typeof api.unmount === "function") {
9015:             api.unmount();
9016:             return true;
9017:         }
9018:         return false;
9019:     }
9020: 
9021:     function isMounted(host) {
9022:         if (!host) return false;
9023:         const api = instances.get(host);
9024:         return !!api;
9025:     }
9026: 
9027:     global.MailboxChat = Object.freeze({
9028:         mount,
9029:         unmount,
9030:         isMounted,
9031:         version: VERSION
9032:     });
9033: })(typeof window !== "undefined" ? window : (typeof globalThis !== "undefined" ? globalThis : this));
```

## src/main/resources/static/index.html
```text
2283: <dialog id="actionDialog" class="action-dialog">
2284:     <form id="actionDialogForm" method="dialog">
2285:         <h3 id="actionDialogTitle"></h3>
2286:         <div id="actionDialogBody" class="action-dialog-body"></div>
2287:         <div class="action-dialog-footer">
2288:             <button type="button" class="button secondary" data-action="action-dialog-cancel">取消</button>
2289:             <button type="submit" class="button primary">确认执行</button>
2290:         </div>
2291:     </form>
2292: </dialog>
2293: 
```

## src/main/resources/db/migration/V126__create_outbound_mail_attachment.sql
```text
1: -- ============================================================================
2: -- V126 outbound_mail_attachment（fast-p 04：人工回复通用附件上传原件）
3: --
4: -- 一行 = 一次成功上传的通用附件原件元数据。id 是服务端随机 UUID（CHAR(36)），
5: -- 同时就是磁盘文件名 outbound/<id>（不再落库第二份 storage_key）。元数据与原
6: -- 字节创建后不可变：本表只有 INSERT 与按 id 批量读取，没有 UPDATE/DELETE API，
7: -- 「移除草稿附件」只解除草稿引用（客户端引用），不删本行、不删已上传文件。
8: --
9: -- 关键不变量：
10: --   I-1 上传只写本表与 outbound/ 目录：不写 mail_attachment/expert_document，
11: --       不调用 SMTP、不建排期、不变更专家状态；UUID 由主键保证永不复用。
12: --   I-2 expert_contact_id 必须是当前已存在专家（FK ON DELETE RESTRICT）；
13: --       created_by 取会话登录名，请求体 operatorName 不参与身份。
14: --   I-3 无扩展名/MIME 白名单列；file_name 只存展示名（最多 255 字符），
15: --       byte_length 与 sha256 都是原件真实字节口径；0 字节合法。
16: --   I-5 发送快照 JSON 由 05 写入 mail_record 新列；本表既不存字节也不存快照。
17: --
18: -- 索引 (expert_contact_id, created_at)：按专家列出本人上传附件的稳定顺序。
19: -- 未被引用的上传文件本期保留原样（不引入状态列/租约/清理调度）。
20: -- ============================================================================
21: CREATE TABLE outbound_mail_attachment (
22:     id                CHAR(36)     NOT NULL,
23:     expert_contact_id BIGINT       NOT NULL,
24:     created_by        VARCHAR(100) NOT NULL,
25:     file_name         VARCHAR(255) NOT NULL,
26:     content_type      VARCHAR(255) NOT NULL,
27:     byte_length       BIGINT       NOT NULL,
28:     sha256            CHAR(64)     NOT NULL,
29:     created_at        DATETIME(6)  NOT NULL,
30:     PRIMARY KEY (id),
31:     KEY idx_outbound_mail_attachment_contact (expert_contact_id, created_at),
32:     CONSTRAINT fk_outbound_mail_attachment_contact
33:         FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id) ON DELETE RESTRICT
34: ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
35:   COMMENT = '人工回复通用附件上传原件元数据（fast-p 04）';
```

## src/main/resources/db/migration/V25__create_admin_user.sql
```text
1: CREATE TABLE admin_user (
2:     id              BIGINT AUTO_INCREMENT PRIMARY KEY,
3:     username        VARCHAR(64)  NOT NULL,
4:     password_hash   VARCHAR(100) NOT NULL,
5:     must_change_password TINYINT(1) NOT NULL DEFAULT 1,
6:     last_login_at   DATETIME     NULL,
7:     created_at      DATETIME     NOT NULL,
8:     updated_at      DATETIME     NOT NULL,
9:     CONSTRAINT uk_admin_user_username UNIQUE (username)
10: );
```

## src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt
```text
112: data class ConversationManualRichReplyRequest(
113:     val requestId: String,
114:     val accountScope: String? = null,
115:     val anchorMailRecordId: Long? = null,
116:     val subject: String,
117:     val htmlBody: String,
118:     val textBody: String? = null,
119:     val operatorName: String? = null,
120:     val safetyWarningConfirmed: Boolean = false,
121:     val strongConfirmationText: String? = null,
122:     /**
123:      * 06 (I-1/I-2)：通用附件 id（用户选择顺序，04 上传产物）。默认空 = 既有纯文本回复
124:      * 形态逐字不变；非空时服务端按 (专家 + 会话身份) 重读 04 元数据与原件，失败在 claim
125:      * 之前以 400/404/409/413 返回。同一 requestId 重提时附件语义必须与原记录一致。
126:      */
127:     val attachmentIds: List<String> = emptyList()
128: )
129: 
130: data class ConversationListResponse(
131:     val items: List<ConversationItemResponse>,
132:     val total: Long,
133:     val page: Int,
134:     val size: Int
```
```text
380:     // 引入前端可选的账号/QA/RAG 字段。Auth 由 AuthInterceptor 统一拦截（/api/**）。
381:     // ------------------------------------------------------------------
382: 
383:     @PostMapping("/{contactId}/manual-rich-reply")
384:     fun conversationManualRichReply(
385:         @PathVariable contactId: Long,
386:         @RequestBody body: ConversationManualRichReplyRequest,
387:         // 06 (I-1)：通用附件身份只取会话（AuthInterceptor 保证生产必已登录）；无附件请求
388:         // 完全不依赖它，直接调用（测试/内部）可省略。
389:         servletRequest: HttpServletRequest? = null
390:     ): PendingMailSendResult = pendingMailOperationService.sendConversationManualRichReply(
391:         contactId = contactId,
392:         requestId = body.requestId,
393:         accountScope = body.accountScope,
394:         anchorMailRecordId = body.anchorMailRecordId,
395:         subject = body.subject,
396:         htmlBody = body.htmlBody,
397:         textBody = body.textBody,
398:         operatorName = body.operatorName,
399:         safetyWarningConfirmed = body.safetyWarningConfirmed,
400:         strongConfirmationText = body.strongConfirmationText,
401:         // 06 (I-1/I-2)：附件 id 与会话身份；identity 绝不取请求体 operatorName。
402:         attachmentIds = body.attachmentIds,
403:         authenticatedUsername = servletRequest?.let { sessionUsername(it) }
404:     )
405: 
```

## src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt
```text
258:     fun sendManualRichReply(
259:         @PathVariable id: Long,
260:         @RequestBody request: PendingManualRichReplyRequest,
261:         // 06 (I-1): 通用附件身份只取会话（AuthInterceptor 保证生产必已登录）；直接调用
262:         // （测试/内部）可省略，无附件路径完全不依赖它。
263:         servletRequest: HttpServletRequest? = null
264:     ): PendingMailSendResult =
265:         pendingMailOperationService.sendManualRichReply(
266:             inboundProcessingId = id,
267:             senderAccountCode = request.senderAccountCode,
268:             subject = request.subject,
269:             htmlBody = request.htmlBody,
270:             textBody = request.textBody,
271:             operatorName = request.operatorName,
272:             qaRuleIds = request.qaRuleIds,
273:             suggestedRuleIds = request.suggestedRuleIds,
274:             ackSnippetId = request.ackSnippetId,
275:             edited = request.edited,
276:             freeTextPreview = request.freeTextPreview,
277:             useVariants = request.useVariants,
278:             templateTextBody = request.templateTextBody,
279:             templateHtmlBody = request.templateHtmlBody,
280:             trustReplyAssembly = request.trustReplyAssembly,
281:             ragFactCodes = request.ragFactCodes,
282:             ragCorpusFingerprint = request.ragCorpusFingerprint,
283:             safetyWarningConfirmed = request.safetyWarningConfirmed,
284:             strongConfirmationText = request.strongConfirmationText,
285:             // 03 (T1/I-1): 透传已预览会议配置与预览快照 sha256（不新增第二个发送 API）。
286:             meeting = request.meeting,
287:             previewAttachmentSha256 = request.previewAttachmentSha256,
288:             // 06 (T1/I-1): 透传附件 id 与会话身份；identity 绝不取请求体 operatorName。
289:             attachmentIds = request.attachmentIds,
290:             authenticatedUsername = servletRequest.sessionUsernameOrNull()
291:         )
292: 
293:     private fun HttpServletRequest?.sessionUsernameOrNull(): String? = this
294:         ?.getSession(false)
295:         ?.getAttribute(AuthSessionKeys.USERNAME) as? String
296: 
297:     @GetMapping("/unmatched-inbound/{id}/auto-reply-preview")
```

## src/main/resources/static/styles.css（最终层叠与移动规则）
```text
12547:     #view-mailbox .mailbox-list { min-width: 0; }
12548:     #view-mailbox .table-wrap { max-width: 100%; overflow-x: auto; }
12549:     .mail-chat.mobile-core-mailbox { display: flex; flex-direction: column; height: auto; min-height: 0; gap: 12px; }
12550:     .mail-chat.mobile-core-mailbox[data-mobile-pane="list"] > .mc-conversation,
12551:     .mail-chat.mobile-core-mailbox[data-mobile-pane="list"] > .mobile-mailbox-back,
12552:     .mail-chat.mobile-core-mailbox[data-mobile-pane="detail"] > .mc-experts { display: none; }
12553:     .mail-chat.mobile-core-mailbox[data-mobile-pane="detail"] > .mobile-mailbox-back {
12554:         display: inline-flex; align-self: flex-start; min-height: 44px; height: auto;
12555:     }
12556:     .mail-chat.mobile-core-mailbox .mc-experts { min-height: 0; max-height: none; }
12557:     .mail-chat.mobile-core-mailbox .mc-expert-list { min-height: 120px; max-height: 65dvh; overflow: auto; }
12558:     .mail-chat.mobile-core-mailbox .mc-conversation { min-height: 0; }
12559:     .mail-chat.mobile-core-mailbox .mc-scroll { flex: none; height: 65dvh; min-height: 240px; max-height: none; overflow: auto; }
12560:     .mail-chat.mobile-core-mailbox .mc-header,
12561:     .mail-chat.mobile-core-mailbox .mc-actions,
12562:     .mail-chat.mobile-core-mailbox .mc-compose-footer,
12563:     .mail-chat.mobile-core-mailbox .mc-message footer { flex-wrap: wrap; gap: 8px; }
12564:     .mail-chat.mobile-core-mailbox .mc-header-meta { overflow-wrap: anywhere; }
12565:     .mail-chat.mobile-core-mailbox :is(button, .button) { min-height: 44px; height: auto; white-space: normal; }
12566:     .mail-chat.mobile-core-mailbox .mc-icon { min-width: 44px; }
12567:     .mail-chat.mobile-core-mailbox .mc-search-row { flex-wrap: wrap; }
12568:     .mail-chat.mobile-core-mailbox .mc-filter-popover {
12569:         position: static; inset: auto; flex: 1 0 100%;
12570:         width: 100%; max-width: 100%; max-height: none;
12571:     }
12572:     .mail-chat.mobile-core-mailbox .mc-filter-fields { grid-template-columns: minmax(0, 1fr); }
12573:     .mail-chat.mobile-core-mailbox :is(input:not([type="checkbox"]):not([type="radio"]), select, textarea, [contenteditable="true"]) {
12574:         min-width: 0; max-width: 100%; min-height: 44px; font-size: 16px;
12575:     }
12576:     .mail-chat.mobile-core-mailbox .mc-editor { min-height: 160px; max-height: 40dvh; }
12577:     .mail-chat.mobile-core-mailbox .mc-body { font-size: 14px; line-height: 1.8; }
12578:     .mail-chat.mobile-core-mailbox .mc-compose-footer > [data-role="target-info"] { width: 100%; overflow-wrap: anywhere; }
12579:     .mail-chat.mobile-core-mailbox .outbound-file { min-width: 0; }
12580:     body .mail-chat.mc-overlay-root .mc-manage-dialog { width: calc(100vw - 24px); max-height: calc(100dvh - 24px); }
12581:     body .mail-chat.mc-overlay-root .mc-manage-dialog .button,
12582:     body .reply-template-dialog .button,
```
```text
12646: /* mailbox-suspension-followup: full tabs and optional editable reason */
12647: @media(min-width:761px){#view-mailbox.mc-refined .mail-chat{grid-template-columns:380px minmax(0,1fr)}}
12648: #view-mailbox.mc-refined .mail-chat .mc-filters{flex-wrap:wrap;overflow-x:visible;row-gap:0}
12649: [data-role=suspension-reason-actions]{display:flex;flex:none;align-items:center;margin-left:auto}
12650: .mailbox-suspend-banner-content strong{overflow-wrap:anywhere}
12651: @media(max-width:760px){.mailbox-suspend-banner{flex-wrap:wrap}[data-role=suspension-reason-actions]{margin-left:auto}.mailbox-suspend-banner-content{flex-basis:calc(100% - 40px)}}
12652: 
12653: /* mailbox-progress-contract:start */
12654: .mail-chat .mc-person.mailbox-progress-card{grid-template-columns:minmax(0,1fr)}
12655: .mail-chat .mailbox-progress-card .mc-person-main{grid-column:1 / -1;padding-right:12px}
12656: .mail-chat .mailbox-progress-card .mc-person-heading{padding-right:76px;min-height:30px}
12657: .mail-chat .mailbox-progress-card .mc-person-main small:first-of-type{padding-right:76px}
```
```text
12823: #view-mailbox.mc-refined .mc-filters{display:flex;flex-wrap:nowrap;gap:4px;overflow-x:auto;scrollbar-width:thin}
12824: #view-mailbox.mc-refined .mc-filter{flex:0 0 auto;min-height:36px;padding:7px 5px 10px;border:0;border-bottom:2px solid transparent;border-radius:0;background:transparent;color:#7d8ca2;font-size:12px;line-height:1.5;white-space:nowrap}
12825: #view-mailbox.mc-refined .mc-filter:hover{background:#f4f7ff;color:#3762d8}
12826: #view-mailbox.mc-refined .mc-filter:active{background:#e9efff}
12827: #view-mailbox.mc-refined .mc-filter[aria-pressed=true]{background:transparent;border-bottom-color:#3762d8;color:#3762d8;font-weight:600}
12828: #view-mailbox.mc-refined .mc-filter:disabled{opacity:.45;cursor:not-allowed}
12829: #view-mailbox.mc-refined .mc-filter:focus-visible{outline:2px solid #9eb9ff;outline-offset:-2px}
12830: .mailbox-suspend-count{display:inline-flex;align-items:center;justify-content:center;min-width:16px;height:16px;margin-left:3px;padding:0 4px;border-radius:5px;background:#f0f3f8;color:#8a98ab;font-size:10px;font-weight:600;line-height:16px}
12831: #view-mailbox.mc-refined .mc-filter[aria-pressed=true] .mailbox-suspend-count{background:#e9efff;color:#3762d8}
```
```text
2873: .action-dialog {
2874:     border: 1px solid var(--panel-border);
2875:     border-radius: var(--radius-lg);
2876:     padding: 20px;
2877:     width: min(500px, 90vw);
2878:     margin: auto;
2879:     background: rgba(255, 255, 255, 0.97);
2880:     backdrop-filter: blur(8px);
2881:     -webkit-backdrop-filter: blur(8px);
2882:     color: var(--text-main);
2883:     box-shadow: var(--shadow-xl);
2884: }
2885: 
2886: .action-dialog::backdrop {
2887:     background: rgba(15, 23, 42, 0.5);
2888:     backdrop-filter: blur(6px);
2889:     -webkit-backdrop-filter: blur(6px);
2890: }
2891: 
2892: .action-dialog h3 {
2893:     margin-bottom: 12px;
2894:     font-size: 15px;
2895:     font-weight: 600;
2896:     font-family: var(--font-body);
2897:     color: var(--text-main);
2898: }
2899: 
2900: .action-dialog-body {
2901:     display: flex;
2902:     flex-direction: column;
2903:     gap: 12px;
2904:     margin-bottom: 20px;
2905: }
2906: 
2907: .action-dialog-body p {
2908:     color: var(--text-main);
2909:     font-size: 13px;
2910:     line-height: 1.6;
2911: }
2912: 
2913: .action-dialog-body .ai-reply-coverage {
2914:     color: var(--text-secondary);
2915: }
2916: 
2917: .action-dialog-footer {
2918:     display: flex;
```
