# v6 复核：实际代码逐字摘录

## src/main/resources/static/mailbox-chat.js

```text
155:     function operatorName() {
156:         try {
157:             return (global.localStorage && global.localStorage.getItem("operatorName")) || "console";
158:         } catch (e) {
159:             return "console";
160:         }
161:     }
162: 
```
```text
503:     function sessionUserFromOptions(options) {
504:         const value = options && options.sessionUser ? String(options.sessionUser) : "";
505:         return value || operatorName();
506:     }
507: 
```
```text
3070:                 ? `${SOURCE_LABELS.INBOUND_PROCESSING || "专家来信"} · ${timePart(message.eventAt)}${account ? ` · ${escapeText(account)}` : ""}`
3071:                 : `${direction === "OUTBOUND" ? "发出邮件" : "往来邮件"} · ${timePart(message.eventAt)}${account ? ` · ${escapeText(account)}` : ""}`;
3072:             const subject = message.subject || "(无主题)";
3073:             const displayBody = messageDisplayText(message);
3074:             const bodyHtml = displayBody ? `<div class="mc-body">${messageDisplayHtml(displayBody)}</div>` : "";
3075:             const sentMeetingHtml = sentMeetingAttachmentHtml(message);
3076:             // fast-p 07（I-4）：已发通用附件只消费 06 的 outboundAttachments 快照。
3077:             const sentOutboundFilesHtml = outboundSentFilesHtml(message);
3078:             const attachmentHtml = Number(message.attachmentCount) > 0 ? renderAttachmentSummary(message) : "";
3079:             const statusBadge = renderStatusBadge(message, direction);
3080:             const pending = isInboundProcessing && message.processStatus === "MANUAL_REVIEW";
3081:             const processed = isInboundProcessing && message.processStatus === "PROCESSED";
3082:             const tagRow = isInboundProcessing ? renderTagRow(message, key) : "";
3083:             const translationStateFor = translationState(key, displayBody);
3084:             const translationHtml = renderTranslationBlock(translationStateFor);
3085:             const canTranslate = !!displayBody;
3086:             const footerButtons = [];
3087:             if (canTranslate) {
3088:                 footerButtons.push(`<button class="mc-text-button" type="button" data-action="mc-translate" data-message-key="${escapeText(key)}">${escapeText(translateButtonLabel(translationStateFor))}</button>`);
3089:             }
3090:             if (isInboundProcessing) {
3091:                 footerButtons.push(`<button class="mc-text-button" type="button" data-action="mc-add-mail-tag" data-message-key="${escapeText(key)}">＋ 添加标签</button>`);
3092:             }
3093:             if (pending) {
3094:                 footerButtons.push(`<button class="mc-text-button mc-process" type="button" data-action="mc-mark-resolved" data-message-key="${escapeText(key)}">✓ 标记已处理</button>`);
3095:             } else if (processed) {
3096:                 footerButtons.push(`<span class="mc-done">✓ 已处理</span>`);
3097:             }
3098:             const footerHtml = footerButtons.length > 0
3099:                 ? `<footer>${footerButtons.join("")}</footer>`
3100:                 : "";
3101:             return `
3102:                 <article class="mc-message" data-direction="${direction}" data-source="${escapeText(message.source)}" data-id="${escapeText(message.id)}" data-message-key="${escapeText(key)}">
3103:                     <header><span>${who}</span>${statusBadge ? `<span>${statusBadge}</span>` : ""}</header>
3104:                     <h3>${escapeText(subject)}</h3>
3105:                     ${bodyHtml}
3106:                     ${sentMeetingHtml}
3107:                     ${sentOutboundFilesHtml}
3108:                     ${attachmentHtml}
3109:                     ${translationHtml}
3110:                     ${tagRow}
3111:                     <p class="mc-inline-error" role="alert" hidden></p>
3112:                     ${footerHtml}
3113:                 </article>
3114:             `;
3115:         }
3116: 
3117:         function renderStatusBadge(message, direction) {
3118:             if (direction === "INBOUND") {
3119:                 if (message.processStatus === "MANUAL_REVIEW") {
3120:                     return '<span class="mc-badge" data-tone="pending">待处理</span>';
3121:                 }
3122:                 return "";
3123:             }
```
```text
3487:         function markResolvedByKey(key) {
3488:             const message = messageByKey(key);
3489:             if (!message || !(message.processStatus === "MANUAL_REVIEW")) return;
3490:             const processingId = Number(message.id);
3491:             openDialog("mark-unmatched-resolved").then((payload) => {
3492:                 if (!payload) return;
3493:                 return hostApi()(`/api/mail/unmatched-inbound/${processingId}/mark-resolved`, {
3494:                     method: "POST",
3495:                     body: JSON.stringify(payload)
3496:                 }).then(() => {
3497:                     if (instance.disposed) return;
3498:                     hostShowStatus("已标记为处理完成");
3499:                     const index = (instance.conversation.items || []).findIndex((item) => `${item.source}:${item.id}` === key);
3500:                     if (index >= 0) {
3501:                         const updated = Object.assign({}, instance.conversation.items[index], {
3502:                             processStatus: "PROCESSED"
3503:                         });
3504:                         const items = instance.conversation.items.slice();
3505:                         items[index] = updated;
3506:                         instance.conversation.items = items;
3507:                     }
3508:                     const summary = instance.selectedSummary;
3509:                     if (summary) {
3510:                         summary.pendingCount = Math.max(0, (Number(summary.pendingCount) || 0) - 1);
3511:                     }
3512:                     const refreshBadge = hostFn("refreshUnmatchedBadge");
3513:                     if (refreshBadge) refreshBadge();
3514:                     renderTimeline();
3515:                     // 服务端顺序重查当前页；空页回退
3516:                     refreshListWithFallback();
3517:                     // 新来信检查（可能最新来信变化）但不打断编辑器
3518:                     checkInboundChangeQuiet();
3519:                     saveConversationState();
3520:                 }).catch((err) => {
3521:                     if (instance.disposed) return;
3522:                     hostShowStatus(err && err.message ? err.message : "标记失败", "error");
3523:                     const article = messageElByKey(key);
3524:                     if (article) setInlineError(article, err && err.message ? err.message : "标记失败，请重试");
3525:                 });
3526:             }).catch((err) => {
3527:                 if (instance.disposed) return;
3528:                 hostShowStatus(err && err.message ? err.message : "标记失败", "error");
3529:             });
3530:         }
3531: 
3532:         // ---- 邮件标签（I-3：timeline.tags 直读 / POST 回包 / 删除按 tagId） ----
3533: 
3534:         function openInboundTagModal(message) {
3535:             const contactId = Number(instance.selectedContactId);
3536:             const adapter = {
3537:                 inboundId: Number(message.id),
3538:                 source: "INBOUND_PROCESSING",
3539:                 contactId,
3540:                 onTagsChanged: (tags) => {
```
## src/main/resources/static/app.js

```text
16089: const ACTION_DIALOG_SCHEMAS = {
16090:     "mark-unmatched-resolved": {
16091:         title: "标记为已处理",
16092:         fields: [
16093:             { name: "resolvedBy", label: "操作人姓名", type: "text", required: true },
16094:             { name: "note", label: "处理备注", type: "textarea", required: false }
16095:         ]
16096:     },
16097:     "cancel-unmatched-resolved": {
16098:         title: "取消处理",
16099:         fields: [
16100:             { name: "operatorName", label: "操作人姓名", type: "text", required: true },
16101:             { name: "note", label: "取消原因", type: "textarea", required: false }
16102:         ]
16103:     },
16104:     "bind-unmatched-contact": {
```
```text
16845:     // 由组件内部维护已生效筛选，宿主只触发静默列表刷新）。
16846:     if (state.mailbox.chatMounted) {
16847:         try {
16848:             MailboxChat.mount(list, {});
16849:             return Promise.resolve();
16850:         } catch (e) {
16851:             showStatus(e.message || "聊天视图刷新失败", "error");
16852:             return Promise.resolve();
16853:         }
16854:     }
16855:     const mountOptions = { filters: mailboxChatFilterSnapshot() };
16856:     // contextPath 是 app.js 顶层 const 词法绑定（非 window 属性）：宿主守卫测试在隔离
16857:     // 沙箱里逐函数执行 refreshMailboxChatList，typeof 守卫让无绑定的沙箱安全回退空前缀。
16858:     if (typeof contextPath !== "undefined") mountOptions.contextPath = contextPath;
16859:     if (state.mailbox.focusExpertContactId != null) {
16860:         mountOptions.focus = {
16861:             contactId: state.mailbox.focusExpertContactId,
16862:             email: state.mailbox.focusExpertEmail || ""
16863:         };
16864:     }
16865:     try {
16866:         MailboxChat.mount(list, mountOptions);
16867:         state.mailbox.chatMounted = true;
16868:         return Promise.resolve();
16869:     } catch (e) {
16870:         showStatus(e.message || "聊天视图加载失败", "error");
16871:         return Promise.resolve();
16872:     }
```
## src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt

```text
171:     @PostMapping("/unmatched-inbound/{id}/mark-resolved")
172:     fun markResolved(
173:         @PathVariable id: Long,
174:         @RequestBody request: MarkResolvedRequest
175:     ) {
176:         val actualOperator = request.operatorName?.takeIf { it.isNotBlank() }
177:             ?: request.resolvedBy
178:             ?: "UNKNOWN"
179:         pendingMailOperationService.markResolved(
180:             inboundProcessingId = id,
181:             resolvedBy = actualOperator,
182:             operatorName = actualOperator,
183:             note = request.note
184:         )
185:     }
186: 
```
```text
288:     private fun HttpServletRequest?.sessionUsernameOrNull(): String? = this
289:         ?.getSession(false)
290:         ?.getAttribute(AuthSessionKeys.USERNAME) as? String
291: 
```
```text
894: data class MarkResolvedRequest(
895:     val resolvedBy: String?,
896:     val operatorName: String? = null,
897:     val note: String?
898: )
899: 
```
## src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt

```text
1511:     @Transactional
1512:     fun markResolved(
1513:         inboundProcessingId: Long,
1514:         resolvedBy: String?,
1515:         operatorName: String?,
1516:         note: String?
1517:     ) {
1518:         val record = inboundMailProcessingRepository.findById(inboundProcessingId)
1519:             .orElseThrow { error("Inbound mail processing not found: $inboundProcessingId") }
1520:         require(record.processStatus == "MANUAL_REVIEW") { "Record $inboundProcessingId is not in MANUAL_REVIEW" }
1521: 
1522:         val actualOperator = operatorName?.takeIf { it.isNotBlank() } ?: resolvedBy ?: "UNKNOWN"
1523:         val now = LocalDateTime.now()
1524:         inboundMailProcessingRepository.save(
1525:             record.copy(
1526:                 processStatus = "PROCESSED",
1527:                 processReason = "MANUAL_RESOLVED",
1528:                 reasonType = "MANUAL_RESOLVED",
1529:                 resolvedBy = actualOperator,
1530:                 resolvedAt = now,
1531:                 updatedAt = now
1532:             )
1533:         )
1534: 
1535:         val contactId = record.expertContactId
1536:         if (contactId != null) {
1537:             val remaining = inboundMailProcessingRepository.countByExpertContactIdAndProcessStatus(
1538:                 contactId, "MANUAL_REVIEW"
1539:             )
1540:             if (remaining == 0L) {
1541:                 expertContactRepository.findById(contactId).ifPresent { contact ->
1542:                     if (contact.needsManualAttention) {
1543:                         expertContactRepository.save(contact.copy(needsManualAttention = false))
1544:                     }
1545:                 }
1546:             }
1547:         }
1548: 
1549:         operatorActionLogService.record(
1550:             targetType = "INBOUND_MAIL_PROCESSING",
1551:             targetId = inboundProcessingId,
1552:             actionType = OperatorActionType.MARK_INBOUND_RESOLVED,
1553:             expertContactId = contactId,
1554:             inboundProcessingId = inboundProcessingId,
1555:             before = mapOf(
1556:                 "processStatus" to "MANUAL_REVIEW",
1557:                 "processReason" to record.processReason,
1558:                 "reasonType" to record.reasonType
1559:             ),
1560:             after = mapOf(
1561:                 "processStatus" to "PROCESSED",
1562:                 "processReason" to "MANUAL_RESOLVED",
1563:                 "reasonType" to "MANUAL_RESOLVED"
1564:             ),
1565:             operatorName = actualOperator,
1566:             note = note
1567:         )
1568:     }
1569: 
1570:     @Transactional
```
## src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptor.kt

```text
13:     override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
14:         if ("OPTIONS".equals(request.method, ignoreCase = true)) {
15:             return true
16:         }
17: 
18:         val session = request.getSession(false)
19:         val username = session?.getAttribute(AuthSessionKeys.USERNAME) as? String
20: 
21:         if (username == null) {
22:             writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "未登录")
23:             return false
24:         }
25: 
26:         val user = authService.findUser(username)
27:         if (user == null) {
28:             session.invalidate()
29:             writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "未登录")
30:             return false
31:         }
32: 
33:         if (user.mustChangePassword) {
34:             val uri = request.requestURI
35:             val contextPath = request.contextPath
36:             val relativeUri = if (uri.startsWith(contextPath)) uri.substring(contextPath.length) else uri
37: 
38:             if (relativeUri != "/api/auth/change-password" &&
39:                 relativeUri != "/api/auth/logout" &&
40:                 relativeUri != "/api/auth/me"
41:             ) {
42:                 writeErrorResponse(
43:                     response,
44:                     HttpServletResponse.SC_FORBIDDEN,
45:                     "PASSWORD_CHANGE_REQUIRED",
46:                     "首次登录请先修改密码"
47:                 )
48:                 return false
49:             }
50:         }
51: 
52:         return true
53:     }
```
## src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt

```text
583:     private fun expertPredicates(username: String, filter: ConversationFilter, eligibility: MembershipEligibility): String {
584:         val clauses = mutableListOf<String>()
585:         clauses += """
586:             (:q IS NULL OR :q = ''
587:              OR ec.expert_name LIKE CONCAT('%', :q, '%')
588:              OR ec.expert_email LIKE CONCAT('%', :q, '%'))
589:         """.trimIndent()
590:         clauses += """
591:             (:followed = 0 OR EXISTS (
592:                 SELECT 1 FROM expert_follow eff
593:                  WHERE eff.username = :username
594:                    AND eff.expert_contact_id = u.expert_contact_id))
595:         """.trimIndent()
596:         if (filter.repliedOnly) {
597:             clauses += """
598:                 NOT EXISTS (
599:                     SELECT 1 FROM expert_follow ef_replied
600:                      WHERE ef_replied.username = :username
601:                        AND ef_replied.expert_contact_id = u.expert_contact_id)
602:             """.trimIndent()
603:             clauses += """
604:                 EXISTS (
605:                     SELECT 1 FROM mail_record mr_replied
606:                      WHERE mr_replied.expert_contact_id = u.expert_contact_id
607:                        AND mr_replied.direction = 'OUTBOUND'
608:                        AND mr_replied.send_status = 'SENT'
609:                        AND mr_replied.sender_account_code IN (:accountCodes)
610:                        AND (:accountCode IS NULL OR mr_replied.sender_account_code = :accountCode))
611:             """.trimIndent()
612:             clauses += """
613:                 EXISTS (
614:                     SELECT 1 FROM inbound_mail_processing imp_replied
615:                      WHERE imp_replied.expert_contact_id = u.expert_contact_id
616:                        AND imp_replied.sender_account_code IN (:accountCodes)
617:                        AND (:accountCode IS NULL OR imp_replied.sender_account_code = :accountCode)
618:                        AND imp_replied.id > COALESCE((
619:                            SELECT erd.last_inbound_id FROM expert_replied_dismissal erd
620:                             WHERE erd.username = :username
621:                               AND erd.expert_contact_id = u.expert_contact_id
622:                        ), 0))
623:             """.trimIndent()
624:         }
625:         val membershipClauses = listOfNotNull(eligibility.outboundClause, eligibility.inboundClause)
626:         if (membershipClauses.isNotEmpty()) {
627:             clauses += "(\n${membershipClauses.joinToString("\n      OR ")}\n      )"
628:         }
629:         return clauses.joinToString("\n   AND ")
630:     }
631: 
632:     private fun havingClause(filter: ConversationFilter): String {
633:         val conditions = mutableListOf<String>()
634:         if (filter.waitingReply) {
635:             // I-2：全历史账号范围 receivedCount=0 且 sentCount>0（FAILED 不计为 sent）。
636:             conditions += "SUM(u.received_flag) = 0 AND SUM(u.sent_flag) > 0"
637:         }
638:         if (filter.pendingOnly) {
639:             // 待处理谓词复用 processing 现有 MANUAL_REVIEW 谓词。
640:             conditions += "SUM(u.pending_flag) > 0"
641:         }
642:         return if (conditions.isEmpty()) "" else "HAVING ${conditions.joinToString(" AND ")}"
```