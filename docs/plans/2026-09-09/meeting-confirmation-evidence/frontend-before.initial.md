# 修改前前端逐字基线

2026-09-09 工作区源码，不代表已部署版本。行号与哈希共同定位。

## src/main/resources/static/mailbox-chat.js:888

```javascript
        function renderManualSection() {
            const body = conversationBody();
            if (!body) return;
            const scroll = body.querySelector(".mc-scroll");
            if (!scroll) return;
            const summary = instance.selectedSummary || {};
            const latestInbound = summary.latestInbound || null;
            const mode = latestInbound && latestInbound.processingId != null ? "inbound" : "outboundOnly";
            const targetProcessingId = mode === "inbound" ? Number(latestInbound.processingId) : null;
            const targetAccount = mode === "inbound" ? (latestInbound.accountCode || "") : "";
            const targetKey = mode === "inbound" ? `${Number(instance.selectedContactId)}:${targetProcessingId}:${targetAccount}` : null;

            let existing = scroll.querySelector('.mc-section[data-section="manual"]');
            if (existing && instance.manual.mode === mode && instance.manual.targetKey === targetKey) {
                return; // 同一目标：保留用户正在编辑的内容
            }
            if (existing) existing.remove();

            const targetMsg = targetProcessingId != null ? latestInboundMessage() : null;
            const defaultSubject = targetMsg ? chatSubjectPrefill(targetMsg.subject) : "Re:";
            const draft = targetKey != null ? (instance.drafts.get(targetKey) || null) : null;

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
```

## src/main/resources/static/mailbox-chat.js:586

```javascript
        function renderMessage(message) {
            const direction = message.direction === "OUTBOUND" ? "OUTBOUND" : "INBOUND";
            const isInboundProcessing = message.source === "INBOUND_PROCESSING";
            const key = `${message.source}:${message.id}`;
            const account = message.accountCode || "";
            const who = isInboundProcessing
                ? `${SOURCE_LABELS.INBOUND_PROCESSING || "专家来信"} · ${timePart(message.eventAt)}${account ? ` · ${escapeText(account)}` : ""}`
                : `${direction === "OUTBOUND" ? "发出邮件" : "往来邮件"} · ${timePart(message.eventAt)}${account ? ` · ${escapeText(account)}` : ""}`;
            const statusBadge = renderStatusBadge(message, direction);
            const subject = message.subject || "(无主题)";
            const displayBody = String(message.cleanedBody || message.body || "").trim()
                ? escapeText(message.cleanedBody || message.body)
                : "";
            const attachmentHtml = Number(message.attachmentCount) > 0
                ? renderAttachmentSummary(message)
                : "";
            const extrasHtml = renderExtrasBlock(message, direction);
            const footerHtml = renderFooter(message, direction);
            return `
                <article class="mc-message" data-direction="${direction}" data-source="${escapeText(message.source)}" data-id="${escapeText(message.id)}" data-message-key="${escapeText(key)}">
                    <header><span>${who}</span>${statusBadge ? `<span>${statusBadge}</span>` : ""}</header>
                    <h3>${escapeText(subject)}</h3>
                    ${displayBody ? `<div class="mc-body">${displayBody}</div>` : ""}
                    ${attachmentHtml}
                    ${extrasHtml}
                    ${footerHtml ? `<footer>${footerHtml}</footer>` : ""}
                </article>
            `;
        }

        function renderStatusBadge(message, direction) {
            if (direction === "INBOUND") {
                if (isManualReview(message)) {
                    return '<span class="mc-badge" data-tone="pending">待处理</span>';
                }
```

## src/main/resources/static/mailbox-chat.css:1

```css
.mail-chat{display:grid;grid-template-columns:320px minmax(0,1fr);gap:16px;min-height:620px;height:calc(100dvh - 220px);color:#475569;font-size:12px;line-height:1.6}
.mail-chat *{box-sizing:border-box}
.mail-chat [hidden]{display:none!important}
.mail-chat :is(h2,h3,p){margin:0}
.mail-chat .mc-experts,.mail-chat .mc-conversation{display:flex;flex-direction:column;min-width:0;min-height:0;border:1px solid rgba(15,23,42,.11);border-radius:18px;background:rgba(255,255,255,.55);overflow:hidden}
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
.mail-chat .mc-person[data-active=true]{border-color:#4564df;border-left-color:#e11d48;background:#e8edfb}
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
.mail-chat .mc-badge[data-tone=waiting]{background:#eff5ff;border-color:#bfdbfe;color:#1e40af}
.mail-chat .mc-badge[data-tone=success]{background:#ecfdf5;border-color:#a7f3d0;color:#059669}
.mail-chat .mc-badge[data-tone=error]{background:#fff1f2;border-color:#fecdd3;color:#e11d48}
.mail-chat .mc-pager{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:6px;padding:10px 12px;border-top:1px solid #e2e8f0;color:#64748b;font-size:11px}
.mail-chat .mc-header{display:flex;align-items:flex-start;justify-content:space-between;flex-wrap:wrap;gap:12px;padding:16px 18px;border-bottom:1px solid #e2e8f0}
.mail-chat .mc-identity{flex:1;min-width:180px}
.mail-chat .mc-identity h2{font-size:16px;font-weight:600;color:#1e293b;overflow-wrap:anywhere}
.mail-chat .mc-identity p{margin-top:4px;color:#64748b;font-size:11px;overflow-wrap:anywhere}
.mail-chat .mc-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
.mail-chat .mc-scroll{flex:1;min-height:0;overflow:auto;padding:16px 18px;overscroll-behavior:contain;scrollbar-gutter:stable}
.mail-chat .mc-expert-settings{padding:10px 12px;margin-bottom:14px;border:1px solid #dce4ef;border-radius:10px;background:#f8faff}
.mail-chat .mc-expert-settings>summary{cursor:pointer;color:#475569;font-weight:600}
.mail-chat .mc-settings-content{display:grid;gap:12px;margin-top:12px}
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

## src/main/resources/static/index.html:9

```html
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=JetBrains+Mono:wght@400;500;600;700&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="styles.css?v=20260907-material-chat">
    <link rel="stylesheet" href="expert-materials.css?v=20260907-material-chat">
    <link rel="stylesheet" href="mailbox-chat.css?v=20260907-material-chat">
</head>
```

## src/main/resources/static/index.html:2107

```html
<!-- App Core controller -->
<script src="task-modal-runtime.js"></script>
<script src="trust-reply-workbench.js?v=20260907-material-chat"></script>
<script src="expert-materials.js?v=20260907-material-chat"></script>
<script src="mailbox-chat.js?v=20260907-material-chat"></script>
<script src="app.js?v=20260907-material-chat"></script>
</body>
</html>
```
