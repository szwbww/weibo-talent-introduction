# 最新前端逐字基线

2026-09-09 工作区，mailbox-refinement合并后。前次另存frontend-before.initial.md；以下为最终计划基线。

## src/main/resources/static/mailbox-chat.js:2126

```javascript
        function renderManualSectionInto(scroll) {
            if (!scroll) return;
            const summary = instance.selectedSummary || {};
            const latestInbound = summary.latestInbound || null;
            const mode = latestInbound && latestInbound.processingId != null ? "inbound" : "outboundOnly";
            const targetProcessingId = mode === "inbound" ? Number(latestInbound.processingId) : null;
            const targetAccount = mode === "inbound" ? (latestInbound.accountCode || "") : "";
            const targetKey = mode === "inbound" ? `${Number(instance.selectedContactId)}:${targetProcessingId}:${targetAccount}` : null;
            const targetMsg = targetProcessingId != null ? latestInboundMessage() : null;
            const defaultSubject = targetMsg ? chatSubjectPrefill(targetMsg.subject) : "Re:";
            const draft = targetKey != null ? getDraft(targetKey) : null;

            instance.manual.mode = mode;
            instance.manual.targetProcessingId = targetProcessingId;
            instance.manual.targetAccountCode = targetAccount;
            instance.manual.targetKey = targetKey;
            instance.manual.qa = draft && draft.qa ? draft.qa : null;
            instance.manual.busy = false;

            const manualContent = mode === "inbound"
                ? manualComposeHtml(targetKey, targetProcessingId, targetAccount, draft, defaultSubject)
                : manualFollowUpHtml();
            scroll.insertAdjacentHTML("beforeend", `
                <details class="mc-section" data-section="manual" open>
                    <summary>人工回复</summary>
                    <div class="mc-section-content">${manualContent}</div>
                </details>
            `);
        }

        function manualTargetInfoText(processingId, account) {
            const parts = [];
            if (account) parts.push(escapeText(account));
            if (processingId != null) parts.push(`目标来信 #${processingId}`);
            return parts.join(" · ");
        }

        function manualComposeHtml(targetKey, processingId, account, draft, defaultSubject) {
            const subjectValue = draft ? draft.subject : defaultSubject;
            const editorText = draft ? draft.text : "";
            const targetInfo = manualTargetInfoText(processingId, account);
            return `
                <div class="mc-compose" data-role="manual-compose" data-target-key="${escapeText(targetKey)}">
                    <label>主题<input aria-label="回复主题" value="${escapeText(subjectValue)}"></label>
                    <div class="mc-editor-tools">
                        <button class="button" type="button" data-action="mc-rich-command" data-command="bold">B</button>
                        <button class="button" type="button" data-action="mc-rich-command" data-command="italic">I</button>
                        <button class="button" type="button" data-action="mc-rich-command" data-command="insertUnorderedList">列表</button>
                        <button class="button" type="button" data-action="mc-rich-command" data-command="createLink">链接</button>
                    </div>
                    <div class="mc-editor" contenteditable="true" role="textbox" aria-multiline="true" aria-label="人工回复正文" data-role="mc-editor">${editorText ? escapeText(editorText) : ""}</div>
                    <div class="mc-compose-footer">
                        <span data-role="target-info">回复账号与目标来信信息：${targetInfo}</span>
                        <button class="button primary" type="button" data-action="mc-send-manual">发送人工回复</button>
                    </div>
                </div>
            `;
        }

        function manualFollowUpHtml() {
            return `
                <div data-role="manual-followup">
                    <div class="mc-note">该专家暂无来信。请使用既有模板发送跟进邮件；系统不会在没有真实来信时伪造可生成的人工富文本回复。</div>
                    <button class="button primary" type="button" data-action="mc-template-follow" data-contact-id="${escapeText(instance.selectedContactId)}">选择模板发送跟进邮件</button>
                </div>
            `;
        }

        function renderLogsBlockInto(scroll) {
            if (!scroll) return;
            scroll.insertAdjacentHTML("beforeend", `
                <details class="mc-section" data-section="logs">
```

## src/main/resources/static/mailbox-chat.js:1514

```javascript
        function renderMessage(message) {
            const direction = message.direction === "OUTBOUND" ? "OUTBOUND" : "INBOUND";
            const isInboundProcessing = message.source === "INBOUND_PROCESSING";
            const key = `${message.source}:${message.id}`;
            const account = message.accountCode || "";
            const who = isInboundProcessing
                ? `${SOURCE_LABELS.INBOUND_PROCESSING || "专家来信"} · ${timePart(message.eventAt)}${account ? ` · ${escapeText(account)}` : ""}`
                : `${direction === "OUTBOUND" ? "发出邮件" : "往来邮件"} · ${timePart(message.eventAt)}${account ? ` · ${escapeText(account)}` : ""}`;
            const subject = message.subject || "(无主题)";
            const displayBody = messageDisplayText(message);
            const bodyHtml = displayBody ? `<div class="mc-body">${escapeText(displayBody)}</div>` : "";
            const attachmentHtml = Number(message.attachmentCount) > 0 ? renderAttachmentSummary(message) : "";
            const statusBadge = renderStatusBadge(message, direction);
            const pending = isInboundProcessing && message.processStatus === "MANUAL_REVIEW";
            const processed = isInboundProcessing && message.processStatus === "PROCESSED";
            const tagRow = isInboundProcessing ? renderTagRow(message, key) : "";
            const translationStateFor = translationState(key, displayBody);
            const translationHtml = renderTranslationBlock(translationStateFor);
            const canTranslate = !!displayBody;
            const footerButtons = [];
            if (canTranslate) {
                footerButtons.push(`<button class="mc-text-button" type="button" data-action="mc-translate" data-message-key="${escapeText(key)}">${escapeText(translateButtonLabel(translationStateFor))}</button>`);
            }
            if (isInboundProcessing) {
                footerButtons.push(`<button class="mc-text-button" type="button" data-action="mc-add-mail-tag" data-message-key="${escapeText(key)}">＋ 添加标签</button>`);
            }
            if (pending) {
                footerButtons.push(`<button class="mc-text-button mc-process" type="button" data-action="mc-mark-resolved" data-message-key="${escapeText(key)}">✓ 标记已处理</button>`);
            } else if (processed) {
                footerButtons.push(`<span class="mc-done">✓ 已处理</span>`);
            }
            const footerHtml = footerButtons.length > 0
                ? `<footer>${footerButtons.join("")}</footer>`
                : "";
            return `
                <article class="mc-message" data-direction="${direction}" data-source="${escapeText(message.source)}" data-id="${escapeText(message.id)}" data-message-key="${escapeText(key)}">
                    <header><span>${who}</span>${statusBadge ? `<span>${statusBadge}</span>` : ""}</header>
                    <h3>${escapeText(subject)}</h3>
                    ${bodyHtml}
                    ${attachmentHtml}
                    ${translationHtml}
                    ${tagRow}
                    <p class="mc-inline-error" role="alert" hidden></p>
                    ${footerHtml}
                </article>
            `;
        }
```

## src/main/resources/static/mailbox-chat.css:1

```css
.mail-chat{display:grid;grid-template-columns:306px minmax(0,1fr);gap:16px;min-height:480px;height:calc(100dvh - 216px);color:#475569;font-size:12px;line-height:1.6}
.mail-chat *{box-sizing:border-box}
.mail-chat [hidden]{display:none!important}
.mail-chat :is(h2,h3,p){margin:0}
.mail-chat .mc-experts,.mail-chat .mc-conversation{display:flex;flex-direction:column;min-width:0;min-height:0;border:1px solid rgba(15,23,42,.11);border-radius:14px;background:#f8faff;overflow:hidden}
.mail-chat .mc-list-tools{display:flex;flex-direction:column;gap:10px;padding:14px;border-bottom:1px solid #e2e8f0}
.mail-chat .mc-list-tools input{width:100%;height:32px;min-height:32px;margin:0;padding:0 10px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#475569;font:inherit}
.mail-chat .mc-list-tools input::placeholder{color:#94a3b8}
.mail-chat .mc-filters{display:flex;flex-wrap:wrap;gap:6px}
.mail-chat .mc-filter{min-height:28px;padding:3px 8px;border:1px solid #dce4ef;border-radius:7px;background:#f8faff;color:#64748b;font:inherit;cursor:pointer}
.mail-chat .mc-filter:hover{border-color:#93b4ec;background:#eff5ff}
.mail-chat .mc-filter:active{background:#dbeafe}
.mail-chat .mc-filter[aria-pressed=true]{border-color:#1e40af;background:#eff5ff;color:#1e40af;font-weight:600}
.mail-chat .mc-filter:disabled{opacity:.45;cursor:not-allowed}
.mail-chat .mc-expert-list{flex:1;min-height:0;overflow:auto;padding:8px;overscroll-behavior:contain}
.mail-chat .mc-person{position:relative;display:grid;grid-template-columns:minmax(0,1fr) 28px;gap:8px;margin-bottom:6px;border:1px solid transparent;border-left:3px solid transparent;border-radius:10px;background:transparent}
.mail-chat .mc-person:hover{background:#eff5ff}
.mail-chat .mc-person[data-active=true]{border-color:#c2d3f2;border-left-color:#3c65cd;background:#eaf1ff}
.mail-chat .mc-person-main{display:flex;flex-direction:column;align-items:stretch;min-width:0;gap:5px;padding:12px 0 12px 10px;border:0;background:transparent;color:#475569;text-align:left;font:inherit;cursor:pointer}
.mail-chat .mc-person-main:active{opacity:.85}
.mail-chat .mc-person-main strong{font-size:13px;font-weight:600;color:#1e293b;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.mail-chat .mc-person-main small{font-size:11px;color:#64748b;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.mail-chat .mc-person-meta{display:flex;align-items:center;flex-wrap:wrap;gap:6px;font-size:11px}
.mail-chat .mc-follow{align-self:start;margin:10px 4px 0 0;padding:0;width:24px;height:28px;border:0;border-radius:7px;background:transparent;color:#94a3b8;font-size:20px;line-height:1;cursor:pointer}
.mail-chat .mc-follow:hover{background:#fef3c7;color:#b45309}
.mail-chat .mc-follow:active{background:#fde68a}
.mail-chat .mc-follow[aria-pressed=true]{color:#d97706}
.mail-chat .mc-follow:disabled{opacity:.45;cursor:not-allowed}
.mail-chat .mc-badge{display:inline-flex;align-items:center;padding:1px 7px;border:1px solid #dce4ef;border-radius:12px;background:#f1f5f9;color:#64748b;font-size:11px;white-space:nowrap}
.mail-chat .mc-badge[data-tone=pending]{background:#fff7ed;border-color:#fed7aa;color:#b45309}
.mail-chat .mc-badge[data-tone=success]{background:#ecfdf5;border-color:#a7f3d0;color:#059669}
.mail-chat .mc-badge[data-tone=error]{background:#fff1f2;border-color:#fecdd3;color:#e11d48}
.mail-chat .mc-pager{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:6px;padding:10px 12px;border-top:1px solid #e2e8f0;color:#64748b;font-size:11px}
.mail-chat .mc-header{display:flex;align-items:flex-start;justify-content:space-between;flex-wrap:wrap;gap:12px;padding:16px 18px;border-bottom:1px solid #e2e8f0}
.mail-chat .mc-identity{flex:1;min-width:180px}
.mail-chat .mc-identity h2{font-size:16px;font-weight:600;color:#1e293b;overflow-wrap:anywhere}
.mail-chat .mc-identity p{margin-top:4px;color:#64748b;font-size:11px;overflow-wrap:anywhere}
.mail-chat .mc-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
.mail-chat .mc-scroll{flex:1;min-height:0;overflow:auto;padding:16px 18px;overscroll-behavior:contain;scrollbar-gutter:stable}
.mail-chat .mc-timeline{display:flex;flex-direction:column;gap:14px;margin-bottom:16px}
.mail-chat .mc-load-older{align-self:center}
.mail-chat .mc-day{align-self:center;color:#94a3b8;font-size:11px;padding:2px 8px}
.mail-chat .mc-message{align-self:flex-start;width:min(88%,820px);min-width:0;padding:12px 14px;border:1px solid #dce4ef;border-radius:10px;background:#fff}
.mail-chat .mc-message[data-direction=OUTBOUND]{align-self:flex-end;background:#eff5ff;border-color:#cbdcf7}
.mail-chat .mc-message header{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;margin-bottom:8px;color:#64748b;font-size:11px}
.mail-chat .mc-message h3{font-size:13px;color:#334155;font-weight:600;margin-bottom:8px;overflow-wrap:anywhere}
.mail-chat .mc-body{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px;line-height:1.8;color:#334155}
.mail-chat .mc-message footer{display:flex;justify-content:flex-end;align-items:center;gap:8px;margin-top:10px;color:#64748b;font-size:11px}
.mail-chat .mc-mail-extras{margin-top:10px;padding-top:8px;border-top:1px solid #e2e8f0;color:#64748b;font-size:11px}
.mail-chat .mc-mail-extras summary{cursor:pointer}
.mail-chat .mc-attachment-names{padding-top:6px;overflow-wrap:anywhere;line-height:1.8}
.mail-chat .mc-attachment-names>div{overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.mail-chat .mc-section{margin-top:14px;border:1px solid #dce4ef;border-radius:10px;background:#f8faff;overflow:hidden}
.mail-chat .mc-section>summary{padding:12px 14px;cursor:pointer;color:#334155;font-size:13px;font-weight:600}
.mail-chat .mc-section[open]>summary{border-bottom:1px solid #e2e8f0}
.mail-chat .mc-section-content{padding:14px;min-width:0}
.mail-chat .mc-note{padding:12px;border:1px solid #dbe7fa;border-radius:7px;background:#eff5ff;color:#64748b;font-size:12px;line-height:1.7}
.mail-chat .mc-empty{padding:40px 20px;color:#64748b;text-align:center}
.mail-chat .mc-error{padding:12px;border:1px solid #fecdd3;border-radius:7px;background:#fff1f2;color:#be123c}
.mail-chat .mc-compose{display:flex;flex-direction:column;gap:10px;min-width:0}
.mail-chat .mc-compose label{display:flex;flex-direction:column;gap:6px;color:#64748b;font-size:12px}
.mail-chat .mc-compose input{width:100%;height:32px;min-height:32px;padding:0 10px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#334155;font:inherit}
.mail-chat .mc-editor-tools{display:flex;flex-wrap:wrap;gap:6px}
.mail-chat .mc-editor{min-height:160px;max-height:360px;overflow:auto;padding:12px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#334155;font-size:12px;line-height:1.8;overflow-wrap:anywhere}
.mail-chat .mc-compose-footer{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px}
.mail-chat .button{white-space:nowrap}
.mail-chat .button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
.mail-chat :is(button,a,input,select,summary,[contenteditable=true]):focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
@media(max-width:1100px){.mail-chat{grid-template-columns:280px minmax(0,1fr);gap:12px}.mail-chat .mc-header{padding:14px}.mail-chat .mc-scroll{padding:14px}.mail-chat .mc-message{width:94%}}
@media(max-width:760px){.mail-chat{display:flex;flex-direction:column;height:auto;min-height:0;gap:12px}.mail-chat .mc-experts{max-height:320px;min-height:240px}.mail-chat .mc-expert-list{min-height:100px}.mail-chat .mc-conversation{min-height:560px}.mail-chat .mc-scroll{max-height:none;overflow:visible;padding:12px}.mail-chat .mc-message{width:100%}.mail-chat .mc-header{padding:12px}.mail-chat .mc-identity{min-width:0;width:100%;flex-basis:100%}.mail-chat .mc-section-content{padding:12px}.mail-chat .mc-actions{width:100%}}
@media(prefers-reduced-motion:reduce){.mail-chat .button{transition:none}.mail-chat .button:hover,.mail-chat .button:active{transform:none}}
/* S-1..S-6: mailbox only; retain production shell. */
#view-mailbox.mc-refined #mailboxLegacyToolbar{display:none}
#view-mailbox.mc-refined #mailboxConversationPanel{overflow:visible}
#view-mailbox.mc-refined #mailboxList{padding:16px}
#view-mailbox:not(.mc-refined) .mc-filter-fields{display:contents}
.mail-chat .mc-experts{overflow:visible;position:relative}
.mail-chat .mc-search-row{position:relative;display:flex;align-items:center;gap:7px}
.mail-chat .mc-search-row input{flex:1;min-width:0;height:36px;min-height:36px}
.mail-chat .mc-icon{display:inline-flex;align-items:center;justify-content:center;flex:none;min-width:36px;height:36px;padding:0 8px;border:1px solid #d8e1ef;border-radius:8px;background:#fff;color:#6482b2;font:inherit;font-size:20px;cursor:pointer;gap:4px}
.mail-chat .mc-icon:hover{background:#edf3ff;border-color:#93b4ec}
.mail-chat .mc-icon:active{background:#dbeafe}
.mail-chat .mc-icon[aria-expanded=true]{background:#eaf1ff;border-color:#7396df;color:#2451b9}
.mail-chat .mc-icon:disabled{opacity:.45;cursor:not-allowed}
.mail-chat .mc-filter-count{padding:0 4px;border-radius:8px;background:#2553c5;color:#fff;font-size:10px;line-height:16px}
.mail-chat .mc-filters{gap:12px;flex-wrap:nowrap}
.mail-chat .mc-filter{border:0;border-bottom:2px solid transparent;border-radius:0;background:transparent;padding:6px 5px 10px}
.mail-chat .mc-filter[aria-pressed=true]{border-bottom-color:#2e59c7;background:transparent;color:#244ca9}
.mail-chat .mc-filter-summary{display:flex;align-items:center;gap:8px;color:#7890b3;font-size:11px}
.mail-chat .mc-filter-popover{position:absolute;top:44px;left:0;z-index:var(--z-dropdown);width:430px;max-width:calc(100vw - 48px);max-height:calc(100dvh - 210px);overflow:auto;background:#fff;border:1px solid #dce5f1;border-radius:12px;box-shadow:0 14px 50px #20395d26}
.mail-chat .mc-filter-popover header{display:flex;align-items:center;justify-content:space-between;padding:16px 20px;border-bottom:1px solid #edf1f7;font-size:14px;color:#475d79}
.mail-chat .mc-filter-popover .mc-filter-fields{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:14px;padding:18px 20px}
.mail-chat .mc-field{display:flex;flex-direction:column;gap:7px;min-width:0;color:#8494aa;font-size:11px}
.mail-chat .mc-field-wide{grid-column:1/-1}
.mail-chat .mc-field input,.mail-chat .mc-field select{width:100%;min-width:0;height:34px;min-height:34px;margin:0;padding:0 9px;border:1px solid #dce4ef;border-radius:7px;background:#fcfdff;color:#5f7390;font:inherit;font-size:12px}
.mail-chat .mc-filter-popover footer{display:flex;justify-content:flex-end;align-items:center;gap:8px;padding:14px 20px;border-top:1px solid #edf1f7}
.mail-chat .mc-filter-popover footer .mc-text-button{margin-right:auto}
.mail-chat .mc-close{display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border:0;border-radius:5px;background:transparent;color:#91a1b7;font-size:21px;cursor:pointer}
.mail-chat .mc-close:hover{background:#edf3ff;color:#2451b9}
.mail-chat .mc-close:active{background:#dbeafe}
.mail-chat .mc-close:disabled{opacity:.45;cursor:not-allowed}
.mail-chat .mc-header{flex:none;background:#fff;padding:17px 22px 14px}
.mail-chat .mc-header-meta{display:flex;align-items:center;flex-wrap:wrap;gap:8px;flex-basis:100%;font-size:11px;color:#8a9bb2}
.mail-chat .mc-identity h2{font-size:17px;font-weight:600;letter-spacing:-.25px}
.mail-chat .mc-badge{border-radius:5px;font-size:10px;line-height:1.6;padding:2px 7px}
.mail-chat .mc-badge[data-tone=pending]{background:#fff5e9;border-color:#f6dfc6;color:#bb7838}
.mail-chat .mc-timeline-head{flex:none;display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 22px;color:#8b9bb1;font-size:11px}
.mail-chat .mc-position-hint{margin-left:auto;font-size:10px;color:#8b9bb1}
.mail-chat .mc-text-button{display:inline-flex;align-items:center;gap:4px;border:0;border-radius:4px;background:transparent;color:#6482b2;font:inherit;font-size:11px;line-height:1.5;padding:3px 0;cursor:pointer}
.mail-chat .mc-text-button:hover{color:#244ca9;background:#edf3ff}
.mail-chat .mc-text-button:active{background:#dbeafe}
.mail-chat .mc-text-button:disabled{opacity:.45;cursor:not-allowed}
.mail-chat .mc-message{width:min(92%,820px);padding:14px 17px;border-radius:11px;box-shadow:0 2px 6px #334b7210}
.mail-chat .mc-message h3{font-size:12px;color:#4d617d;font-weight:600;margin-bottom:11px;line-height:1.55}
.mail-chat .mc-body{font-size:13px;line-height:1.85;color:#465974}
.mail-chat .mc-message footer{justify-content:flex-start;gap:12px;margin-top:12px;padding-top:10px;border-top:1px solid #e7edf5}
.mail-chat .mc-process{margin-left:auto;border:1px solid #dce4ef;border-radius:7px;padding:3px 9px;white-space:nowrap}
.mail-chat .mc-done{margin-left:auto;color:#4c927b;font-size:11px}
.mail-chat .mc-translation{white-space:pre-wrap;overflow-wrap:anywhere;padding:12px 14px;margin-top:12px;border-left:2px solid #b8ccef;border-radius:0 7px 7px 0;background:#f3f7ff;color:#59708f;font-size:12px;line-height:1.85}
.mail-chat .mc-tag-row{display:flex;align-items:center;flex-wrap:wrap;gap:6px;margin-top:12px}
.mail-chat .mc-tag-row .inbound-tag-chip,.mail-chat .mc-header-meta .expert-tag{font-size:10px;line-height:1.6;padding:2px 6px;border-radius:5px;margin:0}
.mail-chat .mc-inline-error{padding:8px 0;color:#be123c;font-size:11px;line-height:1.6}
.mail-chat .mc-section{background:#fff;border-radius:10px}
.mail-chat .mc-section>summary{display:flex;align-items:center;gap:8px;list-style:none;font-size:12px;font-weight:600;color:#506783}
.mail-chat .mc-section>summary::-webkit-details-marker{display:none}
.mail-chat .mc-section>summary::after{content:'⌄';margin-left:auto;color:#91a1b7}
.mail-chat .mc-section[open]>summary::after{content:'⌃'}
.mail-chat .mc-section>summary:hover{background:#f5f8ff}
.mail-chat .mc-section>summary:active{background:#edf3ff}
.mail-chat .mc-editor{min-height:100px;max-height:240px}
.mail-chat .mc-manage-overlay{position:fixed;inset:0;z-index:990;display:flex;align-items:center;justify-content:center;padding:16px;background:#172c4738;backdrop-filter:blur(2px)}
.mail-chat .mc-manage-dialog{width:480px;max-width:100%;max-height:85dvh;overflow:auto;background:#fff;color:#475d79;border:1px solid #d9e3f1;border-radius:14px;box-shadow:0 20px 90px #17325730}
.mail-chat .mc-manage-dialog header{display:flex;align-items:flex-start;justify-content:space-between;padding:18px 20px;border-bottom:1px solid #edf1f7}
.mail-chat .mc-manage-dialog h3{font-size:16px;font-weight:600}
.mail-chat .mc-manage-dialog header p{margin-top:6px;color:#96a6ba;font-size:11px}
.mail-chat .mc-status-grid{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:16px;padding:22px 20px}
.mail-chat .mc-settings-tags{padding:0 20px 20px}
.mail-chat .mc-settings-tags .expert-tag-editor{margin:0;padding:0;border:0;background:transparent;box-shadow:none}
.mail-chat .mc-settings-tags .tag-editor-loading{min-height:0}
.mail-chat .mc-settings-tags .inbound-tag-editor-head{display:flex;align-items:center;justify-content:space-between;gap:8px;margin:0 0 12px}
.mail-chat .mc-settings-tags .inbound-tag-editor-head h3{font-size:12px}
.mail-chat .mc-settings-tags .inbound-tag-editor-chips{display:flex;flex-wrap:wrap;gap:6px}
.mail-chat .mc-settings-tags .expert-tag{font-size:11px;line-height:1.6;padding:2px 7px;border-radius:5px;margin:0}
.mail-chat .mc-manage-note{padding:0 20px 16px;color:#8b9bb1;font-size:11px;line-height:1.7}
.mail-chat .mc-dialog-actions{display:flex;justify-content:flex-end;gap:8px;padding:14px 20px;border-top:1px solid #edf1f7}
.mail-chat .mc-dialog-actions .button{width:auto;flex:none}
.mail-chat .button{height:32px;min-height:32px;padding:0 11px;font-size:12px;border-radius:7px}
.mail-chat .button:not(.primary){box-shadow:none}
.mail-chat .mc-manage-dialog .mc-inline-error,.mail-chat .mc-filter-popover .mc-inline-error{padding:0 20px 12px}
@media(max-width:1100px){.mail-chat{grid-template-columns:275px minmax(0,1fr);gap:12px}.mail-chat .mc-header{padding:15px}.mail-chat .mc-timeline-head{padding:10px 15px}.mail-chat .mc-message{width:96%}.mail-chat .mc-header .button{font-size:11px;padding:0 8px}}
@media(max-width:760px){#view-mailbox.mc-refined #mailboxList{padding:12px}.mail-chat{height:auto;min-height:0}.mail-chat .mc-search-row{position:static}.mail-chat .mc-filter-popover{position:fixed;top:100px;left:16px;width:calc(100vw - 32px);max-width:none;max-height:calc(100dvh - 116px)}.mail-chat .mc-scroll{max-height:65dvh;overflow:auto}.mail-chat .mc-message{width:100%}.mail-chat .mc-timeline-head{padding:10px 12px}.mail-chat .mc-position-hint{display:none}.mail-chat .mc-status-grid{grid-template-columns:1fr}.mail-chat .mc-manage-dialog{max-height:calc(100dvh - 32px)}}
@media(prefers-reduced-motion:reduce){.mail-chat *{scroll-behavior:auto!important;transition:none!important}}

.mail-chat.mc-overlay-root{display:contents}

/* S-7: expert tags in the list, one line only. */
.mail-chat .mc-person-heading{display:flex;align-items:center;gap:6px;min-width:0}
.mail-chat .mc-person-heading strong{flex:1;min-width:0}
.mail-chat .mc-person-meta{flex-wrap:nowrap;min-width:0;gap:6px;max-width:100%}
.mail-chat .mc-person-counts{flex:none;white-space:nowrap;font-size:11px;color:#79899f}
.mail-chat .mc-person-tags{display:block;flex:1;min-width:0;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;line-height:20px;color:#6482b2;cursor:help}
.mail-chat .mc-person-tag{display:inline;padding:2px 5px;margin-right:4px;border:1px solid #d5e2fb;border-radius:5px;background:#edf3ff;color:#5476ba;font-size:10px;line-height:16px;white-space:nowrap}
.mail-chat .mc-person-tags:hover .mc-person-tag{background:#e6efff;border-color:#b8cef3}
.mail-chat .mc-person-tags-unavailable{color:#94a3b8;font-size:10px}
```

## src/main/resources/static/styles.css:1

```css
:root {
    /* Brand — modern business blue */
    --primary: #1e40af;
    --primary-hover: #1e3a8a;
    --primary-active: #172554;
    --primary-rgb: 30, 64, 175;
    --primary-light: rgba(var(--primary-rgb), 0.07);
    --primary-tint: rgba(var(--primary-rgb), 0.1);

    --bg-main: #f5f7fb;
    --bg-sidebar: #ffffff;
    --bg-sidebar-hover: rgba(var(--primary-rgb), 0.06);
    --bg-sidebar-active: rgba(var(--primary-rgb), 0.09);

    --panel-bg: rgba(255, 255, 255, 0.55);
    --panel-border: rgba(15, 23, 42, 0.08);
    --line: rgba(15, 23, 42, 0.055);
    --border: rgba(15, 23, 42, 0.11);
    --surface: rgba(15, 23, 42, 0.022);

    --text-main: #1e293b;
    --text-muted: #94a3b8;
    --text-sidebar: #64748b;
    --text-sidebar-active: #1e293b;
    --text-secondary: #475569;
    --text-strong: #334155;
    --ink: #1e293b;
    --bg-subtle: #f8fafc;
    --border-strong: #cbd5e1;
    --primary-bright: #3b82f6;

    --success: #059669;
    --success-rgb: 5, 150, 105;
    --success-bg: rgba(var(--success-rgb), 0.08);
    --success-border: rgba(var(--success-rgb), 0.18);
    --green: var(--success);

    --error: #e11d48;
    --error-rgb: 225, 29, 72;
    --error-bg: rgba(var(--error-rgb), 0.07);
    --error-border: rgba(var(--error-rgb), 0.16);
    --error-strong: #be123c;
    --red: var(--error);

    --warning: #d97706;
    --warning-rgb: 217, 119, 6;
    --warning-bg: rgba(var(--warning-rgb), 0.08);
    --warning-border: rgba(var(--warning-rgb), 0.2);
    --warning-strong: #b45309;
    --warning-bright: #f59e0b;
    --amber: var(--warning);

    --info: #0ea5e9;
    --info-rgb: 14, 165, 233;
    --info-bg: rgba(var(--info-rgb), 0.08);
    --info-border: rgba(var(--info-rgb), 0.2);

    --z-sticky: 10;
    --z-dropdown: 20;
    --z-overlay: 50;
    --z-drawer: 60;
    --z-modal: 1000;
    --z-confirm: 1200;
    --z-toast: 9999;

    --glass-border: rgba(255, 255, 255, 0.5);
    --glass-shadow: 0 8px 32px rgba(var(--primary-rgb), 0.1);
    --glass-blur: blur(16px);

    --radius-sm: 7px;
    --radius-md: 10px;
    --radius-lg: 18px;

    --shadow-sm: 0 1px 2px rgba(15, 23, 42, 0.04);
    --shadow-md: 0 1px 3px rgba(15, 23, 42, 0.06), 0 1px 2px rgba(15, 23, 42, 0.03);
    --shadow-lg: 0 10px 28px -8px rgba(15, 23, 42, 0.14), 0 2px 6px rgba(15, 23, 42, 0.05);
```

## src/main/resources/static/styles.css:303

```css
    color: var(--text-main);
}

p {
    font-size: 12px;
    color: var(--text-muted);
    margin-top: 2px;
}
```

## src/main/resources/static/styles.css:802

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

.button.secondary {
```

## src/main/resources/static/styles.css:1117

```css
label {
    display: flex;
    flex-direction: column;
    gap: 4px;
    color: var(--text-muted);
    font-size: 11px;
    font-weight: 600;
    font-family: var(--font-body);
    text-transform: uppercase;
    letter-spacing: 0.3px;
}

input, select, textarea {
    width: 100%;
    height: 34px;
    min-height: 34px;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    padding: 6px 10px;
    color: var(--text-main);
    background-color: var(--panel-bg);
    transition: var(--transition);
    outline: none;
    font-size: 13px;
}

input:hover, select:hover, textarea:hover {
    border-color: rgba(15, 23, 42, 0.2);
}

input:focus, select:focus, textarea:focus {
    border-color: var(--primary);
    background-color: var(--panel-bg);
    box-shadow: 0 0 0 3px rgba(var(--primary-rgb), 0.1), 0 0 12px rgba(var(--primary-rgb), 0.06);
    transition: border-color 0.2s ease, box-shadow 0.25s ease;
}

input:disabled, select:disabled, textarea:disabled {
    background-color: var(--surface);
    color: var(--text-muted);
    cursor: not-allowed;
    border-color: var(--border);
}

textarea {
    resize: vertical;
    height: auto;
}

```

## src/main/resources/static/index.html:9

```html
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=JetBrains+Mono:wght@400;500;600;700&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="styles.css?v=20260909-mailbox-refinement">
    <link rel="stylesheet" href="expert-materials.css?v=20260909-mailbox-refinement">
    <link rel="stylesheet" href="mailbox-chat.css?v=20260909-mailbox-refinement">
</head>
```

## src/main/resources/static/index.html:2107

```html
<!-- App Core controller -->
<script src="task-modal-runtime.js"></script>
<script src="trust-reply-workbench.js?v=20260909-mailbox-refinement"></script>
<script src="expert-materials.js?v=20260909-mailbox-refinement"></script>
<script src="mailbox-chat.js?v=20260909-mailbox-refinement"></script>
<script src="app.js?v=20260909-mailbox-refinement"></script>
</body>
</html>
```
