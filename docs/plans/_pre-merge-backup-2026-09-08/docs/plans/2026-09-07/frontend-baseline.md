# 前端改动前基线（逐字快照）

现有生产源代码，不把预览 mock 作为接口事实。S 契约仅新增命名空间规则，不修改以下全局规则。

## src/main/resources/static/styles.css:1

SHA-256（全文件）：`09201fc8d34688cfab2a13b5a0f0d6c016f392f9c6c302c90046f548982e800c`

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
```

## src/main/resources/static/styles.css:802

SHA-256（全文件）：`09201fc8d34688cfab2a13b5a0f0d6c016f392f9c6c302c90046f548982e800c`

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
```

## src/main/resources/static/styles.css:906

SHA-256（全文件）：`09201fc8d34688cfab2a13b5a0f0d6c016f392f9c6c302c90046f548982e800c`

```css
.contacts-layout {
    grid-template-columns: 500px 6px minmax(0, 1fr);
    align-items: stretch;
    flex: 1 1 auto;
    min-height: 320px;
}

/* Resizer Divider */
.layout-resizer {
    width: 6px;
    cursor: col-resize;
    touch-action: none;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: transparent;
    transition: background-color 0.2s ease;
    user-select: none;
    align-self: stretch;
    position: relative;
    z-index: var(--z-sticky);
}

.layout-resizer:hover,
.layout-resizer.dragging {
    background-color: var(--primary-light);
}

.resizer-handle {
    width: 2px;
    height: 32px;
    background-color: var(--border);
    border-radius: 1px;
    transition: background-color 0.2s ease;
}

.layout-resizer:hover .resizer-handle,
.layout-resizer.dragging .resizer-handle {
    background-color: var(--primary);
}

/* Panels */
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

```

## src/main/resources/static/styles.css:1659

SHA-256（全文件）：`09201fc8d34688cfab2a13b5a0f0d6c016f392f9c6c302c90046f548982e800c`

```css
.metadata-card {
    background-color: rgba(15, 23, 42, 0.02);
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-sm);
    padding: 10px 12px;
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.metadata-card:hover {
    border-color: var(--border);
}

.metadata-card-header {
    display: flex;
    align-items: center;
    gap: 4px;
    color: var(--text-muted);
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    font-family: var(--font-body);
}

.metadata-card-header svg {
    color: var(--text-muted);
    flex-shrink: 0;
}

.metadata-card-value {
    font-size: 13px;
    font-weight: 500;
    color: var(--text-main);
    word-break: break-all;
    line-height: 1.4;
}

.metadata-card-value a {
    color: var(--primary);
    text-decoration: none;
    display: inline-flex;
    align-items: center;
    gap: 4px;
}

.metadata-card-value a:hover {
    text-decoration: underline;
}

.metadata-card.span-all {
    grid-column: 1 / -1;
}

.document-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    padding: 8px 10px;
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-sm);
    background: transparent;
}

.document-row > div {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
}

.document-row strong {
    font-size: 12px;
    color: var(--text-main);
    word-break: break-word;
}

.document-row span {
    color: var(--text-muted);
    font-size: 11px;
    font-weight: 500;
}

.next-action-text {
    color: var(--text-main);
```

## src/main/resources/static/styles.css:3048

SHA-256（全文件）：`09201fc8d34688cfab2a13b5a0f0d6c016f392f9c6c302c90046f548982e800c`

```css
.document-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-top: 4px;
}

.document-actions {
    display: flex;
    gap: 4px;
    flex-shrink: 0;
}

.document-card-header {
    justify-content: space-between;
}

.document-card-header-title {
    display: flex;
    align-items: center;
    gap: 4px;
}

/* AI Document Analysis */
.ai-analysis-modal {
    width: min(720px, 92vw);
}

.ai-analysis-modal-body {
    display: flex;
    flex-direction: column;
```

## src/main/resources/static/index.html:662

SHA-256（全文件）：`84fa0801a610f8ddb4916fd6bae1e928fd44835baef809d6812f467eec20c564`

```html
            <div class="split-layout contacts-layout">
                <!-- Left panel: candidate list -->
                <section class="panel contacts-list-panel">
                    <div class="panel-head">
                        <h2>专家列表</h2>
                        <div class="layout-preset-group" title="调整左右分栏比例（也可拖拽中缝，双击恢复默认）">
                            <button class="layout-preset-btn" id="btnLayoutDefault" title="默认分栏 (500px)">▏</button>
                            <button class="layout-preset-btn" id="btnLayoutWideList" title="宽列表 (500px)">▎</button>
                            <button class="layout-preset-btn" id="btnLayoutSplit" title="左右等宽 (1:1)">▌</button>
                        </div>
                    </div>
                    <div id="contactCountInfo" class="contact-count-info text-muted"></div>
                    <div id="contactList" class="list"></div>
                    <div id="contactPager" class="list-pager">
                        <select id="expertIndexSize" aria-label="每页行数">
                            <option value="10">10 条/页</option>
                            <option value="20">20 条/页</option>
                            <option value="50" selected>50 条/页</option>
                            <option value="100">100 条/页</option>
                        </select>
                        <button class="button small" id="contactPrevPage">上一页</button>
                        <span id="contactPageInfo" class="list-pager-info"></span>
                        <button class="button small" id="contactNextPage">下一页</button>
                    </div>
                </section>

                <!-- Resizer Divider -->
                <div class="layout-resizer" id="contactsLayoutResizer" title="拖拽调整宽度，双击恢复默认">
                    <div class="resizer-handle"></div>
                </div>

                <!-- Right panel: detail & timeline view -->
                <section class="panel contact-detail-panel">
                    <div class="panel-head contact-detail-head">
                        <div style="display: flex; align-items: center; gap: 8px; width: 100%; margin-bottom: 8px;">
                            <h2 style="flex: 1; margin: 0;">专家引进状态与联系详情</h2>
                        </div>
                        <div class="contact-head-actions" id="contactHeadActions" hidden></div>
                    </div>
                    <div id="contactDetail" class="detail-empty">
                        <svg viewBox="0 0 24 24" width="48" height="48" stroke="currentColor" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round" style="color: var(--text-muted);">
                            <circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/>
                        </svg>
                        <span>请在左侧列表中选择一位专家以查看详细往来记录和操作状态。</span>
                    </div>
                </section>
            </div>
        </section>

        <!-- View: Mailbox (Inbound / Outbound records) -->
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

            <section class="panel" id="unmatchedDetailPanel" hidden style="margin-top: 16px;">
                <div class="panel-head">
                    <h2>工单详情与专家关系映射</h2>
                    <button class="button secondary" id="closeUnmatchedDetailBtn">收起面板</button>
```

## src/main/resources/static/app.js:8363

SHA-256（全文件）：`d20d716b45f071c5b01fc3ef2235880c3769cef94201eccb39763ccf07a395f6`

```javascript
function renderExpertDocuments(documents, contactId) {
    const list = Array.isArray(documents) ? documents : (documents?.records || []);
    if (list.length === 0) {
        return `
            <div class="metadata-card-header">
                <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
                <span>专家上传资料</span>
            </div>
            <p style="color: var(--text-muted); font-size: 12px;">暂无资料文件。</p>
        `;
    }
    return `
        <div class="metadata-card-header document-card-header">
            <div class="document-card-header-title">
                <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
                <span>专家上传资料</span>
            </div>
            <button class="button small primary" type="button" data-action="open-ai-analysis" data-contact-id="${contactId}">AI 智能分析</button>
        </div>
        <div class="document-list">
            ${list.map(doc => `
                <div class="document-row">
                    <div>
                        <strong>${escapeHtml(doc.fileName || "?")}</strong>
                        <span>${escapeHtml(labelDocumentType(doc.documentType))}&nbsp;·&nbsp;${escapeHtml(labelDocumentStatus(doc.documentStatus))}&nbsp;·&nbsp;${formatFileSize(doc.fileSize)}</span>
                        ${doc.createdAt ? `<span style="font-size:11px">${escapeHtml(doc.createdAt)}</span>` : ""}
                    </div>
                    <div class="document-actions">
                        ${doc.downloadUrl ? `<a class="button small" href="${contextPath}${escapeHtml(doc.downloadUrl)}" download>下载</a>` : ""}
                        ${doc.previewable && doc.previewUrl ? `<button class="button small" data-action="preview-document" data-url="${escapeHtml(doc.previewUrl)}">预览</button>` : ""}
                    </div>
                </div>
            `).join("")}
        </div>
    `;
}

const AI_ANALYSIS_DEFAULT_TYPES = new Set(["CV", "PHD_DEGREE", "MASTER_DEGREE", "BACHELOR_DEGREE"]);
```

## src/main/resources/static/app.js:10845

SHA-256（全文件）：`d20d716b45f071c5b01fc3ef2235880c3769cef94201eccb39763ccf07a395f6`

```javascript
                        <h3>${escapeHtml(record.subject || "（无主题）")}</h3>
                        <p>来自 ${escapeHtml(record.fromEmail)}</p>
                    </div>
                    <div class="mail-overview-meta">
                        <span>${escapeHtml(record.receivedAt || "-")}</span>
                        <span>账号：${escapeHtml(record.senderAccountCode || "-")}</span>
                    </div>
                </div>
                ${renderMailboxInboundTagEditor(inboundTags, Number(id))}
                ${linkedExpertHtml}
                <details class="mail-technical-detail">
                    <summary>邮件技术信息 · Message-ID / In-Reply-To</summary>
                    <div class="mail-technical-grid">
                        <div><span>Message-ID</span><code>${escapeHtml(record.messageId || "-")}</code></div>
                        <div><span>In-Reply-To</span><code>${escapeHtml(record.inReplyTo || "-")}</code></div>
                    </div>
                </details>
            </div>

            ${record.body ? `
            <div class="mail-detail-group-label"><span>邮件正文</span></div>
            <details class="detail-section reply-workflow-detail mail-body-section original-mail-body-section">
                <summary class="reply-workflow-summary">
                    <span class="reply-workflow-icon" aria-hidden="true">原</span>
                    <span class="reply-workflow-title"><strong>原始正文</strong><small>包含原始引用、签名及未清洗内容</small></span>
                    <span class="reply-workflow-chevron" aria-hidden="true">⌄</span>
                </summary>
                <div class="reply-workflow-content mail-body-content">
                ${translatableBody(record.body)}
                </div>
            </details>` : ""}

            ${record.cleanedBody ? `
            <details class="detail-section reply-workflow-detail mail-body-section cleaned-mail-body-section" open>
                <summary class="reply-workflow-summary">
                    <span class="reply-workflow-icon" aria-hidden="true">净</span>
                    <span class="reply-workflow-title"><strong>清洗后正文</strong><small>已移除引用历史与签名，默认显示</small></span>
                    <span class="reply-workflow-status">默认显示</span>
                    <span class="reply-workflow-chevron" aria-hidden="true">⌄</span>
                </summary>
                <div class="reply-workflow-content mail-body-content">
                ${translatableBody(record.cleanedBody)}
                </div>
            </details>` : ""}

            <div class="mail-detail-group-label"><span>处理与回复</span></div>
            ${historyHtml}

            ${composeWorkbenchHtml}

            <details class="detail-section reply-workflow-detail manual-rich-reply-section">
                <summary class="reply-workflow-summary">
                    <span class="reply-workflow-icon" aria-hidden="true">✎</span>
                    <span class="reply-workflow-title"><strong>人工富文本回复</strong><small>手动编辑主题和正文后发送</small></span>
                    <span class="reply-workflow-status">未填写</span>
                    <span class="reply-workflow-chevron" aria-hidden="true">⌄</span>
                </summary>
                <div class="reply-workflow-content">
                <input id="manualReplySubject" placeholder="邮件主题" value="${escapeHtml(buildManualReplySubject(record.subject))}" style="margin-bottom:8px;">
                <div class="rich-toolbar">
                    <button type="button" data-action="rich-command" data-command="bold"><strong>B</strong></button>
                    <button type="button" data-action="rich-command" data-command="italic"><em>I</em></button>
                    <button type="button" data-action="rich-command" data-command="insertUnorderedList">列表</button>
                    <button type="button" data-action="rich-command" data-command="createLink">链接</button>
                </div>
                <div id="manualRichReplyEditor" contenteditable="true" class="rich-editor"></div>
                <div id="manualReplyPreflight" class="ai-reply-feedback" role="status" aria-live="polite" hidden></div>
                <button class="button primary" data-action="send-manual-rich-reply" data-record-id="${id}" style="margin-top:8px;">发送人工回复</button>
                </div>
            </details>

            <div class="mail-detail-group-label"><span>操作记录</span></div>
            <details class="detail-section reply-workflow-detail operator-log-section">
                <summary class="reply-workflow-summary">
                    <span class="reply-workflow-icon" aria-hidden="true">记</span>
                    <span class="reply-workflow-title"><strong>操作日志</strong><small>查看处理、回复与状态变更记录</small></span>
```

## src/main/resources/static/app.js:13924

SHA-256（全文件）：`d20d716b45f071c5b01fc3ef2235880c3769cef94201eccb39763ccf07a395f6`

```javascript
function renderMailboxCard(row) {
    const timeStr = row.timestamp ? row.timestamp.replace('T', ' ').slice(0, 19) : "-";
    const directionBadge = row.direction === "INBOUND"
        ? '<span class="badge">收件</span>'
        : '<span class="badge ok">发件</span>';

    const expertEmailLink = row.expertContactId
        ? `<a href="javascript:void 0" data-action="open-monitoring-contact" data-id="${row.expertContactId}">${escapeHtml(row.expertEmail || "")}</a>`
        : escapeHtml(row.expertEmail || "-");

    let sourceBadge = "";
    if (row.direction === "OUTBOUND") {
        if (row.triggeredBy === "SYSTEM") {
            sourceBadge = '<span class="badge warn">系统自动</span>';
        } else if (row.triggeredBy === "OPERATOR") {
            sourceBadge = '<span class="badge">人工</span>';
        } else if (row.triggeredBy === "MANUAL") {
            sourceBadge = '<span class="badge">手动</span>';
        }
    }

    const attachment = row.hasAttachment
        ? '<span class="badge warn" title="有附件">📎 附件</span>'
        : "";

    const sendStatus = (row.direction === "OUTBOUND" && row.sendStatus !== "SENT")
        ? `<span class="badge error" title="发送失败">发送失败</span>`
        : "";

    const actions = renderMailboxActions(row);

    return `
        <div class="mailbox-card" data-source="${escapeHtml(row.source || "")}" data-id="${escapeHtml(row.id)}">
            <div class="mailbox-card-tags">
                ${directionBadge}
                ${renderMailboxTagBadges(row.tags)}
                ${sourceBadge}
                ${attachment}
                ${sendStatus}
            </div>
            <div class="mailbox-card-subject">${escapeHtml(row.subject || "(无主题)")}</div>
            <div class="mailbox-card-meta">
                <span>${escapeHtml(timeStr)}</span>
                <span>${escapeHtml(row.senderAccountCode || "-")}</span>
                <span>${escapeHtml(row.expertName || "-")}</span>
                <span>${expertEmailLink}</span>
            </div>
            ${actions ? `<div class="mailbox-card-actions">${actions}</div>` : ""}
        </div>
    `;
}

function renderMailboxExpertGroups() {
    const list = $("#mailboxList");
    const groups = state.mailbox.groups || [];

    if (groups.length === 0) {
        list.innerHTML = `<div class="mailbox-empty muted">${state.mailbox.onlyPending ? "暂无待处理专家" : "暂无关联专家邮件"}</div>`;
        return;
    }

    list.innerHTML = groups.map((group) => {
        const nameLink = group.expertContactId
            ? `<a href="javascript:void 0" data-action="open-monitoring-contact" data-id="${group.expertContactId}">${escapeHtml(group.expertName || "-")}</a>`
            : escapeHtml(group.expertName || "-");
        const statusBadge = badge(operatorStatusLabels[group.operatorStatus] || group.operatorStatus || "?", "ok");
        const levelBadge = badge(indexLevelLabels[group.expertIndexLevel] || group.expertIndexLevel || "?", "");
        const emailLine = `${escapeHtml(group.expertEmail || "-")} · ${escapeHtml(group.expertOrcidId || "-")}`;
        const mailCards = (group.mails || []).map((row) => renderMailboxCard(row)).join("");
        const countText = state.mailbox.onlyPending
            ? `${group.pendingCount} 封待处理`
            : `共 ${group.mailCount} 封${group.pendingCount > 0 ? ` · 待处理 ${group.pendingCount} 封` : ""}`;
        const focused = state.mailbox.focusExpertContactId != null
            && String(group.expertContactId) === String(state.mailbox.focusExpertContactId);

        return `
            <details class="inbound-expert-group" data-expert-contact-id="${escapeHtml(group.expertContactId)}"${focused ? " open" : ""}>
                <summary class="inbound-expert-group-header">
                    <span class="inbound-expert-group-name">${nameLink} ${statusBadge} ${levelBadge}</span>
                    <span class="inbound-expert-group-email">${emailLine}</span>
                    <span class="inbound-expert-group-count">${escapeHtml(countText)}</span>
                </summary>
                <div class="inbound-expert-group-mails">${mailCards}</div>
            </details>
        `;
    }).join("");
}

function renderMailboxTable() {
    const list = $("#mailboxList");
    const tagFilter = state.mailbox.tagFilter;
    const rows = (state.mailbox.items || []).filter((row) => {
        if (!tagFilter || tagFilter === "待处理") return true;
        return (row.tags || []).includes(tagFilter);
    });

    if (rows.length === 0) {
        list.innerHTML = `<div class="mailbox-empty muted">暂无邮件记录</div>`;
        return;
    }

    list.innerHTML = rows.map((row) => renderMailboxCard(row)).join("");
```
