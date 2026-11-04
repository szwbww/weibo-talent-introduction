# 前端逐字基线

## src/main/resources/static/mailbox-chat.js:2223

```text
2223:                 ? `<span class="mc-person-tags-unavailable" title="标签暂不可用">标签暂不可用</span>`
2224:                 : (tagNames.length === 0 ? "" : `<span class="mc-person-tags" title="专家标签：${escapeText(tagNames.join("、"))}">${tagNames.map((name) => `<span class="mc-person-tag">${escapeText(name)}</span>`).join("")}</span>`);
2225:             const ariaLabel = (tagNames !== null && tagNames.length > 0
2226:                 ? `查看${item.name || item.email || ""}往来邮件；专家标签：${tagNames.join("、")}`
2227:                 : `查看${item.name || item.email || ""}往来邮件`)
2228:                 + `；${lastReplyAriaSuffix(lastReplyDisplay(item))}`;
2229:             return `
2230:                 <div class="mc-person mailbox-progress-card" data-replied="${instance.chip === CHIP_REPLIED ? "true" : "false"}" data-active="${active ? "true" : "false"}" data-contact-id="${escapeText(item.contactId)}">
2231:                     <button class="mc-person-main" type="button" data-action="mc-select-expert" data-contact-id="${escapeText(item.contactId)}" aria-label="${escapeText(ariaLabel)}"${active ? ' aria-current="true"' : ""}>
2232:                         <span class="mc-person-heading"><strong title="${escapeText(cardName)}">${escapeText(cardName)}</strong>${Number(item.reviewPendingCount) > 0 ? `<span class="mc-badge" data-tone="pending" data-role="review-pending-count">待审核 ${Number(item.reviewPendingCount)}</span>` : ""}</span>
2233:                         ${item.accessLevel === "FULL" ? '<span class="mc-badge mc-grant-badge">user1: 写</span>' : ""}
2234:                         <small>${escapeText(accounts)}</small>
2235:                         <small>${escapeText(latestLine)}</small>
2236:                         ${lastReplyListMarkup(item)}
2237:                         <span class="mc-person-meta">
2238:                             <span class="mc-person-counts">收 ${Number(item.receivedCount) || 0} · 发 ${Number(item.sentCount) || 0}</span>
2239:                             ${tagLine}
2240:                             ${Number(instance.draftCounts.get(Number(item.contactId))) > 0 ? `<span class="mailbox-draft-count" data-role="contact-draft-count">草稿 ${Number(instance.draftCounts.get(Number(item.contactId)))}</span>` : ""}
2241:                         </span>
2242:                         <span class="calendar-summary" data-role="meeting-summary"></span>
2243:                     </button>
```

## src/main/resources/static/mailbox-chat.js:5118

```text
5118:             const tagRow = isInboundProcessing ? renderTagRow(message, key) : "";
5119:             const translationStateFor = translationState(key, displayBody);
5120:             const translationHtml = renderTranslationBlock(translationStateFor);
5121:             const canTranslate = !!displayBody;
5122:             const footerButtons = [];
5123:             if (canTranslate) {
5124:                 footerButtons.push(`<button class="mc-text-button" type="button" data-action="mc-translate" data-message-key="${escapeText(key)}">${escapeText(translateButtonLabel(translationStateFor))}</button>`);
5125:             }
5126:             if (isInboundProcessing) {
5127:                 footerButtons.push(`<button class="mc-text-button" type="button" data-action="mc-add-mail-tag" data-message-key="${escapeText(key)}">＋ 添加标签</button>`);
5128:             }
5129:             if (pending) {
5130:                 if (instance.processConfirm.key === key) {
5131:                     const processBusy = instance.processConfirm.busy;
5132:                     footerButtons.push(`<span class="mailbox-suspend-process-confirm">
5133:                         <span>确认标记为已处理？</span>
5134:                         <button type="button" class="mailbox-suspend-inline-secondary" data-action="mc-process-cancel" data-message-key="${escapeText(key)}"${processBusy ? " disabled" : ""}>取消</button>
5135:                         <button type="button" class="mailbox-suspend-inline-primary" data-action="mc-process-confirm" data-message-key="${escapeText(key)}"${processBusy ? " disabled" : ""}>${processBusy ? "处理中…" : "确认"}</button>
5136:                     </span>`);
5137:                 } else {
5138:                     const pendingDisabled = instance.auth.ready ? "" : " disabled";
5139:                     footerButtons.push(`<button class="mc-text-button mc-process mailbox-suspend-pending-action" type="button" data-action="mc-mark-resolved" data-message-key="${escapeText(key)}"${pendingDisabled}>待处理</button>`);
5140:                 }
5141:             } else if (processed) {
5142:                 const resolvedBy = instance.resolvedLabels.get(key);
5143:                 footerButtons.push(`<span class="mailbox-suspend-processed-label">已处理${resolvedBy ? ` · ${escapeText(resolvedBy)}` : ""}</span>`);
5144:             }
5145:             const footerHtml = footerButtons.length > 0
5146:                 ? `<footer>${footerButtons.join("")}</footer>`
5147:                 : "";
5148:             const completionHtml = instance.completion.anchorKey === key ? completionLineHtml() : "";
5149:             return `
5150:                 <article class="mc-message" data-direction="${direction}" data-source="${escapeText(message.source)}" data-id="${escapeText(message.id)}" data-message-key="${escapeText(key)}">
5151:                     <header><span>${who}</span>${statusBadge ? `<span>${statusBadge}</span>` : ""}</header>
5152:                     <h3>${escapeText(subject)}</h3>
5153:                     ${bodyHtml}
5154:                     ${sentMeetingHtml}
5155:                     ${sentOutboundFilesHtml}
5156:                     ${attachmentHtml}
5157:                     ${translationHtml}
5158:                     ${tagRow}
5159:                     <p class="mc-inline-error" role="alert" hidden></p>
5160:                     ${footerHtml}
5161:                     ${completionHtml}
5162:                 </article>
5163:             `;
5164:         }
5165: 
5166:         function renderStatusBadge(message, direction) {
5167:             if (direction === "INBOUND") {
5168:                 if (message.processStatus === "MANUAL_REVIEW") {
5169:                     return '<span class="mc-badge" data-tone="pending">待处理</span>';
5170:                 }
5171:                 return "";
5172:             }
5173:             if (message.sendStatus === "SENT") {
5174:                 return '<span class="mc-badge" data-tone="success">已发送</span>';
5175:             }
5176:             if (message.sendStatus === "FAILED") {
5177:                 return '<span class="mc-badge" data-tone="error">发送失败</span>';
5178:             }
5179:             return "";
5180:         }
5181: 
5182:         function renderAttachmentSummary(message) {
```

## src/main/resources/static/mailbox-chat.js:5845

```text
5845:         }
5846:         function reviewLabel(draft) {
5847:             if (draft.deleted) return "已删除";
5848:             if (draft.sendStatus === "SENT") return "已发送";
5849:             if (["UNKNOWN", "DELIVERY_UNKNOWN", "IN_PROGRESS", "DELIVERY_IN_PROGRESS"].includes(draft.sendStatus)) return "发送结果待确认，请勿重复发送";
5850:             return { DRAFT: "未提交", RETURNED: "已退回", SUBMITTED: "已提交", APPROVED: "已审核 · 尚未发送" }[draft.state] || "状态不可用";
5851:         }
5852:         function reviewSendable(draft) {
5853:             return draft && !draft.deleted && draft.state === "APPROVED" && ["NOT_SENT", "PREPARED", "FAILED_SAFE_TO_RETRY"].includes(draft.sendStatus);
5854:         }
5855:         function closeReviewConfirmation() {
5856:             const dialog = reviewNode("review-context-confirm");
5857:             if (!dialog) return;
5858:             if (dialog) { if (dialog.open) dialog.close(); dialog.remove(); }
5859:             const captured = instance.review.confirmTrigger;
5860:             const trigger = captured && captured.isConnected ? captured : reviewNode("review-selection");
```

## src/main/resources/static/mailbox-chat.js:6100

```text
6100:         async function sendAdminReview() {
6101:             const r = instance.review, ctx = reviewCapture(); if (!reviewSendable(r.detail) || r.busy) return;
6102:             r.busy = true; renderAdminReview(); reviewError("");
6103:             try {
6104:                 if (!await checkReviewContext(true) || !reviewCurrent(ctx)) return;
6105:                 const body = { expectedVersion: ctx.version, confirmationEventId: r.confirmationEventId || null };
6106:                 let result;
6107:                 try { result = await hostApi()(`${REVIEW_PATH}/${ctx.id}/send`, { method: "POST", body: JSON.stringify(body) }); }
6108:                 catch (error) {
6109:                     if (!reviewCurrent(ctx)) return;
6110:                     const data = error.data, confirm = hostFn("confirmManualReplySafety");
6111:                     if (!data || data.code !== "MANUAL_SEND_SAFETY_BLOCKED" || !Array.isArray(data.findings) || !confirm) throw error;
6112:                     const confirmation = await confirm(data);
6113:                     if (!confirmation || !reviewCurrent(ctx)) return;
6114:                     if (!await checkReviewContext(true) || !reviewCurrent(ctx)) return;
6115:                     result = await hostApi()(`${REVIEW_PATH}/${ctx.id}/send`, { method: "POST", body: JSON.stringify({ ...body, ...confirmation }) });
6116:                 }
6117:                 if (!reviewCurrent(ctx) || result === null) return;
6118:                 const detail = await hostApi()(`${REVIEW_PATH}/${ctx.id}`); if (!reviewCurrent(ctx)) return;
6119:                 r.detail = detail; r.epoch += 1; r.confirmationEventId = null; r.confirmedToken = null;
6120:                 r.message = "";
6121:                 refreshConversationQuiet(); await fetchList({ page: instance.list.page });
6122:             } catch (error) {
6123:                 if (!reviewCurrent(ctx)) return;
6124:                 if (error.data && error.data.code === "REVIEW_CONTEXT_CHANGED") {
6125:                     r.confirmationEventId = null; r.confirmedToken = null; await checkReviewContext(true);
6126:                 } else {
6127:                     try { const detail = await hostApi()(`${REVIEW_PATH}/${ctx.id}`); if (reviewCurrent(ctx)) r.detail = detail; }
6128:                     catch (_) { if (reviewCurrent(ctx)) r.detail = { ...r.detail, sendStatus: "UNKNOWN" }; }
6129:                     if (reviewCurrent(ctx)) reviewError(error.message || "发送结果待确认，请刷新查看");
6130:                 }
6131:             } finally { if (instance.review === r) { r.busy = false; renderAdminReview(); } }
```

## src/main/resources/static/mailbox-chat.js:9337

```text
9337:         function sendManualReply() {
9338:             if (instance.review.mode !== "manual") return;
9339:             const rec = currentDraftRecord();
9340:             if (!rec || rec.preparing || rec.sending || rec.conflict || rec.closed || !rec.loaded || rec.error === "load" || rec.readOnly) return;
9341:             if (outboundAttachmentsBlockSend()) {
9342:                 hostShowStatus("附件正在上传或上传失败，请等待上传完成或移除失败附件后再发送", "error");
9343:                 return;
9344:             }
9345:             rec.preparing = true;
9346:             const key = rec.key;
9347:             return (async () => {
9348:                 try {
9349:                     let seq;
9350:                     do {
9351:                         if (currentTargetKey() !== key || !draftOwnerLive(rec)) return;
9352:                         saveDraftFromInputs({ force: true });
9353:                         ensureOutboundRequestId();
9354:                         seq = rec.seq;
9355:                         await flushDrafts();
9356:                     } while (seq !== rec.seq);
9357:                     const detail = await hostApi()(`/api/mail/mailbox/drafts/${rec.id}`);
9358:                     if (currentTargetKey() !== key || !draftOwnerLive(rec)) return;
9359:                     if (seq !== rec.seq) { rec.preparing = false; return sendManualReply(); }
9360:                     if (detail.state !== "ACTIVE" || Number(detail.version) !== rec.version
9361:                         || !draftContentEqual(draftFromDetail(detail), rec.map.get(key))) {
9362:                         rec.conflict = true;
9363:                         refreshDraftStatus();
9364:                         return;
9365:                     }
9366:                     acceptDraftDetail(rec, detail, false);
9367:                     if (["DELIVERY_UNKNOWN", "DELIVERY_IN_PROGRESS"].includes(detail.sendAttemptStatus)) {
9368:                         refreshDraftStatus();
9369:                         return;
9370:                     }
9371:                     return sendSavedManualReply(rec, seq);
9372:                 } catch (err) {
9373:                     hostShowStatus(err.message || "保存失败，内容尚未同步到服务器", "error");
9374:                 } finally { rec.preparing = false; }
9375:             })();
```

## src/main/resources/static/mailbox-chat.js:9480

```text
9480:             }
9481:             // I-2：异步前捕获 draftsMap/owner/key/revision/requestBody；不回调里再取。
9482:             const contactId = Number(instance.selectedContactId);
9483:             const ownerKey = conversationCacheKey(instance.user, instance.conversation.accountScope || "", contactId);
9484:             requestBody.draftRef = { id: rec.id, version: rec.version };
9485:             const inFlightKey = meeting ? `${ownerKey}|${key}` : null;
9486:             if (inFlightKey && meetingInFlight.has(inFlightKey)) {
9487:                 hostShowStatus("该回复目标已有发送中的会议回复，请稍候", "error");
9488:                 return;
9489:             }
9490:             if (inFlightKey) meetingInFlight.add(inFlightKey);
9491:             // 07（I-3）：带已就绪附件的发送同样锁住当前 owner 的编辑/附件增删；其他专家不受影响。
9492:             const lockedCompose = !!meeting || attachmentItems.length > 0;
9493:             instance.manual.busy = true;
9494:             rec.sending = true;
9495:             refreshDraftStatus();
9496:             setSendButtonDisabled(true);
9497:             if (lockedCompose) setManualComposeSending(true);
9498:             const adapter = conversationSend
9499:                 ? hostFn("mcHostSendConversationRichReply")
9500:                 : hostFn("mcHostSendRichReply");
9501:             const request = adapter
9502:                 ? (conversationSend
9503:                     ? adapter(contactId, requestBody)
9504:                     : adapter(processingId, requestBody))
9505:                 : Promise.reject(new Error("发送能力不可用"));
9506:             return Promise.resolve(request).catch(() => false).then(async () => {
9507:                 // HTTP/host Boolean is not the draft-close authority.
9508:                 try {
9509:                     const detail = await hostApi()(`/api/mail/mailbox/drafts/${rec.id}`);
9510:                     if (!draftOwnerLive(rec)) return;
9511:                     const stillCurrent = !instance.disposed && currentTargetKey() === key;
9512:                     if (stillCurrent) saveDraftFromInputs();
9513:                     if (Number(detail.version) < rec.version) return;
9514:                     if (detail.state === "ACTIVE" && Number(detail.version) !== rec.version && rec.seq > sentSeq) {
9515:                         const remote = draftFromDetail(detail);
9516:                         if (draftContentEqual(remote, rec.map.get(key))) rec.ack = rec.seq;
9517:                         else if (rec.submitted && draftContentEqual(remote, {
9518:                             subject: rec.submitted.snapshot.subject, html: rec.submitted.snapshot.html,
9519:                             text: rec.submitted.snapshot.text, ...rec.submitted.snapshot.context })) {
9520:                             rec.ack = Math.max(rec.ack, rec.submitted.seq);
9521:                         } else rec.conflict = true;
9522:                     }
9523:                     acceptDraftDetail(rec, detail, false);
9524:                     rec.sendUnconfirmed = false;
9525:                     if (detail.state === "SENT" || detail.state === "DISCARDED") {
9526:                         clearTimeout(rec.timer);
9527:                         if (rec.seq === sentSeq) {
9528:                             rec.map.delete(key);
9529:                             rec.ack = rec.seq;
9530:                             rec.closed = false;
9531:                             if (stillCurrent) renderDraftEditor(rec);
9532:                         } else {
9533:                             rec.closed = true;
9534:                             rec.error = "";
9535:                             rec.conflict = false;
9536:                         }
9537:                     } else if (rec.seq === sentSeq && rec.ack === rec.seq) {
9538:                         const saved = draftFromDetail(detail);
9539:                         if (saved && !draftContentEqual(saved, rec.map.get(key))) {
9540:                             rec.map.set(key, saved);
9541:                             if (stillCurrent) renderDraftEditor(rec);
9542:                         }
9543:                     }
9544:                     if (stillCurrent && detail.sendAttemptStatus === "SENT") afterSuccessfulSend(key);
9545:                     loadDraftSummaries();
9546:                 } catch (err) {
9547:                     if (draftOwnerLive(rec)) rec.sendUnconfirmed = true;
9548:                 } finally {
9549:                     rec.sending = false;
9550:                     if (inFlightKey) meetingInFlight.delete(inFlightKey);
9551:                     if (!instance.disposed && currentTargetKey() === key && draftOwnerLive(rec)) {
9552:                         if (lockedCompose) setManualComposeSending(false);
9553:                         instance.manual.busy = false;
9554:                         refreshDraftStatus();
9555:                     }
9556:                 }
9557:             });
9558:         }
9559: 
9560:         function setSendButtonDisabled(disabled) {
9561:             const composeEl = manualComposeEl();
9562:             if (!composeEl || !composeEl.querySelector) return;
9563:             const button = composeEl.querySelector('[data-action="mc-send-manual"]');
9564:             if (button) button.disabled = disabled;
9565:         }
9566: 
9567:         function afterSuccessfulSend(clearedKey) {
9568:             const contactId = Number(instance.selectedContactId);
9569:             const myEpoch = instance.convEpoch;
9570:             const msgParams = new URLSearchParams();
9571:             msgParams.set("limit", String(MESSAGE_LIMIT));
9572:             const accountFilter = instance.conversation.accountScope || "";
9573:             if (accountFilter) msgParams.set("accountCode", accountFilter);
9574:             hostApi()(`/api/mail/mailbox/conversations/${contactId}/messages?${msgParams.toString()}`).then((msgData) => {
9575:                 if (instance.disposed || myEpoch !== instance.convEpoch) return;
9576:                 if (delegated && (!msgData || msgData.accessLevel !== "FULL")) {
9577:                     if (instance.options.onAccessChanged) instance.options.onAccessChanged();
9578:                     return;
9579:                 }
9580:                 const serverItems = (msgData && Array.isArray(msgData.items)) ? msgData.items : [];
9581:                 instance.conversation.items = mergeServerIntoWindow(instance.conversation.items, serverItems);
9582:                 instance.conversation.nextBefore = (msgData && msgData.nextBefore) || null;
9583:                 instance.conversation.hasMore = !!(msgData && msgData.hasMore);
9584:                 renderTimeline();
9585:                 checkInboundChangeQuiet();
9586:                 saveConversationState();
9587:             }).catch(() => {});
9588:             fetchList({ page: instance.list.page });
9589:             // fast-p 03（IP-3）：发送成功恰由服务端产生排期，按 contactId 使两端失效并回读
9590:             notifyMeetingScheduleChanged(contactId);
9591:         }
9592: 
9593:         // ---- quiet refresh（I-5：合并保留已加载窗口） ----
9594: 
9595:         function refreshConversationQuiet() {
9596:             const contactId = Number(instance.selectedContactId);
9597:             if (!Number.isFinite(contactId) || contactId <= 0) return;
9598:             const myEpoch = instance.convEpoch;
9599:             loadExpertNote(contactId);
9600:             const params = new URLSearchParams();
9601:             params.set("limit", String(MESSAGE_LIMIT));
9602:             const scopeAccount = instance.conversation.accountScope || "";
9603:             if (scopeAccount) params.set("accountCode", scopeAccount);
9604:             hostApi()(`/api/mail/mailbox/conversations/${contactId}/messages?${params.toString()}`).then((msgData) => {
9605:                 if (instance.disposed || myEpoch !== instance.convEpoch) return;
9606:                 if (delegated && (!msgData || msgData.accessLevel !== "FULL")) {
9607:                     if (instance.options.onAccessChanged) instance.options.onAccessChanged();
9608:                     return;
```

## src/main/resources/static/mailbox-readonly.js:823

```text
823:                 var person = makeNode(doc, "div", "mc-person mailbox-progress-card");
824:                 person.dataset.active = selected && String(selected.contactId) === String(item.contactId) ? "true" : "false";
825:                 var main = makeNode(doc, "button", "mc-person-main");
826:                 main.setAttribute("type", "button"); main.dataset.action = "select-expert";
827:                 main.dataset.contactId = String(item.contactId);
828:                 var heading = makeNode(doc, "span", "mc-person-heading");
829:                 var name = makeNode(doc, "strong"); setText(name, (item.name || "").trim() || "未填写姓名");
830:                 name.setAttribute("title", name.textContent); heading.appendChild(name); main.appendChild(heading);
831:                 var account = makeNode(doc, "small"); setText(account, (item.accountCodes || []).join("、")); main.appendChild(account);
832:                 if (item.accessLevel === "FULL" && item.email) appendText(main, item.email);
833:                 var subject = makeNode(doc, "small");
834:                 setText(subject, (item.latestMessage && item.latestMessage.direction === "INBOUND" ? "最近来信：" : "最近发件：") + latestSubject(item)); main.appendChild(subject);
835:                 var reply = makeNode(doc, "span", "mailbox-reply-list"), replyLabel = makeNode(doc, "span");
836:                 setText(replyLabel, "上次回复"); reply.appendChild(replyLabel);
837:                 var receivedAt = item.latestInbound && item.latestInbound.receivedAt;
838:                 var formatted = global.MailboxChat && global.MailboxChat.formatLastReplyTime
839:                     ? global.MailboxChat.formatLastReplyTime(receivedAt)
840:                     : receivedAt ? { display: formatTime(receivedAt), datetime: receivedAt, title: "北京时间" } : null;
841:                 var replyValue = makeNode(doc, formatted ? "time" : "span", formatted ? "" : "mailbox-reply-empty");
842:                 setText(replyValue, formatted ? formatted.display : "暂无回复");
843:                 if (formatted) { replyValue.setAttribute("datetime", formatted.datetime); replyValue.setAttribute("title", formatted.title); }
844:                 reply.appendChild(replyValue); main.appendChild(reply);
845:                 var meta = makeNode(doc, "span", "mc-person-meta"), counts = makeNode(doc, "span", "mc-person-counts");
846:                 setText(counts, "收 " + (Number(item.receivedCount) || 0) + " · 发 " + (Number(item.sentCount) || 0)); meta.appendChild(counts);
847:                 if (Array.isArray(item.expertTags)) appendExpertTags(doc, meta, item.expertTags, true);
848:                 else appendExpertTags(doc, main, item.expertTags);
849:                 main.appendChild(meta);
850:                 var calendar = makeNode(doc, "span", "calendar-summary"); setText(calendar, scheduleText(item.contactId)); main.appendChild(calendar);
851:                 person.appendChild(main);
852:                 var progress = makeNode(doc, "span"); progress.dataset.role = "person-actions";
853:                 var status = makeNode(doc, "span", "mailbox-progress-status"); status.dataset.access = item.accessLevel;
854:                 setText(status, item.accessLevel === "FULL" ? "可写" : "可读"); progress.appendChild(status); person.appendChild(progress);
855:                 if (Number(item.displayPendingCount === undefined ? item.pendingCount : item.displayPendingCount) > 0 || item.displaySuspended) {
856:                     var foot = makeNode(doc, "div", "mailbox-suspend-card-footer"), pending = makeNode(doc, "span", "mailbox-suspend-state");
857:                     pending.dataset.pending = String(!item.displaySuspended); setText(pending, pendingText(item)); foot.appendChild(pending); person.appendChild(foot);
858:                 }
859:                 var ownDrafts = Array.isArray(item.ownDrafts) ? item.ownDrafts : [];
860:                 if (ownDrafts.length) {
861:                     if (listScope === "drafts") {
862:                         ownDrafts.forEach(function (entry) {
863:                             var label = entry.kind === "MANUAL" ? "草稿" : ({DRAFT: "未提交", SUBMITTED: "已提交", RETURNED: "已退回", APPROVED: "已审核"}[entry.state] || "草稿");
864:                             var open = buttonNode(doc, "reader-open-draft", label + " · " + (entry.subject || "无主题"), "mc-text-button mc-reader-draft");
865:                             open.dataset.contactId = String(item.contactId); open.dataset.draftId = String(entry.id); open.dataset.draftKind = entry.kind;
866:                             person.appendChild(open);
```

## src/main/resources/static/mailbox-readonly.js:992

```text
992:                 listLoading = false;
993:                 listFailed = false;
994:                 if (selected) {
995:                     listItems.forEach(function (item) {
996:                         if (String(item.contactId) !== String(selected.contactId)) return;
997:                         Object.assign(selected, item);
998:                         if (delegated) {
999:                             if (item.accessLevel === "FULL") delegated.applyOptions({ summary: item });
1000:                             else loadSelectedDetail();
1001:                         }
1002:                         selected.expertTags = Array.isArray(item.expertTags) ? item.expertTags.slice() : null;
1003:                     });
1004:                     renderIdentity();
1005:                 }
1006:                 renderList();
1007:             }, function (error) {
1008:                 if (isStale(gen, seq, currentListSeq)) return;
1009:                 listLoading = false;
1010:                 listItems = [];
1011:                 listTotal = 0;
1012:                 listFailed = failureKind(error) !== "session";
1013:                 renderList();
1014:             });
1015:         }
1016: 
1017:         function loadSelectedDetail() {
1018:             if (!selected) return Promise.resolve();
1019:             var gen = generation;
1020:             var seq = ++detailSeq;
1021:             var contactId = selected.contactId;
1022:             restoreReadDetail();
1023:             // 刷新/重选先清正文：任何失败都不能回退到旧内容（I-3）。
1024:             detailItems = [];
1025:             detailNextBefore = null;
1026:             detailHasMore = false;
1027:             detailStatus = "loading";
1028:             renderConversation();
1029:             return requestJson(detailUrl(contactId)).then(function (data) {
1030:                 if (isStale(gen, seq, currentDetailSeq)) return;
1031:                 if (!selected || String(selected.contactId) !== String(contactId)) return;
1032:                 if (!data || (data.accessLevel !== "READ" && data.accessLevel !== "FULL")) {
1033:                     detailStatus = "forbidden"; renderConversation(); return;
1034:                 }
1035:                 var openDraft = selected.openDraft;
1036:                 delete selected.openDraft;
1037:                 if (data.accessLevel === "FULL") {
1038:                     resetReview();
1039:                     if (!global.MailboxChat || typeof global.MailboxChat.mount !== "function") {
1040:                         detailStatus = "error"; renderConversation(); return;
```

## src/main/resources/static/app.js:18434

```text
18434:     async function submitManualRichReply(recordId, requestBody) {
18435:         if (element.disabled) return false;
18436:         element.disabled = true;
18437:         const lockedEditor = $("#manualRichReplyEditor");
18438:         const lockedSubject = $("#manualReplySubject");
18439:         const editable = lockedEditor?.getAttribute("contenteditable");
18440:         const readOnly = lockedSubject?.readOnly;
18441:         if (lockedEditor) lockedEditor.setAttribute("contenteditable", "false");
18442:         if (lockedSubject) lockedSubject.readOnly = true;
18443:         try {
18444:             const result = await postManualRichReplySnapshot(`/api/mail/unmatched-inbound/${recordId}/manual-rich-reply`, requestBody);
18445:             if (result === null) return false;
18446:             manualReplyQaContext = null;
18447:             aiReplyState.adoptContext = null;
18448:             resetPreflightState();
18449:             const archiveStatus = result.unsupportedAnswerArchiveStatus || "NOT_APPLICABLE";
18450:             const archivedCount = Number(result.unsupportedAnswerArchivedCount) || 0;
18451:             if (archiveStatus === "SAVED") {
18452:                 const suffix = archivedCount > 0 ? `，已记录 ${archivedCount} 条无依据回答` : "";
18453:                 alert(`人工回复邮件发送成功${suffix}`);
18454:                 showStatus(`人工回复邮件发送成功${suffix}`, "ok");
18455:             } else if (archiveStatus === "PARTIAL" || archiveStatus === "FAILED") {
18456:                 alert("人工回复邮件发送成功\n无依据回答索引未完整写入，请勿重复发送");
18457:                 showStatus("人工回复邮件发送成功；无依据回答索引未完整写入，请勿重复发送", "warn");
18458:             } else {
18459:                 alert("人工回复邮件发送成功");
18460:             }
18461:         } catch (e) {
18462:             alert("人工回复发送失败: " + e.message);
18463:             throw e;
18464:         } finally {
18465:             element.disabled = false;
18466:             if (lockedEditor) {
18467:                 if (editable == null) lockedEditor.removeAttribute("contenteditable");
18468:                 else lockedEditor.setAttribute("contenteditable", editable);
18469:             }
18470:             if (lockedSubject) lockedSubject.readOnly = readOnly;
18471:         }
18472:         await showUnmatchedDetail(recordId);
18473:         await refreshMailboxAfterPendingAction();
18474:         return true;
18475:     }
18476: 
18477:     if (action === "send-manual-rich-reply") {
18478:         const editor = $("#manualRichReplyEditor");
18479:         const subject = $("#manualReplySubject")?.value?.trim();
18480:         if (!subject) {
```

## src/main/resources/static/app.js:21180

```text
21180: 
21181: function isMailReadOnlyMode() {
21182:     return mailReadOnlyState().active === true;
21183: }
21184: 
21185: // Reader permissions are exact method/path pairs; the backend independently enforces grants.
21186: function isMailReadOnlyRequestAllowed(path, options) {
21187:     const normalized = String(path ?? "").split("?")[0];
21188:     const method = String((options && options.method) || "GET").toUpperCase();
21189:     if (method === "GET" && normalized === "/api/auth/me") return true;
21190:     if (method === "POST" && (normalized === "/api/auth/login" || normalized === "/api/auth/logout")) return true;
21191:     if (method === "GET" && normalized === "/api/mail/mailbox/conversations") return true;
21192:     if (method === "GET" && /^\/api\/mail\/mailbox\/conversations\/[1-9][0-9]*\/(messages|access)$/.test(normalized)) return true;
21193:     const scopedRoutes = [
21194:         ["POST", /^\/api\/mail\/reply-identity\/inspect$/],
21195:         ["GET", /^\/api\/mail\/mailbox\/conversations\/pending-badge$/],
21196:         ["GET", /^\/api\/operator-action-logs$/],
21197:         ["POST", /^\/api\/mail\/mailbox\/conversations\/[1-9][0-9]*\/manual-rich-reply$/],
21198:         ["PUT", /^\/api\/mail\/mailbox\/conversations\/[1-9][0-9]*\/(follow|progress-status|replied-dismissal|suspension)$/],
21199:         ["DELETE", /^\/api\/mail\/mailbox\/conversations\/[1-9][0-9]*\/(follow|suspension)$/],
21200:         ["GET", /^\/api\/mail\/mailbox\/conversations\/[1-9][0-9]*\/suspension$/],
21201:         ["PATCH", /^\/api\/mail\/mailbox\/conversations\/[1-9][0-9]*\/suspension\/reason$/],
21202:         ["GET", /^\/api\/mail\/unmatched-inbound\/[1-9][0-9]*$/],
21203:         ["POST", /^\/api\/mail\/unmatched-inbound\/[1-9][0-9]*\/(manual-rich-reply|mark-resolved|cancel-resolved)$/],
21204:         ["GET", /^\/api\/mail\/mailbox\/(INBOUND_PROCESSING|MAIL_RECORD)\/[1-9][0-9]*\/attachments$/],
21205:         ["GET", /^\/api\/mail\/mailbox\/attachments\/[1-9][0-9]*\/download$/],
21206:         ["POST", /^\/api\/mail\/conversations\/[1-9][0-9]*\/outbound-attachments$/],
21207:         ["GET", /^\/api\/mail\/conversations\/[1-9][0-9]*\/outbound-attachments\/[A-Za-z0-9-]+\/download$/],
21208:         ["GET", /^\/api\/mail\/conversations\/[1-9][0-9]*\/messages\/[1-9][0-9]*\/outbound-attachments\/[A-Za-z0-9-]+\/download$/],
21209:         ["GET", /^\/api\/mail\/conversations\/[1-9][0-9]*\/messages\/[1-9][0-9]*\/calendar-attachment$/],
21210:         ["GET", /^\/api\/expert-contacts\/[1-9][0-9]*$/],
21211:         ["POST", /^\/api\/expert-contacts\/[1-9][0-9]*\/(operator-status|index-level)$/],
21212:         ["GET", /^\/api\/expert-contacts\/[1-9][0-9]*\/(materials|documents)$/],
21213:         ["GET", /^\/api\/expert-contacts\/[1-9][0-9]*\/attachments\/[1-9][0-9]*\/(download|preview)$/],
21214:         ["POST", /^\/api\/expert-contacts\/[1-9][0-9]*\/materials\/(transfers|uploads|download-zip|reconcile)$/],
21215:         ["GET", /^\/api\/mail\/contact-(notes|locations)\/[1-9][0-9]*$/],
21216:         ["PUT", /^\/api\/mail\/contact-(notes|locations)\/[1-9][0-9]*$/],
21217:         ["GET", /^\/api\/mail\/contact-locations\/([1-9][0-9]*\/timing|countries)$/],
21218:         ["GET", /^\/api\/experts\/(profile|manual-profile)$/],
21219:         ["PATCH", /^\/api\/experts\/manual-profile$/],
21220:         ["POST", /^\/api\/experts\/tags\/(add|remove)$/],
21221:         ["GET", /^\/api\/inbound-summary\/tags\/options$/],
21222:         ["POST", /^\/api\/inbound-summary\/mails\/[1-9][0-9]*\/tags(\/auto)?$/],
21223:         ["DELETE", /^\/api\/inbound-summary\/tags\/[1-9][0-9]*$/],
21224:         ["GET", /^\/api\/meeting-calendar\/(events|summaries)$/],
21225:         ["POST", /^\/api\/meeting-calendar\/events$/],
21226:         ["GET", /^\/api\/meeting-calendar\/events\/[1-9][0-9]*$/],
21227:         ["PUT", /^\/api\/meeting-calendar\/events\/[1-9][0-9]*$/],
21228:         ["POST", /^\/api\/meeting-calendar\/events\/[1-9][0-9]*\/cancel$/],
21229:         ["GET", /^\/api\/mail\/unmatched-inbound\/[1-9][0-9]*\/meeting-confirmation\/options$/],
21230:         ["POST", /^\/api\/mail\/unmatched-inbound\/[1-9][0-9]*\/meeting-confirmation\/preview$/],
```

## src/main/resources/static/app.js:22038

```text
22038: async function submitManualRichReply(url, requestBody) {
22039:         try {
22040:             const result = await postManualRichReplySnapshot(url, requestBody);
22041:             if (result === null) return false;
22042:             const archiveStatus = result?.unsupportedAnswerArchiveStatus || "NOT_APPLICABLE";
22043:             const archivedCount = Number(result?.unsupportedAnswerArchivedCount) || 0;
22044:             if (archiveStatus === "SAVED") {
22045:                 const suffix = archivedCount > 0 ? `，已记录 ${archivedCount} 条无依据回答` : "";
22046:                 alert(`人工回复邮件发送成功${suffix}`);
22047:                 showStatus(`人工回复邮件发送成功${suffix}`, "ok");
22048:             } else if (archiveStatus === "PARTIAL" || archiveStatus === "FAILED") {
22049:                 alert("人工回复邮件发送成功\n无依据回答索引未完整写入，请勿重复发送");
22050:                 showStatus("人工回复邮件发送成功；无依据回答索引未完整写入，请勿重复发送", "warn");
22051:             } else {
22052:                 alert("人工回复邮件发送成功");
22053:             }
22054:             return true;
22055:         } catch (e) {
22056:             alert("人工回复发送失败: " + e.message);
22057:             return false;
22058:         }
22059: }
22060: 
22061: // I-4：无来信专家「选择模板发送跟进邮件」→ 既有专家模板发件流程
22062: // （ManualMailOptionType 仅 COMPOSE_TEMPLATE；command 无自由 subject/body，
```

## src/main/resources/static/mailbox-chat.css:35

```text
35: .mail-chat .mc-identity{flex:1;min-width:180px}
36: .mail-chat .mc-identity h2{font-size:16px;font-weight:600;color:#1e293b;overflow-wrap:anywhere}
37: .mail-chat .mc-identity p{margin-top:4px;color:#64748b;font-size:11px;overflow-wrap:anywhere}
38: .mail-chat .mc-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
39: .mail-chat .mc-scroll{flex:1;min-height:0;overflow:auto;padding:16px 18px;overscroll-behavior:contain;scrollbar-gutter:stable}
40: .mail-chat .mc-timeline{display:flex;flex-direction:column;gap:14px;margin-bottom:16px}
41: .mail-chat .mc-load-older{align-self:center}
42: .mail-chat .mc-day{align-self:center;color:#94a3b8;font-size:11px;padding:2px 8px}
43: .mail-chat .mc-message{align-self:flex-start;width:min(88%,820px);min-width:0;padding:12px 14px;border:1px solid #dce4ef;border-radius:10px;background:#fff}
44: .mail-chat .mc-message[data-direction=OUTBOUND]{align-self:flex-end;background:#eff5ff;border-color:#cbdcf7}
45: .mail-chat .mc-message header{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;margin-bottom:8px;color:#64748b;font-size:11px}
46: .mail-chat .mc-message h3{font-size:13px;color:#334155;font-weight:600;margin-bottom:8px;overflow-wrap:anywhere}
47: .mail-chat .mc-body{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px;line-height:1.8;color:#334155}
48: .mail-chat .mc-message footer{display:flex;justify-content:flex-end;align-items:center;gap:8px;margin-top:10px;color:#64748b;font-size:11px}
49: .mail-chat .mc-mail-extras{margin-top:10px;padding-top:8px;border-top:1px solid #e2e8f0;color:#64748b;font-size:11px}
50: .mail-chat .mc-mail-extras summary{cursor:pointer}
51: .mail-chat .mc-attachment-names{padding-top:6px;overflow-wrap:anywhere;line-height:1.8}
52: .mail-chat .mc-attachment-names>div{overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
53: .mail-chat .mc-section{margin-top:14px;border:1px solid #dce4ef;border-radius:10px;background:#f8faff;overflow:hidden}
54: .mail-chat .mc-section>summary{padding:12px 14px;cursor:pointer;color:#334155;font-size:13px;font-weight:600}
55: .mail-chat .mc-section[open]>summary{border-bottom:1px solid #e2e8f0}
56: .mail-chat .mc-section-content{padding:14px;min-width:0}
57: .mail-chat .mc-note{padding:12px;border:1px solid #dbe7fa;border-radius:7px;background:#eff5ff;color:#64748b;font-size:12px;line-height:1.7}
58: .mail-chat .mc-empty{padding:40px 20px;color:#64748b;text-align:center}
59: .mail-chat .mc-error{padding:12px;border:1px solid #fecdd3;border-radius:7px;background:#fff1f2;color:#be123c}
```

## src/main/resources/static/mailbox-chat.css:103

```text
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
115: .mail-chat .mc-body{font-size:13px;line-height:1.55;color:#465974}
116: .mail-chat .mc-message footer{justify-content:flex-start;gap:12px;margin-top:12px;padding-top:10px;border-top:1px solid #e7edf5}
117: .mail-chat .mc-process{margin-left:auto;border:1px solid #dce4ef;border-radius:7px;padding:3px 9px;white-space:nowrap}
118: .mail-chat .mc-done{margin-left:auto;color:#4c927b;font-size:11px}
119: .mail-chat .mc-translation{white-space:pre-wrap;overflow-wrap:anywhere;padding:12px 14px;margin-top:12px;border-left:2px solid #b8ccef;border-radius:0 7px 7px 0;background:#f3f7ff;color:#59708f;font-size:12px;line-height:1.85}
120: .mail-chat .mc-tag-row{display:flex;align-items:center;flex-wrap:wrap;gap:6px;margin-top:12px}
121: .mail-chat .mc-tag-row .inbound-tag-chip,.mail-chat .mc-header-meta .expert-tag{font-size:10px;line-height:1.6;padding:2px 6px;border-radius:5px;margin:0}
122: .mail-chat .mc-inline-error{padding:8px 0;color:#be123c;font-size:11px;line-height:1.6}
123: .mail-chat .mc-section{background:#fff;border-radius:10px}
```

## src/main/resources/static/mailbox-chat.css:155

```text
155: 
156: /* S-7: expert tags in the list, one line only. */
157: .mail-chat .mc-person-heading{display:flex;align-items:center;gap:6px;min-width:0}
158: .mail-chat .mc-person-heading strong{flex:1;min-width:0}
159: .mail-chat .mc-person-meta{flex-wrap:nowrap;min-width:0;gap:6px;max-width:100%}
160: .mail-chat .mc-person-counts{flex:none;white-space:nowrap;font-size:11px;color:#79899f}
```

## src/main/resources/static/styles.css:12905

```text
12905: .mailbox-suspend-inline-secondary,.mailbox-suspend-inline-primary{display:inline-flex;align-items:center;justify-content:center;min-height:30px;margin:0;padding:5px 11px;border:1px solid #dce5f0;border-radius:6px;background:#fff;color:#72849d;font:inherit;font-size:11px;font-weight:500;line-height:1.5;white-space:nowrap;box-shadow:none;cursor:pointer}
12906: .mailbox-suspend-inline-secondary:hover:not(:disabled){border-color:#bbcae2;background:#f4f7fc}.mailbox-suspend-inline-primary{border-color:#3762d8;background:#3762d8;color:#fff}.mailbox-suspend-inline-primary:hover:not(:disabled){border-color:#2954c8;background:#2954c8}.mailbox-suspend-inline-secondary:active:not(:disabled),.mailbox-suspend-inline-primary:active:not(:disabled){transform:translateY(1px)}.mailbox-suspend-inline-secondary:disabled,.mailbox-suspend-inline-primary:disabled{opacity:.5;cursor:not-allowed;transform:none}.mailbox-suspend-inline-secondary:focus-visible,.mailbox-suspend-inline-primary:focus-visible{outline:3px solid #9eb9ff;outline-offset:2px}
12907: .mailbox-suspend-process-confirm{display:inline-flex;align-items:center;flex-wrap:wrap;gap:7px;margin-left:auto}.mailbox-suspend-process-confirm>span{color:#8a9bb2;font-size:11px;margin-right:3px}#view-mailbox.mc-refined .mc-message footer .mc-process{margin-left:auto}.mailbox-suspend-processed-label{margin-left:auto;color:#679481;font-size:11px;line-height:30px}
```

## src/main/resources/static/styles.css:12918

```text
12918: @media(max-width:760px){.mailbox-suspend-banner{margin:12px 12px 3px;padding:11px 12px}#view-mailbox.mc-refined .mc-filter{min-height:44px;padding-left:6px;padding-right:6px}.mailbox-suspend-card-action,#view-mailbox.mc-refined .mc-actions .mailbox-suspend-action,.mailbox-suspend-inline-secondary,.mailbox-suspend-inline-primary,#view-mailbox.mc-refined .mc-message footer .mailbox-suspend-pending-action{min-height:44px}.mailbox-suspend-inline-reason textarea{font-size:16px}.mailbox-suspend-inline-reason{margin:10px 12px}#view-mailbox.mc-refined .mc-message .mailbox-suspend-completion-line{flex-direction:column;align-items:flex-start;gap:10px}.mailbox-suspend-completion-actions{align-self:flex-end}.mailbox-suspend-process-confirm>span{flex-basis:100%;text-align:right}}
```