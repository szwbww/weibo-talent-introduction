## src/main/resources/static/mailbox-chat.js:2779-2796
```
2779:         function teardownConversationSubViews() {
2780:             closeContactTimingDialog({ restoreFocus: false });
2781:             invalidateContactTiming();
2782:             closeManageOverlay({ restoreFocus: false });
2783:             closeFollowUpDialog({ restoreFocus: false });
2784:             closeMaterialRequestDialog({ restoreFocus: false });
2785:             closeTemplateReferenceDialog({ restoreFocus: false });
2786:             if (instance.workbench.instance) {
2787:                 try { instance.workbench.instance.unmount(); } catch (e) { /* noop */ }
2788:                 instance.workbench.instance = null;
2789:                 instance.workbench.processingId = null;
2790:             }
2791:             teardownMeetingViews();
2792:             const releaseMaterials = hostFn("unmountExpertMaterialsHosts");
2793:             if (releaseMaterials && instance.host) releaseMaterials(instance.host);
2794:         }
2795: 
2796:         function accountFilterFromOptions() {
```

## src/main/resources/static/mailbox-chat.js:2800-2859
```
2800:         function selectExpert(item, options) {
2801:             const opts = options || {};
2802:             const sameOwner = Number(instance.selectedContactId) === Number(item.contactId)
2803:                 && String(instance.conversation.accountScope || "") === accountFilterFromOptions();
2804:             if (sameOwner && !opts.force && !instance.conversation.error) {
2805:                 if (opts.present !== false) setMobilePane("detail", opts.trigger);
2806:                 return;
2807:             }
2808:             // DOM 草稿必须在切换旧 owner 之前采集。
2809:             saveCurrentConversation();
2810:             if (opts.present !== false) setMobilePane("detail", opts.trigger);
2811:             teardownConversationSubViews();
2812:             instance.pendingPosition = null;
2813:             instance.clearedEditorSnapshot = null;
2814:             instance.selectedContactId = Number(item.contactId);
2815:             instance.selectedSummary = item;
2816:             instance.seq += 1;
2817:             instance.convEpoch += 1;
2818:             const mySeq = instance.seq;
2819:             const myEpoch = instance.convEpoch;
2820:             instance.conversation.accountScope = accountFilterFromOptions();
2821:             instance.conversation.loading = true;
2822:             instance.conversation.error = "";
2823:             instance.conversation.items = [];
2824:             instance.conversation.nextBefore = null;
2825:             instance.conversation.hasMore = false;
2826:             instance.conversation.contact = null;
2827:             instance.manual = {
2828:                 mode: "none",
2829:                 targetProcessingId: null,
2830:                 targetAccountCode: "",
2831:                 targetKey: null,
2832:                 qa: null,
2833:                 busy: false
2834:             };
2835:             instance.logs = { loaded: false };
2836:             instance.dismissedNewInbound = null;
2837:             instance.pendingPrompt = null;
2838:             instance.translations = new Map();
2839:             instance.loadOlderBusy = false;
2840:             instance.draftsRef = null;
2841:             resetSuspensionState();
2842:             renderConversationScaffold();
2843: 
2844:             const contactId = Number(item.contactId);
2845:             loadSuspensionState(contactId);
2846:             // c3（I-3）：换专家即作废旧 timing 代次，再按新 contact 取推荐。
2847:             loadContactTiming(contactId);
2848:             const accountFilter = accountFilterFromOptions();
2849:             const msgParams = new URLSearchParams();
2850:             msgParams.set("limit", String(MESSAGE_LIMIT));
2851:             if (accountFilter) msgParams.set("accountCode", accountFilter);
2852:             const contactPromise = hostApi()(`/api/expert-contacts/${contactId}`).catch(() => null);
2853:             const messagesPromise = hostApi()(`/api/mail/mailbox/conversations/${contactId}/messages?${msgParams.toString()}`)
2854:                 .catch(() => null);
2855: 
2856:             Promise.all([contactPromise, messagesPromise]).then(([contact, msgData]) => {
2857:                 if (instance.disposed || mySeq !== instance.seq || myEpoch !== instance.convEpoch) return;
2858:                 instance.conversation.contact = contact && contact.contact ? contact.contact : (contact || null);
2859:                 instance.conversation.accountScope = accountFilter;
```

## src/main/resources/static/mailbox-chat.js:2993-3018
```
2993:         function renderHeaderMeta(metaEl) {
2994:             if (!metaEl) return;
2995:             const contact = instance.conversation.contact || null;
2996:             const summary = instance.selectedSummary || {};
2997:             const statusText = contact ? displayNameForCatalog(statusCatalog(), contact.operatorStatus) : "";
2998:             const levelText = contact ? displayNameForCatalog(levelCatalog(), contact.currentIndexLevel) : "";
2999:             const statusBadge = statusText ? `<span class="mc-badge" data-tone="success">${escapeText(statusText)}</span>` : "";
3000:             const levelBadge = levelText ? `<span class="mc-badge">${escapeText(levelText)}</span>` : "";
3001:             const orcid = (contact && contact.orcidId) || summary.orcid || "";
3002:             const expertTagSpans = headerExpertTagSpans();
3003:             metaEl.innerHTML = `
3004:                 ${statusBadge}${levelBadge}${expertTagSpans}${orcid ? `<span>ORCID ${escapeText(orcid)}</span>` : ""}<button class="mc-text-button" type="button" data-action="mc-open-expert" data-contact-id="${escapeText(Number(instance.selectedContactId))}">查看专家详情 ↗</button>${contactTimingMarkup()}
3005:             `;
3006:         }
3007: 
3008:         function headerExpertTagSpans() {
3009:             const contact = instance.conversation.contact || null;
3010:             const orcidId = contact && (contact.orcidId || contact.expertOrcidId) ? String(contact.orcidId || contact.expertOrcidId) : "";
3011:             const level = contact && contact.currentIndexLevel ? String(contact.currentIndexLevel) : "";
3012:             const tags = instance.headerTags || null;
3013:             if (tags === null) return "";
3014:             if (tags.length === 0) return "";
3015:             return tags.map((tag) => `<span class="expert-tag">${escapeText(expertTagLabel(tag))}</span>`).join("");
3016:         }
3017: 
3018:         function refreshHeaderExpertTags() {
```

## src/main/resources/static/mailbox-chat.js:3188-3205
```
3188:             }).catch(() => {
3189:                 if (instance.disposed || mySeq !== state.seq) return;
3190:                 state.data = null;
3191:                 state.loading = false;
3192:                 state.error = failureText || CONTACT_TIMING_ERROR_TEXT;
3193:                 repaintContactTiming();
3194:             });
3195:         }
3196: 
3197:         /** 只重绘状态行：不重建时间线、不碰草稿与滚动（T-1）。 */
3198:         function repaintContactTiming() {
3199:             const body = conversationBody();
3200:             if (!body || typeof body.querySelector !== "function") return;
3201:             const meta = body.querySelector(".mc-header-meta");
3202:             if (meta) renderHeaderMeta(meta);
3203:         }
3204: 
3205:         // ---- 自有 dialog 生命周期（I-5：直接挂 body，只移除自己的节点） ----
```

## src/main/resources/static/mailbox-chat.js:3252-3330
```
3252:             state.dialogSeq += 1;
3253:             contactTimingInstanceSeq += 1;
3254:             state.prefix = `ct${contactTimingInstanceSeq}`;
3255:             const doc = docRoot();
3256:             if (!doc || typeof doc.createElement !== "function" || !doc.body) return null;
3257:             const dialog = {
3258:                 kind,
3259:                 seq: state.dialogSeq,
3260:                 contactId: Number(contactId),
3261:                 node: null,
3262:                 trigger: trigger || null,
3263:                 ready: false,
3264:                 busy: false,
3265:                 catalog: state.catalog || null,
3266:                 location: null,
3267:                 error: ""
3268:             };
3269:             const node = doc.createElement("dialog");
3270:             node.setAttribute("class", "contact-timing-dialog");
3271:             node.setAttribute("data-kind", kind);
3272:             node.setAttribute("aria-labelledby", `${state.prefix}-${kind === "evidence" ? "evidence" : "location"}-title`);
3273:             dialog.node = node;
3274:             // 自有节点自己绑事件，不借共享 portal 委托，也不进其 innerHTML（I-5）。
3275:             if (typeof node.addEventListener === "function") {
3276:                 node.addEventListener("click", onContactDialogClick);
3277:                 node.addEventListener("change", onContactDialogChange);
3278:                 node.addEventListener("input", onContactDialogInput);
3279:                 node.addEventListener("submit", onContactDialogSubmit);
3280:                 node.addEventListener("cancel", onContactDialogCancel);
3281:                 node.addEventListener("keydown", onContactDialogKeyDown);
3282:             }
3283:             state.dialog = dialog;
3284:             doc.body.appendChild(node);
3285:             return dialog;
3286:         }
3287: 
3288:         function showContactDialog(dialog) {
3289:             const node = contactDialogNode(dialog);
3290:             if (!node) return;
3291:             if (typeof node.showModal === "function") {
3292:                 try {
3293:                     node.showModal();
3294:                     return;
3295:                 } catch (e) { /* fallback: 静态 open */ }
3296:             }
3297:             if (typeof node.setAttribute === "function") node.setAttribute("open", "");
3298:         }
3299: 
3300:         /** 只移除本实例拥有的 dialog，绝不清空共享 portal（I-5）。 */
3301:         function closeContactTimingDialog(options) {
3302:             const opts = options || {};
3303:             const state = contactTimingState();
3304:             const dialog = state.dialog;
3305:             state.dialogSeq += 1;
3306:             state.dialog = null;
3307:             if (!dialog) return;
3308:             const node = dialog.node;
3309:             if (node) {
3310:                 if (typeof node.close === "function") {
3311:                     try {
3312:                         node.close();
3313:                     } catch (e) { /* noop */ }
3314:                 }
3315:                 if (typeof node.hasAttribute === "function" && node.hasAttribute("open")) node.removeAttribute("open");
3316:                 if (node.parentNode && typeof node.parentNode.removeChild === "function") {
3317:                     node.parentNode.removeChild(node);
3318:                 } else if (typeof node.remove === "function") {
3319:                     node.remove();
3320:                 }
3321:             }
3322:             if (opts.restoreFocus !== false) focusIfAvailable(dialog.trigger);
3323:         }
3324: 
3325:         function onContactDialogCancel(event) {
3326:             if (event && typeof event.preventDefault === "function") event.preventDefault();
3327:             closeContactTimingDialog({ restoreFocus: true });
3328:         }
3329: 
3330:         function onContactDialogKeyDown(event) {
```

## src/main/resources/static/mailbox-chat.js:7338-7367
```
7338:         function teardownMeetingViews() {
7339:             const controller = instance.meeting.controller;
7340:             if (controller) {
7341:                 try { controller.close({ restoreFocus: false }); } catch (e) { /* noop */ }
7342:                 try { controller.dispose(); } catch (e) { /* noop */ }
7343:                 instance.meeting.controller = null;
7344:             }
7345:             revokeMeetingBlob();
7346:             instance.meeting.editorRevision = 0;
7347:         }
7348: 
7349:         // T5：账号过滤变化 → close/dispose 会议并 abort 在途请求（草稿缓存不清）
7350:         function meetingCloseDisposeOnAccountScopeChange(prevAccount, nextAccount) {
7351:             if (instance.selectedContactId == null) return;
7352:             if (String(prevAccount || "") === String(nextAccount || "")) return;
7353:             // 跟进候选绑定账号范围：范围变化即关闭弹窗（草稿缓存不清，I-7）。
7354:             closeFollowUpDialog({ restoreFocus: false });
7355:             closeMaterialRequestDialog({ restoreFocus: false });
7356:             closeTemplateReferenceDialog({ restoreFocus: false });
7357:             teardownMeetingViews();
7358:             // c3（I-3）：账号上下文变化同样作废推荐代次与自有弹窗。
7359:             closeContactTimingDialog({ restoreFocus: false });
7360:             invalidateContactTiming();
7361:         }
7362: 
7363:         function meetingCardActionsDisabled(disabled) {
7364:             const composeEl = manualComposeEl();
7365:             if (!composeEl || !composeEl.querySelectorAll) return;
7366:             ["mc-open-meeting", "mc-download-meeting", "mc-edit-meeting", "mc-remove-meeting"].forEach((action) => {
7367:                 composeEl.querySelectorAll(`[data-action="${action}"]`).forEach((node) => {
```

## src/main/resources/static/mailbox-chat.js:7872-7903
```
7872:         function refreshConversationQuiet() {
7873:             const contactId = Number(instance.selectedContactId);
7874:             if (!Number.isFinite(contactId) || contactId <= 0) return;
7875:             const myEpoch = instance.convEpoch;
7876:             const params = new URLSearchParams();
7877:             params.set("limit", String(MESSAGE_LIMIT));
7878:             const scopeAccount = instance.conversation.accountScope || "";
7879:             if (scopeAccount) params.set("accountCode", scopeAccount);
7880:             hostApi()(`/api/mail/mailbox/conversations/${contactId}/messages?${params.toString()}`).then((msgData) => {
7881:                 if (instance.disposed || myEpoch !== instance.convEpoch) return;
7882:                 const serverItems = (msgData && Array.isArray(msgData.items)) ? msgData.items : [];
7883:                 if (serverItems.length === 0 && (instance.conversation.items || []).length === 0) {
7884:                     instance.conversation.nextBefore = (msgData && msgData.nextBefore) || null;
7885:                     instance.conversation.hasMore = !!(msgData && msgData.hasMore);
7886:                     return;
7887:                 }
7888:                 instance.conversation.items = mergeServerIntoWindow(instance.conversation.items, serverItems);
7889:                 instance.conversation.nextBefore = (msgData && msgData.nextBefore) || null;
7890:                 instance.conversation.hasMore = !!(msgData && msgData.hasMore);
7891:                 renderTimeline();
7892:                 checkInboundChangeQuiet();
7893:                 saveConversationState();
7894:                 // c3（T-1）：现有刷新成功后按同一 contact 重读推荐（渲染只重绘状态行）。
7895:                 loadContactTiming(contactId);
7896:                 // 02（T3.4）：已有会话刷新成功也是挂起状态 GET 的统一入口。
7897:                 loadSuspensionState(contactId);
7898:             }).catch(() => {});
7899:         }
7900: 
7901:         function checkInboundChangeQuiet() {
7902:             const summary = instance.selectedSummary || findSummaryByContactId(instance.selectedContactId);
7903:             if (!summary) return;
```

## src/main/resources/static/mailbox-chat.js:8395-8414
```
8395:         function onDocumentKeyDown(event) {
8396:             if (instance.disposed) return;
8397:             if (event.key !== "Escape") return;
8398:             if (reasonFormSection(null)) {
8399:                 if (event.preventDefault) event.preventDefault();
8400:                 cancelReasonForm();
8401:                 return;
8402:             }
8403:             if (instance.processConfirm.key != null) {
8404:                 if (event.preventDefault) event.preventDefault();
8405:                 cancelProcessConfirm();
8406:             }
8407:         }
8408: 
8409:         function onOutsideFilterClick(event) {
8410:             if (instance.disposed) return;
8411:             const target = event.target;
8412:             if (!target) return;
8413:             // 02（S-2/I-5）：状态菜单外部点击关闭；触发按钮自身的点击由 onClick 处理。
8414:             if (instance.progressMenu.contactId != null) {
```

## src/main/resources/static/mailbox-chat.js:8682-8718
```
8682:         function unmount() {
8683:             if (instance.disposed) return;
8684:             saveCurrentConversation();
8685:             if (instance.mobileMedia) {
8686:                 if (typeof instance.mobileMedia.removeEventListener === "function") instance.mobileMedia.removeEventListener("change", onMobileViewportChange);
8687:                 else if (typeof instance.mobileMedia.removeListener === "function") instance.mobileMedia.removeListener(onMobileViewportChange);
8688:             }
8689:             // 右栏/根节点清空前必须先归还详情面板 lease（I-5）。
8690:             closeProgressMenu({ restoreFocus: false });
8691:             clearUnmatchedState();
8692:             resetSuspensionState();
8693:             instance.disposed = true;
8694:             clearTimeout(instance.searchTimer);
8695:             clearTimeout(instance.saveTimer);
8696:             teardownConversationSubViews();
8697:             handlers.forEach((pair) => host.removeEventListener(pair[0], pair[1]));
8698:             handlers.length = 0;
8699:             portalHandlers.forEach((pair) => {
8700:                 const [root, type, fn] = pair;
8701:                 if (root && typeof root.removeEventListener === "function") root.removeEventListener(type, fn);
8702:             });
8703:             portalHandlers.length = 0;
8704:             const doc = docRoot();
8705:             if (doc && typeof doc.removeEventListener === "function") {
8706:                 doc.removeEventListener("click", onOutsideFilterClick);
8707:                 doc.removeEventListener("keydown", onDocumentKeyDown);
8708:                 doc.removeEventListener("meeting-calendar-changed", onMeetingScheduleChanged);
8709:             }
8710:             restoreRefreshButton();
8711:             restoreLegacyFilterNodes();
8712:             removePortalRoot();
8713:             setRefined(false);
8714:             if (instance.host) instance.host.innerHTML = "";
8715:             instances.delete(instance.host);
8716:         }
8717: 
8718:         function refreshFromHost() {
```

## src/main/resources/static/mailbox-chat.css:98-114
```
98: .mail-chat .mc-close{display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border:0;border-radius:5px;background:transparent;color:#91a1b7;font-size:21px;cursor:pointer}
99: .mail-chat .mc-close:hover{background:#edf3ff;color:#2451b9}
100: .mail-chat .mc-close:active{background:#dbeafe}
101: .mail-chat .mc-close:disabled{opacity:.45;cursor:not-allowed}
102: .mail-chat .mc-header{flex:none;background:#fff;padding:17px 22px 14px}
103: .mail-chat .mc-header-meta{display:flex;align-items:center;flex-wrap:wrap;gap:8px;flex-basis:100%;font-size:11px;color:#8a9bb2}
104: .mail-chat .mc-identity h2{font-size:17px;font-weight:600;letter-spacing:-.25px}
105: .mail-chat .mc-badge{border-radius:5px;font-size:10px;line-height:1.6;padding:2px 7px}
106: .mail-chat .mc-badge[data-tone=pending]{background:#fff5e9;border-color:#f6dfc6;color:#bb7838}
107: .mail-chat .mc-timeline-head{flex:none;display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 22px;color:#8b9bb1;font-size:11px}
108: .mail-chat .mc-position-hint{margin-left:auto;font-size:10px;color:#8b9bb1}
109: .mail-chat .mc-text-button{display:inline-flex;align-items:center;gap:4px;border:0;border-radius:4px;background:transparent;color:#6482b2;font:inherit;font-size:11px;line-height:1.5;padding:3px 0;cursor:pointer}
110: .mail-chat .mc-text-button:hover{color:#244ca9;background:#edf3ff}
111: .mail-chat .mc-text-button:active{background:#dbeafe}
112: .mail-chat .mc-text-button:disabled{opacity:.45;cursor:not-allowed}
113: .mail-chat .mc-message{width:min(92%,820px);padding:14px 17px;border-radius:11px;box-shadow:0 2px 6px #334b7210}
114: .mail-chat .mc-message h3{font-size:12px;color:#4d617d;font-weight:600;margin-bottom:11px;line-height:1.55}
```

## src/main/resources/static/mailbox-chat.css:118-123
```
118: .mail-chat .mc-done{margin-left:auto;color:#4c927b;font-size:11px}
119: .mail-chat .mc-translation{white-space:pre-wrap;overflow-wrap:anywhere;padding:12px 14px;margin-top:12px;border-left:2px solid #b8ccef;border-radius:0 7px 7px 0;background:#f3f7ff;color:#59708f;font-size:12px;line-height:1.85}
120: .mail-chat .mc-tag-row{display:flex;align-items:center;flex-wrap:wrap;gap:6px;margin-top:12px}
121: .mail-chat .mc-tag-row .inbound-tag-chip,.mail-chat .mc-header-meta .expert-tag{font-size:10px;line-height:1.6;padding:2px 6px;border-radius:5px;margin:0}
122: .mail-chat .mc-inline-error{padding:8px 0;color:#be123c;font-size:11px;line-height:1.6}
123: .mail-chat .mc-section{background:#fff;border-radius:10px}
```

## src/main/resources/static/styles.css:1-80
```
1: :root {
2:     /* Brand — modern business blue */
3:     --primary: #1e40af;
4:     --primary-hover: #1e3a8a;
5:     --primary-active: #172554;
6:     --primary-rgb: 30, 64, 175;
7:     --primary-light: rgba(var(--primary-rgb), 0.07);
8:     --primary-tint: rgba(var(--primary-rgb), 0.1);
9: 
10:     --bg-main: #f5f7fb;
11:     --bg-sidebar: #ffffff;
12:     --bg-sidebar-hover: rgba(var(--primary-rgb), 0.06);
13:     --bg-sidebar-active: rgba(var(--primary-rgb), 0.09);
14: 
15:     --panel-bg: rgba(255, 255, 255, 0.55);
16:     --panel-border: rgba(15, 23, 42, 0.08);
17:     --line: rgba(15, 23, 42, 0.055);
18:     --border: rgba(15, 23, 42, 0.11);
19:     --surface: rgba(15, 23, 42, 0.022);
20: 
21:     --text-main: #1e293b;
22:     --text-muted: #94a3b8;
23:     --text-sidebar: #64748b;
24:     --text-sidebar-active: #1e293b;
25:     --text-secondary: #475569;
26:     --text-strong: #334155;
27:     --ink: #1e293b;
28:     --bg-subtle: #f8fafc;
29:     --border-strong: #cbd5e1;
30:     --primary-bright: #3b82f6;
31: 
32:     --success: #059669;
33:     --success-rgb: 5, 150, 105;
34:     --success-bg: rgba(var(--success-rgb), 0.08);
35:     --success-border: rgba(var(--success-rgb), 0.18);
36:     --green: var(--success);
37: 
38:     --error: #e11d48;
39:     --error-rgb: 225, 29, 72;
40:     --error-bg: rgba(var(--error-rgb), 0.07);
41:     --error-border: rgba(var(--error-rgb), 0.16);
42:     --error-strong: #be123c;
43:     --red: var(--error);
44: 
45:     --warning: #d97706;
46:     --warning-rgb: 217, 119, 6;
47:     --warning-bg: rgba(var(--warning-rgb), 0.08);
48:     --warning-border: rgba(var(--warning-rgb), 0.2);
49:     --warning-strong: #b45309;
50:     --warning-bright: #f59e0b;
51:     --amber: var(--warning);
52: 
53:     --info: #0ea5e9;
54:     --info-rgb: 14, 165, 233;
55:     --info-bg: rgba(var(--info-rgb), 0.08);
56:     --info-border: rgba(var(--info-rgb), 0.2);
57: 
58:     --z-sticky: 10;
59:     --z-dropdown: 20;
60:     --z-overlay: 50;
61:     --z-drawer: 60;
62:     --z-modal: 1000;
63:     --z-confirm: 1200;
64:     --z-toast: 9999;
65: 
66:     --glass-border: rgba(255, 255, 255, 0.5);
67:     --glass-shadow: 0 8px 32px rgba(var(--primary-rgb), 0.1);
68:     --glass-blur: blur(16px);
69: 
70:     --radius-sm: 7px;
71:     --radius-md: 10px;
72:     --radius-lg: 18px;
73: 
74:     --shadow-sm: 0 1px 2px rgba(15, 23, 42, 0.04);
75:     --shadow-md: 0 1px 3px rgba(15, 23, 42, 0.06), 0 1px 2px rgba(15, 23, 42, 0.03);
76:     --shadow-lg: 0 10px 28px -8px rgba(15, 23, 42, 0.14), 0 2px 6px rgba(15, 23, 42, 0.05);
77:     --shadow-xl: 0 20px 48px -12px rgba(15, 23, 42, 0.2), 0 4px 12px rgba(15, 23, 42, 0.06);
78:     --shadow: var(--shadow-md);
79: 
80:     --transition: all 0.15s ease;
```

## src/main/resources/static/styles.css:802-870
```
802: .button {
803:     display: inline-flex;
804:     align-items: center;
805:     justify-content: center;
806:     gap: 6px;
807:     min-height: 32px;
808:     height: 32px;
809:     padding: 0 12px;
810:     border-radius: var(--radius-sm);
811:     font-weight: 500;
812:     font-size: 12px;
813:     cursor: pointer;
814:     border: 1px solid var(--border);
815:     background-color: transparent;
816:     color: var(--text-main);
817:     transition: transform 0.12s ease, box-shadow 0.15s ease, background-color 0.15s ease, border-color 0.15s ease, opacity 0.1s ease;
818:     outline: none;
819:     user-select: none;
820:     font-family: var(--font-body);
821:     position: relative;
822:     overflow: hidden;
823: }
824: 
825: .button:hover {
826:     border-color: rgba(15, 23, 42, 0.2);
827:     background-color: var(--surface);
828:     transform: translateY(-1px);
829:     box-shadow: 0 2px 6px rgba(15, 23, 42, 0.08);
830: }
831: 
832: .button:active {
833:     transform: translateY(0) scale(0.97);
834:     box-shadow: none;
835:     opacity: 0.85;
836: }
837: 
838: .button.primary {
839:     background-image: linear-gradient(180deg, var(--primary-bright), var(--primary));
840:     background-color: var(--primary);
841:     border-color: transparent;
842:     color: #ffffff;
843:     font-weight: 600;
844:     box-shadow: 0 1px 2px rgba(var(--primary-rgb), 0.4), inset 0 1px 0 rgba(255,255,255,0.18);
845: }
846: 
847: .button.primary:hover {
848:     background-image: linear-gradient(180deg, #2f7bff, var(--primary-hover));
849:     box-shadow: 0 4px 14px rgba(var(--primary-rgb), 0.35), inset 0 1px 0 rgba(255,255,255,0.18);
850: }
851: 
852: .button.secondary {
853:     background-color: var(--primary-light);
854:     border-color: rgba(var(--primary-rgb), 0.12);
855:     color: var(--primary);
856: }
857: 
858: .button.secondary:hover {
859:     background-color: rgba(var(--primary-rgb), 0.1);
860: }
861: 
862: .button.danger {
863:     background-color: var(--error-bg);
864:     border-color: var(--error-border);
865:     color: var(--error);
866: }
867: 
868: .button.danger:hover {
869:     background-color: rgba(var(--error-rgb), 0.1);
870: }
```

## src/main/resources/static/styles.css:12413-12449
```
12413: /* contact-timing S-1 */
12414: .mail-chat .contact-timing{display:inline-flex;align-items:center;flex-wrap:wrap;gap:9px;margin-left:auto;padding-left:10px;border-left:1px solid #e1e8f2;min-height:22px;max-width:100%;font-size:11px;line-height:1.6}
12415: .mail-chat .contact-timing-location{max-width:140px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#61748e}
12416: .mail-chat .contact-timing-recommend{display:inline-flex;align-items:center;gap:5px;white-space:nowrap;color:#8494aa;font-size:11px}
12417: .mail-chat .contact-timing-recommend strong{font-size:12px;font-weight:600;color:#285ac0;font-variant-numeric:tabular-nums}
12418: .mail-chat .contact-timing-info{justify-content:center;width:20px;height:22px;padding:0;font-size:14px;color:#8a9bb2}
12419: .mail-chat .contact-timing-note{color:#8494aa;font-size:11px}
12420: .mail-chat .contact-timing .mc-text-button:hover{color:#244ca9;background:#edf3ff}
12421: .mail-chat .contact-timing .mc-text-button:active{background:#dbeafe}
12422: .mail-chat .contact-timing .mc-text-button:disabled{opacity:.45;cursor:not-allowed}
12423: .mail-chat .contact-timing .mc-text-button:focus-visible{outline:2px solid #6389d3;outline-offset:2px}
12424: 
12425: /* contact-timing S-2 */
12426: .contact-timing-dialog{position:fixed;inset:0;margin:auto;padding:0;width:440px;max-width:calc(100vw - 32px);height:fit-content;max-height:calc(100dvh - 48px);overflow:auto;border:1px solid #d9e3f1;border-radius:14px;background:#fff;color:#475d79;box-shadow:0 20px 90px #17325730;font-family:var(--font-body);font-size:12px;line-height:1.6}
12427: .contact-timing-dialog[open]{display:flex;flex-direction:column}
12428: .contact-timing-dialog[data-kind=evidence]{width:360px}
12429: .contact-timing-dialog,.contact-timing-dialog *{box-sizing:border-box}
12430: .contact-timing-dialog [hidden]{display:none!important}
12431: .contact-timing-dialog::backdrop{background:#172c4738;backdrop-filter:blur(2px)}
12432: .contact-timing-dialog header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;padding:18px 20px;border-bottom:1px solid #edf1f7}
12433: .contact-timing-dialog h3{margin:0;color:#475d79;font-size:16px;font-weight:600;line-height:1.6}
12434: .contact-timing-dialog header p{margin:6px 0 0;color:#8799b0;font-size:11px;line-height:1.6;overflow-wrap:anywhere}
12435: .contact-timing-dialog form{margin:0;min-width:0}
12436: .contact-timing-body{display:flex;flex-direction:column;gap:12px;padding:20px;min-width:0}
12437: .contact-timing-field{display:flex;flex-direction:column;gap:8px;color:#61748e;font-size:12px}
12438: .contact-timing-field select{width:100%;min-width:0;height:37px;min-height:37px;margin:0;padding:0 10px;border:1px solid #dce4ef;border-radius:7px;background:#fcfdff;color:#475d79;font:inherit;font-size:12px}
12439: .contact-timing-field select:hover{border-color:#93b4ec}
12440: .contact-timing-field select:focus-visible{outline:2px solid #6389d3;outline-offset:2px}
12441: .contact-timing-field select:disabled{opacity:.55;cursor:not-allowed}
12442: .contact-timing-dialog .contact-timing-help{margin:0;color:#71849f;font-size:11px;line-height:1.7;overflow-wrap:anywhere}
12443: .contact-timing-dialog .contact-timing-error{margin:0;color:#be123c;font-size:12px;line-height:1.6;overflow-wrap:anywhere}
12444: .contact-timing-range{display:flex;flex-direction:column;gap:4px;color:#61748e;font-size:12px;font-variant-numeric:tabular-nums}
12445: .contact-timing-range strong{color:#285ac0;font-size:13px;font-weight:600}
12446: .contact-timing-history{display:flex;flex-direction:column;gap:6px;margin:0;padding:10px 0 0;list-style:none;border-top:1px solid #edf1f7;color:#71849f;font-size:11px;font-variant-numeric:tabular-nums}
12447: .contact-timing-history li{display:flex;justify-content:space-between;flex-wrap:wrap;gap:4px 12px}
12448: .contact-timing-dialog footer{display:flex;justify-content:flex-end;gap:9px;padding:14px 20px;border-top:1px solid #edf1f7}
12449: .contact-timing-dialog .button{min-height:33px;height:33px;padding:0 13px;font-size:12px}
```

## src/main/resources/db/migration/V1__create_business_tables.sql:79-96
```
79: CREATE TABLE expert_contact (
80:     id BIGINT PRIMARY KEY AUTO_INCREMENT,
81:     campaign_id BIGINT NOT NULL,
82:     orcid_id VARCHAR(64) NOT NULL,
83:     expert_email VARCHAR(255) NOT NULL,
84:     expert_name VARCHAR(255),
85:     current_status VARCHAR(64) NOT NULL DEFAULT 'NEW',
86:     last_mail_at DATETIME,
87:     last_reply_at DATETIME,
88:     manual_handoff_required TINYINT(1) NOT NULL DEFAULT 0,
89:     closed_reason VARCHAR(255),
90:     created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
91:     updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
92:     UNIQUE KEY uk_campaign_expert (campaign_id, orcid_id),
93:     CONSTRAINT fk_expert_contact_campaign
94:         FOREIGN KEY (campaign_id) REFERENCES campaign(id)
95: );
96: 
```

## src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:9-39
```
9: interface ExpertContactRepository : CrudRepository<ExpertContact, Long> {
10:     fun existsByCampaignIdAndOrcidId(campaignId: Long, orcidId: String): Boolean
11:     fun existsByOrcidId(orcidId: String): Boolean
12:     fun findAllByCurrentStatus(currentStatus: String): List<ExpertContact>
13:     fun findByCampaignIdAndOrcidId(campaignId: Long, orcidId: String): ExpertContact?
14: 
15:     fun findFirstByExpertEmailOrderByUpdatedAtDesc(expertEmail: String): ExpertContact?
16: 
17:     fun findFirstByOrcidIdOrderByUpdatedAtDesc(orcidId: String): ExpertContact?
18: 
19:     fun findByOrcidIdIn(orcidIds: List<String>): List<ExpertContact>
20: 
21:     fun findAllByOrderByUpdatedAtDesc(): List<ExpertContact>
22: 
23:     fun findAllByCurrentStatusOrderByUpdatedAtDesc(currentStatus: String): List<ExpertContact>
24: 
25:     fun findAllByCampaignIdOrderByUpdatedAtDesc(campaignId: Long): List<ExpertContact>
26: 
27:     fun findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(
28:         campaignId: Long,
29:         currentStatus: String
30:     ): List<ExpertContact>
31: 
32:     fun findAllByExpertNameContainingIgnoreCaseOrExpertEmailContainingIgnoreCaseOrderByUpdatedAtDesc(
33:         expertName: String,
34:         expertEmail: String
35:     ): List<ExpertContact>
36: 
37:     fun findAllByOrcidIdContainingIgnoreCaseOrExpertNameContainingIgnoreCaseOrExpertEmailContainingIgnoreCaseOrderByUpdatedAtDesc(
38:         orcidId: String,
39:         expertName: String,
```

## src/main/kotlin/com/weibo/talentintroduction/campaign/repository/ExpertContactRepository.kt:66-110
```
66:     @Modifying
67:     @Query("UPDATE expert_contact SET country = :country WHERE id = :id")
68:     fun updateCountryById(id: Long, country: String?): Int
69: 
70:     @Modifying
71:     @Query("""
72:         UPDATE expert_contact
73:            SET bound_sender_account_code = :accountCode,
74:                sender_account_bound_at = :boundAt
75:          WHERE id = :id
76:     """)
77:     fun updateBindingById(id: Long, accountCode: String?, boundAt: LocalDateTime?): Int
78: 
79:     @Modifying
80:     @Query("""
81:         UPDATE expert_contact
82:            SET bound_sender_account_code = :accountCode,
83:                sender_account_bound_at = :changedAt,
84:                sender_account_changed = true,
85:                sender_account_changed_at = :changedAt
86:          WHERE id = :id
87:     """)
88:     fun rebindSenderAccountById(id: Long, accountCode: String, changedAt: LocalDateTime): Int
89: 
90:     @Modifying
91:     @Query("""
92:         UPDATE expert_contact
93:            SET bound_sender_account_code = :toAccountCode,
94:                sender_account_bound_at = :migratedAt
95:          WHERE bound_sender_account_code = :fromAccountCode
96:     """)
97:     fun migrateBindingByAccount(
98:         fromAccountCode: String,
99:         toAccountCode: String,
100:         migratedAt: LocalDateTime
101:     ): Int
102: 
103:     @Modifying
104:     @Query("""
105:         UPDATE expert_contact
106:            SET sender_account_changed = false,
107:                sender_account_changed_at = NULL
108:          WHERE id = :id
109:     """)
110:     fun clearSenderChangeMarkById(id: Long): Int
```

## src/main/resources/static/app.js:4686-4716
```
4686: async function api(path, options = {}) {
4687:     // I-7（c3）：可选有限超时 —— 新模式的状态/启动请求必须有出口，旧模式耗时请求照旧不设超时。
4688:     const { timeoutMs, ...fetchOptions } = options;
4689:     const controller = timeoutMs ? new AbortController() : null;
4690:     const timer = controller ? setTimeout(() => controller.abort(), timeoutMs) : null;
4691:     let response;
4692:     try {
4693:         response = await fetch(`${contextPath}${path}`, {
4694:             headers: { "Content-Type": "application/json", ...(fetchOptions.headers || {}) },
4695:             ...fetchOptions,
4696:             ...(controller ? { signal: controller.signal } : {})
4697:         });
4698:     } catch (e) {
4699:         if (controller && controller.signal.aborted) {
4700:             throw new Error(`请求超时（${Math.round(timeoutMs / 1000)} 秒）`);
4701:         }
4702:         throw e;
4703:     } finally {
4704:         if (timer) clearTimeout(timer);
4705:     }
4706:     await handleAuthResponse(response);
4707:     const text = await response.text();
4708:     const data = text ? JSON.parse(text) : null;
4709:     if (!response.ok) {
4710:         const message = data?.message || `${response.status} ${response.statusText}`;
4711:         const error = new Error(message);
4712:         error.status = response.status;
4713:         error.data = data;
4714:         throw error;
4715:     }
4716:     return data;
```

## src/test/js/mailboxChatBehavior.test.js:1110-1135
```
1110:         calls,
1111:         timers,
1112:         contactLocations,
1113:         mediaListeners,
1114:         resize: (mobile) => { media.matches = mobile; mediaListeners.forEach((fn) => fn({ matches: mobile })); },
1115:         runFrames: () => { while (frames.length) frames.shift()(); },
1116:         runTimers: () => { while (timers.length) { const fn = timers.shift(); fn(); } }
1117:     };
1118: }
1119: 
1120: function expertA(extra) {
1121:     return Object.assign({
1122:         contactId: 1,
1123:         name: "专家A",
1124:         email: "a@example.edu",
1125:         orcid: "0000-0001",
1126:         accountCodes: ["acc1"],
1127:         followed: false,
1128:         receivedCount: 2,
1129:         sentCount: 2,
1130:         failedCount: 0,
1131:         pendingCount: 1,
1132:         waitingReply: false,
1133:         expertTags: ["学术科研", "重点关注"],
1134:         latestMessage: { source: "INBOUND_PROCESSING", id: 101, direction: "INBOUND", subject: "Question 1", time: "2026-09-07T03:00:00", sendStatus: null },
1135:         latestInbound: { processingId: 101, accountCode: "acc1", messageId: "m101", receivedAt: "2026-09-07T03:00:00" },
```

## src/test/js/mailboxChatBehavior.test.js:1280-1350
```
1280:     const { doc, host } = dom || createDom();
1281:     ctx.doc = doc;
1282:     ctx.host = host;
1283:     ctx.sandbox.document = doc;
1284:     ctx.sandbox.MailboxChat.mount(host, mountOptions || { filters: {} });
1285:     return ctx;
1286: }
1287: 
1288: async function bootChat(serverOverrides, mountOptions, dom) {
1289:     const ctx = mountChat(serverOverrides || {}, mountOptions, dom);
1290:     await flush();
1291:     return ctx;
1292: }
1293: 
1294: function personButtons(host) {
1295:     return host.querySelectorAll(".mc-person-main");
1296: }
1297: 
1298: function click(el) {
1299:     el.dispatchEvent(new MiniEvent("click", { bubbles: true }));
1300: }
1301: 
1302: function inputEvent(el) {
1303:     el.dispatchEvent(new MiniEvent("input", { bubbles: true }));
1304: }
1305: 
1306: // 浏览器语义模拟：真实 contenteditable 的 innerText 由渲染后的 DOM 派生，与 innerHTML
1307: // 不同源（迷你 DOM 不做布局推导）。人工富文本用例需要「HTML 含连续 <br>、纯文本含连续
1308: // 换行」这一真实组合，故显式注入该元素的 innerText 取值。
1309: function setEditorContent(editor, html, text) {
1310:     editor.innerHTML = html;
1311:     let innerTextValue = String(text == null ? "" : text);
1312:     Object.defineProperty(editor, "innerText", {
1313:         configurable: true,
1314:         get: () => innerTextValue,
1315:         set: (next) => { innerTextValue = String(next == null ? "" : next); }
1316:     });
1317:     return editor;
1318: }
1319: 
1320: function changeEvent(el) {
1321:     el.dispatchEvent(new MiniEvent("change", { bubbles: true }));
1322: }
1323: 
1324: function keyEvent(el, key) {
1325:     const event = new MiniEvent("keydown", { bubbles: true });
1326:     event.key = key;
1327:     el.dispatchEvent(event);
1328: }
1329: 
1330: function toggleOpen(el) {
1331:     el.setAttribute("open", "");
1332:     el.dispatchEvent(new MiniEvent("toggle", { bubbles: true }));
1333: }
1334: 
1335: function scrollEvent(el) {
1336:     el.dispatchEvent(new MiniEvent("scroll", { bubbles: true }));
1337: }
1338: 
1339: function docById(ctx, id) {
1340:     return ctx.doc.getElementById(id);
1341: }
1342: 
1343: function popoverField(ctx, id) {
1344:     return docById(ctx, id) || ctx.host.querySelector(`#${id}`);
1345: }
1346: 
1347: function conversationsRequests(ctx) {
1348:     // 02：Tab 计数请求（page=0&size=1）不算列表请求。
1349:     return ctx.calls.api.filter((entry) => entry.url.startsWith("/api/mail/mailbox/conversations?")
1350:         && queryOf(entry.url).get("size") !== "1");
```
