# 前端改动前逐字基线

来源为当前仓库实际文件；不是线上部署版本认证。

## src/main/resources/static/index.html:714

```html
        <section class="view" id="view-mailbox">
            <div class="toolbar">
                <button class="button primary" id="mailboxRefreshBtn">刷新</button>
                <div class="mailbox-view-controls">
                    <div class="mailbox-segmented-control mailbox-view-mode" role="radiogroup" aria-label="收发件箱展示方式">
                        <label><input type="radio" name="mailboxViewMode" value="MAIL" checked><span>按邮件</span></label>
                        <label><input type="radio" name="mailboxViewMode" value="EXPERT"><span>按专家聚合</span></label>
                    </div>
                    <div class="mailbox-segmented-control mailbox-scope-mode" role="radiogroup" aria-label="收发件箱邮件范围">
                        <label><input type="radio" name="mailboxMailScope" value="ALL" checked><span>全部邮件</span></label>
                        <label><input type="radio" name="mailboxMailScope" value="PENDING"><span>仅待处理</span></label>
                    </div>
                </div>
                <select id="mailboxFilterAccountCode">
                    <option value="">全部邮箱账号</option>
                </select>
                <select id="mailboxFilterDirection">
                    <option value="">全部收发方向</option>
                    <option value="INBOUND">收件 (INBOUND)</option>
                    <option value="OUTBOUND">发件 (OUTBOUND)</option>
                </select>
                <select id="mailboxFilterTag">
                    <option value="">全部标签</option>
                    <option value="专家">专家</option>
                    <option value="待匹配">待匹配</option>
                    <option value="自动回复">自动回复</option>
                    <option value="手动回复">手动回复</option>
                    <option value="首发">首发</option>
                    <option value="待处理">待处理</option>
                    <option value="收件">收件</option>
                    <option value="发件">发件</option>
                </select>
                <input id="mailboxFilterRecipient" placeholder="过滤收件人邮箱" style="width: 180px;">
                <input id="mailboxFilterKeyword" placeholder="过滤主题/内容关键词" style="width: 200px;">
                <input type="date" id="mailboxFilterStartDate" style="width: 130px;">
                <span>至</span>
                <input type="date" id="mailboxFilterEndDate" style="width: 130px;">
                <button class="button primary" id="mailboxSearchBtn">查询</button>
            </div>

            <!-- B4 (S2b-3): 按任务执行过滤的提示条；位于既有 .toolbar 之下、不与标题栏「批量发送」按钮同行 -->
            <div id="mailboxExecutionFilterBar" class="toolbar" hidden>
                <span class="text-muted" id="mailboxExecutionFilterText"></span>
                <button type="button" class="button small" id="mailboxExecutionFilterClear">清除过滤</button>
            </div>

            <section class="panel">
                <div class="panel-head">
                    <h2>已激活账号收发邮件记录</h2>
                    <div class="panel-head-actions">
                        <button class="button" id="checkRepliesBtn" onclick="handleCheckReplies()">检查回复</button>
                        <button class="button primary" id="bulkOutreachBtn" onclick="handleBulkOutreach()">批量发送</button>
                        <button class="button" id="bulkAutoReplyBtn">自动回复：加载中...</button>
                    </div>
                </div>
                <div class="mailbox-list" id="mailboxList"></div>
                <div class="pagination" id="mailboxPagination" style="padding: 16px 24px; display: flex; justify-content: flex-end; gap: 8px;"></div>
            </section>
```

## src/main/resources/static/mailbox-chat.js:190

```javascript
        // --------------------------------------------------------------
        // 渲染骨架（S-3 层级：专家栏 + 会话栏）
        // --------------------------------------------------------------

        function skeletonHtml() {
            return `
                <div class="mail-chat">
                    <aside class="mc-experts" aria-label="专家会话列表">
                        <div class="mc-list-tools">
                            <input type="search" aria-label="搜索专家" placeholder="搜索专家姓名、邮箱">
                            <div class="mc-filters">
                                ${FILTER_CHIPS.map((chip) =>
        `<button class="mc-filter" type="button" data-action="mc-filter" data-chip="${chip.key}" aria-pressed="${instance.chip === chip.key ? "true" : "false"}">${escapeText(chip.label)}</button>`
    ).join("")}
                            </div>
                        </div>
                        <div class="mc-expert-list" aria-live="polite"></div>
                        <div class="mc-pager"></div>
                    </aside>
                    <section class="mc-conversation" aria-label="专家往来信件"></section>
                </div>
            `;
        }

        function expertsRoot() {
            return host.querySelector ? host.querySelector(".mc-expert-list") : null;
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
                if (message.processStatus === "PROCESSED") {
                    return '<span class="mc-badge" data-tone="success">已处理</span>';
                }
                return "";
            }
            if (message.sendStatus === "SENT") {
                return '<span class="mc-badge" data-tone="success">已发送</span>';
            }
            if (message.sendStatus === "FAILED") {
                return '<span class="mc-badge" data-tone="error">发送失败</span>';
            }
            return "";
        }

        function isManualReview(message) {
            return message.processStatus === "MANUAL_REVIEW";
        }

        function renderAttachmentSummary(message) {
            const count = Number(message.attachmentCount) || 0;
            const names = Array.isArray(message.firstAttachmentNames) ? message.firstAttachmentNames : [];
            const nameRows = names.slice(0, 3).map((name) =>
                `<div title="${escapeText(name)}">${escapeText(name)}</div>`
            ).join("");
            return `
                <details class="mc-mail-extras" data-role="attachment-extras">
                    <summary>附件 ${count} 份 · 仅文件信息</summary>
                    <div class="mc-attachment-names">${nameRows}<button class="button" type="button" data-action="mc-view-attachments" data-contact-id="${escapeText(message.contactId)}">查看全部附件</button></div>
                </details>
            `;
        }

        function renderExtrasBlock(message, direction) {
            const technicalParts = [];
            if (message.messageId) technicalParts.push(`<div><span>Message-ID</span><code>${escapeText(message.messageId)}</code></div>`);
            if (message.inReplyTo) technicalParts.push(`<div><span>In-Reply-To</span><code>${escapeText(message.inReplyTo)}</code></div>`);
            if (message.accountCode) technicalParts.push(`<div><span>账号</span><code>${escapeText(message.accountCode)}</code></div>`);
            const isInboundProcessing = message.source === "INBOUND_PROCESSING";
            const originalText = direction === "INBOUND" && String(message.body || "").trim()
                ? String(message.body)
                : "";
            const tagHost = isInboundProcessing
                ? `<div data-role="mail-tags" data-inbound-id="${escapeText(message.id)}"></div>`
                : "";
            const originalHtml = originalText
                ? `<details><summary>原始正文（含引用与签名）</summary>${typeof global.translatableBody === "function" ? global.translatableBody(originalText) : `<div class="pre">${escapeText(originalText)}</div>`}</details>`
                : "";
            const techHtml = technicalParts.length
                ? `<details class="mail-technical-detail"><summary>技术信息 · Message-ID / 账号</summary><div class="mail-technical-grid">${technicalParts.join("")}</div></details>`
                : "";
            if (!tagHost && !originalHtml && !techHtml) return "";
            const tagsLine = tagHost
                ? `<p>邮件标签：</p>${tagHost}`
                : "";
            return `
                <details class="mc-mail-extras" data-role="mail-extras" data-source="${escapeText(message.source)}" data-id="${escapeText(message.id)}">
                    <summary>原文、翻译、邮件标签与技术信息</summary>
                    <div>${originalHtml}${tagsLine}${techHtml}</div>
                </details>
            `;
        }

        function renderFooter(message, direction) {
            if (direction === "INBOUND" && isManualReview(message)) {
                return `<button class="button" type="button" data-action="mc-mark-resolved" data-message-key="${escapeText(`${message.source}:${message.id}`)}">标记已处理</button>`;
            }
            return "";
        }

        function loadMailTags(inboundId, containerEl) {
            if (!containerEl) return;
            const key = `inbound:${inboundId}`;
            if (instance.extras.has(key)) {
                containerEl.innerHTML = instance.extras.get(key);
                return;
            }
            hostApi()(`/api/inbound-summary/mails/${inboundId}/thread`).then((data) => {
                if (instance.disposed) return;
                const tags = (data && Array.isArray(data.tags)) ? data.tags : [];
                let html = tags.length === 0 ? '<p>暂无邮件标签</p>' : "";
                html += tags.map((tag) => {
                    if (typeof global.renderInboundTagChip === "function") {
                        return global.renderInboundTagChip(tag, { removable: false });
                    }
                    const label = tag && tag.label ? tag.label : "";
                    const cls = ["inbound-tag-chip"].concat(tag && tag.tagType === "QA" ? ["qa"] : ["custom"]).join(" ");
                    return `<span class="${cls}">${escapeText(label)}</span>`;
                }).join("");
```

## src/main/resources/static/mailbox-chat.js:751

```javascript
        function renderSettingsBlock() {
            const body = conversationBody();
            if (!body) return;
            const scroll = body.querySelector(".mc-scroll");
            if (!scroll) return;
            const contact = instance.conversation.contact || null;
            if (!contact) {
                const existing = scroll.querySelector(".mc-expert-settings");
                if (existing) existing.remove();
                return;
            }
            let settings = scroll.querySelector(".mc-expert-settings");
            const statusValue = contact.operatorStatus || "";
            const levelValue = contact.currentIndexLevel || "";
            const statusOptions = optionsFromCatalog(global.operatorStatusOptions, statusValue);
            const levelOptions = optionsFromCatalog(global.indexLevelOptions, levelValue);
            const settingsHtml = `
                <details class="mc-expert-settings" data-role="expert-settings"${instance.settings.open ? " open" : ""}>
                    <summary>专家状态、层级与标签</summary>
                    <div class="mc-settings-content">
                        <label><span>专家状态</span>
                            <select data-role="status-select" data-current-value="${escapeText(statusValue)}">${statusOptions}</select>
                        </label>
                        <label><span>专家层级</span>
                            <select data-role="level-select" data-current-value="${escapeText(levelValue)}">${levelOptions}</select>
                        </label>
                        <button class="button primary" type="button" data-action="mc-save-settings">保存变更</button>
                        <div data-role="expert-tags">${instance.settings.tagsState === "loading" ? "<p>正在加载专家标签…</p>" : ""}</div>
                    </div>
                </details>
            `;
            if (!settings) {
                scroll.insertAdjacentHTML("afterbegin", settingsHtml);
                settings = scroll.querySelector(".mc-expert-settings");
            } else {
                settings.outerHTML = settingsHtml;
            }
            if (instance.settings.tagsState === "idle") {
                loadExpertTags(contact);
            }
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
    --shadow-xl: 0 20px 48px -12px rgba(15, 23, 42, 0.2), 0 4px 12px rgba(15, 23, 42, 0.06);
    --shadow: var(--shadow-md);

    --transition: all 0.15s ease;

    --font-mono: 'SF Mono', ui-monospace, Menlo, monospace;
    --font-body: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Helvetica Neue', sans-serif;

    --verbatim: #7c3aed;
    --verbatim-bg: rgba(124, 58, 237, 0.06);
    --verbatim-border: rgba(124, 58, 237, 0.24);
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
    background-color: var(--primary-light);
    border-color: rgba(var(--primary-rgb), 0.12);
    color: var(--primary);
}

.button.secondary:hover {
    background-color: rgba(var(--primary-rgb), 0.1);
}
```

## src/main/resources/static/styles.css:948

```css
.panel {
    background: var(--panel-bg);
    backdrop-filter: var(--glass-blur);
    -webkit-backdrop-filter: var(--glass-blur);
    border: 1px solid var(--glass-border);
    border-radius: var(--radius-lg);
    box-shadow: var(--glass-shadow);
}

.panel:hover {
    box-shadow: var(--shadow-lg);
    border-color: rgba(15, 23, 42, 0.12);
}

.panel-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 12px 16px;
    border-bottom: 1px solid var(--line);
}

.panel-head h2 {
    padding-left: 0;
}

.panel-head h2::before {
    display: none;
}

.panel-head-actions {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
}
```

## src/main/resources/static/mailbox-chat.css:1（完整旧规则）

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
