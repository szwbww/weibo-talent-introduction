const state = {
    view: "accounts",
    accounts: [],
    categories: [],
    qaRules: [],
    contacts: [],
    mailSendOptions: [],
    selectedAccount: null,
    accountEditorMode: null,
    selectedExpertOrcid: null,
    selectedRuleId: null
};

const contextPath = (() => {
    const firstSegment = window.location.pathname.split("/").filter(Boolean)[0];
    return firstSegment ? `/${firstSegment}` : "";
})();

const viewMeta = {
    accounts: ["邮箱账号", "维护发送账号、权重、限额和连通性。"],
    qa: ["QA 规则", "维护英文关键词规则、自动回复和人工处理策略。"],
    contacts: ["专家联系", "查看联系状态、邮件时间线和人工处理。"],
    tasks: ["任务记录", "查看定时任务、队列消费和失败记录。"]
};

const statusLabels = {
    NEW: "新建",
    INTRO_SENT: "首封已发送",
    WAITING_REPLY: "等待回复",
    QA_AUTO_REPLIED: "QA 已自动回复",
    MEETING_INVITATION_SENT: "会议邀约已发送",
    WAITING_MEETING_CONFIRMATION: "等待会议确认",
    MANUAL_HANDOFF: "已转人工",
    MANUAL_REVIEW: "待人工审核",
    CLOSED: "已关闭",
    RUNNING: "运行中",
    SUCCESS: "成功",
    FAILED: "失败",
    PENDING: "待处理",
    ASSIGNED: "已分配",
    COMPLETED: "已完成"
};

const indexLevelLabels = {
    RAW: "原始",
    CANDIDATE: "筛选",
    APPLICATION: "有效"
};

const mailDirectionLabels = {
    INBOUND: "收到",
    OUTBOUND: "发出"
};

const mailTypeLabels = {
    INTRODUCTION: "首封介绍",
    REPLY: "专家回复",
    QA_REPLY: "QA 自动回复",
    MANUAL_QA_REPLY: "手动 QA 回复",
    MEETING_INVITATION: "会议邀约"
};

function labelStatus(value) {
    return statusLabels[value] || value || "";
}

function labelMailDirection(value) {
    return mailDirectionLabels[value] || value || "";
}

function labelMailType(value) {
    return mailTypeLabels[value] || value || "";
}

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => Array.from(document.querySelectorAll(selector));

async function api(path, options = {}) {
    const response = await fetch(`${contextPath}${path}`, {
        headers: { "Content-Type": "application/json", ...(options.headers || {}) },
        ...options
    });
    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) {
        const message = data?.message || `${response.status} ${response.statusText}`;
        throw new Error(message);
    }
    return data;
}

function showStatus(message, type = "ok") {
    const bar = $("#statusBar");
    bar.textContent = message;
    bar.className = `status-bar ${type}`;
    bar.hidden = false;
    clearTimeout(showStatus.timer);
    showStatus.timer = setTimeout(() => {
        bar.hidden = true;
    }, 4200);
}

function badge(value, type) {
    return `<span class="badge ${type || ""}">${escapeHtml(value)}</span>`;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function formValues(form) {
    return Object.fromEntries(Array.from(new FormData(form).entries()).map(([key, value]) => [key, value]));
}

function numberValue(value, fallback = 0) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
}

function setView(view) {
    state.view = view;
    $$(".nav-tab").forEach((tab) => tab.classList.toggle("active", tab.dataset.view === view));
    $$(".view").forEach((section) => section.classList.toggle("active", section.id === `view-${view}`));
    $("#viewTitle").textContent = viewMeta[view][0];
    $("#viewSubtitle").textContent = viewMeta[view][1];
    refreshCurrentView();
}

async function refreshCurrentView() {
    try {
        if (state.view === "accounts") await loadAccounts();
        if (state.view === "qa") await loadQa();
        if (state.view === "contacts") await loadContacts();
        if (state.view === "tasks") await loadTasks();
    } catch (error) {
        showStatus(error.message, "error");
    }
}

async function loadAccounts() {
    state.accounts = await api("/api/mail/sender-accounts");
    $("#accountsTable").innerHTML = state.accounts.map((account) => `
        <tr>
            <td><strong>${escapeHtml(account.accountCode)}</strong></td>
            <td>${escapeHtml(account.senderEmail)}</td>
            <td>${account.strategyWeight}</td>
            <td>${account.todaySentCount}/${account.dailySendLimit}</td>
            <td>${badge(account.enabled ? "启用" : "禁用", account.enabled ? "ok" : "error")}</td>
            <td class="actions">
                <button class="button" data-action="view-account" data-code="${escapeHtml(account.accountCode)}">查看</button>
                <button class="button" data-action="edit-account" data-code="${escapeHtml(account.accountCode)}">编辑</button>
                <button class="button" data-action="test-account" data-code="${escapeHtml(account.accountCode)}">测试</button>
                <button class="button" data-action="toggle-account" data-code="${escapeHtml(account.accountCode)}" data-enabled="${account.enabled}">
                    ${account.enabled ? "禁用" : "启用"}
                </button>
                <button class="button" data-action="reset-account" data-code="${escapeHtml(account.accountCode)}">重置</button>
            </td>
        </tr>
    `).join("");
}

function showAccountEditor() {
    $("#accountModal").hidden = false;
    document.body.classList.add("modal-open");
}

function hideAccountEditor() {
    const form = $("#accountForm");
    form.reset();
    form.accountCode.disabled = false;
    Array.from(form.elements).forEach((element) => {
        element.disabled = false;
    });
    $("#accountModal").hidden = true;
    document.body.classList.remove("modal-open");
    state.selectedAccount = null;
    state.accountEditorMode = null;
}

const newAccountDefaults = {
    senderTitle: "Customer Care Officer",
    teamName: "Qingfei Tech Talent Team",
    countryName: "China",
    smtpHost: "smtp.exmail.qq.com",
    smtpPort: 465,
    imapHost: "imap.exmail.qq.com",
    imapPort: 993,
    strategyWeight: 100,
    dailySendLimit: 100,
    todaySentCount: 0
};

function fillAccountForm(account, mode = account ? "edit" : "new") {
    const form = $("#accountForm");
    showAccountEditor();
    state.selectedAccount = account?.accountCode || null;
    state.accountEditorMode = mode;
    $("#accountEditorTitle").textContent = account
        ? `${mode === "view" ? "查看账号" : "编辑账号"}：${account.accountCode}`
        : "新增账号";
    Array.from(form.elements).forEach((element) => {
        element.disabled = mode === "view";
    });
    form.accountCode.disabled = Boolean(account) || mode === "view";
    $("#saveAccountBtn").hidden = mode === "view";
    $("#clearAccountFormBtn").disabled = false;
    [
        "accountCode", "senderEmail", "senderName", "senderTitle", "senderDisplayName", "teamName",
        "countryName", "smtpHost", "smtpPort", "smtpUsername", "imapHost", "imapPort",
        "imapUsername", "strategyWeight", "dailySendLimit", "todaySentCount"
    ].forEach((name) => {
        form[name].value = account?.[name] ?? newAccountDefaults[name] ?? "";
    });
    form.smtpPassword.value = "";
    form.imapPassword.value = "";
    form.enabled.checked = account?.enabled ?? true;
}

async function saveAccount(event) {
    event.preventDefault();
    if (state.accountEditorMode === "view") {
        return;
    }
    const form = event.currentTarget;
    const values = formValues(form);
    const payload = {
        accountCode: form.accountCode.value,
        senderEmail: values.senderEmail,
        senderName: values.senderName,
        senderTitle: values.senderTitle || null,
        senderDisplayName: values.senderDisplayName || null,
        teamName: values.teamName || null,
        countryName: values.countryName || null,
        smtpHost: values.smtpHost,
        smtpPort: numberValue(values.smtpPort, 465),
        smtpUsername: values.smtpUsername,
        smtpPassword: values.smtpPassword,
        imapHost: values.imapHost,
        imapPort: numberValue(values.imapPort, 993),
        imapUsername: values.imapUsername,
        imapPassword: values.imapPassword,
        strategyWeight: numberValue(values.strategyWeight, 100),
        dailySendLimit: numberValue(values.dailySendLimit, 100),
        enabled: form.enabled.checked
    };
    if (state.selectedAccount) {
        payload.todaySentCount = numberValue(values.todaySentCount, 0);
        await api(`/api/mail/sender-accounts/${encodeURIComponent(state.selectedAccount)}`, {
            method: "PUT",
            body: JSON.stringify(payload)
        });
    } else {
        await api("/api/mail/sender-accounts", {
            method: "POST",
            body: JSON.stringify(payload)
        });
    }
    showStatus("邮箱账号已保存");
    hideAccountEditor();
    await loadAccounts();
}

async function handleAccountAction(button) {
    const code = button.dataset.code;
    const action = button.dataset.action;
    if (action === "view-account") {
        const account = await api(`/api/mail/sender-accounts/${encodeURIComponent(code)}`);
        fillAccountForm(account, "view");
    }
    if (action === "edit-account") {
        const account = await api(`/api/mail/sender-accounts/${encodeURIComponent(code)}`);
        fillAccountForm(account, "edit");
    }
    if (action === "test-account") {
        const result = await api(`/api/mail/sender-accounts/${encodeURIComponent(code)}/test-connectivity`, { method: "POST" });
        showStatus(`${code}: SMTP ${result.smtp.passed ? "成功" : "失败"}，IMAP ${result.imap.passed ? "成功" : "失败"}`, result.passed ? "ok" : "error");
    }
    if (action === "toggle-account") {
        const enabled = button.dataset.enabled === "true";
        await api(`/api/mail/sender-accounts/${encodeURIComponent(code)}/${enabled ? "disable" : "enable"}`, { method: "POST" });
        await loadAccounts();
    }
    if (action === "reset-account") {
        await api(`/api/mail/sender-accounts/${encodeURIComponent(code)}/reset-today-sent-count`, { method: "POST" });
        await loadAccounts();
    }
}

async function loadQa() {
    const [categories, rules] = await Promise.all([
        api("/api/qa/categories"),
        api("/api/qa/rules")
    ]);
    state.categories = categories;
    state.qaRules = rules;
    $("#categoryList").innerHTML = categories.map((category) => `
        <div class="list-item">
            <div class="list-item-title">${escapeHtml(category.categoryName)} ${badge(`#${category.id}`, "")}</div>
            <div class="list-item-meta">
                <span>${escapeHtml(category.categoryCode)}</span>
                <span>${category.enabled ? "启用" : "禁用"}</span>
            </div>
        </div>
    `).join("");
    $("#qaRulesTable").innerHTML = rules.map((rule) => `
        <tr>
            <td>${rule.id}</td>
            <td>${escapeHtml(rule.categoryCode || rule.categoryId)}</td>
            <td>${escapeHtml(rule.keywords)}</td>
            <td>${rule.matchMode}</td>
            <td>${rule.priority}</td>
            <td>${badge(rule.enabled ? "启用" : "禁用", rule.enabled ? "ok" : "error")}</td>
            <td class="actions">
                <button class="button" data-action="edit-rule" data-id="${rule.id}">编辑</button>
                <button class="button" data-action="toggle-rule" data-id="${rule.id}" data-enabled="${rule.enabled}">
                    ${rule.enabled ? "禁用" : "启用"}
                </button>
            </td>
        </tr>
    `).join("");
}

async function saveCategory(event) {
    event.preventDefault();
    const values = formValues(event.currentTarget);
    await api("/api/qa/categories", {
        method: "POST",
        body: JSON.stringify({
            categoryCode: values.categoryCode,
            categoryName: values.categoryName,
            description: null,
            enabled: true
        })
    });
    event.currentTarget.reset();
    await loadQa();
}

function fillQaRuleForm(rule) {
    const form = $("#qaRuleForm");
    state.selectedRuleId = rule?.id || null;
    $("#qaRuleEditorTitle").textContent = rule ? `QA 规则：${rule.id}` : "QA 规则";
    form.id.value = rule?.id || "";
    form.categoryId.value = rule?.categoryId || "";
    form.keywords.value = rule?.keywords || "";
    form.matchMode.value = rule?.matchMode || "ANY";
    form.priority.value = rule?.priority || 100;
    form.replySubject.value = rule?.replySubject || "";
    form.replyBody.value = rule?.replyBody || "";
    form.autoReplyEnabled.checked = rule?.autoReplyEnabled ?? true;
    form.handoffRequired.checked = rule?.handoffRequired ?? false;
    form.enabled.checked = rule?.enabled ?? true;
}

async function saveQaRule(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const values = formValues(form);
    const payload = {
        categoryId: numberValue(values.categoryId),
        keywords: values.keywords,
        matchMode: values.matchMode,
        priority: numberValue(values.priority, 100),
        replySubject: values.replySubject || null,
        replyBody: values.replyBody,
        autoReplyEnabled: form.autoReplyEnabled.checked,
        handoffRequired: form.handoffRequired.checked,
        enabled: form.enabled.checked
    };
    const path = state.selectedRuleId ? `/api/qa/rules/${state.selectedRuleId}` : "/api/qa/rules";
    await api(path, { method: state.selectedRuleId ? "PUT" : "POST", body: JSON.stringify(payload) });
    showStatus("QA 规则已保存");
    fillQaRuleForm(null);
    await loadQa();
}

async function handleQaAction(button) {
    const action = button.dataset.action;
    const id = button.dataset.id;
    if (action === "edit-rule") {
        const rule = state.qaRules.find((item) => String(item.id) === String(id));
        fillQaRuleForm(rule);
    }
    if (action === "toggle-rule") {
        const enabled = button.dataset.enabled === "true";
        await api(`/api/qa/rules/${id}/${enabled ? "disable" : "enable"}`, { method: "POST" });
        await loadQa();
    }
}

async function loadContacts() {
    const params = new URLSearchParams();
    params.set("level", $("#expertIndexLevel").value);
    params.set("size", $("#expertIndexSize").value || "50");
    state.contacts = await api(`/api/experts?${params}`);
    $("#contactList").innerHTML = state.contacts.map((contact) => {
        const status = contact.contactId ? labelStatus(contact.contactStatus) : "未联系";
        const statusType = contact.contactStatus === "CLOSED"
            ? "error"
            : contact.contactStatus === "MANUAL_HANDOFF" || contact.contactStatus === "MANUAL_REVIEW"
                ? "warn"
                : contact.contactId
                    ? "ok"
                    : "";
        return `
        <div class="list-item expert-list-item ${state.selectedExpertOrcid === contact.orcidId ? "active" : ""}" data-action="select-expert" data-orcid="${escapeHtml(contact.orcidId)}" data-contact-id="${contact.contactId || ""}">
            <div class="expert-row-main">
                <div class="expert-name-block">
                    <div class="list-item-title expert-title">${escapeHtml(contact.displayName || contact.email || contact.orcidId)}</div>
                    <div class="list-item-meta expert-meta">
                        <span>${escapeHtml(indexLevelLabels[contact.indexLevel] || contact.indexLevelName || contact.indexLevel)}</span>
                        <span>${escapeHtml(contact.country || "国家未知")}</span>
                        <span>${escapeHtml(contact.email || "邮箱未知")}</span>
                    </div>
                </div>
                ${badge(status, statusType)}
            </div>
            <div class="expert-row-sub">
                <span>ORCID: ${escapeHtml(contact.orcidId || "-")}</span>
                ${contact.employment ? `<span>${escapeHtml(contact.employment)}</span>` : ""}
                ${contact.keyword ? `<span>${escapeHtml(contact.keyword)}</span>` : ""}
            </div>
        </div>
    `;
    }).join("");
}

function renderKeywords(keywordString) {
    if (!keywordString) return `<span class="text-muted">无关键词</span>`;
    const list = keywordString.split(/[,,，，;；]/).map(k => k.trim()).filter(Boolean);
    if (list.length === 0) return `<span class="text-muted">无关键词</span>`;
    return `<div class="keywords-container">
        ${list.map(k => `<span class="keyword-pill">${escapeHtml(k)}</span>`).join("")}
    </div>`;
}

function showExpertDetail(expert) {
    const name = expert.displayName || expert.email || expert.orcidId || "?";
    const initial = name.charAt(0).toUpperCase();
    const contactDetail = $("#contactDetail");
    contactDetail.classList.remove("detail-empty");
    contactDetail.scrollTop = 0;
    contactDetail.innerHTML = `
        <div class="detail">
            <div class="expert-profile-header">
                <div class="expert-avatar">${escapeHtml(initial)}</div>
                <div class="expert-header-info">
                    <h2>${escapeHtml(name)}</h2>
                    <p>
                        <span>${escapeHtml(indexLevelLabels[expert.indexLevel] || expert.indexLevelName || expert.indexLevel)}</span>
                        <span class="divider">·</span>
                        <span>${escapeHtml(expert.email || "邮箱未知")}</span>
                    </p>
                </div>
            </div>

            <div class="mail-timeline">
                ${detail.mails.slice().reverse().map(renderMailItem).join("") || "<p>暂无邮件记录。</p>"}
            </div>

            <div class="metadata-grid">
                <div class="metadata-card">
                    <div class="metadata-card-header">
                        <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
                        <span>ORCID</span>
                    </div>
                    <div class="metadata-card-value">
                        ${expert.orcidId ? `<a href="https://orcid.org/${escapeHtml(expert.orcidId)}" target="_blank" title="在新窗口打开 ORCID">${escapeHtml(expert.orcidId)}</a>` : "-"}
                    </div>
                </div>

                <div class="metadata-card">
                    <div class="metadata-card-header">
                        <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M2 12h20"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>
                        <span>国家 / 国籍</span>
                    </div>
                    <div class="metadata-card-value">
                        ${escapeHtml(expert.country || "未知")} ${expert.nationality ? `/ ${escapeHtml(expert.nationality)}` : ""}
                    </div>
                </div>

                <div class="metadata-card">
                    <div class="metadata-card-header">
                        <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                        <span>年龄 / 学历</span>
                    </div>
                    <div class="metadata-card-value">
                        ${expert.age ? `${escapeHtml(expert.age)} 岁` : ""} ${expert.degree ? `(${escapeHtml(expert.degree)})` : (!expert.age ? "-" : "")}
                    </div>
                </div>

                <div class="metadata-card">
                    <div class="metadata-card-header">
                        <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                        <span>阶段状态</span>
                    </div>
                    <div class="metadata-card-value">
                        ${badge(expert.contactStatus ? labelStatus(expert.contactStatus) : "未联系", expert.contactStatus === "CLOSED" ? "error" : expert.contactStatus === "MANUAL_HANDOFF" || expert.contactStatus === "MANUAL_REVIEW" ? "warn" : expert.contactId ? "ok" : "")}
                    </div>
                </div>

                <div class="metadata-card span-all">
                    <div class="metadata-card-header">
                        <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                        <span>当前单位 / 机构</span>
                    </div>
                    <div class="metadata-card-value">
                        ${escapeHtml(expert.employment || "-")}
                    </div>
                </div>

                <div class="metadata-card span-all">
                    <div class="metadata-card-header">
                        <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/></svg>
                        <span>专业关键词</span>
                    </div>
                    <div class="metadata-card-value">
                        ${renderKeywords(expert.keyword)}
                    </div>
                </div>
            </div>

            ${expert.contactId ? `
                <div class="toolbar" style="margin-top: 4px;">
                    <button class="button primary" data-action="select-contact" data-id="${expert.contactId}">
                        <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 4px;"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                        查看联系详情
                    </button>
                </div>
            ` : ""}
        </div>
    `;
    requestAnimationFrame(() => {
        contactDetail.scrollTop = 0;
    });
}

function formatMailTime(mail) {
    return mail.receivedAt || mail.sentAt || mail.createdAt || "";
}

function compactText(value, maxLength = 220) {
    const text = String(value || "")
        .replace(/\s+/g, " ")
        .trim();
    if (text.length <= maxLength) {
        return text;
    }
    return `${text.slice(0, maxLength)}...`;
}

function renderMailItem(mail) {
    const direction = mail.direction.toLowerCase();
    const body = mail.body || "";
    const compactBody = compactText(body);
    const shouldCollapse = body.trim().length > compactBody.length;
    
    // Choose appropriate SVG icon
    const isOutbound = mail.direction === "OUTBOUND";
    const mailIcon = isOutbound 
        ? `<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>`
        : `<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>`;

    return `
        <article class="mail-item ${direction}">
            <div class="mail-item-head">
                <div class="mail-meta-type">
                    ${mailIcon}
                    <strong>${escapeHtml(labelMailDirection(mail.direction))} · ${escapeHtml(labelMailType(mail.mailType))}</strong>
                </div>
                <div class="mail-meta-time">
                    <span>${escapeHtml(formatMailTime(mail))}</span>
                    ${mail.sendStatus ? badge(mail.sendStatus, mail.sendStatus === "SENT" ? "ok" : "warn") : ""}
                </div>
            </div>
            <div class="mail-subject">${escapeHtml(mail.subject || "无主题")}</div>
            <div class="mail-preview">${escapeHtml(compactBody || "无正文")}</div>
            ${shouldCollapse ? `
                <details class="mail-body-detail">
                    <summary>查看完整正文</summary>
                    <div class="pre">${escapeHtml(body)}</div>
                </details>
            ` : ""}
        </article>
    `;
}

async function loadContactDetail(contactId) {
    const [detail, options] = await Promise.all([
        api(`/api/expert-contacts/${contactId}`),
        loadMailSendOptions()
    ]);
    const contact = detail.contact;
    const expert = state.contacts.find(item => item.orcidId === state.selectedExpertOrcid) || {};
    const name = contact.expertName || contact.expertEmail || expert.displayName || "?";
    const initial = name.charAt(0).toUpperCase();
    $("#contactHeadActions").hidden = false;
    $("#contactHeadActions").innerHTML = `
        <div class="contact-head-status-actions">
            <button class="button" data-action="create-handoff" data-id="${contact.id}">
                <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 4px;"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><line x1="19" y1="8" x2="19" y2="14"/><line x1="22" y1="11" x2="16" y2="11"/></svg>
                <span>转人工</span>
            </button>
            <button class="button" data-action="complete-handoff" data-id="${contact.id}">
                <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.2" fill="none" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 4px;"><polyline points="20 6 9 17 4 12"/></svg>
                <span>完成人工</span>
            </button>
            <button class="button danger" data-action="close-contact" data-id="${contact.id}">
                <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.2" fill="none" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 4px;"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><line x1="9" y1="9" x2="15" y2="15"/><line x1="15" y1="9" x2="9" y2="15"/></svg>
                <span>关闭</span>
            </button>
        </div>
        <div class="contact-head-mail-actions">
        <select id="manualMailOption" aria-label="选择要发送的邮件">
            ${options.map((option) => `
                <option value="${escapeHtml(option.optionType)}:${escapeHtml(option.optionValue)}">
                    ${escapeHtml(option.optionName)}${option.subject ? ` - ${escapeHtml(option.subject)}` : ""}
                </option>
            `).join("")}
        </select>
        <button class="button primary" data-action="send-manual-mail" data-id="${contact.id}">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 4px;"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
            <span>发送邮件</span>
        </button>
        </div>
    `;

    const contactDetail = $("#contactDetail");
    contactDetail.classList.remove("detail-empty");
    contactDetail.scrollTop = 0;
    contactDetail.innerHTML = `
        <div class="detail">
            <div class="expert-profile-header">
                <div class="expert-avatar">${escapeHtml(initial)}</div>
                <div class="expert-header-info">
                    <h2>${escapeHtml(name)}</h2>
                    <p>
                        <span>${escapeHtml(contact.expertEmail)}</span>
                    </p>
                </div>
            </div>

            <div class="mail-timeline">
                ${detail.mails.slice().reverse().map(renderMailItem).join("") || "<p>暂无邮件记录。</p>"}
            </div>

            <div class="metadata-grid">
                <!-- Stage Status Card -->
                <div class="metadata-card">
                    <div class="metadata-card-header">
                        <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                        <span>阶段状态</span>
                    </div>
                    <div class="metadata-card-value">
                        ${badge(labelStatus(contact.currentStatus), contact.currentStatus === "CLOSED" ? "error" : contact.currentStatus === "MANUAL_HANDOFF" || contact.currentStatus === "MANUAL_REVIEW" ? "warn" : "ok")}
                    </div>
                </div>
                
                <!-- Manual Review Card -->
                <div class="metadata-card">
                    <div class="metadata-card-header">
                        <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                        <span>人工处理需求</span>
                    </div>
                    <div class="metadata-card-value">
                        ${contact.manualHandoffRequired ? badge("需要人工", "warn") : badge("无需人工", "")}
                    </div>
                </div>

                <!-- ORCID Card -->
                <div class="metadata-card">
                    <div class="metadata-card-header">
                        <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
                        <span>ORCID</span>
                    </div>
                    <div class="metadata-card-value">
                        ${expert.orcidId ? `<a href="https://orcid.org/${escapeHtml(expert.orcidId)}" target="_blank" title="在新窗口打开 ORCID">${escapeHtml(expert.orcidId)}</a>` : (contact.orcidId ? `<a href="https://orcid.org/${escapeHtml(contact.orcidId)}" target="_blank">${escapeHtml(contact.orcidId)}</a>` : "-")}
                    </div>
                </div>

                <!-- Country / Region Card -->
                <div class="metadata-card">
                    <div class="metadata-card-header">
                        <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M2 12h20"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>
                        <span>国家 / 地区</span>
                    </div>
                    <div class="metadata-card-value">
                        ${escapeHtml(expert.country || contact.expertCountry || "未知")}
                    </div>
                </div>

                <!-- Employment Card -->
                ${expert.employment ? `
                <div class="metadata-card span-all">
                    <div class="metadata-card-header">
                        <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                        <span>当前单位 / 机构</span>
                    </div>
                    <div class="metadata-card-value">
                        ${escapeHtml(expert.employment)}
                    </div>
                </div>
                ` : ""}

                <!-- Keywords Card -->
                ${expert.keyword ? `
                <div class="metadata-card span-all">
                    <div class="metadata-card-header">
                        <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/></svg>
                        <span>专业关键词</span>
                    </div>
                    <div class="metadata-card-value">
                        ${renderKeywords(expert.keyword)}
                    </div>
                </div>
                ` : ""}

                <!-- Handoff & Closed Details Card -->
                ${(contact.closedReason || detail.latestHandoff) ? `
                <div class="metadata-card span-all" style="background-color: #fffbeb; border-color: #fde68a;">
                    <div class="metadata-card-header" style="color: #b45309;">
                        <svg viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
                        <span>人工流转与关闭备注</span>
                    </div>
                    <div class="metadata-card-value" style="font-size: 13px; font-weight: 500; color: #78350f; display: flex; flex-direction: column; gap: 4px;">
                        ${contact.closedReason ? `<div><strong>关闭原因</strong>: ${escapeHtml(contact.closedReason)}</div>` : ""}
                        ${detail.latestHandoff ? `
                            <div><strong>转人工状态</strong>: ${badge(labelStatus(detail.latestHandoff.handoffStatus), "warn")}</div>
                            ${detail.latestHandoff.assignedTo ? `<div><strong>处理人</strong>: ${escapeHtml(detail.latestHandoff.assignedTo)}</div>` : ""}
                            ${detail.latestHandoff.note ? `<div><strong>处理说明</strong>: ${escapeHtml(detail.latestHandoff.note)}</div>` : ""}
                        ` : ""}
                    </div>
                </div>
                ` : ""}
            </div>

        </div>
    `;
    requestAnimationFrame(() => {
        contactDetail.scrollTop = 0;
    });
}

async function loadMailSendOptions() {
    if (state.mailSendOptions.length > 0) {
        return state.mailSendOptions;
    }
    state.mailSendOptions = await api("/api/expert-contacts/mail-send-options");
    return state.mailSendOptions;
}

async function handleContactAction(element) {
    const id = element.dataset.id;
    const action = element.dataset.action;
    if (action === "select-expert") {
        const orcidId = element.dataset.orcid;
        const expert = state.contacts.find((item) => item.orcidId === orcidId);
        state.selectedExpertOrcid = orcidId;
        $$("#contactList .list-item").forEach((item) => {
            item.classList.toggle("active", item.dataset.orcid === orcidId);
        });
        if (expert?.contactId) {
            await loadContactDetail(expert.contactId);
        } else if (expert) {
            showExpertDetail(expert);
        }
        return;
    }
    if (action === "select-contact") await loadContactDetail(id);
    if (action === "send-manual-mail") {
        const selected = $("#manualMailOption")?.value;
        if (!selected) {
            showStatus("请选择要发送的邮件", "error");
            return;
        }
        const [optionType, optionValue] = selected.split(":");
        await api(`/api/expert-contacts/${id}/manual-mail`, {
            method: "POST",
            body: JSON.stringify({
                optionType,
                optionValue,
                senderAccountCode: null
            })
        });
        showStatus("邮件已发送");
        await loadContactDetail(id);
    }
    if (action === "create-handoff") {
        const reason = prompt("转人工原因", "Needs manual review");
        if (!reason) return;
        await api(`/api/expert-contacts/${id}/manual-handoff`, {
            method: "POST",
            body: JSON.stringify({ reason, assignedTo: null, note: null })
        });
        await loadContactDetail(id);
        await loadContacts();
    }
    if (action === "complete-handoff") {
        await api(`/api/expert-contacts/${id}/manual-handoff/complete`, {
            method: "POST",
            body: JSON.stringify({ nextStatus: "WAITING_REPLY", note: "Completed from console" })
        });
        await loadContactDetail(id);
        await loadContacts();
    }
    if (action === "close-contact") {
        const reason = prompt("关闭原因", "Closed from console");
        if (!reason) return;
        await api(`/api/expert-contacts/${id}/close`, {
            method: "POST",
            body: JSON.stringify({ reason })
        });
        await loadContacts();
        $("#contactHeadActions").hidden = true;
        $("#contactHeadActions").innerHTML = "";
        $("#contactDetail").classList.add("detail-empty");
        $("#contactDetail").innerHTML = `选择一条专家联系记录。`;
    }
}

async function loadTasks() {
    const params = new URLSearchParams();
    const taskType = $("#taskTypeFilter").value;
    const status = $("#taskStatusFilter").value;
    if (taskType) params.set("taskType", taskType);
    if (status) params.set("status", status);
    const suffix = params.toString() ? `?${params}` : "";
    const tasks = await api(`/api/task-executions${suffix}`);
    $("#tasksTable").innerHTML = tasks.map((task) => `
        <tr>
            <td>${task.id}</td>
            <td>${escapeHtml(task.taskType)}</td>
            <td>${escapeHtml(task.triggerType)}</td>
            <td>${badge(labelStatus(task.status), task.status === "SUCCESS" ? "ok" : task.status === "FAILED" ? "error" : "warn")}</td>
            <td>${task.successCount}/${task.failureCount}</td>
            <td>${escapeHtml(task.startedAt)}</td>
            <td>${escapeHtml(task.errorMessage || "")}</td>
        </tr>
    `).join("");
}

function bindEvents() {
    $$(".nav-tab").forEach((tab) => tab.addEventListener("click", () => setView(tab.dataset.view)));
    $("#refreshBtn").addEventListener("click", refreshCurrentView);
    $("#reloadAccountsBtn").addEventListener("click", loadAccounts);
    $("#newAccountBtn").addEventListener("click", () => fillAccountForm(null, "new"));
    $("#clearAccountFormBtn").addEventListener("click", hideAccountEditor);
    $("#accountModalCloseBtn").addEventListener("click", hideAccountEditor);
    $("#accountModalBackdrop").addEventListener("click", hideAccountEditor);
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && !$("#accountModal").hidden) {
            hideAccountEditor();
        }
    });
    $("#accountForm").addEventListener("submit", saveAccount);
    $("#accountsTable").addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (button) handleAccountAction(button).catch((error) => showStatus(error.message, "error"));
    });
    $("#reloadQaBtn").addEventListener("click", loadQa);
    $("#categoryForm").addEventListener("submit", (event) => saveCategory(event).catch((error) => showStatus(error.message, "error")));
    $("#qaRuleForm").addEventListener("submit", (event) => saveQaRule(event).catch((error) => showStatus(error.message, "error")));
    $("#clearQaRuleBtn").addEventListener("click", () => fillQaRuleForm(null));
    $("#qaRulesTable").addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (button) handleQaAction(button).catch((error) => showStatus(error.message, "error"));
    });
    $("#loadContactsBtn").addEventListener("click", loadContacts);
    $("#contactList").addEventListener("click", (event) => {
        const item = event.target.closest("[data-action]");
        if (item) handleContactAction(item).catch((error) => showStatus(error.message, "error"));
    });
    $("#contactDetail").addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (button) handleContactAction(button).catch((error) => showStatus(error.message, "error"));
    });
    $("#contactHeadActions").addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (button) handleContactAction(button).catch((error) => showStatus(error.message, "error"));
    });
    $("#loadTasksBtn").addEventListener("click", loadTasks);
}

bindEvents();
refreshCurrentView();
